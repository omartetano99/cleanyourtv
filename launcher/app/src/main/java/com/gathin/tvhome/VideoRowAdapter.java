package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Outline;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/** Poster shelves with persistent titles, local focus outlines and publisher-owned playback links. */
public class VideoRowAdapter extends RecyclerView.Adapter<VideoRowAdapter.Holder> {
    interface Listener {
        void onVideoFocused(View card, View itemView, int rowIndex, int lane);
        boolean onKey(int rowIndex, int lane, int keyCode);
    }
    private final Context ctx;
    private final Listener listener;
    private final int rowIndex;
    private final RecyclerView row;
    private List<FeedLoader.Video> items = new ArrayList<>();
    VideoRowAdapter(Context ctx, RecyclerView row, int rowIndex, Listener listener) {
        this.ctx = ctx; this.row = row; this.rowIndex = rowIndex; this.listener = listener;
    }
    void setItems(List<FeedLoader.Video> videos) {
        List<FeedLoader.Video> old = items;
        List<FeedLoader.Video> next = videos == null ? new ArrayList<>() : new ArrayList<>(videos);
        androidx.recyclerview.widget.DiffUtil.DiffResult diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(
            new androidx.recyclerview.widget.DiffUtil.Callback() {
                @Override public int getOldListSize() { return old.size(); }
                @Override public int getNewListSize() { return next.size(); }
                @Override public boolean areItemsTheSame(int a, int b) { return old.get(a).identity().equals(next.get(b).identity()); }
                @Override public boolean areContentsTheSame(int a, int b) {
                    FeedLoader.Video x = old.get(a), y = next.get(b);
                    return java.util.Objects.equals(x.title, y.title) && java.util.Objects.equals(x.thumb, y.thumb)
                        && java.util.Objects.equals(x.rating, y.rating) && java.util.Objects.equals(x.genres, y.genres)
                        && x.bannerIndex == y.bannerIndex && x.positionMs == y.positionMs && x.durationMs == y.durationMs;
                }
            });
        items = next;
        diff.dispatchUpdatesTo(this);
    }
    String keyAt(int index) { return index >= 0 && index < items.size() ? items.get(index).identity() : null; }
    int positionOf(String key) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).identity().equals(key)) return i;
        return -1;
    }
    void focusAt(int lane) {
        if (!items.isEmpty()) FocusRequests.focus(row, Math.max(0, Math.min(lane, items.size() - 1)));
    }
    @Override public int getItemViewType(int position) {
        FeedLoader.Video video = items.get(position);
        return CinemanaResume.PACKAGE.equals(video.pkg) && video.bannerIndex >= 0 ? 1 : 0;
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
        if (type == 1) {
            float density = parent.getResources().getDisplayMetrics().density;
            // Cinemana's original cover artwork is 4050 × 1500 (2.7:1).
            float screenDp = parent.getResources().getDisplayMetrics().widthPixels / density;
            int width = Math.round(Math.min(480f, screenDp - 112f) * density);
            ViewGroup.LayoutParams itemParams = item.getLayoutParams();
            itemParams.width = width;
            item.setLayoutParams(itemParams);
            View card = item.findViewById(R.id.card);
            ViewGroup.LayoutParams cardParams = card.getLayoutParams();
            cardParams.height = Math.round(width / 2.7f);
            card.setLayoutParams(cardParams);
            ((ImageView) item.findViewById(R.id.thumb)).setScaleType(ImageView.ScaleType.FIT_CENTER);
            TextView title = item.findViewById(R.id.title);
            title.setMaxLines(1);
            title.setMaxWidth(Math.round(width * 0.4f));
            android.widget.LinearLayout details = item.findViewById(R.id.bannerDetails);
            ((ViewGroup) title.getParent()).removeView(title);
            android.widget.LinearLayout.LayoutParams titleParams = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.setMarginStart(Math.round(14 * density));
            details.addView(title, titleParams);
            ViewGroup.MarginLayoutParams detailsParams = (ViewGroup.MarginLayoutParams) details.getLayoutParams();
            detailsParams.topMargin = Math.round(10 * density);
            detailsParams.height = Math.round(28 * density);
            details.setLayoutParams(detailsParams);
        }
        return new Holder(item);
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        FeedLoader.Video v = items.get(position);
        h.card.animate().cancel();
        h.card.setScaleX(h.card.isFocused() ? AppAdapter.FOCUS_SCALE : 1f);
        h.card.setScaleY(h.card.isFocused() ? AppAdapter.FOCUS_SCALE : 1f);
        h.title.setText(v.title);
        boolean hasRating = v.rating != null && !v.rating.isEmpty();
        boolean hasGenres = v.genres != null && !v.genres.isEmpty();
        h.details.setVisibility(getItemViewType(position) == 1 || hasRating || hasGenres ? View.VISIBLE : View.GONE);
        h.rating.setVisibility(hasRating ? View.VISIBLE : View.GONE);
        h.rating.setText(hasRating ? "★ " + v.rating + "/10" : "");
        h.genres.setText(hasGenres ? v.genres : "");
        h.fallback.setText(v.title);
        h.card.setContentDescription(v.title);
        if (getItemViewType(position) == 1) Thumbnails.loadBanner(v.thumb, h.thumb);
        else Thumbnails.load(v.thumb, h.thumb);
        h.progress.setVisibility(v.positionMs > 0 && v.durationMs > 0 ? View.VISIBLE : View.GONE);
        h.progress.setProgress(v.durationMs > 0 ? (int) Math.min(1000, 1000.0 * v.positionMs / v.durationMs) : 0);
        h.card.setOnClickListener(view -> open(v));
    }
    @Override public void onViewRecycled(@NonNull Holder h) {
        h.card.animate().cancel();
        Thumbnails.clear(h.thumb);
        super.onViewRecycled(h);
    }
    @Override public int getItemCount() { return items.size(); }

    private void open(FeedLoader.Video v) {
        if (CinemanaResume.PACKAGE.equals(v.pkg) && v.url != null && v.url.startsWith("cinemana-banner://")) {
            try { CinemanaResume.openBanner(ctx, v); } catch (Exception e) { unavailable(v); }
            return;
        }
        if (v.url == null || v.url.trim().isEmpty()) { unavailable(v); return; }
        String pkg = v.pkg != null && !v.pkg.trim().isEmpty() ? v.pkg : ctx.getString(R.string.video_player_package).trim();
        try {
            Intent intent = Intent.parseUri(v.url, Intent.URI_INTENT_SCHEME);
            intent.setSelector(null);
            intent.setClipData(null);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!pkg.isEmpty()) {
                if (intent.getComponent() != null && !pkg.equals(intent.getComponent().getPackageName())) {
                    throw new IllegalArgumentException("Playback component is not owned by publisher");
                }
                intent.setPackage(pkg);
            }
            if (intent.getAction() == null) intent.setAction(Intent.ACTION_VIEW);
            ctx.startActivity(intent);
            PlaybackRecovery.launched(ctx, intent, v.title);
            return;
        } catch (Exception e) {
            android.util.Log.w("TvHome", "Playback link unavailable for " + pkg);
        }
        // External fallback is only for configured feeds. Provider links stay in their owning app.
        if (v.pkg == null) {
            try { Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(v.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(fallback); PlaybackRecovery.launched(ctx, fallback, v.title); return; }
            catch (Exception ignored) { }
        }
        unavailable(v);
    }
    private void unavailable(FeedLoader.Video v) {
        Toast.makeText(ctx, ctx.getString(R.string.cant_open, v.title), Toast.LENGTH_SHORT).show();
    }
    final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView thumb;
        final TextView title, fallback, rating, genres;
        final View details;
        final ProgressBar progress;
        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.card); thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.title); fallback = v.findViewById(R.id.artFallback);
            progress = v.findViewById(R.id.watchProgress);
            details = v.findViewById(R.id.bannerDetails);
            rating = v.findViewById(R.id.bannerRating); genres = v.findViewById(R.id.bannerGenres);
            float radius = 12 * v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) { outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius); }
            });
            card.setClipToOutline(true);
            card.setOnKeyListener((view, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return false;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    focusAt(pos + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1));
                    return true;
                }
                return (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) && listener.onKey(rowIndex, pos, key);
            });
            card.setOnFocusChangeListener((view, focused) -> {
                view.animate().scaleX(focused ? AppAdapter.FOCUS_SCALE : 1f).scaleY(focused ? AppAdapter.FOCUS_SCALE : 1f)
                    .setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                title.setTextColor(ctx.getColor(focused ? R.color.home_accent : R.color.home_text));
                int pos = getBindingAdapterPosition();
                if (focused && pos != RecyclerView.NO_POSITION) listener.onVideoFocused(card, itemView, rowIndex, pos);
            });
        }
    }
}
