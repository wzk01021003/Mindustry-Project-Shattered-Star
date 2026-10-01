package project.graphics;

import mindustry.entities.Effect;

/**
 * 屏幕扭曲效果的触发器。
 * 它的唯一职责：命中时把坐标/半径/强度交给 DistortionRenderer。
 * 真正画扭曲的是 DistortionRenderer 里的 shader。
 */
public class DistortionFx extends Effect {

    public final float radius;
    public final float strength;
    public final float life;

    public DistortionFx(float radius, float strength, float life) {
        super(life, e -> DistortionRenderer.addDistortion(e.x, e.y, radius, strength, life));
        this.radius = radius;
        this.strength = strength;
        this.life = life;
    }

    // 预设
    public static final DistortionFx smallIonImpact   = new DistortionFx(60f,  0.6f, 20f);
    public static final DistortionFx largeIonImpact   = new DistortionFx(120f, 1.2f, 35f);
    public static final DistortionFx hugeExplosion    = new DistortionFx(200f, 2.0f, 50f);
    public static final DistortionFx subtleContinuous = new DistortionFx(40f,  0.3f, 10f);
}