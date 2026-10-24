package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class AmbientView extends FrameLayout implements AmbientRepository.Listener {
    private final LandscapeView landscape;
    private final LinearLayout content;
    private final TextView clock,date,weather,weatherFresh,route;
    private final TextView[] leagues=new TextView[3];
    private final AmbientRepository repository;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long lastRefresh;
    private final Runnable tick=new Runnable(){ @Override public void run(){
        updateClock(); landscape.invalidate();
        if(System.currentTimeMillis()-lastRefresh>300000) { lastRefresh=System.currentTimeMillis(); repository.refresh(AmbientView.this); }
        if(!AmbientSettings.reducedMotion(getContext())) { long minute=System.currentTimeMillis()/60000; content.setTranslationX(TvUi.dp(getContext(),(minute%5)-2)); content.setTranslationY(TvUi.dp(getContext(),(minute%3)-1)); }
        handler.postDelayed(this,15000);
    }};
    AmbientView(Context c) {
        super(c); landscape=new LandscapeView(c); addView(landscape,new LayoutParams(-1,-1)); repository=new AmbientRepository(c);
        content=new LinearLayout(c); content.setOrientation(LinearLayout.HORIZONTAL); content.setPadding(TvUi.dp(c,48),TvUi.dp(c,42),TvUi.dp(c,48),TvUi.dp(c,40)); addView(content,new LayoutParams(-1,-1));
        LinearLayout left=new LinearLayout(c); left.setOrientation(LinearLayout.VERTICAL); content.addView(left,new LinearLayout.LayoutParams(0,-1,1));
        left.addView(TvUi.text(c,"BAGHDAD",16,0xffb9d3d4));
        clock=TvUi.text(c,"",78,0xfff5f4ef); clock.setFontVariationSettings("'wght' 350"); left.addView(clock);
        date=TvUi.text(c,"",20,0xffe4e5df); left.addView(date);
        weather=TvUi.text(c,"Loading weather…",24,0xfff5f4ef); weather.setPadding(0,TvUi.dp(c,20),0,0); left.addView(weather);
        weatherFresh=TvUi.text(c,"",13,0xffc1cdcf); left.addView(weatherFresh);
        left.addView(new View(c),new LinearLayout.LayoutParams(1,0,1));
        left.addView(TvUi.text(c,"DRIVE TO "+AmbientSettings.destinationLabel(c).toUpperCase(Locale.ROOT),15,0xffb9d3d4));
        route=TvUi.text(c,"Loading route estimate…",20,0xfff5f4ef); route.setPadding(0,TvUi.dp(c,8),TvUi.dp(c,16),0); left.addView(route);
        LinearLayout right=new LinearLayout(c); right.setOrientation(LinearLayout.VERTICAL);
        int width=Math.min(420,(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density*.44));
        content.addView(right,new LinearLayout.LayoutParams(TvUi.dp(c,width),-1));
        if(AmbientSettings.prefs(c).getBoolean("football",true)) {
            TextView heading=TvUi.text(c,"FOOTBALL · BAGHDAD TIME",15,0xffb9d3d4); heading.setPadding(0,0,0,TvUi.dp(c,22)); right.addView(heading);
            String[] names={"Premier League","La Liga","Champions League"};
            for(int i=0;i<3;i++) { TextView name=TvUi.text(c,names[i],20,0xfff5f4ef); right.addView(name);
                leagues[i]=TvUi.text(c,"Loading fixtures…",16,0xffe1e7e6); leagues[i].setLineSpacing(TvUi.dp(c,5),1); leagues[i].setPadding(0,TvUi.dp(c,7),0,TvUi.dp(c,22)); right.addView(leagues[i]); }
        }
        TextView sources=TvUi.text(c,"Weather: Open-Meteo  ·  Football: ESPN  ·  Routing: OSRM / © OpenStreetMap contributors",11,0xffb7c1c4);
        LayoutParams sourceParams=new LayoutParams(-1,-2,Gravity.BOTTOM); sourceParams.setMargins(TvUi.dp(c,48),0,TvUi.dp(c,48),TvUi.dp(c,12)); addView(sources,sourceParams);
        setFocusable(true); updateClock();
    }
    private void updateClock() { ZonedDateTime now=ZonedDateTime.now(AmbientData.ZONE); clock.setText(now.format(DateTimeFormatter.ofPattern("HH:mm",Locale.ENGLISH))); date.setText(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale.ENGLISH))); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); handler.post(tick); if(!AmbientSettings.reducedMotion(getContext())) { content.setAlpha(0); content.animate().alpha(1).setDuration(900).start(); } }
    @Override protected void onDetachedFromWindow() { handler.removeCallbacksAndMessages(null); content.animate().cancel(); repository.close(); super.onDetachedFromWindow(); }
    @Override public void weather(AmbientData.Weather value,String freshness) { landscape.weather(value); weather.setText(value==null?"Weather unavailable":Math.round(value.temperature)+"°  ·  "+AmbientData.description(value.code)); weatherFresh.setText(freshness); }
    @Override public void league(int index,String text) { if(leagues[index]!=null) leagues[index].setText(smallLastLine(text)); }
    @Override public void route(String text) { route.setText(smallLastLine(text)); }
    private CharSequence smallLastLine(String text) { android.text.SpannableString s=new android.text.SpannableString(text); int last=text.lastIndexOf('\n'); if(last>=0) s.setSpan(new android.text.style.RelativeSizeSpan(.78f),last+1,text.length(),0); return s; }
}
