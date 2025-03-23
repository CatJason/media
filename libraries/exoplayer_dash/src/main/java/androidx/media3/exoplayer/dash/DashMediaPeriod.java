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

import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.util.Pair;
import android.util.SparseArray;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.StreamKey;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.dash.PlayerEmsgHandler.PlayerEmsgCallback;
import androidx.media3.exoplayer.dash.PlayerEmsgHandler.PlayerTrackEmsgHandler;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.Descriptor;
import androidx.media3.exoplayer.dash.manifest.EventStream;
import androidx.media3.exoplayer.dash.manifest.Period;
import androidx.media3.exoplayer.dash.manifest.Representation;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.CompositeSequenceableLoaderFactory;
import androidx.media3.exoplayer.source.EmptySampleStream;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.source.MediaSourceEventListener.EventDispatcher;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.SequenceableLoader;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.source.chunk.ChunkSampleStream;
import androidx.media3.exoplayer.source.chunk.ChunkSampleStream.EmbeddedSampleStream;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoaderErrorThrower;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A DASH {@link MediaPeriod}. */
/* package */ final class DashMediaPeriod
    implements MediaPeriod,
        SequenceableLoader.Callback<ChunkSampleStream<DashChunkSource>>,
        ChunkSampleStream.ReleaseCallback<DashChunkSource> {

  // Defined by ANSI/SCTE 214-1 2016 7.2.3.
  private static final Pattern CEA608_SERVICE_DESCRIPTOR_REGEX = Pattern.compile("CC([1-4])=(.+)");
  // Defined by ANSI/SCTE 214-1 2016 7.2.2.
  private static final Pattern CEA708_SERVICE_DESCRIPTOR_REGEX =
      Pattern.compile("([1-4])=lang:(\\w+)(,.+)?");

  /* package */ final int id;
  private final DashChunkSource.Factory chunkSourceFactory;
  @Nullable private final TransferListener transferListener;
  @Nullable private final CmcdConfiguration cmcdConfiguration;
  private final DrmSessionManager drmSessionManager;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final BaseUrlExclusionList baseUrlExclusionList;
  private final long elapsedRealtimeOffsetMs;
  private final LoaderErrorThrower manifestLoaderErrorThrower;
  private final Allocator allocator;
  private final TrackGroupArray trackGroups;
  private final TrackGroupInfo[] trackGroupInfos;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final PlayerEmsgHandler playerEmsgHandler;
  private final IdentityHashMap<ChunkSampleStream<DashChunkSource>, PlayerTrackEmsgHandler>
      trackEmsgHandlerBySampleStream;
  private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
  private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;
  private final PlayerId playerId;

  @Nullable private Callback callback;
  private ChunkSampleStream<DashChunkSource>[] sampleStreams;
  private EventSampleStream[] eventSampleStreams;
  private SequenceableLoader compositeSequenceableLoader;
  private DashManifest manifest;
  private int periodIndex;
  private List<EventStream> eventStreams;
  private boolean canReportInitialDiscontinuity;
  private long initialStartTimeUs;

  public DashMediaPeriod(
      int id,
      DashManifest manifest,
      BaseUrlExclusionList baseUrlExclusionList,
      int periodIndex,
      DashChunkSource.Factory chunkSourceFactory,
      @Nullable TransferListener transferListener,
      @Nullable CmcdConfiguration cmcdConfiguration,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      EventDispatcher mediaSourceEventDispatcher,
      long elapsedRealtimeOffsetMs,
      LoaderErrorThrower manifestLoaderErrorThrower,
      Allocator allocator,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      PlayerEmsgCallback playerEmsgCallback,
      PlayerId playerId) {
    this.id = id;
    this.manifest = manifest;
    this.baseUrlExclusionList = baseUrlExclusionList;
    this.periodIndex = periodIndex;
    this.chunkSourceFactory = chunkSourceFactory;
    this.transferListener = transferListener;
    this.cmcdConfiguration = cmcdConfiguration;
    this.drmSessionManager = drmSessionManager;
    this.drmEventDispatcher = drmEventDispatcher;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
    this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.allocator = allocator;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.playerId = playerId;
    this.canReportInitialDiscontinuity = true;
    playerEmsgHandler = new PlayerEmsgHandler(manifest, playerEmsgCallback, allocator);
    sampleStreams = newSampleStreamArray(0);
    eventSampleStreams = new EventSampleStream[0];
    trackEmsgHandlerBySampleStream = new IdentityHashMap<>();
    compositeSequenceableLoader = compositeSequenceableLoaderFactory.empty();
    Period period = manifest.getPeriod(periodIndex);
    eventStreams = period.eventStreams;
    Pair<TrackGroupArray, TrackGroupInfo[]> result =
        buildTrackGroups(
            drmSessionManager, chunkSourceFactory, period.adaptationSets, eventStreams);
    trackGroups = result.first;
    trackGroupInfos = result.second;
  }

  /**
   * Updates the {@link DashManifest} and the index of this period in the manifest.
   *
   * @param manifest The updated manifest.
   * @param periodIndex the new index of this period in the updated manifest.
   */
  public void updateManifest(DashManifest manifest, int periodIndex) {
    this.manifest = manifest;
    this.periodIndex = periodIndex;
    playerEmsgHandler.updateManifest(manifest);
    if (sampleStreams != null) {
      for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
        sampleStream.getChunkSource().updateManifest(manifest, periodIndex);
      }
      callback.onContinueLoadingRequested(this);
    }
    eventStreams = manifest.getPeriod(periodIndex).eventStreams;
    for (EventSampleStream eventSampleStream : eventSampleStreams) {
      for (EventStream eventStream : eventStreams) {
        if (eventStream.id().equals(eventSampleStream.eventStreamId())) {
          int lastPeriodIndex = manifest.getPeriodCount() - 1;
          eventSampleStream.updateEventStream(
              eventStream,
              /* eventStreamAppendable= */ manifest.dynamic && periodIndex == lastPeriodIndex);
          break;
        }
      }
    }
  }

  public void release() {
    playerEmsgHandler.release();
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.release(this);
    }
    callback = null;
  }

  // ChunkSampleStream.ReleaseCallback implementation.

  @Override
  public synchronized void onSampleStreamReleased(ChunkSampleStream<DashChunkSource> stream) {
    PlayerTrackEmsgHandler trackEmsgHandler = trackEmsgHandlerBySampleStream.remove(stream);
    if (trackEmsgHandler != null) {
      trackEmsgHandler.release();
    }
  }

  // MediaPeriod implementation.

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.callback = callback;
    callback.onPrepared(this);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    manifestLoaderErrorThrower.maybeThrowError();
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  @Override
  public List<StreamKey> getStreamKeys(List<ExoTrackSelection> trackSelections) {
    List<AdaptationSet> manifestAdaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
    List<StreamKey> streamKeys = new ArrayList<>();
    for (ExoTrackSelection trackSelection : trackSelections) {
      int trackGroupIndex = trackGroups.indexOf(trackSelection.getTrackGroup());
      TrackGroupInfo trackGroupInfo = trackGroupInfos[trackGroupIndex];
      if (trackGroupInfo.trackGroupCategory != TrackGroupInfo.CATEGORY_PRIMARY) {
        // Ignore non-primary tracks.
        continue;
      }
      int[] adaptationSetIndices = trackGroupInfo.adaptationSetIndices;
      int[] trackIndices = new int[trackSelection.length()];
      for (int i = 0; i < trackSelection.length(); i++) {
        trackIndices[i] = trackSelection.getIndexInTrackGroup(i);
      }
      Arrays.sort(trackIndices);

      int currentAdaptationSetIndex = 0;
      int totalTracksInPreviousAdaptationSets = 0;
      int tracksInCurrentAdaptationSet =
          manifestAdaptationSets.get(adaptationSetIndices[0]).representations.size();
      for (int trackIndex : trackIndices) {
        while (trackIndex >= totalTracksInPreviousAdaptationSets + tracksInCurrentAdaptationSet) {
          currentAdaptationSetIndex++;
          totalTracksInPreviousAdaptationSets += tracksInCurrentAdaptationSet;
          tracksInCurrentAdaptationSet =
              manifestAdaptationSets
                  .get(adaptationSetIndices[currentAdaptationSetIndex])
                  .representations
                  .size();
        }
        streamKeys.add(
            new StreamKey(
                periodIndex,
                adaptationSetIndices[currentAdaptationSetIndex],
                trackIndex - totalTracksInPreviousAdaptationSets));
      }
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
    int[] streamIndexToTrackGroupIndex = getStreamIndexToTrackGroupIndex(selections);
    releaseDisabledStreams(selections, mayRetainStreamFlags, streams);
    releaseOrphanEmbeddedStreams(selections, streams, streamIndexToTrackGroupIndex);
    selectNewStreams(
        selections, streams, streamResetFlags, positionUs, streamIndexToTrackGroupIndex);

    ArrayList<ChunkSampleStream<DashChunkSource>> sampleStreamList = new ArrayList<>();
    ArrayList<EventSampleStream> eventSampleStreamList = new ArrayList<>();
    for (SampleStream sampleStream : streams) {
      if (sampleStream instanceof ChunkSampleStream) {
        @SuppressWarnings("unchecked")
        ChunkSampleStream<DashChunkSource> stream =
            (ChunkSampleStream<DashChunkSource>) sampleStream;
        sampleStreamList.add(stream);
      } else if (sampleStream instanceof EventSampleStream) {
        eventSampleStreamList.add((EventSampleStream) sampleStream);
      }
    }
    sampleStreams = newSampleStreamArray(sampleStreamList.size());
    sampleStreamList.toArray(sampleStreams);
    eventSampleStreams = new EventSampleStream[eventSampleStreamList.size()];
    eventSampleStreamList.toArray(eventSampleStreams);

    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.create(
            sampleStreamList,
            Lists.transform(sampleStreamList, s -> ImmutableList.of(s.primaryTrackType)));
    if (canReportInitialDiscontinuity) {
      canReportInitialDiscontinuity = false;
      initialStartTimeUs = positionUs;
    }
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.discardBuffer(positionUs, toKeyframe);
    }
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    compositeSequenceableLoader.reevaluateBuffer(positionUs);
  }

  @Override
  public boolean continueLoading(LoadingInfo loadingInfo) {
    return compositeSequenceableLoader.continueLoading(loadingInfo);
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
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      if (sampleStream.consumeInitialDiscontinuity()) {
        return initialStartTimeUs;
      }
    }
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    return compositeSequenceableLoader.getBufferedPositionUs();
  }

  @Override
  public long seekToUs(long positionUs) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    for (EventSampleStream sampleStream : eventSampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      if (sampleStream.primaryTrackType == C.TRACK_TYPE_VIDEO) {
        return sampleStream.getAdjustedSeekPositionUs(positionUs, seekParameters);
      }
    }
    return positionUs;
  }

  // SequenceableLoader.Callback implementation.

  @Override
  public void onContinueLoadingRequested(ChunkSampleStream<DashChunkSource> sampleStream) {
    callback.onContinueLoadingRequested(this);
  }

  // Internal methods.

  private int[] getStreamIndexToTrackGroupIndex(ExoTrackSelection[] selections) {
    int[] streamIndexToTrackGroupIndex = new int[selections.length];
    for (int i = 0; i < selections.length; i++) {
      if (selections[i] != null) {
        streamIndexToTrackGroupIndex[i] = trackGroups.indexOf(selections[i].getTrackGroup());
      } else {
        streamIndexToTrackGroupIndex[i] = C.INDEX_UNSET;
      }
    }
    return streamIndexToTrackGroupIndex;
  }

  private void releaseDisabledStreams(
      ExoTrackSelection[] selections, boolean[] mayRetainStreamFlags, SampleStream[] streams) {
    for (int i = 0; i < selections.length; i++) {
      if (selections[i] == null || !mayRetainStreamFlags[i]) {
        if (streams[i] instanceof ChunkSampleStream) {
          @SuppressWarnings("unchecked")
          ChunkSampleStream<DashChunkSource> stream =
              (ChunkSampleStream<DashChunkSource>) streams[i];
          stream.release(this);
        } else if (streams[i] instanceof EmbeddedSampleStream) {
          ((EmbeddedSampleStream) streams[i]).release();
        }
        streams[i] = null;
      }
    }
  }

  private void releaseOrphanEmbeddedStreams(
      ExoTrackSelection[] selections, SampleStream[] streams, int[] streamIndexToTrackGroupIndex) {
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] instanceof EmptySampleStream || streams[i] instanceof EmbeddedSampleStream) {
        // We need to release an embedded stream if the corresponding primary stream is released.
        int primaryStreamIndex = getPrimaryStreamIndex(i, streamIndexToTrackGroupIndex);
        boolean mayRetainStream;
        if (primaryStreamIndex == C.INDEX_UNSET) {
          // If the corresponding primary stream is not selected, we may retain an existing
          // EmptySampleStream.
          mayRetainStream = streams[i] instanceof EmptySampleStream;
        } else {
          // If the corresponding primary stream is selected, we may retain the embedded stream if
          // the stream's parent still matches.
          mayRetainStream =
              (streams[i] instanceof EmbeddedSampleStream)
                  && ((EmbeddedSampleStream) streams[i]).parent == streams[primaryStreamIndex];
        }
        if (!mayRetainStream) {
          if (streams[i] instanceof EmbeddedSampleStream) {
            ((EmbeddedSampleStream) streams[i]).release();
          }
          streams[i] = null;
        }
      }
    }
  }

  private void selectNewStreams(
      ExoTrackSelection[] selections,
      SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs,
      int[] streamIndexToTrackGroupIndex) {
    // 创建新选择的主流和事件流
    for (int i = 0; i < selections.length; i++) {
      ExoTrackSelection selection = selections[i];
      if (selection == null) {
        continue;
      }
      if (streams[i] == null) {
        // 为选择创建新流
        streamResetFlags[i] = true;
        int trackGroupIndex = streamIndexToTrackGroupIndex[i];
        TrackGroupInfo trackGroupInfo = trackGroupInfos[trackGroupIndex];
        if (trackGroupInfo.trackGroupCategory == TrackGroupInfo.CATEGORY_PRIMARY) {
          streams[i] = buildSampleStream(trackGroupInfo, selection, positionUs);
        } else if (trackGroupInfo.trackGroupCategory == TrackGroupInfo.CATEGORY_MANIFEST_EVENTS) {
          EventStream eventStream = eventStreams.get(trackGroupInfo.eventStreamGroupIndex);
          Format format = selection.getTrackGroup().getFormat(0);
          streams[i] = new EventSampleStream(eventStream, format, manifest.dynamic);
        }
      } else if (streams[i] instanceof ChunkSampleStream) {
        // 更新现有流中的选择
        @SuppressWarnings("unchecked")
        ChunkSampleStream<DashChunkSource> stream = (ChunkSampleStream<DashChunkSource>) streams[i];
        stream.getChunkSource().updateTrackSelection(selection);
      }
    }
    // 从对应的主流创建新选择的嵌入流。注意，由于主流的索引可能大于嵌入流的索引，
    // 因此在第一次遍历时可能尚未创建主流，因此需要第二次遍历。
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] == null && selections[i] != null) {
        int trackGroupIndex = streamIndexToTrackGroupIndex[i];
        TrackGroupInfo trackGroupInfo = trackGroupInfos[trackGroupIndex];
        if (trackGroupInfo.trackGroupCategory == TrackGroupInfo.CATEGORY_EMBEDDED) {
          int primaryStreamIndex = getPrimaryStreamIndex(i, streamIndexToTrackGroupIndex);
          if (primaryStreamIndex == C.INDEX_UNSET) {
            // 如果选择了嵌入轨道但没有选择对应的主轨道，则创建一个空的样本流
            streams[i] = new EmptySampleStream();
          } else {
            streams[i] =
                ((ChunkSampleStream) streams[primaryStreamIndex])
                    .selectEmbeddedTrack(positionUs, trackGroupInfo.trackType);
          }
        }
      }
    }
  }

  private int getPrimaryStreamIndex(int embeddedStreamIndex, int[] streamIndexToTrackGroupIndex) {
    int embeddedTrackGroupIndex = streamIndexToTrackGroupIndex[embeddedStreamIndex];
    if (embeddedTrackGroupIndex == C.INDEX_UNSET) {
      return C.INDEX_UNSET;
    }
    int primaryTrackGroupIndex = trackGroupInfos[embeddedTrackGroupIndex].primaryTrackGroupIndex;
    for (int i = 0; i < streamIndexToTrackGroupIndex.length; i++) {
      int trackGroupIndex = streamIndexToTrackGroupIndex[i];
      if (trackGroupIndex == primaryTrackGroupIndex
          && trackGroupInfos[trackGroupIndex].trackGroupCategory
              == TrackGroupInfo.CATEGORY_PRIMARY) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  private static Pair<TrackGroupArray, TrackGroupInfo[]> buildTrackGroups(
      DrmSessionManager drmSessionManager,
      DashChunkSource.Factory chunkSourceFactory,
      List<AdaptationSet> adaptationSets,
      List<EventStream> eventStreams) {
    int[][] groupedAdaptationSetIndices = getGroupedAdaptationSetIndices(adaptationSets);

    int primaryGroupCount = groupedAdaptationSetIndices.length;
    boolean[] primaryGroupHasEventMessageTrackFlags = new boolean[primaryGroupCount];
    Format[][] primaryGroupClosedCaptionTrackFormats = new Format[primaryGroupCount][];
    int totalEmbeddedTrackGroupCount =
        identifyEmbeddedTracks(
            primaryGroupCount,
            adaptationSets,
            groupedAdaptationSetIndices,
            primaryGroupHasEventMessageTrackFlags,
            primaryGroupClosedCaptionTrackFormats);

    int totalGroupCount = primaryGroupCount + totalEmbeddedTrackGroupCount + eventStreams.size();
    TrackGroup[] trackGroups = new TrackGroup[totalGroupCount];
    TrackGroupInfo[] trackGroupInfos = new TrackGroupInfo[totalGroupCount];

    int trackGroupCount =
        buildPrimaryAndEmbeddedTrackGroupInfos(
            drmSessionManager,
            chunkSourceFactory,
            adaptationSets,
            groupedAdaptationSetIndices,
            primaryGroupCount,
            primaryGroupHasEventMessageTrackFlags,
            primaryGroupClosedCaptionTrackFormats,
            trackGroups,
            trackGroupInfos);

    buildManifestEventTrackGroupInfos(eventStreams, trackGroups, trackGroupInfos, trackGroupCount);

    return Pair.create(new TrackGroupArray(trackGroups), trackGroupInfos);
  }

  /**
   * 对自适应集进行分组。两个自适应集属于同一组的条件是：
   *
   * <ul>
   *   <li>一个是 trick-play 自适应集，并使用 {@code http://dashif.org/guidelines/trickmode} 的 essential 或 supplemental 属性来指示其对应的主自适应集。
   *   <li>两个自适应集使用 {@code urn:mpeg:dash:adaptation-set-switching:2016} 的 supplemental 属性标记为可以安全切换。
   * </ul>
   *
   * @param adaptationSets 需要合并的自适应集。
   * @return 一个数组，其中每个元素是一个组的自适应集索引数组。
   */
  private static int[][] getGroupedAdaptationSetIndices(List<AdaptationSet> adaptationSets) {
    int adaptationSetCount = adaptationSets.size();
    HashMap<Long, Integer> adaptationSetIdToIndex =
        Maps.newHashMapWithExpectedSize(adaptationSetCount);
    List<List<Integer>> adaptationSetGroupedIndices = new ArrayList<>(adaptationSetCount);
    SparseArray<List<Integer>> adaptationSetIndexToGroupedIndices =
        new SparseArray<>(adaptationSetCount);

    // 初始时，每个自适应集属于其自己的组。同时构建 adaptationSetIdToIndex 映射。
    for (int i = 0; i < adaptationSetCount; i++) {
      adaptationSetIdToIndex.put(adaptationSets.get(i).id, i);
      List<Integer> initialGroup = new ArrayList<>();
      initialGroup.add(i);
      adaptationSetGroupedIndices.add(initialGroup);
      adaptationSetIndexToGroupedIndices.put(i, initialGroup);
    }

    // 合并自适应集组。
    for (int i = 0; i < adaptationSetCount; i++) {
      int mergedGroupIndex = i;
      AdaptationSet adaptationSet = adaptationSets.get(i);

      // 将 trick-play 自适应集与其对应的主自适应集合并。
      @Nullable
      Descriptor trickPlayProperty = findTrickPlayProperty(adaptationSet.essentialProperties);
      if (trickPlayProperty == null) {
        // trick-play 也可以通过 supplemental 属性指定。
        trickPlayProperty = findTrickPlayProperty(adaptationSet.supplementalProperties);
      }
      if (trickPlayProperty != null) {
        long mainAdaptationSetId = Long.parseLong(trickPlayProperty.value);
        @Nullable Integer mainAdaptationSetIndex = adaptationSetIdToIndex.get(mainAdaptationSetId);
        if (mainAdaptationSetIndex != null) {
          mergedGroupIndex = mainAdaptationSetIndex;
        }
      }

      // 对于可以安全切换的自适应集，将其合并，并使用最小的索引作为合并组的索引。
      if (mergedGroupIndex == i) {
        @Nullable
        Descriptor adaptationSetSwitchingProperty =
            findAdaptationSetSwitchingProperty(adaptationSet.supplementalProperties);
        if (adaptationSetSwitchingProperty != null) {
          String[] otherAdaptationSetIds = Util.split(adaptationSetSwitchingProperty.value, ",");
          for (String adaptationSetId : otherAdaptationSetIds) {
            @Nullable
            Integer otherAdaptationSetIndex =
                adaptationSetIdToIndex.get(Long.parseLong(adaptationSetId));
            if (otherAdaptationSetIndex != null) {
              mergedGroupIndex = min(mergedGroupIndex, otherAdaptationSetIndex);
            }
          }
        }
      }

      // 如果需要，合并组。
      if (mergedGroupIndex != i) {
        List<Integer> thisGroup = adaptationSetIndexToGroupedIndices.get(i);
        List<Integer> mergedGroup = adaptationSetIndexToGroupedIndices.get(mergedGroupIndex);
        mergedGroup.addAll(thisGroup);
        adaptationSetIndexToGroupedIndices.put(i, mergedGroup);
        adaptationSetGroupedIndices.remove(thisGroup);
      }
    }

    int[][] groupedAdaptationSetIndices = new int[adaptationSetGroupedIndices.size()][];
    for (int i = 0; i < groupedAdaptationSetIndices.length; i++) {
      groupedAdaptationSetIndices[i] = Ints.toArray(adaptationSetGroupedIndices.get(i));
      // 在每个组内恢复自适应集的原始顺序。
      Arrays.sort(groupedAdaptationSetIndices[i]);
    }
    return groupedAdaptationSetIndices;
  }

  /**
   * 遍历主轨道组列表并识别嵌入轨道。
   *
   * @param primaryGroupCount 主轨道组的数量。
   * @param adaptationSets 当前 DASH 周期的 {@link AdaptationSet} 列表。
   * @param groupedAdaptationSetIndices 属于同一主轨道组的 {@link AdaptationSet} 索引，按主轨道组顺序分组。
   * @param primaryGroupHasEventMessageTrackFlags 输出数组，用于填充标志，指示每个主轨道组是否包含嵌入的事件消息轨道。
   * @param primaryGroupClosedCaptionTrackFormats 输出数组，用于填充每个主轨道组中嵌入的闭路字幕轨道的格式。
   * @return 嵌入轨道组的总数。
   */
  private static int identifyEmbeddedTracks(
      int primaryGroupCount,
      List<AdaptationSet> adaptationSets,
      int[][] groupedAdaptationSetIndices,
      boolean[] primaryGroupHasEventMessageTrackFlags,
      Format[][] primaryGroupClosedCaptionTrackFormats) {
    int numEmbeddedTrackGroups = 0;
    for (int i = 0; i < primaryGroupCount; i++) {
      // 检查当前主轨道组是否包含事件消息轨道
      if (hasEventMessageTrack(adaptationSets, groupedAdaptationSetIndices[i])) {
        primaryGroupHasEventMessageTrackFlags[i] = true;
        numEmbeddedTrackGroups++;
      }
      // 获取当前主轨道组中的闭路字幕轨道格式
      primaryGroupClosedCaptionTrackFormats[i] =
          getClosedCaptionTrackFormats(adaptationSets, groupedAdaptationSetIndices[i]);
      // 如果存在闭路字幕轨道，则增加嵌入轨道组的计数
      if (primaryGroupClosedCaptionTrackFormats[i].length != 0) {
        numEmbeddedTrackGroups++;
      }
    }
    return numEmbeddedTrackGroups;
  }

  private static int buildPrimaryAndEmbeddedTrackGroupInfos(
      DrmSessionManager drmSessionManager,
      DashChunkSource.Factory chunkSourceFactory,
      List<AdaptationSet> adaptationSets,
      int[][] groupedAdaptationSetIndices,
      int primaryGroupCount,
      boolean[] primaryGroupHasEventMessageTrackFlags,
      Format[][] primaryGroupClosedCaptionTrackFormats,
      TrackGroup[] trackGroups,
      TrackGroupInfo[] trackGroupInfos) {
    int trackGroupCount = 0;
    for (int i = 0; i < primaryGroupCount; i++) {
      int[] adaptationSetIndices = groupedAdaptationSetIndices[i];
      List<Representation> representations = new ArrayList<>();
      for (int adaptationSetIndex : adaptationSetIndices) {
        representations.addAll(adaptationSets.get(adaptationSetIndex).representations);
      }
      Format[] formats = new Format[representations.size()];
      for (int j = 0; j < formats.length; j++) {
        Format originalFormat = representations.get(j).format;
        Format.Builder updatedFormat =
            originalFormat
                .buildUpon()
                .setCryptoType(drmSessionManager.getCryptoType(originalFormat));
        formats[j] = updatedFormat.build();
      }

      AdaptationSet firstAdaptationSet = adaptationSets.get(adaptationSetIndices[0]);
      String trackGroupId =
          firstAdaptationSet.id != AdaptationSet.ID_UNSET
              ? Long.toString(firstAdaptationSet.id)
              : ("unset:" + i);
      int primaryTrackGroupIndex = trackGroupCount++;
      int eventMessageTrackGroupIndex =
          primaryGroupHasEventMessageTrackFlags[i] ? trackGroupCount++ : C.INDEX_UNSET;
      int closedCaptionTrackGroupIndex =
          primaryGroupClosedCaptionTrackFormats[i].length != 0 ? trackGroupCount++ : C.INDEX_UNSET;

      maybeUpdateFormatsForParsedText(chunkSourceFactory, formats);
      trackGroups[primaryTrackGroupIndex] = new TrackGroup(trackGroupId, formats);
      trackGroupInfos[primaryTrackGroupIndex] =
          TrackGroupInfo.primaryTrack(
              firstAdaptationSet.type,
              adaptationSetIndices,
              primaryTrackGroupIndex,
              eventMessageTrackGroupIndex,
              closedCaptionTrackGroupIndex);
      if (eventMessageTrackGroupIndex != C.INDEX_UNSET) {
        String eventMessageTrackGroupId = trackGroupId + ":emsg";
        Format format =
            new Format.Builder()
                .setId(eventMessageTrackGroupId)
                .setSampleMimeType(MimeTypes.APPLICATION_EMSG)
                .build();
        trackGroups[eventMessageTrackGroupIndex] = new TrackGroup(eventMessageTrackGroupId, format);
        trackGroupInfos[eventMessageTrackGroupIndex] =
            TrackGroupInfo.embeddedEmsgTrack(adaptationSetIndices, primaryTrackGroupIndex);
      }
      if (closedCaptionTrackGroupIndex != C.INDEX_UNSET) {
        String closedCaptionTrackGroupId = trackGroupId + ":cc";
        trackGroupInfos[closedCaptionTrackGroupIndex] =
            TrackGroupInfo.embeddedClosedCaptionTrack(
                adaptationSetIndices,
                primaryTrackGroupIndex,
                ImmutableList.copyOf(primaryGroupClosedCaptionTrackFormats[i]));
        maybeUpdateFormatsForParsedText(
            chunkSourceFactory, primaryGroupClosedCaptionTrackFormats[i]);
        trackGroups[closedCaptionTrackGroupIndex] =
            new TrackGroup(closedCaptionTrackGroupId, primaryGroupClosedCaptionTrackFormats[i]);
      }
    }
    return trackGroupCount;
  }

  private static void buildManifestEventTrackGroupInfos(
      List<EventStream> eventStreams,
      TrackGroup[] trackGroups,
      TrackGroupInfo[] trackGroupInfos,
      int existingTrackGroupCount) {
    for (int i = 0; i < eventStreams.size(); i++) {
      EventStream eventStream = eventStreams.get(i);
      Format format =
          new Format.Builder()
              .setId(eventStream.id())
              .setSampleMimeType(MimeTypes.APPLICATION_EMSG)
              .build();
      String uniqueTrackGroupId = eventStream.id() + ":" + i;
      trackGroups[existingTrackGroupCount] = new TrackGroup(uniqueTrackGroupId, format);
      trackGroupInfos[existingTrackGroupCount++] = TrackGroupInfo.mpdEventTrack(i);
    }
  }

  private ChunkSampleStream<DashChunkSource> buildSampleStream(
      TrackGroupInfo trackGroupInfo, ExoTrackSelection selection, long positionUs) {
    int embeddedTrackCount = 0;
    boolean enableEventMessageTrack =
        trackGroupInfo.embeddedEventMessageTrackGroupIndex != C.INDEX_UNSET;
    TrackGroup embeddedEventMessageTrackGroup = null;
    if (enableEventMessageTrack) {
      embeddedEventMessageTrackGroup =
          trackGroups.get(trackGroupInfo.embeddedEventMessageTrackGroupIndex);
      embeddedTrackCount++;
    }
    ImmutableList<Format> embeddedClosedCaptionOriginalFormats =
        trackGroupInfo.embeddedClosedCaptionTrackGroupIndex != C.INDEX_UNSET
            ? trackGroupInfos[trackGroupInfo.embeddedClosedCaptionTrackGroupIndex]
                .embeddedClosedCaptionTrackOriginalFormats
            : ImmutableList.of();
    embeddedTrackCount += embeddedClosedCaptionOriginalFormats.size();

    Format[] embeddedTrackFormats = new Format[embeddedTrackCount];
    int[] embeddedTrackTypes = new int[embeddedTrackCount];
    embeddedTrackCount = 0;
    if (enableEventMessageTrack) {
      embeddedTrackFormats[embeddedTrackCount] = embeddedEventMessageTrackGroup.getFormat(0);
      embeddedTrackTypes[embeddedTrackCount] = C.TRACK_TYPE_METADATA;
      embeddedTrackCount++;
    }
    List<Format> embeddedClosedCaptionTrackFormats = new ArrayList<>();
    for (int i = 0; i < embeddedClosedCaptionOriginalFormats.size(); i++) {
      embeddedTrackFormats[embeddedTrackCount] = embeddedClosedCaptionOriginalFormats.get(i);
      embeddedTrackTypes[embeddedTrackCount] = C.TRACK_TYPE_TEXT;
      embeddedClosedCaptionTrackFormats.add(embeddedTrackFormats[embeddedTrackCount]);
      embeddedTrackCount++;
    }

    PlayerTrackEmsgHandler trackPlayerEmsgHandler =
        manifest.dynamic && enableEventMessageTrack
            ? playerEmsgHandler.newPlayerTrackEmsgHandler()
            : null;
    DashChunkSource chunkSource =
        chunkSourceFactory.createDashChunkSource(
            manifestLoaderErrorThrower,
            manifest,
            baseUrlExclusionList,
            periodIndex,
            trackGroupInfo.adaptationSetIndices,
            selection,
            trackGroupInfo.trackType,
            elapsedRealtimeOffsetMs,
            enableEventMessageTrack,
            embeddedClosedCaptionTrackFormats,
            trackPlayerEmsgHandler,
            transferListener,
            playerId,
            cmcdConfiguration);
    ChunkSampleStream<DashChunkSource> stream =
        new ChunkSampleStream<>(
            trackGroupInfo.trackType,
            embeddedTrackTypes,
            embeddedTrackFormats,
            chunkSource,
            this,
            allocator,
            positionUs,
            drmSessionManager,
            drmEventDispatcher,
            loadErrorHandlingPolicy,
            mediaSourceEventDispatcher,
            canReportInitialDiscontinuity,
            /* downloadExecutor= */ null);
    synchronized (this) {
      // The map is also accessed on the loading thread so synchronize access.
      trackEmsgHandlerBySampleStream.put(stream, trackPlayerEmsgHandler);
    }
    return stream;
  }

  @Nullable
  private static Descriptor findAdaptationSetSwitchingProperty(List<Descriptor> descriptors) {
    return findDescriptor(descriptors, "urn:mpeg:dash:adaptation-set-switching:2016");
  }

  @Nullable
  private static Descriptor findTrickPlayProperty(List<Descriptor> descriptors) {
    return findDescriptor(descriptors, "http://dashif.org/guidelines/trickmode");
  }

  @Nullable
  private static Descriptor findDescriptor(List<Descriptor> descriptors, String schemeIdUri) {
    for (int i = 0; i < descriptors.size(); i++) {
      Descriptor descriptor = descriptors.get(i);
      if (schemeIdUri.equals(descriptor.schemeIdUri)) {
        return descriptor;
      }
    }
    return null;
  }

  private static boolean hasEventMessageTrack(
      List<AdaptationSet> adaptationSets, int[] adaptationSetIndices) {
    for (int i : adaptationSetIndices) {
      List<Representation> representations = adaptationSets.get(i).representations;
      for (int j = 0; j < representations.size(); j++) {
        Representation representation = representations.get(j);
        if (!representation.inbandEventStreams.isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  private static Format[] getClosedCaptionTrackFormats(
      List<AdaptationSet> adaptationSets, int[] adaptationSetIndices) {
    for (int i : adaptationSetIndices) {
      AdaptationSet adaptationSet = adaptationSets.get(i);
      List<Descriptor> descriptors = adaptationSets.get(i).accessibilityDescriptors;
      for (int j = 0; j < descriptors.size(); j++) {
        Descriptor descriptor = descriptors.get(j);
        if ("urn:scte:dash:cc:cea-608:2015".equals(descriptor.schemeIdUri)) {
          Format cea608Format =
              new Format.Builder()
                  .setSampleMimeType(MimeTypes.APPLICATION_CEA608)
                  .setId(adaptationSet.id + ":cea608")
                  .build();
          return parseClosedCaptionDescriptor(
              descriptor, CEA608_SERVICE_DESCRIPTOR_REGEX, cea608Format);
        } else if ("urn:scte:dash:cc:cea-708:2015".equals(descriptor.schemeIdUri)) {
          Format cea708Format =
              new Format.Builder()
                  .setSampleMimeType(MimeTypes.APPLICATION_CEA708)
                  .setId(adaptationSet.id + ":cea708")
                  .build();
          return parseClosedCaptionDescriptor(
              descriptor, CEA708_SERVICE_DESCRIPTOR_REGEX, cea708Format);
        }
      }
    }
    return new Format[0];
  }

  private static Format[] parseClosedCaptionDescriptor(
      Descriptor descriptor, Pattern serviceDescriptorRegex, Format baseFormat) {
    @Nullable String value = descriptor.value;
    if (value == null) {
      // 存在嵌入的闭路字幕轨道，但未声明服务信息
      return new Format[] {baseFormat};
    }
    // 按分号分割服务信息
    String[] services = Util.split(value, ";");
    Format[] formats = new Format[services.length];
    for (int i = 0; i < services.length; i++) {
      // 使用正则表达式匹配服务信息
      Matcher matcher = serviceDescriptorRegex.matcher(services[i]);
      if (!matcher.matches()) {
        // 如果无法解析所有服务的服务信息，则假定为单轨道
        return new Format[] {baseFormat};
      }
      // 解析无障碍通道编号
      int accessibilityChannel = Integer.parseInt(matcher.group(1));
      // 构建格式对象
      formats[i] =
          baseFormat
              .buildUpon()
              .setId(baseFormat.id + ":" + accessibilityChannel)
              .setAccessibilityChannel(accessibilityChannel)
              .setLanguage(matcher.group(2))
              .build();
    }
    return formats;
  }

  /**
   * 如果配置为在提取期间解析字幕/文本，则修改提供的 {@link Format} 数组。
   */
  private static void maybeUpdateFormatsForParsedText(
      DashChunkSource.Factory chunkSourceFactory, Format[] formats) {
    for (int i = 0; i < formats.length; i++) {
      // 获取解析后的文本格式并更新数组
      formats[i] = chunkSourceFactory.getOutputTextFormat(formats[i]);
    }
  }

  // 我们不会将数组分配给一个会擦除泛型类型的变量，然后再写入它。
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ChunkSampleStream<DashChunkSource>[] newSampleStreamArray(int length) {
    // 创建指定长度的 ChunkSampleStream 数组
    return new ChunkSampleStream[length];
  }

  private static final class TrackGroupInfo {

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef({CATEGORY_PRIMARY, CATEGORY_EMBEDDED, CATEGORY_MANIFEST_EVENTS})
    public @interface TrackGroupCategory {}

    /**
     * 一个普通的轨道组，其样本从流中提取。例如：视频轨道组或音频轨道组。
     */
    private static final int CATEGORY_PRIMARY = 0;

    /**
     * 一个轨道组，其样本嵌入在某个主流中。例如：EMSG 轨道的样本嵌入在主流的 emsg 原子中。
     */
    private static final int CATEGORY_EMBEDDED = 1;

    /**
     * 一个轨道组，其样本直接在 DASH 清单文件中列出。例如：EventStream 轨道的样本（事件）直接包含在 DASH 清单文件中。
     */
    private static final int CATEGORY_MANIFEST_EVENTS = 2;

    public final int[] adaptationSetIndices;
    public final @C.TrackType int trackType;
    public final @TrackGroupCategory int trackGroupCategory;

    public final int eventStreamGroupIndex;
    public final int primaryTrackGroupIndex;
    public final int embeddedEventMessageTrackGroupIndex;
    public final int embeddedClosedCaptionTrackGroupIndex;

    /** 仅对表示嵌入字幕轨道的轨道组有效，包含嵌入字幕轨道的原始格式列表。 */
    public final ImmutableList<Format> embeddedClosedCaptionTrackOriginalFormats;

    public static TrackGroupInfo primaryTrack(
        int trackType,
        int[] adaptationSetIndices,
        int primaryTrackGroupIndex,
        int embeddedEventMessageTrackGroupIndex,
        int embeddedClosedCaptionTrackGroupIndex) {
      return new TrackGroupInfo(
          trackType,
          CATEGORY_PRIMARY,
          adaptationSetIndices,
          primaryTrackGroupIndex,
          embeddedEventMessageTrackGroupIndex,
          embeddedClosedCaptionTrackGroupIndex,
          /* eventStreamGroupIndex= */ -1,
          /* embeddedClosedCaptionTrackOriginalFormats= */ ImmutableList.of());
    }

    public static TrackGroupInfo embeddedEmsgTrack(
        int[] adaptationSetIndices, int primaryTrackGroupIndex) {
      return new TrackGroupInfo(
          C.TRACK_TYPE_METADATA,
          CATEGORY_EMBEDDED,
          adaptationSetIndices,
          primaryTrackGroupIndex,
          C.INDEX_UNSET,
          C.INDEX_UNSET,
          /* eventStreamGroupIndex= */ -1,
          /* embeddedClosedCaptionTrackOriginalFormats= */ ImmutableList.of());
    }

    public static TrackGroupInfo embeddedClosedCaptionTrack(
        int[] adaptationSetIndices,
        int primaryTrackGroupIndex,
        ImmutableList<Format> originalFormats) {
      return new TrackGroupInfo(
          C.TRACK_TYPE_TEXT,
          CATEGORY_EMBEDDED,
          adaptationSetIndices,
          primaryTrackGroupIndex,
          C.INDEX_UNSET,
          C.INDEX_UNSET,
          /* eventStreamGroupIndex= */ -1,
          originalFormats);
    }

    public static TrackGroupInfo mpdEventTrack(int eventStreamIndex) {
      return new TrackGroupInfo(
          C.TRACK_TYPE_METADATA,
          CATEGORY_MANIFEST_EVENTS,
          new int[0],
          /* primaryTrackGroupIndex= */ -1,
          C.INDEX_UNSET,
          C.INDEX_UNSET,
          eventStreamIndex,
          /* embeddedClosedCaptionTrackOriginalFormats= */ ImmutableList.of());
    }

    private TrackGroupInfo(
        @C.TrackType int trackType,
        @TrackGroupCategory int trackGroupCategory,
        int[] adaptationSetIndices,
        int primaryTrackGroupIndex,
        int embeddedEventMessageTrackGroupIndex,
        int embeddedClosedCaptionTrackGroupIndex,
        int eventStreamGroupIndex,
        ImmutableList<Format> embeddedClosedCaptionTrackOriginalFormats) {
      this.trackType = trackType;
      this.adaptationSetIndices = adaptationSetIndices;
      this.trackGroupCategory = trackGroupCategory;
      this.primaryTrackGroupIndex = primaryTrackGroupIndex;
      this.embeddedEventMessageTrackGroupIndex = embeddedEventMessageTrackGroupIndex;
      this.embeddedClosedCaptionTrackGroupIndex = embeddedClosedCaptionTrackGroupIndex;
      this.eventStreamGroupIndex = eventStreamGroupIndex;
      this.embeddedClosedCaptionTrackOriginalFormats = embeddedClosedCaptionTrackOriginalFormats;
    }
  }
}
