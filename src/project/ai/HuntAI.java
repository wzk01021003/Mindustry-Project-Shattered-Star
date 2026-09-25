package project.ai;

import mindustry.ai.types.FlyingAI;
import mindustry.ai.types.GroundAI;
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;

/**
 * 狩猎 AI：给 ss-hunt / ss-dogfight 指令用的控制器。
 *
 * 内部 = SmartAIWrapper 包着原版 AI：
 *   - 飞行 → SmartAIWrapper(FlyingAI)
 *   - 其他 → SmartAIWrapper(GroundAI)
 *
 * 效果：单位用原版 AI 的行为（自动找核心 / 打敌人 / 绕墙），
 * 并且带防卡死救援。
 */
public class HuntAI extends AIController {

    protected AIController delegate;

    /** 兼容字段：ss-dogfight 指令会设置它（目前不影响行为） */
    public boolean forceDogfighter = false;

    protected AIController getDelegate() {
        if (delegate == null) {
            AIController inner = unit.type.flying ? new FlyingAI() : new GroundAI();
            delegate = new SmartAIWrapper(inner);
            delegate.unit(unit);
        }
        return delegate;
    }

    @Override
    public void updateUnit() {
        getDelegate().updateUnit();
    }

    @Override
    public void removed(Unit unit) {
        super.removed(unit);
        if (delegate != null) delegate.removed(unit);
    }
}