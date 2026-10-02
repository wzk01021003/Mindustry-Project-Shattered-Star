package project.ui;

import arc.Events;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Unit;
import mindustry.ui.Styles;
import project.content.units.CarrierManager;

public class TransportPayloadUI {

    private static Table table;
    private static boolean installed = false;

    public static float iconSize = 32f;
    public static float padRight = 16f;
    public static float padBottom = 180f;
    public static String onlyUnitPrefix = "transport-";

    public static void init() {
        if (installed) return;
        installed = true;
        Events.run(Trigger.uiDrawBegin, TransportPayloadUI::rebuild);
    }

    private static void rebuild() {
        if (Vars.headless) return;
        if (Vars.ui == null || Vars.ui.hudGroup == null) return;
        if (Vars.player == null) return;

        Unit unit = Vars.player.unit();
        boolean shouldShow = false;

        if (unit != null && unit.isValid()) {
            if (unit.type != null
                && (onlyUnitPrefix == null || unit.type.name.startsWith(onlyUnitPrefix))
                && CarrierManager.getCarried(unit).size > 0) {
                shouldShow = true;
            }
        }

        if (!shouldShow) {
            if (table != null) {
                table.remove();
                table = null;
            }
            return;
        }

        if (table == null) {
            table = new Table();
            table.background(Styles.black6);
            table.margin(6f);
            Vars.ui.hudGroup.add(table)
            .name("ss-transport-payload-ui")
            .right()
            .bottom()
            .padRight(padRight)
            .padBottom(padBottom);
        }

        table.clearChildren();

        int perRow = 4;
        int i = 0;
        for (Unit p : CarrierManager.getCarried(unit)) {
            try {
                table.image(p.type.uiIcon).size(iconSize).padRight(4f);
                i++;
                if (i % perRow == 0) table.row();
            } catch (Throwable ignored) {}
        }
    }
}