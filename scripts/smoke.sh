#!/usr/bin/env bash
# Smoke test: cài APK, chụp màn hình các trạng thái chính, kiểm tra crash.
set -x
mkdir -p out
adb install -r apk/*.apk || { echo "INSTALL FAILED" > out/result.txt; exit 1; }
adb shell input keyevent 82

SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}

# 0) Ứng dụng khởi động (icon ngoài màn hình): Xin chào
adb logcat -c
adb shell am start -n com.example.mylauncher/.OnboardingActivity
sleep 4
adb exec-out screencap -p > out/00_hello.png
adb shell input tap $((W/2)) $((H*91/100))   # Bắt đầu
sleep 2
adb exec-out screencap -p > out/00b_permissions.png

# 1) Đặt làm launcher mặc định rồi về màn hình chính
adb shell cmd package set-home-activity com.example.mylauncher/.MainActivity
adb shell input keyevent KEYCODE_HOME
sleep 8
adb exec-out screencap -p > out/01_home.png

# 2) Vuốt xuống -> Spotlight, gõ phép tính
adb shell input swipe $((W/2)) $((H*30/100)) $((W/2)) $((H*72/100)) 400
sleep 3
adb exec-out screencap -p > out/02_spotlight.png
adb shell input text "12+30"
sleep 2
adb exec-out screencap -p > out/03_spotlight_calc.png
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1

# 3) Vuốt lên -> thư viện ứng dụng
adb shell input swipe $((W/2)) $((H*88/100)) $((W/2)) $((H*20/100)) 400
sleep 3
adb exec-out screencap -p > out/04_library.png
adb shell input tap $((W/4)) $((H*28/100))
sleep 2
adb exec-out screencap -p > out/05_category.png
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2

# 4) Giữ lâu nền -> menu, rồi chỉnh sửa (rung lắc)
adb shell input swipe $((W/2)) $((H*60/100)) $((W/2)) $((H*60/100)) 900
sleep 2
adb exec-out screencap -p > out/06_home_menu.png
adb shell input tap $((W/2)) $((H*60/100))
sleep 2
adb exec-out screencap -p > out/07_edit_or_after.png

# 5) Mở một ứng dụng (animation icon phóng to) rồi quay lại
adb shell input tap $((W/8*1+W/16)) $((H*50/100))
sleep 1
adb exec-out screencap -p > out/08_launching.png
sleep 3
adb shell input keyevent KEYCODE_HOME
sleep 1
adb exec-out screencap -p > out/09_returning.png
sleep 2

adb logcat -d > out/logcat.txt
if grep -E "FATAL EXCEPTION|AndroidRuntime: Process: com.example.mylauncher" out/logcat.txt > /dev/null; then
  echo "CRASH" > out/result.txt
  grep -B2 -A30 "FATAL EXCEPTION" out/logcat.txt | head -150 >> out/result.txt
  cat out/result.txt
  exit 1
fi
echo "OK: không có crash" > out/result.txt
