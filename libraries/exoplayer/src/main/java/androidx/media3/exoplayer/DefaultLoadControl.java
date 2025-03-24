package androidx.media3.exoplayer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.InlineMe;
import java.util.HashMap;

/** 默认的 {@link LoadControl} 实现。 */
@UnstableApi
public class DefaultLoadControl implements LoadControl {

  /**
   * 默认情况下，播放器将尝试始终缓冲的最小媒体时长，单位为毫秒。
   */
  public static final int DEFAULT_MIN_BUFFER_MS = 50_000;

  /**
   * 默认情况下，播放器将尝试缓冲的最大媒体时长，单位为毫秒。
   */
  public static final int DEFAULT_MAX_BUFFER_MS = 50_000;

  /**
   * 默认情况下，在用户操作（如跳转）后，播放开始或恢复所需缓冲的媒体时长，单位为毫秒。
   */
  public static final int DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2500;

  /**
   * 默认情况下，在重新缓冲后，播放恢复所需缓冲的媒体时长，单位为毫秒。重新缓冲定义为由缓冲区耗尽引起，而非用户操作。
   */
  public static final int DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5000;

  /**
   * 默认的目标缓冲区大小，单位为字节。值为 {@link C#LENGTH_UNSET} 时，表示加载控制将根据选定的轨道计算目标缓冲区大小。
   */
  public static final int DEFAULT_TARGET_BUFFER_BYTES = C.LENGTH_UNSET;

  /** 默认情况下，是否优先考虑缓冲区时间限制而非大小限制。 */
  public static final boolean DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS = false;

  /** 默认的后备缓冲区时长，单位为毫秒。 */
  public static final int DEFAULT_BACK_BUFFER_DURATION_MS = 0;

  /** 默认情况下，是否从上一关键帧保留后备缓冲区。 */
  public static final boolean DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME = false;

  /** 视频缓冲区的默认大小，单位为字节。 */
  public static final int DEFAULT_VIDEO_BUFFER_SIZE = 2000 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** 音频缓冲区的默认大小，单位为字节。 */
  public static final int DEFAULT_AUDIO_BUFFER_SIZE = 200 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** 文本缓冲区的默认大小，单位为字节。 */
  public static final int DEFAULT_TEXT_BUFFER_SIZE = 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** 元数据缓冲区的默认大小，单位为字节。 */
  public static final int DEFAULT_METADATA_BUFFER_SIZE = 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** 相机运动缓冲区的默认大小，单位为字节。 */
  public static final int DEFAULT_CAMERA_MOTION_BUFFER_SIZE = 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** 图像缓冲区的默认大小，单位为字节。 */
  public static final int DEFAULT_IMAGE_BUFFER_SIZE = 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** 多路复用缓冲区（例如包含视频、音频和文本）的默认大小，单位为字节。 */
  public static final int DEFAULT_MUXED_BUFFER_SIZE =
      DEFAULT_VIDEO_BUFFER_SIZE + DEFAULT_AUDIO_BUFFER_SIZE + DEFAULT_TEXT_BUFFER_SIZE;

  /**
   * 在所有情况下使用的最小目标缓冲区大小，单位为字节。这也是在选定轨道之前的默认目标缓冲区大小。
   */
  public static final int DEFAULT_MIN_BUFFER_SIZE = 200 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** {@link DefaultLoadControl} 的构建器。 */
  public static final class Builder {

    @Nullable private DefaultAllocator allocator;
    private int minBufferMs;
    private int maxBufferMs;
    private int bufferForPlaybackMs;
    private int bufferForPlaybackAfterRebufferMs;
    private int targetBufferBytes;
    private boolean prioritizeTimeOverSizeThresholds;
    private int backBufferDurationMs;
    private boolean retainBackBufferFromKeyframe;
    private boolean buildCalled;

    /** 构造一个新的实例。 */
    public Builder() {
      minBufferMs = DEFAULT_MIN_BUFFER_MS;
      maxBufferMs = DEFAULT_MAX_BUFFER_MS;
      bufferForPlaybackMs = DEFAULT_BUFFER_FOR_PLAYBACK_MS;
      bufferForPlaybackAfterRebufferMs = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
      targetBufferBytes = DEFAULT_TARGET_BUFFER_BYTES;
      prioritizeTimeOverSizeThresholds = DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS;
      backBufferDurationMs = DEFAULT_BACK_BUFFER_DURATION_MS;
      retainBackBufferFromKeyframe = DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME;
    }

