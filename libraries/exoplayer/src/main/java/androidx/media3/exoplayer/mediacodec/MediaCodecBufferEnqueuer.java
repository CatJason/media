/*
 * 版权所有 2023 The Android Open Source Project
 *
 * 根据 Apache 许可证 2.0 版本（“许可证”）授权；
 * 除非符合许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则根据许可证分发的软件是基于“按原样”分发的，
 * 没有任何明示或暗示的担保或条件。
 * 请参阅许可证以了解具体的语言权限和限制。
 */
package androidx.media3.exoplayer.mediacodec;

import android.media.MediaCodec;
import android.os.Bundle;
import androidx.media3.decoder.CryptoInfo;

/**
 * 用于将缓冲区提交到 {@link MediaCodec} 的接口。
 *
 * <p>所有方法必须在同一线程中调用。
 */
/* package */ interface MediaCodecBufferEnqueuer {

  /**
   * 启动此实例。
   *
   * <p>在创建实例后、提交输入缓冲区之前调用此方法。
   */
  void start();

  /**
   * 提交一个输入缓冲区以进行解码。
   *
   * @see android.media.MediaCodec#queueInputBuffer
   */
  void queueInputBuffer(int index, int offset, int size, long presentationTimeUs, int flags);

  /**
   * 提交一个可能包含加密数据的输入缓冲区以进行解码。
   *
   * <p>注意：此方法的行为类似于 {@link MediaCodec#queueSecureInputBuffer}，但不同之处在于
   * {@code info} 的类型是 {@link CryptoInfo} 而不是 {@link MediaCodec.CryptoInfo}。
   *
   * @see MediaCodec#queueSecureInputBuffer
   */
  void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags);

  /**
   * 提交新的编解码器参数，这些参数应从下一个提交的输入缓冲区开始应用。
   *
   * @see MediaCodec#setParameters(Bundle)
   */
  void setParameters(Bundle parameters);

  /** 刷新实例。 */
  void flush();

  /** 关闭实例。确保调用此方法以释放其内部资源。 */
  void shutdown();

  /** 阻塞当前线程，直到所有待提交的输入缓冲区都已提交。 */
  void waitUntilQueueingComplete() throws InterruptedException;

  /** 抛出在提交过程中发生的任何异常。 */
  void maybeThrowException();
}