package androidx.media3.common.util;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;

import android.os.Bundle;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;

/** 用于将集合与 {@link Bundle} 实例相互转换的工具类。 */
@UnstableApi
public final class BundleCollectionUtil {

  /**
   * 将对象列表打包为 {@link Bundle} 实例列表。
   *
   * @param list 要打包的项列表。
   * @param toBundleFunc 指定如何打包每个项的函数。
   * @return 打包后的 {@link ImmutableList}。
   */
  public static <T extends @NonNull Object> ImmutableList<Bundle> toBundleList(
      List<T> list, Function<T, Bundle> toBundleFunc) {
    ImmutableList.Builder<Bundle> builder = ImmutableList.builder();
    for (int i = 0; i < list.size(); i++) {
      T item = list.get(i);
      builder.add(toBundleFunc.apply(item));
    }
    return builder.build();
  }

  /**
   * 将 {@link Bundle} 实例列表解包为对象列表。
   *
   * @param fromBundleFunc 指定如何解包每个项的函数。
   * @param bundleList 要解包的 {@link Bundle} 实例列表。
   * @return 解包后的 {@link ImmutableList}。
   */
  public static <T extends @NonNull Object> ImmutableList<T> fromBundleList(
      Function<Bundle, T> fromBundleFunc, List<Bundle> bundleList) {
    ImmutableList.Builder<T> builder = ImmutableList.builder();
    for (int i = 0; i < bundleList.size(); i++) {
      Bundle bundle = checkNotNull(bundleList.get(i)); // 在解析时快速失败。
      T item = fromBundleFunc.apply(bundle);
      builder.add(item);
    }
    return builder.build();
  }

  /**
   * 将对象集合打包为 {@link Bundle} 实例的 {@link ArrayList}，以便返回的列表可以通过 {@link Bundle#putParcelableArrayList} 方便地放入 {@link Bundle}。
   *
   * @param items 要打包的项集合。
   * @param toBundleFunc 指定如何打包每个项的函数。
   * @return 打包后的 {@link ArrayList}。
   */
  @SuppressWarnings("NonApiType") // 有意使用 ArrayList 以便使用 putParcelableArrayList。
  public static <T extends @NonNull Object> ArrayList<Bundle> toBundleArrayList(
      Collection<T> items, Function<T, Bundle> toBundleFunc) {
    ArrayList<Bundle> arrayList = new ArrayList<>(items.size());
    for (T item : items) {
      arrayList.add(toBundleFunc.apply(item));
    }
    return arrayList;
  }

  /**
   * 将 {@link Bundle} 实例的 {@link SparseArray} 解包为对象的 {@link SparseArray}。
   *
   * @param fromBundleFunc 指定如何解包每个项的函数。
   * @param bundleSparseArray 要解包的 {@link Bundle} 实例的 {@link SparseArray}。
   * @return 解包后的 {@link SparseArray}。
   */
  public static <T extends @NonNull Object> SparseArray<T> fromBundleSparseArray(
      Function<Bundle, T> fromBundleFunc, SparseArray<Bundle> bundleSparseArray) {
    SparseArray<T> result = new SparseArray<>(bundleSparseArray.size());
    for (int i = 0; i < bundleSparseArray.size(); i++) {
      result.put(bundleSparseArray.keyAt(i), fromBundleFunc.apply(bundleSparseArray.valueAt(i)));
    }
    return result;
  }

  /**
   * 将对象的 {@link SparseArray} 打包为 {@link Bundle} 实例的 {@link SparseArray}，以便返回的 {@link SparseArray} 可以通过 {@link Bundle#putSparseParcelableArray} 方便地放入 {@link Bundle}。
   *
   * @param items 要打包的项集合。
   * @param toBundleFunc 指定如何打包每个项的函数。
   * @return 打包后的 {@link SparseArray}。
   */
  public static <T extends @NonNull Object> SparseArray<Bundle> toBundleSparseArray(
      SparseArray<T> items, Function<T, Bundle> toBundleFunc) {
    SparseArray<Bundle> sparseArray = new SparseArray<>(items.size());
    for (int i = 0; i < items.size(); i++) {
      sparseArray.put(items.keyAt(i), toBundleFunc.apply(items.valueAt(i)));
    }
    return sparseArray;
  }

  /**
   * 将字符串映射转换为 {@link Bundle}。
   *
   * @param map 字符串映射。
   * @return 转换后的 {@link Bundle}。
   */
  public static Bundle stringMapToBundle(Map<String, String> map) {
    Bundle bundle = new Bundle();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      bundle.putString(entry.getKey(), entry.getValue());
    }
    return bundle;
  }

  /**
   * 将 {@link Bundle} 转换为字符串的 {@link HashMap}。
   *
   * @param bundle 要转换的 {@link Bundle}。
   * @return 转换后的 {@link HashMap}。
   */
  public static HashMap<String, String> bundleToStringHashMap(Bundle bundle) {
    HashMap<String, String> map = new HashMap<>();
    if (bundle == Bundle.EMPTY) {
      return map;
    }
    for (String key : bundle.keySet()) {
      @Nullable String value = bundle.getString(key);
      if (value != null) {
        map.put(key, value);
      }
    }
    return map;
  }

  /**
   * 将 {@link Bundle} 转换为字符串的 {@link ImmutableMap}。
   *
   * @param bundle 要转换的 {@link Bundle}。
   * @return 转换后的 {@link ImmutableMap}。
   */
  public static ImmutableMap<String, String> bundleToStringImmutableMap(Bundle bundle) {
    if (bundle == Bundle.EMPTY) {
      return ImmutableMap.of();
    }
    HashMap<String, String> map = bundleToStringHashMap(bundle);
    return ImmutableMap.copyOf(map);
  }

  /**
   * 从 {@link Bundle} 中获取指定字段的 {@link Bundle}，如果字段不存在则返回默认值。
   *
   * @param bundle 要查询的 {@link Bundle}。
   * @param field 字段名称。
   * @param defaultValue 默认值。
   * @return 字段的值或默认值。
   */
  public static Bundle getBundleWithDefault(Bundle bundle, String field, Bundle defaultValue) {
    @Nullable Bundle result = bundle.getBundle(field);
    return result != null ? result : defaultValue;
  }

  /**
   * 从 {@link Bundle} 中获取指定字段的整数 {@link ArrayList}，如果字段不存在则返回默认值。
   *
   * @param bundle 要查询的 {@link Bundle}。
   * @param field 字段名称。
   * @param defaultValue 默认值。
   * @return 字段的值或默认值。
   */
  public static ArrayList<Integer> getIntegerArrayListWithDefault(
      Bundle bundle, String field, ArrayList<Integer> defaultValue) {
    @Nullable ArrayList<Integer> result = bundle.getIntegerArrayList(field);
    return result != null ? result : defaultValue;
  }

  /**
   * 如果给定的 {@link Bundle} 中没有类加载器，则为其设置应用程序类加载器。
   *
   * <p>假设从 {@code bundle} 中解包的所有类都共享 {@code BundleCollectionUtil} 的类加载器。
   */
  public static void ensureClassLoader(@Nullable Bundle bundle) {
    if (bundle != null) {
      bundle.setClassLoader(castNonNull(BundleCollectionUtil.class.getClassLoader()));
    }
  }

  private BundleCollectionUtil() {}
}