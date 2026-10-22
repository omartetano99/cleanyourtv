package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Live channels"-style row above the dock: wide tiles, horizontally scrolling.
 * Each entry comes from res/values/config.xml (channels) as Label|target, where target is
 * a package name, a ComponentName, a video URL, or the special "livetv" for the tuner input.
 */
public class ChannelRowAdapter extends RecyclerView.Adapter<ChannelRowAdapter.Holder> {

    interface Listener {
        /** Called whenever a tile gains focus (it rebinds the shared glass highlight). */
        void onChannelFocused(View card, View itemView, String key);

        /** D-pad Up/Down from a tile at {@code index}. Return true when handled. */
        boolean onKey(int keyCode, int index);
    }

    private static final class Channel {
        String label;
        ComponentName component;   // when it is an app
        String uri;                // when it is a video URL or a passthrough channel URI
        Drawable banner;
        Drawable icon;
        int ambient;
    }

    private static final float FOCUS_SCALE = AppAdapter.FOCUS_SCALE;

    private final Context ctx;
    private final Listener listener;
    /** Set by MainActivity so tiles can be focused before they are laid out (smooth scroll then focus). */
    private RecyclerView row;
    private final List<Channel> items = new ArrayList<>();

    ChannelRowAdapter(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
        load();
    }

    void setRow(RecyclerView view) {
        row = view;
    }

    int count() {
        return items.size();
    }

    private static String keyOf(Channel c) {
        return "channel:" + (c.component != null ? c.component.flattenToShortString()
                : String.valueOf(c.uri != null ? c.uri.hashCode() : 0));
    }

    private void load() {
        PackageManager pm = ctx.getPackageManager();
        for (String line : ctx.getResources().getStringArray(R.array.channels)) {
            if (line == null) continue;
            String s = line.trim();
            if (s.isEmpty()) continue;
            String[] parts = s.split("\\|");
            if (parts.length < 2) continue;
            Channel c = new Channel();
            c.label = parts[0].trim();
            String target = parts[1].trim();

            if ("livetv".equalsIgnoreCase(target)) {
                resolveTuner(c, pm);                       // Live TV via the tuner passthrough channel
            } else if (target.contains("://")) {           // arbitrary video URL
                c.uri = target;
            } else if (target.contains("/")) {             // explicit ComponentName
                c.component = ComponentName.unflattenFromString(target);
                if (c.component == null && target.contains("/.")) {
                    int i = target.indexOf('/');
                    String pkg = target.substring(0, i);   // "pkg/.Cls" → "pkg/pkg.Cls"
                    c.component = new ComponentName(pkg, pkg + target.substring(i + 1));
                }
            } else {                                       // plain package: its leanback launch activity
                c.component = launcherFor(pm, target);
            }
            if (c.component == null && c.uri == null) continue;
            resolveArt(c, pm);
            if (c.banner == null && c.icon == null) continue;
            items.add(c);
        }
        notifyDataSetChanged();
    }

    private void resolveTuner(Channel c, PackageManager pm) {
        try {
            TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
            String tunerId = null;
            if (tim != null) {
                for (TvInputInfo info : tim.getTvInputList()) {
                    if (info.getType() != TvInputInfo.TYPE_TUNER) continue;
                    if ((info.getServiceInfo().applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) continue;
                    if (tunerId == null || info.getId().compareTo(tunerId) < 0) tunerId = info.getId();
                }
            }
            if (tunerId != null) {
                c.uri = TvContract.buildChannelUriForPassthroughInput(tunerId).toString();
                c.label = ctx.getString(R.string.live_tv);
                if (c.icon == null) {
                    // URI channels have no package artwork: reuse the input icon so the tile is not dropped.
                    c.icon = ctx.getResources().getDrawable(R.drawable.ic_livetv);
                }
                return;
            }
        } catch (Exception ignored) {
        }
        c.component = launcherFor(pm, ctx.getString(R.string.live_tv).isEmpty() ? "com.android.tv" : "com.cultraview.livetv");
        if (c.component == null) c.component = launcherFor(pm, "com.android.tv");
    }

    private ComponentName launcherFor(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i != null ? i.getComponent() : null;
    }

    private void resolveArt(Channel c, PackageManager pm) {
        try {
            if (c.component != null) {
                ActivityInfo info = pm.getActivityInfo(c.component, 0);
                c.banner = info.loadBanner(pm);
                if (c.banner == null) c.banner = info.applicationInfo.loadBanner(pm);
                c.icon = info.loadIcon(pm);
            }
        } catch (Exception ignored) {
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        // In a horizontal row each item must wrap its card; item_app.xml uses match_parent for the grid.
        v.getLayoutParams().width = (int) (196 * parent.getResources().getDisplayMetrics().density);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Channel c = items.get(position);
        boolean focused = h.card.isFocused();
        h.name.setText(c.label);
        h.name.setAlpha(1f);
        h.card.setContentDescription(c.label);
        h.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        h.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        if (c.banner != null) {
            h.banner.setImageDrawable(c.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(c.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(c.label);
            h.label.setVisibility(View.VISIBLE);
        }
        h.card.setOnClickListener(v -> launch(c));
        h.card.setOnLongClickListener(v -> {
            if (c.component == null) return false;
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.component.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        });
    }

    private void launch(Channel c) {
        Intent i;
        if (c.uri != null) {
            i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(c.uri),
                    c.uri.startsWith("content://android.media.tv/") ? "vnd.android.cursor.item/channel" : "video/*");
        } else {
            i = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(c.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
            PlaybackRecovery.launched(ctx, i, c.label);
        } catch (Exception e) {
            try {
                if (c.component != null) {
                    ctx.startActivity(new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)   // non-leanback fallback
                            .setComponent(c.component)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                }
            } catch (Exception ignored) {
            }
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, c.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return keyOf(items.get(position)).hashCode();
    }

    /** Key of the channel at a position, or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? keyOf(items.get(position)) : null;
    }

    /** Position of the channel with this key, or -1 when it is gone. */
    int positionOfKey(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (keyOf(items.get(i)).equals(key)) return i;
        }
        return -1;
    }

    /** Focuses tile {@code index}, scrolling the row to it first when it is off screen. */
    void requestFocusAt(final int index) {
        if (row == null || items.isEmpty()) return;
        FocusRequests.focus(row, Math.max(0, Math.min(index, items.size() - 1)));
    }

    final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;
        int position = RecyclerView.NO_POSITION;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            final float radius = density * 12f;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int current = getBindingAdapterPosition();
                if (current == RecyclerView.NO_POSITION) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    requestFocusAt(current + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1));
                    return true;
                }
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                return listener != null && listener.onKey(keyCode, current);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                view.animate().scaleX(s).scaleY(s)
                        .setDuration(focused ? 260 : 180)
                        .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f))
                        .start();
                name.setTextColor(ctx.getColor(focused ? R.color.home_accent : R.color.home_text));
                if (focused && listener != null) {
                    position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        // Keep the focused tile fully on screen as the row scrolls.
                        if (row != null) {
                            Rect want = new Rect(0, 0, view.getWidth(), view.getHeight());
                            row.requestChildRectangleOnScreen(itemView, want, true);
                        }
                        listener.onChannelFocused(card, itemView, keyAt(position));
                    }
                }
            });
        }
    }
}
