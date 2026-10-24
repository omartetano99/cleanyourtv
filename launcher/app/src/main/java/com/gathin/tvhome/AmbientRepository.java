package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Independent requests: a failed fixtures provider cannot hold up weather or the clock. */
final class AmbientRepository {
    interface Listener { void weather(AmbientData.Weather weather,String freshness); void league(int index,String text); void route(String text); }
    private final Context c;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newFixedThreadPool(2);
    private volatile boolean closed;
    private final java.util.concurrent.atomic.AtomicBoolean refreshing=new java.util.concurrent.atomic.AtomicBoolean();
    AmbientRepository(Context context) { c=context.getApplicationContext(); }
    void refresh(Listener listener) {
        if(closed || !refreshing.compareAndSet(false,true)) return;
        java.util.concurrent.atomic.AtomicInteger remaining=new java.util.concurrent.atomic.AtomicInteger(5);
        Runnable done=()->{ if(remaining.decrementAndGet()==0) refreshing.set(false); };
        worker.execute(()->{ try {
            JsonCache.Result data=JsonCache.get(c,"https://api.open-meteo.com/v1/forecast?latitude=33.3152&longitude=44.3661&current=temperature_2m,weather_code,is_day&daily=sunrise,sunset&timezone=Asia%2FBaghdad&forecast_days=1",900000);
            AmbientData.Weather weather=AmbientData.weather(data.json); post(()->listener.weather(weather,freshness(data)));
        } catch(Exception e) { post(()->listener.weather(null,"Weather unavailable")); } finally { done.run(); } });
        String[] codes={"eng.1","esp.1","uefa.champions"};
        boolean football=AmbientSettings.prefs(c).getBoolean("football",true);
        for(int i=0;i<3;i++) { final int index=i; worker.execute(()->{ try {
            if(!football) { post(()->listener.league(index,"")); return; }
            String endpoint="https://site.api.espn.com/apis/site/v2/sports/soccer/"+codes[index]+"/scoreboard";
            JsonCache.Result data=JsonCache.get(c,endpoint,300000); Instant now=Instant.now();
            List<AmbientData.Match> matches=AmbientData.relevant(AmbientData.matches(data.json),now);
            if(matches.isEmpty()) {
                String next=AmbientData.nextDate(data.json,LocalDate.now(AmbientData.ZONE));
                if(next!=null) { data=JsonCache.get(c,endpoint+"?dates="+next,900000); matches=AmbientData.relevant(AmbientData.matches(data.json),now); }
            }
            List<String> lines=new ArrayList<>();
            if (!matches.isEmpty()) {
                // Keep a live game visible; otherwise rotate the matchday without crowding the sky.
                int selected = "in".equals(matches.get(0).state) ? 0 : (int)((System.currentTimeMillis()/300000) % matches.size());
                lines.add(AmbientData.matchLine(matches.get(selected)));
            }
            String result=lines.isEmpty()?"No upcoming fixtures available":String.join("\n",lines);
            result+="\n"+freshness(data); final String text=result; post(()->listener.league(index,text));
        } catch(Exception e) { post(()->listener.league(index,"Fixtures unavailable")); } finally { done.run(); } }); }
        worker.execute(()->{ try {
            String url=AmbientSettings.routeUrl(AmbientSettings.origin(c),AmbientSettings.destination(c));
            if(url==null) { post(()->listener.route("Set your starting point in Home settings")); return; }
            JsonCache.Result data=JsonCache.get(c,url,86400000); double[] route=AmbientData.route(data.json);
            String text="≈ "+Math.max(1,Math.round(route[0]/60))+" min  ·  "+String.format(Locale.US,"%.1f km",route[1]/1000)+"\nNo live traffic · "+freshness(data);
            post(()->listener.route(text));
        } catch(Exception e) { post(()->listener.route("Route estimate unavailable")); } finally { done.run(); } });
    }
    static String freshness(JsonCache.Result data) {
        String stamp=Instant.ofEpochMilli(data.fetchedAt).atZone(AmbientData.ZONE).format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm",Locale.ENGLISH));
        return (data.stale?"Offline · saved ":"Updated ")+stamp;
    }
    private void post(Runnable r) { main.post(()->{ if(!closed) r.run(); }); }
    void close() { closed=true; worker.shutdownNow(); main.removeCallbacksAndMessages(null); }
}
