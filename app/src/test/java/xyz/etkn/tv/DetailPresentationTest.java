package xyz.etkn.tv;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DetailPresentationTest {
    private JSONObject episode(String id,int season,int number)throws Exception{return new JSONObject().put("Id",id).put("Type","Episode").put("ParentIndexNumber",season).put("IndexNumber",number);}
    @Test public void nextEpisodeUsesNumericOrderAcrossSeasonsAndSkipsMissingEntries()throws Exception{
        JSONObject current=episode("current",1,9),next=episode("next",1,12),nextSeason=episode("season-two",2,1);
        List<JSONObject> episodes=Arrays.asList(nextSeason,episode("missing",1,10).put("IsMissing",true),next,current,episode("virtual",1,11).put("LocationType","Virtual"),episode("special",0,99));
        assertSame(next,PlaybackSelection.nextEpisode(episodes,"current"));
        assertSame(nextSeason,PlaybackSelection.nextEpisode(episodes,"next"));
        assertNull(PlaybackSelection.nextEpisode(episodes,"season-two"));assertNull(PlaybackSelection.nextEpisode(episodes,"unknown"));
        nextSeason.remove("IndexNumber");assertNull(PlaybackSelection.nextEpisode(episodes,"next"));
        current.remove("ParentIndexNumber");assertNull(PlaybackSelection.nextEpisode(episodes,"current"));
    }
    @Test public void seasonAudioUsesListOrdinalDespiteMislabeledTitlesAndDifferentServerIndices()throws Exception{
        JSONObject source=new JSONObject().put("DefaultAudioStreamIndex",3).put("MediaStreams",new JSONArray()
            .put(new JSONObject().put("Type","Video").put("Index",0))
            .put(new JSONObject().put("Type","Audio").put("Index",3).put("Title","国语").put("Language","chi"))
            .put(new JSONObject().put("Type","Subtitle").put("Index",7))
            .put(new JSONObject().put("Type","Audio").put("Index",9).put("Title","国语").put("Language","chi")));
        assertEquals(1,PlaybackSelection.audioOrdinal(source,9));assertEquals(9,PlaybackSelection.audioAt(source,1));
        source.getJSONArray("MediaStreams").getJSONObject(3).put("Index",24);
        assertEquals(24,PlaybackSelection.audioAt(source,1));assertEquals(PlaybackSelection.AUTO,PlaybackSelection.audioAt(source,2));
        assertEquals(-1,PlaybackSelection.audioOrdinal(source,PlaybackSelection.AUTO));
        JSONObject episode=new JSONObject().put("Type","Episode").put("SeasonId","season-a");
        assertEquals("season-a",PlaybackSelection.seasonKey(episode));assertEquals("",PlaybackSelection.seasonKey(new JSONObject().put("Type","Movie").put("SeasonId","season-a")));
    }
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
        assertSame(first,DetailPresentation.recommended(Arrays.asList(first,second,third)));
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
        assertEquals("etkn-subtitle-8",PlaybackSelection.externalId(8));assertEquals("etkn-subtitle-8",PlaybackSelection.localTrackId("1:etkn-subtitle-8"));assertEquals("关闭",PlaybackSelection.selectionLabel(source,-2,true));assertEquals("关闭",PlaybackSelection.selectionLabel(source,-1,true));
    }
    @Test public void automaticLabelsResolveTheServersExactDefaultTrack()throws Exception{
        JSONObject source=new JSONObject().put("DefaultAudioStreamIndex",4).put("DefaultSubtitleStreamIndex",9).put("MediaStreams",new JSONArray()
            .put(new JSONObject().put("Type","Audio").put("Index",1).put("IsDefault",true).put("DisplayTitle","原声"))
            .put(new JSONObject().put("Type","Audio").put("Index",4).put("DisplayTitle","国语 DTS 5.1 (默认)"))
            .put(new JSONObject().put("Type","Subtitle").put("Index",9).put("DisplayTitle","简体中文 ASS (默认 ass)")));
        assertEquals("国语 DTS 5.1",PlaybackSelection.selectionLabel(source,PlaybackSelection.AUTO,false));
        assertEquals("简体中文 ASS",PlaybackSelection.selectionLabel(source,PlaybackSelection.AUTO,true));
        assertEquals("原声",PlaybackSelection.selectionLabel(source,1,false));
        assertEquals("自动选择",PlaybackSelection.selectionLabel(null,PlaybackSelection.AUTO,false));
    }
    @Test public void trackLabelsPreferSubtitlesAndOnlyRemoveDefaultBadges()throws Exception{
        JSONObject stream=new JSONObject().put("Title","国语配音[CH-CSD]").put("DisplayTitle","国语 TrueHD 5.1 (默认)");
        assertEquals("国语配音[CH-CSD]",PlaybackSelection.streamLabel(stream));
        stream.put("Title","   ");assertEquals("国语 TrueHD 5.1",PlaybackSelection.streamLabel(stream));
        stream.put("Title",JSONObject.NULL);assertEquals("国语 TrueHD 5.1",PlaybackSelection.streamLabel(stream));
        stream.put("Title","双语特效（导演剪辑）（默认）");assertEquals("双语特效（导演剪辑）",PlaybackSelection.streamLabel(stream));
        stream.put("Title","国语 默认");assertEquals("国语",PlaybackSelection.streamLabel(stream));
        stream.remove("Title");stream.remove("DisplayTitle");stream.put("Language","chi").put("Codec","PGS");assertEquals("chi · PGS",PlaybackSelection.streamLabel(stream));
    }
}
