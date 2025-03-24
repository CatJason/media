package androidx.media3.common.audio;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.nio.ByteBuffer;

/**
 * 一个 {@link AudioProcessor}，用于将不同的 PCM 音频编码转换为 16 位整数 PCM。支持以下输入编码：
 *
 * <ul>
 *   <li>{@link C#ENCODING_PCM_8BIT}
 *   <li>{@link C#ENCODING_PCM_16BIT}（{@link #isActive()} 将返回 {@code false}）
 *   <li>{@link C#ENCODING_PCM_16BIT_BIG_ENDIAN}
 *   <li>{@link C#ENCODING_PCM_24BIT}
 *   <li>{@link C#ENCODING_PCM_24BIT_BIG_ENDIAN}
 *   <li>{@link C#ENCODING_PCM_32BIT}
 *   <li>{@link C#ENCODING_PCM_32BIT_BIG_ENDIAN}
 *   <li>{@link C#ENCODING_PCM_FLOAT}
 * </ul>
 */
@UnstableApi
public final class ToInt16PcmAudioProcessor extends BaseAudioProcessor {

  @Override
  public AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    @C.PcmEncoding int encoding = inputAudioFormat.encoding;
    if (encoding != C.ENCODING_PCM_8BIT
        && encoding != C.ENCODING_PCM_16BIT
        && encoding != C.ENCODING_PCM_16BIT_BIG_ENDIAN
        && encoding != C.ENCODING_PCM_24BIT
        && encoding != C.ENCODING_PCM_24BIT_BIG_ENDIAN
        && encoding != C.ENCODING_PCM_32BIT
        && encoding != C.ENCODING_PCM_32BIT_BIG_ENDIAN
        && encoding != C.ENCODING_PCM_FLOAT) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
    return encoding != C.ENCODING_PCM_16BIT
        ? new AudioFormat(
        inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_16BIT)
        : AudioFormat.NOT_SET;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    // 准备输出缓冲区。
    int position = inputBuffer.position();
    int limit = inputBuffer.limit();
    int size = limit - position;
    int resampledSize;
    switch (inputAudioFormat.encoding) {
      case C.ENCODING_PCM_8BIT:
        resampledSize = size * 2;
        break;
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
        resampledSize = size;
        break;
      case C.ENCODING_PCM_24BIT:
      case C.ENCODING_PCM_24BIT_BIG_ENDIAN:
        resampledSize = (size / 3) * 2;
        break;
      case C.ENCODING_PCM_32BIT:
      case C.ENCODING_PCM_32BIT_BIG_ENDIAN:
      case C.ENCODING_PCM_FLOAT:
        resampledSize = size / 2;
        break;
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        throw new IllegalStateException();
    }

    // 对输入进行重采样并更新输入/输出缓冲区。
    ByteBuffer buffer = replaceOutputBuffer(resampledSize);
    switch (inputAudioFormat.encoding) {
      case C.ENCODING_PCM_8BIT:
        // 8 -> 16 位重采样。将每个字节从 [0, 256) 转换为 [-128, 128) 并放大。
        for (int i = position; i < limit; i++) {
          buffer.put((byte) 0);
          buffer.put((byte) ((inputBuffer.get(i) & 0xFF) - 128));
        }
        break;
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
        // 大端序到小端序重采样。交换字节顺序。
        for (int i = position; i < limit; i += 2) {
          buffer.put(inputBuffer.get(i + 1));
          buffer.put(inputBuffer.get(i));
        }
        break;
      case C.ENCODING_PCM_24BIT:
        // 24 -> 16 位重采样。丢弃最低有效字节。
        for (int i = position; i < limit; i += 3) {
          buffer.put(inputBuffer.get(i + 1));
          buffer.put(inputBuffer.get(i + 2));
        }
        break;
      case C.ENCODING_PCM_24BIT_BIG_ENDIAN:
        // 24 位大端序 -> 16 位重采样。丢弃最低有效字节。
        for (int i = position; i < limit; i += 3) {
          buffer.put(inputBuffer.get(i + 1));
          buffer.put(inputBuffer.get(i));
        }
        break;
      case C.ENCODING_PCM_32BIT:
        // 32 -> 16 位重采样。丢弃两个最低有效字节。
        for (int i = position; i < limit; i += 4) {
          buffer.put(inputBuffer.get(i + 2));
          buffer.put(inputBuffer.get(i + 3));
        }
        break;
      case C.ENCODING_PCM_32BIT_BIG_ENDIAN:
        // 32 位大端序 -> 16 位重采样。丢弃两个最低有效字节。
        for (int i = position; i < limit; i += 4) {
          buffer.put(inputBuffer.get(i + 1));
          buffer.put(inputBuffer.get(i));
        }
        break;
      case C.ENCODING_PCM_FLOAT:
        // 32 位浮点数 -> 16 位重采样。浮点数的范围为 [-1.0, 1.0]，因此需要乘以 Short.MAX_VALUE。
        for (int i = position; i < limit; i += 4) {
          // 限制以避免浮点值超出其标称范围时的整数溢出 [内部参考：b/161204847]。
          float floatValue =
              Util.constrainValue(inputBuffer.getFloat(i), /* min= */ -1, /* max= */ 1);
          short shortValue = (short) (floatValue * Short.MAX_VALUE);
          buffer.put((byte) (shortValue & 0xFF));
          buffer.put((byte) ((shortValue >> 8) & 0xFF));
        }
        break;
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        // 永远不会发生。
        throw new IllegalStateException();
    }
    inputBuffer.position(inputBuffer.limit());
    buffer.flip();
  }
}