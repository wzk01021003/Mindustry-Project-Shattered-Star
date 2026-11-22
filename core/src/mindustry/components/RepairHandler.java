package mindustry.components;

import mindustry.gen.Unit;

/**
 * 特殊修复方法。部件配置了 specialRepair 后，建造枪无法修复它，
 * 只有这里返回 true 时才算修复完成。
 */
public interface RepairHandler{
    /**
     * 每帧调用一次（仅当部件已损坏、且单位未被移除时）。
     * @param delta 帧时间倍率，正常为 1
     * @return true 表示修复完成，部件恢复满耐久
     */
    boolean update(Unit unit, UnitComponent component, float delta);

    /** 脱战若干秒后自愈的通用实现。 */
    static RepairHandler regen(float seconds){
        return new RepairHandler(){
            // 用一个按单位 id 分开的计时器，避免多个单位共享进度
            final arc.struct.LongMap<Float> timers = new arc.struct.LongMap<>();

            @Override
            public boolean update(Unit unit, UnitComponent component, float delta){
                long key = ((long)unit.id << 16) | (component.id & 0xFFFF);
                // 受到伤害就重置计时
                if(unit.health < unit.maxHealth - 0.01f || unit.isShooting()){
                    timers.put(key, 0f);
                    return false;
                }
                float t = timers.get(key, 0f) + delta / 60f;
                timers.put(key, t);
                return t >= seconds;
            }
        };
    }
}
