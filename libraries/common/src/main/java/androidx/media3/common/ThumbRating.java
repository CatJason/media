package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;

/** 以“点赞”或“点踩”表示的评分。 */
public final class ThumbRating extends Rating {

  private final boolean rated;
  private final boolean isThumbsUp;

  /** 创建一个未评分的实例。 */
  public ThumbRating() {
    rated = false;
    isThumbsUp = false;
  }

  /**
   * 创建一个已评分的实例。
   *
   * @param isThumbsUp {@code true} 表示“点赞”，{@code false} 表示“点踩”。
   */
  public ThumbRating(boolean isThumbsUp) {
    rated = true;
    this.isThumbsUp = isThumbsUp;
  }

  @Override
  public boolean isRated() {
    return rated;
  }

  /** 返回评分是否为“点赞”。 */
  public boolean isThumbsUp() {
    return isThumbsUp;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(rated, isThumbsUp);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof ThumbRating)) {
      return false;
    }
    ThumbRating other = (ThumbRating) obj;
    return isThumbsUp == other.isThumbsUp && rated == other.rated;
  }

  private static final @RatingType int TYPE = RATING_TYPE_THUMB;

  private static final String FIELD_RATED = Util.intToStringMaxRadix(1);
  private static final String FIELD_IS_THUMBS_UP = Util.intToStringMaxRadix(2);

  @UnstableApi
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_RATING_TYPE, TYPE);
    bundle.putBoolean(FIELD_RATED, rated);
    bundle.putBoolean(FIELD_IS_THUMBS_UP, isThumbsUp);
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复一个 {@code ThumbRating}。 */
  @UnstableApi
  public static ThumbRating fromBundle(Bundle bundle) {
    checkArgument(bundle.getInt(FIELD_RATING_TYPE, /* defaultValue= */ RATING_TYPE_UNSET) == TYPE);
    boolean rated = bundle.getBoolean(FIELD_RATED, /* defaultValue= */ false);
    return rated
        ? new ThumbRating(bundle.getBoolean(FIELD_IS_THUMBS_UP, /* defaultValue= */ false))
        : new ThumbRating();
  }
}