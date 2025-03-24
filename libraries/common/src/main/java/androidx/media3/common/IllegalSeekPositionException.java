package androidx.media3.common;

import androidx.media3.common.util.UnstableApi;

/**
 * 当尝试跳转到播放器的 {@link Timeline} 中不存在的位置时抛出的异常。
 */
@UnstableApi
public final class IllegalSeekPositionException extends IllegalStateException {

  /** 尝试跳转的 {@link Timeline}。 */
  public final Timeline timeline;

  /** 尝试跳转到的窗口索引。 */
  public final int windowIndex;

  /** 在指定窗口中的跳转位置（单位：毫秒）。 */
  public final long positionMs;

  /**
   * @param timeline 尝试跳转的 {@link Timeline}。
   * @param windowIndex 尝试跳转到的窗口索引。
   * @param positionMs 在指定窗口中的跳转位置（单位：毫秒）。
   */
  public IllegalSeekPositionException(Timeline timeline, int windowIndex, long positionMs) {
    this.timeline = timeline;
    this.windowIndex = windowIndex;
    this.positionMs = positionMs;
  }
}