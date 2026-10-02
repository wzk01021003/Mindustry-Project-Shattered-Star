package project.content.units;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.ai.types.CommandAI;
import mindustry.gen.Unit;

public class TransportUnloadHandler {

    public static float unloadRadius = 70f;

    public static void unloadAll(Unit host) {
        Seq<Unit> list = CarrierManager.getCarried(host);
        if (list.isEmpty()) return;

        int total = list.size;
        Unit[] snapshot = new Unit[total];
        for (int i = 0; i < total; i++) snapshot[i] = list.get(i);

        for (int i = 0; i < total; i++) {
            Unit p = snapshot[i];
            if (p == null || !p.isValid()) continue;

            float ang = host.rotation - 90f + i * 360f / total;
            p.x = host.x + Mathf.cosDeg(ang) * unloadRadius;
            p.y = host.y + Mathf.sinDeg(ang) * unloadRadius;
            p.rotation = host.rotation;

            // ★ 换成 CommandAI，玩家能立即用指挥模式控制
            CommandAI cai = new CommandAI();
            cai.unit(p);
            cai.targetPos = null;
            cai.attackTarget = null;
            p.controller(cai);

            CarrierManager.detach(p);
        }
    }
}