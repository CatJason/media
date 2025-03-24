/*
 * Copyright (C) 2016 The Android Open Source Project
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

import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;

/** 控制媒体的缓冲。 */
@UnstableApi
public interface LoadControl {

  /**
   * 关于当前播放上下文以及调用 {@link LoadControl} 方法的 {@link MediaPeriod} 的信息。
   */
  final class Parameters {
    /** 播放器的 {@linkplain PlayerId ID}。 */
    public final PlayerId playerId;

    /** 播放器当前的 {@link Timeline}。 */
    public final Timeline timeline;

    /**
     * 当前 {@link #timeline} 中受影响的 {@link MediaPeriod} 的 {@link MediaPeriodId}。
     */
    public final MediaPeriodId mediaPeriodId;

    /**
     * 当前播放位置，单位为微秒，相对于由 {@link #mediaPeriodId} 标识的 {@link MediaPeriod} 的开始位置。如果此时段的播放尚未开始，则该值为负数，其绝对值等于仍需播放的前一时段的媒体时长。
     */
    public final long playbackPositionUs;

    /** 当前缓冲的媒体总时长。 */
    public final long bufferedDurationUs;

    /** 当前播放速度的加速因子。 */
    public final float playbackSpeed;

    /** 当 {@link Player#STATE_READY} 时，是否应继续播放。 */
    public final boolean playWhenReady;

    /**
     * 播放器是否正在重新缓冲。重新缓冲定义为由缓冲区耗尽引起，而不是用户操作。因此，在初始缓冲期间或由于搜索操作导致的缓冲期间，此参数为 false。
     */
    public final boolean rebuffering;

    /**
     * 期望的播放位置与直播边缘的偏移量，单位为微秒，如果媒体不是直播流或未配置偏移量，则为 {@link C#TIME_UNSET}。
     */
    public final long targetLiveOffsetUs;

    /**
     * 为 {@link LoadControl} 方法创建参数。
     *
     * @param playerId 参见 {@link #playerId}。
     * @param timeline 参见 {@link #timeline}。
     * @param mediaPeriodId 参见 {@link #mediaPeriodId}。
     * @param playbackPositionUs 参见 {@link #playbackPositionUs}。
     * @param bufferedDurationUs 参见 {@link #bufferedDurationUs}。
     * @param playbackSpeed 参见 {@link #playbackSpeed}。
     * @param playWhenReady 参见 {@link #playWhenReady}。
     * @param rebuffering 参见 {@link #rebuffering}。
     * @param targetLiveOffsetUs 参见 {@link #targetLiveOffsetUs}。
     */
    public Parameters(
        PlayerId playerId,
        Timeline timeline,
        MediaPeriodId mediaPeriodId,
        long playbackPositionUs,
        long bufferedDurationUs,
        float playbackSpeed,
        boolean playWhenReady,
        boolean rebuffering,
        long targetLiveOffsetUs) {
      this.playerId = playerId;
      this.timeline = timeline;
      this.mediaPeriodId = mediaPeriodId;
      this.playbackPositionUs = playbackPositionUs;
      this.bufferedDurationUs = bufferedDurationUs;
      this.playbackSpeed = playbackSpeed;
      this.playWhenReady = playWhenReady;
      this.rebuffering = rebuffering;
      this.targetLiveOffsetUs = targetLiveOffsetUs;
    }
  }

  /**
   * @deprecated 当 MediaPeriodId 未知时用作占位符。仅在调用已弃用的方法 {@link #onTracksSelected(Renderer[], TrackGroupArray, ExoTrackSelection[])} 或
   *     {@link #shouldStartPlayback(long, float, boolean, long)} 时使用。
   */
  @Deprecated
  MediaPeriodId EMPTY_MEDIA_PERIOD_ID = new MediaPeriodId(/* periodUid= */ new Object());

  /**
   * 当播放器准备好新源时调用。
   *
   * @param playerId 准备好新源的播放器的 {@linkplain PlayerId ID}。
   */
  @SuppressWarnings("deprecation") // 调用此方法的已弃用版本。
  default void onPrepared(PlayerId playerId) {
    onPrepared();
  }

  /**
   * @deprecated 请使用 {@link #onPrepared(PlayerId)} 代替。
   */
  @Deprecated
  default void onPrepared() {
    // Media3 ExoPlayer 永远不会调用此方法。此默认实现仅为满足编译器要求。
    throw new IllegalStateException("onPrepared not implemented");
  }

  /**
   * 当发生轨道选择时由播放器调用。
   *
   * @param parameters 包含播放器的 {@linkplain PlayerId ID}、ExoPlayer 中的当前 {@link Timeline} 以及进行选择的 {@link MediaPeriod}。当 {@code timeline} 为空时，将为 {@link #EMPTY_MEDIA_PERIOD_ID}。
   * @param trackGroups 进行选择的 {@link TrackGroup}。
   * @param trackSelections 所做的轨道选择。
   */
  default void onTracksSelected(
      Parameters parameters,
      TrackGroupArray trackGroups,
      @NullableType ExoTrackSelection[] trackSelections) {
    // Media3 ExoPlayer 永远不会调用此方法。此默认实现仅为满足编译器要求。
    throw new IllegalStateException("onTracksSelected not implemented");
  }

