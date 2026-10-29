package xyz.etkn.tv;

import android.app.ActivityManager;
import android.content.Context;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.*;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.ui.SubtitleView;
import io.github.peerless2012.ass.media.AssHandler;
import io.github.peerless2012.ass.media.AssHandlerConfig;
import io.github.peerless2012.ass.media.extractor.AssMatroskaExtractor;
import io.github.peerless2012.ass.media.factory.AssRenderersFactory;
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory;
import io.github.peerless2012.ass.media.type.AssRenderType;
import io.github.peerless2012.ass.media.widget.AssSubtitleView;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;

/** Playback policies verified against ChaiChai alpha4, adapted to ETKN's pinned JNI. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class TvPlayerFactory {
    private static final OkHttpClient HTTP=new OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS)
        .readTimeout(10,TimeUnit.SECONDS).connectionPool(new ConnectionPool(5,5,TimeUnit.MINUTES)).build();
    static ExoPlayer create(Context context,SubtitleView subtitleView,boolean softwareVideo,boolean softwareAudio,boolean enhancedSubtitles){
        if(subtitleView!=null)subtitleView.setStyle(new androidx.media3.ui.CaptionStyleCompat(android.graphics.Color.WHITE,android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT,androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,android.graphics.Color.BLACK,null));
        // Authentication stays in same-origin media URIs; never attach ETKN headers to CDN requests.
        DefaultDataSource.Factory data=new DefaultDataSource.Factory(context,new OkHttpDataSource.Factory(HTTP).setUserAgent(EtknApi.USER_AGENT));
        ActivityManager memory=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        int bufferMb=memory!=null&&memory.isLowRamDevice()?128:256;
        ExoPlayer.Builder builder=new ExoPlayer.Builder(context).setLoadControl(new DefaultLoadControl.Builder()
            .setBufferDurationsMs(50000,120000,2000,5000).setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(12000,false).setTargetBufferBytes(bufferMb*1024*1024).build());
        RenderersFactory renderers=new TvRenderersFactory(context,softwareVideo,softwareAudio);
        AssHandler ass=enhancedSubtitles?new AssHandler(AssRenderType.OVERLAY_CANVAS,new AssHandlerConfig(10000,64,1920*1080)):null;
        // Keep embedded PGS raw for the decoder worker; external SUP still needs extraction parsing.
        SubtitleParser.Factory delegate=ass==null?new DefaultSubtitleParserFactory():new AssSubtitleParserFactory(ass);
        SubtitleParser.Factory parsers=new TvSubtitleParserFactory(delegate,false);
        DefaultExtractorsFactory defaults=new DefaultExtractorsFactory().setSubtitleParserFactory(parsers);
        ExtractorsFactory extractors=defaults;
        if(ass!=null){
            extractors=()->{
                Extractor[] result=defaults.createExtractors();
                for(int i=0;i<result.length;i++)if(result[i] instanceof MatroskaExtractor)result[i]=new AssMatroskaExtractor(parsers,ass,0);
                return result;
            };
            renderers=new AssRenderersFactory(ass,renderers);
        }
        ExoPlayer player=builder.setRenderersFactory(renderers).setMediaSourceFactory(new DefaultMediaSourceFactory(data,new TvExtractorsFactory(extractors)).setSubtitleParserFactory(new TvSubtitleParserFactory(delegate))).build();
        try{
            if(ass!=null){ass.init(player);if(subtitleView!=null)subtitleView.addView(new AssSubtitleView(context,ass));}
            return player;
        }catch(RuntimeException|LinkageError failure){player.release();clearAssView(subtitleView);throw failure;}
    }
    static void clearAssView(SubtitleView view){if(view!=null)for(int i=view.getChildCount()-1;i>=0;i--)if(view.getChildAt(i) instanceof AssSubtitleView)view.removeViewAt(i);}
    static boolean rendererFailure(PlaybackException error,int type){
        if(!(error instanceof ExoPlaybackException))return false;
        ExoPlaybackException failure=(ExoPlaybackException)error;
        if(failure.type!=ExoPlaybackException.TYPE_RENDERER)return false;
        if(failure.rendererFormat!=null)return MimeTypes.getTrackType(failure.rendererFormat.sampleMimeType)==type;
        String name=failure.rendererName==null?"":failure.rendererName;
        return type==C.TRACK_TYPE_TEXT?name.contains("Text")||name.contains("Ass"):type==C.TRACK_TYPE_VIDEO?name.contains("Video"):type==C.TRACK_TYPE_AUDIO&&name.contains("Audio");
    }
    static boolean subtitleFailure(PlaybackException error){
        if(rendererFailure(error,C.TRACK_TYPE_TEXT))return true;
        for(Throwable cause=error;cause!=null;cause=cause.getCause())for(StackTraceElement frame:cause.getStackTrace())
            if(frame.getClassName().startsWith("io.github.peerless2012.ass."))return true;
        return false;
    }
}
