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
    private boolean landing;            // page 0 is the welcome page
    private boolean resumed;

    // Edit/drag state.
    private boolean editing, dragging;
    private String dragKey;
    private float grabDX, grabDY;       // finger offset from the dragged card's top-left
    private float dragTransX, dragTransY; // live translation of the dragged card
    private boolean dragNeedsAnchor;    // long-press grab: anchor on the first MOVE
    private Map<String, Integer> pendingAnim; // key -> pre-rebuild index, for shuffle animation
    private int cardGap, cardStep;      // cardW + gap, for index math and animation
    private final Handler handler = new Handler();
    private Runnable edgeScroll;
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

        root = new FrameLayout(this);
        root.addView(new ChromeViews.Background(this), match());

        topBar = new ChromeViews.TopBar(this, bold, regular);
        topBar.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showHomeMenu(); return true; }
        });
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(64));
        root.addView(topBar, tp);

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
        hint.setText("Drag to rearrange");
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
        chrome.addView(done, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        cl.setMargins(px(28), px(76), px(28), 0);
        editChrome = chrome;
        editChrome.setVisibility(View.GONE);
        root.addView(editChrome, cl);

        pager.setListener(new Pager.Listener() {
            @Override public void onPagePosition(float p) { indicator.setPosition(p); updateTray(); topBar.setClockAlpha(landing ? p : 1f); }
        });
        pager.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != builtW || b - t != builtH) v.post(new Runnable() {
                    @Override public void run() { buildPages(); }
                });
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
        if (nowPlaying != null) nowPlaying.stop();
        resumed = false;
        unregisterReceiver(clock);
    }

    @Override protected void onDestroy() {
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
        entries = demo ? AppRepo.demo(this) : repo.load(false);
        repo.applyStacks(entries);
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

    @Override public void onWeatherTapped() {
        // ccNC Weather when it's installed; the stock telematics weather screen only as a fallback.
        try {
            getPackageManager().getPackageInfo("dev.ccnc.weather", 0);
            startActivity(new Intent().setComponent(new ComponentName("dev.ccnc.weather", "dev.ccnc.weather.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (PackageManager.NameNotFoundException e) {
            sendBroadcast(new Intent(Curated.WEATHER_SHORTCUT));
        }
    }

    @Override public void onPhoneTapped() {
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

        int side = px(36), gap = px(14);
        int perPage = Math.round((w - 2f * side + gap) / (190f * dp + gap));
        perPage = Math.max(3, Math.min(7, perPage));
        cardsPerPage = perPage;
        int cardW = (w - 2 * side - (perPage - 1) * gap) / perPage;
        cardStep = cardW + gap;
        int cardPages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        landing = welcomeEnabled();
        if (landing) {
            if (welcome.getParent() != null) ((ViewGroup) welcome.getParent()).removeView(welcome);
            strip.addView(welcome, new LinearLayout.LayoutParams(w, h));
        }
        int pages = cardPages + (landing ? 1 : 0);

        for (int p = 0; p < cardPages; p++) {
            FrameLayout page = new FrameLayout(this);
            for (int i = 0; i < perPage; i++) {
                int idx = p * perPage + i;
                if (idx >= entries.size()) break;
                CardView card = new CardView(this, entries.get(idx), bold, regular, this);
                card.setTag(entries.get(idx).key());
                if (editing && !entries.get(idx).key().equals(dragKey)) {
                    card.setEditMode(true);
                }
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cardW, h);
                lp.leftMargin = side + i * (cardW + gap);
                page.addView(card, lp);
            }
            strip.addView(page, new LinearLayout.LayoutParams(w, h));
        }
        indicator.setCount(pages);
        updateTray();
        topBar.setClockAlpha(landing && pager.page() == 0 ? 0f : 1f);
        animateShuffleIn();
        final int target = Math.min(keepPage, pages - 1);
        pager.post(new Runnable() {
            @Override public void run() { pager.toPage(target, false); indicator.setPosition(target); }
        });
    }

    /**
     * After a mid-drag rebuild, slides cards from their old positions to the new ones
     * and pins the dragged card to the finger. {@link #pendingAnim} maps card keys to
     * their pre-rebuild indices; null means "just build".
     */
    private void animateShuffleIn() {
        if (pendingAnim == null && dragKey == null) return;
        for (int p = 0; p < strip.getChildCount(); p++) {
            if (!(strip.getChildAt(p) instanceof ViewGroup)) continue;
            ViewGroup page = (ViewGroup) strip.getChildAt(p);
            for (int i = 0; i < page.getChildCount(); i++) {
                View v = page.getChildAt(i);
                if (!(v instanceof CardView)) continue;
                CardView card = (CardView) v;
                String key = (String) card.getTag();
                if (key == null) continue;
                if (key.equals(dragKey)) {
                    card.bringToFront();
                    card.setEditMode(editing);
                    card.setLifted(true);
                    card.setScaleX(1.05f);
                    card.setScaleY(1.05f);
                    card.setAlpha(1f);
                    card.setTranslationX(dragTransX);
                    card.setTranslationY(dragTransY);
                    continue;
                }
                if (pendingAnim == null) continue;
                Integer old = pendingAnim.get(key);
                if (old == null) continue;
                int now = indexOfKey(key);
                if (now < 0 || now == old) continue;
                card.setTranslationX((old - now) * cardStep);
                card.animate().translationX(0f).setDuration(180).start();
            }
        }
        pendingAnim = null;
    }

    // ---- CardView.Listener ----

    @Override public void onOpen(AppRepo.Entry e) {
        if (e.broadcast != null) {
            sendBroadcast(new Intent(e.broadcast));
            return;
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
                    onDragMove(ev);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    tapEntry = null;
                    onDragEnd();
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
        int per = Math.max(1, cardsPerPage);
        int cardPages = Math.max(1, (entries.size() + per - 1) / per);
        int cur = pager.page() - (landing ? 1 : 0);
        tray.set(cardPages, cur >= 0 && cur < cardPages ? cur : -1);
    }

    /** Puts the card at the end of the given page of cards. */
    private void moveToPage(String key, int page) {
        int from = indexOfKey(key);
        if (from < 0) return;
        int per = Math.max(1, cardsPerPage), n = entries.size();
        AppRepo.Entry moved = entries.remove(from);
        int to = Math.min((page + 1) * per, n) - 1;
        entries.add(Math.max(0, Math.min(to, entries.size())), moved);
        repo.saveOrder(entries);
        builtW = -1;
        buildPages();
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
        maybeShowEditGuide();
    }

    private void exitEditMode() {
        editing = false;
        stopDragVisuals();
        handler.removeCallbacksAndMessages(null);
        pager.setPagingEnabled(true);
        pagerLp.topMargin = px(80);
        pager.setLayoutParams(pagerLp);
        forEachCard(new CardOp() {
            @Override public void apply(CardView c) {
                c.setEditMode(false);
                c.setAlpha(1f);
                c.setScaleX(1f); c.setScaleY(1f);
                c.setTranslationX(0f); c.setTranslationY(0f);
            }
        });
        editChrome.setVisibility(View.GONE);
        View guide = root.findViewWithTag("edit_guide");
        if (guide != null) root.removeView(guide);
        repo.saveOrder(entries);
    }

    private void startDrag(AppRepo.Entry e, MotionEvent down) {
        dragging = true;
        dragKey = e.key();
        CardView card = findCard(dragKey);
        if (card == null) { dragging = false; return; }
        card.bringToFront();
        card.setLifted(true);
        card.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start();
        card.setAlpha(1f);
        dragTransX = card.getTranslationX();
        dragTransY = card.getTranslationY();
        grabDX = down != null ? down.getRawX() - (screenX(card) - dragTransX) : 0f;
        grabDY = down != null ? down.getRawY() - (screenY(card) - dragTransY) : 0f;
        dragNeedsAnchor = down == null;
    }

    private void onDragMove(MotionEvent ev) {
        CardView card = findCard(dragKey);
        if (card == null) return;
        if (dragNeedsAnchor) {
            grabDX = ev.getRawX() - (screenX(card) - card.getTranslationX());
            grabDY = ev.getRawY() - (screenY(card) - card.getTranslationY());
            dragNeedsAnchor = false;
        }
        dragTransX = ev.getRawX() - grabDX - (screenX(card) - card.getTranslationX());
        dragTransY = ev.getRawY() - grabDY - (screenY(card) - card.getTranslationY());
        dragTransY = Math.max(-card.getHeight() * 0.2f, Math.min(card.getHeight() * 0.2f, dragTransY));
        card.setTranslationX(dragTransX);
        card.setTranslationY(dragTransY);

        // Over a page chip: show that page and wait for the drop; the list order isn't touched.
        dragChip = tray != null && editChrome.getVisibility() == View.VISIBLE ? tray.chipAt(ev.getRawX(), ev.getRawY()) : -1;
        if (tray != null) tray.setHover(dragChip);
        if (dragChip >= 0) {
            int want = dragChip + (landing ? 1 : 0);
            if (pager.page() != want) pager.toPage(want, true);
            return;
        }
        // Held over the middle of another card: after a moment it lights up, and dropping groups the two.
        CardView under = groupCandidate(ev.getRawX(), ev.getRawY());
        if (under != null) {
            String uk = under.entry.key();
            if (!uk.equals(hoverKey)) { clearGroupTarget(); hoverKey = uk; hoverSince = ev.getEventTime(); }
            else if (ev.getEventTime() - hoverSince >= 500 && groupTarget == null) { groupTarget = under; under.setStackTarget(true); }
            return; // no reordering while aiming at a card
        }
        clearGroupTarget();
        // Where in the list does the finger sit now?
        int[] pl = new int[2];
        pager.getLocationOnScreen(pl);
        float xInStrip = ev.getRawX() - pl[0] + pager.getScrollX();
        if (landing) xInStrip -= pager.getWidth();
        int target = Math.round((xInStrip - px(36)) / (float) Math.max(1, cardStep));
        target = Math.max(0, Math.min(entries.size() - 1, target));
        int current = indexOfKey(dragKey);
        if (target != current && current >= 0) {
            Map<String, Integer> before = new HashMap<String, Integer>();
            for (int i = 0; i < entries.size(); i++) before.put(entries.get(i).key(), i);
            AppRepo.Entry moved = entries.remove(current);
            entries.add(target, moved);
            repo.saveOrder(entries);
            pendingAnim = before;
            buildPages();
        }
        scheduleEdgeScroll(ev.getRawX() - pl[0]);
    }

    private void onDragEnd() {
        CardView card = dragKey != null ? findCard(dragKey) : null;
        if (card != null) {
            if (editing) card.setEditMode(true);
            card.animate().translationX(0f).translationY(0f).setDuration(160).start();
        }
        dragging = false;
        dragNeedsAnchor = false;
        handler.removeCallbacksAndMessages(null);
        String droppedKey = dragKey;
        AppRepo.Entry groupWith = groupTarget != null ? groupTarget.entry : null;
        clearGroupTarget();
        int chip = dragChip;
        dragChip = -1;
        if (tray != null) tray.setHover(-1);
        stopDragVisuals();
        repo.saveOrder(entries);
        if (chip >= 0 && droppedKey != null) moveToPage(droppedKey, chip);
        else if (groupWith != null && droppedKey != null) group(groupWith, droppedKey);
    }

    private void stopDragVisuals() {
        dragKey = null;
        dragTransX = dragTransY = 0f;
    }

    /** Holding the finger near the screen edge flips to the next/previous page. */
    private void scheduleEdgeScroll(final float xInPager) {
        final int w = pager.getWidth();
        final int dir = xInPager > w - px(56) ? 1 : xInPager < px(56) ? -1 : 0;
        handler.removeCallbacksAndMessages(null);
        if (dir == 0 || pager.pageCount() <= 1) return;
        edgeScroll = new Runnable() {
            @Override public void run() {
                if (!dragging) return;
                int p = pager.page() + dir;
                if (p >= 0 && p < pager.pageCount()) pager.toPage(p, true);
                handler.postDelayed(this, 700);
            }
        };
        handler.postDelayed(edgeScroll, 450);
    }

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
        title.setText("Edit home");
        title.setTextSize(26f);
        title.setTypeface(bold);
        title.setTextColor(light ? 0xFF16212A : 0xFFFFFFFF);
        panel.addView(title);

        TextView body = new TextView(this);
        body.setText("Hold and drag an app to rearrange it, or tap it to move it to another page.\nTap \u2212 on an app to hide it.");
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
        if (dragKey != null && dragKey.equals(e.key())) {
            dragging = false;
            stopDragVisuals();
        }
        Toast.makeText(this, "Hidden: " + e.label, Toast.LENGTH_SHORT).show();
        if (entries.isEmpty()) exitEditMode();
        else { builtW = -1; buildPages(); }
    }

    // ---- card lookup helpers ----

    private interface CardOp { void apply(CardView c); }

    private void forEachCard(CardOp op) {
        for (int p = 0; p < strip.getChildCount(); p++) {
            if (!(strip.getChildAt(p) instanceof ViewGroup)) continue;
            ViewGroup page = (ViewGroup) strip.getChildAt(p);
            for (int i = 0; i < page.getChildCount(); i++) {
                View v = page.getChildAt(i);
                if (v instanceof CardView) op.apply((CardView) v);
            }
        }
    }

    private CardView findCard(String key) {
        for (int p = 0; p < strip.getChildCount(); p++) {
            if (!(strip.getChildAt(p) instanceof ViewGroup)) continue;
            ViewGroup page = (ViewGroup) strip.getChildAt(p);
            for (int i = 0; i < page.getChildCount(); i++) {
                View v = page.getChildAt(i);
                if (v instanceof CardView && ((CardView) v).entry.key().equals(key)) return (CardView) v;
            }
        }
        return null;
    }

    /** Topmost card containing the raw screen point, or null. */
    private CardView cardAt(float rawX, float rawY) {
        CardView found = null;
        for (int p = 0; p < strip.getChildCount(); p++) {
            if (!(strip.getChildAt(p) instanceof ViewGroup)) continue;
            ViewGroup page = (ViewGroup) strip.getChildAt(p);
            for (int i = 0; i < page.getChildCount(); i++) {
                View v = page.getChildAt(i);
                if (!(v instanceof CardView)) continue;
                float l = screenX(v), t = screenY(v);
                if (rawX >= l && rawX <= l + v.getWidth() && rawY >= t && rawY <= t + v.getHeight()) found = (CardView) v;
            }
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


    private void showHomeMenu() {
        final boolean wp = welcomeEnabled();
        final String[] items = {"Rearrange apps\u2026", "Theme\u2026", "Accent colour\u2026", "Stack cards\u2026", wp ? "Hide welcome page" : "Show welcome page",
                "Show hidden apps\u2026", "Reset layout", "Open stock home", "Android settings"};
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle("ccNC Home")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: enterEditMode(); break;
                            case 1: showThemes(); break;
                            case 2: showAccents(); break;
                            case 3: showStacks(); break;
                            case 4:
                                getSharedPreferences("home", MODE_PRIVATE).edit().putBoolean("welcome_page", !wp).apply();
                                builtW = -1;
                                buildPages();
                                pager.post(new Runnable() { @Override public void run() { pager.toPage(0, false); } });
                                break;
                            case 5: showHidden(); break;
                            case 6: repo.reset(); reload(); break;
                            case 7: openStockHome(); break;
                            default:
                                launch(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Settings");
                        }
                    }
                }).show();
    }

    private void showAccents() {
        int cur = 0;
        for (int i = 0; i < dev.ccnc.common.Themes.ACCENTS.length; i++) if (dev.ccnc.common.Themes.ACCENTS[i] == Theme.accentOverride) cur = i;
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle("Accent colour")
                .setSingleChoiceItems(dev.ccnc.common.Themes.ACCENT_NAMES, cur, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Theme.saveAccent(HomeActivity.this, dev.ccnc.common.Themes.ACCENTS[which]);
                        refreshTheme();
                        d.dismiss();
                    }
                }).show();
    }

    // ---- stacks: two half-height cards sharing one slot ----

    private void showStacks() {
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle("Stacked cards")
                .setItems(new String[]{"Stack two cards\u2026", "Remove a stack\u2026"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) pickStackFront(); else pickStackToRemove();
                    }
                }).show();
    }

    private void pickStackFront() {
        final List<AppRepo.Entry> free = new ArrayList<AppRepo.Entry>();
        for (AppRepo.Entry e : entries) if (e.stackBack == null) free.add(e);
        if (free.size() < 2) { Toast.makeText(this, "Not enough cards to stack", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[free.size()];
        for (int i = 0; i < names.length; i++) names[i] = free.get(i).label;
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle("Top card")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { pickStackBack(free.get(which), free); }
                }).show();
    }

    private void pickStackBack(final AppRepo.Entry front, List<AppRepo.Entry> free) {
        final List<AppRepo.Entry> rest = new ArrayList<AppRepo.Entry>(free);
        rest.remove(front);
        String[] names = new String[rest.size()];
        for (int i = 0; i < names.length; i++) names[i] = rest.get(i).label;
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle("Under " + front.label)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        List<String[]> st = repo.stacks();
                        st.add(new String[]{front.key(), rest.get(which).key()});
                        repo.saveStacks(st);
                        reload();
                    }
                }).show();
    }

    private String hoverKey;
    private long hoverSince;
    private CardView groupTarget;

    private void clearGroupTarget() {
        if (groupTarget != null) groupTarget.setStackTarget(false);
        groupTarget = null;
        hoverKey = null;
    }

    /** A single card (not the dragged one, not already a stack) whose middle half the finger is over. */
    private CardView groupCandidate(float rawX, float rawY) {
        AppRepo.Entry dragged = null;
        for (AppRepo.Entry e : entries) if (e.key().equals(dragKey)) dragged = e;
        if (dragged == null || dragged.stackBack != null) return null;
        for (int p = 0; p < strip.getChildCount(); p++) {
            if (!(strip.getChildAt(p) instanceof ViewGroup)) continue;
            ViewGroup page = (ViewGroup) strip.getChildAt(p);
            for (int i = 0; i < page.getChildCount(); i++) {
                View v = page.getChildAt(i);
                if (!(v instanceof CardView)) continue;
                CardView cv = (CardView) v;
                if (cv.entry.key().equals(dragKey) || cv.entry.stackBack != null) continue;
                int[] o = new int[2];
                v.getLocationOnScreen(o);
                float l = o[0] - v.getTranslationX(), t = o[1] - v.getTranslationY(), w = v.getWidth(), h = v.getHeight();
                if (rawX >= l + w * 0.25f && rawX <= l + w * 0.75f && rawY >= t && rawY <= t + h) return cv;
            }
        }
        return null;
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

    /** Stack the tapped card with a free one: the tapped card goes on top. */
    private void pickStackBackFor(AppRepo.Entry front) {
        List<AppRepo.Entry> free = new ArrayList<AppRepo.Entry>();
        for (AppRepo.Entry x : entries) if (x.stackBack == null) free.add(x);
        if (free.size() < 2) { Toast.makeText(this, "Not enough cards to stack", Toast.LENGTH_SHORT).show(); return; }
        pickStackBack(front, free);
    }

    private void pickStackToRemove() {
        final List<String[]> st = repo.stacks();
        if (st.isEmpty()) { Toast.makeText(this, "No stacks", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[st.size()];
        for (int i = 0; i < names.length; i++) names[i] = labelOf(st.get(i)[0]) + "  +  " + labelOf(st.get(i)[1]);
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle("Remove which stack?")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        st.remove(which);
                        repo.saveStacks(st);
                        reload();
                    }
                }).show();
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

    private void showHidden() {
        final List<AppRepo.Entry> hidden = new ArrayList<AppRepo.Entry>();
        java.util.Set<String> keys = repo.hidden();
        for (AppRepo.Entry e : repo.load(true)) if (keys.contains(e.key())) hidden.add(e);
        if (hidden.isEmpty()) {
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[hidden.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = hidden.get(i).label;
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle("Tap to show again")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        repo.setHidden(hidden.get(which).key(), false);
                        reload();
                    }
                }).show();
    }

    private void openStockHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.setPackage(AppRepo.STOCK_LAUNCHER);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (getPackageManager().resolveActivity(i, 0) == null) {
            i = getPackageManager().getLaunchIntentForPackage(AppRepo.STOCK_LAUNCHER);
            if (i == null) {
                Toast.makeText(this, "Stock launcher not found", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        launch(i, "Stock home");
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
