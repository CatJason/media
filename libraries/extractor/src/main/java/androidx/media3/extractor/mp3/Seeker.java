package androidx.media3.extractor.mp3;

import androidx.media3.common.C;
import androidx.media3.extractor.SeekMap;

/**
 * {@link SeekMap} 实现，提供音频数据的结束位置，并允许从位置（字节偏移量）映射到时间，
 * 这可以用于在搜索和重新同步后计算新的样本基准时间戳。
 */
/* package */ interface Seeker extends SeekMap {

  /**
   * 将位置（字节偏移量）映射到相应的样本时间戳。
   *
   * @param position 相对于流开始的搜索位置（字节偏移量）。
   * @return 下一个要读取的样本的对应时间戳，单位为微秒。
   */
  long getTimeUs(long position);

  /**
   * 返回流中音频数据结束后的位置（字节偏移量），如果未知则返回 {@link C#INDEX_UNSET}。
   */
  long getDataEndPosition();

  /**
   * 返回平均比特率（通常从文件的持续时间和长度中得出），如果未知则返回 {@link C#RATE_UNSET_INT}。
   */
  int getAverageBitrate();

  /** 不支持通过音频数据进行搜索的 {@link Seeker}。 */
  /* package */ class UnseekableSeeker extends SeekMap.Unseekable implements Seeker {

    public UnseekableSeeker() {
      super(/* durationUs= */ C.TIME_UNSET);
    }

    @Override
    public long getTimeUs(long position) {
      return 0;
    }

    @Override
    public long getDataEndPosition() {
      // 由于我们不知道数据结束位置，因此返回未设置的值。注意，返回 0 是无效的。
      return C.INDEX_UNSET;
    }

    @Override
    public int getAverageBitrate() {
      return C.RATE_UNSET_INT;
    }
  }
}