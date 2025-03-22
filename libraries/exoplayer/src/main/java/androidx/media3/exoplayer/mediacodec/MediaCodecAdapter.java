package androidx.media3.exoplayer.mediacodec;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.CryptoInfo;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 抽象了 {@link MediaCodec} 的操作。
 *
 * <p>{@code MediaCodecAdapter} 提供了一个通用接口来与 {@link MediaCodec} 交互，无论 {@link MediaCodec} 处于何种模式。
 */
@UnstableApi
public interface MediaCodecAdapter {
  /** {@link MediaCodecAdapter} 的配置参数。 */
  final class Configuration {

    /**
     * 为音频解码创建配置。
     *
     * @param codecInfo 参见 {@link #codecInfo}.
     * @param mediaFormat 参见 {@link #mediaFormat}.
     * @param format 参见 {@link #format}.
     * @param crypto 参见 {@link #crypto}.
     * @param loudnessCodecController 参见 {@link #loudnessCodecController}.
     * @return 创建的实例。
     */
    public static Configuration createForAudioDecoding(
        MediaCodecInfo codecInfo,
        MediaFormat mediaFormat,
        Format format,
        @Nullable MediaCrypto crypto,
        @Nullable LoudnessCodecController loudnessCodecController) {
      return new Configuration(
          codecInfo, mediaFormat, format, /* surface= */ null, crypto, loudnessCodecController);
    }

    /**
     * 为视频解码创建配置。
     *
     * @param codecInfo 参见 {@link #codecInfo}.
     * @param mediaFormat 参见 {@link #mediaFormat}.
     * @param format 参见 {@link #format}.
     * @param surface 参见 {@link #surface}.
     * @param crypto 参见 {@link #crypto}.
     * @return 创建的实例。
     */
    public static Configuration createForVideoDecoding(
        MediaCodecInfo codecInfo,
        MediaFormat mediaFormat,
        Format format,
        @Nullable Surface surface,
        @Nullable MediaCrypto crypto) {
      return new Configuration(
          codecInfo, mediaFormat, format, surface, crypto, /* loudnessCodecController= */ null);
    }

    /** 有关正在配置的 {@link MediaCodec} 的信息。 */
    public final MediaCodecInfo codecInfo;

    /** {@link MediaCodec} 正在配置的 {@link MediaFormat}。 */
    public final MediaFormat mediaFormat;

    /** {@link MediaCodec} 正在配置的 {@link Format}。 */
    public final Format format;

    /**
     * 对于视频解码，输出对象将渲染解码帧的 Surface。如果编解码器不是视频解码器，或者它配置为 {@link ByteBuffer} 输出，则必须为 null。
     */
    @Nullable public final Surface surface;

    /** 对于 DRM 保护的播放，用于解密的 {@link MediaCrypto}。 */
    @Nullable public final MediaCrypto crypto;

    /** 音频编解码器的 {@link LoudnessCodecController}。 */
    @Nullable public final LoudnessCodecController loudnessCodecController;

    private Configuration(
        MediaCodecInfo codecInfo,
        MediaFormat mediaFormat,
        Format format,
        @Nullable Surface surface,
        @Nullable MediaCrypto crypto,
        @Nullable LoudnessCodecController loudnessCodecController) {
      this.codecInfo = codecInfo;
      this.mediaFormat = mediaFormat;
      this.format = format;
      this.surface = surface;
      this.crypto = crypto;
      this.loudnessCodecController = loudnessCodecController;
    }
  }

  /** {@link MediaCodecAdapter} 实例的工厂。 */
  interface Factory {

    /**
     * @deprecated 请使用 {@link #getDefault} 代替。
     */
    @Deprecated
    @SuppressWarnings("deprecation") // 转发到已弃用的方法。
        Factory DEFAULT = new DefaultMediaCodecAdapterFactory();

    /**
     * 返回在大多数情况下应使用的默认工厂。
     *
     * @param context 一个 {@link Context}。
     * @return 默认工厂。
     */
    static Factory getDefault(Context context) {
      return new DefaultMediaCodecAdapterFactory(context);
    }

    /** 创建一个 {@link MediaCodecAdapter} 实例。 */
    MediaCodecAdapter createAdapter(Configuration configuration) throws IOException;
  }

  /**
   * 当输出帧在输出 Surface 上渲染时调用的监听器。
   *
   * @see MediaCodec.OnFrameRenderedListener
   */
  interface OnFrameRenderedListener {
    void onFrameRendered(MediaCodecAdapter codec, long presentationTimeUs, long nanoTime);
  }

  /** 当输入或输出缓冲区可用时调用的监听器。 */
  interface OnBufferAvailableListener {
    /**
     * 当输入缓冲区可用时调用。
     *
     * @see MediaCodec.Callback#onInputBufferAvailable(MediaCodec, int)
     */
    default void onInputBufferAvailable() {}

    /**
     * 当输出缓冲区可用时调用。
     *
     * @see MediaCodec.Callback#onOutputBufferAvailable(MediaCodec, int, MediaCodec.BufferInfo)
     */
    default void onOutputBufferAvailable() {}
  }

  /**
   * 从底层的 {@link MediaCodec} 返回下一个可用的输入缓冲区索引，如果不存在这样的缓冲区，则返回 {@link MediaCodec#INFO_TRY_AGAIN_LATER}。
   *
   * @throws IllegalStateException 如果底层的 {@link MediaCodec} 引发错误。
   */
  int dequeueInputBufferIndex();

