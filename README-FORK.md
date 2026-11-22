# Mindustry: 碎星计划 / Project Shattered Star — Fork 工程

这是 **Anuken/Mindustry v160.7 官方源码的 fork**，不是独立复刻：游戏本体与官方桌面版
**完全一致**（UI、玩法、单位、战役、科技树、电力、液体、蓝图、多人联机全部保留），
并附带 A4PAPER 的「碎星计划」Java Mod。源码在手里，本体和 Mod 都能深度修改。

> 与 `/workspace/shattered-star`（早先那套独立简化同人工程）无关，那套可以删掉。

## 目录结构

```
core/ desktop/ server/ tools/ annotations/ ...   官方 Mindustry 源码，未改动
shattered-star-mod/                             碎星计划 Mod 源码
  settings.gradle                              新增：让 Mod 独立构建，不被上层 settings 吸收
  build.gradle                                 改为引用本地官方 jar 编译（原件 build.gradle.upstream）
windows/                                       Windows 打包层（本 fork 新增）
  launcher.c / launcher.rc / app.manifest / app.ico / make-icon.py
  installer.nsi / README-Windows.tpl.txt / build-exe.sh
  dist-cache/Mindustry.jar                      官方 v160.7 release jar（编译 Mod + 打包兜底）
  dist/                                        生成的 exe / zip
LICENSE / NOTICE / SOURCES.md                   GPL-3.0 合规文件
../Arc/                                        同级的 Arc 源码（官方推荐，绕开 jitpack）
```

## 构建

必须 **JDK 17**（官方 README：`Other JDK versions will not work`）。

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew desktop:dist                # 本体 → desktop/build/libs/Mindustry.jar
cd shattered-star-mod && gradle jar -PnoJabel   # Mod → build/libs/*Desktop.jar
cd .. && bash windows/build-exe.sh    # Windows exe + zip（Linux 交叉编译）
```

`windows/build-exe.sh` 优先用源码构建的 `Mindustry.jar`；若本体尚未构建，
自动回退到 `windows/dist-cache/Mindustry.jar`（官方 v160.7 release，内容等价）。
Windows 打包需要 `gcc-mingw-w64-x86-64`、`nsis`、`p7zip-full`、Python Pillow。

启动 exe 后，启动器会把 `mods\*.jar` 自动安装到 `%APPDATA%\Mindustry\mods\`
（Mindustry 标准 mod 目录），首次运行即自带碎星计划内容。

## 深度修改去哪里改

| 想改什么 | 改哪里 |
| --- | --- |
| 本体机制 / 新增方块、单位、战役 | `core/src/mindustry/` 下官方源码，然后 `./gradlew desktop:dist` |
| 碎星计划内容 | `shattered-star-mod/src/project/`，然后 `gradle jar -PnoJabel` |
| 启动参数、Mod 自动安装、图标、安装包 | `windows/launcher.c`、`windows/installer.nsi`、`windows/make-icon.py` |
| 内容注册表（物品/方块/单位） | 官方 `core/src/mindustry/content/` 与 Mod 的 `content/` 包 |
| 敌方 AI 智能度（三档：原版 / 智能 / 精锐，设置入口在难度与自定义规则里） | `core/src/mindustry/ai/AIIntelligence.java` |

改完源码后重跑 `bash windows/build-exe.sh` 即可产出新 exe。

## 开源协议（重要）

Mindustry 本体是 **GPL-3.0**。自己本地玩、随便改、不分发 → 无公开义务；
**把 exe 发给别人**时必须：继续 GPL-3.0、随附完整对应源码（本仓库）、
保留 `LICENSE` / `NOTICE` / 版权声明、不附加禁止再分发的限制。
详见 `NOTICE` 与 `SOURCES.md`。「Mindustry」商标归原权利人所有，本作品非官方、非商业。

## 验证过的点

- 官方 `archash=7445105cd2` 与本地 `../Arc` 提交一致，构建走 composite build 绕开 jitpack
- `./gradlew desktop:dist` **BUILD SUCCESSFUL（7m13s）**，从源码产出 85MB `Mindustry.jar`
- Mod 编译用官方 jar 作 classpath，完全离线，规避国内镜像「有 POM 无 jar」问题
- Xvfb 用源码 jar + Mod 真启动：`[Mods] Loading mod ...` → `Loaded ShatteredStarMod constructor.`
  → `Initializing ShatteredStarMod.`，`Total time to load: 16555ms`，无任何错误
- Wine 跑启动器 `--selftest`：`modsDir = C:\users\root\AppData\Roaming\Mindustry\mods`，
  `ProjectShatteredStar.jar -> installed` 且文件真实落盘
- 生成的启动器是 PE32+ / Windows GUI 子系统，内置 JRE 17，双击不弹黑框
- 源码自行构建的版本字符串显示为 `custom`（未设 `-Pbuildversion`），不影响玩法；
  这是自行构建而非官方发行的正常标识，如需显示 `160.7` 可加 `-Pbuildversion=160.7` 重建
