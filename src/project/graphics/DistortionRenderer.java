package project.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import mindustry.game.EventType.Trigger;
import project.SSSettings;

public class DistortionRenderer {

    public static final int MAX_GPU_SLOTS = 16;

    public static class Data {
        public final Vec2 position = new Vec2();
        public float radius, maxRadius;
        public float strength;
        public float lifetime;
        public float elapsed;
    }

    private static final Seq<Data> active = new Seq<>();
    private static FrameBuffer buffer;
    private static Shader shader;
    private static boolean initialized = false;
    private static boolean unsupported = false;

    // 每帧添加计数器
    private static int addedThisFrame = 0;
    private static long lastFrameId = -1;

    // 自动降级
    private static float fpsAccum = 0f;
    private static int fpsSamples = 0;
    private static int lastFps = 60;
    private static int downgrade = 0;

    public static void init() {
        if (initialized || unsupported) return;

        Events.run(Trigger.draw, () -> {
                if (unsupported || !SSSettings.enabled()) {
                    active.clear();
                    return;
                }
                try {
                    ensureInit();
                    if (unsupported) return;
                    tickFps();
                    renderPass();
                } catch (Throwable t) {
                    Log.err("[ss-distort] 运行异常，已自动禁用", t);
                    unsupported = true;
                    dispose();
                }
            });
    }

    private static void ensureInit() {
        if (initialized) return;
        if (Core.graphics == null) return;

        int w = Core.graphics.getWidth();
        int h = Core.graphics.getHeight();
        if (w <= 0 || h <= 0) return;

        try {
            buffer = new FrameBuffer(w, h);
            shader = createShader();
            initialized = true;
        } catch (Throwable t) {
            unsupported = true;
            dispose();
        }
    }

    // ============================================================
    //  外部调用入口 —— 所有限制都在这里
    // ============================================================
    public static void addDistortion(float x, float y, float radius, float strength, float lifetime) {
        if (!SSSettings.enabled() || unsupported) return;

        // 1. 每帧数量上限
        resetFrameCounter();
        if (addedThisFrame >= SSSettings.maxPerFrame()) return;
        addedThisFrame++;

        // 2. 数据健全性检查
        if (!Float.isFinite(x) || !Float.isFinite(y)
            || !Float.isFinite(radius) || !Float.isFinite(strength) || !Float.isFinite(lifetime)) return;
        if (radius <= 0f || strength <= 0f || lifetime <= 0f) return;

        // 3. 数值 clamp
        radius   = Math.min(radius,   SSSettings.maxRadius());
        strength = Math.min(strength, SSSettings.maxStrength());

        // 4. 屏幕外剔除
        if (SSSettings.cullOffscreen() && isOffscreen(x, y, radius)) return;

        // 5. 并发数量上限，超出丢最旧
        int max = SSSettings.maxConcurrent();
        while (active.size >= max) active.remove(0);

        Data d = new Data();
        d.position.set(x, y);
        d.maxRadius = d.radius = radius;
        d.strength = strength;
        d.lifetime = lifetime;
        d.elapsed = 0f;
        active.add(d);
    }

    private static void resetFrameCounter() {
        long fid = Core.graphics.getFrameId();
        if (fid != lastFrameId) {
            lastFrameId = fid;
            addedThisFrame = 0;
        }
    }

    private static boolean isOffscreen(float x, float y, float radius) {
        if (Core.camera == null) return false;
        float hw = Core.camera.width / 2f + radius + 64f;
        float hh = Core.camera.height / 2f + radius + 64f;
        float cx = Core.camera.position.x;
        float cy = Core.camera.position.y;
        return Math.abs(x - cx) > hw || Math.abs(y - cy) > hh;
    }

    // ============================================================
    //  自动降级
    // ============================================================
    private static void tickFps() {
        if (!SSSettings.autoDisable()) {
            downgrade = 0;
            return;
        }

        fpsAccum += Time.delta;
        fpsSamples++;
        if (fpsSamples >= 30) {
            lastFps = (int) (30f / Math.max(fpsAccum, 0.0001f));
            fpsAccum = 0;
            fpsSamples = 0;

            if (lastFps < 25 && downgrade < 3) downgrade++;
            else if (lastFps > 45 && downgrade > 0) downgrade--;
        }
    }

