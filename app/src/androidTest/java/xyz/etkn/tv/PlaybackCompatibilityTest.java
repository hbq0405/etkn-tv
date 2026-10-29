package xyz.etkn.tv;

import android.graphics.Color;
import androidx.media3.common.*;
import androidx.media3.common.text.Cue;
import androidx.media3.extractor.text.*;
import androidx.media3.exoplayer.ExoPlaybackException;
import org.junit.Test;
import java.io.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class PlaybackCompatibilityTest {
    private static byte[] bytes(int... values){byte[] result=new byte[values.length];for(int i=0;i<values.length;i++)result[i]=(byte)values[i];return result;}
    private static byte[] join(byte[]... values)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] value:values)out.write(value);return out.toByteArray();}
    private static byte[] section(int type,byte[] payload)throws IOException{return join(bytes(type,payload.length>>8,payload.length),payload);}
    private static byte[] composition(int state,int count)throws IOException{
        byte[] header=bytes(0,100,0,100,16,0,1,state,0,0,count);
        for(int i=0;i<count;i++)header=join(header,bytes(0,i+1,0,0,0,10+i*30,0,20));
        return section(0x16,header);
    }
    private static byte[] palette()throws IOException{return section(0x14,bytes(0,0,1,235,128,128,255,2,81,240,90,255));}
    private static byte[] object(int id,byte[] rle)throws IOException{return section(0x15,join(bytes(0,id,0,0xc0,0,0,rle.length+4,0,2,0,1),rle));}
    static byte[] sample()throws IOException{return join(composition(0x80,2),palette(),object(1,bytes(1,1)),object(2,bytes(2,2)),section(0x80,bytes()));}
    static byte[] supSample()throws IOException{
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        for(int seconds:new int[]{0,15}){
            byte[] data=seconds==0?sample():join(composition(0,0),section(0x80,bytes()));int pts=seconds*90000;
            for(int position=0;position<data.length;){
                int size=(data[position+1]&255)<<8|(data[position+2]&255);
                output.write(bytes('P','G',pts>>24,pts>>16,pts>>8,pts,0,0,0,0));output.write(data,position,size+3);position+=size+3;
            }
        }
        return output.toByteArray();
    }
    private static List<Cue> parse(SubtitleParser parser,byte[] data){List<Cue> result=new ArrayList<>();parser.parse(data,SubtitleParser.OutputOptions.allCues(),value->result.addAll(value.cues));return result;}
    private static SubtitleOutputBuffer decode(SubtitleDecoder decoder,byte[] data,long timeUs)throws Exception{
        long deadline=System.nanoTime()+5000000000L;SubtitleInputBuffer input;
        while((input=decoder.dequeueInputBuffer())==null&&System.nanoTime()<deadline)Thread.sleep(1);
        assertNotNull("PGS input buffer timeout",input);
        input.timeUs=timeUs;input.subsampleOffsetUs=Format.OFFSET_SAMPLE_RELATIVE;
        input.ensureSpaceForWrite(data.length);input.data.put(data);input.flip();decoder.queueInputBuffer(input);
        SubtitleOutputBuffer output;
        while((output=decoder.dequeueOutputBuffer())==null&&System.nanoTime()<deadline)Thread.sleep(1);
        assertNotNull("PGS worker output timeout",output);return output;
    }
    @Test public void embeddedPgsUsesWorkerWhileExternalSupAndOtherFormatsKeepParsing()throws Exception{
        SubtitleParser.Factory delegate=new DefaultSubtitleParserFactory();
        SubtitleParser.Factory embedded=new TvSubtitleParserFactory(delegate,false),external=new TvSubtitleParserFactory(delegate);
        Format pgs=new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_PGS).build();
        assertFalse(embedded.supportsFormat(pgs));assertTrue(external.supportsFormat(pgs));
        for(String mime:new String[]{MimeTypes.TEXT_SSA,MimeTypes.APPLICATION_SUBRIP,MimeTypes.TEXT_VTT}){
            Format format=new Format.Builder().setSampleMimeType(mime).build();
            assertEquals(delegate.supportsFormat(format),embedded.supportsFormat(format));
        }
        List<CuesWithTiming> events=new ArrayList<>();external.create(pgs).parse(supSample(),SubtitleParser.OutputOptions.allCues(),events::add);
        assertEquals(15000000,events.get(0).durationUs);assertTrue(events.get(1).cues.isEmpty());
        SubtitleDecoder decoder=new TvSubtitleDecoderFactory().createDecoder(pgs);
        try{
            assertEquals("TvPgsDecoder",decoder.getName());
            SubtitleOutputBuffer output=decode(decoder,sample(),5000000);
            try{assertEquals(5000000,output.getEventTime(0));assertTrue(output.getCues(4999999).isEmpty());
                assertEquals(2,output.getCues(5000000).size());assertEquals(2,output.getCues(5000000).get(0).bitmap.getWidth());
                assertEquals(Color.rgb(235,235,235),output.getCues(5000000).get(0).bitmap.getPixel(0,0));
            }finally{output.release();}
            byte[] reused=join(composition(0,2),section(0x80,bytes()));
            output=decode(decoder,reused,6000000);try{assertEquals(2,output.getCues(6000000).size());}finally{output.release();}
            decoder.flush();
            output=decode(decoder,reused,7000000);try{assertTrue(output.getCues(7000000).isEmpty());}finally{output.release();}
        }finally{decoder.release();}
    }
    @Test public void multipleObjectsArePreservedWhereStockPgsOnlyReturnsOne()throws Exception{
        Format format=new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_PGS).build();
        assertEquals("确认旧解析器只保留一个对象",1,parse(new DefaultSubtitleParserFactory().create(format),sample()).size());
        List<Cue> cues=parse(new TvSubtitleParserFactory(new DefaultSubtitleParserFactory()).create(format),sample());
        assertEquals(2,cues.size());assertEquals(.1f,cues.get(0).position,.0001f);assertEquals(.4f,cues.get(1).position,.0001f);
        assertEquals(Color.rgb(235,235,235),cues.get(0).bitmap.getPixel(0,0));assertTrue(Color.red(cues.get(1).bitmap.getPixel(0,0))>200);
        ByteArrayOutputStream zipped=new ByteArrayOutputStream();try(DeflaterOutputStream zip=new DeflaterOutputStream(zipped)){zip.write(sample());}
        assertEquals(2,parse(new TvPgsParser(),zipped.toByteArray()).size());
    }
    @Test public void pgsHandlesFragmentedObjectsReusedPalettesClearingAndSeek()throws Exception{
        TvPgsParser parser=new TvPgsParser();
        byte[] first=join(composition(0x80,1),palette(),section(0x15,bytes(0,1,0,0x80,0,0,6,0,2,0,1,1)));
        assertTrue(parse(parser,first).isEmpty());
        assertEquals(1,parse(parser,join(section(0x15,bytes(0,1,0,0x40,1)),section(0x80,bytes()))).size());
        assertEquals(1,parse(parser,join(composition(0,1),section(0x80,bytes()))).size());
        assertTrue(parse(parser,join(composition(0,0),section(0x80,bytes()))).isEmpty());
        parser.reset();assertTrue(parse(parser,join(composition(0,1),section(0x80,bytes()))).isEmpty());
    }
    @Test public void externalSupKeepsTimestampsAndClearEvents()throws Exception{
        List<CuesWithTiming> events=new ArrayList<>();new TvPgsParser().parse(supSample(),SubtitleParser.OutputOptions.allCues(),events::add);
        assertEquals(2,events.size());assertEquals(2,events.get(0).cues.size());assertEquals(0,events.get(0).startTimeUs);
        assertEquals(15000000,events.get(0).durationUs);assertTrue(events.get(1).cues.isEmpty());assertEquals(15000000,events.get(1).startTimeUs);
    }
    @Test public void malformedPgsRunLengthsAndOversizedObjectsDoNotBreakPlayback()throws Exception{
        for(byte[] bad:new byte[][]{bytes(0),bytes(0,0x40),bytes(0,0x82),bytes(0,0x85,1)}){
            assertTrue(parse(new TvPgsParser(),join(composition(0x80,1),palette(),object(1,bad),section(0x80,bytes()))).isEmpty());
        }
        byte[] oversized=section(0x15,bytes(0,1,0,0xc0,0,0,6,255,255,255,255,1,1));
        assertTrue(parse(new TvPgsParser(),join(composition(0x80,1),palette(),oversized,section(0x80,bytes()))).isEmpty());
        assertEquals(2,parse(new TvPgsParser(),sample()).size());
    }
    @Test public void p7IsDeclaredAsHevcBeforeSelectionAndP5KeepsDolbyColor() {
        Format p7=new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).setCodecs("dvhe.07.06").setWidth(3840).setHeight(2160).build();
        Format base=TvExtractorsFactory.baseLayer(p7);assertEquals(MimeTypes.VIDEO_H265,base.sampleMimeType);assertEquals(3840,base.width);
        Format p5=p7.buildUpon().setCodecs("dvhe.05.06").build();assertSame(p5,TvExtractorsFactory.baseLayer(p5));
        Format p8=p7.buildUpon().setCodecs("dvhe.08.06").build();assertSame(p8,TvExtractorsFactory.baseLayer(p8));
    }
    @Test public void textAndAudioErrorsCannotTriggerVideoRecovery(){
        for(String mime:new String[]{MimeTypes.APPLICATION_PGS,MimeTypes.TEXT_SSA,MimeTypes.AUDIO_TRUEHD,MimeTypes.VIDEO_H265}){
            PlaybackException error=ExoPlaybackException.createForRenderer(new IllegalStateException("synthetic decoder failure"),"renderer",0,new Format.Builder().setSampleMimeType(mime).build(),C.FORMAT_HANDLED,false,PlaybackException.ERROR_CODE_DECODING_FAILED);
            assertEquals(MimeTypes.isVideo(mime),TvPlayerFactory.rendererFailure(error,C.TRACK_TYPE_VIDEO));
            assertEquals(MimeTypes.isText(mime),TvPlayerFactory.subtitleFailure(error));
        }
    }
}
