package project.graphics;

import arc.graphics.Color;
import arc.math.Interp;
import mindustry.entities.Effect;
import mindustry.entities.effect.MultiEffect;
import mindustry.entities.effect.RadialEffect;
import mindustry.entities.effect.TriangleEffect;
import mindustry.entities.effect.WaveEffect;

/**
 * 跃迁特效的「几何层」：多边形能量环。
 *
 * <p>VE 的跃迁特效写在 JSON 里，形如：
 * <pre>{@code
 * { "type": "WaveEffect", "sides": 24, "sizeFrom": 0, "sizeTo": 40,
 *   "strokeFrom": 2, "strokeTo": 0, "interp": "circleOut", "lifetime": 20 }
 * }</pre>
 * 这是 Mindustry v8 给 Effect 加的内联定义能力（{@code mindustry.entities.effect} 包），
 * JSON 只是它的序列化形式。这里把同一套写法直接落在 Java 里：
 * 少一层 JSON 解析，参数还能在构造时按单位算。
 *
 * <p>为什么值得抄：{@link WarpEffects} 里手写的「环」是 {@code Lines.circle}，只能是圆；
 * 而 {@code WaveEffect} 靠 {@code sides} 一个参数就能把环换成六边形、二十四边形，
 * 半径和描边宽度各自插值，还能用 {@code startDelay} 让几层环错峰炸开，
 * 层次比单圆丰富得多，代价只是多几个 draw call。
 *
 * <p>颜色写死在 {@link #CORE} / {@link #MAIN}：{@code WaveEffect} 读的是自身
 * {@code colorFrom/colorTo} 字段，不吃 {@code at(..., color)} 传进来的颜色。
 * 星辉只有一种配色，够用；真要按单位换色，把下面几个常量换成工厂参数即可。
 */
public class WarpShapes{

    /** 白热核心 */
    public static final Color CORE = Color.valueOf("fff4dc");
    /** 主色，与机体辉光同色系 */
    public static final Color MAIN = Color.valueOf("ffa665");

    /**
     * 蓄力：一圈贴着机体淡出的二十四边形。
     *
     * <p>照抄 VE 的 omicron：{@code size} 全程恒定（那边是 13，这里按星辉机身放大到 22）、
     * 只有 {@code stroke} 从 1.1 掉到 0，
     * 相当于「一层能量膜贴在机身上被抽干」，而不是环在缩放。
     * 这比单纯画个呼吸圆更耐看，因为它始终贴着机体，玩家能看出是这台机器在聚能。
     *
     * <p>层数刻意压到两层（另一层是手写的向心环）：蓄力特效每 8 帧就生成一个，
     * 同屏本来就会叠 2~3 个，再往上加只会糊成一坨。
     */
    public static final Effect CHARGE = new MultiEffect(
        new WarpEffects.WarpChargeEffect(),
        wave(24, 22f, 22f, 1.1f, 0f, 20f, 0f, Interp.linear, tint(CORE, 0.95f), tint(MAIN, 0f), 0f)
    );

    /**
     * 起跳：主环 + 光刃。
     *
     * <p>主环的参数（48 边、半径 16→80、描边 1.25→3.5、linear）来自 VE 的 omicron，
     * 起点 12.5 按星辉机身放大到 16：
     * 环在扩大的同时描边反而变粗，再整体淡出，像一层能量膜被从机体上扯开。
     * 我原来写的是描边越扩越细，看起来更像水波，不如这个有「撕开」的劲儿。
     *
     * <p>几何层只补两样：这圈膜，和六片方向感明确的刀光。
     * 圆环、尖刺、白闪交给 {@link WarpEffects.WarpBurstEffect}，
     * 两边不重复画环，否则三圈同心环会互相糊掉。
     */
    public static final Effect BURST = new MultiEffect(
        new WarpEffects.WarpBurstEffect(),
        wave(48, 16f, 80f, 1.25f, 3.5f, 15f, 0f, Interp.linear, tint(MAIN, 1f), tint(MAIN, 0f), 0f),
        // 余波：主膜 15 帧就没了，留一圈更慢更细的环继续往外走，收尾不至于突然空掉
        wave(0, 8f, 108f, 1.8f, 0f, 34f, 8f, Interp.pow2Out, tint(MAIN, 0.45f), tint(MAIN, 0f), 0f),
        blades()
    );

