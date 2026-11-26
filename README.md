<div align="center">

# ccNC for Gen5W

**Hyundai and Kia's newest ccNC infotainment look, on Gen5W head units.**

[![Buy me a coffee](https://img.shields.io/badge/Buy%20me%20a%20coffee-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/jacobpantuso)

![Platform](https://img.shields.io/badge/head%20unit-Gen5W-1f6feb)
![Android](https://img.shields.io/badge/Android-4.4-3ddc84)
![Installer](https://img.shields.io/badge/installs%20with-NEMESIS-8957e5)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

![ccNC Home on a Gen5W head unit](docs/screenshots/home.png)

[Install](docs/INSTALL.md) · [Features](docs/FEATURES.md) · [Safety](docs/SAFETY.md) · [Themes](https://github.com/JacobPantuso/ccnc-themes) · [Developer menu](docs/DEVELOPER-MENU.md) · [Building](BUILDING.md)

</div>

---

> ☕ **Enjoying ccNC-Gen5W?** It's free and took a *lot* of late nights to build, and a fair amount of paid AI usage
> along the way. If you enjoy the experience, consider [donating](https://buymeacoffee.com/jacobpantuso). It helps keep updates and new features coming.

## Overview

ccNC for Gen5W replaces the Gen5W home screen and everyday apps with ccNC-style versions. It installs as
**one NEMESIS package**: a new launcher, Climate, EV, Media, Settings and seven more apps, plus a few small
system changes made with NEMESIS LivePatch so the car's own buttons and pop-ups fit the new look.

- **Nothing in `/system` is rewritten.** LivePatch mounts patched copies at startup; uninstalling brings the
  stock files back.
- **The stock apps stay installed.** Everything the car does, it still does.
- **One-tap removal.** Uninstall from NEMESIS Manager and restart.

> Built and tested on a 2023+ Hyundai Ioniq 6 (firmware `CEEV.CAN.S5W_M.V014.010.250818`). Hyundai and Kia
> are both supported. This project is not affiliated with Hyundai or Kia.

## At a glance

<table>
<tr>
<td width="50%"><img src="docs/screenshots/welcome.png"></td>
<td width="50%"><img src="docs/screenshots/climate.png"></td>
</tr>
<tr>
<td><img src="docs/screenshots/ev.png"></td>
<td><img src="docs/screenshots/media.png"></td>
</tr>
<tr>
<td><img src="docs/screenshots/settings.png"></td>
<td><img src="docs/screenshots/theme-store.png"></td>
</tr>
</table>

See every app in **[Features](docs/FEATURES.md)**.

## Quick install

1. Make sure your head unit has **NEMESIS with LivePatch** (1.17.1 or newer).
2. In **NEMESIS Manager > Package Store**, install **ccNC for Gen5W**, or install it from a USB stick with
   the zip from the [releases page](https://github.com/JacobPantuso/ccnc-gen5w/releases).
3. Restart the head unit, press **Home** and choose **ccNC Home > Always**.

Full steps, updating from 1.x and uninstalling: **[Install guide](docs/INSTALL.md)**.

## Is it safe?

ccNC is a hobby project, built carefully and tested on a real car, but it modifies your head unit. Read
**[Safety](docs/SAFETY.md)** before installing. In short:

| | |
|---|---|
| Apps | Added alongside the stock apps; removed on uninstall (Climate and Home after the restart). |
| System changes | Made by LivePatch, never written to `/system`; stock again after uninstall and a restart. |
| Unsupported firmware | Each change checks it fits your firmware and leaves the file stock if it doesn't. |
| A failed install | The apps go in first; if one fails, nothing is changed. |
| If the unit doesn't start | NEMESIS starts stock automatically after failed boots (safe mode). |

Install while parked. Don't use the screen while driving. Use at your own risk.

## Documentation

| Guide | What's in it |
|---|---|
| [Install](docs/INSTALL.md) | Requirements, installing, first setup, updating from 1.x, uninstalling |
| [Features](docs/FEATURES.md) | Every app and system change, with screenshots |
| [Safety](docs/SAFETY.md) | What ccNC changes, how it's kept reversible, recovery |
| [Troubleshooting](docs/TROUBLESHOOTING.md) | Common problems and fixes |
| [Developer menu](docs/DEVELOPER-MENU.md) | Status, CPU, compatibility and on-car checks |
| [Building](BUILDING.md) | Building the apps and the package from source |
| [ccnc-themes](https://github.com/JacobPantuso/ccnc-themes) | The theme catalog and how to make your own |

## License

Code: [MIT](LICENSE). ccNC, Hyundai, Kia and their artwork, fonts and logos belong to their owners; they are
not in this repository (the release packages include what the apps need).
