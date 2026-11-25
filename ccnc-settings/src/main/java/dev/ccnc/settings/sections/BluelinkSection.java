package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.Intent;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Section;

/** Bluelink (stock classId com.hkmc.telematics.apps.Vrm.main.bluelink). Stage 2: add the menus/rows this car really reads/writes; see /tmp/swarm/settings-api.md. */
public final class BluelinkSection extends Section {
    @Override public String classId() { return "com.hkmc.telematics.apps.Vrm.main.bluelink"; }
    @Override public int treeNode() { return 1017; }
    @Override public String icon() { return "set_ic_menu_bluelink"; }

    @Override public void build(Category c) {
        // No rows yet: the card opens the stock Gen5W screen directly.
    }

    @Override public Intent stockIntent(Context c) { return component("com.hkmc.telematics.apps.Vrm.main", "com.hkmc.telematics.apps.Vrm.settings.ui.BluelinkUvoSettingsMenu"); }
}
