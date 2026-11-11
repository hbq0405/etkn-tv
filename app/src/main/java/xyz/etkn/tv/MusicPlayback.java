package xyz.etkn.tv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import okhttp3.HttpUrl;
import org.json.*;
import java.util.*;
import java.util.concurrent.Executors;

/** One account-bound audio session shared by the browser and full-screen player. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class MusicPlayback {
    interface Listener { void changed(); }
    private static MusicPlayback instance;
    private static final java.util.concurrent.ExecutorService REPORTS=Executors.newSingleThreadExecutor();
    final MusicQueue queue=new MusicQueue();
    final EtknApi api;
    private final Context context;
    private final Handler timer=new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners=new LinkedHashSet<>();
    private final Random random=new Random();
    private ExoPlayer player;
    private JSONObject source;
    private String session="";
    private int generation,queueGeneration;
    private boolean started,loading,moreLoading,pendingNext;
    private android.os.CancellationSignal request;
    String message="";
    private long lastReport;
    private float volume=1;
    private MusicPlayback(Context context){this.context=context.getApplicationContext();api=new EtknApi(context);}
    static MusicPlayback get(Context context){
        if(instance==null||!TvAppearance.sameSession(instance.api,instance.api.userId)){stop();instance=new MusicPlayback(context);}
        return instance;
    }
    static boolean active(){return instance!=null&&instance.queue.current()!=null&&TvAppearance.sameSession(instance.api,instance.api.userId);}
    static void stop(){if(instance!=null)instance.clear();}
    void addListener(Listener listener){listeners.add(listener);}
    void removeListener(Listener listener){listeners.remove(listener);}
    private void changed(){for(Listener listener:new ArrayList<>(listeners))listener.changed();}
    private void clear(){queueGeneration++;release(false);queue.items.clear();queue.index=-1;queue.total=0;queue.path="";moreLoading=false;pendingNext=false;message="";changed();}
    void play(List<JSONObject> tracks,JSONObject selected,String path,int total){
        queueGeneration++;queue.replace(tracks,selected.optString("Id"),path,total);moreLoading=false;pendingNext=false;playAt(queue.index);
    }
    void playAt(int index){
        if(index<0||index>=queue.items.size())return;
        if(!TvAppearance.sameSession(api,api.userId)){clear();return;}
        release(false);queue.index=index;loading=true;message="正在读取音乐…";changed();
        final int ticket=generation;final JSONObject item=queue.current();request=new android.os.CancellationSignal();final android.os.CancellationSignal cancellation=request;
        TvActivity.IO.execute(()->{
            try{JSONObject info=api.post("/api/tv/Items/"+EtknApi.encode(item.optString("Id"))+"/PlaybackInfo",new JSONObject(),cancellation);
                timer.post(()->{
                    if(ticket!=generation||!TvAppearance.sameSession(api,api.userId))return;
                    try{
                        JSONArray sources=info.optJSONArray("MediaSources");if(sources==null||sources.length()==0)throw new Exception("这首歌暂无可播放版本");
                        source=sources.getJSONObject(0);session=info.optString("PlaySessionId");
                        String address=PlaybackAddress.resolve(source,api.server,api.token,api.userId);
                        address=HttpUrl.get(address).newBuilder().setQueryParameter("X-Emby-Token",api.token).setQueryParameter("UserId",api.userId).setQueryParameter("PlaySessionId",session).setQueryParameter("MediaSourceId",source.optString("Id")).build().toString();
                        player=TvPlayerFactory.create(context,null,false,false,false);
                        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true);
                        player.setHandleAudioBecomingNoisy(true);player.setVolume(volume);
                        player.addListener(new Player.Listener(){
                            @Override public void onPlaybackStateChanged(int state){
                                if(ticket!=generation)return;
                                if(state==Player.STATE_READY){loading=false;message="";if(!started){started=true;report("",false);}changed();}
                                else if(state==Player.STATE_ENDED){report("Stopped",true);source=null;next(1,true);}
                            }
                            @Override public void onIsPlayingChanged(boolean playing){if(ticket==generation){changed();if(started)report("Progress",false);}}
                            @Override public void onPlayerError(PlaybackException error){if(ticket!=generation)return;loading=false;message="播放失败，可重试或切换歌曲（"+error.getErrorCodeName()+"）";report("Stopped",false);source=null;changed();}
                        });
                        player.setMediaItem(MediaItem.fromUri(address));player.prepare();player.play();lastReport=android.os.SystemClock.elapsedRealtime();timer.post(tick);
                        requestMoreIfNeeded();changed();
                    }catch(Exception|LinkageError error){loading=false;message="无法播放这首歌，请重试";release(false);changed();}
                });
            }catch(Exception error){timer.post(()->{if(ticket==generation){loading=false;message=error instanceof EtknApi.AuthException?"登录已过期，请重新登录":"无法读取音乐，请重试";changed();}});}
        });
    }
    boolean playing(){return player!=null&&player.isPlaying();}
    boolean loading(){return loading;}
    long position(){return player==null?0:Math.max(0,player.getCurrentPosition());}
    long duration(){return player!=null&&player.getDuration()>0?player.getDuration():queue.current()==null?0:queue.current().optLong("RunTimeTicks")/10000;}
    void toggle(){if(player==null||player.getPlayerError()!=null){playAt(queue.index);return;}if(player.isPlaying())player.pause();else player.play();changed();}
    void seek(long position){if(player!=null&&player.isCurrentMediaItemSeekable()){player.seekTo(Math.max(0,Math.min(duration(),position)));if(started)report("Progress",false);changed();}}
    float volume(){return volume;}
    void volume(float value){volume=Math.max(0,Math.min(1,value));if(player!=null)player.setVolume(volume);changed();}
    void mode(int mode){queue.mode=mode;pendingNext=false;if(mode==1)loadMore();changed();}
    void next(int direction,boolean ended){
        if(direction<0&&queue.index==0&&queue.mode!=1&&queue.mode!=2){seek(0);return;}
        if(ended&&queue.mode!=3&&queue.index+1>=queue.items.size()&&queue.items.size()<queue.total&&!queue.path.isEmpty()){
            release(false);pendingNext=true;loading=true;message="正在读取下一页歌曲…";loadMore();changed();return;
        }
        int next=queue.next(direction,ended,random);
        if(next>=0)playAt(next);else if(direction>0&&queue.items.size()<queue.total&&!queue.path.isEmpty()){pendingNext=true;loadMore();}
        else{release(false);message=ended?"已播放完列表":"没有更多歌曲";changed();}
    }
    void remove(int index){
        if(index<0||index>=queue.items.size())return;
        if(index==queue.index)release(false);
        queue.items.remove(index);queue.total=queue.items.size();queue.path="";queueGeneration++;moreLoading=false;
        if(queue.items.isEmpty()){clear();return;}
        if(index<queue.index)queue.index--;else if(index==queue.index){queue.index=Math.min(index,queue.items.size()-1);playAt(queue.index);}changed();
    }
    boolean hasMore(){return !queue.path.isEmpty()&&queue.items.size()<queue.total;}
    boolean moreLoading(){return moreLoading;}
    private void requestMoreIfNeeded(){if(queue.mode==1||queue.index>=queue.items.size()-4)loadMore();}
    void loadMore(){
        if(moreLoading||!hasMore())return;moreLoading=true;final int ticket=queueGeneration,offset=queue.items.size();String path=queue.path;
        TvActivity.IO.execute(()->{try{JSONObject page=api.get(path+"&StartIndex="+offset+"&Limit=200");timer.post(()->{
            if(ticket!=queueGeneration||!TvAppearance.sameSession(api,api.userId))return;
            moreLoading=false;int previous=queue.items.size();queue.append(EtknApi.rows(page));queue.total=page.optInt("TotalRecordCount",queue.total);
            if(queue.items.size()==previous)queue.total=queue.items.size();
            if(pendingNext){pendingNext=false;loading=false;int target=queue.index+1;if(target<queue.items.size())playAt(target);else{message="已播放完列表";changed();}}
            else{changed();if(queue.mode==1)loadMore();}
        });}catch(Exception error){timer.post(()->{if(ticket==queueGeneration){moreLoading=false;loading=false;pendingNext=false;message="后续歌曲加载失败，可重新加载";changed();}});}});
    }
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(!TvAppearance.sameSession(api,api.userId)){clear();return;}
        if(player==null)return;
        long now=android.os.SystemClock.elapsedRealtime();if(started&&now-lastReport>=10000){report("Progress",false);lastReport=now;}
        changed();timer.postDelayed(this,1000);
    }};
    private void report(String event,boolean completed){
        JSONObject item=queue.current();if(item==null||source==null)return;
        try{JSONObject payload=new JSONObject().put("ItemId",item.optString("Id")).put("UserId",api.userId).put("MediaSourceId",source.optString("Id")).put("PlaySessionId",session).put("PositionTicks",position()*10000).put("IsPaused",!playing()).put("PlayedToCompletion",completed);
            REPORTS.execute(()->{try{api.post("/api/tv/Sessions/Playing"+(event.isEmpty()?"":"/"+event),payload);}catch(Exception ignored){}});
        }catch(JSONException ignored){}
    }
    private void release(boolean completed){
        generation++;if(request!=null){request.cancel();request=null;}timer.removeCallbacks(tick);report("Stopped",completed);source=null;session="";started=false;loading=false;
        if(player!=null){player.release();player=null;}
    }
}
