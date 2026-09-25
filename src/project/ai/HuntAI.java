package project.ai;

import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.ai.Pathfinder;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Teamc;
import mindustry.gen.WaterMovec;
import mindustry.type.Weapon;
import mindustry.world.meta.BlockFlag;

/**
 * 狩猎 AI：
 *  - 自动适配单位（飞行 / 海军 / 地面 / 全向）
 *  - 远程单位控制距离（kiting）：太近退，太远追，范围内停下射击
 *  - 狗斗 / 自爆单位（crawler 类）直接冲脸
 *  - 非全向单位用 A* 寻路绕开建筑 / 墙
 *  - 没目标时走向最近的敌方核心
 */
public class HuntAI extends AIController {

    // ============ 可调参数 ============
    /** 进入交战距离 = 射程 × 此系数 */
    public static float engageFactor = 0.85f;
    /** 后退距离 = 射程 × 此系数（低于此值后退） */
    public static float retreatFactor = 0.55f;
    /** 索敌间隔（帧） */
    public static float retargetInterval = 20f;
    /** 索敌范围 = 射程 × 此系数 */
    public static float searchRangeMultiplier = 1.5f;
    /** 无武器时的兜底射程（避免除以 0） */
    public static float minRange = 40f;

    // ============ 运行时状态 ============
    protected boolean isFlying;
    protected boolean isNaval;
    protected boolean isOmni;
    protected boolean isDogfighter;
    protected boolean initialized = false;

    /** 强制狗斗模式，忽略自动判断（由 ss-dogfight 指令设置） */
    public boolean forceDogfighter = false;

    protected void ensureInit() {
        if (initialized) return;
        initialized = true;
        isFlying = unit.type.flying;
        isNaval = unit instanceof WaterMovec;
        isOmni = isFlying || isNaval || unit.type.omniMovement;
        isDogfighter = forceDogfighter || computeDogfighter();
    }

    /**
     * 判断这个单位是不是"必须贴脸才能打"：
     *  1. 有 self-destruct 子弹（killShooter）
     *  2. 有近战爆炸弹（instantDisappear + splashDamageRadius）
     *  3. 射程 < 60（贴脸近战）
     */
    protected boolean computeDogfighter() {
        if (unit.mounts != null) {
            for (Weapon mount : unit.mounts) {
                if (mount == null || mount.weapon == null || mount.weapon.bullet == null) continue;
                var b = mount.weapon.bullet;
                if (b.killShooter) return true;
                if (b.instantDisappear && b.splashDamageRadius > 0f) return true;
            }
        }
        float range = unit.range();
        return range > 0f && range < 60f;
    }

    @Override
    public void updateMovement() {
        ensureInit();

        // ---- 1. 索敌 ----
        if (target == null && retarget()) {
            target = findTarget(unit.x, unit.y, unit.range() * searchRangeMultiplier,
                unit.type.targetAir, unit.type.targetGround);
        }

        if (target != null && Units.invalidateTarget(target, unit.team, unit.x, unit.y, Float.MAX_VALUE)) {
            target = null;
        }

        // ---- 2. 移动 ----
        if (target != null) {
            updateCombat();
        } else {
            updateSearch();
        }

        // ---- 3. 非飞行单位落地 ----
        if (!isFlying && unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.descentSpeed);
        }

        faceTarget();
    }

    /** 有目标：接近 + kiting（狗斗单位直接冲） */
    protected void updateCombat() {
        float dst = unit.dst(target);

        // ============ 狗斗 / 自爆单位：直接冲脸 ============
        if (isDogfighter) {
            if (isOmni) {
                Tmp.v1.set(target).sub(unit).setLength(unit.speed());
                unit.movePref(Tmp.v1);
                unit.lookAt(target);
            } else {
                // 非全向单位用 moveTo 寻路冲，range 传 0 表示"能多近就多近"
                moveTo(target, 0f, 30f);
            }
            return;
        }

        // ============ 远程单位：kiting ============
        float range = Math.max(unit.range(), minRange);
        float engage = range * engageFactor;
        float retreat = range * retreatFactor;

        if (isOmni) {
            float speed = unit.speed();
            Tmp.v1.set(target).sub(unit);

            if (dst < retreat) {
                // 太近，后退
                Tmp.v1.setLength(-speed);
                unit.movePref(Tmp.v1);
            } else if (dst > engage) {
                // 太远，接近
                Tmp.v1.setLength(speed);
                unit.movePref(Tmp.v1);
            } else {
                // 在射程内，停下
                Tmp.v1.setZero();
                unit.movePref(Tmp.v1);
            }

            unit.lookAt(target);
        } else {
            // 非全向单位：moveTo 内部会用 A* 绕开建筑 / 墙
            if (dst > engage) {
                moveTo(target, engage, 30f);
            }
            // 已经在射程内：什么都不做，停下
        }
    }

    /** 没目标：去最近的敌方核心 */
    protected void updateSearch() {
        if (isFlying) {
            Teamc core = targetFlag(unit.x, unit.y, BlockFlag.core, true);
            if (core != null) {
                moveTo(core, Math.max(unit.range(), minRange) * 0.7f, 30f);
                return;
            }
        }
        // 地面 / 海军 / 无核心：用 flowfield 走向核心
        pathfind(Pathfinder.fieldCore);
    }

    @Override
    public void updateTargeting() {
        if (retarget()) {
            target = findTarget(unit.x, unit.y, unit.range() * searchRangeMultiplier,
                unit.type.targetAir, unit.type.targetGround);
        }
    }

    @Override
    public Teamc findTarget(float x, float y, float range, boolean air, boolean ground) {
        // 1. 最近的敌方单位
        Teamc u = Units.closestTarget(unit.team, x, y, range,
            other -> other.checkTarget(air, ground),
            tile -> ground);
        if (u != null) return u;

        // 2. 敌方核心（在 2 倍射程内才追）
        Teamc core = targetFlag(x, y, BlockFlag.core, true);
        if (core != null && Mathf.dst(x, y, core.getX(), core.getY()) <= range * 2f) {
            return core;
        }

        // 3. 敌方炮塔
        Teamc turret = targetFlag(x, y, BlockFlag.turret, true);
        if (turret != null && Mathf.dst(x, y, turret.getX(), turret.getY()) <= range * 2f) {
            return turret;
        }

        return null;
    }

    @Override
    public boolean retarget() {
        return timer.get(timerTarget, retargetInterval);
    }
}