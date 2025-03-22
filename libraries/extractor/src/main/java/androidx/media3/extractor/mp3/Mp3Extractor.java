package androidx.media3.extractor.mp3;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.DiscardingTrackOutput;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.GaplessInfoHolder;
import androidx.media3.extractor.Id3Peeker;
import androidx.media3.extractor.MpegAudioUtil;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.metadata.id3.Id3Decoder;
import androidx.media3.extractor.metadata.id3.Id3Decoder.FramePredicate;
import androidx.media3.extractor.metadata.id3.MlltFrame;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;
import androidx.media3.extractor.mp3.Seeker.UnseekableSeeker;
import com.google.common.math.LongMath;
import com.google.common.primitives.Ints;
import java.io.EOFException;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.math.RoundingMode;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * 从 MP3 容器格式中提取数据。
 */
@UnstableApi
public final class Mp3Extractor implements Extractor {

  /**
   * {@link Mp3Extractor} 实例的工厂。
   */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[]{new Mp3Extractor()};

  /**
   * 控制提取器行为的标志。可能的标志值包括 {@link #FLAG_ENABLE_CONSTANT_BITRATE_SEEKING}、{@link
   * #FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS}、{@link #FLAG_ENABLE_INDEX_SEEKING} 和 {@link
   * #FLAG_DISABLE_ID3_METADATA}。
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      flag = true,
      value = {
          FLAG_ENABLE_CONSTANT_BITRATE_SEEKING,
          FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS,
          FLAG_ENABLE_INDEX_SEEKING,
          FLAG_DISABLE_ID3_METADATA
      })
  public @interface Flags {

  }

  /**
   * 强制启用基于恒定比特率假设的搜索，否则无法进行搜索。
   *
   * <p>如果设置了 {@link #FLAG_ENABLE_INDEX_SEEKING}，则忽略此标志。
   */
  public static final int FLAG_ENABLE_CONSTANT_BITRATE_SEEKING = 1;

  /**
   * 类似于 {@link #FLAG_ENABLE_CONSTANT_BITRATE_SEEKING}，但即使内容长度（以及媒体的持续时间）未知，也启用搜索。
   * 应用程序代码在使用此标志时应确保请求的搜索位置有效，或准备处理通过 {@link Player.Listener#onPlayerError} 报告的播放失败，
   * 错误代码为 {@link PlaybackException#ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE}。
   *
   * <p>如果设置了此标志，则 {@link #FLAG_ENABLE_CONSTANT_BITRATE_SEEKING} 的行为也会隐式启用。
   *
   * <p>如果设置了 {@link #FLAG_ENABLE_INDEX_SEEKING}，则忽略此标志。
   */
  public static final int FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS = 1 << 1;

  /**
   * 强制启用索引搜索，即在读取文件时构建时间到字节的映射。
   *
   * <p>此搜索器可能需要扫描文件的很大一部分来计算搜索点。因此，仅在以下情况下使用：
   *
   * <ul>
   *   <li>文件较小。
   *   <li>比特率可变（或未知是否可变），且文件未提供足够精确的搜索元数据。
   * </ul>
   */
  public static final int FLAG_ENABLE_INDEX_SEEKING = 1 << 2;

  /**
   * 禁用 ID3 元数据的解析。如果不需要 ID3 元数据，可以设置此标志以节省内存。
   */
  public static final int FLAG_DISABLE_ID3_METADATA = 1 << 3;

  private static final String TAG = "Mp3Extractor";

  /**
   * 匹配仅包含所需无缝播放/搜索元数据的 ID3 帧的谓词。
   */
  private static final FramePredicate REQUIRED_ID3_FRAME_PREDICATE =
      (majorVersion, id0, id1, id2, id3) ->
          ((id0 == 'C' && id1 == 'O' && id2 == 'M' && (id3 == 'M' || majorVersion == 2))
              || (id0 == 'M' && id1 == 'L' && id2 == 'L' && (id3 == 'T' || majorVersion == 2)));

  /**
   * 同步时搜索的最大字节数，超过则放弃。
   */
  private static final int MAX_SYNC_BYTES = 128 * 1024;

  /**
   * 嗅探时读取的最大字节数，不包括 ID3 头，超过则放弃。
   */
  private static final int MAX_SNIFF_BYTES = 32 * 1024;

  /**
   * 读取到 {@link #scratch} 的最大数据长度。
   */
  private static final int SCRATCH_LENGTH = 10;

