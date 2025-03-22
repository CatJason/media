package androidx.media3.extractor.mp4;

import androidx.annotation.Nullable;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.ExtractorInput;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** 用于封装 MP4 文件中单个片段信息的类。 */
/* package */ final class TrackFragment {

  /** 轨道片段头中样本的默认值。 */
  public @MonotonicNonNull DefaultSampleValues header;

  /** 片段起始位置（字节偏移量）。 */
  public long atomPosition;

  /** 片段中包含的数据起始位置（字节偏移量）。 */
  public long dataPosition;

  /** 辅助数据起始位置（字节偏移量）。 */
  public long auxiliaryDataPosition;

  /** 片段中轨道运行（track run）的数量。 */
  public int trunCount;

  /** 片段中样本的总数。 */
  public int sampleCount;

  /** 片段中每个轨道运行（track run）的样本数据起始位置（字节偏移量）。 */
  public long[] trunDataPosition;

  /** 片段中每个轨道运行（track run）包含的样本数量。 */
  public int[] trunLength;

  /** 片段中每个样本的大小。 */
  public int[] sampleSizeTable;

  /** 片段中每个样本的呈现时间（微秒）。 */
  public long[] samplePresentationTimesUs;

  /** 指示哪些样本是关键帧（sync frame）。 */
  public boolean[] sampleIsSyncFrameTable;

  /** 片段是否定义了加密数据。 */
  public boolean definesEncryptionData;

  /**
   * 如果 {@link #definesEncryptionData} 为 true，指示哪些样本使用子样本加密。否则未定义。
   */
  public boolean[] sampleHasSubsampleEncryptionTable;

  /** 片段特定的轨道加密信息。可能为 null。 */
  @Nullable public TrackEncryptionBox trackEncryptionBox;

  /**
   * 如果 {@link #definesEncryptionData} 为 true，包含二进制样本加密数据。否则未定义。
   */
  public final ParsableByteArray sampleEncryptionData;

  /** 是否需要填充 {@link #sampleEncryptionData} 中的实际加密数据。 */
  public boolean sampleEncryptionDataNeedsFill;

  /**
   * 所有片段中定义的样本的总持续时间（微秒），包括当前片段，如果 {@link #nextFragmentDecodeTimeIncludesMoov} 为 true，
   * 则还包括 moov 原子中定义的样本的持续时间。
   */
  public long nextFragmentDecodeTime;

  /**
   * {@link #nextFragmentDecodeTime} 是否包括 moov 原子中定义的样本的持续时间。
   */
  public boolean nextFragmentDecodeTimeIncludesMoov;

  public TrackFragment() {
    trunDataPosition = new long[0];
    trunLength = new int[0];
    sampleSizeTable = new int[0];
    samplePresentationTimesUs = new long[0];
    sampleIsSyncFrameTable = new boolean[0];
    sampleHasSubsampleEncryptionTable = new boolean[0];
    sampleEncryptionData = new ParsableByteArray();
  }

  /**
   * 重置片段。
   *
   * <p>{@link #sampleCount} 和 {@link #nextFragmentDecodeTime} 被设置为 0，{@link #definesEncryptionData} 和
   * {@link #sampleEncryptionDataNeedsFill} 被设置为 false，{@link #trackEncryptionBox} 被设置为 null。
   */
  public void reset() {
    trunCount = 0;
    nextFragmentDecodeTime = 0;
    nextFragmentDecodeTimeIncludesMoov = false;
    definesEncryptionData = false;
    sampleEncryptionDataNeedsFill = false;
    trackEncryptionBox = null;
  }

  /**
   * 为指定数量的样本配置片段。
   *
   * <p>片段的 {@link #sampleCount} 被设置为指定的样本数量，并且如果必要，内部表会被调整大小，以确保至少能够容纳该数量的样本。
   *
   * @param sampleCount 新运行中的样本数量。
   */
  public void initTables(int trunCount, int sampleCount) {
    this.trunCount = trunCount;
    this.sampleCount = sampleCount;
    if (trunLength.length < trunCount) {
      trunDataPosition = new long[trunCount];
      trunLength = new int[trunCount];
    }
    if (sampleSizeTable.length < sampleCount) {
      // 将表的大小调整为比所需大小大 25%，以减少未来调整大小的可能性。25% 的选择相对随意。
      int tableSize = (sampleCount * 125) / 100;
      sampleSizeTable = new int[tableSize];
      samplePresentationTimesUs = new long[tableSize];
      sampleIsSyncFrameTable = new boolean[tableSize];
      sampleHasSubsampleEncryptionTable = new boolean[tableSize];
    }
  }

  /**
   * 配置片段以定义指定长度的加密数据。
   *
   * <p>{@link #definesEncryptionData} 被设置为 true，并且 {@link ParsableByteArray#limit()} 的
   * {@link #sampleEncryptionData} 被设置为指定的长度。
   *
   * @param length 加密数据的长度（字节）。
   */
  public void initEncryptionData(int length) {
    sampleEncryptionData.reset(length);
    definesEncryptionData = true;
    sampleEncryptionDataNeedsFill = true;
  }

  /**
   * 从提供的输入中填充 {@link #sampleEncryptionData}。
   *
   * @param input 用于读取加密数据的 {@link ExtractorInput}。
   */
  public void fillEncryptionData(ExtractorInput input) throws IOException {
    input.readFully(sampleEncryptionData.getData(), 0, sampleEncryptionData.limit());
    sampleEncryptionData.setPosition(0);
    sampleEncryptionDataNeedsFill = false;
  }

  /**
   * 从提供的源中填充 {@link #sampleEncryptionData}。
   *
   * @param source 用于读取加密数据的源。
   */
  public void fillEncryptionData(ParsableByteArray source) {
    source.readBytes(sampleEncryptionData.getData(), 0, sampleEncryptionData.limit());
    sampleEncryptionData.setPosition(0);
    sampleEncryptionDataNeedsFill = false;
  }

  /**
   * 返回样本的呈现时间戳（微秒）。
   *
   * @param index 样本索引。
   * @return 该样本的呈现时间戳（微秒）。
   */
  public long getSamplePresentationTimeUs(int index) {
    return samplePresentationTimesUs[index];
  }

  /** 返回给定索引的样本是否具有子样本加密表。 */
  public boolean sampleHasSubsampleEncryptionTable(int index) {
    return definesEncryptionData && sampleHasSubsampleEncryptionTable[index];
  }
}