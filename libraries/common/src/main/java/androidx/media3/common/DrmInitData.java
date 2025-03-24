package androidx.media3.common;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import androidx.media3.common.DrmInitData.SchemeData;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
/** 用于一个或多个 DRM 方案的初始化数据。 */
@UnstableApi
public final class DrmInitData implements Comparator<SchemeData>, Parcelable {

  /**
   * 合并从媒体清单和媒体流中获取的 {@link DrmInitData}。
   *
   * <p>结果按以下方式生成：
   *
   * <ol>
   *   <li>包含来自 {@code manifestData} 的所有 {@link SchemeData}，其中 {@link SchemeData#hasData()} 为 true。
   *   <li>包含来自 {@code mediaData} 的所有 {@link SchemeData}，其中 {@link SchemeData#hasData()} 为 true，并且我们没有从清单中包含针对相同 UUID 的条目。
   *   <li>如果可用，则使用清单中的方案类型。如果不可用，则使用媒体中的方案类型。
   * </ol>
   *
   * @param manifestData 从清单中获取的 DRM 会话获取数据。
   * @param mediaData 从媒体中获取的 DRM 会话获取数据。
   * @return 通过合并媒体清单和媒体流获取的 {@link DrmInitData}。
   */
  @Nullable
  public static DrmInitData createSessionCreationData(
      @Nullable DrmInitData manifestData, @Nullable DrmInitData mediaData) {
    ArrayList<SchemeData> result = new ArrayList<>();
    String schemeType = null;
    if (manifestData != null) {
      schemeType = manifestData.schemeType;
      for (SchemeData data : manifestData.schemeDatas) {
        if (data.hasData()) {
          result.add(data);
        }
      }
    }

    if (mediaData != null) {
      if (schemeType == null) {
        schemeType = mediaData.schemeType;
      }
      int manifestDatasCount = result.size();
      for (SchemeData data : mediaData.schemeDatas) {
        if (data.hasData() && !containsSchemeDataWithUuid(result, manifestDatasCount, data.uuid)) {
          result.add(data);
        }
      }
    }

    return result.isEmpty() ? null : new DrmInitData(schemeType, result);
  }

  private final SchemeData[] schemeDatas;

  // Lazily initialized hashcode.
  private int hashCode;
  /** 保护方案类型，如果不适用或未知则为 null。 */
  @Nullable public final String schemeType;

  /** {@link SchemeData} 的数量。 */
  public final int schemeDataCount;

  /**
   * @param schemeDatas 可能用于多个 DRM 方案的初始化数据。
   */
  public DrmInitData(List<SchemeData> schemeDatas) {
    this(null, false, schemeDatas.toArray(new SchemeData[0]));
  }

  /**
   * @param schemeType 参见 {@link #schemeType}。
   * @param schemeDatas 可能用于多个 DRM 方案的初始化数据。
   */
  public DrmInitData(@Nullable String schemeType, List<SchemeData> schemeDatas) {
    this(schemeType, false, schemeDatas.toArray(new SchemeData[0]));
  }

  /**
   * @param schemeDatas 可能用于多个 DRM 方案的初始化数据。
   */
  public DrmInitData(SchemeData... schemeDatas) {
    this(null, schemeDatas);
  }

  /**
   * @param schemeType 参见 {@link #schemeType}。
   * @param schemeDatas 可能用于多个 DRM 方案的初始化数据。
   */
  public DrmInitData(@Nullable String schemeType, SchemeData... schemeDatas) {
    this(schemeType, true, schemeDatas);
  }

  private DrmInitData(
      @Nullable String schemeType, boolean cloneSchemeDatas, SchemeData... schemeDatas) {
    this.schemeType = schemeType;
    if (cloneSchemeDatas) {
      schemeDatas = schemeDatas.clone();
    }
    this.schemeDatas = schemeDatas;
    schemeDataCount = schemeDatas.length;
    // Sorting ensures that universal scheme data (i.e. data that applies to all schemes) is matched
    // last. It's also required by the equals and hashcode implementations.
    Arrays.sort(this.schemeDatas, this);
  }

