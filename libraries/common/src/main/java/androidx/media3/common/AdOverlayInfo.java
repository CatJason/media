package androidx.media3.common;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.view.View;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 提供有关在广告视图组顶部显示的覆盖视图的信息。 */
public final class AdOverlayInfo {

  /**
   * 覆盖视图的用途。取值为 {@link #PURPOSE_CONTROLS}、{@link #PURPOSE_CLOSE_AD}、{@link #PURPOSE_OTHER} 或 {@link #PURPOSE_NOT_VISIBLE}。
   */
  // @Target 列表包括 'default' 目标和 TYPE_USE，以确保与添加 TYPE_USE 之前的 Kotlin 用法兼容。
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({PURPOSE_CONTROLS, PURPOSE_CLOSE_AD, PURPOSE_OTHER, PURPOSE_NOT_VISIBLE})
  public @interface Purpose {}

  /** 用于播放控制覆盖视图的用途。 */
  public static final int PURPOSE_CONTROLS = 1;

  /** 用于广告关闭按钮覆盖视图的用途。 */
  public static final int PURPOSE_CLOSE_AD = 2;

  /** 用于其他覆盖视图的用途。 */
  public static final int PURPOSE_OTHER = 3;

  /** 用于不可见覆盖视图的用途。 */
  public static final int PURPOSE_NOT_VISIBLE = 4;

  /** {@link AdOverlayInfo} 实例的构建器。 */
  public static final class Builder {

    private final View view;
    private final @Purpose int purpose;

    @Nullable private String detailedReason;

    /**
     * 创建一个新的构建器。
     *
     * @param view 覆盖在播放器上的视图。
     * @param purpose 视图的用途。
     */
    public Builder(View view, @Purpose int purpose) {
      this.view = view;
      this.purpose = purpose;
    }

    /**
     * 设置视图位于播放器顶部的可选详细原因。
     *
     * @return 此构建器，方便链式调用。
     */
    @CanIgnoreReturnValue
    public Builder setDetailedReason(@Nullable String detailedReason) {
      this.detailedReason = detailedReason;
      return this;
    }

    /** 返回具有当前构建器值的新 {@link AdOverlayInfo} 实例。 */
    // 在构造函数仍然存在时使用已弃用的构造函数。
    @SuppressWarnings("deprecation")
    public AdOverlayInfo build() {
      return new AdOverlayInfo(view, purpose, detailedReason);
    }
  }

  /** 覆盖视图。 */
  public final View view;

  /** 覆盖视图的用途。 */
  public final @Purpose int purpose;

  /** 覆盖视图所需的可选详细原因。 */
  @Nullable public final String reasonDetail;

  /**
   * @deprecated 请使用 {@link Builder} 代替。
   */
  @UnstableApi
  @SuppressWarnings("deprecation") // 故意使用已弃用的构造函数
  @Deprecated
  public AdOverlayInfo(View view, @Purpose int purpose) {
    this(view, purpose, /* detailedReason= */ null);
  }

  /**
   * @deprecated 请使用 {@link Builder} 代替。
   */
  @UnstableApi
  @Deprecated
  public AdOverlayInfo(View view, @Purpose int purpose, @Nullable String detailedReason) {
    this.view = view;
    this.purpose = purpose;
    this.reasonDetail = detailedReason;
  }
}