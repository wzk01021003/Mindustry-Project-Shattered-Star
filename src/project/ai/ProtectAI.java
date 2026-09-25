package project.ai;

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
 *  - 行为按 ProtectModes 分四种：ATTACK / PUSH / SHIELD / PASSIVE
 *  - 非飞行单位用 ControlPathfinder 显式寻路（会绕开墙 / 建筑）
 */
public class ProtectAI extends AIController {

    // ============ 静态参数 ============
    public static float followDist = 60f;
    public static float engageRange = 220f;
    public static float pushContact = 20f;
    public static float shieldOffset = 45f;

    /** 非飞行 moveTo 的 smoothing，100f 与原版 CommandAI 一致 */
    public static float groundSmoothing = 100f;
    /** 飞行 moveTo 的 smoothing */
    public static float flySmoothing = 40f;
    /** 距离终点小于这个值直接 moveTo，不走寻路 */
    public static float directApproachDist = 40f;

    public static float stuckThreshold = 1.5f;
    public static float minMovePerFrame = 0.5f;
    public static float rescueSpeedMul = 1.5f;

    // ============ 运行时状态 ============
    public float anchorX, anchorY;
    public boolean hasAnchor = false;

    public ProtectModes mode;
    protected boolean initialized = false;

    protected final Vec2 targetVec = new Vec2();

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

    /** 从外层 CommandAI 读取玩家点的目标坐标 */
    protected void refreshAnchor() {
        if (unit == null) return;
        var c = unit.controller();
        if (c instanceof CommandAI cai && cai.targetPos != null) {
            anchorX = cai.targetPos.x;
            anchorY = cai.targetPos.y;
            hasAnchor = true;
        }
    }

    @Override
    public void updateMovement() {
        ensureInit();
        refreshAnchor();

        if (!hasAnchor) return;

        Teamc enemy = Units.closestTarget(unit.team, anchorX, anchorY, engageRange,
            u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround),
            b -> unit.type.targetGround);

        switch (mode) {
            case PUSH:    doPush(enemy);    break;
            case SHIELD:  doShield(enemy);  break;
            case PASSIVE: doPassive();      break;
            case ATTACK:
            default:      doAttack(enemy);  break;
        }

        if (!isFlying() && unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.descentSpeed);
        }

        antiStuck();
    }

    // ================================================================
    //  寻路核心：目标远就调 pathfinder，目标近才直接 moveTo
    // ================================================================

    /**
     * 走向 (x, y)。飞行单位直接 moveTo；地面单位远距离时显式寻路绕墙。
     * @param range 保留的距离（离目标多远停下）
     */
    protected void pathTowards(float x, float y, float range) {
        // 飞行：直线
        if (isFlying()) {
            targetVec.set(x, y);
            moveTo(targetVec, range, flySmoothing);
            return;
        }

        // 距离目标近：直接冲
        float distToTarget = Mathf.dst(unit.x, unit.y, x, y);
        if (distToTarget <= directApproachDist) {
            targetVec.set(x, y);
            moveTo(targetVec, range, groundSmoothing);
            return;
        }

        // 远：请求寻路
        Tmp.v2.set(x, y);
        var result = controlPath.getPathPosition(unit, Tmp.v2);

        if (result.move) {
            moveTo(result.dest, range, groundSmoothing);
        }
        // 找不到路径就什么都不做，下一帧再试
    }

    // ================================================================
    //  四种模式
    // ================================================================

    /** 常规攻击：站在锚点旁边 + 打范围内敌人 */
    protected void doAttack(Teamc enemy) {
        if (enemy != null) {
            float dst = unit.dst(enemy);
            float range = Math.max(unit.range(), 40f);
            float engage = range * 0.85f;

            if (dst <= engage) {
                unit.lookAt(enemy);
            } else {
                // 朝敌人走，也要寻路
                pathTowards(enemy.getX(), enemy.getY(), engage);
            }
        } else {
            pathTowards(anchorX, anchorY, followDist);
        }
    }

    /** 推开：无武器单位冲向敌人，用物理碰撞推走 */
    protected void doPush(Teamc enemy) {
        if (enemy != null) {
            float dst = unit.dst(enemy);

            // 推开需要贴脸，先寻路接近，最后 20 格直接冲
            if (dst > pushContact) {
                pathTowards(enemy.getX(), enemy.getY(), pushContact);
            } else {
                // 已经贴上：用力推
                Tmp.v1.set(enemy.getX(), enemy.getY()).sub(unit).setLength(unit.speed() * 1.5f);
                unit.movePref(Tmp.v1);
            }
            unit.lookAt(enemy);
        } else {
            pathTowards(anchorX, anchorY, followDist);
        }
    }

    /** 护盾：挡在锚点和敌人之间 */
    protected void doShield(Teamc enemy) {
        if (enemy != null) {
            // 锚点 → 敌人方向，偏移 shieldOffset 的位置
            Tmp.v1.set(enemy.getX() - anchorX, enemy.getY() - anchorY).setLength(shieldOffset);
            float tx = anchorX + Tmp.v1.x;
            float ty = anchorY + Tmp.v1.y;

            float dst = unit.dst(tx, ty);

            if (dst > 15f) {
                pathTowards(tx, ty, 10f);
            } else {
                unit.lookAt(enemy);
            }
        } else {
            pathTowards(anchorX, anchorY, followDist * 0.6f);
        }
    }

    /** 被动：只跟随锚点 */
    protected void doPassive() {
        pathTowards(anchorX, anchorY, followDist);
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