package project.ai;

import arc.math.geom.Vec2;
import mindustry.ai.types.CommandAI;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;

public class HuntAI extends AIController {

    protected AIController delegate;
    public boolean forceDogfighter = false;

    // ============ 自适应分离参数 ============
    /** 双方 hitSize 之和的倍数 */
    public static float separationFactor = 0.65f;
    /** 额外固定间距（格） */
    public static float separationMargin = 4f;
    /** 搜索半径 = hitSize × 此值 */
    public static float sepSearchMul = 3f;
    /** 搜索半径额外固定值 */
    public static float sepSearchMargin = 20f;
    /** 分离力强度 */
    public static float separationStrength = 0.8f;

    protected final Vec2 sepAccum = new Vec2();

    protected AIController getDelegate() {
        if (delegate == null) {
            delegate = unit.type.flying ? new FastFlyingAI() : new FastGroundAI();
            delegate.unit(unit);
        }
        return delegate;
    }

    @Override
    public void updateUnit() {
        getDelegate().updateUnit();
        applySeparation();
    }

    protected void applySeparation() {
        if (unit == null || !unit.isAdded()) return;

        // ★ 搜索半径按单位尺寸自适应
        float sepRadius = AIUtils.separationRadius(unit, sepSearchMul, sepSearchMargin);
        sepAccum.setZero();

        Units.nearby(unit.team, unit.x, unit.y, sepRadius, other -> {
            if (other == unit) return;
            if (!AIUtils.sameMovementClass(unit, other)) return;
            if (!(other.controller() instanceof CommandAI)) return;

            // ★ 分离距离按双方尺寸自适应
            float minDist = AIUtils.separationDistance(unit, other, separationFactor, separationMargin);
            float dst = unit.dst(other);

            if (dst < minDist && dst > 0.01f) {
                float strength = (minDist - dst) / minDist;
                sepAccum.add((unit.x - other.x) / dst * strength,
                             (unit.y - other.y) / dst * strength);
            }
        });

        if (sepAccum.len2() > 0.001f) {
            sepAccum.setLength(unit.speed() * separationStrength);
            unit.vel.add(sepAccum);
        }
    }

    @Override
    public void removed(Unit unit) {
        super.removed(unit);
        if (delegate != null) delegate.removed(unit);
    }
}