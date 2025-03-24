/**
 * 版权所有 (C) 2020 The Android Open Source Project
 *
 * 根据 Apache 许可证 2.0 版本（“许可证”）授权；
 * 除非符合许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则依据许可证分发的软件按“原样”分发，
 * 不提供任何形式的明示或暗示的担保或条件。
 * 有关许可证的详细信息，请参阅许可证中的具体语言。
 */
package androidx.media3.common.util;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一个允许重复元素的无序集合，同时也允许访问唯一元素集合。
 *
 * <p>该类通过使用与 {@link java.util.concurrent.CopyOnWriteArrayList} 相同的方式实现线程安全。
 * 修改方法会导致底层数据被复制。{@link #elementSet()} 和 {@link #iterator()} 返回的集合快照不受后续修改的影响。
 *
 * <p>直接遍历该类会显示重复元素。唯一元素可以通过 {@link #elementSet()} 访问。这两种遍历的顺序未定义。
 *
 * @param <E> 存储的元素类型。
 */
// 故意扩展 @NonNull-by-default Object 以禁止 @Nullable E 类型。
@SuppressWarnings("TypeParameterExplicitlyExtendsObject")
@UnstableApi
public final class CopyOnWriteMultiset<E extends Object> implements Iterable<E> {

  private final Object lock;

  @GuardedBy("lock")
  private final Map<E, Integer> elementCounts;

  @GuardedBy("lock")
  private Set<E> elementSet;

  @GuardedBy("lock")
  private List<E> elements;

  public CopyOnWriteMultiset() {
    lock = new Object();
    elementCounts = new HashMap<>();
    elementSet = Collections.emptySet();
    elements = Collections.emptyList();
  }

  /**
   * 将 {@code element} 添加到多重集合中。
   *
   * @param element 要添加的元素。
   */
  public void add(E element) {
    synchronized (lock) {
      List<E> elements = new ArrayList<>(this.elements);
      elements.add(element);
      this.elements = Collections.unmodifiableList(elements);

      @Nullable Integer count = elementCounts.get(element);
      if (count == null) {
        Set<E> elementSet = new HashSet<>(this.elementSet);
        elementSet.add(element);
        this.elementSet = Collections.unmodifiableSet(elementSet);
      }
      elementCounts.put(element, count != null ? count + 1 : 1);
    }
  }

  /**
   * 从多重集合中移除 {@code element}。
   *
   * @param element 要移除的元素。
   */
  public void remove(E element) {
    synchronized (lock) {
      @Nullable Integer count = elementCounts.get(element);
      if (count == null) {
        return;
      }

      List<E> elements = new ArrayList<>(this.elements);
      elements.remove(element);
      this.elements = Collections.unmodifiableList(elements);

      if (count == 1) {
        elementCounts.remove(element);
        Set<E> elementSet = new HashSet<>(this.elementSet);
        elementSet.remove(element);
        this.elementSet = Collections.unmodifiableSet(elementSet);
      } else {
        elementCounts.put(element, count - 1);
      }
    }
  }

  /**
   * 返回当前多重集合中唯一元素的快照。
   *
   * <p>对底层多重集合的修改不会反映在返回值中。
   *
   * @return 包含多重集合中唯一元素的不可修改集合。
   */
  public Set<E> elementSet() {
    synchronized (lock) {
      return elementSet;
    }
  }

  /**
   * 返回一个遍历当前多重集合中所有元素（包括重复元素）快照的迭代器。
   *
   * <p>对底层多重集合的修改不会反映在返回值中。
   *
   * @return 遍历多重集合中所有元素（包括重复元素）的不可修改迭代器。
   */
  @Override
  public Iterator<E> iterator() {
    synchronized (lock) {
      return elements.iterator();
    }
  }

  /** 返回多重集合中某个元素出现的次数。 */
  public int count(E element) {
    synchronized (lock) {
      return elementCounts.containsKey(element) ? elementCounts.get(element) : 0;
    }
  }
}