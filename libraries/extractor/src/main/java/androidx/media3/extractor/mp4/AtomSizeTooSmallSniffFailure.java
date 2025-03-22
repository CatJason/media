package androidx.media3.extractor.mp4;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.SniffFailure;

/**
 * 一个 {@link SniffFailure}，表示某个原子（atom）声明的尺寸太小，无法容纳给定类型必须存在的头字段。
 */
@UnstableApi
public final class AtomSizeTooSmallSniffFailure implements SniffFailure {
  public final int atomType; // 原子类型
  public final long atomSize; // 原子大小
  public final int minimumHeaderSize; // 最小头字段大小

  public AtomSizeTooSmallSniffFailure(int atomType, long atomSize, int minimumHeaderSize) {
    this.atomType = atomType;
    this.atomSize = atomSize;
    this.minimumHeaderSize = minimumHeaderSize;
  }
}