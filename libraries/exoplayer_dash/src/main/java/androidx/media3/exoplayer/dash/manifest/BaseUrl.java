package androidx.media3.exoplayer.dash.manifest;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import com.google.common.base.Objects;

/** 表示一个基础 URL，定义见 ISO 23009-1 第二版 5.6 节和 ETSI TS 103 285 V1.2.1 第 10.8.2.1 节。 */
@UnstableApi
public final class BaseUrl {

  /** 默认权重。 */
  public static final int DEFAULT_WEIGHT = 1;

  /** 默认优先级。 */
  public static final int DEFAULT_DVB_PRIORITY = 1;

  /** 表示未设置优先级的常量，适用于未声明 DVB 配置文件的清单。 */
  public static final int PRIORITY_UNSET = Integer.MIN_VALUE;

  /** URL。 */
  public final String url;

  /** 服务位置。 */
  public final String serviceLocation;

  /** 优先级。 */
  public final int priority;

  /** 权重。 */
  public final int weight;

  /**
   * 创建一个实例，使用 {@link #PRIORITY_UNSET 未设置的优先级}、{@link #DEFAULT_WEIGHT 默认权重}，
   * 并将 URL 作为服务位置。
   */
  public BaseUrl(String url) {
    this(url, /* serviceLocation= */ url, PRIORITY_UNSET, DEFAULT_WEIGHT);
  }

  /** 创建一个实例。 */
  public BaseUrl(String url, String serviceLocation, int priority, int weight) {
    this.url = url;
    this.serviceLocation = serviceLocation;
    this.priority = priority;
    this.weight = weight;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BaseUrl)) {
      return false;
    }
    BaseUrl baseUrl = (BaseUrl) o;
    return priority == baseUrl.priority
        && weight == baseUrl.weight
        && Objects.equal(url, baseUrl.url)
        && Objects.equal(serviceLocation, baseUrl.serviceLocation);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(url, serviceLocation, priority, weight);
  }
}