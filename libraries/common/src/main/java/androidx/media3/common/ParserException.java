package androidx.media3.common;

import androidx.annotation.Nullable;
import androidx.media3.common.C.DataType;
import androidx.media3.common.util.UnstableApi;
import java.io.IOException;

/** 当解析媒体数据或元数据时发生错误时抛出的异常。 */
@UnstableApi
public class ParserException extends IOException {

  /**
   * 创建一个新实例，其中 {@link #contentIsMalformed} 为 true，且 {@link #dataType} 为 {@link C#DATA_TYPE_UNKNOWN}。
   *
   * @param message 参见 {@link #getMessage()}。
   * @param cause 参见 {@link #getCause()}。
   * @return 创建的实例。
   */
  public static ParserException createForMalformedDataOfUnknownType(
      @Nullable String message, @Nullable Throwable cause) {
    return new ParserException(message, cause, /* contentIsMalformed= */ true, C.DATA_TYPE_UNKNOWN);
  }

  /**
   * 创建一个新实例，其中 {@link #contentIsMalformed} 为 true，且 {@link #dataType} 为 {@link C#DATA_TYPE_MEDIA}。
   *
   * @param message 参见 {@link #getMessage()}。
   * @param cause 参见 {@link #getCause()}。
   * @return 创建的实例。
   */
  public static ParserException createForMalformedContainer(
      @Nullable String message, @Nullable Throwable cause) {
    return new ParserException(message, cause, /* contentIsMalformed= */ true, C.DATA_TYPE_MEDIA);
  }

  /**
   * 创建一个新实例，其中 {@link #contentIsMalformed} 为 true，且 {@link #dataType} 为 {@link C#DATA_TYPE_MANIFEST}。
   *
   * @param message 参见 {@link #getMessage()}。
   * @param cause 参见 {@link #getCause()}。
   * @return 创建的实例。
   */
  public static ParserException createForMalformedManifest(
      @Nullable String message, @Nullable Throwable cause) {
    return new ParserException(
        message, cause, /* contentIsMalformed= */ true, C.DATA_TYPE_MANIFEST);
  }

  /**
   * 创建一个新实例，其中 {@link #contentIsMalformed} 为 false，且 {@link #dataType} 为 {@link C#DATA_TYPE_MANIFEST}。
   *
   * @param message 参见 {@link #getMessage()}。
   * @param cause 参见 {@link #getCause()}。
   * @return 创建的实例。
   */
  public static ParserException createForManifestWithUnsupportedFeature(
      @Nullable String message, @Nullable Throwable cause) {
    return new ParserException(
        message, cause, /* contentIsMalformed= */ false, C.DATA_TYPE_MANIFEST);
  }

  /**
   * 创建一个新实例，其中 {@link #contentIsMalformed} 为 false，且 {@link #dataType} 为 {@link C#DATA_TYPE_MEDIA}。
   *
   * @param message 参见 {@link #getMessage()}。
   * @return 创建的实例。
   */
  public static ParserException createForUnsupportedContainerFeature(@Nullable String message) {
    return new ParserException(
        message, /* cause= */ null, /* contentIsMalformed= */ false, C.DATA_TYPE_MEDIA);
  }

  /**
   * 解析错误是否由于比特流未遵循预期格式引起。如果解析器遇到合法但不支持的条件，则可能为 false。
   */
  public final boolean contentIsMalformed;

  /** 解析的比特流的 {@link DataType 数据类型}。 */
  public final int dataType;

  protected ParserException(
      @Nullable String message,
      @Nullable Throwable cause,
      boolean contentIsMalformed,
      @DataType int dataType) {
    super(message, cause);
    this.contentIsMalformed = contentIsMalformed;
    this.dataType = dataType;
  }

  @Nullable
  @Override
  public String getMessage() {
    return super.getMessage()
        + " {contentIsMalformed="
        + contentIsMalformed
        + ", dataType="
        + dataType
        + "}";
  }
}