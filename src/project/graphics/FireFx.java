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
    /** 烟色。暖灰而不是中性灰：纯灰放在橙金机体旁边会显脏，暖灰是同一色温 */
    public static final Color SMOKE = Color.valueOf("8a8078");

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
        /** 焰长（世界单位，1 格 = 8）。0.75 格，略长于机身高 */
        public float len = 6f;
        /** 根部半宽，0.16 格：全宽 0.31 格，比机身窄得多，不会盖住机体 */
        public float halfWidth = 1.25f;
        /** 侧向火星的张开角度 */
        public float sparkAngle = 38f;
        /** 侧向火星长度，0.38 格 */
        public float sparkLen = 3f;
        /** 侧向火星数量（单侧） */
        public int sparks = 2;
        /**
         * 从子弹生成点沿弹道反方向回退的距离。默认 0：生成点本身就是枪口。
         *
         * <p>依据是贴图：{@code large-weapon-allotropes.png} 里那块橙色实心区
         * （枪口）落在贴图中心上方约 3px，而武器的 {@code shootY} 正好是 3 ——
         * 也就是说原版把子弹中心直接放在了枪口上，而不是"尾端压枪口"。
         *
         * <p>这个字段留着只是为了某些"子弹很长、必须让步"的武器：那种情况下
         * 把它设成 {@code shootY - halfHeight}，而不是无脑等于 {@code shootY}。
         */
        public float back = 0f;

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
            // 从子弹中心退回子弹尾端，也就是炮口
            float ox = e.x - cx * back, oy = e.y - cy * back;

            Draw.blend(Blending.additive);

            // 外层：橙→暗红，长度随 out 拉满，根部随时间收窄
            // 宽度收到 45% 就打住、长度也回收 45%：只靠 fade 乘的话，最后两帧会缩成一根
            // 一像素宽的针，看起来像炮口还挂着一根线，而不是「焰要散了」
            float w = halfWidth * (0.45f + 0.55f * fade);
            float tip = len * (0.35f + 0.65f * out) * (0.55f + 0.45f * fade);
            Draw.color(main(e), DEEP, p);
            Draw.alpha(fade * 0.9f);
            tri(ox, oy, nx, ny, w, cx, cy, tip);

            // 内层：白热，长度和宽度都打六折，叠在外层中间
            Draw.color(CORE);
            Draw.alpha(fade);
            tri(ox, oy, nx, ny, w * 0.55f, cx, cy, tip * 0.58f);

            // 侧向火星：从根部往两边各甩两条短线，是「火药被挤出来」的那部分
            if(sparks > 0 && p < 0.6f){
                rand.setSeed(e.id);
                Lines.stroke(0.55f * fade);
                Draw.color(WARM, DEEP, p);
                Draw.alpha(fade * 0.8f);
                for(int i = 0; i < sparks; i++){
                    float side = i % 2 == 0 ? 1f : -1f;
                    float a = rot + side * (sparkAngle + rand.range(16f));
                    float l = sparkLen * (0.4f + 0.6f * out) * rand.random(0.7f, 1.15f);
                    Lines.line(ox, oy,
                        ox + Mathf.cosDeg(a) * l,
                        oy + Mathf.sinDeg(a) * l);
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
    //  枪口小烟
    // ============================================================
    /**
     * 炮口余烟。
     *
     * <p>枪口焰只有 6 帧，是「砰」的那一下；烟活 30 帧，是「砰」之后留在原地的痕迹。
     * 两者寿命差五倍，正好把连发的节奏串起来：焰负责每一发的点，烟负责整串的线。
     * 只有焰没有烟的话，速射武器看起来像在戳空气——每一下都干净利落地消失，
     * 没有任何东西证明上一发刚刚发生过。
     *
     * <p>必须用 {@link Blending#normal}。additive 会把灰烟加成白雾，
     * 在暗色地形上等于贴了一块发光板，比不加更糟；这也是原版 {@code Fx.smoke}
     * 虽然丑但没改混合方式的原因。
     *
     * <p>烟团不做旋转贴图，而是三层同心圆叠出柔边：小尺寸（半径不到 10px）下
     * 硬边圆的锯齿比贴图糊边更容易看出来，三层递减正好把边缘磨掉。
     */
    public static class MuzzleSmokeEffect extends Effect{
        /** 单团终态半径，0.3 格 */
        public float size = 2.4f;
        /** 团数：1 主团 + 1 炮口团 + 其余碎絮 */
        public int puffs = 4;
        /** 沿炮管方向漂出的距离，0.69 格。只在 {@link #followBarrel} 打开时生效 */
        public float drift = 5.5f;
        /** 出膛初速是否沿炮管。默认 false：烟只在炮口膨胀上浮，完全不理会炮塔朝向 */
        public boolean followBarrel = false;
        /** 侧向散开量，0.19 格。走世界水平方向，不随炮塔转 */
        public float spread = 1.5f;
        /** 出膛后向世界正上方浮起的距离，0.28 格。烟离开炮口就不再属于炮塔了 */
        public float rise = 2.2f;
        /** 峰值不透明度。这是全特效最容易调过头的地方，0.4 已经能看清了 */
        public float opacity = 0.46f;
        /** 从子弹生成点反退回枪口的距离，同 {@link MuzzleEffect#back}，默认 0 */
        public float back = 0f;

        public MuzzleSmokeEffect(){
            this(1f);
        }

        public MuzzleSmokeEffect(float scale){
            size *= scale;
            drift *= scale;
            spread *= scale;
            rise *= scale;
            lifetime = 30f;
            clip = (drift + spread + rise + size * 2f) * 2f + 40f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            // 炮管方向只用来算出膛那一下的初速。形态（椭圆长轴）、侧向散开、上浮
            // 全部在世界坐标系里算，所以炮塔转到哪儿，这团烟都是一样的竖直形状，
            // 不会像之前那样整条跟着炮管横过来
            float rot = e.rotation;
            float cx = Mathf.cosDeg(rot), cy = Mathf.sinDeg(rot);
            // 烟同样要从子弹生成点退回挂点，否则整团烟悬在枪口前方
            float ox = e.x - cx * back, oy = e.y - cy * back;

            Draw.blend(Blending.normal);
            Draw.color(SMOKE);

            rand.setSeed(e.id);
            for(int i = 0; i < puffs; i++){
                // 第 0 团是主团：压在轴线上、最大最浓、立刻出现；
                // 其余是碎絮：往两侧散、更小更淡、稍晚才冒出来。
                // 早先让三团平等随机，结果它们各自都往轴线附近挤，读起来是一颗球——
                // 枪口烟本来就是「一股主线 + 边缘碎絮」，不是三个并列的团
                boolean core = i == 0;
                // 第二团钉死在炮口，专门负责让烟「从枪口长出来」。
                // 只有主团时它的中心离炮口 2~3px，靠自身柔边接不上，中间会空一段，
                // 看着像烟飘在枪口前面，而不是从枪口冒出来
                boolean root = i == 1;
                float along = core ? 0.62f : root ? 0.1f : rand.random(0.2f, 0.5f);
                // 碎絮上下交替，而不是纯随机：随机时两团可能同时偏同一侧，
                // 整条烟就斜着往外飘，看着像没对准炮管中轴线
                float side = (core || root) ? 0f : (i % 2 == 0 ? 1f : -1f) * rand.random(0.55f, 0.95f);
                float delay = (core || root) ? 0f : rand.random(0.04f, 0.12f);
                float sz = core ? 1f : root ? 0.62f : rand.random(0.6f, 0.85f);
                // 越外层越淡；主团不衰减，碎絮只有它的七成上下
                float dim = core ? 1f : root ? 0.85f : rand.random(0.7f, 0.9f);

                float pp = Mathf.clamp((p - delay) / (1f - delay));
                // followBarrel 默认关：烟从炮口原地涨开，不带着炮管方向的初速。
                // 打开时用 pow3Out，出膛瞬间走完大部分行程就停住——用 pow2Out 的话
                // 后半段还在往前爬，烟会一路跟着炮口跑，看着像拖尾
                float travel = followBarrel ? drift * along * Interp.pow3Out.apply(pp) : 0f;
                // 侧向散开走世界水平，上浮走世界竖直，两者都与 rot 无关
                float sideD = spread * side * Interp.pow2In.apply(pp) * 1.6f;
                float riseD = rise * sz * Interp.pow2In.apply(pp);
                float x = ox + cx * travel + sideD;
                float y = oy + cy * travel + riseD;
                float r = size * sz * (0.34f + 0.66f * Interp.pow3Out.apply(pp));

                // 起手两帧涨到满不透明度，之后缓慢消。这个 0.07 别调大：
                // 烟比焰亮得还慢的话，第一帧炮口是空的，像没打出去
                float a = Mathf.clamp(pp / 0.07f) * Mathf.pow(1f - pp, 1.3f) * opacity * dim;

                // 四层递减：最外那层只有 0.1a，专门用来磨掉边缘。
                // 之前三层时最外圈直接从 0 跳到 0.4a，小半径下能看出锯齿状的硬边
                //
                // 每层画两个错开的圆，叠成长宽比约 1.5 的椭圆：
                // 烟是被气流推着走的，形状本来就被拉长。单个正圆不管怎么调 alpha 都是一颗球，
                // 这正是它上一版看起来像「贴上去的灰点」的原因。
                // 长轴固定竖直（世界 +y），不跟炮管转——否则炮塔一转，烟条就横过来
                float er = r * 0.82f;
                float eo = r * 0.45f;
                float ax = x, ay = y + eo;
                float bx = x, by = y - eo;

                Draw.alpha(a * 0.1f);
                Fill.circle(ax, ay, er * 1.35f);
                Fill.circle(bx, by, er * 1.35f);
                Draw.alpha(a * 0.3f);
                Fill.circle(ax, ay, er);
                Fill.circle(bx, by, er);
                Draw.alpha(a * 0.22f);
                Fill.circle(ax, ay, er * 0.6f);
                Fill.circle(bx, by, er * 0.6f);
            }

            Draw.blend();
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
        /** 最长的一条线，0.48 格 */
        public float len = 3.8f;
        /** 线条数 */
        public int lines = 6;
        /** 白闪半径。别超过线条的起手长度，否则第一帧会被这颗圆点整个盖住，只剩一片白 */
        public float flash = 0.8f;

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
            // 整簇以 rot + 180 为中心：火花是「被打中的东西往回溅」，
            // 全落在子弹来的那一侧。写成 rot ± 39° 等于让火花跟着子弹往前飞，
            // 看起来像武器自己在喷东西，方向是反的
            for(int i = 0; i < lines; i++){
                // 左右交替，而不是每条各自 random(-39,39)：
                // 纯随机时 6 条可能全落在同一侧，整簇火花看着就往一边歪，
                // 像命中点没对上；交替才能保证重心压在弹道法线上
                float side = i % 2 == 0 ? 1f : -1f;
                float a = rot + 180f + side * rand.random(8f, 39f);
                float l = len * out * rand.random(0.4f, 1f);
                Lines.stroke(0.5f * fade * rand.random(0.7f, 1.1f));
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
    /** 小口径枪口余烟，配合 {@link #MUZZLE} 一起用 */
    public static final Effect MUZZLE_SMOKE = new MuzzleSmokeEffect(1f);
    /** 小口径命中火花 */
    public static final Effect HIT = new HitSparkEffect(1f);

    /** 按尺寸取枪口焰。同一 scale 每次调用都会新建，别在 update 里反复调 */
    public static Effect muzzle(float scale){
        return new MuzzleEffect(scale);
    }

    /** 按尺寸取枪口余烟 */
    public static Effect muzzleSmoke(float scale){
        return new MuzzleSmokeEffect(scale);
    }

    /** 按尺寸取命中火花 */
    public static Effect hit(float scale){
        return new HitSparkEffect(scale);
    }
}
