package dev.ccnc.home;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

/** Horizontal scroller that settles on whole pages (one page = the view's width). */
final class Pager extends HorizontalScrollView {

    interface Listener { void onPagePosition(float position); }

    private Listener listener;
    private int startPage;
    private boolean flung;
    private float downX;
    private boolean pagingEnabled = true;

    Pager(Context c) {
        super(c);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(true);
    }

    void setListener(Listener l) { listener = l; }

    /** When false the pager neither intercepts nor consumes touches (card drag/edit mode). */
    void setPagingEnabled(boolean enabled) { pagingEnabled = enabled; }

    int page() { return getWidth() == 0 ? 0 : Math.round((float) getScrollX() / getWidth()); }

    int pageCount() {
        if (getChildCount() == 0 || getWidth() == 0) return 1;
        return Math.max(1, Math.round((float) getChildAt(0).getWidth() / getWidth()));
    }

    void toPage(int p, boolean smooth) {
        p = Math.max(0, Math.min(pageCount() - 1, p));
        if (smooth) smoothScrollTo(p * getWidth(), 0); else scrollTo(p * getWidth(), 0);
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        if (!pagingEnabled) return false;
        int a = ev.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) { startPage = page(); flung = false; downX = ev.getRawX(); }
        boolean r = super.onTouchEvent(ev);
        if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && !flung) {
            // Short, quick swipes may not register as a fling: go by distance instead.
            float dx = ev.getRawX() - downX;
            if (a == MotionEvent.ACTION_UP && Math.abs(dx) > getWidth() * 0.12f) toPage(dx < 0 ? startPage + 1 : startPage - 1, true);
            else toPage(page(), true);
        }
        return r;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!pagingEnabled) return false;
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) { startPage = page(); flung = false; downX = ev.getRawX(); }
        return super.onInterceptTouchEvent(ev);
    }

    @Override public void fling(int velocityX) {
        flung = true;
        // velocityX > 0 means content moves toward higher scrollX, i.e. the next page.
        toPage(velocityX > 0 ? startPage + 1 : startPage - 1, true);
    }

    @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        if (listener != null && getWidth() > 0) listener.onPagePosition((float) l / getWidth());
    }
}
