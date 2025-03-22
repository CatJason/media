package androidx.media3.decoder;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/**
 * 描述加密输入样本结构的元数据。
 *
 * <p>此类是 {@link android.media.MediaCodec.CryptoInfo} 的兼容性封装类。
 */
@UnstableApi
public final class CryptoInfo {

  /**
   * 16 字节的初始化向量。如果内容的初始化向量短于 16 字节，则会附加 0 字节填充以将其扩展到所需的 16 字节长度。
   *
   * @see android.media.MediaCodec.CryptoInfo#iv
   */
  @Nullable public byte[] iv;

  /**
   * 16 字节的密钥 ID。
   *
   * @see android.media.MediaCodec.CryptoInfo#key
   */
  @Nullable public byte[] key;

  /**
   * 已应用的加密类型。必须是 {@link C.CryptoMode} 值之一。
   *
   * @see android.media.MediaCodec.CryptoInfo#mode
   */
  public @C.CryptoMode int mode;

  /**
   * 每个子样本中前导未加密字节的数量。如果为 null，则所有字节均被视为加密，并且必须指定 {@link #numBytesOfEncryptedData}。
   *
   * @see android.media.MediaCodec.CryptoInfo#numBytesOfClearData
   */
  @Nullable public int[] numBytesOfClearData;

  /**
   * 每个子样本中尾部加密字节的数量。如果为 null，则所有字节均被视为未加密，并且必须指定 {@link #numBytesOfClearData}。
   *
   * @see android.media.MediaCodec.CryptoInfo#numBytesOfEncryptedData
   */
  @Nullable public int[] numBytesOfEncryptedData;

  /**
   * 组成缓冲区内容的子样本数量。
   *
   * @see android.media.MediaCodec.CryptoInfo#numSubSamples
   */
  public int numSubSamples;

  /**
   * @see android.media.MediaCodec.CryptoInfo.Pattern
   */
  public int encryptedBlocks;

  /**
   * @see android.media.MediaCodec.CryptoInfo.Pattern
   */
  public int clearBlocks;

  private final android.media.MediaCodec.CryptoInfo frameworkCryptoInfo;
  @Nullable private final PatternHolderV24 patternHolder;

  public CryptoInfo() {
    frameworkCryptoInfo = new android.media.MediaCodec.CryptoInfo();
    patternHolder = Util.SDK_INT >= 24 ? new PatternHolderV24(frameworkCryptoInfo) : null;
  }

  /**
   * @see android.media.MediaCodec.CryptoInfo#set(int, int[], int[], byte[], byte[], int)
   */
  public void set(
      int numSubSamples,
      int[] numBytesOfClearData,
      int[] numBytesOfEncryptedData,
      byte[] key,
      byte[] iv,
      @C.CryptoMode int mode,
      int encryptedBlocks,
      int clearBlocks) {
    this.numSubSamples = numSubSamples;
    this.numBytesOfClearData = numBytesOfClearData;
    this.numBytesOfEncryptedData = numBytesOfEncryptedData;
    this.key = key;
    this.iv = iv;
    this.mode = mode;
    this.encryptedBlocks = encryptedBlocks;
    this.clearBlocks = clearBlocks;
    // 直接更新 frameworkCryptoInfo 字段，因为 CryptoInfo.set 在 Android N 上会执行不必要的对象分配。
    frameworkCryptoInfo.numSubSamples = numSubSamples;
    frameworkCryptoInfo.numBytesOfClearData = numBytesOfClearData;
    frameworkCryptoInfo.numBytesOfEncryptedData = numBytesOfEncryptedData;
    frameworkCryptoInfo.key = key;
    frameworkCryptoInfo.iv = iv;
    frameworkCryptoInfo.mode = mode;
    if (Util.SDK_INT >= 24) {
      Assertions.checkNotNull(patternHolder).set(encryptedBlocks, clearBlocks);
    }
  }

  /**
   * 返回等效的 {@link android.media.MediaCodec.CryptoInfo} 实例。
   *
   * <p>对单个 {@link CryptoInfo} 连续调用此方法将返回相同的实例。对 {@link CryptoInfo} 的更改将反映在返回的对象中。返回的对象不应直接修改。
   *
   * @return 等效的 {@link android.media.MediaCodec.CryptoInfo} 实例。
   */
  public android.media.MediaCodec.CryptoInfo getFrameworkCryptoInfo() {
    return frameworkCryptoInfo;
  }

  /**
   * 将第一个子样本的未加密数据数量增加 {@code count}。
   *
   * <p>如果 {@code count} 为 0，则此方法不执行任何操作。否则，它将 {@code count} 添加到 {@link #numBytesOfClearData}[0]。
   *
   * <p>如果 {@link #numBytesOfClearData} 为 null（这是允许的），则此方法会将其实例化为一个新的 {@code int[1]}。
   *
   * @param count 要添加到 {@link #numBytesOfClearData} 第一个子样本的字节数。
   */
  public void increaseClearDataFirstSubSampleBy(int count) {
    if (count == 0) {
      return;
    }
    if (numBytesOfClearData == null) {
      numBytesOfClearData = new int[1];
      frameworkCryptoInfo.numBytesOfClearData = numBytesOfClearData;
    }
    numBytesOfClearData[0] += count;
  }

  @RequiresApi(24)
  private static final class PatternHolderV24 {

    private final android.media.MediaCodec.CryptoInfo frameworkCryptoInfo;
    private final android.media.MediaCodec.CryptoInfo.Pattern pattern;

    private PatternHolderV24(android.media.MediaCodec.CryptoInfo frameworkCryptoInfo) {
      this.frameworkCryptoInfo = frameworkCryptoInfo;
      pattern = new android.media.MediaCodec.CryptoInfo.Pattern(0, 0);
    }

    private void set(int encryptedBlocks, int clearBlocks) {
      pattern.set(encryptedBlocks, clearBlocks);
      frameworkCryptoInfo.setPattern(pattern);
    }
  }
}