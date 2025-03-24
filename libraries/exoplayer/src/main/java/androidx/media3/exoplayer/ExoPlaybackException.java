/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.exoplayer;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import androidx.annotation.CheckResult;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.C.FormatSupport;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 当发生无法本地恢复的播放失败时抛出。 */
public final class ExoPlaybackException extends PlaybackException {

  /**
   * 产生错误的源类型。取值为 {@link #TYPE_SOURCE}、{@link #TYPE_RENDERER}、{@link #TYPE_UNEXPECTED} 或 {@link #TYPE_REMOTE} 之一。请注意，未来可能会添加新的类型，错误处理应能够处理未知的类型值。
   */
  // @Target 列表包括 'default' 目标和 TYPE_USE，以确保与添加 TYPE_USE 之前的 Kotlin 用法兼容。
  @UnstableApi
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({TYPE_SOURCE, TYPE_RENDERER, TYPE_UNEXPECTED, TYPE_REMOTE})
  public @interface Type {}

  /**
   * 错误发生在从 {@link MediaSource} 加载数据时。
   *
   * <p>调用 {@link #getSourceException()} 以获取根本原因。
   */
  @UnstableApi public static final int TYPE_SOURCE = 0;

  /**
   * 错误发生在 {@link Renderer} 中。
   *
   * <p>调用 {@link #getRendererException()} 以获取根本原因。
   */
  @UnstableApi public static final int TYPE_RENDERER = 1;

  /**
   * 错误是一个意外的 {@link RuntimeException}。
   *
   * <p>调用 {@link #getUnexpectedException()} 以获取根本原因。
   */
  @UnstableApi public static final int TYPE_UNEXPECTED = 2;

  /**
   * 错误发生在远程组件中。
   *
   * <p>调用 {@link #getMessage()} 以获取与错误相关的消息。
   */
  @UnstableApi public static final int TYPE_REMOTE = 3;

  /** 播放失败的 {@link Type} 类型。 */
  @UnstableApi public final @Type int type;

  /** 如果 {@link #type} 是 {@link #TYPE_RENDERER}，这是渲染器的名称。 */
  @UnstableApi @Nullable public final String rendererName;

  /** 如果 {@link #type} 是 {@link #TYPE_RENDERER}，这是渲染器的索引。 */
  @UnstableApi public final int rendererIndex;

  /**
   * 如果 {@link #type} 是 {@link #TYPE_RENDERER}，这是异常发生时渲染器使用的 {@link Format}，如果渲染器未使用 {@link Format}，则为 null。
   */
  @UnstableApi @Nullable public final Format rendererFormat;

  /**
   * 如果 {@link #type} 是 {@link #TYPE_RENDERER}，这是渲染器对 {@link #rendererFormat} 的 {@link FormatSupport} 级别。如果 {@link #rendererFormat} 为 null，则此值为 {@link C#FORMAT_HANDLED}。
   */
  @UnstableApi public final @FormatSupport int rendererFormatSupport;

  /** 与此错误关联的 {@link MediaPeriodId}，如果未确定，则为 null。 */
  @UnstableApi @Nullable public final MediaPeriodId mediaPeriodId;

  /**
   * 如果 {@link #type} 是 {@link #TYPE_RENDERER}，此字段指示是否可以通过禁用并重新启用（但 <em>不</em> 重置）渲染器来恢复错误。对于其他 {@link Type} 类型，此字段始终为 {@code false}。
   */
  /* package */ final boolean isRecoverable;

  /**
   * 创建一个类型为 {@link #TYPE_SOURCE} 的实例。
   *
   * @param cause 失败的原因。
   * @param errorCode 参见 {@link #errorCode}。
   * @return 创建的实例。
   */
  @UnstableApi
  public static ExoPlaybackException createForSource(IOException cause, int errorCode) {
    return new ExoPlaybackException(TYPE_SOURCE, cause, errorCode);
  }

  /**
   * 创建一个类型为 {@link #TYPE_RENDERER} 的实例。
   *
   * @param cause 失败的原因。
   * @param rendererName 发生失败的渲染器的 {@linkplain Renderer#getName() 名称}。
   * @param rendererIndex 发生失败的渲染器的索引。
   * @param rendererFormat 异常发生时渲染器使用的 {@link Format}，如果渲染器未使用 {@link Format}，则为 null。
   * @param rendererFormatSupport 渲染器对 {@code rendererFormat} 的 {@link FormatSupport}。如果 {@code rendererFormat} 为 null，则忽略此参数。
   * @param isRecoverable 是否可以通过禁用并重新启用渲染器来恢复失败。
   * @param errorCode 参见 {@link #errorCode}。
   * @return 创建的实例。
   */
  @UnstableApi
  public static ExoPlaybackException createForRenderer(
      Throwable cause,
      String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport,
      boolean isRecoverable,
      @ErrorCode int errorCode) {

    return new ExoPlaybackException(
        TYPE_RENDERER,
        cause,
        /* customMessage= */ null,
        errorCode,
        rendererName,
        rendererIndex,
        rendererFormat,
        rendererFormat == null ? C.FORMAT_HANDLED : rendererFormatSupport,
        isRecoverable);
  }

