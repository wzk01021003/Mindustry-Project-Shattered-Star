package project.graphics;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.entities.Effect.EffectContainer;
import mindustry.graphics.Drawf;

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
     * 炮口焰。形态照原版 {@code Fx.shootSmall}：一个向前的长三角 + 一个压在枪口后面的短三角，
     * 8 帧线性收缩。原版只有这两个三角、而且只上纯色；这里在它下面垫了两层发光：
     *
     * <ul>
     *   <li><b>自发光光晕</b>：一圈径向渐变的白热球，把三角的硬边糊掉，
     *       读起来是「从枪口炸开的一团光」，而不是「贴上去的两个三角形」。</li>
     *   <li><b>场景光</b>：{@link Drawf#light} 注册一个真光源，开火瞬间机体和地面会被照亮。
     *       原版枪口特效没有这一层，而它恰恰是「发光」最容易被眼睛读到的地方。</li>
     * </ul>
     *
     * <p>颜色不走原版的 {@code Pal.lighterOrange}，仍用机体的橙金三段
     * （{@link #WARM} → {@link #DEEP}，核心 {@link #CORE}），和机体条纹同一条色路。
     *
     * <p>落点就是 {@code shootEffect.at()} 给的子弹生成点，不做任何回退——
     * 实测原版 {@code Fx.shootSmall} 不做偏移也正好压在枪口上。
     */
    public static class MuzzleEffect extends Effect{
        /** 焰长（世界单位，1 格 = 8）。取原版 15px，约 1.9 格 */
        public float len = 15f;
        /** 根部全宽。原版 6px */
        public float width = 6f;
        /** 枪口后面压着的那一小截尾焰，原版 3px */
        public float backLen = 3f;
        /** 场景光半径，0.5 格上下，够把枪口周围一圈照亮 */
        public float glow = 26f;
        /** 场景光不透明度。调过 1 就很刺眼，0.7 刚好 */
        public float glowOpacity = 0.7f;
        /** 自发光光晕半径，相对焰长 */
        public float haloScale = 0.35f;

        public MuzzleEffect(){
            this(1f);
        }

        public MuzzleEffect(float scale){
            len *= scale;
            width *= scale;
            backLen *= scale;
            glow *= scale;
            lifetime = 8f;
            clip = (len + glow) * 2f + 40f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            // 原版是线性收缩：出膛第一帧最长，8 帧收到 0
            float fout = e.fout();
            float rot = e.rotation;
            float w = width * (0.35f + 0.65f * fout);

            // ① 场景光：真光源，机体和地面会被照亮
            Drawf.light(e.x, e.y, glow * (0.6f + 0.4f * fout), WARM, glowOpacity * fout);

            Draw.blend(Blending.additive);

            // ② 外辉光：同形状放大一圈、极淡，让火焰轮廓往外"洇"一层。
            //    只靠底下的白热球做不出这个形状——球是圆的，火焰是尖的
            Draw.color(WARM, DEEP, p);
            Draw.alpha(0.3f * fout);
            Drawf.tri(e.x, e.y, w * 1.45f, len * fout * 1.12f, rot);

            // ③ 自发光光晕：径向渐变的白热球，压在根部
            Tmp.c1.set(CORE).a(0.4f * fout);
            Tmp.c2.set(DEEP).a(0f);
            Fill.light(e.x, e.y, 20, len * haloScale * (0.45f + 0.55f * fout), Tmp.c1, Tmp.c2);

            // ④ 主体：前向长三角 + 后向短三角（原版形态）
            Draw.color(WARM, DEEP, p);
            Draw.alpha(0.85f * fout);
            Drawf.tri(e.x, e.y, w, len * fout, rot);
            Drawf.tri(e.x, e.y, w, backLen * fout, rot + 180f);

            // ⑤ 白热核心：同形状缩到六成，压在最里面
            Draw.color(CORE);
            Draw.alpha(fout);
            Drawf.tri(e.x, e.y, w * 0.5f, len * fout * 0.6f, rot);
            Drawf.tri(e.x, e.y, w * 0.5f, backLen * fout * 0.6f, rot + 180f);

            Draw.blend();
        }
    }

    // ============================================================
    //  枪口小烟
    // ============================================================
    /**
     * 炮口余烟。
     *
     * <p>这团烟是<b>战场上的东西</b>，不是机体的挂件：出膛那一下被燃气推着沿炮管冲出去
     * （约 1.5 格），随后迅速减速，交给浮力和风。位置从生成起就钉在世界坐标里，
     * 不跟随单位、不跟随炮塔；机体开走以后，烟还在原处慢慢散。
     *
     * <p>「喷出去」和「不跟炮塔」不矛盾：喷射方向只取开火那一瞬的炮管角度，
     * {@code EffectState.rotation} 在 {@code at()} 时就写死了，之后炮塔怎么转都不会
     * 拖着已经喷出去的烟走；形状的长轴也只是在头几帧沿弹道，之后自动转回竖直。
     *
     * <p>枪口焰只有 6 帧，是「砰」的那一下；烟活 110 帧（约 1.7 秒），是「砰」之后
     * 留在原地的那点痕迹。两者寿命差近二十倍，正好把连发的节奏串起来：
     * 焰负责每一发的点，烟负责整串的线。只有焰没有烟的话，速射武器看起来像在戳空气——
     * 每一下都干净利落地消失，没有任何东西证明上一发刚刚发生过。
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
        /** 出膛后沿炮管喷出的距离，1.5 格。只在 {@link #followBarrel} 打开时生效 */
        public float drift = 12f;
        /** 出膛初速是否沿炮管。默认 true：烟被燃气推着从枪口喷出去，之后才交给浮力 */
        public boolean followBarrel = true;
        /** 侧向散开量，0.45 格。走世界水平方向，不随炮塔转 */
        public float spread = 3.6f;
        /** 出膛后向世界正上方浮起的距离，1.25 格。烟离开炮口就不再属于炮塔了 */
        public float rise = 10f;
        /** 世界水平方向的风，把所有团往同一侧慢慢推。这是「属于战场」而不是「属于武器」最直接的信号 */
        public float wind = 1.4f;
        /** 峰值不透明度。这是全特效最容易调过头的地方，0.4 已经能看清了 */
        public float opacity = 0.42f;
        /** 寿命，110 帧约 1.7 秒。比枪口焰（6 帧）长一个量级，才可能在原地留下东西 */
        public float life = 110f;

        public MuzzleSmokeEffect(){
            this(1f);
        }

        public MuzzleSmokeEffect(float scale){
            size *= scale;
            drift *= scale;
            spread *= scale;
            rise *= scale;
            wind *= scale;
            lifetime = life;
            // 烟一生成就钉死在世界坐标上：不跟随单位、不跟随炮塔。
            // Effect 只在 followParent 为真、且 at() 的 data 是单位时才会跟着单位走；
            // 这里两个开关都显式关掉并留在这里，避免以后随手打开又变成「机体在冒烟」
            followParent = false;
            rotWithParent = false;
            clip = (drift + rise + spread + wind + size * 3f) * 2f + 40f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            // 炮管方向只用来算出膛那一下的初速。形态（椭圆长轴）、侧向散开、上浮
            // 全部在世界坐标系里算，所以炮塔转到哪儿，这团烟都是一样的竖直形状，
            // 不会像之前那样整条跟着炮管横过来
            float rot = e.rotation;
            float cx = Mathf.cosDeg(rot), cy = Mathf.sinDeg(rot);

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
                float along = core ? 1f : root ? 0.12f : rand.random(0.45f, 0.9f);
                // 碎絮上下交替，而不是纯随机：随机时两团可能同时偏同一侧，
                // 整条烟就斜着往外飘，看着像没对准炮管中轴线
                float side = (core || root) ? 0f : (i % 2 == 0 ? 1f : -1f) * rand.random(0.55f, 0.95f);
                float delay = (core || root) ? 0f : rand.random(0.04f, 0.12f);
                float sz = core ? 1f : root ? 0.62f : rand.random(0.6f, 0.85f);
                // 越外层越淡；主团不衰减，碎絮只有它的七成上下
                float dim = core ? 1f : root ? 0.85f : rand.random(0.7f, 0.9f);

                float pp = Mathf.clamp((p - delay) / (1f - delay));
                // 沿炮管喷出去：pow3Out 表示「出膛那一下最猛、之后迅速减速」。
                // 换成匀速或 pow2Out，后半段还在往前爬，整条烟会一路跟着炮口跑，像挂在枪上的拖尾。
                // 方向只取开火那一瞬的炮管角度（e.rotation 在生成时就固定了），
                // 炮塔之后怎么转都拖不动已经喷出去的烟
                float travel = followBarrel ? drift * along * Interp.pow3Out.apply(pp) : 0f;
                // 世界坐标下的三股位移，全部与 rot 无关：
                //   ① 侧向散开 —— 出膛后被气流推开，越推越远
                //   ② 上浮 —— 前慢后快，热烟自己往上爬，不是匀速平移
                //   ③ 风 —— 全场同一个方向，是「这团烟属于战场」最直接的暗示
                float sideD = spread * side * Interp.pow2Out.apply(pp);
                float riseD = rise * sz * (0.35f * pp + 0.65f * pp * pp);
                float windD = wind * pp;
                // 每团相位不同的小摆动。没有它，四团烟就是四条平行上升的直线；
                // 相位若相同，会变成整块一起左右摆，更像贴图在动而不是空气在动
                float turb = Mathf.sin(pp * 3.1f + i * 1.7f) * 0.9f * pp;
                float x = e.x + cx * travel + sideD + windD + turb;
                float y = e.y + cy * travel + riseD;
                // 半径一路长到终态：开火瞬间迅速胀开，之后还在慢慢变大。
                // 若像焰那样在 30% 内长完，剩下 70% 就是一坨定住不动的灰斑
                float r = size * sz * (0.3f + 0.7f * Mathf.pow(pp, 0.55f));

                // 起手两帧涨到满不透明度，之后是很长的淡出尾巴。
                // 这个 0.06 别调大：烟比焰亮得还慢的话，第一帧炮口是空的，像没打出去。
                // 尾段用 1.2 次幂（不是 1.6）：烟是慢慢散开的，不是到点就熄，
                // 太陡的衰减会让后一半寿命完全看不见，等于寿命白给
                float a = Mathf.clamp(pp / 0.06f) * Mathf.pow(1f - pp, 1.2f) * opacity * dim;

                // 四层递减：最外那层只有 0.1a，专门用来磨掉边缘。
                // 之前三层时最外圈直接从 0 跳到 0.4a，小半径下能看出锯齿状的硬边
                //
                // 每层画两个错开的圆，叠成长宽比约 1.5 的椭圆：
                // 烟是被气流推着走的，形状本来就被拉长。单个正圆不管怎么调 alpha 都是一颗球，
                // 这正是它上一版看起来像「贴上去的灰点」的原因。
                float er = r * 0.82f;
                // 越老的烟拉得越长：上升时上缘跑得快、下缘被拖住，形状会自己变细长
                float eo = r * (0.45f + 0.25f * pp);
                // 长轴方向：出膛那一瞬沿弹道（正被喷出去，烟柱是横的），
                // 之后浮力接管，慢慢转回世界竖直。只有形状在转，位置始终钉在世界坐标上
                float shapeMix = Mathf.pow(pp, 1.4f);
                float dx = Mathf.lerp(cx, 0f, shapeMix);
                float dy = Mathf.lerp(cy, 1f, shapeMix);
                float dl = (float)Math.sqrt(dx * dx + dy * dy);
                if(dl > 0.001f){
                    dx /= dl;
                    dy /= dl;
                }else{
                    dx = 0f;
                    dy = 1f;
                }
                float ax = x + dx * eo, ay = y + dy * eo;
                float bx = x - dx * eo, by = y - dy * eo;

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
