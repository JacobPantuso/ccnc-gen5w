package dev.ccnc.home;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Card names, icons and order for the stock Gen5W apps (from the launchable-activity
 * list of a CEEV.CAN Ioniq 6, firmware KVT49L). Apps not listed here still get a card,
 * after these, with their own label.
 */
final class Curated {
    private Curated() {}

    static final class Card {
        final String label;
        final Icons.Kind kind;
        final int order;
        /** ccNC icon name (assets/ccnc/<brand>/icons), or null for the app's own icon / line art. */
        final String icon;
        Card(String label, Icons.Kind kind, String icon, int order) {
            this.label = label; this.kind = kind; this.icon = icon; this.order = order;
        }
    }

    /** Weather: opened by broadcast (see AppRepo.addWeather); this key places and hides its card. */
    static final String WEATHER_PKG = "com.hkmc.telematics.Weather";
    static final String WEATHER_KEY = "com.hkmc.telematics.Weather/.ui.WeatherActivity";
    static final String WEATHER_SHORTCUT = "com.mobis.intent.action.WEATHER_SHORTCUT";

    /** NEMESIS Manager (the package manager the ccNC apps install through). */
    static final String NEMESIS_KEY = "com.nemesis.manager/.MainActivity";

    /** Phone projection settings: the gear on the Android Auto / CarPlay cards. */
    static final String PROJECTION_SETTINGS = "com.daudio.app.phoneprojection/.settings.ui.DeviceConnectionSettings";

    private static final Object[][] CARDS = {
            {PROJECTION_SETTINGS, "Phone Projection", Icons.Kind.PROJECTION, "pp"},
            {"com.google.android.projection.sink/.ui.MainActivity", "Android Auto", Icons.Kind.PROJECTION, null},
            {"com.daudio.app.dipo/.ui.DipoActivity", "Apple CarPlay", Icons.Kind.PROJECTION, null},
            {"dev.ccnc.phone/.MainActivity", "Phone", Icons.Kind.PHONE, "phone"},
            {"com.hkmc.bluetooth/com.android.contacts.DialtactsActivity", "Phone (stock)", Icons.Kind.PHONE, "phone"},
            {"dev.ccnc.voicememo/.MainActivity", "Voice Memo", Icons.Kind.VOICE_MEMO, "voicememo"},
            {"com.daudio.app.voicememo/.MainActivity", "Voice Memo (stock)", Icons.Kind.VOICE_MEMO, "voicememo"},
            {"dev.ccnc.weather/.MainActivity", "Weather", Icons.Kind.WEATHER, "weather"},
            {WEATHER_KEY, "Weather", Icons.Kind.WEATHER, "weather"},
            {"dev.ccnc.settings/.MainActivity", "Setup", Icons.Kind.SETTINGS, "setup"},
            {"dev.ccnc.navi/.MainActivity", "Navigation", Icons.Kind.NAVIGATION, "navi"},
            {"com.mnsoft.navi/.NaviApp", "Navigation (stock)", Icons.Kind.NAVIGATION, "navi"},
            {"dev.ccnc.radio/.MainActivity", "Radio", Icons.Kind.RADIO, "media_radio"},
            {"com.hkmc.av.app.radiona/.RadioMainActivity", "Radio (stock)", Icons.Kind.RADIO, "media_radio"},
            {"com.hkmc.av.app.mediagate/.MediaGateActivity", "Media", Icons.Kind.MEDIA, "media"},
            {"dev.ccnc.media/.MainActivity", "Media", Icons.Kind.MEDIA, "media"},
            {"com.daudio.av.app.btaudio.ui/.BtMusicMainActivity", "Bluetooth Audio", Icons.Kind.MEDIA, "bt_audio"},
            {"com.daudio.av.app.usbmusic/.ui.MusicActivity", "USB Music", Icons.Kind.MEDIA, "usb_music"},
            {"dev.ccnc.climate/.MainActivity", "Climate", Icons.Kind.CLIMATE, "climate"},
            {"com.hkmc.telematics.service.apps.climate/.activities.UIClimate", "Climate (stock)", Icons.Kind.CLIMATE, "climate"},
            {"dev.ccnc.ev/.MainActivity", "EV", Icons.Kind.EV, "ev"},
            {"com.hkmc.daudio.app.ev3/.MainActivity", "EV (stock)", Icons.Kind.EV, "ev"},
            {"com.hkmc.telematics.apps.Vrm.main/.BluelinkUvoMenu", "Bluelink", Icons.Kind.APP, "bluelink"},
            {"dev.ccnc.profile/.MainActivity", "Profile", Icons.Kind.PROFILE, "userprofile"},
            {"com.hkmc.system.app.userprofile/.UserProfileMainActivity", "Profile (stock)", Icons.Kind.PROFILE, "userprofile"},
            {"com.hkmc.av.app.naturesound/.ui.NatureSoundActivity", "Sounds of Nature", Icons.Kind.MEDIA, "media_soundofnature"},
            {"com.hkmc.av.app.hddata/.HDDataMainActivity", "HD Radio Data", Icons.Kind.RADIO, "radio"},
            {"com.SoundHound/.page.SoundHoundActivity", "SoundHound", Icons.Kind.MEDIA, "voicerecognition"},
            {"com.android.rear_care/.RearCareModeMainActivity", "Rear Seat", Icons.Kind.PROFILE, "vehicle"},
            {"com.android.vehiclesettings/.VehicleSetting", "Vehicle Settings", Icons.Kind.SETTINGS, "set_vehicle"},
            {NEMESIS_KEY, "NEMESIS", Icons.Kind.SETTINGS, "customization_frs"},
            // hidden by default (see DEFAULT_HIDDEN), named for the "Show hidden apps" list
            {"com.android.btsettings/com.android.settings2.bluetooth.BluetoothSettings", "Bluetooth Settings", Icons.Kind.SETTINGS, "set_bluetooth"},
            {"com.hkmc.wifisettings/.settings.ui.NetworkConnectionSettings", "Wi-Fi Settings", Icons.Kind.SETTINGS, "set_wifi"},
            {"com.android.displaysettings/.DisplaySetting", "Display Settings", Icons.Kind.SETTINGS, "set_display"},
            {"com.android.soundsettings/.SoundSetting", "Sound Settings", Icons.Kind.SETTINGS, "set_sound"},
            {"com.android.generalsettings/.GeneralSettings", "General Settings", Icons.Kind.SETTINGS, "set_general"},
            {"com.android.themesettings/.MainActivity", "Screen Theme", Icons.Kind.SETTINGS, "set_themelayout"},
            {"com.android.buttonsettings/.ButtonSettings", "Button Settings", Icons.Kind.SETTINGS, "set_button"},
            {"com.hkmc.telematics.apps.Vrm.main/com.hkmc.telematics.apps.Vrm.settings.ui.BluelinkUvoSettingsMenu", "Bluelink Settings", Icons.Kind.SETTINGS, "set_bluelink"},
            {"com.hkmc.system.app.homesetting/.MainListActivity", "Edit Home Icons", Icons.Kind.SETTINGS, "customization_frs"},
            {"com.hkmc.telematics.apps.Vrm.main/com.hkmc.telematics.apps.Vrm.vehicleInfo.ui.MaintenanceWeb", "Maintenance", Icons.Kind.APP, "manual"},
            {"com.android.voicedialer/.VoiceDialerActivity", "Voice Dialer", Icons.Kind.PHONE, "voicerecognition"},
            {"com.android.development/.Development", "Dev Tools", Icons.Kind.APP, "general"},
    };

