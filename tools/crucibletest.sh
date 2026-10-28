#!/usr/bin/env bash
# Runs the Bellow self test on a copy of a real Crucible server (server.jar, mods/, plugins/, config/, agents).
#
#   tools/crucibletest.sh <server dir> [phase]        phase: full (default), compat, compatverify, bench, chunkload
#
# The server directory must be a disposable copy marked with a .bellow-test-copy file (the world is deleted) and must
# already contain the reobfuscated Bellow jar in mods/. Report: <dir>/akashicbellow-*.txt,
# log: <dir>/crucibletest-<phase>.log. Exit status: the server's, or 1 if the report says FAIL.
#
# JVM flags follow the production command line, with three deliberate differences:
#   - heap HEAP (default 9216M) instead of 14 GB, on test machines with less memory;
#   - young generation G1NEW..G1MAXNEW % (default 5..30) instead of 40..50 %, so the old generation still holds the
#     ~6 GB live set of the pack at 9 GB (at 40 % it does not);
#   - stdin is an open pipe that never sends anything. With -Dterminal.jline=false, Crucible's console reader spins on
#     end-of-file and queues empty ServerCommand objects until the heap is exhausted (2.7 GB measured in 3 minutes).
# Phases that run ticks (compat, chunkload) need BungeeGuard out of the copy: with settings.bungeecord false it shuts
# the server down while plugins are enabled. chunkload also needs an akashicbellow entry in forgeChunkLoading.cfg when
# the copy gives mods without one a quota of 0.
# Missing libraries: Crucible downloads them on first start; if a repository is unreachable, place the jar under
# libraries/ with a .md5 file containing its md5 (Crucible's own cache marker).
set -uo pipefail
dir=${1:?server dir}
phase=${2:-full}
cd "$dir" || exit 2
# Every phase but compatverify deletes the world: refuse to run on anything not explicitly marked as a test copy, so
# pointing this at a real server by mistake cannot destroy its map. Mark a copy with: touch <dir>/.bellow-test-copy
if [ ! -f .bellow-test-copy ]; then
  echo "refusing: $PWD has no .bellow-test-copy marker (this script deletes world/). Use a copy of the server." >&2
  exit 2
fi
# the report this phase writes; only it decides the result (reports of other phases may still be in the directory)
case "$phase" in
  full) report=akashicbellow-selftest.txt ;;
  *) report="akashicbellow-$phase.txt" ;;
esac
# every report goes: a stale one must never pass for this run (compat.dat and the recheck input stay for later phases)
rm -rf crash-reports
for f in akashicbellow-*.txt; do [ "$f" = akashicbellow-recheck-input.txt ] || rm -f -- "$f"; done
# compatverify reads back the world written by compat; every other phase starts from a new world
level=$(sed -n 's/^level-name=//p' server.properties 2>/dev/null | tail -1)
level=${level:-world}
case "$level" in */*|.*|"") echo "refusing: unexpected level-name '$level'" >&2; exit 2 ;; esac
[ "$phase" = compatverify ] || rm -rf -- "$level"
agents=()
for a in *-agent.jar; do [ -f "$a" ] && agents+=("-javaagent:$PWD/$a"); done
JAVA=${JAVA:-java}
log="crucibletest-$phase.log"
# Stall watchdog: if no report appears STALL seconds (default 600) after "Done", dump the threads once to
# crucibletest-<phase>-stall.txt (the report is written only when the whole test finished).
rm -f "crucibletest-$phase-stall.txt"
(
  # the JVM itself, not the timeout wrapper whose command line holds the same arguments
  server() { [ -s "crucibletest-$phase.pid" ] && pgrep -P "$(cat "crucibletest-$phase.pid")" | head -1; }
  sleep 30
  until grep -aq 'Done (' "$log" 2>/dev/null; do sleep 5; [ -n "$(server)" ] || exit 0; done
  for _ in $(seq 1 $(( ${STALL:-600} / 5 ))); do
    [ -f "$report" ] && exit 0
    [ -n "$(server)" ] || exit 0
    sleep 5
  done
  pid=$(server)
  [ -n "$pid" ] && jstack "$pid" > "crucibletest-$phase-stall.txt" 2>&1 && echo "stall: threads dumped to crucibletest-$phase-stall.txt"
) &
# stdin: an open pipe that never sends anything, closed with the run (no sleep left behind)
exec 3< <(exec sleep 86400)
sleeper=$!
timeout "${TIMEOUT:-3600}" "$JAVA" -Duser.timezone=America/Sao_Paulo -Dterminal.jline=false -Dterminal.ansi=true \
  -Dfile.encoding=UTF8 -Xms"${HEAP:-9216M}" -Xmx"${HEAP:-9216M}" -XX:+UseG1GC -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC \
  -XX:G1NewSizePercent="${G1NEW:-5}" -XX:G1MaxNewSizePercent="${G1MAXNEW:-30}" -XX:G1HeapRegionSize=16M \
  -XX:G1ReservePercent=15 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=20 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 "${agents[@]}" @java9args.txt \
  -Dfml.doNotBackup=true -Dfml.queryResult=confirm -Dakashicbellow.selftest=true -Dakashicbellow.selftest.exit=true \
  -Dakashicbellow.selftest.phase="$phase" -jar server.jar nogui > "$log" 2>&1 <&3 &
echo $! > "crucibletest-$phase.pid"
wait $!
rc=$?
kill "$sleeper" 2>/dev/null
exec 3<&-
rm -f "crucibletest-$phase.pid"

echo "crucible $phase rc=$rc"
grep -ahE "^(FAIL|FINDING|RESULT|SKIP)" "$report" 2>/dev/null
ls crash-reports 2>/dev/null
[ "$rc" -ne 0 ] && exit "$rc"
# a server that dies at boot (e.g. a mixin that found no target) writes no report
[ -f "$report" ] || { echo "no report $report (see crucibletest-$phase.log)"; exit 1; }
grep -aq "^RESULT FAIL" "$report" && exit 1
exit 0
