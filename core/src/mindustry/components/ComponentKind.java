package mindustry.components;

/** 部件分类。每类部件管理不同的单位属性。 */
public enum ComponentKind{
    /** 移动部件：管理移动速度。 */
    mobility("mobility"),
    /** 装甲部件：管理护甲与最大生命值。 */
    armor("armor"),
    /** 武器部件：管理射速与冷却。 */
    weapon("weapon"),
    /** 能力部件：管理能力的生效。 */
    ability("ability");

    /** 文案 key 后缀，见 component.* */
    public final String tag;

    ComponentKind(String tag){
        this.tag = tag;
    }

    public String bundleName(){
        return "component.kind." + tag;
    }
}
