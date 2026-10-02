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

    /** 收缩速度。 */
    public static float shrinkSpeed = 0.10f;

    /** 收缩后腿长相对原长比例。0.4 = 保留 40%。 */
    public static float shrinkTo = 0.4f;

    /** 腿张开角度系数。0 = 全在中线，1 = 均分 ±90°。 */
    public static float spreadScl = 0.5f;

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
                shrink(p);
            }
        }

        Seq<Unit> stale = new Seq<>();
        for (Unit u : progress.keys()) {
            if (!CarrierManager.slotIndex.containsKey(u)) stale.add(u);
        }
        for (Unit u : stale) progress.remove(u);
    }

    private static void shrink(Unit unit) {
        if (!(unit instanceof Legsc)) return;
        Leg[] legs = ((Legsc) unit).legs();
        if (legs == null || legs.length == 0) return;

        float p = progress.get(unit, 0f);
        p = Mathf.lerpDelta(p, 1f, shrinkSpeed);
        progress.put(unit, p);

        UnitType type = unit.type;
        float ang = unit.rotation - 90f;
        float len = type.legLength * shrinkTo;
        int count = legs.length;
        float totalSpread = 180f * spreadScl;

        for (int i = 0; i < count; i++) {
            Leg leg = legs[i];

            float frac = count == 1 ? 0f : (i / (float)(count - 1) - 0.5f);
            float rel = frac * totalSpread;
            float legAng = ang + rel;

            float jx = unit.x + Angles.trnsx(legAng, len * 0.5f);
            float jy = unit.y + Angles.trnsy(legAng, len * 0.5f);
            float bx = unit.x + Angles.trnsx(legAng, len);
            float by = unit.y + Angles.trnsy(legAng, len);

            leg.joint.x = Mathf.lerp(leg.joint.x, jx, p);
            leg.joint.y = Mathf.lerp(leg.joint.y, jy, p);
            leg.base.x  = Mathf.lerp(leg.base.x,  bx, p);
            leg.base.y  = Mathf.lerp(leg.base.y,  by, p);
        }
    }
}