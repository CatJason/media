package androidx.media3.exoplayer.dash.manifest;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/** 描述符，定义见 ISO 23009-1 第二版 5.8.2 节。 */
@UnstableApi
public final class Descriptor {

  /** 方案的 URI。 */
  public final String schemeIdUri;

  /** 值，可能为 null。 */
  @Nullable public final String value;

  /** 标识符，可能为 null。 */
  @Nullable public final String id;

  /**
   * 构造一个描述符实例。
   *
   * @param schemeIdUri 方案的 URI。
   * @param value 值，可能为 null。
   * @param id 标识符，可能为 null。
   */
  public Descriptor(String schemeIdUri, @Nullable String value, @Nullable String id) {
    this.schemeIdUri = schemeIdUri;
    this.value = value;
    this.id = id;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Descriptor other = (Descriptor) obj;
    return Util.areEqual(schemeIdUri, other.schemeIdUri)
        && Util.areEqual(value, other.value)
        && Util.areEqual(id, other.id);
  }

  @Override
  public int hashCode() {
    int result = schemeIdUri.hashCode();
    result = 31 * result + (value != null ? value.hashCode() : 0);
    result = 31 * result + (id != null ? id.hashCode() : 0);
    return result;
  }
}