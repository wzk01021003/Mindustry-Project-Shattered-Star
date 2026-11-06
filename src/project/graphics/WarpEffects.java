package project.graphics;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import mindustry.entities.Effect;
import mindustry.entities.Effect.EffectContainer;
import mindustry.graphics.Drawf;

/**
 * 星辉跃迁的「手写层」特效：能量轨迹、蓄力、起跳、落点。
 *
 * <p>原来用的是 {@code Fx.circleColorSpark} / {@code Fx.shootBigColor}，
 * 前者是给炮口用的通用火花，后者干脆是「开大炮」的爆炸，
 * 跟瞬移没有半点关系，所以看起来很像随手贴了个爆炸图。
 *
 * <p>这里的共同点是都围绕「环 + 向心/离心线」来画，不依赖贴图，任何分辨率下都清晰，
 * 负责那些不好用参数描述、需要逐帧控制的东西：
 *
 * <ul>
 *   <li>{@link WarpChargeEffect}——蓄力，一圈向心收缩的细环加吸进来的短线</li>
 *   <li>{@link WarpBurstEffect}——起跳，放射尖刺 + 白热核心（环交给 WarpShapes）</li>
 *   <li>{@link WarpArriveEffect}——落点，收束环 + 向心线 + 落地火花，节奏慢一点（32 帧），
 *       强调「合拢」</li>
 * </ul>
 *
 * <p>多边形能量环、放射光刃这类「几何层」在 {@link WarpShapes} 里，
 * 由 {@code MultiEffect} 和本文件的三个类合成。两边刻意不画同一个东西：
 * 手写层不画扩散环，几何层不画尖刺和白闪，否则同心环叠在一起只会互相糊掉。
 *
 * <p>颜色走 {@code e.color}（由 WarpAbility 传进来），没传时退回星辉的橙金。
 */
public class WarpEffects{

    /** 白热核心 */
    public static final Color CORE = Color.valueOf("fff4dc");
    /** 亮边 */
    public static final Color WARM = Color.valueOf("ffb87a");
    /** 主色，与机体辉光同色系 */
    public static final Color MAIN = Color.valueOf("ffa665");

    private static final Rand rand = new Rand();

    private static Color main(EffectContainer e){
        return e.color == null ? MAIN : e.color;
    }

    /**
     * 从 {@code at(x, y, rot, color, data)} 的 data 里取一个 float。
     * 跃迁长度、粒子数这类每发都不同的参数全走这里，Effect 本身才能做成静态单例。
     */
    public static float data(EffectContainer e, float def){
        Object o = e.data;
        return o instanceof Number ? ((Number)o).floatValue() : def;
    }

    // ============================================================
    //  能量轨迹：从起点拉到落点的一束光
    // ============================================================
    public static class WarpTrailEffect extends Effect{
        public float stroke = 18f;
        public Color c1 = Color.valueOf("ffa665").a(0.45f);
        public Color c2 = Color.valueOf("ffb87a").a(0.7f);
        public Color c3 = Color.white;
        /** 轨迹粗细倍率：日蚀这类大机体需要更宽的一束，否则像根线 */
        public float scale = 1f;

        public WarpTrailEffect(){
            this(1f);
        }

        public WarpTrailEffect(float scale){
            this.scale = scale;
            lifetime = 28f;
            clip = 2000f;
        }

        @Override
        public void render(EffectContainer e){
            float len = data(e, 0f);
            if(len <= 0.01f) return;

            float dx = Mathf.cosDeg(e.rotation) * len, dy = Mathf.sinDeg(e.rotation) * len;
            float x2 = e.x + dx, y2 = e.y + dy;
            float f = e.fout();
            float s = stroke * scale;

            Drawf.light(e.x, e.y, x2, y2, s * f, main(e), 1f);

            // 三层同线、逐层变细变亮，叠起来就是一束带核心高光的能量轨迹
            Lines.stroke(s * f);
            Draw.color(c1);
            Lines.line(e.x, e.y, x2, y2);
            Lines.stroke(s * f * 0.6f);
            Draw.color(c2);
            Lines.line(e.x, e.y, x2, y2);
            Lines.stroke(s * f * 0.3f);
            Draw.color(c3);
            Lines.line(e.x, e.y, x2, y2);
        }
    }

