package androidx.media3.exoplayer;

import static androidx.media3.exoplayer.Renderer.STATE_STARTED;

import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Clock;

/**
 * 默认的 {@link MediaClock} 实现，使用渲染器的媒体时钟，并在必要时回退到 {@link StandaloneMediaClock}。
 */
/* package */ final class DefaultMediaClock implements MediaClock {

  /** 监听器接口，用于监听播放参数的变化。 */
  public interface PlaybackParametersListener {

    /**
     * 当活动的播放参数发生变化时调用。不会为 {@link #setPlaybackParameters(PlaybackParameters)} 调用。
     *
     * @param newPlaybackParameters 新的活动播放参数。
     */
    void onPlaybackParametersChanged(PlaybackParameters newPlaybackParameters);
  }

  private final StandaloneMediaClock standaloneClock; // 独立的媒体时钟
  private final PlaybackParametersListener listener; // 播放参数监听器

  @Nullable private Renderer rendererClockSource; // 提供媒体时钟的渲染器
  @Nullable private MediaClock rendererClock; // 渲染器的媒体时钟
  private boolean isUsingStandaloneClock; // 是否正在使用独立时钟
  private boolean standaloneClockIsStarted; // 独立时钟是否已启动

  /**
   * 创建一个新实例，带有播放参数变化的监听器和用于独立时钟实现的 {@link Clock}。
   *
   * @param listener 监听播放参数变化的 {@link PlaybackParametersListener}。
   * @param clock 用于独立时钟的 {@link Clock}。
   */
  public DefaultMediaClock(PlaybackParametersListener listener, Clock clock) {
    this.listener = listener;
    this.standaloneClock = new StandaloneMediaClock(clock);
    isUsingStandaloneClock = true;
  }

  /** 启动独立的回退时钟。 */
  public void start() {
    standaloneClockIsStarted = true;
    standaloneClock.start();
  }

  /** 停止独立的回退时钟。 */
  public void stop() {
    standaloneClockIsStarted = false;
    standaloneClock.stop();
  }

  /**
   * 重置独立回退时钟的位置。
   *
   * @param positionUs 要设置的位置，单位为微秒。
   */
  public void resetPosition(long positionUs) {
    standaloneClock.resetPosition(positionUs);
  }

  /**
   * 通知媒体时钟某个渲染器已启用。如果可用，则开始使用该渲染器的媒体时钟。
   *
   * @param renderer 已启用的渲染器。
   * @throws ExoPlaybackException 如果该渲染器提供了媒体时钟，但另一个渲染器已经提供了媒体时钟。
   */
  public void onRendererEnabled(Renderer renderer) throws ExoPlaybackException {
    @Nullable MediaClock rendererMediaClock = renderer.getMediaClock();
    if (rendererMediaClock != null && rendererMediaClock != rendererClock) {
      if (rendererClock != null) {
        throw ExoPlaybackException.createForUnexpected(
            new IllegalStateException("多个渲染器媒体时钟已启用。"),
            PlaybackException.ERROR_CODE_UNSPECIFIED);
      }
      this.rendererClock = rendererMediaClock;
      this.rendererClockSource = renderer;
      rendererClock.setPlaybackParameters(standaloneClock.getPlaybackParameters());
    }
  }

  /**
   * 通知媒体时钟某个渲染器已禁用。如果正在使用该渲染器的媒体时钟，则停止使用。
   *
   * @param renderer 已禁用的渲染器。
   */
  public void onRendererDisabled(Renderer renderer) {
    if (renderer == rendererClockSource) {
      this.rendererClock = null;
      this.rendererClockSource = null;
      isUsingStandaloneClock = true;
    }
  }

  /**
   * 如果需要，同步内部时钟并返回当前时钟位置，单位为微秒。
   *
   * @param isReadingAhead 渲染器是否正在预读。
   */
  public long syncAndGetPositionUs(boolean isReadingAhead) {
    syncClocks(isReadingAhead);
    return getPositionUs();
  }

  // MediaClock 实现。

  @Override
  public long getPositionUs() {
    return isUsingStandaloneClock
        ? standaloneClock.getPositionUs()
        : Assertions.checkNotNull(rendererClock).getPositionUs();
  }

  @Override
  public boolean hasSkippedSilenceSinceLastCall() {
    return isUsingStandaloneClock
        ? standaloneClock.hasSkippedSilenceSinceLastCall()
        : Assertions.checkNotNull(rendererClock).hasSkippedSilenceSinceLastCall();
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    if (rendererClock != null) {
      rendererClock.setPlaybackParameters(playbackParameters);
      playbackParameters = rendererClock.getPlaybackParameters();
    }
    standaloneClock.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return rendererClock != null
        ? rendererClock.getPlaybackParameters()
        : standaloneClock.getPlaybackParameters();
  }

  private void syncClocks(boolean isReadingAhead) {
    if (shouldUseStandaloneClock(isReadingAhead)) {
      isUsingStandaloneClock = true;
      if (standaloneClockIsStarted) {
        standaloneClock.start();
      }
      return;
    }
    // 我们已经在使用渲染器时钟，或者正在从独立时钟切换到渲染器时钟，因此它必须为非空。
    MediaClock rendererClock = Assertions.checkNotNull(this.rendererClock);
    long rendererClockPositionUs = rendererClock.getPositionUs();
    if (isUsingStandaloneClock) {
      // 确保启用渲染器时钟不会使时间跳回。
      if (rendererClockPositionUs < standaloneClock.getPositionUs()) {
        standaloneClock.stop();
        return;
      }
      isUsingStandaloneClock = false;
      if (standaloneClockIsStarted) {
        standaloneClock.start();
      }
    }
    // 持续将独立时钟同步到渲染器时钟，以便在需要时可以接管。
    standaloneClock.resetPosition(rendererClockPositionUs);
    PlaybackParameters playbackParameters = rendererClock.getPlaybackParameters();
    if (!playbackParameters.equals(standaloneClock.getPlaybackParameters())) {
      standaloneClock.setPlaybackParameters(playbackParameters);
      listener.onPlaybackParametersChanged(playbackParameters);
    }
  }

  private boolean shouldUseStandaloneClock(boolean isReadingAhead) {
    // 如果提供时钟的渲染器未设置或已结束，则使用独立时钟。如果正在预读且渲染器未处于启动状态，则使用独立时钟。
    // 如果渲染器未准备好且我们已经读取完流或正在预读，则使用独立时钟，以避免在当前周期的轨道持续时间不均匀时卡住。
    // 参见：https://github.com/google/ExoPlayer/issues/1874。
    return rendererClockSource == null
        || rendererClockSource.isEnded()
        || (isReadingAhead && rendererClockSource.getState() != STATE_STARTED)
        || (!rendererClockSource.isReady()
        && (isReadingAhead || rendererClockSource.hasReadStreamToEnd()));
  }
}