package androidx.media3.common;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Thrown when a non locally recoverable playback failure occurs. */
public class PlaybackException extends Exception {

  /**
   * 标识播放器错误原因的代码。
   *
   * <p>此错误列表可能会在未来的版本中扩展，并且 {@link Player} 实现可以定义自定义错误代码。
   */
// @Target 列表包括 'default' 目标和 TYPE_USE，以确保与添加 TYPE_USE 之前的 Kotlin 用法兼容。
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef(
      open = true,
      value = {
          ERROR_CODE_INVALID_STATE, // 无效状态
          ERROR_CODE_BAD_VALUE, // 错误的值
          ERROR_CODE_PERMISSION_DENIED, // 权限被拒绝
          ERROR_CODE_NOT_SUPPORTED, // 不支持的操作
          ERROR_CODE_DISCONNECTED, // 连接断开
          ERROR_CODE_AUTHENTICATION_EXPIRED, // 认证过期
          ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED, // 需要高级账户
          ERROR_CODE_CONCURRENT_STREAM_LIMIT, // 并发流限制
          ERROR_CODE_PARENTAL_CONTROL_RESTRICTED, // 家长控制限制
          ERROR_CODE_NOT_AVAILABLE_IN_REGION, // 在当前区域不可用
          ERROR_CODE_SKIP_LIMIT_REACHED, // 跳过限制已达到
          ERROR_CODE_SETUP_REQUIRED, // 需要设置
          ERROR_CODE_END_OF_PLAYLIST, // 播放列表结束
          ERROR_CODE_CONTENT_ALREADY_PLAYING, // 内容已在播放
          ERROR_CODE_UNSPECIFIED, // 未指定的错误
          ERROR_CODE_REMOTE_ERROR, // 远程错误
          ERROR_CODE_BEHIND_LIVE_WINDOW, // 落后于直播窗口
          ERROR_CODE_TIMEOUT, // 超时
          ERROR_CODE_FAILED_RUNTIME_CHECK, // 运行时检查失败
          ERROR_CODE_IO_UNSPECIFIED, // 未指定的 I/O 错误
          ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, // 网络连接失败
          ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, // 网络连接超时
          ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE, // 无效的 HTTP 内容类型
          ERROR_CODE_IO_BAD_HTTP_STATUS, // 错误的 HTTP 状态
          ERROR_CODE_IO_FILE_NOT_FOUND, // 文件未找到
          ERROR_CODE_IO_NO_PERMISSION, // 没有权限
          ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED, // 不允许明文传输
          ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE, // 读取位置超出范围
          ERROR_CODE_PARSING_CONTAINER_MALFORMED, // 容器格式解析错误
          ERROR_CODE_PARSING_MANIFEST_MALFORMED, // 清单文件解析错误
          ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, // 不支持的容器格式
          ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED, // 不支持的清单格式
          ERROR_CODE_DECODER_INIT_FAILED, // 解码器初始化失败
          ERROR_CODE_DECODER_QUERY_FAILED, // 解码器查询失败
          ERROR_CODE_DECODING_FAILED, // 解码失败
          ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES, // 解码格式超出设备能力
          ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, // 不支持的解码格式
          ERROR_CODE_AUDIO_TRACK_INIT_FAILED, // 音频轨道初始化失败
          ERROR_CODE_AUDIO_TRACK_WRITE_FAILED, // 音频轨道写入失败
          ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED, // 音频轨道卸载初始化失败
          ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED, // 音频轨道卸载写入失败
          ERROR_CODE_DRM_UNSPECIFIED, // 未指定的 DRM 错误
          ERROR_CODE_DRM_SCHEME_UNSUPPORTED, // 不支持的 DRM 方案
          ERROR_CODE_DRM_PROVISIONING_FAILED, // DRM 配置失败
          ERROR_CODE_DRM_CONTENT_ERROR, // DRM 内容错误
          ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED, // DRM 许可证获取失败
          ERROR_CODE_DRM_DISALLOWED_OPERATION, // DRM 不允许的操作
          ERROR_CODE_DRM_SYSTEM_ERROR, // DRM 系统错误
          ERROR_CODE_DRM_DEVICE_REVOKED, // DRM 设备被吊销
          ERROR_CODE_DRM_LICENSE_EXPIRED // DRM 许可证过期
      })
  public @interface ErrorCode {}