  /**
   * 包含音频头中必须匹配的值的掩码。
   */
  private static final int MPEG_AUDIO_HEADER_MASK = 0xFFFE0C00;

  @Documented
  @Target(TYPE_USE)
  @Retention(SOURCE)
  @IntDef(value = {SEEK_HEADER_XING, SEEK_HEADER_INFO, SEEK_HEADER_VBRI, SEEK_HEADER_UNSET})
  private @interface SeekHeader {

  }

  private static final int SEEK_HEADER_XING = 0x58696e67;
  private static final int SEEK_HEADER_INFO = 0x496e666f;
  private static final int SEEK_HEADER_VBRI = 0x56425249;
  private static final int SEEK_HEADER_UNSET = 0;

  private final @Flags int flags;
  private final long forcedFirstSampleTimestampUs;
  private final ParsableByteArray scratch;
  private final MpegAudioUtil.Header synchronizedHeader;
  private final GaplessInfoHolder gaplessInfoHolder;
  private final Id3Peeker id3Peeker;
  private final TrackOutput skippingTrackOutput;

  private @MonotonicNonNull ExtractorOutput extractorOutput;
  private @MonotonicNonNull TrackOutput realTrackOutput;
  private TrackOutput currentTrackOutput; // skippingTrackOutput 或 realTrackOutput。

  private int synchronizedHeaderData;

  @Nullable
  private Metadata metadata;
  private long basisTimeUs;
  private long samplesRead;
  private long firstSamplePosition;
  private long endPositionOfLastSampleRead;
  private int sampleBytesRemaining;

  private @MonotonicNonNull Seeker seeker;
  private boolean disableSeeking;
  private boolean isSeekInProgress;
  private long seekTimeUs;

  public Mp3Extractor() {
    this(0);
  }

  /**
   * @param flags 控制提取器行为的标志。
   */
  public Mp3Extractor(@Flags int flags) {
    this(flags, C.TIME_UNSET);
  }

  /**
   * @param flags                        控制提取器行为的标志。
   * @param forcedFirstSampleTimestampUs 强制为第一个样本设置的时间戳，如果不需要强制，则为 {@link C#TIME_UNSET}。
   */
  public Mp3Extractor(@Flags int flags, long forcedFirstSampleTimestampUs) {
    if ((flags & FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS) != 0) {
      flags |= FLAG_ENABLE_CONSTANT_BITRATE_SEEKING;
    }
    this.flags = flags;
    this.forcedFirstSampleTimestampUs = forcedFirstSampleTimestampUs;
    scratch = new ParsableByteArray(SCRATCH_LENGTH);
    synchronizedHeader = new MpegAudioUtil.Header();
    gaplessInfoHolder = new GaplessInfoHolder();
    basisTimeUs = C.TIME_UNSET;
    id3Peeker = new Id3Peeker();
    skippingTrackOutput = new DiscardingTrackOutput();
    currentTrackOutput = skippingTrackOutput;
    endPositionOfLastSampleRead = C.INDEX_UNSET;
  }

