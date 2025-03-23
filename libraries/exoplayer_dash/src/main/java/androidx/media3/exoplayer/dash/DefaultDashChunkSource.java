/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.exoplayer.dash;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.SystemClock;
import android.util.Pair;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.UriUtil;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.dash.PlayerEmsgHandler.PlayerTrackEmsgHandler;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.BaseUrl;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.RangedUri;
import androidx.media3.exoplayer.dash.manifest.Representation;
import androidx.media3.exoplayer.source.BehindLiveWindowException;
import androidx.media3.exoplayer.source.chunk.BaseMediaChunkIterator;
import androidx.media3.exoplayer.source.chunk.BundledChunkExtractor;
import androidx.media3.exoplayer.source.chunk.Chunk;
import androidx.media3.exoplayer.source.chunk.ChunkExtractor;
import androidx.media3.exoplayer.source.chunk.ChunkHolder;
import androidx.media3.exoplayer.source.chunk.ContainerMediaChunk;
import androidx.media3.exoplayer.source.chunk.InitializationChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator;
import androidx.media3.exoplayer.source.chunk.SingleSampleMediaChunk;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.CmcdData;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoaderErrorThrower;
import androidx.media3.extractor.ChunkIndex;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A default {@link DashChunkSource} implementation. */
@UnstableApi
public class DefaultDashChunkSource implements DashChunkSource {

  /** {@link DashChunkSource.Factory} for {@link DefaultDashChunkSource} instances. */
  public static final class Factory implements DashChunkSource.Factory {

    private final DataSource.Factory dataSourceFactory;
    private final int maxSegmentsPerLoad;
    private final ChunkExtractor.Factory chunkExtractorFactory;

