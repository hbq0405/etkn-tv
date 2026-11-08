package xyz.etkn.tv;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackNetworkTest {
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
