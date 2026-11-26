package dev.ccnc.settings.sections;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;

/**
 * Navigation (stock classId com.hkmc.intent.action.ACTION_NAVI_SETUP).
 *
 * NOT PORTED. The stock card does not open a settings app: it sends ACTION_NAVI_SETUP to the navigation app
 * (com.mnsoft.navi), whose Display / Guidance / Alerts / Map options live inside the navigation engine. The engine
 * does answer the smart-control broadcasts for type 1 (NaviETCReceiver -> NativeCall), but the key ids and value
 * ranges are tables inside its native library and are not decoded, so nothing can be read or written safely from
 * here. ccNC Setup therefore shows a note instead of starting the stock navigation settings screen; the same
 * options remain reachable from the navigation app's own menu. The navigation sound options that are car audio
 * settings (guidance volume, guidance during calls, volume priority, mute) are native under Sound, Guidance.
 */
public final class NavigationSection extends Section {
    @Override public String classId() { return "com.hkmc.intent.action.ACTION_NAVI_SETUP"; }
    @Override public int treeNode() { return 1011; }
    @Override public String icon() { return "set_ic_menu_navigation"; }

    @Override public void build(Category c) {
        c.tab(c.word(1255, "Navigation"), new Fn.Build() {
            @Override public void build(Page p) {
                p.note("Map, guidance and alert options are set from the navigation app's own menu. Navigation volumes are under Sound > Guidance.");
            }
        });
    }
}
