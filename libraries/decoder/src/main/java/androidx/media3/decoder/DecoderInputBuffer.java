package androidx.media3.decoder;

import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;

/** 保存解码器的输入数据。 */
@UnstableApi
public class DecoderInputBuffer extends Buffer {

  static {
    MediaLibraryInfo.registerModule("media3.decoder");
  }

  /**
   * 当尝试向 {@link DecoderInputBuffer} 写入数据时，如果其 {@link #bufferReplacementMode} 为 {@link #BUFFER_REPLACEMENT_MODE_DISABLED} 且 {@link #data} 的容量不足，则抛出此异常。
   */
  public static final class InsufficientCapacityException extends IllegalStateException {

    /** 缓冲区的当前容量。 */
    public final int currentCapacity;

    /** 缓冲区所需的容量。 */
    public final int requiredCapacity;

    /**
     * 创建实例。
     *
     * @param currentCapacity 缓冲区的当前容量。
     * @param requiredCapacity 缓冲区所需的容量。
     */
    public InsufficientCapacityException(int currentCapacity, int requiredCapacity) {
      super("缓冲区太小 (" + currentCapacity + " < " + requiredCapacity + ")");
      this.currentCapacity = currentCapacity;
      this.requiredCapacity = requiredCapacity;
    }
  }

  /**
   * 缓冲区替换模式。此模式控制当现有缓冲区容量不足时，{@link #ensureSpaceForWrite} 如何生成替换缓冲区。可以是 {@link #BUFFER_REPLACEMENT_MODE_DISABLED}、{@link #BUFFER_REPLACEMENT_MODE_NORMAL} 或 {@link #BUFFER_REPLACEMENT_MODE_DIRECT} 之一。
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
      BUFFER_REPLACEMENT_MODE_DISABLED,
      BUFFER_REPLACEMENT_MODE_NORMAL,
      BUFFER_REPLACEMENT_MODE_DIRECT
  })
  public @interface BufferReplacementMode {}

  /** 禁止缓冲区替换。 */
  public static final int BUFFER_REPLACEMENT_MODE_DISABLED = 0;

  /** 允许使用 {@link ByteBuffer#allocate(int)} 替换缓冲区。 */
  public static final int BUFFER_REPLACEMENT_MODE_NORMAL = 1;

  /** 允许使用 {@link ByteBuffer#allocateDirect(int)} 替换缓冲区。 */
  public static final int BUFFER_REPLACEMENT_MODE_DIRECT = 2;

  /** {@link Format} 格式信息。 */
  @Nullable public Format format;

  /** 加密数据的 {@link CryptoInfo}。 */
  public final CryptoInfo cryptoInfo;

  /** 缓冲区的数据，如果未设置数据则为 {@code null}。 */
  @Nullable public ByteBuffer data;

  // TODO: 修复使用内容保护的剪辑的流结束传播问题后，删除此临时信号。详见 [Internal: b/153326944]。
  /**
   * 上次尝试将样本读取到此缓冲区是否因尚未获取与下一个样本关联的 DRM 密钥而失败。
   */
  public boolean waitingForKeys;

  /** 样本应呈现的时间。 */
  public long timeUs;

  /**
   * 与缓冲区相关的补充数据，如果 {@link #hasSupplementalData()} 返回 true。如果存在，则缓冲区从位置 0 到其限制填充补充数据。
   */
  @Nullable public ByteBuffer supplementalData;

  private final @BufferReplacementMode int bufferReplacementMode;
  private final int paddingSize;

