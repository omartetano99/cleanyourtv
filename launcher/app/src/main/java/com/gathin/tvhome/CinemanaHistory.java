package com.gathin.tvhome;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import org.json.*;
import java.util.*;

/** Local, bounded history. Never reads Cinemana's private database or account tokens. */
final class CinemanaHistory {
    static final String KEY="cinemana|history";
    private static final String PREFS="cinemana_history";
    static synchronized void remember(Context c, String title, String art, long position, long duration, boolean watched) {
        if (title==null || title.trim().isEmpty()) return;
        title=title.trim(); if (title.length()>300) title=title.substring(0,300);
        List<JSONObject> entries=entries(c); JSONObject found=null;
        for (JSONObject item:entries) if (title.equals(item.optString("title"))) { found=item; break; }
        long now=System.currentTimeMillis();
        if (found!=null && !watched) return; // Seeing a History card must not reorder previous playback.
        try {
            long oldest=now; for(JSONObject item:entries) oldest=Math.min(oldest,item.optLong("updated",now));
            if (found==null) { found=new JSONObject(); found.put("title",title); entries.add(found); }
            if (watched && now-found.optLong("updated")<4000 && (position < 0 || Math.abs(position-found.optLong("position"))<4000)) return;
            long newest = 0; for (JSONObject item : entries) newest = Math.max(newest, item.optLong("updated"));
            found.put("updated",watched ? Math.max(now, newest + 1) : oldest - 1);
            if (art!=null && (art.startsWith("https://") || art.startsWith("content://"))) found.put("art",art);
            if (position>=0) found.put("position",position); if (duration>0) found.put("duration",duration);
            entries.sort((a,b)->Long.compare(b.optLong("updated"),a.optLong("updated")));
            JSONArray array=new JSONArray(); for(int i=0;i<Math.min(50,entries.size());i++) array.put(entries.get(i));
            c.getSharedPreferences(PREFS,0).edit().putString("items",array.toString()).apply();
        } catch(JSONException ignored) { }
    }
    static void capture(Context c, MediaMetadata metadata, PlaybackState state) {
        if(CinemanaResume.pending(c) || metadata==null || state==null || (state.getState()!=PlaybackState.STATE_PLAYING && state.getState()!=PlaybackState.STATE_PAUSED)) return;
        String title=metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        if(title==null || title.isEmpty()) title=metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String art=metadata.getString(MediaMetadata.METADATA_KEY_ART_URI);
        if(art==null) art=metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        long position=Math.max(0,state.getPosition()),duration=metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if(state.getState()==PlaybackState.STATE_PLAYING && state.getLastPositionUpdateTime()>0)
            position+=(long)(Math.max(0,android.os.SystemClock.elapsedRealtime()-state.getLastPositionUpdateTime())*Math.max(0,state.getPlaybackSpeed()));
        if(duration>0) position=Math.min(position,duration);
        remember(c,title,art,position,duration,true);
    }
    private static List<JSONObject> entries(Context c) {
        List<JSONObject> items=new ArrayList<>();
        try { JSONArray array=new JSONArray(c.getSharedPreferences(PREFS,0).getString("items","[]"));
            for(int i=0;i<array.length();i++) if(array.optJSONObject(i)!=null) items.add(array.getJSONObject(i));
        } catch(Exception ignored) { }
        return items;
    }
    static FeedLoader.Row row(Context c, List<FeedLoader.Row> banners) {
        FeedLoader.Row row=new FeedLoader.Row(); row.key=KEY; row.title="Cinemana 4K · History";
        for(JSONObject item:entries(c)) {
            FeedLoader.Video v=new FeedLoader.Video(); v.title=item.optString("title"); v.pkg=CinemanaResume.PACKAGE;
            v.url="cinemana-history://title/"+android.net.Uri.encode(v.title); v.thumb=item.optString("art",null);
            v.rating=item.optString("rating",null); v.genres=item.optString("genres",null);
            v.positionMs=item.optLong("position"); v.durationMs=item.optLong("duration");
            if(banners!=null) for(FeedLoader.Row banner:banners) for(FeedLoader.Video other:banner.videos) if(v.title.equals(other.title)) {
                if(v.thumb==null) v.thumb=other.thumb; v.rating=other.rating; v.genres=other.genres; break;
            }
            row.videos.add(v);
        }
        if(row.videos.isEmpty()) {
            FeedLoader.Video help=new FeedLoader.Video(); help.pkg=CinemanaResume.PACKAGE; help.title="Open Cinemana History"; help.url="cinemana-history://import"; row.videos.add(help);
        }
        return row;
    }
    /** Enrich at most three missing covers per refresh; ambiguous remakes deliberately stay text-only. */
    static void enrich(Context c) {
        int requests=0;
        for(JSONObject entry:entries(c)) {
            if(requests>=3 || Thread.currentThread().isInterrupted()) break;
            if(!entry.optString("art").isEmpty() || System.currentTimeMillis()-entry.optLong("lookup")<86400000) continue;
            requests++;
            try {
                String title=entry.optString("title");
                String query=android.util.Base64.encodeToString(title.getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);
                JSONObject response=JsonCache.get(c,"https://cinemana.shabakaty.com/api/android/video/V/2/videoKind/0/itemsPerPage/20/video_title_search/"+android.net.Uri.encode(query),86400000).json;
                JSONObject match=uniqueMatch(response.getJSONArray("items"),title);
                updateArt(c,title,match);
            } catch(Exception ignored) { }
        }
    }
    static JSONObject uniqueMatch(JSONArray array,String title) {
        JSONObject match=null;
        for(int i=0;i<array.length();i++) {
            JSONObject candidate=array.optJSONObject(i); if(candidate==null) continue;
            if(title.equals(candidate.optString("ar_title").trim()) || title.equals(candidate.optString("en_title").trim())) {
                if(match!=null) return null; match=candidate;
            }
        }
        return match;
    }
    private static synchronized void updateArt(Context c,String title,JSONObject match) throws JSONException {
        List<JSONObject> entries=entries(c); JSONArray result=new JSONArray();
        for(JSONObject entry:entries) {
            if(title.equals(entry.optString("title"))) {
                entry.put("lookup",System.currentTimeMillis());
                if(match!=null) {
                    String art=match.optString("imgObjUrl"); if(art.startsWith("https://")) entry.put("art",art);
                    double score=match.optDouble("stars",0); if(score>0 && score<=10) entry.put("rating",match.optString("stars"));
                    JSONArray categories=match.optJSONArray("categories"); List<String> genres=new ArrayList<>();
                    if(categories!=null) for(int i=0;i<Math.min(2,categories.length());i++) {
                        JSONObject category=categories.optJSONObject(i); if(category!=null) genres.add(category.optString("ar_title",category.optString("en_title")));
                    }
                    entry.put("genres",String.join(" · ",genres));
                }
            }
            result.put(entry);
        }
        c.getSharedPreferences(PREFS,0).edit().putString("items",result.toString()).apply();
    }
    static void clear(Context c) { c.getSharedPreferences(PREFS,0).edit().clear().apply(); }
}
