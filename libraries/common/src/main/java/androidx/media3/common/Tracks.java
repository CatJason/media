package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.BundleCollectionUtil.toBundleArrayList;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.util.BundleCollectionUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Booleans;
import java.util.Arrays;
import java.util.List;

/**
 * 关于轨道组的信息。
 */
public final class Tracks {

  /**
   * 关于单个轨道组的信息，包括底层的 {@link TrackGroup}、每个轨道的播放支持级别以及是否有任何轨道被选中。
   */
  public static final class Group {

    /** 组中轨道的数量。 */
    public final int length;

    private final TrackGroup mediaTrackGroup;
    private final boolean adaptiveSupported;
    private final @C.FormatSupport int[] trackSupport;
    private final boolean[] trackSelected;

    /**
     * 构造实例。
     *
     * @param mediaTrackGroup 由媒体定义的底层 {@link TrackGroup}。
     * @param adaptiveSupported 播放器是否支持包含多个轨道的自适应选择。
     * @param trackSupport 组中每个轨道的 {@link C.FormatSupport}。
     * @param trackSelected 组中每个轨道是否被选中。
     */
    @UnstableApi
    public Group(
        TrackGroup mediaTrackGroup,
        boolean adaptiveSupported,
        @C.FormatSupport int[] trackSupport,
        boolean[] trackSelected) {
      length = mediaTrackGroup.length;
      checkArgument(length == trackSupport.length && length == trackSelected.length);
      this.mediaTrackGroup = mediaTrackGroup;
      this.adaptiveSupported = adaptiveSupported && length > 1;
      this.trackSupport = trackSupport.clone();
      this.trackSelected = trackSelected.clone();
    }

    /**
     * 返回由媒体定义的底层 {@link TrackGroup}。
     *
     * <p>与当前类不同，{@link TrackGroup} 仅包含由媒体本身定义的信息，不包含运行时信息（例如哪些轨道受支持以及当前被选中）。这使得它适合在某些 {@code (key, value)} 数据结构中作为 {@code key} 使用。
     */
    public TrackGroup getMediaTrackGroup() {
      return mediaTrackGroup;
    }

    /**
     * 返回指定轨道的 {@link Format}。
     *
     * @param trackIndex 组中轨道的索引。
     * @return 轨道的 {@link Format}。
     */
    public Format getTrackFormat(int trackIndex) {
      return mediaTrackGroup.getFormat(trackIndex);
    }

    /**
     * 返回指定轨道的支持级别。
     *
     * @param trackIndex 组中轨道的索引。
     * @return 轨道的 {@link C.FormatSupport}。
     */
    @UnstableApi
    public @C.FormatSupport int getTrackSupport(int trackIndex) {
      return trackSupport[trackIndex];
    }

    /**
     * 返回指定轨道是否支持播放，而不超出设备的广告能力。等效于 {@code isTrackSupported(trackIndex, false)}。
     *
     * @param trackIndex 组中轨道的索引。
     * @return 如果轨道的格式可以播放，则返回 true，否则返回 false。
     */
    public boolean isTrackSupported(int trackIndex) {
      return isTrackSupported(trackIndex, /* allowExceedsCapabilities= */ false);
    }

    /**
     * 返回指定轨道是否支持播放。
     *
     * @param trackIndex 组中轨道的索引。
     * @param allowExceedsCapabilities 如果轨道具有支持的 {@link Format#sampleMimeType MIME 类型}，但其他方面超出设备的广告能力，是否将其视为支持。例如，视频轨道具有相应的解码器，但轨道的分辨率超出了解码器的最大广告分辨率。在某些情况下，此类轨道可能是可播放的。
     * @return 如果轨道的格式可以播放，则返回 true，否则返回 false。
     */
    public boolean isTrackSupported(int trackIndex, boolean allowExceedsCapabilities) {
      return trackSupport[trackIndex] == C.FORMAT_HANDLED
          || (allowExceedsCapabilities
          && trackSupport[trackIndex] == C.FORMAT_EXCEEDS_CAPABILITIES);
    }

    /** 返回组中是否至少有一个轨道被选中用于播放。 */
    public boolean isSelected() {
      return Booleans.contains(trackSelected, true);
    }

    /** 返回是否支持包含多个轨道的自适应选择。 */
    public boolean isAdaptiveSupported() {
      return adaptiveSupported;
    }

