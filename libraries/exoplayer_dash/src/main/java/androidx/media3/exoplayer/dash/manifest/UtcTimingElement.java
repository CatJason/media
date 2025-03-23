package androidx.media3.exoplayer.dash.manifest;

import androidx.media3.common.util.UnstableApi;

/** 表示一个 UTCTiming 元素。 */
@UnstableApi
public final class UtcTimingElement {

  /** 方案的 URI。 */
  public final String schemeIdUri;

  /** 值。 */
  public final String value;

  /**
   * 构造一个 UtcTimingElement 实例。
   *
   * @param schemeIdUri 方案的 URI。
   * @param value 值。
   */
  public UtcTimingElement(String schemeIdUri, String value) {
    this.schemeIdUri = schemeIdUri;
    this.value = value;
  }

  @Override
  public String toString() {
    return schemeIdUri + ", " + value;
  }
}