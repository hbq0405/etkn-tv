package xyz.etkn.tv;

import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import androidx.media3.common.Player;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;

/** Created only for an enabled playback; close releases requests, thread and view. */
final class DanmakuSession implements AutoCloseable {
    private final EtknApi api;private final String path;
    private final Player player;private final FrameLayout stage;private final DanmakuOverlay overlay;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService network=new java.util.concurrent.ThreadPoolExecutor(1,1,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(1),new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    private CancellationSignal request;
    private boolean closed;private long loaded=-1,wanted=-1,retryAt;
    DanmakuSession(EtknApi api,String item,String session,Player player,FrameLayout stage,JSONObject style){
        this.api=api;this.player=player;this.stage=stage;path="/api/tv/Items/"+EtknApi.encode(item)+"/Danmaku?session="+EtknApi.encode(session);
        overlay=new DanmakuOverlay(stage.getContext(),player,style);stage.addView(overlay,2,new FrameLayout.LayoutParams(-1,-1));tick();
    }
    void tick(){
        if(closed)return;overlay.refresh();if(!player.isPlaying()){if(request!=null){request.cancel();request=null;}return;}long target=Math.max(0,player.getCurrentPosition())/120000*120000;
        if(target==loaded||(target==wanted&&request!=null)||android.os.SystemClock.elapsedRealtime()<retryAt)return;
        if(request!=null)request.cancel();CancellationSignal signal=new CancellationSignal();request=signal;wanted=target;
        if(loaded!=target)overlay.setTimeline(new DanmakuTimeline(null));
        network.execute(()->{
            try{
                JSONObject result=api.get(path+"&start="+target,signal);DanmakuTimeline timeline=new DanmakuTimeline(result.optJSONArray("comments"));
                main.post(()->{if(closed||signal.isCanceled()||wanted!=target)return;request=null;
                    if(!result.optBoolean("enabled")){close();return;}
                    if("stale".equals(result.optString("status"))){retryAt=android.os.SystemClock.elapsedRealtime()+1000;return;}
                    if("busy".equals(result.optString("status"))||"unavailable".equals(result.optString("status"))){retryAt=android.os.SystemClock.elapsedRealtime()+15000;return;}
                    loaded=target;overlay.setTimeline(timeline);
                });
            }catch(Exception ignored){main.post(()->{if(!closed&&!signal.isCanceled()&&wanted==target){request=null;retryAt=android.os.SystemClock.elapsedRealtime()+15000;}});}
        });
    }
    boolean active(){return !closed;}
    @Override public void close(){if(closed)return;closed=true;if(request!=null){request.cancel();request=null;}network.shutdownNow();main.removeCallbacksAndMessages(null);overlay.close();stage.removeView(overlay);}
}
