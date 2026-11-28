package mindustry.ai;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.ai.types.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.type.*;

import java.io.*;

import static mindustry.Vars.*;

/**
 * 玩家编队管理器（纯逻辑，网络同步交给 InputHandler 的 @Remote 方法）。
 * 负责：按设置挑选最多 6 个单位、把它们的控制器换成 SquadAI、维护队长与槽位、解除编队。
 */
public class SquadManager{
    public static final int maxUnits = 6;

    /** 本玩家当前编队成员的单位 id。 */
    public static final IntSeq squad = new IntSeq(maxUnits);

    /** 编队是否跟随玩家。false = 自由编队，成员原地待命，靠指挥模式下达指令。 */
    public static boolean follow = true;

    /** 编队指定投放点（世界坐标）。null = 未指定。成员挖矿满了会来这里卸货。 */
    public static @Nullable Vec2 dropPoint;

    /** 一键吸附：按当前设置挑选最多 6 个周围友军。返回被选中的单位 id。 */
    public static IntSeq autoPick(Unit playerUnit){
        IntSeq result = new IntSeq(maxUnits);
        if(playerUnit == null || playerUnit.dead()) return result;

        float range = 90f * Vars.tilesize; // 周围 90 格
        Seq<Unit> candidates = new Seq<>();
        for(Unit u : Groups.unit){
            if(u == playerUnit || u.dead() || u.team != playerUnit.team) continue;
            //逻辑块控制的单位不可编入编队
            if(u.controller() instanceof LogicAI) continue;
            if(!u.isCommandable() && !(u.controller() instanceof SquadAI)) continue;
            if(u.dst(playerUnit) > range) continue;
            candidates.add(u);
        }

        int rule = SquadSettings.pickRule();
        Unit playerRef = playerUnit;

        switch(rule){
            case SquadSettings.RULE_NEAREST:
                candidates.sort(u -> u.dst2(playerRef));
                break;
            case SquadSettings.RULE_WHITELIST:
                Seq<String> wl = Seq.with(SquadSettings.whitelist().toLowerCase().split("\\s*[,，]\\s*"));
                wl.removeAll(s -> s == null || s.isEmpty());
                candidates.sort(u -> (wl.contains(u.type.name.toLowerCase()) ? 0 : 1) + u.dst2(playerRef) / 1e9f);
                candidates.removeAll(u -> !wl.contains(u.type.name.toLowerCase()) && wl.size > 0);
                break;
            case SquadSettings.RULE_HEALTH:
                // 血量/强度最高：按最大生命值排序（大单位通常更强）
                candidates.sort(u -> -u.maxHealth());
                break;
            case SquadSettings.RULE_SAME:
            default:
                // 同类优先：同型号排前，其次距离近
                candidates.sort(u -> (u.type == playerRef.type ? 0f : 1e6f) + u.dst2(playerRef));
                break;
        }

        int n = Math.min(maxUnits, candidates.size);
        for(int i = 0; i < n; i++) result.add(candidates.get(i).id);
        return result;
    }

    /** 把一组单位编入玩家的编队（服务器/客户端调用，实际控制器切换在 applySquad）。 */
    public static void setSquad(IntSeq ids){
        squad.clear();
        squad.addAll(ids);
    }

    /** 该单位是否正被逻辑块控制（不可编入编队）。 */
    public static boolean isLogicControlled(Unit unit){
        return unit != null && unit.controller() instanceof LogicAI;
    }

    /** 给单个单位套上 SquadAI 控制器，记录队长和槽位。 */
    public static void applySquad(Unit unit, Unit leader, int slot, int size){
        if(unit == null || unit.dead() || leader == null) return;
        //逻辑块控制的单位不可编入：拒绝并把它从编队列表里剔除。
        if(isLogicControlled(unit)){
            squad.removeValue(unit.id);
            return;
        }
        SquadAI ai;
        if(unit.controller() instanceof SquadAI s){
            ai = s;
        }else{
            ai = new SquadAI();
            unit.controller(ai);
        }
        ai.leader = leader;
        ai.slotIndex = slot;
        ai.squadSize = size;
        ai.followAttack = SquadSettings.autoAttack();
        ai.command(UnitCommand.moveCommand);
    }

