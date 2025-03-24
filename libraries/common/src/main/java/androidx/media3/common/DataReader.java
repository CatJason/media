package androidx.media3.common;

import androidx.media3.common.util.UnstableApi;
import java.io.IOException;

/** 从数据流中读取字节。 */
@UnstableApi
public interface DataReader {
  /**
   * 从输入中读取最多 {@code length} 字节的数据。
   *
   * <p>如果 {@code readLength} 为零，则返回 0。否则，如果由于已到达打开范围的末尾而没有可用数据，则返回 {@link C#RESULT_END_OF_INPUT}。
   * 否则，该方法将阻塞，直到至少读取了一个字节的数据，并返回读取的字节数。
   *
   * @param buffer 目标数组，数据应写入其中。
   * @param offset 目标数组中的偏移量，表示写入的起始位置。
   * @param length 从输入中读取的最大字节数。
   * @return 读取的字节数，如果输入已结束，则返回 {@link C#RESULT_END_OF_INPUT}。这可能小于 {@code length}，因为已到达输入（或可用数据）的末尾、方法被中断或操作因其他原因提前中止。
   * @throws IOException 如果从输入中读取时发生错误。
   */
  int read(byte[] buffer, int offset, int length) throws IOException;
}