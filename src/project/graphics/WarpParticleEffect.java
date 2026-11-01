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
        // 注意：这个构造器跑在字段初始化之前，读到的 generalLength 恒为下方默认值，
        // clip 因此固定为 1440。原文如此，对 800px 以内的跃迁足够，保留不改。
        clip = generalLength * 2f;
    }

    private static final Rand rand = new Rand();
    private static final Vec2 rv = new Vec2();

    public float width = 24f;
    public float generalLength = 720f;
    public float stroke = 1f;
    public float lengthFrom = 20f;
    public float lengthTo = 5f;
    public int particles = 40;
    public Color colorFrom = Color.valueOf("ffd9a0").mul(1.2f);
    public Color colorTo = Color.valueOf("ffa665");
    public Interp interp = Interp.circleOut;
    public Interp sizeInterp = Interp.pow2In;
    public Interp colorInterp = Interp.linear;
    public float cone = 0;

    @Override
    public void render(EffectContainer e){
        float rawfin = e.fin();
        float fin = e.fin(interp);
        float colFin = e.fin(colorInterp);
        Draw.color(colorFrom, colorTo, colFin);
        Lines.stroke(sizeInterp.apply(stroke, 0, rawfin));
        float len = sizeInterp.apply(lengthFrom, lengthTo, rawfin);

        // 用特效 id 做种子，保证同一发跃迁每次重绘的粒子分布完全一致，不会闪
        rand.setSeed(e.id);
        for(int i = 0; i < particles; i++){
            float l = generalLength * fin;
            float c = rand.random(-1f, 1f);
            float lm = rand.random(0.5f, 2f);
            rv.trns(e.rotation + rand.range(cone), rand.random(l) * Mathf.pow(Mathf.clamp(1f - Math.abs(c)), 0.5f));
            float x = rv.x + width * 0.5f * c * Mathf.cosDeg(e.rotation - 90f), y = rv.y + width * 0.5f * c * Mathf.sinDeg(e.rotation - 90f);

            Lines.lineAngle(e.x + x, e.y + y, e.rotation, Mathf.clamp(e.fout() - (1 - lm)) * len * Mathf.pow(Mathf.clamp(1.25f - Math.abs(c)), 0.5f), 0);
            Drawf.light(e.x + x, e.y + y, len, Draw.getColor(), 1f);
        }
    }
}
