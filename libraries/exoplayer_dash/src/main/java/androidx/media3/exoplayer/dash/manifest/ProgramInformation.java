package androidx.media3.exoplayer.dash.manifest;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/** 解析后的节目信息元素。 */
@UnstableApi
public final class ProgramInformation {
  /** 媒体呈现的标题。 */
  @Nullable public final String title;

  /** 媒体呈现的原始来源信息。 */
  @Nullable public final String source;

  /** 媒体呈现的版权声明。 */
  @Nullable public final String copyright;

  /** 提供有关媒体呈现更多信息的 URL。 */
  @Nullable public final String moreInformationURL;

  /** 声明此节目信息的语言代码。 */
  @Nullable public final String lang;

  public ProgramInformation(
      @Nullable String title,
      @Nullable String source,
      @Nullable String copyright,
      @Nullable String moreInformationURL,
      @Nullable String lang) {
    this.title = title;
    this.source = source;
    this.copyright = copyright;
    this.moreInformationURL = moreInformationURL;
    this.lang = lang;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ProgramInformation)) {
      return false;
    }
    ProgramInformation other = (ProgramInformation) obj;
    return Util.areEqual(this.title, other.title)
        && Util.areEqual(this.source, other.source)
        && Util.areEqual(this.copyright, other.copyright)
        && Util.areEqual(this.moreInformationURL, other.moreInformationURL)
        && Util.areEqual(this.lang, other.lang);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (title != null ? title.hashCode() : 0);
    result = 31 * result + (source != null ? source.hashCode() : 0);
    result = 31 * result + (copyright != null ? copyright.hashCode() : 0);
    result = 31 * result + (moreInformationURL != null ? moreInformationURL.hashCode() : 0);
    result = 31 * result + (lang != null ? lang.hashCode() : 0);
    return result;
  }
}
