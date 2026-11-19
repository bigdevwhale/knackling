#!/usr/bin/env bash
# Installs an APK on the test phone and taps MIUI's «Install via USB» confirmation (only when that dialog has focus).
#   ANDROID_SERIAL=d7a925e6 tools/install.sh app/build/outputs/apk/debug/app-debug.apk
set -u
export MSYS_NO_PATHCONV=1
apk=$1; log=$(mktemp)
adb install -r "$apk" > "$log" 2>&1 &
pid=$!
for i in $(seq 1 15); do
  sleep 1
  kill -0 $pid 2>/dev/null || break
  if adb shell dumpsys window | grep -m1 mCurrentFocus | grep -q AdbInstallActivity; then
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    b=$(adb shell cat /sdcard/ui.xml | grep -o 'text="\(Установить\|Install\)" [^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o '\[[0-9,]*\]\[[0-9,]*\]' | head -1)
    if [ -n "$b" ]; then
      set -- $(echo "$b" | sed 's/\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]/\1 \2 \3 \4/')
      adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); break
    fi
  fi
done
wait $pid; cat "$log"; rm -f "$log"
