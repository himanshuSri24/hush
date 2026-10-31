#!/usr/bin/env bash
# End-to-end test of Hush on a real phone (USB or Wireless debugging).
# Installs the DEBUG build (com.devwithcoffee.hush.debug, sits beside the real app),
# drives it over adb, asserts DND + hold/through behaviour, and saves screenshots.
#
#   cd Hush && ./gradlew assembleDebug && bash scripts/device-test.sh
set -u
cd "$(dirname "$0")/.."
A=${ADB:-adb}
PKG=com.devwithcoffee.hush.debug
RCV=$PKG/com.devwithcoffee.hush.DebugReceiver
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=scripts/out; mkdir -p "$OUT"
pass=0; fail=0

ok()   { echo "  PASS  $1"; pass=$((pass+1)); }
bad()  { echo "  FAIL  $1"; fail=$((fail+1)); }
check(){ if eval "$2"; then ok "$1"; else bad "$1  ($2)"; fi; }
b()    { $A shell am broadcast -a "$1" -n $RCV "${@:2}" >/dev/null; sleep 1; }
post() { $A shell "cmd notification post -t '$2' $1 '$3'" >/dev/null; sleep 2; }
zen()  { $A shell settings get global zen_mode | tr -d '\r'; }
state(){ b hush.test.DUMP; $A logcat -d -s HushTest:I | grep STATE | tail -1; }
alerts(){ $A logcat -d -s HushAlert:I | grep -c breakthrough; }
shot() { $A exec-out screencap -p > "$OUT/$1.png"; }
tap_text() {  # tap the first UI node whose text is exactly $1
  $A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local b; b=$($A shell cat /sdcard/ui.xml | tr '>' '\n' | grep "text=\"$1\"" | head -1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
  [ -z "$b" ] && return 1
  set -- $b; $A shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 1.5
}

[ "$($A get-state 2>/dev/null)" = "device" ] || { echo "No phone connected. Plug in (USB debugging) or pair Wireless debugging first."; exit 1; }
echo "Phone: $($A shell getprop ro.product.model | tr -d '\r'), Android $($A shell getprop ro.build.version.release | tr -d '\r')"
ORIG_ZEN=$(zen)

echo "== install + permissions"
$A install -r -g "$APK" >/dev/null && ok "installed debug build" || { bad "install"; exit 1; }
$A shell cmd notification allow_listener $PKG/com.devwithcoffee.hush.engine.HushListener
$A shell cmd notification allow_dnd $PKG
for p in READ_PHONE_STATE READ_CALL_LOG READ_CONTACTS POST_NOTIFICATIONS; do $A shell pm grant $PKG android.permission.$p 2>/dev/null; done
$A shell am start -n $PKG/com.devwithcoffee.hush.ui.MainActivity >/dev/null; sleep 3
$A logcat -c
check "notification listener connected" "$A shell dumpsys notification | grep -q '$PKG/com.devwithcoffee.hush.engine.HushListener'"

echo "== configure"
b hush.test.RESET
b hush.test.VIP --es name "Mom Test" --es num "+91 98765 43210"

echo "== hush on"
b hush.test.START --ei min 5
sleep 1
check "phone is in DND alarms-only (zen_mode=3)" "[ \"$(zen)\" = 3 ]"
check "status notification shown" "$A shell dumpsys notification --noredact | grep -q 'pkg=$PKG.*id=1 '"

echo "== a VIP message gets through"
before=$(alerts)
post hush_vip "Mom Test" "are you free?"
check "VIP 'Mom Test' triggered a breakthrough" "[ $(alerts) -gt $before ]"
check "breakthrough alert notification posted" "$A shell dumpsys notification --noredact | grep -q 'channel=breakthrough'"

echo "== a stranger is held"
before=$(alerts)
post hush_rand "Random Person" "hey"
check "stranger did not ring" "[ $(alerts) -eq $before ]"
check "stranger logged as held" "state | grep -q 'Random Person\",\"through\":false'"

echo "== an allowed app gets through"
b hush.test.ALLOW --es pkg com.android.shell
before=$(alerts)
post hush_work "Priya" "standup in 5"
check "allowed app triggered a breakthrough" "[ $(alerts) -gt $before ]"
check "reason recorded as ALLOWED_APP" "state | grep -q 'ALLOWED_APP'"

echo "== screenshots"
$A shell am start -n $PKG/com.devwithcoffee.hush.ui.MainActivity >/dev/null; sleep 2
shot 1-now-on
tap_text "Allowed" && shot 2-allowed
tap_text "Schedule" && shot 3-schedule
tap_text "Setup" && shot 4-setup
tap_text "Now"

echo "== hush off"
b hush.test.STOP
sleep 1
check "DND restored (zen_mode back to $ORIG_ZEN)" "[ \"$(zen)\" = \"$ORIG_ZEN\" ] || [ \"$(zen)\" = 0 ]"
check "welcome-back summary posted" "$A shell dumpsys notification --noredact | grep -q 'channel=summary'"
check "session ended" "state | grep -q '\"active\":false'"
shot 5-after
$A shell cmd notification cancel_all hush_vip 2>/dev/null

echo
echo "Result: $pass passed, $fail failed. Screenshots in $OUT/"
echo "Not covered here (needs a second phone): a real SIM call + a real WhatsApp call from a VIP."
[ $fail -eq 0 ]
