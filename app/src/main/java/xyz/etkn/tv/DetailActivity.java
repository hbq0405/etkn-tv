package xyz.etkn.tv;

import android.app.AlertDialog;
import android.content.Intent;
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
    private JSONObject item,playable;
    private final List<JSONObject> allEpisodes=new ArrayList<>();
    private JSONArray sources=new JSONArray();
    private int sourceIndex,audio=PlaybackSelection.AUTO,subtitle=PlaybackSelection.AUTO;
    private Button playButton,versionButton,audioButton,subtitleButton,restartButton,favoriteButton,playedButton;
    private int stateTicket;
    private Button refreshButton,deleteButton;
    private boolean managementBusy;
    private AlertDialog managementDialog;
    private LinearLayout episodeArea,preview;
    private TextView episodeTitle,episodeOverview;
    private ImageView backdrop,episodeImage,detailLogo;
    private TextView detailTitle,detailDouban,detailTmdb,detailYear,detailDuration,detailRating,detailCategories,detailOverview;
    private Button cloudButton;
    private boolean canDiscover,cloudSearching;
    private JSONObject previewEpisode;
    private HomeUpdates artworkUpdates;
    private boolean artworkForeground,artworkLoading,artworkPending;
    private String artworkRevision="";
    private final android.os.Handler artworkTimer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable artworkRefresh=()->refreshArtwork();
    private final Runnable artworkFallback=new Runnable(){@Override public void run(){refreshArtwork();artworkTimer.postDelayed(this,60000);}};
    private ScrollView scroll;
    private GridLayout episodeGrid;
    private Button episodeEntry;
    private LinearLayout facetButtons;
    private Button seriesButton;
    private final List<JSONObject> itemCollections=new ArrayList<>();
    private String renderedFacets="";

    private boolean inEpisodeMatrix(View view){while(view!=null){if(view==episodeGrid)return true;android.view.ViewParent parent=view.getParent();view=parent instanceof View?(View)parent:null;}return false;}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN&&episodeEntry!=null&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP)){
            View current=getCurrentFocus();int direction=event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN?View.FOCUS_DOWN:View.FOCUS_UP;
            if(current!=null&&!inEpisodeMatrix(current)&&inEpisodeMatrix(current.focusSearch(direction))){episodeEntry.requestFocus();return true;}
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(INK);
        backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(backdrop,0);
        stage.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        stage.addView(backdropShade(),new FrameLayout.LayoutParams(-1,-1));
        scroll=new FocusScroll.Vertical(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        content=column();content.setPadding(dp(42),dp(26),dp(42),dp(28));scroll.addView(content);stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        status=text("正在读取详情…",13,MUTED);content.addView(status);setContentView(stage);
        async(()->api.get(api.userPath()+"/Items/"+EtknApi.encode(getIntent().getStringExtra("id"))),this::render);
    }

    private void render(JSONObject result){
        item=result;content.removeAllViews();
        Images.load(api,api.artwork(item,"Backdrop",1920),backdrop);
        FrameLayout identity=new FrameLayout(this);int titleWidth=Math.min(560,getResources().getConfiguration().screenWidthDp-84);
        TextView title=text(EtknApi.title(item),32,Color.WHITE);detailTitle=title;title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER_VERTICAL);title.setMaxLines(2);
        identity.addView(title,new FrameLayout.LayoutParams(-1,-1));
        ImageView logo=new ImageView(this);detailLogo=logo;logo.setScaleType(ImageView.ScaleType.FIT_START);Images.rounded(logo,dp(4));logo.setVisibility(View.GONE);
        identity.addView(logo,new FrameLayout.LayoutParams(dp(Math.min(360,titleWidth)),dp(88),Gravity.CENTER_VERTICAL));
        content.addView(identity,new LinearLayout.LayoutParams(dp(titleWidth),dp(96)));
        Images.load(api,api.artwork(item,"Logo",720),logo,ok->{logo.setVisibility(ok?View.VISIBLE:View.GONE);title.setVisibility(ok?View.GONE:View.VISIBLE);});
        LinearLayout metadata=row();metadata.setGravity(Gravity.CENTER_VERTICAL);
        ImageView douban=new ImageView(this);douban.setImageDrawable(doubanMark());douban.setPadding(dp(3),dp(3),dp(3),dp(3));douban.setBackground(shape(Color.rgb(0,119,34),0));metadata.addView(douban,new LinearLayout.LayoutParams(dp(21),dp(21)));
        detailDouban=meta("",Color.WHITE);metadata.addView(detailDouban);
        ImageView tmdb=new ImageView(this);tmdb.setImageDrawable(tmdbMark());metadata.addView(tmdb,new LinearLayout.LayoutParams(dp(21),dp(21)));
        detailTmdb=meta("",Color.rgb(255,205,75));metadata.addView(detailTmdb);
        detailYear=meta("",MUTED);metadata.addView(detailYear);detailDuration=meta("",MUTED);metadata.addView(detailDuration);
        detailRating=meta("",Color.WHITE);detailRating.setPadding(dp(7),dp(2),dp(7),dp(2));detailRating.setBackground(shape(Color.TRANSPARENT,MUTED));metadata.addView(detailRating);
        seriesButton=button("系列",this::openItemCollection);seriesButton.setBackground(selectionBackground());seriesButton.setContentDescription("所属合集");seriesButton.setVisibility(View.GONE);
        content.addView(metadata);
        detailCategories=text("",14,MUTED);content.addView(detailCategories);
        facetButtons=row();HorizontalScrollView facetScroll=new FocusScroll.Row(this);facetScroll.setHorizontalScrollBarEnabled(false);facetScroll.addView(facetButtons);content.addView(facetScroll);
        LinearLayout actions=row();
        playButton=actionButton("play","播放",()->play(false));playButton.setId(View.generateViewId());playButton.setEnabled(false);actions.addView(playButton);
        restartButton=actionButton("restart","从头播放",()->play(true));restartButton.setVisibility(View.GONE);actions.addView(restartButton);
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
        LinearLayout selections=row();versionButton=button("版本",this::chooseVersion);audioButton=button("音轨",()->chooseStream(false));subtitleButton=button("字幕",()->chooseStream(true));
        selections.addView(versionButton);selections.addView(audioButton);selections.addView(subtitleButton);
        for(Button choice:new Button[]{versionButton,audioButton,subtitleButton})choice.setBackground(selectionBackground());
        HorizontalScrollView selectionScroll=new FocusScroll.Row(this);selectionScroll.setHorizontalScrollBarEnabled(false);selectionScroll.addView(selections);content.addView(selectionScroll);
        TextView overview=text("",15,Color.rgb(219,227,237));detailOverview=overview;overview.setMaxLines(3);overview.setEllipsize(android.text.TextUtils.TruncateAt.END);overview.setFocusableInTouchMode(false);overview.setPadding(dp(10),dp(5),dp(10),dp(5));overview.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(EtknApi.title(item)).setMessage(item.optString("Overview")).setPositiveButton("关闭",null).show());
        content.addView(overview,new LinearLayout.LayoutParams(dp(Math.min(650,getResources().getConfiguration().screenWidthDp-84)),-2));
        updateMetadata();
        renderChapters();
        status=text("",12,MUTED);content.addView(status);episodeArea=column();content.addView(episodeArea);updateChoices();
        String type=item.optString("Type");
        if(type.equals("Series")||type.equals("Season")||type.equals("Episode"))loadEpisodes();else{setPlayable(item);focusPlay();}
        renderCast();
        renderCollections();
        renderSimilar();
    }
    private TextView meta(String value,int color){TextView label=text(value,13,color);label.setGravity(Gravity.CENTER_VERTICAL);return label;}
    private void updateMetadata(){
        detailTitle.setText(EtknApi.title(item));detailDouban.setText(" "+DetailPresentation.rating(item,"DoubanRating")+"    ");detailTmdb.setText(" "+DetailPresentation.rating(item,"TMDbRating")+"    ");
        String year=item.optString("ProductionYear"),duration=DetailPresentation.duration(item.optLong("RunTimeTicks")),rating=DetailPresentation.contentRating(item),categories=DetailPresentation.categories(item);
        detailYear.setText(year+"    ");detailYear.setVisibility(year.isEmpty()?View.GONE:View.VISIBLE);detailDuration.setText(duration+"    ");detailDuration.setVisibility(duration.isEmpty()?View.GONE:View.VISIBLE);
        detailRating.setText(rating);detailRating.setVisibility(rating.isEmpty()?View.GONE:View.VISIBLE);detailCategories.setVisibility(View.GONE);renderFacets();
        boolean hasOverview=!item.optString("Overview").trim().isEmpty();detailOverview.setText(hasOverview?item.optString("Overview"):"暂无简介");
        if(detailOverview.isFocusable()!=hasOverview||detailOverview.getBackground()==null){if(!hasOverview&&detailOverview.hasFocus())playButton.requestFocus();detailOverview.setFocusable(hasOverview);detailOverview.setBackground(hasOverview?transparentFocusBackground():shape(Color.TRANSPARENT,0));}
        detailOverview.setClickable(hasOverview);
    }
    private void renderFacets(){
        String identity=String.valueOf(item.optJSONArray("Genres"))+String.valueOf(item.optJSONArray("Tags"));if(identity.equals(renderedFacets))return;renderedFacets=identity;facetButtons.removeAllViews();
        for(LibraryFilters.Entry entry:LibraryFilters.mergeTypes(item)){Button choice=button(entry.label,()->startActivity(new Intent(this,HomeActivity.class).putExtra("facetKind",entry.kind).putExtra("facetValue",entry.label)));choice.setBackground(selectionBackground());choice.setContentDescription((entry.kind.equals("Genres")?"题材 · ":"标签 · ")+entry.label);facetButtons.addView(choice);}
        facetButtons.addView(seriesButton);
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
    private void copyPresentation(JSONObject target,JSONObject value){
        for(String key:new String[]{"Name","Overview","ProductionYear","RunTimeTicks","OfficialRating","CustomRating","DoubanRating","TMDbRating","Genres","Tags","ImageTags","BackdropImageTags","BackdropImageTag","ParentBackdropItemId","ParentBackdropImageTags","ParentLogoItemId","ParentLogoImageTag","PremiereDate","IsMissing","IsUnaired","IsVirtualItem","LocationType"})try{target.put(key,value.opt(key));}catch(org.json.JSONException ignored){}
    }
    private Button actionButton(String icon,String label,Runnable click){
        Button button=button("",click);button.setBackground(selectionBackground());button.setPadding(dp(16),dp(8),dp(16),dp(8));button.setMinWidth(0);button.setMinimumWidth(0);
        LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(dp(64),dp(52));bounds.setMargins(0,dp(6),dp(12),dp(2));button.setLayoutParams(bounds);
        actionIcon(button,icon,label,false);return button;
    }
    private void actionIcon(Button button,String icon,String label,boolean active){
        MediaActionIcon drawable=new MediaActionIcon(icon,active);drawable.setBounds(0,0,dp(32),dp(32));button.setCompoundDrawables(drawable,null,null,null);button.setContentDescription(label);
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
        if(target==null)return;
        JSONObject data=target.optJSONObject("UserData");
        if(data==null){data=new JSONObject();try{target.put("UserData",data);}catch(Exception ignored){}}
        for(String key:new String[]{"PlaybackPositionTicks","PlayedPercentage","IsResumable","PlayCount","IsFavorite","Played","LastPlayedDate"}){try{if(result.has(key))data.put(key,result.get(key));}catch(Exception ignored){}}
    }
    private void applyUserData(JSONObject result){
        mergeUserData(item,result);
        if(playable!=null&&playable.optString("Id").equals(item.optString("Id")))mergeUserData(playable,result);
        if(playable!=null)updatePlayAction();
    }
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
        async(()->api.get("/api/tv/auth/check_status"),result->{JSONObject user=result.optJSONObject("user");canDiscover=user!=null&&user.optBoolean("can_access_discovery");if(cloudButton!=null)cloudButton.setVisibility(canDiscover?View.VISIBLE:View.GONE);});
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
    @Override protected void onDestroy(){if(managementDialog!=null)managementDialog.dismiss();super.onDestroy();}
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
        if(cloudButton!=null)updateDiscoveryPermission();
        if(!PlaybackSelection.seasonKey(playable).isEmpty()){audio=PlaybackSelection.AUTO;updateChoices();}
        refreshArtwork();artworkTimer.postDelayed(artworkFallback,60000);
        artworkUpdates=new HomeUpdates(api,value->runOnUiThread(()->{
            if(!artworkForeground||value.equals(artworkRevision))return;
            artworkRevision=value;artworkTimer.removeCallbacks(artworkRefresh);artworkTimer.postDelayed(artworkRefresh,250);
        }),()->runOnUiThread(()->{if(artworkForeground){api.session.clear();startActivity(new Intent(this,SetupActivity.class));finish();}}));
    }
    @Override protected void onPause(){artworkForeground=false;artworkTimer.removeCallbacksAndMessages(null);if(artworkUpdates!=null){artworkUpdates.close();artworkUpdates=null;}super.onPause();}
    private void refreshArtwork(){
        if(!artworkForeground||item==null)return;if(artworkLoading){artworkPending=true;return;}
        artworkLoading=true;String path=api.userPath()+"/Items/"+EtknApi.encode(item.optString("Id"));
        String episodeId=previewEpisode==null?"":previewEpisode.optString("Id");
        async(()->{try{JSONObject result=api.get(path);if(!episodeId.isEmpty())result.put("_preview",api.get(api.userPath()+"/Items/"+EtknApi.encode(episodeId)));return result;}
            catch(EtknApi.AuthException error){throw error;}catch(Exception error){return new JSONObject();}},result->{
            artworkLoading=false;if(artworkForeground&&!result.optString("Id").isEmpty()){
                copyPresentation(item,result);mergeSources(result);updateMetadata();
                Images.load(api,api.artwork(result,"Backdrop",1920),backdrop);
                Images.load(api,api.artwork(result,"Logo",720),detailLogo,ok->{detailLogo.setVisibility(ok?View.VISIBLE:View.GONE);detailTitle.setVisibility(ok?View.GONE:View.VISIBLE);});
                JSONObject episode=result.optJSONObject("_preview");if(episode!=null&&previewEpisode!=null&&episodeId.equals(previewEpisode.optString("Id"))){copyPresentation(previewEpisode,episode);mergeSources(episode);updatePreviewText(previewEpisode);Images.load(api,api.artwork(episode,"Primary",640),episodeImage);}
            }
            if(artworkPending){artworkPending=false;refreshArtwork();}
        });
    }
    @Override void metadataRefreshed(){super.metadataRefreshed();refreshArtwork();renderCollections();focusPlay();}
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
    private void loadEpisodes(){
        String series=item.optString("Type").equals("Series")?item.optString("Id"):item.optString("SeriesId",item.optString("ParentId"));status.setText("正在读取分集…");
        async(()->new JSONObject().put("seasons",api.get("/api/tv/Shows/"+EtknApi.encode(series)+"/Seasons?UserId="+EtknApi.encode(api.session.userId()))).put("episodes",api.get("/api/tv/Shows/"+EtknApi.encode(series)+"/Episodes?UserId="+EtknApi.encode(api.session.userId())+"&Fields=MediaSources,MediaStreams,Overview")),data->{
            episodeArea.removeAllViews();episodeGrid=null;preview=null;
            allEpisodes.clear();allEpisodes.addAll(EtknApi.rows(data.optJSONObject("episodes")));
            if(item.optString("Type").equals("Season"))allEpisodes.removeIf(episode->!item.optString("Id").equals(episode.optString("SeasonId")));
            if(allEpisodes.isEmpty()){status.setText("暂无可播放分集");return;}
            JSONObject recommended=DetailPresentation.recommended(allEpisodes);if(item.optString("Type").equals("Episode"))for(JSONObject episode:allEpisodes)if(item.optString("Id").equals(episode.optString("Id"))){recommended=episode;break;}setPlayable(recommended);List<JSONObject> seasons=EtknApi.rows(data.optJSONObject("seasons"));
            LinearLayout seasonRow=row();HorizontalScrollView seasonScroll=new FocusScroll.Row(this);seasonScroll.setHorizontalScrollBarEnabled(false);seasonScroll.addView(seasonRow);
            if(seasons.size()>1&&!item.optString("Type").equals("Season")){episodeArea.addView(seasonScroll);for(JSONObject season:seasons)seasonRow.addView(button(EtknApi.title(season),()->showSeason(season.optString("Id"))));}
            preview=row();preview.setGravity(Gravity.CENTER_VERTICAL);preview.setPadding(0,dp(8),0,dp(8));
            episodeImage=new ImageView(this);episodeImage.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(episodeImage,dp(8));preview.addView(episodeImage,new LinearLayout.LayoutParams(dp(224),dp(126)));
            LinearLayout copy=column();copy.setPadding(dp(18),0,0,0);episodeTitle=text("",19,Color.WHITE);episodeTitle.setMaxLines(2);copy.addView(episodeTitle);episodeOverview=text("",14,MUTED);episodeOverview.setMaxLines(4);episodeOverview.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(episodeOverview);preview.addView(copy,new LinearLayout.LayoutParams(0,dp(144),1));episodeArea.addView(preview);
            showSeason(recommended.optString("SeasonId"),recommended);status.setText(allEpisodes.size()+" 集");focusPlay();
        });
    }
    private void showSeason(String seasonId){
        showSeason(seasonId,null);
    }
    private void showSeason(String seasonId,JSONObject preferred){
        if(preview==null)return;
        while(episodeArea.indexOfChild(preview)+1<episodeArea.getChildCount())episodeArea.removeViewAt(episodeArea.getChildCount()-1);
        List<JSONObject> episodes=new ArrayList<>();for(JSONObject episode:allEpisodes)if(seasonId.isEmpty()||seasonId.equals(episode.optString("SeasonId")))episodes.add(episode);if(episodes.isEmpty())return;
        JSONObject selected=preferred==null?DetailPresentation.recommended(episodes):preferred;setPlayable(selected);updatePreview(selected);
        GridLayout grid=new GridLayout(this);episodeGrid=grid;episodeEntry=null;int available=getResources().getConfiguration().screenWidthDp-84;int columns=Math.max(3,available/112);grid.setColumnCount(columns);int cellWidth=(dp(available)-dp(10)*(columns-1))/columns;
        boolean dates=episodes.stream().anyMatch(DetailPresentation::placeholder);int cellHeight=dates?68:44;
        ScrollView matrix=new FocusScroll.Vertical(this);matrix.setFillViewport(false);matrix.addView(grid);episodeArea.addView(matrix,new LinearLayout.LayoutParams(-1,dp(Math.min(224,((episodes.size()+columns-1)/columns)*(cellHeight+14)+24))));
        for(JSONObject episode:episodes){JSONObject state=episode.optJSONObject("UserData");boolean watched=state!=null&&state.optBoolean("Played");Button cell=button("",()->{setPlayable(episode);play(false);});cell.setText(episodeLabel(episode,watched?" ✓":"",dates));if(dates){cell.setSingleLine(false);cell.setMaxLines(2);cell.setPadding(dp(6),0,dp(6),0);}cell.setContentDescription(DetailPresentation.episodeNumber(episode)+"，"+EtknApi.title(episode)+(watched?"，已观看":"")+(DetailPresentation.placeholder(episode)?"，播出："+DetailPresentation.airDate(episode):""));
            long runtime=episode.optLong("RunTimeTicks");int progress=watched?10000:runtime>0?(int)Math.min(9999,10000.0*DetailPresentation.position(episode)/runtime):0;
            cell.setBackground(episodeBackground(progress));if(!watched&&progress>0)cell.setContentDescription(cell.getContentDescription()+"，续播进度 "+progress/100+"%");
            if(episode==selected)episodeEntry=cell;
            GridLayout.LayoutParams bounds=new GridLayout.LayoutParams();bounds.width=cellWidth;bounds.height=dp(cellHeight);bounds.setMargins(0,dp(7),grid.getChildCount()%columns==columns-1?0:dp(10),dp(7));grid.addView(cell,bounds);
            cell.setOnFocusChangeListener((view,focused)->{if(focused){setPlayable(episode);updatePreview(episode);scroll.post(()->{Rect rect=new Rect();preview.getDrawingRect(rect);content.offsetDescendantRectToMyCoords(preview,rect);scroll.smoothScrollTo(0,rect.top-dp(24));});}});
        }
    }
    private void updatePreview(JSONObject episode){
        previewEpisode=episode;updatePreviewText(episode);
        episodeImage.setImageDrawable(null);Images.load(api,api.artwork(episode,"Primary",640),episodeImage);
    }
    private void updatePreviewText(JSONObject episode){episodeTitle.setText(DetailPresentation.episodeNumber(episode)+"  ·  "+EtknApi.title(episode)+(DetailPresentation.placeholder(episode)?"\n播出："+DetailPresentation.airDate(episode):""));episodeOverview.setText(episode.optString("Overview").isEmpty()?"暂无分集简介":episode.optString("Overview"));}
    private void renderChapters(){
        JSONArray chapters=item.optJSONArray("Chapters");if(chapters==null||chapters.length()==0)return;
        List<JSONObject> images=new ArrayList<>();for(int i=0;i<chapters.length();i++){JSONObject chapter=chapters.optJSONObject(i);if(chapter!=null&&(!chapter.optString("ImageTag").isEmpty()||!chapter.optString("ImagePath").isEmpty()))images.add(chapter);}
        if(images.isEmpty())return;
        TextView heading=text("章节",20,Color.WHITE);heading.setFocusable(false);heading.setFocusableInTouchMode(false);content.addView(heading);
        HorizontalScrollView rail=new FocusScroll.Row(this);rail.setHorizontalScrollBarEnabled(false);rail.setFocusable(false);rail.setFocusableInTouchMode(false);rail.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        LinearLayout cards=row();cards.setFocusable(false);cards.setFocusableInTouchMode(false);rail.addView(cards);content.addView(rail);
        for(int i=0;i<images.size();i++){
            JSONObject chapter=images.get(i);LinearLayout card=column();card.setFocusable(true);card.setFocusableInTouchMode(false);card.setClickable(true);card.setBackground(transparentFocusBackground());card.setPadding(dp(3),dp(3),dp(3),dp(3));
            LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(dp(180),dp(140));bounds.setMargins(0,dp(8),dp(14),dp(8));cards.addView(card,bounds);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(image,dp(8));image.setFocusable(false);card.addView(image,new LinearLayout.LayoutParams(-1,dp(102)));Images.load(api,api.chapterArtwork(item,chapter,480),image);
            String name=chapter.optString("Name");if(name.isEmpty())name="章节 "+(i+1);TextView label=text(name+"  ·  "+chapterTime(chapter.optLong("StartPositionTicks")),12,Color.WHITE);label.setFocusable(false);label.setFocusableInTouchMode(false);label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setPadding(dp(3),dp(3),dp(3),0);card.addView(label);
            card.setContentDescription(name);card.setOnClickListener(view->playChapter(chapter));
        }
    }
    private String chapterTime(long ticks){long seconds=Math.max(0,ticks/10000000L),minutes=seconds/60;seconds%=60;return String.format(Locale.ROOT,"%02d:%02d",minutes,seconds);}
    private void playChapter(JSONObject chapter){
        if(playable==null)return;long position=Math.max(0,chapter.optLong("StartPositionTicks",0)/10000L);
        Intent intent=new Intent(this,PlayerActivity.class).putExtra("id",playable.optString("Id")).putExtra("title",EtknApi.resumeTitle(playable)).putExtra("position",position).putExtra("seriesId",playable.optString("SeriesId",item.optString("Type").equals("Series")?item.optString("Id"):item.optString("SeriesId")));
        if(selectedSource()!=null)intent.putExtra("sourceId",selectedSource().optString("Id"));intent.putExtra("seasonKey",PlaybackSelection.seasonKey(playable)).putExtra("audioIndex",audio).putExtra("subtitleIndex",subtitle);startActivity(intent);
    }
    private void renderCast(){
        JSONArray people=item.optJSONArray("People");if(people==null||people.length()==0)return;
        content.addView(text("演员表",20,Color.WHITE));LinearLayout cast=row();HorizontalScrollView castScroll=new FocusScroll.Row(this);castScroll.setHorizontalScrollBarEnabled(false);castScroll.addView(cast);content.addView(castScroll);
        for(int i=0;i<people.length();i++){JSONObject person=people.optJSONObject(i);if(person==null||!person.optString("Type",person.optString("PersonType")).equalsIgnoreCase("Actor"))continue;
            LinearLayout card=column();card.setFocusable(true);card.setBackground(transparentFocusBackground());card.setPadding(dp(3),dp(3),dp(3),dp(3));LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(dp(112),dp(200));bounds.setMargins(0,dp(8),dp(14),dp(8));cast.addView(card,bounds);
            card.setContentDescription("演员 · "+person.optString("Name"));card.setClickable(true);card.setOnClickListener(view->startActivity(new Intent(this,PersonActivity.class).putExtra("id",person.optString("Id")).putExtra("name",person.optString("Name"))));
            ImageView portrait=new ImageView(this);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);portrait.setImageResource(android.R.drawable.ic_menu_myplaces);Images.rounded(portrait,dp(7));card.addView(portrait,new LinearLayout.LayoutParams(-1,dp(130)));Images.load(api,api.artwork(person,"Primary",240),portrait);
            TextView name=text(person.optString("Name"),13,Color.WHITE);name.setGravity(Gravity.CENTER);name.setMaxLines(1);card.addView(name);TextView role=text(person.optString("Role"),11,MUTED);role.setGravity(Gravity.CENTER);role.setMaxLines(2);role.setPadding(0,0,0,0);card.addView(role);
        }
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
        if(selectedSource()!=null)intent.putExtra("sourceId",selectedSource().optString("Id"));intent.putExtra("seasonKey",PlaybackSelection.seasonKey(playable)).putExtra("audioIndex",audio).putExtra("subtitleIndex",subtitle);startActivity(intent);
    }
}
