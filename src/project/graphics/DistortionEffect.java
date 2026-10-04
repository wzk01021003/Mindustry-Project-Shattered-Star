package project.graphics;

import arc.math.Interp;
import mindustry.entities.Effect;

public class DistortionEffect extends Effect {

    // ====== 会被 JSON 填充的字段（必须 public 非 final）======
    public float radius = 100f;
    public float strength = 1f;
    public int effectType = 1;             // 0=内凹 1=外扩 2=先凹后凸 3=先凸后凹 4=延迟
    public float radiusFrom = 0f;
    public float radiusTo = 1f;
    public float ringWidth = 0f;
    public String interpName = "pow2Out";

    public DistortionEffect() {
        // lifetime 由 JSON 的 "lifetime" 字段覆盖
        // 占位 renderer，实际用 render() 接管
        super(1f, e -> {});
    }

    @Override
    public void render(EffectContainer e) {
        // 这里 this 字段已经是 JSON 填充后的值
        DistortionRenderer.addDistortion(
            e.x, e.y,
            radius, strength, lifetime, effectType,
            radiusFrom, radiusTo, ringWidth,
            parseInterp(interpName)
        );
    }

    private static Interp parseInterp(String name) {
        if (name == null) return Interp.pow2Out;
        switch (name.toLowerCase()) {
            case "linear":    return Interp.linear;
            case "pow2in":    return Interp.pow2In;
            case "pow2out":   return Interp.pow2Out;
            case "pow3in":    return Interp.pow3In;
            case "pow3out":   return Interp.pow3Out;
            case "pow4in":    return Interp.pow4In;
            case "pow4out":   return Interp.pow4Out;
            case "circleout": return Interp.circleOut;
            case "circlein":  return Interp.circleIn;
            case "sinein":    return Interp.sineIn;
            case "sineout":   return Interp.sineOut;
            default:          return Interp.pow2Out;
        }
    }
}