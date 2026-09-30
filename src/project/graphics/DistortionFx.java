package project.graphics;

import mindustry.entities.Effect;

/**
* 扭曲效果的 Effect 包装。
* 用法：
*   public static final Effect ionImpact = new DistortionFx(80f, 0.8f, 25f);
* 然后直接当作 Effect 用：
*   bullet.hitEffect = ionImpact;
*   block.updateEffect = ionImpact;
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