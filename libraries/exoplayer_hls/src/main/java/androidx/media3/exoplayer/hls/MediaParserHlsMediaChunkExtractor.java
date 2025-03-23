package androidx.media3.exoplayer.hls;

import static android.media.MediaParser.PARAMETER_TS_IGNORE_AAC_STREAM;
import static android.media.MediaParser.PARAMETER_TS_IGNORE_AVC_STREAM;
import static android.media.MediaParser.PARAMETER_TS_IGNORE_SPLICE_INFO_STREAM;
import static android.media.MediaParser.PARAMETER_TS_MODE;
import static androidx.media3.exoplayer.source.mediaparser.MediaParserUtil.PARAMETER_EAGERLY_EXPOSE_TRACK_TYPE;
import static androidx.media3.exoplayer.source.mediaparser.MediaParserUtil.PARAMETER_EXPOSE_CAPTION_FORMATS;
import static androidx.media3.exoplayer.source.mediaparser.MediaParserUtil.PARAMETER_IGNORE_TIMESTAMP_OFFSET;
import static androidx.media3.exoplayer.source.mediaparser.MediaParserUtil.PARAMETER_IN_BAND_CRYPTO_INFO;
import static androidx.media3.exoplayer.source.mediaparser.MediaParserUtil.PARAMETER_OVERRIDE_IN_BAND_CAPTION_DECLARATIONS;

import android.annotation.SuppressLint;
import android.media.MediaFormat;
import android.media.MediaParser;
import android.media.MediaParser.OutputConsumer;
import android.media.MediaParser.SeekPoint;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.FileTypes;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.mediaparser.InputReaderAdapterV30;
import androidx.media3.exoplayer.source.mediaparser.MediaParserUtil;
import androidx.media3.exoplayer.source.mediaparser.OutputConsumerAdapterV30;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.common.collect.ImmutableList;
import java.io.IOException;

/** 基于平台 {@link MediaParser} 实现的 {@link HlsMediaChunkExtractor}。 */
@RequiresApi(30)
@UnstableApi
public final class MediaParserHlsMediaChunkExtractor implements HlsMediaChunkExtractor {

  /**
   * {@link HlsExtractorFactory} 实现，为除 WebVTT 之外的所有容器格式生成 {@link MediaParserHlsMediaChunkExtractor}，
   * 对于 WebVTT，返回 {@link BundledHlsMediaChunkExtractor}。
   */
  public static final HlsExtractorFactory FACTORY =
      (uri,
          format,
          muxedCaptionFormats,
          timestampAdjuster,
          responseHeaders,
          sniffingExtractorInput,
          playerId) -> {
        if (FileTypes.inferFileTypeFromMimeType(format.sampleMimeType) == FileTypes.WEBVTT) {
          // 该段包含 WebVTT。MediaParser 不支持 WebVTT 解析，因此我们使用捆绑的提取器。
          return new BundledHlsMediaChunkExtractor(
              new WebvttExtractor(
                  format.language,
                  timestampAdjuster,
                  SubtitleParser.Factory.UNSUPPORTED,
                  /* parseSubtitlesDuringExtraction= */ false),
              format,
              timestampAdjuster);
        }

        boolean overrideInBandCaptionDeclarations = muxedCaptionFormats != null;
        ImmutableList.Builder<MediaFormat> muxedCaptionMediaFormatsBuilder =
            ImmutableList.builder();
        if (muxedCaptionFormats != null) {
          // 清单中包含字幕声明。我们使用这些声明来确定 MediaParser 将暴露哪些字幕。
          for (int i = 0; i < muxedCaptionFormats.size(); i++) {
            muxedCaptionMediaFormatsBuilder.add(
                MediaParserUtil.toCaptionsMediaFormat(muxedCaptionFormats.get(i)));
          }
        } else {
          // 清单中未声明流中的任何字幕。模仿默认的 HLS 提取器工厂，默认声明一个 608 轨道。
          muxedCaptionMediaFormatsBuilder.add(
              MediaParserUtil.toCaptionsMediaFormat(
                  new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).build()));
        }

        ImmutableList<MediaFormat> muxedCaptionMediaFormats =
            muxedCaptionMediaFormatsBuilder.build();

