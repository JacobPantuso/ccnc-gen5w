#!/system/bin/sh
# Installs the tools ccNC's developer menu (tap the clock 7 times) uses; nothing runs now. Run as root by the
# ccNC Home / ccNC Apps postinstall (cwd = the package dir).
#   /data/ccnc/verify.sh   the on-car check (Developer menu > Run checks), same as scripts/verify.sh
#   /data/ccnc/nemesis-d   a non-SUID copy of /system/xbin/nemesis: its "root -c" goes through the NEMESIS daemon
#                          (full root), where the SUID binary run by an app only gets a limited root.
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
D=/data/ccnc
mkdir -p "$D" && chmod 0755 "$D"
cp ./scripts/verify.sh "$D/verify.sh" && chmod 0755 "$D/verify.sh"
[ -f /system/xbin/nemesis ] && cp /system/xbin/nemesis "$D/nemesis-d" && chmod 0755 "$D/nemesis-d"
chown root:root "$D" "$D/verify.sh" "$D/nemesis-d" 2>/dev/null
echo "ccNC dev tools: $(ls $D 2>/dev/null)"
exit 0
