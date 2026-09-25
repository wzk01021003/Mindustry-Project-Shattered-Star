package project.ai;

import mindustry.gen.Unit;
import mindustry.gen.WaterMovec;
import mindustry.type.UnitType;
import mindustry.type.unit.TankUnitType;

public class AIUtils {

    /**
     * 运动类别（独立编号，互不干扰分散）：
     *   0 — 飞行（flying）
     *   1 — 海军（WaterMovec，水栖单位）
     *   2 — 坦克（TankUnitType，会压碎小单位）
     *   3 — 多足 / 腿（legCount > 0，可跨越墙、悬崖）
     *   4 — 普通地面
     *
     * 分开分类的意义：
     *   空军不会被陆军挤，陆军不会被坦克挤，
     *   多足不会被普通机甲挤，互不干扰。
     */
    public static int moveClass(Unit u) {
        UnitType t = u.type;
        if (t.flying) return 0;
        if (u instanceof WaterMovec) return 1;
        if (t instanceof TankUnitType) return 2;
        if (t.legCount > 0) return 3;
        return 4;
    }

    /** 两个单位是否属于同一运动类别 */
    public static boolean sameMovementClass(Unit a, Unit b) {
        return moveClass(a) == moveClass(b);
    }
}