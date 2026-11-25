package dev.ccnc.settings;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ccNC's Setup menu tree (from ccNC's own menu database, en_US titles), loaded from assets/tree.tsv. */
final class Tree {
    static final class Node {
        final int id, parent;
        final String title, screen;
        final List<Node> kids = new ArrayList<Node>();
        Node(int id, int parent, String title, String screen) { this.id = id; this.parent = parent; this.title = title; this.screen = screen; }
    }

    static final int ROOT = 1001;
    final Map<Integer, Node> byId = new HashMap<Integer, Node>();

    static Tree load(Context c) {
        Tree t = new Tree();
        t.byId.put(ROOT, new Node(ROOT, 0, "Setup", ""));
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("tree.tsv"), "UTF-8"));
            String l;
            while ((l = r.readLine()) != null) {
                String[] p = l.split("\t", -1);
                if (p.length < 4) continue;
                int id = Integer.parseInt(p[0]);
                t.byId.put(id, new Node(id, Integer.parseInt(p[1]), p[2], p[3]));
            }
            r.close();
        } catch (Throwable ignored) { }
        List<Node> all = new ArrayList<Node>(t.byId.values());
        java.util.Collections.sort(all, new java.util.Comparator<Node>() {
            @Override public int compare(Node a, Node b) { return a.id < b.id ? -1 : a.id == b.id ? 0 : 1; }
        });
        for (Node n : all) {
            Node p = t.byId.get(n.parent);
            if (p == null || n == p) continue;
            if (n.kids.isEmpty() && n.title.equals(p.title)) { /* ccNC's self-named page entry */ }
            p.kids.add(n);
        }
        // drop self-named leaf entries (the page's own search entry)
        for (Node n : all) {
            for (int i = n.kids.size() - 1; i >= 0; i--) {
                Node k = n.kids.get(i);
                if (k.kids.isEmpty() && k.title.equals(n.title)) n.kids.remove(i);
            }
        }
        return t;
    }

    Node top(Node n) {
        while (n != null && n.parent != ROOT && n.parent != 0) n = byId.get(n.parent);
        return n;
    }
}
