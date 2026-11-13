#!/usr/bin/env bash
# 交叉生成 Windows 可执行文件：官方 Mindustry 本体 + 碎星计划 Mod + 内置 JRE
# 在 Linux 上运行（含 GitHub Actions ubuntu runner），不需要 Windows 机器 / Wine。
#
# 完全自包含：缺什么下什么。
#   - Mindustry.jar  无源码构建产物时，自动下载官方 v160.7 release
#   - Mod jar        用 windows/ci-build-mod.sh 以 javac 直编（零 Gradle / 零 jitpack）
#   - Windows JRE 17 自动下载 Eclipse Temurin
#
# 产物（ASCII 文件名，便于 CI 上传 Release）：
#   dist/Mindustry-ShatteredStar-Windows-x64-Setup.exe    NSIS 安装版
#   dist/Mindustry-ShatteredStar-Windows-x64-Portable.zip 绿色免安装
#   dist/SHA256SUMS.txt
set -euo pipefail

WIN="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$WIN/.." && pwd)"
CACHE="${SS_CACHE:-${HOME}/.cache/mindustry-shattered-star-win}"
OUT="${SS_OUT:-$WIN/dist}"
STAGE="$CACHE/Mindustry-ShatteredStar"
JRE_NAME="OpenJDK17U-jre_x64_windows_hotspot_17.0.21_9.zip"
JRE_ZIP="${SS_JRE_ZIP:-$CACHE/$JRE_NAME}"
JRE_URL_PRIMARY="${SS_JRE_URL_PRIMARY:-https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jre/x64/windows/$JRE_NAME}"
JRE_URL_FALLBACK="${SS_JRE_URL_FALLBACK:-https://mirrors.huaweicloud.com/openjdk/17.0.21/openjdk-17.0.21_windows-x64_bin.zip}"
HTTP_TIMEOUT="${SS_HTTP_TIMEOUT:-300}"
OLD_JRE="${HOME}/.cache/shattered-star-win/$JRE_NAME"
ZIP_OUT="$OUT/Mindustry-ShatteredStar-Windows-x64-Portable.zip"
SETUP_OUT="$OUT/Mindustry-ShatteredStar-Windows-x64-Setup.exe"
JAR_BUILT="$ROOT/desktop/build/libs/Mindustry.jar"
JAR_CACHE="$WIN/dist-cache/Mindustry.jar"
JAR_URL_PRIMARY="https://github.com/Anuken/Mindustry/releases/download/v160.7/Mindustry.jar"
JAR_URL_FALLBACK="https://ghfast.top/https://github.com/Anuken/Mindustry/releases/download/v160.7/Mindustry.jar"
MOD_OUT="$OUT/ProjectShatteredStar.jar"

need() { command -v "$1" >/dev/null 2>&1 || { echo "[FATAL] 缺少工具：$1" >&2; exit 1; }; }
info() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
fetch() { # fetch <url1> <url2> <dest>
    if curl -fL --retry 3 --max-time "$HTTP_TIMEOUT" -o "$3.part" "$1"; then
        mv "$3.part" "$3"; return 0
    fi
    echo "    主源失败，尝试备用源..." >&2
    rm -f "$3.part"
    curl -fL --retry 3 --max-time "$HTTP_TIMEOUT" -o "$3.part" "$2" && mv "$3.part" "$3"
}

need x86_64-w64-mingw32-gcc
need x86_64-w64-mingw32-windres
need makensis
need 7z
need unzip
need curl
need python3
need javac
need jar
python3 -c "import PIL" >/dev/null 2>&1 || { echo "[FATAL] 需要 Python Pillow" >&2; exit 1; }

mkdir -p "$CACHE" "$OUT" "$WIN/dist-cache"

# 1. 游戏本体 jar：优先源码构建产物，否则缓存，否则官方 v160.7 release
info "选择 Mindustry 本体 jar"
if [[ -n "${SS_GAME_JAR:-}" && -f "$SS_GAME_JAR" ]]; then
    GAME_JAR="$SS_GAME_JAR"; echo "使用环境变量指定：$GAME_JAR"
elif [[ -f "$JAR_BUILT" ]]; then
    GAME_JAR="$JAR_BUILT"; echo "使用源码构建产物：$GAME_JAR"
