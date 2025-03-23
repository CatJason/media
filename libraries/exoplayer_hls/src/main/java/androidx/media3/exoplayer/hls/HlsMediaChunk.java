package androidx.media3.exoplayer.hls;

import static androidx.media3.datasource.DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UriUtil;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceUtil;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.source.chunk.MediaChunk;
import androidx.media3.exoplayer.upstream.CmcdData;
import androidx.media3.extractor.DefaultExtractorInput;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.metadata.id3.Id3Decoder;
import androidx.media3.extractor.metadata.id3.PrivFrame;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** An HLS {@link MediaChunk}. */
/* package */ final class HlsMediaChunk extends MediaChunk {

  /**
   * 创建一个新实例。
   *
   * @param extractorFactory 用于获取 {@link HlsMediaChunkExtractor} 的 {@link HlsExtractorFactory}。
   * @param dataSource 数据应从中加载的源。
   * @param format 块的格式。
   * @param startOfPlaylistInPeriodUs 播放列表在周期中的位置，以微秒为单位。
   * @param mediaPlaylist 从中获取此块的媒体播放列表。
   * @param segmentBaseHolder 片段持有者。
   * @param playlistUrl 从中获取此块的播放列表的 URL。
   * @param muxedCaptionFormats 多路复用的字幕 {@link Format} 列表。如果多变量播放列表中没有隐藏字幕信息，则为 null。
   * @param trackSelectionReason 参见 {@link #trackSelectionReason}。
   * @param trackSelectionData 参见 {@link #trackSelectionData}。
   * @param isPrimaryTimestampSource 如果块可以初始化时间戳调整器，则为 true。
   * @param timestampAdjusterProvider 用于获取 {@link TimestampAdjuster} 的提供者。
   * @param timestampAdjusterInitializationTimeoutMs 加载线程等待时间戳调整器初始化的超时时间（以毫秒为单位）。零超时表示无限超时。
   * @param previousChunk 在此块之前的 {@link HlsMediaChunk}。可能为 null。
   * @param mediaSegmentKey 媒体片段的解密密钥（如果完全加密）。否则为 null。
   * @param initSegmentKey 初始化片段的解密密钥（如果完全加密）。否则为 null。
   * @param shouldSpliceIn 此块的样本是否应拼接到现有样本中。
   * @param cmcdDataFactory 用于生成 {@link CmcdData} 的 {@link CmcdData.Factory}。
   */
  public static HlsMediaChunk createInstance(
      HlsExtractorFactory extractorFactory,
      DataSource dataSource,
      Format format,
      long startOfPlaylistInPeriodUs,
      HlsMediaPlaylist mediaPlaylist,
      HlsChunkSource.SegmentBaseHolder segmentBaseHolder,
      Uri playlistUrl,
      @Nullable List<Format> muxedCaptionFormats,
      @C.SelectionReason int trackSelectionReason,
      @Nullable Object trackSelectionData,
      boolean isPrimaryTimestampSource,
      TimestampAdjusterProvider timestampAdjusterProvider,
      long timestampAdjusterInitializationTimeoutMs,
      @Nullable HlsMediaChunk previousChunk,
      @Nullable byte[] mediaSegmentKey,
      @Nullable byte[] initSegmentKey,
      boolean shouldSpliceIn,
      PlayerId playerId,
      @Nullable CmcdData.Factory cmcdDataFactory) {
    // Media segment.
    HlsMediaPlaylist.SegmentBase mediaSegment = segmentBaseHolder.segmentBase;
    DataSpec dataSpec =
        new DataSpec.Builder()
            .setUri(UriUtil.resolveToUri(mediaPlaylist.baseUri, mediaSegment.url))
            .setPosition(mediaSegment.byteRangeOffset)
            .setLength(mediaSegment.byteRangeLength)
            .setFlags(segmentBaseHolder.isPreload ? FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED : 0)
            .build();
    if (cmcdDataFactory != null) {
      CmcdData cmcdData =
          cmcdDataFactory.setChunkDurationUs(mediaSegment.durationUs).createCmcdData();
      dataSpec = cmcdData.addToDataSpec(dataSpec);
    }

    boolean mediaSegmentEncrypted = mediaSegmentKey != null;
    @Nullable
    byte[] mediaSegmentIv =
        mediaSegmentEncrypted
            ? getEncryptionIvArray(Assertions.checkNotNull(mediaSegment.encryptionIV))
            : null;
    DataSource mediaDataSource = buildDataSource(dataSource, mediaSegmentKey, mediaSegmentIv);

    // Init segment.
    HlsMediaPlaylist.Segment initSegment = mediaSegment.initializationSegment;
    DataSpec initDataSpec = null;
    boolean initSegmentEncrypted = false;
    @Nullable DataSource initDataSource = null;
    if (initSegment != null) {
      initSegmentEncrypted = initSegmentKey != null;
      @Nullable
      byte[] initSegmentIv =
          initSegmentEncrypted
              ? getEncryptionIvArray(Assertions.checkNotNull(initSegment.encryptionIV))
              : null;
      Uri initSegmentUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, initSegment.url);
      initDataSpec =
          new DataSpec.Builder()
              .setUri(initSegmentUri)
              .setPosition(initSegment.byteRangeOffset)
              .setLength(initSegment.byteRangeLength)
              .build();
      if (cmcdDataFactory != null) {
        CmcdData cmcdData =
            cmcdDataFactory
                .setObjectType(CmcdData.Factory.OBJECT_TYPE_INIT_SEGMENT)
                .createCmcdData();
        initDataSpec = cmcdData.addToDataSpec(initDataSpec);
      }

      initDataSource = buildDataSource(dataSource, initSegmentKey, initSegmentIv);
    }

    long segmentStartTimeInPeriodUs = startOfPlaylistInPeriodUs + mediaSegment.relativeStartTimeUs;
    long segmentEndTimeInPeriodUs = segmentStartTimeInPeriodUs + mediaSegment.durationUs;
    int discontinuitySequenceNumber =
        mediaPlaylist.discontinuitySequence + mediaSegment.relativeDiscontinuitySequence;

    @Nullable HlsMediaChunkExtractor previousExtractor = null;
    Id3Decoder id3Decoder;
    ParsableByteArray scratchId3Data;

    if (previousChunk != null) {
      boolean isSameInitData =
          initDataSpec == previousChunk.initDataSpec
              || (initDataSpec != null
                  && previousChunk.initDataSpec != null
                  && initDataSpec.uri.equals(previousChunk.initDataSpec.uri)
                  && initDataSpec.position == previousChunk.initDataSpec.position);
      boolean isFollowingChunk =
          playlistUrl.equals(previousChunk.playlistUrl) && previousChunk.loadCompleted;
      id3Decoder = previousChunk.id3Decoder;
      scratchId3Data = previousChunk.scratchId3Data;
      previousExtractor =
          isSameInitData
                  && isFollowingChunk
                  && !previousChunk.extractorInvalidated
                  && previousChunk.discontinuitySequenceNumber == discontinuitySequenceNumber
              ? previousChunk.extractor
              : null;
    } else {
      id3Decoder = new Id3Decoder();
      scratchId3Data = new ParsableByteArray(Id3Decoder.ID3_HEADER_LENGTH);
    }
    return new HlsMediaChunk(
        extractorFactory,
        mediaDataSource,
        dataSpec,
        format,
        mediaSegmentEncrypted,
        initDataSource,
        initDataSpec,
        initSegmentEncrypted,
        playlistUrl,
        muxedCaptionFormats,
        trackSelectionReason,
        trackSelectionData,
        segmentStartTimeInPeriodUs,
        segmentEndTimeInPeriodUs,
        segmentBaseHolder.mediaSequence,
        segmentBaseHolder.partIndex,
        /* isPublished= */ !segmentBaseHolder.isPreload,
        discontinuitySequenceNumber,
        mediaSegment.hasGapTag,
        isPrimaryTimestampSource,
        /* timestampAdjuster= */ timestampAdjusterProvider.getAdjuster(discontinuitySequenceNumber),
        timestampAdjusterInitializationTimeoutMs,
        mediaSegment.drmInitData,
        previousExtractor,
        id3Decoder,
        scratchId3Data,
        shouldSpliceIn,
        playerId);
  }

  /**
   * 返回是否应将新 HLS 媒体块的样本拼接到现有样本中。
   *
   * @param previousChunk 之前存在的媒体块，如果新块是队列中的第一个块，则为 null。
   * @param playlistUrl 从中获取新块的播放列表的 URL。
   * @param mediaPlaylist 包含新块的 {@link HlsMediaPlaylist}。
   * @param segmentBaseHolder 包含新块信息的 {@link HlsChunkSource.SegmentBaseHolder}。
   * @param startOfPlaylistInPeriodUs 播放列表在周期中的开始时间，以微秒为单位。
   * @return 是否应将新块的样本拼接到现有样本中。
   */
  public static boolean shouldSpliceIn(
      @Nullable HlsMediaChunk previousChunk,
      Uri playlistUrl,
      HlsMediaPlaylist mediaPlaylist,
      HlsChunkSource.SegmentBaseHolder segmentBaseHolder,
      long startOfPlaylistInPeriodUs) {
    if (previousChunk == null) {
      // 第一个块不需要拼接。
      return false;
    }
    if (playlistUrl.equals(previousChunk.playlistUrl) && previousChunk.loadCompleted) {
      // 在完全加载上一个块后（即加载未被取消或失败），继续在同一播放列表中加载下一个块总是可行的。
      return false;
    }
    // 更换播放列表或在块取消/失败后继续，需要独立且不重叠的片段以避免拼接。
    long segmentStartTimeInPeriodUs =
        startOfPlaylistInPeriodUs + segmentBaseHolder.segmentBase.relativeStartTimeUs;
    return !isIndependent(segmentBaseHolder, mediaPlaylist)
        || segmentStartTimeInPeriodUs < previousChunk.endTimeUs;
  }

  public static final String PRIV_TIMESTAMP_FRAME_OWNER =
      "com.apple.streaming.transportStreamTimestamp";

  private static final AtomicInteger uidSource = new AtomicInteger();

  /** 块的唯一标识符。 */
  public final int uid;

  /** 块的间断序列号。 */
  public final int discontinuitySequenceNumber;

  /** 从中获取此块的播放列表的 URL。 */
  public final Uri playlistUrl;

  /** 是否应将此块的样本拼接到现有样本中。 */
  public final boolean shouldSpliceIn;

  /** 部分索引，如果块是完整片段则为 {@link C#INDEX_UNSET} */
  public final int partIndex;

  @Nullable private final DataSource initDataSource;
  @Nullable private final DataSpec initDataSpec;
  @Nullable private final HlsMediaChunkExtractor previousExtractor;

  private final boolean isPrimaryTimestampSource;
  private final boolean hasGapTag;
  private final TimestampAdjuster timestampAdjuster;
  private final HlsExtractorFactory extractorFactory;
  @Nullable private final List<Format> muxedCaptionFormats;
  @Nullable private final DrmInitData drmInitData;
  private final Id3Decoder id3Decoder;
  private final ParsableByteArray scratchId3Data;
  private final boolean mediaSegmentEncrypted;
  private final boolean initSegmentEncrypted;
  private final PlayerId playerId;
  private final long timestampAdjusterInitializationTimeoutMs;

  private @MonotonicNonNull HlsMediaChunkExtractor extractor;
  private @MonotonicNonNull HlsSampleStreamWrapper output;
  // 如果 initDataLoadRequired 为 true，则 nextLoadPosition 指向初始化片段。
  // 否则，nextLoadPosition 指向媒体片段。
  private int nextLoadPosition;
  private boolean initDataLoadRequired;
  private volatile boolean loadCanceled;
  private boolean loadCompleted;
  private ImmutableList<Integer> sampleQueueFirstSampleIndices;
  private boolean extractorInvalidated;
  private boolean isPublished;

  private HlsMediaChunk(
      HlsExtractorFactory extractorFactory,
      DataSource mediaDataSource,
      DataSpec dataSpec,
      Format format,
      boolean mediaSegmentEncrypted,
      @Nullable DataSource initDataSource,
      @Nullable DataSpec initDataSpec,
      boolean initSegmentEncrypted,
      Uri playlistUrl,
      @Nullable List<Format> muxedCaptionFormats,
      @C.SelectionReason int trackSelectionReason,
      @Nullable Object trackSelectionData,
      long startTimeUs,
      long endTimeUs,
      long chunkMediaSequence,
      int partIndex,
      boolean isPublished,
      int discontinuitySequenceNumber,
      boolean hasGapTag,
      boolean isPrimaryTimestampSource,
      TimestampAdjuster timestampAdjuster,
      long timestampAdjusterInitializationTimeoutMs,
      @Nullable DrmInitData drmInitData,
      @Nullable HlsMediaChunkExtractor previousExtractor,
      Id3Decoder id3Decoder,
      ParsableByteArray scratchId3Data,
      boolean shouldSpliceIn,
      PlayerId playerId) {
    super(
        mediaDataSource,
        dataSpec,
        format,
        trackSelectionReason,
        trackSelectionData,
        startTimeUs,
        endTimeUs,
        chunkMediaSequence);
    this.mediaSegmentEncrypted = mediaSegmentEncrypted;
    this.partIndex = partIndex;
    this.isPublished = isPublished;
    this.discontinuitySequenceNumber = discontinuitySequenceNumber;
    this.initDataSpec = initDataSpec;
    this.initDataSource = initDataSource;
    this.initDataLoadRequired = initDataSpec != null;
    this.initSegmentEncrypted = initSegmentEncrypted;
    this.playlistUrl = playlistUrl;
    this.isPrimaryTimestampSource = isPrimaryTimestampSource;
    this.timestampAdjuster = timestampAdjuster;
    this.timestampAdjusterInitializationTimeoutMs = timestampAdjusterInitializationTimeoutMs;
    this.hasGapTag = hasGapTag;
    this.extractorFactory = extractorFactory;
    this.muxedCaptionFormats = muxedCaptionFormats;
    this.drmInitData = drmInitData;
    this.previousExtractor = previousExtractor;
    this.id3Decoder = id3Decoder;
    this.scratchId3Data = scratchId3Data;
    this.shouldSpliceIn = shouldSpliceIn;
    this.playerId = playerId;
    sampleQueueFirstSampleIndices = ImmutableList.of();
    uid = uidSource.getAndIncrement();
  }

  /**
   * 初始化块以进行加载。
   *
   * @param output 将接收加载样本的 {@link HlsSampleStreamWrapper}。
   * @param sampleQueueWriteIndices 输出中现有样本队列的当前写入索引。
   */
  public void init(HlsSampleStreamWrapper output, ImmutableList<Integer> sampleQueueWriteIndices) {
    this.output = output;
    this.sampleQueueFirstSampleIndices = sampleQueueWriteIndices;
  }

  /**
   * 返回此块在输出中指定样本队列中的第一个样本索引。
   *
   * <p>如果 {@link #shouldSpliceIn} 为 true，则不得使用此方法。
   *
   * @param sampleQueueIndex 输出中样本队列的索引。
   * @return 此块在指定样本队列中的第一个样本索引。
   */
  public int getFirstSampleIndex(int sampleQueueIndex) {
    Assertions.checkState(!shouldSpliceIn);
    if (sampleQueueIndex >= sampleQueueFirstSampleIndices.size()) {
      // 该样本队列由此块或后续块创建。
      return 0;
    }
    return sampleQueueFirstSampleIndices.get(sampleQueueIndex);
  }

  /** 阻止提取器被后续的媒体块重用。 */
  public void invalidateExtractor() {
    extractorInvalidated = true;
  }

  @Override
  public boolean isLoadCompleted() {
    return loadCompleted;
  }

  // Loadable implementation

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public void load() throws IOException {
    // output == null means init() hasn't been called.
    Assertions.checkNotNull(output);
    if (extractor == null && previousExtractor != null && previousExtractor.isReusable()) {
      extractor = previousExtractor;
      initDataLoadRequired = false;
    }
    maybeLoadInitData();
    if (!loadCanceled) {
      if (!hasGapTag) {
        loadMedia();
      }
      loadCompleted = !loadCanceled;
    }
  }

  /**
   * 块是否是已发布的块，而不是可能在播放列表更新时更改的预加载提示。
   */
  public boolean isPublished() {
    return isPublished;
  }

  /**
   * 设置媒体块的发布标志，以指示它不是基于播放列表中作为预加载提示的部分。
   */
  public void publish() {
    isPublished = true;
  }

  // Internal methods.

  @RequiresNonNull("output")
  private void maybeLoadInitData() throws IOException {
    if (!initDataLoadRequired) {
      return;
    }
    // initDataLoadRequired =>  initDataSource != null && initDataSpec != null
    Assertions.checkNotNull(initDataSource);
    Assertions.checkNotNull(initDataSpec);
    feedDataToExtractor(
        initDataSource,
        initDataSpec,
        initSegmentEncrypted,
        /* initializeTimestampAdjuster= */ false);
    nextLoadPosition = 0;
    initDataLoadRequired = false;
  }

  @RequiresNonNull("output")
  private void loadMedia() throws IOException {
    feedDataToExtractor(
        dataSource, dataSpec, mediaSegmentEncrypted, /* initializeTimestampAdjuster= */ true);
  }

  /**
   * 尝试将给定的 {@code dataSpec} 提供给 {@code this.extractor}。每当操作结束时（无论是由于抛出异常还是操作完成），已读取的字节数将被写入 {@code nextLoadPosition}。
   */
  @RequiresNonNull("output")
  private void feedDataToExtractor(
      DataSource dataSource,
      DataSpec dataSpec,
      boolean dataIsEncrypted,
      boolean initializeTimestampAdjuster)
      throws IOException {
    // 如果我们之前已经将此块的一部分提供给提取器，这次需要跳过它。对于加密内容，我们需要通过源读取数据来跳过，以确保正确解密块的剩余部分。对于未加密内容，我们可以直接请求块的剩余部分。
    DataSpec loadDataSpec;
    boolean skipLoadedBytes;
    if (dataIsEncrypted) {
      loadDataSpec = dataSpec;
      skipLoadedBytes = nextLoadPosition != 0;
    } else {
      loadDataSpec = dataSpec.subrange(nextLoadPosition);
      skipLoadedBytes = false;
    }
    try {
      ExtractorInput input =
          prepareExtraction(dataSource, loadDataSpec, initializeTimestampAdjuster);
      if (skipLoadedBytes) {
        input.skipFully(nextLoadPosition);
      }
      try {
        while (!loadCanceled && extractor.read(input)) {}
      } catch (EOFException e) {
        if ((trackFormat.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0) {
          // 有关为什么我们吞下 trick play 轨道的 EOF 异常的更多信息，请参阅 onTruncatedSegmentParsed 的文档。
          extractor.onTruncatedSegmentParsed();
        } else {
          throw e;
        }
      } finally {
        nextLoadPosition = (int) (input.getPosition() - dataSpec.position);
      }
    } finally {
      DataSourceUtil.closeQuietly(dataSource);
    }
  }

  @RequiresNonNull("output")
  @EnsuresNonNull("extractor")
  private DefaultExtractorInput prepareExtraction(
      DataSource dataSource, DataSpec dataSpec, boolean initializeTimestampAdjuster)
      throws IOException {
    long bytesToRead = dataSource.open(dataSpec);
    if (initializeTimestampAdjuster) {
      try {
        timestampAdjuster.sharedInitializeOrWait(
            isPrimaryTimestampSource, startTimeUs, timestampAdjusterInitializationTimeoutMs);
      } catch (InterruptedException e) {
        throw new InterruptedIOException();
      } catch (TimeoutException e) {
        throw new IOException(e);
      }
    }
    DefaultExtractorInput extractorInput =
        new DefaultExtractorInput(dataSource, dataSpec.position, bytesToRead);

    if (extractor == null) {
      long id3Timestamp = peekId3PrivTimestamp(extractorInput);
      extractorInput.resetPeekPosition();

      extractor =
          previousExtractor != null
              ? previousExtractor.recreate()
              : extractorFactory.createExtractor(
                  dataSpec.uri,
                  trackFormat,
                  muxedCaptionFormats,
                  timestampAdjuster,
                  dataSource.getResponseHeaders(),
                  extractorInput,
                  playerId);
      if (extractor.isPackedAudioExtractor()) {
        output.setSampleOffsetUs(
            id3Timestamp != C.TIME_UNSET
                ? timestampAdjuster.adjustTsTimestamp(id3Timestamp)
                : startTimeUs);
      } else {
        // 如果容器格式在流中中途更改为非打包音频，我们需要重置时间戳偏移量。
        output.setSampleOffsetUs(/* sampleOffsetUs= */ 0L);
      }
      output.onNewExtractor();
      extractor.init(output);
    }
    output.setDrmInitData(drmInitData);
    return extractorInput;
  }

  /**
   * 从 HLS 规范第 20 版第 3.4 节定义的 ID3 PRIV 帧中提取块中第一个样本的呈现时间戳。如果未找到帧，则返回 {@link C#TIME_UNSET}。此方法仅修改提取位置。
   *
   * @param input 用于提取 PRIV 帧的 {@link ExtractorInput}。
   * @return 解析并调整后的时间戳（以微秒为单位）。
   * @throws IOException 如果从输入中提取时发生错误。
   */
  private long peekId3PrivTimestamp(ExtractorInput input) throws IOException {
    input.resetPeekPosition();
    try {
      scratchId3Data.reset(Id3Decoder.ID3_HEADER_LENGTH);
      input.peekFully(scratchId3Data.getData(), 0, Id3Decoder.ID3_HEADER_LENGTH);
    } catch (EOFException e) {
      // 输入长度不足以包含任何 ID3 数据。
      return C.TIME_UNSET;
    }
    int id = scratchId3Data.readUnsignedInt24();
    if (id != Id3Decoder.ID3_TAG) {
      return C.TIME_UNSET;
    }
    scratchId3Data.skipBytes(3); // version(2), flags(1).
    int id3Size = scratchId3Data.readSynchSafeInt();
    int requiredCapacity = id3Size + Id3Decoder.ID3_HEADER_LENGTH;
    if (requiredCapacity > scratchId3Data.capacity()) {
      byte[] data = scratchId3Data.getData();
      scratchId3Data.reset(requiredCapacity);
      System.arraycopy(data, 0, scratchId3Data.getData(), 0, Id3Decoder.ID3_HEADER_LENGTH);
    }
    input.peekFully(scratchId3Data.getData(), Id3Decoder.ID3_HEADER_LENGTH, id3Size);
    Metadata metadata = id3Decoder.decode(scratchId3Data.getData(), id3Size);
    if (metadata == null) {
      return C.TIME_UNSET;
    }
    int metadataLength = metadata.length();
    for (int i = 0; i < metadataLength; i++) {
      Metadata.Entry frame = metadata.get(i);
      if (frame instanceof PrivFrame) {
        PrivFrame privFrame = (PrivFrame) frame;
        if (PRIV_TIMESTAMP_FRAME_OWNER.equals(privFrame.owner)) {
          System.arraycopy(
              privFrame.privateData, 0, scratchId3Data.getData(), 0, 8 /* timestamp size */);
          scratchId3Data.setPosition(0);
          scratchId3Data.setLimit(8);
          // 最高 31 位应为零，但显式将其置零以处理流媒体提供商忘记处理的情况。参见：https://github.com/google/ExoPlayer/pull/3495。
          return scratchId3Data.readLong() & 0x1FFFFFFFFL;
        }
      }
    }
    return C.TIME_UNSET;
  }

  // Internal methods.

  private static byte[] getEncryptionIvArray(String ivString) {
    String trimmedIv;
    if (Ascii.toLowerCase(ivString).startsWith("0x")) {
      trimmedIv = ivString.substring(2);
    } else {
      trimmedIv = ivString;
    }

    byte[] ivData = new BigInteger(trimmedIv, /* radix= */ 16).toByteArray();
    byte[] ivDataWithPadding = new byte[16];
    int offset = ivData.length > 16 ? ivData.length - 16 : 0;
    System.arraycopy(
        ivData,
        offset,
        ivDataWithPadding,
        ivDataWithPadding.length - ivData.length + offset,
        ivData.length - offset);
    return ivDataWithPadding;
  }

  /**
   * 如果片段完全加密，则返回一个包装原始数据源的 {@link Aes128DataSource} 以解密加载的数据。否则返回原始数据源。
   *
   * <p>{@code fullSegmentEncryptionKey} 和 {@code encryptionIv} 可以同时为 null，或者都不为 null。
   */
  private static DataSource buildDataSource(
      DataSource dataSource,
      @Nullable byte[] fullSegmentEncryptionKey,
      @Nullable byte[] encryptionIv) {
    if (fullSegmentEncryptionKey != null) {
      Assertions.checkNotNull(encryptionIv);
      return new Aes128DataSource(dataSource, fullSegmentEncryptionKey, encryptionIv);
    }
    return dataSource;
  }

  private static boolean isIndependent(
      HlsChunkSource.SegmentBaseHolder segmentBaseHolder, HlsMediaPlaylist mediaPlaylist) {
    if (segmentBaseHolder.segmentBase instanceof HlsMediaPlaylist.Part) {
      return ((HlsMediaPlaylist.Part) segmentBaseHolder.segmentBase).isIndependent
          || (segmentBaseHolder.partIndex == 0 && mediaPlaylist.hasIndependentSegments);
    }
    return mediaPlaylist.hasIndependentSegments;
  }
}
