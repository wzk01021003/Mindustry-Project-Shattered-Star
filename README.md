# 像素工厂：碎星计划 / Mindustry: Project Shattered Star

> 作者 **A4PAPER（A4纸）** 的 Mindustry Java Mod。本文对应仓库当前代码（`mod.hjson` 版本仍为 **v0.2.0**，跃迁与日蚀复刻尚未 bump 版本号）。

- 英文名：`Project Shattered Star`
- 中文名：像素工厂：碎星计划
- 作者：`A4PAPER`
- 版本：`0.2.0`
- 平台要求：Mindustry `minGameVersion 160`（v8 系列）
- 仓库：`https://github.com/wzk01021003/Mindustry-Project-Shattered-Star`

---

## 剧情 / 背景

> 以下内容整理自 `mod.hjson` 的剧情描述。

我们攻占了赤潮阵营的行星发射终端，在残骸中发现了他们的绝密研究资料——赛普洛斯的资源分布与区块扩张情报。原来赤潮阵营早已通过行星际加速器将主力全部发射到新星球**赛普洛斯**，妄图凭借其丰富资源建立强大军队与后勤，进而扩张至全星系。

我们派出 AI 最精锐的部队去捣毁其基地。然而近地轨道上有**未知单位**巡逻，终代核心被击落，随行单位全部被歼灭。幸而当时暴风雨严重，未知单位以为我方已全灭而返航。我们的**终代核心**损毁严重、无法再次使用，通讯系统也已损坏——只能在遍布赤潮基地群的星球上，尝试活下去。

**最新可玩内容（v0.2.0）**：迫降点（制作中）。

---

## 特性概览

- 新增星球 **赛普洛斯** 与起始区块 **迫降点（Crash Site）**
- 新增矿物与物品：**孔雀石**、**粗铅**
- 新增核心 **终代核心**（护甲最高、存储最大）
- 新增建筑：钢化玻璃熔炼器、埃里克尔强化逻辑套装、初代物流系列、**多单位组装厂**
- **塞普罗特供 T1 单位**与**异构（AT）单位**
- 在原版全谱系单位基础上重做的 **R 系列单位**（id 带 `-r` 后缀），含原版复刻的 **日蚀**
- **5 套单位指令**（狩猎 / 保护 / 定点防御 / 进入运输 / 卸载载荷）
- **运输 / 载具系统**（单位可进入运输车并卸载载荷）
- **星辉跃迁（WarpAbility）系统**：异构-星辉（`flare-at`）与日蚀（`eclipse-r`）搭载主动跃迁，按住 Alt 蓄力投送至鼠标位置；含 9 项专属设置、指挥 / 移动端自动跃迁、两批机体残影与落点棱形定位（详见「星辉跃迁（WarpAbility）系统」章）
- **屏幕扭曲（Distortion）实验性渲染系统**及其全套设置

---

## 安装方法

