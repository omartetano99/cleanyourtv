package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

final class AmbientSettings {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("ambient",0); }
    static long idleMillis(Context c) { return prefs(c).getInt("idle_minutes",5)*60000L; }
    static boolean reducedMotion(Context c) { return prefs(c).getBoolean("reduce_motion",false) || Settings.Global.getFloat(c.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)==0; }
    static double[] coordinates(String input) {
        try {
            String[] parts=input.trim().split(","); if(parts.length!=2) return null;
            double lat=Double.parseDouble(parts[0].trim()),lon=Double.parseDouble(parts[1].trim());
            if(!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180) return null;
            return new double[]{lat,lon};
        } catch(Exception e) { return null; }
    }
    static String origin(Context c) { return prefs(c).getString("origin",""); }
    static String destination(Context c) { return prefs(c).getString("destination","33.303040,44.371053"); }
    static String destinationLabel(Context c) { return prefs(c).getString("destination_label","Harthya (area)"); }
    static String routeUrl(String origin,String destination) {
        double[] a=coordinates(origin),b=coordinates(destination); if(a==null || b==null) return null;
        return "https://router.project-osrm.org/route/v1/driving/"+a[1]+","+a[0]+";"+b[1]+","+b[0]+"?overview=false";
    }
    static String mapsUrl(Context c) {
        return "https://www.google.com/maps/dir/?api=1&origin="+android.net.Uri.encode(origin(c))+"&destination="+android.net.Uri.encode(destination(c))+"&travelmode=driving";
    }
}
