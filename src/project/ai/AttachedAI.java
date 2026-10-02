package project.ai;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.ai.types.FlyingAI;
import mindustry.ai.types.GroundAI;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;
import project.content.units.CarrierManager;
import project.content.units.CarrierUnitType;
import project.content.units.Slot;

public class AttachedAI extends AIController {

    public Unit host;

    /** 索敌间隔（帧）。 */
    public static float retargetInterval = 20f;
    /** 索敌半径倍率（相对 unit.range()）。 */
    public static float engageRangeMul = 1.2f;

    private float retargetTimer = 0f;

    public AttachedAI(Unit host) {
        this.host = host;
    }

    @Override
    public void updateMovement() {
        if (host == null || !host.isValid() || !host.isAdded()) {
            CarrierManager.detach(unit);
            unit.controller(unit.type.flying ? new FlyingAI() : new GroundAI());
            return;
        }

        if (!(host.type instanceof CarrierUnitType)) return;

        CarrierUnitType ct = (CarrierUnitType) host.type;
        Integer idx = CarrierManager.slotIndex.get(unit);
        if (idx == null || idx < 0 || idx >= ct.slots.size) return;

        Slot s = ct.slots.get(idx);

        // ============ 位置和角度：跟载具槽位 ============
        float ang = host.rotation - 90f;
        float cos = Mathf.cosDeg(ang);
        float sin = Mathf.sinDeg(ang);
        float wx = host.x + s.x * cos - s.y * sin;
        float wy = host.y + s.x * sin + s.y * cos;

        unit.x = Mathf.lerpDelta(unit.x, wx, s.smoothSpeed);
        unit.y = Mathf.lerpDelta(unit.y, wy, s.smoothSpeed);
        unit.vel.setZero();

        float targetRot = s.absoluteRotation ? s.rotation : host.rotation + s.rotation;
        unit.rotation = Mathf.slerpDelta(unit.rotation, targetRot, s.smoothSpeed);

        if (host.type.flying) {
            unit.elevation = host.elevation;
        }

        // ============ 开火逻辑 ============
        boolean canShoot;
        if (s.canShootWhenAttached != null) {
            canShoot = s.canShootWhenAttached;
        } else {
            canShoot = ct.defaultCanShootWhenAttached;
        }

        if (canShoot && !unit.type.weapons.isEmpty()) {
            updateShooting();
        } else {
            target = null;
            unit.controlWeapons(false);
        }
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
        // 屏蔽原版自动索敌，由 updateMovement 里的 updateShooting 接管
    }
}