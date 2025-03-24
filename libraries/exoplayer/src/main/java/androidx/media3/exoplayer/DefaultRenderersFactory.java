package androidx.media3.exoplayer;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.media.MediaCodec;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer;
import androidx.media3.exoplayer.image.ImageDecoder;
import androidx.media3.exoplayer.image.ImageRenderer;
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.metadata.MetadataOutput;
import androidx.media3.exoplayer.metadata.MetadataRenderer;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.exoplayer.video.spherical.CameraMotionRenderer;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.util.ArrayList;

/** 默认的 {@link RenderersFactory} 实现。 */
@UnstableApi
public class DefaultRenderersFactory implements RenderersFactory {

  /**
   * 视频渲染器可以尝试无缝加入正在进行的播放的默认最大持续时间。
   */
  public static final long DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS = 5000;

  /**
   * 使用扩展渲染器的模式。可以是 {@link #EXTENSION_RENDERER_MODE_OFF}、{@link
   * #EXTENSION_RENDERER_MODE_ON} 或 {@link #EXTENSION_RENDERER_MODE_PREFER} 之一。
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({EXTENSION_RENDERER_MODE_OFF, EXTENSION_RENDERER_MODE_ON, EXTENSION_RENDERER_MODE_PREFER})
  public @interface ExtensionRendererMode {}

  /** 不允许使用扩展渲染器。 */
  public static final int EXTENSION_RENDERER_MODE_OFF = 0;

  /**
   * 允许使用扩展渲染器。扩展渲染器在相同类型的核心渲染器之后索引。因此，如果 {@link TrackSelector} 更喜欢第一个合适的渲染器，
   * 那么在两者都能播放给定轨道的情况下，它将优先使用核心渲染器而不是扩展渲染器。
   */
  public static final int EXTENSION_RENDERER_MODE_ON = 1;

  /**
   * 允许使用扩展渲染器。扩展渲染器在相同类型的核心渲染器之前索引。因此，如果 {@link TrackSelector} 更喜欢第一个合适的渲染器，
   * 那么在两者都能播放给定轨道的情况下，它将优先使用扩展渲染器而不是核心渲染器。
   */
  public static final int EXTENSION_RENDERER_MODE_PREFER = 2;

  /**
   * {@link VideoRendererEventListener#onDroppedFrames(int, long)} 调用之间可以丢弃的最大帧数。
   */
  public static final int MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY = 50;

  private static final String TAG = "DefaultRenderersFactory";

  private final Context context;
  private final DefaultMediaCodecAdapterFactory codecAdapterFactory;
  private @ExtensionRendererMode int extensionRendererMode;
  private long allowedVideoJoiningTimeMs;
  private boolean enableDecoderFallback;
  private MediaCodecSelector mediaCodecSelector;
  private boolean enableFloatOutput;
  private boolean enableAudioTrackPlaybackParams;

  /**
   * @param context 一个 {@link Context}。
   */
  public DefaultRenderersFactory(Context context) {
    this.context = context;
    codecAdapterFactory = new DefaultMediaCodecAdapterFactory(context);
    extensionRendererMode = EXTENSION_RENDERER_MODE_OFF;
    allowedVideoJoiningTimeMs = DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS;
    mediaCodecSelector = MediaCodecSelector.DEFAULT;
  }

  /**
   * 设置扩展渲染器模式，该模式决定是否以及如何使用可用的扩展渲染器。请注意，扩展必须包含在应用程序构建中才能被视为可用。
   *
   * <p>默认值为 {@link #EXTENSION_RENDERER_MODE_OFF}。
   *
   * @param extensionRendererMode 扩展渲染器模式。
   * @return 此工厂，方便链式调用。
   */
  @CanIgnoreReturnValue
  public final DefaultRenderersFactory setExtensionRendererMode(
      @ExtensionRendererMode int extensionRendererMode) {
    this.extensionRendererMode = extensionRendererMode;
    return this;
  }