  /** 返回一个不能保存任何数据的新实例。 */
  public static DecoderInputBuffer newNoDataInstance() {
    return new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);
  }

  /**
   * 创建新实例。
   *
   * @param bufferReplacementMode 缓冲区替换模式 {@link BufferReplacementMode}。
   */
  public DecoderInputBuffer(@BufferReplacementMode int bufferReplacementMode) {
    this(bufferReplacementMode, /* paddingSize= */ 0);
  }

  /**
   * 创建新实例。
   *
   * @param bufferReplacementMode 缓冲区替换模式 {@link BufferReplacementMode}。
   * @param paddingSize 如果非零，{@link #ensureSpaceForWrite(int)} 将确保缓冲区比请求的长度大此字节数。这对于以固定大小块消费数据的解码器非常有用，可以提高效率。将填充大小设置为解码器的固定读取大小可以防止解码器尝试读取超出缓冲区的末尾。
   */
  public DecoderInputBuffer(@BufferReplacementMode int bufferReplacementMode, int paddingSize) {
    this.cryptoInfo = new CryptoInfo();
    this.bufferReplacementMode = bufferReplacementMode;
    this.paddingSize = paddingSize;
  }

  /**
   * 清除 {@link #supplementalData} 并确保其足够大以容纳 {@code length} 字节。
   *
   * @param length 必须容纳的补充数据的长度，以字节为单位。
   */
  @EnsuresNonNull("supplementalData")
  public void resetSupplementalData(int length) {
    if (supplementalData == null || supplementalData.capacity() < length) {
      supplementalData = ByteBuffer.allocate(length);
    } else {
      supplementalData.clear();
    }
  }

  /**
   * 确保 {@link #data} 足够大以容纳在其当前位置写入指定长度的数据。
   *
   * <p>如果 {@link #data} 的容量足够，则此方法不执行任何操作。如果容量不足，则尝试将 {@link #data} 替换为容量足够的新 {@link ByteBuffer}。当前位置之前的数据将复制到新缓冲区中。
   *
   * @param length 必须容纳的写入长度，以字节为单位。
   * @throws InsufficientCapacityException 如果没有足够容量容纳写入且 {@link #bufferReplacementMode} 为 {@link #BUFFER_REPLACEMENT_MODE_DISABLED}。
   */
  @EnsuresNonNull("data")
  public void ensureSpaceForWrite(int length) {
    length += paddingSize;
    @Nullable ByteBuffer currentData = data;
    if (currentData == null) {
      data = createReplacementByteBuffer(length);
      return;
    }
    // 检查当前缓冲区是否足够。
    int capacity = currentData.capacity();
    int position = currentData.position();
    int requiredCapacity = position + length;
    if (capacity >= requiredCapacity) {
      data = currentData;
      return;
    }
    // 如果可能，实例化一个新缓冲区。
    ByteBuffer newData = createReplacementByteBuffer(requiredCapacity);
    newData.order(currentData.order());
    // 将旧缓冲区中当前位置之前的数据复制到新缓冲区中。
    if (position > 0) {
      currentData.flip();
      newData.put(currentData);
    }
    // 设置新缓冲区。
    data = newData;
  }

  /** 返回是否设置了 {@link C#BUFFER_FLAG_ENCRYPTED} 标志。 */
  public final boolean isEncrypted() {
    return getFlag(C.BUFFER_FLAG_ENCRYPTED);
  }

  /**
   * 翻转 {@link #data} 和 {@link #supplementalData}，以便将其排队到解码器。
   *
   * @see java.nio.Buffer#flip()
   */
  public final void flip() {
    if (data != null) {
      data.flip();
    }
    if (supplementalData != null) {
      supplementalData.flip();
    }
  }

  @Override
  public void clear() {
    super.clear();
    if (data != null) {
      data.clear();
    }
    if (supplementalData != null) {
      supplementalData.clear();
    }
    waitingForKeys = false;
  }

  private ByteBuffer createReplacementByteBuffer(int requiredCapacity) {
    if (bufferReplacementMode == BUFFER_REPLACEMENT_MODE_NORMAL) {
      return ByteBuffer.allocate(requiredCapacity);
    } else if (bufferReplacementMode == BUFFER_REPLACEMENT_MODE_DIRECT) {
      return ByteBuffer.allocateDirect(requiredCapacity);
    } else {
      int currentCapacity = data == null ? 0 : data.capacity();
      throw new InsufficientCapacityException(currentCapacity, requiredCapacity);
    }
  }
}