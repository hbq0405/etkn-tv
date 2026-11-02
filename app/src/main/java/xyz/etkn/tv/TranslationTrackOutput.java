package xyz.etkn.tv;

import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.ForwardingTrackOutput;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.text.CueDecoder;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Tee only the selected text track after demuxing. Never do network work on the loader thread. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class TranslationTrackOutput extends ForwardingTrackOutput {
    interface Sink { void cue(long startMs,long endMs,String text); }
    private final Sink sink;
    private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    private Format format;
    private SubtitleParser parser;
    TranslationTrackOutput(TrackOutput output,Sink sink){super(output);this.sink=sink;}
    void reset(){bytes.reset();if(parser!=null)parser.reset();}
    @Override public void format(Format value){
        format=value;DefaultSubtitleParserFactory factory=new DefaultSubtitleParserFactory();
        parser=factory.supportsFormat(value)?factory.create(value):null;
        super.format(value);
    }
    private void capture(byte[] data,int offset,int length){
        if(bytes.size()+length>1024*1024){bytes.reset();return;}
        bytes.write(data,offset,length);
    }
    @Override public void sampleData(ParsableByteArray data,int length,int part){
        if(part==TrackOutput.SAMPLE_DATA_PART_MAIN)capture(data.getData(),data.getPosition(),length);
        super.sampleData(data,length,part);
    }
    @Override public void sampleData(ParsableByteArray data,int length){sampleData(data,length,TrackOutput.SAMPLE_DATA_PART_MAIN);}
    @Override public int sampleData(DataReader input,int length,boolean allowEnd,int part)throws IOException{
        return super.sampleData((data,offset,count)->{int read=input.read(data,offset,count);if(read>0&&part==TrackOutput.SAMPLE_DATA_PART_MAIN)capture(data,offset,read);return read;},length,allowEnd,part);
    }
    @Override public int sampleData(DataReader input,int length,boolean allowEnd)throws IOException{return sampleData(input,length,allowEnd,TrackOutput.SAMPLE_DATA_PART_MAIN);}
    @Override public void sampleMetadata(long timeUs,int flags,int size,int offset,CryptoData crypto){
        byte[] data=bytes.toByteArray();bytes.reset();
        if(offset>0&&offset<=data.length)bytes.write(data,data.length-offset,offset);
        int start=data.length-offset-size;
        if(crypto==null&&format!=null&&start>=0){
            try{
                if(MimeTypes.APPLICATION_MEDIA3_CUES.equals(format.sampleMimeType))emit(new CueDecoder().decode(timeUs,data,start,size),0);
                else if(parser!=null){
                    long shift=format.subsampleOffsetUs==Format.OFFSET_SAMPLE_RELATIVE?timeUs:format.subsampleOffsetUs;
                    parser.parse(data,start,size,SubtitleParser.OutputOptions.allCues(),cues->emit(cues,shift));
                }
            }catch(RuntimeException ignored){/* Subtitle generation must never interrupt original playback. */}
        }
        super.sampleMetadata(timeUs,flags,size,offset,crypto);
    }
    private void emit(CuesWithTiming group,long offsetUs){
        if(group.startTimeUs==C.TIME_UNSET||group.durationUs==C.TIME_UNSET||group.durationUs<=0)return;
        for(Cue cue:group.cues)if(cue.text!=null&&!cue.text.toString().trim().isEmpty())
            sink.cue((group.startTimeUs+offsetUs)/1000,(group.startTimeUs+offsetUs+group.durationUs)/1000,cue.text.toString());
    }
}