    /**
     * 落点：一圈迟到的能量膜。
     *
     * <p>参数和起跳那圈相同，只是晚 4 帧才出现、颜色从核心白起算 ——
     * 机体已经落位了膜才被「踩」出来，正好对应踩地的节奏。
     * 光柱、收束环、落地火花都在手写层里，这里不再叠第二圈环。
     */
    public static final Effect ARRIVE = new MultiEffect(
        new WarpEffects.WarpArriveEffect(),
        wave(48, 16f, 80f, 1.25f, 3.5f, 15f, 4f, Interp.linear, tint(CORE, 0.9f), tint(MAIN, 0f), 0f)
    );

    /**
     * 造一圈多边形描边环。
     *
     * @param sides   边数，0 表示自动（正圆）
     * @param sizeFrom/sizeTo     外接圆半径的起止值
     * @param strokeFrom/strokeTo 描边宽度的起止值
     * @param life    生命周期（帧）
     * @param delay   延迟多少帧才生成，用来让几层环错峰
     * @param interp  半径与描边共用的插值曲线
     * @param from/to 颜色，alpha 参与插值，所以 to 给全透明就是淡出
     * @param extraRot 额外旋转角，六边形错开 30° 能拼出十二边形的观感
     */
    private static WaveEffect wave(int sides, float sizeFrom, float sizeTo, float strokeFrom, float strokeTo,
                                   float life, float delay, Interp interp, Color from, Color to, float extraRot){
        WaveEffect w = new WaveEffect();
        w.sides = sides;
        w.sizeFrom = sizeFrom;
        w.sizeTo = sizeTo;
        w.strokeFrom = strokeFrom;
        w.strokeTo = strokeTo;
        w.lifetime = life;
        w.startDelay = delay;
        w.interp = interp;
        w.colorFrom = from;
        w.colorTo = to;
        w.rotation = extraRot;

        // 关掉内置光斑：一次跃迁已经有五六个 additive quad，再加就纯属浪费填充率，
        // 外面的手写层已经统一打过一次 light。
        w.lightScl = 0f;
        w.lightOpacity = 0f;

        // clip 决定这发特效在创建时会不会被视锥剔除（Effect.create 里用它做矩形裁剪）。
        // 基类默认 0，不改的话环永远不渲染；init() 里只会取 max，不会把 0 补上。
        w.clip = Math.max(sizeFrom, sizeTo) + Math.max(strokeFrom, strokeTo) + 24f;
        w.init();
        return w;
    }

    /** 取一个带透明度的副本，直接改原常量会污染别的环 */
    private static Color tint(Color c, float alpha){
        return c.cpy().a(alpha);
    }

    /**
     * 六片三角光刃，沿 60° 均匀分布在机体四周向外甩。
     *
     * <p>{@code TriangleEffect} 的几何是：一个按 {@code interp} 从
     * ({@code startX},{@code startY}) 平移到 ({@code endX},{@code endY}) 的点，
     * 在该点画一个朝向为该点角度、底边 {@code width}、高 {@code height} 的三角形。
     * 所以起点贴中心、终点甩到 52px 外，同时把高从 38 收到 8、底边从 2.5 放到 11，
     * 就能得到「又细又长 → 又短又宽」的刀光。
     *
     * <p>{@code RadialEffect} 只负责把同一份效果按角度复制 N 份，
     * 自身不渲染，所以 clip 无所谓。
     */
    private static RadialEffect blades(){
        TriangleEffect blade = new TriangleEffect();
        blade.useRotation = true;
        blade.interp = Interp.pow2Out;
        blade.startX = 4f;
        blade.startY = 0f;
        blade.endX = 52f;
        blade.endY = 0f;
        blade.widthFrom = 2.5f;
        blade.widthTo = 11f;
        blade.heightFrom = 38f;
        blade.heightTo = 8f;
        blade.colorFrom = tint(CORE, 0.85f);
        blade.colorTo = tint(MAIN, 0f);
        // 关掉自带光照：六片刀光各打一个光斑比环还贵，观感上又看不出
        blade.lightScl = 0f;
        blade.lightOpacityFrom = 0f;
        blade.lightOpacityTo = 0f;
        blade.lifetime = 18f;
        blade.clip = 140f;
        blade.init();

        RadialEffect r = new RadialEffect();
        r.effect = blade;
        r.amount = 6;
        r.rotationSpacing = 60f;
        r.rotationOffset = 0f;
        r.effectRotationOffset = 0f;
        r.lengthOffset = 0f;
        return r;
    }
}
