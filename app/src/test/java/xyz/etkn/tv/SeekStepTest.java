package xyz.etkn.tv;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SeekStepTest {
    @Test public void repeatedSeekScalesWithDurationAndSingleTapStaysPrecise(){
        assertEquals(10000,PlaybackSelection.seekStepMillis(-1,true));
        assertEquals(10000,PlaybackSelection.seekStepMillis(45*60000L,true));
        assertEquals(20000,PlaybackSelection.seekStepMillis(90*60000L,true));
        assertEquals(27000,PlaybackSelection.seekStepMillis(120*60000L,true));
        assertEquals(40000,PlaybackSelection.seekStepMillis(180*60000L,true));
        assertEquals(60000,PlaybackSelection.seekStepMillis(6*3600000L,true));
        assertEquals(10000,PlaybackSelection.seekStepMillis(180*60000L,false));
    }
}
