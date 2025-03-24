package androidx.media3.common.text;

import static androidx.media3.common.text.CustomSpanBundler.bundleCustomSpans;
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Binder;
import android.os.Bundle;
import android.text.Layout;
import android.text.Layout.Alignment;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import org.checkerframework.dataflow.qual.Pure;
/** 包含特定字幕提示（Cue）的信息，包括文本内容和格式化数据。 */
// 该类不应被继承。如果某个字幕格式需要额外的字段，这些字段应该足够通用以便添加到此，或者格式特定的解码器应通过辅助对象传递信息。
public final class Cue {

  /**
   * @deprecated 通常不需要一个空文本字符串的字幕提示。如果需要，请自行创建。
   */
  @Deprecated public static final Cue EMPTY = new Cue.Builder().setText("").build();

  /** 未设置的位置、宽度或大小。 */
  // 注意：我们故意不使用 Float.MIN_VALUE，因为它是正数且非常接近零。
  public static final float DIMEN_UNSET = -Float.MAX_VALUE;

  /**
   * 锚点类型，可能未设置。取值为 {@link #TYPE_UNSET}、{@link #ANCHOR_TYPE_START}、{@link #ANCHOR_TYPE_MIDDLE} 或 {@link #ANCHOR_TYPE_END} 之一。
   */
  // @Target 列表包括 'default' 目标和 TYPE_USE，以确保与添加 TYPE_USE 之前的 Kotlin 用法向后兼容。
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({TYPE_UNSET, ANCHOR_TYPE_START, ANCHOR_TYPE_MIDDLE, ANCHOR_TYPE_END})
  public @interface AnchorType {}

  /** 未设置的锚点、行、文本大小或垂直类型值。 */
  public static final int TYPE_UNSET = Integer.MIN_VALUE;

  /**
   * 将字幕框的左边缘（对于水平位置）或上边缘（对于垂直位置）锚定。
   */
  public static final int ANCHOR_TYPE_START = 0;

  /** 将字幕框的中间锚定。 */
  public static final int ANCHOR_TYPE_MIDDLE = 1;

  /**
   * 将字幕框的右边缘（对于水平位置）或下边缘（对于垂直位置）锚定。
   */
  public static final int ANCHOR_TYPE_END = 2;

  /**
   * 行的类型，可能未设置。取值为 {@link #TYPE_UNSET}、{@link #LINE_TYPE_FRACTION} 或 {@link #LINE_TYPE_NUMBER} 之一。
   */
  // @Target 列表包括 'default' 目标和 TYPE_USE，以确保与添加 TYPE_USE 之前的 Kotlin 用法向后兼容。
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({TYPE_UNSET, LINE_TYPE_FRACTION, LINE_TYPE_NUMBER})
  public @interface LineType {}

  /** 当 {@link #line} 为分数位置时，{@link #lineType} 的值。 */
  public static final int LINE_TYPE_FRACTION = 0;

  /** 当 {@link #line} 为行号时，{@link #lineType} 的值。 */
  public static final int LINE_TYPE_NUMBER = 1;

