package androidx.media3.exoplayer.hls;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** HLS 媒体块提取器的工厂。 */
@UnstableApi
public interface HlsExtractorFactory {

  /**
   * @deprecated {@code HlsExtractorFactory} 实例是可变的，因此在静态字段中共享一个实例是不安全的。每次使用时请构造一个新的 {@link DefaultHlsExtractorFactory} 实例。
   */
  @Deprecated HlsExtractorFactory DEFAULT = new DefaultHlsExtractorFactory();

  /**
   * 创建一个用于提取 HLS 媒体块的 {@link Extractor}。
   *
   * @param uri 媒体块的 URI。
   * @param format 与要提取的块关联的 {@link Format}。
   * @param muxedCaptionFormats 多路复用的字幕 {@link Format} 列表。如果多变量播放列表中没有隐藏字幕信息，则为 null。
   * @param timestampAdjuster 与提供的间断序列号对应的时间戳调整器。
   * @param responseHeaders 与要提取的媒体片段或初始化部分关联的 HTTP 响应头。
   * @param sniffingExtractorInput 将传递给返回的提取器的 {@link Extractor#read(ExtractorInput, PositionHolder)} 的第一个提取器输入。只能用于调用 {@link Extractor#sniff(ExtractorInput)}。
   * @param playerId 使用此提取器工厂的播放器的 {@link PlayerId}。
   * @return 一个 {@link HlsMediaChunkExtractor}。
   * @throws IOException 如果在嗅探时遇到 I/O 错误。
   */
  HlsMediaChunkExtractor createExtractor(
      Uri uri,
      Format format,
      @Nullable List<Format> muxedCaptionFormats,
      TimestampAdjuster timestampAdjuster,
      Map<String, List<String>> responseHeaders,
      ExtractorInput sniffingExtractorInput,
      PlayerId playerId)
      throws IOException;

  /**
   * 设置用于在提取过程中解析字幕的 {@link SubtitleParser.Factory}。默认的工厂值取决于具体实现。
   *
   * @param subtitleParserFactory 用于在提取过程中解析字幕的 {@link SubtitleParser.Factory}。
   * @return 为了方便，返回此工厂。
   */
  @CanIgnoreReturnValue
  default HlsExtractorFactory setSubtitleParserFactory(
      SubtitleParser.Factory subtitleParserFactory) {
    return this;
  }

  /**
   * 设置是否应在提取过程中（在添加到样本队列之前）或在渲染过程中（从样本队列中取出时）解析字幕。默认为 {@code false}（即字幕将在渲染过程中解析）。
   *
   * <p>此方法是实验性的，将在未来的版本中重命名或移除。
   *
   * @param parseSubtitlesDuringExtraction 是否在提取过程中解析字幕。
   * @return 为了方便，返回此工厂。
   */
  @CanIgnoreReturnValue
  default HlsExtractorFactory experimentalParseSubtitlesDuringExtraction(
      boolean parseSubtitlesDuringExtraction) {
    return this;
  }

  /**
   * 返回最初为 {@code sourceFormat} 的 {@linkplain C#TRACK_TYPE_TEXT 文本样本} 的输出 {@link Format}。
   *
   * <p>在许多情况下，如果 {@link Extractor} 从源中提取样本而不进行修改，则此方法直接返回 {@code sourceFormat}。在其他情况下，例如 {@link Extractor} 将字幕从 {@code sourceFormat} 转码为 {@link MimeTypes#APPLICATION_MEDIA3_CUES}，则会更新格式以指示正在进行的转码。
   *
   * <p>非文本的源格式始终会原样返回。
   *
   * @param sourceFormat 原始的基于文本的格式。
   * @return 将与 {@linkplain C#TRACK_TYPE_TEXT 文本轨道} 关联的 {@link Format}。
   */
  default Format getOutputTextFormat(Format sourceFormat) {
    return sourceFormat;
  }
}