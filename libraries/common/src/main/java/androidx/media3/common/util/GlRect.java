package androidx.media3.common.util;

import static androidx.media3.common.util.Assertions.checkArgument;

/**
 * 通过其 4 个边的坐标（左、下、右、上）表示一个矩形。
 *
 * <p>注意，右和上坐标是独占的（不包含在矩形内）。
 *
 * <p>该类表示 OpenGL 坐标约定中的坐标：{@code left <= right} 且 {@code bottom <= top}。
 */
@UnstableApi
public final class GlRect {
  public int left;
  public int bottom;
  public int right;
  public int top;

  /** 创建一个从 (0, 0) 到指定宽度和高度的实例。 */
  public GlRect(int width, int height) {
    this(/* left= */ 0, /* bottom= */ 0, width, height);
  }

  /** 创建一个实例。 */
  public GlRect(int left, int bottom, int right, int top) {
    checkArgument(left <= right && bottom <= top);
    this.left = left;
    this.bottom = bottom;
    this.right = right;
    this.top = top;
  }
}