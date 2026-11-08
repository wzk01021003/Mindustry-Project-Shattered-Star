package project.modules;

import arc.math.Mathf;

/**
 * 模块降级曲线：血量比例 → 性能比例。
 *
 * <p>关键点按 hp 从 1 降到 0 排列，段与段之间线性插值。用分段折线而不是
 * 一个 {@link arc.math.Interp}，是因为三种常见需求它一条就能表达：
 *
 * <ul>
 *   <li><b>满血才有满属性</b>：{@link #linear()}，掉 10% 血就掉 10% 性能</li>
 *   <li><b>过半才开始掉</b>：{@link #plateau(float)}，前半段一条平线</li>
 *   <li><b>掉到某个值就不动了，0 才坏</b>：{@link #floored(float, float)}，
 *       中间那段平线之后接一个断崖</li>
 * </ul>
 *
 * <p>最后一点必须是 {@code (0, 0)}：模块报废时性能一定要是 0，否则会出现
 * 「模块已经坏了但炮还在打」。
 */
public class ModuleCurve{

    /** 关键点血量比例，降序，首项 1、末项 0 */
    public final float[] hp;
    /** 对应的性能比例，和 {@link #hp} 一一对应 */
    public final float[] effect;

    /**
     * 形状的人话描述，只给 UI 看（数据库页每行末尾那一小段）。
     *
     * <p>不写成「从曲线反推形状」是因为反推不可靠：{@code (1,1)→(0,0)} 的直线
     * 和一条恰好两点的自定义折线长得一模一样，但玩家该看到的名字不同。
     * 谁造的曲线谁最清楚，所以名字由工厂自己填。
     */
    public String label = "自定义";

    public ModuleCurve(float[] hp, float[] effect){
        if(hp.length != effect.length || hp.length < 2){
            throw new IllegalArgumentException("曲线至少两个点，且 hp / effect 必须一一对应");
        }
        this.hp = hp;
        this.effect = effect;
    }

    /** 给定血量比例，返回性能比例 */
    public float effect(float hpRatio){
        hpRatio = Mathf.clamp(hpRatio);

        for(int i = 1; i < hp.length; i++){
            if(hpRatio >= hp[i]){
                float span = hp[i - 1] - hp[i];
                // 两点重合时退化成阶跃，取上面那个点的值，避免除零
                if(span <= 1e-6f) return effect[i - 1];

                float t = (hpRatio - hp[i]) / span;
                return Mathf.lerp(effect[i], effect[i - 1], t);
            }
        }
        return effect[effect.length - 1];
    }

    // ============================================================
    //  工厂
    // ============================================================

    /** 满血才有满属性：一条直线 */
    public static ModuleCurve linear(){
        ModuleCurve c = new ModuleCurve(new float[]{1f, 0f}, new float[]{1f, 0f});
        c.label = "线性下降";
        return c;
    }

    /** 掉到 {@code at} 才开始降，之前保持满性能 */
    public static ModuleCurve plateau(float at){
        ModuleCurve c = new ModuleCurve(new float[]{1f, at, 0f}, new float[]{1f, 1f, 0f});
        c.label = percent(at) + " 以下才掉";
        return c;
    }

    /**
     * 降到 {@code at} 之后保持 {@code floor} 不再往下掉，只有报废才归零。
     *
     * <p>中间那两个点（{@code at} 和 {@code 0.001}）是为了把这段拉成平线——
     * 只写 {@code (at, floor) → (0, 0)} 的话，最后那一小段会是一个陡坡，
     * 表现出来就是「血量在 5% 以下时性能突然掉一大截」。
     */
    public static ModuleCurve floored(float at, float floor){
        ModuleCurve c = new ModuleCurve(
            new float[]{1f, at, 0.001f, 0f},
            new float[]{1f, floor, floor, 0f}
        );
        c.label = percent(at) + " 以下保 " + percent(floor);
        return c;
    }

    private static String percent(float v){
        return Math.round(v * 100f) + "%";
    }

    /** 自定义：{@code hp0, effect0, hp1, effect1, ...}，hp 必须降序、首 1 末 0 */
    public static ModuleCurve of(float... pairs){
        if(pairs.length < 4 || pairs.length % 2 != 0){
            throw new IllegalArgumentException("参数成对给：hp0, effect0, hp1, effect1, ...");
        }

        int n = pairs.length / 2;
        float[] hp = new float[n], effect = new float[n];
        for(int i = 0; i < n; i++){
            hp[i] = pairs[i * 2];
            effect[i] = pairs[i * 2 + 1];
        }
        return new ModuleCurve(hp, effect);
    }
}
