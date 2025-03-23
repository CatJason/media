package androidx.media3.exoplayer.hls;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.lang.Math.max;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.net.Uri;
import android.os.SystemClock;
import android.util.Pair;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UriUtil;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist.Segment;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker;
import androidx.media3.exoplayer.source.BehindLiveWindowException;
import androidx.media3.exoplayer.source.chunk.BaseMediaChunkIterator;
import androidx.media3.exoplayer.source.chunk.Chunk;
import androidx.media3.exoplayer.source.chunk.DataChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator;
import androidx.media3.exoplayer.trackselection.BaseTrackSelection;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.CmcdData;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** HLS（可能是自适应的）块的来源。 */
/* package */ class HlsChunkSource {

  /** 允许调度重试的块持有者。 */
  public static final class HlsChunkHolder {

    public HlsChunkHolder() {
      clear();
    }

    /** 要加载的下一个块。 */
    @Nullable public Chunk chunk;

    /** 表示流已结束。 */
    public boolean endOfStream;

    /** 表示块源正在等待引用的播放列表刷新。 */
    @Nullable public Uri playlistUrl;

    /** 清除持有者。 */
    public void clear() {
      chunk = null;
      endOfStream = false;
      playlistUrl = null;
    }
  }

  /**
   * 块发布状态。可以是 {@link #CHUNK_PUBLICATION_STATE_PRELOAD}、{@link #CHUNK_PUBLICATION_STATE_PUBLISHED}、{@link #CHUNK_PUBLICATION_STATE_REMOVED} 之一。
   */
  @Documented
  @Target(TYPE_USE)
  @IntDef({
      CHUNK_PUBLICATION_STATE_PRELOAD,
      CHUNK_PUBLICATION_STATE_PUBLISHED,
      CHUNK_PUBLICATION_STATE_REMOVED
  })
  @Retention(RetentionPolicy.SOURCE)
  @interface ChunkPublicationState {}

  /** 表示该块基于预加载提示。 */
  public static final int CHUNK_PUBLICATION_STATE_PRELOAD = 0;

  /** 表示该块已明确发布。 */
  public static final int CHUNK_PUBLICATION_STATE_PUBLISHED = 1;

  /**
   * 表示该块已从播放列表中移除。
   *
   * <p>另请参见 RFC 8216，第 6.2.6 节。
   */
  public static final int CHUNK_PUBLICATION_STATE_REMOVED = 2;

  /**
   * 密钥缓存可以容纳的最大密钥数量。此值必须为 2 或更大，以便同时容纳初始化片段和媒体片段的密钥。
   */
  private static final int KEY_CACHE_SIZE = 4;

  private final HlsExtractorFactory extractorFactory;
  private final DataSource mediaDataSource;
  private final DataSource encryptionDataSource;
  private final TimestampAdjusterProvider timestampAdjusterProvider;
  private final Uri[] playlistUrls;
  private final Format[] playlistFormats;
  private final HlsPlaylistTracker playlistTracker;
  private final TrackGroup trackGroup;
  @Nullable private final List<Format> muxedCaptionFormats;
  private final FullSegmentEncryptionKeyCache keyCache;
  private final PlayerId playerId;
  @Nullable private final CmcdConfiguration cmcdConfiguration;
  private final long timestampAdjusterInitializationTimeoutMs;

  private boolean isPrimaryTimestampSource;
  private byte[] scratchSpace;
  @Nullable private IOException fatalError;
  @Nullable private Uri expectedPlaylistUrl;
  private boolean independentSegments;

  // 注意：选择中的轨道组通常*不*等于 trackGroup。这是由于 HlsSampleStreamWrapper 生成轨道组的方式所致。请仅使用 ExoTrackSelection 中基于索引的方法，以避免意外行为。
  private ExoTrackSelection trackSelection;
  private long liveEdgeInPeriodTimeUs;
  private boolean seenExpectedPlaylistError;

  /**
   * 上次调用 {@link #getNextChunk(LoadingInfo, long, List, boolean, HlsChunkHolder)} 方法的时间，以 {@link SystemClock#elapsedRealtime} 测量。
   */
  private long lastChunkRequestRealtimeMs;

  /**
   * @param extractorFactory 用于获取媒体块提取器的 {@link HlsExtractorFactory}。
   * @param playlistTracker 用于获取媒体播放列表的 {@link HlsPlaylistTracker}。
   * @param playlistUrls 此块源可以适应的媒体播放列表的 {@link Uri}。
   * @param playlistFormats 与媒体播放列表对应的 {@link Format}。
   * @param dataSourceFactory 用于创建块 {@link DataSource} 的 {@link HlsDataSourceFactory}。
   * @param mediaTransferListener 应通知任何媒体数据传输的传输监听器。如果不可用，可以为 null。
   * @param timestampAdjusterProvider {@link TimestampAdjuster} 实例的提供者。如果单个播放使用了多个 {@link HlsChunkSource}，它们应共享同一个提供者。
   * @param timestampAdjusterInitializationTimeoutMs 加载线程等待时间戳调整器初始化的超时时间（以毫秒为单位）。零超时表示无限超时。
   * @param muxedCaptionFormats 多路复用的字幕 {@link Format} 列表。如果多变量播放列表中没有隐藏字幕信息，则为 null。
   * @param playerId 使用此块源的播放器的 {@link PlayerId}。
   * @param cmcdConfiguration 此块源的 {@link CmcdConfiguration}。
   */
  public HlsChunkSource(
      HlsExtractorFactory extractorFactory,
      HlsPlaylistTracker playlistTracker,
      Uri[] playlistUrls,
      Format[] playlistFormats,
      HlsDataSourceFactory dataSourceFactory,
      @Nullable TransferListener mediaTransferListener,
      TimestampAdjusterProvider timestampAdjusterProvider,
      long timestampAdjusterInitializationTimeoutMs,
      @Nullable List<Format> muxedCaptionFormats,
      PlayerId playerId,
      @Nullable CmcdConfiguration cmcdConfiguration) {
    this.extractorFactory = extractorFactory;
    this.playlistTracker = playlistTracker;
    this.playlistUrls = playlistUrls;
    this.playlistFormats = playlistFormats;
    this.timestampAdjusterProvider = timestampAdjusterProvider;
    this.timestampAdjusterInitializationTimeoutMs = timestampAdjusterInitializationTimeoutMs;
    this.muxedCaptionFormats = muxedCaptionFormats;
    this.playerId = playerId;
    this.cmcdConfiguration = cmcdConfiguration;
    this.lastChunkRequestRealtimeMs = C.TIME_UNSET;
    keyCache = new FullSegmentEncryptionKeyCache(KEY_CACHE_SIZE);
    scratchSpace = Util.EMPTY_BYTE_ARRAY;
    liveEdgeInPeriodTimeUs = C.TIME_UNSET;
    mediaDataSource = dataSourceFactory.createDataSource(C.DATA_TYPE_MEDIA);
    if (mediaTransferListener != null) {
      mediaDataSource.addTransferListener(mediaTransferListener);
    }
    encryptionDataSource = dataSourceFactory.createDataSource(C.DATA_TYPE_DRM);
    trackGroup = new TrackGroup(playlistFormats);
    // 仅使用非 trickplay 变体进行准备。参见 [内部参考：b/161529098]。
    ArrayList<Integer> initialTrackSelection = new ArrayList<>();
    for (int i = 0; i < playlistUrls.length; i++) {
      if ((playlistFormats[i].roleFlags & C.ROLE_FLAG_TRICK_PLAY) == 0) {
        initialTrackSelection.add(i);
      }
    }
    trackSelection =
        new InitializationTrackSelection(trackGroup, Ints.toArray(initialTrackSelection));
  }

  /**
   * 如果源当前在提供块时遇到困难，则此方法会抛出底层错误。否则不执行任何操作。
   *
   * @throws IOException 底层错误。
   */
  public void maybeThrowError() throws IOException {
    if (fatalError != null) {
      throw fatalError;
    }
    if (expectedPlaylistUrl != null && seenExpectedPlaylistError) {
      playlistTracker.maybeThrowPlaylistRefreshError(expectedPlaylistUrl);
    }
  }

  /** 返回源暴露的轨道组。 */
  public TrackGroup getTrackGroup() {
    return trackGroup;
  }

  /** 返回块源是否具有独立的片段。 */
  public boolean hasIndependentSegments() {
    return independentSegments;
  }

  /**
   * 设置当前的轨道选择。
   *
   * @param trackSelection {@link ExoTrackSelection}。
   */
  public void setTrackSelection(ExoTrackSelection trackSelection) {
    // 为旧轨道选择停用所选播放列表以进行播放。
    deactivatePlaylistForSelectedTrack();
    this.trackSelection = trackSelection;
  }

  /** 返回当前的 {@link ExoTrackSelection}。 */
  public ExoTrackSelection getTrackSelection() {
    return trackSelection;
  }

  /** 重置源。 */
  public void reset() {
    deactivatePlaylistForSelectedTrack();
    fatalError = null;
  }

  /**
   * 设置此块源是否负责初始化时间戳调整器。
   *
   * @param isPrimaryTimestampSource 如果此块源负责初始化时间戳调整器，则为 true。
   */
  public void setIsPrimaryTimestampSource(boolean isPrimaryTimestampSource) {
    this.isPrimaryTimestampSource = isPrimaryTimestampSource;
  }

  /**
   * 根据指定的 {@link SeekParameters} 调整搜索位置。
   *
   * @param positionUs 搜索位置（以微秒为单位）。
   * @param seekParameters 控制搜索方式的参数。
   * @return 调整后的搜索位置（以微秒为单位）。
   */
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    int selectedIndex = trackSelection.getSelectedIndex();
    @Nullable
    HlsMediaPlaylist mediaPlaylist =
        selectedIndex < playlistUrls.length && selectedIndex != C.INDEX_UNSET
            ? playlistTracker.getPlaylistSnapshot(
                playlistUrls[trackSelection.getSelectedIndexInTrackGroup()],
                /* isForPlayback= */ true)
            : null;

    if (mediaPlaylist == null
        || mediaPlaylist.segments.isEmpty()
        || !mediaPlaylist.hasIndependentSegments) {
      return positionUs;
    }

    // 片段以同步样本开始（即设置了 EXT-X-INDEPENDENT-SEGMENTS），并且播放列表非空，因此我们可以使用片段的开始时间作为同步点。需要注意的是，在极少数情况下，
    // 如果在调整和实际执行搜索之间发生了自适应质量切换，并且不同变体之间的片段开始时间未对齐，则调整后的位置可能不会在预期的时间点处于同步点。然而，这种情况非常罕见，
    // 并且在绝大多数情况下保持正确的同时保持实现相对简单是值得的。
    long startOfPlaylistInPeriodUs =
        mediaPlaylist.startTimeUs - playlistTracker.getInitialStartTimeUs();
    long relativePositionUs = positionUs - startOfPlaylistInPeriodUs;
    int segmentIndex =
        Util.binarySearchFloor(
            mediaPlaylist.segments,
            relativePositionUs,
            /* inclusive= */ true,
            /* stayInBounds= */ true);
    long firstSyncUs = mediaPlaylist.segments.get(segmentIndex).relativeStartTimeUs;
    long secondSyncUs = firstSyncUs;
    if (segmentIndex != mediaPlaylist.segments.size() - 1) {
      secondSyncUs = mediaPlaylist.segments.get(segmentIndex + 1).relativeStartTimeUs;
    }
    return seekParameters.resolveSeekPositionUs(relativePositionUs, firstSyncUs, secondSyncUs)
        + startOfPlaylistInPeriodUs;
  }

  /**
   * 返回给定块的发布状态。
   *
   * @param mediaChunk 要评估发布状态的媒体块。
   * @return 媒体块是 {@link #CHUNK_PUBLICATION_STATE_PRELOAD 预加载块}，已被 {@link #CHUNK_PUBLICATION_STATE_REMOVED 移除}，
   * 还是明确 {@link #CHUNK_PUBLICATION_STATE_PUBLISHED 发布}。
   */
  public @ChunkPublicationState int getChunkPublicationState(HlsMediaChunk mediaChunk) {
    if (mediaChunk.partIndex == C.INDEX_UNSET) {
      // 基于完整片段的块不能被移除，并且始终是已发布的。
      return CHUNK_PUBLICATION_STATE_PUBLISHED;
    }
    Uri playlistUrl = playlistUrls[trackGroup.indexOf(mediaChunk.trackFormat)];
    HlsMediaPlaylist mediaPlaylist =
        checkNotNull(playlistTracker.getPlaylistSnapshot(playlistUrl, /* isForPlayback= */ false));
    int segmentIndexInPlaylist = (int) (mediaChunk.chunkIndex - mediaPlaylist.mediaSequence);
    if (segmentIndexInPlaylist < 0) {
      // 上一个块的父片段已不在当前播放列表中。
      return CHUNK_PUBLICATION_STATE_PUBLISHED;
    }
    List<HlsMediaPlaylist.Part> partsInCurrentPlaylist =
        segmentIndexInPlaylist < mediaPlaylist.segments.size()
            ? mediaPlaylist.segments.get(segmentIndexInPlaylist).parts
            : mediaPlaylist.trailingParts;
    if (mediaChunk.partIndex >= partsInCurrentPlaylist.size()) {
      // 如果在上一个播放列表中提示的部分被错误地分配给了当时完整但尚未终止的片段，我们无论如何都会丢弃它，无论 URI 是否不同。虽然这在理论上是可能的且未指定，
      // 但这似乎是一个边缘情况，我们可以通过稍微低效地无效丢弃来避免。我们可以在这里允许这种情况，但如果块没有被丢弃，它可能会在以后产生不可预测的问题，
      // 因为 previous.chunkIndex 中的媒体序列与新的播放列表中的实际媒体序列不匹配。
      return CHUNK_PUBLICATION_STATE_REMOVED;
    }
    HlsMediaPlaylist.Part newPart = partsInCurrentPlaylist.get(mediaChunk.partIndex);
    if (newPart.isPreload) {
      // 播放列表没有变化，并且新播放列表中的部分仍然是预加载提示。
      return CHUNK_PUBLICATION_STATE_PRELOAD;
    }
    Uri newUri = Uri.parse(UriUtil.resolve(mediaPlaylist.baseUri, newPart.url));
    return Util.areEqual(newUri, mediaChunk.dataSpec.uri)
        ? CHUNK_PUBLICATION_STATE_PUBLISHED
        : CHUNK_PUBLICATION_STATE_REMOVED;
  }

  /**
   * 返回下一个要加载的块。
   *
   * <p>如果块可用，则设置 {@link HlsChunkHolder#chunk}。如果已到达流的末尾，则设置 {@link HlsChunkHolder#endOfStream}。如果块不可用但尚未到达流的末尾，
   * 则设置 {@link HlsChunkHolder#playlistUrl} 以包含需要刷新的播放列表的 {@link Uri}。
   *
   * @param loadingInfo 发出加载请求时的 {@link LoadingInfo}。
   * @param loadPositionUs 当前加载位置相对于周期开始的微秒数。
   * @param queue 缓冲的 {@link HlsMediaChunk} 队列。
   * @param allowEndOfStream 是否允许为非空媒体播放列表设置 {@link HlsChunkHolder#endOfStream}。如果为 {@code false}，则返回最后一个可用的块。如果媒体播放列表为空，
   *     则始终设置 {@link HlsChunkHolder#endOfStream}。
   * @param out 要填充的持有者。
   */
  public void getNextChunk(
      LoadingInfo loadingInfo,
      long loadPositionUs,
      List<HlsMediaChunk> queue,
      boolean allowEndOfStream,
      HlsChunkHolder out) {
    @Nullable HlsMediaChunk previous = queue.isEmpty() ? null : Iterables.getLast(queue);
    int oldTrackIndex = previous == null ? C.INDEX_UNSET : trackGroup.indexOf(previous.trackFormat);
    long playbackPositionUs = loadingInfo.playbackPositionUs;
    long bufferedDurationUs = loadPositionUs - playbackPositionUs;
    long timeToLiveEdgeUs = resolveTimeToLiveEdgeUs(playbackPositionUs);
    if (previous != null && !independentSegments) {
      // 除非已知片段是独立的，否则切换轨道需要下载重叠的片段。因此，我们从缓冲的持续时间中减去上一个片段的持续时间。
      // 这可能会影响直播流的自适应轨道选择逻辑，因为我们在比较缓冲的持续时间与实时边缘时间以决定是否切换时，也会从 timeToLiveEdgeUs 中减去最后加载的片段的持续时间。
      long subtractedDurationUs = previous.getDurationUs();
      bufferedDurationUs = max(0, bufferedDurationUs - subtractedDurationUs);
      if (timeToLiveEdgeUs != C.TIME_UNSET) {
        timeToLiveEdgeUs = max(0, timeToLiveEdgeUs - subtractedDurationUs);
      }
    }

    // Select the track.
    MediaChunkIterator[] mediaChunkIterators = createMediaChunkIterators(previous, loadPositionUs);
    trackSelection.updateSelectedTrack(
        playbackPositionUs, bufferedDurationUs, timeToLiveEdgeUs, queue, mediaChunkIterators);
    int selectedTrackIndex = trackSelection.getSelectedIndexInTrackGroup();
    boolean switchingTrack = oldTrackIndex != selectedTrackIndex;
    Uri selectedPlaylistUrl = playlistUrls[selectedTrackIndex];
    if (!playlistTracker.isSnapshotValid(selectedPlaylistUrl)) {
      out.playlistUrl = selectedPlaylistUrl;
      seenExpectedPlaylistError &= selectedPlaylistUrl.equals(expectedPlaylistUrl);
      expectedPlaylistUrl = selectedPlaylistUrl;
      // 当播放列表刷新时重试。
      return;
    }
    @Nullable
    HlsMediaPlaylist playlist =
        playlistTracker.getPlaylistSnapshot(selectedPlaylistUrl, /* isForPlayback= */ true);
    // playlistTracker 的快照是有效的（由上面的 if() 检查），因此 playlist 必须为非空。
    checkNotNull(playlist);
    independentSegments = playlist.hasIndependentSegments;

    updateLiveEdgeTimeUs(playlist);

    // 选择块。
    long startOfPlaylistInPeriodUs = playlist.startTimeUs - playlistTracker.getInitialStartTimeUs();
    Pair<Long, Integer> nextMediaSequenceAndPartIndex =
        getNextMediaSequenceAndPartIndex(
            previous, switchingTrack, playlist, startOfPlaylistInPeriodUs, loadPositionUs);
    long chunkMediaSequence = nextMediaSequenceAndPartIndex.first;
    int partIndex = nextMediaSequenceAndPartIndex.second;
    if (chunkMediaSequence < playlist.mediaSequence && previous != null && switchingTrack) {
      // 我们尝试在不进行自适应的情况下获取下一个块，以防这是落后于直播窗口的原因。
      selectedTrackIndex = oldTrackIndex;
      selectedPlaylistUrl = playlistUrls[selectedTrackIndex];
      playlist =
          playlistTracker.getPlaylistSnapshot(selectedPlaylistUrl, /* isForPlayback= */ true);
      // playlistTracker 的快照是有效的（由上面的 if() 检查），因此 playlist 必须为非空。
      checkNotNull(playlist);
      startOfPlaylistInPeriodUs = playlist.startTimeUs - playlistTracker.getInitialStartTimeUs();
      // 在不切换轨道的情况下获取下一个片段/部分。
      Pair<Long, Integer> nextMediaSequenceAndPartIndexWithoutAdapting =
          getNextMediaSequenceAndPartIndex(
              previous,
              /* switchingTrack= */ false,
              playlist,
              startOfPlaylistInPeriodUs,
              loadPositionUs);
      chunkMediaSequence = nextMediaSequenceAndPartIndexWithoutAdapting.first;
      partIndex = nextMediaSequenceAndPartIndexWithoutAdapting.second;
    }

    // 如果选定的轨道索引从另一个索引更改，我们应该停用旧播放列表以进行播放。
    if (selectedTrackIndex != oldTrackIndex && oldTrackIndex != C.INDEX_UNSET) {
      Uri oldPlaylistUrl = playlistUrls[oldTrackIndex];
      playlistTracker.deactivatePlaylistForPlayback(oldPlaylistUrl);
    }

    if (chunkMediaSequence < playlist.mediaSequence) {
      fatalError = new BehindLiveWindowException();
      return;
    }

    @Nullable
    SegmentBaseHolder segmentBaseHolder =
        getNextSegmentHolder(playlist, chunkMediaSequence, partIndex);
    if (segmentBaseHolder == null) {
      if (!playlist.hasEndTag) {
        // 如果是直播流，则重新加载播放列表。
        out.playlistUrl = selectedPlaylistUrl;
        seenExpectedPlaylistError &= selectedPlaylistUrl.equals(expectedPlaylistUrl);
        expectedPlaylistUrl = selectedPlaylistUrl;
        return;
      } else if (allowEndOfStream || playlist.segments.isEmpty()) {
        out.endOfStream = true;
        return;
      }
      // 如果是 VOD 流，则使用播放列表中最后一个可用的片段。
      segmentBaseHolder =
          new SegmentBaseHolder(
              Iterables.getLast(playlist.segments),
              playlist.mediaSequence + playlist.segments.size() - 1,
              /* partIndex= */ C.INDEX_UNSET);
    }

    // 我们有一个有效的媒体片段，此时可以丢弃任何播放列表错误。
    seenExpectedPlaylistError = false;
    expectedPlaylistUrl = null;

    @Nullable CmcdData.Factory cmcdDataFactory = null;
    if (cmcdConfiguration != null) {
      cmcdDataFactory =
          new CmcdData.Factory(
                  cmcdConfiguration,
                  trackSelection,
                  max(0, bufferedDurationUs),
                  /* playbackRate= */ loadingInfo.playbackSpeed,
                  /* streamingFormat= */ CmcdData.Factory.STREAMING_FORMAT_HLS,
                  /* isLive= */ !playlist.hasEndTag,
                  /* didRebuffer= */ loadingInfo.rebufferedSince(lastChunkRequestRealtimeMs),
                  /* isBufferEmpty= */ queue.isEmpty())
              .setObjectType(
                  getIsMuxedAudioAndVideo()
                      ? CmcdData.Factory.OBJECT_TYPE_MUXED_AUDIO_AND_VIDEO
                      : CmcdData.Factory.getObjectType(trackSelection));

      long nextMediaSequence =
          segmentBaseHolder.partIndex == C.INDEX_UNSET
              ? segmentBaseHolder.mediaSequence + 1
              : segmentBaseHolder.mediaSequence;
      int nextPartIndex =
          segmentBaseHolder.partIndex == C.INDEX_UNSET
              ? C.INDEX_UNSET
              : segmentBaseHolder.partIndex + 1;
      SegmentBaseHolder nextSegmentBaseHolder =
          getNextSegmentHolder(playlist, nextMediaSequence, nextPartIndex);
      if (nextSegmentBaseHolder != null) {
        Uri uri = UriUtil.resolveToUri(playlist.baseUri, segmentBaseHolder.segmentBase.url);
        Uri nextUri = UriUtil.resolveToUri(playlist.baseUri, nextSegmentBaseHolder.segmentBase.url);
        cmcdDataFactory.setNextObjectRequest(UriUtil.getRelativePath(uri, nextUri));

        String nextRangeRequest = nextSegmentBaseHolder.segmentBase.byteRangeOffset + "-";
        if (nextSegmentBaseHolder.segmentBase.byteRangeLength != C.LENGTH_UNSET) {
          nextRangeRequest +=
              (nextSegmentBaseHolder.segmentBase.byteRangeOffset
                  + nextSegmentBaseHolder.segmentBase.byteRangeLength);
        }
        cmcdDataFactory.setNextRangeRequest(nextRangeRequest);
      }
    }
    lastChunkRequestRealtimeMs = SystemClock.elapsedRealtime();
    // 检查媒体片段或其初始化片段是否完全加密。
    @Nullable
    Uri initSegmentKeyUri =
        getFullEncryptionKeyUri(playlist, segmentBaseHolder.segmentBase.initializationSegment);
    out.chunk =
        maybeCreateEncryptionChunkFor(
            initSegmentKeyUri, selectedTrackIndex, /* isInitSegment= */ true, cmcdDataFactory);
    if (out.chunk != null) {
      return;
    }
    @Nullable
    Uri mediaSegmentKeyUri = getFullEncryptionKeyUri(playlist, segmentBaseHolder.segmentBase);
    out.chunk =
        maybeCreateEncryptionChunkFor(
            mediaSegmentKeyUri, selectedTrackIndex, /* isInitSegment= */ false, cmcdDataFactory);
    if (out.chunk != null) {
      return;
    }

    boolean shouldSpliceIn =
        HlsMediaChunk.shouldSpliceIn(
            previous, selectedPlaylistUrl, playlist, segmentBaseHolder, startOfPlaylistInPeriodUs);
    if (shouldSpliceIn && segmentBaseHolder.isPreload) {
      // 我们不支持丢弃拼接的片段 [内部：b/159904763]，但预加载的部分如果在被永久发布之前被移除，则可能需要被丢弃。因此，不允许这种组合，而是等待下一个部分完全可用后再加载
      // （或者轨道选择选择另一个轨道）。
      return;
    }

    out.chunk =
        HlsMediaChunk.createInstance(
            extractorFactory,
            mediaDataSource,
            playlistFormats[selectedTrackIndex],
            startOfPlaylistInPeriodUs,
            playlist,
            segmentBaseHolder,
            selectedPlaylistUrl,
            muxedCaptionFormats,
            trackSelection.getSelectionReason(),
            trackSelection.getSelectionData(),
            isPrimaryTimestampSource,
            timestampAdjusterProvider,
            timestampAdjusterInitializationTimeoutMs,
            previous,
            /* mediaSegmentKey= */ keyCache.get(mediaSegmentKeyUri),
            /* initSegmentKey= */ keyCache.get(initSegmentKeyUri),
            shouldSpliceIn,
            playerId,
            cmcdDataFactory);
  }

  private boolean getIsMuxedAudioAndVideo() {
    Format format = trackGroup.getFormat(trackSelection.getSelectedIndex());
    String audioMimeType = MimeTypes.getAudioMediaMimeType(format.codecs);
    String videoMimeType = MimeTypes.getVideoMediaMimeType(format.codecs);
    return audioMimeType != null && videoMimeType != null;
  }

  @Nullable
  private static SegmentBaseHolder getNextSegmentHolder(
      HlsMediaPlaylist mediaPlaylist, long nextMediaSequence, int nextPartIndex) {
    int segmentIndexInPlaylist = (int) (nextMediaSequence - mediaPlaylist.mediaSequence);
    if (segmentIndexInPlaylist == mediaPlaylist.segments.size()) {
      int index = nextPartIndex != C.INDEX_UNSET ? nextPartIndex : 0;
      return index < mediaPlaylist.trailingParts.size()
          ? new SegmentBaseHolder(mediaPlaylist.trailingParts.get(index), nextMediaSequence, index)
          : null;
    }

    Segment mediaSegment = mediaPlaylist.segments.get(segmentIndexInPlaylist);
    if (nextPartIndex == C.INDEX_UNSET) {
      return new SegmentBaseHolder(mediaSegment, nextMediaSequence, /* partIndex= */ C.INDEX_UNSET);
    }

    if (nextPartIndex < mediaSegment.parts.size()) {
      // 请求的部分在请求的片段中可用。
      return new SegmentBaseHolder(
          mediaSegment.parts.get(nextPartIndex), nextMediaSequence, nextPartIndex);
    } else if (segmentIndexInPlaylist + 1 < mediaPlaylist.segments.size()) {
      // 请求的是下一个片段的第一个部分，但我们可以使用下一个完整的片段。
      return new SegmentBaseHolder(
          mediaPlaylist.segments.get(segmentIndexInPlaylist + 1),
          nextMediaSequence + 1,
          /* partIndex= */ C.INDEX_UNSET);
    } else if (!mediaPlaylist.trailingParts.isEmpty()) {
      // 部分索引滚动到第一个尾部部分。
      return new SegmentBaseHolder(
          mediaPlaylist.trailingParts.get(0), nextMediaSequence + 1, /* partIndex= */ 0);
    }
    // 流结束。
    return null;
  }

  /**
   * 当 {@link HlsSampleStreamWrapper} 完成加载从此源获取的块时调用。
   *
   * @param chunk 已完成加载的块。
   */
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof EncryptionKeyChunk) {
      EncryptionKeyChunk encryptionKeyChunk = (EncryptionKeyChunk) chunk;
      scratchSpace = encryptionKeyChunk.getDataHolder();
      keyCache.put(encryptionKeyChunk.dataSpec.uri, checkNotNull(encryptionKeyChunk.getResult()));
    }
  }

  /**
   * 尝试排除与给定块关联的轨道。如果该轨道是选择中唯一未排除的轨道，则排除将失败。
   *
   * @param chunk 导致排除尝试的块。
   * @param exclusionDurationMs 轨道选择应被排除的毫秒数。
   * @return 排除是否成功。
   */
  public boolean maybeExcludeTrack(Chunk chunk, long exclusionDurationMs) {
    return trackSelection.excludeTrack(
        trackSelection.indexOf(trackGroup.indexOf(chunk.trackFormat)), exclusionDurationMs);
  }
  /**
   * 当播放列表加载遇到错误时调用。
   *
   * @param playlistUrl 加载遇到错误的播放列表的 {@link Uri}。
   * @param exclusionDurationMs 播放列表应被排除的持续时间。如果播放列表不应被排除，则为 {@link C#TIME_UNSET}。
   * @return 如果排除未遇到错误，则为 true。否则为 false。
   */
  public boolean onPlaylistError(Uri playlistUrl, long exclusionDurationMs) {
    int trackGroupIndex = C.INDEX_UNSET;
    for (int i = 0; i < playlistUrls.length; i++) {
      if (playlistUrls[i].equals(playlistUrl)) {
        trackGroupIndex = i;
        break;
      }
    }
    if (trackGroupIndex == C.INDEX_UNSET) {
      return true;
    }
    int trackSelectionIndex = trackSelection.indexOf(trackGroupIndex);
    if (trackSelectionIndex == C.INDEX_UNSET) {
      return true;
    }
    seenExpectedPlaylistError |= playlistUrl.equals(expectedPlaylistUrl);
    return exclusionDurationMs == C.TIME_UNSET
        || (trackSelection.excludeTrack(trackSelectionIndex, exclusionDurationMs)
        && playlistTracker.excludeMediaPlaylist(playlistUrl, exclusionDurationMs));
  }

  /**
   * 返回即将到来的媒体块的 {@link MediaChunkIterator} 数组。
   *
   * @param previous 上一个媒体块。可能为 null。
   * @param loadPositionUs 迭代器将开始的位置。
   * @return 每个轨道的 {@link MediaChunkIterator} 数组。
   */
  public MediaChunkIterator[] createMediaChunkIterators(
      @Nullable HlsMediaChunk previous, long loadPositionUs) {
    int oldTrackIndex = previous == null ? C.INDEX_UNSET : trackGroup.indexOf(previous.trackFormat);
    MediaChunkIterator[] chunkIterators = new MediaChunkIterator[trackSelection.length()];
    for (int i = 0; i < chunkIterators.length; i++) {
      int trackIndex = trackSelection.getIndexInTrackGroup(i);
      Uri playlistUrl = playlistUrls[trackIndex];
      if (!playlistTracker.isSnapshotValid(playlistUrl)) {
        chunkIterators[i] = MediaChunkIterator.EMPTY;
        continue;
      }
      @Nullable
      HlsMediaPlaylist playlist =
          playlistTracker.getPlaylistSnapshot(playlistUrl, /* isForPlayback= */ false);
      // 播放列表快照是有效的（由上面的 if() 检查），因此 playlist 必须为非空。
      checkNotNull(playlist);
      long startOfPlaylistInPeriodUs =
          playlist.startTimeUs - playlistTracker.getInitialStartTimeUs();
      boolean switchingTrack = trackIndex != oldTrackIndex;
      Pair<Long, Integer> chunkMediaSequenceAndPartIndex =
          getNextMediaSequenceAndPartIndex(
              previous, switchingTrack, playlist, startOfPlaylistInPeriodUs, loadPositionUs);
      long chunkMediaSequence = chunkMediaSequenceAndPartIndex.first;
      int partIndex = chunkMediaSequenceAndPartIndex.second;
      chunkIterators[i] =
          new HlsMediaPlaylistSegmentIterator(
              playlist.baseUri,
              startOfPlaylistInPeriodUs,
              getSegmentBaseList(playlist, chunkMediaSequence, partIndex));
    }
    return chunkIterators;
  }

  /**
   * 评估是否应从队列的后面移除 {@link MediaChunk}。
   *
   * <p>如果可以用质量显著更高的块替换队列后面的 {@link MediaChunk}（例如，因为可用带宽大幅增加），则移除它们可能是有用的。
   *
   * <p>只有在队列中没有 {@link MediaChunk} 正在加载时才会调用。
   *
   * @param playbackPositionUs 当前的播放位置，以微秒为单位。
   * @param queue 缓冲的 {@link MediaChunk} 队列。
   * @return 首选队列大小。
   */
  public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
    if (fatalError != null || trackSelection.length() < 2) {
      return queue.size();
    }
    return trackSelection.evaluateQueueSize(playbackPositionUs, queue);
  }

  /**
   * 返回是否应取消正在进行的块加载。
   *
   * @param playbackPositionUs 当前的播放位置，以微秒为单位。
   * @param loadingChunk 当前正在加载的 {@link Chunk}。
   * @param queue 缓冲的 {@link MediaChunk} 队列。
   * @return 是否应取消 {@code loadingChunk} 的正在进行的加载。
   */
  public boolean shouldCancelLoad(
      long playbackPositionUs, Chunk loadingChunk, List<? extends MediaChunk> queue) {
    if (fatalError != null) {
      return false;
    }
    return trackSelection.shouldCancelChunkLoad(playbackPositionUs, loadingChunk, queue);
  }

  // 包方法。

  /**
   * 返回从给定播放列表中的 {@code mediaSequence} 和 {@code partIndex} 开始的所有片段基的列表。如果起始点不在播放列表中，则列表可能为空。
   */
  @VisibleForTesting
  /* package */ static List<HlsMediaPlaylist.SegmentBase> getSegmentBaseList(
      HlsMediaPlaylist playlist, long mediaSequence, int partIndex) {
    int firstSegmentIndexInPlaylist = (int) (mediaSequence - playlist.mediaSequence);
    if (firstSegmentIndexInPlaylist < 0 || playlist.segments.size() < firstSegmentIndexInPlaylist) {
      // 第一个媒体序列不在播放列表中。
      return ImmutableList.of();
    }
    List<HlsMediaPlaylist.SegmentBase> segmentBases = new ArrayList<>();
    if (firstSegmentIndexInPlaylist < playlist.segments.size()) {
      if (partIndex != C.INDEX_UNSET) {
        // 迭代器从一个属于片段的部分开始。
        Segment firstSegment = playlist.segments.get(firstSegmentIndexInPlaylist);
        if (partIndex == 0) {
          // 使用完整片段而不是第一个部分。
          segmentBases.add(firstSegment);
        } else if (partIndex < firstSegment.parts.size()) {
          // 添加从第一个请求的片段开始的部分。
          segmentBases.addAll(firstSegment.parts.subList(partIndex, firstSegment.parts.size()));
        }
        firstSegmentIndexInPlaylist++;
      }
      partIndex = 0;
      // 添加所有剩余的片段。
      segmentBases.addAll(
          playlist.segments.subList(firstSegmentIndexInPlaylist, playlist.segments.size()));
    }

    if (playlist.partTargetDurationUs != C.TIME_UNSET) {
      // 这是一个低延迟播放列表。
      partIndex = partIndex == C.INDEX_UNSET ? 0 : partIndex;
      if (partIndex < playlist.trailingParts.size()) {
        segmentBases.addAll(
            playlist.trailingParts.subList(partIndex, playlist.trailingParts.size()));
      }
    }
    return Collections.unmodifiableList(segmentBases);
  }
  /** 返回此块源是否为给定 URL 的播放列表获取块。 */
  public boolean obtainsChunksForPlaylist(Uri playlistUrl) {
    return Util.contains(playlistUrls, playlistUrl);
  }

  // 私有方法。

  /**
   * 返回要在 {@code mediaPlaylist} 中加载的下一个媒体序列号和部分索引。
   *
   * @param previous 最后（至少部分）加载的片段。
   * @param switchingTrack 要加载的片段是否不是由同一轨道中的片段前导。
   * @param mediaPlaylist 要加载的片段所属的媒体播放列表。
   * @param startOfPlaylistInPeriodUs {@code mediaPlaylist} 相对于周期开始的起始时间（以微秒为单位）。
   * @param loadPositionUs 当前加载位置相对于周期开始的微秒数。
   * @return 要加载的媒体序列和部分索引。
   */
  private Pair<Long, Integer> getNextMediaSequenceAndPartIndex(
      @Nullable HlsMediaChunk previous,
      boolean switchingTrack,
      HlsMediaPlaylist mediaPlaylist,
      long startOfPlaylistInPeriodUs,
      long loadPositionUs) {
    if (previous == null || switchingTrack) {
      long endOfPlaylistInPeriodUs = startOfPlaylistInPeriodUs + mediaPlaylist.durationUs;
      long targetPositionInPeriodUs =
          (previous == null || independentSegments) ? loadPositionUs : previous.startTimeUs;
      if (!mediaPlaylist.hasEndTag && targetPositionInPeriodUs >= endOfPlaylistInPeriodUs) {
        // 如果播放列表太旧以至于不包含该块，我们需要刷新它。
        return new Pair<>(
            mediaPlaylist.mediaSequence + mediaPlaylist.segments.size(),
            /* partIndex */ C.INDEX_UNSET);
      }
      long targetPositionInPlaylistUs = targetPositionInPeriodUs - startOfPlaylistInPeriodUs;
      int segmentIndexInPlaylist =
          Util.binarySearchFloor(
              mediaPlaylist.segments,
              /* value= */ targetPositionInPlaylistUs,
              /* inclusive= */ true,
              /* stayInBounds= */ !playlistTracker.isLive() || previous == null);
      long mediaSequence = segmentIndexInPlaylist + mediaPlaylist.mediaSequence;
      int partIndex = C.INDEX_UNSET;
      if (segmentIndexInPlaylist >= 0) {
        // 如果我们在直播窗口内，我们尝试选择一个部分（如果可用）。
        Segment segment = mediaPlaylist.segments.get(segmentIndexInPlaylist);
        List<HlsMediaPlaylist.Part> parts =
            targetPositionInPlaylistUs < segment.relativeStartTimeUs + segment.durationUs
                ? segment.parts
                : mediaPlaylist.trailingParts;
        for (int i = 0; i < parts.size(); i++) {
          HlsMediaPlaylist.Part part = parts.get(i);
          if (targetPositionInPlaylistUs < part.relativeStartTimeUs + part.durationUs) {
            if (part.isIndependent) {
              partIndex = i;
              // 如果该部分是尾部部分，则将媒体序列增加一。
              mediaSequence += parts == mediaPlaylist.trailingParts ? 1 : 0;
            }
            break;
          }
        }
      }
      return new Pair<>(mediaSequence, partIndex);
    }
    // 如果加载未完成，我们返回上一个块。
    return (previous.isLoadCompleted()
        ? new Pair<>(
        previous.partIndex == C.INDEX_UNSET
            ? previous.getNextChunkIndex()
            : previous.chunkIndex,
        previous.partIndex == C.INDEX_UNSET ? C.INDEX_UNSET : previous.partIndex + 1)
        : new Pair<>(previous.chunkIndex, previous.partIndex));
  }

  private long resolveTimeToLiveEdgeUs(long playbackPositionUs) {
    final boolean resolveTimeToLiveEdgePossible = liveEdgeInPeriodTimeUs != C.TIME_UNSET;
    return resolveTimeToLiveEdgePossible
        ? liveEdgeInPeriodTimeUs - playbackPositionUs
        : C.TIME_UNSET;
  }

  private void updateLiveEdgeTimeUs(HlsMediaPlaylist mediaPlaylist) {
    liveEdgeInPeriodTimeUs =
        mediaPlaylist.hasEndTag
            ? C.TIME_UNSET
            : (mediaPlaylist.getEndTimeUs() - playlistTracker.getInitialStartTimeUs());
  }

  @Nullable
  private Chunk maybeCreateEncryptionChunkFor(
      @Nullable Uri keyUri,
      int selectedTrackIndex,
      boolean isInitSegment,
      @Nullable CmcdData.Factory cmcdDataFactory) {
    if (keyUri == null) {
      return null;
    }

    @Nullable byte[] encryptionKey = keyCache.remove(keyUri);
    if (encryptionKey != null) {
      // 密钥存在于密钥缓存中。我们重新插入它以防止它被后续的密钥添加所驱逐。注意，移除密钥是必要的，以影响驱逐顺序。
      keyCache.put(keyUri, encryptionKey);
      return null;
    }

    DataSpec dataSpec =
        new DataSpec.Builder().setUri(keyUri).setFlags(DataSpec.FLAG_ALLOW_GZIP).build();
    if (cmcdDataFactory != null) {
      if (isInitSegment) {
        cmcdDataFactory.setObjectType(CmcdData.Factory.OBJECT_TYPE_INIT_SEGMENT);
      }
      CmcdData cmcdData = cmcdDataFactory.createCmcdData();
      dataSpec = cmcdData.addToDataSpec(dataSpec);
    }

    return new EncryptionKeyChunk(
        encryptionDataSource,
        dataSpec,
        playlistFormats[selectedTrackIndex],
        trackSelection.getSelectionReason(),
        trackSelection.getSelectionData(),
        scratchSpace);
  }

  @Nullable
  private static Uri getFullEncryptionKeyUri(
      HlsMediaPlaylist playlist, @Nullable HlsMediaPlaylist.SegmentBase segmentBase) {
    if (segmentBase == null || segmentBase.fullSegmentEncryptionKeyUri == null) {
      return null;
    }
    return UriUtil.resolveToUri(playlist.baseUri, segmentBase.fullSegmentEncryptionKeyUri);
  }

  private void deactivatePlaylistForSelectedTrack() {
    int selectedTrackIndex = this.trackSelection.getSelectedIndexInTrackGroup();
    playlistTracker.deactivatePlaylistForPlayback(playlistUrls[selectedTrackIndex]);
  }

  // Package classes.

  /* package */ static final class SegmentBaseHolder {

    public final HlsMediaPlaylist.SegmentBase segmentBase;
    public final long mediaSequence;
    public final int partIndex;
    public final boolean isPreload;

    /** Creates a new instance. */
    public SegmentBaseHolder(
        HlsMediaPlaylist.SegmentBase segmentBase, long mediaSequence, int partIndex) {
      this.segmentBase = segmentBase;
      this.mediaSequence = mediaSequence;
      this.partIndex = partIndex;
      this.isPreload =
          segmentBase instanceof HlsMediaPlaylist.Part
              && ((HlsMediaPlaylist.Part) segmentBase).isPreload;
    }
  }

  // Private classes.

  /** A {@link ExoTrackSelection} to use for initialization. */
  private static final class InitializationTrackSelection extends BaseTrackSelection {

    private int selectedIndex;

    public InitializationTrackSelection(TrackGroup group, int[] tracks) {
      super(group, tracks);
      // 最初选择的索引对应于多变量播放列表中的第一个 EXT-X-STREAMINF 标签。
      selectedIndex = indexOf(group.getFormat(tracks[0]));
    }

    @Override
    public void updateSelectedTrack(
        long playbackPositionUs,
        long bufferedDurationUs,
        long availableDurationUs,
        List<? extends MediaChunk> queue,
        MediaChunkIterator[] mediaChunkIterators) {
      long nowMs = SystemClock.elapsedRealtime();
      if (!isTrackExcluded(selectedIndex, nowMs)) {
        return;
      }
      // 从最低码率到最高码率尝试。
      for (int i = length - 1; i >= 0; i--) {
        if (!isTrackExcluded(i, nowMs)) {
          selectedIndex = i;
          return;
        }
      }
      // 这种情况不应发生。
      throw new IllegalStateException();
    }

    @Override
    public int getSelectedIndex() {
      return selectedIndex;
    }

    @Override
    public @C.SelectionReason int getSelectionReason() {
      return C.SELECTION_REASON_UNKNOWN;
    }

    @Override
    @Nullable
    public Object getSelectionData() {
      return null;
    }
  }

  private static final class EncryptionKeyChunk extends DataChunk {

    private byte @MonotonicNonNull [] result;

    public EncryptionKeyChunk(
        DataSource dataSource,
        DataSpec dataSpec,
        Format trackFormat,
        @C.SelectionReason int trackSelectionReason,
        @Nullable Object trackSelectionData,
        byte[] scratchSpace) {
      super(
          dataSource,
          dataSpec,
          C.DATA_TYPE_DRM,
          trackFormat,
          trackSelectionReason,
          trackSelectionData,
          scratchSpace);
    }

    @Override
    protected void consume(byte[] data, int limit) {
      result = Arrays.copyOf(data, limit);
    }

    /** 返回此块的结果，如果加载未完成则返回 null。 */
    @Nullable
    public byte[] getResult() {
      return result;
    }
  }

  @VisibleForTesting
  /* package */ static final class HlsMediaPlaylistSegmentIterator extends BaseMediaChunkIterator {

    private final List<HlsMediaPlaylist.SegmentBase> segmentBases;
    private final long startOfPlaylistInPeriodUs;
    private final String playlistBaseUri;

    /**
     * 创建一个包装了 {@link HlsMediaPlaylist.SegmentBase} 列表的迭代器实例。
     *
     * @param playlistBaseUri {@link HlsMediaPlaylist} 的基础 URI。
     * @param startOfPlaylistInPeriodUs 播放列表在周期中的开始时间，以微秒为单位。
     * @param segmentBases 要包装的 {@link HlsMediaPlaylist.SegmentBase} 列表。
     */
    public HlsMediaPlaylistSegmentIterator(
        String playlistBaseUri,
        long startOfPlaylistInPeriodUs,
        List<HlsMediaPlaylist.SegmentBase> segmentBases) {
      super(/* fromIndex= */ 0, segmentBases.size() - 1);
      this.playlistBaseUri = playlistBaseUri;
      this.startOfPlaylistInPeriodUs = startOfPlaylistInPeriodUs;
      this.segmentBases = segmentBases;
    }

    @Override
    public DataSpec getDataSpec() {
      checkInBounds();
      HlsMediaPlaylist.SegmentBase segmentBase = segmentBases.get((int) getCurrentIndex());
      Uri chunkUri = UriUtil.resolveToUri(playlistBaseUri, segmentBase.url);
      return new DataSpec(chunkUri, segmentBase.byteRangeOffset, segmentBase.byteRangeLength);
    }

    @Override
    public long getChunkStartTimeUs() {
      checkInBounds();
      return startOfPlaylistInPeriodUs
          + segmentBases.get((int) getCurrentIndex()).relativeStartTimeUs;
    }

    @Override
    public long getChunkEndTimeUs() {
      checkInBounds();
      HlsMediaPlaylist.SegmentBase segmentBase = segmentBases.get((int) getCurrentIndex());
      long segmentStartTimeInPeriodUs = startOfPlaylistInPeriodUs + segmentBase.relativeStartTimeUs;
      return segmentStartTimeInPeriodUs + segmentBase.durationUs;
    }
  }
}
