package xyz.etkn.tv;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.app.AlertDialog;
import org.json.*;
import java.util.*;

public final class MusicActivity extends TvActivity {
    private static final String[] TABS={"recommended","albums","artists","songs","videos","folders","playlists"};
    private static final String[] LABELS={"推荐","专辑","歌手","歌曲","音乐视频","文件夹","歌单"};
    private String parent="",tab="recommended",key="",title="音乐";
    private String sort="DateCreated",order="Descending",shuffleSeed=UUID.randomUUID().toString();
    private final List<JSONObject> tracks=new ArrayList<>();
    private final List<JSONObject> displayedVideos = new ArrayList<>();
    private int revision,offset,total;
    private boolean loading,tracksOnly,loaded,pageFailed,albumHasVideos;
    private android.os.CancellationSignal pageRequest;
    private android.os.CancellationSignal playRequest;
    private boolean listPlaying;
    private HorizontalScrollView sortBar;
    private Button sortButton,orderButton,sequenceButton,shuffleButton;
    private GridLayout listing;
    private Button more;
    private JSONObject artist;
    private LinearLayout musicMenu;
    private HorizontalScrollView mainMenu;
    private View previousFocus;
    private int menuTransitionKey;
    static void open(Context context,String parent,String tab,String key,String title){context.startActivity(new Intent(context,MusicActivity.class).putExtra("parent",parent).putExtra("tab",tab).putExtra("key",key).putExtra("title",title));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Intent intent=getIntent();parent=value(intent,"parent","");tab=value(intent,"tab","recommended");key=value(intent,"key","");title=value(intent,"title","音乐");
        albumHasVideos=intent.getBooleanExtra("videos",false);
        FrameLayout stage=new FrameLayout(this);
        screenScroll=new FocusScroll.Vertical(this);screenScroll.setFillViewport(true);screenScroll.setClipToPadding(false);
        root=column();root.setPadding(dp(36),dp(20),dp(36),dp(28));screenScroll.addView(root);stage.addView(screenScroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);TextView label=text("音乐库",25,TEXT);label.setTypeface(null,android.graphics.Typeface.BOLD);label.setPadding(dp(8),0,dp(24),0);heading.addView(label);
        HorizontalScrollView navigation=new FocusScroll.Row(this);navigation.setHorizontalScrollBarEnabled(false);navigation.setClipToPadding(false);navigation.setPadding(dp(8),dp(10),dp(8),dp(10));musicMenu=row();navigation.addView(musicMenu);heading.addView(navigation,new LinearLayout.LayoutParams(0,-2,1));root.addView(heading);
        for(int i=0;i<TABS.length;i++){final int at=i;Button choice=button(LABELS[i],()->{tab=TABS[at];key="";tracksOnly=false;load();});choice.setBackground(transparentFocusBackground());musicMenu.addView(choice);}
        sortBar=new FocusScroll.Row(this);sortBar.setHorizontalScrollBarEnabled(false);sortBar.setClipToPadding(false);sortBar.setPadding(dp(8),dp(6),dp(8),dp(6));LinearLayout sorting=row();sortBar.addView(sorting);root.addView(sortBar);
        sortButton=button("",this::chooseSort);orderButton=button("",()->{order=order.equals("Ascending")?"Descending":"Ascending";load();});
        sequenceButton=button("顺序播放",()->playList(false));shuffleButton=button("随机播放",()->playList(true));
        sorting.addView(sortButton);sorting.addView(orderButton);sorting.addView(sequenceButton);sorting.addView(shuffleButton);
        status=text("",13,MUTED);status.setPadding(dp(8),dp(4),dp(8),dp(4));root.addView(status);content=column();content.setPadding(dp(8),dp(8),dp(8),dp(16));root.addView(content);
        nav=row();nav.setGravity(Gravity.CENTER_VERTICAL);nav.setBaselineAligned(false);nav.setPadding(dp(12),dp(8),dp(12),dp(8));
        mainMenu=new HorizontalScrollView(this);mainMenu.setHorizontalScrollBarEnabled(false);mainMenu.setBackground(capsuleSurface(TvAppearance.surface(appearanceDark())));mainMenu.addView(nav,new FrameLayout.LayoutParams(-2,-1));mainMenu.setVisibility(View.GONE);
        FrameLayout.LayoutParams menuBounds=new FrameLayout.LayoutParams(-2,dp(68),Gravity.TOP|Gravity.CENTER_HORIZONTAL);menuBounds.topMargin=dp(12);stage.addView(mainMenu,menuBounds);
        mainMenuItem("首页",()->openHome("home"));mainMenuItem("媒体库",()->openHome("library"));mainMenuItem("音乐",()->{tab="recommended";key="";tracksOnly=false;load();});mainMenuItem("收藏",()->openHome("favorites"));mainMenuItem("搜索",()->openHome("search"));mainMenuItem("设置",()->open(SettingsActivity.class));
        screenScroll.setOnScrollChangeListener((view,x,y,oldX,oldY)->nextPageIfNeeded());
        root.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->nextPageIfNeeded());
        setContentView(stage);musicMenu.getChildAt(Math.max(0,Arrays.asList(TABS).indexOf(tab))).requestFocus();
        async(()->api.get("/api/tv/auth/check_status"),result->{JSONObject user=result.optJSONObject("user");if(user!=null&&user.optBoolean("can_access_discovery")){int index=nav.getChildCount()-1;mainMenuItem("影视探索",()->open(DiscoveryActivity.class));View entry=nav.getChildAt(nav.getChildCount()-1);nav.removeView(entry);nav.addView(entry,index);}});
        load();
    }
    private static String value(Intent intent,String key,String fallback){String value=intent.getStringExtra(key);return value==null?fallback:value;}
    private boolean sortable(){return key.isEmpty()&&Arrays.asList("albums","artists","songs","videos").contains(tab);}
    private String path(){return path(false);}
    private String path(boolean shuffle){
        String sorting="&SortBy=SortName&SortOrder=Ascending";
        if(shuffle)sorting="&SortBy=Random&SortOrder=Ascending&ShuffleSeed="+UUID.randomUUID();
        else if(sortable())sorting="&SortBy="+sort+"&SortOrder="+order+"&ShuffleSeed="+shuffleSeed;
        return api.userPath()+"/Music?ParentId="+EtknApi.encode(parent)+"&Tab="+tab+"&Key="+EtknApi.encode(key)+sorting+(tracksOnly?"&Tracks=1&AudioOnly=1":"");
    }
    private void updateSorting(){
        sortBar.setVisibility(sortable()?View.VISIBLE:View.GONE);
        sortButton.setText("排序 · "+(sort.equals("DateCreated")?"最新入库":sort.equals("Random")?"随机":"名称"));orderButton.setText("顺序 · "+(order.equals("Ascending")?"升序":"降序"));
        sequenceButton.setEnabled(!listPlaying&&total>0);shuffleButton.setEnabled(!listPlaying&&total>0);
    }
    private void chooseSort(){
        String[] values={"SortName","DateCreated","Random"};
        AlertDialog dialog=dialogBuilder().setTitle("音乐排序").setSingleChoiceItems(new String[]{"名称","最新入库","随机"},Arrays.asList(values).indexOf(sort),(choice,index)->{sort=values[index];shuffleSeed=UUID.randomUUID().toString();choice.dismiss();load();}).setNegativeButton("取消",null).create();dialog.show();themeDialog(dialog);
    }
    private void load(){
        if(playRequest!=null)playRequest.cancel();playRequest=null;listPlaying=false;
        displayedVideos.clear();
        revision++;if(pageRequest!=null)pageRequest.cancel();pageRequest=null;offset=0;total=0;tracks.clear();artist=null;loading=false;pageFailed=false;listing=null;more=null;content.removeAllViews();status.setText("正在读取音乐…");
        updateSorting();
        for(int i=0;i<TABS.length;i++){Button choice=(Button)musicMenu.getChildAt(i);themeText(choice,TABS[i].equals(tab)?ACCENT:TEXT);choice.setTypeface(null,TABS[i].equals(tab)?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);}
        if(tab.equals("recommended")){recommendations();return;}
        if(tab.equals("playlists")&&key.isEmpty()){playlists();return;}
        if(tab.equals("albums")&&!key.isEmpty()&&!albumHasVideos){playAlbum(key,title);return;}
        listing=grid(content,tracksOnly||tab.equals("songs")||tab.equals("videos")||tab.equals("folders")||!key.isEmpty()&&!tab.equals("artists")?300:140,12,104);
        listing.setPadding(0,dp(8),0,dp(12));more=button("重试加载",this::page);more.setVisibility(View.GONE);content.addView(more);page();
    }
    private void recommendations(){int ticket=revision;async(()->api.get(path()+"&FrequentLimit="+Math.min(200,Math.max(6,shelfCapacity(300,12,88)*2))),result->{if(ticket!=revision)return;content.removeAllViews();status.setText("推荐音乐");
        for(String section:new String[]{"Latest","Recent","Frequent"}){
            content.addView(text(section.equals("Latest")?"最新专辑":section.equals("Recent")?"最近听的":"最近常听",20,TEXT));
            JSONArray data=result.optJSONArray(section);List<JSONObject> items=items(data);boolean flat=!section.equals("Latest");
            if(items.isEmpty()){content.addView(text(flat?"暂无播放记录":"暂无专辑",14,MUTED));continue;}
            GridLayout shelf;
            if(flat)shelf=grid(content,300,12,104);
            else{HorizontalScrollView row=new FocusScroll.Row(this);row.setHorizontalScrollBarEnabled(false);row.setClipToPadding(false);row.setPadding(dp(12),dp(12),dp(12),dp(12));shelf=new GridLayout(this);shelf.setRowCount(1);shelf.setColumnCount(items.size());shelf.setClipChildren(false);shelf.setClipToPadding(false);row.addView(shelf);content.addView(row);}
            shelf.setPadding(0,dp(6),0,dp(14));
            for(JSONObject item:items){View card=MusicViews.card(this,shelf,item,()->{if(flat)play(item,items,"");else openGroup(item);},flat);if(flat)bindTrack(card,item);}
        }
    });}
    private void playlists(){int ticket=revision;async(()->api.get(path()),result->{if(ticket!=revision)return;status.setText("我的歌单");content.addView(button("新建歌单",()->MusicActions.createPlaylist(this,created->load())));JSONArray data=result.optJSONArray("Playlists");List<JSONObject> lists=items(data);if(lists.isEmpty())content.addView(text("暂无歌单，选择“新建歌单”添加。",16,MUTED));
        GridLayout grid=grid(content,300,12,104);for(JSONObject list:lists){Button row=button(list.optString("Name")+"  ·  "+list.optInt("Count")+" 首",()->open(this,parent,"playlists",list.optString("Key"),list.optString("Name")));row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);addAdaptiveGridItem(grid,row,12,12);row.setOnLongClickListener(view->{deletePlaylist(list);return true;});row.setTag(list);row.setOnKeyListener((view,code,event)->{if(code!=KeyEvent.KEYCODE_MENU)return false;if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)deletePlaylist(list);return true;});}
    });}
    private void nextPageIfNeeded(){
        if(listing==null||more==null||loading||pageFailed||offset>=total||!screenScroll.isShown())return;
        if(root.getHeight()-screenScroll.getScrollY()-screenScroll.getHeight()<=screenScroll.getHeight()/2)page();
    }
    private void page(){
        if(loading||listing==null)return;loading=true;pageFailed=false;more.setVisibility(View.GONE);int ticket=revision,start=offset;
        String address=path()+"&StartIndex="+start+"&Limit=60";android.os.CancellationSignal cancellation=new android.os.CancellationSignal();pageRequest=cancellation;
        IO.execute(()->{try{JSONObject result=api.get(address,cancellation);runOnUiThread(()->{
            if(ticket!=revision||isFinishing()||isDestroyed()||!TvAppearance.sameSession(api,api.userId))return;
            loading=false;pageRequest=null;
            List<JSONObject> rows=EtknApi.rows(result);total=result.optInt("TotalRecordCount",start+rows.size());offset=start+rows.size();
            if(start==0){JSONObject info=result.optJSONObject("Artist");artist=info;if(info!=null&&!tracksOnly){LinearLayout header=column();header.setPadding(dp(12),dp(8),dp(12),dp(12));TextView bio=text(info.optString("Overview"),14,MUTED);bio.setMaxLines(4);header.addView(bio);header.addView(button("播放歌手全部歌曲",()->{tracksOnly=true;load();}));content.addView(header,0);}
                JSONArray folders=result.optJSONArray("Folders");if(folders!=null&&folders.length()>0){GridLayout folderGrid=grid(content,140,12,104);content.removeView(folderGrid);content.addView(folderGrid,0);for(JSONObject folder:items(folders))MusicViews.card(this,folderGrid,folder,()->openGroup(folder),false);}
            }
            if(start==0){if(tab.equals("albums")&&albumHasVideos&&!rows.isEmpty())content.addView(button("播放专辑歌曲",()->playAlbum(key,title)),0);List<JSONObject> videos=items(result.optJSONArray("Videos"));if(!videos.isEmpty()){LinearLayout section=column();section.addView(text("音乐视频",20,TEXT));GridLayout videoGrid=grid(section,300,12,104);for(JSONObject video:videos)MusicViews.card(this,videoGrid,video,()->playVideo(video,videos),false);content.addView(section);}}
            for(JSONObject item:rows){boolean track=item.optString("Kind").isEmpty()&&"Audio".equals(item.optString("Type"));if(track)tracks.add(item);if(item.optBoolean("MusicVideo"))displayedVideos.add(item);View card=MusicViews.card(this,listing,item,()->{if(item.optBoolean("MusicVideo"))openVideo(item);else if(track)play(item,tracks,path());else openGroup(item);},track);if(track)bindTrack(card,item);}
            if(rows.isEmpty())total=offset;updateSorting();
            int videoCount=result.optJSONArray("Videos")==null?0:result.optJSONArray("Videos").length();status.setText((result.optString("Title").isEmpty()?title:result.optString("Title"))+"  ·  "+(rows.isEmpty()&&start==0?(videoCount>0?videoCount+" 个 MV":"暂无音乐"):"已加载 "+offset+" / "+total));
        });}catch(Exception error){runOnUiThread(()->{
            if(ticket!=revision||cancellation.isCanceled()||isFinishing()||isDestroyed()||!TvAppearance.sameSession(api,api.userId))return;
            loading=false;pageRequest=null;pageFailed=true;more.setVisibility(View.VISIBLE);status.setText(error.getMessage()==null?"音乐加载失败，请重试":error.getMessage());
            if(error instanceof EtknApi.AuthException){api.session.clear();open(SetupActivity.class);finish();}
        });}});
    }
    private static List<JSONObject> items(JSONArray array){List<JSONObject> items=new ArrayList<>();if(array!=null)for(int i=0;i<array.length();i++)if(array.optJSONObject(i)!=null)items.add(array.optJSONObject(i));return items;}
    private String albumPath(String albumKey){return api.userPath()+"/Music?ParentId="+EtknApi.encode(parent)+"&Tab=albums&Key="+EtknApi.encode(albumKey)+"&AudioOnly=1&SortBy=Name&SortOrder=Ascending";}
    private void playAlbum(String albumKey,String albumTitle){
        String albumPath=albumPath(albumKey);int ticket=revision;status.setText("正在打开专辑…");
        async(()->api.get(albumPath+"&StartIndex=0&Limit=200"),result->{
            if(ticket!=revision)return;List<JSONObject> songs=new ArrayList<>();for(JSONObject item:EtknApi.rows(result))if("Audio".equals(item.optString("Type")))songs.add(item);
            if(songs.isEmpty()){status.setText("专辑暂无可播放歌曲");return;}
            status.setText("正在播放 · "+albumTitle);MusicPlayback.get(this).play(songs,songs.get(0),albumPath,result.optInt("TotalRecordCount",songs.size()));open(MusicPlayerActivity.class);
        });
    }
    private void openGroup(JSONObject item){String kind=item.optString("Kind",tab);if(kind.equals("albums")){if(item.optInt("VideoCount")>0){startActivity(new Intent(this,MusicActivity.class).putExtra("parent",parent).putExtra("tab",kind).putExtra("key",item.optString("Key")).putExtra("title",item.optString("Name")).putExtra("videos",true));}else playAlbum(item.optString("Key"),item.optString("Name"));return;}open(this,parent,kind,item.optString("Key"),item.optString("Name"));}
    private void openVideo(JSONObject item) {
        if (tab.equals("videos") && sortable() && offset < total) playList(false, item);
        else playVideo(item, displayedVideos);
    }
    private void playVideo(JSONObject item, List<JSONObject> videos) {
        String id = item.optString("Id");
        Map<String, String> queue = PlaybackSelection.musicVideoQueue(item, videos);
        String[] ids = queue.keySet().toArray(new String[0]);
        MusicPlayback.stop();
        startActivity(new Intent(this, PlayerActivity.class).putExtra("id", id).putExtra("title", item.optString("Name"))
                .putExtra("queue", ids).putExtra("musicVideoQueueTitles", queue.values().toArray(new String[0]))
                .putExtra("queueIndex", Arrays.asList(ids).indexOf(id)).putExtra("resolved", true));
    }
    private void playList(boolean shuffle) { playList(shuffle, null); }
    private void playList(boolean shuffle, JSONObject selected){
        if(!sortable()||listPlaying||total==0)return;
        boolean videoList=tab.equals("videos");int ticket=revision;
        String endpoint=path(shuffle)+"&Tracks=1";
        android.os.CancellationSignal cancellation=new android.os.CancellationSignal();playRequest=cancellation;listPlaying=true;updateSorting();
        IO.execute(()->{try{
            List<JSONObject> items=new ArrayList<>();Set<String> seen=new LinkedHashSet<>();int count=-1;
            do{
                if(cancellation.isCanceled()||!TvAppearance.sameSession(api,api.userId))return;
                JSONObject result=api.get(endpoint+"&StartIndex="+items.size()+"&Limit=200",cancellation);
                int current=result.optInt("TotalRecordCount");if(count>=0&&count!=current)throw new IllegalStateException("播放清单发生变化，请重试");count=current;
                List<JSONObject> page=EtknApi.rows(result);if(page.isEmpty()&&items.size()<count)throw new IllegalStateException("播放清单不完整，请重试");
                for(JSONObject item:page){String id=item.optString("Id");if(id.isEmpty()||!seen.add(id))throw new IllegalStateException("播放清单发生变化，请重试");items.add(item);}
            }while(items.size()<count);
            if(items.isEmpty()||items.size()!=count)throw new IllegalStateException("暂无可播放内容或清单已变化，请重试");
            runOnUiThread(()->{
                if(ticket!=revision||cancellation.isCanceled()||isFinishing()||isDestroyed()||!TvAppearance.sameSession(api,api.userId))return;
                listPlaying=false;playRequest=null;updateSorting();
                JSONObject first=items.get(0);
                if(videoList){
                    playVideo(selected == null ? first : selected, selected == null || seen.contains(selected.optString("Id")) ? items : displayedVideos);
                }else{
                    MusicPlayback playback=MusicPlayback.get(this);playback.mode(0);
                    playback.play(items,first,"",items.size());open(MusicPlayerActivity.class);
                }
            });
        }catch(Exception error){runOnUiThread(()->{
            if(ticket!=revision||cancellation.isCanceled()||isFinishing()||isDestroyed()||!TvAppearance.sameSession(api,api.userId))return;
            listPlaying=false;playRequest=null;updateSorting();status.setText(error.getMessage()==null?"播放清单读取失败，请重试":error.getMessage());
            if(error instanceof EtknApi.AuthException){api.session.clear();open(SetupActivity.class);finish();}
            else if (selected != null) playVideo(selected, displayedVideos);
        });}});
    }
    private void bindTrack(View card,JSONObject item){card.setTag(item);card.setOnLongClickListener(view->{MusicActions.trackMenu(this,item,parent,this::refreshAfterAction);return true;});card.setOnKeyListener((view,code,event)->{if(code!=KeyEvent.KEYCODE_MENU)return false;if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)MusicActions.trackMenu(this,item,parent,this::refreshAfterAction);return true;});}
    private void refreshAfterAction(){if(tab.equals("playlists"))load();}
    private void play(JSONObject item,List<JSONObject> items,String context){MusicPlayback.get(this).play(new ArrayList<>(items),item,context,context.isEmpty()?items.size():total);open(MusicPlayerActivity.class);}
    private void mainMenuItem(String label,Runnable click){super.nav(label,()->{hideMainMenu();click.run();});}
    private void openHome(String target){startActivity(new Intent(this,HomeActivity.class).putExtra("mainMenuTarget",target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    private void showMainMenu(){previousFocus=getCurrentFocus();musicMenu.setVisibility(View.INVISIBLE);screenScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);animateMenuIn(mainMenu);nav.getChildAt(0).requestFocus();}
    private void hideMainMenu(){mainMenu.animate().cancel();mainMenu.setTranslationY(-dp(84));mainMenu.setVisibility(View.GONE);musicMenu.setVisibility(View.VISIBLE);screenScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);if(previousFocus==null||!previousFocus.isShown()||!previousFocus.requestFocus())musicMenu.getChildAt(0).requestFocus();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(menuTransitionKey!=0&&event.getKeyCode()==menuTransitionKey){if(event.getAction()==KeyEvent.ACTION_UP)menuTransitionKey=0;return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP){if(mainMenu.getVisibility()==View.VISIBLE)return true;View focused=getCurrentFocus();if(focused==null||focused.focusSearch(View.FOCUS_UP)==null){showMainMenu();menuTransitionKey=event.getKeyCode();return true;}}
            if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN&&mainMenu.getVisibility()==View.VISIBLE){hideMainMenu();menuTransitionKey=event.getKeyCode();return true;}
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){if(mainMenu.getVisibility()==View.VISIBLE){hideMainMenu();return;}super.onBackPressed();}
    @Override void appearanceChanged(){if(mainMenu!=null)mainMenu.setBackground(capsuleSurface(TvAppearance.surface(appearanceDark())));}
    private void deletePlaylist(JSONObject list){AlertDialog dialog=dialogBuilder().setTitle("删除歌单“"+list.optString("Name")+"”？").setMessage("只删除歌单，保留音乐文件。").setPositiveButton("删除",(choice,index)->async(()->api.delete(api.userPath()+"/Music/Playlists/"+EtknApi.encode(list.optString("Key"))),result->load())).setNegativeButton("取消",null).create();dialog.show();themeDialog(dialog);}
    @Override protected void onResume(){super.onResume();MusicPlayback.get(this);if(loaded&&(tab.equals("recommended")||tab.equals("playlists")))load();loaded=true;}
    @Override protected void onPause(){if(playRequest!=null){playRequest.cancel();playRequest=null;listPlaying=false;updateSorting();}super.onPause();}
    @Override protected void onDestroy(){revision++;if(pageRequest!=null)pageRequest.cancel();if(playRequest!=null)playRequest.cancel();super.onDestroy();}
    @Override void imageSizeChanged(){if(listing!=null||tab.equals("recommended"))load();}
}
