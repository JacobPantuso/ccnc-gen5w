package dev.ccnc.navi;

import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A place ccNC Navigation can route to: a name, an address and a position in degrees (WGS84). */
final class Place {
    String name = "", address = "", phone = "";
    double lat = Double.NaN, lon = Double.NaN;
    /** When it was last used (ms), for the recents order. */
    long when;

    Place() {}

    Place(String name, String address, double lat, double lon) {
        this.name = nz(name);
        this.address = nz(address);
        this.lat = lat;
        this.lon = lon;
    }

    boolean valid() {
        return !Double.isNaN(lat) && !Double.isNaN(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180
                && !(Math.abs(lat) < 1e-6 && Math.abs(lon) < 1e-6);
    }

    /** What a list shows as the first line. */
    String title() {
        if (!name.isEmpty()) return name;
        if (!address.isEmpty()) return address;
        return coords();
    }

    /** Second line: the address, or the position when the address is the title or missing. */
    String subtitle() {
        if (!name.isEmpty() && !address.isEmpty()) return address;
        return valid() ? coords() : "";
    }

    String coords() {
        return valid() ? String.format(Locale.US, "%.5f, %.5f", lat, lon) : "";
    }

    /** Same spot (within 40 m): one entry in the recents, whatever name the engine gave it. */
    boolean near(Place o) {
        return o != null && valid() && o.valid() && meters(lat, lon, o.lat, o.lon) < 40;
    }

    boolean matches(String q) {
        String l = q.toLowerCase(Locale.getDefault());
        return name.toLowerCase(Locale.getDefault()).contains(l) || address.toLowerCase(Locale.getDefault()).contains(l);
    }

    JSONObject json() {
        JSONObject o = new JSONObject();
        try {
            o.put("n", name);
            o.put("a", address);
            if (!phone.isEmpty()) o.put("p", phone);
            o.put("lat", lat);
            o.put("lon", lon);
            o.put("t", when);
        } catch (Exception ignored) {}
        return o;
    }

    static Place from(JSONObject o) {
        if (o == null) return null;
        Place p = new Place(o.optString("n", ""), o.optString("a", ""), o.optDouble("lat", Double.NaN), o.optDouble("lon", Double.NaN));
        p.phone = o.optString("p", "");
        p.when = o.optLong("t", 0);
        return p.valid() ? p : null;
    }

    Place copy() {
        Place p = new Place(name, address, lat, lon);
        p.phone = phone;
        p.when = when;
        return p;
    }

    static double meters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0, dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** "1.3 km" / "850 m", or miles/feet when the unit's locale is US or UK. */
    static String distance(double m) {
        if (Double.isNaN(m)) return "";
        String c = Locale.getDefault().getCountry();
        if ("US".equals(c) || "GB".equals(c)) {
            double mi = m / 1609.344;
            return mi >= 0.1 ? String.format(Locale.US, mi >= 10 ? "%.0f mi" : "%.1f mi", mi) : String.format(Locale.US, "%.0f ft", m * 3.28084);
        }
        return m >= 1000 ? String.format(Locale.US, m >= 10000 ? "%.0f km" : "%.1f km", m / 1000) : String.format(Locale.US, "%.0f m", m);
    }

    // "43.6532, -79.3832", "43.6532 -79.3832", "43.6532N 79.3832W"
    private static final Pattern COORDS = Pattern.compile(
            "^\\s*([-+]?\\d{1,2}(?:\\.\\d+)?)\\s*([NnSs])?\\s*[,; ]\\s*([-+]?\\d{1,3}(?:\\.\\d+)?)\\s*([EeWw])?\\s*$");

    /** A typed position, or null when the text isn't one. */
    static Place parseCoords(String q) {
        if (q == null) return null;
        Matcher m = COORDS.matcher(q);
        if (!m.matches()) return null;
        try {
            double la = Double.parseDouble(m.group(1)), lo = Double.parseDouble(m.group(3));
            if (m.group(2) != null && m.group(2).equalsIgnoreCase("S")) la = -Math.abs(la);
            if (m.group(4) != null && m.group(4).equalsIgnoreCase("W")) lo = -Math.abs(lo);
            Place p = new Place("", "", la, lo);
            return p.valid() ? p : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String nz(String s) { return s == null ? "" : s.trim(); }
}
