package project.blocks;

import arc.scene.ui.layout.Table;
import mindustry.world.blocks.units.UnitAssembler;

public class MultiAssembler extends UnitAssembler {

    public MultiAssembler(String name) {
        super(name);
        configurable = true;
        solid = true;
        update = true;
        size = 5;
        health = 3500;
    }

    public class MultiAssemblerBuild extends UnitAssemblerBuild {
        @Override
        public void buildConfiguration(Table table) {
            table.button("HELLO", () -> {}).size(200f, 50f);
        }
    }
}