    // ============================================================
    //  蓄力：向心收缩
    // ============================================================
    public static class WarpChargeEffect extends Effect{
        public float radius = 20f;
        public float stroke = 2.2f;
        public int spikes = 9;
        /**
         * 整体尺寸倍率。
         *
         * <p>特效是静态单例，不能按单位改半径，所以把「多大」做成构造参数：
         * 星辉（hitSize 9）用 1，异构日蚀（hitSize 58）用 4，
         * 同一个类就能同时服务小飞机和 T5 轰炸机，不必复制一份代码。
         */
        public float scale = 1f;

        public WarpChargeEffect(){
            this(1f);
        }

        public WarpChargeEffect(float scale){
            this.scale = scale;
            lifetime = 22f;
            clip = 320f * scale;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            float R = radius * scale, S = stroke * scale;
            float shrink = 1f - Interp.pow3In.apply(p);
            float r = R * shrink;
            float fade = 1f - p * 0.75f;
            Color c = main(e);

            // 主环：越收越细
            Draw.color(CORE, c, p);
            Draw.alpha(fade);
            Lines.stroke(S * shrink);
            Lines.circle(e.x, e.y, r);

            // 被吸进来的短线，方向固定（用 id 做种子），看起来像能量流而不是噪点
            rand.setSeed(e.id);
            Lines.stroke(S * 0.65f * shrink);
            Draw.color(c);
            Draw.alpha(fade * 0.85f);
            for(int i = 0; i < spikes; i++){
                float ang = rand.random(360f);
                float len = rand.random(5f, 13f) * shrink * scale;
                float rr = r + len;
                Lines.lineAngle(e.x + Mathf.cosDeg(ang) * rr, e.y + Mathf.sinDeg(ang) * rr, ang + 180f, len);
            }

            // 中心的一点余烬
            Draw.color(CORE);
            Draw.alpha(0.5f * shrink * shrink);
            Fill.circle(e.x, e.y, R * 0.4f * shrink);
            Drawf.light(e.x, e.y, R * 1.8f * shrink, c, 0.35f * fade);
        }
    }

    // ============================================================
    //  起跳：撕开
    // ============================================================
    /**
     * 只管「从中心射出去的东西」：放射尖刺 + 白热核心。
     *
     * <p>扩散环交给 {@link WarpShapes#BURST} 里的二十四/四十八边形，
     * 这里不再画圆，否则两三层同心环叠在一起只会互相糊掉。
     */
    public static class WarpBurstEffect extends Effect{
        public float radius = 42f;
        public float stroke = 4f;
        public int spikes = 14;
        /** 整体尺寸倍率，见 {@link WarpChargeEffect#scale} */
        public float scale = 1f;

        public WarpBurstEffect(){
            this(1f);
        }

        public WarpBurstEffect(float scale){
            this.scale = scale;
            lifetime = 26f;
            clip = 420f * scale;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            float R = radius * scale, S = stroke * scale;
            float out = Interp.pow3Out.apply(p);
            float fade = 1f - p;
            Color c = main(e);

            // 放射尖刺：长短不一，外端先淡
            rand.setSeed(e.id);
            Draw.color(CORE, c, p);
            Draw.alpha(fade * 0.8f);
            Lines.stroke(S * 0.55f * fade);
            for(int i = 0; i < spikes; i++){
                float ang = 360f / spikes * i + rand.random(-9f, 9f);
                float len = R * rand.random(0.65f, 1.45f) * out * (1f - p * 0.45f);
                // 从接近中心的地方射出去，起点露在外面会变成车轮辐条
                float r0 = R * 0.04f * out;
                Lines.lineAngle(e.x + Mathf.cosDeg(ang) * r0, e.y + Mathf.sinDeg(ang) * r0, ang, len);
            }

            // 核心：两层白热，收得比环快
            float flash = 1f - Interp.pow2In.apply(p);
            Draw.color(Color.white);
            Draw.alpha(flash);
            Fill.circle(e.x, e.y, S * 1.7f * flash);
            Draw.alpha(flash * flash);
            Fill.circle(e.x, e.y, S * 0.85f * flash * flash);

            Drawf.light(e.x, e.y, R * 2f * fade + 10f, c, 0.9f * fade);
        }
    }

