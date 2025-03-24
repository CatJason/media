package androidx.media3.common.util;

import android.os.Looper;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaLibraryInfo;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.dataflow.qual.Pure;

/** 提供用于断言表达式和属性是否为真的方法。 */
@UnstableApi
public final class Assertions {

  private Assertions() {}

  /**
   * 如果 {@code expression} 为 false，则抛出 {@link IllegalArgumentException}。
   *
   * @param expression 要评估的表达式。
   * @throws IllegalArgumentException 如果 {@code expression} 为 false。
   */
  @Pure
  public static void checkArgument(boolean expression) {
    if (MediaLibraryInfo.ASSERTIONS_ENABLED && !expression) {
      throw new IllegalArgumentException();
    }
  }

  /**
   * 如果 {@code expression} 为 false，则抛出 {@link IllegalArgumentException}。
   *
   * @param expression 要评估的表达式。
   * @param errorMessage 抛出异常时的异常消息。消息通过 {@link String#valueOf(Object)} 转换为 {@link String}。
   * @throws IllegalArgumentException 如果 {@code expression} 为 false。
   */
  @Pure
  public static void checkArgument(boolean expression, Object errorMessage) {
    if (MediaLibraryInfo.ASSERTIONS_ENABLED && !expression) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
  }

  /**
   * 如果 {@code index} 超出指定范围，则抛出 {@link IndexOutOfBoundsException}。
   *
   * @param index 要测试的索引。
   * @param start 允许范围的起始值（包含）。
   * @param limit 允许范围的结束值（不包含）。
   * @return 已验证的 {@code index}。
   * @throws IndexOutOfBoundsException 如果 {@code index} 超出指定范围。
   */
  @Pure
  public static int checkIndex(int index, int start, int limit) {
    if (index < start || index >= limit) {
      throw new IndexOutOfBoundsException();
    }
    return index;
  }

  /**
   * 如果 {@code expression} 为 false，则抛出 {@link IllegalStateException}。
   *
   * @param expression 要评估的表达式。
   * @throws IllegalStateException 如果 {@code expression} 为 false。
   */
  @Pure
  public static void checkState(boolean expression) {
    if (MediaLibraryInfo.ASSERTIONS_ENABLED && !expression) {
      throw new IllegalStateException();
    }
  }

  /**
   * 如果 {@code expression} 为 false，则抛出 {@link IllegalStateException}。
   *
   * @param expression 要评估的表达式。
   * @param errorMessage 抛出异常时的异常消息。消息通过 {@link String#valueOf(Object)} 转换为 {@link String}。
   * @throws IllegalStateException 如果 {@code expression} 为 false。
   */
  @Pure
  public static void checkState(boolean expression, Object errorMessage) {
    if (MediaLibraryInfo.ASSERTIONS_ENABLED && !expression) {
      throw new IllegalStateException(String.valueOf(errorMessage));
    }
  }

  /**
   * 如果 {@code reference} 为 null，则抛出 {@link IllegalStateException}。
   *
   * @param <T> 引用的类型。
   * @param reference 引用。
   * @return 已验证的非 null 引用。
   * @throws IllegalStateException 如果 {@code reference} 为 null。
   */
  @SuppressWarnings({"nullness:contracts.postcondition", "nullness:return"})
  @EnsuresNonNull({"#1"})
  @Pure
  public static <T> T checkStateNotNull(@Nullable T reference) {
    if (MediaLibraryInfo.ASSERTIONS_ENABLED && reference == null) {
      throw new IllegalStateException();
    }
    return reference;
  }

