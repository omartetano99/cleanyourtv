package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches and parses RSS feeds into "Google-TV-style" content rows: a caption plus video cards.
 * Works with YouTube channel/playlist feeds (https://www.youtube.com/feeds/videos.xml?channel_id=...)
 * and plain RSS items with &lt;link&gt; and &lt;media:thumbnail&gt;/&lt;enclosure&gt;.
 * Feeds are cached in the app's cache dir and served stale when the TV is offline.
 */
public final class FeedLoader {

    public static final class Video {
        public String title;
        public String url;
        public String thumb;
        /** Non-null only for provider-published rows: the owning package; URIs are restricted to it. */
        public String pkg;
        public long positionMs, durationMs;
        public int bannerIndex = -1;
        public String rating, genres;
        public String identity() { return (pkg == null ? "feed" : pkg) + "|" + url; }
    }

    public static final class Row {
        public String title;
        /** Stable identity for the row manager and user prefs ("channel|…", "rss|…", "system|…"). */
        public String key;
        public final List<Video> videos = new ArrayList<>();
    }

    public interface Callback {
        void onRows(List<Row> rows);
    }

    /** Serves the cached copy for an hour before re-downloading. */
    private static final long TTL_MS = 60L * 60 * 1000;
    private static final int MAX_VIDEOS = 15;

    private final Context ctx;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    FeedLoader(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    void close() { io.shutdownNow(); main.removeCallbacksAndMessages(null); }

    void load(List<String> specs, final Callback cb) {
        io.execute(() -> {
            List<Row> rows = new ArrayList<>();
            for (String spec : specs) {
                if (spec == null) continue;
                String[] parts = spec.split("\\|");
                if (parts.length < 2 || parts[1].trim().isEmpty()) continue;
                Row row = new Row();
                row.title = parts[0].trim();
                row.key = "rss|" + row.title;
                try {
                    row.videos.addAll(parse(read(parts[1].trim())));
                } catch (Exception ignored) {
                }
                if (!row.videos.isEmpty()) rows.add(row);
            }
            main.post(() -> cb.onRows(rows));
        });
    }

    private InputStream read(String url) throws Exception {
        File cache = cacheFile(url);
        if (cache.isFile() && System.currentTimeMillis() - cache.lastModified() < TTL_MS) {
            return new ByteArrayInputStream(bytes(cache));
        }
        try {
            byte[] fresh = httpGet(url);
            FileOutputStream out = new FileOutputStream(cache);
            out.write(fresh);
            out.close();
            return new ByteArrayInputStream(fresh);
        } catch (Exception e) {
            if (cache.isFile()) return new ByteArrayInputStream(bytes(cache));   // offline: stale beats empty
            throw e;
        }
    }

    private static byte[] httpGet(String spec) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(spec).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(9000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36");
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        c.disconnect();
        return out.toByteArray();
    }

    private static List<Video> parse(InputStream in) throws Exception {
        List<Video> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        try (InputStream source = in) {
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            p.setInput(source, null);
            Video current = null;
            StringBuilder text = new StringBuilder();
            for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.next()) {
                if (event == XmlPullParser.START_TAG) {
                    text.setLength(0);
                    String name = p.getName();
                    if ("entry".equals(name) || "item".equals(name)) current = new Video();
                    if (current == null) continue;
                    if ("link".equals(name)) {
                        String href = p.getAttributeValue(null, "href"), rel = p.getAttributeValue(null, "rel");
                        if (href != null && (rel == null || "alternate".equals(rel))) current.url = href;
                    } else if ("thumbnail".equals(name)) {
                        current.thumb = p.getAttributeValue(null, "url");
                    } else if ("enclosure".equals(name)) {
                        String type = p.getAttributeValue(null, "type");
                        if (type != null && type.startsWith("image/")) current.thumb = p.getAttributeValue(null, "url");
                    }
                } else if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) {
                    text.append(p.getText());
                } else if (event == XmlPullParser.END_TAG && current != null) {
                    String name = p.getName(), value = text.toString().trim();
                    if ("title".equals(name) && !value.isEmpty()) current.title = value;
                    else if ("link".equals(name) && current.url == null && !value.isEmpty()) current.url = value;
                    else if ("videoId".equals(name) && current.url == null && !value.isEmpty()) current.url = "https://www.youtube.com/watch?v=" + value;
                    else if ("entry".equals(name) || "item".equals(name)) {
                        if (current.title != null && current.url != null && !current.title.isEmpty() && seen.add(current.url) && out.size() < MAX_VIDEOS) out.add(current);
                        current = null;
                    }
                }
            }
        }
        return out;
    }

    private File cacheFile(String url) throws Exception {
        byte[] dig = MessageDigest.getInstance("MD5").digest(url.getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder();
        for (byte b : dig) hex.append(String.format("%02x", b));
        return new File(ctx.getCacheDir(), "row_" + hex + ".xml");
    }

    private static byte[] bytes(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }
}
