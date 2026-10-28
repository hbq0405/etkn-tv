package xyz.etkn.tv;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import org.json.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends TvActivity {
    private ExoPlayer player;
    private PlayerView video;
    private JSONObject info,source;
    private String itemId,playSession="";
    private long position;
    private int selected;
    private boolean foreground,reportedStart;
    private final Handler timer=new Handler(Looper.getMainLooper());
    private final ExecutorService reports=Executors.newSingleThreadExecutor();
    private final Runnable progress=new Runnable(){@Override public void run(){if(player!=null){report("Progress");timer.postDelayed(this,10000);}}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);itemId=getIntent().getStringExtra("id");position=state==null?getIntent().getLongExtra("position",0):state.getLong("position");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root=column();root.setBackgroundColor(INK);root.setPadding(dp(18),dp(8),dp(18),dp(10));
        LinearLayout actions=row();actions.addView(button("返回",this::finish));actions.addView(button("版本",this::chooseVersion));actions.addView(button("音轨",()->chooseTrack(C.TRACK_TYPE_AUDIO)));actions.addView(button("字幕",()->chooseTrack(C.TRACK_TYPE_TEXT)));root.addView(actions);
        status=text(getIntent().getStringExtra("title"),14,MUTED);root.addView(status);
        video=new PlayerView(this);video.setUseController(true);video.setControllerAutoShow(true);video.setKeepContentOnPlayerReset(true);video.setShowSubtitleButton(false);
        root.addView(video,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);video.requestFocus();
        async(()->api.post("/emby/Items/"+EtknApi.encode(itemId)+"/PlaybackInfo?UserId="+EtknApi.encode(api.session.userId()),new JSONObject()),result->{info=result;playSession=info.optString("PlaySessionId");if(info.optJSONArray("MediaSources")==null||info.optJSONArray("MediaSources").length()==0){status.setText("暂无可播放版本");return;}if(info.optJSONArray("MediaSources").length()>1)chooseVersion();else if(foreground)startSource();});
    }
    private void chooseVersion(){
        if(info==null)return;JSONArray sources=info.optJSONArray("MediaSources");if(sources==null||sources.length()==0)return;
        String[] labels=new String[sources.length()];for(int i=0;i<labels.length;i++)labels[i]=sources.optJSONObject(i).optString("Name","版本 "+(i+1));
        new AlertDialog.Builder(this).setTitle("选择播放版本").setSingleChoiceItems(labels,selected,(dialog,index)->{selected=index;dialog.dismiss();stopPlayer();startSource();}).setNegativeButton("取消",null).show();
    }
    private void startSource(){
        if(!foreground||info==null||player!=null)return;
        try{
            source=info.getJSONArray("MediaSources").getJSONObject(selected);
            String address=source.getString("DirectStreamUrl");if(address.startsWith("/"))address=api.server+address;
            if(!Http.sameOrigin(address,api.server))throw new Exception("服务器返回了无效播放地址");
            // The authenticated ETKN URL carries its token in the query. No auth header is
            // attached to Media3, so a remote CDN redirect cannot receive ETKN credentials.
            DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(EtknApi.USER_AGENT).setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15000).setReadTimeoutMs(30000);
            player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build();video.setPlayer(player);
            List<MediaItem.SubtitleConfiguration> subtitles=new ArrayList<>();JSONArray streams=source.optJSONArray("MediaStreams");
            TrackSelectionParameters.Builder preferences=player.getTrackSelectionParameters().buildUpon();
            preferences.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,source.optInt("DefaultSubtitleStreamIndex",-1)<0);
            if(streams!=null)for(int i=0;i<streams.length();i++){
                JSONObject stream=streams.getJSONObject(i);String type=stream.optString("Type");
                if(stream.optBoolean("IsDefault")){if(type.equalsIgnoreCase("audio"))preferences.setPreferredAudioLanguage(stream.optString("Language"));if(type.equalsIgnoreCase("subtitle"))preferences.setPreferredTextLanguage(stream.optString("Language"));}
                if(!type.equalsIgnoreCase("subtitle")||!stream.optBoolean("IsExternal"))continue;
                String delivery=stream.optString("DeliveryUrl");if(delivery.startsWith("/"))delivery=api.server+delivery;if(!Http.sameOrigin(delivery,api.server))continue;
                Uri uri=Uri.parse(delivery).buildUpon().appendQueryParameter("X-Emby-Token",api.token).appendQueryParameter("UserId",api.session.userId()).build();
                String codec=stream.optString("Codec").toLowerCase(Locale.ROOT);
                String mime=codec.contains("ass")||codec.contains("ssa")?MimeTypes.TEXT_SSA:codec.contains("vtt")?MimeTypes.TEXT_VTT:MimeTypes.APPLICATION_SUBRIP;
                subtitles.add(new MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setLanguage(stream.optString("Language")).setLabel(stream.optString("DisplayTitle","外挂字幕")).setSelectionFlags(stream.optBoolean("IsDefault")?C.SELECTION_FLAG_DEFAULT:0).build());
            }
            player.setTrackSelectionParameters(preferences.build());
            player.setMediaItem(new MediaItem.Builder().setUri(address).setSubtitleConfigurations(subtitles).build(),Math.max(0,position));
            reportedStart=false;
            player.addListener(new Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY&&!reportedStart){reportedStart=true;report("Started");timer.postDelayed(progress,10000);status.setText(getIntent().getStringExtra("title")+"  ·  "+source.optString("Name"));}if(state==Player.STATE_ENDED){report("Stopped");status.setText("播放完成");}}
                @Override public void onPlayerError(PlaybackException error){status.setText("播放失败："+error.getErrorCodeName()+"。可切换版本或返回重试。");report("Stopped");}
            });
            player.prepare();player.play();video.requestFocus();
        }catch(Exception error){status.setText(error.getMessage()==null?"无法开始播放":error.getMessage());}
    }
    private void chooseTrack(int type){
        if(player==null)return;List<Runnable> choose=new ArrayList<>();List<String> labels=new ArrayList<>();
        if(type==C.TRACK_TYPE_TEXT){labels.add("关闭字幕");choose.add(()->player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(type,true).build()));}
        for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==type)for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)){
            int index=i;Format format=group.getTrackFormat(i);labels.add((group.isTrackSelected(i)?"✓ ":"")+(format.label!=null?format.label:format.language!=null?format.language:"轨道 "+(labels.size()+1))+(format.codecs==null?"":"  "+format.codecs));
            choose.add(()->player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(type,false).setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),index)).build()));
        }
        if(labels.isEmpty()){status.setText("暂无可选择的轨道");return;}
        new AlertDialog.Builder(this).setTitle(type==C.TRACK_TYPE_AUDIO?"音轨":"字幕").setItems(labels.toArray(new String[0]),(dialog,index)->{if(player!=null)choose.get(index).run();}).setNegativeButton("取消",null).show();
    }
    private void report(String event){
        if(source==null)return;long milliseconds=player==null?position:Math.max(0,player.getCurrentPosition());boolean paused=player==null||!player.isPlaying();long duration=player==null?source.optLong("RunTimeTicks"):player.getDuration()>0?player.getDuration()*10000:source.optLong("RunTimeTicks");
        try{JSONObject payload=new JSONObject().put("ItemId",itemId).put("UserId",api.session.userId()).put("MediaSourceId",source.optString("Id")).put("PlaySessionId",playSession).put("PositionTicks",milliseconds*10000).put("RunTimeTicks",duration).put("IsPaused",paused).put("CanSeek",true).put("PlayMethod","DirectStream").put("Client","ETKN TV");
            reports.execute(()->{try{api.post("/emby/Sessions/Playing"+(event.equals("Started")?"":"/"+event),payload);}catch(Exception ignored){}});
        }catch(Exception ignored){}
    }
    private void stopPlayer(){timer.removeCallbacks(progress);if(player!=null){position=Math.max(0,player.getCurrentPosition());report("Stopped");video.setPlayer(null);player.release();player=null;}}
    @Override protected void onStart(){super.onStart();foreground=true;if(info!=null&&player==null)startSource();}
    @Override protected void onStop(){foreground=false;stopPlayer();super.onStop();}
    @Override protected void onDestroy(){timer.removeCallbacksAndMessages(null);reports.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putLong("position",player==null?position:player.getCurrentPosition());super.onSaveInstanceState(state);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(event.getKeyCode()==KeyEvent.KEYCODE_MENU&&event.getAction()==KeyEvent.ACTION_DOWN){chooseTrack(C.TRACK_TYPE_TEXT);return true;}return video!=null&&video.dispatchMediaKeyEvent(event)||super.dispatchKeyEvent(event);}
}
