package androidx.media3.exoplayer.dash;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.constrainValue;
import static androidx.media3.common.util.Util.usToMs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseArray;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.Player;
import androidx.media3.common.StreamKey;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.dash.PlayerEmsgHandler.PlayerEmsgCallback;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.dash.manifest.Period;
import androidx.media3.exoplayer.dash.manifest.Representation;
import androidx.media3.exoplayer.dash.manifest.UtcTimingElement;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.offline.FilteringManifestParser;
import androidx.media3.exoplayer.source.BaseMediaSource;
import androidx.media3.exoplayer.source.CompositeSequenceableLoaderFactory;
import androidx.media3.exoplayer.source.DefaultCompositeSequenceableLoaderFactory;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.source.MediaSourceEventListener.EventDispatcher;
import androidx.media3.exoplayer.source.MediaSourceFactory;
import androidx.media3.exoplayer.source.SequenceableLoader;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import androidx.media3.exoplayer.upstream.Loader;
import androidx.media3.exoplayer.upstream.Loader.LoadErrorAction;
import androidx.media3.exoplayer.upstream.LoaderErrorThrower;
import androidx.media3.exoplayer.upstream.ParsingLoadable;
import androidx.media3.exoplayer.util.SntpClient;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.common.math.LongMath;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A DASH {@link MediaSource}.
 */
@UnstableApi
public final class DashMediaSource extends BaseMediaSource {

  static {
    MediaLibraryInfo.registerModule("media3.exoplayer.dash");
  }

  /**
   * Factory for {@link DashMediaSource}s.
   */
  @SuppressWarnings("deprecation") // Implement deprecated type for backwards compatibility.
  public static final class Factory implements MediaSourceFactory {

    private final DashChunkSource.Factory chunkSourceFactory;
    @Nullable
    private final DataSource.Factory manifestDataSourceFactory;

    private CmcdConfiguration.Factory cmcdConfigurationFactory;
    private DrmSessionManagerProvider drmSessionManagerProvider;
    private CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private long fallbackTargetLiveOffsetMs;
    private long minLiveStartPositionUs;
    @Nullable
    private ParsingLoadable.Parser<? extends DashManifest> manifestParser;

    /**
     * 创建一个新的 {@link DashMediaSource} 工厂。
     *
     * <p>该工厂将使用以下默认组件：
     *
     * <ul>
     *   <li>{@link DefaultDashChunkSource.Factory}
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     *   <li>{@link DefaultCompositeSequenceableLoaderFactory}
     * </ul>
     *
     * @param dataSourceFactory 用于创建加载清单和媒体数据的 {@link DataSource} 实例的工厂。
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(new DefaultDashChunkSource.Factory(dataSourceFactory), dataSourceFactory);
    }

    /**
     * 创建一个新的 {@link DashMediaSource} 工厂。
     *
     * <p>该工厂将使用以下默认组件：
     *
     * <ul>
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     *   <li>{@link DefaultCompositeSequenceableLoaderFactory}
     * </ul>
     *
     * @param chunkSourceFactory        用于创建 {@link DashChunkSource} 实例的工厂。
     * @param manifestDataSourceFactory 用于加载（和刷新）清单的 {@link DataSource} 实例的工厂。
     *                                  如果该工厂仅用于通过 {@link #createMediaSource(DashManifest, MediaItem)} 创建带有旁加载清单的媒体源，则可以为 {@code null}。
     */
    public Factory(
        DashChunkSource.Factory chunkSourceFactory,
        @Nullable DataSource.Factory manifestDataSourceFactory) {
      this.chunkSourceFactory = checkNotNull(chunkSourceFactory);
      this.manifestDataSourceFactory = manifestDataSourceFactory;
      drmSessionManagerProvider = new DefaultDrmSessionManagerProvider();
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
      fallbackTargetLiveOffsetMs = DEFAULT_FALLBACK_TARGET_LIVE_OFFSET_MS;
      minLiveStartPositionUs = MIN_LIVE_DEFAULT_START_POSITION_US;
      compositeSequenceableLoaderFactory = new DefaultCompositeSequenceableLoaderFactory();
      experimentalParseSubtitlesDuringExtraction(true);
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
                  + " 显式构造并传递一个实例以保留旧的行为。");
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      this.loadErrorHandlingPolicy =
          checkNotNull(
              loadErrorHandlingPolicy,
              "MediaSource.Factory#setLoadErrorHandlingPolicy 不再通过实例化新的 DefaultLoadErrorHandlingPolicy 来处理 null。"
                  + " 显式构造并传递一个实例以保留旧的行为。");
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Factory setSubtitleParserFactory(SubtitleParser.Factory subtitleParserFactory) {
      chunkSourceFactory.setSubtitleParserFactory(checkNotNull(subtitleParserFactory));
      return this;
    }

    @Override
    @Deprecated
    @CanIgnoreReturnValue
    public Factory experimentalParseSubtitlesDuringExtraction(
        boolean parseSubtitlesDuringExtraction) {
      chunkSourceFactory.experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction);
      return this;
    }

    /**
     * 设置用于直播流的 {@link Player#getCurrentLiveOffset() 当前直播偏移量} 的目标值，如果 {@link MediaItem} 或清单中未定义该值，则使用此值。
     *
     * <p>默认值为 {@link #DEFAULT_FALLBACK_TARGET_LIVE_OFFSET_MS}。
     *
     * @param fallbackTargetLiveOffsetMs 回退的直播目标偏移量（以毫秒为单位）。
     * @return 返回此工厂，方便链式调用。
     */
    @CanIgnoreReturnValue
    public Factory setFallbackTargetLiveOffsetMs(long fallbackTargetLiveOffsetMs) {
      this.fallbackTargetLiveOffsetMs = fallbackTargetLiveOffsetMs;
      return this;
    }

    /**
     * 设置直播流中播放开始的最小位置（以微秒为单位），相对于直播窗口的起始位置。
     *
     * <p>该值将覆盖清单中建议的任何值，并有助于防止 {@link androidx.media3.exoplayer.source.BehindLiveWindowException} 问题。
     *
     * <p>默认值为 {@link #MIN_LIVE_DEFAULT_START_POSITION_US}。
     *
     * @param minLiveStartPositionUs 最小直播开始位置（以微秒为单位），相对于直播窗口的起始位置。
     * @return 返回此工厂，方便链式调用。
     */
    @CanIgnoreReturnValue
    public Factory setMinLiveStartPositionUs(long minLiveStartPositionUs) {
      this.minLiveStartPositionUs = minLiveStartPositionUs;
      return this;
    }

    /**
     * 设置用于解析加载的清单数据的清单解析器，当加载清单 URI 时使用。
     *
     * @param manifestParser 用于解析加载的清单数据的解析器。
     * @return 返回此工厂，方便链式调用。
     */
    @CanIgnoreReturnValue
    public Factory setManifestParser(
        @Nullable ParsingLoadable.Parser<? extends DashManifest> manifestParser) {
      this.manifestParser = manifestParser;
      return this;
    }

