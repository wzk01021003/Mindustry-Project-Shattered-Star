package project.modules;

import arc.graphics.Color;

/**
 * 模块种类。决定这个模块受损之后，降级作用到哪个属性上。
 *
 * <p>只有 {@link #weapon} 和 {@link #move} 有连续降级，{@link #armor} 走覆盖值，
 * {@link #ability} 是开关——理由见 {@link ModuleSystem}。
 *
 * <p>颜色只给 UI 用（悬停面板的条、数据库页的色点），不参与任何数值逻辑。
 * 四种颜色按「暖→冷」排开，同一块面板上不用看字也能分出是哪一类。
 */
public enum ModuleKind{
    /** 装甲：改写 {@code unit.armorOverride} */
    armor("F2B33D"),
    /** 武器：压低 {@code WeaponMount.reload} 的恢复速度 */
    weapon("E05B4B"),
    /** 移动：改写 {@code unit.speedMultiplier} */
    move("4FA3D1"),
    /** 能力：模块报废即禁用，不做数值降级 */
    ability("9B7FE0");

    /** UI 用色 */
    public final Color color;

    ModuleKind(String hex){
        this.color = Color.valueOf(hex);
    }
}
