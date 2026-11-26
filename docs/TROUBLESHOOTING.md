# Troubleshooting

[← Back to README](../README.md)

| Problem | Fix |
|---|---|
| The package won't install: *depends on usb-handler* | Update NEMESIS to 1.17.1 or newer. |
| The package won't install: *conflicts with ccnc.…* | Uninstall all ccNC 1.x packages, restart, then try again ([Install guide](INSTALL.md#updating-from-ccnc-1x)). |
| *Android isn't up yet* | Wait for the home screen to appear, then install again. |
| The stock home screen still shows | Press **Home** and choose **ccNC Home > Always**. If you picked *Just once*, press **Home** again and choose **Always**. |
| Climate/seat buttons still open the stock screens | Restart the head unit after installing. Then check the [developer menu](DEVELOPER-MENU.md): *Climate buttons* should say **Active**. |
| A system change says *Not supported on this firmware* | Your firmware's code is different; that change was skipped safely. Everything else works. |
| A system change says *Installed, restart to apply* | Restart the head unit. If it stays, NEMESIS may be in safe mode: check NEMESIS Manager. |
| Wireless CarPlay stopped after installing | Restart once more. If it persists, choose **Export Logs** in the developer menu and open an issue with the logs. |
| Theme Store shows no themes | Connect the head unit to Wi-Fi with internet access, then open the store again. |
| A system change says *Patch tool error* | The patch tool failed on your firmware. Choose **Export Logs** and open an issue with the logs. |
| An update failed | Install the package again. Your apps from the previous version stay until the new one installs. |
| Climate won't change anything | Vehicle control may have been turned off: long-press the top-left corner of Climate (over the home button) and turn it back on. |

## Reporting a problem

1. Open the [developer menu](DEVELOPER-MENU.md) and choose **Export Logs** with a USB stick plugged in (the logs are saved there).
2. Open an [issue](https://github.com/JacobPantuso/ccnc-gen5w/issues) with your car, firmware (shown in the
   menu's header) and the folder from `ccNC logs` on the stick.
