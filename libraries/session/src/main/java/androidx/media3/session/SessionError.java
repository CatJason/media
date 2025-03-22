package androidx.media3.session;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

/** 提供有关会话错误的信息。 */
@UnstableApi
public final class SessionError {

  /**
   * 信息和错误结果代码。
   *
   * <ul>
   *   <li>信息代码：正整数
   *   <li>错误代码：负整数
   * </ul>
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
      ERROR_UNKNOWN,
      ERROR_INVALID_STATE,
      ERROR_BAD_VALUE,
      ERROR_PERMISSION_DENIED,
      ERROR_IO,
      ERROR_NOT_SUPPORTED,
      ERROR_SESSION_DISCONNECTED,
      ERROR_SESSION_AUTHENTICATION_EXPIRED,
      ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED,
      ERROR_SESSION_CONCURRENT_STREAM_LIMIT,
      ERROR_SESSION_CONTENT_ALREADY_PLAYING,
      ERROR_SESSION_END_OF_PLAYLIST,
      ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED,
      ERROR_SESSION_NOT_AVAILABLE_IN_REGION,
      ERROR_SESSION_SKIP_LIMIT_REACHED,
      ERROR_SESSION_SETUP_REQUIRED,
      INFO_CANCELLED
  })
  public @interface Code {}

  // 信息代码 (> 0).

  /** 信息代码，表示命令被取消。 */
  public static final int INFO_CANCELLED = 1;

  // 错误代码 (< 0).

  /** 错误代码，表示命令以未知错误结束。 */
  public static final int ERROR_UNKNOWN = -1;

  /**
   * 错误代码，表示由于当前状态对命令无效，命令无法完成。
   */
  public static final int ERROR_INVALID_STATE = PlaybackException.ERROR_CODE_INVALID_STATE;

  /** 错误代码，表示参数非法。 */
  public static final int ERROR_BAD_VALUE = PlaybackException.ERROR_CODE_BAD_VALUE;

  /** 错误代码，表示命令不被允许。 */
  public static final int ERROR_PERMISSION_DENIED = PlaybackException.ERROR_CODE_PERMISSION_DENIED;

  /** 错误代码，表示发生了文件或网络相关错误。 */
  public static final int ERROR_IO = -5;

  /** 错误代码，表示命令不被支持。 */
  public static final int ERROR_NOT_SUPPORTED = PlaybackException.ERROR_CODE_NOT_SUPPORTED;

  /** 错误代码，表示会话和控制器已断开连接。 */
  public static final int ERROR_SESSION_DISCONNECTED = PlaybackException.ERROR_CODE_DISCONNECTED;

  /** 错误代码，表示认证已过期。 */
  public static final int ERROR_SESSION_AUTHENTICATION_EXPIRED =
      PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED;

  /** 错误代码，表示需要高级账户。 */
  public static final int ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED =
      PlaybackException.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED;

  /** 错误代码，表示检测到太多并发流。 */
  public static final int ERROR_SESSION_CONCURRENT_STREAM_LIMIT =
      PlaybackException.ERROR_CODE_CONCURRENT_STREAM_LIMIT;

  /** 错误代码，表示由于家长控制，内容被阻止。 */
  public static final int ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED =
      PlaybackException.ERROR_CODE_PARENTAL_CONTROL_RESTRICTED;

  /** 错误代码，表示由于区域不可用，内容被阻止。 */
  public static final int ERROR_SESSION_NOT_AVAILABLE_IN_REGION =
      PlaybackException.ERROR_CODE_NOT_AVAILABLE_IN_REGION;

  /**
   * 错误代码，表示由于跳过限制已达到，应用程序无法再跳过。
   */
  public static final int ERROR_SESSION_SKIP_LIMIT_REACHED =
      PlaybackException.ERROR_CODE_SKIP_LIMIT_REACHED;

  /** 错误代码，表示会话需要用户手动干预。 */
  public static final int ERROR_SESSION_SETUP_REQUIRED =
      PlaybackException.ERROR_CODE_SETUP_REQUIRED;

  /** 错误代码，表示由于播放列表已耗尽，导航失败。 */
  public static final int ERROR_SESSION_END_OF_PLAYLIST =
      PlaybackException.ERROR_CODE_END_OF_PLAYLIST;

  /** 错误代码，表示请求的内容已经在播放。 */
  public static final int ERROR_SESSION_CONTENT_ALREADY_PLAYING =
      PlaybackException.ERROR_CODE_CONTENT_ALREADY_PLAYING;

