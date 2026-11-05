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
            cues.add(new Cue(time,truncate(text),0xff000000|(row.optInt("color",0xffffff)&0xffffff),mode));
        }
        cues.sort(Comparator.comparingLong(cue->cue.time));
    }
    static String truncate(String text){
        if(text.codePointCount(0,text.length())<=200)return text;
        int end=text.offsetByCodePoints(0,200);
        while(end>0){
            int point=text.codePointAt(end),previous=text.codePointBefore(end),type=Character.getType(point);
            if(point==0x200d||previous==0x200d||type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK||type==Character.ENCLOSING_MARK||point>=0x1f3fb&&point<=0x1f3ff||point>=0xe0020&&point<=0xe007f)end-=Character.charCount(previous);
            else if(point>=0x1f1e6&&point<=0x1f1ff&&previous>=0x1f1e6&&previous<=0x1f1ff){
                int start=end;while(start>0){int prior=text.codePointBefore(start);if(prior<0x1f1e6||prior>0x1f1ff)break;start-=Character.charCount(prior);}
                if(text.codePointCount(start,end)%2!=0)end-=Character.charCount(previous);break;
            }else break;
        }
        return text.substring(0,end);
    }
    static DanmakuTimeline merge(java.util.Collection<DanmakuTimeline> pages){
        DanmakuTimeline result=new DanmakuTimeline(null);java.util.Set<String> seen=new java.util.HashSet<>();
        for(DanmakuTimeline page:pages)for(Cue cue:page.cues)if(seen.add(cue.time+"/"+cue.mode+"/"+cue.color+"/"+cue.text))result.cues.add(cue);
        result.cues.sort(Comparator.comparingLong(cue->cue.time));return result;
    }
    int firstAt(long time){int low=0,high=cues.size();while(low<high){int mid=(low+high)>>>1;if(cues.get(mid).time<time)low=mid+1;else high=mid;}return low;}
}
