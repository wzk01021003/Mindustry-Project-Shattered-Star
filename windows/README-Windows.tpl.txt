Mindustry: 碎星计划 / Project Shattered Star  —  Windows 版
==========================================================

本体版本：Mindustry v160.7（v8 Build 160.7，官方源码，已修改）
修改内容：1)「跃迁」系统已并入原版引擎，原版飞行单位「星辉 / Flare」自带主动跃迁；
          2)「部件系统」已并入原版引擎，星辉与尖刀（Dagger）带可独立损坏的部件；
          3) 建造器可以像铁锈战争那样修理己方单位与部件；
          4) 敌方 AI 三档智能度（原版 / 智能 / 精锐），入口在难度与自定义规则里。
          不是外挂 Mod，也没有额外 mod jar —— 代码直接编译进游戏本体。
平台：Windows 10/11 64 位    运行时：内置 Java 17，无需另装 JDK

【怎么玩】
双击本目录下的 Mindustry.exe。游戏本体与官方桌面版一致（UI、玩法、单位、
战役、科技树、电力、液体、蓝图、多人联机全都一样），唯一改动是：
原版「星辉 / Flare」可以主动跃迁 —— 选中它，按住 Alt 蓄力，松开后瞬间投送到
鼠标位置。设置里新增「跃迁」分类，可调总开关、距离、音量、特效与准星。

【部件系统】
把鼠标停在任意单位上，右下角的信息卡里，结构血条下面会逐件列出该单位的部件耐久条（条上带部件名）。
颜色由绿到红表示状态，哪件被打坏、还剩多少一眼可见。

四类部件分别影响：
  移动部件  → 移动速度
  装甲部件  → 护甲与最大生命值
  武器部件  → 射速与冷却
  能力部件  → 该能力是否生效

部件被打坏后按三种方式衰减（每件部件各自配置）：
  固定值  —— 耐久高于阈值时给满额属性，跌破阈值直接掉到下限
  曲线    —— 按耐久比例做幂曲线平滑衰减
  禁用    —— 只要出现损伤就完全失效，修满才恢复

目前挂载情况：
  星辉 / Flare：推进器、轻量化骨架、供弹机构、能力核心（特殊修复）
  尖刀 / Dagger：履带、复合装甲板、精密枪管

能力核心是「特殊修复」部件：默认两把修理枪都碰不了，只能让单位脱战 10 秒自行充能恢复。

打开核心数据库点开任意单位，会多出「部件」与「可修复性」两块：
  部件    —— 逐件列出类型、耐久、衰减方式，以及建造枪 / 修复枪能不能修它（✔ 可修 / ✘ 不可修 / ⓘ 由接口决定）
  可修复性 —— 汇总该单位在两把枪下各有多少部件可修；没有部件的单位显示「仅结构血量」

【修理：建造枪 / 修复枪】
建造枪是建造单位（巨像、多面体）手上的那把，本职是盖建筑，顺手也能修单位；
修复枪只有灾星头上那对治疗炮算数，是专门的修理工具，速度是建造枪的 2 倍。

把准星指向己方受损单位，出现修理光标后按住左键或右键即可持续修复，
顺序固定为先补结构血量、再修部件。修理速度和单位的建造速度无关，只看工具，
而且全是绝对数值、不随目标血量缩放：建造枪每秒补 10 结构血量、10 部件耐久；
修复枪分别是 20 和 20（修一台大机体要好几分钟，不会一秒钟回一大截）。

修理光标只在准星落进单位碰撞箱（hitSize 那个圆）时才出现：
以前是「范围内最近的单位」都会被吸住，隔着一段距离就冒出光标，看着像自动锁定。
这两个数字都在 Components 顶部（repairHullRate / repairComponentRate / repairToolMultiplier），
改一处全局生效。想按「一把枪一套速度」来调，就直接把数值挂在武器上——
建造枪是 BuildWeapon，修复枪是带 repairTool 标记的武器（灾星那门 RepairBeamWeapon），
优先级是「武器自填 > 单位整覆盖 > 默认档」：

    UnitTypes.mega.weapons.first().repairToolSpeed = new RepairSpeed(60f, 40f);   // 这把枪每秒 60 结构、40 部件

    UnitTypes.vela.weapons.get(1).repairTool = true;                 // 标成修复枪，不填数值就走默认修复枪档

    UnitTypes.vela.weapons.get(1).repairToolSpeed = new RepairSpeed(){   // 按目标动态算
        @Override public float healthRate(Unit self, Unit target){
            return target != null && target.healthf() < 0.3f ? 80f : 40f;
        }
    };

    UnitTypes.mega.repairSpeed = new RepairSpeed(40f, 30f);          // 实在想整单位统一改，也有这个入口

建造枪额外支持「自动锁定」指令：下达后不必瞄准，会自动挑选范围内的受伤单位。
默认挑绝对血量最少的，也可以换成护甲最高的、或离自己最近的：

    Vars.control.input.orderAutoRepair();                                    // 默认：血量最少
    Vars.control.input.orderAutoRepair(mindustry.components.RepairPriority.highestArmor);
    Vars.control.input.orderAutoRepair(mindustry.components.RepairPriority.nearest);
    Vars.control.input.cancelAutoRepair();                                   // 取消，回到手动瞄准

