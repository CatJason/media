/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.video;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.exoplayer.video.VideoSink.INPUT_TYPE_SURFACE;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Looper;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PreviewingVideoGraph;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.VideoGraph;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlaybackException;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Processes input from {@link VideoSink} instances, plumbing the data through a {@link VideoGraph}
 * and rendering the output.
 */
@UnstableApi
@RestrictTo({Scope.LIBRARY_GROUP})
public final class PlaybackVideoGraphWrapper implements VideoSinkProvider, VideoGraph.Listener {

  /**
   * {@link PlaybackVideoGraphWrapper} 事件的监听器。
   */
  public interface Listener {

    /**
     * 当视频帧处理器渲染第一帧时调用。
     *
     * @param playbackVideoGraphWrapper 触发此事件的 {@link PlaybackVideoGraphWrapper}。
     */
    void onFirstFrameRendered(PlaybackVideoGraphWrapper playbackVideoGraphWrapper);

    /**
     * 当视频帧处理器丢弃一帧时调用。
     *
     * @param playbackVideoGraphWrapper 触发此事件的 {@link PlaybackVideoGraphWrapper}。
     */
    void onFrameDropped(PlaybackVideoGraphWrapper playbackVideoGraphWrapper);

    /**
     * 在设置 Surface 后首次渲染帧之前调用，以及每次视频渲染的尺寸、旋转或像素宽高比发生变化时调用。
     *
     * @param playbackVideoGraphWrapper 触发此事件的 {@link PlaybackVideoGraphWrapper}。
     * @param videoSize                 视频尺寸。
     */
    void onVideoSizeChanged(
        PlaybackVideoGraphWrapper playbackVideoGraphWrapper, VideoSize videoSize);

    /**
     * 当视频帧处理器遇到错误时调用。
     *
     * @param playbackVideoGraphWrapper     触发此事件的 {@link PlaybackVideoGraphWrapper}。
     * @param videoFrameProcessingException 错误信息。
     */
    void onError(
        PlaybackVideoGraphWrapper playbackVideoGraphWrapper,
        VideoFrameProcessingException videoFrameProcessingException);
  }

  /**
   * {@link PlaybackVideoGraphWrapper} 实例的构建器。
   */
  public static final class Builder {

    private final Context context;
    private final VideoFrameReleaseControl videoFrameReleaseControl;

    private VideoFrameProcessor.@MonotonicNonNull Factory videoFrameProcessorFactory;
    private PreviewingVideoGraph.@MonotonicNonNull Factory previewingVideoGraphFactory;
    private List<Effect> compositionEffects;
    private Clock clock;
    private boolean built;

    /**
     * 创建构建器。
     */
    public Builder(Context context, VideoFrameReleaseControl videoFrameReleaseControl) {
      this.context = context.getApplicationContext();
      this.videoFrameReleaseControl = videoFrameReleaseControl;
      compositionEffects = ImmutableList.of();
      clock = Clock.DEFAULT;
    }

    /**
     * 设置用于创建 {@link VideoFrameProcessor} 实例的 {@link VideoFrameProcessor.Factory}。
     *
     * <p>默认情况下，将使用 {@code DefaultVideoFrameProcessor.Factory} 及其默认值。
     *
     * @param videoFrameProcessorFactory {@link VideoFrameProcessor.Factory}。
     * @return 此构建器，方便链式调用。
     */
    @CanIgnoreReturnValue
    public Builder setVideoFrameProcessorFactory(
        VideoFrameProcessor.Factory videoFrameProcessorFactory) {
      this.videoFrameProcessorFactory = videoFrameProcessorFactory;
      return this;
    }

    /**
     * 设置用于创建 {@link PreviewingVideoGraph} 实例的 {@link PreviewingVideoGraph.Factory}。
     *
     * <p>默认情况下，将使用 {@code PreviewingSingleInputVideoGraph.Factory}。
     *
     * @param previewingVideoGraphFactory {@link PreviewingVideoGraph.Factory}。
     * @return 此构建器，方便链式调用。
     */
    @CanIgnoreReturnValue
    public Builder setPreviewingVideoGraphFactory(
        PreviewingVideoGraph.Factory previewingVideoGraphFactory) {
      this.previewingVideoGraphFactory = previewingVideoGraphFactory;
      return this;
    }

    /**
     * 设置应用于合成 sinks 数据后的 {@linkplain Effect 效果}。
     *
     * @param compositionEffects 合成 {@linkplain Effect 效果}。
     * @return 此构建器，方便链式调用。
     */
    @CanIgnoreReturnValue
    public Builder setCompositionEffects(List<Effect> compositionEffects) {
      this.compositionEffects = compositionEffects;
      return this;
    }

    /**
     * 设置要使用的 {@link Clock}。
     *
     * <p>默认情况下，将使用 {@link Clock#DEFAULT}。
     *
     * @param clock {@link Clock}。
     * @return 此构建器，方便链式调用。
     */
    @CanIgnoreReturnValue
    public Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * 构建 {@link PlaybackVideoGraphWrapper}。
     *
     * <p>此方法最多只能调用一次，如果已经调用过，则会抛出 {@link IllegalStateException}。
     */
    public PlaybackVideoGraphWrapper build() {
      checkState(!built); // 检查是否已经构建过，如果已经构建则抛出异常

      // 如果未设置 previewingVideoGraphFactory，则使用默认实现
      if (previewingVideoGraphFactory == null) {
        // 如果未设置 videoFrameProcessorFactory，则使用默认实现
        if (videoFrameProcessorFactory == null) {
          videoFrameProcessorFactory = new ReflectiveDefaultVideoFrameProcessorFactory();
        }
        // 使用默认的 PreviewingSingleInputVideoGraphFactory
        previewingVideoGraphFactory =
            new ReflectivePreviewingSingleInputVideoGraphFactory(videoFrameProcessorFactory);
      }

      // 创建 PlaybackVideoGraphWrapper 实例
      PlaybackVideoGraphWrapper playbackVideoGraphWrapper = new PlaybackVideoGraphWrapper(this);
      built = true; // 标记为已构建
      return playbackVideoGraphWrapper;
    }
  }

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({STATE_CREATED, STATE_INITIALIZED, STATE_RELEASED})
  private @interface State {

  }
  private static final int STATE_CREATED = 0; // 状态：已创建
  private static final int STATE_INITIALIZED = 1; // 状态：已初始化
  private static final int STATE_RELEASED = 2; // 状态：已释放

