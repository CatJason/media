package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/** 用于标识 {@link Format} 的标签。 */
@UnstableApi
public class Label {
  /**
   * 标签的语言，符合 IETF BCP 47 标准的标签，如果未知或不适用则为 null。
   */
  @Nullable public final String language;

  /** 标签的值。 */
  public final String value;

  /**
   * 创建一个标签。
   *
   * @param language 标签的语言，符合 IETF BCP 47 标准的标签，如果未知或不适用则为 null。
   * @param value 标签的值。
   */
  public Label(@Nullable String language, String value) {
    this.language = Util.normalizeLanguageCode(language);
    this.value = value;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Label label = (Label) o;
    return Util.areEqual(language, label.language) && Util.areEqual(value, label.value);
  }

  @Override
  public int hashCode() {
    int result = value.hashCode();
    result = 31 * result + (language != null ? language.hashCode() : 0);
    return result;
  }

  private static final String FIELD_LANGUAGE_INDEX = Util.intToStringMaxRadix(0); // 语言字段的标识符
  private static final String FIELD_VALUE_INDEX = Util.intToStringMaxRadix(1); // 值字段的标识符

  /** 将此实例序列化为 {@link Bundle}。 */
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    if (language != null) {
      bundle.putString(FIELD_LANGUAGE_INDEX, language); // 添加语言字段
    }
    bundle.putString(FIELD_VALUE_INDEX, value); // 添加值字段
    return bundle;
  }

  /** 从 {@link #toBundle()} 生成的 {@link Bundle} 中反序列化一个实例。 */
  public static Label fromBundle(Bundle bundle) {
    return new Label(
        bundle.getString(FIELD_LANGUAGE_INDEX), checkNotNull(bundle.getString(FIELD_VALUE_INDEX)));
  }
}