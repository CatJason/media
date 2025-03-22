package androidx.media3.extractor.mp4;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.TrackOutput;

/**
 * 封装从 MP4 流中的轨道加密（tenc）盒或样本组描述（sgpd）盒解析的信息。
 */
@UnstableApi
public final class TrackEncryptionBox {

  private static final String TAG = "TrackEncryptionBox";

  /** 指示样本组中样本的加密状态。 */
  public final boolean isEncrypted;

  /** 保护方案类型，由 'schm' 盒定义，如果未知则为 null。 */
  @Nullable public final String schemeType;

  /**
   * 一个 {@link TrackOutput.CryptoData} 实例，包含来自此 {@link TrackEncryptionBox} 的加密信息。
   */
  public final TrackOutput.CryptoData cryptoData;

  /** 样本组中样本的初始化向量大小（字节）。 */
  public final int perSampleIvSize;

  /**
   * 如果 {@link #perSampleIvSize} 为 0，则保存轨道加密盒或样本组描述盒中定义的默认初始化向量。否则为 null。
   */
  @Nullable public final byte[] defaultInitializationVector;

  /**
   * @param isEncrypted 参见 {@link #isEncrypted}。
   * @param schemeType 参见 {@link #schemeType}。
   * @param perSampleIvSize 参见 {@link #perSampleIvSize}。
   * @param keyId 参见 {@link TrackOutput.CryptoData#encryptionKey}。
   * @param defaultEncryptedBlocks 参见 {@link TrackOutput.CryptoData#encryptedBlocks}。
   * @param defaultClearBlocks 参见 {@link TrackOutput.CryptoData#clearBlocks}。
   * @param defaultInitializationVector 参见 {@link #defaultInitializationVector}。
   */
  public TrackEncryptionBox(
      boolean isEncrypted,
      @Nullable String schemeType,
      int perSampleIvSize,
      byte[] keyId,
      int defaultEncryptedBlocks,
      int defaultClearBlocks,
      @Nullable byte[] defaultInitializationVector) {
    // 检查 perSampleIvSize 和 defaultInitializationVector 的逻辑关系
    Assertions.checkArgument(perSampleIvSize == 0 ^ defaultInitializationVector == null);
    this.isEncrypted = isEncrypted;
    this.schemeType = schemeType;
    this.perSampleIvSize = perSampleIvSize;
    this.defaultInitializationVector = defaultInitializationVector;
    // 创建 CryptoData 实例
    cryptoData =
        new TrackOutput.CryptoData(
            schemeToCryptoMode(schemeType), keyId, defaultEncryptedBlocks, defaultClearBlocks);
  }

  /**
   * 将保护方案类型转换为加密模式。
   *
   * @param schemeType 保护方案类型
   * @return 对应的加密模式
   */
  private static @C.CryptoMode int schemeToCryptoMode(@Nullable String schemeType) {
    if (schemeType == null) {
      // 如果未知，假设为 cenc 模式
      return C.CRYPTO_MODE_AES_CTR;
    }
    switch (schemeType) {
      case C.CENC_TYPE_cenc:
      case C.CENC_TYPE_cens:
        return C.CRYPTO_MODE_AES_CTR;
      case C.CENC_TYPE_cbc1:
      case C.CENC_TYPE_cbcs:
        return C.CRYPTO_MODE_AES_CBC;
      default:
        // 如果保护方案类型不受支持，记录警告并假设为 AES-CTR 模式
        Log.w(
            TAG,
            "Unsupported protection scheme type '"
                + schemeType
                + "'. Assuming AES-CTR crypto mode.");
        return C.CRYPTO_MODE_AES_CTR;
    }
  }
}