1. **Steam 创意工坊**：订阅后自动启用。（工坊链接占位，待作者发布后补充）
2. **Mindustry 内置 Mod 浏览器**：在游戏内「Mods」→「Browse」中搜索本 Mod（GitHub 仓库会被自动收录到列表），点击安装。
3. **手动导入 jar**：从 [Releases](https://github.com/wzk01021003/Mindustry-Project-Shattered-Star/releases) 下载 `Mindustry-Project-Shattered-StarDesktop.jar`，放入 Mindustry 的 `mods/` 目录后重启游戏。
4. **从源码构建**（见下方「构建与开发」），把 `build/libs/` 里的 jar 放进 `mods/` 目录。

---

## 内容清单

> 下列中文名与 id 均取自游戏内 bundle（`bundle_zh_CN.properties`）与代码。
> 关于 id 前缀（bundle 普遍使用 `ss-`，而多数 JSON / Java 内容实际 id 不带此前缀）的不一致，见「已知问题」。

### 星球与区块

| 名称 | 内容 id | 说明 |
| --- | --- | --- |
| 赛普洛斯（Planet） | `serpulo-r`（Java 定义）/ `serpulos`（JSON 资源 `assets/content/planets/serpulos.json`） | 新星球，中文名来自 bundle 键 `planet.ss-serpulos.name` |
| 迫降点（Crash Site，起始区块） | `ss-crash-site` | 你被击落迫降之处；需在一定时间内建立多重防线，抵御未知单位带领的巡逻队 |

### 新增矿物（Ore）

| 名称 | 内容 id | 说明 |
| --- | --- | --- |
| 孔雀石（Malachite） | `ss-ore-malachite` | 赛普洛斯最常见矿物，开采后精炼为红铜，可建造基础建筑 |
| 粗铅矿（Raw Lead Ore） | `ss-ore-raw-lead` | 赛普洛斯最常见矿物，开采后精炼为灰铅，可建造基础建筑 |

### 新增物品（Item）

| 名称 | 内容 id |
| --- | --- |
| 孔雀石 | `ss-malachite` |
| 粗铅 | `ss-raw-lead` |

### 方块（Block）

| 名称 | 内容 id（bundle 键） | 类型 / 说明 |
| --- | --- | --- |
| 终代核心 | `ss-core-nucleus-root` | 护甲最高的基地核心，最大存储量，可在敌方攻势下支撑一段时间 |
| 钢化玻璃熔炼器 | `ss-metaglass-smelter` | 将沙子与铅高效熔炼为钢化玻璃（大型玻璃厂） |
| 强化微型逻辑块 | `ss-reinforced-micro-processor` | 埃里克尔逻辑套装；循环运行逻辑指令，可控制单位与建筑 |
| 强化内存元 | `ss-reinforced-memory-cell` | 埃里克尔逻辑套装；存储处理器信息 |
| 强化开关 | `ss-reinforced-switch` | 埃里克尔逻辑套装；可切换开关，状态可被逻辑读取/控制 |
| 初代传送带 | `ss-primitive-conveyor` | 未精炼矿物建造，缓慢输送物资 |
| 初代交叉器 | `ss-primitive-junction` | 未精炼矿物建造，交叉输送 |
| 初代路由器 | `ss-primitive-router` | 未精炼矿物建造，平均分配 |
| 初代传送带桥 | `ss-primitive-bridge-conveyor` | 未精炼矿物建造，跨方块输送 |
| 初代机械钻头 | `ss-primitive-mechanical-drill` | 未精炼矿物建造，效率低、耗材少，适合前期 |
| 多单位组装厂（MultiAssembler） | `ss-multi-assembler` | 点击选择要生产的单位并加入队列，每个单位有独立生产进度；支持物品/液体/载荷消耗；可设「全局出厂指令」 |

> 说明：多单位组装厂在 Java 端以 `new MultiAssembler("multi-assembler")` 注册，配置位于 `assets/content/blocks/unit/multi-assembler.json`，bundle 中文名为「多单位组装厂」。

### 单位族（Unit Families）

| 族 | 中文名（id） | 定位 |
| --- | --- | --- |
| 塞普罗特供 T1（SE） | 围护-SE（`stell-serpulo`）、挣脱-SE（`elude-serpulo`）、天守-SE（`merui-serpulo`） | 塞普罗特供 T1 轻型侦查战斗单位（陆战坦克 / 悬浮飞艇 / 多足机甲） |
| 异构 AT1 | 异构-星辉（`flare-at`）、异构-尖刀（`dagger-at`）、异构-新星（`nova-at`） | AT1 单位，相比塞普罗单位在速度、防御、伤害上有提升 |
| R 系列（塞普罗系 / 埃里克尔系） | 如 `eclipse-r`（日蚀）、`corvus-r`、`atrax-r`、`stell-r`、`merui-r`、`flare-r` 等（id 带 `-r` 后缀） | 在原版全谱系单位基础上重做，所有单位用途描述已按本 Mod 世界观重写；数据库按 `-r` 后缀自动归入「原版复刻」分类 |
| 运输 / 载具 | `transport-land-r`（载具单位） | 可装载单位，配合「进入运输 / 卸载载荷」指令使用 |

> 注：单位意图与定位的中文描述来自 `bundle_zh_CN.properties`；R 系列单位的具体清单与数值以游戏内为准。

### 单位指令系统（5 套）

注册于 `ShatteredStarMod.java`，会自动挂载到符合条件的单位上：

| 指令 | 内容 id | 行为 |
| --- | --- | --- |
| 狩猎 | `ss-hunt` | `HuntAI`，自动猎杀敌人 |
| 保护 | `ss-protect` | `ProtectAI`，保护指定建筑 / 目标 |
| 定点防御 | `ss-guard` | `GuardAI`，定点防御 |
| 进入运输 | `ss-enter-transport` | 进入载具（仅地面单位） |
| 卸载载荷 | `ss-unload` | 从载具卸载载荷（仅载具单位） |

### 屏幕扭曲（Distortion）渲染系统

一套**实验性后处理渲染**系统：在单位死亡、武器命中、部件触发时产生屏幕扭曲特效。预设与触发规则由 `assets/configs/ss-distortions.json`（及代码内 `DistortionConfig.java`）定义，渲染由 `DistortionRenderer` 实现。

> 注意：**Distortion 是「屏幕扭曲」特效系统，不是「跃迁」。** 本 Mod 的「跃迁（星辉跃迁）」为另一套独立系统，详见下方「星辉跃迁（WarpAbility）系统」章。

---

## 设置说明（屏幕扭曲渲染）

在游戏设置中新增分类 **「实验性渲染」**（`ss-settings.category`），包含以下开关（键名与官方中文描述均来自 bundle）：

| 设置项 | 设置键 | 官方中文描述 |
| --- | --- | --- |
| 启用实验性渲染 | `ss-render-enabled` | 开启屏幕扭曲效果。低端设备可能会降低性能。 |
| 渲染等级 | `ss-render-level` | 等级越高，允许同时存在的扭曲效果越多、范围越大。 |
| 屏幕外剔除 | `ss-render-cull` | 跳过不在摄像机视野内的扭曲效果，以节省性能。 |
| 自动降级 | `ss-render-autodisable` | 当帧率持续低于 25 时，自动降低扭曲效果质量。 |
| 调试信息 | `ss-render-debug` | 将当前激活的扭曲数量和帧率输出到控制台。 |
| 色散 | `ss-render-chroma` | 在扭曲边缘分离 RGB 通道，呈现能量场观感。关闭可节省纹理采样。 |

**渲染等级对照**（数值来自 `SSSettings.java`，等级 0=关 / 1=低 / 2=中 / 3=高）：

| 等级 | 最大并发数 | 最大半径 | 最大强度 | 每帧最大数 |
| --- | --- | --- | --- | --- |
| 关（0） | 0 | 0 | 0 | 0 |
| 低（1） | 8 | 120 | 0.8 | 2 |
| 中（2） | 50 | 240 | 1.5 | 5 |
| 高（3） | 100 | 480 | 3.0 | 10 |

> 设备要求：需要 OpenGL ES 3.0 或更高版本；不满足时设置界面会提示「你的设备不支持此功能」。

---

## 本地化

- 支持语言：**英语 / 简体中文 / 繁体中文（正体）**
- bundle 直接打进 jar，安装后即可显示对应语言，无需额外文件。
- 鸣谢 **DeepSeek** 协助完成了部分 JSON 编写，以及英 / 简中 / 繁中三语的 bundle 编写。
- 对繁体中文玩家，作者完善了各单位英文翻译与名称优化。

---

## 构建与开发

```bash
# 克隆仓库
git clone https://github.com/wzk01021003/Mindustry-Project-Shattered-Star.git
cd Mindustry-Project-Shattered-Star

# 本地构建（只需要 JDK 17，产物在 build/libs/）
./gradlew jar --console=plain --offline
# 产物：build/libs/Mindustry-Project-Shattered-StarDesktop.jar
```

- **JDK**：需要 **Java 17**。
- **Gradle**：使用仓库自带 wrapper（无需自行安装）。
- **目标游戏版本**：`minGameVersion 160`（对应 Mindustry v8 / v160，构建依赖 `mindustryVersion = "v160"`）。
- **Android**：`./gradlew deploy` 会额外调用 `d8` 打包 Android 版，需要本机配置 `ANDROID_HOME`；只做桌面版时用上面的 `jar` 即可。
- **CI**：`.github/workflows/commitTest.yml` 仍保留（JDK 17 + `./gradlew deploy`），但日常构建建议直接用本地 `jar`，不再依赖 Actions 排队。

---

## 已知问题 / 路线图

- **迫降点区块仍在制作中**：`mod.hjson` 在 v0.2.0 更新说明中注明「迫降点正在制作中」。
- **命名前缀不一致**：bundle 中内容键普遍带 `ss-` 前缀（如 `ss-primitive-conveyor`、`ss-multi-assembler`、`ss-core-nucleus-root`），但多数 JSON 资源与 Java 内容实际 id 不带此前缀（按文件名 / 构造参数命名）。需作者统一，否则中文名可能无法正确绑定。详见「需作者确认事项」。

---

## 星辉跃迁（WarpAbility）系统

> 以下内容整理自代码 `src/project/abilities/WarpAbility.java`、`src/project/content/SSUnitType.java`、`src/project/SSWarpSettings.java`、`src/project/graphics/WarpShapes.java` / `WarpEffects.java` 与 `assets/bundles/bundle_zh_CN.properties`。设置项中文名 / 描述、分类名均引用 bundle 原文；数值与代码 id 来自源码。距离换算：1 格 = 8 px（Mindustry `tilesize`）。

### 跃迁机制

按住 **Alt** 蓄力，随后将单位跃迁至鼠标所指位置（bundle `ability.warp`：「按住 Alt 蓄力，随后跃迁至鼠标所指位置。距离越远蓄力越久，起跳前必须先转向目标。」）。核心规则：

- **距离越远，蓄力越久**：蓄力时间 = 基础蓄力 +（距离 ÷ 阈值距离）× 每格追加蓄力；跳得越远等得越久，不能贴脸乱闪。
- **起跳前必须先转向**：默认要求机头先对准目标方向才允许跃迁，避免背对瞬移造成的朝向突变；大型单位改为「蓄力期自适应转向 + 起跳前硬对齐」，不要求提前对准。
- **冷却**：每次跃迁后进入冷却；自动跃迁（指挥 / 移动端）另有独立的、更短的冷却。三条触发路径统一走同一个冷却判断，指挥单位不会因为「AI 不需要等」而无限连闪。
- 蓄力期间可临时按住 **O** 屏蔽本次跃迁（仅当前单位；设置总开关见下）。

单位卡片上的中文 stat（来自 `ability.warp.stat`，括号内为异构-星辉默认值）：

| 项目 | 含义 | 异构-星辉（默认 `WarpAbility`）|
| --- | --- | --- |
| 基础蓄力 | 与距离无关的固有蓄力 | 0.25 秒（15 帧）|
| 每 100 格追加 | 超过 800 px（100 格）后每 100 格增加 | 0.25 秒（15 帧）|
| 冷却 | 手动跃迁结束后冷却 | 2.5 秒（150 帧）|

> 数值以游戏内单位卡片显示为准；日蚀使用单独调过的参数，见下表。

### 跃迁单位清单

跃迁能力挂在 `WarpAbility` 上，目前两款单位搭载：

| 单位（中文名）| 内容 id | 定位 | 跃迁特性（代码字段 / 默认值）|
| --- | --- | --- | --- |
| 异构-星辉 | `flare-at` | AT1 轻型侦查空战战机 | 默认 `WarpAbility` 数值：基础蓄力 15 帧、每 800 px 追加 15 帧、冷却 150 帧；**需机头先对准**（`warpRequireFacing` 默认 true）；无落地气浪（`warpImpactRadius 0`）；距离不限（默认 `warpMaxDistance 0`）。 |
| 日蚀 | `eclipse-r` | 塞普罗 T5 重型轰炸机的原版复刻（22000 结构值 / 22 护甲 / hitSize 58），归在 R 系列 | 大型参数：`warpBaseWarmup 110`、`warpMinWarmup 60`（每 6400 px 追加）、`warpCooldown 300`（自动 120）、`warpMaxDistance 1100`（约 137 格）；**无需提前对准**（`warpRequireFacing false`，蓄力期自适应转向 + 起跳硬对齐）；**可边打边跳**（`warpRequireIdleWeapons false`，落地炮塔压 36 帧装填）；**落地掀气浪** `warpImpactRadius 130`（约 16 格）/`warpImpactForce 6`，只推不伤；大机体专用的更强落地震屏、重音，以及蓄力期就在落点撑开的棱形符印 + 碎片翻涌 + 持续扭曲预示。 |

> 注意：**Distortion（屏幕扭曲）是另一套渲染系统，与跃迁无关**。跃迁单位在起跳 / 落地时也会叠一层屏幕扭曲，但那是复用 Distortion 的视觉，未开启「实验性渲染」时不显示。

### 跃迁设置

游戏设置中新增分类 **「星辉跃迁」**（`ss-warp.category`），共 9 项（键名与中文描述均来自 bundle）：

| 设置项（中文名）| 设置键 | 官方中文描述 | 默认值 / 范围 |
| --- | --- | --- | --- |
| 启用主动跃迁 | `ss-warp-enabled` | 星辉跃迁装置的总开关。按住 Alt 蓄力，同时按住 O 可临时屏蔽。关闭后所有跃迁失效。 | 默认开（true）|
| 指令自动跃迁 | `ss-warp-auto` | AI 控制的星辉接到远处的移动、采矿、建造或搬运指令时，直接跃迁过去而不是飞过去。 | 默认开（true）|
| 指令自动跃迁距离 | `ss-warp-auto-dist` | 指令目标超过这个距离才自动跃迁。助推、搬运、支援三档会按比例跟着变。 | 滑条 10~120 格，默认 60 格（步进 5）|
| 移动端自动跃迁 | `ss-warp-mobile` | 触屏没有 Alt 键，镜头停止拖动且离机体过远时，星辉自动跃迁到镜头位置。 | 默认开（true）|
| 移动端自动跃迁距离 | `ss-warp-mobile-dist` | 镜头离机体超过这个距离才触发移动端跃迁。 | 滑条 10~80 格，默认 30 格（步进 5）|
| 跃迁音效音量 | `ss-warp-volume` | 起跳与落地音效的音量。 | 滑条 0~100%，默认 55% |
| 跃迁特效 | `ss-warp-effect` | 起落闪光、能量轨迹、粒子与震屏。关闭后只保留音效，可明显减轻卡顿。 | 默认开（true）|
| 跃迁准星 | `ss-warp-reticle` | 蓄力时在落点画虚线、目标双环和十字准星。关掉只是不再提示落点，跃迁本身照常可用。 | 默认开（true）|
| 只为自己的单位显示准星 | `ss-warp-reticle-own` | 打开后只有你直接操控的单位会画准星。指挥出去的单位照样跃迁，只是不再满地图铺落点提示。 | 默认关（false）|

> 运行期 `WarpAbility` 直接读 `SSWarpSettings` 的静态字段，设置改动即时生效，无需重启。

### 指令自动跃迁 与 移动端自动跃迁

**指令自动跃迁**（`ss-warp-auto`）：单位处于 AI 指挥下、且指令目标点超过阈值时，会自动跃迁过去而非飞过去。阈值由 `ss-warp-auto-dist`（默认 60 格）控制，并按指令类型按比例缩放（出厂默认，单位为 px / 格）：

- 普通移动：超过 `ss-warp-auto-dist`（480 px / 60 格）即跃迁；
- 助推姿态：更短阈值（160 px / 20 格）；
- 搬运 / 装载（`loopPayload` / `loadUnits` / `loadBlocks` / `unloadPayload`）：更短阈值（80 px / 10 格）；
- 采矿 / 建造 / 维修 / 协助：较短阈值（120 px / 15 格）。

自动跃迁使用独立的、更短的冷却（`autoWarpCooldown`：星辉 40 帧、日蚀 120 帧），避免与手动冷却叠加导致 AI 永远等不满。

**移动端自动跃迁**（`ss-warp-mobile`）：触屏没有 Alt 键。当镜头停止拖动且离机体超过 `ss-warp-mobile-dist`（默认 30 格）时，星辉自动跃迁到镜头位置；需镜头静止约 20 帧才触发，避免拖动视角时乱闪。

### 视觉与音效

- **三段式能量环**：起跳 `WarpShapes.BURST`、蓄力 `WarpShapes.CHARGE`、落点 `WarpShapes.ARRIVE`；大型单位改用大号版本 `BURST_LARGE` / `CHARGE_LARGE` / `ARRIVE_LARGE`。
- **能量轨迹与通道粒子**：`WarpEffects.WarpTrailEffect` 与 `WarpParticleEffect`（日蚀用放大版 `warpTrailEffectLarge` / `warpParticleEffectLarge`）。
- **落点残影**：数份机体剪影分两批——先 4 份从落点向外推开，延迟后再 4 份从最远处收回，结构与 VE 幻象的 `meta-spawn` 一致（星辉 4 份 / 半径 58 px / 不透明度 0.75；日蚀 4 份 / 半径 210 px / 0.42，贴图 1:1 不缩放）。
- **落点棱形符印**：贴图为自绘的 `assets/sprites/effects/warp-mark.png`（512×512 竖长棱形符印：双层菱形 + 四角刻线 + 中心菱形，additive、染橙金）。
  - **蓄力（定位）**：4 份符印在落点从中心裂开、21 帧撑到 220px（`warpMarkRadius`），同时尺寸从 55% 长到 180px（`warpMarkSize`），撑满后缓慢自转并随蓄力增亮 —— 落点必须在跃迁之前就被标出来。
  - **蓄力（碎片）**：`WarpEffects.WarpMoteEffect`，贴图为 `assets/sprites/effects/warp-shard.png`（128×64 硬边菱形）。对应 VE 幻象载体上挂的 `ve-warp1` / `ve-warp2`：每帧按概率在落点 108±40px 处生成一片，随机朝向、边自转边向内收、16 帧淡出，密度随蓄力从 35% 涨到 100%。
  - **蓄力（扭曲）**：每 12 帧在落点补一发内向扭曲（`warpTargetDistortion`，走 `DistortionRenderer`），强度随蓄力从 0.35 涨到 0.85。
  - **落地（合拢）**：`WarpEffects.WarpMarkCollapseEffect`，4 份符印用 `circleIn` 从 220px 收回中心、20 帧，尺寸同步收 35%、透明度按 `(1-p)²` 掉 —— 读作「缝在合上」，不是落地后的装饰。
  - 整体结构照 VE 幻象的 `meta-spawn`，但时序是反过来的：那边载体在落点隐形站 3 秒冒碎片，死亡才把机体放出来并撑开棱形；这里把「撑开」提前到蓄力期，落地只保留「合拢」，因为跃迁是瞬时的，没有 3 秒可以铺陈。
- **震屏**：`Effect.shake`，星辉 `warpShake 10`、日蚀 `warpShake 22`。
- **屏幕扭曲**：起跳 `warpOutDistortion`、落地 `warpInDistortion`（复用 Distortion 系统），未开启「实验性渲染」时不显示。
- **音效**：起跳 `stAstroWarp1`、落地 `stAstroWarp2`（加载失败退回 `massdriver`）；日蚀落地额外叠一层重音 `stAstroWarp3`（`useHeavyLandingSound`）。音量由 `ss-warp-volume` 控制。

---

## 鸣谢

- **DeepSeek**：协助完成部分 JSON 编写，以及英 / 简中 / 繁中三语 bundle。
- 提出意见与给予支持的网络网友们。

---

## 许可证

- 许可证：**未在当前仓库声明**（`mod.hjson` 无 license 字段）。如需开源发布，请作者补充 `LICENSE` 与 `mod.hjson` 中的 license 字段。

---

## 仓库与链接

- 仓库：`https://github.com/wzk01021003/Mindustry-Project-Shattered-Star`
- 作者：A4PAPER（A4纸）
