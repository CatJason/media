package androidx.media3.common;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.primitives.Longs;
import java.util.Arrays;
import java.util.List;

/** 元数据条目的集合。 */
@UnstableApi
public final class Metadata implements Parcelable {

  /** 元数据条目。 */
  public interface Entry extends Parcelable {

    /**
     * 返回可用于解码 {@link #getWrappedMetadataBytes()} 中包装元数据的 {@link Format}，如果此条目不包含包装元数据，则返回 null。
     */
    @Nullable
    default Format getWrappedMetadataFormat() {
      return null;
    }

    /**
     * 返回此条目中包装元数据的字节数组，如果它不包含包装元数据，则返回 null。
     */
    @Nullable
    default byte[] getWrappedMetadataBytes() {
      return null;
    }

    /**
     * 使用此 {@code Entry} 中存储的类型特定值更新 {@link MediaMetadata.Builder}。
     *
     * @param builder 要更新的构建器。
     */
    default void populateMediaMetadata(MediaMetadata.Builder builder) {}
  }

  private final Entry[] entries;

  /**
   * 元数据的呈现时间，单位为微秒。
   *
   * <p>此时间是相对于当前 {@link Timeline.Period} 开始时间的偏移量。
   *
   * <p>当时间未知或未定义时，此时间为 {@link C#TIME_UNSET}。
   */
  public final long presentationTimeUs;

  /**
   * @param entries 元数据条目。
   */
  public Metadata(Entry... entries) {
    this(/* presentationTimeUs= */ C.TIME_UNSET, entries);
  }

  /**
   * @param presentationTimeUs 元数据条目的呈现时间。
   * @param entries 元数据条目。
   */
  public Metadata(long presentationTimeUs, Entry... entries) {
    this.presentationTimeUs = presentationTimeUs;
    this.entries = entries;
  }

  /**
   * @param entries 元数据条目。
   */
  public Metadata(List<? extends Entry> entries) {
    this(entries.toArray(new Entry[0]));
  }

  /**
   * @param presentationTimeUs 元数据条目的呈现时间。
   * @param entries 元数据条目。
   */
  public Metadata(long presentationTimeUs, List<? extends Entry> entries) {
    this(presentationTimeUs, entries.toArray(new Entry[0]));
  }

  /* package */ Metadata(Parcel in) {
    entries = new Metadata.Entry[in.readInt()];
    for (int i = 0; i < entries.length; i++) {
      entries[i] = in.readParcelable(Entry.class.getClassLoader());
    }
    presentationTimeUs = in.readLong();
  }

  /** 返回元数据条目的数量。 */
  public int length() {
    return entries.length;
  }

  /**
   * 返回指定索引处的条目。
   *
   * @param index 条目的索引。
   * @return 指定索引处的条目。
   */
  public Metadata.Entry get(int index) {
    return entries[index];
  }

  /**
   * 返回此元数据的副本，并附加指定元数据的条目。如果 {@code other} 为 null，则返回此实例。
   *
   * @param other 包含要附加条目的元数据。如果为 null，则返回此实例。
   * @return 包含附加条目的元数据实例。
   */
  public Metadata copyWithAppendedEntriesFrom(@Nullable Metadata other) {
    if (other == null) {
      return this;
    }
    return copyWithAppendedEntries(other.entries);
  }

  /**
   * 返回此元数据的副本，并附加指定的条目。
   *
   * @param entriesToAppend 要附加的条目。
   * @return 包含附加条目的元数据实例。
   */
  public Metadata copyWithAppendedEntries(Entry... entriesToAppend) {
    if (entriesToAppend.length == 0) {
      return this;
    }
    return new Metadata(
        presentationTimeUs, Util.nullSafeArrayConcatenation(entries, entriesToAppend));
  }

  /**
   * 返回此元数据的副本，并使用指定的呈现时间。
   *
   * @param presentationTimeUs 新的呈现时间，单位为微秒。
   * @return 包含新呈现时间的元数据实例。
   */
  public Metadata copyWithPresentationTimeUs(long presentationTimeUs) {
    if (this.presentationTimeUs == presentationTimeUs) {
      return this;
    }
    return new Metadata(presentationTimeUs, entries);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Metadata other = (Metadata) obj;
    return Arrays.equals(entries, other.entries) && presentationTimeUs == other.presentationTimeUs;
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(entries);
    result = 31 * result + Longs.hashCode(presentationTimeUs);
    return result;
  }

  @Override
  public String toString() {
    return "entries="
        + Arrays.toString(entries)
        + (presentationTimeUs == C.TIME_UNSET ? "" : ", presentationTimeUs=" + presentationTimeUs);
  }

  // Parcelable 实现。

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(entries.length);
    for (Entry entry : entries) {
      dest.writeParcelable(entry, 0);
    }
    dest.writeLong(presentationTimeUs);
  }

  public static final Parcelable.Creator<Metadata> CREATOR =
      new Parcelable.Creator<Metadata>() {
        @Override
        public Metadata createFromParcel(Parcel in) {
          return new Metadata(in);
        }

        @Override
        public Metadata[] newArray(int size) {
          return new Metadata[size];
        }
      };
}