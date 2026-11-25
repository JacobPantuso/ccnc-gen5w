package dev.ccnc.settings.api;

/** Small functional interfaces for row getters/setters (java.util.function is API 24+; lambdas are fine, d8 desugars them). */
public final class Fn {
    private Fn() {}
    public interface Bool { boolean get(); }
    public interface SetBool { void set(boolean v); }
    public interface Int { int get(); }
    public interface SetInt { void set(int v); }
    public interface Str { String get(); }
    /** A click action; gets the host so it can push a page, refresh, open the stock screen or start an activity. */
    public interface Act { void run(Host h); }
    /** Fills a page with rows. Called every time the page is shown (values are re-read then). */
    public interface Build { void build(Page p); }
}
