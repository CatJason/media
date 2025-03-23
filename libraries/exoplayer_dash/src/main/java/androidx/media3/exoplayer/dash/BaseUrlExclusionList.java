package androidx.media3.exoplayer.dash;

import static androidx.media3.common.util.Util.castNonNull;
import static java.lang.Math.max;

import android.os.SystemClock;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.dash.manifest.BaseUrl;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 用于管理被 {@link #exclude(BaseUrl, long) 排除} 的 Base URL 状态，并根据这些排除规则 {@link #selectBaseUrl(List) 选择} Base URL。
 */
@UnstableApi
public final class BaseUrlExclusionList {

  private final Map<String, Long> excludedServiceLocations;
  private final Map<Integer, Long> excludedPriorities;
  private final Map<List<Pair<String, Integer>>, BaseUrl> selectionsTaken = new HashMap<>();
  private final Random random;

  /** 创建一个实例。 */
  public BaseUrlExclusionList() {
    this(new Random());
  }

  /** 使用指定的 {@link Random} 创建一个实例。 */
  @VisibleForTesting
  /* package */ BaseUrlExclusionList(Random random) {
    this.random = random;
    excludedServiceLocations = new HashMap<>();
    excludedPriorities = new HashMap<>();
  }

  /**
   * 排除指定的 Base URL。
   *
   * @param baseUrlToExclude 要排除的 Base URL。
   * @param exclusionDurationMs 排除的持续时间，单位为毫秒。
   */
  public void exclude(BaseUrl baseUrlToExclude, long exclusionDurationMs) {
    long excludeUntilMs = SystemClock.elapsedRealtime() + exclusionDurationMs;
    addExclusion(baseUrlToExclude.serviceLocation, excludeUntilMs, excludedServiceLocations);
    if (baseUrlToExclude.priority != BaseUrl.PRIORITY_UNSET) {
      addExclusion(baseUrlToExclude.priority, excludeUntilMs, excludedPriorities);
    }
  }

  /**
   * 从给定的 Base URL 列表中选择一个 Base URL。
   *
   * <p>该列表会根据已通过 {@link #exclude(BaseUrl, long)} 排除的 Base URL 的服务位置和优先级进行缩减。
   * 然后从剩余的 Base URL 中根据优先级和权重选择一个 Base URL。
   *
   * @param baseUrls 要从中选择的 {@link BaseUrl Base URL} 列表。
   * @return 排除后选择的 Base URL，如果所有元素都被排除则返回 null。
   */
  @Nullable
  public BaseUrl selectBaseUrl(List<BaseUrl> baseUrls) {
    List<BaseUrl> includedBaseUrls = applyExclusions(baseUrls);
    if (includedBaseUrls.size() < 2) {
      return Iterables.getFirst(includedBaseUrls, /* defaultValue= */ null);
    }
    // 按优先级和服务位置排序，使候选者的顺序具有确定性。
    Collections.sort(includedBaseUrls, BaseUrlExclusionList::compareBaseUrl);
    // 从排序后的列表头部获取最低优先级的候选者。
    List<Pair<String, Integer>> candidateKeys = new ArrayList<>();
    int lowestPriority = includedBaseUrls.get(0).priority;
    for (int i = 0; i < includedBaseUrls.size(); i++) {
      BaseUrl baseUrl = includedBaseUrls.get(i);
      if (lowestPriority != baseUrl.priority) {
        if (candidateKeys.size() == 1) {
          // 只有一个最低优先级的候选者；无需选择。
          return includedBaseUrls.get(0);
        }
        break;
      }
      candidateKeys.add(new Pair<>(baseUrl.serviceLocation, baseUrl.weight));
    }
    // 检查是否已经进行过选择。
    @Nullable BaseUrl baseUrl = selectionsTaken.get(candidateKeys);
    if (baseUrl == null) {
      // 从多个相同优先级的候选者中进行加权随机选择。
      baseUrl = selectWeighted(includedBaseUrls.subList(0, candidateKeys.size()));
      // 记住已进行的选择，以便后续使用。
      selectionsTaken.put(candidateKeys, baseUrl);
    }
    return baseUrl;
  }

  /**
   * 返回排除后给定 Base URL 列表的优先级级别数量。
   *
   * @param baseUrls Base URL 列表。
   * @return 排除后的优先级级别数量。
   */
  public int getPriorityCountAfterExclusion(List<BaseUrl> baseUrls) {
    Set<Integer> priorities = new HashSet<>();
    List<BaseUrl> includedBaseUrls = applyExclusions(baseUrls);
    for (int i = 0; i < includedBaseUrls.size(); i++) {
      priorities.add(includedBaseUrls.get(i).priority);
    }
    return priorities.size();
  }

  /**
   * 返回给定 Base URL 列表的优先级级别数量。
   *
   * @param baseUrls Base URL 列表。
   * @return 排除前的优先级级别数量。
   */
  public static int getPriorityCount(List<BaseUrl> baseUrls) {
    Set<Integer> priorities = new HashSet<>();
    for (int i = 0; i < baseUrls.size(); i++) {
      priorities.add(baseUrls.get(i).priority);
    }
    return priorities.size();
  }

  /** 重置状态。 */
  public void reset() {
    excludedServiceLocations.clear();
    excludedPriorities.clear();
    selectionsTaken.clear();
  }

  // 内部方法。

  private List<BaseUrl> applyExclusions(List<BaseUrl> baseUrls) {
    long nowMs = SystemClock.elapsedRealtime();
    removeExpiredExclusions(nowMs, excludedServiceLocations);
    removeExpiredExclusions(nowMs, excludedPriorities);
    List<BaseUrl> includedBaseUrls = new ArrayList<>();
    for (int i = 0; i < baseUrls.size(); i++) {
      BaseUrl baseUrl = baseUrls.get(i);
      if (!excludedServiceLocations.containsKey(baseUrl.serviceLocation)
          && !excludedPriorities.containsKey(baseUrl.priority)) {
        includedBaseUrls.add(baseUrl);
      }
    }
    return includedBaseUrls;
  }

  private BaseUrl selectWeighted(List<BaseUrl> candidates) {
    int totalWeight = 0;
    for (int i = 0; i < candidates.size(); i++) {
      totalWeight += candidates.get(i).weight;
    }
    int randomChoice = random.nextInt(/* bound= */ totalWeight);
    totalWeight = 0;
    for (int i = 0; i < candidates.size(); i++) {
      BaseUrl baseUrl = candidates.get(i);
      totalWeight += baseUrl.weight;
      if (randomChoice < totalWeight) {
        return baseUrl;
      }
    }
    return Iterables.getLast(candidates);
  }

  private static <T> void addExclusion(
      T toExclude, long excludeUntilMs, Map<T, Long> currentExclusions) {
    if (currentExclusions.containsKey(toExclude)) {
      excludeUntilMs = max(excludeUntilMs, castNonNull(currentExclusions.get(toExclude)));
    }
    currentExclusions.put(toExclude, excludeUntilMs);
  }

  private static <T> void removeExpiredExclusions(long nowMs, Map<T, Long> exclusions) {
    List<T> expiredExclusions = new ArrayList<>();
    for (Map.Entry<T, Long> entries : exclusions.entrySet()) {
      if (entries.getValue() <= nowMs) {
        expiredExclusions.add(entries.getKey());
      }
    }
    for (int i = 0; i < expiredExclusions.size(); i++) {
      exclusions.remove(expiredExclusions.get(i));
    }
  }

  /** 按优先级和服务位置进行比较。 */
  private static int compareBaseUrl(BaseUrl a, BaseUrl b) {
    int compare = Integer.compare(a.priority, b.priority);
    return compare != 0 ? compare : a.serviceLocation.compareTo(b.serviceLocation);
  }
}