package androidx.media3.exoplayer.dash;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.dash.PlayerEmsgHandler.PlayerTrackEmsgHandler;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.source.chunk.ChunkSource;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.LoaderErrorThrower;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;

/** 用于 DASH 流的 {@link ChunkSource}。 */
@UnstableApi
public interface DashChunkSource extends ChunkSource {

  /** {@link DashChunkSource} 的工厂接口。 */
  interface Factory {

    /**
     * 设置用于在提取过程中解析字幕的 {@link SubtitleParser.Factory}。默认的工厂值取决于具体实现。
     *
     * @param subtitleParserFactory 用于在提取过程中解析字幕的 {@link SubtitleParser.Factory}。
     * @return 返回此工厂，方便链式调用。
     */
    @CanIgnoreReturnValue
    default Factory setSubtitleParserFactory(SubtitleParser.Factory subtitleParserFactory) {
      return this;
    }

    /**
     * 设置字幕是在提取过程中（添加到样本队列之前）还是渲染过程中（从样本队列中取出时）解析。默认为 {@code false}（即在渲染过程中解析字幕）。
     *
     * <p>此方法是实验性的，在未来的版本中可能会被重命名或移除。
     *
     * @param parseSubtitlesDuringExtraction 是否在提取过程中解析字幕。
     * @return 返回此工厂，方便链式调用。
     */
    @CanIgnoreReturnValue
    default Factory experimentalParseSubtitlesDuringExtraction(
        boolean parseSubtitlesDuringExtraction) {
      return this;
    }

    /**
     * @param manifestLoaderErrorThrower 抛出影响清单加载的错误。
     * @param manifest 初始清单。
     * @param baseUrlExclusionList 基础 URL 排除列表。
     * @param periodIndex 清单中对应周期的索引。
     * @param adaptationSetIndices 周期中对应自适应集的索引。
     * @param trackSelection 轨道选择。
     * @param trackType {@link C.TrackType 轨道类型}。
     * @param elapsedRealtimeOffsetMs 如果已知，服务器端 Unix 时间与 {@link SystemClock#elapsedRealtime()} 之间的瞬时差异估计值（以毫秒为单位），
     *     指定为服务器的 Unix 时间减去本地运行时间。如果未知，则为 {@link C#TIME_UNSET}。
     * @param enableEventMessageTrack 是否输出事件消息轨道。
     * @param closedCaptionFormats 要输出的闭路字幕轨道的 {@link Format 格式}。
     * @param playerEmsgHandler 写入 emsg 消息的轨道输出，如果不应写入 emsg，则为 null。
     * @param transferListener 应通知任何数据传输的传输监听器。如果没有可用的监听器，则为 null。
     * @param playerId 使用此块源的播放器的 {@link PlayerId}。
     * @param cmcdConfiguration 此块源的 {@link CmcdConfiguration}。
     * @return 创建的 {@link DashChunkSource}。
     */
    DashChunkSource createDashChunkSource(
        LoaderErrorThrower manifestLoaderErrorThrower,
        DashManifest manifest,
        BaseUrlExclusionList baseUrlExclusionList,
        int periodIndex,
        int[] adaptationSetIndices,
        ExoTrackSelection trackSelection,
        @C.TrackType int trackType,
        long elapsedRealtimeOffsetMs,
        boolean enableEventMessageTrack,
        List<Format> closedCaptionFormats,
        @Nullable PlayerTrackEmsgHandler playerEmsgHandler,
        @Nullable TransferListener transferListener,
        PlayerId playerId,
        @Nullable CmcdConfiguration cmcdConfiguration);

    /**
     * 返回从 {@code sourceFormat} 中发出的 {@linkplain C#TRACK_TYPE_TEXT 文本样本} 的输出 {@link Format}。
     *
     * <p>在许多情况下，当 {@link Extractor} 从源中发出未经修改的样本时，此方法直接返回 {@code sourceFormat}。
     * 在其他情况下，例如当 {@link Extractor} 将字幕从 {@code sourceFormat} 转码为 {@link MimeTypes#APPLICATION_MEDIA3_CUES} 时，
     * 格式会更新以指示正在进行的转码。
     *
     * <p>非文本源格式始终返回未经修改。
     *
     * @param sourceFormat 原始的基于文本的格式。
     * @return 与 {@linkplain C#TRACK_TYPE_TEXT 文本轨道} 关联的 {@link Format}。
     */
    default Format getOutputTextFormat(Format sourceFormat) {
      return sourceFormat;
    }
  }

  /**
   * 更新清单。
   *
   * @param newManifest 新的清单。
   * @param newPeriodIndex {@code newManifest} 所覆盖的周期的索引。
   */
  void updateManifest(DashManifest newManifest, int newPeriodIndex);

  /**
   * 更新轨道选择。
   *
   * @param trackSelection 新的轨道选择实例。必须与之前的实例等效。
   */
  void updateTrackSelection(ExoTrackSelection trackSelection);
}