  // Policy errors (-1 to -999)
  /** 由于当前状态无效而无法完成命令导致的错误。 */
  public static final int ERROR_CODE_INVALID_STATE = -2;

  /** 由于参数非法导致的错误。 */
  public static final int ERROR_CODE_BAD_VALUE = -3;

  /** 由于命令不被允许导致的错误。 */
  public static final int ERROR_CODE_PERMISSION_DENIED = -4;

  /** 由于命令不被支持导致的错误。 */
  public static final int ERROR_CODE_NOT_SUPPORTED = -6;

  /** 由于组件断开连接导致的错误。 */
  public static final int ERROR_CODE_DISCONNECTED = -100;

  /** 由于认证过期导致的错误。 */
  public static final int ERROR_CODE_AUTHENTICATION_EXPIRED = -102;

  /** 由于需要高级账户但用户未订阅导致的错误。 */
  public static final int ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED = -103;

  /** 由于并发流数量过多导致的错误。 */
  public static final int ERROR_CODE_CONCURRENT_STREAM_LIMIT = -104;

  /** 由于内容被家长控制屏蔽导致的错误。 */
  public static final int ERROR_CODE_PARENTAL_CONTROL_RESTRICTED = -105;

  /** 由于内容在区域不可用导致的错误。 */
  public static final int ERROR_CODE_NOT_AVAILABLE_IN_REGION = -106;

  /** 由于跳过次数达到限制导致的错误。 */
  public static final int ERROR_CODE_SKIP_LIMIT_REACHED = -107;

  /** 由于播放需要手动用户干预导致的错误。 */
  public static final int ERROR_CODE_SETUP_REQUIRED = -108;

  /** 由于导航失败，因为播放列表已耗尽导致的错误。 */
  public static final int ERROR_CODE_END_OF_PLAYLIST = -109;

  /** 由于请求的内容已在播放导致的错误。 */
  public static final int ERROR_CODE_CONTENT_ALREADY_PLAYING = -110;

// 其他错误 (1xxx)。

  /** 由于无法识别原因导致的错误。 */
  public static final int ERROR_CODE_UNSPECIFIED = 1000;

  /**
   * 由于远程播放器（运行在不同主机或进程中的播放器）中未识别的错误导致的错误。
   */
  public static final int ERROR_CODE_REMOTE_ERROR = 1001;

  /** 由于加载位置落后于可用直播内容的滑动窗口导致的错误。 */
  public static final int ERROR_CODE_BEHIND_LIVE_WINDOW = 1002;

  /** 由于通用超时导致的错误。 */
  public static final int ERROR_CODE_TIMEOUT = 1003;

  /**
   * 由于运行时检查失败导致的错误。
   *
   * <p>当应用程序未遵守播放器的 API 要求（例如传递了无效参数）或播放器达到无效状态时，可能会发生此错误。
   */
  public static final int ERROR_CODE_FAILED_RUNTIME_CHECK = 1004;

// 输入/输出错误 (2xxx)。

  /** 由于无法识别的输入/输出错误导致的错误。 */
  public static final int ERROR_CODE_IO_UNSPECIFIED = 2000;

  /**
   * 由于网络连接失败导致的错误。
   *
   * <p>以下是非详尽的可能原因列表：
   *
   * <ul>
   *   <li>没有网络连接（可以通过查询 {@link ConnectivityManager#getActiveNetwork} 检查）。
   *   <li>URL 的域名拼写错误或不存在。
   *   <li>目标主机无法访问。
   *   <li>服务器意外关闭连接。
   * </ul>
   */
  public static final int ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001;

