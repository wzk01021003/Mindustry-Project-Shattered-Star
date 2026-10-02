package project.ai;

import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.ai.types.CommandAI;
import mindustry.entities.Units;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import project.content.units.CarrierManager;
import project.content.units.CarrierUnitType;

public class EnterTransportAI extends CommandAI {

    public static float transportFindRange = 120f;
    public static float boardRange = 80f;
    public static float groundSmoothing = 100f;
    public static float flySmoothing = 40f;
    public static float turnSpeed = 0.15f;
    public static float retargetInterval = 20f;
    public static float engageRangeMul = 1.5f;

    private float smoothing() {
        return unit.type.flying ? flySmoothing : groundSmoothing;
    }

    /** 完全接管，绕过 CommandAI 的自动处理逻辑。 */
    @Override
    public void updateUnit() {
        updateMovement();
        updateTargeting();
    }

    @Override
    public void updateMovement() {
        Vec2 targetPos = unit.command() != null ? unit.command().targetPos : null;
        if (targetPos == null) return;

        // 索敌
        Teamc enemy = null;
        if (!unit.type.weapons.isEmpty()) {
            float range = unit.range();
            if (retarget() || target == null
                || Units.invalidateTarget(target, unit.team, unit.x, unit.y, range * engageRangeMul)) {
                target = Units.closestTarget(unit.team, unit.x, unit.y, range * engageRangeMul,
                    u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround),
                    b -> unit.type.targetGround);
            }
            enemy = target;
        }

        // 找车
        Unit transport = Units.closest(unit.team, targetPos.x, targetPos.y, transportFindRange,
            u -> u != unit
                && u.isValid()
                && (u.type instanceof CarrierUnitType)
                && !CarrierManager.isFull(u)
                && CarrierManager.accepts(u, unit.type));

        if (transport == null) {
            Tmp.v1.set(targetPos.x, targetPos.y);
            moveTo(Tmp.v1, 0f, smoothing());
            faceToward(targetPos.x, targetPos.y);
        } else {
            float dst = unit.dst(transport);
            if (dst <= boardRange) {
                if (CarrierManager.tryAttach(transport, unit)) {
                    unit.controller(new AttachedAI(transport));
                }
                return;
            }
            Tmp.v1.set(transport.x, transport.y);
            moveTo(Tmp.v1, 0f, smoothing());
            faceToward(transport.x, transport.y);
        }

        if (enemy != null) {
            unit.aim(enemy.getX(), enemy.getY());
            unit.controlWeapons(true);
        } else {
            unit.controlWeapons(false);
        }
    }

    private void faceToward(float x, float y) {
        float ang = Mathf.atan2(y - unit.y, x - unit.x) * Mathf.radDeg;
        unit.rotation = Mathf.slerpDelta(unit.rotation, ang, turnSpeed);
    }

    @Override
    public boolean retarget() {
        return timer.get(timerTarget, retargetInterval);
    }
}