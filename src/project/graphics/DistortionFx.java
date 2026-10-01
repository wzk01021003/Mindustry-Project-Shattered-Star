package project.graphics;

import arc.graphics.Color;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Draw.reset;
import static arc.graphics.g2d.Lines.stroke;

/**
 * 屏幕叠加式"冲击波"效果。
 * 不依赖 FBO / shader，直接在场景上叠加波纹、光晕、放射线，性能开销极低。
 * 用法与原版 Effect 完全一致：
 *   bullet.hitEffect = DistortionFx.largeIonImpact;
 */
public class DistortionFx extends Effect {

    public final float radius;
    public final float strength;
    public final float life;

    public DistortionFx(float radius, float strength, float life) {
        super(life, e -> {
            float fin = e.fin();
            float fout = e.fout();

            Color base = Pal.heal;
            Color edge = Color.white;

            // 1. 中心光晕 —— 快速扩散淡出
            color(base, edge, fin);
            Fill.circle(e.x, e.y, radius * 0.18f * fout);

            // 2. 主波纹 —— 外环，从中心扩散
            color(edge, base, fin);
            stroke(strength * 4.5f * fout);
            Lines.circle(e.x, e.y, radius * fin);

            // 3. 副波纹 —— 70% 半径，稍慢
            stroke(strength * 2.2f * fout);
            Lines.circle(e.x, e.y, radius * 0.7f * fin);

            // 4. 放射线 —— 向外扩散的短线
            stroke(strength * 1.4f * fout);
            int spokes = 12;
            float baseAngle = (e.id * 37f) % 360f;
            for (int i = 0; i < spokes; i++) {
                float a = baseAngle + i * (360f / spokes);
                float r0 = radius * 0.45f * fin;
                Tmp.v1.trns(a, r0);
                float len = radius * 0.35f * fout;
                Lines.lineAngle(e.x + Tmp.v1.x, e.y + Tmp.v1.y, a, len);
            }

            // 5. 光晕
            Drawf.light(e.x, e.y, radius * 1.3f * fout, base, fout * 0.7f);

            reset();
        });
        this.radius = radius;
        this.strength = strength;
        this.life = life;
    }

    // ============================================================
    //  预设（可以按需增加）
    // ============================================================

    /** 小型离子冲击 */
    public static final DistortionFx smallIonImpact   = new DistortionFx(60f,  0.6f, 20f);
    /** 大型离子冲击 */
    public static final DistortionFx largeIonImpact   = new DistortionFx(120f, 1.2f, 35f);
    /** 巨大爆炸 */
    public static final DistortionFx hugeExplosion    = new DistortionFx(200f, 2.0f, 50f);
    /** 微弱持续（比如激光炮塔射击时）*/
    public static final DistortionFx subtleContinuous = new DistortionFx(40f,  0.3f, 10f);
}