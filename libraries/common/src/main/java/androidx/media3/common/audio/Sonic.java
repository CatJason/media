package androidx.media3.common.audio;

import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.Math.min;

import java.nio.ShortBuffer;
import java.util.Arrays;

/**
 * Sonic 音频流处理器，用于时间/音高拉伸。
 *
 * <p>基于 https://github.com/waywardgeek/sonic。
 */
/* package */ final class Sonic {

  private static final int MINIMUM_PITCH = 65;
  private static final int MAXIMUM_PITCH = 400;
  private static final int AMDF_FREQUENCY = 4000;
  private static final int BYTES_PER_SAMPLE = 2;

  private final int inputSampleRateHz;
  private final int channelCount;
  private final float speed;
  private final float pitch;
  private final float rate;
  private final int minPeriod;
  private final int maxPeriod;
  private final int maxRequiredFrameCount;
  private final short[] downSampleBuffer;

  private short[] inputBuffer;
  private int inputFrameCount;
  private short[] outputBuffer;
  private int outputFrameCount;
  private short[] pitchBuffer;
  private int pitchFrameCount;
  private int oldRatePosition;
  private int newRatePosition;

  /**
   * 等待从 {@link #inputBuffer} 直接复制到 {@link #outputBuffer} 的帧数。
   *
   * <p>该字段仅与 {@link #changeSpeed(double)} 中的时间拉伸或音高变换相关，
   * 特别是当需要复制到 {@link #outputBuffer} 的帧数超过 {@link #inputBuffer} 中可用帧数时，Sonic 必须等待直到下一个缓冲区（或 EOS）被加入队列。
   */
  private int remainingInputToCopyFrameCount;

  private int prevPeriod;
  private int prevMinDiff;
  private int minDiff;
  private int maxDiff;
  private double accumulatedSpeedAdjustmentError;

  /**
   * 创建一个新的 Sonic 音频流处理器。
   *
   * @param inputSampleRateHz 输入音频的采样率，单位为赫兹（Hz）。
   * @param channelCount 输入音频的通道数。
   * @param speed 输出音频的加速因子。
   * @param pitch 输出音频的音高因子。
   * @param outputSampleRateHz 输出音频的采样率，单位为赫兹（Hz）。
   */
  public Sonic(
      int inputSampleRateHz, int channelCount, float speed, float pitch, int outputSampleRateHz) {
    this.inputSampleRateHz = inputSampleRateHz;
    this.channelCount = channelCount;
    this.speed = speed;
    this.pitch = pitch;
    rate = (float) inputSampleRateHz / outputSampleRateHz;
    minPeriod = inputSampleRateHz / MAXIMUM_PITCH;
    maxPeriod = inputSampleRateHz / MINIMUM_PITCH;
    maxRequiredFrameCount = 2 * maxPeriod;
    downSampleBuffer = new short[maxRequiredFrameCount];
    inputBuffer = new short[maxRequiredFrameCount * channelCount];
    outputBuffer = new short[maxRequiredFrameCount * channelCount];
    pitchBuffer = new short[maxRequiredFrameCount * channelCount];
  }

  /**
   * 返回已输入但尚未处理的字节数，直到提供更多输入数据时才会处理。
   */
  public int getPendingInputBytes() {
    return inputFrameCount * channelCount * BYTES_PER_SAMPLE;
  }

  /**
   * 将 {@code buffer} 中剩余的数据加入队列，并根据消耗的字节数推进其位置。
   *
   * @param buffer 一个 {@link ShortBuffer}，包含其位置和限制之间的输入数据。
   */
  public void queueInput(ShortBuffer buffer) {
    int framesToWrite = buffer.remaining() / channelCount;
    int bytesToWrite = framesToWrite * channelCount * 2;
    inputBuffer = ensureSpaceForAdditionalFrames(inputBuffer, inputFrameCount, framesToWrite);
    buffer.get(inputBuffer, inputFrameCount * channelCount, bytesToWrite / 2);
    inputFrameCount += framesToWrite;
    processStreamInput();
  }

  /**
   * 获取可用的输出，并将其写入 {@code buffer} 的起始位置。缓冲区的位置将根据写入的字节数推进。
   *
   * @param buffer 一个 {@link ShortBuffer}，用于写入输出。
   */
  public void getOutput(ShortBuffer buffer) {
    int framesToRead = min(buffer.remaining() / channelCount, outputFrameCount);
    buffer.put(outputBuffer, 0, framesToRead * channelCount);
    outputFrameCount -= framesToRead;
    System.arraycopy(
        outputBuffer,
        framesToRead * channelCount,
        outputBuffer,
        0,
        outputFrameCount * channelCount);
  }

  /**
   * 强制使用已排队的任何数据生成输出。不会在输出中添加额外的延迟，但在单词中间刷新可能会引入失真。
   */
  public void queueEndOfStream() {
    int remainingFrameCount = inputFrameCount;
    double s = speed / pitch;
    double r = rate * pitch;

    // 如果有帧可以直接复制到输出缓冲区，则不应将其计为“输入帧”，因为 Sonic 不会对它们进行任何处理。
    int adjustedRemainingFrames = remainingFrameCount - remainingInputToCopyFrameCount;

    // 我们将 remainingInputToCopyFrameCount 中的帧数直接添加到输出中。
    // 否则，expectedOutputFrames 将不准确，并导致 Sonic 输出错误的帧数。
    int expectedOutputFrames =
        outputFrameCount
            + (int)
            ((adjustedRemainingFrames / s
                + remainingInputToCopyFrameCount
                + accumulatedSpeedAdjustmentError
                + pitchFrameCount)
                / r
                + 0.5);
    accumulatedSpeedAdjustmentError = 0;

    // 添加足够的静音以刷新输入和音高缓冲区。
    inputBuffer =
        ensureSpaceForAdditionalFrames(
            inputBuffer, inputFrameCount, remainingFrameCount + 2 * maxRequiredFrameCount);
    for (int xSample = 0; xSample < 2 * maxRequiredFrameCount * channelCount; xSample++) {
      inputBuffer[remainingFrameCount * channelCount + xSample] = 0;
    }
    inputFrameCount += 2 * maxRequiredFrameCount;
    processStreamInput();
    // 丢弃由于添加静音而生成的额外帧。
    if (outputFrameCount > expectedOutputFrames) {
      outputFrameCount = expectedOutputFrames;
    }
    // 清空输入和音高缓冲区。
    inputFrameCount = 0;
    remainingInputToCopyFrameCount = 0;
    pitchFrameCount = 0;
  }

  /** 清除状态，准备接收新的输入缓冲区流。 */
  public void flush() {
    inputFrameCount = 0;
    outputFrameCount = 0;
    pitchFrameCount = 0;
    oldRatePosition = 0;
    newRatePosition = 0;
    remainingInputToCopyFrameCount = 0;
    prevPeriod = 0;
    prevMinDiff = 0;
    minDiff = 0;
    maxDiff = 0;
    accumulatedSpeedAdjustmentError = 0;
  }

  /** 返回可以通过 {@link #getOutput(ShortBuffer)} 读取的输出大小，单位为字节。 */
  public int getOutputSize() {
    return outputFrameCount * channelCount * BYTES_PER_SAMPLE;
  }

// 内部方法。

  /**
   * 返回 {@code buffer} 或其副本，确保返回的缓冲区中有足够的空间来存储 {@code newFrameCount} 个额外帧。
   *
   * @param buffer 缓冲区。
   * @param frameCount 缓冲区中已有的帧数。
   * @param additionalFrameCount 需要存储在缓冲区中的额外帧数。
   * @return 具有足够空间存储额外帧的缓冲区。
   */
  private short[] ensureSpaceForAdditionalFrames(
      short[] buffer, int frameCount, int additionalFrameCount) {
    int currentCapacityFrames = buffer.length / channelCount;
    if (frameCount + additionalFrameCount <= currentCapacityFrames) {
      return buffer;
    } else {
      int newCapacityFrames = 3 * currentCapacityFrames / 2 + additionalFrameCount;
      return Arrays.copyOf(buffer, newCapacityFrames * channelCount);
    }
  }

  private void removeProcessedInputFrames(int positionFrames) {
    int remainingFrames = inputFrameCount - positionFrames;
    System.arraycopy(
        inputBuffer, positionFrames * channelCount, inputBuffer, 0, remainingFrames * channelCount);
    inputFrameCount = remainingFrames;
  }

  private void copyToOutput(short[] samples, int positionFrames, int frameCount) {
    outputBuffer = ensureSpaceForAdditionalFrames(outputBuffer, outputFrameCount, frameCount);
    System.arraycopy(
        samples,
        positionFrames * channelCount,
        outputBuffer,
        outputFrameCount * channelCount,
        frameCount * channelCount);
    outputFrameCount += frameCount;
  }

  private int copyInputToOutput(int positionFrames) {
    int frameCount = min(maxRequiredFrameCount, remainingInputToCopyFrameCount);
    copyToOutput(inputBuffer, positionFrames, frameCount);
    remainingInputToCopyFrameCount -= frameCount;
    return frameCount;
  }

  private void downSampleInput(short[] samples, int position, int skip) {
    // 如果 skip 大于 1，则将 skip 个样本取平均值并写入降采样缓冲区。
    // 如果 channelCount 大于 1，则在降采样时混合通道。
    int frameCount = maxRequiredFrameCount / skip;
    int samplesPerValue = channelCount * skip;
    position *= channelCount;
    for (int i = 0; i < frameCount; i++) {
      int value = 0;
      for (int j = 0; j < samplesPerValue; j++) {
        value += samples[position + i * samplesPerValue + j];
      }
      value /= samplesPerValue;
      downSampleBuffer[i] = (short) value;
    }
  }

  private int findPitchPeriodInRange(short[] samples, int position, int minPeriod, int maxPeriod) {
    // 在给定范围内找到最佳频率匹配，并考虑样本跳过多重性。目前仅查找第一个通道的音高。
    int bestPeriod = 0;
    int worstPeriod = 255;
    int minDiff = 1;
    int maxDiff = 0;
    position *= channelCount;
    for (int period = minPeriod; period <= maxPeriod; period++) {
      int diff = 0;
      for (int i = 0; i < period; i++) {
        short sVal = samples[position + i];
        short pVal = samples[position + period + i];
        diff += Math.abs(sVal - pVal);
      }
      // 注意，由于我们跳过了样本，diff 的最大值将小于 256。因此，diff 是一个 24 位数，
      // 我们可以安全地乘以 numSamples 而不会溢出。
      if (diff * bestPeriod < minDiff * period) {
        minDiff = diff;
        bestPeriod = period;
      }
      if (diff * worstPeriod > maxDiff * period) {
        maxDiff = diff;
        worstPeriod = period;
      }
    }
    this.minDiff = minDiff / bestPeriod;
    this.maxDiff = maxDiff / worstPeriod;
    return bestPeriod;
  }

  /**
   * 返回先前的音高周期估计是否更优，这可能发生在有声词突然结束时。
   */
  private boolean previousPeriodBetter(int minDiff, int maxDiff) {
    if (minDiff == 0 || prevPeriod == 0) {
      return false;
    }
    if (maxDiff > minDiff * 3) {
      // 当前周期匹配合理。
      return false;
    }
    if (minDiff * 2 <= prevMinDiff * 3) {
      // 当前周期的失配并不比之前大很多。
      return false;
    }
    return true;
  }

  private int findPitchPeriod(short[] samples, int position) {
    // 查找音高周期。这是一个关键步骤，我们可能需要尝试多种方法来获得一个好的结果。
    // 此版本使用 AMDF（平均幅度差函数）。为了提高速度，我们首先通过整数因子降采样到 11 kHz 范围内，
    // 然后在更窄的频率范围内再次查找，但不再降采样。
    int period;
    int retPeriod;
    int skip = inputSampleRateHz > AMDF_FREQUENCY ? inputSampleRateHz / AMDF_FREQUENCY : 1;
    if (channelCount == 1 && skip == 1) {
      period = findPitchPeriodInRange(samples, position, minPeriod, maxPeriod);
    } else {
      downSampleInput(samples, position, skip);
      period = findPitchPeriodInRange(downSampleBuffer, 0, minPeriod / skip, maxPeriod / skip);
      if (skip != 1) {
        period *= skip;
        int minP = period - (skip * 4);
        int maxP = period + (skip * 4);
        if (minP < minPeriod) {
          minP = minPeriod;
        }
        if (maxP > maxPeriod) {
          maxP = maxPeriod;
        }
        if (channelCount == 1) {
          period = findPitchPeriodInRange(samples, position, minP, maxP);
        } else {
          downSampleInput(samples, position, 1);
          period = findPitchPeriodInRange(downSampleBuffer, 0, minP, maxP);
        }
      }
    }
    if (previousPeriodBetter(minDiff, maxDiff)) {
      retPeriod = prevPeriod;
    } else {
      retPeriod = period;
    }
    prevMinDiff = minDiff;
    prevPeriod = period;
    return retPeriod;
  }

  private void moveNewSamplesToPitchBuffer(int originalOutputFrameCount) {
    int frameCount = outputFrameCount - originalOutputFrameCount;
    pitchBuffer = ensureSpaceForAdditionalFrames(pitchBuffer, pitchFrameCount, frameCount);
    System.arraycopy(
        outputBuffer,
        originalOutputFrameCount * channelCount,
        pitchBuffer,
        pitchFrameCount * channelCount,
        frameCount * channelCount);
    outputFrameCount = originalOutputFrameCount;
    pitchFrameCount += frameCount;
  }

  private void removePitchFrames(int frameCount) {
    if (frameCount == 0) {
      return;
    }
    System.arraycopy(
        pitchBuffer,
        frameCount * channelCount,
        pitchBuffer,
        0,
        (pitchFrameCount - frameCount) * channelCount);
    pitchFrameCount -= frameCount;
  }

  private short interpolate(short[] in, int inPos, long oldSampleRate, long newSampleRate) {
    short left = in[inPos];
    short right = in[inPos + channelCount];
    long position = newRatePosition * oldSampleRate;
    long leftPosition = oldRatePosition * newSampleRate;
    long rightPosition = (oldRatePosition + 1) * newSampleRate;
    long ratio = rightPosition - position;
    long width = rightPosition - leftPosition;
    return (short) ((ratio * left + (width - ratio) * right) / width);
  }

  private void adjustRate(float rate, int originalOutputFrameCount) {
    if (outputFrameCount == originalOutputFrameCount) {
      return;
    }

    // 使用 long 类型以避免 int 乘法溢出。newSampleRate 和 oldSampleRate 的实际值应始终在 int 范围内。
    long newSampleRate = (long) (inputSampleRateHz / rate);
    long oldSampleRate = inputSampleRateHz;
    // 调整这些值以简化整数运算。
    while (newSampleRate != 0
        && oldSampleRate != 0
        && newSampleRate % 2 == 0
        && oldSampleRate % 2 == 0) {
      newSampleRate /= 2;
      oldSampleRate /= 2;
    }
    moveNewSamplesToPitchBuffer(originalOutputFrameCount);
    // 在缓冲区中至少保留一个音高样本。
    for (int position = 0; position < pitchFrameCount - 1; position++) {
      // 转换为 long 以避免溢出。
      while ((oldRatePosition + 1) * newSampleRate > newRatePosition * oldSampleRate) {
        outputBuffer =
            ensureSpaceForAdditionalFrames(
                outputBuffer, outputFrameCount, /* additionalFrameCount= */ 1);
        for (int i = 0; i < channelCount; i++) {
          outputBuffer[outputFrameCount * channelCount + i] =
              interpolate(pitchBuffer, position * channelCount + i, oldSampleRate, newSampleRate);
        }
        newRatePosition++;
        outputFrameCount++;
      }
      oldRatePosition++;
      if (oldRatePosition == oldSampleRate) {
        oldRatePosition = 0;
        checkState(newRatePosition == newSampleRate);
        newRatePosition = 0;
      }
    }
    removePitchFrames(pitchFrameCount - 1);
  }

  private int skipPitchPeriod(short[] samples, int position, double speed, int period) {
    // 跳过一个音高周期，并将 period/speed 个样本复制到输出中。
    int newFrameCount;
    if (speed >= 2.0f) {
      double expectedFrameCount = period / (speed - 1.0) + accumulatedSpeedAdjustmentError;
      newFrameCount = (int) Math.round(expectedFrameCount);
      accumulatedSpeedAdjustmentError = expectedFrameCount - newFrameCount;
    } else {
      newFrameCount = period;
      double expectedInputToCopy =
          period * (2.0f - speed) / (speed - 1.0f) + accumulatedSpeedAdjustmentError;
      remainingInputToCopyFrameCount = (int) Math.round(expectedInputToCopy);
      accumulatedSpeedAdjustmentError = expectedInputToCopy - remainingInputToCopyFrameCount;
    }
    outputBuffer = ensureSpaceForAdditionalFrames(outputBuffer, outputFrameCount, newFrameCount);
    overlapAdd(
        newFrameCount,
        channelCount,
        outputBuffer,
        outputFrameCount,
        samples,
        position,
        samples,
        position + period);
    outputFrameCount += newFrameCount;
    return newFrameCount;
  }

  private int insertPitchPeriod(short[] samples, int position, double speed, int period) {
    // 插入一个音高周期，并确定需要直接复制的输入数据量。
    int newFrameCount;
    if (speed < 0.5f) {
      double expectedFrameCount = period * speed / (1.0f - speed) + accumulatedSpeedAdjustmentError;
      newFrameCount = (int) Math.round(expectedFrameCount);
      accumulatedSpeedAdjustmentError = expectedFrameCount - newFrameCount;
    } else {
      newFrameCount = period;
      double expectedInputToCopy =
          period * (2.0f * speed - 1.0f) / (1.0f - speed) + accumulatedSpeedAdjustmentError;
      remainingInputToCopyFrameCount = (int) Math.round(expectedInputToCopy);
      accumulatedSpeedAdjustmentError = expectedInputToCopy - remainingInputToCopyFrameCount;
    }
    outputBuffer =
        ensureSpaceForAdditionalFrames(outputBuffer, outputFrameCount, period + newFrameCount);
    System.arraycopy(
        samples,
        position * channelCount,
        outputBuffer,
        outputFrameCount * channelCount,
        period * channelCount);
    overlapAdd(
        newFrameCount,
        channelCount,
        outputBuffer,
        outputFrameCount + period,
        samples,
        position + period,
        samples,
        position);
    outputFrameCount += period + newFrameCount;
    return newFrameCount;
  }

  private void changeSpeed(double speed) {
    if (inputFrameCount < maxRequiredFrameCount) {
      return;
    }
    int frameCount = inputFrameCount;
    int positionFrames = 0;
    do {
      if (remainingInputToCopyFrameCount > 0) {
        positionFrames += copyInputToOutput(positionFrames);
      } else {
        int period = findPitchPeriod(inputBuffer, positionFrames);
        if (speed > 1.0) {
          positionFrames += period + skipPitchPeriod(inputBuffer, positionFrames, speed, period);
        } else {
          positionFrames += insertPitchPeriod(inputBuffer, positionFrames, speed, period);
        }
      }
    } while (positionFrames + maxRequiredFrameCount <= frameCount);
    removeProcessedInputFrames(positionFrames);
  }

  private void processStreamInput() {
    // 对输入缓冲区中已缓冲的尽可能多的音高周期进行重采样。
    int originalOutputFrameCount = outputFrameCount;
    double s = speed / pitch;
    float r = rate * pitch;
    if (s > 1.00001 || s < 0.99999) {
      changeSpeed(s);
    } else {
      copyToOutput(inputBuffer, 0, inputFrameCount);
      inputFrameCount = 0;
    }
    if (r != 1.0f) {
      adjustRate(r, originalOutputFrameCount);
    }
  }

  private static void overlapAdd(
      int frameCount,
      int channelCount,
      short[] out,
      int outPosition,
      short[] rampDown,
      int rampDownPosition,
      short[] rampUp,
      int rampUpPosition) {
    for (int i = 0; i < channelCount; i++) {
      int o = outPosition * channelCount + i;
      int u = rampUpPosition * channelCount + i;
      int d = rampDownPosition * channelCount + i;
      for (int t = 0; t < frameCount; t++) {
        out[o] = (short) ((rampDown[d] * (frameCount - t) + rampUp[u] * t) / frameCount);
        o += channelCount;
        d += channelCount;
        u += channelCount;
      }
    }
  }
}
