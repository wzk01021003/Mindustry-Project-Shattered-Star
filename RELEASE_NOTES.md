# Mindustry 碎星计划 · Windows 版

**Mindustry v160.7 官方桌面版（未修改） + 碎星计划 Java Mod + 内置 Java 17**,
打包成开箱即用的 Windows 可执行文件。玩法、UI、单位、战役、科技树、电力、
液体、蓝图、联机全部与官方桌面版一致。

> 这是同人非官方作品，与 Anuken / Mindustry 原作者无隶属关系。
> 基于 GPL-3.0，请保留包内 `LICENSE` / `NOTICE` / `SOURCES.md`。

## 下载哪个

| 文件 | 说明 |
| --- | --- |
| `Mindustry-ShatteredStar-Windows-x64-Setup.exe` | **安装版**: 双击 -> 下一步, 创建桌面/开始菜单快捷方式与卸载程序 |
| `Mindustry-ShatteredStar-Windows-x64-Portable.zip` | **绿色版**: 解压到任意目录, 双击 `Mindustry.exe` 即玩, 不写注册表, 删目录即卸载 |
| `SHA256SUMS.txt` | 两个包的 SHA-256 校验值 |

适用 **Windows 10 / 11 64 位**, **无需安装 Java**(已内置 Temurin JRE 17)。

## 关于 Mod

启动时, 启动器会把自带的 `ProjectShatteredStar.jar` 自动装到
`%APPDATA%\Mindustry\mods\`(Mindustry 标准 mod 目录)。进游戏后可在
「Mods」页确认已启用。带动内容: 新星球**赛普洛斯**、起始区块**迫降点**、
孔雀石 / 粗铅、**终代核心**、初代物流系列、多单位组装厂、SE / AT / R 单位族、
5 套单位指令、**星辉跃迁**等。

存档、设置、蓝图、截图在 `%APPDATA%\Mindustry\`, 卸载游戏不会删除。

## 校验

```powershell
certutil -hashfile Mindustry-ShatteredStar-Windows-x64-Setup.exe SHA256
```

与 `SHA256SUMS.txt` 比对即可。

## 排错

- 双击没反应: 运行绿色版目录里的 `Debug-Console.bat` 看 JVM 日志,
  或查看 `%APPDATA%\Mindustry\last_log.txt`。
- exe **未签名**, 首次运行可能被 SmartScreen 拦, 点「更多信息 -> 仍要运行」。
- 显卡 / OpenGL 报错: 命令行加 `-gl 2.1` 或 `-compatibilityGl`。
- 绿色版不能只拷单个 exe, `Mindustry.jar`、`jre\`、`mods\` 必须同目录。
- 安装路径含中文出问题时, 放到纯英文路径。

启动器参数: `--console`(带控制台看日志)、`--no-mods`、
`--reinstall-mods`(更新 Mod 后强制重装)、`--selftest`(只写自检报告不启动游戏)。

## 自己重新打包

本 Release 由 `.github/workflows/release-windows.yml` 在 GitHub Actions 上
自动构建, 源码与脚本全在本仓库, 详见 `SOURCES.md` 与 `windows/README.md`。
本地 Linux 上执行 `bash windows/build-exe.sh` 即可复现。
