package project.content.units;

import arc.struct.ObjectMap;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

public class CarrierManager {

    /** host → (槽位索引 → 挂载单位) */
    public static final ObjectMap<Unit, ObjectMap<Integer, Unit>> carried = new ObjectMap<>();

    public static boolean tryAttach(Unit host, Unit passenger) {
        if (host == null || passenger == null) return false;
        if (!(host.type instanceof CarrierUnitType)) return false;
        if (passenger == host) return false;

        CarrierUnitType ct = (CarrierUnitType) host.type;
        if (ct.globalFilter != null && !ct.globalFilter.get(passenger.type)) return false;

        ObjectMap<Integer, Unit> map = carried.get(host, ObjectMap::new);

        int chosen = -1;
        for (int i = 0; i < ct.slots.size; i++) {
            if (map.containsKey(i)) continue;
            if (!ct.slots.get(i).accepts(passenger.type)) continue;
            chosen = i;
            break;
        }
        if (chosen < 0) return false;

        map.put(chosen, passenger);
        passenger.remove();
        return true;
    }

    public static void detach(Unit passenger) {
        Unit host = getHost(passenger);
        if (host == null) return;

        ObjectMap<Integer, Unit> map = carried.get(host);
        if (map != null) {
            Integer key = null;
            for (ObjectMap.Entry<Integer, Unit> e : map) {
                if (e.value == passenger) { key = e.key; break; }
            }
            if (key != null) map.remove(key);
            if (map.isEmpty()) carried.remove(host);
        }
    }

    public static Unit getHost(Unit passenger) {
        for (ObjectMap.Entry<Unit, ObjectMap<Integer, Unit>> e : carried) {
            for (Unit u : e.value.values()) {
                if (u == passenger) return e.key;
            }
        }
        return null;
    }

    public static ObjectMap<Integer, Unit> getCarried(Unit host) {
        return carried.get(host, ObjectMap::new);
    }

    public static boolean isFull(Unit host) {
        if (!(host.type instanceof CarrierUnitType)) return true;
        CarrierUnitType ct = (CarrierUnitType) host.type;
        return carried.get(host, ObjectMap::new).size >= ct.slots.size;
    }

    public static boolean accepts(Unit host, UnitType passengerType) {
        if (!(host.type instanceof CarrierUnitType)) return false;
        CarrierUnitType ct = (CarrierUnitType) host.type;
        if (ct.globalFilter != null && !ct.globalFilter.get(passengerType)) return false;
        for (int i = 0; i < ct.slots.size; i++) {
            if (ct.slots.get(i).accepts(passengerType)) return true;
        }
        return false;
    }
}