package androidx.media3.exoplayer.dash.manifest;

import static androidx.media3.exoplayer.dash.DashSegmentIndex.INDEX_UNBOUNDED;
import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.dash.DashSegmentIndex;
import com.google.common.math.BigIntegerMath;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import org.checkerframework.checker.initialization.qual.UnderInitialization;

/** 表示 SegmentBase 清单元素的近似实现。 */
@UnstableApi
public abstract class SegmentBase {

  @Nullable /* package */ final RangedUri initialization;
  /* package */ final long timescale;
  /* package */ final long presentationTimeOffset;

  /**
   * @param initialization 初始化数据对应的 {@link RangedUri}，如果存在的话。
   * @param timescale 时间刻度，单位为每秒。
   * @param presentationTimeOffset 表示时间偏移量。以秒为单位的值是该值除以 {@code timescale} 的结果。
   */
  public SegmentBase(
      @Nullable RangedUri initialization, long timescale, long presentationTimeOffset) {
    this.initialization = initialization;
    this.timescale = timescale;
    this.presentationTimeOffset = presentationTimeOffset;
  }

  /**
   * 返回定义给定表示初始化数据位置的 {@link RangedUri}，如果不存在初始化数据则返回 null。
   *
   * @param representation 需要初始化数据的 {@link Representation}。
   * @return 定义初始化数据位置的 {@link RangedUri}，或 null。
   */
  @Nullable
  public RangedUri getInitialization(
      @UnderInitialization(Representation.class) Representation representation) {
    return initialization;
  }

  /** 返回表示时间偏移量，单位为微秒。 */
  public long getPresentationTimeOffsetUs() {
    return Util.scaleLargeTimestamp(presentationTimeOffset, C.MICROS_PER_SECOND, timescale);
  }

  /** 表示定义单个分段的 {@link SegmentBase}。 */
  public static class SingleSegmentBase extends SegmentBase {

    /* package */ final long indexStart;
    /* package */ final long indexLength;

    /**
     * @param initialization 初始化数据对应的 {@link RangedUri}，如果存在的话。
     * @param timescale 时间刻度，单位为每秒。
     * @param presentationTimeOffset 表示时间偏移量。以秒为单位的值是该值除以 {@code timescale} 的结果。
     * @param indexStart 索引数据在分段中的字节偏移量。
     * @param indexLength 索引数据的字节长度。
     */
    public SingleSegmentBase(
        @Nullable RangedUri initialization,
        long timescale,
        long presentationTimeOffset,
        long indexStart,
        long indexLength) {
      super(initialization, timescale, presentationTimeOffset);
      this.indexStart = indexStart;
      this.indexLength = indexLength;
    }

    public SingleSegmentBase() {
      this(
          /* initialization= */ null,
          /* timescale= */ 1,
          /* presentationTimeOffset= */ 0,
          /* indexStart= */ 0,
          /* indexLength= */ 0);
    }

    @Nullable
    public RangedUri getIndex() {
      return indexLength <= 0
          ? null
          : new RangedUri(/* referenceUri= */ null, indexStart, indexLength);
    }
  }
  /** 表示由多个分段组成的 {@link SegmentBase}。 */
  public abstract static class MultiSegmentBase extends SegmentBase {

    /* package */ final long startNumber;
    /* package */ final long duration;
    @Nullable /* package */ final List<SegmentTimelineElement> segmentTimeline;
    private final long timeShiftBufferDepthUs;
    private final long periodStartUnixTimeUs;

    /**
     * 相对于当前实时时间的偏移量，单位为微秒，表示分段何时可用。如果所有分段立即可用，则为 {@link C#TIME_UNSET}。
     *
     * <p>分段的结束时间 &le; 当前实时时间 + 可用时间偏移量时，分段将变为可用。
     */
    @VisibleForTesting /* package */ final long availabilityTimeOffsetUs;

