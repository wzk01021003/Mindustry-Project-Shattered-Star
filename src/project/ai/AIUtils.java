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
    //  射程（从武器算）
    // ================================================================

    /**
     * 单位所有武器里**最小**的射程。
     * 用于 kiting 控距：有远距离主炮 + 近距离副炮时，按副炮算。
     * 无武器 → 返回 0。
     */
    public static float minRange(UnitType type) {
        if (type.weapons.isEmpty()) return 0f;

        float min = Float.MAX_VALUE;
        for (int i = 0; i < type.weapons.size; i++) {
            Weapon w = type.weapons.get(i);
            if (w == null || w.bullet == null) continue;
            float r = w.bullet.range();
            if (r > 0 && r < min) min = r;
        }
        return min == Float.MAX_VALUE ? 0f : min;
    }

    /**
     * 单位所有武器里**最大**的射程。
     * 用于索敌范围（索敌用最大射程，才能看到远处的敌人）。
     */
    public static float maxRange(UnitType type) {
        if (type.weapons.isEmpty()) return 0f;

        float max = 0f;
        for (int i = 0; i < type.weapons.size; i++) {
            Weapon w = type.weapons.get(i);
            if (w == null || w.bullet == null) continue;
            float r = w.bullet.range();
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
     * 分离搜索半径。保证能搜到最远的"应该分离"的邻居。
     */
    public static float separationRadius(Unit a, float mul, float margin) {
        return a.hitSize * mul + margin;
    }
}