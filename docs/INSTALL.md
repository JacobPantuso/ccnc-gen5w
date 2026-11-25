# Install guide

[← Back to README](../README.md)

## Contents

1. [Requirements](#requirements)
2. [Install from the NEMESIS Package Store](#install-from-the-nemesis-package-store)
3. [Install from a USB stick](#install-from-a-usb-stick)
4. [First setup](#first-setup)
5. [Updating](#updating)
6. [Updating from ccNC 1.x](#updating-from-ccnc-1x)
7. [Uninstalling](#uninstalling)

---

## Requirements

| | |
|---|---|
| Head unit | Hyundai or Kia **Gen5W** (1920 x 720 screen) |
| NEMESIS | **1.17.1 or newer** (LivePatch). Earlier versions can't install the package. |
| Space | About 100 MB free on `/data` |
| Car | Parked, engine or accessory power on, for the whole install |

> **NEMESIS 1.17.0 with HMG-API:** don't install that NEMESIS release. The package itself also refuses to
> install on it.

## Install from the NEMESIS Package Store

1. Open **NEMESIS Manager > Package Store**.
2. Search for **ccNC for Gen5W** and tap **Install**.
3. Wait for the queue to finish. NEMESIS counts down a restart: tap **Restart Now**.

## Install from a USB stick

1. Download **`ccnc.gen5w-<version>-nemesis.zip`** from the
   [latest release](https://github.com/JacobPantuso/ccnc-gen5w/releases/latest).
2. Unzip it onto the **root** of a FAT32 USB stick. You should see `NEMESIS.data` and a `packages` folder.
3. Plug the stick in, open **NEMESIS Manager > Package Store** and install **ccNC for Gen5W**.
4. Tap **Restart Now** when the queue finishes.

## First setup

1. Press the **Home** button. Android asks which home screen to use: choose **ccNC Home**, then **Always**.
2. Optional: pick a theme in **Settings > Screen Theme > ccNC Theme**, or get more from the **Theme Store**.

## Updating

Install the new version the same way. Your layout, theme and settings are kept. Restart when asked.

## Updating from ccNC 1.x

ccNC 1.x came as separate packages (ccNC Apps, ccNC Home, Climate Buttons, Side Popups Off, Volume, Startup
Video). The new package replaces all of them and won't install while any are present.

1. In **NEMESIS Manager**, uninstall every ccNC 1.x package.
2. **Restart the head unit** so the stock system files are back.
3. Install **ccNC for Gen5W** as above and restart again.

Your home-screen layout and theme are reset by this one-time move.

## Uninstalling

1. In **NEMESIS Manager**, uninstall **ccNC for Gen5W**.
2. Restart the head unit. The stock home screen, files and pop-ups are back.

Something not right? See [Troubleshooting](TROUBLESHOOTING.md).
