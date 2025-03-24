package androidx.media3.common.util;

import static androidx.media3.common.util.Assertions.checkState;

import android.os.Looper;
import android.os.Message;
import androidx.annotation.CheckResult;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.FlagSet;
import java.util.ArrayDeque;
import java.util.concurrent.CopyOnWriteArraySet;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * 一组监听器。
 *
 * <p>事件保证按照发生的顺序传递，即使新事件是从另一个监听器递归触发的。
 *
 * <p>事件也保证仅发送给在事件入队时注册且此后未被移除的监听器。
 *
 * <p>除非另有说明，否则所有方法都必须在传递给构造函数的 {@link Looper} 上调用。
 *
 * @param <T> 监听器类型。
 */
@UnstableApi
public final class ListenerSet<T extends @NonNull Object> {

  /**
   * 发送给监听器的事件。
   *
   * @param <T> 监听器类型。
   */
  public interface Event<T> {

    /** 在给定的监听器上调用事件通知。 */
    void invoke(T listener);
  }

  /**
   * 当所有其他事件在一次 {@link Looper} 消息队列迭代中被监听器处理时，发送给监听器的事件。
   *
   * @param <T> 监听器类型。
   */
  public interface IterationFinishedEvent<T> {

    /**
     * 调用迭代完成事件。
     *
     * @param listener 要调用事件的监听器。
     * @param eventFlags 本次迭代中发送的所有事件的组合 {@link FlagSet 标志}。
     */
    void invoke(T listener, FlagSet eventFlags);
  }

  private static final int MSG_ITERATION_FINISHED = 1;

  private final Clock clock;
  private final HandlerWrapper handler;
  private final IterationFinishedEvent<T> iterationFinishedEvent;
  private final CopyOnWriteArraySet<ListenerHolder<T>> listeners;
  private final ArrayDeque<Runnable> flushingEvents;
  private final ArrayDeque<Runnable> queuedEvents;
  private final Object releasedLock;

  @GuardedBy("releasedLock")
  private boolean released;

  private boolean throwsWhenUsingWrongThread;

  /**
   * 创建一个新的监听器集合。
   *
   * @param looper 用于调用监听器的 {@link Looper}。除非另有说明，否则必须使用相同的 {@link Looper} 调用此类的所有其他方法。
   * @param clock 一个 {@link Clock}。
   * @param iterationFinishedEvent 当所有其他事件在一次 {@link Looper} 消息队列迭代中被监听器处理时发送的 {@link IterationFinishedEvent}。
   */
  public ListenerSet(Looper looper, Clock clock, IterationFinishedEvent<T> iterationFinishedEvent) {
    this(
        /* listeners= */ new CopyOnWriteArraySet<>(),
        looper,
        clock,
        iterationFinishedEvent,
        /* throwsWhenUsingWrongThread= */ true);
  }

  private ListenerSet(
      CopyOnWriteArraySet<ListenerHolder<T>> listeners,
      Looper looper,
      Clock clock,
      IterationFinishedEvent<T> iterationFinishedEvent,
      boolean throwsWhenUsingWrongThread) {
    this.clock = clock;
    this.listeners = listeners;
    this.iterationFinishedEvent = iterationFinishedEvent;
    releasedLock = new Object();
    flushingEvents = new ArrayDeque<>();
    queuedEvents = new ArrayDeque<>();
    // 使用 "this" 是安全的，因为我们在退出构造函数之前不会发送消息。
    @SuppressWarnings("nullness:methodref.receiver.bound")
    HandlerWrapper handler = clock.createHandler(looper, this::handleMessage);
    this.handler = handler;
    this.throwsWhenUsingWrongThread = throwsWhenUsingWrongThread;
  }

  /**
   * 复制监听器集合。
   *
   * <p>此方法可以从任何线程调用。
   *
   * @param looper 新监听器集合的 {@link Looper}。
   * @param iterationFinishedEvent 当所有其他事件在一次 {@link Looper} 消息队列迭代中被监听器处理时发送的新 {@link IterationFinishedEvent}。
   * @return 复制的监听器集合。
   */
  @CheckResult
  public ListenerSet<T> copy(Looper looper, IterationFinishedEvent<T> iterationFinishedEvent) {
    return copy(looper, clock, iterationFinishedEvent);
  }

  /**
   * 复制监听器集合。
   *
   * <p>此方法可以从任何线程调用。
   *
   * @param looper 新监听器集合的 {@link Looper}。
   * @param clock 新监听器集合的 {@link Clock}。
   * @param iterationFinishedEvent 当所有其他事件在一次 {@link Looper} 消息队列迭代中被监听器处理时发送的新 {@link IterationFinishedEvent}。
   * @return 复制的监听器集合。
   */
  @CheckResult
  public ListenerSet<T> copy(
      Looper looper, Clock clock, IterationFinishedEvent<T> iterationFinishedEvent) {
    return new ListenerSet<>(
        listeners, looper, clock, iterationFinishedEvent, throwsWhenUsingWrongThread);
  }

  /**
   * 向集合中添加一个监听器。
   *
   * <p>如果监听器已经存在，则不会再次添加。
   *
   * <p>此方法可以从任何线程调用。
   *
   * @param listener 要添加的监听器。
   */
  public void add(T listener) {
    Assertions.checkNotNull(listener);
    synchronized (releasedLock) {
      if (released) {
        return;
      }
      listeners.add(new ListenerHolder<>(listener));
    }
  }

