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
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Effect.EffectContainer;
import mindustry.entities.bullet.BulletType;
import mindustry.graphics.Drawf;

/**
 * 能量武器特效：激光的枪口脉冲与命中光斑。
 *
 * <p>为什么不复用 {@link FireFx} 的枪口焰：那套形状是「一个向前的长三角 + 一个压在
 * 枪口后面的短三角」，读的是火药燃气被喷出枪管的过程——有后焰、有烟、有白热核心。
 * 激光没有这个过程。它从枪口「亮起来」，然后瞬间收掉，中间没有燃烧物。
 * 把激光塞进那个三角里，看起来只是一支会发绿光的枪在打实弹。
 *
 * <p>所以这一套只做三件事：
 * <ul>
 *   <li><b>方向</b>：一条极细的锥（宽高比 1:7），不是焰那种胖三角。激光是准直的线，
 *       任何「鼓起来」的形状都会让它变回火焰。</li>
 *   <li><b>亮度</b>：核心纯白，中间浅绿，外圈深绿。三道同轴、依次变宽变暗，
 *       叠出「中心过曝、边缘洇开」的辉光，而不是一整块绿。</li>
 *   <li><b>时间</b>：6 帧，出膛第一帧最长最亮，之后长度线性收到 0。
 *       激光的「发射」是一个瞬间，不能像焰那样有 8 帧的余韵。</li>
 * </ul>
 *
 * <p>颜色默认取异构新星的机体绿（{@code 84f491}）。换武器时用 {@link #muzzle(float, Color)}
 * 传主色即可，白芯和深色边会自动跟着主色推导——白芯固定是白，深色边是主色压暗后的结果。
 */
public class LaserFx{

    /** 白热芯。任何颜色的激光，中心都是过曝的白，否则读不出「能量」 */
    public static final Color CORE = Color.valueOf("ffffff");
    /** 光束主体，主色的提亮版 */
    public static final Color BEAM = Color.valueOf("ccffd8");
    /** 主色，和机体辉光同色 */
    public static final Color GLOW = Color.valueOf("84f491");
    /** 末端暗色，越靠外越暗 */
    public static final Color DEEP = Color.valueOf("3fae62");

    private static final Rand rand = new Rand();

    /** 实例自带的颜色，优先于 {@link #GLOW}；{@code Effect.at} 传的 color 仍然最优先 */
    private static Color pick(EffectContainer e, Color own){
        return e.color != null ? e.color : (own != null ? own : GLOW);
    }

    // ============================================================
    //  枪口脉冲
    // ============================================================
    /**
     * 激光枪口脉冲。一圈外扩的光圈 + 一条细光束 + 一颗枪口光球，5 帧内收完。
     *
     * <p><b>光圈才是原版的手感来源。</b>查一下 v160 的源码就清楚了：
     * {@code LaserBoltBulletType} 的构造里写着
     * {@code smokeEffect = hitEffect = despawnEffect = Fx.hitLaser}——注意第一项，
     * 这一项是<b>枪口</b>特效。所以原版激光每开一枪，枪口除了
     * {@code Fx.shootSmall} 那个橙锥，还会同时播一遍 {@code Fx.hitLaser}：
     *
     * <pre>
     * color(Color.white, Pal.heal, e.fin());
     * stroke(0.5f + e.fout());
     * Lines.circle(e.x, e.y, e.fin() * 5f);
     * </pre>
     *
     * <p>白→绿、实色、张开就完。没有任何叠加混合，所以它在任何地形上都是硬的。
     * 上一版把这层当成「原版在枪口误播命中」删掉了，只剩一条叠加的线——
     * 手感也就跟着没了。现在把它按原样做回来，只是半径放到 7（光束有 15px 长，
     * 5 太小包不住），并和光束、光球合成同一个 Effect，时序才对得上。
     */
    public static class LaserMuzzleEffect extends Effect{
        /** 光束长度，2.25 格。和原版 {@code Fx.shootSmall} 的 15 对齐——再短就只是枪口一点绿光 */
        public float len = 18f;
        /** 光束半径基准，1.6。白芯按 1.2 倍画，实际 2px 宽：原版枪口焰起手就是 6px 宽 */
        public float width = 1.6f;
        /** 场景光半径 */
        public float glow = 16f;
        /** 场景光不透明度 */
        public float glowOpacity = 0.55f;
        /** 光圈最大半径。原版 {@code Fx.hitLaser} 是 5，这里 9：原版那个环只有 10px 直径，
         *  在 8 格机身上只够包住枪口，读不出「一次放电」 */
        public float ring = 9f;
        /** 光圈收细量。线宽按 {@code 0.45 + ringStroke * fout} 从 1.8 收到 0.45 */
        public float ringStroke = 1.35f;
        /** 主色。null 用 {@link LaserFx#GLOW}，换武器时在工厂里设 */
        public Color tint;

        public LaserMuzzleEffect(){
            this(1f);
        }

