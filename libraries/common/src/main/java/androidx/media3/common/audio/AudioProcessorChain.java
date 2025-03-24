package androidx.media3.common.audio;

import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.util.UnstableApi;

/**
 * 提供音频处理器链，用于任何用户定义的处理和应用播放参数（如果支持）。由于应用播放参数可能会跳过和拉伸/压缩音频，
 * 音频接收器会通过 {@link #getMediaDuration(long)} 和 {@link #getSkippedOutputFrameCount()} 查询链以获取如何将其输出位置映射到媒体位置的信息。
 */
@UnstableApi
public interface AudioProcessorChain {

  /**
   * 返回将处理音频的固定音频处理器链。此方法在初始化期间调用一次，但音频处理器可能会在播放期间更改状态以变为激活/非激活状态。
   */
  AudioProcessor[] getAudioProcessors();

  /**
   * 配置音频处理器以立即应用指定的播放参数，返回实际应用的播放参数，可能与传入的参数不同。仅在处理器没有挂起的输入时调用。
   *
   * @param playbackParameters 要尝试应用的播放参数。
   * @return 实际应用的播放参数。
   */
  PlaybackParameters applyPlaybackParameters(PlaybackParameters playbackParameters);

  /**
   * 配置音频处理器以立即应用是否跳过静音，返回新的值。仅在处理器没有挂起的输入时调用。
   *
   * @param skipSilenceEnabled 是否应在音频流中跳过静音。
   * @return 新的值。
   */
  boolean applySkipSilenceEnabled(boolean skipSilenceEnabled);

  /**
   * 返回与指定播放持续时间对应的媒体持续时间，考虑到音频处理导致的速度调整。
   *
   * <p>此方法执行的缩放将使用音频处理器链自上次刷新以来实际实现的平均播放速度。这可能与目标播放速度略有不同。
   *
   * @param playoutDuration 要缩放的播放持续时间。
   * @return 对应的媒体持续时间，与 {@code duration} 使用相同的单位。
   */
  long getMediaDuration(long playoutDuration);

  /**
   * 返回自音频处理器上次刷新以来跳过的输出音频帧数。
   */
  long getSkippedOutputFrameCount();
}