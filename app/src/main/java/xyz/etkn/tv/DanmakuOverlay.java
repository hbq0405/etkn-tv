package xyz.etkn.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import androidx.media3.common.Player;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Vsync drawing uses the media clock, with shaping and fallback fonts measured once. */
final class DanmakuOverlay extends View {
    private final Player player;
    private final android.text.TextPaint paint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint bitmapPaint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final android.util.LruCache<String,android.graphics.Bitmap> glyphs=new android.util.LruCache<String,android.graphics.Bitmap>(8*1024*1024){
        @Override protected int sizeOf(String key,android.graphics.Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    private DanmakuTimeline timeline=new DanmakuTimeline(null);
    private List<DanmakuLayout.Entry> placed=new ArrayList<>();
    private int rows,alpha,density;private long life;private boolean themeColor;
    private float lineHeight,top;
    private boolean closed,layoutDirty=true,framePending;
    private String styleKey="";
    private final Runnable frame=()->{framePending=false;if(!closed)invalidate();};
    DanmakuOverlay(Context context,Player player,JSONObject style){
        super(context);this.player=player;setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setTypeface(android.graphics.Typeface.createFromAsset(context.getAssets(),"fonts/NotoColorEmoji.ttf"));setStyle(style);
    }
    void setStyle(JSONObject style){
        if(style==null)style=new JSONObject();String key=style.toString();if(key.equals(styleKey))return;styleKey=key;
        rows=Math.max(1,Math.min(16,style.optInt("rows",8)));density=Math.max(10,Math.min(100,style.optInt("density",100)));themeColor=style.optBoolean("theme_color",false);
        int font=Math.max(12,Math.min(40,style.optInt("font_size",21)));
        alpha=Math.round(Math.max(10,Math.min(100,style.optInt("opacity",85)))*2.55f);
        life=Math.round(8000/Math.max(.5,Math.min(2,style.optDouble("speed",1))));
        float density=getResources().getDisplayMetrics().density;paint.setTextSize(font*density);paint.setShadowLayer(density,0,density,0xff000000);
        Paint.FontMetrics metrics=paint.getFontMetrics();lineHeight=metrics.descent-metrics.ascent+4*density;top=-metrics.ascent+8*density;
        glyphs.evictAll();layoutDirty=true;refresh();
    }
    void setTimeline(DanmakuTimeline value){timeline=value;layoutDirty=true;refresh();}
    void refresh(){if(!closed){if(!player.isPlaying()){removeCallbacks(frame);framePending=false;}invalidate();}}
    void close(){closed=true;removeCallbacks(frame);framePending=false;timeline=new DanmakuTimeline(null);placed.clear();glyphs.evictAll();setVisibility(GONE);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){layoutDirty=true;}
    private void layoutCues(){
        int lanes=Math.max(1,Math.min(rows,1+(int)((getHeight()*.8f-top)/lineHeight)));
        placed=DanmakuLayout.place(timeline,lanes,getWidth(),life,16*getResources().getDisplayMetrics().density,density,text->paint.measureText(text));
        layoutDirty=false;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(closed)return;if(layoutDirty)layoutCues();long now=Math.max(0,player.getCurrentPosition());
        int index=DanmakuLayout.firstAt(placed,now-life);
        for(int i=index;i<placed.size();i++){
            DanmakuLayout.Entry item=placed.get(i);if(item.start>now)break;if(now-item.start>=life)continue;
            int color=themeColor?TvAppearance.primary():item.cue.color;String key=color+"/"+item.cue.text;
            android.graphics.Bitmap bitmap=glyphs.get(key);float padding=2*getResources().getDisplayMetrics().density;
            if(bitmap==null){
                Paint.FontMetrics metrics=paint.getFontMetrics();
                bitmap=android.graphics.Bitmap.createBitmap(Math.max(1,(int)Math.ceil(item.width+2*padding)),Math.max(1,(int)Math.ceil(metrics.descent-metrics.ascent+2*padding)),android.graphics.Bitmap.Config.ARGB_8888);
                paint.setColor(color);paint.setAlpha(255);new Canvas(bitmap).drawText(item.cue.text,padding,padding-metrics.ascent,paint);glyphs.put(key,bitmap);
            }
            bitmapPaint.setAlpha(alpha);canvas.drawBitmap(bitmap,item.x(now,getWidth(),life)-padding,top+lineHeight*item.lane+paint.getFontMetrics().ascent-padding,bitmapPaint);
        }
        if(player.isPlaying()&&!timeline.cues.isEmpty()&&!framePending){framePending=true;postOnAnimation(frame);}
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(frame);framePending=false;super.onDetachedFromWindow();}
}
