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
import mindustry.type.*;
import mindustry.world.*;

import static mindustry.Vars.*;

/**
 * 玩家编队成员的控制器。继承 CommandAI 以兼容原版的选择/命令 UI（isCommandable 检查 instanceof CommandAI）。
 *
 * 行为：以方阵保持在玩家单位附近的固定槽位跟随；玩家挖矿时跟着挖同种矿；
 * 玩家建造/修复/拆除时复制其建造队列；玩家开火时同步瞄准射击（可选）；自动攻击（可选）。
 */
public class SquadAI extends CommandAI{
    /** 编队队长（玩家当前驾驶的单位）。null 表示未编队。 */
    public @Nullable Unit leader;
    /** 本成员在方阵里的槽位序号。 */
    public int slotIndex = 0;
    /** 编队总人数（最多 6）。 */
    public int squadSize = 1;
    /** 方阵间距（世界单位）。 */
    public static final float spacing = 26f;
    private static final float arriveDst = 6f;

    public boolean followAttack = true;

    private final Vec2 slotVec = new Vec2();
    private @Nullable Tile followOre;
    private float oreTimer;

    /** 队长是否还有效。 */
    private boolean followingActive(){
        return leader != null && leader.isValid() && !leader.dead();
    }

    /** 本成员相对队长的方阵偏移（2 列 N 行，随玩家朝向旋转）。 */
    public void getSlotOffset(Vec2 out){
        int col = slotIndex % 2;
        int row = slotIndex / 2;
        float rows = Mathf.ceil(squadSize / 2f);
        float ox = (col == 0 ? -1f : 1f) * spacing * 0.5f;
        float oy = (row - (rows - 1f) / 2f) * spacing;
        float rot = leader != null ? leader.rotation - 90f : 0f;
        out.set(ox, oy).rotate(rot);
    }

    @Override
    public void updateUnit(){
        //队长失效 → 还原成普通 CommandAI
        if(!followingActive()){
            leader = null;
            super.updateUnit();
            return;
        }

        //玩家在挖矿：跟着挖同种矿（近核心由 MinerComp 自动直送，远则存自身仓库）
        boolean mining = leader.mining() && leader.mineTile != null && unit.canMine();
        //玩家在建造/修复/拆除：复制其建造队列
        boolean building = leader.activelyBuilding() && unit.canBuild();

        if(mining){
            followMining();
        }else if(building){
            followBuilding();
            unit.updateBuilding = true;
        }else{
            unit.mineTile = null;
            followLeader();
        }

        //攻击：自动索敌 & 跟随玩家开火
        updateAttack();
    }

    /** 跟随玩家挖矿：挖玩家正在挖的同一种矿。 */
    private void followMining(){
        Item item = unit.getMineResult(leader.mineTile);
        if(item == null){
            unit.mineTile = null;
            followLeader();
            return;
        }
        //定时重新找最近的该种矿，避免每帧全图搜索
        if((oreTimer -= Time.delta) <= 0 || followOre == null || !unit.validMine(followOre) || unit.getMineResult(followOre) != item){
            oreTimer = 60f;
            followOre = indexer.findClosestOre(unit.x, unit.y, item);
            if(followOre == null && unit.type.mineWalls) followOre = indexer.findClosestWallOre(unit.x, unit.y, item);
        }
        if(followOre != null){
            moveTo(followOre, unit.type.mineRange / 2f, 20f);
            if(unit.within(followOre, unit.type.mineRange) && unit.validMine(followOre)){
                unit.mineTile = followOre;
            }
        }else{
            unit.mineTile = null;
            followLeader();
        }
    }

    /** 跟随玩家建造：复制玩家的建造队列（建造/修复/拆除都包含在内）。 */
    private void followBuilding(){
        //复制玩家的第一个建造计划，剩下的靠队伍队列
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
            //没有建造任务就回到玩家身边待命
            followLeader();
        }
    }

    private boolean samePlan(BuildPlan a, BuildPlan b){
        return a != null && b != null && a.x == b.x && a.y == b.y && a.block == b.block && a.breaking == b.breaking;
    }

    /** 朝自己的方阵槽位移动。 */
    private void followLeader(){
        getSlotOffset(slotVec);
        float tx = leader.x + slotVec.x;
        float ty = leader.y + slotVec.y;
        float dst = Mathf.dst(unit.x, unit.y, tx, ty);

        if(dst > arriveDst){
            moveTo(Tmp.v1.set(tx, ty), arriveDst, 30f);
        }else{
            unit.lookAt(leader.rotation);
        }
    }

    /** 攻击同步：自动索敌 + 玩家开火时朝玩家瞄准点射击。 */
    private void updateAttack(){
        boolean auto = SquadSettings.autoAttack();
        if(auto){
            updateTargeting();
        }else{
            stopShooting();
        }
        if(SquadSettings.followFire() && leader.isShooting()){
            unit.aim(leader.aimX(), leader.aimY(), true);
            unit.controlWeapons(true, true);
        }else if(!auto){
            unit.controlWeapons(false, false);
        }
    }

    @Override
    public void removed(Unit unit){
        leader = null;
        super.removed(unit);
    }
}
