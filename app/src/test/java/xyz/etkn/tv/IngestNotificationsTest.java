package xyz.etkn.tv;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IngestNotificationsTest {
    @Test public void episodeUpdatesUseSeriesNameAndKeepReplacementAndMovieLabels() throws Exception {
        JSONObject item = new JSONObject().put("Type", "Episode").put("Name", "第 5 集")
                .put("SeriesName", "合成剧集").put("Outcome", "ingested");
        assertEquals("你观看的《合成剧集》有更新了", IngestNotifications.followedEpisodeTitle(item));
        item.put("Outcome", "version_added");
        assertEquals("你观看的《合成剧集》有更新了", IngestNotifications.followedEpisodeTitle(item));
        item.put("Outcome", "replaced");
        assertEquals("", IngestNotifications.followedEpisodeTitle(item));
        assertEquals("洗版成功", IngestNotifications.outcomeLabel(item));
        item.put("Type", "Movie").put("Outcome", "ingested").remove("SeriesName");
        assertEquals("", IngestNotifications.followedEpisodeTitle(item));
        assertEquals("已入库", IngestNotifications.outcomeLabel(item));
    }
}
