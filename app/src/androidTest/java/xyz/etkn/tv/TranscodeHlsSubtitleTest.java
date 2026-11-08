package xyz.etkn.tv;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.common.*;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.SubtitleView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class TranscodeHlsSubtitleTest {
    @Test public void televisionReportsHevcAndHdr10HardwareCapabilities(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        org.json.JSONArray hevc=TranscodeChoice.hevcCapabilities(),hdr=TranscodeChoice.hdrCapabilities(context);
        android.util.Log.i("EtknHdrProbe","HEVC="+hevc+" HDR10="+hdr);
        android.os.Bundle report=new android.os.Bundle();report.putString("stream","\nDEVICE_HEVC="+hevc+" HDR10="+hdr+"\n");InstrumentationRegistry.getInstrumentation().sendStatus(2,report);
        assertTrue(hevc.length()>0);assertTrue(hdr.length()>0);
    }
    @Test public void hevcHdrTranscodeProducesHardwareFrameWithPqMetadata()throws Exception{
        var instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        byte[] data;try(InputStream in=instrumentation.getContext().getAssets().open("transcode-hdr.ts")){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);data=out.toByteArray();
        }
        try(Fixture fixture=new Fixture(data)){
            AtomicReference<ExoPlayer> player=new AtomicReference<>();AtomicReference<android.graphics.SurfaceTexture> texture=new AtomicReference<>();
            AtomicReference<android.view.Surface> surface=new AtomicReference<>();AtomicReference<Format> format=new AtomicReference<>();
            AtomicReference<String> error=new AtomicReference<>(),decoder=new AtomicReference<>();CountDownLatch frame=new CountDownLatch(1);
            instrumentation.runOnMainSync(()->{
                texture.set(new android.graphics.SurfaceTexture(0));texture.get().setDefaultBufferSize(1280,536);surface.set(new android.view.Surface(texture.get()));
                ExoPlayer value=TvPlayerFactory.create(context,new SubtitleView(context),false,false,true);player.set(value);value.setVideoSurface(surface.get());
                value.addAnalyticsListener(new androidx.media3.exoplayer.analytics.AnalyticsListener(){
                    @Override public void onVideoDecoderInitialized(EventTime time,String name,long at,long duration){decoder.set(name);}
                });
                value.addListener(new Player.Listener(){
                    @Override public void onRenderedFirstFrame(){format.set(value.getVideoFormat());frame.countDown();}
                    @Override public void onPlayerError(PlaybackException failure){error.set(failure.toString());frame.countDown();}
                });
                value.setMediaItem(MediaItem.fromUri("http://127.0.0.1:"+fixture.server.getLocalPort()+"/0.ts"));value.prepare();value.play();
            });
            try{
                assertTrue("HEVC first frame timeout",frame.await(15,TimeUnit.SECONDS));assertNull(error.get());assertNotNull(format.get());
                System.out.println("HDR_FIRST_FRAME decoder="+decoder.get()+" format="+format.get()+" color="+format.get().colorInfo);
                android.os.Bundle report=new android.os.Bundle();report.putString("stream","\nHDR_FIRST_FRAME decoder="+decoder.get()+" color="+format.get().colorInfo+"\n");instrumentation.sendStatus(2,report);
                assertEquals(MimeTypes.VIDEO_H265,format.get().sampleMimeType);assertNotNull(format.get().colorInfo);
                assertEquals(C.COLOR_SPACE_BT2020,format.get().colorInfo.colorSpace);assertEquals(C.COLOR_TRANSFER_ST2084,format.get().colorInfo.colorTransfer);
                assertNotNull(decoder.get());assertFalse(decoder.get().toLowerCase(Locale.ROOT).contains("ffmpeg"));
            }finally{instrumentation.runOnMainSync(()->{player.get().release();surface.get().release();texture.get().release();});}
        }
    }
    @Test public void generatedStreamMustExcludePhantomClosedCaptionTrack() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();
        byte[] video;try(InputStream in=instrumentation.getContext().getAssets().open("transcode-subtitle.ts")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);video=out.toByteArray();}
        try(Fixture fixture=new Fixture(video)){
            for(boolean closedCaptionsNone:new boolean[]{false,true}){
                AtomicReference<ExoPlayer> player=new AtomicReference<>();
                AtomicReference<String> firstText=new AtomicReference<>(),error=new AtomicReference<>();
                AtomicBoolean sawCue=new AtomicBoolean();CountDownLatch ready=new CountDownLatch(1);
                instrumentation.runOnMainSync(()->{
                    ExoPlayer value=TvPlayerFactory.create(context,new SubtitleView(context),false,false,true);player.set(value);
                    value.addListener(new Player.Listener(){
                        @Override public void onPlayerError(PlaybackException failure){error.set(failure.toString());ready.countDown();}
                        @Override public void onTracksChanged(Tracks tracks){
                            if(firstText.get()!=null)return;
                            for(Tracks.Group group:tracks.getGroups())if(group.getType()==C.TRACK_TYPE_TEXT){
                                firstText.set(group.getTrackFormat(0).sampleMimeType);
                                value.setTrackSelectionParameters(value.getTrackSelectionParameters().buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false)
                                    .setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),0)).build());return;
                            }
                        }
                        @Override public void onCues(CueGroup group){if(!group.cues.isEmpty())sawCue.set(true);}
                        @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED)ready.countDown();}
                    });
                    value.setMediaItem(MediaItem.fromUri("http://127.0.0.1:"+fixture.server.getLocalPort()+"/"+(closedCaptionsNone?"fixed":"legacy")+"/master.m3u8"));
                    value.prepare();value.play();
                });
                try{
                    assertTrue("Playback timed out",ready.await(25,TimeUnit.SECONDS));assertNull(error.get());
                    System.out.println("HLS_SUBTITLE none="+closedCaptionsNone+" first="+firstText.get()+" cue="+sawCue.get());
                    if(closedCaptionsNone){assertEquals(MimeTypes.APPLICATION_MEDIA3_CUES,firstText.get());assertTrue("Selected text subtitle must render",sawCue.get());}
                    else{assertEquals(MimeTypes.APPLICATION_CEA608,firstText.get());assertFalse(sawCue.get());}
                }finally{instrumentation.runOnMainSync(()->player.get().release());}
            }
        }
    }
    private static final class Fixture implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0,20,InetAddress.getByName("127.0.0.1"));
        final ExecutorService workers=Executors.newCachedThreadPool();final byte[] video;
        Fixture(byte[] video)throws Exception{this.video=video;workers.execute(()->{while(!server.isClosed())try{Socket socket=server.accept();workers.execute(()->respond(socket));}catch(IOException ignored){}});}
        void respond(Socket socket){try(socket){
            BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
            String request=reader.readLine();if(request==null)return;String path=request.split(" ")[1],line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
            byte[] body;String mime="application/vnd.apple.mpegurl";
            if(path.endsWith(".ts")){body=video;mime="video/mp2t";}
            else if(path.endsWith(".vtt")){body="WEBVTT\nX-TIMESTAMP-MAP=LOCAL:00:00:00.000,MPEGTS:216000\n\n00:00:00.000 --> 00:00:05.000\n字幕测试\n\n".getBytes(StandardCharsets.UTF_8);mime="text/vtt";}
            else if(path.endsWith("master.m3u8"))body=("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"Selected\",DEFAULT=YES,AUTOSELECT=YES,URI=\"text.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,SUBTITLES=\"subs\""+(path.contains("fixed")?",CLOSED-CAPTIONS=NONE":"")+"\nvideo.m3u8\n").getBytes(StandardCharsets.UTF_8);
            else body=("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:6.000000,\n"+(path.endsWith("text.m3u8")?"0.vtt":"0.ts")+"\n#EXT-X-ENDLIST\n").getBytes(StandardCharsets.UTF_8);
            OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: "+mime+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();
        }catch(IOException ignored){}}
        @Override public void close()throws IOException{server.close();workers.shutdownNow();}
    }
}
