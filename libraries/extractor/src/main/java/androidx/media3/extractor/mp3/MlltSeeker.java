package androidx.media3.extractor.mp3;

import android.util.Pair;
import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.SeekPoint;
import androidx.media3.extractor.metadata.id3.MlltFrame;

/** 使用 {@link MlltFrame} 元数据的 MP3 搜索器。 */
/* package */ final class MlltSeeker implements Seeker {

  /**
   * 返回一个用于在流中搜索的 {@link MlltSeeker}。
   *
   * @param firstFramePosition 流中第一帧的起始位置。
   * @param mlltFrame 包含搜索元数据的 MLLT 帧。
   * @param durationUs 流的持续时间（单位：微秒），如果未知则为 {@link C#TIME_UNSET}。
   * @return 一个用于在流中搜索的 {@link MlltSeeker}。
   */
  public static MlltSeeker create(long firstFramePosition, MlltFrame mlltFrame, long durationUs) {
    int referenceCount = mlltFrame.bytesDeviations.length; // 参考点的数量
    long[] referencePositions = new long[1 + referenceCount]; // 参考点的位置数组
    long[] referenceTimesMs = new long[1 + referenceCount]; // 参考点的时间数组（单位：毫秒）
    referencePositions[0] = firstFramePosition; // 第一个参考点的位置
    referenceTimesMs[0] = 0; // 第一个参考点的时间
    long position = firstFramePosition; // 当前的位置
    long timeMs = 0; // 当前的时间
    for (int i = 1; i <= referenceCount; i++) {
      position += mlltFrame.bytesBetweenReference + mlltFrame.bytesDeviations[i - 1]; // 计算下一个参考点的位置
      timeMs += mlltFrame.millisecondsBetweenReference + mlltFrame.millisecondsDeviations[i - 1]; // 计算下一个参考点的时间
      referencePositions[i] = position; // 记录参考点的位置
      referenceTimesMs[i] = timeMs; // 记录参考点的时间
    }
    return new MlltSeeker(referencePositions, referenceTimesMs, durationUs); // 创建 MlltSeeker 实例
  }

  private final long[] referencePositions; // 参考点的位置数组
  private final long[] referenceTimesMs; // 参考点的时间数组（单位：毫秒）
  private final long durationUs; // 流的持续时间（单位：微秒）

  private MlltSeeker(long[] referencePositions, long[] referenceTimesMs, long durationUs) {
    this.referencePositions = referencePositions;
    this.referenceTimesMs = referenceTimesMs;
    // 如果流的持续时间未知，则使用最后一个参考点的时间作为持续时间，
    // 因为在流末尾外推可变比特率可能会产生较大误差。
    this.durationUs =
        durationUs != C.TIME_UNSET
            ? durationUs
            : Util.msToUs(referenceTimesMs[referenceTimesMs.length - 1]);
  }

  @Override
  public boolean isSeekable() {
    return true; // 返回是否可搜索
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    timeUs = Util.constrainValue(timeUs, 0, durationUs); // 约束时间在有效范围内
    Pair<Long, Long> timeMsAndPosition =
        linearlyInterpolate(Util.usToMs(timeUs), referenceTimesMs, referencePositions); // 线性插值获取位置
    timeUs = Util.msToUs(timeMsAndPosition.first); // 将时间转换回微秒
    long position = timeMsAndPosition.second; // 获取位置
    return new SeekPoints(new SeekPoint(timeUs, position)); // 返回搜索点
  }

  @Override
  public long getTimeUs(long position) {
    Pair<Long, Long> positionAndTimeMs =
        linearlyInterpolate(position, referencePositions, referenceTimesMs); // 线性插值获取时间
    return Util.msToUs(positionAndTimeMs.second); // 将时间转换回微秒
  }

  @Override
  public long getDurationUs() {
    return durationUs; // 返回流的持续时间
  }

  /**
   * 给定一组参考点的 x 轴和 y 轴坐标，以及一个 x 轴值，通过线性插值计算对应的 y 轴值。
   *
   * @param x 需要计算 y 轴值的 x 轴值。
   * @param xReferences 参考点的 x 轴坐标。
   * @param yReferences 参考点的 y 轴坐标。
   * @return 线性插值计算出的 y 轴值。
   */
  private static Pair<Long, Long> linearlyInterpolate(
      long x, long[] xReferences, long[] yReferences) {
    int previousReferenceIndex =
        Util.binarySearchFloor(xReferences, x, /* inclusive= */ true, /* stayInBounds= */ true); // 查找最近的参考点索引
    long xPreviousReference = xReferences[previousReferenceIndex]; // 获取前一个参考点的 x 轴值
    long yPreviousReference = yReferences[previousReferenceIndex]; // 获取前一个参考点的 y 轴值
    int nextReferenceIndex = previousReferenceIndex + 1; // 下一个参考点的索引
    if (nextReferenceIndex == xReferences.length) {
      return Pair.create(xPreviousReference, yPreviousReference); // 如果超出范围，返回前一个参考点的值
    } else {
      long xNextReference = xReferences[nextReferenceIndex]; // 获取下一个参考点的 x 轴值
      long yNextReference = yReferences[nextReferenceIndex]; // 获取下一个参考点的 y 轴值
      double proportion =
          xNextReference == xPreviousReference
              ? 0.0
              : ((double) x - xPreviousReference) / (xNextReference - xPreviousReference); // 计算插值比例
      long y = (long) (proportion * (yNextReference - yPreviousReference)) + yPreviousReference; // 计算 y 轴值
      return Pair.create(x, y); // 返回插值结果
    }
  }

  @Override
  public long getDataEndPosition() {
    return C.INDEX_UNSET; // 返回数据结束位置（未知）
  }

  @Override
  public int getAverageBitrate() {
    return C.RATE_UNSET_INT; // 返回平均比特率（未知）
  }
}