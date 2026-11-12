package xyz.etkn.tv;

import androidx.media3.common.C;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ForwardingRenderer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.common.Format;
import androidx.media3.exoplayer.text.TextRenderer;

/** Subtitle clock only: audio, video, playback reports and captured source text stay original. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class SubtitleOffsetClock {
    volatile int milliseconds;
    final long durationUs;
    SubtitleOffsetClock(long durationMs){durationUs=durationMs*1000;}
    long positionUs(long value){return value-milliseconds*1000L;}
    Renderer wrap(Renderer renderer){
        if(renderer.getTrackType()!=C.TRACK_TYPE_TEXT&&!renderer.getName().equals("AssRenderer"))return renderer;
        return new ForwardingRenderer(renderer){
            private long streamOffsetUs;
            @Override public void enable(RendererConfiguration config,Format[] formats,SampleStream stream,long positionUs,
                    boolean joining,boolean start,long startUs,long offsetUs,MediaPeriodId period)throws ExoPlaybackException{
                streamOffsetUs=offsetUs;super.enable(config,formats,stream,positionUs(positionUs),joining,start,startUs,offsetUs,period);
            }
            @Override public void replaceStream(Format[] formats,SampleStream stream,long startUs,long offsetUs,MediaPeriodId period)throws ExoPlaybackException{
                streamOffsetUs=offsetUs;super.replaceStream(formats,stream,startUs,offsetUs,period);
            }
            @Override public void resetPosition(long value)throws ExoPlaybackException{super.resetPosition(positionUs(value));}
            @Override public void render(long value,long elapsed)throws ExoPlaybackException{
                // Media3 recognizes an unwrapped TextRenderer here; preserve its end-of-stream contract.
                if(renderer instanceof TextRenderer&&renderer.isCurrentStreamFinal()&&durationUs>0)
                    ((TextRenderer)renderer).setFinalStreamEndPositionUs(positionUs(streamOffsetUs+durationUs));
                super.render(positionUs(value),elapsed);
            }
        };
    }
}
