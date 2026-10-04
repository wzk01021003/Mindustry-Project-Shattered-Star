package project.content;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.math.Interp;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.entities.effect.MultiEffect;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.io.JsonIO;
import mindustry.mod.Mods.LoadedMod;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import project.graphics.DistortionFx;
import project.graphics.DistortionRenderer;

public class DistortionConfig {

    // ============================================================
    //  JSON 结构
    // ============================================================
    public static class Preset {
        public float radius = 100f;
        public float strength = 1f;
        public float life = 30f;
        public int type = 1;
        public float radiusFrom = 0f;
        public float radiusTo = 1f;
        public float ringWidth = 0f;
        public String interp = "pow2Out";
    }

    /** 基础条目：unit + weapon（可选）+ preset */
    public static class Entry {
        public String unit;
        public String weapon;
        public String preset;
    }

    /** 特效挂载点条目：preset + 挂在哪 */
    public static class EffectEntry {
        public String unit;
        public String weapon;
        public String preset;
        public String trigger;   // "hit" / "shoot" / "spawn" / "despawn" / "death"
    }

    /** Part 触发器：进度阈值触发 */
    public static class PartTrigger {
        public String unit;
        public String weapon;
        public String preset;
        public String progress = "warmup";   // "warmup" / "recoil" / "reload" / "heat"
        public float threshold = 0.95f;
        public boolean once = true;           // true=每次循环一次，false=只触发一次
        public float offsetX = 0f;            // 相对单位的炮口偏移
        public float offsetY = 0f;
    }

    public static class Config {
        public String version = "1.0";
        public ObjectMap<String, Preset> presets = new ObjectMap<>();
        public Seq<Entry> unitDeaths = new Seq<>();
        public Seq<EffectEntry> weaponEffects = new Seq<>();
        public Seq<PartTrigger> partTriggers = new Seq<>();
    }

    // ============================================================
    //  运行时
    // ============================================================
    private static final ObjectMap<String, DistortionFx> presetCache = new ObjectMap<>();
    private static Config cfg = new Config();

    // ============================================================
    //  加载
    // ============================================================
    public static void load() {
        cfg = new Config();

        // 1. 自己的配置
        mergeFromFile(Core.files.internal("configs/ss-distortions.json"));

        // 2. 其他模组的配置（依赖你模组的）
        if (Vars.mods != null && Vars.mods.list() != null) {
            for (LoadedMod mod : Vars.mods.list()) {
                if (mod == null || mod.root == null) continue;
                Fi f = mod.root.child("assets").child("ss-distortion.json");
                if (f.exists()) {
                    Log.info("[ss-distortion] loading from mod: " + mod.name);
                    mergeFromFile(f);
                }
            }
        }

        // 3. 建预设
        presetCache.clear();
        for (ObjectMap.Entry<String, Preset> e : cfg.presets) {
            presetCache.put(e.key, buildFx(e.value));
        }

        Log.info("[ss-distortion] " + presetCache.size + " presets, "
            + cfg.unitDeaths.size + " deaths, "
            + cfg.weaponEffects.size + " weapon fx, "
            + cfg.partTriggers.size + " part triggers");
    }

    private static void mergeFromFile(Fi file) {
        if (file == null || !file.exists()) return;
        try {
            Config c = JsonIO.json.fromJson(Config.class, file);
            if (c == null) return;
            if (c.presets != null) {
                for (ObjectMap.Entry<String, Preset> e : c.presets) cfg.presets.put(e.key, e.value);
            }
            if (c.unitDeaths != null) cfg.unitDeaths.addAll(c.unitDeaths);
            if (c.weaponEffects != null) cfg.weaponEffects.addAll(c.weaponEffects);
            if (c.partTriggers != null) cfg.partTriggers.addAll(c.partTriggers);
        } catch (Throwable t) {
            Log.err("[ss-distortion] parse failed: " + file.path(), t);
        }
    }

    private static DistortionFx buildFx(Preset p) {
        return new DistortionFx(p.radius, p.strength, p.life, p.type,
            p.radiusFrom, p.radiusTo, p.ringWidth, parseInterp(p.interp));
    }

