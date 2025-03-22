package androidx.media3.decoder;

import androidx.media3.common.util.UnstableApi;

/** 当非平台组件无法解密数据时抛出的异常。 */
@UnstableApi
public class CryptoException extends Exception {

  /** 组件特定的错误代码。 */
  public final int errorCode;

  /**
   * @param errorCode 组件特定的错误代码。
   * @param message 详细信息。
   */
  public CryptoException(int errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}