    /**
     * 设置加载器使用的 {@link DefaultAllocator}。
     *
     * @param allocator {@link DefaultAllocator}。
     * @return 此构建器，方便链式调用。
     * @throws IllegalStateException 如果已调用 {@link #build()}。
     */
    @CanIgnoreReturnValue
    public Builder setAllocator(DefaultAllocator allocator) {
      checkState(!buildCalled);
      this.allocator = allocator;
      return this;
    }

    /**
     * 设置缓冲区时长参数。
     *
     * @param minBufferMs 播放器将尝试始终缓冲的最小媒体时长，单位为毫秒。
     * @param maxBufferMs 播放器将尝试缓冲的最大媒体时长，单位为毫秒。
     * @param bufferForPlaybackMs 在用户操作（如跳转）后，播放开始或恢复所需缓冲的媒体时长，单位为毫秒。
     * @param bufferForPlaybackAfterRebufferMs 在重新缓冲后，播放恢复所需缓冲的媒体时长，单位为毫秒。重新缓冲定义为由缓冲区耗尽引起，而非用户操作。
     * @return 此构建器，方便链式调用。
     * @throws IllegalStateException 如果已调用 {@link #build()}。
     */
    @CanIgnoreReturnValue
    public Builder setBufferDurationsMs(
        int minBufferMs,
        int maxBufferMs,
        int bufferForPlaybackMs,
        int bufferForPlaybackAfterRebufferMs) {
      checkState(!buildCalled);
      assertGreaterOrEqual(bufferForPlaybackMs, 0, "bufferForPlaybackMs", "0");
      assertGreaterOrEqual(
          bufferForPlaybackAfterRebufferMs, 0, "bufferForPlaybackAfterRebufferMs", "0");
      assertGreaterOrEqual(minBufferMs, bufferForPlaybackMs, "minBufferMs", "bufferForPlaybackMs");
      assertGreaterOrEqual(
          minBufferMs,
          bufferForPlaybackAfterRebufferMs,
          "minBufferMs",
          "bufferForPlaybackAfterRebufferMs");
      assertGreaterOrEqual(maxBufferMs, minBufferMs, "maxBufferMs", "minBufferMs");
      this.minBufferMs = minBufferMs;
      this.maxBufferMs = maxBufferMs;
      this.bufferForPlaybackMs = bufferForPlaybackMs;
      this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs;
      return this;
    }

    /**
     * 设置每个播放器的目标缓冲区大小，单位为字节。实际的总目标缓冲区大小为此值乘以同时使用加载控制的播放器数量。
     * 如果设置为 {@link C#LENGTH_UNSET}，播放器的目标缓冲区大小将根据其选定的轨道计算。
     *
     * @param targetBufferBytes 目标缓冲区大小，单位为字节。
     * @return 此构建器，方便链式调用。
     * @throws IllegalStateException 如果已调用 {@link #build()}。
     */
    @CanIgnoreReturnValue
    public Builder setTargetBufferBytes(int targetBufferBytes) {
      checkState(!buildCalled);
      this.targetBufferBytes = targetBufferBytes;
      return this;
    }

    /**
     * 设置加载控制是否优先考虑缓冲区时间限制而非大小限制。
     *
     * @param prioritizeTimeOverSizeThresholds 是否优先考虑缓冲区时间限制而非大小限制。
     * @return 此构建器，方便链式调用。
     * @throws IllegalStateException 如果已调用 {@link #build()}。
     */
    @CanIgnoreReturnValue
    public Builder setPrioritizeTimeOverSizeThresholds(boolean prioritizeTimeOverSizeThresholds) {
      checkState(!buildCalled);
      this.prioritizeTimeOverSizeThresholds = prioritizeTimeOverSizeThresholds;
      return this;
    }

