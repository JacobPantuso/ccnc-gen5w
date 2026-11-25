package dev.ccnc.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Full-bleed ccNC background: dark blue-grey diagonal gradient with two soft glows. */
public final class Bg extends View {

    private final Paint base = new Paint();
    private final Paint glow1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint();
    private int drawnW = -1, drawnH = -1, drawnVersion = -1;
    private android.graphics.Bitmap wall;
    private final android.graphics.RectF dst = new android.graphics.RectF();
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);

    public Bg(Context c) { super(c); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { drawnW = -1; }

    private void shaders(int w, int h) {
        base.setShader(new LinearGradient(0, 0, w, h, Palette.BG,
                new float[]{0f, 0.35f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        glow1.setShader(new RadialGradient(w * 0.4f, -h * 0.1f, Math.max(w, h) * 0.5f,
                Palette.GLOW1, Palette.GLOW1 & 0x00FFFFFF, Shader.TileMode.CLAMP));
        glow2.setShader(new RadialGradient(w * 0.8f, h * 0.3f, Math.max(w, h) * 0.32f,
                Palette.GLOW2, Palette.GLOW2 & 0x00FFFFFF, Shader.TileMode.CLAMP));
        shade.setShader(new LinearGradient(0, h * 0.55f, 0, h,
                Palette.SHADE & 0x00FFFFFF, Palette.SHADE, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w != drawnW || h != drawnH || drawnVersion != Palette.VERSION) {
            shaders(w, h);
            drawnW = w; drawnH = h; drawnVersion = Palette.VERSION;
            wall = Palette.WALLPAPER == null ? null : Art.wallpaper(getContext(), Palette.WALLPAPER);
        }
        if (wall != null) { // ccNC wallpaper, centre-cropped, then the bottom shade
            float k = Math.max(w / (float) wall.getWidth(), h / (float) wall.getHeight());
            float bw = wall.getWidth() * k, bh = wall.getHeight() * k;
            dst.set((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f);
            c.drawBitmap(wall, null, dst, bmp);
            c.drawRect(0, 0, w, h, shade);
            return;
        }
        c.drawRect(0, 0, w, h, base);
        c.drawRect(0, 0, w, h, glow1);
        c.drawRect(0, 0, w, h, glow2);
        c.drawRect(0, 0, w, h, shade);
    }
}
