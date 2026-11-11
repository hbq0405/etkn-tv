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
import android.view.TextureView;
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
    private ExoPlayer player;
    private PlaybackTiming playbackTiming;
    private PlaybackNetwork playbackNetwork;
    private int networkGeneration,automaticNetworkBitrate;
    private SubtitleTranslation translation;
    private DanmakuSession danmaku;
    private FrameLayout playbackStage;
    private PlayerView video;
    private PixelTimeBar seekProgress;
    private FrameLayout playbackOverlay;
    private AppearanceBackground waitingBackground;
    private ImageView pauseIcon,pauseAvatar;
    private TextView pauseName;
    private TextClock pauseClock;
    private boolean playbackIdentityRequested;
    private LinearLayout controls;
    private Button versionButton,audioButton,subtitleButton,decoderButton,skipButton,nextButton,speedButton,danmakuSettingsButton,bitrateButton;
    private JSONObject transcodeSource;
    private String transcodeSession="";
    private int assistanceRevision;
    private boolean assistancePending,bitrateAttempted,audioAssistAttempted,autoTranscodeNoticePending;
    private Button danmakuPanelToggle;
    private boolean danmakuSaving;
    private int danmakuRevision;
    private String danmakuNotice="";
    private AlertDialog speedDialog,danmakuDialog;
    private PlayerEpisodePanel episodePanel;
    private List<JSONObject> episodes=new ArrayList<>();
    private boolean episodesLoading,episodesFailed;
    private float playbackSpeed=1f;
    private AlertDialog trackDialog;
    private JSONObject info,source,nextEpisode;
    private String itemId,playSession="";
    private String seasonKey;
    private int missingSeasonAudio=-1;
    private long position;
    private int selected;
    private boolean foreground,reportedStart,versionConfirmed,audioPending,subtitlePending;
    private boolean softwareVideo,hardwareSurfaceRetry,playWhenReady=true;
    private boolean softwareAudio=true,enhancedSubtitles=true;
    private boolean videoFrameSeen,completedByCredits,introSkippedAtStart,creditsSkipCancelled;
    private long videoReadySince,sourceStartedAt,networkRetryAfter,lastPlaybackDiagnostic,autoTranscodeNoticeUntil;
    private boolean videoAssistAttempted,assistanceDisabled;
    private String videoDecoder="等待解码器";
    private TextView videoInfo;
    private KeyEvent confirmPress;
    private boolean confirmLongPressed;
    private TextView skipNotice;
    private ProgressBar playbackLoading;
    private int skipMode;
    private long skipTarget=-1;
    private long creditsCountdownStartedAt=-1;
    private long pendingSeekPosition=-1;
    private boolean seekKeys,focusNextWhenLoaded;
    private int audioIndex=PlaybackSelection.AUTO,subtitleIndex=PlaybackSelection.AUTO;
    private String[] queueIds;
    private int queueIndex;
    private boolean queueMode,nextLoaded,pendingAdvance;
    private final Handler timer=new Handler(Looper.getMainLooper());
    private final Runnable confirmLongPress=()->{if(confirmPress!=null&&player!=null){confirmLongPressed=true;videoInfo.setVisibility(videoInfo.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);updateVideoInfo();}};
    private final Runnable seekCommit=this::commitSeek;
    private final Runnable seekProgressHide=this::hideSeekProgress;
    private final Runnable hideSkipNotice=()->{if(skipNotice!=null)skipNotice.setVisibility(View.GONE);};
    private final Runnable creditsCountdown=this::updateCreditsCountdown;
    private final Runnable seekProgressUpdate=new Runnable(){@Override public void run(){
        if(player==null||seekProgress.getVisibility()!=View.VISIBLE)return;
        seekProgress.setDuration(Math.max(0,player.getDuration()));seekProgress.setPosition(pendingSeekPosition>=0?pendingSeekPosition:player.getCurrentPosition());seekProgress.setBufferedPosition(player.getBufferedPosition());timer.postDelayed(this,200);
    }};
    private final ExecutorService reports=Executors.newSingleThreadExecutor();
    private final Runnable progress=new Runnable(){@Override public void run(){if(player!=null){report("Progress");timer.postDelayed(this,10000);}}};
    private final Runnable skipCheck=new Runnable(){@Override public void run(){if(player!=null){updateSkip();if(player!=null)timer.postDelayed(this,400);}}};
    private final Runnable videoCheck=new Runnable(){@Override public void run(){
        if(player==null)return;
        boolean expected=player.getCurrentTracks().containsType(C.TRACK_TYPE_VIDEO);
        int state=player.getPlaybackState();
        boolean buffered=(state==Player.STATE_READY||state==Player.STATE_BUFFERING)&&player.getTotalBufferedDuration()>=2000&&pendingSeekPosition<0&&!player.isPlayingAd();
        if(!expected||videoFrameSeen||!player.getPlayWhenReady()||!buffered)videoReadySince=0;
        else if(videoReadySince==0)videoReadySince=android.os.SystemClock.elapsedRealtime();
        else if(android.os.SystemClock.elapsedRealtime()-videoReadySince>=3000&&!assistancePending&&!videoAssistAttempted){videoAssistAttempted=true;if(transcodeSource==null)requestAssistance("no_first_frame");else{message("转码后仍未输出画面，请选择更低质量或其他版本");showControls();}}
        long now=android.os.SystemClock.elapsedRealtime();
        if(!assistancePending&&!bitrateAttempted&&transcodeSource==null&&TranscodeChoice.required(source,new TvPreferences(PlayerActivity.this,api).bitrateLimit())&&now-sourceStartedAt>=1000){requestAssistance("user_bitrate");}
        if(playbackNetwork!=null&&!assistancePending&&!assistanceDisabled&&new TvPreferences(PlayerActivity.this,api).bitrateLimit()==0){
            int target=playbackNetwork.target(now,now>=networkRetryAfter&&videoFrameSeen&&player.getPlayWhenReady()&&pendingSeekPosition<0,
                    state==Player.STATE_BUFFERING,player.getTotalBufferedDuration(),TranscodeChoice.bitrate(source),playbackSpeed);
            if(target>0){automaticNetworkBitrate=target;networkRetryAfter=now+20000;requestAssistance("network_limit");}
        }
        if(now-lastPlaybackDiagnostic>=5000){lastPlaybackDiagnostic=now;playbackTiming.event("playback_health","state="+state+" position_ms="+player.getCurrentPosition()+" buffered_ms="+player.getTotalBufferedDuration()+" loading="+player.isLoading()+" first_frame="+videoFrameSeen+" pending="+assistancePending+" transcoded="+(transcodeSource!=null)+" quality="+new TvPreferences(PlayerActivity.this,api).bitrateLimit());}
        updateVideoInfo();if(danmaku!=null)danmaku.tick();if(episodePanel!=null&&episodePanel.isShown())episodePanel.updateCurrent(player);timer.postDelayed(this,1000);
    }};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);MusicPlayback.stop();itemId=getIntent().getStringExtra("id");playbackTiming=new PlaybackTiming(itemId);seasonKey=getIntent().getStringExtra("seasonKey");position=state==null?getIntent().getLongExtra("position",0):state.getLong("position");
        audioIndex=state==null?getIntent().getIntExtra("audioIndex",PlaybackSelection.AUTO):state.getInt("audioIndex",PlaybackSelection.AUTO);subtitleIndex=state==null?getIntent().getIntExtra("subtitleIndex",PlaybackSelection.AUTO):state.getInt("subtitleIndex",PlaybackSelection.AUTO);
        playbackSpeed=state==null?getIntent().getFloatExtra("speed",1f):state.getFloat("speed",1f);
        softwareVideo=state!=null&&state.getBoolean("softwareVideo");hardwareSurfaceRetry=state!=null&&state.getBoolean("hardwareSurfaceRetry");
        softwareAudio=state==null||state.getBoolean("softwareAudio",true);enhancedSubtitles=state==null||state.getBoolean("enhancedSubtitles",true);
        playWhenReady=state==null||state.getBoolean("playWhenReady",true);skipMode=new TvPreferences(this,api).skipMode();
        queueIds=getIntent().getStringArrayExtra("queue");queueIndex=getIntent().getIntExtra("queueIndex",0);queueMode=queueIds!=null&&queueIds.length>0;
        String seriesExtra=getIntent().getStringExtra("seriesId");nextLoaded=seriesExtra==null||seriesExtra.isEmpty();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        FrameLayout stage=new FrameLayout(this);playbackStage=stage;stage.setBackgroundColor(android.graphics.Color.BLACK);
        video=createVideoView(useTextureVideo());
        stage.addView(video,new FrameLayout.LayoutParams(-1,-1));
        waitingBackground=new AppearanceBackground(this);stage.addView(waitingBackground,new FrameLayout.LayoutParams(-1,-1));
        seekProgress=new PixelTimeBar(this);seekProgress.setEnabled(false);seekProgress.setVisibility(View.GONE);
        FrameLayout.LayoutParams seekLayout=new FrameLayout.LayoutParams(-1,dp(64),Gravity.BOTTOM);seekLayout.setMargins(dp(28),0,dp(28),0);stage.addView(seekProgress,seekLayout);
        createPlaybackOverlay(stage);
        controls=column();controls.setPadding(dp(12),dp(8),dp(12),dp(8));controls.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title=text(getIntent().getStringExtra("title"),18,TEXT);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);controls.addView(title);
        LinearLayout actions=row();versionButton=button("版本",this::chooseVersion);actions.addView(versionButton);
        audioButton=button("音轨",()->chooseTrack(C.TRACK_TYPE_AUDIO));subtitleButton=button("字幕",()->chooseTrack(C.TRACK_TYPE_TEXT));decoderButton=button("解码",this::chooseDecoder);
        speedButton=button("倍速 · "+speedLabel(),this::chooseSpeed);
        bitrateButton=button("质量",this::chooseBitrate);
        danmakuSettingsButton=button("弹幕设置",this::showDanmakuSettings);
        nextButton=button("下一集",this::advanceQueue);nextButton.setVisibility(View.GONE);
        actions.addView(audioButton);actions.addView(subtitleButton);actions.addView(nextButton);actions.addView(bitrateButton);actions.addView(speedButton);actions.addView(danmakuSettingsButton);actions.addView(decoderButton);
        int controlWidth=Math.max(1,(Math.min(900,getResources().getConfiguration().screenWidthDp-48)-24)/actions.getChildCount()-4);
        for(int i=0;i<actions.getChildCount();i++){
            Button control=(Button)actions.getChildAt(i);control.setBackground(transparentFocusBackground());
            control.setTextSize(12);control.setSingleLine(true);control.setEllipsize(android.text.TextUtils.TruncateAt.END);
            control.setMinWidth(0);control.setMinimumWidth(0);control.setMinHeight(dp(36));control.setMinimumHeight(dp(36));
            control.setPadding(dp(6),dp(4),dp(6),dp(4));
            LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(dp(controlWidth),dp(36));cell.setMargins(dp(2),dp(4),dp(2),dp(4));control.setLayoutParams(cell);
        }
        actions.setGravity(Gravity.CENTER);HorizontalScrollView actionScroll=new FocusScroll.Row(this);actionScroll.setHorizontalScrollBarEnabled(false);actionScroll.setFillViewport(true);actionScroll.addView(actions);controls.addView(actionScroll);
        FrameLayout.LayoutParams overlay=new FrameLayout.LayoutParams(Math.min(dp(900),getResources().getDisplayMetrics().widthPixels-dp(48)),-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);overlay.bottomMargin=dp(70);stage.addView(controls,overlay);controls.setVisibility(View.GONE);
        videoInfo=text("",14,TEXT);videoInfo.setPadding(dp(16),dp(12),dp(16),dp(12));videoInfo.setBackground(selectionBackground());videoInfo.setVisibility(View.GONE);androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(videoInfo,10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);FrameLayout.LayoutParams mediaLayout=new FrameLayout.LayoutParams(dp(Math.min(400,getResources().getConfiguration().screenWidthDp*2/5)),-1,Gravity.TOP|Gravity.START);mediaLayout.setMargins(dp(28),dp(88),0,dp(180));stage.addView(videoInfo,mediaLayout);
        skipButton=button("跳过片头",this::skipSegment);shadow(skipButton,0);TvNotice.style(skipButton,this);skipButton.setOnFocusChangeListener((v,focused)->{v.setScaleX(focused?FOCUS_SCALE:1f);v.setScaleY(focused?FOCUS_SCALE:1f);});skipButton.setVisibility(View.GONE);FrameLayout.LayoutParams skipLayout=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);skipLayout.topMargin=dp(48);stage.addView(skipButton,skipLayout);
        status=text("",15,android.graphics.Color.WHITE);TvNotice.style(status,this);status.setVisibility(View.GONE);FrameLayout.LayoutParams statusLayout=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);statusLayout.topMargin=dp(48);stage.addView(status,statusLayout);
        playbackLoading=new ProgressBar(this,null,android.R.attr.progressBarStyleLarge);playbackLoading.setIndeterminate(true);playbackLoading.setContentDescription("正在读取播放信息");playbackLoading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(resolveColor(ACCENT)));stage.addView(playbackLoading,new FrameLayout.LayoutParams(dp(64),dp(64),Gravity.CENTER));
        skipNotice=status;
        episodePanel=new PlayerEpisodePanel(this,itemId,this::playEpisode,this::loadNextEpisode);
        episodePanel.setVisibility(View.GONE);stage.addView(episodePanel,new FrameLayout.LayoutParams(dp(Math.min(480,getResources().getConfiguration().screenWidthDp*3/5)),-1,Gravity.END));
        setContentView(stage);video.hideController();video.requestFocus();
        if(queueMode&&!getIntent().getBooleanExtra("resolved",false)){showLoading();resolveQueueUnit(itemId,true);return;}
        String requestedSource=state==null?getIntent().getStringExtra("sourceId"):state.getString("sourceId",getIntent().getStringExtra("sourceId"));
        playbackTiming.event("info_queued");
        async(()->{playbackTiming.event("info_request");JSONObject result=api.post("/api/tv/Items/"+EtknApi.encode(itemId)+"/PlaybackInfo?UserId="+EtknApi.encode(api.session.userId()),new JSONObject());playbackTiming.event("info_received");return result;},result->{
            playbackTiming.event("info_delivered");
            info=result;playSession=info.optString("PlaySessionId");JSONArray sources=info.optJSONArray("MediaSources");
            if(sources==null||sources.length()==0){message("暂无可播放版本");return;}
            int preferred=PlaybackSelection.sourceIndex(sources,requestedSource);
            if(requestedSource!=null&&!requestedSource.isEmpty()&&preferred<0){message("所选版本已不可用，请返回重新选择");return;}
            if(preferred>=0){selected=preferred;versionConfirmed=true;startSource();}
            else if(sources.length()>1&&!getIntent().getBooleanExtra("resume",false))chooseVersion();else{versionConfirmed=true;startSource();}
        });
        loadNextEpisode();
    }
    // L055FCN 保留 SDR 旋转兼容；HDR 在创建解码器前选择 SurfaceView，保留颜色信号。
    private boolean useTextureVideo(){return softwareVideo||(!hardwareSurfaceRetry&&PlaybackSelection.useTextureVideo(false,android.os.Build.MODEL,android.os.Build.VERSION.SDK_INT,source));}
    private PlayerView createVideoView(boolean texture){
        PlayerView view=texture?(PlayerView)getLayoutInflater().inflate(R.layout.player_texture,null):new PlayerView(this);
        view.setUseController(true);view.setControllerAnimationEnabled(false);view.setControllerAutoShow(false);view.setControllerShowTimeoutMs(5000);view.setKeepContentOnPlayerReset(true);view.setShowSubtitleButton(false);view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        View center=view.findViewById(androidx.media3.ui.R.id.exo_center_controls);((ViewGroup)center.getParent()).removeView(center);
        DefaultTimeBar bar=view.findViewById(androidx.media3.ui.R.id.exo_progress);
        if(bar!=null){
            FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)bar.getLayoutParams();layout.gravity=Gravity.BOTTOM;layout.height=dp(64);layout.leftMargin=layout.rightMargin=dp(28);layout.topMargin=layout.bottomMargin=0;bar.setLayoutParams(layout);
            bar.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{FrameLayout.LayoutParams bounds=(FrameLayout.LayoutParams)v.getLayoutParams();if(bounds.bottomMargin!=0||bounds.topMargin!=0){bounds.topMargin=bounds.bottomMargin=0;v.setLayoutParams(bounds);}});
        }
        View time=view.findViewById(androidx.media3.ui.R.id.exo_bottom_bar);
        time.setVisibility(View.GONE);
        for(int id:new int[]{androidx.media3.ui.R.id.exo_basic_controls,androidx.media3.ui.R.id.exo_extra_controls_scroll_view,androidx.media3.ui.R.id.exo_minimal_controls}){View nativeControls=view.findViewById(id);if(nativeControls!=null)((ViewGroup)nativeControls.getParent()).removeView(nativeControls);}
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)visibility->{if(controls!=null)controls.setVisibility(episodePanel!=null&&episodePanel.isShown()?View.GONE:visibility);updatePlaybackOverlay();if(visibility!=View.VISIBLE){focusNextWhenLoaded=false;if(episodePanel==null||!episodePanel.isShown())view.requestFocus();}});
        if(texture)view.getVideoSurfaceView().addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(player!=null)applyVideoRotation(player.getVideoSize());});
        return view;
    }
    private void createPlaybackOverlay(FrameLayout stage){
        playbackOverlay=new FrameLayout(this);playbackOverlay.setVisibility(View.GONE);stage.addView(playbackOverlay,new FrameLayout.LayoutParams(-1,-1));
        pauseIcon=new ImageView(this);pauseIcon.setTag("pause-icon");pauseIcon.setImageResource(android.R.drawable.ic_media_pause);pauseIcon.setColorFilter(android.graphics.Color.WHITE);pauseIcon.setPadding(dp(20),dp(20),dp(20),dp(20));pauseIcon.setContentDescription("已暂停");Images.rounded(pauseIcon,dp(40));pauseIcon.getBackground().setTint(OVERLAY);playbackOverlay.addView(pauseIcon,new FrameLayout.LayoutParams(dp(80),dp(80),Gravity.CENTER));
        FrameLayout.LayoutParams identity=new FrameLayout.LayoutParams(-2,dp(48),Gravity.TOP|Gravity.START);identity.setMargins(dp(28),dp(24),dp(28),0);
        pauseName=text(api.session.name(),20,android.graphics.Color.WHITE);pauseName.setSingleLine(true);pauseName.setMaxWidth(dp(280));pauseName.setEllipsize(android.text.TextUtils.TruncateAt.END);pauseName.setGravity(Gravity.CENTER_VERTICAL);pauseName.setShadowLayer(dp(2),0,dp(1),android.graphics.Color.BLACK);playbackOverlay.addView(pauseName,identity);
        pauseAvatar=new ImageView(this);pauseAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);pauseAvatar.setContentDescription(api.session.name());Images.rounded(pauseAvatar,dp(24));pauseAvatar.setVisibility(View.GONE);FrameLayout.LayoutParams avatar=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.START);avatar.setMargins(dp(28),dp(24),0,0);playbackOverlay.addView(pauseAvatar,avatar);
        pauseClock=new TextClock(this);pauseClock.setFormat12Hour("HH:mm");pauseClock.setFormat24Hour("HH:mm");pauseClock.setTextSize(22);pauseClock.setTextColor(android.graphics.Color.WHITE);pauseClock.setGravity(Gravity.CENTER_VERTICAL);pauseClock.setShadowLayer(dp(2),0,dp(1),android.graphics.Color.BLACK);FrameLayout.LayoutParams clock=new FrameLayout.LayoutParams(-2,dp(48),Gravity.TOP|Gravity.END);clock.setMargins(0,dp(24),dp(28),0);playbackOverlay.addView(pauseClock,clock);
    }
    @Override void appearanceChanged(){if(waitingBackground!=null)waitingBackground.update();if(status!=null)TvNotice.style(status,this);if(skipButton!=null)TvNotice.style(skipButton,this);if(playbackLoading!=null)playbackLoading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(resolveColor(ACCENT)));if(danmaku!=null)danmaku.tick();if(danmakuDialog!=null&&danmakuDialog.isShowing())themeDialog(danmakuDialog);}
    private void loadPlaybackIdentity(){
        if(playbackIdentityRequested)return;playbackIdentityRequested=true;
        async(()->{try{return api.get("/api/tv/Users/Me");}catch(Exception ignored){return null;}},user->{
            if(user==null)return;String tag=user.optString("PrimaryImageTag");if(tag.isEmpty())return;
            Images.load(api,api.server+api.userPath()+"/Images/Primary?maxWidth=160&tag="+EtknApi.encode(tag),pauseAvatar,loaded->{pauseAvatar.setVisibility(loaded?View.VISIBLE:View.GONE);pauseName.setVisibility(loaded?View.GONE:View.VISIBLE);});
        });
    }
    private void updatePlaybackOverlay(){
        if(playbackOverlay==null)return;
        if(waitingBackground!=null)waitingBackground.setVisibility(!videoFrameSeen?View.VISIBLE:View.GONE);
        boolean paused=foreground&&player!=null&&!player.getPlayWhenReady()&&player.getPlayerError()==null&&(player.getPlaybackState()==Player.STATE_READY||player.getPlaybackState()==Player.STATE_BUFFERING);
        boolean panel=episodePanel!=null&&episodePanel.isShown(),controller=controls!=null&&controls.getVisibility()==View.VISIBLE;
        playbackOverlay.setVisibility(!panel&&(paused||controller)?View.VISIBLE:View.GONE);pauseIcon.setVisibility(paused?View.VISIBLE:View.GONE);
        if(playbackOverlay.getVisibility()==View.VISIBLE)loadPlaybackIdentity();
        seekProgress.setVisibility(!panel&&(seekKeys||paused&&!controller)?View.VISIBLE:View.GONE);timer.removeCallbacks(seekProgressUpdate);if(seekProgress.getVisibility()==View.VISIBLE)seekProgressUpdate.run();
    }
    private void loadNextEpisode(){
        String series=getIntent().getStringExtra("seriesId");
        if(series==null||series.isEmpty()){nextLoaded=true;updateNextButton();return;}
        if(episodesLoading)return;episodesLoading=true;episodesFailed=false;
        if(episodePanel.isShown())episodePanel.loading();
        async(()->{
            try{return EtknApi.rows(api.get("/api/tv/Shows/"+EtknApi.encode(series)+"/Episodes?UserId="+EtknApi.encode(api.session.userId())));}
            catch(EtknApi.AuthException error){throw error;}
            catch(Exception ignored){return null;}
        },rows->{
            episodesLoading=false;episodesFailed=rows==null;episodes=rows==null?new ArrayList<>():new ArrayList<>(rows);
            episodes.removeIf(ep->!"Episode".equals(ep.optString("Type"))||ep.optString("Id").isEmpty()||ep.optInt("ParentIndexNumber",-1)<0||ep.optInt("IndexNumber",-1)<0);
            episodes.sort(Comparator.comparingInt((JSONObject ep)->ep.optInt("ParentIndexNumber")).thenComparingInt(ep->ep.optInt("IndexNumber")));
            nextEpisode=PlaybackSelection.nextEpisode(episodes,itemId);nextLoaded=true;updateNextButton();
            if(episodePanel.isShown())renderEpisodes();
            if(hasNext()&&focusNextWhenLoaded&&controls.getVisibility()==View.VISIBLE)nextButton.requestFocus();focusNextWhenLoaded=false;
            if(pendingAdvance){pendingAdvance=false;advanceQueue();}
        });
    }
    private void renderEpisodes(){if(episodesLoading)episodePanel.loading();else if(episodesFailed)episodePanel.failed();else{episodePanel.showEpisodes(episodes);episodePanel.updateCurrent(player);}}
    private void showEpisodes(){
        String series=getIntent().getStringExtra("seriesId");if(series==null||series.isEmpty()){TvNotice.show(this,"当前视频没有分集",Toast.LENGTH_SHORT);return;}
        hideSeekProgress();episodePanel.setVisibility(View.VISIBLE);video.hideController();controls.setVisibility(View.GONE);skipButton.setVisibility(View.GONE);updatePlaybackOverlay();renderEpisodes();
    }
    private void hideEpisodes(){episodePanel.setVisibility(View.GONE);updatePlaybackOverlay();video.requestFocus();}
    private boolean hasNext(){return nextEpisode!=null||(queueMode&&queueIndex+1<queueIds.length);}
    private void updateNextButton(){if(nextButton==null)return;nextButton.setText(nextEpisode!=null?"下一集":"下一个");nextButton.setVisibility(hasNext()?View.VISIBLE:View.GONE);}
    private void playNextEpisode(){
        if(nextEpisode!=null)playEpisode(nextEpisode,0);
    }
    private void playEpisode(JSONObject episode){playEpisode(episode,DetailPresentation.position(episode)/10000);}
    private void playEpisode(JSONObject episode,long start){
        if(isFinishing())return;if(itemId.equals(episode.optString("Id"))){hideEpisodes();return;}
        Intent next=new Intent(this,PlayerActivity.class).putExtra("id",episode.optString("Id")).putExtra("title",EtknApi.resumeTitle(episode)).putExtra("seriesId",getIntent().getStringExtra("seriesId")).putExtra("seasonKey",PlaybackSelection.seasonKey(episode)).putExtra("resume",true).putExtra("position",start).putExtra("speed",playbackSpeed);
        if(queueMode)next.putExtra("queue",queueIds).putExtra("queueIndex",queueIndex).putExtra("resolved",true);
        stopPlayer();startActivity(next);finish();
    }
    private void advanceQueue(){
        if(isFinishing())return;
        if(!nextLoaded){pendingAdvance=true;return;}
        if(nextEpisode!=null){playNextEpisode();return;}
        if(!queueMode||queueIndex+1>=queueIds.length){finish();return;}
        queueIndex++;showLoading();resolveQueueUnit(queueIds[queueIndex],false);
    }
    private void resolveQueueUnit(String id,boolean first){
        async(()->{
            try{
                JSONObject entry=api.get(api.userPath()+"/Items/"+EtknApi.encode(id));
                String type=entry.optString("Type");
                if("Series".equals(type)||"Season".equals(type)){
                    String series="Series".equals(type)?entry.optString("Id"):entry.optString("SeriesId",entry.optString("ParentId"));
                    List<JSONObject> episodes=EtknApi.rows(api.get("/api/tv/Shows/"+EtknApi.encode(series)+"/Episodes?UserId="+EtknApi.encode(api.session.userId())+"&IsMissing=false"));
                    JSONObject target=episodes.isEmpty()?null:(first?DetailPresentation.recommended(episodes):PlaybackSelection.firstUnwatched(episodes));
                    if(target==null)return new JSONObject().put("failed",true);
                    return new JSONObject().put("unit",target).put("seriesId",series);
                }
                return new JSONObject().put("unit",entry).put("seriesId",entry.optString("SeriesId"));
            }catch(EtknApi.AuthException error){throw error;}
            catch(Exception error){return new JSONObject().put("failed",true);}
        },result->{
            if(isFinishing()||isDestroyed())return;
            JSONObject unit=result.optJSONObject("unit");
            if(unit==null){
                if(queueIndex+1<queueIds.length){queueIndex++;resolveQueueUnit(queueIds[queueIndex],first);return;}
                if(first)message("队列中没有可播放的媒体项");else finish();
                return;
            }
            long start=first?("Episode".equals(unit.optString("Type"))?DetailPresentation.position(unit)/10000:position):0;
            Intent next=new Intent(this,PlayerActivity.class).putExtra("id",unit.optString("Id")).putExtra("title",EtknApi.resumeTitle(unit)).putExtra("position",Math.max(0,start)).putExtra("speed",playbackSpeed).putExtra("seriesId",result.optString("seriesId")).putExtra("seasonKey",PlaybackSelection.seasonKey(unit)).putExtra("resume",true).putExtra("resolved",true).putExtra("queue",queueIds).putExtra("queueIndex",queueIndex);
            stopPlayer();startActivity(next);finish();
        });
    }
    private void chooseVersion(){
        if(info==null)return;JSONArray sources=info.optJSONArray("MediaSources");if(sources==null||sources.length()==0)return;
        String[] labels=new String[sources.length()];for(int i=0;i<labels.length;i++)labels[i]=PlaybackSelection.sourceLabel(sources.optJSONObject(i),i);
        hideLoading();PlayerChoiceDialog.create(this,"选择播放版本",labels,selected,(dialog,index)->{stopPlayer();selected=index;hardwareSurfaceRetry=false;versionConfirmed=true;audioIndex=subtitleIndex=PlaybackSelection.AUTO;dialog.dismiss();startSource();}).show();
    }
    private void startSource(){
        if(!foreground||!versionConfirmed||info==null||player!=null||pendingAdvance||assistancePending)return;
        try{
            showLoading();playbackTiming.event("source_start");
            completedByCredits=false;
            source=transcodeSource==null?info.getJSONArray("MediaSources").getJSONObject(selected):transcodeSource;
            int quality=new TvPreferences(this,api).bitrateLimit();
            String planned=transcodeSource!=null||bitrateAttempted||assistanceDisabled||softwareVideo||source.optString("Container").equalsIgnoreCase("iso")?"":
                    TranscodeChoice.required(source,quality)?"user_bitrate":TranscodeChoice.hardwareRequired(source,TranscodeChoice.sourceCapabilities(source))?"hardware_limit":"";
            int generation=++networkGeneration;
            lastPlaybackDiagnostic=0;
            playbackNetwork=new PlaybackNetwork(planned.isEmpty()?null:()->timer.post(()->{
                if(generation==networkGeneration&&player!=null&&!bitrateAttempted&&!assistancePending)requestAssistance(planned);
            }));
            boolean texture=useTextureVideo();
            if((video.getVideoSurfaceView() instanceof TextureView)!=texture){
                ViewGroup parent=(ViewGroup)video.getParent();int index=parent.indexOfChild(video);ViewGroup.LayoutParams layout=video.getLayoutParams();
                parent.removeView(video);video=createVideoView(texture);parent.addView(video,index,layout);
            }
            int locked=new TvPreferences(this,api).seasonAudio(seasonKey);
            if(locked>=0){audioIndex=PlaybackSelection.audioAt(source,locked);if(audioIndex==PlaybackSelection.AUTO&&missingSeasonAudio!=locked){missingSeasonAudio=locked;TvNotice.show(this,"本集缺少第 "+(locked+1)+" 音轨，使用默认音轨；本季锁定已保留",Toast.LENGTH_LONG);}}
            String address=PlaybackAddress.resolve(source,api.server,api.token,api.session.userId());
            if(source.optBoolean("AudioOnly")&&source.has("OriginalVideoUrl")){
                JSONObject originalAddress=new JSONObject().put("DirectStreamUrl",source.optString("OriginalVideoUrl"));
                source.put("OriginalVideoUrl",PlaybackAddress.resolve(originalAddress,api.server,api.token,api.session.userId()));
            }
            JSONObject translationPlan=source.optJSONObject("SubtitleTranslation");
            if(translationPlan!=null&&subtitleIndex==PlaybackSelection.AUTO)
                translation=new SubtitleTranslation(api,itemId,source.optString("Id"),translationPlan,video,()->player,()->subtitleIndex==PlaybackSelection.AUTO);
            try{player=TvPlayerFactory.create(this,video.getSubtitleView(),softwareVideo,softwareAudio,enhancedSubtitles,translation,playbackTiming,playbackNetwork);}
            catch(RuntimeException|LinkageError failure){if(!enhancedSubtitles)throw failure;enhancedSubtitles=false;TvPlayerFactory.clearAssView(video.getSubtitleView());player=TvPlayerFactory.create(this,video.getSubtitleView(),softwareVideo,softwareAudio,false,translation,playbackTiming,playbackNetwork);TvNotice.show(this,"特效字幕初始化失败，已切换兼容字幕",Toast.LENGTH_LONG);}
            playbackTiming.event("player_created");
            video.setPlayer(player);player.setPlaybackSpeed(playbackSpeed);
            List<MediaItem.SubtitleConfiguration> subtitles=new ArrayList<>();JSONArray streams=source.optJSONArray("MediaStreams");
            TrackSelectionParameters.Builder preferences=player.getTrackSelectionParameters().buildUpon();
            preferences.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,source.optInt("DefaultSubtitleStreamIndex",-1)<0);
            if(streams!=null)for(int i=0;i<streams.length();i++){
                JSONObject stream=streams.getJSONObject(i);String type=stream.optString("Type");
                if(stream.optBoolean("IsDefault")){if(type.equalsIgnoreCase("audio"))preferences.setPreferredAudioLanguage(stream.optString("Language"));if(type.equalsIgnoreCase("subtitle"))preferences.setPreferredTextLanguage(stream.optString("Language"));}
                if(!type.equalsIgnoreCase("subtitle")||!stream.optBoolean("IsExternal"))continue;
                String delivery=stream.optString("DeliveryUrl");if(delivery.startsWith("/"))delivery=api.server+delivery;if(!Http.sameOrigin(delivery,api.server))continue;
                Uri uri=Uri.parse(PlaybackAddress.subtitle(delivery,api.server,api.token));
                String codec=stream.optString("Codec").toLowerCase(Locale.ROOT);
                String mime=codec.contains("pgs")?MimeTypes.APPLICATION_PGS:codec.contains("ass")||codec.contains("ssa")?MimeTypes.TEXT_SSA:codec.contains("vtt")?MimeTypes.TEXT_VTT:MimeTypes.APPLICATION_SUBRIP;
                subtitles.add(new MediaItem.SubtitleConfiguration.Builder(uri).setId(PlaybackSelection.externalId(stream.optInt("Index"))).setMimeType(mime).setLanguage(stream.optString("Language")).setLabel(PlaybackSelection.streamLabel(stream)).setSelectionFlags(stream.optBoolean("IsDefault")?C.SELECTION_FLAG_DEFAULT:0).build());
            }
            audioPending=subtitlePending=true;
            if(subtitleIndex==-1)preferences.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true);
            else if(subtitleIndex>=0)preferences.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false);
            player.setTrackSelectionParameters(preferences.build());
            JSONArray chapters=source.optJSONArray("Chapters");long duration=source.optLong("RunTimeTicks",0)/10000;long startPosition=SkipSegments.initialPosition(chapters,position,duration,skipMode==TvPreferences.SKIP_AUTO&&playWhenReady);
            SkipSegments.Segment startSegment=SkipSegments.at(chapters,Math.max(0,position),duration);introSkippedAtStart=skipMode==TvPreferences.SKIP_AUTO&&playWhenReady&&startPosition>Math.max(0,position)&&startSegment!=null&&"跳过片头".equals(startSegment.label);
            playbackTiming.event("initial_position","position_ms="+startPosition);
            player.setMediaItem(new MediaItem.Builder().setUri(address).setTag(source).setSubtitleConfigurations(subtitles).build(),startPosition);
            reportedStart=false;
            videoFrameSeen=false;videoReadySince=0;sourceStartedAt=android.os.SystemClock.elapsedRealtime();videoDecoder="等待解码器";updatePlaybackOverlay();
            player.addAnalyticsListener(new androidx.media3.exoplayer.analytics.AnalyticsListener(){
                @Override public void onVideoDecoderInitialized(EventTime time,String name,long at,long duration){playbackTiming.event("video_decoder","init_ms="+duration);videoDecoder=name;if(player!=null)applyVideoRotation(player.getVideoSize());updateVideoInfo();}
            });
            player.addListener(new Player.Listener(){
                @Override public void onEvents(Player current,Player.Events events){updatePlaybackOverlay();if(danmaku!=null)danmaku.tick();}
                @Override public void onTimelineChanged(Timeline timeline,int reason){updateTimelineMarkers();}
                @Override public void onPlaybackParametersChanged(PlaybackParameters parameters){playbackSpeed=parameters.speed;speedButton.setText("倍速 · "+speedLabel());}
                @Override public void onRenderedFirstFrame(){playbackTiming.event("first_frame");videoFrameSeen=true;videoReadySince=0;updatePlaybackOverlay();updateVideoInfo();}
                @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){if((reason==Player.DISCONTINUITY_REASON_SEEK||reason==Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)&&playbackNetwork!=null)playbackNetwork.afterSeek(android.os.SystemClock.elapsedRealtime());}
                @Override public void onVideoSizeChanged(VideoSize size){applyVideoRotation(size);}
                @Override public void onTracksChanged(Tracks tracks){applyInitialTrack(C.TRACK_TYPE_AUDIO);applyInitialTrack(C.TRACK_TYPE_TEXT);updateTrackButtons();}
                @Override public void onPlaybackStateChanged(int state){playbackTiming.event("state_"+state,"position_ms="+(player==null?-1:player.getCurrentPosition())+" buffered_ms="+(player==null?-1:player.getTotalBufferedDuration()));if(state==Player.STATE_READY&&!reportedStart){reportedStart=true;report("Started");timer.postDelayed(progress,10000);hideLoading();status.setVisibility(View.GONE);updateTrackButtons();if(introSkippedAtStart){introSkippedAtStart=false;showSkipNotice("已跳过片头",2200);}if(autoTranscodeNoticePending){autoTranscodeNoticePending=false;autoTranscodeNoticeUntil=android.os.SystemClock.elapsedRealtime()+4000;showUnifiedNotice("已自动转码",4000);}}if(state==Player.STATE_ENDED)advanceQueue();}
                @Override public void onPlayerError(PlaybackException error){handlePlayerError(error);}
            });
            applyDanmakuState(info.optJSONObject("Danmaku"));
            updateTimelineMarkers();playbackTiming.event("prepare");player.prepare();player.setPlayWhenReady(playWhenReady);timer.post(skipCheck);timer.post(videoCheck);video.hideController();if(episodePanel.isShown())renderEpisodes();else video.requestFocus();updateTrackButtons();
        }catch(Exception error){message(error.getMessage()==null?"无法开始播放":error.getMessage());}
    }
    private void showLoading(){if(playbackLoading!=null)playbackLoading.setVisibility(View.VISIBLE);if(status!=null)status.setVisibility(View.GONE);}
    private void hideLoading(){if(playbackLoading!=null)playbackLoading.setVisibility(View.GONE);}
    @Override void requestError(String value){danmakuSaving=false;message(value);}
    private void message(String value){hideLoading();showUnifiedNotice(value,0);}
    void showUnifiedNotice(String value,long duration){if(skipNotice==null)return;timer.removeCallbacks(hideSkipNotice);TvNotice.style(skipNotice,this);skipNotice.setText(value);skipNotice.setVisibility(View.VISIBLE);skipNotice.bringToFront();if(skipButton!=null)skipButton.setVisibility(View.GONE);if(duration>0)timer.postDelayed(hideSkipNotice,duration);}
    private void showSkipNotice(String value,long hideAfter){showUnifiedNotice(value,hideAfter);}
    private void cancelCreditsCountdown(){timer.removeCallbacks(creditsCountdown);creditsCountdownStartedAt=-1;if(skipNotice!=null&&skipNotice.getText().toString().startsWith("即将跳过片尾"))hideSkipNotice.run();}
    private void updateCreditsCountdown(){
        if(player==null)return;long duration=player.getDuration();SkipSegments.Segment segment=SkipSegments.at(source.optJSONArray("Chapters"),player.getCurrentPosition(),duration);
        if(segment==null||segment.end!=duration){cancelCreditsCountdown();creditsSkipCancelled=false;return;}
        if(!player.isPlaying()||creditsSkipCancelled){cancelCreditsCountdown();return;}
        long remaining=5000-(android.os.SystemClock.elapsedRealtime()-creditsCountdownStartedAt);
        if(remaining<=0){skipTarget=segment.end;cancelCreditsCountdown();skipSegment();return;}
        showSkipNotice("即将跳过片尾 "+((remaining+999)/1000),0);timer.postDelayed(creditsCountdown,Math.min(1000,remaining));
    }
    private void handlePlayerError(PlaybackException error){
        if(enhancedSubtitles&&TvPlayerFactory.subtitleFailure(error)){stopPlayer(false);enhancedSubtitles=false;TvNotice.show(this,"特效字幕异常，已切换兼容字幕",Toast.LENGTH_LONG);startSource();return;}
        if(TvPlayerFactory.rendererFailure(error,C.TRACK_TYPE_AUDIO)){
            if(softwareAudio){stopPlayer(false);softwareAudio=false;message("音频解码失败，正在尝试系统解码…");startSource();return;}
            if(!audioAssistAttempted&&transcodeSource==null){stopPlayer(false);audioAssistAttempted=true;requestAssistance("audio_error");return;}
        }
        if(!softwareVideo&&TvPlayerFactory.rendererFailure(error,C.TRACK_TYPE_VIDEO)){recoverVideo();return;}
        message("播放失败："+error.getErrorCodeName()+"。可切换解码模式或播放版本。");report("Stopped");showControls();
    }
    private void recoverVideo(){
        if(!softwareVideo&&!hardwareSurfaceRetry&&video.getVideoSurfaceView() instanceof TextureView){
            playbackTiming.event("hardware_surface_retry","buffered_ms="+player.getTotalBufferedDuration());
            stopPlayer(false);hardwareSurfaceRetry=true;message("正在重试硬件视频输出…");startSource();
        }else{
            boolean assisted=!transcodeSession.isEmpty();playbackTiming.event("video_output_failed");stopPlayer();
            if(assisted){message("转码后设备仍无法硬解，请选择更低质量或其他版本");showControls();}
            else requestAssistance("no_first_frame");
        }
    }
    private void updateVideoInfo(){
        if(player==null||videoInfo==null||videoInfo.getVisibility()!=View.VISIBLE)return;Format format=player.getVideoFormat();
        if(format==null)for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_VIDEO)for(int i=0;i<group.length;i++)if(group.isTrackSelected(i))format=group.getTrackFormat(i);
        JSONObject videoStream=null,onlyVideo=null;int videoCount=0;JSONArray streams=source==null?null:source.optJSONArray("MediaStreams");if(streams!=null)for(int i=0;i<streams.length();i++){JSONObject stream=streams.optJSONObject(i);if(stream!=null&&"Video".equalsIgnoreCase(stream.optString("Type"))){videoCount++;onlyVideo=stream;if(format!=null&&(DiscTrackIdentity.matches(stream,format.id)||Integer.toString(stream.optInt("Index",-1)).equals(PlaybackSelection.localTrackId(format.id))))videoStream=stream;}}if(videoStream==null&&videoCount==1)videoStream=onlyVideo;
        Format subtitle=null;for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_TEXT)for(int i=0;i<group.length;i++)if(group.isTrackSelected(i))subtitle=group.getTrackFormat(i);
        boolean disabled=player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
        videoInfo.setText("媒体信息\n\n视频\n"+mediaParameters(format,videoStream)+"解码器："+videoDecoder+"\n\n音轨 · "+currentTrackTitle(false)+"\n"+mediaParameters(player.getAudioFormat(),selectedStream(false))+"\n字幕 · "+currentTrackTitle(true)+(disabled?"":"\n"+mediaParameters(subtitle,selectedStream(true))));
    }
    private JSONObject selectedStream(boolean subtitles){for(JSONObject stream:PlaybackSelection.streams(source,subtitles)){TrackRef ref=findTrack(stream,subtitles?C.TRACK_TYPE_TEXT:C.TRACK_TYPE_AUDIO);if(ref!=null&&ref.group.isTrackSelected(ref.index))return stream;}return null;}
    private String mediaParameters(Format format,JSONObject stream){
        JSONObject values=stream==null?new JSONObject():stream;StringBuilder result=new StringBuilder();String codec=values.optString("Codec",format==null||format.sampleMimeType==null?"":format.sampleMimeType);if(!codec.isEmpty())result.append("编码：").append(codec);String profile=values.optString("Profile");if(!profile.isEmpty())result.append(" · ").append(profile);int depth=values.optInt("BitDepth");if(depth>0)result.append(" · ").append(depth).append(" bit");if(result.length()>0)result.append('\n');
        int width=format!=null&&format.width>0?format.width:values.optInt("Width"),height=format!=null&&format.height>0?format.height:values.optInt("Height");if(width>0&&height>0){result.append("分辨率：").append(width).append('×').append(height);if(format!=null&&format.rotationDegrees!=0)result.append(" · 旋转 ").append(format.rotationDegrees).append('°');result.append('\n');}
        double fps=format!=null&&format.frameRate>0?format.frameRate:values.optDouble("AverageFrameRate",values.optDouble("RealFrameRate",0));if(fps>0)result.append(String.format(Locale.ROOT,"帧率：%.3f fps\n",fps));
        String range=values.optString("VideoRange");if(!range.isEmpty())result.append("动态范围：").append(range).append('\n');
        int channels=format!=null&&format.channelCount>0?format.channelCount:values.optInt("Channels"),sampleRate=format!=null&&format.sampleRate>0?format.sampleRate:values.optInt("SampleRate");if(channels>0)result.append("声道：").append(channels).append('\n');if(sampleRate>0)result.append("采样率：").append(sampleRate).append(" Hz\n");
        long bitrate=format!=null&&format.bitrate>0?format.bitrate:values.optLong("BitRate");if(bitrate>0)result.append(String.format(Locale.ROOT,bitrate>=1000000?"码率：%.2f Mbps\n":"码率：%.0f kbps\n",bitrate/(bitrate>=1000000?1000000d:1000d)));
        String language=values.optString("DisplayLanguage",values.optString("Language",format==null||format.language==null?"":format.language));if(!language.isEmpty())result.append("语言：").append(language).append('\n');return result.length()==0?"等待媒体参数\n":result.toString();
    }
    /** MediaCodec 已旋转输出；FFmpeg 的原始帧只在 TextureView 内做旋转，SurfaceView 不做视图变换。 */
    private void applyVideoRotation(VideoSize size){
        if(player==null)return;
        View surface=video.getVideoSurfaceView();
        if(!(surface instanceof TextureView))return;
        androidx.media3.ui.AspectRatioFrameLayout frame=video.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        Format format=player.getVideoFormat();boolean raw=videoDecoder.startsWith("ffmpeg");
        int rotation=raw&&format!=null?format.rotationDegrees:0;
        boolean swap=rotation==90||rotation==270;
        if(frame!=null&&size.width>0&&size.height>0){
            float par=raw&&format!=null?format.pixelWidthHeightRatio:size.pixelWidthHeightRatio;
            frame.setAspectRatio(swap?size.height/(size.width*par):size.width*par/size.height);
        }
        android.graphics.Matrix transform=new android.graphics.Matrix();int w=surface.getWidth(),h=surface.getHeight();
        if(rotation!=0&&w>0&&h>0){
            float x=w/2f,y=h/2f;transform.postRotate(rotation,x,y);
            android.graphics.RectF bounds=new android.graphics.RectF(0,0,w,h);transform.mapRect(bounds);
            transform.postScale(w/bounds.width(),h/bounds.height(),x,y);
        }
        ((TextureView)surface).setTransform(transform);
    }
    private void updateSkip(){
        SkipSegments.Segment segment=skipMode==TvPreferences.SKIP_OFF||pendingSeekPosition>=0||player.getPlaybackState()!=Player.STATE_READY?null:SkipSegments.at(source.optJSONArray("Chapters"),player.getCurrentPosition(),player.getDuration());
        if(segment==null){cancelCreditsCountdown();creditsSkipCancelled=false;}
        skipTarget=segment==null?-1:segment.end;
        if(segment!=null&&skipMode==TvPreferences.SKIP_AUTO){
            if(segment.end==player.getDuration()){
                if(player.isPlaying()&&!creditsSkipCancelled){if(creditsCountdownStartedAt<0){creditsCountdownStartedAt=android.os.SystemClock.elapsedRealtime();updateCreditsCountdown();}}else cancelCreditsCountdown();
                return;
            }
            creditsSkipCancelled=false;cancelCreditsCountdown();if(player.isPlaying()){skipSegment();return;}
        }
        if(segment!=null&&skipMode==TvPreferences.SKIP_BUTTON&&!seekKeys&&!episodePanel.isShown()&&skipNotice.getVisibility()!=View.VISIBLE){skipButton.setText(segment.label);skipButton.setVisibility(View.VISIBLE);}else{if(skipButton.isFocused()){if(controls.getVisibility()==View.VISIBLE)audioButton.requestFocus();else video.requestFocus();}skipButton.setVisibility(View.GONE);}
    }
    private void updateTimelineMarkers(){
        long[] points=SkipSegments.boundaries(source==null?null:source.optJSONArray("Chapters"),player==null?0:player.getDuration());boolean[] played=new boolean[points.length];
        video.setExtraAdGroupMarkers(points,played);seekProgress.setChapterMarkers(source==null?null:source.optJSONArray("Chapters"));
        PixelTimeBar bar=video.findViewById(androidx.media3.ui.R.id.exo_progress);if(bar!=null)bar.setChapterMarkers(source==null?null:source.optJSONArray("Chapters"));
    }
    private void skipSegment(){
        if(player==null||skipTarget<0)return;
        if(skipTarget==player.getDuration()){
            cancelCreditsCountdown();completedByCredits=true;skipTarget=-1;stopPlayer();advanceQueue();
        }else{if(skipMode==TvPreferences.SKIP_AUTO)showSkipNotice("已跳过片头",2200);player.seekTo(skipTarget);updateSkip();}
    }
    private static final class TrackRef{
        final Tracks.Group group;final int index;
        TrackRef(Tracks.Group group,int index){this.group=group;this.index=index;}
        boolean supported(){return group.isTrackSupported(index);}
    }
    private TrackRef findTrack(JSONObject stream,int type){
        if(player==null)return null;
        if(source!=null&&source.optBoolean("IsTranscoding")&&stream.optBoolean("IsTranscoded")&&!(source.optBoolean("AudioOnly")&&type==C.TRACK_TYPE_TEXT)){
            if(stream.optBoolean("IsBurnedIn"))return null;
            for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==type)for(int i=0;i<group.length;i++){
                if(!PlaybackSelection.localTrackId(group.getTrackFormat(i).id).startsWith("etkn-subtitle-"))return new TrackRef(group,i);
            }
            return null;
        }
        if(source!=null&&source.optBoolean("IsTranscoding")&&!stream.optBoolean("IsExternal")&&!(source.optBoolean("AudioOnly")&&type==C.TRACK_TYPE_TEXT))return null;
        List<TrackRef> embedded=new ArrayList<>();String externalId=PlaybackSelection.externalId(stream.optInt("Index"));TrackRef named=null,identified=null;int namedCount=0,identityCount=0;
        for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==type)for(int i=0;i<group.length;i++){
            Format format=group.getTrackFormat(i);String id=PlaybackSelection.localTrackId(format.id);boolean external=id.startsWith("etkn-subtitle-");
            if(stream.optBoolean("IsExternal")){if(externalId.equals(id))return new TrackRef(group,i);}
            else if(!external){TrackRef ref=new TrackRef(group,i);embedded.add(ref);if(DiscTrackIdentity.matches(stream,format.id)){identified=ref;identityCount++;}if(!stream.optString("Title").isEmpty()&&stream.optString("Title").equals(format.label)){named=ref;namedCount++;}}
        }
        if(stream.optBoolean("IsExternal"))return null;
        if(stream.optInt("TransportStreamId",-1)>0)return identityCount==1?identified:null;
        if(type==C.TRACK_TYPE_TEXT&&namedCount==1)return named;
        List<JSONObject> expected=new ArrayList<>();for(JSONObject value:PlaybackSelection.streams(source,type==C.TRACK_TYPE_TEXT))if(!value.optBoolean("IsExternal"))expected.add(value);
        // Ordinals are valid only when both parsers exposed the same complete list.
        int ordinal=PlaybackSelection.embeddedOrdinal(source,stream.optInt("Index"),type==C.TRACK_TYPE_TEXT);
        return expected.size()==embedded.size()&&ordinal>=0&&ordinal<embedded.size()?embedded.get(ordinal):null;
    }
    private void applyInitialTrack(int type){
        boolean subtitles=type==C.TRACK_TYPE_TEXT;
        if(player==null||!(subtitles?subtitlePending:audioPending))return;
        int index=subtitles?subtitleIndex:audioIndex;if(index==PlaybackSelection.AUTO)index=PlaybackSelection.defaultIndex(source,subtitles);
        if(subtitles&&subtitleIndex==PlaybackSelection.AUTO&&index<0&&translation!=null)index=translation.sourceIndex;
        if(index<0){if(subtitles){subtitlePending=false;player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,true).build());}else audioPending=false;return;}
        JSONObject selectedStream=PlaybackSelection.stream(source,index,subtitles);
        if(selectedStream!=null&&selectedStream.optBoolean("IsBurnedIn")){subtitlePending=false;return;}
        if(selectedStream==null){if(subtitles)subtitlePending=false;else audioPending=false;return;}
        TrackRef ref=findTrack(selectedStream,type);if(ref==null||!ref.supported())return;
        if(subtitles)subtitlePending=false;else audioPending=false;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(type,false).setOverrideForType(new TrackSelectionOverride(ref.group.getMediaTrackGroup(),ref.index)).build());
    }
    private void chooseTrack(int type){
        if(player==null)return;boolean subtitles=type==C.TRACK_TYPE_TEXT;List<Integer> indices=new ArrayList<>();List<String> labels=new ArrayList<>();
        indices.add(PlaybackSelection.AUTO);labels.add(subtitles&&translation!=null&&translation.active()?translation.label:(subtitles?"":seasonKey==null||seasonKey.isEmpty()?"自动选择 · ":"自动选择（解除本季锁定） · ")+PlaybackSelection.selectionLabel(source,PlaybackSelection.AUTO,subtitles,!subtitles));
        if(subtitles){
            indices.add(-1);
            boolean disabled=player.getTrackSelectionParameters().disabledTrackTypes.contains(type);
            labels.add("关闭字幕"+(disabled?" ✓":""));
        }
        for(JSONObject stream:PlaybackSelection.streams(source,subtitles)){
            indices.add(stream.optInt("Index"));TrackRef ref=findTrack(stream,type);
            boolean active=source.optBoolean("IsTranscoding")?(subtitles?source.optInt("TranscodeSubtitleStreamIndex",-1):source.optInt("TranscodeAudioStreamIndex",-1))==stream.optInt("Index"):(ref!=null&&ref.group.isTrackSelected(ref.index));
            labels.add((subtitles?"":"第 "+(PlaybackSelection.audioOrdinal(source,stream.optInt("Index"))+1)+" 音轨 · ")+PlaybackSelection.streamLabel(stream,!subtitles)+(source.optBoolean("IsTranscoding")?"":ref==null?"  ·  尚未解析":ref.supported()?"":"  ·  解码不支持")+(active?" ✓":""));
        }
        int selectedIndex=indices.indexOf(subtitles?subtitleIndex:audioIndex);
        video.setControllerShowTimeoutMs(0);
        trackDialog=PlayerChoiceDialog.create(this,subtitles?"字幕":"音轨",labels.toArray(new String[0]),selectedIndex,(dialog,index)->{
            if(player==null)return;int wanted=indices.get(index),resolved=wanted==PlaybackSelection.AUTO?PlaybackSelection.defaultIndex(source,subtitles):wanted;
            JSONObject stream=PlaybackSelection.stream(source,resolved,subtitles);TrackRef ref=stream==null?null:findTrack(stream,type);
            if(!source.optBoolean("IsTranscoding")&&resolved>=0&&(ref==null||!ref.supported())){TvNotice.show(this,ref==null?"该轨道尚未解析，不能确认对应关系":"当前解码模式不支持该轨道",Toast.LENGTH_LONG);return;}
            if(subtitles){subtitleIndex=wanted;subtitlePending=true;}else{audioIndex=wanted;audioPending=true;new TvPreferences(this,api).saveSeasonAudio(seasonKey,PlaybackSelection.audioOrdinal(source,wanted));}
            if(source.optBoolean("IsTranscoding")){
                position=Math.max(0,player.getCurrentPosition());
                requestAssistance(source.optBoolean("AudioOnly")?"audio_error":"track_switch");
            }else applyInitialTrack(type);
            updateTrackButtons();dialog.dismiss();
        });
        trackDialog.setOnDismissListener(dialog->{video.setControllerShowTimeoutMs(5000);showControls();});trackDialog.show();
    }
    private String currentTrackTitle(boolean subtitles){
        int type=subtitles?C.TRACK_TYPE_TEXT:C.TRACK_TYPE_AUDIO;
        if(subtitles&&subtitleIndex==PlaybackSelection.AUTO&&translation!=null&&translation.active())return translation.label;
        if(source!=null&&source.optBoolean("IsTranscoding"))return PlaybackSelection.selectionLabel(source,subtitles?source.optInt("TranscodeSubtitleStreamIndex",-1):source.optInt("TranscodeAudioStreamIndex",-1),subtitles);
        if(player!=null&&subtitles&&player.getTrackSelectionParameters().disabledTrackTypes.contains(type))return "关闭";
        for(JSONObject stream:PlaybackSelection.streams(source,subtitles)){TrackRef ref=findTrack(stream,type);if(ref!=null&&ref.group.isTrackSelected(ref.index))return PlaybackSelection.streamLabel(stream);}
        return PlaybackSelection.selectionLabel(source,subtitles?subtitleIndex:audioIndex,subtitles);
    }
    private void updateTrackButtons(){versionButton.setText("版本 · "+PlaybackSelection.slotLabel(source));audioButton.setText("音轨 · "+currentTrackTitle(false));subtitleButton.setText("字幕 · "+currentTrackTitle(true));decoderButton.setText(softwareVideo?"解码 · 软件兼容":"解码 · 自动");int limit=new TvPreferences(this,api).bitrateLimit();bitrateButton.setText("质量 · "+TranscodeChoice.label(limit));}

    private void chooseBitrate(){
        if(source==null)return;int limit=new TvPreferences(this,api).bitrateLimit(),checked=TranscodeChoice.index(limit);
        video.setControllerShowTimeoutMs(0);
        AlertDialog dialog=PlayerChoiceDialog.create(this,"播放质量",TranscodeChoice.LABELS,checked,(choice,index)->{
            int limitWanted=TranscodeChoice.BITRATES[index];new TvPreferences(this,api).setBitrateLimit(limitWanted);automaticNetworkBitrate=0;hardwareSurfaceRetry=false;choice.dismiss();
            if(limitWanted==0){stopPlayer();startSource();}
            else{bitrateAttempted=false;if(player!=null)position=Math.max(0,player.getCurrentPosition());JSONObject original=info.optJSONArray("MediaSources").optJSONObject(selected);requestAssistance(!TranscodeChoice.required(original,limitWanted)&&TranscodeChoice.hardwareRequired(original,TranscodeChoice.sourceCapabilities(original))?"hardware_limit":"user_bitrate");}
        });
        dialog.setOnDismissListener(value->{video.setControllerShowTimeoutMs(5000);showControls();bitrateButton.requestFocus();});dialog.show();
    }
    private void requestAssistance(String reason){
        if(assistancePending||assistanceDisabled||!foreground||source==null)return;
        int revision=++assistanceRevision;assistancePending=true;bitrateAttempted=true;showLoading();
        playbackTiming.event("assistance_requested","reason="+reason+" network_bitrate="+automaticNetworkBitrate);
        String expectedItem=itemId,expectedSource=source.optString("Id"),expectedPlay=playSession;
        long at=player==null?position:Math.max(0,player.getCurrentPosition());int limit=new TvPreferences(this,api).bitrateLimit();
        JSONObject original=info.optJSONArray("MediaSources").optJSONObject(selected);
        int requestedAudio=audioIndex==PlaybackSelection.AUTO?PlaybackSelection.defaultIndex(original,false):audioIndex;
        int locked=new TvPreferences(this,api).seasonAudio(seasonKey);
        if(locked>=0)requestedAudio=PlaybackSelection.audioAt(original,locked);
        final int audioRequested=requestedAudio<0?-1:requestedAudio;
        final int subtitleRequested=subtitleIndex;
        int networkLimit=automaticNetworkBitrate;
        reports.execute(()->{
            JSONObject result=null;String failure="";
            try{result=api.post("/api/tv/Items/"+EtknApi.encode(expectedItem)+"/Transcode",new JSONObject().put("MediaSourceId",expectedSource).put("PlaySessionId",expectedPlay).put("PositionTicks",at*10000).put("Reason",reason).put("Bitrate",limit).put("MaxWidth",TranscodeChoice.width(limit)).put("MaxHeight",TranscodeChoice.height(limit)).put("NetworkBitrate",networkLimit).put("H264",TranscodeChoice.capabilities(original)).put("HEVC",TranscodeChoice.hevcCapabilities(original)).put("HevcHdr10",TranscodeChoice.hdrCapabilities(PlayerActivity.this,original)).put("AudioStreamIndex",audioRequested).put("SubtitleIndex",subtitleRequested));}
            catch(Exception error){failure=error.getMessage()==null?"服务端协助转码失败":error.getMessage();}
            JSONObject response=result;String error=failure;
            runOnUiThread(()->{
                if(revision!=assistanceRevision||!foreground||!expectedItem.equals(itemId)||!expectedPlay.equals(playSession)){
                    if(response!=null&&!response.optString("SessionId").isEmpty())IO.execute(()->{try{api.delete("/api/tv/Transcodes/"+response.optString("SessionId"));}catch(Exception ignored){}});
                    return;
                }
                assistancePending=false;
                if(response==null){message(error);showControls();return;}
                if(response.optBoolean("DirectPlay")){
                    boolean disabled=response.has("AssistanceAllowed")&&!response.optBoolean("AssistanceAllowed");
                    if(transcodeSource!=null){stopPlayer();assistanceDisabled=disabled;startSource();}
                    else{assistanceDisabled=disabled;hideLoading();if(player==null)startSource();}
                    return;
                }
                transcodeSession=response.optString("SessionId");transcodeSource=response.optJSONObject("MediaSource");softwareVideo=false;softwareAudio=false;hardwareSurfaceRetry=false;
                if(transcodeSession.isEmpty()||transcodeSource==null){message("服务端未返回可用转码流");return;}
                autoTranscodeNoticePending=!reason.equals("user_bitrate")&&!reason.equals("track_switch")&&!reason.equals("subtitle");
                stopPlayer(false);videoAssistAttempted=false;
                playbackTiming.event("server_transcode",response.optString("Method"));startSource();
                if(!response.optString("Notice").isEmpty())showSkipNotice(response.optString("Notice"),8000);
            });
        });
    }
    private void clearAssistance(){
        automaticNetworkBitrate=0;networkRetryAfter=0;autoTranscodeNoticePending=false;
        assistanceRevision++;assistancePending=false;bitrateAttempted=false;audioAssistAttempted=false;videoAssistAttempted=false;assistanceDisabled=false;String id=transcodeSession;transcodeSession="";transcodeSource=null;
        if(!id.isEmpty())reports.execute(()->{try{api.delete("/api/tv/Transcodes/"+id);}catch(Exception ignored){}});
    }
    private void showSeekProgress(){
        seekKeys=true;video.hideController();controls.setVisibility(View.GONE);skipButton.setVisibility(View.GONE);seekProgress.setVisibility(View.VISIBLE);
        timer.removeCallbacks(seekProgressUpdate);timer.removeCallbacks(seekProgressHide);seekProgressUpdate.run();if(pendingSeekPosition<0)timer.postDelayed(seekProgressHide,5000);video.requestFocus();
    }
    private void commitSeek(){if(player==null||pendingSeekPosition<0)return;videoReadySince=0;long target=pendingSeekPosition;pendingSeekPosition=-1;player.seekTo(target);showSeekProgress();}
    private void hideSeekProgress(){seekKeys=false;pendingSeekPosition=-1;timer.removeCallbacks(seekCommit);timer.removeCallbacks(seekProgressUpdate);timer.removeCallbacks(seekProgressHide);updatePlaybackOverlay();}
    private void showControls(){hideSeekProgress();video.showController();controls.setVisibility(View.VISIBLE);updatePlaybackOverlay();focusNextWhenLoaded=!hasNext();(hasNext()?nextButton:audioButton).requestFocus();}
    private void applyDanmakuState(JSONObject state){
        if(state==null){if(danmaku!=null){danmaku.close();danmaku=null;}danmakuSettingsButton.setVisibility(View.GONE);return;}
        danmakuSettingsButton.setVisibility(View.VISIBLE);
        if(info!=null)try{info.put("Danmaku",state);}catch(JSONException ignored){}
        JSONObject personal=new TvPreferences(this,api).danmaku();boolean enabled=state.optBoolean("Available")&&personal.optBoolean("enabled");
        if(danmaku!=null&&(!enabled||!danmaku.active())){danmaku.close();danmaku=null;}
        if(enabled&&danmaku==null&&player!=null)danmaku=new DanmakuSession(api,itemId,playSession,player,playbackStage,personal,this::showDanmakuNotice);
        else if(danmaku!=null)danmaku.setStyle(personal);
        if(danmakuPanelToggle!=null)danmakuPanelToggle.setText(!state.optBoolean("Available")?"弹幕 · 服务关闭":personal.optBoolean("enabled")?"弹幕 · 开启":"弹幕 · 关闭");
    }
    private void showDanmakuNotice(String value,long duration){
        if(value.isEmpty()){if(skipNotice!=null&&skipNotice.getText().toString().equals(danmakuNotice))hideSkipNotice.run();}
        else if(android.os.SystemClock.elapsedRealtime()>=autoTranscodeNoticeUntil)showUnifiedNotice(value,duration);
        danmakuNotice=value;
    }
    private void saveDanmaku(JSONObject settings){
        new TvPreferences(this,api).saveDanmaku(settings);danmakuRevision++;applyDanmakuState(info==null?null:info.optJSONObject("Danmaku"));
    }
    private void toggleDanmaku(){
        if(danmakuSaving||info==null||player==null)return;
        JSONObject current=info.optJSONObject("Danmaku"),personal=new TvPreferences(this,api).danmaku();
        boolean enabled=!personal.optBoolean("enabled");
        try{personal.put("enabled",enabled);saveDanmaku(personal);}catch(JSONException ignored){return;}
        if(!enabled||current!=null&&current.optBoolean("Available"))return;
        danmakuSaving=true;int revision=++danmakuRevision;String expected=playSession;ExoPlayer expectedPlayer=player;
        async(()->api.get("/api/tv/System/Danmaku"),state->{
            danmakuSaving=false;if(revision!=danmakuRevision||!expected.equals(playSession)||player!=expectedPlayer||!foreground)return;
            applyDanmakuState(state);if(!state.optBoolean("Available"))showSkipNotice("请在服务端增强功能中启用弹幕服务",2500);
        });
    }
    private void showDanmakuSettings(){
        if(player==null||danmakuDialog!=null&&danmakuDialog.isShowing())return;video.setControllerShowTimeoutMs(0);
        LinearLayout panel=column();panel.setPadding(dp(22),dp(18),dp(22),dp(18));panel.addView(text("弹幕设置",22,TEXT));
        Button enabled=button("",this::toggleDanmaku);danmakuPanelToggle=enabled;panel.addView(enabled);applyDanmakuState(info==null?null:info.optJSONObject("Danmaku"));
        Button color=button("",()->{});color.setContentDescription("弹幕跟随主题色");
        Runnable colorLabel=()->color.setText("跟随主题色 · "+(new TvPreferences(this,api).danmaku().optBoolean("theme_color")?"开启":"关闭"));colorLabel.run();
        color.setOnClickListener(view->{JSONObject settings=new TvPreferences(this,api).danmaku();try{settings.put("theme_color",!settings.optBoolean("theme_color"));saveDanmaku(settings);colorLabel.run();}catch(JSONException ignored){}});panel.addView(color);
        danmakuSlider(panel,"行数","rows",1,16,1,8,"");
        danmakuSlider(panel,"密度","density",10,100,10,100,"%");
        danmakuSlider(panel,"字号","font_size",12,40,1,21,"");
        danmakuSlider(panel,"不透明度","opacity",10,100,5,85,"%");
        danmakuSlider(panel,"移动速度","speed",.5,2,.25,1,"×");
        danmakuDialog=dialogBuilder().setView(panel).create();
        danmakuDialog.setOnShowListener(value->{android.view.Window window=danmakuDialog.getWindow();if(window!=null){window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setBackgroundDrawable(shape(PANEL,ACCENT));window.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);window.setLayout(dp(Math.min(350,getResources().getConfiguration().screenWidthDp-48)),-2);}themeDialog(danmakuDialog);enabled.requestFocus();});
        danmakuDialog.setOnDismissListener(value->{danmakuDialog=null;danmakuPanelToggle=null;if(foreground&&player!=null){video.setControllerShowTimeoutMs(5000);showControls();danmakuSettingsButton.requestFocus();}});danmakuDialog.show();
    }
    private void danmakuSlider(LinearLayout panel,String name,String key,double min,double max,double step,double fallback,String suffix){
        JSONObject settings=new TvPreferences(this,api).danmaku();double value=Math.max(min,Math.min(max,settings.optDouble(key,fallback)));
        TextView label=text(name+" · "+new java.math.BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString()+suffix,15,TEXT);panel.addView(label);
        SeekBar slider=new SeekBar(this);slider.setContentDescription(name);slider.setMax((int)Math.round((max-min)/step));slider.setProgress((int)Math.round((value-min)/step));slider.setKeyProgressIncrement(1);
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(resolveColor(ACCENT)));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(resolveColor(ACCENT)));slider.setBackground(selectionBackground());panel.addView(slider,new LinearLayout.LayoutParams(-1,dp(42)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar view,int progress,boolean fromUser){if(!fromUser)return;double next=min+progress*step;JSONObject updated=new TvPreferences(PlayerActivity.this,api).danmaku();try{updated.put(key,next);saveDanmaku(updated);}catch(JSONException ignored){}label.setText(name+" · "+new java.math.BigDecimal(Double.toString(next)).stripTrailingZeros().toPlainString()+suffix);}
            @Override public void onStartTrackingTouch(SeekBar view){}
            @Override public void onStopTrackingTouch(SeekBar view){}
        });
    }
    private String speedLabel(){return String.format(Locale.ROOT,"%s×",new java.math.BigDecimal(Float.toString(playbackSpeed)).stripTrailingZeros().toPlainString());}
    private void chooseSpeed(){
        if(player==null)return;float[] speeds={0.25f,0.5f,0.75f,1f,1.25f,1.5f,2f};String[] labels=new String[speeds.length];int selectedSpeed=3;
        for(int i=0;i<speeds.length;i++){labels[i]=speeds[i]+"×";if(Math.abs(speeds[i]-playbackSpeed)<0.01f)selectedSpeed=i;}
        video.setControllerShowTimeoutMs(0);
        speedDialog=PlayerChoiceDialog.create(this,"播放倍速",labels,selectedSpeed,(dialog,index)->{if(player!=null)player.setPlaybackSpeed(speeds[index]);dialog.dismiss();});
        speedDialog.setOnDismissListener(dialog->{video.setControllerShowTimeoutMs(5000);showControls();speedButton.requestFocus();});speedDialog.show();
    }
    private void chooseDecoder(){
        if(source==null)return;video.setControllerShowTimeoutMs(0);
        AlertDialog dialog=PlayerChoiceDialog.create(this,"视频解码",new String[]{"硬件解码：优先使用设备解码器","软件兼容：高分辨率可能较慢"},softwareVideo?1:0,(choice,index)->{if(player==null||softwareVideo!=(index==1)){stopPlayer();softwareVideo=index==1;hardwareSurfaceRetry=false;startSource();}choice.dismiss();});
        dialog.setOnDismissListener(value->{video.setControllerShowTimeoutMs(5000);showControls();});dialog.show();
    }
    private void report(String event){
        if(source==null)return;long milliseconds=player==null?position:Math.max(0,player.getCurrentPosition());boolean paused=player==null||!player.isPlaying();long duration=player==null?source.optLong("RunTimeTicks"):player.getDuration()>0?player.getDuration()*10000:source.optLong("RunTimeTicks");
        try{JSONObject payload=new JSONObject().put("ItemId",itemId).put("UserId",api.session.userId()).put("MediaSourceId",source.optString("Id")).put("PlaySessionId",playSession).put("PositionTicks",milliseconds*10000).put("RunTimeTicks",duration).put("IsPaused",paused).put("CanSeek",true).put("PlayMethod","DirectStream").put("Client","ETKN TV");
            payload.put("TranscodeSessionId",transcodeSession);
            if(event.equals("Stopped")&&completedByCredits)payload.put("PlayedToCompletion",true);
            if(event.equals("Stopped"))notifyPlaybackProgress(payload,false);
            int revision=danmakuRevision;ExoPlayer expectedPlayer=player;String expectedSession=playSession;
            JSONObject currentState=info==null?null:info.optJSONObject("Danmaku");
            if(!event.equals("Stopped")&&!danmakuSaving&&currentState!=null&&currentState.optBoolean("Available")&&new TvPreferences(this,api).danmaku().optBoolean("enabled"))payload.put("DanmakuActive",true);
            reports.execute(()->{try{JSONObject result=api.post("/api/tv/Sessions/Playing"+(event.equals("Started")?"":"/"+event),payload);if(event.equals("Stopped"))notifyPlaybackProgress(payload,true);JSONObject state=result.optJSONObject("Danmaku");
                if(state!=null)runOnUiThread(()->{if(revision==danmakuRevision&&expectedSession.equals(playSession)&&player==expectedPlayer&&player!=null&&foreground&&!danmakuSaving)applyDanmakuState(state);});
            }catch(Exception ignored){}});
        }catch(Exception ignored){}
    }
    private void notifyPlaybackProgress(JSONObject payload,boolean synced){
        Intent update=new Intent(DetailActivity.ACTION_PLAYBACK_PROGRESS).setPackage(getPackageName()).putExtra("itemId",payload.optString("ItemId")).putExtra("positionTicks",payload.optLong("PositionTicks")).putExtra("runtimeTicks",payload.optLong("RunTimeTicks")).putExtra("completed",payload.optBoolean("PlayedToCompletion")).putExtra("synced",synced);sendBroadcast(update);
    }
    private void stopPlayer(){stopPlayer(true);}
    private void stopPlayer(boolean releaseAssistance){cancelConfirm();if(danmakuDialog!=null)danmakuDialog.dismiss();danmakuSaving=false;danmakuRevision++;if(danmaku!=null){danmaku.close();danmaku=null;}if(translation!=null){translation.close();translation=null;}cancelCreditsCountdown();creditsSkipCancelled=false;hideSeekProgress();timer.removeCallbacks(progress);timer.removeCallbacks(skipCheck);timer.removeCallbacks(videoCheck);if(skipButton!=null)skipButton.setVisibility(View.GONE);if(player!=null){position=Math.max(0,player.getCurrentPosition());playWhenReady=player.getPlayWhenReady();if(releaseAssistance||transcodeSession.isEmpty())report("Stopped");video.setPlayer(null);player.release();player=null;TvPlayerFactory.clearAssView(video.getSubtitleView());}if(releaseAssistance)clearAssistance();updatePlaybackOverlay();}
    @Override protected void onStart(){super.onStart();if(!LoginGate.isSatisfied()&&api.session.persist()!=SessionStore.PERSIST_FULL)return;foreground=true;if(info!=null&&player==null)startSource();}
    private void cancelConfirm(){timer.removeCallbacks(confirmLongPress);confirmPress=null;confirmLongPressed=false;}
    @Override protected void onPause(){cancelConfirm();cancelCreditsCountdown();hideSeekProgress();super.onPause();}
    @Override protected void onStop(){foreground=false;stopPlayer();clearAssistance();super.onStop();}
    @Override protected void onDestroy(){timer.removeCallbacksAndMessages(null);reports.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putFloat("speed",playbackSpeed);state.putLong("position",player==null?position:player.getCurrentPosition());state.putString("sourceId",source==null?getIntent().getStringExtra("sourceId"):source.optString("Id"));state.putInt("audioIndex",audioIndex);state.putInt("subtitleIndex",subtitleIndex);state.putBoolean("softwareVideo",softwareVideo);state.putBoolean("hardwareSurfaceRetry",hardwareSurfaceRetry);state.putBoolean("softwareAudio",softwareAudio);state.putBoolean("enhancedSubtitles",enhancedSubtitles);state.putBoolean("playWhenReady",player==null?playWhenReady:player.getPlayWhenReady());super.onSaveInstanceState(state);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        if(key==KeyEvent.KEYCODE_BACK){cancelConfirm();if(down&&event.getRepeatCount()==0){if(episodePanel.isShown())hideEpisodes();else if(creditsCountdownStartedAt>=0){creditsSkipCancelled=true;cancelCreditsCountdown();}else if(seekKeys||controls.getVisibility()==View.VISIBLE||video.isControllerFullyVisible()){hideSeekProgress();video.hideController();controls.setVisibility(View.GONE);video.requestFocus();}else finish();}return true;}
        if(down)focusNextWhenLoaded=false;
        if(episodePanel.isShown()){
            if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||key==KeyEvent.KEYCODE_MEDIA_REWIND)return true;
            if(key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT){View focus=getCurrentFocus();int direction=key==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:key==KeyEvent.KEYCODE_DPAD_DOWN?View.FOCUS_DOWN:key==KeyEvent.KEYCODE_DPAD_LEFT?View.FOCUS_LEFT:View.FOCUS_RIGHT;if(focus==null||!episodePanel.contains(focus.focusSearch(direction)))return true;}
            return super.dispatchKeyEvent(event);
        }
        if(key==KeyEvent.KEYCODE_DPAD_UP){if(down&&event.getRepeatCount()==0)showEpisodes();return true;}
        if(key==KeyEvent.KEYCODE_MENU){if(down&&event.getRepeatCount()==0)chooseTrack(C.TRACK_TYPE_AUDIO);return true;}
        boolean mediaSeek=key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||key==KeyEvent.KEYCODE_MEDIA_REWIND;
        if((key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||mediaSeek)&&video!=null&&(mediaSeek||!video.isControllerFullyVisible()||seekKeys)){
            if(player!=null){
                timer.removeCallbacks(seekCommit);
                if(down){long end=player.getDuration(),step=PlaybackSelection.seekStepMillis(end,event.getRepeatCount()>0);long target=Math.max(0,(pendingSeekPosition>=0?pendingSeekPosition:player.getCurrentPosition())+(key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD?step:-step));pendingSeekPosition=end>0?Math.min(target,end):target;showSeekProgress();}
                else if(event.getAction()==KeyEvent.ACTION_UP){if(event.isCanceled())hideSeekProgress();else if(pendingSeekPosition>=0)timer.postDelayed(seekCommit,250);}
            }return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_DOWN&&seekKeys){if(down){commitSeek();showControls();}return true;}
        if((key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&player!=null){
            if(down&&event.getRepeatCount()==0){cancelConfirm();confirmPress=new KeyEvent(event);timer.postDelayed(confirmLongPress,android.view.ViewConfiguration.getLongPressTimeout());}
            else if(event.getAction()==KeyEvent.ACTION_UP){KeyEvent pressed=confirmPress;boolean held=confirmLongPressed;cancelConfirm();if(pressed!=null&&!event.isCanceled()&&!held){if(!video.isControllerFullyVisible()||seekKeys){if(skipButton.getVisibility()==View.VISIBLE&&skipTarget>=0)skipSegment();else if(player.getPlayWhenReady())player.pause();else player.play();}else{super.dispatchKeyEvent(pressed);super.dispatchKeyEvent(event);}}}return true;
        }
        if(video!=null&&!video.isControllerFullyVisible()){
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(down)showControls();return true;}
        }
        return video!=null&&video.dispatchMediaKeyEvent(event)||super.dispatchKeyEvent(event);
    }
}
