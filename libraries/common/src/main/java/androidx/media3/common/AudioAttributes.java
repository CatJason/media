package androidx.media3.common;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * 音频播放属性，用于配置底层的 {@link android.media.AudioTrack}。
 *
 * <p>要设置音频属性，请使用 {@link Builder} 创建实例，并将其传递给播放器，或向音频渲染器发送类型为 {@code Renderer#MSG_SET_AUDIO_ATTRIBUTES} 的消息。
 *
 * <p>此类基于 {@link android.media.AudioAttributes}，但可以在所有支持的 API 版本上使用。
 */
public final class AudioAttributes {

  /** {@link android.media.AudioAttributes} 的直接封装类。 */
  public static final class AudioAttributesV21 {
    public final android.media.AudioAttributes audioAttributes;

    private AudioAttributesV21(AudioAttributes audioAttributes) {
      android.media.AudioAttributes.Builder builder =
          new android.media.AudioAttributes.Builder()
              .setContentType(audioAttributes.contentType)
              .setFlags(audioAttributes.flags)
              .setUsage(audioAttributes.usage);
      if (Util.SDK_INT >= 29) {
        Api29.setAllowedCapturePolicy(builder, audioAttributes.allowedCapturePolicy);
      }
      if (Util.SDK_INT >= 32) {
        Api32.setSpatializationBehavior(builder, audioAttributes.spatializationBehavior);
      }
      this.audioAttributes = builder.build();
    }
  }

  /**
   * 默认的音频属性，其中内容类型为 {@link C#AUDIO_CONTENT_TYPE_UNKNOWN}，用途为 {@link C#USAGE_MEDIA}，捕获策略为 {@link C#ALLOW_CAPTURE_BY_ALL}，且未设置任何标志。
   */
  public static final AudioAttributes DEFAULT = new Builder().build();

  /** {@link AudioAttributes} 的构建器。 */
  public static final class Builder {

    private @C.AudioContentType int contentType;
    private @C.AudioFlags int flags;
    private @C.AudioUsage int usage;
    private @C.AudioAllowedCapturePolicy int allowedCapturePolicy;
    private @C.SpatializationBehavior int spatializationBehavior;

    /**
     * 创建一个新的 {@link AudioAttributes} 构建器。
     *
     * <p>默认情况下，内容类型为 {@link C#AUDIO_CONTENT_TYPE_UNKNOWN}，用途为 {@link C#USAGE_MEDIA}，捕获策略为 {@link C#ALLOW_CAPTURE_BY_ALL}，且未设置任何标志。
     */
    public Builder() {
      contentType = C.AUDIO_CONTENT_TYPE_UNKNOWN;
      flags = 0;
      usage = C.USAGE_MEDIA;
      allowedCapturePolicy = C.ALLOW_CAPTURE_BY_ALL;
      spatializationBehavior = C.SPATIALIZATION_BEHAVIOR_AUTO;
    }

    /** 参见 {@link android.media.AudioAttributes.Builder#setContentType(int)} */
    @CanIgnoreReturnValue
    public Builder setContentType(@C.AudioContentType int contentType) {
      this.contentType = contentType;
      return this;
    }

    /** 参见 {@link android.media.AudioAttributes.Builder#setFlags(int)} */
    @CanIgnoreReturnValue
    public Builder setFlags(@C.AudioFlags int flags) {
      this.flags = flags;
      return this;
    }

    /** 参见 {@link android.media.AudioAttributes.Builder#setUsage(int)} */
    @CanIgnoreReturnValue
    public Builder setUsage(@C.AudioUsage int usage) {
      this.usage = usage;
      return this;
    }

    /** 参见 {@link android.media.AudioAttributes.Builder#setAllowedCapturePolicy(int)}。 */
    @CanIgnoreReturnValue
    public Builder setAllowedCapturePolicy(@C.AudioAllowedCapturePolicy int allowedCapturePolicy) {
      this.allowedCapturePolicy = allowedCapturePolicy;
      return this;
    }

    /** 参见 {@link android.media.AudioAttributes.Builder#setSpatializationBehavior(int)}。 */
    @CanIgnoreReturnValue
    public Builder setSpatializationBehavior(@C.SpatializationBehavior int spatializationBehavior) {
      this.spatializationBehavior = spatializationBehavior;
      return this;
    }

    /** 从该构建器创建 {@link AudioAttributes} 实例。 */
    public AudioAttributes build() {
      return new AudioAttributes(
          contentType, flags, usage, allowedCapturePolicy, spatializationBehavior);
    }
  }

  /** {@link C.AudioContentType}。 */
  public final @C.AudioContentType int contentType;

  /** {@link C.AudioFlags}。 */
  public final @C.AudioFlags int flags;

  /** {@link C.AudioUsage}。 */
  public final @C.AudioUsage int usage;

  /** {@link C.AudioAllowedCapturePolicy}。 */
  public final @C.AudioAllowedCapturePolicy int allowedCapturePolicy;

  /** {@link C.SpatializationBehavior}。 */
  public final @C.SpatializationBehavior int spatializationBehavior;

  @Nullable private AudioAttributesV21 audioAttributesV21;

  private AudioAttributes(
      @C.AudioContentType int contentType,
      @C.AudioFlags int flags,
      @C.AudioUsage int usage,
      @C.AudioAllowedCapturePolicy int allowedCapturePolicy,
      @C.SpatializationBehavior int spatializationBehavior) {
    this.contentType = contentType;
    this.flags = flags;
    this.usage = usage;
    this.allowedCapturePolicy = allowedCapturePolicy;
    this.spatializationBehavior = spatializationBehavior;
  }

  /**
   * 从该实例返回一个 {@link AudioAttributesV21}。
   *
   * <p>如果当前 API 级别不支持相应的 {@link android.media.AudioAttributes.Builder} 设置方法，则某些字段将被忽略。
   */
  public AudioAttributesV21 getAudioAttributesV21() {
    if (audioAttributesV21 == null) {
      audioAttributesV21 = new AudioAttributesV21(this);
    }
    return audioAttributesV21;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    AudioAttributes other = (AudioAttributes) obj;
    return this.contentType == other.contentType
        && this.flags == other.flags
        && this.usage == other.usage
        && this.allowedCapturePolicy == other.allowedCapturePolicy
        && this.spatializationBehavior == other.spatializationBehavior;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + contentType;
    result = 31 * result + flags;
    result = 31 * result + usage;
    result = 31 * result + allowedCapturePolicy;
    result = 31 * result + spatializationBehavior;
    return result;
  }

  private static final String FIELD_CONTENT_TYPE = Util.intToStringMaxRadix(0);
  private static final String FIELD_FLAGS = Util.intToStringMaxRadix(1);
  private static final String FIELD_USAGE = Util.intToStringMaxRadix(2);
  private static final String FIELD_ALLOWED_CAPTURE_POLICY = Util.intToStringMaxRadix(3);
  private static final String FIELD_SPATIALIZATION_BEHAVIOR = Util.intToStringMaxRadix(4);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_CONTENT_TYPE, contentType);
    bundle.putInt(FIELD_FLAGS, flags);
    bundle.putInt(FIELD_USAGE, usage);
    bundle.putInt(FIELD_ALLOWED_CAPTURE_POLICY, allowedCapturePolicy);
    bundle.putInt(FIELD_SPATIALIZATION_BEHAVIOR, spatializationBehavior);
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复 {@code AudioAttributes}。 */
  @UnstableApi
  public static AudioAttributes fromBundle(Bundle bundle) {
    Builder builder = new Builder();
    if (bundle.containsKey(FIELD_CONTENT_TYPE)) {
      builder.setContentType(bundle.getInt(FIELD_CONTENT_TYPE));
    }
    if (bundle.containsKey(FIELD_FLAGS)) {
      builder.setFlags(bundle.getInt(FIELD_FLAGS));
    }
    if (bundle.containsKey(FIELD_USAGE)) {
      builder.setUsage(bundle.getInt(FIELD_USAGE));
    }
    if (bundle.containsKey(FIELD_ALLOWED_CAPTURE_POLICY)) {
      builder.setAllowedCapturePolicy(bundle.getInt(FIELD_ALLOWED_CAPTURE_POLICY));
    }
    if (bundle.containsKey(FIELD_SPATIALIZATION_BEHAVIOR)) {
      builder.setSpatializationBehavior(bundle.getInt(FIELD_SPATIALIZATION_BEHAVIOR));
    }
    return builder.build();
  }
  ;

  @RequiresApi(29)
  private static final class Api29 {
    public static void setAllowedCapturePolicy(
        android.media.AudioAttributes.Builder builder,
        @C.AudioAllowedCapturePolicy int allowedCapturePolicy) {
      builder.setAllowedCapturePolicy(allowedCapturePolicy);
    }
  }

  @RequiresApi(32)
  private static final class Api32 {
    public static void setSpatializationBehavior(
        android.media.AudioAttributes.Builder builder,
        @C.SpatializationBehavior int spatializationBehavior) {
      builder.setSpatializationBehavior(spatializationBehavior);
    }
  }
}