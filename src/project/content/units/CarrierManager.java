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

        float used = usedCapacity(host);
        float cost = ct.payloadCost(passenger.type);
        if (used + cost > ct.carrierCapacity + 0.001f) return false;

        int chosen = -1;
        for (int i = 0; i < 100; i++) {
            if (!map.containsKey(i)) { chosen = i; break; }
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

    public static float usedCapacity(Unit host) {
        if (!(host.type instanceof CarrierUnitType)) return 0f;
        CarrierUnitType ct = (CarrierUnitType) host.type;
        float sum = 0f;
        for (Unit u : carried.get(host, ObjectMap::new).values()) {
            sum += ct.payloadCost(u.type);
        }
        return sum;
    }

    public static float remainingCapacity(Unit host) {
        if (!(host.type instanceof CarrierUnitType)) return 0f;
        CarrierUnitType ct = (CarrierUnitType) host.type;
        return ct.carrierCapacity - usedCapacity(host);
    }

    public static boolean isFull(Unit host) {
        if (!(host.type instanceof CarrierUnitType)) return true;
        CarrierUnitType ct = (CarrierUnitType) host.type;
        return usedCapacity(host) >= ct.carrierCapacity - 0.001f;
    }

    public static boolean accepts(Unit host, UnitType passengerType) {
        if (!(host.type instanceof CarrierUnitType)) return false;
        CarrierUnitType ct = (CarrierUnitType) host.type;
        if (ct.globalFilter != null && !ct.globalFilter.get(passengerType)) return false;
        float cost = ct.payloadCost(passengerType);
        return usedCapacity(host) + cost <= ct.carrierCapacity + 0.001f;
    }
}