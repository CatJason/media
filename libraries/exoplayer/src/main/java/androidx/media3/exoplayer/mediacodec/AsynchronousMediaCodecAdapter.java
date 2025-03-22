/*
 * 版权所有 (C) 2019 The Android Open Source Project
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

import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.view.Surface;
import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoInfo;
import com.google.common.base.Supplier;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;

/**
 * 一个 {@link MediaCodecAdapter}，它以异步模式操作底层的 {@link MediaCodec}，在内部管理的专用线程上路由 {@link MediaCodec.Callback} 回调，
 * 并以异步方式排队输入缓冲区。
 */
@RequiresApi(23)
/* package */ final class AsynchronousMediaCodecAdapter implements MediaCodecAdapter {

  /** 用于创建 {@link AsynchronousMediaCodecAdapter} 实例的工厂。 */
  public static final class Factory implements MediaCodecAdapter.Factory {
    private final Supplier<HandlerThread> callbackThreadSupplier;
    private final Supplier<HandlerThread> queueingThreadSupplier;

    private boolean enableSynchronousBufferQueueingWithAsyncCryptoFlag;

    /**
     * 创建一个用于 {@link AsynchronousMediaCodecAdapter} 实例的工厂。
     *
     * @param trackType {@link C#TRACK_TYPE_AUDIO} 或 {@link C#TRACK_TYPE_VIDEO} 之一。用于为内部线程命名。
     */
    public Factory(@C.TrackType int trackType) {
      this(
          /* callbackThreadSupplier= */ () ->
              new HandlerThread(createCallbackThreadLabel(trackType)),
          /* queueingThreadSupplier= */ () ->
              new HandlerThread(createQueueingThreadLabel(trackType)));
    }

    @VisibleForTesting
      /* package */ Factory(
        Supplier<HandlerThread> callbackThreadSupplier,
        Supplier<HandlerThread> queueingThreadSupplier) {
      this.callbackThreadSupplier = callbackThreadSupplier;
      this.queueingThreadSupplier = queueingThreadSupplier;
      enableSynchronousBufferQueueingWithAsyncCryptoFlag = false;
    }

    /**
     * 设置是否在 API 34 及以上版本上启用 {@link MediaCodec#CONFIGURE_FLAG_USE_CRYPTO_ASYNC}。
     *
     * <p>此方法是实验性的。其默认值可能会更改，或者它可能会在未来的版本中重命名或移除。
     */
    public void experimentalSetAsyncCryptoFlagEnabled(boolean enableAsyncCryptoFlag) {
      enableSynchronousBufferQueueingWithAsyncCryptoFlag = enableAsyncCryptoFlag;
    }

    @Override
    public AsynchronousMediaCodecAdapter createAdapter(Configuration configuration)
        throws IOException {
      String codecName = configuration.codecInfo.name;
      @Nullable AsynchronousMediaCodecAdapter codecAdapter = null;
      @Nullable MediaCodec codec = null;
      try {
        TraceUtil.beginSection("createCodec:" + codecName);
        codec = MediaCodec.createByCodecName(codecName);
        int flags = 0;
        MediaCodecBufferEnqueuer bufferEnqueuer;
        if (enableSynchronousBufferQueueingWithAsyncCryptoFlag
            && useSynchronousBufferQueueingWithAsyncCryptoFlag(configuration.format)) {
          bufferEnqueuer = new SynchronousMediaCodecBufferEnqueuer(codec);
          flags |= MediaCodec.CONFIGURE_FLAG_USE_CRYPTO_ASYNC;
        } else {
          bufferEnqueuer =
              new AsynchronousMediaCodecBufferEnqueuer(codec, queueingThreadSupplier.get());
        }
        codecAdapter =
            new AsynchronousMediaCodecAdapter(
                codec,
                callbackThreadSupplier.get(),
                bufferEnqueuer,
                configuration.loudnessCodecController);
        TraceUtil.endSection();
        if (configuration.surface == null
            && configuration.codecInfo.detachedSurfaceSupported
            && Util.SDK_INT >= 35) {
          flags |= MediaCodec.CONFIGURE_FLAG_DETACHED_SURFACE;
        }
        codecAdapter.initialize(
            configuration.mediaFormat, configuration.surface, configuration.crypto, flags);
        return codecAdapter;
      } catch (Exception e) {
        if (codecAdapter != null) {
          codecAdapter.release();
        } else if (codec != null) {
          codec.release();
        }
        throw e;
      }
    }

    @ChecksSdkIntAtLeast(api = 34)
    private static boolean useSynchronousBufferQueueingWithAsyncCryptoFlag(Format format) {
      if (Util.SDK_INT < 34) {
        return false;
      }
      // CONFIGURE_FLAG_USE_CRYPTO_ASYNC 仅在 API 35+ 上对音频有效（参见 b/316565675）。
      return Util.SDK_INT >= 35 || MimeTypes.isVideo(format.sampleMimeType);
    }
  }

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({STATE_CREATED, STATE_INITIALIZED, STATE_SHUT_DOWN})
  private @interface State {}

  private static final int STATE_CREATED = 0;
  private static final int STATE_INITIALIZED = 1;
  private static final int STATE_SHUT_DOWN = 2;

  private final MediaCodec codec;
  private final AsynchronousMediaCodecCallback asynchronousMediaCodecCallback;
  private final MediaCodecBufferEnqueuer bufferEnqueuer;
  @Nullable private final LoudnessCodecController loudnessCodecController;

  private boolean codecReleased;
  private @State int state;

  private AsynchronousMediaCodecAdapter(
      MediaCodec codec,
      HandlerThread callbackThread,
      MediaCodecBufferEnqueuer bufferEnqueuer,
      @Nullable LoudnessCodecController loudnessCodecController) {
    this.codec = codec;
    this.asynchronousMediaCodecCallback = new AsynchronousMediaCodecCallback(callbackThread);
    this.bufferEnqueuer = bufferEnqueuer;
    this.loudnessCodecController = loudnessCodecController;
    this.state = STATE_CREATED;
  }

  private void initialize(
      @Nullable MediaFormat mediaFormat,
      @Nullable Surface surface,
      @Nullable MediaCrypto crypto,
      int flags) {
    asynchronousMediaCodecCallback.initialize(codec);
    TraceUtil.beginSection("configureCodec");
    codec.configure(mediaFormat, surface, crypto, flags);
    TraceUtil.endSection();
    bufferEnqueuer.start();
    TraceUtil.beginSection("startCodec");
    codec.start();
    TraceUtil.endSection();
    if (Util.SDK_INT >= 35 && loudnessCodecController != null) {
      loudnessCodecController.addMediaCodec(codec);
    }
    state = STATE_INITIALIZED;
  }

  @Override
  public boolean needsReconfiguration() {
    return false;
  }

  @Override
  public void queueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flags) {
    bufferEnqueuer.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
  }

  @Override
  public void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
    bufferEnqueuer.queueSecureInputBuffer(index, offset, info, presentationTimeUs, flags);
  }

  @Override
  public void releaseOutputBuffer(int index, boolean render) {
    codec.releaseOutputBuffer(index, render);
  }

  @Override
  public void releaseOutputBuffer(int index, long renderTimeStampNs) {
    codec.releaseOutputBuffer(index, renderTimeStampNs);
  }

  @Override
  public int dequeueInputBufferIndex() {
    bufferEnqueuer.maybeThrowException();
    return asynchronousMediaCodecCallback.dequeueInputBufferIndex();
  }

  @Override
  public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    bufferEnqueuer.maybeThrowException();
    return asynchronousMediaCodecCallback.dequeueOutputBufferIndex(bufferInfo);
  }

  @Override
  public MediaFormat getOutputFormat() {
    return asynchronousMediaCodecCallback.getOutputFormat();
  }

  @Override
  @Nullable
  public ByteBuffer getInputBuffer(int index) {
    return codec.getInputBuffer(index);
  }

  @Override
  @Nullable
  public ByteBuffer getOutputBuffer(int index) {
    return codec.getOutputBuffer(index);
  }

  @Override
  public void flush() {
    // 调用顺序很重要：
    // 1. 刷新 bufferEnqueuer 以停止排队输入缓冲区。
    // 2. 刷新 codec 以停止生成可用的输入/输出缓冲区。
    // 3. 刷新回调以丢弃正在进行的回调。
    // 4. 启动 codec。异步回调将丢弃挂起的回调，我们现在可以启动 codec。
    bufferEnqueuer.flush();
    codec.flush();
    asynchronousMediaCodecCallback.flush();
    codec.start();
  }

  @Override
  public void release() {
    try {
      if (state == STATE_INITIALIZED) {
        bufferEnqueuer.shutdown();
        asynchronousMediaCodecCallback.shutdown();
      }
      state = STATE_SHUT_DOWN;
    } finally {
      if (!codecReleased) {
        try {
          // 在释放 codec 之前停止它可以解决 API 30、31 和 32 上的一个 bug，
          // 在这些版本中，MediaCodec.release() 在完全分离 Surface 之前过早返回，
          // 导致后续使用相同 Surface 的 MediaCodec.configure() 调用失败。
          // 参见 https://github.com/google/ExoPlayer/issues/8696 和 b/191966399。
          if (Util.SDK_INT >= 30 && Util.SDK_INT < 33) {
            codec.stop();
          }
        } finally {
          if (Util.SDK_INT >= 35 && loudnessCodecController != null) {
            loudnessCodecController.removeMediaCodec(codec);
          }
          codec.release();
          codecReleased = true;
        }
      }
    }
  }

  @Override
  public void setOnFrameRenderedListener(OnFrameRenderedListener listener, Handler handler) {
    codec.setOnFrameRenderedListener(
        (codec, presentationTimeUs, nanoTime) ->
            listener.onFrameRendered(
                AsynchronousMediaCodecAdapter.this, presentationTimeUs, nanoTime),
        handler);
  }

  @Override
  public boolean registerOnBufferAvailableListener(OnBufferAvailableListener listener) {
    asynchronousMediaCodecCallback.setOnBufferAvailableListener(listener);
    return true;
  }

  @Override
  public void setOutputSurface(Surface surface) {
    codec.setOutputSurface(surface);
  }

  @RequiresApi(35)
  @Override
  public void detachOutputSurface() {
    codec.detachOutputSurface();
  }

  @Override
  public void setParameters(Bundle params) {
    bufferEnqueuer.setParameters(params);
  }

  @Override
  public void setVideoScalingMode(@C.VideoScalingMode int scalingMode) {
    codec.setVideoScalingMode(scalingMode);
  }

  @Override
  @RequiresApi(26)
  public PersistableBundle getMetrics() {
    return codec.getMetrics();
  }

  @VisibleForTesting
    /* package */ void onError(MediaCodec.CodecException error) {
    asynchronousMediaCodecCallback.onError(codec, error);
  }

  @VisibleForTesting
    /* package */ void onOutputFormatChanged(MediaFormat format) {
    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, format);
  }

  private static String createCallbackThreadLabel(@C.TrackType int trackType) {
    return createThreadLabel(trackType, /* prefix= */ "ExoPlayer:MediaCodecAsyncAdapter:");
  }

  private static String createQueueingThreadLabel(@C.TrackType int trackType) {
    return createThreadLabel(trackType, /* prefix= */ "ExoPlayer:MediaCodecQueueingThread:");
  }

  private static String createThreadLabel(@C.TrackType int trackType, String prefix) {
    StringBuilder labelBuilder = new StringBuilder(prefix);
    if (trackType == C.TRACK_TYPE_AUDIO) {
      labelBuilder.append("Audio");
    } else if (trackType == C.TRACK_TYPE_VIDEO) {
      labelBuilder.append("Video");
    } else {
      labelBuilder.append("Unknown(").append(trackType).append(")");
    }
    return labelBuilder.toString();
  }
}