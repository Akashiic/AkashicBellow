#!/usr/bin/env bash
# Headless client verification under Xvfb (Mesa llvmpipe). Extra gradle args are forwarded,
# e.g. tools/clienttest.sh -Pbellow.compatRun=true -Pbellow.angelica=true
# The frames captured in the room across Y 0 are checked by tools/clientframes.py (flicker, holes).
set -uo pipefail
cd "$(dirname "$0")/.."
mkdir -p build
rm -f run/client/akashicbellow-clienttest.txt run/client/screenshots/bellow-*.png run/client/screenshots/frames-*.png
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew --no-daemon -q runClient21 -Pbellow.clienttest=true "$@" > build/clienttest.log 2>&1
report=run/client/akashicbellow-clienttest.txt
[ -f "$report" ] || { echo "no report (see build/clienttest.log)"; exit 1; }
python3 tools/clientframes.py run/client/screenshots >> "$report"
cat "$report"
! grep -q "RESULT FAIL" "$report"
