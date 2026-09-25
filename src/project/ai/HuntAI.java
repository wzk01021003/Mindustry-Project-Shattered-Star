package project.ai;

import mindustry.ai.types.FlyingAI;
import mindustry.ai.types.GroundAI;
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;

/**
 * 狩猎 AI：完全委托给原版 AI。
 * 飞行单位 → FlyingAI（原版飞行单位 AI）
 * 其他单位 → GroundAI（原版地面单位 AI，自动寻路绕开墙 / 建筑）
 *
 * 不添加任何自定义逻辑。
 */
public class HuntAI extends AIController {

    protected AIController delegate;

    /**
     * 兼容字段：ss-dogfight 指令会设置它。
     * 目前不影响行为（原版 GroundAI 本身就是"接近并打"）。
     */
    public boolean forceDogfighter = false;

    protected AIController getDelegate() {
        if (delegate == null) {
            delegate = unit.type.flying ? new FlyingAI() : new GroundAI();
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