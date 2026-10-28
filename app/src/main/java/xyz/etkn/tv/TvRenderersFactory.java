package xyz.etkn.tv;

import android.content.Context;
import android.os.Handler;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer;
import java.util.ArrayList;

/** Keep hardware video by default; decode audio to PCM rather than relying on TV passthrough. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class TvRenderersFactory extends DefaultRenderersFactory {
    private final boolean softwareVideo;
    TvRenderersFactory(Context context,boolean softwareVideo){
        super(context);
        this.softwareVideo=softwareVideo;
        setEnableDecoderFallback(true);
        setExtensionRendererMode(softwareVideo?EXTENSION_RENDERER_MODE_PREFER:EXTENSION_RENDERER_MODE_ON);
    }
    @Override protected void buildAudioRenderers(Context context,int mode,MediaCodecSelector selector,
            boolean fallback,AudioSink sink,Handler handler,AudioRendererEventListener listener,ArrayList<Renderer> out){
        int first=out.size();super.buildAudioRenderers(context,EXTENSION_RENDERER_MODE_OFF,selector,fallback,sink,handler,listener,out);
        out.add(first,new FfmpegAudioRenderer(handler,listener,sink));
    }
    @Override protected void buildVideoRenderers(Context context,int mode,MediaCodecSelector selector,
            boolean fallback,Handler handler,VideoRendererEventListener listener,long joining,ArrayList<Renderer> out){
        Renderer hardware=new TvVideoRenderer(context,selector,handler,listener,joining);
        Renderer software=new FfmpegVideoRenderer(joining,handler,listener,50);
        out.add(softwareVideo?software:hardware);out.add(softwareVideo?hardware:software);
    }
}
