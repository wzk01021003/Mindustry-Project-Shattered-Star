package project.content.units;

import arc.func.Boolf;
import arc.struct.Seq;
import mindustry.type.UnitType;

public class CarrierUnitType extends UnitType {

    public final Seq<Slot> slots = new Seq<>();
    public Boolf<UnitType> globalFilter = null;

    public CarrierUnitType(String name) {
        super(name);
    }

    public CarrierUnitType slot(float x, float y, float rotation) {
        slots.add(new Slot(x, y, rotation));
        return this;
    }

    public CarrierUnitType slot(Slot s) {
        slots.add(s);
        return this;
    }

    public CarrierUnitType filter(Boolf<UnitType> f) {
        this.globalFilter = f;
        return this;
    }
}