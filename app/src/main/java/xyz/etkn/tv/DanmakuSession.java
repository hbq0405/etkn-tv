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
    private boolean closed,firstLoad=true;private long wanted=-1,retryAt,base=-1;
    private final java.util.function.BiConsumer<String,Long> notice;
    private String lastNotice="";
    private void notifyOnce(String message,long duration){if(!lastNotice.equals(message)){lastNotice=message;notice.accept(message,duration);}}
    DanmakuSession(EtknApi api,String item,String session,Player player,FrameLayout stage,JSONObject style,java.util.function.BiConsumer<String,Long> notice){
        this.notice=notice;this.api=api;this.player=player;this.stage=stage;path="/api/tv/Items/"+EtknApi.encode(item)+"/Danmaku?local=true&session="+EtknApi.encode(session);
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
        if(firstLoad&&target==base&&lastNotice.isEmpty())notifyOnce("正在拉取弹幕…",0);
        CancellationSignal signal=new CancellationSignal();request=signal;wanted=target;
        network.execute(()->{
            try{
                JSONObject result=api.get(path+"&start="+target,signal);DanmakuTimeline timeline=new DanmakuTimeline(result.optJSONArray("comments"));
                main.post(()->{if(closed||signal.isCanceled()||request!=signal)return;request=null;
                    if(!result.optBoolean("enabled")){close();notice.accept("弹幕服务已关闭",3500L);return;}
                    if("stale".equals(result.optString("status"))){retryAt=android.os.SystemClock.elapsedRealtime()+1000;return;}
                    String status=result.optString("status");
                    if("busy".equals(status)||"unavailable".equals(status)){
                        if(firstLoad&&target==base)notifyOnce("busy".equals(status)?"弹幕服务繁忙，稍后自动重试":"弹幕拉取失败，稍后自动重试",3500);
                        retryAt=android.os.SystemClock.elapsedRealtime()+15000;return;
                    }
                    pages.put(target,timeline);overlay.setTimeline(DanmakuTimeline.merge(pages.values()));
                    if(firstLoad&&target==base){firstLoad=false;notifyOnce("unmatched".equals(status)?"未匹配到该影片的弹幕":timeline.cues.isEmpty()?"当前时段暂无弹幕":"弹幕已加载",3500);}
                });
            }catch(Exception ignored){main.post(()->{if(!closed&&!signal.isCanceled()&&request==signal){request=null;if(firstLoad&&target==base)notifyOnce("弹幕拉取失败，稍后自动重试",3500);retryAt=android.os.SystemClock.elapsedRealtime()+15000;}});}
        });
    }
    boolean active(){return !closed;}
    @Override public void close(){if(closed)return;closed=true;notice.accept("",0L);if(request!=null){request.cancel();request=null;}network.shutdownNow();main.removeCallbacksAndMessages(null);pages.clear();overlay.close();stage.removeView(overlay);}
}
