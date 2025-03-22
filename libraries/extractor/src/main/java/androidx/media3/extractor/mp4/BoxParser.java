package androidx.media3.extractor.mp4;

import static androidx.media3.common.MimeTypes.getMimeTypeFromMp4ObjectType;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;
import static java.lang.Math.max;

import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.CodecSpecificDataUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.container.Mp4Box;
import androidx.media3.container.Mp4Box.LeafBox;
import androidx.media3.container.Mp4LocationData;
import androidx.media3.container.Mp4TimestampData;
import androidx.media3.container.NalUnitUtil;
import androidx.media3.extractor.AacUtil;
import androidx.media3.extractor.Ac3Util;
import androidx.media3.extractor.Ac4Util;
import androidx.media3.extractor.AvcConfig;
import androidx.media3.extractor.DolbyVisionConfig;
import androidx.media3.extractor.ExtractorUtil;
import androidx.media3.extractor.GaplessInfoHolder;
import androidx.media3.extractor.HevcConfig;
import androidx.media3.extractor.OpusUtil;
import androidx.media3.extractor.VorbisUtil;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 根据 ISO/IEC 14496-12 标准解析 MP4 格式盒子内容的工具方法。
 */
@SuppressWarnings("ConstantField")
@UnstableApi
public final class BoxParser {

  private static final String TAG = "BoxParsers";

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_clcp = 0x636c6370;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_mdta = 0x6d647461;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_meta = 0x6d657461;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_nclc = 0x6e636c63;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_nclx = 0x6e636c78;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_sbtl = 0x7362746c;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_soun = 0x736f756e;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_subt = 0x73756274;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_text = 0x74657874;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_vide = 0x76696465;

  /**
   * 在对音频轨道应用编辑时，从音频轨道的开始/结束修剪的样本数量的阈值，
   * 低于该阈值时可以使用无缝播放信息（而不是从样本表中删除样本）。
   */
  private static final int MAX_GAPLESS_TRIM_SIZE_SAMPLES = 4;

  /**
   * Opus 识别头部的魔数签名，定义在 RFC-7845 中。
   */
  private static final byte[] opusMagic = Util.getUtf8Bytes("OpusHead");

  /**
   * 从完整盒（full box）的附加整数部分解析版本号。
   *
   * @param fullBoxInt 完整盒的附加整数部分
   * @return 解析出的版本号
   */
  public static int parseFullBoxVersion(int fullBoxInt) {
    return 0x000000FF & (fullBoxInt >> 24); // 取高 8 位作为版本号
  }

  /**
   * 从完整盒（full box）的附加整数部分解析盒标志。
   *
   * @param fullBoxInt 完整盒的附加整数部分
   * @return 解析出的盒标志
   */
  public static int parseFullBoxFlags(int fullBoxInt) {
    return 0x00FFFFFF & fullBoxInt; // 取低 24 位作为盒标志
  }

  /**
   * 解析 moov 盒中的 trak 盒（定义在 ISO/IEC 14496-12 中）。
   *
   * @param moov                要解码的 moov 盒。
   * @param gaplessInfoHolder   用于填充无缝播放信息的容器。
   * @param duration            以 mvhd 盒中声明的时间尺度为单位的持续时间，如果应从 tkhd 盒中解析持续时间，则为 {@link C#TIME_UNSET}。
   * @param drmInitData         要包含在格式中的 {@link DrmInitData}，如果不需要则为 {@code null}。
   * @param ignoreEditLists     是否忽略 trak 盒中的任何编辑列表。
   * @param isQuickTime         如果为 QuickTime 媒体则为 true，否则为 false。
   * @param modifyTrackFunction 应用于结果中 {@link Track Tracks} 的函数。
   * @return 一个 {@link TrackSampleTable} 实例的列表。
   * @throws ParserException 如果 trak 盒无法解析，则抛出此异常。
   */
  public static List<TrackSampleTable> parseTraks(
      Mp4Box.ContainerBox moov,
      GaplessInfoHolder gaplessInfoHolder,
      long duration,
      @Nullable DrmInitData drmInitData,
      boolean ignoreEditLists,
      boolean isQuickTime,
      Function<@NullableType Track, @NullableType Track> modifyTrackFunction)
      throws ParserException {
    List<TrackSampleTable> trackSampleTables = new ArrayList<>();
    for (int i = 0; i < moov.containerChildren.size(); i++) {
      Mp4Box.ContainerBox atom = moov.containerChildren.get(i);
      if (atom.type != Mp4Box.TYPE_trak) {
        continue;
      }
      @Nullable
      Track track =
          modifyTrackFunction.apply(
              parseTrak(
                  atom,
                  checkNotNull(moov.getLeafBoxOfType(Mp4Box.TYPE_mvhd)),
                  duration,
                  drmInitData,
                  ignoreEditLists,
                  isQuickTime));
      if (track == null) {
        continue;
      }
      Mp4Box.ContainerBox stblAtom =
          checkNotNull(
              checkNotNull(
                  checkNotNull(atom.getContainerBoxOfType(Mp4Box.TYPE_mdia))
                      .getContainerBoxOfType(Mp4Box.TYPE_minf))
                  .getContainerBoxOfType(Mp4Box.TYPE_stbl));
      TrackSampleTable trackSampleTable = parseStbl(track, stblAtom, gaplessInfoHolder);
      trackSampleTables.add(trackSampleTable);
    }
    return trackSampleTables;
  }

  /**
   * 解析 udta 盒。
   *
   * @param udtaBox 要解码的 udta（用户数据）盒。
   * @return 解析后的元数据。
   */
  public static Metadata parseUdta(LeafBox udtaBox) {
    ParsableByteArray udtaData = udtaBox.data;
    udtaData.setPosition(Mp4Box.HEADER_SIZE);
    Metadata metadata = new Metadata();
    while (udtaData.bytesLeft() >= Mp4Box.HEADER_SIZE) {
      int atomPosition = udtaData.getPosition();
      int atomSize = udtaData.readInt();
      int atomType = udtaData.readInt();
      if (atomType == Mp4Box.TYPE_meta) {
        udtaData.setPosition(atomPosition);
        metadata =
            metadata.copyWithAppendedEntriesFrom(parseUdtaMeta(udtaData, atomPosition + atomSize));
      } else if (atomType == Mp4Box.TYPE_smta) {
        udtaData.setPosition(atomPosition);
        metadata =
            metadata.copyWithAppendedEntriesFrom(
                SmtaAtomUtil.parseSmta(udtaData, atomPosition + atomSize));
      } else if (atomType == Mp4Box.TYPE_xyz) {
        metadata = metadata.copyWithAppendedEntriesFrom(parseXyz(udtaData));
      }
      udtaData.setPosition(atomPosition + atomSize);
    }
    return metadata;
  }

  /**
   * 解析 mvhd 盒（定义在 ISO/IEC 14496-12 中）。
   *
   * @param mvhd 要解析的 mvhd 盒内容。
   * @return 包含解析数据的对象。
   */
  public static Mp4TimestampData parseMvhd(ParsableByteArray mvhd) {
    mvhd.setPosition(Mp4Box.HEADER_SIZE);
    int fullAtom = mvhd.readInt();
    int version = parseFullBoxVersion(fullAtom);
    long creationTimestampSeconds;
    long modificationTimestampSeconds;
    if (version == 0) {
      creationTimestampSeconds = mvhd.readUnsignedInt();
      modificationTimestampSeconds = mvhd.readUnsignedInt();
    } else {
      creationTimestampSeconds = mvhd.readLong();
      modificationTimestampSeconds = mvhd.readLong();
    }

    long timescale = mvhd.readUnsignedInt();
    return new Mp4TimestampData(creationTimestampSeconds, modificationTimestampSeconds, timescale);
  }

  /**
   * 解析一个元数据 meta 盒，如果它包含 handler 为 'mdta' 的元数据。
   *
   * @param meta 要解码的元数据盒。
   * @return 解析后的元数据，如果不符合条件则返回 null。
   */
  @Nullable
  public static Metadata parseMdtaFromMeta(Mp4Box.ContainerBox meta) {
    @Nullable LeafBox hdlrAtom = meta.getLeafBoxOfType(Mp4Box.TYPE_hdlr);
    @Nullable LeafBox keysAtom = meta.getLeafBoxOfType(Mp4Box.TYPE_keys);
    @Nullable LeafBox ilstAtom = meta.getLeafBoxOfType(Mp4Box.TYPE_ilst);
    if (hdlrAtom == null
        || keysAtom == null
        || ilstAtom == null
        || parseHdlr(hdlrAtom.data) != TYPE_mdta) {
      // There isn't enough information to parse the metadata, or the handler type is unexpected.
      return null;
    }

    // Parse metadata keys.
    ParsableByteArray keys = keysAtom.data;
    keys.setPosition(Mp4Box.FULL_HEADER_SIZE);
    int entryCount = keys.readInt();
    String[] keyNames = new String[entryCount];
    for (int i = 0; i < entryCount; i++) {
      int entrySize = keys.readInt();
      keys.skipBytes(4); // keyNamespace
      int keySize = entrySize - 8;
      keyNames[i] = keys.readString(keySize);
    }

    // Parse metadata items.
    ParsableByteArray ilst = ilstAtom.data;
    ilst.setPosition(Mp4Box.HEADER_SIZE);
    ArrayList<Metadata.Entry> entries = new ArrayList<>();
    while (ilst.bytesLeft() > Mp4Box.HEADER_SIZE) {
      int atomPosition = ilst.getPosition();
      int atomSize = ilst.readInt();
      int keyIndex = ilst.readInt() - 1;
      if (keyIndex >= 0 && keyIndex < keyNames.length) {
        String key = keyNames[keyIndex];
        @Nullable
        Metadata.Entry entry =
            MetadataUtil.parseMdtaMetadataEntryFromIlst(ilst, atomPosition + atomSize, key);
        if (entry != null) {
          entries.add(entry);
        }
      } else {
        Log.w(TAG, "Skipped metadata with unknown key index: " + keyIndex);
      }
      ilst.setPosition(atomPosition + atomSize);
    }
    return entries.isEmpty() ? null : new Metadata(entries);
  }

  /**
   * 可能跳过完整 meta 盒的版本和标志字段（1+3 字节）。
   *
   * <p>类型为 {@link Mp4Box#TYPE_meta} 的盒被定义为完整盒，其中包含四个额外的字节用于版本和标志字段
   * （参见 ISO/IEC 14496-12:2005 中的 4.2 'Object Structure'）。QuickTime 文件没有这种完整盒结构。
   * 由于某些文件的编码错误，我们不能仅依赖文件类型来判断。相反，我们必须自己检查 meta 盒大小和类型之后的 8 个字节。
   *
   * @param meta meta 盒大小和类型之后的 8 个或更多字节。
   */
  public static void maybeSkipRemainingMetaBoxHeaderBytes(ParsableByteArray meta) {
    int endPosition = meta.getPosition();
    // 接下来的 8 个字节可能是以下两种情况之一：
    // (iso) [1 字节版本 + 3 字节标志][4 字节下一个原子的大小]
    // (qt)  [4 字节下一个原子的大小      ][4 字节 hdlr 原子类型   ]
    // 如果是 (iso) 的情况，我们需要跳过接下来的 4 个字节。
    meta.skipBytes(4);
    if (meta.readInt() != Mp4Box.TYPE_hdlr) {
      endPosition += 4;
    }
    meta.setPosition(endPosition);
  }

  /**
   * 解析 trak 盒（定义在 ISO/IEC 14496-12 中）。
   *
   * @param trak            要解码的 trak 盒。
   * @param mvhd            电影头盒，用于获取时间尺度。
   * @param duration        以 mvhd 盒中声明的时间尺度为单位的持续时间，如果应从 tkhd 盒中解析持续时间，则为 {@link C#TIME_UNSET}。
   * @param drmInitData     要包含在格式中的 {@link DrmInitData}，如果不需要则为 {@code null}。
   * @param ignoreEditLists 是否忽略 trak 盒中的任何编辑列表。
   * @param isQuickTime     如果为 QuickTime 媒体则为 true，否则为 false。
   * @return 一个 {@link Track} 实例，如果轨道类型不受支持则返回 {@code null}。
   * @throws ParserException 如果 trak 盒无法解析，则抛出此异常。
   */
  @Nullable
  public static Track parseTrak(
      Mp4Box.ContainerBox trak,
      LeafBox mvhd,
      long duration,
      @Nullable DrmInitData drmInitData,
      boolean ignoreEditLists,
      boolean isQuickTime)
      throws ParserException {
    Mp4Box.ContainerBox mdia = checkNotNull(trak.getContainerBoxOfType(Mp4Box.TYPE_mdia));
    @C.TrackType
    int trackType =
        getTrackTypeForHdlr(parseHdlr(checkNotNull(mdia.getLeafBoxOfType(Mp4Box.TYPE_hdlr)).data));
    if (trackType == C.TRACK_TYPE_UNKNOWN) {
      return null;
    }

    TkhdData tkhdData = parseTkhd(checkNotNull(trak.getLeafBoxOfType(Mp4Box.TYPE_tkhd)).data);
    if (duration == C.TIME_UNSET) {
      duration = tkhdData.duration;
    }
    long movieTimescale = parseMvhd(mvhd.data).timescale;
    long durationUs;
    if (duration == C.TIME_UNSET) {
      durationUs = C.TIME_UNSET;
    } else {
      durationUs = Util.scaleLargeTimestamp(duration, C.MICROS_PER_SECOND, movieTimescale);
    }
    Mp4Box.ContainerBox stbl =
        checkNotNull(
            checkNotNull(mdia.getContainerBoxOfType(Mp4Box.TYPE_minf))
                .getContainerBoxOfType(Mp4Box.TYPE_stbl));

    MdhdData mdhdData = parseMdhd(checkNotNull(mdia.getLeafBoxOfType(Mp4Box.TYPE_mdhd)).data);
    LeafBox stsd = stbl.getLeafBoxOfType(Mp4Box.TYPE_stsd);
    if (stsd == null) {
      throw ParserException.createForMalformedContainer(
          "Malformed sample table (stbl) missing sample description (stsd)", /* cause= */ null);
    }
    StsdData stsdData =
        parseStsd(
            stsd.data,
            tkhdData.id,
            tkhdData.rotationDegrees,
            mdhdData.language,
            drmInitData,
            isQuickTime);
    @Nullable long[] editListDurations = null;
    @Nullable long[] editListMediaTimes = null;
    if (!ignoreEditLists) {
      @Nullable Mp4Box.ContainerBox edtsAtom = trak.getContainerBoxOfType(Mp4Box.TYPE_edts);
      if (edtsAtom != null) {
        @Nullable Pair<long[], long[]> edtsData = parseEdts(edtsAtom);
        if (edtsData != null) {
          editListDurations = edtsData.first;
          editListMediaTimes = edtsData.second;
        }
      }
    }
    return stsdData.format == null
        ? null
        : new Track(
            tkhdData.id,
            trackType,
            mdhdData.timescale,
            movieTimescale,
            durationUs,
            mdhdData.mediaDurationUs,
            stsdData.format,
            stsdData.requiredSampleTransformation,
            stsdData.trackEncryptionBoxes,
            stsdData.nalUnitLengthFieldLength,
            editListDurations,
            editListMediaTimes);
  }

