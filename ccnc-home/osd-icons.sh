#!/bin/bash
# Makes ccNC Home's climate notice icons (src/main/res/raw, git-ignored: Hyundai's ccNC artwork) from the ccNC image
# dump, like the volume icons next to them. Crops the official OSD art to square icons:
#   osd_ic_clm_washer.png   windscreen spray   (osd_ic_ss_washer_fluid.png, left half)
#   osd_ic_clm_recirc.png   recirculation car  (osd_ic_ss_washer_fluid.png, right half)
#   osd_ic_clm_air.png      air cleaner tree   (osd_ic_ss_aircleaner_top_mono.png)
# and the icons of the other notices that popup shows (USB debugging, seat memory):
#   osd_ic_ntc_info.png     "i" in a circle    (osd_ic_info01.png)
#   osd_ic_ntc_warning.png  warning triangle   (osd_ic_warning_white.png)
#   osd_ic_ntc_seat.png     seat               (setup(vehicle)/icon/set_ic_menu_seat.png; drawn white by ClimateNotice)
set -euo pipefail
cd "$(dirname "$0")"
ROOT="${CCNC_DUMP:-apps/share/images-2/ccnc}/hyundai/normal/default/assembled"
DUMP="$ROOT/osd/icon"
OUT=src/main/res/raw
[ -d "$DUMP" ] || { echo "no ccNC dump at $DUMP (set CCNC_DUMP)"; exit 1; }
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
cat > "$T/Crop.java" <<'JAVA'
import javax.imageio.ImageIO; import java.awt.image.BufferedImage; import java.io.File;
public class Crop { // in out x0 x1 pad: visible pixels in columns [x0,x1), centred in a padded square
  public static void main(String[] a) throws Exception {
    BufferedImage im = ImageIO.read(new File(a[0]));
    int x0 = Integer.parseInt(a[2]), x1 = Math.min(Integer.parseInt(a[3]), im.getWidth()), pad = Integer.parseInt(a[4]);
    int minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE, maxx = -1, maxy = -1;
    for (int y = 0; y < im.getHeight(); y++) for (int x = x0; x < x1; x++) if (((im.getRGB(x, y) >>> 24) & 0xff) > 16) {
      minx = Math.min(minx, x); maxx = Math.max(maxx, x); miny = Math.min(miny, y); maxy = Math.max(maxy, y); }
    int w = maxx - minx + 1, h = maxy - miny + 1, s = Math.max(w, h) + 2 * pad;
    BufferedImage o = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) o.setRGB((s - w) / 2 + x, (s - h) / 2 + y, im.getRGB(minx + x, miny + y));
    ImageIO.write(o, "png", new File(a[1]));
  }
}
JAVA
javac -d "$T" "$T/Crop.java"
java -cp "$T" Crop "$DUMP/osd_ic_ss_washer_fluid.png" "$OUT/osd_ic_clm_washer.png" 0 180 4
java -cp "$T" Crop "$DUMP/osd_ic_ss_washer_fluid.png" "$OUT/osd_ic_clm_recirc.png" 215 396 4
java -cp "$T" Crop "$DUMP/osd_ic_ss_aircleaner_top_mono.png" "$OUT/osd_ic_clm_air.png" 0 290 10
java -cp "$T" Crop "$DUMP/osd_ic_info01.png" "$OUT/osd_ic_ntc_info.png" 0 64 10
java -cp "$T" Crop "$DUMP/osd_ic_warning_white.png" "$OUT/osd_ic_ntc_warning.png" 0 64 11
java -cp "$T" Crop "$ROOT/setup(vehicle)/icon/set_ic_menu_seat.png" "$OUT/osd_ic_ntc_seat.png" 0 80 6
echo "notice icons: $OUT/osd_ic_clm_{washer,recirc,air}.png $OUT/osd_ic_ntc_{info,warning,seat}.png"
