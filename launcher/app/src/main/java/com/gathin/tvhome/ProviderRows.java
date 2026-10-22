package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads published shelves using their publisher's order and a separate recent watch-next shelf. */
public final class ProviderRows {
    private static final int MAX_PER_ROW = 50;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    interface Callback { void onRows(List<FeedLoader.Row> rows); }
    void close() { io.shutdownNow(); main.removeCallbacksAndMessages(null); }
    void load(Context context, Callback cb) {
        Context app = context.getApplicationContext();
        io.execute(() -> {
            List<FeedLoader.Row> rows = read(app);
            main.post(() -> cb.onRows(rows));
        });
    }
    private List<FeedLoader.Row> read(Context context) {
        List<FeedLoader.Row> rows = new ArrayList<>();
        if (context.checkSelfPermission("android.permission.READ_TV_LISTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return rows;
        List<Program> next = readPrograms(context, TvContract.WatchNextPrograms.CONTENT_URI, true);
        FeedLoader.Row watching = new FeedLoader.Row();
        watching.key = "system|continue"; watching.title = context.getString(R.string.row_continue_watching);
        appendUnique(watching, next);
        if (!watching.videos.isEmpty()) rows.add(watching);
        Map<Long, FeedLoader.Row> channels = new LinkedHashMap<>();
        Map<Long, String> owners = new LinkedHashMap<>();
        try (Cursor c = context.getContentResolver().query(TvContract.Channels.CONTENT_URI, null, null, null, "_id ASC")) {
            while (c != null && c.moveToNext()) {
                String type = text(c, "type");
                if (!TextUtils.isEmpty(type) && !TvContract.Channels.TYPE_PREVIEW.equals(type)) continue;
                long id = number(c, "_id");
                FeedLoader.Row row = new FeedLoader.Row();
                row.key = "channel|" + id; row.title = text(c, "display_name");
                if (TextUtils.isEmpty(row.title)) continue;
                channels.put(id, row); owners.put(id, text(c, "package_name"));
            }
        } catch (Exception e) { Log.w("TvHome", "Could not read preview channels", e); }
        Map<Long, List<Program>> byChannel = new LinkedHashMap<>();
        for (Program p : readPrograms(context, TvContract.PreviewPrograms.CONTENT_URI, false)) {
            if (!channels.containsKey(p.channel)) continue;
            String owner = owners.get(p.channel);
            if (TextUtils.isEmpty(p.video.pkg)) p.video.pkg = owner;
            if (TextUtils.isEmpty(p.video.pkg) || (!TextUtils.isEmpty(owner) && !owner.equals(p.video.pkg))) continue;
            byChannel.computeIfAbsent(p.channel, k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<Long, FeedLoader.Row> entry : channels.entrySet()) {
            List<Program> programs = byChannel.get(entry.getKey());
            if (programs == null) continue;
            appendUnique(entry.getValue(), programs);
            if (!entry.getValue().videos.isEmpty()) rows.add(entry.getValue());
        }
        return rows;
    }
    private static final class Program {
        FeedLoader.Video video;
        long channel, order, id;
    }
    private List<Program> readPrograms(Context context, Uri uri, boolean watchNext) {
        List<Program> result = new ArrayList<>();
        // Optional fields vary on vendor TVs. Read available columns rather than failing the whole shelf.
        try (Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                FeedLoader.Video v = new FeedLoader.Video();
                v.title = text(c, "title"); v.url = text(c, "intent_uri"); v.pkg = text(c, "package_name");
                v.thumb = text(c, "poster_art_uri");
                if (TextUtils.isEmpty(v.thumb)) v.thumb = text(c, "thumbnail_uri");
                v.positionMs = number(c, "last_playback_position_millis"); v.durationMs = number(c, "duration_millis");
                if (TextUtils.isEmpty(v.title) || TextUtils.isEmpty(v.url)) continue;
                if (watchNext && TextUtils.isEmpty(v.pkg)) continue;
                Program program = new Program(); program.video = v;
                program.id = number(c, "_id"); program.channel = number(c, "channel_id");
                program.order = number(c, watchNext ? "last_engagement_time_utc_millis" : "weight");
                result.add(program);
            }
        } catch (Exception e) { Log.w("TvHome", "Could not read " + uri.getLastPathSegment(), e); }
        result.sort((a, b) -> { int byOrder = Long.compare(b.order, a.order); return byOrder != 0 ? byOrder : Long.compare(b.id, a.id); });
        return result;
    }
    private static void appendUnique(FeedLoader.Row row, List<Program> programs) {
        Set<String> seen = new HashSet<>();
        for (Program p : programs) {
            if (row.videos.size() >= MAX_PER_ROW) break;
            if (seen.add(p.video.identity())) row.videos.add(p.video);
        }
    }
    private static String text(Cursor c, String name) { int i = c.getColumnIndex(name); return i < 0 || c.isNull(i) ? null : c.getString(i); }
    private static long number(Cursor c, String name) { int i = c.getColumnIndex(name); return i < 0 || c.isNull(i) ? 0 : c.getLong(i); }
}
