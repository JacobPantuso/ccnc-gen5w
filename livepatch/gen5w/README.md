# ccnc.gen5w: one LivePatch package (DRAFT, not published)

Everything in one required NEMESIS package: the 11 ccNC apps (pm installed, /system untouched) plus four
LivePatch steps (NEMESIS mounts patched copies at boot; nothing is written to /system):

| step | target | tool | on a firmware it doesn't fit |
|---|---|---|---|
| patch.1 volume | /system/framework/automotive-service.odex | `bin/lp-ccnc volume` | left stock (exit 10) |
| patch.2 sideosd | /system/app/RightScreenHome.odex | `bin/lp-ccnc sideosd` (only canShowSSOsd(), WithoutVR(), WithDispOff()) | left stock |
| patch.3 climate | /system/app/HKMC_Climate.odex | `bin/lp-ccnc climate` | left stock |
| patch.4 welcome | /system/app/WelcomeGoodbye.apk | `bin/lp-welcome` (Kia/Hyundai video by brand; only welcome_hyundai*/welcome_kia* entries) | left as it is |

Build: `./mkgen5w.sh` (or `REBUILD=1`, `NEMESIS_MIN=<x>`, `V=<x>`) -> dist/ (package zip, USB stick zip, unpacked tree).

Before it goes anywhere near a car:
1. The fixed NEMESIS release is out and its notes no longer say "do not install with HMG-API"; read its
   boot wrapper (LIVEPATCH_PLAN.md has the checks). Set NEMESIS_MIN to its real version and check that
   `nemesis version` prints what scripts/pre.sh expects.
2. On the car: uninstall every 1.x ccNC package (ccnc.apps, ccnc.home, ccnc.volume, ccnc.sideosd,
   ccnc.climatebtn, ccnc.welcome), restart, then install this one; restart again.
3. Check: Home, climate + seat buttons, no volume strip / side popups, no "Media off" at boot, wireless
   CarPlay (HMG-API hooks still on: /data/hmgapi/stock/system/framework/automotive-service.odex present), startup video.
