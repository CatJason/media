package androidx.media3.exoplayer.video;

import static androidx.media3.common.util.EGLSurfaceTexture.SECURE_MODE_NONE;
import static androidx.media3.common.util.EGLSurfaceTexture.SECURE_MODE_PROTECTED_PBUFFER;
import static androidx.media3.common.util.EGLSurfaceTexture.SECURE_MODE_SURFACELESS_CONTEXT;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.EGLSurfaceTexture;
import androidx.media3.common.util.EGLSurfaceTexture.SecureMode;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import com.google.errorprone.annotations.InlineMe;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** 一个占位符 {@link Surface}。 */
@UnstableApi
public final class PlaceholderSurface extends Surface {

  private static final String TAG = "PlaceholderSurface";

  /** 该 Surface 是否是安全的。 */
  public final boolean secure;

  private static @SecureMode int secureMode;
  private static boolean secureModeInitialized;

  private final PlaceholderSurfaceThread thread;
  private boolean threadReleased;

  /**
   * 返回设备是否支持安全的占位符 Surface。
   *
   * @param context 任意 {@link Context}。
   * @return 设备是否支持安全的占位符 Surface。
   */
  public static synchronized boolean isSecureSupported(Context context) {
    if (!secureModeInitialized) {
      secureMode = getSecureMode(context);
      secureModeInitialized = true;
    }
    return secureMode != SECURE_MODE_NONE;
  }

  /**
   * @deprecated 请使用 {@link #newInstance(Context, boolean)} 代替。
   */
  @InlineMe(
      replacement = "PlaceholderSurface.newInstance(context, secure)",
      imports = "androidx.media3.exoplayer.video.PlaceholderSurface")
  @Deprecated
  public static PlaceholderSurface newInstanceV17(Context context, boolean secure) {
    return newInstance(context, secure);
  }

  /**
   * 返回一个新创建的占位符 Surface。当不再需要时，必须通过调用 {@link #release} 释放该 Surface。
   *
   * @param context 任意 {@link Context}。
   * @param secure 是否需要安全的 Surface。仅在 {@link #isSecureSupported(Context)} 返回 {@code true} 时才能请求。
   * @throws IllegalStateException 如果在 {@link #isSecureSupported(Context)} 返回 {@code false} 的设备上请求了安全的 Surface。
   */
  public static PlaceholderSurface newInstance(Context context, boolean secure) {
    Assertions.checkState(!secure || isSecureSupported(context));
    PlaceholderSurfaceThread thread = new PlaceholderSurfaceThread();
    return thread.init(secure ? secureMode : SECURE_MODE_NONE);
  }

  private PlaceholderSurface(
      PlaceholderSurfaceThread thread, SurfaceTexture surfaceTexture, boolean secure) {
    super(surfaceTexture);
    this.thread = thread;
    this.secure = secure;
  }

  @Override
  public void release() {
    super.release();
    // Surface 可能会被多次释放（显式释放和通过 Surface.finalize() 释放）。
    // super.release() 的实现有它自己的去重逻辑。下面我们需要自己处理去重。
    // 由于我们无法控制 Surface.finalize() 的调用线程，因此需要同步。
    synchronized (thread) {
      if (!threadReleased) {
        thread.release();
        threadReleased = true;
      }
    }
  }

  private static @SecureMode int getSecureMode(Context context) {
    if (GlUtil.isProtectedContentExtensionSupported(context)) {
      if (GlUtil.isSurfacelessContextExtensionSupported()) {
        return SECURE_MODE_SURFACELESS_CONTEXT;
      } else {
        // 如果无法使用无表面上下文，我们使用一个受保护的 1 * 1 像素缓冲区 Surface。
        // 这可能需要支持 EXT_protected_surface，但在实践中，它在一些没有该扩展的设备上也能工作。
        // 另请参阅 https://github.com/google/ExoPlayer/issues/3558。
        return SECURE_MODE_PROTECTED_PBUFFER;
      }
    } else {
      return SECURE_MODE_NONE;
    }
  }

  private static class PlaceholderSurfaceThread extends HandlerThread implements Handler.Callback {

    private static final int MSG_INIT = 1;
    private static final int MSG_RELEASE = 2;

    private @MonotonicNonNull EGLSurfaceTexture eglSurfaceTexture;
    private @MonotonicNonNull Handler handler;
    @Nullable private Error initError;
    @Nullable private RuntimeException initException;
    @Nullable private PlaceholderSurface surface;

    public PlaceholderSurfaceThread() {
      super("ExoPlayer:PlaceholderSurface");
    }

    public PlaceholderSurface init(@SecureMode int secureMode) {
      start();
      handler = new Handler(getLooper(), /* callback= */ this);
      eglSurfaceTexture = new EGLSurfaceTexture(handler);
      boolean wasInterrupted = false;
      synchronized (this) {
        handler.obtainMessage(MSG_INIT, secureMode, 0).sendToTarget();
        while (surface == null && initException == null && initError == null) {
          try {
            wait();
          } catch (InterruptedException e) {
            wasInterrupted = true;
          }
        }
      }
      if (wasInterrupted) {
        // 恢复中断状态。
        Thread.currentThread().interrupt();
      }
      if (initException != null) {
        throw initException;
      } else if (initError != null) {
        throw initError;
      } else {
        return Assertions.checkNotNull(surface);
      }
    }

    public void release() {
      Assertions.checkNotNull(handler);
      handler.sendEmptyMessage(MSG_RELEASE);
    }

    @Override
    public boolean handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_INIT:
          try {
            initInternal(/* secureMode= */ msg.arg1);
          } catch (RuntimeException e) {
            Log.e(TAG, "Failed to initialize placeholder surface", e);
            initException = e;
          } catch (GlUtil.GlException e) {
            Log.e(TAG, "Failed to initialize placeholder surface", e);
            initException = new IllegalStateException(e);
          } catch (Error e) {
            Log.e(TAG, "Failed to initialize placeholder surface", e);
            initError = e;
          } finally {
            synchronized (this) {
              notify();
            }
          }
          return true;
        case MSG_RELEASE:
          try {
            releaseInternal();
          } catch (Throwable e) {
            Log.e(TAG, "Failed to release placeholder surface", e);
          } finally {
            quit();
          }
          return true;
        default:
          return true;
      }
    }

    private void initInternal(@SecureMode int secureMode) throws GlUtil.GlException {
      Assertions.checkNotNull(eglSurfaceTexture);
      eglSurfaceTexture.init(secureMode);
      this.surface =
          new PlaceholderSurface(
              this, eglSurfaceTexture.getSurfaceTexture(), secureMode != SECURE_MODE_NONE);
    }

    private void releaseInternal() {
      Assertions.checkNotNull(eglSurfaceTexture);
      eglSurfaceTexture.release();
    }
  }
}