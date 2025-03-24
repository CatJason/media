package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.media3.common.util.UnstableApi;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** 用于指定解码视频帧信息的值类。 */
@UnstableApi
public class FrameInfo {

  /** {@link FrameInfo} 实例的构建器。 */
  public static final class Builder {

    private ColorInfo colorInfo;
    private int width;
    private int height;
    private float pixelWidthHeightRatio;
    private long offsetToAddUs;

    /**
     * 使用默认值创建实例。
     *
     * @param colorInfo {@link ColorInfo}。
     * @param width 帧的宽度，以像素为单位。
     * @param height 帧的高度，以像素为单位。
     */
    public Builder(ColorInfo colorInfo, int width, int height) {
      this.colorInfo = colorInfo;
      this.width = width;
      this.height = height;
      pixelWidthHeightRatio = 1;
    }

    /** 使用提供的 {@link FrameInfo} 的值创建实例。 */
    public Builder(FrameInfo frameInfo) {
      colorInfo = frameInfo.colorInfo;
      width = frameInfo.width;
      height = frameInfo.height;
      pixelWidthHeightRatio = frameInfo.pixelWidthHeightRatio;
      offsetToAddUs = frameInfo.offsetToAddUs;
    }

    /** 设置 {@link ColorInfo}。 */
    @CanIgnoreReturnValue
    public Builder setColorInfo(ColorInfo colorInfo) {
      this.colorInfo = colorInfo;
      return this;
    }

    /** 设置帧的宽度，以像素为单位。 */
    @CanIgnoreReturnValue
    public Builder setWidth(int width) {
      this.width = width;
      return this;
    }

    /** 设置帧的高度，以像素为单位。 */
    @CanIgnoreReturnValue
    public Builder setHeight(int height) {
      this.height = height;
      return this;
    }

    /**
     * 设置每个像素的宽高比。
     *
     * <p>默认值为 {@code 1}。
     */
    @CanIgnoreReturnValue
    public Builder setPixelWidthHeightRatio(float pixelWidthHeightRatio) {
      this.pixelWidthHeightRatio = pixelWidthHeightRatio;
      return this;
    }

    /**
     * 设置要添加到帧呈现时间戳的偏移量，以微秒为单位。
     *
     * <p>默认值为 {@code 0}。
     */
    @CanIgnoreReturnValue
    public Builder setOffsetToAddUs(long offsetToAddUs) {
      this.offsetToAddUs = offsetToAddUs;
      return this;
    }

    /** 构建 {@link FrameInfo} 实例。 */
    public FrameInfo build() {
      return new FrameInfo(colorInfo, width, height, pixelWidthHeightRatio, offsetToAddUs);
    }
  }

  /** 帧的 {@link ColorInfo}。 */
  public final ColorInfo colorInfo;

  /** 帧的宽度，以像素为单位。 */
  public final int width;

  /** 帧的高度，以像素为单位。 */
  public final int height;

  /** 每个像素的宽高比。 */
  public final float pixelWidthHeightRatio;

  /**
   * 必须添加到帧呈现时间戳的偏移量，以微秒为单位。
   *
   * <p>此偏移量不属于输入时间戳。它在处理前被添加到帧时间戳中，并保留在输出时间戳中。
   */
  public final long offsetToAddUs;

  private FrameInfo(
      ColorInfo colorInfo, int width, int height, float pixelWidthHeightRatio, long offsetToAddUs) {
    checkArgument(width > 0, "宽度必须为正数，但为: " + width);
    checkArgument(height > 0, "高度必须为正数，但为: " + height);

    this.colorInfo = colorInfo;
    this.width = width;
    this.height = height;
    this.pixelWidthHeightRatio = pixelWidthHeightRatio;
    this.offsetToAddUs = offsetToAddUs;
  }
}