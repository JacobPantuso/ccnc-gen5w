#!/bin/bash
# Copies the ccNC artwork the apps use out of the ccNC image dump into
# ccnc-common/assets/ccnc (bundled into each APK's assets by its build.sh):
#   ccnc/<brand>/icons/<name>.png   90x90 app icons (globalsearch set), hyundai + kia
#   ccnc/wallpaper/<name>.jpg       1920x720 home wallpapers (JPEG, ~10x smaller than the PNGs)
# Run again only when the dump or the list below changes; the output is kept in the tree.
set -euo pipefail
cd "$(dirname "$0")"
DUMP="${CCNC_DUMP:-../ccnc-home/apps/share/images-2/ccnc}"
OUT=assets/ccnc
[ -d "$DUMP" ] || { echo "no ccNC dump at $DUMP (set CCNC_DUMP)"; exit 1; }

ICONS="pp carplay phone voicememo weather setup navi media media_radio radio bt_audio
usb_music media_usbmusic climate ev bluelink userprofile media_soundofnature vehicle
set_vehicle set_bluetooth set_wifi set_display set_sound set_general set_themelayout
set_button set_bluelink set_userprofile set_deviceconnections set_voicerecognition
drive eco set_ev set_datanetwork set_cluster_hud set_wifihotspot set_builtincam set_alexa set_navi navi_search navi_station navi_poi navi_home navi_office current customization_frs manual voicerecognition deviceconnections notifications calendar
datanetwork general sound display button themelayout eco whatsnew"

rm -rf "$OUT" assets-da
for brand in hyundai kia; do
  src="$DUMP/$brand/normal/default/assembled/globalsearch/icon"
  mkdir -p "$OUT/$brand/icons"
  for n in $ICONS; do
    f="$src/global_ic_$n.png"
    if [ -f "$f" ]; then cp "$f" "$OUT/$brand/icons/$n.png"; else echo "  $brand: no global_ic_$n.png"; fi
  done
done

# Climate screen artwork (hyundai set; the cabin/seat drawings are brand-neutral).
C="$DUMP/hyundai/normal/default/assembled/climate"
mkdir -p "$OUT/climate"
for n in bg/clm_bg_front bg/clm_bg_driver_seat bg/clm_bg_passenger_seat bg/clm_bg_seat \
  bg/clm_bg_driver_heat_01 bg/clm_bg_driver_heat_02 bg/clm_bg_driver_heat_03 \
  bg/clm_bg_driver_vent_01 bg/clm_bg_driver_vent_02 bg/clm_bg_driver_vent_03 \
  bg/clm_bg_passenger_heat_01 bg/clm_bg_passenger_heat_02 bg/clm_bg_passenger_heat_03 \
  bg/clm_bg_passenger_vent_01 bg/clm_bg_passenger_vent_02 bg/clm_bg_passenger_vent_03 \
  icon/clm_ic_air_top_on icon/clm_ic_air_top_off icon/clm_ic_air_mid_on icon/clm_ic_air_mid_off \
  icon/clm_ic_air_bot_on icon/clm_ic_air_bot_off icon/clm_ic_defog_on icon/clm_ic_defog_off \
  icon/clm_ic_up_white_n icon/clm_ic_down_white_n icon/clm_ic_up_red_n icon/clm_ic_down_blue_n \
  icon/clm_ic_toggle_01_n icon/clm_ic_toggle_02_n icon/clm_ic_toggle_02_s icon/clm_ic_toggle_03_n icon/clm_ic_toggle_03_s \
  icon/clm_ic_sw_heat_n icon/clm_ic_pop_fan_up_n icon/clm_icon_set_temperature \
  bg/cli_bg_seat bg/clm_bg_pop_fan bg/clm_bg_wind_top bg/clm_bg_wind_mid bg/clm_bg_wind_bot bg/clm_bg_set_polygon \
  icon/clm_ic_divider_h icon/clm_ic_divider_w icon/clm_ic_pop_fan_02_up_n icon/clm_ic_pop_fan_02_down_n icon/clm_ic_pop_fan_02_off_n \
  icon/clm_ic_seat_on icon/clm_ic_seat_off; do
  f="$C/$n.png"
  if [ -f "$f" ]; then cp "$f" "$OUT/climate/$(basename "$n" | sed 's/^clm_\(bg\|ic\|icon\)_//; s/_n$//').png"; else echo "  no $n"; fi
done

# Weather: condition icons (296 px, day and night) and the small info icons.
W="$DUMP/hyundai/normal/default/assembled/weather/icon"
mkdir -p "$OUT/weather"
for f in "$W"/weather/day/01/we_ic_*_01_day.png; do
  n=$(basename "$f" _01_day.png); n=${n#we_ic_}
  cp "$f" "$OUT/weather/${n}_day.png"
  [ -f "$W/weather/night/01/we_ic_${n}_01_night.png" ] && cp "$W/weather/night/01/we_ic_${n}_01_night.png" "$OUT/weather/${n}_night.png"
done
for n in hum wind temp umb; do cp "$W/we_ic_$n.png" "$OUT/weather/info_$n.png"; done

# Profile avatars (first-run setup set).
F="$DUMP/hyundai/normal/default/assembled/customization_frs/icon"
mkdir -p "$OUT/profile"
cp "$F/frs_ic_thumbnail_default.png" "$OUT/profile/driver.png"
cp "$F/frs_ic_thumbnail_guest.png" "$OUT/profile/guest.png"

# System bar buttons (back / home).
mkdir -p "$OUT/chrome"
cp "$DUMP/hyundai/normal/default/assembled/scene_common/btn/co_btn_back_n.png" "$OUT/chrome/back.png"
cp "$DUMP/hyundai/normal/default/assembled/scene_common/btn/co_btn_home_n.png" "$OUT/chrome/home.png"

mkdir -p "$OUT/wallpaper"
H="$DUMP/hyundai/normal/default/assembled"
sips -s format jpeg -s formatOptions 85 "$H/home/bg/hom_bg_wallpaper.png" --out "$OUT/wallpaper/nebula.jpg" >/dev/null
sips -s format jpeg -s formatOptions 85 "$H/customization_frs/bg/frs_bg_wallpaper_01.png" --out "$OUT/wallpaper/starfield.jpg" >/dev/null
du -sh "$OUT"/*

# Driver-assistance graphics (sedan set, deduplicated): kept apart so only ccnc-settings bundles them.
DA=assets-da/ccnc/da
mkdir -p "$DA"
A="$DUMP/hyundai/normal/default/assembled"
( cd "$A" && find . -type f -name '*.png' \( -name 'usm_da_*' -o -name 'usm_da[0-9]*' -o -iname 'Lane_safety*' -o -name 'set_bg_adv_custombtn_img_driver_assistance.png' -o -name 'set_bg_sound_driver_assistance_warning.png' -o -name 'ccs_ic_adas.png' -o -name 'set_ic_menu_drive_assistance*.png' \) \
  | grep -v "@old\|@del\|/hev/\|splitscreen\|/camera/\|daylight\|middleeast" | grep -viE "_(bus|truck|pickup|suv|wagon|mpv|hatch)[._]" ) | while read -r f; do
  b=$(basename "$f")
  if [ ! -f "$DA/$b" ] || [ "$(stat -f%z "$A/$f")" -lt "$(stat -f%z "$DA/$b")" ]; then cp "$A/$f" "$DA/$b"; fi
done
echo "driver assistance: $(ls "$DA" | wc -l) files, $(du -sh "$DA" | cut -f1)"