    /**
     * 等价于 {@link #Factory(ChunkExtractor.Factory, DataSource.Factory, int) new
     * Factory(BundledChunkExtractor.FACTORY, dataSourceFactory, maxSegmentsPerLoad = 1)}。
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(dataSourceFactory, /* maxSegmentsPerLoad= */ 1);
    }

    /**
     * 等价于 {@link #Factory(ChunkExtractor.Factory, DataSource.Factory, int) new
     * Factory(BundledChunkExtractor.FACTORY, dataSourceFactory, maxSegmentsPerLoad)}。
     */
    public Factory(DataSource.Factory dataSourceFactory, int maxSegmentsPerLoad) {
      this(BundledChunkExtractor.FACTORY, dataSourceFactory, maxSegmentsPerLoad);
    }

    /**
     * 创建一个新的实例。
     *
     * @param chunkExtractorFactory 用于创建 {@link ChunkExtractor} 实例，以提取块。
     * @param dataSourceFactory 用于创建 {@link DataSource} 实例，以下载块。
     * @param maxSegmentsPerLoad 参见 {@link DefaultDashChunkSource#DefaultDashChunkSource}。
     */
    public Factory(
        ChunkExtractor.Factory chunkExtractorFactory,
        DataSource.Factory dataSourceFactory,
        int maxSegmentsPerLoad) {
      this.chunkExtractorFactory = chunkExtractorFactory;
      this.dataSourceFactory = dataSourceFactory;
      this.maxSegmentsPerLoad = maxSegmentsPerLoad;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory setSubtitleParserFactory(SubtitleParser.Factory subtitleParserFactory) {
      chunkExtractorFactory.setSubtitleParserFactory(subtitleParserFactory);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory experimentalParseSubtitlesDuringExtraction(
        boolean parseSubtitlesDuringExtraction) {
      chunkExtractorFactory.experimentalParseSubtitlesDuringExtraction(
          parseSubtitlesDuringExtraction);
      return this;
    }

    @Override
    public DashChunkSource createDashChunkSource(
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
        @Nullable CmcdConfiguration cmcdConfiguration) {
      DataSource dataSource = dataSourceFactory.createDataSource();
      if (transferListener != null) {
        dataSource.addTransferListener(transferListener);
      }
      return new DefaultDashChunkSource(
          chunkExtractorFactory,
          manifestLoaderErrorThrower,
          manifest,
          baseUrlExclusionList,
          periodIndex,
          adaptationSetIndices,
          trackSelection,
          trackType,
          dataSource,
          elapsedRealtimeOffsetMs,
          maxSegmentsPerLoad,
          enableEventMessageTrack,
          closedCaptionFormats,
          playerEmsgHandler,
          playerId,
          cmcdConfiguration);
    }

    /**
     * {@inheritDoc}
     *
     * <p>此实现将输出格式的确定委托给传递给此类的构造函数的 {@link ChunkExtractor.Factory}。
     */
    @Override
    public Format getOutputTextFormat(Format sourceFormat) {
      return chunkExtractorFactory.getOutputTextFormat(sourceFormat);
    }
  }

  private final LoaderErrorThrower manifestLoaderErrorThrower;
  private final BaseUrlExclusionList baseUrlExclusionList;
  private final int[] adaptationSetIndices;
  private final @C.TrackType int trackType;
  private final DataSource dataSource;
  private final long elapsedRealtimeOffsetMs;
  private final int maxSegmentsPerLoad;
  @Nullable private final PlayerTrackEmsgHandler playerTrackEmsgHandler;
  @Nullable private final CmcdConfiguration cmcdConfiguration;

  protected final RepresentationHolder[] representationHolders;

  private ExoTrackSelection trackSelection;
  private DashManifest manifest;
  private int periodIndex;
  @Nullable private IOException fatalError;
  private boolean missingLastSegment;

  /**
   * 上次调用 {@link #getNextChunk(LoadingInfo, long, List, ChunkHolder)} 方法的时间，
   * 以 {@link SystemClock#elapsedRealtime} 测量。
   */
  private long lastChunkRequestRealtimeMs;

  /**
   * @param chunkExtractorFactory 用于创建 {@link ChunkExtractor} 实例，以提取块。
   * @param manifestLoaderErrorThrower 抛出影响清单加载的错误。
   * @param manifest 初始清单。
   * @param baseUrlExclusionList 基础 URL 排除列表。
   * @param periodIndex 清单中周期的索引。
   * @param adaptationSetIndices 周期中自适应集的索引。
   * @param trackSelection 轨道选择。
   * @param trackType 轨道选择中的 {@link C.TrackType 类型}。
   * @param dataSource 用于加载媒体数据的 {@link DataSource}。
   * @param elapsedRealtimeOffsetMs 如果已知，服务器端 Unix 时间与 {@link SystemClock#elapsedRealtime()} 之间的瞬时差异估计值（以毫秒为单位），
   *     指定为服务器的 Unix 时间减去本地运行时间。如果未知，则为 {@link C#TIME_UNSET}。
   * @param maxSegmentsPerLoad 单个请求中组合的最大段数。注意，段仅在它们的 {@link Uri} 相同且数据范围相邻时才会被组合。
   * @param enableEventMessageTrack 是否输出事件消息轨道。
   * @param closedCaptionFormats 要输出的封闭字幕轨道的 {@link Format 格式}。
   * @param playerTrackEmsgHandler 用于处理针对播放器的 emsg 消息的 {@link PlayerTrackEmsgHandler} 实例。
   *     如果不需要，则可能为 null。
   * @param playerId 使用此块源的播放器的 {@link PlayerId}。
   * @param cmcdConfiguration 此块源的 {@link CmcdConfiguration}。
   */
  public DefaultDashChunkSource(
      ChunkExtractor.Factory chunkExtractorFactory,
      LoaderErrorThrower manifestLoaderErrorThrower,
      DashManifest manifest,
      BaseUrlExclusionList baseUrlExclusionList,
      int periodIndex,
      int[] adaptationSetIndices,
      ExoTrackSelection trackSelection,
      @C.TrackType int trackType,
      DataSource dataSource,
      long elapsedRealtimeOffsetMs,
      int maxSegmentsPerLoad,
      boolean enableEventMessageTrack,
      List<Format> closedCaptionFormats,
      @Nullable PlayerTrackEmsgHandler playerTrackEmsgHandler,
      PlayerId playerId,
      @Nullable CmcdConfiguration cmcdConfiguration) {
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.manifest = manifest;
    this.baseUrlExclusionList = baseUrlExclusionList;
    this.adaptationSetIndices = adaptationSetIndices;
    this.trackSelection = trackSelection;
    this.trackType = trackType;
    this.dataSource = dataSource;
    this.periodIndex = periodIndex;
    this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
    this.maxSegmentsPerLoad = maxSegmentsPerLoad;
    this.playerTrackEmsgHandler = playerTrackEmsgHandler;
    this.cmcdConfiguration = cmcdConfiguration;
    this.lastChunkRequestRealtimeMs = C.TIME_UNSET;

    long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);

    List<Representation> representations = getRepresentations();
    representationHolders = new RepresentationHolder[trackSelection.length()];
    for (int i = 0; i < representationHolders.length; i++) {
      Representation representation = representations.get(trackSelection.getIndexInTrackGroup(i));
      @Nullable
      BaseUrl selectedBaseUrl = baseUrlExclusionList.selectBaseUrl(representation.baseUrls);
      representationHolders[i] =
          new RepresentationHolder(
              periodDurationUs,
              representation,
              selectedBaseUrl != null ? selectedBaseUrl : representation.baseUrls.get(0),
              chunkExtractorFactory.createProgressiveMediaExtractor(
                  trackType,
                  representation.format,
                  enableEventMessageTrack,
                  closedCaptionFormats,
                  playerTrackEmsgHandler,
                  playerId),
              /* segmentNumShift= */ 0,
              representation.getIndex());
    }
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    // 段在表示之间是对齐的，因此可以使用任何段索引。
    for (RepresentationHolder representationHolder : representationHolders) {
      if (representationHolder.segmentIndex != null) {
        long segmentCount = representationHolder.getSegmentCount();
        if (segmentCount == 0) {
          continue;
        }
        long segmentNum = representationHolder.getSegmentNum(positionUs);
        long firstSyncUs = representationHolder.getSegmentStartTimeUs(segmentNum);
        long secondSyncUs =
            firstSyncUs < positionUs
                    && (segmentCount == DashSegmentIndex.INDEX_UNBOUNDED
                        || segmentNum
                            < representationHolder.getFirstSegmentNum() + segmentCount - 1)
                ? representationHolder.getSegmentStartTimeUs(segmentNum + 1)
                : firstSyncUs;
        return seekParameters.resolveSeekPositionUs(positionUs, firstSyncUs, secondSyncUs);
      }
    }
    // 我们还没有段索引来调整搜索位置。
    return positionUs;
  }

  @Override
  public void updateManifest(DashManifest newManifest, int newPeriodIndex) {
    try {
      manifest = newManifest;
      periodIndex = newPeriodIndex;
      long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
      List<Representation> representations = getRepresentations();
      for (int i = 0; i < representationHolders.length; i++) {
        Representation representation = representations.get(trackSelection.getIndexInTrackGroup(i));
        representationHolders[i] =
            representationHolders[i].copyWithNewRepresentation(periodDurationUs, representation);
      }
    } catch (BehindLiveWindowException e) {
      fatalError = e;
    }
  }

  @Override
  public void updateTrackSelection(ExoTrackSelection trackSelection) {
    this.trackSelection = trackSelection;
  }

  @Override
  public void maybeThrowError() throws IOException {
    if (fatalError != null) {
      throw fatalError;
    } else {
      manifestLoaderErrorThrower.maybeThrowError();
    }
  }

  @Override
  public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
    if (fatalError != null || trackSelection.length() < 2) {
      return queue.size();
    }
    return trackSelection.evaluateQueueSize(playbackPositionUs, queue);
  }

  @Override
  public boolean shouldCancelLoad(
      long playbackPositionUs, Chunk loadingChunk, List<? extends MediaChunk> queue) {
    if (fatalError != null) {
      return false;
    }
    return trackSelection.shouldCancelChunkLoad(playbackPositionUs, loadingChunk, queue);
  }

  @Override
  public void getNextChunk(
      LoadingInfo loadingInfo,
      long loadPositionUs,
      List<? extends MediaChunk> queue,
      ChunkHolder out) {
    if (fatalError != null) {
      return;
    }

    long playbackPositionUs = loadingInfo.playbackPositionUs;
    long bufferedDurationUs = loadPositionUs - playbackPositionUs;
    long presentationPositionUs =
        Util.msToUs(manifest.availabilityStartTimeMs)
            + Util.msToUs(manifest.getPeriod(periodIndex).startMs)
            + loadPositionUs;

    if (playerTrackEmsgHandler != null
        && playerTrackEmsgHandler.maybeRefreshManifestBeforeLoadingNextChunk(
            presentationPositionUs)) {
      return;
    }

    long nowUnixTimeUs = Util.msToUs(Util.getNowUnixTimeMs(elapsedRealtimeOffsetMs));
    long nowPeriodTimeUs = getNowPeriodTimeUs(nowUnixTimeUs);
    MediaChunk previous = queue.isEmpty() ? null : queue.get(queue.size() - 1);
    MediaChunkIterator[] chunkIterators = new MediaChunkIterator[trackSelection.length()];
    for (int i = 0; i < chunkIterators.length; i++) {
      RepresentationHolder representationHolder = representationHolders[i];
      if (representationHolder.segmentIndex == null) {
        chunkIterators[i] = MediaChunkIterator.EMPTY;
      } else {
        long firstAvailableSegmentNum =
            representationHolder.getFirstAvailableSegmentNum(nowUnixTimeUs);
        long lastAvailableSegmentNum =
            representationHolder.getLastAvailableSegmentNum(nowUnixTimeUs);
        long segmentNum =
            getSegmentNum(
                representationHolder,
                previous,
                loadPositionUs,
                firstAvailableSegmentNum,
                lastAvailableSegmentNum);
        if (segmentNum < firstAvailableSegmentNum) {
          chunkIterators[i] = MediaChunkIterator.EMPTY;
        } else {
          representationHolder = updateSelectedBaseUrl(/* trackIndex= */ i);
          chunkIterators[i] =
              new RepresentationSegmentIterator(
                  representationHolder, segmentNum, lastAvailableSegmentNum, nowPeriodTimeUs);
        }
      }
    }

    long availableLiveDurationUs = getAvailableLiveDurationUs(nowUnixTimeUs, playbackPositionUs);
    trackSelection.updateSelectedTrack(
        playbackPositionUs, bufferedDurationUs, availableLiveDurationUs, queue, chunkIterators);
    int selectedTrackIndex = trackSelection.getSelectedIndex();

    @Nullable
    CmcdData.Factory cmcdDataFactory =
        cmcdConfiguration == null
            ? null
            : new CmcdData.Factory(
                cmcdConfiguration,
                trackSelection,
                max(0, bufferedDurationUs),
                /* playbackRate= */ loadingInfo.playbackSpeed,
                /* streamingFormat= */ CmcdData.Factory.STREAMING_FORMAT_DASH,
                /* isLive= */ manifest.dynamic,
                /* didRebuffer= */ loadingInfo.rebufferedSince(lastChunkRequestRealtimeMs),
                /* isBufferEmpty= */ queue.isEmpty());
    lastChunkRequestRealtimeMs = SystemClock.elapsedRealtime();

    RepresentationHolder representationHolder = updateSelectedBaseUrl(selectedTrackIndex);
    if (representationHolder.chunkExtractor != null) {
      Representation selectedRepresentation = representationHolder.representation;
      @Nullable RangedUri pendingInitializationUri = null;
      @Nullable RangedUri pendingIndexUri = null;
      if (representationHolder.chunkExtractor.getSampleFormats() == null) {
        pendingInitializationUri = selectedRepresentation.getInitializationUri();
      }
      if (representationHolder.segmentIndex == null) {
        pendingIndexUri = selectedRepresentation.getIndexUri();
      }
      if (pendingInitializationUri != null || pendingIndexUri != null) {
        // 我们有初始化请求和/或索引请求需要处理。
        out.chunk =
            newInitializationChunk(
                representationHolder,
                dataSource,
                trackSelection.getSelectedFormat(),
                trackSelection.getSelectionReason(),
                trackSelection.getSelectionData(),
                pendingInitializationUri,
                pendingIndexUri,
                cmcdDataFactory);
        return;
      }
    }

    long periodDurationUs = representationHolder.periodDurationUs;
    boolean isLastPeriodInDynamicManifest =
        manifest.dynamic && periodIndex == manifest.getPeriodCount() - 1;
    boolean periodEnded = !isLastPeriodInDynamicManifest || periodDurationUs != C.TIME_UNSET;

    if (representationHolder.getSegmentCount() == 0) {
      // 索引没有定义任何段。
      out.endOfStream = periodEnded;
      return;
    }

    long firstAvailableSegmentNum = representationHolder.getFirstAvailableSegmentNum(nowUnixTimeUs);
    long lastAvailableSegmentNum = representationHolder.getLastAvailableSegmentNum(nowUnixTimeUs);
    if (isLastPeriodInDynamicManifest) {
      long lastAvailableSegmentEndTimeUs =
          representationHolder.getSegmentEndTimeUs(lastAvailableSegmentNum);
      long lastSegmentDurationUs =
          lastAvailableSegmentEndTimeUs
              - representationHolder.getSegmentStartTimeUs(lastAvailableSegmentNum);
      // 通过假设在整体持续时间内无法再容纳完整的样本，来考虑整体周期持续时间值的一些不准确性。
      periodEnded &= (lastAvailableSegmentEndTimeUs + lastSegmentDurationUs >= periodDurationUs);
    }
    long segmentNum =
        getSegmentNum(
            representationHolder,
            previous,
            loadPositionUs,
            firstAvailableSegmentNum,
            lastAvailableSegmentNum);
    if (segmentNum < firstAvailableSegmentNum) {
      // 这早于当前清单中的第一个块。
      fatalError = new BehindLiveWindowException();
      return;
    }

    if (segmentNum > lastAvailableSegmentNum
        || (missingLastSegment && segmentNum >= lastAvailableSegmentNum)) {
      // 段超出了周期的结束位置。
      out.endOfStream = periodEnded;
      return;
    }

    if (periodEnded && representationHolder.getSegmentStartTimeUs(segmentNum) >= periodDurationUs) {
      // 周期持续时间将周期剪裁到段之前的位置。
      out.endOfStream = true;
      return;
    }

    int maxSegmentCount = (int) min(maxSegmentsPerLoad, lastAvailableSegmentNum - segmentNum + 1);
    if (periodDurationUs != C.TIME_UNSET) {
      while (maxSegmentCount > 1
          && representationHolder.getSegmentStartTimeUs(segmentNum + maxSegmentCount - 1)
          >= periodDurationUs) {
        // 周期持续时间将周期剪裁到范围 [segmentNum, segmentNum + maxSegmentCount - 1] 中最后一个段之前的位置。
        // 减少 maxSegmentCount。
        maxSegmentCount--;
      }
    }

    long seekTimeUs = queue.isEmpty() ? loadPositionUs : C.TIME_UNSET;
    out.chunk =
        newMediaChunk(
            representationHolder,
            dataSource,
            trackType,
            trackSelection.getSelectedFormat(),
            trackSelection.getSelectionReason(),
            trackSelection.getSelectionData(),
            segmentNum,
            maxSegmentCount,
            seekTimeUs,
            nowPeriodTimeUs,
            cmcdDataFactory);
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof InitializationChunk) {
      InitializationChunk initializationChunk = (InitializationChunk) chunk;
      int trackIndex = trackSelection.indexOf(initializationChunk.trackFormat);
      RepresentationHolder representationHolder = representationHolders[trackIndex];
      // 空值检查可以避免用从流中获取的索引覆盖从清单中获取的索引。
      // 如果清单中定义了索引，那么流中不应该再定义索引，但在某些情况下，如果流中定义了索引，我们应该忽略它。
      if (representationHolder.segmentIndex == null) {
        @Nullable
        ChunkIndex chunkIndex =
            checkStateNotNull(representationHolder.chunkExtractor).getChunkIndex();
        if (chunkIndex != null) {
          representationHolders[trackIndex] =
              representationHolder.copyWithNewSegmentIndex(
                  new DashWrappingSegmentIndex(
                      chunkIndex, representationHolder.representation.presentationTimeOffsetUs));
        }
      }
    }
    if (playerTrackEmsgHandler != null) {
      playerTrackEmsgHandler.onChunkLoadCompleted(chunk);
    }
  }

  @Override
  public boolean onChunkLoadError(
      Chunk chunk,
      boolean cancelable,
      LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    if (!cancelable) {
      return false;
    }
    if (playerTrackEmsgHandler != null && playerTrackEmsgHandler.onChunkLoadError(chunk)) {
      return true;
    }
    // 针对时间段末尾缺少片段的临时解决方案
    if (!manifest.dynamic
        && chunk instanceof MediaChunk
        && loadErrorInfo.exception instanceof InvalidResponseCodeException
        && ((InvalidResponseCodeException) loadErrorInfo.exception).responseCode == 404) {
      RepresentationHolder representationHolder =
          representationHolders[trackSelection.indexOf(chunk.trackFormat)];
      long segmentCount = representationHolder.getSegmentCount();
      if (segmentCount != DashSegmentIndex.INDEX_UNBOUNDED && segmentCount != 0) {
        long lastAvailableSegmentNum = representationHolder.getFirstSegmentNum() + segmentCount - 1;
        if (((MediaChunk) chunk).getNextChunkIndex() > lastAvailableSegmentNum) {
          missingLastSegment = true;
          return true;
        }
      }
    }

    int trackIndex = trackSelection.indexOf(chunk.trackFormat);
    RepresentationHolder representationHolder = representationHolders[trackIndex];
    @Nullable
    BaseUrl newBaseUrl =
        baseUrlExclusionList.selectBaseUrl(representationHolder.representation.baseUrls);
    if (newBaseUrl != null && !representationHolder.selectedBaseUrl.equals(newBaseUrl)) {
      // 自创建失败的分片以来，基础 URL 已发生变化。请求一个替换分片，它将使用新的基础 URL。
      return true;
    }

    LoadErrorHandlingPolicy.FallbackOptions fallbackOptions =
        createFallbackOptions(trackSelection, representationHolder.representation.baseUrls);
    if (!fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK)
        && !fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION)) {
      return false;
    }
    @Nullable
    LoadErrorHandlingPolicy.FallbackSelection fallbackSelection =
        loadErrorHandlingPolicy.getFallbackSelectionFor(fallbackOptions, loadErrorInfo);
    if (fallbackSelection == null || !fallbackOptions.isFallbackAvailable(fallbackSelection.type)) {
      // 策略指示不使用任何回退方案，或使用了不可用的回退类型。
      return false;
    }

    boolean cancelLoad = false;
    if (fallbackSelection.type == LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK) {
      cancelLoad =
          trackSelection.excludeTrack(
              trackSelection.indexOf(chunk.trackFormat), fallbackSelection.exclusionDurationMs);
    } else if (fallbackSelection.type == LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION) {
      baseUrlExclusionList.exclude(
          representationHolder.selectedBaseUrl, fallbackSelection.exclusionDurationMs);
      cancelLoad = true;
    }
    return cancelLoad;
  }

  @Override
  public void release() {
    for (RepresentationHolder representationHolder : representationHolders) {
      @Nullable ChunkExtractor chunkExtractor = representationHolder.chunkExtractor;
      if (chunkExtractor != null) {
        chunkExtractor.release();
      }
    }
  }

  // Internal methods.

  private LoadErrorHandlingPolicy.FallbackOptions createFallbackOptions(
      ExoTrackSelection trackSelection, List<BaseUrl> baseUrls) {
    long nowMs = SystemClock.elapsedRealtime();
    int numberOfTracks = trackSelection.length();
    int numberOfExcludedTracks = 0;
    for (int i = 0; i < numberOfTracks; i++) {
      if (trackSelection.isTrackExcluded(i, nowMs)) {
        numberOfExcludedTracks++;
      }
    }
    int priorityCount = BaseUrlExclusionList.getPriorityCount(baseUrls);
    return new LoadErrorHandlingPolicy.FallbackOptions(
        /* numberOfLocations= */ priorityCount,
        /* numberOfExcludedLocations= */ priorityCount
            - baseUrlExclusionList.getPriorityCountAfterExclusion(baseUrls),
        numberOfTracks,
        numberOfExcludedTracks);
  }

  private long getSegmentNum(
      RepresentationHolder representationHolder,
      @Nullable MediaChunk previousChunk,
      long loadPositionUs,
      long firstAvailableSegmentNum,
      long lastAvailableSegmentNum) {
    return previousChunk != null
        ? previousChunk.getNextChunkIndex()
        : Util.constrainValue(
            representationHolder.getSegmentNum(loadPositionUs),
            firstAvailableSegmentNum,
            lastAvailableSegmentNum);
  }

  @RequiresNonNull({"manifest", "adaptationSetIndices"})
  private ArrayList<Representation> getRepresentations(
      @UnknownInitialization DefaultDashChunkSource this) {
    List<AdaptationSet> manifestAdaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
    ArrayList<Representation> representations = new ArrayList<>();
    for (int adaptationSetIndex : adaptationSetIndices) {
      representations.addAll(manifestAdaptationSets.get(adaptationSetIndex).representations);
    }
    return representations;
  }

  private long getAvailableLiveDurationUs(long nowUnixTimeUs, long playbackPositionUs) {
    if (!manifest.dynamic || representationHolders[0].getSegmentCount() == 0) {
      return C.TIME_UNSET;
    }
    long lastSegmentNum = representationHolders[0].getLastAvailableSegmentNum(nowUnixTimeUs);
    long lastSegmentEndTimeUs = representationHolders[0].getSegmentEndTimeUs(lastSegmentNum);
    long nowPeriodTimeUs = getNowPeriodTimeUs(nowUnixTimeUs);
    long availabilityEndTimeUs = min(nowPeriodTimeUs, lastSegmentEndTimeUs);
    return max(0, availabilityEndTimeUs - playbackPositionUs);
  }

  private long getNowPeriodTimeUs(long nowUnixTimeUs) {
    return manifest.availabilityStartTimeMs == C.TIME_UNSET
        ? C.TIME_UNSET
        : nowUnixTimeUs
            - Util.msToUs(
                manifest.availabilityStartTimeMs + manifest.getPeriod(periodIndex).startMs);
  }

  /**
   * 创建一个新的 {@link Chunk} 用于初始化。
   *
   * @param representationHolder 用于初始化的 {@link Representation} 持有者。
   * @param dataSource 用于加载数据的数据源。
   * @param trackFormat 该分片所属轨道的格式。
   * @param trackSelectionReason {@link C.SelectionReason} 选择原因之一。
   * @param trackSelectionData 与轨道选择相关的附加数据。
   * @param initializationUri 指向初始化数据的 URI。如果 {@code indexUri} 不为 {@code null}，则可以为 {@code null}。
   * @param indexUri 指向索引数据的 URI。如果 {@code initializationUri} 不为 {@code null}，则可以为 {@code null}。
   * @param cmcdDataFactory 用于生成 CMCD 数据的 {@link CmcdData.Factory}。
   */
  @RequiresNonNull("#1.chunkExtractor")
  protected Chunk newInitializationChunk(
      RepresentationHolder representationHolder,
      DataSource dataSource,
      Format trackFormat,
      @C.SelectionReason int trackSelectionReason,
      @Nullable Object trackSelectionData,
      @Nullable RangedUri initializationUri,
      @Nullable RangedUri indexUri,
      @Nullable CmcdData.Factory cmcdDataFactory) {
    Representation representation = representationHolder.representation;
    RangedUri requestUri;
    if (initializationUri != null) {
      // 初始化和索引数据通常存储在一起。尝试将这两个请求合并，以便一次性请求两者。
      requestUri =
          initializationUri.attemptMerge(indexUri, representationHolder.selectedBaseUrl.url);
      if (requestUri == null) {
        requestUri = initializationUri;
      }
    } else {
      requestUri = checkNotNull(indexUri);
    }
    DataSpec dataSpec =
        DashUtil.buildDataSpec(
            representation,
            representationHolder.selectedBaseUrl.url,
            requestUri,
            /* flags= */ 0,
            /* httpRequestHeaders= */ ImmutableMap.of());
    if (cmcdDataFactory != null) {
      CmcdData cmcdData =
          cmcdDataFactory.setObjectType(CmcdData.Factory.OBJECT_TYPE_INIT_SEGMENT).createCmcdData();
      dataSpec = cmcdData.addToDataSpec(dataSpec);
    }

    return new InitializationChunk(
        dataSource,
        dataSpec,
        trackFormat,
        trackSelectionReason,
        trackSelectionData,
        representationHolder.chunkExtractor);
  }

  // TODO: b/289983417 - 当不再支持旧版字幕解码时，停止使用已弃用的 SingleSampleMediaChunk。
  // is no longer supported.
  @SuppressWarnings("deprecation")
  protected Chunk newMediaChunk(
      RepresentationHolder representationHolder,
      DataSource dataSource,
      @C.TrackType int trackType,
      Format trackFormat,
      @C.SelectionReason int trackSelectionReason,
      @Nullable Object trackSelectionData,
      long firstSegmentNum,
      int maxSegmentCount,
      long seekTimeUs,
      long nowPeriodTimeUs,
      @Nullable CmcdData.Factory cmcdDataFactory) {
    Representation representation = representationHolder.representation;
    long startTimeUs = representationHolder.getSegmentStartTimeUs(firstSegmentNum);
    RangedUri segmentUri = representationHolder.getSegmentUrl(firstSegmentNum);
    if (representationHolder.chunkExtractor == null) {
      long endTimeUs = representationHolder.getSegmentEndTimeUs(firstSegmentNum);
      int flags =
          representationHolder.isSegmentAvailableAtFullNetworkSpeed(
                  firstSegmentNum, nowPeriodTimeUs)
              ? 0
              : DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED;
      DataSpec dataSpec =
          DashUtil.buildDataSpec(
              representation,
              representationHolder.selectedBaseUrl.url,
              segmentUri,
              flags,
              /* httpRequestHeaders= */ ImmutableMap.of());
      if (cmcdDataFactory != null) {
        cmcdDataFactory
            .setChunkDurationUs(endTimeUs - startTimeUs)
            .setObjectType(CmcdData.Factory.getObjectType(trackSelection));
        @Nullable
        Pair<String, String> nextObjectAndRangeRequest =
            getNextObjectAndRangeRequest(firstSegmentNum, segmentUri, representationHolder);
        if (nextObjectAndRangeRequest != null) {
          cmcdDataFactory
              .setNextObjectRequest(nextObjectAndRangeRequest.first)
              .setNextRangeRequest(nextObjectAndRangeRequest.second);
        }
        CmcdData cmcdData = cmcdDataFactory.createCmcdData();
        dataSpec = cmcdData.addToDataSpec(dataSpec);
      }

      return new SingleSampleMediaChunk(
          dataSource,
          dataSpec,
          trackFormat,
          trackSelectionReason,
          trackSelectionData,
          startTimeUs,
          endTimeUs,
          firstSegmentNum,
          trackType,
          trackFormat);
    } else {
      int segmentCount = 1;
      for (int i = 1; i < maxSegmentCount; i++) {
        RangedUri nextSegmentUri = representationHolder.getSegmentUrl(firstSegmentNum + i);
        @Nullable
        RangedUri mergedSegmentUri =
            segmentUri.attemptMerge(nextSegmentUri, representationHolder.selectedBaseUrl.url);
        if (mergedSegmentUri == null) {
          // 无法合并片段请求，因为 URI 无法合并。
          break;
        }
        segmentUri = mergedSegmentUri;
        segmentCount++;
      }
      long segmentNum = firstSegmentNum + segmentCount - 1;
      long endTimeUs = representationHolder.getSegmentEndTimeUs(segmentNum);
      long periodDurationUs = representationHolder.periodDurationUs;
      long clippedEndTimeUs =
          periodDurationUs != C.TIME_UNSET && periodDurationUs <= endTimeUs
              ? periodDurationUs
              : C.TIME_UNSET;
      int flags =
          representationHolder.isSegmentAvailableAtFullNetworkSpeed(segmentNum, nowPeriodTimeUs)
              ? 0
              : DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED;
      DataSpec dataSpec =
          DashUtil.buildDataSpec(
              representation,
              representationHolder.selectedBaseUrl.url,
              segmentUri,
              flags,
              /* httpRequestHeaders= */ ImmutableMap.of());
      if (cmcdDataFactory != null) {
        cmcdDataFactory
            .setChunkDurationUs(endTimeUs - startTimeUs)
            .setObjectType(CmcdData.Factory.getObjectType(trackSelection));
        @Nullable
        Pair<String, String> nextObjectAndRangeRequest =
            getNextObjectAndRangeRequest(firstSegmentNum, segmentUri, representationHolder);
        if (nextObjectAndRangeRequest != null) {
          cmcdDataFactory
              .setNextObjectRequest(nextObjectAndRangeRequest.first)
              .setNextRangeRequest(nextObjectAndRangeRequest.second);
        }
        CmcdData cmcdData = cmcdDataFactory.createCmcdData();
        dataSpec = cmcdData.addToDataSpec(dataSpec);
      }
      long sampleOffsetUs = -representation.presentationTimeOffsetUs;
      if (MimeTypes.isImage(trackFormat.sampleMimeType)) {
        sampleOffsetUs += startTimeUs;
      }
      return new ContainerMediaChunk(
          dataSource,
          dataSpec,
          trackFormat,
          trackSelectionReason,
          trackSelectionData,
          startTimeUs,
          endTimeUs,
          seekTimeUs,
          clippedEndTimeUs,
          firstSegmentNum,
          segmentCount,
          sampleOffsetUs,
          representationHolder.chunkExtractor);
    }
  }

  @Nullable
  private Pair<String, String> getNextObjectAndRangeRequest(
      long segmentNum, RangedUri segmentUri, RepresentationHolder representationHolder) {
    if (segmentNum + 1 >= representationHolder.getSegmentCount()) {
      return null;
    }
    RangedUri nextSegmentUri = representationHolder.getSegmentUrl(segmentNum + 1);
    Uri uri = segmentUri.resolveUri(representationHolder.selectedBaseUrl.url);
    Uri nextUri = nextSegmentUri.resolveUri(representationHolder.selectedBaseUrl.url);
    String nextObjectRequest = UriUtil.getRelativePath(uri, nextUri);

    String nextRangeRequest = nextSegmentUri.start + "-";
    if (nextSegmentUri.length != C.LENGTH_UNSET) {
      nextRangeRequest += (nextSegmentUri.start + nextSegmentUri.length);
    }
    return new Pair<>(nextObjectRequest, nextRangeRequest);
  }

  private RepresentationHolder updateSelectedBaseUrl(int trackIndex) {
    RepresentationHolder representationHolder = representationHolders[trackIndex];
    @Nullable
    BaseUrl selectedBaseUrl =
        baseUrlExclusionList.selectBaseUrl(representationHolder.representation.baseUrls);
    if (selectedBaseUrl != null && !selectedBaseUrl.equals(representationHolder.selectedBaseUrl)) {
      representationHolder = representationHolder.copyWithNewSelectedBaseUrl(selectedBaseUrl);
      representationHolders[trackIndex] = representationHolder;
    }
    return representationHolder;
  }

  // Protected classes.

  /** {@link MediaChunkIterator} wrapping a {@link RepresentationHolder}. */
  protected static final class RepresentationSegmentIterator extends BaseMediaChunkIterator {

    private final RepresentationHolder representationHolder;
    private final long nowPeriodTimeUs;

    /**
     * 创建迭代器。
     *
     * @param representation 要包装的 {@link RepresentationHolder}。
     * @param firstAvailableSegmentNum 第一个可用片段的编号。
     * @param lastAvailableSegmentNum 最后一个可用片段的编号。
     * @param nowPeriodTimeUs 以微秒为单位的当前时间，从周期开始计算，用于判断片段是否以全网络速度可用。
     */
    public RepresentationSegmentIterator(
        RepresentationHolder representation,
        long firstAvailableSegmentNum,
        long lastAvailableSegmentNum,
        long nowPeriodTimeUs) {
      super(/* fromIndex= */ firstAvailableSegmentNum, /* toIndex= */ lastAvailableSegmentNum);
      this.representationHolder = representation;
      this.nowPeriodTimeUs = nowPeriodTimeUs;
    }

    @Override
    public DataSpec getDataSpec() {
      checkInBounds();
      long currentIndex = getCurrentIndex();
      RangedUri segmentUri = representationHolder.getSegmentUrl(currentIndex);
      int flags =
          representationHolder.isSegmentAvailableAtFullNetworkSpeed(currentIndex, nowPeriodTimeUs)
              ? 0
              : DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED;
      return DashUtil.buildDataSpec(
          representationHolder.representation,
          representationHolder.selectedBaseUrl.url,
          segmentUri,
          flags,
          /* httpRequestHeaders= */ ImmutableMap.of());
    }

    @Override
    public long getChunkStartTimeUs() {
      checkInBounds();
      return representationHolder.getSegmentStartTimeUs(getCurrentIndex());
    }

    @Override
    public long getChunkEndTimeUs() {
      checkInBounds();
      return representationHolder.getSegmentEndTimeUs(getCurrentIndex());
    }
  }

  /** 保存关于单个 {@link Representation} 快照的信息。 */
  protected static final class RepresentationHolder {

    @Nullable /* package */ final ChunkExtractor chunkExtractor;

    public final Representation representation;
    public final BaseUrl selectedBaseUrl;
    @Nullable public final DashSegmentIndex segmentIndex;

    private final long periodDurationUs;
    private final long segmentNumShift;

    /* package */ RepresentationHolder(
        long periodDurationUs,
        Representation representation,
        BaseUrl selectedBaseUrl,
        @Nullable ChunkExtractor chunkExtractor,
        long segmentNumShift,
        @Nullable DashSegmentIndex segmentIndex) {
      this.periodDurationUs = periodDurationUs;
      this.representation = representation;
      this.selectedBaseUrl = selectedBaseUrl;
      this.segmentNumShift = segmentNumShift;
      this.chunkExtractor = chunkExtractor;
      this.segmentIndex = segmentIndex;
    }

    @CheckResult
    /* package */ RepresentationHolder copyWithNewRepresentation(
        long newPeriodDurationUs, Representation newRepresentation)
        throws BehindLiveWindowException {
      @Nullable DashSegmentIndex oldIndex = representation.getIndex();
      @Nullable DashSegmentIndex newIndex = newRepresentation.getIndex();

      if (oldIndex == null) {
        // Segment numbers cannot shift if the index isn't defined by the manifest.
        return new RepresentationHolder(
            newPeriodDurationUs,
            newRepresentation,
            selectedBaseUrl,
            chunkExtractor,
            segmentNumShift,
            oldIndex);
      }

      if (!oldIndex.isExplicit()) {
        // Segment numbers cannot shift if the index isn't explicit.
        return new RepresentationHolder(
            newPeriodDurationUs,
            newRepresentation,
            selectedBaseUrl,
            chunkExtractor,
            segmentNumShift,
            newIndex);
      }

      long oldIndexSegmentCount = oldIndex.getSegmentCount(newPeriodDurationUs);
      if (oldIndexSegmentCount == 0) {
        // Segment numbers cannot shift if the old index was empty.
        return new RepresentationHolder(
            newPeriodDurationUs,
            newRepresentation,
            selectedBaseUrl,
            chunkExtractor,
            segmentNumShift,
            newIndex);
      }

      checkStateNotNull(newIndex);

      long oldIndexFirstSegmentNum = oldIndex.getFirstSegmentNum();
      long oldIndexStartTimeUs = oldIndex.getTimeUs(oldIndexFirstSegmentNum);
      long oldIndexLastSegmentNum = oldIndexFirstSegmentNum + oldIndexSegmentCount - 1;
      long oldIndexEndTimeUs =
          oldIndex.getTimeUs(oldIndexLastSegmentNum)
              + oldIndex.getDurationUs(oldIndexLastSegmentNum, newPeriodDurationUs);
      long newIndexFirstSegmentNum = newIndex.getFirstSegmentNum();
      long newIndexStartTimeUs = newIndex.getTimeUs(newIndexFirstSegmentNum);
      long newSegmentNumShift = segmentNumShift;
      if (oldIndexEndTimeUs == newIndexStartTimeUs) {
        // 新索引从旧索引结束的位置继续，没有重叠。
        newSegmentNumShift += oldIndexLastSegmentNum + 1 - newIndexFirstSegmentNum;
      } else if (oldIndexEndTimeUs < newIndexStartTimeUs) {
        // 旧索引和新索引之间存在间隙，这意味着我们已经落后于直播窗口，无法继续。
        throw new BehindLiveWindowException();
      } else if (newIndexStartTimeUs < oldIndexStartTimeUs) {
        // 新索引与旧索引重叠（但其起始位置不包含在旧索引内）。这只能在索引开头添加了额外片段时发生。
        newSegmentNumShift -=
            newIndex.getSegmentNum(oldIndexStartTimeUs, newPeriodDurationUs)
                - oldIndexFirstSegmentNum;
      } else {
        // 新索引与旧索引重叠（且其起始位置包含在旧索引内）。
        newSegmentNumShift +=
            oldIndex.getSegmentNum(newIndexStartTimeUs, newPeriodDurationUs)
                - newIndexFirstSegmentNum;
      }
      return new RepresentationHolder(
          newPeriodDurationUs,
          newRepresentation,
          selectedBaseUrl,
          chunkExtractor,
          newSegmentNumShift,
          newIndex);
    }

    @CheckResult
    /* package */ RepresentationHolder copyWithNewSegmentIndex(DashSegmentIndex segmentIndex) {
      return new RepresentationHolder(
          periodDurationUs,
          representation,
          selectedBaseUrl,
          chunkExtractor,
          segmentNumShift,
          segmentIndex);
    }

    @CheckResult
    /* package */ RepresentationHolder copyWithNewSelectedBaseUrl(BaseUrl selectedBaseUrl) {
      return new RepresentationHolder(
          periodDurationUs,
          representation,
          selectedBaseUrl,
          chunkExtractor,
          segmentNumShift,
          segmentIndex);
    }

    public long getFirstSegmentNum() {
      return checkStateNotNull(segmentIndex).getFirstSegmentNum() + segmentNumShift;
    }

    public long getFirstAvailableSegmentNum(long nowUnixTimeUs) {
      return checkStateNotNull(segmentIndex)
              .getFirstAvailableSegmentNum(periodDurationUs, nowUnixTimeUs)
          + segmentNumShift;
    }

    public long getSegmentCount() {
      return checkStateNotNull(segmentIndex).getSegmentCount(periodDurationUs);
    }

    public long getSegmentStartTimeUs(long segmentNum) {
      return checkStateNotNull(segmentIndex).getTimeUs(segmentNum - segmentNumShift);
    }

    public long getSegmentEndTimeUs(long segmentNum) {
      return getSegmentStartTimeUs(segmentNum)
          + checkStateNotNull(segmentIndex)
              .getDurationUs(segmentNum - segmentNumShift, periodDurationUs);
    }

    public long getSegmentNum(long positionUs) {
      return checkStateNotNull(segmentIndex).getSegmentNum(positionUs, periodDurationUs)
          + segmentNumShift;
    }

    public RangedUri getSegmentUrl(long segmentNum) {
      return checkStateNotNull(segmentIndex).getSegmentUrl(segmentNum - segmentNumShift);
    }

    public long getLastAvailableSegmentNum(long nowUnixTimeUs) {
      return getFirstAvailableSegmentNum(nowUnixTimeUs)
          + checkStateNotNull(segmentIndex)
              .getAvailableSegmentCount(periodDurationUs, nowUnixTimeUs)
          - 1;
    }

    public boolean isSegmentAvailableAtFullNetworkSpeed(long segmentNum, long nowPeriodTimeUs) {
      if (checkStateNotNull(segmentIndex).isExplicit()) {
        // 我们不支持显式索引的片段可用性检查（内部参考：b/172894901）。
        // 因此，假设显式索引中的所有片段始终以全网络速度可用，即使它们的结束时间在未来。
        return true;
      }
      return nowPeriodTimeUs == C.TIME_UNSET || getSegmentEndTimeUs(segmentNum) <= nowPeriodTimeUs;
    }
  }
}