    /**
     * Stock apps that a ccNC app replaces: hidden while that ccNC app is installed, unless the owner shows them
     * again from "Show hidden apps" (AppRepo.hidden). Stock key -> ccNC package.
     */
    static final Map<String, String> REPLACED = new HashMap<String, String>();
    static {
        String[][] r = {
                {"com.hkmc.bluetooth/com.android.contacts.DialtactsActivity", "dev.ccnc.phone"},
                {"com.daudio.app.voicememo/.MainActivity", "dev.ccnc.voicememo"},
                {"com.mnsoft.navi/.NaviApp", "dev.ccnc.navi"},
                {"com.hkmc.av.app.radiona/.RadioMainActivity", "dev.ccnc.radio"},
                {"com.hkmc.av.app.mediagate/.MediaGateActivity", "dev.ccnc.media"},
                {"com.hkmc.telematics.service.apps.climate/.activities.UIClimate", "dev.ccnc.climate"},
                {"com.hkmc.daudio.app.ev3/.MainActivity", "dev.ccnc.ev"},
                {"com.hkmc.system.app.userprofile/.UserProfileMainActivity", "dev.ccnc.profile"},
                {"com.android.settings/.Settings", "dev.ccnc.settings"},
        };
        for (String[] x : r) REPLACED.put(x[0], x[1]);
    }

