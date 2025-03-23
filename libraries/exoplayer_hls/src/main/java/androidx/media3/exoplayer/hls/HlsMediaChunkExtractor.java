package androidx.media3.exoplayer.hls;

import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import java.io.IOException;

/** 从 {@link HlsMediaChunk} 中提取样本和轨道 {@link Format}。 */
@UnstableApi
public interface HlsMediaChunkExtractor {

  /**
   * 使用 {@link ExtractorOutput} 初始化提取器。最多调用一次。
   *
   * @param extractorOutput 用于接收提取数据的 {@link ExtractorOutput}。
   */
  void init(ExtractorOutput extractorOutput);

  /**
   * 从提供的 {@link ExtractorInput} 中提取数据。在调用 {@link #init(ExtractorOutput)} 之前不得调用此方法。
   *
   * <p>此方法的单次调用将阻塞直到取得一些进展，但不会阻塞更长时间。因此，每次调用只会消耗少量输入数据。
   *
   * <p>当此方法抛出 {@link IOException} 时，可以通过提供 {@link ExtractorInput#getPosition() 读取位置} 未更改的 {@link ExtractorInput} 继续提取，并在后续调用此方法时继续提取。
   *
   * @param extractorInput 要从中读取的输入。
   * @return 是否还有数据需要提取。如果已到达输入末尾，则返回 false。
   * @throws IOException 如果从输入读取或解析时发生错误。
   */
  boolean read(ExtractorInput extractorInput) throws IOException;

  /** 返回这是否是 RFC 8216 第 3.4 节中定义的打包音频提取器。 */
  boolean isPackedAudioExtractor();

  /** 返回此实例是否可用于提取多个连续片段。 */
  boolean isReusable();

  /**
   * 返回一个用于提取与此实例相同类型媒体的新实例。只能在不可 {@link #isReusable() 重用} 的实例上调用。
   */
  HlsMediaChunkExtractor recreate();

  /**
   * 重置样本解析状态。
   *
   * <p>重置解析状态允许支持 Fragmented MP4 EXT-X-I-FRAME-STREAM-INF 片段。EXT-X-I-FRAME-STREAM-INF 片段被截断为仅包含前导关键帧。在解析该关键帧后，提取器可能会遇到意外的文件结尾。通过重置其状态，我们可以继续将后续片段中的样本提供给提取器。有关上下文，请参阅 <a href="https://github.com/google/ExoPlayer/issues/7512">#7512</a>。
   */
  void onTruncatedSegmentParsed();
}