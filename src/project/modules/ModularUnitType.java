package project.modules;

import mindustry.type.UnitType;

/**
 * 带模块表的单位类型。
 *
 * <p>{@code modules} 为 null 或空表示这个单位不参与模块系统，命中时直接被
 * {@link ModuleSystem} 跳过，血量、护甲、射速全部走原生逻辑。这样不用给
 * 内容库里每个单位都配一遍模块——只有需要表达「会被打残」的重装单位才配。
 */
public class ModularUnitType extends UnitType{

    /** 模块表。顺序即存档 / 日志里的顺序，改动前想清楚 */
    public UnitModule[] modules;

    public ModularUnitType(String name){
        super(name);
    }

    public ModularUnitType modules(UnitModule... modules){
        this.modules = modules;
        return this;
    }

    /** 有没有配模块 */
    public boolean modular(){
        return modules != null && modules.length > 0;
    }
}