  private static final Executor NO_OP_EXECUTOR = runnable -> {
    // 无操作的 Executor，用于占位或默认值
  };

  private final Context context; // 上下文对象
  private final InputVideoSink inputVideoSink; // 视频输入接收器
  private final VideoFrameReleaseControl videoFrameReleaseControl; // 视频帧释放控制器
  private final VideoFrameRenderControl videoFrameRenderControl; // 视频帧渲染控制器
  private final PreviewingVideoGraph.Factory previewingVideoGraphFactory; // 预览视频图的工厂
  private final List<Effect> compositionEffects; // 合成效果列表
  private final VideoSink defaultVideoSink; // 默认视频接收器
  private final Clock clock; // 时钟对象
  private final CopyOnWriteArraySet<PlaybackVideoGraphWrapper.Listener> listeners; // 监听器集合

  private @MonotonicNonNull Format outputFormat; // 输出格式（非空，初始化后不会为 null）
  private @MonotonicNonNull VideoFrameMetadataListener videoFrameMetadataListener; // 视频帧元数据监听器（非空，初始化后不会为 null）
  private @MonotonicNonNull HandlerWrapper handler; // 处理器封装类（非空，初始化后不会为 null）
  private @MonotonicNonNull PreviewingVideoGraph videoGraph; // 预览视频图（非空，初始化后不会为 null）
  @Nullable
  private Pair<Surface, Size> currentSurfaceAndSize; // 当前的 Surface 和尺寸（可能为 null）
  private int pendingFlushCount; // 待刷新的计数
  private @State int state; // 当前状态（STATE_CREATED、STATE_INITIALIZED 或 STATE_RELEASED）

  /**
   * 将缓冲区时间戳（播放器位置，包含渲染器偏移量）转换为合成时间戳，单位为微秒。
   * 合成时间从零开始，将此调整值加到缓冲区时间戳上即可得到合成时间。
   */
  private long bufferTimestampAdjustmentUs;

  private PlaybackVideoGraphWrapper(Builder builder) {
    context = builder.context;
    inputVideoSink = new InputVideoSink(context);
    clock = builder.clock;
    videoFrameReleaseControl = builder.videoFrameReleaseControl;
    videoFrameReleaseControl.setClock(clock);
    videoFrameRenderControl =
        new VideoFrameRenderControl(new FrameRendererImpl(), videoFrameReleaseControl);
    previewingVideoGraphFactory = checkStateNotNull(builder.previewingVideoGraphFactory);
    compositionEffects = builder.compositionEffects;
    defaultVideoSink = new DefaultVideoSink(videoFrameReleaseControl, videoFrameRenderControl);
    listeners = new CopyOnWriteArraySet<>();
    state = STATE_CREATED;
    addListener(inputVideoSink);
  }

  /**
   * 添加一个 {@link PlaybackVideoGraphWrapper.Listener} 监听器。
   *
   * @param listener 要添加的监听器。
   */
  public void addListener(PlaybackVideoGraphWrapper.Listener listener) {
    listeners.add(listener); // 将监听器添加到监听器集合中
  }

  /**
   * 移除一个 {@link PlaybackVideoGraphWrapper.Listener} 监听器。
   *
   * @param listener 要移除的监听器。
   */
  public void removeListener(PlaybackVideoGraphWrapper.Listener listener) {
    listeners.remove(listener); // 从监听器集合中移除指定的监听器
  }
  // VideoSinkProvider methods

  @Override
  public VideoSink getSink() {
    return inputVideoSink;
  }

  @Override
  public void setOutputSurfaceInfo(Surface outputSurface, Size outputResolution) {
    if (currentSurfaceAndSize != null
        && currentSurfaceAndSize.first.equals(outputSurface)
        && currentSurfaceAndSize.second.equals(outputResolution)) {
      return;
    }
    currentSurfaceAndSize = Pair.create(outputSurface, outputResolution);
    maybeSetOutputSurfaceInfo(
        outputSurface, outputResolution.getWidth(), outputResolution.getHeight());
  }

  @Override
  public void clearOutputSurfaceInfo() {
    maybeSetOutputSurfaceInfo(
        /* surface= */ null,
        /* width= */ Size.UNKNOWN.getWidth(),
        /* height= */ Size.UNKNOWN.getHeight());
    currentSurfaceAndSize = null;
  }

  @Override
  public void release() {
    if (state == STATE_RELEASED) {
      return;
    }

    if (handler != null) {
      handler.removeCallbacksAndMessages(/* token= */ null);
    }

    if (videoGraph != null) {
      videoGraph.release();
    }
    currentSurfaceAndSize = null;
    state = STATE_RELEASED;
  }

  // VideoGraph.Listener

