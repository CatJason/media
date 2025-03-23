/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.hls;

import static androidx.media3.common.util.Assertions.checkState;

import androidx.annotation.VisibleForTesting;
import androidx.media3.common.Format;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.mp3.Mp3Extractor;
import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.ts.Ac3Extractor;
import androidx.media3.extractor.ts.Ac4Extractor;
import androidx.media3.extractor.ts.AdtsExtractor;
import androidx.media3.extractor.ts.TsExtractor;
import java.io.IOException;

/**
 * {@link HlsMediaChunkExtractor} 实现，使用 ExoPlayer 应用内捆绑的 {@link Extractor}。
 */
@UnstableApi
public final class BundledHlsMediaChunkExtractor implements HlsMediaChunkExtractor {

  private static final PositionHolder POSITION_HOLDER = new PositionHolder();

  @VisibleForTesting /* package */ final Extractor extractor;
  private final Format multivariantPlaylistFormat;
  private final TimestampAdjuster timestampAdjuster;
  private final SubtitleParser.Factory subtitleParserFactory;
  private final boolean parseSubtitlesDuringExtraction;

  /**
   * 创建一个新实例。
   *
   * @param extractor 底层的 {@link Extractor}。
   * @param multivariantPlaylistFormat 从多变量播放列表获取的 {@link Format}。
   * @param timestampAdjuster 用于调整样本时间戳的 {@link TimestampAdjuster}。
   */
  public BundledHlsMediaChunkExtractor(
      Extractor extractor, Format multivariantPlaylistFormat, TimestampAdjuster timestampAdjuster) {
    this(
        extractor,
        multivariantPlaylistFormat,
        timestampAdjuster,
        SubtitleParser.Factory.UNSUPPORTED,
        /* parseSubtitlesDuringExtraction= */ false);
  }

  /**
   * 创建一个新实例。
   *
   * @param extractor 底层的 {@link Extractor}。
   * @param multivariantPlaylistFormat 从多变量播放列表获取的 {@link Format}。
   * @param timestampAdjuster 用于调整样本时间戳的 {@link TimestampAdjuster}。
   * @param subtitleParserFactory 用于 WebVTT 字幕的 {@link SubtitleParser.Factory}。如果该值为 null，字幕将在解码期间解析，否则将在提取期间解析。仅当此 subtitleParserFactory 支持提供的 multivariantPlaylistFormat 时，解码才能正常工作。
   */
  // TODO(b/289983417): 一旦字幕在提取期间解析成为唯一可用的流程，将此构造函数设为公开并移除 parseSubtitlesDuringExtraction 参数。
  /* package */ BundledHlsMediaChunkExtractor(
      Extractor extractor,
      Format multivariantPlaylistFormat,
      TimestampAdjuster timestampAdjuster,
      SubtitleParser.Factory subtitleParserFactory,
      boolean parseSubtitlesDuringExtraction) {
    this.extractor = extractor;
    this.multivariantPlaylistFormat = multivariantPlaylistFormat;
    this.timestampAdjuster = timestampAdjuster;
    this.subtitleParserFactory = subtitleParserFactory;
    this.parseSubtitlesDuringExtraction = parseSubtitlesDuringExtraction;
  }

  @Override
  public void init(ExtractorOutput extractorOutput) {
    extractor.init(extractorOutput);
  }

  @Override
  public boolean read(ExtractorInput extractorInput) throws IOException {
    return extractor.read(extractorInput, POSITION_HOLDER) == Extractor.RESULT_CONTINUE;
  }

  @Override
  public boolean isPackedAudioExtractor() {
    Extractor underlyingExtractor = extractor.getUnderlyingImplementation();
    return underlyingExtractor instanceof AdtsExtractor
        || underlyingExtractor instanceof Ac3Extractor
        || underlyingExtractor instanceof Ac4Extractor
        || underlyingExtractor instanceof Mp3Extractor;
  }

  @Override
  public boolean isReusable() {
    Extractor underlyingExtractor = extractor.getUnderlyingImplementation();
    return underlyingExtractor instanceof TsExtractor
        || underlyingExtractor instanceof FragmentedMp4Extractor;
  }

  @Override
  public HlsMediaChunkExtractor recreate() {
    checkState(!isReusable());
    checkState(
        extractor.getUnderlyingImplementation() == extractor,
        "Can't recreate wrapped extractors. Outer type: " + extractor.getClass());
    Extractor newExtractorInstance;
    // LINT.IfChange(extractor_instantiation)
    if (extractor instanceof WebvttExtractor) {
      newExtractorInstance =
          new WebvttExtractor(
              multivariantPlaylistFormat.language,
              timestampAdjuster,
              subtitleParserFactory,
              parseSubtitlesDuringExtraction);
    } else if (extractor instanceof AdtsExtractor) {
      newExtractorInstance = new AdtsExtractor();
    } else if (extractor instanceof Ac3Extractor) {
      newExtractorInstance = new Ac3Extractor();
    } else if (extractor instanceof Ac4Extractor) {
      newExtractorInstance = new Ac4Extractor();
    } else if (extractor instanceof Mp3Extractor) {
      newExtractorInstance = new Mp3Extractor();
    } else {
      throw new IllegalStateException(
          "Unexpected extractor type for recreation: " + extractor.getClass().getSimpleName());
    }
    return new BundledHlsMediaChunkExtractor(
        newExtractorInstance,
        multivariantPlaylistFormat,
        timestampAdjuster,
        subtitleParserFactory,
        parseSubtitlesDuringExtraction);
  }

  @Override
  public void onTruncatedSegmentParsed() {
    extractor.seek(/* position= */ 0, /* timeUs= */ 0);
  }
}
