/*
 * Copyright 2023 The Android Open Source Project
 *
 * 根据 Apache License, Version 2.0（“许可证”）授权；
 * 除非遵守许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则按“原样”分发软件，
 * 没有任何明示或暗示的担保或条件。
 * 有关特定语言的管理权限和限制，请参阅许可证。
 */
package androidx.media3.exoplayer;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import com.google.common.base.Objects;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** 关于加载开始或继续时的播放器状态信息。 */
@UnstableApi
public final class LoadingInfo {

  /** {@link LoadingInfo} 实例的构建器。 */
  public static final class Builder {
    private long playbackPositionUs;
    private float playbackSpeed;
    private long lastRebufferRealtimeMs;

    /** 使用默认值创建一个新实例。 */
    public Builder() {
      this.playbackPositionUs = C.TIME_UNSET;
      this.playbackSpeed = C.RATE_UNSET;
      this.lastRebufferRealtimeMs = C.TIME_UNSET;
    }

    private Builder(LoadingInfo loadingInfo) {
      this.playbackPositionUs = loadingInfo.playbackPositionUs;
      this.playbackSpeed = loadingInfo.playbackSpeed;
      this.lastRebufferRealtimeMs = loadingInfo.lastRebufferRealtimeMs;
    }

    /** 设置 {@link LoadingInfo#playbackPositionUs}。默认值为 {@link C#TIME_UNSET}。 */
    @CanIgnoreReturnValue
    public Builder setPlaybackPositionUs(long playbackPositionUs) {
      this.playbackPositionUs = playbackPositionUs;
      return this;
    }

    /**
     * 设置 {@link LoadingInfo#playbackSpeed}。默认值为 {@link C#RATE_UNSET}。
     *
     * @throws IllegalArgumentException 如果 {@code playbackSpeed} 不等于 {@link C#RATE_UNSET} 且为非正数。
     */
    @CanIgnoreReturnValue
    public Builder setPlaybackSpeed(float playbackSpeed) {
      checkArgument(playbackSpeed > 0 || playbackSpeed == C.RATE_UNSET);
      this.playbackSpeed = playbackSpeed;
      return this;
    }

    /**
     * 设置 {@link LoadingInfo#lastRebufferRealtimeMs}。默认值为 {@link C#TIME_UNSET}。
     *
     * @throws IllegalArgumentException 如果 {@code lastRebufferRealtimeMs} 不等于 {@link C#TIME_UNSET} 且为负数。
     */
    @CanIgnoreReturnValue
    public Builder setLastRebufferRealtimeMs(long lastRebufferRealtimeMs) {
      checkArgument(lastRebufferRealtimeMs >= 0 || lastRebufferRealtimeMs == C.TIME_UNSET);
      this.lastRebufferRealtimeMs = lastRebufferRealtimeMs;
      return this;
    }

    /** 返回具有当前构建器值的新 {@link LoadingInfo} 实例。 */
    public LoadingInfo build() {
      return new LoadingInfo(this);
    }
  }

  /**
   * 当前播放位置，单位为微秒，如果未设置则为 {@link C#TIME_UNSET}。如果与此加载信息相关的播放尚未开始，则该值将是该时段的起始位置减去仍需播放的前一时段的媒体时长。
   */
  public final long playbackPositionUs;

  /**
   * 播放速度，表示当前的播放速率，如果加载开始或继续时播放速度未知，则为 {@link C#RATE_UNSET}。
   */
  public final float playbackSpeed;

  /**
   * 设置上次重新缓冲发生的时间，单位为自启动以来的毫秒数，包括睡眠时间。
   *
   * <p>使用的时间基准与 {@link SystemClock#elapsedRealtime} 测量的相同。
   *
   * <p><b>注意：</b>如果在加载开始或继续时不知道重新缓冲事件，或者没有发生重新缓冲，或者发生了用户交互（如搜索或停止播放器），则该值将设置为 {@link C#TIME_UNSET}。
   */
  public final long lastRebufferRealtimeMs;

  private LoadingInfo(Builder builder) {
    this.playbackPositionUs = builder.playbackPositionUs;
    this.playbackSpeed = builder.playbackSpeed;
    this.lastRebufferRealtimeMs = builder.lastRebufferRealtimeMs;
  }

  /** 创建一个新的 {@link Builder}，并从此实例中复制初始值。 */
  public LoadingInfo.Builder buildUpon() {
    return new LoadingInfo.Builder(this);
  }

  /**
   * 检查自 {@code realtimeMs} 以来是否发生了重新缓冲。
   *
   * @param realtimeMs 要比较的时间，由 {@link SystemClock#elapsedRealtime()} 测量。
   * @return 自提供的时间戳以来是否发生了重新缓冲。
   */
  public boolean rebufferedSince(long realtimeMs) {
    return lastRebufferRealtimeMs != C.TIME_UNSET
        && realtimeMs != C.TIME_UNSET
        && lastRebufferRealtimeMs >= realtimeMs;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LoadingInfo)) {
      return false;
    }
    LoadingInfo that = (LoadingInfo) o;
    return playbackPositionUs == that.playbackPositionUs
        && playbackSpeed == that.playbackSpeed
        && lastRebufferRealtimeMs == that.lastRebufferRealtimeMs;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(playbackPositionUs, playbackSpeed, lastRebufferRealtimeMs);
  }
}