    /**
     * 设置后备缓冲区时长，以及是否从上一关键帧保留后备缓冲区。
     *
     * @param backBufferDurationMs 后备缓冲区时长，单位为毫秒。
     * @param retainBackBufferFromKeyframe 是否从上一关键帧保留后备缓冲区。
     * @return 此构建器，方便链式调用。
     * @throws IllegalStateException 如果已调用 {@link #build()}。
     */
    @CanIgnoreReturnValue
    public Builder setBackBuffer(int backBufferDurationMs, boolean retainBackBufferFromKeyframe) {
      checkState(!buildCalled);
      assertGreaterOrEqual(backBufferDurationMs, 0, "backBufferDurationMs", "0");
      this.backBufferDurationMs = backBufferDurationMs;
      this.retainBackBufferFromKeyframe = retainBackBufferFromKeyframe;
      return this;
    }

    /** 创建 {@link DefaultLoadControl}。 */
    public DefaultLoadControl build() {
      checkState(!buildCalled);
      buildCalled = true;
      if (allocator == null) {
        allocator = new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
      }
      return new DefaultLoadControl(
          allocator,
          minBufferMs,
          maxBufferMs,
          bufferForPlaybackMs,
          bufferForPlaybackAfterRebufferMs,
          targetBufferBytes,
          prioritizeTimeOverSizeThresholds,
          backBufferDurationMs,
          retainBackBufferFromKeyframe);
    }
  }

  private final DefaultAllocator allocator;

  private final long minBufferUs;
  private final long maxBufferUs;
  private final long bufferForPlaybackUs;
  private final long bufferForPlaybackAfterRebufferUs;
  private final int targetBufferBytesOverwrite;
  private final boolean prioritizeTimeOverSizeThresholds;
  private final long backBufferDurationUs;
  private final boolean retainBackBufferFromKeyframe;
  private final HashMap<PlayerId, PlayerLoadingState> loadingStates;

  private long threadId;

  /** 构造一个新的实例，使用此类中定义的 {@code DEFAULT_*} 常量。 */
  public DefaultLoadControl() {
    this(
        new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
        DEFAULT_MIN_BUFFER_MS,
        DEFAULT_MAX_BUFFER_MS,
        DEFAULT_BUFFER_FOR_PLAYBACK_MS,
        DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        DEFAULT_TARGET_BUFFER_BYTES,
        DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
        DEFAULT_BACK_BUFFER_DURATION_MS,
        DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME);
  }

  protected DefaultLoadControl(
      DefaultAllocator allocator,
      int minBufferMs,
      int maxBufferMs,
      int bufferForPlaybackMs,
      int bufferForPlaybackAfterRebufferMs,
      int targetBufferBytes,
      boolean prioritizeTimeOverSizeThresholds,
      int backBufferDurationMs,
      boolean retainBackBufferFromKeyframe) {
    assertGreaterOrEqual(bufferForPlaybackMs, 0, "bufferForPlaybackMs", "0");
    assertGreaterOrEqual(
        bufferForPlaybackAfterRebufferMs, 0, "bufferForPlaybackAfterRebufferMs", "0");
    assertGreaterOrEqual(minBufferMs, bufferForPlaybackMs, "minBufferMs", "bufferForPlaybackMs");
    assertGreaterOrEqual(
        minBufferMs,
        bufferForPlaybackAfterRebufferMs,
        "minBufferMs",
        "bufferForPlaybackAfterRebufferMs");
    assertGreaterOrEqual(maxBufferMs, minBufferMs, "maxBufferMs", "minBufferMs");
    assertGreaterOrEqual(backBufferDurationMs, 0, "backBufferDurationMs", "0");

    this.allocator = allocator;
    this.minBufferUs = Util.msToUs(minBufferMs);
    this.maxBufferUs = Util.msToUs(maxBufferMs);
    this.bufferForPlaybackUs = Util.msToUs(bufferForPlaybackMs);
    this.bufferForPlaybackAfterRebufferUs = Util.msToUs(bufferForPlaybackAfterRebufferMs);
    this.targetBufferBytesOverwrite = targetBufferBytes;
    this.prioritizeTimeOverSizeThresholds = prioritizeTimeOverSizeThresholds;
    this.backBufferDurationUs = Util.msToUs(backBufferDurationMs);
    this.retainBackBufferFromKeyframe = retainBackBufferFromKeyframe;
    loadingStates = new HashMap<>();
    threadId = C.INDEX_UNSET;
  }

