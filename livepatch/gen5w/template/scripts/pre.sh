#!/system/bin/sh
# Installs the ccNC apps (pm; /system untouched). Runs from the unpacked package before NEMESIS commits anything:
# if an app can't be installed, this fails, NEMESIS stops, and no LivePatch change is applied (the car stays stock
# instead of having its buttons point at an app that isn't there). NEMESIS's depends= already requires
# @NEMESIS_MIN@+ and no 1.x ccNC packages.
. ./scripts/common.sh
wait_android || { log "FAIL: Android isn't up yet; install again once the home screen shows. Nothing was changed."; exit 1; }
rm -f "$PENDING"   # installing again before the restart that would finish an uninstall: keep Climate and Home
before=""
for a in $APPS; do installed "${a#*:}" && before="$before ${a#*:}"; done
pm_begin
bad=""; added=""
for a in $APPS; do
    f=${a%%:*}; p=${a#*:}
    if pm_install "$f"; then
        log "installed $f"
        case " $before " in *" $p "*) ;; *) added="$added $p" ;; esac
    else
        log "FAIL: $f"; bad=$f; break
    fi
done
pm_end
if [ -n "$bad" ]; then
    # Undo only what this run added: on an update the previous version's apps stay and keep working.
    for p in $added; do pm uninstall "$p" >/dev/null 2>&1 && log "removed $p again"; done
    # This attempt's tool results (nothing was committed). On an update the previous version's patches stay
    # mounted, so its records are kept.
    [ -z "$before" ] && rm -rf "$CCNC_DIR/livepatch"
    toast "ccNC couldn't install $bad, so nothing was changed (see NEMESIS's log)."
    exit 1
fi
exit 0
