package xyz.etkn.tv;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public final class DetailActivity extends TvActivity {
    static final String ACTION_PLAYBACK_PROGRESS = "xyz.etkn.tv.PLAYBACK_PROGRESS";
    private static final int EPISODE_PICKER=1002;
    private JSONObject item,playable;
    private final List<JSONObject> allEpisodes=new ArrayList<>();
    private final Map<String,Button> episodeCells=new HashMap<>();
    private final Map<String,Boolean> episodeCellDates=new HashMap<>();
    private JSONArray sources=new JSONArray();
    private int sourceIndex,audio=PlaybackSelection.AUTO,subtitle=PlaybackSelection.AUTO;
    private Button playButton,versionButton,audioButton,subtitleButton,restartButton,favoriteButton,playedButton;
    private int stateTicket;
    private Button refreshButton,deleteButton;
    private boolean managementBusy;
    private AlertDialog managementDialog;
    private LinearLayout episodeArea,preview,castArea,chapterArea;
    private TextView episodeTitle,episodeOverview;
    private ImageView backdrop,episodeImage,detailLogo;
    private TextView detailTitle,detailDouban,detailTmdb,detailImdb,detailYear,detailDuration,detailRating,detailCategories,detailOverview,detailTagline;
    private Button cloudButton,episodesButton;
    private boolean episodePicker;
    private boolean canDiscover,cloudSearching;
    private JSONObject previewEpisode;
    private HomeUpdates artworkUpdates;
    private boolean artworkForeground,artworkLoading;
    private boolean playbackReturnPending,playbackReceiverRegistered,playbackReportConfirmed;
    private int playbackRefreshTicket;
    private final DetailChanges detailChanges=new DetailChanges();
    private final Set<String> artworkInFlight=new LinkedHashSet<>();
    private int artworkTicket;
    private final Map<String,JSONObject> visibleSeasons=new HashMap<>();
    private final Map<String,View> seasonCards=new HashMap<>();
    private final Map<String,ImageView> castImages=new HashMap<>(),chapterImages=new HashMap<>();
    private final android.os.Handler artworkTimer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable artworkRefresh=()->refreshArtwork();
    private final Runnable playbackRefreshFallback=()->{if(artworkForeground&&playbackReturnPending&&!playbackReportConfirmed)refreshPlaybackProgress();};
    private final BroadcastReceiver playbackProgressReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(!playbackReturnPending)return;
            String id=intent.getStringExtra("itemId");if(!isPlaybackItem(id))return;
            if(intent.getBooleanExtra("synced",false)){
                playbackReportConfirmed=true;if(artworkForeground)refreshPlaybackProgress();
            }else applyPlaybackPosition(id,intent.getLongExtra("positionTicks",0),intent.getLongExtra("runtimeTicks",0),intent.getBooleanExtra("completed",false));
        }
    };
    private ScrollView scroll;
    private GridLayout episodeGrid;
    private Button episodeEntry;
    private LinearLayout facetButtons,studioButtons;
    private Button seriesButton;
    private final List<JSONObject> itemCollections=new ArrayList<>();
    private String renderedFacets="";
    private HorizontalScrollView menu,castScroll;
    private View castEntry;
    private View previousFocus;
    private int mainMenuTransitionKey;

    private boolean inEpisodeMatrix(View view){while(view!=null){if(view==episodeGrid)return true;android.view.ViewParent parent=view.getParent();view=parent instanceof View?(View)parent:null;}return false;}
    private boolean inCast(View view){while(view!=null){if(view==castScroll)return true;android.view.ViewParent parent=view.getParent();view=parent instanceof View?(View)parent:null;}return false;}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(mainMenuTransitionKey!=0&&event.getKeyCode()==mainMenuTransitionKey){if(event.getAction()==KeyEvent.ACTION_UP)mainMenuTransitionKey=0;return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&menu.getVisibility()!=View.VISIBLE){View focused=getCurrentFocus();if(focused==null||focused.focusSearch(View.FOCUS_UP)==null){showMainMenu();mainMenuTransitionKey=event.getKeyCode();return true;}}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN&&menu.getVisibility()==View.VISIBLE){hideMainMenu();mainMenuTransitionKey=event.getKeyCode();return true;}
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP)){
            View current=getCurrentFocus();int direction=event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN?View.FOCUS_DOWN:View.FOCUS_UP;
            View next=current==null?null:current.focusSearch(direction);
            if(castEntry!=null&&!inCast(current)&&inCast(next)){castScroll.scrollTo(0,0);castEntry.requestFocus();return true;}
            if(episodeEntry!=null&&current!=null&&!inEpisodeMatrix(current)&&inEpisodeMatrix(next)){episodeEntry.requestFocus();return true;}
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        episodePicker=getIntent().getBooleanExtra("episodePicker",false);
        androidx.core.content.ContextCompat.registerReceiver(this,playbackProgressReceiver,new IntentFilter(ACTION_PLAYBACK_PROGRESS),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);playbackReceiverRegistered=true;
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(INK);
        backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(backdrop,0);
        stage.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        stage.addView(backdropShade(),new FrameLayout.LayoutParams(-1,-1));
        scroll=new FocusScroll.Vertical(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        content=column();content.setPadding(dp(42),dp(26),dp(42),dp(28));scroll.addView(content);stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        setupMainMenu(stage);
        status=text("正在读取详情…",13,MUTED);content.addView(status);setContentView(stage);
        async(()->api.get(api.userPath()+"/Items/"+EtknApi.encode(getIntent().getStringExtra("id"))),this::render);
    }

    private void setupMainMenu(FrameLayout stage){
        nav=row();nav.setGravity(Gravity.CENTER_VERTICAL);nav.setBaselineAligned(false);nav.setPadding(dp(12),dp(8),dp(12),dp(8));
        menu=new HorizontalScrollView(this);menu.setHorizontalScrollBarEnabled(false);menu.setClipChildren(false);menu.setClipToPadding(false);menu.setBackground(capsuleSurface(TvAppearance.surface(appearanceDark())));menu.addView(nav,new FrameLayout.LayoutParams(-2,-1));menu.setVisibility(View.GONE);
        FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(-2,dp(68),Gravity.TOP|Gravity.CENTER_HORIZONTAL);bounds.topMargin=dp(12);stage.addView(menu,bounds);refreshMainMenu();
    }
    private void refreshMainMenu(){
        if(menu==null)return;nav.removeAllViews();mainMenuItem("首页",()->openHomeMenu("home"));mainMenuItem("媒体库",()->openHomeMenu("library"));mainMenuItem("收藏",()->openHomeMenu("favorites"));mainMenuItem("搜索",()->openHomeMenu("search"));
        if(canDiscover)mainMenuItem("影视探索",()->open(DiscoveryActivity.class));mainMenuItem("设置",()->open(SettingsActivity.class));
    }
    private void mainMenuItem(String title,Runnable click){super.nav(title,()->{hideMainMenu();click.run();});}
    private void openHomeMenu(String target){startActivity(new Intent(this,HomeActivity.class).putExtra("mainMenuTarget",target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    private void showMainMenu(){previousFocus=getCurrentFocus();scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);animateMenuIn(menu);if(nav.getChildCount()>0)nav.getChildAt(0).requestFocus();}
    private void hideMainMenu(){menu.animate().cancel();menu.setTranslationY(-dp(84));menu.setVisibility(View.GONE);scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);if(previousFocus==null||!previousFocus.isShown()||!previousFocus.requestFocus())content.requestFocus(View.FOCUS_DOWN);}
    @Override public void onBackPressed(){if(menu!=null&&menu.getVisibility()==View.VISIBLE){hideMainMenu();return;}super.onBackPressed();}
    @Override void appearanceChanged(){if(menu!=null)menu.setBackground(capsuleSurface(TvAppearance.surface(appearanceDark())));}

    private void render(JSONObject result){
        item=result;content.removeAllViews();
        Images.backdrop(api,api.artwork(item,"Backdrop",1920),backdrop,ok->{});
        if(episodePicker){renderEpisodePicker();return;}
        FrameLayout identity=new FrameLayout(this);int titleWidth=Math.min(560,getResources().getConfiguration().screenWidthDp-84);
        TextView title=text(EtknApi.title(item),26,Color.WHITE);detailTitle=title;title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER_VERTICAL);title.setPadding(0,0,0,0);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setVisibility(View.INVISIBLE);
        identity.addView(title,new FrameLayout.LayoutParams(-1,-1));
        ImageView logo=new ImageView(this);detailLogo=logo;logo.setScaleType(ImageView.ScaleType.FIT_START);Images.rounded(logo,dp(4));logo.setVisibility(View.GONE);
        identity.addView(logo,new FrameLayout.LayoutParams(dp(Math.min(360,titleWidth)),dp(58),Gravity.CENTER_VERTICAL));
        content.addView(identity,new LinearLayout.LayoutParams(dp(titleWidth),dp(64)));
        Images.backdrop(api,api.artwork(item,"Logo",720),logo,ok->{logo.setVisibility(ok?View.VISIBLE:View.GONE);title.setVisibility(ok?View.GONE:View.VISIBLE);});
        LinearLayout metadata=row();metadata.setGravity(Gravity.CENTER_VERTICAL);
        ImageView douban=new ImageView(this);douban.setImageDrawable(doubanMark());douban.setPadding(dp(3),dp(3),dp(3),dp(3));douban.setBackground(shape(Color.rgb(0,119,34),0));metadata.addView(douban,new LinearLayout.LayoutParams(dp(21),dp(21)));
        detailDouban=meta("",Color.WHITE);metadata.addView(detailDouban);
        ImageView tmdb=new ImageView(this);tmdb.setImageDrawable(tmdbMark());metadata.addView(tmdb,new LinearLayout.LayoutParams(dp(21),dp(21)));
        detailTmdb=meta("",Color.rgb(255,205,75));metadata.addView(detailTmdb);
        ImageView imdb=new ImageView(this);imdb.setImageDrawable(imdbMark());imdb.setContentDescription("IMDb评分");metadata.addView(imdb,new LinearLayout.LayoutParams(dp(21),dp(21)));
        detailImdb=meta("",Color.WHITE);metadata.addView(detailImdb);
        detailYear=meta("",MUTED);metadata.addView(detailYear);detailDuration=meta("",MUTED);metadata.addView(detailDuration);
        detailRating=meta("",Color.WHITE);detailRating.setPadding(dp(7),dp(2),dp(7),dp(2));detailRating.setBackground(shape(Color.TRANSPARENT,MUTED));LinearLayout.LayoutParams ratingBounds=new LinearLayout.LayoutParams(-2,-2);ratingBounds.rightMargin=dp(12);metadata.addView(detailRating,ratingBounds);
        seriesButton=compactButton("系列",this::openItemCollection);seriesButton.setContentDescription("所属合集");seriesButton.setVisibility(View.GONE);
        facetButtons=row();facetButtons.setGravity(Gravity.CENTER_VERTICAL);metadata.addView(facetButtons);
        studioButtons=row();studioButtons.setGravity(Gravity.CENTER_VERTICAL);metadata.addView(studioButtons);
        HorizontalScrollView metadataScroll=new FocusScroll.Row(this);metadataScroll.setHorizontalScrollBarEnabled(false);metadataScroll.addView(metadata);content.addView(metadataScroll,new LinearLayout.LayoutParams(-1,dp(36)));
        detailCategories=text("",14,MUTED);content.addView(detailCategories);
        LinearLayout actions=row();
        playButton=actionButton("play","播放",()->play(false));playButton.setId(View.generateViewId());playButton.setEnabled(false);actions.addView(playButton);
        restartButton=actionButton("restart","从头播放",()->play(true));restartButton.setVisibility(View.GONE);actions.addView(restartButton);
        String type=item.optString("Type");boolean episodic=type.equals("Series")||type.equals("Season")||type.equals("Episode");
        episodesButton=actionButton("episodes","选集",this::openEpisodePicker);episodesButton.setVisibility(episodic?View.VISIBLE:View.GONE);episodesButton.setEnabled(false);actions.addView(episodesButton);
        favoriteButton=actionButton("favorite","收藏",this::toggleFavorite);actions.addView(favoriteButton);
        playedButton=actionButton("played","标记已播放",this::togglePlayed);actions.addView(playedButton);refreshStateButtons();
        refreshButton=actionButton("refresh","刷新元数据",this::confirmRefresh);deleteButton=actionButton("delete","删除",this::confirmDelete);
        refreshButton.setVisibility(View.GONE);deleteButton.setVisibility(View.GONE);actions.addView(refreshButton);actions.addView(deleteButton);
        cloudButton=actionButton("search","搜索云资源",this::searchCloudResources);cloudButton.setVisibility(View.GONE);actions.addView(cloudButton);updateDiscoveryPermission();
        HorizontalScrollView actionScroll=new FocusScroll.Row(this);actionScroll.setHorizontalScrollBarEnabled(false);actionScroll.addView(actions);content.addView(actionScroll);
        if(item.optString("Id").startsWith("item-"))async(()->api.get("/api/tv/Users/Me"),user->{
            JSONObject policy=user.optJSONObject("Policy"),permissions=user.optJSONObject("TvPermissions");
            boolean admin=policy!=null&&policy.optBoolean("IsAdministrator");
            boolean refresh=admin||(permissions!=null&&permissions.optBoolean("CanRefreshMetadata"));
            boolean delete=admin||(permissions!=null&&permissions.optBoolean("CanDeleteMedia"));
            refreshButton.setVisibility(refresh?View.VISIBLE:View.GONE);deleteButton.setVisibility(delete?View.VISIBLE:View.GONE);
        });
        LinearLayout selections=row();selections.setPadding(0,dp(2),0,dp(2));versionButton=compactButton("版本",this::chooseVersion);audioButton=compactButton("音轨",()->chooseStream(false));subtitleButton=compactButton("字幕",()->chooseStream(true));
        selections.addView(versionButton);selections.addView(audioButton);selections.addView(subtitleButton);
        for(Button choice:new Button[]{versionButton,audioButton,subtitleButton})choice.setBackground(selectionBackground());
        HorizontalScrollView selectionScroll=new FocusScroll.Row(this);selectionScroll.setHorizontalScrollBarEnabled(false);selectionScroll.addView(selections);content.addView(selectionScroll);
        detailTagline=text("",16,ACCENT);detailTagline.setTypeface(null,Typeface.BOLD);detailTagline.setMaxLines(1);detailTagline.setEllipsize(android.text.TextUtils.TruncateAt.END);detailTagline.setPadding(0,dp(3),0,dp(3));content.addView(detailTagline);
        TextView overview=text("",15,Color.rgb(219,227,237));detailOverview=overview;overview.setLines(3);overview.setEllipsize(android.text.TextUtils.TruncateAt.END);overview.setFocusableInTouchMode(false);overview.setPadding(dp(10),dp(5),dp(10),dp(5));overview.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(EtknApi.title(item)).setMessage(item.optString("Overview")).setPositiveButton("关闭",null).show());
        content.addView(overview,new LinearLayout.LayoutParams(-1,-2));
        updateMetadata();
        renderCast();
        renderChapters();
        status=text("",12,MUTED);content.addView(status);episodeArea=column();updateChoices();
        if(episodic)loadEpisodes();else{setPlayable(item);focusPlay();}
        renderCollections();
        renderSimilar();
    }
    private TextView meta(String value,int color){TextView label=text(value,13,color);label.setGravity(Gravity.CENTER_VERTICAL);return label;}
    private void updateMetadata(){
        if(episodePicker){detailTitle.setText(EtknApi.title(item)+" · 选集");return;}
        detailTitle.setText(EtknApi.title(item));detailDouban.setText(" "+DetailPresentation.rating(item,"DoubanRating")+"    ");detailTmdb.setText(" "+DetailPresentation.rating(item,"TMDbRating")+"    ");detailImdb.setText(" "+DetailPresentation.rating(item,"IMDbRating")+"    ");
        String year=item.optString("ProductionYear"),duration=DetailPresentation.duration(item.optLong("RunTimeTicks")),rating=DetailPresentation.contentRating(item),categories=DetailPresentation.categories(item);
        detailYear.setText(year+"    ");detailYear.setVisibility(year.isEmpty()?View.GONE:View.VISIBLE);detailDuration.setText(duration+"    ");detailDuration.setVisibility(duration.isEmpty()?View.GONE:View.VISIBLE);
        detailRating.setText(rating);detailRating.setVisibility(rating.isEmpty()?View.GONE:View.VISIBLE);detailCategories.setVisibility(View.GONE);renderFacets();
        boolean hasOverview=!item.optString("Overview").trim().isEmpty();detailOverview.setText(hasOverview?item.optString("Overview"):"暂无简介");
        if(detailOverview.isFocusable()!=hasOverview||detailOverview.getBackground()==null){if(!hasOverview&&detailOverview.hasFocus())playButton.requestFocus();detailOverview.setFocusable(hasOverview);detailOverview.setBackground(hasOverview?transparentFocusBackground():shape(Color.TRANSPARENT,0));}
        detailOverview.setClickable(hasOverview);
        String tagline=item.optString("Tagline").trim();if(tagline.isEmpty()){JSONArray lines=item.optJSONArray("Taglines");if(lines!=null)tagline=lines.optString(0).trim();}detailTagline.setText(tagline);detailTagline.setVisibility(tagline.isEmpty()?View.GONE:View.VISIBLE);
    }
    private void renderFacets(){
        String identity=String.valueOf(item.optJSONArray("Genres"))+String.valueOf(item.optJSONArray("Tags"))+String.valueOf(item.optJSONArray("Networks"))+String.valueOf(item.optJSONArray("Companies"));if(identity.equals(renderedFacets))return;renderedFacets=identity;facetButtons.removeAllViews();
        for(LibraryFilters.Entry entry:LibraryFilters.mergeTypes(item)){Button choice=compactButton(entry.label,()->startActivity(new Intent(this,HomeActivity.class).putExtra("facetKind",entry.kind).putExtra("facetValue",entry.label)));choice.setContentDescription((entry.kind.equals("Genres")?"题材 · ":"标签 · ")+entry.label);facetButtons.addView(choice);}
        facetButtons.addView(seriesButton);
        studioButtons.removeAllViews();
        for(String kind:new String[]{"Networks","Companies"}){
            JSONArray labels=item.optJSONArray(kind);if(labels==null||labels.length()==0)continue;
            for(int i=0;i<labels.length();i++){
                String label=labels.optString(i);if(label.isEmpty())continue;
                Button choice=compactButton(label,()->startActivity(new Intent(this,HomeActivity.class).putExtra("facetKind",kind).putExtra("facetValue",label)));choice.setContentDescription(label);
                FrameLayout tile=new FrameLayout(this);LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-2,dp(28));bounds.setMargins(0,0,dp(8),0);studioButtons.addView(tile,bounds);tile.addView(choice,new FrameLayout.LayoutParams(-2,-1));
            }
        }
    }
    private void renderCollections(){
        if(!"Movie".equals(item.optString("Type")))return;
        async(()->api.get(api.userPath()+"/Items/"+EtknApi.encode(item.optString("Id"))+"/Collections?Limit=60"),result->{
            itemCollections.clear();itemCollections.addAll(EtknApi.rows(result));if(itemCollections.isEmpty()&&seriesButton.hasFocus())focusPlay();seriesButton.setVisibility(itemCollections.isEmpty()?View.GONE:View.VISIBLE);
        });
    }
    private void openItemCollection(){
        List<JSONObject> choices=new ArrayList<>(itemCollections);if(choices.isEmpty())return;
        if(choices.size()==1){openCollection(choices.get(0));return;}
        String[] names=new String[choices.size()];for(int i=0;i<names.length;i++)names[i]=choices.get(i).optString("Name");
        PlayerChoiceDialog.create(this,"所属合集",names,-1,(dialog,index)->{dialog.dismiss();openCollection(choices.get(index));},320).show();
    }
    private void openCollection(JSONObject collection){startActivity(new Intent(this,HomeActivity.class).putExtra("collection",collection.toString()));}
    private void openEpisodePicker(){
        if(playable==null)return;
        playbackReturnPending=true;playbackReportConfirmed=true;
        Intent intent=new Intent(this,DetailActivity.class).putExtra("id",item.optString("Id")).putExtra("episodePicker",true).putExtra("episodeId",playable.optString("Id")).putExtra("audioIndex",audio).putExtra("subtitleIndex",subtitle);
        if(selectedSource()!=null)intent.putExtra("sourceId",selectedSource().optString("Id"));startActivityForResult(intent,EPISODE_PICKER);
    }
    private void renderEpisodePicker(){
        detailTitle=text(EtknApi.title(item)+" · 选集",22,Color.WHITE);detailTitle.setMaxLines(1);detailTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);content.addView(detailTitle);
        LinearLayout actions=row();actions.addView(compactButton("返回详情",this::finish));
        playButton=actionButton("play","播放",()->play(false));playButton.setEnabled(false);actions.addView(playButton);restartButton=actionButton("restart","从头播放",()->play(true));restartButton.setVisibility(View.GONE);actions.addView(restartButton);
        versionButton=compactButton("版本",this::chooseVersion);audioButton=compactButton("音轨",()->chooseStream(false));subtitleButton=compactButton("字幕",()->chooseStream(true));actions.addView(versionButton);actions.addView(audioButton);actions.addView(subtitleButton);actions.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView choices=new FocusScroll.Row(this);choices.setHorizontalScrollBarEnabled(false);choices.addView(actions);content.addView(choices);
        status=text("",12,MUTED);content.addView(status);episodeArea=column();content.addView(episodeArea);updateChoices();loadEpisodes();updateDiscoveryPermission();
    }
    @Override public void finish(){
        if(episodePicker&&playable!=null){Intent result=new Intent().putExtra("episodeId",playable.optString("Id")).putExtra("audioIndex",audio).putExtra("subtitleIndex",subtitle);if(selectedSource()!=null)result.putExtra("sourceId",selectedSource().optString("Id"));setResult(RESULT_OK,result);}
        super.finish();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(requestCode!=EPISODE_PICKER||resultCode!=RESULT_OK||data==null)return;
        for(JSONObject episode:allEpisodes)if(episode.optString("Id").equals(data.getStringExtra("episodeId"))){setPlayable(episode);int index=PlaybackSelection.sourceIndex(sources,data.getStringExtra("sourceId"));if(index>=0)sourceIndex=index;audio=data.getIntExtra("audioIndex",PlaybackSelection.AUTO);subtitle=data.getIntExtra("subtitleIndex",PlaybackSelection.AUTO);updateChoices();break;}
    }
    private void copyPresentation(JSONObject target,JSONObject value){
        for(String key:new String[]{"Name","Overview","Tagline","Taglines","ProductionYear","RunTimeTicks","OfficialRating","CustomRating","DoubanRating","TMDbRating","IMDbRating","Genres","Tags","Networks","Companies","StudioImageTags","ImageTags","BackdropImageTags","BackdropImageTag","ParentBackdropItemId","ParentBackdropImageTags","ParentLogoItemId","ParentLogoImageTag","PremiereDate","IsMissing","IsUnaired","IsVirtualItem","LocationType","People","Chapters"})try{target.put(key,value.opt(key));}catch(org.json.JSONException ignored){}
        for(String key:new String[]{"IndexNumber","ParentIndexNumber","SeriesId","SeriesName","SeasonId","ParentId"})if(value.has(key))try{target.put(key,value.opt(key));}catch(org.json.JSONException ignored){}
    }
    private Button compactButton(String title,Runnable click){
        Button choice=button(title,click);choice.setBackground(selectionBackground());choice.setTextSize(12);choice.setPadding(dp(10),0,dp(10),0);choice.setMinWidth(0);choice.setMinimumWidth(0);choice.setMinHeight(0);choice.setMinimumHeight(0);
        LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-2,dp(32));bounds.rightMargin=dp(8);choice.setLayoutParams(bounds);return choice;
    }
    private Button actionButton(String icon,String label,Runnable click){
        Button button=button("",click);button.setBackground(selectionBackground());button.setPadding(dp(12),dp(8),dp(12),dp(8));button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);
        LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(dp(48),dp(40));bounds.setMargins(0,dp(6),dp(10),dp(2));button.setLayoutParams(bounds);
        actionIcon(button,icon,label,false);return button;
    }
    private void actionIcon(Button button,String icon,String label,boolean active){
        MediaActionIcon drawable=new MediaActionIcon(icon,active);drawable.setBounds(0,0,dp(24),dp(24));button.setCompoundDrawables(drawable,null,null,null);button.setContentDescription(label);
    }
    private void updatePlayAction(){boolean resume=DetailPresentation.position(playable)>0;actionIcon(playButton,resume?"resume":"play",resume?"继续播放":"播放",false);restartButton.setVisibility(resume?View.VISIBLE:View.GONE);}
    private void focusPlay(){playButton.post(()->{playButton.requestFocus();scroll.scrollTo(0,0);});}
    private JSONObject userData(){JSONObject data=item==null?null:item.optJSONObject("UserData");return data==null?new JSONObject():data;}
    private void refreshStateButtons(){
        if(favoriteButton==null||playedButton==null)return;
        JSONObject data=userData();
        actionIcon(favoriteButton,"favorite",data.optBoolean("IsFavorite")?"取消收藏":"收藏",data.optBoolean("IsFavorite"));
        actionIcon(playedButton,"played",data.optBoolean("Played")?"标记未播放":"标记已播放",data.optBoolean("Played"));
    }
    private void mergeUserData(JSONObject target,JSONObject result){
        if(target==null||result==null)return;
        JSONObject data=target.optJSONObject("UserData");
        if(data==null){data=new JSONObject();try{target.put("UserData",data);}catch(Exception ignored){}}
        for(String key:new String[]{"PlaybackPositionTicks","PlayedPercentage","IsResumable","PlayCount","IsFavorite","Played","LastPlayedDate"}){try{if(result.has(key))data.put(key,result.get(key));}catch(Exception ignored){}}
    }
    private void applyUserData(JSONObject result){
        mergeUserData(item,result);
        if(playable!=null&&playable.optString("Id").equals(item.optString("Id")))mergeUserData(playable,result);
        if(playable!=null)updatePlayAction();
    }
    private boolean isPlaybackItem(String id){
        if(id==null||item==null)return false;if(id.equals(item.optString("Id")))return true;
        for(JSONObject episode:allEpisodes)if(id.equals(episode.optString("Id")))return true;return false;
    }
    private void applyPlaybackPosition(String id,long ticks,long runtime,boolean completed){
        ++playbackRefreshTicket;
        try{
            JSONObject data=new JSONObject().put("PlaybackPositionTicks",completed?0:Math.max(0,ticks));if(completed)data.put("Played",true);
            if(id.equals(item.optString("Id")))applyUserData(data);
            for(JSONObject episode:allEpisodes)if(id.equals(episode.optString("Id"))){mergeUserData(episode,data);if(runtime>0)episode.put("RunTimeTicks",runtime);setPlayable(episode);if(preview!=null)updatePreview(episode);break;}
            refreshPlaybackViews();
        }catch(org.json.JSONException ignored){}
    }
    private void refreshPlaybackProgress(){
        if(!artworkForeground||item==null)return;
        int ticket=++playbackRefreshTicket;boolean confirmed=playbackReportConfirmed;String id=item.optString("Id"),type=item.optString("Type"),series=type.equals("Series")?id:item.optString("SeriesId",item.optString("ParentId"));
        boolean episodes=type.equals("Series")||type.equals("Season")||type.equals("Episode");
        async(()->{
            try{JSONObject result=new JSONObject().put("item",api.get(api.userPath()+"/Items/"+EtknApi.encode(id)));if(episodes&&!series.isEmpty())result.put("episodes",api.get("/api/tv/Shows/"+EtknApi.encode(series)+"/Episodes?UserId="+EtknApi.encode(api.session.userId())+"&Fields=MediaSources,MediaStreams,Overview"));return result;}
            catch(EtknApi.AuthException error){throw error;}catch(Exception ignored){return null;}
        },result->{
            if(!artworkForeground||ticket!=playbackRefreshTicket||result==null)return;
            JSONObject current=result.optJSONObject("item");if(current!=null){if(confirmed)applyUserData(current.optJSONObject("UserData"));mergeSources(current);}
            JSONObject episodesResult=result.optJSONObject("episodes");if(episodesResult!=null)for(JSONObject updated:EtknApi.rows(episodesResult))for(JSONObject episode:allEpisodes)if(updated.optString("Id").equals(episode.optString("Id"))){if(confirmed)mergeUserData(episode,updated.optJSONObject("UserData"));mergeSources(updated);break;}
            refreshPlaybackViews();if(confirmed)playbackReturnPending=false;
        });
    }
    private void refreshPlaybackViews(){
        refreshStateButtons();if(playable!=null)updatePlayAction();
        for(JSONObject episode:allEpisodes){Button cell=episodeCells.get(episode.optString("Id"));if(cell!=null)updateEpisodeCell(cell,episode,Boolean.TRUE.equals(episodeCellDates.get(episode.optString("Id"))));}
    }
    private void startPlayback(Intent intent){playbackReturnPending=true;playbackReportConfirmed=false;++playbackRefreshTicket;startActivity(intent);}
    private void toggleFavorite(){
        if(item==null)return;
        boolean current=userData().optBoolean("IsFavorite");
        String path=api.userPath()+"/FavoriteItems/"+EtknApi.encode(item.optString("Id"));
        int ticket=++stateTicket;
        async(()->current?api.delete(path):api.post(path,new JSONObject()),result->{if(ticket!=stateTicket)return;applyUserData(result);refreshStateButtons();});
    }
    private void togglePlayed(){
        if(item==null)return;
        boolean current=userData().optBoolean("Played");
        String path=api.userPath()+"/PlayedItems/"+EtknApi.encode(item.optString("Id"));
        int ticket=++stateTicket;
        async(()->current?api.delete(path):api.post(path,new JSONObject()),result->{
            if(ticket!=stateTicket)return;
            String type=item.optString("Type");
            if(type.equals("Series")||type.equals("Season")){mergeUserData(item,result);refreshStateButtons();loadEpisodes();return;}
            applyUserData(result);refreshStateButtons();
            for(JSONObject episode:allEpisodes)if(item.optString("Id").equals(episode.optString("Id"))){mergeUserData(episode,result);break;}
            if(episodeGrid!=null&&playable!=null)showSeason(playable.optString("SeasonId"),playable);
        });
    }
    private void confirmRefresh(){
        if(managementBusy||!refreshButton.isShown())return;
        managementDialog=MediaActions.refresh(this,this::submitManagement);
    }
    private void updateDiscoveryPermission(){
        async(()->api.get("/api/tv/auth/check_status"),result->{JSONObject user=result.optJSONObject("user");boolean allowed=user!=null&&user.optBoolean("can_access_discovery");if(canDiscover!=allowed){canDiscover=allowed;refreshMainMenu();}if(cloudButton!=null)cloudButton.setVisibility(canDiscover?View.VISIBLE:View.GONE);});
    }
    private void searchCloudResources(){
        if(!canDiscover||cloudSearching)return;
        cloudSearching=true;String type=item.optString("Type");int season=type.equals("Season")?item.optInt("IndexNumber",-1):type.equals("Episode")?item.optInt("ParentIndexNumber",-1):-1;
        async(()->{try{
            JSONObject root=item;if(type.equals("Season")||type.equals("Episode")){String series=item.optString("SeriesId");if(series.isEmpty())throw new Exception("当前媒体缺少剧集身份");root=api.get(api.userPath()+"/Items/"+EtknApi.encode(series));}
            JSONObject providers=root.optJSONObject("ProviderIds");String tmdb=providers==null?"":providers.optString("Tmdb");if(!tmdb.matches("[1-9][0-9]*"))throw new Exception("当前媒体缺少 TMDb 身份，无法搜索云资源");
            return new JSONObject().put("id",tmdb).put("title",EtknApi.title(root)).put("media_type",root.optString("Type").equals("Movie")?"movie":"tv");
        }finally{runOnUiThread(()->cloudSearching=false);}},target->startActivity(new Intent(this,DiscoveryActivity.class).putExtra("cloudItem",target.toString()).putExtra("cloudSeason",season)));
    }
    private void confirmDelete(){
        if(managementBusy||!deleteButton.isShown())return;
        managementDialog=MediaActions.delete(this,item,()->submitManagement("delete"));
    }
    private void submitManagement(String action){
        if(managementBusy)return;
        boolean deleting=action.equals("delete");String path="/api/tv/Items/"+EtknApi.encode(item.optString("Id"));
        managementBusy=true;refreshButton.setEnabled(false);deleteButton.setEnabled(false);status.setText(deleting?"正在提交删除…":"正在提交元数据刷新…");
        async(()->{try{return deleting?api.delete(path):api.post(path+"/Refresh?Action="+action,new JSONObject());}
            finally{runOnUiThread(()->{managementBusy=false;refreshButton.setEnabled(true);deleteButton.setEnabled(true);});}},result->{
            if(deleting){Toast.makeText(this,"删除任务已提交",Toast.LENGTH_SHORT).show();setResult(RESULT_OK);finish();}
            else{refreshSubmitted(item.optString("Id"),result);focusPlay();}
        });
    }
    @Override protected void onDestroy(){if(playbackReceiverRegistered)unregisterReceiver(playbackProgressReceiver);if(managementDialog!=null)managementDialog.dismiss();super.onDestroy();}
    private void setPlayable(JSONObject value){
        boolean changed=playable==null||!playable.optString("Id").equals(value.optString("Id"));playable=value;
        sources=value.optJSONArray("MediaSources");if(sources==null)sources=new JSONArray();
        if(changed){sourceIndex=0;audio=subtitle=PlaybackSelection.AUTO;}
        playButton.setEnabled(true);updatePlayAction();updateChoices();
    }
    private JSONObject selectedSource(){return sources.optJSONObject(sourceIndex);}
    private void mergeSources(JSONObject value){
        JSONArray updated=value.optJSONArray("MediaSources");if(updated==null)return;
        String id=value.optString("Id");
        for(JSONObject episode:allEpisodes)if(id.equals(episode.optString("Id")))try{episode.put("MediaSources",updated);}catch(Exception ignored){}
        if(playable==null||!id.equals(playable.optString("Id"))||sources.toString().equals(updated.toString()))return;
        String selected=selectedSource()==null?"":selectedSource().optString("Id");int index=PlaybackSelection.sourceIndex(updated,selected);
        boolean retained=index>=0;sourceIndex=retained?index:0;sources=updated;
        try{playable.put("MediaSources",updated);}catch(Exception ignored){}
        if(!retained)audio=subtitle=PlaybackSelection.AUTO;
        updateChoices();
    }
    @Override protected void onResume(){
        super.onResume();if(isFinishing())return;artworkForeground=true;
        if(playbackReturnPending){if(playbackReportConfirmed)refreshPlaybackProgress();else artworkTimer.postDelayed(playbackRefreshFallback,2500);}
        if(cloudButton!=null)updateDiscoveryPermission();
        if(!PlaybackSelection.seasonKey(playable).isEmpty()){audio=PlaybackSelection.AUTO;updateChoices();}
        artworkUpdates=HomeUpdates.detail(api,getIntent().getStringExtra("id"),value->runOnUiThread(()->{
            if(!artworkForeground)return;
            JSONArray removed=value.optJSONArray("Removed");if(removed!=null)for(int i=0;i<removed.length();i++)if(getIntent().getStringExtra("id").equals(removed.optString(i))){finish();return;}
            detailChanges.accept(value);artworkTimer.removeCallbacks(artworkRefresh);artworkTimer.postDelayed(artworkRefresh,250);
        }),()->runOnUiThread(()->{if(artworkForeground){api.session.clear();startActivity(new Intent(this,SetupActivity.class));finish();}}));
    }
    @Override protected void onPause(){artworkForeground=false;++artworkTicket;artworkLoading=false;detailChanges.updated.addAll(artworkInFlight);artworkInFlight.clear();artworkTimer.removeCallbacksAndMessages(null);if(artworkUpdates!=null){artworkUpdates.close();artworkUpdates=null;}super.onPause();}
    private void refreshArtwork(){
        if(!artworkForeground||item==null||artworkLoading)return;
        if(detailChanges.structure){detailChanges.structure=false;if(episodeArea!=null)loadEpisodes(true);renderCollections();}
        Set<String> ids=new LinkedHashSet<>(detailChanges.updated);detailChanges.updated.clear();
        String root=item.optString("Id");
        for(String parent:new String[]{item.optString("SeriesId"),item.optString("ParentId"),item.optString("ParentBackdropItemId"),item.optString("ParentLogoItemId")})if(ids.contains(parent))ids.add(root);
        ids.removeIf(id->!id.equals(root)&&!visibleSeasons.containsKey(id)&&allEpisodes.stream().noneMatch(episode->id.equals(episode.optString("Id"))));
        if(ids.isEmpty())return;
        artworkLoading=true;artworkInFlight.addAll(ids);int ticket=++artworkTicket;
        async(()->{JSONObject values=new JSONObject();for(String id:ids)try{values.put(id,api.get(api.userPath()+"/Items/"+EtknApi.encode(id)));}catch(EtknApi.AuthException error){throw error;}catch(Exception ignored){}return values;},values->{
            if(ticket!=artworkTicket||!artworkForeground)return;artworkLoading=false;artworkInFlight.clear();
            for(String id:ids){JSONObject value=values.optJSONObject(id);if(value==null){detailChanges.updated.add(id);continue;}applyDetail(value);}
            if(!detailChanges.updated.isEmpty()){artworkTimer.removeCallbacks(artworkRefresh);artworkTimer.postDelayed(artworkRefresh,3000);}
        });
    }
    private void applyDetail(JSONObject value){
        String id=value.optString("Id");
        if(id.equals(item.optString("Id"))){
            String oldBackground=api.artwork(item,"Backdrop",1920),oldLogo=api.artwork(item,"Logo",720);
            updateDependentImages(item,value);copyPresentation(item,value);mergeSources(value);
            if(!playbackReturnPending){applyUserData(value.optJSONObject("UserData"));refreshStateButtons();}updateMetadata();
            if(!oldBackground.equals(api.artwork(item,"Backdrop",1920)))Images.backdrop(api,api.artwork(item,"Backdrop",1920),backdrop,ok->{});
            if(!episodePicker&&!oldLogo.equals(api.artwork(item,"Logo",720)))Images.backdrop(api,api.artwork(item,"Logo",720),detailLogo,ok->{detailLogo.setVisibility(ok?View.VISIBLE:View.GONE);detailTitle.setVisibility(ok?View.GONE:View.VISIBLE);});
            if("Movie".equals(item.optString("Type")))renderCollections();
        }
        JSONObject season=visibleSeasons.get(id);View card=seasonCards.get(id);
        if(season!=null&&card instanceof ViewGroup){if(!api.image(season).equals(api.image(value)))Images.visible(api,api.image(value),(ImageView)((ViewGroup)card).getChildAt(0),ok->{});copyPresentation(season,value);((Button)((ViewGroup)card).getChildAt(2)).setText(EtknApi.title(season));}
        for(JSONObject episode:allEpisodes)if(id.equals(episode.optString("Id"))){
            String oldImage=api.artwork(episode,"Primary",640);copyPresentation(episode,value);mergeSources(value);if(!playbackReturnPending)mergeUserData(episode,value.optJSONObject("UserData"));
            Button cell=episodeCells.get(id);if(cell!=null)updateEpisodeCell(cell,episode,Boolean.TRUE.equals(episodeCellDates.get(id)));
            if(previewEpisode!=null&&id.equals(previewEpisode.optString("Id"))){copyPresentation(previewEpisode,value);updatePreviewText(previewEpisode);if(!oldImage.equals(api.artwork(value,"Primary",640)))Images.load(api,api.artwork(value,"Primary",640),episodeImage);}
            if(playable!=null&&id.equals(playable.optString("Id")))updatePlayAction();break;
        }
    }
    private void updateDependentImages(JSONObject before,JSONObject after){
        JSONArray people=after.optJSONArray("People"),oldPeople=before.optJSONArray("People");
        if(!castIds(people).equals(castIds(oldPeople)))renderCast(after);
        else if(people!=null)for(int i=0;i<people.length();i++){JSONObject person=people.optJSONObject(i);if(person==null)continue;ImageView view=castImages.get(person.optString("Id"));if(view==null)continue;JSONObject old=null;if(oldPeople!=null)for(int j=0;j<oldPeople.length();j++){JSONObject candidate=oldPeople.optJSONObject(j);if(candidate!=null&&person.optString("Id").equals(candidate.optString("Id")))old=candidate;}if(old==null||!api.artwork(old,"Primary",240).equals(api.artwork(person,"Primary",240)))Images.load(api,api.artwork(person,"Primary",240),view);
            ViewGroup card=(ViewGroup)view.getParent();((TextView)card.getChildAt(1)).setText(person.optString("Name"));((TextView)card.getChildAt(2)).setText(person.optString("Role"));view.setContentDescription("演员 · "+person.optString("Name"));view.setOnClickListener(v->startActivity(new Intent(this,PersonActivity.class).putExtra("id",person.optString("Id")).putExtra("name",person.optString("Name"))));}
        JSONArray chapters=after.optJSONArray("Chapters"),oldChapters=before.optJSONArray("Chapters");
        if(!chapterIds(chapters).equals(chapterIds(oldChapters)))renderChapters(after);
        else if(chapters!=null)for(int i=0;i<chapters.length();i++){JSONObject chapter=chapters.optJSONObject(i);if(chapter==null)continue;String key=String.valueOf(chapter.optInt("ChapterIndex",i));ImageView view=chapterImages.get(key);JSONObject old=oldChapters==null?null:oldChapters.optJSONObject(i);if(view==null)continue;if(old==null||!api.chapterArtwork(before,old,480).equals(api.chapterArtwork(after,chapter,480)))Images.load(api,api.chapterArtwork(after,chapter,480),view);
            ViewGroup card=(ViewGroup)view.getParent();String name=chapter.optString("Name");if(name.isEmpty())name="章节 "+(i+1);((TextView)card.getChildAt(1)).setText(name+"  ·  "+chapterTime(chapter.optLong("StartPositionTicks")));card.setContentDescription(name);card.setOnClickListener(v->playChapter(chapter));}
    }
    private List<String> castIds(JSONArray people){List<String> ids=new ArrayList<>();if(people!=null)for(int i=0;i<people.length();i++){JSONObject person=people.optJSONObject(i);if(person!=null&&person.optString("Type",person.optString("PersonType")).equalsIgnoreCase("Actor"))ids.add(person.optString("Id"));}return ids;}
    private List<String> chapterIds(JSONArray chapters){List<String> ids=new ArrayList<>();if(chapters!=null)for(int i=0;i<chapters.length();i++){JSONObject chapter=chapters.optJSONObject(i);if(chapter!=null&&(!chapter.optString("ImageTag").isEmpty()||!chapter.optString("ImagePath").isEmpty()))ids.add(String.valueOf(chapter.optInt("ChapterIndex",i)));}return ids;}
    @Override void metadataRefreshed(){super.metadataRefreshed();if(item!=null)detailChanges.updated.add(item.optString("Id"));refreshArtwork();focusPlay();}
    private void updateChoices(){
        int locked=new TvPreferences(this,api).seasonAudio(PlaybackSelection.seasonKey(playable));
        if(locked>=0)audio=PlaybackSelection.audioAt(selectedSource(),locked);
        versionButton.setVisibility(sources.length()>1?View.VISIBLE:View.GONE);versionButton.setText("版本 · "+PlaybackSelection.slotLabel(selectedSource()));
        audioButton.setEnabled(playable!=null);subtitleButton.setEnabled(playable!=null);
        audioButton.setText("音轨 · "+PlaybackSelection.selectionLabel(selectedSource(),audio,false)+(locked>=0?" · 本季第 "+(locked+1)+" 音轨":""));subtitleButton.setText("字幕 · "+PlaybackSelection.selectionLabel(selectedSource(),subtitle,true));
    }
    private void chooseVersion(){
        JSONArray choices=sources;String[] labels=new String[choices.length()];for(int i=0;i<labels.length;i++)labels[i]=PlaybackSelection.sourceLabel(choices.optJSONObject(i),i);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("版本").setSingleChoiceItems(labels,sourceIndex,(choice,index)->{int selected=PlaybackSelection.sourceIndex(sources,choices.optJSONObject(index).optString("Id"));if(selected>=0){sourceIndex=selected;audio=subtitle=PlaybackSelection.AUTO;updateChoices();}else Toast.makeText(this,"版本已变化，请重新选择",Toast.LENGTH_SHORT).show();choice.dismiss();}).setNegativeButton("取消",null).show();
        dialog.getWindow().setBackgroundDrawable(shape(OVERLAY,0));dialog.getWindow().setDimAmount(0.15f);
    }
    private void chooseStream(boolean subtitles){
        List<JSONObject> streams=PlaybackSelection.streams(selectedSource(),subtitles);List<String> labels=new ArrayList<>();List<Integer> indices=new ArrayList<>();labels.add((subtitles?"":PlaybackSelection.seasonKey(playable).isEmpty()?"自动选择 · ":"自动选择（解除本季锁定） · ")+PlaybackSelection.selectionLabel(selectedSource(),PlaybackSelection.AUTO,subtitles,!subtitles));indices.add(PlaybackSelection.AUTO);
        if(subtitles){labels.add("关闭字幕");indices.add(-1);}
        for(int i=0;i<streams.size();i++){JSONObject stream=streams.get(i);labels.add((subtitles?"":"第 "+(i+1)+" 音轨 · ")+PlaybackSelection.streamLabel(stream,!subtitles));indices.add(stream.optInt("Index"));}
        int current=indices.indexOf(subtitles?subtitle:audio);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(subtitles?"字幕":"音轨").setSingleChoiceItems(labels.toArray(new String[0]),current,(choice,index)->{if(subtitles)subtitle=indices.get(index);else{audio=indices.get(index);new TvPreferences(this,api).saveSeasonAudio(PlaybackSelection.seasonKey(playable),PlaybackSelection.audioOrdinal(selectedSource(),audio));}updateChoices();choice.dismiss();}).setNegativeButton("取消",null).create();
        dialog.show();dialog.getWindow().setBackgroundDrawable(shape(OVERLAY,0));dialog.getWindow().setDimAmount(0.15f);
    }
    private void loadEpisodes(){loadEpisodes(false);}
    private void loadEpisodes(boolean preserve){
        String previousEpisode=preserve&&playable!=null?playable.optString("Id"):"",previousSource=preserve&&selectedSource()!=null?selectedSource().optString("Id"):"";
        int previousAudio=audio,previousSubtitle=subtitle,previousY=scroll.getScrollY();View focused=getCurrentFocus();boolean matrixFocused=inEpisodeMatrix(focused);
        String series=item.optString("Type").equals("Series")?item.optString("Id"):item.optString("SeriesId",item.optString("ParentId"));status.setText("正在读取分集…");
        async(()->new JSONObject().put("seasons",api.get("/api/tv/Shows/"+EtknApi.encode(series)+"/Seasons?UserId="+EtknApi.encode(api.session.userId()))).put("episodes",api.get("/api/tv/Shows/"+EtknApi.encode(series)+"/Episodes?UserId="+EtknApi.encode(api.session.userId())+"&Fields=MediaSources,MediaStreams,Overview")),data->{
            episodeArea.removeAllViews();episodeGrid=null;preview=null;episodeCells.clear();episodeCellDates.clear();
            allEpisodes.clear();allEpisodes.addAll(EtknApi.rows(data.optJSONObject("episodes")));
            if(item.optString("Type").equals("Season"))allEpisodes.removeIf(episode->!item.optString("Id").equals(episode.optString("SeasonId")));
            if(allEpisodes.isEmpty()){status.setText("暂无可播放分集");return;}
            JSONObject recommended=DetailPresentation.recommended(allEpisodes);if(item.optString("Type").equals("Episode")||preserve)for(JSONObject episode:allEpisodes)if((preserve?previousEpisode:item.optString("Id")).equals(episode.optString("Id"))){recommended=episode;break;}setPlayable(recommended);List<JSONObject> seasons=EtknApi.rows(data.optJSONObject("seasons"));
            if(!episodePicker){if(preserve){int retained=PlaybackSelection.sourceIndex(sources,previousSource);if(retained>=0){sourceIndex=retained;audio=previousAudio;subtitle=previousSubtitle;updateChoices();}}episodesButton.setEnabled(true);status.setText("");if(!preserve)focusPlay();return;}
            visibleSeasons.clear();seasonCards.clear();
            if(!preserve)for(JSONObject episode:allEpisodes)if(episode.optString("Id").equals(getIntent().getStringExtra("episodeId"))){recommended=episode;setPlayable(episode);break;}
            int selected=PlaybackSelection.sourceIndex(sources,getIntent().getStringExtra("sourceId"));if(selected>=0)sourceIndex=selected;audio=getIntent().getIntExtra("audioIndex",PlaybackSelection.AUTO);subtitle=getIntent().getIntExtra("subtitleIndex",PlaybackSelection.AUTO);updateChoices();
            LinearLayout seasonRow=row();HorizontalScrollView seasonScroll=new FocusScroll.Row(this);seasonScroll.setHorizontalScrollBarEnabled(false);seasonScroll.setClipToPadding(false);seasonScroll.addView(seasonRow);
            View seasonEntry=null;if(seasons.size()>1&&!item.optString("Type").equals("Season")){episodeArea.addView(seasonScroll);for(JSONObject season:seasons){View card=seasonCard(season,()->showSeason(season.optString("Id")));visibleSeasons.put(season.optString("Id"),season);seasonCards.put(season.optString("Id"),card);seasonRow.addView(card);if(season.optString("Id").equals(recommended.optString("SeasonId")))seasonEntry=((ViewGroup)card).getChildAt(2);}}
            preview=row();preview.setGravity(Gravity.CENTER_VERTICAL);preview.setPadding(0,dp(8),0,dp(8));
            episodeImage=new ImageView(this);episodeImage.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(episodeImage,dp(8));episodeImage.setForeground(Images.border(dp(8)));preview.addView(episodeImage,new LinearLayout.LayoutParams(dp(224),dp(126)));
            LinearLayout copy=column();copy.setPadding(dp(18),0,0,0);episodeTitle=text("",19,Color.WHITE);episodeTitle.setMaxLines(2);copy.addView(episodeTitle);episodeOverview=text("",14,MUTED);episodeOverview.setMaxLines(4);episodeOverview.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(episodeOverview);preview.addView(copy,new LinearLayout.LayoutParams(0,dp(144),1));episodeArea.addView(preview);
            showSeason(recommended.optString("SeasonId"),recommended);status.setText(allEpisodes.size()+" 集");
            if(preserve){int retained=PlaybackSelection.sourceIndex(sources,previousSource);if(retained>=0){sourceIndex=retained;audio=previousAudio;subtitle=previousSubtitle;updateChoices();}View entry=matrixFocused?episodeCells.get(previousEpisode):focused;if(entry==null||!entry.isAttachedToWindow())entry=episodeEntry;View restore=entry;episodeArea.post(()->{if(restore!=null)restore.requestFocus();scroll.scrollTo(0,previousY);});}
            else{View entry=seasonEntry==null?episodeEntry:seasonEntry;if(entry!=null)entry.post(entry::requestFocus);}
        });
    }
    private void showSeason(String seasonId){
        showSeason(seasonId,null);
    }
    private void showSeason(String seasonId,JSONObject preferred){
        if(preview==null)return;
        episodeCells.clear();episodeCellDates.clear();
        while(episodeArea.indexOfChild(preview)+1<episodeArea.getChildCount())episodeArea.removeViewAt(episodeArea.getChildCount()-1);
        List<JSONObject> episodes=new ArrayList<>();for(JSONObject episode:allEpisodes)if(seasonId.isEmpty()||seasonId.equals(episode.optString("SeasonId")))episodes.add(episode);if(episodes.isEmpty())return;
        JSONObject selected=preferred==null?DetailPresentation.recommended(episodes):preferred;setPlayable(selected);updatePreview(selected);
        GridLayout grid=new GridLayout(this);episodeGrid=grid;episodeEntry=null;int available=getResources().getConfiguration().screenWidthDp-84;int columns=Math.max(3,available/112);grid.setColumnCount(columns);int cellWidth=(dp(available)-dp(10)*(columns-1))/columns;
        int matrixHeight=24;for(int index=0;index<episodes.size();index+=columns)matrixHeight+=(DetailPresentation.episodeRowHasDates(episodes,index,columns)?68:44)+14;
        ScrollView matrix=new FocusScroll.Vertical(this);matrix.setFillViewport(false);matrix.addView(grid);episodeArea.addView(matrix,new LinearLayout.LayoutParams(-1,dp(Math.min(224,matrixHeight))));
        for(JSONObject episode:episodes){boolean dates=DetailPresentation.episodeRowHasDates(episodes,grid.getChildCount(),columns);int cellHeight=dates?68:44;Button cell=button("",()->{setPlayable(episode);play(false);});if(dates){cell.setSingleLine(false);cell.setMaxLines(2);cell.setPadding(dp(6),0,dp(6),0);}updateEpisodeCell(cell,episode,dates);episodeCells.put(episode.optString("Id"),cell);episodeCellDates.put(episode.optString("Id"),dates);
            if(episode==selected)episodeEntry=cell;
            GridLayout.LayoutParams bounds=new GridLayout.LayoutParams();bounds.width=cellWidth;bounds.height=dp(cellHeight);bounds.setMargins(0,dp(7),grid.getChildCount()%columns==columns-1?0:dp(10),dp(7));grid.addView(cell,bounds);
            cell.setOnFocusChangeListener((view,focused)->{if(focused){setPlayable(episode);updatePreview(episode);scroll.post(()->{Rect rect=new Rect();preview.getDrawingRect(rect);content.offsetDescendantRectToMyCoords(preview,rect);scroll.smoothScrollTo(0,rect.top-dp(24));});}});
        }
    }
    private void updateEpisodeCell(Button cell,JSONObject episode,boolean dates){
        JSONObject state=episode.optJSONObject("UserData");boolean watched=state!=null&&state.optBoolean("Played");long runtime=episode.optLong("RunTimeTicks");int progress=watched?10000:runtime>0?(int)Math.min(9999,10000.0*DetailPresentation.position(episode)/runtime):0;
        cell.setText(episodeLabel(episode,watched?" ✓":"",dates));cell.setContentDescription(DetailPresentation.episodeNumber(episode)+"，"+EtknApi.title(episode)+(watched?"，已观看":"")+(DetailPresentation.placeholder(episode)?"，播出："+DetailPresentation.airDate(episode):"")+(!watched&&progress>0?"，续播进度 "+progress/100+"%":""));cell.setBackground(episodeBackground(progress));
    }
    private void updatePreview(JSONObject episode){
        previewEpisode=episode;updatePreviewText(episode);
        episodeImage.setImageDrawable(null);Images.load(api,api.artwork(episode,"Primary",640),episodeImage);
    }
    private void updatePreviewText(JSONObject episode){episodeTitle.setText(DetailPresentation.episodeNumber(episode)+"  ·  "+EtknApi.title(episode)+(DetailPresentation.placeholder(episode)?"\n播出："+DetailPresentation.airDate(episode):""));episodeOverview.setText(episode.optString("Overview").isEmpty()?"暂无分集简介":episode.optString("Overview"));}
    private void renderChapters(){renderChapters(item);}
    private void renderChapters(JSONObject source){
        String focused="";for(Map.Entry<String,ImageView> entry:chapterImages.entrySet())if(entry.getValue().getParent()==getCurrentFocus())focused=entry.getKey();String restore=focused;
        if(chapterArea==null){chapterArea=column();content.addView(chapterArea);}chapterArea.removeAllViews();chapterImages.clear();
        JSONArray chapters=source.optJSONArray("Chapters");if(chapters==null||chapters.length()==0)return;
        List<JSONObject> images=new ArrayList<>();for(int i=0;i<chapters.length();i++){JSONObject chapter=chapters.optJSONObject(i);if(chapter!=null&&(!chapter.optString("ImageTag").isEmpty()||!chapter.optString("ImagePath").isEmpty()))images.add(chapter);}
        if(images.isEmpty())return;
        TextView heading=text("章节",20,Color.WHITE);heading.setFocusable(false);heading.setFocusableInTouchMode(false);chapterArea.addView(heading);
        HorizontalScrollView rail=new FocusScroll.Row(this);rail.setHorizontalScrollBarEnabled(false);rail.setFocusable(false);rail.setFocusableInTouchMode(false);rail.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        LinearLayout cards=row();cards.setFocusable(false);cards.setFocusableInTouchMode(false);rail.addView(cards);chapterArea.addView(rail);
        for(int i=0;i<images.size();i++){
            JSONObject chapter=images.get(i);LinearLayout card=column();card.setFocusable(true);card.setFocusableInTouchMode(false);card.setClickable(true);card.setBackground(transparentFocusBackground());card.setPadding(dp(3),dp(3),dp(3),dp(3));
            LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(dp(180),dp(140));bounds.setMargins(0,dp(8),dp(14),dp(8));cards.addView(card,bounds);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(image,dp(8));image.setForeground(Images.border(dp(8)));image.setFocusable(false);card.addView(image,new LinearLayout.LayoutParams(-1,dp(102)));chapterImages.put(String.valueOf(chapter.optInt("ChapterIndex",i)),image);Images.load(api,api.chapterArtwork(source,chapter,480),image);
            String name=chapter.optString("Name");if(name.isEmpty())name="章节 "+(i+1);TextView label=text(name+"  ·  "+chapterTime(chapter.optLong("StartPositionTicks")),12,Color.WHITE);label.setFocusable(false);label.setFocusableInTouchMode(false);label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setPadding(dp(3),dp(3),dp(3),0);card.addView(label);
            card.setContentDescription(name);card.setOnClickListener(view->playChapter(chapter));
        }if(!restore.isEmpty()){ImageView retained=chapterImages.get(restore);View target=retained==null?cards.getChildAt(0):(View)retained.getParent();target.post(target::requestFocus);}
    }
    private String chapterTime(long ticks){long seconds=Math.max(0,ticks/10000000L),minutes=seconds/60;seconds%=60;return String.format(Locale.ROOT,"%02d:%02d",minutes,seconds);}
    private void playChapter(JSONObject chapter){
        if(playable==null)return;long position=Math.max(0,chapter.optLong("StartPositionTicks",0)/10000L);
        Intent intent=new Intent(this,PlayerActivity.class).putExtra("id",playable.optString("Id")).putExtra("title",EtknApi.resumeTitle(playable)).putExtra("position",position).putExtra("seriesId",playable.optString("SeriesId",item.optString("Type").equals("Series")?item.optString("Id"):item.optString("SeriesId")));
        if(selectedSource()!=null)intent.putExtra("sourceId",selectedSource().optString("Id"));intent.putExtra("seasonKey",PlaybackSelection.seasonKey(playable)).putExtra("audioIndex",audio).putExtra("subtitleIndex",subtitle);startPlayback(intent);
    }
    private void renderCast(){renderCast(item);}
    private void renderCast(JSONObject source){
        String focusedId="";for(Map.Entry<String,ImageView> entry:castImages.entrySet())if(entry.getValue()==getCurrentFocus())focusedId=entry.getKey();String restore=focusedId;
        if(castArea==null){castArea=column();content.addView(castArea);}castArea.removeAllViews();castImages.clear();castEntry=null;castScroll=null;
        JSONArray people=source.optJSONArray("People");if(people==null||people.length()==0)return;
        List<JSONObject> actors=new ArrayList<>();for(int i=0;i<people.length();i++){JSONObject person=people.optJSONObject(i);if(person!=null&&person.optString("Type",person.optString("PersonType")).equalsIgnoreCase("Actor"))actors.add(person);}if(actors.isEmpty())return;
        TextView heading=text("演员表",18,Color.WHITE);heading.setPadding(0,0,0,0);castArea.addView(heading);LinearLayout cast=row();castScroll=new FocusScroll.Row(this);castScroll.setHorizontalScrollBarEnabled(false);castScroll.setFocusable(false);castScroll.setClipToPadding(false);castScroll.addView(cast);castArea.addView(castScroll);
        int width=Math.max(1,(getResources().getDisplayMetrics().widthPixels-content.getPaddingLeft()-content.getPaddingRight())/8-dp(12)),height=Math.round(width*1.5f);
        for(JSONObject person:actors){
            LinearLayout card=column();LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(width,height+dp(50));bounds.setMargins(dp(6),dp(8),dp(6),dp(8));cast.addView(card,bounds);
            ImageView portrait=new ImageView(this);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);portrait.setImageResource(android.R.drawable.ic_menu_myplaces);Images.rounded(portrait,dp(7));portrait.setForeground(Images.border(dp(12)));shadow(portrait,4);portrait.setFocusable(true);portrait.setClickable(true);portrait.setContentDescription("演员 · "+person.optString("Name"));castImages.put(person.optString("Id"),portrait);
            portrait.setOnClickListener(view->startActivity(new Intent(this,PersonActivity.class).putExtra("id",person.optString("Id")).putExtra("name",person.optString("Name"))));
            if(castEntry==null)castEntry=portrait;
            portrait.setOnFocusChangeListener((view,focused)->{portrait.setScaleX(focused?FOCUS_SCALE:1f);portrait.setScaleY(focused?FOCUS_SCALE:1f);shadow(portrait,focused?8:4);portrait.setForeground(focused?focusOutline():Images.border(dp(12)));});card.addView(portrait,new LinearLayout.LayoutParams(-1,height));Images.load(api,api.artwork(person,"Primary",240),portrait);
            TextView name=text(person.optString("Name"),13,Color.WHITE);name.setGravity(Gravity.CENTER);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(0,dp(4),0,0);card.addView(name,new LinearLayout.LayoutParams(-1,dp(22)));TextView role=text(person.optString("Role"),11,MUTED);role.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);role.setMaxLines(2);role.setEllipsize(android.text.TextUtils.TruncateAt.END);role.setPadding(0,0,0,0);card.addView(role,new LinearLayout.LayoutParams(-1,dp(28)));
        }if(!restore.isEmpty()){View target=castImages.containsKey(restore)?castImages.get(restore):castEntry;target.post(target::requestFocus);}
    }
    private void renderSimilar(){
        LinearLayout area=column();content.addView(area);area.addView(text("更多类似",20,Color.WHITE));TextView loading=text("正在读取相似媒体…",13,MUTED);area.addView(loading);
        async(()->api.get("/api/tv/Items/"+EtknApi.encode(item.optString("Id"))+"/Similar?UserId="+EtknApi.encode(api.session.userId())+"&Limit=20"),result->{
            area.removeView(loading);List<JSONObject> items=EtknApi.rows(result);if(items.isEmpty()){area.addView(text("暂无相似推荐",13,MUTED));return;}
            HorizontalScrollView rail=new FocusScroll.Row(this);rail.setHorizontalScrollBarEnabled(false);GridLayout cards=new GridLayout(this);cards.setRowCount(1);cards.setColumnCount(items.size());rail.addView(cards);area.addView(rail);
            for(JSONObject media:items)posterCard(cards,media,()->detail(media),result.optString("EpisodeCountMode","unplayed"));
        });
    }
    private void play(boolean fromStart){
        if(playable==null)return;
        Intent intent=new Intent(this,PlayerActivity.class).putExtra("id",playable.optString("Id")).putExtra("title",EtknApi.resumeTitle(playable)).putExtra("position",fromStart?0:DetailPresentation.position(playable)/10000).putExtra("seriesId",playable.optString("SeriesId",item.optString("Type").equals("Series")?item.optString("Id"):item.optString("SeriesId")));
        if(selectedSource()!=null)intent.putExtra("sourceId",selectedSource().optString("Id"));intent.putExtra("seasonKey",PlaybackSelection.seasonKey(playable)).putExtra("audioIndex",audio).putExtra("subtitleIndex",subtitle);startPlayback(intent);
    }
}
