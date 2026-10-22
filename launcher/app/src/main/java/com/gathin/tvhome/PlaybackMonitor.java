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
                snapshot(); PlaybackRecovery.screenOff(c);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                handler.postDelayed(PlaybackMonitor.this::wake, 1500);
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
            if (state == null || state.getState() == PlaybackState.STATE_NONE || state.getState() == PlaybackState.STATE_STOPPED || state.getState() == PlaybackState.STATE_ERROR) continue;
            // Standby normally leaves the original player alive. Do not reopen or reset its position.
            if (PlaybackRecovery.prefs(this).getBoolean("playing", true)) c.getTransportControls().play();
            PlaybackRecovery.consumed(this);
            return;
        }
        PlaybackRecovery.resume(this);
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
