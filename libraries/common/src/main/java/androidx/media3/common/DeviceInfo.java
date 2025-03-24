package androidx.media3.common;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.MediaRouter2;
import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 播放设备的信息。 */
public final class DeviceInfo {

  /** 播放类型。取值为 {@link #PLAYBACK_TYPE_LOCAL} 或 {@link #PLAYBACK_TYPE_REMOTE}。 */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
      PLAYBACK_TYPE_LOCAL,
      PLAYBACK_TYPE_REMOTE,
  })
  public @interface PlaybackType {}

  /** 播放发生在本地设备上（例如手机）。 */
  public static final int PLAYBACK_TYPE_LOCAL = 0;

  /** 播放发生在设备外部（例如投屏设备）。 */
  public static final int PLAYBACK_TYPE_REMOTE = 1;

  /** 未知的 DeviceInfo。 */
  public static final DeviceInfo UNKNOWN = new Builder(PLAYBACK_TYPE_LOCAL).build();

  /** {@link DeviceInfo} 的构建器。 */
  public static final class Builder {

    private final @PlaybackType int playbackType;

    private int minVolume;
    private int maxVolume;
    @Nullable private String routingControllerId;

    /**
     * 创建构建器。
     *
     * @param playbackType 播放类型。
     */
    public Builder(@PlaybackType int playbackType) {
      this.playbackType = playbackType;
    }

    /**
     * 设置设备支持的最小音量。
     *
     * <p>如果未指定，最小值将设置为 {@code 0}。
     *
     * @param minVolume 设备的最小音量。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setMinVolume(@IntRange(from = 0) int minVolume) {
      this.minVolume = minVolume;
      return this;
    }

    /**
     * 设置设备支持的最大音量。
     *
     * @param maxVolume 设备的最大音量，或 {@code 0} 表示不指定最大值。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setMaxVolume(@IntRange(from = 0) int maxVolume) {
      this.maxVolume = maxVolume;
      return this;
    }

    /**
     * 设置关联的 {@link MediaRouter2.RoutingController} 的 {@linkplain MediaRouter2.RoutingController#getId() 路由控制器 ID}。
     *
     * <p>此 ID 允许将此设备信息映射到路由控制器，路由控制器提供有关媒体路由的信息并允许控制其音量。
     *
     * <p>如果 {@link DeviceInfo#playbackType} 是 {@link #PLAYBACK_TYPE_LOCAL}，则设置的值必须为 null。
     *
     * @param routingControllerId 关联的 {@link MediaRouter2.RoutingController} 的 {@linkplain MediaRouter2.RoutingController#getId() 路由控制器 ID}，或 null 表示不指定。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setRoutingControllerId(@Nullable String routingControllerId) {
      Assertions.checkArgument(playbackType != PLAYBACK_TYPE_LOCAL || routingControllerId == null);
      this.routingControllerId = routingControllerId;
      return this;
    }

    /** 构建 {@link DeviceInfo}。 */
    public DeviceInfo build() {
      Assertions.checkArgument(minVolume <= maxVolume);
      return new DeviceInfo(this);
    }
  }

  /** 播放类型。 */
  public final @PlaybackType int playbackType;

  /** 设备支持的最小音量。 */
  @IntRange(from = 0)
  public final int minVolume;

  /** 设备支持的最大音量，或 {@code 0} 表示未指定。 */
  @IntRange(from = 0)
  public final int maxVolume;

  /**
   * 关联的 {@link MediaRouter2.RoutingController} 的 {@linkplain MediaRouter2.RoutingController#getId() 路由控制器 ID}，或 null 表示未设置或 {@link #playbackType} 为 {@link #PLAYBACK_TYPE_LOCAL}。
   *
   * <p>此 ID 允许将此设备信息映射到路由控制器，路由控制器提供有关媒体路由的信息并允许控制其音量。
   */
  @Nullable public final String routingControllerId;

  /**
   * @deprecated 请使用 {@link Builder} 代替。
   */
  @UnstableApi
  @Deprecated
  public DeviceInfo(
      @PlaybackType int playbackType,
      @IntRange(from = 0) int minVolume,
      @IntRange(from = 0) int maxVolume) {
    this(new Builder(playbackType).setMinVolume(minVolume).setMaxVolume(maxVolume));
  }

  private DeviceInfo(Builder builder) {
    this.playbackType = builder.playbackType;
    this.minVolume = builder.minVolume;
    this.maxVolume = builder.maxVolume;
    this.routingControllerId = builder.routingControllerId;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DeviceInfo)) {
      return false;
    }
    DeviceInfo other = (DeviceInfo) obj;
    return playbackType == other.playbackType
        && minVolume == other.minVolume
        && maxVolume == other.maxVolume
        && Util.areEqual(routingControllerId, other.routingControllerId);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + playbackType;
    result = 31 * result + minVolume;
    result = 31 * result + maxVolume;
    result = 31 * result + (routingControllerId == null ? 0 : routingControllerId.hashCode());
    return result;
  }

  private static final String FIELD_PLAYBACK_TYPE = Util.intToStringMaxRadix(0);
  private static final String FIELD_MIN_VOLUME = Util.intToStringMaxRadix(1);
  private static final String FIELD_MAX_VOLUME = Util.intToStringMaxRadix(2);
  private static final String FIELD_ROUTING_CONTROLLER_ID = Util.intToStringMaxRadix(3);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    if (playbackType != PLAYBACK_TYPE_LOCAL) {
      bundle.putInt(FIELD_PLAYBACK_TYPE, playbackType);
    }
    if (minVolume != 0) {
      bundle.putInt(FIELD_MIN_VOLUME, minVolume);
    }
    if (maxVolume != 0) {
      bundle.putInt(FIELD_MAX_VOLUME, maxVolume);
    }
    if (routingControllerId != null) {
      bundle.putString(FIELD_ROUTING_CONTROLLER_ID, routingControllerId);
    }
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复 {@code DeviceInfo}。 */
  @UnstableApi
  public static DeviceInfo fromBundle(Bundle bundle) {
    int playbackType = bundle.getInt(FIELD_PLAYBACK_TYPE, /* defaultValue= */ PLAYBACK_TYPE_LOCAL);
    int minVolume = bundle.getInt(FIELD_MIN_VOLUME, /* defaultValue= */ 0);
    int maxVolume = bundle.getInt(FIELD_MAX_VOLUME, /* defaultValue= */ 0);
    @Nullable String routingControllerId = bundle.getString(FIELD_ROUTING_CONTROLLER_ID);
    return new DeviceInfo.Builder(playbackType)
        .setMinVolume(minVolume)
        .setMaxVolume(maxVolume)
        .setRoutingControllerId(routingControllerId)
        .build();
  }
  ;
}