    /** 解除某单位的编队（还原成普通 CommandAI）。 */
    public static void removeSquad(Unit unit){
        if(unit == null) return;
        squad.removeValue(unit.id);
        if(unit.controller() instanceof SquadAI){
            unit.controller(new CommandAI());
        }
    }

    /**
     * 切换某单位是否在编队内（点选自选用）。
     * 返回 true 表示现在在编队内，false 表示已移出。
     */
    public static boolean toggleMember(Unit unit, Unit leader){
        if(unit == null || unit.dead() || leader == null) return false;
        //逻辑块正在控制的单位不能加入编队（已在编队的成员也不会被逻辑接管，见 SquadAI.isLogicControllable）。
        if(isLogicControlled(unit)) return false;
        if(squad.contains(unit.id)){
            removeSquad(unit);
            return false;
        }
        if(squad.size >= maxUnits) return true;
        squad.add(unit.id);
        applySquad(unit, leader, squad.size - 1, squad.size);
        return true;
    }

    /** 设置投放点（世界坐标）。传 null 清除。 */
    public static void setDropPoint(@Nullable Vec2 pos){
        dropPoint = pos == null ? null : pos.cpy();
    }

    /**
     * 调整槽位顺序：把 from 位置的成员与 to 位置的成员交换。
     * squad 的顺序即槽位顺序（update() 每帧按索引重算 slotIndex），所以直接换顺序即可换位。
     */
    public static void moveSlot(int from, int to){
        if(from < 0 || to < 0 || from >= squad.size || to >= squad.size || from == to) return;
        int a = squad.get(from), b = squad.get(to);
        squad.set(from, b);
        squad.set(to, a);
    }

    /** 让某个成员归队：脱离自由游荡，独自回到玩家身边待命。 */
    public static void requestReturn(Unit unit){
        if(unit != null && unit.controller() instanceof SquadAI s){
            s.returning = true;
        }
    }

    /**
     * 编队共享容量：背包满了以后，把货转给还有空位的队友。
     * 只接收同矿种（或空背包）的成员。
     *
     * <p>只要对方有一个空位就会填过去，不设最低容量门槛：自己没满时先填自己，
     * 自己满了就把队友的空位逐个填满。货不会回流（见 SquadAI.receivedCargo），
     * 所以不存在两人来回倒货的问题。
     *
     * @param source 出货方：玩家或编队成员
     */
    public static @Nullable Unit findCargoReceiver(Item item, Unit source){
        if(item == null || source == null || squad.size == 0) return null;
        //只有玩家和编队成员能往外倒货，普通单位不参与
        if(!source.isPlayer() && !(source.controller() instanceof SquadAI)) return null;

        //玩家在队外，可以放宽到 20 格；成员之间离得远不如各自去卸货，收紧到 15 格
        float maxDst = source.isPlayer() ? 160f : 120f;

        Unit best = null;
        float bestDst = Float.MAX_VALUE;

        for(int i = 0; i < squad.size; i++){
            Unit u = Groups.unit.getByID(squad.get(i));
            if(u == null || u == source || u.dead() || u.team != source.team) continue;
            if(u.stack == null) continue;
            if(u.stack.amount > 0 && u.stack.item != item) continue;
            //只要还有一个空位就能接
            if(u.type.itemCapacity - u.stack.amount < 1) continue;

            float dst = u.dst(source);
            if(dst > maxDst) continue;
            if(dst < bestDst){
                best = u;
                bestDst = dst;
            }
        }
        return best;
    }

    /** 这个单位背包已满、且队里有人能接货。用于让挖矿单位继续挖而不是丢下矿回去卸。 */
    public static boolean canOverflowCargo(Item item, Unit source){
        if(source == null || item == null) return false;
        if(!source.isPlayer() && !(source.controller() instanceof SquadAI)) return false;
        return source.stack != null && source.stack.amount >= source.type.itemCapacity
            && findCargoReceiver(item, source) != null;
    }

