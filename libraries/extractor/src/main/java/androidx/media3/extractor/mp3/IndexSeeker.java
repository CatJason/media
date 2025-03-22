package androidx.media3.extractor.mp3;

import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.IndexSeekMap;
import java.math.RoundingMode;

/** MP3 搜索器，在读取流时构建时间到字节的映射。 */
/* package */ final class IndexSeeker implements Seeker {

  @VisibleForTesting
  /* package */ static final long MIN_TIME_BETWEEN_POINTS_US = C.MICROS_PER_SECOND / 10; // 两个搜索点之间的最小时间间隔（单位：微秒）

  private final long dataEndPosition; // 数据结束位置
  private final int averageBitrate; // 平均比特率
  private final IndexSeekMap indexSeekMap; // 索引搜索映射

  /**
   * 构造一个实例。
   *
   * @param durationUs 流的持续时间（单位：微秒）。
   * @param dataStartPosition 数据的起始位置（单位：字节）。
   * @param dataEndPosition 数据的结束位置（单位：字节）。
   */
  public IndexSeeker(long durationUs, long dataStartPosition, long dataEndPosition) {
    this.indexSeekMap =
        new IndexSeekMap(
            /* positions= */ new long[] {dataStartPosition}, // 初始位置数组
            /* timesUs= */ new long[] {0L}, // 初始时间数组
            durationUs); // 流的持续时间
    this.dataEndPosition = dataEndPosition;
    if (durationUs != C.TIME_UNSET) {
      // 计算平均比特率
      long bitrate =
          Util.scaleLargeValue(
              dataStartPosition - dataEndPosition, 8, durationUs, RoundingMode.HALF_UP);
      this.averageBitrate =
          bitrate > 0 && bitrate <= Integer.MAX_VALUE ? (int) bitrate : C.RATE_UNSET_INT;
    } else {
      this.averageBitrate = C.RATE_UNSET_INT;
    }
  }

  @Override
  public long getTimeUs(long position) {
    return indexSeekMap.getTimeUs(position); // 根据位置返回对应的时间（单位：微秒）
  }

  @Override
  public long getDataEndPosition() {
    return dataEndPosition; // 返回数据结束位置
  }

  @Override
  public boolean isSeekable() {
    return indexSeekMap.isSeekable(); // 返回是否可搜索
  }

  @Override
  public long getDurationUs() {
    return indexSeekMap.getDurationUs(); // 返回流的持续时间
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    return indexSeekMap.getSeekPoints(timeUs); // 根据时间返回搜索点
  }

  @Override
  public int getAverageBitrate() {
    return averageBitrate; // 返回平均比特率
  }

  /**
   * 如果搜索点与已有搜索点的时间间隔足够大，则将其添加到索引中。
   *
   * <p>搜索点必须按顺序添加。
   *
   * @param timeUs 要添加的搜索点对应的时间（单位：微秒）。
   * @param position 要添加的搜索点对应的位置（单位：字节）。
   */
  public void maybeAddSeekPoint(long timeUs, long position) {
    if (isTimeUsInIndex(timeUs)) { // 如果时间已经在索引中，则跳过
      return;
    }
    indexSeekMap.addSeekPoint(timeUs, position); // 添加搜索点到索引中
  }

  /**
   * 根据 {@code timeUs}（单位：微秒）与索引中最后一个记录搜索点的接近程度，判断是否应将其视为索引的一部分。
   *
   * <p>此方法假设 {@code timeUs} 是按递增顺序提供的，与 {@link #maybeAddSeekPoint(long, long)} 中添加点的顺序一致。
   *
   * @param timeUs 要检查的时间（单位：微秒）。
   */
  public boolean isTimeUsInIndex(long timeUs) {
    return indexSeekMap.isTimeUsInIndex(timeUs, MIN_TIME_BETWEEN_POINTS_US); // 检查时间是否在索引中
  }

  /* package */ void setDurationUs(long durationUs) {
    indexSeekMap.setDurationUs(durationUs); // 设置流的持续时间
  }
}