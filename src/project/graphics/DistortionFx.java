package project.graphics;

import arc.math.Interp;
import mindustry.entities.Effect;

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
    public final float radiusFrom;
    public final float radiusTo;
    public final float ringWidth;   // 0=实心圆，>0=环，单位=相对 radius 的厚度
    public final Interp interp;

    // ============================================================
    //  构造：逐层递进
    // ============================================================

    public DistortionFx(float radius, float strength, float life) {
        this(radius, strength, life, TYPE_OUTWARD);
    }

    public DistortionFx(float radius, float strength, float life, int type) {
        this(radius, strength, life, type, 0f, 1f, Interp.pow2Out);
    }

    public DistortionFx(float radius, float strength, float life, int type,
                        float radiusFrom, float radiusTo, Interp interp) {
        this(radius, strength, life, type, radiusFrom, radiusTo, 0f, interp);
    }

    // 完整构造：包含环厚度
    public DistortionFx(float radius, float strength, float life, int type,
                        float radiusFrom, float radiusTo, float ringWidth, Interp interp) {
        super(life, e -> DistortionRenderer.addDistortion(
            e.x, e.y, radius, strength, life, type,
            radiusFrom, radiusTo, ringWidth, interp));
        this.radius = radius;
        this.strength = strength;
        this.life = life;
        this.type = type;
        this.radiusFrom = radiusFrom;
        this.radiusTo = radiusTo;
        this.ringWidth = ringWidth;
        this.interp = interp;
    }

    // ============================================================
    //  预设
    // ============================================================

    // ---- 实心圆 ----
    public static final DistortionFx smallIonImpact =
        new DistortionFx(60f, 0.6f, 20f, TYPE_OUTWARD, 0f, 1f, 0f, Interp.pow2Out);
    public static final DistortionFx largeIonImpact =
        new DistortionFx(120f, 1.2f, 35f, TYPE_OUTWARD, 0f, 1f, 0f, Interp.pow2Out);
    public static final DistortionFx implosion =
        new DistortionFx(150f, 1.5f, 40f, TYPE_INWARD, 1f, 0f, 0f, Interp.pow2Out);
    public static final DistortionFx vortexBlast =
        new DistortionFx(140f, 1.4f, 45f, TYPE_INWARD_BOUNCE, 1f, 0f, 0f, Interp.pow2In);

    // ---- 环状扩散（水波）----
    /** 标准水波：环从中心向外扩散，先快后慢，环厚 0.25 */
    public static final DistortionFx ripple =
        new DistortionFx(200f, 1.5f, 50f, TYPE_OUTWARD, 0f, 1f, 0.25f, Interp.pow2Out);

    /** 薄环冲击波：环极薄，外扩速度带加速度 */
    public static final DistortionFx shockwaveRing =
        new DistortionFx(220f, 2.0f, 45f, TYPE_OUTWARD, 0f, 1f, 0.12f, Interp.pow2In);

    /** 内收环：环从外围向内收 */
    public static final DistortionFx inwardRing =
        new DistortionFx(180f, 1.6f, 50f, TYPE_INWARD, 1f, 0f, 0.25f, Interp.pow2In);

    /** 慢水波：持续久的环，慢慢扩散 */
    public static final DistortionFx slowRipple =
        new DistortionFx(260f, 1.0f, 90f, TYPE_OUTWARD, 0f, 1f, 0.3f, Interp.linear);

    /** 厚环爆炸：环很厚，看起来像气球膨胀 */
    public static final DistortionFx balloon =
        new DistortionFx(180f, 1.8f, 45f, TYPE_OUTWARD, 0f, 1f, 0.8f, Interp.pow2Out);

    // ---- 其他 ----
    public static final DistortionFx hugeExplosion =
        new DistortionFx(200f, 2.0f, 50f, TYPE_OUTWARD_BOUNCE, 0f, 1f, 0f, Interp.pow2Out);
    public static final DistortionFx delayedBlast =
        new DistortionFx(180f, 2.0f, 60f, TYPE_DELAYED, 0f, 1f, 0f, Interp.pow2Out);
    public static final DistortionFx subtleContinuous =
        new DistortionFx(40f, 0.3f, 10f, TYPE_INWARD, 1f, 0f, 0f, Interp.linear);
}