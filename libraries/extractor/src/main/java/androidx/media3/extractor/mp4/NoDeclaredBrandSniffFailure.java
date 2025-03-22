package androidx.media3.extractor.mp4;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.SniffFailure;

/** {@link SniffFailure}，表示 MP4 文件未声明任何品牌。 */
@UnstableApi
public final class NoDeclaredBrandSniffFailure implements SniffFailure {

  // 单例实例
  public static final NoDeclaredBrandSniffFailure INSTANCE = new NoDeclaredBrandSniffFailure();

  // 私有构造函数，防止外部实例化
  private NoDeclaredBrandSniffFailure() {}
}
