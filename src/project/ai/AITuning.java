package project.ai;

import arc.Core;
import arc.Events;
import arc.util.Log;
import mindustry.game.EventType.Trigger;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;

import static mindustry.Vars.content;

public class AITuning {

    public static float targetInterval = 1f;

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

        if (newInterval != lastAppliedInterval) {
            lastAppliedInterval = newInterval;
            applyToTurrets(newInterval);
        }
    }

    private static void applyToTurrets(float interval) {
        int count = 0;
        for (int i = 0; i < content.blocks().size; i++) {
            Block b = content.blocks().get(i);
            if (b == null) continue;
            if (!(b instanceof Turret)) continue;

            Turret t = (Turret) b;
            t.targetInterval = interval;
            t.targetSwitchInterval = interval;
            count++;
        }
        Log.info("[ss] Applied targetInterval=" + interval + " to " + count + " turrets.");
    }
}