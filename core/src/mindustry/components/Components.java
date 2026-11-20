package mindustry.components;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.*;

/**
 * 部件系统的运行时入口：耐久状态、属性结算、损伤与修复。
 *
 * <p>状态按单位 id 存在外部表里（{@link Unit} 是编译期生成的类，无法加字段），
 * 单位被移除后由定期清理回收。没有部件的单位不产生任何分配，也不参与任何计算。
 */
public class Components{
    /** 单位受到伤害时，分摊到部件上的比例。0 = 部件不会因战斗损坏。 */
    public static float damageShare = 0.5f;
    /**
     * 建造枪每秒能修复的结构血量（绝对值，不随目标血量缩放）。
     * 量级对齐原版修理塔：每秒十几到几十点，修大机体要几十秒，
     * 不会出现「一秒钟回一大截」。想按目标血量缩放就给单位挂自定义 {@link RepairSpeed}。
     */
    public static float repairHullRate = 10f;
    /**
     * 建造枪每秒能修复的部件耐久（绝对值，部件耐久本身就是固定值，不随单位血量缩放）。
     * 部件耐久普遍在 50~130，取 10 表示单件部件要好几秒才补齐，不至于一碰就满。
     */
    public static float repairComponentRate = 10f;
    /** 修复枪（专用修理工具，如灾星头上那对）相对建造枪的倍率。 */
    public static float repairToolMultiplier = 2f;
    /** 清理孤儿状态的间隔（帧）。 */
    private static final float cleanupInterval = 600f;
    private static float cleanupTimer;

    private static final IntMap<State> states = new IntMap<>();

    /** 单个单位的部件运行期数据。 */
    public static class State{
        /** 各部件当前耐久，下标与 unit.type.components 对齐。 */
        public float[] durability;
        /** 各部件当前属性系数缓存。 */
        public float[] factor;
        /** 特殊修复计时（帧）。 */
        public float[] specialTimer;
    }

    /** @return 该单位类型是否配置了部件。 */
    public static boolean has(Unit unit){
        //type 在 setType 之前可能为 null，这里必须挡住，否则生成类早期调用会 NPE
        return unit != null && unit.type != null && unit.type.components.size > 0;
    }

    /** 取（或创建）单位状态。 */
    public static State state(Unit unit){
        int size = unit.type.components.size;
        State s = states.get(unit.id);

        if(s == null){
            s = new State();
            s.durability = new float[size];
            s.factor = new float[size];
            s.specialTimer = new float[size];
            for(int i = 0; i < size; i++){
                s.durability[i] = unit.type.components.get(i).health;
                s.factor[i] = 1f;
            }
            states.put(unit.id, s);
        }else if(s.durability.length != size){
            //单位换了类型（变形/升级），按新类型重建
            float[] old = s.durability;
            s = new State();
            s.durability = new float[size];
            s.factor = new float[size];
            s.specialTimer = new float[size];
            for(int i = 0; i < size; i++){
                s.durability[i] = i < old.length ? Math.min(old[i], unit.type.components.get(i).health) : unit.type.components.get(i).health;
                s.factor[i] = 1f;
            }
            states.put(unit.id, s);
        }

        return s;
    }

    /** 单位消失后回收。 */
    public static void remove(Unit unit){
        states.remove(unit.id);
    }

    /** 每帧由 UnitComp 调用：推进特殊修复、结算属性。 */
    public static void update(Unit unit){
        if(!has(unit)) return;

        State s = state(unit);

        for(int i = 0; i < s.durability.length; i++){
            UnitComponent c = unit.type.components.get(i);
            boolean broken = c.broken(s.durability[i]);

            if(broken && c.repairHandler != null){
                s.specialTimer[i] += Time.delta;
                if(c.repairHandler.update(unit, c, Time.delta)){
                    s.durability[i] = c.health;
                    s.specialTimer[i] = 0f;
                    broken = false;
                }
            }

            s.factor[i] = c.factor(s.durability[i]);
            if(broken) s.factor[i] = c.minFactor;
        }

        applyStats(unit, s);

        cleanupTimer += Time.delta;
        if(cleanupTimer >= cleanupInterval){
            cleanupTimer = 0f;
            cleanup();
        }
    }

