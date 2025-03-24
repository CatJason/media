package androidx.media3.common;

import android.view.SurfaceView;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

/**
 * Provider for views to show diagnostic information during an export, for debugging.
 *
 * <p>This is not intended for production use-cases.
 */
@UnstableApi
public interface DebugViewProvider {

  /** Debug view provider that doesn't show any debug info. */
  DebugViewProvider NONE = (int width, int height) -> null;

  /**
   * Returns a new surface view to show a preview of transformer output with the given width/height
   * in pixels, or {@code null} if no debug information should be shown.
   *
   * <p>This method may be called on an arbitrary thread.
   */
  @Nullable
  SurfaceView getDebugPreviewSurfaceView(int width, int height);
}