  /**
   * 从集合中移除一个监听器。
   *
   * <p>如果监听器不存在，则什么都不做。
   *
   * @param listener 要移除的监听器。
   */
  public void remove(T listener) {
    verifyCurrentThread();
    for (ListenerHolder<T> listenerHolder : listeners) {
      if (listenerHolder.listener.equals(listener)) {
        listenerHolder.release(iterationFinishedEvent);
        listeners.remove(listenerHolder);
      }
    }
  }

  /** 从集合中移除所有监听器。 */
  public void clear() {
    verifyCurrentThread();
    for (ListenerHolder<T> listenerHolder : listeners) {
      listenerHolder.release(iterationFinishedEvent);
    }
    listeners.clear();
  }

  /** 返回已添加的监听器数量。 */
  public int size() {
    verifyCurrentThread();
    return listeners.size();
  }

  /**
   * 添加一个事件，该事件在调用 {@link #flushEvents} 时发送给监听器。
   *
   * @param eventFlag 表示事件类型的整数，或 {@link C#INDEX_UNSET} 表示不带标志的事件。
   * @param event 事件。
   */
  public void queueEvent(int eventFlag, Event<T> event) {
    verifyCurrentThread();
    CopyOnWriteArraySet<ListenerHolder<T>> listenerSnapshot = new CopyOnWriteArraySet<>(listeners);
    queuedEvents.add(
        () -> {
          for (ListenerHolder<T> holder : listenerSnapshot) {
            holder.invoke(eventFlag, event);
          }
        });
  }

  /** 通知监听器之前通过 {@link #queueEvent(int, Event)} 入队的事件。 */
  public void flushEvents() {
    verifyCurrentThread();
    if (queuedEvents.isEmpty()) {
      return;
    }
    if (!handler.hasMessages(MSG_ITERATION_FINISHED)) {
      handler.sendMessageAtFrontOfQueue(handler.obtainMessage(MSG_ITERATION_FINISHED));
    }
    boolean recursiveFlushInProgress = !flushingEvents.isEmpty();
    flushingEvents.addAll(queuedEvents);
    queuedEvents.clear();
    if (recursiveFlushInProgress) {
      // 递归调用 flush。让外部调用处理 flush 队列。
      return;
    }
    while (!flushingEvents.isEmpty()) {
      flushingEvents.peekFirst().run();
      flushingEvents.removeFirst();
    }
  }

  /**
   * {@link #queueEvent(int, Event) 入队} 单个事件并立即 {@link #flushEvents() 刷新} 事件队列以通知所有监听器。
   *
   * @param eventFlag 表示事件类型的整数标志，或 {@link C#INDEX_UNSET} 表示不带标志的事件。
   * @param event 事件。
   */
  public void sendEvent(int eventFlag, Event<T> event) {
    queueEvent(eventFlag, event);
    flushEvents();
  }

  /**
   * 立即释放监听器集合。
   *
   * <p>这将确保在此方法调用后不会向任何监听器发送事件。
   */
  public void release() {
    verifyCurrentThread();
    synchronized (releasedLock) {
      released = true;
    }
    for (ListenerHolder<T> listenerHolder : listeners) {
      listenerHolder.release(iterationFinishedEvent);
    }
    listeners.clear();
  }

  /**
   * 设置在使用错误线程时是否抛出异常。
   *
   * <p>除非支持遗留用例，否则不要使用此方法。
   *
   * @param throwsWhenUsingWrongThread 是否在使用错误线程时抛出异常。
   * @deprecated 不要使用此方法，并确保所有调用都从正确的线程进行。
   */
  @Deprecated
  public void setThrowsWhenUsingWrongThread(boolean throwsWhenUsingWrongThread) {
    this.throwsWhenUsingWrongThread = throwsWhenUsingWrongThread;
  }

  private boolean handleMessage(Message message) {
    for (ListenerHolder<T> holder : listeners) {
      holder.iterationFinished(iterationFinishedEvent);
      if (handler.hasMessages(MSG_ITERATION_FINISHED)) {
        // 上述调用触发了新事件（因此安排了新消息）。我们需要在此停止，
        // 因为此新消息将负责通知每个监听器有关新更新（包括已在此处调用的监听器）。
        break;
      }
    }
    return true;
  }

  private void verifyCurrentThread() {
    if (!throwsWhenUsingWrongThread) {
      return;
    }
    checkState(Thread.currentThread() == handler.getLooper().getThread());
  }

  private static final class ListenerHolder<T extends @NonNull Object> {

    public final T listener;

    private FlagSet.Builder flagsBuilder;
    private boolean needsIterationFinishedEvent;
    private boolean released;

    public ListenerHolder(T listener) {
      this.listener = listener;
      this.flagsBuilder = new FlagSet.Builder();
    }

    public void release(IterationFinishedEvent<T> event) {
      released = true;
      if (needsIterationFinishedEvent) {
        needsIterationFinishedEvent = false;
        event.invoke(listener, flagsBuilder.build());
      }
    }

    public void invoke(int eventFlag, Event<T> event) {
      if (!released) {
        if (eventFlag != C.INDEX_UNSET) {
          flagsBuilder.add(eventFlag);
        }
        needsIterationFinishedEvent = true;
        event.invoke(listener);
      }
    }

    public void iterationFinished(IterationFinishedEvent<T> event) {
      if (!released && needsIterationFinishedEvent) {
        // 在调用监听器之前重置标志，以确保我们保留由此回调触发的递归事件设置的所有新标志。
        FlagSet flagsToNotify = flagsBuilder.build();
        flagsBuilder = new FlagSet.Builder();
        needsIterationFinishedEvent = false;
        event.invoke(listener, flagsToNotify);
      }
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      return listener.equals(((ListenerHolder<?>) other).listener);
    }

    @Override
    public int hashCode() {
      return listener.hashCode();
    }
  }
}