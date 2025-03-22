package androidx.media3.extractor.metadata;

import androidx.annotation.Nullable;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;

/** 一个验证输入缓冲区的 {@link MetadataDecoder} 基类。 */
@UnstableApi
public abstract class SimpleMetadataDecoder implements MetadataDecoder {

  @Override
  @Nullable
  public final Metadata decode(MetadataInputBuffer inputBuffer) {
    ByteBuffer buffer = Assertions.checkNotNull(inputBuffer.data); // 确保输入缓冲区的数据不为空
    Assertions.checkArgument(
        buffer.position() == 0 && buffer.hasArray() && buffer.arrayOffset() == 0); // 验证缓冲区的位置、数组和偏移量
    return decode(inputBuffer, buffer); // 调用抽象方法进行实际解码
  }

  /**
   * 在 {@link #decode(MetadataInputBuffer)} 完成输入缓冲区验证后调用。
   *
   * @param inputBuffer 要解码的输入缓冲区。
   * @param buffer 输入缓冲区的 {@link MetadataInputBuffer#data 数据缓冲区}，为了方便使用。
   *     {@link #decode} 的验证保证 {@link ByteBuffer#hasArray()} 为 {@code true}，{@link ByteBuffer#position()} 和 {@link ByteBuffer#arrayOffset()} 分别为 {@code 0}。
   * @return 解码后的元数据对象，如果无法解码则返回 {@code null}。
   */
  @Nullable
  protected abstract Metadata decode(MetadataInputBuffer inputBuffer, ByteBuffer buffer);
}