    private static int effectiveSlots() {
        int n = active.size;
        if (downgrade >= 1) n = Math.min(n, 12);
        if (downgrade >= 2) n = Math.min(n, 6);
        if (downgrade >= 3) n = Math.min(n, 3);
        return Math.min(n, MAX_GPU_SLOTS);
    }

    // ============================================================
    //  渲染
    //  注意：真正把 buffer 画回屏幕的那一步，需要你自己接。
    //  参见文件末尾注释。
    // ============================================================
    private static void renderPass() {
        // 生命周期推进
        for (int i = active.size - 1; i >= 0; i--) {
            Data d = active.get(i);
            d.elapsed += Time.delta;
            if (d.elapsed >= d.lifetime) {
                active.remove(i);
                continue;
            }
            float t = 1f - d.elapsed / d.lifetime;
            d.radius = d.maxRadius * t;
        }

        int slots = effectiveSlots();
        if (slots == 0) return;

        // TODO: 把 buffer 内容用 shader 处理并画回屏幕
        // 参考实现方式：
        //   1. 抓屏到 buffer（glCopyTexImage2D 或 renderer 提供的 hook）
        //   2. shader.bind();
        //      shader.setUniformi("u_texture", 0);
        //      shader.setUniformf("u_resolution", w, h);
        //      shader.setUniformi("u_count", slots);
        //      for (int i = 0; i < slots; i++) {
            //          Data d = active.get(i);
            //          shader.setUniformf("u_centers[" + i + "]", d.position.x, d.position.y);
            //          shader.setUniformf("u_radii[" + i + "]", d.radius);
            //          shader.setUniformf("u_strengths[" + i + "]", d.strength * (1f - d.elapsed / d.lifetime));
            //      }
        //      buffer.getTexture().bind(0);
        //      Draw.blit(buffer.getTexture(), shader);
        //      Draw.flush();
        //
        // 建议直接参考 MEEPofFaith/MindustryBlackHoleRenderer 的接入方式。

        if (SSSettings.showDebug()) {
            Log.info("[ss-distort] slots=" + slots + " fps=" + lastFps + " downgrade=" + downgrade);
        }
    }

    private static Shader createShader() {
        String v =
        "attribute vec4 a_position;\n" +
        "attribute vec2 a_texCoord0;\n" +
        "uniform mat4 u_projTrans;\n" +
        "varying vec2 v_texCoords;\n" +
        "void main(){\n" +
        "  v_texCoords = a_texCoord0;\n" +
        "  gl_Position = u_projTrans * a_position;\n" +
        "}\n";

        StringBuilder f = new StringBuilder();
        f.append("precision mediump float;\n");
        f.append("uniform sampler2D u_texture;\n");
        f.append("uniform vec2 u_resolution;\n");
        f.append("uniform int u_count;\n");
        f.append("uniform vec2 u_centers[").append(MAX_GPU_SLOTS).append("];\n");
        f.append("uniform float u_radii[").append(MAX_GPU_SLOTS).append("];\n");
        f.append("uniform float u_strengths[").append(MAX_GPU_SLOTS).append("];\n");
        f.append("varying vec2 v_texCoords;\n");
        f.append("void main(){\n");
        f.append("  vec2 uv = v_texCoords;\n");
        f.append("  vec2 offset = vec2(0.0);\n");
        f.append("  for (int i = 0; i < ").append(MAX_GPU_SLOTS).append("; i++){\n");
        f.append("    if (i >= u_count) break;\n");
        f.append("    vec2 center = u_centers[i] / u_resolution;\n");
        f.append("    float radiusN = u_radii[i] / u_resolution.x;\n");
        f.append("    if (radiusN < 0.0001) continue;\n");
        f.append("    float d = distance(uv, center);\n");
        f.append("    if (d < radiusN){\n");
        f.append("      float fall = 1.0 - (d / radiusN);\n");
        f.append("      fall = fall * fall;\n");
        f.append("      vec2 dir = normalize(uv - center + vec2(0.0001));\n");
        f.append("      offset += dir * fall * u_strengths[i] * 0.05;\n");
        f.append("    }\n");
        f.append("  }\n");
        f.append("  gl_FragColor = texture2D(u_texture, uv + offset);\n");
        f.append("}\n");

        return new Shader(v, f.toString());
    }

    public static void dispose() {
        if (buffer != null) {
            buffer.dispose();
            buffer = null;
        }
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        initialized = false;
    }

    public static int activeCount() {
        return active.size();
    }
    public static int currentFps()  {
        return lastFps;
    }
}