package project.content.units;

import arc.func.Boolf;
import arc.struct.ObjectMap;
import mindustry.gen.ElevationMovec;
import mindustry.gen.Mechc;
import mindustry.gen.Tankc;
import mindustry.gen.Unit;
import mindustry.gen.WaterMovec;
import mindustry.type.UnitType;

public class UnitFilters {

    private static final ObjectMap<UnitType, ObjectMap<String, Boolean>> catCache = new ObjectMap<>();

    public static Boolf<UnitType> any() {
        return t -> true;
    }

    public static Boolf<UnitType> name(String name) {
        return t -> t != null && name != null && name.equals(t.name);
    }

    public static Boolf<UnitType> names(String... names) {
        return t -> {
            if (t == null) return false;
            for (String n : names) {
                if (n != null && n.equals(t.name)) return true;
            }
            return false;
        };
    }

    public static Boolf<UnitType> category(String cat) {
        return t -> matchCategory(t, cat);
    }

    private static boolean matchCategory(UnitType t, String cat) {
        if (t == null || cat == null) return false;
        ObjectMap<String, Boolean> m = catCache.get(t);
        if (m == null) {
            m = new ObjectMap<>();
            catCache.put(t, m);
        }
        if (m.containsKey(cat)) return m.get(cat);

        boolean result = computeCategory(t, cat);
        m.put(cat, result);
        return result;
    }

    private static boolean computeCategory(UnitType t, String cat) {
        switch (cat) {
            case "mech":
                if (t.flying || t.legCount > 0) return false;
                return instanceOf(t, Mechc.class);
            case "legs":
                return t.legCount > 0;
            case "tank":
                return instanceOf(t, Tankc.class);
            case "naval":
                return instanceOf(t, WaterMovec.class);
            case "hover":
                return instanceOf(t, ElevationMovec.class);
            case "flying":
                return t.flying;
            case "ground":
                return !t.flying;
            default:
                return false;
        }
    }

    private static boolean instanceOf(UnitType t, Class<?> clazz) {
        if (t.constructor == null) return false;
        try {
            Unit u = t.constructor.get();
            return clazz.isInstance(u);
        } catch (Throwable e) {
            return false;
        }
    }
}