        public LaserMuzzleEffect(float scale){
            len *= scale;
            width *= scale;
            glow *= scale;
            ring *= scale;
            // 5 帧。nova 的 reload 是 7 帧，8 帧的特效会首尾相接、一帧不落地糊成常亮；
            // 留 2 帧空档，每次开火才有「一下一下」的节奏
            lifetime = 5f;
            clip = (len + glow + ring) * 2f + 40f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            float fout = e.fout();
            float rot = e.rotation;
            Color glowColor = pick(e, tint);

            // 前 45% 是「过曝」窗口，之后才交给衰减
            float hot = Mathf.clamp(1f - p / 0.45f);
            // 光束前 40% 钉死、之后一次收回。乘 fout 线性缩水会挂出一根越来越短的棍子
            float shrink = Interp.pow3In.apply(Mathf.clamp((p - 0.4f) / 0.6f));
            float lenD = len * (1f - shrink);

            Drawf.light(e.x, e.y, glow * (0.85f + 0.15f * fout), glowColor,
                glowOpacity * (0.45f + 0.55f * fout));

            // ① 光圈：原版 Fx.hitLaser 的形态——普通混合、白→主色、张开的同
            //    时线宽收细。这一层是「力度」的主力：实色、一帧张开，比任何叠加辉光都硬
            Draw.blend(Blending.normal);
            Draw.color(CORE, glowColor, p);
            Draw.alpha(1f);
            Lines.stroke(0.45f + ringStroke * fout);
            Lines.circle(e.x, e.y, ring * Interp.pow2Out.apply(p));

            float tipx = e.x + Mathf.cosDeg(rot) * lenD;
            float tipy = e.y + Mathf.sinDeg(rot) * lenD;

            Draw.blend(Blending.additive);

            // ② 外辉光：3.6 倍宽、极淡，负责边缘过曝
            Draw.color(glowColor, DEEP, p);
            Draw.alpha((0.26f + 0.34f * hot) * fout);
            Lines.stroke(width * 3.6f);
            Lines.line(e.x, e.y, tipx, tipy);

            // ③ 光束主体
            Draw.color(BEAM, DEEP, p);
            Draw.alpha(0.85f * fout);
            Lines.stroke(width * 2f);
            Lines.line(e.x, e.y, tipx, tipy);

            // ④ 白热芯：普通混合、alpha 接近 1。这一层是光束的「硬」——
            //    整条走叠加混合时，在草地这种中亮地形上等于往亮处再加亮，叠多少都看不出来
            Draw.blend(Blending.normal);
            Draw.color(CORE);
            Draw.alpha(Mathf.clamp(1.15f - p * 0.9f));
            Lines.stroke(width * 1.2f);
            Lines.line(e.x, e.y, tipx, tipy);
            // 圆头收口：Lines.line 两端是平的，宽到 2px 以上就会露出方角
            Fill.circle(e.x, e.y, width * 0.6f);
            Fill.circle(tipx, tipy, width * 0.6f);

            Draw.blend(Blending.additive);

            // ⑤ 枪口光球：过曝窗口内几乎全白，把光圈和光束的根部糊成一个亮点
            Tmp.c1.set(CORE).a(0.55f + 0.45f * hot);
            Tmp.c2.set(glowColor).a(0f);
            Fill.light(e.x, e.y, 20, width * 3.2f * (0.6f + 0.4f * hot), Tmp.c1, Tmp.c2);

            Draw.blend();
        }
    }

    // ============================================================
    //  命中光斑
    // ============================================================
    /**
     * 激光命中光斑。和 {@link FireFx.HitSparkEffect} 的火花是两种东西：
     *
     * <ul>
     *   <li>火花<b>只往后半球溅</b>，因为破片是往回弹的；激光是穿透，光斑要
     *       <b>各向均匀</b>地散开，否则看起来像子弹撞在墙上炸了。</li>
     *   <li>火花没有环，激光有一圈很薄的环：能量在命中点沿表面扩散，
     *       环是唯一能把「光的扩散」和「碎片的飞散」区分开的形状。</li>
     *   <li>火花线条粗（0.5）而短，激光线条细（0.45）而长，末端几乎看不见，
     *       像被拉出去的光丝而不是金属屑。</li>
     * </ul>
     *
     * <p>打友军时这个特效是「能量注入」，打敌人时是「电击」——同一套形状，
     * 因为两者都是「一束能量到达了一个表面」，差别只在伤害方向。
     * 加环之后它才不像「打爆」，所以治疗武器反而更需要这个环。
     */
    public static class LaserHitEffect extends Effect{
        /** 光斑半径，0.3 格 */
        public float size = 2.4f;
        /** 能量环最大半径，0.88 格 */
        public float ring = 7f;
        /** 放射光丝条数。速射武器别超过 6，每秒八发时会糊成一片 */
        public int sparks = 5;
        /** 主色。null 用 {@link LaserFx#GLOW} */
        public Color tint;

        public LaserHitEffect(){
            this(1f);
        }

