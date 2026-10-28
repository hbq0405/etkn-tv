package xyz.etkn.tv;

import android.content.Context;
import android.media.MediaFormat;
import android.os.Handler;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import java.util.ArrayList;
import java.util.List;

/** Prefer the compatible base layer for Dolby Vision profiles 7/8 on TV decoders. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class TvVideoRenderer extends MediaCodecVideoRenderer {
    TvVideoRenderer(Context context,MediaCodecSelector selector,Handler handler,VideoRendererEventListener listener,long joining){
        super(new Builder(context).setMediaCodecSelector(selector).setEnableDecoderFallback(true)
            .setEventHandler(handler).setEventListener(listener).setAllowedJoiningTimeMs(joining));
    }
    static boolean hasHevcBaseLayer(Format format){
        return io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary.hasHevcBaseLayer(format);
    }
    @Override protected int supportsFormat(MediaCodecSelector selector,Format format)throws MediaCodecUtil.DecoderQueryException{
        return super.supportsFormat(selector,hasHevcBaseLayer(format)?format.buildUpon().setSampleMimeType(MimeTypes.VIDEO_H265).setCodecs(null).build():format);
    }
    @Override protected List<MediaCodecInfo> getDecoderInfos(MediaCodecSelector selector,Format format,boolean secure)throws MediaCodecUtil.DecoderQueryException{
        List<MediaCodecInfo> original=super.getDecoderInfos(selector,format,secure);
        if(!hasHevcBaseLayer(format))return original;
        List<MediaCodecInfo> result=new ArrayList<>(selector.getDecoderInfos(MimeTypes.VIDEO_H265,secure,false));
        for(MediaCodecInfo decoder:original)if(result.stream().noneMatch(value->value.name.equals(decoder.name)))result.add(decoder);
        return result;
    }
    @Override protected MediaFormat getMediaFormat(Format format,String mime,CodecMaxValues maximum,float rate,boolean noPostProcess,int audioSession){
        if(hasHevcBaseLayer(format)&&MimeTypes.VIDEO_H265.equals(mime))format=format.buildUpon().setSampleMimeType(MimeTypes.VIDEO_H265).setCodecs(null).build();
        return super.getMediaFormat(format,mime,maximum,rate,noPostProcess,audioSession);
    }
}
