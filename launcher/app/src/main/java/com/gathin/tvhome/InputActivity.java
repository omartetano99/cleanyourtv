package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Hosts the selected passthrough session instead of hoping the vendor launch intent switches ports. */
public final class InputActivity extends Activity {
    private TvView tv;
    private LinearLayout panel;
    private TextView status;
    private String input, label;
    private boolean started;
    private android.media.AudioManager audio;
    private android.media.AudioFocusRequest audioFocus;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> message("No picture yet. Check the connected device or open the TV input app.");
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        input = getIntent().getStringExtra("input_id"); label = getIntent().getStringExtra("input_label");
        TvInputManager manager = getSystemService(TvInputManager.class);
        TvInputInfo info = input == null || manager == null ? null : manager.getTvInputInfo(input);
        if (info == null || !info.isPassthroughInput()) { finish(); return; }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(0xff000000);
        tv = new TvView(this); root.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(30, 20, 30, 20); panel.setBackgroundColor(0xee10151a);
        panel.addView(TvUi.text(this, label == null ? "TV input" : label, 26, 0xfff5f4ef));
        status = TvUi.text(this, "Connecting…", 18, 0xffbac4c5); panel.addView(status);
        panel.addView(TvUi.button(this, "Retry connection", this::tune));
        panel.addView(TvUi.button(this, "Open TV input app", () -> vendor(info)));
        panel.addView(TvUi.button(this, "Back to Home", this::finish));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(TvUi.dp(this, 580), -2, Gravity.CENTER); root.addView(panel, lp); setContentView(root);
        audio = getSystemService(android.media.AudioManager.class);
        audioFocus = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build())
            .setOnAudioFocusChangeListener(change -> { if (started) tv.setStreamVolume(change == android.media.AudioManager.AUDIOFOCUS_GAIN ? 1f : 0f); }).build();
        tv.setCallback(new TvView.TvInputCallback() {
            @Override public void onVideoAvailable(String id) { if (started) { handler.removeCallbacks(timeout); panel.setVisibility(View.GONE); tv.requestFocus(); } }
            @Override public void onVideoUnavailable(String id, int reason) { message(reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING ? "Connecting…" : "No signal. Check the cable and connected device."); }
            @Override public void onConnectionFailed(String id) { message("This TV could not open the selected input. Try the TV input app."); }
            @Override public void onDisconnected(String id) { message("Input disconnected. You can retry or return Home."); }
        });
    }
    @Override protected void onStart() { super.onStart(); if (tv != null) { started = true; tune(); } }
    private void tune() {
        if (!started) return; handler.removeCallbacks(timeout); message("Connecting…");
        try { if (audio != null) audio.requestAudioFocus(audioFocus); tv.reset(); tv.tune(input, TvContract.buildChannelUriForPassthroughInput(input)); tv.setStreamVolume(1f); handler.postDelayed(timeout, 10000); }
        catch (RuntimeException e) { message("The TV input service rejected this connection. Open the TV input app."); }
    }
    private void message(String text) { if (!started) return; boolean hidden = panel.getVisibility() != View.VISIBLE; status.setText(text); panel.setVisibility(View.VISIBLE); if (hidden || !panel.hasFocus()) panel.getChildAt(2).requestFocus(); }
    private void vendor(TvInputInfo info) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(TvContract.buildChannelUriForPassthroughInput(input), "vnd.android.cursor.item/channel")
                .setPackage(getString(R.string.live_tv_package).trim()).putExtra(TvInputInfo.EXTRA_INPUT_ID, input);
            if (i.resolveActivity(getPackageManager()) == null) i.setPackage(info.getServiceInfo().packageName);
            startActivity(i); finish();
        } catch (Exception e) { message("Vendor input shortcut unavailable. Use the remote’s Input button."); }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_MENU) { message("Input controls"); return true; }
        return super.dispatchKeyEvent(e);
    }
    @Override protected void onStop() { started = false; handler.removeCallbacksAndMessages(null); if (tv != null) tv.reset(); if (audio != null && audioFocus != null) audio.abandonAudioFocusRequest(audioFocus); super.onStop(); }
}
