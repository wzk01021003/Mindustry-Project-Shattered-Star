
---

## `README_zh_CN.md`（简体中文）

```markdown
# 碎星计划 / Project Shattered Star

一个面向 **Mindustry v8 (v160.3)** 的大型多星球内容模组。

碎星计划在游戏中加入了一套自定义星系、数十个改写单位、多种自定义
AI、一个多单位组装厂，以及一个实验性的屏幕扭曲渲染系统。

---

## 内容

### 星球

| 名称 | 说明 |
|---|---|
| `ss-sun` | 自定义恒星，不显示在行星界面。 |
| `serpulo-r` | 塞普罗的平行副本，绕 `ss-sun` 运行。 |

### 单位

碎星计划把原版所有单位重做成 `*-r` 变体，并额外加了两族单位：

- **塞普罗** —— 陆军 / 空军 / 海军，战斗与辅助。
- **埃里克尔** —— 坦克 / 机甲 / 飞船，另有一条带**发光腿**的蜘蛛线。
- **异构**（`*-at`）—— 视觉上完全自定义的新分支。
- **导弹** —— 内嵌在武器里的导弹单位
  （`plasma-missile`、`quell-missile-r`、`disrupt-missile-r`、
  `anthicus-missile-r`）。

### 方块

- **多单位组装厂**（`multi-assembler`）—— 并行组装多种单位，支持
  队列、循环模式、指挥分配和载荷。

### AI

三个自定义单位指令：

- **`ss-hunt`** —— 委托给原版 FlyingAI / GroundAI。
- **`ss-protect`** —— 跟随友方目标并保护它。
- **`ss-guard`** —— 驻守一个点并在周围巡逻。

### 实验性渲染

可开关的屏幕扭曲系统（设置 → **实验性渲染**）。附带：

- 等级切换（关 / 低 / 中 / 高）
- 屏幕外剔除
- 低帧率自动降级
- 并发数量、半径、强度的硬性上限

默认关闭，仅在性能足够的设备上推荐开启。

### 阵营

新增第九个阵营 **Aurora**，带自定义配色与 emoji。

---

## 安装

1. 从 [Releases](../../releases) 页下载 `ProjectShatteredStar.jar`。
2. 放到 Mindustry 模组文件夹：
   - **安卓**：`/Android/data/io.anuke.mindustry/files/mods/`
   - **桌面**：`~/.local/share/Mindustry/mods/`（Linux）或
     `%APPDATA%/Mindustry/mods/`（Windows）
3. 重启游戏。

---

## 从源码构建

需要 **Java 17** 和 **Gradle 8.10.2**。

```bash
./gradlew deploy