package mindustry.ai;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/**
 * 原版 AI 智能度分层。
 *
 * <p>level 0 = 原版行为，默认值，手感完全不变。
 * <p>level 1 = 智能：索敌更快、按威胁打分挑目标、跟友军集火、残血撤回核心。
 * <p>level 2 = 精锐：在 1 的基础上再加闪避直冲自己的子弹、绕开拥堵。
 *
 * <p>只作用于敌方队伍，玩家部队保持原版行为，避免自己的兵突然不听使唤。
 */
public class AIIntelligence{
    public static final int maxLevel = 2;

    /** 当前智能度。不在游戏里时一律按原版处理。 */
    public static int level(){
        return state.isGame() ? Mathf.clamp(state.rules.aiLevel, 0, maxLevel) : 0;
    }

    /** 这名单位是否吃增强。 */
    public static boolean active(Unit unit){
        return level() > 0
            && unit != null
            && unit.team != Team.derelict
            && unit.team != state.rules.defaultTeam
            && unit.isAI();
    }

    /** 重新索敌间隔（tick）。原版是没目标 40、有目标 90。 */
    public static float retargetInterval(boolean hasTarget){
        int l = level();
        if(l <= 0) return hasTarget ? 90f : 40f;
        if(l == 1) return hasTarget ? 48f : 24f;
        return hasTarget ? 24f : 12f;
    }

    /**
     * 按「距离 + 残血 + 是否在打我 + 有没有武器」打分挑目标，而不是只挑最近的。
     * 返回 null 时调用方回落到原版 {@link Units#closestTarget}。
     */
    public static Teamc bestTarget(Unit unit, float x, float y, float range, boolean air, boolean ground){
        if(!active(unit)) return null;

        Teamc[] best = {null};
        float[] bestScore = {Float.MAX_VALUE};
        float inv = 1f / Math.max(1f, range);

        Units.nearbyEnemies(unit.team, x, y, range, other -> {
            if(other == unit || !other.checkTarget(air, ground)) return;
            if(Units.invalidateTarget(other, unit.team, x, y, range)) return;

            //距离占大头，保证不会为了追残血横穿半张地图
            float score = Mathf.dst(x, y, other.x, other.y) * inv * 0.55f;
            //越残血越优先，收人头
            score += other.health / Math.max(1f, other.maxHealth()) * 0.24f;
            //正在打我的先处理
            if(other.controller() instanceof AIController ai && ai.target() == unit) score -= 0.2f;
            //带武器的威胁更大
            if(other.hasWeapons()) score -= 0.06f;

            if(score < bestScore[0]){
                bestScore[0] = score;
                best[0] = other;
            }
        });

        return best[0];
    }

    /** 跟随附近友军已锁定的目标，形成集火。 */
    public static Teamc focusTarget(Unit unit, float range){
        if(!active(unit)) return null;

        Teamc[] found = {null};
        float radius = Math.min(range, 420f);

        Units.nearby(unit.team, unit.x, unit.y, radius, other -> {
            if(found[0] != null || other == unit) return;
            if(!(other.controller() instanceof AIController ai)) return;

            Teamc t = ai.target();
            if(t == null || Units.invalidateTarget(t, unit.team, unit.x, unit.y, range)) return;
            if(Mathf.dst(unit.x, unit.y, t.getX(), t.getY()) > range) return;

            found[0] = t;
        });

        return found[0];
    }

    /** 残血时是否应该后撤。 */
    public static boolean shouldRetreat(Unit unit){
        int l = level();
        if(l <= 0) return false;
        float threshold = l >= 2 ? 0.4f : 0.28f;
        return unit.health < unit.maxHealth() * threshold;
    }

    /**
     * 最近的威胁：先找敌方单位，再找敌方炮塔。
     * 撤退时用它当参照物，避免「射程内没敌人就站着挨打」。
     */
    public static Teamc nearestEnemy(Unit unit, float radius){
        Teamc[] best = {null};
        float[] bestDist = {Float.MAX_VALUE};

        Units.nearbyEnemies(unit.team, unit.x, unit.y, radius, other -> {
            float d = Mathf.dst(unit.x, unit.y, other.x, other.y);
            if(d < bestDist[0]){
                bestDist[0] = d;
                best[0] = other;
            }
        });

        //敌方炮塔一样能把人打残，一并算成威胁
        for(Building b : indexer.getEnemy(unit.team, BlockFlag.turret)){
            if(!b.isValid()) continue;
            float d = Mathf.dst(unit.x, unit.y, b.x, b.y);
            if(d < radius && d < bestDist[0]){
                bestDist[0] = d;
                best[0] = b;
            }
        }

        return best[0];
    }

    /**
     * 找一颗正在直冲自己、且很快会命中的子弹，输出一个垂直方向的闪避点。
     * 调用方需要限制调用频率（建议 10 tick 一次）。
     */
    public static boolean dodge(Unit unit, Vec2 out){
        if(level() < 2) return false;

        float rad = unit.hitSize + 64f;
        boolean[] found = {false};

        Groups.bullet.intersect(unit.x - rad, unit.y - rad, rad * 2f, rad * 2f, b -> {
            if(found[0] || b.team == unit.team || b.type.homingPower > 0.0001f) return;

            float speed = b.vel.len();
            if(speed < 0.01f) return;

            float dx = unit.x - b.x, dy = unit.y - b.y;
            float dist = Mathf.dst(b.x, b.y, unit.x, unit.y);
            if(dist < 1f || dist > rad) return;

            //方向必须大致指向我，擦边而过的不用管
            if((b.vel.x * dx + b.vel.y * dy) / (speed * dist) < 0.9f) return;
            //0.45 秒内会到才值得躲
            if(dist / speed > 0.45f) return;

            //垂直于弹道让开
            float px = -b.vel.y / speed, py = b.vel.x / speed;
            float side = (dx * px + dy * py) >= 0f ? 1f : -1f;
            out.set(unit.x + px * side * (unit.hitSize + 40f), unit.y + py * side * (unit.hitSize + 40f));
            found[0] = true;
        });

        return found[0];
    }
}