        // TODO: 将优化嗅探顺序的代码提取到两个工厂中。
        OutputConsumerAdapterV30 outputConsumerAdapter = new OutputConsumerAdapterV30();
        outputConsumerAdapter.setMuxedCaptionFormats(
            muxedCaptionFormats != null ? muxedCaptionFormats : ImmutableList.of());
        outputConsumerAdapter.setTimestampAdjuster(timestampAdjuster);
        MediaParser mediaParser =
            createMediaParserInstance(
                outputConsumerAdapter,
                format,
                overrideInBandCaptionDeclarations,
                muxedCaptionMediaFormats,
                playerId,
                MediaParser.PARSER_NAME_FMP4,
                MediaParser.PARSER_NAME_AC3,
                MediaParser.PARSER_NAME_AC4,
                MediaParser.PARSER_NAME_ADTS,
                MediaParser.PARSER_NAME_MP3,
                MediaParser.PARSER_NAME_TS);

        PeekingInputReader peekingInputReader = new PeekingInputReader(sniffingExtractorInput);
        // 块提取器构造函数需要一个已知解析器名称的实例，因此我们前进一次以便 MediaParser 嗅探内容。
        mediaParser.advance(peekingInputReader);
        outputConsumerAdapter.setSelectedParserName(mediaParser.getParserName());

