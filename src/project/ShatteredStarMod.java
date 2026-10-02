package project;

import arc.Events;
import arc.util.Log;
import mindustry.ai.UnitCommand;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.mod.Mod;
import mindustry.type.UnitType;
import project.ai.GuardAI;
import project.ai.HuntAI;
import project.ai.ProtectAI;
import project.ai.UnloadAI;
import project.blocks.MultiAssembler;
import project.content.ProtectModeRegistry;
import project.content.SSPlanets;
import project.content.SSUnitType;
import project.graphics.DistortionRenderer;

import static mindustry.Vars.content;

public class ShatteredStarMod extends Mod {

    public static UnitCommand huntCommand;
    public static UnitCommand protectCommand;
    public static UnitCommand guardCommand;
    public static UnitCommand enterTransportCommand;
    public static UnitCommand unloadCommand;

    public static UnitCommand globalFactoryCommand = null;
    public static boolean globalFactoryCommandEnabled = false;

    public ShatteredStarMod() {
        Log.info("Loaded ShatteredStarMod constructor.");
    }

    @Override
    public void loadContent() {
        Log.info("Loading content.");

        SSPlanets.load();
        ProtectModeRegistry.load();
        new MultiAssembler("multi-assembler");
        SSUnitType.load();
    }

    @Override
    public void init() {
        Log.info("Initializing ShatteredStarMod.");

        // 0. 实验性渲染（你已完成）
        SSSettings.load();
        DistortionRenderer.init();

        // 0.5. 载荷 UI
        project.ui.TransportPayloadUI.init();

        // 0.7. 挂载单位渲染
        project.render.AttachedUnitRenderer.init();

        // 0.8. 挂载蜘蛛腿折叠
        project.content.units.LegShrinkHandler.init();

        // 1. 指令
        huntCommand = new UnitCommand("ss-hunt", "right", u -> new HuntAI());
        huntCommand.drawTarget = false;
        huntCommand.switchToMove = false;
        huntCommand.resetTarget = false;
        huntCommand.exactArrival = false;

        protectCommand = new UnitCommand("ss-protect", "effect", u -> new ProtectAI());
        protectCommand.drawTarget = true;
        protectCommand.switchToMove = false;
        protectCommand.resetTarget = false;
        protectCommand.exactArrival = false;
        protectCommand.snapToBuilding = true;

        guardCommand = new UnitCommand("ss-guard", "commandRetreat", u -> new GuardAI());
        guardCommand.drawTarget = true;
        guardCommand.switchToMove = false;
        guardCommand.resetTarget = false;
        guardCommand.exactArrival = false;
        guardCommand.snapToBuilding = false;

        enterTransportCommand = new UnitCommand("ss-enter-transport", "upOpen",
            u -> new project.ai.EnterTransportAI());
        enterTransportCommand.drawTarget = true;
        enterTransportCommand.switchToMove = false;
        enterTransportCommand.resetTarget = false;
        enterTransportCommand.exactArrival = false;

        unloadCommand = new UnitCommand("ss-unload", "downOpen", u -> new UnloadAI());
        unloadCommand.drawTarget = false;
        unloadCommand.switchToMove = false;
        unloadCommand.resetTarget = false;
        unloadCommand.exactArrival = false;

        // 2. 注册
        try {
            register(huntCommand);
            register(protectCommand);
            register(guardCommand);
            register(enterTransportCommand);
            register(unloadCommand);
        } catch (Throwable t) {
            Log.err("Failed to register commands", t);
        }

        // 3. 给单位加指令
        int count = 0;
        for (UnitType type : content.units()) {
            if (type == null) continue;
            if (type.internal) continue;
            if (type.commands.isEmpty()) continue;

            if (!type.weapons.isEmpty() && !type.commands.contains(huntCommand)) {
                type.commands.add(huntCommand);
            }
            if (!type.commands.contains(protectCommand)) type.commands.add(protectCommand);
            if (!type.commands.contains(guardCommand)) type.commands.add(guardCommand);

            if (!type.flying && !type.commands.contains(enterTransportCommand)) {
                type.commands.add(enterTransportCommand);
            }

            if (type instanceof project.content.units.CarrierUnitType) {
                if (!type.commands.contains(unloadCommand)) {
                    type.commands.add(unloadCommand);
                }
            }

            count++;
        }
        Log.info("Registered commands to " + count + " unit types.");

        // 4. 全局出厂指令
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

    private static void register(UnitCommand cmd) {
        if (content.unitCommands().indexOf(cmd, true) == -1) {
            content.unitCommands().add(cmd);
        }
    }
}