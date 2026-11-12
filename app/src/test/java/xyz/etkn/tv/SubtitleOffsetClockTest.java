package xyz.etkn.tv;

import androidx.media3.common.C;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.text.TextRenderer;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class SubtitleOffsetClockTest {
    @Test public void previewAndCancelUseOriginalClock()throws Exception{
        SubtitleOffsetClock clock=new SubtitleOffsetClock(60000);
        Renderer text=mock(Renderer.class);when(text.getTrackType()).thenReturn(C.TRACK_TYPE_TEXT);
        Renderer shifted=clock.wrap(text);
        clock.milliseconds=500;shifted.render(10000000,123);
        verify(text).render(9500000,123);
        clock.milliseconds=-500;shifted.render(10000000,124);
        verify(text).render(10500000,124);
        clock.milliseconds=0;shifted.resetPosition(10000000);
        verify(text).resetPosition(10000000);
    }
    @Test public void audioAndVideoKeepOriginalRenderer(){
        SubtitleOffsetClock clock=new SubtitleOffsetClock(60000);
        for(int type:new int[]{C.TRACK_TYPE_AUDIO,C.TRACK_TYPE_VIDEO}){
            Renderer renderer=mock(Renderer.class);when(renderer.getTrackType()).thenReturn(type);when(renderer.getName()).thenReturn("decoder");
            assertSame(renderer,clock.wrap(renderer));
        }
    }
    @Test public void assUsesTheSameClock()throws Exception{
        SubtitleOffsetClock clock=new SubtitleOffsetClock(60000);clock.milliseconds=1000;
        Renderer ass=mock(Renderer.class);when(ass.getName()).thenReturn("AssRenderer");
        clock.wrap(ass).render(10000000,0);verify(ass).render(9000000,0);
    }
    @Test public void delayedTextStillEndsAtVideoEnd()throws Exception{
        SubtitleOffsetClock clock=new SubtitleOffsetClock(60000);clock.milliseconds=500;
        TextRenderer text=mock(TextRenderer.class);when(text.getTrackType()).thenReturn(C.TRACK_TYPE_TEXT);when(text.isCurrentStreamFinal()).thenReturn(true);
        clock.wrap(text).render(60000000,0);
        verify(text).setFinalStreamEndPositionUs(59500000);
    }
    @Test public void independentSubtitleRetainsCalibrationAfterDeliveryChanges()throws Exception{
        org.json.JSONObject original=new org.json.JSONObject("{\"MediaStreams\":[{\"Type\":\"Subtitle\",\"Index\":3,\"SubtitleOffsetKey\":\"key\",\"SubtitleOffsetMs\":500}]}");
        org.json.JSONObject delivered=new org.json.JSONObject("{\"MediaStreams\":[{\"Type\":\"Subtitle\",\"Index\":3,\"IsBurnedIn\":false},{\"Type\":\"Subtitle\",\"Index\":4}]}");
        SubtitleOffsetSession.inheritCalibration(delivered,original);
        org.json.JSONObject subtitle=PlaybackSelection.stream(delivered,3,true);
        assertEquals(500,subtitle.getInt("SubtitleOffsetMs"));assertEquals("key",subtitle.getString("SubtitleOffsetKey"));
        assertFalse(subtitle.getBoolean("IsBurnedIn"));assertFalse(PlaybackSelection.stream(delivered,4,true).has("SubtitleOffsetKey"));
    }
}
