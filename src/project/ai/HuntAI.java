package project.ai;

import arc.math.geom.Vec2;
import mindustry.ai.types.CommandAI;
import mindustry.ai.types.FlyingAI;
import mindustry.ai.types.GroundAI;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;

/**
 * 狩猎 AI：
 *  - 内部委托给 SmartAIWrapper(原版 AI)，保留原版的寻路 / 寻敌 / 追击
 *  - 在委托之上叠加编队分离力，避免多个狩猎单位挤在一起
 *
 * 效果：
 *  - 3 个狩猎单位追同一个敌人 → 自动散成扇形包围，不排队
 *  - 移动中互相靠太近 → 自动推开
 *  - 不动 SmartAIWrapper 的 kiting / 卡死逻辑
 */
public class HuntAI extends AIController {

    // ============ 委托 ============
    protected AIController delegate;
    public boolean forceDogfighter = false;

    // ============ 编队参数 ============
    /** 分离力检测半径倍率（相对于自身 hitSize） */
    public static float separationMul = 2.8f;
    /** 分离力强度（0~1） */
    public static float separationStrength = 0.8f;
    /** 单位之间额外留出的间隔（格） */
    public static float separationMargin = 8f;

    // ============ 状态 ============
    protected final Vec2 sepAccum = new Vec2();

    protected AIController getDelegate() {
        if (delegate == null) {
            AIController inner = unit.type.flying ? new FlyingAI() : new GroundAI();
            delegate = new SmartAIWrapper(inner);
            delegate.unit(unit);
        }
        return delegate;
    }

    @Override
    public void updateUnit() {
        // 1. 委托给 SmartAIWrapper：原版 AI 决定目标 + 寻路 + kiting + 防卡死
        getDelegate().updateUnit();

        // 2. 叠加分离力：微调速度向量，避免同队单位重叠
        applySeparation();
    }

    // ================================================================
    //  分离力
    // ================================================================

    protected void applySeparation() {
        if (unit == null || !unit.isAdded()) return;

        float sepRadius = unit.hitSize * separationMul;
        sepAccum.setZero();

        Units.nearby(unit.team, unit.x, unit.y, sepRadius, other -> {
            if (other == unit) return;
            if (!isAllyCommander(other)) return;

            float dst = unit.dst(other);
            float minDist = (unit.hitSize + other.hitSize) / 2f + separationMargin;
            if (dst < minDist && dst > 0.01f) {
                // 越近推力越大
                float strength = (minDist - dst) / minDist;
                sepAccum.add((unit.x - other.x) / dst * strength,
                             (unit.y - other.y) / dst * strength);
            }
        });

        if (sepAccum.len2() > 0.001f) {
            sepAccum.setLength(unit.speed() * separationStrength);
            // 直接加在速度上（叠加，不覆盖）。若原 AI 已经设了 movePref，最终速度是二者合成
            unit.vel.add(sepAccum);
        }
    }

    /**
     * 是不是"自己人"（同队 + 被玩家指挥控制的单位）。
     * 这样会排除：核心无人机、敌方单位、建筑、中立单位。
     */
    protected boolean isAllyCommander(Unit other) {
        return other.team == unit.team && other.controller() instanceof CommandAI;
    }
}