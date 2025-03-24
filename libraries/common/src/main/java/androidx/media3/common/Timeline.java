package androidx.media3.common;

import static androidx.media3.common.AdPlaybackState.AD_STATE_UNAVAILABLE;
import static androidx.media3.common.util.Assertions.checkArgument;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.BundleCollectionUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.InlineMe;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * 媒体结构的灵活表示。Timeline 能够表示各种媒体的结构，从简单的单个媒体文件到复杂的媒体组合（如播放列表和插入广告的流媒体）。实例是不可变的。对于动态变化的媒体（例如直播流），Timeline 提供了当前状态的快照。
 *
 * <p>Timeline 由 {@link Window 窗口} 和 {@link Period 时间段} 组成。
 *
 * <ul>
 *   <li>{@link Window} 通常对应于一个播放列表项。它可以跨越一个或多个时间段，并定义这些时间段中当前可用于播放的区域。窗口还提供其他信息，例如是否支持在窗口内进行搜索，以及默认位置（即播放器开始播放窗口时的起始位置）。
 *   <li>{@link Period} 定义一个单独的媒体逻辑片段，例如一个媒体文件。它还可以定义插入到媒体中的广告组，以及这些广告是否已加载和播放的信息。
 * </ul>
 *
 * <p>以下示例说明了各种用例的 Timeline。
 *
 * <h2 id="single-file">单个媒体文件或点播流</h2>
 *
 * <p style="align:center"><img
 * src="https://developer.android.com/static/images/reference/androidx/media3/common/timeline-single-file.svg"
 * alt="单个文件的示例 Timeline">
 *
 * <p>单个媒体文件或点播流的 Timeline 由单个时间段和窗口组成。窗口跨越整个时间段，表示媒体的所有部分都可用于播放。窗口的默认位置通常位于时间段的开头（如上图中的黑点所示）。
 *
 * <h2>媒体文件或点播流的播放列表</h2>
 *
 * <p style="align:center"><img
 * src="https://developer.android.com/static/images/reference/androidx/media3/common/timeline-playlist.svg"
 * alt="播放列表文件的示例 Timeline">
 *
 * <p>媒体文件或点播流的播放列表的 Timeline 由多个时间段组成，每个时间段都有自己的窗口。每个窗口跨越相应时间段的全部区域，默认位置通常位于时间段的开头。时间段和窗口的属性（例如它们的持续时间以及窗口是否可搜索）通常只有在播放器开始缓冲相应的文件或流时才会被知晓。
 *
 * <h2 id="live-limited">有限可用性的直播流</h2>
 *
 * <p style="align:center"><img
 * src="https://developer.android.com/static/images/reference/androidx/media3/common/timeline-live-limited.svg"
 * alt="有限可用性直播流的示例 Timeline">
 *
 * <p>直播流的 Timeline 由一个持续时间未知的时间段组成，因为随着更多内容的广播，它会不断扩展。如果内容仅在有限的时间内可用，则窗口可能从非零位置开始，定义仍可播放的内容区域。窗口将从 {@link Window#isLive()} 返回 true 以指示它是直播流，并且只要预期直播窗口会发生变化，{@link Window#isDynamic} 将设置为 true。其默认位置通常靠近直播边缘（如上图中的黑点所示）。
 *
 * <h2>无限可用性的直播流</h2>
 *
 * <p style="align:center"><img
 * src="https://developer.android.com/static/images/reference/androidx/media3/common/timeline-live-indefinite.svg"
 * alt="无限可用性直播流的示例 Timeline">
 *
 * <p>无限可用性的直播流的 Timeline 类似于 <a href="#live-limited">有限可用性的直播流</a> 的情况，不同之处在于窗口从时间段的开头开始，以指示所有之前广播的内容仍可播放。
 *
 * <h2 id="live-multi-period">多时间段的直播流</h2>
 *
 * <p style="align:center"><img
 * src="https://developer.android.com/static/images/reference/androidx/media3/common/timeline-live-multi-period.svg"
 * alt="多时间段直播流的示例 Timeline">
 *
 * <p>当直播流被明确划分为多个时间段时（例如在内容边界处），会出现这种情况。这种情况类似于 <a href="#live-limited">有限可用性的直播流</a> 的情况，不同之处在于窗口可能跨越多个时间段。在无限可用性的情况下，也可能存在多个时间段。
 *
 * <h2>点播流后接直播流</h2>
 *
 * <p style="align:center"><img
 * src="https://developer.android.com/static/images/reference/androidx/media3/common/timeline-advanced.svg"
 * alt="点播流后接直播流的示例 Timeline">
 *
 * <p>这种情况是 <a href="#single-file">单个媒体文件或点播流</a> 和 <a href="#multi-period">多时间段的直播流</a> 情况的结合。当点播流播放结束时，将从直播流的默认位置（靠近直播边缘）开始播放。
 *
 * <h2 id="single-file-midrolls">带有中插广告的点播流</h2>
 *
 * <p style="align:center"><img
 * src="https://developer.android.com/static/images/reference/androidx/media3/common/timeline-single-file-midrolls.svg"
 * alt="带有中插广告组的点播流的示例 Timeline">
 *
 * <p>这种情况包括中插广告组，它们被定义为 Timeline 的单个时间段的一部分。可以查询时间段以获取有关广告组及其包含的广告的信息。
 */
public abstract class Timeline {

  /**
   * 保存 {@link Timeline} 中窗口的信息。窗口通常对应于一个播放列表项，并定义当前可用于播放的媒体区域以及附加信息（例如是否支持在窗口内进行搜索）。
   *下图显示了窗口定义的一些信息，以及这些信息如何与时间线中对应的 {@link Period 时间段} 相关联。
   *
   * <p style="align:center"><img
   * src="https://developer.android.com/static/images/reference/androidx/media3/common/timeline-window.svg"
   * alt="时间线窗口定义的信息">
   */
  public static final class Window {

    /**
     * A {@link #uid} for a window that must be used for single-window {@link Timeline Timelines}.
     */
    public static final Object SINGLE_WINDOW_UID = new Object();

    private static final Object FAKE_WINDOW_UID = new Object();

    private static final MediaItem PLACEHOLDER_MEDIA_ITEM =
        new MediaItem.Builder()
            .setMediaId("androidx.media3.common.Timeline")
            .setUri(Uri.EMPTY)
            .build();

    /**
     * 窗口的唯一标识符。单窗口的 {@link Timeline} 必须使用 {@link #SINGLE_WINDOW_UID}。
     */
    public Object uid;

    /**
     * @deprecated 请改用 {@link #mediaItem}。
     */
    @UnstableApi @Deprecated @Nullable public Object tag;

    /** 与窗口关联的 {@link MediaItem}。不一定是唯一的。 */
    public MediaItem mediaItem;

    /** 窗口的清单。可能为 {@code null}。 */
    @Nullable public Object manifest;

    /**
     * 此窗口所属的演示的开始时间，以 Unix 纪元以来的毫秒数表示，如果未知或不适用，则为 {@link C#TIME_UNSET}。仅用于信息目的。
     */
    public long presentationStartTimeMs;

    /**
     * 窗口的开始时间，以 Unix 纪元以来的毫秒数表示，如果未知或不适用，则为 {@link C#TIME_UNSET}。
     */
    public long windowStartTimeMs;

    /**
     * {@link SystemClock#elapsedRealtime()} 与媒体源服务器时钟的 Unix 纪元时间之间的偏移量，如果未知或不适用，则为 {@link C#TIME_UNSET}。
     *
     * <p>注意，可以使用 {@link #getCurrentUnixTimeMs()} 获取当前的 Unix 时间，其计算公式为 {@code SystemClock.elapsedRealtime() + elapsedRealtimeEpochOffsetMs}。
     */
    public long elapsedRealtimeEpochOffsetMs;

    /** Whether it's possible to seek within this window. */
    public boolean isSeekable;

// TODO: 将此拆分为更详细地描述窗口的哪些部分可能会发生变化。
//  例如，应该可以单独确定窗口的开始位置和结束位置是否可能相对于底层的时间段发生变化。
//  关于了解结束位置是固定的而开始位置可能仍然变化的有用示例，请参见：
//  https://github.com/google/ExoPlayer/issues/4780。
    /** 此窗口在时间线更新时是否可能发生变化。 */
    public boolean isDynamic;
    /**
     * 使用的 {@link MediaItem.LiveConfiguration}，如果 {@link #isLive()} 返回 false，则为 null。
     */
    @Nullable public MediaItem.LiveConfiguration liveConfiguration;

    /**
     * 此窗口是否包含占位符信息，因为实际信息尚未加载。
     */
    public boolean isPlaceholder;

    /**
     * 相对于窗口开始位置的默认播放位置，以微秒为单位。如果且仅当窗口填充了非零的默认位置投影，并且指定的投影在窗口范围内无法执行时，则可能为 {@link C#TIME_UNSET}。
     */
    @UnstableApi public long defaultPositionUs;

    /** 此窗口的持续时间，以微秒为单位，如果未知则为 {@link C#TIME_UNSET}。 */
    @UnstableApi public long durationUs;

    /** 属于此窗口的第一个时间段的索引。 */
    public int firstPeriodIndex;

    /** 属于此窗口的最后一个时间段的索引。 */
    public int lastPeriodIndex;

    /**
     * 此窗口的开始位置相对于属于它的第一个时间段的开始位置的偏移量，以微秒为单位。
     */
    @UnstableApi public long positionInFirstPeriodUs;

    /** Creates window. */
    public Window() {
      uid = SINGLE_WINDOW_UID;
      mediaItem = PLACEHOLDER_MEDIA_ITEM;
    }

    /** Sets the data held by this window. */
    @CanIgnoreReturnValue
    @UnstableApi
    @SuppressWarnings("deprecation")
    public Window set(
        Object uid,
        @Nullable MediaItem mediaItem,
        @Nullable Object manifest,
        long presentationStartTimeMs,
        long windowStartTimeMs,
        long elapsedRealtimeEpochOffsetMs,
        boolean isSeekable,
        boolean isDynamic,
        @Nullable MediaItem.LiveConfiguration liveConfiguration,
        long defaultPositionUs,
        long durationUs,
        int firstPeriodIndex,
        int lastPeriodIndex,
        long positionInFirstPeriodUs) {
      this.uid = uid;
      this.mediaItem = mediaItem != null ? mediaItem : PLACEHOLDER_MEDIA_ITEM;
      this.tag =
          mediaItem != null && mediaItem.localConfiguration != null
              ? mediaItem.localConfiguration.tag
              : null;
      this.manifest = manifest;
      this.presentationStartTimeMs = presentationStartTimeMs;
      this.windowStartTimeMs = windowStartTimeMs;
      this.elapsedRealtimeEpochOffsetMs = elapsedRealtimeEpochOffsetMs;
      this.isSeekable = isSeekable;
      this.isDynamic = isDynamic;
      this.liveConfiguration = liveConfiguration;
      this.defaultPositionUs = defaultPositionUs;
      this.durationUs = durationUs;
      this.firstPeriodIndex = firstPeriodIndex;
      this.lastPeriodIndex = lastPeriodIndex;
      this.positionInFirstPeriodUs = positionInFirstPeriodUs;
      this.isPlaceholder = false;
      return this;
    }
    /**
     * 返回相对于窗口开始位置的默认播放位置，以毫秒为单位。如果且仅当窗口填充了非零的默认位置投影，并且指定的投影在窗口范围内无法执行时，则可能为 {@link C#TIME_UNSET}。
     */
    public long getDefaultPositionMs() {
      return Util.usToMs(defaultPositionUs);
    }

    /**
     * 返回相对于窗口开始位置的默认播放位置，以微秒为单位。如果且仅当窗口填充了非零的默认位置投影，并且指定的投影在窗口范围内无法执行时，则可能为 {@link C#TIME_UNSET}。
     */
    public long getDefaultPositionUs() {
      return defaultPositionUs;
    }

    /** 返回窗口的持续时间，以毫秒为单位，如果未知则为 {@link C#TIME_UNSET}。 */
    public long getDurationMs() {
      return Util.usToMs(durationUs);
    }

    /** 返回窗口的持续时间，以微秒为单位，如果未知则为 {@link C#TIME_UNSET}。 */
    public long getDurationUs() {
      return durationUs;
    }

    /**
     * 返回此窗口的开始位置相对于属于它的第一个时间段的开始位置的偏移量，以毫秒为单位。
     */
    public long getPositionInFirstPeriodMs() {
      return Util.usToMs(positionInFirstPeriodUs);
    }

    /**
     * 返回此窗口的开始位置相对于属于它的第一个时间段的开始位置的偏移量，以微秒为单位。
     */
    public long getPositionInFirstPeriodUs() {
      return positionInFirstPeriodUs;
    }

    /**
     * 返回当前时间，以 Unix 纪元以来的毫秒数表示。
     *
     * <p>此方法应用了媒体提供的 {@link #elapsedRealtimeEpochOffsetMs 已知修正}，使得该时间与媒体源服务器的时钟一致。
     */
    public long getCurrentUnixTimeMs() {
      return Util.getNowUnixTimeMs(elapsedRealtimeEpochOffsetMs);
    }

    /** 返回此窗口是否为直播流。 */
    public boolean isLive() {
      return liveConfiguration != null;
    }

    // Provide backward compatibility for tag.
    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || !getClass().equals(obj.getClass())) {
        return false;
      }
      Window that = (Window) obj;
      return Util.areEqual(uid, that.uid)
          && Util.areEqual(mediaItem, that.mediaItem)
          && Util.areEqual(manifest, that.manifest)
          && Util.areEqual(liveConfiguration, that.liveConfiguration)
          && presentationStartTimeMs == that.presentationStartTimeMs
          && windowStartTimeMs == that.windowStartTimeMs
          && elapsedRealtimeEpochOffsetMs == that.elapsedRealtimeEpochOffsetMs
          && isSeekable == that.isSeekable
          && isDynamic == that.isDynamic
          && isPlaceholder == that.isPlaceholder
          && defaultPositionUs == that.defaultPositionUs
          && durationUs == that.durationUs
          && firstPeriodIndex == that.firstPeriodIndex
          && lastPeriodIndex == that.lastPeriodIndex
          && positionInFirstPeriodUs == that.positionInFirstPeriodUs;
    }

    // Provide backward compatibility for tag.
    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + uid.hashCode();
      result = 31 * result + mediaItem.hashCode();
      result = 31 * result + (manifest == null ? 0 : manifest.hashCode());
      result = 31 * result + (liveConfiguration == null ? 0 : liveConfiguration.hashCode());
      result = 31 * result + (int) (presentationStartTimeMs ^ (presentationStartTimeMs >>> 32));
      result = 31 * result + (int) (windowStartTimeMs ^ (windowStartTimeMs >>> 32));
      result =
          31 * result
              + (int) (elapsedRealtimeEpochOffsetMs ^ (elapsedRealtimeEpochOffsetMs >>> 32));
      result = 31 * result + (isSeekable ? 1 : 0);
      result = 31 * result + (isDynamic ? 1 : 0);
      result = 31 * result + (isPlaceholder ? 1 : 0);
      result = 31 * result + (int) (defaultPositionUs ^ (defaultPositionUs >>> 32));
      result = 31 * result + (int) (durationUs ^ (durationUs >>> 32));
      result = 31 * result + firstPeriodIndex;
      result = 31 * result + lastPeriodIndex;
      result = 31 * result + (int) (positionInFirstPeriodUs ^ (positionInFirstPeriodUs >>> 32));
      return result;
    }

    private static final String FIELD_MEDIA_ITEM = Util.intToStringMaxRadix(1);
    private static final String FIELD_PRESENTATION_START_TIME_MS = Util.intToStringMaxRadix(2);
    private static final String FIELD_WINDOW_START_TIME_MS = Util.intToStringMaxRadix(3);
    private static final String FIELD_ELAPSED_REALTIME_EPOCH_OFFSET_MS =
        Util.intToStringMaxRadix(4);
    private static final String FIELD_IS_SEEKABLE = Util.intToStringMaxRadix(5);
    private static final String FIELD_IS_DYNAMIC = Util.intToStringMaxRadix(6);
    private static final String FIELD_LIVE_CONFIGURATION = Util.intToStringMaxRadix(7);
    private static final String FIELD_IS_PLACEHOLDER = Util.intToStringMaxRadix(8);
    private static final String FIELD_DEFAULT_POSITION_US = Util.intToStringMaxRadix(9);
    private static final String FIELD_DURATION_US = Util.intToStringMaxRadix(10);
    private static final String FIELD_FIRST_PERIOD_INDEX = Util.intToStringMaxRadix(11);
    private static final String FIELD_LAST_PERIOD_INDEX = Util.intToStringMaxRadix(12);
    private static final String FIELD_POSITION_IN_FIRST_PERIOD_US = Util.intToStringMaxRadix(13);

    /**
     * 返回表示此对象中存储信息的 {@link Bundle}。
     *
     * <p>它省略了 {@link #uid} 和 {@link #manifest} 字段。
     * 通过 {@link #fromBundle} 恢复的实例的 {@link #uid} 将是一个假的 {@link Object}，
     * 而实例的 {@link #manifest} 将为 {@code null}。
     */
    @UnstableApi
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      if (!MediaItem.EMPTY.equals(mediaItem)) {
        bundle.putBundle(FIELD_MEDIA_ITEM, mediaItem.toBundle());
      }
      if (presentationStartTimeMs != C.TIME_UNSET) {
        bundle.putLong(FIELD_PRESENTATION_START_TIME_MS, presentationStartTimeMs);
      }
      if (windowStartTimeMs != C.TIME_UNSET) {
        bundle.putLong(FIELD_WINDOW_START_TIME_MS, windowStartTimeMs);
      }
      if (elapsedRealtimeEpochOffsetMs != C.TIME_UNSET) {
        bundle.putLong(FIELD_ELAPSED_REALTIME_EPOCH_OFFSET_MS, elapsedRealtimeEpochOffsetMs);
      }
      if (isSeekable) {
        bundle.putBoolean(FIELD_IS_SEEKABLE, isSeekable);
      }
      if (isDynamic) {
        bundle.putBoolean(FIELD_IS_DYNAMIC, isDynamic);
      }

      @Nullable MediaItem.LiveConfiguration liveConfiguration = this.liveConfiguration;
      if (liveConfiguration != null) {
        bundle.putBundle(FIELD_LIVE_CONFIGURATION, liveConfiguration.toBundle());
      }
      if (isPlaceholder) {
        bundle.putBoolean(FIELD_IS_PLACEHOLDER, isPlaceholder);
      }
      if (defaultPositionUs != 0) {
        bundle.putLong(FIELD_DEFAULT_POSITION_US, defaultPositionUs);
      }
      if (durationUs != C.TIME_UNSET) {
        bundle.putLong(FIELD_DURATION_US, durationUs);
      }
      if (firstPeriodIndex != 0) {
        bundle.putInt(FIELD_FIRST_PERIOD_INDEX, firstPeriodIndex);
      }
      if (lastPeriodIndex != 0) {
        bundle.putInt(FIELD_LAST_PERIOD_INDEX, lastPeriodIndex);
      }
      if (positionInFirstPeriodUs != 0) {
        bundle.putLong(FIELD_POSITION_IN_FIRST_PERIOD_US, positionInFirstPeriodUs);
      }
      return bundle;
    }

    /** Restores a {@code Window} from a {@link Bundle}. */
    @UnstableApi
    public static Window fromBundle(Bundle bundle) {
      @Nullable Bundle mediaItemBundle = bundle.getBundle(FIELD_MEDIA_ITEM);
      @Nullable
      MediaItem mediaItem =
          mediaItemBundle != null ? MediaItem.fromBundle(mediaItemBundle) : MediaItem.EMPTY;
      long presentationStartTimeMs =
          bundle.getLong(FIELD_PRESENTATION_START_TIME_MS, /* defaultValue= */ C.TIME_UNSET);
      long windowStartTimeMs =
          bundle.getLong(FIELD_WINDOW_START_TIME_MS, /* defaultValue= */ C.TIME_UNSET);
      long elapsedRealtimeEpochOffsetMs =
          bundle.getLong(FIELD_ELAPSED_REALTIME_EPOCH_OFFSET_MS, /* defaultValue= */ C.TIME_UNSET);
      boolean isSeekable = bundle.getBoolean(FIELD_IS_SEEKABLE, /* defaultValue= */ false);
      boolean isDynamic = bundle.getBoolean(FIELD_IS_DYNAMIC, /* defaultValue= */ false);
      @Nullable Bundle liveConfigurationBundle = bundle.getBundle(FIELD_LIVE_CONFIGURATION);
      @Nullable
      MediaItem.LiveConfiguration liveConfiguration =
          liveConfigurationBundle != null
              ? MediaItem.LiveConfiguration.fromBundle(liveConfigurationBundle)
              : null;
      boolean isPlaceHolder = bundle.getBoolean(FIELD_IS_PLACEHOLDER, /* defaultValue= */ false);
      long defaultPositionUs = bundle.getLong(FIELD_DEFAULT_POSITION_US, /* defaultValue= */ 0);
      long durationUs = bundle.getLong(FIELD_DURATION_US, /* defaultValue= */ C.TIME_UNSET);
      int firstPeriodIndex = bundle.getInt(FIELD_FIRST_PERIOD_INDEX, /* defaultValue= */ 0);
      int lastPeriodIndex = bundle.getInt(FIELD_LAST_PERIOD_INDEX, /* defaultValue= */ 0);
      long positionInFirstPeriodUs =
          bundle.getLong(FIELD_POSITION_IN_FIRST_PERIOD_US, /* defaultValue= */ 0);

      Window window = new Window();
      window.set(
          FAKE_WINDOW_UID,
          mediaItem,
          /* manifest= */ null,
          presentationStartTimeMs,
          windowStartTimeMs,
          elapsedRealtimeEpochOffsetMs,
          isSeekable,
          isDynamic,
          liveConfiguration,
          defaultPositionUs,
          durationUs,
          firstPeriodIndex,
          lastPeriodIndex,
          positionInFirstPeriodUs);
      window.isPlaceholder = isPlaceHolder;
      return window;
    }
  }
  /**
   * 保存 {@link Timeline} 中时间段的信息。时间段定义了一个单独的媒体逻辑片段，例如一个媒体文件。它还可以定义插入到媒体中的广告组，以及这些广告是否已加载和播放的信息。
   *
   * <p>下图展示了时间段定义的一些信息，以及这些信息如何与时间线中对应的 {@link Window} 相关联。
   *
   * <p style="align:center"><img
   * src="https://developer.android.com/static/images/reference/androidx/media3/common/timeline-period.svg"
   * alt="时间段定义的信息">
   */
  public static final class Period {

    /**
     * 时间段的标识符。不一定是唯一的。如果不需要时间段的标识符，则可以为 null。
     */
    @Nullable public Object id;

    /**
     * 时间段的唯一标识符。如果不需要时间段的标识符，则可以为 null。
     */
    @Nullable public Object uid;

    /** 此时间段所属窗口的索引。 */
    public int windowIndex;

    /** 此时间段的持续时间，以微秒为单位，如果未知则为 {@link C#TIME_UNSET}。 */
    @UnstableApi public long durationUs;

    /**
     * 此时间段的开始位置相对于所属窗口开始位置的偏移量，以微秒为单位。如果时间段的开始位置不在窗口内，则可能为负数。
     */
    @UnstableApi public long positionInWindowUs;

    /**
     * 此时间段是否包含占位符信息，因为实际信息尚未加载。
     */
    public boolean isPlaceholder;

    /** 此时间段中所有广告的 {@link AdPlaybackState}。 */
    @UnstableApi public AdPlaybackState adPlaybackState;

    /** 创建一个没有广告播放状态的新实例。 */
    public Period() {
      adPlaybackState = AdPlaybackState.NONE;
    }

    /**
     * 设置此时间段持有的数据。
     *
     * @param id 时间段的标识符。不一定是唯一的。如果不需要时间段的标识符，则可以为 null。
     * @param uid 时间段的唯一标识符。如果不需要时间段的标识符，则可以为 null。
     * @param windowIndex 此时间段所属窗口的索引。
     * @param durationUs 此时间段的持续时间，以微秒为单位，如果未知则为 {@link C#TIME_UNSET}。
     * @param positionInWindowUs 此时间段的开始位置相对于所属窗口开始位置的偏移量，以毫秒为单位。如果时间段的开始位置不在窗口内，则可能为负数。
     * @return 此时间段，方便链式调用。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Period set(
        @Nullable Object id,
        @Nullable Object uid,
        int windowIndex,
        long durationUs,
        long positionInWindowUs) {
      return set(
          id,
          uid,
          windowIndex,
          durationUs,
          positionInWindowUs,
          AdPlaybackState.NONE,
          /* isPlaceholder= */ false);
    }

    /**
     * 设置此时间段持有的数据。
     *
     * @param id 时间段的标识符。不一定是唯一的。如果不需要时间段的标识符，则可以为 null。
     * @param uid 时间段的唯一标识符。如果不需要时间段的标识符，则可以为 null。
     * @param windowIndex 此时间段所属窗口的索引。
     * @param durationUs 此时间段的持续时间，以微秒为单位，如果未知则为 {@link C#TIME_UNSET}。
     * @param positionInWindowUs 此时间段的开始位置相对于所属窗口开始位置的偏移量，以毫秒为单位。如果时间段的开始位置不在窗口内，则可能为负数。
     * @param adPlaybackState 时间段中广告的播放状态，如果没有广告则为 {@link AdPlaybackState#NONE}。
     * @param isPlaceholder 此时间段是否包含占位符信息，因为实际信息尚未加载。
     * @return 此时间段，方便链式调用。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Period set(
        @Nullable Object id,
        @Nullable Object uid,
        int windowIndex,
        long durationUs,
        long positionInWindowUs,
        AdPlaybackState adPlaybackState,
        boolean isPlaceholder) {
      this.id = id;
      this.uid = uid;
      this.windowIndex = windowIndex;
      this.durationUs = durationUs;
      this.positionInWindowUs = positionInWindowUs;
      this.adPlaybackState = adPlaybackState;
      this.isPlaceholder = isPlaceholder;
      return this;
    }
    /** 返回时间段的持续时间，以毫秒为单位，如果未知则为 {@link C#TIME_UNSET}。 */
    public long getDurationMs() {
      return Util.usToMs(durationUs);
    }

    /** 返回时间段的持续时间，以微秒为单位，如果未知则为 {@link C#TIME_UNSET}。 */
    public long getDurationUs() {
      return durationUs;
    }

    /**
     * 返回此时间段的开始位置相对于所属窗口开始位置的偏移量，以毫秒为单位。如果时间段的开始位置不在窗口内，则可能为负数。
     */
    public long getPositionInWindowMs() {
      return Util.usToMs(positionInWindowUs);
    }

    /**
     * 返回此时间段的开始位置相对于所属窗口开始位置的偏移量，以微秒为单位。如果时间段的开始位置不在窗口内，则可能为负数。
     */
    public long getPositionInWindowUs() {
      return positionInWindowUs;
    }

    /** 返回与此时间段关联的广告的不透明标识符，如果未设置则返回 {@code null}。 */
    @Nullable
    public Object getAdsId() {
      return adPlaybackState.adsId;
    }

    /** 返回时间段中广告组的数量。 */
    public int getAdGroupCount() {
      return adPlaybackState.adGroupCount;
    }

    /**
     * 返回时间段中已移除的广告组的数量。索引在 {@code 0}（包含）和 {@code removedAdGroupCount}（不包含）之间的广告组将为空。
     */
    public int getRemovedAdGroupCount() {
      return adPlaybackState.removedAdGroupCount;
    }

    /**
     * 返回时间段中指定索引 {@code adGroupIndex} 的广告组的时间，以微秒为单位。
     *
     * @param adGroupIndex 广告组索引。
     * @return 指定广告组的时间，相对于所属 {@link Period} 的开始时间，以微秒为单位，如果是后置广告组则返回 {@link C#TIME_END_OF_SOURCE}。
     */
    public long getAdGroupTimeUs(int adGroupIndex) {
      return adPlaybackState.getAdGroup(adGroupIndex).timeUs;
    }

    /**
     * 返回指定广告组中应播放的第一个广告的索引，如果不应播放任何广告，则返回广告组中的广告数量。
     *
     * @param adGroupIndex 广告组索引。
     * @return 应播放的第一个广告的索引，如果不应播放任何广告，则返回广告组中的广告数量。
     */
    public int getFirstAdIndexToPlay(int adGroupIndex) {
      return adPlaybackState.getAdGroup(adGroupIndex).getFirstAdIndexToPlay();
    }
    /**
     * 返回指定广告组中在播放 {@code adIndexInAdGroup} 之后应播放的下一个广告的索引，如果不应播放任何后续广告，则返回广告组中的广告数量。
     *
     * @param adGroupIndex 广告组索引。
     * @param lastPlayedAdIndex 广告组中最后播放的广告索引。
     * @return 应播放的下一个广告的索引，如果广告组中没有剩余的广告可播放，则返回广告组中的广告数量。
     */
    public int getNextAdIndexToPlay(int adGroupIndex, int lastPlayedAdIndex) {
      return adPlaybackState.getAdGroup(adGroupIndex).getNextAdIndexToPlay(lastPlayedAdIndex);
    }

    /**
     * 返回指定广告组中的所有广告是否已被播放、跳过或失败。
     *
     * @param adGroupIndex 广告组索引。
     * @return 指定广告组中的所有广告是否已被播放、跳过或失败。
     */
    public boolean hasPlayedAdGroup(int adGroupIndex) {
      return !adPlaybackState.getAdGroup(adGroupIndex).hasUnplayedAds();
    }

    /**
     * 返回在时间段中位于或早于 {@code positionUs} 的广告组中应在 {@code positionUs} 内容之前播放的广告组的索引。如果位于或早于 {@code positionUs} 的广告组没有剩余的广告可播放，或者不存在这样的广告组，则返回 {@link C#INDEX_UNSET}。
     *
     * @param positionUs 时间段中用于查找广告组的位置，以微秒为单位。
     * @return 广告组的索引，或 {@link C#INDEX_UNSET}。
     */
    public int getAdGroupIndexForPositionUs(long positionUs) {
      return adPlaybackState.getAdGroupIndexForPositionUs(positionUs, durationUs);
    }

    /**
     * 返回时间段中位于 {@code positionUs} 之后的广告组中应播放的下一个广告组的索引。如果不存在这样的广告组，则返回 {@link C#INDEX_UNSET}。
     *
     * @param positionUs 时间段中用于查找广告组的位置，以微秒为单位。
     * @return 广告组的索引，或 {@link C#INDEX_UNSET}。
     */
    public int getAdGroupIndexAfterPositionUs(long positionUs) {
      return adPlaybackState.getAdGroupIndexAfterPositionUs(positionUs, durationUs);
    }

    /**
     * 返回指定广告组中的广告数量，如果尚未知晓，则返回 {@link C#LENGTH_UNSET}。
     *
     * @param adGroupIndex 广告组索引。
     * @return 广告组中的广告数量，如果尚未知晓，则返回 {@link C#LENGTH_UNSET}。
     */
    public int getAdCountInAdGroup(int adGroupIndex) {
      return adPlaybackState.getAdGroup(adGroupIndex).count;
    }

    /**
     * 返回指定广告组中指定索引 {@code adIndexInAdGroup} 的广告的持续时间，以微秒为单位，如果尚未知晓，则返回 {@link C#TIME_UNSET}。
     *
     * @param adGroupIndex 广告组索引。
     * @param adIndexInAdGroup 广告组中的广告索引。
     * @return 广告的持续时间，如果尚未知晓，则返回 {@link C#TIME_UNSET}。
     */
    public long getAdDurationUs(int adGroupIndex, int adIndexInAdGroup) {
      AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(adGroupIndex);
      return adGroup.count != C.LENGTH_UNSET ? adGroup.durationsUs[adIndexInAdGroup] : C.TIME_UNSET;
    }

    /**
     * 返回指定广告组中指定索引 {@code adIndexInAdGroup} 的广告的状态，如果尚未知晓，则返回 {@link AdPlaybackState#AD_STATE_UNAVAILABLE}。
     *
     * @param adGroupIndex 广告组索引。
     * @param adIndexInAdGroup 广告组中的广告索引。
     * @return 广告的状态，如果尚未知晓，则返回 {@link AdPlaybackState#AD_STATE_UNAVAILABLE}。
     */
    @UnstableApi
    public int getAdState(int adGroupIndex, int adIndexInAdGroup) {
      AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(adGroupIndex);
      return adGroup.count != C.LENGTH_UNSET
          ? adGroup.states[adIndexInAdGroup]
          : AD_STATE_UNAVAILABLE;
    }
    /**
     * 返回指定广告组索引处的广告组是否为直播后置广告占位符。
     *
     * @param adGroupIndex 广告组索引。
     * @return 如果指定索引处的广告组是直播后置广告占位符，则返回 true。
     */
    @UnstableApi
    public boolean isLivePostrollPlaceholder(int adGroupIndex) {
      return adGroupIndex == getAdGroupCount() - 1
          && adPlaybackState.isLivePostrollPlaceholder(adGroupIndex);
    }

    /**
     * 返回在第一个未播放的广告中开始播放的位置偏移量，以微秒为单位。
     */
    public long getAdResumePositionUs() {
      return adPlaybackState.adResumePositionUs;
    }

    /**
     * 返回指定广告组索引处的广告组是否为服务器端插入并属于内容流的一部分。
     *
     * @param adGroupIndex 广告组索引。
     * @return 如果该广告组是服务器端插入并属于内容流的一部分，则返回 true。
     */
    @UnstableApi
    public boolean isServerSideInsertedAdGroup(int adGroupIndex) {
      return adPlaybackState.getAdGroup(adGroupIndex).isServerSideInserted;
    }

    /**
     * 返回在指定广告组之后恢复播放时应添加到内容流的偏移量，以微秒为单位。
     *
     * @param adGroupIndex 广告组索引。
     * @return 应添加到内容流的偏移量，以微秒为单位。
     */
    @UnstableApi
    public long getContentResumeOffsetUs(int adGroupIndex) {
      return adPlaybackState.getAdGroup(adGroupIndex).contentResumeOffsetUs;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || !getClass().equals(obj.getClass())) {
        return false;
      }
      Period that = (Period) obj;
      return Util.areEqual(id, that.id)
          && Util.areEqual(uid, that.uid)
          && windowIndex == that.windowIndex
          && durationUs == that.durationUs
          && positionInWindowUs == that.positionInWindowUs
          && isPlaceholder == that.isPlaceholder
          && Util.areEqual(adPlaybackState, that.adPlaybackState);
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + (id == null ? 0 : id.hashCode());
      result = 31 * result + (uid == null ? 0 : uid.hashCode());
      result = 31 * result + windowIndex;
      result = 31 * result + (int) (durationUs ^ (durationUs >>> 32));
      result = 31 * result + (int) (positionInWindowUs ^ (positionInWindowUs >>> 32));
      result = 31 * result + (isPlaceholder ? 1 : 0);
      result = 31 * result + adPlaybackState.hashCode();
      return result;
    }

    private static final String FIELD_WINDOW_INDEX = Util.intToStringMaxRadix(0);
    private static final String FIELD_DURATION_US = Util.intToStringMaxRadix(1);
    private static final String FIELD_POSITION_IN_WINDOW_US = Util.intToStringMaxRadix(2);
    private static final String FIELD_PLACEHOLDER = Util.intToStringMaxRadix(3);
    private static final String FIELD_AD_PLAYBACK_STATE = Util.intToStringMaxRadix(4);

    /**
     * 返回表示此对象中存储信息的 {@link Bundle}。
     *
     * <p>它省略了 {@link #id} 和 {@link #uid} 字段，因此通过 {@link #fromBundle} 恢复的实例的这些字段将始终为 {@code null}。
     */
    @UnstableApi
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      if (windowIndex != 0) {
        bundle.putInt(FIELD_WINDOW_INDEX, windowIndex);
      }
      if (durationUs != C.TIME_UNSET) {
        bundle.putLong(FIELD_DURATION_US, durationUs);
      }
      if (positionInWindowUs != 0) {
        bundle.putLong(FIELD_POSITION_IN_WINDOW_US, positionInWindowUs);
      }
      if (isPlaceholder) {
        bundle.putBoolean(FIELD_PLACEHOLDER, isPlaceholder);
      }
      if (!adPlaybackState.equals(AdPlaybackState.NONE)) {
        bundle.putBundle(FIELD_AD_PLAYBACK_STATE, adPlaybackState.toBundle());
      }
      return bundle;
    }

    /** Restores a {@code Period} from a {@link Bundle}. */
    @UnstableApi
    public static Period fromBundle(Bundle bundle) {
      int windowIndex = bundle.getInt(FIELD_WINDOW_INDEX, /* defaultValue= */ 0);
      long durationUs = bundle.getLong(FIELD_DURATION_US, /* defaultValue= */ C.TIME_UNSET);
      long positionInWindowUs = bundle.getLong(FIELD_POSITION_IN_WINDOW_US, /* defaultValue= */ 0);
      boolean isPlaceholder = bundle.getBoolean(FIELD_PLACEHOLDER, /* defaultValue= */ false);
      @Nullable Bundle adPlaybackStateBundle = bundle.getBundle(FIELD_AD_PLAYBACK_STATE);
      AdPlaybackState adPlaybackState =
          adPlaybackStateBundle != null
              ? AdPlaybackState.fromBundle(adPlaybackStateBundle)
              : AdPlaybackState.NONE;

      Period period = new Period();
      period.set(
          /* id= */ null,
          /* uid= */ null,
          windowIndex,
          durationUs,
          positionInWindowUs,
          adPlaybackState,
          isPlaceholder);
      return period;
    }
  }

  /** An empty timeline. */
  public static final Timeline EMPTY =
      new Timeline() {

        @Override
        public int getWindowCount() {
          return 0;
        }

        @Override
        public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
          throw new IndexOutOfBoundsException();
        }

        @Override
        public int getPeriodCount() {
          return 0;
        }

        @Override
        public Period getPeriod(int periodIndex, Period period, boolean setIds) {
          throw new IndexOutOfBoundsException();
        }

        @Override
        public int getIndexOfPeriod(Object uid) {
          return C.INDEX_UNSET;
        }

        @Override
        public Object getUidOfPeriod(int periodIndex) {
          throw new IndexOutOfBoundsException();
        }
      };

  @UnstableApi
  protected Timeline() {}

  /** Returns whether the timeline is empty. */
  public final boolean isEmpty() {
    return getWindowCount() == 0;
  }

  /** Returns the number of windows in the timeline. */
  public abstract int getWindowCount();

  /**
   * 根据 {@code repeatMode} 和是否启用了随机播放，返回位于索引 {@code windowIndex} 的窗口之后的下一个窗口的索引。
   *
   * @param windowIndex 时间线中某个窗口的索引。
   * @param repeatMode 重复模式。
   * @param shuffleModeEnabled 是否启用了随机播放。
   * @return 下一个窗口的索引，如果这是最后一个窗口，则返回 {@link C#INDEX_UNSET}。
   */
  public int getNextWindowIndex(
      int windowIndex, @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return windowIndex == getLastWindowIndex(shuffleModeEnabled)
            ? C.INDEX_UNSET
            : windowIndex + 1;
      case Player.REPEAT_MODE_ONE:
        return windowIndex;
      case Player.REPEAT_MODE_ALL:
        return windowIndex == getLastWindowIndex(shuffleModeEnabled)
            ? getFirstWindowIndex(shuffleModeEnabled)
            : windowIndex + 1;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * 根据 {@code repeatMode} 和是否启用了随机播放，返回位于索引 {@code windowIndex} 的窗口之前的上一个窗口的索引。
   *
   * @param windowIndex 时间线中某个窗口的索引。
   * @param repeatMode 重复模式。
   * @param shuffleModeEnabled 是否启用了随机播放。
   * @return 上一个窗口的索引，如果这是第一个窗口，则返回 {@link C#INDEX_UNSET}。
   */
  public int getPreviousWindowIndex(
      int windowIndex, @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return windowIndex == getFirstWindowIndex(shuffleModeEnabled)
            ? C.INDEX_UNSET
            : windowIndex - 1;
      case Player.REPEAT_MODE_ONE:
        return windowIndex;
      case Player.REPEAT_MODE_ALL:
        return windowIndex == getFirstWindowIndex(shuffleModeEnabled)
            ? getLastWindowIndex(shuffleModeEnabled)
            : windowIndex - 1;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Returns the index of the last window in the playback order depending on whether shuffling is
   * enabled.
   *
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the last window in the playback order, or {@link C#INDEX_UNSET} if the
   *     timeline is empty.
   */
  public int getLastWindowIndex(boolean shuffleModeEnabled) {
    return isEmpty() ? C.INDEX_UNSET : getWindowCount() - 1;
  }

  /**
   * Returns the index of the first window in the playback order depending on whether shuffling is
   * enabled.
   *
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the first window in the playback order, or {@link C#INDEX_UNSET} if the
   *     timeline is empty.
   */
  public int getFirstWindowIndex(boolean shuffleModeEnabled) {
    return isEmpty() ? C.INDEX_UNSET : 0;
  }

  /**
   * Populates a {@link Window} with data for the window at the specified index.
   *
   * @param windowIndex The index of the window.
   * @param window The {@link Window} to populate. Must not be null.
   * @return The populated {@link Window}, for convenience.
   */
  public final Window getWindow(int windowIndex, Window window) {
    return getWindow(windowIndex, window, /* defaultPositionProjectionUs= */ 0);
  }

  /**
   * Populates a {@link Window} with data for the window at the specified index.
   *
   * @param windowIndex The index of the window.
   * @param window The {@link Window} to populate. Must not be null.
   * @param defaultPositionProjectionUs A duration into the future that the populated window's
   *     default start position should be projected.
   * @return The populated {@link Window}, for convenience.
   */
  public abstract Window getWindow(
      int windowIndex, Window window, long defaultPositionProjectionUs);

  /** Returns the number of periods in the timeline. */
  public abstract int getPeriodCount();

  /**
   * Returns the index of the period after the period at index {@code periodIndex} depending on the
   * {@code repeatMode} and whether shuffling is enabled.
   *
   * @param periodIndex Index of a period in the timeline.
   * @param period A {@link Period} to be used internally. Must not be null.
   * @param window A {@link Window} to be used internally. Must not be null.
   * @param repeatMode A repeat mode.
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the next period, or {@link C#INDEX_UNSET} if this is the last period.
   */
  public final int getNextPeriodIndex(
      int periodIndex,
      Period period,
      Window window,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled) {
    int windowIndex = getPeriod(periodIndex, period).windowIndex;
    if (getWindow(windowIndex, window).lastPeriodIndex == periodIndex) {
      int nextWindowIndex = getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
      if (nextWindowIndex == C.INDEX_UNSET) {
        return C.INDEX_UNSET;
      }
      return getWindow(nextWindowIndex, window).firstPeriodIndex;
    }
    return periodIndex + 1;
  }

  /**
   * Returns whether the given period is the last period of the timeline depending on the {@code
   * repeatMode} and whether shuffling is enabled.
   *
   * @param periodIndex A period index.
   * @param period A {@link Period} to be used internally. Must not be null.
   * @param window A {@link Window} to be used internally. Must not be null.
   * @param repeatMode A repeat mode.
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return Whether the period of the given index is the last period of the timeline.
   */
  public final boolean isLastPeriod(
      int periodIndex,
      Period period,
      Window window,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled) {
    return getNextPeriodIndex(periodIndex, period, window, repeatMode, shuffleModeEnabled)
        == C.INDEX_UNSET;
  }

  /**
   * @deprecated Use {@link #getPeriodPositionUs(Window, Period, int, long)} instead.
   */
  @UnstableApi
  @Deprecated
  @InlineMe(replacement = "this.getPeriodPositionUs(window, period, windowIndex, windowPositionUs)")
  public final Pair<Object, Long> getPeriodPosition(
      Window window, Period period, int windowIndex, long windowPositionUs) {
    return getPeriodPositionUs(window, period, windowIndex, windowPositionUs);
  }

  /**
   * @deprecated Use {@link #getPeriodPositionUs(Window, Period, int, long, long)} instead.
   */
  @UnstableApi
  @Deprecated
  @Nullable
  @InlineMe(
      replacement =
          "this.getPeriodPositionUs("
              + "window, period, windowIndex, windowPositionUs, defaultPositionProjectionUs)")
  public final Pair<Object, Long> getPeriodPosition(
      Window window,
      Period period,
      int windowIndex,
      long windowPositionUs,
      long defaultPositionProjectionUs) {
    return getPeriodPositionUs(
        window, period, windowIndex, windowPositionUs, defaultPositionProjectionUs);
  }

  /**
   * Calls {@link #getPeriodPositionUs(Window, Period, int, long)} with a zero default position
   * projection.
   */
  public final Pair<Object, Long> getPeriodPositionUs(
      Window window, Period period, int windowIndex, long windowPositionUs) {
    return Assertions.checkNotNull(
        getPeriodPositionUs(
            window, period, windowIndex, windowPositionUs, /* defaultPositionProjectionUs= */ 0));
  }

  /**
   * 将 {@code (windowIndex, windowPositionUs)} 转换为对应的 {@code (periodUid, periodPositionUs)}。返回的 {@code periodPositionUs} 被限制为非负数，并且如果已知包含时间段（Period）的持续时间，则小于该持续时间。
   *
   * @param window 一个可能被覆盖的 {@link Window}。
   * @param period 一个可能被覆盖的 {@link Period}。
   * @param windowIndex 窗口索引。
   * @param windowPositionUs 窗口时间，或 {@link C#TIME_UNSET} 以使用窗口的默认起始位置。
   * @param defaultPositionProjectionUs 如果 {@code windowPositionUs} 是 {@link C#TIME_UNSET}，则表示窗口位置应投影到未来的时间长度。
   * @return 对应的 (periodUid, periodPositionUs)，如果 {@code windowPositionUs} 是 {@link C#TIME_UNSET}，{@code defaultPositionProjectionUs} 非零，且窗口位置无法按 {@code defaultPositionProjectionUs} 投影，则返回 null。
   */
  @Nullable
  public final Pair<Object, Long> getPeriodPositionUs(
      Window window,
      Period period,
      int windowIndex,
      long windowPositionUs,
      long defaultPositionProjectionUs) {
    Assertions.checkIndex(windowIndex, 0, getWindowCount());
    getWindow(windowIndex, window, defaultPositionProjectionUs);
    if (windowPositionUs == C.TIME_UNSET) {
      windowPositionUs = window.getDefaultPositionUs();
      if (windowPositionUs == C.TIME_UNSET) {
        return null;
      }
    }
    int periodIndex = window.firstPeriodIndex;
    getPeriod(periodIndex, period);
    while (periodIndex < window.lastPeriodIndex
        && period.positionInWindowUs != windowPositionUs
        && getPeriod(periodIndex + 1, period).positionInWindowUs <= windowPositionUs) {
      periodIndex++;
    }
    getPeriod(periodIndex, period, /* setIds= */ true);
    long periodPositionUs = windowPositionUs - period.positionInWindowUs;
    // The period positions must be less than the period duration, if it is known.
    if (period.durationUs != C.TIME_UNSET) {
      periodPositionUs = min(periodPositionUs, period.durationUs - 1);
    }
    // Period positions cannot be negative.
    periodPositionUs = max(0, periodPositionUs);
    return Pair.create(Assertions.checkNotNull(period.uid), periodPositionUs);
  }
  /**
   * 使用指定唯一标识符的时间段数据填充 {@link Period}。
   *
   * @param periodUid 时间段的唯一标识符。
   * @param period 要填充的 {@link Period}。不能为 null。
   * @return 填充后的 {@link Period}，方便链式调用。
   */
  public Period getPeriodByUid(Object periodUid, Period period) {
    return getPeriod(getIndexOfPeriod(periodUid), period, /* setIds= */ true);
  }

  /**
   * 使用指定索引的时间段数据填充 {@link Period}。{@link Period#id} 和 {@link Period#uid} 将被设置为 null。
   *
   * @param periodIndex 时间段的索引。
   * @param period 要填充的 {@link Period}。不能为 null。
   * @return 填充后的 {@link Period}，方便链式调用。
   */
  public final Period getPeriod(int periodIndex, Period period) {
    return getPeriod(periodIndex, period, false);
  }

  /**
   * 使用指定索引的时间段数据填充 {@link Period}。
   *
   * @param periodIndex 时间段的索引。
   * @param period 要填充的 {@link Period}。不能为 null。
   * @param setIds 是否填充 {@link Period#id} 和 {@link Period#uid}。如果为 false，这些字段将被设置为 null。除非需要这些字段，否则调用者应传递 false 以提高效率。
   * @return 填充后的 {@link Period}，方便链式调用。
   */
  public abstract Period getPeriod(int periodIndex, Period period, boolean setIds);

  /**
   * 返回由唯一 {@link Period#uid} 标识的时间段的索引，如果时间段不在时间线中，则返回 {@link C#INDEX_UNSET}。
   *
   * @param uid 时间段的唯一标识符。
   * @return 时间段的索引，如果未找到时间段，则返回 {@link C#INDEX_UNSET}。
   */
  public abstract int getIndexOfPeriod(Object uid);

  /**
   * 返回由其在时间线中的索引标识的时间段的唯一 id。
   *
   * @param periodIndex 时间段的索引。
   * @return 时间段的唯一 id。
   */
  public abstract Object getUidOfPeriod(int periodIndex);

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Timeline)) {
      return false;
    }
    Timeline other = (Timeline) obj;
    if (other.getWindowCount() != getWindowCount() || other.getPeriodCount() != getPeriodCount()) {
      return false;
    }
    Timeline.Window window = new Timeline.Window();
    Timeline.Period period = new Timeline.Period();
    Timeline.Window otherWindow = new Timeline.Window();
    Timeline.Period otherPeriod = new Timeline.Period();
    for (int i = 0; i < getWindowCount(); i++) {
      if (!getWindow(i, window).equals(other.getWindow(i, otherWindow))) {
        return false;
      }
    }
    for (int i = 0; i < getPeriodCount(); i++) {
      if (!getPeriod(i, period, /* setIds= */ true)
          .equals(other.getPeriod(i, otherPeriod, /* setIds= */ true))) {
        return false;
      }
    }

    // Check shuffled order
    int windowIndex = getFirstWindowIndex(/* shuffleModeEnabled= */ true);
    if (windowIndex != other.getFirstWindowIndex(/* shuffleModeEnabled= */ true)) {
      return false;
    }
    int lastWindowIndex = getLastWindowIndex(/* shuffleModeEnabled= */ true);
    if (lastWindowIndex != other.getLastWindowIndex(/* shuffleModeEnabled= */ true)) {
      return false;
    }
    while (windowIndex != lastWindowIndex) {
      int nextWindowIndex =
          getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true);
      if (nextWindowIndex
          != other.getNextWindowIndex(
              windowIndex, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true)) {
        return false;
      }
      windowIndex = nextWindowIndex;
    }

    return true;
  }

  @Override
  public int hashCode() {
    Window window = new Window();
    Period period = new Period();
    int result = 7;
    result = 31 * result + getWindowCount();
    for (int i = 0; i < getWindowCount(); i++) {
      result = 31 * result + getWindow(i, window).hashCode();
    }
    result = 31 * result + getPeriodCount();
    for (int i = 0; i < getPeriodCount(); i++) {
      result = 31 * result + getPeriod(i, period, /* setIds= */ true).hashCode();
    }

    for (int windowIndex = getFirstWindowIndex(true);
        windowIndex != C.INDEX_UNSET;
        windowIndex = getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, true)) {
      result = 31 * result + windowIndex;
    }

    return result;
  }

  private static final String FIELD_WINDOWS = Util.intToStringMaxRadix(0);
  private static final String FIELD_PERIODS = Util.intToStringMaxRadix(1);
  private static final String FIELD_SHUFFLED_WINDOW_INDICES = Util.intToStringMaxRadix(2);

  /**
   * Returns a {@link Bundle} representing the information stored in this object.
   *
   * <p>The {@link #getWindow(int, Window)} windows} and {@link #getPeriod(int, Period) periods} of
   * an instance restored by {@link #fromBundle} may have missing fields as described in {@link
   * Window#toBundle()} and {@link Period#toBundle()}.
   */
  @UnstableApi
  public final Bundle toBundle() {
    List<Bundle> windowBundles = new ArrayList<>();
    int windowCount = getWindowCount();
    Window window = new Window();
    for (int i = 0; i < windowCount; i++) {
      windowBundles.add(getWindow(i, window, /* defaultPositionProjectionUs= */ 0).toBundle());
    }

    List<Bundle> periodBundles = new ArrayList<>();
    int periodCount = getPeriodCount();
    Period period = new Period();
    for (int i = 0; i < periodCount; i++) {
      periodBundles.add(getPeriod(i, period, /* setIds= */ false).toBundle());
    }

    int[] shuffledWindowIndices = new int[windowCount];
    if (windowCount > 0) {
      shuffledWindowIndices[0] = getFirstWindowIndex(/* shuffleModeEnabled= */ true);
    }
    for (int i = 1; i < windowCount; i++) {
      shuffledWindowIndices[i] =
          getNextWindowIndex(
              shuffledWindowIndices[i - 1], Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true);
    }

    Bundle bundle = new Bundle();
    bundle.putBinder(FIELD_WINDOWS, new BundleListRetriever(windowBundles));
    bundle.putBinder(FIELD_PERIODS, new BundleListRetriever(periodBundles));
    bundle.putIntArray(FIELD_SHUFFLED_WINDOW_INDICES, shuffledWindowIndices);
    return bundle;
  }

  /**
   * 返回一个仅包含指定 {@link Window} 的此时间线的副本。
   *
   * <p>如果时间线中只有一个窗口，则返回相同的实例。
   *
   * @param windowIndex 要包含在副本中的 {@link Window} 的索引。
   * @return 一个仅包含指定 {@link Window} 的 {@link Timeline}。
   */
  @UnstableApi
  public final Timeline copyWithSingleWindow(int windowIndex) {
    if (getWindowCount() == 1) {
      return this;
    }
    Window window = getWindow(windowIndex, new Window(), /* defaultPositionProjectionUs= */ 0);
    ImmutableList.Builder<Period> periods = ImmutableList.builder();
    for (int i = window.firstPeriodIndex; i <= window.lastPeriodIndex; i++) {
      Period period = getPeriod(i, new Period(), /* setIds= */ true);
      period.windowIndex = 0;
      periods.add(period);
    }
    window.lastPeriodIndex = window.lastPeriodIndex - window.firstPeriodIndex;
    window.firstPeriodIndex = 0;
    return new RemotableTimeline(
        ImmutableList.of(window), periods.build(), /* shuffledWindowIndices= */ new int[] {0});
  }

  /** Restores a {@code Timeline} from a {@link Bundle}. */
  @UnstableApi
  public static Timeline fromBundle(Bundle bundle) {
    ImmutableList<Window> windows =
        fromBundleListRetriever(Window::fromBundle, bundle.getBinder(FIELD_WINDOWS));
    ImmutableList<Period> periods =
        fromBundleListRetriever(Period::fromBundle, bundle.getBinder(FIELD_PERIODS));
    @Nullable int[] shuffledWindowIndices = bundle.getIntArray(FIELD_SHUFFLED_WINDOW_INDICES);
    return new RemotableTimeline(
        windows,
        periods,
        shuffledWindowIndices == null
            ? generateUnshuffledIndices(windows.size())
            : shuffledWindowIndices);
  }

  private static <T extends @NonNull Object> ImmutableList<T> fromBundleListRetriever(
      Function<Bundle, T> fromBundleFunc, @Nullable IBinder binder) {
    if (binder == null) {
      return ImmutableList.of();
    }
    return BundleCollectionUtil.fromBundleList(fromBundleFunc, BundleListRetriever.getList(binder));
  }

  private static int[] generateUnshuffledIndices(int n) {
    int[] indices = new int[n];
    for (int i = 0; i < n; i++) {
      indices[i] = i;
    }
    return indices;
  }

  /**
   * A concrete class of {@link Timeline} to restore a {@link Timeline} instance from a {@link
   * Bundle} sent by another process via {@link IBinder}.
   */
  @UnstableApi
  public static final class RemotableTimeline extends Timeline {

    private final ImmutableList<Window> windows;
    private final ImmutableList<Period> periods;
    private final int[] shuffledWindowIndices;
    private final int[] windowIndicesInShuffled;

    public RemotableTimeline(
        ImmutableList<Window> windows, ImmutableList<Period> periods, int[] shuffledWindowIndices) {
      checkArgument(windows.size() == shuffledWindowIndices.length);
      this.windows = windows;
      this.periods = periods;
      this.shuffledWindowIndices = shuffledWindowIndices;
      windowIndicesInShuffled = new int[shuffledWindowIndices.length];
      for (int i = 0; i < shuffledWindowIndices.length; i++) {
        windowIndicesInShuffled[shuffledWindowIndices[i]] = i;
      }
    }

    @Override
    public int getWindowCount() {
      return windows.size();
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      Window w = windows.get(windowIndex);
      window.set(
          w.uid,
          w.mediaItem,
          w.manifest,
          w.presentationStartTimeMs,
          w.windowStartTimeMs,
          w.elapsedRealtimeEpochOffsetMs,
          w.isSeekable,
          w.isDynamic,
          w.liveConfiguration,
          w.defaultPositionUs,
          w.durationUs,
          w.firstPeriodIndex,
          w.lastPeriodIndex,
          w.positionInFirstPeriodUs);
      window.isPlaceholder = w.isPlaceholder;
      return window;
    }

    @Override
    public int getNextWindowIndex(
        int windowIndex, @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
      if (repeatMode == Player.REPEAT_MODE_ONE) {
        return windowIndex;
      }
      if (windowIndex == getLastWindowIndex(shuffleModeEnabled)) {
        return repeatMode == Player.REPEAT_MODE_ALL
            ? getFirstWindowIndex(shuffleModeEnabled)
            : C.INDEX_UNSET;
      }
      return shuffleModeEnabled
          ? shuffledWindowIndices[windowIndicesInShuffled[windowIndex] + 1]
          : windowIndex + 1;
    }

    @Override
    public int getPreviousWindowIndex(
        int windowIndex, @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
      if (repeatMode == Player.REPEAT_MODE_ONE) {
        return windowIndex;
      }
      if (windowIndex == getFirstWindowIndex(shuffleModeEnabled)) {
        return repeatMode == Player.REPEAT_MODE_ALL
            ? getLastWindowIndex(shuffleModeEnabled)
            : C.INDEX_UNSET;
      }
      return shuffleModeEnabled
          ? shuffledWindowIndices[windowIndicesInShuffled[windowIndex] - 1]
          : windowIndex - 1;
    }

    @Override
    public int getLastWindowIndex(boolean shuffleModeEnabled) {
      if (isEmpty()) {
        return C.INDEX_UNSET;
      }
      return shuffleModeEnabled
          ? shuffledWindowIndices[getWindowCount() - 1]
          : getWindowCount() - 1;
    }

    @Override
    public int getFirstWindowIndex(boolean shuffleModeEnabled) {
      if (isEmpty()) {
        return C.INDEX_UNSET;
      }
      return shuffleModeEnabled ? shuffledWindowIndices[0] : 0;
    }

    @Override
    public int getPeriodCount() {
      return periods.size();
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      Period p = periods.get(periodIndex);
      period.set(
          p.id,
          p.uid,
          p.windowIndex,
          p.durationUs,
          p.positionInWindowUs,
          p.adPlaybackState,
          p.isPlaceholder);
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      throw new UnsupportedOperationException();
    }
  }
}
