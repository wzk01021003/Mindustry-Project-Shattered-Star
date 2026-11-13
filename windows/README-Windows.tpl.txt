Mindustry: 碎星计划 / Project Shattered Star  —  Windows 版
==========================================================

本体版本：Mindustry v160.7（v8 Build 160.7，官方桌面版，未修改）
附带内容：A4PAPER 的「碎星计划」Java Mod（赛普洛斯 / 迫降点 / 孔雀石 / 终代核心…）
平台：Windows 10/11 64 位    运行时：内置 Java 17，无需另装 JDK

【怎么玩】
双击本目录下的 Mindustry.exe。游戏本体与官方桌面版完全一致（UI、玩法、单位、
战役、科技树、电力、液体、蓝图、多人联机全都一样），额外自带碎星计划 Mod。

首次启动时，启动器会把 mods\ 下的 Mod 自动安装到：
    %APPDATA%\Mindustry\mods\
这是 Mindustry 标准的 mod 目录。你自己往那里加别的 Mod 也不受影响。
存档、设置、蓝图、截图都在 %APPDATA%\Mindustry\，卸载游戏不会删除。

【两种下载文件的区别】
- Mindustry-ShatteredStar-Windows-x64-Setup.exe：安装版，双击后一路下一步，
  创建桌面/开始菜单快捷方式与卸载程序。
- Mindustry-ShatteredStar-Windows-x64-Portable.zip：绿色版，解压到任意目录，
  双击 Mindustry.exe 即玩，不写注册表，删目录即卸载。
※ 绿色版里 Mindustry.exe、Mindustry.jar、jre\、mods\ 必须放在一起，
  不能只拷单个 exe。SHA256SUMS.txt 可用来校验文件完整性。

【启动器参数（命令行）】
Mindustry.exe --console           用 java.exe 启动，控制台可见 JVM 日志
Mindustry.exe --no-mods           本次跳过自动安装 Mod
Mindustry.exe --reinstall-mods    强制覆盖已安装的 Mod（更新 Mod 后用）
Mindustry.exe --selftest          写出 launcher-selftest.txt（jre / jar / mods 目录 / 完整命令行），不启动游戏
其余参数（如 -width 1280 -height 720 -debug）原样透传给 Mindustry。

【排错】
- 双击没反应：运行 Debug-Console.bat 看日志，或检查 %APPDATA%\Mindustry\last_log.txt。
- 找不到 Java 运行时：jre\bin\javaw.exe 丢失；也可装 Java 17 后设 JAVA_HOME，
  或用环境变量 SS_JAVA 指向任意 javaw.exe。
- Mod 没出现：进游戏「Mods」页看是否启用；或用 --reinstall-mods 重跑一次。
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
    bash windows/build-exe.sh
脚本会自动下载缺失的官方 Mindustry.jar 与 Windows JRE 17，用 javac 直接编译
本仓库 src/ 下的 Mod 源码，然后交叉编译启动器并打出 exe / zip。
详见 windows/README.md。本仓库的 .github/workflows/release-windows.yml 就是
在 GitHub Actions 上自动执行同一套脚本并发布 Release。
