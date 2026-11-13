# Windows 发行打包层

在 **Linux**（含 GitHub Actions `ubuntu-22.04`）上交叉编译出可直接双击运行的
Windows 游戏包，不需要 Windows 机器，也不需要 Wine。

产物：`windows/dist/`

| 文件 | 说明 |
| --- | --- |
| `Mindustry-ShatteredStar-Windows-x64-Setup.exe` | NSIS 安装版 |
| `Mindustry-ShatteredStar-Windows-x64-Portable.zip` | 绿色免安装版 |
| `SHA256SUMS.txt` | 校验和 |

## 一键构建

```bash
bash windows/build-exe.sh
```

脚本自包含，缺什么下什么：

1. 下载官方 Mindustry `v160.7` 桌面版 `Mindustry.jar`（未经修改）；
2. `ci-build-mod.sh` 用 `javac` 直接编译本仓库 `src/` 下的 Mod 源码，
   与 `assets/`、`mod.hjson` 一起打成 `ProjectShatteredStar.jar`
   （**不依赖 Gradle / jitpack**，CI 里能稳定复现）；
3. 下载 Eclipse Temurin Windows JRE 17；
4. `mingw-w64` 交叉编译 `launcher.c` 成 PE32+ Windows GUI 子系统 exe；
5. 组装绿色目录（含 GPL 合规文件），压成 zip；
6. `makensis` 编译安装版 exe。

## 依赖

```bash
sudo apt-get install -y gcc-mingw-w64-x86-64 nsis p7zip-full python3-pil
# 另需 JDK 17（javac / jar）、curl、unzip
```

## 环境变量

| 变量 | 作用 |
| --- | --- |
| `SS_GAME_JAR` | 指定已有的 `Mindustry.jar`，跳过下载 |
| `SS_MOD_JAR` | 指定已编译的 Mod jar，跳过编译 |
| `SS_JRE_ZIP` | 指定已下载的 Windows JRE zip，跳过下载 |
| `SS_JRE_URL_PRIMARY` / `SS_JRE_URL_FALLBACK` | 自定义 JRE 下载源 |
| `SS_HTTP_TIMEOUT` | 单次下载超时秒数（默认 300） |
| `SS_CACHE` / `SS_OUT` | 自定义缓存目录 / 输出目录 |
| `JAVAC` / `JAR_TOOL` | 覆盖 javac / jar 可执行文件 |

## 自动发布

`.github/workflows/release-windows.yml` 在推送打包层文件到 `master` 时自动执行
本脚本，并用 `GITHUB_TOKEN` 创建 / 更新 `windows-v0.1.0` Release，
上传 exe、zip 与校验和。也可在 Actions 页手动 `workflow_dispatch`。

## 启动器

`launcher.c` 编译出的 `Mindustry.exe` 负责：

- 按自身目录定位 `jre\bin\javaw.exe` 与 `Mindustry.jar`；
- 首次运行把 `mods\*.jar` 复制到 `%APPDATA%\Mindustry\mods\`（标准 mod 目录）；
- 透传命令行参数给 Mindustry。

支持 `--console`、`--no-mods`、`--reinstall-mods`、`--selftest`（自检，不启动游戏）。

> 分发二进制包时必须保留包内 `LICENSE` / `NOTICE` / `SOURCES.md`，详见根目录 `SOURCES.md`。
