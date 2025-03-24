package androidx.media3.common.audio;

import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;

/**
 * 一个用于处理音频通道混合和缩放的 {@link AudioProcessor}。在使用音频处理器之前，调用 {@link
 * #putChannelMixingMatrix(ChannelMixingMatrix)} 为每个可能的输入通道数指定要应用的混合矩阵。输入和输出为 16 位 PCM。
 */
@UnstableApi
public final class ChannelMixingAudioProcessor extends BaseAudioProcessor {

  private final SparseArray<ChannelMixingMatrix> matrixByInputChannelCount;

  /** 创建一个新的音频处理器，用于混合和缩放音频通道。 */
  public ChannelMixingAudioProcessor() {
    matrixByInputChannelCount = new SparseArray<>();
  }

  /**
   * 存储一个通道混合矩阵，用于处理具有给定 {@link ChannelMixingMatrix#getInputChannelCount() 通道数} 的音频。覆盖之前为相同输入通道数存储的任何矩阵。
   */
  public void putChannelMixingMatrix(ChannelMixingMatrix matrix) {
    int inputChannelCount = matrix.getInputChannelCount();
    matrixByInputChannelCount.put(inputChannelCount, matrix);
  }

  @Override
  protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    // TODO(b/290002731): 扩展以支持浮点数，因为 AudioMixingUtil 内置支持浮点数。
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
    @Nullable
    ChannelMixingMatrix channelMixingMatrix =
        matrixByInputChannelCount.get(inputAudioFormat.channelCount);
    if (channelMixingMatrix == null) {
      throw new UnhandledAudioFormatException(
          "没有适用于输入通道数的混合矩阵", inputAudioFormat);
    }
    if (channelMixingMatrix.isIdentity()) {
      return AudioFormat.NOT_SET;
    }
    return new AudioFormat(
        inputAudioFormat.sampleRate,
        channelMixingMatrix.getOutputChannelCount(),
        C.ENCODING_PCM_16BIT);
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    ChannelMixingMatrix channelMixingMatrix =
        checkStateNotNull(matrixByInputChannelCount.get(inputAudioFormat.channelCount));

    int framesToMix = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame;
    ByteBuffer outputBuffer = replaceOutputBuffer(framesToMix * outputAudioFormat.bytesPerFrame);
    AudioMixingUtil.mix(
        inputBuffer,
        inputAudioFormat,
        outputBuffer,
        outputAudioFormat,
        channelMixingMatrix,
        framesToMix,
        /* accumulate= */ false,
        /* clipFloatOutput= */ true);
    outputBuffer.flip();
  }
}