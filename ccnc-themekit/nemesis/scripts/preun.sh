#!/system/bin/sh
# Removes the pack; if it was the theme in use, ccNC goes back to its default (ccNC Nebula).
. ./scripts/common.sh
rm -rf "$DEST"
cur=$(content query --uri content://dev.ccnc.home.theme/theme 2>/dev/null | grep -o 'theme=[^,]*' | cut -d= -f2)
[ "$cur" = "pack.$ID" ] && content update --uri content://dev.ccnc.home.theme/theme --bind theme:s:nebula >/dev/null 2>&1
log "removed"
exit 0
