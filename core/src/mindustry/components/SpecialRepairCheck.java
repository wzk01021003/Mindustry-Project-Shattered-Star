package mindustry.components;

import mindustry.gen.Unit;

/**
 * 「特殊修复」部件的可修判定。
 *
 * <p>部件用 {@link UnitComponent#special(RepairHandler)} 标记成特殊修复后，
 * 常规工具默认碰不了它。想让某把工具能修，就给部件挂一个本接口的实现，
 * 由它决定「当前这个修理者能不能修这件部件」。
 *
 * <p>做成接口而不是布尔，是因为判定经常要看修理者本身：
 * 比如「只有带了专用工具的修复枪能修」，或者「战力低于某个阈值时不许修」。
 */
@FunctionalInterface
public interface SpecialRepairCheck{

    /**
     * @param repairer 正在修理的单位（可能是建造枪，也可能是修复枪）
     * @param component 目标部件
     * @return 是否允许这次修理
     */
    boolean canRepair(Unit repairer, UnitComponent component);
}
