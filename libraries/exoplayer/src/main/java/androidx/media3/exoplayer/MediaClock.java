package androidx.media3.exoplayer;

import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.util.UnstableApi;

/** 跟踪媒体时间的进度。 */
@UnstableApi
public interface MediaClock {

  /** 返回当前的媒体位置，单位为微秒。 */
  long getPositionUs();

  /** 返回自上次调用此方法以来是否跳过了静音。 */
  default boolean hasSkippedSilenceSinceLastCall() {
    return false;
  }

  /**
   * 尝试设置播放参数。如果更改播放参数不受支持，媒体时钟可能会覆盖速度。
   *
   * @param playbackParameters 要尝试设置的播放参数。
   */
  void setPlaybackParameters(PlaybackParameters playbackParameters);

  /** 返回当前的播放参数。 */
  PlaybackParameters getPlaybackParameters();
}