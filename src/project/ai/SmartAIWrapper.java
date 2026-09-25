package project.ai;

import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;

/**
 * 智能 AI 包装器：
 * 不替换原版 AI，而是在原版 AI 之上叠加一层"防卡死"逻辑。
 */
public class SmartAIWrapper extends AIController {

    public final AIController delegate;

    // 卡住检测
    protected float lastX = Float.NaN, lastY = Float.NaN;
    protected float stuckTimer = 0f;

    public static float stuckThreshold = 1.5f;
    public static float minMovePerFrame = 0.5f;
    public static float rescueSpeedMul = 1.5f;

    public SmartAIWrapper(AIController delegate) {
        this.delegate = delegate;
    }

    @Override
    public void updateUnit() {
        if (delegate != null) {
            if (delegate.unit() != unit) delegate.unit(unit);
            delegate.updateUnit();
        }
        antiStuck();
    }

    protected void antiStuck() {
        if (unit == null || !unit.isAdded()) return;

        if (Float.isNaN(lastX)) {
            lastX = unit.x;
            lastY = unit.y;
            return;
        }

        float moved = Mathf.dst(unit.x, unit.y, lastX, lastY);
        // 有速度向量（想动）但位置没变 → 卡住
        boolean wantsToMove = unit.vel.len2() > 0.01f;

        if (wantsToMove && moved < minMovePerFrame * Time.delta) {
            stuckTimer += Time.delta;
        } else {
            stuckTimer = 0f;
        }

        lastX = unit.x;
        lastY = unit.y;

        if (stuckTimer > 60f * stuckThreshold) {
            stuckTimer = 0f;
            rescue();
        }
    }

    protected void rescue() {
        if (unit == null) return;
        float angle = Mathf.random(360f);
        Tmp.v1.trns(angle, unit.speed() * rescueSpeedMul);
        unit.movePref(Tmp.v1);
    }

    @Override
    public void removed(Unit unit) {
        super.removed(unit);
        if (delegate != null) {
            delegate.removed(unit);
        }
    }
}