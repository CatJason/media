package androidx.media3.common.text;

import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.BundleCollectionUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/** 用于表示特定时间点活动 {@link Cue} 状态的类。 */
public final class CueGroup {

  /** 一个空组，没有 {@link Cue}，且展示时间为零。 */
  @UnstableApi
  public static final CueGroup EMPTY_TIME_ZERO =
      new CueGroup(ImmutableList.of(), /* presentationTimeUs= */ 0);

  /**
   * 该组中的 {@link Cue}。
   *
   * <p>此列表按优先级升序排列。如果显示的 {@link Cue} 框有重叠，则列表中靠后的 {@link Cue} 应显示在顶部。
   *
   * <p>如果该组表示没有 {@link Cue} 的状态，则此列表可能为空。
   */
  public final ImmutableList<Cue> cues;

  /**
   * {@link #cues} 的展示时间，单位为微秒。
   *
   * <p>此时间是相对于当前 {@link Timeline.Period} 开始时间的偏移量。
   */
  @UnstableApi public final long presentationTimeUs;

  /** 创建一个 CueGroup。 */
  @UnstableApi
  public CueGroup(List<Cue> cues, long presentationTimeUs) {
    this.cues = ImmutableList.copyOf(cues);
    this.presentationTimeUs = presentationTimeUs;
  }

  private static final String FIELD_CUES = Util.intToStringMaxRadix(0);
  private static final String FIELD_PRESENTATION_TIME_US = Util.intToStringMaxRadix(1);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putParcelableArrayList(
        FIELD_CUES,
        BundleCollectionUtil.toBundleArrayList(
            filterOutBitmapCues(cues), Cue::toBinderBasedBundle));
    bundle.putLong(FIELD_PRESENTATION_TIME_US, presentationTimeUs);
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复一个 {@code final CueGroup}。 */
  @UnstableApi
  public static CueGroup fromBundle(Bundle bundle) {
    @Nullable ArrayList<Bundle> cueBundles = bundle.getParcelableArrayList(FIELD_CUES);
    List<Cue> cues =
        cueBundles == null
            ? ImmutableList.of()
            : BundleCollectionUtil.fromBundleList(Cue::fromBundle, cueBundles);
    long presentationTimeUs = bundle.getLong(FIELD_PRESENTATION_TIME_US);
    return new CueGroup(cues, presentationTimeUs);
  }

  /**
   * 过滤掉包含 {@link Bitmap} 的 {@link Cue} 对象。在进程间传输 {@link Cue} 时使用，以防止传输过多数据。
   */
  private static ImmutableList<Cue> filterOutBitmapCues(List<Cue> cues) {
    ImmutableList.Builder<Cue> builder = ImmutableList.builder();
    for (int i = 0; i < cues.size(); i++) {
      if (cues.get(i).bitmap != null) {
        continue;
      }
      builder.add(cues.get(i));
    }
    return builder.build();
  }
}