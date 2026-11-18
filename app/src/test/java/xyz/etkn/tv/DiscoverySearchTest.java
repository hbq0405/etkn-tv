package xyz.etkn.tv;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscoverySearchTest {
    private JSONObject source(String type,int page,int total,int id)throws Exception{return new JSONObject().put("page",page).put("total_pages",total).put("results",new JSONArray().put(new JSONObject().put("id",id).put("media_type",type)));}
    @Test public void sameNumericIdsRemainDistinctAndEachSourceHasItsOwnCursor()throws Exception{
        DiscoverySearch state=new DiscoverySearch();state.accept(new JSONObject().put("sources",new JSONObject().put("movie",source("movie",1,1,7)).put("series",source("tv",1,2,7))));
        assertEquals(2,state.items("all").size());assertEquals(1,state.items("series").size());
        JSONObject request=state.request("片名","all",false);assertEquals(0,request.getInt("movie_page"));assertEquals(2,request.getInt("series_page"));
        state.accept(new JSONObject().put("sources",new JSONObject().put("series",source("tv",2,2,8))));
        assertEquals(3,state.items("all").size());assertFalse(state.hasMore("all"));
    }
    @Test public void partialFailuresRetainSuccessfulItemsAndRetryFailedPage()throws Exception{
        DiscoverySearch state=new DiscoverySearch();state.accept(new JSONObject().put("sources",new JSONObject().put("movie",new JSONObject().put("error","failed")).put("series",source("tv",1,1,2))));
        assertEquals(1,state.items("all").size());assertEquals("failed",state.error("all"));assertEquals(1,state.request("x","all",false).getInt("movie_page"));
        state.accept(new JSONObject().put("sources",new JSONObject().put("movie",source("movie",1,1,3))));
        assertEquals("",state.error("all"));assertEquals(2,state.items("all").size());assertEquals(2,state.items("all").get(0).getInt("id"));
    }
    @Test public void emptyHiddenPageCanContinueAndTypeSwitchDoesNotConsumeOtherCursor()throws Exception{
        DiscoverySearch state=new DiscoverySearch();state.accept(new JSONObject().put("sources",new JSONObject().put("movie",new JSONObject().put("page",1).put("total_pages",2).put("results",new JSONArray()))));
        assertTrue(state.hasMore("movie"));assertEquals(2,state.request("x","movie",true).getInt("movie_page"));assertEquals(0,state.request("x","movie",true).getInt("series_page"));
    }
}