  /**
   * @deprecated 请实现 {@link #onTracksSelected(Parameters, TrackGroupArray, ExoTrackSelection[])} 代替。
   */
  @SuppressWarnings("deprecation") // 调用此方法的已弃用版本。
  @Deprecated
  default void onTracksSelected(
      PlayerId playerId,
      Timeline timeline,
      MediaPeriodId mediaPeriodId,
      Renderer[] renderers,
      TrackGroupArray trackGroups,
      @NullableType ExoTrackSelection[] trackSelections) {
    onTracksSelected(timeline, mediaPeriodId, renderers, trackGroups, trackSelections);
  }

  /**
   * @deprecated 请实现 {@link #onTracksSelected(Parameters, TrackGroupArray, ExoTrackSelection[])} 代替。
   */
  @SuppressWarnings("deprecation") // 调用此方法的已弃用版本。
  @Deprecated
  default void onTracksSelected(
      Timeline timeline,
      MediaPeriodId mediaPeriodId,
      Renderer[] renderers,
      TrackGroupArray trackGroups,
      @NullableType ExoTrackSelection[] trackSelections) {
    onTracksSelected(renderers, trackGroups, trackSelections);
  }

  /**
   * @deprecated 请实现 {@link #onTracksSelected(Parameters, TrackGroupArray, ExoTrackSelection[])} 代替。
   */
  @SuppressWarnings("deprecation") // 调用此方法的已弃用版本。
  @Deprecated
  default void onTracksSelected(
      Renderer[] renderers,
      TrackGroupArray trackGroups,
      @NullableType ExoTrackSelection[] trackSelections) {
    // Media3 ExoPlayer 永远不会调用此方法。此默认实现仅为满足编译器要求。
    throw new IllegalStateException("onTracksSelected not implemented");
  }

  /**
   * 当播放器停止时调用。
   *
   * @param playerId 已停止的播放器的 {@linkplain PlayerId ID}。
   */
  @SuppressWarnings("deprecation") // 调用此方法的已弃用版本。
  default void onStopped(PlayerId playerId) {
    onStopped();
  }

  /**
   * @deprecated 请实现 {@link #onStopped(PlayerId)} 代替。
   */
  @Deprecated
  default void onStopped() {
    // Media3 ExoPlayer 永远不会调用此方法。此默认实现仅为满足编译器要求。
    throw new IllegalStateException("onStopped not implemented");
  }

  /**
   * 当播放器释放时调用。
   *
   * @param playerId 已释放的播放器的 {@linkplain PlayerId ID}。
   */
  @SuppressWarnings("deprecation") // 调用此方法的已弃用版本。
  default void onReleased(PlayerId playerId) {
    onReleased();
  }

  /**
   * @deprecated 请实现 {@link #onReleased(PlayerId)} 代替。
   */
  @Deprecated
  default void onReleased() {
    // Media3 ExoPlayer 永远不会调用此方法。此默认实现仅为满足编译器要求。
    throw new IllegalStateException("onReleased not implemented");
  }

  /** 返回应用于获取媒体缓冲区分配的 {@link Allocator}。 */
  Allocator getAllocator();

  /**
   * 返回在当前播放位置之前保留在缓冲区中的媒体时长，用于快速向后搜索。
   *
   * <p>注意：如果 {@link #retainBackBufferFromKeyframe()} 为 false，则只有当后缓冲区包含搜索位置之前的关键帧时，向后搜索才会快速。
   *
   * <p>注意：实现应返回单个值。目前不支持动态更改后缓冲区。
   *
   * @param playerId 请求后缓冲时长的播放器的 {@linkplain PlayerId ID}。
   * @return 在当前播放位置之前保留在缓冲区中的媒体时长，单位为微秒。
   */
  @SuppressWarnings("deprecation") // 调用此方法的已弃用版本。
  default long getBackBufferDurationUs(PlayerId playerId) {
    return getBackBufferDurationUs();
  }

  /**
   * @deprecated 请实现 {@link #getBackBufferDurationUs(PlayerId)} 代替。
   */
  @Deprecated
  default long getBackBufferDurationUs() {
    // Media3 ExoPlayer 永远不会调用此方法。此默认实现仅为满足编译器要求。
    throw new IllegalStateException("getBackBufferDurationUs not implemented");
  }

