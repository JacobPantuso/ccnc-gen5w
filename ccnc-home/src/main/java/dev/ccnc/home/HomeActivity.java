package dev.ccnc.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeActivity extends Activity implements CardView.Listener, WelcomePage.Listener {

    private Typeface bold, regular;
    private float dp;
    private AppRepo repo;
    private WeatherClient weather;
    private PhoneInfo phone;
    private WelcomePage welcome;
    private ChromeViews.PageTray tray;
    private int dragChip = -1;     // page chip the dragged card is over, or -1
    private Projection.State proj = new Projection.State(false, false, false);
    private boolean demo;
    private List<AppRepo.Entry> entries = new ArrayList<AppRepo.Entry>();

    private FrameLayout root;
    private ChromeViews.TopBar topBar;
    private Pager pager;
    private LinearLayout strip;
    private ChromeViews.PageIndicator indicator;
    private int builtW = -1, builtH = -1;
    private Object profileWatch;        // follows profile switches for the welcome greeting
    private boolean landing;            // page 0 is the welcome page
    private View profileChip;           // top-left profile chip, hidden on the welcome page
    /** Hidden apps: in edit mode their cards follow the other apps (greyed, crossed-out eye; tap to show again). */
    private List<AppRepo.Entry> hiddenEntries = new ArrayList<AppRepo.Entry>();
    private final List<CardView> hiddenCards = new ArrayList<CardView>();
    private boolean resumed;

    // Edit/drag state.
    private boolean editing, dragging;
    private String dragKey;
    private CardView dragCard;          // the view for dragKey (re-found if the pages are ever rebuilt mid-drag)
    private float grabDX, grabDY;       // finger offset from the dragged card's top-left
    private float lastRawX, lastRawY;   // last finger position: re-pins the card when the page scrolls under a still finger
    private boolean dragNeedsAnchor;    // long-press grab: anchor on the first MOVE
    private boolean orderDirty;         // the order changed during this drag: saved once, on drop
    private int cardSide, cardW, cardStep; // slot geometry: page side margin, card width, cardW + gap
    private int edgeDir;                // -1 / 1 while the finger rests in an edge zone, else 0
    private int aimIndex = -1;          // slot of the card the dragged one is held over the middle of, or -1
    private FrameLayout cardLayer;      // every card, on one wide layer spanning all the card pages
    private final Map<String, CardView> cardByKey = new HashMap<String, CardView>();
    private final int[] loc = new int[2];
    private final Handler handler = new Handler();
    private static final android.view.animation.Interpolator EASE = new android.view.animation.DecelerateInterpolator(1.6f);
    private View editChrome;            // "Done" pill + hint, shown in edit mode
    private FrameLayout.LayoutParams pagerLp;

    private final BroadcastReceiver clock = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            topBar.invalidate();
            if (welcome != null) welcome.invalidate();
            refreshTheme(); // Auto switches between Day and Midnight on the hour.
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reload(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UninstallCleanup.run(this);   // after an uninstall + restart: removes Climate and Home (see the class)
        StartupProfile.run(this);     // default profile / auto-advance on the start-up profile screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        dp = getResources().getDisplayMetrics().density;
        repo = new AppRepo(this);
        String forced = getIntent().getStringExtra("theme"); // preview: am start ... --es theme ocean
        if (forced != null) Theme.save(this, forced);
        Theme.loadAccent(this);
        Theme.current = Theme.resolve(this, Theme.savedId(this));
        applySharedPalette(); // before the app list loads: a theme pack's icons replace the built-in ones
        demo = getIntent().getBooleanExtra("demo", false);
        weather = new WeatherClient(this, new WeatherClient.Listener() {
            @Override public void onWeather(String subtitle) { applyWeather(subtitle); }
        });
        phone = new PhoneInfo(this, new PhoneInfo.Listener() {
            @Override public void onPhoneChanged() { updatePhone(); }
        });
        loadFonts();
        welcome = new WelcomePage(this, bold, regular, this);
        loadSlots();

        root = new FrameLayout(this);
        root.addView(new ChromeViews.Background(this), match());

        topBar = new ChromeViews.TopBar(this, bold, regular);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(64));
        root.addView(topBar, tp);
        // Top left: the active profile (photo + name), as in every ccNC top bar. Tap: ccNC Profile.
        FrameLayout.LayoutParams chip = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(64), Gravity.TOP | Gravity.LEFT);
        chip.leftMargin = px(20);
        // Hidden on the welcome page (its greeting already names the driver); fades in with the clock.
        profileChip = new dev.ccnc.common.ProfileChip(this);
        root.addView(profileChip, chip);

        pager = new Pager(this);
        strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        pager.addView(strip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams pp = match();
        pp.topMargin = px(80);
        pp.bottomMargin = px(60);
        pagerLp = pp;
        root.addView(pager, pp);

        indicator = new ChromeViews.PageIndicator(this);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(20), Gravity.BOTTOM);
        ip.bottomMargin = px(22);
        root.addView(indicator, ip);

        // Edit-mode chrome: hint + Done pill, top right, hidden until long-press.
        TextView done = new TextView(this);
        done.setText("Done");
        done.setTextSize(15f);
        done.setTypeface(bold);
        done.setTextColor(0xFF10161C);
        done.setGravity(Gravity.CENTER);
        int padH = px(20), padV = px(10);
        done.setPadding(padH, padV, padH, padV);
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setCornerRadius(px(20));
        pill.setColor(Theme.current.accent());
        done.setBackground(pill);
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exitEditMode(); }
        });

        TextView hint = new TextView(this);
        hint.setText("Drag to Rearrange");
        hint.setTextSize(14f);
        hint.setTypeface(regular);
        hint.setTextColor(0xA6FFFFFF);

        LinearLayout chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.HORIZONTAL);
        chrome.setGravity(Gravity.CENTER_VERTICAL);
        chrome.addView(hint, new LinearLayout.LayoutParams(-2, -2));
        // Page chips in the middle: tap to go to a page, drop a dragged card on one to move it there.
        chrome.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        tray = new ChromeViews.PageTray(this, bold, new ChromeViews.PageTray.Listener() {
            @Override public void onChip(int page) { pager.toPage(page + (landing ? 1 : 0), true); }
        });
        chrome.addView(tray, new LinearLayout.LayoutParams(-2, -2));
        // Spacer pushes Done to the right edge of the toolbar row.
        chrome.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        // Welcome Page and Reset Layout: the edit-mode toolbar (no long-press menus). Hidden apps show as cards
        // after the other apps while editing, so they need no button.
        final TextView resetBtn = outlinePill("Reset Layout");
        resetBtn.setOnClickListener(new View.OnClickListener() {
            long armed;
            @Override public void onClick(View v) {
                long now = android.os.SystemClock.uptimeMillis();
                if (now - armed > 3000) {       // first tap arms it for 3 s
                    armed = now;
                    resetBtn.setText("Tap Again to Reset");
                    resetBtn.postDelayed(new Runnable() { @Override public void run() { resetBtn.setText("Reset Layout"); } }, 3000);
                    return;
                }
                armed = 0;
                resetBtn.setText("Reset Layout");
                repo.reset();
                reload();
            }
        });
        final TextView welcomeBtn = outlinePill(welcomeEnabled() ? "Hide Welcome" : "Show Welcome");
        welcomeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = !welcomeEnabled();
                getSharedPreferences("home", MODE_PRIVATE).edit().putBoolean("welcome_page", on).apply();
                welcomeBtn.setText(on ? "Hide Welcome" : "Show Welcome");
                builtW = -1;
                buildPages();
                pager.post(new Runnable() { @Override public void run() { pager.toPage(landing ? 1 : 0, false); } });
            }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.rightMargin = px(12);
        chrome.addView(welcomeBtn, bp);
        chrome.addView(resetBtn, new LinearLayout.LayoutParams(bp));
        chrome.addView(done, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        cl.setMargins(px(28), px(76), px(28), 0);
        editChrome = chrome;
        editChrome.setVisibility(View.GONE);
        root.addView(editChrome, cl);

        pager.setListener(new Pager.Listener() {
            @Override public void onPagePosition(float p) {
                indicator.setPosition(p); updateTray(); topBar.setClockAlpha(landing ? p : 1f);
                setChipAlpha(landing ? p : 1f);
                if (dragging) onPagerMovedInDrag();
            }
        });
        pager.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                // Only a new width needs new pages. The height changes whenever edit mode adds or removes its
                // toolbar row, and the cards (MATCH_PARENT) follow that by themselves: rebuilding there threw away
                // the card that had just been picked up.
                if (r - l != builtW) v.post(new Runnable() {
                    @Override public void run() { buildPages(); }
                });
                else builtH = b - t;
            }
        });

        setContentView(root);

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
        // Phone connects / disconnects: swap the projection card.
        IntentFilter mf = new IntentFilter(Projection.ACTION_MODE_AVAILABLE);
        mf.addAction(Projection.ACTION_PP_STATUS);
        registerReceiver(packages, mf);

        reload();
        phone.start();
        handleExtras(getIntent());
    }

    /** From ccNC Settings: {@code --ez themes true} opens the theme picker. */
    private void handleExtras(Intent i) {
        String st = i == null ? null : i.getStringExtra("stack"); // preview: --es stack "frontKey|backKey"
        if (st != null) {
            i.removeExtra("stack");
            List<String[]> l = repo.stacks();
            String[] k = st.split("\\|");
            if (k.length == 2) { l.add(k); repo.saveStacks(l); reload(); }
        }
        if (i != null && i.getBooleanExtra("themes", false)) {
            i.removeExtra("themes");
            root.post(new Runnable() { @Override public void run() { showThemes(); } });
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (splitHider == null) splitHider = new SplitScreenHider(this);
        splitHider.apply();
        startService(new android.content.Intent(this, VolumeOsdService.class));
        if (nowPlaying == null) nowPlaying = new NowPlaying(this, new NowPlaying.Listener() {
            @Override public void onNowPlaying(NowPlaying n) { applyNowPlaying(n); }
        });
        nowPlaying.start();
        resumed = true;
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_LOCALE_CHANGED);
        f.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        registerReceiver(clock, f);
        topBar.invalidate();
        refreshTheme();
        welcome.setDriver(demo ? "Jacob" : driverName());
        refreshSlots();
        if (profileWatch == null) profileWatch = dev.ccnc.common.Profiles.watch(this, new dev.ccnc.common.Profiles.Listener() {
            @Override public void onProfilesChanged() { welcome.setDriver(demo ? "Jacob" : driverName()); }
        });
        weather.start();
        if (getIntent().getBooleanExtra("diag", false)) {
            getIntent().removeExtra("diag");
            weather.logDiagnostics();
            android.util.Log.i(WeatherClient.TAG, "diag: phone carplay=" + phone.name(true) + " other=" + phone.name(false));
            for (AppRepo.Entry e : repo.load(true)) {
                android.util.Log.i(WeatherClient.TAG, "diag: app " + e.component.flattenToShortString()
                        + " label=\"" + e.label + "\" kind=" + e.kind);
            }
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (dragging) onDragEnd(false); // the rest of the gesture may never arrive: put the card down and save
        evHandler.removeCallbacks(evTick);
        if (nowPlaying != null) nowPlaying.stop();
        resumed = false;
        unregisterReceiver(clock);
    }

    @Override protected void onDestroy() {
        evHandler.removeCallbacksAndMessages(null);
        dev.ccnc.common.Profiles.unwatch(this, profileWatch);
        profileWatch = null;
        unregisterReceiver(packages);
        weather.stop();
        phone.stop();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleExtras(intent);
        // Home pressed while already home: leave edit mode and go back to the first page, as ccNC does.
        if (resumed && Intent.ACTION_MAIN.equals(intent.getAction())) {
            if (editing) exitEditMode();
            pager.toPage(0, true);
        }
    }

    private void reload() {
        if (orderDirty) { repo.saveOrder(entries); orderDirty = false; } // mid-drag (package change): keep the order so far
        entries = demo ? AppRepo.demo(this) : repo.load(false);
        repo.applyStacks(entries);
        hiddenEntries = new ArrayList<AppRepo.Entry>();
        if (!demo) {
            java.util.Set<String> shown = new java.util.HashSet<String>();
            for (AppRepo.Entry e : entries) { shown.add(e.key()); if (e.stackBack != null) shown.add(e.stackBack.key()); }
            for (AppRepo.Entry e : repo.load(true)) if (!shown.contains(e.key())) { e.hiddenApp = true; hiddenEntries.add(e); }
        }
        proj = demo ? new Projection.State(true, true, false) : Projection.read(this);
        setWeatherSubtitle(weather.cached());
        updateWelcomeWeather();
        updatePhone();
        builtW = -1;
        buildPages();
    }

    private boolean welcomeEnabled() {
        return getSharedPreferences("home", MODE_PRIVATE).getBoolean("welcome_page", true);
    }

    private void updateWelcomeWeather() {
        String[] d = weather.details();
        if (demo && d[1].isEmpty()) d = new String[]{"Few Clouds", "12\u00b0C", "Seoul"};
        welcome.setWeather(dev.ccnc.common.Art.icon(this, "weather"), d[0], d[1], d[2]);
    }

    /**
     * Connected phone: the CarPlay / Android Auto card shows its name (or "Connected"),
     * and the welcome page's phone widget follows.
     */
    /**
     * Name of the active user profile (UserProfileManager, by reflection) for the greeting; null when
     * the unit has no such service or the profile still has its default name ("Driver 1", "Guest"...).
     * Only the first word is used.
     */
    private String driverName() {
        try {
            Class<?> c = Class.forName("com.hkmc.userprofile.UserProfileManager");
            Object m = c.getConstructor(android.content.Context.class).newInstance(this);
            int active = ((Number) c.getMethod("getUserType").invoke(m)).intValue();
            String n = (String) c.getMethod("getUserName", int.class).invoke(m, active);
            android.util.Log.i("ccncHome", "profile " + active + " name=" + n);
            if (n == null) return null;
            n = n.trim();
            if (n.isEmpty() || n.matches("(?i)(guest|driver|user|profile)\\s*\\d*")) return null;
            int sp = n.indexOf(' ');
            return sp > 0 ? n.substring(0, sp) : n;
        } catch (Throwable t) {
            return null;
        }
    }

    private Iap2NowPlaying cpNowPlaying;
    private SplitScreenHider splitHider;
    private NowPlaying nowPlaying;

    /** The welcome page's phone widget shows what the connected phone is playing. */
    private void applyNowPlaying(NowPlaying n) {
        boolean show = n.valid && n.title != null && n.fromPhone();
        welcome.setNowPlaying(show ? n.title : null, show ? n.artist : null, show ? n.art : null);
    }

    private void updatePhone() {
        if (cpNowPlaying == null) cpNowPlaying = new Iap2NowPlaying(this);
        cpNowPlaying.carPlay(proj.carplay);
        String name;
        if (proj.carplay || proj.androidAuto) {
            boolean cp = proj.carplay;
            name = demo ? "Jacob's iPhone" : phone.name(cp);
            String key = cp ? CARPLAY_KEY : ANDROID_AUTO_KEY;
            welcome.setPhone(Projection.logo(this, key), null, cp ? "Apple CarPlay" : "Android Auto", "", name);
        } else {
            name = phone.name(false);
            if (name != null) {
                welcome.setPhone(null, dev.ccnc.common.Art.icon(this, "phone"), "Bluetooth", "", name);
            } else {
                welcome.setPhone(null, dev.ccnc.common.Art.icon(this, "pp"), "Phone", "", null);
            }
        }
        // Each projection card says who is connected through it: the phone's name, or "Connected".
        for (AppRepo.Entry e : entries) {
            String pkg = e.component.getPackageName();
            boolean isCp = Projection.CARPLAY_PKG.equals(pkg), isAa = Projection.ANDROID_AUTO_PKG.equals(pkg);
            if (!isCp && !isAa) continue;
            boolean on = isCp ? proj.carplay : proj.androidAuto;
            if (on) {
                String n = demo ? "Jacob's iPhone" : phone.name(isCp);
                e.subtitle = n != null ? n : "Connected";
            } else {
                e.subtitle = null;
            }
        }
        forEachCard(new CardOp() {
            @Override public void apply(CardView c) { if (c.entry.kind == Icons.Kind.PROJECTION) c.invalidate(); }
        });
    }

    private static final String CARPLAY_KEY = "com.daudio.app.dipo/.ui.DipoActivity";
    private static final String ANDROID_AUTO_KEY = "com.google.android.projection.sink/.ui.MainActivity";

    // ---- WelcomePage.Listener ----

    // ---- welcome page cards (long-press a card to choose what it shows) ----

    private static final String[] SLOT_DEFAULTS = {"weather", "phone"};

    private void loadSlots() {
        android.content.SharedPreferences p = getSharedPreferences("home", MODE_PRIVATE);
        for (int i = 0; i < 2; i++) welcome.slots[i] = p.getString("welcome_slot" + i, SLOT_DEFAULTS[i]);
        refreshSlots();
    }

    /** App cards' pictures and names, and the EV card's data. */
    private void refreshSlots() {
        boolean ev = false;
        for (int i = 0; i < 2; i++) {
            String k = welcome.slots[i];
            if (k.equals("ev")) ev = true;
            if (k.startsWith("app:")) {
                AppRepo.Entry e = entryFor(k.substring(4));
                if (e != null) welcome.setApp(i, e.art, e.appIcon, e.label);
                else welcome.setApp(i, null, null, "Not Installed");
            }
        }
        evHandler.removeCallbacks(evTick);
        if (ev) evHandler.post(evTick);
    }

    private AppRepo.Entry entryFor(String key) {
        for (AppRepo.Entry e : repo.load(true)) if (e.key().equals(key)) return e;
        return null;
    }

    /** Battery and range for the EV card, read the way ccNC EV does (HEVManager; 0.5 % steps), every 30 s. */
    private final Runnable evTick = new Runnable() { @Override public void run() {
        new Thread(new Runnable() { @Override public void run() {
            int soc = -1, range = -1, unit = -1;
            try {
                Class<?> c = Class.forName("com.hkmc.hev.HEVManager");
                Object m = c.getConstructor(android.content.Context.class).newInstance(getApplicationContext());
                java.lang.reflect.Method g = c.getMethod("getHEVStatus", int.class);
                int raw = ((Number) g.invoke(m, 0x19)).intValue();
                soc = raw >= 0 && raw <= 200 ? Math.round(raw / 2f) : -1;
                int e = ((Number) g.invoke(m, 0x9)).intValue(), t = ((Number) g.invoke(m, 0xb)).intValue();
                range = e >= 0 ? e : t;
                unit = ((Number) g.invoke(m, 0xc)).intValue();
            } catch (Throwable ignored) { }
            final int fs = soc, fr = range;
            final boolean km = unit != 2;
            runOnUiThread(new Runnable() { @Override public void run() { welcome.setEv(fs, fr, km); } });
        } }, "ccncHomeEv").start();
        evHandler.postDelayed(this, 30000);
    } };
    private final Handler evHandler = new Handler();   // its own: drag-and-drop clears `handler`

    @Override public void onSlotTapped(int slot, String kind) {
        if (kind.equals("weather")) onWeatherTapped();
        else if (kind.equals("ev")) {
            Intent i = getPackageManager().getLaunchIntentForPackage("dev.ccnc.ev");
            if (i != null) launch(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "EV");
        } else if (kind.startsWith("app:")) {
            AppRepo.Entry e = entryFor(kind.substring(4));
            if (e != null) onOpen(e);
            else Toast.makeText(this, "That app isn't installed. Long-press the card to choose another.", Toast.LENGTH_SHORT).show();
        } else onPhoneTapped();
    }

    /** ccNC-style chooser: Weather, Phone & Now Playing, EV Battery, or any app. */
    @Override public void onSlotLongPressed(final int slot) {
        dev.ccnc.common.Chrome.beep(this);
        final List<String> keys = new ArrayList<String>();
        final List<String> labels = new ArrayList<String>();
        keys.add("weather"); labels.add("Weather");
        keys.add("phone"); labels.add("Phone & Now Playing");
        keys.add("ev"); labels.add("EV Battery");
        for (AppRepo.Entry e : repo.load(false)) { keys.add("app:" + e.key()); labels.add(e.label); }
        int sel = keys.indexOf(welcome.slots[slot]);
        new dev.ccnc.common.Popup(this, (slot == 0 ? "Left" : "Right") + " Card", "Choose what this card on the welcome page shows.")
                .options(labels.toArray(new String[0]), sel, new dev.ccnc.common.Popup.Pick() {
                    @Override public void onPick(int i) {
                        welcome.slots[slot] = keys.get(i);
                        getSharedPreferences("home", MODE_PRIVATE).edit().putString("welcome_slot" + slot, keys.get(i)).apply();
                        refreshSlots();
                        welcome.invalidate();
                    }
                }).show();
    }

    private void onWeatherTapped() {
        // ccNC Weather when it's installed; the stock telematics weather screen only as a fallback.
        try {
            getPackageManager().getPackageInfo("dev.ccnc.weather", 0);
            startActivity(new Intent().setComponent(new ComponentName("dev.ccnc.weather", "dev.ccnc.weather.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (PackageManager.NameNotFoundException e) {
            sendBroadcast(new Intent(Curated.WEATHER_SHORTCUT));
        }
    }

    private void onPhoneTapped() {
        for (AppRepo.Entry e : entries) {
            String pkg = e.component.getPackageName();
            if ((proj.carplay && Projection.CARPLAY_PKG.equals(pkg))
                    || (proj.androidAuto && Projection.ANDROID_AUTO_PKG.equals(pkg))) {
                onOpen(e);
                return;
            }
        }
        // Playing from the phone over Bluetooth (the card shows the track): open ccNC Media.
        if (nowPlaying != null && nowPlaying.valid && nowPlaying.title != null && nowPlaying.fromPhone() && hasMediaApp()) {
            launch(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("dev.ccnc.media", "dev.ccnc.media.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Media");
            return;
        }
        onGear(null);
    }

    private boolean hasMediaApp() {
        try { getPackageManager().getPackageInfo("dev.ccnc.media", 0); return true; } catch (PackageManager.NameNotFoundException x) { return false; }
    }

    private void setWeatherSubtitle(String subtitle) {
        if (subtitle == null) return;
        for (AppRepo.Entry e : entries) if (e.kind == Icons.Kind.WEATHER) e.subtitle = subtitle;
    }

    private void applyWeather(String subtitle) {
        setWeatherSubtitle(subtitle);
        updateWelcomeWeather();
        for (int p = 0; p < strip.getChildCount(); p++) {
            if (!(strip.getChildAt(p) instanceof ViewGroup)) continue;
            ViewGroup page = (ViewGroup) strip.getChildAt(p);
            for (int i = 0; i < page.getChildCount(); i++) {
                View v = page.getChildAt(i);
                if (v instanceof CardView && ((CardView) v).entry.kind == Icons.Kind.WEATHER) v.invalidate();
            }
        }
    }

    private int cardsPerPage = 4;
    private AppRepo.Entry tapEntry;   // card a touch went down on in edit mode
    private float tapX, tapY;
    private long tapT;

    private void buildPages() {
        int w = pager.getWidth(), h = pager.getHeight();
        if (w == 0 || h == 0) return;
        int keepPage = pager.page();
        builtW = w;
        builtH = h;
        strip.removeAllViews();
        cardByKey.clear();

        int side = px(36), gap = px(14);
        int perPage = Math.round((w - 2f * side + gap) / (190f * dp + gap));
        perPage = Math.max(3, Math.min(7, perPage));
        cardsPerPage = perPage;
        cardSide = side;
        cardW = (w - 2 * side - (perPage - 1) * gap) / perPage;
        cardStep = cardW + gap;
        int cardPages = cardPages();
        landing = welcomeEnabled();
        if (landing) {
            if (welcome.getParent() != null) ((ViewGroup) welcome.getParent()).removeView(welcome);
            strip.addView(welcome, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        int pages = cardPages + (landing ? 1 : 0);

        // One layer for all the card pages (a card's page is only its left margin), so a reorder moves the
        // existing views instead of rebuilding them, and a dragged card isn't clipped at its page's edge.
        cardLayer = new FrameLayout(this);
        cardLayer.setClipChildren(false);
        strip.setClipChildren(false);
        for (int idx = 0; idx < entries.size(); idx++) {
            CardView card = new CardView(this, entries.get(idx), bold, regular, this);
            card.setTag(entries.get(idx).key());
            card.setEditMode(editing);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cardW, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.leftMargin = slotLeft(idx);
            cardLayer.addView(card, lp);
            cardByKey.put(entries.get(idx).key(), card);
        }
        hiddenCards.clear();
        for (int i = 0; i < hiddenEntries.size(); i++) {
            CardView card = new CardView(this, hiddenEntries.get(i), bold, regular, this);
            card.setTag("hidden:" + hiddenEntries.get(i).key());
            card.setEditMode(true);
            card.setVisibility(editing ? View.VISIBLE : View.GONE);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cardW, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.leftMargin = slotLeft(entries.size() + i);
            cardLayer.addView(card, lp);
            hiddenCards.add(card);
        }
        strip.addView(cardLayer, new LinearLayout.LayoutParams(cardPages * w, ViewGroup.LayoutParams.MATCH_PARENT));
        indicator.setCount(pages);
        updateTray();
        topBar.setClockAlpha(landing && pager.page() == 0 ? 0f : 1f);
        setChipAlpha(landing && pager.page() == 0 ? 0f : 1f);
        if (dragKey != null) {
            // Rebuilt under a held card (an app was installed, the screen width changed): pick the new view up.
            dragCard = cardByKey.get(dragKey);
            if (dragCard == null) cancelDrag();
            else { lift(dragCard, false); if (!dragNeedsAnchor) pinDragCard(); }
        }
        final int target = Math.min(keepPage, pages - 1);
        pager.post(new Runnable() {
            @Override public void run() { pager.toPage(target, false); indicator.setPosition(target); }
        });
    }

    /** Left edge of list slot {@code idx} on the card layer. */
    private int slotLeft(int idx) {
        int per = Math.max(1, cardsPerPage);
        return (idx / per) * builtW + cardSide + (idx % per) * cardStep;
    }

    /**
     * Moves every card to the slot its place in {@link #entries} gives it, without rebuilding anything. A card
     * that changes slot is put there at once and slides in from where it was drawn; one that is already sliding
     * carries on from where it is, so quick back-and-forth reorders never jump.
     */
    private void relayoutCards() {
        for (int i = 0; i < entries.size(); i++) {
            CardView c = cardByKey.get(entries.get(i).key());
            if (c == null) continue;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) c.getLayoutParams();
            int left = slotLeft(i), delta = lp.leftMargin - left;
            if (delta == 0) continue;
            lp.leftMargin = left;
            // Moved directly rather than with a layout pass: this also runs from scroll callbacks, where a
            // requested layout would land a frame after the translation below.
            if (c.getWidth() > 0) c.offsetLeftAndRight(left - c.getLeft()); else c.requestLayout();
            if (c == dragCard && dragging) continue; // pinned to the finger below
            c.setTranslationX(c.getTranslationX() + delta);
            c.animate().translationX(0f).setDuration(200).setInterpolator(EASE).start();
        }
        if (dragging && !dragNeedsAnchor) pinDragCard();
    }

    // ---- CardView.Listener ----

    @Override public void onOpen(AppRepo.Entry e) {
        if (e.broadcast != null) {
            sendBroadcast(new Intent(e.broadcast));
            return;
        }
        if (Projection.ANDROID_AUTO_PKG.equals(e.component.getPackageName())) {
            if (!Projection.openAndroidAuto(this)) Toast.makeText(this, "Android Auto can't be opened right now", Toast.LENGTH_SHORT).show();
            return;
        }
        // Bluetooth Audio's screen isn't exported either: the mode manager opens it, as the stock home does.
        if (dev.ccnc.common.CarMedia.BT_AUDIO_PKG.equals(e.component.getPackageName())) {
            int r = dev.ccnc.common.CarMedia.launchMode(this, dev.ccnc.common.CarMedia.MODE_BLUETOOTH);
            if (r == dev.ccnc.common.CarMedia.LAUNCHED) return;
            if (r == dev.ccnc.common.CarMedia.NOT_AVAILABLE) {
                Toast.makeText(this, "Connect a phone over Bluetooth to use Bluetooth Audio", Toast.LENGTH_SHORT).show();
                return;
            }
            // No mode service (not on the car): the plain launch below, which reports its own failure.
        }
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        // The stock media app is replaced by ccNC Media wherever a card points at it.
        ComponentName cn = "com.hkmc.av.app.mediagate".equals(e.component.getPackageName()) && hasMediaApp()
                ? new ComponentName("dev.ccnc.media", "dev.ccnc.media.MainActivity") : e.component;
        i.setComponent(cn);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        launch(i, e.label);
    }

    @Override public void onGear(AppRepo.Entry e) {
        ComponentName ps = ComponentName.unflattenFromString(Curated.PROJECTION_SETTINGS);
        Intent pi = new Intent(Intent.ACTION_MAIN).setComponent(ps).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (getPackageManager().resolveActivity(pi, 0) != null) {
            launch(pi, "Phone projection settings");
            return;
        }
        for (AppRepo.Entry s : entries) {
            if (s.kind == Icons.Kind.SETTINGS && s.component.getPackageName().equals("com.android.settings")) {
                onOpen(s);
                return;
            }
        }
        launch(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Settings");
    }

    @Override public void onEdit(AppRepo.Entry e, CardView v) {
        // Long-press: enter edit mode and immediately grab this card.
        tapEntry = null;
        enterEditMode();
        startDrag(e, null);
        // From here the activity consumes the gesture, so the card would never see its UP and would stay drawn
        // pressed for the whole drag: end the touch for the view tree now.
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    // ---- edit mode & drag-to-rearrange ----

    @Override public void onBackPressed() {
        if (editing) { exitEditMode(); return; }
        pager.toPage(0, true);
    }

    /** Consumes the gesture stream while dragging so the Pager can't steal it. */
    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (dragging) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (tapEntry != null && Math.hypot(ev.getRawX() - tapX, ev.getRawY() - tapY) > px(18)) tapEntry = null;
                    onDragMove(ev.getRawX(), ev.getRawY());
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    tapEntry = null;
                    onDragEnd(ev.getActionMasked() == MotionEvent.ACTION_UP);
                    break;
                default:
                    break;
            }
            return true;
        }
        if (editing && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // Chrome (Done/hint/guide) gets its touches first; it sits above cards.
            if (onChrome(ev.getRawX(), ev.getRawY())) {
                return super.dispatchTouchEvent(ev);
            }
            CardView hit = cardAt(ev.getRawX(), ev.getRawY());
            if (hit != null && hit.entry.hiddenApp) {
                dev.ccnc.common.Chrome.beep(this);
                unhideEntry(hit.entry);
                return true;
            }
            if (hit != null) {
                float lx = ev.getRawX() - screenX(hit), ly = ev.getRawY() - screenY(hit);
                if (hit.inBadge(lx, ly)) {
                    hideEntry(hit.entry);
                } else if (hit.inUngroup(lx, ly)) {
                    unstack(hit.entry);
                } else {
                    tapEntry = hit.entry; tapX = ev.getRawX(); tapY = ev.getRawY(); tapT = ev.getEventTime();
                    startDrag(hit.entry, ev);
                }
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /** True when the point lands on edit chrome (Done/hint) or the guide overlay. */
    private boolean onChrome(float rawX, float rawY) {
        if (editChrome.getVisibility() == View.VISIBLE
                && rawX >= screenX(editChrome) && rawX <= screenX(editChrome) + editChrome.getWidth()
                && rawY >= screenY(editChrome) && rawY <= screenY(editChrome) + editChrome.getHeight()) {
            return true;
        }
        View guide = root.findViewWithTag("edit_guide");
        return guide != null;
    }

    private void updateTray() {
        if (tray == null) return;
        int cardPages = cardPages();
        int cur = pager.page() - (landing ? 1 : 0);
        tray.set(cardPages, cur >= 0 && cur < cardPages ? cur : -1);
    }

    /** Pages of cards: the apps, plus the hidden ones while editing. */
    private int cardPages() {
        int per = Math.max(1, cardsPerPage);
        int n = entries.size() + (editing ? hiddenEntries.size() : 0);
        return Math.max(1, (n + per - 1) / per);
    }

    /**
     * Shows or hides the hidden-app cards and resizes the card pages to match, without rebuilding the other cards
     * (edit mode starts with a long-press that has just picked one of them up).
     */
    private void applyHiddenCards() {
        if (cardLayer == null || builtW <= 0) return;
        for (CardView c : hiddenCards) c.setVisibility(editing ? View.VISIBLE : View.GONE);
        int cardPages = cardPages(), pages = cardPages + (landing ? 1 : 0);
        ViewGroup.LayoutParams lp = cardLayer.getLayoutParams();
        if (lp.width != cardPages * builtW) { lp.width = cardPages * builtW; cardLayer.setLayoutParams(lp); }
        indicator.setCount(pages);
        if (pager.page() > pages - 1) { final int last = pages - 1; pager.post(new Runnable() { @Override public void run() { pager.toPage(last, true); } }); }
        updateTray();
    }

    /** Shows a hidden app again, at the end of the other apps (where its card already is). */
    private void unhideEntry(AppRepo.Entry e) {
        repo.setHidden(e.key(), false);
        e.hiddenApp = false;
        entries.add(e);
        repo.saveOrder(entries);
        orderDirty = false;
        Toast.makeText(this, "Shown: " + e.label, Toast.LENGTH_SHORT).show();
        reload();
    }

    /** Puts the card at the end of the given page of cards (the caller saves the order). */
    private void moveToPage(String key, int page) {
        int from = indexOfKey(key);
        if (from < 0) return;
        int per = Math.max(1, cardsPerPage), n = entries.size();
        int to = Math.max(0, Math.min(Math.min((page + 1) * per, n) - 1, n - 1));
        if (to != from) moveEntry(from, to);
    }

    private void moveEntry(int from, int to) {
        entries.add(to, entries.remove(from));
        orderDirty = true;
        relayoutCards();
    }

    private void enterEditMode() {
        if (editing) return;
        editing = true;
        pager.setPagingEnabled(false);
        // Drop the grid and give edit controls a dedicated toolbar row.
        pagerLp.topMargin = px(132);
        pager.setLayoutParams(pagerLp);
        forEachCard(new CardOp() {
            @Override public void apply(CardView c) { c.setEditMode(true); }
        });
        editChrome.setVisibility(View.VISIBLE);
        applyHiddenCards();
        maybeShowEditGuide();
    }

    private void exitEditMode() {
        editing = false;
        cancelDrag();
        pager.setPagingEnabled(true);
        pagerLp.topMargin = px(80);
        pager.setLayoutParams(pagerLp);
        forEachCard(new CardOp() {
            @Override public void apply(CardView c) {
                c.animate().cancel(); // or a slide still running would keep writing the translation
                c.setEditMode(false);
                c.setLayerType(View.LAYER_TYPE_NONE, null);
                c.setAlpha(1f);
                c.setScaleX(1f); c.setScaleY(1f);
                c.setTranslationX(0f); c.setTranslationY(0f);
            }
        });
        editChrome.setVisibility(View.GONE);
        applyHiddenCards();
        View guide = root.findViewWithTag("edit_guide");
        if (guide != null) root.removeView(guide);
        repo.saveOrder(entries);
        orderDirty = false;
    }

    /** The dragged look: on top, lit, slightly larger. On its own GPU layer so moving it never redraws it. */
    private void lift(CardView card, boolean animate) {
        card.animate().cancel(); // a drop glide still running: grab it where it is
        card.bringToFront();
        card.setPressed(false);
        card.setLifted(true);
        card.setAlpha(1f);
        card.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (animate) card.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).setInterpolator(EASE).start();
        else { card.setScaleX(1.05f); card.setScaleY(1.05f); }
    }

    private void startDrag(AppRepo.Entry e, MotionEvent down) {
        CardView card = cardByKey.get(e.key());
        if (card == null) return;
        dragging = true;
        dragKey = e.key();
        dragCard = card;
        lift(card, true);
        dragNeedsAnchor = down == null;
        if (down != null) anchor(down.getRawX(), down.getRawY());
    }

    /** X of the card layer inside the strip: it follows the welcome page when there is one. */
    private int layerX() { return landing ? builtW : 0; }

    /** Remembers where on the card the finger holds it. */
    private void anchor(float rawX, float rawY) {
        lastRawX = rawX;
        lastRawY = rawY;
        pager.getLocationOnScreen(loc);
        int left = ((FrameLayout.LayoutParams) dragCard.getLayoutParams()).leftMargin;
        grabDX = rawX - loc[0] + pager.getScrollX() - layerX() - (left + dragCard.getTranslationX());
        grabDY = rawY - loc[1] - dragCard.getTranslationY();
        dragNeedsAnchor = false;
    }

    /**
     * Keeps the dragged card under the finger, whatever its slot and the pager's scroll position are. Works
     * from the slot geometry, not from where views happen to be on screen, so it is right in the middle of a
     * reorder or a page flip too. Leaves the pager's screen position in {@link #loc}.
     */
    private void pinDragCard() {
        if (dragCard == null) return;
        pager.getLocationOnScreen(loc);
        int left = ((FrameLayout.LayoutParams) dragCard.getLayoutParams()).leftMargin;
        float limit = pager.getHeight() * 0.2f;
        dragCard.setTranslationX(lastRawX - loc[0] + pager.getScrollX() - layerX() - grabDX - left);
        dragCard.setTranslationY(Math.max(-limit, Math.min(limit, lastRawY - loc[1] - grabDY)));
    }

    private void onDragMove(float rawX, float rawY) {
        if (dragCard == null) return;
        if (dragNeedsAnchor) anchor(rawX, rawY);
        lastRawX = rawX;
        lastRawY = rawY;
        pinDragCard();

        // Over a page chip: show that page and wait for the drop; the list order isn't touched.
        int chip = tray != null && editChrome.getVisibility() == View.VISIBLE ? tray.chipAt(rawX, rawY) : -1;
        if (chip != dragChip) {
            dragChip = chip;
            if (tray != null) tray.setHover(chip);
            // Once per chip: asking again on every move restarts the scroll, and the pager then jumps instead.
            if (chip >= 0) pager.toPage(chip + (landing ? 1 : 0), true);
        }
        if (chip >= 0) {
            clearAim();
            clearPending();
            setEdge(0);
            return;
        }
        float x = rawX - loc[0];
        setEdge(x > pager.getWidth() - px(56) ? 1 : x < px(56) ? -1 : 0);
        if (pagerSettled()) retarget(); // mid-flip the slots are flying past: decided when the page lands
    }

    private boolean pagerSettled() { return builtW > 0 && pager.getScrollX() % builtW == 0; }

    /**
     * Decides what the dragged card is over, going by the card's own centre (what the eye follows) on the page
     * that is showing. Into another slot: that card makes room. Over the middle of a single card: nothing
     * moves, and holding there lights it up as a group target.
     */
    private void retarget() {
        int cur = indexOfKey(dragKey), n = entries.size(), per = Math.max(1, cardsPerPage);
        int page = pager.page() - (landing ? 1 : 0);
        if (cur < 0 || page < 0 || dragCard == null) return;
        int left = ((FrameLayout.LayoutParams) dragCard.getLayoutParams()).leftMargin;
        float xr = left + dragCard.getTranslationX() + cardW / 2f - page * builtW - cardSide;
        int col = (int) Math.floor((xr + (cardStep - cardW) / 2f) / cardStep);
        col = Math.max(0, Math.min(per - 1, col));
        int slot = page * per + col;
        float f = (xr - col * cardStep) / Math.max(1, cardW); // 0..1 across the slot
        if (slot >= n) {
            // The empty space after the last card: go to the end.
            clearAim();
            clearPending();
            if (cur != n - 1) moveEntry(cur, n - 1);
            return;
        }
        // A neighbour only gives way once the card is properly into its slot. Without this dead band a finger
        // resting on the boundary swaps the two back and forth.
        if (slot == cur || (slot == cur + 1 && f < 0.08f) || (slot == cur - 1 && f > 0.92f)) {
            clearAim();
            clearPending();
            return;
        }
        if (entries.get(cur).stackBack == null && entries.get(slot).stackBack == null) {
            if (f >= 0.30f && f <= 0.70f) {
                clearPending();
                if (aimIndex != slot) {
                    clearAim();
                    aimIndex = slot;
                    handler.postDelayed(armGroup, 500); // a timer, not move events: a still finger sends none
                }
                return; // no reordering while aiming at a card
            }
            // On the way in, the card first crosses the near edge of the one it may be heading for the middle
            // of. Making room at once would whisk that card away before it could ever be aimed at, so wait a
            // moment: still on the edge then, it's a reorder. (Past the middle, out the far side: no wait.)
            boolean nearEdge = slot > cur ? f < 0.30f : f > 0.70f;
            if (nearEdge && !(reorderDue && pendingSlot == slot)) {
                clearAim();
                if (pendingSlot != slot) {
                    clearPending();
                    pendingSlot = slot;
                    handler.postDelayed(reorderLater, 160);
                }
                return;
            }
        }
        clearAim();
        clearPending();
        moveEntry(cur, slot);
    }

    private int pendingSlot = -1;       // slot a short dwell on its near edge will reorder into, or -1
    private boolean reorderDue;         // set while that dwell timer re-runs retarget()

    private void clearPending() {
        handler.removeCallbacks(reorderLater);
        pendingSlot = -1;
    }

    private final Runnable reorderLater = new Runnable() {
        @Override public void run() {
            if (!dragging || dragChip >= 0 || !pagerSettled()) { pendingSlot = -1; return; }
            reorderDue = true;
            retarget();
            reorderDue = false;
        }
    };

    private final Runnable armGroup = new Runnable() {
        @Override public void run() {
            if (!dragging || aimIndex < 0 || aimIndex >= entries.size()) return;
            CardView c = cardByKey.get(entries.get(aimIndex).key());
            if (c == null) return;
            groupTarget = c;
            c.setStackTarget(true);
        }
    };

    /** The page moved under the finger (edge flip, page chip): the card stays with the finger, not the page. */
    /** The profile chip follows the clock: gone on the welcome page, so it can't be tapped there either. */
    private void setChipAlpha(float a) {
        if (profileChip == null) return;
        a = Math.max(0f, Math.min(1f, a));
        profileChip.setAlpha(a);
        profileChip.setVisibility(a <= 0.01f ? View.INVISIBLE : View.VISIBLE);
    }

    private void onPagerMovedInDrag() {
        if (dragCard == null || dragNeedsAnchor) return;
        pinDragCard();
        if (pagerSettled()) {
            // Landed: slot the card into the page that arrived. Posted, as this runs inside the scroll's draw pass.
            handler.removeCallbacks(retargetLater);
            handler.post(retargetLater);
        }
    }

    private final Runnable retargetLater = new Runnable() {
        @Override public void run() { if (dragging && dragChip < 0 && pagerSettled()) retarget(); }
    };

    /** @param drop false when the gesture was cancelled: the card goes to its slot, nothing is grouped or moved. */
    private void onDragEnd(boolean drop) {
        final CardView card = dragCard;
        String droppedKey = dragKey;
        AppRepo.Entry groupWith = drop && groupTarget != null ? groupTarget.entry : null;
        int aim = drop && groupTarget == null ? aimIndex : -1;
        int chip = drop ? dragChip : -1;
        cancelDrag();
        if (droppedKey == null) return;
        if (chip >= 0) moveToPage(droppedKey, chip);
        else if (aim >= 0 && aim < entries.size()) {
            // Let go over a card before it lit up as a group target: that was a move to its slot.
            int cur = indexOfKey(droppedKey);
            if (cur >= 0 && cur != aim) moveEntry(cur, aim);
        }
        if (orderDirty) { repo.saveOrder(entries); orderDirty = false; } // once per drag, not once per swap
        if (groupWith != null) { group(groupWith, droppedKey); return; } // rebuilds the pages
        if (card == null) return;
        // Glide from the finger into the slot. The card stays lit and on top until it lands, then becomes a
        // normal edit-mode card again (dimmed, with its edit outline).
        card.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(EASE)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        card.setLifted(false);
                        card.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                }).start();
    }

    /** Forgets the drag: timers, hover states, flags. Leaves the card's look to the caller. */
    private void cancelDrag() {
        handler.removeCallbacks(edgeFlip);
        handler.removeCallbacks(retargetLater);
        clearAim();
        clearPending();
        edgeDir = 0;
        dragChip = -1;
        if (tray != null) tray.setHover(-1);
        dragging = false;
        dragNeedsAnchor = false;
        dragKey = null;
        dragCard = null;
    }

    /**
     * Resting near a screen edge flips to the next/previous page. The timer starts when the finger enters the
     * zone and is left alone while it stays there: restarting it on every move meant it never fired under a
     * real (always slightly moving) finger.
     */
    private void setEdge(int dir) {
        if (dir == edgeDir) return;
        edgeDir = dir;
        handler.removeCallbacks(edgeFlip);
        if (dir != 0 && pager.pageCount() > 1) handler.postDelayed(edgeFlip, 450);
    }

    private final Runnable edgeFlip = new Runnable() {
        @Override public void run() {
            if (!dragging || edgeDir == 0) return;
            int p = pager.page() + edgeDir, first = landing ? 1 : 0; // never onto the welcome page
            if (p >= first && p < pager.pageCount()) pager.toPage(p, true);
            handler.postDelayed(this, 800);
        }
    };

    /** First time edit mode runs: ccNC-style full-screen guide (see pop_bg_guide_edit_home). */
    private void maybeShowEditGuide() {
        final android.content.SharedPreferences prefs = getSharedPreferences("home", MODE_PRIVATE);
        if (prefs.getBoolean("edit_guide_seen", false)) return;
        prefs.edit().putBoolean("edit_guide_seen", true).apply();

        boolean light = Theme.current.light;
        final FrameLayout overlay = new FrameLayout(this);
        View scrim = new View(this);
        scrim.setBackgroundColor(light ? 0xB3FFFFFF : 0xB3000000);
        overlay.addView(scrim, match());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Edit Home");
        title.setTextSize(26f);
        title.setTypeface(bold);
        title.setTextColor(light ? 0xFF16212A : 0xFFFFFFFF);
        panel.addView(title);

        TextView body = new TextView(this);
        body.setText("Hold and drag an app to rearrange it. Drag it to the screen edge, or onto the page markers below, to move it to another page.\nTap \u2212 on an app to hide it; hidden apps wait after the others, tap one to show it again.");
        body.setTextSize(17f);
        body.setTypeface(regular);
        body.setTextColor(light ? 0xFF5B6873 : 0xFFB4BEC6);
        body.setLineSpacing(px(6), 1f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.topMargin = px(14);
        panel.addView(body, bl);

        TextView ok = new TextView(this);
        ok.setText("Got it");
        ok.setTextSize(16f);
        ok.setTypeface(bold);
        ok.setTextColor(0xFF10161C);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(px(28), px(12), px(28), px(12));
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setCornerRadius(px(24));
        pill.setColor(Theme.current.accent());
        ok.setBackground(pill);
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, -2);
        ol.topMargin = px(28);
        panel.addView(ok, ol);

        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        overlay.addView(panel, pl);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { root.removeView(overlay); }
        });
        overlay.setTag("edit_guide");
        root.addView(overlay, match());
    }

    private void hideEntry(AppRepo.Entry e) {
        repo.setHidden(e.key(), true);
        entries.remove(e);
        repo.saveOrder(entries);
        if (dragging) cancelDrag();
        Toast.makeText(this, "Hidden: " + e.label, Toast.LENGTH_SHORT).show();
        if (entries.isEmpty()) exitEditMode();
        reload(); // its card moves to the hidden apps after the others
    }

    // ---- card lookup helpers ----

    private interface CardOp { void apply(CardView c); }

    private void forEachCard(CardOp op) {
        if (cardLayer == null) return;
        for (int i = 0; i < cardLayer.getChildCount(); i++) {
            View v = cardLayer.getChildAt(i);
            if (v instanceof CardView) op.apply((CardView) v);
        }
    }

    /** Topmost card containing the raw screen point, or null. */
    private CardView cardAt(float rawX, float rawY) {
        CardView found = null;
        if (cardLayer == null) return null;
        for (int i = 0; i < cardLayer.getChildCount(); i++) {
            View v = cardLayer.getChildAt(i);
            if (!(v instanceof CardView) || v.getVisibility() != View.VISIBLE) continue;
            float l = screenX(v), t = screenY(v);
            if (rawX >= l && rawX <= l + v.getWidth() && rawY >= t && rawY <= t + v.getHeight()) found = (CardView) v;
        }
        return found;
    }

    private static float screenX(View v) {
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        return l[0];
    }

    private static float screenY(View v) {
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        return l[1];
    }

    private int indexOfKey(String key) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).key().equals(key)) return i;
        return -1;
    }


    /** Edit-mode button next to Done: same size, outlined instead of filled. */
    private TextView outlinePill(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(15f);
        b.setTypeface(bold);
        b.setTextColor(0xFFFFFFFF);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(20), px(10), px(20), px(10));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(px(20));
        g.setColor(0x33FFFFFF);
        g.setStroke(Math.max(1, px(1)), 0x59FFFFFF);
        b.setBackground(g);
        return b;
    }

    // ---- stacks: two half-height cards sharing one slot ----

    private CardView groupTarget;       // lit: dropping the dragged card here groups the two

    private void clearAim() {
        handler.removeCallbacks(armGroup);
        aimIndex = -1;
        if (groupTarget != null) groupTarget.setStackTarget(false);
        groupTarget = null;
    }

    /** Groups the dropped card under the one it was held over (the target stays on top). */
    private void group(AppRepo.Entry target, String droppedKey) {
        List<String[]> st = repo.stacks();
        st.add(new String[]{target.key(), droppedKey});
        repo.saveStacks(st);
        reload();
    }

    private void unstack(AppRepo.Entry e) {
        List<String[]> st = repo.stacks();
        for (int i = st.size() - 1; i >= 0; i--) if (st.get(i)[0].equals(e.key()) || st.get(i)[1].equals(e.key())) st.remove(i);
        repo.saveStacks(st);
        reload();
    }

    private String labelOf(String key) {
        for (AppRepo.Entry e : repo.load(true)) if (e.key().equals(key)) return e.label;
        return key;
    }

    private void showThemes() {
        java.util.List<Theme> all = Theme.all(this); // built-in, then installed theme packs
        if (dev.ccnc.common.Fonts.kia()) all.remove(Theme.HYUNDAI); // brand theme
        final String[] ids = new String[all.size() + 1];
        String[] names = new String[ids.length];
        ids[0] = Theme.AUTO;
        names[0] = "Auto (Day 7:00–19:00, ccNC Nebula at night)";
        for (int i = 0; i < all.size(); i++) {
            ids[i + 1] = all.get(i).id;
            names[i + 1] = all.get(i).name;
        }
        int checked = java.util.Arrays.asList(ids).indexOf(Theme.savedId(this));
        new AlertDialog.Builder(this, Theme.current.light ? AlertDialog.THEME_HOLO_LIGHT : AlertDialog.THEME_HOLO_DARK)
                .setTitle("Theme")
                .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Theme.save(HomeActivity.this, ids[which]);
                        refreshTheme();
                        d.dismiss();
                    }
                }).show();
    }

    /** Re-reads the saved theme and redraws everything if it changed. */
    private static final int R_ACCENT = 0x7f0f0001; // view tag key (any id with the app package prefix)

    private void refreshTheme() {
        Theme.loadAccent(this); // Settings may have changed it (ThemeProvider)
        Theme t = Theme.resolve(this, Theme.savedId(this));
        String iconsBefore = dev.ccnc.common.Palette.ICONS;
        applySharedPalette();
        String iconsNow = dev.ccnc.common.Palette.ICONS;
        if (!entries.isEmpty() && (iconsBefore == null ? iconsNow != null : !iconsBefore.equals(iconsNow))) {
            reload(); // the cards' icons come from the new theme pack (or back to the built-in set)
        }
        if (t == Theme.current && root.getTag() == t && root.getTag(R_ACCENT) != null && (Integer) root.getTag(R_ACCENT) == Theme.accentOverride) return;
        root.setTag(R_ACCENT, Theme.accentOverride);
        Theme.current = t;
        root.setTag(t);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(t.bg[0]));
        invalidateAll(root);
    }

    /** Home draws its cards from Theme, but the shared Palette (theme pack icons and wallpaper, Chrome) follows too. */
    private void applySharedPalette() {
        String sid = Theme.savedId(this);
        dev.ccnc.common.Themes.set(Theme.AUTO.equals(sid) ? dev.ccnc.common.Themes.resolve(sid)
                : dev.ccnc.common.Themes.find(this, sid), Theme.accentOverride);
    }

    private static void invalidateAll(View v) {
        v.invalidate();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) invalidateAll(g.getChildAt(i));
        }
    }

    private void launch(Intent i, String what) {
        try {
            startActivity(i);
            overridePendingTransition(0, 0);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, what + " is not available", Toast.LENGTH_SHORT).show();
        } catch (SecurityException ex) {
            Toast.makeText(this, what + " can't be opened from here", Toast.LENGTH_SHORT).show();
        }
    }

    // ---- helpers ----

    private void loadFonts() {
        // ccNC's own typeface (Hyundai Sans / Kia Signature, assets), else the unit's Mobis fonts.
        bold = dev.ccnc.common.Fonts.get(this, dev.ccnc.common.Fonts.BOLD);
        regular = dev.ccnc.common.Fonts.get(this, dev.ccnc.common.Fonts.REGULAR);
        if (!hasAsset("fonts/" + dev.ccnc.common.Fonts.brand(this) + "/sans_bold.ttf")) {
            bold = font("/system/fonts/Mobis-Bold.ttf", bold);
            regular = font("/system/fonts/Mobis-Regular.ttf", regular);
        }
    }

    private boolean hasAsset(String path) {
        try {
            getAssets().open(path).close();
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static Typeface font(String path, Typeface fallback) {
        try {
            if (new File(path).canRead()) return Typeface.createFromFile(path);
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    private int dialogTheme() {
        return Theme.current.light ? AlertDialog.THEME_HOLO_LIGHT : AlertDialog.THEME_HOLO_DARK;
    }

    private int px(float d) { return Math.round(d * dp); }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
