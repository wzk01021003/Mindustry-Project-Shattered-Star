package project.ai;

import arc.math.Mathf;
import mindustry.ai.types.FlyingAI;
import mindustry.ai.types.GroundAI;
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;
import project.content.units.CarrierManager;
import project.content.units.CarrierUnitType;
import project.content.units.Slot;

public class AttachedAI extends AIController {

    public Unit host;

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
        unit.aimX = wx;
        unit.aimY = wy;

        if (host.type.flying) {
            unit.elevation = host.elevation;
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