  /**
   * 启用 {@link androidx.media3.exoplayer.mediacodec.MediaCodecRenderer} 实例以异步模式操作其 {@link MediaCodec} 并执行异步队列。
   *
   * <p>此功能只能在 API 版本 >= 23 的设备上启用。对于 API 版本较旧的设备，此方法无效。
   *
   * @return 此工厂，方便链式调用。
   */
  @CanIgnoreReturnValue
  public final DefaultRenderersFactory forceEnableMediaCodecAsynchronousQueueing() {
    codecAdapterFactory.forceEnableAsynchronous();
    return this;
  }

  /**
   * 禁用 {@link androidx.media3.exoplayer.mediacodec.MediaCodecRenderer} 实例以异步模式操作其 {@link MediaCodec} 并执行异步队列。
   * {@link MediaCodec} 实例将以同步模式操作。
   *
   * @return 此工厂，方便链式调用。
   */
  @CanIgnoreReturnValue
  public final DefaultRenderersFactory forceDisableMediaCodecAsynchronousQueueing() {
    codecAdapterFactory.forceDisableAsynchronous();
    return this;
  }

  /**
   * 设置是否在 API 34 及以上版本中启用 {@link MediaCodec#CONFIGURE_FLAG_USE_CRYPTO_ASYNC}，当以异步模式操作编解码器时。
   *
   * <p>此方法是实验性的。其默认值可能会更改，或者它可能会在未来的版本中重命名或移除。
   */
  @CanIgnoreReturnValue
  public final DefaultRenderersFactory experimentalSetMediaCodecAsyncCryptoFlagEnabled(
      boolean enableAsyncCryptoFlag) {
    codecAdapterFactory.experimentalSetAsyncCryptoFlagEnabled(enableAsyncCryptoFlag);
    return this;
  }

  /**
   * 设置是否在解码器初始化失败时启用回退到较低优先级的解码器。这可能会导致使用效率较低或较慢的解码器。
   *
   * @param enableDecoderFallback 是否在解码器初始化失败时启用回退到较低优先级的解码器。
   * @return 此工厂，方便链式调用。
   */
  @CanIgnoreReturnValue
  public final DefaultRenderersFactory setEnableDecoderFallback(boolean enableDecoderFallback) {
    this.enableDecoderFallback = enableDecoderFallback;
    return this;
  }

  /**
   * 设置 {@link MediaCodec} 基于渲染器使用的 {@link MediaCodecSelector}。
   *
   * <p>默认值为 {@link MediaCodecSelector#DEFAULT}。
   *
   * @param mediaCodecSelector {@link MediaCodecSelector}。
   * @return 此工厂，方便链式调用。
   */
  @CanIgnoreReturnValue
  public final DefaultRenderersFactory setMediaCodecSelector(
      MediaCodecSelector mediaCodecSelector) {
    this.mediaCodecSelector = mediaCodecSelector;
    return this;
  }

  /**
   * 设置是否在可能的情况下输出浮点音频。
   *
   * <p>启用浮点输出会禁用音频处理，但可能允许更高质量的音频输出。
   *
   * <p>默认值为 {@code false}。
   *
   * @param enableFloatOutput 是否启用浮点音频输出（如果可用）。
   * @return 此工厂，方便链式调用。
   */
  @CanIgnoreReturnValue
  public final DefaultRenderersFactory setEnableAudioFloatOutput(boolean enableFloatOutput) {
    this.enableFloatOutput = enableFloatOutput;
    return this;
  }

