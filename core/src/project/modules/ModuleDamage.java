package project.modules;

import arc.struct.ObjectMap;
import mindustry.entities.bullet.BulletType;

/**
 * 一发子弹对模块造成的伤害。两种填法：
 *
 * <ul>
 *   <li>{@link #ratio(float)}：{@code bullet.damage * 倍率}。子弹伤害调了它自动跟，
 *       默认全用它</li>
 *   <li>{@link #flat(float)}：固定值，和子弹伤害无关。只用来表达「这颗子弹伤害低、
 *       但专门破模块」这类特例——搜 {@code ModuleDamage.flat} 能一眼列出所有例外</li>
 * </ul>
 *
 * <p>绑定走外部映射而不是给 {@code BulletType} 加字段：项目里 14 处子弹定义覆盖
 * 了 7 种原版基类，为了一个字段去给每种基类写子类不值得，而且原版子弹（敌人用的）
 * 永远拿不到那个字段，最后还是得有一条兜底路径。
 */
public class ModuleDamage{

    /** 没配过的子弹走这个 */
    public static final ModuleDamage DEFAULT = ratio(0.15f);

    private static final ObjectMap<BulletType, ModuleDamage> table = new ObjectMap<>();

    /** 数值本身 */
    public final float amount;
    /** true = 倍率，false = 绝对值 */
    public final boolean ratio;
    /**
     * 随机浮动。0.25 = 每次命中在 ±25% 内随机。
     *
     * <p>放在子弹上而不是全局，是为了让不同武器有不同手感：激光稳（0.05）、
     * 炮弹飘（0.35）。全局一个方差的话，所有武器读起来都一样。
     */
    public float spread = 0.25f;

    private ModuleDamage(float amount, boolean ratio){
        this.amount = amount;
        this.ratio = ratio;
    }

    /** 绝对值：每发固定扣这么多 */
    public static ModuleDamage flat(float amount){
        return new ModuleDamage(amount, false);
    }

    /** 倍率：{@code bullet.damage * amount} */
    public static ModuleDamage ratio(float amount){
        return new ModuleDamage(amount, true);
    }

    public ModuleDamage spread(float spread){
        this.spread = spread;
        return this;
    }

    /** 未配置时的基准值。{@code bullet} 可以为 null（火焰、环境伤害等） */
    public float base(BulletType bullet){
        if(ratio){
            return bullet == null ? 0f : bullet.damage * amount;
        }
        return amount;
    }

    // ============================================================
    //  映射
    // ============================================================

    public static void set(BulletType bullet, ModuleDamage damage){
        table.put(bullet, damage);
    }

    public static ModuleDamage get(BulletType bullet){
        if(bullet == null) return DEFAULT;
        ModuleDamage d = table.get(bullet);
        return d == null ? DEFAULT : d;
    }

    public static void clear(){
        table.clear();
    }
}
