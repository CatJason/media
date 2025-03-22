/*
 * 版权所有 (C) 2016 The Android Open Source Project
 *
 * 根据 Apache 许可证 2.0 版本（“许可证”）授权；
 * 除非遵守许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则根据许可证分发的软件
 * 均按“原样”分发，不附带任何明示或暗示的担保或条件。
 * 请参阅许可证以了解特定语言的权限和限制。
 */
package androidx.media3.decoder;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import java.util.ArrayDeque;

/**
 * 使用自己的解码线程并立即将每个输入缓冲区解码为相应输出缓冲区的 {@link Decoder} 的基类。
 */
@SuppressWarnings("UngroupedOverloads")
@UnstableApi
public abstract class SimpleDecoder<
    I extends DecoderInputBuffer, O extends DecoderOutputBuffer, E extends DecoderException>
    implements Decoder<I, O, E> {

  private final Thread decodeThread;

  private final Object lock;
  private final ArrayDeque<I> queuedInputBuffers;
  private final ArrayDeque<O> queuedOutputBuffers;
  private final I[] availableInputBuffers;
  private final O[] availableOutputBuffers;

  private int availableInputBufferCount;
  private int availableOutputBufferCount;
  @Nullable private I dequeuedInputBuffer;

  @Nullable private E exception;
  private boolean flushed;
  private boolean released;
  private int skippedOutputBufferCount;
  private long outputStartTimeUs;

  /**
   * @param inputBuffers 用于存储输入缓冲区引用的空数组。
   * @param outputBuffers 用于存储输出缓冲区引用的空数组。
   */
  @SuppressWarnings("nullness:method.invocation")
  protected SimpleDecoder(I[] inputBuffers, O[] outputBuffers) {
    lock = new Object();
    outputStartTimeUs = C.TIME_UNSET;
    queuedInputBuffers = new ArrayDeque<>();
    queuedOutputBuffers = new ArrayDeque<>();
    availableInputBuffers = inputBuffers;
    availableInputBufferCount = inputBuffers.length;
    for (int i = 0; i < availableInputBufferCount; i++) {
      availableInputBuffers[i] = createInputBuffer();
    }
    availableOutputBuffers = outputBuffers;
    availableOutputBufferCount = outputBuffers.length;
    for (int i = 0; i < availableOutputBufferCount; i++) {
      availableOutputBuffers[i] = createOutputBuffer();
    }
    decodeThread =
        new Thread("ExoPlayer:SimpleDecoder") {
          @Override
          public void run() {
            SimpleDecoder.this.run();
          }
        };
    decodeThread.start();
  }

  /**
   * 设置每个输入缓冲区的初始大小。
   *
   * <p>此方法应在解码器使用之前调用（即在第一次调用 {@link #dequeueInputBuffer()} 之前）。
   *
   * @param size 所需的输入缓冲区大小。
   */
  protected final void setInitialInputBufferSize(int size) {
    Assertions.checkState(availableInputBufferCount == availableInputBuffers.length);
    for (I inputBuffer : availableInputBuffers) {
      inputBuffer.ensureSpaceForWrite(size);
    }
  }

  /**
   * 返回采样时间是否大于或等于 {@link #setOutputStartTimeUs} 设置的输出开始时间。
   *
   * <p>如果此方法返回 false，则缓冲区将不会作为输出缓冲区提供。
   *
   * @param timeUs 缓冲区时间，以微秒为单位。
   * @return 缓冲区时间是否大于或等于输出开始时间，或者如果未设置输出开始时间则返回 {@code true}。
   */
  protected final boolean isAtLeastOutputStartTimeUs(long timeUs) {
    synchronized (lock) {
      return outputStartTimeUs == C.TIME_UNSET || timeUs >= outputStartTimeUs;
    }
  }

  @Override
  public final void setOutputStartTimeUs(long outputStartTimeUs) {
    synchronized (lock) {
      Assertions.checkState(availableInputBufferCount == availableInputBuffers.length || flushed);
      this.outputStartTimeUs = outputStartTimeUs;
    }
  }

  @Override
  @Nullable
  public final I dequeueInputBuffer() throws E {
    synchronized (lock) {
      maybeThrowException();
      Assertions.checkState(dequeuedInputBuffer == null);
      dequeuedInputBuffer =
          availableInputBufferCount == 0
              ? null
              : availableInputBuffers[--availableInputBufferCount];
      return dequeuedInputBuffer;
    }
  }

  @Override
  public final void queueInputBuffer(I inputBuffer) throws E {
    synchronized (lock) {
      maybeThrowException();
      Assertions.checkArgument(inputBuffer == dequeuedInputBuffer);
      queuedInputBuffers.addLast(inputBuffer);
      maybeNotifyDecodeLoop();
      dequeuedInputBuffer = null;
    }
  }

  @Override
  @Nullable
  public final O dequeueOutputBuffer() throws E {
    synchronized (lock) {
      maybeThrowException();
      if (queuedOutputBuffers.isEmpty()) {
        return null;
      }
      return queuedOutputBuffers.removeFirst();
    }
  }

  /**
   * 将输出缓冲区释放回解码器。
   *
   * @param outputBuffer 要释放的输出缓冲区。
   */
  @CallSuper
  protected void releaseOutputBuffer(O outputBuffer) {
    synchronized (lock) {
      releaseOutputBufferInternal(outputBuffer);
      maybeNotifyDecodeLoop();
    }
  }

  @Override
  public final void flush() {
    synchronized (lock) {
      flushed = true;
      skippedOutputBufferCount = 0;
      if (dequeuedInputBuffer != null) {
        releaseInputBufferInternal(dequeuedInputBuffer);
        dequeuedInputBuffer = null;
      }
      while (!queuedInputBuffers.isEmpty()) {
        releaseInputBufferInternal(queuedInputBuffers.removeFirst());
      }
      while (!queuedOutputBuffers.isEmpty()) {
        queuedOutputBuffers.removeFirst().release();
      }
    }
  }

  @CallSuper
  @Override
  public void release() {
    synchronized (lock) {
      released = true;
      lock.notify();
    }
    try {
      decodeThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * 如果存在解码异常，则抛出该异常。
   *
   * @throws E 解码异常。
   */
  private void maybeThrowException() throws E {
    @Nullable E exception = this.exception;
    if (exception != null) {
      throw exception;
    }
  }

  /**
   * 如果存在排队的输入缓冲区和可用的输出缓冲区以供解码，则通知解码循环。
   *
   * <p>应在锁定对象上同步调用。
   */
  private void maybeNotifyDecodeLoop() {
    if (canDecodeBuffer()) {
      lock.notify();
    }
  }

  private void run() {
    try {
      while (decode()) {
        // 无需操作。
      }
    } catch (InterruptedException e) {
      // 不应发生。
      throw new IllegalStateException(e);
    }
  }

  private boolean decode() throws InterruptedException {
    I inputBuffer;
    O outputBuffer;
    boolean resetDecoder;

    // 等待直到有输入缓冲区可供解码，并且有输出缓冲区可供解码。
    synchronized (lock) {
      while (!released && !canDecodeBuffer()) {
        lock.wait();
      }
      if (released) {
        return false;
      }
      inputBuffer = queuedInputBuffers.removeFirst();
      outputBuffer = availableOutputBuffers[--availableOutputBufferCount];
      resetDecoder = flushed;
      flushed = false;
    }

    if (inputBuffer.isEndOfStream()) {
      outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
    } else {
      outputBuffer.timeUs = inputBuffer.timeUs;
      if (inputBuffer.isFirstSample()) {
        outputBuffer.addFlag(C.BUFFER_FLAG_FIRST_SAMPLE);
      }
      if (!isAtLeastOutputStartTimeUs(inputBuffer.timeUs)) {
        outputBuffer.shouldBeSkipped = true;
      }
      @Nullable E exception;
      try {
        exception = decode(inputBuffer, outputBuffer, resetDecoder);
      } catch (RuntimeException e) {
        // 如果样本格式错误，解码器无法处理，可能会发生这种情况。
        // 我们不希望进程因此崩溃，但希望传播错误。
        exception = createUnexpectedDecodeException(e);
      } catch (OutOfMemoryError e) {
        // 如果样本格式错误，导致解码器认为需要分配大量内存，可能会发生这种情况。
        // 我们不希望进程因此崩溃，但希望传播错误。
        exception = createUnexpectedDecodeException(e);
      }
      if (exception != null) {
        synchronized (lock) {
          this.exception = exception;
        }
        return false;
      }
    }

    synchronized (lock) {
      if (flushed) {
        outputBuffer.release();
      } else if (outputBuffer.shouldBeSkipped) {
        skippedOutputBufferCount++;
        outputBuffer.release();
      } else {
        outputBuffer.skippedOutputBufferCount = skippedOutputBufferCount;
        skippedOutputBufferCount = 0;
        queuedOutputBuffers.addLast(outputBuffer);
      }
      // 使输入缓冲区再次可用。
      releaseInputBufferInternal(inputBuffer);
    }

    return true;
  }

  private boolean canDecodeBuffer() {
    return !queuedInputBuffers.isEmpty() && availableOutputBufferCount > 0;
  }

  private void releaseInputBufferInternal(I inputBuffer) {
    inputBuffer.clear();
    availableInputBuffers[availableInputBufferCount++] = inputBuffer;
  }

  private void releaseOutputBufferInternal(O outputBuffer) {
    outputBuffer.clear();
    availableOutputBuffers[availableOutputBufferCount++] = outputBuffer;
  }

  /** 创建一个新的输入缓冲区。 */
  protected abstract I createInputBuffer();

  /** 创建一个新的输出缓冲区。 */
  protected abstract O createOutputBuffer();

  /**
   * 为意外的解码错误创建要传播的异常。
   *
   * @param error 意外的解码错误。
   * @return 要传播的异常。
   */
  protected abstract E createUnexpectedDecodeException(Throwable error);

  /**
   * 解码 {@code inputBuffer} 并将解码后的输出存储在 {@code outputBuffer} 中。
   *
   * @param inputBuffer 要解码的缓冲区。
   * @param outputBuffer 用于存储解码数据的输出缓冲区。如果输出缓冲区的 {@link DecoderOutputBuffer#timeUs} 不满足 {@linkplain
   *     #isAtLeastOutputStartTimeUs 输出开始时间} 或者被标记为 {@link DecoderOutputBuffer#shouldBeSkipped}，则不会将其提供给解队列。在这些情况下，输出缓冲区可能未被填充。
   * @param reset 解码前是否必须重置解码器。
   * @return 如果发生错误，则返回解码异常；如果解码成功，则返回 null。
   */
  @Nullable
  protected abstract E decode(I inputBuffer, O outputBuffer, boolean reset);
}