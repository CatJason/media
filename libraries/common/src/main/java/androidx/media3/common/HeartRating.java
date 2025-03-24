package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;

/**
 * 以“喜欢”或“不喜欢”表示的评分。可用于指示内容是否为收藏。
 */
public final class HeartRating extends Rating {

  private final boolean rated; // 是否已评分
  private final boolean isHeart; // 是否为“喜欢”

  /** 创建一个未评分的实例。 */
  public HeartRating() {
    rated = false;
    isHeart = false;
  }

  /**
   * 创建一个已评分的实例。
   *
   * @param isHeart {@code true} 表示“喜欢”，{@code false} 表示“不喜欢”。
   */
  public HeartRating(boolean isHeart) {
    rated = true;
    this.isHeart = isHeart;
  }

  @Override
  public boolean isRated() {
    return rated;
  }

  /** 返回评分是否为“喜欢”。 */
  public boolean isHeart() {
    return isHeart;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(rated, isHeart);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof HeartRating)) {
      return false;
    }
    HeartRating other = (HeartRating) obj;
    return isHeart == other.isHeart && rated == other.rated;
  }

  private static final @RatingType int TYPE = RATING_TYPE_HEART; // 评分类型为“喜欢”

  private static final String FIELD_RATED = Util.intToStringMaxRadix(1); // 是否已评分的字段标识
  private static final String FIELD_IS_HEART = Util.intToStringMaxRadix(2); // 是否为“喜欢”的字段标识

  @UnstableApi
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_RATING_TYPE, TYPE); // 添加评分类型
    bundle.putBoolean(FIELD_RATED, rated); // 添加是否已评分
    bundle.putBoolean(FIELD_IS_HEART, isHeart); // 添加是否为“喜欢”
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复一个 {@code HeartRating} 实例。 */
  @UnstableApi
  public static HeartRating fromBundle(Bundle bundle) {
    checkArgument(bundle.getInt(FIELD_RATING_TYPE, /* defaultValue= */ RATING_TYPE_UNSET) == TYPE);
    boolean isRated = bundle.getBoolean(FIELD_RATED, /* defaultValue= */ false);
    return isRated
        ? new HeartRating(bundle.getBoolean(FIELD_IS_HEART, /* defaultValue= */ false))
        : new HeartRating();
  }
}