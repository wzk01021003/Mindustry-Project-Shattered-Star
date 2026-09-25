package project.ai;

import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.ai.Pathfinder;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.gen.WaterMovec;
import mindustry.world.Tile;
import mindustry.world.meta.BlockFlag;

import static mindustry.Vars.*;

/**
 * 狩猎 AI：
 *  - 自动适配单位（飞行 / 海军 / 地面 / 全向）
 *  - 远程单位控制距离（kiting）：太近退，太远追，范围内停下射击
 *  - 狗斗 / 自爆单位直接冲脸
 *  - 非全向单位用 A* 寻路绕开建筑 / 墙
 *  - 没目标时按优先级：敌方核心 → spawner → 敌方建筑 → 回防自家核心
 *  - 静止 / 慢速时微微摆动，增加生命感
 */
public class HuntAI extends AIController {

    // ============ 行为参数 ============
    public static float engageFactor = 0.85f;
    public static float retreatFactor = 0.55f;
    public static float retargetInterval = 20f;
    public static float searchRangeMultiplier = 1.5f;
    public static float minRange = 40f;
    public static float defendRadius = 80f;
    public static float coreDangerRadius = 300f;

    // ============ 摆动参数 ============
    /** 摆动频率（弧度/秒） */
    public static float idleSpeed = 0.8f;
    /** 最大摆角（度） */
    public static float idleAmp = 3.5f;

    // ============ 运行时状态 ============
    protected boolean isFlying;
    protected boolean isNaval;
    protected boolean isOmni;
    protected boolean isDogfighter;
    protected boolean initialized = false;

    public boolean forceDogfighter = false;

    protected void ensureInit() {
        if (initialized) return;
        initialized = true;
        isFlying = unit.type.flying;
        isNaval = unit instanceof WaterMovec;
        isOmni = isFlying || isNaval || unit.type.omniMovement;
        isDogfighter = forceDogfighter || computeDogfighter();
    }

    protected boolean computeDogfighter() {
        if (unit.mounts != null) {
            for (WeaponMount mount : unit.mounts) {
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

        if (target == null && retarget()) {
            target = findTarget(unit.x, unit.y, unit.range() * searchRangeMultiplier,
                unit.type.targetAir, unit.type.targetGround);
        }

        if (target != null && Units.invalidateTarget(target, unit.team, unit.x, unit.y, Float.MAX_VALUE)) {
            target = null;
        }

        if (target != null) {
            updateCombat();
        } else {
            updateSearch();
        }

        if (!isFlying && unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.descentSpeed);
        }

        faceTarget();

        // 待命 / 慢速时微微摆动
        if (unit.vel.len2() < Mathf.sqr(unit.speed() * 0.2f)) {
            applyIdleSway();
        }
    }

    /** 正弦摆动，让单位看起来有生命感 */
    protected void applyIdleSway() {
        float seconds = Time.time / 60f;
        unit.rotation += Mathf.sin(seconds * idleSpeed + unit.id * 0.37f) * idleAmp;
    }

    protected void updateCombat() {
        float dst = unit.dst(target);

        if (isDogfighter) {
            if (isOmni) {
                Tmp.v1.set(target).sub(unit).setLength(unit.speed());
                unit.movePref(Tmp.v1);
                unit.lookAt(target);
            } else {
                moveTo(target, 0f, 30f);
            }
            return;
        }

        float range = Math.max(unit.range(), minRange);
        float engage = range * engageFactor;
        float retreat = range * retreatFactor;

        if (isOmni) {
            float speed = unit.speed();
            Tmp.v1.set(target).sub(unit);

            if (dst < retreat) {
                Tmp.v1.setLength(-speed);
                unit.movePref(Tmp.v1);
            } else if (dst > engage) {
                Tmp.v1.setLength(speed);
                unit.movePref(Tmp.v1);
            } else {
                Tmp.v1.setZero();
                unit.movePref(Tmp.v1);
            }

            unit.lookAt(target);
        } else {
            if (dst > engage) {
                moveTo(target, engage, 30f);
            }
        }
    }

    protected void updateSearch() {
        if (isFlying) {
            Teamc core = targetFlag(unit.x, unit.y, BlockFlag.core, true);
            if (core != null) {
                moveTo(core, Math.max(unit.range(), minRange) * 0.7f, 30f);
                return;
            }
        }

        if (!indexer.getEnemy(unit.team, BlockFlag.core).isEmpty()) {
            pathfind(Pathfinder.fieldCore);
            return;
        }

        Tile spawnTile = getClosestSpawner();
        if (spawnTile != null) {
            if (isOmni) {
                Tmp.v1.set(spawnTile.worldx(), spawnTile.worldy()).sub(unit).setLength(unit.speed());
                unit.movePref(Tmp.v1);
            } else {
                moveTo(spawnTile, 0f, 30f);
            }
            return;
        }

        Building enemyBuilding = findClosestEnemyBuilding();
        if (enemyBuilding != null) {
            moveTo(enemyBuilding, Math.max(unit.range(), minRange) * 0.7f, 30f);
            return;
        }

        Building allyCore = unit.closestCore();
        if (allyCore != null && isCoreUnderAttack(allyCore)) {
            moveTo(allyCore, defendRadius, 30f);
        }
    }

    protected Building findClosestEnemyBuilding() {
        BlockFlag[] flags = {
            BlockFlag.turret,
            BlockFlag.generator,
            BlockFlag.factory,
            BlockFlag.battery,
            BlockFlag.storage
        };

        Building best = null;
        float bestDst = Float.MAX_VALUE;

        for (BlockFlag flag : flags) {
            for (Building b : indexer.getEnemy(unit.team, flag)) {
                if (b == null || !b.isValid()) continue;
                float dst = b.dst2(unit);
                if (dst < bestDst) {
                    bestDst = dst;
                    best = b;
                }
            }
        }
        return best;
    }

    protected boolean isCoreUnderAttack(Building core) {
        return Units.nearEnemy(unit.team,
            core.x - coreDangerRadius, core.y - coreDangerRadius,
            coreDangerRadius * 2f, coreDangerRadius * 2f);
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
        Teamc u = Units.closestTarget(unit.team, x, y, range,
            other -> other.checkTarget(air, ground),
            tile -> ground);
        if (u != null) return u;

        Teamc core = targetFlag(x, y, BlockFlag.core, true);
        if (core != null && Mathf.dst(x, y, core.getX(), core.getY()) <= range * 2f) {
            return core;
        }

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