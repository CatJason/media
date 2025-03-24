package androidx.media3.common.audio;

import static androidx.media3.common.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * 一个使用 Sonic 库来修改音频速度/音高/采样率的 {@link AudioProcessor}。
 */
@UnstableApi
public class SonicAudioProcessor implements AudioProcessor {

  /** 表示输出采样率应与输入采样率相同。 */
  public static final int SAMPLE_RATE_NO_CHANGE = -1;

  /** 用于判断两个音高/速度因子之间差异是否可忽略的阈值。 */
  private static final float CLOSE_THRESHOLD = 0.0001f;

  /**
   * 用于计算时长缩放的最小输出字节数。如果输出字节数小于此值，则使用当前播放速度来计算时长缩放，而不是使用输入和输出字节数。
   */
  private static final int MIN_BYTES_FOR_DURATION_SCALING_CALCULATION = 1024;

  private int pendingOutputSampleRate;
  private float speed;
  private float pitch;

  private AudioFormat pendingInputAudioFormat;
  private AudioFormat pendingOutputAudioFormat;
  private AudioFormat inputAudioFormat;
  private AudioFormat outputAudioFormat;

  private boolean pendingSonicRecreation;
  @Nullable private Sonic sonic;
  private ByteBuffer buffer;
  private ShortBuffer shortBuffer;
  private ByteBuffer outputBuffer;
  private long inputBytes;
  private long outputBytes;
  private boolean inputEnded;

  /** 创建一个新的 Sonic 音频处理器。 */
  public SonicAudioProcessor() {
    speed = 1f;
    pitch = 1f;
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
    buffer = EMPTY_BUFFER;
    shortBuffer = buffer.asShortBuffer();
    outputBuffer = EMPTY_BUFFER;
    pendingOutputSampleRate = SAMPLE_RATE_NO_CHANGE;
  }
  /**
   * 设置目标播放速度。此方法只能在处理器中的数据被完全处理后调用。调用后，{@link #isActive()} 的返回值可能会发生变化，
   * 并且在加入更多数据之前必须对处理器进行 {@link #flush() 刷新}。
   *
   * @param speed 目标播放速度的加速因子。
   */
  public final void setSpeed(float speed) {
    if (this.speed != speed) {
      this.speed = speed;
      pendingSonicRecreation = true;
    }
  }

  /**
   * 设置目标播放音高。此方法只能在处理器中的数据被完全处理后调用。调用后，{@link #isActive()} 的返回值可能会发生变化，
   * 并且在加入更多数据之前必须对处理器进行 {@link #flush() 刷新}。
   *
   * @param pitch 目标音高。
   */
  public final void setPitch(float pitch) {
    if (this.pitch != pitch) {
      this.pitch = pitch;
      pendingSonicRecreation = true;
    }
  }
  /**
   * 设置输出音频的采样率，单位为赫兹（Hz）。传递 {@link #SAMPLE_RATE_NO_CHANGE} 表示输出音频的采样率与输入相同。
   * 调用此方法后，调用 {@link #configure(AudioFormat)} 以使用新的采样率配置处理器。
   *
   * @param sampleRateHz 输出音频的采样率，单位为赫兹（Hz）。
   * @see #configure(AudioFormat)
   */
  public final void setOutputSampleRateHz(int sampleRateHz) {
    pendingOutputSampleRate = sampleRateHz;
  }

  /**
   * 返回与指定播放持续时间对应的媒体持续时间，考虑了速度调整。
   *
   * <p>此方法执行的缩放将使用音频处理器自上次刷新以来实际实现的平均播放速度。这可能与目标播放速度略有不同。
   *
   * @param playoutDuration 要缩放的播放持续时间。
   * @return 对应的媒体持续时间，与 {@code duration} 使用相同的单位。
   */
  public final long getMediaDuration(long playoutDuration) {
    if (outputBytes >= MIN_BYTES_FOR_DURATION_SCALING_CALCULATION) {
      long processedInputBytes = inputBytes - checkNotNull(sonic).getPendingInputBytes();
      return outputAudioFormat.sampleRate == inputAudioFormat.sampleRate
          ? Util.scaleLargeTimestamp(playoutDuration, processedInputBytes, outputBytes)
          : Util.scaleLargeTimestamp(
              playoutDuration,
              processedInputBytes * outputAudioFormat.sampleRate,
              outputBytes * inputAudioFormat.sampleRate);
    } else {
      return (long) ((double) speed * playoutDuration);
    }
  }
  /**
   * 返回与指定媒体持续时间对应的播放持续时间，考虑了速度调整。
   *
   * <p>此方法执行的缩放将使用音频处理器自上次刷新以来实际实现的平均播放速度。这可能与目标播放速度略有不同。
   *
   * @param mediaDuration 要缩放的媒体持续时间。
   * @return 对应的播放持续时间，与 {@code mediaDuration} 使用相同的单位。
   */
  public final long getPlayoutDuration(long mediaDuration) {
    if (outputBytes >= MIN_BYTES_FOR_DURATION_SCALING_CALCULATION) {
      long processedInputBytes = inputBytes - checkNotNull(sonic).getPendingInputBytes();
      return outputAudioFormat.sampleRate == inputAudioFormat.sampleRate
          ? Util.scaleLargeTimestamp(mediaDuration, outputBytes, processedInputBytes)
          : Util.scaleLargeTimestamp(
              mediaDuration,
              outputBytes * inputAudioFormat.sampleRate,
              processedInputBytes * outputAudioFormat.sampleRate);
    } else {
      return (long) (mediaDuration / (double) speed);
    }
  }