  @Override
  public void onPrepared(PlayerId playerId) {
    long currentThreadId = Thread.currentThread().getId();
    checkState(
        threadId == C.INDEX_UNSET || threadId == currentThreadId,
        "共享相同 LoadControl 的播放器必须共享相同的播放线程。请参阅"
            + " ExoPlayer.Builder.setPlaybackLooper(Looper)。");
    threadId = currentThreadId;
    if (!loadingStates.containsKey(playerId)) {
      loadingStates.put(playerId, new PlayerLoadingState());
    }
    resetPlayerLoadingState(playerId);
  }

  @Override
  public void onTracksSelected(
      LoadControl.Parameters parameters,
      TrackGroupArray trackGroups,
      @NullableType ExoTrackSelection[] trackSelections) {
    checkNotNull(loadingStates.get(parameters.playerId)).targetBufferBytes =
        targetBufferBytesOverwrite == C.LENGTH_UNSET
            ? calculateTargetBufferBytes(trackSelections)
            : targetBufferBytesOverwrite;
    updateAllocator();
  }

  @Override
  public void onStopped(PlayerId playerId) {
    removePlayer(playerId);
  }

  @Override
  public void onReleased(PlayerId playerId) {
    removePlayer(playerId);
    if (loadingStates.isEmpty()) {
      threadId = C.INDEX_UNSET;
    }
  }

  @Override
  public Allocator getAllocator() {
    return allocator;
  }

  @Override
  public long getBackBufferDurationUs(PlayerId playerId) {
    return backBufferDurationUs;
  }

  @Override
  public boolean retainBackBufferFromKeyframe(PlayerId playerId) {
    return retainBackBufferFromKeyframe;
  }

  @Override
  public boolean shouldContinueLoading(Parameters parameters) {
    PlayerLoadingState playerLoadingState = checkNotNull(loadingStates.get(parameters.playerId));
    boolean targetBufferSizeReached =
        allocator.getTotalBytesAllocated() >= calculateTotalTargetBufferBytes();
    long minBufferUs = this.minBufferUs;
    if (parameters.playbackSpeed > 1) {
      // 播放速度比实时快，因此按比例增加所需的最小媒体时长，以保持足够的缓冲媒体。
      long mediaDurationMinBufferUs =
          Util.getMediaDurationForPlayoutDuration(minBufferUs, parameters.playbackSpeed);
      minBufferUs = min(mediaDurationMinBufferUs, maxBufferUs);
    }
    // 如果 minBufferUs 太小，防止播放卡住。
    minBufferUs = max(minBufferUs, 500_000);
    if (parameters.bufferedDurationUs < minBufferUs) {
      playerLoadingState.isLoading = prioritizeTimeOverSizeThresholds || !targetBufferSizeReached;
      if (!playerLoadingState.isLoading && parameters.bufferedDurationUs < 500_000) {
        Log.w(
            "DefaultLoadControl",
            "目标缓冲区大小已达到，但缓冲的媒体数据少于 500ms。");
      }
    } else if (parameters.bufferedDurationUs >= maxBufferUs || targetBufferSizeReached) {
      playerLoadingState.isLoading = false;
    } // 否则不改变加载状态。
    return playerLoadingState.isLoading;
  }

  @Override
  public boolean shouldStartPlayback(Parameters parameters) {
    long bufferedDurationUs =
        Util.getPlayoutDurationForMediaDuration(
            parameters.bufferedDurationUs, parameters.playbackSpeed);
    long minBufferDurationUs =
        parameters.rebuffering ? bufferForPlaybackAfterRebufferUs : bufferForPlaybackUs;
    if (parameters.targetLiveOffsetUs != C.TIME_UNSET) {
      minBufferDurationUs = min(parameters.targetLiveOffsetUs / 2, minBufferDurationUs);
    }
    return minBufferDurationUs <= 0
        || bufferedDurationUs >= minBufferDurationUs
        || (!prioritizeTimeOverSizeThresholds
        && allocator.getTotalBytesAllocated() >= calculateTotalTargetBufferBytes());
  }

  @Override
  public boolean shouldContinuePreloading(
      Timeline timeline, MediaPeriodId mediaPeriodId, long bufferedDurationUs) {
    for (PlayerLoadingState playerLoadingState : loadingStates.values()) {
      if (playerLoadingState.isLoading) {
        return false;
      }
    }
    return true;
  }

