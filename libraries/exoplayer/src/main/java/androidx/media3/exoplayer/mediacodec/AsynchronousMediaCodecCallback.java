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

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.collection.CircularIntArray;
import androidx.media3.common.util.Util;
import java.util.ArrayDeque;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** 一个在单独线程中路由回调的 {@link MediaCodec.Callback}。 */
@RequiresApi(23)
/* package */ final class AsynchronousMediaCodecCallback extends MediaCodec.Callback {
  private final Object lock;
  private final HandlerThread callbackThread;

  private @MonotonicNonNull Handler handler;

  @GuardedBy("lock")
  private final CircularIntArray availableInputBuffers;

  @GuardedBy("lock")
  private final CircularIntArray availableOutputBuffers;

  @GuardedBy("lock")
  private final ArrayDeque<MediaCodec.BufferInfo> bufferInfos;

  @GuardedBy("lock")
  private final ArrayDeque<MediaFormat> formats;

  @GuardedBy("lock")
  @Nullable
  private MediaFormat currentFormat;

  @GuardedBy("lock")
  @Nullable
  private MediaFormat pendingOutputFormat;

  @GuardedBy("lock")
  @Nullable
  private MediaCodec.CodecException mediaCodecException;

  @GuardedBy("lock")
  @Nullable
  private MediaCodec.CryptoException mediaCodecCryptoException;

  @GuardedBy("lock")
  private long pendingFlushCount;

  @GuardedBy("lock")
  private boolean shutDown;

  @GuardedBy("lock")
  @Nullable
  private IllegalStateException internalException;

  @GuardedBy("lock")
  @Nullable
  private MediaCodecAdapter.OnBufferAvailableListener onBufferAvailableListener;

  /**
   * 创建一个新实例。
   *
   * @param callbackThread 用于路由 {@link MediaCodec} 回调的线程。该线程必须未启动。
   */
  /* package */ AsynchronousMediaCodecCallback(HandlerThread callbackThread) {
    this.lock = new Object();
    this.callbackThread = callbackThread;
    this.availableInputBuffers = new CircularIntArray();
    this.availableOutputBuffers = new CircularIntArray();
    this.bufferInfos = new ArrayDeque<>();
    this.formats = new ArrayDeque<>();
  }

  /**
   * 在 {@code codec} 上设置回调并启动后台回调线程。
   *
   * <p>确保调用 {@link #shutdown()} 以停止后台线程并释放其资源。
   *
   * @see MediaCodec#setCallback(MediaCodec.Callback, Handler)
   */
  public void initialize(MediaCodec codec) {
    checkState(handler == null);

    callbackThread.start();
    Handler handler = new Handler(callbackThread.getLooper());
    codec.setCallback(this, handler);
    // 在最后初始化 this.handler，确保如果 MediaCodec 抛出异常，回调不会被配置。
    this.handler = handler;
  }

  /**
   * 关闭此实例。
   *
   * <p>此方法将停止回调线程。调用后，回调将不再被处理，出队方法将返回 {@link MediaCodec#INFO_TRY_AGAIN_LATER}。
   */
  public void shutdown() {
    synchronized (lock) {
      shutDown = true;
      callbackThread.quit();
      flushInternal();
    }
  }

  /**
   * 返回下一个可用的输入缓冲区索引，如果没有这样的缓冲区，则返回 {@link MediaCodec#INFO_TRY_AGAIN_LATER}。
   */
  public int dequeueInputBufferIndex() {
    synchronized (lock) {
      maybeThrowException();
      if (isFlushingOrShutdown()) {
        return MediaCodec.INFO_TRY_AGAIN_LATER;
      } else {
        return availableInputBuffers.isEmpty()
            ? MediaCodec.INFO_TRY_AGAIN_LATER
            : availableInputBuffers.popFirst();
      }
    }
  }

  /**
   * 返回下一个可用的输出缓冲区索引。如果下一个可用输出是 MediaFormat 更改，则返回 {@link MediaCodec#INFO_OUTPUT_FORMAT_CHANGED}，
   * 您应调用 {@link #getOutputFormat()} 获取格式。如果没有可用输出，则返回 {@link MediaCodec#INFO_TRY_AGAIN_LATER}。
   */
  public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    synchronized (lock) {
      maybeThrowException();
      if (isFlushingOrShutdown()) {
        return MediaCodec.INFO_TRY_AGAIN_LATER;
      } else {
        if (availableOutputBuffers.isEmpty()) {
          return MediaCodec.INFO_TRY_AGAIN_LATER;
        } else {
          int bufferIndex = availableOutputBuffers.popFirst();
          if (bufferIndex >= 0) {
            checkStateNotNull(currentFormat);
            MediaCodec.BufferInfo nextBufferInfo = bufferInfos.remove();
            bufferInfo.set(
                nextBufferInfo.offset,
                nextBufferInfo.size,
                nextBufferInfo.presentationTimeUs,
                nextBufferInfo.flags);
          } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            currentFormat = formats.remove();
          }
          return bufferIndex;
        }
      }
    }
  }

  /**
   * 返回由底层 {@link MediaCodec} 发出的 {@link MediaFormat}。
   *
   * <p>在 {@link #dequeueOutputBufferIndex} 返回 {@link MediaCodec#INFO_OUTPUT_FORMAT_CHANGED} 后调用此方法。
   *
   * @throws IllegalStateException 如果在 {@link #dequeueOutputBufferIndex} 返回 {@link MediaCodec#INFO_OUTPUT_FORMAT_CHANGED} 之前调用。
   */
  public MediaFormat getOutputFormat() {
    synchronized (lock) {
      if (currentFormat == null) {
        throw new IllegalStateException();
      }
      return currentFormat;
    }
  }

  /**
   * 异步启动刷新操作，刷新操作将在回调线程上完成。刷新完成后，将从回调线程触发 {@code onFlushCompleted}。
   */
  public void flush() {
    synchronized (lock) {
      ++pendingFlushCount;
      Util.castNonNull(handler).post(this::onFlushCompleted);
    }
  }

  // 从回调线程调用。

  @Override
  public void onInputBufferAvailable(MediaCodec codec, int index) {
    synchronized (lock) {
      availableInputBuffers.addLast(index);
      if (onBufferAvailableListener != null) {
        onBufferAvailableListener.onInputBufferAvailable();
      }
    }
  }

  @Override
  public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
    synchronized (lock) {
      if (pendingOutputFormat != null) {
        addOutputFormat(pendingOutputFormat);
        pendingOutputFormat = null;
      }
      availableOutputBuffers.addLast(index);
      bufferInfos.add(info);
      if (onBufferAvailableListener != null) {
        onBufferAvailableListener.onOutputBufferAvailable();
      }
    }
  }

  @Override
  public void onError(MediaCodec codec, MediaCodec.CodecException e) {
    synchronized (lock) {
      mediaCodecException = e;
    }
  }

  @Override
  public void onCryptoError(MediaCodec codec, MediaCodec.CryptoException e) {
    synchronized (lock) {
      mediaCodecCryptoException = e;
    }
  }

  @Override
  public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
    synchronized (lock) {
      addOutputFormat(format);
      pendingOutputFormat = null;
    }
  }

  /**
   * 设置 {@link MediaCodecAdapter.OnBufferAvailableListener}，当 {@link #onInputBufferAvailable} 和 {@link #onOutputBufferAvailable} 被调用时通知它。
   *
   * @param onBufferAvailableListener 当 {@link #onInputBufferAvailable} 和 {@link #onOutputBufferAvailable} 被调用时通知的监听器。
   */
  public void setOnBufferAvailableListener(
      MediaCodecAdapter.OnBufferAvailableListener onBufferAvailableListener) {
    synchronized (lock) {
      this.onBufferAvailableListener = onBufferAvailableListener;
    }
  }

  private void onFlushCompleted() {
    synchronized (lock) {
      if (shutDown) {
        return;
      }

      --pendingFlushCount;
      if (pendingFlushCount > 0) {
        // 另一个 flush() 已被调用。
        return;
      } else if (pendingFlushCount < 0) {
        // 这不应该发生。
        setInternalException(new IllegalStateException());
        return;
      }
      flushInternal();
    }
  }

  /** 刷新所有可用的输入和输出缓冲区以及之前设置的任何错误。 */
  @GuardedBy("lock")
  private void flushInternal() {
    if (!formats.isEmpty()) {
      pendingOutputFormat = formats.getLast();
    }
    // 否则，pendingOutputFormat 可能在之前的刷新后已经非空，在这种情况下保持不变。

    // mediaCodecException 不会重置为 null。如果编解码器引发了错误，则即使刷新后它仍处于 FAILED_STATE。
    availableInputBuffers.clear();
    availableOutputBuffers.clear();
    bufferInfos.clear();
    formats.clear();
  }

  @GuardedBy("lock")
  private boolean isFlushingOrShutdown() {
    return pendingFlushCount > 0 || shutDown;
  }

  @GuardedBy("lock")
  private void addOutputFormat(MediaFormat mediaFormat) {
    availableOutputBuffers.addLast(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    formats.add(mediaFormat);
  }

  @GuardedBy("lock")
  private void maybeThrowException() {
    maybeThrowInternalException();
    maybeThrowMediaCodecException();
    maybeThrowMediaCodecCryptoException();
  }

  @GuardedBy("lock")
  private void maybeThrowInternalException() {
    if (internalException != null) {
      IllegalStateException e = internalException;
      internalException = null;
      throw e;
    }
  }

  @GuardedBy("lock")
  private void maybeThrowMediaCodecException() {
    if (mediaCodecException != null) {
      MediaCodec.CodecException codecException = mediaCodecException;
      mediaCodecException = null;
      throw codecException;
    }
  }

  @GuardedBy("lock")
  private void maybeThrowMediaCodecCryptoException() {
    if (mediaCodecCryptoException != null) {
      MediaCodec.CryptoException cryptoException = mediaCodecCryptoException;
      mediaCodecCryptoException = null;
      throw cryptoException;
    }
  }

  private void setInternalException(IllegalStateException e) {
    synchronized (lock) {
      internalException = e;
    }
  }
}