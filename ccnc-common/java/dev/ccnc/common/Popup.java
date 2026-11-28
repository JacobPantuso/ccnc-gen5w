package dev.ccnc.common;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


/** ccNC-style popup (like the developer menu): a title, then a list of options or a grid of pictures. Outside taps close it after 3 s. */
public final class Popup extends Dialog {
    public interface Pick { void onPick(int index); }

    private final Activity a;
    private final float dp;
    private final LinearLayout box;
    private final long shownAt = SystemClock.uptimeMillis();

    public Popup(Activity activity, String title, String subtitle) {
        super(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        a = activity;
        dp = a.getResources().getDisplayMetrics().density;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawable(new ColorDrawable(scrimColor()));
        FrameLayout root = new FrameLayout(a);
        root.setSoundEffectsEnabled(false);
        // An outside tap closes it once it has been open 3 s (fast taps behind it don't close it by accident).
        root.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            if (SystemClock.uptimeMillis() - shownAt >= 3000) dismiss();
        } });
        box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(panelColor());
        bg.setCornerRadius(24 * dp);
        bg.setStroke(Math.max(1, (int) dp), Palette.BORDER);
        box.setBackgroundDrawable(bg);
        box.setPadding(px(28), px(22), px(28), px(20));
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(title, 24, Palette.TEXT, Fonts.SEMIBOLD), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = text("✕", 24, Palette.TEXT, Fonts.REGULAR);
        close.setPadding(px(12), 0, px(4), 0);
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Chrome.beep(a); dismiss(); } });
        head.addView(close);
        box.addView(head);
        if (subtitle != null) {
            TextView s = text(subtitle, 15, Palette.SUBTEXT, Fonts.REGULAR);
            s.setPadding(0, px(6), 0, 0);
            box.addView(s);
        }
        root.addView(box, new FrameLayout.LayoutParams(px(760), -2, Gravity.CENTER));
        setContentView(root);
    }

    int px(float v) { return Math.round(v * dp); }

    /** Popup panel fill: near-black on dark themes, near-white on light ones (the text is Palette.TEXT). */
    public static int panelColor() { return Palette.LIGHT ? 0xFAF5F8FA : 0xFA17212C; }

    /** Dim behind a popup: lighter on light themes so the screen doesn't go black. */
    public static int scrimColor() { return Palette.LIGHT ? 0x66101820 : 0x99000000; }

    /** Secondary button / chip fill over the panel. */
    public static int softFill() { return Palette.LIGHT ? 0x1A16212A : 0x26FFFFFF; }

    TextView text(String s, float size, int color, int face) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Fonts.get(a, face));
        t.setSoundEffectsEnabled(false);
        return t;
    }

    /** A list of options; {@code selected} gets the accent highlight. */
    public Popup options(String[] labels, int selected, final Pick pick) {
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, px(14), 0, 0);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = text(labels[i], 20, Palette.TEXT, i == selected ? Fonts.SEMIBOLD : Fonts.REGULAR);
            t.setPadding(px(18), px(14), px(18), px(14));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(16 * dp);
            g.setColor(i == selected ? Palette.alpha(Palette.ACCENT, 0x40) : 0);
            t.setBackgroundDrawable(g);
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Chrome.beep(a); dismiss(); pick.onPick(idx); } });
            list.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        ScrollView sc = new ScrollView(a);
        sc.addView(list);
        box.addView(sc, new LinearLayout.LayoutParams(-1, Math.min(px(420), px(62) * labels.length + px(14))));
        return this;
    }

    /** A grid of round pictures, then optional text buttons below it (indexes after the pictures). */
    public Popup pictures(Bitmap[] pics, int selected, String[] buttons, final Pick pick) {
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(6);
        grid.setPadding(0, px(16), 0, px(6));
        for (int i = 0; i < pics.length; i++) {
            final int idx = i;
            ImageView v = new ImageView(a);
            v.setImageBitmap(pics[i]);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setPadding(px(6), px(6), px(6), px(6));
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0);
            g.setStroke(px(3), i == selected ? Palette.ACCENT : 0);
            v.setBackgroundDrawable(g);
            v.setSoundEffectsEnabled(false);
            v.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { Chrome.beep(a); dismiss(); pick.onPick(idx); } });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = px(104); lp.height = px(104);
            lp.setMargins(px(4), px(4), px(4), px(4));
            grid.addView(v, lp);
        }
        box.addView(grid);
        if (buttons != null) {
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.RIGHT);
            for (int i = 0; i < buttons.length; i++) {
                final int idx = pics.length + i;
                TextView b = text(buttons[i], 18, Palette.TEXT, Fonts.SEMIBOLD);
                b.setGravity(Gravity.CENTER);
                b.setPadding(px(22), px(12), px(22), px(12));
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(24 * dp);
                g.setColor(softFill());
                b.setBackgroundDrawable(g);
                b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Chrome.beep(a); dismiss(); pick.onPick(idx); } });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.leftMargin = px(12);
                row.addView(b, lp);
            }
            box.addView(row);
        }
        return this;
    }
}
