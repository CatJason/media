package androidx.media3.common.audio;

import static androidx.media3.common.util.Assertions.checkArgument;
import static java.lang.Math.min;
import static java.lang.Math.round;

import androidx.annotation.GuardedBy;
import androidx.media3.common.C;
import androidx.media3.common.util.LongArray;
import androidx.media3.common.util.LongArrayQueue;
import androidx.media3.common.util.SpeedProviderUtil;
import androidx.media3.common.util.TimestampConsumer;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.LongConsumer;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * 一个 {@link AudioProcessor}，根据时间戳改变音频样本的播放速度。
 */
// TODO(b/288221200): 考虑在速度为 1 时使处理器处于非活动状态，并在处理器链中跳过它。
@UnstableApi
public final class SpeedChangingAudioProcessor extends BaseAudioProcessor {

  private final Object lock;

  /** 提供每个时间戳对应速度的速度提供器。 */
  private final SpeedProvider speedProvider;

  /**
   * 用于改变速度的 {@link SonicAudioProcessor}。如果不需要改变速度，则直接将输入缓冲区复制到输出缓冲区，
   * 而不使用此处理器。
   */
  private final SynchronizedSonicAudioProcessor sonicAudioProcessor;

  // 队列中相同位置的元素是相关联的。

  @GuardedBy("lock")
  private final LongArrayQueue pendingCallbackInputTimesUs;

  @GuardedBy("lock")
  private final Queue<TimestampConsumer> pendingCallbacks;

  // 数组中相同位置的元素是相关联的。

  @GuardedBy("lock")
  private LongArray inputSegmentStartTimesUs;

  @GuardedBy("lock")
  private LongArray outputSegmentStartTimesUs;

  @GuardedBy("lock")
  private long lastProcessedInputTimeUs;

  @GuardedBy("lock")
  private long lastSpeedAdjustedInputTimeUs;

  @GuardedBy("lock")
  private long lastSpeedAdjustedOutputTimeUs;

  @GuardedBy("lock")
  private long speedAdjustedTimeAsyncInputTimeUs;

  @GuardedBy("lock")
  private float currentSpeed;

  private long bytesRead;

  private boolean endOfStreamQueuedToSonic;

  public SpeedChangingAudioProcessor(SpeedProvider speedProvider) {
    this.speedProvider = speedProvider;
    lock = new Object();
    sonicAudioProcessor = new SynchronizedSonicAudioProcessor(lock);
    pendingCallbackInputTimesUs = new LongArrayQueue();
    pendingCallbacks = new ArrayDeque<>();
    speedAdjustedTimeAsyncInputTimeUs = C.TIME_UNSET;
    resetState();
  }

  @Override
  public long getDurationAfterProcessorApplied(long durationUs) {
    return SpeedProviderUtil.getDurationAfterSpeedProviderApplied(speedProvider, durationUs);
  }

  @Override
  public AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    return sonicAudioProcessor.configure(inputAudioFormat);
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    long currentTimeUs =
        Util.scaleLargeTimestamp(
            /* timestamp= */ bytesRead,
            /* multiplier= */ C.MICROS_PER_SECOND,
            /* divisor= */ (long) inputAudioFormat.sampleRate * inputAudioFormat.bytesPerFrame);
    float newSpeed = speedProvider.getSpeed(currentTimeUs);
    long nextSpeedChangeTimeUs = speedProvider.getNextSpeedChangeTimeUs(currentTimeUs);
    long sampleRateAlignedNextSpeedChangeTimeUs =
        getSampleRateAlignedTimestamp(nextSpeedChangeTimeUs, inputAudioFormat.sampleRate);

    // 如果下一个速度变化时间落在当前样本位置和下一个样本之间，则从下一个样本获取下一个速度和下一个速度变化时间。
    // 如果需要，这将忽略一个或多个样本中间的速度变化。
    if (sampleRateAlignedNextSpeedChangeTimeUs == currentTimeUs) {
      long sampleDuration =
          Util.sampleCountToDurationUs(/* sampleCount= */ 1, inputAudioFormat.sampleRate);
      newSpeed = speedProvider.getSpeed(currentTimeUs + sampleDuration);
      nextSpeedChangeTimeUs =
          speedProvider.getNextSpeedChangeTimeUs(currentTimeUs + sampleDuration);
    }

    updateSpeed(newSpeed, currentTimeUs);

