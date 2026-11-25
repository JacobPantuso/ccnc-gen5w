#!/system/bin/sh
# NEMESIS Manager's Launch: opens ccNC Settings.
export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
pm path dev.ccnc.settings 2>/dev/null | grep -q "^package:" || { echo "ccNC Apps isn't installed: reinstall the package"; exit 1; }
am start -n dev.ccnc.settings/.MainActivity >/dev/null 2>&1 || { echo "couldn't open ccNC Settings"; exit 1; }
exit 0
