package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/** Adapter for Cinemana's private player: match visible History title, then use MediaSession seek.
 * Does not read other apps, notifications, credentials, or Cinemana's private storage. */
public final class CinemanaResume extends AccessibilityService {
    static final String PACKAGE = "com.shabakaty.cbox.cinemanb";
    private static CinemanaResume instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String screen = "", detailTitle = "", detailYear = "";
    private long lastAction;
    private boolean openedHistory;
    private boolean bannerSection, bannerJumped, bannerPositioned;
    private int bannerSteps;
    static boolean openBanner(Context context, FeedLoader.Video video) {
        if (instance == null) {
            android.widget.Toast.makeText(context, R.string.cinemana_helper_needed, android.widget.Toast.LENGTH_LONG).show();
            context.startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            return false;
        }
        android.content.Intent intent = context.getPackageManager().getLeanbackLaunchIntentForPackage(PACKAGE);
        if (intent == null) return false;
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PlaybackRecovery.launched(context, intent, video.title);
        PlaybackRecovery.prefs(context).edit().putString("cinemana_banner_title", video.title)
            .putInt("cinemana_banner_index", video.bannerIndex)
            .putLong("cinemana_banner_until", System.currentTimeMillis() + 60000).commit();
        instance.bannerSection = false; instance.bannerJumped = false; instance.bannerPositioned = false; instance.bannerSteps = 0; instance.screen = "";
        // Wait for the new Home window; accessibility can briefly return the previous detail page.
        instance.lastAction = SystemClock.uptimeMillis() + 1000;
        context.startActivity(intent);
        instance.schedule(400);
        android.widget.Toast.makeText(context, context.getString(R.string.cinemana_opening, video.title), android.widget.Toast.LENGTH_SHORT).show();
        return true;
    }
    private static boolean bannerPending(Context context) {
        return PlaybackRecovery.prefs(context).getLong("cinemana_banner_until", 0) > System.currentTimeMillis();
    }
    private boolean scheduled;
    private final Runnable tick = () -> { scheduled = false; inspect(); };
    private void schedule(long delay) {
        if (!scheduled) { scheduled = true; handler.postDelayed(tick, delay); }
    }

