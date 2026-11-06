package project.graphics;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import mindustry.entities.Effect;
import mindustry.entities.Effect.EffectContainer;

/**
 * 开火与打击特效：把原版那几只通用 {@code Fx} 换成本 mod 自己的。
 *
 * <p>原版的 {@code Fx.shootBig} / {@code Fx.shockwave} / {@code Fx.hitBulletSmall}
 * 是给「所有单位共用」设计的，只能画到「不违和」为止——颜色一律白灰，
 * 形状一律是点或环。放在一台机身有明确配色（橙金）的单位上就会显脏：
 * 炮口喷出一小撮冷白点，跟机身条纹不是一套语言。
 *
 * <p>这里换两个思路：
 *
 * <ul>
 *   <li><b>形状带方向</b>：枪口焰是沿炮管方向的锥，命中火花是沿弹道法线散开的线。
 *       原版那些是各向同性的点，不管朝哪打看起来都一样，这是「简陋」的主要来源。</li>
 *   <li><b>颜色走三段</b>：白热 {@link #CORE} → 亮橙 {@link #WARM} → 暗红 {@link #DEEP}，
 *       和机体的 {@code FFA665} 同一条色路，不额外引入第三种冷色。</li>
 * </ul>
 *
 * <p>全部是矢量绘制，不依赖贴图：小口径武器的特效只有十几像素，
 * 贴图在这个尺寸下会糊成一团，线条反而更清楚，也不挑分辨率。
 *
 * <p>尺寸全部走 {@code scale} 参数，{@code hitSize 8} 的机甲用 1，
 * 大型单位按机身比例往上乘即可。
 */
public class FireFx{

    /** 白热核心 */
    public static final Color CORE = Color.valueOf("fff4dc");
    /** 亮橙，和机体辉光同色 */
    public static final Color WARM = Color.valueOf("ffb87a");
    /** 主色 */
    public static final Color MAIN = Color.valueOf("ffa665");
    /** 暗红余烬 */
    public static final Color DEEP = Color.valueOf("d9542a");

    private static final Rand rand = new Rand();

    private static Color main(EffectContainer e){
        return e.color == null ? MAIN : e.color;
    }

    // ============================================================
    //  枪口焰
    // ============================================================
    /**
     * 炮口锥形焰。
     *
     * <p>形态是「根部宽、沿炮管迅速收尖」的三角形，两层叠：外层橙、内层白热。
     * 原版 {@code Fx.shootBig} 是 9 个各向同性的白点，打哪儿都像往空气里吐了口烟；
     * 锥形能读出「这一炮是朝这个方向打出去的」，这是它和原版最大的区别。
     *
     * <p>寿命只有 6 帧：速射武器的枪口焰如果活得比装填久，两发之间会连成一片，
     * 看起来像炮口一直在喷火，反而失去了每一发的节奏。
     */
    public static class MuzzleEffect extends Effect{
        /** 焰长（像素，scale = 1 时） */
        public float len = 22f;
        /** 根部半宽 */
        public float halfWidth = 5.5f;
        /** 侧向火星的张开角度 */
        public float sparkAngle = 38f;
        /** 侧向火星长度 */
        public float sparkLen = 12f;
        /** 侧向火星数量（单侧） */
        public int sparks = 2;

        public MuzzleEffect(){
            this(1f);
        }

        public MuzzleEffect(float scale){
            len *= scale;
            halfWidth *= scale;
            sparkLen *= scale;
            lifetime = 6f;
            clip = (len + sparkLen) * 2f + 40f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            float fade = 1f - p;
            // pow2Out：出膛瞬间就接近满长，之后几乎不再长——枪口焰是被「顶」出来的，不是慢慢伸出来的
            float out = Interp.pow2Out.apply(p);
            float rot = e.rotation;
            float cx = Mathf.cosDeg(rot), cy = Mathf.sinDeg(rot);
            // 垂直于炮管的方向，用来撑开锥形的根
            float nx = Mathf.cosDeg(rot + 90f), ny = Mathf.sinDeg(rot + 90f);

            Draw.blend(Blending.additive);

            // 外层：橙→暗红，长度随 out 拉满，根部随时间收窄
            float w = halfWidth * fade;
            float tip = len * (0.35f + 0.65f * out);
            Draw.color(main(e), DEEP, p);
            Draw.alpha(fade * 0.9f);
            tri(e.x + cx * 2f, e.y + cy * 2f, nx, ny, w, cx, cy, tip);

            // 内层：白热，长度和宽度都打六折，叠在外层中间
            Draw.color(CORE);
            Draw.alpha(fade);
            tri(e.x + cx * 2f, e.y + cy * 2f, nx, ny, w * 0.55f, cx, cy, tip * 0.58f);

            // 侧向火星：从根部往两边各甩两条短线，是「火药被挤出来」的那部分
            if(sparks > 0 && p < 0.6f){
                rand.setSeed(e.id);
                Lines.stroke(1.3f * fade);
                Draw.color(WARM, DEEP, p);
                Draw.alpha(fade * 0.8f);
                for(int i = 0; i < sparks; i++){
                    float side = i % 2 == 0 ? 1f : -1f;
                    float a = rot + side * (sparkAngle + rand.range(16f));
                    float l = sparkLen * (0.4f + 0.6f * out) * rand.random(0.7f, 1.15f);
                    Lines.line(e.x + cx * 2f, e.y + cy * 2f,
                        e.x + cx * 2f + Mathf.cosDeg(a) * l,
                        e.y + cy * 2f + Mathf.sinDeg(a) * l);
                }
            }

            Draw.blend();
        }

