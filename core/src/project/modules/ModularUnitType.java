package project.modules;

import arc.scene.ui.layout.Table;
import mindustry.gen.Unit;
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

    // ============================================================
    //  UI
    // ============================================================

    /**
     * 单位悬停面板。原版把这一段拿去画控制器、载荷之类，先让它画完再往后接，
     * 免得覆盖掉原版那些行。
     */
    @Override
    public void display(Unit unit, Table table){
        super.display(unit, table);
        ModuleDisplay.hover(unit, table, this);
    }

    /** 核心数据库的单位页。位置在属性表之后、描述之前 */
    @Override
    public void displayExtra(Table table){
        super.displayExtra(table);
        ModuleDisplay.database(table, this);
    }
}
