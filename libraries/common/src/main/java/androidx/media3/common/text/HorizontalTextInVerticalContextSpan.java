package androidx.media3.common.text;

import androidx.media3.common.util.UnstableApi;

/**
 * 用于在垂直上下文中水平文本的样式 Span。
 *
 * <p>此 Span 用于垂直文本中，将某些字符以水平方向书写，日语中称为 "tate-chu-yoko"（縦中横）。
 *
 * <p>更多信息请参考 <a
 * href="https://www.w3.org/TR/jlreq/#handling_of_tatechuyoko">tate-chu-yoko</a> 和 <a
 * href="https://developer.android.com/guide/topics/text/spans">Span 样式</a>。
 */
// 注意：Android 布局不支持此功能，因此此 Span 目前不继承任何样式超类（例如 MetricAffectingSpan）。
// 渲染此样式的唯一方法是提取 Span 并手动进行布局。
@UnstableApi
public final class HorizontalTextInVerticalContextSpan implements LanguageFeatureSpan {}