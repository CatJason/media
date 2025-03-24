package androidx.media3.common;

import android.view.SurfaceView;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

/**
 * 用于在导出过程中显示诊断信息的视图提供者，用于调试。
 *
 * <p>这不适用于生产环境。
 */
@UnstableApi
public interface DebugViewProvider {

  /** 不显示任何调试信息的调试视图提供者。 */
  DebugViewProvider NONE = (int width, int height) -> null;

  /**
   * 返回一个新的 SurfaceView，用于显示具有指定宽度/高度（以像素为单位）的 Transformer 输出预览，如果不应显示调试信息，则返回 {@code null}。
   *
   * <p>此方法可能会在任意线程上调用。
   */
  @Nullable
  SurfaceView getDebugPreviewSurfaceView(int width, int height);
}