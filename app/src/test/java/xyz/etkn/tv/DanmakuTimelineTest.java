package xyz.etkn.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DanmakuTimelineTest {
    @Test public void ordersCuesAndSeeksByMilliseconds()throws Exception{
        JSONArray rows=new JSONArray().put(new JSONObject().put("time",5000).put("text","late").put("mode",5)).put(new JSONObject().put("time",1000).put("text","early").put("color",0x112233));
        DanmakuTimeline timeline=new DanmakuTimeline(rows);
        assertEquals("early",timeline.cues.get(0).text);assertEquals(0xff112233,timeline.cues.get(0).color);
        assertEquals(0,timeline.firstAt(0));assertEquals(0,timeline.firstAt(1000));assertEquals(1,timeline.firstAt(1001));assertEquals(1,timeline.firstAt(5000));assertEquals(2,timeline.firstAt(5001));
    }
    @Test public void ignoresMalformedModesAndBoundsTextAndCount()throws Exception{
        JSONArray rows=new JSONArray().put(new JSONObject().put("time",-1).put("text","bad")).put(new JSONObject().put("time",1).put("text"," ")).put(new JSONObject().put("time",1).put("text","bad").put("mode",7));
        assertTrue(new DanmakuTimeline(rows).cues.isEmpty());
        rows=new JSONArray();for(int i=0;i<4000;i++)rows.put(new JSONObject().put("time",i).put("text","a".repeat(250)));
        DanmakuTimeline timeline=new DanmakuTimeline(rows);assertEquals(3000,timeline.cues.size());assertEquals(200,timeline.cues.get(0).text.length());
    }
}