elif [[ -f "$JAR_CACHE" ]]; then
    GAME_JAR="$JAR_CACHE"; echo "使用缓存：$GAME_JAR"
else
    echo "下载官方 v160.7 release..."
    fetch "$JAR_URL_PRIMARY" "$JAR_URL_FALLBACK" "$JAR_CACHE"
    GAME_JAR="$JAR_CACHE"
fi
MANIFEST="$(unzip -p "$GAME_JAR" META-INF/MANIFEST.MF)"
[[ "$MANIFEST" == *"mindustry.desktop.DesktopLauncher"* ]] \
    || { echo "[FATAL] jar 不是官方桌面版" >&2; exit 1; }

# 2. 碎星计划 Mod jar：优先现成 jar，否则 javac 直编
info "准备碎星计划 Mod"
MOD_JAR="${SS_MOD_JAR:-}"
if [[ -z "$MOD_JAR" || ! -f "$MOD_JAR" ]]; then
    for d in "$ROOT/build/libs" "$ROOT/shattered-star-mod/build/libs"; do
        found="$(find "$d" -maxdepth 1 -name '*Desktop.jar' -print -quit 2>/dev/null || true)"
        if [[ -n "$found" && -f "$found" ]]; then MOD_JAR="$found"; break; fi
    done
fi
if [[ -z "$MOD_JAR" || ! -f "$MOD_JAR" ]]; then
    echo "javac 直编 Mod（零 Gradle）..."
    bash "$WIN/ci-build-mod.sh" "$GAME_JAR" "$MOD_OUT"
    MOD_JAR="$MOD_OUT"
fi
MODLIST="$(unzip -l "$MOD_JAR")"
[[ "$MODLIST" == *"mod.hjson"* ]] || { echo "[FATAL] Mod jar 缺 mod.hjson" >&2; exit 1; }
echo "Mod jar：$MOD_JAR"

# 3. 内置 Windows JRE 17
if [[ ! -f "$JRE_ZIP" ]]; then
    if [[ -f "$OLD_JRE" ]]; then
        cp "$OLD_JRE" "$JRE_ZIP"
    else
        info "下载 Windows JRE 17"
        fetch "$JRE_URL_PRIMARY" "$JRE_URL_FALLBACK" "$JRE_ZIP"
    fi
fi
echo "JRE 压缩包：$(du -h "$JRE_ZIP" | cut -f1)"

# 4. 图标 + 启动器 exe
info "交叉编译 Windows 启动器"
python3 "$WIN/make-icon.py"
(
    cd "$WIN"
    x86_64-w64-mingw32-windres -i launcher.rc -o launcher-rc.o -O coff
    x86_64-w64-mingw32-gcc -O2 -s -municode -mwindows -Wall -Wextra \
        -finput-charset=UTF-8 -fwide-exec-charset=UTF-16LE \
        -o "$CACHE/Mindustry.exe" launcher.c launcher-rc.o
)
file "$CACHE/Mindustry.exe"

# 5. 组装绿色目录
info "组装绿色目录"
rm -rf "$STAGE" "$CACHE/jre-src"
mkdir -p "$STAGE/mods" "$STAGE/jre" "$CACHE/jre-src"
cp "$CACHE/Mindustry.exe" "$STAGE/Mindustry.exe"
cp "$GAME_JAR" "$STAGE/Mindustry.jar"
cp "$MOD_JAR" "$STAGE/mods/ProjectShatteredStar.jar"

unzip -q -o "$JRE_ZIP" -d "$CACHE/jre-src"
JRE_DIR="$(find "$CACHE/jre-src" -mindepth 1 -maxdepth 1 -type d \( -name 'jdk-*-jre' -o -name 'jdk-17*' \) -print -quit)"
[[ -n "$JRE_DIR" && -f "$JRE_DIR/bin/javaw.exe" ]] || { echo "[FATAL] JRE 包结构异常，找不到 javaw.exe" >&2; exit 1; }
cp -a "$JRE_DIR/." "$STAGE/jre/"

