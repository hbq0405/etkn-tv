package xyz.etkn.tv;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class TranscodeChoiceTest {
    @Test public void bitrateIsAnUpperLimit()throws Exception{
        JSONObject source=new JSONObject().put("Bitrate",4_000_000);
        assertFalse(TranscodeChoice.required(source,0));
        assertFalse(TranscodeChoice.required(source,4_000_000));
        assertFalse(TranscodeChoice.required(source,8_000_000));
        assertTrue(TranscodeChoice.required(source,2_000_000));
        assertFalse(TranscodeChoice.required(new JSONObject(),2_000_000));
    }
    @Test public void highFrameRateAloneNeverTriggersAssistance()throws Exception{
        JSONObject source=new JSONObject().put("Bitrate",8_000_000).put("MediaStreams",new JSONArray().put(new JSONObject().put("Type","Video").put("RealFrameRate",60)));
        assertFalse(TranscodeChoice.required(source,0));
        JSONObject video=source.getJSONArray("MediaStreams").getJSONObject(0);video.put("Width",3840).put("Height",2160);
        JSONArray caps=new JSONArray().put(new JSONObject().put("Width",3840).put("Height",2160).put("FrameRate",60).put("Bitrate",80_000_000));
        assertFalse(TranscodeChoice.hardwareRequired(source,caps));
        caps.getJSONObject(0).put("FrameRate",30);
        assertTrue(TranscodeChoice.hardwareRequired(source,caps));
        assertFalse(TranscodeChoice.hardwareRequired(source,new JSONArray()));
    }
    @Test public void qualityUsesEitherResolutionOrBitrateAsUpperLimit()throws Exception{
        JSONObject video=new JSONObject().put("Type","Video").put("Width",3840).put("Height",1608);
        JSONObject source=new JSONObject().put("Bitrate",4_000_000).put("MediaStreams",new JSONArray().put(video));
        assertTrue(TranscodeChoice.required(source,20_000_000));
        assertFalse(TranscodeChoice.required(source,80_000_000));
        video.put("Width",1280).put("Height",720);source.put("Bitrate",3_000_000);
        assertTrue(TranscodeChoice.required(source,2_000_000));
        source.put("Bitrate",2_000_000);
        assertFalse(TranscodeChoice.required(source,2_000_000));
        video.put("Width",720).put("Height",1280).put("Rotation",90);
        assertFalse(TranscodeChoice.required(source,2_000_000));
        assertFalse(TranscodeChoice.required(source,0));
    }
    @Test public void qualityPresetsHaveOnlyThreeResolutionsAndKeepSavedLimits(){
        assertEquals("4K · 80Mbps",TranscodeChoice.label(80_000_000));
        assertEquals("4K · 60Mbps",TranscodeChoice.label(60_000_000));
        assertEquals(3840,TranscodeChoice.width(60_000_000));
        assertEquals("720p · 1Mbps",TranscodeChoice.label(1_000_000));
        for(int rate:TranscodeChoice.BITRATES){
            assertEquals(rate,TranscodeChoice.BITRATES[TranscodeChoice.index(rate)]);
            if(rate>0)assertTrue(TranscodeChoice.height(rate)==720||TranscodeChoice.height(rate)==1080||TranscodeChoice.height(rate)==2160);
        }
        assertEquals(1920,TranscodeChoice.width(12_000_000));
        assertEquals(0,TranscodeChoice.width(0));
    }
}
