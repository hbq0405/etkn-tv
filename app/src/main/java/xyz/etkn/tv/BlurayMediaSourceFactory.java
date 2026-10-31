package xyz.etkn.tv;

import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.common.util.CodecSpecificDataUtil;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.*;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.*;
import java.io.IOException;
import java.util.*;

/** ISO uses the same ProgressiveMediaSource loading, seeking and renderer lifecycle as other files. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class BlurayMediaSourceFactory implements MediaSource.Factory {
    private final MediaSource.Factory ordinary;
    private final DataSource.Factory upstream;
    private final ExtractorsFactory extractors;
    private final androidx.media3.extractor.text.SubtitleParser.Factory subtitles;
    BlurayMediaSourceFactory(MediaSource.Factory ordinary,DataSource.Factory upstream,ExtractorsFactory extractors,androidx.media3.extractor.text.SubtitleParser.Factory subtitles){
        this.ordinary=ordinary;this.upstream=upstream;this.extractors=extractors;this.subtitles=subtitles;
    }
    static boolean isIso(Uri uri){String path=uri.getPath();return path!=null&&path.toLowerCase(Locale.ROOT).endsWith(".iso");}
    @Override public MediaSource createMediaSource(MediaItem item){
        Uri uri=Objects.requireNonNull(item.localConfiguration).uri;
        if(!isIso(uri))return ordinary.createMediaSource(item);
        Session session=new Session(new BlurayInput(upstream,uri));
        DataSource.Factory data=()->new DiscDataSource(session,uri,upstream.createDataSource());
        ExtractorsFactory discExtractors=new ExtractorsFactory(){
            @Override public Extractor[] createExtractors(){return new Extractor[]{new DiscExtractor(session)};}
            @Override public Extractor[] createExtractors(Uri address,Map<String,List<String>> headers){return uri.equals(address)?createExtractors():extractors.createExtractors(address,headers);}
        };
        return new DefaultMediaSourceFactory(data,discExtractors).setSubtitleParserFactory(subtitles).createMediaSource(item);
    }
    @Override public int[] getSupportedTypes(){return ordinary.getSupportedTypes();}
    @Override public MediaSource.Factory setDrmSessionManagerProvider(DrmSessionManagerProvider provider){ordinary.setDrmSessionManagerProvider(provider);return this;}
    @Override public MediaSource.Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy policy){ordinary.setLoadErrorHandlingPolicy(policy);return this;}
    private static final class Session {
        final BlurayInput input;long handle;
        Session(BlurayInput input){this.input=input;}
        synchronized long open()throws IOException{if(handle==0)try{handle=BlurayNative.open(input);}catch(LinkageError error){throw new IOException("原盘组件加载失败",error);}return handle;}
        synchronized void close(){if(handle!=0){BlurayNative.close(handle);handle=0;}try{input.close();}catch(IOException ignored){}}
    }
    private static final class DiscDataSource extends BaseDataSource {
        final Session session;final Uri iso;final DataSource fallback;
        boolean disc,started;long remaining;
        DiscDataSource(Session session,Uri iso,DataSource fallback){super(true);this.session=session;this.iso=iso;this.fallback=fallback;}
        @Override public long open(DataSpec spec)throws IOException{
            disc=iso.equals(spec.uri);if(!disc)return fallback.open(spec);
            transferInitializing(spec);
            try{
                long handle=session.open(),size=BlurayNative.size(handle);
                if(spec.position<0||spec.position>size)throw new IOException("原盘跳转超出正片范围");
                if(BlurayNative.seek(handle,spec.position)!=spec.position)throw new IOException("原盘无法跳转到指定位置");
                remaining=size-spec.position;if(spec.length!=C.LENGTH_UNSET)remaining=Math.min(remaining,spec.length);
                transferStarted(spec);started=true;return remaining;
            }catch(IOException|RuntimeException error){session.close();throw error;}
        }
        @Override public int read(byte[] buffer,int offset,int length)throws IOException{
            if(!disc)return fallback.read(buffer,offset,length);if(length==0)return 0;if(remaining==0)return C.RESULT_END_OF_INPUT;
            int count=BlurayNative.read(session.handle,buffer,offset,(int)Math.min(remaining,length));
            if(count<=0)throw new java.io.EOFException("原盘正片数据不完整");remaining-=count;bytesTransferred(count);return count;
        }
        @Override public Uri getUri(){return disc?iso:fallback.getUri();}
        @Override public void close()throws IOException{if(!disc){fallback.close();return;}if(started){started=false;transferEnded();}}
    }
    private static final class DiscExtractor implements Extractor {
        final Session session;ExtractorOutput output;TrackOutput[] tracks;
        byte[] buffer=new byte[2*1024*1024];final long[] metadata=new long[3];
        boolean initialized;
        DiscExtractor(Session session){this.session=session;}
        @Override public boolean sniff(ExtractorInput input){return true;}
        @Override public void init(ExtractorOutput output){this.output=output;}
        @Override public int read(ExtractorInput input,PositionHolder seek)throws IOException{
            if(!initialized){
                BlurayNative.Track[] descriptions=BlurayNative.tracks(session.open(),input,input.getPosition());
                if(tracks==null){
                    int last=0;for(BlurayNative.Track track:descriptions)last=Math.max(last,track.index);
                    tracks=new TrackOutput[last+1];
                    for(BlurayNative.Track track:descriptions){
                        Format.Builder format=new Format.Builder().setId(Integer.toString(track.index)).setSampleMimeType(track.mime).setContainerMimeType(MimeTypes.VIDEO_MP2T).setLanguage(track.language);
                        if(track.type==C.TRACK_TYPE_VIDEO)format.setWidth(track.width).setHeight(track.height).setMaxInputSize(buffer.length);
                        if(track.type==C.TRACK_TYPE_AUDIO)format.setChannelCount(track.channels).setSampleRate(track.sampleRate).setPcmEncoding(track.pcmEncoding);
                        if(track.initialization.length>0){byte[][] nals=MimeTypes.VIDEO_H264.equals(track.mime)?CodecSpecificDataUtil.splitNalUnits(track.initialization):null;format.setInitializationData(nals==null?Collections.singletonList(track.initialization):Arrays.asList(nals));}
                        TrackOutput target=output.track(track.index,track.type);target.format(TvExtractorsFactory.baseLayer(format.build()));tracks[track.index]=target;
                    }
                    output.endTracks();
                    output.seekMap(new SeekMap(){
                        @Override public boolean isSeekable(){return true;}
                        @Override public long getDurationUs(){return BlurayNative.duration(session.handle);}
                        @Override public SeekPoints getSeekPoints(long timeUs){return new SeekPoints(new SeekPoint(timeUs,BlurayNative.seekPoint(session.handle,timeUs)));}
                    });
                }
                initialized=true;
            }
            int count=BlurayNative.packet(session.handle,input,buffer,metadata);
            if(count<-1){if(-count>16*1024*1024)throw new IOException("原盘视频帧过大");buffer=new byte[-count];return RESULT_CONTINUE;}
            if(count==-1)return RESULT_END_OF_INPUT;
            if(count>0){int index=(int)metadata[0];if(index<tracks.length&&tracks[index]!=null){TrackOutput track=tracks[index];track.sampleData(new ParsableByteArray(buffer,count),count);track.sampleMetadata(metadata[1],(int)metadata[2],count,0,null);}}
            return RESULT_CONTINUE;
        }
        @Override public void seek(long position,long timeUs){if(initialized){BlurayNative.reset(session.handle);initialized=false;}}
        @Override public void release(){session.close();}
    }
}
