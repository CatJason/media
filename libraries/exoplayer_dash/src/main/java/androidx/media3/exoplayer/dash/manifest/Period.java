package androidx.media3.exoplayer.dash.manifest;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import java.util.Collections;
import java.util.List;

/** 封装了在一段连续时间内的媒体内容组件。 */
@UnstableApi
public class Period {

  /** 周期标识符，如果存在。 */
  @Nullable public final String id;

  /** 周期的开始时间（以毫秒为单位），相对于清单的开始时间。 */
  public final long startMs;

  /** 属于此周期的自适应集。 */
  public final List<AdaptationSet> adaptationSets;

  /** 属于此周期的事件流。 */
  public final List<EventStream> eventStreams;

  /** 此周期的资产标识符，如果存在。 */
  @Nullable public final Descriptor assetIdentifier;

  /**
   * @param id 周期标识符。可能为 null。
   * @param startMs 周期的开始时间（以毫秒为单位）。
   * @param adaptationSets 属于此周期的自适应集。
   */
  public Period(@Nullable String id, long startMs, List<AdaptationSet> adaptationSets) {
    this(id, startMs, adaptationSets, Collections.emptyList(), /* assetIdentifier= */ null);
  }

  /**
   * @param id 周期标识符。可能为 null。
   * @param startMs 周期的开始时间（以毫秒为单位）。
   * @param adaptationSets 属于此周期的自适应集。
   * @param eventStreams 属于此周期的 {@link EventStream} 列表。
   */
  public Period(
      @Nullable String id,
      long startMs,
      List<AdaptationSet> adaptationSets,
      List<EventStream> eventStreams) {
    this(id, startMs, adaptationSets, eventStreams, /* assetIdentifier= */ null);
  }

  /**
   * @param id 周期标识符。可能为 null。
   * @param startMs 周期的开始时间（以毫秒为单位）。
   * @param adaptationSets 属于此周期的自适应集。
   * @param eventStreams 属于此周期的 {@link EventStream} 列表。
   * @param assetIdentifier 此周期的资产标识符。
   */
  public Period(
      @Nullable String id,
      long startMs,
      List<AdaptationSet> adaptationSets,
      List<EventStream> eventStreams,
      @Nullable Descriptor assetIdentifier) {
    this.id = id;
    this.startMs = startMs;
    this.adaptationSets = Collections.unmodifiableList(adaptationSets);
    this.eventStreams = Collections.unmodifiableList(eventStreams);
    this.assetIdentifier = assetIdentifier;
  }

  /**
   * 返回指定类型的第一个自适应集的索引，如果不存在指定类型的自适应集，则返回 {@link C#INDEX_UNSET}。
   *
   * @param type 自适应集类型。
   * @return 指定类型的第一个自适应集的索引，或 {@link C#INDEX_UNSET}。
   */
  public int getAdaptationSetIndex(int type) {
    int adaptationCount = adaptationSets.size();
    for (int i = 0; i < adaptationCount; i++) {
      if (adaptationSets.get(i).type == type) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }
}