package project.ai;

import arc.math.geom.Vec2;
import mindustry.ai.types.CommandAI;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;

public class HuntAI extends AIController {

    protected AIController delegate;
    public boolean forceDogfighter = false;

    // ============ 编队参数 ============
    public static float separationMul = 2.8f;
    public static float separationStrength = 0.8f;
    public static float separationMargin = 8f;

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

        float sepRadius = unit.hitSize * separationMul;
        sepAccum.setZero();

        Units.nearby(unit.team, unit.x, unit.y, sepRadius, other -> {
            if (other == unit) return;
            if (!AIUtils.sameMovementClass(unit, other)) return;  // ← 只对同类分散
            if (!(other.controller() instanceof CommandAI)) return;

            float dst = unit.dst(other);
            float minDist = (unit.hitSize + other.hitSize) / 2f + separationMargin;
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