# Making a ccNC theme

A ccNC theme pack changes the colours, wallpaper and (optionally) icons of every ccNC app: Home, Settings,
Climate, Media, EV and the rest. It's installed from NEMESIS like any package and picked in
**Settings > Screen Theme > ccNC Theme**. A pack is files only; the install scripts are the same for every pack.

## 1. Start from the template

Copy `template/` to a new folder and edit `theme.json`:

| Field | What |
|---|---|
| `id` | short unique name, `a-z 0-9 _ -` (becomes the package id `ccnc.theme.<id>`) |
| `name`, `author`, `version`, `description` | shown in NEMESIS and in the theme list (`version` = dotted numbers) |
| `brands` | `["hyundai"]`, `["kia"]` or both. ccNC only lists the pack on matching cars |
| `light` | `true` for a light theme (dark text), else `false` |
| `colors.background` | 4 colours of the diagonal background gradient (drawn when there's no wallpaper, and behind it) |
| `colors.glow1`, `glow2` | soft glows over the gradient (use `#00000000` for none) |
| `colors.shade` | the darkening at the bottom of the screen, over the wallpaper |
| `colors.card` | card fill (keep it translucent, e.g. `#5C2A343D`) |
| `colors.subtext` | secondary text |
| `colors.accent` | highlight colour (selections, progress, toggles). Owners can still override it |
| `wallpaper` | `wallpaper.jpg`: **1920 x 720 JPEG**, at most 2 MB |
| `icons` | folder of **90 x 90 PNG** icons replacing ccNC's; names must be from `icon-names.txt`. Leave any out to keep ccNC's |
| `boot_video`, `shutdown_video` | optional `.mp4` (max 30 MB). Carried in the pack; applied by the ccNC Videos package (coming) |

Colours are `#rrggbb` or `#aarrggbb` (alpha first).

## 2. Build

    ./mktheme.sh path/to/your-theme

This checks everything (sizes, names, colours) and writes to `dist/`:

- `ccnc.theme.<id>-<version>-nemesis-package.zip`: submit this one to the NEMESIS Package Store
- `ccnc.theme.<id>-<version>-nemesis.zip`: unzip onto the root of a USB stick to install it directly

## 3. Install and test

Install it from NEMESIS (ccNC Home must already be installed), then choose it in
Settings > Screen Theme > ccNC Theme. Every ccNC app switches when it next comes to the front.
Uninstalling the package removes the theme. If it was in use, ccNC goes back to ccNC Nebula.

`examples/sample-ember` is a complete pack to copy from.

## 4. Publish to the online Theme Store

ccNC Home can install themes straight from the internet when the head unit is on Wi-Fi: Settings > Screen Theme >
Theme Store lists them (with previews), installs, updates and removes them. Nothing installs by itself; when the
unit joins Wi-Fi, Home only says when there are new themes or updates.

    store/publish.sh <tag>       e.g. store/publish.sh 2026.11.24

builds every pack in packs/, makes the signed index (`store/mkstore.py`: themes.json + themes.json.sig, previews and
the pack zips in store/out/) and uploads them as the latest release of JacobPantuso/ccnc-themes. Home only trusts an
index signed with keys/ccnc-themes-store.pem (keep that file private); each pack zip must match the index's size and
SHA-256, and only a pack's own files are unpacked. The pack zip is the same NEMESIS package used for USB installs.
