package mindustry.ai;

import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/**
 * 编队物资联动：玩家从储存建筑取货 / 向储存建筑存货时，编队单位同步动作。
 *
 * 取货（两种模式）：
 * - 按序（MODE_SEQUENTIAL，默认）：取货接收者在 玩家→单位1..6 之间轮转，跳过已装满/无效的实体。
 *   玩家连续点不同物品，即可让 玩家+6单位 各带一种，一次搭载 7 种不同物质。
 * - 全员（MODE_ALL）：玩家自己正常取货，同时所有空单位各拿一份同种物品。
 *
 * 存货（两种模式）：玩家向建筑存货时，把编队单位身上的物品投进就近储存。
 * - 按序（MODE_SEQUENTIAL，默认）：每次投一个带货单位。
 * - 全员（MODE_ALL）：所有带货单位一起投。
 *
 * 说明：本联动依赖本地轮转状态与本地编队列表，仅单机/本地玩家编队生效；网络改动走服务端权威 Call。
 */
public class SquadInventoryLink{
    /** 取货轮转游标：0=玩家，1..N=编队槽位。 */
    private static int withdrawCursor = 0;
    /** 存货轮转游标：0..N-1 对应编队槽位。 */
    private static int depositCursor = 0;

    // ==================== 取货 ====================

    /**
     * 按序模式：返回本次取货应接收物品的单位 id；返回 -1 表示给玩家自己。
     * 会跳过已装满或无效的实体。非按序模式一律返回 -1（玩家自己取）。
     * 供 BlockInventoryFragment 在客户端计算 amount 与决定调用哪个 Call。
     */
    public static int nextWithdrawReceiver(Item item){
        if(item == null) return -1;
        if(SquadManager.squad.size == 0 || SquadSettings.pickupMode() != SquadSettings.MODE_SEQUENTIAL) return -1;

        int n = SquadManager.squad.size;
        for(int k = 0; k <= n; k++){
            int slot = (withdrawCursor + k) % (n + 1);
            if(slot == 0){
                //玩家自己：能装下该物品才轮到玩家
                if(player != null && !player.dead() && player.unit() != null && player.unit().maxAccepted(item) > 0) return -1;
            }else{
                Unit u = squadUnit(slot - 1);
                if(u != null && u.isValid() && !u.dead() && u.maxAccepted(item) > 0) return u.id;
            }
        }
        return -1;
    }

    /** 按序模式取货成功后推进游标，使下一次轮到下一个接收者。非按序模式不处理。 */
    public static void advanceWithdraw(int receiverId){
        int n = SquadManager.squad.size;
        if(n == 0 || SquadSettings.pickupMode() != SquadSettings.MODE_SEQUENTIAL) return;
        if(receiverId == -1){
            withdrawCursor = 1 % (n + 1);
        }else{
            for(int i = 0; i < n; i++){
                Unit u = squadUnit(i);
                if(u != null && u.id == receiverId){
                    withdrawCursor = (i + 2) % (n + 1); // i 对应槽位 i+1，下一个是 i+2
                    break;
                }
            }
        }
    }

    /** 全员模式：玩家自己取货成功后，所有空单位各拿一份。按序模式不处理（由 UI 层轮转接管）。 */
    public static void onWithdraw(Player player, Building build, Item item, int amount){
        if(SquadManager.squad.size == 0 || player == null) return;
        if(SquadSettings.pickupMode() != SquadSettings.MODE_ALL) return;
        if(build == null || item == null || amount <= 0) return;

        for(int i = 0; i < SquadManager.squad.size; i++){
            Unit u = squadUnit(i);
            if(canAccept(u, item)) withdrawTo(u, build, item, amount);
        }
    }

    // ==================== 存货 ====================

    /** 玩家向 build 存货成功后调用：编队单位也把身上物品投进就近储存。 */
    public static void onDeposit(Player player, Building build){
        if(SquadManager.squad.size == 0 || player == null || player.dead()) return;

        if(SquadSettings.depositMode() == SquadSettings.MODE_ALL){
            for(int i = 0; i < SquadManager.squad.size; i++){
                depositFrom(squadUnit(i));
            }
        }else{
            int n = SquadManager.squad.size;
            for(int k = 0; k < n; k++){
                int idx = (depositCursor + k) % n;
                if(depositFrom(squadUnit(idx))){
                    depositCursor = (idx + 1) % n;
                    break;
                }
            }
        }
    }

    /** 全员卸货：所有带货编队单位把物品投进就近储存（供 squadUnloadAll 远程调用）。 */
    public static void unloadAll(){
        for(int i = 0; i < SquadManager.squad.size; i++){
            depositFrom(squadUnit(i));
        }
    }

    // ==================== 通用 ====================

    private static Unit squadUnit(int idx){
        if(idx < 0 || idx >= SquadManager.squad.size) return null;
        return Groups.unit.getByID(SquadManager.squad.get(idx));
    }

    /** 单位能否接受该物品：单位物品栏只有一格，空手或已装同种才行。 */
    private static boolean canAccept(Unit u, Item item){
        return u != null && u.isValid() && !u.dead() && u.maxAccepted(item) > 0;
    }

    /** 让单位从 build 取走 item（走服务端权威 Call.takeItems，扣建筑、加单位、带特效）。 */
    private static void withdrawTo(Unit u, Building build, Item item, int amount){
        int amt = Math.min(u.maxAccepted(item), Math.max(1, amount));
        Call.takeItems(build, item, amt, u);
    }

    /** 把一个单位身上的物品投进就近储存，返回是否真的投了。 */
    private static boolean depositFrom(Unit u){
        if(u == null || !u.isValid() || u.dead() || u.stack == null || u.stack.amount <= 0) return false;
        Item item = u.item();
        if(item == null) return false;

        Building storage = findStorage(u, item);
        if(storage == null) return false;

        int accepted = storage.acceptStack(item, u.stack.amount, u);
        if(accepted <= 0) return false;

        Call.transferItemTo(u, item, accepted, u.x, u.y, storage);
        return true;
    }

    /** 找离单位最近的、能接收该物品的本方储存（核心或仓库）。 */
    private static @Nullable Building findStorage(Unit u, Item item){
        Building best = null;
        float bestDst = Float.MAX_VALUE;

        Building core = u.team.core();
        if(core != null && core.interactable(u.team) && core.acceptStack(item, 1, u) > 0){
            best = core;
            bestDst = u.dst2(core);
        }

        Seq<Building> stores = indexer.getFlagged(u.team, BlockFlag.storage);
        if(stores != null){
            for(int i = 0; i < stores.size; i++){
                Building b = stores.get(i);
                if(b == null || !b.interactable(u.team) || b.acceptStack(item, 1, u) <= 0) continue;
                float d = u.dst2(b);
                if(d < bestDst){
                    best = b;
                    bestDst = d;
                }
            }
        }
        return best;
    }

    /** 编队变更/解散时重置轮转状态。 */
    public static void reset(){
        withdrawCursor = 0;
        depositCursor = 0;
    }
}
