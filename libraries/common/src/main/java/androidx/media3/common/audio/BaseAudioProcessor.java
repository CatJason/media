package androidx.media3.common.audio;

import androidx.annotation.CallSuper;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 音频处理器的基类，该类维护一个输出缓冲区和一个内部缓冲区，每当输入被加入队列时都会重用该内部缓冲区。
 * 子类应重写 {@link #onConfigure(AudioFormat)} 以返回处理器的输出音频格式（如果处理器处于激活状态）。
 */
@UnstableApi
public abstract class BaseAudioProcessor implements AudioProcessor {

  /** 当前的输入音频格式。 */
  protected AudioFormat inputAudioFormat;

  /** 当前的输出音频格式。 */
  protected AudioFormat outputAudioFormat;

  private AudioFormat pendingInputAudioFormat;
  private AudioFormat pendingOutputAudioFormat;
  private ByteBuffer buffer;
  private ByteBuffer outputBuffer;
  private boolean inputEnded;

  public BaseAudioProcessor() {
    buffer = EMPTY_BUFFER;
    outputBuffer = EMPTY_BUFFER;
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
  }

  @Override
  public final AudioFormat configure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    pendingInputAudioFormat = inputAudioFormat;
    pendingOutputAudioFormat = onConfigure(inputAudioFormat);
    return isActive() ? pendingOutputAudioFormat : AudioFormat.NOT_SET;
  }

  @CallSuper
  @Override
  public boolean isActive() {
    return pendingOutputAudioFormat != AudioFormat.NOT_SET;
  }

  @Override
  public final void queueEndOfStream() {
    inputEnded = true;
    onQueueEndOfStream();
  }

  @CallSuper
  @Override
  public ByteBuffer getOutput() {
    ByteBuffer outputBuffer = this.outputBuffer;
    this.outputBuffer = EMPTY_BUFFER;
    return outputBuffer;
  }

  @CallSuper
  @SuppressWarnings("ReferenceEquality")
  @Override
  public boolean isEnded() {
    return inputEnded && outputBuffer == EMPTY_BUFFER;
  }

  @Override
  public final void flush() {
    outputBuffer = EMPTY_BUFFER;
    inputEnded = false;
    inputAudioFormat = pendingInputAudioFormat;
    outputAudioFormat = pendingOutputAudioFormat;
    onFlush();
  }

  @Override
  public final void reset() {
    flush();
    buffer = EMPTY_BUFFER;
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
    onReset();
  }

  /**
   * 用至少 {@code size} 字节的缓冲区替换当前输出缓冲区并返回它。
   * 调用者应写入返回的缓冲区，然后调用 {@link ByteBuffer#flip()}，以便可以通过 {@link #getOutput()} 读取。
   */
  protected final ByteBuffer replaceOutputBuffer(int size) {
    if (buffer.capacity() < size) {
      buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    } else {
      buffer.clear();
    }
    outputBuffer = buffer;
    return buffer;
  }

  /** 返回当前输出缓冲区是否还有剩余数据。 */
  protected final boolean hasPendingOutput() {
    return outputBuffer.hasRemaining();
  }

  /** 当处理器为新输入格式配置时调用。 */
  protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    return AudioFormat.NOT_SET;
  }

  /** 当流结束信号被加入处理器时调用。 */
  protected void onQueueEndOfStream() {
    // 默认不执行任何操作。
  }

  /** 当处理器被刷新时调用，无论是直接刷新还是作为重置的一部分。 */
  protected void onFlush() {
    // 默认不执行任何操作。
  }

  /** 当处理器被重置时调用。 */
  protected void onReset() {
    // 默认不执行任何操作。
  }
}