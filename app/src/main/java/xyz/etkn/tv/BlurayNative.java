package xyz.etkn.tv;

import androidx.media3.extractor.ExtractorInput;
import java.io.IOException;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class BlurayNative {
    static {System.loadLibrary("etkn_bluray");}
    private BlurayNative(){}
    static final class Track {
        final int index,type,width,height,sampleRate,channels,pcmEncoding;
        final String mime,language;
        final byte[] initialization;
        Track(int index,int type,int width,int height,int sampleRate,int channels,int pcmEncoding,String mime,String language,byte[] initialization){
            this.index=index;this.type=type;this.width=width;this.height=height;this.sampleRate=sampleRate;this.channels=channels;this.pcmEncoding=pcmEncoding;
            this.mime=mime;this.language=language;this.initialization=initialization;
        }
    }
    static native long open(BlurayInput input)throws IOException;
    static native long size(long handle);
    static native long duration(long handle);
    static native long seekPoint(long handle,long timeUs);
    static native long seek(long handle,long position)throws IOException;
    static native int read(long handle,byte[] bytes,int offset,int length)throws IOException;
    static native Track[] tracks(long handle,ExtractorInput input,long position)throws IOException;
    static native int packet(long handle,ExtractorInput input,byte[] bytes,long[] metadata)throws IOException;
    static native void reset(long handle);
    static native void close(long handle);
}
