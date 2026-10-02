package project.ai;

import mindustry.ai.types.CommandAI;
import project.content.units.TransportUnloadHandler;

public class UnloadAI extends CommandAI {

    private boolean executed = false;

    @Override
    public void updateUnit() {
        if (!executed) {
            executed = true;
            TransportUnloadHandler.unloadAll(unit);
        }

        // 卸载后交还给 CommandAI（清空目标，等玩家指挥）
        CommandAI cai = new CommandAI();
        cai.unit(unit);
        cai.targetPos = null;
        cai.attackTarget = null;
        unit.controller(cai);

        try {
            if (unit.command() != null) {
                unit.command().targetPos = null;
                unit.command().attackTarget = null;
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void updateMovement() {
    }

    @Override
    public boolean retarget() {
        return false;
    }
}