package androidx.media3.common;

import static java.lang.Math.max;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.io.IOException;
import java.util.Collections;
import java.util.PriorityQueue;

/**
 * 允许具有关联 {@linkplain C.Priority 优先级}的任务控制它们相对于彼此的执行顺序。
 *
 * <p>任务应调用 {@link #add(int)} 进行注册，并在完成后调用 {@link #remove(int)} 进行注销。
 * 已注册的任务将阻止优先级较低的任务继续执行，并且应在每次希望检查自身是否被允许继续执行时调用
 * {@link #proceed(int)}、{@link #proceedNonBlocking(int)} 或 {@link #proceedOrThrow(int)}。
 *
 * <p>建议使用预定义的 {@linkplain C.Priority 优先级} 或相对于这些默认值定义的优先级值。
 */
@UnstableApi
public final class PriorityTaskManager {

  /** 当任务尝试继续执行时，如果另一个已注册的任务具有更高的优先级，则抛出此异常。 */
  public static class PriorityTooLowException extends IOException {

    public PriorityTooLowException(@C.Priority int priority, @C.Priority int highestPriority) {
      super("优先级太低 [priority=" + priority + ", highest=" + highestPriority + "]");
    }
  }

  private final Object lock = new Object();

  // 由 lock 保护。
  private final PriorityQueue<@C.Priority Integer> queue;
  private @C.Priority int highestPriority;

  public PriorityTaskManager() {
    queue = new PriorityQueue<>(10, Collections.reverseOrder());
    highestPriority = Integer.MIN_VALUE;
  }

  /**
   * 注册一个新任务。任务完成后必须调用 {@link #remove(int)}。
   *
   * <p>建议使用预定义的 {@linkplain C.Priority 优先级} 或相对于这些默认值定义的优先级值。
   *
   * @param priority 任务的 {@link C.Priority}。值越大表示优先级越高。
   */
  public void add(@C.Priority int priority) {
    synchronized (lock) {
      queue.add(priority);
      highestPriority = max(highestPriority, priority);
    }
  }

  /**
   * 阻塞，直到任务被允许继续执行。
   *
   * @param priority 任务的 {@link C.Priority}。
   * @throws InterruptedException 如果线程被中断。
   */
  public void proceed(@C.Priority int priority) throws InterruptedException {
    synchronized (lock) {
      while (highestPriority != priority) {
        lock.wait();
      }
    }
  }

  /**
   * {@link #proceed(int)} 的非阻塞版本。
   *
   * @param priority 任务的 {@link C.Priority}。
   * @return 任务是否被允许继续执行。
   */
  public boolean proceedNonBlocking(@C.Priority int priority) {
    synchronized (lock) {
      return highestPriority == priority;
    }
  }

  /**
   * {@link #proceed(int)} 的抛出异常版本。
   *
   * @param priority 任务的 {@link C.Priority}。
   * @throws PriorityTooLowException 如果任务未被允许继续执行。
   */
  public void proceedOrThrow(@C.Priority int priority) throws PriorityTooLowException {
    synchronized (lock) {
      if (highestPriority != priority) {
        throw new PriorityTooLowException(priority, highestPriority);
      }
    }
  }

  /**
   * 注销一个任务。
   *
   * @param priority 任务的 {@link C.Priority}。
   */
  public void remove(@C.Priority int priority) {
    synchronized (lock) {
      queue.remove(priority);
      highestPriority = queue.isEmpty() ? Integer.MIN_VALUE : Util.castNonNull(queue.peek());
      lock.notifyAll();
    }
  }
}