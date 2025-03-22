/*
 * 版权所有 (C) 2016 The Android Open Source Project
 *
 * 根据 Apache 许可证 2.0 版本（“许可证”）授权；
 * 除非遵守许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则根据许可证分发的软件
 * 均按“原样”分发，不附带任何明示或暗示的担保或条件。
 * 请参阅许可证以了解特定语言的权限和限制。
 */
package androidx.media3.decoder;

import androidx.annotation.Nullable;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 用于 {@link SimpleDecoder} 输出的缓冲区。 */
@UnstableApi
public class SimpleDecoderOutputBuffer extends DecoderOutputBuffer {

  private final Owner<SimpleDecoderOutputBuffer> owner;

  @Nullable public ByteBuffer data;

  public SimpleDecoderOutputBuffer(Owner<SimpleDecoderOutputBuffer> owner) {
    this.owner = owner;
  }

  /**
   * 初始化缓冲区。
   *
   * @param timeUs 缓冲区的呈现时间戳，以微秒为单位。
   * @param size 写入缓冲区的数据大小的上限。
   * @return 返回 {@link #data} 缓冲区，以便于使用。
   */
  public ByteBuffer init(long timeUs, int size) {
    this.timeUs = timeUs;
    if (data == null || data.capacity() < size) {
      data = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }
    data.position(0);
    data.limit(size);
    return data;
  }

  /**
   * 将缓冲区扩展到新的大小。
   *
   * <p>现有数据会被复制到新缓冲区中，并且 {@link ByteBuffer#position} 会被保留。
   *
   * @param newSize 缓冲区的新大小。
   * @return 返回 {@link #data} 缓冲区，以便于使用。
   */
  public ByteBuffer grow(int newSize) {
    ByteBuffer oldData = Assertions.checkNotNull(this.data);
    Assertions.checkArgument(newSize >= oldData.limit());
    ByteBuffer newData = ByteBuffer.allocateDirect(newSize).order(ByteOrder.nativeOrder());
    int restorePosition = oldData.position();
    oldData.position(0);
    newData.put(oldData);
    newData.position(restorePosition);
    newData.limit(newSize);
    this.data = newData;
    return newData;
  }

  @Override
  public void clear() {
    super.clear();
    if (data != null) {
      data.clear();
    }
  }

  @Override
  public void release() {
    owner.releaseOutputBuffer(this);
  }
}