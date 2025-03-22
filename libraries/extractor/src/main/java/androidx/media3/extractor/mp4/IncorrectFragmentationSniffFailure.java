package androidx.media3.extractor.mp4;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.SniffFailure;

/**
 * {@link SniffFailure}，表示文件的碎片化标志与当前 {@link androidx.media3.extractor.Extractor} 不兼容。
 */
@UnstableApi
public final class IncorrectFragmentationSniffFailure implements SniffFailure {

  // 文件已碎片化的失败实例
  public static final IncorrectFragmentationSniffFailure FILE_FRAGMENTED =
      new IncorrectFragmentationSniffFailure(/* fileIsFragmented= */ true);

  // 文件未碎片化的失败实例
  public static final IncorrectFragmentationSniffFailure FILE_NOT_FRAGMENTED =
      new IncorrectFragmentationSniffFailure(/* fileIsFragmented= */ false);

  // 文件是否碎片化的标志
  public final boolean fileIsFragmented;

  // 私有构造函数，用于创建实例
  private IncorrectFragmentationSniffFailure(boolean fileIsFragmented) {
    this.fileIsFragmented = fileIsFragmented;
  }
}