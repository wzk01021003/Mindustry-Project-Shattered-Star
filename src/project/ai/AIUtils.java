package project.ai;

import mindustry.gen.Unit;
import mindustry.gen.WaterMovec;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.type.unit.TankUnitType;

public class AIUtils {

    // ================================================================
    //  运动类别
    // ================================================================

    /**
     *   0 — 飞行
     *   1 — 海军（WaterMovec）
     *   2 — 坦克（TankUnitType）
     *   3 — 多足 / 腿（legCount > 0）
     *   4 — 普通地面
     */
    public static int moveClass(Unit u) {
        UnitType t = u.type;
        if (t.flying) return 0;
        if (u instanceof WaterMovec) return 1;
        if (t instanceof TankUnitType) return 2;
        if (t.legCount > 0) return 3;
        return 4;
    }

    public static boolean sameMovementClass(Unit a, Unit b) {
        return moveClass(a) == moveClass(b);
    }

    // ================================================================
    //  射程
    // ================================================================

    /** 单个武器的实际射程 = lifetime × speed（BulletType 没有 range() 方法） */
    public static float weaponRange(Weapon w) {
        if (w == null || w.bullet == null) return 0f;
        // 优先用 rangeOverride
        if (w.bullet.rangeOverride > 0f) return w.bullet.rangeOverride;
        return w.bullet.lifetime * w.bullet.speed;
    }

    /**
     * 单位所有武器里**最小**的射程。
     * 用于 kiting 控距：有远距离主炮 + 近距离副炮时，按副炮算。
     * 无武器 → 返回 0。
     */
    public static float minRange(UnitType type) {
        if (type.weapons.isEmpty()) return 0f;

        float min = Float.MAX_VALUE;
        for (int i = 0; i < type.weapons.size; i++) {
            float r = weaponRange(type.weapons.get(i));
            if (r > 0 && r < min) min = r;
        }
        return min == Float.MAX_VALUE ? 0f : min;
    }

    /**
     * 单位所有武器里**最大**的射程。
     * 用于索敌范围。
     */
    public static float maxRange(UnitType type) {
        if (type.weapons.isEmpty()) return 0f;

        float max = 0f;
        for (int i = 0; i < type.weapons.size; i++) {
            float r = weaponRange(type.weapons.get(i));
            if (r > max) max = r;
        }
        return max;
    }

    // ================================================================
    //  自适应分离距离
    // ================================================================

    /**
     * 两个单位之间的最小分离距离。
     * 完全按视觉尺寸（hitSize）成比例。
     */
    public static float separationDistance(Unit a, Unit b, float factor, float margin) {
        return (a.hitSize + b.hitSize) * factor + margin;
    }

    /**
     * 分离搜索半径。
     */
    public static float separationRadius(Unit a, float mul, float margin) {
        return a.hitSize * mul + margin;
    }
}