  /**
   * @deprecated 请使用 {@link #createForUnexpected(RuntimeException, int)
   *     createForUnexpected(RuntimeException, ERROR_CODE_UNSPECIFIED)} 代替。
   */
  @UnstableApi
  @Deprecated
  public static ExoPlaybackException createForUnexpected(RuntimeException cause) {
    return createForUnexpected(cause, ERROR_CODE_UNSPECIFIED);
  }

  /**
   * 创建一个类型为 {@link #TYPE_UNEXPECTED} 的实例。
   *
   * @param cause 失败的原因。
   * @param errorCode 参见 {@link #errorCode}。
   * @return 创建的实例。
   */
  @UnstableApi
  public static ExoPlaybackException createForUnexpected(
      RuntimeException cause, @ErrorCode int errorCode) {
    return new ExoPlaybackException(TYPE_UNEXPECTED, cause, errorCode);
  }

  /**
   * 创建一个类型为 {@link #TYPE_REMOTE} 的实例。
   *
   * @param message 与错误相关的消息。
   * @return 创建的实例。
   */
  @UnstableApi
  public static ExoPlaybackException createForRemote(String message) {
    return new ExoPlaybackException(
        TYPE_REMOTE,
        /* cause= */ null,
        /* customMessage= */ message,
        ERROR_CODE_REMOTE_ERROR,
        /* rendererName= */ null,
        /* rendererIndex= */ C.INDEX_UNSET,
        /* rendererFormat= */ null,
        /* rendererFormatSupport= */ C.FORMAT_HANDLED,
        /* isRecoverable= */ false);
  }

  private ExoPlaybackException(@Type int type, Throwable cause, @ErrorCode int errorCode) {
    this(
        type,
        cause,
        /* customMessage= */ null,
        errorCode,
        /* rendererName= */ null,
        /* rendererIndex= */ C.INDEX_UNSET,
        /* rendererFormat= */ null,
        /* rendererFormatSupport= */ C.FORMAT_HANDLED,
        /* isRecoverable= */ false);
  }

  private ExoPlaybackException(
      @Type int type,
      @Nullable Throwable cause,
      @Nullable String customMessage,
      @ErrorCode int errorCode,
      @Nullable String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport,
      boolean isRecoverable) {
    this(
        deriveMessage(
            type,
            customMessage,
            rendererName,
            rendererIndex,
            rendererFormat,
            rendererFormatSupport),
        cause,
        errorCode,
        type,
        rendererName,
        rendererIndex,
        rendererFormat,
        rendererFormatSupport,
        /* mediaPeriodId= */ null,
        /* timestampMs= */ SystemClock.elapsedRealtime(),
        isRecoverable);
  }

  private ExoPlaybackException(Bundle bundle) {
    super(bundle);
    type = bundle.getInt(FIELD_TYPE, /* defaultValue= */ TYPE_UNEXPECTED);
    rendererName = bundle.getString(FIELD_RENDERER_NAME);
    rendererIndex = bundle.getInt(FIELD_RENDERER_INDEX, /* defaultValue= */ C.INDEX_UNSET);
    @Nullable Bundle rendererFormatBundle = bundle.getBundle(FIELD_RENDERER_FORMAT);
    rendererFormat = rendererFormatBundle == null ? null : Format.fromBundle(rendererFormatBundle);
    rendererFormatSupport =
        bundle.getInt(FIELD_RENDERER_FORMAT_SUPPORT, /* defaultValue= */ C.FORMAT_HANDLED);
    isRecoverable = bundle.getBoolean(FIELD_IS_RECOVERABLE, /* defaultValue= */ false);
    mediaPeriodId = null;
  }

  private ExoPlaybackException(
      String message,
      @Nullable Throwable cause,
      @ErrorCode int errorCode,
      @Type int type,
      @Nullable String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport,
      @Nullable MediaPeriodId mediaPeriodId,
      long timestampMs,
      boolean isRecoverable) {
    super(message, cause, errorCode, Bundle.EMPTY, timestampMs);
    Assertions.checkArgument(!isRecoverable || type == TYPE_RENDERER);
    Assertions.checkArgument(cause != null || type == TYPE_REMOTE);
    this.type = type;
    this.rendererName = rendererName;
    this.rendererIndex = rendererIndex;
    this.rendererFormat = rendererFormat;
    this.rendererFormatSupport = rendererFormatSupport;
    this.mediaPeriodId = mediaPeriodId;
    this.isRecoverable = isRecoverable;
  }
  /**
   * 当 {@link #type} 为 {@link #TYPE_SOURCE} 时，获取底层错误。
   *
   * @throws IllegalStateException 如果 {@link #type} 不是 {@link #TYPE_SOURCE}。
   */
  @UnstableApi
  public IOException getSourceException() {
    Assertions.checkState(type == TYPE_SOURCE);
    return (IOException) Assertions.checkNotNull(getCause());
  }

