package dev.ccnc.settings.sections;

import dev.ccnc.settings.api.Section;

/** Registry: one Section per stock Gen5W Setup category (StockMenu.MAP classIds). Stage-2 agents don't edit this. */
public final class Sections {
    private Sections() {}

    public static final Section[] ALL = {
            new VehicleSection(), new EvSection(), new NavigationSection(), new SoundSection(), new BluetoothSection(),
            new DeviceConnectionsSection(), new UserProfileSection(), new VoiceRecognitionSection(), new ThemeSection(),
            new DisplaySection(), new ButtonSection(), new DataNetworkSection(), new BluelinkSection(), new GeneralSection(),
    };

    public static Section byClassId(String id) {
        for (Section s : ALL) if (s.classId().equals(id)) return s;
        return null;
    }

    public static Section byTreeNode(int node) {
        if (node < 0) return null;
        for (Section s : ALL) if (s.treeNode() == node) return s;
        return null;
    }
}
