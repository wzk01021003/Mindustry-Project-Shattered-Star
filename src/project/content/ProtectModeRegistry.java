package project.content;

import arc.Core;
import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.Log;
import mindustry.type.UnitType;

/** 每个单位类型的保护模式注册表。默认值硬编码，可被 JSON 覆盖 */
public class ProtectModeRegistry {

    private static final ObjectMap<String, ProtectModes> modes = new ObjectMap<>();

    public static void load() {
        // ============ 默认配置 ============
        put("mono", ProtectModes.PUSH);
        put("poly", ProtectModes.PUSH);
        put("mega", ProtectModes.SHIELD);
        put("fortress", ProtectModes.SHIELD);
        put("mace", ProtectModes.SHIELD);
        put("scepter", ProtectModes.SHIELD);
        put("reign", ProtectModes.SHIELD);

        // ============ JSON 覆盖 ============
        tryReadJson();
    }

    private static void tryReadJson() {
        try {
            Fi file = Core.files.internal("configs/ss-protect-modes.json");
            if (!file.exists()) {
                Log.info("[ss] No protect-modes.json found, using defaults.");
                return;
            }

            var root = new arc.util.serialization.JsonReader().parse(file);
            for (var entry : root) {
                String unitName = entry.name;
                String modeName = entry.asString();
                try {
                    ProtectModes mode = ProtectModes.valueOf(modeName.toUpperCase());
                    put(unitName, mode);
                } catch (IllegalArgumentException e) {
                    Log.err("[ss] Invalid protect mode: " + modeName + " for unit " + unitName);
                }
            }
            Log.info("[ss] Loaded protect modes from JSON.");
        } catch (Exception e) {
            Log.err("[ss] Failed to read protect-modes.json", e);
        }
    }

    public static void put(String unitName, ProtectModes mode) {
        modes.put(unitName, mode);
    }

    public static ProtectModes get(UnitType type) {
        if (type == null) return ProtectModes.ATTACK;
        return modes.get(type.name, ProtectModes.ATTACK);
    }
}