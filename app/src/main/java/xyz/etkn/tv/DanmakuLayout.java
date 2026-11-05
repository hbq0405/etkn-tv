package xyz.etkn.tv;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Bounded delays preserve cue timing while sharing lanes by actual travel clearance. */
final class DanmakuLayout {
    static final class Entry {
        final DanmakuTimeline.Cue cue;final long start;final float width;final int lane;
        Entry(DanmakuTimeline.Cue cue,long start,float width,int lane){this.cue=cue;this.start=start;this.width=width;this.lane=lane;}
        float x(long now,int screen,long life){
            float fraction=(now-start)/(float)life;
            return cue.mode==4||cue.mode==5?(screen-width)/2:cue.mode==6?-width+(screen+width)*fraction:screen-(screen+width)*fraction;
        }
    }
    static List<Entry> place(DanmakuTimeline timeline,int lanes,int screen,long life,float gap,ToDoubleFunction<String> measure){
        List<Entry> result=new ArrayList<>();Entry[] previous=new Entry[lanes];long last=-1000;int next=0;
        long spacing=Math.max(60,Math.min(220,900/lanes));
        for(DanmakuTimeline.Cue cue:timeline.cues){
            float width=(float)measure.applyAsDouble(cue.text);long desired=Math.max(cue.time,last+spacing),best=Long.MAX_VALUE;int chosen=0;
            for(int offset=0;offset<lanes;offset++){
                int lane=(next+offset)%lanes;Entry old=previous[lane];long start=desired;
                if(old!=null){
                    if((cue.mode==1||cue.mode==6)&&old.cue.mode==cue.mode){
                        double priorSpeed=(screen+old.width)/(double)life,newSpeed=(screen+width)/(double)life;
                        long clear=old.start+(long)Math.ceil((old.width+gap)/priorSpeed);
                        long catchUp=old.start+life-(long)Math.floor((screen-gap)/newSpeed);
                        start=Math.max(start,Math.max(clear,catchUp));
                    }else start=Math.max(start,old.start+life);
                }
                if(start<best){best=start;chosen=lane;}
            }
            if(best-cue.time>1500)continue;
            Entry entry=new Entry(cue,best,width,chosen);result.add(entry);previous[chosen]=entry;last=best;next=(chosen+1)%lanes;
        }
        return result;
    }
    static int firstAt(List<Entry> entries,long time){int low=0,high=entries.size();while(low<high){int mid=(low+high)>>>1;if(entries.get(mid).start<time)low=mid+1;else high=mid;}return low;}
}