    // ============================================================
    //  落点：合拢
    // ============================================================
    public static class WarpArriveEffect extends Effect{
        public float radius = 38f;
        public float stroke = 3.4f;
        /** 整体尺寸倍率，见 {@link WarpChargeEffect#scale} */
        public float scale = 1f;

        public WarpArriveEffect(){
            this(1f);
        }

        public WarpArriveEffect(float scale){
            this.scale = scale;
            lifetime = 32f;
            clip = 460f * scale;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            float R = radius * scale, S = stroke * scale;
            Color c = main(e);

            // 1. 收束环：快速收到脚下，制造「被压缩」的错觉。
            //    原来这里还有一根自上而下的竖直光柱，俯视视角里就是一根杆子戳在落点中间，
            //    既不像瞬移也不像落地，已经删掉。落点的「散开再收回」交给 WarpAbility 的四份残影。
            float pin = Mathf.clamp(p / 0.4f);
            Draw.color(c);
            Draw.alpha(1f - pin);
            Lines.stroke(S * (1f - pin * 0.65f));
            Lines.circle(e.x, e.y, R * (1f - Interp.pow3In.apply(pin)) + 3f * scale);

            // 2. 落地火花：一圈向外的小点，比线条更碎
            rand.setSeed(e.id);
            Draw.color(CORE);
            float spark = Mathf.clamp((p - 0.2f) / 0.5f);
            if(spark > 0f && spark < 1f){
                Draw.alpha(0.9f * (1f - spark));
                for(int i = 0; i < 10; i++){
                    float ang = rand.random(360f);
                    float d = R * rand.random(0.5f, 1.3f) * Interp.pow2Out.apply(spark);
                    Fill.circle(e.x + Mathf.cosDeg(ang) * d, e.y + Mathf.sinDeg(ang) * d, (1.4f * (1f - spark) + 0.4f) * scale);
                }
            }

            // 3. 核心白闪，压到前一半
            float flash = 1f - Interp.pow2In.apply(Mathf.clamp(p / 0.45f));
            Draw.color(Color.white);
            Draw.alpha(flash);
            Fill.circle(e.x, e.y, S * 1.8f * flash);

            Drawf.light(e.x, e.y, R * 2.2f * (1f - p) + 10f, c, 0.85f * (1f - p));
        }
    }

