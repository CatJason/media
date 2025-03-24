package androidx.media3.exoplayer;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.AuxEffectInfo;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.analytics.AnalyticsCollector;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.analytics.DefaultAnalyticsCollector;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer;
import androidx.media3.exoplayer.image.ImageOutput;
import androidx.media3.exoplayer.metadata.MetadataRenderer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ShuffleOrder;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelectionArray;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;
import androidx.media3.exoplayer.video.spherical.CameraMotionListener;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;

/**
 * 一个可扩展的媒体播放器，用于播放 {@link MediaSource}。实例可以通过 {@link Builder} 获取。
 *
 * <h2>播放器组件</h2>
 *
 * <p>ExoPlayer 的设计使其对播放的媒体类型、存储方式和位置以及渲染方式做出很少的假设（因此施加很少的限制）。
 * ExoPlayer 的实现并不直接负责媒体的加载和渲染，而是将这些工作委托给在创建播放器或准备播放时注入的组件。
 * 所有 ExoPlayer 实现共有的组件包括：
 *
 * <ul>
 *   <li><b>{@link MediaSource MediaSources}</b>：定义要播放的媒体，加载媒体，并从中读取加载的媒体。
 *   MediaSources 由 {@link MediaItem MediaItems} 通过注入到播放器 {@link Builder#setMediaSourceFactory Builder} 的 {@link MediaSource.Factory} 创建，也可以通过 {@link #setMediaSource(MediaSource)} 等方法直接添加。
 *   库提供了用于渐进式媒体文件、DASH、SmoothStreaming 和 HLS 的 {@link DefaultMediaSourceFactory}，还包括旁加载字幕文件和剪辑媒体的功能。
 *   <li><b>{@link Renderer}</b>：渲染媒体的各个组件。库为常见媒体类型提供了默认实现（{@link MediaCodecVideoRenderer}、{@link MediaCodecAudioRenderer}、{@link TextRenderer} 和 {@link MetadataRenderer}）。
 *   Renderer 从正在播放的 MediaSource 中消费媒体。
 *   Renderer 在创建播放器时注入。可以通过调用 {@link #getRendererCount()} 和 {@link #getRendererType(int)} 获取 Renderer 的数量及其各自的轨道类型。
 *   <li><b>{@link TrackSelector}</b>：选择由 MediaSource 提供的轨道，以供每个可用的 Renderer 消费。库提供了适用于大多数用例的默认实现（{@link DefaultTrackSelector}）。
 *   TrackSelector 在创建播放器时注入。
 *   <li><b>{@link LoadControl}</b>：控制 MediaSource 何时缓冲更多媒体，以及缓冲多少媒体。
 *   库提供了适用于大多数用例的默认实现（{@link DefaultLoadControl}）。
 *   LoadControl 在创建播放器时注入。
 * </ul>
 *
 * <p>ExoPlayer 可以使用库提供的默认组件构建，但如果需要非标准行为，也可以使用自定义实现构建。
 * 例如，可以注入自定义的 LoadControl 以更改播放器的缓冲策略，或者注入自定义的 Renderer 以添加对 Android 不原生支持的视频编解码器的支持。
 *
 * <p>注入实现播放器功能组件的概念贯穿整个库。上面列出的默认组件实现将工作委托给进一步注入的组件。
 * 这允许许多子组件可以单独替换为自定义实现。
 * 例如，默认的 MediaSource 实现需要通过其构造函数注入一个或多个 {@link DataSource} 工厂。
 * 通过提供自定义工厂，可以从非标准源或通过不同的网络堆栈加载数据。
 *
 * <h2>线程模型</h2>
 *
 * <p>下图展示了 ExoPlayer 的线程模型。
 *
 * <p style="align:center"><img
 * src="https://developer.android.com/static/images/reference/androidx/media3/exoplayer/exoplayer-threading-model.svg"
 * alt="ExoPlayer 的线程模型">
 *
 * <ul>
 *   <li>除非另有说明，否则必须在单个应用程序线程上访问 ExoPlayer 实例。
 *   在绝大多数情况下，这应该是应用程序的主线程。
 *   使用应用程序的主线程也是使用 ExoPlayer 的 UI 组件或 IMA 扩展的要求。
 *   可以通过在创建播放器时传递 {@link Looper} 来明确指定必须访问 ExoPlayer 实例的线程。如果未指定 {@code Looper}，则使用创建播放器的线程的 {@code Looper}，或者如果该线程没有 {@code Looper}，则使用应用程序主线程的 {@code Looper}。
 *   在所有情况下，可以使用 {@link #getApplicationLooper()} 查询必须访问播放器的线程的 {@code Looper}。
 *   <li>注册的监听器在与 {@link #getApplicationLooper()} 关联的线程上调用。
 *   请注意，这意味着注册的监听器在与必须用于访问播放器的同一线程上调用。
 *   <li>内部播放线程负责播放。
 *   注入的播放器组件（如 Renderer、MediaSource、TrackSelector 和 LoadControl）由播放器在此线程上调用。
 *   <li>当应用程序在播放器上执行操作（例如搜索）时，消息通过消息队列传递到内部播放线程。
 *   内部播放线程从队列中消费消息并执行相应的操作。
 *   类似地，当在内部播放线程上发生播放事件时，消息通过第二个消息队列传递到应用程序线程。
 *   应用程序线程从队列中消费消息，更新应用程序可见状态并调用相应的监听器方法。
 *   <li>注入的播放器组件可能会使用额外的后台线程。
 *   例如，MediaSource 可能会使用后台线程加载数据。这些是特定于实现的。
 * </ul>
 */
public interface ExoPlayer extends Player {

  /**
   * @deprecated Use {@link ExoPlayer}, as all methods are defined by that interface.
   */
  @UnstableApi
  @Deprecated
  interface AudioComponent {

    /**
     * @deprecated Use {@link Player#setAudioAttributes(AudioAttributes, boolean)} instead.
     */
    @Deprecated
    void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus);

    /**
     * @deprecated Use {@link Player#getAudioAttributes()} instead.
     */
    @Deprecated
    AudioAttributes getAudioAttributes();

    /**
     * @deprecated Use {@link ExoPlayer#setAudioSessionId(int)} instead.
     */
    @Deprecated
    void setAudioSessionId(int audioSessionId);

    /**
     * @deprecated Use {@link ExoPlayer#getAudioSessionId()} instead.
     */
    @Deprecated
    int getAudioSessionId();

    /**
     * @deprecated Use {@link ExoPlayer#setAuxEffectInfo(AuxEffectInfo)} instead.
     */
    @Deprecated
    void setAuxEffectInfo(AuxEffectInfo auxEffectInfo);

    /**
     * @deprecated Use {@link ExoPlayer#clearAuxEffectInfo()} instead.
     */
    @Deprecated
    void clearAuxEffectInfo();

    /**
     * @deprecated Use {@link Player#setVolume(float)} instead.
     */
    @Deprecated
    void setVolume(float audioVolume);

    /**
     * @deprecated Use {@link Player#getVolume()} instead.
     */
    @Deprecated
    float getVolume();

    /**
     * @deprecated Use {@link ExoPlayer#setSkipSilenceEnabled(boolean)} instead.
     */
    @Deprecated
    void setSkipSilenceEnabled(boolean skipSilenceEnabled);

