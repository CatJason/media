package androidx.media3.common.util;

import java.util.Arrays;

/** 可配置的本地库加载器。 */
@UnstableApi
public abstract class LibraryLoader {

  private static final String TAG = "LibraryLoader";

  private String[] nativeLibraries;
  private boolean loadAttempted;
  private boolean isAvailable;

  /**
   * @param libraries 要加载的库的名称。
   */
  public LibraryLoader(String... libraries) {
    nativeLibraries = libraries;
  }

  /**
   * 覆盖要加载的库的名称。必须在任何调用 {@link #isAvailable()} 之前调用。
   */
  public synchronized void setLibraries(String... libraries) {
    Assertions.checkState(!loadAttempted, "加载后无法设置库");
    nativeLibraries = libraries;
  }

  /** 返回底层库是否可用，必要时会加载它们。 */
  public synchronized boolean isAvailable() {
    if (loadAttempted) {
      return isAvailable;
    }
    loadAttempted = true;
    try {
      for (String lib : nativeLibraries) {
        loadLibrary(lib);
      }
      isAvailable = true;
    } catch (UnsatisfiedLinkError exception) {
      // 记录警告，因为检查库的尝试表明应用程序依赖于某个扩展，
      // 并且通常期望其本地库可用。
      Log.w(TAG, "加载失败 " + Arrays.toString(nativeLibraries));
    }
    return isAvailable;
  }

  /**
   * 应实现为调用 {@code System.loadLibrary(name)}。
   *
   * <p>每个子类都需要实现此方法，因为 {@link System#loadLibrary(String)} 使用反射获取调用类，
   * 然后用于获取加载本地库时要使用的类加载器。如果此类直接实现该方法，
   * 并且如果子类具有不同的类加载器，则加载本地库将失败。
   *
   * @param name 要加载的库的名称。
   */
  protected abstract void loadLibrary(String name);
}