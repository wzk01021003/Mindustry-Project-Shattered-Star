package project.content;

import arc.math.Angles;
import arc.struct.Seq;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Unit;
import mindustry.type.Weapon;
import project.graphics.DistortionFx;

public class PartTriggerAbility extends Ability {

    public static class Rule {
        public String weaponName;
        public DistortionFx preset;
        public String progress = "warmup";
        public float threshold = 0.95f;
        public boolean once = true;
        public float offsetX = 0f;
        public float offsetY = 0f;

        public transient boolean triggered = false;
    }

    public Seq<Rule> rules = new Seq<>();

    @Override
    public void update(Unit unit) {
        if (unit == null || unit.type == null || unit.type.weapons == null) return;

        for (Rule r : rules) {
            Weapon w = findWeapon(unit, r.weaponName);
            if (w == null) continue;

            float progress = getProgress(w, r.progress);

            if (progress < r.threshold * 0.5f) r.triggered = false;

            if (!r.triggered && progress >= r.threshold) {
                r.triggered = true;

                float mx = w.x + r.offsetX;
                float my = w.y + w.shootY + r.offsetY;

                float wx = unit.x + Angles.trnsx(unit.rotation - 90f, mx, my);
                float wy = unit.y + Angles.trnsy(unit.rotation - 90f, mx, my);

                DistortionConfig.playFx(r.preset, wx, wy);
            }
        }
    }

    private static Weapon findWeapon(Unit u, String name) {
        if (name == null || name.isEmpty()) {
            return u.type.weapons.size > 0 ? u.type.weapons.first() : null;
        }
        for (Weapon w : u.type.weapons) {
            if (name.equals(w.name)) return w;
        }
        return null;
    }

    /** 用反射读 Weapon 的进度字段，找不到就返回 0。 */
    private static float getProgress(Weapon w, String type) {
        String field = null;
        if (type == null) field = "warmup";
        else switch (type.toLowerCase()) {
            case "warmup": field = "warmup"; break;
            case "recoil": field = "recoil"; break;
            case "heat":   field = "heat";   break;
            case "reload": {
                try {
                    java.lang.reflect.Field fR = w.getClass().getField("reload");
                    java.lang.reflect.Field fC = w.getClass().getField("reloadCounter");
                    float reload = fR.getFloat(w);
                    float counter = fC.getFloat(w);
                    if (reload <= 0f) return 0f;
                    return 1f - Math.max(0f, Math.min(1f, counter / reload));
                } catch (Throwable t) {
                    return 0f;
                }
            }
            default: field = "warmup";
        }

        try {
            java.lang.reflect.Field f = w.getClass().getField(field);
            return f.getFloat(w);
        } catch (Throwable t) {
            return 0f;
        }
    }
}