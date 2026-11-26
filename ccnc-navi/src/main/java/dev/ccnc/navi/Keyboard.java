package dev.ccnc.navi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * ccNC's on-screen keyboard for the navigation search. The unit's own IME (PinyinIME / DioInputMethodService) and the
 * map's built-in keyboard are both stock-styled, so the search draws its own: letters, digits, a symbol layer, space,
 * Delete (hold: clear) and Search. Keys are ccNC cards in design pixels; the board fills whatever space it gets.
 */
final class Keyboard extends Board {
    interface Listener {
        void onText(String s);
        void onDelete(boolean all);
        void onSearch();
    }

    private static final String[][] LETTERS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"},
            {"A", "S", "D", "F", "G", "H", "J", "K", "L", "'"},
            {"Z", "X", "C", "V", "B", "N", "M", ",", ".", "-"}};
    private static final String[][] SYMBOLS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"#", "&", "/", "(", ")", ":", ";", "@", "\"", "+"},
            {"°", "N", "S", "E", "W", "!", "?", "*", "_", "'"},
            {"$", "%", "=", "<", ">", "[", "]", ",", ".", "-"}};

    private final Listener listener;
    private boolean symbols;

    Keyboard(Context c, Listener l) {
        super(c);
        listener = l;
    }

    @Override protected void paint(Canvas c) {
        float w = W(), h = H(), gap = 10f;
        int rows = 5;
        float kh = (h - gap * (rows - 1)) / rows, kw = (w - gap * 9) / 10f;
        String[][] keys = symbols ? SYMBOLS : LETTERS;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 10; col++) {
                final String s = keys[row][col];
                float l = col * (kw + gap), t = row * (kh + gap);
                key(c, l, t, kw, kh, s, 34, Palette.CARD, new Runnable() { @Override public void run() { listener.onText(s); } }, null);
            }
        }
        // Bottom row: symbols | space | delete | search, as on any keyboard (not mirrored for right-hand drive).
        float t = 4 * (kh + gap);
        float symW = kw * 1.5f + gap * 0.5f, delW = kw * 1.5f + gap * 0.5f, goW = kw * 2.5f + gap * 1.5f;
        float spaceW = w - symW - delW - goW - gap * 3;
        float x = 0;
        key(c, x, t, symW, kh, symbols ? "ABC" : "#+=", 28, Palette.CARD_HIGHLIGHT,
                new Runnable() { @Override public void run() { symbols = !symbols; invalidate(); } }, null);
        x += symW + gap;
        key(c, x, t, spaceW, kh, "Space", 28, Palette.CARD, new Runnable() { @Override public void run() { listener.onText(" "); } }, null);
        x += spaceW + gap;
        key(c, x, t, delW, kh, "Delete", 28, Palette.CARD_HIGHLIGHT,
                new Runnable() { @Override public void run() { listener.onDelete(false); } },
                new Runnable() { @Override public void run() { listener.onDelete(true); } });
        x += delW + gap;
        int i = box(x, t, goW, kh, new Runnable() { @Override public void run() { listener.onSearch(); } }, null);
        card(c, x, t, goW, kh, 16, isDown(i), Palette.alpha(Palette.ACCENT, 0xCC));
        txtMid(c, "Search", x + goW / 2f, t + kh / 2f, 30, Palette.TEXT, Fonts.SEMIBOLD, Paint.Align.CENTER, goW - 16);
    }

    private void key(Canvas c, float l, float t, float w, float h, String label, float size, int color, Runnable tap, Runnable hold) {
        int i = box(l, t, w, h, tap, hold);
        card(c, l, t, w, h, 16, isDown(i), color);
        txtMid(c, label, l + w / 2f, t + h / 2f, size, Palette.TEXT, Fonts.SEMIBOLD, Paint.Align.CENTER, w - 8);
    }
}
