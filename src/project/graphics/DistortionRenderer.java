package project.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Layer;
import project.SSSettings;

public class DistortionRenderer {

    public static final int MAX_GPU_SLOTS = 6;

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
    private static boolean unsupported = false;
    private static boolean capturing = false;
    private static boolean eventsRegistered = false;
    private static int texW = -1, texH = -1;

    private static int addedThisFrame = 0;
    private static long lastFrameId = -1;

    private static float fpsAccum = 0f;
    private static int fpsSamples = 0;
    private static int lastFps = 60;
    private static int downgrade = 0;

    public static void init() {
        if (eventsRegistered) return;
        eventsRegistered = true;
        Events.run(Trigger.update, DistortionRenderer::onUpdate);
        Events.run(Trigger.draw,   DistortionRenderer::onDrawEvent);
    }

    private static void onUpdate() {
        if (unsupported || !SSSettings.enabled()) {
            active.clear();
            return;
        }
        tickFps();
        updateActive();
    }

    // ============================================================
    //  Trigger.draw 里我们把 capture/render 插入到场景绘制的正确时机。
    //  这是 Bloom.java 的做法，能避免与 Mindustry 内部的 framebuffer
    //  切换冲突。
    // ============================================================
    private static void onDrawEvent() {
        if (unsupported || !SSSettings.enabled() || active.size == 0) return;

        // 在场景最前面开始捕获
        Draw.draw(Layer.background - 1f, DistortionRenderer::beginCapture);
        // 在 UI 之前结束捕获并渲染
        Draw.draw(Layer.overlayUI - 1f, DistortionRenderer::endCaptureAndRender);
    }

    private static void beginCapture() {
        int w = Core.graphics.getWidth();
        int h = Core.graphics.getHeight();
        if (w <= 0 || h <= 0) return;

        ensureInit(w, h);
        if (unsupported) return;

        try {
            buffer.begin(Color.clear);
            capturing = true;
        } catch (Throwable t) {
            Log.err("[ss-distort] beginCapture 异常", t);
            unsupported = true;
            capturing = false;
        }
    }

    private static void endCaptureAndRender() {
        if (!capturing) return;
        capturing = false;

        try {
            buffer.end();

            int slots = effectiveSlots();

            float w = Core.graphics.getWidth();
            float h = Core.graphics.getHeight();
            float camX = Core.camera.position.x;
            float camY = Core.camera.position.y;
            float camW = Core.camera.width;
            float camH = Core.camera.height;

            float[] d = new float[24];
            for (int i = 0; i < slots; i++) {
                Data data = active.get(i);
                d[i * 4]     = (data.position.x - camX) / camW * w + w / 2f;
                d[i * 4 + 1] = (data.position.y - camY) / camH * h + h / 2f;
                d[i * 4 + 2] = data.radius / camW * w;
                d[i * 4 + 3] = data.strength;
            }

            shader.bind();
            shader.setUniformi("u_count", slots);
            shader.setUniformf("u_resolution", w, h);
            shader.setUniformf("u_d0", d[0],  d[1],  d[2],  d[3]);
            shader.setUniformf("u_d1", d[4],  d[5],  d[6],  d[7]);
            shader.setUniformf("u_d2", d[8],  d[9],  d[10], d[11]);
            shader.setUniformf("u_d3", d[12], d[13], d[14], d[15]);
            shader.setUniformf("u_d4", d[16], d[17], d[18], d[19]);
            shader.setUniformf("u_d5", d[20], d[21], d[22], d[23]);

            // buffer.blit 会用 ScreenQuad 直接渲染，不走 batch
            buffer.blit(shader);

            if (SSSettings.showDebug()) {
                Log.info("[ss-distort] slots=" + slots + " fps=" + lastFps + " downgrade=" + downgrade);
            }
        } catch (Throwable t) {
            Log.err("[ss-distort] endCaptureAndRender 异常", t);
            unsupported = true;
        }
    }

    private static void ensureInit(int w, int h) {
        if (buffer != null && (texW != w || texH != h)) {
            buffer.dispose();
            buffer = null;
        }

        try {
            if (buffer == null) {
                buffer = new FrameBuffer(w, h);
                texW = w;
                texH = h;
            }
            if (shader == null) {
                shader = createShader();
            }
        } catch (Throwable t) {
            Log.err("[ss-distort] 初始化失败", t);
            unsupported = true;
        }
    }