  @Override
  public void onOutputSizeChanged(int width, int height) {
    // 即使正在刷新，我们也会将输出尺寸变化转发给接收器。
    Format format = new Format.Builder().setWidth(width).setHeight(height).build(); // 构建新的格式对象
    defaultVideoSink.onInputStreamChanged(INPUT_TYPE_SURFACE, format); // 通知接收器输入流尺寸变化
  }

  @Override
  public void onOutputFrameAvailableForRendering(long framePresentationTimeUs) {
    if (pendingFlushCount > 0) {
      // 如果正在刷新，则忽略可用的帧
      return;
    }
    // 帧的呈现时间是相对于合成开始的，并且不包含渲染器偏移量
    videoFrameRenderControl.onOutputFrameAvailableForRendering(
        framePresentationTimeUs - bufferTimestampAdjustmentUs); // 通知渲染控制器帧可用于渲染
  }

  @Override
  public void onEnded(long finalFramePresentationTimeUs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onError(VideoFrameProcessingException exception) {
    for (PlaybackVideoGraphWrapper.Listener listener : listeners) {
      listener.onError(/* playbackVideoGraphWrapper= */ this, exception);
    }
  }

  // Internal methods
  /**
   * 初始化视频帧处理器。
   *
   * @param sourceFormat 源视频格式。
   * @return 初始化后的视频帧处理器。
   * @throws VideoSink.VideoSinkException 如果初始化过程中发生异常。
   */
  private VideoFrameProcessor initialize(Format sourceFormat) throws VideoSink.VideoSinkException {
    // 检查当前状态是否为 STATE_CREATED，确保在正确状态下进行初始化
    checkState(state == STATE_CREATED);

    // 获取调整后的输入颜色信息
    ColorInfo inputColorInfo = getAdjustedInputColorInfo(sourceFormat.colorInfo);
    ColorInfo outputColorInfo = inputColorInfo;

    // 如果输入颜色传输为 HLG（混合对数伽马）且设备 API 版本低于 34
    if (inputColorInfo.colorTransfer == C.COLOR_TRANSFER_HLG && Util.SDK_INT < 34) {
      // 从 API 33 开始支持 PQ（感知量化）的 SurfaceView 输出，但 HLG 输出从 API 34 开始支持。
      // 因此，在 API 34 以下将 HLG 转换为 PQ，以便 HLG 输入可以在 API 33 上正确显示。
      outputColorInfo =
          inputColorInfo.buildUpon().setColorTransfer(C.COLOR_TRANSFER_ST2084).build();
    }

    // 创建 Handler，用于处理回调
    handler = clock.createHandler(checkStateNotNull(Looper.myLooper()), /* callback= */ null);

    try {
      // 使用预览视频图工厂创建视频图实例
      videoGraph =
          previewingVideoGraphFactory.create(
              context, // 上下文
              outputColorInfo, // 输出颜色信息
              DebugViewProvider.NONE, // 调试视图提供者（无）
              /* listener= */ this, // 监听器（当前对象）
              /* listenerExecutor= */ handler::post, // 监听器执行器（通过 Handler 发布）
              /* compositionEffects= */ ImmutableList.of(), // 合成效果（空列表）
              /* initialTimestampOffsetUs= */ 0); // 初始时间戳偏移量（0 微秒）

      // 如果当前 Surface 和尺寸不为空，则设置输出 Surface 信息
      if (currentSurfaceAndSize != null) {
        Surface surface = currentSurfaceAndSize.first;
        Size size = currentSurfaceAndSize.second;
        maybeSetOutputSurfaceInfo(surface, size.getWidth(), size.getHeight());
      }

      // 注册输入流（输入索引为 0）
      videoGraph.registerInput(/* inputIndex= */ 0);
    } catch (VideoFrameProcessingException e) {
      // 如果发生异常，抛出 VideoSinkException
      throw new VideoSink.VideoSinkException(e, sourceFormat);
    }

    // 初始化默认视频接收器
    defaultVideoSink.initialize(sourceFormat);

    // 更新状态为 STATE_INITIALIZED
    state = STATE_INITIALIZED;

    // 返回视频图的处理器（输入索引为 0）
    return videoGraph.getProcessor(/* inputIndex= */ 0);
  }

  private boolean isInitialized() {
    return state == STATE_INITIALIZED;
  }
  /**
   * 设置输出 Surface 信息（如果视频图已初始化）。
   *
   * @param surface 输出 Surface，如果为 null 则清除 Surface 信息。
   * @param width Surface 的宽度。
   * @param height Surface 的高度。
   */
  private void maybeSetOutputSurfaceInfo(@Nullable Surface surface, int width, int height) {
    // 如果视频图未初始化，则直接返回
    if (videoGraph == null) {
      return;
    }

    // 更新视频图和默认视频接收器的 Surface 信息
    if (surface != null) {
      // 如果 Surface 不为 null，则设置视频图的输出 Surface 信息
      videoGraph.setOutputSurfaceInfo(new SurfaceInfo(surface, width, height));
      // 设置默认视频接收器的输出 Surface 信息
      defaultVideoSink.setOutputSurfaceInfo(surface, new Size(width, height));
    } else {
      // 如果 Surface 为 null，则清除视频图的输出 Surface 信息
      videoGraph.setOutputSurfaceInfo(/* outputSurfaceInfo= */ null);
      // 清除默认视频接收器的输出 Surface 信息
      defaultVideoSink.clearOutputSurfaceInfo();
    }
  }

  /**
   * 检查视频接收器是否准备好渲染。
   *
   * @param rendererOtherwiseReady 如果渲染器在其他方面已准备好，则为 true。
   * @return 如果视频接收器准备好渲染，则返回 true。
   */
  private boolean isReady(boolean rendererOtherwiseReady) {
    return defaultVideoSink.isReady(
        /* rendererOtherwiseReady= */ rendererOtherwiseReady && pendingFlushCount == 0);
  }

  /**
   * 检查是否已释放指定呈现时间的帧。
   *
   * @param presentationTimeUs 帧的呈现时间，单位为微秒。
   * @return 如果已释放帧，则返回 true。
   */
  private boolean hasReleasedFrame(long presentationTimeUs) {
    return pendingFlushCount == 0 && videoFrameRenderControl.hasReleasedFrame(presentationTimeUs);
  }

  /**
   * 逐步渲染可用的视频帧。
   *
   * @param positionUs 当前的播放位置，单位为微秒。
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} 的时间，单位为微秒，
   *                          大约在播放位置为 {@code positionUs} 时记录。
   * @throws ExoPlaybackException 如果渲染过程中发生异常。
   */
  private void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    videoFrameRenderControl.render(positionUs, elapsedRealtimeUs);
  }

