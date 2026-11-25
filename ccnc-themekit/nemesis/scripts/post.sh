#!/system/bin/sh
# Copies the pack into ccNC Home's storage (files/themes/<id>), owned by Home, SELinux labels restored.
. ./scripts/common.sh
U=$(home_uid)
[ -n "$U" ] || { log "FAIL: ccNC Home not found"; exit 1; }
mkdir -p "$APP/files/themes" || exit 1
rm -rf "$DEST"
cp -r files/theme "$DEST" || { rm -rf "$DEST"; log "FAIL: copy failed"; exit 1; }
chown "$U:$U" "$APP/files" "$APP/files/themes"
chown -R "$U:$U" "$DEST"
chmod 0771 "$APP/files"; chmod 0700 "$APP/files/themes"
chmod -R 0700 "$DEST"
restorecon -R "$APP/files" 2>/dev/null
[ -f "$DEST/theme.json" ] || { log "FAIL: theme.json missing after copy"; exit 1; }
log "installed"
toast "Theme installed: pick it in Settings > Screen Theme > ccNC Theme"
exit 0