  /**
   * 解析 stbl 盒（定义在 ISO/IEC 14496-12 中）。
   *
   * @param track             该样本表对应的轨道。
   * @param stblBox           要解码的 stbl（样本表）盒。
   * @param gaplessInfoHolder 用于填充无缝播放信息的容器。
   * @return 由 stbl 盒描述的样本表。
   * @throws ParserException 如果 stbl 盒无法解析，则抛出此异常。
   */
  public static TrackSampleTable parseStbl(
      Track track, Mp4Box.ContainerBox stblBox, GaplessInfoHolder gaplessInfoHolder)
      throws ParserException {
    SampleSizeBox sampleSizeBox;
    @Nullable LeafBox stszAtom = stblBox.getLeafBoxOfType(Mp4Box.TYPE_stsz);
    if (stszAtom != null) {
      sampleSizeBox = new StszSampleSizeBox(stszAtom, track.format);
    } else {
      @Nullable LeafBox stz2Atom = stblBox.getLeafBoxOfType(Mp4Box.TYPE_stz2);
      if (stz2Atom == null) {
        throw ParserException.createForMalformedContainer(
            "Track has no sample table size information", /* cause= */ null);
      }
      sampleSizeBox = new Stz2SampleSizeBox(stz2Atom);
    }

    int sampleCount = sampleSizeBox.getSampleCount();
    if (sampleCount == 0) {
      return new TrackSampleTable(
          track,
          /* offsets= */ new long[0],
          /* sizes= */ new int[0],
          /* maximumSize= */ 0,
          /* timestampsUs= */ new long[0],
          /* flags= */ new int[0],
          /* durationUs= */ 0);
    }

    if (track.type == C.TRACK_TYPE_VIDEO && track.mediaDurationUs > 0) {
      float frameRate = sampleCount / (track.mediaDurationUs / 1000000f);
      Format format = track.format.buildUpon().setFrameRate(frameRate).build();
      track = track.copyWithFormat(format);
    }

    // Entries are byte offsets of chunks.
    boolean chunkOffsetsAreLongs = false;
    @Nullable LeafBox chunkOffsetsAtom = stblBox.getLeafBoxOfType(Mp4Box.TYPE_stco);
    if (chunkOffsetsAtom == null) {
      chunkOffsetsAreLongs = true;
      chunkOffsetsAtom = checkNotNull(stblBox.getLeafBoxOfType(Mp4Box.TYPE_co64));
    }
    ParsableByteArray chunkOffsets = chunkOffsetsAtom.data;
    // Entries are (chunk number, number of samples per chunk, sample description index).
    ParsableByteArray stsc = checkNotNull(stblBox.getLeafBoxOfType(Mp4Box.TYPE_stsc)).data;
    // Entries are (number of samples, timestamp delta between those samples).
    ParsableByteArray stts = checkNotNull(stblBox.getLeafBoxOfType(Mp4Box.TYPE_stts)).data;
    // Entries are the indices of samples that are synchronization samples.
    @Nullable LeafBox stssAtom = stblBox.getLeafBoxOfType(Mp4Box.TYPE_stss);
    @Nullable ParsableByteArray stss = stssAtom != null ? stssAtom.data : null;
    // Entries are (number of samples, timestamp offset).
    @Nullable LeafBox cttsAtom = stblBox.getLeafBoxOfType(Mp4Box.TYPE_ctts);
    @Nullable ParsableByteArray ctts = cttsAtom != null ? cttsAtom.data : null;

    // Prepare to read chunk information.
    ChunkIterator chunkIterator = new ChunkIterator(stsc, chunkOffsets, chunkOffsetsAreLongs);

    // Prepare to read sample timestamps.
    stts.setPosition(Mp4Box.FULL_HEADER_SIZE);
    int remainingTimestampDeltaChanges = stts.readUnsignedIntToInt() - 1;
    int remainingSamplesAtTimestampDelta = stts.readUnsignedIntToInt();
    int timestampDeltaInTimeUnits = stts.readUnsignedIntToInt();

    // Prepare to read sample timestamp offsets, if ctts is present.
    int remainingSamplesAtTimestampOffset = 0;
    int remainingTimestampOffsetChanges = 0;
    int timestampOffset = 0;
    if (ctts != null) {
      ctts.setPosition(Mp4Box.FULL_HEADER_SIZE);
      remainingTimestampOffsetChanges = ctts.readUnsignedIntToInt();
    }

    int nextSynchronizationSampleIndex = C.INDEX_UNSET;
    int remainingSynchronizationSamples = 0;
    if (stss != null) {
      stss.setPosition(Mp4Box.FULL_HEADER_SIZE);
      remainingSynchronizationSamples = stss.readUnsignedIntToInt();
      if (remainingSynchronizationSamples > 0) {
        nextSynchronizationSampleIndex = stss.readUnsignedIntToInt() - 1;
      } else {
        // Ignore empty stss boxes, which causes all samples to be treated as sync samples.
        stss = null;
      }
    }

    // Fixed sample size raw audio may need to be rechunked.
    int fixedSampleSize = sampleSizeBox.getFixedSampleSize();
    @Nullable String sampleMimeType = track.format.sampleMimeType;
    boolean rechunkFixedSizeSamples =
        fixedSampleSize != C.LENGTH_UNSET
            && (MimeTypes.AUDIO_RAW.equals(sampleMimeType)
            || MimeTypes.AUDIO_MLAW.equals(sampleMimeType)
            || MimeTypes.AUDIO_ALAW.equals(sampleMimeType))
            && remainingTimestampDeltaChanges == 0
            && remainingTimestampOffsetChanges == 0
            && remainingSynchronizationSamples == 0;

    long[] offsets;
    int[] sizes;
    int maximumSize = 0;
    long[] timestamps;
    int[] flags;
    long timestampTimeUnits = 0;
    long duration;

    if (rechunkFixedSizeSamples) {
      long[] chunkOffsetsBytes = new long[chunkIterator.length];
      int[] chunkSampleCounts = new int[chunkIterator.length];
      while (chunkIterator.moveNext()) {
        chunkOffsetsBytes[chunkIterator.index] = chunkIterator.offset;
        chunkSampleCounts[chunkIterator.index] = chunkIterator.numSamples;
      }
      FixedSampleSizeRechunker.Results rechunkedResults =
          FixedSampleSizeRechunker.rechunk(
              fixedSampleSize, chunkOffsetsBytes, chunkSampleCounts, timestampDeltaInTimeUnits);
      offsets = rechunkedResults.offsets;
      sizes = rechunkedResults.sizes;
      maximumSize = rechunkedResults.maximumSize;
      timestamps = rechunkedResults.timestamps;
      flags = rechunkedResults.flags;
      duration = rechunkedResults.duration;
    } else {
      offsets = new long[sampleCount];
      sizes = new int[sampleCount];
      timestamps = new long[sampleCount];
      flags = new int[sampleCount];
      long offset = 0;
      int remainingSamplesInChunk = 0;

      for (int i = 0; i < sampleCount; i++) {
        // Advance to the next chunk if necessary.
        boolean chunkDataComplete = true;
        while (remainingSamplesInChunk == 0 && (chunkDataComplete = chunkIterator.moveNext())) {
          offset = chunkIterator.offset;
          remainingSamplesInChunk = chunkIterator.numSamples;
        }
        if (!chunkDataComplete) {
          Log.w(TAG, "Unexpected end of chunk data");
          sampleCount = i;
          offsets = Arrays.copyOf(offsets, sampleCount);
          sizes = Arrays.copyOf(sizes, sampleCount);
          timestamps = Arrays.copyOf(timestamps, sampleCount);
          flags = Arrays.copyOf(flags, sampleCount);
          break;
        }

        // Add on the timestamp offset if ctts is present.
        if (ctts != null) {
          while (remainingSamplesAtTimestampOffset == 0 && remainingTimestampOffsetChanges > 0) {
            remainingSamplesAtTimestampOffset = ctts.readUnsignedIntToInt();
            // BMFF 规范（ISO/IEC 14496-12）规定，在版本 0 的 ctts 盒中，样本偏移量应为无符号整数，
            // 但某些流违反了规范，使用了有符号整数。在这里始终将样本偏移量解码为有符号整数是安全的，
            // 因为无符号整数仍然会被正确解析（除非它们的最高位被设置，但在实践中这永远不会发生，
            // 因为样本偏移量总是很小）。
            timestampOffset = ctts.readInt();
            remainingTimestampOffsetChanges--;
          }
          remainingSamplesAtTimestampOffset--;
        }

        offsets[i] = offset;
        sizes[i] = sampleSizeBox.readNextSampleSize();
        if (sizes[i] > maximumSize) {
          maximumSize = sizes[i];
        }
        timestamps[i] = timestampTimeUnits + timestampOffset;

        // All samples are synchronization samples if the stss is not present.
        flags[i] = stss == null ? C.BUFFER_FLAG_KEY_FRAME : 0;
        if (i == nextSynchronizationSampleIndex) {
          flags[i] = C.BUFFER_FLAG_KEY_FRAME;
          remainingSynchronizationSamples--;
          if (remainingSynchronizationSamples > 0) {
            nextSynchronizationSampleIndex = checkNotNull(stss).readUnsignedIntToInt() - 1;
          }
        }

        // Add on the duration of this sample.
        timestampTimeUnits += timestampDeltaInTimeUnits;
        remainingSamplesAtTimestampDelta--;
        if (remainingSamplesAtTimestampDelta == 0 && remainingTimestampDeltaChanges > 0) {
          remainingSamplesAtTimestampDelta = stts.readUnsignedIntToInt();
          // BMFF 规范（ISO/IEC 14496-12）规定，在 stts 盒中，样本时间差值应为无符号整数，
          // 但某些流违反了规范，使用了有符号整数。参见 https://github.com/google/ExoPlayer/issues/3384。
          // 在这里始终将样本时间差值解码为有符号整数是安全的，因为无符号整数仍然会被正确解析
          // （除非它们的最高位被设置，但在实践中这永远不会发生，因为样本时间差值总是很小）。
          timestampDeltaInTimeUnits = stts.readInt();
          remainingTimestampDeltaChanges--;
        }

        offset += sizes[i];
        remainingSamplesInChunk--;
      }
      duration = timestampTimeUnits + timestampOffset;

      // 如果 stbl 的子盒不一致，则容器格式错误，但流可能仍然可以播放。
      boolean isCttsValid = true;
      if (ctts != null) {
        while (remainingTimestampOffsetChanges > 0) {
          if (ctts.readUnsignedIntToInt() != 0) {
            isCttsValid = false;
            break;
          }
          ctts.readInt(); // Ignore offset.
          remainingTimestampOffsetChanges--;
        }
      }
      if (remainingSynchronizationSamples != 0
          || remainingSamplesAtTimestampDelta != 0
          || remainingSamplesInChunk != 0
          || remainingTimestampDeltaChanges != 0
          || remainingSamplesAtTimestampOffset != 0
          || !isCttsValid) {
        Log.w(
            TAG,
            "Inconsistent stbl box for track "
                + track.id
                + ": remainingSynchronizationSamples "
                + remainingSynchronizationSamples
                + ", remainingSamplesAtTimestampDelta "
                + remainingSamplesAtTimestampDelta
                + ", remainingSamplesInChunk "
                + remainingSamplesInChunk
                + ", remainingTimestampDeltaChanges "
                + remainingTimestampDeltaChanges
                + ", remainingSamplesAtTimestampOffset "
                + remainingSamplesAtTimestampOffset
                + (!isCttsValid ? ", ctts invalid" : ""));
      }
    }
    long durationUs = Util.scaleLargeTimestamp(duration, C.MICROS_PER_SECOND, track.timescale);

    if (track.editListDurations == null) {
      Util.scaleLargeTimestampsInPlace(timestamps, C.MICROS_PER_SECOND, track.timescale);
      return new TrackSampleTable(
          track, offsets, sizes, maximumSize, timestamps, flags, durationUs);
    }

    // 参见 BMFF 规范（ISO/IEC 14496-12）第 8.6.6 小节。不支持需要从同步样本预滚的编辑列表。
    // 仅支持在编辑列表中有一个编辑，并且从轨道的开始/结束移除的样本数少于
    // MAX_GAPLESS_TRIM_SIZE_SAMPLES 的部分音频样本截断。此实现处理简单的样本丢弃/延迟。
    // 提取器可能会进一步限制哪些编辑后的流可以播放。

    if (track.editListDurations.length == 1
        && track.type == C.TRACK_TYPE_AUDIO
        && timestamps.length >= 2) {
      long editStartTime = checkNotNull(track.editListMediaTimes)[0];
      long editEndTime =
          editStartTime
              + Util.scaleLargeTimestamp(
              track.editListDurations[0], track.timescale, track.movieTimescale);
      if (canApplyEditWithGaplessInfo(timestamps, duration, editStartTime, editEndTime)) {
        long paddingTimeUnits = duration - editEndTime;
        long encoderDelay =
            Util.scaleLargeTimestamp(
                editStartTime - timestamps[0], track.format.sampleRate, track.timescale);
        long encoderPadding =
            Util.scaleLargeTimestamp(paddingTimeUnits, track.format.sampleRate, track.timescale);
        if ((encoderDelay != 0 || encoderPadding != 0)
            && encoderDelay <= Integer.MAX_VALUE
            && encoderPadding <= Integer.MAX_VALUE) {
          gaplessInfoHolder.encoderDelay = (int) encoderDelay;
          gaplessInfoHolder.encoderPadding = (int) encoderPadding;
          Util.scaleLargeTimestampsInPlace(timestamps, C.MICROS_PER_SECOND, track.timescale);
          long editedDurationUs =
              Util.scaleLargeTimestamp(
                  track.editListDurations[0], C.MICROS_PER_SECOND, track.movieTimescale);
          return new TrackSampleTable(
              track, offsets, sizes, maximumSize, timestamps, flags, editedDurationUs);
        }
      }
    }

    if (track.editListDurations.length == 1 && track.editListDurations[0] == 0) {
      // 当前版本的规范对于未分段文件中 segment_duration 为零的编辑的处理方式没有明确说明。
      // 我们将其作为特殊情况处理，并包含编辑中的所有样本。
      long editStartTime = checkNotNull(track.editListMediaTimes)[0];
      for (int i = 0; i < timestamps.length; i++) {
        timestamps[i] =
            Util.scaleLargeTimestamp(
                timestamps[i] - editStartTime, C.MICROS_PER_SECOND, track.timescale);
      }
      durationUs =
          Util.scaleLargeTimestamp(duration - editStartTime, C.MICROS_PER_SECOND, track.timescale);
      return new TrackSampleTable(
          track, offsets, sizes, maximumSize, timestamps, flags, durationUs);
    }

    // 在应用编辑列表时，我们需要包含末尾的任何部分裁剪样本，以确保最终输出的渲染正确
    // （参见 https://github.com/google/ExoPlayer/issues/2408）。对于仅音频的情况，
    // 我们可以省略任何恰好从编辑结束点开始的样本，因为在这种情况下没有部分音频。
    boolean omitZeroDurationClippedSample = track.type == C.TRACK_TYPE_AUDIO;

    // 在应用编辑后计算样本的数量。
    int editedSampleCount = 0;
    int nextSampleIndex = 0;
    boolean copyMetadata = false;
    int[] startIndices = new int[track.editListDurations.length];
    int[] endIndices = new int[track.editListDurations.length];
    long[] editListMediaTimes = checkNotNull(track.editListMediaTimes);
    for (int i = 0; i < track.editListDurations.length; i++) {
      long editMediaTime = editListMediaTimes[i];
      if (editMediaTime != -1) {
        long editDuration =
            Util.scaleLargeTimestamp(
                track.editListDurations[i], track.timescale, track.movieTimescale);
        // 时间戳数组是按照从媒体中读取的顺序排列的，可能不是严格排序的。
        // 然而，所有同步帧都保证是按顺序排列的，任何乱序的帧都会出现在它们各自的同步帧之后。
        // 这确保了尽管二分查找的结果可能不完全准确（由于乱序的时间戳），
        // 但下面的逻辑保证了起始和结束索引的正确性。
        //
        // startIndices 的计算会找到小于或等于 editMediaTime 的最大时间戳。
        // 然后它会向后遍历，以确保索引指向一个同步帧，因为解码必须从关键帧开始。
        startIndices[i] =
            Util.binarySearchFloor(
                timestamps, editMediaTime, /* inclusive= */ true, /* stayInBounds= */ true);
        while (startIndices[i] >= 0 && (flags[startIndices[i]] & C.BUFFER_FLAG_KEY_FRAME) == 0) {
          startIndices[i]--;
        }
        // endIndices 的计算会找到大于 editMediaTime + editDuration 的最小时间戳，
        // 除非 omitZeroDurationClippedSample 为 true，在这种情况下，
        // 它会找到大于或等于 editMediaTime + editDuration 的最小时间戳。
        endIndices[i] =
            Util.binarySearchCeil(
                timestamps,
                editMediaTime + editDuration,
                /* inclusive= */ omitZeroDurationClippedSample,
                /* stayInBounds= */ false);
        if (track.type == C.TRACK_TYPE_VIDEO) {
          // 为了处理可能具有小于或等于 editMediaTime + editDuration 的时间戳但仍然落在有效范围内的乱序视频帧，
          // 循环会向前遍历时间戳数组，以确保包含所有时间戳在编辑持续时间内的帧。
          while (endIndices[i] < timestamps.length - 1
              && timestamps[endIndices[i] + 1] <= (editMediaTime + editDuration)) {
            endIndices[i]++;
          }
        }
        editedSampleCount += endIndices[i] - startIndices[i];
        copyMetadata |= nextSampleIndex != startIndices[i];
        nextSampleIndex = endIndices[i];
      }
    }
    copyMetadata |= editedSampleCount != sampleCount;

    // Calculate edited sample timestamps and update the corresponding metadata arrays.
    long[] editedOffsets = copyMetadata ? new long[editedSampleCount] : offsets;
    int[] editedSizes = copyMetadata ? new int[editedSampleCount] : sizes;
    int editedMaximumSize = copyMetadata ? 0 : maximumSize;
    int[] editedFlags = copyMetadata ? new int[editedSampleCount] : flags;
    long[] editedTimestamps = new long[editedSampleCount];
    long pts = 0;
    int sampleIndex = 0;
    boolean hasPrerollSamples = false;
    for (int i = 0; i < track.editListDurations.length; i++) {
      long editMediaTime = track.editListMediaTimes[i];
      int startIndex = startIndices[i];
      int endIndex = endIndices[i];
      if (copyMetadata) {
        int count = endIndex - startIndex;
        System.arraycopy(offsets, startIndex, editedOffsets, sampleIndex, count);
        System.arraycopy(sizes, startIndex, editedSizes, sampleIndex, count);
        System.arraycopy(flags, startIndex, editedFlags, sampleIndex, count);
      }
      for (int j = startIndex; j < endIndex; j++) {
        long ptsUs = Util.scaleLargeTimestamp(pts, C.MICROS_PER_SECOND, track.movieTimescale);
        long timeInSegmentUs =
            Util.scaleLargeTimestamp(
                timestamps[j] - editMediaTime, C.MICROS_PER_SECOND, track.timescale);
        if (timeInSegmentUs < 0) {
          hasPrerollSamples = true;
        }
        editedTimestamps[sampleIndex] = ptsUs + timeInSegmentUs;
        if (copyMetadata && editedSizes[sampleIndex] > editedMaximumSize) {
          editedMaximumSize = sizes[j];
        }
        sampleIndex++;
      }
      pts += track.editListDurations[i];
    }
    long editedDurationUs =
        Util.scaleLargeTimestamp(pts, C.MICROS_PER_SECOND, track.movieTimescale);
    if (hasPrerollSamples) {
      Format format = track.format.buildUpon().setHasPrerollSamples(true).build();
      track = track.copyWithFormat(format);
    }
    return new TrackSampleTable(
        track,
        editedOffsets,
        editedSizes,
        editedMaximumSize,
        editedTimestamps,
        editedFlags,
        editedDurationUs);
  }

