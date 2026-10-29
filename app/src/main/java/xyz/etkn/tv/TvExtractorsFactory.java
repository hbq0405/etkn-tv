package xyz.etkn.tv;

import android.net.Uri;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.container.NalUnitUtil;
import androidx.media3.extractor.*;
import java.util.List;
import java.util.Map;

/** Declare the HDR10 base layer before Media3 chooses a Dolby Vision P7 decoder. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class TvExtractorsFactory implements ExtractorsFactory {
    private final ExtractorsFactory delegate;
    TvExtractorsFactory(ExtractorsFactory delegate){this.delegate=delegate;}
    static Format baseLayer(Format format){
        if(!MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)||format.codecs==null
                ||!format.codecs.toLowerCase(java.util.Locale.ROOT).matches("dv(?:he|h1)\\.0?7(?:\\..*)?"))return format;
        return format.buildUpon().setSampleMimeType(MimeTypes.VIDEO_H265)
            .setCodecs(NalUnitUtil.getH265BaseLayerCodecsString(format.initializationData)).build();
    }
    @Override public Extractor[] createExtractors(){return wrap(delegate.createExtractors());}
    @Override public Extractor[] createExtractors(Uri uri,Map<String,List<String>> headers){return wrap(delegate.createExtractors(uri,headers));}
    private static Extractor[] wrap(Extractor[] extractors){
        for(int i=0;i<extractors.length;i++)extractors[i]=new ForwardingExtractor(extractors[i]){
            @Override public void init(ExtractorOutput output){super.init(new ForwardingExtractorOutput(output){
                @Override public TrackOutput track(int id,int type){return new ForwardingTrackOutput(super.track(id,type)){
                    @Override public void format(Format format){super.format(baseLayer(format));}
                };}
            });}
        };
        return extractors;
    }
}
