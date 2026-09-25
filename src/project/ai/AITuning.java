package project.ai;

import arc.Core;
import arc.Events;
import arc.util.Log;
import mindustry.game.EventType.Trigger;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;

import static mindustry.Vars.content;

/**
 * 全局 AI 调优：
 *  - 根据 FPS 动态决定索敌间隔
 *  - 应用到所有炮塔 + 所有使用 AITuning.targetInterval 的 AI
 *
 * FPS 档位：
 *   >= 55  → 1 帧（每帧索敌）
 *   >= 45  → 2 帧
 *   >= 35  → 3 帧
 *   >= 25  → 5 帧
 *   <  25  → 10 帧
 */
public class AITuning {

    /** 当前索敌间隔（帧）。所有 AI 应读取这个值 */
    public static float targetInterval = 1f;

    /** 上次应用到炮塔的 interval，避免每帧都遍历 content */
    private static float lastAppliedInterval = -1f;

    private static int sampleFrames = 0;
    private static boolean inited = false;

    public static void init() {
        if (inited) return;
        inited = true;
        Events.run(Trigger.update, AITuning::tick);
        Log.info("[ss] AITuning initialized.");
    }

    private static void tick() {
        // 每 60 帧采样一次，避免 FPS 抖动导致频繁切换
        sampleFrames++;
        if (sampleFrames < 60) return;
        sampleFrames = 0;

        float fps = Core.graphics.getFramesPerSecond();

        float newInterval;
        if (fps >= 55f) newInterval = 1f;
        else if (fps >= 45f) newInterval = 2f;
        else if (fps >= 35f) newInterval = 3f;
        else if (fps >= 25f) newInterval = 5f;
        else newInterval = 10f;

        if (newInterval != targetInterval) {
            targetInterval = newInterval;
            Log.info("[ss] FPS=" + (int) fps + " -> targetInterval=" + newInterval);
        }

        // 只在 interval 变化时重写炮塔字段
        if (newInterval != lastAppliedInterval) {
            lastAppliedInterval = newInterval;
            applyToTurrets(newInterval);
        }
    }

    private static void applyToTurrets(float interval) {
        int count = 0;
        for (Block b : content.blocks()) {
            if (b instanceof Turret t) {
                t.targetInterval = interval;
                t.targetSwitchInterval = interval;
                count++;
            }
        }
        Log.info("[ss] Applied targetInterval=" + interval + " to " + count + " turrets.");
    }
}