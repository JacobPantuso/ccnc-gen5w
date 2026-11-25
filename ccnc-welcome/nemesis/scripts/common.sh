#!/system/bin/sh
# Shared by the ccNC Startup Video scripts (full root, cwd = the package dir).
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
umask 022

ID=ccnc.welcome
APP=/system/app/WelcomeGoodbye.apk
# Size of the stock file on KVT49L (CEEV.CAN). Anything else is not stock: don't touch it.
STOCK_SIZE=37579235
BK=/data/nemesis/backup/$ID
TMP=/data/local/tmp/ccnc-welcome.apk
# Real (non-stub) video entries in the stock apk, one per brand variant.
ENTRIES="res/raw/welcome_hyundai_new_jong_e_1920.mp4 res/raw/welcome_hyundai_jong_e_1920.mp4"

log() { echo "ccNC Startup Video: $1"; }

toast() {
    [ "$(getprop sys.boot_completed)" = 1 ] || return 0
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}

# File size in bytes without stat/wc: ls -l column 4 (toolbox ls prints "-rw-r--r-- root root 123 date time name").
size_of() {
    set -- $(ls -l "$1" 2>/dev/null)
    echo "$4"
}

# Writes $1 over the system apk. A new inode (cp + mv) so a running WelcomeService keeps
# its mapped copy; the new file is used from its next start.
install_apk() {
    nemesis remount rw || return 1
    cp "$1" "$APP.new" && chmod 644 "$APP.new" && chown root:root "$APP.new" 2>/dev/null && mv "$APP.new" "$APP"
    r=$?
    rm -f "$APP.new"
    nemesis remount ro
    return $r
}