    /**
     * @param initialization 初始化数据对应的 {@link RangedUri}，如果存在的话。
     * @param timescale 时间刻度，单位为每秒。
     * @param presentationTimeOffset 表示时间偏移量。以秒为单位的值是该值除以 {@code timescale} 的结果。
     * @param startNumber 第一个分段的序列号。
     * @param duration 固定时长分段的分段时长。以秒为单位的值是该值除以 {@code timescale} 的结果。如果 {@code segmentTimeline} 不为 null，则忽略此参数。
     * @param segmentTimeline 分段时间线。如果为 null，则假定分段时长为 {@code duration} 指定的固定时长。
     * @param availabilityTimeOffsetUs 分段可用的实时时间偏移量，单位为微秒，如果不适用则为 {@link C#TIME_UNSET}。
     * @param timeShiftBufferDepthUs 时间偏移缓冲区深度，单位为微秒。
     * @param periodStartUnixTimeUs 所属周期的开始时间，单位为微秒（自 Unix 纪元以来）。
     */
    public MultiSegmentBase(
        @Nullable RangedUri initialization,
        long timescale,
        long presentationTimeOffset,
        long startNumber,
        long duration,
        @Nullable List<SegmentTimelineElement> segmentTimeline,
        long availabilityTimeOffsetUs,
        long timeShiftBufferDepthUs,
        long periodStartUnixTimeUs) {
      super(initialization, timescale, presentationTimeOffset);
      this.startNumber = startNumber;
      this.duration = duration;
      this.segmentTimeline = segmentTimeline;
      this.availabilityTimeOffsetUs = availabilityTimeOffsetUs;
      this.timeShiftBufferDepthUs = timeShiftBufferDepthUs;
      this.periodStartUnixTimeUs = periodStartUnixTimeUs;
    }

    /** 参见 {@link DashSegmentIndex#getSegmentNum(long, long)}。 */
    public long getSegmentNum(long timeUs, long periodDurationUs) {
      final long firstSegmentNum = getFirstSegmentNum();
      final long segmentCount = getSegmentCount(periodDurationUs);
      if (segmentCount == 0) {
        return firstSegmentNum;
      }
      if (segmentTimeline == null) {
        // 所有分段的时长相等（最后一个分段可能除外）。
        long durationUs = (duration * C.MICROS_PER_SECOND) / timescale;
        long segmentNum = startNumber + timeUs / durationUs;
        // 确保在范围内。
        return segmentNum < firstSegmentNum
            ? firstSegmentNum
            : segmentCount == INDEX_UNBOUNDED
                ? segmentNum
                : min(segmentNum, firstSegmentNum + segmentCount - 1);
      } else {
        // 索引不能是无限的。使用二分查找确定分段。
        long lowIndex = firstSegmentNum;
        long highIndex = firstSegmentNum + segmentCount - 1;
        while (lowIndex <= highIndex) {
          long midIndex = lowIndex + (highIndex - lowIndex) / 2;
          long midTimeUs = getSegmentTimeUs(midIndex);
          if (midTimeUs < timeUs) {
            lowIndex = midIndex + 1;
          } else if (midTimeUs > timeUs) {
            highIndex = midIndex - 1;
          } else {
            return midIndex;
          }
        }
        return lowIndex == firstSegmentNum ? lowIndex : highIndex;
      }
    }

    /** 参见 {@link DashSegmentIndex#getDurationUs(long, long)}。 */
    public final long getSegmentDurationUs(long sequenceNumber, long periodDurationUs) {
      if (segmentTimeline != null) {
        long duration = segmentTimeline.get((int) (sequenceNumber - startNumber)).duration;
        return (duration * C.MICROS_PER_SECOND) / timescale;
      } else {
        long segmentCount = getSegmentCount(periodDurationUs);
        return segmentCount != INDEX_UNBOUNDED
            && sequenceNumber == (getFirstSegmentNum() + segmentCount - 1)
            ? (periodDurationUs - getSegmentTimeUs(sequenceNumber))
            : ((duration * C.MICROS_PER_SECOND) / timescale);
      }
    }

