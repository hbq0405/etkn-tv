package xyz.etkn.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import androidx.media3.common.Player;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Canvas overlay driven exclusively by the media clock; no focus or touch target. */
final class DanmakuOverlay extends View {
    private final Player player;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private DanmakuTimeline timeline=new DanmakuTimeline(null);
    private final List<Placed> placed=new ArrayList<>();
    private final int rows,alpha;
    private final long life;
    private final float lineHeight,top;
    private boolean closed,layoutDirty=true;
    private final Runnable frame=()->{if(!closed)invalidate();};
    private static final class Placed {
        final DanmakuTimeline.Cue cue;final float width,y;final boolean visible;
        Placed(DanmakuTimeline.Cue cue,float width,float y,boolean visible){this.cue=cue;this.width=width;this.y=y;this.visible=visible;}
    }
    DanmakuOverlay(Context context,Player player,JSONObject style){
        super(context);this.player=player;setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if(style==null)style=new JSONObject();
        rows=Math.max(1,Math.min(16,style.optInt("rows",8)));
        int font=Math.max(12,Math.min(40,style.optInt("font_size",21)));
        alpha=Math.round(Math.max(10,Math.min(100,style.optInt("opacity",85)))*2.55f);
        life=Math.round(8000/Math.max(.5,Math.min(2,style.optDouble("speed",1))));
        float density=getResources().getDisplayMetrics().density;paint.setTextSize(font*density);paint.setShadowLayer(2*density,0,density,0xff000000);lineHeight=(font+8)*density;top=(font+4)*density;
    }
    void setTimeline(DanmakuTimeline value){timeline=value;layoutDirty=true;refresh();}
    void refresh(){removeCallbacks(frame);if(!closed)invalidate();}
    void close(){closed=true;removeCallbacks(frame);timeline=new DanmakuTimeline(null);placed.clear();setVisibility(GONE);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){layoutDirty=true;}
    private void layoutCues(){
        placed.clear();int lanes=Math.max(1,Math.min(rows,1+(int)((getHeight()*.68f-top)/lineHeight)));
        long[] available=new long[lanes];
        for(DanmakuTimeline.Cue cue:timeline.cues){
            int lane=-1;
            // One shared lane budget also prevents fixed/reverse comments colliding.
            for(int i=0;i<lanes;i++){int candidate=cue.mode==4?lanes-1-i:i;if(cue.time>=available[candidate]){lane=candidate;break;}}
            boolean visible=lane>=0;if(visible)available[lane]=cue.time+life;
            placed.add(new Placed(cue,paint.measureText(cue.text),top+lineHeight*Math.max(0,lane),visible));
        }
        layoutDirty=false;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(closed)return;if(layoutDirty)layoutCues();long now=Math.max(0,player.getCurrentPosition());
        int index=timeline.firstAt(now-life);boolean active=false;
        for(int i=index;i<placed.size();i++){
            Placed item=placed.get(i);if(item.cue.time>now)break;if(!item.visible)continue;
            float elapsed=(now-item.cue.time)/(float)life;if(elapsed>=1)continue;active=true;
            float x=item.cue.mode==4||item.cue.mode==5?(getWidth()-item.width)/2:item.cue.mode==6?-item.width+(getWidth()+item.width)*elapsed:getWidth()-(getWidth()+item.width)*elapsed;
            paint.setColor(item.cue.color);paint.setAlpha(alpha);canvas.drawText(item.cue.text,x,item.y,paint);
        }
        if(player.isPlaying()&&!timeline.cues.isEmpty())postDelayed(frame,active?33:200);
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(frame);super.onDetachedFromWindow();}
}