  /**
   * 此字幕提示的默认文本大小类型，可能未设置。取值为 {@link #TYPE_UNSET}、{@link #TEXT_SIZE_TYPE_FRACTIONAL}、{@link #TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING} 或 {@link #TEXT_SIZE_TYPE_ABSOLUTE} 之一。
   */
  // @Target 列表包括 'default' 目标和 TYPE_USE，以确保与添加 TYPE_USE 之前的 Kotlin 用法向后兼容。
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
      TYPE_UNSET,
      TEXT_SIZE_TYPE_FRACTIONAL,
      TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING,
      TEXT_SIZE_TYPE_ABSOLUTE
  })
  public @interface TextSizeType {}

  /** 文本大小以视口大小减去视口内边距的分数为单位。 */
  public static final int TEXT_SIZE_TYPE_FRACTIONAL = 0;

  /** 文本大小以视口大小的分数为单位，忽略视口内边距。 */
  public static final int TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING = 1;

  /** 文本大小以像素为单位。 */
  public static final int TEXT_SIZE_TYPE_ABSOLUTE = 2;

  /**
   * 此字幕提示的垂直布局类型，可能未设置（即水平）。取值为 {@link #TYPE_UNSET}、{@link #VERTICAL_TYPE_RL} 或 {@link #VERTICAL_TYPE_LR} 之一。
   */
  // @Target 列表包括 'default' 目标和 TYPE_USE，以确保与添加 TYPE_USE 之前的 Kotlin 用法向后兼容。
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
      TYPE_UNSET,
      VERTICAL_TYPE_RL,
      VERTICAL_TYPE_LR,
  })
  public @interface VerticalType {}
  /** 垂直从右到左（例如用于日语）。 */
  public static final int VERTICAL_TYPE_RL = 1;

  /** 垂直从左到右（例如用于蒙古语）。 */
  public static final int VERTICAL_TYPE_LR = 2;

  /**
   * 字幕文本，如果这是图像字幕则为 null。注意，{@link CharSequence} 可能被样式 Span 装饰。
   */
  @Nullable public final CharSequence text;

  /** 字幕文本在字幕框内的对齐方式，如果未定义对齐方式则为 null。 */
  @Nullable public final Alignment textAlignment;

  /**
   * 多行文本相对于最长行的对齐方式，如果未定义对齐方式则为 null。
   */
  @Nullable public final Alignment multiRowAlignment;

  /** 字幕图像，如果这是文本字幕则为 null。 */
  @Nullable public final Bitmap bitmap;

  /**
   * 字幕框在视口中与书写方向正交的方向上的位置（由 {@link #verticalType} 决定），或 {@link #DIMEN_UNSET}。当设置时，值的解释取决于 {@link #lineType} 的值。
   *
   * <p>测量方向取决于 {@link #verticalType}：
   *
   * <ul>
   *   <li>对于 {@link #TYPE_UNSET}（即水平），这是相对于视口顶部的垂直位置。
   *   <li>对于 {@link #VERTICAL_TYPE_LR}，这是相对于视口左侧的水平位置。
   *   <li>对于 {@link #VERTICAL_TYPE_RL}，这是相对于视口右侧的水平位置。
   * </ul>
   */
  public final float line;

  /**
   * {@link #line} 值的类型。
   *
   * <ul>
   *   <li>{@link #LINE_TYPE_FRACTION} 表示 {@link #line} 是视口中的分数位置（测量到由 {@link #lineAnchor} 决定的字幕框部分）。
   *   <li>{@link #LINE_TYPE_NUMBER} 表示 {@link #line} 是视口行号。视口被划分为若干行（每行大小等于字幕框的第一行）。字幕框的位置按如下方式与视口行对齐：
   *       <ul>
   *         <li>{@link #lineAnchor} 被忽略。
   *         <li>当 {@code line} 大于或等于 0 时，字幕框的第一行与视口行对齐，0 表示视口的第一行。
   *         <li>当 {@code line} 为负数时，字幕框的最后一行与视口行对齐，-1 表示视口的最后一行。
   *         <li>对于水平文本，视口的开始和结束分别是顶部和底部。
   *       </ul>
   * </ul>
   */
  public final @LineType int lineType;

  /**
   * 当 {@link #lineType} 为 {@link #LINE_TYPE_FRACTION} 时，由 {@link #line} 定位的字幕框锚点。
   *
   * <p>取值为：
   *
   * <ul>
   *   <li>{@link #ANCHOR_TYPE_START}
   *   <li>{@link #ANCHOR_TYPE_MIDDLE}
   *   <li>{@link #ANCHOR_TYPE_END}
   *   <li>{@link #TYPE_UNSET}
   * </ul>
   *
   * <p>对于正常的水平文本，{@link #ANCHOR_TYPE_START}、{@link #ANCHOR_TYPE_MIDDLE} 和 {@link #ANCHOR_TYPE_END} 分别对应于字幕框的顶部、中间和底部。
   */
  public final @AnchorType int lineAnchor;
  /**
   * 字幕框的 {@link #positionAnchor} 在视口中与 {@link #line} 正交方向上的分数位置，或 {@link #DIMEN_UNSET}。
   *
   * <p>测量方向取决于 {@link #verticalType}。
   *
   * <ul>
   *   <li>对于 {@link #TYPE_UNSET}（即水平），这是相对于视口左侧的水平位置。注意，即使对于从右到左的文本，定位也是相对于视口的左侧。
   *   <li>对于 {@link #VERTICAL_TYPE_LR} 和 {@link #VERTICAL_TYPE_RL}（即垂直），这是相对于视口顶部的垂直位置。
   * </ul>
   */
  public final float position;

  /**
   * 由 {@link #position} 定位的字幕框锚点。取值为 {@link #ANCHOR_TYPE_START}、{@link #ANCHOR_TYPE_MIDDLE}、{@link #ANCHOR_TYPE_END} 或 {@link #TYPE_UNSET} 之一。
   *
   * <p>对于正常的水平文本，{@link #ANCHOR_TYPE_START}、{@link #ANCHOR_TYPE_MIDDLE} 和 {@link #ANCHOR_TYPE_END} 分别对应于字幕框的左侧、中间和右侧。
   */
  public final @AnchorType int positionAnchor;

  /**
   * 字幕框在书写方向上的大小，以视口在该方向上的大小的分数表示，或 {@link #DIMEN_UNSET}。
   */
  public final float size;

  /**
   * 位图高度作为视口大小的分数，或 {@link #DIMEN_UNSET}，如果位图应根据其自然高度显示（给定位图尺寸和指定的 {@link #size}）。
   */
  public final float bitmapHeight;

  /** 指定 {@link #windowColor} 属性是否已设置。 */
  public final boolean windowColorSet;

  /** 窗口的填充颜色。 */
  public final int windowColor;

  /**
   * 此字幕文本的默认文本大小类型，或 {@link #TYPE_UNSET}，如果此字幕没有默认文本大小。
   */
  public final @TextSizeType int textSizeType;

  /**
   * 此字幕文本的默认文本大小，或 {@link #DIMEN_UNSET}，如果此字幕没有默认文本大小。
   */
  public final float textSize;

  /**
   * 此字幕的垂直格式，或 {@link #TYPE_UNSET}，如果字幕没有垂直设置（因此应为水平）。
   */
  public final @VerticalType int verticalType;

  /**
   * 应用于此字幕的剪切角度（以度为单位），以图形坐标表示。这将导致沿内联进度轴对块进行倾斜变换。
   */
  public final float shearDegrees;

  private Cue(
      @Nullable CharSequence text,
      @Nullable Alignment textAlignment,
      @Nullable Alignment multiRowAlignment,
      @Nullable Bitmap bitmap,
      float line,
      @LineType int lineType,
      @AnchorType int lineAnchor,
      float position,
      @AnchorType int positionAnchor,
      @TextSizeType int textSizeType,
      float textSize,
      float size,
      float bitmapHeight,
      boolean windowColorSet,
      int windowColor,
      @VerticalType int verticalType,
      float shearDegrees) {
    // Exactly one of text or bitmap should be set.
    if (text == null) {
      Assertions.checkNotNull(bitmap);
    } else {
      Assertions.checkArgument(bitmap == null);
    }
    if (text instanceof Spanned) {
      this.text = SpannedString.valueOf(text);
    } else if (text != null) {
      this.text = text.toString();
    } else {
      this.text = null;
    }
    this.textAlignment = textAlignment;
    this.multiRowAlignment = multiRowAlignment;
    this.bitmap = bitmap;
    this.line = line;
    this.lineType = lineType;
    this.lineAnchor = lineAnchor;
    this.position = position;
    this.positionAnchor = positionAnchor;
    this.size = size;
    this.bitmapHeight = bitmapHeight;
    this.windowColorSet = windowColorSet;
    this.windowColor = windowColor;
    this.textSizeType = textSizeType;
    this.textSize = textSize;
    this.verticalType = verticalType;
    this.shearDegrees = shearDegrees;
  }

  /** 返回一个新的 {@link Cue.Builder}，并使用此 Cue 的值进行初始化。 */
  @UnstableApi
  public Builder buildUpon() {
    return new Cue.Builder(this);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Cue that = (Cue) obj;
    return TextUtils.equals(text, that.text)
        && textAlignment == that.textAlignment
        && multiRowAlignment == that.multiRowAlignment
        && (bitmap == null
            ? that.bitmap == null
            : (that.bitmap != null && bitmap.sameAs(that.bitmap)))
        && line == that.line
        && lineType == that.lineType
        && lineAnchor == that.lineAnchor
        && position == that.position
        && positionAnchor == that.positionAnchor
        && size == that.size
        && bitmapHeight == that.bitmapHeight
        && windowColorSet == that.windowColorSet
        && windowColor == that.windowColor
        && textSizeType == that.textSizeType
        && textSize == that.textSize
        && verticalType == that.verticalType
        && shearDegrees == that.shearDegrees;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        text,
        textAlignment,
        multiRowAlignment,
        bitmap,
        line,
        lineType,
        lineAnchor,
        position,
        positionAnchor,
        size,
        bitmapHeight,
        windowColorSet,
        windowColor,
        textSizeType,
        textSize,
        verticalType,
        shearDegrees);
  }

  /** A builder for {@link Cue} objects. */
  @UnstableApi
  public static final class Builder {
    @Nullable private CharSequence text;
    @Nullable private Bitmap bitmap;
    @Nullable private Alignment textAlignment;
    @Nullable private Alignment multiRowAlignment;
    private float line;
    private @LineType int lineType;
    private @AnchorType int lineAnchor;
    private float position;
    private @AnchorType int positionAnchor;
    private @TextSizeType int textSizeType;
    private float textSize;
    private float size;
    private float bitmapHeight;
    private boolean windowColorSet;
    @ColorInt private int windowColor;
    private @VerticalType int verticalType;
    private float shearDegrees;

    public Builder() {
      text = null;
      bitmap = null;
      textAlignment = null;
      multiRowAlignment = null;
      line = DIMEN_UNSET;
      lineType = TYPE_UNSET;
      lineAnchor = TYPE_UNSET;
      position = DIMEN_UNSET;
      positionAnchor = TYPE_UNSET;
      textSizeType = TYPE_UNSET;
      textSize = DIMEN_UNSET;
      size = DIMEN_UNSET;
      bitmapHeight = DIMEN_UNSET;
      windowColorSet = false;
      windowColor = Color.BLACK;
      verticalType = TYPE_UNSET;
    }

    private Builder(Cue cue) {
      text = cue.text;
      bitmap = cue.bitmap;
      textAlignment = cue.textAlignment;
      multiRowAlignment = cue.multiRowAlignment;
      line = cue.line;
      lineType = cue.lineType;
      lineAnchor = cue.lineAnchor;
      position = cue.position;
      positionAnchor = cue.positionAnchor;
      textSizeType = cue.textSizeType;
      textSize = cue.textSize;
      size = cue.size;
      bitmapHeight = cue.bitmapHeight;
      windowColorSet = cue.windowColorSet;
      windowColor = cue.windowColor;
      verticalType = cue.verticalType;
      shearDegrees = cue.shearDegrees;
    }
    /**
     * 设置字幕文本。
     *
     * <p>注意，{@code text} 可能被样式 Span 装饰。
     *
     * @see Cue#text
     */
    @CanIgnoreReturnValue
    public Builder setText(CharSequence text) {
      this.text = text;
      return this;
    }

    /**
     * 获取字幕文本。
     *
     * @see Cue#text
     */
    @Pure
    @Nullable
    public CharSequence getText() {
      return text;
    }

    /**
     * 设置字幕图像。
     *
     * @see Cue#bitmap
     */
    @CanIgnoreReturnValue
    public Builder setBitmap(Bitmap bitmap) {
      this.bitmap = bitmap;
      return this;
    }

    /**
     * 获取字幕图像。
     *
     * @see Cue#bitmap
     */
    @Pure
    @Nullable
    public Bitmap getBitmap() {
      return bitmap;
    }

    /**
     * 设置字幕文本在字幕框内的对齐方式。
     *
     * <p>传递 null 表示对齐方式未定义。
     *
     * @see Cue#textAlignment
     */
    @CanIgnoreReturnValue
    public Builder setTextAlignment(@Nullable Layout.Alignment textAlignment) {
      this.textAlignment = textAlignment;
      return this;
    }

    /**
     * 获取字幕文本在字幕框内的对齐方式，如果未定义对齐方式则返回 null。
     *
     * @see Cue#textAlignment
     */
    @Pure
    @Nullable
    public Alignment getTextAlignment() {
      return textAlignment;
    }

    /**
     * 设置字幕的多行对齐方式。
     *
     * <p>传递 null 表示对齐方式未定义。
     *
     * @see Cue#multiRowAlignment
     */
    @CanIgnoreReturnValue
    public Builder setMultiRowAlignment(@Nullable Layout.Alignment multiRowAlignment) {
      this.multiRowAlignment = multiRowAlignment;
      return this;
    }

    /**
     * 设置字幕框在视口中与书写方向正交的方向上的位置。
     *
     * @see Cue#line
     * @see Cue#lineType
     */
    @CanIgnoreReturnValue
    public Builder setLine(float line, @LineType int lineType) {
      this.line = line;
      this.lineType = lineType;
      return this;
    }

    /**
     * 获取字幕框的 {@code lineAnchor} 在视口中与书写方向正交的方向上的位置。
     *
     * @see Cue#line
     */
    @Pure
    public float getLine() {
      return line;
    }

    /**
     * 获取 {@link #getLine()} 值的类型。
     *
     * @see Cue#lineType
     */
    @Pure
    public @LineType int getLineType() {
      return lineType;
    }

    /**
     * 设置由 {@link #setLine(float, int) line} 定位的字幕框锚点。
     *
     * @see Cue#lineAnchor
     */
    @CanIgnoreReturnValue
    public Builder setLineAnchor(@AnchorType int lineAnchor) {
      this.lineAnchor = lineAnchor;
      return this;
    }

    /**
     * 获取由 {@link #setLine(float, int) line} 定位的字幕框锚点。
     *
     * @see Cue#lineAnchor
     */
    @Pure
    public @AnchorType int getLineAnchor() {
      return lineAnchor;
    }

    /**
     * 设置字幕框的 {@link #setPositionAnchor(int) positionAnchor} 在视口中与 {@link #setLine(float, int) line} 正交方向上的分数位置。
     *
     * @see Cue#position
     */
    @CanIgnoreReturnValue
    public Builder setPosition(float position) {
      this.position = position;
      return this;
    }

    /**
     * 获取字幕框的 {@link #setPositionAnchor(int) positionAnchor} 在视口中与 {@link #setLine(float, int) line} 正交方向上的分数位置。
     *
     * @see Cue#position
     */
    @Pure
    public float getPosition() {
      return position;
    }

    /**
     * 设置由 {@link #setPosition(float) position} 定位的字幕框锚点。
     *
     * @see Cue#positionAnchor
     */
    @CanIgnoreReturnValue
    public Builder setPositionAnchor(@AnchorType int positionAnchor) {
      this.positionAnchor = positionAnchor;
      return this;
    }

    /**
     * 获取由 {@link #setPosition(float) position} 定位的字幕框锚点。
     *
     * @see Cue#positionAnchor
     */
    @Pure
    public @AnchorType int getPositionAnchor() {
      return positionAnchor;
    }

    /**
     * 设置此字幕文本的默认文本大小和类型。
     *
     * @see Cue#textSize
     * @see Cue#textSizeType
     */
    @CanIgnoreReturnValue
    public Builder setTextSize(float textSize, @TextSizeType int textSizeType) {
      this.textSize = textSize;
      this.textSizeType = textSizeType;
      return this;
    }

    /**
     * 获取此字幕文本的默认文本大小类型。
     *
     * @see Cue#textSizeType
     */
    @Pure
    public @TextSizeType int getTextSizeType() {
      return textSizeType;
    }

    /**
     * 获取此字幕文本的默认文本大小。
     *
     * @see Cue#textSize
     */
    @Pure
    public float getTextSize() {
      return textSize;
    }
    /**
     * 设置字幕框在书写方向上的大小，以视口在该方向上的大小的分数表示。
     *
     * @see Cue#size
     */
    @CanIgnoreReturnValue
    public Builder setSize(float size) {
      this.size = size;
      return this;
    }

    /**
     * 获取字幕框在书写方向上的大小，以视口在该方向上的大小的分数表示。
     *
     * @see Cue#size
     */
    @Pure
    public float getSize() {
      return size;
    }

    /**
     * 设置位图高度作为视口大小的分数。
     *
     * @see Cue#bitmapHeight
     */
    @CanIgnoreReturnValue
    public Builder setBitmapHeight(float bitmapHeight) {
      this.bitmapHeight = bitmapHeight;
      return this;
    }

    /**
     * 获取位图高度作为视口大小的分数。
     *
     * @see Cue#bitmapHeight
     */
    @Pure
    public float getBitmapHeight() {
      return bitmapHeight;
    }

    /**
     * 设置窗口的填充颜色。
     *
     * <p>同时将 {@link Cue#windowColorSet} 设置为 true。
     *
     * @see Cue#windowColor
     * @see Cue#windowColorSet
     */
    @CanIgnoreReturnValue
    public Builder setWindowColor(@ColorInt int windowColor) {
      this.windowColor = windowColor;
      this.windowColorSet = true;
      return this;
    }

    /** 将 {@link Cue#windowColorSet} 设置为 false。 */
    @CanIgnoreReturnValue
    public Builder clearWindowColor() {
      this.windowColorSet = false;
      return this;
    }

    /**
     * 返回窗口的填充颜色是否已设置。
     *
     * @see Cue#windowColorSet
     */
    public boolean isWindowColorSet() {
      return windowColorSet;
    }

    /**
     * 获取窗口的填充颜色。
     *
     * @see Cue#windowColor
     */
    @Pure
    @ColorInt
    public int getWindowColor() {
      return windowColor;
    }

    /**
     * 设置此字幕的垂直格式。
     *
     * @see Cue#verticalType
     */
    @CanIgnoreReturnValue
    public Builder setVerticalType(@VerticalType int verticalType) {
      this.verticalType = verticalType;
      return this;
    }

    /** 设置此字幕的剪切角度。 */
    @CanIgnoreReturnValue
    public Builder setShearDegrees(float shearDegrees) {
      this.shearDegrees = shearDegrees;
      return this;
    }

    /**
     * 获取此字幕的垂直格式。
     *
     * @see Cue#verticalType
     */
    @Pure
    public @VerticalType int getVerticalType() {
      return verticalType;
    }

    /** Build the cue. */
    public Cue build() {
      return new Cue(
          text,
          textAlignment,
          multiRowAlignment,
          bitmap,
          line,
          lineType,
          lineAnchor,
          position,
          positionAnchor,
          textSizeType,
          textSize,
          size,
          bitmapHeight,
          windowColorSet,
          windowColor,
          verticalType,
          shearDegrees);
    }
  }

  private static final String FIELD_TEXT = Util.intToStringMaxRadix(0);
  private static final String FIELD_CUSTOM_SPANS = Util.intToStringMaxRadix(17);
  private static final String FIELD_TEXT_ALIGNMENT = Util.intToStringMaxRadix(1);
  private static final String FIELD_MULTI_ROW_ALIGNMENT = Util.intToStringMaxRadix(2);
  private static final String FIELD_BITMAP_PARCELABLE = Util.intToStringMaxRadix(3);
  private static final String FIELD_BITMAP_BYTES = Util.intToStringMaxRadix(18);
  private static final String FIELD_LINE = Util.intToStringMaxRadix(4);
  private static final String FIELD_LINE_TYPE = Util.intToStringMaxRadix(5);
  private static final String FIELD_LINE_ANCHOR = Util.intToStringMaxRadix(6);
  private static final String FIELD_POSITION = Util.intToStringMaxRadix(7);
  private static final String FIELD_POSITION_ANCHOR = Util.intToStringMaxRadix(8);
  private static final String FIELD_TEXT_SIZE_TYPE = Util.intToStringMaxRadix(9);
  private static final String FIELD_TEXT_SIZE = Util.intToStringMaxRadix(10);
  private static final String FIELD_SIZE = Util.intToStringMaxRadix(11);
  private static final String FIELD_BITMAP_HEIGHT = Util.intToStringMaxRadix(12);
  private static final String FIELD_WINDOW_COLOR = Util.intToStringMaxRadix(13);
  private static final String FIELD_WINDOW_COLOR_SET = Util.intToStringMaxRadix(14);
  private static final String FIELD_VERTICAL_TYPE = Util.intToStringMaxRadix(15);
  private static final String FIELD_SHEAR_DEGREES = Util.intToStringMaxRadix(16);
  /**
   * 返回一个可以序列化为字节的 {@link Bundle}。
   *
   * <p>如果结果不需要序列化，请优先使用更高效的 {@link #toBinderBasedBundle()}。
   *
   * <p>从此方法返回的 {@link Bundle} 不得传递给可能使用不同版本 media3 库的其他进程。
   */
  @UnstableApi
  public Bundle toSerializableBundle() {
    Bundle bundle = toBundleWithoutBitmap();
    if (bitmap != null) {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      // PNG 格式是无损的，质量参数被忽略。
      checkState(bitmap.compress(Bitmap.CompressFormat.PNG, /* quality= */ 0, output));
      bundle.putByteArray(FIELD_BITMAP_BYTES, output.toByteArray());
    }
    return bundle;
  }

  /**
   * 返回一个可能包含 {@link Binder} 引用的 {@link Bundle}，这意味着它不能安全地序列化为字节。
   *
   * <p>从此方法返回的 {@link Bundle} 可以安全地在进程之间传递，并被旧版本的 media3 库解析。
   *
   * <p>如果需要获取可以安全序列化的 {@link Bundle}，请使用 {@link #toSerializableBundle()}。
   */
  @UnstableApi
  public Bundle toBinderBasedBundle() {
    Bundle bundle = toBundleWithoutBitmap();
    if (bitmap != null) {
      bundle.putParcelable(FIELD_BITMAP_PARCELABLE, bitmap);
    }
    return bundle;
  }

  /**
   * @deprecated 请改用 {@link #toSerializableBundle()} 或 {@link #toBinderBasedBundle()}。
   */
  @UnstableApi
  @Deprecated
  public Bundle toBundle() {
    return toBinderBasedBundle();
  }

  private Bundle toBundleWithoutBitmap() {
    Bundle bundle = new Bundle();
    if (text != null) {
      bundle.putCharSequence(FIELD_TEXT, text);
      if (text instanceof Spanned) {
        ArrayList<Bundle> customSpanBundles = bundleCustomSpans((Spanned) text);
        if (!customSpanBundles.isEmpty()) {
          bundle.putParcelableArrayList(FIELD_CUSTOM_SPANS, customSpanBundles);
        }
      }
    }
    bundle.putSerializable(FIELD_TEXT_ALIGNMENT, textAlignment);
    bundle.putSerializable(FIELD_MULTI_ROW_ALIGNMENT, multiRowAlignment);
    bundle.putFloat(FIELD_LINE, line);
    bundle.putInt(FIELD_LINE_TYPE, lineType);
    bundle.putInt(FIELD_LINE_ANCHOR, lineAnchor);
    bundle.putFloat(FIELD_POSITION, position);
    bundle.putInt(FIELD_POSITION_ANCHOR, positionAnchor);
    bundle.putInt(FIELD_TEXT_SIZE_TYPE, textSizeType);
    bundle.putFloat(FIELD_TEXT_SIZE, textSize);
    bundle.putFloat(FIELD_SIZE, size);
    bundle.putFloat(FIELD_BITMAP_HEIGHT, bitmapHeight);
    bundle.putBoolean(FIELD_WINDOW_COLOR_SET, windowColorSet);
    bundle.putInt(FIELD_WINDOW_COLOR, windowColor);
    bundle.putInt(FIELD_VERTICAL_TYPE, verticalType);
    bundle.putFloat(FIELD_SHEAR_DEGREES, shearDegrees);
    return bundle;
  }

  /** Restores a cue from a {@link Bundle}. */
  @UnstableApi
  public static Cue fromBundle(Bundle bundle) {
    Builder builder = new Builder();
    @Nullable CharSequence text = bundle.getCharSequence(FIELD_TEXT);
    if (text != null) {
      builder.setText(text);
      @Nullable
      ArrayList<Bundle> customSpanBundles = bundle.getParcelableArrayList(FIELD_CUSTOM_SPANS);
      if (customSpanBundles != null) {
        SpannableString textWithCustomSpans = SpannableString.valueOf(text);
        for (Bundle customSpanBundle : customSpanBundles) {
          CustomSpanBundler.unbundleAndApplyCustomSpan(customSpanBundle, textWithCustomSpans);
        }
        builder.setText(textWithCustomSpans);
      }
    }
    @Nullable Alignment textAlignment = (Alignment) bundle.getSerializable(FIELD_TEXT_ALIGNMENT);
    if (textAlignment != null) {
      builder.setTextAlignment(textAlignment);
    }
    @Nullable
    Alignment multiRowAlignment = (Alignment) bundle.getSerializable(FIELD_MULTI_ROW_ALIGNMENT);
    if (multiRowAlignment != null) {
      builder.setMultiRowAlignment(multiRowAlignment);
    }
    @Nullable Bitmap bitmap = bundle.getParcelable(FIELD_BITMAP_PARCELABLE);
    if (bitmap != null) {
      builder.setBitmap(bitmap);
    } else {
      @Nullable byte[] bitmapBytes = bundle.getByteArray(FIELD_BITMAP_BYTES);
      if (bitmapBytes != null) {
        builder.setBitmap(
            BitmapFactory.decodeByteArray(bitmapBytes, /* offset= */ 0, bitmapBytes.length));
      }
    }
    if (bundle.containsKey(FIELD_LINE) && bundle.containsKey(FIELD_LINE_TYPE)) {
      builder.setLine(bundle.getFloat(FIELD_LINE), bundle.getInt(FIELD_LINE_TYPE));
    }
    if (bundle.containsKey(FIELD_LINE_ANCHOR)) {
      builder.setLineAnchor(bundle.getInt(FIELD_LINE_ANCHOR));
    }
    if (bundle.containsKey(FIELD_POSITION)) {
      builder.setPosition(bundle.getFloat(FIELD_POSITION));
    }
    if (bundle.containsKey(FIELD_POSITION_ANCHOR)) {
      builder.setPositionAnchor(bundle.getInt(FIELD_POSITION_ANCHOR));
    }
    if (bundle.containsKey(FIELD_TEXT_SIZE) && bundle.containsKey(FIELD_TEXT_SIZE_TYPE)) {
      builder.setTextSize(bundle.getFloat(FIELD_TEXT_SIZE), bundle.getInt(FIELD_TEXT_SIZE_TYPE));
    }
    if (bundle.containsKey(FIELD_SIZE)) {
      builder.setSize(bundle.getFloat(FIELD_SIZE));
    }
    if (bundle.containsKey(FIELD_BITMAP_HEIGHT)) {
      builder.setBitmapHeight(bundle.getFloat(FIELD_BITMAP_HEIGHT));
    }
    if (bundle.containsKey(FIELD_WINDOW_COLOR)) {
      builder.setWindowColor(bundle.getInt(FIELD_WINDOW_COLOR));
    }
    if (!bundle.getBoolean(FIELD_WINDOW_COLOR_SET, /* defaultValue= */ false)) {
      builder.clearWindowColor();
    }
    if (bundle.containsKey(FIELD_VERTICAL_TYPE)) {
      builder.setVerticalType(bundle.getInt(FIELD_VERTICAL_TYPE));
    }
    if (bundle.containsKey(FIELD_SHEAR_DEGREES)) {
      builder.setShearDegrees(bundle.getFloat(FIELD_SHEAR_DEGREES));
    }
    return builder.build();
  }
}
