package project.modules;

import mindustry.gen.UnitEntity;

/**
 * 走了模块系统的通用单位（飞行、悬浮这一类没写 {@code constructor} 的）。
 *
 * <p>和 {@link ModularMechUnit} 是同一件事的两个版本：{@code UnitType} 默认
 * 用 {@code UnitEntity::create}，机甲用 {@code MechUnit::create}，两条继承链
 * 各自实现 {@code speed()} / {@code damage(float)}，没法只写一个。
 *
 * <p>逻辑和那边完全一致，理由也一致，见 {@link ModularMechUnit} 的注释。
 */
public class ModularUnitEntity extends UnitEntity{

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

    public static ModularUnitEntity create(){
        return new ModularUnitEntity();
    }
}