    /** 参见 {@link DashSegmentIndex#getTimeUs(long)}。 */
    public final long getSegmentTimeUs(long sequenceNumber) {
      long unscaledSegmentTime;
      if (segmentTimeline != null) {
        unscaledSegmentTime =
            segmentTimeline.get((int) (sequenceNumber - startNumber)).startTime
                - presentationTimeOffset;
      } else {
        unscaledSegmentTime = (sequenceNumber - startNumber) * duration;
      }
      return Util.scaleLargeTimestamp(unscaledSegmentTime, C.MICROS_PER_SECOND, timescale);
    }

    /**
     * 返回定义给定表示中指定索引分段位置的 {@link RangedUri}。
     *
     * <p>参见 {@link DashSegmentIndex#getSegmentUrl(long)}。
     */
    public abstract RangedUri getSegmentUrl(Representation representation, long index);

    /** 参见 {@link DashSegmentIndex#getFirstSegmentNum()}。 */
    public long getFirstSegmentNum() {
      return startNumber;
    }

    /** 参见 {@link DashSegmentIndex#getFirstAvailableSegmentNum(long, long)}。 */
    public long getFirstAvailableSegmentNum(long periodDurationUs, long nowUnixTimeUs) {
      long segmentCount = getSegmentCount(periodDurationUs);
      if (segmentCount != INDEX_UNBOUNDED || timeShiftBufferDepthUs == C.TIME_UNSET) {
        return getFirstSegmentNum();
      }
      // 索引本身是无限的。需要使用当前时间计算可用分段的范围。
      long liveEdgeTimeInPeriodUs = nowUnixTimeUs - periodStartUnixTimeUs;
      long timeShiftBufferStartInPeriodUs = liveEdgeTimeInPeriodUs - timeShiftBufferDepthUs;
      long timeShiftBufferStartSegmentNum =
          getSegmentNum(timeShiftBufferStartInPeriodUs, periodDurationUs);
      return max(getFirstSegmentNum(), timeShiftBufferStartSegmentNum);
    }

    /** 参见 {@link DashSegmentIndex#getAvailableSegmentCount(long, long)}。 */
    public long getAvailableSegmentCount(long periodDurationUs, long nowUnixTimeUs) {
      long segmentCount = getSegmentCount(periodDurationUs);
      if (segmentCount != INDEX_UNBOUNDED) {
        return segmentCount;
      }
      // 索引本身是无限的。需要使用当前时间计算可用分段的范围。
      long liveEdgeTimeInPeriodUs = nowUnixTimeUs - periodStartUnixTimeUs;
      long availabilityTimeOffsetUs = liveEdgeTimeInPeriodUs + this.availabilityTimeOffsetUs;
      // getSegmentNum(availabilityTimeOffsetUs) 尚未完成。
      long firstIncompleteSegmentNum = getSegmentNum(availabilityTimeOffsetUs, periodDurationUs);
      long firstAvailableSegmentNum = getFirstAvailableSegmentNum(periodDurationUs, nowUnixTimeUs);
      return (int) (firstIncompleteSegmentNum - firstAvailableSegmentNum);
    }

    /** 参见 {@link DashSegmentIndex#getNextSegmentAvailableTimeUs(long, long)}。 */
    public long getNextSegmentAvailableTimeUs(long periodDurationUs, long nowUnixTimeUs) {
      if (segmentTimeline != null) {
        return C.TIME_UNSET;
      }
      long firstIncompleteSegmentNum =
          getFirstAvailableSegmentNum(periodDurationUs, nowUnixTimeUs)
              + getAvailableSegmentCount(periodDurationUs, nowUnixTimeUs);
      return getSegmentTimeUs(firstIncompleteSegmentNum)
          + getSegmentDurationUs(firstIncompleteSegmentNum, periodDurationUs)
          - availabilityTimeOffsetUs;
    }

