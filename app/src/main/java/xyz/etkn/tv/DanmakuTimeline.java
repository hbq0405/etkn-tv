package xyz.etkn.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Millisecond cues, sorted independently of response order. */
final class DanmakuTimeline {
    static final class Cue {
        final long time; final String text; final int color,mode;
        Cue(long time,String text,int color,int mode){this.time=time;this.text=text;this.color=color;this.mode=mode;}
    }
    final List<Cue> cues=new ArrayList<>();
    DanmakuTimeline(JSONArray rows){
        if(rows!=null)for(int i=0;i<Math.min(rows.length(),3000);i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;
            long time=row.optLong("time",-1);String text=row.optString("text").replace('\n',' ').trim();int mode=row.optInt("mode",1);
            if(time<0||time>604800000||text.isEmpty()||(mode!=1&&mode!=4&&mode!=5&&mode!=6))continue;
            cues.add(new Cue(time,text.substring(0,Math.min(text.length(),200)),0xff000000|(row.optInt("color",0xffffff)&0xffffff),mode));
        }
        cues.sort(Comparator.comparingLong(cue->cue.time));
    }
    int firstAt(long time){int low=0,high=cues.size();while(low<high){int mid=(low+high)>>>1;if(cues.get(mid).time<time)low=mid+1;else high=mid;}return low;}
}