    /**
     * @deprecated Use {@link ExoPlayer#getSkipSilenceEnabled()} instead.
     */
    @Deprecated
    boolean getSkipSilenceEnabled();
  }

  /**
   * @deprecated Use {@link ExoPlayer}, as all methods are defined by that interface.
   */
  @UnstableApi
  @Deprecated
  interface VideoComponent {

    /**
     * @deprecated Use {@link ExoPlayer#setVideoScalingMode(int)} instead.
     */
    @Deprecated
    void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode);

    /**
     * @deprecated Use {@link ExoPlayer#getVideoScalingMode()} instead.
     */
    @Deprecated
    @C.VideoScalingMode
    int getVideoScalingMode();

    /**
     * @deprecated Use {@link ExoPlayer#setVideoChangeFrameRateStrategy(int)} instead.
     */
    @Deprecated
    void setVideoChangeFrameRateStrategy(
        @C.VideoChangeFrameRateStrategy int videoChangeFrameRateStrategy);

    /**
     * @deprecated Use {@link ExoPlayer#getVideoChangeFrameRateStrategy()} instead.
     */
    @Deprecated
    @C.VideoChangeFrameRateStrategy
    int getVideoChangeFrameRateStrategy();

    /**
     * @deprecated Use {@link ExoPlayer#setVideoFrameMetadataListener(VideoFrameMetadataListener)}
     *     instead.
     */
    @Deprecated
    void setVideoFrameMetadataListener(VideoFrameMetadataListener listener);

    /**
     * @deprecated Use {@link ExoPlayer#clearVideoFrameMetadataListener(VideoFrameMetadataListener)}
     *     instead.
     */
    @Deprecated
    void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener);

    /**
     * @deprecated Use {@link ExoPlayer#setCameraMotionListener(CameraMotionListener)} instead.
     */
    @Deprecated
    void setCameraMotionListener(CameraMotionListener listener);

    /**
     * @deprecated Use {@link ExoPlayer#clearCameraMotionListener(CameraMotionListener)} instead.
     */
    @Deprecated
    void clearCameraMotionListener(CameraMotionListener listener);

    /**
     * @deprecated Use {@link Player#clearVideoSurface()} instead.
     */
    @Deprecated
    void clearVideoSurface();

    /**
     * @deprecated Use {@link Player#clearVideoSurface(Surface)} instead.
     */
    @Deprecated
    void clearVideoSurface(@Nullable Surface surface);

    /**
     * @deprecated Use {@link Player#setVideoSurface(Surface)} instead.
     */
    @Deprecated
    void setVideoSurface(@Nullable Surface surface);

    /**
     * @deprecated Use {@link Player#setVideoSurfaceHolder(SurfaceHolder)} instead.
     */
    @Deprecated
    void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

    /**
     * @deprecated Use {@link Player#clearVideoSurfaceHolder(SurfaceHolder)} instead.
     */
    @Deprecated
    void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

    /**
     * @deprecated Use {@link Player#setVideoSurfaceView(SurfaceView)} instead.
     */
    @Deprecated
    void setVideoSurfaceView(@Nullable SurfaceView surfaceView);

    /**
     * @deprecated Use {@link Player#clearVideoSurfaceView(SurfaceView)} instead.
     */
    @Deprecated
    void clearVideoSurfaceView(@Nullable SurfaceView surfaceView);

    /**
     * @deprecated Use {@link Player#setVideoTextureView(TextureView)} instead.
     */
    @Deprecated
    void setVideoTextureView(@Nullable TextureView textureView);

    /**
     * @deprecated Use {@link Player#clearVideoTextureView(TextureView)} instead.
     */
    @Deprecated
    void clearVideoTextureView(@Nullable TextureView textureView);

    /**
     * @deprecated Use {@link Player#getVideoSize()} instead.
     */
    @Deprecated
    VideoSize getVideoSize();
  }

  /**
   * @deprecated Use {@link Player}, as all methods are defined by that interface.
   */
  @UnstableApi
  @Deprecated
  interface TextComponent {

    /**
     * @deprecated Use {@link Player#getCurrentCues()} instead.
     */
    @Deprecated
    CueGroup getCurrentCues();
  }

  /**
   * @deprecated Use {@link Player}, as all methods are defined by that interface.
   */
  @UnstableApi
  @Deprecated
  interface DeviceComponent {

    /**
     * @deprecated Use {@link Player#getDeviceInfo()} instead.
     */
    @Deprecated
    DeviceInfo getDeviceInfo();

    /**
     * @deprecated Use {@link Player#getDeviceVolume()} instead.
     */
    @Deprecated
    int getDeviceVolume();

    /**
     * @deprecated Use {@link Player#isDeviceMuted()} instead.
     */
    @Deprecated
    boolean isDeviceMuted();

    /**
     * @deprecated Use {@link Player#setDeviceVolume(int)} instead.
     */
    @Deprecated
    void setDeviceVolume(int volume);

    /**
     * @deprecated Use {@link Player#increaseDeviceVolume()} instead.
     */
    @Deprecated
    void increaseDeviceVolume();

    /**
     * @deprecated Use {@link Player#decreaseDeviceVolume()} instead.
     */
    @Deprecated
    void decreaseDeviceVolume();

    /**
     * @deprecated Use {@link Player#setDeviceMuted(boolean)} instead.
     */
    @Deprecated
    void setDeviceMuted(boolean muted);
  }

  /** 用于监听音频卸载事件的监听器。 */
  @UnstableApi
  interface AudioOffloadListener {
    /**
     * 当 {@link #isSleepingForOffload} 的值发生变化时调用。
     *
     * <p>当 {@code isSleepingForOffload} 为 {@code true} 时，表示播放器已暂停其主循环以节省功耗，处于卸载调度模式。
     */
    default void onSleepingForOffloadChanged(boolean isSleepingForOffload) {}

    /**
     * 当 {@link AudioTrack#isOffloadedPlayback} 的值发生变化时调用。
     *
     * <p>通常不需要对此采取行动。但当卸载对效率或音频功能（如无缝播放、播放速度）至关重要时，此方法将通知应用程序。
     */
    default void onOffloadedPlayback(boolean isOffloadedPlayback) {}
  }
  /** 用于预加载播放列表项的配置选项。 */
  @UnstableApi
  class PreloadConfiguration {

    /** 默认的预加载配置，禁用播放列表预加载。 */
    public static final PreloadConfiguration DEFAULT =
        new PreloadConfiguration(/* targetPreloadDurationUs= */ C.TIME_UNSET);

    /**
     * 预加载时的目标缓冲时长，单位为微秒，或 {@link C#TIME_UNSET} 表示禁用预加载。
     */
    public final long targetPreloadDurationUs;

    /**
     * 创建一个实例。
     *
     * @param targetPreloadDurationUs 预加载的目标时长，单位为微秒，或 {@link C#TIME_UNSET} 表示禁用预加载。
     */
    public PreloadConfiguration(long targetPreloadDurationUs) {
      this.targetPreloadDurationUs = targetPreloadDurationUs;
    }
  }

  /**
   * 用于构建 {@link ExoPlayer} 实例的构建器。
   *
   * <p>有关默认值的列表，请参阅 {@link #Builder(Context)}。
   */
  @SuppressWarnings("deprecation")
  final class Builder {

    /* package */ final Context context; // 上下文对象，用于初始化播放器。

    /* package */ Clock clock; // 时钟对象，用于控制时间相关操作。
    /* package */ long foregroundModeTimeoutMs; // 前台模式超时时间（毫秒）。
    /* package */ Supplier<RenderersFactory> renderersFactorySupplier; // 提供 RenderersFactory 的 Supplier，用于创建渲染器。
    /* package */ Supplier<MediaSource.Factory> mediaSourceFactorySupplier; // 提供 MediaSource.Factory 的 Supplier，用于创建媒体源。
    /* package */ Supplier<TrackSelector> trackSelectorSupplier; // 提供 TrackSelector 的 Supplier，用于选择轨道。
    /* package */ Supplier<LoadControl> loadControlSupplier; // 提供 LoadControl 的 Supplier，用于控制加载行为。
    /* package */ Supplier<BandwidthMeter> bandwidthMeterSupplier; // 提供 BandwidthMeter 的 Supplier，用于测量带宽。
    /* package */ Function<Clock, AnalyticsCollector> analyticsCollectorFunction; // 根据 Clock 创建 AnalyticsCollector 的 Function，用于收集分析数据。
    /* package */ Looper looper; // 用于处理播放器事件的 Looper。
    /* package */ @C.Priority int priority; // 播放器的优先级。
    @Nullable /* package */ PriorityTaskManager priorityTaskManager; // 优先级任务管理器，可能为 null。
    /* package */ AudioAttributes audioAttributes; // 音频属性，用于配置音频播放行为。
    /* package */ boolean handleAudioFocus; // 是否处理音频焦点。
    @C.WakeMode /* package */ int wakeMode; // 唤醒模式，用于控制设备唤醒行为。
    /* package */ boolean handleAudioBecomingNoisy; // 是否处理音频变得嘈杂的情况（例如耳机断开）。
    /* package */ boolean skipSilenceEnabled; // 是否跳过静音部分。
    /* package */ boolean deviceVolumeControlEnabled; // 是否启用设备音量控制。
    @C.VideoScalingMode /* package */ int videoScalingMode; // 视频缩放模式。
    @C.VideoChangeFrameRateStrategy /* package */ int videoChangeFrameRateStrategy; // 视频帧率变化策略。
    /* package */ boolean useLazyPreparation; // 是否使用延迟准备。
    /* package */ SeekParameters seekParameters; // 搜索参数，用于控制搜索行为。
    /* package */ long seekBackIncrementMs; // 向后搜索的增量时间（毫秒）。
    /* package */ long seekForwardIncrementMs; // 向前搜索的增量时间（毫秒）。
    /* package */ long maxSeekToPreviousPositionMs; // 最大回到上一位置的时间（毫秒）。
    /* package */ LivePlaybackSpeedControl livePlaybackSpeedControl; // 直播播放速度控制。
    /* package */ long releaseTimeoutMs; // 释放超时时间（毫秒）。
    /* package */ long detachSurfaceTimeoutMs; // 分离 Surface 的超时时间（毫秒）。
    /* package */ boolean pauseAtEndOfMediaItems; // 是否在媒体项结束时暂停。
    /* package */ boolean usePlatformDiagnostics; // 是否使用平台诊断功能。
    @Nullable /* package */ PlaybackLooperProvider playbackLooperProvider; // 提供播放循环的 Provider，可能为 null。
    /* package */ boolean buildCalled; // 是否已调用 build 方法。
    /* package */ boolean suppressPlaybackOnUnsuitableOutput; // 是否在不合适的输出设备上抑制播放。
    /* package */ String playerName; // 播放器名称。
    /* package */ boolean dynamicSchedulingEnabled; // 是否启用动态调度。
    @Nullable /* package */ SuitableOutputChecker suitableOutputChecker; // 检查输出设备是否合适的工具，可能为 null。

    /**
     * 创建一个构建器。
     *
     * <p>如果您打算提供自定义的 {@link RenderersFactory}、{@link ExtractorsFactory} 或 {@link DefaultMediaSourceFactory}，
     * 请改用 {@link #Builder(Context, RenderersFactory)}、{@link #Builder(Context, MediaSource.Factory)}
     * 或 {@link #Builder(Context, RenderersFactory, MediaSource.Factory)}。这是为了确保 ProGuard 或 R8
     * 可以从 APK 中移除 ExoPlayer 的 {@link DefaultRenderersFactory}、{@link DefaultExtractorsFactory}
     * 和 {@link DefaultMediaSourceFactory}。
     *
     * <p>构建器使用以下默认值：
     *
     * <ul>
     *   <li>{@link RenderersFactory}：{@link DefaultRenderersFactory}
     *   <li>{@link TrackSelector}：{@link DefaultTrackSelector}
     *   <li>{@link MediaSource.Factory}：{@link DefaultMediaSourceFactory}
     *   <li>{@link LoadControl}：{@link DefaultLoadControl}
     *   <li>{@link BandwidthMeter}：{@link DefaultBandwidthMeter#getSingletonInstance(Context)}
     *   <li>{@link LivePlaybackSpeedControl}：{@link DefaultLivePlaybackSpeedControl}
     *   <li>{@link Looper}：与当前线程关联的 {@link Looper}，如果当前线程没有 {@link Looper}，则使用应用程序主线程的 {@link Looper}
     *   <li>{@link AnalyticsCollector}：使用 {@link Clock#DEFAULT} 的 {@link AnalyticsCollector}
     *   <li>{@link C.Priority}：{@link C#PRIORITY_PLAYBACK}
     *   <li>{@link PriorityTaskManager}：{@code null}（未使用）
     *   <li>{@link AudioAttributes}：{@link AudioAttributes#DEFAULT}，不处理音频焦点
     *   <li>{@link C.WakeMode}：{@link C#WAKE_MODE_NONE}
     *   <li>{@code handleAudioBecomingNoisy}：{@code false}
     *   <li>{@code skipSilenceEnabled}：{@code false}
     *   <li>{@link C.VideoScalingMode}：{@link C#VIDEO_SCALING_MODE_DEFAULT}
     *   <li>{@link C.VideoChangeFrameRateStrategy}：{@link C#VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS}
     *   <li>{@code useLazyPreparation}：{@code true}
     *   <li>{@link SeekParameters}：{@link SeekParameters#DEFAULT}
     *   <li>{@code seekBackIncrementMs}：{@link C#DEFAULT_SEEK_BACK_INCREMENT_MS}
     *   <li>{@code seekForwardIncrementMs}：{@link C#DEFAULT_SEEK_FORWARD_INCREMENT_MS}
     *   <li>{@code maxSeekToPreviousPositionMs}：{@link C#DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS}
     *   <li>{@code releaseTimeoutMs}：{@link #DEFAULT_RELEASE_TIMEOUT_MS}
     *   <li>{@code detachSurfaceTimeoutMs}：{@link #DEFAULT_DETACH_SURFACE_TIMEOUT_MS}
     *   <li>{@code pauseAtEndOfMediaItems}：{@code false}
     *   <li>{@code usePlatformDiagnostics}：{@code true}
     *   <li>{@link Clock}：{@link Clock#DEFAULT}
     *   <li>{@code playbackLooper}：{@code null}（创建新线程）
     *   <li>{@code dynamicSchedulingEnabled}：{@code false}
     * </ul>
     *
     * @param context 一个 {@link Context}。
     */
    public Builder(Context context) {
      this(
          context,
          () -> new DefaultRenderersFactory(context),
          () -> new DefaultMediaSourceFactory(context, new DefaultExtractorsFactory()));
    }

    /**
     * 使用自定义的 {@link RenderersFactory} 创建一个构建器。
     *
     * <p>有关默认值的列表，请参阅 {@link #Builder(Context)}。
     *
     * <p>请注意，此构造函数仅用于尝试确保 ProGuard 或 R8 可以移除 ExoPlayer 的 {@link DefaultRenderersFactory}。
     *
     * @param context 一个 {@link Context}。
     * @param renderersFactory 用于创建播放器使用的 {@link Renderer Renderers} 的工厂。
     */
    @UnstableApi
    public Builder(Context context, RenderersFactory renderersFactory) {
      this(
          context,
          () -> renderersFactory,
          () -> new DefaultMediaSourceFactory(context, new DefaultExtractorsFactory()));
      checkNotNull(renderersFactory);
    }

    /**
     * 使用自定义的 {@link MediaSource.Factory} 创建一个构建器。
     *
     * <p>有关默认值的列表，请参阅 {@link #Builder(Context)}。
     *
     * <p>请注意，此构造函数仅用于尝试确保 ProGuard 或 R8 可以移除 ExoPlayer 的 {@link DefaultMediaSourceFactory}（以及 {@link DefaultExtractorsFactory}）。
     *
     * @param context 一个 {@link Context}。
     * @param mediaSourceFactory 用于从 {@link MediaItem} 创建 {@link MediaSource} 的工厂。
     */
    @UnstableApi
    public Builder(Context context, MediaSource.Factory mediaSourceFactory) {
      this(context, () -> new DefaultRenderersFactory(context), () -> mediaSourceFactory);
      checkNotNull(mediaSourceFactory);
    }

    /**
     * 使用自定义的 {@link RenderersFactory} 和 {@link MediaSource.Factory} 创建一个构建器。
     *
     * <p>有关默认值的列表，请参阅 {@link #Builder(Context)}。
     *
     * <p>请注意，此构造函数仅用于尝试确保 ProGuard 或 R8 可以移除 ExoPlayer 的 {@link DefaultRenderersFactory}、{@link DefaultMediaSourceFactory}（以及 {@link DefaultExtractorsFactory}）。
     *
     * @param context 一个 {@link Context}。
     * @param renderersFactory 用于创建播放器使用的 {@link Renderer Renderers} 的工厂。
     * @param mediaSourceFactory 用于从 {@link MediaItem} 创建 {@link MediaSource} 的工厂。
     */
    @UnstableApi
    public Builder(
        Context context,
        RenderersFactory renderersFactory,
        MediaSource.Factory mediaSourceFactory) {
      this(context, () -> renderersFactory, () -> mediaSourceFactory);
      checkNotNull(renderersFactory);
      checkNotNull(mediaSourceFactory);
    }

    /**
     * 使用指定的自定义组件创建一个构建器。
     *
     * <p>请注意，此构造函数仅用于尝试确保 ProGuard 或 R8 可以移除 ExoPlayer 的默认组件。
     *
     * @param context 一个 {@link Context}。
     * @param renderersFactory 用于创建播放器使用的 {@link Renderer Renderers} 的工厂。
     * @param mediaSourceFactory 一个 {@link MediaSource.Factory}。
     * @param trackSelector 一个 {@link TrackSelector}。
     * @param loadControl 一个 {@link LoadControl}。
     * @param bandwidthMeter 一个 {@link BandwidthMeter}。
     * @param analyticsCollector 一个 {@link AnalyticsCollector}。
     */
    @UnstableApi
    public Builder(
        Context context,
        RenderersFactory renderersFactory,
        MediaSource.Factory mediaSourceFactory,
        TrackSelector trackSelector,
        LoadControl loadControl,
        BandwidthMeter bandwidthMeter,
        AnalyticsCollector analyticsCollector) {
      this(
          context,
          () -> renderersFactory,
          () -> mediaSourceFactory,
          () -> trackSelector,
          () -> loadControl,
          () -> bandwidthMeter,
          (clock) -> analyticsCollector);
      checkNotNull(renderersFactory);
      checkNotNull(mediaSourceFactory);
      checkNotNull(trackSelector);
      checkNotNull(bandwidthMeter);
      checkNotNull(analyticsCollector);
    }

    private Builder(
        Context context,
        Supplier<RenderersFactory> renderersFactorySupplier,
        Supplier<MediaSource.Factory> mediaSourceFactorySupplier) {
      this(
          context,
          renderersFactorySupplier,
          mediaSourceFactorySupplier,
          () -> new DefaultTrackSelector(context),
          DefaultLoadControl::new,
          () -> DefaultBandwidthMeter.getSingletonInstance(context),
          DefaultAnalyticsCollector::new);
    }

    private Builder(
        Context context,
        Supplier<RenderersFactory> renderersFactorySupplier,
        Supplier<MediaSource.Factory> mediaSourceFactorySupplier,
        Supplier<TrackSelector> trackSelectorSupplier,
        Supplier<LoadControl> loadControlSupplier,
        Supplier<BandwidthMeter> bandwidthMeterSupplier,
        Function<Clock, AnalyticsCollector> analyticsCollectorFunction) {
      this.context = checkNotNull(context);
      this.renderersFactorySupplier = renderersFactorySupplier;
      this.mediaSourceFactorySupplier = mediaSourceFactorySupplier;
      this.trackSelectorSupplier = trackSelectorSupplier;
      this.loadControlSupplier = loadControlSupplier;
      this.bandwidthMeterSupplier = bandwidthMeterSupplier;
      this.analyticsCollectorFunction = analyticsCollectorFunction;
      looper = Util.getCurrentOrMainLooper();
      audioAttributes = AudioAttributes.DEFAULT;
      wakeMode = C.WAKE_MODE_NONE;
      videoScalingMode = C.VIDEO_SCALING_MODE_DEFAULT;
      videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS;
      useLazyPreparation = true;
      seekParameters = SeekParameters.DEFAULT;
      seekBackIncrementMs = C.DEFAULT_SEEK_BACK_INCREMENT_MS;
      seekForwardIncrementMs = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS;
      maxSeekToPreviousPositionMs = C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS;
      livePlaybackSpeedControl = new DefaultLivePlaybackSpeedControl.Builder().build();
      clock = Clock.DEFAULT;
      releaseTimeoutMs = DEFAULT_RELEASE_TIMEOUT_MS;
      detachSurfaceTimeoutMs = DEFAULT_DETACH_SURFACE_TIMEOUT_MS;
      usePlatformDiagnostics = true;
      playerName = "";
      priority = C.PRIORITY_PLAYBACK;
    }

    /**
     * 设置对 {@link #setForegroundMode} 调用所花费时间的限制。如果 {@link #setForegroundMode} 的调用耗时超过 {@code timeoutMs} 毫秒，
     * 播放器将通过 {@link Player.Listener#onPlayerError} 抛出一个错误。
     *
     * <p>此方法是实验性的，在未来的版本中可能会重命名或移除。
     *
     * @param timeoutMs 时间限制，单位为毫秒。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder experimentalSetForegroundModeTimeoutMs(long timeoutMs) {
      checkState(!buildCalled);
      foregroundModeTimeoutMs = timeoutMs;
      return this;
    }

    /**
     * 设置是否启用动态调度。
     *
     * <p>如果启用，ExoPlayer 的播放循环将尽可能少地运行，仅在 {@link Renderer} 可以取得进展时调度工作。
     *
     * <p>此方法是实验性的，在未来的版本中可能会重命名或移除。
     *
     * @param dynamicSchedulingEnabled 是否启用动态调度。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder experimentalSetDynamicSchedulingEnabled(boolean dynamicSchedulingEnabled) {
      checkState(!buildCalled);
      this.dynamicSchedulingEnabled = dynamicSchedulingEnabled;
      return this;
    }

    /**
     * 设置播放器是否应抑制在不合适的输出设备上尝试播放的行为。一个不合适的音频输出设备的例子是 Wear OS 设备的内置扬声器（除非用户明确选择它）。
     *
     * <p>如果调用时传入 {@code suppressPlaybackOnUnsuitableOutput = true}，则在不合适的音频输出设备上尝试播放将导致调用 {@link
     * Player.Listener#onPlaybackSuppressionReasonChanged(int)}，并传入值 {@link
     * Player#PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT}。
     *
     * <p>调用此方法的开发者可能还需要启用 {@link #setHandleAudioBecomingNoisy(boolean)}，以防止在耳机断开连接时继续通过内置扬声器播放。
     *
     * @param suppressPlaybackOnUnsuitableOutput 播放器是否应抑制在不合适的输出设备上尝试播放的行为。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setSuppressPlaybackOnUnsuitableOutput(
        boolean suppressPlaybackOnUnsuitableOutput) {
      checkState(!buildCalled);
      this.suppressPlaybackOnUnsuitableOutput = suppressPlaybackOnUnsuitableOutput;
      return this;
    }

    /**
     * 设置播放器将使用的 {@link RenderersFactory}。
     *
     * @param renderersFactory 一个 {@link RenderersFactory}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setRenderersFactory(RenderersFactory renderersFactory) {
      checkState(!buildCalled);
      checkNotNull(renderersFactory);
      this.renderersFactorySupplier = () -> renderersFactory;
      return this;
    }

    /**
     * 设置播放器将使用的 {@link MediaSource.Factory}。
     *
     * @param mediaSourceFactory 一个 {@link MediaSource.Factory}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    public Builder setMediaSourceFactory(MediaSource.Factory mediaSourceFactory) {
      checkState(!buildCalled);
      checkNotNull(mediaSourceFactory);
      this.mediaSourceFactorySupplier = () -> mediaSourceFactory;
      return this;
    }
    /**
     * 设置播放器将使用的 {@link TrackSelector}。
     *
     * @param trackSelector 一个 {@link TrackSelector}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setTrackSelector(TrackSelector trackSelector) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      checkNotNull(trackSelector); // 检查 trackSelector 是否为 null
      this.trackSelectorSupplier = () -> trackSelector; // 将 trackSelector 封装为 Supplier
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置播放器将使用的 {@link LoadControl}。
     *
     * @param loadControl 一个 {@link LoadControl}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setLoadControl(LoadControl loadControl) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      checkNotNull(loadControl); // 检查 loadControl 是否为 null
      this.loadControlSupplier = () -> loadControl; // 将 loadControl 封装为 Supplier
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置播放器将使用的 {@link BandwidthMeter}。
     *
     * @param bandwidthMeter 一个 {@link BandwidthMeter}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setBandwidthMeter(BandwidthMeter bandwidthMeter) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      checkNotNull(bandwidthMeter); // 检查 bandwidthMeter 是否为 null
      this.bandwidthMeterSupplier = () -> bandwidthMeter; // 将 bandwidthMeter 封装为 Supplier
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置用于所有播放器调用以及调用监听器的 {@link Looper}。
     *
     * @param looper 一个 {@link Looper}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setLooper(Looper looper) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      checkNotNull(looper); // 检查 looper 是否为 null
      this.looper = looper; // 设置 looper
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置用于收集和转发所有播放器事件的 {@link AnalyticsCollector}。
     *
     * @param analyticsCollector 一个 {@link AnalyticsCollector}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setAnalyticsCollector(AnalyticsCollector analyticsCollector) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      checkNotNull(analyticsCollector); // 检查 analyticsCollector 是否为 null
      this.analyticsCollectorFunction = (clock) -> analyticsCollector; // 将 analyticsCollector 封装为 Function
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置此播放器的 {@link C.Priority}。
     *
     * <p>优先级可能影响同一应用程序中运行的多个播放器或其他组件之间的资源分配。
     *
     * <p>如果设置了 {@linkplain #setPriorityTaskManager PriorityTaskManager}，则此优先级将用于 {@link PriorityTaskManager}。
     *
     * @param priority 一个 {@link C.Priority}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setPriority(@C.Priority int priority) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.priority = priority; // 设置优先级
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置播放器将使用的 {@link PriorityTaskManager}。
     *
     * <p>通过 {@link #setPriority} 设置的优先级（或默认的 {@link C#PRIORITY_PLAYBACK}）将在播放器加载时使用。
     *
     * @param priorityTaskManager 一个 {@link PriorityTaskManager}，或 null 表示不使用。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.priorityTaskManager = priorityTaskManager; // 设置 PriorityTaskManager
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置播放器将使用的 {@link AudioAttributes} 以及是否处理音频焦点。
     *
     * <p>如果需要处理音频焦点，则 {@link AudioAttributes#usage} 必须为 {@link C#USAGE_MEDIA} 或 {@link C#USAGE_GAME}。其他用途将抛出 {@link IllegalArgumentException}。
     *
     * @param audioAttributes {@link AudioAttributes}。
     * @param handleAudioFocus 播放器是否应处理音频焦点。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    public Builder setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.audioAttributes = checkNotNull(audioAttributes); // 检查 audioAttributes 是否为 null 并设置
      this.handleAudioFocus = handleAudioFocus; // 设置是否处理音频焦点
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置播放器将使用的 {@link C.WakeMode}。
     *
     * <p>启用此功能需要 {@link android.Manifest.permission#WAKE_LOCK} 权限。它应与前台 {@link android.app.Service} 一起使用，用于屏幕关闭时的播放场景（例如后台音频播放）。对于屏幕保持开启的播放场景（例如前台视频播放），此功能无意义。
     *
     * <p>启用后，当播放器处于 {@link #STATE_READY} 或 {@link #STATE_BUFFERING} 状态且 {@code playWhenReady = true} 时，将持有锁（{@link android.os.PowerManager.WakeLock} / {@link android.net.wifi.WifiManager.WifiLock}）。持有的锁取决于指定的 {@link C.WakeMode}。
     *
     * @param wakeMode 一个 {@link C.WakeMode}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    public Builder setWakeMode(@C.WakeMode int wakeMode) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.wakeMode = wakeMode; // 设置唤醒模式
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置当音频从耳机切换到设备扬声器时，播放器是否应自动暂停。有关更多信息，请参阅 <a href="https://developer.android.com/media/platform/output#becoming-noisy">音频变得嘈杂</a> 文档。
     *
     * @param handleAudioBecomingNoisy 当音频从耳机切换到设备扬声器时，播放器是否应自动暂停。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    public Builder setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.handleAudioBecomingNoisy = handleAudioBecomingNoisy; // 设置是否处理音频变得嘈杂的情况
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置是否启用跳过音频流中的静音部分。
     *
     * @param skipSilenceEnabled 是否启用跳过静音。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setSkipSilenceEnabled(boolean skipSilenceEnabled) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.skipSilenceEnabled = skipSilenceEnabled; // 设置是否跳过静音
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置播放器是否允许设置、增加、减少或静音设备音量。
     *
     * @param deviceVolumeControlEnabled 是否启用设备音量控制。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setDeviceVolumeControlEnabled(boolean deviceVolumeControlEnabled) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.deviceVolumeControlEnabled = deviceVolumeControlEnabled; // 设置是否启用设备音量控制
      return this; // 返回此构建器，支持链式调用
    }
    /**
     * 设置播放器将使用的 {@link C.VideoScalingMode}。
     *
     * <p>缩放模式仅在使用基于 {@link MediaCodec} 的视频 {@link Renderer} 且输出 Surface 由 {@link SurfaceView} 拥有时生效。
     *
     * @param videoScalingMode 一个 {@link C.VideoScalingMode}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setVideoScalingMode(@C.VideoScalingMode int videoScalingMode) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.videoScalingMode = videoScalingMode; // 设置视频缩放模式
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置播放器在提供视频输出 {@link Surface} 时将使用的 {@link C.VideoChangeFrameRateStrategy}。
     *
     * <p>该策略仅在使用基于 {@link MediaCodec} 的视频 {@link Renderer} 时生效。如果应用程序希望使用 {@link Surface#CHANGE_FRAME_RATE_ALWAYS}，
     * 应将模式设置为 {@link C#VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF} 以禁用 ExoPlayer 对 {@link Surface#setFrameRate} 的调用，
     * 然后从应用程序代码中直接调用 {@link Surface#setFrameRate}。
     *
     * @param videoChangeFrameRateStrategy 一个 {@link C.VideoChangeFrameRateStrategy}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setVideoChangeFrameRateStrategy(
        @C.VideoChangeFrameRateStrategy int videoChangeFrameRateStrategy) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.videoChangeFrameRateStrategy = videoChangeFrameRateStrategy; // 设置视频帧率变化策略
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置是否应延迟初始化媒体源。
     *
     * <p>如果为 false，所有初始准备步骤（例如，清单加载）会立即执行。如果为 true，这些初始准备仅在播放器开始缓冲媒体时触发。
     *
     * @param useLazyPreparation 是否使用延迟准备。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setUseLazyPreparation(boolean useLazyPreparation) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.useLazyPreparation = useLazyPreparation; // 设置是否使用延迟准备
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置控制搜索操作执行方式的参数。
     *
     * @param seekParameters {@link SeekParameters}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setSeekParameters(SeekParameters seekParameters) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.seekParameters = checkNotNull(seekParameters); // 检查 seekParameters 是否为 null 并设置
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置 {@link #seekBack()} 的增量。
     *
     * @param seekBackIncrementMs 向后搜索的增量，单位为毫秒。
     * @return 此构建器。
     * @throws IllegalArgumentException 如果 {@code seekBackIncrementMs} 非正数。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setSeekBackIncrementMs(@IntRange(from = 1) long seekBackIncrementMs) {
      checkArgument(seekBackIncrementMs > 0); // 检查 seekBackIncrementMs 是否为正数
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.seekBackIncrementMs = seekBackIncrementMs; // 设置向后搜索的增量
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置 {@link #seekForward()} 的增量。
     *
     * @param seekForwardIncrementMs 向前搜索的增量，单位为毫秒。
     * @return 此构建器。
     * @throws IllegalArgumentException 如果 {@code seekForwardIncrementMs} 非正数。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setSeekForwardIncrementMs(@IntRange(from = 1) long seekForwardIncrementMs) {
      checkArgument(seekForwardIncrementMs > 0); // 检查 seekForwardIncrementMs 是否为正数
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.seekForwardIncrementMs = seekForwardIncrementMs; // 设置向前搜索的增量
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置 {@link #seekToPrevious()} 跳转到上一个 {@link MediaItem} 的最大位置。
     *
     * @param maxSeekToPreviousPositionMs 最大位置，单位为毫秒。
     * @return 此构建器。
     * @throws IllegalArgumentException 如果 {@code maxSeekToPreviousPositionMs} 为负数。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setMaxSeekToPreviousPositionMs(
        @IntRange(from = 0) long maxSeekToPreviousPositionMs) {
      checkArgument(maxSeekToPreviousPositionMs >= 0L); // 检查 maxSeekToPreviousPositionMs 是否为非负数
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.maxSeekToPreviousPositionMs = maxSeekToPreviousPositionMs; // 设置最大跳转位置
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置 {@link #release} 和 {@link #setForegroundMode} 调用的超时时间。
     *
     * <p>如果 {@link #release} 或 {@link #setForegroundMode} 调用耗时超过 {@code timeoutMs}，
     * 播放器将通过 {@link Player.Listener#onPlayerError} 报告错误。
     *
     * @param releaseTimeoutMs 释放超时时间，单位为毫秒。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setReleaseTimeoutMs(long releaseTimeoutMs) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.releaseTimeoutMs = releaseTimeoutMs; // 设置释放超时时间
      return this; // 返回此构建器，支持链式调用
    }
    /**
     * 设置从播放器分离 Surface 的超时时间。
     *
     * <p>如果分离 Surface 或替换 Surface 耗时超过 {@code detachSurfaceTimeoutMs}，播放器将通过 {@link Player.Listener#onPlayerError} 报告错误。
     *
     * @param detachSurfaceTimeoutMs 分离 Surface 的超时时间，单位为毫秒。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setDetachSurfaceTimeoutMs(long detachSurfaceTimeoutMs) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.detachSurfaceTimeoutMs = detachSurfaceTimeoutMs; // 设置分离 Surface 的超时时间
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置是否在每个媒体项结束时暂停播放。
     *
     * <p>这意味着播放器将在当前 {@link #getCurrentTimeline() 时间轴} 的每个窗口结束时暂停。当这种情况发生时，监听器将通过 {@link Player.Listener#onPlayWhenReadyChanged(boolean, int)} 被通知，原因值为 {@link Player#PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM}。
     *
     * @param pauseAtEndOfMediaItems 是否在每个媒体项结束时暂停播放。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems; // 设置是否在每个媒体项结束时暂停播放
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置控制直播流播放速度的 {@link LivePlaybackSpeedControl}，以保持与直播流边缘的稳定目标偏移。
     *
     * @param livePlaybackSpeedControl {@link LivePlaybackSpeedControl}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setLivePlaybackSpeedControl(LivePlaybackSpeedControl livePlaybackSpeedControl) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.livePlaybackSpeedControl = checkNotNull(livePlaybackSpeedControl); // 检查 livePlaybackSpeedControl 是否为 null 并设置
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置播放器是否向 Android 平台报告诊断数据。
     *
     * <p>如果启用，播放器将使用 {@link android.media.metrics.MediaMetricsManager} 创建一个 {@link android.media.metrics.PlaybackSession}，并将播放事件和性能数据转发到该会话。这有助于提供设备上媒体播放的系统性能和调试信息。如果用户启用了<a href="https://support.google.com/accounts/answer/6078260">共享使用和诊断数据</a>，这些数据也可能被 Google 收集。
     *
     * @param usePlatformDiagnostics 播放器是否向 Android 平台报告诊断数据。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setUsePlatformDiagnostics(boolean usePlatformDiagnostics) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.usePlatformDiagnostics = usePlatformDiagnostics; // 设置是否使用平台诊断
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置播放器将使用的 {@link Clock}。仅应用于测试目的。
     *
     * @param clock {@link Clock}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @VisibleForTesting
    public Builder setClock(Clock clock) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.clock = clock; // 设置时钟
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置用于检查所选输出设备是否适合播放的 {@link SuitableOutputChecker}。
     *
     * <p>如果未调用此方法，库将使用基于框架 API 的默认实现。
     *
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @RestrictTo(LIBRARY_GROUP)
    @VisibleForTesting
    @RequiresApi(35)
    public Builder setSuitableOutputChecker(SuitableOutputChecker suitableOutputChecker) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.suitableOutputChecker = suitableOutputChecker; // 设置 SuitableOutputChecker
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置用于播放的 {@link Looper}。
     *
     * <p>后台线程应以 {@link Process#THREAD_PRIORITY_AUDIO} 优先级运行，并应在 10 毫秒内处理消息。
     *
     * @param playbackLooper {@link Looper}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setPlaybackLooper(Looper playbackLooper) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.playbackLooperProvider = new PlaybackLooperProvider(playbackLooper); // 设置 PlaybackLooperProvider
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置用于播放的 {@link PlaybackLooperProvider}。
     *
     * @param playbackLooperProvider {@link PlaybackLooperProvider}。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @RestrictTo(LIBRARY_GROUP)
    public Builder setPlaybackLooperProvider(PlaybackLooperProvider playbackLooperProvider) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.playbackLooperProvider = playbackLooperProvider; // 设置 PlaybackLooperProvider
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 设置包含在 {@link PlayerId} 中的播放器名称，用于通过 {@link PlayerId} 识别播放器。
     *
     * <p>默认值为空字符串。
     *
     * @param playerName {@link PlayerId} 中的播放器名称。
     * @return 此构建器。
     * @throws IllegalStateException 如果已经调用了 {@link #build()}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setName(String playerName) {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      this.playerName = playerName; // 设置播放器名称
      return this; // 返回此构建器，支持链式调用
    }

    /**
     * 构建一个 {@link ExoPlayer} 实例。
     *
     * @throws IllegalStateException 如果此方法已被调用。
     */
    public ExoPlayer build() {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      buildCalled = true; // 标记 build 方法已调用
      if (suitableOutputChecker == null
          && Util.SDK_INT >= 35
          && suppressPlaybackOnUnsuitableOutput) {
        suitableOutputChecker = new DefaultSuitableOutputChecker(context, new Handler(looper)); // 设置默认的 SuitableOutputChecker
      }
      return new ExoPlayerImpl(/* builder= */ this, /* wrappingPlayer= */ null); // 返回 ExoPlayer 实例
    }

    /* package */ SimpleExoPlayer buildSimpleExoPlayer() {
      checkState(!buildCalled); // 检查是否已调用 build 方法
      buildCalled = true; // 标记 build 方法已调用
      return new SimpleExoPlayer(/* builder= */ this); // 返回 SimpleExoPlayer 实例
    }
  }

  /**
   * The default timeout for calls to {@link #release} and {@link #setForegroundMode}, in
   * milliseconds.
   */
  @UnstableApi long DEFAULT_RELEASE_TIMEOUT_MS = 500;

  /** The default timeout for detaching a surface from the player, in milliseconds. */
  @UnstableApi long DEFAULT_DETACH_SURFACE_TIMEOUT_MS = 2_000;

  /**
   * Equivalent to {@link Player#getPlayerError()}, except the exception is guaranteed to be an
   * {@link ExoPlaybackException}.
   */
  @Override
  @Nullable
  ExoPlaybackException getPlayerError();

  /**
   * @deprecated Use {@link ExoPlayer}, as the {@link AudioComponent} methods are defined by that
   *     interface.
   */
  @SuppressWarnings("deprecation") // Intentionally returning deprecated type
  @UnstableApi
  @Nullable
  @Deprecated
  AudioComponent getAudioComponent();

  /**
   * @deprecated Use {@link ExoPlayer}, as the {@link VideoComponent} methods are defined by that
   *     interface.
   */
  @SuppressWarnings("deprecation") // Intentionally returning deprecated type
  @UnstableApi
  @Nullable
  @Deprecated
  VideoComponent getVideoComponent();

  /**
   * @deprecated Use {@link Player}, as the {@link TextComponent} methods are defined by that
   *     interface.
   */
  @SuppressWarnings("deprecation") // Intentionally returning deprecated type
  @UnstableApi
  @Nullable
  @Deprecated
  TextComponent getTextComponent();

  /**
   * @deprecated Use {@link Player}, as the {@link DeviceComponent} methods are defined by that
   *     interface.
   */
  @SuppressWarnings("deprecation") // Intentionally returning deprecated type
  @UnstableApi
  @Nullable
  @Deprecated
  DeviceComponent getDeviceComponent();

  /**
   * 添加一个监听器以接收音频卸载事件。
   *
   * <p>此方法可以从任何线程调用。
   *
   * @param listener 要注册的监听器。
   */
  @UnstableApi
  void addAudioOffloadListener(AudioOffloadListener listener);

  /**
   * 移除音频卸载事件的监听器。
   *
   * @param listener 要注销的监听器。
   */
  @UnstableApi
  void removeAudioOffloadListener(AudioOffloadListener listener);

  /** 返回用于收集分析事件的 {@link AnalyticsCollector}。 */
  @UnstableApi
  AnalyticsCollector getAnalyticsCollector();

  /**
   * 添加一个 {@link AnalyticsListener} 以接收分析事件。
   *
   * <p>此方法可以从任何线程调用。
   *
   * @param listener 要添加的监听器。
   */
  void addAnalyticsListener(AnalyticsListener listener);

  /**
   * 移除一个 {@link AnalyticsListener}。
   *
   * @param listener 要移除的监听器。
   */
  void removeAnalyticsListener(AnalyticsListener listener);

  /** 返回渲染器的数量。 */
  @UnstableApi
  int getRendererCount();

  /**
   * 返回给定索引处渲染器处理的轨道类型。
   *
   * <p>例如，视频渲染器将返回 {@link C#TRACK_TYPE_VIDEO}，音频渲染器将返回 {@link C#TRACK_TYPE_AUDIO}，文本渲染器将返回 {@link C#TRACK_TYPE_TEXT}。
   *
   * @param index 渲染器的索引。
   * @return 渲染器处理的 {@link C.TrackType 轨道类型}。
   */
  @UnstableApi
  @C.TrackType
  int getRendererType(int index);

  /**
   * 返回给定索引处的渲染器。
   *
   * @param index 渲染器的索引。
   * @return 该索引处的渲染器。
   */
  @UnstableApi
  Renderer getRenderer(int index);

  /**
   * 返回此播放器使用的轨道选择器，如果轨道选择不受支持则返回 null。
   */
  @UnstableApi
  @Nullable
  TrackSelector getTrackSelector();

  /**
   * 返回可用的轨道组。
   *
   * @see Listener#onTracksChanged(Tracks)
   * @deprecated 使用 {@link #getCurrentTracks()}。
   */
  @UnstableApi
  @Deprecated
  TrackGroupArray getCurrentTrackGroups();

  /**
   * 返回每个渲染器的当前轨道选择，可能包含 {@code null} 元素，如果某些渲染器没有选择任何轨道。
   *
   * @see Listener#onTracksChanged(Tracks)
   * @deprecated 使用 {@link #getCurrentTracks()}。
   */
  @UnstableApi
  @Deprecated
  TrackSelectionArray getCurrentTrackSelections();

  /**
   * 返回与播放线程关联的 {@link Looper}。
   *
   * <p>此方法可以从任何线程调用。
   */
  @UnstableApi
  Looper getPlaybackLooper();

  /**
   * 返回用于播放的 {@link Clock}。
   *
   * <p>此方法可以从任何线程调用。
   */
  @UnstableApi
  Clock getClock();

  /**
   * @deprecated 使用 {@link #setMediaSource(MediaSource)} 和 {@link #prepare()}。
   */
  @UnstableApi
  @Deprecated
  void prepare(MediaSource mediaSource);

  /**
   * @deprecated 使用 {@link #setMediaSource(MediaSource, boolean)} 和 {@link #prepare()}。
   */
  @UnstableApi
  @Deprecated
  void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState);

  /**
   * 清除播放列表，添加指定的 {@link MediaSource MediaSources} 并将位置重置为默认位置。
   *
   * @param mediaSources 新的 {@link MediaSource MediaSources}。
   */
  @UnstableApi
  void setMediaSources(List<MediaSource> mediaSources);

  /**
   * 清除播放列表并添加指定的 {@link MediaSource MediaSources}。
   *
   * @param mediaSources 新的 {@link MediaSource MediaSources}。
   * @param resetPosition 是否将播放位置重置为第一个 {@link Timeline.Window} 中的默认位置。如果为 false，播放将从 {@link #getCurrentMediaItemIndex()} 和 {@link #getCurrentPosition()} 定义的位置开始。
   */
  @UnstableApi
  void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition);

  /**
   * 清除播放列表并添加指定的 {@link MediaSource MediaSources}。
   *
   * @param mediaSources 新的 {@link MediaSource MediaSources}。
   * @param startMediaItemIndex 开始播放的媒体项索引。如果传递 {@link C#INDEX_UNSET}，则不会重置当前位置。
   * @param startPositionMs 开始播放的位置，单位为毫秒。如果传递 {@link C#TIME_UNSET}，则使用给定媒体源的默认位置。无论如何，如果 {@code startMediaItemIndex} 设置为 {@link C#INDEX_UNSET}，则忽略此参数且不会重置位置。
   */
  @UnstableApi
  void setMediaSources(
      List<MediaSource> mediaSources, int startMediaItemIndex, long startPositionMs);

  /**
   * 清除播放列表，添加指定的 {@link MediaSource} 并将位置重置为默认位置。
   *
   * @param mediaSource 新的 {@link MediaSource}。
   */
  @UnstableApi
  void setMediaSource(MediaSource mediaSource);

  /**
   * 清除播放列表并添加指定的 {@link MediaSource}。
   *
   * @param mediaSource 新的 {@link MediaSource}。
   * @param startPositionMs 开始播放的位置，单位为毫秒。如果传递 {@link C#TIME_UNSET}，则使用给定媒体源的默认位置。
   */
  @UnstableApi
  void setMediaSource(MediaSource mediaSource, long startPositionMs);

  /**
   * 清除播放列表并添加指定的 {@link MediaSource}。
   *
   * @param mediaSource 新的 {@link MediaSource}。
   * @param resetPosition 是否将播放位置重置为默认位置。如果为 false，播放将从 {@link #getCurrentMediaItemIndex()} 和 {@link #getCurrentPosition()} 定义的位置开始。
   */
  @UnstableApi
  void setMediaSource(MediaSource mediaSource, boolean resetPosition);

  /**
   * 将媒体源添加到播放列表的末尾。
   *
   * @param mediaSource 要添加的 {@link MediaSource}。
   */
  @UnstableApi
  void addMediaSource(MediaSource mediaSource);

  /**
   * 将媒体源添加到播放列表的指定索引处。
   *
   * @param index 添加源的索引。
   * @param mediaSource 要添加的 {@link MediaSource}。
   */
  @UnstableApi
  void addMediaSource(int index, MediaSource mediaSource);

  /**
   * 将媒体源列表添加到播放列表的末尾。
   *
   * @param mediaSources 要添加的 {@link MediaSource MediaSources}。
   */
  @UnstableApi
  void addMediaSources(List<MediaSource> mediaSources);

  /**
   * 将媒体源列表添加到播放列表的指定索引处。
   *
   * @param index 添加媒体源的索引。
   * @param mediaSources 要添加的 {@link MediaSource MediaSources}。
   */
  @UnstableApi
  void addMediaSources(int index, List<MediaSource> mediaSources);

  /**
   * 设置播放顺序。
   *
   * <p>传递的 {@link ShuffleOrder} 必须与当前播放列表的长度相同（{@link Player#getMediaItemCount()}）。
   *
   * @param shuffleOrder 播放顺序。
   */
  @UnstableApi
  void setShuffleOrder(ShuffleOrder shuffleOrder);

  /**
   * 设置 {@linkplain PreloadConfiguration 预加载配置} 以配置播放列表的预加载。
   *
   * @param preloadConfiguration 预加载配置。
   */
  @UnstableApi
  void setPreloadConfiguration(PreloadConfiguration preloadConfiguration);

  /** 返回 {@linkplain PreloadConfiguration 预加载配置}。 */
  @UnstableApi
  PreloadConfiguration getPreloadConfiguration();

  /**
   * {@inheritDoc}
   *
   * <p>如果 {@link MediaSource} 支持 {@linkplain MediaSource#canUpdateMediaItem}，ExoPlayer 将保留此 {@link MediaItem} 的现有 {@link MediaSource}。如果当前项被替换，这也不会中断正在进行的播放。
   */
  @Override
  void replaceMediaItem(int index, MediaItem mediaItem);

  /**
   * {@inheritDoc}
   *
   * <p>如果所有 {@link MediaSource} 实例都支持 {@linkplain MediaSource#canUpdateMediaItem}，ExoPlayer 将保留新 {@link MediaItem MediaItems} 的现有 {@link MediaSource} 实例。如果当前项被替换，这也不会中断正在进行的播放。
   */
  @Override
  void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems);

  /**
   * 设置要附加到底层 {@link android.media.AudioTrack} 的音频会话 ID。
   *
   * <p>音频会话 ID 可以使用 {@link Util#generateAudioSessionIdV21(Context)} 为 API 21+ 生成。
   *
   * @param audioSessionId 音频会话 ID，或 {@link C#AUDIO_SESSION_ID_UNSET} 表示由框架生成。
   */
  @UnstableApi
  void setAudioSessionId(int audioSessionId);
  /**
   * 返回音频会话标识符，如果未设置则返回 {@link C#AUDIO_SESSION_ID_UNSET}。
   *
   * @see Listener#onAudioSessionIdChanged(int)
   */
  @UnstableApi
  int getAudioSessionId();

  /** 设置附加到底层音频轨道的辅助音频效果的信息。 */
  @UnstableApi
  void setAuxEffectInfo(AuxEffectInfo auxEffectInfo);

  /** 从底层音频轨道分离之前附加的任何辅助音频效果。 */
  @UnstableApi
  void clearAuxEffectInfo();

  /**
   * 设置首选的音频设备。
   *
   * @param audioDeviceInfo 首选的 {@linkplain AudioDeviceInfo 音频设备}，或 null 以恢复默认设置。
   */
  @UnstableApi
  @RequiresApi(23)
  void setPreferredAudioDevice(@Nullable AudioDeviceInfo audioDeviceInfo);

  /**
   * 设置是否启用跳过音频流中的静音部分。
   *
   * @param skipSilenceEnabled 是否启用跳过音频流中的静音部分。
   */
  @UnstableApi
  void setSkipSilenceEnabled(boolean skipSilenceEnabled);

  /**
   * 返回是否启用跳过音频流中的静音部分。
   *
   * @see Listener#onSkipSilenceEnabledChanged(boolean)
   */
  @UnstableApi
  boolean getSkipSilenceEnabled();

  /**
   * 设置将应用于每个视频帧的 {@linkplain Effect 视频效果} 的 {@link List}。
   *
   * <p>如果 {@linkplain #setVideoSurface 直接向播放器传递 Surface}，则需要在调用此方法后通过向 {@linkplain Renderer 视频渲染器} 传递类型为 {@link Renderer#MSG_SET_VIDEO_OUTPUT_RESOLUTION} 的 {@linkplain #createMessage(PlayerMessage.Target) 消息} 来通知输出分辨率。对于 {@link SurfaceView}、{@link TextureView} 和 {@link SurfaceHolder} 输出，这会自动完成。
   *
   * <p>使用 {@linkplain Effect 视频效果} 存在以下限制：
   *
   * <ul>
   *   <li>{@code androidx.media3:media3-effect} 模块必须在运行时类路径中可用。{@code androidx.media3:media3-exoplayer} 并不显式依赖效果模块，因此应用必须确保其可用。它必须与应用使用的其他 {@code androidx.media3} 模块版本相同。
   *   <li>此功能仅适用于默认的 {@link MediaCodecVideoRenderer}，不适用于自定义或扩展的 {@linkplain Renderer 视频渲染器}。
   *   <li>此功能不适用于更新帧时间戳的 {@linkplain Effect 效果}。
   *   <li>此功能不适用于受 DRM 保护的内容。
   *   <li>此方法必须在调用 {@link #prepare()} 之前至少调用一次（以设置效果管道）。在调用 {@link #prepare()} 后，可以通过后续调用此方法更改效果。
   * </ul>
   *
   * @param videoEffects 要应用的 {@linkplain Effect 视频效果} 的 {@link List}。
   */
  @UnstableApi
  void setVideoEffects(List<Effect> videoEffects);

  /**
   * 设置 {@link C.VideoScalingMode}。
   *
   * <p>缩放模式仅在使用基于 {@link MediaCodec} 的视频 {@link Renderer} 且输出 Surface 由 {@link SurfaceView} 拥有时生效。
   *
   * @param videoScalingMode {@link C.VideoScalingMode}。
   */
  @UnstableApi
  void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode);

  /** 返回 {@link C.VideoScalingMode}。 */
  @UnstableApi
  @C.VideoScalingMode
  int getVideoScalingMode();

  /**
   * 设置播放器在提供视频输出 {@link Surface} 时将使用的 {@link C.VideoChangeFrameRateStrategy}。
   *
   * <p>该策略仅在使用基于 {@link MediaCodec} 的视频 {@link Renderer} 时生效。如果应用程序希望使用 {@link Surface#CHANGE_FRAME_RATE_ALWAYS}，
   * 应将模式设置为 {@link C#VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF} 以禁用 ExoPlayer 对 {@link Surface#setFrameRate} 的调用，
   * 然后从应用程序代码中直接调用 {@link Surface#setFrameRate}。
   *
   * @param videoChangeFrameRateStrategy {@link C.VideoChangeFrameRateStrategy}。
   */
  @UnstableApi
  void setVideoChangeFrameRateStrategy(
      @C.VideoChangeFrameRateStrategy int videoChangeFrameRateStrategy);

  /** 返回 {@link C.VideoChangeFrameRateStrategy}。 */
  @UnstableApi
  @C.VideoChangeFrameRateStrategy
  int getVideoChangeFrameRateStrategy();

  /**
   * 设置监听器以接收视频帧元数据事件。
   *
   * <p>此方法应由设置视频渲染 {@link Surface} 的组件调用。如果使用 ExoPlayer 的标准 UI 组件，则不应直接从应用程序代码调用此方法。
   *
   * @param listener 监听器。
   */
  @UnstableApi
  void setVideoFrameMetadataListener(VideoFrameMetadataListener listener);

  /**
   * 如果传入的监听器与当前监听器匹配，则清除接收视频帧元数据事件的监听器。否则不执行任何操作。
   *
   * @param listener 要清除的监听器。
   */
  @UnstableApi
  void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener);

  /**
   * 设置监听器以接收相机运动事件。
   *
   * @param listener 监听器。
   */
  @UnstableApi
  void setCameraMotionListener(CameraMotionListener listener);

  /**
   * 如果传入的监听器与当前监听器匹配，则清除接收相机运动事件的监听器。否则不执行任何操作。
   *
   * @param listener 要清除的监听器。
   */
  @UnstableApi
  void clearCameraMotionListener(CameraMotionListener listener);

  /**
   * 创建一个可以发送到 {@link PlayerMessage.Target} 的消息。默认情况下，消息将立即传递，而不会阻塞播放线程。默认的 {@link PlayerMessage#getType()} 为 0，默认的 {@link PlayerMessage#getPayload()} 为 null。如果使用 {@link PlayerMessage#setPosition(long)} 指定了位置，则消息将在当前媒体项中定义的位置传递，位置由 {@link #getCurrentMediaItemIndex()} 确定。或者，可以使用 {@link PlayerMessage#setPosition(int, long)} 在特定媒体项中发送消息。
   */
  @UnstableApi
  PlayerMessage createMessage(PlayerMessage.Target target);

  /**
   * 设置控制搜索操作执行方式的参数。
   *
   * @param seekParameters 搜索参数，或 {@code null} 以使用默认值。
   */
  @UnstableApi
  void setSeekParameters(@Nullable SeekParameters seekParameters);

  /** 返回播放器当前活动的 {@link SeekParameters}。 */
  @UnstableApi
  SeekParameters getSeekParameters();

  /**
   * 设置是否允许播放器在空闲状态下继续持有有限的资源（例如视频解码器）。这样做可以减少在播放需要相同资源的新内容时的延迟。
   *
   * <p>应谨慎使用此模式，因为持有有限的资源可能会阻止其他播放器或媒体组件获取它们。只有在以下<em>两个</em>条件都成立时才应启用：
   *
   * <ul>
   *   <li>拥有播放器的应用程序处于前台。
   *   <li>播放器的使用方式可能受益于前台模式。为此，必须使用相同的播放器实例播放多个内容片段，并且播放之间必须有间隙（即调用 {@link #stop} 停止一个播放，并在稍后调用 {@link #prepare} 开始新的播放）。
   * </ul>
   *
   * <p>请注意，前台模式<em>不</em>适用于在播放之间没有间隙的情况下切换内容。对于此用例，无需调用 {@link #stop}，只需为新媒体调用 {@link #prepare} 即可，即使未启用前台模式，也会保留有限的资源。
   *
   * <p>如果启用了前台模式，应用程序有责任在不再满足上述条件时禁用它。
   *
   * @param foregroundMode 是否允许播放器在空闲状态下继续持有有限的资源。
   */
  @UnstableApi
  void setForegroundMode(boolean foregroundMode);
  /**
   * 设置是否在每个媒体项结束时暂停播放。
   *
   * <p>这意味着播放器将在当前 {@link #getCurrentTimeline() 时间轴} 的每个窗口结束时暂停。当这种情况发生时，监听器将通过 {@link Player.Listener#onPlayWhenReadyChanged(boolean, int)} 被通知，原因值为 {@link Player#PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM}。
   *
   * @param pauseAtEndOfMediaItems 是否在每个媒体项结束时暂停播放。
   */
  @UnstableApi
  void setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems);

  /**
   * 返回播放器是否在每个媒体项结束时暂停播放。
   *
   * @see #setPauseAtEndOfMediaItems(boolean)
   */
  @UnstableApi
  boolean getPauseAtEndOfMediaItems();

  /** 返回当前正在播放的音频格式，如果没有播放音频则返回 null。 */
  @UnstableApi
  @Nullable
  Format getAudioFormat();

  /** 返回当前正在播放的视频格式，如果没有播放视频则返回 null。 */
  @UnstableApi
  @Nullable
  Format getVideoFormat();

  /** 返回音频的 {@link DecoderCounters}，如果没有播放音频则返回 null。 */
  @UnstableApi
  @Nullable
  DecoderCounters getAudioDecoderCounters();

  /** 返回视频的 {@link DecoderCounters}，如果没有播放视频则返回 null。 */
  @UnstableApi
  @Nullable
  DecoderCounters getVideoDecoderCounters();

  /**
   * 设置当音频从耳机切换到设备扬声器时，播放器是否应自动暂停。有关更多信息，请参阅 <a
   * href="https://developer.android.com/guide/topics/media-apps/volume-and-earphones#becoming-noisy">音频变得嘈杂</a> 文档。
   *
   * @param handleAudioBecomingNoisy 当音频从耳机切换到设备扬声器时，播放器是否应自动暂停。
   */
  void setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy);

  /**
   * 设置播放器在屏幕关闭时如何保持设备唤醒以进行播放。
   *
   * <p>启用此功能需要 {@link android.Manifest.permission#WAKE_LOCK} 权限。它应与前台 {@link android.app.Service} 一起使用，用于屏幕关闭时的播放场景（例如后台音频播放）。对于屏幕保持开启的播放场景（例如前台视频播放），此功能无意义。
   *
   * <p>启用后，当播放器处于 {@link #STATE_READY} 或 {@link #STATE_BUFFERING} 状态且 {@code playWhenReady = true} 时，将持有锁（{@link android.os.PowerManager.WakeLock} / {@link android.net.wifi.WifiManager.WifiLock}）。持有的锁取决于指定的 {@link C.WakeMode}。
   *
   * @param wakeMode 用于在播放期间保持设备唤醒的 {@link C.WakeMode} 选项。
   */
  void setWakeMode(@C.WakeMode int wakeMode);

  /**
   * 设置此播放器的 {@link C.Priority}。
   *
   * <p>优先级可能影响同一应用程序中运行的多个播放器或其他组件之间的资源分配。
   *
   * <p>如果设置了 {@linkplain #setPriorityTaskManager PriorityTaskManager}，则此优先级将用于 {@link PriorityTaskManager}。
   *
   * @param priority {@link C.Priority}。
   */
  @UnstableApi
  void setPriority(@C.Priority int priority);

  /**
   * 设置 {@link PriorityTaskManager}，或 null 以清除之前设置的优先级任务管理器。
   *
   * <p>通过 {@link #setPriority} 设置的优先级（或默认的 {@link C#PRIORITY_PLAYBACK}）将在播放器加载时使用。
   *
   * @param priorityTaskManager {@link PriorityTaskManager}，或 null 以清除之前设置的优先级任务管理器。
   */
  @UnstableApi
  void setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager);

  /**
   * 返回播放器是否已暂停其主循环以在卸载调度模式下节省电量。
   *
   * <p>在屏幕关闭时播放卸载音频时，卸载调度模式应显著节省电量。
   *
   * <p>卸载调度仅在以下所有条件满足时启用：
   *
   * <ul>
   *   <li>通过 {@link TrackSelectionParameters.Builder#setAudioOffloadPreferences} 启用了音频卸载渲染。
   *   <li>正在播放的音频轨道是设备支持卸载的格式（例如 MP3 或 AAC）。
   *   <li>{@link AudioSink} 正在使用卸载 {@link AudioTrack} 播放。
   * </ul>
   *
   * @see AudioOffloadListener#onSleepingForOffloadChanged(boolean)
   */
  @UnstableApi
  boolean isSleepingForOffload();

  /**
   * 返回是否启用了 <a href="https://source.android.com/devices/tv/multimedia-tunneling">隧道模式</a> 用于当前选定的轨道。
   *
   * @see Player.Listener#onTracksChanged(Tracks)
   */
  @UnstableApi
  boolean isTunnelingEnabled();

  /**
   * {@inheritDoc}
   *
   * <p>上述规则的例外是 {@link #isReleased()}，它可以在已释放的播放器上调用。
   */
  @Override
  void release();

  /**
   * 返回是否已对播放器调用 {@link #release()}。
   *
   * <p>此方法允许在 {@link #release()} 之后调用。
   */
  @UnstableApi
  boolean isReleased();

  /**
   * 设置渲染图像将转发到的 {@link ImageOutput}。
   *
   * @param imageOutput {@link ImageOutput}。可以为 null 以清除之前设置的图像输出。
   */
  @UnstableApi
  void setImageOutput(@Nullable ImageOutput imageOutput);
}
