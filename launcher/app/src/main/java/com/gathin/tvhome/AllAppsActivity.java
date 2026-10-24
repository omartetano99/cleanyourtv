package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Includes TV, sideloaded mobile and (on request) non-launchable system apps. */
public final class AllAppsActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private RecyclerView grid;
    private boolean system;
    private int generation;
    private TextView filter, status;
    private static final class Entry { String label, pkg; Intent launch; Drawable icon; }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout page = TvUi.page(this, "All apps", "Open an app · Hold OK for app information · Back to Home");
        filter = TvUi.button(this, "Show system apps", () -> { system = !system; load(); }); page.addView(filter);
        status = TvUi.text(this, "Loading apps…", 15, 0xffbac4c5); page.addView(status);
        grid = new RecyclerView(this); grid.setItemAnimator(null); grid.setClipToPadding(false);
        grid.setPadding(0, TvUi.dp(this, 12), 0, TvUi.dp(this, 8));
        int columns = Math.max(3, Math.min(6, (int)(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density - 96) / 160));
        grid.setLayoutManager(new GridLayoutManager(this, columns));
        page.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(page);
    }
    @Override protected void onResume() { super.onResume(); load(); }
    private void load() {
        int request = ++generation; boolean all = system;
        filter.setText(all ? "Showing all installed apps · Hide system apps" : "Showing apps you can open · Show system apps");
        worker.execute(() -> {
            List<Entry> entries = new ArrayList<>(); PackageManager pm = getPackageManager();
            for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                if (getPackageName().equals(app.packageName)) continue;
                Intent launch = pm.getLeanbackLaunchIntentForPackage(app.packageName);
                if (launch == null) launch = pm.getLaunchIntentForPackage(app.packageName);
                if (!all && launch == null) continue;
                Entry e = new Entry(); e.pkg = app.packageName; e.launch = launch;
                e.label = String.valueOf(app.loadLabel(pm)); entries.add(e);
            }
            entries.sort((a,b) -> a.label.compareToIgnoreCase(b.label));
            runOnUiThread(() -> {
                if (isDestroyed() || request != generation) return;
                grid.setAdapter(new Apps(entries)); status.setText(entries.size() + " apps" + (all ? " · Apps without a launch screen open their information page" : ""));
                if (!filter.hasFocus() && !entries.isEmpty()) FocusRequests.focus(grid, 0);
            });
        });
    }
    private void details(Entry e) { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg))); }
    private final class Apps extends RecyclerView.Adapter<AppHolder> {
        final List<Entry> entries; Apps(List<Entry> e) { entries = e; }
        @Override public AppHolder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout tile = new LinearLayout(AllAppsActivity.this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(android.view.Gravity.CENTER);
            tile.setPadding(8, TvUi.dp(AllAppsActivity.this, 14), 8, TvUi.dp(AllAppsActivity.this, 14));
            tile.setBackgroundResource(R.drawable.control_bg); tile.setFocusable(true); tile.setClickable(true);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, TvUi.dp(AllAppsActivity.this, 126));
            lp.setMargins(4, 4, 4, 4); tile.setLayoutParams(lp);
            ImageView icon = new ImageView(AllAppsActivity.this); tile.addView(icon, new LinearLayout.LayoutParams(TvUi.dp(AllAppsActivity.this, 54), TvUi.dp(AllAppsActivity.this, 54)));
            TextView name = TvUi.text(AllAppsActivity.this, "", 16, 0xfff5f4ef); name.setGravity(android.view.Gravity.CENTER); name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END); name.setPadding(0, 10, 0, 0); tile.addView(name, new LinearLayout.LayoutParams(-1, -2));
            return new AppHolder(tile, icon, name);
        }
        @Override public void onBindViewHolder(AppHolder h, int index) {
            Entry e = entries.get(index); h.name.setText(e.label); h.itemView.setContentDescription(e.label);
            h.icon.setImageDrawable(e.icon); h.icon.setTag(e.pkg);
            if (e.icon == null) worker.execute(() -> {
                try { Drawable icon = getPackageManager().getApplicationIcon(e.pkg); runOnUiThread(() -> {
                    if (isDestroyed()) return; e.icon = icon; if (e.pkg.equals(h.icon.getTag())) h.icon.setImageDrawable(icon);
                }); } catch (Exception ignored) { }
            });
            h.itemView.setOnClickListener(v -> {
                try { if (e.launch == null) details(e); else { startActivity(e.launch); PlaybackRecovery.launched(AllAppsActivity.this, e.launch, e.label); } }
                catch (Exception ex) { android.widget.Toast.makeText(AllAppsActivity.this, "App is unavailable", android.widget.Toast.LENGTH_SHORT).show(); }
            });
            h.itemView.setOnLongClickListener(v -> { details(e); return true; });
        }
        @Override public int getItemCount() { return entries.size(); }
    }
    private static final class AppHolder extends RecyclerView.ViewHolder {
        ImageView icon; TextView name; AppHolder(LinearLayout v, ImageView i, TextView n) { super(v); icon=i; name=n; }
    }
    @Override protected void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}
