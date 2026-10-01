package project.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Layer;
import project.SSSettings;

public class DistortionRenderer {

    public static final int MAX_GPU_SLOTS = 16;

    public static class Data {
        public final Vec2 position = new Vec2();
        public float radius, maxRadius;
        public float strength;
        public float lifetime;
        public float elapsed;
        public int type;
        public float radiusFrom, radiusTo;
        public float ringWidth;
        public Interp interp;
        // 运行时 fade 值（随生命周期衰减）
        public float currentStrength;
        public float currentRingWidth;
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

    private static final String[] D_NAMES = new String[MAX_GPU_SLOTS];
    private static final String[] E_NAMES = new String[MAX_GPU_SLOTS];
    static {
        for (int i = 0; i < MAX_GPU_SLOTS; i++) {
            D_NAMES[i] = "u_d" + i;
            E_NAMES[i] = "u_e" + i;
        }
    }

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

    private static void onDrawEvent() {
        if (unsupported || !SSSettings.enabled() || active.size == 0) return;

        Draw.draw(Layer.background - 1f, DistortionRenderer::beginCapture);
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

            float[] d = new float[MAX_GPU_SLOTS * 4];
            float[] e = new float[MAX_GPU_SLOTS * 4];

            for (int i = 0; i < slots; i++) {
                Data data = active.get(i);
                d[i * 4]     = (data.position.x - camX) / camW * w + w / 2f;
                d[i * 4 + 1] = (data.position.y - camY) / camH * h + h / 2f;
                d[i * 4 + 2] = data.radius / camW * w;
                d[i * 4 + 3] = data.currentStrength;
                // ← 用 fade 后的强度

                float progress = data.lifetime > 0f
                ? Math.min(1f, data.elapsed / data.lifetime)
                : 1f;

                float ringPx = data.currentRingWidth * data.maxRadius / camW * w;
                // ← fade 后的环宽

                e[i * 4]     = data.type;
                e[i * 4 + 1] = progress;
                e[i * 4 + 2] = ringPx;
                e[i * 4 + 3] = 0f;
            }

            shader.bind();
            shader.setUniformi("u_count", slots);
            shader.setUniformf("u_resolution", w, h);

            for (int i = 0; i < MAX_GPU_SLOTS; i++) {
                shader.setUniformf(D_NAMES[i], d[i * 4], d[i * 4 + 1], d[i * 4 + 2], d[i * 4 + 3]);
                shader.setUniformf(E_NAMES[i], e[i * 4], e[i * 4 + 1], e[i * 4 + 2], e[i * 4 + 3]);
            }

            buffer.blit(shader);

            if (SSSettings.showDebug()) {
                Log.info("[ss-distort] slots=" + slots + "/" + active.size
                    + " fps=" + lastFps + " downgrade=" + downgrade);
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
        String vertex =
        "attribute vec4 a_position;\n" +
        "attribute vec2 a_texCoord0;\n" +
        "varying vec2 v_texCoords;\n" +
        "void main(){\n" +
        "    v_texCoords = a_texCoord0;\n" +
        "    gl_Position = a_position;\n" +
        "}\n";

        StringBuilder uniformDecl = new StringBuilder();
        uniformDecl.append("varying vec2 v_texCoords;\n");
        uniformDecl.append("uniform sampler2D u_texture;\n");
        uniformDecl.append("uniform vec2 u_resolution;\n");
        uniformDecl.append("uniform int u_count;\n");
        for (int i = 0; i < MAX_GPU_SLOTS; i++) {
            uniformDecl.append("uniform vec4 u_d").append(i).append(";\n");
        }
        for (int i = 0; i < MAX_GPU_SLOTS; i++) {
            uniformDecl.append("uniform vec4 u_e").append(i).append(";\n");
        }

        StringBuilder mainBody = new StringBuilder();
        mainBody.append("void main(){\n");
        mainBody.append("    vec2 uv = v_texCoords;\n");
        mainBody.append("    vec2 screenPos = uv * u_resolution;\n");
        mainBody.append("    vec2 offset = vec2(0.0);\n");
        for (int i = 0; i < MAX_GPU_SLOTS; i++) {
            mainBody.append("    if(u_count > ").append(i)
            .append(") offset += applyDistort(screenPos, u_d").append(i)
            .append(", u_e").append(i).append(");\n");
        }
        mainBody.append("    vec2 distortedUv = uv + offset / u_resolution;\n");
        mainBody.append("    vec4 c = texture2D(u_texture, distortedUv);\n");
        mainBody.append("    gl_FragColor = vec4(c.rgb, 1.0);\n");
        mainBody.append("}\n");

        // 关键改动：环厚阈值从 0.001 改成 0.5（像素级），
        // 避免环宽 fade 到很小时突然变成实心圆。
        String applyFn =
        "#define PI 3.14159265\n" +
        "\n" +
        "vec2 applyDistort(vec2 screenPos, vec4 d, vec4 e){\n" +
        "    vec2 diff = screenPos - d.xy;\n" +
        "    float dist = length(diff);\n" +
        "    if(dist <= 0.001) return vec2(0.0);\n" +
        "\n" +
        "    float ringThickness = e.z;\n" +
        "    float falloff;\n" +
        "\n" +
        "    if(ringThickness > 0.5){\n" +
        "        float halfWidth = ringThickness * 0.5;\n" +
        "        float ringDist = abs(dist - d.z);\n" +
        "        if(ringDist >= halfWidth) return vec2(0.0);\n" +
        "        float t = 1.0 - ringDist / halfWidth;\n" +
        "        falloff = t * t;\n" +
        "    } else {\n" +
        "        if(dist >= d.z) return vec2(0.0);\n" +
        "        float t = 1.0 - dist / d.z;\n" +
        "        falloff = t * t;\n" +
        "    }\n" +
        "\n" +
        "    int type = int(e.x + 0.5);\n" +
        "    float progress = e.y;\n" +
        "\n" +
        "    float dir = 1.0;\n" +
        "    float mag = 1.0;\n" +
        "\n" +
        "    if(type == 0){\n" +
        "        dir = 1.0;\n" +
        "    }else if(type == 1){\n" +
        "        dir = -1.0;\n" +
        "    }else if(type == 2){\n" +
        "        dir = cos(progress * PI);\n" +
        "    }else if(type == 3){\n" +
        "        dir = -cos(progress * PI);\n" +
        "    }else if(type == 4){\n" +
        "        float delay = 0.3;\n" +
        "        mag = max(0.0, (progress - delay) / (1.0 - delay));\n" +
        "        dir = -1.0;\n" +
        "    }\n" +
        "\n" +
        "    return diff / dist * falloff * d.w * mag * dir * 20.0;\n" +
        "}\n" +
        "\n";

        String fragment = uniformDecl.toString() + "\n" + applyFn + mainBody.toString();

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
            float progress = data.elapsed / data.lifetime;
            float curve = data.interp.apply(progress);
            data.radius = data.maxRadius * Mathf.lerp(data.radiusFrom, data.radiusTo, curve);

            // ============================================================
            //  ★ 关键改动：后 50% 开始淡出，强度和环宽一起平滑归零
            // ============================================================
            float fadeT = Mathf.clamp((progress - 0.5f) / 0.5f);
            float fade = 1f - fadeT * fadeT;
            // 1 - t²，先慢后快的下降曲线
            data.currentStrength = data.strength * fade;
            data.currentRingWidth = data.ringWidth * fade;
        }
    }

    public static void addDistortion(float x, float y, float radius, float strength,
        float lifetime, int type,
        float radiusFrom, float radiusTo,
        float ringWidth, Interp interp) {
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
        data.type = type;
        data.radiusFrom = radiusFrom;
        data.radiusTo = radiusTo;
        data.ringWidth = ringWidth;
        data.interp = interp;
        data.currentStrength = strength;
        // ← 初始值
        data.currentRingWidth = ringWidth;
        // ← 初始值
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
        if (downgrade >= 1) n = Math.min(n, 8);
        if (downgrade >= 2) n = Math.min(n, 4);
        if (downgrade >= 3) n = Math.min(n, 2);
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