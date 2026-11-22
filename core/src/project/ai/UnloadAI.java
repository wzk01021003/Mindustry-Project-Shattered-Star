package project.ai;

import mindustry.ai.types.CommandAI;
import project.content.units.CarrierReleaseHandler;
import project.content.units.TransportUnloadHandler;

public class UnloadAI extends CommandAI {

    private boolean triggered = false;

    @Override
    public void updateUnit() {
        if (!triggered) {
            triggered = true;
            TransportUnloadHandler.unloadAll(unit);
        }

        // 释放还在进行 → 保持这个 AI 不做别的
        if (CarrierReleaseHandler.isReleasing(unit)) {
            return;
        }

        // 释放完成 → 交还控制
        CommandAI cai = new CommandAI();
        cai.unit(unit);
        cai.targetPos = null;
        cai.attackTarget = null;
        unit.controller(cai);
    }

    @Override
    public void updateMovement() {
    }

    @Override
    public boolean retarget() {
        return false;
    }
}