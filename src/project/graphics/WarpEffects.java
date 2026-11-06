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
    //  落点棱形：VE 幻象 meta-spawn 的「先撑开，机体再从里面出来」
    // ============================================================
    /**
     * 落点合拢：4 份棱形符印从外圈收回落点中心。
     *
     * <p>VE 幻象（strengthened/meta-spawn.json）的入场是这么走的：
     * 隐形载体死亡 → 子弹 {@code despawnUnit} 把幻象放在落点 → 同一发 {@code shootEffect} 里，
     * 4 个 {@code RadialEffect} 拿整张机体贴图（{@code ve-meta-full}）用 {@code circleOut}
     * 向外走 48px、21 帧，另外 4 个晚 20 帧用 {@code circleIn} 反向收回、20 帧。
     *
     * <p>关键在时序：那 8 份棱形全部压在「机体出现的那一瞬间」附近，
     * 撑开段与机体同帧起步，收拢段在机体站稳之后才结束。读起来是
     * 「空间先被撑开一道缝 → 机体从缝里挤出来 → 缝合上」，
     * 是**抵达**本身的演出，不是抵达之后在机体周围摆一圈装饰。
     *
     * <p>所以这里只保留「合拢」这一半。撑开那半由
     * {@code WarpAbility.draw} 在蓄力期直接画（见 {@code warpMarkRadius}）——
     * 落点必须在跃迁之前就被标出来，等机体落地再撑开就晚了。
     */
    public static class WarpMarkCollapseEffect extends Effect{
        /** 几份符印 */
        public int count = 4;
        /** 合拢起点半径 */
        public float radius = 220f;
        /** 符印尺寸（正方形） */
        public float size = 180f;
        /** 合拢时长（帧）。VE 是 20 */
        public float contractTime = 20f;
        /** 起始不透明度 */
        public float alpha = 0.4f;
        /** 合拢期间额外自转的角度 */
        public float spin = 10f;

        public WarpMarkCollapseEffect(){
            this(1f);
        }

        public WarpMarkCollapseEffect(float scale){
            size *= scale;
            radius *= scale;
            lifetime = contractTime;
            // clip 决定这发特效在创建时会不会被视锥剔除（Effect.create 里用它做矩形裁剪）
            clip = radius + size + 80f;
        }

        @Override
        public void render(EffectContainer e){
            TextureRegion reg = mark();
            if(reg == null) return;

            float p = e.fin();
            // circleIn：起步几乎不动、越往后收得越快，和 VE 那条曲线一致。
            // 换成线性会变成匀速吸进去，像被绳子拽，不像空间自己在合
            float k = Interp.circleIn.apply(p);
            // 收到机体内侧时如果还亮着，就成了「机体上盖了四个框」，所以淡出比收缩快，
            // 尺寸也一起收 35%：单靠位置收，符印边缘会先在机体上拖出一道亮边
            float a = alpha * (1f - p) * (1f - p);
            float sz = size * (1f - 0.35f * p);

            Draw.blend(Blending.additive);
            Draw.color(main(e));

            for(int i = 0; i < count; i++){
                // 从 45° 起，四份符印落在斜向，不会正压在机头和机尾上
                float ang = 45f + 360f / count * i;
                float r = radius * (1f - k);
                if(r < 1f) continue;
                Draw.alpha(a);
                Draw.rect(reg, e.x + Mathf.cosDeg(ang) * r, e.y + Mathf.sinDeg(ang) * r,
                    sz, sz, ang + spin * p);
            }

            Draw.blend();
            Drawf.light(e.x, e.y, radius + size * 0.5f, main(e), 0.45f * (1f - p));
        }
    }

    // ============================================================
    //  落点周围的小棱形碎片（蓄力期持续生成）
    // ============================================================
    /**
     * 单片小棱形，在落点外圈闪一下就缩掉。
     *
     * <p>对应 VE 幻象载体上挂的两个 {@code EffectSpawnerPart}：{@code ve-warp1} / {@code ve-warp2}
     * 每帧按 0.5 / 0.8 的概率生成在机体半径 60~90px 处，{@code effectRandRot} 随机朝向，
     * 尺寸 48→32，寿命 16 帧。那台载体是隐形的，所以玩家看到的是
     * 「落点上凭空有一圈菱形碎片在翻」—— 这才是幻象出现之前的定位感来源。
     *
     * <p>只负责单片。要铺满一圈靠调用方按帧概率反复 {@code at()}，不是一次生成十几片：
     * 一次性生成会在某一帧突然全亮，按帧概率生成才是持续翻涌。
     */
    public static class WarpMoteEffect extends Effect{
        /** 出现位置到落点的距离 */
        public float ringRadius = 108f;
        /** 距离抖动，让碎片不是一个正圆环 */
        public float ringJitter = 40f;
        /** 长轴：起始 / 结束 */
        public float lenFrom = 52f, lenTo = 32f;
        /** 短轴 / 长轴。贴图本身是 128×64，所以默认 0.5 */
        public float widthRatio = 0.5f;
        /** 起始不透明度 */
        public float alpha = 0.62f;
        /** 生命周期内自转的角度 */
        public float spin = 110f;
        /** 生命周期内朝落点收进来多少（0 = 原地不动，1 = 收到中心） */
        public float inward = 0.55f;

        public WarpMoteEffect(){
            lifetime = 16f;
            clip = ringRadius + ringJitter + lenFrom + 40f;
        }

        @Override
        public void render(EffectContainer e){
            TextureRegion reg = shard();
            if(reg == null) return;

            // 方向和距离都用 id 做种子：同一片碎片重绘时位置一致，不会每帧乱跳
            rand.setSeed(e.id);
            float ang = rand.random(360f);
            float dist = ringRadius + rand.range(ringJitter);
            float p = e.fin();

            float d = dist * (1f - inward * Interp.pow2Out.apply(p));
            float len = lenFrom + (lenTo - lenFrom) * p;
            float rot = ang + spin * p;

            Draw.blend(Blending.additive);
            Draw.color(CORE, main(e), p);
            Draw.alpha(alpha * (1f - p * p));
            Draw.rect(reg, e.x + Mathf.cosDeg(ang) * d, e.y + Mathf.sinDeg(ang) * d,
                len, len * widthRatio, rot);
            Draw.blend();
        }
    }

    /** 落点贴图缓存：preload 之后 atlas 才有内容，所以不能在静态初始化里取 */
    private static TextureRegion markRegion, shardRegion;

    /** 落点符印贴图。mod 的 sprite 在 atlas 里带 ss- 前缀，取不到再用原名兜底 */
    public static TextureRegion mark(){
        if(markRegion == null){
            TextureRegion r = Core.atlas.find("ss-warp-mark");
            if(!r.found()) r = Core.atlas.find("warp-mark");
            markRegion = r.found() ? r : Core.atlas.find("error");
        }
        // 缺图时返回 null，让调用方整发跳过：画一堆 error 白方块比不画更糟
        return markRegion.found() ? markRegion : null;
    }

    /** 小棱形碎片贴图，缺图同样返回 null */
    public static TextureRegion shard(){
        if(shardRegion == null){
            TextureRegion r = Core.atlas.find("ss-warp-shard");
            if(!r.found()) r = Core.atlas.find("warp-shard");
            shardRegion = r.found() ? r : Core.atlas.find("error");
        }
        return shardRegion.found() ? shardRegion : null;
    }
}
