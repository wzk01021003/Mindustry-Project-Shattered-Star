package project.graphics;

/**
 * 已废弃：屏幕叠加方案不再需要 FBO / shader 管线。
 * 保留此类是为了 ShatteredStarMod 里的 DistortionRenderer.init() 调用不报错。
 */
public class DistortionRenderer {

    public static void init() {
        // 空实现。屏幕叠加效果由 DistortionFx 直接绘制，不经过此类。
    }

    public static int activeCount() {
        return 0;
    }

    public static int currentFps() {
        return 60;
    }
}