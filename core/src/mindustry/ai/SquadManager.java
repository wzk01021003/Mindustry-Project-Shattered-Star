package mindustry.ai;

import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.types.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;

import static mindustry.Vars.*;

/**
 * 玩家编队管理器（纯逻辑，网络同步交给 InputHandler 的 @Remote 方法）。
 * 负责：按设置挑选最多 6 个单位、把它们的控制器换成 SquadAI、维护队长与槽位、解除编队。
 */
public class SquadManager{
    public static final int maxUnits = 6;

    /** 本玩家当前编队成员的单位 id。 */
    public static final IntSeq squad = new IntSeq(maxUnits);

    /** 一键吸附：按当前设置挑选最多 6 个周围友军。返回被选中的单位 id。 */
    public static IntSeq autoPick(Unit playerUnit){
        IntSeq result = new IntSeq(maxUnits);
        if(playerUnit == null || playerUnit.dead()) return result;

        float range = 90f * Vars.tilesize; // 周围 90 格
        Seq<Unit> candidates = new Seq<>();
        for(Unit u : Groups.unit){
            if(u == playerUnit || u.dead() || u.team != playerUnit.team) continue;
            if(!u.isCommandable() && !(u.controller() instanceof SquadAI)) continue;
            if(u.dst(playerUnit) > range) continue;
            candidates.add(u);
        }

        int rule = SquadSettings.pickRule();
        Unit playerRef = playerUnit;

        switch(rule){
            case SquadSettings.RULE_NEAREST:
                candidates.sort(u -> u.dst2(playerRef));
                break;
            case SquadSettings.RULE_WHITELIST:
                Seq<String> wl = Seq.with(SquadSettings.whitelist().toLowerCase().split("\\s*[,，]\\s*"));
                wl.removeAll(s -> s == null || s.isEmpty());
                candidates.sort(u -> (wl.contains(u.type.name.toLowerCase()) ? 0 : 1) + u.dst2(playerRef) / 1e9f);
                candidates.removeAll(u -> !wl.contains(u.type.name.toLowerCase()) && wl.size > 0);
                break;
            case SquadSettings.RULE_HEALTH:
                // 血量/强度最高：按最大生命值排序（大单位通常更强）
                candidates.sort(u -> -u.maxHealth());
                break;
            case SquadSettings.RULE_SAME:
            default:
                // 同类优先：同型号排前，其次距离近
                candidates.sort(u -> (u.type == playerRef.type ? 0f : 1e6f) + u.dst2(playerRef));
                break;
        }

        int n = Math.min(maxUnits, candidates.size);
        for(int i = 0; i < n; i++) result.add(candidates.get(i).id);
        return result;
    }

    /** 把一组单位编入玩家的编队（服务器/客户端调用，实际控制器切换在 applySquad）。 */
    public static void setSquad(IntSeq ids){
        squad.clear();
        squad.addAll(ids);
    }

    /** 给单个单位套上 SquadAI 控制器，记录队长和槽位。 */
    public static void applySquad(Unit unit, Unit leader, int slot, int size){
        if(unit == null || unit.dead() || leader == null) return;
        SquadAI ai;
        if(unit.controller() instanceof SquadAI s){
            ai = s;
        }else{
            ai = new SquadAI();
            unit.controller(ai);
        }
        ai.leader = leader;
        ai.slotIndex = slot;
        ai.squadSize = size;
        ai.followAttack = SquadSettings.autoAttack();
        ai.command(UnitCommand.moveCommand);
    }

    /** 解除某单位的编队（还原成普通 CommandAI）。 */
    public static void removeSquad(Unit unit){
        if(unit == null) return;
        squad.removeValue(unit.id);
        if(unit.controller() instanceof SquadAI){
            unit.controller(new CommandAI());
        }
    }

    /** 清空当前玩家编队。 */
    public static void clear(){
        for(int i = 0; i < squad.size; i++){
            Unit u = Groups.unit.getByID(squad.get(i));
            if(u != null && u.controller() instanceof SquadAI){
                u.controller(new CommandAI());
            }
        }
        squad.clear();
    }

    /** 每帧维护：队长变了就重新指定，成员死了就清理。 */
    public static void update(Unit playerUnit){
        if(playerUnit == null || playerUnit.dead()){
            if(squad.size > 0) clear();
            return;
        }
        for(int i = squad.size - 1; i >= 0; i--){
            Unit u = Groups.unit.getByID(squad.get(i));
            if(u == null || u.dead()){
                squad.removeIndex(i);
                continue;
            }
            if(u.controller() instanceof SquadAI s){
                if(s.leader != playerUnit){
                    s.leader = playerUnit;
                    s.squadSize = squad.size;
                    s.slotIndex = i;
                }
            }
        }
    }
}
