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
import mindustry.type.Weapon;
import mindustry.world.Tile;

import static mindustry.Vars.*;

/**
 * 定点防御 AI：
 *  - 玩家点"定点防御" + 点位置 → 单位以该点为圆心巡逻
 *  - 巡逻半径 = 单位最大武器射程 × 0.5
 *  - 索敌半径 = 单位最大武器射程
 *  - IDLE 时保持静止（不摇头）
 *  - 移动时长根据单位速度动态计算，慢速单位也能走到目标
 *  - 距离锚点超过 leash → 强制回来
 *  - 巡逻选点会检查可达性，避免卡墙
 */
public class GuardAI extends AIController {

    // ============ 行为参数 ============
    public static float roamMultiplier = 0.5f;
    public static float leashMultiplier = 1.3f;
    public static float minSearchRadius = 80f;
    public static float maxSearchRadius = 500f;

    public static float idleDuration = 90f;
    /** 移动时间下限（帧）。60f = 1 秒 */
    public static float minMoveDuration = 60f * 3f;
    /** 移动时间上限（帧）。防止慢单位/卡死时无限走 */
    public static float maxMoveDuration = 60f * 15f;
    /** 移动时间的计算系数：预期时间 × 此值 */
    public static float moveTimeMultiplier = 3f;
    /** 到达判定距离 */
    public static float arrivalDist = 8f;

    // ============ 寻路参数 ============
    public static float groundSmoothing = 100f;
    public static float flySmoothing = 40f;
    public static float directApproachDist = 40f;
    public static int roamPickAttempts = 8;
    public static int pathWaitTolerance = 15;

    // ============ 卡死检测 ============
    public static float stuckThreshold = 1.5f;
    public static float minMovePerFrame = 0.5f;
    public static float rescueSpeedMul = 1.5f;
    public static int hardStuckAfterRescues = 3;

    // ============ 状态 ============
    public enum State { IDLE, MOVING, COMBAT }
    public State state = State.IDLE;

    public float anchorX, anchorY;
    public boolean hasAnchor = false;

    public float targetX, targetY;
    public float stateTimer = 0f;
    /** 本周期允许的最大移动时间（帧），根据 unit speed 动态算 */
    public float currentMoveLimit = 60f * 4f;

    public boolean hasWeapons = false;
    public float searchRadius = 220f;
    public float roamRadius = 110f;
    public float leashRange = 286f;

    protected boolean requestedMove = false;
    protected int pathFailCount = 0;

    protected boolean initialized = false;
    protected final Vec2 tmpVec = new Vec2();

    protected float lastX = Float.NaN, lastY = Float.NaN;
    protected float stuckTimer = 0f;
    protected int rescueCount = 0;

    protected boolean isFlying() { return unit.type.flying; }
    protected boolean isOmni() { return unit.type.omniMovement; }

    // ================================================================
    //  武器射程
    // ================================================================

    protected static float weaponRange(Weapon w) {
        if (w == null || w.bullet == null) return 0f;
        if (w.bullet.rangeOverride > 0f) return w.bullet.rangeOverride;
        return w.bullet.lifetime * w.bullet.speed;
    }

    public static float maxWeaponRange(mindustry.type.UnitType type) {
        if (type.weapons.isEmpty()) return 0f;
        float max = 0f;
        for (int i = 0; i < type.weapons.size; i++) {
            float r = weaponRange(type.weapons.get(i));
            if (r > max) max = r;
        }
        return max;
    }

    protected void ensureInit() {
        if (initialized) return;
        initialized = true;
        hasWeapons = !unit.type.weapons.isEmpty();

        float r = maxWeaponRange(unit.type);
        if (r <= 0f) r = minSearchRadius;

        searchRadius = Mathf.clamp(r, minSearchRadius, maxSearchRadius);
        roamRadius = searchRadius * roamMultiplier;
        leashRange = searchRadius * leashMultiplier;
    }

    // ================================================================
    //  锚点
    // ================================================================

    protected void refreshAnchor() {
        if (unit == null) return;
        var c = unit.controller();
        if (!(c instanceof CommandAI)) return;
        CommandAI cai = (CommandAI) c;

        if (cai.targetPos != null) {
            anchorX = cai.targetPos.x;
            anchorY = cai.targetPos.y;
            hasAnchor = true;
        }
    }

    // ================================================================
    //  主循环
    // ================================================================

    @Override
    public void updateMovement() {
        ensureInit();
        refreshAnchor();

        if (!hasAnchor) {
            faceMyMovement();
            return;
        }

        if (hasWeapons) {
            if (retarget() || target == null
                || Units.invalidateTarget(target, unit.team, unit.x, unit.y, Float.MAX_VALUE)) {
                target = Units.closestTarget(unit.team, anchorX, anchorY, searchRadius,
                    u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround),
                    b -> unit.type.targetGround);
            }
        } else {
            target = null;
        }

        Teamc enemy = target;
        float distToAnchor = Mathf.dst(unit.x, unit.y, anchorX, anchorY);
        boolean overLeash = distToAnchor > leashRange;

        requestedMove = false;

        if (enemy != null && hasWeapons) {
            if (state != State.COMBAT) {
                state = State.COMBAT;
                stateTimer = 0f;
            }
            doCombat(enemy);
        } else if (overLeash) {
            state = State.MOVING;
            targetX = anchorX;
            targetY = anchorY;
            stateTimer += Time.delta;
            doMove(targetX, targetY);
        } else {
            if (state == State.COMBAT) {
                state = State.IDLE;
                stateTimer = 0f;
            }
            stateTimer += Time.delta;
            if (state == State.IDLE) doIdle();
            else doMove(targetX, targetY);
        }

