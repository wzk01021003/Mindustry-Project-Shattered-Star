package project.ai;

import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.ai.types.FlyingAI;
import mindustry.gen.Teamc;

public class FastFlyingAI extends FlyingAI {

    public static float engageFactor = 0.85f;
    public static float retreatFactor = 0.55f;

    public static boolean strafeInRange = true;
    public static float sideSpreadStrength = 0.5f;
    public static float jitterAmp = 10f;

    public static float stuckThreshold = 1.5f;
    public static float minMovePerFrame = 0.5f;
    public static float rescueSpeedMul = 1.5f;

    protected float lastX = Float.NaN, lastY = Float.NaN;
    protected float stuckTimer = 0f;

    @Override
    public boolean retarget() {
        return timer.get(timerTarget, AITuning.targetInterval);
    }

    @Override
    public void updateMovement() {
        super.updateMovement();
        applyCombatLayer();
        forceFaceTarget();
        antiStuck();
    }

    protected void applyCombatLayer() {
        if (unit == null || !unit.isAdded()) return;
        if (unit.type.weapons.isEmpty()) return;

        Teamc enemy = target;
        if (enemy == null) return;

        float range = Math.max(AIUtils.minRange(unit.type), 40f);
        float engage = range * engageFactor;
        float retreat = range * retreatFactor;

        float dst = unit.dst(enemy);
        if (dst > engage) return;

        float speed = unit.speed();
        float angleToEnemy = Mathf.angle(enemy.getX() - unit.x, enemy.getY() - unit.y);

        if (dst < retreat) {
            Tmp.v1.trns(angleToEnemy + 180f, speed);
            unit.movePref(Tmp.v1);
        } else if (strafeInRange) {
            float sideSign = (unit.id % 2 == 0) ? 1f : -1f;
            float jitter = Mathf.sin(Time.time * 0.05f + unit.id * 0.37f) * jitterAmp;
            Tmp.v1.trns(angleToEnemy + 90f * sideSign + jitter, speed * sideSpreadStrength);
            unit.movePref(Tmp.v1);
        }
    }

    protected void forceFaceTarget() {
        if (target == null) return;
        if (unit.type.weapons.isEmpty()) return;
        if (unit.type.omniMovement) return;
        unit.lookAt(target);
    }

    protected void antiStuck() {
        if (unit == null || !unit.isAdded()) return;
        if (Float.isNaN(lastX)) { lastX = unit.x; lastY = unit.y; return; }

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
}