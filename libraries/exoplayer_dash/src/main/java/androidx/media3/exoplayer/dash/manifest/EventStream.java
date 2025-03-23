package androidx.media3.exoplayer.dash.manifest;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.metadata.emsg.EventMessage;

/** 表示 DASH MPD 中的 EventStream 元素，定义见 ISO/IEC 23009-1 第二版第 5.10 节。 */
@UnstableApi
public final class EventStream {

  /** 事件流中的 {@link EventMessage} 数组。 */
  public final EventMessage[] events;

  /** 事件的呈现时间（以微秒为单位），按升序排序。 */
  public final long[] presentationTimesUs;

  /** 方案的 URI。 */
  public final String schemeIdUri;

  /** 事件流的值。如果未在清单中定义，则使用空字符串。 */
  public final String value;

  /** 时间刻度（每秒的单位数），如清单中定义。 */
  public final long timescale;

  /**
   * 构造一个事件流实例。
   *
   * @param schemeIdUri 方案的 URI。
   * @param value 事件流的值。
   * @param timescale 时间刻度（每秒的单位数）。
   * @param presentationTimesUs 事件的呈现时间（以微秒为单位）。
   * @param events 事件流中的 {@link EventMessage} 数组。
   */
  public EventStream(
      String schemeIdUri,
      String value,
      long timescale,
      long[] presentationTimesUs,
      EventMessage[] events) {
    this.schemeIdUri = schemeIdUri;
    this.value = value;
    this.timescale = timescale;
    this.presentationTimesUs = presentationTimesUs;
    this.events = events;
  }

  /** 返回此 {@link EventStream} 的构造 ID，等于 {@code schemeIdUri + "/" + value}。 */
  public String id() {
    return schemeIdUri + "/" + value;
  }
}