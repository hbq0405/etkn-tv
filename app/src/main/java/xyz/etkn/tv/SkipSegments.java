package xyz.etkn.tv;

import org.json.JSONArray;
import org.json.JSONObject;

/** Only explicit server markers are skippable; ordinary chapter names are never guessed. */
final class SkipSegments {
    static final class Segment {
        final String label;
        final long end;
        Segment(String label,long end){this.label=label;this.end=end;}
    }
    static Segment at(JSONArray chapters,long position,long duration){
        if(chapters==null||position<0||duration<=0)return null;
        long introStart=-1,introEnd=-1,credits=-1;
        for(int i=0;i<chapters.length();i++){
            JSONObject chapter=chapters.optJSONObject(i);if(chapter==null)continue;
            long ticks=chapter.optLong("StartPositionTicks",-1);if(ticks<0)continue;long time=ticks/10000;
            switch(chapter.optString("MarkerType")){case "IntroStart":introStart=time;break;case "IntroEnd":introEnd=time;break;case "CreditsStart":credits=time;break;default:break;}
        }
        if(introStart>=0&&introEnd>introStart&&introEnd<duration&&position>=introStart&&position<introEnd)return new Segment("跳过片头",introEnd);
        if(credits>=0&&credits<duration&&position>=credits&&position<duration)return new Segment("跳过片尾",duration);
        return null;
    }
}
