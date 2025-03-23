package androidx.media3.exoplayer.hls;

import static androidx.media3.exoplayer.hls.HlsChunkSource.CHUNK_PUBLICATION_STATE_PRELOAD;
import static androidx.media3.exoplayer.hls.HlsChunkSource.CHUNK_PUBLICATION_STATE_PUBLISHED;
import static androidx.media3.exoplayer.hls.HlsChunkSource.CHUNK_PUBLICATION_STATE_REMOVED;
import static androidx.media3.exoplayer.trackselection.TrackSelectionUtil.createFallbackOptions;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.Handler;
import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.drm.DrmSession;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.source.SampleQueue;
import androidx.media3.exoplayer.source.SampleQueue.UpstreamFormatChangedListener;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.SampleStream.ReadFlags;
import androidx.media3.exoplayer.source.SequenceableLoader;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.source.chunk.Chunk;
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import androidx.media3.exoplayer.upstream.Loader;
import androidx.media3.exoplayer.upstream.Loader.LoadErrorAction;
import androidx.media3.extractor.DiscardingTrackOutput;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.metadata.emsg.EventMessage;
import androidx.media3.extractor.metadata.emsg.EventMessageDecoder;
import androidx.media3.extractor.metadata.id3.PrivFrame;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
/**
 * 加载从 {@link HlsChunkSource} 获取的 {@link HlsMediaChunk}，并提供 {@link SampleStream} 以消费加载的媒体。
 */
