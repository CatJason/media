package androidx.media3.common;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

/** 表示用于处理原始视频帧的图。 */
@UnstableApi
public interface VideoGraph {

  /** 视频帧处理事件的监听器。 */
  @UnstableApi
  interface Listener {
    /**
     * 当输出尺寸发生变化时调用。
     *
     * @param width 新的输出宽度，单位为像素。
     * @param height 新的输出高度，单位为像素。
     */
    default void onOutputSizeChanged(int width, int height) {}

    /**
     * 当具有给定 {@code framePresentationTimeUs} 的输出帧可用于渲染时调用。
     *
     * @param framePresentationTimeUs 帧的呈现时间，单位为微秒。
     */
    default void onOutputFrameAvailableForRendering(long framePresentationTimeUs) {}

    /**
     * 当 {@link VideoGraph} 渲染完其最后一个输出帧后调用。
     *
     * @param finalFramePresentationTimeUs 最后一个输出帧的时间戳，单位为微秒。
     */
    default void onEnded(long finalFramePresentationTimeUs) {}

    /**
     * 当视频帧处理过程中发生异常时调用。
     *
     * <p>如果调用此方法，调用者必须立即 {@linkplain #release() 释放} 相关的 {@link VideoGraph}。
     */
    default void onError(VideoFrameProcessingException exception) {}
  }

  /**
   * 初始化 {@code VideoGraph}。
   *
   * <p>在调用其他方法之前必须调用此方法。
   *
   * <p>如果此方法抛出异常，调用者必须调用 {@link #release}。
   */
  void initialize() throws VideoFrameProcessingException;

  /**
   * 向 {@code VideoGraph} 注册一个新的输入。
   *
   * <p>每次调用此方法时，都会创建一个底层的处理 {@link VideoFrameProcessor}。
   *
   * <p>所有输入必须在向底层 {@link #getProcessor(int) VideoFrameProcessor} 渲染帧之前注册。
   *
   * <p>如果此方法抛出异常，调用者必须调用 {@link #release}。
   *
   * @param inputIndex 输入的索引，用于对输入进行排序。索引必须从 0 开始。
   */
  void registerInput(@IntRange(from = 0) int inputIndex) throws VideoFrameProcessingException;

  /**
   * 返回处理通过 {@link #registerInput(int)} 注册的输入的 {@link VideoFrameProcessor}。如果 {@code inputIndex} 未 {@linkplain #registerInput(int) 注册}，此方法将抛出 {@link IllegalStateException}。
   */
  VideoFrameProcessor getProcessor(int inputIndex);

  /**
   * 设置输出 Surface 及其支持信息。
   *
   * <p>新的输出 {@link SurfaceInfo} 将从下一个渲染的输出帧开始应用。如果输出 {@link SurfaceInfo} 为 {@code null}，{@code VideoGraph} 将停止渲染挂起的帧，并在设置非空 {@link SurfaceInfo} 后恢复渲染。
   *
   * <p>如果 {@link SurfaceInfo} 中给定的尺寸与 {@linkplain Listener#onOutputSizeChanged(int,int) 应用最终效果后的输出尺寸} 不匹配，则在渲染到 Surface 之前会对帧进行缩放，并应用黑边/白边。
   *
   * <p>调用者负责跟踪 {@link SurfaceInfo#surface} 的生命周期，包括在其销毁时调用此方法设置新的 Surface。当此方法返回时，之前的输出 Surface 不再被使用，调用者可以安全地释放它。
   */
  void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo);

  /**
   * 返回 {@code VideoGraph} 是否生成了具有零时间戳的帧。
   */
  boolean hasProducedFrameWithTimestampZero();

  /**
   * 释放相关资源。
   *
   * <p>调用此方法后，不得再使用此 {@code VideoGraph} 实例。
   */
  void release();
}