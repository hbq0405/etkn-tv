package xyz.etkn.tv;

import android.os.Bundle;
import android.view.View;
import android.view.KeyEvent;
import android.view.Gravity;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.*;
import org.json.JSONObject;
import java.util.List;

public final class HomeActivity extends TvActivity {
    private String parent="",query="",mode="home";
    private int offset,revision;
    private org.json.JSONObject selectedLibrary;
    private GridLayout posterGrid;
    private TextView libraryMeta,posterStatus;
    private boolean loadingMore,hasMore;
    private String episodeCountMode="unplayed",sortBy="SortName",sortOrder="Ascending";
    private static final String[][] SORTS={
        {"名称","SortName"},{"名称（显示名）","Name"},{"最近添加","DateAdded"},{"创建时间","DateCreated"},
        {"最近修改","DateModified"},{"最近内容更新","DateLastContentAdded"},{"最近媒体更新","DateLastMediaAdded"},
        {"最近内容首播","LastContentPremiereDate"},{"首播日期","PremiereDate"},{"年份","ProductionYear"},
        {"文件大小","Size"},{"最近播放","DatePlayed"},{"播放次数","PlayCount"},{"TMDb评分","TMDbRating"},
        {"豆瓣评分","DoubanRating"},{"影评评分","CriticRating"},{"IMDb评分","ImdbRating"},
        {"分级","OfficialRating"},{"时长","Runtime"},{"时长刻度","RunTimeTicks"},
        {"集数","IndexNumber"},{"季数","ParentIndexNumber"},{"随机","Random"}
    };
    private boolean canDiscover;
    private HorizontalScrollView menu;
    private ScrollView homeScroll;
    private View heroSpacer;
    private View previousFocus;
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
    private final Runnable fallbackRefresh=new Runnable(){@Override public void run(){refreshHome();homeTimer.postDelayed(this,60000);}};
    private final Runnable notifiedRefresh=()->refreshHome();
    private View resumeFirst,libraryFirst;
    private boolean resumeReady,libraryReady,initialFocusChosen;
    private final List<View> homeCards=new java.util.ArrayList<>();
    private final List<View> resumeCards=new java.util.ArrayList<>();
    private final java.util.Map<String,View> libraryCards=new java.util.HashMap<>();
    private String libraryOrigin="home",pendingLibraryFocus="";
    private boolean carouselEnabled,carouselActive;
    private final List<JSONObject> carouselItems=new java.util.ArrayList<>();
    private int carouselIndex;
    private ImageView carouselPrefetch;
    private final Runnable carouselRotate=new Runnable(){@Override public void run(){if(!carouselActive||!"home".equals(mode))return;showCarouselItem(carouselIndex+1);homeTimer.postDelayed(this,8000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        homeCache=new HomeCache(this,api);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(INK);homeBackdrop=new FocusBackdrop(this);stage.addView(homeBackdrop,new FrameLayout.LayoutParams(-1,-1));
        homeScroll=new FocusScroll.Vertical(this);homeScroll.setFillViewport(true);homeScroll.setClipToPadding(false);
        content=column();homeScroll.addView(content);stage.addView(homeScroll,new FrameLayout.LayoutParams(-1,-1));
        homeScroll.setOnScrollChangeListener((View view,int x,int y,int oldX,int oldY)->{if("items".equals(mode)&&posterGrid!=null&&hasMore&&!loadingMore&&homeScroll.getChildAt(0)!=null&&y+homeScroll.getHeight()>=homeScroll.getChildAt(0).getHeight()-dp(420))loadPosterPage(false);});
        nav=row();nav.setPadding(dp(28),dp(8),dp(18),dp(8));
        menu=new HorizontalScrollView(this);menu.setHorizontalScrollBarEnabled(false);menu.setBackgroundColor(android.graphics.Color.argb(245,11,17,26));menu.addView(nav);menu.setVisibility(View.GONE);
        stage.addView(menu,new FrameLayout.LayoutParams(-1,dp(76),Gravity.TOP));
        status=text("",13,MUTED);status.setPadding(dp(24),dp(4),dp(24),dp(4));
        FrameLayout.LayoutParams notice=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);stage.addView(status,notice);
        setContentView(stage);refreshNavigation();loadHome();
    }
    @Override void nav(String title,Runnable click){super.nav(title,()->{hideMenu();click.run();});}
    private void showMenu(){previousFocus=getCurrentFocus();menu.setVisibility(View.VISIBLE);if(nav.getChildCount()>0)nav.getChildAt(0).requestFocus();}
    private void hideMenu(){menu.setVisibility(View.GONE);if(previousFocus==null||!previousFocus.isShown()||!previousFocus.requestFocus())content.requestFocus(View.FOCUS_DOWN);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP){
                View focused=getCurrentFocus();View next=focused==null?null:focused.focusSearch(View.FOCUS_UP);
                if(next!=null&&resumeCards.contains(next)){lowerResumeRow(next);return true;}
            }
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&menu.getVisibility()!=View.VISIBLE){View focused=getCurrentFocus();if(focused==null||focused.focusSearch(View.FOCUS_UP)==null){showMenu();return true;}}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN&&menu.getVisibility()==View.VISIBLE){hideMenu();return true;}
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onResume(){
        super.onResume();foreground=true;
        boolean carouselSetting=new TvPreferences(this,api).smartCarousel();
        if("home".equals(mode)&&carouselSetting!=carouselEnabled)loadHome();
        else if(!createdHome)refreshHome();
        createdHome=false;
        if(carouselActive&&"home".equals(mode)){homeTimer.removeCallbacks(carouselRotate);showCarouselItem(carouselIndex);if(carouselItems.size()>1)homeTimer.postDelayed(carouselRotate,8000);}
        homeTimer.postDelayed(fallbackRefresh,60000);
        homeUpdates=new HomeUpdates(api,value->runOnUiThread(()->{
            if(!foreground||isFinishing())return;
            String previous=homeCache.revision();homeCache.revision(value);
            if(!previous.isEmpty()&&!previous.equals(value)){homeGeneration++;homeTimer.removeCallbacks(notifiedRefresh);homeTimer.postDelayed(notifiedRefresh,250);}
        }),()->runOnUiThread(()->{if(foreground){api.session.clear();startActivity(new Intent(this,SetupActivity.class));finish();}}));
        async(()->api.get("/api/tv/auth/check_status"),result->{JSONObject user=result.optJSONObject("user");boolean allowed=user!=null&&user.optBoolean("can_access_discovery");if(canDiscover!=allowed){canDiscover=allowed;refreshNavigation();}});
    }
    @Override protected void onPause(){foreground=false;homeTimer.removeCallbacksAndMessages(null);if(homeUpdates!=null){homeUpdates.close();homeUpdates=null;}super.onPause();}
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
        LinearLayout.LayoutParams face=new LinearLayout.LayoutParams(dp(52),dp(54));face.setMargins(0,dp(4),dp(8),0);nav.addView(account,face);account.setOnClickListener(view->{hideMenu();showAccounts();});loadAvatar(avatar);
        nav("首页",this::loadHome);nav("媒体库",this::loadLibraries);nav("继续观看",()->{mode="resume";selectedLibrary=null;parent="";offset=0;loadItems();});nav("搜索",this::search);if(canDiscover)nav("影视探索",()->open(DiscoveryActivity.class));nav("设置",()->open(SettingsActivity.class));
    }
    private void loadHome(){
        mode="home";selectedLibrary=null;posterGrid=null;loadingMore=false;int request=++revision;homeReloads.clear();renderedShelves.clear();pendingShelves.clear();content.removeAllViews();content.setPadding(0,0,0,dp(24));homeScroll.scrollTo(0,0);status.setText("");
        homeBackdrop.clear();resumeFirst=null;libraryFirst=null;resumeReady=false;libraryReady=false;initialFocusChosen=false;homeCards.clear();libraryCards.clear();
        resumeCards.clear();latestLibraries="";
        stopCarousel();carouselEnabled=new TvPreferences(this,api).smartCarousel();
        int heroHeight=dp(getResources().getConfiguration().screenHeightDp*0.55f);
        heroSpacer=new View(this);heroSpacer.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->homeScroll.post(this::restoreHomeFocusPosition));content.addView(heroSpacer,new LinearLayout.LayoutParams(-1,heroHeight));
        LinearLayout resume=section("继续观看"),libraries=section("我的媒体库"),latest=column();latestSections=latest;
        resumeArea=resume;resume.setPadding(dp(28),dp(8),dp(22),dp(2));resume.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->positionResumeRow());
        latest.setPadding(0,dp(4),0,dp(8));content.addView(latest);
        loadShelf(resume,api.userPath()+"/Items/Resume?Limit=12",request,true,false,true,"暂无继续观看记录");
        loadShelf(libraries,api.userPath()+"/Views",request,true,true,false,"暂无可访问媒体库");
        if(carouselEnabled){
            String carouselPath=api.userPath()+"/Items/PersonalRecommendations?Limit=12&Fields=Overview";
            homeReloads.put(carouselPath,()->loadCarousel(carouselPath,request));
            loadCarousel(carouselPath,request);
        }
    }
    private void loadLatestByLibrary(LinearLayout target,int request,List<JSONObject> libraries){
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
        String path=api.userPath()+"/Items/Latest?ParentId="+EtknApi.encode(libraries.get(index).optString("Id"))+"&Limit=12&IncludeItemTypes="+EtknApi.encode(libraryIncludeTypes(libraries.get(index)));
        homeReloads.put(path,()->loadLatestSection(libraries,areas,index,request));
        homeRequest(path,request,result->{
            if(request!=revision)return;
            LinearLayout area=areas.get(index);String focused=forgetCards(area);while(area.getChildCount()>1)area.removeViewAt(1);area.setVisibility(View.VISIBLE);
            List<JSONObject> items=EtknApi.rows(result);
            if(items.isEmpty()){if(result.optBoolean("unavailable"))area.addView(text("暂时无法加载，请稍后返回首页重试。",14,MUTED));else area.setVisibility(View.GONE);}
            else{
                HorizontalScrollView scroll=new FocusScroll.Row(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(dp(12),dp(12),dp(12),dp(12));GridLayout shelf=new GridLayout(this);shelf.setClipChildren(false);shelf.setClipToPadding(false);shelf.setRowCount(1);shelf.setColumnCount(items.size());scroll.addView(shelf);area.addView(scroll);
                for(JSONObject item:items){View card=mediaCard(shelf,item,()->detail(item),false,false,view->onHomeCardFocused(view,item,false,false));registerHomeCard(card);card.setTag(item.optString("Id"));if(item.optString("Id").equals(focused))card.requestFocus();}
            }
            loadLatestSection(libraries,areas,index+2,request);
        });
    }
    private void showAccounts(){
        List<SavedAccounts.Account> accounts=api.session.accounts();
        if(accounts.isEmpty())return;
        String[] labels=new String[accounts.size()];
        for(int index=0;index<accounts.size();index++){
            SavedAccounts.Account account=accounts.get(index);labels[index]=account.name+(account.userId.equals(api.session.userId())?"（当前）":"");
        }
        new AlertDialog.Builder(this).setTitle("切换用户").setItems(labels,(dialog,index)->{
            SavedAccounts.Account account=accounts.get(index);if(account.userId.equals(api.session.userId()))return;
            try{api.session.activate(account.userId);recreate();}catch(Exception error){status.setText(error.getMessage());}
        }).setNeutralButton("登录新用户",(dialog,index)->startActivity(new Intent(this,SetupActivity.class).putExtra(SetupActivity.EXTRA_ADD_ACCOUNT,true))).setNegativeButton("取消",null).show();
    }
    private LinearLayout section(String title){return section(title,content);}
    private LinearLayout section(String title,LinearLayout parent){
        LinearLayout area=column();area.setPadding(dp(28),dp(14),dp(22),dp(2));area.addView(text(title,20,android.graphics.Color.WHITE));area.addView(text("正在加载…",13,MUTED));parent.addView(area);return area;
    }
    private void loadShelf(LinearLayout area,String path,int request,boolean landscape,boolean libraries,boolean resumeShelf,String empty){
        homeReloads.put(path,()->loadShelf(area,path,request,landscape,libraries,resumeShelf,empty));
        homeRequest(path,request,result->{
            if(request!=revision)return;String focused=forgetCards(area);if(resumeShelf)resumeCards.clear();if(libraries)libraryCards.clear();while(area.getChildCount()>1)area.removeViewAt(1);List<JSONObject> items=EtknApi.rows(result);
            if(libraries)loadLatestByLibrary(latestSections,request,items);
            if(items.isEmpty()){area.addView(text(result.optBoolean("unavailable")?"暂时无法加载，请稍后返回首页重试。":empty,14,MUTED));onHomeShelfLoaded(resumeShelf,null);return;}
            HorizontalScrollView scroll=new FocusScroll.Row(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(dp(12),dp(12),dp(12),dp(12));GridLayout shelf=new GridLayout(this);shelf.setClipChildren(false);shelf.setClipToPadding(false);shelf.setRowCount(1);shelf.setColumnCount(items.size());scroll.addView(shelf);area.addView(scroll);
            View first=null;
            for(JSONObject item:items){View card=mediaCard(shelf,item,()->{if(libraries)openLibrary(item);else if(resumeShelf)resumePlayback(item);else detail(item);},landscape,resumeShelf,view->onHomeCardFocused(view,item,libraries,resumeShelf));registerHomeCard(card);card.setTag(item.optString("Id"));if(libraries)libraryCards.put(item.optString("Id"),card);if(resumeShelf)resumeCards.add(card);if(first==null)first=card;if(item.optString("Id").equals(focused))card.requestFocus();}
            onHomeShelfLoaded(resumeShelf,first);
            area.post(this::positionResumeRow);
        });
    }
    private String forgetCards(View area){
        String focused="";for(View card:new java.util.ArrayList<>(homeCards)){android.view.ViewParent parent=card.getParent();while(parent instanceof View&&parent!=area)parent=parent.getParent();if(parent==area){if(card.hasFocus())focused=String.valueOf(card.getTag());homeCards.remove(card);}}
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
    private void onHomeCardFocused(View card,JSONObject item,boolean library,boolean resumeShelf){
        if(!carouselActive)homeBackdrop.show(item,library,resumeShelf);
        if(resumeShelf)homeScroll.post(()->{if(resumeCards.contains(getCurrentFocus()))homeScroll.scrollTo(0,0);});
    }
    private void loadCarousel(String path,int request){
        homeRequest(path,request,result->{if(request!=revision)return;startCarousel(EtknApi.rows(result));});
    }
    private void startCarousel(List<JSONObject> items){
        carouselItems.clear();carouselItems.addAll(items);carouselIndex=0;
        carouselActive=carouselEnabled&&"home".equals(mode)&&!carouselItems.isEmpty();
        homeTimer.removeCallbacks(carouselRotate);
        if(!carouselActive)return;
        showCarouselItem(0);
        if(carouselItems.size()>1)homeTimer.postDelayed(carouselRotate,8000);
    }
    private void stopCarousel(){carouselActive=false;carouselItems.clear();homeTimer.removeCallbacks(carouselRotate);}
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
        if(resumeFirst==null&&libraryFirst==null&&(!resumeReady||!libraryReady))return;
        initialFocusChosen=true;for(View card:homeCards)card.setFocusable(true);
        View target=libraryCards.get(pendingLibraryFocus);pendingLibraryFocus="";if(target==null)target=resumeFirst!=null?resumeFirst:libraryFirst;if(target!=null)target.requestFocus();else content.requestFocus(View.FOCUS_DOWN);
    }
    private void search(){EditText field=input("搜索片库",false);new AlertDialog.Builder(this).setTitle("搜索片库").setView(field).setPositiveButton("搜索",(dialog,which)->{query=field.getText().toString();parent="";selectedLibrary=null;offset=0;mode="search";loadItems();}).setNegativeButton("取消",null).show();}
    private void openLibrary(JSONObject item){libraryOrigin=mode;selectedLibrary=item;parent=item.optString("Id");String[] sorting=new TvPreferences(this,api).librarySort(parent);sortBy=sorting[0];sortOrder=sorting[1];mode="items";offset=0;loadItems();}
    private void loadLibraries(){mode="library";selectedLibrary=null;posterGrid=null;hasMore=false;loadingMore=false;stopCarousel();homeBackdrop.clear();libraryCards.clear();homeScroll.scrollTo(0,0);int request=++revision;status.setText("正在读取媒体库…");async(()->api.get(api.userPath()+"/Views"),result->{if(request!=revision)return;content.removeAllViews();content.setPadding(dp(28),dp(24),dp(28),dp(18));List<JSONObject> items=EtknApi.rows(result);status.setText(items.isEmpty()?"暂无可访问媒体库":"选择媒体库");GridLayout grid=grid();grid.setColumnCount(Math.max(2,(getResources().getConfiguration().screenWidthDp-56)/272));for(JSONObject item:items){View card=mediaCard(grid,item,()->openLibrary(item));libraryCards.put(item.optString("Id"),card);}View target=libraryCards.get(pendingLibraryFocus);pendingLibraryFocus="";if(target==null&&grid.getChildCount()>0)target=grid.getChildAt(0);if(target!=null)target.requestFocus();});}
    private void loadItems(){
        stopCarousel();
        if("items".equals(mode)){loadPosterPage(true);return;}
        homeBackdrop.clear();int request=++revision;status.setText("正在加载…");homeScroll.scrollTo(0,0);
        String path=mode.equals("resume")?api.userPath()+"/Items/Resume?Limit=60":api.userPath()+"/Items?Recursive=true&IncludeItemTypes=Movie,Series&Limit=60&StartIndex="+offset+"&SortBy=SortName&Fields=PrimaryImageAspectRatio,Overview"+(parent.isEmpty()?"":"&ParentId="+EtknApi.encode(parent))+(mode.equals("search")?"&SearchTerm="+EtknApi.encode(query):"");
        async(()->api.get(path),result->{if(request!=revision)return;content.removeAllViews();content.setPadding(dp(28),dp(24),dp(28),dp(18));List<JSONObject> items=EtknApi.rows(result);int total=result.optInt("TotalRecordCount",items.size());status.setText(items.isEmpty()?"暂无影片":"共 "+total+" 项  ·  第 "+(offset/60+1)+" 页");GridLayout grid=grid();for(JSONObject item:items)mediaCard(grid,item,()->{if(mode.equals("resume"))resumePlayback(item);else detail(item);});LinearLayout pages=row();if(offset>0)pages.addView(button("上一页",()->{offset=Math.max(0,offset-60);loadItems();}));if(!mode.equals("resume")&&offset+items.size()<total)pages.addView(button("下一页",()->{offset+=60;loadItems();}));content.addView(pages);});
    }
    private void loadPosterPage(boolean reset){
        if(selectedLibrary==null)return;
        if(reset){offset=0;posterGrid=null;loadingMore=false;hasMore=true;++revision;content.removeAllViews();content.setPadding(dp(28),dp(20),dp(28),dp(24));homeScroll.scrollTo(0,0);homeBackdrop.clear();
            LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);LinearLayout labels=column();TextView name=text(selectedLibrary.optString("Name","媒体库"),22,android.graphics.Color.WHITE);name.setTypeface(null,android.graphics.Typeface.BOLD);labels.addView(name);libraryMeta=text("正在读取媒体项…",13,MUTED);labels.addView(libraryMeta);heading.addView(labels,new LinearLayout.LayoutParams(0,-2,1));heading.addView(button(sortLabel(),this::chooseSort));content.addView(heading,new LinearLayout.LayoutParams(-1,-2));
            posterGrid=new GridLayout(this);posterGrid.setColumnCount(Math.max(2,(getResources().getConfiguration().screenWidthDp-56)/144));posterGrid.setClipChildren(false);posterGrid.setClipToPadding(false);posterGrid.setPadding(0,dp(FocusScroll.MARGIN_DP),0,dp(FocusScroll.MARGIN_DP));LinearLayout.LayoutParams gridSpace=new LinearLayout.LayoutParams(-1,-2);gridSpace.topMargin=dp(14);content.addView(posterGrid,gridSpace);posterStatus=text("",13,MUTED);posterStatus.setGravity(Gravity.CENTER);content.addView(posterStatus,new LinearLayout.LayoutParams(-1,dp(38)));
        }
        if(loadingMore||posterGrid==null||!hasMore)return;
        loadingMore=true;int request=revision;int start=offset;String includeTypes=libraryIncludeTypes(selectedLibrary);
        String path=api.userPath()+"/Items?Recursive=true&ParentId="+EtknApi.encode(parent)+"&IncludeItemTypes="+EtknApi.encode(includeTypes)+"&Limit=60&StartIndex="+start+"&SortBy="+EtknApi.encode(sortBy)+"&SortOrder="+sortOrder+"&Fields=PrimaryImageAspectRatio,Overview,CommunityRating,TMDbRating,DoubanRating,ProductionYear,RecursiveItemCount,UserData";
        posterStatus.setText("正在加载…");
        async(()->{try{return api.get(path);}catch(EtknApi.AuthException error){throw error;}catch(Exception error){return new JSONObject().put("_loadError",error.getMessage()==null?"加载失败":error.getMessage());}},result->{if(request!=revision)return;loadingMore=false;if(result.has("_loadError")){posterStatus.setText(result.optString("_loadError"));return;}List<JSONObject> items=EtknApi.rows(result);int total=result.optInt("TotalRecordCount",start+items.size());episodeCountMode=result.optString("EpisodeCountMode",episodeCountMode);offset=start+items.size();hasMore=!items.isEmpty()&&offset<total;libraryMeta.setText("共 "+total+" 项  ·  "+mediaTypeLabel(selectedLibrary)+"  ·  "+sourceTypeLabel(selectedLibrary));for(JSONObject item:items)posterCard(posterGrid,item,()->detail(item),episodeCountMode);posterStatus.setText(offset<total?"已加载 "+offset+" / "+total:"共 "+total+" 项");if(start==0&&!items.isEmpty()){View first=posterGrid.getChildAt(0);homeScroll.post(()->{first.requestFocus();homeScroll.scrollTo(0,0);});}else if(items.isEmpty()&&start==0)posterStatus.setText("暂无媒体项");});
    }
    private static String libraryIncludeTypes(JSONObject library){
        return switch(library.optString("CollectionType","mixed")){case "movies"->"Movie";case "tvshows"->"Series";case "homevideos"->"Video";default->"Movie,Series,Video";};
    }
    private String mediaTypeLabel(JSONObject library){return switch(library.optString("CollectionType","mixed")){case "movies"->"电影";case "tvshows"->"剧集";case "homevideos"->"家庭视频";default->"混合";};}
    private String sourceTypeLabel(JSONObject library){return switch(library.optString("SourceType","")){case "path"->"实体库";case "filter"->"筛选库";case "list"->"榜单库";case "personal_recommendation"->"推荐库";case "shared"->"共享库";default->"媒体库";};}
    private String sortLabel(){String label=sortBy;for(String[] item:SORTS)if(item[1].equalsIgnoreCase(sortBy)){label=item[0];break;}return "排序 · "+label+(sortBy.equalsIgnoreCase("Random")?"":" "+("Descending".equals(sortOrder)?"降序":"升序"));}
    private void setSort(String field,String direction){sortBy=field;sortOrder=direction;new TvPreferences(this,api).saveLibrarySort(parent,field,direction);loadPosterPage(true);}
    private void chooseSort(){String[] labels=new String[SORTS.length];int selected=0;for(int i=0;i<SORTS.length;i++){labels[i]=SORTS[i][0];if(SORTS[i][1].equalsIgnoreCase(sortBy))selected=i;}new AlertDialog.Builder(this).setTitle("排序方式").setSingleChoiceItems(labels,selected,(dialog,index)->{dialog.dismiss();String field=SORTS[index][1];if(field.equals("Random")){setSort(field,"Ascending");return;}String[] directions={"降序","升序"};int direction="Descending".equals(sortOrder)?0:1;new AlertDialog.Builder(this).setTitle(labels[index]).setSingleChoiceItems(directions,direction,(choice,value)->{choice.dismiss();setSort(field,value==0?"Descending":"Ascending");}).setNegativeButton("取消",null).show();}).setNegativeButton("取消",null).show();}
    @Override public void onBackPressed(){if(menu.getVisibility()==View.VISIBLE){hideMenu();return;}if("items".equals(mode)&&selectedLibrary!=null){pendingLibraryFocus=parent;if("library".equals(libraryOrigin))loadLibraries();else loadHome();return;}if(!mode.equals("home")){loadHome();return;}super.onBackPressed();}
}
