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
    private final SubtitleTranslation translation;
    TvExtractorsFactory(ExtractorsFactory delegate){this(delegate,null);}
    TvExtractorsFactory(ExtractorsFactory delegate,SubtitleTranslation translation){this.delegate=delegate;this.translation=translation;}
    static Format baseLayer(Format format){
        if(!MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)||format.codecs==null
                ||!format.codecs.toLowerCase(java.util.Locale.ROOT).matches("dv(?:he|h1)\\.0?7(?:\\..*)?"))return format;
        return format.buildUpon().setSampleMimeType(MimeTypes.VIDEO_H265)
            .setCodecs(NalUnitUtil.getH265BaseLayerCodecsString(format.initializationData)).build();
    }
    @Override public Extractor[] createExtractors(){return wrap(delegate.createExtractors());}
    @Override public Extractor[] createExtractors(Uri uri,Map<String,List<String>> headers){return wrap(delegate.createExtractors(uri,headers));}
    private Extractor[] wrap(Extractor[] extractors){
        for(int i=0;i<extractors.length;i++)extractors[i]=new ForwardingExtractor(extractors[i]){
            final java.util.List<TranslationTrackOutput> translationOutputs=new java.util.ArrayList<>();
            @Override public void seek(long position,long timeUs){for(TranslationTrackOutput output:translationOutputs)output.reset();super.seek(position,timeUs);}
            @Override public void init(ExtractorOutput output){super.init(new ForwardingExtractorOutput(output){
                int subtitleOrdinal;
                final java.util.Map<Integer,TrackOutput> tracks=new java.util.HashMap<>();
                @Override public TrackOutput track(int id,int type){
                    TrackOutput cached=tracks.get(id);if(cached!=null)return cached;
                    TrackOutput target=super.track(id,type);
                    if(type==androidx.media3.common.C.TRACK_TYPE_TEXT&&translation!=null&&subtitleOrdinal++==translation.sourceOrdinal){
                        TranslationTrackOutput tee=new TranslationTrackOutput(target,translation);translationOutputs.add(tee);target=tee;
                    }
                    TrackOutput result=new ForwardingTrackOutput(target){@Override public void format(Format format){super.format(baseLayer(format));}};
                    tracks.put(id,result);return result;
                }
            });}
        };
        return extractors;
    }
}