  /**
   * 刷新视频接收器，可选择重置位置。
   *
   * @param resetPosition 如果为 true，则重置播放位置。
   */
  private void flush(boolean resetPosition) {
    // 如果未初始化，则直接返回
    if (!isInitialized()) {
      return;
    }

    // 增加待刷新计数
    pendingFlushCount++;

    // 刷新默认视频接收器
    defaultVideoSink.flush(resetPosition);

    // 处理待处理的视频图回调，确保视频尺寸变化传递到视频渲染控制器
    checkStateNotNull(handler).post(() -> pendingFlushCount--);
  }

  private void setVideoFrameMetadataListener(
      VideoFrameMetadataListener videoFrameMetadataListener) {
    this.videoFrameMetadataListener = videoFrameMetadataListener;
  }

  private void setPlaybackSpeed(float speed) {
    defaultVideoSink.setPlaybackSpeed(speed);
  }

  private void onStreamOffsetChange(
      long bufferTimestampAdjustmentUs, long bufferPresentationTimeUs, long streamOffsetUs) {
    this.bufferTimestampAdjustmentUs = bufferTimestampAdjustmentUs;
    videoFrameRenderControl.onStreamOffsetChange(bufferPresentationTimeUs, streamOffsetUs);
  }

  private static ColorInfo getAdjustedInputColorInfo(@Nullable ColorInfo inputColorInfo) {
    if (inputColorInfo == null || !inputColorInfo.isDataSpaceValid()) {
      return ColorInfo.SDR_BT709_LIMITED;
    }

    return inputColorInfo;
  }
  /**
   * 接收来自 ExoPlayer 渲染器的输入并将其转发到视频图。
   */
  private final class InputVideoSink implements VideoSink, PlaybackVideoGraphWrapper.Listener {

    private final int videoFrameProcessorMaxPendingFrameCount; // 视频帧处理器允许的最大待处理帧数
    private final ArrayList<Effect> videoEffects; // 视频效果列表
    private final VideoFrameReleaseControl.FrameReleaseInfo frameReleaseInfo; // 帧释放信息

    private @MonotonicNonNull VideoFrameProcessor videoFrameProcessor; // 视频帧处理器（非空，初始化后不会为 null）
    @Nullable
    private Format inputFormat; // 输入格式（可能为 null）
    private @InputType int inputType; // 输入类型
    private long inputStreamStartPositionUs; // 输入流的起始位置，单位为微秒
    private long inputStreamOffsetUs; // 输入流的偏移量，单位为微秒
    private long inputBufferTimestampAdjustmentUs; // 输入缓冲区时间戳调整值，单位为微秒
    private long lastResetPositionUs; // 最后一次重置的位置，单位为微秒
    private boolean pendingInputStreamOffsetChange; // 是否等待输入流偏移量变化

    /**
     * 输入流中最后一帧的缓冲区呈现时间，单位为微秒。
     */
    private long finalBufferPresentationTimeUs;

    /**
     * 最近注册的帧的缓冲区呈现时间戳，单位为微秒。
     */
    private long lastBufferPresentationTimeUs;

    private boolean hasRegisteredFirstInputStream; // 是否已注册第一个输入流
    private boolean isInputStreamChangePending; // 是否等待输入流变化
    private long pendingInputStreamBufferPresentationTimeUs; // 等待的输入流缓冲区呈现时间，单位为微秒
    private VideoSink.Listener listener; // 监听器
    private Executor listenerExecutor; // 监听器执行器

    /**
     * 创建一个新的实例。
     */
    public InputVideoSink(Context context) {
      // TODO b/226330223 - 研究在允许丢帧时增加帧数的可能性。
      // TODO b/278234847 - 评估在不允许丢帧时限制帧数是否减少解码器超时，并考虑恢复。
      videoFrameProcessorMaxPendingFrameCount =
          Util.getMaxPendingFramesCountForMediaCodecDecoders(context); // 获取 MediaCodec 解码器的最大待处理帧数
      videoEffects = new ArrayList<>(); // 初始化视频效果列表
      frameReleaseInfo = new VideoFrameReleaseControl.FrameReleaseInfo(); // 初始化帧释放信息
      finalBufferPresentationTimeUs = C.TIME_UNSET; // 初始化最后一帧的呈现时间为未设置
      lastBufferPresentationTimeUs = C.TIME_UNSET; // 初始化最近注册的帧的呈现时间为未设置
      listener = VideoSink.Listener.NO_OP; // 初始化监听器为无操作
      listenerExecutor = NO_OP_EXECUTOR; // 初始化监听器执行器为无操作
    }

    @Override
    public void onRendererEnabled(boolean mayRenderStartOfStream) {
      defaultVideoSink.onRendererEnabled(mayRenderStartOfStream);
    }

