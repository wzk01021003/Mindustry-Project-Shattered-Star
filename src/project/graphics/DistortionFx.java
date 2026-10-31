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

    // ---- 堡垒专属 ----
    /**
     * 堡垒火炮炮弹命中·内层：等离子「拧紧」阶段。
     *
     * 寿命刻意只有外层的一半不到（13 帧 vs 32 帧），与外层同时触发时
     * 形成「先向内收一口、再向外炸开」的时序层次——单一预设无法表达
     * 这种双段节奏，因为一个 Data 只有一条生命周期曲线。
     *
     * 实心（环宽 0）让它作用于整个圆面而非细环，配合 pow3In 的
     * 前段缓慢、末段加速，收束感集中在爆炸瞬间；涡旋 2.0 与外层
     * 的 1.2 同向叠加，两层切向分量累加后形成螺旋状的扭转纹路。
     */
    public static final DistortionFx fortressArtilleryImplosion =
    new DistortionFx(58f, 1.5f, 13f, TYPE_INWARD, 0f, 1f, 0f, Interp.pow3In, 2.0f);

    /**
     * 堡垒火炮炮弹命中·外层：高强度锐环外扩。
     *
     * 参数对齐 {@link #rwPlasmaImpact}（半径 170 / 强度 1.5 / 环宽 0.10 /
     * 涡旋 1.2）并整体上调一档——铁锈未来科技等离子的冲击感来自
     * 「环足够锐、扩散足够快、强度到达即巅峰」：环宽 0.10 而非 0.18
     * 让冲击面收窄成一个清晰的能量壳，pow3Out 使半径在前 1/3 就冲到
     * 近似满值，之后靠 1-p² 的强度衰减自然散去。
     *
     * 半径 145 约为炮弹 splash 半径（35）的 4 倍：既明显溢出伤害圈，
     * 又不会在单发时铺满半个屏幕——原 185 在 1080p 下约占 1/3 屏宽，
     * 多门齐射时会糊成一片，收敛到 145 后单发清晰、齐射可控。
     */
    public static final DistortionFx fortressArtilleryImpact =
    new DistortionFx(145f, 1.7f, 32f, TYPE_OUTWARD, 0f, 1f, 0.10f, Interp.pow3Out, 1.2f);

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
