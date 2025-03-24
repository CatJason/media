package androidx.media3.common.text;

import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** 文本注释的属性（例如注音、文本强调标记）。 */
@UnstableApi
public final class TextAnnotation {
  /** 文本注释的位置未知。 */
  public static final int POSITION_UNKNOWN = -1;

  /**
   * 对于水平文本，文本注释应位于基础文本的上方。
   *
   * <p>对于垂直文本，它应位于右侧，与 CSS 的 <a
   * href="https://developer.mozilla.org/en-US/docs/Web/CSS/ruby-position">ruby-position</a> 相同。
   */
  public static final int POSITION_BEFORE = 1;

  /**
   * 对于水平文本，文本注释应位于基础文本的下方。
   *
   * <p>对于垂直文本，它应位于左侧，与 CSS 的 <a
   * href="https://developer.mozilla.org/en-US/docs/Web/CSS/ruby-position">ruby-position</a> 相同。
   */
  public static final int POSITION_AFTER = 2;

  /**
   * 注释文本相对于基础文本的可能位置。
   *
   * <p>可能的值包括：
   *
   * <ul>
   *   <li>{@link #POSITION_UNKNOWN}
   *   <li>{@link #POSITION_BEFORE}
   *   <li>{@link #POSITION_AFTER}
   * </ul>
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({POSITION_UNKNOWN, POSITION_BEFORE, POSITION_AFTER})
  public @interface Position {}

  private TextAnnotation() {}
}