/*
 * 版权所有 (C) 2020 The Android Open Source Project
 *
 * 根据 Apache License, Version 2.0（“许可证”）授权；
 * 除非符合许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则按“原样”分发的软件
 * 没有任何形式的明示或暗示的保证或条件。
 * 请参阅许可证以了解特定语言下的权限和限制。
 */
package androidx.media3.exoplayer.mediacodec;

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.decoder.DecoderInputBuffer;
import java.nio.ByteBuffer;

/** 用于批量处理的多样本缓冲区，可以将多个样本缓冲区追加到其中。 */
/* package */ final class BatchBuffer extends DecoderInputBuffer {

  /** 默认情况下，缓冲区在满之前可以追加的最大样本数。 */
  public static final int DEFAULT_MAX_SAMPLE_COUNT = 32;

  /**
   * 缓冲区的最大大小（以字节为单位）。这可以防止高比特率流占用过多内存。
   * 该限制相当于最高比特率（320kb/s）下 75 秒的 mp3 音频，或最高比特率（800kb/s）下 30 秒的 AAC LC 音频。
   * 对于第一个样本，此限制被忽略。
   */
  @VisibleForTesting /* package */ static final int MAX_SIZE_BYTES = 3 * 1000 * 1024;

  private long lastSampleTimeUs;
  private int sampleCount;
  private int maxSampleCount;

  public BatchBuffer() {
    super(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
    maxSampleCount = DEFAULT_MAX_SAMPLE_COUNT;
  }

  @Override
  public void clear() {
    super.clear();
    sampleCount = 0;
  }

  /** 设置缓冲区在满之前可以追加的最大样本数。 */
  public void setMaxSampleCount(@IntRange(from = 1) int maxSampleCount) {
    checkArgument(maxSampleCount > 0);
    this.maxSampleCount = maxSampleCount;
  }

  /**
   * 返回缓冲区中第一个样本的时间戳。如果 {@link #hasSamples()} 为 {@code false}，则返回值未定义。
   */
  public long getFirstSampleTimeUs() {
    return timeUs;
  }

  /**
   * 返回缓冲区中最后一个样本的时间戳。如果 {@link #hasSamples()} 为 {@code false}，则返回值未定义。
   */
  public long getLastSampleTimeUs() {
    return lastSampleTimeUs;
  }

  /** 返回缓冲区中的样本数量。 */
  public int getSampleCount() {
    return sampleCount;
  }

  /** 返回缓冲区是否包含一个或多个样本。 */
  public boolean hasSamples() {
    return sampleCount > 0;
  }

  /**
   * 尝试追加提供的缓冲区。
   *
   * @param buffer 要追加的缓冲区。
   * @return 缓冲区是否成功追加。
   * @throws IllegalArgumentException 如果 {@code buffer} 已加密、包含补充数据或是流结束缓冲区，这些情况均不支持。
   */
  public boolean append(DecoderInputBuffer buffer) {
    checkArgument(!buffer.isEncrypted());
    checkArgument(!buffer.hasSupplementalData());
    checkArgument(!buffer.isEndOfStream());
    if (!canAppendSampleBuffer(buffer)) {
      return false;
    }
    if (sampleCount++ == 0) {
      timeUs = buffer.timeUs;
      if (buffer.isKeyFrame()) {
        setFlags(C.BUFFER_FLAG_KEY_FRAME);
      }
    }
    @Nullable ByteBuffer bufferData = buffer.data;
    if (bufferData != null) {
      ensureSpaceForWrite(bufferData.remaining());
      data.put(bufferData);
    }
    lastSampleTimeUs = buffer.timeUs;
    return true;
  }

  private boolean canAppendSampleBuffer(DecoderInputBuffer buffer) {
    if (!hasSamples()) {
      // 如果缓冲区为空，则始终允许追加，否则无法继续处理。
      return true;
    }
    if (sampleCount >= maxSampleCount) {
      return false;
    }
    @Nullable ByteBuffer bufferData = buffer.data;
    if (bufferData != null
        && data != null
        && data.position() + bufferData.remaining() > MAX_SIZE_BYTES) {
      return false;
    }
    return true;
  }
}