    /** Shown in "Show hidden apps" but not on the pages until the owner unhides them. */
    static final Set<String> DEFAULT_HIDDEN = new HashSet<String>(Arrays.asList(
            // Stock settings apps replaced by the ccnc-settings hub.
            "com.android.settings/.Settings",
            "com.android.vehiclesettings/.VehicleSetting",
            "com.android.rear_care/.RearCareModeMainActivity",
            // Stock media app replaced by the ccNC panel (transport works). The other stock apps
            // ccNC replaces are hidden through REPLACED while their ccNC app is installed.
            "com.hkmc.av.app.mediagate/.MediaGateActivity",
            "com.android.btsettings/com.android.settings2.bluetooth.BluetoothSettings",
            "com.hkmc.wifisettings/.settings.ui.NetworkConnectionSettings",
            "com.android.displaysettings/.DisplaySetting",
            "com.android.soundsettings/.SoundSetting",
            "com.android.generalsettings/.GeneralSettings",
            "com.android.themesettings/.MainActivity",
            "com.android.buttonsettings/.ButtonSettings",
            "com.hkmc.telematics.apps.Vrm.main/com.hkmc.telematics.apps.Vrm.settings.ui.BluelinkUvoSettingsMenu",
            "com.hkmc.system.app.homesetting/.MainListActivity",
            "com.hkmc.telematics.apps.Vrm.main/com.hkmc.telematics.apps.Vrm.vehicleInfo.ui.MaintenanceWeb",
            "com.android.voicedialer/.VoiceDialerActivity",
            "com.android.development/.Development"));

    /** Popups, sub-screens and helpers that are launchable but aren't apps: never a card. */
    static final Set<String> SKIP = new HashSet<String>(Arrays.asList(
            PROJECTION_SETTINGS,
            "com.daudio.app.voicememo/.StorageFullPopUpActivity",
            "com.daudio.app.voicememo/.VoiceMemoActivityPopup",
            "com.hkmc.telematics.apps.Vrm.main/com.hkmc.telematics.apps.Vrm.settings.ui.SettingMenuUVO",
            "com.android.generalsettings/.LocalePickerCheckActivity",
            "com.android.generalsettings/.deviceinfo.StorageMemory",
            "com.android.displaysettings/.WaitDisplay",
            "com.android.themesettings/.WaitDisplay",
            "com.hkmc.av.app.hddata/.DataServicesPopUpActivity",
            "com.hkmc.av.app.hddata/.DopplerRadarActivity",
            "com.hkmc.telematics.service.apps.climate/.activities.InteriorClimate",
            "com.hkmc.telematics.service.apps.climate/.activities.InteriorClimateNew",
            "com.hkmc.telematics.service.apps.climate/.activities.SWRCFrontSeatHeatVentControl",
            "com.hkmc.telematics.service.apps.climate/.activities.RearSeatHeatVentControl",
            "com.hkmc.telematics.service.apps.climate/.activities.DialogActivity",
            "com.hkmc.telematics.service.apps.climate/.activities.NewClimateControl",
            "com.hkmc.telematics.service.apps.climate/.activities.SeatHeatVentPopup",
            "com.hkmc.daudio.app.ev3/.drivemode.DriveModeVCUCheckActivity",
            "com.mobis.system/.MicomSystemPopup",
            "com.mobis.voicehandler/.MainActivity",
            "com.hkmc.mediazen.siri/.MainActivity"));

    private static final Map<String, Card> BY_KEY = new HashMap<String, Card>();
    static {
        for (int i = 0; i < CARDS.length; i++) {
            String label = (String) CARDS[i][1];
            // Kia's name for the connected-car service (the same stock app, com.hkmc.telematics.apps.Vrm.main).
            if (dev.ccnc.common.Fonts.kia() && label.startsWith("Bluelink")) label = "Kia Connect" + label.substring(8);
            BY_KEY.put((String) CARDS[i][0], new Card(label, (Icons.Kind) CARDS[i][2], (String) CARDS[i][3], i));
        }
    }

    static Card get(String key) { return BY_KEY.get(key); }

    /** ccNC icon for an app that isn't curated, by its guessed kind; null keeps the app's own icon. */
    static String iconFor(Icons.Kind k) {
        switch (k) {
            case PHONE: return "phone";
            case VOICE_MEMO: return "voicememo";
            case WEATHER: return "weather";
            case SETTINGS: return "setup";
            case NAVIGATION: return "navi";
            case RADIO: return "media_radio";
            case MEDIA: return "media";
            case CLIMATE: return "climate";
            case EV: return "ev";
            case PROFILE: return "userprofile";
            default: return null;
        }
    }

    static List<Object[]> all() { return Arrays.asList(CARDS); }
}
