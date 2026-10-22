package com.gathin.tvhome;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Run with adb shell am instrument -w com.gathin.tvhome.test/com.gathin.tvhome.LauncherRegression. */
public final class LauncherRegression extends Instrumentation {
    private MainActivity activity;
    private final List<String> passed = new ArrayList<>();
    private Set<String> hidden;
    private String savedOrder;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            recoveryChecks();
            rowChecks();
            Intent intent = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity = (MainActivity) startActivitySync(intent);
            waitForIdleSync(); SystemClock.sleep(1500);
            ui(() -> {
                savedOrder = activity.getSharedPreferences("tvhome", 0).getString("row_order", null);
                activity.getSharedPreferences("tvhome", 0).edit().remove("row_order").commit();
                set("cinemanaRowData", null);
                hidden = new HashSet<>(activity.getSharedPreferences("tvhome", 0).getStringSet("hidden_rows", new HashSet<>()));
                activity.getSharedPreferences("tvhome", 0).edit().remove("hidden_rows").commit();
                ((Handler) get("handler")).removeCallbacksAndMessages(null);
                activity.getContentResolver().unregisterContentObserver((android.database.ContentObserver) get("contentObserver"));
                set("observerRegistered", false);
                set("refreshGeneration", (int) get("refreshGeneration") + 1);
                set("providerRowData", fixture()); set("feedRowData", new ArrayList<FeedLoader.Row>());
                invoke("renderContentRows");
            });
            settle();
            ui(() -> {
                LinearLayout rows = activity.findViewById(R.id.videoRows);
                check(rows.getChildCount() == 8, "An empty shelf must not suppress later shelves; all eight must render");
                check("Shelf 0".contentEquals(((TextView) rows.getChildAt(0).findViewById(R.id.rowCaption)).getText()), "First nonempty shelf should be retained");
            }); passed.add("empty shelf + more than six shelves");
            ui(() -> adapters().get(0).focusAt(42)); awaitFocus("Video 0:42");
            ui(() -> check("Video 0:42".contentEquals(activity.getCurrentFocus().getContentDescription()), "Offscreen card 42 should gain focus"));
            passed.add("offscreen horizontal focus");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); awaitFocus("Video 1:2");
            ui(() -> check("Video 1:2".contentEquals(activity.getCurrentFocus().getContentDescription()), "Moving to a shorter row must clamp lane"));
            passed.add("vertical navigation to shorter row");
            final View[] original = new View[1];
            ui(() -> { original[0] = activity.getCurrentFocus(); invoke("renderContentRows"); }); settle();
            ui(() -> check(original[0] == activity.getCurrentFocus(), "Unchanged refresh should retain the actual focused view"));
            passed.add("unchanged refresh retains focus");
            ui(() -> {
                List<FeedLoader.Row> rows = fixture();
                RowOrder.move(activity.getSharedPreferences("tvhome", 0), rows, "test|1", 0);
                invoke("renderContentRows");
            }); awaitFocus("Video 1:2");
            ui(() -> {
                LinearLayout shelves = activity.findViewById(R.id.videoRows);
                check("Shelf 1".contentEquals(((TextView) shelves.getChildAt(0).findViewById(R.id.rowCaption)).getText()), "Moved shelf must render first");
                activity.getSharedPreferences("tvhome", 0).edit().remove("row_order").commit();
                invoke("renderContentRows");
            }); awaitFocus("Video 1:2");
            passed.add("row reorder retains selected video and updates screen order");
            ui(() -> {
                List<FeedLoader.Row> rows = fixture();
                FeedLoader.Video first = rows.get(2).videos.remove(0); rows.get(2).videos.add(first);
                set("providerRowData", rows); invoke("renderContentRows");
            }); settle();
            ui(() -> check("Video 1:2".contentEquals(activity.getCurrentFocus().getContentDescription()), "Changed refresh should follow video identity"));
            passed.add("reordered refresh restores selected video");
            ui(() -> {
                Set<String> hide = new HashSet<>(); for (int i = 0; i < 8; i++) hide.add("test|" + i);
                activity.getSharedPreferences("tvhome", 0).edit().putStringSet("hidden_rows", hide).commit(); invoke("renderContentRows");
            }); settle();
            ui(() -> {
                check(((LinearLayout) activity.findViewById(R.id.videoRows)).getChildCount() == 0, "All hidden shelves must disappear");
                check(activity.findViewById(R.id.grid).hasFocus(), "Removing the focused shelf must return focus to apps");
                check(activity.findViewById(R.id.contentStatus).getVisibility() == View.VISIBLE, "Empty state must be visible");
            }); passed.add("hide all rows retains usable app navigation");
            ui(() -> {
                activity.getSharedPreferences("tvhome", 0).edit().remove("hidden_rows").commit();
                FeedLoader.Row featured = new FeedLoader.Row(); featured.key = CinemanaRows.KEY; featured.title = "Cinemana";
                FeedLoader.Video banner = new FeedLoader.Video(); banner.title = "Featured movie";
                banner.pkg = CinemanaResume.PACKAGE; banner.bannerIndex = 0; banner.url = "cinemana-banner://video/42";
                banner.rating = "8.1"; banner.genres = "Drama · Crime"; featured.videos.add(banner);
                set("cinemanaRowData", java.util.Collections.singletonList(featured));
                set("providerRowData", fixture()); invoke("renderContentRows");
                adapters().get(0).focusAt(0);
            }); awaitFocus("Featured movie");
            ui(() -> {
                View featured = activity.findViewById(R.id.cinemanaShelf);
                check(featured.getBottom() <= activity.findViewById(R.id.appsCaption).getTop(), "Cinemana shelf must be above the app dock");
                check("★ 8.1/10".contentEquals(((TextView) featured.findViewById(R.id.bannerRating)).getText()), "Banner rating must be rendered");
                check("Drama · Crime".contentEquals(((TextView) featured.findViewById(R.id.bannerGenres)).getText()), "Banner genres must be rendered");
            }); passed.add("Cinemana above dock with rating and genres");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); settle();
            ui(() -> check(activity.findViewById(R.id.grid).hasFocus(), "Down from featured banners must focus apps"));
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP); awaitFocus("Featured movie");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP); settle();
            ui(() -> check(activity.findViewById(R.id.btnInputs).isFocused(), "Up from banners must focus header"));
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); awaitFocus("Featured movie");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); settle();
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); settle();
            ui(() -> check(!activity.findViewById(R.id.cinemanaShelf).hasFocus() && !activity.findViewById(R.id.grid).hasFocus(), "Down from apps must enter the remaining shelves"));
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP); settle();
            ui(() -> check(activity.findViewById(R.id.grid).hasFocus(), "Up from first regular shelf must return to apps"));
            passed.add("remote navigation follows banner, dock and regular shelf order");
            ui(() -> {
                ImageView image = new ImageView(activity);
                Field f = Thumbnails.class.getDeclaredField("CACHE"); f.setAccessible(true);
                android.util.LruCache<String, Bitmap> cache = (android.util.LruCache<String, Bitmap>) f.get(null);
                cache.put("test-cache", Bitmap.createBitmap(2, 2, Bitmap.Config.RGB_565));
                Object stale = new Object(); image.setTag(stale);
                Thumbnails.load("test-cache", image);
                check(image.getTag() != stale && image.getDrawable() != null, "Cache hit must invalidate old thumbnail token");
                Thumbnails.load(null, image);
                check(image.getTag() == null && image.getDrawable() == null, "Empty artwork must invalidate pending thumbnail and clear drawable");
                cache.remove("test-cache");
            }); passed.add("thumbnail cache reuse and missing artwork");
            Method parser = FeedLoader.class.getDeclaredMethod("parse", java.io.InputStream.class); parser.setAccessible(true);
            String rss = "<rss><channel><title>Channel</title><item><title>A</title><link>https://example.invalid/a</link></item><item><title>Duplicate</title><link>https://example.invalid/a</link></item></channel></rss>";
            List<FeedLoader.Video> parsed = (List<FeedLoader.Video>) parser.invoke(null, new ByteArrayInputStream(rss.getBytes(StandardCharsets.UTF_8)));
            check(parsed.size() == 1 && "https://example.invalid/a".equals(parsed.get(0).url), "RSS text links and duplicates");
            String atom = "<feed xmlns='http://www.w3.org/2005/Atom' xmlns:yt='http://www.youtube.com/xml/schemas/2015' xmlns:media='http://search.yahoo.com/mrss/'><entry><yt:videoId>abc123</yt:videoId><title>Title</title><media:group><media:thumbnail url='https://example.invalid/art.jpg'/></media:group></entry></feed>";
            parsed = (List<FeedLoader.Video>) parser.invoke(null, new ByteArrayInputStream(atom.getBytes(StandardCharsets.UTF_8)));
            check(parsed.size() == 1 && "https://www.youtube.com/watch?v=abc123".equals(parsed.get(0).url) && parsed.get(0).thumb != null, "Namespaced Atom IDs must not include preceding text");
            passed.add("RSS and namespaced Atom parsing");
            result.putString("stream", "\nPASS " + passed.size() + " regression checks\n" + String.join("\n", passed) + "\n");
            restore(); finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("stream", "\nFAILED after " + passed + "\n" + android.util.Log.getStackTraceString(e));
            restore(); finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void recoveryChecks() throws Exception {
        android.content.Context isolated = new android.content.ContextWrapper(getTargetContext()) {
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("test_" + name, mode);
            }
        };
        android.content.SharedPreferences p = PlaybackRecovery.prefs(isolated);
        boolean previousHome = PlaybackRecovery.homeVisible;
        try {
            p.edit().clear().commit();
            PlaybackRecovery.initialize(isolated);
            check(!PlaybackRecovery.due(isolated), "Fresh install must not auto-open an app");
            Intent launch = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=abcdefghijk"))
                .setPackage("org.smarttube.stable");
            PlaybackRecovery.launched(isolated, launch, "Example video");
            check(!PlaybackRecovery.due(isolated), "Ordinary return in the same boot must not autoplay");
            p.edit().putInt("boot", PlaybackRecovery.boot(isolated) - 1).commit();
            check(PlaybackRecovery.due(isolated), "New boot must recover an active app");
            PlaybackRecovery.consumed(isolated);
            check(!PlaybackRecovery.due(isolated), "Boot recovery must be attempted only once");
            PlaybackRecovery.screenOff(isolated);
            check(PlaybackRecovery.due(isolated), "Standby must schedule recovery");
            PlaybackRecovery.stayHome(isolated);
            check(!PlaybackRecovery.due(isolated), "Home cancels standby recovery");
            p.edit().putInt("boot", PlaybackRecovery.boot(isolated) - 1).commit();
            check(!PlaybackRecovery.due(isolated), "Power cycling from Home must stay on Home");
            passed.add("boot, standby, Home cancellation and one-shot recovery");
            PlaybackRecovery.launched(isolated, launch, "Example video");
            android.media.MediaMetadata metadata = new android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID, "lmnopqrstuv")
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "Next video")
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, 600000).build();
            android.media.session.PlaybackState state = new android.media.session.PlaybackState.Builder()
                .setState(android.media.session.PlaybackState.STATE_PAUSED, 123000, 0f).build();
            PlaybackRecovery.capture(isolated, "unrelated.app", metadata, state);
            check(!p.contains("media_id"), "Other apps cannot overwrite the recovery record");
            PlaybackRecovery.capture(isolated, "org.smarttube.stable", metadata, state);
            Intent recovered = PlaybackRecovery.intent(isolated);
            check("lmnopqrstuv".equals(recovered.getData().getQueryParameter("v")), "In-app video changes must replace the original link");
            check("123".equals(recovered.getData().getQueryParameter("t")), "Resume URL must include the saved timestamp");
            check("org.smarttube.stable".equals(recovered.getPackage()), "Resume link must stay in the original app");
            passed.add("latest media identity and SmartTube resume timestamp");
            PlaybackRecovery.launched(isolated, new Intent(Intent.ACTION_MAIN).setPackage(CinemanaResume.PACKAGE), "Cinemana");
            p.edit().putString("cinemana_title", "Test movie").putLong("position", 123000).commit();
            CinemanaResume.begin(isolated);
            check(CinemanaResume.pending(isolated), "Cinemana restoration survives service binding order");
            check(p.getLong("cinemana_restore_position", 0) == 123000, "Saved timestamp is frozen before reopening player");
            PlaybackRecovery.capture(isolated, CinemanaResume.PACKAGE, metadata,
                new android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PLAYING, 0, 1f).build());
            check(p.getLong("position", 0) == 123000, "Fresh player must not overwrite saved position before seek");
            check(!CinemanaResume.sameTitle("Test movie", "Different movie"), "Never auto-open a different History title");
            check(!CinemanaResume.sameTitle("", ""), "Empty titles cannot identify a video");
            PlaybackRecovery.stayHome(isolated);
            check(!CinemanaResume.pending(isolated), "Home cancels Cinemana navigation");
            p.edit().putLong("cinemana_restore_until", System.currentTimeMillis() - 1).commit();
            check(!CinemanaResume.pending(isolated), "Expired restores stop controlling the app");
            passed.add("Cinemana timestamp freeze, identity checks, cancellation and timeout");
        } finally { p.edit().clear().commit(); PlaybackRecovery.homeVisible = previousHome; }
    }

    private void rowChecks() throws Exception {
        android.content.SharedPreferences prefs = getTargetContext().getSharedPreferences("test_row_order", 0);
        prefs.edit().clear().commit();
        try {
            List<FeedLoader.Row> rows = fixture();
            RowOrder.move(prefs, rows, "test|7", 0);
            RowOrder.sort(rows, prefs);
            check("test|7".equals(rows.get(0).key), "Move to first persists order by key");
            List<FeedLoader.Row> refreshed = fixture();
            refreshed.removeIf(r -> "test|3".equals(r.key));
            RowOrder.sort(refreshed, prefs);
            check("test|7".equals(refreshed.get(0).key), "Missing provider cannot reset saved order");
            RowOrder.move(prefs, refreshed, "test|7", 2);
            RowOrder.sort(refreshed, prefs);
            check("test|7".equals(refreshed.get(2).key), "Move down persists new position");
            check(RowOrder.read(prefs).contains("test|3"), "Temporarily absent row order is retained");
            prefs.edit().putString("row_order", "invalid").commit();
            check(RowOrder.read(prefs).isEmpty(), "Corrupt order falls back safely");
            passed.add("persistent row order, missing providers and malformed preferences");
            FeedLoader.Row banners = CinemanaRows.parse("[{\"nb\":\"42\",\"ar_title\":\"عنوان\",\"imgObjUrl\":\"https://example.invalid/banner.jpg\"},{\"nb\":\"42\",\"ar_title\":\"Duplicate\"},{\"nb\":\"43\",\"en_title\":\"English title\"},{\"nb\":\"bad\",\"ar_title\":\"Invalid\"}]");
            check(CinemanaRows.KEY.equals(banners.key) && banners.videos.size() == 2, "Banner IDs deduplicate and malformed entries are skipped");
            check("عنوان".equals(banners.videos.get(0).title) && banners.videos.get(1).bannerIndex == 2, "Original catalogue index and Arabic title survive parsing");
            check(CinemanaResume.PACKAGE.equals(banners.videos.get(0).pkg), "Banners remain owned by Cinemana 4K");
            passed.add("Cinemana live banner parsing and original catalogue positions");
            FeedLoader.Row detail = CinemanaRows.parse("[{\"nb\":\"1\",\"ar_title\":\"Movie\",\"stars\":\"7.8\",\"categories\":[{\"ar_title\":\"دراما\",\"en_title\":\"Drama\"},{\"en_title\":\"Crime\"}]},{\"nb\":\"2\",\"ar_title\":\"Unknown\",\"stars\":\"0\"}]");
            check("7.8".equals(detail.videos.get(0).rating), "Use the catalogue star rating");
            check("دراما · Crime".equals(detail.videos.get(0).genres), "Preserve genres with English fallback");
            check(detail.videos.get(1).rating == null && detail.videos.get(1).genres.isEmpty(), "Missing ratings and genres must not be invented");
            passed.add("Cinemana rating and genre parsing with missing data");
        } finally { prefs.edit().clear().commit(); }
    }

    private void restore() {
        if (activity == null) return;
        try { ui(() -> {
            activity.getSharedPreferences("tvhome", 0).edit().putString("row_order", savedOrder).commit();
            if (hidden != null) activity.getSharedPreferences("tvhome", 0).edit().putStringSet("hidden_rows", hidden).commit();
            set("focusZone", "apps"); set("pendingVideoRestore", false); invoke("refreshContentRows");
        }); } catch (Throwable ignored) { }
    }
    private List<FeedLoader.Row> fixture() {
        List<FeedLoader.Row> rows = new ArrayList<>();
        FeedLoader.Row empty = new FeedLoader.Row(); empty.key = "test|empty"; empty.title = "Empty"; rows.add(empty);
        for (int i = 0; i < 8; i++) {
            FeedLoader.Row row = new FeedLoader.Row(); row.key = "test|" + i; row.title = "Shelf " + i;
            for (int j = 0; j < (i == 0 ? 50 : 3); j++) {
                FeedLoader.Video v = new FeedLoader.Video(); v.title = "Video " + i + ":" + j;
                v.url = "https://example.invalid/" + i + "/" + j; row.videos.add(v);
            }
            rows.add(row);
        }
        return rows;
    }
    private List<VideoRowAdapter> adapters() throws Exception { return (List<VideoRowAdapter>) get("videoRowAdapters"); }
    private Object get(String name) throws Exception { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    private void set(String name, Object value) throws Exception { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity, value); }
    private void invoke(String name) throws Exception { Method m = MainActivity.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(activity); }
    private interface Task { void run() throws Exception; }
    private void ui(Task task) throws Exception {
        Throwable[] failure = new Throwable[1]; runOnMainSync(() -> { try { task.run(); } catch (Throwable t) { failure[0] = t; } });
        if (failure[0] != null) throw new Exception(failure[0]);
    }
    private void awaitFocus(String description) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 4000;
        String[] current = new String[1];
        do {
            waitForIdleSync();
            ui(() -> current[0] = activity.getCurrentFocus() == null ? "none" : String.valueOf(activity.getCurrentFocus().getContentDescription()));
            if (description.equals(current[0])) return;
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Expected focus " + description + ", got " + current[0]);
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(400); waitForIdleSync(); }
    private static void check(boolean okay, String message) { if (!okay) throw new AssertionError(message); }
}
