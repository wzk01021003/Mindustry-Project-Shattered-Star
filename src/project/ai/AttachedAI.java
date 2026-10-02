package project.ai;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.ai.types.CommandAI;
import mindustry.ai.types.FlyingAI;
import mindustry.ai.types.GroundAI;
import mindustry.entities.Units;
import mindustry.gen.Legsc;
import mindustry.gen.Mechc;
import mindustry.gen.Unit;
import project.content.units.CarrierManager;
import project.content.units.CarrierUnitType;
import project.content.units.Slot;

public class AttachedAI extends CommandAI {

    public Unit host;

    public static float retargetInterval = 20f;
    public static float engageRangeMul = 1.2f;

    public static float rotationFollowSpeed = 1f;
    public static float positionLerpSpeed = 0.15f;

    private float retargetTimer = 0f;

    public AttachedAI(Unit host) {
        this.host = host;
    }

    @Override
    public void updateUnit() {
        updateMovement();
        updateTargeting();
    }

    @Override
    public void updateMovement() {
        if (host == null || !host.isValid() || !host.isAdded()
            || !(host.type instanceof CarrierUnitType)) {
            releaseControl();
            return;
        }

        CarrierUnitType ct = (CarrierUnitType) host.type;
        Integer idx = CarrierManager.slotIndex.get(unit);
        if (idx == null || idx < 0 || idx >= ct.slots.size) {
            releaseControl();
            return;
        }

        Slot s = ct.slots.get(idx);

        // ============ 位置 ============
        float ang = host.rotation - 90f;
        float cos = Mathf.cosDeg(ang);
        float sin = Mathf.sinDeg(ang);
        float wx = host.x + s.x * cos - s.y * sin;
        float wy = host.y + s.x * sin + s.y * cos;

        unit.x = Mathf.lerpDelta(unit.x, wx, positionLerpSpeed);
        unit.y = Mathf.lerpDelta(unit.y, wy, positionLerpSpeed);
        unit.vel.setZero();

        // ============ 抑制走路特效 ============
        suppressWalkEffects(unit);

        // ============ 防溺水 ============
        try {
            unit.drownTime = 0f;
        } catch (Throwable t) {
            try { unit.drownTime(0f); } catch (Throwable ignored) {}
        }

        // ============ 角度 ============
        float targetRot = s.absoluteRotation ? s.rotation : host.rotation + s.rotation;
        if (rotationFollowSpeed >= 1f) {
            unit.rotation = targetRot;
        } else {
            unit.rotation = Mathf.slerpDelta(unit.rotation, targetRot, rotationFollowSpeed);
        }

        if (host.type.flying) {
            unit.elevation = host.elevation;
        }

        // ============ 开火 ============
        boolean canShoot = s.canShootWhenAttached != null
            ? s.canShootWhenAttached
            : ct.defaultCanShootWhenAttached;

        if (canShoot && !unit.type.weapons.isEmpty()) {
            updateShooting();
        } else {
            target = null;
            unit.controlWeapons(false);
        }
    }

    /** 重置走路累积计时，防止尘土/水花/脚印。 */
    private static void suppressWalkEffects(Unit unit) {
        try {
            if (unit instanceof Mechc) {
                ((Mechc) unit).walkTime(0f);
            }
        } catch (Throwable ignored) {}

        try {
            if (unit instanceof Legsc) {
                ((Legsc) unit).walkTime(0f);
            }
        } catch (Throwable ignored) {}

        try {
            unit.elevation = Math.min(unit.elevation, 0f);
        } catch (Throwable ignored) {}
    }

    private void releaseControl() {
        CarrierManager.detach(unit);

        CommandAI cai = new CommandAI();
        cai.unit(unit);
        cai.targetPos = null;
        cai.attackTarget = null;
        unit.controller(cai);

        try {
            if (unit.command() != null) {
                unit.command().targetPos = null;
                unit.command().attackTarget = null;
            }
        } catch (Throwable ignored) {}
    }

    private void updateShooting() {
        float range = unit.range();
        if (range <= 0f) range = 80f;
        float searchR = range * engageRangeMul;

        retargetTimer -= Time.delta;
        if (retargetTimer <= 0f
            || target == null
            || Units.invalidateTarget(target, unit.team, unit.x, unit.y, searchR)) {
            target = Units.closestTarget(unit.team, unit.x, unit.y, searchR,
                u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround),
                b -> unit.type.targetGround);
            retargetTimer = retargetInterval;
        }

        if (target != null) {
            unit.aim(target.getX(), target.getY());
            unit.controlWeapons(true);
        } else {
            unit.controlWeapons(false);
        }
    }

    @Override
    public boolean retarget() {
        return false;
    }

    @Override
    public void updateTargeting() {
    }
}