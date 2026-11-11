package xyz.etkn.tv;

import java.util.List;
import java.util.Random;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class MusicQueueTest {
    private JSONObject track(String id)throws Exception{return new JSONObject().put("Id",id).put("Type","Audio");}
    @Test public void queueDeduplicatesAndKeepsSelectedTrack()throws Exception{
        MusicQueue queue=new MusicQueue();
        queue.replace(List.of(track("one"),track("two"),track("one"),new JSONObject().put("Type","Movie")),"two","/music?Tracks=1",3);
        assertEquals(2,queue.items.size());assertEquals("two",queue.current().getString("Id"));
        queue.append(List.of(track("two"),track("three")));
        assertEquals(3,queue.items.size());assertEquals(2,queue.next(1,false,new Random(0)));
    }
    @Test public void loopWaitsForUnloadedPageAndSingleRepeatOnlyAppliesAtEnd()throws Exception{
        MusicQueue queue=new MusicQueue();queue.replace(List.of(track("one"),track("two")),"two","/music?Tracks=1",3);
        queue.mode=2;assertEquals(-1,queue.next(1,true,new Random(0)));
        queue.append(List.of(track("three")));queue.index=2;
        assertEquals(0,queue.next(1,true,new Random(0)));
        queue.mode=3;assertEquals(2,queue.next(1,true,new Random(0)));
        assertEquals(1,queue.next(-1,false,new Random(0)));
        queue.mode=1;assertNotEquals(queue.index,queue.next(1,false,new Random(0)));
    }
}
