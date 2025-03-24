package androidx.media3.common;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/**
 * 用于标识可以单独加载的媒体子集（即“流”）的键。
 *
 * <p>流键由周期索引、周期内的组索引和组内的流索引组成。这些索引的解释取决于使用流键的媒体类型。
 * 注意，这些索引<em>不</em>等同于轨道组和轨道索引，因为多个轨道可以复用到单个流中。
 *
 * <p>应用程序代码通常不应直接尝试构建 StreamKey 实例。相反，可以使用 {@code DownloadHelper.getDownloadRequest}
 * 生成下载请求，其中包含与帮助程序上配置的轨道选择相对应的正确 StreamKey。{@code MediaPeriod.getStreamKeys}
 * 提供了一种更低级别的方式来生成与特定轨道选择相对应的 StreamKey。
 */
@UnstableApi
public final class StreamKey implements Comparable<StreamKey>, Parcelable {

  /** 周期索引。 */
  public final int periodIndex;

  /** 组索引。 */
  public final int groupIndex;

  /** 流索引。 */
  public final int streamIndex;

  /**
   * 创建一个实例，其中 {@link #periodIndex} 设置为 0。
   *
   * @param groupIndex 组索引。
   * @param streamIndex 流索引。
   */
  public StreamKey(int groupIndex, int streamIndex) {
    this(0, groupIndex, streamIndex);
  }

  /**
   * 使用 3 个索引创建一个 {@link StreamKey} 实例。
   *
   * @param periodIndex 周期索引。
   * @param groupIndex 组索引。
   * @param streamIndex 流索引。
   */
  public StreamKey(int periodIndex, int groupIndex, int streamIndex) {
    this.periodIndex = periodIndex;
    this.groupIndex = groupIndex;
    this.streamIndex = streamIndex;
  }

  /* package */ StreamKey(Parcel in) {
    periodIndex = in.readInt();
    groupIndex = in.readInt();
    streamIndex = in.readInt();
  }

  @Override
  public String toString() {
    return periodIndex + "." + groupIndex + "." + streamIndex;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    StreamKey that = (StreamKey) o;
    return periodIndex == that.periodIndex
        && groupIndex == that.groupIndex
        && streamIndex == that.streamIndex;
  }

  @Override
  public int hashCode() {
    int result = periodIndex;
    result = 31 * result + groupIndex;
    result = 31 * result + streamIndex;
    return result;
  }

  // Comparable 接口实现。

  @Override
  public int compareTo(StreamKey o) {
    int result = periodIndex - o.periodIndex;
    if (result == 0) {
      result = groupIndex - o.groupIndex;
      if (result == 0) {
        result = streamIndex - o.streamIndex;
      }
    }
    return result;
  }

  // Parcelable 接口实现。

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(periodIndex);
    dest.writeInt(groupIndex);
    dest.writeInt(streamIndex);
  }

  public static final Parcelable.Creator<StreamKey> CREATOR =
      new Parcelable.Creator<StreamKey>() {

        @Override
        public StreamKey createFromParcel(Parcel in) {
          return new StreamKey(in);
        }

        @Override
        public StreamKey[] newArray(int size) {
          return new StreamKey[size];
        }
      };

  private static final String FIELD_PERIOD_INDEX = Util.intToStringMaxRadix(0);
  private static final String FIELD_GROUP_INDEX = Util.intToStringMaxRadix(1);
  private static final String FIELD_STREAM_INDEX = Util.intToStringMaxRadix(2);

  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    if (periodIndex != 0) {
      bundle.putInt(FIELD_PERIOD_INDEX, periodIndex);
    }
    if (groupIndex != 0) {
      bundle.putInt(FIELD_GROUP_INDEX, groupIndex);
    }
    if (streamIndex != 0) {
      bundle.putInt(FIELD_STREAM_INDEX, streamIndex);
    }
    return bundle;
  }

  /**
   * 从 {@link Bundle} 中构建一个 {@link StreamKey} 实例，该 Bundle 由 {@link #toBundle()} 生成。
   */
  public static StreamKey fromBundle(Bundle bundle) {
    return new StreamKey(
        bundle.getInt(FIELD_PERIOD_INDEX, /* defaultValue= */ 0),
        bundle.getInt(FIELD_GROUP_INDEX, /* defaultValue= */ 0),
        bundle.getInt(FIELD_STREAM_INDEX, /* defaultValue= */ 0));
  }
}