    private static Interp parseInterp(String name) {
        if (name == null) return Interp.pow2Out;
        switch (name.toLowerCase()) {
            case "linear":    return Interp.linear;
            case "pow2in":    return Interp.pow2In;
            case "pow2out":   return Interp.pow2Out;
            case "pow3in":    return Interp.pow3In;
            case "pow3out":   return Interp.pow3Out;
            case "pow4in":    return Interp.pow4In;
            case "pow4out":   return Interp.pow4Out;
            case "circleout": return Interp.circleOut;
            case "circlein":  return Interp.circleIn;
            case "sinein":    return Interp.sineIn;
            case "sineout":   return Interp.sineOut;
            default:          return Interp.pow2Out;
        }
    }

    // ============================================================
    //  应用
    // ============================================================
    public static void apply() {
        applyWeaponEffects();
        applyUnitDeaths();
        applyPartTriggers();
    }

    private static void applyWeaponEffects() {
        for (EffectEntry e : cfg.weaponEffects) {
            DistortionFx fx = presetCache.get(e.preset);
            if (fx == null) { Log.warn("[ss-distortion] unknown preset: " + e.preset); continue; }

            UnitType type = Vars.content.unit(e.unit);
            if (type == null) { Log.warn("[ss-distortion] unknown unit: " + e.unit); continue; }

            for (Weapon w : type.weapons) {
                if (e.weapon != null && !e.weapon.isEmpty() && !e.weapon.equals(w.name)) continue;
                if (w.bullet == null) continue;

                switch (e.trigger == null ? "hit" : e.trigger) {
                    case "hit":     w.bullet.hitEffect    = wrap(w.bullet.hitEffect, fx); break;
                    case "shoot":   w.bullet.shootEffect  = wrap(w.bullet.shootEffect, fx); break;
                    case "spawn":   w.bullet.spawnEffect  = wrap(w.bullet.spawnEffect, fx); break;
                    case "despawn": w.bullet.despawnEffect= wrap(w.bullet.despawnEffect, fx); break;
                    case "trail":   w.bullet.trailEffect  = wrap(w.bullet.trailEffect, fx); break;
                }
            }
        }
    }

    private static mindustry.entities.Effect wrap(mindustry.entities.Effect orig, DistortionFx fx) {
        if (orig == null || orig == mindustry.content.Fx.none) return fx;
        return new MultiEffect(orig, fx);
    }

    private static void applyUnitDeaths() {
        Events.on(UnitDestroyEvent.class, e -> {
            if (e.unit == null || e.unit.type == null) return;
            for (Entry en : cfg.unitDeaths) {
                if (!en.unit.equals(e.unit.type.name)) continue;
                DistortionFx fx = presetCache.get(en.preset);
                if (fx == null) continue;
                playFx(fx, e.unit.x, e.unit.y);
                return;
            }
        });
    }

    private static void applyPartTriggers() {
        // 按 unit 分组，给每个 UnitType 加一个 PartTriggerAbility
        ObjectMap<String, Seq<PartTrigger>> byUnit = new ObjectMap<>();
        for (PartTrigger t : cfg.partTriggers) {
            byUnit.get(t.unit, Seq::new).add(t);
        }

        for (ObjectMap.Entry<String, Seq<PartTrigger>> entry : byUnit) {
            UnitType type = Vars.content.unit(entry.key);
            if (type == null) { Log.warn("[ss-distortion] unknown unit: " + entry.key); continue; }

            PartTriggerAbility ab = new PartTriggerAbility();
            for (PartTrigger t : entry.value) {
                DistortionFx fx = presetCache.get(t.preset);
                if (fx == null) { Log.warn("[ss-distortion] unknown preset: " + t.preset); continue; }

                PartTriggerAbility.Rule r = new PartTriggerAbility.Rule();
                r.weaponName = t.weapon;
                r.preset = fx;
                r.progress = t.progress;
                r.threshold = t.threshold;
                r.once = t.once;
                r.offsetX = t.offsetX;
                r.offsetY = t.offsetY;
                ab.rules.add(r);
            }
            if (ab.rules.size > 0) type.abilities.add(ab);
        }
    }

    public static void playFx(DistortionFx fx, float x, float y) {
        DistortionRenderer.addDistortion(x, y, fx.radius, fx.strength, fx.life,
            fx.type, fx.radiusFrom, fx.radiusTo, fx.ringWidth, fx.interp);
    }

    public static void play(String preset, float x, float y) {
        DistortionFx fx = presetCache.get(preset);
        if (fx != null) playFx(fx, x, y);
    }
}