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
    private static final String[] TABS={"recommended","albums","artists","songs","folders","playlists"};
    private static final String[] LABELS={"推荐","专辑","歌手","歌曲","文件夹","歌单"};
    private String parent="",tab="recommended",key="",search="",sort="Name",order="Ascending",title="音乐";
    private final List<JSONObject> tracks=new ArrayList<>();
    private int revision,offset,total;
    private boolean loading,tracksOnly,loaded;
    private GridLayout listing;
    private Button more;
    private JSONObject artist;
    static void open(Context context,String parent,String tab,String key,String title){context.startActivity(new Intent(context,MusicActivity.class).putExtra("parent",parent).putExtra("tab",tab).putExtra("key",key).putExtra("title",title));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Intent intent=getIntent();parent=value(intent,"parent","");tab=value(intent,"tab","recommended");key=value(intent,"key","");title=value(intent,"title","音乐");
        screen(title,"音乐库  ·  长按确认键或菜单键查看歌曲操作");
        root.setClipChildren(false);root.setClipToPadding(false);screenScroll.setClipToPadding(false);content.setPadding(dp(8),dp(8),dp(8),dp(16));
        int navIndex=root.indexOfChild(nav);root.removeView(nav);HorizontalScrollView navigation=new FocusScroll.Row(this);navigation.setHorizontalScrollBarEnabled(false);navigation.setClipToPadding(false);navigation.setPadding(dp(8),dp(8),dp(8),dp(8));navigation.addView(nav);root.addView(navigation,navIndex);
        nav("返回",this::finish);for(int i=0;i<TABS.length;i++){final int at=i;nav(LABELS[i],()->{tab=TABS[at];key="";search="";tracksOnly=false;load();});}
        LinearLayout actions=row();actions.addView(button("搜索",this::search));actions.addView(button("排序",this::sort));actions.addView(button("播放全部",this::playAll));actions.addView(button("新建歌单",()->MusicActions.createPlaylist(this,result->{tab="playlists";key="";load();})));actions.addView(button("正在播放",()->{if(MusicPlayback.active())open(MusicPlayerActivity.class);else status.setText("先选择一首歌曲");}));root.addView(actions,root.indexOfChild(status));
        load();
    }
    private static String value(Intent intent,String key,String fallback){String value=intent.getStringExtra(key);return value==null?fallback:value;}
    private String path(){return api.userPath()+"/Music?ParentId="+EtknApi.encode(parent)+"&Tab="+tab+"&Key="+EtknApi.encode(key)+"&SearchTerm="+EtknApi.encode(search)+"&SortBy="+sort+"&SortOrder="+order+(tracksOnly?"&Tracks=1":"");}
    private void load(){
        revision++;offset=0;total=0;tracks.clear();artist=null;loading=false;content.removeAllViews();status.setText("正在读取音乐…");
        if(tab.equals("recommended")){recommendations();return;}
        if(tab.equals("playlists")&&key.isEmpty()){playlists();return;}
        listing=grid(content,tracksOnly||tab.equals("songs")||!key.isEmpty()&&!tab.equals("artists")?300:140,12,104);
        listing.setPadding(0,dp(8),0,dp(12));more=button("加载更多",this::page);content.addView(more);page();
    }
    private void recommendations(){int ticket=revision;async(()->api.get(path()+"&FrequentLimit="+Math.min(200,Math.max(6,shelfCapacity(300,12,88)*2))),result->{if(ticket!=revision)return;content.removeAllViews();status.setText("推荐音乐");
        for(String section:new String[]{"Latest","Recent","Frequent"}){
            content.addView(text(section.equals("Latest")?"最新专辑":section.equals("Recent")?"最近听的":"最近常听",20,TEXT));
            JSONArray data=result.optJSONArray(section);List<JSONObject> items=items(data);boolean flat=!section.equals("Latest");
            if(items.isEmpty()){content.addView(text(flat?"暂无播放记录":"暂无专辑",14,MUTED));continue;}
            GridLayout shelf=grid(content,flat?300:140,12,104);shelf.setPadding(0,dp(6),0,dp(14));
            for(JSONObject item:items){View card=MusicViews.card(this,shelf,item,()->{if(flat)play(item,items,"");else openGroup(item);},flat);if(flat)bindTrack(card,item);}
        }
    });}
    private void playlists(){int ticket=revision;async(()->api.get(path()),result->{if(ticket!=revision)return;status.setText("我的歌单");JSONArray data=result.optJSONArray("Playlists");List<JSONObject> lists=items(data);if(lists.isEmpty())content.addView(text("暂无歌单，选择“新建歌单”添加。",16,MUTED));
        GridLayout grid=grid(content,300,12,104);for(JSONObject list:lists){Button row=button(list.optString("Name")+"  ·  "+list.optInt("Count")+" 首",()->open(this,parent,"playlists",list.optString("Key"),list.optString("Name")));row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);addAdaptiveGridItem(grid,row,12,12);row.setOnLongClickListener(view->{deletePlaylist(list);return true;});row.setTag(list);row.setOnKeyListener((view,code,event)->{if(code!=KeyEvent.KEYCODE_MENU)return false;if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)deletePlaylist(list);return true;});}
    });}
    private void page(){
        if(loading)return;loading=true;more.setEnabled(false);int ticket=revision,start=offset;
        async(()->{try{return api.get(path()+"&StartIndex="+start+"&Limit=60");}finally{runOnUiThread(()->{if(ticket==revision){loading=false;if(more!=null)more.setEnabled(true);}});}},result->{
            if(ticket!=revision)return;
            List<JSONObject> rows=EtknApi.rows(result);total=result.optInt("TotalRecordCount",start+rows.size());offset=start+rows.size();
            if(start==0){JSONObject info=result.optJSONObject("Artist");artist=info;if(info!=null&&!tracksOnly){LinearLayout header=column();header.setPadding(dp(12),dp(8),dp(12),dp(12));TextView bio=text(info.optString("Overview"),14,MUTED);bio.setMaxLines(4);header.addView(bio);header.addView(button("播放歌手全部歌曲",()->{tracksOnly=true;load();}));content.addView(header,0);}
                JSONArray folders=result.optJSONArray("Folders");for(JSONObject folder:items(folders))MusicViews.card(this,listing,folder,()->openGroup(folder),false);
            }
            for(JSONObject item:rows){boolean track=item.optString("Kind").isEmpty()&&"Audio".equals(item.optString("Type"));if(track)tracks.add(item);View card=MusicViews.card(this,listing,item,()->{if(track)play(item,tracks,path());else openGroup(item);},track);if(track)bindTrack(card,item);}
            more.setVisibility(offset<total?View.VISIBLE:View.GONE);status.setText((result.optString("Title").isEmpty()?title:result.optString("Title"))+"  ·  "+(rows.isEmpty()&&start==0?"暂无音乐":"已加载 "+offset+" / "+total));
        });
    }
    private static List<JSONObject> items(JSONArray array){List<JSONObject> items=new ArrayList<>();if(array!=null)for(int i=0;i<array.length();i++)if(array.optJSONObject(i)!=null)items.add(array.optJSONObject(i));return items;}
    private void openGroup(JSONObject item){String kind=item.optString("Kind",tab);open(this,parent,kind,item.optString("Key"),item.optString("Name"));}
    private void bindTrack(View card,JSONObject item){card.setTag(item);card.setOnLongClickListener(view->{MusicActions.trackMenu(this,item,parent,this::refreshAfterAction);return true;});card.setOnKeyListener((view,code,event)->{if(code!=KeyEvent.KEYCODE_MENU)return false;if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)MusicActions.trackMenu(this,item,parent,this::refreshAfterAction);return true;});}
    private void refreshAfterAction(){if(tab.equals("playlists"))load();}
    private void play(JSONObject item,List<JSONObject> items,String context){MusicPlayback.get(this).play(new ArrayList<>(items),item,context,context.isEmpty()?items.size():total);open(MusicPlayerActivity.class);}
    private void playAll(){
        if(!tracks.isEmpty()){play(tracks.get(0),tracks,path());return;}
        String trackPath=api.userPath()+"/Music?ParentId="+EtknApi.encode(parent)+"&Tab="+(tab.equals("recommended")||key.isEmpty()?"songs":tab)+"&Key="+EtknApi.encode(key)+"&Tracks=1&SearchTerm="+EtknApi.encode(search);int ticket=revision;
        async(()->api.get(trackPath+"&Limit=200"),result->{if(ticket!=revision)return;List<JSONObject> items=EtknApi.rows(result);if(items.isEmpty()){status.setText("暂无可播放歌曲");return;}MusicPlayback.get(this).play(items,items.get(0),trackPath,result.optInt("TotalRecordCount",items.size()));open(MusicPlayerActivity.class);});
    }
    private void search(){EditText input=input("歌曲、专辑或歌手",false);input.setText(search);AlertDialog dialog=dialogBuilder().setTitle("搜索音乐").setView(input).setPositiveButton("搜索",(choice,index)->{search=input.getText().toString().trim();if(tab.equals("recommended")||tab.equals("playlists")||tab.equals("folders"))tab="songs";key="";tracksOnly=false;load();}).setNeutralButton("清除",(choice,index)->{search="";load();}).setNegativeButton("取消",null).create();dialog.show();themeDialog(dialog);}
    private void sort(){AlertDialog dialog=dialogBuilder().setTitle("排序").setItems(new String[]{"名称升序","名称降序","最新专辑"},(choice,index)->{sort=index==2?"DateCreated":"Name";order=index==0?"Ascending":"Descending";if(index==2){tab="albums";key="";tracksOnly=false;}load();}).setNegativeButton("取消",null).create();dialog.show();themeDialog(dialog);}
    private void deletePlaylist(JSONObject list){AlertDialog dialog=dialogBuilder().setTitle("删除歌单“"+list.optString("Name")+"”？").setMessage("只删除歌单，保留音乐文件。").setPositiveButton("删除",(choice,index)->async(()->api.delete(api.userPath()+"/Music/Playlists/"+EtknApi.encode(list.optString("Key"))),result->load())).setNegativeButton("取消",null).create();dialog.show();themeDialog(dialog);}
    @Override protected void onResume(){super.onResume();MusicPlayback.get(this);if(loaded&&(tab.equals("recommended")||tab.equals("playlists")))load();loaded=true;}
    @Override void imageSizeChanged(){if(listing!=null||tab.equals("recommended"))load();}
}
