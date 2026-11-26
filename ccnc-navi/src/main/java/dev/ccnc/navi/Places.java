package dev.ccnc.navi;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ccNC Navigation's own saved places: recents, favourites, home and work. The stock engine keeps its own lists in an
 * encrypted database no other app can open (see private/navi/STOCK-NAVI.md), so ccNC keeps these itself. Recents are
 * filled from every route ccNC starts and from the engine's DestinationInfo broadcast (voice, phone and stock-set
 * destinations too).
 */
final class Places {
    private Places() {}

    static final int MAX_RECENT = 30, MAX_FAVORITES = 60;
    private static final String PREFS = "places", RECENT = "recent", FAV = "favorites", HOME = "home", WORK = "work";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized List<Place> recents(Context c) { return list(c, RECENT); }

    static synchronized List<Place> favorites(Context c) { return list(c, FAV); }

    static synchronized Place home(Context c) { return one(c, HOME); }

    static synchronized Place work(Context c) { return one(c, WORK); }

    static synchronized void setHome(Context c, Place p) { putOne(c, HOME, p); }

    static synchronized void setWork(Context c, Place p) { putOne(c, WORK, p); }

    /** Moves the place to the top of the recents (one entry per spot). Returns false when nothing changed. */
    static synchronized boolean addRecent(Context c, Place p) {
        if (p == null || !p.valid()) return false;
        List<Place> l = list(c, RECENT);
        if (!l.isEmpty() && l.get(0).near(p) && (p.name.isEmpty() || p.name.equals(l.get(0).name))) return false;
        Place keep = p.copy();
        for (int i = l.size() - 1; i >= 0; i--) {
            Place o = l.get(i);
            if (o.near(p)) {
                if (keep.name.isEmpty()) keep.name = o.name;       // the engine sometimes sends a bare position
                if (keep.address.isEmpty()) keep.address = o.address;
                l.remove(i);
            }
        }
        keep.when = System.currentTimeMillis();
        l.add(0, keep);
        while (l.size() > MAX_RECENT) l.remove(l.size() - 1);
        save(c, RECENT, l);
        return true;
    }

    static synchronized void removeRecent(Context c, Place p) { save(c, RECENT, without(list(c, RECENT), p)); }

    static synchronized boolean isFavorite(Context c, Place p) {
        for (Place f : list(c, FAV)) if (f.near(p)) return true;
        return false;
    }

    /** Adds or removes; returns true when it is a favourite afterwards. */
    static synchronized boolean toggleFavorite(Context c, Place p) {
        List<Place> l = list(c, FAV);
        List<Place> w = without(l, p);
        if (w.size() != l.size()) { save(c, FAV, w); return false; }
        Place f = p.copy();
        f.when = System.currentTimeMillis();
        w.add(0, f);
        while (w.size() > MAX_FAVORITES) w.remove(w.size() - 1);
        save(c, FAV, w);
        return true;
    }

    private static List<Place> without(List<Place> l, Place p) {
        List<Place> out = new ArrayList<Place>();
        for (Place o : l) if (!o.near(p)) out.add(o);
        return out;
    }

    private static List<Place> list(Context c, String key) {
        List<Place> out = new ArrayList<Place>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                Place p = Place.from(a.optJSONObject(i));
                if (p != null) out.add(p);
            }
        } catch (Exception e) {
            Log.w(StockNavi.TAG, "places: " + key + " unreadable, starting empty (" + e + ")");
        }
        return out;
    }

    private static void save(Context c, String key, List<Place> l) {
        JSONArray a = new JSONArray();
        for (Place p : l) a.put(p.json());
        prefs(c).edit().putString(key, a.toString()).apply();
    }

    private static Place one(Context c, String key) {
        try {
            String s = prefs(c).getString(key, null);
            return s == null ? null : Place.from(new JSONObject(s));
        } catch (Exception e) {
            return null;
        }
    }

    private static void putOne(Context c, String key, Place p) {
        if (p == null) prefs(c).edit().remove(key).apply();
        else prefs(c).edit().putString(key, p.json().toString()).apply();
    }
}
