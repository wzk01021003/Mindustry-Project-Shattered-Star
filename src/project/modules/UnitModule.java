package project.modules;

/**
 * 一个模块的定义：名字、种类、血量上限、易损度、降级曲线。
 *
 * <p>运行时血量不在这里——同一张定义表会被同类型的几十个单位共用，
 * 血量按单位存在 {@link ModuleState} 里。
 */
public class UnitModule{

    /** 显示名，也用作存档 / 日志里的标识 */
    public final String name;
    public final ModuleKind kind;
    /** 血量上限 */
    public final float maxHp;

    /**
     * 易损度，乘在受到的那个模块伤害上。1 = 正常，0.4 = 抗打，1.8 = 一碰就碎。
     *
     * <p>它和 {@link #maxHp} 在数学上有重叠（血 200 × 易损 1.0 等价于血 400 × 易损 0.5），
     * 只有打算做成「不同伤害类型对不同模块的克制」时才需要真的用满它。
     */
    public float vulnerability = 1f;

    /** 降级曲线。null 时按 {@link #kind} 取默认值 */
    public ModuleCurve curve;

    public UnitModule(String name, ModuleKind kind, float maxHp){
        this.name = name;
        this.kind = kind;
        this.maxHp = maxHp;
    }

    public UnitModule vulnerability(float v){
        this.vulnerability = v;
        return this;
    }

    public UnitModule curve(ModuleCurve c){
        this.curve = c;
        return this;
    }

    /** 血量比例 → 性能比例 */
    public float effect(float hpRatio){
        return (curve == null ? defaultCurve(kind) : curve).effect(hpRatio);
    }

    /**
     * 各类模块的默认曲线。
     *
     * <p>默认值不是随便给的，是按「玩家能不能感知到」排的：
     * 武器掉一点就该慢下来（线性），移动要过一半才有感觉（平台），
     * 装甲则要在低血量时止跌——装甲再叠上血量本身变少，线性降会让重甲单位
     * 在残血时瞬间崩掉，那是调不回来的雪崩。能力是开关，满血到报废之间
     * 一直是满性能，只在归零那一下断掉，所以是一条贴到 1 的平线。
     */
    public static ModuleCurve defaultCurve(ModuleKind kind){
        if(kind == ModuleKind.move) return ModuleCurve.plateau(0.5f);
        if(kind == ModuleKind.armor) return ModuleCurve.floored(0.3f, 0.5f);
        if(kind == ModuleKind.ability) return ModuleCurve.floored(0.001f, 1f);
        return ModuleCurve.linear();
    }
}
