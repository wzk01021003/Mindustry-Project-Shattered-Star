package project.ai;

import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.ai.types.GroundAI;
import mindustry.gen.Teamc;

public class FastGroundAI extends GroundAI {

    public static float engageFactor = 0.85f;
    public static float retreatFactor = 0.55f;

    /** 是否在射程内横向移动（绕圈）。默认关闭，进射程就停下 */
    public static boolean strafeInRange = false;
    /** 横向移动强度（0~1），只有 strafeInRange = true 时才用 */
    public static float sideSpreadStrength = 0.4f;
    /** 横向移动的额外随机相位幅度（度） */
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
        antiStuck();
    }

    protected void applyCombatLayer() {
        if (unit == null || !unit.isAdded()) return;
        if (unit.type.weapons.isEmpty()) return;
        if (unit.range() <= 0f) return;

        Teamc enemy = target;
        if (enemy == null) return;

        float dst = unit.dst(enemy);
        float range = Math.max(unit.range(), 40f);
        float engage = range * engageFactor;
        float retreat = range * retreatFactor;

        // 太远：让原版 AI 继续接近
        if (dst > engage) return;

        float speed = unit.speed();
        float angleToEnemy = Mathf.angle(enemy.getX() - unit.x, enemy.getY() - unit.y);

        if (dst < retreat) {
            // 太近：后退（不管 strafeInRange 开不开，后退都生效）
            Tmp.v1.trns(angleToEnemy + 180f, speed);
            unit.movePref(Tmp.v1);
        } else if (strafeInRange) {
            // 射程内且开启横移：绕圈
            float sideSign = (unit.id % 2 == 0) ? 1f : -1f;
            float jitter = Mathf.sin(Time.time * 0.05f + unit.id * 0.37f) * jitterAmp;
            Tmp.v1.trns(angleToEnemy + 90f * sideSign + jitter, speed * sideSpreadStrength);
            unit.movePref(Tmp.v1);
        }
        // 否则：什么都不做，停下 —— 让它自然站在原地开火

        if (!unit.type.omniMovement) {
            unit.lookAt(enemy);
        }
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