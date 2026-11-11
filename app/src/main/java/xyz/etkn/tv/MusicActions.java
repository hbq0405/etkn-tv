package xyz.etkn.tv;

import android.app.AlertDialog;
import android.widget.EditText;
import org.json.*;
import java.util.*;

final class MusicActions {
    static void createPlaylist(TvActivity host,java.util.function.Consumer<JSONObject> done){
        EditText name=host.input("歌单名称",false);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        AlertDialog dialog=host.dialogBuilder().setTitle("新建歌单").setView(name).setPositiveButton("创建",null).setNegativeButton("取消",null).create();dialog.show();host.themeDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("请输入歌单名称");return;}dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            host.async(()->{try{return host.api.post(host.api.userPath()+"/Music/Playlists",new JSONObject().put("Name",value));}finally{host.runOnUiThread(()->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true));}},result->{dialog.dismiss();done.accept(result);});});
    }
    static void playlist(TvActivity host,JSONObject item,Runnable done){
        host.async(()->host.api.get(host.api.userPath()+"/Music?Tab=playlists&ItemId="+EtknApi.encode(item.optString("Id"))),result->{
            JSONArray lists=result.optJSONArray("Playlists");List<JSONObject> rows=new ArrayList<>();if(lists!=null)for(int i=0;i<lists.length();i++)rows.add(lists.optJSONObject(i));
            String[] labels=new String[rows.size()+1];labels[0]="＋ 新建歌单";for(int i=0;i<rows.size();i++)labels[i+1]=(rows.get(i).optBoolean("Included")?"✓ ":"＋ ")+rows.get(i).optString("Name");
            AlertDialog dialog=host.dialogBuilder().setTitle("保存到歌单 · "+item.optString("Name")).setItems(labels,(choice,index)->{
                if(index==0){createPlaylist(host,list->changePlaylist(host,list,item,false,done));return;}
                JSONObject list=rows.get(index-1);changePlaylist(host,list,item,list.optBoolean("Included"),done);
            }).setNegativeButton("取消",null).create();dialog.show();host.themeDialog(dialog);
        });
    }
    private static void changePlaylist(TvActivity host,JSONObject list,JSONObject item,boolean remove,Runnable done){
        String path=host.api.userPath()+"/Music/Playlists/"+EtknApi.encode(list.optString("Key"))+"/Items/"+EtknApi.encode(item.optString("Id"));
        host.async(()->remove?host.api.delete(path):host.api.post(path,new JSONObject()),result->{host.status.setText(remove?"已从歌单移除":"已保存到歌单");done.run();});
    }
    static void artist(TvActivity host,JSONObject item,String parent){
        JSONArray links=item.optJSONArray("ArtistLinks");if(links==null||links.length()==0){host.status.setText("暂无歌手信息");return;}
        String[] labels=new String[links.length()];for(int i=0;i<labels.length;i++)labels[i]=links.optJSONObject(i).optString("Name");
        if(labels.length==1){JSONObject artist=links.optJSONObject(0);MusicActivity.open(host,parent,"artists",artist.optString("Key"),artist.optString("Name"));return;}
        AlertDialog dialog=host.dialogBuilder().setTitle("选择歌手").setItems(labels,(choice,index)->MusicActivity.open(host,parent,"artists",links.optJSONObject(index).optString("Key"),labels[index])).setNegativeButton("取消",null).create();dialog.show();host.themeDialog(dialog);
    }
    static void trackMenu(TvActivity host,JSONObject item,String parent,Runnable refresh){
        List<String> labels=new ArrayList<>(List.of("播放","保存到歌单","查看专辑","查看歌手","收藏 / 取消收藏"));
        AlertDialog dialog=host.dialogBuilder().setTitle(item.optString("Name")).setItems(labels.toArray(new String[0]),(choice,index)->{
            if(index==0){MusicPlayback.get(host).play(List.of(item),item,"",1);host.open(MusicPlayerActivity.class);}
            else if(index==1)playlist(host,item,refresh);
            else if(index==2){if(item.optString("AlbumKey").isEmpty()){host.status.setText("暂无专辑信息");return;}MusicActivity.open(host,parent,"albums",item.optString("AlbumKey"),item.optString("Album"));}
            else if(index==3)artist(host,item,parent);
            else {JSONObject data=item.optJSONObject("UserData");boolean favorite=data!=null&&data.optBoolean("IsFavorite");String path=host.api.userPath()+"/FavoriteItems/"+EtknApi.encode(item.optString("Id"));host.async(()->favorite?host.api.delete(path):host.api.post(path,new JSONObject()),result->{try{item.put("UserData",result);}catch(JSONException ignored){}host.status.setText(favorite?"已取消收藏":"已收藏");refresh.run();});}
        }).setNegativeButton("取消",null).create();dialog.show();host.themeDialog(dialog);
    }
}
