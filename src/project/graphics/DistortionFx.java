package project.graphics;

import arc.func.Cons;
import arc.math.Interp;
import arc.util.Time;
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
    public final float ringWidth;
    public final Interp interp;

    // ============================================================
    //  构造
    // ============================================================

    public DistortionFx(float radius, float strength, float life) {
        this(radius, strength, life, TYPE_OUTWARD, 0f, 1f, 0f, Interp.pow2Out);
    }

    public DistortionFx(float radius, float strength, float life, int type) {
        this(radius, strength, life, type, 0f, 1f, 0f, Interp.pow2Out);
    }

    public DistortionFx(float radius, float strength, float life, int type,
                        float radiusFrom, float radiusTo, Interp interp) {
        this(radius, strength, life, type, radiusFrom, radiusTo, 0f, interp);
    }

    public DistortionFx(float radius, float strength, float life, int type,
                        float radiusFrom, float radiusTo, float ringWidth, Interp interp) {
        super(life, new CooldownRenderer(radius, strength, life, type,
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
    //  内部类：同帧去重
    //  同一实例在 1 帧内只会触发一次，防止一发攻击炸出 N 个环。
    //  EffectContainer 不写 import，直接从父类 Effect 的构造签名继承。
    // ============================================================
    private static class CooldownRenderer implements Cons<EffectContainer> {
        final float radius, strength, life;
        final int type;
        final float radiusFrom, radiusTo, ringWidth;
        final Interp interp;
        long lastFrame = -1L;

        CooldownRenderer(float radius, float strength, float life, int type,
                         float radiusFrom, float radiusTo, float ringWidth,
                         Interp interp) {
            this.radius = radius;
            this.strength = strength;
            this.life = life;
            this.type = type;
            this.radiusFrom = radiusFrom;
            this.radiusTo = radiusTo;
            this.ringWidth = ringWidth;
            this.interp = interp;
        }

        @Override
        public void get(EffectContainer e) {
            long fid = arc.Core.graphics.getFrameId();
            if (fid == lastFrame) return;
            lastFrame = fid;
            DistortionRenderer.addDistortion(e.x, e.y, radius, strength, life, type,
                radiusFrom, radiusTo, ringWidth, interp);
        }
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
    public static final DistortionFx ripple =
        new DistortionFx(200f, 1.5f, 50f, TYPE_OUTWARD, 0f, 1f, 0.25f, Interp.pow2Out);

    public static final DistortionFx shockwaveRing =
        new DistortionFx(220f, 2.0f, 45f, TYPE_OUTWARD, 0f, 1f, 0.12f, Interp.pow2In);

    public static final DistortionFx inwardRing =
        new DistortionFx(180f, 1.6f, 50f, TYPE_INWARD, 1f, 0f, 0.25f, Interp.pow2In);

    public static final DistortionFx slowRipple =
        new DistortionFx(260f, 1.0f, 90f, TYPE_OUTWARD, 0f, 1f, 0.3f, Interp.linear);

    public static final DistortionFx balloon =
        new DistortionFx(180f, 1.8f, 45f, TYPE_OUTWARD, 0f, 1f, 0.8f, Interp.pow2Out);

    // ---- 其他 ----
    public static final DistortionFx hugeExplosion =
        new DistortionFx(200f, 2.0f, 50f, TYPE_OUTWARD_BOUNCE, 0f, 1f, 0f, Interp.pow2Out);
    public static final DistortionFx delayedBlast =
        new DistortionFx(180f, 2.0f, 60f, TYPE_DELAYED, 0f, 1f, 0f, Interp.pow2Out);
    public static final DistortionFx subtleContinuous =
        new DistortionFx(40f, 0.3f, 10f, TYPE_INWARD, 1f, 0f, 0f, Interp.linear);

    // ---- 龙王专属 ----
    /** 龙王 EMP：急速外扩 + 收尾减速（同帧自动去重）。 */
    public static final DistortionFx reignImpact =
        new DistortionFx(280f, 2.0f, 55f, TYPE_OUTWARD, 0f, 1f, 0.15f, Interp.pow2Out);
}