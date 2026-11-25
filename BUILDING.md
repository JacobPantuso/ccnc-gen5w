# Building ccNC

[← Back to README](README.md)

Everything is built with plain shell scripts, no Gradle.

## You need

- macOS or Linux with `bash`, `python3`, `zip`, a JDK (8+)
- Android SDK: build-tools 36 (`aapt2`, `d8`, `apksigner`) and `platforms/android-36/android.jar`
- **ccNC fonts and artwork.** They aren't in this repository. They come from a ccNC firmware image dump:
  - `ccnc-common/build-assets.sh` copies the icons and wallpapers into `ccnc-common/assets/ccnc`
    (set `CCNC_DUMP` to the dump's `share/images-2/ccnc` folder).
  - `ccnc-common/build-fonts.sh` subsets the ccNC fonts into `ccnc-common/fonts/<brand>`.
  - Each app's own artwork (e.g. `ccnc-climate/assets/ccnc/climate`, `ccnc-ev/src/main/assets/ccnc/ev`) comes
    from the same dump, under `assembled/<app>/`.
  - The startup videos `ccnc-welcome/video/welcome_hyundai_avnt.mp4` and `welcome_kia_avnt.mp4` come from the
    same dump.

## Build

| Command | Output |
|---|---|
| `cd ccnc-home && ./build.sh` | One app, in `build/` (car build, Android 4.4 / API 19) |
| `TARGET_SDK=30 ./build.sh` | A copy for a modern Android emulator (1920 x 720 AVD) |
| `livepatch/gen5w/mkgen5w.sh` | The release package: `livepatch/gen5w/dist/ccnc.gen5w-<version>-nemesis-package.zip` (Package Store) and `-nemesis.zip` (USB stick) |
| `REBUILD=1 livepatch/gen5w/mkgen5w.sh` | The same, rebuilding every app first |
| `./ccnc-release.sh` | The legacy 1.x packages (separate apps and system packages) |

`mkgen5w.sh` also builds the two on-unit tools if needed: `livepatch/tool` (the LivePatch dex patcher) and
`ccnc-welcome/build-patch.sh` (the startup-video patcher).

## The release package

`livepatch/gen5w/template` is the NEMESIS package: `NEMESIS.data.in` (with `[patch.N]` LivePatch steps),
install scripts in `scripts/` and the LivePatch tools in `bin/`. Each tool follows the NEMESIS `exec` contract
(`<tool> <args> <in> <out>`; exit 0 = written, 10 = leave the file as it is) and exits 10 when a change doesn't
fit the firmware, so the apps still install. See [`livepatch/gen5w/README.md`](livepatch/gen5w/README.md).

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
