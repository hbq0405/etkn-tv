package xyz.etkn.tv;

import android.os.SystemClock;
import android.util.Log;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Startup diagnostics only; no addresses, authentication headers or media names. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackTiming implements TransferListener {
    private static final AtomicInteger IDS=new AtomicInteger();
    private final int trace=IDS.incrementAndGet();
    private final long started=SystemClock.elapsedRealtime();
    private final String item;
    private final Map<DataSource,Transfer> transfers=new IdentityHashMap<>();
    private int sequence;
    private static final class Transfer {
        int id; long started,bytes; boolean first,mib;
    }
    PlaybackTiming(String item){this.item=item!=null&&item.matches("item-[0-9]+")?item:"";event("activity_created");}
    void event(String event){event(event,"");}
    void event(String event,String values){
        Log.i("EtknPlaybackTiming","trace="+trace+" item="+item+" event="+event+" elapsed_ms="+(SystemClock.elapsedRealtime()-started)+" "+values);
    }
    @Override public synchronized void onTransferInitializing(DataSource source,DataSpec spec,boolean network){
        Transfer transfer=new Transfer();transfer.id=++sequence;transfer.started=SystemClock.elapsedRealtime();transfers.put(source,transfer);
        event("transfer_init","load="+transfer.id+" position="+spec.position+" length="+spec.length+" network="+network);
    }
    @Override public synchronized void onTransferStart(DataSource source,DataSpec spec,boolean network){
        Transfer transfer=transfers.get(source);if(transfer==null)return;
        event("transfer_open","load="+transfer.id+" open_ms="+(SystemClock.elapsedRealtime()-transfer.started));
    }
    @Override public synchronized void onBytesTransferred(DataSource source,DataSpec spec,boolean network,int bytes){
        Transfer transfer=transfers.get(source);if(transfer==null)return;transfer.bytes+=bytes;
        if(!transfer.first){transfer.first=true;event("first_byte","load="+transfer.id+" read_ms="+(SystemClock.elapsedRealtime()-transfer.started)+" bytes="+bytes);}
        if(!transfer.mib&&transfer.bytes>=1024*1024){transfer.mib=true;event("first_mib","load="+transfer.id+" read_ms="+(SystemClock.elapsedRealtime()-transfer.started)+" bytes="+transfer.bytes);}
    }
    @Override public synchronized void onTransferEnd(DataSource source,DataSpec spec,boolean network){
        Transfer transfer=transfers.remove(source);if(transfer!=null)event("transfer_end","load="+transfer.id+" read_ms="+(SystemClock.elapsedRealtime()-transfer.started)+" bytes="+transfer.bytes);
    }
}
