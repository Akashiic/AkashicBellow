#!/usr/bin/env bash
# Headless end-to-end verification on a dedicated dev server (Java 21 + lwjgl3ify + Hodgepodge).
#   1. bootstrap: world created with worldgen disabled  -> "legacy" chunks without deep data
#   2. full:      worldgen enabled -> retrogen of legacy chunks, terrain, blocks, light, ores; writes markers
#   3. verify:    fresh JVM reads the markers back from disk
# Usage: tools/selftest.sh [extra gradle args, e.g. -Pbellow.compatRun=true]
set -euo pipefail
cd "$(dirname "$0")/.."
RUN=run/server
CFG=$RUN/config/akashicbellow.cfg
mkdir -p "$RUN"
echo "eula=true" > "$RUN/eula.txt"
printf "online-mode=false\nspawn-protection=0\nlevel-seed=akashic\nmax-tick-time=-1\n" > "$RUN/server.properties"
rm -rf "$RUN/world" "$RUN/crash-reports" "$RUN"/akashicbellow-selftest*.txt

set_gen() { [ -f "$CFG" ] && sed -i "s/B:enabled=.*/B:enabled=$1/" "$CFG" || true; }
phase() {
  ./gradlew --no-daemon -q runServer21 -Pbellow.selftest=true -Pbellow.phase="$1" "${@:2}" > "build/selftest-$1.log" 2>&1 || true
  if [ -f "$RUN/akashicbellow-selftest.txt" ]; then mv "$RUN/akashicbellow-selftest.txt" "$RUN/akashicbellow-selftest-$1.txt"; fi
  echo "== phase $1"; cat "$RUN/akashicbellow-selftest-$1.txt" 2>/dev/null || { echo "no report (see build/selftest-$1.log)"; return 1; }
}

# The config file only exists after the first launch; bootstrap twice-safe: create it, then disable gen.
if [ ! -f "$CFG" ]; then phase bootstrap "$@"; rm -rf "$RUN/world"; fi
set_gen false; phase bootstrap "$@"
set_gen true;  phase full "$@"
phase verify "$@"
grep -h "^RESULT\|^FAIL" "$RUN"/akashicbellow-selftest-*.txt || true
