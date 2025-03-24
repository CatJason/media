package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.os.Bundle;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import androidx.media3.common.util.BundleCollectionUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 表示媒体流中可用的一组不可变的轨道。组中的所有轨道呈现相同的内容，但它们的格式可能不同。
 *
 * <p>作为轨道如何分组的示例，考虑一个自适应播放场景，其中主视频流提供了五种分辨率，而替代视频流（例如，体育比赛中的不同摄像机角度）提供了两种分辨率。在这种情况下，将有两个视频轨道组，一个对应于主视频流，包含五个轨道，另一个对应于替代视频流，包含两个轨道。
 *
 * <p>请注意，语言不同的音频轨道不会被分组，因为不同语言的内容不被认为是相同的。相反，语言相同但仅在比特率、采样率、声道数等属性上不同的音频轨道可以被分组。这也适用于文本轨道。
 *
 * <p>还要注意，此类仅包含从媒体本身派生的信息。与 {@link Tracks.Group} 不同，它不包含运行时信息，例如设备对每个轨道的播放支持程度，或当前选择了哪些轨道。
 */
public final class TrackGroup {

  private static final String TAG = "TrackGroup";

  /** 组中轨道的数量。 */
  @UnstableApi public final int length;

  /** 轨道组的标识符。 */
  @UnstableApi public final String id;

  /** 组中轨道的类型。 */
  @UnstableApi public final @C.TrackType int type;

  private final Format[] formats;

  // 延迟初始化的哈希码。
  private int hashCode;

  /**
   * 构造一个包含提供的 {@code formats} 的轨道组。
   *
   * @param formats {@link Format} 的列表。不能为空。
   */
  @UnstableApi
  public TrackGroup(Format... formats) {
    this(/* id= */ "", formats);
  }

  /**
   * 使用提供的 {@code id} 和 {@code formats} 构造一个轨道组。
   *
   * @param id 轨道组的标识符。可以是空字符串。
   * @param formats {@link Format} 的列表。不能为空。
   */
  @UnstableApi
  public TrackGroup(String id, Format... formats) {
    checkArgument(formats.length > 0);
    this.id = id;
    this.formats = formats;
    this.length = formats.length;
    @C.TrackType int type = MimeTypes.getTrackType(formats[0].sampleMimeType);
    if (type == C.TRACK_TYPE_UNKNOWN) {
      type = MimeTypes.getTrackType(formats[0].containerMimeType);
    }
    this.type = type;
    verifyCorrectness();
  }

  /**
   * 返回一个具有指定 {@code id} 的此轨道组的副本。
   *
   * @param id 轨道组副本的标识符。
   * @return 复制的轨道组。
   */
  @UnstableApi
  @CheckResult
  public TrackGroup copyWithId(String id) {
    return new TrackGroup(id, formats);
  }

  /**
   * 返回给定索引处轨道的格式。
   *
   * @param index 轨道的索引。
   * @return 轨道的格式。
   */
  @UnstableApi
  public Format getFormat(int index) {
    return formats[index];
  }

  /**
   * 返回具有给定格式的轨道在组中的索引。格式通过身份定位，因此，例如，即使多个轨道具有包含相同值的格式，{@code group.indexOf(group.getFormat(index)) == index} 也成立。
   *
   * @param format 格式。
   * @return 轨道的索引，如果不存在这样的轨道，则返回 {@link C#INDEX_UNSET}。
   */
  @SuppressWarnings("ReferenceEquality")
  @UnstableApi
  public int indexOf(Format format) {
    for (int i = 0; i < formats.length; i++) {
      if (format == formats[i]) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + id.hashCode();
      result = 31 * result + Arrays.hashCode(formats);
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
    TrackGroup other = (TrackGroup) obj;
    return id.equals(other.id) && Arrays.equals(formats, other.formats);
  }

  private static final String FIELD_FORMATS = Util.intToStringMaxRadix(0);
  private static final String FIELD_ID = Util.intToStringMaxRadix(1);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    ArrayList<Bundle> arrayList = new ArrayList<>(formats.length);
    for (Format format : formats) {
      arrayList.add(format.toBundle(/* excludeMetadata= */ true));
    }
    bundle.putParcelableArrayList(FIELD_FORMATS, arrayList);
    bundle.putString(FIELD_ID, id);
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复一个 {@code TrackGroup}。 */
  @UnstableApi
  public static TrackGroup fromBundle(Bundle bundle) {
    @Nullable List<Bundle> formatBundles = bundle.getParcelableArrayList(FIELD_FORMATS);
    List<Format> formats =
        formatBundles == null
            ? ImmutableList.of()
            : BundleCollectionUtil.fromBundleList(Format::fromBundle, formatBundles);
    String id = bundle.getString(FIELD_ID, /* defaultValue= */ "");
    return new TrackGroup(id, formats.toArray(new Format[0]));
  }

  private void verifyCorrectness() {
    // TrackGroups 应仅包含内容完全相同但质量不同的轨道。我们只记录错误而不抛出异常，以避免破坏那些由于偶然情况（例如，自适应选择始终被禁用）而工作的情况的向后兼容性。
    String language = normalizeLanguage(formats[0].language);
    @C.RoleFlags int roleFlags = normalizeRoleFlags(formats[0].roleFlags);
    for (int i = 1; i < formats.length; i++) {
      if (!language.equals(normalizeLanguage(formats[i].language))) {
        logErrorMessage(
            /* mismatchField= */ "languages",
            /* valueIndex0= */ formats[0].language,
            /* otherValue=* */ formats[i].language,
            /* otherIndex= */ i);
        return;
      }
      if (roleFlags != normalizeRoleFlags(formats[i].roleFlags)) {
        logErrorMessage(
            /* mismatchField= */ "role flags",
            /* valueIndex0= */ Integer.toBinaryString(formats[0].roleFlags),
            /* otherValue=* */ Integer.toBinaryString(formats[i].roleFlags),
            /* otherIndex= */ i);
        return;
      }
    }
  }

  private static String normalizeLanguage(@Nullable String language) {
    // 将所有未确定或未知语言的变体视为兼容。
    return language == null || language.equals(C.LANGUAGE_UNDETERMINED) ? "" : language;
  }

  private static @C.RoleFlags int normalizeRoleFlags(@C.RoleFlags int roleFlags) {
    // 将 trick-play 和非 trick-play 格式视为兼容。
    return roleFlags | C.ROLE_FLAG_TRICK_PLAY;
  }

  private static void logErrorMessage(
      String mismatchField,
      @Nullable String valueIndex0,
      @Nullable String otherValue,
      int otherIndex) {
    Log.e(
        TAG,
        "",
        new IllegalStateException(
            "不同的 "
                + mismatchField
                + " 组合在一个 TrackGroup 中：'"
                + valueIndex0
                + "'（轨道 0）和 '"
                + otherValue
                + "'（轨道 "
                + otherIndex
                + ")"));
  }
}