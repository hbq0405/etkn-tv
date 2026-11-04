package xyz.etkn.tv;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Segmented transport bar; Media3 retains seeking and accessibility behavior. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class PixelTimeBar extends DefaultTimeBar {
    static final int PLAYED=0xff1a7f70,BUFFERED=0xff5cc7b0,UNPLAYED=0xff193b36;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private long duration,position,buffered,scrub=-1;
    private JSONArray chapters;
    public PixelTimeBar(Context context){this(context,null);}
    public PixelTimeBar(Context context,AttributeSet attributes){
        super(context,attributes);setPlayedColor(PLAYED);setBufferedColor(BUFFERED);setUnplayedColor(UNPLAYED);
        addListener(new TimeBar.OnScrubListener(){
            @Override public void onScrubStart(TimeBar bar,long value){scrub=value;invalidate();}
            @Override public void onScrubMove(TimeBar bar,long value){scrub=value;invalidate();}
            @Override public void onScrubStop(TimeBar bar,long value,boolean canceled){scrub=-1;invalidate();}
        });
    }
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    @Override public void setDuration(long value){super.setDuration(value);duration=value;invalidate();}
    @Override public void setPosition(long value){super.setPosition(value);position=value;invalidate();}
    @Override public void setBufferedPosition(long value){super.setBufferedPosition(value);buffered=value;invalidate();}
    void setChapterMarkers(JSONArray values){chapters=values;invalidate();}
    static int playedColor(){return TvAppearance.primary();}
    static int bufferedColor(){return BUFFERED;}
    static int unplayedColor(){return TvAppearance.mix(playedColor(),Color.BLACK,.72f);}
    static int stateColor(long cell,long position,long buffered){return cell<position?playedColor():cell<Math.max(position,buffered)?bufferedColor():unplayedColor();}
    private String time(long value){long seconds=Math.max(0,value/1000);return seconds>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    @Override public void onDraw(Canvas canvas){
        float left=dp(3),right=getWidth()-dp(3),top=getHeight()-dp(24),bottom=getHeight()-dp(8),inset=dp(3);
        if(right<=left||top<0)return;
        long current=Math.max(0,scrub>=0?scrub:position),length=Math.max(0,duration);
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(0xe00b201c);canvas.drawRoundRect(left,top,right,bottom,dp(3),dp(3),paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(playedColor());canvas.drawRoundRect(left,top,right,bottom,dp(3),dp(3),paint);paint.setStyle(Paint.Style.FILL);
        float width=right-left-2*inset;int count=Math.max(1,(int)(width/dp(8)));float cell=width/count;
        for(int i=0;i<count;i++){
            long value=length<=0?Long.MAX_VALUE:(long)((i+.5)*length/count);
            int color=stateColor(value,current,buffered);
            paint.setShader(new LinearGradient(0,top+inset,0,bottom-inset,color,Color.rgb(Color.red(color)*2/3,Color.green(color)*2/3,Color.blue(color)*2/3),Shader.TileMode.CLAMP));
            float x=left+inset+i*cell;canvas.drawRect(x,top+inset,x+cell-dp(2),bottom-inset,paint);
        }
        paint.setShader(null);paint.setColor(Color.WHITE);paint.setTextSize(dp(12));paint.setTypeface(Typeface.MONOSPACE);paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(time(current),left,dp(14),paint);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(time(length),right,dp(14),paint);
        if(length<=0||chapters==null)return;
        paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(dp(10));paint.setStrokeWidth(dp(1));
        for(int i=0;i<chapters.length();i++){
            JSONObject chapter=chapters.optJSONObject(i);if(chapter==null)continue;String type=chapter.optString("MarkerType");
            if(!type.equals("IntroStart")&&!type.equals("IntroEnd")&&!type.equals("CreditsStart"))continue;
            long ticks=chapter.optLong("StartPositionTicks",-1);if(ticks<0)continue;long value=ticks/10000;if(value>=length)continue;
            float x=left+inset+width*value/length;paint.setColor(0xffbcebe0);canvas.drawLine(x,top-dp(5),x,top-dp(1),paint);
            if(type.equals("IntroEnd"))continue;
            String label=type.equals("IntroStart")?"片头":"片尾";
            float textWidth=paint.measureText(label);float textX=Math.max(left,Math.min(x-textWidth/2,right-textWidth));paint.setTextAlign(Paint.Align.LEFT);canvas.drawText(label,textX,top-dp(9),paint);
        }
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){
        // DefaultTimeBar keeps its internal hit target centered in this view.
        android.view.MotionEvent mapped=android.view.MotionEvent.obtain(event);mapped.setLocation(event.getX(),getHeight()/2f);
        boolean handled=super.onTouchEvent(mapped);mapped.recycle();return handled;
    }
}
