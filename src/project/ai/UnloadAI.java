package project.ai;

import mindustry.ai.types.FlyingAI;
import mindustry.ai.types.GroundAI;
import mindustry.entities.units.AIController;
import project.content.units.TransportUnloadHandler;

public class UnloadAI extends AIController {

    private boolean executed = false;

    @Override
    public void updateMovement() {
        if (executed) return;
        executed = true;

        TransportUnloadHandler.unloadAll(unit);

        unit.controller(unit.type.flying ? new FlyingAI() : new GroundAI());
    }

    @Override
    public boolean retarget() {
        return false;
    }
}