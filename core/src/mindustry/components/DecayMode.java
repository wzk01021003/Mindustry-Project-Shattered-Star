package mindustry.components;

/** 部件属性随损伤衰减的方式。 */
public enum DecayMode{
    /** 固定值：耐久高于阈值时给满额属性，跌破阈值后直接掉到下限。适合"坏了就是坏了"的部件。 */
    fixed("fixed"),
    /** 曲线：按当前耐久比例做幂曲线平滑衰减，pow 控制曲线形状。适合磨损类部件。 */
    curve("curve"),
    /** 禁用：只要出现损伤就完全失效，修满才恢复。适合精密部件。 */
    disable("disable");

    public final String tag;

    DecayMode(String tag){
        this.tag = tag;
    }

    public String bundleName(){
        return "component.mode." + tag;
    }
}
