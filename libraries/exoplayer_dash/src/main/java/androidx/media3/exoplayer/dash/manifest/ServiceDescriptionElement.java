package androidx.media3.exoplayer.dash.manifest;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;

/** 表示服务描述元素。 */
@UnstableApi
public final class ServiceDescriptionElement {

  /** 目标直播偏移量（以毫秒为单位），如果未定义则为 {@link C#TIME_UNSET}。 */
  public final long targetOffsetMs;

  /** 最小直播偏移量（以毫秒为单位），如果未定义则为 {@link C#TIME_UNSET}。 */
  public final long minOffsetMs;

  /** 最大直播偏移量（以毫秒为单位），如果未定义则为 {@link C#TIME_UNSET}。 */
  public final long maxOffsetMs;

  /**
   * 用于直播速度调整的最小播放速度因子，如果未定义则为 {@link C#RATE_UNSET}。
   */
  public final float minPlaybackSpeed;

  /**
   * 用于直播速度调整的最大播放速度因子，如果未定义则为 {@link C#RATE_UNSET}。
   */
  public final float maxPlaybackSpeed;

  /**
   * 创建一个服务描述元素。
   *
   * @param targetOffsetMs 目标直播偏移量（以毫秒为单位），如果未定义则为 {@link C#TIME_UNSET}。
   * @param minOffsetMs 最小直播偏移量（以毫秒为单位），如果未定义则为 {@link C#TIME_UNSET}。
   * @param maxOffsetMs 最大直播偏移量（以毫秒为单位），如果未定义则为 {@link C#TIME_UNSET}。
   * @param minPlaybackSpeed 用于直播速度调整的最小播放速度因子，如果未定义则为 {@link C#RATE_UNSET}。
   * @param maxPlaybackSpeed 用于直播速度调整的最大播放速度因子，如果未定义则为 {@link C#RATE_UNSET}。
   */
  public ServiceDescriptionElement(
      long targetOffsetMs,
      long minOffsetMs,
      long maxOffsetMs,
      float minPlaybackSpeed,
      float maxPlaybackSpeed) {
    this.targetOffsetMs = targetOffsetMs;
    this.minOffsetMs = minOffsetMs;
    this.maxOffsetMs = maxOffsetMs;
    this.minPlaybackSpeed = minPlaybackSpeed;
    this.maxPlaybackSpeed = maxPlaybackSpeed;
  }
}