  // Extractor 实现。

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    return synchronize(input, true);
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    realTrackOutput = extractorOutput.track(0, C.TRACK_TYPE_AUDIO);
    currentTrackOutput = realTrackOutput;
    extractorOutput.endTracks();
  }

  @Override
  public void seek(long position, long timeUs) {
    synchronizedHeaderData = 0;
    basisTimeUs = C.TIME_UNSET;
    samplesRead = 0;
    sampleBytesRemaining = 0;
    seekTimeUs = timeUs;
    if (seeker instanceof IndexSeeker && !((IndexSeeker) seeker).isTimeUsInIndex(timeUs)) {
      isSeekInProgress = true;
      currentTrackOutput = skippingTrackOutput;
    }
  }

  @Override
  public void release() {
    // 什么都不做
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    assertInitialized();
    int readResult = readInternal(input);
    if (readResult == RESULT_END_OF_INPUT && seeker instanceof IndexSeeker) {
      // 使用索引搜索器时，持续时间是精确的。
      long durationUs = computeTimeUs(samplesRead);
      if (seeker.getDurationUs() != durationUs) {
        ((IndexSeeker) seeker).setDurationUs(durationUs);
        extractorOutput.seekMap(seeker);
      }
    }
    return readResult;
  }

  /**
   * 禁用提取器对媒体进行搜索的能力。
   *
   * <p>请注意，此方法需要在 {@link #read} 之前调用。
   */
  public void disableSeeking() {
    disableSeeking = true;
  }

  // 内部方法。

  @RequiresNonNull({"extractorOutput", "realTrackOutput"})
  private int readInternal(ExtractorInput input) throws IOException {
    if (synchronizedHeaderData == 0) {
      try {
        synchronize(input, false);
      } catch (EOFException e) {
        return RESULT_END_OF_INPUT;
      }
    }
    if (seeker == null) {
      seeker = computeSeeker(input);
      extractorOutput.seekMap(seeker);
      Format.Builder format =
          new Format.Builder()
              .setSampleMimeType(synchronizedHeader.mimeType)
              .setMaxInputSize(MpegAudioUtil.MAX_FRAME_SIZE_BYTES)
              .setChannelCount(synchronizedHeader.channels)
              .setSampleRate(synchronizedHeader.sampleRate)
              .setEncoderDelay(gaplessInfoHolder.encoderDelay)
              .setEncoderPadding(gaplessInfoHolder.encoderPadding)
              .setMetadata((flags & FLAG_DISABLE_ID3_METADATA) != 0 ? null : metadata);
      if (seeker.getAverageBitrate() != C.RATE_UNSET_INT) {
        format.setAverageBitrate(seeker.getAverageBitrate());
      }
      currentTrackOutput.format(format.build());
      firstSamplePosition = input.getPosition();
    } else if (firstSamplePosition != 0) {
      long inputPosition = input.getPosition();
      if (inputPosition < firstSamplePosition) {
        // 跳过搜索帧。
        input.skipFully((int) (firstSamplePosition - inputPosition));
      }
    }
    return readSample(input);
  }

  @RequiresNonNull({"realTrackOutput", "seeker"})
  private int readSample(ExtractorInput extractorInput) throws IOException {
    if (sampleBytesRemaining == 0) {
      extractorInput.resetPeekPosition();
      if (peekEndOfStreamOrHeader(extractorInput)) {
        return RESULT_END_OF_INPUT;
      }
      scratch.setPosition(0);
      int sampleHeaderData = scratch.readInt();
      if (!headersMatch(sampleHeaderData, synchronizedHeaderData)
          || MpegAudioUtil.getFrameSize(sampleHeaderData) == C.LENGTH_UNSET) {
        // 我们失去了同步，因此尝试从下一个字节开始重新同步。
        extractorInput.skipFully(1);
        synchronizedHeaderData = 0;
        return RESULT_CONTINUE;
      }
      synchronizedHeader.setForHeaderData(sampleHeaderData);
      if (basisTimeUs == C.TIME_UNSET) {
        basisTimeUs = seeker.getTimeUs(extractorInput.getPosition());
        if (forcedFirstSampleTimestampUs != C.TIME_UNSET) {
          long embeddedFirstSampleTimestampUs = seeker.getTimeUs(0);
          basisTimeUs += forcedFirstSampleTimestampUs - embeddedFirstSampleTimestampUs;
        }
      }
      sampleBytesRemaining = synchronizedHeader.frameSize;
      endPositionOfLastSampleRead = extractorInput.getPosition() + synchronizedHeader.frameSize;
      if (seeker instanceof IndexSeeker) {
        IndexSeeker indexSeeker = (IndexSeeker) seeker;
        // 添加与下一帧对应的搜索点，而不是当前帧，以便在搜索进行时能够及时开始写入 realTrackOutput。
        indexSeeker.maybeAddSeekPoint(
            computeTimeUs(samplesRead + synchronizedHeader.samplesPerFrame),
            endPositionOfLastSampleRead);
        if (isSeekInProgress && indexSeeker.isTimeUsInIndex(seekTimeUs)) {
          isSeekInProgress = false;
          currentTrackOutput = realTrackOutput;
        }
      }
    }
    int bytesAppended = currentTrackOutput.sampleData(extractorInput, sampleBytesRemaining, true);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      return RESULT_END_OF_INPUT;
    }
    sampleBytesRemaining -= bytesAppended;
    if (sampleBytesRemaining > 0) {
      return RESULT_CONTINUE;
    }
    currentTrackOutput.sampleMetadata(
        computeTimeUs(samplesRead), C.BUFFER_FLAG_KEY_FRAME, synchronizedHeader.frameSize, 0, null);
    samplesRead += synchronizedHeader.samplesPerFrame;
    sampleBytesRemaining = 0;
    return RESULT_CONTINUE;
  }

  private long computeTimeUs(long samplesRead) {
    return basisTimeUs + samplesRead * C.MICROS_PER_SECOND / synchronizedHeader.sampleRate;
  }

  /**
   * 同步MP3音频流的帧头。
   *
   * @param input    输入流，用于读取数据。
   * @param sniffing 是否在嗅探模式下运行。如果是，则只检查前几个字节以确定是否支持该格式。
   * @return 如果成功同步到有效的MP3帧头，则返回true；否则返回false。
   * @throws IOException 如果读取数据时发生错误。
   */
  private boolean synchronize(ExtractorInput input, boolean sniffing) throws IOException {
    int validFrameCount = 0; // 有效帧的数量
    int candidateSynchronizedHeaderData = 0; // 候选同步头数据
    int peekedId3Bytes = 0; // 已读取的ID3元数据的字节数
    int searchedBytes = 0; // 已搜索的字节数
    int searchLimitBytes = sniffing ? MAX_SNIFF_BYTES : MAX_SYNC_BYTES; // 搜索的字节数限制
    input.resetPeekPosition(); // 重置输入流的读取位置

    if (input.getPosition() == 0) {
      // 如果输入流的位置为0，则需要解析ID3元数据以获取无缝播放/搜索信息
      boolean parseAllId3Frames = (flags & FLAG_DISABLE_ID3_METADATA) == 0; // 是否解析所有ID3帧
      Id3Decoder.FramePredicate id3FramePredicate =
          parseAllId3Frames ? null : REQUIRED_ID3_FRAME_PREDICATE; // ID3帧的过滤条件
      metadata = id3Peeker.peekId3Data(input, id3FramePredicate); // 读取ID3元数据
      if (metadata != null) {
        gaplessInfoHolder.setFromMetadata(metadata); // 设置无缝播放信息
      }
      peekedId3Bytes = (int) input.getPeekPosition(); // 记录已读取的ID3字节数
      if (!sniffing) {
        input.skipFully(peekedId3Bytes); // 如果不是嗅探模式，则跳过已读取的ID3字节
      }
    }

    while (true) {
      if (peekEndOfStreamOrHeader(input)) {
        // 如果到达流的末尾或读取到帧头
        if (validFrameCount > 0) {
          // 如果至少找到一个有效帧，则跳出循环
          break;
        }
        maybeUpdateCbrDurationToLastSample(); // 更新CBR（恒定比特率）的持续时间
        throw new EOFException(); // 抛出EOF异常
      }

      scratch.setPosition(0); // 重置临时缓冲区的位置
      int headerData = scratch.readInt(); // 读取帧头数据
      int frameSize;
      if ((candidateSynchronizedHeaderData != 0
          && !headersMatch(headerData, candidateSynchronizedHeaderData))
          || (frameSize = MpegAudioUtil.getFrameSize(headerData)) == C.LENGTH_UNSET) {
        // 如果帧头不匹配候选帧头或帧头无效，则尝试下一个字节偏移
        if (searchedBytes++ == searchLimitBytes) {
          if (!sniffing) {
            maybeUpdateCbrDurationToLastSample(); // 更新CBR的持续时间
            throw new EOFException(); // 抛出EOF异常
          }
          return false; // 返回false，表示同步失败
        }
        validFrameCount = 0; // 重置有效帧计数
        candidateSynchronizedHeaderData = 0; // 重置候选帧头数据
        if (sniffing) {
          input.resetPeekPosition(); // 重置输入流的读取位置
          input.advancePeekPosition(peekedId3Bytes + searchedBytes); // 前进读取位置
        } else {
          input.skipFully(1); // 跳过1个字节
        }
      } else {
        // 如果帧头匹配候选帧头且有效
        validFrameCount++; // 增加有效帧计数
        if (validFrameCount == 1) {
          synchronizedHeader.setForHeaderData(headerData); // 设置同步帧头
          candidateSynchronizedHeaderData = headerData; // 设置候选帧头数据
        } else if (validFrameCount == 4) {
          break; // 如果找到4个有效帧，则跳出循环
        }
        input.advancePeekPosition(frameSize - 4); // 前进读取位置
      }
    }

    // 准备读取同步帧
    if (sniffing) {
      input.skipFully(peekedId3Bytes + searchedBytes); // 跳过已读取的ID3字节和搜索字节
    } else {
      input.resetPeekPosition(); // 重置输入流的读取位置
    }
    synchronizedHeaderData = candidateSynchronizedHeaderData; // 设置同步帧头数据
    return true; // 返回true，表示同步成功
  }

  /**
   * 检查输入流是否到达末尾或读取到帧头。
   *
   * @param extractorInput 输入流，用于读取数据。
   * @return 如果到达流的末尾或读取到帧头，则返回true；否则返回false。
   * @throws IOException 如果读取数据时发生错误。
   */
  private boolean peekEndOfStreamOrHeader(ExtractorInput extractorInput) throws IOException {
    if (seeker != null) {
      long dataEndPosition = seeker.getDataEndPosition(); // 获取数据结束位置
      if (dataEndPosition != C.INDEX_UNSET
          && extractorInput.getPeekPosition() > dataEndPosition - 4) {
        return true; // 如果读取位置超过数据结束位置，则返回true
      }
    }
    try {
      return !extractorInput.peekFully(
          scratch.getData(), /* offset= */ 0, /* length= */ 4, /* allowEndOfInput= */ true);
    } catch (EOFException e) {
      return true; // 如果发生EOF异常，则返回true
    }
  }

  /**
   * 根据输入流计算Seeker（用于定位和搜索）。
   *
   * @param input 输入流，用于读取数据。
   * @return 返回一个Seeker对象。
   * @throws IOException 如果读取数据时发生错误。
   */
  private Seeker computeSeeker(ExtractorInput input) throws IOException {
    // 读取任何搜索帧，并根据元数据或搜索帧设置Seeker。元数据优先，因为它可以提供更高的精度。
    Seeker seekFrameSeeker = maybeReadSeekFrame(input); // 读取搜索帧
    Seeker metadataSeeker = maybeHandleSeekMetadata(metadata, input.getPosition()); // 处理元数据

    if (disableSeeking) {
      return new UnseekableSeeker(); // 如果禁用搜索，则返回不可搜索的Seeker
    }

    @Nullable Seeker resultSeeker = null;
    if ((flags & FLAG_ENABLE_INDEX_SEEKING) != 0) {
      long durationUs;
      long dataEndPosition = C.INDEX_UNSET;
      if (metadataSeeker != null) {
        durationUs = metadataSeeker.getDurationUs(); // 获取持续时间
        dataEndPosition = metadataSeeker.getDataEndPosition(); // 获取数据结束位置
      } else if (seekFrameSeeker != null) {
        durationUs = seekFrameSeeker.getDurationUs(); // 获取持续时间
        dataEndPosition = seekFrameSeeker.getDataEndPosition(); // 获取数据结束位置
      } else {
        durationUs = getId3TlenUs(metadata); // 从ID3元数据中获取持续时间
      }
      resultSeeker =
          new IndexSeeker(
              durationUs, /* dataStartPosition= */ input.getPosition(), dataEndPosition);
    } else if (metadataSeeker != null) {
      resultSeeker = metadataSeeker; // 使用元数据Seeker
    } else if (seekFrameSeeker != null) {
      resultSeeker = seekFrameSeeker; // 使用搜索帧Seeker
    }

    if (resultSeeker == null
        || (!resultSeeker.isSeekable() && (flags & FLAG_ENABLE_CONSTANT_BITRATE_SEEKING) != 0)) {
      resultSeeker =
          getConstantBitrateSeeker(
              input, (flags & FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS) != 0);
    }

    return resultSeeker; // 返回最终的Seeker
  }

  /**
   * 如果输入流包含VBRI或Xing搜索元数据，则读取并返回一个Seeker对象；否则返回null。
   *
   * @param input 输入流，用于读取数据。
   * @return 如果存在有效的搜索元数据，则返回Seeker对象；否则返回null。
   * @throws IOException 如果读取数据时发生错误。
   */
  @Nullable
  private Seeker maybeReadSeekFrame(ExtractorInput input) throws IOException {
    ParsableByteArray frame = new ParsableByteArray(synchronizedHeader.frameSize);
    input.peekFully(frame.getData(), 0, synchronizedHeader.frameSize);
    int xingBase =
        (synchronizedHeader.version & 1) != 0
            ? (synchronizedHeader.channels != 1 ? 36 : 21) // MPEG 1
            : (synchronizedHeader.channels != 1 ? 21 : 13); // MPEG 2 or 2.5
    @SeekHeader int seekHeader = getSeekFrameHeader(frame, xingBase);
    @Nullable Seeker seeker;
    switch (seekHeader) {
      case SEEK_HEADER_XING:
      case SEEK_HEADER_INFO:
        XingFrame xingFrame = XingFrame.parse(synchronizedHeader, frame);
        if (!gaplessInfoHolder.hasGaplessInfo()
            && xingFrame.encoderDelay != C.LENGTH_UNSET
            && xingFrame.encoderPadding != C.LENGTH_UNSET) {
          gaplessInfoHolder.encoderDelay = xingFrame.encoderDelay;
          gaplessInfoHolder.encoderPadding = xingFrame.encoderPadding;
        }
        long startPosition = input.getPosition();
        if (input.getLength() != C.LENGTH_UNSET
            && xingFrame.dataSize != C.LENGTH_UNSET
            && input.getLength() != startPosition + xingFrame.dataSize) {
          Log.i(
              TAG,
              "Data size mismatch between stream ("
                  + input.getLength()
                  + ") and Xing frame ("
                  + (startPosition + xingFrame.dataSize)
                  + "), using Xing value.");
        }
        input.skipFully(synchronizedHeader.frameSize);
        if (seekHeader == SEEK_HEADER_XING) {
          seeker = XingSeeker.create(xingFrame, startPosition);
        } else { // seekHeader == SEEK_HEADER_INFO
          seeker = getConstantBitrateSeeker(startPosition, xingFrame, input.getLength());
        }
        break;
      case SEEK_HEADER_VBRI:
        seeker =
            VbriSeeker.create(input.getLength(), input.getPosition(), synchronizedHeader, frame);
        input.skipFully(synchronizedHeader.frameSize);
        break;
      case SEEK_HEADER_UNSET:
      default:
        seeker = null;
        input.resetPeekPosition();
    }
    return seeker;
  }

  /**
   * 根据输入流的比特率返回一个ConstantBitrateSeeker对象。
   *
   * @param input                     输入流，用于读取数据。
   * @param allowSeeksIfLengthUnknown 如果流的长度未知，是否允许搜索。
   * @return 返回一个ConstantBitrateSeeker对象。
   * @throws IOException 如果读取数据时发生错误。
   */
  private Seeker getConstantBitrateSeeker(ExtractorInput input, boolean allowSeeksIfLengthUnknown)
      throws IOException {
    input.peekFully(scratch.getData(), 0, 4);
    scratch.setPosition(0);
    synchronizedHeader.setForHeaderData(scratch.readInt());
    return new ConstantBitrateSeeker(
        input.getLength(), input.getPosition(), synchronizedHeader, allowSeeksIfLengthUnknown);
  }

  /**
   * 根据提供的XingFrame（Info帧）返回一个ConstantBitrateSeeker对象。
   *
   * @param infoFramePosition    Info帧的位置（从流的开始处计算）。
   * @param infoFrame            解析后的Info帧。
   * @param fallbackStreamLength 输入流的完整长度（仅在XingFrame的dataSize未设置时使用）。
   * @return 如果XingFrame包含足够的信息用于搜索，则返回Seeker对象；否则返回null。
   */
  @Nullable
  private Seeker getConstantBitrateSeeker(
      long infoFramePosition, XingFrame infoFrame, long fallbackStreamLength) {
    long durationUs = infoFrame.computeDurationUs();
    if (durationUs == C.TIME_UNSET) {
      return null;
    }
    long streamLength;
    long audioLength;
    if (infoFrame.dataSize != C.LENGTH_UNSET) {
      streamLength = infoFramePosition + infoFrame.dataSize;
      audioLength = infoFrame.dataSize - infoFrame.header.frameSize;
    } else if (fallbackStreamLength != C.LENGTH_UNSET) {
      streamLength = fallbackStreamLength;
      audioLength = fallbackStreamLength - infoFramePosition - infoFrame.header.frameSize;
    } else {
      return null;
    }

    int averageBitrate =
        Ints.checkedCast(
            Util.scaleLargeValue(
                audioLength,
                C.BITS_PER_BYTE * C.MICROS_PER_SECOND,
                durationUs,
                RoundingMode.HALF_UP));
    int frameSize =
        Ints.checkedCast(LongMath.divide(audioLength, infoFrame.frameCount, RoundingMode.HALF_UP));
    return new ConstantBitrateSeeker(
        streamLength,
        /* firstFramePosition= */ infoFramePosition + infoFrame.header.frameSize,
        averageBitrate,
        frameSize,
        /* allowSeeksIfLengthUnknown= */ false);
  }

  /**
   * 如果seeker是一个可搜索的ConstantBitrateSeeker，则更新它以结束于我们读取的最后一个样本
   * （因为我们未能找到后续的同步字，因此我们假设MP3数据已结束）。
   */
  private void maybeUpdateCbrDurationToLastSample() {
    if (seeker instanceof ConstantBitrateSeeker
        && seeker.isSeekable()
        && endPositionOfLastSampleRead != C.INDEX_UNSET
        && endPositionOfLastSampleRead != seeker.getDataEndPosition()) {
      seeker =
          ((ConstantBitrateSeeker) seeker).copyWithNewDataEndPosition(endPositionOfLastSampleRead);
      checkNotNull(extractorOutput).seekMap(seeker);
    }
  }

  /**
   * 确保extractorOutput和realTrackOutput已初始化。
   */
  @EnsuresNonNull({"extractorOutput", "realTrackOutput"})
  private void assertInitialized() {
    Assertions.checkStateNotNull(realTrackOutput);
    Util.castNonNull(extractorOutput);
  }

  /**
   * 检查两个帧头是否匹配（基于MPEG_AUDIO_HEADER_MASK掩码）。
   *
   * @param headerA 第一个帧头。
   * @param headerB 第二个帧头。
   * @return 如果两个帧头匹配，则返回true；否则返回false。
   */
  private static boolean headersMatch(int headerA, long headerB) {
    return (headerA & MPEG_AUDIO_HEADER_MASK) == (headerB & MPEG_AUDIO_HEADER_MASK);
  }

  /**
   * 如果提供的frame可能包含搜索元数据，则返回SEEK_HEADER_XING、SEEK_HEADER_INFO或SEEK_HEADER_VBRI；
   * 否则返回SEEK_HEADER_UNSET。
   *
   * @param frame    包含帧数据的ParsableByteArray。
   * @param xingBase Xing帧的基址。
   * @return 返回搜索头类型。
   */
  private static @SeekHeader int getSeekFrameHeader(ParsableByteArray frame, int xingBase) {
    if (frame.limit() >= xingBase + 4) {
      frame.setPosition(xingBase);
      int headerData = frame.readInt();
      if (headerData == SEEK_HEADER_XING || headerData == SEEK_HEADER_INFO) {
        return headerData;
      }
    }
    if (frame.limit() >= 40) {
      frame.setPosition(36); // MPEG音频头（4字节）+ 32字节。
      if (frame.readInt() == SEEK_HEADER_VBRI) {
        return SEEK_HEADER_VBRI;
      }
    }
    return SEEK_HEADER_UNSET;
  }

  /**
   * 如果元数据包含MLLT帧，则返回一个MlltSeeker对象；否则返回null。
   *
   * @param metadata           元数据。
   * @param firstFramePosition 第一帧的位置。
   * @return 如果存在MLLT帧，则返回MlltSeeker对象；否则返回null。
   */
  @Nullable
  private static MlltSeeker maybeHandleSeekMetadata(
      @Nullable Metadata metadata, long firstFramePosition) {
    if (metadata != null) {
      int length = metadata.length();
      for (int i = 0; i < length; i++) {
        Metadata.Entry entry = metadata.get(i);
        if (entry instanceof MlltFrame) {
          return MlltSeeker.create(firstFramePosition, (MlltFrame) entry, getId3TlenUs(metadata));
        }
      }
    }
    return null;
  }

  /**
   * 从ID3元数据中获取TLEN（音轨长度）信息，并将其转换为微秒。
   *
   * @param metadata 元数据。
   * @return 返回音轨长度的微秒数，如果未找到则返回C.TIME_UNSET。
   */
  private static long getId3TlenUs(@Nullable Metadata metadata) {
    if (metadata != null) {
      int length = metadata.length();
      for (int i = 0; i < length; i++) {
        Metadata.Entry entry = metadata.get(i);
        if (entry instanceof TextInformationFrame
            && ((TextInformationFrame) entry).id.equals("TLEN")) {
          return Util.msToUs(Long.parseLong(((TextInformationFrame) entry).values.get(0)));
        }
      }
    }
    return C.TIME_UNSET;
  }
}