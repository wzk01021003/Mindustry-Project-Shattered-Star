package project;

import arc.Events;
import arc.util.Log;
import arc.util.Time;
import mindustry.ai.UnitCommand;
import mindustry.ai.types.CommandAI;
import mindustry.ai.types.FlyingAI;
import mindustry.ai.types.GroundAI;
import mindustry.entities.units.AIController;
import mindustry.entities.units.UnitController;   // ← 正确的包
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.mod.Mod;
import mindustry.type.UnitType;
import project.ai.AITuning;
import project.ai.FastFlyingAI;
import project.ai.FastGroundAI;
import project.ai.HuntAI;
import project.ai.ProtectAI;
import project.blocks.MultiAssembler;
import project.content.ProtectModeRegistry;

import static mindustry.Vars.content;

public class ShatteredStarMod extends Mod {

    public static UnitCommand huntCommand;
    public static UnitCommand dogfightCommand;
    public static UnitCommand protectCommand;

    public static UnitCommand globalFactoryCommand = null;
    public static boolean globalFactoryCommandEnabled = false;
    public static boolean smartAIEnabled = true;

    public ShatteredStarMod() {
        Log.info("Loaded ShatteredStarMod constructor.");
    }

    @Override
    public void loadContent() {
        Log.info("Loading content.");
        ProtectModeRegistry.load();
        new MultiAssembler("multi-assembler");
    }

    @Override
    public void init() {
        Log.info("Initializing ShatteredStarMod.");

        AITuning.init();

        // ============ 1. 指令 ============
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

        protectCommand = new UnitCommand("ss-protect", "effect", u -> new ProtectAI());
        protectCommand.drawTarget = true;
        protectCommand.switchToMove = false;
        protectCommand.resetTarget = false;
        protectCommand.exactArrival = false;
        protectCommand.snapToBuilding = true;

        // ============ 2. 注册 ============
        try {
            register(huntCommand);
            register(dogfightCommand);
            register(protectCommand);
        } catch (Throwable t) {
            Log.err("Failed to register commands to content list", t);
        }

        // ============ 3. 加指令 ============
        int count = 0;
        for (UnitType type : content.units()) {
            if (type == null) continue;
            if (type.internal) continue;
            if (type.commands.isEmpty()) continue;

            if (!type.weapons.isEmpty()) {
                if (!type.commands.contains(huntCommand)) type.commands.add(huntCommand);
                if (!type.commands.contains(dogfightCommand)) type.commands.add(dogfightCommand);
            }
            if (!type.commands.contains(protectCommand)) type.commands.add(protectCommand);
            count++;
        }
        Log.info("Registered commands to " + count + " unit types.");

        // ============ 4. 全局出厂指令 ============
        Events.on(UnitCreateEvent.class, e -> {
            if (!globalFactoryCommandEnabled) return;
            if (globalFactoryCommand == null) return;
            if (e.spawner == null) return;
            if (e.unit == null) return;
            if (!e.unit.isCommandable()) return;
            if (!e.unit.type.commands.contains(globalFactoryCommand)) return;

            e.unit.command().command(globalFactoryCommand);
        });

        // ============ 5. 替换原版 AI 为快速索敌版 ============
        Events.on(UnitCreateEvent.class, e -> {
            if (!smartAIEnabled) return;
            if (e.unit == null) return;

            Time.run(1f, () -> {
                if (e.unit == null || !e.unit.isValid()) return;

                UnitController uc = e.unit.controller();
                if (!(uc instanceof AIController)) return;
                AIController c = (AIController) uc;

                if (c instanceof FastGroundAI) return;
                if (c instanceof FastFlyingAI) return;
                if (c instanceof HuntAI) return;
                if (c instanceof ProtectAI) return;
                if (c instanceof CommandAI) return;

                AIController replacement = null;
                if (c instanceof GroundAI) replacement = new FastGroundAI();
                else if (c instanceof FlyingAI) replacement = new FastFlyingAI();

                if (replacement != null) {
                    replacement.unit(e.unit);
                    e.unit.controller(replacement);
                }
            });
        });
    }

    private static void register(UnitCommand cmd) {
        if (content.unitCommands().indexOf(cmd, true) == -1) {
            content.unitCommands().add(cmd);
        }
    }
}