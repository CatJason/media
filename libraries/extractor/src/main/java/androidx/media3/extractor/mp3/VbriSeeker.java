package androidx.media3.extractor.mp3;

import static java.lang.Math.max;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.MpegAudioUtil;
import androidx.media3.extractor.SeekPoint;

/** 使用 VBRI 头中的元数据进行 MP3 搜索的 Seeker。 */
/* package */ final class VbriSeeker implements Seeker {

  private static final String TAG = "VbriSeeker";

  /**
   * 返回一个用于在流中搜索的 {@link VbriSeeker}，如果所需信息存在的话。如果不存在，则返回 {@code null}。
   * 返回时，{@code frame} 的位置未指定，因此调用者应重置它。
   *
   * @param inputLength 流的长度（以字节为单位），如果未知则为 {@link C#LENGTH_UNSET}。
   * @param position 该帧在流中的起始位置。
   * @param mpegAudioHeader 与该帧关联的 MPEG 音频头。
   * @param frame 该音频帧中的数据，其位置设置为紧接在 'VBRI' 标签之后。
   * @return 用于在流中搜索的 {@link VbriSeeker}，如果所需信息不存在，则返回 {@code null}。
   */
  @Nullable
  public static VbriSeeker create(
      long inputLength,
      long position,
      MpegAudioUtil.Header mpegAudioHeader,
      ParsableByteArray frame) {
    frame.skipBytes(6); // 跳过 6 个字节
    int bytes = frame.readInt(); // 读取 VBRI 头中的字节数
    long endOfMp3Data = position + mpegAudioHeader.frameSize + bytes; // 计算 MP3 数据的结束位置
    int numFrames = frame.readInt(); // 读取帧数
    if (numFrames <= 0) {
      return null; // 如果帧数无效，返回 null
    }
    int sampleRate = mpegAudioHeader.sampleRate; // 获取采样率
    long durationUs =
        Util.scaleLargeTimestamp(
            numFrames, C.MICROS_PER_SECOND * (sampleRate >= 32000 ? 1152 : 576), sampleRate); // 计算持续时间
    int entryCount = frame.readUnsignedShort(); // 读取条目数
    int scale = frame.readUnsignedShort(); // 读取比例因子
    int entrySize = frame.readUnsignedShort(); // 读取每个条目的大小
    frame.skipBytes(2); // 跳过 2 个字节

    position += mpegAudioHeader.frameSize; // 更新位置
    // 读取目录条目
    long[] timesUs = new long[entryCount];
    long[] positions = new long[entryCount];
    for (int index = 0; index < entryCount; index++) {
      timesUs[index] = (index * durationUs) / entryCount; // 计算每个条目的时间
      positions[index] = position; // 记录每个条目的位置
      int segmentSize;
      switch (entrySize) {
        case 1:
          segmentSize = frame.readUnsignedByte(); // 读取 1 字节的段大小
          break;
        case 2:
          segmentSize = frame.readUnsignedShort(); // 读取 2 字节的段大小
          break;
        case 3:
          segmentSize = frame.readUnsignedInt24(); // 读取 3 字节的段大小
          break;
        case 4:
          segmentSize = frame.readUnsignedIntToInt(); // 读取 4 字节的段大小
          break;
        default:
          return null; // 如果条目大小无效，返回 null
      }
      position += segmentSize * ((long) scale); // 更新位置
    }
    if (inputLength != C.LENGTH_UNSET && inputLength != endOfMp3Data) {
      Log.w(TAG, "VBRI 数据大小不匹配: " + inputLength + ", " + endOfMp3Data); // 记录警告
    }
    if (endOfMp3Data != position) {
      Log.w(
          TAG,
          "VBRI 字节与目录不匹配（使用最大值）: "
              + endOfMp3Data
              + ", "
              + position
              + "\n搜索将不准确。"); // 记录警告
      endOfMp3Data = max(endOfMp3Data, position); // 使用最大值
    }

    return new VbriSeeker(timesUs, positions, durationUs, endOfMp3Data, mpegAudioHeader.bitrate); // 返回 VbriSeeker
  }

  private final long[] timesUs; // 时间数组
  private final long[] positions; // 位置数组
  private final long durationUs; // 持续时间
  private final long dataEndPosition; // 数据结束位置
  private final int bitrate; // 比特率

  private VbriSeeker(
      long[] timesUs, long[] positions, long durationUs, long dataEndPosition, int bitrate) {
    this.timesUs = timesUs;
    this.positions = positions;
    this.durationUs = durationUs;
    this.dataEndPosition = dataEndPosition;
    this.bitrate = bitrate;
  }

  @Override
  public boolean isSeekable() {
    return true; // 支持搜索
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    int tableIndex = Util.binarySearchFloor(timesUs, timeUs, true, true); // 查找时间对应的索引
    SeekPoint seekPoint = new SeekPoint(timesUs[tableIndex], positions[tableIndex]); // 创建搜索点
    if (seekPoint.timeUs >= timeUs || tableIndex == timesUs.length - 1) {
      return new SeekPoints(seekPoint); // 返回单个搜索点
    } else {
      SeekPoint nextSeekPoint = new SeekPoint(timesUs[tableIndex + 1], positions[tableIndex + 1]); // 创建下一个搜索点
      return new SeekPoints(seekPoint, nextSeekPoint); // 返回两个搜索点
    }
  }

  @Override
  public long getTimeUs(long position) {
    return timesUs[Util.binarySearchFloor(positions, position, true, true)]; // 根据位置获取时间
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
}