package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.session.*;

/** Unknown media access is deliberately treated as busy, including during service startup. */
final class PlaybackGuard {
    static boolean busyState(int state) {
        return state==PlaybackState.STATE_PLAYING || state==PlaybackState.STATE_BUFFERING || state==PlaybackState.STATE_CONNECTING
            || state==PlaybackState.STATE_FAST_FORWARDING || state==PlaybackState.STATE_REWINDING
            || state==PlaybackState.STATE_SKIPPING_TO_NEXT || state==PlaybackState.STATE_SKIPPING_TO_PREVIOUS || state==PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM;
    }
    static boolean canIdle(Context c) {
        try {
            AudioManager audio=c.getSystemService(AudioManager.class);
            if(audio==null || audio.isMusicActive()) return false;
            MediaSessionManager sessions=c.getSystemService(MediaSessionManager.class); if(sessions==null) return false;
            for(MediaController controller:sessions.getActiveSessions(new ComponentName(c,PlaybackMonitor.class))) {
                PlaybackState state=controller.getPlaybackState(); if(state==null || busyState(state.getState())) return false;
            }
            return true;
        } catch(SecurityException e) { return false; }
    }
}
