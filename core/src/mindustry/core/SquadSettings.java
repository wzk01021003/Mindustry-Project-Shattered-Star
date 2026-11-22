package mindustry.core;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;

import static mindustry.Vars.*;

/** 玩家编队（Squad）设置项。 */
public class SquadSettings{
    public static final String KEY_ENABLED = "squad-enabled";
    public static final String KEY_AUTO_ATTACK = "squad-autoattack";
    public static final String KEY_FOLLOW_FIRE = "squad-followfire";
    public static final String KEY_PICK_RULE = "squad-pickrule";
    public static final String KEY_WHITELIST = "squad-whitelist";
    public static final String KEY_MAX_FOLLOW_DIST = "squad-maxdist";
    public static final String KEY_PICKUP_MODE = "squad-pickupmode";
    public static final String KEY_DEPOSIT_MODE = "squad-depositmode";

    // 拾取/投放模式：0=按序补给/逐个丢（默认，玩家描述的方式），1=一次性全员
    public static final int MODE_SEQUENTIAL = 0;
    public static final int MODE_ALL = 1;

    // 吸附规则枚举值
    public static final int RULE_SAME = 0;      // 同类优先
    public static final int RULE_NEAREST = 1;   // 距离最近
    public static final int RULE_WHITELIST = 2; // 类型白名单
    public static final int RULE_HEALTH = 3;    // 血量/等级最高

    public static final int MAX_FOLLOW_DIST_DEF = 260; // 世界单位

    private static boolean loaded;

    public static void load(){
        if(loaded) return;
        loaded = true;
        try{
            Vars.ui.settings.addCategory("@squad.category", "settings", SquadSettings::buildUI);
            refresh();
        }catch(Throwable t){
            Log.err("[squad] 无法注册编队设置分类", t);
        }
    }

    public static void refresh(){
        // 目前设置项都是即时读取的，这里预留给未来的缓存
    }

    private static void buildUI(SettingsTable table){
        table.checkPref(KEY_ENABLED, true);
        table.checkPref(KEY_AUTO_ATTACK, false);
        table.checkPref(KEY_FOLLOW_FIRE, true);
        table.sliderPref(KEY_PICK_RULE, RULE_SAME, RULE_SAME, RULE_HEALTH, 1, SquadSettings::ruleName);
        table.textPref(KEY_WHITELIST, "");
        table.sliderPref(KEY_MAX_FOLLOW_DIST, MAX_FOLLOW_DIST_DEF, 80, 600, 20, i -> i + " tiles");
        table.sliderPref(KEY_PICKUP_MODE, MODE_SEQUENTIAL, MODE_SEQUENTIAL, MODE_ALL, 1, SquadSettings::modeName);
        table.sliderPref(KEY_DEPOSIT_MODE, MODE_SEQUENTIAL, MODE_SEQUENTIAL, MODE_ALL, 1, SquadSettings::modeName);
    }

    public static String modeName(int mode){
        return mode == MODE_ALL ? Core.bundle.get("squad.mode.all") : Core.bundle.get("squad.mode.seq");
    }

    public static String ruleName(int rule){
        switch(rule){
            case RULE_NEAREST: return Core.bundle.get("squad.rule.nearest");
            case RULE_WHITELIST: return Core.bundle.get("squad.rule.whitelist");
            case RULE_HEALTH: return Core.bundle.get("squad.rule.health");
            default: return Core.bundle.get("squad.rule.same");
        }
    }

    public static boolean enabled(){ return Core.settings.getBool(KEY_ENABLED, true); }
    public static boolean autoAttack(){ return Core.settings.getBool(KEY_AUTO_ATTACK, false); }
    public static boolean followFire(){ return Core.settings.getBool(KEY_FOLLOW_FIRE, true); }
    public static int pickRule(){ return Core.settings.getInt(KEY_PICK_RULE, RULE_SAME); }
    public static String whitelist(){ return Core.settings.getString(KEY_WHITELIST, ""); }
    public static float maxFollowDistance(){ return Math.max(60, Core.settings.getInt(KEY_MAX_FOLLOW_DIST, MAX_FOLLOW_DIST_DEF)); }
    public static int pickupMode(){ return Core.settings.getInt(KEY_PICKUP_MODE, MODE_SEQUENTIAL); }
    public static int depositMode(){ return Core.settings.getInt(KEY_DEPOSIT_MODE, MODE_SEQUENTIAL); }
}
