package androidx.media3.exoplayer.hls;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.text.SubtitleTranscodingExtractorOutput;
import androidx.media3.extractor.text.webvtt.WebvttParserUtil;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * 用于 HLS 中 WebVTT 内容的专用提取器。
 *
 * <p>该提取器将非空的 WebVTT 文件原样传递，但通过嗅探 X-TIMESTAMP-MAP 标头以及第一个 cue 标头的起始时间戳来推导每个样本的正确时间戳。
 * 空的 WebVTT 文件不会被传递，因为在这种情况下无法推导样本时间戳。
 */
@UnstableApi
public final class WebvttExtractor implements Extractor {

  private static final Pattern LOCAL_TIMESTAMP = Pattern.compile("LOCAL:([^,]+)");
  private static final Pattern MEDIA_TIMESTAMP = Pattern.compile("MPEGTS:(-?\\d+)");
  private static final int HEADER_MIN_LENGTH = 6 /* "WEBVTT" */;
  private static final int HEADER_MAX_LENGTH = 3 /* 可选的字节顺序标记 */ + HEADER_MIN_LENGTH;

  @Nullable private final String language;
  private final TimestampAdjuster timestampAdjuster;
  private final ParsableByteArray sampleDataWrapper;
  private final SubtitleParser.Factory subtitleParserFactory;
  private final boolean parseSubtitlesDuringExtraction;

  private @MonotonicNonNull ExtractorOutput output;

  private byte[] sampleData;
  private int sampleSize;

  /**
   * @deprecated 请使用 {@link #WebvttExtractor(String, TimestampAdjuster, SubtitleParser.Factory, boolean)} 代替。
   */
  @Deprecated
  public WebvttExtractor(@Nullable String language, TimestampAdjuster timestampAdjuster) {
    this(
        language,
        timestampAdjuster,
        SubtitleParser.Factory.UNSUPPORTED,
        /* parseSubtitlesDuringExtraction= */ false);
  }

  public WebvttExtractor(
      @Nullable String language,
      TimestampAdjuster timestampAdjuster,
      SubtitleParser.Factory subtitleParserFactory,
      boolean parseSubtitlesDuringExtraction) {
    this.language = language;
    this.timestampAdjuster = timestampAdjuster;
    this.sampleDataWrapper = new ParsableByteArray();
    sampleData = new byte[1024];
    this.subtitleParserFactory = subtitleParserFactory;
    this.parseSubtitlesDuringExtraction = parseSubtitlesDuringExtraction;
  }

