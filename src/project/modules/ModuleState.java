package project.modules;

import arc.struct.ObjectMap;
import mindustry.gen.Unit;

/**
 * 一个单位上各模块的运行时血量。
 *
 * <p>表挂在 {@link Unit} 对象上而不是 {@code unit.type} 上——同一张模块定义表
 * 会被同类型的几十个单位共用，血量必须按单位分开。
 *
 * <p>{@link Unit} 是池化复用的（销毁后被 {@code EntityGroup} 回收，新单位拿到的
 * 可能是同一个对象），所以这张表必须在 {@code UnitCreateEvent} 里重建、
 * 在 {@code UnitDestroyEvent} 里删除。只靠懒创建的话，新单位会继承旧单位
 * 打剩的血量。
 */
public class ModuleState{

    private static final ObjectMap<Unit, ModuleState> all = new ObjectMap<>();

    public final Unit unit;
    public final ModularUnitType type;
    public final UnitModule[] defs;
    /** 各模块当前血量，和 {@link #defs} 一一对应 */
    public final float[] hp;
    /** 上一次应用过的移动系数，用来按比例调整 speedMultiplier 而不是每帧覆盖 */
    public float moveEff = 1f;

    private ModuleState(Unit unit, ModularUnitType type){
        this.unit = unit;
        this.type = type;
        this.defs = type.modules;
        this.hp = new float[defs.length];
        for(int i = 0; i < defs.length; i++) hp[i] = defs[i].maxHp;
    }

    /** 取状态，没有就按类型建一个 */
    public static ModuleState get(Unit unit){
        if(unit == null) return null;

        ModuleState st = all.get(unit);
        if(st == null){
            st = create(unit);
            if(st != null) all.put(unit, st);
        }
        return st;
    }

    /** 重建状态（单位创建 / 复用后调用）。非模块单位返回 null */
    public static ModuleState ensure(Unit unit){
        if(unit == null) return null;

        ModuleState st = create(unit);
        if(st == null){
            all.remove(unit);
        }else{
            all.put(unit, st);
        }
        return st;
    }

    public static void remove(Unit unit){
        if(unit != null) all.remove(unit);
    }

    public static void clear(){
        all.clear();
    }

    public static ObjectMap<Unit, ModuleState> all(){
        return all;
    }

    private static ModuleState create(Unit unit){
        if(unit == null || !(unit.type instanceof ModularUnitType)) return null;

        ModularUnitType type = (ModularUnitType) unit.type;
        if(!type.modular()) return null;

        return new ModuleState(unit, type);
    }

    // ============================================================
    //  查询
    // ============================================================

    public float ratio(int index){
        float max = defs[index].maxHp;
        return max <= 0f ? 0f : hp[index] / max;
    }

    /** 某个模块是否已经报废 */
    public boolean broken(int index){
        return hp[index] <= 0f;
    }

    /**
     * 某一种模块的整体性能比例，0~1。
     *
     * <p>同类有多个模块时取<b>最小值</b>：两门炮共用一套射速逻辑，只要有一门
     * 报废了，另一门也没法「替它」开火——取最小比取平均更接近实际表现。
     */
    public float effect(ModuleKind kind){
        float min = 1f;
        boolean found = false;

        for(int i = 0; i < defs.length; i++){
            if(defs[i].kind != kind) continue;
            found = true;
            min = Math.min(min, defs[i].effect(ratio(i)));
        }
        return found ? min : 1f;
    }

    // ============================================================
    //  结算
    // ============================================================

    /** 每个模块都扣同一份伤害，各自的 {@code vulnerability} 再乘一次 */
    public void damage(float total){
        if(total <= 0f) return;

        for(int i = 0; i < hp.length; i++){
            // 已经报废的模块不再吃伤害：它的血量不能被扣成负数，
            // 否则重建时（补到 maxHp 的百分比）会被算错
            if(hp[i] <= 0f) continue;
            hp[i] = Math.max(0f, hp[i] - total * defs[i].vulnerability);
        }
    }

    /**
     * 修一段血。返回实际修进去的量。
     *
     * <p>{@code rebuild = false} 时跳过已报废的模块——普通维修只能修「受损」，
     * 报废必须靠建筑或无人机走 {@code rebuild = true}。
     */
    public float repair(float amount, boolean rebuild){
        if(amount <= 0f) return 0f;

        float used = 0f;
        for(int i = 0; i < hp.length; i++){
            if(hp[i] <= 0f && !rebuild) continue;

            float max = defs[i].maxHp;
            if(hp[i] >= max) continue;

            float add = Math.min(amount - used, max - hp[i]);
            if(add <= 0f) break;

            hp[i] += add;
            used += add;
        }
        return used;
    }
}