        public LaserHitEffect(float scale){
            size *= scale;
            ring *= scale;
            lifetime = 9f;
            clip = (ring + size * 3f) * 2f + 40f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            float fade = 1f - p;
            float out = Interp.pow2Out.apply(p);
            Color glowColor = pick(e, tint);

            // ① 白芯：普通混合的实心圆，和枪口脉冲同一个道理——叠加混合在中亮地形上
            //    等于没画，命中点必须先有一个烧穿的白点，辉光才有地方可以洇
            Draw.blend(Blending.normal);
            Draw.color(CORE);
            Draw.alpha(fade);
            Fill.circle(e.x, e.y, size * 1.15f * (1f - 0.45f * p));

            Draw.blend(Blending.additive);

            // ② 辉光：径向渐变，同时收半径和透明度。收半径是为了让亮点「聚」而不是「胀」；
            //    外半径给到 3.4 倍时整颗光斑会糊成一个白盘，把后面的环和光丝全吃掉
            Tmp.c1.set(CORE).lerp(glowColor, p).a(fade * 0.55f);
            Tmp.c2.set(glowColor).a(0f);
            Fill.light(e.x, e.y, 24, size * 2.4f * (1f - 0.4f * p), Tmp.c1, Tmp.c2);

            // ③ 光圈：和枪口同一个道理，普通混合、白→主色、alpha 恒 1。
            //    这层是原版 Fx.hitLaser 的全部内容，也是命中读起来「硬」的唯一来源
            Draw.blend(Blending.normal);
            Draw.color(CORE, glowColor, p);
            Draw.alpha(1f);
            Lines.stroke(0.5f + 1.1f * fade);
            Lines.circle(e.x, e.y, ring * out);

            Draw.blend(Blending.additive);

            // ④ 放射光丝：以弹道为轴均分，只抖角度不抖方向——
            //    纯随机时五条可能挤在同一侧，整簇会歪，看着像命中点没对上
            rand.setSeed(e.id);
            Draw.color(CORE, BEAM, Mathf.clamp(p * 1.6f));
            Draw.alpha(fade * 0.7f);
            for(int i = 0; i < sparks; i++){
                float a = e.rotation + 360f * i / sparks + rand.random(-20f, 20f);
                // 长度差异要给足：等长均分时五条光丝加上外圈会读成风车，
                // 长短不齐才像「溅出去的光」
                float l = size * (0.7f + 1.3f * out) * rand.random(0.5f, 1.25f);
                // 起点离中心远一点：从圆心放射时五条线会在中间交叉，
                // 加上环之后整颗特效读起来像风扇叶，而不是「光溅出去」
                float r0 = size * 0.55f;
                Lines.stroke(0.24f * fade * rand.random(0.7f, 1.1f));
                Lines.line(e.x + Mathf.cosDeg(a) * r0, e.y + Mathf.sinDeg(a) * r0,
                    e.x + Mathf.cosDeg(a) * l, e.y + Mathf.sinDeg(a) * l);
            }

            Draw.blend();
        }
    }

    // ============================================================
    //  静态单例与工厂
    // ============================================================
    /** {@code hitSize 8} 级别的激光武器直接用这个 */
    public static final Effect MUZZLE = new LaserMuzzleEffect(1f);
    /** 配合 {@link #MUZZLE} 的命中光斑 */
    public static final Effect HIT = new LaserHitEffect(1f);

    /** 换色版枪口脉冲。只换主色，白芯和深绿边不动——激光的冷色底一变就不像激光了 */
    public static Effect muzzle(float scale, Color color){
        LaserMuzzleEffect e = new LaserMuzzleEffect(scale);
        e.tint = color;
        return e;
    }

    /** 换色版命中光斑 */
    public static Effect hit(float scale, Color color){
        LaserHitEffect e = new LaserHitEffect(scale);
        e.tint = color;
        return e;
    }

    /** 按尺寸取枪口脉冲。同一 scale 每次调用都会新建，别在 update 里反复调 */
    public static Effect muzzle(float scale){
        return new LaserMuzzleEffect(scale);
    }

    /** 按尺寸取命中光斑 */
    public static Effect hit(float scale){
        return new LaserHitEffect(scale);
    }

    /**
     * 一次给一颗激光子弹接上发射脉冲与命中光斑。
     *
     * <p>顺带把 {@code smokeEffect} 关掉：{@code LaserBoltBulletType} 在构造里把它设成了
     * {@code Fx.hitLaser}，于是每次开火都会在枪口播一遍「命中」特效——
     * 这就是原版激光看起来枪口乱的原因。激光没有烟，这项本来就该是 {@code Fx.none}。
     *
     * @param scale 口径系数，{@code hitSize 8} 用 1，更大的激光炮按比例加
     */
    public static void attach(BulletType bullet, float scale){
        bullet.shootEffect = muzzle(scale);
        bullet.smokeEffect = Fx.none;
        bullet.hitEffect = hit(scale);
        bullet.despawnEffect = hit(scale);
    }
}
