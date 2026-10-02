package project.ai;

import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;
import project.content.units.CarrierManager;
import project.content.units.CarrierUnitType;

public class EnterTransportAI extends AIController {

    public static float transportFindRange = 50f;
    public static float boardRange = 24f;

    @Override
    public void updateMovement() {
        Vec2 targetPos = unit.command().targetPos;
        if (targetPos == null) return;

        Unit transport = Units.closest(unit.team, targetPos.x, targetPos.y, transportFindRange,
            u -> u != unit
            && u.isValid()
            && (u.type instanceof CarrierUnitType)
            && !CarrierManager.isFull(u)
            && CarrierManager.accepts(u, unit.type));

        if (transport == null) {
            moveTo(Tmp.v1.set(targetPos.x, targetPos.y));
            return;
        }

        float dst = unit.dst(transport);
        if (dst <= boardRange) {
            if (CarrierManager.tryAttach(transport, unit)) {
                unit.controller(new AttachedAI(transport));
            }
            return;
        }

        moveTo(Tmp.v1.set(transport.x, transport.y));
    }

    @Override
    public boolean retarget() {
        return false;
    }
}