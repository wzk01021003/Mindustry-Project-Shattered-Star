package project.ai;

import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;

/**
 * 智能 AI 包装器：
 *  - 寻路 / 寻敌 / 目标选择：完全交给原版 AI（delegate）
 *  - 战斗层：进入射程后，控制距离（kiting）+ 横向分散
 *  - 兜底：卡死检测 + 救援推力
 */
public class SmartAIWrapper extends AIController {

    public final AIController delegate;

    // ============ 战斗层参数 ============
    public static float engageFactor = 0.85f;
    public static float retreatFactor = 0.55f;
    public static float sideSpreadStrength = 0.6f;

    // ============ 卡住检测参数 ============
    public static float stuckThreshold = 1.5f;
    public static float minMovePerFrame = 0.5f;
    public static float rescueSpeedMul = 1.5f;

    // ============ 运行时状态 ============
    protected float lastX = Float.NaN, lastY = Float.NaN;
    protected float stuckTimer = 0f;

    public SmartAIWrapper(AIController delegate) {
        this.delegate = delegate;
    }

    @Override
    public void updateUnit() {
        // 1. 原 AI 完全决定：寻敌、寻路、接近、开火
        if (delegate != null) {
            if (delegate.unit() != unit) delegate.unit(unit);
            delegate.updateUnit();
        }

        // 2. 叠加战斗层
        applyCombatLayer();

        // 3. 兜底：卡住检测
        antiStuck();
    }

    protected void applyCombatLayer() {
        if (unit == null || !unit.isAdded()) return;
        if (unit.type.weapons.isEmpty()) return;   // ← 就是这里
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
            rescue();
        }
    }

    protected void rescue() {
        if (unit == null) return;
        Tmp.v1.trns(Mathf.random(360f), unit.speed() * rescueSpeedMul);
        unit.movePref(Tmp.v1);
    }

    @Override
    public void removed(Unit unit) {
        super.removed(unit);
        if (delegate != null) {
            delegate.removed(unit);
        }
    }
}