  /**
   * 设置是否启用使用 {@link android.media.AudioTrack#setPlaybackParams(PlaybackParams)} 设置播放速度，
   * 该功能从 API 级别 23 开始支持，而不是使用应用级的音频速度调整。此设置在 API 级别 23 之前的构建中无效（在所有情况下都将使用应用级的速度调整）。
   *
   * <p>如果启用并支持，新的播放速度设置将更快生效，因为它们是在音频混音器中应用的，而不是在将数据写入轨道时应用的。
   *
   * <p>在使用此模式时，最大支持的播放速度受音频轨道缓冲区大小的限制。如果请求的速度不受支持，播放器的事件监听器将在设置播放速度时被通知两次，
   * 第一次是请求的速度，然后是旧的播放速度，反映请求的速度不受支持的事实。
   *
   * @param enableAudioTrackPlaybackParams 是否启用使用 {@link android.media.AudioTrack#setPlaybackParams(PlaybackParams)} 设置播放速度。
   * @return 此工厂，方便链式调用。
   */
  @CanIgnoreReturnValue
  public final DefaultRenderersFactory setEnableAudioTrackPlaybackParams(
      boolean enableAudioTrackPlaybackParams) {
    this.enableAudioTrackPlaybackParams = enableAudioTrackPlaybackParams;
    return this;
  }

  /**
   * 设置视频渲染器可以尝试无缝加入正在进行的播放的最大持续时间。
   *
   * <p>默认值为 {@link #DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS}。
   *
   * @param allowedVideoJoiningTimeMs 视频渲染器可以尝试无缝加入正在进行的播放的最大持续时间，以毫秒为单位。
   * @return 此工厂，方便链式调用。
   */
  @CanIgnoreReturnValue
  public final DefaultRenderersFactory setAllowedVideoJoiningTimeMs(
      long allowedVideoJoiningTimeMs) {
    this.allowedVideoJoiningTimeMs = allowedVideoJoiningTimeMs;
    return this;
  }

