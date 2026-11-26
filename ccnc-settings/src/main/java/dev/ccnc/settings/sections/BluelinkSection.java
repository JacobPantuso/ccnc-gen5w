package dev.ccnc.settings.sections;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Section;

/**
 * Bluelink (stock classId com.hkmc.telematics.apps.Vrm.main.bluelink): the same page as the Data/Network category's
 * first tab ({@link DataNetworkSection#telematics}): Activate Service is blocked for a normal app (telematics
 * provisioning), Modem Information is read only. No stock screen is opened.
 */
public final class BluelinkSection extends Section {
    @Override public String classId() { return "com.hkmc.telematics.apps.Vrm.main.bluelink"; }
    @Override public int treeNode() { return 1017; }
    @Override public String icon() { return "set_ic_menu_bluelink"; }

    @Override public void build(Category c) {
        DataNetworkSection.telematics(c, c.context(), c.title);
    }
}
