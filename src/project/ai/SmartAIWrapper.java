package project.ai;

import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;

public class SmartAIWrapper extends AIController {

    public final AIController delegate;

    // 战斗层参数
    public static float engageFactor = 0.85f;
    public static float retreatFactor = 0.55f;
    public static float sideSpreadStrength = 0.6f;

    // 卡住检测
    public static float stuckThreshold = 1.5f;
    public static float minMovePerFrame = 0.5f;
    public static float rescueSpeedMul = 1.5f;

    protected float lastX = Float.NaN, lastY = Float.NaN;
    protected float stuckTimer = 0f;

    /** 用于按 AITuning.targetInterval 强制 delegate 重索敌 */
    protected float retargetTimer = 0f;

    public SmartAIWrapper(AIController delegate) {
        this.delegate = delegate;
    }

    @Override
    public void updateUnit() {
        if (delegate != null) {
            if (delegate.unit() != unit) delegate.unit(unit);

            // ★ 根据 AITuning.targetInterval 定时强制 delegate 重新索敌
            retargetTimer += Time.delta;
            if (retargetTimer >= AITuning.targetInterval) {
                retargetTimer = 0f;
                delegate.timer.reset(AIController.timerTarget, 1000f);
            }

            delegate.updateUnit();
        }

        applyCombatLayer();
        antiStuck();
    }

    protected void applyCombatLayer() {
        if (unit == null || !unit.isAdded()) return;
        if (unit.type.weapons.isEmpty()) return;
        if (unit.range() <= 0f) return;

        Teamc enemy = Units.closestTarget(unit.team, unit.x, unit.y,
            unit.range() * 1.5f,
            u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround),
            b -> unit.type.targetGround);
        if (enemy == null) return;

        float dst = unit.dst(enemy);
        float range = Math.max(unit.range(), 40f);
        float engage = range * engageFactor;
        float retreat = range * retreatFactor;

        if (dst > engage) return;

        float speed = unit.speed();
        float angleToEnemy = Mathf.angle(enemy.getX() - unit.x, enemy.getY() - unit.y);

        if (dst < retreat) {
            Tmp.v1.trns(angleToEnemy + 180f, speed);
            unit.movePref(Tmp.v1);
        } else {
            float sideSign = (unit.id % 2 == 0) ? 1f : -1f;
            float jitter = Mathf.sin(Time.time * 0.05f + unit.id * 0.37f) * 15f;
            Tmp.v1.trns(angleToEnemy + 90f * sideSign + jitter, speed * sideSpreadStrength);
            unit.movePref(Tmp.v1);
        }

        if (!unit.type.omniMovement) {
            unit.lookAt(enemy);
        }
    }

    protected void antiStuck() {
        if (unit == null || !unit.isAdded()) return;

        if (Float.isNaN(lastX)) {
            lastX = unit.x;
            lastY = unit.y;
            return;
        }

        float moved = Mathf.dst(unit.x, unit.y, lastX, lastY);
        boolean wantsToMove = unit.vel.len2() > 0.01f;

        if (wantsToMove && moved < minMovePerFrame * Time.delta) {
            stuckTimer += Time.delta;
        } else {
            stuckTimer = 0f;
        }

        lastX = unit.x;
        lastY = unit.y;

        if (stuckTimer > 60f * stuckThreshold) {
            stuckTimer = 0f;
            Tmp.v1.trns(Mathf.random(360f), unit.speed() * rescueSpeedMul);
            unit.movePref(Tmp.v1);
        }
    }

    @Override
    public void removed(Unit unit) {
        super.removed(unit);
        if (delegate != null) delegate.removed(unit);
    }
}