/* package */ final class HlsSampleStreamWrapper
    implements Loader.Callback<Chunk>,
    Loader.ReleaseCallback,
    SequenceableLoader,
    ExtractorOutput,
    UpstreamFormatChangedListener {

  /** 用于接收事件通知的回调接口。 */
  public interface Callback extends SequenceableLoader.Callback<HlsSampleStreamWrapper> {

    /**
     * 当包装器准备就绪时调用。
     *
     * <p>注意：此方法将在比调用 {@link #prepareWithMultivariantPlaylistInfo} 或 {@link #continuePreparing} 的 Handler 循环稍晚的循环中调用。
     */
    void onPrepared();

    /**
     * 当给定 URL 引用的播放列表发生变化，或需要刷新以检查提示的资源是否已发布或删除时，调用此方法以安排 {@link #continueLoading(LoadingInfo)} 调用。
     *
     * <p>注意：此方法将在比调用 {@link #onPlaylistUpdated()} 的 Handler 循环稍晚的循环中调用。
     */
    void onPlaylistRefreshRequired(Uri playlistUrl);
  }

  private static final String TAG = "HlsSampleStreamWrapper";

  public static final int SAMPLE_QUEUE_INDEX_PENDING = -1;
  public static final int SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL = -2;
  public static final int SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL = -3;

  private static final Set<Integer> MAPPABLE_TYPES =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_METADATA)));

  private final String uid;
  private final @C.TrackType int trackType;
  private final Callback callback;
  private final HlsChunkSource chunkSource;
  private final Allocator allocator;
  @Nullable private final Format muxedAudioFormat;
  private final DrmSessionManager drmSessionManager;
  private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final Loader loader;
  private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
  private final @HlsMediaSource.MetadataType int metadataType;
  private final HlsChunkSource.HlsChunkHolder nextChunkHolder;
  private final ArrayList<HlsMediaChunk> mediaChunks;
  private final List<HlsMediaChunk> readOnlyMediaChunks;
  // 使用 Runnable 而不是内联方法引用，以避免重复分配。
  private final Runnable maybeFinishPrepareRunnable;
  private final Runnable onTracksEndedRunnable;
  private final Handler handler;
  private final ArrayList<HlsSampleStream> hlsSampleStreams;
  private final Map<String, DrmInitData> overridingDrmInitData;

  @Nullable private Chunk loadingChunk;
  private HlsSampleQueue[] sampleQueues;
  private int[] sampleQueueTrackIds;
  private Set<Integer> sampleQueueMappingDoneByType;
  private SparseIntArray sampleQueueIndicesByType;
  private @MonotonicNonNull TrackOutput emsgUnwrappingTrackOutput;
  private int primarySampleQueueType;
  private int primarySampleQueueIndex;
  private boolean sampleQueuesBuilt;
  private boolean prepared;
  private int enabledTrackGroupCount;
  private @MonotonicNonNull Format upstreamTrackFormat;
  @Nullable private Format downstreamTrackFormat;
  private boolean released;

  // 在 HLS 中，轨道（tracks）的处理较为复杂。详情请参阅 buildTracksFromSampleStreams 的文档。
  // 按轨道索引（由此源暴露的轨道）。
  private @MonotonicNonNull TrackGroupArray trackGroups;
  private @MonotonicNonNull Set<TrackGroup> optionalTrackGroups;
  // Indexed by track group.
  private int @MonotonicNonNull [] trackGroupToSampleQueueIndex;
  private int primaryTrackGroupIndex;
  private boolean haveAudioVideoSampleQueues;
  private boolean[] sampleQueuesEnabledStates;
  private boolean[] sampleQueueIsAudioVideoFlags;

  private long lastSeekPositionUs;
  private long pendingResetPositionUs;
  private boolean pendingResetUpstreamFormats;
  private boolean seenFirstTrackSelection;
  private boolean loadingFinished;

  // 仅由加载线程访问。
  private boolean tracksEnded;
  private long sampleOffsetUs;
  @Nullable private DrmInitData drmInitData;
  @Nullable private HlsMediaChunk sourceChunk;

  /**
   * @param uid 此样本流包装器的唯一标识符。标识符在周期内必须唯一。
   * @param trackType {@link C.TrackType 轨道类型}。
   * @param callback 包装器的回调接口。
   * @param chunkSource 用于获取要加载的块的 {@link HlsChunkSource}。
   * @param overridingDrmInitData 按保护方案类型（即 {@link DrmInitData#schemeType}）键控的覆盖 {@link DrmInitData}。如果流具有 {@link DrmInitData} 并且使用了提供覆盖 {@link DrmInitData} 的保护方案类型，则流的 {@link DrmInitData} 将被覆盖。
   * @param allocator 用于获取媒体缓冲区分配的 {@link Allocator}。
   * @param positionUs 开始加载媒体的位置（以微秒为单位）。
   * @param muxedAudioFormat 由多变量播放列表定义的可选多路复用音频 {@link Format}。
   * @param drmSessionManager 用于获取 {@link DrmSession DrmSessions} 的 {@link DrmSessionManager}。
   * @param drmEventDispatcher 用于通知 {@link DrmSessionEventListener} 事件的调度器。
   * @param loadErrorHandlingPolicy {@link LoadErrorHandlingPolicy}。
   * @param mediaSourceEventDispatcher 用于通知 {@link MediaSourceEventListener} 事件的调度器。
   */
  public HlsSampleStreamWrapper(
      String uid,
      @C.TrackType int trackType,
      Callback callback,
      HlsChunkSource chunkSource,
      Map<String, DrmInitData> overridingDrmInitData,
      Allocator allocator,
      long positionUs,
      @Nullable Format muxedAudioFormat,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      @HlsMediaSource.MetadataType int metadataType) {
    this.uid = uid;
    this.trackType = trackType;
    this.callback = callback;
    this.chunkSource = chunkSource;
    this.overridingDrmInitData = overridingDrmInitData;
    this.allocator = allocator;
    this.muxedAudioFormat = muxedAudioFormat;
    this.drmSessionManager = drmSessionManager;
    this.drmEventDispatcher = drmEventDispatcher;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
    this.metadataType = metadataType;
    loader = new Loader("Loader:HlsSampleStreamWrapper");
    nextChunkHolder = new HlsChunkSource.HlsChunkHolder();
    sampleQueueTrackIds = new int[0];
    sampleQueueMappingDoneByType = new HashSet<>(MAPPABLE_TYPES.size());
    sampleQueueIndicesByType = new SparseIntArray(MAPPABLE_TYPES.size());
    sampleQueues = new HlsSampleQueue[0];
    sampleQueueIsAudioVideoFlags = new boolean[0];
    sampleQueuesEnabledStates = new boolean[0];
    mediaChunks = new ArrayList<>();
    readOnlyMediaChunks = Collections.unmodifiableList(mediaChunks);
    hlsSampleStreams = new ArrayList<>();
    // Suppressions are needed because `this` is not initialized here.
    @SuppressWarnings("nullness:methodref.receiver.bound")
    Runnable maybeFinishPrepareRunnable = this::maybeFinishPrepare;
    this.maybeFinishPrepareRunnable = maybeFinishPrepareRunnable;
    @SuppressWarnings("nullness:methodref.receiver.bound")
    Runnable onTracksEndedRunnable = this::onTracksEnded;
    this.onTracksEndedRunnable = onTracksEndedRunnable;
    handler = Util.createHandlerForCurrentLooper();
    lastSeekPositionUs = positionUs;
    pendingResetPositionUs = positionUs;
  }

  public void continuePreparing() {
    if (!prepared) {
      continueLoading(new LoadingInfo.Builder().setPlaybackPositionUs(lastSeekPositionUs).build());
    }
  }

  /**
   * 使用多变量播放列表信息准备样本流包装器。
   *
   * @param trackGroups 通过 {@link #getTrackGroups()} 暴露的 {@link TrackGroup TrackGroups}。
   * @param primaryTrackGroupIndex 自适应轨道组的索引。
   * @param optionalTrackGroupsIndices 任何 {@code trackGroups} 的索引，这些轨道组如果在媒体播放列表的片段中未找到，不应触发失败。
   */
  public void prepareWithMultivariantPlaylistInfo(
      TrackGroup[] trackGroups, int primaryTrackGroupIndex, int... optionalTrackGroupsIndices) {
    this.trackGroups = createTrackGroupArrayWithDrmInfo(trackGroups);
    optionalTrackGroups = new HashSet<>();
    for (int optionalTrackGroupIndex : optionalTrackGroupsIndices) {
      optionalTrackGroups.add(this.trackGroups.get(optionalTrackGroupIndex));
    }
    this.primaryTrackGroupIndex = primaryTrackGroupIndex;
    handler.post(callback::onPrepared);
    setIsPrepared();
  }

  public void maybeThrowPrepareError() throws IOException {
    maybeThrowError();
    if (loadingFinished && !prepared) {
      throw ParserException.createForMalformedContainer(
          "Loading finished before preparation is complete.", /* cause= */ null);
    }
  }

  public TrackGroupArray getTrackGroups() {
    assertIsPrepared();
    return trackGroups;
  }

  public int getPrimaryTrackGroupIndex() {
    return primaryTrackGroupIndex;
  }

  public int bindSampleQueueToSampleStream(int trackGroupIndex) {
    assertIsPrepared();
    Assertions.checkNotNull(trackGroupToSampleQueueIndex);

    int sampleQueueIndex = trackGroupToSampleQueueIndex[trackGroupIndex];
    if (sampleQueueIndex == C.INDEX_UNSET) {
      return optionalTrackGroups.contains(trackGroups.get(trackGroupIndex))
          ? SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL
          : SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL;
    }
    if (sampleQueuesEnabledStates[sampleQueueIndex]) {
      // This sample queue is already bound to a different sample stream.
      return SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL;
    }
    sampleQueuesEnabledStates[sampleQueueIndex] = true;
    return sampleQueueIndex;
  }

  public void unbindSampleQueue(int trackGroupIndex) {
    assertIsPrepared();
    Assertions.checkNotNull(trackGroupToSampleQueueIndex);
    int sampleQueueIndex = trackGroupToSampleQueueIndex[trackGroupIndex];
    Assertions.checkState(sampleQueuesEnabledStates[sampleQueueIndex]);
    sampleQueuesEnabledStates[sampleQueueIndex] = false;
  }

  /**
   * 当发生轨道选择时，由父 {@link HlsMediaPeriod} 调用。
   *
   * @param selections 渲染器的轨道选择。
   * @param mayRetainStreamFlags 指示每个选择的现有样本流是否可以保留的标志。{@code true} 值表示选择未更改，并且调用者不要求重新创建样本流。
   * @param streams 现有的样本流，将更新以反映提供的选择。
   * @param streamResetFlags 将更新以指示新的样本流，以及已保留但需要重置消费渲染器的样本流。
   * @param positionUs 当前的播放位置（以微秒为单位）。
   * @param forceReset 如果为 true，则强制执行重置（即，将执行跳转，但禁用缓冲区内的跳转）。
   * @return 此包装器是否要求父 {@link HlsMediaPeriod} 作为轨道选择的一部分执行跳转。
   */
  public boolean selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs,
      boolean forceReset) {
    assertIsPrepared();
    int oldEnabledTrackGroupCount = enabledTrackGroupCount;
    // 取消选择旧轨道。
    for (int i = 0; i < selections.length; i++) {
      HlsSampleStream stream = (HlsSampleStream) streams[i];
      if (stream != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        enabledTrackGroupCount--;
        stream.unbindSampleQueue();
        streams[i] = null;
      }
    }
    // 如果我们被强制重置，或者这是第一次选择到与开始准备时不同的位置，或者我们在之前禁用了所有轨道后进行选择，则始终需要跳转。
    boolean seekRequired =
        forceReset
            || (seenFirstTrackSelection
            ? oldEnabledTrackGroupCount == 0
            : positionUs != lastSeekPositionUs);
    // 获取旧的（即在下面的循环执行之前的当前）主轨道选择。新的主选择将等于旧选择，除非在循环中更改。
    ExoTrackSelection oldPrimaryTrackSelection = chunkSource.getTrackSelection();
    ExoTrackSelection primaryTrackSelection = oldPrimaryTrackSelection;
    // 选择新轨道。
    for (int i = 0; i < selections.length; i++) {
      ExoTrackSelection selection = selections[i];
      if (selection == null) {
        continue;
      }
      int trackGroupIndex = trackGroups.indexOf(selection.getTrackGroup());
      if (trackGroupIndex == primaryTrackGroupIndex) {
        primaryTrackSelection = selection;
        chunkSource.setTrackSelection(selection);
      }
      if (streams[i] == null) {
        enabledTrackGroupCount++;
        streams[i] = new HlsSampleStream(this, trackGroupIndex);
        streamResetFlags[i] = true;
        if (trackGroupToSampleQueueIndex != null) {
          ((HlsSampleStream) streams[i]).bindSampleQueue();
          // 如果仍有可能避免跳转，则尝试在样本队列内跳转。
          if (!seekRequired) {
            SampleQueue sampleQueue = sampleQueues[trackGroupToSampleQueueIndex[trackGroupIndex]];
            // 如果我们尚未读取任何样本（例如，对于第一次轨道选择），或者我们能够在样本队列中跳转到当前播放位置，则可以避免跳转。在所有其他情况下，需要跳转。
            seekRequired =
                sampleQueue.getReadIndex() != 0
                    && !sampleQueue.seekTo(positionUs, /* allowTimeBeyondBuffer= */ true);
          }
        }
      }
    }
    if (enabledTrackGroupCount == 0) {
      chunkSource.reset();
      downstreamTrackFormat = null;
      pendingResetUpstreamFormats = true;
      mediaChunks.clear();
      if (loader.isLoading()) {
        if (sampleQueuesBuilt) {
          // 尽可能同步丢弃数据。
          for (SampleQueue sampleQueue : sampleQueues) {
            sampleQueue.discardToEnd();
          }
        }
        loader.cancelLoading();
      } else {
        resetSampleQueues();
      }
    } else {
      if (!mediaChunks.isEmpty()
          && !Util.areEqual(primaryTrackSelection, oldPrimaryTrackSelection)) {
        // 主轨道选择已更改，并且我们已缓冲了媒体。缓冲的媒体可能需要被丢弃。
        boolean primarySampleQueueDirty = false;
        if (!seenFirstTrackSelection) {
          long bufferedDurationUs = positionUs < 0 ? -positionUs : 0;
          HlsMediaChunk lastMediaChunk = getLastMediaChunk();
          MediaChunkIterator[] mediaChunkIterators =
              chunkSource.createMediaChunkIterators(lastMediaChunk, positionUs);
          primaryTrackSelection.updateSelectedTrack(
              positionUs,
              bufferedDurationUs,
              C.TIME_UNSET,
              readOnlyMediaChunks,
              mediaChunkIterators);
          int chunkIndex = chunkSource.getTrackGroup().indexOf(lastMediaChunk.trackFormat);
          if (primaryTrackSelection.getSelectedIndexInTrackGroup() != chunkIndex) {
            // 这是第一次选择，并且准备期间加载的块与最初选择的格式不匹配。
            primarySampleQueueDirty = true;
          }
        } else {
          // 主样本队列包含为旧主轨道选择缓冲的媒体。
          primarySampleQueueDirty = true;
        }
        if (primarySampleQueueDirty) {
          forceReset = true;
          seekRequired = true;
          pendingResetUpstreamFormats = true;
        }
      }
      if (seekRequired) {
        seekToUs(positionUs, forceReset);
        // 由于跳转，我们需要重置所有流的渲染器。
        for (int i = 0; i < streams.length; i++) {
          if (streams[i] != null) {
            streamResetFlags[i] = true;
          }
        }
      }
    }

    updateSampleStreams(streams);
    seenFirstTrackSelection = true;
    return seekRequired;
  }

  public void discardBuffer(long positionUs, boolean toKeyframe) {
    if (!sampleQueuesBuilt || isPendingReset()) {
      return;
    }
    int sampleQueueCount = sampleQueues.length;
    for (int i = 0; i < sampleQueueCount; i++) {
      sampleQueues[i].discardTo(positionUs, toKeyframe, sampleQueuesEnabledStates[i]);
    }
  }

  /**
   * 尝试跳转到指定的微秒位置。
   *
   * @param positionUs 跳转位置（以微秒为单位）。
   * @param forceReset 如果为 true，则强制执行重置（即禁用缓冲区内的跳转）。
   * @return 包装器是否被重置，意味着包装的样本队列被重置。如果为 false，则执行了缓冲区内的跳转。
   */
  public boolean seekToUs(long positionUs, boolean forceReset) {
    lastSeekPositionUs = positionUs;
    if (isPendingReset()) {
      // 已经有一个待处理的重置。我们只需要更新其位置。
      pendingResetPositionUs = positionUs;
      return true;
    }

    // 检测跳转是否指向至少部分缓冲的块的起始位置。
    @Nullable HlsMediaChunk seekToMediaChunk = null;
    if (chunkSource.hasIndependentSegments()) {
      for (int i = 0; i < mediaChunks.size(); i++) {
        HlsMediaChunk mediaChunk = mediaChunks.get(i);
        long mediaChunkStartTimeUs = mediaChunk.startTimeUs;
        if (mediaChunkStartTimeUs == positionUs) {
          seekToMediaChunk = mediaChunk;
          break;
        }
      }
    }
    // 如果没有被强制重置，尝试在缓冲区内跳转。
    if (sampleQueuesBuilt && !forceReset && seekInsideBufferUs(positionUs, seekToMediaChunk)) {
      return false;
    }

    // 无法在缓冲区内跳转，因此需要重置。
    pendingResetPositionUs = positionUs;
    loadingFinished = false;
    mediaChunks.clear();
    if (loader.isLoading()) {
      if (sampleQueuesBuilt) {
        // 尽可能同步丢弃数据。
        for (SampleQueue sampleQueue : sampleQueues) {
          sampleQueue.discardToEnd();
        }
      }
      loader.cancelLoading();
    } else {
      loader.clearFatalError();
      resetSampleQueues();
    }
    return true;
  }

  /** Called when the playlist is updated. */
  public void onPlaylistUpdated() {
    if (mediaChunks.isEmpty()) {
      return;
    }
    HlsMediaChunk lastMediaChunk = Iterables.getLast(mediaChunks);
    @HlsChunkSource.ChunkPublicationState
    int chunkState = chunkSource.getChunkPublicationState(lastMediaChunk);
    if (chunkState == CHUNK_PUBLICATION_STATE_PUBLISHED) {
      lastMediaChunk.publish();
    } else if (chunkState == CHUNK_PUBLICATION_STATE_PRELOAD) {
      handler.post(() -> callback.onPlaylistRefreshRequired(lastMediaChunk.playlistUrl));
    } else if (chunkState == CHUNK_PUBLICATION_STATE_REMOVED
        && !loadingFinished
        && loader.isLoading()) {
      loader.cancelLoading();
    }
  }

  public void release() {
    if (prepared) {
      // 尽可能同步丢弃数据。我们仅在已准备的情况下执行此操作，因为否则 sampleQueues 可能仍在被加载线程修改。
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.preRelease();
      }
    }
    chunkSource.reset();
    loader.release(this);
    handler.removeCallbacksAndMessages(null);
    released = true;
    hlsSampleStreams.clear();
  }

  @Override
  public void onLoaderReleased() {
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueue.release();
    }
  }

  public void setIsPrimaryTimestampSource(boolean isPrimaryTimestampSource) {
    chunkSource.setIsPrimaryTimestampSource(isPrimaryTimestampSource);
  }

  /**
   * 当加载播放列表时遇到错误时调用。
   *
   * @param playlistUrl 加载时遇到错误的播放列表的 {@link Uri}。
   * @param loadErrorInfo 加载错误信息。
   * @param forceRetry 是否在不考虑排除的情况下强制重试。
   * @return 如果排除未遇到错误，则返回 true。否则返回 false。
   */
  public boolean onPlaylistError(Uri playlistUrl, LoadErrorInfo loadErrorInfo, boolean forceRetry) {
    if (!chunkSource.obtainsChunksForPlaylist(playlistUrl)) {
      // 如果块源不提供失败播放列表的块，则提前返回。
      return true;
    }
    long exclusionDurationMs = C.TIME_UNSET;
    if (!forceRetry) {
      @Nullable
      LoadErrorHandlingPolicy.FallbackSelection fallbackSelection =
          loadErrorHandlingPolicy.getFallbackSelectionFor(
              createFallbackOptions(chunkSource.getTrackSelection()), loadErrorInfo);
      if (fallbackSelection != null
          && fallbackSelection.type == LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK) {
        exclusionDurationMs = fallbackSelection.exclusionDurationMs;
      }
    }
    // 无论如何，我们必须调用 ChunkSource.onPlaylistError，以便块源有机会将播放列表标记为失败。
    return chunkSource.onPlaylistError(playlistUrl, exclusionDurationMs)
        && exclusionDurationMs != C.TIME_UNSET;
  }

  /** 返回主样本流是否为 {@link C#TRACK_TYPE_VIDEO}。 */
  public boolean isVideoSampleStream() {
    return primarySampleQueueType == C.TRACK_TYPE_VIDEO;
  }

  /**
   * 根据指定的 {@link SeekParameters} 调整跳转位置。
   *
   * @param positionUs 跳转位置（以微秒为单位）。
   * @param seekParameters 控制跳转方式的参数。
   * @return 调整后的跳转位置（以微秒为单位）。
   */
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return chunkSource.getAdjustedSeekPositionUs(positionUs, seekParameters);
  }

  // SampleStream implementation.

  public boolean isReady(int sampleQueueIndex) {
    return !isPendingReset() && sampleQueues[sampleQueueIndex].isReady(loadingFinished);
  }

  public void maybeThrowError(int sampleQueueIndex) throws IOException {
    maybeThrowError();
    sampleQueues[sampleQueueIndex].maybeThrowError();
  }

  public void maybeThrowError() throws IOException {
    loader.maybeThrowError();
    chunkSource.maybeThrowError();
  }

  public int readData(
      int sampleQueueIndex,
      FormatHolder formatHolder,
      DecoderInputBuffer buffer,
      @ReadFlags int readFlags) {
    if (isPendingReset()) {
      return C.RESULT_NOTHING_READ;
    }

    // TODO: 将其分为丢弃（在 discardBuffer 中）和格式更改（在这里和 skipData 中）步骤。
    if (!mediaChunks.isEmpty()) {
      int discardToMediaChunkIndex = 0;
      while (discardToMediaChunkIndex < mediaChunks.size() - 1
          && finishedReadingChunk(mediaChunks.get(discardToMediaChunkIndex))) {
        discardToMediaChunkIndex++;
      }
      Util.removeRange(mediaChunks, 0, discardToMediaChunkIndex);
      HlsMediaChunk currentChunk = mediaChunks.get(0);
      Format trackFormat = currentChunk.trackFormat;
      if (!trackFormat.equals(downstreamTrackFormat)) {
        mediaSourceEventDispatcher.downstreamFormatChanged(
            trackType,
            trackFormat,
            currentChunk.trackSelectionReason,
            currentChunk.trackSelectionData,
            currentChunk.startTimeUs);
      }
      downstreamTrackFormat = trackFormat;
    }

    if (!mediaChunks.isEmpty() && !mediaChunks.get(0).isPublished()) {
      // 在确定预加载块已永久发布之前，不要读取它们。
      return C.RESULT_NOTHING_READ;
    }

    int result =
        sampleQueues[sampleQueueIndex].read(formatHolder, buffer, readFlags, loadingFinished);
    if (result == C.RESULT_FORMAT_READ) {
      Format format = Assertions.checkNotNull(formatHolder.format);
      if (sampleQueueIndex == primarySampleQueueIndex) {
        // 使用轨道格式中的信息填充主样本格式。
        int chunkUid = Ints.checkedCast(sampleQueues[sampleQueueIndex].peekSourceId());
        int chunkIndex = 0;
        while (chunkIndex < mediaChunks.size() && mediaChunks.get(chunkIndex).uid != chunkUid) {
          chunkIndex++;
        }
        Format trackFormat =
            chunkIndex < mediaChunks.size()
                ? mediaChunks.get(chunkIndex).trackFormat
                : Assertions.checkNotNull(upstreamTrackFormat);
        format = format.withManifestFormatInfo(trackFormat);
      }
      formatHolder.format = format;
    }
    return result;
  }

  public int skipData(int sampleQueueIndex, long positionUs) {
    if (isPendingReset()) {
      return 0;
    }

    SampleQueue sampleQueue = sampleQueues[sampleQueueIndex];
    int skipCount = sampleQueue.getSkipCount(positionUs, loadingFinished);

    // 确保在确定预加载块已永久发布之前，不要跳过它们。
    @Nullable HlsMediaChunk lastChunk = Iterables.getLast(mediaChunks, /* defaultValue= */ null);
    if (lastChunk != null && !lastChunk.isPublished()) {
      int readIndex = sampleQueue.getReadIndex();
      int firstSampleIndex = lastChunk.getFirstSampleIndex(sampleQueueIndex);
      skipCount = min(skipCount, firstSampleIndex - readIndex);
    }

    sampleQueue.skip(skipCount);
    return skipCount;
  }

  // SequenceableLoader implementation

  @Override
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.TIME_END_OF_SOURCE;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long bufferedPositionUs = lastSeekPositionUs;
      HlsMediaChunk lastMediaChunk = getLastMediaChunk();
      HlsMediaChunk lastCompletedMediaChunk =
          lastMediaChunk.isLoadCompleted()
              ? lastMediaChunk
              : mediaChunks.size() > 1 ? mediaChunks.get(mediaChunks.size() - 2) : null;
      if (lastCompletedMediaChunk != null) {
        bufferedPositionUs = max(bufferedPositionUs, lastCompletedMediaChunk.endTimeUs);
      }
      if (sampleQueuesBuilt) {
        for (SampleQueue sampleQueue : sampleQueues) {
          bufferedPositionUs = max(bufferedPositionUs, sampleQueue.getLargestQueuedTimestampUs());
        }
      }
      return bufferedPositionUs;
    }
  }

  @Override
  public long getNextLoadPositionUs() {
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      return loadingFinished ? C.TIME_END_OF_SOURCE : getLastMediaChunk().endTimeUs;
    }
  }

  @Override
  public boolean continueLoading(LoadingInfo loadingInfo) {
    if (loadingFinished || loader.isLoading() || loader.hasFatalError()) {
      return false;
    }

    List<HlsMediaChunk> chunkQueue;
    long loadPositionUs;
    if (isPendingReset()) {
      chunkQueue = Collections.emptyList();
      loadPositionUs = pendingResetPositionUs;
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.setStartTimeUs(pendingResetPositionUs);
      }
    } else {
      chunkQueue = readOnlyMediaChunks;
      HlsMediaChunk lastMediaChunk = getLastMediaChunk();
      loadPositionUs =
          lastMediaChunk.isLoadCompleted()
              ? lastMediaChunk.endTimeUs
              : max(lastSeekPositionUs, lastMediaChunk.startTimeUs);
    }
    nextChunkHolder.clear();
    chunkSource.getNextChunk(
        loadingInfo,
        loadPositionUs,
        chunkQueue,
        /* allowEndOfStream= */ prepared || !chunkQueue.isEmpty(),
        nextChunkHolder);
    boolean endOfStream = nextChunkHolder.endOfStream;
    @Nullable Chunk loadable = nextChunkHolder.chunk;
    @Nullable Uri playlistUrlToLoad = nextChunkHolder.playlistUrl;

    if (endOfStream) {
      pendingResetPositionUs = C.TIME_UNSET;
      loadingFinished = true;
      return true;
    }

    if (loadable == null) {
      if (playlistUrlToLoad != null) {
        callback.onPlaylistRefreshRequired(playlistUrlToLoad);
      }
      return false;
    }

    if (isMediaChunk(loadable)) {
      initMediaChunkLoad((HlsMediaChunk) loadable);
    }
    loadingChunk = loadable;
    long elapsedRealtimeMs =
        loader.startLoading(
            loadable, this, loadErrorHandlingPolicy.getMinimumLoadableRetryCount(loadable.type));
    mediaSourceEventDispatcher.loadStarted(
        new LoadEventInfo(loadable.loadTaskId, loadable.dataSpec, elapsedRealtimeMs),
        loadable.type,
        trackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs);
    return true;
  }

  @Override
  public boolean isLoading() {
    return loader.isLoading();
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    if (loader.hasFatalError() || isPendingReset()) {
      return;
    }

    if (loader.isLoading()) {
      Assertions.checkNotNull(loadingChunk);
      if (chunkSource.shouldCancelLoad(positionUs, loadingChunk, readOnlyMediaChunks)) {
        loader.cancelLoading();
      }
      return;
    }

    int newQueueSize = readOnlyMediaChunks.size();
    while (newQueueSize > 0
        && chunkSource.getChunkPublicationState(readOnlyMediaChunks.get(newQueueSize - 1))
            == CHUNK_PUBLICATION_STATE_REMOVED) {
      newQueueSize--;
    }
    if (newQueueSize < readOnlyMediaChunks.size()) {
      discardUpstream(newQueueSize);
    }

    int preferredQueueSize = chunkSource.getPreferredQueueSize(positionUs, readOnlyMediaChunks);
    if (preferredQueueSize < mediaChunks.size()) {
      discardUpstream(preferredQueueSize);
    }
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs) {
    loadingChunk = null;
    chunkSource.onChunkLoadCompleted(loadable);
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded());
    loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    mediaSourceEventDispatcher.loadCompleted(
        loadEventInfo,
        loadable.type,
        trackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs);
    if (!prepared) {
      continueLoading(new LoadingInfo.Builder().setPlaybackPositionUs(lastSeekPositionUs).build());
    } else {
      callback.onContinueLoadingRequested(this);
    }
  }

  @Override
  public void onLoadCanceled(
      Chunk loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {
    loadingChunk = null;
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded());
    loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    mediaSourceEventDispatcher.loadCanceled(
        loadEventInfo,
        loadable.type,
        trackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs);
    if (!released) {
      if (isPendingReset() || enabledTrackGroupCount == 0) {
        resetSampleQueues();
      }
      if (enabledTrackGroupCount > 0) {
        callback.onContinueLoadingRequested(this);
      }
    }
  }

  @Override
  public LoadErrorAction onLoadError(
      Chunk loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error,
      int errorCount) {
    boolean isMediaChunk = isMediaChunk(loadable);
    if (isMediaChunk
        && !((HlsMediaChunk) loadable).isPublished()
        && error instanceof HttpDataSource.InvalidResponseCodeException) {
      int responseCode = ((HttpDataSource.InvalidResponseCodeException) error).responseCode;
      if (responseCode == 410 || responseCode == 404) {
        // 根据 RFC 8216 第 6.2.6 节，服务器应对已替换且不再可用的提示部分请求返回 HTTP 404（未找到）。
        // 我们在测试流中也看到过 HTTP 410（已消失）的情况。
        return Loader.RETRY;
      }
    }
    long bytesLoaded = loadable.bytesLoaded();
    boolean exclusionSucceeded = false;
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            bytesLoaded);
    MediaLoadData mediaLoadData =
        new MediaLoadData(
            loadable.type,
            trackType,
            loadable.trackFormat,
            loadable.trackSelectionReason,
            loadable.trackSelectionData,
            Util.usToMs(loadable.startTimeUs),
            Util.usToMs(loadable.endTimeUs));
    LoadErrorInfo loadErrorInfo =
        new LoadErrorInfo(loadEventInfo, mediaLoadData, error, errorCount);
    LoadErrorAction loadErrorAction;
    @Nullable
    LoadErrorHandlingPolicy.FallbackSelection fallbackSelection =
        loadErrorHandlingPolicy.getFallbackSelectionFor(
            createFallbackOptions(chunkSource.getTrackSelection()), loadErrorInfo);
    if (fallbackSelection != null
        && fallbackSelection.type == LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK) {
      exclusionSucceeded =
          chunkSource.maybeExcludeTrack(loadable, fallbackSelection.exclusionDurationMs);
    }

    if (exclusionSucceeded) {
      if (isMediaChunk && bytesLoaded == 0) {
        HlsMediaChunk removed = mediaChunks.remove(mediaChunks.size() - 1);
        Assertions.checkState(removed == loadable);
        if (mediaChunks.isEmpty()) {
          pendingResetPositionUs = lastSeekPositionUs;
        } else {
          Iterables.getLast(mediaChunks).invalidateExtractor();
        }
      }
      loadErrorAction = Loader.DONT_RETRY;
    } else /* did not exclude */ {
      long retryDelayMs = loadErrorHandlingPolicy.getRetryDelayMsFor(loadErrorInfo);
      loadErrorAction =
          retryDelayMs != C.TIME_UNSET
              ? Loader.createRetryAction(/* resetErrorCount= */ false, retryDelayMs)
              : Loader.DONT_RETRY_FATAL;
    }

    boolean wasCanceled = !loadErrorAction.isRetry();
    mediaSourceEventDispatcher.loadError(
        loadEventInfo,
        loadable.type,
        trackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs,
        error,
        wasCanceled);
    if (wasCanceled) {
      loadingChunk = null;
      loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    }

    if (exclusionSucceeded) {
      if (!prepared) {
        continueLoading(
            new LoadingInfo.Builder().setPlaybackPositionUs(lastSeekPositionUs).build());
      } else {
        callback.onContinueLoadingRequested(this);
      }
    }
    return loadErrorAction;
  }

  // 由消费线程调用，但仅在无加载线程时调用。

  /**
   * 为即将开始加载的媒体块执行初始化。
   *
   * @param chunk 即将开始加载的媒体块。
   */
  private void initMediaChunkLoad(HlsMediaChunk chunk) {
    sourceChunk = chunk;
    upstreamTrackFormat = chunk.trackFormat;
    pendingResetPositionUs = C.TIME_UNSET;
    mediaChunks.add(chunk);
    ImmutableList.Builder<Integer> sampleQueueWriteIndicesBuilder = ImmutableList.builder();
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueueWriteIndicesBuilder.add(sampleQueue.getWriteIndex());
    }
    chunk.init(/* output= */ this, sampleQueueWriteIndicesBuilder.build());
    for (HlsSampleQueue sampleQueue : sampleQueues) {
      sampleQueue.setSourceChunk(chunk);
      if (chunk.shouldSpliceIn) {
        sampleQueue.splice();
      }
    }
  }

  private void discardUpstream(int preferredQueueSize) {
    Assertions.checkState(!loader.isLoading());

    int newQueueSize = C.LENGTH_UNSET;
    for (int i = preferredQueueSize; i < mediaChunks.size(); i++) {
      if (canDiscardUpstreamMediaChunksFromIndex(i)) {
        newQueueSize = i;
        break;
      }
    }
    if (newQueueSize == C.LENGTH_UNSET) {
      return;
    }

    long endTimeUs = getLastMediaChunk().endTimeUs;
    HlsMediaChunk firstRemovedChunk = discardUpstreamMediaChunksFromIndex(newQueueSize);
    if (mediaChunks.isEmpty()) {
      pendingResetPositionUs = lastSeekPositionUs;
    } else {
      Iterables.getLast(mediaChunks).invalidateExtractor();
    }
    loadingFinished = false;

    mediaSourceEventDispatcher.upstreamDiscarded(
        primarySampleQueueType, firstRemovedChunk.startTimeUs, endTimeUs);
  }

  // ExtractorOutput 实现。由加载线程调用。

  @Override
  public TrackOutput track(int id, int type) {
    @Nullable TrackOutput trackOutput = null;
    if (MAPPABLE_TYPES.contains(type)) {
      // MAPPABLE_TYPES 中的轨道类型会被手动处理以忽略 ID。
      trackOutput = getMappedTrackOutput(id, type);
    } else /* 不可映射类型的轨道 */ {
      for (int i = 0; i < sampleQueues.length; i++) {
        if (sampleQueueTrackIds[i] == id) {
          trackOutput = sampleQueues[i];
          break;
        }
      }
    }

    if (trackOutput == null) {
      if (tracksEnded) {
        return createDiscardingTrackOutput(id, type);
      } else {
        // 相关的 SampleQueue 尚未构建——因此构建它。
        trackOutput = createSampleQueue(id, type);
      }
    }

    if (type == C.TRACK_TYPE_METADATA) {
      if (emsgUnwrappingTrackOutput == null) {
        emsgUnwrappingTrackOutput = new EmsgUnwrappingTrackOutput(trackOutput, metadataType);
      }
      return emsgUnwrappingTrackOutput;
    }
    return trackOutput;
  }

  /**
   * 返回与提供的 {@code type} 和 {@code id} 对应的 {@link TrackOutput}，如果尚未创建，则返回 null。
   *
   * <p>如果已为 {@code type} 创建了一个 {@link SampleQueue} 并且已映射，但其 ID 不同，则返回一个不执行任何操作的 {@link DiscardingTrackOutput}。
   *
   * <p>如果已为 {@code type} 创建了一个 {@link SampleQueue} 但未映射，则将其映射到此 {@code id} 并返回。这种情况可能在调用 {@link #onNewExtractor} 后发生。
   *
   * @param id 轨道的 ID。
   * @param type 轨道的类型，必须是 {@link #MAPPABLE_TYPES} 之一。
   * @return 映射的 {@link TrackOutput}，如果尚未创建，则返回 null。
   */
  @Nullable
  private TrackOutput getMappedTrackOutput(int id, int type) {
    Assertions.checkArgument(MAPPABLE_TYPES.contains(type));
    int sampleQueueIndex = sampleQueueIndicesByType.get(type, C.INDEX_UNSET);
    if (sampleQueueIndex == C.INDEX_UNSET) {
      return null;
    }

    if (sampleQueueMappingDoneByType.add(type)) {
      sampleQueueTrackIds[sampleQueueIndex] = id;
    }
    return sampleQueueTrackIds[sampleQueueIndex] == id
        ? sampleQueues[sampleQueueIndex]
        : createDiscardingTrackOutput(id, type);
  }

  private SampleQueue createSampleQueue(int id, int type) {
    int trackCount = sampleQueues.length;

    boolean isAudioVideo = type == C.TRACK_TYPE_AUDIO || type == C.TRACK_TYPE_VIDEO;
    HlsSampleQueue sampleQueue =
        new HlsSampleQueue(allocator, drmSessionManager, drmEventDispatcher, overridingDrmInitData);
    sampleQueue.setStartTimeUs(lastSeekPositionUs);
    if (isAudioVideo) {
      sampleQueue.setDrmInitData(drmInitData);
    }
    sampleQueue.setSampleOffsetUs(sampleOffsetUs);
    if (sourceChunk != null) {
      sampleQueue.setSourceChunk(sourceChunk);
    }
    sampleQueue.setUpstreamFormatChangeListener(this);
    sampleQueueTrackIds = Arrays.copyOf(sampleQueueTrackIds, trackCount + 1);
    sampleQueueTrackIds[trackCount] = id;
    sampleQueues = Util.nullSafeArrayAppend(sampleQueues, sampleQueue);
    sampleQueueIsAudioVideoFlags = Arrays.copyOf(sampleQueueIsAudioVideoFlags, trackCount + 1);
    sampleQueueIsAudioVideoFlags[trackCount] = isAudioVideo;
    haveAudioVideoSampleQueues |= sampleQueueIsAudioVideoFlags[trackCount];
    sampleQueueMappingDoneByType.add(type);
    sampleQueueIndicesByType.append(type, trackCount);
    if (getTrackTypeScore(type) > getTrackTypeScore(primarySampleQueueType)) {
      primarySampleQueueIndex = trackCount;
      primarySampleQueueType = type;
    }
    sampleQueuesEnabledStates = Arrays.copyOf(sampleQueuesEnabledStates, trackCount + 1);
    return sampleQueue;
  }

  @Override
  public void endTracks() {
    tracksEnded = true;
    handler.post(onTracksEndedRunnable);
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    // Do nothing.
  }
  // UpstreamFormatChangedListener 实现。由加载线程调用。

  @Override
  public void onUpstreamFormatChanged(Format format) {
    handler.post(maybeFinishPrepareRunnable);
  }

  // 由加载线程调用。

  /** 当 {@link HlsMediaChunk} 开始使用新的 {@link Extractor} 提取媒体时调用。 */
  public void onNewExtractor() {
    sampleQueueMappingDoneByType.clear();
  }

  /**
   * 设置一个偏移量，该偏移量将添加到随后由该包装器加载的样本的时间戳（以及子样本时间戳）中。
   *
   * @param sampleOffsetUs 时间戳偏移量（以微秒为单位）。
   */
  public void setSampleOffsetUs(long sampleOffsetUs) {
    if (this.sampleOffsetUs != sampleOffsetUs) {
      this.sampleOffsetUs = sampleOffsetUs;
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.setSampleOffsetUs(sampleOffsetUs);
      }
    }
  }

  /**
   * 为随后由该包装器加载的样本设置默认的 {@link DrmInitData}。
   *
   * <p>应在加载每个 {@link HlsMediaChunk} 之前调用此方法。传递的 {@link DrmInitData} 应为适用于该块的 EXT-X-KEY 标签的 {@link DrmInitData}，否则为 {@code null}。
   *
   * <p>随后排队的样本的最终 {@link DrmInitData} 按以下方式确定：
   *
   * <ol>
   *   <li>它最初设置为 {@code drmInitData}，除非 {@code drmInitData} 为 null，在这种情况下，它设置为上游 {@link Format} 的 {@link Format#drmInitData}。
   *   <li>如果初始 {@link DrmInitData} 非空且 {@link #overridingDrmInitData} 包含一个键与 {@link DrmInitData#schemeType} 匹配的条目，则样本的 {@link DrmInitData} 将被覆盖为该条目的值。
   * </ol>
   *
   * @param drmInitData 随后排队的样本的默认 {@link DrmInitData}。如果非空，则优先于上游 {@link Format} 的 {@link Format#drmInitData}，但仍会被 {@link #overridingDrmInitData} 中的匹配覆盖。
   */
  public void setDrmInitData(@Nullable DrmInitData drmInitData) {
    if (!Util.areEqual(this.drmInitData, drmInitData)) {
      this.drmInitData = drmInitData;
      for (int i = 0; i < sampleQueues.length; i++) {
        if (sampleQueueIsAudioVideoFlags[i]) {
          sampleQueues[i].setDrmInitData(drmInitData);
        }
      }
    }
  }

  // Internal methods.

  private void updateSampleStreams(@NullableType SampleStream[] streams) {
    hlsSampleStreams.clear();
    for (@Nullable SampleStream stream : streams) {
      if (stream != null) {
        hlsSampleStreams.add((HlsSampleStream) stream);
      }
    }
  }

  private boolean finishedReadingChunk(HlsMediaChunk chunk) {
    int chunkUid = chunk.uid;
    int sampleQueueCount = sampleQueues.length;
    for (int i = 0; i < sampleQueueCount; i++) {
      if (sampleQueuesEnabledStates[i] && sampleQueues[i].peekSourceId() == chunkUid) {
        return false;
      }
    }
    return true;
  }

  private boolean canDiscardUpstreamMediaChunksFromIndex(int mediaChunkIndex) {
    for (int i = mediaChunkIndex; i < mediaChunks.size(); i++) {
      if (mediaChunks.get(i).shouldSpliceIn) {
        // 无法丢弃，因为拼接的块可能移除了先前块的样本元数据。
        // TODO: 保留样本元数据以允许恢复这些块 [内部 b/159904763]。
        return false;
      }
    }
    HlsMediaChunk mediaChunk = mediaChunks.get(mediaChunkIndex);
    for (int i = 0; i < sampleQueues.length; i++) {
      int discardFromIndex = mediaChunk.getFirstSampleIndex(/* sampleQueueIndex= */ i);
      if (sampleQueues[i].getReadIndex() > discardFromIndex) {
        // 无法丢弃，因为我们已经从该块读取了数据。
        // TODO: 稀疏轨道（例如 ID3）可能几乎在所有情况下都阻止丢弃，因为这意味着大多数块已经被读取过。参见 [内部 b/161126666]。
        return false;
      }
    }
    return true;
  }

  private HlsMediaChunk discardUpstreamMediaChunksFromIndex(int chunkIndex) {
    HlsMediaChunk firstRemovedChunk = mediaChunks.get(chunkIndex);
    Util.removeRange(mediaChunks, /* fromIndex= */ chunkIndex, /* toIndex= */ mediaChunks.size());
    for (int i = 0; i < sampleQueues.length; i++) {
      int discardFromIndex = firstRemovedChunk.getFirstSampleIndex(/* sampleQueueIndex= */ i);
      sampleQueues[i].discardUpstreamSamples(discardFromIndex);
    }
    return firstRemovedChunk;
  }

  private void resetSampleQueues() {
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueue.reset(pendingResetUpstreamFormats);
    }
    pendingResetUpstreamFormats = false;
  }

  private void onTracksEnded() {
    sampleQueuesBuilt = true;
    maybeFinishPrepare();
  }

  private void maybeFinishPrepare() {
    if (released || trackGroupToSampleQueueIndex != null || !sampleQueuesBuilt) {
      return;
    }
    for (SampleQueue sampleQueue : sampleQueues) {
      if (sampleQueue.getUpstreamFormat() == null) {
        return;
      }
    }
    if (trackGroups != null) {
      // 轨道组是使用多变量播放列表信息创建的。它们只需要映射到样本队列。
      mapSampleQueuesToMatchTrackGroups();
    } else {
      // 使用媒体段信息创建轨道。
      buildTracksFromSampleStreams();
      setIsPrepared();
      callback.onPrepared();
    }
  }

  @RequiresNonNull("trackGroups")
  @EnsuresNonNull("trackGroupToSampleQueueIndex")
  private void mapSampleQueuesToMatchTrackGroups() {
    int trackGroupCount = trackGroups.length;
    trackGroupToSampleQueueIndex = new int[trackGroupCount];
    Arrays.fill(trackGroupToSampleQueueIndex, C.INDEX_UNSET);
    for (int i = 0; i < trackGroupCount; i++) {
      for (int queueIndex = 0; queueIndex < sampleQueues.length; queueIndex++) {
        SampleQueue sampleQueue = sampleQueues[queueIndex];
        Format upstreamFormat = Assertions.checkStateNotNull(sampleQueue.getUpstreamFormat());
        if (formatsMatch(upstreamFormat, trackGroups.get(i).getFormat(0))) {
          trackGroupToSampleQueueIndex[i] = queueIndex;
          break;
        }
      }
    }
    for (HlsSampleStream sampleStream : hlsSampleStreams) {
      sampleStream.bindSampleQueue();
    }
  }

  /**
   * 构建由此 {@link HlsSampleStreamWrapper} 实例暴露的轨道，以及操作所需的内部数据结构。
   *
   * <p>HLS 中的轨道较为复杂。HLS 多变量播放列表包含多个“变体”。
   * 每个变体流通常包含多路复用的视频、音频以及（可能）额外的音频、元数据和字幕轨道。
   * 我们希望允许用户选择跨越所有变体的自适应轨道，以及每个单独的变体。
   * 如果每个变体中存在多个音频轨道，我们还希望允许用户在这些轨道之间进行选择。
   *
   * <p>为此，轨道按如下方式构建。{@link HlsChunkSource} 暴露 (N+1) 个轨道，其中 N 是 HLS 多变量播放列表中定义的变体数量。这些轨道包括一个跨越所有变体的自适应轨道，以及每个单独变体的轨道。自适应轨道最初被选中。然后准备提取器以发现每个变体流中的轨道。然后通过此方法将两组轨道组合起来，创建第三组轨道，即由此 {@link HlsSampleStreamWrapper} 暴露的轨道集：
   *
   * <ul>
   *   <li>检查提取器轨道以推断“主”轨道类型。如果存在视频轨道，则它始终是主类型。
   *   如果没有，则音频是主类型（如果存在）。
   *   否则，文本是主类型（如果存在）。否则，没有主类型。
   *   <li>如果主类型恰好有一个提取器轨道，则将其扩展为 (N+1) 个暴露的轨道，所有这些轨道都对应于主提取器轨道，并且每个轨道对应于不同的块源轨道。
   *   选择其中一个轨道会切换块源上的选中轨道。
   *   <li>所有其他提取器轨道直接暴露。
   *   选择其中一个轨道会选中提取器轨道，而块源上的选中轨道保持不变。
   * </ul>
   */
  @EnsuresNonNull({"trackGroups", "optionalTrackGroups", "trackGroupToSampleQueueIndex"})
  private void buildTracksFromSampleStreams() {
    // 遍历提取器轨道以发现“主”轨道类型，以及该类型的单一轨道的索引。
    int primaryExtractorTrackType = C.TRACK_TYPE_NONE;
    int primaryExtractorTrackIndex = C.INDEX_UNSET;
    int extractorTrackCount = sampleQueues.length;
    for (int i = 0; i < extractorTrackCount; i++) {
      @Nullable
      String sampleMimeType =
          Assertions.checkStateNotNull(sampleQueues[i].getUpstreamFormat()).sampleMimeType;
      int trackType;
      if (MimeTypes.isVideo(sampleMimeType)) {
        trackType = C.TRACK_TYPE_VIDEO;
      } else if (MimeTypes.isAudio(sampleMimeType)) {
        trackType = C.TRACK_TYPE_AUDIO;
      } else if (MimeTypes.isText(sampleMimeType)) {
        trackType = C.TRACK_TYPE_TEXT;
      } else {
        trackType = C.TRACK_TYPE_NONE;
      }
      if (getTrackTypeScore(trackType) > getTrackTypeScore(primaryExtractorTrackType)) {
        primaryExtractorTrackType = trackType;
        primaryExtractorTrackIndex = i;
      } else if (trackType == primaryExtractorTrackType
          && primaryExtractorTrackIndex != C.INDEX_UNSET) {
        // 我们有多个主类型的轨道。只有在主类型只有一个轨道时我们才需要索引，因此再次取消索引。
        primaryExtractorTrackIndex = C.INDEX_UNSET;
      }
    }

    TrackGroup chunkSourceTrackGroup = chunkSource.getTrackGroup();
    int chunkSourceTrackCount = chunkSourceTrackGroup.length;

    // 实例化必要的内部数据结构。
    primaryTrackGroupIndex = C.INDEX_UNSET;
    trackGroupToSampleQueueIndex = new int[extractorTrackCount];
    for (int i = 0; i < extractorTrackCount; i++) {
      trackGroupToSampleQueueIndex[i] = i;
    }

    // 构建暴露的轨道组集合。
    TrackGroup[] trackGroups = new TrackGroup[extractorTrackCount];
    for (int i = 0; i < extractorTrackCount; i++) {
      Format sampleFormat = Assertions.checkStateNotNull(sampleQueues[i].getUpstreamFormat());
      if (i == primaryExtractorTrackIndex) {
        Format[] formats = new Format[chunkSourceTrackCount];
        for (int j = 0; j < chunkSourceTrackCount; j++) {
          Format playlistFormat = chunkSourceTrackGroup.getFormat(j);
          if (primaryExtractorTrackType == C.TRACK_TYPE_AUDIO && muxedAudioFormat != null) {
            playlistFormat = playlistFormat.withManifestFormatInfo(muxedAudioFormat);
          }
          // 如果只有一个变体（chunkSourceTrackCount == 1），则可以安全地保留 sampleFormat 的所有字段。否则，我们需要使用 deriveFormat 仅保留所有变体相同的字段。
          formats[j] =
              chunkSourceTrackCount == 1
                  ? sampleFormat.withManifestFormatInfo(playlistFormat)
                  : deriveFormat(playlistFormat, sampleFormat, /* propagateBitrates= */ true);
        }
        trackGroups[i] = new TrackGroup(uid, formats);
        primaryTrackGroupIndex = i;
      } else {
        @Nullable
        Format playlistFormat =
            primaryExtractorTrackType == C.TRACK_TYPE_VIDEO
                && MimeTypes.isAudio(sampleFormat.sampleMimeType)
                ? muxedAudioFormat
                : null;
        String muxedTrackGroupId = uid + ":muxed:" + (i < primaryExtractorTrackIndex ? i : i - 1);
        trackGroups[i] =
            new TrackGroup(
                muxedTrackGroupId,
                deriveFormat(playlistFormat, sampleFormat, /* propagateBitrates= */ false));
      }
    }
    this.trackGroups = createTrackGroupArrayWithDrmInfo(trackGroups);
    Assertions.checkState(optionalTrackGroups == null);
    optionalTrackGroups = Collections.emptySet();
  }

  private TrackGroupArray createTrackGroupArrayWithDrmInfo(TrackGroup[] trackGroups) {
    for (int i = 0; i < trackGroups.length; i++) {
      TrackGroup trackGroup = trackGroups[i];
      Format[] exposedFormats = new Format[trackGroup.length];
      for (int j = 0; j < trackGroup.length; j++) {
        Format format = trackGroup.getFormat(j);
        exposedFormats[j] = format.copyWithCryptoType(drmSessionManager.getCryptoType(format));
      }
      trackGroups[i] = new TrackGroup(trackGroup.id, exposedFormats);
    }
    return new TrackGroupArray(trackGroups);
  }

  private HlsMediaChunk getLastMediaChunk() {
    return mediaChunks.get(mediaChunks.size() - 1);
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != C.TIME_UNSET;
  }

  /**
   * 尝试在样本队列中跳转到指定位置。
   *
   * @param positionUs 跳转位置（以微秒为单位）。
   * @param chunk 要跳转到的块，如果为 null，则跳转到精确位置。如果此参数非空，则忽略 {@code positionUs}。
   * @return 缓冲区内的跳转是否成功。
   */
  private boolean seekInsideBufferUs(long positionUs, @Nullable HlsMediaChunk chunk) {
    int sampleQueueCount = sampleQueues.length;
    for (int i = 0; i < sampleQueueCount; i++) {
      SampleQueue sampleQueue = sampleQueues[i];
      boolean seekInsideQueue;
      if (chunk != null) {
        seekInsideQueue = sampleQueue.seekTo(chunk.getFirstSampleIndex(i));
      } else {
        seekInsideQueue = sampleQueue.seekTo(positionUs, /* allowTimeBeyondBuffer= */ false);
      }
      // 如果有音视频轨道，则当每个音视频队列中的跳转都成功时，队列内的跳转才成功。在这种情况下，我们忽略非音视频队列中的跳转是否成功，因为它们可能是稀疏的或交错不良的。如果只有非音视频轨道，则只有当每个队列中的跳转都成功时，跳转才成功。
      if (!seekInsideQueue && (sampleQueueIsAudioVideoFlags[i] || !haveAudioVideoSampleQueues)) {
        return false;
      }
    }
    return true;
  }

  @RequiresNonNull({"trackGroups", "optionalTrackGroups"})
  private void setIsPrepared() {
    prepared = true;
  }

  @EnsuresNonNull({"trackGroups", "optionalTrackGroups"})
  private void assertIsPrepared() {
    Assertions.checkState(prepared);
    Assertions.checkNotNull(trackGroups);
    Assertions.checkNotNull(optionalTrackGroups);
  }

  /**
   * 对轨道类型进行评分。当多个轨道复用到同一个容器中时，得分最高的轨道是主轨道。
   *
   * @param trackType 轨道类型。
   * @return 评分。
   */
  private static int getTrackTypeScore(int trackType) {
    switch (trackType) {
      case C.TRACK_TYPE_VIDEO:
        return 3;
      case C.TRACK_TYPE_AUDIO:
        return 2;
      case C.TRACK_TYPE_TEXT:
        return 1;
      default:
        return 0;
    }
  }

  /**
   * 从多变量播放列表中的相应格式以及可能从属于同一轨道组中不同轨道的块中获取的样本格式，推导出轨道样本格式。
   *
   * <p>注意：由于样本格式可能从属于同一轨道组中不同轨道的块中获取，因此不应将其用作可能在轨道之间变化的数据源。
   *
   * @param playlistFormat 从多变量播放列表中获取的格式信息。
   * @param sampleFormat 从块中的样本获取的格式信息。该块可能属于同一轨道组中的不同轨道。
   * @param propagateBitrates 是否将播放列表格式中的比特率包含在推导的格式中。
   * @return 推导的轨道格式。
   */
  private static Format deriveFormat(
      @Nullable Format playlistFormat, Format sampleFormat, boolean propagateBitrates) {
    if (playlistFormat == null) {
      return sampleFormat;
    }

    int sampleTrackType = MimeTypes.getTrackType(sampleFormat.sampleMimeType);
    @Nullable String sampleMimeType;
    @Nullable String codecs;
    if (Util.getCodecCountOfType(playlistFormat.codecs, sampleTrackType) == 1) {
      // 我们可以明确地将此轨道映射到播放列表变体，因为只有一个编解码器字符串与此轨道的类型匹配。
      codecs = Util.getCodecsOfType(playlistFormat.codecs, sampleTrackType);
      sampleMimeType = MimeTypes.getMediaMimeType(codecs);
    } else {
      // 该变体为此轨道分配了多个编解码器字符串。我们选择与样本 MIME 类型匹配的编解码器字符串。当不同语言使用不同编解码器编码时，可能会发生这种情况。
      codecs =
          MimeTypes.getCodecsCorrespondingToMimeType(
              playlistFormat.codecs, sampleFormat.sampleMimeType);
      sampleMimeType = sampleFormat.sampleMimeType;
    }
    Format.Builder formatBuilder =
        sampleFormat
            .buildUpon()
            .setId(playlistFormat.id)
            .setLabel(playlistFormat.label)
            .setLabels(playlistFormat.labels)
            .setLanguage(playlistFormat.language)
            .setSelectionFlags(playlistFormat.selectionFlags)
            .setRoleFlags(playlistFormat.roleFlags)
            .setAverageBitrate(propagateBitrates ? playlistFormat.averageBitrate : Format.NO_VALUE)
            .setPeakBitrate(propagateBitrates ? playlistFormat.peakBitrate : Format.NO_VALUE)
            .setCodecs(codecs);

    if (sampleTrackType == C.TRACK_TYPE_VIDEO) {
      formatBuilder
          .setWidth(playlistFormat.width)
          .setHeight(playlistFormat.height)
          .setFrameRate(playlistFormat.frameRate);
    }

    if (sampleMimeType != null) {
      formatBuilder.setSampleMimeType(sampleMimeType);
    }

    if (playlistFormat.channelCount != Format.NO_VALUE && sampleTrackType == C.TRACK_TYPE_AUDIO) {
      formatBuilder.setChannelCount(playlistFormat.channelCount);
    }

    if (playlistFormat.metadata != null) {
      Metadata metadata = playlistFormat.metadata;
      if (sampleFormat.metadata != null) {
        metadata = sampleFormat.metadata.copyWithAppendedEntriesFrom(metadata);
      }
      formatBuilder.setMetadata(metadata);
    }

    return formatBuilder.build();
  }

  private static boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof HlsMediaChunk;
  }

  private static boolean formatsMatch(Format manifestFormat, Format sampleFormat) {
    @Nullable String manifestFormatMimeType = manifestFormat.sampleMimeType;
    @Nullable String sampleFormatMimeType = sampleFormat.sampleMimeType;
    int manifestFormatTrackType = MimeTypes.getTrackType(manifestFormatMimeType);
    if (manifestFormatTrackType != C.TRACK_TYPE_TEXT) {
      return manifestFormatTrackType == MimeTypes.getTrackType(sampleFormatMimeType);
    } else if (!Util.areEqual(manifestFormatMimeType, sampleFormatMimeType)) {
      return false;
    }
    if (MimeTypes.APPLICATION_CEA608.equals(manifestFormatMimeType)
        || MimeTypes.APPLICATION_CEA708.equals(manifestFormatMimeType)) {
      return manifestFormat.accessibilityChannel == sampleFormat.accessibilityChannel;
    }
    return true;
  }

  private static DiscardingTrackOutput createDiscardingTrackOutput(int id, int type) {
    Log.w(TAG, "Unmapped track with id " + id + " of type " + type);
    return new DiscardingTrackOutput();
  }

  /**
   * 一个 {@link SampleQueue}，增加了 HLS 特定的功能：
   *
   * <ul>
   *   <li>通过检查样本时间戳与当前加载块的预期范围，检测虚假的不连续性。
   *   <li>从 {@link Format Formats} 中剥离私有时间戳元数据，以避免队列中过多的格式切换。
   *   <li>覆盖 {@link Format#drmInitData}。
   * </ul>
   */
  private static final class HlsSampleQueue extends SampleQueue {

    // TODO: 取消注释以拒绝具有意外时间戳的样本。参见
    // https://github.com/google/ExoPlayer/issues/7030。
    // /**
    //  * 块持续时间的分数，从该分数开始，从块内加载的样本的时间戳允许偏离预期范围。
    //  */
    // private static final double MAX_TIMESTAMP_DEVIATION_FRACTION = 0.5;
    //
    // /**
    //  * 样本时间戳的最小容差（以微秒为单位）。从块内加载的样本的时间戳始终允许偏离预期范围最多此值。
    //  */
    // private static final long MIN_TIMESTAMP_DEVIATION_TOLERANCE_US = 4_000_000;
    //
    // @Nullable private HlsMediaChunk sourceChunk;
    // private long sourceChunkLastSampleTimeUs;
    // private long minAllowedSampleTimeUs;
    // private long maxAllowedSampleTimeUs;

    private final Map<String, DrmInitData> overridingDrmInitData;
    @Nullable private DrmInitData drmInitData;

    private HlsSampleQueue(
        Allocator allocator,
        DrmSessionManager drmSessionManager,
        DrmSessionEventListener.EventDispatcher eventDispatcher,
        Map<String, DrmInitData> overridingDrmInitData) {
      super(allocator, drmSessionManager, eventDispatcher);
      this.overridingDrmInitData = overridingDrmInitData;
    }

    public void setSourceChunk(HlsMediaChunk chunk) {
      sourceId(chunk.uid);

      // TODO: 取消注释以拒绝具有意外时间戳的样本。参见
      // https://github.com/google/ExoPlayer/issues/7030。
      // sourceChunk = chunk;
      // sourceChunkLastSampleTimeUs = C.TIME_UNSET;
      // long allowedDeviationUs =
      //     Math.max(
      //         (long) ((chunk.endTimeUs - chunk.startTimeUs) * MAX_TIMESTAMP_DEVIATION_FRACTION),
      //         MIN_TIMESTAMP_DEVIATION_TOLERANCE_US);
      // minAllowedSampleTimeUs = chunk.startTimeUs - allowedDeviationUs;
      // maxAllowedSampleTimeUs = chunk.endTimeUs + allowedDeviationUs;
    }
    public void setDrmInitData(@Nullable DrmInitData drmInitData) {
      this.drmInitData = drmInitData;
      invalidateUpstreamFormatAdjustment();
    }

    @SuppressWarnings("ReferenceEquality")
    @Override
    public Format getAdjustedUpstreamFormat(Format format) {
      @Nullable
      DrmInitData drmInitData = this.drmInitData != null ? this.drmInitData : format.drmInitData;
      if (drmInitData != null) {
        @Nullable
        DrmInitData overridingDrmInitData = this.overridingDrmInitData.get(drmInitData.schemeType);
        if (overridingDrmInitData != null) {
          drmInitData = overridingDrmInitData;
        }
      }
      @Nullable Metadata metadata = getAdjustedMetadata(format.metadata);
      if (drmInitData != format.drmInitData || metadata != format.metadata) {
        format = format.buildUpon().setDrmInitData(drmInitData).setMetadata(metadata).build();
      }
      return super.getAdjustedUpstreamFormat(format);
    }

    /**
     * 如果存在，则从元数据中剥离私有时间戳帧。参见：
     * https://github.com/google/ExoPlayer/issues/5063
     */
    @Nullable
    private Metadata getAdjustedMetadata(@Nullable Metadata metadata) {
      if (metadata == null) {
        return null;
      }
      int length = metadata.length();
      int transportStreamTimestampMetadataIndex = C.INDEX_UNSET;
      for (int i = 0; i < length; i++) {
        Metadata.Entry metadataEntry = metadata.get(i);
        if (metadataEntry instanceof PrivFrame) {
          PrivFrame privFrame = (PrivFrame) metadataEntry;
          if (HlsMediaChunk.PRIV_TIMESTAMP_FRAME_OWNER.equals(privFrame.owner)) {
            transportStreamTimestampMetadataIndex = i;
            break;
          }
        }
      }
      if (transportStreamTimestampMetadataIndex == C.INDEX_UNSET) {
        return metadata;
      }
      if (length == 1) {
        return null;
      }
      Metadata.Entry[] newMetadataEntries = new Metadata.Entry[length - 1];
      for (int i = 0; i < length; i++) {
        if (i != transportStreamTimestampMetadataIndex) {
          int newIndex = i < transportStreamTimestampMetadataIndex ? i : i - 1;
          newMetadataEntries[newIndex] = metadata.get(i);
        }
      }
      return new Metadata(newMetadataEntries);
    }

    @Override
    public void sampleMetadata(
        long timeUs,
        @C.BufferFlags int flags,
        int size,
        int offset,
        @Nullable CryptoData cryptoData) {
      // TODO: 取消注释以拒绝具有意外时间戳的样本。参见
      // https://github.com/google/ExoPlayer/issues/7030。
      // if (timeUs < minAllowedSampleTimeUs || timeUs > maxAllowedSampleTimeUs) {
      //   Util.sneakyThrow(
      //       new UnexpectedSampleTimestampException(
      //           sourceChunk, sourceChunkLastSampleTimeUs, timeUs));
      // }
      // sourceChunkLastSampleTimeUs = timeUs;
      super.sampleMetadata(timeUs, flags, size, offset, cryptoData);
    }
  }

  private static class EmsgUnwrappingTrackOutput implements TrackOutput {

    // TODO: Create a Formats util class with common constants like this.
    private static final Format ID3_FORMAT =
        new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_ID3).build();
    private static final Format EMSG_FORMAT =
        new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_EMSG).build();

    private final EventMessageDecoder emsgDecoder;
    private final TrackOutput delegate;
    private final Format delegateFormat;
    private @MonotonicNonNull Format format;

    private byte[] buffer;
    private int bufferPosition;

    public EmsgUnwrappingTrackOutput(
        TrackOutput delegate, @HlsMediaSource.MetadataType int metadataType) {
      this.emsgDecoder = new EventMessageDecoder();
      this.delegate = delegate;
      switch (metadataType) {
        case HlsMediaSource.METADATA_TYPE_ID3:
          delegateFormat = ID3_FORMAT;
          break;
        case HlsMediaSource.METADATA_TYPE_EMSG:
          delegateFormat = EMSG_FORMAT;
          break;
        default:
          throw new IllegalArgumentException("Unknown metadataType: " + metadataType);
      }

      this.buffer = new byte[0];
      this.bufferPosition = 0;
    }

    @Override
    public void format(Format format) {
      this.format = format;
      delegate.format(delegateFormat);
    }

    @Override
    public int sampleData(
        DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
        throws IOException {
      ensureBufferCapacity(bufferPosition + length);
      int numBytesRead = input.read(buffer, bufferPosition, length);
      if (numBytesRead == C.RESULT_END_OF_INPUT) {
        if (allowEndOfInput) {
          return C.RESULT_END_OF_INPUT;
        } else {
          throw new EOFException();
        }
      }
      bufferPosition += numBytesRead;
      return numBytesRead;
    }

    @Override
    public void sampleData(ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
      ensureBufferCapacity(bufferPosition + length);
      data.readBytes(this.buffer, bufferPosition, length);
      bufferPosition += length;
    }

    @Override
    public void sampleMetadata(
        long timeUs,
        @C.BufferFlags int flags,
        int size,
        int offset,
        @Nullable CryptoData cryptoData) {
      Assertions.checkNotNull(format);
      ParsableByteArray sample = getSampleAndTrimBuffer(size, offset);
      ParsableByteArray sampleForDelegate;
      if (Util.areEqual(format.sampleMimeType, delegateFormat.sampleMimeType)) {
        // 传入的格式与委托轨道的格式匹配，因此直接传递。
        sampleForDelegate = sample;
      } else if (MimeTypes.APPLICATION_EMSG.equals(format.sampleMimeType)) {
        // 传入的样本是 EMSG，而委托轨道不期望 EMSG，因此尝试解包。
        EventMessage emsg = emsgDecoder.decode(sample);
        if (!emsgContainsExpectedWrappedFormat(emsg)) {
          Log.w(
              TAG,
              String.format(
                  "忽略 EMSG。期望它包含包装的 %s，但实际包装的格式为：%s",
                  delegateFormat.sampleMimeType, emsg.getWrappedMetadataFormat()));
          return;
        }
        sampleForDelegate =
            new ParsableByteArray(Assertions.checkNotNull(emsg.getWrappedMetadataBytes()));
      } else {
        Log.w(TAG, "忽略不支持格式的样本：" + format.sampleMimeType);
        return;
      }

      int sampleSize = sampleForDelegate.bytesLeft();

      delegate.sampleData(sampleForDelegate, sampleSize);
      delegate.sampleMetadata(timeUs, flags, sampleSize, /* offset= */ 0, cryptoData);
    }

    private boolean emsgContainsExpectedWrappedFormat(EventMessage emsg) {
      @Nullable Format wrappedMetadataFormat = emsg.getWrappedMetadataFormat();
      return wrappedMetadataFormat != null
          && Util.areEqual(delegateFormat.sampleMimeType, wrappedMetadataFormat.sampleMimeType);
    }

    private void ensureBufferCapacity(int requiredLength) {
      if (buffer.length < requiredLength) {
        buffer = Arrays.copyOf(buffer, requiredLength + requiredLength / 2);
      }
    }

    /**
     * 从 {@link #buffer} 字段中移除一个完整的样本，并将 {@code offset} 跳过的尾部数据重新排列到数组的头部。
     *
     * @param size 参见 {@link #sampleMetadata} 的 {@code size} 参数。
     * @param offset 参见 {@link #sampleMetadata} 的 {@code offset} 参数。
     * @return 一个包含从 {@link #buffer} 中移除的样本的 {@link ParsableByteArray}。
     */
    private ParsableByteArray getSampleAndTrimBuffer(int size, int offset) {
      int sampleEnd = bufferPosition - offset;
      int sampleStart = sampleEnd - size;

      byte[] sampleBytes = Arrays.copyOfRange(buffer, sampleStart, sampleEnd);
      ParsableByteArray sample = new ParsableByteArray(sampleBytes);

      System.arraycopy(buffer, sampleEnd, buffer, 0, offset);
      bufferPosition = offset;
      return sample;
    }
  }
}
