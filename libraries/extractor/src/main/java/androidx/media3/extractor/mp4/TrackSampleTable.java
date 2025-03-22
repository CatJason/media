package androidx.media3.extractor.mp4;

import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/** MP4 文件中轨道的样本表。 */
@UnstableApi
public final class TrackSampleTable {

  /** 与该样本表对应的轨道。 */
  public final Track track;

  /** 样本数量。 */
  public final int sampleCount;

  /** 样本的字节偏移量。 */
  public final long[] offsets;

  /** 样本的字节大小。 */
  public final int[] sizes;

  /** {@link #sizes} 中的最大样本大小。 */
  public final int maximumSize;

  /** 样本的时间戳（微秒）。 */
  public final long[] timestampsUs;

  /** 样本的标志位。 */
  public final int[] flags;

  /** 轨道样本表的总持续时间（微秒）。 */
  public final long durationUs;

  public TrackSampleTable(
      Track track,
      long[] offsets,
      int[] sizes,
      int maximumSize,
      long[] timestampsUs,
      int[] flags,
      long durationUs) {
    // 检查数组长度是否一致
    Assertions.checkArgument(sizes.length == timestampsUs.length);
    Assertions.checkArgument(offsets.length == timestampsUs.length);
    Assertions.checkArgument(flags.length == timestampsUs.length);

    this.track = track;
    this.offsets = offsets;
    this.sizes = sizes;
    this.maximumSize = maximumSize;
    this.timestampsUs = timestampsUs;
    this.flags = flags;
    this.durationUs = durationUs;
    sampleCount = offsets.length;
    // 如果 flags 数组不为空，则将最后一个样本的标志位设置为 BUFFER_FLAG_LAST_SAMPLE
    if (flags.length > 0) {
      flags[flags.length - 1] |= C.BUFFER_FLAG_LAST_SAMPLE;
    }
  }

  /**
   * 返回在给定时间戳之前或等于该时间戳的最接近的同步样本的索引，如果存在。
   *
   * @param timeUs 用于查找同步样本的时间戳。
   * @return 同步样本的索引，如果不存在则返回 {@link C#INDEX_UNSET}。
   */
  public int getIndexOfEarlierOrEqualSynchronizationSample(long timeUs) {
    // 视频帧的时间戳可能未排序，因此此方法的行为可能未定义。
    // 帧不会在同步样本之后重新排序，因此在实际中此方法有效。
    int startIndex = Util.binarySearchFloor(timestampsUs, timeUs, true, false);
    for (int i = startIndex; i >= 0; i--) {
      if ((flags[i] & C.BUFFER_FLAG_KEY_FRAME) != 0) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  /**
   * 返回在给定时间戳之后或等于该时间戳的最接近的同步样本的索引，如果存在。
   *
   * @param timeUs 用于查找同步样本的时间戳。
   * @return 同步样本的索引，如果不存在则返回 {@link C#INDEX_UNSET}。
   */
  public int getIndexOfLaterOrEqualSynchronizationSample(long timeUs) {
    int startIndex = Util.binarySearchCeil(timestampsUs, timeUs, true, false);
    for (int i = startIndex; i < timestampsUs.length; i++) {
      if ((flags[i] & C.BUFFER_FLAG_KEY_FRAME) != 0) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }
}