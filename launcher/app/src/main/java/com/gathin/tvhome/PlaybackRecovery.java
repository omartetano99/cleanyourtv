package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;

/** A local, durable record of the app/video left playing. No notification contents are collected. */
final class PlaybackRecovery {
    static volatile boolean homeVisible;
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("playback_recovery", Context.MODE_PRIVATE); }
    static int boot(Context c) { return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
    static void initialize(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains("boot")) p.edit().putInt("boot", boot(c)).commit();
    }
    static void launched(Context c, Intent intent, String label) {
        String pkg = intent.getPackage();
        if (intent.getComponent() != null) pkg = intent.getComponent().getPackageName();
        if (pkg == null) {
            android.content.ComponentName resolved = intent.resolveActivity(c.getPackageManager());
            if (resolved != null) pkg = resolved.getPackageName();
        }
        if (pkg == null || pkg.equals(c.getPackageName())) return;
        homeVisible = false;
        prefs(c).edit().clear().putInt("boot", boot(c)).putString("package", pkg)
            .putString("intent", intent.toUri(Intent.URI_INTENT_SCHEME)).putString("title", label)
            .putBoolean("active", true).putBoolean("playing", true).commit();
    }
    static void capture(Context c, String pkg, MediaMetadata metadata, PlaybackState state) {
        SharedPreferences p = prefs(c);
        if (CinemanaResume.pending(c) || homeVisible || !p.getBoolean("active", false) || !pkg.equals(p.getString("package", "")) || state == null) return;
        boolean interactive = c.getSystemService(android.os.PowerManager.class).isInteractive();
        // Some players publish an empty/stopped session while suspending. Keep the last valid snapshot.
        if (state.getState() == PlaybackState.STATE_NONE || state.getState() == PlaybackState.STATE_ERROR) return;
        String id = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        String uri = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI);
        String title = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        long duration = metadata == null ? 0 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = Math.max(0, state.getPosition());
        if (state.getState() == PlaybackState.STATE_PLAYING && state.getLastPositionUpdateTime() > 0)
            position += (long) (Math.max(0, SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime()) * Math.max(0, state.getPlaybackSpeed()));
        if (duration > 0) position = Math.min(position, duration);
        SharedPreferences.Editor edit = p.edit();
        boolean changedMedia = id != null && !id.equals(p.getString("media_id", null));
        if (changedMedia) edit.remove("media_uri");
        if (id != null) edit.putString("media_id", id);
        if (uri != null && !uri.isEmpty()) edit.putString("media_uri", uri);
        if (title != null && !title.isEmpty()) edit.putString("title", title);
        if (position > 0 || changedMedia) edit.putLong("position", position);
        if (duration > 0) edit.putLong("duration", duration);
        if (interactive && !p.getBoolean("wake_pending", false))
            edit.putBoolean("playing", state.getState() == PlaybackState.STATE_PLAYING || state.getState() == PlaybackState.STATE_BUFFERING);
        edit.commit();
    }
    static boolean due(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean("active", false) && (p.getBoolean("wake_pending", false) || p.getInt("boot", boot(c)) != boot(c));
    }
    static void stayHome(Context c) {
        homeVisible = true;
        CinemanaResume.cancel(c);
        prefs(c).edit().putInt("boot", boot(c)).putBoolean("active", false).putBoolean("wake_pending", false).commit();
    }
    static void screenOff(Context c) {
        if (!homeVisible && prefs(c).getBoolean("active", false)) prefs(c).edit().putBoolean("wake_pending", true).commit();
    }
    static void consumed(Context c) { prefs(c).edit().putInt("boot", boot(c)).putBoolean("wake_pending", false).commit(); }
    static Intent intent(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        String pkg = p.getString("package", "");
        if (pkg.isEmpty()) return null;
        String id = p.getString("media_id", null);
        String mediaUri = p.getString("media_uri", null);
        Intent intent;
        if ((pkg.equals("org.smarttube.stable") || pkg.equals("org.smarttube.beta")) && id != null && id.matches("[A-Za-z0-9_-]{11}")) {
            // SmartTube's IntentExtractor explicitly supports YouTube's `t` query parameter.
            Uri video = Uri.parse("https://www.youtube.com/watch").buildUpon().appendQueryParameter("v", id)
                .appendQueryParameter("t", Long.toString(Math.max(0, p.getLong("position", 0) / 1000))).build();
            intent = new Intent(Intent.ACTION_VIEW, video);
        } else if (mediaUri != null && (mediaUri.startsWith("https://") || mediaUri.startsWith("http://"))) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mediaUri));
        } else {
            intent = Intent.parseUri(p.getString("intent", ""), Intent.URI_INTENT_SCHEME);
        }
        intent.setSelector(null); intent.setClipData(null);
        if (intent.getComponent() != null && !pkg.equals(intent.getComponent().getPackageName())) return null;
        intent.setPackage(pkg); intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
    static boolean resume(Context c) {
        if (!due(c)) return false;
        consumed(c); // At most one automatic attempt per boot/wake, including broken/uninstalled apps.
        try {
            Intent intent = intent(c);
            if (intent == null) return false;
            homeVisible = false;
            CinemanaResume.begin(c);
            c.startActivity(intent);
            android.util.Log.i("TvHomeResume", "Restored " + prefs(c).getString("package", ""));
            return true;
        } catch (Exception e) {
            CinemanaResume.cancel(c);
            android.util.Log.w("TvHomeResume", "Cannot restore previous app", e);
            prefs(c).edit().putBoolean("active", false).commit();
            return false;
        }
    }
}