    /** 参见 {@link DashSegmentIndex#isExplicit()}。 */
    public boolean isExplicit() {
      return segmentTimeline != null;
    }

    /** 参见 {@link DashSegmentIndex#getSegmentCount(long)}。 */
    public abstract long getSegmentCount(long periodDurationUs);
  }

  /** 使用 SegmentList 定义其分段的 {@link MultiSegmentBase}。 */
  public static final class SegmentList extends MultiSegmentBase {

    @Nullable /* package */ final List<RangedUri> mediaSegments;

    /**
     * @param initialization 初始化数据对应的 {@link RangedUri}，如果存在的话。
     * @param timescale 时间刻度，单位为每秒。
     * @param presentationTimeOffset 表示时间偏移量。以秒为单位的值是该值除以 {@code timescale} 的结果。
     * @param startNumber 第一个分段的序列号。
     * @param duration 固定时长分段的分段时长。以秒为单位的值是该值除以 {@code timescale} 的结果。如果 {@code segmentTimeline} 不为 null，则忽略此参数。
     * @param segmentTimeline 分段时间线。如果为 null，则假定分段时长为 {@code duration} 指定的固定时长。
     * @param availabilityTimeOffsetUs 分段可用的实时时间偏移量，单位为微秒，如果不适用则为 {@link C#TIME_UNSET}。
     * @param mediaSegments 表示分段位置的 {@link RangedUri} 列表。
     * @param timeShiftBufferDepthUs 时间偏移缓冲区深度，单位为微秒。
     * @param periodStartUnixTimeUs 所属周期的开始时间，单位为微秒（自 Unix 纪元以来）。
     */
    public SegmentList(
        RangedUri initialization,
        long timescale,
        long presentationTimeOffset,
        long startNumber,
        long duration,
        @Nullable List<SegmentTimelineElement> segmentTimeline,
        long availabilityTimeOffsetUs,
        @Nullable List<RangedUri> mediaSegments,
        long timeShiftBufferDepthUs,
        long periodStartUnixTimeUs) {
      super(
          initialization,
          timescale,
          presentationTimeOffset,
          startNumber,
          duration,
          segmentTimeline,
          availabilityTimeOffsetUs,
          timeShiftBufferDepthUs,
          periodStartUnixTimeUs);
      this.mediaSegments = mediaSegments;
    }

    @Override
    public RangedUri getSegmentUrl(Representation representation, long sequenceNumber) {
      return mediaSegments.get((int) (sequenceNumber - startNumber));
    }

    @Override
    public long getSegmentCount(long periodDurationUs) {
      return mediaSegments.size();
    }

    @Override
    public boolean isExplicit() {
      return true;
    }
  }

  /** 使用 SegmentTemplate 定义其分段的 {@link MultiSegmentBase}。 */
  public static final class SegmentTemplate extends MultiSegmentBase {

    @Nullable /* package */ final UrlTemplate initializationTemplate;
    @Nullable /* package */ final UrlTemplate mediaTemplate;
    /* package */ final long endNumber;

