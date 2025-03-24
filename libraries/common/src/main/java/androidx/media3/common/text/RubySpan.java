package androidx.media3.common.text;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.os.Bundle;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/**
 * 用于注音文本的样式 Span。
 *
 * <p>此 Span 覆盖的文本称为“基础文本”，而注音文本存储在 {@link #rubyText} 中。
 *
 * <p>更多信息请参考 <a href="https://en.wikipedia.org/wiki/Ruby_character">注音字符</a> 和
 * <a href="https://developer.android.com/guide/topics/text/spans">Span 样式</a>。
 */
// 注意：Android 布局不支持注音文本，因此此 Span 目前不继承任何样式超类（例如 MetricAffectingSpan）。
// 渲染这些注音文本的唯一方法是提取 Span 并手动进行布局。
// TODO: 考虑添加对括号文本的支持，以便在渲染不支持注音文本时使用（例如 HTML 的 <rp> 标签）。
@UnstableApi
public final class RubySpan implements LanguageFeatureSpan {

  /** 注音文本，即较小的解释性字符。 */
  public final String rubyText;

  /** 注音文本相对于基础文本的位置。 */
  public final @TextAnnotation.Position int position;

  private static final String FIELD_TEXT = Util.intToStringMaxRadix(0);
  private static final String FIELD_POSITION = Util.intToStringMaxRadix(1);

  public RubySpan(String rubyText, @TextAnnotation.Position int position) {
    this.rubyText = rubyText;
    this.position = position;
  }

  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putString(FIELD_TEXT, rubyText);
    bundle.putInt(FIELD_POSITION, position);
    return bundle;
  }

  public static RubySpan fromBundle(Bundle bundle) {
    return new RubySpan(
        /* rubyText= */ checkNotNull(bundle.getString(FIELD_TEXT)),
        /* position= */ bundle.getInt(FIELD_POSITION));
  }
}