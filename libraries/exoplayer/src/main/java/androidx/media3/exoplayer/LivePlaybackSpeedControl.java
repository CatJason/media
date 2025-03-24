package androidx.media3.exoplayer;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem.LiveConfiguration;
import androidx.media3.common.util.UnstableApi;

/**
 * 控制播放直播内容时的播放速度，以保持稳定的目标直播偏移量。
 */
@UnstableApi
public interface LivePlaybackSpeedControl {

  /**
   * 设置由媒体定义的直播配置。
   *
   * @param liveConfiguration 媒体定义的 {@link LiveConfiguration}。
   */
  void setLiveConfiguration(LiveConfiguration liveConfiguration);

  /**
   * 设置以微秒为单位的目标直播偏移量，覆盖通过 {@link #setLiveConfiguration} 配置的直播偏移量。传递 {@code C.TIME_UNSET} 会删除之前的覆盖。
   *
   * <p>如果未通过 {@link #setLiveConfiguration} 配置目标直播偏移量，则此覆盖无效。
   */
  void setTargetLiveOffsetOverrideUs(long liveOffsetUs);

  /**
   * 通知直播播放速度控制发生了重新缓冲。
   *
   * <p>重新缓冲定义为由缓冲区耗尽引起，而不是用户操作。因此，在初始缓冲期间或由于搜索操作导致的缓冲期间不会调用此方法。
   */
  void notifyRebuffer();

  /**
   * 返回调整后的播放速度，以便更接近 {@link #getTargetLiveOffsetUs() 目标直播偏移量}。
   *
   * @param liveOffsetUs 当前的直播偏移量，单位为微秒。
   * @param bufferedDurationUs 当前缓冲的媒体时长，单位为微秒。
   * @return 应加速播放的调整因子。
   */
  float getAdjustedPlaybackSpeed(long liveOffsetUs, long bufferedDurationUs);

  /**
   * 返回当前的目标直播偏移量，单位为微秒，如果当前媒体未定义目标直播偏移量，则返回 {@link C#TIME_UNSET}。
   */
  long getTargetLiveOffsetUs();
}