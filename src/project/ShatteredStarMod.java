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
import project.content.SSUnitType;
import project.graphics.DistortionRenderer;
import project.graphics.DustRenderer;
import project.modules.ModuleSystem;

import static mindustry.Vars.content;

public class ShatteredStarMod extends Mod {

    /** 必须与 mod.hjson 里的 name 保持一致。 */
    public static final String MOD_NAME = "ss";

    /**
     * 返回 mod 贴图在 atlas 里的真实名字，即 {@code "ss-" + name}。
     *
     * <p>mod 的 sprites/ 打包时，Mindustry 会给每个 region 名拼上 mod 名做前缀
     * （{@code Mods.packSprites} 里 {@code prefix = mod.name + "-"}），
     * 因为所有 mod 的图最终进同一张全局 atlas，不加前缀必然重名。
     *
     * <p>单位、方块这类 {@code Content} 不用管：{@code ContentLoader.transformName()}
     * 会把内容名一起改写成 {@code ss-dagger-at}，查找时正好对上——语言包键写成
     * {@code unit.ss-dagger-at.name} 能生效也是这个原因。
     *
     * <p>但 {@code Weapon} / {@code RegionPart} 不是 Content，名字保持原样，
     * {@code Weapon.load()} 里又是 {@code Core.atlas.find(name)} 直查，
     * 所以这类引用必须手动拼前缀，否则拿到的是 error region。
     */
    public static String name(String name) {
        return MOD_NAME + "-" + name;
    }

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

        ProtectModeRegistry.load();
        new MultiAssembler("multi-assembler");
        SSUnitType.load();
    }

    @Override
    public void init() {
        Log.info("Initializing ShatteredStarMod.");

        // 0. 实验性渲染
        SSSettings.load();
        DistortionRenderer.init();
        // 0.05. 扬尘渲染器：尘团的层号 / 出生坐标由它统一管
        DustRenderer.init();
        // 0.06. 模块受损系统：受击结算 / 降级应用 / 维修 API
        ModuleSystem.init();
        // 0.1. 星辉跃迁设置
        SSWarpSettings.load();
        // 0.2. 跃迁音效（stAstroWarp1 / 2）
        project.abilities.WarpAbility.loadSounds();
        // ============ 0.9. 扭曲配置 ============
        project.content.DistortionConfig.load();
        project.content.DistortionConfig.apply();

        // 0.5. 载荷 UI
        project.ui.TransportPayloadUI.init();

        // 0.8. 挂载单位更新（载具死亡释放）
        project.content.units.AttachedUpdateHandler.init();

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