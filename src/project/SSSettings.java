package project;

import arc.Core;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import mindustry.Vars;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;

public class SSSettings {

    public static final String KEY_ENABLE       = "ss-render-enabled";
    public static final String KEY_LEVEL        = "ss-render-level";
    public static final String KEY_CULL         = "ss-render-cull";
    public static final String KEY_AUTO_DISABLE = "ss-render-autodisable";
    public static final String KEY_DEBUG        = "ss-render-debug";

    public static final int LEVEL_OFF = 0, LEVEL_LOW = 1, LEVEL_MEDIUM = 2, LEVEL_HIGH = 3;

    private static boolean deviceSupported = true;
    private static String  deviceReason = null;

    public static void load() {
        detectDevice();

        try {
            Vars.ui.settings.addCategory("ss-settings.category", "settings", SSSettings::buildUI);
        } catch (Throwable t) {
            Log.err("[ss] 无法注册设置分类，可通过控制台手动改 Core.settings", t);
        }
    }

    private static void buildUI(SettingsTable table) {
        if (!deviceSupported) {
            table.add(Core.bundle.get("ss-settings.device.unsupported")).row();
            if (deviceReason != null) {
                table.add("[lightgray]" + Core.bundle.get(deviceReason)).row();
            }
            return;
        }

        table.checkPref(KEY_ENABLE, false);
        table.checkPref(KEY_CULL, true);
        table.checkPref(KEY_AUTO_DISABLE, true);
        table.checkPref(KEY_DEBUG, false);

        // 等级：用一个 Table 承载 [标签 + 按钮]
        Table levelRow = new Table();
        levelRow.add("[lightgray]" + Core.bundle.get("ss-settings.level", "Level") + "[]").padRight(8f);

        TextButton b = levelRow.button(levelName(getLevel()), () -> {
            int l = (getLevel() + 1) % 4;
            Core.settings.put(KEY_LEVEL, l);
        }).minWidth(120f).get();

        b.update(() -> b.setText(levelName(getLevel())));

        table.add(levelRow).left().padBottom(4).growX().row();
    }

    private static String levelName(int l) {
        switch (l) {
            case LEVEL_LOW:    return Core.bundle.get("ss-settings.level.low", "Low");
            case LEVEL_MEDIUM: return Core.bundle.get("ss-settings.level.medium", "Medium");
            case LEVEL_HIGH:   return Core.bundle.get("ss-settings.level.high", "High");
            default:           return Core.bundle.get("ss-settings.level.off", "Off");
        }
    }

    private static void detectDevice() {
        try {
            if (Core.graphics == null || Core.graphics.getGLVersion() == null) {
                deviceSupported = false;
                deviceReason = "ss-settings.device.gl";
            }
        } catch (Throwable t) {
            // 检测失败时保守处理，不强行开启
            deviceSupported = false;
            deviceReason = "ss-settings.device.gl";
        }
    }

    public static boolean enabled() {
        return deviceSupported && Core.settings.getBool(KEY_ENABLE, false) && getLevel() != LEVEL_OFF;
    }

    public static boolean cullOffscreen() {
        return Core.settings.getBool(KEY_CULL, true);
    }

    public static boolean autoDisable() {
        return Core.settings.getBool(KEY_AUTO_DISABLE, true);
    }

    public static boolean showDebug() {
        return Core.settings.getBool(KEY_DEBUG, false);
    }

    public static int getLevel() {
        int l = Core.settings.getInt(KEY_LEVEL, LEVEL_MEDIUM);
        return (l < 0 || l > 3) ? LEVEL_MEDIUM : l;
    }

    public static int maxConcurrent() {
        switch (getLevel()) {
            case LEVEL_LOW:    return 2;
            case LEVEL_MEDIUM: return 6;
            case LEVEL_HIGH:   return 12;
            default:           return 0;
        }
    }

    public static float maxRadius() {
        switch (getLevel()) {
            case LEVEL_LOW:    return 80f;
            case LEVEL_MEDIUM: return 160f;
            case LEVEL_HIGH:   return 320f;
            default:           return 0f;
        }
    }

    public static float maxStrength() {
        switch (getLevel()) {
            case LEVEL_LOW:    return 0.5f;
            case LEVEL_MEDIUM: return 1f;
            case LEVEL_HIGH:   return 2f;
            default:           return 0f;
        }
    }

    public static int maxPerFrame() {
        switch (getLevel()) {
            case LEVEL_LOW:    return 1;
            case LEVEL_MEDIUM: return 2;
            case LEVEL_HIGH:   return 5;
            default:           return 0;
        }
    }
}