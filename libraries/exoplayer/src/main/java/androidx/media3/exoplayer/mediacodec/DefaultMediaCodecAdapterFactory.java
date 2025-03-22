/*
 * 版权所有 2021 The Android Open Source Project
 *
 * 根据 Apache License, Version 2.0（“许可证”）授权；
 * 除非符合许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则按“原样”分发的软件
 * 没有任何形式的明示或暗示的保证或条件。
 * 请参阅许可证以了解特定语言下的权限和限制。
 */
package androidx.media3.exoplayer.mediacodec;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.media.MediaCodec;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 默认的 {@link MediaCodecAdapter.Factory}。
 *
 * <p>默认情况下，此工厂在 API 级别 &gt;= 31（Android 12+）的设备上 {@link #createAdapter 创建} {@link AsynchronousMediaCodecAdapter} 实例。
 * 对于 API 版本较旧的设备，默认行为是创建 {@link SynchronousMediaCodecAdapter} 实例。
 * 该工厂提供了 API 来强制创建 {@link AsynchronousMediaCodecAdapter}（适用于 API &gt;= 23 的设备）或 {@link SynchronousMediaCodecAdapter} 实例。
 */
@UnstableApi
public final class DefaultMediaCodecAdapterFactory implements MediaCodecAdapter.Factory {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({MODE_DEFAULT, MODE_ENABLED, MODE_DISABLED})
  private @interface Mode {}

  private static final int MODE_DEFAULT = 0;
  private static final int MODE_ENABLED = 1;
  private static final int MODE_DISABLED = 2;

  private static final String TAG = "DMCodecAdapterFactory";

  @Nullable private final Context context;

  private @Mode int asynchronousMode;
  private boolean asyncCryptoFlagEnabled;

  /**
   * @deprecated 请使用 {@link #DefaultMediaCodecAdapterFactory(Context)} 代替。
   */
  @Deprecated
  public DefaultMediaCodecAdapterFactory() {
    asynchronousMode = MODE_DEFAULT;
    asyncCryptoFlagEnabled = false;
    context = null;
  }

  /**
   * 创建默认的媒体编解码器适配器工厂。
   *
   * @param context 一个 {@link Context}。
   */
  public DefaultMediaCodecAdapterFactory(Context context) {
    this.context = context;
    asynchronousMode = MODE_DEFAULT;
    asyncCryptoFlagEnabled = false;
  }

  /**
   * 强制此工厂始终创建 {@link AsynchronousMediaCodecAdapter} 实例，前提是设备 API 级别 &gt;= 23。
   * 对于 API 级别 &lt; 23 的设备，工厂将创建 {@link SynchronousMediaCodecAdapter} 实例。
   *
   * @return 此工厂，以便链式调用。
   */
  @CanIgnoreReturnValue
  public DefaultMediaCodecAdapterFactory forceEnableAsynchronous() {
    asynchronousMode = MODE_ENABLED;
    return this;
  }

  /**
   * 强制工厂始终创建 {@link SynchronousMediaCodecAdapter} 实例。
   *
   * @return 此工厂，以便链式调用。
   */
  @CanIgnoreReturnValue
  public DefaultMediaCodecAdapterFactory forceDisableAsynchronous() {
    asynchronousMode = MODE_DISABLED;
    return this;
  }

  /**
   * 设置是否在 API 34 及更高版本上为 {@link AsynchronousMediaCodecAdapter} 实例启用 {@link MediaCodec#CONFIGURE_FLAG_USE_CRYPTO_ASYNC}。
   *
   * <p>此方法是实验性的。其默认值可能会更改，或者它可能会在未来的版本中重命名或移除。
   */
  @CanIgnoreReturnValue
  public DefaultMediaCodecAdapterFactory experimentalSetAsyncCryptoFlagEnabled(
      boolean enableAsyncCryptoFlag) {
    asyncCryptoFlagEnabled = enableAsyncCryptoFlag;
    return this;
  }

  @Override
  public MediaCodecAdapter createAdapter(MediaCodecAdapter.Configuration configuration)
      throws IOException {
    if (Util.SDK_INT >= 23
        && (asynchronousMode == MODE_ENABLED
        || (asynchronousMode == MODE_DEFAULT && shouldUseAsynchronousAdapterInDefaultMode()))) {
      int trackType = MimeTypes.getTrackType(configuration.format.sampleMimeType);
      Log.i(
          TAG,
          "为轨道类型创建异步 MediaCodec 适配器："
              + Util.getTrackTypeString(trackType));
      AsynchronousMediaCodecAdapter.Factory factory =
          new AsynchronousMediaCodecAdapter.Factory(trackType);
      factory.experimentalSetAsyncCryptoFlagEnabled(asyncCryptoFlagEnabled);
      return factory.createAdapter(configuration);
    }
    return new SynchronousMediaCodecAdapter.Factory().createAdapter(configuration);
  }

  private boolean shouldUseAsynchronousAdapterInDefaultMode() {
    if (Util.SDK_INT >= 31) {
      // 在 API 31+ 上，异步编解码器交互开始对所有设备可靠。
      return true;
    }
    // 允许某些设备使用异步适配器，因为这些设备在使用异步适配器时表现可靠，而在不使用时会遇到性能问题。
    if (context != null
        && Util.SDK_INT >= 28
        && context.getPackageManager().hasSystemFeature("com.amazon.hardware.tv_screen")) {
      return true;
    }
    return false;
  }
}