package mindustry.ai.types;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/**
 * 玩家编队成员的控制器。继承 CommandAI 以兼容原版的选择/命令 UI（isCommandable 检查 instanceof CommandAI）。
 *
 * <p>跟随逻辑对齐原版 {@link RepairAI}/{@link mindustry.ai.types.BuilderAI}：
 * 贴身目标用 moveTo + lookAt，地面单位走 controlPath 绕开墙体/深水，
 * 飞行 circleTarget（狗斗）单位有目标时走 circleAttack。机体朝向交给移动/原版武器逻辑，不手动钉死。
 */
public class SquadAI extends CommandAI{
    /** 编队队长（玩家当前驾驶的单位）。null 表示未编队。 */
    public @Nullable Unit leader;
    /** 本成员在圆环阵形里的槽位序号。 */
    public int slotIndex = 0;
    /** 编队总人数（最多 6）。 */
    public int squadSize = 1;
    /** 编队环绕玩家的半径（世界单位）。实际半径由设置项 SquadSettings.radius() 决定。 */
    public static final float circleRadius = 40f;
    /** 到达槽位的判定半径。 */
    private static final float arriveDst = 8f;
    /** 贴身跟随（纵队）时与玩家保持的间距。 */
    private static final float followGap = 44f;
    /** 挖矿时离队长超过该距离就放弃这趟矿，回队跟随（避免玩家转移阵地后单位原地挖矿掉队）。 */
    private static final float miningAbandonDst = 40f * tilesize;
    /** 不可达时目标点向单位收缩的比例（从远到近逐个尝试）。 */
    private static final float[] approachSteps = {0.7f, 0.45f, 0.25f};

    public boolean followAttack = true;
    /** 归队途中：正从自由状态赶回玩家身边。 */
    public boolean returning = false;

    private final Vec2 slotVec = new Vec2();
    private @Nullable Tile followOre;
    private float oreTimer;
    /** 成员当前这轮正在挖的矿种。 */
    private @Nullable Item mineItem;
    /**
     * 玩家最近挖过的矿种。玩家停挖后仍然保留：
     * 成员把手头这轮挖满、送走之后，会接着挖这个矿种，一轮接一轮。
     */
    private @Nullable Item leaderMineItem;
    /**
     * 这一包货是从队友那里接过来的。为 true 时满包必须自己去卸货，不许再转手给别人，
     * 否则两个成员会「A 转给 B → B 转回 A」每帧来回倒，谁也挖不了矿。
     */
    public boolean receivedCargo;
    /** 寻路不可达时缓存的可达近似点（避免每帧重复 pathfind）。 */
    private final Vec2 approachDest = new Vec2();
    private boolean approachFound;
    private float approachTimer;

    /** 队长是否还有效。 */
    private boolean followingActive(){
        return leader != null && leader.isValid() && !leader.dead();
    }

    /**
     * 编队单位由 SquadAI 独占控制，禁止逻辑块（ucontrol / unitbind 等）把它抢过去。
     * 原版 {@link CommandAI#isLogicControllable()} 在无明确命令时返回 true，
     * 会被 LogicAI 接管，导致编队成员突然脱离编队、不再跟随玩家。
     */
    @Override
    public boolean isLogicControllable(){
        return false;
    }

    /**
     * 本成员相对队长的偏移（仅圆环阵形使用）。
     * 基准角固定在世界坐标，不跟玩家转身旋转——否则玩家一转身整队就绕着他打转。
     */
    public void getSlotOffset(Vec2 out){
        float radius = SquadSettings.radius();
        int n = Math.max(1, squadSize);
        int i = Math.max(0, slotIndex);
        float base = 90f;
        float angle = base + i * (360f / n);
        float r = n <= 1 ? radius * 0.6f : radius;
        out.set(Mathf.cosDeg(angle) * r, Mathf.sinDeg(angle) * r);
    }

