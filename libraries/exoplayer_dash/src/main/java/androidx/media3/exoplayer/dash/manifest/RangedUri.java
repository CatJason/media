package androidx.media3.exoplayer.dash.manifest;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.UriUtil;

/** 定义位于引用 URI 上的数据范围。 */
@UnstableApi
public final class RangedUri {

  /** 范围的第一个字节的（从零开始的）索引。 */
  public final long start;

  /** 范围的长度，或 {@link C#LENGTH_UNSET} 表示范围是无限的。 */
  public final long length;

  private final String referenceUri;

  private int hashCode;

  /**
   * 构造一个范围 URI。
   *
   * @param referenceUri 引用 URI。
   * @param start 范围的第一个字节的（从零开始的）索引。
   * @param length 范围的长度，或 {@link C#LENGTH_UNSET} 表示范围是无限的。
   */
  public RangedUri(@Nullable String referenceUri, long start, long length) {
    this.referenceUri = referenceUri == null ? "" : referenceUri;
    this.start = start;
    this.length = length;
  }

  /**
   * 返回实例表示的解析后的 {@link Uri}。
   *
   * @param baseUri 基础 URI。
   * @return 实例表示的 {@link Uri}。
   */
  public Uri resolveUri(String baseUri) {
    return UriUtil.resolveToUri(baseUri, referenceUri);
  }

  /**
   * 返回实例表示的解析后的 URI 字符串。
   *
   * @param baseUri 基础 URI。
   * @return 实例表示的 URI。
   */
  public String resolveUriString(String baseUri) {
    return UriUtil.resolve(baseUri, referenceUri);
  }

  /**
   * 尝试将此 {@link RangedUri} 与另一个 {@link RangedUri} 和可选的基础 URI 合并。
   *
   * <p>如果两个实例在解析基础 URI 后定义相同的 {@link Uri}，并且一个实例的起始字节是另一个实例的结束字节的下一个字节，
   * 形成一个连续的区域且没有重叠，则合并成功。
   *
   * <p>如果 {@code other} 为 null，则合并失败，返回 null。
   *
   * @param other 要合并的 {@link RangedUri}。
   * @param baseUri 基础 URI。
   * @return 合并成功时返回合并后的 {@link RangedUri}，否则返回 null。
   */
  @Nullable
  public RangedUri attemptMerge(@Nullable RangedUri other, String baseUri) {
    final String resolvedUri = resolveUriString(baseUri);
    if (other == null || !resolvedUri.equals(other.resolveUriString(baseUri))) {
      return null;
    } else if (length != C.LENGTH_UNSET && start + length == other.start) {
      return new RangedUri(
          resolvedUri,
          start,
          other.length == C.LENGTH_UNSET ? C.LENGTH_UNSET : length + other.length);
    } else if (other.length != C.LENGTH_UNSET && other.start + other.length == start) {
      return new RangedUri(
          resolvedUri,
          other.start,
          length == C.LENGTH_UNSET ? C.LENGTH_UNSET : other.length + length);
    } else {
      return null;
    }
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + (int) start;
      result = 31 * result + (int) length;
      result = 31 * result + referenceUri.hashCode();
      hashCode = result;
    }
    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RangedUri other = (RangedUri) obj;
    return this.start == other.start
        && this.length == other.length
        && referenceUri.equals(other.referenceUri);
  }

  @Override
  public String toString() {
    return "RangedUri("
        + "referenceUri="
        + referenceUri
        + ", start="
        + start
        + ", length="
        + length
        + ")";
  }
}