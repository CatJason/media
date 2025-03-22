package androidx.media3.extractor.mp4;

import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.media3.common.C;
import androidx.media3.common.util.Util;

/**
 * 用于重新分块固定样本大小的媒体数据（例如未压缩的音频），其中每个样本都是关键帧。
 */
/* package */ final class FixedSampleSizeRechunker {

  /** 重新分块操作的结果。 */
  public static final class Results {

    public final long[] offsets; // 每个新样本的偏移量
    public final int[] sizes; // 每个新样本的大小
    public final int maximumSize; // 所有新样本中的最大大小
    public final long[] timestamps; // 每个新样本的时间戳
    public final int[] flags; // 每个新样本的标志位
    public final long duration; // 媒体总时长

    private Results(
        long[] offsets,
        int[] sizes,
        int maximumSize,
        long[] timestamps,
        int[] flags,
        long duration) {
      this.offsets = offsets;
      this.sizes = sizes;
      this.maximumSize = maximumSize;
      this.timestamps = timestamps;
      this.flags = flags;
      this.duration = duration;
    }
  }

  /** 重新分块后每个缓冲区的最大字节数。 */
  private static final int MAX_SAMPLE_SIZE = 8 * 1024;

  /**
   * 对给定的固定样本大小输入进行重新分块，生成新的样本序列。
   *
   * @param fixedSampleSize 每个样本的大小（字节）。
   * @param chunkOffsets MP4 流中每个块的偏移量。
   * @param chunkSampleCounts MP4 流中每个块的样本数量。
   * @param timestampDeltaInTimeUnits 每个样本之间的时间戳增量（以时间单位表示）。
   */
  public static Results rechunk(
      int fixedSampleSize,
      long[] chunkOffsets,
      int[] chunkSampleCounts,
      long timestampDeltaInTimeUnits) {
    // 计算每个新缓冲区可以容纳的最大样本数量
    int maxSampleCount = MAX_SAMPLE_SIZE / fixedSampleSize;

    // 计算重新分块后的新样本总数
    int rechunkedSampleCount = 0;
    for (int chunkSampleCount : chunkSampleCounts) {
      rechunkedSampleCount += Util.ceilDivide(chunkSampleCount, maxSampleCount);
    }

    // 初始化新样本的偏移量、大小、时间戳和标志位数组
    long[] offsets = new long[rechunkedSampleCount];
    int[] sizes = new int[rechunkedSampleCount];
    int maximumSize = 0;
    long[] timestamps = new long[rechunkedSampleCount];
    int[] flags = new int[rechunkedSampleCount];

    // 遍历原始块，生成新的样本数据
    int originalSampleIndex = 0;
    int newSampleIndex = 0;
    for (int chunkIndex = 0; chunkIndex < chunkSampleCounts.length; chunkIndex++) {
      int chunkSamplesRemaining = chunkSampleCounts[chunkIndex]; // 当前块剩余的样本数量
      long sampleOffset = chunkOffsets[chunkIndex]; // 当前块的起始偏移量

      while (chunkSamplesRemaining > 0) {
        // 计算当前新缓冲区的样本数量
        int bufferSampleCount = min(maxSampleCount, chunkSamplesRemaining);

        // 设置新样本的偏移量、大小、时间戳和标志位
        offsets[newSampleIndex] = sampleOffset;
        sizes[newSampleIndex] = fixedSampleSize * bufferSampleCount;
        maximumSize = max(maximumSize, sizes[newSampleIndex]);
        timestamps[newSampleIndex] = (timestampDeltaInTimeUnits * originalSampleIndex);
        flags[newSampleIndex] = C.BUFFER_FLAG_KEY_FRAME;

        // 更新偏移量和样本索引
        sampleOffset += sizes[newSampleIndex];
        originalSampleIndex += bufferSampleCount;

        // 减少当前块剩余的样本数量
        chunkSamplesRemaining -= bufferSampleCount;
        newSampleIndex++;
      }
    }

    // 计算媒体总时长
    long duration = timestampDeltaInTimeUnits * originalSampleIndex;

    // 返回重新分块的结果
    return new Results(offsets, sizes, maximumSize, timestamps, flags, duration);
  }

  private FixedSampleSizeRechunker() {
    // 防止实例化
  }
}