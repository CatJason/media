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

import static androidx.annotation.VisibleForTesting.NONE;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;

import android.media.MediaCodec;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoInfo;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * 在后台线程中执行 {@link MediaCodec} 输入缓冲区的排队操作。这在 API 33 及以下版本中是必需的，
 * 因为排队安全缓冲区会阻塞，直到解密完成。
 */
@RequiresApi(23)
    /* package */ class AsynchronousMediaCodecBufferEnqueuer implements MediaCodecBufferEnqueuer {

  private static final int MSG_QUEUE_INPUT_BUFFER = 1;
  private static final int MSG_QUEUE_SECURE_INPUT_BUFFER = 2;
  private static final int MSG_OPEN_CV = 3;
  private static final int MSG_SET_PARAMETERS = 4;

  @GuardedBy("MESSAGE_PARAMS_INSTANCE_POOL")
  private static final ArrayDeque<MessageParams> MESSAGE_PARAMS_INSTANCE_POOL = new ArrayDeque<>();

  private static final Object QUEUE_SECURE_LOCK = new Object();

  private final MediaCodec codec;
  private final HandlerThread handlerThread;
  private @MonotonicNonNull Handler handler;
  private final AtomicReference<@NullableType RuntimeException> pendingRuntimeException;
  private final ConditionVariable conditionVariable;
  private boolean started;

  /**
   * 创建一个新实例，将输入缓冲区提交到指定的 {@link MediaCodec}。
   *
   * @param codec 要提交输入缓冲区的 {@link MediaCodec}。
   * @param queueingThread 用于排队缓冲区的 {@link HandlerThread}。
   */
  public AsynchronousMediaCodecBufferEnqueuer(MediaCodec codec, HandlerThread queueingThread) {
    this(codec, queueingThread, /* conditionVariable= */ new ConditionVariable());
  }

  @VisibleForTesting
    /* package */ AsynchronousMediaCodecBufferEnqueuer(
      MediaCodec codec, HandlerThread handlerThread, ConditionVariable conditionVariable) {
    this.codec = codec;
    this.handlerThread = handlerThread;
    this.conditionVariable = conditionVariable;
    pendingRuntimeException = new AtomicReference<>();
  }

  @Override
  public void start() {
    if (!started) {
      handlerThread.start();
      handler =
          new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
              doHandleMessage(msg);
            }
          };
      started = true;
    }
  }

  @Override
  public void queueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flags) {
    maybeThrowException();
    MessageParams messageParams = getMessageParams();
    messageParams.setQueueParams(index, offset, size, presentationTimeUs, flags);
    Message message = castNonNull(handler).obtainMessage(MSG_QUEUE_INPUT_BUFFER, messageParams);
    message.sendToTarget();
  }

  @Override
  public void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
    maybeThrowException();
    MessageParams messageParams = getMessageParams();
    messageParams.setQueueParams(index, offset, /* size= */ 0, presentationTimeUs, flags);
    copy(info, messageParams.cryptoInfo);
    Message message =
        castNonNull(handler).obtainMessage(MSG_QUEUE_SECURE_INPUT_BUFFER, messageParams);
    message.sendToTarget();
  }

  @Override
  public void setParameters(Bundle params) {
    maybeThrowException();
    castNonNull(handler).obtainMessage(MSG_SET_PARAMETERS, params).sendToTarget();
  }

  @Override
  public void flush() {
    if (started) {
      try {
        flushHandlerThread();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // 播放线程不应被中断。将此作为 IllegalStateException 抛出。
        throw new IllegalStateException(e);
      }
    }
  }

  @Override
  public void shutdown() {
    if (started) {
      flush();
      handlerThread.quit();
    }
    started = false;
  }

  @Override
  public void waitUntilQueueingComplete() throws InterruptedException {
    blockUntilHandlerThreadIsIdle();
  }

  @Override
  public void maybeThrowException() {
    @Nullable RuntimeException exception = pendingRuntimeException.getAndSet(null);
    if (exception != null) {
      throw exception;
    }
  }

  /**
   * 清空通过 {@link #handler} 在 {@link #handlerThread} 上排队的任务。此方法会阻塞，直到 {@link #handlerThread} 空闲。
   */
  private void flushHandlerThread() throws InterruptedException {
    checkNotNull(this.handler).removeCallbacksAndMessages(null);
    blockUntilHandlerThreadIsIdle();
  }

  private void blockUntilHandlerThreadIsIdle() throws InterruptedException {
    conditionVariable.close();
    checkNotNull(handler).obtainMessage(MSG_OPEN_CV).sendToTarget();
    conditionVariable.block();
  }

  @VisibleForTesting(otherwise = NONE)
    /* package */ void setPendingRuntimeException(RuntimeException exception) {
    pendingRuntimeException.set(exception);
  }

  // 从处理程序线程调用

  private void doHandleMessage(Message msg) {
    @Nullable MessageParams params = null;
    switch (msg.what) {
      case MSG_QUEUE_INPUT_BUFFER:
        params = (MessageParams) msg.obj;
        doQueueInputBuffer(
            params.index, params.offset, params.size, params.presentationTimeUs, params.flags);
        break;
      case MSG_QUEUE_SECURE_INPUT_BUFFER:
        params = (MessageParams) msg.obj;
        doQueueSecureInputBuffer(
            params.index,
            params.offset,
            params.cryptoInfo,
            params.presentationTimeUs,
            params.flags);
        break;
      case MSG_OPEN_CV:
        conditionVariable.open();
        break;
      case MSG_SET_PARAMETERS:
        Bundle parameters = (Bundle) msg.obj;
        doSetParameters(parameters);
        break;
      default:
        pendingRuntimeException.compareAndSet(
            null, new IllegalStateException(String.valueOf(msg.what)));
    }
    if (params != null) {
      recycleMessageParams(params);
    }
  }

  private void doQueueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flag) {
    try {
      codec.queueInputBuffer(index, offset, size, presentationTimeUs, flag);
    } catch (RuntimeException e) {
      pendingRuntimeException.compareAndSet(null, e);
    }
  }

  private void doQueueSecureInputBuffer(
      int index, int offset, MediaCodec.CryptoInfo info, long presentationTimeUs, int flags) {
    try {
      // 同步调用 MediaCodec.queueSecureInputBuffer()，以避免音频和视频共享同一 DRM 会话时加密模块内的竞争条件
      // （参见 [Internal: b/149908061]）。
      synchronized (QUEUE_SECURE_LOCK) {
        codec.queueSecureInputBuffer(index, offset, info, presentationTimeUs, flags);
      }
    } catch (RuntimeException e) {
      pendingRuntimeException.compareAndSet(null, e);
    }
  }

  private void doSetParameters(Bundle parameters) {
    try {
      codec.setParameters(parameters);
    } catch (RuntimeException e) {
      pendingRuntimeException.compareAndSet(null, e);
    }
  }

  private static MessageParams getMessageParams() {
    synchronized (MESSAGE_PARAMS_INSTANCE_POOL) {
      if (MESSAGE_PARAMS_INSTANCE_POOL.isEmpty()) {
        return new MessageParams();
      } else {
        return MESSAGE_PARAMS_INSTANCE_POOL.removeFirst();
      }
    }
  }

  private static void recycleMessageParams(MessageParams params) {
    synchronized (MESSAGE_PARAMS_INSTANCE_POOL) {
      MESSAGE_PARAMS_INSTANCE_POOL.add(params);
    }
  }

  /** 用于排队输入缓冲区和安全输入缓冲区任务的参数。 */
  private static class MessageParams {
    public int index;
    public int offset;
    public int size;
    public final MediaCodec.CryptoInfo cryptoInfo;
    public long presentationTimeUs;
    public int flags;

    MessageParams() {
      cryptoInfo = new MediaCodec.CryptoInfo();
    }

    /** 用于设置排队参数的便捷方法。 */
    public void setQueueParams(
        int index, int offset, int size, long presentationTimeUs, int flags) {
      this.index = index;
      this.offset = offset;
      this.size = size;
      this.presentationTimeUs = presentationTimeUs;
      this.flags = flags;
    }
  }

  /** 将 {@code cryptoInfo} 深度复制到 {@code frameworkCryptoInfo}。 */
  private static void copy(
      CryptoInfo cryptoInfo, android.media.MediaCodec.CryptoInfo frameworkCryptoInfo) {
    // 直接更新 frameworkCryptoInfo 字段，因为 CryptoInfo.set 在 Android N 上执行了不必要的对象分配。
    frameworkCryptoInfo.numSubSamples = cryptoInfo.numSubSamples;
    frameworkCryptoInfo.numBytesOfClearData =
        copy(cryptoInfo.numBytesOfClearData, frameworkCryptoInfo.numBytesOfClearData);
    frameworkCryptoInfo.numBytesOfEncryptedData =
        copy(cryptoInfo.numBytesOfEncryptedData, frameworkCryptoInfo.numBytesOfEncryptedData);
    frameworkCryptoInfo.key = checkNotNull(copy(cryptoInfo.key, frameworkCryptoInfo.key));
    frameworkCryptoInfo.iv = checkNotNull(copy(cryptoInfo.iv, frameworkCryptoInfo.iv));
    frameworkCryptoInfo.mode = cryptoInfo.mode;
    if (Util.SDK_INT >= 24) {
      android.media.MediaCodec.CryptoInfo.Pattern pattern =
          new android.media.MediaCodec.CryptoInfo.Pattern(
              cryptoInfo.encryptedBlocks, cryptoInfo.clearBlocks);
      frameworkCryptoInfo.setPattern(pattern);
    }
  }

  /**
   * 复制 {@code src}，如果 {@code dst} 的长度至少与 {@code src} 相同，则重用 {@code dst}。
   *
   * @param src 源数组。
   * @param dst 目标数组，如果其长度至少与 {@code src} 相同，则会被重用。
   * @return 复制的数组，如果 {@code dst} 被重用，则可能是 {@code dst}。
   */
  @Nullable
  private static int[] copy(@Nullable int[] src, @Nullable int[] dst) {
    if (src == null) {
      return dst;
    }

    if (dst == null || dst.length < src.length) {
      return Arrays.copyOf(src, src.length);
    } else {
      System.arraycopy(src, 0, dst, 0, src.length);
      return dst;
    }
  }

  /**
   * 复制 {@code src}，如果 {@code dst} 的长度至少与 {@code src} 相同，则重用 {@code dst}。
   *
   * @param src 源数组。
   * @param dst 目标数组，如果其长度至少与 {@code src} 相同，则会被重用。
   * @return 复制的数组，如果 {@code dst} 被重用，则可能是 {@code dst}。
   */
  @Nullable
  private static byte[] copy(@Nullable byte[] src, @Nullable byte[] dst) {
    if (src == null) {
      return dst;
    }

    if (dst == null || dst.length < src.length) {
      return Arrays.copyOf(src, src.length);
    } else {
      System.arraycopy(src, 0, dst, 0, src.length);
      return dst;
    }
  }
}