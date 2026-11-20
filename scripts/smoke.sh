#!/usr/bin/env bash
# Smoke test: đi qua các màn hình chính bằng cách bấm theo chữ hiển thị, chụp ảnh, kiểm tra crash.
set -x
mkdir -p out
adb install -r apk/*.apk || { echo "INSTALL FAILED" > out/result.txt; exit 1; }
adb shell input keyevent 82

SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}

shot() { adb exec-out screencap -p > "out/$1.png"; }

find_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml out/ui.xml > /dev/null 2>&1
  python3 - "$1" <<'PY'
import sys, re, xml.etree.ElementTree as ET
t = sys.argv[1].lower()
try:
    tree = ET.parse('out/ui.xml')
except Exception:
    sys.exit(0)
for n in tree.iter('node'):
    txt = (n.get('text') or '') + ' ' + (n.get('content-desc') or '')
    if t in txt.lower():
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.get('bounds') or '')
        if m:
            print((int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2)
            break
PY
}
tap_text()  { read -r X Y < <(find_text "$1"); if [ -n "$X" ]; then adb shell input tap "$X" "$Y"; echo "tapped '$1' at $X,$Y"; else echo "NOT FOUND: $1"; fi; }
hold_text() { read -r X Y < <(find_text "$1"); if [ -n "$X" ]; then adb shell input swipe "$X" "$Y" "$X" "$Y" 900; echo "held '$1' at $X,$Y"; else echo "NOT FOUND: $1"; fi; }

adb logcat -c

# 0) Ứng dụng khởi động: Xin chào -> Quyền -> Mặc định -> Xong
adb shell am start -n com.example.mylauncher/.OnboardingActivity
sleep 4;  shot 00_hello
tap_text "Bắt đầu";  sleep 2;  shot 00b_permissions
tap_text "Tiếp tục"; sleep 2;  shot 00c_default
tap_text "Để sau";   sleep 2;  shot 00d_done

# 1) Đặt làm launcher mặc định rồi về màn hình chính
adb shell cmd package set-home-activity com.example.mylauncher/.MainActivity
adb shell input keyevent KEYCODE_HOME
sleep 8;  shot 01_home

# 2) Spotlight + phép tính + nút Back
adb shell input swipe $((W/2)) $((H*30/100)) $((W/2)) $((H*72/100)) 400
sleep 3;  shot 02_spotlight
adb shell input text "12+30"
sleep 2;  shot 03_spotlight_calc
adb shell input keyevent KEYCODE_BACK; sleep 2; shot 03b_back1
adb shell input keyevent KEYCODE_BACK; sleep 2; shot 03c_back2
adb shell input keyevent KEYCODE_BACK; sleep 2; shot 03d_back3

# 3) Thư viện ứng dụng kiểu iOS: thẻ danh mục -> chạm ô tìm kiếm (A-Z) -> Back -> mở danh mục
adb shell input swipe $((W/2)) $((H*88/100)) $((W/2)) $((H*20/100)) 400
sleep 3;  shot 04_library
tap_text "Thư viện ứng dụng"; sleep 2; shot 04b_library_search_az
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 2; shot 04c_library_after_back
tap_text "Tiện ích"; sleep 2; shot 05_category
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 2; shot 05b_after_library_back

# 4) Giữ lâu icon -> menu kiểu iOS (icon nhấc lên)
hold_text "Settings"; sleep 2; shot 06_icon_menu
tap_text "Chỉnh sửa"; sleep 2; shot 07_edit_mode

# 5) Kéo "Photos" thả vào giữa "YouTube" để tạo thư mục
read -r PX PY < <(find_text "Photos"); read -r YX YY < <(find_text "YouTube")
if [ -n "$PX" ] && [ -n "$YX" ]; then
  adb shell input draganddrop "$PX" $((PY-36)) "$YX" $((YY-36)) 1500
  sleep 2; shot 07d_folder_created
  tap_text "Xong"; sleep 2; shot 07e_after_done
  adb shell input tap "$YX" $((YY-36)); sleep 2; shot 07f_folder_open
  adb shell input keyevent KEYCODE_BACK; sleep 2; shot 07g_folder_closed
fi

# 6) Mở ứng dụng (icon phóng to) rồi quay lại (icon thu về)
tap_text "Settings"
sleep 0.2; shot 08_launching_a
sleep 0.6; shot 08_launching_b
sleep 3
adb shell input keyevent KEYCODE_HOME
sleep 0.4; shot 09_returning_a
sleep 2;   shot 09_returning_b

adb logcat -d > out/logcat.txt
grep -E "MyLauncher" out/logcat.txt > out/mylauncher_log.txt || true
if grep -E "FATAL EXCEPTION|AndroidRuntime: Process: com.example.mylauncher" out/logcat.txt > /dev/null; then
  echo "CRASH" > out/result.txt
  grep -B2 -A30 "FATAL EXCEPTION" out/logcat.txt | head -150 >> out/result.txt
  cat out/result.txt
  exit 1
fi
echo "OK: không có crash" > out/result.txt