    /**
     * @param initialization 初始化数据对应的 {@link RangedUri}，如果存在的话。如果 {@code initializationTemplate} 不为 null，则忽略此参数。
     * @param timescale 时间刻度，单位为每秒。
     * @param presentationTimeOffset 表示时间偏移量。以秒为单位的值是该值除以 {@code timescale} 的结果。
     * @param startNumber 第一个分段的序列号。
     * @param endNumber 最后一个分段的序列号，由 schemeIdUri="http://dashif.org/guidelines/last-segment-number" 的 SupplementalProperty 指定，或为 {@link C#INDEX_UNSET}。
     * @param duration 固定时长分段的分段时长。以秒为单位的值是该值除以 {@code timescale} 的结果。如果 {@code segmentTimeline} 不为 null，则忽略此参数。
     * @param segmentTimeline 分段时间线。如果为 null，则假定分段时长为 {@code duration} 指定的固定时长。
     * @param availabilityTimeOffsetUs 分段可用的实时时间偏移量，单位为微秒，如果不适用则为 {@link C#TIME_UNSET}。
     * @param initializationTemplate 定义初始化数据位置的模板，如果存在的话。如果为非 null，则忽略 {@code initialization} 参数。如果为 null，则使用 {@code initialization}。
     * @param mediaTemplate 定义每个媒体分段位置的模板。
     * @param timeShiftBufferDepthUs 时间偏移缓冲区深度，单位为微秒。
     * @param periodStartUnixTimeUs 所属周期的开始时间，单位为微秒（自 Unix 纪元以来）。
     */
    public SegmentTemplate(
        RangedUri initialization,
        long timescale,
        long presentationTimeOffset,
        long startNumber,
        long endNumber,
        long duration,
        @Nullable List<SegmentTimelineElement> segmentTimeline,
        long availabilityTimeOffsetUs,
        @Nullable UrlTemplate initializationTemplate,
        @Nullable UrlTemplate mediaTemplate,
        long timeShiftBufferDepthUs,
        long periodStartUnixTimeUs) {
      super(
          initialization,
          timescale,
          presentationTimeOffset,
          startNumber,
          duration,
          segmentTimeline,
          availabilityTimeOffsetUs,
          timeShiftBufferDepthUs,
          periodStartUnixTimeUs);
      this.initializationTemplate = initializationTemplate;
      this.mediaTemplate = mediaTemplate;
      this.endNumber = endNumber;
    }

    @Override
    @Nullable
    public RangedUri getInitialization(Representation representation) {
      if (initializationTemplate != null) {
        String urlString =
            initializationTemplate.buildUri(
                representation.format.id, 0, representation.format.bitrate, 0);
        return new RangedUri(urlString, 0, C.LENGTH_UNSET);
      } else {
        return super.getInitialization(representation);
      }
    }

    @Override
    public RangedUri getSegmentUrl(Representation representation, long sequenceNumber) {
      long time;
      if (segmentTimeline != null) {
        time = segmentTimeline.get((int) (sequenceNumber - startNumber)).startTime;
      } else {
        time = (sequenceNumber - startNumber) * duration;
      }
      String uriString =
          mediaTemplate.buildUri(
              representation.format.id, sequenceNumber, representation.format.bitrate, time);
      return new RangedUri(uriString, 0, C.LENGTH_UNSET);
    }

    @Override
    public long getSegmentCount(long periodDurationUs) {
      if (segmentTimeline != null) {
        return segmentTimeline.size();
      } else if (endNumber != C.INDEX_UNSET) {
        return endNumber - startNumber + 1;
      } else if (periodDurationUs != C.TIME_UNSET) {
        BigInteger numerator =
            BigInteger.valueOf(periodDurationUs).multiply(BigInteger.valueOf(timescale));
        BigInteger denominator =
            BigInteger.valueOf(duration).multiply(BigInteger.valueOf(C.MICROS_PER_SECOND));
        return BigIntegerMath.divide(numerator, denominator, RoundingMode.CEILING).longValue();
      } else {
        return INDEX_UNBOUNDED;
      }
    }
  }
  /** 表示 MPD 的 SegmentTimeline 列表中的时间线分段。 */
  public static final class SegmentTimelineElement {

    /* package */ final long startTime;
    /* package */ final long duration;

    /**
     * @param startTime 分段的开始时间。以秒为单位的值是该值除以所属元素的 {@code timescale} 的结果。
     * @param duration 分段的时长。以秒为单位的值是该值除以所属元素的 {@code timescale} 的结果。
     */
    public SegmentTimelineElement(long startTime, long duration) {
      this.startTime = startTime;
      this.duration = duration;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SegmentTimelineElement that = (SegmentTimelineElement) o;
      return startTime == that.startTime && duration == that.duration;
    }

    @Override
    public int hashCode() {
      return 31 * (int) startTime + (int) duration;
    }
  }
}
