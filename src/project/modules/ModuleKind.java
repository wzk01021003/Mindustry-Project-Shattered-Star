package project.modules;

/**
 * 模块种类。决定这个模块受损之后，降级作用到哪个属性上。
 *
 * <p>只有 {@link #weapon} 和 {@link #move} 有连续降级，{@link #armor} 走覆盖值，
 * {@link #ability} 目前只做 0/1 判定——理由见 {@link ModuleSystem#apply}。
 */
public enum ModuleKind{
    /** 装甲：改写 {@code unit.armorOverride} */
    armor,
    /** 武器：压低 {@code WeaponMount.reload} 的恢复速度 */
    weapon,
    /** 移动：改写 {@code unit.speedMultiplier} */
    move,
    /** 能力：模块报废即禁用，不做数值降级 */
    ability
}
