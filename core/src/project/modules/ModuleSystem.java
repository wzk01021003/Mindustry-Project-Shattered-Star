package project.modules;

import arc.Events;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.gen.Unit;

/**
 * 模块系统的总入口：事件注册、命中结算、降级应用、维修 API。
 *
 * <p>三条链路：
 * <ul>
 *   <li><b>受伤</b>：监听 {@code UnitDamageEvent}，拿到 {@code bullet} 后查
 *       {@link ModuleDamage} 映射，把这一发的模块伤害同时扣到所有模块上
 *       （不除以模块数——每个模块各自承担全额，靠 {@code vulnerability} 区分抗性）</li>
 *   <li><b>降级</b>：每帧在 {@code Trigger.update} 里把各模块的性能比例落到
 *       {@code speedMultiplier} / {@code armorOverride} / {@code WeaponMount.reload} 上</li>
 *   <li><b>维修</b>：{@link ModuleState#repair} 对外暴露，建筑和无人机直接调</li>
 * </ul>
 */
public class ModuleSystem{

    private static boolean registered = false;

    public static void init(){
        if(registered) return;
        registered = true;

        Events.on(UnitCreateEvent.class, e -> ModuleState.ensure(e.unit));
        Events.on(UnitDestroyEvent.class, e -> ModuleState.remove(e.unit));
        Events.on(UnitDamageEvent.class, ModuleSystem::damaged);
        Events.run(Trigger.update, ModuleSystem::applyAll);
    }

    // ============================================================
    //  命中结算
    // ============================================================

    private static void damaged(UnitDamageEvent e){
        ModuleState st = ModuleState.get(e.unit);
        if(st == null) return;

        BulletType type = e.bullet == null ? null : e.bullet.type;
        ModuleDamage md = ModuleDamage.get(type);
        float base = md.base(type);
        // 倍率型在没有子弹信息时（火焰、环境伤害）算不出基准值，直接跳过。
        // 想让这类伤害也啃模块，就给它配一个 flat 值
        if(base <= 0f) return;

        float total = md.spread <= 0f
            ? base
            : base * Mathf.random(1f - md.spread, 1f + md.spread);

        st.damage(total);
    }

    // ============================================================
    //  降级应用
    // ============================================================

    private static void applyAll(){
        for(ModuleState st : ModuleState.all().values()){
            apply(st);
        }
    }

    private static void apply(ModuleState st){
        Unit unit = st.unit;
        // 池化复用后单位可能已经变成别的类型，这条状态就不该再往它身上写
        if(unit == null || unit.type != st.type || !unit.isValid()) return;

        applyMove(st);
        applyArmor(st);
        applyWeapon(st);
    }

    private static void applyMove(ModuleState st){
        // 这里只把系数算出来放着，不写 unit.speedMultiplier。
        //
        // 走过一次弯路：speedMultiplier 看着像个可以随手乘的字段，实际上是
        // MechUnit.update() 开头无条件重置成 1 的临时量（字节码里连着
        // healthMultiplier / damageMultiplier 一起 dup_x1 写回去），之后再乘
        // 每个 status 的乘数。任何在外面写进去的值活不过一帧，表现就是
        // 「移动模块报废了但单位照样跑」。
        //
        // 真正生效的位置是 ModularMechUnit.speed() / ModularUnitEntity.speed()，
        // 它们读这个字段再乘上 super.speed()。
        st.moveEff = st.effect(ModuleKind.move);
    }

    private static void applyArmor(ModuleState st){
        // 和移动同一个坑：armor 字段也是每帧由 type.armor 重算的，
        // armorOverride 更是在 update 开头先写 -1 再让 status 覆写。
        // 所以改 armorOverride 一样没用。而且 damage(float) 是直接读字段的，
        // 连 armor() 都不调，只能在 ModularMechUnit.damage(float) 里临时替换。
        st.armorEff = st.effect(ModuleKind.armor);
    }

    private static void applyWeapon(ModuleState st){
        float weapon = st.effect(ModuleKind.weapon);
        if(weapon >= 0.999f || st.unit.mounts == null) return;

        // reload 只能靠「每帧补回去一点」来拖慢，不能靠设下限——
        // 设下限缩短的是倒数区间，反而会让射速变快。
        // 每帧补 Time.delta * (1 - effect)，净倒数速度就变成原来的 effect 倍，
        // 周期正好是 reload / effect。effect = 0 时净变化为 0，永远打不出来。
        float add = Time.delta * (1f - weapon);

        for(WeaponMount mount : st.unit.mounts){
            if(mount == null || mount.weapon == null) continue;
            // 已经装填完的不动，否则会把「等待目标」变成「装填中」
            if(mount.reload > 0f){
                mount.reload += add;
            }
        }
    }

    // ============================================================
    //  对外查询
    // ============================================================

    /** 单位某一类模块的整体性能比例，0~1。没有模块的单位返回 1 */
    public static float effect(Unit unit, ModuleKind kind){
        ModuleState st = ModuleState.get(unit);
        return st == null ? 1f : st.effect(kind);
    }

    /**
     * 移动系数，给 {@code ModularMechUnit.speed()} 用。
     *
     * <p>先判类型再查表：{@code speed()} 是每帧被问好几次的热路径，
     * 场上绝大多数单位都没配模块，一个 instanceof 比一次哈希查找便宜。
     */
    public static float moveOf(Unit unit){
        if(unit == null || !(unit.type instanceof ModularUnitType)) return 1f;
        ModuleState st = ModuleState.get(unit);
        return st == null ? 1f : st.moveEff;
    }

    /** 装甲系数，给 {@code ModularMechUnit.damage(float)} 用 */
    public static float armorOf(Unit unit){
        if(unit == null || !(unit.type instanceof ModularUnitType)) return 1f;
        ModuleState st = ModuleState.get(unit);
        return st == null ? 1f : st.armorEff;
    }

    /**
     * 能力模块是否还能开。
     *
     * <p>{@code Ability} 没有 {@code enabled} 这种开关，一旦挂到
     * {@code unit.abilities} 上就会每帧被调 {@code update}，外面拦不住。
     * 所以能力模块的判定放在使用侧——能力的 {@code update} 开头问一句
     * {@code if(!ModuleSystem.enabled(unit, ModuleKind.ability)) return;}，
     * 模块报废就不再进自己的逻辑。
     *
     * <p>阈值取 0.5 而不是 0：{@link ModuleCurve} 保证报废时是 0，
     * 但中间插值出来的小数（比如 0.4）不代表「半开」——要么开要么不开。
     */
    public static boolean enabled(Unit unit, ModuleKind kind){
        return effect(unit, kind) > 0.5f;
    }

    /** 单位是否还完整无损 */
    public static boolean intact(Unit unit){
        ModuleState st = ModuleState.get(unit);
        if(st == null) return true;

        for(int i = 0; i < st.hp.length; i++){
            if(st.hp[i] < st.defs[i].maxHp) return false;
        }
        return true;
    }
}
