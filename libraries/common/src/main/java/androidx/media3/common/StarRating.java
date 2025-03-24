package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.os.Bundle;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;

/** 以分数表示的星级评分。 */
public final class StarRating extends Rating {

  @IntRange(from = 1)
  private final int maxStars;

  private final float starRating;

  /**
   * 创建一个未评分的实例，指定 {@code maxStars}。如果 {@code maxStars} 不是正整数，则会抛出 {@link IllegalArgumentException}。
   *
   * @param maxStars 该评分可以拥有的最大星数。
   */
  public StarRating(@IntRange(from = 1) int maxStars) {
    checkArgument(maxStars > 0, "maxStars must be a positive integer");
    this.maxStars = maxStars;
    starRating = RATING_UNSET;
  }

  /**
   * 创建一个已评分的实例，指定 {@code maxStars} 和给定的分数星数。非整数值可用于表示平均评分。如果 {@code maxStars} 不是正整数或 {@code starRating} 超出范围，则会抛出 {@link IllegalArgumentException}。
   *
   * @param maxStars 该评分可以拥有的最大星数。
   * @param starRating 该评分的分数星数，范围从 {@code 0f} 到 {@code maxStars}。
   */
  public StarRating(@IntRange(from = 1) int maxStars, @FloatRange(from = 0.0) float starRating) {
    checkArgument(maxStars > 0, "maxStars must be a positive integer");
    checkArgument(
        starRating >= 0.0f && starRating <= maxStars, "starRating is out of range [0, maxStars]");
    this.maxStars = maxStars;
    this.starRating = starRating;
  }

  @Override
  public boolean isRated() {
    return starRating != RATING_UNSET;
  }

  /** 返回最大星数。必须为正数。 */
  @IntRange(from = 1)
  public int getMaxStars() {
    return maxStars;
  }

  /**
   * 返回该评分的分数星数。范围从 {@code 0f} 到 {@link #maxStars}，如果未评分则返回 {@link #RATING_UNSET}。
   */
  public float getStarRating() {
    return starRating;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(maxStars, starRating);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof StarRating)) {
      return false;
    }
    StarRating other = (StarRating) obj;
    return maxStars == other.maxStars && starRating == other.starRating;
  }

  private static final @RatingType int TYPE = RATING_TYPE_STAR;
  private static final int MAX_STARS_DEFAULT = 5;

  private static final String FIELD_MAX_STARS = Util.intToStringMaxRadix(1);
  private static final String FIELD_STAR_RATING = Util.intToStringMaxRadix(2);

  @UnstableApi
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_RATING_TYPE, TYPE);
    bundle.putInt(FIELD_MAX_STARS, maxStars);
    bundle.putFloat(FIELD_STAR_RATING, starRating);
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复一个 {@code StarRating}。 */
  @UnstableApi
  public static StarRating fromBundle(Bundle bundle) {
    checkArgument(bundle.getInt(FIELD_RATING_TYPE, /* defaultValue= */ RATING_TYPE_UNSET) == TYPE);
    int maxStars = bundle.getInt(FIELD_MAX_STARS, /* defaultValue= */ MAX_STARS_DEFAULT);
    float starRating = bundle.getFloat(FIELD_STAR_RATING, /* defaultValue= */ RATING_UNSET);
    return starRating == RATING_UNSET
        ? new StarRating(maxStars)
        : new StarRating(maxStars, starRating);
  }
}