package project.graphics;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;

/**
 * 跃迁拖尾粒子：沿单位朝向向前铺开一束长短不一的短线，
 * 位置向两侧按随机偏移散开，看起来就是「一道被撕开的入口」。
 *
 * 参考 Vanilla Expansion 的同名特效（作者 火星人076 & 128_OTEML，
 * 该项目公开源码供学习）。算法照原样保留，仅改配色与注释：
 * 原版是青绿 00ffce，这里换成星辉的橙金 ffa665，跟机体辉光同一色系。
 */
public class WarpParticleEffect extends Effect{

    public WarpParticleEffect(){
        this(1f);
    }

    /**
     * @param scale 通道宽度倍率。小飞机钻过去只要一条缝，
     *              日蚀这种 320px 的机体整台搬走，缝得开到机身那么宽才说得通。
     *              线宽只跟着放大一点点：通道再宽，那些短线本身还是「细裂口」，
     *              按宽度同比例放大只会变成四根粗柱子。
     */
    public WarpParticleEffect(float scale){
        // 这个类现在做成静态单例，长度、粒子数这类每发不同的参数
        // 由 at(x, y, rot, color, data) 的 data 传进来（见 WarpEffects.data）。
        // 原来是把参数写进匿名子类的实例字段，代价是每次跃迁 new 一个 Effect，
        // 而 Effect 的构造器会把自己永久塞进 Effect.all——等于每跳一次漏一个对象。
        this.width = 8f * scale;
        this.stroke = 2f * Mathf.clamp(scale * 0.4f, 1f, 2.5f);
        lifetime = 60f;
        clip = 2000f;
    }

    private static final Rand rand = new Rand();
    private static final Vec2 rv = new Vec2();

    public float width = 8f;
    public float generalLength = 720f;
    public float stroke = 2f;
    public float lengthFrom = 20f;
    public float lengthTo = 5f;
    public Color colorFrom = Color.valueOf("ffd9a0").mul(1.2f);
    public Color colorTo = Color.valueOf("ffa665");
    public Interp interp = Interp.circleOut;
    public Interp sizeInterp = Interp.pow2In;
    public Interp colorInterp = Interp.linear;
    public float cone = 0;
    /** 每跨越这么多像素铺一个粒子 */
    public float particlePerPixel = 14.4f;

    @Override
    public void render(EffectContainer e){
        float glen = WarpEffects.data(e, generalLength);
        if(glen <= 0.01f) return;

        int count = Mathf.clamp(Mathf.ceil(glen / particlePerPixel), 1, 200);
        Color to = e.color == null ? colorTo : e.color;

        float rawfin = e.fin();
        float fin = e.fin(interp);
        float colFin = e.fin(colorInterp);
        Draw.color(colorFrom, to, colFin);
        Lines.stroke(sizeInterp.apply(stroke, 0, rawfin));
        float len = sizeInterp.apply(lengthFrom, lengthTo, rawfin);

        // 用特效 id 做种子，保证同一发跃迁每次重绘的粒子分布完全一致，不会闪
        rand.setSeed(e.id);
        for(int i = 0; i < count; i++){
            float l = glen * fin;
            float c = rand.random(-1f, 1f);
            float lm = rand.random(0.5f, 2f);
            rv.trns(e.rotation + rand.range(cone), rand.random(l) * Mathf.pow(Mathf.clamp(1f - Math.abs(c)), 0.5f));
            float x = rv.x + width * 0.5f * c * Mathf.cosDeg(e.rotation - 90f), y = rv.y + width * 0.5f * c * Mathf.sinDeg(e.rotation - 90f);

            Lines.lineAngle(e.x + x, e.y + y, e.rotation, Mathf.clamp(e.fout() - (1 - lm)) * len * Mathf.pow(Mathf.clamp(1.25f - Math.abs(c)), 0.5f), 0);
        }

        // 光照整条通道只画一次。原来每个粒子各画一个 additive 光斑，
        // 一次 1200px 的跃迁就是八十多个大 quad 叠加，是这里最贵的一笔开销，
        // 而屏幕上根本分辨不出它们的区别。
        Drawf.light(e.x, e.y, Math.min(120f, glen * 0.3f) * e.fout(), Draw.getColor(), 0.5f);
    }
}
