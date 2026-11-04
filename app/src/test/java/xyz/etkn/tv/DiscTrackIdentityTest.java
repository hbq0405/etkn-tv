package xyz.etkn.tv;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscTrackIdentityTest {
    @Test public void trueHdAndCompatibilityCoreUsePidAndCodec()throws Exception{
        JSONObject track=new JSONObject().put("TransportStreamId",4352).put("Codec","truehd");
        assertTrue(DiscTrackIdentity.matches(track,"0:"+DiscTrackIdentity.id(4352,"audio/true-hd")));
        assertFalse(DiscTrackIdentity.matches(track,DiscTrackIdentity.id(4352,"audio/ac3")));
        assertFalse(DiscTrackIdentity.matches(track,DiscTrackIdentity.id(4353,"audio/true-hd")));
    }
    @Test public void subtitlePidDoesNotDependOnOrdinalOrLanguage()throws Exception{
        JSONObject track=new JSONObject().put("Index",38).put("Language","chi").put("TransportStreamId",4795).put("Codec","pgssub");
        assertTrue(DiscTrackIdentity.matches(track,DiscTrackIdentity.id(4795,"application/pgs")));
        assertFalse(DiscTrackIdentity.matches(track,DiscTrackIdentity.id(4796,"application/pgs")));
        assertFalse(DiscTrackIdentity.matches(new JSONObject(),"0"));
    }
}
