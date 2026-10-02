package project.content.units;

import arc.Events;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.entities.Leg;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Legsc;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

public class LegShrinkHandler {

    public static float shrinkSpeed = 0.10f;
    public static float kneeOut = 0.55f;
    public static float footIn = 0.15f;
    public static float spreadScl = 0.6f;

    private static final ObjectMap<Unit, Float> progress = new ObjectMap<>();
    private static boolean installed = false;

    public static void init() {
        if (installed) return;
        installed = true;
        Events.run(Trigger.draw, LegShrinkHandler::tick);
    }

    private static void tick() {
        for (Unit host : CarrierManager.carried.keys()) {
            for (Unit p : CarrierManager.getCarried(host)) {
                fold(p);
            }
        }

        Seq<Unit> stale = new Seq<>();
        for (Unit u : progress.keys()) {
            if (!CarrierManager.slotIndex.containsKey(u)) stale.add(u);
        }
        for (Unit u : stale) progress.remove(u);
    }

    private static void fold(Unit unit) {
        if (!(unit instanceof Legsc)) return;
        Leg[] legs = ((Legsc) unit).legs();
        if (legs == null || legs.length == 0) return;

        float p = progress.get(unit, 0f);
        p = Mathf.lerpDelta(p, 1f, shrinkSpeed);
        progress.put(unit, p);

        UnitType type = unit.type;
        float ang = unit.rotation - 90f;
        float len = type.legLength;
        int count = legs.length;

        for (int i = 0; i < count; i++) {
            Leg leg = legs[i];

            boolean isFront = i < count / 2f;
            float dir = isFront ? 1f : -1f;

            float sideFrac = (count == 1) ? 0f
            : ((i % 2 == 0) ? -1f : 1f) * ((i / 2f) / Math.max(1f, count / 2f)) * spreadScl;

            float foldAng = ang + dir * 90f + sideFrac * 90f;

            float jx = unit.x + Angles.trnsx(foldAng, len * kneeOut);
            float jy = unit.y + Angles.trnsy(foldAng, len * kneeOut);

            float bx = unit.x + Angles.trnsx(foldAng, len * footIn);
            float by = unit.y + Angles.trnsy(foldAng, len * footIn);

            leg.joint.x = Mathf.lerp(leg.joint.x, jx, p);
            leg.joint.y = Mathf.lerp(leg.joint.y, jy, p);
            leg.base.x  = Mathf.lerp(leg.base.x,  bx, p);
            leg.base.y  = Mathf.lerp(leg.base.y,  by, p);
        }
    }
}