  /** 返回自上次刷新或重置以来已处理的字节数。 */
  public final long getProcessedInputBytes() {
    return inputBytes - checkNotNull(sonic).getPendingInputBytes();
  }

  @Override
  public long getDurationAfterProcessorApplied(long durationUs) {
    return getPlayoutDuration(durationUs);
  }

  @Override
  public final AudioFormat configure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
    int outputSampleRateHz =
        pendingOutputSampleRate == SAMPLE_RATE_NO_CHANGE
            ? inputAudioFormat.sampleRate
            : pendingOutputSampleRate;
    pendingInputAudioFormat = inputAudioFormat;
    pendingOutputAudioFormat =
        new AudioFormat(outputSampleRateHz, inputAudioFormat.channelCount, C.ENCODING_PCM_16BIT);
    pendingSonicRecreation = true;
    return pendingOutputAudioFormat;
  }

  @Override
  public final boolean isActive() {
    return pendingOutputAudioFormat.sampleRate != Format.NO_VALUE
        && (Math.abs(speed - 1f) >= CLOSE_THRESHOLD
            || Math.abs(pitch - 1f) >= CLOSE_THRESHOLD
            || pendingOutputAudioFormat.sampleRate != pendingInputAudioFormat.sampleRate);
  }

  @Override
  public final void queueInput(ByteBuffer inputBuffer) {
    if (!inputBuffer.hasRemaining()) {
      return;
    }
    Sonic sonic = checkNotNull(this.sonic);
    ShortBuffer shortBuffer = inputBuffer.asShortBuffer();
    int inputSize = inputBuffer.remaining();
    inputBytes += inputSize;
    sonic.queueInput(shortBuffer);
    inputBuffer.position(inputBuffer.position() + inputSize);
  }

  @Override
  public final void queueEndOfStream() {
    // TODO(内部 b/174554082)：在此处和 getOutput 中确保 sonic 不为 null。
    if (sonic != null) {
      sonic.queueEndOfStream();
    }
    inputEnded = true;
  }

  @Override
  public final ByteBuffer getOutput() {
    @Nullable Sonic sonic = this.sonic;
    if (sonic != null) {
      int outputSize = sonic.getOutputSize();
      if (outputSize > 0) {
        if (buffer.capacity() < outputSize) {
          buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder());
          shortBuffer = buffer.asShortBuffer();
        } else {
          buffer.clear();
          shortBuffer.clear();
        }
        sonic.getOutput(shortBuffer);
        outputBytes += outputSize;
        buffer.limit(outputSize);
        outputBuffer = buffer;
      }
    }
    ByteBuffer outputBuffer = this.outputBuffer;
    this.outputBuffer = EMPTY_BUFFER;
    return outputBuffer;
  }

  @Override
  public final boolean isEnded() {
    return inputEnded && (sonic == null || sonic.getOutputSize() == 0);
  }

  @Override
  public final void flush() {
    if (isActive()) {
      inputAudioFormat = pendingInputAudioFormat;
      outputAudioFormat = pendingOutputAudioFormat;
      if (pendingSonicRecreation) {
        sonic =
            new Sonic(
                inputAudioFormat.sampleRate,
                inputAudioFormat.channelCount,
                speed,
                pitch,
                outputAudioFormat.sampleRate);
      } else if (sonic != null) {
        sonic.flush();
      }
    }
    outputBuffer = EMPTY_BUFFER;
    inputBytes = 0;
    outputBytes = 0;
    inputEnded = false;
  }

  @Override
  public final void reset() {
    speed = 1f;
    pitch = 1f;
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
    buffer = EMPTY_BUFFER;
    shortBuffer = buffer.asShortBuffer();
    outputBuffer = EMPTY_BUFFER;
    pendingOutputSampleRate = SAMPLE_RATE_NO_CHANGE;
    pendingSonicRecreation = false;
    sonic = null;
    inputBytes = 0;
    outputBytes = 0;
    inputEnded = false;
  }
}
