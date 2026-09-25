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

    public SmartAIWrapper(AIController delegate) {
        this.delegate = delegate;
    }

    @Override
    public void updateUnit() {
        // 1. 让 delegate 决定寻路 / 移动
        if (delegate != null) {
            if (delegate.unit() != unit) delegate.unit(unit);
            delegate.updateUnit();
        }

        // 2. wrapper 自己高频索敌（用 AITuning.targetInterval 控制频率）
        quickRetarget();

        // 3. 战斗层
        applyCombatLayer();

        // 4. 防卡死
        antiStuck();
    }

    /**
     * 用 wrapper 自己的 timer 做高频索敌，覆盖 delegate.target。
     * wrapper 是 AIController 子类，可以访问自己的 protected timer。
     */
    protected void quickRetarget() {
        if (unit == null) return;
        if (unit.type.weapons.isEmpty()) return;
        if (unit.range() <= 0f) return;

        if (retarget() || target == null
            || Units.invalidateTarget(target, unit.team, unit.x, unit.y, Float.MAX_VALUE)) {
            target = Units.closestTarget(unit.team, unit.x, unit.y,
                unit.range() * 1.5f,
                u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround),
                b -> unit.type.targetGround);

            // 同步给 delegate，让它的射击逻辑用新 target
            if (delegate != null) delegate.target = target;
        }
    }

    @Override
    public boolean retarget() {
        return timer.get(timerTarget, AITuning.targetInterval);
    }

    protected void applyCombatLayer() {
        if (unit == null || !unit.isAdded()) return;
        if (unit.type.weapons.isEmpty()) return;
        if (unit.range() <= 0f) return;

        // 用 wrapper 自己的 target（会同步给 delegate）
        Teamc enemy = target;
        if (enemy == null && delegate != null) enemy = delegate.target;
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