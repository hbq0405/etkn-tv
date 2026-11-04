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
    private static final int BUFFERED=android.graphics.Color.rgb(90,178,230),BAR_IDLE=android.graphics.Color.rgb(72,82,92);
    private ExoPlayer player;
    private PlaybackTiming playbackTiming;
    private SubtitleTranslation translation;
    private PlayerView video;
    private PixelTimeBar seekProgress;
    private FrameLayout playbackOverlay;
    private AppearanceBackground waitingBackground;
    private ImageView pauseIcon,pauseAvatar;
    private TextView pauseName;
    private TextClock pauseClock;
    private boolean playbackIdentityRequested;
    private LinearLayout controls;
    private Button versionButton,audioButton,subtitleButton,decoderButton,skipButton,nextButton,speedButton;
    private AlertDialog speedDialog;
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
    private boolean softwareVideo,playWhenReady=true;
    private boolean softwareAudio=true,enhancedSubtitles=true;
    private boolean videoFrameSeen,completedByCredits,introSkippedAtStart,creditsSkipCancelled;
    private long videoReadySince;
    private String videoDecoder="等待解码器";
    private TextView videoInfo;
    private KeyEvent confirmPress;
    private boolean confirmLongPressed;
    private TextView skipNotice;
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
        if(!expected||videoFrameSeen||!player.getPlayWhenReady()||player.getPlaybackState()!=Player.STATE_READY)videoReadySince=0;
        else if(videoReadySince==0)videoReadySince=android.os.SystemClock.elapsedRealtime();
        else if(android.os.SystemClock.elapsedRealtime()-videoReadySince>=6000){recoverVideo();return;}
        updateVideoInfo();if(episodePanel!=null&&episodePanel.isShown())episodePanel.updateCurrent(player);timer.postDelayed(this,1000);
    }};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);itemId=getIntent().getStringExtra("id");playbackTiming=new PlaybackTiming(itemId);seasonKey=getIntent().getStringExtra("seasonKey");position=state==null?getIntent().getLongExtra("position",0):state.getLong("position");
        audioIndex=state==null?getIntent().getIntExtra("audioIndex",PlaybackSelection.AUTO):state.getInt("audioIndex",PlaybackSelection.AUTO);subtitleIndex=state==null?getIntent().getIntExtra("subtitleIndex",PlaybackSelection.AUTO):state.getInt("subtitleIndex",PlaybackSelection.AUTO);
        playbackSpeed=state==null?getIntent().getFloatExtra("speed",1f):state.getFloat("speed",1f);
        softwareVideo=state!=null&&state.getBoolean("softwareVideo");
        softwareAudio=state==null||state.getBoolean("softwareAudio",true);enhancedSubtitles=state==null||state.getBoolean("enhancedSubtitles",true);
        playWhenReady=state==null||state.getBoolean("playWhenReady",true);skipMode=new TvPreferences(this,api).skipMode();
        queueIds=getIntent().getStringArrayExtra("queue");queueIndex=getIntent().getIntExtra("queueIndex",0);queueMode=queueIds!=null&&queueIds.length>0;
        String seriesExtra=getIntent().getStringExtra("seriesId");nextLoaded=seriesExtra==null||seriesExtra.isEmpty();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(android.graphics.Color.BLACK);
        video=createVideoView(useTextureVideo());
        stage.addView(video,new FrameLayout.LayoutParams(-1,-1));
        waitingBackground=new AppearanceBackground(this);stage.addView(waitingBackground,new FrameLayout.LayoutParams(-1,-1));
        seekProgress=new PixelTimeBar(this);seekProgress.setEnabled(false);seekProgress.setVisibility(View.GONE);
        FrameLayout.LayoutParams seekLayout=new FrameLayout.LayoutParams(-1,dp(64),Gravity.BOTTOM);seekLayout.setMargins(dp(28),0,dp(28),0);stage.addView(seekProgress,seekLayout);
        createPlaybackOverlay(stage);
        controls=column();controls.setPadding(dp(24),dp(12),dp(24),dp(12));controls.setGravity(Gravity.CENTER_HORIZONTAL);controls.setBackground(new AppearanceCapsule(dp(1000)));
        TextView title=text(getIntent().getStringExtra("title"),18,TEXT);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);controls.addView(title);
        LinearLayout actions=row();versionButton=button("版本",this::chooseVersion);actions.addView(versionButton);
        audioButton=button("音轨",()->chooseTrack(C.TRACK_TYPE_AUDIO));subtitleButton=button("字幕",()->chooseTrack(C.TRACK_TYPE_TEXT));decoderButton=button("解码",this::chooseDecoder);
        speedButton=button("倍速 · "+speedLabel(),this::chooseSpeed);
        nextButton=button("下一集",this::advanceQueue);nextButton.setVisibility(View.GONE);
        actions.addView(audioButton);actions.addView(subtitleButton);actions.addView(nextButton);actions.addView(speedButton);actions.addView(decoderButton);
        for(int i=0;i<actions.getChildCount();i++)actions.getChildAt(i).setBackground(selectionBackground());
        actions.setGravity(Gravity.CENTER);HorizontalScrollView actionScroll=new FocusScroll.Row(this);actionScroll.setHorizontalScrollBarEnabled(false);actionScroll.setFillViewport(true);actionScroll.addView(actions);controls.addView(actionScroll);
        FrameLayout.LayoutParams overlay=new FrameLayout.LayoutParams(Math.min(dp(820),getResources().getDisplayMetrics().widthPixels-dp(96)),-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);overlay.bottomMargin=dp(70);stage.addView(controls,overlay);controls.setVisibility(View.GONE);
        videoInfo=text("",14,TEXT);videoInfo.setPadding(dp(16),dp(12),dp(16),dp(12));videoInfo.setBackground(selectionBackground());videoInfo.setVisibility(View.GONE);androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(videoInfo,10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);FrameLayout.LayoutParams mediaLayout=new FrameLayout.LayoutParams(dp(Math.min(400,getResources().getConfiguration().screenWidthDp*2/5)),-1,Gravity.TOP|Gravity.START);mediaLayout.setMargins(dp(28),dp(88),0,dp(180));stage.addView(videoInfo,mediaLayout);
        skipButton=button("跳过片头",this::skipSegment);skipButton.setBackground(selectionBackground());skipButton.setVisibility(View.GONE);FrameLayout.LayoutParams skipLayout=new FrameLayout.LayoutParams(-2,dp(44),Gravity.BOTTOM|Gravity.END);skipLayout.rightMargin=dp(28);skipLayout.bottomMargin=dp(220);stage.addView(skipButton,skipLayout);
        status=text("正在读取播放信息…",15,android.graphics.Color.WHITE);status.setPadding(dp(24),dp(12),dp(24),dp(12));status.setBackgroundColor(android.graphics.Color.argb(180,0,0,0));stage.addView(status,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        skipNotice=text("",18,android.graphics.Color.WHITE);skipNotice.setPadding(dp(24),dp(12),dp(24),dp(12));skipNotice.setBackgroundColor(android.graphics.Color.argb(190,0,0,0));skipNotice.setVisibility(View.GONE);FrameLayout.LayoutParams noticeLayout=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);noticeLayout.topMargin=dp(48);stage.addView(skipNotice,noticeLayout);
        episodePanel=new PlayerEpisodePanel(this,itemId,this::playEpisode,this::loadNextEpisode);
        episodePanel.setVisibility(View.GONE);stage.addView(episodePanel,new FrameLayout.LayoutParams(dp(Math.min(480,getResources().getConfiguration().screenWidthDp*3/5)),-1,Gravity.END));
        setContentView(stage);video.hideController();video.requestFocus();
        if(queueMode&&!getIntent().getBooleanExtra("resolved",false)){status.setText("正在解析播放队列…");resolveQueueUnit(itemId,true);return;}
        String requestedSource=state==null?getIntent().getStringExtra("sourceId"):state.getString("sourceId",getIntent().getStringExtra("sourceId"));
        playbackTiming.event("info_queued");
        async(()->{playbackTiming.event("info_request");JSONObject result=api.post("/api/tv/Items/"+EtknApi.encode(itemId)+"/PlaybackInfo?UserId="+EtknApi.encode(api.session.userId()),new JSONObject());playbackTiming.event("info_received");return result;},result->{
            playbackTiming.event("info_delivered");
            info=result;playSession=info.optString("PlaySessionId");JSONArray sources=info.optJSONArray("MediaSources");
            if(sources==null||sources.length()==0){status.setText("暂无可播放版本");return;}
            int preferred=PlaybackSelection.sourceIndex(sources,requestedSource);
            if(requestedSource!=null&&!requestedSource.isEmpty()&&preferred<0){status.setText("所选版本已不可用，请返回重新选择");return;}
            if(preferred>=0){selected=preferred;versionConfirmed=true;startSource();}
            else if(sources.length()>1&&!getIntent().getBooleanExtra("resume",false))chooseVersion();else{versionConfirmed=true;startSource();}
        });
        loadNextEpisode();
    }
    // L055FCN 保留 SDR 旋转兼容；HDR 在创建解码器前选择 SurfaceView，保留颜色信号。
    private boolean useTextureVideo(){return PlaybackSelection.useTextureVideo(softwareVideo,android.os.Build.MODEL,android.os.Build.VERSION.SDK_INT,source);}
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
    @Override void appearanceChanged(){if(waitingBackground!=null)waitingBackground.update();}
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
        String series=getIntent().getStringExtra("seriesId");if(series==null||series.isEmpty()){Toast.makeText(this,"当前视频没有分集",Toast.LENGTH_SHORT).show();return;}
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
        queueIndex++;message("正在准备下一项…");resolveQueueUnit(queueIds[queueIndex],false);
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
                if(first)status.setText("队列中没有可播放的媒体项");else finish();
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
        PlayerChoiceDialog.create(this,"选择播放版本",labels,selected,(dialog,index)->{stopPlayer();selected=index;versionConfirmed=true;audioIndex=subtitleIndex=PlaybackSelection.AUTO;dialog.dismiss();startSource();}).show();
    }
    private void startSource(){
        if(!foreground||!versionConfirmed||info==null||player!=null||pendingAdvance)return;
        try{
            playbackTiming.event("source_start");
            completedByCredits=false;
            source=info.getJSONArray("MediaSources").getJSONObject(selected);
            boolean texture=useTextureVideo();
            if((video.getVideoSurfaceView() instanceof TextureView)!=texture){
                ViewGroup parent=(ViewGroup)video.getParent();int index=parent.indexOfChild(video);ViewGroup.LayoutParams layout=video.getLayoutParams();
                parent.removeView(video);video=createVideoView(texture);parent.addView(video,index,layout);
            }
            int locked=new TvPreferences(this,api).seasonAudio(seasonKey);
            if(locked>=0){audioIndex=PlaybackSelection.audioAt(source,locked);if(audioIndex==PlaybackSelection.AUTO&&missingSeasonAudio!=locked){missingSeasonAudio=locked;Toast.makeText(this,"本集缺少第 "+(locked+1)+" 音轨，使用默认音轨；本季锁定已保留",Toast.LENGTH_LONG).show();}}
            String address=PlaybackAddress.resolve(source,api.server,api.token,api.session.userId());
            JSONObject translationPlan=source.optJSONObject("SubtitleTranslation");
            if(translationPlan!=null&&subtitleIndex==PlaybackSelection.AUTO)
                translation=new SubtitleTranslation(api,itemId,source.optString("Id"),translationPlan,video,()->player,()->subtitleIndex==PlaybackSelection.AUTO);
            try{player=TvPlayerFactory.create(this,video.getSubtitleView(),softwareVideo,softwareAudio,enhancedSubtitles,translation,playbackTiming);}
            catch(RuntimeException|LinkageError failure){if(!enhancedSubtitles)throw failure;enhancedSubtitles=false;TvPlayerFactory.clearAssView(video.getSubtitleView());player=TvPlayerFactory.create(this,video.getSubtitleView(),softwareVideo,softwareAudio,false,translation,playbackTiming);Toast.makeText(this,"特效字幕初始化失败，已切换兼容字幕",Toast.LENGTH_LONG).show();}
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
                Uri uri=Uri.parse(delivery).buildUpon().appendQueryParameter("X-Emby-Token",api.token).appendQueryParameter("UserId",api.session.userId()).build();
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
            player.setMediaItem(new MediaItem.Builder().setUri(address).setSubtitleConfigurations(subtitles).build(),startPosition);
            reportedStart=false;
            videoFrameSeen=false;videoReadySince=0;videoDecoder="等待解码器";updatePlaybackOverlay();
            player.addAnalyticsListener(new androidx.media3.exoplayer.analytics.AnalyticsListener(){
                @Override public void onVideoDecoderInitialized(EventTime time,String name,long at,long duration){playbackTiming.event("video_decoder","init_ms="+duration);videoDecoder=name;if(player!=null)applyVideoRotation(player.getVideoSize());updateVideoInfo();}
            });
            player.addListener(new Player.Listener(){
                @Override public void onEvents(Player current,Player.Events events){updatePlaybackOverlay();}
                @Override public void onTimelineChanged(Timeline timeline,int reason){updateTimelineMarkers();}
                @Override public void onPlaybackParametersChanged(PlaybackParameters parameters){playbackSpeed=parameters.speed;speedButton.setText("倍速 · "+speedLabel());}
                @Override public void onRenderedFirstFrame(){playbackTiming.event("first_frame");videoFrameSeen=true;videoReadySince=0;updatePlaybackOverlay();updateVideoInfo();}
                @Override public void onVideoSizeChanged(VideoSize size){applyVideoRotation(size);}
                @Override public void onTracksChanged(Tracks tracks){applyInitialTrack(C.TRACK_TYPE_AUDIO);applyInitialTrack(C.TRACK_TYPE_TEXT);updateTrackButtons();}
                @Override public void onPlaybackStateChanged(int state){playbackTiming.event("state_"+state,"position_ms="+(player==null?-1:player.getCurrentPosition())+" buffered_ms="+(player==null?-1:player.getTotalBufferedDuration()));if(state==Player.STATE_READY&&!reportedStart){reportedStart=true;report("Started");timer.postDelayed(progress,10000);status.setVisibility(View.GONE);updateTrackButtons();if(introSkippedAtStart){introSkippedAtStart=false;showSkipNotice("已跳过片头",2200);}}if(state==Player.STATE_ENDED)advanceQueue();}
                @Override public void onPlayerError(PlaybackException error){handlePlayerError(error);}
            });
            updateTimelineMarkers();playbackTiming.event("prepare");player.prepare();player.setPlayWhenReady(playWhenReady);timer.post(skipCheck);timer.post(videoCheck);video.hideController();if(episodePanel.isShown())renderEpisodes();else video.requestFocus();updateTrackButtons();
        }catch(Exception error){message(error.getMessage()==null?"无法开始播放":error.getMessage());}
    }
    private void message(String value){status.setText(value);status.setVisibility(View.VISIBLE);}
    private void showSkipNotice(String value,long hideAfter){if(skipNotice==null)return;timer.removeCallbacks(hideSkipNotice);skipNotice.setText(value);skipNotice.setVisibility(View.VISIBLE);if(hideAfter>0)timer.postDelayed(hideSkipNotice,hideAfter);}
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
        if(player==null||videoInfo==null||videoInfo.getVisibility()!=View.VISIBLE)return;Format format=player.getVideoFormat();
        if(format==null)for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_VIDEO)for(int i=0;i<group.length;i++)if(group.isTrackSelected(i))format=group.getTrackFormat(i);
        JSONObject videoStream=null,onlyVideo=null;int videoCount=0;JSONArray streams=source==null?null:source.optJSONArray("MediaStreams");if(streams!=null)for(int i=0;i<streams.length();i++){JSONObject stream=streams.optJSONObject(i);if(stream!=null&&"Video".equalsIgnoreCase(stream.optString("Type"))){videoCount++;onlyVideo=stream;if(format!=null&&Integer.toString(stream.optInt("Index",-1)).equals(PlaybackSelection.localTrackId(format.id)))videoStream=stream;}}if(videoStream==null&&videoCount==1)videoStream=onlyVideo;
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
        if(segment!=null&&skipMode==TvPreferences.SKIP_BUTTON&&!seekKeys&&!episodePanel.isShown()){skipButton.setText(segment.label);skipButton.setVisibility(View.VISIBLE);}else{if(skipButton.isFocused()){if(controls.getVisibility()==View.VISIBLE)audioButton.requestFocus();else video.requestFocus();}skipButton.setVisibility(View.GONE);}
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
        if(subtitles&&subtitleIndex==PlaybackSelection.AUTO&&index<0&&translation!=null)index=translation.sourceIndex;
        if(index<0){if(subtitles){subtitlePending=false;player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,true).build());}else audioPending=false;return;}
        JSONObject selectedStream=PlaybackSelection.stream(source,index,subtitles);
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
            boolean active=ref!=null&&ref.group.isTrackSelected(ref.index);
            labels.add((subtitles?"":"第 "+(PlaybackSelection.audioOrdinal(source,stream.optInt("Index"))+1)+" 音轨 · ")+PlaybackSelection.streamLabel(stream,!subtitles)+(ref==null?"  ·  尚未解析":ref.supported()?"":"  ·  解码不支持")+(active?" ✓":""));
        }
        int selectedIndex=indices.indexOf(subtitles?subtitleIndex:audioIndex);
        video.setControllerShowTimeoutMs(0);
        trackDialog=PlayerChoiceDialog.create(this,subtitles?"字幕":"音轨",labels.toArray(new String[0]),selectedIndex,(dialog,index)->{
            if(player==null)return;int wanted=indices.get(index),resolved=wanted==PlaybackSelection.AUTO?PlaybackSelection.defaultIndex(source,subtitles):wanted;
            JSONObject stream=PlaybackSelection.stream(source,resolved,subtitles);TrackRef ref=stream==null?null:findTrack(stream,type);
            if(resolved>=0&&(ref==null||!ref.supported())){Toast.makeText(this,ref==null?"该轨道尚未解析，不能确认对应关系":"当前解码模式不支持该轨道",Toast.LENGTH_LONG).show();return;}
            if(subtitles){subtitleIndex=wanted;subtitlePending=true;}else{audioIndex=wanted;audioPending=true;new TvPreferences(this,api).saveSeasonAudio(seasonKey,PlaybackSelection.audioOrdinal(source,wanted));}
            applyInitialTrack(type);updateTrackButtons();dialog.dismiss();
        });
        trackDialog.setOnDismissListener(dialog->{video.setControllerShowTimeoutMs(5000);showControls();});trackDialog.show();
    }
    private String currentTrackTitle(boolean subtitles){
        int type=subtitles?C.TRACK_TYPE_TEXT:C.TRACK_TYPE_AUDIO;
        if(subtitles&&subtitleIndex==PlaybackSelection.AUTO&&translation!=null&&translation.active())return translation.label;
        if(player!=null&&subtitles&&player.getTrackSelectionParameters().disabledTrackTypes.contains(type))return "关闭";
        for(JSONObject stream:PlaybackSelection.streams(source,subtitles)){TrackRef ref=findTrack(stream,type);if(ref!=null&&ref.group.isTrackSelected(ref.index))return PlaybackSelection.streamLabel(stream);}
        return PlaybackSelection.selectionLabel(source,subtitles?subtitleIndex:audioIndex,subtitles);
    }
    private void updateTrackButtons(){versionButton.setText("版本 · "+PlaybackSelection.slotLabel(source));audioButton.setText("音轨 · "+currentTrackTitle(false));subtitleButton.setText("字幕 · "+currentTrackTitle(true));decoderButton.setText(softwareVideo?"解码 · 软件兼容":"解码 · 自动");}
    private void showSeekProgress(){
        seekKeys=true;video.hideController();controls.setVisibility(View.GONE);skipButton.setVisibility(View.GONE);seekProgress.setVisibility(View.VISIBLE);
        timer.removeCallbacks(seekProgressUpdate);timer.removeCallbacks(seekProgressHide);seekProgressUpdate.run();if(pendingSeekPosition<0)timer.postDelayed(seekProgressHide,5000);video.requestFocus();
    }
    private void commitSeek(){if(player==null||pendingSeekPosition<0)return;long target=pendingSeekPosition;pendingSeekPosition=-1;player.seekTo(target);showSeekProgress();}
    private void hideSeekProgress(){seekKeys=false;pendingSeekPosition=-1;timer.removeCallbacks(seekCommit);timer.removeCallbacks(seekProgressUpdate);timer.removeCallbacks(seekProgressHide);updatePlaybackOverlay();}
    private void showControls(){hideSeekProgress();video.showController();controls.setVisibility(View.VISIBLE);updatePlaybackOverlay();focusNextWhenLoaded=!hasNext();(hasNext()?nextButton:audioButton).requestFocus();}
    private String speedLabel(){return String.format(Locale.ROOT,"%s×",new java.math.BigDecimal(Float.toString(playbackSpeed)).stripTrailingZeros().toPlainString());}
    private void chooseSpeed(){
        if(player==null)return;float[] speeds={0.25f,0.5f,0.75f,1f,1.25f,1.5f,2f};String[] labels=new String[speeds.length];int selectedSpeed=3;
        for(int i=0;i<speeds.length;i++){labels[i]=speeds[i]+"×";if(Math.abs(speeds[i]-playbackSpeed)<0.01f)selectedSpeed=i;}
        video.setControllerShowTimeoutMs(0);
        speedDialog=PlayerChoiceDialog.create(this,"播放倍速",labels,selectedSpeed,(dialog,index)->{if(player!=null)player.setPlaybackSpeed(speeds[index]);dialog.dismiss();});
        speedDialog.setOnDismissListener(dialog->{video.setControllerShowTimeoutMs(5000);showControls();speedButton.requestFocus();});speedDialog.show();
    }
    private void chooseDecoder(){
        if(player==null)return;video.setControllerShowTimeoutMs(0);
        AlertDialog dialog=PlayerChoiceDialog.create(this,"视频解码",new String[]{"自动：硬解优先，失败回退","软件兼容：高分辨率可能较慢"},softwareVideo?1:0,(choice,index)->{if(softwareVideo!=(index==1)){stopPlayer();softwareVideo=index==1;startSource();}choice.dismiss();});
        dialog.setOnDismissListener(value->{video.setControllerShowTimeoutMs(5000);showControls();});dialog.show();
    }
    private void report(String event){
        if(source==null)return;long milliseconds=player==null?position:Math.max(0,player.getCurrentPosition());boolean paused=player==null||!player.isPlaying();long duration=player==null?source.optLong("RunTimeTicks"):player.getDuration()>0?player.getDuration()*10000:source.optLong("RunTimeTicks");
        try{JSONObject payload=new JSONObject().put("ItemId",itemId).put("UserId",api.session.userId()).put("MediaSourceId",source.optString("Id")).put("PlaySessionId",playSession).put("PositionTicks",milliseconds*10000).put("RunTimeTicks",duration).put("IsPaused",paused).put("CanSeek",true).put("PlayMethod","DirectStream").put("Client","ETKN TV");
            if(event.equals("Stopped")&&completedByCredits)payload.put("PlayedToCompletion",true);
            reports.execute(()->{try{api.post("/api/tv/Sessions/Playing"+(event.equals("Started")?"":"/"+event),payload);}catch(Exception ignored){}});
        }catch(Exception ignored){}
    }
    private void stopPlayer(){cancelConfirm();if(translation!=null){translation.close();translation=null;}cancelCreditsCountdown();creditsSkipCancelled=false;hideSeekProgress();timer.removeCallbacks(progress);timer.removeCallbacks(skipCheck);timer.removeCallbacks(videoCheck);if(skipButton!=null)skipButton.setVisibility(View.GONE);if(player!=null){position=Math.max(0,player.getCurrentPosition());playWhenReady=player.getPlayWhenReady();report("Stopped");video.setPlayer(null);player.release();player=null;TvPlayerFactory.clearAssView(video.getSubtitleView());}updatePlaybackOverlay();}
    @Override protected void onStart(){super.onStart();if(!LoginGate.isSatisfied()&&api.session.persist()!=SessionStore.PERSIST_FULL)return;foreground=true;if(info!=null&&player==null)startSource();}
    private void cancelConfirm(){timer.removeCallbacks(confirmLongPress);confirmPress=null;confirmLongPressed=false;}
    @Override protected void onPause(){cancelConfirm();cancelCreditsCountdown();hideSeekProgress();super.onPause();}
    @Override protected void onStop(){foreground=false;stopPlayer();super.onStop();}
    @Override protected void onDestroy(){timer.removeCallbacksAndMessages(null);reports.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putFloat("speed",playbackSpeed);state.putLong("position",player==null?position:player.getCurrentPosition());state.putString("sourceId",source==null?getIntent().getStringExtra("sourceId"):source.optString("Id"));state.putInt("audioIndex",audioIndex);state.putInt("subtitleIndex",subtitleIndex);state.putBoolean("softwareVideo",softwareVideo);state.putBoolean("softwareAudio",softwareAudio);state.putBoolean("enhancedSubtitles",enhancedSubtitles);state.putBoolean("playWhenReady",player==null?playWhenReady:player.getPlayWhenReady());super.onSaveInstanceState(state);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        if(down)focusNextWhenLoaded=false;
        if(key==KeyEvent.KEYCODE_BACK){cancelConfirm();if(down&&event.getRepeatCount()==0){
            if(episodePanel.isShown())hideEpisodes();
            else if(creditsCountdownStartedAt>=0){creditsSkipCancelled=true;cancelCreditsCountdown();}
            else if(seekKeys||controls.getVisibility()==View.VISIBLE||video.isControllerFullyVisible()){hideSeekProgress();video.hideController();controls.setVisibility(View.GONE);video.requestFocus();}
            else finish();
        }return true;}
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
