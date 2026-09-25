package project.ai;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.ai.types.CommandAI;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import project.content.ProtectModeRegistry;
import project.content.ProtectModes;

import static mindustry.Vars.*;

/**
 * 保护 AI：
 *  - 玩家点"保护"命令 + 点友方目标 → 单位去保护它
 *  - 锚点可以从 CommandAI.targetPos 或 attackTarget 读取
 *  - 行为按 ProtectModes 分四种：ATTACK / PUSH / SHIELD / PASSIVE
 *  - 非飞行单位用 ControlPathfinder 显式寻路
 *  - 编队：每个单位按 id 分配环绕锚点的槽位 + 分离力，避免互相挤压
 */
public class ProtectAI extends AIController {

    // ============ 通用参数 ============
    public static float engageRange = 220f;
    public static float followDist = 60f;
    public static float pushContact = 20f;
    public static float shieldOffset = 45f;
    public static float retargetInterval = 15f;

    public static float groundSmoothing = 100f;
    public static float flySmoothing = 40f;
    public static float directApproachDist = 40f;

    public static float stuckThreshold = 1.5f;
    public static float minMovePerFrame = 0.5f;
    public static float rescueSpeedMul = 1.5f;

    // ============ 编队参数 ============
    /** 环绕锚点的槽位半径 */
    public static float slotRadius = 70f;
    /** 槽位角度偏移（度），每个单位按黄金角分布 */
    public static float slotAngleOffset = 137.508f;
    /** 攻击目标时的额外偏移距离 */
    public static float attackOffset = 18f;
    /** 分离力检测半径倍率（相对于自身 hitSize） */
    public static float separationMul = 2.8f;
    /** 分离力强度（0~1） */
    public static float separationStrength = 0.8f;

    // ============ 运行时状态 ============
    public float anchorX, anchorY;
    public boolean hasAnchor = false;
    public Teamc anchorTarget = null;

    public ProtectModes mode;
    protected boolean initialized = false;

    protected final Vec2 targetVec = new Vec2();
    protected final Vec2 sepAccum = new Vec2();

    protected float lastX = Float.NaN, lastY = Float.NaN;
    protected float stuckTimer = 0f;

    protected boolean isFlying() {
        return unit.type.flying;
    }

    protected void ensureInit() {
        if (initialized) return;
        initialized = true;
        mode = ProtectModeRegistry.get(unit.type);
    }

    // ================================================================
    //  编队槽位
    // ================================================================

    /**
     * 本单位的槽位角度。
     * 用黄金角（137.508°）乘 id，保证任意数量的单位都能均匀散开，
     * 且同一单位的槽位固定不变。
     */
    protected float slotAngle() {
        return (unit.id * slotAngleOffset) % 360f;
    }

    /** 计算本单位的槽位世界坐标 */
    protected void getSlotPos(float centerX, float centerY, Vec2 out) {
        float ang = slotAngle();
        out.set(centerX + Angles.trnsx(ang, slotRadius),
                centerY + Angles.trnsy(ang, slotRadius));
    }

    // ================================================================
    //  锚点
    // ================================================================

    protected void refreshAnchor() {
        if (unit == null) return;
        var c = unit.controller();
        if (!(c instanceof CommandAI cai)) return;

        // 1. 优先：attackTarget（点单位时存这里）
        if (cai.attackTarget != null && !cai.attackTarget.equals(unit)) {
            anchorX = cai.attackTarget.getX();
            anchorY = cai.attackTarget.getY();
            anchorTarget = cai.attackTarget;
            hasAnchor = true;
            return;
        }

        // 2. 其次：targetPos（点空地 / 建筑时存这里）
        if (cai.targetPos != null) {
            anchorX = cai.targetPos.x;
            anchorY = cai.targetPos.y;
            anchorTarget = null;
            hasAnchor = true;
        }
    }

    // ================================================================
    //  索敌
    // ================================================================

