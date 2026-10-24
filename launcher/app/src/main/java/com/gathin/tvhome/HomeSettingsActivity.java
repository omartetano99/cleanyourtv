package com.gathin.tvhome;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;

/** Coordinates stay in private, non-backed-up preferences. No API keys or account sign-in needed. */
public final class HomeSettingsActivity extends Activity {
    private LinearLayout options;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout page=TvUi.page(this,"Home settings","Make the TV yours · Changes save on this device");
        ScrollView scroll=new ScrollView(this); options=new LinearLayout(this); options.setOrientation(LinearLayout.VERTICAL); scroll.addView(options); page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(page); render();
    }
    private void option(String label,Runnable action) { options.addView(TvUi.button(this,label,action),new LinearLayout.LayoutParams(-1,-2)); }
    private void render() {
        int focus=options.indexOfChild(options.getFocusedChild()); options.removeAllViews();
        option("Preview weather screensaver",()->startActivity(new Intent(this,AmbientActivity.class).putExtra("preview",true)));
        int minutes=AmbientSettings.prefs(this).getInt("idle_minutes",5);
        option("Start while Home is idle · "+(minutes==0?"Off":minutes+" minutes"),()->new AlertDialog.Builder(this).setTitle("Idle time on Home").setItems(new String[]{"Off","2 minutes","5 minutes","10 minutes"},(d,i)->{AmbientSettings.prefs(this).edit().putInt("idle_minutes",new int[]{0,2,5,10}[i]).apply();render();}).show());
        option("Football fixtures · "+(AmbientSettings.prefs(this).getBoolean("football",true)?"On":"Off"),()->toggle("football",true));
        option("Reduced motion · "+(AmbientSettings.prefs(this).getBoolean("reduce_motion",false)?"On":"Off"),()->toggle("reduce_motion",false));
        option("Drive starting point · "+(AmbientSettings.coordinates(AmbientSettings.origin(this))==null?"Not set":"Saved privately"),()->coordinate("origin","Starting point",AmbientSettings.origin(this)));
        option("Destination · "+AmbientSettings.destinationLabel(this),()->coordinate("destination","Destination coordinates",AmbientSettings.destination(this)));
        option("Destination name",()->edit("destination_label","Destination name",AmbientSettings.destinationLabel(this),false));
        option("Open route in Google Maps",()->{ if(AmbientSettings.coordinates(AmbientSettings.origin(this))==null) { Toast.makeText(this,"Set a starting point first", android.widget.Toast.LENGTH_SHORT).show();return; } open(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(AmbientSettings.mapsUrl(this)))); });
        option("Cinemana · Import visible History",()->{ FeedLoader.Video v=new FeedLoader.Video();v.title="Cinemana History";v.url="cinemana-history://import";CinemanaResume.openHistory(this,v); });
        option("Clear launcher’s Cinemana history",()->new AlertDialog.Builder(this).setTitle("Clear local history?").setMessage("Cinemana’s own history is kept.").setPositiveButton("Clear",(d,i)->CinemanaHistory.clear(this)).setNegativeButton("Cancel",null).show());
        option("Playback access · Needed for automatic screensaver",()->open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        option("Cinemana helper · Accessibility",()->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        option("System screensaver settings",()->open(new Intent(Settings.ACTION_DREAM_SETTINGS)));
        option("Wi-Fi and network",()->open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        option("TV system settings",()->open(new Intent(Settings.ACTION_SETTINGS)));
        TextView note=TvUi.text(this,"Weather uses Baghdad. Routes use a free OSRM estimate without live traffic. Your configured coordinates are sent to OSRM to calculate the route; opening Maps sends them to Google. Fixtures come from ESPN’s public scoreboard and may be unavailable. Automatic idle display pauses when media access is missing.",15,0xffbac4c5);
        note.setPadding(18,20,18,18); options.addView(note);
        options.getChildAt(Math.max(0,Math.min(focus,options.getChildCount()-2))).requestFocus();
    }
    private void toggle(String key,boolean fallback) { AmbientSettings.prefs(this).edit().putBoolean(key,!AmbientSettings.prefs(this).getBoolean(key,fallback)).apply();render(); }
    private void coordinate(String key,String title,String value) { edit(key,title,value,true); }
    private void edit(String key,String title,String value,boolean coordinates) {
        EditText field=new EditText(this);field.setSingleLine(true);field.setText(value);field.setHint(coordinates?"Latitude,longitude":"Name");
        LinearLayout box=new LinearLayout(this);box.setPadding(24,12,24,12);box.addView(field,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(box).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String text=field.getText().toString().trim();
            if(coordinates && !("origin".equals(key)&&text.isEmpty()) && AmbientSettings.coordinates(text)==null) {field.setError("Use latitude,longitude within valid ranges");return;}
            if(!coordinates && (text.isEmpty()||text.length()>60)) {field.setError("Use a name up to 60 characters");return;}
            AmbientSettings.prefs(this).edit().putString(key,text).apply();dialog.dismiss();render();}));dialog.show();
    }
    private void open(Intent intent) { try {startActivity(intent);} catch(Exception e) {Toast.makeText(this,"This screen is unavailable on this TV", android.widget.Toast.LENGTH_SHORT).show();} }
}