    /** 把部件系数结算进单位的实际属性。 */
    private static void applyStats(Unit unit, State s){
        float healthMul = 1f, armorAdd = 0f;

        for(int i = 0; i < s.durability.length; i++){
            UnitComponent c = unit.type.components.get(i);
            float f = s.factor[i];

            if(c.kind == ComponentKind.armor){
                healthMul *= Mathf.lerp(1f, c.healthMul, f);
                armorAdd += c.armorAdd * f;
            }
        }

        //最大生命：按比例保持当前血量，避免装/坏部件时血条跳变
        float newMax = Math.max(1f, unit.type.health * healthMul);
        if(Math.abs(newMax - unit.maxHealth) > 0.01f){
            float ratio = unit.maxHealth <= 0f ? 1f : unit.health / unit.maxHealth;
            unit.maxHealth = newMax;
            unit.health = Math.min(newMax, Math.max(0f, ratio * newMax));
        }

        unit.armor = unit.type.armor + armorAdd;
    }

    /** 单位受伤时把一部分伤害分摊给部件。由 HealthComp.damage 调用。 */
    public static void onDamage(Unit unit, float amount){
        if(!has(unit) || amount <= 0f || damageShare <= 0f) return;

        State s = state(unit);
        float total = amount * damageShare;

        //平均分摊到所有还有耐久的部件上
        int alive = 0;
        for(float d : s.durability){
            if(d > 0f) alive++;
        }
        if(alive == 0) return;

        float each = total / alive;
        for(int i = 0; i < s.durability.length; i++){
            if(s.durability[i] <= 0f) continue;
            s.durability[i] = Math.max(0f, s.durability[i] - each);
        }
    }

    /**
     * 把「每秒」的速率换算成本帧的修复量。
     *
     * <p>{@link Time#delta} 是<b>相对 60fps 的帧倍率</b>（60fps 时为 1，不是秒），
     * 所以每秒速率必须先除 60 再乘 delta。漏掉这一步会把「每秒 10 点」变成「每帧 10 点」，
     * 也就是 600 点／秒——这正是之前修理速度快得离谱的原因。
     */
    public static float perFrame(float perSecond){
        return perSecond / 60f * Time.delta;
    }

    // ---------------- 修理 ----------------
    // 建造枪与修复枪都走这一套：能建造就能修，先补结构血再修部件。
    // 差别只有「特殊修复部件算不算能修」——那由部件自己挂的 SpecialRepairCheck 决定，
    // 所以这里不需要区分工具类型，也不需要任何全局开关。

    /** @return 该单位能否修理别人（能建造即有修理工具）。 */
    public static boolean canRepair(Unit unit){
        return unit != null && unit.type != null && unit.canBuild();
    }

    /** @return 该单位是否携带修复枪（专用修理工具）。武器标记优先，其次单位整标记。 */
    public static boolean hasRepairTool(mindustry.type.UnitType type){
        if(type == null) return false;
        if(type.repairTool) return true;

        for(int i = 0; i < type.weapons.size; i++){
            if(type.weapons.get(i).repairTool) return true;
        }
        return false;
    }

    /**
     * 取某个单位类型的修理速度，优先级从具体到通用：
     * <ol>
     *     <li>武器自填的 {@code weapon.repairToolSpeed}——一把枪一套速度，最具体</li>
     *     <li>单位级覆盖 {@code type.repairSpeed}——整个单位统一改</li>
     *     <li>默认档：有修复枪就是修复枪档，否则建造枪档</li>
     * </ol>
     */
    public static RepairSpeed speedOf(mindustry.type.UnitType type){
        if(type == null) return RepairSpeed.buildTool;

        for(int i = 0; i < type.weapons.size; i++){
            mindustry.type.Weapon weapon = type.weapons.get(i);
            if(weapon.repairToolSpeed != null) return weapon.repairToolSpeed;
        }

        if(type.repairSpeed != null) return type.repairSpeed;
        return hasRepairTool(type) ? RepairSpeed.repairTool : RepairSpeed.buildTool;
    }

    /** @return 该修理者能否修这件部件；特殊部件交给部件自己的判定接口。 */
    public static boolean canRepair(Unit repairer, UnitComponent component){
        if(!canRepair(repairer) || component == null) return false;
        return component.toolRepairable(repairer);
    }

    /** @return 目标身上是否有该修理者修得了的损坏部件。 */
    public static boolean needsRepair(Unit unit, Unit repairer){
        if(!has(unit)) return false;
        State s = state(unit);

        for(int i = 0; i < s.durability.length; i++){
            UnitComponent c = unit.type.components.get(i);
            if(s.durability[i] >= c.health - 0.01f) continue;
            if(c.toolRepairable(repairer)) return true;
        }
        return false;
    }