    @Override
    public void onRendererDisabled() {
      defaultVideoSink.onRendererDisabled();
    }

    @Override
    public void onRendererStarted() {
      defaultVideoSink.onRendererStarted();
    }

    @Override
    public void onRendererStopped() {
      defaultVideoSink.onRendererStopped();
    }

    @Override
    public void setListener(Listener listener, Executor executor) {
      this.listener = listener;
      listenerExecutor = executor;
    }

    @Override
    public void initialize(Format sourceFormat) throws VideoSinkException {
      checkState(!isInitialized());
      videoFrameProcessor = PlaybackVideoGraphWrapper.this.initialize(sourceFormat);
    }

    @Override
    @EnsuresNonNullIf(result = true, expression = "videoFrameProcessor")
    public boolean isInitialized() {
      return videoFrameProcessor != null;
    }

    @Override
    public void flush(boolean resetPosition) {
      // 如果已初始化，则刷新视频帧处理器
      if (isInitialized()) {
        videoFrameProcessor.flush();
      }

      // 重置状态
      hasRegisteredFirstInputStream = false; // 标记为未注册第一个输入流
      finalBufferPresentationTimeUs = C.TIME_UNSET; // 重置最后一帧的呈现时间为未设置
      lastBufferPresentationTimeUs = C.TIME_UNSET; // 重置最近注册的帧的呈现时间为未设置

      // 调用外部类的 flush 方法
      PlaybackVideoGraphWrapper.this.flush(resetPosition);

      // 重置等待的输入流缓冲区呈现时间为未设置
      pendingInputStreamBufferPresentationTimeUs = C.TIME_UNSET;

      // 注意：不更改输入流偏移量或重置待处理的输入流偏移量变化，以便在下一帧输入时通知。
      // 不重置 isInputStreamChangePending，因为在 seek 操作后不保证会接收到新的输入流。
    }

    @Override
    public boolean isReady(boolean rendererOtherwiseReady) {
      return PlaybackVideoGraphWrapper.this.isReady(
          /* rendererOtherwiseReady= */ rendererOtherwiseReady && isInitialized());
    }

    @Override
    public boolean isEnded() {
      return isInitialized()
          && finalBufferPresentationTimeUs != C.TIME_UNSET
          && PlaybackVideoGraphWrapper.this.hasReleasedFrame(finalBufferPresentationTimeUs);
    }

    @Override
    public void onInputStreamChanged(@InputType int inputType, Format format) {
      // 检查是否已初始化
      checkState(isInitialized());

      // 检查输入类型是否支持
      switch (inputType) {
        case INPUT_TYPE_SURFACE:
        case INPUT_TYPE_BITMAP:
          break;
        default:
          throw new UnsupportedOperationException("Unsupported input type " + inputType);
      }

      // 设置视频帧释放控制器的帧率
      videoFrameReleaseControl.setFrameRate(format.frameRate);

      // 更新输入类型和输入格式
      this.inputType = inputType;
      this.inputFormat = format;

      // 如果尚未注册第一个输入流
      if (!hasRegisteredFirstInputStream) {
        // 尝试注册输入流
        maybeRegisterInputStream();
        hasRegisteredFirstInputStream = true; // 标记为已注册第一个输入流

        // 如果输入流注册是待处理的，并且 seek 操作导致格式变化，执行会到达这里
        // 在 registerInputFrame() 之前。重置 pendingInputStreamTimestampUs，
        // 避免在 registerInputFrame() 中再次注册相同的输入流。
        isInputStreamChangePending = false;
        pendingInputStreamBufferPresentationTimeUs = C.TIME_UNSET;
      } else {
        // 如果执行到这里，说明至少已经注册了一帧进行处理
        checkState(lastBufferPresentationTimeUs != C.TIME_UNSET);

        // 标记输入流变化为待处理
        isInputStreamChangePending = true;
        pendingInputStreamBufferPresentationTimeUs = lastBufferPresentationTimeUs;
      }
    }

    @Override
    public Surface getInputSurface() {
      checkState(isInitialized());
      return checkStateNotNull(videoFrameProcessor).getInputSurface();
    }

    @Override
    public void setVideoFrameMetadataListener(
        VideoFrameMetadataListener videoFrameMetadataListener) {
      PlaybackVideoGraphWrapper.this.setVideoFrameMetadataListener(videoFrameMetadataListener);
    }

    @Override
    public void setPlaybackSpeed(@FloatRange(from = 0, fromInclusive = false) float speed) {
      PlaybackVideoGraphWrapper.this.setPlaybackSpeed(speed);
    }

    @Override
    public void setVideoEffects(List<Effect> videoEffects) {
      if (this.videoEffects.equals(videoEffects)) {
        return;
      }
      setPendingVideoEffects(videoEffects);
      maybeRegisterInputStream();
    }

    @Override
    public void setPendingVideoEffects(List<Effect> videoEffects) {
      this.videoEffects.clear();
      this.videoEffects.addAll(videoEffects);
      this.videoEffects.addAll(compositionEffects);
    }

    @Override
    public void setStreamTimestampInfo(
        long streamStartPositionUs,
        long streamOffsetUs,
        long bufferTimestampAdjustmentUs,
        long lastResetPositionUs) {
      // Ors because this method could be called multiple times on a stream offset change.
      pendingInputStreamOffsetChange |=
          inputStreamOffsetUs != streamOffsetUs
              || inputBufferTimestampAdjustmentUs != bufferTimestampAdjustmentUs;
      inputStreamStartPositionUs = streamStartPositionUs;
      inputStreamOffsetUs = streamOffsetUs;
      inputBufferTimestampAdjustmentUs = bufferTimestampAdjustmentUs;
      this.lastResetPositionUs = lastResetPositionUs;
    }

