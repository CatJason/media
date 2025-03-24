package androidx.media3.common;

import android.media.AudioTrack;
import android.media.audiofx.AudioEffect;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

/**
 * 表示辅助效果信息，可用于将辅助效果附加到底层的 {@link AudioTrack}。
 *
 * <p>只有在应用程序具有 {@code android.permission.MODIFY_AUDIO_SETTINGS} 权限时，才能应用辅助效果。应用负责保留相关的音频效果实例，并在不再需要时释放它。有关更多信息，请参阅 {@link AudioEffect} 的文档。
 */
@UnstableApi
public final class AuxEffectInfo {

  /** 表示无辅助效果的 {@link #effectId} 的值。 */
  public static final int NO_AUX_EFFECT_ID = 0;

  /**
   * 效果的标识符，如果没有效果则为 {@link #NO_AUX_EFFECT_ID}。
   *
   * @see android.media.AudioTrack#attachAuxEffect(int)
   */
  public final int effectId;

  /**
   * 效果的发送级别。
   *
   * @see android.media.AudioTrack#setAuxEffectSendLevel(float)
   */
  public final float sendLevel;

  /**
   * 使用给定的效果标识符和发送级别创建实例。
   *
   * @param effectId 效果标识符。这是效果上 {@link AudioEffect#getId()} 返回的值，或 {@link #NO_AUX_EFFECT_ID} 表示无效果。
   *                 此值将传递到底层音频轨道的 {@link AudioTrack#attachAuxEffect(int)} 方法。
   * @param sendLevel 效果的发送级别，其中 0 表示无效果，1 表示完全发送。如果 {@code effectId} 不是 {@link #NO_AUX_EFFECT_ID}，
   *                  则此值将传递到底层音频轨道的 {@link AudioTrack#setAuxEffectSendLevel(float)} 方法。
   */
  public AuxEffectInfo(int effectId, float sendLevel) {
    this.effectId = effectId;
    this.sendLevel = sendLevel;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AuxEffectInfo auxEffectInfo = (AuxEffectInfo) o;
    return effectId == auxEffectInfo.effectId
        && Float.compare(auxEffectInfo.sendLevel, sendLevel) == 0;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + effectId;
    result = 31 * result + Float.floatToIntBits(sendLevel);
    return result;
  }
}