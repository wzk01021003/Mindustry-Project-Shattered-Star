# Neon 内置版：改动说明与构建

本目录是 [DeterMination-Wind/Neon](https://github.com/DeterMination-Wind/Neon) N19 的修改版源码，
按 GPL-3.0 随「碎星计划」提供。上游说明见 `README.md`、`FEATURES.md`。

## 相对上游的改动

只改了设置界面与遥测，没有触碰任何玩法算法：

| 文件 | 改动 |
|---|---|
| `src/main/java/bektools/BekToolsMod.java` | 新增模块简介渲染、风险确认弹窗、展开后警示条；PostHog 上报器置空；`initializeModule` 在 headless 下返回 null |
| `src/main/java/patchviewer/PatchViewerMod.java` | 基线捕获与 WorldLoadEvent 回调增加 headless 早退，避免无 Scene 环境 NPE |
| `src/main/java/logicsugar/LogicSugarMod.java` | 不再 install LogicSugar 的 PostHog 上报器；关闭 MDT 推荐弹窗 |
| `src/main/resources/bundles/bundle_zh_CN.properties` | 新增 30 条模块简介 + 11 条风险说明 + 1 条弹窗标题 |
| `src/main/resources/bundles/bundle.properties` | 同上（英文） |
| `build.gradle` / `gradle.properties` | 仅调整本机 JDK 路径，未改构建逻辑 |

具体做法：

1. **模块简介**：`ModuleEntry` 自动从 `bektools.section.<id>.desc` 取文案，渲染在折叠标题
   下方（灰色小字，收起时也可见）。bundle 缺这个 key 时不显示，不会把 key 名打到界面上。
2. **风险确认**：11 个会改动原版行为的模块（编队接管、右键语义、命令队列、实际放置、
   真实拆除、地形写入、地图生成、mods 覆盖等）首次展开时弹 `ui.showConfirm`，
   确认后才展开；展开后正文顶部另有一条红色说明。确认记录只存在本次会话内存里，
   重开游戏会再提示一次。
3. **移除遥测**：上游有两处 PostHog 上报，都没有给玩家的开关——
   `bektools.PostHogUsageReporter`（玩家名、稳定 ID、版本、时长、子模块开关）与
   `logicsugar.PostHogReporter`（玩家 ID、会话时长、编辑器使用）。前者把初始化改成
   `postHogUsageReporter = null`，后者不再调用 `install()`；反馈按钮保留（玩家主动发送）。
4. **关闭外部模组推荐**：LogicSugar 原本会在首次进编辑器后弹窗推荐 MDT（Mlog Dev Tools），
   内置包不主动引导玩家装别的模组，这里不再注册该回调。

## 构建

需要 JDK 17、Android SDK build-tools（提供 d8）。

```bash
# 1) 准备 Mindustry 侧 classpath（二选一）
#    a. 上游默认路径：../Mindustry-master/core/build/libs/core-release.jar
#       以及 ../Mindustry-master/desktop/build/libs/Mindustry.jar（rhino 在这个包里）
#    b. 或让 build.gradle 回退到 jitpack（去掉这两个文件即可）

# 2) 打包（同时产出桌面 + Android 双格式 jar）
ANDROID_HOME=/path/to/android-sdk \
D8_PATH=/path/to/build-tools/36.0.0/d8 \
./gradlew deploy

# 产物：build/libs/Neon.jar（含 classes.dex）、../构建/Neon/Neon.jar
```

本仓库内置的那份就是用这条命令产出的，`gradle.properties` 里的
`org.gradle.java.home` 只影响本机，需要按自己的 JDK 路径改或删掉。

## 注意

`mod.hjson` 里 `hidden: true` 是上游设定：装好后 mod 列表里看不到，
设置项在「设置 → 模组 → Neon / 氖」。内置版沿用该设定。
