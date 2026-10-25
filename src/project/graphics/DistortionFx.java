package project.graphics;

import arc.Core;
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
    /** 切向涡旋强度：0 = 纯径向，正值逆时针，负值顺时针 */
    public final float swirl;
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
        this(radius, strength, life, type, radiusFrom, radiusTo, 0f, interp, 0f);
    }

    public DistortionFx(float radius, float strength, float life, int type,
        float radiusFrom, float radiusTo, float ringWidth, Interp interp) {
        this(radius, strength, life, type, radiusFrom, radiusTo, ringWidth, interp, 0f);
    }

    public DistortionFx(float radius, float strength, float life, int type,
        float radiusFrom, float radiusTo, float ringWidth, Interp interp, float swirl) {
        super(life, new CooldownRenderer(radius, strength, life, type,
                radiusFrom, radiusTo, ringWidth, interp, swirl));
        this.radius = radius;
        this.strength = strength;
        this.life = life;
        this.type = type;
        this.radiusFrom = radiusFrom;
        this.radiusTo = radiusTo;
        this.ringWidth = ringWidth;
        this.swirl = swirl;
        this.interp = interp;
    }

    // ============================================================
    //  内部类：只在 Effect 生命周期的第一帧触发
    //  Effect 系统每帧都会调用 renderer 一次，持续 life 帧。
    //  如果不去重，一发攻击会持续 life 帧冒出 life 个扭曲。
    // ============================================================
    private static class CooldownRenderer implements Cons<EffectContainer> {
        final float radius, strength, life;
        final int type;
        final float radiusFrom, radiusTo, ringWidth, swirl;
        final Interp interp;

        CooldownRenderer(float radius, float strength, float life, int type,
            float radiusFrom, float radiusTo, float ringWidth,
            Interp interp, float swirl) {
            this.radius = radius;
            this.strength = strength;
            this.life = life;
            this.type = type;
            this.radiusFrom = radiusFrom;
            this.radiusTo = radiusTo;
            this.ringWidth = ringWidth;
            this.swirl = swirl;
            this.interp = interp;
        }

        @Override
        public void get(EffectContainer e) {
            // 只在 Effect 的第一帧触发（e.time 从 0 开始，每帧 += Time.delta）
            if (e.time > Time.delta * 1.5f) return;

            // 注：旧版这里还有一层「同一帧多实例只触发一次」的帧级去重，
            // 但 lastFrame 存在共享的 renderer 实例上，导致同帧两个单位
            // 触发同一预设时第二个被直接吞掉。并发限量由
            // DistortionRenderer.addDistortion 内的 maxPerFrame 负责，
            // 这里不再重复去重。
            DistortionRenderer.addDistortion(e.x, e.y, radius, strength, life, type,
                radiusFrom, radiusTo, ringWidth, interp, swirl);
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
    /** 龙王 EMP：急速外扩 + 收尾减速。 */
    public static final DistortionFx reignImpact =
    new DistortionFx(280f, 2.0f, 55f, TYPE_OUTWARD, 0f, 1f, 0.15f, Interp.pow2Out);

    // ============================================================
    //  铁锈战争 1.15 shader 效果风格预设
    //  （teleport / water ripples / nuke shockwaves + 未来科技等离子审美）
    // ============================================================

    /**
     * 传送：空间折叠。强吸入 + 强涡旋，末端内爆收拢，
     * 观感类似铁锈传送门把画面「拧」进去。
     */
    public static final DistortionFx rwTeleport =
    new DistortionFx(150f, 1.8f, 35f, TYPE_INWARD, 1f, 0f, 0f, Interp.pow3In, 2.2f);

    /** 水波纹：大范围弱外扩环 + 轻涡旋，缓慢柔和，适合持续能量场。 */
    public static final DistortionFx rwWaterRipple =
    new DistortionFx(240f, 1.0f, 70f, TYPE_OUTWARD, 0f, 1f, 0.3f, Interp.pow2Out, 0.4f);

    /** 核弹冲击波：高速外扩锐环，强度高、寿命短，到达即巅峰。 */
    public static final DistortionFx rwNukeShockwave =
    new DistortionFx(320f, 2.4f, 40f, TYPE_OUTWARD, 0f, 1f, 0.15f, Interp.pow2Out);

    /** 未来科技等离子命中：外扩 + 中涡旋 + 短促，配合色散有能量炸开感。 */
    public static final DistortionFx rwPlasmaImpact =
    new DistortionFx(170f, 1.5f, 30f, TYPE_OUTWARD, 0f, 1f, 0.1f, Interp.pow3Out, 1.2f);

    /** 量子坍缩： inward 涡旋 + 回弹，收招有明显「拧紧再弹开」的层次。 */
    public static final DistortionFx rwQuantumCollapse =
    new DistortionFx(200f, 1.6f, 50f, TYPE_INWARD_BOUNCE, 1f, 0f, 0.2f, Interp.pow2In, 1.6f);
}