    @Override
    public void updateUnit(){
        //队长失效 → 还原成普通 CommandAI
        if(!followingActive()){
            leader = null;
            super.updateUnit();
            return;
        }

        //自由编队：不跟随玩家，原地待命或执行玩家下达的指令（由指挥模式负责）。
        if(!SquadManager.follow){
            unit.mineTile = null;

            if(returning){
                if(unit.dst(leader) <= 24f){
                    returning = false;
                    command = null;
                }else{
                    moveToward(leader.x, leader.y);
                    updateBoost(false);
                    //自由编队归队途中也要让武器面向目标（原版行为）
                    faceTarget();
                    return;
                }
            }

            super.updateUnit();
            if(!SquadSettings.autoAttack() && attackTarget == null){
                stopShooting();
            }
            return;
        }

        //身上有货且背包已满 → 先把货分给还有空位的队友，自己接着挖；没人能接才去卸货。
        //背包未满时即使玩家停挖，也由 followMining 继续把矿挖满，不会半途停住。
        boolean hasCargo = unit.stack != null && unit.stack.amount > 0;
        boolean cargoFull = hasCargo && unit.stack.amount >= unit.type.itemCapacity;
        if(cargoFull){
            //这包货是从队友那里接过来的：必须自己去卸货，不许再转手，否则两人会来回倒货。
            if(receivedCargo){
                receivedCargo = false;
            }else if(shareCargo()){
                updateBoost(true);
                updateAttack();
                return;
            }
            //手头这轮挖满了，先送走；送走之后如果还有玩家指定的矿种，会接着下一轮（见 leaderMineItem）。
            unit.mineTile = null;
            unloadCargo();
            updateBoost(false);
            updateAttack();
            return;
        }

        //玩家在挖矿：记住矿种。停挖后这个记忆保留，成员把手头这轮送走会接着挖。
        boolean leaderMining = leader.mining() && leader.mineTile != null && unit.canMine();
        if(leaderMining){
            Item res = unit.getMineResult(leader.mineTile);
            if(res != null) leaderMineItem = res;
        }

        //决定这一轮挖什么：
        //- 背包里有别的矿：先挖完手头这轮，不换矿种（MinerComp 对异种矿会不停清 mineTile）。
        //- 背包空或同矿种：接着挖玩家指定的矿种；玩家停挖也继续，一轮接一轮。
        //- 玩家在赶路（有速度）：不主动开新矿，先跟上。
        boolean leaderMoving = leader.vel.len() > 0.5f;
        if(leaderMineItem != null && unit.canMine() && !leaderMoving
            && (unit.stack == null || unit.stack.amount <= 0 || unit.stack.item == leaderMineItem)){
            mineItem = leaderMineItem;
        }
        //玩家转去建造/拆除：放弃挖矿记忆，跟着建造。
        //必须清 leaderMineItem，否则下一帧又会被重新派去挖矿，永远跟不上建造。
        boolean building = leader.activelyBuilding() && unit.canBuild();
        if(building){
            leaderMineItem = null;
            mineItem = null;
            unit.mineTile = null;
            followBuilding();
            unit.updateBuilding = true;
        }else if(mineItem != null && unit.canMine()){
            //有正在挖的矿种：持续挖到背包满，送走后再来一轮（玩家停挖也继续）。
            followMining();
        }else{
            unit.mineTile = null;
            followLeader();
        }

        updateAttack();
    }

    /**
     * 背包满时把货分给队里还有空位的成员，自己继续挖，省一趟往返。
     *
     * <p>只要队友还有空位就填：一轮下来把最近几个成员的空位逐个铺满，
     * 自己腾出空间后继续挖矿。货是单向流动的（接收者会被标记 receivedCargo），
     * 所以不会出现两人来回倒货。
     *
     * @return true 表示已经把货分出去，可以继续挖矿
     */
    private boolean shareCargo(){
        if(unit.stack == null || unit.stack.amount <= 0) return false;
        Item item = unit.stack.item;
        boolean shared = false;

        for(int i = 0; i < SquadManager.maxUnits; i++){
            Unit receiver = SquadManager.findCargoReceiver(item, unit);
            if(receiver == null) break;

            int amount = Math.min(unit.stack.amount, receiver.maxAccepted(item));
            if(amount <= 0) break;

            //数据立即结算，只补一个飞行特效：若走 createItemTransfer 的延迟回调，
            //延迟期间接收者可能被其他成员重复选中，堆到超容量后 addItem 会裁掉，等于凭空丢货。
            unit.stack.amount -= amount;
            receiver.addItem(item, amount);
            InputHandler.createItemTransfer(item, amount, unit.x, unit.y, receiver, null);
            //给接收者打标：这包货是转手的，它满包后直接去卸，不能再转回来（否则两人来回倒货）。
            if(receiver.controller() instanceof SquadAI rs) rs.receivedCargo = true;
            shared = true;

            if(unit.stack.amount <= 0){
                unit.clearItem();
                break;
            }
        }

        return shared;
    }

