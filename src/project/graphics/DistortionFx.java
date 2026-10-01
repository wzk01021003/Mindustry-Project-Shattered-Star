package project.graphics;

import mindustry.entities.Effect;

/**
 * 屏幕扭曲的触发器。命中时把参数交给 DistortionRenderer。
 *
 * 支持 5 种类型：
 *   TYPE_INWARD          内凹（吸入）
 *   TYPE_OUTWARD         外凸（喷发）
 *   TYPE_INWARD_BOUNCE   先凹后凸（吸→弹）
 *   TYPE_OUTWARD_BOUNCE  先凸后凹（喷→收）
 *   TYPE_DELAYED         延迟外凸（前 30% 时间平静，然后喷发）
 */
public class DistortionFx extends Effect {

    public static final int TYPE_INWARD         = 0;
    public static final int TYPE_OUTWARD        = 1;
    public static final int TYPE_INWARD_BOUNCE  = 2;
    public static final int TYPE_OUTWARD_BOUNCE = 3;
    public static final int TYPE_DELAYED        = 4;

    public final float radius;
    public final float strength;
    public final float life;
    public final int type;

    public DistortionFx(float radius, float strength, float life) {
        this(radius, strength, life, TYPE_OUTWARD);
    }

    public DistortionFx(float radius, float strength, float life, int type) {
        super(life, e -> DistortionRenderer.addDistortion(
            e.x, e.y, radius, strength, life, type));
        this.radius = radius;
        this.strength = strength;
        this.life = life;
        this.type = type;
    }

    // ============================================================
    //  预设
    // ============================================================

    // 爆炸冲击（外凸）
    public static final DistortionFx smallIonImpact =
        new DistortionFx(60f,  0.6f, 20f, TYPE_OUTWARD);
    public static final DistortionFx largeIonImpact =
        new DistortionFx(120f, 1.2f, 35f, TYPE_OUTWARD);

    // 大爆炸（先喷后收，有"收缩"的余韵）
    public static final DistortionFx hugeExplosion =
        new DistortionFx(200f, 2.0f, 50f, TYPE_OUTWARD_BOUNCE);

    // 吸入（内凹）
    public static final DistortionFx implosion =
        new DistortionFx(150f, 1.5f, 40f, TYPE_INWARD);

    // 吸→弹（内凹先，然后外凸）
    public static final DistortionFx vortexBlast =
        new DistortionFx(140f, 1.4f, 45f, TYPE_INWARD_BOUNCE);

    // 延迟爆发
    public static final DistortionFx delayedBlast =
        new DistortionFx(180f, 2.0f, 60f, TYPE_DELAYED);

    // 微弱持续
    public static final DistortionFx subtleContinuous =
        new DistortionFx(40f,  0.3f, 10f, TYPE_INWARD);
}