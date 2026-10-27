#!/usr/bin/env bash
# Runs the Bellow self test on a copy of a real Crucible server (server.jar, mods/, plugins/, config/, agents).
#
#   tools/crucibletest.sh <server dir> [phase]        phase: full (default), compat, compatverify, bench
#
# The server directory must already contain the reobfuscated Bellow jar in mods/. Report: <dir>/akashicbellow-*.txt,
# log: <dir>/crucibletest-<phase>.log.
#
# JVM flags follow the production command line, with three deliberate differences:
#   - heap HEAP (default 9216M) instead of 14 GB, on test machines with less memory;
#   - young generation G1NEW..G1MAXNEW % (default 5..30) instead of 40..50 %, so the old generation still holds the
#     ~6 GB live set of the pack at 9 GB (at 40 % it does not);
#   - stdin is an open pipe that never sends anything. With -Dterminal.jline=false, Crucible's console reader spins on
#     end-of-file and queues empty ServerCommand objects until the heap is exhausted (2.7 GB measured in 3 minutes).
# Missing libraries: Crucible downloads them on first start; if a repository is unreachable, place the jar under
# libraries/ with a .md5 file containing its md5 (Crucible's own cache marker).
set -uo pipefail
dir=${1:?server dir}
phase=${2:-full}
cd "$dir" || exit 2
rm -rf crash-reports akashicbellow-selftest.txt
# compatverify reads back the world written by compat; every other phase starts from a new world
[ "$phase" = compatverify ] || rm -rf world
agents=()
for a in *-agent.jar; do [ -f "$a" ] && agents+=("-javaagent:$PWD/$a"); done
JAVA=${JAVA:-java}
timeout "${TIMEOUT:-3600}" "$JAVA" -Duser.timezone=America/Sao_Paulo -Dterminal.jline=false -Dterminal.ansi=true \
  -Dfile.encoding=UTF8 -Xms"${HEAP:-9216M}" -Xmx"${HEAP:-9216M}" -XX:+UseG1GC -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC \
  -XX:G1NewSizePercent="${G1NEW:-5}" -XX:G1MaxNewSizePercent="${G1MAXNEW:-30}" -XX:G1HeapRegionSize=16M \
  -XX:G1ReservePercent=15 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=20 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 "${agents[@]}" @java9args.txt \
  -Dfml.doNotBackup=true -Dfml.queryResult=confirm -Dakashicbellow.selftest=true -Dakashicbellow.selftest.exit=true \
  -Dakashicbellow.selftest.phase="$phase" -jar server.jar nogui > "crucibletest-$phase.log" 2>&1 < <(exec sleep 86400)
rc=$?
echo "crucible $phase rc=$rc"
grep -ahE "^(FAIL|FINDING|RESULT|SKIP)" akashicbellow-*.txt 2>/dev/null
ls crash-reports 2>/dev/null
