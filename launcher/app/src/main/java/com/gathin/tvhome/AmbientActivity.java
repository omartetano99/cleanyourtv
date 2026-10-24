package com.gathin.tvhome;

import android.app.Activity;
import android.os.*;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Home-only idle surface; it never launches over an external app or HDMI. */
public final class AmbientActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable guard=new Runnable(){ @Override public void run(){
        if(!getIntent().getBooleanExtra("preview",false) && !PlaybackGuard.canIdle(AmbientActivity.this)) { finish(); return; }
        handler.postDelayed(this,2000);
    }};
    @Override public void onCreate(Bundle saved) { super.onCreate(saved); setContentView(new AmbientView(this)); }
    @Override protected void onResume() { super.onResume(); handler.post(guard); }
    @Override protected void onPause() { handler.removeCallbacksAndMessages(null); super.onPause(); finish(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getAction()==KeyEvent.ACTION_DOWN) {
            finish(); int key=event.getKeyCode();
            if(key==KeyEvent.KEYCODE_VOLUME_UP || key==KeyEvent.KEYCODE_VOLUME_DOWN || key==KeyEvent.KEYCODE_VOLUME_MUTE) return super.dispatchKeyEvent(event);
        }
        return true;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e) { if(e.getAction()==MotionEvent.ACTION_DOWN) finish(); return true; }
}