    // ============================================================
    //  棱形碎片：VE 幻象跃迁时在机体周围飘的那一层
    // ============================================================
    /**
     * 会转、会缩、会淡的棱形光片。
     *
     * <p>照 VE 幻象（strengthened/meta-spawn.json）的 parts 抄的：那边在整个入场过程中
     * 按 0.5 / 0.8 的帧概率往外刷 ve-warp1 / ve-warp2 两张菱形贴图 ——
     * 随机朝向（effectRandRot 360）、尺寸 64→48、16 帧、slope 曲线、lightScl 8。
     * 单看一片就是块很暗的菱形，靠「同时飘着七八片」才堆出空间正在碎裂的观感，
     * 所以 {@link #count} 默认就给 8，少于此数看起来只是往屏幕上贴了块玻璃。
     *
     * <p>两种用法共用同一个类：
     * <ul>
     *   <li>{@code radius > 0}：从中心向外飞散，落点用（日蚀跃迁的那一圈）</li>
     *   <li>{@code radius = 0}：原地旋转缩小，蓄力用（贴在机体周围持续冒）</li>
     * </ul>
     *
     * <p>贴图是自己画的 {@code assets/sprites/effects/warp-shard.png}：
     * 128×64 的硬边菱形，中心亮、边缘略淡。没有直接用 VE 的 ve-warp1/2，
     * 那两张是对方的美术资源。
     *
     * <p>关于混合模式：棱形必须走 additive。普通混合下它是一块实心色块，
     * 压在任何背景上都像贴纸；additive 才会跟机身辉光、能量环融成一体。
     */
    public static class WarpSplinterEffect extends Effect{
        /** 一次生成多少片 */
        public int count = 8;
        /** 每片从中心飞出的距离；0 = 原地旋转缩小 */
        public float radius = 0f;
        /**
         * 起始位置占 {@link #radius} 的比例。
         *
         * <p>不能从 0 起步：十几片棱形同时出现在机体中心，additive 叠起来就是一团白，
         * 看起来像爆炸而不是碎片。让它们一出生就落在机体轮廓之外，
         * 机体只被棱形围住、不被盖住，才看得出是「空间碎片被推开」。
         */
        public float innerRatio = 0.7f;
        /** 起始 / 结束长度（像素，长轴） */
        public float lenFrom = 52f, lenTo = 14f;
        /** 短轴 / 长轴。贴图本身是 128×64，所以默认 0.5 */
        public float widthRatio = 0.5f;
        /** 每片在生命周期内额外自转的角度 */
        public float spin = 55f;
        /** 每片延后启动的上限（占生命周期比例），让爆发不是一次齐射 */
        public float maxDelay = 0.28f;
        /** 整体不透明度。additive 下 0.6 左右已经开始发白，再高就化成一块光斑 */
        public float alpha = 0.6f;

        public WarpSplinterEffect(){
            lifetime = 30f;
            // clip 决定这发特效在创建时会不会被视锥剔除（Effect.create 里用它做矩形裁剪），
            // 基类默认 0，不改的话整发都不渲染
            clip = 320f;
        }

        @Override
        public void render(EffectContainer e){
            TextureRegion reg = shard();
            if(reg == null) return;

            float p = e.fin();
            Color c = main(e);
            rand.setSeed(e.id);

            Draw.blend(Blending.additive);
            for(int i = 0; i < count; i++){
                // 方向和大小都用 id 做种子，同一发每次重绘位置一致，不会抖
                float ang = rand.random(360f);
                float spread = rand.random(0.78f, 1.18f);
                float delay = rand.random(maxDelay);
                float q = Mathf.clamp((p - delay) / Math.max(1f - delay, 0.01f));

                float out = Interp.pow2Out.apply(q);
                float len = (lenFrom + (lenTo - lenFrom) * Interp.pow2In.apply(q)) * spread;
                float wid = len * widthRatio;
                float rot = ang + rand.random(-28f, 28f) + spin * q;
                float dist = radius * spread * (innerRatio + (1f - innerRatio) * out);

                // 恒用主色，不再从白色插值：白棱形叠在机身上跟爆炸的闪光分不开，
                // 橙金色才能读成「同一台机器身上的能量」
                Draw.color(c);
                Draw.alpha(alpha * (1f - q * q));
                Draw.rect(reg, e.x + Mathf.cosDeg(ang) * dist,
                    e.y + Mathf.sinDeg(ang) * dist, len, wid, rot);
            }
            Draw.blend();

            // 整发只打一次光：每片都打的话，同屏十几片的时候光斑比环还贵
            Drawf.light(e.x, e.y, (radius + lenFrom) * 1.3f * (1f - p) + 30f, c, 0.45f * (1f - p));
        }
    }

    /** 棱形贴图缓存：preload 之后 atlas 才有内容，所以不能在静态初始化里取 */
    private static TextureRegion shardRegion;

    /** 棱形贴图。mod 的 sprite 在 atlas 里带 ss- 前缀，取不到再用原名兜底 */
    public static TextureRegion shard(){
        if(shardRegion == null){
            TextureRegion r = Core.atlas.find("ss-warp-shard");
            if(!r.found()) r = Core.atlas.find("warp-shard");
            shardRegion = r.found() ? r : Core.atlas.find("error");
        }
        // 缺图时返回 null，让调用方整发跳过：画一堆 error 白方块比不画更糟
        return shardRegion.found() ? shardRegion : null;
    }
}
