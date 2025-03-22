package androidx.media3.extractor.mp3;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.SeekPoint;

/** 使用 Xing 头中的元数据进行 MP3 搜索的 Seeker。 */
/* package */ final class XingSeeker implements Seeker {

  private static final String TAG = "XingSeeker";

  /**
   * 返回一个用于在流中搜索的 {@link XingSeeker}，如果所需信息存在的话。如果不存在，则返回 {@code null}。
   * 返回时，{@code frame} 的位置未指定，因此调用者应重置它。
   *
   * @param xingFrame 从该音频帧中解析的 Xing 数据。
   * @param position 该帧在流中的起始位置。
   * @return 用于在流中搜索的 {@link XingSeeker}，如果所需信息不存在，则返回 {@code null}。
   */
  @Nullable
  public static XingSeeker create(XingFrame xingFrame, long position) {
    long durationUs = xingFrame.computeDurationUs(); // 计算持续时间
    if (durationUs == C.TIME_UNSET) {
      return null; // 如果持续时间无效，返回 null
    }
    if (xingFrame.dataSize == C.LENGTH_UNSET || xingFrame.tableOfContents == null) {
      // 如果数据大小或目录表缺失，则流不可搜索。
      return new XingSeeker(
          position, xingFrame.header.frameSize, durationUs, xingFrame.header.bitrate);
    }
    return new XingSeeker(
        position,
        xingFrame.header.frameSize,
        durationUs,
        xingFrame.header.bitrate,
        xingFrame.dataSize,
        xingFrame.tableOfContents);
  }

  private final long dataStartPosition; // 数据起始位置
  private final int xingFrameSize; // Xing 帧大小
  private final long durationUs; // 持续时间
  private final int bitrate; // 比特率

  /** 数据大小，包括 XING 帧。 */
  private final long dataSize;

  private final long dataEndPosition; // 数据结束位置

  /**
   * 目录表条目范围为 [0, 255]，但为了方便存储为长整型。如果头信息中缺少目录表，则为 null，此时不支持搜索。
   */
  @Nullable private final long[] tableOfContents;

  private XingSeeker(long dataStartPosition, int xingFrameSize, long durationUs, int bitrate) {
    this(
        dataStartPosition,
        xingFrameSize,
        durationUs,
        bitrate,
        /* dataSize= */ C.LENGTH_UNSET,
        /* tableOfContents= */ null);
  }

  private XingSeeker(
      long dataStartPosition,
      int xingFrameSize,
      long durationUs,
      int bitrate,
      long dataSize,
      @Nullable long[] tableOfContents) {
    this.dataStartPosition = dataStartPosition;
    this.xingFrameSize = xingFrameSize;
    this.durationUs = durationUs;
    this.bitrate = bitrate;
    this.dataSize = dataSize;
    this.tableOfContents = tableOfContents;
    dataEndPosition = dataSize == C.LENGTH_UNSET ? C.INDEX_UNSET : dataStartPosition + dataSize; // 计算数据结束位置
  }

  @Override
  public boolean isSeekable() {
    return tableOfContents != null; // 如果目录表存在，则支持搜索
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    if (!isSeekable()) {
      return new SeekPoints(new SeekPoint(0, dataStartPosition + xingFrameSize)); // 如果不支持搜索，返回默认搜索点
    }
    timeUs = Util.constrainValue(timeUs, 0, durationUs); // 约束时间在有效范围内
    double percent = (timeUs * 100d) / durationUs; // 计算时间百分比
    double scaledPosition;
    if (percent <= 0) {
      scaledPosition = 0; // 如果百分比小于等于 0，位置为 0
    } else if (percent >= 100) {
      scaledPosition = 256; // 如果百分比大于等于 100，位置为 256
    } else {
      int prevTableIndex = (int) percent; // 获取前一个目录表索引
      long[] tableOfContents = Assertions.checkStateNotNull(this.tableOfContents);
      double prevScaledPosition = tableOfContents[prevTableIndex]; // 获取前一个缩放位置
      double nextScaledPosition = prevTableIndex == 99 ? 256 : tableOfContents[prevTableIndex + 1]; // 获取下一个缩放位置
      // 在两个缩放位置之间进行线性插值
      double interpolateFraction = percent - prevTableIndex;
      scaledPosition =
          prevScaledPosition + (interpolateFraction * (nextScaledPosition - prevScaledPosition));
    }
    long positionOffset = Math.round((scaledPosition / 256) * dataSize); // 计算位置偏移量
    // 确保返回的位置跳过包含 XING 头的帧
    positionOffset = Util.constrainValue(positionOffset, xingFrameSize, dataSize - 1);
    return new SeekPoints(new SeekPoint(timeUs, dataStartPosition + positionOffset)); // 返回搜索点
  }

  @Override
  public long getTimeUs(long position) {
    long positionOffset = position - dataStartPosition; // 计算位置偏移量
    if (!isSeekable() || positionOffset <= xingFrameSize) {
      return 0L; // 如果不支持搜索或位置偏移量小于等于 Xing 帧大小，返回 0
    }
    long[] tableOfContents = Assertions.checkStateNotNull(this.tableOfContents);
    double scaledPosition = (positionOffset * 256d) / dataSize; // 计算缩放位置
    int prevTableIndex = Util.binarySearchFloor(tableOfContents, (long) scaledPosition, true, true); // 查找前一个目录表索引
    long prevTimeUs = getTimeUsForTableIndex(prevTableIndex); // 获取前一个时间
    long prevScaledPosition = tableOfContents[prevTableIndex]; // 获取前一个缩放位置
    long nextTimeUs = getTimeUsForTableIndex(prevTableIndex + 1); // 获取下一个时间
    long nextScaledPosition = prevTableIndex == 99 ? 256 : tableOfContents[prevTableIndex + 1]; // 获取下一个缩放位置
    // 在两个目录表条目之间进行线性插值
    double interpolateFraction =
        prevScaledPosition == nextScaledPosition
            ? 0
            : ((scaledPosition - prevScaledPosition) / (nextScaledPosition - prevScaledPosition));
    return prevTimeUs + Math.round(interpolateFraction * (nextTimeUs - prevTimeUs)); // 返回插值时间
  }

  @Override
  public long getDurationUs() {
    return durationUs; // 返回持续时间
  }

  @Override
  public long getDataEndPosition() {
    return dataEndPosition; // 返回数据结束位置
  }

  @Override
  public int getAverageBitrate() {
    return bitrate; // 返回平均比特率
  }

  /**
   * 返回给定目录表索引对应的时间（以微秒为单位）。
   *
   * @param tableIndex 目录表索引，范围为 [0, 100]。
   * @return 对应的时间（以微秒为单位）。
   */
  private long getTimeUsForTableIndex(int tableIndex) {
    return (durationUs * tableIndex) / 100; // 计算时间
  }
}