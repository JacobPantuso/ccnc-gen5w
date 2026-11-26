package dev.ccnc.profile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.List;

/**
 * ccNC Profile's settings and photos for the other ccNC apps (top-bar profile chip, ccNC Setup, Home's start-up
 * handling):
 *   content://dev.ccnc.profile/settings      one row: default_profile, auto_advance, auto_advance_custom
 *   call("set", key, {value})                ccNC Setup changes default_profile / auto_advance
 *   content://dev.ccnc.profile/photo/<id>    PNG of profile id's picture (see ProfileStore.photo)
 * Changes are announced with notifyChange on content://dev.ccnc.profile.
 */
public final class ProfileProvider extends ContentProvider {

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        List<String> seg = uri.getPathSegments();
        if (seg.isEmpty() || !seg.get(0).equals("settings")) return null;
        MatrixCursor c = new MatrixCursor(new String[]{"default_profile", "auto_advance", "auto_advance_custom"});
        c.addRow(new Object[]{ProfileStore.defaultProfile(getContext()), ProfileStore.autoAdvance(getContext()),
                ProfileStore.autoAdvanceCustom(getContext()) ? 1 : 0});
        return c;
    }

    /** call("set", key, {value}) from ccNC Setup: default_profile (-1..2) or auto_advance (-1..30 s). */
    @Override public android.os.Bundle call(String method, String key, android.os.Bundle extras) {
        if (!"set".equals(method) || key == null || extras == null) return null;
        return ProfileStore.set(getContext(), key, extras.getInt("value", Integer.MIN_VALUE)) ? new android.os.Bundle() : null;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 2 || !seg.get(0).equals("photo") || !"r".equals(mode)) throw new FileNotFoundException(uri.toString());
        int id;
        try { id = Integer.parseInt(seg.get(1)); } catch (NumberFormatException e) { throw new FileNotFoundException(uri.toString()); }
        if (id < 0 || id > 2) throw new FileNotFoundException(uri.toString());
        Bitmap b = ProfileStore.photo(getContext(), id);
        if (b == null) throw new FileNotFoundException("no picture for profile " + id);
        // Written to a temp file first and renamed: two apps asking at once never read a half-written file.
        File f = new File(getContext().getCacheDir(), "served_" + id + ".png");
        File tmp = new File(getContext().getCacheDir(), "served_" + id + "." + Thread.currentThread().getId() + ".tmp");
        try {
            FileOutputStream o = new FileOutputStream(tmp);
            try { b.compress(Bitmap.CompressFormat.PNG, 100, o); } finally { o.close(); }
            if (!tmp.renameTo(f)) throw new java.io.IOException("rename " + tmp);
        } catch (Exception e) { tmp.delete(); throw new FileNotFoundException(e.toString()); }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return uri.getPath() != null && uri.getPath().startsWith("/photo") ? "image/png" : null; }
    @Override public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException("read-only"); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException("read-only"); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException("read-only"); }
}