    @Override
    public void updateTargeting() {
        if (!hasAnchor) {
            target = null;
            return;
        }
        if (retarget()) {
            target = Units.closestTarget(unit.team, anchorX, anchorY, engageRange,
                u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround),
                b -> unit.type.targetGround);
        }
    }

    @Override
    public boolean retarget() {
        return timer.get(timerTarget, retargetInterval);
    }

    // ================================================================
    //  主循环
    // ================================================================

    @Override
    public void updateMovement() {
        ensureInit();
        refreshAnchor();

        if (!hasAnchor) {
            faceMovement();
            return;
        }

        Teamc enemy = target;

        switch (mode) {
            case PUSH:    doPush(enemy);    break;
            case SHIELD:  doShield(enemy);  break;
            case PASSIVE: doPassive();      break;
            case ATTACK:
            default:      doAttack(enemy);  break;
        }

        // 分离力：叠加速度，避免和其他护卫重叠
        applySeparation();

        if (!isFlying() && unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.descentSpeed);
        }

        // 朝向
        if (enemy != null) {
            unit.lookAt(enemy);
        } else {
            faceMovement();
        }

        antiStuck();
    }

    protected void faceMovement() {
        if (unit.vel.len2() > 0.01f) {
            unit.lookAt(unit.vel.angle());
        }
    }

    // ================================================================
    //  分离力：检测附近护卫，远离重叠
    // ================================================================

    protected void applySeparation() {
        float sepRadius = unit.hitSize * separationMul;
        sepAccum.setZero();

        Units.nearby(unit.team, unit.x, unit.y, sepRadius, other -> {
            if (other == unit) return;
            if (!(other.controller() instanceof ProtectAI)) return;

            float dst = unit.dst(other);
            float minDist = (unit.hitSize + other.hitSize) / 2f + 6f;
            if (dst < minDist && dst > 0.01f) {
                // 越近推力越大
                float strength = (minDist - dst) / minDist;
                sepAccum.add((unit.x - other.x) / dst * strength,
                             (unit.y - other.y) / dst * strength);
            }
        });

        if (sepAccum.len2() > 0.001f) {
            sepAccum.setLength(unit.speed() * separationStrength);
            // 直接加到速度上（不覆盖），movePref 会在最后统一截断
            unit.vel.add(sepAccum);
        }
    }

    // ================================================================
    //  寻路核心
    // ================================================================

    protected void pathTowards(float x, float y, float range) {
        if (isFlying()) {
            targetVec.set(x, y);
            moveTo(targetVec, range, flySmoothing);
            return;
        }

        float distToTarget = Mathf.dst(unit.x, unit.y, x, y);
        if (distToTarget <= directApproachDist) {
            targetVec.set(x, y);
            moveTo(targetVec, range, groundSmoothing);
            return;
        }

        Tmp.v2.set(x, y);
        var result = controlPath.getPathPosition(unit, Tmp.v2);

        if (result.move) {
            moveTo(result.dest, range, groundSmoothing);
        }
    }

    // ================================================================
    //  四种模式
    // ================================================================

    /** 攻击：追敌人走自己的槽位偏移；无敌人时回到锚点附近的槽位 */
    protected void doAttack(Teamc enemy) {
        if (enemy != null) {
            float dst = unit.dst(enemy);
            float range = Math.max(unit.range(), 40f);
            float engage = range * 0.85f;

            if (dst > engage) {
                // 追击时给每个单位一个角度偏移，避免全部挤向同一点
                float ang = slotAngle();
                float ox = Angles.trnsx(ang, attackOffset);
                float oy = Angles.trnsy(ang, attackOffset);
                pathTowards(enemy.getX() + ox, enemy.getY() + oy, engage);
            }
            // 射程内不动
        } else {
            // 无敌人：走向自己的槽位
            getSlotPos(anchorX, anchorY, targetVec);
            pathTowards(targetVec.x, targetVec.y, 5f);
        }
    }

    protected void doPush(Teamc enemy) {
        if (enemy != null) {
            float dst = unit.dst(enemy);

            if (dst > pushContact) {
                float ang = slotAngle();
                float ox = Angles.trnsx(ang, attackOffset);
                float oy = Angles.trnsy(ang, attackOffset);
                pathTowards(enemy.getX() + ox, enemy.getY() + oy, pushContact);
            } else {
                Tmp.v1.set(enemy.getX(), enemy.getY()).sub(unit).setLength(unit.speed() * 1.5f);
                unit.movePref(Tmp.v1);
            }
        } else {
            getSlotPos(anchorX, anchorY, targetVec);
            pathTowards(targetVec.x, targetVec.y, 5f);
        }
    }

    protected void doShield(Teamc enemy) {
        if (enemy != null) {
            // 锚点 → 敌人方向偏移 shieldOffset，再按槽位角度微调
            Tmp.v1.set(enemy.getX() - anchorX, enemy.getY() - anchorY).setLength(shieldOffset);
            float ang = slotAngle();
            float ox = Angles.trnsx(ang, 15f);
            float oy = Angles.trnsy(ang, 15f);
            float tx = anchorX + Tmp.v1.x + ox;
            float ty = anchorY + Tmp.v1.y + oy;

            float dst = unit.dst(tx, ty);
            if (dst > 15f) {
                pathTowards(tx, ty, 10f);
            }
        } else {
            getSlotPos(anchorX, anchorY, targetVec);
            pathTowards(targetVec.x, targetVec.y, 5f);
        }
    }

    protected void doPassive() {
        getSlotPos(anchorX, anchorY, targetVec);
        pathTowards(targetVec.x, targetVec.y, 5f);
    }

    // ================================================================
    //  卡死检测
    // ================================================================

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
}