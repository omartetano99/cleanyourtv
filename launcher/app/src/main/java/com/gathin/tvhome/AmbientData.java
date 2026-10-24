package com.gathin.tvhome;

import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Parsing is independent of the screen and tested with malformed/offline provider fixtures. */
final class AmbientData {
    static final ZoneId ZONE=ZoneId.of("Asia/Baghdad");
    static final class Weather { double temperature; int code; String sunrise,sunset; }
    static final class Match { String home,away,homeScore,awayScore,state,detail; Instant start; }
    static Weather weather(JSONObject json) throws Exception {
        JSONObject current=json.getJSONObject("current"); Weather w=new Weather();
        w.temperature=current.getDouble("temperature_2m"); w.code=current.getInt("weather_code");
        if(!Double.isFinite(w.temperature) || w.temperature < -80 || w.temperature > 65) throw new JSONException("Invalid temperature");
        JSONObject daily=json.optJSONObject("daily");
        if(daily!=null) { JSONArray a=daily.optJSONArray("sunrise"),b=daily.optJSONArray("sunset"); w.sunrise=a==null?null:a.optString(0); w.sunset=b==null?null:b.optString(0); }
        return w;
    }
    static String description(int code) {
        if(code==0) return "Clear sky"; if(code<=3) return "Cloudy";
        if(code==45 || code==48) return "Fog"; if(code>=95) return "Thunderstorms";
        if(code>=71 && code<=77 || code==85 || code==86) return "Snow";
        if(code>=51 && code<=67 || code>=80 && code<=82) return "Rain"; return "Weather conditions";
    }
    static boolean night(Weather weather,ZonedDateTime now) {
        if(weather!=null && weather.sunrise!=null && weather.sunset!=null) try {
            LocalDateTime rise=LocalDateTime.parse(weather.sunrise),set=LocalDateTime.parse(weather.sunset);
            if(rise.toLocalDate().equals(now.toLocalDate())) return now.toLocalDateTime().isBefore(rise) || !now.toLocalDateTime().isBefore(set);
        } catch(Exception ignored) { }
        return now.getHour()<6 || now.getHour()>=18;
    }
    static List<Match> matches(JSONObject json) throws Exception {
        JSONArray events=json.getJSONArray("events"); List<Match> matches=new ArrayList<>();
        for(int i=0;i<events.length();i++) try {
            JSONObject event=events.getJSONObject(i),comp=event.getJSONArray("competitions").getJSONObject(0);
            Match m=new Match(); m.start=OffsetDateTime.parse(event.getString("date")).toInstant();
            JSONObject status=comp.getJSONObject("status").getJSONObject("type"); m.state=status.optString("state","pre"); m.detail=status.optString("shortDetail","");
            JSONArray teams=comp.getJSONArray("competitors");
            for(int j=0;j<teams.length();j++) {
                JSONObject team=teams.getJSONObject(j); String name=team.getJSONObject("team").optString("shortDisplayName",team.getJSONObject("team").optString("displayName"));
                if("home".equals(team.optString("homeAway"))) { m.home=name; m.homeScore=team.optString("score","0"); }
                else if("away".equals(team.optString("homeAway"))) { m.away=name; m.awayScore=team.optString("score","0"); }
            }
            if(m.home!=null && m.away!=null) matches.add(m);
        } catch(Exception ignored) { }
        matches.sort(Comparator.comparing(m->m.start)); return matches;
    }
    static List<Match> relevant(List<Match> all, Instant now) {
        List<Match> result=new ArrayList<>();
        for(Match m:all) if("in".equals(m.state)) result.add(m);
        for(Match m:all) if("pre".equals(m.state) && !m.start.isBefore(now.minusSeconds(7200))) result.add(m);
        if(result.isEmpty()) for(Match m:all) if(m.start.atZone(ZONE).toLocalDate().equals(now.atZone(ZONE).toLocalDate())) result.add(m);
        return result;
    }
    static String nextDate(JSONObject data, LocalDate today) {
        JSONArray leagues=data.optJSONArray("leagues"); if(leagues==null || leagues.length()==0) return null;
        JSONArray calendar=leagues.optJSONObject(0).optJSONArray("calendar"); if(calendar==null) return null;
        LocalDate next=null;
        for(int i=0;i<calendar.length();i++) try {
            LocalDate date=OffsetDateTime.parse(calendar.getString(i)).atZoneSameInstant(ZONE).toLocalDate();
            if(!date.isBefore(today) && (next==null || date.isBefore(next))) next=date;
        } catch(Exception ignored) { }
        return next==null?null:next.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
    static String matchLine(Match m) {
        String versus=m.home+"  v  "+m.away;
        if("in".equals(m.state) || "post".equals(m.state)) return m.home+" "+m.homeScore+" – "+m.awayScore+" "+m.away+" · "+m.detail;
        return versus+" · "+m.start.atZone(ZONE).format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm",Locale.ENGLISH));
    }
    static double[] route(JSONObject json) throws Exception {
        if(!"Ok".equals(json.optString("code"))) throw new JSONException("No route");
        JSONObject route=json.getJSONArray("routes").getJSONObject(0); double seconds=route.getDouble("duration"),meters=route.getDouble("distance");
        if(!Double.isFinite(seconds) || !Double.isFinite(meters) || seconds<=0 || meters<=0) throw new JSONException("Invalid route");
        return new double[]{seconds,meters};
    }
}
