package androidx.media3.exoplayer.hls;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaItem.LiveConfiguration;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.StreamKey;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory;
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker;
import androidx.media3.exoplayer.hls.playlist.FilteringHlsPlaylistParserFactory;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker;
import androidx.media3.exoplayer.source.BaseMediaSource;
import androidx.media3.exoplayer.source.CompositeSequenceableLoaderFactory;
import androidx.media3.exoplayer.source.DefaultCompositeSequenceableLoaderFactory;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.source.MediaSourceFactory;
import androidx.media3.exoplayer.source.SequenceableLoader;
import androidx.media3.exoplayer.source.SinglePeriodTimeline;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

/** An HLS {@link MediaSource}. */
@UnstableApi
public final class HlsMediaSource extends BaseMediaSource
    implements HlsPlaylistTracker.PrimaryPlaylistListener {

  static {
    MediaLibraryInfo.registerModule("media3.exoplayer.hls");
  }

  /**
   * 可以从 HLS 流中提取的元数据类型。
   *
   * <p>允许的值：
   *
   * <ul>
   *   <li>{@link #METADATA_TYPE_ID3}
   *   <li>{@link #METADATA_TYPE_EMSG}
   * </ul>
   *
   * <p>参见 {@link Factory#setMetadataType(int)}。
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({METADATA_TYPE_ID3, METADATA_TYPE_EMSG})
  public @interface MetadataType {}

  /** HLS 流中 ID3 元数据的类型。 */
  public static final int METADATA_TYPE_ID3 = 1;

  /** HLS 流中 EMSG 元数据的类型。 */
  public static final int METADATA_TYPE_EMSG = 3;

  /** {@link HlsMediaSource} 的工厂。 */
  @SuppressWarnings("deprecation") // 实现已弃用的类型以保持向后兼容性。
  public static final class Factory implements MediaSourceFactory {

    private final HlsDataSourceFactory hlsDataSourceFactory;

    @Nullable private HlsExtractorFactory extractorFactory;
    @Nullable private SubtitleParser.Factory subtitleParserFactoryOverride;
    private boolean parseSubtitlesDuringExtraction;
    private HlsPlaylistParserFactory playlistParserFactory;
    private HlsPlaylistTracker.Factory playlistTrackerFactory;
    private CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    @Nullable private CmcdConfiguration.Factory cmcdConfigurationFactory;
    private DrmSessionManagerProvider drmSessionManagerProvider;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;

    private boolean allowChunklessPreparation;
    private @MetadataType int metadataType;
    private boolean useSessionKeys;
    private long elapsedRealTimeOffsetMs;
    private long timestampAdjusterInitializationTimeoutMs;

    /**
     * 创建一个新的 {@link HlsMediaSource} 工厂。
     *
     * <p>工厂将使用以下默认组件：
     *
     * <ul>
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultHlsPlaylistParserFactory}
     *   <li>{@link DefaultHlsPlaylistTracker#FACTORY}
     *   <li>{@link DefaultHlsExtractorFactory}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     *   <li>{@link DefaultCompositeSequenceableLoaderFactory}
     * </ul>
     *
     * @param dataSourceFactory 数据源工厂，将由 {@link DefaultHlsDataSourceFactory} 包装以创建用于清单、片段和密钥的 {@link DataSource}。
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(new DefaultHlsDataSourceFactory(dataSourceFactory));
    }

    /**
     * 创建一个新的 {@link HlsMediaSource} 工厂。
     *
     * <p>工厂将使用以下默认组件：
     *
     * <ul>
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultHlsPlaylistParserFactory}
     *   <li>{@link DefaultHlsPlaylistTracker#FACTORY}
     *   <li>{@link DefaultHlsExtractorFactory}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     *   <li>{@link DefaultCompositeSequenceableLoaderFactory}
     * </ul>
     *
     * @param hlsDataSourceFactory 用于清单、片段和密钥的 {@link DataSource} 的 {@link HlsDataSourceFactory}。
     */
    public Factory(HlsDataSourceFactory hlsDataSourceFactory) {
      this.hlsDataSourceFactory = checkNotNull(hlsDataSourceFactory);
      drmSessionManagerProvider = new DefaultDrmSessionManagerProvider();
      playlistParserFactory = new DefaultHlsPlaylistParserFactory();
      playlistTrackerFactory = DefaultHlsPlaylistTracker.FACTORY;
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
      compositeSequenceableLoaderFactory = new DefaultCompositeSequenceableLoaderFactory();
      metadataType = METADATA_TYPE_ID3;
      elapsedRealTimeOffsetMs = C.TIME_UNSET;
      allowChunklessPreparation = true;
      experimentalParseSubtitlesDuringExtraction(true);
    }

    /**
     * 设置用于片段的 {@link Extractor} 的工厂。默认值为 {@link DefaultHlsExtractorFactory}。
     *
     * <p>在 {@link #createMediaSource} 期间，传递给 {@link #setSubtitleParserFactory} 或 {@link
     * #experimentalParseSubtitlesDuringExtraction} 的任何值都将转发给提供的 {@link HlsExtractorFactory} 实例。
     *
     * @param extractorFactory 用于片段的 {@link Extractor} 的 {@link HlsExtractorFactory}。
     * @return 为了方便，返回此工厂。
     */
    @CanIgnoreReturnValue
    public Factory setExtractorFactory(@Nullable HlsExtractorFactory extractorFactory) {
      this.extractorFactory = extractorFactory;
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      this.loadErrorHandlingPolicy =
          checkNotNull(
              loadErrorHandlingPolicy,
              "MediaSource.Factory#setLoadErrorHandlingPolicy 不再通过实例化新的 DefaultLoadErrorHandlingPolicy 来处理 null。"
                  + "显式构造并传递一个实例以保留旧行为。");
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory setSubtitleParserFactory(SubtitleParser.Factory subtitleParserFactory) {
      this.subtitleParserFactoryOverride = subtitleParserFactory;
      return this;
    }

    @Override
    @Deprecated
    @CanIgnoreReturnValue
    public Factory experimentalParseSubtitlesDuringExtraction(
        boolean parseSubtitlesDuringExtraction) {
      this.parseSubtitlesDuringExtraction = parseSubtitlesDuringExtraction;
      return this;
    }

    /**
     * 设置从中获取播放列表解析器的工厂。
     *
     * @param playlistParserFactory 一个 {@link HlsPlaylistParserFactory}。
     * @return 为了方便，返回此工厂。
     */
    @CanIgnoreReturnValue
    public Factory setPlaylistParserFactory(HlsPlaylistParserFactory playlistParserFactory) {
      this.playlistParserFactory =
          checkNotNull(
              playlistParserFactory,
              "HlsMediaSource.Factory#setPlaylistParserFactory 不再通过实例化新的 DefaultHlsPlaylistParserFactory 来处理 null。"
                  + "显式构造并传递一个实例以保留旧行为。");
      return this;
    }

    /**
     * 设置 {@link HlsPlaylistTracker} 工厂。
     *
     * @param playlistTrackerFactory 用于创建 {@link HlsPlaylistTracker} 实例的工厂。
     * @return 为了方便，返回此工厂。
     */
    @CanIgnoreReturnValue
    public Factory setPlaylistTrackerFactory(HlsPlaylistTracker.Factory playlistTrackerFactory) {
      this.playlistTrackerFactory =
          checkNotNull(
              playlistTrackerFactory,
              "HlsMediaSource.Factory#setPlaylistTrackerFactory 不再通过默认使用 DefaultHlsPlaylistTracker.FACTORY 来处理 null。"
                  + "显式传递对此实例的引用以保留旧行为。");
      return this;
    }

    /**
     * 设置用于创建复合 {@link SequenceableLoader} 的工厂，当此媒体源从多个流（视频、音频等）加载数据时使用。
     *
     * @param compositeSequenceableLoaderFactory 用于创建复合 {@link SequenceableLoader} 的工厂，当此媒体源从多个流（视频、音频等）加载数据时使用。
     * @return 为了方便，返回此工厂。
     */
    @CanIgnoreReturnValue
    public Factory setCompositeSequenceableLoaderFactory(
        CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory) {
      this.compositeSequenceableLoaderFactory =
          checkNotNull(
              compositeSequenceableLoaderFactory,
              "HlsMediaSource.Factory#setCompositeSequenceableLoaderFactory 不再通过实例化新的 DefaultCompositeSequenceableLoaderFactory 来处理 null。显式构造并传递一个实例以保留旧行为。");
      return this;
    }

    /**
     * 设置是否允许无块准备。如果为 true，则对于在其多变量播放列表中提供足够信息的流，将启用无需下载块的准备。
     *
     * @param allowChunklessPreparation 是否允许无块准备。
     * @return 为了方便，返回此工厂。
     */
    @CanIgnoreReturnValue
    public Factory setAllowChunklessPreparation(boolean allowChunklessPreparation) {
      this.allowChunklessPreparation = allowChunklessPreparation;
      return this;
    }

    /**
     * 设置从 HLS 源中提取的元数据类型（默认为 {@link #METADATA_TYPE_ID3}）。
     *
     * <p>HLS 支持在 TS 和 fMP4 流中嵌入 ID3 元数据，但在 fMP4 情况下，数据被包装在 EMSG 盒子中 [<a href="https://aomediacodec.github.io/av1-id3/">规范</a>]。
     *
     * <p>如果设置为 {@link #METADATA_TYPE_ID3}，则将从 TS 源中提取原始 ID3 元数据。对于 fMP4 流，包含此类元数据的 EMSG（仅在变体流中）将被解包以暴露内部数据。所有其他嵌入的元数据将被丢弃。
     *
     * <p>如果设置为 {@link #METADATA_TYPE_EMSG}，则将从 fMP4 变体流中提取所有 EMSG 数据。TS 流不会提取任何元数据，因为它们不支持 EMSG。
     *
     * @param metadataType 要提取的元数据类型。
     * @return 为了方便，返回此工厂。
     */
    @CanIgnoreReturnValue
    public Factory setMetadataType(@MetadataType int metadataType) {
      this.metadataType = metadataType;
      return this;
    }

    /**
     * 设置是否使用多变量播放列表中提供的 #EXT-X-SESSION-KEY 标签。
     * 如果启用，则假定多变量播放列表中声明的任何单个会话密钥都可以用于获取播放所需的所有密钥
     * 。对于不满足此条件的媒体，不应启用此选项。
     *
     * @param useSessionKeys 是否使用 #EXT-X-SESSION-KEY 标签。
     * @return 为了方便，返回此工厂。
     */
    @CanIgnoreReturnValue
    public Factory setUseSessionKeys(boolean useSessionKeys) {
      this.useSessionKeys = useSessionKeys;
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory setCmcdConfigurationFactory(CmcdConfiguration.Factory cmcdConfigurationFactory) {
      this.cmcdConfigurationFactory = checkNotNull(cmcdConfigurationFactory);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory setDrmSessionManagerProvider(
        DrmSessionManagerProvider drmSessionManagerProvider) {
      this.drmSessionManagerProvider =
          checkNotNull(
              drmSessionManagerProvider,
              "MediaSource.Factory#setDrmSessionManagerProvider 不再通过实例化新的 DefaultDrmSessionManagerProvider 来处理 null。"
                  + "显式构造并传递一个实例以保留旧行为。");
      return this;
    }

    /**
     * 设置加载线程等待时间戳调整器初始化的超时时间（以毫秒为单位）。默认值为零，表示无限超时。
     *
     * @param timestampAdjusterInitializationTimeoutMs 超时时间（以毫秒为单位）。零超时表示无限超时。
     * @return 为了方便，返回此工厂。
     */
    @CanIgnoreReturnValue
    public Factory setTimestampAdjusterInitializationTimeoutMs(
        long timestampAdjusterInitializationTimeoutMs) {
      this.timestampAdjusterInitializationTimeoutMs = timestampAdjusterInitializationTimeoutMs;
      return this;
    }

    /**
     * 设置 {@link SystemClock#elapsedRealtime()} 与 Unix 纪元以来的时间之间的偏移量。默认情况下，它设置为 {@link C#TIME_UNSET}。
     *
     * @param elapsedRealTimeOffsetMs {@link SystemClock#elapsedRealtime()} 与 Unix 纪元以来的时间之间的偏移量（以毫秒为单位）。
     * @return 为了方便，返回此工厂。
     */
    @CanIgnoreReturnValue
    @VisibleForTesting
    /* package */ Factory setElapsedRealTimeOffsetMs(long elapsedRealTimeOffsetMs) {
      this.elapsedRealTimeOffsetMs = elapsedRealTimeOffsetMs;
      return this;
    }

    /**
     * 使用当前参数返回一个新的 {@link HlsMediaSource}。
     *
     * @param mediaItem {@link MediaItem}。
     * @return 新的 {@link HlsMediaSource}。
     * @throws NullPointerException 如果 {@link MediaItem#localConfiguration} 为 {@code null}。
     */
    @Override
    public HlsMediaSource createMediaSource(MediaItem mediaItem) {
      checkNotNull(mediaItem.localConfiguration);
      if (extractorFactory == null) {
        extractorFactory = new DefaultHlsExtractorFactory();
      }
      if (subtitleParserFactoryOverride != null) {
        extractorFactory.setSubtitleParserFactory(subtitleParserFactoryOverride);
      }
      extractorFactory.experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction);
      HlsExtractorFactory extractorFactory = this.extractorFactory;
      HlsPlaylistParserFactory playlistParserFactory = this.playlistParserFactory;
      List<StreamKey> streamKeys = mediaItem.localConfiguration.streamKeys;
      if (!streamKeys.isEmpty()) {
        playlistParserFactory =
            new FilteringHlsPlaylistParserFactory(playlistParserFactory, streamKeys);
      }
      @Nullable
      CmcdConfiguration cmcdConfiguration =
          cmcdConfigurationFactory == null
              ? null
              : cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);

      return new HlsMediaSource(
          mediaItem,
          hlsDataSourceFactory,
          extractorFactory,
          compositeSequenceableLoaderFactory,
          cmcdConfiguration,
          drmSessionManagerProvider.get(mediaItem),
          loadErrorHandlingPolicy,
          playlistTrackerFactory.createTracker(
              hlsDataSourceFactory, loadErrorHandlingPolicy, playlistParserFactory),
          elapsedRealTimeOffsetMs,
          allowChunklessPreparation,
          metadataType,
          useSessionKeys,
          timestampAdjusterInitializationTimeoutMs);
    }

    @Override
    public @C.ContentType int[] getSupportedTypes() {
      return new int[] {C.CONTENT_TYPE_HLS};
    }
  }

  private final HlsExtractorFactory extractorFactory;
  private final HlsDataSourceFactory dataSourceFactory;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  @Nullable private final CmcdConfiguration cmcdConfiguration;
  private final DrmSessionManager drmSessionManager;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final boolean allowChunklessPreparation;
  private final @MetadataType int metadataType;
  private final boolean useSessionKeys;
  private final HlsPlaylistTracker playlistTracker;
  private final long elapsedRealTimeOffsetMs;
  private final long timestampAdjusterInitializationTimeoutMs;

  private MediaItem.LiveConfiguration liveConfiguration;
  @Nullable private TransferListener mediaTransferListener;

  @GuardedBy("this")
  private MediaItem mediaItem;

  private HlsMediaSource(
      MediaItem mediaItem,
      HlsDataSourceFactory dataSourceFactory,
      HlsExtractorFactory extractorFactory,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      @Nullable CmcdConfiguration cmcdConfiguration,
      DrmSessionManager drmSessionManager,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      HlsPlaylistTracker playlistTracker,
      long elapsedRealTimeOffsetMs,
      boolean allowChunklessPreparation,
      @MetadataType int metadataType,
      boolean useSessionKeys,
      long timestampAdjusterInitializationTimeoutMs) {
    this.mediaItem = mediaItem;
    this.liveConfiguration = mediaItem.liveConfiguration;
    this.dataSourceFactory = dataSourceFactory;
    this.extractorFactory = extractorFactory;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.cmcdConfiguration = cmcdConfiguration;
    this.drmSessionManager = drmSessionManager;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.playlistTracker = playlistTracker;
    this.elapsedRealTimeOffsetMs = elapsedRealTimeOffsetMs;
    this.allowChunklessPreparation = allowChunklessPreparation;
    this.metadataType = metadataType;
    this.useSessionKeys = useSessionKeys;
    this.timestampAdjusterInitializationTimeoutMs = timestampAdjusterInitializationTimeoutMs;
  }

  @Override
  public synchronized MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  public boolean canUpdateMediaItem(MediaItem mediaItem) {
    MediaItem existingMediaItem = getMediaItem();
    MediaItem.LocalConfiguration existingConfiguration =
        checkNotNull(existingMediaItem.localConfiguration);
    @Nullable MediaItem.LocalConfiguration newConfiguration = mediaItem.localConfiguration;
    return newConfiguration != null
        && newConfiguration.uri.equals(existingConfiguration.uri)
        && newConfiguration.streamKeys.equals(existingConfiguration.streamKeys)
        && Util.areEqual(newConfiguration.drmConfiguration, existingConfiguration.drmConfiguration)
        && existingMediaItem.liveConfiguration.equals(mediaItem.liveConfiguration);
  }

  @Override
  public synchronized void updateMediaItem(MediaItem mediaItem) {
    this.mediaItem = mediaItem;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    this.mediaTransferListener = mediaTransferListener;
    drmSessionManager.setPlayer(
        /* playbackLooper= */ checkNotNull(Looper.myLooper()), getPlayerId());
    drmSessionManager.prepare();
    MediaSourceEventListener.EventDispatcher eventDispatcher =
        createEventDispatcher(/* mediaPeriodId= */ null);
    playlistTracker.start(
        checkNotNull(getMediaItem().localConfiguration).uri,
        eventDispatcher,
        /* primaryPlaylistListener= */ this);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    playlistTracker.maybeThrowPrimaryPlaylistRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher = createEventDispatcher(id);
    DrmSessionEventListener.EventDispatcher drmEventDispatcher = createDrmEventDispatcher(id);
    return new HlsMediaPeriod(
        extractorFactory,
        playlistTracker,
        dataSourceFactory,
        mediaTransferListener,
        cmcdConfiguration,
        drmSessionManager,
        drmEventDispatcher,
        loadErrorHandlingPolicy,
        mediaSourceEventDispatcher,
        allocator,
        compositeSequenceableLoaderFactory,
        allowChunklessPreparation,
        metadataType,
        useSessionKeys,
        getPlayerId(),
        timestampAdjusterInitializationTimeoutMs);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((HlsMediaPeriod) mediaPeriod).release();
  }

  @Override
  protected void releaseSourceInternal() {
    playlistTracker.stop();
    drmSessionManager.release();
  }

  @Override
  public void onPrimaryPlaylistRefreshed(HlsMediaPlaylist mediaPlaylist) {
    long windowStartTimeMs =
        mediaPlaylist.hasProgramDateTime ? Util.usToMs(mediaPlaylist.startTimeUs) : C.TIME_UNSET;
    // 对于 EVENT 和 VOD 类型的播放列表，我们知道片段永远不会被移除，因此演示的开始时间与窗口的开始时间相同。否则，我们不知道演示的开始时间。
    long presentationStartTimeMs =
        mediaPlaylist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_EVENT
            || mediaPlaylist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_VOD
            ? windowStartTimeMs
            : C.TIME_UNSET;
    // 多变量播放列表非空，因为此时第一个播放列表已经被获取。
    HlsManifest manifest =
        new HlsManifest(checkNotNull(playlistTracker.getMultivariantPlaylist()), mediaPlaylist);
    SinglePeriodTimeline timeline =
        playlistTracker.isLive()
            ? createTimelineForLive(
            mediaPlaylist, presentationStartTimeMs, windowStartTimeMs, manifest)
            : createTimelineForOnDemand(
                mediaPlaylist, presentationStartTimeMs, windowStartTimeMs, manifest);
    refreshSourceInfo(timeline);
  }

  private SinglePeriodTimeline createTimelineForLive(
      HlsMediaPlaylist playlist,
      long presentationStartTimeMs,
      long windowStartTimeMs,
      HlsManifest manifest) {
    long offsetFromInitialStartTimeUs =
        playlist.startTimeUs - playlistTracker.getInitialStartTimeUs();
    long periodDurationUs =
        playlist.hasEndTag ? offsetFromInitialStartTimeUs + playlist.durationUs : C.TIME_UNSET;
    long liveEdgeOffsetUs = getLiveEdgeOffsetUs(playlist);
    long targetLiveOffsetUs;
    if (liveConfiguration.targetOffsetMs != C.TIME_UNSET) {
      // 媒体项具有定义的目标偏移量。
      targetLiveOffsetUs = Util.msToUs(liveConfiguration.targetOffsetMs);
    } else {
      // 从播放列表决定目标偏移量。
      targetLiveOffsetUs = getTargetLiveOffsetUs(playlist, liveEdgeOffsetUs);
    }
    // 确保目标实时偏移量在实时窗口内并且大于实时边缘偏移量。
    targetLiveOffsetUs =
        Util.constrainValue(
            targetLiveOffsetUs, liveEdgeOffsetUs, playlist.durationUs + liveEdgeOffsetUs);
    updateLiveConfiguration(playlist, targetLiveOffsetUs);
    long windowDefaultStartPositionUs =
        getLiveWindowDefaultStartPositionUs(playlist, liveEdgeOffsetUs);
    boolean suppressPositionProjection =
        playlist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_EVENT
            && playlist.hasPositiveStartOffset;
    return new SinglePeriodTimeline(
        presentationStartTimeMs,
        windowStartTimeMs,
        /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
        periodDurationUs,
        /* windowDurationUs= */ playlist.durationUs,
        /* windowPositionInPeriodUs= */ offsetFromInitialStartTimeUs,
        windowDefaultStartPositionUs,
        /* isSeekable= */ true,
        /* isDynamic= */ !playlist.hasEndTag,
        suppressPositionProjection,
        manifest,
        getMediaItem(),
        liveConfiguration);
  }

  private SinglePeriodTimeline createTimelineForOnDemand(
      HlsMediaPlaylist playlist,
      long presentationStartTimeMs,
      long windowStartTimeMs,
      HlsManifest manifest) {
    long windowDefaultStartPositionUs;
    if (playlist.startOffsetUs == C.TIME_UNSET || playlist.segments.isEmpty()) {
      windowDefaultStartPositionUs = 0;
    } else {
      if (playlist.preciseStart || playlist.startOffsetUs == playlist.durationUs) {
        windowDefaultStartPositionUs = playlist.startOffsetUs;
      } else {
        windowDefaultStartPositionUs =
            findClosestPrecedingSegment(playlist.segments, playlist.startOffsetUs)
                .relativeStartTimeUs;
      }
    }
    return new SinglePeriodTimeline(
        presentationStartTimeMs,
        windowStartTimeMs,
        /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
        /* periodDurationUs= */ playlist.durationUs,
        /* windowDurationUs= */ playlist.durationUs,
        /* windowPositionInPeriodUs= */ 0,
        windowDefaultStartPositionUs,
        /* isSeekable= */ true,
        /* isDynamic= */ false,
        /* suppressPositionProjection= */ true,
        manifest,
        getMediaItem(),
        /* liveConfiguration= */ null);
  }

  private long getLiveEdgeOffsetUs(HlsMediaPlaylist playlist) {
    return playlist.hasProgramDateTime
        ? Util.msToUs(Util.getNowUnixTimeMs(elapsedRealTimeOffsetMs)) - playlist.getEndTimeUs()
        : 0;
  }

  private long getLiveWindowDefaultStartPositionUs(
      HlsMediaPlaylist playlist, long liveEdgeOffsetUs) {
    long startPositionUs =
        playlist.startOffsetUs != C.TIME_UNSET
            ? playlist.startOffsetUs
            : playlist.durationUs
                + liveEdgeOffsetUs
                - Util.msToUs(liveConfiguration.targetOffsetMs);
    if (playlist.preciseStart) {
      return startPositionUs;
    }
    @Nullable
    HlsMediaPlaylist.Part part =
        findClosestPrecedingIndependentPart(playlist.trailingParts, startPositionUs);
    if (part != null) {
      return part.relativeStartTimeUs;
    }
    if (playlist.segments.isEmpty()) {
      return 0;
    }
    HlsMediaPlaylist.Segment segment =
        findClosestPrecedingSegment(playlist.segments, startPositionUs);
    part = findClosestPrecedingIndependentPart(segment.parts, startPositionUs);
    if (part != null) {
      return part.relativeStartTimeUs;
    }
    return segment.relativeStartTimeUs;
  }

  private void updateLiveConfiguration(HlsMediaPlaylist playlist, long targetLiveOffsetUs) {
    MediaItem.LiveConfiguration mediaItemLiveConfiguration = getMediaItem().liveConfiguration;
    boolean disableSpeedAdjustment =
        mediaItemLiveConfiguration.minPlaybackSpeed == C.RATE_UNSET
            && mediaItemLiveConfiguration.maxPlaybackSpeed == C.RATE_UNSET
            && playlist.serverControl.holdBackUs == C.TIME_UNSET
            && playlist.serverControl.partHoldBackUs == C.TIME_UNSET;
    liveConfiguration =
        new LiveConfiguration.Builder()
            .setTargetOffsetMs(Util.usToMs(targetLiveOffsetUs))
            .setMinPlaybackSpeed(disableSpeedAdjustment ? 1f : liveConfiguration.minPlaybackSpeed)
            .setMaxPlaybackSpeed(disableSpeedAdjustment ? 1f : liveConfiguration.maxPlaybackSpeed)
            .build();
  }

  /**
   * 获取直播播放列表的目标实时偏移量（以微秒为单位）。
   *
   * <p>目标偏移量通过按以下顺序检查来推导：
   *
   * <ol>
   *   <li>播放列表定义了起始偏移量。
   *   <li>播放列表在服务器控制中定义了部分保持时间，并且具有部分持续时间。
   *   <li>播放列表在服务器控制中定义了保持时间。
   *   <li>回退到 {@code 3 x 目标持续时间}。
   * </ol>
   *
   * @param playlist 播放列表。
   * @param liveEdgeOffsetUs 当前实时边缘偏移量。
   * @return 选择的目标实时偏移量（以微秒为单位）。
   */
  private static long getTargetLiveOffsetUs(HlsMediaPlaylist playlist, long liveEdgeOffsetUs) {
    HlsMediaPlaylist.ServerControl serverControl = playlist.serverControl;
    long targetOffsetUs;
    if (playlist.startOffsetUs != C.TIME_UNSET) {
      targetOffsetUs = playlist.durationUs - playlist.startOffsetUs;
    } else if (serverControl.partHoldBackUs != C.TIME_UNSET
        && playlist.partTargetDurationUs != C.TIME_UNSET) {
      // 只有在播放列表具有部分目标持续时间时，才选择部分保持时间。
      targetOffsetUs = serverControl.partHoldBackUs;
    } else if (serverControl.holdBackUs != C.TIME_UNSET) {
      targetOffsetUs = serverControl.holdBackUs;
    } else {
      // 回退，参见 RFC 8216，第 4.4.3.8 节。
      targetOffsetUs = 3 * playlist.targetDurationUs;
    }
    return targetOffsetUs + liveEdgeOffsetUs;
  }

  @Nullable
  private static HlsMediaPlaylist.Part findClosestPrecedingIndependentPart(
      List<HlsMediaPlaylist.Part> parts, long positionUs) {
    @Nullable HlsMediaPlaylist.Part closestPart = null;
    for (int i = 0; i < parts.size(); i++) {
      HlsMediaPlaylist.Part part = parts.get(i);
      if (part.relativeStartTimeUs <= positionUs && part.isIndependent) {
        closestPart = part;
      } else if (part.relativeStartTimeUs > positionUs) {
        break;
      }
    }
    return closestPart;
  }

  /**
   * 获取包含 {@code positionUs} 的片段，如果位置超出片段列表，则返回最后一个片段。
   */
  private static HlsMediaPlaylist.Segment findClosestPrecedingSegment(
      List<HlsMediaPlaylist.Segment> segments, long positionUs) {
    int segmentIndex =
        Util.binarySearchFloor(
            segments, positionUs, /* inclusive= */ true, /* stayInBounds= */ true);
    return segments.get(segmentIndex);
  }
}
