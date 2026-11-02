package xyz.etkn.tv;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Per-player lifetime; late responses and captured samples cannot cross episode/track changes. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class SubtitleTranslation implements TranslationTrackOutput.Sink {
    private final EtknApi api;
    private final String path,key;
    final int sourceIndex,sourceOrdinal;
    final String label;
    private final Supplier<ExoPlayer> player;
    private final BooleanSupplier enabled;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String,JSONObject> pending=new LinkedHashMap<>();
    private final TextView overlay;
    private final View original;
    private List<JSONObject> translated=new ArrayList<>();
    private volatile boolean closed;
    private boolean loading;
    private long lastRequest,generation;
    private volatile long lastPosition=-1;
    private final Runnable tick=this::update;
    SubtitleTranslation(EtknApi api,String itemId,String sourceId,JSONObject plan,PlayerView parent,Supplier<ExoPlayer> player,BooleanSupplier enabled){
        this.api=api;this.player=player;this.enabled=enabled;key=plan.optString("Key");
        original=parent.getSubtitleView();
        if(original!=null)original.setVisibility(View.INVISIBLE);
        sourceIndex=plan.optInt("SourceIndex",-1);sourceOrdinal=plan.optInt("SourceOrdinal",-1);
        label=("convert".equals(plan.optString("Mode"))?"繁简转换":"实时翻译")+"（"+("cht".equals(plan.optString("Target"))?"繁中":"简中")+"）";
        path="/api/tv/Items/"+EtknApi.encode(itemId)+"/"+EtknApi.encode(sourceId)+"/SubtitleTranslation";
        overlay=new TextView(parent.getContext());overlay.setTextColor(android.graphics.Color.WHITE);overlay.setTextSize(24);
        overlay.setGravity(Gravity.CENTER);overlay.setShadowLayer(3,1,1,android.graphics.Color.BLACK);overlay.setVisibility(View.GONE);
        FrameLayout.LayoutParams layout=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);layout.bottomMargin=(int)(parent.getResources().getDisplayMetrics().density*48);
        parent.addView(overlay,layout);handler.post(tick);
    }
    boolean active(){return !closed;}
    @Override public synchronized void cue(long start,long end,String text){
        if(closed||start<0||end<=start||text.length()>2000)return;
        String id=start+":"+end+":"+text;
        if(pending.size()>=512&&!pending.containsKey(id)){
            long position=Math.max(0,lastPosition);
            String farthest=null;long distance=Math.abs(start-position);
            for(java.util.Map.Entry<String,JSONObject> entry:pending.entrySet()){
                long candidate=Math.abs(entry.getValue().optLong("start")-position);
                if(candidate>distance){farthest=entry.getKey();distance=candidate;}
            }
            if(farthest==null)return;
            pending.remove(farthest);
        }
        try{pending.put(id,new JSONObject().put("start",start).put("end",end).put("text",text));}catch(Exception ignored){}
    }
    private void update(){
        if(closed)return;
        ExoPlayer current=player.get();
        boolean active=current!=null&&enabled.getAsBoolean();
        overlay.setVisibility(active?View.VISIBLE:View.GONE);
        if(active){
            long position=current.getCurrentPosition();
            boolean jumped=lastPosition>=0&&Math.abs(position-lastPosition)>5000;
            if(jumped){generation++;translated=new ArrayList<>();lastRequest=0;}
            lastPosition=position;
            StringBuilder text=new StringBuilder();
            for(JSONObject cue:translated)if(cue.optLong("start")<=position&&cue.optLong("end")>position){if(text.length()>0)text.append('\n');text.append(cue.optString("text"));}
            overlay.setText(text.toString());
            // The server's translated text already contains both lines in bilingual mode.
            // Keep capturing the source track, but never reveal it while waiting for a translation.
            if(original!=null)original.setVisibility(View.INVISIBLE);
            if(!loading&&(jumped||android.os.SystemClock.elapsedRealtime()-lastRequest>=2000))request(position);
        }else {overlay.setText("");if(original!=null)original.setVisibility(View.VISIBLE);}
        handler.postDelayed(tick,100);
    }
    private void request(long position){
        JSONArray batch=new JSONArray();List<String> sent=new ArrayList<>();
        synchronized(this){
            pending.entrySet().removeIf(entry->entry.getValue().optLong("end")<position-3000);
            List<java.util.Map.Entry<String,JSONObject>> nearest=new ArrayList<>(pending.entrySet());
            nearest.sort(java.util.Comparator.comparingLong(entry->Math.abs(entry.getValue().optLong("start")-position)));
            for(java.util.Map.Entry<String,JSONObject> entry:nearest){
                if(entry.getValue().optLong("start")>position+120000)continue;
                batch.put(entry.getValue());sent.add(entry.getKey());if(batch.length()>=64)break;
            }
        }
        long requestedGeneration=generation;
        loading=true;lastRequest=android.os.SystemClock.elapsedRealtime();
        network.execute(()->{
            try{
                JSONObject result=api.post(path,new JSONObject().put("key",key).put("position",position).put("cues",batch));
                List<JSONObject> received=new ArrayList<>();JSONArray cues=result.optJSONArray("Cues");
                if(cues!=null)for(int i=0;i<cues.length();i++){JSONObject cue=cues.optJSONObject(i);if(cue!=null)received.add(cue);}
                handler.post(()->{
                    if(closed)return;loading=false;
                    if(!result.optBoolean("Enabled")){close();return;}
                    ExoPlayer current=player.get();
                    if(current!=null&&generation==requestedGeneration)translated=received;
                    else lastRequest=0;
                    // Repeat unreturned cues: acceptance is asynchronous and the server may retry a failed batch.
                    synchronized(this){
                        for(String id:sent){JSONObject original=pending.get(id);if(original==null)continue;
                            for(JSONObject cue:received)if(cue.optLong("start")==original.optLong("start")&&cue.optLong("end")==original.optLong("end")&&cue.optString("source").equals(original.optString("text"))){pending.remove(id);break;}}
                    }
                });
            }catch(Exception ignored){handler.post(()->{if(!closed){loading=false;lastRequest=generation==requestedGeneration?android.os.SystemClock.elapsedRealtime()+3000:0;}});}
        });
    }
    void close(){closed=true;handler.removeCallbacksAndMessages(null);network.shutdownNow();overlay.setText("");if(original!=null)original.setVisibility(View.VISIBLE);if(overlay.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)overlay.getParent()).removeView(overlay);synchronized(this){pending.clear();}}
}