  /** 由于网络超时，意味着服务器处理请求时间过长导致的错误。 */
  public static final int ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002;

  /**
   * 由于服务器返回的资源具有无效的 "Content-Type" HTTP 标头值导致的错误。
   *
   * <p>例如，当播放器期望获取媒体资源，但服务器返回了一个付费墙 HTML 页面（内容类型为 "text/html"）时，可能会发生此错误。
   */
  public static final int ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE = 2003;

  /** 由于 HTTP 服务器返回意外的 HTTP 响应状态码导致的错误。 */
  public static final int ERROR_CODE_IO_BAD_HTTP_STATUS = 2004;

  /** 由于文件不存在导致的错误。 */
  public static final int ERROR_CODE_IO_FILE_NOT_FOUND = 2005;

  /**
   * 由于缺乏执行 I/O 操作的权限导致的错误。例如，缺乏访问互联网或外部存储的权限。
   */
  public static final int ERROR_CODE_IO_NO_PERMISSION = 2006;

  /**
   * 由于播放器尝试访问明文 HTTP 流量（即 http:// 而不是 https://），而应用的网络安全配置不允许导致的错误。
   *
   * <p>请参阅 <a
   * href="https://developer.android.com/guide/topics/media/issues/cleartext-not-permitted">此相关故障排除主题</a>。
   */
  public static final int ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED = 2007;

  /** 由于读取数据超出数据范围导致的错误。 */
  public static final int ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 2008;

// 内容解析错误 (3xxx)。

  /** 由于与媒体容器格式比特流相关的解析错误导致的错误。 */
  public static final int ERROR_CODE_PARSING_CONTAINER_MALFORMED = 3001;

  /**
   * 由于与媒体清单相关的解析错误导致的错误。媒体清单的示例包括 DASH 或 SmoothStreaming 清单，或 HLS 播放列表。
   */
  public static final int ERROR_CODE_PARSING_MANIFEST_MALFORMED = 3002;

  /**
   * 由于尝试提取具有不受支持的媒体容器格式或不受支持的媒体容器功能的文件导致的错误。
   */
  public static final int ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED = 3003;

  /**
   * 由于媒体清单中不受支持的功能导致的错误。媒体清单的示例包括 DASH 或 SmoothStreaming 清单，或 HLS 播放列表。
   */
  public static final int ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED = 3004;

// 解码错误 (4xxx)。

  /** 由于解码器初始化失败导致的错误。 */
  public static final int ERROR_CODE_DECODER_INIT_FAILED = 4001;

  /** 由于解码器查询失败导致的错误。 */
  public static final int ERROR_CODE_DECODER_QUERY_FAILED = 4002;

  /** 由于尝试解码媒体样本时失败导致的错误。 */
  public static final int ERROR_CODE_DECODING_FAILED = 4003;

  /** 由于尝试解码格式超出设备能力的内容导致的错误。 */
  public static final int ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES = 4004;

  /** 由于尝试解码格式不受支持的内容导致的错误。 */
  public static final int ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 4005;

// TODO: b/322943860 - 稳定错误代码并添加到 IntDef
  /** 由于更高优先级的任务回收了解码所需的资源导致的错误。 */
  @UnstableApi public static final int ERROR_CODE_DECODING_RESOURCES_RECLAIMED = 4006;

// AudioTrack 错误 (5xxx)。

  /** 由于 AudioTrack 初始化失败导致的错误。 */
  public static final int ERROR_CODE_AUDIO_TRACK_INIT_FAILED = 5001;

  /** 由于 AudioTrack 写入操作失败导致的错误。 */
  public static final int ERROR_CODE_AUDIO_TRACK_WRITE_FAILED = 5002;

  /** 由于 AudioTrack 在卸载模式下的写入操作失败导致的错误。 */
  public static final int ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED = 5003;

  /** 由于 AudioTrack 在卸载模式下的初始化操作失败导致的错误。 */
  public static final int ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED = 5004;

// DRM 错误 (6xxx)。

