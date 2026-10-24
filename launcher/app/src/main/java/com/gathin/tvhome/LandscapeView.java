package com.gathin.tvhome;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.time.ZonedDateTime;

/** An illustrated river landscape: resolution independent and no continuous rendering loop. */
final class LandscapeView extends View {
    private final Paint paint=new Paint(3);
    private final Path shape=new Path();
    private AmbientData.Weather weather;
    LandscapeView(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    void weather(AmbientData.Weather value) { weather=value; invalidate(); }
    private void color(int c) { paint.setShader(null); paint.setColor(c); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.save(); canvas.scale(getWidth()/960f,getHeight()/540f);
        ZonedDateTime now=ZonedDateTime.now(AmbientData.ZONE); boolean night=AmbientData.night(weather,now);
        boolean dusk=now.getHour()==6 || now.getHour()==17;
        int code=weather==null?0:weather.code; boolean wet=code>=51,cloudy=code>=2;
        int top=night?0xff071422:wet?0xff263c4b:dusk?0xff372746:0xff244d70;
        int horizon=night?0xff304650:wet?0xff71868b:dusk?0xffc78166:0xffa7bcb9;
        paint.setShader(new LinearGradient(0,0,0,420,top,horizon,Shader.TileMode.CLAMP)); canvas.drawRect(0,0,960,540,paint);
        if(night && !cloudy) { color(0x889fbac8); for(int i=0;i<70;i++) canvas.drawCircle((i*137+53)%960,(i*53+19)%255, i%3==0?1.1f:.6f,paint); }
        color(night?0xffd6d9c6:0xffe6c59a); canvas.drawCircle(435,130,night?20:34,paint);
        if(night) { color(top); canvas.drawCircle(445,122,19,paint); }
        float drift=AmbientSettings.reducedMotion(getContext())?0:(System.currentTimeMillis()/15000%240)/12f;
        color(cloudy?0x487d9299:0x147f9da7);
        for(int i=0;i<(cloudy?9:3);i++) { float x=(i*163+drift)%1040-40,y=100+i%3*34; canvas.drawOval(x,y,x+230,y+27,paint); }
        // Distant banks and a low city skyline; the river becomes the dominant foreground plane.
        color(night?0xff172b34:0xff607773); shape.reset(); shape.moveTo(0,354); shape.cubicTo(240,325,360,382,525,353); shape.cubicTo(680,320,800,349,960,326); shape.lineTo(960,540); shape.lineTo(0,540); shape.close(); canvas.drawPath(shape,paint);
        color(night?0xff21353d:0xff758783);
        for(int i=0;i<45;i++) canvas.drawRect(i*23,331-(i*17%27),i*23+13,353,paint);
        paint.setShader(new LinearGradient(0,347,0,540,night?0xff334d59:0xff90aaa7,night?0xff0e2534:0xff314e5c,Shader.TileMode.CLAMP));
        shape.reset(); shape.moveTo(560,346); shape.cubicTo(350,390,825,414,260,540); shape.lineTo(960,540); shape.lineTo(960,474); shape.cubicTo(630,415,705,389,594,346); shape.close(); canvas.drawPath(shape,paint);
        color(night?0x30759aa6:0x409fb9b3); paint.setStrokeWidth(1);
        for(int i=0;i<35;i++) { float y=365+i*5,x=565+(float)Math.sin(i*.7)*40; canvas.drawLine(x-i*2,y,x+20+i*5,y,paint); }
        color(0xff0c2026); shape.reset(); shape.moveTo(0,435); shape.cubicTo(130,405,155,483,356,540); shape.lineTo(0,540); shape.close(); canvas.drawPath(shape,paint);
        palm(canvas,58,472,92); palm(canvas,126,489,65); palm(canvas,909,510,105);
        if(code==45 || code==48) { color(0x447e9296); canvas.drawRect(0,230,960,440,paint); }
        if(wet) { color(0x227c9caa); paint.setStrokeWidth(1); for(int i=0;i<75;i++){float x=i*137%960,y=i*73%540;canvas.drawLine(x,y,x-5,y+20,paint);} }
        // Constant scrim keeps text contrast predictable in every weather palette.
        paint.setShader(new LinearGradient(0,0,960,0,new int[]{0x9e020811,0x30020811,0xa6020811},null,Shader.TileMode.CLAMP)); canvas.drawRect(0,0,960,540,paint);
        paint.setShader(new LinearGradient(0,310,0,540,0x00020811,0xc4020811,Shader.TileMode.CLAMP)); canvas.drawRect(0,310,960,540,paint);
        canvas.restore();
    }
    private void palm(Canvas canvas,float x,float y,float height) {
        color(0xff091c24); paint.setStrokeWidth(5); canvas.drawLine(x,y,x+9,y-height,paint);
        for(int i=-3;i<=3;i++) { shape.reset(); shape.moveTo(x+9,y-height); shape.quadTo(x+9+i*9,y-height-22+Math.abs(i)*3,x+9+i*16,y-height+Math.abs(i)*5); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); canvas.drawPath(shape,paint); paint.setStyle(Paint.Style.FILL); }
    }
}