    /**
     * 返回组中是否至少有一个轨道支持播放，而不超出设备的广告能力。等效于 {@code isSupported(false)}。
     */
    public boolean isSupported() {
      return isSupported(/* allowExceedsCapabilities= */ false);
    }

    /**
     * 返回组中是否至少有一个轨道支持播放。
     *
     * @param allowExceedsCapabilities 如果轨道具有支持的 {@link Format#sampleMimeType MIME 类型}，但其他方面超出设备的广告能力，是否将其视为支持。例如，视频轨道具有相应的解码器，但轨道的分辨率超出了解码器的最大广告分辨率。在某些情况下，此类轨道可能是可播放的。
     */
    public boolean isSupported(boolean allowExceedsCapabilities) {
      for (int i = 0; i < trackSupport.length; i++) {
        if (isTrackSupported(i, allowExceedsCapabilities)) {
          return true;
        }
      }
      return false;
    }

    /**
     * 返回指定轨道是否被选中用于播放。
     *
     * <p>请注意，组中可能有多个轨道被选中。这在自适应流媒体中很常见，其中不同质量的轨道被选中，播放器在播放期间在它们之间切换（例如，基于可用网络带宽）。
     *
     * <p>此类不提供确定当前正在播放的选中轨道的方法，但某些播放器实现提供了获取此类信息的方式。例如，ExoPlayer 通过 {@code ExoTrackSelection.getSelectedFormat} 提供此信息。
     *
     * @param trackIndex 组中轨道的索引。
     * @return 如果轨道被选中，则返回 true，否则返回 false。
     */
    public boolean isTrackSelected(int trackIndex) {
      return trackSelected[trackIndex];
    }

    /** 返回组的 {@link C.TrackType}。 */
    public @C.TrackType int getType() {
      return mediaTrackGroup.type;
    }

    /**
     * 使用新的 {@link TrackGroup#id} 复制 {@code Group}。
     *
     * @param groupId 新的 {@link TrackGroup#id}。
     * @return 复制的 {@code Group}。
     */
    @UnstableApi
    public Group copyWithId(String groupId) {
      return new Group(
          mediaTrackGroup.copyWithId(groupId), adaptiveSupported, trackSupport, trackSelected);
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      Group that = (Group) other;
      return adaptiveSupported == that.adaptiveSupported
          && mediaTrackGroup.equals(that.mediaTrackGroup)
          && Arrays.equals(trackSupport, that.trackSupport)
          && Arrays.equals(trackSelected, that.trackSelected);
    }

    @Override
    public int hashCode() {
      int result = mediaTrackGroup.hashCode();
      result = 31 * result + (adaptiveSupported ? 1 : 0);
      result = 31 * result + Arrays.hashCode(trackSupport);
      result = 31 * result + Arrays.hashCode(trackSelected);
      return result;
    }

    private static final String FIELD_TRACK_GROUP = Util.intToStringMaxRadix(0);
    private static final String FIELD_TRACK_SUPPORT = Util.intToStringMaxRadix(1);
    private static final String FIELD_TRACK_SELECTED = Util.intToStringMaxRadix(3);
    private static final String FIELD_ADAPTIVE_SUPPORTED = Util.intToStringMaxRadix(4);

    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putBundle(FIELD_TRACK_GROUP, mediaTrackGroup.toBundle());
      bundle.putIntArray(FIELD_TRACK_SUPPORT, trackSupport);
      bundle.putBooleanArray(FIELD_TRACK_SELECTED, trackSelected);
      bundle.putBoolean(FIELD_ADAPTIVE_SUPPORTED, adaptiveSupported);
      return bundle;
    }