  /* package */ DrmInitData(Parcel in) {
    schemeType = in.readString();
    schemeDatas = Util.castNonNull(in.createTypedArray(SchemeData.CREATOR));
    schemeDataCount = schemeDatas.length;
  }

  /**
   * 检索给定索引处的 {@link SchemeData}。
   *
   * @param index 要返回的方案索引。不能超过 {@link #schemeDataCount}。
   * @return 指定索引处的 {@link SchemeData}。
   */
  public SchemeData get(int index) {
    return schemeDatas[index];
  }

  /**
   * 返回具有指定保护方案类型的副本。
   *
   * @param schemeType 保护方案类型。可以为 null。
   * @return 具有指定保护方案类型的副本。
   */
  @CheckResult
  public DrmInitData copyWithSchemeType(@Nullable String schemeType) {
    if (Util.areEqual(this.schemeType, schemeType)) {
      return this;
    }
    return new DrmInitData(schemeType, false, schemeDatas);
  }

  /**
   * 返回一个包含来自当前实例和 {@code other} 的 {@link #schemeDatas} 的实例。被合并的实例的 {@link #schemeType} 必须匹配，或者至少一个方案类型为 {@code null}。
   *
   * @param drmInitData 要合并的实例。
   * @return 合并后的结果。
   */
  public DrmInitData merge(DrmInitData drmInitData) {
    Assertions.checkState(
        schemeType == null
            || drmInitData.schemeType == null
            || TextUtils.equals(schemeType, drmInitData.schemeType));
    String mergedSchemeType = schemeType != null ? this.schemeType : drmInitData.schemeType;
    SchemeData[] mergedSchemeDatas =
        Util.nullSafeArrayConcatenation(schemeDatas, drmInitData.schemeDatas);
    return new DrmInitData(mergedSchemeType, mergedSchemeDatas);
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = (schemeType == null ? 0 : schemeType.hashCode());
      result = 31 * result + Arrays.hashCode(schemeDatas);
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
    DrmInitData other = (DrmInitData) obj;
    return Util.areEqual(schemeType, other.schemeType)
        && Arrays.equals(schemeDatas, other.schemeDatas);
  }

  @Override
  public int compare(SchemeData first, SchemeData second) {
    return C.UUID_NIL.equals(first.uuid)
        ? (C.UUID_NIL.equals(second.uuid) ? 0 : 1)
        : first.uuid.compareTo(second.uuid);
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(schemeType);
    dest.writeTypedArray(schemeDatas, 0);
  }

  public static final Parcelable.Creator<DrmInitData> CREATOR =
      new Parcelable.Creator<DrmInitData>() {

        @Override
        public DrmInitData createFromParcel(Parcel in) {
          return new DrmInitData(in);
        }

        @Override
        public DrmInitData[] newArray(int size) {
          return new DrmInitData[size];
        }
      };

  // Internal methods.

  private static boolean containsSchemeDataWithUuid(
      ArrayList<SchemeData> datas, int limit, UUID uuid) {
    for (int i = 0; i < limit; i++) {
      if (datas.get(i).uuid.equals(uuid)) {
        return true;
      }
    }
    return false;
  }
  /** 方案初始化数据。 */
  public static final class SchemeData implements Parcelable {

    // 延迟初始化的哈希码。
    private int hashCode;

    /**
     * DRM 方案的 {@link UUID}，如果数据是通用的（即适用于所有方案），则为 {@link C#UUID_NIL}。
     */
    public final UUID uuid;

    /** 许可证请求应发送到的服务器 URL。如果未知，则可能为 null。 */
    @Nullable public final String licenseServerUrl;

    /** {@link #data} 的 MIME 类型。 */
    public final String mimeType;

