package mindustry.components;

import arc.util.*;
import mindustry.gen.*;

/**
 * 修理速度配置：一把工具每秒能补多少，全部是绝对数值，不按目标血量缩放。
 *
 * <p>数值挂在<b>武器</b>上（{@link mindustry.type.Weapon#repairToolSpeed}）——
 * 建造枪就是 {@code BuildWeapon}，修复枪是带 {@code repairTool} 标记的武器，
 * 一把枪一套速度；再往上是整个单位的 {@link mindustry.type.UnitType#repairSpeed}，
 * 都没有就用默认两档 {@link #buildTool} / {@link #repairTool}。
 *
 * <pre>{@code
 * // 给这把建造枪自填数值：结构血量每秒 60，部件每秒 40
 * UnitTypes.mega.weapons.first().repairToolSpeed = new RepairSpeed(60f, 40f);
 *
 * // 把某门炮标成修复枪，不填数值就走默认修复枪档（建造枪 × 倍率）
 * UnitTypes.vela.weapons.get(1).repairTool = true;
 *
 * // 逻辑版：修残血目标更快
 * UnitTypes.vela.weapons.get(1).repairToolSpeed = new RepairSpeed(){
 *     @Override
 *     public float healthRate(Unit repairer, Unit target){
 *         return target == null ? 40f : (target.healthf() < 0.3f ? 80f : 40f);
 *     }
 * };
 * }</pre>
 */
public class RepairSpeed{
    /** 建造枪的默认档：结构血量 {@link Components#repairHullRate}／秒，部件耐久 {@link Components#repairComponentRate}／秒。 */
    public static final RepairSpeed buildTool = new RepairSpeed(){
        @Override
        public float healthRate(Unit repairer, Unit target){
            return Components.repairHullRate;
        }

        @Override
        public float componentRate(Unit repairer, Unit target){
            return Components.repairComponentRate;
        }
    };

    /** 修复枪的默认档：在建造枪基础上乘 {@link Components#repairToolMultiplier}。 */
    public static final RepairSpeed repairTool = new RepairSpeed(){
        @Override
        public float healthRate(Unit repairer, Unit target){
            return Components.repairHullRate * Components.repairToolMultiplier;
        }

        @Override
        public float componentRate(Unit repairer, Unit target){
            return Components.repairComponentRate * Components.repairToolMultiplier;
        }
    };

    /** 每秒可回复的结构血量（绝对值）。 */
    public float healthRate;
    /** 每秒可修复的部件耐久（绝对值）。 */
    public float componentRate;

    public RepairSpeed(){
        this(Components.repairHullRate, Components.repairComponentRate);
    }

    public RepairSpeed(float healthRate, float componentRate){
        this.healthRate = healthRate;
        this.componentRate = componentRate;
    }

    /**
     * 每秒回复的结构血量。参数在核心数据库里可能为 null（只有类型没有实例），
     * 覆盖时请自行判空。
     */
    public float healthRate(@Nullable Unit repairer, @Nullable Unit target){
        return healthRate;
    }

    /** 每秒修复的部件耐久。参数说明同 {@link #healthRate(Unit, Unit)}。 */
    public float componentRate(@Nullable Unit repairer, @Nullable Unit target){
        return componentRate;
    }
}
