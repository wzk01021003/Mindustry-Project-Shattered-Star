package project;

import arc.util.*;
import mindustry.mod.*;
import project.blocks.MultiAssembler;

public class ShatteredStarMod extends Mod{

    public ShatteredStarMod(){
        Log.info("Loaded ShatteredStarMod constructor.");
    }

    @Override
    public void loadContent(){
        Log.info("Loading content.");
        new MultiAssembler("multi-assembler");
    }
}