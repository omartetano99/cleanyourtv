package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** System-bound media observer. Uses notification-listener access only to observe media sessions. */
public final class PlaybackMonitor extends NotificationListenerService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<MediaController, MediaController.Callback> controllers = new LinkedHashMap<>();
    private MediaSessionManager manager;
    private boolean connected;
    private final Runnable wakeTick = this::wake;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::observe;
    private final Runnable saveTick = new Runnable() {
        @Override public void run() {
            snapshot();
            if (connected) handler.postDelayed(this, 5000);
        }
    };
    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) || Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
                handler.removeCallbacks(wakeTick);
                snapshot(); PlaybackRecovery.screenOff(c);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                handler.removeCallbacks(wakeTick);
                handler.postDelayed(wakeTick, 1500);
            }
        }
    };
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        if (connected) return;
        connected = true;
        PlaybackRecovery.initialize(this);
        manager = getSystemService(MediaSessionManager.class);
        ComponentName component = new ComponentName(this, PlaybackMonitor.class);
        manager.addOnActiveSessionsChangedListener(sessions, component, handler);
        observe(manager.getActiveSessions(component));
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(power, filter);
        handler.post(saveTick);
    }
    private void observe(List<MediaController> active) {
        for (Map.Entry<MediaController, MediaController.Callback> e : controllers.entrySet()) e.getKey().unregisterCallback(e.getValue());
        controllers.clear();
        for (MediaController c : active) {
            MediaController.Callback callback = new MediaController.Callback() {
                @Override public void onMetadataChanged(MediaMetadata metadata) { save(c); }
                @Override public void onPlaybackStateChanged(PlaybackState state) { save(c); }
            };
            controllers.put(c, callback); c.registerCallback(callback, handler); save(c);
        }
    }
    private void save(MediaController c) { PlaybackRecovery.capture(this, c.getPackageName(), c.getMetadata(), c.getPlaybackState()); }
    private void snapshot() { for (MediaController c : controllers.keySet()) save(c); }
    private void wake() {
        if (!getSystemService(PowerManager.class).isInteractive() || PlaybackRecovery.homeVisible || !PlaybackRecovery.due(this)) return;
        String pkg = PlaybackRecovery.prefs(this).getString("package", "");
        for (MediaController c : controllers.keySet()) {
            if (!pkg.equals(c.getPackageName())) continue;
            PlaybackState state = c.getPlaybackState();
            if (!canResumeSession(state, c.getMetadata())) continue;
            // Standby normally leaves the original player alive. Do not reopen or reset its position.
            if (PlaybackRecovery.prefs(this).getBoolean("playing", true)) c.getTransportControls().play();
            PlaybackRecovery.consumed(this);
            return;
        }
        PlaybackRecovery.resume(this);
    }
    static boolean canResumeSession(PlaybackState state, MediaMetadata metadata) {
        if (state == null || metadata == null) return false;
        if (state.getState() != PlaybackState.STATE_PLAYING && state.getState() != PlaybackState.STATE_PAUSED) return false;
        // An empty/buffering session can survive after the player's surface has gone.
        // Treat it as needing a fresh launch instead of consuming recovery on a black screen.
        if (metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) <= 0 && state.getPosition() <= 0) return false;
        return state.getState() == PlaybackState.STATE_PLAYING
            || (state.getActions() & (PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE)) != 0;
    }
    @Override public void onListenerDisconnected() { disconnect(); super.onListenerDisconnected(); }
    @Override public void onDestroy() { disconnect(); super.onDestroy(); }
    private void disconnect() {
        if (!connected) return;
        connected = false; handler.removeCallbacksAndMessages(null);
        manager.removeOnActiveSessionsChangedListener(sessions);
        unregisterReceiver(power);
        for (Map.Entry<MediaController, MediaController.Callback> e : controllers.entrySet()) e.getKey().unregisterCallback(e.getValue());
        controllers.clear();
    }
}
