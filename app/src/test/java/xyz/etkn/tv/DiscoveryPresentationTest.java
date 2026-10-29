package xyz.etkn.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class DiscoveryPresentationTest {
    // ---------- 订阅状态缎带 ----------
    @Test public void libraryAndSubscriptionStatusesRenderChineseRibbons() throws Exception {
        assertEquals("已入库",DiscoveryPresentation.statusLabel(new JSONObject().put("in_library",true).put("subscription_status","NONE")));
        assertEquals("已订阅",DiscoveryPresentation.statusLabel(new JSONObject().put("subscription_status","SUBSCRIBED")));
        assertEquals("待订阅",DiscoveryPresentation.statusLabel(new JSONObject().put("subscription_status","WANTED")));
        assertEquals("已暂停",DiscoveryPresentation.statusLabel(new JSONObject().put("subscription_status","PAUSED")));
        assertEquals("未发行",DiscoveryPresentation.statusLabel(new JSONObject().put("subscription_status","PENDING_RELEASE")));
        assertNull(DiscoveryPresentation.statusLabel(new JSONObject()));
        assertNull(DiscoveryPresentation.statusLabel(new JSONObject().put("subscription_status","NONE")));
    }
    @Test public void yearPrefersReleaseDateThenFirstAirDateThenNumericYear() throws Exception {
        assertEquals("2020",DiscoveryPresentation.yearOf(new JSONObject().put("release_date","2020-05-01")));
        assertEquals("2019",DiscoveryPresentation.yearOf(new JSONObject().put("first_air_date","2019-02-03")));
        assertEquals("2018",DiscoveryPresentation.yearOf(new JSONObject().put("year",2018)));
        assertEquals("",DiscoveryPresentation.yearOf(new JSONObject()));
    }
    @Test public void mediaTypeMapsSeriesAndTvAndFallsBackToBrowseKind() throws Exception {
        assertEquals("tv",DiscoveryPresentation.mediaTypeOf(new JSONObject().put("media_type","tv"),"movie"));
        assertEquals("tv",DiscoveryPresentation.mediaTypeOf(new JSONObject().put("media_type","series"),"movie"));
        assertEquals("movie",DiscoveryPresentation.mediaTypeOf(new JSONObject().put("media_type","movie"),"series"));
        assertEquals("tv",DiscoveryPresentation.mediaTypeOf(new JSONObject(),"series"));
    }

    // ---------- 共享池版本序号与获取方式 ----------
    @Test public void versionLabelCountsOnlyActualVersions() throws Exception {
        JSONObject shell=new JSONObject().put("title","剧集");
        shell.put("versions",new JSONArray()
            .put(new JSONObject().put("sha1","a"))
            .put(new JSONObject().put("sha1","b"))
            .put((Object)null));
        List<CloudResources.Resource> rows=CloudResources.parse("shared_pool",new JSONObject().put("resources",new JSONArray().put(shell)),null);
        assertEquals(2,rows.size());
        assertEquals("版本 1/2",CloudResources.versionLabel(rows.get(0)));
        assertEquals("版本 2/2",CloudResources.versionLabel(rows.get(1)));
        assertEquals("",CloudResources.versionLabel(new CloudResources.Resource("shared_pool",new JSONObject().put("sha1","solo"))));
    }
    @Test public void transferLabelPrefersValidShareChannelOverRapid() throws Exception {
        assertEquals("可转存",CloudResources.transferLabel(new CloudResources.Resource("shared_pool",new JSONObject().put("share_transfer_available",true))));
        assertEquals("可转存",CloudResources.transferLabel(new CloudResources.Resource("shared_pool",new JSONObject().put("share_channel",new JSONObject().put("status","valid")))));
        assertEquals("可转存",CloudResources.transferLabel(new CloudResources.Resource("shared_pool",new JSONObject().put("preferred_transfer_mode","share"))));
        assertEquals("可秒传",CloudResources.transferLabel(new CloudResources.Resource("shared_pool",new JSONObject())));
        assertEquals("",CloudResources.transferLabel(new CloudResources.Resource("re0",new JSONObject())));
    }

    // ---------- 资源标签 ----------
    @Test public void chipsCollectVersionTagsFromSummaryAndTopLevel() throws Exception {
        JSONObject source=new JSONObject()
            .put("title","资源一")
            .put("pan_type","rapid115")
            .put("total_size",4_831_838_208L)
            .put("video_codec","H265")
            .put("progress_text","24/24")
            .put("tag_labels",new JSONArray().put("一致版"))
            .put("version_summary",new JSONObject().put("resolution","2160p").put("effect_key","杜比视界").put("frame_rate","24000/1001"))
            .put("_version_index",1).put("_version_count",3);
        List<CloudResources.Chip> chips=CloudResources.chips(new CloudResources.Resource("shared_pool",source));
        List<String> labels=new java.util.ArrayList<>();
        for(CloudResources.Chip chip:chips)labels.add(chip.text);
        assertTrue(labels.contains("115秒传"));
        assertTrue(labels.contains("4.50 GB"));
        assertTrue(labels.contains("2160p"));
        assertTrue(labels.contains("杜比视界"));
        assertTrue(labels.contains("H265"));
        assertTrue(labels.contains("23.976 fps"));
        assertTrue(labels.contains("24/24 集"));
        assertTrue(labels.contains("一致版"));
        assertTrue(labels.size()<=10);
    }
    @Test public void chipsParseStringSummaryContainers() throws Exception {
        JSONObject source=new JSONObject().put("summary_json","{\"resolution\":\"1080p\",\"effect\":\"HDR10\"}");
        List<CloudResources.Chip> chips=CloudResources.chips(new CloudResources.Resource("shared_pool",source));
        List<String> labels=new java.util.ArrayList<>();
        for(CloudResources.Chip chip:chips)labels.add(chip.text);
        assertTrue(labels.contains("1080p"));
        assertTrue(labels.contains("HDR10"));
    }
    @Test public void chipsMarkRe0PointsAndCompletion() throws Exception {
        JSONObject source=new JSONObject().put("title","re0资源").put("unlock_points",30).put("_completion_label","完整资源");
        List<CloudResources.Chip> chips=CloudResources.chips(new CloudResources.Resource("re0",source));
        List<String> labels=new java.util.ArrayList<>();
        for(CloudResources.Chip chip:chips)labels.add(chip.text);
        assertTrue(labels.contains("30 积分"));
        assertTrue(labels.contains("完整资源"));
        JSONObject owned=new JSONObject().put("already_owned",true);
        List<CloudResources.Chip> ownedChips=CloudResources.chips(new CloudResources.Resource("re0",owned));
        assertTrue(ownedChips.stream().anyMatch(chip->chip.text.equals("已解锁")));
    }
    @Test public void chipsShowChannelSourceAndDate() throws Exception {
        JSONObject source=new JSONObject().put("source_channel","分享频道").put("message_date","2026-10-29");
        List<CloudResources.Chip> chips=CloudResources.chips(new CloudResources.Resource("channel",source));
        List<String> labels=new java.util.ArrayList<>();
        for(CloudResources.Chip chip:chips)labels.add(chip.text);
        assertTrue(labels.contains("2026-10-29"));
        assertTrue(labels.contains("来源：分享频道"));
    }
    @Test public void chipsSkipSdrAndFlagBooleans() throws Exception {
        JSONObject source=new JSONObject().put("version_summary",new JSONObject().put("effect_key","SDR"))
            .put("is_clean_version",true).put("is_animation",true).put("is_short_drama",true);
        List<CloudResources.Chip> chips=CloudResources.chips(new CloudResources.Resource("shared_pool",source));
        List<String> labels=new java.util.ArrayList<>();
        for(CloudResources.Chip chip:chips)labels.add(chip.text);
        assertFalse(labels.contains("SDR"));
        assertTrue(labels.contains("纯净版"));
        assertTrue(labels.contains("动漫"));
        assertTrue(labels.contains("短剧"));
    }

    // ---------- 尺寸/进度格式化 ----------
    @Test public void sizeTextPrefersProvidedTextThenFormatsBytes() throws Exception {
        assertEquals("4.2 GB",CloudResources.sizeText(new JSONObject().put("share_size","4.2 GB")));
        assertEquals("512 B",CloudResources.sizeText(new JSONObject().put("size",512)));
        assertEquals("1.5 KB",CloudResources.sizeText(new JSONObject().put("total_size",1536)));
        assertEquals("3.00 GB",CloudResources.sizeText(new JSONObject().put("size",3*1024*1024*1024L)));
        assertEquals("",CloudResources.sizeText(new JSONObject()));
    }
    @Test public void episodeProgressAcceptsTextCountsAndFallback() throws Exception {
        assertEquals("24/24 集",CloudResources.episodeProgress(new JSONObject().put("progress_text","24 / 24")));
        assertEquals("8/12 集",CloudResources.episodeProgress(new JSONObject().put("progress_current",8).put("progress_total",12)));
        assertEquals("10 集",CloudResources.episodeProgress(new JSONObject().put("file_count",10)));
        assertEquals("",CloudResources.episodeProgress(new JSONObject()));
    }
    @Test public void frameRateHandlesFractionsAndPlainNumbers() throws Exception {
        assertEquals("23.976 fps",CloudResources.frameRateLabel(new JSONObject().put("version_summary",new JSONObject().put("frame_rate","24000/1001"))));
        assertEquals("24 fps",CloudResources.frameRateLabel(new JSONObject().put("fps","24")));
        assertEquals("",CloudResources.frameRateLabel(new JSONObject()));
    }
    @Test public void firstValueWalksNestedContainersAndSkipsUnknown() throws Exception {
        JSONObject source=new JSONObject()
            .put("version_summary",new JSONObject().put("video_codec","未知"))
            .put("media_signature_json","{\"video_codec\":\"AV1\"}");
        assertEquals("AV1",CloudResources.firstValue(source,"video_codec","codec"));
        assertEquals("",CloudResources.firstValue(source,"missing"));
    }
}
