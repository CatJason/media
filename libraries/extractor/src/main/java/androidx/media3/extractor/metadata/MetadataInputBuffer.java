package androidx.media3.extractor.metadata;

import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;

/** 用于 {@link MetadataDecoder} 的 {@link DecoderInputBuffer}。 */
@UnstableApi
public final class MetadataInputBuffer extends DecoderInputBuffer {

  /**
   * 解码后必须添加到元数据时间戳的偏移量，如果应添加 {@link #timeUs}，则为 {@link Format#OFFSET_SAMPLE_RELATIVE}。
   */
  public long subsampleOffsetUs;

  public MetadataInputBuffer() {
    super(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL); // 调用父类构造函数
  }
}