package mindustry.components;

/**
 * 常用部件预设。四类部件各给两个档位，方便直接挂到单位上。
 * 所有预设都是普通配置对象，可以被多个单位类型共享。
 */
public class ComponentPresets{

    // ---------------- 移动部件 ----------------

    /** 推进器：推力组件，损坏后速度按曲线下滑。 */
    public static UnitComponent thruster(){
        UnitComponent c = new UnitComponent("thruster", ComponentKind.mobility, 60f);
        c.speedMul = 1.08f;
        c.curve(1.6f, 0.45f);
        return c;
    }

    /** 履带：结构件，跌破阈值才明显掉速。 */
    public static UnitComponent tracks(){
        UnitComponent c = new UnitComponent("tracks", ComponentKind.mobility, 95f);
        c.speedMul = 1.05f;
        c.fixedAt(0.35f, 0.55f);
        return c;
    }

    // ---------------- 装甲部件 ----------------

    /** 复合装甲板：提供护甲与额外结构强度。 */
    public static UnitComponent plating(){
        UnitComponent c = new UnitComponent("plating", ComponentKind.armor, 130f);
        c.armorAdd = 2f;
        c.healthMul = 1.15f;
        c.curve(1f, 0.5f);
        return c;
    }

    /** 轻量化骨架：增益小、衰减慢。 */
    public static UnitComponent lightFrame(){
        UnitComponent c = new UnitComponent("light-frame", ComponentKind.armor, 75f);
        c.armorAdd = 1f;
        c.healthMul = 1.05f;
        c.curve(2f, 0.7f);
        return c;
    }

    // ---------------- 武器部件 ----------------

    /** 供弹机构：跌破阈值后射速骤降。 */
    public static UnitComponent autoloader(){
        UnitComponent c = new UnitComponent("autoloader", ComponentKind.weapon, 80f);
        c.fireRateMul = 1.15f;
        c.fixedAt(0.5f, 0.3f);
        return c;
    }

    /** 精密枪管：一旦损伤就失去精度加成。 */
    public static UnitComponent precisionBarrel(){
        UnitComponent c = new UnitComponent("precision-barrel", ComponentKind.weapon, 55f);
        c.fireRateMul = 1.1f;
        c.disabling(0.15f);
        return c;
    }

    // ---------------- 能力部件 ----------------

    /** 通用能力核心：损坏后能力完全失效，且只能用特殊方法修复（脱战 10 秒自愈）。 */
    public static UnitComponent abilityCore(){
        UnitComponent c = new UnitComponent("ability-core", ComponentKind.ability, 50f);
        c.abilityIndex = -1;
        c.disabling(0f);
        c.special(RepairHandler.regen(10f));
        return c;
    }

    /** 指向某个具体能力的核心（下标对应 UnitType.abilities 的顺序）。 */
    public static UnitComponent abilityCore(int index){
        UnitComponent c = abilityCore();
        c.abilityIndex = index;
        return c;
    }
}
