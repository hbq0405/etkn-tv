package xyz.etkn.tv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.text.TextRenderer;
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
    TvRenderersFactory(Context context,boolean softwareVideo){
        this(context,softwareVideo,true);
    }
    private final boolean softwareVideo,softwareAudio;
    TvRenderersFactory(Context context,boolean softwareVideo,boolean softwareAudio){
        super(context);
        this.softwareVideo=softwareVideo;
        this.softwareAudio=softwareAudio;
        setEnableDecoderFallback(true);
        setExtensionRendererMode(softwareVideo?EXTENSION_RENDERER_MODE_PREFER:EXTENSION_RENDERER_MODE_ON);
    }
    @SuppressWarnings("deprecation")
    @Override protected void buildTextRenderers(Context context,TextOutput output,Looper outputLooper,
            int mode,ArrayList<Renderer> out){
        TextRenderer renderer=new TextRenderer(output,outputLooper,new TvSubtitleDecoderFactory());
        renderer.experimentalSetLegacyDecodingEnabled(true);
        out.add(renderer);
    }
    @Override protected void buildAudioRenderers(Context context,int mode,MediaCodecSelector selector,
            boolean fallback,AudioSink sink,Handler handler,AudioRendererEventListener listener,ArrayList<Renderer> out){
        int first=out.size();super.buildAudioRenderers(context,EXTENSION_RENDERER_MODE_OFF,selector,fallback,sink,handler,listener,out);
        out.add(softwareAudio?first:out.size(),new FfmpegAudioRenderer(handler,listener,sink));
    }
    @Override protected void buildVideoRenderers(Context context,int mode,MediaCodecSelector selector,
            boolean fallback,Handler handler,VideoRendererEventListener listener,long joining,ArrayList<Renderer> out){
        Renderer hardware=new TvVideoRenderer(context,selector,handler,listener,joining);
        Renderer software=new FfmpegVideoRenderer(joining,handler,listener,50);
        out.add(softwareVideo?software:hardware);out.add(softwareVideo?hardware:software);
    }
}
