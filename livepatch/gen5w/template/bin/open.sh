#!/system/bin/sh
# NEMESIS Manager's Launch ([app.0]): opens ccNC Home. NEMESIS Manager shows what
# this prints as a toast. No package=/component= in NEMESIS.data on purpose:
# NEMESIS would pm uninstall that app on an update, losing the owner's settings.
export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
if ! pm path dev.ccnc.home 2>/dev/null | grep -q "^package:"; then
    echo "ccNC Home isn't installed: reinstall the package"
    exit 1
fi
am start -n dev.ccnc.home/.HomeActivity >/dev/null 2>&1 || { echo "couldn't open ccNC Home"; exit 1; }
exit 0