  // Extractor 实现。

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    // 检查是否存在不带 BOM 的标头。
    input.peekFully(
        sampleData, /* offset= */ 0, /* length= */ HEADER_MIN_LENGTH, /* allowEndOfInput= */ false);
    sampleDataWrapper.reset(sampleData, HEADER_MIN_LENGTH);
    if (WebvttParserUtil.isWebvttHeaderLine(sampleDataWrapper)) {
      return true;
    }
    // 标头不匹配，尝试包含 BOM。
    input.peekFully(
        sampleData,
        /* offset= */ HEADER_MIN_LENGTH,
        HEADER_MAX_LENGTH - HEADER_MIN_LENGTH,
        /* allowEndOfInput= */ false);
    sampleDataWrapper.reset(sampleData, HEADER_MAX_LENGTH);
    return WebvttParserUtil.isWebvttHeaderLine(sampleDataWrapper);
  }

  @Override
  public void init(ExtractorOutput output) {
    this.output =
        parseSubtitlesDuringExtraction
            ? new SubtitleTranscodingExtractorOutput(output, subtitleParserFactory)
            : output;
    this.output.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
  }

  @Override
  public void seek(long position, long timeUs) {
    // 该提取器仅用于 HLS 场景，不应调用此方法。
    throw new IllegalStateException();
  }

  @Override
  public void release() {
    // 无操作
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    // output == null 表示 init() 未被调用
    Assertions.checkNotNull(output);
    int currentFileSize = (int) input.getLength();

    // 如有必要，增加 sampleData 的大小。
    if (sampleSize == sampleData.length) {
      sampleData =
          Arrays.copyOf(
              sampleData,
              (currentFileSize != C.LENGTH_UNSET ? currentFileSize : sampleData.length) * 3 / 2);
    }

    // 从输入中读取数据。
    int bytesRead = input.read(sampleData, sampleSize, sampleData.length - sampleSize);
    if (bytesRead != C.RESULT_END_OF_INPUT) {
      sampleSize += bytesRead;
      if (currentFileSize == C.LENGTH_UNSET || sampleSize != currentFileSize) {
        return Extractor.RESULT_CONTINUE;
      }
    }

    // 已到达输入末尾，对应当前文件的末尾。
    processSample();
    return Extractor.RESULT_END_OF_INPUT;
  }

  @RequiresNonNull("output")
  private void processSample() throws ParserException {
    ParsableByteArray webvttData = new ParsableByteArray(sampleData);

    // 验证标头的第一行。
    WebvttParserUtil.validateWebvttHeaderLine(webvttData);

    // 如果标头不包含 X-TIMESTAMP-MAP 标头，则使用默认值。
    long vttTimestampUs = 0;
    long tsTimestampUs = 0;

    // 解析标头的其余部分，查找 X-TIMESTAMP-MAP。
    for (String line = webvttData.readLine();
        !TextUtils.isEmpty(line);
        line = webvttData.readLine()) {
      if (line.startsWith("X-TIMESTAMP-MAP")) {
        Matcher localTimestampMatcher = LOCAL_TIMESTAMP.matcher(line);
        if (!localTimestampMatcher.find()) {
          throw ParserException.createForMalformedContainer(
              "X-TIMESTAMP-MAP 不包含本地时间戳: " + line, /* cause= */ null);
        }
        Matcher mediaTimestampMatcher = MEDIA_TIMESTAMP.matcher(line);
        if (!mediaTimestampMatcher.find()) {
          throw ParserException.createForMalformedContainer(
              "X-TIMESTAMP-MAP 不包含媒体时间戳: " + line, /* cause= */ null);
        }
        vttTimestampUs =
            WebvttParserUtil.parseTimestampUs(
                Assertions.checkNotNull(localTimestampMatcher.group(1)));
        tsTimestampUs =
            TimestampAdjuster.ptsToUs(
                Long.parseLong(Assertions.checkNotNull(mediaTimestampMatcher.group(1))));
      }
    }

    // 查找第一个 cue 标头并解析起始时间。
    Matcher cueHeaderMatcher = WebvttParserUtil.findNextCueHeader(webvttData);
    if (cueHeaderMatcher == null) {
      // 未找到 cue。不输出样本，但仍输出对应的轨道。
      buildTrackOutput(0);
      return;
    }

    long firstCueTimeUs =
        WebvttParserUtil.parseTimestampUs(Assertions.checkNotNull(cueHeaderMatcher.group(1)));
    long sampleTimeUs =
        timestampAdjuster.adjustTsTimestamp(
            TimestampAdjuster.usToWrappedPts(firstCueTimeUs + tsTimestampUs - vttTimestampUs));
    long subsampleOffsetUs = sampleTimeUs - firstCueTimeUs;
    // 输出轨道。
    TrackOutput trackOutput = buildTrackOutput(subsampleOffsetUs);
    // 输出样本。
    sampleDataWrapper.reset(sampleData, sampleSize);
    trackOutput.sampleData(sampleDataWrapper, sampleSize);
    trackOutput.sampleMetadata(sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, 0, null);
  }

  @RequiresNonNull("output")
  private TrackOutput buildTrackOutput(long subsampleOffsetUs) {
    TrackOutput trackOutput = output.track(0, C.TRACK_TYPE_TEXT);
    trackOutput.format(
        new Format.Builder()
            .setSampleMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(language)
            .setSubsampleOffsetUs(subsampleOffsetUs)
            .build());
    output.endTracks();
    return trackOutput;
  }
}