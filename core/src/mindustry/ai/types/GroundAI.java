package mindustry.ai.types;

import arc.math.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class GroundAI extends AIController{
    float stuckTime = 0f;
    float stuckX = -999f, stuckY = -999f;

    //精锐档闪避用
    float dodgeTimer = 0f, dodgeX, dodgeY;
    boolean dodging = false;

    static final float stuckRange = tilesize * 1.5f;

    @Override
    public void updateMovement(){

        //残血时后撤：会绕开墙，不会直挺挺撞上去
        if(retreating){
            stuckTime = 0f;
            retreatMovement();
            faceTarget();
            return;
        }

        //if it hasn't moved the stuck range in twice the time it should have taken, it's stuck
        float stuckThreshold = Math.max(1f, stuckRange * 2f / unit.type.speed);

        Building core = unit.closestEnemyCore();
        boolean moved = false;

        if(core != null && unit.within(core, unit.range() / 1.3f + core.block.size * tilesize / 2f)){
            //智能档下，手上已经有值得打的目标就不强行改成核心
            if(!AIIntelligence.active(unit) || target == null || checkTarget(target, unit.x, unit.y, unit.range())){
                target = core;
                for(var mount : unit.mounts){
                    if(mount.weapon.controllable && mount.weapon.bullet.collidesGround){
                        mount.target = core;
                    }
                }
            }
        }

        if((core == null || !unit.within(core, unit.type.range * 0.5f))){
            boolean move = true;

            if(state.rules.waves && unit.team == state.rules.defaultTeam){
                Tile spawner = getClosestSpawner();
                if(spawner != null && unit.within(spawner, state.rules.dropZoneRadius + 120f)) move = false;
                if(spawner == null && core == null) move = false;
            }

            //no reason to move if there's nothing there
            if(core == null && (!state.rules.waves || getClosestSpawner() == null)){
                move = false;
            }

            moved = move;

            //精锐档常开绕行，别一波兵全挤在一条线上
            boolean avoid = stuckTime >= stuckThreshold
                || (AIIntelligence.active(unit) && AIIntelligence.level() >= 2);
            if(move) pathfind(Pathfinder.fieldCore, true, avoid);
        }

        if(unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()){
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.descentSpeed);
        }

        faceTarget();

        if(moved){

            if(unit.within(stuckX, stuckY, stuckRange)){
                stuckTime += Time.delta;
                if(stuckTime - Time.delta < stuckThreshold && stuckTime >= stuckThreshold){
                    float radius = unit.hitSize * Vars.unitCollisionRadiusScale * 2f;
                    Units.nearby(unit.team, unit.x, unit.y, radius, other -> {
                        if(other != unit && other.controller() instanceof GroundAI ai && other.within(unit.x, unit.y, radius + other.hitSize * unitCollisionRadiusScale)){
                            ai.stuckX = other.x;
                            ai.stuckY = other.y;
                            ai.stuckTime = Math.max(1f, stuckRange * 2f / other.type.speed) + 1f;
                        }
                    });
                }
            }else{
                stuckX = unit.x;
                stuckY = unit.y;
                stuckTime = 0f;
            }
        }else{
            stuckTime = 0f;
        }

        //精锐档：闪避直冲自己的子弹（每 10 tick 算一次，避免每帧遍历）
        if(AIIntelligence.active(unit) && AIIntelligence.level() >= 2){
            if((dodgeTimer += Time.delta) >= 10f){
                dodgeTimer = 0f;
                dodging = AIIntelligence.dodge(unit, Tmp.v1);
                if(dodging){
                    dodgeX = Tmp.v1.x;
                    dodgeY = Tmp.v1.y;
                }
            }

            if(dodging){
                unit.movePref(Tmp.v2.set(dodgeX - unit.x, dodgeY - unit.y).setLength(prefSpeed()));
            }
        }else{
            dodging = false;
        }
    }
}