  /** 由于与 DRM 保护相关的未指定错误导致的错误。 */
  public static final int ERROR_CODE_DRM_UNSPECIFIED = 6000;

  /**
   * 由于设备不支持所选的 DRM 保护方案导致的错误。DRM 保护方案的示例包括 ClearKey 和 Widevine。
   */
  public static final int ERROR_CODE_DRM_SCHEME_UNSUPPORTED = 6001;

  /** 由于设备配置失败导致的错误。 */
  public static final int ERROR_CODE_DRM_PROVISIONING_FAILED = 6002;

  /**
   * 由于尝试播放不兼容的 DRM 保护内容导致的错误。
   *
   * <p>例如，当尝试播放使用 DRM 保护方案（如 Widevine）的流，但没有相应的许可证获取数据（如 pssh 盒子）时，可能会发生此错误。
   */
  public static final int ERROR_CODE_DRM_CONTENT_ERROR = 6003;

  /** 由于尝试获取许可证时失败导致的错误。 */
  public static final int ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED = 6004;

  /** 由于许可证策略不允许的操作导致的错误。 */
  public static final int ERROR_CODE_DRM_DISALLOWED_OPERATION = 6005;

  /** 由于 DRM 系统中的错误导致的错误。 */
  public static final int ERROR_CODE_DRM_SYSTEM_ERROR = 6006;

  /** 由于设备的 DRM 权限被吊销导致的错误。 */
  public static final int ERROR_CODE_DRM_DEVICE_REVOKED = 6007;

  /** 由于将过期的 DRM 许可证加载到打开的 DRM 会话中导致的错误。 */
  public static final int ERROR_CODE_DRM_LICENSE_EXPIRED = 6008;

// 帧处理错误 (7xxx)。

  /** 由于初始化 {@link VideoFrameProcessor} 时失败导致的错误。 */
  @UnstableApi public static final int ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED = 7000;

  /** 由于处理视频帧时失败导致的错误。 */
  @UnstableApi public static final int ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED = 7001;

  /**
   * 播放器实现如果希望展示自定义错误，可以使用大于此值的错误代码，以避免与此类中定义的其他错误代码冲突。
   */
  public static final int CUSTOM_ERROR_CODE_BASE = 1000000;

