package project.content.units;

import mindustry.gen.Unit;

public class TransportUnloadHandler {

    /** 触发载具开始释放挂载单位。 */
    public static void unloadAll(Unit host) {
        CarrierReleaseHandler.startRelease(host);
    }

    /** 立即全部释放（用于载具死亡等紧急情况）。 */
    public static void unloadAllInstant(Unit host) {
        CarrierReleaseHandler.startRelease(host);
    }
}