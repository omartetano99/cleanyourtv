package com.gathin.tvhome;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared remote targets and typography; no per-frame blur or large bitmap surfaces. */
final class TvUi {
    static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static TextView text(Context c, String value, int size, int color) {
        TextView v = new TextView(c); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(c.getResources().getFont(R.font.outfit)); v.setFontVariationSettings("'wght' " + (size >= 28 ? "600" : "500")); v.setIncludeFontPadding(false); return v;
    }
    static TextView button(Context c, String value, Runnable action) {
        TextView v = text(c, value, 18, 0xfff5f4ef); v.setGravity(android.view.Gravity.CENTER_VERTICAL);
        v.setPadding(dp(c, 18), dp(c, 13), dp(c, 18), dp(c, 13));
        v.setBackgroundResource(R.drawable.control_bg); v.setFocusable(true); v.setClickable(true);
        v.setOnClickListener(x -> action.run());
        v.setOnFocusChangeListener((x, focus) -> {
            v.setTextColor(focus ? 0xff9ce8d5 : 0xfff5f4ef);
            if (!AmbientSettings.reducedMotion(c)) x.animate().scaleX(focus ? 1.015f : 1f).scaleY(focus ? 1.015f : 1f).setDuration(140).start();
        });
        return v;
    }
    static LinearLayout page(Context c, String title, String subtitle) {
        LinearLayout page = new LinearLayout(c); page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundResource(R.drawable.home_background); page.setPadding(dp(c, 48), dp(c, 28), dp(c, 48), dp(c, 28));
        page.addView(text(c, title, 32, 0xfff5f4ef));
        TextView sub = text(c, subtitle, 16, 0xffbac4c5); sub.setPadding(0, dp(c, 8), 0, dp(c, 20)); page.addView(sub);
        return page;
    }
}
