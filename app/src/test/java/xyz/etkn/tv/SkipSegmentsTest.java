package xyz.etkn.tv;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SkipSegmentsTest {
    private JSONObject marker(String type,long time)throws Exception{return new JSONObject().put("MarkerType",type).put("StartPositionTicks",time*10000);}
    @Test public void skipsOnlyWithinExplicitValidRanges()throws Exception{
        JSONArray markers=new JSONArray().put(marker("IntroStart",1000)).put(marker("IntroEnd",4000)).put(marker("CreditsStart",8000));
        assertNull(SkipSegments.at(markers,999,10000));assertEquals(4000,SkipSegments.at(markers,1000,10000).end);assertNull(SkipSegments.at(markers,4000,10000));
        assertEquals("跳过片尾",SkipSegments.at(markers,8000,10000).label);assertEquals(10000,SkipSegments.at(markers,9999,10000).end);assertNull(SkipSegments.at(markers,10000,10000));
    }
    @Test public void missingInvalidAndOrdinaryChaptersDoNotCauseGuessedSkips()throws Exception{
        assertNull(SkipSegments.at(null,1000,10000));assertNull(SkipSegments.at(new JSONArray().put(marker("IntroEnd",2000)),1000,10000));
        assertNull(SkipSegments.at(new JSONArray().put(marker("IntroStart",4000)).put(marker("IntroEnd",1000)),4500,10000));
        assertNull(SkipSegments.at(new JSONArray().put(marker("IntroStart",0)).put(marker("IntroEnd",20000)),1000,10000));
        assertNull(SkipSegments.at(new JSONArray().put(new JSONObject().put("Name","片头").put("StartPositionTicks",0)),1000,10000));
        assertNull(SkipSegments.at(new JSONArray().put(marker("CreditsStart",8000)),9000,-1));
    }
}
