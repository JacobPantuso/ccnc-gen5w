package dev.ccnc.settings.api;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.ccnc.common.Chrome;
import dev.ccnc.common.Drive;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;
import dev.ccnc.common.Popup;

/** ccNC-styled questions for the Setup pages: a yes/no confirmation and a one-line text entry. */
public final class Ask {
    private Ask() {}

    public interface Text { void onText(String value); }

    /** Confirmation popup (ccNC Popup): runs yes only when the user picks the action label. Nothing happens otherwise. */
    public static void confirm(Activity a, String title, String message, String action, final Runnable yes) {
        try {
            new Popup(a, title, message).options(new String[] { action, "Cancel" }, -1, new Popup.Pick() {
                @Override public void onPick(int index) { if (index == 0) try { yes.run(); } catch (Throwable ignored) { } }
            }).show();
        } catch (Throwable t) { /* activity gone */ }
    }

    /**
     * One-line text entry in a ccNC panel (the unit's own keyboard types into it). done gets the trimmed text on OK.
     * The buttons sit on the driver's side (OK nearest the driver): mirrored for right-hand drive.
     */
    public static void text(final Activity a, String title, String initial, boolean password, int maxLen, final Text done) {
        try {
            final Dialog d = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            d.getWindow().setBackgroundDrawable(new ColorDrawable(0x99000000));
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            final float dp = a.getResources().getDisplayMetrics().density;
            FrameLayout root = new FrameLayout(a);
            LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFA17212C);
            bg.setCornerRadius(24 * dp);
            bg.setStroke(Math.max(1, (int) dp), Palette.BORDER);
            box.setBackgroundDrawable(bg);
            int pad = Math.round(28 * dp);
            box.setPadding(pad, Math.round(22 * dp), pad, Math.round(20 * dp));
            box.addView(label(a, title, 24, Palette.TEXT, Fonts.SEMIBOLD));

            final EditText e = new EditText(a);
            e.setSingleLine(true);
            e.setText(initial == null ? "" : initial);
            e.setSelection(e.getText().length());
            e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            e.setTextColor(Palette.TEXT);
            e.setTypeface(Fonts.get(a, Fonts.REGULAR));
            e.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            if (maxLen > 0) e.setFilters(new InputFilter[] { new InputFilter.LengthFilter(maxLen) });
            GradientDrawable eb = new GradientDrawable();
            eb.setColor(0x33000000);
            eb.setCornerRadius(14 * dp);
            eb.setStroke(Math.max(1, (int) dp), Palette.ACCENT_DIM);
            e.setBackgroundDrawable(eb);
            int ep = Math.round(14 * dp);
            e.setPadding(ep, ep, ep, ep);
            LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-1, -2);
            el.topMargin = Math.round(16 * dp);
            box.addView(e, el);

            LinearLayout row = new LinearLayout(a);
            row.setGravity(Drive.rhd(a) ? Gravity.RIGHT : Gravity.LEFT);
            TextView ok = button(a, "OK", true, dp), cancel = button(a, "Cancel", false, dp);
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2);
            if (Drive.rhd(a)) {
                gap.rightMargin = Math.round(12 * dp);
                row.addView(cancel, gap);
                row.addView(ok);
            } else {
                gap.rightMargin = Math.round(12 * dp);
                row.addView(ok, gap);
                row.addView(cancel);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = Math.round(18 * dp);
            box.addView(row, rl);
            ok.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                Chrome.beep(a);
                String s = e.getText().toString().trim();
                d.dismiss();
                try { done.onText(s); } catch (Throwable ignored) { }
            } });
            cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Chrome.beep(a); d.dismiss(); } });

            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Math.round(760 * dp), -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            bl.topMargin = Math.round(40 * dp);
            root.addView(box, bl);
            d.setContentView(root);
            d.show();
            e.requestFocus();
        } catch (Throwable t) { /* activity gone */ }
    }

    private static TextView label(Activity a, String s, float sp, int color, int face) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Fonts.get(a, face));
        return t;
    }

    private static TextView button(Activity a, String s, boolean primary, float dp) {
        TextView t = label(a, s, 20, Palette.TEXT, primary ? Fonts.SEMIBOLD : Fonts.REGULAR);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(16 * dp);
        g.setColor(primary ? Palette.alpha(Palette.ACCENT, 0x66) : 0x26FFFFFF);
        t.setBackgroundDrawable(g);
        t.setPadding(Math.round(28 * dp), Math.round(12 * dp), Math.round(28 * dp), Math.round(12 * dp));
        t.setSoundEffectsEnabled(false);
        return t;
    }
}
