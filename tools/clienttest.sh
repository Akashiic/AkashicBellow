#!/usr/bin/env bash
# Headless client verification under Xvfb (Mesa llvmpipe). Extra gradle args are forwarded,
# e.g. tools/clienttest.sh -Pbellow.compatRun=true -Pbellow.angelica=true
set -uo pipefail
cd "$(dirname "$0")/.."
rm -f run/client/akashicbellow-clienttest.txt run/client/screenshots/bellow-*.png
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew --no-daemon -q runClient21 -Pbellow.clienttest=true "$@" > build/clienttest.log 2>&1
cat run/client/akashicbellow-clienttest.txt 2>/dev/null || { echo "no report (see build/clienttest.log)"; exit 1; }
! grep -q "^RESULT FAIL" run/client/akashicbellow-clienttest.txt
