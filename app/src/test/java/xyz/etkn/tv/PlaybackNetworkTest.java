package xyz.etkn.tv;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackNetworkTest {
    @Test public void repeatedShortStallsSurviveBufferRecoveryAndIdleLoading(){
        AtomicLong clock=new AtomicLong();PlaybackNetwork network=new PlaybackNetwork(null,clock::get);
        network.onTransferStart(null,null,true);
        network.onBytesTransferred(null,null,true,1_000_000);
        network.target(0,true,false,6000,86_000_000,1);
        clock.set(1000);network.target(1000,true,true,0,86_000_000,1);
        clock.set(2000);network.onBytesTransferred(null,null,true,5_000_000);
        assertEquals(0,network.target(2000,true,false,6000,86_000_000,1));
        network.onTransferEnd(null,null,true);
        clock.set(3000);assertEquals(0,network.target(3000,true,false,5000,86_000_000,1));
        network.onTransferStart(null,null,true);
        clock.set(4000);network.target(4000,true,true,0,86_000_000,1);
        clock.set(5000);network.onBytesTransferred(null,null,true,3_000_000);
        assertTrue("Two short rebuffer episodes must lower bitrate",network.target(5000,true,true,0,86_000_000,1)>0);
    }
    @Test public void establishedPlaybackStalledWithoutNewBytesStillLowersBitrate(){
        AtomicLong clock=new AtomicLong();PlaybackNetwork network=new PlaybackNetwork(null,clock::get);
        network.onTransferStart(null,null,true);network.onBytesTransferred(null,null,true,1_000_000);
        network.target(0,true,true,0,86_000_000,1);
        clock.set(4000);
        assertTrue("A stalled media read must not require more bytes to recover",network.target(4000,true,true,0,86_000_000,1)>0);
    }
    @Test public void sustainedSlowReadsLowerBitrateAndContinueAfterCooldown(){
        AtomicLong clock=new AtomicLong();PlaybackNetwork network=new PlaybackNetwork(null,clock::get);
        network.onTransferStart(null,null,true);
        assertEquals(0,network.target(0,true,false,2000,60_000_000,1));
        clock.set(3000);network.onBytesTransferred(null,null,true,6_000_000);
        assertEquals(0,network.target(3000,true,true,0,60_000_000,1));
        clock.set(6000);network.onBytesTransferred(null,null,true,6_000_000);
        assertEquals(12_750_000,network.target(6000,true,true,0,60_000_000,1));
        clock.set(9000);network.onBytesTransferred(null,null,true,3_000_000);
        assertEquals(0,network.target(9000,true,true,0,12_750_000,1));
        clock.set(27000);network.target(27000,true,true,0,12_750_000,1);
        clock.set(33000);network.onBytesTransferred(null,null,true,3_000_000);
        assertEquals(3_000_000,network.target(33000,true,true,0,12_750_000,1));
    }
    @Test public void startupPauseSeekFastNetworkAndHeaderWaitsCannotLowerQuality(){
        AtomicLong clock=new AtomicLong();PlaybackNetwork network=new PlaybackNetwork(null,clock::get);
        assertEquals(0,network.target(0,false,true,0,60_000_000,1));
        clock.set(10000);assertEquals(0,network.target(10000,true,true,0,60_000_000,1));
        clock.set(20000);assertEquals(0,network.target(20000,true,true,0,60_000_000,1));
        network.onTransferStart(null,null,true);network.afterSeek(20000);
        clock.set(25000);network.onBytesTransferred(null,null,true,50_000_000);
        assertEquals(0,network.target(25000,true,true,0,60_000_000,1));
        clock.set(28000);network.target(28000,true,true,0,60_000_000,1);
        clock.set(35000);network.onBytesTransferred(null,null,true,70_000_000);
        assertEquals(0,network.target(35000,true,true,0,60_000_000,1));
        assertEquals(0,network.target(35000,false,true,0,60_000_000,1));
        assertEquals(0,network.target(35000,true,false,20000,60_000_000,1));
    }
}