    /** @return 是否有任何损坏部件（不区分修理者，供 UI 判断用）。 */
    public static boolean needsRepair(Unit unit){
        if(!has(unit)) return false;
        State s = state(unit);

        for(int i = 0; i < s.durability.length; i++){
            UnitComponent c = unit.type.components.get(i);
            if(s.durability[i] < c.health - 0.01f) return true;
        }
        return false;
    }

    /**
     * 用常规手段修复部件。
     * @param amount 可用修复量
     * @param repairer 修理者，用于判断特殊部件是否可修
     * @return 实际消耗的修复量
     */
    public static float repair(Unit unit, float amount, Unit repairer){
        if(!has(unit) || amount <= 0f) return 0f;

        State s = state(unit);
        float used = 0f;

        for(int i = 0; i < s.durability.length && used < amount; i++){
            UnitComponent c = unit.type.components.get(i);
            if(!c.toolRepairable(repairer)) continue;
            if(s.durability[i] >= c.health) continue;

            float need = c.health - s.durability[i];
            float give = Math.min(need, amount - used);
            s.durability[i] += give;
            used += give;
        }

        return used;
    }

    /** 特殊修复：绕过建造枪限制，直接把部件修满。 */
    public static void specialRepair(Unit unit, UnitComponent component){
        if(!has(unit)) return;
        State s = state(unit);
        int index = unit.type.components.indexOf(component);
        if(index >= 0){
            s.durability[index] = component.health;
            s.specialTimer[index] = 0f;
        }
    }

    /** @return 某部件的耐久比例，0-1。 */
    public static float healthf(Unit unit, int index){
        if(!has(unit)) return 1f;
        State s = state(unit);
        if(index < 0 || index >= s.durability.length) return 1f;
        return Mathf.clamp(s.durability[index] / Math.max(0.0001f, unit.type.components.get(index).health));
    }

    public static float durability(Unit unit, int index){
        if(!has(unit)) return 0f;
        State s = state(unit);
        return index >= 0 && index < s.durability.length ? s.durability[index] : 0f;
    }

    public static float factorOf(Unit unit, int index){
        if(!has(unit)) return 1f;
        State s = state(unit);
        return index >= 0 && index < s.factor.length ? s.factor[index] : 1f;
    }

    public static boolean broken(Unit unit, int index){
        if(!has(unit)) return false;
        State s = state(unit);
        if(index < 0 || index >= s.durability.length) return false;
        return unit.type.components.get(index).broken(s.durability[index]);
    }

    /** 移动部件结算出的速度倍率。由 UnitComp.speed() 调用。 */
    public static float speedFactor(Unit unit){
        if(!has(unit)) return 1f;

        State s = state(unit);
        float mul = 1f;

        for(int i = 0; i < s.durability.length; i++){
            UnitComponent c = unit.type.components.get(i);
            if(c.kind == ComponentKind.mobility){
                mul *= Mathf.lerp(1f, c.speedMul, s.factor[i]);
            }
        }
        return mul;
    }

    /** 武器部件结算出的射速倍率。由 Weapon.update() 调用。 */
    public static float fireRateFactor(Unit unit){
        if(!has(unit)) return 1f;

        State s = state(unit);
        float mul = 1f;

        for(int i = 0; i < s.durability.length; i++){
            UnitComponent c = unit.type.components.get(i);
            if(c.kind == ComponentKind.weapon){
                mul *= Mathf.lerp(1f, c.fireRateMul, s.factor[i]);
            }
        }
        return mul;
    }

    /** 能力部件是否允许第 index 个能力生效。由 UnitComp.update() 调用。 */
    public static boolean abilityEnabled(Unit unit, int index){
        if(!has(unit)) return true;

        State s = state(unit);

        for(int i = 0; i < s.durability.length; i++){
            UnitComponent c = unit.type.components.get(i);
            if(c.kind != ComponentKind.ability) continue;
            if(c.abilityIndex >= 0 && c.abilityIndex != index) continue;
            if(s.factor[i] <= 0.0001f) return false;
        }
        return true;
    }


    // ---------------- 绘制 ----------------

    /** 在单位头顶画一排部件耐久条。由 UnitComp.draw() 调用。 */
    /** 清掉已经不在世界里的单位状态。 */
    private static void cleanup(){
        IntMap.Keys keys = states.keys();
        while(keys.hasNext){
            int id = keys.next();
            if(Groups.unit.getByID(id) == null){
                keys.remove();
            }
        }
    }
}
