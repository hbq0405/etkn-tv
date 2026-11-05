package xyz.etkn.tv;

import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.TrackOutput;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class DiscPgsSampleAssemblerTest {
    private static byte[] section(int type,int... values){
        byte[] result=new byte[values.length+3];result[0]=(byte)type;result[1]=(byte)(values.length>>8);result[2]=(byte)values.length;
        for(int i=0;i<values.length;i++)result[i+3]=(byte)values[i];return result;
    }
    private static byte[] pcs(int count){return section(0x16,0,100,0,100,16,0,1,0,0,0,count);}
    private static byte[] join(byte[]... packets)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] packet:packets)out.write(packet);return out.toByteArray();}
    private static final class Sink {
        final TrackOutput target=mock(TrackOutput.class);
        final List<byte[]> data=new ArrayList<>();final List<Long> times=new ArrayList<>();
        final List<Integer> flags=new ArrayList<>();final DiscPgsSampleAssembler assembler=new DiscPgsSampleAssembler(target);
        Sink(){
            doAnswer(call->{ParsableByteArray input=call.getArgument(0);int size=call.getArgument(1);byte[] bytes=new byte[size];input.readBytes(bytes,0,size);data.add(bytes);return null;}).when(target).sampleData(any(ParsableByteArray.class),anyInt());
            doAnswer(call->{times.add(call.getArgument(0));flags.add(call.getArgument(1));return null;}).when(target).sampleMetadata(anyLong(),anyInt(),anyInt(),anyInt(),isNull());
        }
        void send(byte[] packet,long pts){assembler.sample(packet,packet.length,pts,1);}
    }
    @Test public void splitAnimationDoesNotEmitEmptyFramesAndUsesCompositionPts()throws Exception{
        Sink sink=new Sink();byte[] first=join(pcs(1),section(0x14,0,0),section(0x80));sink.send(first,1000000);
        byte[] composition=pcs(1),palette=section(0x14,0,1,1,235,128,128,128),object=section(0x15,0,1,0,0xc0,0,0,5,0,1,0,1,1),end=section(0x80);
        sink.send(composition,2000000);sink.send(palette,1999000);sink.send(object,1999000);
        assertEquals("尚未完成的PGS显示集不得替换屏幕字幕",1,sink.times.size());
        sink.send(end,1999000);assertEquals(Arrays.asList(1000000L,2000000L),sink.times);
        assertArrayEquals(join(composition,palette,object,end),sink.data.get(1));
        byte[] clear=join(pcs(0),end);sink.send(clear,3000000);
        assertEquals(Arrays.asList(1000000L,2000000L,3000000L),sink.times);assertArrayEquals(clear,sink.data.get(2));
    }
    @Test public void completeDisplaySetsKeepBytesAndTracksStayIndependent()throws Exception{
        Sink left=new Sink(),right=new Sink();byte[] first=join(pcs(1),section(0x80)),second=join(pcs(0),section(0x80));
        left.send(pcs(1),100);right.send(first,200);assertTrue(left.times.isEmpty());assertEquals(Arrays.asList(200L),right.times);
        left.send(section(0x80),90);assertEquals(Arrays.asList(100L),left.times);assertArrayEquals(first,left.data.get(0));
        right.send(join(first,second),300);assertEquals(Arrays.asList(200L,300L,300L),right.times);assertArrayEquals(second,right.data.get(2));
    }
    @Test public void seekAndIncompleteOrMalformedSetsNeverPublishAFalseClear()throws Exception{
        Sink sink=new Sink();sink.send(pcs(1),100);sink.assembler.reset();sink.send(section(0x80),100);
        assertTrue("跳转后不能发布旧显示集尾包",sink.times.isEmpty());
        sink.send(pcs(1),200);sink.send(new byte[]{0x15,0,10,0},200);sink.send(section(0x80),200);assertTrue(sink.times.isEmpty());
        byte[] valid=join(pcs(0),section(0x80));sink.send(pcs(1),300);sink.send(valid,400);
        assertEquals(Arrays.asList(400L),sink.times);assertArrayEquals(valid,sink.data.get(0));
    }
}