  /** Returns the name of a given {@code errorCode}. */
  public static String getErrorCodeName(@ErrorCode int errorCode) {
    switch (errorCode) {
      case ERROR_CODE_INVALID_STATE:
        return "ERROR_CODE_INVALID_STATE";
      case ERROR_CODE_BAD_VALUE:
        return "ERROR_CODE_BAD_VALUE";
      case ERROR_CODE_PERMISSION_DENIED:
        return "ERROR_CODE_PERMISSION_DENIED";
      case ERROR_CODE_NOT_SUPPORTED:
        return "ERROR_CODE_NOT_SUPPORTED";
      case ERROR_CODE_DISCONNECTED:
        return "ERROR_CODE_DISCONNECTED";
      case ERROR_CODE_AUTHENTICATION_EXPIRED:
        return "ERROR_CODE_AUTHENTICATION_EXPIRED";
      case ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED:
        return "ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED";
      case ERROR_CODE_CONCURRENT_STREAM_LIMIT:
        return "ERROR_CODE_CONCURRENT_STREAM_LIMIT";
      case ERROR_CODE_PARENTAL_CONTROL_RESTRICTED:
        return "ERROR_CODE_PARENTAL_CONTROL_RESTRICTED";
      case ERROR_CODE_NOT_AVAILABLE_IN_REGION:
        return "ERROR_CODE_NOT_AVAILABLE_IN_REGION";
      case ERROR_CODE_SKIP_LIMIT_REACHED:
        return "ERROR_CODE_SKIP_LIMIT_REACHED";
      case ERROR_CODE_SETUP_REQUIRED:
        return "ERROR_CODE_SETUP_REQUIRED";
      case ERROR_CODE_END_OF_PLAYLIST:
        return "ERROR_CODE_END_OF_PLAYLIST";
      case ERROR_CODE_CONTENT_ALREADY_PLAYING:
        return "ERROR_CODE_CONTENT_ALREADY_PLAYING";
      case ERROR_CODE_UNSPECIFIED:
        return "ERROR_CODE_UNSPECIFIED";
      case ERROR_CODE_REMOTE_ERROR:
        return "ERROR_CODE_REMOTE_ERROR";
      case ERROR_CODE_BEHIND_LIVE_WINDOW:
        return "ERROR_CODE_BEHIND_LIVE_WINDOW";
      case ERROR_CODE_TIMEOUT:
        return "ERROR_CODE_TIMEOUT";
      case ERROR_CODE_FAILED_RUNTIME_CHECK:
        return "ERROR_CODE_FAILED_RUNTIME_CHECK";
      case ERROR_CODE_IO_UNSPECIFIED:
        return "ERROR_CODE_IO_UNSPECIFIED";
      case ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
        return "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED";
      case ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
        return "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT";
      case ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE:
        return "ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE";
      case ERROR_CODE_IO_BAD_HTTP_STATUS:
        return "ERROR_CODE_IO_BAD_HTTP_STATUS";
      case ERROR_CODE_IO_FILE_NOT_FOUND:
        return "ERROR_CODE_IO_FILE_NOT_FOUND";
      case ERROR_CODE_IO_NO_PERMISSION:
        return "ERROR_CODE_IO_NO_PERMISSION";
      case ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED:
        return "ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED";
      case ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE:
        return "ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE";
      case ERROR_CODE_PARSING_CONTAINER_MALFORMED:
        return "ERROR_CODE_PARSING_CONTAINER_MALFORMED";
      case ERROR_CODE_PARSING_MANIFEST_MALFORMED:
        return "ERROR_CODE_PARSING_MANIFEST_MALFORMED";
      case ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
        return "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED";
      case ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
        return "ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED";
      case ERROR_CODE_DECODER_INIT_FAILED:
        return "ERROR_CODE_DECODER_INIT_FAILED";
      case ERROR_CODE_DECODER_QUERY_FAILED:
        return "ERROR_CODE_DECODER_QUERY_FAILED";
      case ERROR_CODE_DECODING_FAILED:
        return "ERROR_CODE_DECODING_FAILED";
      case ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES:
        return "ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES";
      case ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
        return "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED";
      case ERROR_CODE_DECODING_RESOURCES_RECLAIMED:
        return "ERROR_CODE_DECODING_RESOURCES_RECLAIMED";
      case ERROR_CODE_AUDIO_TRACK_INIT_FAILED:
        return "ERROR_CODE_AUDIO_TRACK_INIT_FAILED";
      case ERROR_CODE_AUDIO_TRACK_WRITE_FAILED:
        return "ERROR_CODE_AUDIO_TRACK_WRITE_FAILED";
      case ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED:
        return "ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED";
      case ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED:
        return "ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED";
      case ERROR_CODE_DRM_UNSPECIFIED:
        return "ERROR_CODE_DRM_UNSPECIFIED";
      case ERROR_CODE_DRM_SCHEME_UNSUPPORTED:
        return "ERROR_CODE_DRM_SCHEME_UNSUPPORTED";
      case ERROR_CODE_DRM_PROVISIONING_FAILED:
        return "ERROR_CODE_DRM_PROVISIONING_FAILED";
      case ERROR_CODE_DRM_CONTENT_ERROR:
        return "ERROR_CODE_DRM_CONTENT_ERROR";
      case ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED:
        return "ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED";
      case ERROR_CODE_DRM_DISALLOWED_OPERATION:
        return "ERROR_CODE_DRM_DISALLOWED_OPERATION";
      case ERROR_CODE_DRM_SYSTEM_ERROR:
        return "ERROR_CODE_DRM_SYSTEM_ERROR";
      case ERROR_CODE_DRM_DEVICE_REVOKED:
        return "ERROR_CODE_DRM_DEVICE_REVOKED";
      case ERROR_CODE_DRM_LICENSE_EXPIRED:
        return "ERROR_CODE_DRM_LICENSE_EXPIRED";
      case ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED:
        return "ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED";
      case ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED:
        return "ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED";
      default:
        if (errorCode >= CUSTOM_ERROR_CODE_BASE) {
          return "custom error code";
        } else {
          return "invalid error code";
        }
    }
  }
  /**
   * 等效于 {@link PlaybackException#getErrorCodeName(int) PlaybackException.getErrorCodeName(this.errorCode)}。
   */
  public final String getErrorCodeName() {
    return getErrorCodeName(errorCode);
  }

