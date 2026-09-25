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
 * 定点防御 AI（简化版）：
 *  - 玩家点位置 → 单位以该点为圆心随机巡逻
 *  - 巡逻半径 = 单位最大武器射程 × 0.5
 *  - 索敌半径 = 单位最大武器射程
 *  - 用 moveTo 直接走，不走 pathfinder（简单直接）
 *  - 到达/超时 → 换新点
 */
public class GuardAI extends AIController {

    // ============ 行为参数 ============
    public static float roamMultiplier = 0.5f;
    public static float leashMultiplier = 1.3f;
    public static float minSearchRadius = 80f;
    public static float maxSearchRadius = 500f;

    /** 到达一个点后停留多久（帧） */
    public static float idleDuration = 90f;
    /** 移动时间下限（帧） */
    public static float minMoveDuration = 60f * 3f;
    /** 移动时间上限（帧） */
    public static float maxMoveDuration = 60f * 15f;
    /** 移动时间倍率 */
    public static float moveTimeMultiplier = 3f;
    /** 到达判定距离 */
    public static float arrivalDist = 10f;

    // ============ 寻路参数 ============
    public static float groundSmoothing = 100f;
    public static float flySmoothing = 40f;
    public static int roamPickAttempts = 8;

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
    public float currentMoveLimit = 60f * 4f;

    public boolean hasWeapons = false;
    public float searchRadius = 220f;
    public float roamRadius = 110f;
    public float leashRange = 286f;

    protected boolean requestedMove = false;
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

        // 索敌
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
            doMoveSimple();
        } else {
            if (state == State.COMBAT) {
                state = State.IDLE;
                stateTimer = 0f;
            }
            stateTimer += Time.delta;
            if (state == State.IDLE) doIdle();
            else doMoveSimple();
        }

        // 朝向
        if (enemy != null && hasWeapons) {
            unit.aim(enemy.getX(), enemy.getY());
            unit.controlWeapons(true);
            if (!requestedMove) unit.lookAt(enemy);
        } else {
            if (hasWeapons) unit.controlWeapons(false);
            if (state == State.MOVING) {
                unit.lookAt(targetX, targetY);
            }
        }

        // 落地
        if (!isFlying() && unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.descentSpeed);
        }

        antiStuck();
    }

    // ================================================================
    //  简单移动：直接 moveTo，不走 pathfinder
    // ================================================================

    /** 直接朝目标点走。用 moveTo 的直线移动（含轻微平滑避障） */
    protected void doMoveSimple() {
        float dist = Mathf.dst(unit.x, unit.y, targetX, targetY);

        // 到达
        if (dist <= arrivalDist) {
            state = State.IDLE;
            stateTimer = 0f;
            return;
        }

        // 超时
        if (stateTimer >= currentMoveLimit) {
            state = State.IDLE;
            stateTimer = 0f;
            return;
        }

        // non-omni 单位：先转向目标
        if (!isOmni()) {
            unit.lookAt(targetX, targetY);
        }

        // ★ 核心：直接 moveTo
        tmpVec.set(targetX, targetY);
        moveTo(tmpVec, arrivalDist, isFlying() ? flySmoothing : groundSmoothing);
        requestedMove = true;
    }

    // ================================================================
    //  状态机
    // ================================================================

    protected void doIdle() {
        if (stateTimer >= idleDuration) {
            pickNewRoamTarget();
            state = State.MOVING;
            stateTimer = 0f;
            computeMoveLimit();
        }
    }

    protected void computeMoveLimit() {
        float dist = Mathf.dst(unit.x, unit.y, targetX, targetY);
        float speed = Math.max(unit.speed(), 0.05f);
        float expectedFrames = dist / speed;
        currentMoveLimit = Mathf.clamp(
            expectedFrames * moveTimeMultiplier,
            minMoveDuration,
            maxMoveDuration);
    }

    /** ★ 直接在锚点周围随机抽一个坐标 */
    protected void pickNewRoamTarget() {
        for (int attempt = 0; attempt < roamPickAttempts; attempt++) {
            float ang = Mathf.random(360f);
            // 距离：从 40% 到 100% roamRadius 之间随取
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

    protected void doCombat(Teamc enemy) {
        float dst = unit.dst(enemy);
        float range = Math.max(unit.range(), 40f);
        float engage = range * 0.9f;

        if (dst > engage) {
            if (!isOmni()) unit.lookAt(enemy);
            tmpVec.set(enemy.getX(), enemy.getY());
            moveTo(tmpVec, engage, isFlying() ? flySmoothing : groundSmoothing);
            requestedMove = true;
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

            // 卡住：放弃当前目标，换新点
            state = State.IDLE;
            stateTimer = 0f;

            Tmp.v1.trns(Mathf.random(360f), unit.speed() * rescueSpeedMul);
            unit.movePref(Tmp.v1);
        }
    }
}