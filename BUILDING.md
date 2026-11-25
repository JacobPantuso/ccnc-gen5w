# Building ccNC

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

## Build

    cd ccnc-home && ./build.sh            # one app (APK in build/)
    ./ccnc-release.sh                     # every NEMESIS package + dist/ccnc-all-nemesis.zip (USB stick bundle)

`TARGET_SDK=30 ./build.sh` builds a copy for a modern Android emulator (the car runs Android 4.4, API 19).

## Layout

| Folder | What |
|---|---|
| `ccnc-common` | Shared code: top/bottom bars, themes and theme packs, fonts, developer menu |
| `ccnc-home` | The launcher, theme provider and Theme Store |
| `ccnc-settings`, `ccnc-climate`, `ccnc-ev`, `ccnc-media`, ... | The apps |
| `ccnc-apps` | The ccNC Apps NEMESIS package |
| `ccnc-volume`, `ccnc-sideosd`, `ccnc-climatebtn`, `ccnc-welcome` | The optional system packages (patch tools and install scripts; the stock files they patch aren't included) |
| `ccnc-themekit` | Theme-pack tools (also in [ccnc-themes](https://github.com/JacobPantuso/ccnc-themes)) |
