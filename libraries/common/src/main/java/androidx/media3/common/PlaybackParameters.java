package androidx.media3.common;

import android.os.Bundle;
import androidx.annotation.CheckResult;
import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/** 适用于播放的参数，包括速度设置。 */
public final class PlaybackParameters {

  /** 默认的播放参数：实时播放，不跳过静音。 */
  public static final PlaybackParameters DEFAULT = new PlaybackParameters(/* speed= */ 1f);

  /** 播放速度的倍数。 */
  public final float speed;

  /** 音高调整的倍数。 */
  public final float pitch;

  private final int scaledUsPerMs;

  /**
   * 创建新的播放参数，设置播放速度。音频的音高不会调整，因此效果是拉伸音频时间。
   *
   * @param speed 播放速度的倍数。必须大于零。
   */
  public PlaybackParameters(@FloatRange(from = 0, fromInclusive = false) float speed) {
    this(speed, /* pitch= */ 1f);
  }

  /**
   * 创建新的播放参数，设置播放速度和音高。
   *
   * @param speed 播放速度的倍数。必须大于零。
   * @param pitch 音频音高调整的倍数。必须大于零。常用的值为 {@code 1}（用于拉伸音频时间）和与 {@code speed} 相同的值（用于重新采样音频，适用于慢动作视频）。
   */
  public PlaybackParameters(
      @FloatRange(from = 0, fromInclusive = false) float speed,
      @FloatRange(from = 0, fromInclusive = false) float pitch) {
    Assertions.checkArgument(speed > 0);
    Assertions.checkArgument(pitch > 0);
    this.speed = speed;
    this.pitch = pitch;
    scaledUsPerMs = Math.round(speed * 1000f);
  }

  /**
   * 返回在 {@code timeMs} 毫秒的墙钟时间内将流逝的媒体时间（以微秒为单位）。
   *
   * @param timeMs 要缩放的时间，以毫秒为单位。
   * @return 缩放后的时间，以微秒为单位。
   */
  @UnstableApi
  public long getMediaTimeUsForPlayoutTimeMs(long timeMs) {
    return timeMs * scaledUsPerMs;
  }

  /**
   * 返回具有给定速度的副本。
   *
   * @param speed 新的速度。必须大于零。
   * @return 复制后的播放参数。
   */
  @CheckResult
  public PlaybackParameters withSpeed(@FloatRange(from = 0, fromInclusive = false) float speed) {
    return new PlaybackParameters(speed, pitch);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    PlaybackParameters other = (PlaybackParameters) obj;
    return this.speed == other.speed && this.pitch == other.pitch;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + Float.floatToRawIntBits(speed);
    result = 31 * result + Float.floatToRawIntBits(pitch);
    return result;
  }

  @Override
  public String toString() {
    return Util.formatInvariant("PlaybackParameters(speed=%.2f, pitch=%.2f)", speed, pitch);
  }

  private static final String FIELD_SPEED = Util.intToStringMaxRadix(0);
  private static final String FIELD_PITCH = Util.intToStringMaxRadix(1);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putFloat(FIELD_SPEED, speed);
    bundle.putFloat(FIELD_PITCH, pitch);
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复一个 {@code PlaybackParameters}。 */
  @UnstableApi
  public static PlaybackParameters fromBundle(Bundle bundle) {
    float speed = bundle.getFloat(FIELD_SPEED, /* defaultValue= */ 1f);
    float pitch = bundle.getFloat(FIELD_PITCH, /* defaultValue= */ 1f);
    return new PlaybackParameters(speed, pitch);
  }
  ;
}