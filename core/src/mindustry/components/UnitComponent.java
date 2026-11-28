package mindustry.components;

import arc.Core;
import arc.math.*;
import arc.util.*;
import mindustry.gen.Unit;

/**
 * 单位部件定义。一个部件代表单位身上一块可独立损坏/修复的模块，
 * 只有挂到 {@link mindustry.type.UnitType#components} 后才会生效。
 *
 * <p>四类部件分别管理：
 * <ul>
 *     <li>{@link ComponentKind#mobility} — 移动速度（{@link #speedMul}）</li>
 *     <li>{@link ComponentKind#armor} — 护甲与最大生命（{@link #armorAdd} / {@link #healthMul}）</li>
 *     <li>{@link ComponentKind#weapon} — 射速与冷却（{@link #fireRateMul}）</li>
 *     <li>{@link ComponentKind#ability} — 能力是否生效（{@link #abilityIndex}）</li>
 * </ul>
 *
 * <p>损伤后的属性可以用三种方式衰减，见 {@link DecayMode}。
 */
public class UnitComponent{
    private static int lastId = 0;

    /** 内部唯一编号，用于运行期状态索引。 */
    public final int id = lastId++;
    /** 唯一名称，同时作为文案 key 后缀：component.&lt;name&gt;.name */
    public final String name;
    public final ComponentKind kind;

    /** 部件最大耐久。 */
    public float health = 100f;

    // ---- 衰减方式 ----
    /** 衰减模式。 */
    public DecayMode mode = DecayMode.curve;
    /** 曲线指数：1 = 线性；&gt;1 前期掉得慢、后期陡降；&lt;1 前期就明显衰减。仅 curve 模式有效。 */
    public float curvePower = 1f;
    /** 完全损坏时的属性系数下限（1 = 不受影响，0 = 完全失效）。 */
    public float minFactor = 0f;
    /** fixed 模式的阈值：耐久比例高于该值时给满额属性。 */
    public float threshold = 0.5f;

    // ---- 特殊修复 ----
    /** 损坏后是否必须用特殊方法修复（此时建造枪无法修复该部件）。 */
    public boolean specialRepair = false;
    /** 特殊修复的具体实现。为 null 且 specialRepair = true 时，该部件损坏后无法自动恢复。 */
    public @Nullable RepairHandler repairHandler;
    /**
     * 特殊修复部件的「可修判定」。
     * 为 null 时，任何常规工具都修不了它，只能走 {@link #repairHandler}。
     * 想让某类修理者能修，就挂一个 {@link SpecialRepairCheck} 实现。
     */
    public @Nullable SpecialRepairCheck specialRepairCheck;

    // ---- 效果参数（按 kind 取用）----
    /** 移动部件：满耐久时的移动速度倍率。 */
    public float speedMul = 1f;
    /** 装甲部件：满耐久时的额外护甲。 */
    public float armorAdd = 0f;
    /** 装甲部件：满耐久时的最大生命倍率。 */
    public float healthMul = 1f;
    /** 武器部件：满耐久时的射速倍率（&gt;1 更快，即冷却更短）。 */
    public float fireRateMul = 1f;
    /** 能力部件：管理的能力下标，对应 UnitType.abilities 的顺序；-1 表示全部能力。 */
    public int abilityIndex = -1;

    public UnitComponent(String name, ComponentKind kind){
        this.name = name;
        this.kind = kind;
    }

    /** 便捷构造：直接指定耐久。 */
    public UnitComponent(String name, ComponentKind kind, float health){
        this(name, kind);
        this.health = health;
    }

    public String localizedName(){
        return Core.bundle.get("component." + name + ".name", name);
    }

    public String localizedDescription(){
        return Core.bundle.get("component." + name + ".description", "");
    }

    /** @return 该部件在给定耐久下的属性系数，1 = 完好，0 = 完全失效。 */
    public float factor(float current){
        float full = Mathf.clamp(current / Math.max(health, 0.0001f));
        return switch(mode){
            // 固定值：跨过阈值前满额，之后直接掉到下限
            case fixed -> full >= threshold ? 1f : minFactor;
            // 禁用：只要不满就失效
            case disable -> full >= 1f ? 1f : minFactor;
            // 曲线：按耐久比例的幂曲线平滑过渡
            case curve -> Mathf.lerp(minFactor, 1f, (float)Math.pow(full, curvePower));
        };
    }

    /** @return 部件是否已损坏（耐久归零）。 */
    public boolean broken(float current){
        return current <= 0.0001f;
    }

    /** 是否可以由建造枪一类的常规手段修复（只看部件自身，不看修理者）。 */
    public boolean repairable(){
        return !specialRepair;
    }

    /**
     * 指定修理者能否修这件部件。
     * @param repairer 正在修理的单位
     * @return 普通部件恒为 true；特殊部件交给 {@link #specialRepairCheck} 判定
     */
    public boolean toolRepairable(Unit repairer){
        if(!specialRepair) return true;
        return specialRepairCheck != null && specialRepairCheck.canRepair(repairer, this);
    }

    /**
     * 数据库展示用的静态判定：某类工具能不能修这件部件。
     * @param repairTool false = 建造枪，true = 修复枪
     * @return {@code TRUE}/{@code FALSE} 为确定结论；{@code null} 表示取决于
     *         {@link #specialRepairCheck} 的运行期判断，界面上显示为待定
     */
    public @Nullable Boolean toolRepairableStatic(boolean repairTool){
        if(!specialRepair) return true;
        if(specialRepairCheck == null) return false;
        return null;
    }

    // ---- 链式配置 ----

    public UnitComponent curve(float power, float min){
        this.mode = DecayMode.curve;
        this.curvePower = power;
        this.minFactor = min;
        return this;
    }

    public UnitComponent fixedAt(float threshold, float min){
        this.mode = DecayMode.fixed;
        this.threshold = threshold;
        this.minFactor = min;
        return this;
    }

    public UnitComponent disabling(float min){
        this.mode = DecayMode.disable;
        this.minFactor = min;
        return this;
    }

    public UnitComponent special(RepairHandler handler){
        this.specialRepair = true;
        this.repairHandler = handler;
        return this;
    }

    public UnitComponent special(){
        return special(null);
    }

    /** 给特殊修复部件挂可修判定：满足条件的修理者能直接修它。 */
    public UnitComponent repairCheck(SpecialRepairCheck check){
        this.specialRepairCheck = check;
        return this;
    }
}
