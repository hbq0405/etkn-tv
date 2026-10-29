package xyz.etkn.tv;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.DefaultTimeBar;
import org.json.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends TvActivity {
    private static final int BUFFERED=android.graphics.Color.rgb(90,178,230),BAR_IDLE=android.graphics.Color.rgb(72,82,92);
    private ExoPlayer player;
    private PlayerView video;
    private DefaultTimeBar seekProgress;
    private LinearLayout controls;
    private Button audioButton,subtitleButton,decoderButton,skipButton,nextButton;
    private AlertDialog trackDialog;
    private JSONObject info,source,nextEpisode;
    private String itemId,playSession="";
    private String seasonKey;
    private int missingSeasonAudio=-1;
    private long position;
    private int selected;
    private boolean foreground,reportedStart,versionConfirmed,audioPending,subtitlePending;
    private boolean softwareVideo,playWhenReady=true;
    private boolean softwareAudio=true,enhancedSubtitles=true;
    private boolean videoFrameSeen;
    private long videoReadySince;
    private String videoDecoder="等待解码器";
    private TextView videoInfo;
    private int skipMode;
    private long skipTarget=-1;
    private long pendingSeekPosition=-1;
    private boolean seekKeys,focusNextWhenLoaded;
    private int audioIndex=PlaybackSelection.AUTO,subtitleIndex=PlaybackSelection.AUTO;
    private final Handler timer=new Handler(Looper.getMainLooper());
    private final Runnable seekCommit=this::commitSeek;
    private final Runnable seekProgressHide=this::hideSeekProgress;
    private final Runnable seekProgressUpdate=new Runnable(){@Override public void run(){
        if(player==null||!seekKeys)return;
        seekProgress.setDuration(Math.max(0,player.getDuration()));seekProgress.setPosition(pendingSeekPosition>=0?pendingSeekPosition:player.getCurrentPosition());seekProgress.setBufferedPosition(player.getBufferedPosition());timer.postDelayed(this,200);
    }};
    private final ExecutorService reports=Executors.newSingleThreadExecutor();
    private final Runnable progress=new Runnable(){@Override public void run(){if(player!=null){report("Progress");timer.postDelayed(this,10000);}}};
    private final Runnable skipCheck=new Runnable(){@Override public void run(){if(player!=null){updateSkip();timer.postDelayed(this,400);}}};
    private final Runnable videoCheck=new Runnable(){@Override public void run(){
        if(player==null)return;
        boolean expected=player.getCurrentTracks().containsType(C.TRACK_TYPE_VIDEO);
        if(!expected||videoFrameSeen||!player.getPlayWhenReady()||player.getPlaybackState()!=Player.STATE_READY)videoReadySince=0;
        else if(videoReadySince==0)videoReadySince=android.os.SystemClock.elapsedRealtime();
        else if(android.os.SystemClock.elapsedRealtime()-videoReadySince>=6000){recoverVideo();return;}
        updateVideoInfo();timer.postDelayed(this,1000);
    }};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);itemId=getIntent().getStringExtra("id");seasonKey=getIntent().getStringExtra("seasonKey");position=state==null?getIntent().getLongExtra("position",0):state.getLong("position");
        audioIndex=state==null?getIntent().getIntExtra("audioIndex",PlaybackSelection.AUTO):state.getInt("audioIndex",PlaybackSelection.AUTO);subtitleIndex=state==null?getIntent().getIntExtra("subtitleIndex",PlaybackSelection.AUTO):state.getInt("subtitleIndex",PlaybackSelection.AUTO);
        softwareVideo=state!=null&&state.getBoolean("softwareVideo");
        softwareAudio=state==null||state.getBoolean("softwareAudio",true);enhancedSubtitles=state==null||state.getBoolean("enhancedSubtitles",true);
        playWhenReady=state==null||state.getBoolean("playWhenReady",true);skipMode=new TvPreferences(this,api).skipMode();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(android.graphics.Color.BLACK);
        video=new PlayerView(this);video.setUseController(true);video.setControllerAutoShow(false);video.setControllerShowTimeoutMs(5000);video.setKeepContentOnPlayerReset(true);video.setShowSubtitleButton(false);video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        View centerControls=video.findViewById(androidx.media3.ui.R.id.exo_center_controls);((ViewGroup)centerControls.getParent()).removeView(centerControls);
        DefaultTimeBar controllerTimeBar=video.findViewById(androidx.media3.ui.R.id.exo_progress);
        if(controllerTimeBar!=null){controllerTimeBar.setPlayedColor(ACCENT);controllerTimeBar.setScrubberColor(ACCENT);controllerTimeBar.setBufferedColor(BUFFERED);controllerTimeBar.setUnplayedColor(BAR_IDLE);}
        stage.addView(video,new FrameLayout.LayoutParams(-1,-1));
        seekProgress=new DefaultTimeBar(this);seekProgress.setEnabled(false);seekProgress.setPlayedColor(ACCENT);seekProgress.setScrubberColor(ACCENT);seekProgress.setVisibility(View.GONE);
        FrameLayout.LayoutParams seekLayout=new FrameLayout.LayoutParams(-1,dp(32),Gravity.BOTTOM);seekLayout.setMargins(dp(28),0,dp(28),dp(28));stage.addView(seekProgress,seekLayout);
        controls=column();controls.setPadding(dp(28),dp(10),dp(28),dp(8));controls.setBackgroundColor(android.graphics.Color.argb(205,0,0,0));
        controls.addView(text(getIntent().getStringExtra("title"),18,android.graphics.Color.WHITE));
        videoInfo=text("",12,MUTED);controls.addView(videoInfo);
        LinearLayout actions=row();actions.addView(button("版本",this::chooseVersion));
        audioButton=button("音轨",()->chooseTrack(C.TRACK_TYPE_AUDIO));subtitleButton=button("字幕",()->chooseTrack(C.TRACK_TYPE_TEXT));decoderButton=button("解码",this::chooseDecoder);
        actions.addView(audioButton);actions.addView(subtitleButton);actions.addView(decoderButton);
        nextButton=button("下一集",this::playNextEpisode);nextButton.setVisibility(View.GONE);actions.addView(nextButton);
        HorizontalScrollView actionScroll=new FocusScroll.Row(this);actionScroll.setHorizontalScrollBarEnabled(false);actionScroll.addView(actions);controls.addView(actionScroll);
        FrameLayout.LayoutParams overlay=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);overlay.bottomMargin=dp(96);stage.addView(controls,overlay);controls.setVisibility(View.GONE);
        video.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)visibility->{controls.setVisibility(visibility);if(visibility!=View.VISIBLE){focusNextWhenLoaded=false;video.requestFocus();}});
        skipButton=button("跳过片头",this::skipSegment);skipButton.setVisibility(View.GONE);FrameLayout.LayoutParams skipLayout=new FrameLayout.LayoutParams(-2,dp(44),Gravity.BOTTOM|Gravity.END);skipLayout.rightMargin=dp(28);skipLayout.bottomMargin=dp(220);stage.addView(skipButton,skipLayout);
        status=text("正在读取播放信息…",15,android.graphics.Color.WHITE);status.setPadding(dp(24),dp(12),dp(24),dp(12));status.setBackgroundColor(android.graphics.Color.argb(180,0,0,0));stage.addView(status,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        setContentView(stage);video.hideController();video.requestFocus();
        String requestedSource=state==null?getIntent().getStringExtra("sourceId"):state.getString("sourceId",getIntent().getStringExtra("sourceId"));
        async(()->api.post("/api/tv/Items/"+EtknApi.encode(itemId)+"/PlaybackInfo?UserId="+EtknApi.encode(api.session.userId()),new JSONObject()),result->{
            info=result;playSession=info.optString("PlaySessionId");JSONArray sources=info.optJSONArray("MediaSources");
            if(sources==null||sources.length()==0){status.setText("暂无可播放版本");return;}
            int preferred=PlaybackSelection.sourceIndex(sources,requestedSource);
            if(requestedSource!=null&&!requestedSource.isEmpty()&&preferred<0){status.setText("所选版本已不可用，请返回重新选择");return;}
            if(preferred>=0){selected=preferred;versionConfirmed=true;startSource();}
            else if(sources.length()>1&&!getIntent().getBooleanExtra("resume",false))chooseVersion();else{versionConfirmed=true;startSource();}
        });
        loadNextEpisode();
    }
    private void loadNextEpisode(){
        String series=getIntent().getStringExtra("seriesId");if(series==null||series.isEmpty())return;
        async(()->{
            try{return PlaybackSelection.nextEpisode(EtknApi.rows(api.get("/api/tv/Shows/"+EtknApi.encode(series)+"/Episodes?UserId="+EtknApi.encode(api.session.userId())+"&IsMissing=false")),itemId);}
            catch(EtknApi.AuthException error){throw error;}
            catch(Exception ignored){return null;}
        },episode->{nextEpisode=episode;nextButton.setVisibility(episode==null?View.GONE:View.VISIBLE);if(episode!=null&&focusNextWhenLoaded&&controls.getVisibility()==View.VISIBLE)nextButton.requestFocus();focusNextWhenLoaded=false;});
    }
    private void playNextEpisode(){
        if(nextEpisode==null||isFinishing())return;
        Intent next=new Intent(this,PlayerActivity.class).putExtra("id",nextEpisode.optString("Id")).putExtra("title",EtknApi.resumeTitle(nextEpisode)).putExtra("seriesId",getIntent().getStringExtra("seriesId")).putExtra("seasonKey",PlaybackSelection.seasonKey(nextEpisode)).putExtra("resume",true);
        stopPlayer();startActivity(next);finish();
    }
    private void chooseVersion(){
        if(info==null)return;JSONArray sources=info.optJSONArray("MediaSources");if(sources==null||sources.length()==0)return;
        String[] labels=new String[sources.length()];for(int i=0;i<labels.length;i++)labels[i]=sources.optJSONObject(i).optString("Name","版本 "+(i+1));
        new AlertDialog.Builder(this).setTitle("选择播放版本").setSingleChoiceItems(labels,selected,(dialog,index)->{stopPlayer();selected=index;versionConfirmed=true;audioIndex=subtitleIndex=PlaybackSelection.AUTO;dialog.dismiss();startSource();}).setNegativeButton("取消",null).show();
    }
    private void startSource(){
        if(!foreground||!versionConfirmed||info==null||player!=null)return;
        try{
            source=info.getJSONArray("MediaSources").getJSONObject(selected);
            int locked=new TvPreferences(this,api).seasonAudio(seasonKey);
            if(locked>=0){audioIndex=PlaybackSelection.audioAt(source,locked);if(audioIndex==PlaybackSelection.AUTO&&missingSeasonAudio!=locked){missingSeasonAudio=locked;Toast.makeText(this,"本集缺少第 "+(locked+1)+" 音轨，使用默认音轨；本季锁定已保留",Toast.LENGTH_LONG).show();}}
            String address=source.getString("DirectStreamUrl");if(address.startsWith("/"))address=api.server+address;
            if(!Http.sameOrigin(address,api.server))throw new Exception("服务器返回了无效播放地址");
            try{player=TvPlayerFactory.create(this,video.getSubtitleView(),softwareVideo,softwareAudio,enhancedSubtitles);}
            catch(RuntimeException|LinkageError failure){if(!enhancedSubtitles)throw failure;enhancedSubtitles=false;TvPlayerFactory.clearAssView(video.getSubtitleView());player=TvPlayerFactory.create(this,video.getSubtitleView(),softwareVideo,softwareAudio,false);Toast.makeText(this,"特效字幕初始化失败，已切换兼容字幕",Toast.LENGTH_LONG).show();}
            video.setPlayer(player);
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
                String mime=codec.contains("pgs")?MimeTypes.APPLICATION_PGS:codec.contains("ass")||codec.contains("ssa")?MimeTypes.TEXT_SSA:codec.contains("vtt")?MimeTypes.TEXT_VTT:MimeTypes.APPLICATION_SUBRIP;
                subtitles.add(new MediaItem.SubtitleConfiguration.Builder(uri).setId(PlaybackSelection.externalId(stream.optInt("Index"))).setMimeType(mime).setLanguage(stream.optString("Language")).setLabel(PlaybackSelection.streamLabel(stream)).setSelectionFlags(stream.optBoolean("IsDefault")?C.SELECTION_FLAG_DEFAULT:0).build());
            }
            audioPending=subtitlePending=true;
            if(subtitleIndex==-1)preferences.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true);
            else if(subtitleIndex>=0)preferences.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false);
            player.setTrackSelectionParameters(preferences.build());
            player.setMediaItem(new MediaItem.Builder().setUri(address).setSubtitleConfigurations(subtitles).build(),Math.max(0,position));
            reportedStart=false;
            videoFrameSeen=false;videoReadySince=0;videoDecoder="等待解码器";
            player.addAnalyticsListener(new androidx.media3.exoplayer.analytics.AnalyticsListener(){
                @Override public void onVideoDecoderInitialized(EventTime time,String name,long at,long duration){videoDecoder=name;updateVideoInfo();}
            });
            player.addListener(new Player.Listener(){
                @Override public void onRenderedFirstFrame(){videoFrameSeen=true;videoReadySince=0;updateVideoInfo();}
                @Override public void onVideoSizeChanged(VideoSize size){applyVideoRotation(size);}
                @Override public void onTracksChanged(Tracks tracks){applyInitialTrack(C.TRACK_TYPE_AUDIO);applyInitialTrack(C.TRACK_TYPE_TEXT);updateTrackButtons();}
                @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY&&!reportedStart){reportedStart=true;report("Started");timer.postDelayed(progress,10000);status.setVisibility(View.GONE);updateTrackButtons();}if(state==Player.STATE_ENDED){report("Stopped");message("播放完成");if(pendingSeekPosition<0)showControls();}}
                @Override public void onPlayerError(PlaybackException error){handlePlayerError(error);}
            });
            player.prepare();player.setPlayWhenReady(playWhenReady);timer.post(skipCheck);timer.post(videoCheck);video.hideController();video.requestFocus();updateTrackButtons();
        }catch(Exception error){message(error.getMessage()==null?"无法开始播放":error.getMessage());}
    }
    private void message(String value){status.setText(value);status.setVisibility(View.VISIBLE);}
    private void handlePlayerError(PlaybackException error){
        if(enhancedSubtitles&&TvPlayerFactory.subtitleFailure(error)){stopPlayer();enhancedSubtitles=false;Toast.makeText(this,"特效字幕异常，已切换兼容字幕",Toast.LENGTH_LONG).show();startSource();return;}
        if(softwareAudio&&TvPlayerFactory.rendererFailure(error,C.TRACK_TYPE_AUDIO)){stopPlayer();softwareAudio=false;message("音频解码失败，正在尝试系统解码…");startSource();return;}
        if(!softwareVideo&&TvPlayerFactory.rendererFailure(error,C.TRACK_TYPE_VIDEO)){recoverVideo();return;}
        message("播放失败："+error.getErrorCodeName()+"。可切换解码模式或播放版本。");report("Stopped");showControls();
    }
    private void recoverVideo(){
        if(!softwareVideo){stopPlayer();softwareVideo=true;message("视频未正常输出，正在尝试软件兼容模式…");startSource();}
        else{message("视频仍未输出画面，请切换版本或解码模式。");showControls();updateVideoInfo();}
    }
    private void updateVideoInfo(){
        if(player==null||videoInfo==null)return;Format format=player.getVideoFormat();
        if(format==null){for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_VIDEO&&group.length>0){format=group.getTrackFormat(0);break;}}
        String label=format==null?"尚未解析视频":format.sampleMimeType+"  "+format.width+"×"+format.height;
        videoInfo.setText(label+"  ·  "+videoDecoder+"  ·  "+(videoFrameSeen?"已输出画面":"等待画面"));
    }
    /** PlayerView（media3 1.8.0）不处理 rotationDegrees：修正画面框比例并旋转 SurfaceView 像素。 */
    private void applyVideoRotation(VideoSize size){
        View surface=video.getVideoSurfaceView();
        androidx.media3.ui.AspectRatioFrameLayout frame=video.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        int rotation=size==null?0:((size.unappliedRotationDegrees%360)+360)%360;
        boolean swap=rotation==90||rotation==270;
        if(surface!=null){
            surface.setRotation((360-rotation)%360);
            if(!swap){surface.setScaleX(1);surface.setScaleY(1);}
            else surface.post(()->{int w=surface.getWidth(),h=surface.getHeight();if(w>0&&h>0){surface.setScaleX((float)h/w);surface.setScaleY((float)w/h);}});
        }
        if(frame!=null&&size!=null&&size.width>0&&size.height>0){
            float par=size.pixelWidthHeightRatio;
            frame.setAspectRatio(swap?size.height*par/size.width:size.width*par/size.height);
        }
    }
    private void updateSkip(){
        SkipSegments.Segment segment=skipMode==TvPreferences.SKIP_OFF||pendingSeekPosition>=0||player.getPlaybackState()!=Player.STATE_READY?null:SkipSegments.at(source.optJSONArray("Chapters"),player.getCurrentPosition(),player.getDuration());
        skipTarget=segment==null?-1:segment.end;
        if(segment!=null&&skipMode==TvPreferences.SKIP_AUTO&&player.isPlaying()){player.seekTo(segment.end);skipTarget=-1;segment=null;}
        if(segment!=null&&skipMode==TvPreferences.SKIP_BUTTON&&!seekKeys){skipButton.setText(segment.label);skipButton.setVisibility(View.VISIBLE);}else{if(skipButton.isFocused()){if(controls.getVisibility()==View.VISIBLE)audioButton.requestFocus();else video.requestFocus();}skipButton.setVisibility(View.GONE);}
    }
    private void skipSegment(){if(player!=null&&skipTarget>=0){player.seekTo(skipTarget);updateSkip();}}
    private static final class TrackRef{
        final Tracks.Group group;final int index;
        TrackRef(Tracks.Group group,int index){this.group=group;this.index=index;}
        boolean supported(){return group.isTrackSupported(index);}
    }
    private TrackRef findTrack(JSONObject stream,int type){
        if(player==null)return null;List<TrackRef> embedded=new ArrayList<>();String externalId=PlaybackSelection.externalId(stream.optInt("Index"));TrackRef named=null;int namedCount=0;
        for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==type)for(int i=0;i<group.length;i++){
            Format format=group.getTrackFormat(i);String id=PlaybackSelection.localTrackId(format.id);boolean external=id.startsWith("etkn-subtitle-");
            if(stream.optBoolean("IsExternal")){if(externalId.equals(id))return new TrackRef(group,i);}
            else if(!external){TrackRef ref=new TrackRef(group,i);embedded.add(ref);if(!stream.optString("Title").isEmpty()&&stream.optString("Title").equals(format.label)){named=ref;namedCount++;}}
        }
        if(stream.optBoolean("IsExternal"))return null;if(type==C.TRACK_TYPE_TEXT&&namedCount==1)return named;
        List<JSONObject> expected=new ArrayList<>();for(JSONObject value:PlaybackSelection.streams(source,type==C.TRACK_TYPE_TEXT))if(!value.optBoolean("IsExternal"))expected.add(value);
        // Ordinals are valid only when both parsers exposed the same complete list.
        int ordinal=PlaybackSelection.embeddedOrdinal(source,stream.optInt("Index"),type==C.TRACK_TYPE_TEXT);
        return expected.size()==embedded.size()&&ordinal>=0&&ordinal<embedded.size()?embedded.get(ordinal):null;
    }
    private void applyInitialTrack(int type){
        boolean subtitles=type==C.TRACK_TYPE_TEXT;
        if(player==null||!(subtitles?subtitlePending:audioPending))return;
        int index=subtitles?subtitleIndex:audioIndex;if(index==PlaybackSelection.AUTO)index=PlaybackSelection.defaultIndex(source,subtitles);
        if(index<0){if(subtitles){subtitlePending=false;player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,true).build());}else audioPending=false;return;}
        JSONObject selectedStream=PlaybackSelection.stream(source,index,subtitles);
        if(selectedStream==null){if(subtitles)subtitlePending=false;else audioPending=false;return;}
        TrackRef ref=findTrack(selectedStream,type);if(ref==null||!ref.supported())return;
        if(subtitles)subtitlePending=false;else audioPending=false;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(type,false).setOverrideForType(new TrackSelectionOverride(ref.group.getMediaTrackGroup(),ref.index)).build());
    }
    private void chooseTrack(int type){
        if(player==null)return;boolean subtitles=type==C.TRACK_TYPE_TEXT;List<Integer> indices=new ArrayList<>();List<String> labels=new ArrayList<>();
        indices.add(PlaybackSelection.AUTO);labels.add((subtitles?"":seasonKey==null||seasonKey.isEmpty()?"自动选择 · ":"自动选择（解除本季锁定） · ")+PlaybackSelection.selectionLabel(source,PlaybackSelection.AUTO,subtitles));
        if(subtitles){
            indices.add(-1);
            boolean disabled=player.getTrackSelectionParameters().disabledTrackTypes.contains(type);
            labels.add("关闭字幕"+(disabled?" ✓":""));
        }
        for(JSONObject stream:PlaybackSelection.streams(source,subtitles)){
            indices.add(stream.optInt("Index"));TrackRef ref=findTrack(stream,type);
            boolean active=ref!=null&&ref.group.isTrackSelected(ref.index);
            labels.add((subtitles?"":"第 "+(PlaybackSelection.audioOrdinal(source,stream.optInt("Index"))+1)+" 音轨 · ")+PlaybackSelection.streamLabel(stream)+(ref==null?"  ·  尚未解析":ref.supported()?"":"  ·  解码不支持")+(active?" ✓":""));
        }
        int selectedIndex=indices.indexOf(subtitles?subtitleIndex:audioIndex);
        video.setControllerShowTimeoutMs(0);
        trackDialog=new AlertDialog.Builder(this).setTitle(subtitles?"字幕":"音轨").setSingleChoiceItems(labels.toArray(new String[0]),selectedIndex,(dialog,index)->{
            if(player==null)return;int wanted=indices.get(index),resolved=wanted==PlaybackSelection.AUTO?PlaybackSelection.defaultIndex(source,subtitles):wanted;
            JSONObject stream=PlaybackSelection.stream(source,resolved,subtitles);TrackRef ref=stream==null?null:findTrack(stream,type);
            if(resolved>=0&&(ref==null||!ref.supported())){Toast.makeText(this,ref==null?"该轨道尚未解析，不能确认对应关系":"当前解码模式不支持该轨道",Toast.LENGTH_LONG).show();return;}
            if(subtitles){subtitleIndex=wanted;subtitlePending=true;}else{audioIndex=wanted;audioPending=true;new TvPreferences(this,api).saveSeasonAudio(seasonKey,PlaybackSelection.audioOrdinal(source,wanted));}
            applyInitialTrack(type);updateTrackButtons();dialog.dismiss();
        }).setNegativeButton("取消",null).create();
        trackDialog.setOnDismissListener(dialog->{video.setControllerShowTimeoutMs(5000);showControls();});trackDialog.show();
    }
    private String currentTrackTitle(boolean subtitles){
        int type=subtitles?C.TRACK_TYPE_TEXT:C.TRACK_TYPE_AUDIO;
        if(player!=null&&subtitles&&player.getTrackSelectionParameters().disabledTrackTypes.contains(type))return "关闭";
        for(JSONObject stream:PlaybackSelection.streams(source,subtitles)){TrackRef ref=findTrack(stream,type);if(ref!=null&&ref.group.isTrackSelected(ref.index))return PlaybackSelection.streamLabel(stream);}
        return PlaybackSelection.selectionLabel(source,subtitles?subtitleIndex:audioIndex,subtitles);
    }
    private void updateTrackButtons(){audioButton.setText("音轨 · "+currentTrackTitle(false));subtitleButton.setText("字幕 · "+currentTrackTitle(true));decoderButton.setText(softwareVideo?"解码 · 软件兼容":"解码 · 自动");}
    private void showSeekProgress(){
        seekKeys=true;video.hideController();controls.setVisibility(View.GONE);skipButton.setVisibility(View.GONE);seekProgress.setVisibility(View.VISIBLE);
        timer.removeCallbacks(seekProgressUpdate);timer.removeCallbacks(seekProgressHide);seekProgressUpdate.run();if(pendingSeekPosition<0)timer.postDelayed(seekProgressHide,5000);video.requestFocus();
    }
    private void commitSeek(){if(player==null||pendingSeekPosition<0)return;long target=pendingSeekPosition;pendingSeekPosition=-1;player.seekTo(target);showSeekProgress();}
    private void hideSeekProgress(){seekKeys=false;pendingSeekPosition=-1;timer.removeCallbacks(seekCommit);timer.removeCallbacks(seekProgressUpdate);timer.removeCallbacks(seekProgressHide);seekProgress.setVisibility(View.GONE);}
    private void showControls(){hideSeekProgress();video.showController();controls.setVisibility(View.VISIBLE);focusNextWhenLoaded=nextEpisode==null;(nextEpisode==null?audioButton:nextButton).requestFocus();}
    private void chooseDecoder(){
        if(player==null)return;video.setControllerShowTimeoutMs(0);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("视频解码").setSingleChoiceItems(new String[]{"自动：硬解优先，失败回退","软件兼容：高分辨率可能较慢"},softwareVideo?1:0,(choice,index)->{if(softwareVideo!=(index==1)){stopPlayer();softwareVideo=index==1;startSource();}choice.dismiss();}).setNegativeButton("取消",null).create();
        dialog.setOnDismissListener(value->{video.setControllerShowTimeoutMs(5000);showControls();});dialog.show();
    }
    private void report(String event){
        if(source==null)return;long milliseconds=player==null?position:Math.max(0,player.getCurrentPosition());boolean paused=player==null||!player.isPlaying();long duration=player==null?source.optLong("RunTimeTicks"):player.getDuration()>0?player.getDuration()*10000:source.optLong("RunTimeTicks");
        try{JSONObject payload=new JSONObject().put("ItemId",itemId).put("UserId",api.session.userId()).put("MediaSourceId",source.optString("Id")).put("PlaySessionId",playSession).put("PositionTicks",milliseconds*10000).put("RunTimeTicks",duration).put("IsPaused",paused).put("CanSeek",true).put("PlayMethod","DirectStream").put("Client","ETKN TV");
            reports.execute(()->{try{api.post("/api/tv/Sessions/Playing"+(event.equals("Started")?"":"/"+event),payload);}catch(Exception ignored){}});
        }catch(Exception ignored){}
    }
    private void stopPlayer(){hideSeekProgress();timer.removeCallbacks(progress);timer.removeCallbacks(skipCheck);timer.removeCallbacks(videoCheck);if(skipButton!=null)skipButton.setVisibility(View.GONE);if(player!=null){position=Math.max(0,player.getCurrentPosition());playWhenReady=player.getPlayWhenReady();report("Stopped");video.setPlayer(null);player.release();player=null;TvPlayerFactory.clearAssView(video.getSubtitleView());}}
    @Override protected void onStart(){super.onStart();foreground=true;if(info!=null&&player==null)startSource();}
    @Override protected void onPause(){hideSeekProgress();super.onPause();}
    @Override protected void onStop(){foreground=false;stopPlayer();super.onStop();}
    @Override protected void onDestroy(){timer.removeCallbacksAndMessages(null);reports.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putLong("position",player==null?position:player.getCurrentPosition());state.putString("sourceId",source==null?getIntent().getStringExtra("sourceId"):source.optString("Id"));state.putInt("audioIndex",audioIndex);state.putInt("subtitleIndex",subtitleIndex);state.putBoolean("softwareVideo",softwareVideo);state.putBoolean("softwareAudio",softwareAudio);state.putBoolean("enhancedSubtitles",enhancedSubtitles);state.putBoolean("playWhenReady",player==null?playWhenReady:player.getPlayWhenReady());super.onSaveInstanceState(state);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        if(down)focusNextWhenLoaded=false;
        if(key==KeyEvent.KEYCODE_BACK){if(down&&event.getRepeatCount()==0){hideSeekProgress();finish();}return true;}
        if(key==KeyEvent.KEYCODE_MENU){if(down)chooseTrack(C.TRACK_TYPE_TEXT);return true;}
        boolean mediaSeek=key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||key==KeyEvent.KEYCODE_MEDIA_REWIND;
        if((key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||mediaSeek)&&video!=null&&(mediaSeek||!video.isControllerFullyVisible()||seekKeys)){
            if(player!=null){
                timer.removeCallbacks(seekCommit);
                if(down){long end=player.getDuration();long target=Math.max(0,(pendingSeekPosition>=0?pendingSeekPosition:player.getCurrentPosition())+(key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD?10000:-10000));pendingSeekPosition=end>0?Math.min(target,end):target;showSeekProgress();}
                else if(event.getAction()==KeyEvent.ACTION_UP){if(event.isCanceled())hideSeekProgress();else if(pendingSeekPosition>=0)timer.postDelayed(seekCommit,250);}
            }return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_DOWN&&seekKeys){if(down){commitSeek();showControls();}return true;}
        if((key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&video!=null&&(!video.isControllerFullyVisible()||seekKeys)){
            if(down&&event.getRepeatCount()==0&&player!=null){if(skipButton.getVisibility()==View.VISIBLE&&skipTarget>=0)skipSegment();else if(player.getPlayWhenReady())player.pause();else player.play();}return true;
        }
        if(video!=null&&!video.isControllerFullyVisible()){
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(down)showControls();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP)return true;
        }
        return video!=null&&video.dispatchMediaKeyEvent(event)||super.dispatchKeyEvent(event);
    }
}
