package androidx.media3.common.audio;

import static androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER;
import static androidx.media3.common.util.Assertions.checkState;

import androidx.annotation.Nullable;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 处理通过多个 {@link AudioProcessor} 实例传递的缓冲区。
 *
 * <p>如果两个 {@link AudioProcessingPipeline} 实例具有相同的底层 {@link AudioProcessor} 引用，并且顺序相同，则认为它们是 {@linkplain #equals(Object) 相等的}。
 *
 * <p>要使用此类，调用者必须：
 *
 * <ul>
 *   <li>初始化一个实例，传入所有可能用于处理的音频处理器。
 *   <li>调用 {@link #configure(AudioFormat)} 并传入输入数据的 {@link AudioFormat}。此方法将返回管道在当前配置下输出的 {@link AudioFormat}。
 *   <li>调用 {@link #flush()} 以应用挂起的配置。
 *   <li>检查管道是否 {@link #isOperational()}。如果否，则管道无法在当前配置下处理缓冲区。这是因为底层的 {@link AudioProcessor} 实例都未 {@linkplain AudioProcessor#isActive 激活}。
 *   <li>如果管道 {@link #isOperational()}，则调用 {@link #queueInput(ByteBuffer)}，然后调用 {@link #getOutput()} 来处理缓冲区。
 *   <li>调用 {@link #queueEndOfStream()} 以通知管道当前输入流已结束。
 *   <li>重复调用 {@link #getOutput()} 并处理这些缓冲区，直到 {@link #isEnded()} 返回 true。
 *   <li>当完成管道操作后，调用 {@link #reset()} 以释放底层资源。
 * </ul>
 *
 * <p>如果底层的 {@link AudioProcessor} 实例有挂起的配置更改，或者输入数据的 {@link AudioFormat} 发生变化：
 *
 * <ul>
 *   <li>调用 {@link #configure(AudioFormat)} 为新的输入流配置管道。此时仍可以在旧配置下调用 {@link #queueInput(ByteBuffer)} 和 {@link #getOutput()}。
 *   <li>调用 {@link #queueEndOfStream()} 以通知管道当前输入流已结束。
 *   <li>重复调用 {@link #getOutput()}，直到 {@link #isEnded()} 返回 true。
 *   <li>调用 {@link #flush()} 以应用新配置并刷新管道。
 *   <li>在新配置下开始 {@linkplain #queueInput(ByteBuffer) 输入队列} 并处理 {@linkplain #getOutput() 输出}。
 * </ul>
 */
@UnstableApi
public final class AudioProcessingPipeline {
  /** 传递给 {@link AudioProcessingPipeline} 的 {@link AudioProcessor} 实例。 */
  private final ImmutableList<AudioProcessor> audioProcessors;

  /**
   * 基于当前配置 {@linkplain AudioProcessor#isActive() 激活} 的处理器。
   */
  private final List<AudioProcessor> activeAudioProcessors;

  /**
   * 由 {@link #activeAudioProcessors} 输出的缓冲区。此数组的元素数量与 {@link #activeAudioProcessors} 相同。
   */
  private ByteBuffer[] outputBuffers;

  /** 管道当前输出的 {@link AudioFormat}。 */
  private AudioFormat outputAudioFormat;

  /** 在调用 {@link #flush()} 后将输出的 {@link AudioFormat}。 */
  private AudioFormat pendingOutputAudioFormat;

  /** 输入是否已结束，无论是由于配置更改还是流结束。 */
  private boolean inputEnded;

  /**
   * 创建一个实例。
   *
   * @param audioProcessors 用于处理缓冲区的 {@link AudioProcessor} 实例。
   */
  public AudioProcessingPipeline(ImmutableList<AudioProcessor> audioProcessors) {
    this.audioProcessors = audioProcessors;
    activeAudioProcessors = new ArrayList<>();
    outputBuffers = new ByteBuffer[0];
    outputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputEnded = false;
  }
  /**
   * 配置管道以处理具有指定格式的输入音频。返回配置后的输出音频格式。
   *
   * <p>要使新配置生效，必须调用 {@linkplain #flush() 刷新} 管道。在应用新配置之前，可以在旧的输入/输出格式/配置下安全地加入输入并获取输出。当不再为旧配置提供输入时，调用 {@link #queueEndOfStream()}。
   *
   * @param inputAudioFormat 在下次调用 {@link #flush()} 后将加入的音频格式。
   * @return 配置后的输出音频格式。
   * @throws AudioProcessor.UnhandledAudioFormatException 如果管道不支持指定的格式。
   */
  @CanIgnoreReturnValue
  public AudioFormat configure(AudioFormat inputAudioFormat)
      throws AudioProcessor.UnhandledAudioFormatException {
    if (inputAudioFormat.equals(AudioFormat.NOT_SET)) {
      throw new AudioProcessor.UnhandledAudioFormatException(inputAudioFormat);
    }

    AudioFormat intermediateAudioFormat = inputAudioFormat;

    for (int i = 0; i < audioProcessors.size(); i++) {
      AudioProcessor audioProcessor = audioProcessors.get(i);
      AudioFormat nextFormat = audioProcessor.configure(intermediateAudioFormat);
      if (audioProcessor.isActive()) {
        checkState(!nextFormat.equals(AudioFormat.NOT_SET));
        intermediateAudioFormat = nextFormat;
      }
    }

    return pendingOutputAudioFormat = intermediateAudioFormat;
  }

  /**
   * 清除所有缓冲的数据和挂起的输出。如果任何底层的音频处理器处于 {@linkplain AudioProcessor#isActive() 激活} 状态，
   * 此方法还会准备它们以接收新的输入流，使用最近一次 {@linkplain #configure(AudioFormat) 配置} 的（挂起）格式。
   *
   * <p>在调用此方法之前，必须在上一次调用 {@link #reset()} 后至少调用过一次 {@link #configure(AudioFormat)}。
   */
  public void flush() {
    activeAudioProcessors.clear();
    outputAudioFormat = pendingOutputAudioFormat;
    inputEnded = false;

    for (int i = 0; i < audioProcessors.size(); i++) {
      AudioProcessor audioProcessor = audioProcessors.get(i);
      audioProcessor.flush();
      if (audioProcessor.isActive()) {
        activeAudioProcessors.add(audioProcessor);
      }
    }

    outputBuffers = new ByteBuffer[activeAudioProcessors.size()];
    for (int i = 0; i <= getFinalOutputBufferIndex(); i++) {
      outputBuffers[i] = activeAudioProcessors.get(i).getOutput();
    }
  }

  /**
   * 返回通过 {@link #getOutput()} 输出的数据的 {@link AudioFormat}。
   *
   * @return 当前正在输出的 {@link AudioFormat}，如果尚未 {@linkplain #flush() 应用} 任何 {@linkplain #configure(AudioFormat) 配置}，则返回 {@link AudioFormat#NOT_SET}。
   */
  public AudioFormat getOutputAudioFormat() {
    return outputAudioFormat;
  }

  /**
   * 管道是否可用于处理缓冲区。
   *
   * <p>要满足此条件，管道必须已经 {@linkplain #configure(AudioFormat) 配置} 并 {@linkplain #flush() 刷新}，
   * 同时具有 {@linkplain AudioProcessor#isActive() 激活} 的 {@linkplain AudioProcessor 底层音频处理器}，
   * 这些处理器已准备好使用当前配置处理缓冲区。
   */
  public boolean isOperational() {
    return !activeAudioProcessors.isEmpty();
  }

  /**
   * 将 {@code inputBuffer} 中从 position 到 limit 之间的音频数据加入队列以进行处理。调用此方法后，处理后的输出可能通过 {@link #getOutput()} 获取。
   *
   * @param inputBuffer 要处理的输入缓冲区。它必须是一个具有本地字节序的直接 {@link ByteBuffer}。
   * 其内容被视为只读。其 position 将根据消耗的字节数（可能为零）前进。调用者保留对提供的缓冲区的所有权。
   */
  public void queueInput(ByteBuffer inputBuffer) {
    if (!isOperational() || inputEnded) {
      return;
    }
    processData(inputBuffer);
  }

  /**
   * 返回一个包含从 position 到 limit 之间的已处理输出数据的 {@link ByteBuffer}。如果没有可用的输出，缓冲区将为空。
   *
   * <p>从此方法返回的缓冲区由管道保留，必须消费数据（或将其复制到另一个缓冲区）以允许管道继续处理。
   *
   * @return 包含从 position 到 limit 之间的已处理输出数据的缓冲区。
   */
  public ByteBuffer getOutput() {
    if (!isOperational()) {
      return EMPTY_BUFFER;
    }
    ByteBuffer outputBuffer = outputBuffers[getFinalOutputBufferIndex()];
    if (outputBuffer.hasRemaining()) {
      return outputBuffer;
    }

    processData(EMPTY_BUFFER);
    return outputBuffers[getFinalOutputBufferIndex()];
  }

  /**
   * 加入一个流结束信号。调用此方法后，在下次调用 {@link #flush()} 之前，不应再调用 {@link #queueInput(ByteBuffer)}。
   * 调用 {@link #getOutput()} 将返回所有剩余的输出数据。可能需要多次调用以读取所有剩余的输出数据。
   * 一旦所有剩余的输出数据被读取完毕，{@link #isEnded()} 将返回 {@code true}。
   */
  public void queueEndOfStream() {
    if (!isOperational() || inputEnded) {
      return;
    }
    inputEnded = true;
    activeAudioProcessors.get(0).queueEndOfStream();
  }

  /**
   * 返回管道是否已结束。
   *
   * <p>管道在以下情况下被视为已结束：
   *
   * <ul>
   *   <li>已 {@linkplain #queueEndOfStream() 加入} 流结束信号。
   *   <li>每个 {@linkplain #queueInput(ByteBuffer) 输入缓冲区} 都已被处理。
   *   <li>每个 {@linkplain #getOutput() 输出缓冲区} 都已被完全消费。
   * </ul>
   */
  public boolean isEnded() {
    return inputEnded
        && activeAudioProcessors.get(getFinalOutputBufferIndex()).isEnded()
        && !outputBuffers[getFinalOutputBufferIndex()].hasRemaining();
  }

  /**
   * 重置管道及其底层的 {@link AudioProcessor} 实例到未配置状态，并释放所有资源。
   */
  public void reset() {
    for (int i = 0; i < audioProcessors.size(); i++) {
      AudioProcessor audioProcessor = audioProcessors.get(i);
      audioProcessor.flush();
      audioProcessor.reset();
    }
    outputBuffers = new ByteBuffer[0];
    outputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputEnded = false;
  }

  /**
   * 指示某个其他对象是否与此对象“相等”。
   *
   * <p>如果两个 {@link AudioProcessingPipeline} 实例具有相同的底层 {@link AudioProcessor} 引用，并且顺序相同，则认为它们是相等的。
   */
  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AudioProcessingPipeline)) {
      return false;
    }
    AudioProcessingPipeline that = (AudioProcessingPipeline) o;
    if (this.audioProcessors.size() != that.audioProcessors.size()) {
      return false;
    }
    for (int i = 0; i < this.audioProcessors.size(); i++) {
      if (this.audioProcessors.get(i) != that.audioProcessors.get(i)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public int hashCode() {
    return audioProcessors.hashCode();
  }

  private void processData(ByteBuffer inputBuffer) {
    boolean progressMade = true;
    while (progressMade) {
      progressMade = false;
      for (int index = 0; index <= getFinalOutputBufferIndex(); index++) {
        if (outputBuffers[index].hasRemaining()) {
          // 此索引处的处理器有尚未被消费的输出。不要加入输入。
          continue;
        }

        AudioProcessor audioProcessor = activeAudioProcessors.get(index);

        if (audioProcessor.isEnded()) {
          if (!outputBuffers[index].hasRemaining() && index < getFinalOutputBufferIndex()) {
            activeAudioProcessors.get(index + 1).queueEndOfStream();
          }
          continue;
        }

        ByteBuffer input =
            index > 0
                ? outputBuffers[index - 1]
                : inputBuffer.hasRemaining() ? inputBuffer : EMPTY_BUFFER;
        long inputBytes = input.remaining();
        audioProcessor.queueInput(input);
        outputBuffers[index] = audioProcessor.getOutput();

        progressMade |= (inputBytes - input.remaining()) > 0 || outputBuffers[index].hasRemaining();
      }
    }
  }

  private int getFinalOutputBufferIndex() {
    return outputBuffers.length - 1;
  }
}
