package project.content;

import arc.Core;
import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.Log;
import mindustry.type.UnitType;

public class ProtectModeRegistry {

    private static final ObjectMap<String, ProtectModes> modes = new ObjectMap<>();

    public static void load() {
        put("mono", ProtectModes.PUSH);
        put("poly", ProtectModes.PUSH);
        put("mega", ProtectModes.SHIELD);
        put("fortress", ProtectModes.SHIELD);
        put("mace", ProtectModes.SHIELD);
        put("scepter", ProtectModes.SHIELD);
        put("reign", ProtectModes.SHIELD);
        tryReadJson();
    }

    private static void tryReadJson() {
        try {
            Fi file = Core.files.internal("configs/ss-protect-modes.json");
            if (!file.exists()) {
                Log.info("[ss] No protect-modes.json, using defaults.");
                return;
            }
            var root = new arc.util.serialization.JsonReader().parse(file);
            for (var entry : root) {
                try {
                    ProtectModes mode = ProtectModes.valueOf(entry.asString().toUpperCase());
                    put(entry.name, mode);
                } catch (IllegalArgumentException ignored) {}
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