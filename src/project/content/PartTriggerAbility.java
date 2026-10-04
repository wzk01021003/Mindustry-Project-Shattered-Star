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
        if (unit == null || unit.type == null) return;

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

    private static float getProgress(Weapon w, String type) {
        if (type == null) return w.warmup;
        switch (type.toLowerCase()) {
            case "warmup":  return w.warmup;
            case "recoil":  return w.recoil;
            case "heat":    return w.heat;
            case "reload":  return w.reload <= 0f ? 0f : 1f - w.reloadCounter / w.reload;
            default:        return w.warmup;
        }
    }
}