    /** 该成员是否正在归队途中。 */
    public static boolean isReturning(Unit unit){
        return unit != null && unit.controller() instanceof SquadAI s && s.returning;
    }

    /** 清空当前玩家编队。 */
    public static void clear(){
        for(int i = 0; i < squad.size; i++){
            Unit u = Groups.unit.getByID(squad.get(i));
            if(u != null && u.controller() instanceof SquadAI){
                u.controller(new CommandAI());
            }
        }
        squad.clear();
        dropPoint = null;
        follow = true;
    }

    /** 每帧维护：队长变了就重新指定、成员死了就清理、控制器丢了就补挂。 */
    public static void update(Unit playerUnit){
        if(playerUnit == null || playerUnit.dead()){
            if(squad.size > 0) clear();
            return;
        }
        for(int i = squad.size - 1; i >= 0; i--){
            Unit u = Groups.unit.getByID(squad.get(i));
            if(u == null || u.dead()){
                squad.removeIndex(i);
                continue;
            }
            SquadAI s;
            if(u.controller() instanceof SquadAI existing){
                s = existing;
            }else{
                //读档后单位是默认 controller（存档不保存它），这里补挂回 SquadAI。
                s = new SquadAI();
                u.controller(s);
            }
            //槽位必须每帧重算：成员数量变化（有人死了）或队长换人时，
            //否则 slotIndex/squadSize 会停留在旧值，队形就散开了。
            s.leader = playerUnit;
            s.squadSize = squad.size;
            s.slotIndex = i;
        }
    }

    // ---------------- 存档 ----------------

    /**
     * 注册存档块。原版存档不保存单位 controller，编队信息（成员 / 跟随开关 / 投放点）
     * 通过自定义 chunk 独立保存，读档后重新套上 SquadAI。
     */
    public static void init(){
        SaveVersion.addCustomChunk("ss-squad", new SaveFileReader.CustomChunk(){
            @Override
            public boolean shouldWrite(){
                return squad.size > 0;
            }

            @Override
            public boolean writeNet(){
                return false;
            }

            @Override
            public void write(DataOutput stream) throws IOException{
                stream.writeInt(squad.size);
                for(int i = 0; i < squad.size; i++){
                    stream.writeInt(squad.get(i));
                }
                stream.writeBoolean(follow);
                stream.writeBoolean(dropPoint != null);
                if(dropPoint != null){
                    stream.writeFloat(dropPoint.x);
                    stream.writeFloat(dropPoint.y);
                }
            }

            @Override
            public void read(DataInput stream) throws IOException{
                int amount = stream.readInt();
                IntSeq ids = new IntSeq(maxUnits);
                for(int i = 0; i < amount; i++){
                    int id = stream.readInt();
                    //id 在存档里保持稳定；找不到说明单位已消失，直接丢弃
                    Unit u = Groups.unit.getByID(id);
                    if(u != null && !u.dead() && ids.size < maxUnits){
                        ids.add(id);
                    }
                }
                follow = stream.readBoolean();
                boolean hasDrop = stream.readBoolean();
                dropPoint = hasDrop ? new Vec2(stream.readFloat(), stream.readFloat()) : null;

                squad.clear();
                squad.addAll(ids);
                //controller 不在这里套：读档时 player.unit() 尚未恢复（由随后的 checkUnit 处理），
                //下一帧 SquadManager.update() 会自动补挂 SquadAI。
            }
        });

        //WorldLoadEvent 在读档流程中由 readMap 的 context.end() 触发，早于本 chunk 的读取；
        //所以这里先清空内存状态，随后 chunk 读取时再恢复编队。
        Events.on(WorldLoadEvent.class, e -> reset());
    }

    /** 清空内存状态，不触碰场上单位（世界正在切换时旧单位已销毁）。 */
    public static void reset(){
        squad.clear();
        dropPoint = null;
        follow = true;
    }
}