    @Override
    public void setOutputSurfaceInfo(Surface outputSurface, Size outputResolution) {
      PlaybackVideoGraphWrapper.this.setOutputSurfaceInfo(outputSurface, outputResolution);
    }

    @Override
    public void clearOutputSurfaceInfo() {
      PlaybackVideoGraphWrapper.this.clearOutputSurfaceInfo();
    }

    @Override
    public void setChangeFrameRateStrategy(
        @C.VideoChangeFrameRateStrategy int changeFrameRateStrategy) {
      defaultVideoSink.setChangeFrameRateStrategy(changeFrameRateStrategy);
    }

    @Override
    public void enableMayRenderStartOfStream() {
      defaultVideoSink.enableMayRenderStartOfStream();
    }

    @Override
    public boolean handleInputFrame(
        long framePresentationTimeUs, // 帧的呈现时间，单位为微秒
        boolean isLastFrame, // 是否为最后一帧
        long positionUs, // 当前的播放位置，单位为微秒
        long elapsedRealtimeUs, // 实时时间，单位为微秒
        VideoFrameHandler videoFrameHandler // 视频帧处理器
    ) throws VideoSinkException {
      // 检查是否已初始化
      checkState(isInitialized());

      // 视频接收器接收的帧具有单调递增、无偏移的帧时间戳。
      // 例如，对于两个十秒长的视频，第二个视频的第一帧在 VideoFrameProcessor 中的时间戳应为 10 秒；
      // 而在 ExoPlayer 中，该帧的时间戳为 0 秒，但 streamOffset 会增加 10 秒以包含第一个视频的时长。
      // 因此，需要此校正以处理 ExoPlayer 和 VideoFrameProcessor 之间对呈现时间戳的不同处理方式。
      //
      // inputBufferTimestampAdjustmentUs 将帧的呈现时间（相对于合成的开始）调整为缓冲区时间戳（对应于播放器位置）。
      long bufferPresentationTimeUs = framePresentationTimeUs - inputBufferTimestampAdjustmentUs;

      // 帧释放操作应针对所有帧（包括将被跳过的帧）获取，因为释放控制从帧时间戳估计内容帧率，
      // 我们希望尽早了解此信息，尤其是在 seek 操作期间。
      @VideoFrameReleaseControl.FrameReleaseAction int frameReleaseAction;
      try {
        frameReleaseAction =
            videoFrameReleaseControl.getFrameReleaseAction(
                bufferPresentationTimeUs, // 调整后的缓冲区呈现时间
                positionUs, // 当前的播放位置
                elapsedRealtimeUs, // 实时时间
                inputStreamStartPositionUs, // 输入流的起始位置
                isLastFrame, // 是否为最后一帧
                frameReleaseInfo // 帧释放信息
            );
      } catch (ExoPlaybackException e) {
        throw new VideoSinkException(e, checkStateNotNull(inputFormat));
      }

      // 如果帧释放操作为忽略，则忽略该帧
      if (frameReleaseAction == VideoFrameReleaseControl.FRAME_RELEASE_IGNORE) {
        return false;
      }

      // 如果帧的呈现时间早于最后一次重置的位置，并且不是最后一帧，则跳过该帧
      if (bufferPresentationTimeUs < lastResetPositionUs && !isLastFrame) {
        videoFrameHandler.skip();
        return true;
      }

      // 渲染当前帧，为新输入帧腾出空间
      render(positionUs, elapsedRealtimeUs);

      // 如果输入流已完全解码，则等待其所有帧释放后再从下一个输入流中排队输入帧
      if (isInputStreamChangePending) {
        if (pendingInputStreamBufferPresentationTimeUs == C.TIME_UNSET
            || PlaybackVideoGraphWrapper.this.hasReleasedFrame(pendingInputStreamBufferPresentationTimeUs)) {
          maybeRegisterInputStream(); // 尝试注册输入流
          isInputStreamChangePending = false; // 标记输入流变化为已处理
          pendingInputStreamBufferPresentationTimeUs = C.TIME_UNSET; // 重置待处理的输入流缓冲区呈现时间
        } else {
          return false;
        }
      }

      // 如果视频帧处理器的待处理输入帧数已达到最大值，则返回 false
      if (checkStateNotNull(videoFrameProcessor).getPendingInputFrameCount()
          >= videoFrameProcessorMaxPendingFrameCount) {
        return false;
      }

      // 如果视频帧处理器无法注册输入帧，则返回 false
      if (!checkStateNotNull(videoFrameProcessor).registerInputFrame()) {
        return false;
      }

      // 可能设置流偏移量变化
      maybeSetStreamOffsetChange(bufferPresentationTimeUs);

      // 更新最近注册的帧的呈现时间
      lastBufferPresentationTimeUs = bufferPresentationTimeUs;

      // 如果是最后一帧，则更新最后一帧的呈现时间
      if (isLastFrame) {
        finalBufferPresentationTimeUs = bufferPresentationTimeUs;
      }

      // 使用帧的呈现时间作为渲染时间，以便 SurfaceTexture 附带此时间戳。
      // 设置基于实时时间的释放时间仅在渲染到 SurfaceView 时相关，但在此情况下我们渲染到 Surface。
      videoFrameHandler.render(/* renderTimestampNs= */ framePresentationTimeUs * 1000);

      return true;
    }

