package xyz.etkn.tv;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TvPlayerFactoryTest {
    @Test public void onlyTranscodeMediaWaitsForEncoder(){
        assertEquals(65,TvPlayerFactory.mediaReadTimeoutSeconds("/api/tv/Transcodes/session/58.ts"));
        assertEquals(10,TvPlayerFactory.mediaReadTimeoutSeconds("/api/tv/Transcodes/session/master.m3u8"));
        assertEquals(10,TvPlayerFactory.mediaReadTimeoutSeconds("/api/tv/Videos/1/stream"));
        assertEquals(10,TvPlayerFactory.mediaReadTimeoutSeconds("/cdn/58.ts"));
    }
}
