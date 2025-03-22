package androidx.media3.decoder;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

/** 当 {@link Decoder} 发生错误时抛出的异常。 */
@UnstableApi
public class DecoderException extends Exception {

  /**
   * 创建实例。
   *
   * @param message 此异常的详细信息。
   */
  public DecoderException(String message) {
    super(message);
  }

  /**
   * 创建实例。
   *
   * @param cause 此异常的原因，或 {@code null}。
   */
  public DecoderException(@Nullable Throwable cause) {
    super(cause);
  }

  /**
   * 创建实例。
   *
   * @param message 此异常的详细信息。
   * @param cause 此异常的原因，或 {@code null}。
   */
  public DecoderException(String message, @Nullable Throwable cause) {
    super(message, cause);
  }
}