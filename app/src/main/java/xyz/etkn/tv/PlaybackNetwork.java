package xyz.etkn.tv;

import androidx.media3.datasource.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Measure media reads after headers, excluding manifests, subtitles and encoder header waits. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackNetwork implements TransferListener {
    private final LongSupplier clock;
    private final Runnable firstBytes;
    private final Set<DataSource> active=Collections.newSetFromMap(new IdentityHashMap<>());
    private long bytes,readMs,activeSince,windowAt=-1,windowBytes,windowRead,stalledAt=-1,holdUntil;
    private boolean notified;
    PlaybackNetwork(Runnable firstBytes){this(firstBytes,android.os.SystemClock::elapsedRealtime);}
    PlaybackNetwork(Runnable firstBytes,LongSupplier clock){this.firstBytes=firstBytes;this.clock=clock;}
    private boolean media(DataSpec spec){
        String path=spec==null?"":String.valueOf(spec.uri.getPath()).toLowerCase(Locale.ROOT);
        return !path.endsWith(".m3u8")&&!path.endsWith(".vtt")&&!path.endsWith(".srt")&&!path.endsWith(".ass")&&!path.endsWith(".sup");
    }
    @Override public void onTransferInitializing(DataSource source,DataSpec spec,boolean network){}
    @Override public synchronized void onTransferStart(DataSource source,DataSpec spec,boolean network){
        if(network&&media(spec)){if(active.isEmpty())activeSince=clock.getAsLong();active.add(source);}
    }
    @Override public void onBytesTransferred(DataSource source,DataSpec spec,boolean network,int count){
        boolean notify=false;
        synchronized(this){if(network&&active.contains(source)){bytes+=count;if(!notified){notified=true;notify=true;}}}
        if(notify&&firstBytes!=null)firstBytes.run();
    }
    @Override public synchronized void onTransferEnd(DataSource source,DataSpec spec,boolean network){
        if(active.remove(source)&&active.isEmpty())readMs+=Math.max(0,clock.getAsLong()-activeSince);
    }
    synchronized void afterSeek(long now){holdUntil=now+8000;windowAt=stalledAt=-1;}
    synchronized int target(long now,boolean eligible,boolean buffering,long buffered,long current,double speed){
        long reads=readMs+(active.isEmpty()?0:Math.max(0,now-activeSince));
        if(!eligible||buffered>=5000||now<holdUntil){windowAt=stalledAt=-1;return 0;}
        if(windowAt<0){windowAt=now;windowBytes=bytes;windowRead=reads;}
        if(buffering&&buffered<1500){if(stalledAt<0)stalledAt=now;}else stalledAt=-1;
        long elapsed=reads-windowRead,loaded=bytes-windowBytes;
        if(current<=1_000_000||stalledAt<0||now-stalledAt<3000||now-windowAt<6000||elapsed<3000||loaded<256*1024)return 0;
        long measured=loaded*8000/elapsed;
        if(measured>=current*Math.max(.1,speed)*.9)return 0;
        long target=Math.max(1_000_000,Math.min(current*4/5,(long)(measured*.8/Math.max(.1,speed))));
        holdUntil=now+20000;windowAt=stalledAt=-1;
        return (int)Math.min(1_000_000_000,target/250000*250000);
    }
}
