package xyz.etkn.tv;

import android.content.Context;
import android.os.Handler;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory;
import java.util.ArrayList;

/** Keep hardware video by default; decode audio to PCM rather than relying on TV passthrough. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class TvRenderersFactory extends NextRenderersFactory {
    static {
        io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary.setLibraries("etkn_media3ext");
    }
    TvRenderersFactory(Context context,boolean softwareVideo){
        super(context);
        setEnableDecoderFallback(true);
        setExtensionRendererMode(softwareVideo?EXTENSION_RENDERER_MODE_PREFER:EXTENSION_RENDERER_MODE_ON);
    }
    @Override protected void buildAudioRenderers(Context context,int mode,MediaCodecSelector selector,
            boolean fallback,AudioSink sink,Handler handler,AudioRendererEventListener listener,ArrayList<Renderer> out){
        super.buildAudioRenderers(context,EXTENSION_RENDERER_MODE_PREFER,selector,fallback,sink,handler,listener,out);
    }
}
