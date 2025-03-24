package androidx.media3.common.text;

import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import androidx.media3.common.util.UnstableApi;

/**
 * 用于 Android <a href="https://developer.android.com/guide/topics/text/spans">Span 样式</a>的工具方法。
 */
@UnstableApi
public final class SpanUtil {

  /**
   * 将 {@code span} 添加到 {@code spannable} 的 {@code start} 和 {@code end} 之间，移除任何具有相同类型、相同索引和相同标志的现有 Span。
   *
   * <p>这对于某些 Span 类型非常有用，例如 {@link ForegroundColorSpan}，因为重复这些 Span 没有意义，且评估顺序可能会对最终文本产生意外影响。
   *
   * @param spannable 要添加 {@code span} 的 {@link Spannable}。
   * @param span 要添加的 Span 对象。
   * @param start 新 Span 的起始索引。
   * @param end 新 Span 的结束索引。
   * @param spanFlags 传递给 {@link Spannable#setSpan(Object, int, int, int)} 的标志。
   */
  public static void addOrReplaceSpan(
      Spannable spannable, Object span, int start, int end, int spanFlags) {
    Object[] existingSpans = spannable.getSpans(start, end, span.getClass());
    for (Object existingSpan : existingSpans) {
      removeIfStartEndAndFlagsMatch(spannable, existingSpan, start, end, spanFlags);
    }
    spannable.setSpan(span, start, end, spanFlags);
  }

  /**
   * 修改 {@code start} 和 {@code end} 之间文本的大小，相对于任何覆盖<b>至少相同范围</b>的现有 {@link RelativeSizeSpan} 实例。
   *
   * <p>仅覆盖 {@code start} 和 {@code end} 之间部分文本的 {@link RelativeSizeSpan} 实例将被忽略。
   *
   * <p>在 {@code start} 和 {@code end} 之间添加一个新的 {@link RelativeSizeSpan} 实例，其 {@code sizeChange} 值通过修改 {@code size} 参数与覆盖 {@code start} 和 {@code end} 之间的 {@link RelativeSizeSpan} 实例的 {@code sizeChange} 值计算得出。
   *
   * <p>移除具有相同 {@code start}、{@code end} 和 {@code spanFlags} 的 {@link RelativeSizeSpan} 实例。
   *
   * @param spannable 要添加 {@link RelativeSizeSpan} 的 {@link Spannable}。
   * @param size 修改文本大小的比例。
   * @param start 新 Span 的起始索引。
   * @param end 新 Span 的结束索引。
   * @param spanFlags 传递给 {@link Spannable#setSpan(Object, int, int, int)} 的标志。
   */
  public static void addInheritedRelativeSizeSpan(
      Spannable spannable, float size, int start, int end, int spanFlags) {
    for (RelativeSizeSpan existingSpan : spannable.getSpans(start, end, RelativeSizeSpan.class)) {
      if (spannable.getSpanStart(existingSpan) <= start
          && spannable.getSpanEnd(existingSpan) >= end) {
        size *= existingSpan.getSizeChange();
      }
      removeIfStartEndAndFlagsMatch(spannable, existingSpan, start, end, spanFlags);
    }
    spannable.setSpan(new RelativeSizeSpan(size), start, end, spanFlags);
  }

  private static void removeIfStartEndAndFlagsMatch(
      Spannable spannable, Object span, int start, int end, int spanFlags) {
    if (spannable.getSpanStart(span) == start
        && spannable.getSpanEnd(span) == end
        && spannable.getSpanFlags(span) == spanFlags) {
      spannable.removeSpan(span);
    }
  }

  private SpanUtil() {}
}