  /** 标识播放失败原因的错误代码。 */
  public final @ErrorCode int errorCode;

  /** 创建此异常时 {@link SystemClock#elapsedRealtime()} 的值。 */
  public final long timestampMs;

  /** 附加的 {@link Bundle}。 */
  @UnstableApi public final Bundle extras;

  /**
   * 创建一个实例。
   *
   * @param errorCode 标识错误原因的数字。可以是 {@link ErrorCode ErrorCodes} 之一。
   * @param cause 参见 {@link #getCause()}。
   * @param message 参见 {@link #getMessage()}。
   */
  @UnstableApi
  public PlaybackException(
      @Nullable String message, @Nullable Throwable cause, @ErrorCode int errorCode) {
    this(message, cause, errorCode, Bundle.EMPTY, Clock.DEFAULT.elapsedRealtime());
  }

  /**
   * 创建一个实例。
   *
   * @param errorCode 标识错误原因的数字。可以是 {@link ErrorCode ErrorCodes} 之一。
   * @param cause 参见 {@link #getCause()}。
   * @param message 参见 {@link #getMessage()}。
   * @param extras 可选的 {@link Bundle}。
   */
  @UnstableApi
  public PlaybackException(
      @Nullable String message,
      @Nullable Throwable cause,
      @ErrorCode int errorCode,
      Bundle extras) {
    this(message, cause, errorCode, extras, Clock.DEFAULT.elapsedRealtime());
  }

  /** Creates a new instance using the fields obtained from the given {@link Bundle}. */
  @UnstableApi
  protected PlaybackException(Bundle bundle) {
    this(
        /* message= */ bundle.getString(FIELD_STRING_MESSAGE),
        /* cause= */ getCauseFromBundle(bundle),
        /* errorCode= */ bundle.getInt(
            FIELD_INT_ERROR_CODE, /* defaultValue= */ ERROR_CODE_UNSPECIFIED),
        /* extras= */ getExtrasFromBundle(bundle),
        /* timestampMs= */ bundle.getLong(
            FIELD_LONG_TIMESTAMP_MS, /* defaultValue= */ SystemClock.elapsedRealtime()));
  }

  /** Creates a new instance using the given values. */
  @UnstableApi
  protected PlaybackException(
      @Nullable String message,
      @Nullable Throwable cause,
      @ErrorCode int errorCode,
      Bundle extras,
      long timestampMs) {
    super(message, cause);
    this.errorCode = errorCode;
    this.extras = extras;
    this.timestampMs = timestampMs;
  }

  /**
   * 返回与此异常关联的错误数据是否与 {@code other} 关联的错误数据相等。
   *
   * <p>请注意，此方法不会比较异常的堆栈跟踪。
   */
  @CallSuper
  public boolean errorInfoEquals(@Nullable PlaybackException other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    @Nullable Throwable thisCause = getCause();
    @Nullable Throwable thatCause = other.getCause();
    if (thisCause != null && thatCause != null) {
      if (!Util.areEqual(thisCause.getMessage(), thatCause.getMessage())) {
        return false;
      }
      if (!Util.areEqual(thisCause.getClass(), thatCause.getClass())) {
        return false;
      }
    } else if (thisCause != null || thatCause != null) {
      return false;
    }
    return errorCode == other.errorCode
        && Util.areEqual(getMessage(), other.getMessage())
        && timestampMs == other.timestampMs;
  }

