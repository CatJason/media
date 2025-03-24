package androidx.media3.common;

import androidx.media3.common.util.UnstableApi;

/**
 * 当准备 {@link Effect} 或对视频帧应用 {@link Effect} 时发生异常时抛出。
 */
@UnstableApi
public final class VideoFrameProcessingException extends Exception {

  /**
   * 如果给定的异常不是 {@code VideoFrameProcessingException}，则将其包装为 {@code VideoFrameProcessingException}，否则直接返回该异常。
   */
  public static VideoFrameProcessingException from(Exception exception) {
    return from(exception, /* presentationTimeUs= */ C.TIME_UNSET);
  }

  /**
   * 如果给定的异常不是 {@code VideoFrameProcessingException}，则将其包装为 {@code VideoFrameProcessingException} 并附带给定的时间戳，否则直接返回该异常。
   */
  public static VideoFrameProcessingException from(Exception exception, long presentationTimeUs) {
    if (exception instanceof VideoFrameProcessingException) {
      return (VideoFrameProcessingException) exception;
    } else {
      return new VideoFrameProcessingException(exception, presentationTimeUs);
    }
  }

  /**
   * 发生异常时正在处理的帧的微秒时间戳，如果未知则为 {@link C#TIME_UNSET}。
   */
  public final long presentationTimeUs;

  /**
   * 创建一个实例。
   *
   * @param message 此异常的详细信息。
   */
  public VideoFrameProcessingException(String message) {
    this(message, /* presentationTimeUs= */ C.TIME_UNSET);
  }

  /**
   * 创建一个实例。
   *
   * @param message 此异常的详细信息。
   * @param presentationTimeUs 发生异常的帧的时间戳。
   */
  public VideoFrameProcessingException(String message, long presentationTimeUs) {
    super(message);
    this.presentationTimeUs = presentationTimeUs;
  }

  /**
   * 创建一个实例。
   *
   * @param message 此异常的详细信息。
   * @param cause 此异常的原因。
   */
  public VideoFrameProcessingException(String message, Throwable cause) {
    this(message, cause, /* presentationTimeUs= */ C.TIME_UNSET);
  }

  /**
   * 创建一个实例。
   *
   * @param message 此异常的详细信息。
   * @param cause 此异常的原因。
   * @param presentationTimeUs 发生异常的帧的时间戳。
   */
  public VideoFrameProcessingException(String message, Throwable cause, long presentationTimeUs) {
    super(message, cause);
    this.presentationTimeUs = presentationTimeUs;
  }

  /**
   * 创建一个实例。
   *
   * @param cause 此异常的原因。
   */
  public VideoFrameProcessingException(Throwable cause) {
    this(cause, /* presentationTimeUs= */ C.TIME_UNSET);
  }

  /**
   * 创建一个实例。
   *
   * @param cause 此异常的原因。
   * @param presentationTimeUs 发生异常的帧的时间戳。
   */
  public VideoFrameProcessingException(Throwable cause, long presentationTimeUs) {
    super(cause);
    this.presentationTimeUs = presentationTimeUs;
  }
}