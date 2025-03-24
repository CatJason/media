package androidx.media3.common.util;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 使用 EGL/GLES 函数生成 {@link SurfaceTexture}。 */
@UnstableApi
public final class EGLSurfaceTexture implements SurfaceTexture.OnFrameAvailableListener, Runnable {

  /** 当 {@link SurfaceTexture} 上的纹理图像更新时调用的监听器。 */
  public interface TextureImageListener {
    /** 当 {@link SurfaceTexture} 从图像生产者接收到新帧时调用。 */
    void onFrameAvailable();
  }

  /**
   * EGL 表面和上下文使用的安全模式。可以是 {@link #SECURE_MODE_NONE}、{@link
   * #SECURE_MODE_SURFACELESS_CONTEXT} 或 {@link #SECURE_MODE_PROTECTED_PBUFFER} 之一。
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({SECURE_MODE_NONE, SECURE_MODE_SURFACELESS_CONTEXT, SECURE_MODE_PROTECTED_PBUFFER})
  public @interface SecureMode {}

  /** 不需要安全的 EGL 表面和上下文。 */
  public static final int SECURE_MODE_NONE = 0;

  /** 创建一个无表面的安全 EGL 上下文。 */
  public static final int SECURE_MODE_SURFACELESS_CONTEXT = 1;

  /** 创建一个由像素缓冲区支持的安全表面。 */
  public static final int SECURE_MODE_PROTECTED_PBUFFER = 2;

  private static final int EGL_SURFACE_WIDTH = 1;
  private static final int EGL_SURFACE_HEIGHT = 1;

  private static final int[] EGL_CONFIG_ATTRIBUTES =
      new int[] {
          EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
          EGL14.EGL_RED_SIZE, 8,
          EGL14.EGL_GREEN_SIZE, 8,
          EGL14.EGL_BLUE_SIZE, 8,
          EGL14.EGL_ALPHA_SIZE, 8,
          EGL14.EGL_DEPTH_SIZE, 0,
          EGL14.EGL_CONFIG_CAVEAT, EGL14.EGL_NONE,
          EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
          EGL14.EGL_NONE
      };

  private static final int EGL_PROTECTED_CONTENT_EXT = 0x32C0;

  private final Handler handler;
  private final int[] textureIdHolder;
  @Nullable private final TextureImageListener callback;

  @Nullable private EGLDisplay display;
  @Nullable private EGLContext context;
  @Nullable private EGLSurface surface;
  @Nullable private SurfaceTexture texture;

  /**
   * @param handler 用于调用 {@link SurfaceTexture#updateTexImage()} 更新 {@link SurfaceTexture} 上图像的 {@link Handler}。
   *     注意，{@link #init(int)} 必须在与 {@link Handler} 的 Looper 相同的线程上调用。
   */
  public EGLSurfaceTexture(Handler handler) {
    this(handler, /* callback= */ null);
  }

  /**
   * @param handler 用于调用 {@link SurfaceTexture#updateTexImage()} 更新 {@link SurfaceTexture} 上图像的 {@link Handler}。
   *     注意，{@link #init(int)} 必须在与 {@link Handler} 的 Looper 相同的线程上调用。
   * @param callback 当 {@link SurfaceTexture} 上的纹理图像更新时调用的 {@link TextureImageListener}。
   *     此回调将在与 {@code handler} 相同的线程上调用。
   */
  public EGLSurfaceTexture(Handler handler, @Nullable TextureImageListener callback) {
    this.handler = handler;
    this.callback = callback;
    textureIdHolder = new int[1];
  }

  /**
   * 初始化所需的 EGL 参数并创建 {@link SurfaceTexture}。
   *
   * @param secureMode 用于 EGL 表面的 {@link SecureMode}。
   */
  public void init(@SecureMode int secureMode) throws GlUtil.GlException {
    display = getDefaultDisplay();
    EGLConfig config = chooseEGLConfig(display);
    context = createEGLContext(display, config, secureMode);
    surface = createEGLSurface(display, config, context, secureMode);
    generateTextureIds(textureIdHolder);
    texture = new SurfaceTexture(textureIdHolder[0]);
    texture.setOnFrameAvailableListener(this);
  }

  /** 释放所有分配的资源。 */
  @SuppressWarnings("nullness:argument")
  public void release() {
    handler.removeCallbacks(this);
    try {
      if (texture != null) {
        texture.release();
        GLES20.glDeleteTextures(1, textureIdHolder, 0);
      }
    } finally {
      if (display != null && !display.equals(EGL14.EGL_NO_DISPLAY)) {
        EGL14.eglMakeCurrent(
            display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
      }
      if (surface != null && !surface.equals(EGL14.EGL_NO_SURFACE)) {
        EGL14.eglDestroySurface(display, surface);
      }
      if (context != null) {
        EGL14.eglDestroyContext(display, context);
      }
      EGL14.eglReleaseThread();
      if (display != null && !display.equals(EGL14.EGL_NO_DISPLAY)) {
        // Android 的特殊之处在于它使用引用计数的 EGLDisplay。因此，对于每次 eglInitialize()，我们都需要调用 eglTerminate()。
        EGL14.eglTerminate(display);
      }
      display = null;
      context = null;
      surface = null;
      texture = null;
    }
  }

  /**
   * 返回包装的 {@link SurfaceTexture}。只能在 {@link #init(int)} 之后调用。
   */
  public SurfaceTexture getSurfaceTexture() {
    return Assertions.checkNotNull(texture);
  }

  // SurfaceTexture.OnFrameAvailableListener

  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    handler.post(this);
  }

