package project.content.units;

import arc.Events;
import mindustry.game.EventType.UnitDestroyEvent;

public class AttachedUpdateHandler {

    private static boolean installed = false;

    public static void init() {
        if (installed) return;
        installed = true;

        // 载具死亡 → 释放挂载单位
        Events.on(UnitDestroyEvent.class, e -> {
            if (e.unit == null) return;
            if (!(e.unit.type instanceof CarrierUnitType)) return;
            TransportUnloadHandler.unloadAll(e.unit);
        });
    }
}