    private static Shader createShader() {
        // ScreenQuad 直接传 NDC 坐标，不需要 u_proj
        String vertex =
            "attribute vec4 a_position;\n" +
            "attribute vec2 a_texCoord0;\n" +
            "varying vec2 v_texCoords;\n" +
            "void main(){\n" +
            "    v_texCoords = a_texCoord0;\n" +
            "    gl_Position = a_position;\n" +
            "}\n";

        String fragment =
            "varying vec2 v_texCoords;\n" +
            "uniform sampler2D u_texture;\n" +
            "uniform vec2 u_resolution;\n" +
            "uniform int u_count;\n" +
            "uniform vec4 u_d0;\n" +
            "uniform vec4 u_d1;\n" +
            "uniform vec4 u_d2;\n" +
            "uniform vec4 u_d3;\n" +
            "uniform vec4 u_d4;\n" +
            "uniform vec4 u_d5;\n" +
            "\n" +
            "vec2 applyDistort(vec2 screenPos, vec4 d){\n" +
            "    vec2 diff = screenPos - d.xy;\n" +
            "    float dist = length(diff);\n" +
            "    if(dist < d.z && dist > 0.001){\n" +
            "        float t = 1.0 - dist / d.z;\n" +
            "        t = t * t;\n" +
            "        return diff / dist * t * d.w * 20.0;\n" +
            "    }\n" +
            "    return vec2(0.0);\n" +
            "}\n" +
            "\n" +
            "void main(){\n" +
            "    vec2 uv = v_texCoords;\n" +
            "    vec2 screenPos = uv * u_resolution;\n" +
            "    vec2 offset = vec2(0.0);\n" +
            "    if(u_count > 0) offset += applyDistort(screenPos, u_d0);\n" +
            "    if(u_count > 1) offset += applyDistort(screenPos, u_d1);\n" +
            "    if(u_count > 2) offset += applyDistort(screenPos, u_d2);\n" +
            "    if(u_count > 3) offset += applyDistort(screenPos, u_d3);\n" +
            "    if(u_count > 4) offset += applyDistort(screenPos, u_d4);\n" +
            "    if(u_count > 5) offset += applyDistort(screenPos, u_d5);\n" +
            "    vec2 distortedUv = uv + offset / u_resolution;\n" +
            "    vec4 c = texture2D(u_texture, distortedUv);\n" +
            "    gl_FragColor = vec4(c.rgb, 1.0);\n" +
            "}\n";

        return new Shader(vertex, fragment);
    }

    private static void updateActive() {
        for (int i = active.size - 1; i >= 0; i--) {
            Data data = active.get(i);
            data.elapsed += Time.delta;
            if (data.elapsed >= data.lifetime) {
                active.remove(i);
                continue;
            }
            float t = 1f - data.elapsed / data.lifetime;
            data.radius = data.maxRadius * t;
        }
    }

    public static void addDistortion(float x, float y, float radius, float strength, float lifetime) {
        if (!SSSettings.enabled() || unsupported) return;

        resetFrameCounter();
        if (addedThisFrame >= SSSettings.maxPerFrame()) return;
        addedThisFrame++;

        if (!Float.isFinite(x) || !Float.isFinite(y)
            || !Float.isFinite(radius) || !Float.isFinite(strength) || !Float.isFinite(lifetime)) return;
        if (radius <= 0f || strength <= 0f || lifetime <= 0f) return;

        radius   = Math.min(radius,   SSSettings.maxRadius());
        strength = Math.min(strength, SSSettings.maxStrength());

        if (SSSettings.cullOffscreen() && isOffscreen(x, y, radius)) return;

        int max = SSSettings.maxConcurrent();
        while (active.size >= max) active.remove(0);

        Data data = new Data();
        data.position.set(x, y);
        data.maxRadius = data.radius = radius;
        data.strength = strength;
        data.lifetime = lifetime;
        data.elapsed = 0f;
        active.add(data);
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
        if (downgrade >= 1) n = Math.min(n, 4);
        if (downgrade >= 2) n = Math.min(n, 2);
        if (downgrade >= 3) n = Math.min(n, 1);
        return Math.min(n, MAX_GPU_SLOTS);
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
        texW = texH = -1;
    }

    public static int activeCount() {
        return active.size;
    }

    public static int currentFps() {
        return lastFps;
    }
}