package project;

import arc.Core;
import arc.math.Mathf;
import arc.util.Log;
import mindustry.Vars;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;

/**
 * 星辉「主动跃迁」的可调参数，以及游戏设置里的对应面板。
 *
 * <p>原版扩展（Vanilla Expansion）把跃迁的移动端自动触发挂在它自己的设置系统里
 * （{@code VESettings} 的 {@code ve-mobile-auto-warp}），本 mod 没有那套东西，
 * 所以在这里单开一个分类，把跃迁相关的开关集中起来，玩家不必改代码就能调手感。
 *
 * <p>数值只在两处同步：{@link #load()} 读一次，设置面板里改动时再读一次。
 * 运行期 {@link project.abilities.WarpAbility} 直接读静态字段，
 * 不每帧查 {@code Core.settings}。
 *
 * <p>这里的默认值要和 {@code WarpAbility} 里的字段保持一致，
 * 因为能力字段是「出厂设定」，设置项是玩家覆盖值。
 */
public class SSWarpSettings{

    public static final String KEY_ENABLED     = "ss-warp-enabled";
    public static final String KEY_AUTO        = "ss-warp-auto";
    public static final String KEY_AUTO_DIST   = "ss-warp-auto-dist";
    public static final String KEY_MOBILE      = "ss-warp-mobile";
    public static final String KEY_MOBILE_DIST = "ss-warp-mobile-dist";
    public static final String KEY_VOLUME      = "ss-warp-volume";
    public static final String KEY_EFFECT      = "ss-warp-effect";
    public static final String KEY_RETICLE     = "ss-warp-reticle";
    public static final String KEY_RETICLE_OWN = "ss-warp-reticle-own";

    /** 自动跃迁距离的滑条范围（单位：格） */
    private static final int AUTO_DIST_MIN = 10, AUTO_DIST_MAX = 120, AUTO_DIST_DEF = 60;
    /** 移动端自动跃迁距离范围（单位：格） */
    private static final int MOBILE_DIST_MIN = 10, MOBILE_DIST_MAX = 80, MOBILE_DIST_DEF = 30;

    // ---- 运行期快照，WarpAbility 每帧读这些字段 ----
    /** 总开关：关掉后 Alt 与所有自动跃迁全部失效 */
    public static boolean enabled = true;
    /** 指挥自动跃迁：AI 接到远处的移动 / 采矿 / 搬运指令时自己闪过去 */
    public static boolean autoWarp = true;
    /** 指挥自动跃迁的基础距离阈值（像素） */
    public static float autoWarpDistance = AUTO_DIST_DEF * Vars.tilesize;
    /** 移动端自动跃迁：镜头拉远后闪到镜头位置（触屏没有 Alt） */
    public static boolean mobileAutoWarp = true;
    /** 移动端自动跃迁的触发距离（像素） */
    public static float mobileAutoWarpDistance = MOBILE_DIST_DEF * Vars.tilesize;
    /** 跃迁音效音量，0~1 */
    public static float volume = 0.55f;
    /** 起终点特效、能量轨迹、粒子与震屏的开关；关掉只留音效 */
    public static boolean effects = true;
    /** 蓄力时在落点画准星（虚线 + 双环 + 十字）；关掉后依然能跃迁，只是没有落点提示 */
    public static boolean reticle = true;
    /** 准星只在玩家直接操控的单位上画，指挥 / AI 单位不画 */
    public static boolean reticlePlayerOnly = false;

    public static void load(){
        try{
            Vars.ui.settings.addCategory("@ss-warp.category", "settings", SSWarpSettings::buildUI);
        }catch(Throwable t){
            Log.err("[ss] 无法注册跃迁设置分类，可通过控制台手动写 Core.settings", t);
        }
        refresh();
    }

    /** 从 Core.settings 重新读一遍，设置面板改动后立刻生效 */
    public static void refresh(){
        enabled = Core.settings.getBool(KEY_ENABLED, true);
        autoWarp = Core.settings.getBool(KEY_AUTO, true);
        autoWarpDistance = Math.max(1, Core.settings.getInt(KEY_AUTO_DIST, AUTO_DIST_DEF)) * Vars.tilesize;
        mobileAutoWarp = Core.settings.getBool(KEY_MOBILE, true);
        mobileAutoWarpDistance = Math.max(1, Core.settings.getInt(KEY_MOBILE_DIST, MOBILE_DIST_DEF)) * Vars.tilesize;
        volume = Mathf.clamp(Core.settings.getInt(KEY_VOLUME, 55) / 100f, 0f, 1f);
        effects = Core.settings.getBool(KEY_EFFECT, true);
        reticle = Core.settings.getBool(KEY_RETICLE, true);
        reticlePlayerOnly = Core.settings.getBool(KEY_RETICLE_OWN, false);
    }

    private static void buildUI(SettingsTable table){
        table.checkPref(KEY_ENABLED, true, b -> refresh());
        table.checkPref(KEY_AUTO, true, b -> refresh());
        table.sliderPref(KEY_AUTO_DIST, AUTO_DIST_DEF, AUTO_DIST_MIN, AUTO_DIST_MAX, 5,
            i -> Core.bundle.format("ss-warp.dist", i), i -> refresh());
        table.checkPref(KEY_MOBILE, true, b -> refresh());
        table.sliderPref(KEY_MOBILE_DIST, MOBILE_DIST_DEF, MOBILE_DIST_MIN, MOBILE_DIST_MAX, 5,
            i -> Core.bundle.format("ss-warp.dist", i), i -> refresh());
        table.sliderPref(KEY_VOLUME, 55, 0, 100, 5, i -> i + "%", i -> refresh());
        table.checkPref(KEY_EFFECT, true, b -> refresh());
        table.checkPref(KEY_RETICLE, true, b -> refresh());
        table.checkPref(KEY_RETICLE_OWN, false, b -> refresh());
    }
}
