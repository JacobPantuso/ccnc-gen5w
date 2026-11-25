package dev.ccnc.settings.sections;


import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Section;

/** Navigation (stock classId com.hkmc.intent.action.ACTION_NAVI_SETUP). Stage 2: add the menus/rows this car really reads/writes; see /tmp/swarm/settings-api.md. */
public final class NavigationSection extends Section {
    @Override public String classId() { return "com.hkmc.intent.action.ACTION_NAVI_SETUP"; }
    @Override public int treeNode() { return 1011; }
    @Override public String icon() { return "set_ic_menu_navigation"; }

    @Override public void build(Category c) {
        // No rows yet: the card opens the stock Gen5W screen directly.
    }

}
