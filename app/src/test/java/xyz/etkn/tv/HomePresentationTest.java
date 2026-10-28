package xyz.etkn.tv;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HomePresentationTest {
    @Test public void resumeEpisodeTitleIncludesSeriesEpisodeNumberAndName() throws Exception {
        JSONObject episode=new JSONObject().put("Type","Episode").put("SeriesName","双轭").put("ParentIndexNumber",1).put("IndexNumber",1).put("Name","第1集");
        assertEquals("双轭\nS1E1 - 第1集",EtknApi.resumeTitle(episode));
    }

    @Test public void resumeEpisodeWithoutSeriesNameStillShowsEpisodeNumberAndName() throws Exception {
        JSONObject episode=new JSONObject().put("Type","Episode").put("ParentIndexNumber",2).put("IndexNumber",3).put("Name","关键时刻");
        assertEquals("S2E3 - 关键时刻",EtknApi.resumeTitle(episode));
    }

    @Test public void resumeEpisodeWithoutNumbersDoesNotShowNullPlaceholders() throws Exception {
        JSONObject episode=new JSONObject().put("Type","Episode").put("SeriesName","双轭").put("Name","第1集");
        assertEquals("双轭\n第1集",EtknApi.resumeTitle(episode));
    }

    @Test public void nonEpisodeResumeTitleRemainsTheMediaName() throws Exception {
        JSONObject movie=new JSONObject().put("Type","Movie").put("Name","测试影片");
        assertEquals("测试影片",EtknApi.resumeTitle(movie));
    }
}
