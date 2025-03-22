package androidx.media3.extractor.metadata;

import androidx.annotation.Nullable;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;

/** 从二进制数据中解码元数据的接口。 */
@UnstableApi
public interface MetadataDecoder {

  /**
   * 从提供的输入缓冲区中解码一个 {@link Metadata} 元素。
   *
   * <p>尊重 {@code inputBuffer.data} 的 {@link ByteBuffer#limit()}，但假设 {@link ByteBuffer#position()} 和 {@link ByteBuffer#arrayOffset()} 均为零，并且 {@link ByteBuffer#hasArray()} 为 true。
   *
   * @param inputBuffer 要解码的输入缓冲区。
   * @return 解码后的元数据对象，如果无法解码则返回 {@code null}。
   */
  @Nullable
  Metadata decode(MetadataInputBuffer inputBuffer);
}