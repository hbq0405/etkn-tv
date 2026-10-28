package xyz.etkn.tv;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DetailPresentationTest {
    @Test public void genresAndTagsMergeWithoutDuplicatesOrNullText()throws Exception{
        JSONObject item=new JSONObject().put("Genres",new JSONArray().put("剧情").put("悬疑")).put("Tags",new JSONArray().put("悬疑").put(new JSONObject().put("Name","经典")).put(JSONObject.NULL));
        assertEquals("剧情 · 悬疑 · 经典",DetailPresentation.categories(item));assertEquals("",DetailPresentation.categories(new JSONObject()));
        assertEquals("1 小时 35 分钟",DetailPresentation.duration(95L*600000000));assertEquals("",DetailPresentation.duration(0));
        assertEquals("–",DetailPresentation.rating(item,"DoubanRating"));
    }
    @Test public void episodeNumberUsesNormalizedIndexAndResumePrefersLatestProgress()throws Exception{
        JSONObject first=new JSONObject().put("IndexNumber",1).put("Name","原始文件名").put("UserData",new JSONObject().put("Played",true));
        JSONObject second=new JSONObject().put("IndexNumber",2).put("UserData",new JSONObject().put("PlaybackPositionTicks",900).put("LastPlayedDate","2026-10-01"));
        JSONObject third=new JSONObject().put("IndexNumber",3).put("UserData",new JSONObject().put("PlaybackPositionTicks",300).put("LastPlayedDate","2026-10-28"));
        assertEquals("第 1 集",DetailPresentation.episodeNumber(first));assertSame(third,DetailPresentation.recommended(Arrays.asList(first,second,third)));
        second.getJSONObject("UserData").put("PlaybackPositionTicks",0);third.getJSONObject("UserData").put("PlaybackPositionTicks",0);
        assertSame(second,DetailPresentation.recommended(Arrays.asList(first,second,third)));
    }
    @Test public void versionIdentitySurvivesSourceReordering()throws Exception{
        JSONArray sources=new JSONArray().put(new JSONObject().put("Id","second")).put(new JSONObject().put("Id","first"));
        assertEquals(0,PlaybackSelection.sourceIndex(sources,"second"));assertEquals(1,PlaybackSelection.sourceIndex(sources,"first"));assertEquals(-1,PlaybackSelection.sourceIndex(sources,"deleted"));
    }
    @Test public void sameLanguageTracksUseServerIndexAndExternalTracksHaveIndependentIdentity()throws Exception{
        JSONObject source=new JSONObject().put("MediaStreams",new JSONArray()
            .put(new JSONObject().put("Type","Video").put("Index",0))
            .put(new JSONObject().put("Type","Audio").put("Index",1).put("Language","chi"))
            .put(new JSONObject().put("Type","Audio").put("Index",4).put("Language","chi"))
            .put(new JSONObject().put("Type","Subtitle").put("Index",8).put("IsExternal",true))
            .put(new JSONObject().put("Type","Subtitle").put("Index",9)));
        assertEquals(1,PlaybackSelection.embeddedOrdinal(source,4,false));assertEquals(0,PlaybackSelection.embeddedOrdinal(source,9,true));assertEquals(-1,PlaybackSelection.embeddedOrdinal(source,8,true));
        assertEquals("etkn-subtitle-8",PlaybackSelection.externalId(8));assertEquals("etkn-subtitle-8",PlaybackSelection.localTrackId("1:etkn-subtitle-8"));assertEquals("默认",PlaybackSelection.selectionLabel(source,-2,true));assertEquals("关闭",PlaybackSelection.selectionLabel(source,-1,true));
    }
}