  @Nullable
  private static Metadata parseUdtaMeta(ParsableByteArray meta, int limit) {
    meta.skipBytes(Mp4Box.HEADER_SIZE);
    maybeSkipRemainingMetaBoxHeaderBytes(meta);
    while (meta.getPosition() < limit) {
      int atomPosition = meta.getPosition();
      int atomSize = meta.readInt();
      int atomType = meta.readInt();
      if (atomType == Mp4Box.TYPE_ilst) {
        meta.setPosition(atomPosition);
        return parseIlst(meta, atomPosition + atomSize);
      }
      meta.setPosition(atomPosition + atomSize);
    }
    return null;
  }

  @Nullable
  private static Metadata parseIlst(ParsableByteArray ilst, int limit) {
    ilst.skipBytes(Mp4Box.HEADER_SIZE);
    ArrayList<Metadata.Entry> entries = new ArrayList<>();
    while (ilst.getPosition() < limit) {
      @Nullable Metadata.Entry entry = MetadataUtil.parseIlstElement(ilst);
      if (entry != null) {
        entries.add(entry);
      }
    }
    return entries.isEmpty() ? null : new Metadata(entries);
  }

  /**
   * Parses the location metadata from the xyz atom.
   */
  @Nullable
  private static Metadata parseXyz(ParsableByteArray xyzBox) {
    int length = xyzBox.readShort();
    xyzBox.skipBytes(2); // language code.
    String location = xyzBox.readString(length);
    // The location string looks like "+35.1345-15.1020/".
    int plusSignIndex = location.lastIndexOf('+');
    int minusSignIndex = location.lastIndexOf('-');
    int latitudeEndIndex = max(plusSignIndex, minusSignIndex);
    try {
      float latitude = Float.parseFloat(location.substring(0, latitudeEndIndex));
      float longitude =
          Float.parseFloat(location.substring(latitudeEndIndex, location.length() - 1));
      return new Metadata(new Mp4LocationData(latitude, longitude));
    } catch (IndexOutOfBoundsException | NumberFormatException exception) {
      // Invalid input.
      return null;
    }
  }

  /**
   * 解析 tkhd 原子（定义在 ISO/IEC 14496-12 中）。
   *
   * @param tkhd 要解析的 tkhd 原子内容。
   * @return 包含解析数据的对象。
   */
  private static TkhdData parseTkhd(ParsableByteArray tkhd) {
    tkhd.setPosition(Mp4Box.HEADER_SIZE);
    int fullAtom = tkhd.readInt();
    int version = parseFullBoxVersion(fullAtom);

    tkhd.skipBytes(version == 0 ? 8 : 16);
    int trackId = tkhd.readInt();

    tkhd.skipBytes(4);
    boolean durationUnknown = true;
    int durationPosition = tkhd.getPosition();
    int durationByteCount = version == 0 ? 4 : 8;
    for (int i = 0; i < durationByteCount; i++) {
      if (tkhd.getData()[durationPosition + i] != -1) {
        durationUnknown = false;
        break;
      }
    }
    long duration;
    if (durationUnknown) {
      tkhd.skipBytes(durationByteCount);
      duration = C.TIME_UNSET;
    } else {
      duration = version == 0 ? tkhd.readUnsignedInt() : tkhd.readUnsignedLongToLong();
      if (duration == 0) {
        // 0 持续时间通常表示文件是完全分段的（即所有媒体样本都在片段中）。将其视为未知。
        duration = C.TIME_UNSET;
      }
    }

    tkhd.skipBytes(16);
    int a00 = tkhd.readInt();
    int a01 = tkhd.readInt();
    tkhd.skipBytes(4);
    int a10 = tkhd.readInt();
    int a11 = tkhd.readInt();

    int rotationDegrees;
    int fixedOne = 65536;
    if (a00 == 0 && a01 == fixedOne && a10 == -fixedOne && a11 == 0) {
      rotationDegrees = 90;
    } else if (a00 == 0 && a01 == -fixedOne && a10 == fixedOne && a11 == 0) {
      rotationDegrees = 270;
    } else if (a00 == -fixedOne && a01 == 0 && a10 == 0 && a11 == -fixedOne) {
      rotationDegrees = 180;
    } else {
      // 仅支持 0、90、180 和 270 度。将其他值视为 0 度。
      rotationDegrees = 0;
    }

    return new TkhdData(trackId, duration, rotationDegrees);
  }

  /**
   * 解析 hdlr 原子。
   *
   * @param hdlr 要解码的 hdlr 原子。
   * @return 处理程序的值。
   */
  private static int parseHdlr(ParsableByteArray hdlr) {
    hdlr.setPosition(Mp4Box.FULL_HEADER_SIZE + 4);
    return hdlr.readInt();
  }

  /**
   * 返回给定处理程序值对应的轨道类型。
   */
  private static @C.TrackType int getTrackTypeForHdlr(int hdlr) {
    if (hdlr == TYPE_soun) {
      return C.TRACK_TYPE_AUDIO;
    } else if (hdlr == TYPE_vide) {
      return C.TRACK_TYPE_VIDEO;
    } else if (hdlr == TYPE_text || hdlr == TYPE_sbtl || hdlr == TYPE_subt || hdlr == TYPE_clcp) {
      return C.TRACK_TYPE_TEXT;
    } else if (hdlr == TYPE_meta) {
      return C.TRACK_TYPE_METADATA;
    } else {
      return C.TRACK_TYPE_UNKNOWN;
    }
  }

  /**
   * 解析 mdhd 原子（定义在 ISO/IEC 14496-12 中）。
   *
   * @param mdhd 要解码的 mdhd 原子。
   * @return 包含解析数据的 {@link MdhdData} 对象。
   */
  private static MdhdData parseMdhd(ParsableByteArray mdhd) {
    mdhd.setPosition(Mp4Box.HEADER_SIZE);
    int fullAtom = mdhd.readInt();
    int version = parseFullBoxVersion(fullAtom);
    mdhd.skipBytes(version == 0 ? 8 : 16);
    long timescale = mdhd.readUnsignedInt();
    boolean mediaDurationUnknown = true;
    int mediaDurationPosition = mdhd.getPosition();
    int mediaDurationByteCount = version == 0 ? 4 : 8;
    for (int i = 0; i < mediaDurationByteCount; i++) {
      if (mdhd.getData()[mediaDurationPosition + i] != -1) {
        mediaDurationUnknown = false;
        break;
      }
    }
    long mediaDurationUs;
    if (mediaDurationUnknown) {
      mdhd.skipBytes(mediaDurationByteCount);
      mediaDurationUs = C.TIME_UNSET;
    } else {
      long mediaDuration = version == 0 ? mdhd.readUnsignedInt() : mdhd.readUnsignedLongToLong();
      if (mediaDuration == 0) {
        // 0 持续时间通常表示文件是完全分段的（即所有媒体样本都在片段中）。将其视为未知。
        mediaDurationUs = C.TIME_UNSET;
      } else {
        mediaDurationUs = Util.scaleLargeTimestamp(mediaDuration, C.MICROS_PER_SECOND, timescale);
      }
    }
    int languageCode = mdhd.readUnsignedShort();
    String language =
        ""
            + (char) (((languageCode >> 10) & 0x1F) + 0x60)
            + (char) (((languageCode >> 5) & 0x1F) + 0x60)
            + (char) ((languageCode & 0x1F) + 0x60);
    return new MdhdData(timescale, mediaDurationUs, language);
  }

  /**
   * 解析 stsd 原子（定义在 ISO/IEC 14496-12 中）。
   *
   * @param stsd            要解码的 stsd 原子。
   * @param trackId         轨道在其容器中的标识符。
   * @param rotationDegrees 轨道的旋转角度（以度为单位）。
   * @param language        轨道的语言。
   * @param drmInitData     要包含在格式中的 {@link DrmInitData}，如果不需要则为 {@code null}。
   * @param isQuickTime     如果为 QuickTime 媒体则为 true，否则为 false。
   * @return 包含解析数据的对象。
   */
  private static StsdData parseStsd(
      ParsableByteArray stsd,
      int trackId,
      int rotationDegrees,
      String language,
      @Nullable DrmInitData drmInitData,
      boolean isQuickTime)
      throws ParserException {
    stsd.setPosition(Mp4Box.FULL_HEADER_SIZE);
    int numberOfEntries = stsd.readInt();
    StsdData out = new StsdData(numberOfEntries);
    for (int i = 0; i < numberOfEntries; i++) {
      int childStartPosition = stsd.getPosition();
      int childAtomSize = stsd.readInt();
      ExtractorUtil.checkContainerInput(childAtomSize > 0, "childAtomSize must be positive");
      int childAtomType = stsd.readInt();
      if (childAtomType == Mp4Box.TYPE_avc1
          || childAtomType == Mp4Box.TYPE_avc3
          || childAtomType == Mp4Box.TYPE_encv
          || childAtomType == Mp4Box.TYPE_m1v_
          || childAtomType == Mp4Box.TYPE_mp4v
          || childAtomType == Mp4Box.TYPE_hvc1
          || childAtomType == Mp4Box.TYPE_hev1
          || childAtomType == Mp4Box.TYPE_s263
          || childAtomType == Mp4Box.TYPE_H263
          || childAtomType == Mp4Box.TYPE_h263
          || childAtomType == Mp4Box.TYPE_vp08
          || childAtomType == Mp4Box.TYPE_vp09
          || childAtomType == Mp4Box.TYPE_av01
          || childAtomType == Mp4Box.TYPE_dvav
          || childAtomType == Mp4Box.TYPE_dva1
          || childAtomType == Mp4Box.TYPE_dvhe
          || childAtomType == Mp4Box.TYPE_dvh1) {
        parseVideoSampleEntry(
            stsd,
            childAtomType,
            childStartPosition,
            childAtomSize,
            trackId,
            rotationDegrees,
            drmInitData,
            out,
            i);
      } else if (childAtomType == Mp4Box.TYPE_mp4a
          || childAtomType == Mp4Box.TYPE_enca
          || childAtomType == Mp4Box.TYPE_ac_3
          || childAtomType == Mp4Box.TYPE_ec_3
          || childAtomType == Mp4Box.TYPE_ac_4
          || childAtomType == Mp4Box.TYPE_mlpa
          || childAtomType == Mp4Box.TYPE_dtsc
          || childAtomType == Mp4Box.TYPE_dtse
          || childAtomType == Mp4Box.TYPE_dtsh
          || childAtomType == Mp4Box.TYPE_dtsl
          || childAtomType == Mp4Box.TYPE_dtsx
          || childAtomType == Mp4Box.TYPE_samr
          || childAtomType == Mp4Box.TYPE_sawb
          || childAtomType == Mp4Box.TYPE_lpcm
          || childAtomType == Mp4Box.TYPE_sowt
          || childAtomType == Mp4Box.TYPE_twos
          || childAtomType == Mp4Box.TYPE__mp2
          || childAtomType == Mp4Box.TYPE__mp3
          || childAtomType == Mp4Box.TYPE_mha1
          || childAtomType == Mp4Box.TYPE_mhm1
          || childAtomType == Mp4Box.TYPE_alac
          || childAtomType == Mp4Box.TYPE_alaw
          || childAtomType == Mp4Box.TYPE_ulaw
          || childAtomType == Mp4Box.TYPE_Opus
          || childAtomType == Mp4Box.TYPE_fLaC
          || childAtomType == Mp4Box.TYPE_iamf) {
        parseAudioSampleEntry(
            stsd,
            childAtomType,
            childStartPosition,
            childAtomSize,
            trackId,
            language,
            isQuickTime,
            drmInitData,
            out,
            i);
      } else if (childAtomType == Mp4Box.TYPE_TTML
          || childAtomType == Mp4Box.TYPE_tx3g
          || childAtomType == Mp4Box.TYPE_wvtt
          || childAtomType == Mp4Box.TYPE_stpp
          || childAtomType == Mp4Box.TYPE_c608) {
        parseTextSampleEntry(
            stsd, childAtomType, childStartPosition, childAtomSize, trackId, language, out);
      } else if (childAtomType == Mp4Box.TYPE_mett) {
        parseMetaDataSampleEntry(stsd, childAtomType, childStartPosition, trackId, out);
      } else if (childAtomType == Mp4Box.TYPE_camm) {
        out.format =
            new Format.Builder()
                .setId(trackId)
                .setSampleMimeType(MimeTypes.APPLICATION_CAMERA_MOTION)
                .build();
      }
      stsd.setPosition(childStartPosition + childAtomSize);
    }
    return out;
  }

