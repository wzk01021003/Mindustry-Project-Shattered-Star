package project.content.units;

import arc.func.Boolf;
import mindustry.type.UnitType;

public class CarrierUnitType extends UnitType {

    public static final float EXIT_BACK = 180f;

    /** 总容量。每个挂载单位按 hitSize 消耗。 */
    public float carrierCapacity = 80f;

    /** 全局筛选。null = 不限制。 */
    public Boolf<UnitType> globalFilter = null;

    // 出口定义
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

    public CarrierUnitType releaseSpeed(float s) {
        this.releaseSpeed = s;
        return this;
    }

    public CarrierUnitType releaseDistance(float d) {
        this.releaseDistance = d;
        return this;
    }

    /** 一个单位占多少容量。默认按 hitSize。 */
    public float payloadCost(UnitType type) {
        if (type == null) return 0f;
        return type.hitSize;
    }
}