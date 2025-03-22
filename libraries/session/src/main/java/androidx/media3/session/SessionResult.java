package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkArgument;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.util.concurrent.ListenableFuture;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于 {@link ListenableFuture} 的结果，表示 {@link MediaSession} 和 {@link MediaController} 之间的异步调用结果。
 */
public final class SessionResult {

  /**
   * 结果代码。
   *
   * <ul>
   *   <li>错误代码：负整数
   *   <li>成功代码：0
   *   <li>信息代码：正整数
   * </ul>
   *
   * <ul>
   *   <li>{@code 0 < |code| < 100} ：保留给播放器特定的代码。
   *   <li>{@code 100 <= |code| < 500} ：会话/控制器特定的代码。
   *   <li>{@code 500 <= |code| < 1000} ：浏览器/媒体库会话特定的代码。
   *   <li>{@code 1000 <= |code|} ：保留给播放器自定义代码。
   * </ul>
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
      RESULT_SUCCESS,
      SessionError.INFO_CANCELLED,
      SessionError.ERROR_UNKNOWN,
      SessionError.ERROR_INVALID_STATE,
      SessionError.ERROR_BAD_VALUE,
      SessionError.ERROR_PERMISSION_DENIED,
      SessionError.ERROR_IO,
      SessionError.ERROR_SESSION_DISCONNECTED,
      SessionError.ERROR_NOT_SUPPORTED,
      SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED,
      SessionError.ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED,
      SessionError.ERROR_SESSION_CONCURRENT_STREAM_LIMIT,
      SessionError.ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED,
      SessionError.ERROR_SESSION_NOT_AVAILABLE_IN_REGION,
      SessionError.ERROR_SESSION_SKIP_LIMIT_REACHED,
      SessionError.ERROR_SESSION_SETUP_REQUIRED
  })
  public @interface Code {}

  /**
   * 结果代码，表示命令已成功完成。
   *
   * <p>互操作性：当与 {@code android.support.v4.media.session.MediaSessionCompat} 或 {@code
   * android.support.v4.media.session.MediaControllerCompat} 连接时，此代码也用于表示命令已成功发送，但结果未知。
   */
  public static final int RESULT_SUCCESS = 0;

  /** 结果代码，表示命令被跳过。 */
  public static final int RESULT_INFO_SKIPPED = SessionError.INFO_CANCELLED;

  /** 结果代码，表示命令以未知错误结束。 */
  public static final int RESULT_ERROR_UNKNOWN = SessionError.ERROR_UNKNOWN;

  /**
   * 结果代码，表示由于当前状态对命令无效，命令无法完成。
   */
  public static final int RESULT_ERROR_INVALID_STATE = SessionError.ERROR_INVALID_STATE;

  /** 结果代码，表示参数非法。 */
  public static final int RESULT_ERROR_BAD_VALUE = SessionError.ERROR_BAD_VALUE;

  /** 结果代码，表示命令不被允许。 */
  public static final int RESULT_ERROR_PERMISSION_DENIED = SessionError.ERROR_PERMISSION_DENIED;

  /** 结果代码，表示发生了文件或网络相关错误。 */
  public static final int RESULT_ERROR_IO = SessionError.ERROR_IO;

  /** 结果代码，表示命令不被支持。 */
  public static final int RESULT_ERROR_NOT_SUPPORTED = SessionError.ERROR_NOT_SUPPORTED;

  /** 结果代码，表示会话和控制器已断开连接。 */
  public static final int RESULT_ERROR_SESSION_DISCONNECTED =
      SessionError.ERROR_SESSION_DISCONNECTED;

  /** 结果代码，表示认证已过期。 */
  public static final int RESULT_ERROR_SESSION_AUTHENTICATION_EXPIRED =
      SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED;

  /** 结果代码，表示需要高级账户。 */
  public static final int RESULT_ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED =
      SessionError.ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED;

  /** 结果代码，表示检测到太多并发流。 */
  public static final int RESULT_ERROR_SESSION_CONCURRENT_STREAM_LIMIT =
      SessionError.ERROR_SESSION_CONCURRENT_STREAM_LIMIT;

  /** 结果代码，表示由于家长控制，内容被阻止。 */
  public static final int RESULT_ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED =
      SessionError.ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED;

  /** 结果代码，表示由于区域不可用，内容被阻止。 */
  public static final int RESULT_ERROR_SESSION_NOT_AVAILABLE_IN_REGION =
      SessionError.ERROR_SESSION_NOT_AVAILABLE_IN_REGION;

  /**
   * 结果代码，表示由于跳过限制已达到，应用程序无法再跳过。
   */
  public static final int RESULT_ERROR_SESSION_SKIP_LIMIT_REACHED =
      SessionError.ERROR_SESSION_SKIP_LIMIT_REACHED;

