package androidx.media3.common;

import static androidx.annotation.VisibleForTesting.PROTECTED;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.common.util.Util.msToUs;
import static androidx.media3.common.util.Util.usToMs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.ListenerSet;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * {@link Player} 的基础实现，将需要实现的方法数量减少到最少。
 *
 * <p>实现说明：
 *
 * <ul>
 *   <li>子类必须重写 {@link #getState()}，以便在请求时填充当前的播放器状态。
 *   <li>{@link State} 应设置 {@linkplain State.Builder#setAvailableCommands 可用命令}，以指示支持哪些 {@link Player} 方法。
 *   <li>所有类似 setter 的播放器方法（例如 {@link #setPlayWhenReady}）会转发到可重写的方法（例如 {@link #handleSetPlayWhenReady}），这些方法可用于处理这些请求。这些方法返回一个 {@link ListenableFuture}，以指示请求何时被处理并完全反映在 {@link #getState} 返回的值中。此类将在请求完成后自动请求状态更新。如果状态更改可以同步处理，这些方法可以返回 Guava 的 {@link Futures#immediateVoidFuture()}。
 *   <li>子类可以通过 {@link #invalidateState} 手动触发状态更新，例如在独立于 {@link Player} 方法调用的情况下发生变化时。
 * </ul>
 *
 * 此基类处理播放器实现的各个方面，以简化子类的实现：
 *
 * <ul>
 *   <li>{@link State} 只能使用允许的状态值组合创建，从而避免任何无效的播放器状态。
 *   <li>只需实现声明为 {@linkplain Player.Command 可用} 的功能。其他方法会自动忽略。
 *   <li>监听器处理以及通知监听器状态更改是自动处理的。
 *   <li>基类提供了异步处理方法调用的框架。它立即将可见的播放状态更改为最可能的结果，以确保用户可见的状态更改看起来像同步操作。一旦异步方法调用完全处理完毕，状态将再次更新。
 * </ul>
 */
@UnstableApi
public abstract class SimpleBasePlayer extends BasePlayer {

  /** An immutable state description of the player. */
  protected static final class State {

    /** A builder for {@link State} objects. */
    public static final class Builder {

      private Commands availableCommands;
      private boolean playWhenReady;
      private @PlayWhenReadyChangeReason int playWhenReadyChangeReason;
      private @Player.State int playbackState;
      private @PlaybackSuppressionReason int playbackSuppressionReason;
      @Nullable private PlaybackException playerError;
      private @RepeatMode int repeatMode;
      private boolean shuffleModeEnabled;
      private boolean isLoading;
      private long seekBackIncrementMs;
      private long seekForwardIncrementMs;
      private long maxSeekToPreviousPositionMs;
      private PlaybackParameters playbackParameters;
      private TrackSelectionParameters trackSelectionParameters;
      private AudioAttributes audioAttributes;
      private float volume;
      private VideoSize videoSize;
      private CueGroup currentCues;
      private DeviceInfo deviceInfo;
      private int deviceVolume;
      private boolean isDeviceMuted;
      private Size surfaceSize;
      private boolean newlyRenderedFirstFrame;
      private Metadata timedMetadata;
      @Nullable private ImmutableList<MediaItemData> playlist;
      private Timeline timeline;
      @Nullable private Tracks currentTracks;
      @Nullable private MediaMetadata currentMetadata;
      private MediaMetadata playlistMetadata;
      private int currentMediaItemIndex;
      private int currentAdGroupIndex;
      private int currentAdIndexInAdGroup;
      @Nullable private Long contentPositionMs;
      private PositionSupplier contentPositionMsSupplier;
      @Nullable private Long adPositionMs;
      private PositionSupplier adPositionMsSupplier;
      private PositionSupplier contentBufferedPositionMsSupplier;
      private PositionSupplier adBufferedPositionMsSupplier;
      private PositionSupplier totalBufferedDurationMsSupplier;
      private boolean hasPositionDiscontinuity;
      private @Player.DiscontinuityReason int positionDiscontinuityReason;
      private long discontinuityPositionMs;

      /** Creates the builder. */
      public Builder() {
        availableCommands = Commands.EMPTY;
        playWhenReady = false;
        playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
        playbackState = Player.STATE_IDLE;
        playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE;
        playerError = null;
        repeatMode = Player.REPEAT_MODE_OFF;
        shuffleModeEnabled = false;
        isLoading = false;
        seekBackIncrementMs = C.DEFAULT_SEEK_BACK_INCREMENT_MS;
        seekForwardIncrementMs = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS;
        maxSeekToPreviousPositionMs = C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS;
        playbackParameters = PlaybackParameters.DEFAULT;
        trackSelectionParameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
        audioAttributes = AudioAttributes.DEFAULT;
        volume = 1f;
        videoSize = VideoSize.UNKNOWN;
        currentCues = CueGroup.EMPTY_TIME_ZERO;
        deviceInfo = DeviceInfo.UNKNOWN;
        deviceVolume = 0;
        isDeviceMuted = false;
        surfaceSize = Size.UNKNOWN;
        newlyRenderedFirstFrame = false;
        timedMetadata = new Metadata(/* presentationTimeUs= */ C.TIME_UNSET);
        playlist = ImmutableList.of();
        timeline = Timeline.EMPTY;
        currentTracks = null;
        currentMetadata = null;
        playlistMetadata = MediaMetadata.EMPTY;
        currentMediaItemIndex = C.INDEX_UNSET;
        currentAdGroupIndex = C.INDEX_UNSET;
        currentAdIndexInAdGroup = C.INDEX_UNSET;
        contentPositionMs = null;
        contentPositionMsSupplier = PositionSupplier.getConstant(C.TIME_UNSET);
        adPositionMs = null;
        adPositionMsSupplier = PositionSupplier.ZERO;
        contentBufferedPositionMsSupplier = PositionSupplier.getConstant(C.TIME_UNSET);
        adBufferedPositionMsSupplier = PositionSupplier.ZERO;
        totalBufferedDurationMsSupplier = PositionSupplier.ZERO;
        hasPositionDiscontinuity = false;
        positionDiscontinuityReason = Player.DISCONTINUITY_REASON_INTERNAL;
        discontinuityPositionMs = 0;
      }

      private Builder(State state) {
        this.availableCommands = state.availableCommands;
        this.playWhenReady = state.playWhenReady;
        this.playWhenReadyChangeReason = state.playWhenReadyChangeReason;
        this.playbackState = state.playbackState;
        this.playbackSuppressionReason = state.playbackSuppressionReason;
        this.playerError = state.playerError;
        this.repeatMode = state.repeatMode;
        this.shuffleModeEnabled = state.shuffleModeEnabled;
        this.isLoading = state.isLoading;
        this.seekBackIncrementMs = state.seekBackIncrementMs;
        this.seekForwardIncrementMs = state.seekForwardIncrementMs;
        this.maxSeekToPreviousPositionMs = state.maxSeekToPreviousPositionMs;
        this.playbackParameters = state.playbackParameters;
        this.trackSelectionParameters = state.trackSelectionParameters;
        this.audioAttributes = state.audioAttributes;
        this.volume = state.volume;
        this.videoSize = state.videoSize;
        this.currentCues = state.currentCues;
        this.deviceInfo = state.deviceInfo;
        this.deviceVolume = state.deviceVolume;
        this.isDeviceMuted = state.isDeviceMuted;
        this.surfaceSize = state.surfaceSize;
        this.newlyRenderedFirstFrame = state.newlyRenderedFirstFrame;
        this.timedMetadata = state.timedMetadata;
        this.timeline = state.timeline;
        if (state.timeline instanceof PlaylistTimeline) {
          this.playlist = ((PlaylistTimeline) state.timeline).playlist;
        } else {
          this.currentTracks = state.currentTracks;
          this.currentMetadata = state.currentMetadata;
        }
        this.playlistMetadata = state.playlistMetadata;
        this.currentMediaItemIndex = state.currentMediaItemIndex;
        this.currentAdGroupIndex = state.currentAdGroupIndex;
        this.currentAdIndexInAdGroup = state.currentAdIndexInAdGroup;
        this.contentPositionMs = null;
        this.contentPositionMsSupplier = state.contentPositionMsSupplier;
        this.adPositionMs = null;
        this.adPositionMsSupplier = state.adPositionMsSupplier;
        this.contentBufferedPositionMsSupplier = state.contentBufferedPositionMsSupplier;
        this.adBufferedPositionMsSupplier = state.adBufferedPositionMsSupplier;
        this.totalBufferedDurationMsSupplier = state.totalBufferedDurationMsSupplier;
        this.hasPositionDiscontinuity = state.hasPositionDiscontinuity;
        this.positionDiscontinuityReason = state.positionDiscontinuityReason;
        this.discontinuityPositionMs = state.discontinuityPositionMs;
      }

      /**
       * 设置可用的 {@link Commands}。
       *
       * @param availableCommands 可用的 {@link Commands}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setAvailableCommands(Commands availableCommands) {
        this.availableCommands = availableCommands;
        return this;
      }

      /**
       * 设置当播放器准备就绪且未被抑制时是否应继续播放。
       *
       * @param playWhenReady 当播放器准备就绪且未被抑制时是否应继续播放。
       * @param playWhenReadyChangeReason 更改此值的 {@linkplain PlayWhenReadyChangeReason 原因}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setPlayWhenReady(
          boolean playWhenReady, @PlayWhenReadyChangeReason int playWhenReadyChangeReason) {
        this.playWhenReady = playWhenReady;
        this.playWhenReadyChangeReason = playWhenReadyChangeReason;
        return this;
      }

      /**
       * 设置播放器的 {@linkplain Player.State 状态}。
       *
       * <p>如果 {@linkplain #setPlaylist 播放列表} 为空，状态必须为 {@link Player#STATE_IDLE} 或 {@link Player#STATE_ENDED}。
       *
       * @param playbackState 播放器的 {@linkplain Player.State 状态}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setPlaybackState(@Player.State int playbackState) {
        this.playbackState = playbackState;
        return this;
      }

      /**
       * 设置即使 {@link #getPlayWhenReady()} 为 true，播放仍被抑制的原因。
       *
       * @param playbackSuppressionReason 即使 {@link #getPlayWhenReady()} 为 true，播放仍被抑制的 {@link Player.PlaybackSuppressionReason 原因}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setPlaybackSuppressionReason(
          @Player.PlaybackSuppressionReason int playbackSuppressionReason) {
        this.playbackSuppressionReason = playbackSuppressionReason;
        return this;
      }

      /**
       * 设置导致播放失败的最后一次错误，如果没有错误则为 null。
       *
       * <p>设置错误时，必须将 {@linkplain #setPlaybackState 播放状态} 设置为 {@link Player#STATE_IDLE}。
       *
       * @param playerError 导致播放失败的最后一次错误，如果没有错误则为 null。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setPlayerError(@Nullable PlaybackException playerError) {
        this.playerError = playerError;
        return this;
      }

      /**
       * 设置用于播放的 {@link RepeatMode}。
       *
       * @param repeatMode 用于播放的 {@link RepeatMode}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setRepeatMode(@Player.RepeatMode int repeatMode) {
        this.repeatMode = repeatMode;
        return this;
      }

      /**
       * 设置是否启用了媒体项的随机播放。
       *
       * @param shuffleModeEnabled 是否启用了媒体项的随机播放。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setShuffleModeEnabled(boolean shuffleModeEnabled) {
        this.shuffleModeEnabled = shuffleModeEnabled;
        return this;
      }

      /**
       * 设置播放器当前是否正在加载其源。
       *
       * <p>如果 {@linkplain #setPlaybackState 状态} 为 {@link Player#STATE_IDLE} 或 {@link Player#STATE_ENDED}，则不能将播放器标记为正在加载。
       *
       * @param isLoading 播放器当前是否正在加载其源。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setIsLoading(boolean isLoading) {
        this.isLoading = isLoading;
        return this;
      }

      /**
       * 设置 {@link Player#seekBack()} 的增量，以毫秒为单位。
       *
       * @param seekBackIncrementMs {@link Player#seekBack()} 的增量，以毫秒为单位。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setSeekBackIncrementMs(long seekBackIncrementMs) {
        this.seekBackIncrementMs = seekBackIncrementMs;
        return this;
      }

      /**
       * 设置 {@link Player#seekForward()} 的增量，以毫秒为单位。
       *
       * @param seekForwardIncrementMs {@link Player#seekForward()} 的增量，以毫秒为单位。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setSeekForwardIncrementMs(long seekForwardIncrementMs) {
        this.seekForwardIncrementMs = seekForwardIncrementMs;
        return this;
      }

      /**
       * 设置 {@link #seekToPrevious()} 跳转到上一个项目的最大位置，以毫秒为单位。
       *
       * @param maxSeekToPreviousPositionMs {@link #seekToPrevious()} 跳转到上一个项目的最大位置，以毫秒为单位。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setMaxSeekToPreviousPositionMs(long maxSeekToPreviousPositionMs) {
        this.maxSeekToPreviousPositionMs = maxSeekToPreviousPositionMs;
        return this;
      }

      /**
       * 设置当前活动的 {@link PlaybackParameters}。
       *
       * @param playbackParameters 当前活动的 {@link PlaybackParameters}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        return this;
      }

      /**
       * 设置当前活动的 {@link TrackSelectionParameters}。
       *
       * @param trackSelectionParameters 当前活动的 {@link TrackSelectionParameters}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setTrackSelectionParameters(
          TrackSelectionParameters trackSelectionParameters) {
        this.trackSelectionParameters = trackSelectionParameters;
        return this;
      }

      /**
       * 设置当前的 {@link AudioAttributes}。
       *
       * @param audioAttributes 当前的 {@link AudioAttributes}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setAudioAttributes(AudioAttributes audioAttributes) {
        this.audioAttributes = audioAttributes;
        return this;
      }

      /**
       * 设置当前的音频音量，0 表示静音，1 表示单位增益（信号不变）。
       *
       * @param volume 当前的音频音量，0 表示静音，1 表示单位增益（信号不变）。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setVolume(@FloatRange(from = 0, to = 1.0) float volume) {
        checkArgument(volume >= 0.0f && volume <= 1.0f);
        this.volume = volume;
        return this;
      }

      /**
       * 设置当前的视频尺寸。
       *
       * @param videoSize 当前的视频尺寸。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setVideoSize(VideoSize videoSize) {
        this.videoSize = videoSize;
        return this;
      }

      /**
       * 设置当前的 {@linkplain CueGroup 字幕组}。
       *
       * @param currentCues 当前的 {@linkplain CueGroup 字幕组}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setCurrentCues(CueGroup currentCues) {
        this.currentCues = currentCues;
        return this;
      }

      /**
       * 设置 {@link DeviceInfo}。
       *
       * @param deviceInfo {@link DeviceInfo}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setDeviceInfo(DeviceInfo deviceInfo) {
        this.deviceInfo = deviceInfo;
        return this;
      }

      /**
       * 设置当前的设备音量。
       *
       * @param deviceVolume 当前的设备音量。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setDeviceVolume(@IntRange(from = 0) int deviceVolume) {
        checkArgument(deviceVolume >= 0);
        this.deviceVolume = deviceVolume;
        return this;
      }

      /**
       * 设置设备是否静音。
       *
       * @param isDeviceMuted 设备是否静音。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setIsDeviceMuted(boolean isDeviceMuted) {
        this.isDeviceMuted = isDeviceMuted;
        return this;
      }

      /**
       * 设置渲染视频的表面的尺寸。
       *
       * @param surfaceSize 表面的尺寸。如果未知，尺寸可能是 {@link C#LENGTH_UNSET}；如果视频未渲染到表面上，则可能是 0。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setSurfaceSize(Size surfaceSize) {
        this.surfaceSize = surfaceSize;
        return this;
      }

      /**
       * 设置自设置表面、渲染重置或渲染的流更改以来是否首次渲染了帧。
       *
       * <p>注意：由于这将触发 {@link Listener#onRenderedFirstFrame()} 事件，因此该标志应仅在首次渲染帧后的第一次 {@link State} 更新时设置。
       *
       * @param newlyRenderedFirstFrame 是否首次渲染了帧。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setNewlyRenderedFirstFrame(boolean newlyRenderedFirstFrame) {
        this.newlyRenderedFirstFrame = newlyRenderedFirstFrame;
        return this;
      }

      /**
       * 设置最近的定时 {@link Metadata}。
       *
       * <p>如果 {@link Metadata#presentationTimeUs} 为 {@link C#TIME_UNSET}，则不会将元数据转发给监听器。
       *
       * @param timedMetadata 最近的定时 {@link Metadata}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setTimedMetadata(Metadata timedMetadata) {
        this.timedMetadata = timedMetadata;
        return this;
      }

      /**
       * 将播放列表设置为 {@link MediaItemData 媒体项} 的列表。
       *
       * <p>所有项必须具有唯一的 {@linkplain MediaItemData.Builder#setUid UID}。
       *
       * <p>此调用将替换之前通过 {@link #setPlaylist(Timeline, Tracks, MediaMetadata)} 设置的任何播放列表。
       *
       * @param playlist {@link MediaItemData 媒体项} 的列表。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setPlaylist(List<MediaItemData> playlist) {
        HashSet<Object> uids = new HashSet<>();
        for (int i = 0; i < playlist.size(); i++) {
          checkArgument(uids.add(playlist.get(i).uid), "播放列表中的 MediaItemData UID 重复");
        }
        this.playlist = ImmutableList.copyOf(playlist);
        this.timeline = new PlaylistTimeline(this.playlist);
        this.currentTracks = null;
        this.currentMetadata = null;
        return this;
      }

      /**
       * 将播放列表设置为包含当前 {@link Tracks} 和 {@link MediaMetadata} 信息的 {@link Timeline}。
       *
       * <p>此调用将替换之前通过 {@link #setPlaylist(List)} 设置的任何播放列表。
       *
       * @param timeline 包含播放列表数据的 {@link Timeline}。
       * @param currentTracks {@linkplain #setCurrentMediaItemIndex 当前媒体项} 的 {@link Tracks}。
       * @param currentMetadata {@linkplain #setCurrentMediaItemIndex 当前媒体项} 的组合 {@link MediaMetadata}。如果为 null，则当前元数据假定为 {@link MediaItem#mediaMetadata MediaItem} 元数据与所选 {@link Format#metadata 格式} 元数据的组合。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setPlaylist(
          Timeline timeline, Tracks currentTracks, @Nullable MediaMetadata currentMetadata) {
        this.playlist = null;
        this.timeline = timeline;
        this.currentTracks = currentTracks;
        this.currentMetadata = currentMetadata;
        return this;
      }

      /**
       * 设置播放列表的 {@link MediaMetadata}。
       *
       * @param playlistMetadata 播放列表的 {@link MediaMetadata}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setPlaylistMetadata(MediaMetadata playlistMetadata) {
        this.playlistMetadata = playlistMetadata;
        return this;
      }

      /**
       * 设置当前媒体项的索引。
       *
       * <p>如果设置了播放列表，则媒体项索引必须小于 {@linkplain #setPlaylist 播放列表中的媒体项数量}。
       *
       * @param currentMediaItemIndex 当前媒体项的索引，或 {@link C#INDEX_UNSET} 以假定播放列表中的默认第一项。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setCurrentMediaItemIndex(int currentMediaItemIndex) {
        this.currentMediaItemIndex = currentMediaItemIndex;
        return this;
      }

      /**
       * 设置当前的广告索引，如果没有播放广告则为 {@link C#INDEX_UNSET}。
       *
       * <p>两个索引要么都需要是 {@link C#INDEX_UNSET}，要么都不是 {@link C#INDEX_UNSET}。
       *
       * <p>广告索引只能在当前 {@linkplain MediaItemData.Builder#setPeriods 时段} 中定义了相应的 {@link AdPlaybackState} 时设置。
       *
       * @param adGroupIndex 当前的广告组索引，如果没有播放广告则为 {@link C#INDEX_UNSET}。
       * @param adIndexInAdGroup 当前广告组中的广告索引，如果没有播放广告则为 {@link C#INDEX_UNSET}。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setCurrentAd(int adGroupIndex, int adIndexInAdGroup) {
        checkArgument((adGroupIndex == C.INDEX_UNSET) == (adIndexInAdGroup == C.INDEX_UNSET));
        this.currentAdGroupIndex = adGroupIndex;
        this.currentAdIndexInAdGroup = adIndexInAdGroup;
        return this;
      }

      /**
       * 设置当前内容的播放位置（以毫秒为单位）。
       *
       * <p>如果整体状态指示播放位置正在推进，则此位置将转换为推进的 {@link PositionSupplier}。
       *
       * <p>此方法会覆盖通过 {@link #setContentPositionMs(PositionSupplier)} 设置的其他 {@link PositionSupplier}。
       *
       * @param positionMs 当前内容的播放位置（以毫秒为单位），或 {@link C#TIME_UNSET} 以指示默认起始位置。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setContentPositionMs(long positionMs) {
        this.contentPositionMs = positionMs;
        return this;
      }

      /**
       * 设置当前内容播放位置的 {@link PositionSupplier}（以毫秒为单位）。
       *
       * <p>如果播放正在推进，供应商应在每次调用时返回更新后的位置，例如使用 {@link PositionSupplier#getExtrapolating}。
       *
       * <p>此方法会覆盖通过 {@link #setContentPositionMs(long)} 设置的其他位置。
       *
       * @param contentPositionMsSupplier 当前内容播放位置的 {@link PositionSupplier}（以毫秒为单位），或 {@link C#TIME_UNSET} 以指示默认起始位置。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setContentPositionMs(PositionSupplier contentPositionMsSupplier) {
        this.contentPositionMs = null;
        this.contentPositionMsSupplier = contentPositionMsSupplier;
        return this;
      }

      /**
       * 设置当前广告的播放位置（以毫秒为单位）。如果没有播放广告，则忽略此值。
       *
       * <p>如果整体状态指示广告播放位置正在推进，则此位置将转换为推进的 {@link PositionSupplier}。
       *
       * <p>此方法会覆盖通过 {@link #setAdPositionMs(PositionSupplier)} 设置的其他 {@link PositionSupplier}。
       *
       * @param positionMs 当前广告的播放位置（以毫秒为单位）。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setAdPositionMs(long positionMs) {
        this.adPositionMs = positionMs;
        return this;
      }

      /**
       * 设置当前广告播放位置的 {@link PositionSupplier}（以毫秒为单位）。如果没有播放广告，则忽略此值。
       *
       * <p>如果播放正在推进，供应商应在每次调用时返回更新后的位置，例如使用 {@link PositionSupplier#getExtrapolating}。
       *
       * <p>此方法会覆盖通过 {@link #setAdPositionMs(long)} 设置的其他位置。
       *
       * @param adPositionMsSupplier 当前广告播放位置的 {@link PositionSupplier}（以毫秒为单位）。如果没有播放广告，则忽略此值。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setAdPositionMs(PositionSupplier adPositionMsSupplier) {
        this.adPositionMs = null;
        this.adPositionMsSupplier = adPositionMsSupplier;
        return this;
      }

      /**
       * 设置当前播放内容的缓冲位置的 {@link PositionSupplier}（以毫秒为单位）。
       *
       * @param contentBufferedPositionMsSupplier 当前播放内容的缓冲位置的 {@link PositionSupplier}（以毫秒为单位），或 {@link C#TIME_UNSET} 以指示默认起始位置。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setContentBufferedPositionMs(
          PositionSupplier contentBufferedPositionMsSupplier) {
        this.contentBufferedPositionMsSupplier = contentBufferedPositionMsSupplier;
        return this;
      }

      /**
       * 设置当前播放广告的缓冲位置的 {@link PositionSupplier}（以毫秒为单位）。如果没有播放广告，则忽略此值。
       *
       * @param adBufferedPositionMsSupplier 当前播放广告的缓冲位置的 {@link PositionSupplier}（以毫秒为单位）。如果没有播放广告，则忽略此值。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setAdBufferedPositionMs(PositionSupplier adBufferedPositionMsSupplier) {
        this.adBufferedPositionMsSupplier = adBufferedPositionMsSupplier;
        return this;
      }

      /**
       * 设置总缓冲时长的 {@link PositionSupplier}（以毫秒为单位）。
       *
       * @param totalBufferedDurationMsSupplier 总缓冲时长的 {@link PositionSupplier}（以毫秒为单位）。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setTotalBufferedDurationMs(PositionSupplier totalBufferedDurationMsSupplier) {
        this.totalBufferedDurationMsSupplier = totalBufferedDurationMsSupplier;
        return this;
      }

      /**
       * 指示自上次播放器更新以来发生了位置不连续，并设置其原因。
       *
       * @param positionDiscontinuityReason 不连续的 {@linkplain Player.DiscontinuityReason 原因}。
       * @param discontinuityPositionMs 不连续后继续播放的当前内容或广告中的位置（以毫秒为单位）。
       * @return 此构建器。
       * @see #clearPositionDiscontinuity
       */
      @CanIgnoreReturnValue
      public Builder setPositionDiscontinuity(
          @Player.DiscontinuityReason int positionDiscontinuityReason,
          long discontinuityPositionMs) {
        this.hasPositionDiscontinuity = true;
        this.positionDiscontinuityReason = positionDiscontinuityReason;
        this.discontinuityPositionMs = discontinuityPositionMs;
        return this;
      }

      /**
       * 清除之前设置的位置不连续信号。
       *
       * @return 此构建器。
       * @see #hasPositionDiscontinuity
       */
      @CanIgnoreReturnValue
      public Builder clearPositionDiscontinuity() {
        this.hasPositionDiscontinuity = false;
        return this;
      }

      /** 构建 {@link State}。 */
      public State build() {
        return new State(this);
      }
    }

    /** 可用的 {@link Commands}。 */
    public final Commands availableCommands;

    /** 当播放器准备就绪且未被抑制时是否应继续播放。 */
    public final boolean playWhenReady;

    /** 更改 {@link #playWhenReady} 的最后原因。 */
    public final @PlayWhenReadyChangeReason int playWhenReadyChangeReason;

    /** 播放器的 {@linkplain Player.State 状态}。 */
    public final @Player.State int playbackState;

    /** 即使 {@link #getPlayWhenReady()} 为 true，播放仍被抑制的原因。 */
    public final @PlaybackSuppressionReason int playbackSuppressionReason;

    /** 导致播放失败的最后一次错误，如果没有错误则为 null。 */
    @Nullable public final PlaybackException playerError;

    /** 用于播放的 {@link RepeatMode}。 */
    public final @RepeatMode int repeatMode;

    /** 是否启用了媒体项的随机播放。 */
    public final boolean shuffleModeEnabled;

    /** 播放器当前是否正在加载其源。 */
    public final boolean isLoading;

    /** {@link Player#seekBack()} 的增量（以毫秒为单位）。 */
    public final long seekBackIncrementMs;

    /** {@link Player#seekForward()} 的增量（以毫秒为单位）。 */
    public final long seekForwardIncrementMs;

    /**
     * {@link #seekToPrevious()} 跳转到上一个项目的最大位置（以毫秒为单位）。
     */
    public final long maxSeekToPreviousPositionMs;

    /** 当前活动的 {@link PlaybackParameters}。 */
    public final PlaybackParameters playbackParameters;

    /** 当前活动的 {@link TrackSelectionParameters}。 */
    public final TrackSelectionParameters trackSelectionParameters;

    /** 当前的 {@link AudioAttributes}。 */
    public final AudioAttributes audioAttributes;

    /** 当前的音频音量，0 表示静音，1 表示单位增益（信号不变）。 */
    @FloatRange(from = 0, to = 1.0)
    public final float volume;

    /** 当前的视频尺寸。 */
    public final VideoSize videoSize;

    /** 当前的 {@linkplain CueGroup 字幕组}。 */
    public final CueGroup currentCues;

    /** {@link DeviceInfo}。 */
    public final DeviceInfo deviceInfo;

    /** 当前的设备音量。 */
    @IntRange(from = 0)
    public final int deviceVolume;

    /** 设备是否静音。 */
    public final boolean isDeviceMuted;

    /** 渲染视频的表面的尺寸。 */
    public final Size surfaceSize;

    /**
     * 自设置表面、渲染重置或渲染的流更改以来是否首次渲染了帧。
     */
    public final boolean newlyRenderedFirstFrame;

    /** 最近的定时元数据。 */
    public final Metadata timedMetadata;

    /** {@link Timeline}。 */
    public final Timeline timeline;

    /** 当前的 {@link Tracks}。 */
    public final Tracks currentTracks;

    /** 当前的组合 {@link MediaMetadata}。 */
    public final MediaMetadata currentMetadata;

    /** 播放列表的 {@link MediaMetadata}。 */
    public final MediaMetadata playlistMetadata;

    /**
     * 当前媒体项的索引，或 {@link C#INDEX_UNSET} 以假定播放列表中的默认第一项。
     */
    public final int currentMediaItemIndex;

    /** 当前的广告组索引，如果没有播放广告则为 {@link C#INDEX_UNSET}。 */
    public final int currentAdGroupIndex;

    /** 当前广告组中的广告索引，如果没有播放广告则为 {@link C#INDEX_UNSET}。 */
    public final int currentAdIndexInAdGroup;

    /**
     * 当前内容播放位置的 {@link PositionSupplier}（以毫秒为单位），或 {@link C#TIME_UNSET} 以指示默认起始位置。
     */
    public final PositionSupplier contentPositionMsSupplier;

    /**
     * 当前广告播放位置的 {@link PositionSupplier}（以毫秒为单位）。如果没有播放广告，则忽略此值。
     */
    public final PositionSupplier adPositionMsSupplier;

    /**
     * 当前播放内容的缓冲位置的 {@link PositionSupplier}（以毫秒为单位），或 {@link C#TIME_UNSET} 以指示默认起始位置。
     */
    public final PositionSupplier contentBufferedPositionMsSupplier;

    /**
     * 当前播放广告的缓冲位置的 {@link PositionSupplier}（以毫秒为单位）。如果没有播放广告，则忽略此值。
     */
    public final PositionSupplier adBufferedPositionMsSupplier;

    /** 总缓冲时长的 {@link PositionSupplier}（以毫秒为单位）。 */
    public final PositionSupplier totalBufferedDurationMsSupplier;

    /** 指示自上次更新播放器以来是否发生了位置不连续。 */
    public final boolean hasPositionDiscontinuity;

    /**
     * 上次位置不连续的 {@linkplain Player.DiscontinuityReason 原因}。如果 {@link #hasPositionDiscontinuity} 为 {@code false}，则忽略此值。
     */
    public final @Player.DiscontinuityReason int positionDiscontinuityReason;

    /**
     * 不连续后继续播放的当前内容或广告中的位置（以毫秒为单位）。如果 {@link #hasPositionDiscontinuity} 为 {@code false}，则忽略此值。
     */
    public final long discontinuityPositionMs;

    private State(Builder builder) {
      Tracks currentTracks = builder.currentTracks;
      MediaMetadata currentMetadata = builder.currentMetadata;
      if (builder.timeline.isEmpty()) {
        checkArgument(
            builder.playbackState == Player.STATE_IDLE
                || builder.playbackState == Player.STATE_ENDED,
            "Empty playlist only allowed in STATE_IDLE or STATE_ENDED");
        checkArgument(
            builder.currentAdGroupIndex == C.INDEX_UNSET
                && builder.currentAdIndexInAdGroup == C.INDEX_UNSET,
            "Ads not allowed if playlist is empty");
        if (currentTracks == null) {
          currentTracks = Tracks.EMPTY;
        }
        if (currentMetadata == null) {
          currentMetadata = MediaMetadata.EMPTY;
        }
      } else {
        int mediaItemIndex = builder.currentMediaItemIndex;
        if (mediaItemIndex == C.INDEX_UNSET) {
          mediaItemIndex = 0; // TODO: Use shuffle order to find first index.
        } else {
          checkArgument(
              builder.currentMediaItemIndex < builder.timeline.getWindowCount(),
              "currentMediaItemIndex must be less than playlist.size()");
        }
        if (builder.currentAdGroupIndex != C.INDEX_UNSET) {
          Timeline.Period period = new Timeline.Period();
          Timeline.Window window = new Timeline.Window();
          long contentPositionMs =
              builder.contentPositionMs != null
                  ? builder.contentPositionMs
                  : builder.contentPositionMsSupplier.get();
          int periodIndex =
              getPeriodIndexFromWindowPosition(
                  builder.timeline, mediaItemIndex, contentPositionMs, window, period);
          builder.timeline.getPeriod(periodIndex, period);
          checkArgument(
              builder.currentAdGroupIndex < period.getAdGroupCount(),
              "PeriodData has less ad groups than adGroupIndex");
          int adCountInGroup = period.getAdCountInAdGroup(builder.currentAdGroupIndex);
          if (adCountInGroup != C.LENGTH_UNSET) {
            checkArgument(
                builder.currentAdIndexInAdGroup < adCountInGroup,
                "Ad group has less ads than adIndexInGroupIndex");
          }
        }
        if (builder.playlist != null) {
          MediaItemData mediaItemData = builder.playlist.get(mediaItemIndex);
          currentTracks = mediaItemData.tracks;
          currentMetadata = mediaItemData.mediaMetadata;
        }
        if (currentMetadata == null) {
          currentMetadata =
              getCombinedMediaMetadata(
                  builder.timeline.getWindow(mediaItemIndex, new Timeline.Window()).mediaItem,
                  checkNotNull(currentTracks));
        }
      }
      if (builder.playerError != null) {
        checkArgument(
            builder.playbackState == Player.STATE_IDLE, "Player error only allowed in STATE_IDLE");
      }
      if (builder.playbackState == Player.STATE_IDLE
          || builder.playbackState == Player.STATE_ENDED) {
        checkArgument(
            !builder.isLoading, "isLoading only allowed when not in STATE_IDLE or STATE_ENDED");
      }
      PositionSupplier contentPositionMsSupplier = builder.contentPositionMsSupplier;
      if (builder.contentPositionMs != null) {
        if (builder.currentAdGroupIndex == C.INDEX_UNSET
            && builder.playWhenReady
            && builder.playbackState == Player.STATE_READY
            && builder.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
            && builder.contentPositionMs != C.TIME_UNSET) {
          contentPositionMsSupplier =
              PositionSupplier.getExtrapolating(
                  builder.contentPositionMs, builder.playbackParameters.speed);
        } else {
          contentPositionMsSupplier = PositionSupplier.getConstant(builder.contentPositionMs);
        }
      }
      PositionSupplier adPositionMsSupplier = builder.adPositionMsSupplier;
      if (builder.adPositionMs != null) {
        if (builder.currentAdGroupIndex != C.INDEX_UNSET
            && builder.playWhenReady
            && builder.playbackState == Player.STATE_READY
            && builder.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
          adPositionMsSupplier =
              PositionSupplier.getExtrapolating(builder.adPositionMs, /* playbackSpeed= */ 1f);
        } else {
          adPositionMsSupplier = PositionSupplier.getConstant(builder.adPositionMs);
        }
      }
      this.availableCommands = builder.availableCommands;
      this.playWhenReady = builder.playWhenReady;
      this.playWhenReadyChangeReason = builder.playWhenReadyChangeReason;
      this.playbackState = builder.playbackState;
      this.playbackSuppressionReason = builder.playbackSuppressionReason;
      this.playerError = builder.playerError;
      this.repeatMode = builder.repeatMode;
      this.shuffleModeEnabled = builder.shuffleModeEnabled;
      this.isLoading = builder.isLoading;
      this.seekBackIncrementMs = builder.seekBackIncrementMs;
      this.seekForwardIncrementMs = builder.seekForwardIncrementMs;
      this.maxSeekToPreviousPositionMs = builder.maxSeekToPreviousPositionMs;
      this.playbackParameters = builder.playbackParameters;
      this.trackSelectionParameters = builder.trackSelectionParameters;
      this.audioAttributes = builder.audioAttributes;
      this.volume = builder.volume;
      this.videoSize = builder.videoSize;
      this.currentCues = builder.currentCues;
      this.deviceInfo = builder.deviceInfo;
      this.deviceVolume = builder.deviceVolume;
      this.isDeviceMuted = builder.isDeviceMuted;
      this.surfaceSize = builder.surfaceSize;
      this.newlyRenderedFirstFrame = builder.newlyRenderedFirstFrame;
      this.timedMetadata = builder.timedMetadata;
      this.timeline = builder.timeline;
      this.currentTracks = checkNotNull(currentTracks);
      this.currentMetadata = currentMetadata;
      this.playlistMetadata = builder.playlistMetadata;
      this.currentMediaItemIndex = builder.currentMediaItemIndex;
      this.currentAdGroupIndex = builder.currentAdGroupIndex;
      this.currentAdIndexInAdGroup = builder.currentAdIndexInAdGroup;
      this.contentPositionMsSupplier = contentPositionMsSupplier;
      this.adPositionMsSupplier = adPositionMsSupplier;
      this.contentBufferedPositionMsSupplier = builder.contentBufferedPositionMsSupplier;
      this.adBufferedPositionMsSupplier = builder.adBufferedPositionMsSupplier;
      this.totalBufferedDurationMsSupplier = builder.totalBufferedDurationMsSupplier;
      this.hasPositionDiscontinuity = builder.hasPositionDiscontinuity;
      this.positionDiscontinuityReason = builder.positionDiscontinuityReason;
      this.discontinuityPositionMs = builder.discontinuityPositionMs;
    }

    /** Returns a {@link Builder} pre-populated with the current state values. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    /**
     * Returns the list of {@link MediaItemData} for the current playlist.
     *
     * @see Builder#setPlaylist(List)
     */
    public ImmutableList<MediaItemData> getPlaylist() {
      if (timeline instanceof PlaylistTimeline) {
        return ((PlaylistTimeline) timeline).playlist;
      }
      Timeline.Window window = new Timeline.Window();
      Timeline.Period period = new Timeline.Period();
      ImmutableList.Builder<MediaItemData> items =
          ImmutableList.builderWithExpectedSize(timeline.getWindowCount());
      for (int i = 0; i < timeline.getWindowCount(); i++) {
        items.add(
            MediaItemData.buildFromState(
                /* state= */ this, /* mediaItemIndex= */ i, period, window));
      }
      return items.build();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof State)) {
        return false;
      }
      State state = (State) o;
      return playWhenReady == state.playWhenReady
          && playWhenReadyChangeReason == state.playWhenReadyChangeReason
          && availableCommands.equals(state.availableCommands)
          && playbackState == state.playbackState
          && playbackSuppressionReason == state.playbackSuppressionReason
          && Objects.equals(playerError, state.playerError)
          && repeatMode == state.repeatMode
          && shuffleModeEnabled == state.shuffleModeEnabled
          && isLoading == state.isLoading
          && seekBackIncrementMs == state.seekBackIncrementMs
          && seekForwardIncrementMs == state.seekForwardIncrementMs
          && maxSeekToPreviousPositionMs == state.maxSeekToPreviousPositionMs
          && playbackParameters.equals(state.playbackParameters)
          && trackSelectionParameters.equals(state.trackSelectionParameters)
          && audioAttributes.equals(state.audioAttributes)
          && volume == state.volume
          && videoSize.equals(state.videoSize)
          && currentCues.equals(state.currentCues)
          && deviceInfo.equals(state.deviceInfo)
          && deviceVolume == state.deviceVolume
          && isDeviceMuted == state.isDeviceMuted
          && surfaceSize.equals(state.surfaceSize)
          && newlyRenderedFirstFrame == state.newlyRenderedFirstFrame
          && timedMetadata.equals(state.timedMetadata)
          && timeline.equals(state.timeline)
          && currentTracks.equals(state.currentTracks)
          && currentMetadata.equals(state.currentMetadata)
          && playlistMetadata.equals(state.playlistMetadata)
          && currentMediaItemIndex == state.currentMediaItemIndex
          && currentAdGroupIndex == state.currentAdGroupIndex
          && currentAdIndexInAdGroup == state.currentAdIndexInAdGroup
          && contentPositionMsSupplier.equals(state.contentPositionMsSupplier)
          && adPositionMsSupplier.equals(state.adPositionMsSupplier)
          && contentBufferedPositionMsSupplier.equals(state.contentBufferedPositionMsSupplier)
          && adBufferedPositionMsSupplier.equals(state.adBufferedPositionMsSupplier)
          && totalBufferedDurationMsSupplier.equals(state.totalBufferedDurationMsSupplier)
          && hasPositionDiscontinuity == state.hasPositionDiscontinuity
          && positionDiscontinuityReason == state.positionDiscontinuityReason
          && discontinuityPositionMs == state.discontinuityPositionMs;
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + availableCommands.hashCode();
      result = 31 * result + (playWhenReady ? 1 : 0);
      result = 31 * result + playWhenReadyChangeReason;
      result = 31 * result + playbackState;
      result = 31 * result + playbackSuppressionReason;
      result = 31 * result + (playerError == null ? 0 : playerError.hashCode());
      result = 31 * result + repeatMode;
      result = 31 * result + (shuffleModeEnabled ? 1 : 0);
      result = 31 * result + (isLoading ? 1 : 0);
      result = 31 * result + (int) (seekBackIncrementMs ^ (seekBackIncrementMs >>> 32));
      result = 31 * result + (int) (seekForwardIncrementMs ^ (seekForwardIncrementMs >>> 32));
      result =
          31 * result + (int) (maxSeekToPreviousPositionMs ^ (maxSeekToPreviousPositionMs >>> 32));
      result = 31 * result + playbackParameters.hashCode();
      result = 31 * result + trackSelectionParameters.hashCode();
      result = 31 * result + audioAttributes.hashCode();
      result = 31 * result + Float.floatToRawIntBits(volume);
      result = 31 * result + videoSize.hashCode();
      result = 31 * result + currentCues.hashCode();
      result = 31 * result + deviceInfo.hashCode();
      result = 31 * result + deviceVolume;
      result = 31 * result + (isDeviceMuted ? 1 : 0);
      result = 31 * result + surfaceSize.hashCode();
      result = 31 * result + (newlyRenderedFirstFrame ? 1 : 0);
      result = 31 * result + timedMetadata.hashCode();
      result = 31 * result + timeline.hashCode();
      result = 31 * result + currentTracks.hashCode();
      result = 31 * result + currentMetadata.hashCode();
      result = 31 * result + playlistMetadata.hashCode();
      result = 31 * result + currentMediaItemIndex;
      result = 31 * result + currentAdGroupIndex;
      result = 31 * result + currentAdIndexInAdGroup;
      result = 31 * result + contentPositionMsSupplier.hashCode();
      result = 31 * result + adPositionMsSupplier.hashCode();
      result = 31 * result + contentBufferedPositionMsSupplier.hashCode();
      result = 31 * result + adBufferedPositionMsSupplier.hashCode();
      result = 31 * result + totalBufferedDurationMsSupplier.hashCode();
      result = 31 * result + (hasPositionDiscontinuity ? 1 : 0);
      result = 31 * result + positionDiscontinuityReason;
      result = 31 * result + (int) (discontinuityPositionMs ^ (discontinuityPositionMs >>> 32));
      return result;
    }
  }

  private static final class PlaylistTimeline extends Timeline {

    private final ImmutableList<MediaItemData> playlist;
    private final int[] firstPeriodIndexByWindowIndex;
    private final int[] windowIndexByPeriodIndex;
    private final HashMap<Object, Integer> periodIndexByUid;

    public PlaylistTimeline(List<MediaItemData> playlist) {
      int mediaItemCount = playlist.size();
      this.playlist = ImmutableList.copyOf(playlist);
      this.firstPeriodIndexByWindowIndex = new int[mediaItemCount];
      int periodCount = 0;
      for (int i = 0; i < mediaItemCount; i++) {
        MediaItemData mediaItemData = playlist.get(i);
        firstPeriodIndexByWindowIndex[i] = periodCount;
        periodCount += getPeriodCountInMediaItem(mediaItemData);
      }
      this.windowIndexByPeriodIndex = new int[periodCount];
      this.periodIndexByUid = new HashMap<>();
      int periodIndex = 0;
      for (int i = 0; i < mediaItemCount; i++) {
        MediaItemData mediaItemData = playlist.get(i);
        for (int j = 0; j < getPeriodCountInMediaItem(mediaItemData); j++) {
          periodIndexByUid.put(mediaItemData.getPeriodUid(j), periodIndex);
          windowIndexByPeriodIndex[periodIndex] = i;
          periodIndex++;
        }
      }
    }

    @Override
    public int getWindowCount() {
      return playlist.size();
    }

    @Override
    public int getNextWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
      // TODO: Support shuffle order.
      return super.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
    }

    @Override
    public int getPreviousWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
      // TODO: Support shuffle order.
      return super.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
    }

    @Override
    public int getLastWindowIndex(boolean shuffleModeEnabled) {
      // TODO: Support shuffle order.
      return super.getLastWindowIndex(shuffleModeEnabled);
    }

    @Override
    public int getFirstWindowIndex(boolean shuffleModeEnabled) {
      // TODO: Support shuffle order.
      return super.getFirstWindowIndex(shuffleModeEnabled);
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      return playlist
          .get(windowIndex)
          .getWindow(firstPeriodIndexByWindowIndex[windowIndex], window);
    }

    @Override
    public int getPeriodCount() {
      return windowIndexByPeriodIndex.length;
    }

    @Override
    public Period getPeriodByUid(Object periodUid, Period period) {
      int periodIndex = checkNotNull(periodIndexByUid.get(periodUid));
      return getPeriod(periodIndex, period, /* setIds= */ true);
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      int windowIndex = windowIndexByPeriodIndex[periodIndex];
      int periodIndexInWindow = periodIndex - firstPeriodIndexByWindowIndex[windowIndex];
      return playlist.get(windowIndex).getPeriod(windowIndex, periodIndexInWindow, period);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      @Nullable Integer index = periodIndexByUid.get(uid);
      return index == null ? C.INDEX_UNSET : index;
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      int windowIndex = windowIndexByPeriodIndex[periodIndex];
      int periodIndexInWindow = periodIndex - firstPeriodIndexByWindowIndex[windowIndex];
      return playlist.get(windowIndex).getPeriodUid(periodIndexInWindow);
    }

    private static int getPeriodCountInMediaItem(MediaItemData mediaItemData) {
      return mediaItemData.periods.isEmpty() ? 1 : mediaItemData.periods.size();
    }
  }

  /**
   * An immutable description of an item in the playlist, containing both static setup information
   * like {@link MediaItem} and dynamic data that is generally read from the media like the
   * duration.
   */
  protected static final class MediaItemData {

    /** A builder for {@link MediaItemData} objects. */
    public static final class Builder {

      private Object uid;
      private Tracks tracks;
      private MediaItem mediaItem;
      @Nullable private MediaMetadata mediaMetadata;
      @Nullable private Object manifest;
      @Nullable private MediaItem.LiveConfiguration liveConfiguration;
      private long presentationStartTimeMs;
      private long windowStartTimeMs;
      private long elapsedRealtimeEpochOffsetMs;
      private boolean isSeekable;
      private boolean isDynamic;
      private long defaultPositionUs;
      private long durationUs;
      private long positionInFirstPeriodUs;
      private boolean isPlaceholder;
      private ImmutableList<PeriodData> periods;

      /**
       * Creates the builder.
       *
       * @param uid The unique identifier of the media item within a playlist. This value will be
       *     set as {@link Timeline.Window#uid} for this item.
       */
      public Builder(Object uid) {
        this.uid = uid;
        tracks = Tracks.EMPTY;
        mediaItem = MediaItem.EMPTY;
        mediaMetadata = null;
        manifest = null;
        liveConfiguration = null;
        presentationStartTimeMs = C.TIME_UNSET;
        windowStartTimeMs = C.TIME_UNSET;
        elapsedRealtimeEpochOffsetMs = C.TIME_UNSET;
        isSeekable = false;
        isDynamic = false;
        defaultPositionUs = 0;
        durationUs = C.TIME_UNSET;
        positionInFirstPeriodUs = 0;
        isPlaceholder = false;
        periods = ImmutableList.of();
      }

      private Builder(MediaItemData mediaItemData) {
        this.uid = mediaItemData.uid;
        this.tracks = mediaItemData.tracks;
        this.mediaItem = mediaItemData.mediaItem;
        this.mediaMetadata = mediaItemData.mediaMetadata;
        this.manifest = mediaItemData.manifest;
        this.liveConfiguration = mediaItemData.liveConfiguration;
        this.presentationStartTimeMs = mediaItemData.presentationStartTimeMs;
        this.windowStartTimeMs = mediaItemData.windowStartTimeMs;
        this.elapsedRealtimeEpochOffsetMs = mediaItemData.elapsedRealtimeEpochOffsetMs;
        this.isSeekable = mediaItemData.isSeekable;
        this.isDynamic = mediaItemData.isDynamic;
        this.defaultPositionUs = mediaItemData.defaultPositionUs;
        this.durationUs = mediaItemData.durationUs;
        this.positionInFirstPeriodUs = mediaItemData.positionInFirstPeriodUs;
        this.isPlaceholder = mediaItemData.isPlaceholder;
        this.periods = mediaItemData.periods;
      }

      /**
       * Sets the unique identifier of this media item within a playlist.
       *
       * <p>This value will be set as {@link Timeline.Window#uid} for this item.
       *
       * @param uid The unique identifier of this media item within a playlist.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setUid(Object uid) {
        this.uid = uid;
        return this;
      }

      /**
       * Sets the {@link Tracks} of this media item.
       *
       * @param tracks The {@link Tracks} of this media item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setTracks(Tracks tracks) {
        this.tracks = tracks;
        return this;
      }

      /**
       * Sets the {@link MediaItem}.
       *
       * @param mediaItem The {@link MediaItem}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setMediaItem(MediaItem mediaItem) {
        this.mediaItem = mediaItem;
        return this;
      }

      /**
       * Sets the {@link MediaMetadata}.
       *
       * <p>This data includes static data from the {@link MediaItem#mediaMetadata MediaItem} and
       * the media's {@link Format#metadata Format}, as well any dynamic metadata that has been
       * parsed from the media. If null, the metadata is assumed to be the simple combination of the
       * {@link MediaItem#mediaMetadata MediaItem} metadata and the metadata of the selected {@link
       * Format#metadata Formats}.
       *
       * @param mediaMetadata The {@link MediaMetadata}, or null to assume that the metadata is the
       *     simple combination of the {@link MediaItem#mediaMetadata MediaItem} metadata and the
       *     metadata of the selected {@link Format#metadata Formats}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setMediaMetadata(@Nullable MediaMetadata mediaMetadata) {
        this.mediaMetadata = mediaMetadata;
        return this;
      }

      /**
       * Sets the manifest of the media item.
       *
       * @param manifest The manifest of the media item, or null if not applicable.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setManifest(@Nullable Object manifest) {
        this.manifest = manifest;
        return this;
      }

      /**
       * Sets the active {@link MediaItem.LiveConfiguration}, or null if the media item is not live.
       *
       * @param liveConfiguration The active {@link MediaItem.LiveConfiguration}, or null if the
       *     media item is not live.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setLiveConfiguration(@Nullable MediaItem.LiveConfiguration liveConfiguration) {
        this.liveConfiguration = liveConfiguration;
        return this;
      }

      /**
       * Sets the start time of the live presentation.
       *
       * <p>This value can only be set to anything other than {@link C#TIME_UNSET} if the stream is
       * {@linkplain #setLiveConfiguration live}.
       *
       * @param presentationStartTimeMs The start time of the live presentation, in milliseconds
       *     since the Unix epoch, or {@link C#TIME_UNSET} if unknown or not applicable.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPresentationStartTimeMs(long presentationStartTimeMs) {
        this.presentationStartTimeMs = presentationStartTimeMs;
        return this;
      }

      /**
       * Sets the start time of the live window.
       *
       * <p>This value can only be set to anything other than {@link C#TIME_UNSET} if the stream is
       * {@linkplain #setLiveConfiguration live}. The value should also be greater or equal than the
       * {@linkplain #setPresentationStartTimeMs presentation start time}, if set.
       *
       * @param windowStartTimeMs The start time of the live window, in milliseconds since the Unix
       *     epoch, or {@link C#TIME_UNSET} if unknown or not applicable.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setWindowStartTimeMs(long windowStartTimeMs) {
        this.windowStartTimeMs = windowStartTimeMs;
        return this;
      }

      /**
       * Sets the offset between {@link SystemClock#elapsedRealtime()} and the time since the Unix
       * epoch according to the clock of the media origin server.
       *
       * <p>This value can only be set to anything other than {@link C#TIME_UNSET} if the stream is
       * {@linkplain #setLiveConfiguration live}.
       *
       * @param elapsedRealtimeEpochOffsetMs The offset between {@link
       *     SystemClock#elapsedRealtime()} and the time since the Unix epoch according to the clock
       *     of the media origin server, or {@link C#TIME_UNSET} if unknown or not applicable.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setElapsedRealtimeEpochOffsetMs(long elapsedRealtimeEpochOffsetMs) {
        this.elapsedRealtimeEpochOffsetMs = elapsedRealtimeEpochOffsetMs;
        return this;
      }

      /**
       * Sets whether it's possible to seek within this media item.
       *
       * @param isSeekable Whether it's possible to seek within this media item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsSeekable(boolean isSeekable) {
        this.isSeekable = isSeekable;
        return this;
      }

      /**
       * Sets whether this media item may change over time, for example a moving live window.
       *
       * @param isDynamic Whether this media item may change over time, for example a moving live
       *     window.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsDynamic(boolean isDynamic) {
        this.isDynamic = isDynamic;
        return this;
      }

      /**
       * Sets the default position relative to the start of the media item at which to begin
       * playback, in microseconds.
       *
       * <p>The default position must be less or equal to the {@linkplain #setDurationUs duration},
       * if set.
       *
       * @param defaultPositionUs The default position relative to the start of the media item at
       *     which to begin playback, in microseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDefaultPositionUs(long defaultPositionUs) {
        checkArgument(defaultPositionUs >= 0);
        this.defaultPositionUs = defaultPositionUs;
        return this;
      }

      /**
       * Sets the duration of the media item, in microseconds.
       *
       * <p>If both this duration and all {@linkplain #setPeriods period} durations are set, the sum
       * of this duration and the {@linkplain #setPositionInFirstPeriodUs offset in the first
       * period} must match the total duration of all periods.
       *
       * @param durationUs The duration of the media item, in microseconds, or {@link C#TIME_UNSET}
       *     if unknown.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDurationUs(long durationUs) {
        checkArgument(durationUs == C.TIME_UNSET || durationUs >= 0);
        this.durationUs = durationUs;
        return this;
      }

      /**
       * Sets the position of the start of this media item relative to the start of the first period
       * belonging to it, in microseconds.
       *
       * @param positionInFirstPeriodUs The position of the start of this media item relative to the
       *     start of the first period belonging to it, in microseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPositionInFirstPeriodUs(long positionInFirstPeriodUs) {
        checkArgument(positionInFirstPeriodUs >= 0);
        this.positionInFirstPeriodUs = positionInFirstPeriodUs;
        return this;
      }

      /**
       * Sets whether this media item contains placeholder information because the real information
       * has yet to be loaded.
       *
       * @param isPlaceholder Whether this media item contains placeholder information because the
       *     real information has yet to be loaded.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsPlaceholder(boolean isPlaceholder) {
        this.isPlaceholder = isPlaceholder;
        return this;
      }

      /**
       * Sets the list of {@linkplain PeriodData periods} in this media item.
       *
       * <p>All periods must have unique {@linkplain PeriodData.Builder#setUid UIDs} and only the
       * last period is allowed to have an unset {@linkplain PeriodData.Builder#setDurationUs
       * duration}.
       *
       * @param periods The list of {@linkplain PeriodData periods} in this media item, or an empty
       *     list to assume a single period without ads and the same duration as the media item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPeriods(List<PeriodData> periods) {
        int periodCount = periods.size();
        for (int i = 0; i < periodCount - 1; i++) {
          checkArgument(
              periods.get(i).durationUs != C.TIME_UNSET, "Periods other than last need a duration");
          for (int j = i + 1; j < periodCount; j++) {
            checkArgument(
                !periods.get(i).uid.equals(periods.get(j).uid),
                "Duplicate PeriodData UIDs in period list");
          }
        }
        this.periods = ImmutableList.copyOf(periods);
        return this;
      }

      /** Builds the {@link MediaItemData}. */
      public MediaItemData build() {
        return new MediaItemData(this);
      }
    }

    /** The unique identifier of this media item. */
    public final Object uid;

    /** The {@link Tracks} of this media item. */
    public final Tracks tracks;

    /** The {@link MediaItem}. */
    public final MediaItem mediaItem;

    /**
     * The {@link MediaMetadata}, including static data from the {@link MediaItem#mediaMetadata
     * MediaItem} and the media's {@link Format#metadata Format}, as well any dynamic metadata that
     * has been parsed from the media. If null, the metadata is assumed to be the simple combination
     * of the {@link MediaItem#mediaMetadata MediaItem} metadata and the metadata of the selected
     * {@link Format#metadata Formats}.
     */
    @Nullable public final MediaMetadata mediaMetadata;

    /** The manifest of the media item, or null if not applicable. */
    @Nullable public final Object manifest;

    /** The active {@link MediaItem.LiveConfiguration}, or null if the media item is not live. */
    @Nullable public final MediaItem.LiveConfiguration liveConfiguration;

    /**
     * The start time of the live presentation, in milliseconds since the Unix epoch, or {@link
     * C#TIME_UNSET} if unknown or not applicable.
     */
    public final long presentationStartTimeMs;

    /**
     * The start time of the live window, in milliseconds since the Unix epoch, or {@link
     * C#TIME_UNSET} if unknown or not applicable.
     */
    public final long windowStartTimeMs;

    /**
     * The offset between {@link SystemClock#elapsedRealtime()} and the time since the Unix epoch
     * according to the clock of the media origin server, or {@link C#TIME_UNSET} if unknown or not
     * applicable.
     */
    public final long elapsedRealtimeEpochOffsetMs;

    /** Whether it's possible to seek within this media item. */
    public final boolean isSeekable;

    /** Whether this media item may change over time, for example a moving live window. */
    public final boolean isDynamic;

    /**
     * The default position relative to the start of the media item at which to begin playback, in
     * microseconds.
     */
    public final long defaultPositionUs;

    /** The duration of the media item, in microseconds, or {@link C#TIME_UNSET} if unknown. */
    public final long durationUs;

    /**
     * The position of the start of this media item relative to the start of the first period
     * belonging to it, in microseconds.
     */
    public final long positionInFirstPeriodUs;

    /**
     * Whether this media item contains placeholder information because the real information has yet
     * to be loaded.
     */
    public final boolean isPlaceholder;

    /**
     * The list of {@linkplain PeriodData periods} in this media item, or an empty list to assume a
     * single period without ads and the same duration as the media item.
     */
    public final ImmutableList<PeriodData> periods;

    private final long[] periodPositionInWindowUs;

    private MediaItemData(Builder builder) {
      if (builder.liveConfiguration == null) {
        checkArgument(
            builder.presentationStartTimeMs == C.TIME_UNSET,
            "presentationStartTimeMs can only be set if liveConfiguration != null");
        checkArgument(
            builder.windowStartTimeMs == C.TIME_UNSET,
            "windowStartTimeMs can only be set if liveConfiguration != null");
        checkArgument(
            builder.elapsedRealtimeEpochOffsetMs == C.TIME_UNSET,
            "elapsedRealtimeEpochOffsetMs can only be set if liveConfiguration != null");
      } else if (builder.presentationStartTimeMs != C.TIME_UNSET
          && builder.windowStartTimeMs != C.TIME_UNSET) {
        checkArgument(
            builder.windowStartTimeMs >= builder.presentationStartTimeMs,
            "windowStartTimeMs can't be less than presentationStartTimeMs");
      }
      int periodCount = builder.periods.size();
      if (builder.durationUs != C.TIME_UNSET) {
        checkArgument(
            builder.defaultPositionUs <= builder.durationUs,
            "defaultPositionUs can't be greater than durationUs");
      }
      this.uid = builder.uid;
      this.tracks = builder.tracks;
      this.mediaItem = builder.mediaItem;
      this.mediaMetadata = builder.mediaMetadata;
      this.manifest = builder.manifest;
      this.liveConfiguration = builder.liveConfiguration;
      this.presentationStartTimeMs = builder.presentationStartTimeMs;
      this.windowStartTimeMs = builder.windowStartTimeMs;
      this.elapsedRealtimeEpochOffsetMs = builder.elapsedRealtimeEpochOffsetMs;
      this.isSeekable = builder.isSeekable;
      this.isDynamic = builder.isDynamic;
      this.defaultPositionUs = builder.defaultPositionUs;
      this.durationUs = builder.durationUs;
      this.positionInFirstPeriodUs = builder.positionInFirstPeriodUs;
      this.isPlaceholder = builder.isPlaceholder;
      this.periods = builder.periods;
      periodPositionInWindowUs = new long[periods.size()];
      if (!periods.isEmpty()) {
        periodPositionInWindowUs[0] = -positionInFirstPeriodUs;
        for (int i = 0; i < periodCount - 1; i++) {
          periodPositionInWindowUs[i + 1] = periodPositionInWindowUs[i] + periods.get(i).durationUs;
        }
      }
    }

    /** Returns a {@link Builder} pre-populated with the current values. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof MediaItemData)) {
        return false;
      }
      MediaItemData mediaItemData = (MediaItemData) o;
      return this.uid.equals(mediaItemData.uid)
          && this.tracks.equals(mediaItemData.tracks)
          && this.mediaItem.equals(mediaItemData.mediaItem)
          && Util.areEqual(this.mediaMetadata, mediaItemData.mediaMetadata)
          && Util.areEqual(this.manifest, mediaItemData.manifest)
          && Util.areEqual(this.liveConfiguration, mediaItemData.liveConfiguration)
          && this.presentationStartTimeMs == mediaItemData.presentationStartTimeMs
          && this.windowStartTimeMs == mediaItemData.windowStartTimeMs
          && this.elapsedRealtimeEpochOffsetMs == mediaItemData.elapsedRealtimeEpochOffsetMs
          && this.isSeekable == mediaItemData.isSeekable
          && this.isDynamic == mediaItemData.isDynamic
          && this.defaultPositionUs == mediaItemData.defaultPositionUs
          && this.durationUs == mediaItemData.durationUs
          && this.positionInFirstPeriodUs == mediaItemData.positionInFirstPeriodUs
          && this.isPlaceholder == mediaItemData.isPlaceholder
          && this.periods.equals(mediaItemData.periods);
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + uid.hashCode();
      result = 31 * result + tracks.hashCode();
      result = 31 * result + mediaItem.hashCode();
      result = 31 * result + (mediaMetadata == null ? 0 : mediaMetadata.hashCode());
      result = 31 * result + (manifest == null ? 0 : manifest.hashCode());
      result = 31 * result + (liveConfiguration == null ? 0 : liveConfiguration.hashCode());
      result = 31 * result + (int) (presentationStartTimeMs ^ (presentationStartTimeMs >>> 32));
      result = 31 * result + (int) (windowStartTimeMs ^ (windowStartTimeMs >>> 32));
      result =
          31 * result
              + (int) (elapsedRealtimeEpochOffsetMs ^ (elapsedRealtimeEpochOffsetMs >>> 32));
      result = 31 * result + (isSeekable ? 1 : 0);
      result = 31 * result + (isDynamic ? 1 : 0);
      result = 31 * result + (int) (defaultPositionUs ^ (defaultPositionUs >>> 32));
      result = 31 * result + (int) (durationUs ^ (durationUs >>> 32));
      result = 31 * result + (int) (positionInFirstPeriodUs ^ (positionInFirstPeriodUs >>> 32));
      result = 31 * result + (isPlaceholder ? 1 : 0);
      result = 31 * result + periods.hashCode();
      return result;
    }

    private static MediaItemData buildFromState(
        State state, int mediaItemIndex, Timeline.Period period, Timeline.Window window) {
      boolean isCurrentItem = getCurrentMediaItemIndexInternal(state) == mediaItemIndex;
      state.timeline.getWindow(mediaItemIndex, window);
      ImmutableList.Builder<PeriodData> periods = ImmutableList.builder();
      for (int i = window.firstPeriodIndex; i <= window.lastPeriodIndex; i++) {
        state.timeline.getPeriod(/* periodIndex= */ i, period, /* setIds= */ true);
        periods.add(
            new PeriodData.Builder(checkNotNull(period.uid))
                .setAdPlaybackState(period.adPlaybackState)
                .setDurationUs(period.durationUs)
                .setIsPlaceholder(period.isPlaceholder)
                .build());
      }
      return new MediaItemData.Builder(window.uid)
          .setDefaultPositionUs(window.defaultPositionUs)
          .setDurationUs(window.durationUs)
          .setElapsedRealtimeEpochOffsetMs(window.elapsedRealtimeEpochOffsetMs)
          .setIsDynamic(window.isDynamic)
          .setIsPlaceholder(window.isPlaceholder)
          .setIsSeekable(window.isSeekable)
          .setLiveConfiguration(window.liveConfiguration)
          .setManifest(window.manifest)
          .setMediaItem(window.mediaItem)
          .setMediaMetadata(isCurrentItem ? state.currentMetadata : null)
          .setPeriods(periods.build())
          .setPositionInFirstPeriodUs(window.positionInFirstPeriodUs)
          .setPresentationStartTimeMs(window.presentationStartTimeMs)
          .setTracks(isCurrentItem ? state.currentTracks : Tracks.EMPTY)
          .setWindowStartTimeMs(window.windowStartTimeMs)
          .build();
    }

    private Timeline.Window getWindow(int firstPeriodIndex, Timeline.Window window) {
      int periodCount = periods.isEmpty() ? 1 : periods.size();
      window.set(
          uid,
          mediaItem,
          manifest,
          presentationStartTimeMs,
          windowStartTimeMs,
          elapsedRealtimeEpochOffsetMs,
          isSeekable,
          isDynamic,
          liveConfiguration,
          defaultPositionUs,
          durationUs,
          firstPeriodIndex,
          /* lastPeriodIndex= */ firstPeriodIndex + periodCount - 1,
          positionInFirstPeriodUs);
      window.isPlaceholder = isPlaceholder;
      return window;
    }

    private Timeline.Period getPeriod(
        int windowIndex, int periodIndexInMediaItem, Timeline.Period period) {
      if (periods.isEmpty()) {
        period.set(
            /* id= */ uid,
            uid,
            windowIndex,
            /* durationUs= */ positionInFirstPeriodUs + durationUs,
            /* positionInWindowUs= */ 0,
            AdPlaybackState.NONE,
            isPlaceholder);
      } else {
        PeriodData periodData = periods.get(periodIndexInMediaItem);
        Object periodId = periodData.uid;
        Object periodUid = Pair.create(uid, periodId);
        period.set(
            periodId,
            periodUid,
            windowIndex,
            periodData.durationUs,
            periodPositionInWindowUs[periodIndexInMediaItem],
            periodData.adPlaybackState,
            periodData.isPlaceholder);
      }
      return period;
    }

    private Object getPeriodUid(int periodIndexInMediaItem) {
      if (periods.isEmpty()) {
        return uid;
      }
      Object periodId = periods.get(periodIndexInMediaItem).uid;
      return Pair.create(uid, periodId);
    }
  }

  /** Data describing the properties of a period inside a {@link MediaItemData}. */
  protected static final class PeriodData {

    /** A builder for {@link PeriodData} objects. */
    public static final class Builder {

      private Object uid;
      private long durationUs;
      private AdPlaybackState adPlaybackState;
      private boolean isPlaceholder;

      /**
       * Creates the builder.
       *
       * @param uid The unique identifier of the period within its media item.
       */
      public Builder(Object uid) {
        this.uid = uid;
        this.durationUs = 0;
        this.adPlaybackState = AdPlaybackState.NONE;
        this.isPlaceholder = false;
      }

      private Builder(PeriodData periodData) {
        this.uid = periodData.uid;
        this.durationUs = periodData.durationUs;
        this.adPlaybackState = periodData.adPlaybackState;
        this.isPlaceholder = periodData.isPlaceholder;
      }

      /**
       * Sets the unique identifier of the period within its media item.
       *
       * @param uid The unique identifier of the period within its media item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setUid(Object uid) {
        this.uid = uid;
        return this;
      }

      /**
       * Sets the total duration of the period, in microseconds, or {@link C#TIME_UNSET} if unknown.
       *
       * <p>Only the last period in a media item can have an unknown duration.
       *
       * @param durationUs The total duration of the period, in microseconds, or {@link
       *     C#TIME_UNSET} if unknown.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDurationUs(long durationUs) {
        checkArgument(durationUs == C.TIME_UNSET || durationUs >= 0);
        this.durationUs = durationUs;
        return this;
      }

      /**
       * Sets the {@link AdPlaybackState}.
       *
       * @param adPlaybackState The {@link AdPlaybackState}, or {@link AdPlaybackState#NONE} if
       *     there are no ads.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAdPlaybackState(AdPlaybackState adPlaybackState) {
        this.adPlaybackState = adPlaybackState;
        return this;
      }

      /**
       * Sets whether this period contains placeholder information because the real information has
       * yet to be loaded
       *
       * @param isPlaceholder Whether this period contains placeholder information because the real
       *     information has yet to be loaded.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsPlaceholder(boolean isPlaceholder) {
        this.isPlaceholder = isPlaceholder;
        return this;
      }

      /** Builds the {@link PeriodData}. */
      public PeriodData build() {
        return new PeriodData(this);
      }
    }

    /** The unique identifier of the period within its media item. */
    public final Object uid;

    /**
     * The total duration of the period, in microseconds, or {@link C#TIME_UNSET} if unknown. Only
     * the last period in a media item can have an unknown duration.
     */
    public final long durationUs;

    /**
     * The {@link AdPlaybackState} of the period, or {@link AdPlaybackState#NONE} if there are no
     * ads.
     */
    public final AdPlaybackState adPlaybackState;

    /**
     * Whether this period contains placeholder information because the real information has yet to
     * be loaded.
     */
    public final boolean isPlaceholder;

    private PeriodData(Builder builder) {
      this.uid = builder.uid;
      this.durationUs = builder.durationUs;
      this.adPlaybackState = builder.adPlaybackState;
      this.isPlaceholder = builder.isPlaceholder;
    }

    /** Returns a {@link Builder} pre-populated with the current values. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof PeriodData)) {
        return false;
      }
      PeriodData periodData = (PeriodData) o;
      return this.uid.equals(periodData.uid)
          && this.durationUs == periodData.durationUs
          && this.adPlaybackState.equals(periodData.adPlaybackState)
          && this.isPlaceholder == periodData.isPlaceholder;
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + uid.hashCode();
      result = 31 * result + (int) (durationUs ^ (durationUs >>> 32));
      result = 31 * result + adPlaybackState.hashCode();
      result = 31 * result + (isPlaceholder ? 1 : 0);
      return result;
    }
  }

  /** A supplier for a position. */
  protected interface PositionSupplier {

    /** An instance returning a constant position of zero. */
    PositionSupplier ZERO = getConstant(/* positionMs= */ 0);

    /**
     * Returns an instance that returns a constant value.
     *
     * @param positionMs The constant position to return, in milliseconds.
     */
    static PositionSupplier getConstant(long positionMs) {
      return () -> positionMs;
    }

    /**
     * Returns an instance that extrapolates the provided position into the future.
     *
     * @param currentPositionMs The current position in milliseconds.
     * @param playbackSpeed The playback speed with which the position is assumed to increase.
     */
    static PositionSupplier getExtrapolating(long currentPositionMs, float playbackSpeed) {
      long startTimeMs = SystemClock.elapsedRealtime();
      return () -> {
        long currentTimeMs = SystemClock.elapsedRealtime();
        return currentPositionMs + (long) ((currentTimeMs - startTimeMs) * playbackSpeed);
      };
    }

    /** Returns the position. */
    long get();
  }

  /**
   * Position difference threshold below which we do not automatically report a position
   * discontinuity, in milliseconds.
   */
  private static final long POSITION_DISCONTINUITY_THRESHOLD_MS = 1000;

  private final ListenerSet<Listener> listeners;
  private final Looper applicationLooper;
  private final HandlerWrapper applicationHandler;
  private final HashSet<ListenableFuture<?>> pendingOperations;
  private final Timeline.Period period;

  private @MonotonicNonNull State state;
  private boolean released;

  /**
   * Creates the base class.
   *
   * @param applicationLooper The {@link Looper} that must be used for all calls to the player and
   *     that is used to call listeners on.
   */
  protected SimpleBasePlayer(Looper applicationLooper) {
    this(applicationLooper, Clock.DEFAULT);
  }

  /**
   * Creates the base class.
   *
   * @param applicationLooper The {@link Looper} that must be used for all calls to the player and
   *     that is used to call listeners on.
   * @param clock The {@link Clock} that will be used by the player.
   */
  protected SimpleBasePlayer(Looper applicationLooper, Clock clock) {
    this.applicationLooper = applicationLooper;
    applicationHandler = clock.createHandler(applicationLooper, /* callback= */ null);
    pendingOperations = new HashSet<>();
    period = new Timeline.Period();
    @SuppressWarnings("nullness:argument.type.incompatible") // Using this in constructor.
    ListenerSet<Player.Listener> listenerSet =
        new ListenerSet<>(
            applicationLooper,
            clock,
            (listener, flags) -> listener.onEvents(/* player= */ this, new Events(flags)));
    listeners = listenerSet;
  }

  @Override
  public final void addListener(Listener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    listeners.add(checkNotNull(listener));
  }

  @Override
  public final void removeListener(Listener listener) {
    verifyApplicationThreadAndInitState();
    listeners.remove(listener);
  }

  @Override
  public final Looper getApplicationLooper() {
    // Don't verify application thread. We allow calls to this method from any thread.
    return applicationLooper;
  }

  @Override
  public final Commands getAvailableCommands() {
    verifyApplicationThreadAndInitState();
    return state.availableCommands;
  }

  @Override
  public final void setPlayWhenReady(boolean playWhenReady) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_PLAY_PAUSE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetPlayWhenReady(playWhenReady),
        /* placeholderStateSupplier= */ () ->
            state
                .buildUpon()
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build());
  }

  @Override
  public final boolean getPlayWhenReady() {
    verifyApplicationThreadAndInitState();
    return state.playWhenReady;
  }

  @Override
  public final void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    verifyApplicationThreadAndInitState();
    int startIndex = resetPosition ? C.INDEX_UNSET : state.currentMediaItemIndex;
    long startPositionMs = resetPosition ? C.TIME_UNSET : state.contentPositionMsSupplier.get();
    setMediaItemsInternal(mediaItems, startIndex, startPositionMs);
  }

  @Override
  public final void setMediaItems(
      List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    verifyApplicationThreadAndInitState();
    if (startIndex == C.INDEX_UNSET) {
      startIndex = state.currentMediaItemIndex;
      startPositionMs = state.contentPositionMsSupplier.get();
    }
    setMediaItemsInternal(mediaItems, startIndex, startPositionMs);
  }

  @RequiresNonNull("state")
  private void setMediaItemsInternal(
      List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    checkArgument(startIndex == C.INDEX_UNSET || startIndex >= 0);
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        && (mediaItems.size() != 1 || !shouldHandleCommand(Player.COMMAND_SET_MEDIA_ITEM))) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetMediaItems(mediaItems, startIndex, startPositionMs),
        /* placeholderStateSupplier= */ () -> {
          ArrayList<MediaItemData> placeholderPlaylist = new ArrayList<>();
          for (int i = 0; i < mediaItems.size(); i++) {
            placeholderPlaylist.add(getPlaceholderMediaItemData(mediaItems.get(i)));
          }
          return getStateWithNewPlaylistAndPosition(
              state, placeholderPlaylist, startIndex, startPositionMs, window);
        });
  }

  @Override
  public final void addMediaItems(int index, List<MediaItem> mediaItems) {
    verifyApplicationThreadAndInitState();
    checkArgument(index >= 0);
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    int playlistSize = state.timeline.getWindowCount();
    if (!shouldHandleCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS) || mediaItems.isEmpty()) {
      return;
    }
    int correctedIndex = min(index, playlistSize);
    updateStateForPendingOperation(
        /* pendingOperation= */ handleAddMediaItems(correctedIndex, mediaItems),
        /* placeholderStateSupplier= */ () -> {
          List<MediaItemData> placeholderPlaylist =
              buildMutablePlaylistFromState(state, period, window);
          for (int i = 0; i < mediaItems.size(); i++) {
            placeholderPlaylist.add(
                i + correctedIndex, getPlaceholderMediaItemData(mediaItems.get(i)));
          }
          if (!state.timeline.isEmpty()) {
            return getStateWithNewPlaylist(state, placeholderPlaylist, period, window);
          } else {
            // Handle initial position update when these are the first items added to the playlist.
            return getStateWithNewPlaylistAndPosition(
                state,
                placeholderPlaylist,
                state.currentMediaItemIndex,
                state.contentPositionMsSupplier.get(),
                window);
          }
        });
  }

  @Override
  public final void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    verifyApplicationThreadAndInitState();
    checkArgument(fromIndex >= 0 && toIndex >= fromIndex && newIndex >= 0);
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    int playlistSize = state.timeline.getWindowCount();
    if (!shouldHandleCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        || playlistSize == 0
        || fromIndex >= playlistSize) {
      return;
    }
    int correctedToIndex = min(toIndex, playlistSize);
    int correctedNewIndex = min(newIndex, playlistSize - (correctedToIndex - fromIndex));
    if (fromIndex == correctedToIndex || correctedNewIndex == fromIndex) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleMoveMediaItems(
            fromIndex, correctedToIndex, correctedNewIndex),
        /* placeholderStateSupplier= */ () -> {
          List<MediaItemData> placeholderPlaylist =
              buildMutablePlaylistFromState(state, period, window);
          Util.moveItems(placeholderPlaylist, fromIndex, correctedToIndex, correctedNewIndex);
          return getStateWithNewPlaylist(state, placeholderPlaylist, period, window);
        });
  }

  @Override
  public final void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    verifyApplicationThreadAndInitState();
    checkArgument(fromIndex >= 0 && fromIndex <= toIndex);
    State state = this.state;
    int playlistSize = state.timeline.getWindowCount();
    if (!shouldHandleCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS) || fromIndex > playlistSize) {
      return;
    }
    int correctedToIndex = min(toIndex, playlistSize);
    updateStateForPendingOperation(
        /* pendingOperation= */ handleReplaceMediaItems(fromIndex, correctedToIndex, mediaItems),
        /* placeholderStateSupplier= */ () -> {
          List<MediaItemData> placeholderPlaylist =
              buildMutablePlaylistFromState(state, period, window);
          for (int i = 0; i < mediaItems.size(); i++) {
            placeholderPlaylist.add(
                i + correctedToIndex, getPlaceholderMediaItemData(mediaItems.get(i)));
          }
          State updatedState;
          if (!state.timeline.isEmpty()) {
            updatedState = getStateWithNewPlaylist(state, placeholderPlaylist, period, window);
          } else {
            // Handle initial position update when these are the first items added to the playlist.
            updatedState =
                getStateWithNewPlaylistAndPosition(
                    state,
                    placeholderPlaylist,
                    state.currentMediaItemIndex,
                    state.contentPositionMsSupplier.get(),
                    window);
          }
          if (fromIndex < correctedToIndex) {
            Util.removeRange(placeholderPlaylist, fromIndex, correctedToIndex);
            return getStateWithNewPlaylist(updatedState, placeholderPlaylist, period, window);
          } else {
            return updatedState;
          }
        });
  }

  @Override
  public final void removeMediaItems(int fromIndex, int toIndex) {
    verifyApplicationThreadAndInitState();
    checkArgument(fromIndex >= 0 && toIndex >= fromIndex);
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    int playlistSize = state.timeline.getWindowCount();
    if (!shouldHandleCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        || playlistSize == 0
        || fromIndex >= playlistSize) {
      return;
    }
    int correctedToIndex = min(toIndex, playlistSize);
    if (fromIndex == correctedToIndex) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleRemoveMediaItems(fromIndex, correctedToIndex),
        /* placeholderStateSupplier= */ () -> {
          List<MediaItemData> placeholderPlaylist =
              buildMutablePlaylistFromState(state, period, window);
          Util.removeRange(placeholderPlaylist, fromIndex, correctedToIndex);
          return getStateWithNewPlaylist(state, placeholderPlaylist, period, window);
        });
  }

  @Override
  public final void prepare() {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_PREPARE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handlePrepare(),
        /* placeholderStateSupplier= */ () ->
            state
                .buildUpon()
                .setPlayerError(null)
                .setPlaybackState(state.timeline.isEmpty() ? STATE_ENDED : STATE_BUFFERING)
                .build());
  }

  @Override
  @Player.State
  public final int getPlaybackState() {
    verifyApplicationThreadAndInitState();
    return state.playbackState;
  }

  @Override
  public final int getPlaybackSuppressionReason() {
    verifyApplicationThreadAndInitState();
    return state.playbackSuppressionReason;
  }

  @Nullable
  @Override
  public final PlaybackException getPlayerError() {
    verifyApplicationThreadAndInitState();
    return state.playerError;
  }

  @Override
  public final void setRepeatMode(@Player.RepeatMode int repeatMode) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_REPEAT_MODE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetRepeatMode(repeatMode),
        /* placeholderStateSupplier= */ () -> state.buildUpon().setRepeatMode(repeatMode).build());
  }

  @Override
  @Player.RepeatMode
  public final int getRepeatMode() {
    verifyApplicationThreadAndInitState();
    return state.repeatMode;
  }

  @Override
  public final void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_SHUFFLE_MODE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetShuffleModeEnabled(shuffleModeEnabled),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setShuffleModeEnabled(shuffleModeEnabled).build());
  }

  @Override
  public final boolean getShuffleModeEnabled() {
    verifyApplicationThreadAndInitState();
    return state.shuffleModeEnabled;
  }

  @Override
  public final boolean isLoading() {
    verifyApplicationThreadAndInitState();
    return state.isLoading;
  }

  @Override
  @VisibleForTesting(otherwise = PROTECTED)
  public final void seekTo(
      int mediaItemIndex,
      long positionMs,
      @Player.Command int seekCommand,
      boolean isRepeatingCurrentItem) {
    verifyApplicationThreadAndInitState();
    checkArgument(mediaItemIndex == C.INDEX_UNSET || mediaItemIndex >= 0);
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(seekCommand)) {
      return;
    }
    boolean ignoreSeekForPlaceholderState =
        mediaItemIndex == C.INDEX_UNSET
            || isPlayingAd()
            || (!state.timeline.isEmpty() && mediaItemIndex >= state.timeline.getWindowCount());
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSeek(mediaItemIndex, positionMs, seekCommand),
        /* placeholderStateSupplier= */ () ->
            ignoreSeekForPlaceholderState
                ? state
                : getStateWithNewPlaylistAndPosition(
                    state, /* newPlaylist= */ null, mediaItemIndex, positionMs, window),
        /* forceSeekDiscontinuity= */ !ignoreSeekForPlaceholderState,
        isRepeatingCurrentItem);
  }

  @Override
  public final long getSeekBackIncrement() {
    verifyApplicationThreadAndInitState();
    return state.seekBackIncrementMs;
  }

  @Override
  public final long getSeekForwardIncrement() {
    verifyApplicationThreadAndInitState();
    return state.seekForwardIncrementMs;
  }

  @Override
  public final long getMaxSeekToPreviousPosition() {
    verifyApplicationThreadAndInitState();
    return state.maxSeekToPreviousPositionMs;
  }

  @Override
  public final void setPlaybackParameters(PlaybackParameters playbackParameters) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_SPEED_AND_PITCH)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetPlaybackParameters(playbackParameters),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setPlaybackParameters(playbackParameters).build());
  }

  @Override
  public final PlaybackParameters getPlaybackParameters() {
    verifyApplicationThreadAndInitState();
    return state.playbackParameters;
  }

  @Override
  public final void stop() {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_STOP)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleStop(),
        /* placeholderStateSupplier= */ () ->
            state
                .buildUpon()
                .setPlaybackState(Player.STATE_IDLE)
                .setTotalBufferedDurationMs(PositionSupplier.ZERO)
                .setContentBufferedPositionMs(
                    PositionSupplier.getConstant(getContentPositionMsInternal(state, window)))
                .setAdBufferedPositionMs(state.adPositionMsSupplier)
                .setIsLoading(false)
                .build());
  }

  @Override
  public final void release() {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_RELEASE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleRelease(), /* placeholderStateSupplier= */ () -> state);
    released = true;
    listeners.release();
    // Enforce some final state values in case getters are called after release.
    this.state =
        this.state
            .buildUpon()
            .setPlaybackState(Player.STATE_IDLE)
            .setTotalBufferedDurationMs(PositionSupplier.ZERO)
            .setContentBufferedPositionMs(
                PositionSupplier.getConstant(getContentPositionMsInternal(state, window)))
            .setAdBufferedPositionMs(state.adPositionMsSupplier)
            .setIsLoading(false)
            .build();
  }

  @Override
  public final Tracks getCurrentTracks() {
    verifyApplicationThreadAndInitState();
    return state.currentTracks;
  }

  @Override
  public final TrackSelectionParameters getTrackSelectionParameters() {
    verifyApplicationThreadAndInitState();
    return state.trackSelectionParameters;
  }

  @Override
  public final void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetTrackSelectionParameters(parameters),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setTrackSelectionParameters(parameters).build());
  }

  @Override
  public final MediaMetadata getMediaMetadata() {
    verifyApplicationThreadAndInitState();
    return state.currentMetadata;
  }

  @Override
  public final MediaMetadata getPlaylistMetadata() {
    verifyApplicationThreadAndInitState();
    return state.playlistMetadata;
  }

  @Override
  public final void setPlaylistMetadata(MediaMetadata mediaMetadata) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_PLAYLIST_METADATA)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetPlaylistMetadata(mediaMetadata),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setPlaylistMetadata(mediaMetadata).build());
  }

  @Override
  public final Timeline getCurrentTimeline() {
    verifyApplicationThreadAndInitState();
    return state.timeline;
  }

  @Override
  public final int getCurrentPeriodIndex() {
    verifyApplicationThreadAndInitState();
    return getCurrentPeriodIndexInternal(state, window, period);
  }

  @Override
  public final int getCurrentMediaItemIndex() {
    verifyApplicationThreadAndInitState();
    return getCurrentMediaItemIndexInternal(state);
  }

  @Override
  public final long getDuration() {
    verifyApplicationThreadAndInitState();
    if (isPlayingAd()) {
      state.timeline.getPeriod(getCurrentPeriodIndex(), period);
      long adDurationUs =
          period.getAdDurationUs(state.currentAdGroupIndex, state.currentAdIndexInAdGroup);
      return Util.usToMs(adDurationUs);
    }
    return getContentDuration();
  }

  @Override
  public final long getCurrentPosition() {
    verifyApplicationThreadAndInitState();
    return isPlayingAd() ? state.adPositionMsSupplier.get() : getContentPosition();
  }

  @Override
  public final long getBufferedPosition() {
    verifyApplicationThreadAndInitState();
    return isPlayingAd()
        ? max(state.adBufferedPositionMsSupplier.get(), state.adPositionMsSupplier.get())
        : getContentBufferedPosition();
  }

  @Override
  public final long getTotalBufferedDuration() {
    verifyApplicationThreadAndInitState();
    return state.totalBufferedDurationMsSupplier.get();
  }

  @Override
  public final boolean isPlayingAd() {
    verifyApplicationThreadAndInitState();
    return state.currentAdGroupIndex != C.INDEX_UNSET;
  }

  @Override
  public final int getCurrentAdGroupIndex() {
    verifyApplicationThreadAndInitState();
    return state.currentAdGroupIndex;
  }

  @Override
  public final int getCurrentAdIndexInAdGroup() {
    verifyApplicationThreadAndInitState();
    return state.currentAdIndexInAdGroup;
  }

  @Override
  public final long getContentPosition() {
    verifyApplicationThreadAndInitState();
    return getContentPositionMsInternal(state, window);
  }

  @Override
  public final long getContentBufferedPosition() {
    verifyApplicationThreadAndInitState();
    return max(
        getContentBufferedPositionMsInternal(state, window),
        getContentPositionMsInternal(state, window));
  }

  @Override
  public final AudioAttributes getAudioAttributes() {
    verifyApplicationThreadAndInitState();
    return state.audioAttributes;
  }

  @Override
  public final void setVolume(float volume) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_VOLUME)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetVolume(volume),
        /* placeholderStateSupplier= */ () -> state.buildUpon().setVolume(volume).build());
  }

  @Override
  public final float getVolume() {
    verifyApplicationThreadAndInitState();
    return state.volume;
  }

  @Override
  public final void setVideoSurface(@Nullable Surface surface) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }
    if (surface == null) {
      clearVideoSurface();
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetVideoOutput(surface),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setSurfaceSize(Size.UNKNOWN).build());
  }

  @Override
  public final void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }
    if (surfaceHolder == null) {
      clearVideoSurface();
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetVideoOutput(surfaceHolder),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setSurfaceSize(getSurfaceHolderSize(surfaceHolder)).build());
  }

  @Override
  public final void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }
    if (surfaceView == null) {
      clearVideoSurface();
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetVideoOutput(surfaceView),
        /* placeholderStateSupplier= */ () ->
            state
                .buildUpon()
                .setSurfaceSize(getSurfaceHolderSize(surfaceView.getHolder()))
                .build());
  }

  @Override
  public final void setVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }
    if (textureView == null) {
      clearVideoSurface();
      return;
    }
    Size surfaceSize;
    if (textureView.isAvailable()) {
      surfaceSize = new Size(textureView.getWidth(), textureView.getHeight());
    } else {
      surfaceSize = Size.ZERO;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetVideoOutput(textureView),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setSurfaceSize(surfaceSize).build());
  }

  @Override
  public final void clearVideoSurface() {
    clearVideoOutput(/* videoOutput= */ null);
  }

  @Override
  public final void clearVideoSurface(@Nullable Surface surface) {
    clearVideoOutput(surface);
  }

  @Override
  public final void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    clearVideoOutput(surfaceHolder);
  }

  @Override
  public final void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    clearVideoOutput(surfaceView);
  }

  @Override
  public final void clearVideoTextureView(@Nullable TextureView textureView) {
    clearVideoOutput(textureView);
  }

  private void clearVideoOutput(@Nullable Object videoOutput) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleClearVideoOutput(videoOutput),
        /* placeholderStateSupplier= */ () -> state.buildUpon().setSurfaceSize(Size.ZERO).build());
  }

  @Override
  public final VideoSize getVideoSize() {
    verifyApplicationThreadAndInitState();
    return state.videoSize;
  }

  @Override
  public final Size getSurfaceSize() {
    verifyApplicationThreadAndInitState();
    return state.surfaceSize;
  }

  @Override
  public final CueGroup getCurrentCues() {
    verifyApplicationThreadAndInitState();
    return state.currentCues;
  }

  @Override
  public final DeviceInfo getDeviceInfo() {
    verifyApplicationThreadAndInitState();
    return state.deviceInfo;
  }

  @Override
  public final int getDeviceVolume() {
    verifyApplicationThreadAndInitState();
    return state.deviceVolume;
  }

  @Override
  public final boolean isDeviceMuted() {
    verifyApplicationThreadAndInitState();
    return state.isDeviceMuted;
  }

  /**
   * @deprecated Use {@link #setDeviceVolume(int, int)} instead.
   */
  @SuppressWarnings("deprecation") // Using deprecated command code
  @Deprecated
  @Override
  public final void setDeviceVolume(int volume) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_DEVICE_VOLUME)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetDeviceVolume(volume, C.VOLUME_FLAG_SHOW_UI),
        /* placeholderStateSupplier= */ () -> state.buildUpon().setDeviceVolume(volume).build());
  }

  @Override
  public final void setDeviceVolume(int volume, @C.VolumeFlags int flags) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetDeviceVolume(volume, flags),
        /* placeholderStateSupplier= */ () -> state.buildUpon().setDeviceVolume(volume).build());
  }

  /**
   * @deprecated Use {@link #increaseDeviceVolume(int)} instead.
   */
  @SuppressWarnings("deprecation") // Using deprecated command code
  @Deprecated
  @Override
  public final void increaseDeviceVolume() {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_ADJUST_DEVICE_VOLUME)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleIncreaseDeviceVolume(C.VOLUME_FLAG_SHOW_UI),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setDeviceVolume(state.deviceVolume + 1).build());
  }

  @Override
  public final void increaseDeviceVolume(@C.VolumeFlags int flags) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleIncreaseDeviceVolume(flags),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setDeviceVolume(state.deviceVolume + 1).build());
  }

  /**
   * @deprecated Use {@link #decreaseDeviceVolume(int)} instead.
   */
  @SuppressWarnings("deprecation") // Using deprecated command code
  @Deprecated
  @Override
  public final void decreaseDeviceVolume() {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_ADJUST_DEVICE_VOLUME)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleDecreaseDeviceVolume(C.VOLUME_FLAG_SHOW_UI),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setDeviceVolume(max(0, state.deviceVolume - 1)).build());
  }

  @Override
  public final void decreaseDeviceVolume(@C.VolumeFlags int flags) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleDecreaseDeviceVolume(flags),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setDeviceVolume(max(0, state.deviceVolume - 1)).build());
  }

  /**
   * @deprecated Use {@link #setDeviceMuted(boolean, int)} instead.
   */
  @SuppressWarnings("deprecation") // Using deprecated command code
  @Deprecated
  @Override
  public final void setDeviceMuted(boolean muted) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_ADJUST_DEVICE_VOLUME)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetDeviceMuted(muted, C.VOLUME_FLAG_SHOW_UI),
        /* placeholderStateSupplier= */ () -> state.buildUpon().setIsDeviceMuted(muted).build());
  }

  @Override
  public final void setDeviceMuted(boolean muted, @C.VolumeFlags int flags) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetDeviceMuted(muted, flags),
        /* placeholderStateSupplier= */ () -> state.buildUpon().setIsDeviceMuted(muted).build());
  }

  @Override
  public final void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_AUDIO_ATTRIBUTES)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetAudioAttributes(audioAttributes, handleAudioFocus),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setAudioAttributes(audioAttributes).build());
  }

  /**
   * 使当前状态失效。
   *
   * <p>触发对 {@link #getState()} 的调用，并在状态更改时通知监听器。
   *
   * <p>请注意，在异步处理播放器方法时，此操作可能不会立即生效。一旦这些挂起的异步操作完成，状态将自动失效，无需再次调用此方法。
   */
  protected final void invalidateState() {
    verifyApplicationThreadAndInitState();
    if (!pendingOperations.isEmpty() || released) {
      return;
    }
    updateStateAndInformListeners(
        getState(), /* forceSeekDiscontinuity= */ false, /* isRepeatingCurrentItem= */ false);
  }

  /**
   * 返回播放器的当前 {@link State}。
   *
   * <p>{@link State} 应包括所有 {@linkplain State.Builder#setAvailableCommands(Commands) 可用命令}，以指示允许调用哪些播放器方法。
   *
   * <p>请注意，在异步处理播放器方法时，不会调用此方法。这意味着实现不需要处理由这些异步操作引起的状态更改，直到它们完成，并可以直接返回当前已知的状态。如果需要，可以通过重写 {@link #getPlaceholderState(State)} 来自定义这些异步操作进行期间使用的占位符状态。
   */
  @ForOverride
  protected abstract State getState();

  /**
   * 返回在异步处理播放器方法时使用的占位符状态。
   *
   * <p>{@code suggestedPlaceholderState} 已经包含了最可能的状态更新，例如如果调用了 {@code player.setPlayWhenReady(true)}，则会将 {@link State#playWhenReady} 设置为 true，实现只需在可以确定更准确的占位符状态时重写此方法。
   *
   * @param suggestedPlaceholderState 建议的占位符 {@link State}，包括处理所有挂起的异步操作的最可能结果。
   * @return 在异步操作挂起期间使用的占位符 {@link State}。
   */
  @ForOverride
  protected State getPlaceholderState(State suggestedPlaceholderState) {
    return suggestedPlaceholderState;
  }

  /**
   * 返回添加到播放列表的新 {@link MediaItem} 的占位符 {@link MediaItemData}。
   *
   * <p>实现只需在可以确定比默认更准确的占位符状态时重写此方法。
   *
   * @param mediaItem 添加到播放列表的 {@link MediaItem}。
   * @return 在将项目添加到播放列表的过程中使用的 {@link MediaItemData}。
   */
  @ForOverride
  protected MediaItemData getPlaceholderMediaItemData(MediaItem mediaItem) {
    return new MediaItemData.Builder(new PlaceholderUid())
        .setMediaItem(mediaItem)
        .setIsDynamic(true)
        .setIsPlaceholder(true)
        .build();
  }

  /**
   * 处理对 {@link Player#setPlayWhenReady}、{@link Player#play} 和 {@link Player#pause} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_PLAY_PAUSE} 可用时调用。
   *
   * @param playWhenReady 请求的 {@link State#playWhenReady}。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
    throw new IllegalStateException("缺少处理 COMMAND_PLAY_PAUSE 的实现");
  }

  /**
   * 处理对 {@link Player#prepare} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_PREPARE} 可用时调用。
   *
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handlePrepare() {
    throw new IllegalStateException("缺少处理 COMMAND_PREPARE 的实现");
  }

  /**
   * 处理对 {@link Player#stop} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_STOP} 可用时调用。
   *
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleStop() {
    throw new IllegalStateException("缺少处理 COMMAND_STOP 的实现");
  }

  /**
   * 处理对 {@link Player#release} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_RELEASE} 可用时调用。
   *
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleRelease() {
    throw new IllegalStateException("缺少处理 COMMAND_RELEASE 的实现");
  }

  /**
   * 处理对 {@link Player#setRepeatMode} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_SET_REPEAT_MODE} 可用时调用。
   *
   * @param repeatMode 请求的 {@link RepeatMode}。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSetRepeatMode(@RepeatMode int repeatMode) {
    throw new IllegalStateException("缺少处理 COMMAND_SET_REPEAT_MODE 的实现");
  }

  /**
   * 处理对 {@link Player#setShuffleModeEnabled} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_SET_SHUFFLE_MODE} 可用时调用。
   *
   * @param shuffleModeEnabled 是否请求启用随机播放模式。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSetShuffleModeEnabled(boolean shuffleModeEnabled) {
    throw new IllegalStateException("缺少处理 COMMAND_SET_SHUFFLE_MODE 的实现");
  }

  /**
   * 处理对 {@link Player#setPlaybackParameters} 或 {@link Player#setPlaybackSpeed} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_SET_SPEED_AND_PITCH} 可用时调用。
   *
   * @param playbackParameters 请求的 {@link PlaybackParameters}。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters playbackParameters) {
    throw new IllegalStateException("缺少处理 COMMAND_SET_SPEED_AND_PITCH 的实现");
  }

  /**
   * 处理对 {@link Player#setTrackSelectionParameters} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_SET_TRACK_SELECTION_PARAMETERS} 可用时调用。
   *
   * @param trackSelectionParameters 请求的 {@link TrackSelectionParameters}。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSetTrackSelectionParameters(
      TrackSelectionParameters trackSelectionParameters) {
    throw new IllegalStateException("缺少处理 COMMAND_SET_TRACK_SELECTION_PARAMETERS 的实现");
  }

  /**
   * 处理对 {@link Player#setPlaylistMetadata} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_SET_PLAYLIST_METADATA} 可用时调用。
   *
   * @param playlistMetadata 请求的 {@linkplain MediaMetadata 播放列表元数据}。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSetPlaylistMetadata(MediaMetadata playlistMetadata) {
    throw new IllegalStateException("缺少处理 COMMAND_SET_PLAYLIST_METADATA 的实现");
  }

  /**
   * 处理对 {@link Player#setVolume} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_SET_VOLUME} 可用时调用。
   *
   * @param volume 请求的音频音量，0 表示静音，1 表示单位增益（信号不变）。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSetVolume(@FloatRange(from = 0, to = 1.0) float volume) {
    throw new IllegalStateException("缺少处理 COMMAND_SET_VOLUME 的实现");
  }

  /**
   * 处理对 {@link Player#setDeviceVolume(int)} 和 {@link Player#setDeviceVolume(int, int)} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_SET_DEVICE_VOLUME} 或 {@link Player#COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS} 可用时调用。
   *
   * @param deviceVolume 请求的设备音量。
   * @param flags 0 或一个或多个 {@link C.VolumeFlags} 的按位组合。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSetDeviceVolume(
      @IntRange(from = 0) int deviceVolume, int flags) {
    throw new IllegalStateException(
        "缺少处理 COMMAND_SET_DEVICE_VOLUME 或 COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS 的实现");
  }

  /**
   * 处理对 {@link Player#increaseDeviceVolume()} 和 {@link Player#increaseDeviceVolume(int)} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_ADJUST_DEVICE_VOLUME} 或 {@link Player#COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS} 可用时调用。
   *
   * @param flags 0 或一个或多个 {@link C.VolumeFlags} 的按位组合。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleIncreaseDeviceVolume(@C.VolumeFlags int flags) {
    throw new IllegalStateException(
        "缺少处理 COMMAND_ADJUST_DEVICE_VOLUME 或 COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS 的实现");
  }

  /**
   * 处理对 {@link Player#decreaseDeviceVolume()} 和 {@link Player#decreaseDeviceVolume(int)} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_ADJUST_DEVICE_VOLUME} 或 {@link Player#COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS} 可用时调用。
   *
   * @param flags 0 或一个或多个 {@link C.VolumeFlags} 的按位组合。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleDecreaseDeviceVolume(@C.VolumeFlags int flags) {
    throw new IllegalStateException(
        "缺少处理 COMMAND_ADJUST_DEVICE_VOLUME 或 COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS 的实现");
  }

  /**
   * 处理对 {@link Player#setDeviceMuted(boolean)} 和 {@link Player#setDeviceMuted(boolean, int)} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_ADJUST_DEVICE_VOLUME} 或 {@link Player#COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS} 可用时调用。
   *
   * @param muted 是否请求将设备静音。
   * @param flags 0 或一个或多个 {@link C.VolumeFlags} 的按位组合。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSetDeviceMuted(boolean muted, @C.VolumeFlags int flags) {
    throw new IllegalStateException(
        "缺少处理 COMMAND_ADJUST_DEVICE_VOLUME 或 COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS 的实现");
  }

  /**
   * 处理设置音频属性的调用。
   *
   * <p>仅在 {@link Player#COMMAND_SET_AUDIO_ATTRIBUTES} 可用时调用。
   *
   * @param audioAttributes 用于音频播放的属性。
   * @param handleAudioFocus 如果播放器应处理音频焦点，则为 true，否则为 false。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSetAudioAttributes(
      AudioAttributes audioAttributes, boolean handleAudioFocus) {
    throw new IllegalStateException("缺少处理 COMMAND_SET_AUTIO_ATTRIBUTES 的实现");
  }

  /**
   * 处理设置视频输出的调用。
   *
   * <p>仅在 {@link Player#COMMAND_SET_VIDEO_SURFACE} 可用时调用。
   *
   * @param videoOutput 请求的视频输出。这是一个 {@link Surface}、{@link SurfaceHolder}、{@link TextureView} 或 {@link SurfaceView}。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
    throw new IllegalStateException("缺少处理 COMMAND_SET_VIDEO_SURFACE 的实现");
  }

  /**
   * 处理清除视频输出的调用。
   *
   * <p>仅在 {@link Player#COMMAND_SET_VIDEO_SURFACE} 可用时调用。
   *
   * @param videoOutput 要清除的视频输出。如果为 null，则应清除任何当前输出。如果为非 null，则仅在输出与提供的参数匹配时才清除输出。这是一个 {@link Surface}、{@link SurfaceHolder}、{@link TextureView} 或 {@link SurfaceView}。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleClearVideoOutput(@Nullable Object videoOutput) {
    throw new IllegalStateException("缺少处理 COMMAND_SET_VIDEO_SURFACE 的实现");
  }

  /**
   * 处理对 {@link Player#setMediaItem} 和 {@link Player#setMediaItems} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_SET_MEDIA_ITEM} 或 {@link Player#COMMAND_CHANGE_MEDIA_ITEMS} 可用时调用。如果仅 {@link Player#COMMAND_SET_MEDIA_ITEM} 可用，则媒体项列表将始终包含一个项目。
   *
   * @param mediaItems 要添加的媒体项。
   * @param startIndex 开始播放的索引，或 {@link C#INDEX_UNSET} 以从默认项目开始。
   * @param startPositionMs 开始播放的位置（以毫秒为单位），或 {@link C#TIME_UNSET} 以从媒体项中的默认位置开始。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSetMediaItems(
      List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    throw new IllegalStateException("缺少处理 COMMAND_SET_MEDIA_ITEM(S) 的实现");
  }

  /**
   * 处理对 {@link Player#addMediaItem} 和 {@link Player#addMediaItems} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_CHANGE_MEDIA_ITEMS} 可用时调用。
   *
   * @param index 添加项目的索引。索引范围为 0 &lt;= {@code index} &lt;= {@link #getMediaItemCount()}。
   * @param mediaItems 要添加的媒体项。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleAddMediaItems(int index, List<MediaItem> mediaItems) {
    throw new IllegalStateException("缺少处理 COMMAND_CHANGE_MEDIA_ITEMS 的实现");
  }

  /**
   * 处理对 {@link Player#moveMediaItem} 和 {@link Player#moveMediaItems} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_CHANGE_MEDIA_ITEMS} 可用时调用。
   *
   * @param fromIndex 要移动的项目的起始索引。索引范围为 0 &lt;= {@code fromIndex} &lt; {@link #getMediaItemCount()}。
   * @param toIndex 不包括在移动中的第一个项目的索引（独占）。索引范围为 {@code fromIndex} &lt; {@code toIndex} &lt;= {@link #getMediaItemCount()}。
   * @param newIndex 第一个移动项目的新索引。索引范围为 {@code 0} &lt;= {@code newIndex} &lt; {@link #getMediaItemCount() - (toIndex - fromIndex)}。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleMoveMediaItems(int fromIndex, int toIndex, int newIndex) {
    throw new IllegalStateException("缺少处理 COMMAND_CHANGE_MEDIA_ITEMS 的实现");
  }

  /**
   * 处理对 {@link Player#replaceMediaItem} 和 {@link Player#replaceMediaItems} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_CHANGE_MEDIA_ITEMS} 可用时调用。
   *
   * @param fromIndex 要替换的项目的起始索引。索引范围为 0 &lt;= {@code fromIndex} &lt; {@link #getMediaItemCount()}。
   * @param toIndex 不包括在替换中的第一个项目的索引（独占）。索引范围为 {@code fromIndex} &lt; {@code toIndex} &lt;= {@link #getMediaItemCount()}。
   * @param mediaItems 用于替换指定范围的媒体项。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleReplaceMediaItems(
      int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    ListenableFuture<?> addFuture = handleAddMediaItems(toIndex, mediaItems);
    ListenableFuture<?> removeFuture = handleRemoveMediaItems(fromIndex, toIndex);
    return Util.transformFutureAsync(addFuture, unused -> removeFuture);
  }

  /**
   * 处理对 {@link Player#removeMediaItem} 和 {@link Player#removeMediaItems} 的调用。
   *
   * <p>仅在 {@link Player#COMMAND_CHANGE_MEDIA_ITEMS} 可用时调用。
   *
   * @param fromIndex 开始删除媒体项的索引。索引范围为 0 &lt;= {@code fromIndex} &lt; {@link #getMediaItemCount()}。
   * @param toIndex 保留的第一个项目的索引（独占）。索引范围为 {@code fromIndex} &lt; {@code toIndex} &lt;= {@link #getMediaItemCount()}。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleRemoveMediaItems(int fromIndex, int toIndex) {
    throw new IllegalStateException("缺少处理 COMMAND_CHANGE_MEDIA_ITEMS 的实现");
  }

  /**
   * 处理对 {@link Player#seekTo} 和其他跳转操作（例如 {@link Player#seekToNext}）的调用。
   *
   * <p>仅在适当的 {@link Player.Command}（例如 {@link Player#COMMAND_SEEK_TO_MEDIA_ITEM} 或 {@link Player#COMMAND_SEEK_TO_NEXT}）可用时调用。
   *
   * @param mediaItemIndex 要跳转到的媒体项索引。如果原始跳转操作未直接指定索引，则这是基于可用播放器状态的最可能隐含索引。如果隐含的操作是不执行任何操作，则此值为 {@link C#INDEX_UNSET}。
   * @param positionMs 开始播放的位置（以毫秒为单位），或 {@link C#TIME_UNSET} 以从媒体项中的默认位置开始。如果原始跳转操作未直接指定位置，则这是基于可用播放器状态的最可能隐含位置。
   * @param seekCommand 用于触发跳转的 {@link Player.Command}。
   * @return 一个 {@link ListenableFuture}，指示此调用引起的所有即时 {@link State} 更改的完成。
   */
  @ForOverride
  protected ListenableFuture<?> handleSeek(
      int mediaItemIndex, long positionMs, @Player.Command int seekCommand) {
    throw new IllegalStateException("缺少处理 COMMAND_SEEK_* 的实现");
  }

  /**
   * 如果调用此方法的线程与构造此实例时指定的 {@link Looper} 线程不匹配，则抛出 {@link IllegalStateException}。
   *
   * <p>子类可以使用此方法来验证其定义的方法是否也由正确的线程访问。
   */
  protected final void verifyApplicationThread() {
    if (Thread.currentThread() != applicationLooper.getThread()) {
      String message =
          Util.formatInvariant(
              "播放器在错误的线程上被访问。\n"
                  + "当前线程：'%s'\n"
                  + "预期线程：'%s'\n",
              Thread.currentThread().getName(), applicationLooper.getThread().getName());
      throw new IllegalStateException(message);
    }
  }

  @RequiresNonNull("state")
  private boolean shouldHandleCommand(@Player.Command int commandCode) {
    return !released && state.availableCommands.contains(commandCode);
  }

  @SuppressWarnings("deprecation") // Calling deprecated listener methods.
  @RequiresNonNull("state")
  private void updateStateAndInformListeners(
      State newState, boolean forceSeekDiscontinuity, boolean isRepeatingCurrentItem) {
    State previousState = state;
    // Assign new state immediately such that all getters return the right values, but use a
    // snapshot of the previous and new state so that listener invocations are triggered correctly.
    this.state = newState;
    if (newState.hasPositionDiscontinuity || newState.newlyRenderedFirstFrame) {
      // Clear one-time events to avoid signalling them again later.
      this.state =
          this.state
              .buildUpon()
              .clearPositionDiscontinuity()
              .setNewlyRenderedFirstFrame(false)
              .build();
    }

    boolean playWhenReadyChanged = previousState.playWhenReady != newState.playWhenReady;
    boolean playbackStateChanged = previousState.playbackState != newState.playbackState;
    int positionDiscontinuityReason =
        getPositionDiscontinuityReason(
            previousState, newState, forceSeekDiscontinuity, window, period);
    boolean timelineChanged = !previousState.timeline.equals(newState.timeline);
    int mediaItemTransitionReason =
        getMediaItemTransitionReason(
            previousState, newState, positionDiscontinuityReason, isRepeatingCurrentItem, window);

    if (timelineChanged) {
      @Player.TimelineChangeReason
      int timelineChangeReason =
          getTimelineChangeReason(previousState.timeline, newState.timeline, window);
      listeners.queueEvent(
          Player.EVENT_TIMELINE_CHANGED,
          listener -> listener.onTimelineChanged(newState.timeline, timelineChangeReason));
    }
    if (positionDiscontinuityReason != C.INDEX_UNSET) {
      PositionInfo previousPositionInfo =
          getPositionInfo(previousState, /* useDiscontinuityPosition= */ false, window, period);
      PositionInfo positionInfo =
          getPositionInfo(
              newState,
              /* useDiscontinuityPosition= */ newState.hasPositionDiscontinuity,
              window,
              period);
      listeners.queueEvent(
          Player.EVENT_POSITION_DISCONTINUITY,
          listener -> {
            listener.onPositionDiscontinuity(positionDiscontinuityReason);
            listener.onPositionDiscontinuity(
                previousPositionInfo, positionInfo, positionDiscontinuityReason);
          });
    }
    if (mediaItemTransitionReason != C.INDEX_UNSET) {
      @Nullable
      MediaItem mediaItem =
          newState.timeline.isEmpty()
              ? null
              : newState.timeline.getWindow(getCurrentMediaItemIndexInternal(newState), window)
                  .mediaItem;
      listeners.queueEvent(
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          listener -> listener.onMediaItemTransition(mediaItem, mediaItemTransitionReason));
    }
    if (!Util.areEqual(previousState.playerError, newState.playerError)) {
      listeners.queueEvent(
          Player.EVENT_PLAYER_ERROR,
          listener -> listener.onPlayerErrorChanged(newState.playerError));
      if (newState.playerError != null) {
        listeners.queueEvent(
            Player.EVENT_PLAYER_ERROR,
            listener -> listener.onPlayerError(castNonNull(newState.playerError)));
      }
    }
    if (!previousState.trackSelectionParameters.equals(newState.trackSelectionParameters)) {
      listeners.queueEvent(
          Player.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED,
          listener ->
              listener.onTrackSelectionParametersChanged(newState.trackSelectionParameters));
    }
    if (!previousState.currentTracks.equals(newState.currentTracks)) {
      listeners.queueEvent(
          Player.EVENT_TRACKS_CHANGED,
          listener -> listener.onTracksChanged(newState.currentTracks));
    }
    if (!previousState.currentMetadata.equals(newState.currentMetadata)) {
      listeners.queueEvent(
          EVENT_MEDIA_METADATA_CHANGED,
          listener -> listener.onMediaMetadataChanged(newState.currentMetadata));
    }
    if (previousState.isLoading != newState.isLoading) {
      listeners.queueEvent(
          Player.EVENT_IS_LOADING_CHANGED,
          listener -> {
            listener.onLoadingChanged(newState.isLoading);
            listener.onIsLoadingChanged(newState.isLoading);
          });
    }
    if (playWhenReadyChanged || playbackStateChanged) {
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener ->
              listener.onPlayerStateChanged(newState.playWhenReady, newState.playbackState));
    }
    if (playbackStateChanged) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          listener -> listener.onPlaybackStateChanged(newState.playbackState));
    }
    if (playWhenReadyChanged
        || previousState.playWhenReadyChangeReason != newState.playWhenReadyChangeReason) {
      listeners.queueEvent(
          Player.EVENT_PLAY_WHEN_READY_CHANGED,
          listener ->
              listener.onPlayWhenReadyChanged(
                  newState.playWhenReady, newState.playWhenReadyChangeReason));
    }
    if (previousState.playbackSuppressionReason != newState.playbackSuppressionReason) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
          listener ->
              listener.onPlaybackSuppressionReasonChanged(newState.playbackSuppressionReason));
    }
    if (isPlaying(previousState) != isPlaying(newState)) {
      listeners.queueEvent(
          Player.EVENT_IS_PLAYING_CHANGED,
          listener -> listener.onIsPlayingChanged(isPlaying(newState)));
    }
    if (!previousState.playbackParameters.equals(newState.playbackParameters)) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
          listener -> listener.onPlaybackParametersChanged(newState.playbackParameters));
    }
    if (previousState.repeatMode != newState.repeatMode) {
      listeners.queueEvent(
          Player.EVENT_REPEAT_MODE_CHANGED,
          listener -> listener.onRepeatModeChanged(newState.repeatMode));
    }
    if (previousState.shuffleModeEnabled != newState.shuffleModeEnabled) {
      listeners.queueEvent(
          Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
          listener -> listener.onShuffleModeEnabledChanged(newState.shuffleModeEnabled));
    }
    if (previousState.seekBackIncrementMs != newState.seekBackIncrementMs) {
      listeners.queueEvent(
          Player.EVENT_SEEK_BACK_INCREMENT_CHANGED,
          listener -> listener.onSeekBackIncrementChanged(newState.seekBackIncrementMs));
    }
    if (previousState.seekForwardIncrementMs != newState.seekForwardIncrementMs) {
      listeners.queueEvent(
          Player.EVENT_SEEK_FORWARD_INCREMENT_CHANGED,
          listener -> listener.onSeekForwardIncrementChanged(newState.seekForwardIncrementMs));
    }
    if (previousState.maxSeekToPreviousPositionMs != newState.maxSeekToPreviousPositionMs) {
      listeners.queueEvent(
          Player.EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED,
          listener ->
              listener.onMaxSeekToPreviousPositionChanged(newState.maxSeekToPreviousPositionMs));
    }
    if (!previousState.audioAttributes.equals(newState.audioAttributes)) {
      listeners.queueEvent(
          Player.EVENT_AUDIO_ATTRIBUTES_CHANGED,
          listener -> listener.onAudioAttributesChanged(newState.audioAttributes));
    }
    if (!previousState.videoSize.equals(newState.videoSize)) {
      listeners.queueEvent(
          Player.EVENT_VIDEO_SIZE_CHANGED,
          listener -> listener.onVideoSizeChanged(newState.videoSize));
    }
    if (!previousState.deviceInfo.equals(newState.deviceInfo)) {
      listeners.queueEvent(
          Player.EVENT_DEVICE_INFO_CHANGED,
          listener -> listener.onDeviceInfoChanged(newState.deviceInfo));
    }
    if (!previousState.playlistMetadata.equals(newState.playlistMetadata)) {
      listeners.queueEvent(
          Player.EVENT_PLAYLIST_METADATA_CHANGED,
          listener -> listener.onPlaylistMetadataChanged(newState.playlistMetadata));
    }
    if (newState.newlyRenderedFirstFrame) {
      listeners.queueEvent(Player.EVENT_RENDERED_FIRST_FRAME, Listener::onRenderedFirstFrame);
    }
    if (!previousState.surfaceSize.equals(newState.surfaceSize)) {
      listeners.queueEvent(
          Player.EVENT_SURFACE_SIZE_CHANGED,
          listener ->
              listener.onSurfaceSizeChanged(
                  newState.surfaceSize.getWidth(), newState.surfaceSize.getHeight()));
    }
    if (previousState.volume != newState.volume) {
      listeners.queueEvent(
          Player.EVENT_VOLUME_CHANGED, listener -> listener.onVolumeChanged(newState.volume));
    }
    if (previousState.deviceVolume != newState.deviceVolume
        || previousState.isDeviceMuted != newState.isDeviceMuted) {
      listeners.queueEvent(
          Player.EVENT_DEVICE_VOLUME_CHANGED,
          listener ->
              listener.onDeviceVolumeChanged(newState.deviceVolume, newState.isDeviceMuted));
    }
    if (!previousState.currentCues.equals(newState.currentCues)) {
      listeners.queueEvent(
          Player.EVENT_CUES,
          listener -> {
            listener.onCues(newState.currentCues.cues);
            listener.onCues(newState.currentCues);
          });
    }
    if (!previousState.timedMetadata.equals(newState.timedMetadata)
        && newState.timedMetadata.presentationTimeUs != C.TIME_UNSET) {
      listeners.queueEvent(
          Player.EVENT_METADATA, listener -> listener.onMetadata(newState.timedMetadata));
    }
    if (!previousState.availableCommands.equals(newState.availableCommands)) {
      listeners.queueEvent(
          Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
          listener -> listener.onAvailableCommandsChanged(newState.availableCommands));
    }
    listeners.flushEvents();
  }

  @EnsuresNonNull("state")
  private void verifyApplicationThreadAndInitState() {
    verifyApplicationThread();
    if (state == null) {
      // First time accessing state.
      state = getState();
    }
  }

  @RequiresNonNull("state")
  private void updateStateForPendingOperation(
      ListenableFuture<?> pendingOperation, Supplier<State> placeholderStateSupplier) {
    updateStateForPendingOperation(
        pendingOperation,
        placeholderStateSupplier,
        /* forceSeekDiscontinuity= */ false,
        /* isRepeatingCurrentItem= */ false);
  }

  @RequiresNonNull("state")
  private void updateStateForPendingOperation(
      ListenableFuture<?> pendingOperation,
      Supplier<State> placeholderStateSupplier,
      boolean forceSeekDiscontinuity,
      boolean isRepeatingCurrentItem) {
    if (pendingOperation.isDone() && pendingOperations.isEmpty()) {
      updateStateAndInformListeners(getState(), forceSeekDiscontinuity, isRepeatingCurrentItem);
    } else {
      pendingOperations.add(pendingOperation);
      State suggestedPlaceholderState = placeholderStateSupplier.get();
      updateStateAndInformListeners(
          getPlaceholderState(suggestedPlaceholderState),
          forceSeekDiscontinuity,
          isRepeatingCurrentItem);
      pendingOperation.addListener(
          () -> {
            castNonNull(state); // Already checked by method @RequiresNonNull pre-condition.
            pendingOperations.remove(pendingOperation);
            if (pendingOperations.isEmpty() && !released) {
              updateStateAndInformListeners(
                  getState(),
                  /* forceSeekDiscontinuity= */ false,
                  /* isRepeatingCurrentItem= */ false);
            }
          },
          this::postOrRunOnApplicationHandler);
    }
  }

  private void postOrRunOnApplicationHandler(Runnable runnable) {
    if (applicationHandler.getLooper() == Looper.myLooper()) {
      runnable.run();
    } else {
      applicationHandler.post(runnable);
    }
  }

  private static boolean isPlaying(State state) {
    return state.playWhenReady
        && state.playbackState == Player.STATE_READY
        && state.playbackSuppressionReason == PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  private static int getCurrentMediaItemIndexInternal(State state) {
    if (state.currentMediaItemIndex != C.INDEX_UNSET) {
      return state.currentMediaItemIndex;
    }
    return 0; // TODO: Use shuffle order to get first item if playlist is not empty.
  }

  private static long getContentPositionMsInternal(State state, Timeline.Window window) {
    return getPositionOrDefaultInMediaItem(state.contentPositionMsSupplier.get(), state, window);
  }

  private static long getContentBufferedPositionMsInternal(State state, Timeline.Window window) {
    return getPositionOrDefaultInMediaItem(
        state.contentBufferedPositionMsSupplier.get(), state, window);
  }

  private static long getPositionOrDefaultInMediaItem(
      long positionMs, State state, Timeline.Window window) {
    if (positionMs != C.TIME_UNSET) {
      return positionMs;
    }
    if (state.timeline.isEmpty()) {
      return 0;
    }
    return state
        .timeline
        .getWindow(getCurrentMediaItemIndexInternal(state), window)
        .getDefaultPositionMs();
  }

  private static int getCurrentPeriodIndexInternal(
      State state, Timeline.Window window, Timeline.Period period) {
    int currentMediaItemIndex = getCurrentMediaItemIndexInternal(state);
    if (state.timeline.isEmpty()) {
      return currentMediaItemIndex;
    }
    return getPeriodIndexFromWindowPosition(
        state.timeline,
        currentMediaItemIndex,
        getContentPositionMsInternal(state, window),
        window,
        period);
  }

  private static int getPeriodIndexFromWindowPosition(
      Timeline timeline,
      int windowIndex,
      long windowPositionMs,
      Timeline.Window window,
      Timeline.Period period) {
    Object periodUid =
        timeline.getPeriodPositionUs(window, period, windowIndex, msToUs(windowPositionMs)).first;
    return timeline.getIndexOfPeriod(periodUid);
  }

  private static @Player.TimelineChangeReason int getTimelineChangeReason(
      Timeline previousTimeline, Timeline newTimeline, Timeline.Window window) {
    if (previousTimeline.getWindowCount() != newTimeline.getWindowCount()) {
      return Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED;
    }
    for (int i = 0; i < previousTimeline.getWindowCount(); i++) {
      Object previousUid = previousTimeline.getWindow(/* windowIndex= */ i, window).uid;
      Object newUid = newTimeline.getWindow(/* windowIndex= */ i, window).uid;
      boolean resolvedAutoGeneratedPlaceholder =
          previousUid instanceof PlaceholderUid && !(newUid instanceof PlaceholderUid);
      if (!previousUid.equals(newUid) && !resolvedAutoGeneratedPlaceholder) {
        return Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED;
      }
    }
    return Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE;
  }

  private static int getPositionDiscontinuityReason(
      State previousState,
      State newState,
      boolean forceSeekDiscontinuity,
      Timeline.Window window,
      Timeline.Period period) {
    if (newState.hasPositionDiscontinuity) {
      // We were asked to report a discontinuity.
      return newState.positionDiscontinuityReason;
    }
    if (forceSeekDiscontinuity) {
      return Player.DISCONTINUITY_REASON_SEEK;
    }
    if (previousState.timeline.isEmpty()) {
      // First change from an empty playlist is not reported as a discontinuity.
      return C.INDEX_UNSET;
    }
    if (newState.timeline.isEmpty()) {
      // The playlist became empty.
      return Player.DISCONTINUITY_REASON_REMOVE;
    }
    Object previousPeriodUid =
        previousState.timeline.getUidOfPeriod(
            getCurrentPeriodIndexInternal(previousState, window, period));
    Object newPeriodUid =
        newState.timeline.getUidOfPeriod(getCurrentPeriodIndexInternal(newState, window, period));
    if (previousPeriodUid instanceof PlaceholderUid && !(newPeriodUid instanceof PlaceholderUid)) {
      // An auto-generated placeholder was resolved to a real item.
      return C.INDEX_UNSET;
    }
    if (!newPeriodUid.equals(previousPeriodUid)
        || previousState.currentAdGroupIndex != newState.currentAdGroupIndex
        || previousState.currentAdIndexInAdGroup != newState.currentAdIndexInAdGroup) {
      // The current period or ad inside a period changed.
      if (newState.timeline.getIndexOfPeriod(previousPeriodUid) == C.INDEX_UNSET) {
        // The previous period no longer exists.
        return Player.DISCONTINUITY_REASON_REMOVE;
      }
      // Check if reached the previous period's or ad's duration to assume an auto-transition.
      long previousPositionMs =
          getCurrentPeriodOrAdPositionMs(previousState, previousPeriodUid, period, window);
      long previousDurationMs = getPeriodOrAdDurationMs(previousState, previousPeriodUid, period);
      return previousDurationMs != C.TIME_UNSET && previousPositionMs >= previousDurationMs
          ? Player.DISCONTINUITY_REASON_AUTO_TRANSITION
          : Player.DISCONTINUITY_REASON_SKIP;
    }
    // We are in the same content period or ad. Check if the position deviates more than a
    // reasonable threshold from the previous one.
    long previousPositionMs =
        getCurrentPeriodOrAdPositionMs(previousState, previousPeriodUid, period, window);
    long newPositionMs = getCurrentPeriodOrAdPositionMs(newState, newPeriodUid, period, window);
    if (Math.abs(previousPositionMs - newPositionMs) < POSITION_DISCONTINUITY_THRESHOLD_MS) {
      return C.INDEX_UNSET;
    }
    // Check if we previously reached the end of the item to assume an auto-repetition.
    long previousDurationMs = getPeriodOrAdDurationMs(previousState, previousPeriodUid, period);
    return previousDurationMs != C.TIME_UNSET && previousPositionMs >= previousDurationMs
        ? Player.DISCONTINUITY_REASON_AUTO_TRANSITION
        : Player.DISCONTINUITY_REASON_INTERNAL;
  }

  private static long getCurrentPeriodOrAdPositionMs(
      State state, Object currentPeriodUid, Timeline.Period period, Timeline.Window window) {
    return state.currentAdGroupIndex != C.INDEX_UNSET
        ? state.adPositionMsSupplier.get()
        : getContentPositionMsInternal(state, window)
            - state.timeline.getPeriodByUid(currentPeriodUid, period).getPositionInWindowMs();
  }

  private static long getPeriodOrAdDurationMs(
      State state, Object currentPeriodUid, Timeline.Period period) {
    state.timeline.getPeriodByUid(currentPeriodUid, period);
    long periodOrAdDurationUs =
        state.currentAdGroupIndex == C.INDEX_UNSET
            ? period.durationUs
            : period.getAdDurationUs(state.currentAdGroupIndex, state.currentAdIndexInAdGroup);
    return usToMs(periodOrAdDurationUs);
  }

  private static PositionInfo getPositionInfo(
      State state,
      boolean useDiscontinuityPosition,
      Timeline.Window window,
      Timeline.Period period) {
    @Nullable Object windowUid = null;
    @Nullable Object periodUid = null;
    int mediaItemIndex = getCurrentMediaItemIndexInternal(state);
    int periodIndex = C.INDEX_UNSET;
    @Nullable MediaItem mediaItem = null;
    if (!state.timeline.isEmpty()) {
      periodIndex = getCurrentPeriodIndexInternal(state, window, period);
      periodUid = state.timeline.getPeriod(periodIndex, period, /* setIds= */ true).uid;
      windowUid = state.timeline.getWindow(mediaItemIndex, window).uid;
      mediaItem = window.mediaItem;
    }
    long contentPositionMs;
    long positionMs;
    if (useDiscontinuityPosition) {
      positionMs = state.discontinuityPositionMs;
      contentPositionMs =
          state.currentAdGroupIndex == C.INDEX_UNSET
              ? positionMs
              : getContentPositionMsInternal(state, window);
    } else {
      contentPositionMs = getContentPositionMsInternal(state, window);
      positionMs =
          state.currentAdGroupIndex != C.INDEX_UNSET
              ? state.adPositionMsSupplier.get()
              : contentPositionMs;
    }
    return new PositionInfo(
        windowUid,
        mediaItemIndex,
        mediaItem,
        periodUid,
        periodIndex,
        positionMs,
        contentPositionMs,
        state.currentAdGroupIndex,
        state.currentAdIndexInAdGroup);
  }

  private static int getMediaItemTransitionReason(
      State previousState,
      State newState,
      int positionDiscontinuityReason,
      boolean isRepeatingCurrentItem,
      Timeline.Window window) {
    Timeline previousTimeline = previousState.timeline;
    Timeline newTimeline = newState.timeline;
    if (newTimeline.isEmpty() && previousTimeline.isEmpty()) {
      return C.INDEX_UNSET;
    } else if (newTimeline.isEmpty() != previousTimeline.isEmpty()) {
      return MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
    }
    Object previousWindowUid =
        previousState.timeline.getWindow(getCurrentMediaItemIndexInternal(previousState), window)
            .uid;
    Object newWindowUid =
        newState.timeline.getWindow(getCurrentMediaItemIndexInternal(newState), window).uid;
    if (previousWindowUid instanceof PlaceholderUid && !(newWindowUid instanceof PlaceholderUid)) {
      // An auto-generated placeholder was resolved to a real item.
      return C.INDEX_UNSET;
    }
    if (!previousWindowUid.equals(newWindowUid)) {
      if (positionDiscontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
        return MEDIA_ITEM_TRANSITION_REASON_AUTO;
      } else if (positionDiscontinuityReason == DISCONTINUITY_REASON_SEEK) {
        return MEDIA_ITEM_TRANSITION_REASON_SEEK;
      } else {
        return MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
      }
    }
    // Only mark changes within the current item as a transition if we are repeating automatically
    // or via a seek to next/previous.
    if (positionDiscontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
      if ((getContentPositionMsInternal(previousState, window)
              > getContentPositionMsInternal(newState, window))
          || (newState.hasPositionDiscontinuity
              && newState.discontinuityPositionMs == C.TIME_UNSET
              && isRepeatingCurrentItem)) {
        return MEDIA_ITEM_TRANSITION_REASON_REPEAT;
      }
    }
    if (positionDiscontinuityReason == DISCONTINUITY_REASON_SEEK && isRepeatingCurrentItem) {
      return MEDIA_ITEM_TRANSITION_REASON_SEEK;
    }
    return C.INDEX_UNSET;
  }

  private static Size getSurfaceHolderSize(SurfaceHolder surfaceHolder) {
    if (!surfaceHolder.getSurface().isValid()) {
      return Size.ZERO;
    }
    Rect surfaceFrame = surfaceHolder.getSurfaceFrame();
    return new Size(surfaceFrame.width(), surfaceFrame.height());
  }

  private static int getMediaItemIndexInNewPlaylist(
      Timeline oldTimeline,
      Timeline newTimeline,
      int oldMediaItemIndex,
      Timeline.Period period,
      Timeline.Window window) {
    if (oldTimeline.isEmpty()) {
      return oldMediaItemIndex < newTimeline.getWindowCount() ? oldMediaItemIndex : C.INDEX_UNSET;
    }
    int oldFirstPeriodIndex = oldTimeline.getWindow(oldMediaItemIndex, window).firstPeriodIndex;
    Object oldFirstPeriodUid =
        checkNotNull(oldTimeline.getPeriod(oldFirstPeriodIndex, period, /* setIds= */ true).uid);
    if (newTimeline.getIndexOfPeriod(oldFirstPeriodUid) == C.INDEX_UNSET) {
      return C.INDEX_UNSET;
    }
    return newTimeline.getPeriodByUid(oldFirstPeriodUid, period).windowIndex;
  }

  private static State getStateWithNewPlaylist(
      State oldState,
      List<MediaItemData> newPlaylist,
      Timeline.Period period,
      Timeline.Window window) {
    State.Builder stateBuilder = oldState.buildUpon();
    Timeline newTimeline = new PlaylistTimeline(newPlaylist);
    Timeline oldTimeline = oldState.timeline;
    long oldPositionMs = oldState.contentPositionMsSupplier.get();
    int oldIndex = getCurrentMediaItemIndexInternal(oldState);
    int newIndex =
        getMediaItemIndexInNewPlaylist(oldTimeline, newTimeline, oldIndex, period, window);
    long newPositionMs = newIndex == C.INDEX_UNSET ? C.TIME_UNSET : oldPositionMs;
    // If the current item no longer exists, try to find a matching subsequent item.
    for (int i = oldIndex + 1; newIndex == C.INDEX_UNSET && i < oldTimeline.getWindowCount(); i++) {
      // TODO: Use shuffle order to iterate.
      newIndex =
          getMediaItemIndexInNewPlaylist(
              oldTimeline, newTimeline, /* oldMediaItemIndex= */ i, period, window);
    }
    // If this fails, transition to ENDED state.
    if (oldState.playbackState != Player.STATE_IDLE && newIndex == C.INDEX_UNSET) {
      stateBuilder.setPlaybackState(Player.STATE_ENDED).setIsLoading(false);
    }
    return buildStateForNewPosition(
        stateBuilder,
        oldState,
        oldPositionMs,
        newTimeline,
        newIndex,
        newPositionMs,
        /* keepAds= */ true,
        window);
  }

  private static State getStateWithNewPlaylistAndPosition(
      State oldState,
      @Nullable List<MediaItemData> newPlaylist,
      int newIndex,
      long newPositionMs,
      Timeline.Window window) {
    State.Builder stateBuilder = oldState.buildUpon();
    Timeline newTimeline =
        newPlaylist == null ? oldState.timeline : new PlaylistTimeline(newPlaylist);
    if (oldState.playbackState != Player.STATE_IDLE) {
      if (newTimeline.isEmpty()
          || (newIndex != C.INDEX_UNSET && newIndex >= newTimeline.getWindowCount())) {
        stateBuilder.setPlaybackState(Player.STATE_ENDED).setIsLoading(false);
      } else {
        stateBuilder.setPlaybackState(Player.STATE_BUFFERING);
      }
    }
    long oldPositionMs = oldState.contentPositionMsSupplier.get();
    return buildStateForNewPosition(
        stateBuilder,
        oldState,
        oldPositionMs,
        newTimeline,
        newIndex,
        newPositionMs,
        /* keepAds= */ false,
        window);
  }

  private static State buildStateForNewPosition(
      State.Builder stateBuilder,
      State oldState,
      long oldPositionMs,
      Timeline newTimeline,
      int newIndex,
      long newPositionMs,
      boolean keepAds,
      Timeline.Window window) {
    // Resolve unset or invalid index and position.
    oldPositionMs = getPositionOrDefaultInMediaItem(oldPositionMs, oldState, window);
    if (!newTimeline.isEmpty()
        && (newIndex == C.INDEX_UNSET || newIndex >= newTimeline.getWindowCount())) {
      newIndex = 0; // TODO: Use shuffle order to get first index.
      newPositionMs = C.TIME_UNSET;
    }
    if (!newTimeline.isEmpty() && newPositionMs == C.TIME_UNSET) {
      newPositionMs = newTimeline.getWindow(newIndex, window).getDefaultPositionMs();
    }
    boolean oldOrNewPlaylistEmpty = oldState.timeline.isEmpty() || newTimeline.isEmpty();
    boolean mediaItemChanged =
        !oldOrNewPlaylistEmpty
            && !oldState
                .timeline
                .getWindow(getCurrentMediaItemIndexInternal(oldState), window)
                .uid
                .equals(newTimeline.getWindow(newIndex, window).uid);
    // Set timeline, resolving tracks and metadata to the new index.
    if (newTimeline.isEmpty()) {
      stateBuilder.setPlaylist(newTimeline, Tracks.EMPTY, /* currentMetadata= */ null);
    } else if (newTimeline instanceof PlaylistTimeline) {
      MediaItemData mediaItemData = ((PlaylistTimeline) newTimeline).playlist.get(newIndex);
      stateBuilder.setPlaylist(newTimeline, mediaItemData.tracks, mediaItemData.mediaMetadata);
    } else {
      boolean keepTracksAndMetadata = !oldOrNewPlaylistEmpty && !mediaItemChanged;
      stateBuilder.setPlaylist(
          newTimeline,
          keepTracksAndMetadata ? oldState.currentTracks : Tracks.EMPTY,
          keepTracksAndMetadata ? oldState.currentMetadata : null);
    }
    if (oldOrNewPlaylistEmpty || mediaItemChanged || newPositionMs < oldPositionMs) {
      // New item or seeking back. Assume no buffer and no ad playback persists.
      stateBuilder
          .setCurrentMediaItemIndex(newIndex)
          .setCurrentAd(C.INDEX_UNSET, C.INDEX_UNSET)
          .setContentPositionMs(newPositionMs)
          .setContentBufferedPositionMs(PositionSupplier.getConstant(newPositionMs))
          .setTotalBufferedDurationMs(PositionSupplier.ZERO);
    } else if (newPositionMs == oldPositionMs) {
      // Unchanged position. Assume ad playback and buffer in current item persists.
      stateBuilder.setCurrentMediaItemIndex(newIndex);
      if (oldState.currentAdGroupIndex != C.INDEX_UNSET && keepAds) {
        stateBuilder.setTotalBufferedDurationMs(
            PositionSupplier.getConstant(
                oldState.adBufferedPositionMsSupplier.get() - oldState.adPositionMsSupplier.get()));
      } else {
        stateBuilder
            .setCurrentAd(C.INDEX_UNSET, C.INDEX_UNSET)
            .setTotalBufferedDurationMs(
                PositionSupplier.getConstant(
                    getContentBufferedPositionMsInternal(oldState, window) - oldPositionMs));
      }
    } else {
      // Seeking forward. Assume remaining buffer in current item persist, but no ad playback.
      long contentBufferedDurationMs =
          max(getContentBufferedPositionMsInternal(oldState, window), newPositionMs);
      long totalBufferedDurationMs =
          max(0, oldState.totalBufferedDurationMsSupplier.get() - (newPositionMs - oldPositionMs));
      stateBuilder
          .setCurrentMediaItemIndex(newIndex)
          .setCurrentAd(C.INDEX_UNSET, C.INDEX_UNSET)
          .setContentPositionMs(newPositionMs)
          .setContentBufferedPositionMs(PositionSupplier.getConstant(contentBufferedDurationMs))
          .setTotalBufferedDurationMs(PositionSupplier.getConstant(totalBufferedDurationMs));
    }
    return stateBuilder.build();
  }

  private static MediaMetadata getCombinedMediaMetadata(MediaItem mediaItem, Tracks tracks) {
    MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
    int trackGroupCount = tracks.getGroups().size();
    for (int i = 0; i < trackGroupCount; i++) {
      Tracks.Group group = tracks.getGroups().get(i);
      for (int j = 0; j < group.length; j++) {
        if (group.isTrackSelected(j)) {
          Format format = group.getTrackFormat(j);
          if (format.metadata != null) {
            for (int k = 0; k < format.metadata.length(); k++) {
              format.metadata.get(k).populateMediaMetadata(metadataBuilder);
            }
          }
        }
      }
    }
    return metadataBuilder.populate(mediaItem.mediaMetadata).build();
  }

  private static List<MediaItemData> buildMutablePlaylistFromState(
      State state, Timeline.Period period, Timeline.Window window) {
    if (state.timeline instanceof PlaylistTimeline) {
      return new ArrayList<>(((PlaylistTimeline) state.timeline).playlist);
    }
    ArrayList<MediaItemData> items = new ArrayList<>(state.timeline.getWindowCount());
    for (int i = 0; i < state.timeline.getWindowCount(); i++) {
      items.add(MediaItemData.buildFromState(state, /* mediaItemIndex= */ i, period, window));
    }
    return items;
  }

  private static final class PlaceholderUid {}
}
