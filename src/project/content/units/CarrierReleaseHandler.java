package project.content.units;

import arc.Events;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.ai.types.CommandAI;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Unit;

public class CarrierReleaseHandler {

    /** 一个"已释放但还在滑行"的单位。 */
    private static class FlyingUnit {
        Unit unit;
        float angle;      // 弧度
        float travel;
        boolean controlGiven;
    }

    /** 一个载具的释放状态。 */
    private static class Queue {
        Seq<Unit> pending = new Seq<>();    // 还没放出来的
        Seq<FlyingUnit> flying = new Seq<>(); // 已放出、正在滑行
        float timer = 0f;
    }

    private static final ObjectMap<Unit, Queue> queues = new ObjectMap<>();
    private static boolean installed = false;

    public static void init() {
        if (installed) return;
        installed = true;
        Events.run(Trigger.update, CarrierReleaseHandler::tick);
    }

    /** 开始释放。 */
    public static void startRelease(Unit host) {
        if (!(host.type instanceof CarrierUnitType)) return;
        if (queues.containsKey(host)) return;

        Seq<Unit> list = CarrierManager.getCarried(host);
        if (list.isEmpty()) return;

        Queue q = new Queue();
        for (Unit p : new Seq<>(list)) {
            if (p != null) q.pending.add(p);
        }

        list.clear();
        CarrierManager.carried.remove(host);
        queues.put(host, q);
    }

    private static void tick() {
        if (queues.isEmpty) return;

        Seq<Unit> toRemove = new Seq<>();

        for (ObjectMap.Entry<Unit, Queue> entry : queues) {
            Unit host = entry.key;
            Queue q = entry.value;

            if (!(host.type instanceof CarrierUnitType) || !host.isValid()) {
                // 载具没了 → 剩下的直接丢在原地
                for (Unit p : q.pending) {
                    if (p == null) continue;
                    p.x = host.x;
                    p.y = host.y;
                    p.add();
                    p.controller(new CommandAI());
                }
                toRemove.add(host);
                continue;
            }

            CarrierUnitType ct = (CarrierUnitType) host.type;

            // ============ 计算出口世界坐标和朝向 ============
            float hostAng = host.rotation - 90f;
            float cos = Mathf.cosDeg(hostAng);
            float sin = Mathf.sinDeg(hostAng);
            float wx = host.x + ct.exitX * cos - ct.exitY * sin;
            float wy = host.y + ct.exitX * sin + ct.exitY * cos;
            float worldAngleDeg = ct.exitRotation + hostAng;
            float worldAngleRad = worldAngleDeg * Mathf.degRad;

            // ============ 释放计时 ============
            q.timer -= Time.delta;
            if (q.timer <= 0f && q.pending.size > 0) {
                q.timer = ct.releaseInterval;

                Unit p = q.pending.remove(0);
                if (p != null) {
                    p.x = wx;
                    p.y = wy;
                    p.rotation = worldAngleDeg;
                    p.add();

                    FlyingUnit f = new FlyingUnit();
                    f.unit = p;
                    f.angle = worldAngleRad;
                    f.travel = 0f;
                    f.controlGiven = false;
                    q.flying.add(f);
                }
            }

            // ============ 滑行中的单位 ============
            float speedPerFrame = ct.releaseSpeed / 60f;
            for (int i = q.flying.size - 1; i >= 0; i--) {
                FlyingUnit f = q.flying.get(i);
                Unit p = f.unit;
                if (p == null || !p.isAdded()) {
                    q.flying.remove(i);
                    continue;
                }

                float dx = Mathf.cos(f.angle) * speedPerFrame * Time.delta;
                float dy = Mathf.sin(f.angle) * speedPerFrame * Time.delta;

                p.x += dx;
                p.y += dy;
                f.travel += speedPerFrame * Time.delta;

                if (!f.controlGiven && f.travel >= ct.releaseDistance) {
                    f.controlGiven = true;
                    p.controller(new CommandAI());
                    q.flying.remove(i);
                }
            }

            // ============ 全部完成 ============
            if (q.pending.isEmpty() && q.flying.isEmpty()) {
                toRemove.add(host);
            }
        }

        for (Unit host : toRemove) queues.remove(host);
    }

    public static boolean isReleasing(Unit host) {
        return queues.containsKey(host);
    }
}