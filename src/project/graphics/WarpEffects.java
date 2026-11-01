package project.graphics;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
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
 *   <li>{@link WarpArriveEffect}——落点，自上而下的光柱 + 收束环 + 落地火花</li>
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

        public WarpTrailEffect(){
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

            Drawf.light(e.x, e.y, x2, y2, stroke * f, main(e), 1f);

            // 三层同线、逐层变细变亮，叠起来就是一束带核心高光的能量轨迹
            Lines.stroke(stroke * f);
            Draw.color(c1);
            Lines.line(e.x, e.y, x2, y2);
            Lines.stroke(stroke * f * 0.6f);
            Draw.color(c2);
            Lines.line(e.x, e.y, x2, y2);
            Lines.stroke(stroke * f * 0.3f);
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

        public WarpChargeEffect(){
            lifetime = 22f;
            clip = 320f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            float shrink = 1f - Interp.pow3In.apply(p);
            float r = radius * shrink;
            float fade = 1f - p * 0.75f;
            Color c = main(e);

            // 主环：越收越细
            Draw.color(CORE, c, p);
            Draw.alpha(fade);
            Lines.stroke(stroke * shrink);
            Lines.circle(e.x, e.y, r);

            // 被吸进来的短线，方向固定（用 id 做种子），看起来像能量流而不是噪点
            rand.setSeed(e.id);
            Lines.stroke(stroke * 0.65f * shrink);
            Draw.color(c);
            Draw.alpha(fade * 0.85f);
            for(int i = 0; i < spikes; i++){
                float ang = rand.random(360f);
                float len = rand.random(5f, 13f) * shrink;
                float rr = r + len;
                Lines.lineAngle(e.x + Mathf.cosDeg(ang) * rr, e.y + Mathf.sinDeg(ang) * rr, ang + 180f, len);
            }

            // 中心的一点余烬
            Draw.color(CORE);
            Draw.alpha(0.5f * shrink * shrink);
            Fill.circle(e.x, e.y, radius * 0.4f * shrink);
            Drawf.light(e.x, e.y, radius * 1.8f * shrink, c, 0.35f * fade);
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

        public WarpBurstEffect(){
            lifetime = 26f;
            clip = 420f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            float out = Interp.pow3Out.apply(p);
            float fade = 1f - p;
            Color c = main(e);

            // 放射尖刺：长短不一，外端先淡
            rand.setSeed(e.id);
            Draw.color(CORE, c, p);
            Draw.alpha(fade * 0.8f);
            Lines.stroke(stroke * 0.55f * fade);
            for(int i = 0; i < spikes; i++){
                float ang = 360f / spikes * i + rand.random(-9f, 9f);
                float len = radius * rand.random(0.65f, 1.45f) * out * (1f - p * 0.45f);
                // 从接近中心的地方射出去，起点露在外面会变成车轮辐条
                float r0 = radius * 0.04f * out;
                Lines.lineAngle(e.x + Mathf.cosDeg(ang) * r0, e.y + Mathf.sinDeg(ang) * r0, ang, len);
            }

            // 核心：两层白热，收得比环快
            float flash = 1f - Interp.pow2In.apply(p);
            Draw.color(Color.white);
            Draw.alpha(flash);
            Fill.circle(e.x, e.y, stroke * 1.7f * flash);
            Draw.alpha(flash * flash);
            Fill.circle(e.x, e.y, stroke * 0.85f * flash * flash);

            Drawf.light(e.x, e.y, radius * 2f * fade + 10f, c, 0.9f * fade);
        }
    }

    // ============================================================
    //  落点：合拢
    // ============================================================
    public static class WarpArriveEffect extends Effect{
        public float radius = 38f;
        public float stroke = 3.4f;
        /** 光柱高度 */
        public float beamHeight = 76f;

        public WarpArriveEffect(){
            lifetime = 32f;
            clip = 460f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            Color c = main(e);

            // 1. 自上而下的光柱：前 35% 落下，之后一边淡出、一边从下往上缩回去。
            //    只淡出的话，收尾那几帧会剩一根又细又长的杆子戳在天上，很突兀。
            float drop = Mathf.clamp(p / 0.35f);
            float beamFade = 1f - Mathf.clamp((p - 0.35f) / 0.65f);
            if(beamFade > 0.01f){
                float w = stroke * 2.2f * beamFade;
                float len = beamHeight * drop * (0.3f + 0.7f * beamFade);
                // 外层淡光晕
                Draw.color(c);
                Draw.alpha(0.28f * beamFade);
                Fill.rect(e.x - w * 1.8f, e.y, w * 3.6f, len);
                // 内层白芯
                Draw.color(CORE, c, 0.35f);
                Draw.alpha(0.85f * beamFade);
                Fill.rect(e.x - w * 0.5f, e.y, w, len);
            }

            // 2. 收束环：快速收到脚下，制造「被压缩」的错觉
            float pin = Mathf.clamp(p / 0.4f);
            Draw.color(c);
            Draw.alpha(1f - pin);
            Lines.stroke(stroke * (1f - pin * 0.65f));
            Lines.circle(e.x, e.y, radius * (1f - Interp.pow3In.apply(pin)) + 3f);

            // 3. 落地火花：一圈向外的小点，比线条更碎
            rand.setSeed(e.id);
            Draw.color(CORE);
            float spark = Mathf.clamp((p - 0.2f) / 0.5f);
            if(spark > 0f && spark < 1f){
                Draw.alpha(0.9f * (1f - spark));
                for(int i = 0; i < 10; i++){
                    float ang = rand.random(360f);
                    float d = radius * rand.random(0.5f, 1.3f) * Interp.pow2Out.apply(spark);
                    Fill.circle(e.x + Mathf.cosDeg(ang) * d, e.y + Mathf.sinDeg(ang) * d, 1.4f * (1f - spark) + 0.4f);
                }
            }

            // 4. 核心白闪，压到前一半
            float flash = 1f - Interp.pow2In.apply(Mathf.clamp(p / 0.45f));
            Draw.color(Color.white);
            Draw.alpha(flash);
            Fill.circle(e.x, e.y, stroke * 1.8f * flash);

            Drawf.light(e.x, e.y, radius * 2.2f * (1f - p) + 10f, c, 0.85f * (1f - p));
        }
    }
}
