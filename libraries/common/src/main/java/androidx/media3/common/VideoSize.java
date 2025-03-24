package androidx.media3.common;

import android.os.Bundle;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/** 表示视频尺寸。 */
public final class VideoSize {

  private static final int DEFAULT_WIDTH = 0;
  private static final int DEFAULT_HEIGHT = 0;
  private static final float DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO = 1F;

  public static final VideoSize UNKNOWN = new VideoSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);

  /** 视频宽度，单位为像素，0 表示未知。 */
  @IntRange(from = 0)
  public final int width;

  /** 视频高度，单位为像素，0 表示未知。 */
  @IntRange(from = 0)
  public final int height;

  /**
   * @deprecated 旋转由播放器内部处理，因此此值始终为零。
   */
  @IntRange(from = 0, to = 359)
  @Deprecated
  public final int unappliedRotationDegrees;

  /**
   * 每个像素的宽高比，1 表示未知。
   *
   * <p>对于正常的方形像素，此值为 1.0。不同的值表示变形内容。
   */
  @FloatRange(from = 0, fromInclusive = false)
  public final float pixelWidthHeightRatio;

  /**
   * 创建一个不包含未应用旋转或变形内容的 VideoSize。
   *
   * @param width 视频宽度，单位为像素。
   * @param height 视频高度，单位为像素。
   */
  @UnstableApi
  public VideoSize(@IntRange(from = 0) int width, @IntRange(from = 0) int height) {
    this(width, height, DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO);
  }

  /**
   * 创建一个新实例。
   *
   * @param width 视频宽度，单位为像素。
   * @param height 视频高度，单位为像素。
   * @param pixelWidthHeightRatio 每个像素的宽高比。对于正常的方形像素，此值为 1.0。不同的值表示变形内容。
   */
  @SuppressWarnings("deprecation") // 设置已弃用的字段
  @UnstableApi
  public VideoSize(
      @IntRange(from = 0) int width,
      @IntRange(from = 0) int height,
      @FloatRange(from = 0, fromInclusive = false) float pixelWidthHeightRatio) {
    this.width = width;
    this.height = height;
    this.unappliedRotationDegrees = 0;
    this.pixelWidthHeightRatio = pixelWidthHeightRatio;
  }

  /**
   * @deprecated 请使用 {@link VideoSize#VideoSize(int, int, float)} 代替。在 API 21+ 上不需要 {@code unappliedRotationDegrees}，且始终为零。
   */
  @Deprecated
  @UnstableApi
  public VideoSize(
      @IntRange(from = 0) int width,
      @IntRange(from = 0) int height,
      @IntRange(from = 0, to = 359) int unappliedRotationDegrees,
      @FloatRange(from = 0, fromInclusive = false) float pixelWidthHeightRatio) {
    this(width, height, pixelWidthHeightRatio);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof VideoSize) {
      VideoSize other = (VideoSize) obj;
      return width == other.width
          && height == other.height
          && pixelWidthHeightRatio == other.pixelWidthHeightRatio;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int result = 7;
    result = 31 * result + width;
    result = 31 * result + height;
    result = 31 * result + Float.floatToRawIntBits(pixelWidthHeightRatio);
    return result;
  }

  private static final String FIELD_WIDTH = Util.intToStringMaxRadix(0);
  private static final String FIELD_HEIGHT = Util.intToStringMaxRadix(1);
  // 2 保留给已弃用的 'unappliedRotationDegrees'。
  private static final String FIELD_PIXEL_WIDTH_HEIGHT_RATIO = Util.intToStringMaxRadix(3);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    if (width != 0) {
      bundle.putInt(FIELD_WIDTH, width);
    }
    if (height != 0) {
      bundle.putInt(FIELD_HEIGHT, height);
    }
    if (pixelWidthHeightRatio != 1f) {
      bundle.putFloat(FIELD_PIXEL_WIDTH_HEIGHT_RATIO, pixelWidthHeightRatio);
    }
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复一个 {@code VideoSize}。 */
  @UnstableApi
  public static VideoSize fromBundle(Bundle bundle) {
    int width = bundle.getInt(FIELD_WIDTH, DEFAULT_WIDTH);
    int height = bundle.getInt(FIELD_HEIGHT, DEFAULT_HEIGHT);
    float pixelWidthHeightRatio =
        bundle.getFloat(FIELD_PIXEL_WIDTH_HEIGHT_RATIO, DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO);
    return new VideoSize(width, height, pixelWidthHeightRatio);
  }
}