  /**
   * 如果 {@code reference} 为 null，则抛出 {@link IllegalStateException}。
   *
   * @param <T> 引用的类型。
   * @param reference 引用。
   * @param errorMessage 检查失败时使用的异常消息。消息通过 {@link String#valueOf(Object)} 转换为字符串。
   * @return 已验证的非 null 引用。
   * @throws IllegalStateException 如果 {@code reference} 为 null。
   */
  @SuppressWarnings({"nullness:contracts.postcondition", "nullness:return"})
  @EnsuresNonNull({"#1"})
  @Pure
  public static <T> T checkStateNotNull(@Nullable T reference, Object errorMessage) {
    if (MediaLibraryInfo.ASSERTIONS_ENABLED && reference == null) {
      throw new IllegalStateException(String.valueOf(errorMessage));
    }
    return reference;
  }

  /**
   * 如果 {@code reference} 为 null，则抛出 {@link NullPointerException}。
   *
   * @param <T> 引用的类型。
   * @param reference 引用。
   * @return 已验证的非 null 引用。
   * @throws NullPointerException 如果 {@code reference} 为 null。
   */
  @SuppressWarnings({"nullness:contracts.postcondition", "nullness:return"})
  @EnsuresNonNull({"#1"})
  @Pure
  public static <T> T checkNotNull(@Nullable T reference) {
    if (MediaLibraryInfo.ASSERTIONS_ENABLED && reference == null) {
      throw new NullPointerException();
    }
    return reference;
  }

  /**
   * 如果 {@code reference} 为 null，则抛出 {@link NullPointerException}。
   *
   * @param <T> 引用的类型。
   * @param reference 引用。
   * @param errorMessage 检查失败时使用的异常消息。消息通过 {@link String#valueOf(Object)} 转换为字符串。
   * @return 已验证的非 null 引用。
   * @throws NullPointerException 如果 {@code reference} 为 null。
   */
  @SuppressWarnings({"nullness:contracts.postcondition", "nullness:return"})
  @EnsuresNonNull({"#1"})
  @Pure
  public static <T> T checkNotNull(@Nullable T reference, Object errorMessage) {
    if (MediaLibraryInfo.ASSERTIONS_ENABLED && reference == null) {
      throw new NullPointerException(String.valueOf(errorMessage));
    }
    return reference;
  }

  /**
   * 如果 {@code string} 为 null 或长度为 0，则抛出 {@link IllegalArgumentException}。
   *
   * @param string 要检查的字符串。
   * @return 已验证的非 null、非空字符串。
   * @throws IllegalArgumentException 如果 {@code string} 为 null 或长度为 0。
   */
  @SuppressWarnings({"nullness:contracts.postcondition", "nullness:return"})
  @EnsuresNonNull({"#1"})
  @Pure
  public static String checkNotEmpty(@Nullable String string) {
    if (MediaLibraryInfo.ASSERTIONS_ENABLED && TextUtils.isEmpty(string)) {
      throw new IllegalArgumentException();
    }
    return string;
  }

  /**
   * 如果 {@code string} 为 null 或长度为 0，则抛出 {@link IllegalArgumentException}。
   *
   * @param string 要检查的字符串。
   * @param errorMessage 检查失败时使用的异常消息。消息通过 {@link String#valueOf(Object)} 转换为字符串。
   * @return 已验证的非 null、非空字符串。
   * @throws IllegalArgumentException 如果 {@code string} 为 null 或长度为 0。
   */
  @SuppressWarnings({"nullness:contracts.postcondition", "nullness:return"})
  @EnsuresNonNull({"#1"})
  @Pure
  public static String checkNotEmpty(@Nullable String string, Object errorMessage) {
    if (MediaLibraryInfo.ASSERTIONS_ENABLED && TextUtils.isEmpty(string)) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
    return string;
  }

  /**
   * 如果调用线程不是应用程序的主线程，则抛出 {@link IllegalStateException}。
   *
   * @throws IllegalStateException 如果调用线程不是应用程序的主线程。
   */
  @Pure
  public static void checkMainThread() {
    if (MediaLibraryInfo.ASSERTIONS_ENABLED && Looper.myLooper() != Looper.getMainLooper()) {
      throw new IllegalStateException("Not in applications main thread");
    }
  }
}