package androidx.media3.common.audio;

import static androidx.media3.common.util.Util.constrainValue;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;

/** 用于混合音频缓冲区的工具类。 */
@UnstableApi
public final class AudioMixingUtil {

  // Float PCM 样本在 [-1.0, 1.0] 范围内以零为中心。
  private static final float FLOAT_PCM_MIN_VALUE = -1.0f;
  private static final float FLOAT_PCM_MAX_VALUE = 1.0f;

  /**
   * 检查给定的音频格式是否可以被混合。
   *
   * @param audioFormat 要检查的音频格式。
   * @return 如果音频格式可以被混合，则返回 {@code true}，否则返回 {@code false}。
   */
  public static boolean canMix(AudioFormat audioFormat) {
    if (audioFormat.sampleRate == Format.NO_VALUE) {
      return false;
    }
    if (audioFormat.channelCount == Format.NO_VALUE) {
      return false;
    }
    return audioFormat.encoding == C.ENCODING_PCM_16BIT
        || audioFormat.encoding == C.ENCODING_PCM_FLOAT;
  }

  /**
   * 检查输入音频格式和输出音频格式是否可以被混合。
   *
   * @param inputAudioFormat 输入音频格式。
   * @param outputAudioFormat 输出音频格式。
   * @return 如果两种音频格式可以被混合，则返回 {@code true}，否则返回 {@code false}。
   */
  public static boolean canMix(AudioFormat inputAudioFormat, AudioFormat outputAudioFormat) {
    if (inputAudioFormat.sampleRate != outputAudioFormat.sampleRate) {
      return false;
    }
    if (!canMix(inputAudioFormat)) {
      return false;
    }
    if (!canMix(outputAudioFormat)) {
      return false;
    }
    return true;
  }

  /**
   * 将输入缓冲区中的音频混合到混合缓冲区中。
   *
   * <p>{@link #canMix(AudioFormat, AudioFormat)} 必须对这两种格式返回 {@code true}。
   *
   * @param inputBuffer 输入音频 {@link ByteBuffer}，其位置会根据读取和混合的字节数前进。
   * @param inputAudioFormat {@code inputBuffer} 的 {@link AudioFormat}。
   * @param mixingBuffer 混合音频 {@link ByteBuffer}，其位置会根据写入的字节数前进。
   * @param mixingAudioFormat {@code mixingBuffer} 的 {@link AudioFormat}。
   * @param matrix 从输入到输出的缩放通道映射矩阵。
   * @param framesToMix 要混合的音频帧数。必须在两个缓冲区的范围内。
   * @param accumulate 是否与混合缓冲区中的现有样本进行累加。
   * @param clipFloatOutput 如果输出编码是 {@link C#ENCODING_PCM_FLOAT}，是否将输出信号限制在 [-1.0, 1.0] 范围内。
   * @return 返回 {@code mixingBuffer}，以便于链式调用。
   */
  public static ByteBuffer mix(
      ByteBuffer inputBuffer,
      AudioFormat inputAudioFormat,
      ByteBuffer mixingBuffer,
      AudioFormat mixingAudioFormat,
      ChannelMixingMatrix matrix,
      int framesToMix,
      boolean accumulate,
      boolean clipFloatOutput) {

    boolean int16Input = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT;
    boolean int16Output = mixingAudioFormat.encoding == C.ENCODING_PCM_16BIT;
    int inputChannels = matrix.getInputChannelCount();
    int outputChannels = matrix.getOutputChannelCount();
    float[] inputFrame = new float[inputChannels];
    float[] outputFrame = new float[outputChannels];

    for (int i = 0; i < framesToMix; i++) {
      if (accumulate) {
        int position = mixingBuffer.position();
        for (int outputChannel = 0; outputChannel < outputChannels; outputChannel++) {
          outputFrame[outputChannel] =
              getPcmSample(mixingBuffer, /* int16Buffer= */ int16Output, int16Output);
        }
        mixingBuffer.position(position);
      }

      for (int inputChannel = 0; inputChannel < inputChannels; inputChannel++) {
        inputFrame[inputChannel] =
            getPcmSample(inputBuffer, /* int16Buffer= */ int16Input, int16Output);
      }

      for (int outputChannel = 0; outputChannel < outputChannels; outputChannel++) {
        for (int inputChannel = 0; inputChannel < inputChannels; inputChannel++) {
          outputFrame[outputChannel] +=
              inputFrame[inputChannel] * matrix.getMixingCoefficient(inputChannel, outputChannel);
        }

        if (int16Output) {
          mixingBuffer.putShort(
              (short) constrainValue(outputFrame[outputChannel], Short.MIN_VALUE, Short.MAX_VALUE));
        } else {
          mixingBuffer.putFloat(
              clipFloatOutput
                  ? constrainValue(
                  outputFrame[outputChannel], FLOAT_PCM_MIN_VALUE, FLOAT_PCM_MAX_VALUE)
                  : outputFrame[outputChannel]);
        }

        outputFrame[outputChannel] = 0;
      }
    }
    return mixingBuffer;
  }

  /**
   * 从原始音频的 {@link ByteBuffer} 中获取下一个样本。
   *
   * <p>Int16 PCM 的值范围：[{@link Short#MIN_VALUE}, {@link Short#MAX_VALUE}]。
   *
   * <p>Float PCM 的值范围：[-1.0, 1.0]。
   *
   * @param buffer 包含原始音频的 {@link ByteBuffer}。
   * @param int16Buffer 缓冲区是否包含 {@link C#ENCODING_PCM_16BIT} 音频。如果缓冲区包含 {@link C#ENCODING_PCM_FLOAT} 音频，则使用 {@code false}。
   * @param int16Output 返回的样本是否应在 {@link C#ENCODING_PCM_16BIT} 的值范围内。如果为 {@code false}，则使用 Float PCM 范围。
   * @return 从缓冲区中获取的下一个样本。
   */
  private static float getPcmSample(ByteBuffer buffer, boolean int16Buffer, boolean int16Output) {
    if (int16Output) {
      return int16Buffer ? buffer.getShort() : floatSampleToInt16Pcm(buffer.getFloat());
    } else {
      return int16Buffer ? int16SampleToFloatPcm(buffer.getShort()) : buffer.getFloat();
    }
  }

  private static float floatSampleToInt16Pcm(float floatPcmValue) {
    return constrainValue(
        floatPcmValue * (floatPcmValue < 0 ? -Short.MIN_VALUE : Short.MAX_VALUE),
        Short.MIN_VALUE,
        Short.MAX_VALUE);
  }

  private static float int16SampleToFloatPcm(short shortPcmValue) {
    // Short.MIN_VALUE != -Short.MAX_VALUE，因此对正负值使用不同的转换。
    return shortPcmValue / (float) (shortPcmValue < 0 ? -Short.MIN_VALUE : Short.MAX_VALUE);
  }

  private AudioMixingUtil() {}
}