    /**
     * 背包满后卸货：优先投放点，其次最近的核心。
     * 找不到任何可卸货目标时不强行清货，回编队待命（下次仍会尝试）。
     */
    private void unloadCargo(){
        if(SquadManager.dropPoint != null && tryUnloadAtDropPoint()){
            unit.mineTile = null;
            return;
        }
        Building core = unit.closestCore();
        if(core == null || !core.interactable(unit.team)){
            unit.mineTile = null;
            followLeader();
            return;
        }
        Item item = unit.item();
        if(item == null || unit.stack.amount == 0){
            unit.mineTile = null;
            return;
        }
        if(unit.within(core, unit.type.range)){
            if(core.acceptStack(item, unit.stack.amount, unit) > 0){
                Call.transferItemTo(unit, item, unit.stack.amount, unit.x, unit.y, core);
            }
            unit.clearItem();
        }else{
            moveToward(core.x, core.y);
        }
        unit.mineTile = null;
    }

    /**
     * 去投放点卸货。返回 true = 本帧已处理；false = 投放点不可用，调用方应让单位回编队。
     */
    private boolean tryUnloadAtDropPoint(){
        Vec2 drop = SquadManager.dropPoint;
        if(drop == null) return false;

        Item item = unit.item();
        if(item == null || unit.stack.amount <= 0) return false;

        if(unit.within(drop, 12f)){
            Building store = findStorageNear(drop, item);
            if(store == null) return false;
            int accepted = store.acceptStack(item, unit.stack.amount, unit);
            if(accepted <= 0) return false;
            Call.transferItemTo(unit, item, accepted, unit.x, unit.y, store);
            return true;
        }

        moveToward(drop.x, drop.y);
        return true;
    }

    private @Nullable Building findStorageNear(Vec2 pos, Item item){
        Building best = null;
        float bestDst = Float.MAX_VALUE;

        Building core = unit.team.core();
        if(core != null && core.interactable(unit.team) && core.acceptStack(item, 1, unit) > 0){
            best = core;
            bestDst = core.dst2(pos);
        }

        var stores = indexer.getFlagged(unit.team, BlockFlag.storage);
        if(stores != null){
            for(int i = 0; i < stores.size; i++){
                Building b = stores.get(i);
                if(b == null || !b.interactable(unit.team) || b.acceptStack(item, 1, unit) <= 0) continue;
                float d = b.dst2(pos);
                if(d < bestDst){
                    best = b;
                    bestDst = d;
                }
            }
        }
        return best;
    }

    /**
     * 挖矿：基于记忆的矿种 {@link #mineItem} 持续采掘，直到背包满。
     * 玩家停挖后 mineItem 仍保留，因此单位会把当前这趟矿挖满再去卸货，不会半途停住。
     */
    private void followMining(){
        Item item = mineItem;
        if(item == null){
            unit.mineTile = null;
            followLeader();
            return;
        }
        //掉队保护：玩家已经跑远（换阵地/赶路），放弃这趟矿回队，别留在原地挖到天荒地老。
        if(unit.dst(leader) > miningAbandonDst){
            mineItem = null;
            followOre = null;
            oreTimer = 0f;
            unit.mineTile = null;
            followLeader();
            return;
        }
        if((oreTimer -= Time.delta) <= 0 || followOre == null || !unit.validMine(followOre) || unit.getMineResult(followOre) != item){
            oreTimer = 60f;
            followOre = indexer.findClosestOre(unit.x, unit.y, item);
            if(followOre == null && unit.type.mineWalls) followOre = indexer.findClosestWallOre(unit.x, unit.y, item);
        }
        if(followOre != null){
            float len = Mathf.clamp((unit.dst(followOre) - unit.type.mineRange / 2f) / 20f, 0f, 1f);
            if(len > 0f) moveToward(followOre.getX(), followOre.getY());
            if(unit.within(followOre, unit.type.mineRange) && unit.validMine(followOre)){
                unit.mineTile = followOre;
            }
        }else{
            //这种矿已经采尽：清掉矿种回去跟随，等玩家指定新的矿再行动。
            mineItem = null;
            unit.mineTile = null;
            followLeader();
        }
        //挖矿期间开助推（有 boostWhenMining 的单位原版就开；没有的也按挖矿状态点火）。
        updateBoost(true);
    }

