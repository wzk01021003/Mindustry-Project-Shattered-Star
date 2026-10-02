package project.content.units;

import arc.func.Boolf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;

public class Slot {

    public float x, y;
    public float rotation;
    public boolean absoluteRotation = false;
    public float smoothSpeed = 0.08f;
    public float drawLayer = Layer.overlayUI - 2f;
    public Boolf<UnitType> filter = null;

    /** 挂载时是否允许开火。null = 跟随载具默认，true = 允许，false = 禁止。 */
    public Boolean canShootWhenAttached = null;

    public Slot(float x, float y, float rotation) {
        this.x = x;
        this.y = y;
        this.rotation = rotation;
    }

    public Slot abs() {
        this.absoluteRotation = true;
        return this;
    }

    public Slot smooth(float s) {
        this.smoothSpeed = s;
        return this;
    }

    public Slot layer(float l) {
        this.drawLayer = l;
        return this;
    }

    public Slot filter(Boolf<UnitType> f) {
        this.filter = f;
        return this;
    }

    /** 显式设置此槽位是否允许开火。 */
    public Slot canShoot(boolean v) {
        this.canShootWhenAttached = v;
        return this;
    }

    public boolean accepts(UnitType t) {
        if (filter == null) return true;
        return filter.get(t);
    }

    public static final float LAYER_ABOVE_ALL = Layer.overlayUI - 2f;
    public static final float LAYER_ABOVE_TURRET = Layer.turret + 1f;
    public static final float LAYER_BELOW_TURRET = Layer.turret - 1f;
    public static final float LAYER_GROUND = Layer.groundUnit + 0.1f;
    public static final float LAYER_FLYING = Layer.flyingUnit + 0.1f;
}