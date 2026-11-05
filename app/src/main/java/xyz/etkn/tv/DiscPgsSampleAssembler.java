package xyz.etkn.tv;

import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.TrackOutput;
import java.io.ByteArrayOutputStream;

/** Publish a disc PGS display set only at END, using its presentation packet timestamp. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class DiscPgsSampleAssembler {
    private static final int MAX_BYTES=16*1024*1024;
    private final TrackOutput output;
    private ByteArrayOutputStream pending;
    private long presentationTimeUs;
    private int presentationFlags;
    DiscPgsSampleAssembler(TrackOutput output){this.output=output;}
    void reset(){pending=null;}
    void sample(byte[] packet,int length,long timeUs,int flags){
        int position=0;
        while(length-position>=3){
            int type=packet[position]&255,size=3+((packet[position+1]&255)<<8)+(packet[position+2]&255);
            if(size>length-position){reset();return;}
            if(type==0x16){
                reset();
                if(size<14)return;
                pending=new ByteArrayOutputStream();presentationTimeUs=timeUs;presentationFlags=flags;
            }
            if(pending!=null){
                if(size>MAX_BYTES-pending.size()){reset();return;}
                pending.write(packet,position,size);
                if(type==0x80){
                    byte[] displaySet=pending.toByteArray();
                    output.sampleData(new ParsableByteArray(displaySet),displaySet.length);
                    output.sampleMetadata(presentationTimeUs,presentationFlags,displaySet.length,0,null);
                    reset();
                }
            }
            position+=size;
        }
        if(position!=length)reset();
    }
}