        return new MediaParserHlsMediaChunkExtractor(
            mediaParser,
            outputConsumerAdapter,
            format,
            overrideInBandCaptionDeclarations,
            muxedCaptionMediaFormats,
            /* leadingBytesToSkip= */ peekingInputReader.totalPeekedBytes,
            playerId);
      };

  private final OutputConsumerAdapterV30 outputConsumerAdapter;
  private final InputReaderAdapterV30 inputReaderAdapter;
  private final MediaParser mediaParser;
  private final Format format;
  private final boolean overrideInBandCaptionDeclarations;
  private final ImmutableList<MediaFormat> muxedCaptionMediaFormats;
  private final PlayerId playerId;

  private int pendingSkipBytes;

  /**
   * 创建一个新实例。
   *
   * @param mediaParser 用于提取段的 {@link MediaParser} 实例。提供的实例必须已完成嗅探，或必须按名称创建。
   * @param outputConsumerAdapter 用于创建 {@code mediaParser} 的 {@link OutputConsumerAdapterV30}。
   * @param format 与段关联的 {@link Format}。
   * @param overrideInBandCaptionDeclarations 是否忽略任何带内字幕轨道声明，转而使用 {@code muxedCaptionMediaFormats}。
   *     如果为 false，则将使用提取的媒体中找到的字幕声明，导致 {@code muxedCaptionMediaFormats} 被忽略。
   * @param muxedCaptionMediaFormats {@link MediaParser} 应暴露的带内字幕 {@link MediaFormat MediaFormats} 列表。
   * @param leadingBytesToSkip 在开始提取之前从输入开头跳过的字节数。
   * @param playerId 使用此块提取器的播放器的 {@link PlayerId}。
   */
  public MediaParserHlsMediaChunkExtractor(
      MediaParser mediaParser,
      OutputConsumerAdapterV30 outputConsumerAdapter,
      Format format,
      boolean overrideInBandCaptionDeclarations,
      ImmutableList<MediaFormat> muxedCaptionMediaFormats,
      int leadingBytesToSkip,
      PlayerId playerId) {
    this.mediaParser = mediaParser;
    this.outputConsumerAdapter = outputConsumerAdapter;
    this.overrideInBandCaptionDeclarations = overrideInBandCaptionDeclarations;
    this.muxedCaptionMediaFormats = muxedCaptionMediaFormats;
    this.format = format;
    this.playerId = playerId;
    pendingSkipBytes = leadingBytesToSkip;
    inputReaderAdapter = new InputReaderAdapterV30();
  }

  // ChunkExtractor 实现。

  @Override
  public void init(ExtractorOutput extractorOutput) {
    outputConsumerAdapter.setExtractorOutput(extractorOutput);
  }

  @Override
  public boolean read(ExtractorInput extractorInput) throws IOException {
    extractorInput.skipFully(pendingSkipBytes);
    pendingSkipBytes = 0;
    inputReaderAdapter.setDataReader(extractorInput, extractorInput.getLength());
    return mediaParser.advance(inputReaderAdapter);
  }

  @Override
  public boolean isPackedAudioExtractor() {
    String parserName = mediaParser.getParserName();
    return MediaParser.PARSER_NAME_AC3.equals(parserName)
        || MediaParser.PARSER_NAME_AC4.equals(parserName)
        || MediaParser.PARSER_NAME_ADTS.equals(parserName)
        || MediaParser.PARSER_NAME_MP3.equals(parserName);
  }

  @Override
  public boolean isReusable() {
    String parserName = mediaParser.getParserName();
    return MediaParser.PARSER_NAME_FMP4.equals(parserName)
        || MediaParser.PARSER_NAME_TS.equals(parserName);
  }

  @Override
  public HlsMediaChunkExtractor recreate() {
    Assertions.checkState(!isReusable());
    return new MediaParserHlsMediaChunkExtractor(
        createMediaParserInstance(
            outputConsumerAdapter,
            format,
            overrideInBandCaptionDeclarations,
            muxedCaptionMediaFormats,
            playerId,
            mediaParser.getParserName()),
        outputConsumerAdapter,
        format,
        overrideInBandCaptionDeclarations,
        muxedCaptionMediaFormats,
        /* leadingBytesToSkip= */ 0,
        playerId);
  }

  @Override
  public void onTruncatedSegmentParsed() {
    mediaParser.seek(SeekPoint.START);
  }

  // 允许使用不属于公共 MediaParser API 的常量。
  @SuppressLint({"WrongConstant"})
  private static MediaParser createMediaParserInstance(
      OutputConsumer outputConsumer,
      Format format,
      boolean overrideInBandCaptionDeclarations,
      ImmutableList<MediaFormat> muxedCaptionMediaFormats,
      PlayerId playerId,
      String... parserNames) {
    MediaParser mediaParser =
        parserNames.length == 1
            ? MediaParser.createByName(parserNames[0], outputConsumer)
            : MediaParser.create(outputConsumer, parserNames);
    mediaParser.setParameter(PARAMETER_EXPOSE_CAPTION_FORMATS, muxedCaptionMediaFormats);
    mediaParser.setParameter(
        PARAMETER_OVERRIDE_IN_BAND_CAPTION_DECLARATIONS, overrideInBandCaptionDeclarations);
    mediaParser.setParameter(PARAMETER_IN_BAND_CRYPTO_INFO, true);
    mediaParser.setParameter(PARAMETER_EAGERLY_EXPOSE_TRACK_TYPE, true);
    mediaParser.setParameter(PARAMETER_IGNORE_TIMESTAMP_OFFSET, true);
    mediaParser.setParameter(PARAMETER_TS_IGNORE_SPLICE_INFO_STREAM, true);
    mediaParser.setParameter(PARAMETER_TS_MODE, "hls");
    @Nullable String codecs = format.codecs;
    if (!TextUtils.isEmpty(codecs)) {
      // 有时 AAC 和 H264 流会在 TS 块中声明，即使它们实际上并不存在。如果我们从编解码器属性中知道它们不存在，
      // 那么即使它们被声明，我们也可以明确地忽略它们。
      if (!MimeTypes.AUDIO_AAC.equals(MimeTypes.getAudioMediaMimeType(codecs))) {
        mediaParser.setParameter(PARAMETER_TS_IGNORE_AAC_STREAM, true);
      }
      if (!MimeTypes.VIDEO_H264.equals(MimeTypes.getVideoMediaMimeType(codecs))) {
        mediaParser.setParameter(PARAMETER_TS_IGNORE_AVC_STREAM, true);
      }
    }
    if (Util.SDK_INT >= 31) {
      MediaParserUtil.setLogSessionIdOnMediaParser(mediaParser, playerId);
    }
    return mediaParser;
  }

  private static final class PeekingInputReader implements MediaParser.SeekableInputReader {

    private final ExtractorInput extractorInput;
    private int totalPeekedBytes;

    private PeekingInputReader(ExtractorInput extractorInput) {
      this.extractorInput = extractorInput;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
      int peekedBytes = extractorInput.peek(buffer, offset, readLength);
      totalPeekedBytes += peekedBytes;
      return peekedBytes;
    }

    @Override
    public long getPosition() {
      return extractorInput.getPeekPosition();
    }

    @Override
    public long getLength() {
      return extractorInput.getLength();
    }

    @Override
    public void seekToPosition(long position) {
      // 在嗅探内容时不允许查找。
      throw new UnsupportedOperationException();
    }
  }
}