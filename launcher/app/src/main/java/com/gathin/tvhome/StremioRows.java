package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Public Cinemeta discovery only. Personal library/watch progress stays with Stremio's TV provider. */
final class StremioRows {
    static final String PACKAGE = "com.stremio.one";
    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private boolean loading;
    private FeedLoader.Callback latestCallback;
    StremioRows(Context c) { context=c.getApplicationContext(); }
    void load(FeedLoader.Callback callback) {
        latestCallback = callback; if (closed || loading) return; loading = true;
        worker.execute(() -> {
            List<FeedLoader.Row> rows = new ArrayList<>();
            try { context.getPackageManager().getPackageInfo(PACKAGE,0); } catch (Exception e) { deliver(rows); return; }
            String[][] specs = {{"movie","top","Popular films"},{"series","top","Popular series"},{"movie","year","New films"},{"series","year","New series"}};
            for (String[] spec : specs) {
                if (closed || Thread.currentThread().isInterrupted()) return;
                try {
                    String suffix = "year".equals(spec[1]) ? "/genre=" + java.time.Year.now().getValue() : "";
                    JSONObject data = JsonCache.get(context,"https://v3-cinemeta.strem.io/catalog/" + spec[0]+"/"+spec[1]+suffix+".json",3600000).json;
                    FeedLoader.Row row = parse(data, spec[0], spec[1], spec[2]); if (!row.videos.isEmpty()) rows.add(row);
                } catch (Exception ignored) { }
            }
            deliver(rows);
        });
    }
    static FeedLoader.Row parse(JSONObject data, String type, String id, String title) throws Exception {
        FeedLoader.Row row = new FeedLoader.Row(); row.key="stremio|"+type+"|"+id; row.title="Stremio · "+title;
        JSONArray metas = data.getJSONArray("metas"); Set<String> seen = new HashSet<>();
        for (int i=0; i<metas.length() && row.videos.size()<20; i++) {
            JSONObject meta=metas.optJSONObject(i); if (meta==null) continue;
            String key=meta.optString("id"), name=meta.optString("name");
            if (!key.matches("tt[0-9]+") || name.isEmpty() || !seen.add(key)) continue;
            FeedLoader.Video v=new FeedLoader.Video(); v.pkg=PACKAGE; v.title=name; v.url="stremio:///detail/"+type+"/"+key;
            String art=meta.optString("background",meta.optString("poster")); v.thumb=art.startsWith("https://")?art:null;
            double rating=meta.optDouble("imdbRating",0); if (rating>0 && rating<=10) v.rating=String.format(Locale.US,"%.1f",rating);
            JSONArray genres=meta.optJSONArray("genres"); List<String> labels=new ArrayList<>();
            if (genres!=null) for(int j=0;j<Math.min(2,genres.length());j++) labels.add(genres.optString(j));
            v.genres=String.join(" · ",labels); row.videos.add(v);
        }
        return row;
    }
    private void deliver(List<FeedLoader.Row> rows) { main.post(() -> { loading = false; if (!closed) latestCallback.onRows(rows); }); }
    void close() { closed=true; worker.shutdownNow(); main.removeCallbacksAndMessages(null); }
}