    /**
     * 设置用于创建组合 {@link SequenceableLoader} 的工厂，当此媒体源从多个流（视频、音频等）加载数据时使用。
     * 默认值为 {@link DefaultCompositeSequenceableLoaderFactory} 的实例。
     *
     * @param compositeSequenceableLoaderFactory 用于创建组合 {@link SequenceableLoader} 的工厂，当此媒体源从多个流（视频、音频等）加载数据时使用。
     * @return 返回此工厂，方便链式调用。
     */
    @CanIgnoreReturnValue
    public Factory setCompositeSequenceableLoaderFactory(
        CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory) {
      this.compositeSequenceableLoaderFactory =
          checkNotNull(
              compositeSequenceableLoaderFactory,
              "DashMediaSource.Factory#setCompositeSequenceableLoaderFactory 不再通过实例化新的 DefaultCompositeSequenceableLoaderFactory 来处理 null。"
                  + " 显式构造并传递一个实例以保留旧的行为。");
      return this;
    }

    /**
     * 使用当前参数和指定的旁加载清单创建一个新的 {@link DashMediaSource}。
     *
     * @param manifest 清单。{@link DashManifest#dynamic} 必须为 false。
     * @return 新的 {@link DashMediaSource}。
     * @throws IllegalArgumentException 如果 {@link DashManifest#dynamic} 为 true。
     */
    public DashMediaSource createMediaSource(DashManifest manifest) {
      return createMediaSource(
          manifest,
          new MediaItem.Builder()
              .setUri(Uri.EMPTY)
              .setMediaId(DEFAULT_MEDIA_ID)
              .setMimeType(MimeTypes.APPLICATION_MPD)
              .build());
    }

    /**
     * 使用当前参数和指定的旁加载清单创建一个新的 {@link DashMediaSource}。
     *
     * @param manifest  清单。{@link DashManifest#dynamic} 必须为 false。
     * @param mediaItem 要包含在时间轴中的 {@link MediaItem}。
     * @return 新的 {@link DashMediaSource}。
     * @throws IllegalArgumentException 如果 {@link DashManifest#dynamic} 为 true。
     */
    public DashMediaSource createMediaSource(DashManifest manifest, MediaItem mediaItem) {
      Assertions.checkArgument(!manifest.dynamic);
      MediaItem.Builder mediaItemBuilder =
          mediaItem.buildUpon().setMimeType(MimeTypes.APPLICATION_MPD);
      if (mediaItem.localConfiguration == null) {
        mediaItemBuilder.setUri(Uri.EMPTY);
      }
      mediaItem = mediaItemBuilder.build();
      @Nullable
      CmcdConfiguration cmcdConfiguration =
          cmcdConfigurationFactory == null
              ? null
              : cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
      return new DashMediaSource(
          mediaItem,
          manifest,
          /* manifestDataSourceFactory= */ null,
          /* manifestParser= */ null,
          chunkSourceFactory,
          compositeSequenceableLoaderFactory,
          cmcdConfiguration,
          drmSessionManagerProvider.get(mediaItem),
          loadErrorHandlingPolicy,
          fallbackTargetLiveOffsetMs,
          minLiveStartPositionUs);
    }

    /**
     * 使用当前参数创建一个新的 {@link DashMediaSource}。
     *
     * @param mediaItem DASH 流的媒体项。
     * @return 新的 {@link DashMediaSource}。
     * @throws NullPointerException 如果 {@link MediaItem#localConfiguration} 为 {@code null}。
     */
    @Override
    public DashMediaSource createMediaSource(MediaItem mediaItem) {
      checkNotNull(mediaItem.localConfiguration);
      @Nullable ParsingLoadable.Parser<? extends DashManifest> manifestParser = this.manifestParser;
      if (manifestParser == null) {
        manifestParser = new DashManifestParser();
      }
      List<StreamKey> streamKeys = mediaItem.localConfiguration.streamKeys;
      if (!streamKeys.isEmpty()) {
        manifestParser = new FilteringManifestParser<>(manifestParser, streamKeys);
      }
      @Nullable
      CmcdConfiguration cmcdConfiguration =
          cmcdConfigurationFactory == null
              ? null
              : cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);

