package androidx.media3.exoplayer.hls;

import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.source.SampleQueue;
import androidx.media3.exoplayer.source.chunk.MediaChunk;
import java.io.IOException;

/**
 * 当尝试将样本写入 {@link SampleQueue} 时，样本的时间戳与其来源的块不一致时抛出的异常。
 */
/* package */ final class UnexpectedSampleTimestampException extends IOException {

  /** 包含被拒绝样本的 {@link MediaChunk}。 */
  public final MediaChunk mediaChunk;

  /**
   * 从 {@link #mediaChunk} 加载并成功写入 {@link SampleQueue} 的最后一个样本的时间戳，单位为微秒。
   * 如果块中的第一个样本被拒绝，则为 {@link C#TIME_UNSET}。
   */
  public final long lastAcceptedSampleTimeUs;

  /** 被拒绝样本的时间戳，单位为微秒。 */
  public final long rejectedSampleTimeUs;

  /**
   * 构造一个实例。
   *
   * @param mediaChunk 包含意外样本时间戳的 {@link MediaChunk}。
   * @param lastAcceptedSampleTimeUs 从块中加载并成功写入 {@link SampleQueue} 的最后一个样本的时间戳，单位为微秒。
   *     如果块中的第一个样本被拒绝，则为 {@link C#TIME_UNSET}。
   * @param rejectedSampleTimeUs 被拒绝样本的时间戳，单位为微秒。
   */
  public UnexpectedSampleTimestampException(
      MediaChunk mediaChunk, long lastAcceptedSampleTimeUs, long rejectedSampleTimeUs) {
    super(
        "意外的样本时间戳: "
            + Util.usToMs(rejectedSampleTimeUs)
            + "，位于块 ["
            + mediaChunk.startTimeUs
            + ", "
            + mediaChunk.endTimeUs
            + "] 中");
    this.mediaChunk = mediaChunk;
    this.lastAcceptedSampleTimeUs = lastAcceptedSampleTimeUs;
    this.rejectedSampleTimeUs = rejectedSampleTimeUs;
  }
}