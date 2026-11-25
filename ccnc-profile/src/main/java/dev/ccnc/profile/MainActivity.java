package dev.ccnc.profile;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;

import dev.ccnc.common.Art;
import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * ccNC-style profile screen: Guest, Driver 1 and Driver 2 with the active one marked. Reads the unit's
 * UserProfileManager (framework, by reflection: getUserType(), getUserName(id)); switching and editing
 * stay with the stock Profile app, which "Manage profiles" opens.
 */
public final class MainActivity extends Activity {

    private static final String TAG = "ccncProfile";
    private static final String STOCK = "com.hkmc.system.app.userprofile/.UserProfileMainActivity";

    private final String[] names = {"Guest driver", "Driver 1", "Driver 2"};
    private int active = -1;
    private Cards cards;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this); // shared ccNC theme
        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(topBar());
        cards = new Cards(this);
        content.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        load();
        cards.invalidate();
    }

    /** Fills {@link #names} and {@link #active} from the unit; leaves the defaults if it can't. */
    private void load() {
        try {
            Class<?> c = Class.forName("com.hkmc.userprofile.UserProfileManager");
            Object m = c.getConstructor(Context.class).newInstance(this);
            Method type = c.getMethod("getUserType"), name = c.getMethod("getUserName", int.class);
            active = ((Number) type.invoke(m)).intValue();
            for (int i = 1; i < 3; i++) {
                String n = (String) name.invoke(m, i);
                if (n != null && !n.trim().isEmpty()) names[i] = n.trim();
            }
            Log.i(TAG, "active=" + active + " names=" + java.util.Arrays.toString(names));
        } catch (Throwable t) {
            Log.i(TAG, "profile service unavailable: " + t);
            active = -1;
        }
    }

    /** Tap on a non-active profile: stock UserProfileManager.switchUser(id, fromSetting=false, false). Not tested on the car. */
    private void switchTo(int id) {
        try {
            Class<?> c = Class.forName("com.hkmc.userprofile.UserProfileManager");
            Object m = c.getConstructor(Context.class).newInstance(this);
            c.getMethod("switchUser", int.class, boolean.class, boolean.class).invoke(m, id, false, false);
            Log.i(TAG, "switchUser(" + id + ")");
        } catch (Throwable t) {
            Log.i(TAG, "switchUser failed: " + t);
        }
    }

    private Bitmap asset(String n) {
        try {
            java.io.InputStream in = getAssets().open("ccnc/frs/" + n + ".png");
            try { return android.graphics.BitmapFactory.decodeStream(in); } finally { in.close(); }
        } catch (Throwable t) { return null; }
    }

    /** ccNC USERPROFILE_UserButtonGroup: 670x296 at (237,180) in a 1920x720 window. */
    private final class Cards extends View {
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF r = new RectF();
        private final float[] cx = {95, 335, 575}; // slot centres inside the group (btn x 0 / 225 / 480)
        private Bitmap frame, guest;
        private final Bitmap[] users = new Bitmap[2];
        private float k, gx, gy;

        Cards(Context c) {
            super(c);
            setClickable(true);
            frame = asset("frs_ic_profile_l_s");
            guest = asset("frs_ic_thumbnail_guest");
            users[0] = asset("frs_ic_detailinfo_01_l_01");
            users[1] = asset("frs_ic_detailinfo_01_l_02");
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                for (int i = 0; i < 3; i++) {
                    float x = gx + cx[i] * k, y = gy + 117 * k;
                    if (Math.abs(e.getX() - x) < 110 * k && Math.abs(e.getY() - y) < 117 * k) {
                        if (i != active && active >= 0) switchTo(i);
                        break;
                    }
                }
            }
            return true;
        }

        @Override protected void onDraw(Canvas c) {
            k = getWidth() / 1920f;
            float oy = getHeight() - 720f * k; // window y 0 = top of the screen, view sits below the top bar
            gx = (1920f - 670f) / 2f * k; // centred (ccNC puts it at x=237 beside its safety message)
            gy = 180f * k + oy;
            for (int i = 0; i < 3; i++) {
                boolean on = i == active;
                float s = (on ? 220f : 190f) * k, x = gx + cx[i] * k, y = gy + 117f * k;
                Bitmap b = i == 0 ? guest : users[i - 1];
                if (b != null) {
                    bmp.setAlpha(on ? 255 : 178); // userImg.opacity 1 / 0.7
                    r.set(x - s / 2, y - s / 2, x + s / 2, y + s / 2);
                    c.drawBitmap(b, null, r, bmp);
                }
                if (on && frame != null) {
                    float f = 234f * k;
                    r.set(x - f / 2, y - f / 2, x + f / 2, y + f / 2);
                    bmp.setAlpha(255);
                    c.drawBitmap(frame, null, r, bmp);
                }
                if (on) {
                    text.setTypeface(Fonts.get(getContext(), Fonts.REGULAR));
                    text.setTextSize(40f * k);
                    text.setColor(Palette.TEXT);
                    text.setTextAlign(Paint.Align.CENTER);
                    c.drawText(names[i], x, gy + (296f - 8f) * k, text);
                }
            }
        }
    }

    private View topBar() {
        LinearLayout bar = dev.ccnc.common.Chrome.topBar(this, "Profile");
        bar.setLayoutParams(new LinearLayout.LayoutParams(-1, Palette.px(this, 56)));
        return bar;
    }

    private TextView text(String t, float size, int color, int face) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Fonts.get(this, face));
        return v;
    }
}