        /** 以 (bx,by) 为根、沿 (cx,cy) 指向、根部半宽 hw、长 len 的三角形 */
        private void tri(float bx, float by, float nx, float ny, float hw, float cx, float cy, float len){
            Fill.tri(bx + nx * hw, by + ny * hw, bx - nx * hw, by - ny * hw,
                bx + cx * len, by + cy * len);
        }
    }

    // ============================================================
    //  打击效果
    // ============================================================
    /**
     * 命中火花。
     *
     * <p>原版 {@code Fx.hitBulletSmall} 是 5 帧的一小撮白点，打在什么东西上都是同一撮。
     * 这里改成「沿弹道法线散开的短线 + 一个白闪」：线有方向，才能读出子弹是从哪边打过来的；
     * 白闪只有 2 帧，负责那一瞬间的亮度。
     *
     * <p>线的散布刻意偏向后半球（{@code rot ± 78°}），也就是子弹来的那一侧——
     * 破片和火花本来就是被打中的东西往回溅的，往前飞反而像武器自己在喷东西。
     */
    public static class HitSparkEffect extends Effect{
        /** 最长的一条线 */
        public float len = 14f;
        /** 线条数 */
        public int lines = 6;
        /** 白闪半径。别超过线条的起手长度，否则第一帧会被这颗圆点整个盖住，只剩一片白 */
        public float flash = 1.8f;

        public HitSparkEffect(){
            this(1f);
        }

        public HitSparkEffect(float scale){
            len *= scale;
            flash *= scale;
            lifetime = 9f;
            clip = len * 2f + 40f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            float fade = 1f - p;
            // circleOut 起步就快到 60%，pow2Out 前段太慢：9 帧的特效有 3 帧只剩一颗白点，
            // 看起来像「先亮一下、再长出线」，中间是断的
            float out = Interp.circleOut.apply(p);
            float rot = e.rotation;

            Draw.blend(Blending.additive);

            rand.setSeed(e.id);
            Draw.color(CORE, main(e), p);
            Draw.alpha(fade * 0.9f);
            for(int i = 0; i < lines; i++){
                // 以 rot + 180 为中心散开：火花是「被打中的东西往回溅」，
                // 所以全落在子弹来的那一侧。之前写成 rot ± 39°，等于让火花跟着子弹一起往前飞，
                // 看起来像武器自己在喷东西，方向是反的
                float a = rot + 180f + rand.random(-39f, 39f);
                float l = len * out * rand.random(0.4f, 1f);
                Lines.stroke(1.35f * fade * rand.random(0.7f, 1.1f));
                Lines.line(e.x + Mathf.cosDeg(a) * l * 0.25f, e.y + Mathf.sinDeg(a) * l * 0.25f,
                    e.x + Mathf.cosDeg(a) * l, e.y + Mathf.sinDeg(a) * l);
            }

            // 白闪压在前 1/3，之后完全消失，避免和后面的线抢注意力
            float fl = Mathf.clamp(1f - p / 0.33f);
            if(fl > 0f){
                Draw.color(CORE);
                Draw.alpha(fl);
                Fill.circle(e.x, e.y, flash * fl);
            }

            Draw.blend();
        }
    }

    // ============================================================
    //  静态单例
    // ============================================================
    /** 小口径（hitSize ≈ 8）的枪口焰，机甲 / T1 单位直接用 */
    public static final Effect MUZZLE = new MuzzleEffect(1f);
    /** 小口径命中火花 */
    public static final Effect HIT = new HitSparkEffect(1f);

    /** 按尺寸取枪口焰。同一 scale 每次调用都会新建，别在 update 里反复调 */
    public static Effect muzzle(float scale){
        return new MuzzleEffect(scale);
    }

    /** 按尺寸取命中火花 */
    public static Effect hit(float scale){
        return new HitSparkEffect(scale);
    }
}
