package project;

import arc.Events;
import arc.util.Log;
import mindustry.ai.UnitCommand;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.mod.Mod;
import mindustry.type.UnitType;
import project.ai.HuntAI;
import project.blocks.MultiAssembler;

import static mindustry.Vars.content;

public class ShatteredStarMod extends Mod {

    public static UnitCommand huntCommand;
    public static UnitCommand dogfightCommand;

    /** 全局出厂指令（预设值） */
    public static UnitCommand globalFactoryCommand = null;
    /** 全局出厂指令开关。默认关闭 */
    public static boolean globalFactoryCommandEnabled = false;

    public ShatteredStarMod() {
        Log.info("Loaded ShatteredStarMod constructor.");
    }

    @Override
    public void loadContent() {
        Log.info("Loading content.");
        new MultiAssembler("multi-assembler");
    }

    @Override
    public void init() {
        Log.info("Initializing ShatteredStarMod.");

        // ============ 1. 创建指令 ============
        huntCommand = new UnitCommand("ss-hunt", "right", u -> new HuntAI());
        huntCommand.drawTarget = false;
        huntCommand.switchToMove = false;
        huntCommand.resetTarget = false;
        huntCommand.exactArrival = false;

        dogfightCommand = new UnitCommand("ss-dogfight", "rightOpen", u -> {
            HuntAI ai = new HuntAI();
            ai.forceDogfighter = true;
            return ai;
        });
        dogfightCommand.drawTarget = false;
        dogfightCommand.switchToMove = false;
        dogfightCommand.resetTarget = false;
        dogfightCommand.exactArrival = false;

        // ============ 2. 注册到 content.unitCommands() ============
        try {
            if (content.unitCommands().indexOf(huntCommand, true) == -1) {
                content.unitCommands().add(huntCommand);
            }
            if (content.unitCommands().indexOf(dogfightCommand, true) == -1) {
                content.unitCommands().add(dogfightCommand);
            }
        } catch (Throwable t) {
            Log.err("Failed to register commands to content list", t);
        }

        // ============ 3. 给所有支持指挥的单位加上这两个指令 ============
        int count = 0;
        for (UnitType type : content.units()) {
            if (type == null) continue;
            if (type.internal) continue;
            if (type.weapons.isEmpty()) continue;
            if (type.commands.isEmpty()) continue;

            if (!type.commands.contains(huntCommand)) {
                type.commands.add(huntCommand);
            }
            if (!type.commands.contains(dogfightCommand)) {
                type.commands.add(dogfightCommand);
            }
            count++;
        }
        Log.info("Registered ss-hunt / ss-dogfight to " + count + " unit types.");

        // ============ 4. 全局：原版工厂产出的单位应用出厂指令 ============
        Events.on(UnitCreateEvent.class, e -> {
            if (!globalFactoryCommandEnabled) return;
            if (globalFactoryCommand == null) return;
            if (e.spawner == null) return;
            if (e.unit == null) return;
            if (!e.unit.isCommandable()) return;
            if (!e.unit.type.commands.contains(globalFactoryCommand)) return;

            e.unit.command().command(globalFactoryCommand);
        });
    }
}