  /**
   * 从底层的 {@link MediaCodec} 返回下一个可用的输出缓冲区索引。如果下一个可用输出是 MediaFormat 更改，则返回 {@link MediaCodec#INFO_OUTPUT_FORMAT_CHANGED}，您应调用 {@link #getOutputFormat()} 获取格式。如果没有可用输出，则返回 {@link MediaCodec#INFO_TRY_AGAIN_LATER}。
   *
   * @throws IllegalStateException 如果底层的 {@link MediaCodec} 引发错误。
   */
  int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo);

  /**
   * 获取从 {@link MediaCodec} 输出的 {@link MediaFormat}。
   *
   * <p>如果之前调用 {@link #dequeueOutputBufferIndex} 返回了 {@link MediaCodec#INFO_OUTPUT_FORMAT_CHANGED}，请调用此方法。
   */
  MediaFormat getOutputFormat();

  /**
   * 返回用于已出队输入缓冲区索引的可写 ByteBuffer 对象。
   *
   * @see MediaCodec#getInputBuffer(int)
   */
  @Nullable
  ByteBuffer getInputBuffer(int index);

  /**
   * 返回用于已出队输出缓冲区索引的只读 ByteBuffer。
   *
   * @see MediaCodec#getOutputBuffer(int)
   */
  @Nullable
  ByteBuffer getOutputBuffer(int index);

  /**
   * 提交输入缓冲区以进行解码。
   *
   * <p>{@code index} 必须是从之前调用 {@link #dequeueInputBufferIndex()} 获得的输入缓冲区索引。
   *
   * @see MediaCodec#queueInputBuffer
   */
  void queueInputBuffer(int index, int offset, int size, long presentationTimeUs, int flags);

  /**
   * 提交可能加密的输入缓冲区以进行解码。
   *
   * <p>{@code index} 必须是从之前调用 {@link #dequeueInputBufferIndex()} 获得的输入缓冲区索引。
   *
   * <p>此方法的行为类似于 {@link MediaCodec#queueSecureInputBuffer}，不同之处在于 {@code info} 的类型是 {@link CryptoInfo} 而不是 {@link android.media.MediaCodec.CryptoInfo}。
   *
   * @see MediaCodec#queueSecureInputBuffer
   */
  void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags);

  /**
   * 将缓冲区返回给 {@link MediaCodec}。如果 {@link MediaCodec} 配置了输出 Surface，将 {@code render} 设置为 {@code true} 会首先将缓冲区发送到输出 Surface。Surface 在不再使用/显示缓冲区后，会将其释放回编解码器。
   *
   * @see MediaCodec#releaseOutputBuffer(int, boolean)
   */
  void releaseOutputBuffer(int index, boolean render);

  /**
   * 更新输出缓冲区的 Surface 时间戳，并将其发送到 {@link MediaCodec} 以在输出 Surface 上渲染。如果 {@link MediaCodec} 未配置输出 Surface，此调用将简单地将缓冲区返回给 {@link MediaCodec}。
   *
   * @see MediaCodec#releaseOutputBuffer(int, long)
   */
  void releaseOutputBuffer(int index, long renderTimeStampNs);

  /** 刷新适配器和底层的 {@link MediaCodec}。 */
  void flush();

  /** 释放适配器和底层的 {@link MediaCodec}。 */
  void release();

  /**
   * 注册一个回调，当输出帧在输出 Surface 上渲染时调用。
   *
   * @see MediaCodec#setOnFrameRenderedListener
   */
  @RequiresApi(23)
  void setOnFrameRenderedListener(OnFrameRenderedListener listener, Handler handler);

  /**
   * 注册一个监听器，当输入或输出缓冲区可用时调用。
   *
   * <p>如果监听器未成功注册回调，则返回 false。
   *
   * @see MediaCodec.Callback#onInputBufferAvailable
   * @see MediaCodec.Callback#onOutputBufferAvailable
   * @return 监听器是否成功注册。
   */
  default boolean registerOnBufferAvailableListener(
      MediaCodecAdapter.OnBufferAvailableListener listener) {
    return false;
  }

  /**
   * 动态设置 {@link MediaCodec} 的输出 Surface。
   *
   * @see MediaCodec#setOutputSurface(Surface)
   */
  @RequiresApi(23)
  void setOutputSurface(Surface surface);

  /**
   * 分离当前的输出 Surface。
   *
   * @see MediaCodec#detachOutputSurface()
   */
  @RequiresApi(35)
  void detachOutputSurface();

  /**
   * 将其他参数更改传递给 {@link MediaCodec} 实例。
   *
   * @see MediaCodec#setParameters(Bundle)
   */
  void setParameters(Bundle params);

  /**
   * 指定缩放模式，如果在创建编解码器时指定了 Surface。
   *
   * @see MediaCodec#setVideoScalingMode(int)
   */
  void setVideoScalingMode(@C.VideoScalingMode int scalingMode);

  /** 适配器在使用前是否需要重新配置。 */
  boolean needsReconfiguration();

  /**
   * 返回有关当前编解码器实例的指标数据。
   *
   * @see MediaCodec#getMetrics()
   */
  @RequiresApi(26)
  PersistableBundle getMetrics();
}