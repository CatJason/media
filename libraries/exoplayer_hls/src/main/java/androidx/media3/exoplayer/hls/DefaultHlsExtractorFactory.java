package androidx.media3.exoplayer.hls;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.extractor.ts.TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.FileTypes;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.mp3.Mp3Extractor;
import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.ts.Ac3Extractor;
import androidx.media3.extractor.ts.Ac4Extractor;
import androidx.media3.extractor.ts.AdtsExtractor;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 默认的 {@link HlsExtractorFactory} 实现。 */
@UnstableApi
public final class DefaultHlsExtractorFactory implements HlsExtractorFactory {

  // 提取器顺序根据以下文档进行了优化：
  // https://docs.google.com/document/d/1w2mKaWMxfz2Ei8-LdxqbPs1VLe_oudB-eryXXw9OvQQ.
  private static final int[] DEFAULT_EXTRACTOR_ORDER =
      new int[] {
          FileTypes.MP4,
          FileTypes.WEBVTT,
          FileTypes.TS,
          FileTypes.ADTS,
          FileTypes.AC3,
          FileTypes.AC4,
          FileTypes.MP3,
      };

  private final @DefaultTsPayloadReaderFactory.Flags int payloadReaderFactoryFlags;

  private SubtitleParser.Factory subtitleParserFactory;
  private boolean parseSubtitlesDuringExtraction;

  private final boolean exposeCea608WhenMissingDeclarations;

  /**
   * 等效于 {@link #DefaultHlsExtractorFactory(int, boolean) new
   * DefaultHlsExtractorFactory(payloadReaderFactoryFlags = 0, exposeCea608WhenMissingDeclarations =
   * true)}
   */
  public DefaultHlsExtractorFactory() {
    this(/* payloadReaderFactoryFlags= */ 0, /* exposeCea608WhenMissingDeclarations */ true);
  }

  /**
   * 创建用于 HLS 片段提取器的工厂。
   *
   * @param payloadReaderFactoryFlags 在构造任何 {@link DefaultTsPayloadReaderFactory} 实例时添加的标志。在创建 {@link DefaultTsPayloadReaderFactory} 时，可能会在 {@code payloadReaderFactoryFlags} 的基础上添加其他标志。
   * @param exposeCea608WhenMissingDeclarations 当多变量播放列表不包含任何 Closed Captions 声明时，创建的 {@link TsExtractor} 实例是否应暴露 CEA-608 轨道。如果多变量播放列表包含任何 Closed Captions 声明，则忽略此标志。
   */
  public DefaultHlsExtractorFactory(
      int payloadReaderFactoryFlags, boolean exposeCea608WhenMissingDeclarations) {
    this.payloadReaderFactoryFlags = payloadReaderFactoryFlags;
    this.exposeCea608WhenMissingDeclarations = exposeCea608WhenMissingDeclarations;
    subtitleParserFactory = new DefaultSubtitleParserFactory();
  }

  @Override
  public BundledHlsMediaChunkExtractor createExtractor(
      Uri uri,
      Format format,
      @Nullable List<Format> muxedCaptionFormats,
      TimestampAdjuster timestampAdjuster,
      Map<String, List<String>> responseHeaders,
      ExtractorInput sniffingExtractorInput,
      PlayerId playerId)
      throws IOException {
    @FileTypes.Type
    int formatInferredFileType = FileTypes.inferFileTypeFromMimeType(format.sampleMimeType);
    @FileTypes.Type
    int responseHeadersInferredFileType =
        FileTypes.inferFileTypeFromResponseHeaders(responseHeaders);
    @FileTypes.Type int uriInferredFileType = FileTypes.inferFileTypeFromUri(uri);

    // 定义尝试提取器的顺序。
    List<Integer> fileTypeOrder =
        new ArrayList<>(/* initialCapacity= */ DEFAULT_EXTRACTOR_ORDER.length);
    addFileTypeIfValidAndNotPresent(formatInferredFileType, fileTypeOrder);
    addFileTypeIfValidAndNotPresent(responseHeadersInferredFileType, fileTypeOrder);
    addFileTypeIfValidAndNotPresent(uriInferredFileType, fileTypeOrder);
    for (int fileType : DEFAULT_EXTRACTOR_ORDER) {
      addFileTypeIfValidAndNotPresent(fileType, fileTypeOrder);
    }
    // 如果类型无法识别，则使用的提取器。
    @Nullable Extractor fallBackExtractor = null;
    sniffingExtractorInput.resetPeekPosition();
    for (int i = 0; i < fileTypeOrder.size(); i++) {
      int fileType = fileTypeOrder.get(i);
      Extractor extractor =
          checkNotNull(
              createExtractorByFileType(fileType, format, muxedCaptionFormats, timestampAdjuster));
      if (sniffQuietly(extractor, sniffingExtractorInput)) {
        return new BundledHlsMediaChunkExtractor(
            extractor,
            format,
            timestampAdjuster,
            subtitleParserFactory,
            parseSubtitlesDuringExtraction);
      }
      if (fallBackExtractor == null
          && (fileType == formatInferredFileType
          || fileType == responseHeadersInferredFileType
          || fileType == uriInferredFileType
          || fileType == FileTypes.TS)) {
        // 如果嗅探失败，则回退到从上下文推断的文件类型。如果所有方法都失败，则回退到传输流。参见 https://github.com/google/ExoPlayer/issues/8219。
        fallBackExtractor = extractor;
      }
    }

    return new BundledHlsMediaChunkExtractor(
        checkNotNull(fallBackExtractor),
        format,
        timestampAdjuster,
        subtitleParserFactory,
        parseSubtitlesDuringExtraction);
  }

