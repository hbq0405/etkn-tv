package xyz.etkn.tv;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.extractor.text.SubtitleParser;

/** Share PGS parsing while allowing embedded samples to reach the decoder worker unchanged. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class TvSubtitleParserFactory implements SubtitleParser.Factory {
    private final SubtitleParser.Factory delegate;
    private final boolean parsePgs;
    TvSubtitleParserFactory(SubtitleParser.Factory delegate){this(delegate,true);}
    TvSubtitleParserFactory(SubtitleParser.Factory delegate,boolean parsePgs){this.delegate=delegate;this.parsePgs=parsePgs;}
    @Override public boolean supportsFormat(Format format){return MimeTypes.APPLICATION_PGS.equals(format.sampleMimeType)?parsePgs:delegate.supportsFormat(format);}
    @Override public int getCueReplacementBehavior(Format format){return MimeTypes.APPLICATION_PGS.equals(format.sampleMimeType)?Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE:delegate.getCueReplacementBehavior(format);}
    @Override public SubtitleParser create(Format format){return MimeTypes.APPLICATION_PGS.equals(format.sampleMimeType)?new TvPgsParser():delegate.create(format);}
}