        // ============ 朝向 ============
        if (enemy != null && hasWeapons) {
            unit.aim(enemy.getX(), enemy.getY());
            unit.controlWeapons(true);
            if (!requestedMove) unit.lookAt(enemy);
        } else {
            if (hasWeapons) unit.controlWeapons(false);

            // IDLE：什么都不做，保持当前 rotation（不摇头）
            // MOVING：朝目标方向
            if (state == State.MOVING) {
                unit.lookAt(targetX, targetY);
            }
        }

        if (!isFlying() && unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.descentSpeed);
        }

        antiStuck();
    }

    // ================================================================
    //  状态机
    // ================================================================

    protected void doIdle() {
        if (stateTimer >= idleDuration) {
            pickNewRoamTarget();
            state = State.MOVING;
            stateTimer = 0f;
            pathFailCount = 0;
            computeMoveLimit();
        }
    }

    /** 根据当前目标距离 + 单位速度，动态计算允许的移动时长 */
    protected void computeMoveLimit() {
        float dist = Mathf.dst(unit.x, unit.y, targetX, targetY);
        float speed = Math.max(unit.speed(), 0.05f);   // 防止除 0
        float expectedFrames = dist / speed;
        currentMoveLimit = Mathf.clamp(
            expectedFrames * moveTimeMultiplier,
            minMoveDuration,
            maxMoveDuration);
    }

    protected void pickNewRoamTarget() {
        for (int attempt = 0; attempt < roamPickAttempts; attempt++) {
            float ang = Mathf.random(360f);
            float r = Mathf.random(roamRadius * 0.6f, roamRadius);
            float tx = anchorX + Angles.trnsx(ang, r);
            float ty = anchorY + Angles.trnsy(ang, r);

            if (isTilePassable(tx, ty)) {
                targetX = tx;
                targetY = ty;
                return;
            }
        }
        targetX = anchorX;
        targetY = anchorY;
    }

    protected boolean isTilePassable(float wx, float wy) {
        int tx = world.toTile(wx), ty = world.toTile(wy);
        Tile t = world.tile(tx, ty);
        if (t == null) return false;
        if (t.solid()) return false;
        if (!isFlying() && t.floor().isDeep()) return false;
        return true;
    }

    protected void doMove(float tx, float ty) {
        float dist = Mathf.dst(unit.x, unit.y, tx, ty);

        if (dist <= arrivalDist) {
            state = State.IDLE;
            stateTimer = 0f;
            pathFailCount = 0;
            return;
        }

        // 超时或到达 → IDLE
        if (stateTimer >= currentMoveLimit) {
            state = State.IDLE;
            stateTimer = 0f;
            pathFailCount = 0;
            return;
        }

        boolean moved = pathTowards(tx, ty, 5f);
        if (moved) {
            requestedMove = true;
            pathFailCount = 0;
        } else {
            pathFailCount++;
            if (pathFailCount >= pathWaitTolerance) {
                state = State.IDLE;
                stateTimer = 0f;
                pathFailCount = 0;
                rescueCount++;
            }
        }
    }

    protected void doCombat(Teamc enemy) {
        float dst = unit.dst(enemy);
        float range = Math.max(unit.range(), 40f);
        float engage = range * 0.9f;

        if (dst > engage) {
            boolean moved = pathTowards(enemy.getX(), enemy.getY(), engage);
            if (moved) requestedMove = true;
        }
    }

    // ================================================================
    //  工具
    // ================================================================

    @Override
    public boolean retarget() {
        return timer.get(timerTarget, 20f);
    }

    protected void faceMyMovement() {
        if (unit.vel.len2() > 0.01f) {
            unit.lookAt(unit.vel.angle());
        }
    }

    protected boolean pathTowards(float x, float y, float range) {
        if (isFlying()) {
            tmpVec.set(x, y);
            moveTo(tmpVec, range, flySmoothing);
            return true;
        }

        float distToTarget = Mathf.dst(unit.x, unit.y, x, y);
        if (distToTarget <= directApproachDist) {
            tmpVec.set(x, y);
            if (!isOmni()) unit.lookAt(x, y);
            moveTo(tmpVec, range, groundSmoothing);
            return true;
        }

        Tmp.v2.set(x, y);
        var result = controlPath.getPathPosition(unit, Tmp.v2);

        if (result.move) {
            if (!isOmni()) unit.lookAt(result.dest.x, result.dest.y);
            moveTo(result.dest, range, groundSmoothing);
            return true;
        }
        return false;
    }

    // ================================================================
    //  卡死检测
    // ================================================================

    protected void antiStuck() {
        if (unit == null || !unit.isAdded()) return;
        if (Float.isNaN(lastX)) { lastX = unit.x; lastY = unit.y; return; }

        float moved = Mathf.dst(unit.x, unit.y, lastX, lastY);
        boolean wantsToMove = unit.vel.len2() > 0.01f;

        if (wantsToMove && moved < minMovePerFrame * Time.delta) {
            stuckTimer += Time.delta;
        } else {
            stuckTimer = 0f;
            if (moved > minMovePerFrame * Time.delta) rescueCount = 0;
        }

        lastX = unit.x;
        lastY = unit.y;

        if (stuckTimer > 60f * stuckThreshold) {
            stuckTimer = 0f;
            rescueCount++;

            if (rescueCount >= hardStuckAfterRescues) {
                rescueCount = 0;
                state = State.MOVING;
                stateTimer = 0f;
                targetX = anchorX;
                targetY = anchorY;
                computeMoveLimit();
            }

            Tmp.v1.trns(Mathf.random(360f), unit.speed() * rescueSpeedMul);
            unit.movePref(Tmp.v1);
        }
    }
}