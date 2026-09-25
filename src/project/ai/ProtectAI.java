package project.ai;

import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import project.content.ProtectModeRegistry;
import project.content.ProtectModes;

/**
 * 保护 AI：
 *  - 玩家点"保护"命令 + 点一个友方建筑/单位 → 单位去保护它
 *  - 行为按 ProtectModes 分四种：ATTACK / PUSH / SHIELD / PASSIVE
 *  - 完全不动原版寻路：只在距离内做行为覆盖
 */
public class ProtectAI extends AIController {

    // ============ 静态参数 ============
    public static float followDist = 60f;
    public static float engageRange = 220f;
    public static float pushContact = 20f;
    public static float shieldOffset = 45f;
    public static float retargetInterval = 15f;

    public static float stuckThreshold = 1.5f;
    public static float minMovePerFrame = 0.5f;
    public static float rescueSpeedMul = 1.5f;

    // ============ 运行时状态 ============
    public float anchorX, anchorY;
    public boolean hasAnchor = false;

    public ProtectModes mode;
    protected boolean initialized = false;

    protected float lastX = Float.NaN, lastY = Float.NaN;
    protected float stuckTimer = 0f;

    protected void ensureInit() {
        if (initialized) return;
        initialized = true;
        mode = ProtectModeRegistry.get(unit.type);
    }

    @Override
    public void updateMovement() {
        ensureInit();

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

        if (!unit.type.flying && unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.descentSpeed);
        }

        antiStuck();
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
                moveTo(enemy, engage, 100f);
            }
        } else {
            // moveTo(x, y, range, smooth, stopAtTarget)
            moveTo(anchorX, anchorY, followDist, 100f, true);
        }
    }

    /** 推开：无武器单位冲向敌人，用物理碰撞推走 */
    protected void doPush(Teamc enemy) {
        if (enemy != null) {
            float dst = unit.dst(enemy);

            if (dst > pushContact) {
                Tmp.v1.set(enemy.getX(), enemy.getY()).sub(unit).setLength(unit.speed());
                unit.movePref(Tmp.v1);
            } else {
                Tmp.v1.set(enemy.getX(), enemy.getY()).sub(unit).setLength(unit.speed() * 1.5f);
                unit.movePref(Tmp.v1);
            }
            unit.lookAt(enemy);
        } else {
            moveTo(anchorX, anchorY, followDist, 100f, true);
        }
    }

    /** 护盾：挡在锚点和敌人之间 */
    protected void doShield(Teamc enemy) {
        if (enemy != null) {
            Tmp.v1.set(enemy.getX() - anchorX, enemy.getY() - anchorY).setLength(shieldOffset);
            Tmp.v2.set(anchorX + Tmp.v1.x, anchorY + Tmp.v1.y);

            float dst = unit.dst(Tmp.v2.x, Tmp.v2.y);

            if (dst > 15f) {
                moveTo(Tmp.v2.x, Tmp.v2.y, 10f, 100f, true);
            } else {
                unit.lookAt(enemy);
            }
        } else {
            moveTo(anchorX, anchorY, followDist * 0.6f, 100f, true);
        }
    }

    /** 被动：只跟随锚点 */
    protected void doPassive() {
        moveTo(anchorX, anchorY, followDist, 100f, true);
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