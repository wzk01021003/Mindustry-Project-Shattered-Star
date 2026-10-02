package project.ai;

import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import project.content.units.CarrierManager;
import project.content.units.CarrierUnitType;

public class EnterTransportAI extends AIController {

    /** 找车范围。 */
    public static float transportFindRange = 120f;
    /** 进车判定距离。 */
    public static float boardRange = 80f;
    /** 地面单位寻路平滑。 */
    public static float groundSmoothing = 100f;
    /** 飞行单位寻路平滑。 */
    public static float flySmoothing = 40f;
    /** 转向速度。 */
    public static float turnSpeed = 0.15f;
    /** 索敌间隔（帧）。 */
    public static float retargetInterval = 20f;
    /** 索敌半径倍率（相对 unit.range()）。 */
    public static float engageRangeMul = 1.5f;

    private float smoothing() {
        return unit.type.flying ? flySmoothing : groundSmoothing;
    }

    @Override
    public void updateMovement() {
        Vec2 targetPos = unit.command().targetPos;
        if (targetPos == null) return;

        // ============ 1. 索敌 ============
        Teamc enemy = null;
        if (!unit.type.weapons.isEmpty()) {
            if (retarget() || target == null
                || Units.invalidateTarget(target, unit.team, unit.x, unit.y, unit.range() * engageRangeMul)) {
                target = Units.closestTarget(unit.team, unit.x, unit.y, unit.range() * engageRangeMul,
                    u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround),
                    b -> unit.type.targetGround);
            }
            enemy = target;
        }

        // ============ 2. 移动 ============
        Unit transport = Units.closest(unit.team, targetPos.x, targetPos.y, transportFindRange,
            u -> u != unit
                && u.isValid()
                && (u.type instanceof CarrierUnitType)
                && !CarrierManager.isFull(u)
                && CarrierManager.accepts(u, unit.type));

        if (transport == null) {
            // 附近没车，继续走向命令点
            Tmp.v1.set(targetPos.x, targetPos.y);
            moveTo(Tmp.v1, 0f, smoothing());
            faceToward(targetPos.x, targetPos.y);
        } else {
            float dst = unit.dst(transport);
            if (dst <= boardRange) {
                // 到达范围，尝试进车
                if (CarrierManager.tryAttach(transport, unit)) {
                    unit.controller(new AttachedAI(transport));
                }
                return;
            }

            // 走向车
            Tmp.v1.set(transport.x, transport.y);
            moveTo(Tmp.v1, 0f, smoothing());
            faceToward(transport.x, transport.y);
        }

        // ============ 3. 开火（不打断移动）============
        if (enemy != null) {
            unit.aim(enemy.getX(), enemy.getY());
            unit.controlWeapons(true);
        } else {
            unit.controlWeapons(false);
        }
    }

    /** 平滑转向目标点。 */
    private void faceToward(float x, float y) {
        float ang = Mathf.atan2(y - unit.y, x - unit.x) * Mathf.radDeg;
        unit.rotation = Mathf.slerpDelta(unit.rotation, ang, turnSpeed);
    }

    @Override
    public boolean retarget() {
        return timer.get(timerTarget, retargetInterval);
    }
}