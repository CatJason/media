package androidx.media3.common;

import android.content.Context;
import androidx.media3.common.util.UnstableApi;
import java.util.List;
import java.util.concurrent.Executor;

/** 用于预览的 {@link VideoGraph}。 */
@UnstableApi
public interface PreviewingVideoGraph extends VideoGraph {

  /** 用于创建 {@link PreviewingVideoGraph} 的工厂接口。 */
  interface Factory {
    /**
     * 创建一个新的 {@link PreviewingVideoGraph} 实例。
     *
     * @param context 一个 {@link Context}。
     * @param outputColorInfo 输出帧的 {@link ColorInfo}。
     * @param debugViewProvider 一个 {@link DebugViewProvider}。
     * @param listener 一个 {@link Listener}。
     * @param listenerExecutor 调用 {@code listener} 的 {@link Executor}。
     * @param compositionEffects 应用于合成的 {@linkplain Effect 效果} 列表。
     * @param initialTimestampOffsetUs 第一帧的时间戳偏移量，以微秒为单位。
     * @return 一个新的实例。
     * @throws VideoFrameProcessingException 如果创建 {@link VideoFrameProcessor} 时出现问题。
     */
    PreviewingVideoGraph create(
        Context context,
        ColorInfo outputColorInfo,
        DebugViewProvider debugViewProvider,
        Listener listener,
        Executor listenerExecutor,
        List<Effect> compositionEffects,
        long initialTimestampOffsetUs)
        throws VideoFrameProcessingException;
  }

  /**
   * 渲染最早未渲染的输出帧，该帧在给定的 {@code renderTimeNs} 时已 {@linkplain
   * Listener#onOutputFrameAvailableForRendering(long) 可用于渲染}。
   *
   * <p>这将根据 {@code renderTimeNs} 将输出帧渲染到 {@linkplain #setOutputSurfaceInfo 输出
   * 表面}，或丢弃该帧。
   *
   * <p>根据实现，{@code renderTimeNs} 可能会传递给 {@link
   * android.opengl.EGLExt#eglPresentationTimeANDROID}。
   *
   * @param renderTimeNs 用于帧的渲染时间，以纳秒为单位。渲染时间可以在当前系统时间之前或之后。使用 {@link
   *     VideoFrameProcessor#DROP_OUTPUT_FRAME} 丢弃帧，或使用 {@link
   *     VideoFrameProcessor#RENDER_OUTPUT_FRAME_IMMEDIATELY} 立即渲染帧。
   */
  void renderOutputFrame(long renderTimeNs);
}