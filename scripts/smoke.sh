#!/usr/bin/env bash
# Smoke test: cài APK, đặt làm launcher, chụp màn hình các trạng thái chính, kiểm tra crash.
set -x
mkdir -p out
adb install -r apk/*.apk || { echo "INSTALL FAILED" > out/result.txt; exit 1; }
adb shell input keyevent 82
adb shell cmd package set-home-activity com.example.mylauncher/.MainActivity
adb logcat -c
adb shell input keyevent KEYCODE_HOME
sleep 8
adb exec-out screencap -p > out/01_home.png

SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}

# Vuốt lên mở thư viện ứng dụng
adb shell input swipe $((W/2)) $((H*88/100)) $((W/2)) $((H*20/100)) 400
sleep 3
adb exec-out screencap -p > out/02_drawer.png

# Mở một danh mục (thẻ đầu tiên)
adb shell input tap $((W/4)) $((H*28/100))
sleep 2
adb exec-out screencap -p > out/03_category.png

# Back -> về màn hình chính, giữ lâu nền để mở menu
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input swipe $((W/2)) $((H*55/100)) $((W/2)) $((H*55/100)) 900
sleep 2
adb exec-out screencap -p > out/04_home_menu.png

# Chạm 2 lần (khoá màn hình cần dịch vụ trợ năng nên chỉ kiểm tra không crash)
adb shell input tap $((W/2)) $((H*50/100))
adb shell input tap $((W/2)) $((H*50/100))
sleep 2
adb exec-out screencap -p > out/05_after_double_tap.png

adb logcat -d > out/logcat.txt
if grep -E "FATAL EXCEPTION|AndroidRuntime: Process: com.example.mylauncher" out/logcat.txt > /dev/null; then
  echo "CRASH" > out/result.txt
  grep -B2 -A30 "FATAL EXCEPTION" out/logcat.txt | head -120 >> out/result.txt
  cat out/result.txt
  exit 1
fi
echo "OK: không có crash" > out/result.txt
