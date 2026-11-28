package dev.ccnc.phone;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * ccNC Bluetooth Phone (phonehmi): Call History / Contacts / Dial tabs. Coordinates are ccNC's own
 * (1920x720 px): 508 px content area, 976x90 bottom tab bar at x=472, 44/1364/40/472 call-list columns,
 * 44/584/40/740/512 dial columns. Layout from the recovered QML (CallHistory_001_002, Contacts_001_002, Dial_001_002_003_004).
 */
public final class MainActivity extends Activity {

    private static final int TOP = 122, CONTENT_H = 508, BAR_X = 472, BAR_Y = 630, BAR_W = 976, BAR_H = 90;
    private static final String[] CHOSUNG = {"", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "", "+", ""};
    private static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#", "setting", "del", "call"};
    private static final int MAX_INPUT = 32;
    private static final String BT_CONTACTS = "content://com.android.contacts_bt/data/phones";

    static final class Row { String name = "", number = "", right = ""; String icon; }

    private Dialer dialer;
    private FrameLayout root, pane;
    private TabBar tabBar;
    private int tab = -1;
    private String deviceName = "";
    private final StringBuilder digits = new StringBuilder();
    private final Handler h = new Handler();
    private List<Row> contacts = new ArrayList<Row>(), recents = new ArrayList<Row>();
    private DialPane dial;
    private final List<Runnable> onData = new ArrayList<Runnable>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this);
        dialer = new Dialer(this);
        root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.addView(dev.ccnc.common.Chrome.topBar(this, "Phone"));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2));
        pane = new FrameLayout(this);
        place(pane, 0, TOP, 1920, CONTENT_H);
        tabBar = new TabBar(this);
        place(tabBar, BAR_X, BAR_Y, BAR_W, BAR_H);
        setContentView(root);
        showTab(0);
        loadData();
    }

    private void place(View v, int x, int y, int w, int hh) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, hh, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = x; lp.topMargin = y;
        root.addView(v, lp);
    }

    @Override protected void onResume() {
        super.onResume();
        dialer.start();
        h.postDelayed(new Runnable() { @Override public void run() { refreshDevice(); } }, 1200);
        loadData();
    }

    @Override protected void onPause() { dialer.stop(); super.onPause(); }

    private void refreshDevice() {
        deviceName = dialer.deviceName();
        showTab(-2);
    }

    // ---- tabs ----

    private void showTab(int t) {
        if (t >= 0) { if (t == tab) return; tab = t; } else if (tab < 0) return;
        tabBar.invalidate();
        pane.removeAllViews();
        if (tab == 0) pane.addView(callHistory(), new FrameLayout.LayoutParams(-1, -1));
        else if (tab == 1) pane.addView(contactsPane(), new FrameLayout.LayoutParams(-1, -1));
        else { dial = new DialPane(this); pane.addView(dial, new FrameLayout.LayoutParams(-1, -1)); }
    }

    private final class TabBar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        TabBar(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            int n = 3, w = getWidth() / n;
            for (int i = 0; i < n; i++) {
                boolean sel = i == tab;
                if (sel) Pic.get(getContext(), "bt_btn_bg_bottom_se.9.png").draw(c, i * w, 0, (i + 1) * w, getHeight());
                String icn = "bt_ic_tabbar_bottom_0" + (i + 1) + (sel ? "_s.png" : "_n.png");
                Pic.Pic9 ic = sel ? Pic.get(getContext(), icn) : Pic.ink(getContext(), icn); // selected: on the accent tab
                c.drawBitmap(ic.bm, i * w + (w - ic.w()) / 2f, (getHeight() - ic.h()) / 2f, p);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int i = (int) (e.getX() / (getWidth() / 3f));
                if (i >= 0 && i < 3) showTab(i);
            }
            return true;
        }
    }

    // ---- data ----

    private void loadData() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Row> r = loadRecents();
                final List<Row> c = loadContacts();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        recents = r; contacts = c;
                        showTab(-2);
                    }
                });
            }
        }).start();
    }

    private List<Row> loadRecents() {
        List<Row> out = new ArrayList<Row>();
        Cursor c = null;
        try {
            c = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DURATION},
                    null, null, CallLog.Calls.DATE + " DESC");
            while (c != null && c.moveToNext() && out.size() < 200) {
                Row r = new Row();
                r.number = nz(c.getString(0));
                r.name = nz(c.getString(1));
                if (r.name.isEmpty()) r.name = r.number;
                int type = c.getInt(2);
                r.icon = type == CallLog.Calls.OUTGOING_TYPE ? "bt_ic_list_01_send.png"
                        : type == CallLog.Calls.INCOMING_TYPE ? "bt_ic_list_01_rcv.png" : "bt_ic_list_01_missed.png";
                r.right = type == CallLog.Calls.MISSED_TYPE ? "" : fmtDur(c.getLong(3));
                r.name = (type == CallLog.Calls.OUTGOING_TYPE ? "\u0001" : "") + r.name; // marker, stripped below
                out.add(r);
            }
        } catch (Throwable t) {
            android.util.Log.w(Dialer.TAG, "call log unavailable", t);
        } finally { if (c != null) c.close(); }
        android.util.Log.i(Dialer.TAG, "call log rows: " + out.size());
        return out;
    }

    private List<Row> loadContacts() {
        List<Row> out = loadPhones(BT_CONTACTS);
        android.util.Log.i(Dialer.TAG, "bt contacts rows: " + out.size());
        if (out.isEmpty()) {
            out = loadPhones(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI.toString());
            android.util.Log.i(Dialer.TAG, "phone contacts rows: " + out.size());
        }
        return out;
    }

    private List<Row> loadPhones(String uri) {
        List<Row> out = new ArrayList<Row>();
        Cursor c = null;
        try {
            c = getContentResolver().query(Uri.parse(uri), new String[]{"display_name", "data1"},
                    null, null, "display_name COLLATE NOCASE ASC");
            while (c != null && c.moveToNext() && out.size() < 3000) {
                Row r = new Row();
                r.name = nz(c.getString(0));
                r.number = nz(c.getString(1));
                out.add(r);
            }
        } catch (Throwable t) {
            android.util.Log.w(Dialer.TAG, "contacts unavailable at " + uri + ": " + t);
        } finally { if (c != null) c.close(); }
        return out;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String fmtDur(long s) {
        if (s >= 3600) return String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private void call(String n) {
        String err = n.isEmpty() ? "" : dialer.dial(n);
        if (err != null && !err.isEmpty()) Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
    }

    // ---- shared pieces ----

    private Paint tp(float px, boolean semi, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(px);
        p.setColor(color);
        p.setTypeface(Fonts.get(this, semi ? Fonts.SEMIBOLD : Fonts.REGULAR));
        return p;
    }

    private static void text(Canvas c, String s, Paint p, float x, float yCenter, float maxW) {
        if (s == null || s.isEmpty()) return;
        String t = maxW > 0 ? TextUtils.ellipsize(s, new android.text.TextPaint(p), maxW, TextUtils.TruncateAt.END).toString() : s;
        Paint.FontMetrics m = p.getFontMetrics();
        c.drawText(t, x, yCenter - (m.ascent + m.descent) / 2f, p);
    }

    /** Default info area: 320 logo then the connected device's name (40 px). */
    private void drawInfo(Canvas c, int x) {
        Pic.Pic9 logo = Pic.get(this, "bt_etc_logo_l.png");
        c.drawBitmap(logo.bm, x + (452 - logo.w()) / 2f, 31 + (320 - logo.h()) / 2f, null);
        Paint p = tp(40, false, Palette.TEXT);
        p.setTextAlign(Paint.Align.CENTER);
        String n = TextUtils.ellipsize(deviceName, new android.text.TextPaint(p), 392, TextUtils.TruncateAt.END).toString();
        Paint.FontMetrics m = p.getFontMetrics();
        c.drawText(n, x + 226, 383 + 26 - (m.ascent + m.descent) / 2f, p);
    }

    private final class ListBox extends ListView {
        ListBox(Context c) {
            super(c);
            setDivider(null);
            setVerticalScrollBarEnabled(false);
            setSelector(android.R.color.transparent);
            setCacheColorHint(0);
        }
    }

    /** One list row: ccNC tabbar_list background, 50 px type icon, 32 semibold name, 28 right text. */
    private final class RowView extends View {
        Row r; int style; // 0 call history (124), 1 two-line (name + number)
        private final Paint pn = tp(32, true, Palette.TEXT), pr = tp(28, false, Palette.SUBTEXT), ps = tp(28, false, Palette.SUBTEXT);
        RowView(Context c, int style) { super(c); this.style = style; setClickable(true); }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), hh = getHeight();
            Pic.ink(getContext(), isPressed() ? "co_bg_tabbar_list_p.9.png" : "co_bg_tabbar_list_n.9.png").draw(c, 0, 0, w, hh);
            if (r == null) return;
            if (style == 0) {
                if (r.icon != null) { Pic.Pic9 ic = Pic.ink(getContext(), r.icon); c.drawBitmap(ic.bm, 32, (hh - ic.h()) / 2f, null); }
                text(c, r.name.replace("\u0001", ""), pn, 112, hh / 2f, 868);
                text(c, r.right, pr, 1148, hh / 2f, 156);
            } else {
                text(c, r.name, pn, 32, hh * 0.33f, w - 160);
                text(c, r.number, ps, 32, hh * 0.74f, w - 160);
            }
        }
    }

    private final class RowAdapter extends BaseAdapter {
        final List<Row> rows; final int style, rowH;
        RowAdapter(List<Row> rows, int style, int rowH) { this.rows = rows; this.style = style; this.rowH = rowH; }
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public View getView(int i, View v, ViewGroup p) {
            RowView rv = v instanceof RowView ? (RowView) v : new RowView(MainActivity.this, style);
            rv.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, rowH));
            final Row r = rows.get(i);
            rv.r = r;
            rv.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { call(r.number); } });
            rv.invalidate();
            return rv;
        }
    }

    // ---- call history ----

    private View callHistory() {
        FrameLayout f = new FrameLayout(this) {
            @Override protected void onDraw(Canvas c) { drawInfo(c, 1448); }
        };
        f.setWillNotDraw(false);
        ListBox l = new ListBox(this);
        l.setAdapter(new RowAdapter(recents, 0, 124));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1364, CONTENT_H);
        lp.leftMargin = 44;
        f.addView(l, lp);
        return f;
    }

    // ---- contacts ----

    private View contactsPane() {
        FrameLayout f = new FrameLayout(this) {
            @Override protected void onDraw(Canvas c) { drawInfo(c, 1448); }
        };
        f.setWillNotDraw(false);
        ListBox l = new ListBox(this);
        l.setAdapter(new RowAdapter(contacts, 1, 90));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1364, 448);
        lp.leftMargin = 44; lp.topMargin = 60;
        f.addView(l, lp);
        View cnt = new View(this) {
            final Paint p = tp(32, true, Palette.TEXT);
            @Override protected void onDraw(Canvas c) { text(c, String.valueOf(contacts.size()), p, 0, 30, 0); }
        };
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(400, 60);
        cl.leftMargin = 44;
        f.addView(cnt, cl);
        return f;
    }

    // ---- dial ----

    private final class DialPane extends FrameLayout {
        private final Paint pg = tp(38, false, Palette.SUBTEXT), pi = tp(38, false, Palette.LIGHT ? Palette.TEXT : 0xFFD1D1D1), ph = tp(28, false, Palette.SUBTEXT);
        private final ListBox list;
        private int pressed = -1;

        DialPane(Context c) {
            super(c);
            setWillNotDraw(false);
            list = new ListBox(c);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(740, 248);
            lp.leftMargin = 668; lp.topMargin = 79 + 70;
            addView(list, lp);
            refreshList();
        }

        void refreshList() {
            List<Row> rows = new ArrayList<Row>();
            LayoutParams lp = (LayoutParams) list.getLayoutParams();
            if (digits.length() >= 2) {
                String d = digits.toString();
                for (Row r : contacts) {
                    if (r.number.replace("-", "").replace(" ", "").contains(d) && rows.size() < 200) rows.add(r);
                }
                lp.topMargin = 79; lp.height = 429;
            } else {
                java.util.HashSet<String> seen = new java.util.HashSet<String>();
                for (Row r : recents) {
                    if (r.name.startsWith("\u0001") && seen.add(r.number) && rows.size() < 20) {
                        Row q = new Row(); q.name = r.name.substring(1); q.number = r.number.equals(q.name) ? "" : r.number; rows.add(q);
                    }
                }
                lp.topMargin = 79 + 70; lp.height = 248;
            }
            list.setLayoutParams(lp);
            list.setAdapter(new RowAdapter(rows, 1, 84));
            invalidate();
        }

        private void cell(int i, int[] o) { o[0] = 44 + (i % 3) * 194; o[1] = (i / 3) * 101; }

        @Override protected void onDraw(Canvas c) {
            int[] o = new int[2];
            for (int i = 0; i < KEYS.length; i++) {
                cell(i, o);
                String k = KEYS[i];
                boolean num = k.length() == 1 && Character.isDigit(k.charAt(0));
                boolean dis = (k.equals("del") || k.equals("call")) && digits.length() == 0;
                String st = dis ? "_d" : pressed == i ? "_p" : "_n";
                String bg = k.equals("call") ? "bt_btn_bg_keycall" : num ? "key_btn_bg_key1" : "key_btn_bg_key2";
                int w = 190, hh = 96;
                Pic.get(getContext(), bg + st + ".9.png").draw(c, o[0], o[1], o[0] + w, o[1] + hh);
                if (num) {
                    Paint pn = tp(56, false, Palette.TEXT); pn.setTextAlign(Paint.Align.CENTER);
                    c.drawText(k, o[0] + w / 2f, o[1] + 10 + 44, pn);
                    Paint pl = tp(20, false, Palette.LIGHT ? Palette.SUBTEXT : 0x80C1C1C1); pl.setTextAlign(Paint.Align.CENTER);
                    c.drawText(CHOSUNG[Integer.parseInt(k) == 0 ? 10 : Integer.parseInt(k) - 1], o[0] + w / 2f, o[1] + 68 + 17, pl);
                } else if (k.equals("#")) {
                    Paint pn = tp(48, false, Palette.TEXT); pn.setTextAlign(Paint.Align.CENTER);
                    Paint.FontMetrics m = pn.getFontMetrics();
                    c.drawText("#", o[0] + w / 2f, o[1] + hh / 2f - (m.ascent + m.descent) / 2f, pn);
                } else {
                    String ic = k.equals("*") ? "bt_ic_star_n.png" : k.equals("setting") ? "key_ic_setting_n.png"
                            : k.equals("del") ? (dis ? "key_ic_del_d.png" : "key_ic_del_n.png") : (dis ? "bt_ic_call_d.png" : "bt_ic_call_n.png");
                    Pic.Pic9 p = ic.startsWith("bt_ic_call") ? Pic.get(getContext(), ic) : Pic.ink(getContext(), ic); // call: on the green key
                    c.drawBitmap(p.bm, o[0] + (w - p.w()) / 2f, o[1] + (hh - p.h()) / 2f, null);
                }
            }
            // input line
            if (digits.length() == 0) text(c, "Enter phone number.", pg, 668 + 16, 77 / 2f, 0);
            else text(c, digits.toString(), pi, 668 + 16, 77 / 2f, 640);
            Pic.ink(getContext(), "co_bg_input_line_n.9.png").draw(c, 668, 77, 668 + 740, 79);
            if (digits.length() >= 2) {
                Paint pc = tp(28, false, Palette.SUBTEXT); pc.setTextAlign(Paint.Align.RIGHT);
                text(c, "(" + list.getAdapter().getCount() + ")", pc, 668 + 710 - 60, 77 / 2f, 0);
            } else {
                text(c, "Recently Called Numbers", ph, 668 + 16, 79 + 30 + 20, 0);
                text(c, deviceName, ph, 668 + 16, 79 + 355 + 30, 538);
            }
            drawInfo(c, 1448);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            float x = e.getX(), y = e.getY();
            int idx = -1;
            if (x >= 44 && x < 44 + 582 && y >= 0 && y < 505) idx = (int) ((y) / 101) * 3 + (int) ((x - 44) / 194);
            if (idx >= KEYS.length) idx = -1;
            if (a == MotionEvent.ACTION_DOWN) { pressed = idx; invalidate(); return idx >= 0; }
            if (a == MotionEvent.ACTION_UP) {
                int k = pressed; pressed = -1;
                if (k >= 0 && k == idx) press(KEYS[k]);
                invalidate();
                return true;
            }
            if (a == MotionEvent.ACTION_CANCEL) { pressed = -1; invalidate(); }
            return true;
        }

        private void press(String k) {
            if (k.equals("del")) { if (digits.length() > 0) digits.setLength(digits.length() - 1); }
            else if (k.equals("call")) { call(digits.toString()); return; }
            else if (k.equals("setting")) {
                try { startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable ignored) {}
                return;
            }
            else if (digits.length() < MAX_INPUT) digits.append(k);
            refreshList();
        }
    }
}
