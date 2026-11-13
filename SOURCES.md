# 源码清单与复现

本仓库的 Windows Release 由 GitHub Actions（`.github/workflows/release-windows.yml`）
自动构建并发布。下表列出成品中每个组件的源码获取方式，用于满足 GPL-3.0
「随附对应源码」的要求。

| 组件 | 版本 / commit | 源码地址 | 许可证 |
| --- | --- | --- | --- |
| Mindustry 桌面版本体 | `v160.7`（v8 Build 160.7） | https://github.com/Anuken/Mindustry/tree/v160.7 | GPL-3.0（`LICENSE`） |
| Arc（本体依赖） | 由 v160.7 的 `archash` 锚定 | https://github.com/Anuken/Arc | 见 Arc 仓库 `LICENSE` |
| 碎星计划 Java Mod | 本仓库 `src/` `assets/` `mod.hjson` | 本仓库 | 以本仓库声明为准 |
| Eclipse Temurin JRE 17 | 17.x Windows x64 | https://github.com/adoptium/temurin17-binaries/releases | GPL-2.0 with Classpath Exception（`jre/legal/`） |
| Windows 打包层 | 本仓库 `windows/`、`.github/workflows/release-windows.yml` | 本仓库 | 随本体 GPL-3.0 |

## 目录说明

```
src/ assets/ mod.hjson ...          碎星计划 Mod 源码（本仓库原有内容）
windows/
  build-exe.sh                    主打包脚本：下载依赖 -> 编译 Mod -> 交叉编译启动器
                                  -> 组装绿色目录 -> 打 zip 与 NSIS 安装版
  ci-build-mod.sh                 以 javac 直接编译 src/ 下的 Mod（零 Gradle / 零 jitpack）
  launcher.c / launcher.rc        Windows 原生启动器源码（自动定位 JRE、自动装 Mod）
  app.manifest                    DPI-aware / 长路径 manifest
  installer.nsi                   NSIS 安装脚本
  make-icon.py                    程序化生成图标（零外部素材）
  README-Windows.tpl.txt          随包附带的 Windows 使用说明
.github/workflows/release-windows.yml   自动构建 + 发布 Release
RELEASE_NOTES.md                 Release 正文
```

## 构建原理

`windows/build-exe.sh` 完全自包含，不依赖本仓库之外的源码：

1. **本体**：直接下载官方 `v160.7` 的 `Mindustry.jar`（未经修改），
   校验 `META-INF/MANIFEST.MF` 的 `mindustry.desktop.DesktopLauncher` 入口。
   若本地存在源码构建产物 `desktop/build/libs/Mindustry.jar` 则优先使用。
2. **Mod**：`windows/ci-build-mod.sh` 用 `javac -cp Mindustry.jar` 直接编译
   本仓库 `src/` 下全部 Java 源码，再与 `assets/`、`mod.hjson`、`README.md`
   一起打成 `ProjectShatteredStar.jar`。绕开 Gradle 与 jitpack，CI 可离线复现。
3. **运行时**：下载 Eclipse Temurin Windows JRE 17，解压进 `jre/`。
4. **启动器**：`x86_64-w64-mingw32-gcc` 交叉编译成 PE32+ / Windows GUI 子系统
   exe（双击不弹黑框），内嵌版本资源、图标与 manifest。
5. **成品**：绿色 zip + NSIS 安装版 exe + `SHA256SUMS.txt`，
   包内带齐 `LICENSE` / `NOTICE` / `SOURCES.md`。

## 本地复现

需要 Linux（Ubuntu 22.04 验证通过）+ JDK 17：

```bash
sudo apt-get install -y gcc-mingw-w64-x86-64 nsis p7zip-full python3-pil
bash windows/build-exe.sh
```

产物在 `windows/dist/`。可用环境变量覆盖下载源：

```bash
SS_GAME_JAR=/path/to/Mindustry.jar   # 跳过下载本体
SS_JRE_ZIP=/path/to/jre-win.zip      # 跳过下载 JRE
SS_JRE_URL_PRIMARY=https://...       # 自定义 JRE 主源
SS_OUT=/tmp/out                      # 自定义输出目录
```

## 分发时的合规动作

1. 保留包内 `LICENSE`（GPL-3.0 全文）、`NOTICE`、本文件，以及 `jre/legal/`。
2. 二进制包与本仓库地址一起提供；对外分发的任何修改版继续以 GPL-3.0 授权。
3. 不附加禁止接收方修改或再分发的限制。