修复枪（灾星）纯手动瞄准，不带自动锁定；建造枪的单位指令里才有自动锁定。

特殊修复的部件（目前是星辉的能力核心）默认谁都修不了，只能脱战 10 秒自行充能。
要让某类修理者能修，给部件挂一个判定接口：

    new UnitComponent("ability-core", ComponentKind.ability, 50f)
        .special(RepairHandler.regen(10f))
        .repairCheck((repairer, component) -> repairer.type.repairTool);     // 只放行修复枪

如果你自己想加载其他社区 Mod，把它们放进：
    %APPDATA%\Mindustry\mods\
即可，不会和内置的碎星计划冲突。
存档、设置、蓝图、截图都在 %APPDATA%\Mindustry\，卸载游戏不会删除。

【敌方 AI 智能度】
新开一局时（选地图 → 自定义规则 → 波次），或者战役的「难度」界面，
多了一栏「敌方 AI 智能度」，三档：

  原版 —— 和官方一致：谁近打谁，残血不跑。
  智能 —— 索敌间隔从 90 tick 缩到 48；按「距离 + 残血 + 是否在打我 + 有没有武器」
          打分挑目标；附近友军锁定的目标会跟着打（集火）；血量低于 28% 且附近
          确实有敌人时后撤。
  精锐 —— 索敌间隔 24 tick；血量低于 40% 就后撤；会横移闪避直冲自己的子弹；
          地面单位寻路常开绕行，不再一波挤成一条线。

后撤的走法：优先回自家核心，波次兵没有核心就退回出生点，都没有才朝远离威胁的方向退。
移动时会挑真正能走的方向（会绕开墙，不会一头撞上去）。
一次后撤最多持续 8 秒，之后会重新进攻，不会一直跑；血量回到一半以上可以再撤。

只影响敌方队伍，玩家自己的部队保持原版手感。默认「原版」，想加难度就往上调。
设置会随存档保存，读档后仍然生效。


【两种下载文件的区别】
- Mindustry-ShatteredStar-Windows-x64-Setup.exe：安装版，双击后一路下一步，
  创建桌面/开始菜单快捷方式与卸载程序。
- Mindustry-ShatteredStar-Windows-x64-Portable.zip：绿色版，解压到任意目录，
  双击 Mindustry.exe 即玩，不写注册表，删目录即卸载。
※ 绿色版里 Mindustry.exe、Mindustry.jar、jre\ 必须放在一起，
  不能只拷单个 exe。SHA256SUMS.txt 可用来校验文件完整性。

【启动器参数（命令行）】
Mindustry.exe --console           用 java.exe 启动，控制台可见 JVM 日志
Mindustry.exe --selftest          写出 launcher-selftest.txt（jre / jar 目录 / 完整命令行），不启动游戏
其余参数（如 -width 1280 -height 720 -debug）原样透传给 Mindustry。

【排错】
- 双击没反应：运行 Debug-Console.bat 看日志，或检查 %APPDATA%\Mindustry\last_log.txt。
- 找不到 Java 运行时：jre\bin\javaw.exe 丢失；也可装 Java 17 后设 JAVA_HOME，
  或用环境变量 SS_JAVA 指向任意 javaw.exe。
- 跃迁没反应：确认单位是「星辉 / Flare」；在「设置 → 跃迁」里检查总开关与距离。
- 显卡/OpenGL 问题：Mindustry v8 使用 SDL + OpenGL，可加 -gl 2.1 或 -compatibilityGl。
- 若安装路径含中文导致异常，把游戏放到纯英文路径。
- exe 未签名，首次运行可能被 SmartScreen 拦，点「更多信息 → 仍要运行」。

【开源协议 — 重要】
本包的 Mindustry 游戏本体来自 Anuken/Mindustry v160.7（GPL-3.0）。随包的
LICENSE、NOTICE、SOURCES.md 必须保留。把这个包发给别人时，需要同时提供
对应源码（本仓库即为 Mod 源码，本体源码地址见 SOURCES.md），且不得改成闭源
或限制别人再分发。仅自己本地使用则不产生公开义务。
「Mindustry」名称与商标归原权利人所有；本作品为非官方、非商业的同人衍生作品。

【自己重新打包】
在装有 gcc-mingw-w64-x86-64、nsis、p7zip-full、Python Pillow、JDK 17 的 Linux 上：
    SS_NO_MOD=1 bash windows/build-exe.sh  # 跃迁已并入原版源码，直接用构建出的 Mindustry.jar
脚本会自动下载缺失的 Windows JRE 17，交叉编译启动器，并打出 exe / zip。
内置模式需先准备好含「碎星计划」源码的 Mindustry.jar（来自本仓库源码构建）。
详见 windows/README.md。本仓库的 .github/workflows/release-windows.yml 就是
在 GitHub Actions 上自动执行同一套脚本并发布 Release。