  private static void parseTextSampleEntry(
      ParsableByteArray parent,
      int atomType,
      int position,
      int atomSize,
      int trackId,
      String language,
      StsdData out) {
    parent.setPosition(position + Mp4Box.HEADER_SIZE + StsdData.STSD_HEADER_SIZE);

    // Default values.
    @Nullable ImmutableList<byte[]> initializationData = null;
    long subsampleOffsetUs = Format.OFFSET_SAMPLE_RELATIVE;

    String mimeType;
    if (atomType == Mp4Box.TYPE_TTML) {
      mimeType = MimeTypes.APPLICATION_TTML;
    } else if (atomType == Mp4Box.TYPE_tx3g) {
      mimeType = MimeTypes.APPLICATION_TX3G;
      int sampleDescriptionLength = atomSize - Mp4Box.HEADER_SIZE - 8;
      byte[] sampleDescriptionData = new byte[sampleDescriptionLength];
      parent.readBytes(sampleDescriptionData, 0, sampleDescriptionLength);
      initializationData = ImmutableList.of(sampleDescriptionData);
    } else if (atomType == Mp4Box.TYPE_wvtt) {
      mimeType = MimeTypes.APPLICATION_MP4VTT;
    } else if (atomType == Mp4Box.TYPE_stpp) {
      mimeType = MimeTypes.APPLICATION_TTML;
      subsampleOffsetUs = 0; // Subsample timing is absolute.
    } else if (atomType == Mp4Box.TYPE_c608) {
      // Defined by the QuickTime File Format specification.
      mimeType = MimeTypes.APPLICATION_MP4CEA608;
      out.requiredSampleTransformation = Track.TRANSFORMATION_CEA608_CDAT;
    } else {
      // Never happens.
      throw new IllegalStateException();
    }

    out.format =
        new Format.Builder()
            .setId(trackId)
            .setSampleMimeType(mimeType)
            .setLanguage(language)
            .setSubsampleOffsetUs(subsampleOffsetUs)
            .setInitializationData(initializationData)
            .build();
  }

  // hdrStaticInfo is allocated using allocate() in allocateHdrStaticInfo().
  @SuppressWarnings("ByteBufferBackingArray")
  private static void parseVideoSampleEntry(
      ParsableByteArray parent,
      int atomType,
      int position,
      int size,
      int trackId,
      int rotationDegrees,
      @Nullable DrmInitData drmInitData,
      StsdData out,
      int entryIndex)
      throws ParserException {
    parent.setPosition(position + Mp4Box.HEADER_SIZE + StsdData.STSD_HEADER_SIZE);

    parent.skipBytes(16);
    int width = parent.readUnsignedShort();
    int height = parent.readUnsignedShort();
    boolean pixelWidthHeightRatioFromPasp = false;
    float pixelWidthHeightRatio = 1;
    // Set default luma and chroma bit depths to 8 as old codecs might not even signal them
    int bitdepthLuma = 8;
    int bitdepthChroma = 8;
    parent.skipBytes(50);

    int childPosition = parent.getPosition();
    if (atomType == Mp4Box.TYPE_encv) {
      @Nullable
      Pair<Integer, TrackEncryptionBox> sampleEntryEncryptionData =
          parseSampleEntryEncryptionData(parent, position, size);
      if (sampleEntryEncryptionData != null) {
        atomType = sampleEntryEncryptionData.first;
        drmInitData =
            drmInitData == null
                ? null
                : drmInitData.copyWithSchemeType(sampleEntryEncryptionData.second.schemeType);
        out.trackEncryptionBoxes[entryIndex] = sampleEntryEncryptionData.second;
      }
      parent.setPosition(childPosition);
    }
    // TODO: 当 [Internal: b/63092960] 修复后取消注释。
    // else {
    //   drmInitData = null;
    // }

    @Nullable String mimeType = null;
    if (atomType == Mp4Box.TYPE_m1v_) {
      mimeType = MimeTypes.VIDEO_MPEG;
    } else if (atomType == Mp4Box.TYPE_H263) {
      mimeType = MimeTypes.VIDEO_H263;
    }

    @Nullable List<byte[]> initializationData = null;
    @Nullable String codecs = null;
    @Nullable byte[] projectionData = null;
    @C.StereoMode int stereoMode = Format.NO_VALUE;
    @Nullable EsdsData esdsData = null;
    int maxNumReorderSamples = Format.NO_VALUE;
    @Nullable NalUnitUtil.H265VpsData vpsData = null;

    // HDR related metadata.
    @C.ColorSpace int colorSpace = Format.NO_VALUE;
    @C.ColorRange int colorRange = Format.NO_VALUE;
    @C.ColorTransfer int colorTransfer = Format.NO_VALUE;
    // HDR 静态信息的格式定义在 CTA-861-G:2017 表 45 中。
    @Nullable ByteBuffer hdrStaticInfo = null;

    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childStartPosition = parent.getPosition();
      int childAtomSize = parent.readInt();
      if (childAtomSize == 0 && parent.getPosition() - position == size) {
        // 处理 MOV 文件中可选的结尾四个零字节。
        break;
      }
      ExtractorUtil.checkContainerInput(childAtomSize > 0, "childAtomSize must be positive");
      int childAtomType = parent.readInt();
      if (childAtomType == Mp4Box.TYPE_avcC) {
        ExtractorUtil.checkContainerInput(mimeType == null, /* message= */ null);
        mimeType = MimeTypes.VIDEO_H264;
        parent.setPosition(childStartPosition + Mp4Box.HEADER_SIZE);
        AvcConfig avcConfig = AvcConfig.parse(parent);
        initializationData = avcConfig.initializationData;
        out.nalUnitLengthFieldLength = avcConfig.nalUnitLengthFieldLength;
        if (!pixelWidthHeightRatioFromPasp) {
          pixelWidthHeightRatio = avcConfig.pixelWidthHeightRatio;
        }
        codecs = avcConfig.codecs;
        maxNumReorderSamples = avcConfig.maxNumReorderFrames;
        colorSpace = avcConfig.colorSpace;
        colorRange = avcConfig.colorRange;
        colorTransfer = avcConfig.colorTransfer;
        bitdepthLuma = avcConfig.bitdepthLuma;
        bitdepthChroma = avcConfig.bitdepthChroma;
      } else if (childAtomType == Mp4Box.TYPE_hvcC) {
        ExtractorUtil.checkContainerInput(mimeType == null, /* message= */ null);
        mimeType = MimeTypes.VIDEO_H265;
        parent.setPosition(childStartPosition + Mp4Box.HEADER_SIZE);
        HevcConfig hevcConfig = HevcConfig.parse(parent);
        initializationData = hevcConfig.initializationData;
        out.nalUnitLengthFieldLength = hevcConfig.nalUnitLengthFieldLength;
        if (!pixelWidthHeightRatioFromPasp) {
          pixelWidthHeightRatio = hevcConfig.pixelWidthHeightRatio;
        }
        maxNumReorderSamples = hevcConfig.maxNumReorderPics;
        codecs = hevcConfig.codecs;
        if (hevcConfig.stereoMode != Format.NO_VALUE) {
          // HEVCDecoderConfigurationRecord may include 3D reference displays information SEI.
          stereoMode = hevcConfig.stereoMode;
        }
        colorSpace = hevcConfig.colorSpace;
        colorRange = hevcConfig.colorRange;
        colorTransfer = hevcConfig.colorTransfer;
        bitdepthLuma = hevcConfig.bitdepthLuma;
        bitdepthChroma = hevcConfig.bitdepthChroma;
        vpsData = hevcConfig.vpsData;
      } else if (childAtomType == Mp4Box.TYPE_lhvC) {
        // lhvC 原子必须跟在 hvcC 原子之后，因此媒体类型必须已经设置。
        ExtractorUtil.checkContainerInput(
            MimeTypes.VIDEO_H265.equals(mimeType), "lhvC 必须跟在 hvcC 原子之后");
        ExtractorUtil.checkContainerInput(
            vpsData != null && vpsData.layerInfos.size() >= 2, "必须至少有两个层");

        parent.setPosition(childStartPosition + Mp4Box.HEADER_SIZE);
        HevcConfig lhevcConfig = HevcConfig.parseLayered(parent, checkNotNull(vpsData));
        ExtractorUtil.checkContainerInput(
            out.nalUnitLengthFieldLength == lhevcConfig.nalUnitLengthFieldLength,
            "nalUnitLengthFieldLength 必须与 hvcC 和 lhvC 原子一致");

        // 目前仅支持立体 MV-HEVC，因此两个视图的以下配置值必须相同。
        if (lhevcConfig.colorSpace != Format.NO_VALUE) {
          ExtractorUtil.checkContainerInput(
              colorSpace == lhevcConfig.colorSpace, "两个视图的 colorSpace 必须相同");
        }
        if (lhevcConfig.colorRange != Format.NO_VALUE) {
          ExtractorUtil.checkContainerInput(
              colorRange == lhevcConfig.colorRange, "两个视图的 colorRange 必须相同");
        }
        if (lhevcConfig.colorTransfer != Format.NO_VALUE) {
          ExtractorUtil.checkContainerInput(
              colorTransfer == lhevcConfig.colorTransfer, "两个视图的 colorTransfer 必须相同");
        }
        ExtractorUtil.checkContainerInput(
            bitdepthLuma == lhevcConfig.bitdepthLuma, "两个视图的 bitdepthLuma 必须相同");
        ExtractorUtil.checkContainerInput(
            bitdepthChroma == lhevcConfig.bitdepthChroma, "两个视图的 bitdepthChroma 必须相同");

        mimeType = MimeTypes.VIDEO_MV_HEVC;
        if (initializationData != null) {
          initializationData =
              ImmutableList.<byte[]>builder()
                  .addAll(initializationData)
                  .addAll(lhevcConfig.initializationData)
                  .build();
        } else {
          ExtractorUtil.checkContainerInput(
              false, "initializationData 必须已从 hvcC 原子中设置");
        }
        codecs = lhevcConfig.codecs;
      } else if (childAtomType == Mp4Box.TYPE_vexu) {
        VexuData vexuData = parseVideoExtendedUsageBox(parent, childStartPosition, childAtomSize);
        if (vexuData != null && vexuData.eyesData != null) {
          if (vpsData != null && vpsData.layerInfos.size() >= 2) {
            // 这是 MV-HEVC（多视图高效视频编码）的情况，因此应将两个视图都标记为可用。
            ExtractorUtil.checkContainerInput(
                vexuData.hasBothEyeViews(), "both eye views must be marked as available");
            // 基于 Apple 提出的立体视频 ISOBMFF 扩展的第 1.4.3 小节
            //（https://developer.apple.com/av-foundation/Stereo-Video-ISOBMFF-Extensions.pdf）：
            // "对于多视图编码，没有隐含的顺序，eye_views_reversed 字段应设置为 0"。
            ExtractorUtil.checkContainerInput(
                !vexuData.eyesData.striData.eyeViewsReversed,
                "for MV-HEVC, eye_views_reversed must be set to false");
          } else if (stereoMode == Format.NO_VALUE) {
            stereoMode =
                vexuData.eyesData.striData.eyeViewsReversed
                    ? C.STEREO_MODE_INTERLEAVED_RIGHT_PRIMARY
                    : C.STEREO_MODE_INTERLEAVED_LEFT_PRIMARY;
          }
        }
      } else if (childAtomType == Mp4Box.TYPE_dvcC || childAtomType == Mp4Box.TYPE_dvvC) {
        @Nullable DolbyVisionConfig dolbyVisionConfig = DolbyVisionConfig.parse(parent);
        if (dolbyVisionConfig != null) {
          codecs = dolbyVisionConfig.codecs;
          mimeType = MimeTypes.VIDEO_DOLBY_VISION;
        }
      } else if (childAtomType == Mp4Box.TYPE_vpcC) {
        ExtractorUtil.checkContainerInput(mimeType == null, /* message= */ null);
        mimeType = (atomType == Mp4Box.TYPE_vp08) ? MimeTypes.VIDEO_VP8 : MimeTypes.VIDEO_VP9;
        parent.setPosition(childStartPosition + Mp4Box.FULL_HEADER_SIZE);
        // See vpcC atom syntax: https://www.webmproject.org/vp9/mp4/#syntax_1
        byte profile = (byte) parent.readUnsignedByte();
        byte level = (byte) parent.readUnsignedByte();
        int byte3 = parent.readUnsignedByte();
        bitdepthLuma = byte3 >> 4;
        bitdepthChroma = bitdepthLuma;
        byte chromaSubsampling = (byte) ((byte3 >> 1) & 0b111);
        if (mimeType.equals(MimeTypes.VIDEO_VP9)) {
          // CSD should be in CodecPrivate format according to VP9 Codec spec.
          initializationData =
              CodecSpecificDataUtil.buildVp9CodecPrivateInitializationData(
                  profile, level, (byte) bitdepthLuma, chromaSubsampling);
        }
        boolean fullRangeFlag = (byte3 & 0b1) != 0;
        int colorPrimaries = parent.readUnsignedByte();
        int transferCharacteristics = parent.readUnsignedByte();
        colorSpace = ColorInfo.isoColorPrimariesToColorSpace(colorPrimaries);
        colorRange = fullRangeFlag ? C.COLOR_RANGE_FULL : C.COLOR_RANGE_LIMITED;
        colorTransfer =
            ColorInfo.isoTransferCharacteristicsToColorTransfer(transferCharacteristics);
      } else if (childAtomType == Mp4Box.TYPE_av1C) {
        mimeType = MimeTypes.VIDEO_AV1;

        int childAtomBodySize = childAtomSize - Mp4Box.HEADER_SIZE;
        byte[] initializationDataChunk = new byte[childAtomBodySize];
        parent.readBytes(initializationDataChunk, /* offset= */ 0, childAtomBodySize);
        initializationData = ImmutableList.of(initializationDataChunk);

        parent.setPosition(childStartPosition + Mp4Box.HEADER_SIZE);
        ColorInfo colorInfo = parseAv1c(parent);

        bitdepthLuma = colorInfo.lumaBitdepth;
        bitdepthChroma = colorInfo.chromaBitdepth;
        colorSpace = colorInfo.colorSpace;
        colorRange = colorInfo.colorRange;
        colorTransfer = colorInfo.colorTransfer;
      } else if (childAtomType == Mp4Box.TYPE_clli) {
        if (hdrStaticInfo == null) {
          hdrStaticInfo = allocateHdrStaticInfo();
        }
        // clli 盒的内容占据 HDR 静态信息数组的最后 4 个字节。注意，
        // 每个字段以大端序读取，并以小端序写入。
        hdrStaticInfo.position(21);
        hdrStaticInfo.putShort(parent.readShort()); // max_content_light_level（最大内容亮度级别）。
        hdrStaticInfo.putShort(parent.readShort()); // max_pic_average_light_level（最大图片平均亮度级别）。
      } else if (childAtomType == Mp4Box.TYPE_mdcv) {
        if (hdrStaticInfo == null) {
          hdrStaticInfo = allocateHdrStaticInfo();
        }
        // mdcv 盒的内容占据 HDR 静态信息数组的第一个字节之后的 20 个字节。注意，
        // 每个字段以大端序读取，并以小端序写入。
        short displayPrimariesGX = parent.readShort();
        short displayPrimariesGY = parent.readShort();
        short displayPrimariesBX = parent.readShort();
        short displayPrimariesBY = parent.readShort();
        short displayPrimariesRX = parent.readShort();
        short displayPrimariesRY = parent.readShort();
        short whitePointX = parent.readShort();
        short whitePointY = parent.readShort();
        long maxDisplayMasteringLuminance = parent.readUnsignedInt();
        long minDisplayMasteringLuminance = parent.readUnsignedInt();

        hdrStaticInfo.position(1);
        hdrStaticInfo.putShort(displayPrimariesRX);
        hdrStaticInfo.putShort(displayPrimariesRY);
        hdrStaticInfo.putShort(displayPrimariesGX);
        hdrStaticInfo.putShort(displayPrimariesGY);
        hdrStaticInfo.putShort(displayPrimariesBX);
        hdrStaticInfo.putShort(displayPrimariesBY);
        hdrStaticInfo.putShort(whitePointX);
        hdrStaticInfo.putShort(whitePointY);
        hdrStaticInfo.putShort((short) (maxDisplayMasteringLuminance / 10000));
        hdrStaticInfo.putShort((short) (minDisplayMasteringLuminance / 10000));
      } else if (childAtomType == Mp4Box.TYPE_d263) {
        ExtractorUtil.checkContainerInput(mimeType == null, /* message= */ null);
        mimeType = MimeTypes.VIDEO_H263;
      } else if (childAtomType == Mp4Box.TYPE_esds) {
        ExtractorUtil.checkContainerInput(mimeType == null, /* message= */ null);
        esdsData = parseEsdsFromParent(parent, childStartPosition);
        mimeType = esdsData.mimeType;
        @Nullable byte[] initializationDataBytes = esdsData.initializationData;
        if (initializationDataBytes != null) {
          initializationData = ImmutableList.of(initializationDataBytes);
        }
      } else if (childAtomType == Mp4Box.TYPE_pasp) {
        pixelWidthHeightRatio = parsePaspFromParent(parent, childStartPosition);
        pixelWidthHeightRatioFromPasp = true;
      } else if (childAtomType == Mp4Box.TYPE_sv3d) {
        projectionData = parseProjFromParent(parent, childStartPosition, childAtomSize);
      } else if (childAtomType == Mp4Box.TYPE_st3d) {
        int version = parent.readUnsignedByte();
        parent.skipBytes(3); // Flags.
        if (version == 0) {
          int layout = parent.readUnsignedByte();
          switch (layout) {
            case 0:
              stereoMode = C.STEREO_MODE_MONO;
              break;
            case 1:
              stereoMode = C.STEREO_MODE_TOP_BOTTOM;
              break;
            case 2:
              stereoMode = C.STEREO_MODE_LEFT_RIGHT;
              break;
            case 3:
              stereoMode = C.STEREO_MODE_STEREO_MESH;
              break;
            default:
              break;
          }
        }
      } else if (childAtomType == Mp4Box.TYPE_colr) {
        // 仅在 'colorSpace' 和 'colorTransfer' 尚未由比特流确定的情况下修改这些值。
        // 缺少颜色描述符（'colorSpace' 和 'colorTransfer'）并不一定意味着 'colorRange' 具有默认值，
        // 因此这里不对其进行验证。
        // 如果存在 'Atom.TYPE_avcC'、'Atom.TYPE_hvcC'、'Atom.TYPE_vpcC' 或 'Atom.TYPE_av1c'，
        // 它们将优先并覆盖任何现有值。
        if (colorSpace == Format.NO_VALUE && colorTransfer == Format.NO_VALUE) {
          int colorType = parent.readInt();
          if (colorType == TYPE_nclx || colorType == TYPE_nclc) {
            // 有关语法的更多信息，请参阅 ISO/IEC 14496-12:2012(E) 的第 8.5.2.2 节和
            // https://developer.apple.com/library/archive/documentation/QuickTime/QTFF/QTFFChap3/qtff3.html。
            int colorPrimaries = parent.readUnsignedShort(); // 读取色彩原色。
            int transferCharacteristics = parent.readUnsignedShort(); // 读取传输特性。
            parent.skipBytes(2); // 跳过 matrix_coefficients（矩阵系数）。

            // 仅在盒子足够长时尝试读取 full_range_flag。在所有 type=nclx 的 colr 盒子中，
            // 它应该存在（ISO/IEC 14496-12:2012(E) 的第 8.5.2.2 节），但某些设备摄像头录制的
            // type=nclx 视频没有这个最终标志（因此 size=18）：
            // https://github.com/google/ExoPlayer/issues/9332
            boolean fullRangeFlag =
                childAtomSize == 19 && (parent.readUnsignedByte() & 0b10000000) != 0;
            colorSpace = ColorInfo.isoColorPrimariesToColorSpace(colorPrimaries);
            colorRange = fullRangeFlag ? C.COLOR_RANGE_FULL : C.COLOR_RANGE_LIMITED;
            colorTransfer =
                ColorInfo.isoTransferCharacteristicsToColorTransfer(transferCharacteristics);
          } else {
            Log.w(TAG, "Unsupported color type: " + Mp4Box.getBoxTypeString(colorType));
          }
        }
      }
      childPosition += childAtomSize;
    }

