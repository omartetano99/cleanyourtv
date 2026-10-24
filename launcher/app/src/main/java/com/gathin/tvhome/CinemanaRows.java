package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Public Watch now catalogue used by Cinemana 4K. No account credentials or private app data. */
final class CinemanaRows {
    static final String KEY = "cinemana|watch-now";
    private static final String ENDPOINT = "https://cinemana.shabakaty.com/api/android/banner/level/0";
    private final Context context;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private boolean loading;
    private FeedLoader.Callback latestCallback;
    CinemanaRows(Context c) { context = c.getApplicationContext(); }
    void close() { closed = true; io.shutdownNow(); main.removeCallbacksAndMessages(null); }
    void load(FeedLoader.Callback callback) {
        latestCallback = callback; if (closed || loading) return; loading = true;
        io.execute(() -> {
            List<FeedLoader.Row> rows = new ArrayList<>();
            boolean installed = false;
            try {
                context.getPackageManager().getPackageInfo(CinemanaResume.PACKAGE, 0);
                installed = true;
                FeedLoader.Row row = parse(read());
                row.title = context.getString(R.string.cinemana_watch_now);
                if (!row.videos.isEmpty()) rows.add(row);
            } catch (Exception e) { android.util.Log.w("TvHome", "Cinemana banners unavailable: " + e.getClass().getSimpleName()); }
            if (installed) rows.add(CinemanaHistory.row(context, rows));
            List<FeedLoader.Row> first = new ArrayList<>(rows);
            main.post(() -> { if (!closed) latestCallback.onRows(first); });
            if (installed && !closed) {
                CinemanaHistory.enrich(context);
                rows.removeIf(row -> CinemanaHistory.KEY.equals(row.key));
                rows.add(CinemanaHistory.row(context, rows));
            }
            main.post(() -> { loading = false; if (!closed) latestCallback.onRows(rows); });
        });
    }
    static FeedLoader.Row parse(String json) throws Exception {
        JSONArray array = new JSONArray(json);
        FeedLoader.Row row = new FeedLoader.Row(); row.key = KEY;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < array.length() && row.videos.size() < 100; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String id = value(item, "nb"), title = value(item, "ar_title");
            if (title.isEmpty()) title = value(item, "en_title");
            String art = value(item, "imgObjUrl");
            if (!id.matches("[0-9]+") || title.isEmpty() || !seen.add(id)) continue;
            FeedLoader.Video video = new FeedLoader.Video();
            video.pkg = CinemanaResume.PACKAGE; video.title = title;
            video.url = "cinemana-banner://video/" + id;
            video.bannerIndex = i;
            String rating = value(item, "stars");
            try {
                double score = Double.parseDouble(rating);
                if (score > 0 && score <= 10) video.rating = rating;
            } catch (NumberFormatException ignored) { }
            JSONArray categories = item.optJSONArray("categories");
            List<String> genres = new ArrayList<>();
            if (categories != null) for (int g = 0; g < categories.length(); g++) {
                JSONObject category = categories.optJSONObject(g);
                if (category == null) continue;
                String genre = value(category, "ar_title");
                if (genre.isEmpty()) genre = value(category, "en_title");
                if (!genre.isEmpty() && !genres.contains(genre)) genres.add(genre);
            }
            video.genres = String.join(" · ", genres);
            video.thumb = art.startsWith("https://") ? art : null;
            row.videos.add(video);
        }
        return row;
    }
    private static String value(JSONObject item, String key) { return item.isNull(key) ? "" : item.optString(key, "").trim(); }
    private String read() throws Exception {
        File cache = new File(context.getCacheDir(), "cinemana-banners.json");
        if (cache.isFile() && System.currentTimeMillis() - cache.lastModified() < 15 * 60 * 1000)
            try (InputStream in = new FileInputStream(cache)) { return bytes(in); }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(7000);
            connection.setRequestProperty("User-Agent", "TvHome/2.4");
            String result;
            try (InputStream in = connection.getInputStream()) { result = bytes(in); }
            parse(result); // Do not replace the last valid cache with an error page.
            File temporary = new File(context.getCacheDir(), "cinemana-banners.tmp");
            try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(result.getBytes(StandardCharsets.UTF_8)); }
            if (!temporary.renameTo(cache)) temporary.delete();
            return result;
        } catch (Exception e) {
            if (cache.isFile()) try (InputStream in = new FileInputStream(cache)) { return bytes(in); }
            throw e;
        } finally { if (connection != null) connection.disconnect(); }
    }
    private static String bytes(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384]; int count;
        while ((count = in.read(buffer)) != -1) {
            if (out.size() + count > 4 * 1024 * 1024) throw new java.io.IOException("Banner response too large");
            out.write(buffer, 0, count);
        }
        return out.toString("UTF-8");
    }
}
