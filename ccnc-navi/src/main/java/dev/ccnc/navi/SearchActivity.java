package dev.ccnc.navi;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Chrome;
import dev.ccnc.common.Drive;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;
import dev.ccnc.common.Popup;

/**
 * ccNC search: a search field and ccNC keyboard on the driver's side, and on the other side Recent / Favorites (with
 * Home and Work) or, while typing, the matching saved places, a typed position, and one row that hands the text to
 * the map's own search (the only stock screen left: the engine returns search results to no other app). Choosing a
 * place starts guidance in the stock engine through {@link StockNavi#route}.
 */
public final class SearchActivity extends Activity {
    static final String EXTRA_TAB = "dev.ccnc.navi.TAB";
    static final int TAB_RECENT = 0, TAB_FAVORITES = 1;

    private static final int PLACE = 0, HOME = 1, WORK = 2, COORDS = 3, STOCK = 4, EMPTY = 5;

    private static final class Row {
        int type;
        Place place;
        String title = "", sub = "", icon;
        boolean favorite;
    }

    private String query = "";
    private int tab = TAB_RECENT;
    private final List<Row> rows = new ArrayList<Row>();
    private Place here;
    private Field field;
    private Tabs tabs;
    private Pinned pinned;
    private RowAdapter adapter;
    private ListView list;

