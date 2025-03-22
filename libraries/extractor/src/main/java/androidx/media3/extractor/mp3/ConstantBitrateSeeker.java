package androidx.media3.extractor.mp3;

import androidx.media3.common.C;
import androidx.media3.extractor.ConstantBitrateSeekMap;
import androidx.media3.extractor.MpegAudioUtil;

/**
 * MP3 搜索器，不依赖元数据，假设源文件具有恒定比特率进行搜索。
 */
/* package */ final class ConstantBitrateSeeker extends ConstantBitrateSeekMap implements Seeker {

  private final long firstFramePosition; // 第一帧的位置
  private final int bitrate; // 比特率（单位：bps）
  private final int frameSize; // 帧大小（单位：字节）
  private final boolean allowSeeksIfLengthUnknown; // 当长度未知时是否允许搜索
  private final long dataEndPosition; // 数据结束位置

  /**
   * 构造一个实例。
   *
   * @param inputLength 流的长度（单位：字节），如果未知则为 {@link C#LENGTH_UNSET}。
   * @param firstFramePosition 流中第一帧的位置。
   * @param mpegAudioHeader 与第一帧关联的 MPEG 音频头。
   * @param allowSeeksIfLengthUnknown 当内容长度未知时是否允许搜索。
   */
  public ConstantBitrateSeeker(
      long inputLength,
      long firstFramePosition,
      MpegAudioUtil.Header mpegAudioHeader,
      boolean allowSeeksIfLengthUnknown) {
    // 将搜索器的帧大小设置为第一帧的大小（即使某些恒定比特率流由于填充而具有可变帧大小），
    // 以避免对恒定帧大小流重新同步的需要。
    this(
        inputLength,
        firstFramePosition,
        mpegAudioHeader.bitrate,
        mpegAudioHeader.frameSize,
        allowSeeksIfLengthUnknown);
  }

  /** 参见 {@link ConstantBitrateSeekMap#ConstantBitrateSeekMap(long, long, int, int, boolean)}。 */
  public ConstantBitrateSeeker(
      long inputLength,
      long firstFramePosition,
      int bitrate,
      int frameSize,
      boolean allowSeeksIfLengthUnknown) {
    super(inputLength, firstFramePosition, bitrate, frameSize, allowSeeksIfLengthUnknown);
    this.firstFramePosition = firstFramePosition;
    this.bitrate = bitrate;
    this.frameSize = frameSize;
    this.allowSeeksIfLengthUnknown = allowSeeksIfLengthUnknown;
    dataEndPosition = inputLength != C.LENGTH_UNSET ? inputLength : C.INDEX_UNSET;
  }

  @Override
  public long getTimeUs(long position) {
    return getTimeUsAtPosition(position); // 根据位置返回对应的时间（单位：微秒）
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
   * 返回一个新的 ConstantBitrateSeeker 实例，具有新的数据结束位置。
   *
   * @param dataEndPosition 新的数据结束位置。
   * @return 新的 ConstantBitrateSeeker 实例。
   */
  public ConstantBitrateSeeker copyWithNewDataEndPosition(long dataEndPosition) {
    return new ConstantBitrateSeeker(
        /* inputLength= */ dataEndPosition,
        firstFramePosition,
        bitrate,
        frameSize,
        allowSeeksIfLengthUnknown);
  }
}