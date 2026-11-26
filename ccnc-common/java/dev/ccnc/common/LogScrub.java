package dev.ccnc.common;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes personal information from exported logs before they reach the USB stick (DevMenu's Export Logs).
 *
 * Three layers:
 *   1. Known values from this car, replaced wherever they appear, even in free text: paired phones' names and
 *      Bluetooth addresses, the head unit's own Bluetooth name and address, profile names, the Wi-Fi network,
 *      the unit's serial number.
 *   2. Patterns: MAC addresses (numbered, so one device stays recognisable across files), email addresses,
 *      tel:/sms:/mailto: links, phone numbers, coordinates, VIN, IMEI/ICCID-like numbers, public IP addresses, and
 *      the value of sensitive keys (name=, ssid=, lat=, token=, password=, serial= ...), getprop lines included.
 *   3. Whole log lines from the phone book, messages and call history (tag names) are replaced by a marker.
 * Free-text names that only the phone knows (a contact named in a sentence) can't all be found; the developer menu
 * still asks the owner to share logs only with people they trust.
 */
final class LogScrub {
    static final String TAG = "ccncScrub";

    private final List<String[]> literals = new ArrayList<String[]>();   // {value, replacement}, longest first
    private final Map<String, String> macs = new HashMap<String, String>();
    final Map<String, Integer> counts = new LinkedHashMap<String, Integer>();

    private static final Pattern MAC = Pattern.compile("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern URI = Pattern.compile("(?i)\\b(tel|sms|smsto|mms|mailto|geo):[^\\s,;)\\]}\"']+");
    private static final Pattern COORDS = Pattern.compile("-?\\d{1,3}\\.\\d{4,}\\s*,\\s*-?\\d{1,3}\\.\\d{4,}");
    private static final Pattern KEYVAL = Pattern.compile("(?i)\\b(?:m_?)?(lat|lng|lon|latitude|longitude|phone|phonenumber|phone_number"
            + "|msisdn|callerid|caller_id|callername|caller_name|callernumber|caller_number|contactname|contact_name|displayname|display_name|devicename|device_name|remotename"
            + "|remote_name|friendlyname|btname|bt_name|username|user_name|ownername|owner_name|ssid|bssid|imei|imsi|iccid|meid"
            + "|serial|serialno|serial_no|serialnumber|serial_number|vin|email|password|passwd|pwd|token|access_token"
            + "|refresh_token|apikey|api_key|appid|app_id|secret|authorization|cookie)\\b(\\s*[=:]\\s*)"
            + "(\"[^\"]*\"|'[^']*'|\\[[^\\]]*\\]|[^\\s,;&)\\]}]+)");
    private static final Pattern GETPROP = Pattern.compile("^\\[([^\\]]*)\\]: \\[(.*)\\]$");
    private static final Pattern PROP_KEY = Pattern.compile("(?i)serial|vin|imei|imsi|iccid|meid|mac|bdaddr|bt.*addr|ssid|phone"
            + "|msisdn|number|name|lat|lon|location|account|email|owner|user");
    private static final Pattern PHONE_INTL = Pattern.compile("(?<![\\w.:/-])\\+\\d[\\d\\- ]{6,16}\\d\\b");
    private static final Pattern PHONE_NANP = Pattern.compile("(?<![\\w.:/-])(?:\\+?1[-. ]?)?\\(?[2-9]\\d{2}\\)?[-. ]\\d{3}[-. ]\\d{4}(?![\\w.:/-])");
    /** A device name in the log ("ipodName = Jacobs iPhone", "mWiredDeviceName Jacobs iPhone", deviceName=...). */
    private static final Pattern NAME_FIELD = Pattern.compile("(?i)\\b(?:m_?)?(?:ipod ?name|(?:wired|wireless|connected|remote|friendly"
            + "|bt|phone|owner|user|bonded|paired|iap|selected|current)?_?device_?name|remote_?name|friendly_?name|bt_?name|phone_?name"
            + "|owner_?name|user_?name|local_?name)\\b\\s*(?:=>|=|:)?\\s*(.+)");
    /** iPhone UDIDs (00008150-000A308A0EDA401C, with or without the dash). */
    private static final Pattern UDID = Pattern.compile("\\b[0-9A-Fa-f]{8}-?[0-9A-Fa-f]{16}\\b");
    /** Bare 10-digit numbers are only phone numbers on lines about phones (else view IDs, indexes ...). */
    private static final Pattern PHONE_CONTEXT = Pattern.compile("(?i)phone|number|call|tel|dial|contact|sms|hfp|pbap|incoming|outgoing|ring");
    private static final Pattern PHONE_BARE = Pattern.compile("(?<![\\w.:/-])(?:1[2-9]\\d{9}|[2-9]\\d{9})(?![\\w.:/-])");
    private static final Pattern LONG_ID = Pattern.compile("(?<![\\w.:/-])(?:89\\d{16,18}|\\d{15})(?![\\w.:/-])");
    private static final Pattern VIN = Pattern.compile("\\b(?=[A-HJ-NPR-Z0-9]*\\d)(?=[A-HJ-NPR-Z0-9]*[A-HJ-NPR-Z])[A-HJ-NPR-Z0-9]{17}\\b");
    private static final Pattern IPV4 = Pattern.compile("(?<![\\w.])(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?![\\w.])");
    private static final Pattern LOGCAT_TAG = Pattern.compile("^(\\S+ \\S+\\s+\\d+\\s+\\d+ [VDIWEFA] )([^:]*):");
    private static final Pattern PRIVATE_TAG = Pattern.compile("(?i)pbap|phonebook|contact|calllog|callhistory|call_history"
            + "|recentcall|btphone|bt_phone|bluetoothmap|btmap|mapclient|msgapp|vcard|dialer");
    /** SMS / MMS as a word of the tag (SmsReceiver, BtSMS, mms_service), not letters inside one (USMService = seats). */
    private static final Pattern SMS_TAG = Pattern.compile("(?:(?:^|[^A-Za-z])(?:[Ss]ms|SMS|[Mm]ms|MMS)|(?<=[a-z])(?:Sms|SMS|Mms|MMS))(?:[^a-z]|$)");

    LogScrub(Context c) {
        try {
            android.bluetooth.BluetoothAdapter bt = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (bt != null) {
                int n = 0;
                for (android.bluetooth.BluetoothDevice d : bt.getBondedDevices()) {
                    n++;
                    addLiteral(d.getName(), "[phone-" + n + "]");
                    mac(d.getAddress());
                }
                addLiteral(bt.getName(), "[head-unit-bt-name]");
                mac(bt.getAddress());
            }
        } catch (Throwable t) { Log.w(TAG, "bluetooth names", t); }
        try {
            for (int i = 0; i < Profiles.COUNT; i++) {
                String n = Profiles.name(c, i);
                if (!n.equals("Guest") && !n.equals("Driver 1") && !n.equals("Driver 2")) addLiteral(n, "[profile-" + i + "]");
            }
        } catch (Throwable t) { Log.w(TAG, "profile names", t); }
        try {
            android.net.wifi.WifiManager w = (android.net.wifi.WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (w != null && w.getConnectionInfo() != null) {
                String s = w.getConnectionInfo().getSSID();
                if (s != null) addLiteral(s.replace("\"", ""), "[wifi]");
            }
        } catch (Throwable t) { Log.w(TAG, "wifi", t); }
        for (String k : new String[]{"ro.serialno", "ro.boot.serialno"}) addLiteral(DevMenu.prop(k), "[serial]");
        Collections.sort(literals, new Comparator<String[]>() {
            @Override public int compare(String[] a, String[] b) { return b[0].length() - a[0].length(); }
        });
    }

    private void addLiteral(String v, String token) {
        if (v == null) return;
        v = v.trim();
        // The same name is logged with a straight, a curly or no apostrophe ("Jacob's", "Jacob\u2019s", "Jacobs").
        if (v.indexOf('\'') >= 0 || v.indexOf('\u2019') >= 0) {
            addOne(v.replace('\u2019', '\''), token);
            addOne(v.replace('\'', '\u2019'), token);
            addOne(v.replace("'", "").replace("\u2019", ""), token);
        }
        addOne(v, token);
    }

    private void addOne(String v, String token) {
        if (v.length() < 3 || v.equalsIgnoreCase("<unknown ssid>") || v.equalsIgnoreCase("unknown")) return;
        for (String[] l : literals) if (l[0].equals(v)) return;
        literals.add(new String[]{v, token});
    }

    /** A stable stand-in for each Bluetooth / Wi-Fi address: [mac-1], [mac-2] ... */
    private String mac(String m) {
        if (m == null) return null;
        String k = m.toUpperCase(java.util.Locale.US).replace('-', ':');
        if (k.equals("00:00:00:00:00:00") || k.equals("02:00:00:00:00:00") || k.equals("FF:FF:FF:FF:FF:FF")) return m;
        String t = macs.get(k);
        if (t == null) { t = "[mac-" + (macs.size() + 1) + "]"; macs.put(k, t); }
        return t;
    }

    private void count(String what) {
        Integer n = counts.get(what);
        counts.put(what, n == null ? 1 : n + 1);
    }

    /** One line, cleaned. */
    String line(String l) {
        if (l.length() == 0) return l;
        Matcher tag = LOGCAT_TAG.matcher(l);
        if (tag.find() && (PRIVATE_TAG.matcher(tag.group(2)).find() || SMS_TAG.matcher(tag.group(2)).find())) {
            count("phone book / message / call log lines");
            return tag.group(1) + tag.group(2) + ": [line removed: phone book, messages or calls]";
        }
        for (String[] lit : literals) {
            if (l.contains(lit[0])) { l = l.replace(lit[0], lit[1]); count("names and network of this car"); }
        }
        Matcher g = GETPROP.matcher(l);
        if (g.matches()) {
            String k = g.group(1);
            if (g.group(2).length() > 0 && PROP_KEY.matcher(k).find() && !k.startsWith("ro.build.") && !k.startsWith("ro.product.")
                    && !k.startsWith("ro.board.") && !k.startsWith("ro.hardware")) {
                count("system properties");
                return "[" + k + "]: [redacted]";
            }
        }
        l = replace(MAC, l, "Bluetooth / Wi-Fi addresses", null);
        l = replace(EMAIL, l, "email addresses", "[email]");
        l = replaceUri(l);
        l = replace(COORDS, l, "locations", "[location]");
        l = replaceKeyVal(l);
        l = replace(PHONE_INTL, l, "phone numbers", "[phone-number]");
        l = replace(PHONE_NANP, l, "phone numbers", "[phone-number]");
        if (PHONE_CONTEXT.matcher(l).find()) l = replace(PHONE_BARE, l, "phone numbers", "[phone-number]");
        l = replace(UDID, l, "phone serial numbers (UDID)", "[udid]");
        l = replace(LONG_ID, l, "device IDs (IMEI / SIM)", "[id]");
        l = replace(VIN, l, "VIN", "[VIN]");
        l = replaceIp(l);
        return l;
    }

    private String replace(Pattern p, String l, String what, String with) {
        Matcher m = p.matcher(l);
        if (!m.find()) return l;
        StringBuffer b = new StringBuffer();
        do {
            String r = with != null ? with : mac(m.group());
            if (!r.equals(m.group())) count(what);
            m.appendReplacement(b, Matcher.quoteReplacement(r));
        } while (m.find());
        m.appendTail(b);
        return b.toString();
    }

    private String replaceUri(String l) {
        Matcher m = URI.matcher(l);
        if (!m.find()) return l;
        StringBuffer b = new StringBuffer();
        do { count("tel / sms / mail / geo links"); m.appendReplacement(b, Matcher.quoteReplacement(m.group(1) + ":[redacted]")); } while (m.find());
        m.appendTail(b);
        return b.toString();
    }

    private String replaceKeyVal(String l) {
        Matcher m = KEYVAL.matcher(l);
        if (!m.find()) return l;
        StringBuffer b = new StringBuffer();
        do {
            String v = m.group(3);
            boolean empty = v.equals("\"\"") || v.equals("''") || v.equals("[]") || v.equalsIgnoreCase("null");
            if (!empty) count("named values (names, Wi-Fi, location, keys)");
            m.appendReplacement(b, Matcher.quoteReplacement(empty ? m.group() : m.group(1) + m.group(2) + "[redacted]"));
        } while (m.find());
        m.appendTail(b);
        return b.toString();
    }

    /** Public IPv4 addresses go; loopback, private and link-local ones (the car's own network) stay. */
    private String replaceIp(String l) {
        Matcher m = IPV4.matcher(l);
        if (!m.find()) return l;
        StringBuffer b = new StringBuffer();
        do {
            int a = Integer.parseInt(m.group(1)), o2 = Integer.parseInt(m.group(2));
            boolean octets = a <= 255 && o2 <= 255 && Integer.parseInt(m.group(3)) <= 255 && Integer.parseInt(m.group(4)) <= 255;
            boolean local = a == 10 || a == 127 || a == 0 || a == 255 || (a == 192 && o2 == 168) || (a == 172 && o2 >= 16 && o2 <= 31)
                    || (a == 169 && o2 == 254) || a >= 224;
            String r = octets && !local ? "[ip]" : m.group();
            if (!r.equals(m.group())) count("public IP addresses");
            m.appendReplacement(b, Matcher.quoteReplacement(r));
        } while (m.find());
        m.appendTail(b);
        return b.toString();
    }

    private int harvested;

    /**
     * First pass over a file: the device names it logs (NAME_FIELD) become values to remove everywhere, in every file,
     * including lines before the one that named them. Call for every file before file().
     */
    void harvest(File in) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(in), "UTF-8"));
            String l;
            while ((l = r.readLine()) != null) {
                Matcher m = NAME_FIELD.matcher(l);
                if (!m.find()) continue;
                String v = m.group(1);
                int cut = v.length();
                for (char ch : new char[]{',', ';', '}', ']', '[', '{', '(', ')', '|', '\t'}) { int k = v.indexOf(ch); if (k >= 0 && k < cut) cut = k; }
                v = v.substring(0, cut).trim();
                if (v.startsWith("\"") || v.startsWith("'")) v = v.substring(1);
                if (v.endsWith("\"") || v.endsWith("'")) v = v.substring(0, v.length() - 1);
                v = v.trim();
                if (v.length() < 3 || v.length() > 48 || v.equalsIgnoreCase("null") || v.equalsIgnoreCase("unknown")
                        || v.indexOf('=') >= 0 || v.indexOf('/') >= 0 || v.matches("[0-9A-Fa-f:._ -]+")) continue;
                int before = literals.size();
                addLiteral(v, "[phone-name-" + (++harvested) + "]");
                if (literals.size() == before) harvested--;
            }
        } catch (Throwable t) {
            Log.w(TAG, "harvest " + in, t);
        } finally {
            if (r != null) try { r.close(); } catch (Throwable ignored) { }
        }
        Collections.sort(literals, new Comparator<String[]>() {
            @Override public int compare(String[] a, String[] b) { return b[0].length() - a[0].length(); }
        });
    }

    /** Cleans {@code in} into {@code out}, line by line. */
    void file(File in, File out) throws java.io.IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(in), "UTF-8"));
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));
        try {
            String l;
            while ((l = r.readLine()) != null) { w.write(line(l)); w.write('\n'); }
        } finally {
            try { r.close(); } catch (Throwable ignored) { }
            w.close();
        }
    }

    /** What was removed, for SANITIZED.txt. */
    String report() {
        StringBuilder b = new StringBuilder("Personal information removed from these logs by ccNC before they were saved:\n\n");
        if (counts.isEmpty()) b.append("(nothing found)\n");
        for (Map.Entry<String, Integer> e : counts.entrySet()) b.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        b.append("\nReplaced with markers such as [phone-1], [mac-2], [phone-number], [location], [redacted].\n"
                + "The same device keeps the same marker in every file. Free-text names only the phone knows may remain:\n"
                + "look the files over before sharing them.\n");
        return b.toString();
    }
}
