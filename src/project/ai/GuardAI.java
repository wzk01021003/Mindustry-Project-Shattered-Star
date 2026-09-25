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
 *  - 玩家点位置 → 单位以该点为圆心随机巡逻
 *  - 巡逻半径 = 单位最大武器射程 × 0.5
 *  - 索敌半径 = 单位最大武器射程
 *  - 用 ControlPathfinder 拿下一跳，绕墙前进
 *  - 地面单位 moveTo range = 0（原版 CommandAI 参数，最快）
 *  - 距离目标 < directApproachDist 时直接 moveTo 目标，省一次寻路
 */
public class GuardAI extends AIController {

    // ============ 行为参数 ============
    public static float roamMultiplier = 0.5f;
    public static float leashMultiplier = 1.3f;
    public static float minSearchRadius = 80f;
    public static float maxSearchRadius = 500f;

    public static float idleDuration = 60f;
    public static float minMoveDuration = 60f * 4f;
    public static float maxMoveDuration = 60f * 20f;
    public static float moveTimeMultiplier = 3f;
    public static float arrivalDist = 10f;

    // ============ 寻路参数 ============
    /** 地面单位 moveTo 的 smoothing（原版 CommandAI 用 100f） */
    public static float groundSmoothing = 100f;
    /** 飞行单位 moveTo 的 smoothing */
    public static float flySmoothing = 40f;
    /** 距离目标小于此值 → 直接 moveTo 目标，不走寻路 */
    public static float directApproachDist = 50f;
    public static int roamPickAttempts = 8;
    /** 连续寻路失败多少次才放弃 */
    public static int pathWaitTolerance = 30;

    // ============ 卡死检测 ============
    public static float stuckThreshold = 2f;
    public static float minMovePerFrame = 0.5f;
    public static float rescueSpeedMul = 1.5f;

    // ============ 状态 ============
    public enum State { IDLE, MOVING, COMBAT }
    public State state = State.IDLE;

    public float anchorX, anchorY;
    public boolean hasAnchor = false;

    public float targetX, targetY;
    public float stateTimer = 0f;
    public float currentMoveLimit = 60f * 6f;

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

        if (enemy != null && hasWeapons) {
            unit.aim(enemy.getX(), enemy.getY());
            unit.controlWeapons(true);
            if (!requestedMove) unit.lookAt(enemy);
        } else {
            if (hasWeapons) unit.controlWeapons(false);
        }

        if (!isFlying() && unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.descentSpeed);
        }

        antiStuck();
    }

    // ================================================================
    //  寻路 + 移动
    // ================================================================

    /**
     * 走向目标。
     * - 距离 < directApproachDist：直接 moveTo 目标
     * - 距离远：调 controlPath 拿下一跳，moveTo 下一跳
     * - range 参数用 0（地面）或 attack range（战斗），避免减速
     */
    protected boolean pathTowards(float x, float y) {
        float dist = Mathf.dst(unit.x, unit.y, x, y);

        // 快到了：直接 moveTo 目标
        if (dist <= directApproachDist) {
            tmpVec.set(x, y);
            if (!isOmni()) unit.lookAt(x, y);

            // ★ range = 0 → 不减到目标就停，冲刺到终点
            moveTo(tmpVec, 0f, isFlying() ? flySmoothing : groundSmoothing);
            return true;
        }

        // 远距离：走寻路
        Tmp.v2.set(x, y);
        var result = controlPath.getPathPosition(unit, Tmp.v2);

        if (result.move) {
            // 下一跳方向先 lookAt（non-omni 单位必须先对准才能 movePref）
            if (!isOmni()) {
                unit.lookAt(result.dest.x, result.dest.y);
            }
            // ★ range = 0 → 冲向下一个 waypoint
            moveTo(result.dest, 0f, isFlying() ? flySmoothing : groundSmoothing);
            return true;
        }

        // 首次异步请求返回 false 是正常的，等几帧
        return false;
    }

    protected void doMove(float tx, float ty) {
        float dist = Mathf.dst(unit.x, unit.y, tx, ty);

        if (dist <= arrivalDist) {
            state = State.IDLE;
            stateTimer = 0f;
            pathFailCount = 0;
            return;
        }

        if (stateTimer >= currentMoveLimit) {
            state = State.IDLE;
            stateTimer = 0f;
            pathFailCount = 0;
            return;
        }

        boolean moved = pathTowards(tx, ty);
        if (moved) {
            requestedMove = true;
            pathFailCount = 0;
        } else {
            pathFailCount++;
            if (pathFailCount >= pathWaitTolerance) {
                // 寻路真的失败：立即切换状态
                state = State.IDLE;
                stateTimer = 0f;
                pathFailCount = 0;
            }
        }
    }

    protected void doCombat(Teamc enemy) {
        float dst = unit.dst(enemy);
        float range = Math.max(unit.range(), 40f);
        float engage = range * 0.85f;

        if (dst > engage) {
            boolean moved = pathTowards(enemy.getX(), enemy.getY());
            if (moved) requestedMove = true;
        }
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

    protected void computeMoveLimit() {
        float dist = Mathf.dst(unit.x, unit.y, targetX, targetY);
        float speed = Math.max(unit.speed(), 0.05f);
        currentMoveLimit = Mathf.clamp(
            dist / speed * moveTimeMultiplier,
            minMoveDuration,
            maxMoveDuration);
    }

    protected void pickNewRoamTarget() {
        for (int attempt = 0; attempt < roamPickAttempts; attempt++) {
            float ang = Mathf.random(360f);
            float r = Mathf.random(roamRadius * 0.4f, roamRadius);
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
        }

        lastX = unit.x;
        lastY = unit.y;

        if (stuckTimer > 60f * stuckThreshold) {
            stuckTimer = 0f;
            state = State.IDLE;
            stateTimer = 0f;
            Tmp.v1.trns(Mathf.random(360f), unit.speed() * rescueSpeedMul);
            unit.movePref(Tmp.v1);
        }
    }
}