# GPL-3.0 合规文件必须随二进制分发。
# 仓库可能不带 LICENSE 全文（本 Mod 仓库即是），缺失时自动从官方下载。
if [[ ! -f "$ROOT/LICENSE" && ! -f "$WIN/LICENSE-Mindustry.txt" ]]; then
    info "补取 GPL-3.0 全文"
    fetch "https://raw.githubusercontent.com/Anuken/Mindustry/v160.7/LICENSE" \
          "https://www.gnu.org/licenses/gpl-3.0.txt" "$ROOT/LICENSE"
fi
# 支持两种布局：仓库根目录（fork 工程）或 windows/ 目录（本 Mod 仓库）。
copy_compliance() {
    local name="$1"; shift
    local src
    for src in "$@"; do
        if [[ -f "$src" ]]; then cp "$src" "$STAGE/$name"; return 0; fi
    done
    echo "[FATAL] 缺合规文件 $name（已查找 $*）" >&2; exit 1
}
copy_compliance LICENSE     "$ROOT/LICENSE"     "$WIN/LICENSE-Mindustry.txt"
copy_compliance NOTICE      "$ROOT/NOTICE"      "$WIN/NOTICE"
copy_compliance SOURCES.md  "$ROOT/SOURCES.md"  "$WIN/SOURCES.md"

python3 - "$WIN/README-Windows.tpl.txt" "$STAGE/README-Windows.txt" <<'PY'
import sys
from pathlib import Path
Path(sys.argv[2]).write_text(Path(sys.argv[1]).read_text(encoding="utf-8"), encoding="utf-8-sig")
PY
cat > "$STAGE/Debug-Console.bat" <<'BAT'
@echo off
REM Launch with a console window so JVM logs stay visible.
"%~dp0Mindustry.exe" --console
echo.
echo Log also at: %%APPDATA%%\Mindustry\last_log.txt
pause
BAT

# 6. 完整性校验
info "校验绿色目录"
[[ -f "$STAGE/jre/bin/javaw.exe" ]] || { echo "[FATAL] 缺 javaw.exe" >&2; exit 1; }
[[ -f "$STAGE/Mindustry.jar" ]] || { echo "[FATAL] 缺 Mindustry.jar" >&2; exit 1; }
[[ -f "$STAGE/mods/ProjectShatteredStar.jar" ]] || { echo "[FATAL] 缺 Mod" >&2; exit 1; }
[[ -f "$STAGE/LICENSE" && -f "$STAGE/NOTICE" && -f "$STAGE/SOURCES.md" ]] \
    || { echo "[FATAL] 缺 GPL 合规文件" >&2; exit 1; }
echo "合规文件：LICENSE / NOTICE / SOURCES.md 已随包"
echo "jre 文件数：$(find "$STAGE/jre" -type f | wc -l)"

# 7. 绿色版 zip
info "打包绿色版 zip"
rm -f "$ZIP_OUT"
(cd "$CACHE" && 7z a -tzip -mx=9 -bso0 -bsp0 "$ZIP_OUT" "Mindustry-ShatteredStar/" >/dev/null)

# 8. NSIS 安装版 exe
info "编译 NSIS 安装程序"
python3 - "$WIN/installer.nsi" "$WIN/.installer-build.nsi" <<'PY'
import sys
from pathlib import Path
data = Path(sys.argv[1]).read_bytes()
if data.startswith(b"\xef\xbb\xbf"):
    data = data[3:]
Path(sys.argv[2]).write_bytes(b"\xef\xbb\xbf" + data)
PY
(
    cd "$WIN"
    makensis -V2 -DSTAGE_DIR="$STAGE" -DOUT_FILE="$SETUP_OUT" ".installer-build.nsi"
) | tail -20
[[ -f "$SETUP_OUT" ]] || { echo "[FATAL] 安装版 exe 未生成" >&2; exit 1; }

# 9. 校验和（相对文件名，便于发布）
info "生成校验和"
(cd "$OUT" && sha256sum "$(basename "$SETUP_OUT")" "$(basename "$ZIP_OUT")" > SHA256SUMS.txt)
cat "$OUT/SHA256SUMS.txt"

info "产物"
ls -lh "$ZIP_OUT" "$SETUP_OUT" "$STAGE/Mindustry.exe"
file "$SETUP_OUT"
echo
echo "完成。分发时请保留包内 LICENSE / NOTICE / SOURCES.md（GPL-3.0 要求）。"
