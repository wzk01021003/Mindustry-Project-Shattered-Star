package project.ai;

import mindustry.ai.types.FlyingAI;
import mindustry.ai.types.GroundAI;
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;

/**
 * 狩猎 AI：完全委托原版 AI。
 * 飞行 → FlyingAI，其他 → GroundAI。
 * 不做任何自定义。
 */
public class HuntAI extends AIController {

    protected AIController delegate;

    @Override
    public void updateUnit() {
        if (delegate == null) {
            delegate = unit.type.flying ? new FlyingAI() : new GroundAI();
            delegate.unit(unit);
        }
        delegate.updateUnit();
    }

    @Override
    public void removed(Unit unit) {
        super.removed(unit);
        if (delegate != null) delegate.removed(unit);
    }
}