  @CanIgnoreReturnValue
  @Override
  public DefaultHlsExtractorFactory setSubtitleParserFactory(
      SubtitleParser.Factory subtitleParserFactory) {
    this.subtitleParserFactory = subtitleParserFactory;
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public DefaultHlsExtractorFactory experimentalParseSubtitlesDuringExtraction(
      boolean parseSubtitlesDuringExtraction) {
    this.parseSubtitlesDuringExtraction = parseSubtitlesDuringExtraction;
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * <p>如果 {@link SubtitleParser.Factory} 支持，此实现会将原始格式转码为 {@link MimeTypes#APPLICATION_MEDIA3_CUES}。
   *
   * <p>要修改支持行为，可以 {@linkplain #setSubtitleParserFactory(SubtitleParser.Factory) 设置您自己的字幕解析器工厂}。
   */
  @Override
  public Format getOutputTextFormat(Format sourceFormat) {
    if (parseSubtitlesDuringExtraction && subtitleParserFactory.supportsFormat(sourceFormat)) {
      return sourceFormat
          .buildUpon()
          .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
          .setCueReplacementBehavior(subtitleParserFactory.getCueReplacementBehavior(sourceFormat))
          .setCodecs(
              sourceFormat.sampleMimeType
                  + (sourceFormat.codecs != null ? " " + sourceFormat.codecs : ""))
          .setSubsampleOffsetUs(Format.OFFSET_SAMPLE_RELATIVE)
          .build();
    } else {
      return sourceFormat;
    }
  }

  private static void addFileTypeIfValidAndNotPresent(
      @FileTypes.Type int fileType, List<Integer> fileTypes) {
    if (Ints.indexOf(DEFAULT_EXTRACTOR_ORDER, fileType) == -1 || fileTypes.contains(fileType)) {
      return;
    }
    fileTypes.add(fileType);
  }

  @SuppressLint("SwitchIntDef") // HLS only supports a small subset of the defined file types.
  @Nullable
  private Extractor createExtractorByFileType(
      @FileTypes.Type int fileType,
      Format format,
      @Nullable List<Format> muxedCaptionFormats,
      TimestampAdjuster timestampAdjuster) {
    // LINT.IfChange(extractor_instantiation)
    switch (fileType) {
      case FileTypes.WEBVTT:
        return new WebvttExtractor(
            format.language,
            timestampAdjuster,
            subtitleParserFactory,
            parseSubtitlesDuringExtraction);
      case FileTypes.ADTS:
        return new AdtsExtractor();
      case FileTypes.AC3:
        return new Ac3Extractor();
      case FileTypes.AC4:
        return new Ac4Extractor();
      case FileTypes.MP3:
        return new Mp3Extractor(/* flags= */ 0, /* forcedFirstSampleTimestampUs= */ 0);
      case FileTypes.MP4:
        return createFragmentedMp4Extractor(
            subtitleParserFactory,
            parseSubtitlesDuringExtraction,
            timestampAdjuster,
            format,
            muxedCaptionFormats);
      case FileTypes.TS:
        return createTsExtractor(
            payloadReaderFactoryFlags,
            exposeCea608WhenMissingDeclarations,
            format,
            muxedCaptionFormats,
            timestampAdjuster,
            subtitleParserFactory,
            parseSubtitlesDuringExtraction);
      default:
        return null;
    }
  }

  private static TsExtractor createTsExtractor(
      @DefaultTsPayloadReaderFactory.Flags int userProvidedPayloadReaderFactoryFlags,
      boolean exposeCea608WhenMissingDeclarations,
      Format format,
      @Nullable List<Format> muxedCaptionFormats,
      TimestampAdjuster timestampAdjuster,
      SubtitleParser.Factory subtitleParserFactory,
      boolean parseSubtitlesDuringExtraction) {
    @DefaultTsPayloadReaderFactory.Flags
    int payloadReaderFactoryFlags =
        DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            | userProvidedPayloadReaderFactoryFlags;
    if (muxedCaptionFormats != null) {
      // 播放列表声明了隐藏字幕渲染，我们应该忽略描述符。
      payloadReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS;
    } else if (exposeCea608WhenMissingDeclarations) {
      // 播放列表未提供任何隐藏字幕信息。我们预先声明一个在通道 0 上的隐藏字幕轨道。
      muxedCaptionFormats =
          Collections.singletonList(
              new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).build());
    } else {
      muxedCaptionFormats = Collections.emptyList();
    }
    @Nullable String codecs = format.codecs;
    if (!TextUtils.isEmpty(codecs)) {
      // 有时即使不存在 AAC 和 H264 流，它们也会在 TS 块中被声明。如果我们从编解码器属性中知道它们不存在，那么即使它们被声明，我们也可以明确忽略它们。
      if (!MimeTypes.containsCodecsCorrespondingToMimeType(codecs, MimeTypes.AUDIO_AAC)) {
        payloadReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_AAC_STREAM;
      }
      if (!MimeTypes.containsCodecsCorrespondingToMimeType(codecs, MimeTypes.VIDEO_H264)) {
        payloadReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_H264_STREAM;
      }
    }
    @TsExtractor.Flags int extractorFlags = 0;
    if (!parseSubtitlesDuringExtraction) {
      subtitleParserFactory = SubtitleParser.Factory.UNSUPPORTED;
      extractorFlags |= TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA;
    }
    return new TsExtractor(
        TsExtractor.MODE_HLS,
        extractorFlags,
        subtitleParserFactory,
        timestampAdjuster,
        new DefaultTsPayloadReaderFactory(payloadReaderFactoryFlags, muxedCaptionFormats),
        DEFAULT_TIMESTAMP_SEARCH_BYTES);
  }

  private static FragmentedMp4Extractor createFragmentedMp4Extractor(
      SubtitleParser.Factory subtitleParserFactory,
      boolean parseSubtitlesDuringExtraction,
      TimestampAdjuster timestampAdjuster,
      Format format,
      @Nullable List<Format> muxedCaptionFormats) {
    // 仅当这是“变体”轨道（即主轨道）时，才启用 EMSG TrackOutput，以避免为视频流中的每个音频轨道创建单独的 EMSG 轨道。
    @FragmentedMp4Extractor.Flags
    int flags = isFmp4Variant(format) ? FragmentedMp4Extractor.FLAG_ENABLE_EMSG_TRACK : 0;
    if (!parseSubtitlesDuringExtraction) {
      subtitleParserFactory = SubtitleParser.Factory.UNSUPPORTED;
      flags |= FragmentedMp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA;
    }
    return new FragmentedMp4Extractor(
        subtitleParserFactory,
        flags,
        timestampAdjuster,
        /* sideloadedTrack= */ null,
        muxedCaptionFormats != null ? muxedCaptionFormats : ImmutableList.of(),
        /* additionalEmsgTrackOutput= */ null);
  }

  /** 如果此 {@code format} 表示“变体”轨道（即主轨道），则返回 true。 */
  private static boolean isFmp4Variant(Format format) {
    Metadata metadata = format.metadata;
    if (metadata == null) {
      return false;
    }
    for (int i = 0; i < metadata.length(); i++) {
      Metadata.Entry entry = metadata.get(i);
      if (entry instanceof HlsTrackMetadataEntry) {
        return !((HlsTrackMetadataEntry) entry).variantInfos.isEmpty();
      }
    }
    return false;
  }

  private static boolean sniffQuietly(Extractor extractor, ExtractorInput input)
      throws IOException {
    boolean result = false;
    try {
      result = extractor.sniff(input);
    } catch (EOFException e) {
      // Do nothing.
    } finally {
      input.resetPeekPosition();
    }
    return result;
  }
}
