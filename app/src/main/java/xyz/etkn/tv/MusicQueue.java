package xyz.etkn.tv;

import java.util.*;
import org.json.JSONObject;

final class MusicQueue {
    static final String[] MODES={"顺序播放","随机播放","列表循环","单曲循环"};
    final List<JSONObject> items=new ArrayList<>();
    int index=-1,mode,total;
    String path="";
    void replace(List<JSONObject> tracks,String id,String path,int total){
        items.clear();index=-1;append(tracks);this.path=path==null?"":path;this.total=Math.max(total,items.size());
        for(int i=0;i<items.size();i++)if(id.equals(items.get(i).optString("Id")))index=i;
        if(index<0&&!items.isEmpty())index=0;
    }
    void append(List<JSONObject> tracks){
        Set<String> ids=new HashSet<>();for(JSONObject item:items)ids.add(item.optString("Id"));
        for(JSONObject item:tracks)if("Audio".equals(item.optString("Type"))&&ids.add(item.optString("Id")))items.add(item);
    }
    JSONObject current(){return index<0||index>=items.size()?null:items.get(index);}
    int next(int direction,boolean ended,Random random){
        if(items.isEmpty())return -1;
        if(ended&&mode==3)return index;
        if(mode==1&&items.size()>1)return (index+1+random.nextInt(items.size()-1))%items.size();
        int next=index+direction;
        if(mode==2&&items.size()>=total)return (next+items.size())%items.size();
        return next>=0&&next<items.size()?next:-1;
    }
}
