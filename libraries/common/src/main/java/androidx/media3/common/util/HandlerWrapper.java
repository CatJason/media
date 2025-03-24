package androidx.media3.common.util;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;

/**
 * 一个通过 {@link Handler} 调用的接口。所有非测试用例的实例必须通过调用 {@link Clock#createHandler(Looper, Handler.Callback)} 在 {@link Clock#DEFAULT} 上创建。
 */
@UnstableApi
public interface HandlerWrapper {

  /** 从 Handler 获取的消息。 */
  interface Message {

    /** 参见 {@link android.os.Message#sendToTarget()}。 */
    void sendToTarget();

    /** 参见 {@link android.os.Message#getTarget()}。 */
    HandlerWrapper getTarget();
  }

  /** 参见 {@link Handler#getLooper()}。 */
  Looper getLooper();

  /** 参见 {@link Handler#hasMessages(int)}。 */
  boolean hasMessages(int what);

  /** 参见 {@link Handler#obtainMessage(int)}。 */
  Message obtainMessage(int what);

  /** 参见 {@link Handler#obtainMessage(int, Object)}。 */
  Message obtainMessage(int what, @Nullable Object obj);

  /** 参见 {@link Handler#obtainMessage(int, int, int)}。 */
  Message obtainMessage(int what, int arg1, int arg2);

  /** 参见 {@link Handler#obtainMessage(int, int, int, Object)}。 */
  Message obtainMessage(int what, int arg1, int arg2, @Nullable Object obj);

  /** 参见 {@link Handler#sendMessageAtFrontOfQueue(android.os.Message)}。 */
  boolean sendMessageAtFrontOfQueue(Message message);

  /** 参见 {@link Handler#sendEmptyMessage(int)}。 */
  boolean sendEmptyMessage(int what);

  /** 参见 {@link Handler#sendEmptyMessageDelayed(int, long)}。 */
  boolean sendEmptyMessageDelayed(int what, int delayMs);

  /** 参见 {@link Handler#sendEmptyMessageAtTime(int, long)}。 */
  boolean sendEmptyMessageAtTime(int what, long uptimeMs);

  /** 参见 {@link Handler#removeMessages(int)}。 */
  void removeMessages(int what);

  /** 参见 {@link Handler#removeCallbacksAndMessages(Object)}。 */
  void removeCallbacksAndMessages(@Nullable Object token);

  /** 参见 {@link Handler#post(Runnable)}。 */
  boolean post(Runnable runnable);

  /** 参见 {@link Handler#postDelayed(Runnable, long)}。 */
  boolean postDelayed(Runnable runnable, long delayMs);

  /** 参见 {@link android.os.Handler#postAtFrontOfQueue(Runnable)}。 */
  boolean postAtFrontOfQueue(Runnable runnable);
}