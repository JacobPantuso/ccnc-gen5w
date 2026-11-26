# Safety

[← Back to README](../README.md)

ccNC is a hobby project. It's built carefully and tested on a real car, but you are modifying your car's head
unit. Please read this page before installing.

## What ccNC changes

| Part | How | Undo |
|---|---|---|
| ccNC apps | Installed as normal apps, next to the stock apps | Removed on uninstall |
| System changes | NEMESIS LivePatch mounts patched copies over 4 system files at startup | Uninstall and restart: stock files are back |
| Settings | A notification listener for ccNC Home; tools in `/data/ccnc` for the developer menu | Removed on uninstall |

Nothing is written to `/system`. The stock files stay byte for byte as shipped.

## How it's kept safe

- **Firmware checks.** Each system change finds the code it changes by name. If it can't, it leaves that file
  stock instead of guessing.
- **Install guards.** The package refuses to install on a NEMESIS release known to be unsafe with HMG-API, while
  any ccNC 1.x package is still installed, or before Android has finished starting.
- **Boot safety net.** If Android fails to start several times in a row, NEMESIS starts the next boot fully
  stock (LivePatch safe mode). Inserting a USB stick with a `NEMESIS.emergency` file does the same.
- **HMG-API aware.** If HMG-API is installed (wireless CarPlay), ccNC keeps its file check happy so its features
  stay on.
- **Car controls are the car's own.** Climate, EV and vehicle settings use the same services as the stock apps.

## Known limits

- A head-unit software update (dealer or over the air) may remove NEMESIS and ccNC; reinstall afterwards.
- Modifying the head unit may affect your warranty.

## If something goes wrong

1. **The unit starts but something misbehaves:** uninstall ccNC for Gen5W in NEMESIS Manager and restart.
2. **Android doesn't finish starting:** turn the car off and on. After a few failed starts NEMESIS boots stock
   by itself; then uninstall the package.
3. **Still stuck:** insert a USB stick with an empty file named `NEMESIS.emergency` and restart.

See also [Troubleshooting](TROUBLESHOOTING.md).

**Don't use the screen while driving. Use at your own risk.**
