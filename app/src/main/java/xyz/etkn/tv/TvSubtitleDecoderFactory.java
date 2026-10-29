package xyz.etkn.tv;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.text.SubtitleDecoderFactory;
import androidx.media3.extractor.text.SimpleSubtitleDecoder;
import androidx.media3.extractor.text.Subtitle;
import androidx.media3.extractor.text.SubtitleDecoder;

/** Decode raw PGS on its own worker without serializing bitmap cues through PNG. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class TvSubtitleDecoderFactory implements SubtitleDecoderFactory {
    @Override public boolean supportsFormat(Format format){
        return MimeTypes.APPLICATION_PGS.equals(format.sampleMimeType)||DEFAULT.supportsFormat(format);
    }
    @Override public SubtitleDecoder createDecoder(Format format){
        if(!MimeTypes.APPLICATION_PGS.equals(format.sampleMimeType))return DEFAULT.createDecoder(format);
        return new SimpleSubtitleDecoder("TvPgsDecoder"){
            private final TvPgsParser parser=new TvPgsParser();
            @Override protected Subtitle decode(byte[] data,int length,boolean reset){
                if(reset)parser.reset();
                return parser.parseToLegacySubtitle(data,0,length);
            }
        };
    }
}