  /** 结果代码，表示会话需要用户手动干预。 */
  public static final int RESULT_ERROR_SESSION_SETUP_REQUIRED =
      SessionError.ERROR_SESSION_SETUP_REQUIRED;

  /** 此结果的结果代码。 */
  public final @Code int resultCode;

  /** 结果的额外数据包。 */
  public final Bundle extras;

  /**
   * 命令的完成时间。与 {@link SystemClock#elapsedRealtime()} 相同，表示命令完成的时间。
   */
  public final long completionTimeMs;

  /** 可选的会话错误。 */
  @UnstableApi @Nullable public final SessionError sessionError;

  /**
   * 使用结果代码创建实例。
   *
   * <p>注意：对于错误，请使用 {@link SessionResult#SessionResult(SessionError)} 以提供本地化的错误消息。
   *
   * @param resultCode 结果代码。
   */
  public SessionResult(@Code int resultCode) {
    this(resultCode, /* extras= */ Bundle.EMPTY);
  }

  /**
   * 使用结果代码和额外数据包创建实例。
   *
   * <p>注意：对于错误，请使用 {@link SessionResult#SessionResult(SessionError, Bundle)} 以提供本地化的错误消息。
   *
   * @param resultCode 结果代码。
   * @param extras 额外数据包。
   */
  public SessionResult(@Code int resultCode, Bundle extras) {
    this(
        resultCode,
        extras,
        /* completionTimeMs= */ SystemClock.elapsedRealtime(),
        /* sessionError= */ null);
  }

  /**
   * 从 {@link SessionError} 创建实例。{@link #resultCode} 取自 {@link SessionError#code}，会话结果的额外数据包为空。
   *
   * @param sessionError 会话错误。
   */
  @UnstableApi
  public SessionResult(SessionError sessionError) {
    this(
        sessionError.code,
        Bundle.EMPTY,
        /* completionTimeMs= */ SystemClock.elapsedRealtime(),
        sessionError);
  }

  /**
   * 从 {@link SessionError} 和额外数据包创建实例。{@link #resultCode} 取自 {@link SessionError}。
   *
   * @param sessionError 会话错误。
   * @param extras 额外数据包。
   */
  @UnstableApi
  public SessionResult(SessionError sessionError, Bundle extras) {
    this(
        sessionError.code,
        extras,
        /* completionTimeMs= */ SystemClock.elapsedRealtime(),
        sessionError);
  }

  private SessionResult(
      @Code int resultCode,
      Bundle extras,
      long completionTimeMs,
      @Nullable SessionError sessionError) {
    checkArgument(sessionError == null || resultCode < 0);
    this.resultCode = resultCode;
    this.extras = new Bundle(extras);
    this.completionTimeMs = completionTimeMs;
    this.sessionError =
        sessionError == null && resultCode < 0
            ? new SessionError(resultCode, SessionError.DEFAULT_ERROR_MESSAGE)
            : sessionError;
  }

  private static final String FIELD_RESULT_CODE = Util.intToStringMaxRadix(0);
  private static final String FIELD_EXTRAS = Util.intToStringMaxRadix(1);
  private static final String FIELD_COMPLETION_TIME_MS = Util.intToStringMaxRadix(2);
  private static final String FIELD_SESSION_ERROR = Util.intToStringMaxRadix(3);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_RESULT_CODE, resultCode);
    bundle.putBundle(FIELD_EXTRAS, extras);
    bundle.putLong(FIELD_COMPLETION_TIME_MS, completionTimeMs);
    if (sessionError != null) {
      bundle.putBundle(FIELD_SESSION_ERROR, sessionError.toBundle());
    }
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复 {@code SessionResult}。 */
  @UnstableApi
  public static SessionResult fromBundle(Bundle bundle) {
    int resultCode =
        bundle.getInt(FIELD_RESULT_CODE, /* defaultValue= */ SessionError.ERROR_UNKNOWN);
    @Nullable Bundle extras = bundle.getBundle(FIELD_EXTRAS);
    long completionTimeMs =
        bundle.getLong(FIELD_COMPLETION_TIME_MS, /* defaultValue= */ SystemClock.elapsedRealtime());
    @Nullable SessionError sessionError = null;
    @Nullable Bundle sessionErrorBundle = bundle.getBundle(FIELD_SESSION_ERROR);
    if (sessionErrorBundle != null) {
      sessionError = SessionError.fromBundle(sessionErrorBundle);
    } else if (resultCode != RESULT_SUCCESS) {
      // 如果会话是尚未包含 SessionError 的库版本，则填充会话错误。
      sessionError = new SessionError(resultCode, SessionError.DEFAULT_ERROR_MESSAGE);
    }
    return new SessionResult(
        resultCode, extras == null ? Bundle.EMPTY : extras, completionTimeMs, sessionError);
  }
}