  /**
   * 根据选定的轨道计算目标缓冲区大小，单位为字节。播放器将尝试不超过此目标缓冲区。仅在 {@code targetBufferBytes} 为 {@link C#LENGTH_UNSET} 时使用。
   *
   * @param trackSelectionArray 选定的轨道。
   * @return 目标缓冲区大小，单位为字节。
   */
  protected int calculateTargetBufferBytes(@NullableType ExoTrackSelection[] trackSelectionArray) {
    int targetBufferSize = 0;
    for (ExoTrackSelection exoTrackSelection : trackSelectionArray) {
      if (exoTrackSelection != null) {
        // 根据轨道类型获取默认缓冲区大小并累加
        targetBufferSize += getDefaultBufferSize(exoTrackSelection.getTrackGroup().type);
      }
    }
    // 返回目标缓冲区大小，确保不小于默认的最小缓冲区大小
    return max(DEFAULT_MIN_BUFFER_SIZE, targetBufferSize);
  }

  /**
   * @deprecated 请改用 {@link #calculateTargetBufferBytes(ExoTrackSelection[])}。
   */
  @InlineMe(replacement = "this.calculateTargetBufferBytes(trackSelectionArray)")
  @Deprecated
  protected final int calculateTargetBufferBytes(
      Renderer[] renderers, ExoTrackSelection[] trackSelectionArray) {
    // 调用新方法计算目标缓冲区大小
    return calculateTargetBufferBytes(trackSelectionArray);
  }

  @VisibleForTesting
  /* package */ int calculateTotalTargetBufferBytes() {
    int totalTargetBufferBytes = 0;
    for (PlayerLoadingState state : loadingStates.values()) {
      totalTargetBufferBytes += state.targetBufferBytes;
    }
    return totalTargetBufferBytes;
  }

  private void resetPlayerLoadingState(PlayerId playerId) {
    PlayerLoadingState playerLoadingState = checkNotNull(loadingStates.get(playerId));
    playerLoadingState.targetBufferBytes =
        targetBufferBytesOverwrite == C.LENGTH_UNSET
            ? DEFAULT_MIN_BUFFER_SIZE
            : targetBufferBytesOverwrite;
    playerLoadingState.isLoading = false;
  }

  private void removePlayer(PlayerId playerId) {
    if (loadingStates.remove(playerId) != null) {
      updateAllocator();
    }
  }

  private void updateAllocator() {
    if (loadingStates.isEmpty()) {
      allocator.reset();
    } else {
      allocator.setTargetBufferSize(calculateTotalTargetBufferBytes());
    }
  }

  private static int getDefaultBufferSize(@C.TrackType int trackType) {
    switch (trackType) {
      case C.TRACK_TYPE_DEFAULT:
        return DEFAULT_MUXED_BUFFER_SIZE;
      case C.TRACK_TYPE_AUDIO:
        return DEFAULT_AUDIO_BUFFER_SIZE;
      case C.TRACK_TYPE_VIDEO:
        return DEFAULT_VIDEO_BUFFER_SIZE;
      case C.TRACK_TYPE_TEXT:
        return DEFAULT_TEXT_BUFFER_SIZE;
      case C.TRACK_TYPE_METADATA:
        return DEFAULT_METADATA_BUFFER_SIZE;
      case C.TRACK_TYPE_CAMERA_MOTION:
        return DEFAULT_CAMERA_MOTION_BUFFER_SIZE;
      case C.TRACK_TYPE_IMAGE:
        return DEFAULT_IMAGE_BUFFER_SIZE;
      case C.TRACK_TYPE_NONE:
        return 0;
      case C.TRACK_TYPE_UNKNOWN:
        return DEFAULT_MIN_BUFFER_SIZE;
      default:
        throw new IllegalArgumentException();
    }
  }

  private static void assertGreaterOrEqual(int value1, int value2, String name1, String name2) {
    Assertions.checkArgument(value1 >= value2, name1 + " cannot be less than " + name2);
  }

  private static class PlayerLoadingState {
    public boolean isLoading;
    public int targetBufferBytes;
  }
}
