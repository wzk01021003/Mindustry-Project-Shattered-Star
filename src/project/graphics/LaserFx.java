package project.graphics;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
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
 * <p>所以枪口走埃里克尔那一套，而不是自绘一条光束：
 * <ul>
 *   <li><b>方向</b>：一束沿弹道散出的<b>电弧</b>，照 v160 的 {@code Fx.sparkShoot}——
 *       七条 5.5px 的短线，不是一条贯穿画面的直条。长条既不像「射出去」，也不像原版。</li>
 *   <li><b>放电</b>：一圈实色外扩光环，照 {@code obviate} 用的 {@code WaveEffect}，
 *       配环心一点过曝。环负责「打出去了」，电弧负责朝向。</li>
 *   <li><b>时间</b>：5 帧。激光的「发射」是一个瞬间，不像枪口焰那样有 8 帧余韵；
 *       而 nova 的 reload 只有 7 帧，特效再长就会首尾相接连成常亮。</li>
 * </ul>
 *
 * <p>颜色默认取异构新星的机体绿（{@code 84f491}）。换武器时用 {@link #muzzle(float, Color)}
 * 传主色即可。
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
     * 激光枪口脉冲。一圈外扩的光环 + 一束沿弹道喷出去的电弧 + 一颗压心光球，5 帧收完。
     *
     * <p>形态直接照埃里克尔单位。v160 里 {@code elude} / {@code avert} / {@code disrupt}
     * 这些能量武器的枪口全是同一个 {@code Fx.sparkShoot}：
     *
     * <pre>
     * color(Color.white, e.color, e.fin());
     * stroke(e.fout() * 1.2f + 0.6f);
     * randLenVectors(e.id, 7, 25f * e.finpow(), e.rotation, 3f, (x, y) -&gt; {
     *     lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fslope() * 5f + 0.5f);
     * });
     * </pre>
     *
     * <p>七条<b>短线</b>沿弹道散出去，每条最长 5.5px——是一束「呲」出来的电弧，不是
     * 一根长棍。方向感由此而来，但不会留下任何贯穿画面的直条。大单位 {@code obviate}
     * 另外叠一圈 {@code WaveEffect}，这里把两样合在一起：环负责「放电」，电弧负责方向。
     *
     * <p>两层都走普通混合、alpha 恒 1，和原版一样。叠加混合只在中心那颗光球上用，
     * 负责过曝——在草地这种中亮地形上，走叠加的东西叠多少都看不出来。
     */
    public static class LaserMuzzleEffect extends Effect{
        /** 电弧散布半径，2.1 格。原版 {@code Fx.sparkShoot} 是 25（3 格），这里按机体略收 */
        public float reach = 20f;
        /** 电弧条数，原版 7 */
        public int sparks = 7;
        /** 电弧相对弹道的散布角，原版 3°。窄扇形让这几条线沿弹道排开，读成「喷出去」而不是「炸开」 */
        public float spread = 3f;
        /** 单根电弧的长度系数。原版 5（最长 5.5px），这里 6：短于 5px 在 1x 下会被采样吃掉 */
        public float sparkLen = 6f;
        /**
         * 光圈起始 / 最大半径。
         *
         * <p>照抄原版数值会出事：{@code obviate} 那圈 {@code WaveEffect} 写的是
         * {@code sizeTo = 26}，但它自身 {@code hitSize = 25}——摊到屏幕上直径一百来像素，
         * 环只占半个机身宽。同一个 26 挂到 {@code hitSize 8} 的小机体上，环就比机身还大，
         * 读起来像顶着个游泳圈，而不是枪口在放电。
         *
         * <p>所以按机身比例收：终半径约为机身宽度的一半，和 {@code obviate} 的观感对齐。
         * 起始值压到 1.2 是为了保住张开的幅度——2 → 8 的扩张比 1:6.7，能看出「炸开」；
         * 2 → 6 就只有 1:3，环会在原地抖一下。
         */
        public float ringFrom = 1.2f, ringTo = 8f;
        /** 光圈线宽，从 strokeFrom 收到 strokeTo。环收小后线宽也要跟着收，否则末帧是实心小圆 */
        public float strokeFrom = 1.8f, strokeTo = 0.15f;
        /** 场景光半径与不透明度 */
        public float glow = 14f, glowOpacity = 0.5f;
        /** 中心光球基准半径 */
        public float width = 1.6f;
        /** 主色。null 用 {@link LaserFx#GLOW}，换武器时在工厂里设 */
        public Color tint;

        public LaserMuzzleEffect(){
            this(1f);
        }

        public LaserMuzzleEffect(float scale){
            reach *= scale;
            sparkLen *= scale;
            ringFrom *= scale;
            ringTo *= scale;
            glow *= scale;
            width *= scale;
            // 5 帧。nova 的 reload 是 7 帧，12 帧的原版时长会首尾相接糊成常亮；
            // 留 2 帧空档，每次开火才有「一下一下」的节奏
            lifetime = 5f;
            clip = Math.max(reach, ringTo) * 2f + 40f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            float fout = e.fout();
            float hot = Mathf.clamp(1f - p / 0.5f);
            Color c = pick(e, tint);

            Drawf.light(e.x, e.y, glow * (0.8f + 0.2f * fout), c,
                glowOpacity * (0.5f + 0.5f * fout));

            // 两层都用普通混合：实色线条在任何地形上都是硬的，叠加混合只留给光球
            Draw.blend(Blending.normal);
            Draw.color(CORE, c, p);

            // ① 光环：{@code WaveEffect} 的形态（obviate 那圈），张开的同时线宽收细
            Lines.stroke(Mathf.lerp(strokeFrom, strokeTo, p));
            Lines.circle(e.x, e.y, Mathf.lerp(ringFrom, ringTo, p));

            // ② 电弧：原版 Fx.sparkShoot 的原样。每条线从枪口外一点起、朝外再画一小截，
            //    散布半径随 finpow 张到 reach——离开枪口时是一团，随后被「吹」成扇形
            // 线宽 1.6 → 0.5，和原版的 0.6 ~ 1.8 同一量级。再粗，第一帧七条线挤在
            // 枪口那一小块里会糊成一个白团——电弧的「细」正是它和光束的区别
            Lines.stroke(0.5f + 1.1f * fout);
            Angles.randLenVectors(e.id, sparks, reach * e.finpow(), e.rotation, spread, (x, y) -> {
                Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fslope() * sparkLen + 0.5f);
            });

            // ③ 压心光球：环心那点过曝。没有它，环 + 电弧会读成「一圈线加几根刺」
            Draw.blend(Blending.additive);
            Tmp.c1.set(CORE).a(0.22f + 0.28f * hot);
            Tmp.c2.set(c).a(0f);
            Fill.light(e.x, e.y, 18, width * 1.8f * (0.7f + 0.3f * hot), Tmp.c1, Tmp.c2);

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