  /**
   * 当 {@link #type} 为 {@link #TYPE_RENDERER} 时，获取底层错误。
   *
   * @throws IllegalStateException 如果 {@link #type} 不是 {@link #TYPE_RENDERER}。
   */
  @UnstableApi
  public Exception getRendererException() {
    Assertions.checkState(type == TYPE_RENDERER);
    return (Exception) Assertions.checkNotNull(getCause());
  }

  /**
   * 当 {@link #type} 为 {@link #TYPE_UNEXPECTED} 时，获取底层错误。
   *
   * @throws IllegalStateException 如果 {@link #type} 不是 {@link #TYPE_UNEXPECTED}。
   */
  @UnstableApi
  public RuntimeException getUnexpectedException() {
    Assertions.checkState(type == TYPE_UNEXPECTED);
    return (RuntimeException) Assertions.checkNotNull(getCause());
  }

  @Override
  public boolean errorInfoEquals(@Nullable PlaybackException that) {
    if (!super.errorInfoEquals(that)) {
      return false;
    }
    // 我们知道 that 不为 null 且是 ExoPlaybackException，因为 super 调用返回了 true。
    ExoPlaybackException other = (ExoPlaybackException) Util.castNonNull(that);
    return type == other.type
        && Util.areEqual(rendererName, other.rendererName)
        && rendererIndex == other.rendererIndex
        && Util.areEqual(rendererFormat, other.rendererFormat)
        && rendererFormatSupport == other.rendererFormatSupport
        && Util.areEqual(mediaPeriodId, other.mediaPeriodId)
        && isRecoverable == other.isRecoverable;
  }

  /**
   * 返回带有指定 {@link MediaPeriodId} 的此异常的副本。
   *
   * @param mediaPeriodId {@link MediaPeriodId}。
   * @return 复制的异常。
   */
  @CheckResult
  /* package */ ExoPlaybackException copyWithMediaPeriodId(@Nullable MediaPeriodId mediaPeriodId) {
    return new ExoPlaybackException(
        Util.castNonNull(getMessage()),
        getCause(),
        errorCode,
        type,
        rendererName,
        rendererIndex,
        rendererFormat,
        rendererFormatSupport,
        mediaPeriodId,
        timestampMs,
        isRecoverable);
  }

  private static String deriveMessage(
      @Type int type,
      @Nullable String customMessage,
      @Nullable String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport) {
    String message;
    switch (type) {
      case TYPE_SOURCE:
        message = "Source error";
        break;
      case TYPE_RENDERER:
        message =
            rendererName
                + " error"
                + ", index="
                + rendererIndex
                + ", format="
                + rendererFormat
                + ", format_supported="
                + Util.getFormatSupportString(rendererFormatSupport);
        break;
      case TYPE_REMOTE:
        message = "Remote error";
        break;
      case TYPE_UNEXPECTED:
      default:
        message = "Unexpected runtime error";
        break;
    }
    if (!TextUtils.isEmpty(customMessage)) {
      message += ": " + customMessage;
    }
    return message;
  }

  /** Restores a {@code ExoPlaybackException} from a {@link Bundle}. */
  @UnstableApi
  public static ExoPlaybackException fromBundle(Bundle bundle) {
    return new ExoPlaybackException(bundle);
  }

  private static final String FIELD_TYPE = Util.intToStringMaxRadix(FIELD_CUSTOM_ID_BASE + 1);
  private static final String FIELD_RENDERER_NAME =
      Util.intToStringMaxRadix(FIELD_CUSTOM_ID_BASE + 2);
  private static final String FIELD_RENDERER_INDEX =
      Util.intToStringMaxRadix(FIELD_CUSTOM_ID_BASE + 3);
  private static final String FIELD_RENDERER_FORMAT =
      Util.intToStringMaxRadix(FIELD_CUSTOM_ID_BASE + 4);
  private static final String FIELD_RENDERER_FORMAT_SUPPORT =
      Util.intToStringMaxRadix(FIELD_CUSTOM_ID_BASE + 5);
  private static final String FIELD_IS_RECOVERABLE =
      Util.intToStringMaxRadix(FIELD_CUSTOM_ID_BASE + 6);

  /**
   * {@inheritDoc}
   *
   * <p>它省略了 {@link #mediaPeriodId} 字段。通过 {@link #fromBundle} 恢复的实例的 {@link #mediaPeriodId} 始终为 {@code null}。
   */
  @UnstableApi
  @Override
  public Bundle toBundle() {
    Bundle bundle = super.toBundle();
    bundle.putInt(FIELD_TYPE, type);
    bundle.putString(FIELD_RENDERER_NAME, rendererName);
    bundle.putInt(FIELD_RENDERER_INDEX, rendererIndex);
    if (rendererFormat != null) {
      bundle.putBundle(
          FIELD_RENDERER_FORMAT, rendererFormat.toBundle(/* excludeMetadata= */ false));
    }
    bundle.putInt(FIELD_RENDERER_FORMAT_SUPPORT, rendererFormatSupport);
    bundle.putBoolean(FIELD_IS_RECOVERABLE, isRecoverable);
    return bundle;
  }
}
