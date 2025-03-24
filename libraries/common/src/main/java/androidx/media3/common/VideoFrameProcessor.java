package androidx.media3.common;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGLExt;
import android.view.Surface;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 用于对单个视频帧进行处理的接口。
 *
 * <p>通过 {@link Effect} 实例传递给 {@link #registerInputStream} 来指定对帧的修改。
 *
 * <p>管理其输入 {@link Surface}，可以通过 {@link #getInputSurface()} 访问。输出 {@link Surface} 必须由调用者使用 {@link
 * #setOutputSurfaceInfo(SurfaceInfo)} 设置。
 *
 * <p>{@code VideoFrameProcessor} 实例可以从任何线程创建，但每个 {@linkplain #registerInputStream 流} 的实例方法必须从同一线程调用。
 */
@UnstableApi
public interface VideoFrameProcessor {
  /**
   * 指定输入帧如何提供给 {@link VideoFrameProcessor}。可以是 {@link #INPUT_TYPE_SURFACE}、{@link #INPUT_TYPE_BITMAP}、{@link #INPUT_TYPE_TEXTURE_ID} 或 {@link #INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION} 之一。
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
      INPUT_TYPE_SURFACE,
      INPUT_TYPE_BITMAP,
      INPUT_TYPE_TEXTURE_ID,
      INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION,
  })
  @interface InputType {}

  /**
   * 输入帧来自 {@link #getInputSurface Surface}。
   *
   * <p>当从 Surface 接收输入时，调用者必须在将帧渲染到输入 {@link Surface} 之前 {@linkplain #registerInputFrame() 注册} 输入帧。
   */
  int INPUT_TYPE_SURFACE = 1;

  /** 输入帧来自 {@link Bitmap}。 */
  int INPUT_TYPE_BITMAP = 2;

  /**
   * 输入帧来自 {@linkplain android.opengl.GLES10#GL_TEXTURE_2D 传统的 GLES 纹理}。
   */
  int INPUT_TYPE_TEXTURE_ID = 3;

  /**
   * 输入帧来自 {@linkplain #getInputSurface 输入 Surface}，并且不需要 {@linkplain #registerInputFrame 注册}（与 {@link #INPUT_TYPE_SURFACE} 不同）。
   *
   * <p>每个帧必须使用 {@linkplain #registerInputStream(int, List, FrameInfo) 输入流的注册} 帧信息。同时设置 Surface 的 {@linkplain
   * android.graphics.SurfaceTexture#setDefaultBufferSize(int, int) 默认缓冲区大小}。
   */
  int INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION = 4;

  /** {@link VideoFrameProcessor} 实例的工厂接口。 */
  interface Factory {

    // TODO(b/271433904): 将带有默认值的参数转换为 setter 方法。
    /**
     * 创建一个新的 {@link VideoFrameProcessor} 实例。
     *
     * @param context 一个 {@link Context}。
     * @param debugViewProvider 一个 {@link DebugViewProvider}。在生产环境中，传递 {@link
     *     DebugViewProvider#NONE}。
     * @param outputColorInfo 输出帧的 {@link ColorInfo}。
     * @param renderFramesAutomatically 如果为 {@code true}，实例将在 {@link VideoFrameProcessor} 处理完帧后自动将输出帧渲染到 {@linkplain #setOutputSurfaceInfo(SurfaceInfo) 输出 Surface}。如果为 {@code false}，{@link
     *     VideoFrameProcessor} 将阻塞，直到调用 {@link #renderOutputFrame(long)} 来渲染或丢弃帧。
     * @param listenerExecutor 调用 {@code listener} 的 {@link Executor}。
     * @param listener 一个 {@link Listener}。
     * @return 一个新的实例。
     * @throws VideoFrameProcessingException 如果在创建 {@link VideoFrameProcessor} 时发生问题。
     */
    VideoFrameProcessor create(
        Context context,
        DebugViewProvider debugViewProvider,
        ColorInfo outputColorInfo,
        boolean renderFramesAutomatically,
        Executor listenerExecutor,
        Listener listener)
        throws VideoFrameProcessingException;
  }

  /**
   * 异步帧处理事件的监听器。
   *
   * <p>所有监听器方法必须在 {@linkplain Factory#create 创建} 时传递的 {@link Executor} 上调用。
   */
  interface Listener {

    /**
     * 当 {@link VideoFrameProcessor} 完成 {@linkplain #registerInputStream(int,
     * List, FrameInfo) 注册输入流} 时调用。
     *
     * <p>{@link VideoFrameProcessor} 现在可以接受新的输入 {@linkplain
     * VideoFrameProcessor#registerInputFrame 帧}、{@linkplain
     * VideoFrameProcessor#queueInputBitmap(Bitmap, TimestampIterator) 位图} 或 {@linkplain
     * VideoFrameProcessor#queueInputTexture(int, long) 纹理}。
     *
     * @param inputType 新输入流的 {@link InputType}。
     * @param effects 应用于新输入流的 {@link Effect 效果} 列表。
     * @param frameInfo 新输入流的 {@link FrameInfo}。
     */
    default void onInputStreamRegistered(
        @InputType int inputType, List<Effect> effects, FrameInfo frameInfo) {}

    /**
     * 当输出尺寸发生变化时调用。
     *
     * <p>输出尺寸是应用所有 {@linkplain Effect 效果} 后的帧尺寸（以像素为单位）。
     *
     * <p>输出尺寸可能与使用 {@link #setOutputSurfaceInfo(SurfaceInfo)} 指定的尺寸不同。
     */
    default void onOutputSizeChanged(int width, int height) {}

    /**
     * 当具有给定 {@code presentationTimeUs} 的输出帧可用于渲染时调用。
     *
     * @param presentationTimeUs 帧的呈现时间，单位为微秒。
     */
    default void onOutputFrameAvailableForRendering(long presentationTimeUs) {}

    /**
     * 当异步视频帧处理过程中发生异常时调用。
     *
     * <p>如果调用此方法，调用者必须立即 {@linkplain VideoFrameProcessor#release() 释放} 相关的 {@link VideoFrameProcessor}。
     */
    default void onError(VideoFrameProcessingException exception) {}

    /** 当 {@link VideoFrameProcessor} 渲染完其最后一个输出帧后调用。 */
    default void onEnded() {}
  }

  /**
   * 指示帧应在调用 {@link #renderOutputFrame(long)} 后立即渲染。
   */
  long RENDER_OUTPUT_FRAME_IMMEDIATELY = -1;

  /** 指示帧应在调用 {@link #renderOutputFrame(long)} 后丢弃。 */
  long DROP_OUTPUT_FRAME = -2;

  /**
   * 指示帧在调用 {@link #renderOutputFrame(long)} 时应保留输入呈现时间。
   */
  @SuppressWarnings("GoodTime-ApiWithNumericTimeUnit") // 这是一个命名常量，不是时间单位。
      long RENDER_OUTPUT_FRAME_WITH_PRESENTATION_TIME = -3;

  /**
   * 向 {@link VideoFrameProcessor} 提供输入 {@link Bitmap}。
   *
   * <p>可以在 {@link #registerInputStream(int, List, FrameInfo) 注册输入流} 后多次调用，以将多个帧放入同一输入流中。
   *
   * @param inputBitmap 排队到 {@code VideoFrameProcessor} 的 {@link Bitmap}。
   * @param timestampIterator 生成位图应显示的确切时间戳的 {@link TimestampIterator}。
   * @return {@link Bitmap} 是否成功排队。返回 {@code false} 表示 {@code VideoFrameProcessor} 尚未准备好接受输入。
   * @throws UnsupportedOperationException 如果 {@code VideoFrameProcessor} 不接受 {@linkplain #INPUT_TYPE_BITMAP 位图输入}。
   */
  boolean queueInputBitmap(Bitmap inputBitmap, TimestampIterator timestampIterator);

  /**
   * 向 {@code VideoFrameProcessor} 提供输入纹理 ID。
   *
   * <p>必须在调用 {@link #setOnInputFrameProcessedListener} 和 {@link #registerInputStream} 后调用。
   *
   * @param textureId 排队到 {@code VideoFrameProcessor} 的纹理 ID。
   * @param presentationTimeUs 排队纹理的呈现时间，单位为微秒。
   * @return 纹理是否成功排队。返回 {@code false} 表示 {@code VideoFrameProcessor} 尚未准备好接受输入。
   */
  // TODO - b/294369303: 移除轮询 API。
  boolean queueInputTexture(int textureId, long presentationTimeUs);

  /**
   * 设置 {@link OnInputFrameProcessedListener}。
   *
   * @param listener {@link OnInputFrameProcessedListener}。
   */
  void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener);

  /**
   * 设置一个监听器，当 {@linkplain #getInputSurface() 输入 Surface} 准备就绪时调用。
   */
  void setOnInputSurfaceReadyListener(Runnable listener);

  // TODO: b/351776002 - 在 INPUT_TYPE_SURFACE 路径上也调用 setDefaultBufferSize，并移除
  //  文件中对该方法的提及（该方法泄漏了实现细节）。
  /**
   * 返回输入 {@link Surface}，{@link VideoFrameProcessor} 从中消费输入帧。
   *
   * <p>在调用 {@link #registerInputStream} 并指定 {@link #INPUT_TYPE_SURFACE} 之前，到达 {@link Surface} 的帧不会被 {@code VideoFrameProcessor} 消费。
   *
   * <p>对于 {@link #INPUT_TYPE_SURFACE} 流，返回的 Surface 立即准备就绪，并且不会设置 {@linkplain SurfaceTexture#setDefaultBufferSize(int, int) 默认缓冲区大小}。这适用于配置 {@link android.media.MediaCodec} 解码器。
   *
   * <p>对于 {@link #INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION} 流，通过 {@link #setOnInputSurfaceReadyListener(Runnable)} 设置监听器以等待 Surface 准备就绪的事件。这适用于与非解码器生产者（如媒体投影）一起使用。
   *
   * @throws UnsupportedOperationException 如果 {@code VideoFrameProcessor} 不接受 {@linkplain #INPUT_TYPE_SURFACE Surface 输入}。
   */
  Surface getInputSurface();

  /**
   * 通知 {@code VideoFrameProcessor} 将使用指定的 {@link Effect 效果} 列表排队新的输入流。
   *
   * <p>在注册第一个输入流后，此方法必须仅在已注册输入流的最后一帧 {@linkplain #registerInputFrame 注册}、最后一个位图 {@link #queueInputBitmap 排队} 或最后一个纹理 ID {@linkplain #queueInputTexture 排队} 后调用。
   *
   * <p>此方法会阻塞调用线程，直到之前的调用完成，即当 {@link Listener#onInputStreamRegistered(int, List, FrameInfo)} 在底层处理管道适应注册的输入流后调用时。
   *
   * @param inputType 新输入流的 {@link InputType}。
   * @param effects 应用于新输入流的 {@link Effect 效果} 列表。
   * @param frameInfo 新输入流的 {@link FrameInfo}。
   */
  void registerInputStream(@InputType int inputType, List<Effect> effects, FrameInfo frameInfo);

  /**
   * 通知 {@code VideoFrameProcessor} 将向其 {@linkplain #getInputSurface() 输入 Surface} 排队一帧。
   *
   * <p>必须在将帧渲染到输入 Surface 之前调用。当返回 {@code false} 时，调用者不得将帧渲染到 {@linkplain #getInputSurface 输入 Surface}。
   *
   * @return 输入帧是否成功注册。如果调用 {@link #registerInputStream(int, List, FrameInfo)}，此方法在 {@link Listener#onInputStreamRegistered(int, List, FrameInfo)} 调用之前返回 {@code false}。否则，返回 {@code false} 表示 {@code VideoFrameProcessor} 尚未准备好接受输入。
   * @throws UnsupportedOperationException 如果 {@code VideoFrameProcessor} 不接受 {@linkplain #INPUT_TYPE_SURFACE Surface 输入}。
   * @throws IllegalStateException 如果在 {@link #signalEndOfInput()} 之后或 {@link #registerInputStream} 之前调用。
   */
  boolean registerInputFrame();

  /**
   * 返回已提供给 {@code VideoFrameProcessor} 但尚未处理的输入帧的数量。
   */
  int getPendingInputFrameCount();

  /**
   * 设置输出 Surface 及其支持信息。当输出帧被渲染且未被丢弃时，它们将被渲染到此输出 {@link SurfaceInfo}。
   *
   * <p>新的输出 {@link SurfaceInfo} 将从下一个渲染的输出帧开始应用。如果输出 {@link SurfaceInfo} 为 {@code null}，{@code VideoFrameProcessor} 将停止渲染挂起的帧，并在设置非空 {@link SurfaceInfo} 后恢复渲染。
   *
   * <p>如果 {@link SurfaceInfo} 中给定的尺寸与 {@linkplain Listener#onOutputSizeChanged(int,int) 应用最终效果后的输出尺寸} 不匹配，则在渲染到 Surface 之前会对帧进行缩放，并应用黑边/白边。
   *
   * <p>调用者负责跟踪 {@link SurfaceInfo#surface} 的生命周期，包括在其销毁时调用此方法设置新的 Surface。当此方法返回时，之前的输出 Surface 不再被使用，调用者可以安全地释放它。
   */
  void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo);

  /**
   * 渲染最旧的未渲染输出帧，该帧已 {@linkplain Listener#onOutputFrameAvailableForRendering(long) 可用于渲染}，并在给定的 {@code renderTimeNs} 时间渲染。
   *
   * <p>这将根据 {@code renderTimeNs} 将输出帧渲染到 {@linkplain #setOutputSurfaceInfo 输出 Surface} 或丢弃帧。
   *
   * <p>仅当使用 {@link Factory} 时将 {@code renderFramesAutomatically} 设置为 {@code false} 时才应调用此方法，并且应为每个 {@linkplain Listener#onOutputFrameAvailableForRendering(long) 可用于渲染} 的帧调用一次。
   *
   * <p>根据实现，{@code renderTimeNs} 可能会传递给 {@link EGLExt#eglPresentationTimeANDROID}。
   *
   * @param renderTimeNs 帧的渲染时间，单位为纳秒。渲染时间可以在当前系统时间之前或之后。使用 {@link #DROP_OUTPUT_FRAME} 丢弃帧，或使用 {@link #RENDER_OUTPUT_FRAME_IMMEDIATELY} 立即渲染帧，或使用 {@link #RENDER_OUTPUT_FRAME_WITH_PRESENTATION_TIME} 将帧渲染到 {@linkplain #setOutputSurfaceInfo 输出 Surface} 并使用 {@link Listener#onOutputFrameAvailableForRendering(long)} 中看到的呈现时间戳。
   */
  void renderOutputFrame(long renderTimeNs);

  /**
   * 通知 {@code VideoFrameProcessor} 不再接受进一步的输入帧。
   *
   * @throws IllegalStateException 如果多次调用。
   */
  void signalEndOfInput();

  /**
   * 刷新 {@code VideoFrameProcessor}。
   *
   * <p>在此方法返回时，所有在此方法调用之前 {@linkplain #registerInputFrame() 注册} 的帧不再被视为已注册。
   *
   * <p>在此方法调用之前调用的 {@link Listener} 方法应被忽略。
   *
   * @throws UnsupportedOperationException 如果 {@code VideoFrameProcessor} 不接受 {@linkplain #INPUT_TYPE_SURFACE Surface 输入}。
   */
  void flush();

  /**
   * 释放所有资源。
   *
   * <p>如果 {@code VideoFrameProcessor} 在 {@linkplain Listener#onEnded() 结束} 之前被释放，它将尝试取消处理已变为可用的所有输入帧。在释放后变为可用的输入帧将被忽略。
   *
   * <p>此方法会阻塞，直到所有资源被释放或释放超时。
   *
   * <p>此 {@link VideoFrameProcessor} 实例在调用此方法后不得再使用。
   */
  void release();
}