    // 如果媒体类型未被识别，则忽略该轨道。
    if (mimeType == null) {
      return;
    }

    Format.Builder formatBuilder =
        new Format.Builder()
            .setId(trackId)
            .setSampleMimeType(mimeType)
            .setCodecs(codecs)
            .setWidth(width)
            .setHeight(height)
            .setPixelWidthHeightRatio(pixelWidthHeightRatio)
            .setRotationDegrees(rotationDegrees)
            .setProjectionData(projectionData)
            .setStereoMode(stereoMode)
            .setInitializationData(initializationData)
            .setMaxNumReorderSamples(maxNumReorderSamples)
            .setDrmInitData(drmInitData)
            // 请注意，如果缺少 mdcv 或 clli，我们会将相应的 HDR 静态元数据字节保留为零值。
            // 参见 [内部参考：b/194535665]。
            .setColorInfo(
                new ColorInfo.Builder()
                    .setColorSpace(colorSpace)
                    .setColorRange(colorRange)
                    .setColorTransfer(colorTransfer)
                    .setHdrStaticInfo(hdrStaticInfo != null ? hdrStaticInfo.array() : null)
                    .setLumaBitdepth(bitdepthLuma)
                    .setChromaBitdepth(bitdepthChroma)
                    .build());

    if (esdsData != null) {
      formatBuilder
          .setAverageBitrate(Ints.saturatedCast(esdsData.bitrate))
          .setPeakBitrate(Ints.saturatedCast(esdsData.peakBitrate));
    }

