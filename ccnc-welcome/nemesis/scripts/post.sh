#!/system/bin/sh
# Backs up the stock apk (once), patches the welcome video into a copy, installs it.
. ./scripts/common.sh

mkdir -p "$BK"
if [ ! -f "$BK/stock.apk" ]; then
    cp "$APP" "$BK/stock.apk" || { log "FAIL: couldn't save the backup"; exit 1; }
    [ "$(size_of "$BK/stock.apk")" = "$STOCK_SIZE" ] || { rm -f "$BK/stock.apk"; log "FAIL: backup is incomplete (disk full?)"; exit 1; }
    log "stock apk saved to $BK"
fi

# Brand as the ccNC apps work it out: firmware product prefix (CVEV = Kia EV6), else the vendor code (...kmc = Kia).
is_kia() {
    case "$(getprop ro.build.product)" in CVEV*) return 0;; CEEV*|NEEV*) return 1;; esac
    case "$(getprop ro.product.brand)" in *kia*|*kmc) return 0;; esac
    return 1
}
VIDEO=files/welcome_hyundai.mp4
is_kia && [ -f files/welcome_kia.mp4 ] && VIDEO=files/welcome_kia.mp4
args=""
for e in $ENTRIES; do args="$args $e=$VIDEO"; done

# Evidence for the report: which videos the stock apk holds (sizes), and what the entries were before patching.
app_process -Djava.class.path=files/patch.jar /system/bin dev.ccnc.welcome.Patch list "$BK/stock.apk" > /data/local/tmp/ccnc-welcome-entries.txt 2>&1

# What the other startup visuals are (boot animation zip), for working out which one the car shows.
for z in /system/media/bootanimation.zip /data/local/bootanimation.zip; do
    [ -f "$z" ] && { echo "=== $z"; app_process -Djava.class.path=files/patch.jar /system/bin dev.ccnc.welcome.Patch zipinfo "$z" desc.txt; } >> /data/local/tmp/ccnc-welcome-entries.txt 2>&1
done
{ echo "=== package state before patching"; pm list packages -f 2>/dev/null | grep -i "mobis.welcome"; ls -l /system/app/WelcomeGoodbye.* 2>&1; logcat -d -v time 2>/dev/null | grep -i "welcome\|digest\|signature\|PackageManager.*Parse" | while read l; do echo "  $l"; done; } >> /data/local/tmp/ccnc-welcome-entries.txt 2>&1
getprop | grep -i "boot\|welcome\|anim" >> /data/local/tmp/ccnc-welcome-entries.txt 2>&1
# Copy to any USB stick so the Mac can read it without another check run.
for u in /storage/usb0 /storage/usb1 /storage/usb2; do
    [ -d "$u" ] && { mkdir -p "$u/ccNC reports" && cp /data/local/tmp/ccnc-welcome-entries.txt "$u/ccNC reports/welcome-info.txt"; } 2>/dev/null
done

rm -f "$TMP"
app_process -Djava.class.path=files/patch.jar /system/bin dev.ccnc.welcome.Patch "$BK/stock.apk" "$TMP" $args || { rm -f "$TMP"; log "FAIL: couldn't build the patched apk. Nothing was changed."; exit 1; }
s=$(size_of "$TMP")
[ -n "$s" ] && [ "$s" -gt 1000000 ] || { rm -f "$TMP"; log "FAIL: patched apk looks wrong ($s bytes). Nothing was changed."; exit 1; }

install_apk "$TMP"
r=$?
rm -f "$TMP"
if [ $r -ne 0 ]; then
    log "FAIL: couldn't write $APP; restoring the stock file"
    install_apk "$BK/stock.apk"
    exit 1
fi
log "installed: $VIDEO plays at the next start"
toast "ccNC startup video installed. It plays at the next start."
exit 0
