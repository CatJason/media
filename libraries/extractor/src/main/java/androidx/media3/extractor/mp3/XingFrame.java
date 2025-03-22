package androidx.media3.extractor.mp3;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.MpegAudioUtil;

/** 表示 LAME Xing 或 Info 帧的类。 */
/* package */ final class XingFrame {

  /** Xing 或 Info 帧的头信息。 */
  public final MpegAudioUtil.Header header;

  /** 帧数，如果头信息中未提供则为 {@link C#LENGTH_UNSET}。 */
  public final long frameCount;

  /**
   * 数据大小，包括 XING 帧，如果头信息中未提供则为 {@link C#LENGTH_UNSET}。
   */
  public final long dataSize;

  /**
   * 在流开头需要跳过的样本数，如果头信息中未提供则为 {@link C#LENGTH_UNSET}。
   */
  public final int encoderDelay;

  /**
   * 在流结尾需要跳过的样本数，如果头信息中未提供则为 {@link C#LENGTH_UNSET}。
   */
  public final int encoderPadding;

  /**
   * 目录表条目范围为 [0, 255]，但为了方便存储为长整型。如果头信息中缺少目录表，则为 null，此时不支持搜索。
   */
  @Nullable public final long[] tableOfContents;

  private XingFrame(
      MpegAudioUtil.Header header,
      long frameCount,
      long dataSize,
      @Nullable long[] tableOfContents,
      int encoderDelay,
      int encoderPadding) {
    this.header = new MpegAudioUtil.Header(header);
    this.frameCount = frameCount;
    this.dataSize = dataSize;
    this.tableOfContents = tableOfContents;
    this.encoderDelay = encoderDelay;
    this.encoderPadding = encoderPadding;
  }

  /**
   * 从 LAME Xing（VBR）或 Info（CBR）帧中解析信息并返回一个 {@link XingFrame}。
   *
   * <p>方法返回时，{@code frame} 中的 {@link ParsableByteArray#getPosition()} 未定义。
   *
   * @param mpegAudioHeader 与该帧关联的 MPEG 音频头。
   * @param frame 该音频帧中的数据，其位置设置为紧接在 'Xing' 或 'Info' 标签之后。
   */
  public static XingFrame parse(MpegAudioUtil.Header mpegAudioHeader, ParsableByteArray frame) {
    int flags = frame.readInt(); // 读取标志位
    int frameCount = (flags & 0x01) != 0 ? frame.readUnsignedIntToInt() : C.LENGTH_UNSET; // 读取帧数
    long dataSize = (flags & 0x02) != 0 ? frame.readUnsignedInt() : C.LENGTH_UNSET; // 读取数据大小

    long[] tableOfContents;
    if ((flags & 0x04) == 0x04) {
      tableOfContents = new long[100]; // 初始化目录表
      for (int i = 0; i < 100; i++) {
        tableOfContents[i] = frame.readUnsignedByte(); // 读取目录表条目
      }
    } else {
      tableOfContents = null; // 如果标志位未设置，目录表为 null
    }

    if ((flags & 0x8) != 0) {
      frame.skipBytes(4); // 跳过质量指示器
    }

    int encoderDelay;
    int encoderPadding;
    // 跳过：版本字符串（9）、修订版和 VBR 方法（1）、低通滤波器（1）、回放增益（8）、
    //       编码标志和 ATH 类型（1）、比特率（1）。
    int bytesToSkipBeforeEncoderDelayAndPadding = 9 + 1 + 1 + 8 + 1 + 1;
    if (frame.bytesLeft() >= bytesToSkipBeforeEncoderDelayAndPadding + 3) {
      frame.skipBytes(bytesToSkipBeforeEncoderDelayAndPadding);
      int encoderDelayAndPadding = frame.readUnsignedInt24(); // 读取编码延迟和填充
      encoderDelay = (encoderDelayAndPadding & 0xFFF000) >> 12; // 提取编码延迟
      encoderPadding = (encoderDelayAndPadding & 0xFFF); // 提取编码填充
    } else {
      encoderDelay = C.LENGTH_UNSET;
      encoderPadding = C.LENGTH_UNSET;
    }

    return new XingFrame(
        mpegAudioHeader, frameCount, dataSize, tableOfContents, encoderDelay, encoderPadding); // 返回 XingFrame
  }

  /**
   * 计算此帧表示的流持续时间（以微秒为单位）。如果帧中未包含足够的信息来计算持续时间，则返回 {@link C#LENGTH_UNSET}。
   */
  // TODO: b/319235116 - 在计算持续时间时处理编码延迟和填充。
  public long computeDurationUs() {
    if (frameCount == C.LENGTH_UNSET || frameCount == 0) {
      // 如果帧数缺失或无效，则无法使用头信息确定持续时间。
      return C.TIME_UNSET;
    }
    // 音频需要开始和结束的 PCM 样本，因此在计算持续时间之前从样本计数中减去 1。
    return Util.sampleCountToDurationUs(
        (frameCount * header.samplesPerFrame) - 1, header.sampleRate); // 计算持续时间
  }
}