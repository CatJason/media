package androidx.media3.common.audio;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;

/** 一个自定义接口，用于确定特定时间戳的媒体速度。 */
@UnstableApi
public interface SpeedProvider {

  /**
   * 返回从提供的时间戳开始的媒体速度。
   *
   * <p>媒体速度将保持不变，直到 {@linkplain #getNextSpeedChangeTimeUs 下一个指定的速度变化}。
   *
   * @param timeUs 媒体的时间戳。
   * @return 媒体应播放的速度。
   */
  float getSpeed(long timeUs);

  /**
   * 返回下一个速度变化的时间戳（如果有）。
   *
   * @param timeUs 一个时间戳，单位为微秒。
   * @return 下一个速度变化的时间戳，单位为微秒，如果没有下一个速度变化，则返回 {@link C#TIME_UNSET}。
   *     如果 {@code timeUs} 对应一个速度变化，则返回值对应下一个速度变化。
   */
  long getNextSpeedChangeTimeUs(long timeUs);
}