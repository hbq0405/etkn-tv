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
    }
}
