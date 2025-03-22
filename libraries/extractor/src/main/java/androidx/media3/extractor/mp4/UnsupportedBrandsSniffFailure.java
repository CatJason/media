package androidx.media3.extractor.mp4;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.SniffFailure;
import com.google.common.primitives.ImmutableIntArray;

/**
 * 一个 {@link SniffFailure}，表示 MP4 文件的 {@code ftyp} 盒中声明的品牌均不受支持
 * （参见 ISO 14496-12:2012 第 4.3 节）。
 */
@UnstableApi
public final class UnsupportedBrandsSniffFailure implements SniffFailure {

  /** {@code ftyp} 盒中的 {@code major_brand}。 */
  public final int majorBrand;

  /** {@code ftyp} 盒中的 {@code compatible_brands} 列表。 */
  public final ImmutableIntArray compatibleBrands;

  public UnsupportedBrandsSniffFailure(int majorBrand, @Nullable int[] compatibleBrands) {
    this.majorBrand = majorBrand;
    this.compatibleBrands =
        compatibleBrands != null
            ? ImmutableIntArray.copyOf(compatibleBrands) // 如果 compatibleBrands 不为 null，则复制到 ImmutableIntArray
            : ImmutableIntArray.of(); // 否则，创建一个空的 ImmutableIntArray
  }
}