    int inputBufferLimit = inputBuffer.limit();
    int bytesToNextSpeedChange;
    if (nextSpeedChangeTimeUs != C.TIME_UNSET) {
      bytesToNextSpeedChange =
          (int)
              Util.scaleLargeTimestamp(
                  /* timestamp= */ nextSpeedChangeTimeUs - currentTimeUs,
                  /* multiplier= */ (long) inputAudioFormat.sampleRate
                      * inputAudioFormat.bytesPerFrame,
                  /* divisor= */ C.MICROS_PER_SECOND);
      int bytesToNextFrame =
          inputAudioFormat.bytesPerFrame - bytesToNextSpeedChange % inputAudioFormat.bytesPerFrame;
      if (bytesToNextFrame != inputAudioFormat.bytesPerFrame) {
        bytesToNextSpeedChange += bytesToNextFrame;
      }
      // 更新输入缓冲区限制，确保所有处理的样本具有相同的速度。
      inputBuffer.limit(min(inputBufferLimit, inputBuffer.position() + bytesToNextSpeedChange));
    } else {
      bytesToNextSpeedChange = C.LENGTH_UNSET;
    }

    long startPosition = inputBuffer.position();
    if (isUsingSonic()) {
      sonicAudioProcessor.queueInput(inputBuffer);
      if (bytesToNextSpeedChange != C.LENGTH_UNSET
          && (inputBuffer.position() - startPosition) == bytesToNextSpeedChange) {
        sonicAudioProcessor.queueEndOfStream();
        endOfStreamQueuedToSonic = true;
      }
    } else {
      ByteBuffer buffer = replaceOutputBuffer(/* size= */ inputBuffer.remaining());
      if (inputBuffer.hasRemaining()) {
        buffer.put(inputBuffer);
      }
      buffer.flip();
    }
    bytesRead += inputBuffer.position() - startPosition;
    updateLastProcessedInputTime();
    inputBuffer.limit(inputBufferLimit);
  }

  @Override
  protected void onQueueEndOfStream() {
    if (!endOfStreamQueuedToSonic) {
      sonicAudioProcessor.queueEndOfStream();
      endOfStreamQueuedToSonic = true;
    }
  }

  @Override
  public ByteBuffer getOutput() {
    ByteBuffer output = isUsingSonic() ? sonicAudioProcessor.getOutput() : super.getOutput();
    processPendingCallbacks();
    return output;
  }

  @Override
  public boolean isEnded() {
    return super.isEnded() && sonicAudioProcessor.isEnded();
  }

  @Override
  protected void onFlush() {
    resetState();
    sonicAudioProcessor.flush();
  }

  @Override
  protected void onReset() {
    resetState();
    sonicAudioProcessor.reset();
  }

  /**
   * 计算在应用速度变化后，{@code inputTimeUs} 对应的输出时间。
   *
   * <p>一旦处理了足够的音频数据以计算输出时间，将调用 {@linkplain LongConsumer#accept(long) 回调}。
   *
   * <p>如果音频处理器已结束，速度将以音频处理器最后处理的速度输出。
   *
   * <p>连续调用的 {@code inputTimeUs} 必须单调递增。
   *
   * <p>可以从任何线程调用。
   *
   * @param inputTimeUs 输入时间，单位为微秒。
   * @param callback 回调，用于传递输出时间。可能在调用此方法的线程之外的线程上调用。
   */
  public void getSpeedAdjustedTimeAsync(long inputTimeUs, TimestampConsumer callback) {
    synchronized (lock) {
      checkArgument(speedAdjustedTimeAsyncInputTimeUs < inputTimeUs);
      speedAdjustedTimeAsyncInputTimeUs = inputTimeUs;
      if ((inputTimeUs <= lastProcessedInputTimeUs && pendingCallbackInputTimesUs.isEmpty())
          || isEnded()) {
        callback.onTimestamp(calculateSpeedAdjustedTime(inputTimeUs));
        return;
      }
      pendingCallbackInputTimesUs.add(inputTimeUs);
      pendingCallbacks.add(callback);
    }
  }

  /**
   * 返回给定播放持续时间对应的输入媒体持续时间。
   *
   * <p>两个持续时间均从音频处理器的最后一次 {@link #reset()} 或 {@link #flush()} 开始计算。
   *
   * <p>{@code playoutDurationUs} 必须小于最后处理的缓冲区输出时间。
   *
   * @param playoutDurationUs 播放持续时间，单位为微秒。
   * @return 对应的输入持续时间，单位为微秒。
   */
  public long getMediaDurationUs(long playoutDurationUs) {
    synchronized (lock) {
      int floorIndex = outputSegmentStartTimesUs.size() - 1;
      while (floorIndex > 0 && outputSegmentStartTimesUs.get(floorIndex) > playoutDurationUs) {
        floorIndex--;
      }
      long lastSegmentOutputDurationUs =
          playoutDurationUs - outputSegmentStartTimesUs.get(floorIndex);
      long lastSegmentInputDurationUs;
      if (floorIndex == outputSegmentStartTimesUs.size() - 1) {
        lastSegmentInputDurationUs = getMediaDurationUsAtCurrentSpeed(lastSegmentOutputDurationUs);

      } else {
        lastSegmentInputDurationUs =
            round(
                lastSegmentOutputDurationUs
                    * divide(
                    inputSegmentStartTimesUs.get(floorIndex + 1)
                        - inputSegmentStartTimesUs.get(floorIndex),
                    outputSegmentStartTimesUs.get(floorIndex + 1)
                        - outputSegmentStartTimesUs.get(floorIndex)));
      }
      return inputSegmentStartTimesUs.get(floorIndex) + lastSegmentInputDurationUs;
    }
  }

  /**
   * 假设已经处理了足够的音频数据，计算在应用速度变化后，{@code inputTimeUs} 对应的输出时间。
   */
  @SuppressWarnings("GuardedBy") // 所有调用点都已加锁。
  private long calculateSpeedAdjustedTime(long inputTimeUs) {
    int floorIndex = inputSegmentStartTimesUs.size() - 1;
    while (floorIndex > 0 && inputSegmentStartTimesUs.get(floorIndex) > inputTimeUs) {
      floorIndex--;
    }
    long lastSegmentOutputDurationUs;
    if (floorIndex == inputSegmentStartTimesUs.size() - 1) {
      if (lastSpeedAdjustedInputTimeUs < inputSegmentStartTimesUs.get(floorIndex)) {
        lastSpeedAdjustedInputTimeUs = inputSegmentStartTimesUs.get(floorIndex);
        lastSpeedAdjustedOutputTimeUs = outputSegmentStartTimesUs.get(floorIndex);
      }
      long lastSegmentInputDurationUs = inputTimeUs - lastSpeedAdjustedInputTimeUs;
      lastSegmentOutputDurationUs = getPlayoutDurationUsAtCurrentSpeed(lastSegmentInputDurationUs);
    } else {
      long lastSegmentInputDurationUs = inputTimeUs - lastSpeedAdjustedInputTimeUs;
      lastSegmentOutputDurationUs =
          round(
              lastSegmentInputDurationUs
                  * divide(
                  outputSegmentStartTimesUs.get(floorIndex + 1)
                      - outputSegmentStartTimesUs.get(floorIndex),
                  inputSegmentStartTimesUs.get(floorIndex + 1)
                      - inputSegmentStartTimesUs.get(floorIndex)));
    }
    lastSpeedAdjustedInputTimeUs = inputTimeUs;
    lastSpeedAdjustedOutputTimeUs += lastSegmentOutputDurationUs;
    return lastSpeedAdjustedOutputTimeUs;
  }

  private static double divide(long dividend, long divisor) {
    return ((double) dividend) / divisor;
  }

  private void processPendingCallbacks() {
    synchronized (lock) {
      while (!pendingCallbacks.isEmpty()
          && (pendingCallbackInputTimesUs.element() <= lastProcessedInputTimeUs || isEnded())) {
        pendingCallbacks
            .remove()
            .onTimestamp(calculateSpeedAdjustedTime(pendingCallbackInputTimesUs.remove()));
      }
    }
  }

  private void updateSpeed(float newSpeed, long timeUs) {
    synchronized (lock) {
      if (newSpeed != currentSpeed) {
        updateSpeedChangeArrays(timeUs);
        currentSpeed = newSpeed;
        if (isUsingSonic()) {
          sonicAudioProcessor.setSpeed(newSpeed);
          sonicAudioProcessor.setPitch(newSpeed);
        }
        // 使 SonicAudioProcessor 和基类中先前创建的缓冲区无效。
        sonicAudioProcessor.flush();
        endOfStreamQueuedToSonic = false;
        super.getOutput();
      }
    }
  }

  @SuppressWarnings("GuardedBy") // 所有调用点都已加锁。
  private void updateSpeedChangeArrays(long currentSpeedChangeInputTimeUs) {
    long lastSpeedChangeOutputTimeUs =
        outputSegmentStartTimesUs.get(outputSegmentStartTimesUs.size() - 1);
    long lastSpeedChangeInputTimeUs =
        inputSegmentStartTimesUs.get(inputSegmentStartTimesUs.size() - 1);
    long lastSpeedSegmentMediaDurationUs =
        currentSpeedChangeInputTimeUs - lastSpeedChangeInputTimeUs;
    inputSegmentStartTimesUs.add(currentSpeedChangeInputTimeUs);
    outputSegmentStartTimesUs.add(
        lastSpeedChangeOutputTimeUs
            + getPlayoutDurationUsAtCurrentSpeed(lastSpeedSegmentMediaDurationUs));
  }

  private long getPlayoutDurationUsAtCurrentSpeed(long mediaDurationUs) {
    return isUsingSonic()
        ? sonicAudioProcessor.getPlayoutDuration(mediaDurationUs)
        : mediaDurationUs;
  }

  private long getMediaDurationUsAtCurrentSpeed(long playoutDurationUs) {
    return isUsingSonic()
        ? sonicAudioProcessor.getMediaDuration(playoutDurationUs)
        : playoutDurationUs;
  }

  private void updateLastProcessedInputTime() {
    synchronized (lock) {
      if (isUsingSonic()) {
        // TODO - b/320242819: 研究是否可以使用 bytesRead 而不是 sonicAudioProcessor.getProcessedInputBytes()。
        long currentProcessedInputDurationUs =
            Util.scaleLargeTimestamp(
                /* timestamp= */ sonicAudioProcessor.getProcessedInputBytes(),
                /* multiplier= */ C.MICROS_PER_SECOND,
                /* divisor= */ (long) inputAudioFormat.sampleRate * inputAudioFormat.bytesPerFrame);
        lastProcessedInputTimeUs =
            inputSegmentStartTimesUs.get(inputSegmentStartTimesUs.size() - 1)
                + currentProcessedInputDurationUs;
      } else {
        lastProcessedInputTimeUs =
            Util.scaleLargeTimestamp(
                /* timestamp= */ bytesRead,
                /* multiplier= */ C.MICROS_PER_SECOND,
                /* divisor= */ (long) inputAudioFormat.sampleRate * inputAudioFormat.bytesPerFrame);
      }
    }
  }

  private boolean isUsingSonic() {
    synchronized (lock) {
      return currentSpeed != 1f;
    }
  }

  @EnsuresNonNull({"inputSegmentStartTimesUs", "outputSegmentStartTimesUs"})
  @RequiresNonNull("lock")
  private void resetState(@UnknownInitialization SpeedChangingAudioProcessor this) {
    synchronized (lock) {
      inputSegmentStartTimesUs = new LongArray();
      outputSegmentStartTimesUs = new LongArray();
      inputSegmentStartTimesUs.add(0);
      outputSegmentStartTimesUs.add(0);
      lastProcessedInputTimeUs = 0;
      lastSpeedAdjustedInputTimeUs = 0;
      lastSpeedAdjustedOutputTimeUs = 0;
      currentSpeed = 1f;
    }

    bytesRead = 0;
    endOfStreamQueuedToSonic = false;
    // TODO: b/339842724 - 理想情况下，这里还应该重置 speedAdjustedTimeAsyncInputTimeUs 并
    //  清除 pendingCallbacks 和 pendingCallbacksInputTimes。目前无法这样做，因为一些客户端在
    //  此音频处理器刷新之前通过 getSpeedAdjustedTimeAsync 注册了回调。
  }

  /**
   * 返回与 {@code timestampUs} 最接近的、由 {@code sampleRate} 定义的样本的时间戳（微秒），
   * 使用 {@link Util#scaleLargeTimestamp} 中指定的舍入模式。
   */
  private static long getSampleRateAlignedTimestamp(long timestampUs, int sampleRate) {
    long exactSamplePosition =
        Util.scaleLargeTimestamp(timestampUs, sampleRate, C.MICROS_PER_SECOND);
    return Util.scaleLargeTimestamp(exactSamplePosition, C.MICROS_PER_SECOND, sampleRate);
  }
}