    /** 跟随玩家建造：复制玩家的建造队列。 */
    private void followBuilding(){
        if(leader.plans.size > 0){
            BuildPlan first = leader.plans.first();
            if(unit.plans.size == 0 || !samePlan(unit.plans.first(), first)){
                unit.plans.clear();
                for(var plan : leader.plans){
                    unit.plans.addLast(plan.copy());
                }
            }
        }else{
            unit.plans.clear();
            followLeader();
            return;
        }
        //建造/拆除期间开助推。
        updateBoost(true);
    }

    private boolean samePlan(BuildPlan a, BuildPlan b){
        return a != null && b != null && a.x == b.x && a.y == b.y && a.block == b.block && a.breaking == b.breaking;
    }

    /**
     * 朝目标点移动。地面单位走 controlPath 绕开墙体/深水，飞行单位直线过去。
     *
     * <p>与原版 {@link mindustry.ai.types.LogicAI} 的 pathfind 指令一致：
     * 用两参数重载 getPathPosition(unit, dest)，它内部 mainDestination=dest。
     *
     * <p>不可达时不再原地静止：把目标点沿"目标→单位"方向按比例收缩（0.7 / 0.45 / 0.25），
     * 找到第一个可达的近似点就走过去——这样墙后/对岸的目标会尽量靠近，且收缩点仍然经过寻路器
     * 判定，不会踏进深水或穿墙。结果缓存 25 帧，避免每帧多次重算 flow field。
     */
    private void moveToward(float tx, float ty){
        if(unit.isGrounded()){
            var result = controlPath.getPathPosition(unit, Tmp.v2.set(tx, ty));
            if(result.move){
                //moveTo 内置平滑减速 + rotateMoveFirst，机体朝向交给它处理，不手动 lookAt
                moveTo(result.dest, arriveDst, 30f, false, null);
                return;
            }

            //不可达：定期重算最近的可达近似点
            if((approachTimer -= Time.delta) <= 0f){
                approachTimer = 25f;
                approachFound = false;
                float ux = unit.x, uy = unit.y;
                for(int i = 0; i < approachSteps.length; i++){
                    float f = approachSteps[i];
                    var near = controlPath.getPathPosition(unit, Tmp.v2.set(ux + (tx - ux) * f, uy + (ty - uy) * f));
                    if(near.move){
                        approachDest.set(near.dest);
                        approachFound = true;
                        break;
                    }
                }
            }
            if(approachFound){
                moveTo(approachDest, arriveDst, 30f, false, null);
            }
        }else{
            Tmp.v3.set(tx, ty);
            moveTo(Tmp.v3, arriveDst, 40f, false, null);
        }
    }

    /**
     * 可助推单位（type.canBoost）的推进控制。
     * 原版 BuilderAI/MinerAI 也是每帧调 updateBoosting：掉队、深水、前方固体障碍、
     * 以及建造/拆除/挖矿期间点火加速。非助推单位由 {@link Unitc#updateBoosting} 内部直接忽略。
     */
    private void updateBoost(boolean busy){
        boolean boost = busy
            || unit.dst(leader) > 160f
            || unit.floorOn().isDeep()
            || blockedAhead();
        unit.updateBoosting(boost);
    }

    /** 机体前方是否顶着不可跨越的固体方块（飞行单位需要拉高越过）。 */
    private boolean blockedAhead(){
        float len = unit.hitSize / 2f + 6f;
        Tile t = world.tileWorld(unit.x + Angles.trnsx(unit.rotation, len), unit.y + Angles.trnsy(unit.rotation, len));
        return t != null && (t.solid() || t.floor().isDeep());
    }

    /** 朝自己的跟随目标移动。圆环用槽位，纵队用 Repair/Builder 式贴身跟随。 */
    private void followLeader(){
        if(SquadSettings.shape() == SquadSettings.SHAPE_COLUMN){
            //纵队：像 BuilderAI.assistFollowing / FlyingFollowAI 那样贴身跟随玩家，不固定相对坐标。
            //距离按双方体型算，避免叠在一起；地面单位由 moveToward 负责绕路。
            float dist = leader.hitSize/2f + unit.hitSize/2f + followGap;
            if(unit.dst(leader) > arriveDst + 4f){
                moveToward(leader.x, leader.y);
            }
            //贴身间距用 moveTo 的 keepDistance 语义在近处微调（保持 dist）
            if(unit.dst(leader) < dist * 0.7f && unit.isFlying()){
                moveTo(leader, dist, 30f, true, null);
            }
        }else{
            //圆环：均匀分布在玩家周围，各自守一个槽位
            getSlotOffset(slotVec);
            float tx = leader.x + slotVec.x;
            float ty = leader.y + slotVec.y;
            if(Mathf.dst(unit.x, unit.y, tx, ty) > arriveDst){
                moveToward(tx, ty);
            }
        }
        //到达后不强制 lookAt：机体朝向由 moveTo / 原版武器 faceTarget 决定，避免"朝向固死"。
        updateBoost(false);
    }

