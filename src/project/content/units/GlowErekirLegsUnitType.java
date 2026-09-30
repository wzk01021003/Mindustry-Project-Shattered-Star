package project.content.units;

import mindustry.gen.Legsc;
import mindustry.gen.Unit;
import mindustry.type.unit.ErekirUnitType;

public class GlowErekirLegsUnitType extends ErekirUnitType {

    public GlowLegsUnitType.GlowData glow = new GlowLegsUnitType.GlowData();

    public GlowErekirLegsUnitType(String name) {
        super(name);
    }

    @Override
    public void load() {
        super.load();
        glow.load(name);
    }

    @Override
    public <T extends Unit & Legsc> void drawLegs(T unit) {
        super.drawLegs(unit);
        glow.draw(this, unit);
    }
}