      return new DashMediaSource(
          mediaItem,
          /* manifest= */ null,
          manifestDataSourceFactory,
          manifestParser,
          chunkSourceFactory,
          compositeSequenceableLoaderFactory,
          cmcdConfiguration,
          drmSessionManagerProvider.get(mediaItem),
          loadErrorHandlingPolicy,
          fallbackTargetLiveOffsetMs,
          minLiveStartPositionUs);
    }

    @Override
    public @C.ContentType int[] getSupportedTypes() {
      return new int[]{C.CONTENT_TYPE_DASH};
    }
  }

  /**
   * 默认的目标 {@link Player#getCurrentLiveOffset() 直播流偏移量}，如果 {@link MediaItem} 或清单中未定义该值，则使用此值。
   */
  public static final long DEFAULT_FALLBACK_TARGET_LIVE_OFFSET_MS = 30_000;

  /**
   * 用于没有清单 URI 的 DASH 媒体源的媒体项的默认媒体 ID。
   */
  public static final String DEFAULT_MEDIA_ID = "DashMediaSource";

  /**
   * 直播流的最小默认开始位置，以微秒为单位，相对于直播窗口的起始位置。
   */
  public static final long MIN_LIVE_DEFAULT_START_POSITION_US = 5_000_000;

  /**
   * 当源的 {@link Timeline} 动态变化时（例如，对于不完整的直播流），调用 {@link MediaSourceCaller#onSourceInfoRefreshed(MediaSource, Timeline)} 的时间间隔（以毫秒为单位）。
   */
  private static final long DEFAULT_NOTIFY_MANIFEST_INTERVAL_MS = 5000;

  private static final String TAG = "DashMediaSource";

  private final boolean sideloadedManifest;
  private final DataSource.Factory manifestDataSourceFactory;
  private final DashChunkSource.Factory chunkSourceFactory;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  @Nullable
  private final CmcdConfiguration cmcdConfiguration;
  private final DrmSessionManager drmSessionManager;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final BaseUrlExclusionList baseUrlExclusionList;
  private final long fallbackTargetLiveOffsetMs;
  private final long minLiveStartPositionUs;
  private final EventDispatcher manifestEventDispatcher;
  private final ParsingLoadable.Parser<? extends DashManifest> manifestParser;
  private final ManifestCallback manifestCallback;
  private final Object manifestUriLock;
  private final SparseArray<DashMediaPeriod> periodsById;
  private final Runnable refreshManifestRunnable;
  private final Runnable simulateManifestRefreshRunnable;
  private final PlayerEmsgCallback playerEmsgCallback;
  private final LoaderErrorThrower manifestLoadErrorThrower;

  private DataSource dataSource;
  private Loader loader;
  @Nullable
  private TransferListener mediaTransferListener;

  private IOException manifestFatalError;
  private Handler handler;

  private MediaItem.LiveConfiguration liveConfiguration;
  private Uri manifestUri;
  private Uri initialManifestUri;
  private DashManifest manifest;
  private boolean manifestLoadPending;
  private long manifestLoadStartTimestampMs;
  private long manifestLoadEndTimestampMs;
  private long elapsedRealtimeOffsetMs;

  private int staleManifestReloadAttempt;
  private long expiredManifestPublishTimeUs;

  private int firstPeriodId;

  @GuardedBy("this")
  private MediaItem mediaItem;

  private DashMediaSource(
      MediaItem mediaItem,
      @Nullable DashManifest manifest,
      @Nullable DataSource.Factory manifestDataSourceFactory,
      @Nullable ParsingLoadable.Parser<? extends DashManifest> manifestParser,
      DashChunkSource.Factory chunkSourceFactory,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      @Nullable CmcdConfiguration cmcdConfiguration,
      DrmSessionManager drmSessionManager,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      long fallbackTargetLiveOffsetMs,
      long minLiveStartPositionUs) {
    this.mediaItem = mediaItem;
    this.liveConfiguration = mediaItem.liveConfiguration;
    this.manifestUri = checkNotNull(mediaItem.localConfiguration).uri;
    this.initialManifestUri = mediaItem.localConfiguration.uri;
    this.manifest = manifest;
    this.manifestDataSourceFactory = manifestDataSourceFactory;
    this.manifestParser = manifestParser;
    this.chunkSourceFactory = chunkSourceFactory;
    this.cmcdConfiguration = cmcdConfiguration;
    this.drmSessionManager = drmSessionManager;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.fallbackTargetLiveOffsetMs = fallbackTargetLiveOffsetMs;
    this.minLiveStartPositionUs = minLiveStartPositionUs;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    baseUrlExclusionList = new BaseUrlExclusionList();
    sideloadedManifest = manifest != null;
    manifestEventDispatcher = createEventDispatcher(/* mediaPeriodId= */ null);
    manifestUriLock = new Object();
    periodsById = new SparseArray<>();
    playerEmsgCallback = new DefaultPlayerEmsgCallback();
    expiredManifestPublishTimeUs = C.TIME_UNSET;
    elapsedRealtimeOffsetMs = C.TIME_UNSET;
    if (sideloadedManifest) {
      Assertions.checkState(!manifest.dynamic);
      manifestCallback = null;
      refreshManifestRunnable = null;
      simulateManifestRefreshRunnable = null;
      manifestLoadErrorThrower = new LoaderErrorThrower.Placeholder();
    } else {
      manifestCallback = new ManifestCallback();
      manifestLoadErrorThrower = new ManifestLoadErrorThrower();
      refreshManifestRunnable = this::startLoadingManifest;
      simulateManifestRefreshRunnable = () -> processManifest(false);
    }
  }

  /**
   * 手动替换清单的 {@link Uri}。
   *
   * @param manifestUri 替换的清单 {@link Uri}。
   */
  public void replaceManifestUri(Uri manifestUri) {
    synchronized (manifestUriLock) {
      this.manifestUri = manifestUri;
      this.initialManifestUri = manifestUri;
    }
  }

  // MediaSource implementation.

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
    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), getPlayerId());
    drmSessionManager.prepare();
    if (sideloadedManifest) {
      processManifest(false);
    } else {
      dataSource = manifestDataSourceFactory.createDataSource();
      loader = new Loader("DashMediaSource");
      handler = Util.createHandlerForCurrentLooper();
      startLoadingManifest();
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    manifestLoadErrorThrower.maybeThrowError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    int periodIndex = (Integer) id.periodUid - firstPeriodId;
    MediaSourceEventListener.EventDispatcher periodEventDispatcher = createEventDispatcher(id);
    DrmSessionEventListener.EventDispatcher drmEventDispatcher = createDrmEventDispatcher(id);
    DashMediaPeriod mediaPeriod =
        new DashMediaPeriod(
            /* id= */ firstPeriodId + periodIndex,
            manifest,
            baseUrlExclusionList,
            periodIndex,
            chunkSourceFactory,
            mediaTransferListener,
            cmcdConfiguration,
            drmSessionManager,
            drmEventDispatcher,
            loadErrorHandlingPolicy,
            periodEventDispatcher,
            elapsedRealtimeOffsetMs,
            manifestLoadErrorThrower,
            allocator,
            compositeSequenceableLoaderFactory,
            playerEmsgCallback,
            getPlayerId());
    periodsById.put(mediaPeriod.id, mediaPeriod);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    DashMediaPeriod dashMediaPeriod = (DashMediaPeriod) mediaPeriod;
    dashMediaPeriod.release();
    periodsById.remove(dashMediaPeriod.id);
  }

  @Override
  protected void releaseSourceInternal() {
    manifestLoadPending = false;
    dataSource = null;
    if (loader != null) {
      loader.release();
      loader = null;
    }
    manifestLoadStartTimestampMs = 0;
    manifestLoadEndTimestampMs = 0;
    manifestUri = initialManifestUri;
    manifestFatalError = null;
    if (handler != null) {
      handler.removeCallbacksAndMessages(null);
      handler = null;
    }
    elapsedRealtimeOffsetMs = C.TIME_UNSET;
    staleManifestReloadAttempt = 0;
    expiredManifestPublishTimeUs = C.TIME_UNSET;
    periodsById.clear();
    baseUrlExclusionList.reset();
    drmSessionManager.release();
  }

  // PlayerEmsgCallback callbacks.

  /* package */ void onDashManifestRefreshRequested() {
    handler.removeCallbacks(simulateManifestRefreshRunnable);
    startLoadingManifest();
  }

  /* package */ void onDashManifestPublishTimeExpired(long expiredManifestPublishTimeUs) {
    if (this.expiredManifestPublishTimeUs == C.TIME_UNSET
        || this.expiredManifestPublishTimeUs < expiredManifestPublishTimeUs) {
      this.expiredManifestPublishTimeUs = expiredManifestPublishTimeUs;
    }
  }

  // Loadable callbacks.

  /* package */ void onManifestLoadCompleted(
      ParsingLoadable<DashManifest> loadable, long elapsedRealtimeMs, long loadDurationMs) {
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
    manifestEventDispatcher.loadCompleted(loadEventInfo, loadable.type);
    DashManifest newManifest = loadable.getResult();

    int oldPeriodCount = manifest == null ? 0 : manifest.getPeriodCount();
    int removedPeriodCount = 0;
    long newFirstPeriodStartTimeMs = newManifest.getPeriod(0).startMs;
    while (removedPeriodCount < oldPeriodCount
        && manifest.getPeriod(removedPeriodCount).startMs < newFirstPeriodStartTimeMs) {
      removedPeriodCount++;
    }

    if (newManifest.dynamic) {
      boolean isManifestStale = false;
      if (oldPeriodCount - removedPeriodCount > newManifest.getPeriodCount()) {
        // 在丢弃旧的周期后，我们不应该拥有比新清单中列出的更多的周期。这意味着之前公布的周期不再被广告。
        // 如果这种情况发生，假设我们正在访问一个不同步且滞后的清单服务器。
        Log.w(TAG, "加载了不同步的清单");
        isManifestStale = true;
      } else if (expiredManifestPublishTimeUs != C.TIME_UNSET
          && newManifest.publishTimeMs * 1000 <= expiredManifestPublishTimeUs) {
        // 如果我们接收到一个动态清单，但它比预期的更旧（即其发布时间已过期，或者它是动态的且我们知道演示已结束），
        // 那么此清单是过时的。
        Log.w(
            TAG,
            "加载了过时的动态清单: "
                + newManifest.publishTimeMs
                + ", "
                + expiredManifestPublishTimeUs);
        isManifestStale = true;
      }

      if (isManifestStale) {
        if (staleManifestReloadAttempt++
            < loadErrorHandlingPolicy.getMinimumLoadableRetryCount(loadable.type)) {
          scheduleManifestRefresh(getManifestLoadRetryDelayMillis());
        } else {
          manifestFatalError = new DashManifestStaleException();
        }
        return;
      }
      staleManifestReloadAttempt = 0;
    }

    manifest = newManifest;
    manifestLoadPending &= manifest.dynamic;
    manifestLoadStartTimestampMs = elapsedRealtimeMs - loadDurationMs;
    manifestLoadEndTimestampMs = elapsedRealtimeMs;
    firstPeriodId += removedPeriodCount;

    synchronized (manifestUriLock) {
      // 检查在本次加载的开始和结束之间是否调用了 replaceManifestUri(Uri) 来手动替换 URI。
      // 如果调用了，则 isSameUriInstance 的值为 false，我们优先使用手动替换的 URI 而不是从之前的请求派生的 URI。
      @SuppressWarnings("ReferenceEquality")
      boolean isSameUriInstance = loadable.dataSpec.uri == manifestUri;
      if (isSameUriInstance) {
        // 如果清单中存在 Location 元素，则使用它指定的 URI 替换清单 URI；
        // 否则使用最终的（可能是重定向的）URI。这遵循 DASH-IF-IOP 4.3 第 3.2.15.3 节的建议。
        // 参见：https://dashif.org/docs/DASH-IF-IOP-v4.3.pdf。
        manifestUri = manifest.location != null ? manifest.location : loadable.getUri();
      }
    }

    if (manifest.dynamic && elapsedRealtimeOffsetMs == C.TIME_UNSET) {
      // 在进一步处理清单之前，确定 elapsedRealtimeOffsetMs。
      if (manifest.utcTiming != null) {
        resolveUtcTimingElement(manifest.utcTiming);
      } else {
        loadNtpTimeOffset();
      }
    } else {
      processManifest(true);
    }
  }

  /* package */ LoadErrorAction onManifestLoadError(
      ParsingLoadable<DashManifest> loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error,
      int errorCount) {
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded());
    MediaLoadData mediaLoadData = new MediaLoadData(loadable.type);
    LoadErrorInfo loadErrorInfo =
        new LoadErrorInfo(loadEventInfo, mediaLoadData, error, errorCount);
    long retryDelayMs = loadErrorHandlingPolicy.getRetryDelayMsFor(loadErrorInfo);
    LoadErrorAction loadErrorAction =
        retryDelayMs == C.TIME_UNSET
            ? Loader.DONT_RETRY_FATAL
            : Loader.createRetryAction(/* resetErrorCount= */ false, retryDelayMs);
    boolean wasCanceled = !loadErrorAction.isRetry();
    manifestEventDispatcher.loadError(loadEventInfo, loadable.type, error, wasCanceled);
    if (wasCanceled) {
      loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    }
    return loadErrorAction;
  }

  /* package */ void onUtcTimestampLoadCompleted(
      ParsingLoadable<Long> loadable, long elapsedRealtimeMs, long loadDurationMs) {
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
    manifestEventDispatcher.loadCompleted(loadEventInfo, loadable.type);
    onUtcTimestampResolved(loadable.getResult() - elapsedRealtimeMs);
  }

  /* package */ LoadErrorAction onUtcTimestampLoadError(
      ParsingLoadable<Long> loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error) {
    manifestEventDispatcher.loadError(
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded()),
        loadable.type,
        error,
        /* wasCanceled= */ true);
    loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    onUtcTimestampResolutionError(error);
    return Loader.DONT_RETRY;
  }

  /* package */ void onLoadCanceled(
      ParsingLoadable<?> loadable, long elapsedRealtimeMs, long loadDurationMs) {
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
    manifestEventDispatcher.loadCanceled(loadEventInfo, loadable.type);
  }

  // Internal methods.

  private void resolveUtcTimingElement(UtcTimingElement timingElement) {
    String scheme = timingElement.schemeIdUri;
    // 根据 UTC 定时元素的 schemeIdUri 进行不同的处理
    if (Util.areEqual(scheme, "urn:mpeg:dash:utc:direct:2014")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:direct:2012")) {
      // 如果 scheme 是直接 UTC 时间，调用直接解析方法
      resolveUtcTimingElementDirect(timingElement);
    } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:http-iso:2014")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:http-iso:2012")) {
      // 如果 scheme 是 HTTP ISO 格式，调用 HTTP 解析方法，并传入 ISO8601 解析器
      resolveUtcTimingElementHttp(timingElement, new Iso8601Parser());
    } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:http-xsdate:2014")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:http-xsdate:2012")) {
      // 如果 scheme 是 HTTP XSDate 格式，调用 HTTP 解析方法，并传入 XSDate 解析器
      resolveUtcTimingElementHttp(timingElement, new XsDateTimeParser());
    } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:ntp:2014")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:ntp:2012")) {
      // 如果 scheme 是 NTP 格式，加载 NTP 时间偏移量
      loadNtpTimeOffset();
    } else {
      // 如果 scheme 不支持，抛出异常并调用错误处理方法
      onUtcTimestampResolutionError(new IOException("Unsupported UTC timing scheme"));
    }
  }

  private void resolveUtcTimingElementDirect(UtcTimingElement timingElement) {
    try {
      long utcTimestampMs = Util.parseXsDateTime(timingElement.value);
      onUtcTimestampResolved(utcTimestampMs - manifestLoadEndTimestampMs);
    } catch (ParserException e) {
      onUtcTimestampResolutionError(e);
    }
  }

  private void resolveUtcTimingElementHttp(
      UtcTimingElement timingElement, ParsingLoadable.Parser<Long> parser) {
    startLoading(
        new ParsingLoadable<>(
            dataSource, Uri.parse(timingElement.value), C.DATA_TYPE_TIME_SYNCHRONIZATION, parser),
        new UtcTimestampCallback(),
        1);
  }

  private void loadNtpTimeOffset() {
    SntpClient.initialize(
        loader,
        new SntpClient.InitializationCallback() {
          @Override
          public void onInitialized() {
            onUtcTimestampResolved(SntpClient.getElapsedRealtimeOffsetMs());
          }

          @Override
          public void onInitializationFailed(IOException error) {
            onUtcTimestampResolutionError(error);
          }
        });
  }

  private void onUtcTimestampResolved(long elapsedRealtimeOffsetMs) {
    this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
    processManifest(true);
  }

  private void onUtcTimestampResolutionError(IOException error) {
    Log.e(TAG, "Failed to resolve time offset.", error);
    // Be optimistic and continue in the hope that the device clock is correct.
    this.elapsedRealtimeOffsetMs = System.currentTimeMillis() - SystemClock.elapsedRealtime();
    processManifest(true);
  }

  private void processManifest(boolean scheduleRefresh) {
    // 更新所有周期
    for (int i = 0; i < periodsById.size(); i++) {
      int id = periodsById.keyAt(i);
      if (id >= firstPeriodId) {
        periodsById.valueAt(i).updateManifest(manifest, id - firstPeriodId);
      } else {
        // 该周期已从清单中移除，因此无需更新
      }
    }
    // 更新窗口
    Period firstPeriod = manifest.getPeriod(0);
    int lastPeriodIndex = manifest.getPeriodCount() - 1;
    Period lastPeriod = manifest.getPeriod(lastPeriodIndex);
    long lastPeriodDurationUs = manifest.getPeriodDurationUs(lastPeriodIndex);
    long nowUnixTimeUs = Util.msToUs(Util.getNowUnixTimeMs(elapsedRealtimeOffsetMs));
    long windowStartTimeInManifestUs =
        getAvailableStartTimeInManifestUs(
            firstPeriod, manifest.getPeriodDurationUs(0), nowUnixTimeUs);
    long windowEndTimeInManifestUs =
        getAvailableEndTimeInManifestUs(lastPeriod, lastPeriodDurationUs, nowUnixTimeUs);
    boolean windowChangingImplicitly = manifest.dynamic && !isIndexExplicit(lastPeriod);
    if (windowChangingImplicitly && manifest.timeShiftBufferDepthMs != C.TIME_UNSET) {
      // 更新可用开始时间以反映清单的时间偏移缓冲区深度
      long timeShiftBufferStartTimeInManifestUs =
          windowEndTimeInManifestUs - Util.msToUs(manifest.timeShiftBufferDepthMs);
      windowStartTimeInManifestUs =
          max(windowStartTimeInManifestUs, timeShiftBufferStartTimeInManifestUs);
    }
    long windowDurationUs = windowEndTimeInManifestUs - windowStartTimeInManifestUs;
    long windowStartUnixTimeMs = C.TIME_UNSET;
    long windowDefaultPositionUs = 0;
    if (manifest.dynamic) {
      checkState(manifest.availabilityStartTimeMs != C.TIME_UNSET);
      long nowInWindowUs =
          nowUnixTimeUs
              - Util.msToUs(manifest.availabilityStartTimeMs)
              - windowStartTimeInManifestUs;
      updateLiveConfiguration(nowInWindowUs, windowDurationUs);
      windowStartUnixTimeMs =
          manifest.availabilityStartTimeMs + Util.usToMs(windowStartTimeInManifestUs);
      windowDefaultPositionUs = nowInWindowUs - Util.msToUs(liveConfiguration.targetOffsetMs);
      long minimumWindowDefaultPositionUs = min(minLiveStartPositionUs, windowDurationUs / 2);
      if (windowDefaultPositionUs < minimumWindowDefaultPositionUs) {
        // 默认位置太接近直播窗口的起始位置。如果窗口至少是两倍大，则将其设置为提供的最小默认位置。
        // 否则将其设置为窗口的中间位置。
        windowDefaultPositionUs = minimumWindowDefaultPositionUs;
      }
    }
    long offsetInFirstPeriodUs = windowStartTimeInManifestUs - Util.msToUs(firstPeriod.startMs);
    DashTimeline timeline =
        new DashTimeline(
            manifest.availabilityStartTimeMs,
            windowStartUnixTimeMs,
            elapsedRealtimeOffsetMs,
            firstPeriodId,
            offsetInFirstPeriodUs,
            windowDurationUs,
            windowDefaultPositionUs,
            manifest,
            getMediaItem(),
            manifest.dynamic ? liveConfiguration : null);
    refreshSourceInfo(timeline);

    if (!sideloadedManifest) {
      // 移除任何挂起的模拟刷新
      handler.removeCallbacks(simulateManifestRefreshRunnable);
      // 如果窗口正在隐式变化，则发布模拟清单刷新以更新窗口
      if (windowChangingImplicitly) {
        handler.postDelayed(
            simulateManifestRefreshRunnable,
            getIntervalUntilNextManifestRefreshMs(
                manifest, Util.getNowUnixTimeMs(elapsedRealtimeOffsetMs)));
      }
      if (manifestLoadPending) {
        startLoadingManifest();
      } else if (scheduleRefresh
          && manifest.dynamic
          && manifest.minUpdatePeriodMs != C.TIME_UNSET) {
        // 如果需要，安排显式刷新
        long minUpdatePeriodMs = manifest.minUpdatePeriodMs;
        if (minUpdatePeriodMs == 0) {
          // TODO: 这是一个临时解决方案，用于避免在 minimumUpdatePeriod 设置为 0 的情况下不断刷新 MPD。
          // 在这种情况下，除非流中有明确的信号，否则不应刷新，根据：
          // http://azure.microsoft.com/blog/2014/09/13/dash-live-streaming-with-azure-media-service
          minUpdatePeriodMs = 5000;
        }
        long nextLoadTimestampMs = manifestLoadStartTimestampMs + minUpdatePeriodMs;
        long delayUntilNextLoadMs = max(0, nextLoadTimestampMs - SystemClock.elapsedRealtime());
        scheduleManifestRefresh(delayUntilNextLoadMs);
      }
    }
  }

  private void updateLiveConfiguration(long nowInWindowUs, long windowDurationUs) {
    MediaItem.LiveConfiguration mediaItemLiveConfiguration = getMediaItem().liveConfiguration;
    // 默认的最大偏移量：窗口的起始位置
    long maxPossibleLiveOffsetMs = usToMs(nowInWindowUs);
    long maxLiveOffsetMs = maxPossibleLiveOffsetMs;
    // 如果用户或媒体定义的偏移量更小，则覆盖最大偏移量
    if (mediaItemLiveConfiguration.maxOffsetMs != C.TIME_UNSET) {
      maxLiveOffsetMs = min(maxLiveOffsetMs, mediaItemLiveConfiguration.maxOffsetMs);
    } else if (manifest.serviceDescription != null
        && manifest.serviceDescription.maxOffsetMs != C.TIME_UNSET) {
      maxLiveOffsetMs = min(maxLiveOffsetMs, manifest.serviceDescription.maxOffsetMs);
    }
    // 默认的最小偏移量：窗口的结束位置
    long minLiveOffsetMs = usToMs(nowInWindowUs - windowDurationUs);
    if (minLiveOffsetMs < 0 && maxLiveOffsetMs > 0) {
      // 当前时间在窗口内，因此假设所有时钟已同步，并将最小偏移量设置为零
      minLiveOffsetMs = 0;
    }
    if (manifest.minBufferTimeMs != C.TIME_UNSET) {
      // 确保保留一个 GOP 作为最小缓冲，且不超过最大可能的偏移量
      minLiveOffsetMs = min(minLiveOffsetMs + manifest.minBufferTimeMs, maxPossibleLiveOffsetMs);
    }
    // 如果用户或媒体定义的偏移量更大，则覆盖最小偏移量，但不超过最大可能的偏移量
    if (mediaItemLiveConfiguration.minOffsetMs != C.TIME_UNSET) {
      minLiveOffsetMs =
          constrainValue(
              mediaItemLiveConfiguration.minOffsetMs, minLiveOffsetMs, maxPossibleLiveOffsetMs);
    } else if (manifest.serviceDescription != null
        && manifest.serviceDescription.minOffsetMs != C.TIME_UNSET) {
      minLiveOffsetMs =
          constrainValue(
              manifest.serviceDescription.minOffsetMs, minLiveOffsetMs, maxPossibleLiveOffsetMs);
    }
    if (minLiveOffsetMs > maxLiveOffsetMs) {
      // 如果最小偏移量大于最大偏移量，则优先使用最大偏移量，以确保播放安全
      maxLiveOffsetMs = minLiveOffsetMs;
    }
    long targetOffsetMs;
    if (liveConfiguration.targetOffsetMs != C.TIME_UNSET) {
      // 如果已有目标偏移量，则保持不变，即使媒体配置发生变化
      targetOffsetMs = liveConfiguration.targetOffsetMs;
    } else if (manifest.serviceDescription != null
        && manifest.serviceDescription.targetOffsetMs != C.TIME_UNSET) {
      targetOffsetMs = manifest.serviceDescription.targetOffsetMs;
    } else if (manifest.suggestedPresentationDelayMs != C.TIME_UNSET) {
      targetOffsetMs = manifest.suggestedPresentationDelayMs;
    } else {
      targetOffsetMs = fallbackTargetLiveOffsetMs;
    }
    if (targetOffsetMs < minLiveOffsetMs) {
      targetOffsetMs = minLiveOffsetMs;
    }
    if (targetOffsetMs > maxLiveOffsetMs) {
      // 如果目标偏移量超过最大偏移量，则将其限制在安全范围内
      long safeDistanceFromWindowStartUs = min(minLiveStartPositionUs, windowDurationUs / 2);
      long maxTargetOffsetForSafeDistanceToWindowStartMs =
          usToMs(nowInWindowUs - safeDistanceFromWindowStartUs);
      targetOffsetMs =
          constrainValue(
              maxTargetOffsetForSafeDistanceToWindowStartMs, minLiveOffsetMs, maxLiveOffsetMs);
    }
    float minPlaybackSpeed = C.RATE_UNSET;
    if (mediaItemLiveConfiguration.minPlaybackSpeed != C.RATE_UNSET) {
      minPlaybackSpeed = mediaItemLiveConfiguration.minPlaybackSpeed;
    } else if (manifest.serviceDescription != null) {
      minPlaybackSpeed = manifest.serviceDescription.minPlaybackSpeed;
    }
    float maxPlaybackSpeed = C.RATE_UNSET;
    if (mediaItemLiveConfiguration.maxPlaybackSpeed != C.RATE_UNSET) {
      maxPlaybackSpeed = mediaItemLiveConfiguration.maxPlaybackSpeed;
    } else if (manifest.serviceDescription != null) {
      maxPlaybackSpeed = manifest.serviceDescription.maxPlaybackSpeed;
    }
    if (minPlaybackSpeed == C.RATE_UNSET
        && maxPlaybackSpeed == C.RATE_UNSET
        && (manifest.serviceDescription == null
        || manifest.serviceDescription.targetOffsetMs == C.TIME_UNSET)) {
      // 如果媒体项或清单中没有定义特定的速度限制，并且清单中也没有低延迟目标偏移量，
      // 则强制使用单位速度（而不是自动调整回退速度）
      minPlaybackSpeed = 1f;
      maxPlaybackSpeed = 1f;
    }
    liveConfiguration =
        new MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(targetOffsetMs)
            .setMinOffsetMs(minLiveOffsetMs)
            .setMaxOffsetMs(maxLiveOffsetMs)
            .setMinPlaybackSpeed(minPlaybackSpeed)
            .setMaxPlaybackSpeed(maxPlaybackSpeed)
            .build();
  }

  private void scheduleManifestRefresh(long delayUntilNextLoadMs) {
    handler.postDelayed(refreshManifestRunnable, delayUntilNextLoadMs);
  }

  private void startLoadingManifest() {
    handler.removeCallbacks(refreshManifestRunnable);
    if (loader.hasFatalError()) {
      return;
    }
    if (loader.isLoading()) {
      manifestLoadPending = true;
      return;
    }
    Uri manifestUri;
    synchronized (manifestUriLock) {
      manifestUri = this.manifestUri;
    }
    manifestLoadPending = false;
    startLoading(
        new ParsingLoadable<>(dataSource, manifestUri, C.DATA_TYPE_MANIFEST, manifestParser),
        manifestCallback,
        loadErrorHandlingPolicy.getMinimumLoadableRetryCount(C.DATA_TYPE_MANIFEST));
  }

  private long getManifestLoadRetryDelayMillis() {
    return min((staleManifestReloadAttempt - 1) * 1000, 5000);
  }

  private <T> void startLoading(
      ParsingLoadable<T> loadable,
      Loader.Callback<ParsingLoadable<T>> callback,
      int minRetryCount) {
    long elapsedRealtimeMs = loader.startLoading(loadable, callback, minRetryCount);
    manifestEventDispatcher.loadStarted(
        new LoadEventInfo(loadable.loadTaskId, loadable.dataSpec, elapsedRealtimeMs),
        loadable.type);
  }

  private static long getIntervalUntilNextManifestRefreshMs(
      DashManifest manifest, long nowUnixTimeMs) {
    int periodIndex = manifest.getPeriodCount() - 1;
    Period period = manifest.getPeriod(periodIndex);
    long periodStartUs = Util.msToUs(period.startMs);
    long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
    long nowUnixTimeUs = Util.msToUs(nowUnixTimeMs);
    long availabilityStartTimeUs = Util.msToUs(manifest.availabilityStartTimeMs);
    long intervalUs = Util.msToUs(DEFAULT_NOTIFY_MANIFEST_INTERVAL_MS);
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      List<Representation> representations = period.adaptationSets.get(i).representations;
      if (representations.isEmpty()) {
        continue;
      }
      @Nullable DashSegmentIndex index = representations.get(0).getIndex();
      if (index != null) {
        long nextSegmentShiftUnixTimeUs =
            availabilityStartTimeUs
                + periodStartUs
                + index.getNextSegmentAvailableTimeUs(periodDurationUs, nowUnixTimeUs);
        long requiredIntervalUs = nextSegmentShiftUnixTimeUs - nowUnixTimeUs;
        // 避免在极短时间内多次刷新
        if (requiredIntervalUs < intervalUs - 100_000
            || (requiredIntervalUs > intervalUs && requiredIntervalUs < intervalUs + 100_000)) {
          intervalUs = requiredIntervalUs;
        }
      }
    }
    // 向上取整以补偿从微秒到毫秒转换时可能产生的精度损失
    return LongMath.divide(intervalUs, 1000, RoundingMode.CEILING);
  }

  private static long getAvailableStartTimeInManifestUs(
      Period period, long periodDurationUs, long nowUnixTimeUs) {
    long periodStartTimeInManifestUs = Util.msToUs(period.startMs);
    long availableStartTimeInManifestUs = periodStartTimeInManifestUs;
    boolean haveAudioVideoAdaptationSets = hasVideoOrAudioAdaptationSets(period);
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      AdaptationSet adaptationSet = period.adaptationSets.get(i);
      List<Representation> representations = adaptationSet.representations;
      // 如果我们至少有一个音频或视频自适应集，则从时长计算中排除其他自适应集。
      // 参见：https://github.com/google/ExoPlayer/issues/4029。
      boolean adaptationSetIsNotAudioVideo =
          adaptationSet.type != C.TRACK_TYPE_AUDIO && adaptationSet.type != C.TRACK_TYPE_VIDEO;
      if ((haveAudioVideoAdaptationSets && adaptationSetIsNotAudioVideo)
          || representations.isEmpty()) {
        continue;
      }
      @Nullable DashSegmentIndex index = representations.get(0).getIndex();
      if (index == null) {
        return periodStartTimeInManifestUs;
      }
      long availableSegmentCount = index.getAvailableSegmentCount(periodDurationUs, nowUnixTimeUs);
      if (availableSegmentCount == 0) {
        return periodStartTimeInManifestUs;
      }
      long firstAvailableSegmentNum =
          index.getFirstAvailableSegmentNum(periodDurationUs, nowUnixTimeUs);
      long adaptationSetAvailableStartTimeInManifestUs =
          periodStartTimeInManifestUs + index.getTimeUs(firstAvailableSegmentNum);
      availableStartTimeInManifestUs =
          max(availableStartTimeInManifestUs, adaptationSetAvailableStartTimeInManifestUs);
    }
    return availableStartTimeInManifestUs;
  }

  private static long getAvailableEndTimeInManifestUs(
      Period period, long periodDurationUs, long nowUnixTimeUs) {
    // 将周期的起始时间从毫秒转换为微秒
    long periodStartTimeInManifestUs = Util.msToUs(period.startMs);
    // 初始化可用结束时间为最大值
    long availableEndTimeInManifestUs = Long.MAX_VALUE;
    // 检查周期中是否包含音频或视频自适应集
    boolean haveAudioVideoAdaptationSets = hasVideoOrAudioAdaptationSets(period);
    // 遍历周期中的所有自适应集
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      AdaptationSet adaptationSet = period.adaptationSets.get(i);
      List<Representation> representations = adaptationSet.representations;
      // 如果存在至少一个音频或视频自适应集，则从时长计算中排除其他自适应集。
      // 参见：https://github.com/google/ExoPlayer/issues/4029
      boolean adaptationSetIsNotAudioVideo =
          adaptationSet.type != C.TRACK_TYPE_AUDIO && adaptationSet.type != C.TRACK_TYPE_VIDEO;
      if ((haveAudioVideoAdaptationSets && adaptationSetIsNotAudioVideo)
          || representations.isEmpty()) {
        continue; // 跳过非音视频自适应集或空表示
      }
      // 获取第一个表示的分段索引
      @Nullable DashSegmentIndex index = representations.get(0).getIndex();
      if (index == null) {
        // 如果索引为空，则返回周期的结束时间
        return periodStartTimeInManifestUs + periodDurationUs;
      }
      // 获取可用分段的数量
      long availableSegmentCount = index.getAvailableSegmentCount(periodDurationUs, nowUnixTimeUs);
      if (availableSegmentCount == 0) {
        // 如果没有可用分段，则返回周期的起始时间
        return periodStartTimeInManifestUs;
      }
      // 计算第一个和最后一个可用分段的编号
      long firstAvailableSegmentNum =
          index.getFirstAvailableSegmentNum(periodDurationUs, nowUnixTimeUs);
      long lastAvailableSegmentNum = firstAvailableSegmentNum + availableSegmentCount - 1;
      // 计算当前自适应集的可用结束时间
      long adaptationSetAvailableEndTimeInManifestUs =
          periodStartTimeInManifestUs
              + index.getTimeUs(lastAvailableSegmentNum)
              + index.getDurationUs(lastAvailableSegmentNum, periodDurationUs);
      // 更新全局可用结束时间为所有自适应集的最小值
      availableEndTimeInManifestUs =
          min(availableEndTimeInManifestUs, adaptationSetAvailableEndTimeInManifestUs);
    }
    // 返回计算出的可用结束时间
    return availableEndTimeInManifestUs;
  }

  private static boolean isIndexExplicit(Period period) {
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      @Nullable
      DashSegmentIndex index = period.adaptationSets.get(i).representations.get(0).getIndex();
      if (index == null || index.isExplicit()) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasVideoOrAudioAdaptationSets(Period period) {
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      int type = period.adaptationSets.get(i).type;
      if (type == C.TRACK_TYPE_AUDIO || type == C.TRACK_TYPE_VIDEO) {
        return true;
      }
    }
    return false;
  }

  private static final class DashTimeline extends Timeline {

    private final long presentationStartTimeMs;
    private final long windowStartTimeMs;
    private final long elapsedRealtimeEpochOffsetMs;

    private final int firstPeriodId;
    private final long offsetInFirstPeriodUs;
    private final long windowDurationUs;
    private final long windowDefaultStartPositionUs;
    private final DashManifest manifest;
    private final MediaItem mediaItem;
    @Nullable
    private final MediaItem.LiveConfiguration liveConfiguration;

    public DashTimeline(
        long presentationStartTimeMs,
        long windowStartTimeMs,
        long elapsedRealtimeEpochOffsetMs,
        int firstPeriodId,
        long offsetInFirstPeriodUs,
        long windowDurationUs,
        long windowDefaultStartPositionUs,
        DashManifest manifest,
        MediaItem mediaItem,
        @Nullable MediaItem.LiveConfiguration liveConfiguration) {
      checkState(manifest.dynamic == (liveConfiguration != null));
      this.presentationStartTimeMs = presentationStartTimeMs;
      this.windowStartTimeMs = windowStartTimeMs;
      this.elapsedRealtimeEpochOffsetMs = elapsedRealtimeEpochOffsetMs;
      this.firstPeriodId = firstPeriodId;
      this.offsetInFirstPeriodUs = offsetInFirstPeriodUs;
      this.windowDurationUs = windowDurationUs;
      this.windowDefaultStartPositionUs = windowDefaultStartPositionUs;
      this.manifest = manifest;
      this.mediaItem = mediaItem;
      this.liveConfiguration = liveConfiguration;
    }

    @Override
    public int getPeriodCount() {
      return manifest.getPeriodCount();
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      Assertions.checkIndex(periodIndex, 0, getPeriodCount());
      Object id = setIds ? manifest.getPeriod(periodIndex).id : null;
      Object uid = setIds ? (firstPeriodId + periodIndex) : null;
      return period.set(
          id,
          uid,
          0,
          manifest.getPeriodDurationUs(periodIndex),
          Util.msToUs(manifest.getPeriod(periodIndex).startMs - manifest.getPeriod(0).startMs)
              - offsetInFirstPeriodUs);
    }

    @Override
    public int getWindowCount() {
      return 1;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      Assertions.checkIndex(windowIndex, 0, 1);
      long windowDefaultStartPositionUs =
          getAdjustedWindowDefaultStartPositionUs(defaultPositionProjectionUs);
      return window.set(
          Window.SINGLE_WINDOW_UID,
          mediaItem,
          manifest,
          presentationStartTimeMs,
          windowStartTimeMs,
          elapsedRealtimeEpochOffsetMs,
          /* isSeekable= */ true,
          /* isDynamic= */ isMovingLiveWindow(manifest),
          liveConfiguration,
          windowDefaultStartPositionUs,
          windowDurationUs,
          /* firstPeriodIndex= */ 0,
          /* lastPeriodIndex= */ getPeriodCount() - 1,
          offsetInFirstPeriodUs);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      if (!(uid instanceof Integer)) {
        return C.INDEX_UNSET;
      }
      int periodId = (int) uid;
      int periodIndex = periodId - firstPeriodId;
      return periodIndex < 0 || periodIndex >= getPeriodCount() ? C.INDEX_UNSET : periodIndex;
    }

    private long getAdjustedWindowDefaultStartPositionUs(long defaultPositionProjectionUs) {
      // 获取窗口的默认起始位置
      long windowDefaultStartPositionUs = this.windowDefaultStartPositionUs;
      // 如果窗口不是动态移动的，直接返回默认起始位置
      if (!isMovingLiveWindow(manifest)) {
        return windowDefaultStartPositionUs;
      }
      // 如果默认位置投影值大于 0，则调整默认起始位置
      if (defaultPositionProjectionUs > 0) {
        windowDefaultStartPositionUs += defaultPositionProjectionUs;
        // 如果调整后的位置超出直播窗口的结束位置，则返回 TIME_UNSET
        if (windowDefaultStartPositionUs > windowDurationUs) {
          return C.TIME_UNSET;
        }
      }
      // 尝试将默认起始位置对齐到对应视频分段的起始位置
      int periodIndex = 0;
      long defaultStartPositionInPeriodUs = offsetInFirstPeriodUs + windowDefaultStartPositionUs;
      long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
      // 遍历周期，找到包含默认起始位置的周期
      while (periodIndex < manifest.getPeriodCount() - 1
          && defaultStartPositionInPeriodUs >= periodDurationUs) {
        defaultStartPositionInPeriodUs -= periodDurationUs;
        periodIndex++;
        periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
      }
      // 获取当前周期
      androidx.media3.exoplayer.dash.manifest.Period period = manifest.getPeriod(periodIndex);
      // 获取视频自适应集的索引
      int videoAdaptationSetIndex = period.getAdaptationSetIndex(C.TRACK_TYPE_VIDEO);
      // 如果没有视频自适应集，则无法对齐，直接返回默认起始位置
      if (videoAdaptationSetIndex == C.INDEX_UNSET) {
        return windowDefaultStartPositionUs;
      }
      // 如果存在多个视频自适应集且分段未对齐，初始时间可能不对应两个分段的起始位置，但这是边缘情况
      @Nullable
      DashSegmentIndex snapIndex =
          period.adaptationSets.get(videoAdaptationSetIndex).representations.get(0).getIndex();
      // 如果视频自适应集没有分段索引或分段为空，则无法对齐，直接返回默认起始位置
      if (snapIndex == null || snapIndex.getSegmentCount(periodDurationUs) == 0) {
        return windowDefaultStartPositionUs;
      }
      // 获取包含默认起始位置的分段编号
      long segmentNum = snapIndex.getSegmentNum(defaultStartPositionInPeriodUs, periodDurationUs);
      // 计算对齐后的默认起始位置
      return windowDefaultStartPositionUs
          + snapIndex.getTimeUs(segmentNum)
          - defaultStartPositionInPeriodUs;
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      Assertions.checkIndex(periodIndex, 0, getPeriodCount());
      return firstPeriodId + periodIndex;
    }

    private static boolean isMovingLiveWindow(DashManifest manifest) {
      return manifest.dynamic
          && manifest.minUpdatePeriodMs != C.TIME_UNSET
          && manifest.durationMs == C.TIME_UNSET;
    }
  }

  private final class DefaultPlayerEmsgCallback implements PlayerEmsgCallback {

    @Override
    public void onDashManifestRefreshRequested() {
      DashMediaSource.this.onDashManifestRefreshRequested();
    }

    @Override
    public void onDashManifestPublishTimeExpired(long expiredManifestPublishTimeUs) {
      DashMediaSource.this.onDashManifestPublishTimeExpired(expiredManifestPublishTimeUs);
    }
  }

  private final class ManifestCallback implements Loader.Callback<ParsingLoadable<DashManifest>> {

    @Override
    public void onLoadCompleted(
        ParsingLoadable<DashManifest> loadable, long elapsedRealtimeMs, long loadDurationMs) {
      onManifestLoadCompleted(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public void onLoadCanceled(
        ParsingLoadable<DashManifest> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        boolean released) {
      DashMediaSource.this.onLoadCanceled(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public LoadErrorAction onLoadError(
        ParsingLoadable<DashManifest> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      return onManifestLoadError(loadable, elapsedRealtimeMs, loadDurationMs, error, errorCount);
    }
  }

  private final class UtcTimestampCallback implements Loader.Callback<ParsingLoadable<Long>> {

    @Override
    public void onLoadCompleted(
        ParsingLoadable<Long> loadable, long elapsedRealtimeMs, long loadDurationMs) {
      onUtcTimestampLoadCompleted(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public void onLoadCanceled(
        ParsingLoadable<Long> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        boolean released) {
      DashMediaSource.this.onLoadCanceled(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public LoadErrorAction onLoadError(
        ParsingLoadable<Long> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      return onUtcTimestampLoadError(loadable, elapsedRealtimeMs, loadDurationMs, error);
    }
  }

  private static final class XsDateTimeParser implements ParsingLoadable.Parser<Long> {

    @Override
    public Long parse(Uri uri, InputStream inputStream) throws IOException {
      String firstLine = new BufferedReader(new InputStreamReader(inputStream)).readLine();
      return Util.parseXsDateTime(firstLine);
    }
  }

  /* package */ static final class Iso8601Parser implements ParsingLoadable.Parser<Long> {

    private static final Pattern TIMESTAMP_WITH_TIMEZONE_PATTERN =
        Pattern.compile("(.+?)(Z|((\\+|-|−)(\\d\\d)(:?(\\d\\d))?))");

    @Override
    public Long parse(Uri uri, InputStream inputStream) throws IOException {
      String firstLine =
          new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).readLine();
      try {
        Matcher matcher = TIMESTAMP_WITH_TIMEZONE_PATTERN.matcher(firstLine);
        if (!matcher.matches()) {
          throw ParserException.createForMalformedManifest(
              "Couldn't parse timestamp: " + firstLine, /* cause= */ null);
        }
        // Parse the timestamp.
        String timestampWithoutTimezone = matcher.group(1);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        long timestampMs = format.parse(timestampWithoutTimezone).getTime();
        // Parse the timezone.
        String timezone = matcher.group(2);
        if ("Z".equals(timezone)) {
          // UTC (no offset).
        } else {
          long sign = "+".equals(matcher.group(4)) ? 1 : -1;
          long hours = Long.parseLong(matcher.group(5));
          String minutesString = matcher.group(7);
          long minutes = TextUtils.isEmpty(minutesString) ? 0 : Long.parseLong(minutesString);
          long timestampOffsetMs = sign * (((hours * 60) + minutes) * 60 * 1000);
          timestampMs -= timestampOffsetMs;
        }
        return timestampMs;
      } catch (ParseException e) {
        throw ParserException.createForMalformedManifest(/* message= */ null, /* cause= */ e);
      }
    }
  }

  /**
   * 一个 {@link LoaderErrorThrower}，用于抛出在清单加载过程中发生的致命 {@link IOException}，或者抛出与已加载清单相关的异常。
   */
  /* package */ final class ManifestLoadErrorThrower implements LoaderErrorThrower {

    @Override
    public void maybeThrowError() throws IOException {
      // 抛出加载器中的错误
      loader.maybeThrowError();
      // 抛出与清单相关的错误
      maybeThrowManifestError();
    }

    @Override
    public void maybeThrowError(int minRetryCount) throws IOException {
      // 抛出加载器中的错误，并指定最小重试次数
      loader.maybeThrowError(minRetryCount);
      // 抛出与清单相关的错误
      maybeThrowManifestError();
    }

    private void maybeThrowManifestError() throws IOException {
      // 如果存在清单致命错误，则抛出该错误
      if (manifestFatalError != null) {
        throw manifestFatalError;
      }
    }
  }
}