    /** 初始化数据。如果仅用于方案支持检查，则可能为 null。 */
    @Nullable public final byte[] data;

    /**
     * @param uuid DRM 方案的 {@link UUID}，如果数据是通用的（即适用于所有方案），则为 {@link C#UUID_NIL}。
     * @param mimeType 参见 {@link #mimeType}。
     * @param data 参见 {@link #data}。
     */
    public SchemeData(UUID uuid, String mimeType, @Nullable byte[] data) {
      this(uuid, /* licenseServerUrl= */ null, mimeType, data);
    }

    /**
     * @param uuid DRM 方案的 {@link UUID}，如果数据是通用的（即适用于所有方案），则为 {@link C#UUID_NIL}。
     * @param licenseServerUrl 参见 {@link #licenseServerUrl}。
     * @param mimeType 参见 {@link #mimeType}。
     * @param data 参见 {@link #data}。
     */
    public SchemeData(
        UUID uuid, @Nullable String licenseServerUrl, String mimeType, @Nullable byte[] data) {
      this.uuid = Assertions.checkNotNull(uuid);
      this.licenseServerUrl = licenseServerUrl;
      this.mimeType = MimeTypes.normalizeMimeType(Assertions.checkNotNull(mimeType));
      this.data = data;
    }

    /* package */ SchemeData(Parcel in) {
      uuid = new UUID(in.readLong(), in.readLong());
      licenseServerUrl = in.readString();
      mimeType = Util.castNonNull(in.readString());
      data = in.createByteArray();
    }

    /**
     * 返回此初始化数据是否适用于指定的方案。
     *
     * @param schemeUuid 方案的 {@link UUID}。
     * @return 此初始化数据是否适用于指定的方案。
     */
    public boolean matches(UUID schemeUuid) {
      return C.UUID_NIL.equals(uuid) || schemeUuid.equals(uuid);
    }

    /**
     * 返回此 {@link SchemeData} 是否可以用于替换 {@code other}。
     *
     * @param other 一个 {@link SchemeData}。
     * @return 此 {@link SchemeData} 是否可以用于替换 {@code other}。
     */
    public boolean canReplace(SchemeData other) {
      return hasData() && !other.hasData() && matches(other.uuid);
    }

    /** 返回 {@link #data} 是否为非 null。 */
    public boolean hasData() {
      return data != null;
    }

    /**
     * 返回具有指定数据的此实例的副本。
     *
     * @param data 要包含在副本中的数据。
     * @return 新实例。
     */
    @CheckResult
    public SchemeData copyWithData(@Nullable byte[] data) {
      return new SchemeData(uuid, licenseServerUrl, mimeType, data);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (!(obj instanceof SchemeData)) {
        return false;
      }
      if (obj == this) {
        return true;
      }
      SchemeData other = (SchemeData) obj;
      return Util.areEqual(licenseServerUrl, other.licenseServerUrl)
          && Util.areEqual(mimeType, other.mimeType)
          && Util.areEqual(uuid, other.uuid)
          && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
      if (hashCode == 0) {
        int result = uuid.hashCode();
        result = 31 * result + (licenseServerUrl == null ? 0 : licenseServerUrl.hashCode());
        result = 31 * result + mimeType.hashCode();
        result = 31 * result + Arrays.hashCode(data);
        hashCode = result;
      }
      return hashCode;
    }

    // Parcelable implementation.

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeLong(uuid.getMostSignificantBits());
      dest.writeLong(uuid.getLeastSignificantBits());
      dest.writeString(licenseServerUrl);
      dest.writeString(mimeType);
      dest.writeByteArray(data);
    }

    public static final Parcelable.Creator<SchemeData> CREATOR =
        new Parcelable.Creator<SchemeData>() {

          @Override
          public SchemeData createFromParcel(Parcel in) {
            return new SchemeData(in);
          }

          @Override
          public SchemeData[] newArray(int size) {
            return new SchemeData[size];
          }
        };
  }
}
