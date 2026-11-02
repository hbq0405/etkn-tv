package xyz.etkn.tv;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SkipSegmentsTest {
    private JSONObject marker(String type,long time)throws Exception{return new JSONObject().put("MarkerType",type).put("StartPositionTicks",time*10000);}
    @Test public void autoIntroStartsAtEndWithoutFirstPlayingAndSeeking()throws Exception{
        JSONArray markers=new JSONArray().put(marker("IntroStart",0)).put(marker("IntroEnd",4000)).put(marker("CreditsStart",8000));
        assertEquals(4000,SkipSegments.initialPosition(markers,0,10000,true));
        assertEquals(4000,SkipSegments.initialPosition(markers,2000,10000,true));
        assertEquals(5000,SkipSegments.initialPosition(markers,5000,10000,true));
        assertEquals(8500,SkipSegments.initialPosition(markers,8500,10000,true));
        assertEquals(0,SkipSegments.initialPosition(markers,0,10000,false));
        assertEquals(0,SkipSegments.initialPosition(null,0,10000,true));
        assertEquals(0,SkipSegments.initialPosition(markers,0,3000,true));
        assertEquals(0,SkipSegments.initialPosition(markers,0,-1,true));
        assertEquals(0,SkipSegments.initialPosition(new JSONArray().put(marker("IntroStart",1000)).put(marker("IntroEnd",4000)),0,10000,true));
    }
    @Test public void timelineUsesExplicitBoundariesAndMediaEndForCredits()throws Exception{
        JSONArray markers=new JSONArray().put(marker("CreditsStart",8000)).put(marker("IntroEnd",4000)).put(marker("IntroStart",0)).put(marker("Chapter",6000));
        assertArrayEquals(new long[]{0,4000,8000,10000},SkipSegments.boundaries(markers,10000));
        assertArrayEquals(new long[0],SkipSegments.boundaries(markers,-1));assertArrayEquals(new long[0],SkipSegments.boundaries(null,10000));
    }
    @Test public void timelineIgnoresInvalidRangesAndClearsSourcesWithoutMarkers()throws Exception{
        assertArrayEquals(new long[0],SkipSegments.boundaries(new JSONArray().put(marker("IntroStart",4000)).put(marker("IntroEnd",1000)).put(marker("CreditsStart",12000)),10000));
        assertArrayEquals(new long[0],SkipSegments.boundaries(new JSONArray(),10000));
    }
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
