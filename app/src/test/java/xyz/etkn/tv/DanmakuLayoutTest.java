package xyz.etkn.tv;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DanmakuLayoutTest {
    private DanmakuTimeline cues(int count,long step)throws Exception{JSONArray rows=new JSONArray();for(int i=0;i<count;i++)rows.put(new JSONObject().put("time",i*step).put("text",i%3==0?"long wide comment":"short"));return new DanmakuTimeline(rows);}
    @Test public void steadyCuesShareLanesWithoutFullLifeBatches()throws Exception{
        List<DanmakuLayout.Entry> entries=DanmakuLayout.place(cues(70,250),4,1280,8000,16,100,text->text.length()*12);
        assertTrue(entries.size()>60);assertTrue(entries.get(4).start<2000);
        long previous=-1000;for(DanmakuLayout.Entry entry:entries){assertTrue(entry.lane<4);assertTrue(entry.start-previous>=220);previous=entry.start;}
        assertEquals(4,entries.stream().map(e->e.lane).distinct().count());
    }
    @Test public void differentWidthsNeverCatchEarlierScrollingCues()throws Exception{
        for(int mode:new int[]{1,6}){
            DanmakuTimeline timeline=cues(40,600);JSONArray data=new JSONArray();for(DanmakuTimeline.Cue c:timeline.cues)data.put(new JSONObject().put("time",c.time).put("text",c.text).put("mode",mode));
            List<DanmakuLayout.Entry> entries=DanmakuLayout.place(new DanmakuTimeline(data),2,1280,8000,16,100,text->text.length()*26);
            for(int i=0;i<entries.size();i++)for(int j=i+1;j<entries.size();j++){
                DanmakuLayout.Entry old=entries.get(i),next=entries.get(j);if(old.lane!=next.lane)continue;
                for(long t=next.start;t<old.start+8000;t+=33){float ox=old.x(t,1280,8000),nx=next.x(t,1280,8000);assertTrue("Scrolling comments collide",mode==1?nx>=ox+old.width+15:nx+next.width+15<=ox);}
            }
        }
    }
    @Test public void simultaneousCuesStaggerAndRowsApplyToLayout()throws Exception{
        DanmakuTimeline timeline=cues(20,0);List<DanmakuLayout.Entry> many=DanmakuLayout.place(timeline,8,1280,8000,16,100,t->120);
        for(int i=1;i<many.size();i++)assertTrue(many.get(i).start>many.get(i-1).start);
        assertEquals(8,many.stream().map(e->e.lane).distinct().count());
        for(DanmakuLayout.Entry e:DanmakuLayout.place(timeline,1,1280,8000,16,100,t->120))assertEquals(0,e.lane);
    }
    @Test public void densityReducesCommentsWithoutChangingTimingAndSurvivesPageChanges()throws Exception{
        DanmakuTimeline timeline=cues(500,1000);
        List<DanmakuLayout.Entry> all=DanmakuLayout.place(timeline,8,1280,8000,16,100,t->120);
        List<DanmakuLayout.Entry> half=DanmakuLayout.place(timeline,8,1280,8000,16,50,t->120);
        List<DanmakuLayout.Entry> low=DanmakuLayout.place(timeline,8,1280,8000,16,10,t->120);
        assertEquals(500,all.size());assertTrue(half.size()>200&&half.size()<300);assertTrue(low.size()>25&&low.size()<75);
        Set<DanmakuTimeline.Cue> selected=new HashSet<>();for(DanmakuLayout.Entry e:half){selected.add(e.cue);assertEquals(e.cue.time,e.start);}
        for(DanmakuLayout.Entry e:low)assertTrue(selected.contains(e.cue));
        for(int window=0;window<5;window++){final int from=window*100000;assertTrue(half.stream().filter(e->e.start>=from&&e.start<from+100000).count()>30);}
        DanmakuTimeline trimmed=new DanmakuTimeline(null);for(DanmakuTimeline.Cue c:timeline.cues)if(c.time>=120000)trimmed.cues.add(c);
        List<DanmakuLayout.Entry> remaining=DanmakuLayout.place(trimmed,8,1280,8000,16,50,t->120);
        assertEquals(half.stream().filter(e->e.cue.time>=120000).count(),remaining.size());for(DanmakuLayout.Entry e:remaining)assertTrue(selected.contains(e.cue));
    }
    @Test public void mergeDeduplicatesWindowsAndKeepsUnicodeIntact()throws Exception{
        JSONArray rows=new JSONArray().put(new JSONObject().put("time",1000).put("text","中文👨‍👩‍👧‍👦👍🏽🇨🇳🫠"));DanmakuTimeline page=new DanmakuTimeline(rows);
        assertEquals(1,DanmakuTimeline.merge(Arrays.asList(page,page)).cues.size());
        for(String emoji:new String[]{"👨‍👩‍👧‍👦","👍🏽","🇨🇳","❤️"})assertEquals("a".repeat(199),DanmakuTimeline.truncate("a".repeat(199)+emoji+"z"));
        assertEquals("😀".repeat(200),DanmakuTimeline.truncate("😀".repeat(210)));
    }
}