    @Override
    public boolean handleInputBitmap(Bitmap inputBitmap, TimestampIterator timestampIterator) {
      // 检查是否已初始化
      checkState(isInitialized());

      // 尝试注册待处理的输入流，如果失败则返回 false
      if (!maybeRegisterPendingInputStream()) {
        return false;
      }

      // 检查 videoFrameProcessor 不为空，并将输入位图和时间戳迭代器加入队列，如果失败则返回 false
      if (!checkStateNotNull(videoFrameProcessor)
          .queueInputBitmap(inputBitmap, timestampIterator)) {
        return false;
      }

      // 创建时间戳迭代器的副本，因为我们需要获取下一个时间戳，但不能改变迭代器的状态
      TimestampIterator copyTimestampIterator = timestampIterator.copyOf();
      long bufferPresentationTimeUs = copyTimestampIterator.next();
      // 时间戳迭代器生成帧时间
      long lastBufferPresentationTimeUs =
          copyTimestampIterator.getLastTimestampUs() - inputBufferTimestampAdjustmentUs;
      // 检查 lastBufferPresentationTimeUs 是否有效
      checkState(lastBufferPresentationTimeUs != C.TIME_UNSET);
      // 可能设置流偏移量变化
      maybeSetStreamOffsetChange(bufferPresentationTimeUs);
      // 更新最后缓冲区呈现时间
      this.lastBufferPresentationTimeUs = lastBufferPresentationTimeUs;
      // 更新最终缓冲区呈现时间
      finalBufferPresentationTimeUs = lastBufferPresentationTimeUs;
      return true;
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws VideoSinkException {
      try {
        PlaybackVideoGraphWrapper.this.render(positionUs, elapsedRealtimeUs);
      } catch (ExoPlaybackException e) {
        throw new VideoSinkException(
            e, inputFormat != null ? inputFormat : new Format.Builder().build());
      }
    }

    @Override
    public void join(boolean renderNextFrameImmediately) {
      defaultVideoSink.join(renderNextFrameImmediately);
    }

    @Override
    public void release() {
      PlaybackVideoGraphWrapper.this.release();
    }

    // Other methods

    private void maybeSetStreamOffsetChange(long bufferPresentationTimeUs) {
      if (pendingInputStreamOffsetChange) {
        PlaybackVideoGraphWrapper.this.onStreamOffsetChange(
            inputBufferTimestampAdjustmentUs,
            bufferPresentationTimeUs,
            /* streamOffsetUs= */ inputStreamOffsetUs);
        pendingInputStreamOffsetChange = false;
      }
    }

    /**
     * 尝试将任何待处理的输入流注册到视频图输入中，并返回 {@code true}，如果已注册待处理的流和/或没有待处理的输入流等待注册，
     * 因此可以安全地将图像或帧加入视频图输入队列。
     */
    private boolean maybeRegisterPendingInputStream() {
      // 如果没有待处理的输入流变更，直接返回 true
      if (!isInputStreamChangePending) {
        return true;
      }
      // 如果输入流已完全解码，等待其所有帧被释放后再将下一输入流的帧加入队列
      if (pendingInputStreamBufferPresentationTimeUs == C.TIME_UNSET
          || PlaybackVideoGraphWrapper.this.hasReleasedFrame(
          pendingInputStreamBufferPresentationTimeUs)) {
        // 尝试注册输入流
        maybeRegisterInputStream();
        // 重置待处理输入流变更标志
        isInputStreamChangePending = false;
        // 重置待处理输入流缓冲区呈现时间
        pendingInputStreamBufferPresentationTimeUs = C.TIME_UNSET;
        return true;
      }
      // 如果条件不满足，返回 false
      return false;
    }

    private void maybeRegisterInputStream() {
      if (inputFormat == null) {
        return;
      }

      ArrayList<Effect> effects = new ArrayList<>(videoEffects);
      Format inputFormat = checkNotNull(this.inputFormat);
      checkStateNotNull(videoFrameProcessor)
          .registerInputStream(
              inputType,
              effects,
              new FrameInfo.Builder(
                  getAdjustedInputColorInfo(inputFormat.colorInfo),
                  inputFormat.width,
                  inputFormat.height)
                  .setPixelWidthHeightRatio(inputFormat.pixelWidthHeightRatio)
                  .build());
      finalBufferPresentationTimeUs = C.TIME_UNSET;
    }

    // PlaybackVideoGraphWrapper.Listener implementation

    @Override
    public void onFirstFrameRendered(PlaybackVideoGraphWrapper playbackVideoGraphWrapper) {
      VideoSink.Listener currentListener = listener;
      listenerExecutor.execute(() -> currentListener.onFirstFrameRendered(/* videoSink= */ this));
    }

    @Override
    public void onFrameDropped(PlaybackVideoGraphWrapper playbackVideoGraphWrapper) {
      VideoSink.Listener currentListener = listener;
      listenerExecutor.execute(
          () -> currentListener.onFrameDropped(checkStateNotNull(/* reference= */ this)));
    }

    @Override
    public void onVideoSizeChanged(
        PlaybackVideoGraphWrapper playbackVideoGraphWrapper, VideoSize videoSize) {
      VideoSink.Listener currentListener = listener;
      listenerExecutor.execute(
          () -> currentListener.onVideoSizeChanged(/* videoSink= */ this, videoSize));
    }

    @Override
    public void onError(
        PlaybackVideoGraphWrapper playbackVideoGraphWrapper,
        VideoFrameProcessingException videoFrameProcessingException) {
      VideoSink.Listener currentListener = listener;
      listenerExecutor.execute(
          () ->
              currentListener.onError(
                  /* videoSink= */ this,
                  new VideoSinkException(
                      videoFrameProcessingException, checkStateNotNull(this.inputFormat))));
    }
  }

