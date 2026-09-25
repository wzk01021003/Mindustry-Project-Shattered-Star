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

public class ProtectAI extends AIController {

    // ============ 通用参数 ============
    public static float engageRange = 220f;
    public static float followDist = 60f;
    public static float pushContact = 20f;
    public static float shieldOffset = 45f;

    public static float groundSmoothing = 100f;
    public static float flySmoothing = 40f;
    public static float directApproachDist = 40f;

    public static float stuckThreshold = 1.5f;
    public static float minMovePerFrame = 0.5f;
    public static float rescueSpeedMul = 1.5f;

    // ============ 编队参数 ============
    public static float slotRadius = 70f;
    public static float slotAngleOffset = 137.508f;
    public static float attackOffset = 18f;
    public static float separationMul = 2.8f;
    public static float separationStrength = 0.8f;
    public static float anchorFollowRange = 40f;

    // ============ 运行时状态 ============
    public float anchorX, anchorY;
    public boolean hasAnchor = false;
    public Teamc anchorTarget = null;

    public ProtectModes mode;
    public boolean hasWeapons = false;
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
        hasWeapons = !unit.type.weapons.isEmpty() && unit.range() > 0f;
    }

    protected float slotAngle() {
        return (unit.id * slotAngleOffset) % 360f;
    }

    protected void getSlotPos(float centerX, float centerY, Vec2 out) {
        float ang = slotAngle();
        out.set(centerX + Angles.trnsx(ang, slotRadius),
                centerY + Angles.trnsy(ang, slotRadius));
    }

    protected void refreshAnchor() {
        if (unit == null) return;
        var c = unit.controller();
        if (!(c instanceof CommandAI)) return;
        CommandAI cai = (CommandAI) c;

        if (cai.attackTarget != null && !cai.attackTarget.equals(unit)) {
            anchorTarget = cai.attackTarget;
            anchorX = cai.attackTarget.getX();
            anchorY = cai.attackTarget.getY();
            hasAnchor = true;
            return;
        }

        if (cai.targetPos != null) {
            if (!hasAnchor) {
                anchorX = cai.targetPos.x;
                anchorY = cai.targetPos.y;
                hasAnchor = true;

                Unit nearby = Units.closest(unit.team, anchorX, anchorY, anchorFollowRange,
                    u -> u != unit && u.isValid());
                if (nearby != null) anchorTarget = nearby;
            }

            if (anchorTarget instanceof Unit) {
                Unit u = (Unit) anchorTarget;
                if (u.isValid()) {
                    anchorX = u.x;
                    anchorY = u.y;
                }
            }
        }
    }

    @Override
    public void updateMovement() {
        ensureInit();
        refreshAnchor();

        if (hasAnchor && hasWeapons) {
            if (retarget() || target == null
                || Units.invalidateTarget(target, unit.team, unit.x, unit.y, Float.MAX_VALUE)) {
                target = Units.closestTarget(unit.team, anchorX, anchorY, engageRange,
                    u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround),
                    b -> unit.type.targetGround);
            }
        } else {
            target = null;
        }

        Teamc enemy = target;

        switch (mode) {
            case PUSH:    doPush(enemy);    break;
            case SHIELD:  doShield(enemy);  break;
            case PASSIVE: doPassive();      break;
            case ATTACK:
            default:      doAttack(enemy);  break;
        }

        applySeparation();

        if (!isFlying() && unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.descentSpeed);
        }

        if (enemy != null && hasWeapons) {
            unit.lookAt(enemy);
            unit.aim(enemy.getX(), enemy.getY());
            unit.controlWeapons(true);
        } else {
            if (hasWeapons) unit.controlWeapons(false);
            faceMyMovement();
        }

        antiStuck();
    }

    @Override
    public boolean retarget() {
        return timer.get(timerTarget, AITuning.targetInterval);
    }

    protected void faceMyMovement() {
        if (unit.vel.len2() > 0.01f) {
            unit.lookAt(unit.vel.angle());
        }
    }

    protected void applySeparation() {
        float sepRadius = unit.hitSize * separationMul;
        sepAccum.setZero();

        Units.nearby(unit.team, unit.x, unit.y, sepRadius, other -> {
            if (other == unit) return;
            if (!AIUtils.sameMovementClass(unit, other)) return;  // ← 只对同类分散
            if (!(other.controller() instanceof ProtectAI)) return;

            float dst = unit.dst(other);
            float minDist = (unit.hitSize + other.hitSize) / 2f + 6f;
            if (dst < minDist && dst > 0.01f) {
                float strength = (minDist - dst) / minDist;
                sepAccum.add((unit.x - other.x) / dst * strength,
                             (unit.y - other.y) / dst * strength);
            }
        });

        if (sepAccum.len2() > 0.001f) {
            sepAccum.setLength(unit.speed() * separationStrength);
            unit.vel.add(sepAccum);
        }
    }

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

    protected void doAttack(Teamc enemy) {
        if (!hasWeapons) { doPassive(); return; }

        if (enemy != null) {
            float dst = unit.dst(enemy);
            float range = Math.max(unit.range(), 40f);
            float engage = range * 0.85f;

            if (dst > engage) {
                float ang = slotAngle();
                float ox = Angles.trnsx(ang, attackOffset);
                float oy = Angles.trnsy(ang, attackOffset);
                pathTowards(enemy.getX() + ox, enemy.getY() + oy, engage);
            }
        } else {
            getSlotPos(anchorX, anchorY, targetVec);
            pathTowards(targetVec.x, targetVec.y, 5f);
        }
    }

    protected void doPush(Teamc enemy) {
        if (hasWeapons) { doAttack(enemy); return; }

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
            Tmp.v1.set(enemy.getX() - anchorX, enemy.getY() - anchorY).setLength(shieldOffset);
            float ang = slotAngle();
            float ox = Angles.trnsx(ang, 15f);
            float oy = Angles.trnsy(ang, 15f);
            float tx = anchorX + Tmp.v1.x + ox;
            float ty = anchorY + Tmp.v1.y + oy;

            float dst = unit.dst(tx, ty);
            if (dst > 15f) pathTowards(tx, ty, 10f);
        } else {
            getSlotPos(anchorX, anchorY, targetVec);
            pathTowards(targetVec.x, targetVec.y, 5f);
        }
    }

    protected void doPassive() {
        getSlotPos(anchorX, anchorY, targetVec);
        pathTowards(targetVec.x, targetVec.y, 5f);
    }

    protected void antiStuck() {
        if (unit == null || !unit.isAdded()) return;

        if (Float.isNaN(lastX)) {
            lastX = unit.x; lastY = unit.y;
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