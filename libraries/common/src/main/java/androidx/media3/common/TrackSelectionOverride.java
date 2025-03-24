package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.util.Collections.max;
import static java.util.Collections.min;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.util.List;

/**
 * 表示一个轨道选择覆盖，包含一个 {@link TrackGroup} 以及应选择的该组轨道中的索引。
 *
 * <p>如果正在播放的媒体包含与覆盖中相同的 {@link TrackGroup}，则在播放期间会应用此轨道选择覆盖。
 * 如果 {@link TrackSelectionParameters} 仅包含一个适用于媒体的给定轨道类型的覆盖，则该覆盖将用于控制该类型的轨道选择。
 * 如果多个给定轨道类型的覆盖适用，则播放器将仅应用其中一个。
 *
 * <p>如果 {@link #trackIndices} 为空，则覆盖指定不应选择任何轨道。
 * 将空覆盖添加到 {@link TrackSelectionParameters} 类似于 {@link TrackSelectionParameters.Builder#setTrackTypeDisabled 禁用轨道类型}，不同之处在于，空覆盖仅在正在播放的媒体包含与覆盖中相同的 {@link TrackGroup} 时才会应用。
 * 相反，禁用轨道类型将阻止选择所有媒体中该类型的轨道。
 */
public final class TrackSelectionOverride {

  /** 媒体 {@link TrackGroup}，其 {@link #trackIndices} 被强制选择。 */
  public final TrackGroup mediaTrackGroup;

  /** 应选择的 {@link TrackGroup} 中的轨道索引。 */
  public final ImmutableList<Integer> trackIndices;

  private static final String FIELD_TRACK_GROUP = Util.intToStringMaxRadix(0);
  private static final String FIELD_TRACKS = Util.intToStringMaxRadix(1);

  /**
   * 构造一个实例，强制选择 {@code trackGroup} 中的 {@code trackIndex}。
   *
   * @param mediaTrackGroup 要覆盖轨道选择的媒体 {@link TrackGroup}。
   * @param trackIndex 要选择的轨道在 {@link TrackGroup} 中的索引。
   */
  public TrackSelectionOverride(TrackGroup mediaTrackGroup, int trackIndex) {
    this(mediaTrackGroup, ImmutableList.of(trackIndex));
  }

  /**
   * 构造一个实例，强制选择 {@code trackGroup} 中的 {@code trackIndices}。
   *
   * @param mediaTrackGroup 要覆盖轨道选择的媒体 {@link TrackGroup}。
   * @param trackIndices 要选择的轨道在 {@link TrackGroup} 中的索引列表。
   */
  public TrackSelectionOverride(TrackGroup mediaTrackGroup, List<Integer> trackIndices) {
    if (!trackIndices.isEmpty()) {
      if (min(trackIndices) < 0 || max(trackIndices) >= mediaTrackGroup.length) {
        throw new IndexOutOfBoundsException();
      }
    }
    this.mediaTrackGroup = mediaTrackGroup;
    this.trackIndices = ImmutableList.copyOf(trackIndices);
  }

  /** 返回覆盖的轨道组的 {@link C.TrackType}。 */
  public @C.TrackType int getType() {
    return mediaTrackGroup.type;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackSelectionOverride that = (TrackSelectionOverride) obj;
    return mediaTrackGroup.equals(that.mediaTrackGroup) && trackIndices.equals(that.trackIndices);
  }

  @Override
  public int hashCode() {
    return mediaTrackGroup.hashCode() + 31 * trackIndices.hashCode();
  }

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putBundle(FIELD_TRACK_GROUP, mediaTrackGroup.toBundle());
    bundle.putIntArray(FIELD_TRACKS, Ints.toArray(trackIndices));
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复一个 {@code TrackSelectionOverride}。 */
  @UnstableApi
  public static TrackSelectionOverride fromBundle(Bundle bundle) {
    Bundle trackGroupBundle = checkNotNull(bundle.getBundle(FIELD_TRACK_GROUP));
    TrackGroup mediaTrackGroup = TrackGroup.fromBundle(trackGroupBundle);
    int[] tracks = checkNotNull(bundle.getIntArray(FIELD_TRACKS));
    return new TrackSelectionOverride(mediaTrackGroup, Ints.asList(tracks));
  }
}