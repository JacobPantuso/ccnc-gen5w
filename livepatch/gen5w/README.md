# ccnc.gen5w: the ccNC for Gen5W package

Everything in one required NEMESIS package: the 11 ccNC apps (pm installed, /system untouched) plus four
LivePatch steps (NEMESIS mounts patched copies at boot; nothing is written to /system):

| step | target | tool | on a firmware it doesn't fit |
|---|---|---|---|
| patch.1 volume | /system/framework/automotive-service.odex | `bin/lp-ccnc volume` (volume strip + the system's boot AV-off OSD) | left stock (exit 10) |
| patch.2 sideosd | /system/app/RightScreenHome.odex | `bin/lp-ccnc sideosd` (canShowSSOsd(), canShowSSOsd(ZZZ), WithoutVR(), WithDispOff()) | left stock |
| patch.3 climate | /system/app/HKMC_Climate.odex | `bin/lp-ccnc climate` | left stock |
| patch.4 welcome | /system/app/WelcomeGoodbye.apk | `bin/lp-welcome` (Kia/Hyundai video by brand; only welcome_hyundai*/welcome_kia* entries) | left as it is |

Build: `V=<version> ./mkgen5w.sh` (always a clean car build signed with keys/ccnc-apps.keystore; `NEMESIS_MIN=<x>` to change the minimum) -> dist/ (package zip, USB stick zip, .sha256, unpacked tree).

## Install, update and uninstall

| Phase | What happens |
|---|---|
| LivePatch prepare | NEMESIS runs each step's tool on the stock file. Doesn't fit → file left stock (exit 10); tool error → install stops, nothing committed. Each tool records its result in `/data/ccnc/livepatch/<step>` for the developer menu. |
| `scripts/pre.sh` | Installs the 11 apps (one `sys.boot_completed` toggle around all of them). If one fails, the apps this run added are removed again and the install stops **before** NEMESIS commits the patches. |
| commit | NEMESIS records the package; the patched files are mounted at the next restart. |
| `scripts/post.sh` | Notification listener, developer-menu tools, missing step records (from `nemesis livepatch status`), HMG-API stock copies of `automotive-service.odex` and `RightScreenHome.odex` (from `nemesis livepatch stock`; recorded in `/data/ccnc/hmgapi-stock`, refreshed when stock changes). Never fails the install. |
| update | The old version's `preun.sh` sees the new version staged and keeps the apps; the new `pre.sh` installs over them. |
| uninstall (`preun.sh`) | Removes the other apps, the listener, our HMG-API stock copies and records now. ccNC Climate and Home stay until the restart (the patched files stay mounted until then); Home removes both and `/data/ccnc` at the next boot (`UninstallCleanup`). |

## Car test before a release

1. Install (or update) on the car; restart.
2. Developer menu: all four steps **Active**; HMG-API hooks on (`hmgapid engine map`).
3. Climate and seat buttons open ccNC Climate; no Gen5W volume strip; no side popups on track change; no "Media off"
   at boot; startup video; wireless CarPlay.
