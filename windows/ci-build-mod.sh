#!/usr/bin/env bash
# 用 javac 直接编译碎星计划 Java Mod（零 Gradle / 零 jitpack，CI 友好）。
# 用法: ci-build-mod.sh <Mindustry.jar> <输出.jar>
# 自动识别两种源码布局：
#   A. 本仓库：   ./src/  ./assets/  ./mod.hjson        （碎星计划 Mod 仓库）
#   B. fork 工程：./shattered-star-mod/src ...           （Mindustry 源码 fork）
set -euo pipefail

GAME_JAR="${1:?用法: ci-build-mod.sh <Mindustry.jar> <输出.jar>}"
OUT_JAR="${2:?用法: ci-build-mod.sh <Mindustry.jar> <输出.jar>}"
WIN="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$WIN/.." && pwd)"

if [[ -f "$ROOT/src/project/ShatteredStarMod.java" ]]; then
    SRC="$ROOT/src"; ASSETS="$ROOT/assets"; META="$ROOT"
elif [[ -f "$ROOT/shattered-star-mod/src/project/ShatteredStarMod.java" ]]; then
    SRC="$ROOT/shattered-star-mod/src"
    ASSETS="$ROOT/shattered-star-mod/assets"
    META="$ROOT/shattered-star-mod"
else
    echo "[FATAL] 找不到碎星计划 Mod 源码（src/project/ShatteredStarMod.java）" >&2
    exit 1
fi
[[ -f "$GAME_JAR" ]] || { echo "[FATAL] classpath jar 不存在: $GAME_JAR" >&2; exit 1; }
[[ -f "$META/mod.hjson" ]] || { echo "[FATAL] 缺 mod.hjson" >&2; exit 1; }

JAVAC="${JAVAC:-javac}"
JAR="${JAR_TOOL:-jar}"
command -v "$JAVAC" >/dev/null 2>&1 || { echo "[FATAL] 缺 javac（需 JDK 17）" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/classes" "$TMP/jar"

echo "==> 编译 Mod 源码: $SRC"
find "$SRC" -name '*.java' -print0 | xargs -0 "$JAVAC" -encoding UTF-8 -cp "$GAME_JAR" -d "$TMP/classes"
echo "    class 数: $(find "$TMP/classes" -name '*.class' | wc -l)"

# 与官方 gradle jar 任务等价：assets 内容放 jar 根，附 mod.hjson / README.md / classes
cp -a "$ASSETS/." "$TMP/jar/"
cp "$META/mod.hjson" "$TMP/jar/"
[[ -f "$META/README.md" ]] && cp "$META/README.md" "$TMP/jar/"
cp -a "$TMP/classes/." "$TMP/jar/"

mkdir -p "$(dirname "$OUT_JAR")"
(cd "$TMP/jar" && "$JAR" --create --file "$OUT_JAR" .)
echo "==> 产物: $OUT_JAR ($(du -h "$OUT_JAR" | cut -f1))"

# 元数据自检（不用 grep 管道，避免 pipefail + SIGPIPE 误判）
LIST="$("$JAR" --list --file "$OUT_JAR")"
[[ "$LIST" == *"mod.hjson"* ]] || { echo "[FATAL] jar 缺 mod.hjson" >&2; exit 1; }
[[ "$LIST" == *"project/ShatteredStarMod.class"* ]] || { echo "[FATAL] jar 缺主类" >&2; exit 1; }
echo "    mod.hjson 与主类均已打入"
