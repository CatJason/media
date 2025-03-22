package androidx.media3.decoder;

import androidx.annotation.CallSuper;
import androidx.media3.common.util.UnstableApi;

/** 由 {@link Decoder} 解码的输出缓冲区。 */
@UnstableApi
public abstract class DecoderOutputBuffer extends Buffer {

  /** 缓冲区所有者。 */
  public interface Owner<S extends DecoderOutputBuffer> {

    /**
     * 释放缓冲区。
     *
     * @param outputBuffer 输出缓冲区。
     */
    void releaseOutputBuffer(S outputBuffer);
  }

  /** 缓冲区的呈现时间戳，以微秒为单位。 */
  public long timeUs;

  /**
   * 在此缓冲区之前被 {@link Decoder} 跳过的缓冲区数量。
   */
  public int skippedOutputBufferCount;

  /**
   * 是否应跳过此缓冲区，通常是因为解码过程未生成数据或生成的数据无效。
   */
  public boolean shouldBeSkipped;

  /** 释放输出缓冲区以供重用。当不再需要缓冲区时必须调用此方法。 */
  public abstract void release();

  @Override
  @CallSuper
  public void clear() {
    super.clear();
    timeUs = 0;
    skippedOutputBufferCount = 0;
    shouldBeSkipped = false;
  }
}