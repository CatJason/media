package androidx.media3.common.audio;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 音频处理器的接口，用于接收音频数据并对其进行转换，可能会修改其声道数、编码和/或采样率。
 *
 * <p>除了能够修改音频格式外，实现类还可以设置参数以影响输出音频以及处理器是否处于激活/非激活状态。
 */
@UnstableApi
public interface AudioProcessor {

  /** 音频处理器可能处理的 PCM 音频格式。 */
  final class AudioFormat {
    /**
     * 用于表示未设置的 {@link AudioFormat} 的实例。如果处理器 {@link #isActive()}，则不应由 {@link #configure(AudioFormat)} 返回。
     *
     * <p>通常用于表示非激活的 {@link AudioProcessor} 的 {@linkplain #configure(AudioFormat) 输出格式}。
     */
    public static final AudioFormat NOT_SET =
        new AudioFormat(
            /* sampleRate= */ Format.NO_VALUE,
            /* channelCount= */ Format.NO_VALUE,
            /* encoding= */ Format.NO_VALUE);

    /** 采样率，单位为赫兹。 */
    public final int sampleRate;

    /** 交错的声道数。 */
    public final int channelCount;

    /** 线性 PCM 编码类型。 */
    public final @C.PcmEncoding int encoding;

    /** 表示一个音频帧的字节数。 */
    public final int bytesPerFrame;

    /**
     * 使用 {@link Format#sampleRate}、{@link Format#channelCount} 和 {@link Format#pcmEncoding} 创建实例。
     */
    public AudioFormat(Format format) {
      this(format.sampleRate, format.channelCount, format.pcmEncoding);
    }

    public AudioFormat(int sampleRate, int channelCount, @C.PcmEncoding int encoding) {
      this.sampleRate = sampleRate;
      this.channelCount = channelCount;
      this.encoding = encoding;
      bytesPerFrame =
          Util.isEncodingLinearPcm(encoding)
              ? Util.getPcmFrameSize(encoding, channelCount)
              : Format.NO_VALUE;
    }

    @Override
    public String toString() {
      return "AudioFormat["
          + "sampleRate="
          + sampleRate
          + ", channelCount="
          + channelCount
          + ", encoding="
          + encoding
          + ']';
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof AudioFormat)) {
        return false;
      }
      AudioFormat that = (AudioFormat) o;
      return sampleRate == that.sampleRate
          && channelCount == that.channelCount
          && encoding == that.encoding;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(sampleRate, channelCount, encoding);
    }
  }

  /** 当给定的 {@link AudioFormat} 无法处理时抛出的异常。 */
  final class UnhandledAudioFormatException extends Exception {
    public final AudioFormat inputAudioFormat;

    public UnhandledAudioFormatException(AudioFormat inputAudioFormat) {
      this("无法处理的输入格式:", inputAudioFormat);
    }

    public UnhandledAudioFormatException(String message, AudioFormat audioFormat) {
      super(message + " " + audioFormat);
      this.inputAudioFormat = audioFormat;
    }
  }

  /** 一个空的直接 {@link ByteBuffer}。 */
  ByteBuffer EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());

  /**
   * 返回处理器应用后输出流的预期持续时间，给定输入 {@code durationUs}。
   */
  default long getDurationAfterProcessorApplied(long durationUs) {
    return durationUs;
  }

  /**
   * 配置处理器以处理具有指定格式的输入音频。调用此方法后，调用 {@link #isActive()} 以确定音频处理器是否处于激活状态。如果此实例处于激活状态，则返回配置后的输出音频格式。
   *
   * <p>调用此方法后，必须调用 {@link #flush()} 以应用新配置。在应用新配置之前，可以在旧的输入/输出格式下安全地加入输入并获取输出。当不再为旧输入格式提供输入时，调用 {@link #queueEndOfStream()}。
   *
   * @param inputAudioFormat 在下次调用 {@link #flush()} 后将加入的音频格式。
   * @return 如果此实例 {@link #isActive()} 处于激活状态，则返回配置后的输出音频格式。
   * @throws UnhandledAudioFormatException 如果无法处理指定的格式作为输入，则抛出此异常。
   */
  AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException;

  /** 返回处理器是否已配置并将处理输入缓冲区。 */
  boolean isActive();

  /**
   * 将 {@code inputBuffer} 中从 position 到 limit 之间的音频数据加入队列以进行处理。调用此方法后，处理后的输出可能通过 {@link #getOutput()} 获取。再次调用 {@code queueInput(ByteBuffer)} 会使任何挂起的输出失效。
   *
   * @param inputBuffer 要处理的输入缓冲区。它必须是一个具有本地字节序的直接字节缓冲区。其内容被视为只读。其 position 将根据消耗的字节数（可能为零）前进。调用者保留对提供的缓冲区的所有权。
   */
  void queueInput(ByteBuffer inputBuffer);

  /**
   * 加入一个流结束信号。调用此方法后，在下次调用 {@link #flush()} 之前，不应再调用 {@link #queueInput(ByteBuffer)}。调用 {@link #getOutput()} 将返回所有剩余的输出数据。可能需要多次调用以读取所有剩余的输出数据。一旦所有剩余的输出数据被读取完毕，{@link #isEnded()} 将返回 {@code true}。
   */
  void queueEndOfStream();

  /**
   * 返回一个包含从 position 到 limit 之间的已处理输出数据的缓冲区。缓冲区将始终是一个具有本地字节序的直接字节缓冲区。调用此方法会使任何先前返回的缓冲区失效。如果没有可用的输出，缓冲区将为空。
   *
   * @return 包含从 position 到 limit 之间的已处理输出数据的缓冲区。
   */
  ByteBuffer getOutput();

  /**
   * 返回此处理器是否不会再从 {@link #getOutput()} 返回输出，直到调用 {@link #flush()} 并加入更多输入。
   */
  boolean isEnded();

  /**
   * 清除所有缓冲的数据和挂起的输出。如果音频处理器处于激活状态，还会准备音频处理器以接收新的输入流，使用最近一次配置的（挂起）格式。
   */
  void flush();

  /** 重置处理器到未配置状态，释放所有资源。 */
  void reset();
}