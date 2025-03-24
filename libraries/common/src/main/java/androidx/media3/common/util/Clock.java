package androidx.media3.common.util;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;

/**
 * 一个接口，用于读取系统时钟并创建 {@link HandlerWrapper}。所有非测试场景必须使用 {@link #DEFAULT} 实现。
 */
@UnstableApi
public interface Clock {

  /** 用于所有非测试场景的默认 {@link Clock}。 */
  Clock DEFAULT = new SystemClock();

  /**
   * 返回自 Unix 纪元以来的当前时间（以毫秒为单位）。
   *
   * @see System#currentTimeMillis()
   */
  long currentTimeMillis();

  /**
   * @see android.os.SystemClock#elapsedRealtime()
   */
  long elapsedRealtime();

  /**
   * @see android.os.SystemClock#uptimeMillis()
   */
  long uptimeMillis();

  /** @see java.lang.System#nanoTime() */
  long nanoTime();

  /**
   * 使用指定的 Looper 和指定的回调创建 {@link HandlerWrapper}，用于处理消息。
   *
   * @see Handler#Handler(Looper, Handler.Callback)
   */
  HandlerWrapper createHandler(
      Looper looper, @Nullable Handler.@UnknownInitialization Callback callback);

  /**
   * 通知时钟当前线程即将被阻塞，直到另一个线程上的条件变为真。
   *
   * <p>对于所有非测试场景，此方法应为空操作。
   */
  void onThreadBlocked();
}