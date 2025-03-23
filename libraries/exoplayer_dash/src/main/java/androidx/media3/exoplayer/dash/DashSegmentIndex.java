package androidx.media3.exoplayer.dash;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.dash.manifest.RangedUri;

/** 用于索引媒体流中的分段。 */
@UnstableApi
public interface DashSegmentIndex {

  int INDEX_UNBOUNDED = -1;

  /**
   * 如果索引没有分段，或者给定的媒体时间早于第一个分段的开始时间，则返回 {@code getFirstSegmentNum()}。
   * 如果给定的媒体时间晚于最后一个分段的结束时间，则返回 {@code getFirstSegmentNum() + getSegmentCount() - 1}。
   * 否则，返回包含给定媒体时间的分段号。
   *
   * @param timeUs 时间，单位为微秒。
   * @param periodDurationUs 所属周期的持续时间，单位为微秒，如果周期持续时间未知，则为 {@link C#TIME_UNSET}。
   * @return 对应分段的分段号。
   */
  long getSegmentNum(long timeUs, long periodDurationUs);

  /**
   * 返回分段的开始时间。
   *
   * @param segmentNum 分段号。
   * @return 对应的开始时间，单位为微秒。
   */
  long getTimeUs(long segmentNum);

  /**
   * 返回分段的持续时间。
   *
   * @param segmentNum 分段号。
   * @param periodDurationUs 所属周期的持续时间，单位为微秒，如果周期持续时间未知，则为 {@link C#TIME_UNSET}。
   * @return 分段的持续时间，单位为微秒。
   */
  long getDurationUs(long segmentNum, long periodDurationUs);

  /**
   * 返回定义分段位置的 {@link RangedUri}。
   *
   * @param segmentNum 分段号。
   * @return 定义数据位置的 {@link RangedUri}。
   */
  RangedUri getSegmentUrl(long segmentNum);

  /** 返回索引中第一个定义的分段号。 */
  long getFirstSegmentNum();

  /**
   * 返回索引中第一个可用分段的分段号。
   *
   * @param periodDurationUs 所属周期的持续时间，单位为微秒，如果周期持续时间未知，则为 {@link C#TIME_UNSET}。
   * @param nowUnixTimeUs 当前时间，单位为微秒，自 Unix 纪元起。
   * @return 第一个可用分段的分段号。
   */
  long getFirstAvailableSegmentNum(long periodDurationUs, long nowUnixTimeUs);

  /**
   * 返回索引中定义的分段数量，或 {@link #INDEX_UNBOUNDED}。
   *
   * <p>如果动态清单使用不带 SegmentTimeline 元素的 SegmentTemplate 元素，并且周期持续时间未知，则会出现无界索引。
   * 在这种情况下，调用者可以使用 {@link #getFirstAvailableSegmentNum(long, long)} 和
   * {@link #getAvailableSegmentCount(long, long)} 查询可用分段。
   *
   * @param periodDurationUs 所属周期的持续时间，单位为微秒，如果周期持续时间未知，则为 {@link C#TIME_UNSET}。
   * @return 索引中的分段数量，或 {@link #INDEX_UNBOUNDED}。
   */
  long getSegmentCount(long periodDurationUs);

  /**
   * 返回索引中可用分段的数量。
   *
   * @param periodDurationUs 所属周期的持续时间，单位为微秒，如果周期持续时间未知，则为 {@link C#TIME_UNSET}。
   * @param nowUnixTimeUs 当前时间，单位为微秒，自 Unix 纪元起。
   * @return 索引中可用分段的数量。
   */
  long getAvailableSegmentCount(long periodDurationUs, long nowUnixTimeUs);

  /**
   * 返回新分段可用的时间，单位为微秒，如果不适用，则返回 {@link C#TIME_UNSET}。
   *
   * @param periodDurationUs 所属周期的持续时间，单位为微秒，如果周期持续时间未知，则为 {@link C#TIME_UNSET}。
   * @param nowUnixTimeUs 当前时间，单位为微秒，自 Unix 纪元起。
   * @return 新分段可用的时间，单位为微秒，如果不适用，则返回 {@link C#TIME_UNSET}。
   */
  long getNextSegmentAvailableTimeUs(long periodDurationUs, long nowUnixTimeUs);

  /**
   * 如果分段由索引显式定义，则返回 true。
   *
   * <p>如果返回 true，则每个分段都由索引数据显式定义，并且在获取索引时列出的所有分段都保证可用。
   *
   * <p>如果返回 false，则分段信息是从诸如固定分段持续时间等属性派生的。如果演示是动态的，则可能只有一部分分段可用。
   *
   * @return 分段是否由索引显式定义。
   */
  boolean isExplicit();
}