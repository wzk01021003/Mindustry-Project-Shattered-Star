package mindustry.components;

import arc.Core;
import mindustry.gen.Unit;

/**
 * 建造枪自动锁定受伤单位时的挑选优先级。
 *
 * <p>只在「已下达自动修理指令」时生效；手动瞄准不受影响。
 */
public enum RepairPriority{
    /** 绝对血量最少（默认）：谁剩的血条最短就先修谁。 */
    lowestHealth("component.repair.lowest-health"),
    /** 护甲最高：优先保住抗伤主力。 */
    highestArmor("component.repair.highest-armor"),
    /** 离自己最近：跑动最少，适合跟团推进。 */
    nearest("component.repair.nearest");

    public final String key;

    RepairPriority(String key){
        this.key = key;
    }

    public String localized(){
        return Core.bundle.get(key, name());
    }

    /**
     * 给候选单位打分，分数越高越优先。
     * @param candidate 候选目标
     * @param self 修理者
     */
    public float score(Unit candidate, Unit self){
        return switch(this){
            case lowestHealth -> -candidate.health;
            case highestArmor -> candidate.armor;
            case nearest -> -candidate.dst(self);
        };
    }
}
