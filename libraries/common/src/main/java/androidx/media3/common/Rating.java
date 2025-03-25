package androidx.media3.common;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 媒体内容的评分。评分类型可以是 {@link HeartRating}、{@link PercentageRating}、{@link StarRating} 或 {@link ThumbRating} 之一。
 */
public abstract class Rating {

  /** 一个浮点值，表示评分未设置。 */
  /* package */ static final float RATING_UNSET = -1.0f;

  // 默认的包级私有构造函数，防止在此包之外扩展 Rating 类。
  /* package */ Rating() {}

  /** 评分是否存在。 */
  public abstract boolean isRated();

  /** 返回一个 {@link Bundle}，表示此评分中存储的信息。 */
  @UnstableApi
  public abstract Bundle toBundle();

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
      RATING_TYPE_UNSET,
      RATING_TYPE_HEART,
      RATING_TYPE_PERCENTAGE,
      RATING_TYPE_STAR,
      RATING_TYPE_THUMB
  })
      /* package */ @interface RatingType {}

  /* package */ static final int RATING_TYPE_UNSET = -1;
  /* package */ static final int RATING_TYPE_HEART = 0;
  /* package */ static final int RATING_TYPE_PERCENTAGE = 1;
  /* package */ static final int RATING_TYPE_STAR = 2;
  /* package */ static final int RATING_TYPE_THUMB = 3;

  /* package */ static final String FIELD_RATING_TYPE = Util.intToStringMaxRadix(0);

  /** 从 {@link Bundle} 恢复 {@code Rating}。 */
  @UnstableApi
  public static Rating fromBundle(Bundle bundle) {
    @RatingType
    int ratingType = bundle.getInt(FIELD_RATING_TYPE, /* defaultValue= */ RATING_TYPE_UNSET);
    switch (ratingType) {
      case RATING_TYPE_HEART:
        return HeartRating.fromBundle(bundle);
      case RATING_TYPE_PERCENTAGE:
        return PercentageRating.fromBundle(bundle);
      case RATING_TYPE_STAR:
        return StarRating.fromBundle(bundle);
      case RATING_TYPE_THUMB:
        return ThumbRating.fromBundle(bundle);
      case RATING_TYPE_UNSET:
      default:
        throw new IllegalArgumentException("未知的评分类型: " + ratingType);
    }
  }
}