package project.content.units;

import arc.func.Boolf;
import mindustry.Vars;
import mindustry.type.UnitType;
import mindustry.world.meta.Stat;
import mindustry.world.meta.Stats;

public class CarrierUnitType extends UnitType {

    // ============================================================
    //  自定义 Stat（详情页显示的条目）
    // ============================================================
    public static final Stat statCarrierCapacity =
        new Stat("ss-carrier-capacity");
    public static final Stat statCarrierAccepts =
        new Stat("ss-carrier-accepts");

    // ============================================================
    //  容量 + 筛选
    // ============================================================
    public float carrierCapacity = 80f;
    public Boolf<UnitType> globalFilter = null;

    // ============================================================
    //  出口定义
    // ============================================================
    public float exitX = 0f, exitY = 0f;
    public float exitRotation = 0f;
    public float releaseInterval = 12f;
    public float releaseSpeed = 2.5f;
    public float releaseDistance = 60f;

    public CarrierUnitType(String name) {
        super(name);
    }

    public CarrierUnitType filter(Boolf<UnitType> f) {
        this.globalFilter = f;
        return this;
    }

    public CarrierUnitType capacity(float c) {
        this.carrierCapacity = c;
        return this;
    }

    public CarrierUnitType exit(float x, float y, float rotation) {
        this.exitX = x;
        this.exitY = y;
        this.exitRotation = rotation;
        return this;
    }

    public CarrierUnitType releaseInterval(float frames) {
        this.releaseInterval = frames;
        return this;
    }

    public float payloadCost(UnitType type) {
        if (type == null) return 0f;
        return type.hitSize;
    }

    // ============================================================
    //  详情页
    // ============================================================
    @Override
    public void setStats() {
        super.setStats();

        // ---- 容量 ----
        stats.add(statCarrierCapacity, table -> {
            table.add("[lightgray]" + (int) carrierCapacity);
        });

        // ---- 可装载单位（图标列表）----
        stats.add(statCarrierAccepts, table -> {
            table.left();

            int maxShow = 12;
            int count = 0;
            int shown = 0;

            for (UnitType type : Vars.content.units()) {
                if (type == null) continue;
                if (type.isHidden()) continue;
                if (globalFilter != null && !globalFilter.get(type)) continue;

                count++;
                if (shown < maxShow) {
                    try {
                        table.image(type.uiIcon)
                            .size(32f)
                            .padRight(4f)
                            .padBottom(4f)
                            .tooltip(type.localizedName);
                        shown++;
                        if (shown % 6 == 0) table.row();
                    } catch (Throwable ignored) {}
                }
            }

            if (count == 0) {
                table.add("[lightgray]" + mindustry.gen.Iconc.cancel);
            } else if (count > maxShow) {
                table.add("[lightgray]+" + (count - maxShow));
            }
        });
    }
}