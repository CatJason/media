package androidx.media3.extractor.metadata.mp4;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 保存有关轨道中慢速播放片段的信息。 */
@UnstableApi
public final class SlowMotionData implements Metadata.Entry {

  /** 保存有关轨道中单个慢速播放片段的信息。 */
  public static final class Segment implements Parcelable {

    public static final Comparator<Segment> BY_START_THEN_END_THEN_DIVISOR =
        (s1, s2) ->
            ComparisonChain.start()
                .compare(s1.startTimeMs, s2.startTimeMs) // 按开始时间比较
                .compare(s1.endTimeMs, s2.endTimeMs) // 按结束时间比较
                .compare(s1.speedDivisor, s2.speedDivisor) // 按速度除数比较
                .result();

    /** 慢速播放片段的开始时间，单位为毫秒。 */
    public final long startTimeMs;

    /** 慢速播放片段的结束时间，单位为毫秒。 */
    public final long endTimeMs;

    /**
     * 速度减除数。
     *
     * <p>例如，4 表示该片段应以正常速度的四分之一（1/4）播放。
     */
    public final int speedDivisor;

    /**
     * 创建一个实例。
     *
     * @param startTimeMs 参见 {@link #startTimeMs}。必须小于 endTimeMs。
     * @param endTimeMs 参见 {@link #endTimeMs}。
     * @param speedDivisor 参见 {@link #speedDivisor}。
     */
    public Segment(long startTimeMs, long endTimeMs, int speedDivisor) {
      checkArgument(startTimeMs < endTimeMs); // 检查开始时间是否小于结束时间
      this.startTimeMs = startTimeMs;
      this.endTimeMs = endTimeMs;
      this.speedDivisor = speedDivisor;
    }

    @Override
    public String toString() {
      return Util.formatInvariant(
          "Segment: startTimeMs=%d, endTimeMs=%d, speedDivisor=%d",
          startTimeMs, endTimeMs, speedDivisor); // 返回片段的字符串表示
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true; // 如果是同一个对象，返回 true
      }
      if (o == null || getClass() != o.getClass()) {
        return false; // 如果对象为 null 或类型不同，返回 false
      }
      Segment segment = (Segment) o;
      return startTimeMs == segment.startTimeMs
          && endTimeMs == segment.endTimeMs
          && speedDivisor == segment.speedDivisor; // 比较所有字段
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(startTimeMs, endTimeMs, speedDivisor); // 计算哈希值
    }

    @Override
    public int describeContents() {
      return 0; // 描述内容类型
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeLong(startTimeMs);
      dest.writeLong(endTimeMs);
      dest.writeInt(speedDivisor); // 将数据写入 Parcel
    }

    public static final Creator<Segment> CREATOR =
        new Creator<Segment>() {

          @Override
          public Segment createFromParcel(Parcel in) {
            long startTimeMs = in.readLong();
            long endTimeMs = in.readLong();
            int speedDivisor = in.readInt();
            return new Segment(startTimeMs, endTimeMs, speedDivisor); // 从 Parcel 创建实例
          }

          @Override
          public Segment[] newArray(int size) {
            return new Segment[size]; // 创建新数组
          }
        };
  }

  public final List<Segment> segments;

  /**
   * 使用一组 {@link Segment} 创建一个实例。
   *
   * <p>片段不能重叠，即一个片段的开始时间不能位于另一个片段的开始时间和结束时间之间。
   */
  public SlowMotionData(List<Segment> segments) {
    this.segments = segments;
    checkArgument(!doSegmentsOverlap(segments)); // 检查片段是否重叠
  }

  @Override
  public String toString() {
    return "SlowMotion: segments=" + segments; // 返回慢速播放数据的字符串表示
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true; // 如果是同一个对象，返回 true
    }
    if (o == null || getClass() != o.getClass()) {
      return false; // 如果对象为 null 或类型不同，返回 false
    }
    SlowMotionData that = (SlowMotionData) o;
    return segments.equals(that.segments); // 比较片段列表
  }

  @Override
  public int hashCode() {
    return segments.hashCode(); // 计算哈希值
  }

  @Override
  public int describeContents() {
    return 0; // 描述内容类型
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeList(segments); // 将片段列表写入 Parcel
  }

  public static final Creator<SlowMotionData> CREATOR =
      new Creator<SlowMotionData>() {
        @Override
        public SlowMotionData createFromParcel(Parcel in) {
          List<Segment> slowMotionSegments = new ArrayList<>();
          in.readList(slowMotionSegments, Segment.class.getClassLoader());
          return new SlowMotionData(slowMotionSegments); // 从 Parcel 创建实例
        }

        @Override
        public SlowMotionData[] newArray(int size) {
          return new SlowMotionData[size]; // 创建新数组
        }
      };

  private static boolean doSegmentsOverlap(List<Segment> segments) {
    if (segments.isEmpty()) {
      return false; // 如果片段列表为空，返回 false
    }
    long previousEndTimeMs = segments.get(0).endTimeMs;
    for (int i = 1; i < segments.size(); i++) {
      if (segments.get(i).startTimeMs < previousEndTimeMs) {
        return true; // 如果片段重叠，返回 true
      }
      previousEndTimeMs = segments.get(i).endTimeMs;
    }

    return false; // 片段不重叠，返回 false
  }
}