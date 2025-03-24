package androidx.media3.common;

import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import java.util.HashSet;

/** 关于媒体库的信息。 */
@UnstableApi
public final class MediaLibraryInfo {

  /** 用于记录库信息的标签。 */
  public static final String TAG = "AndroidXMedia3";

  /** 以字符串形式表示的库版本，例如 "1.2.3" 或 "1.2.0-beta01"。 */
  // 特意硬编码。不要从其他常量（例如 VERSION_INT）派生，反之亦然。
  public static final String VERSION = "1.5.1";

  /** 以 {@code TAG + "/" + VERSION} 形式表示的库版本。 */
  // 特意硬编码。不要从其他常量（例如 VERSION）派生，反之亦然。
  public static final String VERSION_SLASHY = "AndroidXMedia3/1.5.1";

  /**
   * 以整数形式表示的库版本，例如 1002003300。
   *
   * <p>使用三位数字表示 {@link #VERSION} 的前三个部分，然后使用一位数字表示此版本的周期：
   * alpha (0)、beta (1)、rc (2) 或稳定版 (3)。最后使用两位数字表示周期编号（稳定版始终为 00）。
   *
   * <p>例如，"1.2.0-rc05" 对应的整数版本为 1002000205 (001-002-000-2-05)，
   * 而 "123.45.6" 对应的整数版本为 123045006300 (123-045-006-3-00)。
   */
  // 特意硬编码。不要从其他常量（例如 VERSION）派生，反之亦然。
  public static final int VERSION_INT = 1_005_001_3_00;

  /** 库是否在编译时启用了 {@link Assertions} 检查。 */
  public static final boolean ASSERTIONS_ENABLED = true;

  /** 库是否在编译时启用了 {@link TraceUtil} 跟踪功能。 */
  public static final boolean TRACE_ENABLED = true;

  private static final HashSet<String> registeredModules = new HashSet<>();
  private static String registeredModulesString = "media3.common";

  private MediaLibraryInfo() {} // 防止实例化。

  /** 返回由注册模块名称组成的字符串，模块名称之间用 ", " 分隔。 */
  public static synchronized String registeredModules() {
    return registeredModulesString;
  }

  /**
   * 注册一个模块，该模块的名称将包含在 {@link #registeredModules()} 返回的字符串中。
   *
   * @param name 要注册的模块名称。
   */
  public static synchronized void registerModule(String name) {
    if (registeredModules.add(name)) {
      registeredModulesString = registeredModulesString + ", " + name;
    }
  }
}