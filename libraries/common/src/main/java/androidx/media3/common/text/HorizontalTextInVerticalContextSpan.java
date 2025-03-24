package androidx.media3.common.text;

import androidx.media3.common.util.UnstableApi;

/**
 * A styling span for horizontal text in a vertical context.
 *
 * <p>This is used in vertical text to write some characters in a horizontal orientation, known in
 * Japanese as tate-chu-yoko.
 *
 * <p>More information on <a
 * href="https://www.w3.org/TR/jlreq/#handling_of_tatechuyoko">tate-chu-yoko</a> and <a
 * href="https://developer.android.com/guide/topics/text/spans">span styling</a>.
 */
// NOTE: There's no Android layout support for this, so this span currently doesn't extend any
// styling superclasses (e.g. MetricAffectingSpan). The only way to render this styling is to
// extract the spans and do the layout manually.
@UnstableApi
public final class HorizontalTextInVerticalContextSpan implements LanguageFeatureSpan {}