  @Override
  public Renderer[] createRenderers(
      Handler eventHandler,
      VideoRendererEventListener videoRendererEventListener,
      AudioRendererEventListener audioRendererEventListener,
      TextOutput textRendererOutput,
      MetadataOutput metadataRendererOutput) {
    ArrayList<Renderer> renderersList = new ArrayList<>();
    buildVideoRenderers(
        context,
        extensionRendererMode,
        mediaCodecSelector,
        enableDecoderFallback,
        eventHandler,
        videoRendererEventListener,
        allowedVideoJoiningTimeMs,
        renderersList);
    @Nullable
    AudioSink audioSink =
        buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams);
    if (audioSink != null) {
      buildAudioRenderers(
          context,
          extensionRendererMode,
          mediaCodecSelector,
          enableDecoderFallback,
          audioSink,
          eventHandler,
          audioRendererEventListener,
          renderersList);
    }
    buildTextRenderers(
        context,
        textRendererOutput,
        eventHandler.getLooper(),
        extensionRendererMode,
        renderersList);
    buildMetadataRenderers(
        context,
        metadataRendererOutput,
        eventHandler.getLooper(),
        extensionRendererMode,
        renderersList);
    buildCameraMotionRenderers(context, extensionRendererMode, renderersList);
    buildImageRenderers(renderersList);
    buildMiscellaneousRenderers(context, eventHandler, extensionRendererMode, renderersList);
    return renderersList.toArray(new Renderer[0]);
  }
  /**
   * 构建供播放器使用的视频渲染器。
   *
   * @param context 与播放器关联的 {@link Context}。
   * @param extensionRendererMode 扩展渲染器模式。
   * @param mediaCodecSelector 解码器选择器。
   * @param enableDecoderFallback 是否在解码器初始化失败时启用回退到较低优先级的解码器。
   *     这可能会导致使用比主解码器更慢/效率更低的解码器。
   * @param eventHandler 与主线程的 Looper 关联的处理程序。
   * @param eventListener 事件监听器。
   * @param allowedVideoJoiningTimeMs 视频渲染器可以尝试无缝加入正在进行的播放的最大持续时间（以毫秒为单位）。
   * @param out 用于追加构建的渲染器的数组。
   */
  protected void buildVideoRenderers(
      Context context,
      @ExtensionRendererMode int extensionRendererMode,
      MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      Handler eventHandler,
      VideoRendererEventListener eventListener,
      long allowedVideoJoiningTimeMs,
      ArrayList<Renderer> out) {
    MediaCodecVideoRenderer videoRenderer =
        new MediaCodecVideoRenderer(
            context,
            getCodecAdapterFactory(),
            mediaCodecSelector,
            allowedVideoJoiningTimeMs,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    out.add(videoRenderer);

    if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
      return;
    }
    int extensionRendererIndex = out.size();
    if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
      extensionRendererIndex--;
    }

    try {
      // 使用完整的类名作为构造函数参数，以便在它们移动时触发 LINT 规则。
      Class<?> clazz = Class.forName("androidx.media3.decoder.vp9.LibvpxVideoRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              long.class,
              android.os.Handler.class,
              androidx.media3.exoplayer.video.VideoRendererEventListener.class,
              int.class);
      Renderer renderer =
          (Renderer)
              constructor.newInstance(
                  allowedVideoJoiningTimeMs,
                  eventHandler,
                  eventListener,
                  MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "已加载 LibvpxVideoRenderer。");
    } catch (ClassNotFoundException e) {
      // 如果应用程序构建时未包含扩展，则为预期情况。
    } catch (Exception e) {
      // 扩展存在，但实例化失败。
      throw new IllegalStateException("实例化 VP9 扩展时出错", e);
    }

    try {
      // 使用完整的类名作为构造函数参数，以便在它们移动时触发 LINT 规则。
      Class<?> clazz = Class.forName("androidx.media3.decoder.av1.Libgav1VideoRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              long.class,
              android.os.Handler.class,
              androidx.media3.exoplayer.video.VideoRendererEventListener.class,
              int.class);
      Renderer renderer =
          (Renderer)
              constructor.newInstance(
                  allowedVideoJoiningTimeMs,
                  eventHandler,
                  eventListener,
                  MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "已加载 Libgav1VideoRenderer。");
    } catch (ClassNotFoundException e) {
      // 如果应用程序构建时未包含扩展，则为预期情况。
    } catch (Exception e) {
      // 扩展存在，但实例化失败。
      throw new IllegalStateException("实例化 AV1 扩展时出错", e);
    }

    try {
      // 使用完整的类名作为构造函数参数，以便在它们移动时触发 LINT 规则。
      Class<?> clazz =
          Class.forName("androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              long.class,
              android.os.Handler.class,
              androidx.media3.exoplayer.video.VideoRendererEventListener.class,
              int.class);
      Renderer renderer =
          (Renderer)
              constructor.newInstance(
                  allowedVideoJoiningTimeMs,
                  eventHandler,
                  eventListener,
                  MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "已加载 FfmpegVideoRenderer。");
    } catch (ClassNotFoundException e) {
      // 如果应用程序构建时未包含扩展，则为预期情况。
    } catch (Exception e) {
      // 扩展存在，但实例化失败。
      throw new IllegalStateException("实例化 FFmpeg 扩展时出错", e);
    }
  }

  /**
   * 构建供播放器使用的音频渲染器。
   *
   * @param context 与播放器关联的 {@link Context}。
   * @param extensionRendererMode 扩展渲染器模式。
   * @param mediaCodecSelector 解码器选择器。
   * @param enableDecoderFallback 是否在解码器初始化失败时启用回退到较低优先级的解码器。
   *     这可能会导致使用比主解码器更慢/效率更低的解码器。
   * @param audioSink 渲染器将输出到的音频接收器。
   * @param eventHandler 用于调用事件监听器和输出的处理程序。
   * @param eventListener 事件监听器。
   * @param out 用于追加构建的渲染器的数组。
   */
  protected void buildAudioRenderers(
      Context context,
      @ExtensionRendererMode int extensionRendererMode,
      MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      AudioSink audioSink,
      Handler eventHandler,
      AudioRendererEventListener eventListener,
      ArrayList<Renderer> out) {
    MediaCodecAudioRenderer audioRenderer =
        new MediaCodecAudioRenderer(
            context,
            getCodecAdapterFactory(),
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            audioSink);
    out.add(audioRenderer);

    if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
      return;
    }
    int extensionRendererIndex = out.size();
    if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
      extensionRendererIndex--;
    }

    try {
      // 使用完整的类名作为构造函数参数，以便在它们移动时触发 LINT 规则。
      Class<?> clazz = Class.forName("androidx.media3.decoder.midi.MidiRenderer");
      Constructor<?> constructor = clazz.getConstructor(Context.class);
      Renderer renderer = (Renderer) constructor.newInstance(context);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "已加载 MidiRenderer。");
    } catch (ClassNotFoundException e) {
      // 如果应用程序构建时未包含扩展，则为预期情况。
    } catch (Exception e) {
      // 扩展存在，但实例化失败。
      throw new IllegalStateException("实例化 MIDI 扩展时出错", e);
    }

    try {
      // 使用完整的类名作为构造函数参数，以便在它们移动时触发 LINT 规则。
      Class<?> clazz = Class.forName("androidx.media3.decoder.opus.LibopusAudioRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              android.os.Handler.class,
              androidx.media3.exoplayer.audio.AudioRendererEventListener.class,
              androidx.media3.exoplayer.audio.AudioSink.class);
      Renderer renderer =
          (Renderer) constructor.newInstance(eventHandler, eventListener, audioSink);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "已加载 LibopusAudioRenderer。");
    } catch (ClassNotFoundException e) {
      // 如果应用程序构建时未包含扩展，则为预期情况。
    } catch (Exception e) {
      // 扩展存在，但实例化失败。
      throw new IllegalStateException("实例化 Opus 扩展时出错", e);
    }

    try {
      // 使用完整的类名作为构造函数参数，以便在它们移动时触发 LINT 规则。
      Class<?> clazz = Class.forName("androidx.media3.decoder.flac.LibflacAudioRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              android.os.Handler.class,
              androidx.media3.exoplayer.audio.AudioRendererEventListener.class,
              androidx.media3.exoplayer.audio.AudioSink.class);
      Renderer renderer =
          (Renderer) constructor.newInstance(eventHandler, eventListener, audioSink);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "已加载 LibflacAudioRenderer。");
    } catch (ClassNotFoundException e) {
      // 如果应用程序构建时未包含扩展，则为预期情况。
    } catch (Exception e) {
      // 扩展存在，但实例化失败。
      throw new IllegalStateException("实例化 FLAC 扩展时出错", e);
    }

    try {
      // 使用完整的类名作为构造函数参数，以便在它们移动时触发 LINT 规则。
      Class<?> clazz = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              android.os.Handler.class,
              androidx.media3.exoplayer.audio.AudioRendererEventListener.class,
              androidx.media3.exoplayer.audio.AudioSink.class);
      Renderer renderer =
          (Renderer) constructor.newInstance(eventHandler, eventListener, audioSink);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "已加载 FfmpegAudioRenderer。");
    } catch (ClassNotFoundException e) {
      // 如果应用程序构建时未包含扩展，则为预期情况。
    } catch (Exception e) {
      // 扩展存在，但实例化失败。
      throw new IllegalStateException("实例化 FFmpeg 扩展时出错", e);
    }

    try {
      // 使用完整的类名作为构造函数参数，以便在它们移动时触发 LINT 规则。
      Class<?> clazz = Class.forName("androidx.media3.decoder.iamf.LibiamfAudioRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              Context.class,
              android.os.Handler.class,
              androidx.media3.exoplayer.audio.AudioRendererEventListener.class,
              androidx.media3.exoplayer.audio.AudioSink.class);
      Renderer renderer =
          (Renderer) constructor.newInstance(context, eventHandler, eventListener, audioSink);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "已加载 LibiamfAudioRenderer。");
    } catch (ClassNotFoundException e) {
      // 如果应用程序构建时未包含扩展，则为预期情况。
    } catch (Exception e) {
      // 扩展存在，但实例化失败。
      throw new IllegalStateException("实例化 IAMF 扩展时出错", e);
    }
  }
  /**
   * 构建供播放器使用的文本渲染器。
   *
   * @param context 与播放器关联的 {@link Context}。
   * @param output 渲染器的输出。
   * @param outputLooper 与调用输出的线程关联的 Looper。
   * @param extensionRendererMode 扩展渲染器模式。
   * @param out 用于追加构建的渲染器的数组。
   */
  protected void buildTextRenderers(
      Context context,
      TextOutput output,
      Looper outputLooper,
      @ExtensionRendererMode int extensionRendererMode,
      ArrayList<Renderer> out) {
    out.add(new TextRenderer(output, outputLooper));
  }

  /**
   * 构建供播放器使用的元数据渲染器。
   *
   * @param context 与播放器关联的 {@link Context}。
   * @param output 渲染器的输出。
   * @param outputLooper 与调用输出的线程关联的 Looper。
   * @param extensionRendererMode 扩展渲染器模式。
   * @param out 用于追加构建的渲染器的数组。
   */
  protected void buildMetadataRenderers(
      Context context,
      MetadataOutput output,
      Looper outputLooper,
      @ExtensionRendererMode int extensionRendererMode,
      ArrayList<Renderer> out) {
    out.add(new MetadataRenderer(output, outputLooper));
  }

  /**
   * 构建供播放器使用的相机运动渲染器。
   *
   * @param context 与播放器关联的 {@link Context}。
   * @param extensionRendererMode 扩展渲染器模式。
   * @param out 用于追加构建的渲染器的数组。
   */
  protected void buildCameraMotionRenderers(
      Context context, @ExtensionRendererMode int extensionRendererMode, ArrayList<Renderer> out) {
    out.add(new CameraMotionRenderer());
  }

  /**
   * 构建供播放器使用的图像渲染器。
   *
   * <p>默认情况下，{@link ImageRenderer} 的 {@code ImageOutput} 设置为 null，{@link
   * ImageDecoder.Factory} 设置为 {@code ImageDecoder.Factory.DEFAULT}。
   *
   * @param out 用于追加构建的渲染器的数组。
   */
  protected void buildImageRenderers(ArrayList<Renderer> out) {
    out.add(new ImageRenderer(getImageDecoderFactory(), /* imageOutput= */ null));
  }

  /**
   * 构建供播放器使用的任何其他渲染器。
   *
   * @param context 与播放器关联的 {@link Context}。
   * @param eventHandler 用于调用事件监听器和输出的处理程序。
   * @param extensionRendererMode 扩展渲染器模式。
   * @param out 用于追加构建的渲染器的数组。
   */
  protected void buildMiscellaneousRenderers(
      Context context,
      Handler eventHandler,
      @ExtensionRendererMode int extensionRendererMode,
      ArrayList<Renderer> out) {
    // 默认不执行任何操作。
  }

  /**
   * 构建音频渲染器将输出到的 {@link AudioSink}。
   *
   * @param context 与播放器关联的 {@link Context}。
   * @param enableFloatOutput 是否启用浮点音频输出（如果可用）。
   * @param enableAudioTrackPlaybackParams 是否启用使用 {@link
   *     android.media.AudioTrack#setPlaybackParams(PlaybackParams)} 设置播放速度（如果支持）。
   * @return 音频渲染器将输出到的 {@link AudioSink}。如果不需要音频渲染器，则可能为 {@code null}。
   *     如果返回 {@code null}，则不会调用 {@link #buildAudioRenderers}。
   */
  @Nullable
  protected AudioSink buildAudioSink(
      Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams) {
    return new DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .build();
  }

  /**
   * 返回在创建 {@link androidx.media3.exoplayer.mediacodec.MediaCodecRenderer} 实例时将使用的 {@link MediaCodecAdapter.Factory}。
   */
  protected MediaCodecAdapter.Factory getCodecAdapterFactory() {
    return codecAdapterFactory;
  }

  /** 返回用于构建图像渲染器的 {@link ImageDecoder.Factory}。 */
  protected ImageDecoder.Factory getImageDecoderFactory() {
    return ImageDecoder.Factory.DEFAULT;
  }
}
