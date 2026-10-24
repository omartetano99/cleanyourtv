package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;

/** System screensaver shares the same weather/landscape surface and conservative playback guard. */
public final class ClockDream extends DreamService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable guard=new Runnable(){ @Override public void run(){
        if(!PlaybackGuard.canIdle(ClockDream.this)) { finish(); return; } handler.postDelayed(this,2000);
    }};
    @Override public void onAttachedToWindow() { super.onAttachedToWindow(); setInteractive(false); setFullscreen(true); setScreenBright(false);
        if(!PlaybackGuard.canIdle(this)) { finish(); return; } setContentView(new AmbientView(this)); }
    @Override public void onDreamingStarted() { super.onDreamingStarted(); handler.post(guard); }
    @Override public void onDreamingStopped() { handler.removeCallbacksAndMessages(null); super.onDreamingStopped(); }
    @Override public void onDetachedFromWindow() { handler.removeCallbacksAndMessages(null); super.onDetachedFromWindow(); }
}
