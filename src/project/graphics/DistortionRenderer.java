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

/**
 * 屏幕空间扭曲渲染器（实验性渲染）。
 *
 * 视觉参考：Rusted Warfare 1.15 的 shader 效果（teleport / water ripples /
 * nuke shockwaves）与未来科技风格的等离子能量武器——
 *  1. 径向冲击位移（内吸 / 外推 / 回弹）
 *  2. 切向涡旋分量（空间折叠感，铁锈传送门同款）
 *  3. 沿位移方向的 RGB 色散（能量场标志性观感）
 *
 * 性能设计：
 *  - 半分辨率离屏渲染（低 1/4、中 1/2、高 全屏），扭曲是低频信号，
 *    上采样时 bilinear 自动平滑，肉眼几乎无损而填充率降 75%+
 *  - Data 对象池化，运行期零分配
 *  - uniform 只上传实际生效的 slot 数（旧版恒定上传 32 个 vec4）
 *  - 调试日志 30 帧节流
 */
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
        /** 切向涡旋强度系数，0 = 纯径向，正值逆时针，负值顺时针 */
        public float swirl;
        public Interp interp;
        // 运行时 fade 值（随生命周期衰减，仅作用于强度）
        public float currentStrength;
    }

    private static final Seq<Data> active = new Seq<>();
    /** Data 对象池，避免每次触发都 new；池有上限，防止无限增长 */
    private static final Seq<Data> pool = new Seq<>();
    private static final int POOL_LIMIT = 64;

    private static FrameBuffer buffer;
    private static Shader shader;
    private static boolean unsupported = false;
    private static boolean capturing = false;
    private static boolean eventsRegistered = false;
    private static int texW = -1, texH = -1;
    private static float texScale = -1f;

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

    /** 复用的 uniform 上传缓冲，避免每帧分配 */
    private static final float[] dBuf = new float[MAX_GPU_SLOTS * 4];
    private static final float[] eBuf = new float[MAX_GPU_SLOTS * 4];

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

            for (int i = 0; i < slots; i++) {
                Data data = active.get(i);
                // 屏幕像素坐标（uv * u_resolution 同坐标系）
                dBuf[i * 4]     = (data.position.x - camX) / camW * w + w / 2f;
                dBuf[i * 4 + 1] = (data.position.y - camY) / camH * h + h / 2f;
                dBuf[i * 4 + 2] = data.radius / camW * w;
                dBuf[i * 4 + 3] = data.currentStrength;
                // ← 用 fade 后的强度

                float progress = data.lifetime > 0f
                ? Math.min(1f, data.elapsed / data.lifetime)
                : 1f;

                eBuf[i * 4]     = data.type;
                eBuf[i * 4 + 1] = progress;
                // 环宽按「半径比例」传入（0 = 实心），shader 内统一处理
                eBuf[i * 4 + 2] = data.ringWidth;
                // 切向涡旋系数
                eBuf[i * 4 + 3] = data.swirl;
            }

            shader.bind();
            shader.setUniformi("u_count", slots);
            shader.setUniformf("u_resolution", w, h);
            shader.setUniformf("u_chroma", SSSettings.chromatic() ? 1f : 0f);
            // 只上传实际生效的 slot；未使用的 slot 的 d.z 为 0，
            // shader 内有 d.z <= 0.001 的早退，双保险
            for (int i = 0; i < slots; i++) {
                shader.setUniformf(D_NAMES[i], dBuf[i * 4], dBuf[i * 4 + 1], dBuf[i * 4 + 2], dBuf[i * 4 + 3]);
                shader.setUniformf(E_NAMES[i], eBuf[i * 4], eBuf[i * 4 + 1], eBuf[i * 4 + 2], eBuf[i * 4 + 3]);
            }

            buffer.blit(shader);

            // 调试日志 30 帧节流，避免每帧字符串拼接 + IO
            if (SSSettings.showDebug() && Core.graphics.getFrameId() % 30 == 0) {
                Log.info("[ss-distort] slots=" + slots + "/" + active.size
                    + " fps=" + lastFps + " downgrade=" + downgrade
                    + " scale=" + renderScale());
            }
        } catch (Throwable t) {
            Log.err("[ss-distort] endCaptureAndRender 异常", t);
            unsupported = true;
        }
    }

    private static void ensureInit(int w, int h) {
        float scale = renderScale();
        int bw = Math.max(1, (int) (w * scale));
        int bh = Math.max(1, (int) (h * scale));

        if (buffer != null && (texW != bw || texH != bh || texScale != scale)) {
            buffer.dispose();
            buffer = null;
        }

        try {
            if (buffer == null) {
                buffer = new FrameBuffer(bw, bh);
                texW = bw;
                texH = bh;
                texScale = scale;
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
        // 色散总开关（0 = 关闭，走单次采样快路径）
        uniformDecl.append("uniform float u_chroma;\n");
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
        mainBody.append("    vec2 duv = offset / u_resolution;\n");
        // 色散：沿位移方向做 RGB 分离，位移越大分离越明显。
        // 三次采样同时起到强位移下的抗锯齿作用。
        mainBody.append("    if(u_chroma > 0.001){\n");
        mainBody.append("        vec2 nd = length(offset) > 0.0001 ? normalize(offset) : vec2(0.0);\n");
        mainBody.append("        float spread = u_chroma * 0.012 * (1.0 + length(offset) * 0.08);\n");
        mainBody.append("        float rC = texture2D(u_texture, uv + duv + nd * spread).r;\n");
        mainBody.append("        float gC = texture2D(u_texture, uv + duv).g;\n");
        mainBody.append("        float bC = texture2D(u_texture, uv + duv - nd * spread).b;\n");
        mainBody.append("        gl_FragColor = vec4(rC, gC, bC, 1.0);\n");
        mainBody.append("    }else{\n");
        mainBody.append("        gl_FragColor = vec4(texture2D(u_texture, uv + duv).rgb, 1.0);\n");
        mainBody.append("    }\n");
        mainBody.append("}\n");

        // 单个扭曲源的位移函数。
        // d = (屏幕x, 屏幕y, 半径px, 强度)
        // e = (type, progress, 环宽比例[0=实心], 涡旋系数)
        String applyFn =
        "#define PI 3.14159265\n" +
        "\n" +
        "vec2 applyDistort(vec2 screenPos, vec4 d, vec4 e){\n" +
        "    vec2 diff = screenPos - d.xy;\n" +
        "    float dist = length(diff);\n" +
        "    if(dist <= 0.001 || d.z <= 0.001) return vec2(0.0);\n" +
        "\n" +
        "    // 归一化半径：0 = 圆心，1 = 环心/外沿\n" +
        "    float r = dist / d.z;\n" +
        "    float falloff;\n" +
        "\n" +
        "    if(e.z > 0.01){\n" +
        // 环模式：|r - 1| < 环宽/2，smoothstep 边缘（比 t*t 更柔，无硬边）
        "        float rd = abs(r - 1.0);\n" +
        "        float hw = e.z * 0.5;\n" +
        "        if(rd >= hw) return vec2(0.0);\n" +
        "        float t = 1.0 - rd / hw;\n" +
        "        falloff = t * t * (3.0 - 2.0 * t);\n" +
        "    }else{\n" +
        // 实心模式：中心最强、外沿平滑归零
        "        if(r >= 1.0) return vec2(0.0);\n" +
        "        float t = 1.0 - r;\n" +
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
        // 基础位移倍率从 20.0 降到 10.0：强扭曲下像素跳跃感明显减轻，
        // 配合 smoothstep 边缘与半分辨率上采样，观感更细腻
        "    float amp = falloff * d.w * mag * dir * 10.0;\n" +
        "    vec2 radial = diff / dist * amp;\n" +
        // 切向涡旋：铁锈传送门/等离子涡旋的灵魂，纯径向只有放大镜感
        "    vec2 tangent = vec2(-diff.y, diff.x) / dist * amp * e.w;\n" +
        "    return radial + tangent;\n" +
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
                recycle(data);
                continue;
            }
            float progress = data.elapsed / data.lifetime;
            float curve = data.interp.apply(progress);
            data.radius = data.maxRadius * Mathf.lerp(data.radiusFrom, data.radiusTo, curve);

            // ============================================================
            //  ★ 淡出曲线改为 1 - p²：
            //  旧曲线在前 50% 保持满强度、之后 1-t²，在 p=0.5 处导数
            //  不连续，视觉上能看出「突然开始衰减」。
            //  新曲线全程平滑、前段维持强度、尾段快速收敛，
            //  接近铁锈核弹冲击波「到达即巅峰、迅速散去」的手感。
            //  环宽不参与 fade——避免环淡到阈值以下时
            //  从环状突然跳成实心圆。
            // ============================================================
            data.currentStrength = data.strength * (1f - progress * progress);
        }
    }

    public static void addDistortion(float x, float y, float radius, float strength,
        float lifetime, int type,
        float radiusFrom, float radiusTo,
        float ringWidth, Interp interp) {
        addDistortion(x, y, radius, strength, lifetime, type,
            radiusFrom, radiusTo, ringWidth, interp, 0f);
    }

    public static void addDistortion(float x, float y, float radius, float strength,
        float lifetime, int type,
        float radiusFrom, float radiusTo,
        float ringWidth, Interp interp, float swirl) {
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
        while (active.size >= max) {
            active.remove(0);
        }

        Data data = obtain();
        data.position.set(x, y);
        data.maxRadius = radius;
        // 初始半径 = 按 interp(0) 算出的起始值，跟后续 updateActive 公式一致
        float initialCurve = interp.apply(0f);
        data.radius = radius * Mathf.lerp(radiusFrom, radiusTo, initialCurve);
        data.strength = strength;
        data.lifetime = lifetime;
        data.elapsed = 0f;
        data.type = type;
        data.radiusFrom = radiusFrom;
        data.radiusTo = radiusTo;
        data.ringWidth = ringWidth;
        data.swirl = swirl;
        data.interp = interp;
        data.currentStrength = strength;
        // ← 初始值
        active.add(data);
    }

    private static Data obtain() {
        if (pool.size > 0) {
            Data d = pool.pop();
            // Vec2 是 final 字段，set() 复位即可；其余标量上面全量赋值
            return d;
        }
        return new Data();
    }

    private static void recycle(Data data) {
        if (pool.size < POOL_LIMIT) {
            pool.add(data);
        }
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

    /** 离屏渲染缩放：扭曲是低频信号，半分辨率 + bilinear 上采样肉眼无损 */
    public static float renderScale() {
        switch (SSSettings.getLevel()) {
            case SSSettings.LEVEL_LOW:    return 0.25f;
            case SSSettings.LEVEL_MEDIUM: return 0.5f;
            default:                      return 1f;
        }
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
        texScale = -1f;
        active.clear();
        pool.clear();
    }

    public static int activeCount() {
        return active.size;
    }

    public static int currentFps() {
        return lastFps;
    }
}
