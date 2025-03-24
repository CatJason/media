package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.os.Bundle;
import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;

/** 以百分比表示的评分。 */
public final class PercentageRating extends Rating {

  private final float percent;

  /** 创建一个未评分的实例。 */
  public PercentageRating() {
    percent = RATING_UNSET;
  }

  /**
   * 使用给定的百分比创建一个评分实例。
   *
   * @param percent 评分的百分比值。
   */
  public PercentageRating(@FloatRange(from = 0, to = 100) float percent) {
    checkArgument(percent >= 0.0f && percent <= 100.0f, "percent must be in the range of [0, 100]");
    this.percent = percent;
  }

  @Override
  public boolean isRated() {
    return percent != RATING_UNSET;
  }

  /**
   * 返回此评分的百分比值。范围在 {@code [0f, 100f]} 之间，如果未评分则返回 {@link #RATING_UNSET}。
   */
  public float getPercent() {
    return percent;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(percent);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof PercentageRating)) {
      return false;
    }
    return percent == ((PercentageRating) obj).percent;
  }

  private static final @RatingType int TYPE = RATING_TYPE_PERCENTAGE;

  private static final String FIELD_PERCENT = Util.intToStringMaxRadix(1);

  @UnstableApi
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_RATING_TYPE, TYPE);
    bundle.putFloat(FIELD_PERCENT, percent);
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复一个 {@code PercentageRating}。 */
  @UnstableApi
  public static PercentageRating fromBundle(Bundle bundle) {
    checkArgument(bundle.getInt(FIELD_RATING_TYPE, /* defaultValue= */ RATING_TYPE_UNSET) == TYPE);
    float percent = bundle.getFloat(FIELD_PERCENT, /* defaultValue= */ RATING_UNSET);
    return percent == RATING_UNSET ? new PercentageRating() : new PercentageRating(percent);
  }
}