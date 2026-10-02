package project.render;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Unit;
import mindustry.graphics.Draw;
import project.content.units.CarrierManager;
import project.content.units.CarrierUnitType;
import project.content.units.Slot;

public class AttachedUnitRenderer {

    private static boolean installed = false;

    public static void init() {
        if (installed) return;
        installed = true;
        Events.run(Trigger.draw, AttachedUnitRenderer::drawAll);
    }

    private static void drawAll() {
        for (ObjectMap.Entry<Unit, Seq<Unit>> entry : CarrierManager.carried) {
            final Unit host = entry.key;
            if (host == null || !host.isValid() || !host.isAdded()) continue;
            if (!(host.type instanceof CarrierUnitType)) continue;
            final CarrierUnitType ct = (CarrierUnitType) host.type;

            final Seq<Unit> list = entry.value;
            for (int i = 0; i < list.size; i++) {
                final Unit p = list.get(i);
                if (p == null || !p.isValid() || !p.isAdded()) continue;

                Integer idx = CarrierManager.slotIndex.get(p);
                if (idx == null || idx < 0 || idx >= ct.slots.size) continue;
                final Slot s = ct.slots.get(idx);

                Draw.draw(s.drawLayer, () -> p.type.draw(p));
            }
        }
    }
}