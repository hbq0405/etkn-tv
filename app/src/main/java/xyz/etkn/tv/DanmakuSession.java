package xyz.etkn.tv;

import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import androidx.media3.common.Player;
import org.json.JSONObject;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

/** Only enabled playback owns requests and a renderer; adjacent windows are prefetched. */
final class DanmakuSession implements AutoCloseable {
    private static final long WINDOW=120000;
    private final EtknApi api;private final String path;
    private final Player player;private final FrameLayout stage;private final DanmakuOverlay overlay;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService network=new java.util.concurrent.ThreadPoolExecutor(1,1,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(1),new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    private final TreeMap<Long,DanmakuTimeline> pages=new TreeMap<>();
    private CancellationSignal request;
    private boolean closed;private long wanted=-1,retryAt,base=-1;
    DanmakuSession(EtknApi api,String item,String session,Player player,FrameLayout stage,JSONObject style){
        this.api=api;this.player=player;this.stage=stage;path="/api/tv/Items/"+EtknApi.encode(item)+"/Danmaku?local=true&session="+EtknApi.encode(session);
        overlay=new DanmakuOverlay(stage.getContext(),player,style);stage.addView(overlay,2,new FrameLayout.LayoutParams(-1,-1));tick();
    }
    void setStyle(JSONObject style){overlay.setStyle(style);}
    void tick(){
        if(closed)return;overlay.refresh();if(!player.isPlaying()){if(request!=null){request.cancel();request=null;}return;}
        long position=Math.max(0,player.getCurrentPosition()),current=position/WINDOW*WINDOW;
        if(current!=base){
            base=current;retryAt=0;
            if(request!=null&&(wanted<base||wanted>base+WINDOW)){request.cancel();request=null;}
            pages.keySet().removeIf(key->key<base-WINDOW||key>base+WINDOW);
            if(!pages.containsKey(base))overlay.setTimeline(new DanmakuTimeline(null));
        }
        long target=!pages.containsKey(base)?base:position>=base+WINDOW-40000&&!pages.containsKey(base+WINDOW)?base+WINDOW:-1;
        if(target<0||request!=null||android.os.SystemClock.elapsedRealtime()<retryAt)return;
        CancellationSignal signal=new CancellationSignal();request=signal;wanted=target;
        network.execute(()->{
            try{
                JSONObject result=api.get(path+"&start="+target,signal);DanmakuTimeline timeline=new DanmakuTimeline(result.optJSONArray("comments"));
                main.post(()->{if(closed||signal.isCanceled()||request!=signal)return;request=null;
                    if(!result.optBoolean("enabled")){close();return;}
                    if("stale".equals(result.optString("status"))){retryAt=android.os.SystemClock.elapsedRealtime()+1000;return;}
                    if("busy".equals(result.optString("status"))||"unavailable".equals(result.optString("status"))){retryAt=android.os.SystemClock.elapsedRealtime()+15000;return;}
                    pages.put(target,timeline);overlay.setTimeline(DanmakuTimeline.merge(pages.values()));
                });
            }catch(Exception ignored){main.post(()->{if(!closed&&!signal.isCanceled()&&request==signal){request=null;retryAt=android.os.SystemClock.elapsedRealtime()+15000;}});}
        });
    }
    boolean active(){return !closed;}
    @Override public void close(){if(closed)return;closed=true;if(request!=null){request.cancel();request=null;}network.shutdownNow();main.removeCallbacksAndMessages(null);pages.clear();overlay.close();stage.removeView(overlay);}
}
