package xyz.etkn.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackVideoOutputTest {
    private JSONObject source(JSONObject video)throws Exception{return new JSONObject().put("MediaStreams",new JSONArray().put(video.put("Type","Video")));}
    @Test public void hdrAndDolbySourcesKeepHardwareHdrOutputOnL055fcn()throws Exception{
        for(JSONObject video:new JSONObject[]{new JSONObject().put("VideoRange","HDR10"),new JSONObject().put("VideoRange","HLG"),new JSONObject().put("VideoRange","DolbyVision HDR10"),new JSONObject().put("ExtendedVideoType","DolbyVision"),new JSONObject().put("ColorTransfer","smpte2084"),new JSONObject().put("ColorTransfer","arib-std-b67")}){
            JSONObject media=source(video);
            assertFalse(PlaybackSelection.useTextureVideo(false,"L055FCN",28,media));
            assertTrue("软件兼容模式仍使用原有输出",PlaybackSelection.useTextureVideo(true,"L055FCN",28,media));
        }
    }
    @Test public void sdrAndMissingMetadataKeepTheVerifiedRotationWorkaround()throws Exception{
        assertTrue(PlaybackSelection.useTextureVideo(false,"L055FCN",28,source(new JSONObject().put("VideoRange","SDR"))));
        assertTrue(PlaybackSelection.useTextureVideo(false,"L055FCN",28,null));
        assertTrue(PlaybackSelection.useTextureVideo(false,"L055FCN",28,new JSONObject()));
        JSONObject audio=new JSONObject().put("MediaStreams",new JSONArray().put(new JSONObject().put("Type","Audio").put("VideoRange","HDR10")));
        assertTrue("只能由视频轨道选择输出",PlaybackSelection.useTextureVideo(false,"L055FCN",28,audio));
    }
    @Test public void otherDevicesKeepSurfaceAndSoftwareKeepsTexture()throws Exception{
        assertFalse(PlaybackSelection.useTextureVideo(false,"other-tv",28,null));
        assertFalse(PlaybackSelection.useTextureVideo(false,"L055FCN",29,null));
        assertTrue(PlaybackSelection.useTextureVideo(true,"other-tv",28,null));
    }
}