    private final BroadcastReceiver gps = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Place p = StockNavi.position(i);
            if (p == null) return;
            boolean first = here == null;
            here = p;
            if (first) { Log.i(StockNavi.TAG, "search: position from NAVI_GPS_CHANGED"); adapter.notifyDataSetChanged(); }
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        tab = getIntent().getIntExtra(EXTRA_TAB, TAB_RECENT);

        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(Chrome.topBar(this, "Search"));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        int m = Board.px(this, 30);
        body.setPadding(m, Board.px(this, 12), m, 90 + Board.px(this, 12)); // 90 px: ccNC's bottom bar
        content.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Driver's side: field + keyboard. Other side: the list.
        LinearLayout kb = new LinearLayout(this);
        kb.setOrientation(LinearLayout.VERTICAL);
        field = new Field(this);
        kb.addView(field, new LinearLayout.LayoutParams(-1, Board.px(this, 88)));
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(-1, 0, 1f);
        kp.topMargin = Board.px(this, 14);
        kb.addView(new Keyboard(this, new Keyboard.Listener() {
            @Override public void onText(String s) { type(s); }
            @Override public void onDelete(boolean all) { delete(all); }
            @Override public void onSearch() { submit(); }
        }), kp);

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        // Home and Work pinned on top (hidden while typing: matches show in the results), then the filters.
        pinned = new Pinned(this);
        side.addView(pinned, new LinearLayout.LayoutParams(-1, Board.px(this, 112)));
        tabs = new Tabs(this);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, Board.px(this, 76));
        tp.topMargin = Board.px(this, 10);
        side.addView(tabs, tp);
        list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(0);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { Chrome.beep(SearchActivity.this); open(rows.get(pos)); }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) { return options(rows.get(pos)); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = Board.px(this, 14);
        side.addView(list, lp);

        LinearLayout.LayoutParams kbp = new LinearLayout.LayoutParams(Board.px(this, 900), -1);
        LinearLayout.LayoutParams sdp = new LinearLayout.LayoutParams(0, -1, 1f);
        if (Drive.rhd(this)) { sdp.rightMargin = Board.px(this, 30); body.addView(side, sdp); body.addView(kb, kbp); }
        else { sdp.leftMargin = Board.px(this, 30); body.addView(kb, kbp); body.addView(side, sdp); }
        setContentView(root);
        rebuild();
    }

    @Override protected void onResume() {
        super.onResume();
        try { registerReceiver(gps, new IntentFilter(StockNavi.GPS)); } catch (Throwable t) { Log.w(StockNavi.TAG, "gps: " + t); }
        rebuild(); // the DestinationReceiver may have added a recent meanwhile
    }

    @Override protected void onPause() {
        try { unregisterReceiver(gps); } catch (IllegalArgumentException ignored) {}
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (!query.isEmpty()) { query = ""; rebuild(); return; }
        super.onBackPressed();
    }

    // ---- typing ----

    private void type(String s) {
        if (query.length() >= 80) return;
        if (s.equals(" ") && (query.isEmpty() || query.endsWith(" "))) return;
        boolean wordStart = query.isEmpty() || query.endsWith(" ");
        query += wordStart ? s : s.toLowerCase(Locale.getDefault());
        rebuild();
    }

    private void delete(boolean all) {
        if (query.isEmpty()) return;
        query = all ? "" : query.substring(0, query.length() - 1);
        rebuild();
    }

    /** The keyboard's Search: a typed position routes straight away; any other text goes to the map's search. */
    private void submit() {
        String q = query.trim();
        if (q.isEmpty()) return;
        Place p = Place.parseCoords(q);
        if (p != null) { route(p); return; }
        StockNavi.stockSearch(this, q);
    }

    // ---- rows ----

    private void rebuild() {
        rows.clear();
        String q = query.trim();
        Place home = Places.home(this), work = Places.work(this);
        List<Place> favs = Places.favorites(this);
        if (q.isEmpty()) {
            if (tab == TAB_RECENT) {
                List<Place> rec = Places.recents(this);
                for (Place p : rec) rows.add(place(p, favs));
                if (rec.isEmpty()) rows.add(empty("No recent destinations", null));
            } else {
                for (Place p : favs) rows.add(place(p, favs));
                if (favs.isEmpty()) rows.add(empty("No favorites", null));
            }
        } else {
            Place c = Place.parseCoords(q);
            if (c != null) {
                Row r = new Row();
                r.type = COORDS; r.place = c; r.icon = "navi_poi";
                r.title = "Go to These Coordinates"; r.sub = c.coords();
                rows.add(r);
            }
            List<Place> seen = new ArrayList<Place>();
            // Home and Work are found by their own name too ("ho" finds Home).
            String ql = q.toLowerCase(java.util.Locale.US);
            if (home != null && (home.matches(q) || "home".startsWith(ql))) { rows.add(special(HOME, home)); seen.add(home); }
            if (work != null && (work.matches(q) || "work".startsWith(ql))) { rows.add(special(WORK, work)); seen.add(work); }
            List<Place> all = new ArrayList<Place>(favs);
            all.addAll(Places.recents(this));
            for (Place p : all) {
                if (!p.matches(q)) continue;
                boolean dup = false;
                for (Place s : seen) if (s.near(p)) { dup = true; break; }
                if (dup) continue;
                seen.add(p);
                rows.add(place(p, favs));
            }
            Row s = new Row();
            s.type = STOCK; s.icon = "navi_search";
            s.title = "Search the map for “" + q + "”";
            s.sub = "Opens Gen5W NAVI Search";
            rows.add(s);
        }
        if (field != null) field.invalidate();
        if (tabs != null) tabs.invalidate();
        if (pinned != null) {
            pinned.home = special(HOME, home);
            pinned.work = special(WORK, work);
            pinned.setVisibility(q.isEmpty() ? View.VISIBLE : View.GONE);
            pinned.invalidate();
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (list != null) list.setSelection(0);
    }

    private Row place(Place p, List<Place> favs) {
        Row r = new Row();
        r.type = PLACE; r.place = p; r.icon = "navi_poi";
        r.title = p.title(); r.sub = p.subtitle();
        for (Place f : favs) if (f.near(p)) { r.favorite = true; break; }
        return r;
    }

    private Row special(int type, Place p) {
        Row r = new Row();
        r.type = type; r.place = p;
        r.icon = type == HOME ? "navi_home" : "navi_office";
        String n = type == HOME ? "Home" : "Work";
        r.title = n;
        r.sub = p != null ? describe(p, n) : "Not set";
        return r;
    }

    /** "Name · Address" of a saved Home/Work (the row title is already Home or Work). */
    private static String describe(Place p, String label) {
        String n = p.name.equals(label) ? "" : p.name;
        if (!n.isEmpty() && !p.address.isEmpty()) return n + " \u00B7 " + p.address;
        if (!n.isEmpty()) return n;
        if (!p.address.isEmpty()) return p.address;
        return p.coords();
    }

    private Row empty(String title, String sub) {
        Row r = new Row();
        r.type = EMPTY; r.title = title; r.sub = sub;
        return r;
    }

    private void open(Row r) {
        switch (r.type) {
            case STOCK: StockNavi.stockSearch(this, query.trim()); break;
            case EMPTY: break;
            default:
                if (r.place == null) {
                    Toast.makeText(this, "Hold a place and choose Set as " + (r.type == HOME ? "Home" : "Work"), Toast.LENGTH_LONG).show();
                    return;
                }
                route(r.place);
        }
    }

    private void route(Place p) {
        if (StockNavi.route(this, p)) {
            query = "";
            finish(); // the map comes to the front; Back from it returns to the hub
        } else {
            Toast.makeText(this, "This place has no position", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean options(final Row r) {
        if (r.place == null) return false;
        final Place p = r.place;
        final List<String> labels = new ArrayList<String>();
        final List<Runnable> acts = new ArrayList<Runnable>();
        labels.add("Navigate"); acts.add(new Runnable() { @Override public void run() { route(p); } });
        labels.add("Show on Map"); acts.add(new Runnable() { @Override public void run() { StockNavi.showOnMap(SearchActivity.this, p); } });
        if (r.type == HOME || r.type == WORK) {
            labels.add(r.type == HOME ? "Clear Home" : "Clear Work");
            acts.add(new Runnable() { @Override public void run() {
                if (r.type == HOME) Places.setHome(SearchActivity.this, null); else Places.setWork(SearchActivity.this, null);
                rebuild();
            } });
        } else {
            final boolean fav = Places.isFavorite(this, p);
            labels.add(fav ? "Remove from Favorites" : "Add to Favorites");
            acts.add(new Runnable() { @Override public void run() { Places.toggleFavorite(SearchActivity.this, p); rebuild(); } });
            labels.add("Set as Home"); acts.add(new Runnable() { @Override public void run() { Places.setHome(SearchActivity.this, p); rebuild(); } });
            labels.add("Set as Work"); acts.add(new Runnable() { @Override public void run() { Places.setWork(SearchActivity.this, p); rebuild(); } });
            if (query.isEmpty() && tab == TAB_RECENT) {
                labels.add("Remove from Recent");
                acts.add(new Runnable() { @Override public void run() { Places.removeRecent(SearchActivity.this, p); rebuild(); } });
            }
        }
        Chrome.beep(this);
        new Popup(this, p.title(), p.subtitle().isEmpty() ? null : p.subtitle())
                .options(labels.toArray(new String[labels.size()]), -1, new Popup.Pick() {
                    @Override public void onPick(int i) { acts.get(i).run(); }
                }).show();
        return true;
    }

    // ---- views ----

    /** The search field: the text with a caret, or the hint; a clear button while there is text. */
    private final class Field extends Board {
        Field(Context c) { super(c); }

        @Override protected void paint(Canvas c) {
            float w = W(), h = H();
            card(c, 0, 0, w, h, h / 2f, false, Palette.CARD_HIGHLIGHT);
            icon(c, "navi_search", 22, (h - 52) / 2f, 52);
            float tx = 92, maxW = w - tx - (query.isEmpty() ? 30 : 110);
            if (query.isEmpty()) {
                txtMid(c, "Name, Address or Coordinates", tx, h / 2f, 30, Palette.HINT, Fonts.REGULAR, Paint.Align.LEFT, maxW);
            } else {
                // Show the end of a long query, with a caret after it.
                text.setTypeface(Fonts.get(getContext(), Fonts.SEMIBOLD));
                text.setTextSize(X(32));
                String t = query;
                while (t.length() > 1 && text.measureText(t) > X(maxW - 12)) t = t.substring(1);
                if (t.length() != query.length()) t = "…" + t.substring(Math.min(1, t.length()));
                txtMid(c, t, tx, h / 2f, 32, Palette.TEXT, Fonts.SEMIBOLD, Paint.Align.LEFT, 0);
                float cx = tx + text.measureText(t) / k + 4;
                fill.setColor(Palette.ACCENT);
                c.drawRect(X(cx), X(h / 2f - 20), X(cx + 3), X(h / 2f + 20), fill);
                int i = box(w - 96, 0, 96, h, new Runnable() { @Override public void run() { query = ""; rebuild(); } }, null);
                if (isDown(i)) { fill.setColor(Palette.PRESS); c.drawCircle(X(w - 50), X(h / 2f), X(30), fill); }
                txtMid(c, "✕", w - 50, h / 2f, 28, Palette.SUBTEXT, Fonts.REGULAR, Paint.Align.CENTER, 0);
            }
        }
    }

    /** Recent | Favorites | Categories; a Results label while typing. */
    private final class Tabs extends Board {
        Tabs(Context c) { super(c); }

        @Override protected void paint(Canvas c) {
            float h = H(), x = 0;
            if (!query.trim().isEmpty()) {
                txtMid(c, "Results", 6, h / 2f, 30, Palette.TEXT, Fonts.SEMIBOLD, Paint.Align.LEFT, 0);
                x = 200;
            }
            x = chip(c, x, h, "Recent", query.trim().isEmpty() && tab == TAB_RECENT, new Runnable() { @Override public void run() { tab = TAB_RECENT; query = ""; rebuild(); } });
            x = chip(c, x, h, "Favorites", query.trim().isEmpty() && tab == TAB_FAVORITES, new Runnable() { @Override public void run() { tab = TAB_FAVORITES; query = ""; rebuild(); } });
            chip(c, x, h, "Categories", false, new Runnable() { @Override public void run() {
                startActivity(new Intent(SearchActivity.this, CategoriesActivity.class));
            } });
        }

        private float chip(Canvas c, float x, float h, String label, boolean on, Runnable tap) {
            text.setTypeface(Fonts.get(getContext(), Fonts.SEMIBOLD));
            text.setTextSize(X(26));
            float w = text.measureText(label) / k + 56;
            int i = box(x, 6, w, h - 12, tap, null);
            card(c, x, 6, w, h - 12, (h - 12) / 2f, isDown(i), on ? Palette.alpha(Palette.ACCENT, 0x66) : Palette.CARD);
            txtMid(c, label, x + w / 2f, h / 2f, 26, Palette.TEXT, Fonts.SEMIBOLD, Paint.Align.CENTER, 0);
            return x + w + 14;
        }
    }

    /** Home and Work side by side, always on top of Recent and Favorites. Tap: go there (or how to set it); hold: options. */
    private final class Pinned extends Board {
        Row home, work;
        Pinned(Context c) { super(c); }

        @Override protected void paint(Canvas c) {
            if (home == null || work == null) return;
            float gap = 16, w = (W() - gap) / 2f, h = H();
            tile(c, mx(0, w), w, h, home);
            tile(c, mx(w + gap, w), w, h, work);
        }

        private void tile(Canvas c, float x, float w, float h, final Row r) {
            int i = box(x, 0, w, h,
                    new Runnable() { @Override public void run() { Chrome.beep(SearchActivity.this); open(r); } },
                    new Runnable() { @Override public void run() { options(r); } });
            card(c, x, 0, w, h, 20, isDown(i), Palette.CARD);
            icon(c, r.icon, x + 22, (h - 56) / 2f, 56);
            float tx = x + 96, maxW = w - 96 - 20;
            txtMid(c, r.title, tx, h * 0.36f, 30, Palette.TEXT, Fonts.SEMIBOLD, Paint.Align.LEFT, maxW);
            txtMid(c, r.sub, tx, h * 0.70f, 23, Palette.SUBTEXT, Fonts.REGULAR, Paint.Align.LEFT, maxW);
        }
    }

    /** One list row: icon, title, subtitle; distance and a favourite star on the right. */
    private final class RowView extends Board {
        Row row;
        RowView(Context c) { super(c); setClickable(false); }

        @Override protected void paint(Canvas c) {
            if (row == null) return;
            float w = W(), h = H() - 10;
            if (row.type == EMPTY) {
                // Part of the list, not a card: a quiet line where the places would be.
                txtMid(c, row.title, 6, h / 2f, 26, Palette.SUBTEXT, Fonts.REGULAR, Paint.Align.LEFT, w - 12);
                return;
            }
            card(c, 0, 0, w, h, 20, isPressed(), row.type == STOCK ? Palette.alpha(Palette.ACCENT, 0x33) : Palette.CARD);
            float tx = 28;
            if (row.icon != null) { icon(c, row.icon, 24, (h - 60) / 2f, 60); tx = 106; }
            String right = "";
            if (here != null && row.place != null && row.type != COORDS) right = Place.distance(Place.meters(here.lat, here.lon, row.place.lat, row.place.lon));
            float rw = right.isEmpty() ? 0 : 150;
            if (row.favorite) rw += 50;
            float maxW = w - tx - 24 - rw;
            boolean two = row.sub != null && !row.sub.isEmpty();
            txtMid(c, row.title, tx, two ? h * 0.36f : h / 2f, 30, row.type == EMPTY ? Palette.SUBTEXT : Palette.TEXT, Fonts.SEMIBOLD, Paint.Align.LEFT, maxW);
            if (two) txtMid(c, row.sub, tx, h * 0.72f, 23, Palette.SUBTEXT, Fonts.REGULAR, Paint.Align.LEFT, maxW);
            float rx = w - 24;
            if (!right.isEmpty()) { txtMid(c, right, rx, h / 2f, 24, Palette.SUBTEXT, Fonts.REGULAR, Paint.Align.RIGHT, 140); rx -= 150; }
            if (row.favorite) star(c, rx - 18, h / 2f, 16, true, Palette.ACCENT);
        }

        @Override public void setPressed(boolean p) { super.setPressed(p); invalidate(); }

        /** The ListView does taps, long presses, scrolling and the pressed state. */
        @Override public boolean onTouchEvent(MotionEvent e) { return false; }
    }

    private final class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public boolean isEnabled(int i) { return rows.get(i).type != EMPTY; }
        @Override public View getView(int i, View v, ViewGroup parent) {
            RowView rv = v instanceof RowView ? (RowView) v : new RowView(SearchActivity.this);
            rv.setLayoutParams(new AbsListView.LayoutParams(-1, Board.px(SearchActivity.this, 112)));
            rv.row = rows.get(i);
            rv.invalidate();
            return rv;
        }
    }
}
