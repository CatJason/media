package androidx.media3.exoplayer.hls;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.Format;
import androidx.media3.common.Label;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.StreamKey;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.drm.DrmSession;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist.Rendition;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist.Variant;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker;
import androidx.media3.exoplayer.source.CompositeSequenceableLoaderFactory;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSourceEventListener.EventDispatcher;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.SequenceableLoader;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.Extractor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link MediaPeriod} that loads an HLS stream. */
/* package */ final class HlsMediaPeriod
    implements MediaPeriod, HlsPlaylistTracker.PlaylistEventListener {

  private final HlsExtractorFactory extractorFactory;
  private final HlsPlaylistTracker playlistTracker;
  private final HlsDataSourceFactory dataSourceFactory;
  @Nullable private final TransferListener mediaTransferListener;
  @Nullable private final CmcdConfiguration cmcdConfiguration;
  private final DrmSessionManager drmSessionManager;
  private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final EventDispatcher eventDispatcher;
  private final Allocator allocator;
  private final IdentityHashMap<SampleStream, Integer> streamWrapperIndices;
  private final TimestampAdjusterProvider timestampAdjusterProvider;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final boolean allowChunklessPreparation;
  private final @HlsMediaSource.MetadataType int metadataType;
  private final boolean useSessionKeys;
  private final PlayerId playerId;
  private final HlsSampleStreamWrapper.Callback sampleStreamWrapperCallback;
  private final long timestampAdjusterInitializationTimeoutMs;

  @Nullable private MediaPeriod.Callback mediaPeriodCallback;
  private int pendingPrepareCount;
  private @MonotonicNonNull TrackGroupArray trackGroups;
  private HlsSampleStreamWrapper[] sampleStreamWrappers;
  private HlsSampleStreamWrapper[] enabledSampleStreamWrappers;
  // Maps sample stream wrappers to variant/rendition index by matching array positions.
  private int[][] manifestUrlIndicesPerWrapper;
  private int audioVideoSampleStreamWrapperCount;
  private SequenceableLoader compositeSequenceableLoader;

  /**
   * 创建一个 HLS 媒体周期。
   *
   * @param extractorFactory 用于片段提取器的 {@link HlsExtractorFactory}。
   * @param playlistTracker 用于 HLS 播放列表的跟踪器。
   * @param dataSourceFactory 用于片段和密钥数据源的 {@link HlsDataSourceFactory}。
   * @param mediaTransferListener 用于通知任何媒体数据传输的传输监听器。如果没有可用的监听器，可以为 null。
   * @param cmcdConfiguration 周期的 {@link CmcdConfiguration}。
   * @param drmSessionManager 用于获取 {@link DrmSession} 的 {@link DrmSessionManager}。
   * @param drmEventDispatcher 用于分发 DRM 相关事件的 {@link DrmSessionEventListener.EventDispatcher}。
   * @param loadErrorHandlingPolicy 加载错误处理策略 {@link LoadErrorHandlingPolicy}。
   * @param eventDispatcher 用于通知事件的分发器。
   * @param allocator 用于获取媒体缓冲区分配器的 {@link Allocator}。
   * @param compositeSequenceableLoaderFactory 当此媒体源从多个流加载数据时，用于创建复合 {@link SequenceableLoader} 的工厂。
   * @param allowChunklessPreparation 是否允许无块准备。
   * @param metadataType 要从周期中提取的元数据类型。
   * @param useSessionKeys 是否使用 #EXT-X-SESSION-KEY 标签。
   * @param playerId 当前播放器的 ID。
   * @param timestampAdjusterInitializationTimeoutMs 加载线程等待时间戳调整器初始化的超时时间（以毫秒为单位）。零超时表示无限超时。
   */
  public HlsMediaPeriod(
      HlsExtractorFactory extractorFactory,
      HlsPlaylistTracker playlistTracker,
      HlsDataSourceFactory dataSourceFactory,
      @Nullable TransferListener mediaTransferListener,
      @Nullable CmcdConfiguration cmcdConfiguration,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      EventDispatcher eventDispatcher,
      Allocator allocator,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      boolean allowChunklessPreparation,
      @HlsMediaSource.MetadataType int metadataType,
      boolean useSessionKeys,
      PlayerId playerId,
      long timestampAdjusterInitializationTimeoutMs) {
    this.extractorFactory = extractorFactory;
    this.playlistTracker = playlistTracker;
    this.dataSourceFactory = dataSourceFactory;
    this.mediaTransferListener = mediaTransferListener;
    this.cmcdConfiguration = cmcdConfiguration;
    this.drmSessionManager = drmSessionManager;
    this.drmEventDispatcher = drmEventDispatcher;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.eventDispatcher = eventDispatcher;
    this.allocator = allocator;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.allowChunklessPreparation = allowChunklessPreparation;
    this.metadataType = metadataType;
    this.useSessionKeys = useSessionKeys;
    this.playerId = playerId;
    this.timestampAdjusterInitializationTimeoutMs = timestampAdjusterInitializationTimeoutMs;
    sampleStreamWrapperCallback = new SampleStreamWrapperCallback();
    compositeSequenceableLoader = compositeSequenceableLoaderFactory.empty();
    streamWrapperIndices = new IdentityHashMap<>();
    timestampAdjusterProvider = new TimestampAdjusterProvider();
    sampleStreamWrappers = new HlsSampleStreamWrapper[0];
    enabledSampleStreamWrappers = new HlsSampleStreamWrapper[0];
    manifestUrlIndicesPerWrapper = new int[0][];
  }

  public void release() {
    playlistTracker.removeListener(this);
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      sampleStreamWrapper.release();
    }
    mediaPeriodCallback = null;
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.mediaPeriodCallback = callback;
    playlistTracker.addListener(this);
    buildAndPrepareSampleStreamWrappers(positionUs);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      sampleStreamWrapper.maybeThrowPrepareError();
    }
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    // trackGroups 仅在周期未准备或已释放时为 null。
    return Assertions.checkNotNull(trackGroups);
  }

  // TODO: 当多变量播放列表未按 URL 去重并且允许 URL 为 null 的 Renditions 时，必须更新此方法以计算与可能已持久化用于离线的流密钥兼容的流密钥。
  @Override
  public List<StreamKey> getStreamKeys(List<ExoTrackSelection> trackSelections) {
    // 有关 StreamKeys 的解释，请参阅 HlsMultivariantPlaylist.copy。
    HlsMultivariantPlaylist multivariantPlaylist =
        Assertions.checkNotNull(playlistTracker.getMultivariantPlaylist());
    boolean hasVariants = !multivariantPlaylist.variants.isEmpty();
    int audioWrapperOffset = hasVariants ? 1 : 0;
    // 字幕样本流包装器位于最后。
    int subtitleWrapperOffset = sampleStreamWrappers.length - multivariantPlaylist.subtitles.size();

    TrackGroupArray mainWrapperTrackGroups;
    int mainWrapperPrimaryGroupIndex;
    int[] mainWrapperVariantIndices;
    if (hasVariants) {
      HlsSampleStreamWrapper mainWrapper = sampleStreamWrappers[0];
      mainWrapperVariantIndices = manifestUrlIndicesPerWrapper[0];
      mainWrapperTrackGroups = mainWrapper.getTrackGroups();
      mainWrapperPrimaryGroupIndex = mainWrapper.getPrimaryTrackGroupIndex();
    } else {
      mainWrapperVariantIndices = new int[0];
      mainWrapperTrackGroups = TrackGroupArray.EMPTY;
      mainWrapperPrimaryGroupIndex = 0;
    }

    List<StreamKey> streamKeys = new ArrayList<>();
    boolean needsPrimaryTrackGroupSelection = false;
    boolean hasPrimaryTrackGroupSelection = false;
    for (ExoTrackSelection trackSelection : trackSelections) {
      TrackGroup trackSelectionGroup = trackSelection.getTrackGroup();
      int mainWrapperTrackGroupIndex = mainWrapperTrackGroups.indexOf(trackSelectionGroup);
      if (mainWrapperTrackGroupIndex != C.INDEX_UNSET) {
        if (mainWrapperTrackGroupIndex == mainWrapperPrimaryGroupIndex) {
          // 主包装器中的主轨道组。
          hasPrimaryTrackGroupSelection = true;
          for (int i = 0; i < trackSelection.length(); i++) {
            int variantIndex = mainWrapperVariantIndices[trackSelection.getIndexInTrackGroup(i)];
            streamKeys.add(
                new StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, variantIndex));
          }
        } else {
          // 主包装器中的嵌入轨道组。
          needsPrimaryTrackGroupSelection = true;
        }
      } else {
        // 音频或字幕组。
        for (int i = audioWrapperOffset; i < sampleStreamWrappers.length; i++) {
          TrackGroupArray wrapperTrackGroups = sampleStreamWrappers[i].getTrackGroups();
          int selectedTrackGroupIndex = wrapperTrackGroups.indexOf(trackSelectionGroup);
          if (selectedTrackGroupIndex != C.INDEX_UNSET) {
            int groupIndexType =
                i < subtitleWrapperOffset
                    ? HlsMultivariantPlaylist.GROUP_INDEX_AUDIO
                    : HlsMultivariantPlaylist.GROUP_INDEX_SUBTITLE;
            int[] selectedWrapperUrlIndices = manifestUrlIndicesPerWrapper[i];
            for (int trackIndex = 0; trackIndex < trackSelection.length(); trackIndex++) {
              int renditionIndex =
                  selectedWrapperUrlIndices[trackSelection.getIndexInTrackGroup(trackIndex)];
              streamKeys.add(new StreamKey(groupIndexType, renditionIndex));
            }
            break;
          }
        }
      }
    }
    if (needsPrimaryTrackGroupSelection && !hasPrimaryTrackGroupSelection) {
      // 如果轨道选择包含一个嵌入到变体中的轨道，但尚未添加任何变体，则使用比特率最低的有效变体以减少开销。
      int lowestBitrateIndex = mainWrapperVariantIndices[0];
      int lowestBitrate =
          multivariantPlaylist.variants.get(mainWrapperVariantIndices[0]).format.bitrate;
      for (int i = 1; i < mainWrapperVariantIndices.length; i++) {
        int variantBitrate =
            multivariantPlaylist.variants.get(mainWrapperVariantIndices[i]).format.bitrate;
        if (variantBitrate < lowestBitrate) {
          lowestBitrate = variantBitrate;
          lowestBitrateIndex = mainWrapperVariantIndices[i];
        }
      }
      streamKeys.add(
          new StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, lowestBitrateIndex));
    }
    return streamKeys;
  }

  @Override
  public long selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    // 将每个选择和流映射到子周期索引。
    int[] streamChildIndices = new int[selections.length];
    int[] selectionChildIndices = new int[selections.length];
    for (int i = 0; i < selections.length; i++) {
      streamChildIndices[i] =
          streams[i] == null ? C.INDEX_UNSET : streamWrapperIndices.get(streams[i]);
      selectionChildIndices[i] = C.INDEX_UNSET;
      if (selections[i] != null) {
        TrackGroup trackGroup = selections[i].getTrackGroup();
        for (int j = 0; j < sampleStreamWrappers.length; j++) {
          if (sampleStreamWrappers[j].getTrackGroups().indexOf(trackGroup) != C.INDEX_UNSET) {
            selectionChildIndices[i] = j;
            break;
          }
        }
      }
    }

    boolean forceReset = false;
    streamWrapperIndices.clear();
    // 为每个子对象选择轨道，将生成的流复制回新的流数组。
    SampleStream[] newStreams = new SampleStream[selections.length];
    @NullableType SampleStream[] childStreams = new SampleStream[selections.length];
    @NullableType ExoTrackSelection[] childSelections = new ExoTrackSelection[selections.length];
    int newEnabledSampleStreamWrapperCount = 0;
    HlsSampleStreamWrapper[] newEnabledSampleStreamWrappers =
        new HlsSampleStreamWrapper[sampleStreamWrappers.length];
    for (int i = 0; i < sampleStreamWrappers.length; i++) {
      for (int j = 0; j < selections.length; j++) {
        childStreams[j] = streamChildIndices[j] == i ? streams[j] : null;
        childSelections[j] = selectionChildIndices[j] == i ? selections[j] : null;
      }
      HlsSampleStreamWrapper sampleStreamWrapper = sampleStreamWrappers[i];
      boolean wasReset =
          sampleStreamWrapper.selectTracks(
              childSelections,
              mayRetainStreamFlags,
              childStreams,
              streamResetFlags,
              positionUs,
              forceReset);
      boolean wrapperEnabled = false;
      for (int j = 0; j < selections.length; j++) {
        SampleStream childStream = childStreams[j];
        if (selectionChildIndices[j] == i) {
          // 确保子对象为选择提供了一个流。
          Assertions.checkNotNull(childStream);
          newStreams[j] = childStream;
          wrapperEnabled = true;
          streamWrapperIndices.put(childStream, i);
        } else if (streamChildIndices[j] == i) {
          // 确保子对象清除了任何先前的流。
          Assertions.checkState(childStream == null);
        }
      }
      if (wrapperEnabled) {
        newEnabledSampleStreamWrappers[newEnabledSampleStreamWrapperCount] = sampleStreamWrapper;
        if (newEnabledSampleStreamWrapperCount++ == 0) {
          // 第一个启用的包装器始终允许初始化时间戳调整器。注意，第一个包装器将对应于变体，或者音频渲染，或者文本渲染，按此顺序。
          sampleStreamWrapper.setIsPrimaryTimestampSource(true);
          if (wasReset
              || enabledSampleStreamWrappers.length == 0
              || sampleStreamWrapper != enabledSampleStreamWrappers[0]) {
            // 负责初始化时间戳调整器的包装器被重置或更改。我们需要重置时间戳调整器提供程序和其他所有包装器。
            timestampAdjusterProvider.reset();
            forceReset = true;
          }
        } else {
          // 其他包装器如果包含音频或视频，则也允许初始化时间戳调整器，因为它们预计包含密集样本。除了上述情况（未启用任何变体或音频渲染包装器）外，文本包装器不允许。
          sampleStreamWrapper.setIsPrimaryTimestampSource(i < audioVideoSampleStreamWrapperCount);
        }
      }
    }
    // 将新流复制回流数组。
    System.arraycopy(newStreams, 0, streams, 0, newStreams.length);
    // 更新本地状态。
    enabledSampleStreamWrappers =
        Util.nullSafeArrayCopy(newEnabledSampleStreamWrappers, newEnabledSampleStreamWrapperCount);
    ImmutableList<HlsSampleStreamWrapper> enabledSampleStreamWrappersList =
        ImmutableList.copyOf(enabledSampleStreamWrappers);
    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.create(
            enabledSampleStreamWrappersList,
            Lists.transform(
                enabledSampleStreamWrappersList,
                sampleStreamWrapper -> sampleStreamWrapper.getTrackGroups().getTrackTypes()));
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    for (HlsSampleStreamWrapper sampleStreamWrapper : enabledSampleStreamWrappers) {
      sampleStreamWrapper.discardBuffer(positionUs, toKeyframe);
    }
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    compositeSequenceableLoader.reevaluateBuffer(positionUs);
  }

  @Override
  public boolean continueLoading(LoadingInfo loadingInfo) {
    if (trackGroups == null) {
      // 准备工作仍在进行中。
      for (HlsSampleStreamWrapper wrapper : sampleStreamWrappers) {
        wrapper.continuePreparing();
      }
      return false;
    } else {
      return compositeSequenceableLoader.continueLoading(loadingInfo);
    }
  }

  @Override
  public boolean isLoading() {
    return compositeSequenceableLoader.isLoading();
  }

  @Override
  public long getNextLoadPositionUs() {
    return compositeSequenceableLoader.getNextLoadPositionUs();
  }

  @Override
  public long readDiscontinuity() {
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    return compositeSequenceableLoader.getBufferedPositionUs();
  }

  @Override
  public long seekToUs(long positionUs) {
    if (enabledSampleStreamWrappers.length > 0) {
      // 如果负责初始化时间戳调整器的包装器被重置，我们需要重置所有包装器。否则，每个包装器可以独立决定是否重置。
      boolean forceReset = enabledSampleStreamWrappers[0].seekToUs(positionUs, false);
      for (int i = 1; i < enabledSampleStreamWrappers.length; i++) {
        enabledSampleStreamWrappers[i].seekToUs(positionUs, forceReset);
      }
      if (forceReset) {
        timestampAdjusterProvider.reset();
      }
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    long seekTargetUs = positionUs;
    for (HlsSampleStreamWrapper sampleStreamWrapper : enabledSampleStreamWrappers) {
      if (sampleStreamWrapper.isVideoSampleStream()) {
        seekTargetUs = sampleStreamWrapper.getAdjustedSeekPositionUs(positionUs, seekParameters);
        break;
      }
    }
    return seekTargetUs;
  }

  // HlsSampleStreamWrapper.Callback 接口的实现。

  // PlaylistListener 接口的实现。

  @Override
  public void onPlaylistChanged() {
    for (HlsSampleStreamWrapper streamWrapper : sampleStreamWrappers) {
      streamWrapper.onPlaylistUpdated();
    }
    mediaPeriodCallback.onContinueLoadingRequested(this);
  }

  @Override
  public boolean onPlaylistError(
      Uri url, LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo, boolean forceRetry) {
    boolean exclusionSucceeded = true;
    for (HlsSampleStreamWrapper streamWrapper : sampleStreamWrappers) {
      exclusionSucceeded &= streamWrapper.onPlaylistError(url, loadErrorInfo, forceRetry);
    }
    mediaPeriodCallback.onContinueLoadingRequested(this);
    return exclusionSucceeded;
  }

  // 内部方法。

  private void buildAndPrepareSampleStreamWrappers(long positionUs) {
    HlsMultivariantPlaylist multivariantPlaylist =
        Assertions.checkNotNull(playlistTracker.getMultivariantPlaylist());
    Map<String, DrmInitData> overridingDrmInitData =
        useSessionKeys
            ? deriveOverridingDrmInitData(multivariantPlaylist.sessionKeyDrmInitData)
            : Collections.emptyMap();

    boolean hasVariants = !multivariantPlaylist.variants.isEmpty();
    List<Rendition> audioRenditions = multivariantPlaylist.audios;
    List<Rendition> subtitleRenditions = multivariantPlaylist.subtitles;

    pendingPrepareCount = 0;
    ArrayList<HlsSampleStreamWrapper> sampleStreamWrappers = new ArrayList<>();
    ArrayList<int[]> manifestUrlIndicesPerWrapper = new ArrayList<>();

    if (hasVariants) {
      buildAndPrepareMainSampleStreamWrapper(
          multivariantPlaylist,
          positionUs,
          sampleStreamWrappers,
          manifestUrlIndicesPerWrapper,
          overridingDrmInitData);
    }

    // TODO: 在此处构建视频流包装器。
    buildAndPrepareAudioSampleStreamWrappers(
        positionUs,
        audioRenditions,
        sampleStreamWrappers,
        manifestUrlIndicesPerWrapper,
        overridingDrmInitData);

    audioVideoSampleStreamWrapperCount = sampleStreamWrappers.size();

    // 字幕流包装器。我们始终可以使用多变量播放列表信息来准备这些。
    for (int i = 0; i < subtitleRenditions.size(); i++) {
      Rendition subtitleRendition = subtitleRenditions.get(i);
      String sampleStreamWrapperUid = "subtitle:" + i + ":" + subtitleRendition.name;
      // HlsChunkSource 用于创建提取器的格式
      Format originalSubtitleFormat = subtitleRendition.format;
      HlsSampleStreamWrapper sampleStreamWrapper =
          buildSampleStreamWrapper(
              sampleStreamWrapperUid,
              C.TRACK_TYPE_TEXT,
              new Uri[] {subtitleRendition.url},
              new Format[] {originalSubtitleFormat},
              null,
              Collections.emptyList(),
              overridingDrmInitData,
              positionUs);
      manifestUrlIndicesPerWrapper.add(new int[] {i});
      sampleStreamWrappers.add(sampleStreamWrapper);
      sampleStreamWrapper.prepareWithMultivariantPlaylistInfo(
          new TrackGroup[] {
              new TrackGroup(
                  sampleStreamWrapperUid,
                  extractorFactory.getOutputTextFormat(originalSubtitleFormat))
          },
          /* primaryTrackGroupIndex= */ 0);
    }

    this.sampleStreamWrappers = sampleStreamWrappers.toArray(new HlsSampleStreamWrapper[0]);
    this.manifestUrlIndicesPerWrapper = manifestUrlIndicesPerWrapper.toArray(new int[0][]);
    pendingPrepareCount = this.sampleStreamWrappers.length;
    // 设置主时间戳源并触发准备（如果尚未准备）
    for (int i = 0; i < audioVideoSampleStreamWrapperCount; i++) {
      this.sampleStreamWrappers[i].setIsPrimaryTimestampSource(true);
    }
    for (HlsSampleStreamWrapper sampleStreamWrapper : this.sampleStreamWrappers) {
      sampleStreamWrapper.continuePreparing();
    }
    // 在准备期间，所有包装器都处于启用状态。
    enabledSampleStreamWrappers = this.sampleStreamWrappers;
  }

  /**
   * 此方法创建并启动主 {@link HlsSampleStreamWrapper} 的准备工作。
   *
   * <p>主样本流包装器是 {@link #sampleStreamWrappers} 的第一个元素。它为多变量播放列表中的变体 URL 提供 {@link SampleStream}。它可能是自适应的，并且可能包含多个多路复用轨道。
   *
   * <p>如果允许无块准备，媒体周期将尝试在不下载片段的情况下进行准备。这仅在变体包含 CODECS 属性时可行。如果不包含，则需要进行传统的片段下载准备。以下要点适用于无块准备：
   *
   * <ul>
   *   <li>如果编解码器列表包含音频条目，并且多变量播放列表包含不带 URI 属性的 EXT-X-MEDIA 标签或不包含任何 EXT-X-MEDIA 标签，则将暴露多路复用的音频轨道。
   *   <li>仅当多变量播放列表声明了隐藏字幕时，才会暴露隐藏字幕。
   *   <li>预先暴露 ID3 轨道，以防片段包含 ID3 轨道。
   * </ul>
   *
   * @param multivariantPlaylist HLS 多变量播放列表。
   * @param positionUs 如果准备需要任何片段下载，则以微秒为单位的下载起始位置。否则忽略。
   * @param sampleStreamWrappers 要添加构建的主样本流包装器的列表。
   * @param manifestUrlIndicesPerWrapper 要添加所选变体索引的列表。
   * @param overridingDrmInitData 按保护方案类型（即 {@link DrmInitData#schemeType}）键控的覆盖 {@link DrmInitData}。
   */
  private void buildAndPrepareMainSampleStreamWrapper(
      HlsMultivariantPlaylist multivariantPlaylist,
      long positionUs,
      List<HlsSampleStreamWrapper> sampleStreamWrappers,
      List<int[]> manifestUrlIndicesPerWrapper,
      Map<String, DrmInitData> overridingDrmInitData) {
    int[] variantTypes = new int[multivariantPlaylist.variants.size()];
    int videoVariantCount = 0;
    int audioVariantCount = 0;
    for (int i = 0; i < multivariantPlaylist.variants.size(); i++) {
      Variant variant = multivariantPlaylist.variants.get(i);
      Format format = variant.format;
      if (format.height > 0 || Util.getCodecsOfType(format.codecs, C.TRACK_TYPE_VIDEO) != null) {
        variantTypes[i] = C.TRACK_TYPE_VIDEO;
        videoVariantCount++;
      } else if (Util.getCodecsOfType(format.codecs, C.TRACK_TYPE_AUDIO) != null) {
        variantTypes[i] = C.TRACK_TYPE_AUDIO;
        audioVariantCount++;
      } else {
        variantTypes[i] = C.TRACK_TYPE_UNKNOWN;
      }
    }
    boolean useVideoVariantsOnly = false;
    boolean useNonAudioVariantsOnly = false;
    int selectedVariantsCount = variantTypes.length;
    if (videoVariantCount > 0) {
      // 我们已经确定了一些变体肯定包含视频。假设多变量播放列表中的变体标记一致，因此我们拥有完整的集合。过滤掉其他变体，这些变体可能仅包含音频。
      useVideoVariantsOnly = true;
      selectedVariantsCount = videoVariantCount;
    } else if (audioVariantCount < variantTypes.length) {
      // 我们已经确定了一些变体（但不是全部）为仅音频变体。过滤掉它们，留下剩余的变体，这些变体可能包含视频。
      useNonAudioVariantsOnly = true;
      selectedVariantsCount = variantTypes.length - audioVariantCount;
    }
    Uri[] selectedPlaylistUrls = new Uri[selectedVariantsCount];
    Format[] selectedPlaylistFormats = new Format[selectedVariantsCount];
    int[] selectedVariantIndices = new int[selectedVariantsCount];
    int outIndex = 0;
    for (int i = 0; i < multivariantPlaylist.variants.size(); i++) {
      if ((!useVideoVariantsOnly || variantTypes[i] == C.TRACK_TYPE_VIDEO)
          && (!useNonAudioVariantsOnly || variantTypes[i] != C.TRACK_TYPE_AUDIO)) {
        Variant variant = multivariantPlaylist.variants.get(i);
        selectedPlaylistUrls[outIndex] = variant.url;
        selectedPlaylistFormats[outIndex] = variant.format;
        selectedVariantIndices[outIndex++] = i;
      }
    }
    String codecs = selectedPlaylistFormats[0].codecs;
    int numberOfVideoCodecs = Util.getCodecCountOfType(codecs, C.TRACK_TYPE_VIDEO);
    int numberOfAudioCodecs = Util.getCodecCountOfType(codecs, C.TRACK_TYPE_AUDIO);
    boolean codecsStringAllowsChunklessPreparation =
        (numberOfAudioCodecs == 1
                || (numberOfAudioCodecs == 0 && multivariantPlaylist.audios.isEmpty()))
            && numberOfVideoCodecs <= 1
            && numberOfAudioCodecs + numberOfVideoCodecs > 0;
    @C.TrackType
    int trackType =
        !useVideoVariantsOnly && numberOfAudioCodecs > 0
            ? C.TRACK_TYPE_AUDIO
            : C.TRACK_TYPE_DEFAULT;
    String sampleStreamWrapperUid = "main";
    HlsSampleStreamWrapper sampleStreamWrapper =
        buildSampleStreamWrapper(
            sampleStreamWrapperUid,
            trackType,
            selectedPlaylistUrls,
            selectedPlaylistFormats,
            multivariantPlaylist.muxedAudioFormat,
            multivariantPlaylist.muxedCaptionFormats,
            overridingDrmInitData,
            positionUs);
    sampleStreamWrappers.add(sampleStreamWrapper);
    manifestUrlIndicesPerWrapper.add(selectedVariantIndices);
    if (allowChunklessPreparation && codecsStringAllowsChunklessPreparation) {
      List<TrackGroup> muxedTrackGroups = new ArrayList<>();
      if (numberOfVideoCodecs > 0) {
        Format[] videoFormats = new Format[selectedVariantsCount];
        for (int i = 0; i < videoFormats.length; i++) {
          videoFormats[i] = deriveVideoFormat(selectedPlaylistFormats[i]);
        }
        muxedTrackGroups.add(new TrackGroup(sampleStreamWrapperUid, videoFormats));

        if (numberOfAudioCodecs > 0
            && (multivariantPlaylist.muxedAudioFormat != null
                || multivariantPlaylist.audios.isEmpty())) {
          muxedTrackGroups.add(
              new TrackGroup(
                  /* id= */ sampleStreamWrapperUid + ":audio",
                  deriveAudioFormat(
                      selectedPlaylistFormats[0],
                      multivariantPlaylist.muxedAudioFormat,
                      /* isPrimaryTrackInVariant= */ false)));
        }
        List<Format> ccFormats = multivariantPlaylist.muxedCaptionFormats;
        if (ccFormats != null) {
          for (int i = 0; i < ccFormats.size(); i++) {
            String ccId = sampleStreamWrapperUid + ":cc:" + i;
            muxedTrackGroups.add(
                new TrackGroup(ccId, extractorFactory.getOutputTextFormat(ccFormats.get(i))));
          }
        }
      } else /* numberOfAudioCodecs > 0 */ {
        // Variants only contain audio.
        Format[] audioFormats = new Format[selectedVariantsCount];
        for (int i = 0; i < audioFormats.length; i++) {
          audioFormats[i] =
              deriveAudioFormat(
                  /* variantFormat= */ selectedPlaylistFormats[i],
                  multivariantPlaylist.muxedAudioFormat,
                  /* isPrimaryTrackInVariant= */ true);
        }
        muxedTrackGroups.add(new TrackGroup(sampleStreamWrapperUid, audioFormats));
      }

      TrackGroup id3TrackGroup =
          new TrackGroup(
              /* id= */ sampleStreamWrapperUid + ":id3",
              new Format.Builder()
                  .setId("ID3")
                  .setSampleMimeType(MimeTypes.APPLICATION_ID3)
                  .build());
      muxedTrackGroups.add(id3TrackGroup);

      sampleStreamWrapper.prepareWithMultivariantPlaylistInfo(
          muxedTrackGroups.toArray(new TrackGroup[0]),
          /* primaryTrackGroupIndex= */ 0,
          /* optionalTrackGroupsIndices...= */ muxedTrackGroups.indexOf(id3TrackGroup));
    }
  }

  private void buildAndPrepareAudioSampleStreamWrappers(
      long positionUs,
      List<Rendition> audioRenditions,
      List<HlsSampleStreamWrapper> sampleStreamWrappers,
      List<int[]> manifestUrlsIndicesPerWrapper,
      Map<String, DrmInitData> overridingDrmInitData) {
    ArrayList<Uri> scratchPlaylistUrls =
        new ArrayList<>(/* initialCapacity= */ audioRenditions.size());
    ArrayList<Format> scratchPlaylistFormats =
        new ArrayList<>(/* initialCapacity= */ audioRenditions.size());
    ArrayList<Integer> scratchIndicesList =
        new ArrayList<>(/* initialCapacity= */ audioRenditions.size());
    HashSet<String> alreadyGroupedNames = new HashSet<>();
    for (int renditionByNameIndex = 0;
        renditionByNameIndex < audioRenditions.size();
        renditionByNameIndex++) {
      String name = audioRenditions.get(renditionByNameIndex).name;
      if (!alreadyGroupedNames.add(name)) {
        // This name already has a corresponding group.
        continue;
      }

      boolean codecStringsAllowChunklessPreparation = true;
      scratchPlaylistUrls.clear();
      scratchPlaylistFormats.clear();
      scratchIndicesList.clear();
      // Group all renditions with matching name.
      for (int renditionIndex = 0; renditionIndex < audioRenditions.size(); renditionIndex++) {
        if (Util.areEqual(name, audioRenditions.get(renditionIndex).name)) {
          Rendition rendition = audioRenditions.get(renditionIndex);
          scratchIndicesList.add(renditionIndex);
          scratchPlaylistUrls.add(rendition.url);
          scratchPlaylistFormats.add(rendition.format);
          codecStringsAllowChunklessPreparation &=
              Util.getCodecCountOfType(rendition.format.codecs, C.TRACK_TYPE_AUDIO) == 1;
        }
      }

      String sampleStreamWrapperUid = "audio:" + name;
      HlsSampleStreamWrapper sampleStreamWrapper =
          buildSampleStreamWrapper(
              sampleStreamWrapperUid,
              C.TRACK_TYPE_AUDIO,
              scratchPlaylistUrls.toArray(Util.castNonNullTypeArray(new Uri[0])),
              scratchPlaylistFormats.toArray(new Format[0]),
              /* muxedAudioFormat= */ null,
              /* muxedCaptionFormats= */ Collections.emptyList(),
              overridingDrmInitData,
              positionUs);
      manifestUrlsIndicesPerWrapper.add(Ints.toArray(scratchIndicesList));
      sampleStreamWrappers.add(sampleStreamWrapper);

      if (allowChunklessPreparation && codecStringsAllowChunklessPreparation) {
        Format[] renditionFormats = scratchPlaylistFormats.toArray(new Format[0]);
        sampleStreamWrapper.prepareWithMultivariantPlaylistInfo(
            new TrackGroup[] {new TrackGroup(sampleStreamWrapperUid, renditionFormats)},
            /* primaryTrackGroupIndex= */ 0);
      }
    }
  }

  private HlsSampleStreamWrapper buildSampleStreamWrapper(
      String uid,
      @C.TrackType int trackType,
      Uri[] playlistUrls,
      Format[] playlistFormats,
      @Nullable Format muxedAudioFormat,
      @Nullable List<Format> muxedCaptionFormats,
      Map<String, DrmInitData> overridingDrmInitData,
      long positionUs) {
    HlsChunkSource defaultChunkSource =
        new HlsChunkSource(
            extractorFactory,
            playlistTracker,
            playlistUrls,
            playlistFormats,
            dataSourceFactory,
            mediaTransferListener,
            timestampAdjusterProvider,
            timestampAdjusterInitializationTimeoutMs,
            muxedCaptionFormats,
            playerId,
            cmcdConfiguration);
    return new HlsSampleStreamWrapper(
        uid,
        trackType,
        /* callback= */ sampleStreamWrapperCallback,
        defaultChunkSource,
        overridingDrmInitData,
        allocator,
        positionUs,
        muxedAudioFormat,
        drmSessionManager,
        drmEventDispatcher,
        loadErrorHandlingPolicy,
        eventDispatcher,
        metadataType);
  }

  private static Map<String, DrmInitData> deriveOverridingDrmInitData(
      List<DrmInitData> sessionKeyDrmInitData) {
    ArrayList<DrmInitData> mutableSessionKeyDrmInitData = new ArrayList<>(sessionKeyDrmInitData);
    HashMap<String, DrmInitData> drmInitDataBySchemeType = new HashMap<>();
    for (int i = 0; i < mutableSessionKeyDrmInitData.size(); i++) {
      DrmInitData drmInitData = sessionKeyDrmInitData.get(i);
      String scheme = drmInitData.schemeType;
      // 合并任何具有相同方案类型的后续 drmInitData 实例。这是有效的，因为 HlsMediaSource.Builder.setUseSessionKeys 中记录的假设，并且对于将不同 CDN（例如 Widevine 和 PlayReady）的数据合并到单个 drmInitData 中是必要的。
      int j = i + 1;
      while (j < mutableSessionKeyDrmInitData.size()) {
        DrmInitData nextDrmInitData = mutableSessionKeyDrmInitData.get(j);
        if (TextUtils.equals(nextDrmInitData.schemeType, scheme)) {
          drmInitData = drmInitData.merge(nextDrmInitData);
          mutableSessionKeyDrmInitData.remove(j);
        } else {
          j++;
        }
      }
      drmInitDataBySchemeType.put(scheme, drmInitData);
    }
    return drmInitDataBySchemeType;
  }

  private static Format deriveVideoFormat(Format variantFormat) {
    @Nullable String codecs = Util.getCodecsOfType(variantFormat.codecs, C.TRACK_TYPE_VIDEO);
    @Nullable String sampleMimeType = MimeTypes.getMediaMimeType(codecs);
    return new Format.Builder()
        .setId(variantFormat.id)
        .setLabel(variantFormat.label)
        .setLabels(variantFormat.labels)
        .setContainerMimeType(variantFormat.containerMimeType)
        .setSampleMimeType(sampleMimeType)
        .setCodecs(codecs)
        .setMetadata(variantFormat.metadata)
        .setAverageBitrate(variantFormat.averageBitrate)
        .setPeakBitrate(variantFormat.peakBitrate)
        .setWidth(variantFormat.width)
        .setHeight(variantFormat.height)
        .setFrameRate(variantFormat.frameRate)
        .setSelectionFlags(variantFormat.selectionFlags)
        .setRoleFlags(variantFormat.roleFlags)
        .build();
  }

  private static Format deriveAudioFormat(
      Format variantFormat, @Nullable Format mediaTagFormat, boolean isPrimaryTrackInVariant) {
    @Nullable String codecs;
    @Nullable Metadata metadata;
    int channelCount = Format.NO_VALUE;
    int selectionFlags = 0;
    int roleFlags = 0;
    @Nullable String language = null;
    @Nullable String label = null;
    List<Label> labels = ImmutableList.of();
    if (mediaTagFormat != null) {
      codecs = mediaTagFormat.codecs;
      metadata = mediaTagFormat.metadata;
      channelCount = mediaTagFormat.channelCount;
      selectionFlags = mediaTagFormat.selectionFlags;
      roleFlags = mediaTagFormat.roleFlags;
      language = mediaTagFormat.language;
      label = mediaTagFormat.label;
      labels = mediaTagFormat.labels;
    } else {
      codecs = Util.getCodecsOfType(variantFormat.codecs, C.TRACK_TYPE_AUDIO);
      metadata = variantFormat.metadata;
      if (isPrimaryTrackInVariant) {
        channelCount = variantFormat.channelCount;
        selectionFlags = variantFormat.selectionFlags;
        roleFlags = variantFormat.roleFlags;
        language = variantFormat.language;
        label = variantFormat.label;
        labels = variantFormat.labels;
      }
    }
    @Nullable String sampleMimeType = MimeTypes.getMediaMimeType(codecs);
    int averageBitrate = isPrimaryTrackInVariant ? variantFormat.averageBitrate : Format.NO_VALUE;
    int peakBitrate = isPrimaryTrackInVariant ? variantFormat.peakBitrate : Format.NO_VALUE;
    return new Format.Builder()
        .setId(variantFormat.id)
        .setLabel(label)
        .setLabels(labels)
        .setContainerMimeType(variantFormat.containerMimeType)
        .setSampleMimeType(sampleMimeType)
        .setCodecs(codecs)
        .setMetadata(metadata)
        .setAverageBitrate(averageBitrate)
        .setPeakBitrate(peakBitrate)
        .setChannelCount(channelCount)
        .setSelectionFlags(selectionFlags)
        .setRoleFlags(roleFlags)
        .setLanguage(language)
        .build();
  }

  private class SampleStreamWrapperCallback implements HlsSampleStreamWrapper.Callback {
    @Override
    public void onPrepared() {
      if (--pendingPrepareCount > 0) {
        return;
      }

      int totalTrackGroupCount = 0;
      for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
        totalTrackGroupCount += sampleStreamWrapper.getTrackGroups().length;
      }
      TrackGroup[] trackGroupArray = new TrackGroup[totalTrackGroupCount];
      int trackGroupIndex = 0;
      for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
        int wrapperTrackGroupCount = sampleStreamWrapper.getTrackGroups().length;
        for (int j = 0; j < wrapperTrackGroupCount; j++) {
          trackGroupArray[trackGroupIndex++] = sampleStreamWrapper.getTrackGroups().get(j);
        }
      }
      trackGroups = new TrackGroupArray(trackGroupArray);
      mediaPeriodCallback.onPrepared(HlsMediaPeriod.this);
    }

    @Override
    public void onPlaylistRefreshRequired(Uri url) {
      playlistTracker.refreshPlaylist(url);
    }

    @Override
    public void onContinueLoadingRequested(HlsSampleStreamWrapper sampleStreamWrapper) {
      mediaPeriodCallback.onContinueLoadingRequested(HlsMediaPeriod.this);
    }
  }
}