    static boolean pending(Context c) {
        return PlaybackRecovery.prefs(c).getLong("cinemana_restore_until", 0) > System.currentTimeMillis();
    }
    static void begin(Context c) {
        SharedPreferences p = PlaybackRecovery.prefs(c);
        if (!PACKAGE.equals(p.getString("package", "")) || p.getString("cinemana_title", "").isEmpty()) return;
        p.edit().putLong("cinemana_restore_until", System.currentTimeMillis() + 60000)
            .putLong("cinemana_restore_position", p.getLong("position", 0)).commit();
        if (instance == null) return; // System may bind accessibility after the home activity on boot.
        instance.openedHistory = false;
        instance.lastAction = 0;
        instance.schedule(500);
    }
    static void cancel(Context c) {
        PlaybackRecovery.prefs(c).edit().remove("cinemana_restore_until").remove("cinemana_restore_position").remove("cinemana_banner_until").commit();
    }
    static boolean sameTitle(String expected, CharSequence actual) {
        return actual != null && !expected.isEmpty() && expected.trim().equals(actual.toString().trim());
    }
    @Override protected void onServiceConnected() { instance = this; schedule(0); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!PACKAGE.contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getClassName() != null) {
            String name = event.getClassName().toString();
            if (name.startsWith("com.shabakaty.")) screen = name;
        }
        schedule(150);
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && (pending(this) || bannerPending(this))) {
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_HOME || key == KeyEvent.KEYCODE_DPAD_CENTER
                || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) cancel(this);
        }
        return false;
    }
    private void inspect() {
        long bannerDeadline = PlaybackRecovery.prefs(this).getLong("cinemana_banner_until", 0);
        if (bannerDeadline > 0 && bannerDeadline <= System.currentTimeMillis()) {
            cancel(this);
            android.widget.Toast.makeText(this, R.string.cinemana_banner_unavailable, android.widget.Toast.LENGTH_LONG).show();
        }
        boolean restoring = pending(this);
        if (restoring || bannerPending(this)) schedule(450);
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            if (!PACKAGE.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) return;
            SharedPreferences p = PlaybackRecovery.prefs(this);
            if (!p.getBoolean("active", false) || !PACKAGE.equals(p.getString("package", ""))) return;
            if (bannerPending(this)) { navigateBanner(root, p); return; }
            String title = text(root, "detail_movie_title");
            if (!title.isEmpty() && !restoring) {
                detailTitle = title;
                detailYear = text(root, "categoryNameTV");
            }
            String playerTitle = text(root, "lb_details_description_title");
            boolean player = screen.endsWith(".PlayerActivity") || !playerTitle.isEmpty();
            if (player) {
                if (restoring) {
                    // Never seek an unidentified/wrong video, even if History changed underneath us.
                    if (sameTitle(p.getString("cinemana_title", ""), playerTitle)) restorePosition(p);
                } else if (!playerTitle.isEmpty()) {
                    boolean changed = !sameTitle(p.getString("cinemana_title", ""), playerTitle);
                    SharedPreferences.Editor edit = p.edit().putString("cinemana_title", playerTitle).putString("title", playerTitle);
                    if (sameTitle(detailTitle, playerTitle)) edit.putString("cinemana_year", detailYear);
                    else if (changed) edit.remove("cinemana_year");
                    if (changed) edit.putLong("position", 0);
                    edit.commit();
                }
                return;
            }
            if (!restoring || SystemClock.uptimeMillis() - lastAction < 900) return;
            String expected = p.getString("cinemana_title", "");
            if (!title.isEmpty()) {
                if (sameTitle(expected, title)) clickId(root, "btn_play");
                else stop("Cinemana opened a different title");
                return;
            }
            // Navigate by semantic resource IDs and exact text, never screen coordinates or first-card guesses.
            if (clickText(root, "nav_label", "السجل") || clickText(root, "nav_label", "History")) {
                openedHistory = true; return;
            }
            if (!openedHistory && screen.endsWith(".HomeActivity") && !text(root, "movie_name").isEmpty()) {
                // Leanback can start with headers collapsed. Reveal the navigation before selecting History.
                performGlobalAction(GLOBAL_ACTION_BACK); lastAction = SystemClock.uptimeMillis(); return;
            }
            if (openedHistory || sameTitle("السجل", text(root, "title_text")) || sameTitle("History", text(root, "title_text"))) {
                List<AccessibilityNodeInfo> cards = root.findAccessibilityNodeInfosByViewId(PACKAGE + ":id/card_title");
                AccessibilityNodeInfo match = null;
                int matches = 0;
                for (AccessibilityNodeInfo card : cards) {
                    if (sameTitle(expected, card.getText())) { match = card; matches++; }
                }
                if (matches == 1) click(match);
                else if (matches > 1) stop("Cinemana History has ambiguous titles");
                for (AccessibilityNodeInfo card : cards) card.recycle();
            }
        } finally { root.recycle(); }
    }
    private void navigateBanner(AccessibilityNodeInfo root, SharedPreferences prefs) {
        if (SystemClock.uptimeMillis() - lastAction < 1000) return;
        String target = prefs.getString("cinemana_banner_title", "");
        String detail = text(root, "detail_movie_title");
        if (!detail.isEmpty() && sameTitle(target, detail)) {
            cancel(this); android.util.Log.i("TvHomeResume", "Opened selected Cinemana banner"); return;
        }
        // A series opens its episode selector, rather than a movie detail screen.
        if (screen.endsWith(".SeriesActivity") && bannerSection) { cancel(this); return; }
        if (clickText(root, "nav_label", "شاهد الآن") || clickText(root, "nav_label", "Watch now")) {
            bannerSection = true; return;
        }
        String current = text(root, "movie_name");
        List<AccessibilityNodeInfo> bannerRows = root.findAccessibilityNodeInfosByViewId(PACKAGE + ":id/row_content");
        boolean hasBannerRow = !bannerRows.isEmpty();
        for (AccessibilityNodeInfo row : bannerRows) row.recycle();
        if (!current.isEmpty() || (hasBannerRow && (bannerSection || sameTitle("شاهد الآن", text(root, "row_header"))))) {
            bannerSection = true;
            if (sameTitle(target, current)) { clickText(root, "movie_name", target); return; }
            List<AccessibilityNodeInfo> rows = root.findAccessibilityNodeInfosByViewId(PACKAGE + ":id/row_content");
            for (AccessibilityNodeInfo row : rows) {
                if (!bannerJumped) {
                    android.os.Bundle args = new android.os.Bundle();
                    args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, 0);
                    args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, Math.max(0, prefs.getInt("cinemana_banner_index", 0)));
                    bannerJumped = true;
                    boolean jumped = row.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(), args);
                    android.util.Log.i("TvHomeResume", "Banner jump " + prefs.getInt("cinemana_banner_index", 0) + " accepted=" + jumped);
                    if (jumped) { row.recycle(); lastAction = SystemClock.uptimeMillis(); return; }
                }
                // Focus the indexed banner once the RecyclerView has laid it out.
                int index = prefs.getInt("cinemana_banner_index", -1);
                for (int i = 0; i < row.getChildCount(); i++) {
                    AccessibilityNodeInfo child = row.getChild(i);
                    if (child == null) continue;
                    AccessibilityNodeInfo.CollectionItemInfo item = child.getCollectionItemInfo();
                    if (!bannerPositioned && item != null && item.getColumnIndex() == index) {
                        bannerPositioned = true;
                        child.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        child.recycle(); row.recycle(); lastAction = SystemClock.uptimeMillis(); return;
                    }
                    child.recycle();
                }
                row.recycle();
            }
            if (++bannerSteps > 100) { stop("Requested Cinemana banner no longer in catalogue"); return; }
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null) {
                AccessibilityNodeInfo next = focused.focusSearch(android.view.View.FOCUS_LEFT);
                if (next != null) { next.performAction(AccessibilityNodeInfo.ACTION_FOCUS); next.recycle(); }
                focused.recycle();
            }
            lastAction = SystemClock.uptimeMillis();
            return;
        }
        // The explicit Home intent clears old player/detail screens. Wait for its content;
        // issuing Back during that transition can accidentally return to the launcher.
    }
    private void restorePosition(SharedPreferences p) {
        try {
            MediaSessionManager manager = getSystemService(MediaSessionManager.class);
            for (MediaController controller : manager.getActiveSessions(new ComponentName(this, PlaybackMonitor.class))) {
                if (!PACKAGE.equals(controller.getPackageName())) continue;
                PlaybackState state = controller.getPlaybackState();
                MediaMetadata metadata = controller.getMetadata();
                long duration = metadata == null ? 0 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (state == null || duration <= 0 || (state.getState() != PlaybackState.STATE_PLAYING && state.getState() != PlaybackState.STATE_PAUSED)) continue;
                if ((state.getActions() & PlaybackState.ACTION_SEEK_TO) == 0) { stop("Cinemana player cannot seek"); return; }
                long position = Math.max(0, Math.min(p.getLong("cinemana_restore_position", 0), duration - 1000));
                controller.getTransportControls().seekTo(position);
                if (p.getBoolean("playing", true)) controller.getTransportControls().play();
                else controller.getTransportControls().pause();
                cancel(this);
                android.util.Log.i("TvHomeResume", "Cinemana video restored at " + position + "ms");
                return;
            }
        } catch (SecurityException e) { stop("Media access unavailable"); }
    }
    private void stop(String reason) {
        boolean banner = bannerPending(this);
        cancel(this);
        if (banner) android.widget.Toast.makeText(this, R.string.cinemana_banner_unavailable, android.widget.Toast.LENGTH_LONG).show();
        android.util.Log.w("TvHomeResume", reason);
    }
    private String text(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(PACKAGE + ":id/" + id);
        String value = "";
        for (AccessibilityNodeInfo node : nodes) {
            if (value.isEmpty() && node.isVisibleToUser() && node.getText() != null) value = node.getText().toString();
            node.recycle();
        }
        return value;
    }
    private boolean clickId(AccessibilityNodeInfo root, String id) { return clickText(root, id, null); }
    private boolean clickText(AccessibilityNodeInfo root, String id, String expected) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(PACKAGE + ":id/" + id);
        boolean clicked = false;
        for (AccessibilityNodeInfo node : nodes) {
            if (!clicked && node.isVisibleToUser() && (expected == null || sameTitle(expected, node.getText()))) clicked = click(node);
            node.recycle();
        }
        return clicked;
    }
    private boolean click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        for (int i = 0; current != null && i < 8; i++) {
            if (current.isClickable() && current.isEnabled()) {
                // Leanback chooses the row on focus; clicking an unfocused header opens the old row.
                int action = current.isFocusable() && !current.isFocused()
                    ? AccessibilityNodeInfo.ACTION_FOCUS : AccessibilityNodeInfo.ACTION_CLICK;
                boolean result = current.performAction(action);
                current.recycle();
                if (result) lastAction = SystemClock.uptimeMillis();
                return result;
            }
            AccessibilityNodeInfo parent = current.getParent(); current.recycle(); current = parent;
        }
        if (current != null) current.recycle();
        return false;
    }
    @Override public void onInterrupt() { cancel(this); }
    @Override public void onDestroy() { if (instance == this) instance = null; handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
