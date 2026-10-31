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
 *  - 视口全离屏时整条管线跳过：FBO 捕获 + 全屏 blit 都不执行
 *  - shader 快路径：offset ≈ 0 的像素（无扭曲区）即使开启色散
 *    也只取 1 次纹理采样，与旧代码输出完全一致
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
    /** 本帧参与渲染的源索引（按强度降序），复用避免每帧分配 */
    private static final int[] slotOrder = new int[MAX_GPU_SLOTS];
    /** Data 对象池，避免每次触发都 new；池有上限，防止无限增长 */
    private static final Seq<Data> pool = new Seq<>();
    private static final int POOL_LIMIT = 64;

    private static FrameBuffer buffer;
    /**
     * 按槽位数缓存的 shader 变体（量化到 1/2/4/8/16 五档）。
     *
     * 旧实现只有一个 shader，主循环固定编译 16 次迭代：
     * {@code for(int i = 0; i < 16; i++) if(u_count > i) offset += applyDistort(...)}
     * u_count 是 uniform，GLSL ES 2.0 无法在编译期裁剪这个分支，于是哪怕
     * 画面上只有 1 个扭曲源，每个像素也要跑满 16 次循环 + 16 次判断——
     * 这是「几十个一起上就卡」的主因。按实际槽位数编译变体后，循环次数
     * 成为编译期常量，2 个源时 ALU 开销直接降到 1/8。
     * 只量化五档而非每档都编译，避免 shader program 数量失控。
     */
    private static final Shader[] shaderVariants = new Shader[MAX_GPU_SLOTS + 1];
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

        // 全部扭曲都在视口外时，整条离屏管线（FBO 捕获 + 全屏 blit）
        // 都不必执行——玩家看不见扭曲时，这两步是纯浪费。
        if (!anyVisible()) return;

        Draw.draw(Layer.background - 1f, DistortionRenderer::beginCapture);
        Draw.draw(Layer.overlayUI - 1f, DistortionRenderer::endCaptureAndRender);
    }

    /**
     * 是否存在与视口相交的扭曲源。
     * 换算方式与 endCaptureAndRender 的 uniform 上传保持一致；
     * 半径额外加上最大位移（strength * 10）与 64px 余量，
     * 确保扭曲边缘的采样偏移区域也被视为可见。
     */
    private static boolean anyVisible() {
        if (Core.camera == null) return true;
        float w = Core.graphics.getWidth();
        float h = Core.graphics.getHeight();
        if (w <= 0 || h <= 0) return true;

        float camX = Core.camera.position.x;
        float camY = Core.camera.position.y;
        float camW = Core.camera.width;
        float camH = Core.camera.height;

        for (int i = 0; i < active.size; i++) {
            Data d = active.get(i);
            float sx = (d.position.x - camX) / camW * w + w / 2f;
            float sy = (d.position.y - camY) / camH * h + h / 2f;
            float r = Math.abs(d.radius) / camW * w
                    + Math.abs(d.currentStrength) * 10f + 64f;
            if (sx + r >= 0f && sx - r <= w && sy + r >= 0f && sy - r <= h) {
                return true;
            }
        }
        return false;
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

            float w = Core.graphics.getWidth();
            float h = Core.graphics.getHeight();
            float camX = Core.camera.position.x;
            float camY = Core.camera.position.y;
            float camW = Core.camera.width;
            float camH = Core.camera.height;

            // 只收集视口内、强度未衰减完的源，并按强度降序取前 N 个。
            // 旧实现直接取 active 的前 N 个（数组顺序 = 创建顺序），
            // 齐射时刚爆炸的强源可能被一批较弱的旧扭曲挤掉，
            // 白白占掉槽位却几乎看不见。
            int found = collectSlots(w, h, camX, camY, camW, camH);
            // 注意：这里不能提前 return。离屏内容还没画回屏幕，
            // 直接返回会让 Layer.background ~ overlayUI 之间的画面整段丢失（黑屏）。
            // 没有可用源时用 1 槽空变体跑一遍，等价于原样输出。
            // 用 found 而非 slots 作为有效数量，避免读到 slotOrder 里的陈旧索引。
            int slots = found > 0 ? found : 1;

            // 变体声明的 uniform 数可能多于实际源数（量化到 1/2/4/8/16）。
            // 同一 program 复用时会残留上一帧的值，必须把多余槽位显式清零，
            // 否则会多渲染一个早已消失的扭曲。
            int q = quantizeSlots(slots);

            for (int i = 0; i < q; i++) {
                if (i < found) {
                    Data data = active.get(slotOrder[i]);
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
                } else {
                    // d.z = 0 → applyDistort 内部立即早退，代价可忽略
                    dBuf[i * 4] = 0f; dBuf[i * 4 + 1] = 0f; dBuf[i * 4 + 2] = 0f; dBuf[i * 4 + 3] = 0f;
                    eBuf[i * 4] = 0f; eBuf[i * 4 + 1] = 0f; eBuf[i * 4 + 2] = 0f; eBuf[i * 4 + 3] = 0f;
                }
            }

            Shader sh = shaderFor(slots);
            sh.bind();
            sh.setUniformf("u_resolution", w, h);
            sh.setUniformf("u_chroma", SSSettings.chromatic() ? 1f : 0f);
            for (int i = 0; i < q; i++) {
                sh.setUniformf(D_NAMES[i], dBuf[i * 4], dBuf[i * 4 + 1], dBuf[i * 4 + 2], dBuf[i * 4 + 3]);
                sh.setUniformf(E_NAMES[i], eBuf[i * 4], eBuf[i * 4 + 1], eBuf[i * 4 + 2], eBuf[i * 4 + 3]);
            }

            buffer.blit(sh);

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
            // shader 变体改为按需编译：首次用到某个槽位数时才创建，
            // 避免启动时一次性编译多个 program 造成卡顿
        } catch (Throwable t) {
            Log.err("[ss-distort] 初始化失败", t);
            unsupported = true;
        }
    }

    /** 把实际槽位数向上量化到 1/2/4/8/16，限制 shader 变体数量 */
    private static int quantizeSlots(int slots) {
        if (slots <= 1) return 1;
        if (slots <= 2) return 2;
        if (slots <= 4) return 4;
        if (slots <= 8) return 8;
        return MAX_GPU_SLOTS;
    }

    /** 取得（必要时懒编译）对应槽位数的 shader 变体 */
    private static Shader shaderFor(int slots) {
        int q = quantizeSlots(slots);
        Shader s = shaderVariants[q];
        if (s == null) {
            s = createShader(q);
            shaderVariants[q] = s;
        }
        return s;
    }

    /**
     * 为指定槽位数编译 shader。
     * slots 是编译期常量，因此主循环可以被编译器完全展开/裁剪，
     * 不再有「固定 16 次迭代 + uniform 分支」的浪费。
     */
    private static Shader createShader(int slots) {
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
        // 色散总开关（0 = 关闭，走单次采样快路径）
        uniformDecl.append("uniform float u_chroma;\n");
        for (int i = 0; i < slots; i++) {
            uniformDecl.append("uniform vec4 u_d").append(i).append(";\n");
        }
        for (int i = 0; i < slots; i++) {
            uniformDecl.append("uniform vec4 u_e").append(i).append(";\n");
        }

        StringBuilder mainBody = new StringBuilder();
        mainBody.append("void main(){\n");
        mainBody.append("    vec2 uv = v_texCoords;\n");
        mainBody.append("    vec2 screenPos = uv * u_resolution;\n");
        mainBody.append("    vec2 offset = vec2(0.0);\n");
        // 循环次数由变体固定：slots = 1/2/4/8/16
        for (int i = 0; i < slots; i++) {
            mainBody.append("    offset += applyDistort(screenPos, u_d").append(i)
            .append(", u_e").append(i).append(");\n");
        }
        mainBody.append("    vec2 duv = offset / u_resolution;\n");
        // 色散：沿位移方向做 RGB 分离，位移越大分离越明显。
        // 三次采样同时起到强位移下的抗锯齿作用。
        // 快路径：offset ≈ 0（画面中无扭曲的绝大多数像素）时即使开启
        // 色散也只取 1 次采样——此时旧代码中 normalize(offset) 本就
        // 被 0.0001 阈值兜底为 vec2(0.0)，三次采样结果完全相同，
        // 属于零视觉差异的纯填充率优化。
        mainBody.append("    if(u_chroma > 0.001 && length(offset) > 0.0001){\n");
        mainBody.append("        vec2 nd = normalize(offset);\n");
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

        // ============================================================
        //  同帧合并
        //  齐射时同一片区域会在几帧内产生大量几乎完全重叠的扭曲，
        //  它们各占一个槽位，但在屏幕上根本分辨不出来——纯属浪费。
        //  合并成一个（半径取大、强度叠加、按当前进度重算）能让
        //  「几十发一起」只吃 1~2 个槽位，是降低齐射开销最关键的一步。
        //  只合并同帧/上一帧的源，避免把已经扩散开的老环拽回起点。
        // ============================================================
        float mergeR = radius * 0.35f;
        float mergeR2 = mergeR * mergeR;
        for (int i = 0; i < active.size; i++) {
            Data d = active.get(i);
            if (d.type != type) continue;
            if (d.elapsed > Time.delta * 2f) continue;
            float dx = d.position.x - x;
            float dy = d.position.y - y;
            if (dx * dx + dy * dy > mergeR2) continue;

            float p = d.lifetime > 0f ? Math.min(1f, d.elapsed / d.lifetime) : 1f;
            if (radius > d.maxRadius) {
                d.maxRadius = radius;
                d.radiusFrom = radiusFrom;
                d.radiusTo = radiusTo;
                d.interp = interp;
                // 按当前进度重算半径，避免合并瞬间半径突跳
                d.radius = radius * Mathf.lerp(radiusFrom, radiusTo, interp.apply(p));
            }
            // 强度叠加但有上限，避免多枚叠加后把画面撕烂
            d.strength = Math.min(SSSettings.maxStrength(), d.strength + strength * 0.5f);
            d.currentStrength = d.strength * (1f - p * p);
            d.lifetime = Math.max(d.lifetime, lifetime);
            return;
        }

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

    /** 本帧允许使用的槽位上限（自动降级会收紧） */
    private static int slotBudget() {
        if (downgrade >= 3) return 2;
        if (downgrade >= 2) return 4;
        if (downgrade >= 1) return 8;
        return MAX_GPU_SLOTS;
    }

    /**
     * 按「视口内 + 强度降序」挑选参与本帧渲染的源，返回实际数量。
     *
     * 旧实现直接取 active 的前 N 个（数组顺序即创建顺序），
     * 齐射时刚爆炸的强源可能被一批已快衰减完的旧扭曲挤掉。
     * 这里用一个 16 元插入排序（active 通常几十个，O(n·16) 可忽略），
     * 顺带丢掉衰减到肉眼不可见的源，进一步压低 shader 循环次数。
     */
    private static int collectSlots(float w, float h, float camX, float camY, float camW, float camH) {
        int cap = slotBudget();
        int n = 0;
        for (int i = 0; i < active.size; i++) {
            Data d = active.get(i);
            // 强度已衰减到不可见，或半径塌缩，直接不占槽位
            if (d.currentStrength <= 0.015f || d.radius <= 0.5f) continue;

            float sx = (d.position.x - camX) / camW * w + w / 2f;
            float sy = (d.position.y - camY) / camH * h + h / 2f;
            float r = d.radius / camW * w + Math.abs(d.currentStrength) * 10f + 64f;
            if (sx + r < 0f || sx - r > w || sy + r < 0f || sy - r > h) continue;

            float s = d.currentStrength;
            int pos = n;
            while (pos > 0 && active.get(slotOrder[pos - 1]).currentStrength < s) {
                if (pos < cap) slotOrder[pos] = slotOrder[pos - 1];
                pos--;
            }
            if (pos < cap) {
                slotOrder[pos] = i;
                if (n < cap) n++;
            }
        }
        return n;
    }

    private static int effectiveSlots() {
        int n = active.size;
        if (downgrade >= 1) n = Math.min(n, 8);
        if (downgrade >= 2) n = Math.min(n, 4);
        if (downgrade >= 3) n = Math.min(n, 2);
        return Math.min(n, MAX_GPU_SLOTS);
    }

    /**
     * 离屏渲染缩放：扭曲是低频信号，半分辨率 + bilinear 上采样肉眼无损。
     *
     * 自动降级到第 3 档时，除槽位收到 2 个之外，分辨率也强制降到 1/4——
     * 填充率是 4 倍差距，比减少槽位更直接。宁可扭曲糊一点，也不能掉帧。
     */
    public static float renderScale() {
        float base;
        switch (SSSettings.getLevel()) {
            case SSSettings.LEVEL_LOW:    base = 0.25f; break;
            case SSSettings.LEVEL_MEDIUM: base = 0.5f;  break;
            default:                      base = 1f;    break;
        }
        if (downgrade >= 3 && base > 0.25f) return 0.25f;
        // 高画质（全分辨率）一旦开始掉帧就先砍到半分辨率：
        // 填充率直接腰斩，且半分辨率对扭曲这种低频信号几乎看不出差别
        if (downgrade >= 1 && base > 0.5f) return 0.5f;
        return base;
    }

    public static void dispose() {
        if (buffer != null) {
            buffer.dispose();
            buffer = null;
        }
        for (int i = 0; i < shaderVariants.length; i++) {
            if (shaderVariants[i] != null) {
                shaderVariants[i].dispose();
                shaderVariants[i] = null;
            }
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