    /** Restores a group of tracks from a {@link Bundle}. */
    @UnstableApi
    public static Group fromBundle(Bundle bundle) {
      // Can't create a Tracks.Group without a TrackGroup
      TrackGroup trackGroup =
          TrackGroup.fromBundle(checkNotNull(bundle.getBundle(FIELD_TRACK_GROUP)));
      final @C.FormatSupport int[] trackSupport =
          MoreObjects.firstNonNull(
              bundle.getIntArray(FIELD_TRACK_SUPPORT), new int[trackGroup.length]);
      boolean[] selected =
          MoreObjects.firstNonNull(
              bundle.getBooleanArray(FIELD_TRACK_SELECTED), new boolean[trackGroup.length]);
      boolean adaptiveSupported = bundle.getBoolean(FIELD_ADAPTIVE_SUPPORTED, false);
      return new Group(trackGroup, adaptiveSupported, trackSupport, selected);
    }
  }

  /** Empty tracks. */
  public static final Tracks EMPTY = new Tracks(ImmutableList.of());

  private final ImmutableList<Group> groups;

  /**
   * Constructs an instance.
   *
   * @param groups The {@link Group groups} of tracks.
   */
  @UnstableApi
  public Tracks(List<Group> groups) {
    this.groups = ImmutableList.copyOf(groups);
  }

  /** Returns the {@link Group groups} of tracks. */
  public ImmutableList<Group> getGroups() {
    return groups;
  }

  /** Returns {@code true} if there are no tracks, and {@code false} otherwise. */
  public boolean isEmpty() {
    return groups.isEmpty();
  }

  /** Returns true if there are tracks of type {@code trackType}, and false otherwise. */
  public boolean containsType(@C.TrackType int trackType) {
    for (int i = 0; i < groups.size(); i++) {
      if (groups.get(i).getType() == trackType) {
        return true;
      }
    }
    return false;
  }

  /**
   * 如果至少有一个类型为 {@code trackType} 的轨道是 {@link Group#isTrackSupported(int) 支持的}，则返回 true。
   */
  public boolean isTypeSupported(@C.TrackType int trackType) {
    return isTypeSupported(trackType, /* allowExceedsCapabilities= */ false);
  }

  /**
   * 如果至少有一个类型为 {@code trackType} 的轨道是 {@link Group#isTrackSupported(int, boolean) 支持的}，则返回 true。
   *
   * @param trackType 要查询支持的轨道类型。
   * @param allowExceedsCapabilities 如果轨道具有支持的 {@link Format#sampleMimeType MIME 类型}，但其他方面超出设备的广告能力，是否将其视为支持。例如，视频轨道具有相应的解码器，但轨道的分辨率超出了解码器的最大广告分辨率。在某些情况下，此类轨道可能是可播放的。
   */
  public boolean isTypeSupported(@C.TrackType int trackType, boolean allowExceedsCapabilities) {
    for (int i = 0; i < groups.size(); i++) {
      if (groups.get(i).getType() == trackType) {
        if (groups.get(i).isSupported(allowExceedsCapabilities)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @deprecated Use {@link #containsType(int)} and {@link #isTypeSupported(int)}.
   */
  @Deprecated
  @UnstableApi
  @SuppressWarnings("deprecation")
  public boolean isTypeSupportedOrEmpty(@C.TrackType int trackType) {
    return isTypeSupportedOrEmpty(trackType, /* allowExceedsCapabilities= */ false);
  }

  /**
   * @deprecated Use {@link #containsType(int)} and {@link #isTypeSupported(int, boolean)}.
   */
  @Deprecated
  @UnstableApi
  public boolean isTypeSupportedOrEmpty(
      @C.TrackType int trackType, boolean allowExceedsCapabilities) {
    return !containsType(trackType) || isTypeSupported(trackType, allowExceedsCapabilities);
  }

  /** Returns true if at least one track of the type {@code trackType} is selected for playback. */
  public boolean isTypeSelected(@C.TrackType int trackType) {
    for (int i = 0; i < groups.size(); i++) {
      Group group = groups.get(i);
      if (group.isSelected() && group.getType() == trackType) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    Tracks that = (Tracks) other;
    return groups.equals(that.groups);
  }

  @Override
  public int hashCode() {
    return groups.hashCode();
  }

  private static final String FIELD_TRACK_GROUPS = Util.intToStringMaxRadix(0);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putParcelableArrayList(FIELD_TRACK_GROUPS, toBundleArrayList(groups, Group::toBundle));
    return bundle;
  }

  /** Restores a {@code Tracks} from a {@link Bundle}. */
  @UnstableApi
  public static Tracks fromBundle(Bundle bundle) {
    @Nullable List<Bundle> groupBundles = bundle.getParcelableArrayList(FIELD_TRACK_GROUPS);
    List<Group> groups =
        groupBundles == null
            ? ImmutableList.of()
            : BundleCollectionUtil.fromBundleList(Group::fromBundle, groupBundles);
    return new Tracks(groups);
  }
}