    /**
     * 攻击同步：自动索敌 + 跟枪。
     *
     * <p>回归原版武器机制：不使用 independentAim（它会覆盖 rotate=true 炮塔的独立旋转，
     * 导致 alpha 这类"面向攻击目标"的单位武器错位）。
     *
     * <p><b>朝向是这里的关键</b>：原版所有攻击 AI（GroundAI/BuilderAI/RepairAI/CommandAI）都会在
     * updateMovement 里 unit.lookAt(target)/faceTarget() 让机体面向目标。rotate=false 的武器方向 =
     * 机体朝向 + baseRotation，机体不朝目标武器就朝错方向。之前跟随模式漏了朝向处理 → "朝向不对"。
     * <ul>
     *   <li>跟枪：aimLook(光标) = aim + lookAt，机体与武器一起指向玩家光标真实坐标。
     *       rotate=false 武器靠机体朝向朝光标打；rotate=true 炮塔由 Weapon.update 依据 aimX/aimY 独立跟踪。
     *       lookAt 是渐进转向（原版行为），不是每帧钉死 rotation，鼠标横移不会"固死"。</li>
     *   <li>自动攻击：unit.aim(target) + faceTarget()，与原版 GroundAI 一致
     *       （faceTarget/全向单位朝目标并做拦截预判，普通地面单位仍由移动方向决定朝向）。</li>
     *   <li>飞行 circleTarget（狗斗）单位：有目标走 circleAttack 绕目标缠斗；陆地单位绝不套用。</li>
     * </ul>
     */
    private void updateAttack(){
        boolean auto = SquadSettings.autoAttack();

        //飞行狗斗单位：有目标就绕目标缠斗（原版 circleTarget 语义），陆地单位不适用。
        if(unit.isFlying() && unit.type.circleTarget && target != null && !invalid(target)){
            circleAttack(unit.type.circleTargetRadius);
            unit.controlWeapons(true, true);
            return;
        }

        if(auto){
            updateTargeting();
        }else{
            stopShooting();
        }

        if(SquadSettings.followFire() && leader.isShooting()){
            //跟枪：机体朝向 + 武器瞄准一起指向玩家光标。
            //关键：rotate=false 武器方向由机体朝向决定，必须 lookAt 光标它才会朝光标打；
            //      rotate=true 炮塔由 Weapon.update 依据 aimX/aimY 自行跟踪，不受机体朝向影响。
            //aimLook 用的是原版渐进 lookAt（rotateSpeed），不会像之前那样每帧钉死 rotation。
            unit.aimLook(leader.aimX, leader.aimY);
            unit.controlWeapons(true, true);
        }else if(target != null && auto && !invalid(target)){
            //自动索敌：照 RepairAI/BuilderAI —— aim 目标 + lookAt 让机体正面朝向目标。
            //注意这里不用 faceTarget()：它只对 omni/Mech 生效，普通地面单位（尖刀）会被跳过、
            //机体不朝目标 → rotate=false 武器朝错方向。lookAt 对所有单位生效，正是修复/重建 AI 的写法。
            unit.aim(target);
            unit.lookAt(target);
            unit.controlWeapons(true, true);
        }else if(!auto){
            unit.controlWeapons(false, false);
        }

        //没有正在进行的攻击朝向时：玩家在移动就让机体朝向玩家的移动方向。
        //否则单位静止时会一直保持旧朝向（看起来"只朝一个方向"）。lookAt 是渐进转向，自然平滑。
        boolean attacking = (SquadSettings.followFire() && leader.isShooting())
            || (target != null && auto && !invalid(target));
        if(!attacking && leader.moving() && leader.vel().len2() > 0.01f){
            unit.lookAt(leader.vel().angle());
        }
    }

    @Override
    public void removed(Unit unit){
        leader = null;
        super.removed(unit);
    }
}
