/*
 * Copyright 2021 The Android Open Source Project
 *
 * 根据 Apache License, Version 2.0（“许可证”）授权；
 * 除非遵守许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则按“原样”分发软件，
 * 没有任何明示或暗示的担保或条件。
 * 有关特定语言的管理权限和限制，请参阅许可证。
 */
package androidx.media3.exoplayer;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** ExoPlayer 播放线程上操作超时的异常。 */
@UnstableApi
public final class ExoTimeoutException extends RuntimeException {

  /**
   * 导致超时错误的操作。取值为 {@link #TIMEOUT_OPERATION_RELEASE}、{@link #TIMEOUT_OPERATION_SET_FOREGROUND_MODE}、{@link #TIMEOUT_OPERATION_DETACH_SURFACE} 或
   * {@link #TIMEOUT_OPERATION_UNDEFINED} 之一。请注意，未来可能会添加新的操作，错误处理应能够处理未知的操作值。
   */
  // @Target 列表包括 'default' 目标和 TYPE_USE，以确保与添加 TYPE_USE 之前的 Kotlin 用法兼容。
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
      TIMEOUT_OPERATION_UNDEFINED,
      TIMEOUT_OPERATION_RELEASE,
      TIMEOUT_OPERATION_SET_FOREGROUND_MODE,
      TIMEOUT_OPERATION_DETACH_SURFACE
  })
  public @interface TimeoutOperation {}

  /** 发生此错误的操作未定义。 */
  public static final int TIMEOUT_OPERATION_UNDEFINED = 0;

  /** 错误发生在 {@link Player#release} 中。 */
  public static final int TIMEOUT_OPERATION_RELEASE = 1;

  /** 错误发生在 {@link ExoPlayer#setForegroundMode} 中。 */
  public static final int TIMEOUT_OPERATION_SET_FOREGROUND_MODE = 2;

  /** 错误发生在从播放器分离表面时。 */
  public static final int TIMEOUT_OPERATION_DETACH_SURFACE = 3;

  /** 在 ExoPlayer 播放线程上超时的操作。 */
  public final @TimeoutOperation int timeoutOperation;

  /**
   * 创建超时异常。
   *
   * @param timeoutOperation 导致超时的 {@link TimeoutOperation 操作}。
   */
  public ExoTimeoutException(@TimeoutOperation int timeoutOperation) {
    super(getErrorMessage(timeoutOperation));
    this.timeoutOperation = timeoutOperation;
  }

  private static String getErrorMessage(@TimeoutOperation int timeoutOperation) {
    switch (timeoutOperation) {
      case TIMEOUT_OPERATION_RELEASE:
        return "播放器释放超时。";
      case TIMEOUT_OPERATION_SET_FOREGROUND_MODE:
        return "设置前台模式超时。";
      case TIMEOUT_OPERATION_DETACH_SURFACE:
        return "分离表面超时。";
      case TIMEOUT_OPERATION_UNDEFINED:
      default:
        return "未定义的超时。";
    }
  }
}