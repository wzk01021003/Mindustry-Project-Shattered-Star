package project.content.units;

import arc.Events;
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
    public static float shrinkSpeed = 0.12f;

    /** 收缩后长度（占原 legLength 比例）。0.25 = 保留 25%。 */
    public static float finalLength = 0.25f;

    /** 膝盖伸出比例（相对最终长度）。0.5 = 膝盖在中点。 */
    public static float kneeRatio = 0.5f;

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
        float bodyAngle = unit.rotation - 90f;   // sprite 朝上 = 前方
        float len = type.legLength * finalLength;
        int count = legs.length;

        for (int i = 0; i < count; i++) {
            Leg leg = legs[i];

            // 关键：用「腿到身体中心的当前方向」作为目标方向，
            // 每个腿沿自己原本所在的方向缩回，不会互相打架。
            float baseAng = Mathf.atan2(leg.base.y - unit.y, leg.base.x - unit.x) * Mathf.radDeg;
            // 回退到"均匀分布"，防止 leg.base 一开始就在中心（角度为 0）
            if (Mathf.dst(leg.base.x, leg.base.y, unit.x, unit.y) < 1f) {
                baseAng = bodyAngle + i * (360f / count);
            }

            float jx = unit.x + Mathf.cosDeg(baseAng) * len * kneeRatio;
            float jy = unit.y + Mathf.sinDeg(baseAng) * len * kneeRatio;
            float bx = unit.x + Mathf.cosDeg(baseAng) * len;
            float by = unit.y + Mathf.sinDeg(baseAng) * len;

            leg.joint.x = Mathf.lerp(leg.joint.x, jx, p);
            leg.joint.y = Mathf.lerp(leg.joint.y, jy, p);
            leg.base.x = Mathf.lerp(leg.base.x, bx, p);
            leg.base.y = Mathf.lerp(leg.base.y, by, p);
        }
    }
}