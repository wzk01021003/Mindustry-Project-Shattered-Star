package project.ai;

import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;

/**
 * 智能 AI 包装器：
 * 不替换原版 AI，而是在原版 AI 之上叠加一层"防卡死"逻辑。
 *
 * 机制：
 *   - updateUnit() 里先调 delegate.updateUnit()，让原 AI 决定目标、移动、射击
 *   - 然后检查单位位置：若连续 X 秒"想动但没动"，判定卡住
 *   - 卡住时施加一次随机推力，让原 AI 重新规划路径
 *
 * 用法：
 *   unit.controller(new SmartAIWrapper(原 controller))
 */
public class SmartAIWrapper extends AIController {

    /** 原版 AI（GroundAI / FlyingAI / 或模组 AI） */
    public final AIController delegate;

    // 卡住检测
    protected float lastX = Float.NaN, lastY = Float.NaN;
    protected float stuckTimer = 0f;

    /** 卡住多久后触发救援（秒） */
    public static float stuckThreshold = 1.5f;
    /** 一帧移动少于这个值算"没动" */
    public static float minMovePerFrame = 0.5f;
    /** 救援推力倍率（相对于单位速度） */
    public static float rescueSpeedMul = 1.5f;

    public SmartAIWrapper(AIController delegate) {
        this.delegate = delegate;
    }

    @Override
    public void updateUnit() {
        // 1. 让原版 AI 决定一切（目标、移动、射击）
        if (delegate != null) {
            if (delegate.unit() != unit) delegate.unit(unit);
            delegate.updateUnit();
        }
        // 2. 追加：检测卡住 + 救援
        antiStuck();
    }

    protected void antiStuck() {
        if (unit == null || !unit.isAdded()) return;

        // 第一帧只初始化位置
        if (Float.isNaN(lastX)) {
            lastX = unit.x;
            lastY = unit.y;
            return;
        }

        float moved = Mathf.dst(unit.x, unit.y, lastX, lastY);
        boolean wantsToMove = unit.vel.len2() > 0.01f || unit.isMoving();

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

    /** 卡住救援：往随机方向推一下，让原 AI 重新规划路径 */
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