package androidx.media3.decoder;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

/**
 * 媒体解码器。
 *
 * @param <I> 解码器输入缓冲区的类型。
 * @param <O> 解码器输出缓冲区的类型。
 * @param <E> 解码器抛出的异常类型。
 */
@UnstableApi
public interface Decoder<I, O, E extends DecoderException> {

  /**
   * 返回解码器的名称。
   *
   * @return 解码器的名称。
   */
  String getName();

  /**
   * 设置应从哪个时间戳开始生成输出缓冲区，以微秒为单位。
   *
   * <p>任何时间戳小于 {@code outputStartTimeUs} 的解码缓冲区应由实现跳过，并且不应通过 {@link #dequeueOutputBuffer} 提供。
   *
   * <p>此方法必须在初始 {@linkplain #queueInputBuffer 排队第一个输入缓冲区} 之前或 {@link #flush()} 之后调用。
   *
   * @param outputStartTimeUs 应从哪个时间开始生成输出缓冲区，以微秒为单位。
   */
  void setOutputStartTimeUs(long outputStartTimeUs);

  /**
   * 取出下一个要填充并排队到解码器的输入缓冲区。
   *
   * @return 输入缓冲区（已被清除），如果没有可用的缓冲区则返回 null。
   * @throws E 如果发生解码器错误。
   */
  @Nullable
  I dequeueInputBuffer() throws E;

  /**
   * 将输入缓冲区排队到解码器。
   *
   * @param inputBuffer 输入缓冲区。
   * @throws E 如果发生解码器错误。
   */
  void queueInputBuffer(I inputBuffer) throws E;

  /**
   * 从解码器中取出下一个输出缓冲区。
   *
   * @return 输出缓冲区，如果没有可用的输出缓冲区则返回 null。
   * @throws E 如果发生解码器错误。
   */
  @Nullable
  O dequeueOutputBuffer() throws E;

  /**
   * 刷新解码器。已取出的输入缓冲区的所有权将返回给解码器。调用者仍负责释放任何已取出的输出缓冲区。
   */
  void flush();

  /** 释放解码器。当不再需要解码器时必须调用此方法。 */
  void release();
}