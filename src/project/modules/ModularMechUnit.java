package project.modules;

import mindustry.gen.MechUnit;

/**
 * 走了模块系统的步行 / 机甲单位。
 *
 * <p>存在的唯一理由是这两个量没有别的入口能改——引擎把它们算在每帧重算的
 * 临时字段里，外面写进去活不过一帧：
 *
 * <ul>
 *   <li>{@code speedMultiplier}：{@code MechUnit.update()} 开头重置成 1，
 *       再乘各 status 的乘数，最后由 {@code floorSpeedMultiplier()} 带进
 *       {@code speed()}。所以只能在 {@link #speed()} 里乘模块系数</li>
 *   <li>{@code armor} / {@code armorOverride}：同样每帧由 {@code type.armor}
 *       重算。而且 {@code damage(float)} 是<b>直接读字段</b>的，不调
 *       {@code armor()}，所以覆盖 {@code armor()} 没用，得在
 *       {@link #damage(float)} 里临时替换再放回</li>
 * </ul>
 *
 * <p>{@code super} 的结果里已经含地形、状态、boost、strafe 这些原版因素，
 * 乘一个模块系数不会把它们抹掉。
 */
public class ModularMechUnit extends MechUnit{

    @Override
    public float speed(){
        return super.speed() * ModuleSystem.moveOf(this);
    }

    @Override
    public void damage(float amount){
        float eff = ModuleSystem.armorOf(this);
        if(eff >= 0.999f){
            super.damage(amount);
            return;
        }

        // armorOverride >= 0 时 damage 优先用它，两个都要缩。
        // 调用完原样放回去：字段被引擎每帧重置，这里只是临时借用，
        // 留在原地反而会让同一帧的第二次命中再乘一次
        float savedArmor = armor, savedOverride = armorOverride;
        if(armorOverride >= 0f){
            armorOverride *= eff;
        }else{
            armor *= eff;
        }

        try{
            super.damage(amount);
        }finally{
            armor = savedArmor;
            armorOverride = savedOverride;
        }
    }

    public static ModularMechUnit create(){
        return new ModularMechUnit();
    }
}
