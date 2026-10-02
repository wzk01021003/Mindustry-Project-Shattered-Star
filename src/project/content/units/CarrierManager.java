package project.content.units;

import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.gen.Unit;

public class CarrierManager {

    public static final ObjectMap<Unit, Seq<Unit>> carried = new ObjectMap<>();
    public static final ObjectMap<Unit, Integer> slotIndex = new ObjectMap<>();

    public static boolean tryAttach(Unit host, Unit passenger) {
        if (host == null || passenger == null) return false;
        if (!(host.type instanceof CarrierUnitType)) return false;
        if (passenger == host) return false;
        if (carried.get(host, Seq::new).contains(passenger)) return false;

        CarrierUnitType ct = (CarrierUnitType) host.type;

        if (ct.globalFilter != null && !ct.globalFilter.get(passenger.type)) return false;

        Seq<Unit> list = carried.get(host, Seq::new);

        ObjectSet<Integer> used = new ObjectSet<>();
        for (Unit u : list) {
            Integer idx = slotIndex.get(u);
            if (idx != null) used.add(idx);
        }

        int chosen = -1;
        for (int i = 0; i < ct.slots.size; i++) {
            if (used.contains(i)) continue;
            if (!ct.slots.get(i).accepts(passenger.type)) continue;
            chosen = i;
            break;
        }
        if (chosen < 0) return false;

        list.add(passenger);
        slotIndex.put(passenger, chosen);
        return true;
    }

    public static void detach(Unit passenger) {
        Unit host = getHost(passenger);
        if (host == null) return;

        Seq<Unit> list = carried.get(host);
        if (list != null) {
            list.remove(passenger);
            if (list.isEmpty()) carried.remove(host);
        }
        slotIndex.remove(passenger);
    }

    public static Unit getHost(Unit passenger) {
        for (ObjectMap.Entry<Unit, Seq<Unit>> e : carried) {
            if (e.value.contains(passenger)) return e.key;
        }
        return null;
    }

    public static Seq<Unit> getCarried(Unit host) {
        return carried.get(host, Seq::new);
    }

    public static boolean isFull(Unit host) {
        if (!(host.type instanceof CarrierUnitType)) return true;
        CarrierUnitType ct = (CarrierUnitType) host.type;
        return carried.get(host, Seq::new).size >= ct.slots.size;
    }

    public static boolean accepts(Unit host, mindustry.type.UnitType passengerType) {
        if (!(host.type instanceof CarrierUnitType)) return false;
        CarrierUnitType ct = (CarrierUnitType) host.type;
        if (ct.globalFilter != null && !ct.globalFilter.get(passengerType)) return false;
        for (int i = 0; i < ct.slots.size; i++) {
            if (ct.slots.get(i).accepts(passengerType)) return true;
        }
        return false;
    }
}