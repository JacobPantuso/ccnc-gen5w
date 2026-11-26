# Building ccNC

[← Back to README](README.md)

Everything is built with plain shell scripts, no Gradle.

## You need

- macOS or Linux with `bash`, `python3`, `zip`, a JDK (8+)
- Android SDK: build-tools (the newest installed is used: `aapt`, `aapt2`, `d8`, `apksigner`, `zipalign`) and a
  `platforms/android-*/android.jar`
- **ccNC fonts and artwork.** They aren't in this repository. They come from a ccNC firmware image dump:
  - `ccnc-common/build-assets.sh` copies the icons and wallpapers into `ccnc-common/assets/ccnc`
    (set `CCNC_DUMP` to the dump's `share/images-2/ccnc` folder).
  - `ccnc-common/build-fonts.sh` subsets the ccNC fonts into `ccnc-common/fonts/<brand>`.
  - Each app's own artwork (e.g. `ccnc-climate/assets/ccnc/climate`, `ccnc-ev/src/main/assets/ccnc/ev`) comes
    from the same dump, under `assembled/<app>/`.
  - The startup videos `ccnc-welcome/video/welcome_hyundai_avnt.mp4` and `welcome_kia_avnt.mp4` come from the
    same dump.
- **For a release: the release signing key** at `keys/ccnc-apps.keystore` (not in this repository). Every ccNC
  release is signed with it, so updates install over the apps already on a car. A build signed with another key
  can't update those apps (Android refuses with `INCONSISTENT_CERTIFICATES`), and the package's install then stops
  without changing anything.

## Build

| Command | Output |
|---|---|
| `cd ccnc-home && ./build.sh` | One app, in `build/` (car build, Android 4.4 / API 19). `KEYSTORE=…` signs with another key (default: the SDK debug key) |
| `TARGET_SDK=30 DEBUGGABLE=1 ./build.sh` | A copy for a modern Android emulator (1920 x 720 AVD). Never ship this one |
| `V=<version> livepatch/gen5w/mkgen5w.sh` | The release package. Always rebuilds every app as a car build signed with the release key, rebuilds both on-unit tools, runs the patch tool's offline tests, and checks every APK (targetSdk 19, not debuggable, release key), the descriptor and the scripts. Output in `livepatch/gen5w/dist/`: `ccnc.gen5w-<version>-nemesis-package.zip` (Package Store), `-nemesis.zip` (USB stick), `ccnc.gen5w-<version>.sha256` |
| `livepatch/tool/offline-test.sh` | The patch tool's tests. The file tests need the stock odex files of a CEEV.CAN 250818 unit (`CCNC_STOCK`), which aren't in this repository; without them only the wrapper tests run |
| `./ccnc-release.sh` | The legacy 1.x packages (separate apps and system packages). Needs the sample theme's artwork, not in this repository |

## The release package

`livepatch/gen5w/template` is the NEMESIS package: `NEMESIS.data.in` (with `[patch.N]` LivePatch steps),
install scripts in `scripts/` and the LivePatch tools in `bin/`. Each tool follows the NEMESIS `exec` contract
(`<tool> <args> <in> <out>`; exit 0 = written, 10 = leave the file as it is, anything else = stop the install):
a change that doesn't fit the firmware exits 10, so the apps still install; a tool error stops the install
before anything is committed. The apps are installed by `scripts/pre.sh`, before NEMESIS commits the patches.
See [`livepatch/gen5w/README.md`](livepatch/gen5w/README.md).

## Layout

| Folder | What |
|---|---|
| `ccnc-common` | Shared code: top/bottom bars, themes and theme packs, fonts, developer menu |
| `ccnc-home` | The launcher, theme provider and Theme Store |
| `ccnc-settings`, `ccnc-climate`, `ccnc-ev`, `ccnc-media`, ... | The apps |
| `livepatch/gen5w` | The ccNC for Gen5W release package (apps + LivePatch steps) |
| `livepatch/tool` | The LivePatch dex patcher (volume, side pop-ups, climate buttons) |
| `ccnc-welcome` | The startup-video patcher and videos |
| `ccnc-apps`, `ccnc-volume`, `ccnc-sideosd`, `ccnc-climatebtn` | Legacy 1.x packages (kept for reference) |
| `ccnc-themekit` | Theme-pack tools (also in [ccnc-themes](https://github.com/JacobPantuso/ccnc-themes)) |
