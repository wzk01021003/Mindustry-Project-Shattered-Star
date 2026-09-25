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

    /**
     * 单位所有武器里**最小**的射程。
     * 用于 kiting 控距：有远距离主炮 + 近距离副炮时，按副炮算。
     * 无武器或全部无效 → 返回 type.range()。
     */
    public static float minRange(UnitType type) {
        if (type.weapons.isEmpty()) return type.range();

        float min = Float.MAX_VALUE;
        for (int i = 0; i < type.weapons.size; i++) {
            Weapon w = type.weapons.get(i);
            if (w == null || w.bullet == null) continue;
            float r = w.bullet.range();
            if (r > 0 && r < min) min = r;
        }
        return min == Float.MAX_VALUE ? type.range() : min;
    }

    /**
     * 单位所有武器里**最大**的射程。
     * 用于索敌范围（索敌还是要用最大射程，不然看不到远的敌人）。
     */
    public static float maxRange(UnitType type) {
        return type.range();
    }

    // ================================================================
    //  自适应分离距离
    // ================================================================

    /**
     * 两个单位之间的最小分离距离。
     * 完全按视觉尺寸（hitSize）成比例，不再被固定 + 8 撑开。
     *
     * @param factor 双方 hitSize 之和的倍数（0.6~0.75 之间）
     * @param margin 额外固定间距（格），用于避免完美贴一起
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