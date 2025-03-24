package androidx.media3.common.text;

import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 用于文本强调标记的样式 Span。
 *
 * <p>这些是发音辅助工具，例如 <a
 * href="https://www.w3.org/TR/jlreq/?lang=en#term.emphasis-dots">日语傍点</a>，可以使用 <a
 * href="https://developer.mozilla.org/en-US/docs/Web/CSS/text-emphasis">text-emphasis</a> CSS 属性进行渲染。
 */
// 注意：Android 布局不支持文本强调标记，因此此 Span 目前不继承任何样式超类（例如 MetricAffectingSpan）。
// 渲染此强调标记的唯一方法是提取 Span 并手动进行布局。
@UnstableApi
public final class TextEmphasisSpan implements LanguageFeatureSpan {

  /**
   * 可以使用的标记形状。
   *
   * <p>可能的值包括：
   *
   * <ul>
   *   <li>{@link #MARK_SHAPE_NONE}
   *   <li>{@link #MARK_SHAPE_CIRCLE}
   *   <li>{@link #MARK_SHAPE_DOT}
   *   <li>{@link #MARK_SHAPE_SESAME}
   * </ul>
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({MARK_SHAPE_NONE, MARK_SHAPE_CIRCLE, MARK_SHAPE_DOT, MARK_SHAPE_SESAME})
  public @interface MarkShape {}

  public static final int MARK_SHAPE_NONE = 0;
  public static final int MARK_SHAPE_CIRCLE = 1;
  public static final int MARK_SHAPE_DOT = 2;
  public static final int MARK_SHAPE_SESAME = 3;

  /**
   * 可以使用的标记填充方式。
   *
   * <p>可能的值包括：
   *
   * <ul>
   *   <li>{@link #MARK_FILL_UNKNOWN}
   *   <li>{@link #MARK_FILL_FILLED}
   *   <li>{@link #MARK_FILL_OPEN}
   * </ul>
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({MARK_FILL_UNKNOWN, MARK_FILL_FILLED, MARK_FILL_OPEN})
  public @interface MarkFill {}

  public static final int MARK_FILL_UNKNOWN = 0;
  public static final int MARK_FILL_FILLED = 1;
  public static final int MARK_FILL_OPEN = 2;

  /** 用于文本强调的标记形状。 */
  public @MarkShape int markShape;

  /** 文本强调标记的填充方式。 */
  public @MarkFill int markFill;

  /** 文本强调标记相对于基础文本的位置。 */
  public final @TextAnnotation.Position int position;

  private static final String FIELD_MARK_SHAPE = Util.intToStringMaxRadix(0);
  private static final String FIELD_MARK_FILL = Util.intToStringMaxRadix(1);
  private static final String FIELD_POSITION = Util.intToStringMaxRadix(2);

  public TextEmphasisSpan(
      @MarkShape int shape, @MarkFill int fill, @TextAnnotation.Position int position) {
    this.markShape = shape;
    this.markFill = fill;
    this.position = position;
  }

  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_MARK_SHAPE, markShape);
    bundle.putInt(FIELD_MARK_FILL, markFill);
    bundle.putInt(FIELD_POSITION, position);
    return bundle;
  }

  public static TextEmphasisSpan fromBundle(Bundle bundle) {
    return new TextEmphasisSpan(
        /* shape= */ bundle.getInt(FIELD_MARK_SHAPE),
        /* fill= */ bundle.getInt(FIELD_MARK_FILL),
        /* position= */ bundle.getInt(FIELD_POSITION));
  }
}