    out.format = formatBuilder.build();
  }

  /**
   * 解析 av1C 配置记录和 OBU 序列头，并从其数据中返回一个 {@link ColorInfo}。
   *
   * <p>参见 av1C 配置记录语法，参考此 <a
   * href="https://aomediacodec.github.io/av1-isobmff/#av1codecconfigurationbox-syntax">规范</a>。
   *
   * <p>参见 av1C OBU 语法，参考此 <a
   * href="https://aomediacodec.github.io/av1-spec/av1-spec.pdf">规范</a>。
   *
   * <p>方法中引用的部分来自这些规范。
   *
   * @param data av1C 原子数据。
   * @return 从 av1C 数据中解析出的 {@link ColorInfo}。
   */
  private static ColorInfo parseAv1c(ParsableByteArray data) {
    ColorInfo.Builder colorInfo = new ColorInfo.Builder();
    ParsableBitArray bitArray = new ParsableBitArray(data.getData());
    bitArray.setPosition(data.getPosition() * 8); // 将字节位置转换为比特位置。

    // 解析 av1C 配置记录以获取位深信息。
    // 参见 https://aomediacodec.github.io/av1-isobmff/#av1codecconfigurationbox-syntax。
    bitArray.skipBytes(1); // 跳过 marker 和 version。
    int seqProfile = bitArray.readBits(3); // 读取 seq_profile。
    bitArray.skipBits(6); // 跳过 seq_level_idx_0 和 seq_tier_0。
    boolean highBitdepth = bitArray.readBit(); // 读取 high_bitdepth。
    boolean twelveBit = bitArray.readBit(); // 读取 twelve_bit。
    if (seqProfile == 2 && highBitdepth) {
      colorInfo.setLumaBitdepth(twelveBit ? 12 : 10); // 设置亮度位深。
      colorInfo.setChromaBitdepth(twelveBit ? 12 : 10); // 设置色度位深。
    } else if (seqProfile <= 2) {
      colorInfo.setLumaBitdepth(highBitdepth ? 10 : 8); // 设置亮度位深。
      colorInfo.setChromaBitdepth(highBitdepth ? 10 : 8); // 设置色度位深。
    }
    // 跳过 monochrome、chroma_subsampling_x、chroma_subsampling_y、chroma_sample_position、
    // reserved 和 initial_presentation_delay。
    bitArray.skipBits(13);

    // 5.3.1. 通用 OBU 语法
    bitArray.skipBit(); // 跳过 obu_forbidden_bit。
    int obuType = bitArray.readBits(4); // 读取 obu_type。
    if (obuType != 1) { // 如果 obu_type 不是 OBU_SEQUENCE_HEADER。
      Log.i(TAG, "不支持的 obu_type: " + obuType);
      return colorInfo.build();
    }
    if (bitArray.readBit()) { // 如果 obu_extension_flag 为 true。
      Log.i(TAG, "不支持的 obu_extension_flag");
      return colorInfo.build();
    }
    boolean obuHasSizeField = bitArray.readBit(); // 读取 obu_has_size_field。
    bitArray.skipBit(); // 跳过 obu_reserved_1bit。
    // obu_size 是无符号 leb128，如果 obu_size <= 127，则可以简化为 readBits(8)。
    if (obuHasSizeField && bitArray.readBits(8) > 127) { // 如果 obu_size 过大。
      Log.i(TAG, "过大的 obu_size");
      return colorInfo.build();
    }
    // 5.5.1. 通用 OBU 序列头语法
    int obuSeqHeaderSeqProfile = bitArray.readBits(3); // 读取 seq_profile。
    bitArray.skipBit(); // 跳过 still_picture。
    if (bitArray.readBit()) { // 如果 reduced_still_picture_header 为 true。
      Log.i(TAG, "不支持的 reduced_still_picture_header");
      return colorInfo.build();
    }
    if (bitArray.readBit()) { // 如果 timing_info_present_flag 为 true。
      Log.i(TAG, "不支持的 timing_info_present_flag");
      return colorInfo.build();
    }
    if (bitArray.readBit()) { // 如果 initial_display_delay_present_flag 为 true。
      Log.i(TAG, "不支持的 initial_display_delay_present_flag");
      return colorInfo.build();
    }
    int operatingPointsCountMinus1 = bitArray.readBits(5); // 读取 operating_points_cnt_minus_1。
    for (int i = 0; i <= operatingPointsCountMinus1; i++) {
      bitArray.skipBits(12); // 跳过 operating_point_idc[i]。
      int seqLevelIdx = bitArray.readBits(5); // 读取 seq_level_idx[i]。
      if (seqLevelIdx > 7) {
        bitArray.skipBit(); // 跳过 seq_tier[i]。
      }
    }
    int frameWidthBitsMinus1 = bitArray.readBits(4); // 读取 frame_width_bits_minus_1。
    int frameHeightBitsMinus1 = bitArray.readBits(4); // 读取 frame_height_bits_minus_1。
    bitArray.skipBits(frameWidthBitsMinus1 + 1); // 跳过 max_frame_width_minus_1。
    bitArray.skipBits(frameHeightBitsMinus1 + 1); // 跳过 max_frame_height_minus_1。
    if (bitArray.readBit()) { // 如果 frame_id_numbers_present_flag 为 true。
      bitArray.skipBits(
          7); // 跳过 delta_frame_id_length_minus_2 和 additional_frame_id_length_minus_1。
    }
    bitArray.skipBits(7); // 跳过 use_128x128_superblock...enable_dual_filter: 7 个标志。
    boolean enableOrderHint = bitArray.readBit(); // 读取 enable_order_hint。
    if (enableOrderHint) {
      bitArray.skipBits(2); // 跳过 enable_jnt_comp 和 enable_ref_frame_mvs。
    }
    int seqForceScreenContentTools =
        bitArray.readBit() // 读取 seq_choose_screen_content_tools。
            ? 2 // SELECT_SCREEN_CONTENT_TOOLS
            : bitArray.readBits(1); // 读取 seq_force_screen_content_tools。
    if (seqForceScreenContentTools > 0) {
      if (!bitArray.readBit()) { // 如果 seq_choose_integer_mv 为 false。
        bitArray.skipBits(1); // 跳过 seq_force_integer_mv。
      }
    }
    if (enableOrderHint) {
      bitArray.skipBits(3); // 跳过 order_hint_bits_minus_1。
    }
    bitArray.skipBits(3); // 跳过 enable_superres、enable_cdef 和 enable_restoration。
    // 5.5.2. OBU 颜色配置语法
    boolean colorConfigHighBitdepth = bitArray.readBit(); // 读取 high_bitdepth。
    if (obuSeqHeaderSeqProfile == 2 && colorConfigHighBitdepth) {
      bitArray.skipBit(); // 跳过 twelve_bit。
    }

    boolean monochrome = (obuSeqHeaderSeqProfile != 1) && bitArray.readBit(); // 读取 mono_chrome。

    if (bitArray.readBit()) { // 如果 color_description_present_flag 为 true。
      int colorPrimaries = bitArray.readBits(8); // 读取 color_primaries。
      int transferCharacteristics = bitArray.readBits(8); // 读取 transfer_characteristics。
      int matrixCoefficients = bitArray.readBits(8); // 读取 matrix_coefficients。
      int colorRange =
          (!monochrome
              && colorPrimaries == 1 // CP_BT_709
              && transferCharacteristics == 13 // TC_SRGB
              && matrixCoefficients == 0) // MC_IDENTITY
              ? 1
              : bitArray.readBits(1); // 读取 color_range;
      colorInfo
          .setColorSpace(ColorInfo.isoColorPrimariesToColorSpace(colorPrimaries)) // 设置色彩空间。
          .setColorRange((colorRange == 1) ? C.COLOR_RANGE_FULL : C.COLOR_RANGE_LIMITED) // 设置色彩范围。
          .setColorTransfer(
              ColorInfo.isoTransferCharacteristicsToColorTransfer(
                  transferCharacteristics)); // 设置色彩传输特性。
    }
    return colorInfo.build();
  }

  private static ByteBuffer allocateHdrStaticInfo() {
    // 对于 HDR 静态信息，Android 解码器期望一个 25 字节的数组。第一个字节为 0，
    // 表示静态元数据类型 1（根据 CTA-861-G:2017 表 44）。接下来的 24 字节
    // 遵循 CTA-861-G:2017 表 45。
    return ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN);
  }

  private static void parseMetaDataSampleEntry(
      ParsableByteArray parent, int atomType, int position, int trackId, StsdData out) {
    parent.setPosition(position + Mp4Box.HEADER_SIZE + StsdData.STSD_HEADER_SIZE);
    if (atomType == Mp4Box.TYPE_mett) {
      parent.readNullTerminatedString(); // Skip optional content_encoding
      @Nullable String mimeType = parent.readNullTerminatedString();
      if (mimeType != null) {
        out.format = new Format.Builder().setId(trackId).setSampleMimeType(mimeType).build();
      }
    }
  }

  /**
   * 解析 edts 原子（定义在 ISO/IEC 14496-12 第 8.6.5 小节）。
   *
   * @param edtsAtom 要解码的 edts（编辑盒）原子。
   * @return 编辑列表持续时间和编辑列表媒体时间的配对，如果不存在则返回 {@code null}。
   */
  @Nullable
  private static Pair<long[], long[]> parseEdts(Mp4Box.ContainerBox edtsAtom) {
    @Nullable LeafBox elstAtom = edtsAtom.getLeafBoxOfType(Mp4Box.TYPE_elst);
    if (elstAtom == null) {
      return null;
    }
    ParsableByteArray elstData = elstAtom.data;
    elstData.setPosition(Mp4Box.HEADER_SIZE);
    int fullAtom = elstData.readInt();
    int version = parseFullBoxVersion(fullAtom);
    int entryCount = elstData.readUnsignedIntToInt();
    long[] editListDurations = new long[entryCount];
    long[] editListMediaTimes = new long[entryCount];
    for (int i = 0; i < entryCount; i++) {
      editListDurations[i] =
          version == 1 ? elstData.readUnsignedLongToLong() : elstData.readUnsignedInt();
      editListMediaTimes[i] = version == 1 ? elstData.readLong() : elstData.readInt();
      int mediaRateInteger = elstData.readShort();
      if (mediaRateInteger != 1) {
        // 提取器不支持处理停留编辑（mediaRateInteger == 0）。
        throw new IllegalArgumentException("Unsupported media rate.");
      }
      elstData.skipBytes(2);
    }
    return Pair.create(editListDurations, editListMediaTimes);
  }

  private static float parsePaspFromParent(ParsableByteArray parent, int position) {
    parent.setPosition(position + Mp4Box.HEADER_SIZE);
    int hSpacing = parent.readUnsignedIntToInt();
    int vSpacing = parent.readUnsignedIntToInt();
    return (float) hSpacing / vSpacing;
  }

  private static void parseAudioSampleEntry(
      ParsableByteArray parent,
      int atomType,
      int position,
      int size,
      int trackId,
      String language,
      boolean isQuickTime,
      @Nullable DrmInitData drmInitData,
      StsdData out,
      int entryIndex)
      throws ParserException {
    parent.setPosition(position + Mp4Box.HEADER_SIZE + StsdData.STSD_HEADER_SIZE);

    int quickTimeSoundDescriptionVersion = 0;
    if (isQuickTime) {
      quickTimeSoundDescriptionVersion = parent.readUnsignedShort();
      parent.skipBytes(6);
    } else {
      parent.skipBytes(8);
    }

    int channelCount;
    int sampleRate;
    int sampleRateMlp = 0;
    @C.PcmEncoding int pcmEncoding = Format.NO_VALUE;
    @Nullable String codecs = null;
    @Nullable EsdsData esdsData = null;

    if (quickTimeSoundDescriptionVersion == 0 || quickTimeSoundDescriptionVersion == 1) {
      channelCount = parent.readUnsignedShort();
      parent.skipBytes(6); // sampleSize, compressionId, packetSize.

      sampleRate = parent.readUnsignedFixedPoint1616();
      // The sample rate has been redefined as a 32-bit value for Dolby TrueHD (MLP) streams.
      parent.setPosition(parent.getPosition() - 4);
      sampleRateMlp = parent.readInt();

      if (quickTimeSoundDescriptionVersion == 1) {
        parent.skipBytes(16);
      }
    } else if (quickTimeSoundDescriptionVersion == 2) {
      parent.skipBytes(16); // always[3,16,Minus2,0,65536], sizeOfStructOnly

      sampleRate = (int) Math.round(parent.readDouble());
      channelCount = parent.readUnsignedIntToInt();

      parent.skipBytes(4); // always7F000000
      int bitsPerSample = parent.readUnsignedIntToInt();
      int formatSpecificFlags = parent.readUnsignedIntToInt();
      boolean isFloat = (formatSpecificFlags & 1) != 0;
      boolean isBigEndian = (formatSpecificFlags & (1 << 1)) != 0;
      if (!isFloat) {
        if (bitsPerSample == 8) {
          pcmEncoding = C.ENCODING_PCM_8BIT;
        } else if (bitsPerSample == 16) {
          pcmEncoding = isBigEndian ? C.ENCODING_PCM_16BIT_BIG_ENDIAN : C.ENCODING_PCM_16BIT;
        } else if (bitsPerSample == 24) {
          pcmEncoding = isBigEndian ? C.ENCODING_PCM_24BIT_BIG_ENDIAN : C.ENCODING_PCM_24BIT;
        } else if (bitsPerSample == 32) {
          pcmEncoding = isBigEndian ? C.ENCODING_PCM_32BIT_BIG_ENDIAN : C.ENCODING_PCM_32BIT;
        }
      } else if (bitsPerSample == 32) {
        pcmEncoding = C.ENCODING_PCM_FLOAT;
      }
      parent.skipBytes(8); // constBytesPerAudioPacket, constLPCMFramesPerAudioPacket
    } else {
      // Unsupported version.
      return;
    }

    // 根据 IAMF 规范（https://aomediacodec.github.io/iamf/#iasampleentry-section），
    // channelCount 和 sampleRate 应设置为 0 并被忽略。我们通过使用 Format.NO_VALUE 而不是 0 来忽略它。
    if (atomType == Mp4Box.TYPE_iamf) {
      channelCount = Format.NO_VALUE;
      sampleRate = Format.NO_VALUE;
    }

    int childPosition = parent.getPosition();
    if (atomType == Mp4Box.TYPE_enca) {
      @Nullable
      Pair<Integer, TrackEncryptionBox> sampleEntryEncryptionData =
          parseSampleEntryEncryptionData(parent, position, size);
      if (sampleEntryEncryptionData != null) {
        atomType = sampleEntryEncryptionData.first;
        drmInitData =
            drmInitData == null
                ? null
                : drmInitData.copyWithSchemeType(sampleEntryEncryptionData.second.schemeType);
        out.trackEncryptionBoxes[entryIndex] = sampleEntryEncryptionData.second;
      }
      parent.setPosition(childPosition);
    }
    // TODO: Uncomment when [Internal: b/63092960] is fixed.
    // else {
    //   drmInitData = null;
    // }

    // If the atom type determines a MIME type, set it immediately.
    @Nullable String mimeType = null;
    if (atomType == Mp4Box.TYPE_ac_3) {
      mimeType = MimeTypes.AUDIO_AC3;
    } else if (atomType == Mp4Box.TYPE_ec_3) {
      mimeType = MimeTypes.AUDIO_E_AC3;
    } else if (atomType == Mp4Box.TYPE_ac_4) {
      mimeType = MimeTypes.AUDIO_AC4;
    } else if (atomType == Mp4Box.TYPE_dtsc) {
      mimeType = MimeTypes.AUDIO_DTS;
    } else if (atomType == Mp4Box.TYPE_dtsh || atomType == Mp4Box.TYPE_dtsl) {
      mimeType = MimeTypes.AUDIO_DTS_HD;
    } else if (atomType == Mp4Box.TYPE_dtse) {
      mimeType = MimeTypes.AUDIO_DTS_EXPRESS;
    } else if (atomType == Mp4Box.TYPE_dtsx) {
      mimeType = MimeTypes.AUDIO_DTS_X;
    } else if (atomType == Mp4Box.TYPE_samr) {
      mimeType = MimeTypes.AUDIO_AMR_NB;
    } else if (atomType == Mp4Box.TYPE_sawb) {
      mimeType = MimeTypes.AUDIO_AMR_WB;
    } else if (atomType == Mp4Box.TYPE_sowt) {
      mimeType = MimeTypes.AUDIO_RAW;
      pcmEncoding = C.ENCODING_PCM_16BIT;
    } else if (atomType == Mp4Box.TYPE_twos) {
      mimeType = MimeTypes.AUDIO_RAW;
      pcmEncoding = C.ENCODING_PCM_16BIT_BIG_ENDIAN;
    } else if (atomType == Mp4Box.TYPE_lpcm) {
      mimeType = MimeTypes.AUDIO_RAW;
      if (pcmEncoding == Format.NO_VALUE) {
        pcmEncoding = C.ENCODING_PCM_16BIT;
      }
    } else if (atomType == Mp4Box.TYPE__mp2 || atomType == Mp4Box.TYPE__mp3) {
      mimeType = MimeTypes.AUDIO_MPEG;
    } else if (atomType == Mp4Box.TYPE_mha1) {
      mimeType = MimeTypes.AUDIO_MPEGH_MHA1;
    } else if (atomType == Mp4Box.TYPE_mhm1) {
      mimeType = MimeTypes.AUDIO_MPEGH_MHM1;
    } else if (atomType == Mp4Box.TYPE_alac) {
      mimeType = MimeTypes.AUDIO_ALAC;
    } else if (atomType == Mp4Box.TYPE_alaw) {
      mimeType = MimeTypes.AUDIO_ALAW;
    } else if (atomType == Mp4Box.TYPE_ulaw) {
      mimeType = MimeTypes.AUDIO_MLAW;
    } else if (atomType == Mp4Box.TYPE_Opus) {
      mimeType = MimeTypes.AUDIO_OPUS;
    } else if (atomType == Mp4Box.TYPE_fLaC) {
      mimeType = MimeTypes.AUDIO_FLAC;
    } else if (atomType == Mp4Box.TYPE_mlpa) {
      mimeType = MimeTypes.AUDIO_TRUEHD;
    } else if (atomType == Mp4Box.TYPE_iamf) {
      mimeType = MimeTypes.AUDIO_IAMF;
    }

    @Nullable List<byte[]> initializationData = null;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      ExtractorUtil.checkContainerInput(childAtomSize > 0, "childAtomSize must be positive");
      int childAtomType = parent.readInt();
      if (childAtomType == Mp4Box.TYPE_mhaC) {
        // 参见 ISO_IEC_23008-3;2022 MHADecoderConfigurationRecord
        // 头部包括：size (4), boxtype 'mhaC' (4), configurationVersion (1),
        // mpegh3daProfileLevelIndication (1), referenceChannelLayout (1), mpegh3daConfigLength (2)。
        parent.setPosition(childPosition + Mp4Box.HEADER_SIZE);
        parent.skipBytes(1); // 跳过 configurationVersion
        int mpeghProfileLevelIndication = parent.readUnsignedByte();
        parent.skipBytes(1); // 跳过 mpeghReferenceChannelLayout
        codecs =
            Objects.equals(mimeType, MimeTypes.AUDIO_MPEGH_MHM1)
                ? String.format("mhm1.%02X", mpeghProfileLevelIndication)
                : String.format("mha1.%02X", mpeghProfileLevelIndication);
        int mpegh3daConfigLength = parent.readUnsignedShort();
        byte[] initializationDataBytes = new byte[mpegh3daConfigLength];
        parent.readBytes(initializationDataBytes, 0, mpegh3daConfigLength);
        // mpegh3daConfig 应始终是 initializationData 的第一个条目。
        if (initializationData == null) {
          initializationData = ImmutableList.of(initializationDataBytes);
        } else {
          // 假设 mhaP 盒子已解析，因此将兼容的 profile level sets 作为第二个条目添加。
          initializationData = ImmutableList.of(initializationDataBytes, initializationData.get(0));
        }
      } else if (childAtomType == Mp4Box.TYPE_mhaP) {
        // 参见 ISO_IEC_23008-3;2022 MHAProfileAndLevelCompatibilitySetBox
        // 头部包括：size (4), boxtype 'mhaP' (4), numCompatibleSets (1)。
        parent.setPosition(childPosition + Mp4Box.HEADER_SIZE);
        int numCompatibleSets = parent.readUnsignedByte();
        if (numCompatibleSets > 0) {
          byte[] mpeghCompatibleProfileLevelSet = new byte[numCompatibleSets];
          parent.readBytes(mpeghCompatibleProfileLevelSet, 0, numCompatibleSets);
          if (initializationData == null) {
            initializationData = ImmutableList.of(mpeghCompatibleProfileLevelSet);
          } else {
            // 假设 mhaC 盒子已解析，因此将兼容的 profile level sets 作为第二个条目添加。
            initializationData =
                ImmutableList.of(initializationData.get(0), mpeghCompatibleProfileLevelSet);
          }
        }
      } else if (childAtomType == Mp4Box.TYPE_esds
          || (isQuickTime && childAtomType == Mp4Box.TYPE_wave)) {
        int esdsAtomPosition =
            childAtomType == Mp4Box.TYPE_esds
                ? childPosition
                : findBoxPosition(parent, Mp4Box.TYPE_esds, childPosition, childAtomSize);
        if (esdsAtomPosition != C.INDEX_UNSET) {
          esdsData = parseEsdsFromParent(parent, esdsAtomPosition);
          mimeType = esdsData.mimeType;
          @Nullable byte[] initializationDataBytes = esdsData.initializationData;
          if (initializationDataBytes != null) {
            if (MimeTypes.AUDIO_VORBIS.equals(mimeType)) {
              initializationData =
                  VorbisUtil.parseVorbisCsdFromEsdsInitializationData(initializationDataBytes);
            } else {
              if (MimeTypes.AUDIO_AAC.equals(mimeType)) {
                // 从 AudioSpecificConfig 初始化数据更新 sampleRate 和 channelCount，更可靠。
                // 参见 [Internal: b/10903778]。
                AacUtil.Config aacConfig =
                    AacUtil.parseAudioSpecificConfig(initializationDataBytes);
                sampleRate = aacConfig.sampleRateHz;
                channelCount = aacConfig.channelCount;
                codecs = aacConfig.codecs;
              }
              initializationData = ImmutableList.of(initializationDataBytes);
            }
          }
        }
      } else if (childAtomType == Mp4Box.TYPE_dac3) {
        parent.setPosition(Mp4Box.HEADER_SIZE + childPosition);
        out.format =
            Ac3Util.parseAc3AnnexFFormat(parent, Integer.toString(trackId), language, drmInitData);
      } else if (childAtomType == Mp4Box.TYPE_dec3) {
        parent.setPosition(Mp4Box.HEADER_SIZE + childPosition);
        out.format =
            Ac3Util.parseEAc3AnnexFFormat(parent, Integer.toString(trackId), language, drmInitData);
      } else if (childAtomType == Mp4Box.TYPE_dac4) {
        parent.setPosition(Mp4Box.HEADER_SIZE + childPosition);
        out.format =
            Ac4Util.parseAc4AnnexEFormat(parent, Integer.toString(trackId), language, drmInitData);
      } else if (childAtomType == Mp4Box.TYPE_dmlp) {
        if (sampleRateMlp <= 0) {
          throw ParserException.createForMalformedContainer(
              "Invalid sample rate for Dolby TrueHD MLP stream: " + sampleRateMlp,
              /* cause= */ null);
        }
        sampleRate = sampleRateMlp;
        // 对于 Dolby TrueHD (MLP) 流，必须忽略样本条目中的 channelCount，
        // 因为这些流可以同时携带同一音频的多种表示。默认使用立体声。
        channelCount = 2;
      } else if (childAtomType == Mp4Box.TYPE_ddts || childAtomType == Mp4Box.TYPE_udts) {
        out.format =
            new Format.Builder()
                .setId(trackId)
                .setSampleMimeType(mimeType)
                .setChannelCount(channelCount)
                .setSampleRate(sampleRate)
                .setDrmInitData(drmInitData)
                .setLanguage(language)
                .build();
      } else if (childAtomType == Mp4Box.TYPE_dOps) {
        // 通过连接 Opus Magic Signature 和 dOps 盒子的主体构建 Opus Identification Header（定义在 RFC-7845 中）。
        int childAtomBodySize = childAtomSize - Mp4Box.HEADER_SIZE;
        byte[] headerBytes = Arrays.copyOf(opusMagic, opusMagic.length + childAtomBodySize);
        parent.setPosition(childPosition + Mp4Box.HEADER_SIZE);
        parent.readBytes(headerBytes, opusMagic.length, childAtomBodySize);
        initializationData = OpusUtil.buildInitializationData(headerBytes);
      } else if (childAtomType == Mp4Box.TYPE_dfLa) {
        int childAtomBodySize = childAtomSize - Mp4Box.FULL_HEADER_SIZE;
        byte[] initializationDataBytes = new byte[4 + childAtomBodySize];
        initializationDataBytes[0] = 0x66; // f
        initializationDataBytes[1] = 0x4C; // L
        initializationDataBytes[2] = 0x61; // a
        initializationDataBytes[3] = 0x43; // C
        parent.setPosition(childPosition + Mp4Box.FULL_HEADER_SIZE);
        parent.readBytes(initializationDataBytes, /* offset= */ 4, childAtomBodySize);
        initializationData = ImmutableList.of(initializationDataBytes);
      } else if (childAtomType == Mp4Box.TYPE_alac) {
        int childAtomBodySize = childAtomSize - Mp4Box.FULL_HEADER_SIZE;
        byte[] initializationDataBytes = new byte[childAtomBodySize];
        parent.setPosition(childPosition + Mp4Box.FULL_HEADER_SIZE);
        parent.readBytes(initializationDataBytes, /* offset= */ 0, childAtomBodySize);
        // 从 AudioSpecificConfig 初始化数据更新 sampleRate 和 channelCount，更可靠。
        // 参见 https://github.com/google/ExoPlayer/pull/6629。
        Pair<Integer, Integer> audioSpecificConfig =
            CodecSpecificDataUtil.parseAlacAudioSpecificConfig(initializationDataBytes);
        sampleRate = audioSpecificConfig.first;
        channelCount = audioSpecificConfig.second;
        initializationData = ImmutableList.of(initializationDataBytes);
      } else if (childAtomType == Mp4Box.TYPE_iacb) {
        parent.setPosition(
            childPosition + Mp4Box.HEADER_SIZE + 1); // 头部和配置版本
        int configObusSize = parent.readUnsignedLeb128ToInt();
        byte[] initializationDataBytes = new byte[configObusSize];
        parent.readBytes(initializationDataBytes, /* offset= */ 0, configObusSize);
        initializationData = ImmutableList.of(initializationDataBytes);
      }
      childPosition += childAtomSize;
    }

    if (out.format == null && mimeType != null) {
      Format.Builder formatBuilder =
          new Format.Builder()
              .setId(trackId)
              .setSampleMimeType(mimeType)
              .setCodecs(codecs)
              .setChannelCount(channelCount)
              .setSampleRate(sampleRate)
              .setPcmEncoding(pcmEncoding)
              .setInitializationData(initializationData)
              .setDrmInitData(drmInitData)
              .setLanguage(language);

      if (esdsData != null) {
        formatBuilder
            .setAverageBitrate(Ints.saturatedCast(esdsData.bitrate))
            .setPeakBitrate(Ints.saturatedCast(esdsData.peakBitrate));
      }

      out.format = formatBuilder.build();
    }
  }

  /**
   * 返回 {@code parent} 中第一个具有指定 {@code boxType} 的盒子的位置，
   * 如果未找到则返回 {@link C#INDEX_UNSET}。
   *
   * @param parent            要搜索的 {@link ParsableByteArray}。搜索将从 {@link ParsableByteArray#getPosition() 当前位置} 开始。
   * @param boxType           要搜索的盒子类型。
   * @param parentBoxPosition {@code parent} 中要搜索的盒子的位置。
   * @param parentBoxSize     要搜索的父盒子的大小（以字节为单位）。
   * @return {@code parent} 中第一个具有指定 {@code boxType} 的盒子的位置，如果未找到则返回 {@link C#INDEX_UNSET}。
   */
  private static int findBoxPosition(
      ParsableByteArray parent, int boxType, int parentBoxPosition, int parentBoxSize)
      throws ParserException {
    int childAtomPosition = parent.getPosition();
    ExtractorUtil.checkContainerInput(childAtomPosition >= parentBoxPosition, /* message= */ null);
    while (childAtomPosition - parentBoxPosition < parentBoxSize) {
      parent.setPosition(childAtomPosition);
      int childAtomSize = parent.readInt();
      ExtractorUtil.checkContainerInput(childAtomSize > 0, "childAtomSize must be positive");
      int childType = parent.readInt();
      if (childType == boxType) {
        return childAtomPosition;
      }
      childAtomPosition += childAtomSize;
    }
    return C.INDEX_UNSET;
  }

  /**
   * 返回 esds 盒子中包含的编解码器特定初始化数据。
   */
  private static EsdsData parseEsdsFromParent(ParsableByteArray parent, int position) {
    parent.setPosition(position + Mp4Box.HEADER_SIZE + 4);
    // 开始解析 ES_Descriptor（定义在 ISO/IEC 14496-1 中）
    parent.skipBytes(1); // 跳过 ES_Descriptor tag
    parseExpandableClassSize(parent);
    parent.skipBytes(2); // 跳过 ES_ID

    int flags = parent.readUnsignedByte();
    if ((flags & 0x80 /* streamDependenceFlag */) != 0) {
      parent.skipBytes(2);
    }
    if ((flags & 0x40 /* URL_Flag */) != 0) {
      parent.skipBytes(parent.readUnsignedByte());
    }
    if ((flags & 0x20 /* OCRstreamFlag */) != 0) {
      parent.skipBytes(2);
    }

    // 开始解析 DecoderConfigDescriptor（定义在 ISO/IEC 14496-1 中）
    parent.skipBytes(1); // 跳过 DecoderConfigDescriptor tag
    parseExpandableClassSize(parent);

    // 根据对象类型指示（ISO/IEC 14496-1 表 5）设置 MIME 类型。
    int objectTypeIndication = parent.readUnsignedByte();
    @Nullable String mimeType = getMimeTypeFromMp4ObjectType(objectTypeIndication);
    if (MimeTypes.AUDIO_MPEG.equals(mimeType)
        || MimeTypes.AUDIO_DTS.equals(mimeType)
        || MimeTypes.AUDIO_DTS_HD.equals(mimeType)) {
      return new EsdsData(
          mimeType,
          /* initializationData= */ null,
          /* bitrate= */ Format.NO_VALUE,
          /* peakBitrate= */ Format.NO_VALUE);
    }

    parent.skipBytes(4);
    long peakBitrate = parent.readUnsignedInt();
    long bitrate = parent.readUnsignedInt();

    // 开始解析 DecoderSpecificInfo。
    parent.skipBytes(1); // 跳过 DecoderSpecificInfo tag
    int initializationDataSize = parseExpandableClassSize(parent);
    byte[] initializationData = new byte[initializationDataSize];
    parent.readBytes(initializationData, 0, initializationDataSize);

    // 跳过零值，视为未知。
    return new EsdsData(
        mimeType,
        /* initializationData= */ initializationData,
        /* bitrate= */ bitrate > 0 ? bitrate : Format.NO_VALUE,
        /* peakBitrate= */ peakBitrate > 0 ? peakBitrate : Format.NO_VALUE);
  }

  /**
   * 从 vexu 盒子中返回与立体视频播放相关的元数据。参见
   * https://developer.apple.com/av-foundation/Stereo-Video-ISOBMFF-Extensions.pdf。
   */
  @Nullable
  /* package */ static VexuData parseVideoExtendedUsageBox(
      ParsableByteArray parent, int position, int size) throws ParserException {
    parent.setPosition(position + Mp4Box.HEADER_SIZE);
    int childPosition = parent.getPosition();
    @Nullable EyesData eyesData = null;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      ExtractorUtil.checkContainerInput(childAtomSize > 0, "childAtomSize must be positive");
      int childAtomType = parent.readInt();
      if (childAtomType == Mp4Box.TYPE_eyes) {
        eyesData = parseStereoViewBox(parent, childPosition, childAtomSize);
      }
      childPosition += childAtomSize;
    }
    return eyesData == null ? null : new VexuData(eyesData);
  }

  @Nullable
  private static EyesData parseStereoViewBox(ParsableByteArray parent, int position, int size)
      throws ParserException {
    parent.setPosition(position + Mp4Box.HEADER_SIZE);
    int childPosition = parent.getPosition();
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      ExtractorUtil.checkContainerInput(childAtomSize > 0, "childAtomSize must be positive");
      if (parent.readInt() == Mp4Box.TYPE_stri) {
        // The stri box extends FullBox that includes version (8 bits) and flags (24 bits).
        parent.skipBytes(4);
        int striInfo = parent.readUnsignedByte() & 0x0F;
        return new EyesData(
            new StriData(
                ((striInfo & 0x01) == 0x01),
                ((striInfo & 0x02) == 0x02),
                ((striInfo & 0x08) == 0x08),
                ((striInfo & 0x04) == 0x04)));
      }
      childPosition += childAtomSize;
    }
    return null;
  }

  /**
   * 从音频/视频样本条目中解析加密数据，返回一个由未加密原子类型和 {@link TrackEncryptionBox} 组成的配对。
   * 如果未找到通用的加密 sinf 原子，则返回 null。
   */
  @Nullable
  private static Pair<Integer, TrackEncryptionBox> parseSampleEntryEncryptionData(
      ParsableByteArray parent, int position, int size) throws ParserException {
    int childPosition = parent.getPosition();
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      ExtractorUtil.checkContainerInput(childAtomSize > 0, "childAtomSize must be positive");
      int childAtomType = parent.readInt();
      if (childAtomType == Mp4Box.TYPE_sinf) {
        @Nullable
        Pair<Integer, TrackEncryptionBox> result =
            parseCommonEncryptionSinfFromParent(parent, childPosition, childAtomSize);
        if (result != null) {
          return result;
        }
      }
      childPosition += childAtomSize;
    }
    return null;
  }

  @Nullable
  /* package */ static Pair<Integer, TrackEncryptionBox> parseCommonEncryptionSinfFromParent(
      ParsableByteArray parent, int position, int size) throws ParserException {
    int childPosition = position + Mp4Box.HEADER_SIZE;
    int schemeInformationBoxPosition = C.INDEX_UNSET;
    int schemeInformationBoxSize = 0;
    @Nullable String schemeType = null;
    @Nullable Integer dataFormat = null;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Mp4Box.TYPE_frma) {
        dataFormat = parent.readInt();
      } else if (childAtomType == Mp4Box.TYPE_schm) {
        parent.skipBytes(4);
        // Common encryption scheme_type values are defined in ISO/IEC 23001-7:2016, section 4.1.
        schemeType = parent.readString(4);
      } else if (childAtomType == Mp4Box.TYPE_schi) {
        schemeInformationBoxPosition = childPosition;
        schemeInformationBoxSize = childAtomSize;
      }
      childPosition += childAtomSize;
    }

    if (C.CENC_TYPE_cenc.equals(schemeType)
        || C.CENC_TYPE_cbc1.equals(schemeType)
        || C.CENC_TYPE_cens.equals(schemeType)
        || C.CENC_TYPE_cbcs.equals(schemeType)) {
      ExtractorUtil.checkContainerInput(dataFormat != null, "frma atom is mandatory");
      ExtractorUtil.checkContainerInput(
          schemeInformationBoxPosition != C.INDEX_UNSET, "schi atom is mandatory");
      @Nullable
      TrackEncryptionBox encryptionBox =
          parseSchiFromParent(
              parent, schemeInformationBoxPosition, schemeInformationBoxSize, schemeType);
      ExtractorUtil.checkContainerInput(encryptionBox != null, "tenc atom is mandatory");
      return Pair.create(dataFormat, castNonNull(encryptionBox));
    } else {
      return null;
    }
  }

  @Nullable
  private static TrackEncryptionBox parseSchiFromParent(
      ParsableByteArray parent, int position, int size, String schemeType) {
    int childPosition = position + Mp4Box.HEADER_SIZE;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Mp4Box.TYPE_tenc) {
        int fullAtom = parent.readInt();
        int version = parseFullBoxVersion(fullAtom);
        parent.skipBytes(1); // reserved = 0.
        int defaultCryptByteBlock = 0;
        int defaultSkipByteBlock = 0;
        if (version == 0) {
          parent.skipBytes(1); // reserved = 0.
        } else /* version 1 or greater */ {
          int patternByte = parent.readUnsignedByte();
          defaultCryptByteBlock = (patternByte & 0xF0) >> 4;
          defaultSkipByteBlock = patternByte & 0x0F;
        }
        boolean defaultIsProtected = parent.readUnsignedByte() == 1;
        int defaultPerSampleIvSize = parent.readUnsignedByte();
        byte[] defaultKeyId = new byte[16];
        parent.readBytes(defaultKeyId, 0, defaultKeyId.length);
        byte[] constantIv = null;
        if (defaultIsProtected && defaultPerSampleIvSize == 0) {
          int constantIvSize = parent.readUnsignedByte();
          constantIv = new byte[constantIvSize];
          parent.readBytes(constantIv, 0, constantIvSize);
        }
        return new TrackEncryptionBox(
            defaultIsProtected,
            schemeType,
            defaultPerSampleIvSize,
            defaultKeyId,
            defaultCryptByteBlock,
            defaultSkipByteBlock,
            constantIv);
      }
      childPosition += childAtomSize;
    }
    return null;
  }

  /**
   * 从 sv3d 盒子中解析 proj 盒子，按照 https://github.com/google/spatial-media 的规范。
   */
  @Nullable
  private static byte[] parseProjFromParent(ParsableByteArray parent, int position, int size) {
    int childPosition = position + Mp4Box.HEADER_SIZE; // 子盒子的起始位置
    while (childPosition - position < size) { // 遍历所有子盒子
      parent.setPosition(childPosition); // 设置解析位置为当前子盒子的起始位置
      int childAtomSize = parent.readInt(); // 读取子盒子的大小
      int childAtomType = parent.readInt(); // 读取子盒子的类型
      if (childAtomType == Mp4Box.TYPE_proj) { // 如果子盒子类型是 proj
        return Arrays.copyOfRange(parent.getData(), childPosition, childPosition + childAtomSize); // 返回 proj 盒子的数据
      }
      childPosition += childAtomSize; // 移动到下一个子盒子
    }
    return null; // 如果未找到 proj 盒子，返回 null
  }

  /**
   * 解析可扩展类的大小，按照 ISO/IEC 14496-1 第 8.3.3 小节的规定。
   */
  private static int parseExpandableClassSize(ParsableByteArray data) {
    int currentByte = data.readUnsignedByte(); // 读取当前字节
    int size = currentByte & 0x7F; // 取低 7 位作为初始大小
    while ((currentByte & 0x80) == 0x80) { // 如果最高位为 1，表示还有后续字节
      currentByte = data.readUnsignedByte(); // 读取下一个字节
      size = (size << 7) | (currentByte & 0x7F); // 将新字节的低 7 位拼接到大小中
    }
    return size; // 返回解析出的大小
  }

  /**
   * 判断是否可以使用无缝播放信息应用指定的编辑。
   */
  private static boolean canApplyEditWithGaplessInfo(
      long[] timestamps, // 时间戳数组
      long duration, // 总时长
      long editStartTime, // 编辑开始时间
      long editEndTime // 编辑结束时间
  ) {
    int lastIndex = timestamps.length - 1; // 时间戳数组的最后一个索引
    int latestDelayIndex = Util.constrainValue(MAX_GAPLESS_TRIM_SIZE_SAMPLES, 0, lastIndex); // 延迟修剪的最大样本索引
    int earliestPaddingIndex =
        Util.constrainValue(timestamps.length - MAX_GAPLESS_TRIM_SIZE_SAMPLES, 0, lastIndex); // 填充修剪的最小样本索引
    return timestamps[0] <= editStartTime // 编辑开始时间在第一个时间戳之后
        && editStartTime < timestamps[latestDelayIndex] // 编辑开始时间在延迟修剪范围内
        && timestamps[earliestPaddingIndex] < editEndTime // 编辑结束时间在填充修剪范围内
        && editEndTime <= duration; // 编辑结束时间不超过总时长
  }

  private BoxParser() {
    // Prevent instantiation.
  }

  private static final class ChunkIterator {

    public final int length;

    public int index;
    public int numSamples;
    public long offset;

    private final boolean chunkOffsetsAreLongs;
    private final ParsableByteArray chunkOffsets;
    private final ParsableByteArray stsc;

    private int nextSamplesPerChunkChangeIndex;
    private int remainingSamplesPerChunkChanges;

    public ChunkIterator(
        ParsableByteArray stsc, ParsableByteArray chunkOffsets, boolean chunkOffsetsAreLongs)
        throws ParserException {
      this.stsc = stsc;
      this.chunkOffsets = chunkOffsets;
      this.chunkOffsetsAreLongs = chunkOffsetsAreLongs;
      chunkOffsets.setPosition(Mp4Box.FULL_HEADER_SIZE);
      length = chunkOffsets.readUnsignedIntToInt();
      stsc.setPosition(Mp4Box.FULL_HEADER_SIZE);
      remainingSamplesPerChunkChanges = stsc.readUnsignedIntToInt();
      ExtractorUtil.checkContainerInput(stsc.readInt() == 1, "first_chunk must be 1");
      index = -1;
    }

    public boolean moveNext() {
      if (++index == length) {
        return false;
      }
      offset =
          chunkOffsetsAreLongs
              ? chunkOffsets.readUnsignedLongToLong()
              : chunkOffsets.readUnsignedInt();
      if (index == nextSamplesPerChunkChangeIndex) {
        numSamples = stsc.readUnsignedIntToInt();
        stsc.skipBytes(4); // Skip sample_description_index
        nextSamplesPerChunkChangeIndex =
            --remainingSamplesPerChunkChanges > 0
                ? (stsc.readUnsignedIntToInt() - 1)
                : C.INDEX_UNSET;
      }
      return true;
    }
  }

  /**
   * 存储从 tkhd 原子中解析出的数据。
   */
  private static final class TkhdData {

    private final int id; // 轨道 ID
    private final long duration; // 轨道持续时间（单位：timescale ticks）
    private final int rotationDegrees; // 轨道旋转角度（单位：度）

    /**
     * 构造函数。
     *
     * @param id 轨道 ID。
     * @param duration 轨道持续时间（单位：timescale ticks）。
     * @param rotationDegrees 轨道旋转角度（单位：度）。
     */
    public TkhdData(int id, long duration, int rotationDegrees) {
      this.id = id;
      this.duration = duration;
      this.rotationDegrees = rotationDegrees;
    }
  }
  /**
   * 存储从 stsd 原子及其子原子中解析出的数据。
   */
  private static final class StsdData {

    public static final int STSD_HEADER_SIZE = 8; // stsd 原子的头部大小

    public final TrackEncryptionBox[] trackEncryptionBoxes; // 轨道加密盒子数组

    @Nullable
    public Format format; // 轨道格式（可能为 null）
    public int nalUnitLengthFieldLength; // NAL 单元长度字段的大小
    public @Track.Transformation int requiredSampleTransformation; // 所需的样本转换类型

    /**
     * 构造函数。
     *
     * @param numberOfEntries stsd 原子中的条目数量。
     */
    public StsdData(int numberOfEntries) {
      trackEncryptionBoxes = new TrackEncryptionBox[numberOfEntries]; // 初始化轨道加密盒子数组
      requiredSampleTransformation = Track.TRANSFORMATION_NONE; // 默认不需要样本转换
    }
  }

  /**
   * 从 esds 盒子中解析出的数据。
   */
  private static final class EsdsData {

    private final @NullableType String mimeType; // MIME 类型，表示音频或视频的格式
    private final byte @NullableType [] initializationData; // 初始化数据，用于解码器初始化
    private final long bitrate; // 比特率（单位：bps）
    private final long peakBitrate; // 峰值比特率（单位：bps）

    /**
     * 构造函数。
     *
     * @param mimeType MIME 类型，表示音频或视频的格式。
     * @param initializationData 初始化数据，用于解码器初始化。
     * @param bitrate 比特率（单位：bps）。
     * @param peakBitrate 峰值比特率（单位：bps）。
     */
    public EsdsData(
        @NullableType String mimeType,
        byte @NullableType [] initializationData,
        long bitrate,
        long peakBitrate) {
      this.mimeType = mimeType;
      this.initializationData = initializationData;
      this.bitrate = bitrate;
      this.peakBitrate = peakBitrate;
    }
  }

  /**
   * 从 stri 盒子中解析出的数据。
   */
  private static final class StriData {

    private final boolean hasLeftEyeView; // 是否包含左眼视图
    private final boolean hasRightEyeView; // 是否包含右眼视图
    private final boolean eyeViewsReversed; // 左右眼视图是否反转
    private final boolean hasAdditionalViews; // 是否包含额外的视图

    public StriData(
        boolean hasLeftEyeView,
        boolean hasRightEyeView,
        boolean eyeViewsReversed,
        boolean hasAdditionalViews) {
      this.hasLeftEyeView = hasLeftEyeView;
      this.hasRightEyeView = hasRightEyeView;
      this.eyeViewsReversed = eyeViewsReversed;
      this.hasAdditionalViews = hasAdditionalViews;
    }
  }

  /**
   * 从 eyes 盒子中解析出的数据。
   */
  private static final class EyesData {

    private final StriData striData; // 从 eyes 盒子中解析出的立体视图数据

    public EyesData(StriData striData) {
      this.striData = striData;
    }
  }

  /**
   * 从 mdhd 盒子中解析出的数据。
   */
  private static final class MdhdData {

    private final long timescale; // 时间尺度（单位：ticks per second）
    private final long mediaDurationUs; // 媒体持续时间（单位：微秒）
    private final String language; // 语言代码

    public MdhdData(long timescale, long mediaDurationUs, String language) {
      this.timescale = timescale;
      this.mediaDurationUs = mediaDurationUs;
      this.language = language;
    }
  }

  /**
   * 从 vexu 盒子中解析出的数据。
   */
  /* package */ static final class VexuData {

    @Nullable
    private final EyesData eyesData; // 从 vexu 盒子中解析出的眼睛数据

    public VexuData(EyesData eyesData) {
      this.eyesData = eyesData;
    }

    /**
     * 检查是否包含左右眼视图。
     *
     * @return 如果包含左右眼视图，则返回 true；否则返回 false。
     */
    public boolean hasBothEyeViews() {
      return eyesData != null
          && eyesData.striData.hasLeftEyeView
          && eyesData.striData.hasRightEyeView;
    }
  }

  /**
   * 包含样本大小的盒子（例如 stsz、stz2）。
   */
  private interface SampleSizeBox {

    /**
     * 返回样本数量。
     */
    int getSampleCount();

    /**
     * 返回每个样本的大小（如果固定），否则返回 {@link C#LENGTH_UNSET}。
     */
    int getFixedSampleSize();

    /**
     * 返回下一个样本的大小。
     */
    int readNextSampleSize();
  }

  /**
   * 一个 stsz 样本大小盒子。
   */
  /* package */ static final class StszSampleSizeBox implements SampleSizeBox {

    private final int fixedSampleSize; // 固定样本大小
    private final int sampleCount; // 样本数量
    private final ParsableByteArray data; // 存储 stsz 盒子数据的可解析字节数组

    public StszSampleSizeBox(LeafBox stszAtom, Format trackFormat) {
      data = stszAtom.data;
      data.setPosition(Mp4Box.FULL_HEADER_SIZE); // 设置数据解析位置为盒子头部之后
      int fixedSampleSize = data.readUnsignedIntToInt(); // 读取固定样本大小
      if (MimeTypes.AUDIO_RAW.equals(trackFormat.sampleMimeType)) {
        int pcmFrameSize = Util.getPcmFrameSize(trackFormat.pcmEncoding, trackFormat.channelCount);
        if (fixedSampleSize == 0 || fixedSampleSize % pcmFrameSize != 0) {
          // 如果 stsz 盒子中的样本大小与 stsd 盒子中的 PCM 编码和声道数不一致，
          // 则以 stsd 盒子为准 [Internal ref: b/171627904]。
          Log.w(
              TAG,
              "音频样本大小不匹配。stsd 样本大小: "
                  + pcmFrameSize
                  + ", stsz 样本大小: "
                  + fixedSampleSize);
          fixedSampleSize = pcmFrameSize;
        }
      }
      this.fixedSampleSize = fixedSampleSize == 0 ? C.LENGTH_UNSET : fixedSampleSize;
      sampleCount = data.readUnsignedIntToInt(); // 读取样本数量
    }

    @Override
    public int getSampleCount() {
      return sampleCount; // 返回样本数量
    }

    @Override
    public int getFixedSampleSize() {
      return fixedSampleSize; // 返回固定样本大小
    }

    @Override
    public int readNextSampleSize() {
      return fixedSampleSize == C.LENGTH_UNSET ? data.readUnsignedIntToInt() : fixedSampleSize;
      // 如果固定样本大小未设置，则读取下一个样本大小；否则返回固定样本大小
    }
  }

  /**
   * 一个 stz2 样本大小盒子。
   */
  /* package */ static final class Stz2SampleSizeBox implements SampleSizeBox {

    private final ParsableByteArray data; // 存储 stz2 盒子数据的可解析字节数组
    private final int sampleCount; // 样本数量
    private final int fieldSize; // 字段大小，可以是 4、8 或 16

    // 仅当 fieldSize == 4 时使用
    private int sampleIndex; // 当前样本索引
    private int currentByte; // 当前字节（用于处理 4 位字段大小）

    public Stz2SampleSizeBox(LeafBox stz2Atom) {
      data = stz2Atom.data;
      data.setPosition(Mp4Box.FULL_HEADER_SIZE); // 设置数据解析位置为盒子头部之后
      fieldSize = data.readUnsignedIntToInt() & 0x000000FF; // 读取字段大小
      sampleCount = data.readUnsignedIntToInt(); // 读取样本数量
    }

    @Override
    public int getSampleCount() {
      return sampleCount; // 返回样本数量
    }

    @Override
    public int getFixedSampleSize() {
      return C.LENGTH_UNSET; // 返回未设置固定样本大小
    }

    @Override
    public int readNextSampleSize() {
      if (fieldSize == 8) {
        return data.readUnsignedByte(); // 读取 8 位样本大小
      } else if (fieldSize == 16) {
        return data.readUnsignedShort(); // 读取 16 位样本大小
      } else {
        // fieldSize == 4
        if ((sampleIndex++ % 2) == 0) {
          // 当读取高 4 位时，读取下一个字节到缓存字节中
          currentByte = data.readUnsignedByte();
          // 读取字节的高 4 位并右移到低 4 位
          return (currentByte & 0xF0) >> 4;
        } else {
          // 屏蔽掉上次读取字节的高 4 位，读取低 4 位
          return currentByte & 0x0F;
        }
      }
    }
  }
}