  /**
   * 返回是否应从当前播放位置减去 {@link #getBackBufferDurationUs()} 之前的关键帧保留媒体，而不是从该位置之前或该位置的任何样本保留。
   *
   * <p>警告：返回 true 将导致后缓冲区的大小取决于所播放媒体中关键帧的间距。除非您控制媒体并且能够接受后缓冲区大小超过 {@link
   * #getBackBufferDurationUs()} 的最大关键帧间距，否则不建议返回 true。
   *
   * <p>注意：实现应返回单个值。目前不支持动态更改后缓冲区。
   *
   * @param playerId 请求是否从关键帧保留后缓冲区的播放器的 {@linkplain PlayerId ID}。
   * @return 是否应从当前播放位置减去 {@link #getBackBufferDurationUs()} 之前的关键帧保留媒体，而不是从该位置之前或该位置的任何样本保留。
   */
  @SuppressWarnings("deprecation") // 调用此方法的已弃用版本。
  default boolean retainBackBufferFromKeyframe(PlayerId playerId) {
    return retainBackBufferFromKeyframe();
  }

  /**
   * @deprecated 请实现 {@link #retainBackBufferFromKeyframe(PlayerId)} 代替。
   */
  @Deprecated
  default boolean retainBackBufferFromKeyframe() {
    // Media3 ExoPlayer 永远不会调用此方法。此默认实现仅为满足编译器要求。
    throw new IllegalStateException("retainBackBufferFromKeyframe not implemented");
  }

  /**
   * 由播放器调用以确定是否应继续加载源。如果此方法返回 true，则在最近的 {@link #onTracksSelected} 调用中标识的 {@link MediaPeriod} 将继续加载。
   *
   * @param parameters 关于播放上下文和将在此方法返回 {@code true} 时继续加载的 {@link MediaPeriod} 的信息。
   * @return 是否应继续加载。
   */
  @SuppressWarnings("deprecation")
  default boolean shouldContinueLoading(Parameters parameters) {
    return shouldContinueLoading(
        parameters.playbackPositionUs, parameters.bufferedDurationUs, parameters.playbackSpeed);
  }

  /**
   * @deprecated 请实现 {@link #shouldContinueLoading(Parameters)} 代替。
   */
  @Deprecated
  default boolean shouldContinueLoading(
      long playbackPositionUs, long bufferedDurationUs, float playbackSpeed) {
    // Media3 ExoPlayer 永远不会调用此方法。此默认实现仅为满足编译器要求。
    throw new IllegalStateException("shouldContinueLoading not implemented");
  }

  /**
   * 调用以确定是否应继续预加载。如果此方法返回 true，则呈现的时段将继续加载媒体。
   *
   * @param timeline 包含可通过 MediaPeriodId.periodUid 查找的预加载时段的 Timeline。
   * @param mediaPeriodId 预加载时段的 MediaPeriodId。
   * @param bufferedDurationUs 预加载时段当前缓冲的媒体时长。
   * @return 是否应继续为给定时段进行预加载。
   */
  default boolean shouldContinuePreloading(
      Timeline timeline, MediaPeriodId mediaPeriodId, long bufferedDurationUs) {
    Log.w(
        "LoadControl",
        "启用播放列表预加载时需要实现 shouldContinuePreloading");
    return false;
  }

  /**
   * 当播放器正在加载源、尚未开始播放并且具有开始播放所需的最小数据量时，由播放器重复调用。返回值决定是否实际开始播放。加载控制可以选择返回 {@code
   * false}，直到满足某些条件（例如缓冲了一定量的媒体）。
   *
   * @param parameters 关于播放上下文和将在此方法返回 {@code true} 时开始播放的 {@link MediaPeriod} 的信息。
   * @return 是否应允许开始或恢复播放。
   */
  @SuppressWarnings("deprecation") // 调用此方法的已弃用版本。
  default boolean shouldStartPlayback(Parameters parameters) {
    return shouldStartPlayback(
        parameters.timeline,
        parameters.mediaPeriodId,
        parameters.bufferedDurationUs,
        parameters.playbackSpeed,
        parameters.rebuffering,
        parameters.targetLiveOffsetUs);
  }

  /**
   * @deprecated 请实现 {@link #shouldStartPlayback(Parameters)} 代替。
   */
  @SuppressWarnings("deprecation") // 调用此方法的已弃用版本。
  @Deprecated
  default boolean shouldStartPlayback(
      Timeline timeline,
      MediaPeriodId mediaPeriodId,
      long bufferedDurationUs,
      float playbackSpeed,
      boolean rebuffering,
      long targetLiveOffsetUs) {
    // Media3 ExoPlayer 永远不会调用此方法。默认实现仅用于转发到下面的已弃用版本。
    return shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs);
  }

  /**
   * @deprecated 请实现 {@link #shouldStartPlayback(Parameters)} 代替。
   */
  @Deprecated
  default boolean shouldStartPlayback(
      long bufferedDurationUs, float playbackSpeed, boolean rebuffering, long targetLiveOffsetUs) {
    // Media3 ExoPlayer 永远不会调用此方法。此默认实现仅为满足编译器要求。
    throw new IllegalStateException("shouldStartPlayback not implemented");
  }
}