  // Runnable

  @Override
  public void run() {
    // 当有新的图像帧可用时，在提供的 handler 线程上运行。
    dispatchOnFrameAvailable();
    if (texture != null) {
      try {
        texture.updateTexImage();
      } catch (RuntimeException e) {
        // 忽略
      }
    }
  }

  private void dispatchOnFrameAvailable() {
    if (callback != null) {
      callback.onFrameAvailable();
    }
  }

  private static EGLDisplay getDefaultDisplay() throws GlUtil.GlException {
    EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    GlUtil.checkGlException(display != null, "eglGetDisplay 失败");

    int[] version = new int[2];
    boolean eglInitialized =
        EGL14.eglInitialize(display, version, /* majorOffset= */ 0, version, /* minorOffset= */ 1);
    GlUtil.checkGlException(eglInitialized, "eglInitialize 失败");
    return display;
  }

  private static EGLConfig chooseEGLConfig(EGLDisplay display) throws GlUtil.GlException {
    EGLConfig[] configs = new EGLConfig[1];
    int[] numConfigs = new int[1];
    boolean success =
        EGL14.eglChooseConfig(
            display,
            EGL_CONFIG_ATTRIBUTES,
            /* attrib_listOffset= */ 0,
            configs,
            /* configsOffset= */ 0,
            /* config_size= */ 1,
            numConfigs,
            /* num_configOffset= */ 0);
    GlUtil.checkGlException(
        success && numConfigs[0] > 0 && configs[0] != null,
        Util.formatInvariant(
            /* format= */ "eglChooseConfig 失败: success=%b, numConfigs[0]=%d, configs[0]=%s",
            success, numConfigs[0], configs[0]));

    return configs[0];
  }

  private static EGLContext createEGLContext(
      EGLDisplay display, EGLConfig config, @SecureMode int secureMode) throws GlUtil.GlException {
    int[] glAttributes;
    if (secureMode == SECURE_MODE_NONE) {
      glAttributes = new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
    } else {
      glAttributes =
          new int[] {
              EGL14.EGL_CONTEXT_CLIENT_VERSION,
              2,
              EGL_PROTECTED_CONTENT_EXT,
              EGL14.EGL_TRUE,
              EGL14.EGL_NONE
          };
    }
    EGLContext context =
        EGL14.eglCreateContext(
            display, config, android.opengl.EGL14.EGL_NO_CONTEXT, glAttributes, 0);
    GlUtil.checkGlException(context != null, "eglCreateContext 失败");
    return context;
  }

  private static EGLSurface createEGLSurface(
      EGLDisplay display, EGLConfig config, EGLContext context, @SecureMode int secureMode)
      throws GlUtil.GlException {
    EGLSurface surface;
    if (secureMode == SECURE_MODE_SURFACELESS_CONTEXT) {
      surface = EGL14.EGL_NO_SURFACE;
    } else {
      int[] pbufferAttributes;
      if (secureMode == SECURE_MODE_PROTECTED_PBUFFER) {
        pbufferAttributes =
            new int[] {
                EGL14.EGL_WIDTH,
                EGL_SURFACE_WIDTH,
                EGL14.EGL_HEIGHT,
                EGL_SURFACE_HEIGHT,
                EGL_PROTECTED_CONTENT_EXT,
                EGL14.EGL_TRUE,
                EGL14.EGL_NONE
            };
      } else {
        pbufferAttributes =
            new int[] {
                EGL14.EGL_WIDTH,
                EGL_SURFACE_WIDTH,
                EGL14.EGL_HEIGHT,
                EGL_SURFACE_HEIGHT,
                EGL14.EGL_NONE
            };
      }
      surface = EGL14.eglCreatePbufferSurface(display, config, pbufferAttributes, /* offset= */ 0);
      GlUtil.checkGlException(surface != null, "eglCreatePbufferSurface 失败");
    }

    boolean eglMadeCurrent =
        EGL14.eglMakeCurrent(display, /* draw= */ surface, /* read= */ surface, context);
    GlUtil.checkGlException(eglMadeCurrent, "eglMakeCurrent 失败");
    return surface;
  }

  private static void generateTextureIds(int[] textureIdHolder) throws GlUtil.GlException {
    GLES20.glGenTextures(/* n= */ 1, textureIdHolder, /* offset= */ 0);
    GlUtil.checkGlError();
  }
}