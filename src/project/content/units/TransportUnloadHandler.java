package project.content.units;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.ai.types.FlyingAI;
import mindustry.ai.types.GroundAI;
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

            // ★ 清空指挥状态，防止往旧命令点跑
            if (p.isCommandable()) {
                p.command().targetPos = null;
                p.command().attackTarget = null;
                p.command().command(UnitCommand.moveCommand);  // 重置为默认移动
            }

            p.controller(p.type.flying ? new FlyingAI() : new GroundAI());
            CarrierManager.detach(p);
        }
    }
}