  private final class FrameRendererImpl implements VideoFrameRenderControl.FrameRenderer {

    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
      outputFormat =
          new Format.Builder()
              .setWidth(videoSize.width)
              .setHeight(videoSize.height)
              .setSampleMimeType(MimeTypes.VIDEO_RAW)
              .build();
      for (PlaybackVideoGraphWrapper.Listener listener : listeners) {
        listener.onVideoSizeChanged(PlaybackVideoGraphWrapper.this, videoSize);
      }
    }

    @Override
    public void renderFrame(
        long renderTimeNs,
        long bufferPresentationTimeUs,
        long streamOffsetUs,
        boolean isFirstFrame) {
      // 如果是第一帧且当前 Surface 和尺寸不为空，通知所有监听器第一帧已渲染
      if (isFirstFrame && currentSurfaceAndSize != null) {
        for (PlaybackVideoGraphWrapper.Listener listener : listeners) {
          listener.onFirstFrameRendered(PlaybackVideoGraphWrapper.this);
        }
      }
      // 如果设置了视频帧元数据监听器，通知即将渲染的视频帧元数据
      if (videoFrameMetadataListener != null) {
        // TODO b/292111083 - 输出格式在渲染第一帧后初始化，因为 onVideoSizeChanged 在渲染第一帧可用后才会被调用
        Format format = outputFormat == null ? new Format.Builder().build() : outputFormat;
        videoFrameMetadataListener.onVideoFrameAboutToBeRendered(
            /* presentationTimeUs= */ bufferPresentationTimeUs,
            clock.nanoTime(),
            format,
            /* mediaFormat= */ null);
      }
      // 确保 videoGraph 不为空，并渲染输出帧
      checkStateNotNull(videoGraph).renderOutputFrame(renderTimeNs);
    }

    @Override
    public void dropFrame() {
      for (PlaybackVideoGraphWrapper.Listener listener : listeners) {
        listener.onFrameDropped(PlaybackVideoGraphWrapper.this);
      }
      checkStateNotNull(videoGraph).renderOutputFrame(VideoFrameProcessor.DROP_OUTPUT_FRAME);
    }
  }

  /**
   * 延迟反射以加载 {@linkplain PreviewingVideoGraph.Factory PreviewingSingleInputVideoGraph} 实例。
   */
  private static final class ReflectivePreviewingSingleInputVideoGraphFactory
      implements PreviewingVideoGraph.Factory {

    private final VideoFrameProcessor.Factory videoFrameProcessorFactory;

    public ReflectivePreviewingSingleInputVideoGraphFactory(
        VideoFrameProcessor.Factory videoFrameProcessorFactory) {
      this.videoFrameProcessorFactory = videoFrameProcessorFactory;
    }

    @Override
    public PreviewingVideoGraph create(
        Context context,
        ColorInfo outputColorInfo,
        DebugViewProvider debugViewProvider,
        VideoGraph.Listener listener,
        Executor listenerExecutor,
        List<Effect> compositionEffects,
        long initialTimestampOffsetUs)
        throws VideoFrameProcessingException {
      try {
        // 通过反射加载 PreviewingSingleInputVideoGraph$Factory 类
        Class<?> previewingSingleInputVideoGraphFactoryClass =
            Class.forName("androidx.media3.effect.PreviewingSingleInputVideoGraph$Factory");
        // 获取 Factory 实例
        PreviewingVideoGraph.Factory factory =
            (PreviewingVideoGraph.Factory)
                previewingSingleInputVideoGraphFactoryClass
                    .getConstructor(VideoFrameProcessor.Factory.class)
                    .newInstance(videoFrameProcessorFactory);
        // 调用 Factory 的 create 方法创建 PreviewingVideoGraph 实例
        return factory.create(
            context,
            outputColorInfo,
            debugViewProvider,
            listener,
            listenerExecutor,
            compositionEffects,
            initialTimestampOffsetUs);
      } catch (Exception e) {
        // 如果发生异常，抛出 VideoFrameProcessingException
        throw VideoFrameProcessingException.from(e);
      }
    }
  }

  /**
   * 延迟反射以加载 {@linkplain VideoFrameProcessor.Factory DefaultVideoFrameProcessor.Factory} 实例。
   */
  private static final class ReflectiveDefaultVideoFrameProcessorFactory
      implements VideoFrameProcessor.Factory {

    // 使用 Suppliers.memoize 缓存工厂实例，确保只初始化一次
    private static final Supplier<VideoFrameProcessor.Factory>
        VIDEO_FRAME_PROCESSOR_FACTORY_SUPPLIER =
        Suppliers.memoize(
            () -> {
              try {
                // 通过反射加载 DefaultVideoFrameProcessor$Factory$Builder 类
                Class<?> defaultVideoFrameProcessorFactoryBuilderClass =
                    Class.forName(
                        "androidx.media3.effect.DefaultVideoFrameProcessor$Factory$Builder");
                Object builder =
                    defaultVideoFrameProcessorFactoryBuilderClass
                        .getConstructor()
                        .newInstance();
                return (VideoFrameProcessor.Factory)
                    checkNotNull(
                        defaultVideoFrameProcessorFactoryBuilderClass
                            .getMethod("build")
                            .invoke(builder));
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });

    @Override
    public VideoFrameProcessor create(
        Context context,
        DebugViewProvider debugViewProvider,
        ColorInfo outputColorInfo,
        boolean renderFramesAutomatically,
        Executor listenerExecutor,
        VideoFrameProcessor.Listener listener)
        throws VideoFrameProcessingException {
      return VIDEO_FRAME_PROCESSOR_FACTORY_SUPPLIER
          .get()
          .create(
              context,
              debugViewProvider,
              outputColorInfo,
              renderFramesAutomatically,
              listenerExecutor,
              listener);
    }
  }
}
