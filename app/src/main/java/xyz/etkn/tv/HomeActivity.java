package xyz.etkn.tv;

import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.view.KeyEvent;
import android.view.Gravity;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.util.List;

public final class HomeActivity extends TvActivity {
    private String parent="",query="",mode="home";
    private int offset,revision;
    private int listingLimit;
    private org.json.JSONObject selectedLibrary,selectedGroup;
    private String groupOrigin="home";
    private GridLayout posterGrid;
    private TextView libraryMeta,posterStatus;
    private boolean loadingMore,hasMore;
    private boolean refreshingPosters,pendingPosterRefresh,mediaActionBusy,openingItemMenu;
    private AlertDialog itemMenu;
    private String episodeCountMode="unplayed",sortBy="SortName",sortOrder="Ascending";
    private String viewMode="portrait",playMode="sequence",typeFilter="",typeKind="";
    private boolean favoriteOnly;
    private org.json.JSONObject activeCollection;
    private boolean folderMode;
    private String folderId="",folderTitle="",restorePosterId="",collectionReturnFocus="";
    private int restorePosterCount,restorePosterScroll,collectionReturnCount,collectionReturnScroll;
    private final java.util.ArrayDeque<JSONObject> folderTrail=new java.util.ArrayDeque<>();
    private Button playModeButton,viewModeButton;
    private static final String[][] SORTS={
        {"名称","SortName"},{"名称（显示名）","Name"},{"最近添加","DateAdded"},{"创建时间","DateCreated"},
        {"最近修改","DateModified"},{"最近内容更新","DateLastContentAdded"},{"最近媒体更新","DateLastMediaAdded"},
        {"最近内容首播","LastContentPremiereDate"},{"首播日期","PremiereDate"},{"年份","ProductionYear"},
        {"文件大小","Size"},{"最近播放","DatePlayed"},{"播放次数","PlayCount"},{"TMDb评分","TMDbRating"},
        {"豆瓣评分","DoubanRating"},{"影评评分","CriticRating"},{"IMDb评分","ImdbRating"},
        {"分级","OfficialRating"},{"时长","Runtime"},{"时长刻度","RunTimeTicks"},
        {"集数","IndexNumber"},{"季数","ParentIndexNumber"},{"随机","Random"}
    };
    private static final String[][] FACET_SORTS={{"名称","SortName"},{"最近添加","DateAdded"},{"首播日期","PremiereDate"},{"年份","ProductionYear"},{"TMDb评分","TMDbRating"},{"豆瓣评分","DoubanRating"},{"随机","Random"}};
    private boolean canDiscover;
    private HorizontalScrollView menu;
    private ScrollView homeScroll;
    private View heroSpacer;
    private View previousFocus;
    private int menuTransitionKey;
    private FocusBackdrop homeBackdrop;
    private LinearLayout latestSections;
    private String latestLibraries="";
    private LinearLayout resumeArea;
    private HomeCache homeCache;
    private HomeUpdates homeUpdates;
    private final android.os.Handler homeTimer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.Map<String,Runnable> homeReloads=new java.util.LinkedHashMap<>();
    private final java.util.Map<String,String> renderedShelves=new java.util.HashMap<>();
    private final java.util.Set<String> pendingShelves=new java.util.HashSet<>();
    private final java.util.Map<String,Integer> shelfTickets=new java.util.HashMap<>();
    private int shelfSerial;
    private int homeGeneration;
    private boolean foreground,createdHome=true;
    private final Runnable fallbackRefresh=new Runnable(){@Override public void run(){refreshCurrent();homeTimer.postDelayed(this,60000);}};
    private final Runnable notifiedRefresh=()->refreshCurrent();
    private View resumeFirst,libraryFirst;
    private boolean resumeReady,libraryReady,initialFocusChosen;
    private final List<View> homeCards=new java.util.ArrayList<>();
    private final java.util.Map<View,JSONObject> actionItems=new java.util.HashMap<>();
    private final List<View> resumeCards=new java.util.ArrayList<>();
    private final java.util.Map<String,View> libraryCards=new java.util.HashMap<>();
    private String libraryOrigin="home",pendingLibraryFocus="";
    private boolean carouselEnabled,carouselActive;
    private final List<JSONObject> carouselItems=new java.util.ArrayList<>();
    private int carouselIndex;
    private ImageView carouselPrefetch;
    private final Runnable carouselRotate=new Runnable(){@Override public void run(){if(!carouselActive||!"home".equals(mode))return;if(!homeBackdrop.actionView().hasFocus())showCarouselItem(carouselIndex+1);homeTimer.postDelayed(this,8000);}};
    private static boolean updateChecked;
    private Updater.Release pendingUpdate;
    private java.io.File pendingApk;
    private boolean installing;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        homeCache=new HomeCache(this,api);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(INK);homeBackdrop=new FocusBackdrop(this);stage.addView(homeBackdrop,new FrameLayout.LayoutParams(-1,-1));
        homeBackdrop.actionView().setOnKeyListener((view,key,event)->{
            if(!carouselActive)return false;
            if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){showCarouselItem(carouselIndex+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1));homeTimer.removeCallbacks(carouselRotate);homeTimer.postDelayed(carouselRotate,8000);}
            return true;
        });
        homeScroll=new FocusScroll.Vertical(this);homeScroll.setFillViewport(true);homeScroll.setClipToPadding(false);
        content=column();homeScroll.addView(content);stage.addView(homeScroll,new FrameLayout.LayoutParams(-1,-1));
        homeScroll.setOnScrollChangeListener((View view,int x,int y,int oldX,int oldY)->{maybeLoadPosterPage();});
        nav=row();nav.setGravity(Gravity.CENTER_VERTICAL);nav.setBaselineAligned(false);nav.setPadding(dp(12),dp(8),dp(12),dp(8));
        menu=new HorizontalScrollView(this);menu.setHorizontalScrollBarEnabled(false);menu.setBackground(capsuleSurface(TvAppearance.surface(appearanceDark())));menu.addView(nav,new FrameLayout.LayoutParams(-2,-1));menu.setVisibility(View.GONE);
        FrameLayout.LayoutParams menuBounds=new FrameLayout.LayoutParams(-2,dp(68),Gravity.TOP|Gravity.CENTER_HORIZONTAL);menuBounds.topMargin=dp(12);stage.addView(menu,menuBounds);
        status=text("",13,MUTED);status.setPadding(dp(24),dp(4),dp(24),dp(4));
        FrameLayout.LayoutParams notice=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);stage.addView(status,notice);
        setContentView(stage);refreshNavigation();
        handleLaunchIntent(getIntent());checkUpdateOnce();
        stage.getViewTreeObserver().addOnGlobalFocusChangeListener((previous,focused)->{updateHomeBackdropFocus();maybeLoadPosterPage();});
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleLaunchIntent(intent);}
    private void handleLaunchIntent(Intent intent){
        if(intent.hasExtra("collection")){try{libraryOrigin="detail";openCollection(new JSONObject(intent.getStringExtra("collection")));}catch(Exception error){loadHome();}}
        else if(intent.hasExtra("facetKind")){try{JSONObject library=new JSONObject().put("Id","facet").put("Name",intent.getStringExtra("facetValue")).put("SourceType","facet");openLibrary(library);libraryOrigin="detail";}catch(Exception error){loadHome();}}
        else {loadHome();String target=intent.getStringExtra("mainMenuTarget");if(target!=null&&!target.isEmpty())homeTimer.postDelayed(()->{if("library".equals(target))loadLibraries();else if("favorites".equals(target))openFavorites();else if("search".equals(target))search();},120);}
    }
    @Override void nav(String title,Runnable click){super.nav(title,()->{hideMenu();click.run();});}
    private void showMenu(){previousFocus=getCurrentFocus();homeScroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);animateMenuIn(menu);if(nav.getChildCount()>0)nav.getChildAt(0).requestFocus();}
    private void hideMenu(){menu.animate().cancel();menu.setTranslationY(-dp(84));menu.setVisibility(View.GONE);homeScroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);if(previousFocus==null||!previousFocus.isShown()||!previousFocus.requestFocus())content.requestFocus(View.FOCUS_DOWN);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(menuTransitionKey!=0&&event.getKeyCode()==menuTransitionKey){if(event.getAction()==KeyEvent.ACTION_UP)menuTransitionKey=0;return true;}
        if(event.getKeyCode()==KeyEvent.KEYCODE_MENU&&getCurrentFocus()!=null&&(actionItems.containsKey(getCurrentFocus())||"items".equals(mode)&&getCurrentFocus().getTag() instanceof JSONObject)){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)showItemMenu(getCurrentFocus());
            return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&menu.getVisibility()==View.VISIBLE)return true;
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP){
                View focused=getCurrentFocus();View next=focused==null?null:focused.focusSearch(View.FOCUS_UP);
                if(next!=null&&resumeCards.contains(next)){lowerResumeRow(next);return true;}
            }
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&menu.getVisibility()!=View.VISIBLE){View focused=getCurrentFocus();if(focused==null||focused.focusSearch(View.FOCUS_UP)==null){showMenu();menuTransitionKey=event.getKeyCode();return true;}}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN&&menu.getVisibility()==View.VISIBLE){hideMenu();menuTransitionKey=event.getKeyCode();return true;}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN)maybeLoadPosterPage();
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onResume(){
        super.onResume();if(isFinishing())return;foreground=true;
        boolean carouselSetting=new TvPreferences(this,api).smartCarousel();
        if("home".equals(mode)&&carouselSetting!=carouselEnabled)loadHome();
        else if(!createdHome)refreshCurrent();
        createdHome=false;
        if(carouselActive&&"home".equals(mode)){homeTimer.removeCallbacks(carouselRotate);showCarouselItem(carouselIndex);if(carouselItems.size()>1)homeTimer.postDelayed(carouselRotate,8000);}
        homeTimer.postDelayed(fallbackRefresh,60000);
        showUpdatePrompt();
        if(installing){installing=false;if(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls())installUpdate();else status.setText("尚未允许安装更新，可授权后重试。");}
        homeUpdates=new HomeUpdates(api,value->runOnUiThread(()->{
            if(!foreground||isFinishing())return;
            String previous=homeCache.revision();homeCache.revision(value);
            if(!previous.isEmpty()&&!previous.equals(value)){homeGeneration++;homeTimer.removeCallbacks(notifiedRefresh);homeTimer.postDelayed(notifiedRefresh,250);}
        }),()->runOnUiThread(()->{if(foreground){api.session.clear();startActivity(new Intent(this,SetupActivity.class));finish();}}));
        async(()->api.get("/api/tv/auth/check_status"),result->{JSONObject user=result.optJSONObject("user");boolean allowed=user!=null&&user.optBoolean("can_access_discovery");if(canDiscover!=allowed){canDiscover=allowed;refreshNavigation();}});
    }
    @Override protected void onPause(){foreground=false;homeTimer.removeCallbacksAndMessages(null);if(homeUpdates!=null){homeUpdates.close();homeUpdates=null;}super.onPause();}
    @Override void appearanceChanged(){if(menu!=null)menu.setBackground(capsuleSurface(TvAppearance.surface(appearanceDark())));}
    @Override void imageSizeChanged(){if("home".equals(mode))loadHome();else if("search".equals(mode)||"resume".equals(mode)){offset=0;listingLimit=0;loadItems();}else if("items".equals(mode))refreshPosters();else if("group".equals(mode))refreshLibraryGroup();}
    private int homeShelfLimit(int cardWidthDp){return shelfCapacity(cardWidthDp,12,74)+1;}
    private int posterPageSize(){
        boolean landscape=folderMode||viewMode.equals("landscape");
        int count=gridCapacity(posterGrid,homeScroll,landscape?9f/16f:1.5f,folderMode?42:landscape?0:45,6);
        return count+posterGrid.getColumnCount();
    }
    @Override void mediaDeleted(){if("items".equals(mode))refreshPosters();else if("home".equals(mode))refreshHome();else loadItems();}
    @Override void metadataRefreshed(){super.metadataRefreshed();refreshCurrent();}
    private void refreshCurrent(){if("group".equals(mode)){refreshLibraryGroup();return;}if("items".equals(mode))refreshPosters();else if("resume".equals(mode))loadItems();else refreshHome();}
    private void refreshHome(){if(!"home".equals(mode))return;for(Runnable reload:new java.util.ArrayList<>(homeReloads.values()))reload.run();}
    private void homeRequest(String path,int request,java.util.function.Consumer<JSONObject> done){
        if(request!=revision||pendingShelves.contains(path))return;
        JSONObject cached=homeCache.get(path);
        if(cached!=null&&!renderedShelves.containsKey(path)){renderedShelves.put(path,cached.toString());done.accept(cached);}
        pendingShelves.add(path);int generation=homeGeneration,ticket=++shelfSerial;shelfTickets.put(path,ticket);
        async(()->{try{return api.get(path);}catch(EtknApi.AuthException error){throw error;}catch(Exception error){return new JSONObject().put("unavailable",true);}},result->{
            if(request!=revision||!java.util.Objects.equals(shelfTickets.get(path),ticket))return;pendingShelves.remove(path);
            if(!result.optBoolean("unavailable")){homeCache.put(path,result);String json=result.toString();if(!json.equals(renderedShelves.get(path))){renderedShelves.put(path,json);done.accept(result);}}
            else if(!renderedShelves.containsKey(path))done.accept(result);
            if(generation!=homeGeneration){Runnable reload=homeReloads.get(path);if(reload!=null)reload.run();}
        });
    }
    private void refreshNavigation(){
        nav.removeAllViews();FrameLayout account=new FrameLayout(this);account.setBackground(focusBackground());account.setFocusable(true);account.setClickable(true);account.setContentDescription("当前用户头像，按确定切换账号或登录新用户");
        ImageView avatar=new ImageView(this);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(avatar,dp(19));
        FrameLayout.LayoutParams image=new FrameLayout.LayoutParams(dp(38),dp(38),Gravity.CENTER);account.addView(avatar,image);
        LinearLayout.LayoutParams face=new LinearLayout.LayoutParams(dp(52),dp(44));face.setMargins(0,0,dp(8),0);nav.addView(account,face);account.setOnClickListener(view->{hideMenu();showAccounts();});loadAvatar(avatar);
        nav("首页",this::loadHome);nav("媒体库",this::loadLibraries);nav("收藏",this::openFavorites);nav("搜索",this::search);if(canDiscover)nav("影视探索",()->open(DiscoveryActivity.class));nav("设置",()->open(SettingsActivity.class));
    }
    private void loadHome(){
        mode="home";selectedGroup=null;selectedLibrary=null;posterGrid=null;loadingMore=false;int request=++revision;homeReloads.clear();renderedShelves.clear();pendingShelves.clear();content.removeAllViews();content.setPadding(0,0,0,dp(24));homeScroll.scrollTo(0,0);status.setText("");
        homeBackdrop.clear();resumeFirst=null;libraryFirst=null;resumeReady=false;libraryReady=false;initialFocusChosen=false;homeCards.clear();libraryCards.clear();
        resumeCards.clear();actionItems.clear();latestLibraries="";
        stopCarousel();carouselEnabled=new TvPreferences(this,api).smartCarousel();
        int heroHeight=Math.round(getResources().getConfiguration().screenHeightDp*0.55f);
        heroSpacer=new View(this);heroSpacer.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->homeScroll.post(this::restoreHomeFocusPosition));content.addView(heroSpacer,new LinearLayout.LayoutParams(-1,dp(heroHeight)));
        LinearLayout resume=section("继续观看"),libraries=section("我的媒体库"),latest=column();latestSections=latest;
        resumeArea=resume;resume.setPadding(dp(28),dp(8),dp(22),dp(2));resume.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->positionResumeRow());
        latest.setPadding(0,dp(4),0,dp(8));content.addView(latest);
        loadShelf(resume,api.userPath()+"/Items/Resume?Limit="+homeShelfLimit(192),request,true,false,true,"暂无继续观看记录");
        loadShelf(libraries,api.userPath()+"/Views",request,true,true,false,"暂无可访问媒体库");
        if(carouselEnabled){
            String carouselPath=api.userPath()+"/Items/PersonalRecommendations?Limit=12&Fields=Overview";
            homeReloads.put(carouselPath,()->loadCarousel(carouselPath,request));
            loadCarousel(carouselPath,request);
        }
    }
    private void loadLatestByLibrary(LinearLayout target,int request,List<JSONObject> libraries){
        libraries=new java.util.ArrayList<>(libraries);libraries.removeIf(library->"boxsets".equals(library.optString("CollectionType")));
        org.json.JSONArray identity=new org.json.JSONArray();for(JSONObject library:libraries)identity.put(new org.json.JSONArray().put(library.optString("Id")).put(EtknApi.title(library)));
        if(identity.toString().equals(latestLibraries))return;latestLibraries=identity.toString();
        forgetCards(target);target.removeAllViews();
        homeReloads.keySet().removeIf(path->path.contains("/Items/Latest?"));
        renderedShelves.keySet().removeIf(path->path.contains("/Items/Latest?"));
        pendingShelves.removeIf(path->path.contains("/Items/Latest?"));
        shelfTickets.keySet().removeIf(path->path.contains("/Items/Latest?"));
        List<LinearLayout> areas=new java.util.ArrayList<>();
        for(JSONObject library:libraries)areas.add(section("最新"+EtknApi.title(library),target));
        // Keep library order while rendering each completed shelf immediately, with at most two requests.
        for(int index=0;index<Math.min(2,libraries.size());index++)loadLatestSection(libraries,areas,index,request);
    }
    private void loadLatestSection(List<JSONObject> libraries,List<LinearLayout> areas,int index,int request){
        if(request!=revision||index>=libraries.size())return;
        String path=api.userPath()+"/Items/Latest?ParentId="+EtknApi.encode(libraries.get(index).optString("Id"))+"&Limit="+homeShelfLimit(132)+"&IncludeItemTypes="+EtknApi.encode(libraryIncludeTypes(libraries.get(index)));
        homeReloads.put(path,()->loadLatestSection(libraries,areas,index,request));
        homeRequest(path,request,result->{
            if(request!=revision)return;
            LinearLayout area=areas.get(index);String focused=forgetCards(area);while(area.getChildCount()>1)area.removeViewAt(1);area.setVisibility(View.VISIBLE);
            List<JSONObject> items=EtknApi.rows(result);
            if(items.isEmpty()){if(result.optBoolean("unavailable"))area.addView(text("暂时无法加载，请稍后返回首页重试。",14,MUTED));else area.setVisibility(View.GONE);}
            else{
                HorizontalScrollView scroll=new FocusScroll.Row(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(dp(12),dp(12),dp(12),dp(12));GridLayout shelf=new GridLayout(this);shelf.setClipChildren(false);shelf.setClipToPadding(false);shelf.setRowCount(1);shelf.setColumnCount(items.size());scroll.addView(shelf);area.addView(scroll);
                for(JSONObject item:items){View card=mediaCard(shelf,item,()->detail(item),false,false,view->onHomeCardFocused(view,item,false,false));registerHomeCard(card);bindActions(card,item);card.setTag(item.optString("Id"));if(item.optString("Id").equals(focused))card.requestFocus();}
            }
            loadLatestSection(libraries,areas,index+2,request);
        });
    }
    private void showAccounts(){startActivity(new Intent(this,SetupActivity.class).putExtra(SetupActivity.EXTRA_PICK_ACCOUNT,true));}
    private LinearLayout section(String title){return section(title,content);}
    private LinearLayout section(String title,LinearLayout parent){
        LinearLayout area=column();area.setPadding(dp(28),dp(14),dp(22),dp(2));area.addView(text(title,20,TEXT));area.addView(text("正在加载…",13,MUTED));parent.addView(area);return area;
    }
    private void loadShelf(LinearLayout area,String path,int request,boolean landscape,boolean libraries,boolean resumeShelf,String empty){
        homeReloads.put(path,()->loadShelf(area,path,request,landscape,libraries,resumeShelf,empty));
        homeRequest(path,request,result->{
            if(request!=revision)return;boolean focusFirst=resumeShelf&&resumeCards.contains(getCurrentFocus());String focused=forgetCards(area);if(resumeShelf)resumeCards.clear();if(libraries)libraryCards.clear();while(area.getChildCount()>1)area.removeViewAt(1);List<JSONObject> items=EtknApi.rows(result);
            if(libraries)loadLatestByLibrary(latestSections,request,items);
            if(items.isEmpty()){area.addView(text(result.optBoolean("unavailable")?"暂时无法加载，请稍后返回首页重试。":empty,14,MUTED));onHomeShelfLoaded(resumeShelf,null);if(!focused.isEmpty()&&libraryFirst!=null&&libraryFirst.isShown())libraryFirst.requestFocus();return;}
            HorizontalScrollView scroll=new FocusScroll.Row(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(dp(12),dp(12),dp(12),dp(12));GridLayout shelf=new GridLayout(this);shelf.setClipChildren(false);shelf.setClipToPadding(false);shelf.setRowCount(1);shelf.setColumnCount(items.size());scroll.addView(shelf);area.addView(scroll);
            View first=null;
            for(JSONObject item:items){View card=mediaCard(shelf,item,()->{if(libraries)openLibrary(item);else if(resumeShelf)resumePlayback(item);else detail(item);},landscape,resumeShelf,view->onHomeCardFocused(view,item,libraries,resumeShelf));registerHomeCard(card);bindActions(card,item);card.setTag(item.optString("Id"));if(libraries)libraryCards.put(item.optString("Id"),card);if(resumeShelf)resumeCards.add(card);if(first==null)first=card;if(item.optString("Id").equals(focused))card.requestFocus();}
            onHomeShelfLoaded(resumeShelf,first);
            if(resumeShelf)updateHomeBackdropFocus();
            if(focusFirst&&first!=null){first.requestFocus();}else if(!focused.isEmpty()&&items.stream().noneMatch(item->focused.equals(item.optString("Id")))&&first!=null)first.requestFocus();
            area.post(this::positionResumeRow);
        });
    }
    private String forgetCards(View area){
        String focused="";for(View card:new java.util.ArrayList<>(homeCards)){android.view.ViewParent parent=card.getParent();while(parent instanceof View&&parent!=area)parent=parent.getParent();if(parent==area){if(card.hasFocus())focused=String.valueOf(card.getTag());homeCards.remove(card);actionItems.remove(card);}}
        return focused;
    }
    private void positionResumeRow(){
        if(!"home".equals(mode)||resumeFirst==null||resumeArea==null||resumeArea.getHeight()==0||homeScroll.getHeight()==0)return;
        int height=Math.max(0,homeScroll.getHeight()-resumeArea.getHeight());
        if(heroSpacer.getLayoutParams().height!=height){heroSpacer.getLayoutParams().height=height;heroSpacer.requestLayout();if(resumeCards.contains(getCurrentFocus()))homeScroll.post(()->homeScroll.scrollTo(0,0));}
    }
    private void restoreHomeFocusPosition(){
        View focused=getCurrentFocus();if(!"home".equals(mode)||!homeCards.contains(focused))return;
        if(resumeCards.contains(focused))homeScroll.scrollTo(0,0);
        else{android.graphics.Rect bounds=new android.graphics.Rect();focused.getDrawingRect(bounds);focused.requestRectangleOnScreen(bounds,true);}
    }
    private void registerHomeCard(View card){if(card==null)return;if(!initialFocusChosen)card.setFocusable(false);homeCards.add(card);}
    private void lowerResumeRow(View target){
        positionResumeRow();
        homeScroll.post(()->{homeScroll.scrollTo(0,0);target.requestFocus();});
    }
    private void updateHomeBackdropFocus(){
        View focused=getCurrentFocus();boolean heroFocused="home".equals(mode)&&(resumeCards.contains(focused)||focused==homeBackdrop.actionView()||carouselActive&&menu.getVisibility()==View.VISIBLE);homeBackdrop.resumeFocused(heroFocused);resumeTextColor(heroFocused);
    }
    private void resumeTextColor(boolean heroFocused){
        if(resumeArea==null||!"home".equals(mode))return;
        int color=heroFocused?android.graphics.Color.WHITE:TEXT;resumeLabelColor((TextView)resumeArea.getChildAt(0),color);
        for(View card:resumeCards)resumeLabelColor((TextView)((android.view.ViewGroup)card).getChildAt(1),color);
    }
    private void resumeLabelColor(TextView label,int color){label.setTag(R.id.appearance_text,color);label.setTextColor(resolveColor(color));}
    private void onHomeCardFocused(View card,JSONObject item,boolean library,boolean resumeShelf){
        homeBackdrop.resumeFocused(resumeShelf);
        if(resumeShelf&&!carouselActive)homeBackdrop.show(item,library,true);
        if(resumeShelf)homeScroll.post(()->{if(resumeCards.contains(getCurrentFocus()))homeScroll.scrollTo(0,0);});
    }
    private void loadCarousel(String path,int request){
        homeRequest(path,request,result->{if(request!=revision)return;startCarousel(EtknApi.rows(result));});
    }
    private void startCarousel(List<JSONObject> items){
        carouselItems.clear();carouselItems.addAll(items);carouselIndex=0;
        carouselActive=carouselEnabled&&"home".equals(mode)&&!carouselItems.isEmpty();
        homeTimer.removeCallbacks(carouselRotate);
        homeBackdrop.actionVisible(carouselActive);homeBackdrop.actionView().setFocusable(initialFocusChosen);updateHomeBackdropFocus();
        if(!carouselActive)return;
        showCarouselItem(0);
        if(carouselItems.size()>1)homeTimer.postDelayed(carouselRotate,8000);
    }
    private void stopCarousel(){carouselActive=false;carouselItems.clear();homeTimer.removeCallbacks(carouselRotate);homeBackdrop.actionVisible(false);}
    private void showCarouselItem(int target){
        if(carouselItems.isEmpty())return;
        carouselIndex=(target%carouselItems.size()+carouselItems.size())%carouselItems.size();
        homeBackdrop.show(carouselItems.get(carouselIndex),false,true);
        if(carouselItems.size()>1){
            if(carouselPrefetch==null)carouselPrefetch=new ImageView(this);
            Images.backdrop(api,api.artwork(carouselItems.get((carouselIndex+1)%carouselItems.size()),"Backdrop",1280),carouselPrefetch,success->{});
        }
    }
    private void onHomeShelfLoaded(boolean resumeShelf,View first){
        if(resumeShelf){resumeFirst=first;resumeReady=true;}else{libraryFirst=first;libraryReady=true;}
        if(initialFocusChosen)return;
        if(!pendingLibraryFocus.isEmpty()&&!libraryReady)return;
        if(pendingLibraryFocus.isEmpty()&&!resumeReady)return;
        if(resumeFirst==null&&libraryFirst==null&&(!resumeReady||!libraryReady))return;
        initialFocusChosen=true;for(View card:homeCards)card.setFocusable(true);homeBackdrop.actionView().setFocusable(true);
        View target=libraryCards.get(pendingLibraryFocus);pendingLibraryFocus="";if(target==null)target=resumeFirst!=null?resumeFirst:libraryFirst;if(target!=null)target.requestFocus();else content.requestFocus(View.FOCUS_DOWN);
    }
    private void search(){EditText field=input("搜索片库",false);new AlertDialog.Builder(this).setTitle("搜索片库").setView(field).setPositiveButton("搜索",(dialog,which)->{query=field.getText().toString();parent="";selectedLibrary=null;offset=0;listingLimit=0;mode="search";loadItems();}).setNegativeButton("取消",null).show();}
    private void openLibrary(JSONObject item){if("group".equals(item.optString("SourceType"))){groupOrigin=mode;selectedGroup=item;loadLibraryGroup();return;}libraryOrigin=mode;selectedLibrary=item;activeCollection=null;folderMode=false;folderId="";folderTitle="";folderTrail.clear();restorePosterId="";restorePosterCount=0;parent=item.optString("Id");TvPreferences prefs=new TvPreferences(this,api);String[] sorting=prefs.librarySort(parent);sortBy=sorting[0];sortOrder=sorting[1];viewMode=prefs.libraryView(parent);playMode=prefs.libraryPlayMode(parent);folderMode=physicalLibrary()&&prefs.libraryFolders(parent);typeFilter="";typeKind="";favoriteOnly=false;mode="items";offset=0;loadItems();}
    private void loadLibraries(){mode="library";selectedGroup=null;selectedLibrary=null;posterGrid=null;hasMore=false;loadingMore=false;stopCarousel();homeBackdrop.clear();libraryCards.clear();homeScroll.scrollTo(0,0);int request=++revision;status.setText("正在读取媒体库…");async(()->api.get(api.userPath()+"/Views"),result->{if(request!=revision)return;content.removeAllViews();content.setPadding(dp(28),dp(24),dp(28),dp(18));List<JSONObject> items=EtknApi.rows(result);status.setText(items.isEmpty()?"暂无可访问媒体库":"选择媒体库");GridLayout grid=grid(content,192,12,56);for(JSONObject item:items){View card=mediaCard(grid,item,()->openLibrary(item));libraryCards.put(item.optString("Id"),card);}View target=libraryCards.get(pendingLibraryFocus);pendingLibraryFocus="";if(target==null&&grid.getChildCount()>0)target=grid.getChildAt(0);if(target!=null)target.requestFocus();});}
    private void refreshLibraryGroup(){
        View focused=getCurrentFocus();for(java.util.Map.Entry<String,View> entry:libraryCards.entrySet())if(entry.getValue()==focused){pendingLibraryFocus=entry.getKey();break;}loadLibraryGroup();
    }
    private void loadLibraryGroup(){
        if(selectedGroup==null)return;
        mode="group";selectedLibrary=null;posterGrid=null;hasMore=false;loadingMore=false;stopCarousel();homeBackdrop.clear();libraryCards.clear();homeScroll.scrollTo(0,0);int request=++revision;
        content.removeAllViews();content.setPadding(dp(28),dp(24),dp(28),dp(18));content.addView(text(selectedGroup.optString("Name","媒体库分组"),22,TEXT));status.setText("正在读取子媒体库…");
        String path=api.userPath()+"/Items?ParentId="+EtknApi.encode(selectedGroup.optString("Id"));
        async(()->api.get(path),result->{
            if(request!=revision)return;
            List<JSONObject> items=EtknApi.rows(result);status.setText(items.isEmpty()?"暂无可访问媒体库":"共 "+items.size()+" 个媒体库");GridLayout grid=grid(content,192,12,56);
            for(JSONObject item:items){View card=mediaCard(grid,item,()->openLibrary(item));card.setTag(item.optString("Id"));libraryCards.put(item.optString("Id"),card);bindActions(card,item);}
            View target=libraryCards.get(pendingLibraryFocus);pendingLibraryFocus="";if(target==null&&grid.getChildCount()>0)target=grid.getChildAt(0);if(target!=null)target.requestFocus();
        });
    }
    private void loadItems(){
        stopCarousel();
        if("items".equals(mode)){loadPosterPage(true);return;}
        homeBackdrop.clear();int request=++revision;status.setText("正在加载…");homeScroll.scrollTo(0,0);
        content.removeAllViews();content.setPadding(dp(28),dp(24),dp(28),dp(18));GridLayout grid=grid();
        afterLayout(grid,()->{
            if(request!=revision)return;
            if(offset==0||listingLimit==0)listingLimit=gridCapacity(grid,homeScroll,1.5f,46,5)+grid.getColumnCount();
            int limit=listingLimit,start=offset;
            String path=mode.equals("resume")?api.userPath()+"/Items/Resume?Limit="+limit:api.userPath()+"/Items?Recursive=true&IncludeItemTypes=Movie,Series&Limit="+limit+"&StartIndex="+start+"&SortBy=SortName&Fields=PrimaryImageAspectRatio,Overview"+(parent.isEmpty()?"":"&ParentId="+EtknApi.encode(parent))+(mode.equals("search")?"&SearchTerm="+EtknApi.encode(query):"");
            async(()->api.get(path),result->{if(request!=revision)return;List<JSONObject> items=EtknApi.rows(result);int total=result.optInt("TotalRecordCount",items.size());status.setText(items.isEmpty()?"暂无影片":"共 "+total+" 项  ·  第 "+(start/limit+1)+" 页");for(JSONObject item:items)mediaCard(grid,item,()->{if(mode.equals("resume"))resumePlayback(item);else detail(item);});LinearLayout pages=row();if(start>0)pages.addView(button("上一页",()->{offset=Math.max(0,start-limit);loadItems();}));if(!mode.equals("resume")&&start+items.size()<total)pages.addView(button("下一页",()->{offset=start+items.size();loadItems();}));content.addView(pages);});
        });
    }
    private void maybeLoadPosterPage(){
        if(!"items".equals(mode)||posterGrid==null||posterGrid.getChildCount()==0||!hasMore||loadingMore||refreshingPosters||menu.getVisibility()==View.VISIBLE)return;
        if(posterGrid.isLayoutRequested()){int request=revision;afterLayout(posterGrid,()->{if(request==revision)maybeLoadPosterPage();});return;}
        int focused=posterGrid.indexOfChild(getCurrentFocus()),columns=Math.max(1,posterGrid.getColumnCount());
        boolean lastRow=focused>=0&&focused/columns==(posterGrid.getChildCount()-1)/columns;
        if(lastRow||homeScroll.getScrollY()+homeScroll.getHeight()>=content.getHeight()-prefetchDistance(posterGrid))loadPosterPage(false);
    }
    private void loadPosterPage(boolean reset){
        if(selectedLibrary==null&&activeCollection==null)return;
        if(reset){offset=0;posterGrid=null;loadingMore=false;refreshingPosters=false;pendingPosterRefresh=false;hasMore=true;++revision;content.removeAllViews();content.setPadding(dp(28),dp(20),dp(28),dp(24));homeScroll.scrollTo(0,0);homeBackdrop.clear();
            LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);LinearLayout labels=column();TextView name=text(activeCollection!=null?activeCollection.optString("Name","合集"):folderMode&&!folderTitle.isEmpty()?folderTitle:selectedLibrary.optString("Name","媒体库"),22,TEXT);name.setTypeface(null,android.graphics.Typeface.BOLD);labels.addView(name);libraryMeta=text("正在读取媒体项…",13,MUTED);labels.addView(libraryMeta);heading.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout actions=row();
            playModeButton=button("播放 ▾",this::choosePlayMode);
            if(!collectionLibrary())actions.addView(playModeButton);
            viewModeButton=button(viewLabel(),this::toggleView);viewModeButton.setVisibility(folderMode?View.GONE:View.VISIBLE);actions.addView(viewModeButton);
            if(activeCollection==null&&!collectionLibrary()&&!"facet".equals(selectedLibrary.optString("SourceType"))&&!"favorites".equals(selectedLibrary.optString("SourceType"))){
                actions.addView(button("类型",this::chooseType));
                String collection=selectedLibrary.optString("CollectionType");
                if("movies".equals(collection)||"tvshows".equals(collection))actions.addView(button("tvshows".equals(collection)?"播出平台":"出品公司",()->openFacets("tvshows".equals(collection)?"Networks":"Companies","tvshows".equals(collection)?"播出平台":"出品公司")));
            }
            if(physicalLibrary())actions.addView(button(folderMode?"文件夹 · 已开启":"文件夹",this::toggleFolders));
            if(!folderMode&&!collectionLibrary())actions.addView(button(sortLabel(),this::chooseSort));
            HorizontalScrollView actionScroll=new FocusScroll.Row(this);actionScroll.setHorizontalScrollBarEnabled(false);actionScroll.addView(actions);heading.addView(actionScroll);
            content.addView(heading,new LinearLayout.LayoutParams(-1,-2));
            posterGrid=new GridLayout(this);posterGrid.setClipChildren(false);posterGrid.setClipToPadding(false);posterGrid.setPadding(dp(FocusScroll.MARGIN_DP),dp(FocusScroll.MARGIN_DP),dp(FocusScroll.MARGIN_DP),dp(FocusScroll.MARGIN_DP));int initialWidth=folderMode||viewMode.equals("landscape")?192:132,initialGap=folderMode||viewMode.equals("landscape")?12:6;watchGridColumns(posterGrid,initialWidth,initialGap,56+2*FocusScroll.MARGIN_DP);LinearLayout.LayoutParams gridSpace=new LinearLayout.LayoutParams(-1,-2);gridSpace.topMargin=dp(14);content.addView(posterGrid,gridSpace);posterStatus=text("",13,MUTED);posterStatus.setGravity(Gravity.CENTER);content.addView(posterStatus,new LinearLayout.LayoutParams(-1,dp(38)));
            int request=revision;afterLayout(posterGrid,()->{if(request==revision)loadPosterPage(false);});return;
        }
        if(loadingMore||refreshingPosters||posterGrid==null||!hasMore)return;
        loadingMore=true;int request=revision;int start=offset;
        String path=posterQuery(posterPageSize(),start);
        posterStatus.setText("正在加载…");
        async(()->{try{return api.get(path);}catch(EtknApi.AuthException error){throw error;}catch(Exception error){return new JSONObject().put("_loadError",error.getMessage()==null?"加载失败":error.getMessage());}},result->{if(request!=revision)return;loadingMore=false;if(result.has("_loadError")){posterStatus.setText(result.optString("_loadError"));return;}List<JSONObject> items=EtknApi.rows(result);int total=result.optInt("TotalRecordCount",start+items.size());episodeCountMode=result.optString("EpisodeCountMode",episodeCountMode);offset=start+items.size();hasMore=!items.isEmpty()&&offset<total;libraryMeta.setText(libraryMetaLabel(total));for(JSONObject item:items)addPoster(item);updatePosterViewButton();posterStatus.setText(offset<total?"已加载 "+offset+" / "+total:"共 "+total+" 项");if(restorePosterCount>0){if(offset<Math.min(restorePosterCount,total)&&hasMore){loadPosterPage(false);return;}restorePosterPosition(request);}else if(start==0&&!items.isEmpty()){View first=posterGrid.getChildAt(0);homeScroll.post(()->{if(request==revision&&first.getParent()==posterGrid&&menu.getVisibility()!=View.VISIBLE){first.requestFocus();homeScroll.scrollTo(0,0);}});}else if(items.isEmpty()&&start==0)posterStatus.setText("暂无媒体项");if(pendingPosterRefresh)refreshPosters();afterLayout(posterGrid,()->{if(request==revision)maybeLoadPosterPage();});});
    }
    private void addPoster(JSONObject item){
        boolean folder="Folder".equals(item.optString("Type"));
        View card=folder?folderCard(item):posterCard(posterGrid,item,()->{if("BoxSet".equals(item.optString("Type"))){rememberCollectionPosition(item);openCollection(item);}else detail(item);},episodeCountMode,viewMode.equals("landscape"));card.setTag(item);
        card.setOnLongClickListener(view->{showItemMenu(view);return true;});
    }
    private void updatePosterViewButton(){
        boolean media=false,folders=false;for(int i=0;i<posterGrid.getChildCount();i++){Object tag=posterGrid.getChildAt(i).getTag();if(tag instanceof JSONObject){if("Folder".equals(((JSONObject)tag).optString("Type")))folders=true;else media=true;}}
        viewModeButton.setVisibility(folderMode&&!media?View.GONE:View.VISIBLE);
        int inset=FocusScroll.MARGIN_DP;
        posterGrid.setPadding(dp(inset),dp(FocusScroll.MARGIN_DP),dp(inset),dp(FocusScroll.MARGIN_DP));
        int cardWidth=folders||viewMode.equals("landscape")?192:132;int gap=folders||viewMode.equals("landscape")?12:6;
        if(media&&viewMode.equals("landscape")){cardWidth=192;gap=12;}
        setAdaptiveGridTarget(posterGrid,cardWidth,gap);
    }
    private View folderCard(JSONObject item){
        LinearLayout card=column();card.setFocusable(true);card.setClickable(true);card.setBackgroundColor(android.graphics.Color.TRANSPARENT);card.setClipChildren(false);card.setClipToPadding(false);card.setContentDescription("文件夹 · "+item.optString("Name"));
        boolean adaptive=isAdaptiveGrid(posterGrid);FrameLayout artwork=new FrameLayout(this);artwork.setBackground(shape(PANEL,0));artwork.setClipToOutline(true);shadow(artwork,4);artwork.setForeground(Images.border(dp(12)));card.addView(artwork,new LinearLayout.LayoutParams(-1,adaptive?dp(108):imageDp(108)));
        if(item.has("ImageTags")){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(image,dp(9));artwork.addView(image,new FrameLayout.LayoutParams(-1,-1));Images.load(api,api.folderArtwork(selectedLibrary.optString("Id"),item,640),image);}else{TextView icon=text("▰",56,android.graphics.Color.rgb(117,203,218));icon.setGravity(Gravity.CENTER);artwork.addView(icon,new FrameLayout.LayoutParams(-1,-1));}
        TextView title=text(item.optString("Name"),12,TEXT);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(0,dp(4),0,0);card.addView(title,new LinearLayout.LayoutParams(-1,adaptive?dp(42):imageDp(42)));scaleImageText(title,12,42);
        if(adaptive){addAdaptiveGridItem(posterGrid,card,12,5);fitArtworkHeight(card,artwork,9f/16f);}
        else{GridLayout.LayoutParams bounds=new GridLayout.LayoutParams();bounds.width=imageDp(192);bounds.height=imageDp(150);bounds.setMargins(imageDp(12),dp(5),imageDp(12),dp(5));posterGrid.addView(card,bounds);imageSizeUpdater(card,()->{((LinearLayout.LayoutParams)artwork.getLayoutParams()).height=imageDp(108);artwork.requestLayout();((LinearLayout.LayoutParams)title.getLayoutParams()).height=imageDp(42);title.requestLayout();GridLayout.LayoutParams layout=(GridLayout.LayoutParams)card.getLayoutParams();layout.width=imageDp(192);layout.height=imageDp(150);layout.setMargins(imageDp(12),dp(5),imageDp(12),dp(5));card.setLayoutParams(layout);});}
        card.setOnFocusChangeListener((view,focused)->{card.setScaleX(focused?FOCUS_SCALE:1f);card.setScaleY(focused?FOCUS_SCALE:1f);card.setTranslationZ(0);shadow(artwork,focused?8:4);artwork.setForeground(focused?focusOutline():Images.border(dp(12)));});card.setOnClickListener(view->enterFolder(item));return card;
    }
    private JSONObject posterPosition(){JSONObject state=new JSONObject();View focus=getCurrentFocus();try{state.put("id",folderId).put("title",folderTitle).put("count",offset).put("scroll",homeScroll.getScrollY()).put("focus",focus!=null&&focus.getTag() instanceof JSONObject?((JSONObject)focus.getTag()).optString("Id"):"");}catch(Exception ignored){}return state;}
    private void enterFolder(JSONObject item){folderTrail.push(posterPosition());folderId=item.optString("Id");folderTitle=item.optString("Name");loadPosterPage(true);}
    private void toggleFolders(){folderMode=!folderMode;new TvPreferences(this,api).saveLibraryFolders(parent,folderMode);folderId="";folderTitle="";folderTrail.clear();restorePosterCount=0;loadPosterPage(true);}
    private void restorePosterPosition(int request){String id=restorePosterId;int y=restorePosterScroll;restorePosterCount=0;restorePosterId="";homeScroll.post(()->{if(request!=revision||menu.getVisibility()==View.VISIBLE)return;for(int i=0;i<posterGrid.getChildCount();i++){View card=posterGrid.getChildAt(i);if(card.getTag() instanceof JSONObject&&id.equals(((JSONObject)card.getTag()).optString("Id"))){card.requestFocus();break;}}homeScroll.scrollTo(0,y);});}
    private void rememberCollectionPosition(JSONObject item){collectionReturnFocus=item.optString("Id");collectionReturnCount=offset;collectionReturnScroll=homeScroll.getScrollY();}
    private boolean collectionLibrary(){return activeCollection==null&&selectedLibrary!=null&&"boxsets".equals(selectedLibrary.optString("CollectionType"));}
    private void refreshPosters(){
        if(!"items".equals(mode)||posterGrid==null)return;
        if(loadingMore||refreshingPosters){pendingPosterRefresh=true;return;}
        refreshingPosters=true;pendingPosterRefresh=false;int request=revision,generation=homeGeneration;
        int limit=posterPageSize(),wanted=Math.max(limit,offset);
        java.util.List<String> paths=new java.util.ArrayList<>();for(int start=0;start<wanted;start+=limit)paths.add(posterQuery(Math.min(limit,wanted-start),start));
        async(()->{try{org.json.JSONArray rows=new org.json.JSONArray();JSONObject page=new JSONObject();for(String path:paths){int remaining=wanted-rows.length();if(remaining<=0)break;int count=Math.min(limit,remaining);page=api.get(path);List<JSONObject> batch=EtknApi.rows(page);for(int i=0;i<Math.min(count,batch.size());i++)rows.put(batch.get(i));if(batch.size()<count||rows.length()>=page.optInt("TotalRecordCount",Integer.MAX_VALUE))break;}return page.put("Items",rows);}
            catch(EtknApi.AuthException error){throw error;}catch(Exception error){return new JSONObject().put("_loadError",error.getMessage());}},result->{
            if(request!=revision||!"items".equals(mode))return;refreshingPosters=false;
            if(!result.has("_loadError")){
                View focused=getCurrentFocus();int index=posterGrid.indexOfChild(focused),y=homeScroll.getScrollY();String id=focused!=null&&focused.getTag() instanceof JSONObject?((JSONObject)focused.getTag()).optString("Id"):"";
                List<JSONObject> rows=EtknApi.rows(result);int total=result.optInt("TotalRecordCount",rows.size());episodeCountMode=result.optString("EpisodeCountMode",episodeCountMode);
                java.util.Map<String,View> previous=new java.util.HashMap<>();for(int i=0;i<posterGrid.getChildCount();i++){View card=posterGrid.getChildAt(i);previous.put(((JSONObject)card.getTag()).optString("Id"),card);}posterGrid.removeAllViews();View target=null;
                for(JSONObject row:rows){View old=previous.get(row.optString("Id"));if(old!=null&&old.getTag().toString().equals(row.toString()))posterGrid.addView(old);else addPoster(row);View card=posterGrid.getChildAt(posterGrid.getChildCount()-1);if(id.equals(row.optString("Id")))target=card;}
                updatePosterViewButton();offset=rows.size();hasMore=offset<total;libraryMeta.setText(libraryMetaLabel(total));posterStatus.setText(offset<total?"已加载 "+offset+" / "+total:"共 "+total+" 项");
                if(target==null&&index>=0&&posterGrid.getChildCount()>0)target=posterGrid.getChildAt(Math.min(index,posterGrid.getChildCount()-1));if(target!=null)target.requestFocus();
                if(index>=0&&posterGrid.getChildCount()==0)content.requestFocus(View.FOCUS_UP);homeScroll.scrollTo(0,y);
            }
            if(generation!=homeGeneration)pendingPosterRefresh=true;
            if(pendingPosterRefresh)refreshPosters();
        });
    }
    private void showItemMenu(View card){
        if(mediaActionBusy||openingItemMenu||itemMenu!=null&&itemMenu.isShowing())return;
        JSONObject item=actionItems.get(card);if(item==null&&card.getTag() instanceof JSONObject)item=(JSONObject)card.getTag();if(item==null)return;
        JSONObject selected=item;boolean library=item.optString("Id").startsWith("library-"),resume=resumeCards.contains(card)||"resume".equals(mode),latest="home".equals(mode)&&!library&&!resume;
        if("group".equals(item.optString("SourceType")))return;
        if("Folder".equals(item.optString("Type"))){showFolderMenu(item);return;}
        if("collection".equals(item.optString("SourceType")))return;
        int request=revision;openingItemMenu=true;
        async(()->{try{return api.get("/api/tv/Users/Me");}finally{runOnUiThread(()->openingItemMenu=false);}},user->{
            if(!foreground||request!=revision)return;
            JSONObject policy=user.optJSONObject("Policy"),permissions=user.optJSONObject("TvPermissions"),data=selected.optJSONObject("UserData");
            boolean admin=policy!=null&&policy.optBoolean("IsAdministrator");
            List<String> labels=new java.util.ArrayList<>(),actions=new java.util.ArrayList<>();
            if(!library){
                labels.add(data!=null&&data.optBoolean("IsFavorite")?"取消收藏":"收藏");actions.add("favorite");
                if(latest){labels.add("播放");actions.add("play");}else{labels.add(data!=null&&data.optBoolean("Played")?"标记未播放":"标记已播放");actions.add("played");}
                if(resume){labels.add("从继续观看中移除");actions.add("hide");}
            }
            if(library||selected.optString("Id").startsWith("item-")){
                if((!library||!"personal_recommendation".equals(selected.optString("SourceType")))&&(admin||permissions!=null&&permissions.optBoolean("CanRefreshMetadata"))){labels.add("刷新");actions.add("refresh");}
                if(!library&&(admin||permissions!=null&&permissions.optBoolean("CanDeleteMedia"))){labels.add("删除");actions.add("delete");}
                if(library&&admin&&java.util.Arrays.asList("path","list").contains(selected.optString("SourceType"))){labels.add("扫描媒体库");actions.add("scan");}
            }
            if(labels.isEmpty()){status.setText("当前账号没有媒体库管理权限");return;}
            itemMenu=PlayerChoiceDialog.create(this,EtknApi.title(selected),labels.toArray(new String[0]),-1,(dialog,index)->{
                dialog.dismiss();String action=actions.get(index);
                if(action.equals("refresh"))itemMenu=MediaActions.refresh(this,value->submitItemAction(selected,value));
                else if(action.equals("delete"))itemMenu=MediaActions.delete(this,selected,()->submitItemAction(selected,"delete"));
                else if(action.equals("play"))startActivity(new Intent(this,PlayerActivity.class).putExtra("id",selected.optString("Id")).putExtra("title",EtknApi.title(selected)).putExtra("queue",new String[]{selected.optString("Id")}).putExtra("resume",true));
                else submitItemAction(selected,action);
            },320);itemMenu.show();
        });
    }
    private void bindActions(View card,JSONObject item){actionItems.put(card,item);card.setOnLongClickListener(view->{showItemMenu(view);return true;});}
    private void showFolderMenu(JSONObject item){
        openingItemMenu=true;int request=revision;async(()->{try{return api.get("/api/tv/Users/Me");}finally{runOnUiThread(()->openingItemMenu=false);}},user->{if(request!=revision||!foreground)return;JSONObject policy=user.optJSONObject("Policy"),permissions=user.optJSONObject("TvPermissions");if(!(policy!=null&&policy.optBoolean("IsAdministrator"))&&!(permissions!=null&&permissions.optBoolean("CanRefreshMetadata"))){status.setText("当前账号没有生成封面权限");return;}
            itemMenu=PlayerChoiceDialog.create(this,item.optString("Name"),new String[]{"生成封面"},-1,(dialog,index)->{dialog.dismiss();mediaActionBusy=true;status.setText("正在生成目录封面…");String path=api.userPath()+"/Libraries/"+EtknApi.encode(selectedLibrary.optString("Id"))+"/FolderCover?FolderId="+EtknApi.encode(item.optString("Id"));async(()->{try{return api.post(path,new JSONObject());}finally{runOnUiThread(()->mediaActionBusy=false);}},result->{status.setText("目录封面已生成");refreshPosters();});},320);itemMenu.show();});
    }
    private void submitItemAction(JSONObject item,String action){
        if(mediaActionBusy)return;mediaActionBusy=true;JSONObject data=item.optJSONObject("UserData");
        boolean state=action.equals("favorite")||action.equals("played");boolean current=data!=null&&data.optBoolean(action.equals("favorite")?"IsFavorite":"Played");
        String path=state?api.userPath()+"/"+(action.equals("favorite")?"FavoriteItems":"PlayedItems")+"/"+EtknApi.encode(item.optString("Id")):action.equals("hide")?api.userPath()+"/Items/"+EtknApi.encode(item.optString("Id"))+"/HideFromResume":"/api/tv/Items/"+EtknApi.encode(item.optString("Id"));
        async(()->{try{return state?(current?api.delete(path):api.post(path,new JSONObject())):action.equals("delete")?api.delete(path):api.post(path+(action.equals("hide")?"":action.equals("scan")?"/Scan":"/Refresh?Action="+action),new JSONObject());}
            finally{runOnUiThread(()->mediaActionBusy=false);}},result->{if(state){JSONObject updated=item.optJSONObject("UserData");if(updated==null)updated=new JSONObject();for(java.util.Iterator<String> keys=result.keys();keys.hasNext();){String key=keys.next();try{updated.put(key,result.get(key));}catch(Exception ignored){}}try{item.put("UserData",updated);}catch(Exception ignored){}}status.setText(state?"状态已更新":action.equals("hide")?"已从继续观看中移除":action.equals("scan")?"媒体库扫描任务已提交":action.equals("delete")?"删除任务已提交":"");if(!state&&!action.equals("hide")&&!action.equals("scan")&&!action.equals("delete"))refreshSubmitted(item.optString("Id"),result);refreshCurrent();});
    }
    @Override protected void onDestroy(){if(itemMenu!=null)itemMenu.dismiss();super.onDestroy();}
    private String posterQuery(int limit,int start){
        if(folderMode)return folderQuery(limit,start,false);
        if(selectedLibrary!=null&&"favorites".equals(selectedLibrary.optString("SourceType")))return api.userPath()+"/Browse?Filters=IsFavorite&Limit="+limit+"&StartIndex="+start+"&SortBy="+EtknApi.encode(sortBy)+"&SortOrder="+sortOrder;
        if(selectedLibrary!=null&&"facet".equals(selectedLibrary.optString("SourceType")))return api.userPath()+"/Browse?Limit="+limit+"&StartIndex="+start+"&SortBy="+EtknApi.encode(sortBy)+"&SortOrder="+sortOrder+"&ParentId="+EtknApi.encode(getIntent().hasExtra("facetParent")?getIntent().getStringExtra("facetParent"):"")+"&"+getIntent().getStringExtra("facetKind")+"="+EtknApi.encode(getIntent().getStringExtra("facetValue"));
        String includeTypes=activeCollection!=null?"Movie":collectionLibrary()?"BoxSet":libraryIncludeTypes(selectedLibrary);
        return api.userPath()+"/Items?Recursive=true&ParentId="+EtknApi.encode(parent)+"&IncludeItemTypes="+EtknApi.encode(includeTypes)+"&Limit="+limit+"&StartIndex="+start+"&SortBy="+EtknApi.encode(sortBy)+"&SortOrder="+sortOrder+"&Fields=PrimaryImageAspectRatio,Overview,CommunityRating,TMDbRating,DoubanRating,ProductionYear,RecursiveItemCount,UserData"+(typeKind.isEmpty()?"":"&"+typeKind+"="+EtknApi.encode(typeFilter))+(favoriteOnly?"&Filters=IsFavorite":"");
    }
    private String folderQuery(int limit,int start,boolean recursive){return api.userPath()+"/Libraries/"+EtknApi.encode(selectedLibrary.optString("Id"))+"/Folders?FolderId="+EtknApi.encode(folderId)+"&Limit="+limit+"&StartIndex="+start+(recursive?"&Recursive=true":"")+(typeKind.isEmpty()?"":"&"+typeKind+"="+EtknApi.encode(typeFilter))+(favoriteOnly?"&Filters=IsFavorite":"");}
    private String libraryMetaLabel(int total){
        if(activeCollection!=null)return "共 "+total+" 项  ·  合集";
        if(collectionLibrary())return "共 "+total+" 个合集";
        if(folderMode)return "共 "+total+" 项  ·  文件夹";
        String label="共 "+total+" 项  ·  "+mediaTypeLabel(selectedLibrary)+"  ·  "+sourceTypeLabel(selectedLibrary);
        if(!typeFilter.isEmpty())label+="  ·  类型 "+typeFilter;
        if(favoriteOnly)label+="  ·  收藏";
        return label;
    }
    private static String libraryIncludeTypes(JSONObject library){
        return switch(library.optString("CollectionType","mixed")){case "movies"->"Movie";case "tvshows"->"Series";case "homevideos"->"Video";default->"Movie,Series,Video";};
    }
    private String mediaTypeLabel(JSONObject library){return switch(library.optString("CollectionType","mixed")){case "movies"->"电影";case "tvshows"->"剧集";case "homevideos"->"家庭视频";default->"混合";};}
    private String sourceTypeLabel(JSONObject library){return switch(library.optString("SourceType","")){case "path"->"实体库";case "filter"->"筛选库";case "list"->"榜单库";case "personal_recommendation"->"推荐库";case "shared"->"共享库";default->"媒体库";};}
    private String sortLabel(){if(sortBy.isEmpty())return "排序 · 默认";String label=sortBy;for(String[] item:SORTS)if(item[1].equalsIgnoreCase(sortBy)){label=item[0];break;}return "排序 · "+label+(sortBy.equalsIgnoreCase("Random")?"":" "+("Descending".equals(sortOrder)?"降序":"升序"));}
    private void setSort(String field,String direction){sortBy=field;sortOrder=direction;new TvPreferences(this,api).saveLibrarySort(parent,field,direction);loadPosterPage(true);}
    private void chooseSort(){String[][] sorts=selectedLibrary!=null&&"facet".equals(selectedLibrary.optString("SourceType"))?FACET_SORTS:SORTS;String[] labels=new String[sorts.length];int selected=0;for(int i=0;i<sorts.length;i++){labels[i]=sorts[i][0];if(sorts[i][1].equalsIgnoreCase(sortBy))selected=i;}new AlertDialog.Builder(this).setTitle("排序方式").setSingleChoiceItems(labels,selected,(dialog,index)->{dialog.dismiss();String field=sorts[index][1];if(field.equals("Random")){setSort(field,"Ascending");return;}String[] directions={"降序","升序"};int direction="Descending".equals(sortOrder)?0:1;new AlertDialog.Builder(this).setTitle(labels[index]).setSingleChoiceItems(directions,direction,(choice,value)->{choice.dismiss();setSort(field,value==0?"Descending":"Ascending");}).setNegativeButton("取消",null).show();}).setNegativeButton("取消",null).show();}
    private boolean physicalLibrary(){return activeCollection==null&&selectedLibrary!=null&&"path".equals(selectedLibrary.optString("SourceType"));}
    private String playModeLabel(){return "模式 · "+(playMode.equals("random")?"随机播放":"顺序播放");}
    private String viewLabel(){return "视图 · "+(viewMode.equals("landscape")?"横幅":"竖幅");}
    private String typeLabel(){return typeFilter.isEmpty()?"类型":"类型 · "+typeFilter;}
    private String favoriteLabel(){return favoriteOnly?"收藏 · 已开启":"收藏";}
    private void togglePlayMode(){playMode=playMode.equals("random")?"sequence":"random";new TvPreferences(this,api).saveLibraryPlayMode(parent,playMode);if(playModeButton!=null)playModeButton.setText(playModeLabel());}
    private void toggleView(){viewMode=viewMode.equals("landscape")?"portrait":"landscape";new TvPreferences(this,api).saveLibraryView(parent,viewMode);if("collections".equals(mode))openCollections();else loadPosterPage(true);}
    private void toggleFavoriteFilter(){favoriteOnly=!favoriteOnly;loadPosterPage(true);}
    private void chooseType(){
        openFacets("Types","类型");
    }
    private void openFacets(String kind,String title){startActivity(new Intent(this,FacetActivity.class).putExtra("parent",selectedLibrary.optString("Id")).putExtra("kind",kind).putExtra("title",title));}
    private void openFavorites(){try{openLibrary(new JSONObject().put("Id","favorites").put("Name","收藏").put("SourceType","favorites").put("CollectionType","mixed"));}catch(Exception error){status.setText("无法打开收藏");}}
    private void choosePlayMode(){
        PopupMenu popup=new PopupMenu(this,playModeButton);popup.getMenu().add(0,0,0,"顺序播放");popup.getMenu().add(0,1,1,"随机播放");
        popup.setOnMenuItemClickListener(item->{playMode=item.getItemId()==1?"random":"sequence";new TvPreferences(this,api).saveLibraryPlayMode(parent,playMode);playQueue();return true;});popup.show();
    }
    private void openCollections(){
        if(selectedLibrary==null)return;
        mode="collections";activeCollection=null;posterGrid=null;int request=++revision;content.removeAllViews();content.setPadding(dp(28),dp(24),dp(28),dp(18));homeScroll.scrollTo(0,0);status.setText("正在读取合集…");
        async(()->api.get(api.userPath()+"/Libraries/"+EtknApi.encode(selectedLibrary.optString("Id"))+"/Collections?Limit=200"),result->{
            if(request!=revision)return;
            List<JSONObject> items=EtknApi.rows(result);status.setText(items.isEmpty()?"该媒体库暂无合集":"合集 · "+selectedLibrary.optString("Name","媒体库"));
            if(items.isEmpty())return;
            GridLayout grid=grid(content,viewMode.equals("landscape")?192:132,viewMode.equals("landscape")?12:6,80);grid.setPadding(dp(FocusScroll.MARGIN_DP),dp(FocusScroll.MARGIN_DP),dp(FocusScroll.MARGIN_DP),dp(FocusScroll.MARGIN_DP));
            for(JSONObject item:items)posterCard(grid,item,()->openCollection(item),episodeCountMode,viewMode.equals("landscape"));
            if(grid.getChildCount()>0)grid.getChildAt(0).requestFocus();
        });
    }
    private void openCollection(JSONObject item){activeCollection=item;parent=item.optString("Id");sortBy="";sortOrder="Ascending";typeFilter="";typeKind="";favoriteOnly=false;mode="items";offset=0;loadPosterPage(true);}
    private void playQueue(){
        if(activeCollection==null&&selectedLibrary==null)return;
        posterStatus.setText("正在准备播放队列…");
        async(()->api.get(folderMode?folderQuery(1000,0,true):posterQuery(1000,0)),result->{
            List<JSONObject> items=EtknApi.rows(result);
            items.removeIf(item->!item.optString("Id").startsWith("item-"));
            if(items.isEmpty()){posterStatus.setText("没有可播放的媒体项");return;}
            int total=result.optInt("TotalRecordCount",items.size());
            posterStatus.setText(total>items.size()?"队列截取前 "+items.size()+" / "+total+" 项":"队列共 "+items.size()+" 项");
            if("random".equals(playMode))java.util.Collections.shuffle(items);
            String[] ids=new String[items.size()];for(int i=0;i<ids.length;i++)ids[i]=items.get(i).optString("Id");
            JSONObject first=items.get(0);String type=first.optString("Type");
            Intent intent=new Intent(this,PlayerActivity.class).putExtra("id",first.optString("Id")).putExtra("title",EtknApi.resumeTitle(first)).putExtra("position",DetailPresentation.position(first)/10000).putExtra("resume",true).putExtra("queue",ids).putExtra("queueIndex",0).putExtra("resolved",!"Series".equals(type)&&!"Season".equals(type));
            startActivity(intent);
        });
    }
    private void checkUpdateOnce(){
        if(updateChecked)return;updateChecked=true;
        async(()->{try{return Updater.check();}catch(Exception error){return null;}},release->{if(release==null||release.code<=BuildConfig.VERSION_CODE)return;pendingUpdate=release;showUpdatePrompt();});
    }
    private void showUpdatePrompt(){
        if(pendingUpdate==null||!foreground||isFinishing())return;
        Updater.Release release=pendingUpdate;pendingUpdate=null;
        new AlertDialog.Builder(this).setTitle("发现新版本 "+release.version).setMessage(release.notes+"\n\n大小："+(release.size/1024/1024)+" MB").setPositiveButton("下载更新",(dialog,index)->{status.setText("开始下载…");async(()->Updater.download(this,release,percent->runOnUiThread(()->{if(!isDestroyed())status.setText("正在下载更新 "+percent+"%");})),apk->{pendingApk=apk;status.setText("签名和文件校验通过，可以安装。");installUpdate();});}).setNegativeButton("暂不",null).show();
    }
    private void installUpdate(){
        if(pendingApk==null)return;
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){installing=true;startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;}
        installing=false;
        try{Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",pendingApk);startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception error){status.setText("此电视未提供安装器，请从下载目录手动安装。");}
    }
    @Override public void onBackPressed(){
        if(menu.getVisibility()==View.VISIBLE){hideMenu();return;}
        if("items".equals(mode)&&folderMode&&!folderTrail.isEmpty()){JSONObject previous=folderTrail.pop();folderId=previous.optString("id");folderTitle=previous.optString("title");restorePosterId=previous.optString("focus");restorePosterCount=previous.optInt("count",60);restorePosterScroll=previous.optInt("scroll");loadPosterPage(true);return;}
        if("items".equals(mode)&&activeCollection!=null&&selectedLibrary!=null&&"boxsets".equals(selectedLibrary.optString("CollectionType"))){activeCollection=null;parent=selectedLibrary.optString("Id");restorePosterId=collectionReturnFocus;restorePosterCount=collectionReturnCount;restorePosterScroll=collectionReturnScroll;loadPosterPage(true);return;}
        if("items".equals(mode)&&"detail".equals(libraryOrigin)){finish();return;}
        if("items".equals(mode)&&activeCollection!=null&&selectedLibrary!=null){parent=selectedLibrary.optString("Id");openCollections();return;}
        if("collections".equals(mode)&&selectedLibrary!=null){TvPreferences prefs=new TvPreferences(this,api);parent=selectedLibrary.optString("Id");String[] sorting=prefs.librarySort(parent);sortBy=sorting[0];sortOrder=sorting[1];viewMode=prefs.libraryView(parent);playMode=prefs.libraryPlayMode(parent);typeFilter="";typeKind="";favoriteOnly=false;mode="items";offset=0;loadPosterPage(true);return;}
        if("items".equals(mode)&&selectedLibrary!=null){pendingLibraryFocus=selectedLibrary.optString("Id");if("group".equals(libraryOrigin)&&selectedGroup!=null)loadLibraryGroup();else if("library".equals(libraryOrigin))loadLibraries();else loadHome();return;}
        if("group".equals(mode)&&selectedGroup!=null){pendingLibraryFocus=selectedGroup.optString("Id");if("library".equals(groupOrigin))loadLibraries();else loadHome();return;}
        if(!mode.equals("home")){loadHome();return;}
        super.onBackPressed();
    }
}
