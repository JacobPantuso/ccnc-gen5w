package dev.ccnc.settings.api;

import android.app.Activity;
import android.content.Intent;

/** What rows can ask of the Setup screen (implemented by MainActivity). */
public interface Host {
    Activity activity();
    /** Drill into a sub-page in place of the current list (ccNC's _loader); its title goes into the top-bar depth text. */
    void push(String title, Fn.Build page);
    /** Re-read every getter on the current page and redraw it. */
    void refresh();
    /** Leave the current sub-page (same as the Back key); used after a confirm page has done its work. */
    void back();
    /** startActivity with NEW_TASK, failures swallowed. */
    void start(Intent i);
}
