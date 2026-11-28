package dev.ccnc.settings.api;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Handle on a row Page just added; lets a section grey it out (ccNC disabled state: _d art, 30% text). */
public final class Row {
    public final View view;
    final TextView title, sub;

    Row(View view, TextView title, TextView sub) { this.view = view; this.title = title; this.sub = sub; }

    public Row enabled(boolean on) {
        setEnabled(view, on);
        if (title != null) title.setTextColor(on ? Skin.fg() : Skin.fgOff());
        if (sub != null) sub.setTextColor(on ? Skin.sub() : Skin.subOff());
        return this;
    }

    private static void setEnabled(View v, boolean on) {
        v.setEnabled(on);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) setEnabled(((ViewGroup) v).getChildAt(i), on);
    }
}
