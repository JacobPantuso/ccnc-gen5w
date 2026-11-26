package dev.ccnc.settings.sections;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.ccnc.settings.sections.VehicleSectionUsm.Node;

/**
 * Emulator-only demo tables (see dev.ccnc.settings.api.Demo): the Vehicle and EV item trees an EV would report, in
 * the shape VehicleSectionUsm builds from the stock providers (keyid ranges: &lt;10000 group, 1xxxx radio group,
 * 2xxxx radio option, 3xxxx check box, 4xxxx level). Invented wording and values; never used on a car build.
 */
final class DemoData {
    private DemoData() { }
    private static long ids;

    private static Node node(int key, String text) {
        Node n = new Node();
        n.key = key; n.text = text; n.id = ids++;
        return n;
    }

    private static Node menu(List<Node> out, int minor, int key, String text) {
        Node n = node(key, text);
        n.minor = minor; n.major = VehicleSectionUsm.ROOT_MAJOR;
        out.add(n);
        return n;
    }

    private static Node group(Node parent, int key, String text) {
        Node n = node(key, text);
        n.parent = parent.key; n.major = parent.minor;
        parent.kids.add(n);
        return n;
    }

    private static void check(Node parent, Map<Integer, Integer> vals, int key, String text, boolean on) {
        Node n = node(key, text);
        n.parent = parent.key;
        parent.kids.add(n);
        vals.put(key, on ? 1 : 0);
    }

    /** Radio group key with options key+1.. ; sel = index of the selected option. */
    private static void radio(Node parent, Map<Integer, Integer> vals, int key, String text, int sel, String... options) {
        Node n = node(key, text);
        n.parent = parent.key;
        parent.kids.add(n);
        for (int i = 0; i < options.length; i++) {
            Node o = node(key + 10000 + i + 1, options[i]);
            o.parent = key;
            n.kids.add(o);
        }
        vals.put(key, key + 10000 + sel + 1);
    }

    private static void level(Node parent, Map<Integer, Integer> vals, int key, String text, int v) {
        Node n = node(key, text);
        n.parent = parent.key;
        parent.kids.add(n);
        vals.put(key, v);
    }

    /** The menus (ev: the EV3 table, else Vehicle Settings); vals receives every leaf's starting value. */
    static List<Node> menus(boolean ev, Map<Integer, Integer> vals) {
        List<Node> out = new ArrayList<Node>();
        if (ev) {
            Node m = menu(out, 0, 100, "Max. % Charge");
            level(m, vals, 40001, "DC Charger", 80);
            level(m, vals, 40002, "AC Charger", 100);
            m = menu(out, 0, 110, "Charging Current");
            radio(m, vals, 10010, "AC Charging Current", 0, "100%", "90%", "60%");
            m = menu(out, 0, 120, "Battery Conditioning Mode");
            check(m, vals, 30020, "Battery Conditioning Mode", true);
            m = menu(out, 0, 130, "Utility Mode");
            check(m, vals, 30030, "Utility Mode", false);
            m = menu(out, 0, 140, "Smart Regeneration System");
            check(m, vals, 30040, "Smart Regeneration System", true);
            return out;
        }
        // Driver Assistance (groups 14 / 53 / 38 carry the ccNC guide art in VehicleSection)
        Node m = menu(out, 3315, 2, "Driver Assistance");
        Node g = group(m, 14, "Driving Convenience");
        check(g, vals, 30101, "Highway Driving Assist", true);
        check(g, vals, 30102, "Auto Highway Speed Change", false);
        g = group(m, 53, "Driving Safety");
        radio(g, vals, 10110, "Forward Safety", 0, "Active Assist", "Warning Only", "Off");
        check(g, vals, 30111, "Lane Safety", true);
        radio(g, vals, 10120, "Blind-Spot Safety", 0, "Active Assist", "Warning Only", "Off");
        g = group(m, 38, "Warning Volume");
        radio(g, vals, 10130, "Warning Volume", 1, "High", "Medium", "Low", "Off");
        m = menu(out, 3316, 3, "Drive Mode");
        radio(m, vals, 10200, "Snow Mode Guidance", 0, "On", "Off");
        m = menu(out, 3321, 5, "Cluster");
        check(m, vals, 30301, "Wiper/Lights Display", true);
        check(m, vals, 30302, "Icy Road Warning", true);
        level(m, vals, 40301, "Cluster Voice Guidance Volume", 3);
        m = menu(out, 3317, 6, "Climate");
        check(m, vals, 30401, "Auto Defog", true);
        check(m, vals, 30402, "Activate Recirculation Upon Washer Fluid Use", true);
        m = menu(out, 3323, 7, "Seat");
        check(m, vals, 30501, "Seat Easy Access", true);
        radio(m, vals, 10500, "Heated/Ventilated Features", 0, "Auto Comfort Control", "Off");
        m = menu(out, 3319, 8, "Lights");
        radio(m, vals, 10600, "Ambient Light Colour", 3, "Polar White", "Moon White", "Ice Blue", "Ocean Blue",
                "Jade Green", "Orchid Green", "Freesia Yellow", "Sunrise Red", "Aurora Purple", "Lightning Violet");
        level(m, vals, 40601, "Ambient Light Brightness", 5);
        radio(m, vals, 10620, "One Touch Turn Signal", 1, "Off", "3 Flashes", "5 Flashes", "7 Flashes");
        check(m, vals, 30601, "High Beam Assist", true);
        m = menu(out, 3320, 9, "Door");
        radio(m, vals, 10700, "Automatically Lock", 0, "Enable on Speed", "Enable on Shift", "Off");
        check(m, vals, 30701, "Two Press Unlock", false);
        check(m, vals, 30702, "Power Tailgate", true);
        m = menu(out, 3322, 10, "Convenience");
        check(m, vals, 30801, "Welcome Mirror/Light", true);
        check(m, vals, 30802, "Wireless Charging System", true);
        check(m, vals, 30803, "Auto Rear Wiper (In R)", false);
        return out;
    }
}
