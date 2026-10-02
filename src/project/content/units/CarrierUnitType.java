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

    /** 返回新建的 Slot，方便链式调用。 */
    public Slot slot(float x, float y, float rotation) {
        Slot s = new Slot(x, y, rotation);
        slots.add(s);
        return s;
    }

    public Slot slot(Slot s) {
        slots.add(s);
        return s;
    }

    public CarrierUnitType filter(Boolf<UnitType> f) {
        this.globalFilter = f;
        return this;
    }
}