  /** 默认错误消息。仅用于已弃用的方法和向后兼容性。 */
  /* package */ static final String DEFAULT_ERROR_MESSAGE = "未提供错误消息";

  /** 返回给定错误代码的名称。 */
  public static String getErrorCodeName(@Code int errorCode) {
    switch (errorCode) {
      case ERROR_UNKNOWN:
        return "ERROR_UNKNOWN";
      case ERROR_INVALID_STATE:
        return "ERROR_INVALID_STATE";
      case ERROR_BAD_VALUE:
        return "ERROR_BAD_VALUE";
      case ERROR_PERMISSION_DENIED:
        return "ERROR_PERMISSION_DENIED";
      case ERROR_IO:
        return "ERROR_IO";
      case ERROR_NOT_SUPPORTED:
        return "ERROR_NOT_SUPPORTED";
      case ERROR_SESSION_DISCONNECTED:
        return "ERROR_SESSION_DISCONNECTED";
      case ERROR_SESSION_AUTHENTICATION_EXPIRED:
        return "ERROR_SESSION_AUTHENTICATION_EXPIRED";
      case ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED:
        return "ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED";
      case ERROR_SESSION_CONCURRENT_STREAM_LIMIT:
        return "ERROR_SESSION_CONCURRENT_STREAM_LIMIT";
      case ERROR_SESSION_CONTENT_ALREADY_PLAYING:
        return "ERROR_SESSION_CONTENT_ALREADY_PLAYING";
      case ERROR_SESSION_END_OF_PLAYLIST:
        return "ERROR_SESSION_END_OF_PLAYLIST";
      case ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED:
        return "ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED";
      case ERROR_SESSION_NOT_AVAILABLE_IN_REGION:
        return "ERROR_SESSION_NOT_AVAILABLE_IN_REGION";
      case ERROR_SESSION_SKIP_LIMIT_REACHED:
        return "ERROR_SESSION_SKIP_LIMIT_REACHED";
      case ERROR_SESSION_SETUP_REQUIRED:
        return "ERROR_SESSION_SETUP_REQUIRED";
      case INFO_CANCELLED:
        return "INFO_CANCELLED";
      default:
        return "无效的错误代码";
    }
  }

  public @SessionError.Code int code;
  public String message;
  public Bundle extras;

  /**
   * 创建一个实例，并使用 {@linkplain Bundle#EMPTY 空的额外数据包}。
   *
   * @param code 错误结果代码。
   * @param message 错误消息。
   * @throws IllegalArgumentException 如果结果代码不是错误结果代码。
   */
  public SessionError(@SessionError.Code int code, String message) {
    this(code, message, Bundle.EMPTY);
  }

  /**
   * 创建一个实例。
   *
   * @param code 错误结果代码。
   * @param message 错误消息。
   * @param extras 错误的额外数据。
   * @throws IllegalArgumentException 如果结果代码不是错误结果代码。
   */
  public SessionError(@SessionError.Code int code, String message, Bundle extras) {
    Assertions.checkArgument(code < 0 || code == INFO_CANCELLED);
    this.code = code;
    this.message = message;
    this.extras = extras;
  }

  /** 检查给定的错误是否相等，忽略 {@link #extras}。 */
  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SessionError)) {
      return false;
    }
    SessionError that = (SessionError) o;
    return code == that.code && Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message);
  }

  // Bundleable 实现。

  private static final String FIELD_CODE = Util.intToStringMaxRadix(0);
  private static final String FIELD_MESSAGE = Util.intToStringMaxRadix(1);
  private static final String FIELD_EXTRAS = Util.intToStringMaxRadix(2);

  /** 返回表示此对象中存储的信息的 {@link Bundle}。 */
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_CODE, code);
    bundle.putString(FIELD_MESSAGE, message);
    if (!extras.isEmpty()) {
      bundle.putBundle(FIELD_EXTRAS, extras);
    }
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复 {@code SessionError}。 */
  public static SessionError fromBundle(Bundle bundle) {
    int code =
        bundle.getInt(FIELD_CODE, /* defaultValue= */ PlaybackException.ERROR_CODE_UNSPECIFIED);
    String message = bundle.getString(FIELD_MESSAGE, /* defaultValue= */ "");
    @Nullable Bundle extras = bundle.getBundle(FIELD_EXTRAS);
    return new SessionError(code, message, extras == null ? Bundle.EMPTY : extras);
  }
}