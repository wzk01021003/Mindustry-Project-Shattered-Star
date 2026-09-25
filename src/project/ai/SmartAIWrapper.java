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
 *
 * 关键原则：距离大于 engage 时完全不介入，让原 AI 自由寻路接近。
 *          只有进入射程后才接管移动向量。
 */
public class SmartAIWrapper extends AIController {

    public final AIController delegate;

    // ============ 战斗层参数 ============
    /** 进入"交战接管"的距离 = 射程 × 此系数 */
    public static float engageFactor = 0.85f;
    /** 后退距离 = 射程 × 此系数 */
    public static float retreatFactor = 0.55f;
    /** 横向分散速度比例（0~1） */
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

        // 2. 叠加战斗层（只影响移动向量，不改目标 / 不改寻路）
        applyCombatLayer();

        // 3. 兜底：卡住检测
        antiStuck();
    }

    /**
     * 战斗层：
     *  - 距离 > engage：不介入，原 AI 继续寻路接近
     *  - 距离在 (retreat, engage)：横向移动，保持距离 + 分散
     *  - 距离 < retreat：后退，拉开距离
     */
    protected void applyCombatLayer() {
        if (unit == null || !unit.isAdded()) return;
        if (unit.type.weapons.isEmpty) return;
        if (unit.range() <= 0f) return;

        // 找最近敌人（单位或建筑）
        Teamc enemy = Units.closestTarget(unit.team, unit.x, unit.y,
            unit.range() * 1.5f,
            u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround),
            b -> unit.type.targetGround);
        if (enemy == null) return;

        float dst = unit.dst(enemy);
        float range = Math.max(unit.range(), 40f);
        float engage = range * engageFactor;
        float retreat = range * retreatFactor;

        // 距离还远 → 原 AI 处理接近（它自己会寻路绕墙）
        if (dst > engage) return;

        float speed = unit.speed();
        float angleToEnemy = Mathf.angle(enemy.getX() - unit.x, enemy.getY() - unit.y);

        if (dst < retreat) {
            // 太近：往反方向后退
            Tmp.v1.trns(angleToEnemy + 180f, speed);
            unit.movePref(Tmp.v1);
        } else {
            // 范围内：横向移动，用 unit.id 决定左右，避免所有单位往同一侧挤
            float sideSign = (unit.id % 2 == 0) ? 1f : -1f;
            // 加一点 id 相关的微偏移，让轨迹不完全同步
            float jitter = Mathf.sin(Time.time * 0.05f + unit.id * 0.37f) * 15f;
            Tmp.v1.trns(angleToEnemy + 90f * sideSign + jitter, speed * sideSpreadStrength);
            unit.movePref(Tmp.v1);
        }

        // 非全向单位需要朝向敌人才能开火
        if (!unit.type.omniMovement) {
            unit.lookAt(enemy);
        }
    }

    /** 卡住检测：想动但没动 → 累积计时 → 触发救援 */
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

    /** 救援：随机方向推一下，让原 AI 重新规划 */
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