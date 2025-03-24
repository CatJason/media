package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

/** 用于表示 {@link Surface} 及其相关信息的不可变值类。 */
@UnstableApi
public final class SurfaceInfo {

  /** {@link Surface} 对象。 */
  public final Surface surface;

  /** 渲染到 {@link #surface} 的帧的宽度，以像素为单位。 */
  public final int width;

  /** 渲染到 {@link #surface} 的帧的高度，以像素为单位。 */
  public final int height;

  /**
   * 在将帧渲染到 {@link #surface} 之前，应用于帧的逆时针旋转角度。
   *
   * <p>必须为 0、90、180 或 270 度。默认值为 0。
   */
  public final int orientationDegrees;

  /** {@link #surface} 是否为编码器输入表面。 */
  public final boolean isEncoderInputSurface;

  /** 创建一个新实例。 */
  public SurfaceInfo(Surface surface, int width, int height) {
    this(surface, width, height, /* orientationDegrees= */ 0);
  }

  /** 创建一个新实例。 */
  public SurfaceInfo(Surface surface, int width, int height, int orientationDegrees) {
    this(surface, width, height, orientationDegrees, /* isEncoderInputSurface= */ false);
  }

  /** 创建一个新实例。 */
  public SurfaceInfo(
      Surface surface,
      int width,
      int height,
      int orientationDegrees,
      boolean isEncoderInputSurface) {
    checkArgument(
        orientationDegrees == 0
            || orientationDegrees == 90
            || orientationDegrees == 180
            || orientationDegrees == 270,
        "orientationDegrees must be 0, 90, 180, or 270");
    this.surface = surface;
    this.width = width;
    this.height = height;
    this.orientationDegrees = orientationDegrees;
    this.isEncoderInputSurface = isEncoderInputSurface;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SurfaceInfo)) {
      return false;
    }
    SurfaceInfo that = (SurfaceInfo) o;
    return width == that.width
        && height == that.height
        && orientationDegrees == that.orientationDegrees
        && isEncoderInputSurface == that.isEncoderInputSurface
        && surface.equals(that.surface);
  }

  @Override
  public int hashCode() {
    int result = surface.hashCode();
    result = 31 * result + width;
    result = 31 * result + height;
    result = 31 * result + orientationDegrees;
    result = 31 * result + (isEncoderInputSurface ? 1 : 0);
    return result;
  }
}