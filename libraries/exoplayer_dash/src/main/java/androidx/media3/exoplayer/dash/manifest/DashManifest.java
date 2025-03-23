package androidx.media3.exoplayer.dash.manifest;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.StreamKey;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.offline.FilterableManifest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 表示 DASH 媒体呈现描述（MPD），定义见 ISO/IEC 23009-1:2014 第 5.3.1.2 节。
 */
@UnstableApi
public class DashManifest implements FilterableManifest<DashManifest> {

  /**
   * {@code availabilityStartTime} 值，表示自纪元以来的毫秒数，如果未提供则为 {@link C#TIME_UNSET}。
   */
  public final long availabilityStartTimeMs;

  /**
   * 媒体呈现的持续时间，单位为毫秒，如果不适用则为 {@link C#TIME_UNSET}。
   */
  public final long durationMs;

  /** {@code minBufferTime} 值，单位为毫秒，如果未提供则为 {@link C#TIME_UNSET}。 */
  public final long minBufferTimeMs;

  /** 清单的 {@code type} 属性是否为 "dynamic"。 */
  public final boolean dynamic;

  /**
   * {@code minimumUpdatePeriod} 值，单位为毫秒，如果不适用则为 {@link C#TIME_UNSET}。
   */
  public final long minUpdatePeriodMs;

  /**
   * {@code timeShiftBufferDepth} 值，单位为毫秒，如果未提供则为 {@link C#TIME_UNSET}。
   */
  public final long timeShiftBufferDepthMs;

  /**
   * {@code suggestedPresentationDelay} 值，单位为毫秒，如果未提供则为 {@link C#TIME_UNSET}。
   */
  public final long suggestedPresentationDelayMs;

  /**
   * {@code publishTime} 值，表示自纪元以来的毫秒数，如果未提供则为 {@link C#TIME_UNSET}。
   */
  public final long publishTimeMs;

  /**
   * {@link UtcTimingElement}，如果未提供则为 null。定义见 DVB A168:7/2016 第 4.7.2 节。
   */
  @Nullable public final UtcTimingElement utcTiming;

  /** {@link ServiceDescriptionElement}，如果未提供则为 null。 */
  @Nullable public final ServiceDescriptionElement serviceDescription;

  /** 该清单的位置，如果未提供则为 null。 */
  @Nullable public final Uri location;

  /** {@link ProgramInformation}，如果未提供则为 null。 */
  @Nullable public final ProgramInformation programInformation;
  private final List<Period> periods;

  public DashManifest(
      long availabilityStartTimeMs,
      long durationMs,
      long minBufferTimeMs,
      boolean dynamic,
      long minUpdatePeriodMs,
      long timeShiftBufferDepthMs,
      long suggestedPresentationDelayMs,
      long publishTimeMs,
      @Nullable ProgramInformation programInformation,
      @Nullable UtcTimingElement utcTiming,
      @Nullable ServiceDescriptionElement serviceDescription,
      @Nullable Uri location,
      List<Period> periods) {
    this.availabilityStartTimeMs = availabilityStartTimeMs;
    this.durationMs = durationMs;
    this.minBufferTimeMs = minBufferTimeMs;
    this.dynamic = dynamic;
    this.minUpdatePeriodMs = minUpdatePeriodMs;
    this.timeShiftBufferDepthMs = timeShiftBufferDepthMs;
    this.suggestedPresentationDelayMs = suggestedPresentationDelayMs;
    this.publishTimeMs = publishTimeMs;
    this.programInformation = programInformation;
    this.utcTiming = utcTiming;
    this.location = location;
    this.serviceDescription = serviceDescription;
    this.periods = periods == null ? Collections.emptyList() : periods;
  }

  public final int getPeriodCount() {
    return periods.size();
  }

  public final Period getPeriod(int index) {
    return periods.get(index);
  }

  public final long getPeriodDurationMs(int index) {
    return index == periods.size() - 1
        ? (durationMs == C.TIME_UNSET ? C.TIME_UNSET : (durationMs - periods.get(index).startMs))
        : (periods.get(index + 1).startMs - periods.get(index).startMs);
  }

  public final long getPeriodDurationUs(int index) {
    return Util.msToUs(getPeriodDurationMs(index));
  }

  @Override
  public final DashManifest copy(List<StreamKey> streamKeys) {
    LinkedList<StreamKey> keys = new LinkedList<>(streamKeys);
    Collections.sort(keys);
    keys.add(new StreamKey(-1, -1, -1)); // Add a stopper key to the end

    ArrayList<Period> copyPeriods = new ArrayList<>();
    long shiftMs = 0;
    for (int periodIndex = 0; periodIndex < getPeriodCount(); periodIndex++) {
      if (keys.peek().periodIndex != periodIndex) {
        // 此周期中未选择任何表示。
        long periodDurationMs = getPeriodDurationMs(periodIndex);
        if (periodDurationMs != C.TIME_UNSET) {
          shiftMs += periodDurationMs;
        }
      } else {
        Period period = getPeriod(periodIndex);
        ArrayList<AdaptationSet> copyAdaptationSets =
            copyAdaptationSets(period.adaptationSets, keys);
        Period copiedPeriod =
            new Period(
                period.id, period.startMs - shiftMs, copyAdaptationSets, period.eventStreams);
        copyPeriods.add(copiedPeriod);
      }
    }
    long newDuration = durationMs != C.TIME_UNSET ? durationMs - shiftMs : C.TIME_UNSET;
    return new DashManifest(
        availabilityStartTimeMs,
        newDuration,
        minBufferTimeMs,
        dynamic,
        minUpdatePeriodMs,
        timeShiftBufferDepthMs,
        suggestedPresentationDelayMs,
        publishTimeMs,
        programInformation,
        utcTiming,
        serviceDescription,
        location,
        copyPeriods);
  }

  private static ArrayList<AdaptationSet> copyAdaptationSets(
      List<AdaptationSet> adaptationSets, LinkedList<StreamKey> keys) {
    StreamKey key = keys.poll();
    int periodIndex = key.periodIndex;
    ArrayList<AdaptationSet> copyAdaptationSets = new ArrayList<>();
    do {
      int adaptationSetIndex = key.groupIndex;
      AdaptationSet adaptationSet = adaptationSets.get(adaptationSetIndex);

      List<Representation> representations = adaptationSet.representations;
      ArrayList<Representation> copyRepresentations = new ArrayList<>();
      do {
        Representation representation = representations.get(key.streamIndex);
        copyRepresentations.add(representation);
        key = keys.poll();
      } while (key.periodIndex == periodIndex && key.groupIndex == adaptationSetIndex);

      copyAdaptationSets.add(
          new AdaptationSet(
              adaptationSet.id,
              adaptationSet.type,
              copyRepresentations,
              adaptationSet.accessibilityDescriptors,
              adaptationSet.essentialProperties,
              adaptationSet.supplementalProperties));
    } while (key.periodIndex == periodIndex);
    // 此周期中未选择任何表示。
    keys.addFirst(key);
    return copyAdaptationSets;
  }
}
