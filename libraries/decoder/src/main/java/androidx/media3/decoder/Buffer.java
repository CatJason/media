package androidx.media3.decoder;

import androidx.annotation.CallSuper;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;

/** 带有标志的缓冲区的基类。 */
@UnstableApi
public abstract class Buffer {

  private @C.BufferFlags int flags;

  /** 清除缓冲区。 */
  @CallSuper
  public void clear() {
    flags = 0;
  }

  /** 返回是否设置了 {@link C#BUFFER_FLAG_FIRST_SAMPLE} 标志。 */
  public final boolean isFirstSample() {
    return getFlag(C.BUFFER_FLAG_FIRST_SAMPLE);
  }

  /**
   * 返回是否设置了 {@link C#BUFFER_FLAG_END_OF_STREAM} 标志。
   *
   * <p>如果设置了此标志，则应忽略缓冲区的所有其他属性。
   */
  public final boolean isEndOfStream() {
    return getFlag(C.BUFFER_FLAG_END_OF_STREAM);
  }

  /** 返回是否设置了 {@link C#BUFFER_FLAG_KEY_FRAME} 标志。 */
  public final boolean isKeyFrame() {
    return getFlag(C.BUFFER_FLAG_KEY_FRAME);
  }

  /** 返回是否设置了 {@link C#BUFFER_FLAG_LAST_SAMPLE} 标志。 */
  public final boolean isLastSample() {
    return getFlag(C.BUFFER_FLAG_LAST_SAMPLE);
  }

  /** 返回是否设置了 {@link C#BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA} 标志。 */
  public final boolean hasSupplementalData() {
    return getFlag(C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA);
  }

  /** 返回是否设置了 {@link C#BUFFER_FLAG_NOT_DEPENDED_ON} 标志。 */
  public final boolean notDependedOn() {
    return getFlag(C.BUFFER_FLAG_NOT_DEPENDED_ON);
  }

  /**
   * 将此缓冲区的标志替换为 {@code flags}。
   *
   * @param flags 要设置的标志，应为 {@code C.BUFFER_FLAG_*} 常量的组合。
   */
  public final void setFlags(@C.BufferFlags int flags) {
    this.flags = flags;
  }

  /**
   * 将 {@code flag} 添加到此缓冲区的标志中。
   *
   * @param flag 要添加到此缓冲区标志中的标志，应为 {@code C.BUFFER_FLAG_*} 常量之一。
   */
  public final void addFlag(@C.BufferFlags int flag) {
    flags |= flag;
  }

  /**
   * 如果设置了 {@code flag}，则将其从此缓冲区的标志中移除。
   *
   * @param flag 要移除的标志。
   */
  public final void clearFlag(@C.BufferFlags int flag) {
    flags &= ~flag;
  }

  /**
   * 返回是否在此缓冲区上设置了指定的标志。
   *
   * @param flag 要检查的标志。
   * @return 是否设置了该标志。
   */
  protected final boolean getFlag(@C.BufferFlags int flag) {
    return (flags & flag) == flag;
  }
}