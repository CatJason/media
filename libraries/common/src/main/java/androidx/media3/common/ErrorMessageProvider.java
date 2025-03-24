package androidx.media3.common;

import android.util.Pair;

/** 将异常转换为错误代码和用户可读的错误消息。 */
public interface ErrorMessageProvider<T extends Throwable> {

  /**
   * 返回一个由错误代码和用户可读的错误消息组成的键值对，用于给定的异常。
   *
   * @param throwable 需要生成错误代码和消息的异常。
   * @return 一个由错误代码和用户可读的错误消息组成的键值对。
   */
  Pair<Integer, String> getErrorMessage(T throwable);
}