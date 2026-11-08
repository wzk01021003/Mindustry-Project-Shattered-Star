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
     * 激光枪口脉冲。一条细锥 + 一颗枪口光球 + 一道横向能量闪，6 帧内收完。
     *
     * <p>横向那道闪是这套特效里唯一「非物理」的东西：它垂直于弹道，在真实的激光里
     * 不存在。但它能把枪口从「一个点」变成「一次放电」，速射时尤其明显——
     * 没有它，每秒八发的武器看起来只是在枪口反复擦亮一颗小灯。
     */
    public static class LaserMuzzleEffect extends Effect{
        /** 光束长度，2 格。速射武器别超过 20：每秒八发时相邻两发会连成一条常亮的线 */
        public float len = 16f;
        /** 光束半宽。1.1 时宽高比约 1:7，再宽就成火焰了 */
        public float width = 1.1f;
        /** 场景光半径 */
        public float glow = 14f;
        /** 场景光不透明度 */
        public float glowOpacity = 0.5f;
        /** 横向能量闪的半长，相对宽度 */
        public float crossScale = 1.6f;
        /** 主色。null 用 {@link LaserFx#GLOW}，换武器时在工厂里设 */
        public Color tint;

        public LaserMuzzleEffect(){
            this(1f);
        }

        public LaserMuzzleEffect(float scale){
            len *= scale;
            width *= scale;
            glow *= scale;
            lifetime = 6f;
            clip = (len + glow) * 2f + 40f;
        }

        @Override
        public void render(EffectContainer e){
            float p = e.fin();
            float fout = e.fout();
            float rot = e.rotation;
            Color glowColor = pick(e, tint);

            Drawf.light(e.x, e.y, glow * (0.7f + 0.3f * fout), glowColor, glowOpacity * fout);

            Draw.blend(Blending.additive);

            // 三层同轴的「线段」而不是三角。三角的底边是一条垂直弹道的直线，
            // 放大后读起来像「旗子挂在杆子上」——激光没有那根杆。
            // 线段是等宽的，才是准直光束该有的样子
            float tipx = e.x + Mathf.cosDeg(rot) * len * fout;
            float tipy = e.y + Mathf.sinDeg(rot) * len * fout;

            // ① 外辉光：比主体宽一倍、极淡，负责边缘过曝
            Draw.color(glowColor, DEEP, p);
            Draw.alpha(0.20f * fout);
            Lines.stroke(width * 3.2f);
            Lines.line(e.x, e.y, tipx, tipy);

            // ② 光束主体
            Draw.color(BEAM, DEEP, p);
            Draw.alpha(0.7f * fout);
            Lines.stroke(width * 1.7f);
            Lines.line(e.x, e.y, tipx, tipy);

            // ③ 白热芯
            Draw.color(CORE);
            Draw.alpha(fout);
            Lines.stroke(width * 0.75f);
            Lines.line(e.x, e.y, tipx, tipy);

            // ④ 枪口光球：径向渐变，把三条锥的根部糊成一个亮点
            Tmp.c1.set(CORE).a(0.5f * fout);
            Tmp.c2.set(glowColor).a(0f);
            Fill.light(e.x, e.y, 20, width * 2.1f * (0.55f + 0.45f * fout), Tmp.c1, Tmp.c2);

            // ⑤ 横向能量闪：垂直弹道的一道短线，负责「放电」那一下
            float nx = Mathf.cosDeg(rot + 90f), ny = Mathf.sinDeg(rot + 90f);
            float cl = width * crossScale * (0.4f + 0.6f * fout);
            Draw.color(BEAM, glowColor, p);
            Draw.alpha(0.55f * fout);
            Lines.stroke(width * 0.38f * fout);
            Lines.line(e.x - nx * cl, e.y - ny * cl, e.x + nx * cl, e.y + ny * cl);

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

            Draw.blend(Blending.additive);

            // ① 光斑：径向渐变，同时收半径和透明度。收半径是为了让亮点「聚」而不是「胀」；
            //    外半径给到 3.4 倍时整颗光斑会糊成一个白盘，把后面的环和光丝全吃掉
            Tmp.c1.set(CORE).a(fade * 0.7f);
            Tmp.c2.set(glowColor).a(0f);
            Fill.light(e.x, e.y, 24, size * 2.2f * (1f - 0.4f * p), Tmp.c1, Tmp.c2);

            // ② 能量环：外扩 + 线宽同步变细，读作「沿表面扩散的一圈光」。
            //    起始半径就给 35%：从 0 长出来的环在前两帧只有几个像素，
            //    套在白斑上像一枚小铜环，不像扩散
            Draw.color(BEAM, DEEP, p);
            Draw.alpha(fade * 0.42f);
            Lines.stroke(0.6f * fade);
            Lines.circle(e.x, e.y, ring * (0.35f + 0.65f * out));

            // ③ 放射光丝：以弹道为轴均分，只抖角度不抖方向——
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
