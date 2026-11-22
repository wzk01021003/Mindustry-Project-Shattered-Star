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
import mindustry.content.Blocks;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.Effect.EffectContainer;
import mindustry.graphics.Drawf;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

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
    /**
     * 尘土色。比 {@link #SMOKE} 更亮更黄一点：烟是烧出来的（炭化、偏灰），
     * 尘是从地里崩起来的（矿物微粒、偏黄）。两者混用会让落地扬尘看起来
     * 像「地上冒烟」，而不是「土被砸起来」
     */
    public static final Color EARTH = Color.valueOf("b5a276");
    /** 更暗的土色，重炮开火扬尘用：那层土压在机甲脚边，亮度和地面齐平时就看不见了 */
    public static final Color DUST_DARK = Color.valueOf("9d8d68");

    private static final Rand rand = new Rand();
    /** 四周余尘专用。和 {@link #rand} 分开是为了不打乱主循环那串随机数，
     *  否则加一层余尘会把扇形里每一团的位置全洗牌 */
    private static final Rand ringRand = new Rand();

    private static Color main(EffectContainer e){
        return e.color == null ? MAIN : e.color;
    }

    /**
     * 地板取色：把脚下的地板色压成「带环境倾向的土色」。
     *
     * <p>落地扬尘和开火扬尘是同一件事的两种触发方式——都是「土被掀起来」，
     * 只不过一个由炮弹砸、一个由炮口冲击吹。取色逻辑完全一致，所以抽出来共用。
     *
     * <p>不是直接 {@code lerp(地板色, w)}：地板色只能借色相，明度和饱和度得自己重算。
     * 雪原的地板接近纯白，原样拿来是一团比雪还亮的白斑，等于没画；玄武岩接近纯黑，
     * 尘会整个糊进地面。所以明度在「提亮 / 压暗」之间按地板明度插值，再夹进
     * {@link #minValue}~{@link #maxValue}；饱和度压到 {@link #maxSaturation} 以下，
     * 免得彩色的地板把土染成烟。
     *
     * <p>最后只把结果按 {@link #mix} 搅回 {@link FireFx#EARTH}，留一部分土黄：
     * 尘终究是从地里崩出来的矿物微粒，不该变成地板的同色贴纸。
     */
    public static class FloorTint{
        /** 是否按地板自动调色。关掉就一律用 {@link #base} */
        public boolean enabled = true;
        /** 兜底色，也是调完色之后要搅回去的底子。默认 {@link FireFx#EARTH} */
        public Color base = EARTH;
        /** 地板色混入比例。0 = 纯土黄，1 = 完全跟随地板 */
        public float mix = 0.65f;
        /** 暗地板上提亮多少。玄武岩的明度只有 0.32，不往上抬就整个糊进地面 */
        public float liftDark = 0.28f;
        /** 亮地板上压暗多少。雪原的明度接近 0.95，不提反压才有对比 */
        public float dropBright = 0.20f;
        /** 最终明度夹取，防止提亮过头或压成黑块 */
        public float minValue = 0.48f, maxValue = 0.86f;
        /** 饱和度上限。土是低饱和的东西，原样吃地板的艳色会变成彩色烟雾 */
        public float maxSaturation = 0.42f;

        private final Color sample = new Color();
        private final float[] hsv = new float[3];

        public void tint(float x, float y, Color out){
            out.set(base);
            if(!enabled || Vars.world == null) return;

            Tile tile = Vars.world.tileWorld(x, y);
            if(tile == null) return;

            Floor floor = tile.floor();
            if(floor == null || floor == Blocks.air || floor.mapColor == null) return;

            // getFloorColor 在有 minimap 混色时返回的是共享的 Tmp.c3，必须立刻拷走
            sample.set(tile.getFloorColor());
            if(sample.a <= 0.01f) return;

            sample.toHsv(hsv);
            // 纯黑说明这块地板没有可用颜色：mapColor 是 Block.createIcons 从图标中心
            // 像素采的，虚空、纯黑面板、被贴图覆盖的地块都取不到值。这时留 EARTH，
            // 否则压出来的尘是一团 0.46 明度的灰，连色相都没有
            if(hsv[2] < 0.05f) return;

            // 亮度不能跟着地板走：和地面同亮度就等于没画。而且两条地板要往相反方向调——
            // 玄武岩得提亮、雪原得压暗，等比乘法对 0.24 和 0.94 都只是缩放，
            // 兼顾不了，所以按地板明度在「提亮」和「压暗」之间插值
            float t = Mathf.clamp((hsv[2] - 0.45f) / 0.45f);
            float v = Mathf.clamp(hsv[2] + Mathf.lerp(liftDark, -dropBright, t), minValue, maxValue);
            float s = Mathf.clamp(hsv[1] * 0.9f, 0f, maxSaturation);
            sample.fromHsv(hsv[0], s, v);

            out.lerp(sample, mix);
        }
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
     * （约 1.75 格），随后逐渐减速，斜着往上飘。位置从生成起就钉在世界坐标里，
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
        /** 出膛后沿炮管喷出的距离，1.75 格。只在 {@link #followBarrel} 打开时生效 */
        public float drift = 14f;
        /** 出膛初速是否沿炮管。默认 true：烟被燃气推着从枪口喷出去，之后才交给浮力 */
        public boolean followBarrel = true;
        /** 侧向散开量，0.45 格。走世界水平方向，不随炮塔转 */
        public float spread = 3.6f;
        /**
         * 上浮量，0.25 格。它和 {@link #drift} 一起决定烟柱的仰角：{@code atan(2/14) ≈ 8°}。
         *
         * <p>关键不在这个数，在<b>它走哪条时间曲线</b>。之前 {@code drift} 用
         * {@code pow2Out}、{@code rise} 用 {@code 0.35p + 0.65p²}——一个越走越慢、一个越走越快，
         * 于是前段横着冲、后段几乎垂直爬，末端切线 59°，终点看着只翘 23°。
         * 俯视角的屏幕上方没有地面可参照，那段竖直位移只能被读成「烟飘走了」。
         *
         * <p>现在两项用同一条 {@code pow2Out}：位移矢量的方向全程不变，轨迹是一条直线。
         * 想更平就调小这个值，别换回递增曲线——只要两项不同相，末端就一定会翘起来。
         */
        public float rise = 2f;
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
                // 沿炮管喷出去，用 pow2Out 而不是 pow3Out：
                // pow3Out 在 30 帧内就走完 87% 的行程，剩下的寿命里烟只能往上爬，
                // 轨迹于是变成「一段斜线 + 一段垂直线」，看起来就是「喷一下就开始固定往上飞」。
                // pow2Out 把余速留得更久，烟柱是一条连续上翘的弧线。
                // 方向只取开火那一瞬的炮管角度（e.rotation 生成时就固定了），
                // 炮塔之后怎么转都拖不动已经喷出去的烟
                float travel = followBarrel ? drift * along * Interp.pow2Out.apply(pp) : 0f;
                // 世界坐标下的三股位移，全部与 rot 无关：
                //   ① 侧向散开 —— 出膛后被气流推开，越推越远
                //   ② 上浮 —— 和 ① 同一条 pow2Out，两者速度比恒定，位移方向不变
                //   ③ 风 —— 全场同一个方向，是「这团烟属于战场」最直接的暗示
                float sideD = spread * side * Interp.pow2Out.apply(pp);
                // 上浮必须和 travel 用同一条曲线。两项不同相（一个 pow2Out、一个二次）
                // 就会出现「前段横冲、后段竖爬」的折线——末端切线 59°，读起来就是
                // 「喷一下之后固定往上飞」。同相之后位移方向恒定 ≈8°，全程是一条直线，
                // 没有任何一段是竖直的
                float riseD = rise * sz * Interp.pow2Out.apply(pp);
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
                // 错开量决定椭圆有多长。这里刻意在末端把它收掉：长轴会随着 pp 转到世界竖直，
                // 位置明明已经停住，形状自己「竖起来」也会被读成在上升。
                // 收到 20% 后末段基本是正圆，只剩摊开，不再给方向暗示
                float eo = r * (0.45f + 0.25f * pp) * (1f - 0.8f * Mathf.pow(pp, 2.5f));
                // 长轴方向：出膛那一瞬沿弹道（正被喷出去，烟柱是横的），之后慢慢转回世界竖直。
                // 只有形状在转，位置始终钉在世界坐标上；而 eo 在末段已经收掉八成，
                // 所以这个旋转只在中段可见，末段是正圆，不会「立起来」暗示上升
                float shapeMix = Mathf.pow(pp, 1.9f);
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
    //  落地扬尘
    // ============================================================
    /**
     * 炮弹落地扬起的尘土环。
     *
     * <p>爆炸是「光」，几十帧就没了；扬尘是「土」，要在地上留一秒多。
     * 两者叠在一起才有重量——只有爆炸时，炮弹看起来像在半空中炸开的烟花，
     * 完全没有和地面发生过关系。
     *
     * <p><b>「贴地」是它和枪口烟最根本的区别</b>，也是这类效果最容易做错的地方：
     *
     * <ul>
     *   <li>位移以水平为主，垂直只给 {@link #lift}（不到半格）。枪口烟活 110 帧
     *       能上升 0.75 格，那是因为热气体真的会往上跑；被砸起来的土不会。</li>
     *   <li>整圈按 {@link #squash} 压扁成椭圆。俯视角下地面上的正圆看起来本来就是扁的，
     *       画正圆会像一块竖着插在地上的圆盘。</li>
     *   <li>顺弹道方向的那一侧飞得更远（{@link #pushBias}）：土是被炮弹推出去的，
     *       不是以落点为圆心均匀炸开的。</li>
     * </ul>
     *
     * <p>必须用 {@link Blending#normal}。additive 会把这圈土加成发光的雾，
     * 变成第二个爆炸，反而抢了主视觉。
     *
     * <p>放在 {@code despawnEffect} 上而不是 {@code hitEffect}：后者打中空中单位时
     * 也会触发，那时地上并没有土可以扬。
     */
    public static class GroundDustEffect extends Effect{
        /** 最外圈尘团离落点的距离，6 格。比 splash 半径（35）大一圈，才像被崩起来的土 */
        public float radius = 48f;
        /** 单个尘团半径，1.1 格。必须大到相邻两团能叠上，否则整圈会散成一粒粒灰点 */
        public float size = 9f;
        /** 尘团数量（含落点核心那一团）。外环 14 团在 6 格圆周上弧长约 21，正好叠住 */
        public int puffs = 15;
        /** 垂直压缩比，俯视角下地面圆环的透视系数 */
        public float squash = 0.58f;
        /** 整圈尘土的上浮量，0.38 格。给一点是为了别像贴纸，给多了就成蘑菇云了 */
        public float lift = 3f;
        /** 顺弹道方向的额外行程比。0.3 时最远那一团会甩出环外、和主体断开，0.22 刚好 */
        public float pushBias = 0.22f;
        /** 峰值不透明度。土要把地形盖住一截才叫土，0.5 只能算一层薄雾 */
        public float opacity = 0.85f;
        /** 寿命，80 帧约 1.2 秒。比爆炸（32 帧）长一倍多，负责收尾 */
        public float life = 80f;

        /** 地板取色。和开火扬尘共用同一套算法与默认值，见 {@link FloorTint} */
        public final FloorTint floor = new FloorTint();

        private final Color tint = new Color();

        public GroundDustEffect(){
            this(1f);
        }

        public GroundDustEffect(float scale){
            radius *= scale;
            size *= scale;
            lift *= scale;
            lifetime = life;
            clip = (radius + size * 3f) * 2f + 40f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();

            // 每帧重算，不做缓存：同一个 Effect 单例会被多个落点的容器同时 render，
            // 缓存下来的颜色会让火山的尘一直红到雪山上去
            floor.tint(e.x, e.y, tint);

            Draw.blend(Blending.normal);
            Draw.color(tint);

            rand.setSeed(e.id);
            for(int i = 0; i < puffs; i++){
                // 第 0 团是落点核心：不动、最大、最浓，活到最后。
                // 只让尘团往四周飞的话，中心会先空出来一块——可炮弹分明砸在这里，
                // 最浓的土应该就在弹坑上，而不是一圈凭空外扩的环
                boolean core = i == 0;

                // 角度均分后再抖：纯随机时相邻两团会挤在一起、对面留出空洞，环就断了。
                // 均分保证骨架均匀，抖动负责去掉机械感
                float a = core ? 0f : i * 360f / (puffs - 1) + rand.random(-13f, 13f);
                // 每团错开起跑时间，尘不是整圈同时被点着的
                float dly = core ? 0f : rand.random(0f, 0.16f);
                float pp = Mathf.clamp((p - dly) / (1f - dly));

                // 顺弹道方向跑得更远：炮弹是往前撞进地面的，土也被带着往前推
                float bias = 1f + pushBias * Math.max(0f, Mathf.cosDeg(a - e.rotation));
                // 扩散先快后慢，用 pow3Out 让大半个行程在前 1/3 走完：
                // 土是被崩出去的，落地那一下就该铺到外圈，剩下的寿命只负责慢慢落定。
                // 换成 pow2Out 时到半条命才走完 3/4，画面会像「尘土在匀速膨胀」。
                // 起点取 0.25 而不是贴着 0：全程贴着圆心取点时，落点会糊成一坨，
                // 看不出是「从坑里崩出来的环」
                float d = core ? 0f : radius * rand.random(0.35f, 1f) * bias * Interp.pow3Out.apply(pp);
                float x = e.x + Mathf.cosDeg(a) * d;
                // 垂直方向压扁成椭圆，再整体抬一点离地
                float y = e.y + Mathf.sinDeg(a) * d * squash + lift * pp;

                // 半径一路长大，越晚越大：尘团边飘边散，不是定住的一颗颗球
                float r = size * (core ? 1.6f : rand.random(0.5f, 1f)) * (0.4f + 0.6f * Mathf.pow(pp, 0.5f));
                // 起手几帧迅速变浓，之后长尾淡出。1.4 次幂比烟陡：
                // 土沉得快，落到地上就没了，不会像烟那样在天上飘很久
                float a0 = Mathf.clamp(pp / 0.12f) * Mathf.pow(1f - pp, 1.4f) * opacity
                    * (core ? 1f : rand.random(0.7f, 1f));

                // 柔边用 Fill.light 一次画出「中心实、边缘渐隐」的径向渐变。
                // 之前是三层同心圆递减 alpha，结果每一层的边界都留下一个可见的环，
                // 13 团叠在一起看起来是一串气泡浮在地上，完全不像土。
                // 渐变没有边界，相邻尘团叠加时也只会在浓度上叠，不会叠出轮廓
                Tmp.c1.set(tint).a(a0);
                Tmp.c2.set(tint).a(0f);
                Fill.light(x, y, 20, r * 1.55f, Tmp.c1, Tmp.c2);
            }

            Draw.blend();
        }
    }

    // ============================================================
    //  开火扬尘
    // ============================================================
    /**
     * 重型火炮开火时从炮口掀起来的地面扬尘。
     *
     * <p>和 {@link GroundDustEffect} 是同一件事的两种触发：都是土被掀起来，共用
     * {@link FloorTint} 的取色。但两者的成因完全不同，形状必须分开做——
     *
     * <ul>
     *   <li><b>方向</b>：落地扬尘是炮弹砸进地面，土向<b>四周</b>崩；开火扬尘是炮口气压
     *       把土<b>往前推</b>，只在弹道前方铺开。做成圆形的话，一炮打出去炮口周围
     *       凭空鼓起一圈土，像踩了个隐形的地雷。</li>
     *   <li><b>大小</b>：落地是 6 格的一圈，开火只有 2 格多。炮口冲击掀的是脚边那点浮土，
     *       不是弹坑。</li>
     *   <li><b>时间</b>：落地扬尘 80 帧，负责在爆炸之后收尾；开火扬尘 36 帧，必须在下一次
     *       开火（堡垒 reload 60 帧）之前散干净，否则连射时会糊成一层常驻的雾。</li>
     *   <li><b>厚度</b>：压得比落地更扁（0.5 对 0.58），上浮只有 0.15 格。炮口吹起来的土
     *       是贴着地面滑出去的，飘起来就成烟了。</li>
     * </ul>
     *
     * <p>走普通混合，和土一样。additive 会把这层加成发光的雾，看起来像炮口在喷蒸汽。
     *
     * <p>挂在 {@code shootEffect} 上（和枪口焰同一个位置）。不要挂 {@code smokeEffect}：
     * 那个位置会被原版 {@code Turret} 在冷却时反复播，土的密度会跟着热量乱跳。
     */
    public static class MuzzleDustEffect extends Effect implements DustRenderer.Painter{
        /** 扬尘最远推到炮口外多远，3 格。再远就跟炮口脱节，像地上起了阵风 */
        public float radius = 24f;
        /** 单团半径，0.55 格。比落地扬尘小：炮口掀的是浮土，块头没那么大 */
        public float size = 4.4f;
        /** 尘团数量（含炮口正下方那一团）。比落地扬尘密，碎一点才像被吹散的土粒 */
        public int puffs = 14;
        /**
         * 扇形半角。60° 覆盖前向锥，再大就会往机体背后扬——俯视角下那层土会盖住
         * 炮管和机身，看着像单位自己陷进地里
         */
        public float spread = 60f;
        /** 垂直压缩，0.5。比落地扬尘更扁：土是被气压贴着地面推出去的 */
        public float squash = 0.5f;
        /** 上浮量，0.15 格。给一点是为了别像贴纸，给多了就成蘑菇云 */
        public float lift = 1.2f;
        /** 峰值不透明度 */
        public float opacity = 0.8f;
        /** 寿命，36 帧 */
        public float life = 36f;
        /** 炮口正下方那一团的尺寸倍数。它是整簇里最浓的，负责「土就在脚下」 */
        public float coreSize = 1.4f;

        /**
         * 要不要在扇形底下垫一圈四周余尘。现实里重炮开火时冲击波打到地面会反射出
         * 地表波，沿地面<b>径向</b>扩散——这才是真实照片里车体被整团尘罩住的主因，
         * 纯前向锥是俯视角游戏才有的简化。这里补一层很淡的环来暗示「不止往前」，
         * 又不能把方向感糊掉，所以半径和透明度都压得很低。
         */
        public boolean ring = true;
        /**
         * 余尘半径占主层的比例。0.45 → scale 1.6 下 2.2 格，比机体宽一圈。
         * 给到 0.3 时整圈都压在机身上，p 值小的时候根本露不到地面，
         * 读作「机体脏了一块」而不是「脚边有土」
         */
        public float ringReach = 0.45f;
        /** 余尘团数。径向均分，不需要太多，够把「圈」读出来就行 */
        public int ringPuffs = 11;
        /**
         * 余尘峰值不透明度。土色和草地只差二十几个色阶，乘 0.2 之后只有五阶变化，
         * 等于没画；0.42 才读得出机体周围多了一层土，同时仍不到主体 0.8 的一半，
         * 不会盖住前向的方向感
         */
        public float ringOpacity = 0.42f;
        /**
         * 余尘走完全程所占的寿命比例。0.55 → 20 帧，比主体的 36 帧早散。
         * 余尘是冲击波的一次反射，主体还在往前推的时候它就该没了——
         * 同时淡出的话，尾巴会拖着两条一样长的尘，读起来像两个特效叠在一起
         */
        public float ringLife = 0.55f;

        /** 地板取色，和 {@link GroundDustEffect} 同一套 */
        public final FloorTint floor = new FloorTint();

        private final Color tint = new Color();

        public MuzzleDustEffect(){
            this(1f);
        }

        public MuzzleDustEffect(float scale){
            radius *= scale;
            size *= scale;
            lift *= scale;
            // 比落地扬尘少混地板色（0.3 对 0.65）。草地色相 97°、土色相 42°，
            // 五五开之后色相落在 70°——那是一块「更灰的草」，不是土；在草地上
            // 唯一的可见部分就是它压在机体上的那一层，于是整颗尘读成「机体脏了」。
            // 0.3 让土色相主导，地板只负责把色相往当地土壤上带一点
            floor.mix = 0.3f;
            // 兜底土色压暗一档。EARTH 的明度 0.71 比草地（0.59）还亮，
            // 原样用会在草地上读成一团发光的黄雾——尘是暗的，它挡光，不发光
            floor.base = DUST_DARK;
            // 提得少、压得多：草地在中间明度，按原来那组参数会被提亮到 0.72
            floor.liftDark = 0.05f;
            floor.dropBright = 0.22f;
            lifetime = life;
            clip = (radius + size * 3f) * 2f + 40f;
        }

        @Override
        public void render(EffectContainer e){
            // 不在这里画：交给 DustRenderer 在单位下面那一层画。这里只按
            // EffectState 的 id 保活，位置 / 朝向 / 寿命仍由 Effect 提供——
            // 暂停、切图、单位死亡这些边界情况就不用另写一套回收逻辑
            DustRenderer.retain(e.id, e.x, e.y, e.rotation, life, e.id, this);
        }

        @Override
        public void paint(float x, float y, float rotation, float p, int seed){
            // 每帧重算，不缓存：单例会被多个开火点同时绘制
            floor.tint(x, y, tint);

            Draw.blend(Blending.normal);
            Draw.color(tint);

            // 先画四周余尘，垫在扇形底下。放前面不只是为了遮挡关系（它该被前向的
            // 主体盖住），也是为了不改主循环的随机序列——见 ringRand 的注释
            if(ring){
                float rp = Mathf.clamp(p / ringLife);
                ringRand.setSeed(seed);

                // 圆心沿弹道反面退回机体：Effect 只拿得到炮口坐标（堡垒的 weapon.x = 9），
                // 而这一圈土是被后坐震起来的、贴在整个机体脚下，绕炮口画的话
                // 机体后半截就一点土都没有了。1.3 个 size ≈ 9.2（scale 1.6），
                // 刚好抵消那 9 个单位的炮口偏移
                float bx = x - Mathf.cosDeg(rotation) * size * 1.3f;
                float by = y - Mathf.sinDeg(rotation) * size * 1.3f;

                for(int i = 0; i < ringPuffs; i++){
                    // 径向均分再抖 14°。抖太多会几团挤一起、留下空档，圈就断了
                    float a = i * 360f / ringPuffs + ringRand.random(-14f, 14f);
                    // pow2Out：地表波比气压波慢半拍，但也是往外的，不是匀速
                    float d = radius * ringReach * ringRand.random(0.7f, 1f) * Interp.pow2Out.apply(rp);
                    float rx = bx + Mathf.cosDeg(a) * d;
                    // 贴地：这圈是被地面反射带上来的浮土，几乎不离开地表
                    float ry = by + Mathf.sinDeg(a) * d * squash;

                    float r = size * ringRand.random(0.55f, 1f) * (0.5f + 0.5f * Mathf.pow(rp, 0.5f));
                    // 起手 0.06 比主体的 0.08 还陡：反射波是瞬时起爆，没有加速段
                    float a0 = Mathf.clamp(rp / 0.06f) * Mathf.pow(1f - rp, 1.6f) * ringOpacity;

                    Tmp.c1.set(tint).a(a0);
                    Tmp.c2.set(tint).a(0f);
                    Fill.light(rx, ry, 18, r * 1.3f, Tmp.c1, Tmp.c2);
                }
            }

            rand.setSeed(seed);
            for(int i = 0; i < puffs; i++){
                // 第 0 团压在炮口正下方：气压最先掀的是脚边那层土
                boolean core = i == 0;

                // 角度在扇形内均分再抖。纯随机会让几团挤在轴线上、两侧空掉，
                // 扇形读起来就只是「一条往前喷的灰柱」，没有「面」的宽度
                float a = rotation + (core ? rand.random(-spread, spread)
                    : Mathf.lerp(-spread, spread, (i - 1f) / Math.max(1f, puffs - 2f))
                        + rand.random(-9f, 9f));
                // 错开起跑：整排同时炸开像一堵推出去的墙
                float dly = core ? 0f : rand.random(0f, 0.12f);
                float pp = Mathf.clamp((p - dly) / (1f - dly));

                // 偏离弹道轴线的程度，0 = 正前方，1 = 扇形边缘
                float off = Mathf.clamp(Math.abs(a - rotation) / spread);
                // 轴线上的团跑得最远、两侧的近：炮口气压是往前顶的，侧向只分到一点。
                // 不给这个收窄时，九团等距铺在 ±70° 上，整簇读成一个半圆饼——
                // 形状是扇的，但没有「往前冲」的方向感
                float reach = 1f - 0.45f * off;

                // 扩散用 pow3Out：炮口气压是「一下子」推出去的，前 1/3 的寿命就该铺满，
                // 剩下的时间只负责淡出。换成 pow2Out 时到半条命才走完 3/4，
                // 画面读作「尘土在匀速膨胀」，炮口那一下的爆发感全没了
                float d = core ? radius * 0.12f
                    : radius * reach * rand.random(0.45f, 1f) * Interp.pow3Out.apply(pp);
                float px = x + Mathf.cosDeg(a) * d;
                float py = y + Mathf.sinDeg(a) * d * squash + lift * pp;

                float r = size * (core ? coreSize : rand.random(0.5f, 1f))
                    * (0.45f + 0.55f * Mathf.pow(pp, 0.5f));
                // 起手比落地扬尘更陡（0.08 对 0.12）：炮口这一下要先「炸」出来再散。
                // 边缘再压 25%：两侧本来就没多少土，和轴线一样浓时会糊成一个圆
                float a0 = Mathf.clamp(pp / 0.08f) * Mathf.pow(1f - pp, 1.2f) * opacity
                    * (core ? 1f : rand.random(0.6f, 1f)) * (1f - 0.25f * off);

                // 柔边同样用 Fill.light，理由见 GroundDustEffect：叠同心圆会留下可见的环
                Tmp.c1.set(tint).a(a0);
                Tmp.c2.set(tint).a(0f);
                Fill.light(px, py, 18, r * 1.5f, Tmp.c1, Tmp.c2);
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
    /** 落地扬尘。半径按 splash 30~40 的火炮口径给的，更大的炮用 {@link #dust(float)} */
    public static final Effect DUST = new GroundDustEffect(1f);
    /** 开火扬尘。给重型火炮用：炮口那一圈往前推的土，尺寸对应 hitSize ≈ 8 */
    public static final Effect MUZZLE_DUST = new MuzzleDustEffect(1f);

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

    /** 按尺寸取落地扬尘。口径越大给得越大，一般取 splash 半径 / 35 */
    public static Effect dust(float scale){
        return new GroundDustEffect(scale);
    }

    /** 按尺寸取开火扬尘。hitSize 8 用 1，堡垒那一级（hitSize 24）用 2 ~ 2.5 */
    public static Effect muzzleDust(float scale){
        return new MuzzleDustEffect(scale);
    }

    /**
     * 一次给一颗子弹接上三件套（枪口焰 / 余烟 / 命中火花）。
     *
     * <p>三个特效都挂在 {@link BulletType} 上，任何武器、任何单位都能用：
     * <pre>{@code
     * bullet = new BasicBulletType(5f, 9){
     *     {
     *         // ... 其余参数
     *         FireFx.attach(this, 1f);
     *     }
     * };
     * }</pre>
     *
     * <p><b>装之前先看两件事：</b>
     *
     * <ul>
     *   <li><b>落点</b>：{@code shootEffect} 画在「挂点 + shootY」处，也就是子弹生成点。
     *       有独立炮管的武器通常正好落在管口；而 {@code x = y = 0} 的内置武器
     *       （例如 nova-at 的发射口在机体中心）会把火焰画在机身上，糊住单位。
     *       遇到这种要么改 shootY，要么不要装焰。</li>
     *   <li><b>射速</b>：烟活 110 帧，{@code reload = 2} 的速射武器会同时存在上百团，
     *       每团 4 个 puff × 6 个圆。给速射武器装烟请单独 {@code new MuzzleSmokeEffect()}
     *       并把 {@code life} 压到 30 左右，或者只装焰不装烟。</li>
     * </ul>
     *
     * @param scale 口径系数，hitSize 8 的机甲用 1，大一圈的炮用 1.4 ~ 2.5
     */
    public static void attach(BulletType bullet, float scale){
        bullet.shootEffect = muzzle(scale);
        bullet.smokeEffect = muzzleSmoke(scale);
        bullet.hitEffect = hit(scale);
    }
}
