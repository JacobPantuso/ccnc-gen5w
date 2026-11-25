package dev.ccnc.settings.api;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/** ccNC's own menu wording (en_US titles from ccNC's menuTree.db, assets/tree.tsv) by node id. */
public final class Words {
    private Words() {}
    private static Map<Integer, String> map;

    public static synchronized String get(Context c, int id, String fallback) {
        if (map == null) {
            map = new HashMap<Integer, String>();
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("tree.tsv"), "UTF-8"));
                String l;
                while ((l = r.readLine()) != null) {
                    String[] p = l.split("\t", -1);
                    if (p.length >= 3) try { map.put(Integer.parseInt(p[0]), p[2]); } catch (NumberFormatException ignored) { }
                }
                r.close();
            } catch (Throwable ignored) { }
        }
        String s = map.get(id);
        return s != null ? s : fallback;
    }
}
