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
    public static final String KEY_SHAPE = "squad-shape";
    public static final String KEY_RADIUS = "squad-radius";
    public static final String KEY_PRESETS = "squad-presets";

    // 拾取/投放模式：0=按序补给/逐个丢（默认，玩家描述的方式），1=一次性全员
    public static final int MODE_SEQUENTIAL = 0;
    public static final int MODE_ALL = 1;

    // 吸附规则枚举值
    public static final int RULE_SAME = 0;      // 同类优先
    public static final int RULE_NEAREST = 1;   // 距离最近
    public static final int RULE_WHITELIST = 2; // 类型白名单
    public static final int RULE_HEALTH = 3;    // 血量/等级最高

    // 队形枚举值（已去掉方阵，保留圆环与纵队）
    public static final int SHAPE_RING = 0;    // 圆环：均匀环绕玩家
    public static final int SHAPE_COLUMN = 1;  // 纵队：像修复/重建 AI 一样贴身跟随玩家

    public static final int MAX_FOLLOW_DIST_DEF = 260; // 世界单位
    public static final int RADIUS_DEF = 40, RADIUS_MIN = 20, RADIUS_MAX = 100;
    /** 预设槽位数量。 */
    public static final int presetCount = 5;

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
        table.sliderPref(KEY_SHAPE, SHAPE_RING, SHAPE_RING, SHAPE_COLUMN, 1, SquadSettings::shapeName);
        table.sliderPref(KEY_RADIUS, RADIUS_DEF, RADIUS_MIN, RADIUS_MAX, 5, i -> i + " px");
    }

    public static String shapeName(int shape){
        switch(shape){
            case SHAPE_COLUMN: return Core.bundle.get("squad.shape.column");
            default: return Core.bundle.get("squad.shape.ring");
        }
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
    public static int shape(){ return Core.settings.getInt(KEY_SHAPE, SHAPE_RING); }
    public static void setShape(int shape){ Core.settings.put(KEY_SHAPE, shape); }
    public static float radius(){ return Core.settings.getInt(KEY_RADIUS, RADIUS_DEF); }
    public static void setRadius(int radius){ Core.settings.put(KEY_RADIUS, radius); }

    /** 一套编队预设：成员 id 列表 + 队形 + 是否跟随。 */
    public static class Preset{
        public int[] ids = new int[0];
        public int shape = SHAPE_RING;
        public boolean follow = true;

        public boolean valid(){ return ids.length > 0; }
    }

    /** 读取第 index 套预设的原始字符串（空串 = 未保存）。 */
    public static String presetRaw(int index){
        if(index < 0 || index >= presetCount) return "";
        String all = Core.settings.getString(KEY_PRESETS, "");
        if(all == null || all.isEmpty()) return "";
        String[] parts = all.split(";");
        return index < parts.length ? parts[index] : "";
    }

    /** 写入第 index 套预设的原始字符串。 */
    public static void savePresetRaw(int index, String raw){
        if(index < 0 || index >= presetCount) return;
        String all = Core.settings.getString(KEY_PRESETS, "");
        String[] parts = (all == null || all.isEmpty()) ? new String[presetCount] : all.split(";");
        if(parts.length < presetCount){
            String[] grown = new String[presetCount];
            System.arraycopy(parts, 0, grown, 0, parts.length);
            for(int i = parts.length; i < presetCount; i++) grown[i] = "";
            parts = grown;
        }
        parts[index] = raw == null ? "" : raw;
        Core.settings.put(KEY_PRESETS, String.join(";", parts));
    }

    /** 解析预设字符串。格式：shape|follow|id1,id2,id3 */
    public static Preset parsePreset(String raw){
        Preset p = new Preset();
        if(raw == null || raw.isEmpty()) return p;
        String[] f = raw.split("\\|");
        if(f.length > 0) p.shape = parseInt(f[0], SHAPE_RING);
        if(f.length > 1) p.follow = "1".equals(f[1]);
        if(f.length > 2 && !f[2].isEmpty()){
            String[] parts = f[2].split(",");
            int[] ids = new int[parts.length];
            int n = 0;
            for(String s : parts){
                int v = parseInt(s, -1);
                if(v >= 0) ids[n++] = v;
            }
            p.ids = new int[n];
            System.arraycopy(ids, 0, p.ids, 0, n);
        }
        return p;
    }

    /** 把当前编队序列化成预设字符串。 */
    public static String buildPreset(int[] ids, int shape, boolean follow){
        StringBuilder sb = new StringBuilder();
        sb.append(shape).append('|').append(follow ? '1' : '0').append('|');
        for(int i = 0; i < ids.length; i++){
            if(i > 0) sb.append(',');
            sb.append(ids[i]);
        }
        return sb.toString();
    }

    private static int parseInt(String s, int def){
        try{
            return Integer.parseInt(s.trim());
        }catch(Exception e){
            return def;
        }
    }
}