  private static final String FIELD_INT_ERROR_CODE = Util.intToStringMaxRadix(0);
  private static final String FIELD_LONG_TIMESTAMP_MS = Util.intToStringMaxRadix(1);
  private static final String FIELD_STRING_MESSAGE = Util.intToStringMaxRadix(2);
  private static final String FIELD_STRING_CAUSE_CLASS_NAME = Util.intToStringMaxRadix(3);
  private static final String FIELD_STRING_CAUSE_MESSAGE = Util.intToStringMaxRadix(4);
  private static final String FIELD_BUNDLE_EXTRAS = Util.intToStringMaxRadix(5);

  /**
   * 定义了子类在实现 {@link #toBundle()} 并委托给 {@link #PlaybackException(Bundle)} 时使用的最小字段 ID 值。
   *
   * <p>子类应通过对此常量应用一个非负偏移量，并将结果传递给 {@link Util#intToStringMaxRadix(int)}，来获取其 {@link Bundle Bundle's} 字段键。
   */
  @UnstableApi protected static final int FIELD_CUSTOM_ID_BASE = 1000;

  /** Restores a {@code PlaybackException} from a {@link Bundle}. */
  @UnstableApi
  public static PlaybackException fromBundle(Bundle bundle) {
    return new PlaybackException(bundle);
  }

  /** Returns a {@link Bundle} representing the information stored in this exception. */
  @UnstableApi
  @CallSuper
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_INT_ERROR_CODE, errorCode);
    bundle.putLong(FIELD_LONG_TIMESTAMP_MS, timestampMs);
    bundle.putString(FIELD_STRING_MESSAGE, getMessage());
    bundle.putBundle(FIELD_BUNDLE_EXTRAS, extras);
    @Nullable Throwable cause = getCause();
    if (cause != null) {
      bundle.putString(FIELD_STRING_CAUSE_CLASS_NAME, cause.getClass().getName());
      bundle.putString(FIELD_STRING_CAUSE_MESSAGE, cause.getMessage());
    }
    return bundle;
  }

  // Creates a new {@link Throwable} with possibly {@code null} message.
  @SuppressWarnings("nullness:argument")
  private static Throwable createThrowable(Class<?> clazz, @Nullable String message)
      throws Exception {
    return (Throwable) clazz.getConstructor(String.class).newInstance(message);
  }

  // Creates a new {@link RemoteException} with possibly {@code null} message.
  @SuppressWarnings("nullness:argument")
  private static RemoteException createRemoteException(@Nullable String message) {
    return new RemoteException(message);
  }

  private static Bundle getExtrasFromBundle(Bundle bundle) {
    Bundle extras = bundle.getBundle(FIELD_BUNDLE_EXTRAS);
    return extras != null ? extras : Bundle.EMPTY;
  }

  @Nullable
  private static Throwable getCauseFromBundle(Bundle bundle) {
    @Nullable String causeClassName = bundle.getString(FIELD_STRING_CAUSE_CLASS_NAME);
    @Nullable String causeMessage = bundle.getString(FIELD_STRING_CAUSE_MESSAGE);
    @Nullable Throwable cause = null;
    if (!TextUtils.isEmpty(causeClassName)) {
      try {
        Class<?> clazz =
            Class.forName(
                causeClassName, /* initialize= */ true, PlaybackException.class.getClassLoader());
        if (Throwable.class.isAssignableFrom(clazz)) {
          cause = createThrowable(clazz, causeMessage);
        }
      } catch (Throwable e) {
        // 在使用反射创建 cause 时发生错误，此处不执行任何操作，让 finally 块处理该问题。
      } finally {
        if (cause == null) {
          // Bundle 中有表示 cause 的字段，但我们无法使用反射重新创建异常。我们实例化一个 RemoteException 来反映此问题。
          cause = createRemoteException(causeMessage);
        }
      }
    }
    return cause;
  }
}
