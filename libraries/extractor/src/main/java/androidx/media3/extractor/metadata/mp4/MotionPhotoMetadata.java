package androidx.media3.extractor.metadata.mp4;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import com.google.common.primitives.Longs;

/** 运动照片文件的元数据。 */
@UnstableApi
public final class MotionPhotoMetadata implements Metadata.Entry {

  /** 照片数据的起始偏移量，单位为字节。 */
  public final long photoStartPosition;

  /** 照片数据的大小，单位为字节。 */
  public final long photoSize;

  /**
   * 照片的呈现时间戳，单位为微秒，如果未知则为 {@link C#TIME_UNSET}。
   */
  public final long photoPresentationTimestampUs;

  /** 视频数据的起始偏移量，单位为字节。 */
  public final long videoStartPosition;

  /** 视频数据的大小，单位为字节。 */
  public final long videoSize;

  /** 创建一个实例。 */
  public MotionPhotoMetadata(
      long photoStartPosition,
      long photoSize,
      long photoPresentationTimestampUs,
      long videoStartPosition,
      long videoSize) {
    this.photoStartPosition = photoStartPosition;
    this.photoSize = photoSize;
    this.photoPresentationTimestampUs = photoPresentationTimestampUs;
    this.videoStartPosition = videoStartPosition;
    this.videoSize = videoSize;
  }

  private MotionPhotoMetadata(Parcel in) {
    photoStartPosition = in.readLong();
    photoSize = in.readLong();
    photoPresentationTimestampUs = in.readLong();
    videoStartPosition = in.readLong();
    videoSize = in.readLong();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true; // 如果是同一个对象，返回 true
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false; // 如果对象为 null 或类型不同，返回 false
    }
    MotionPhotoMetadata other = (MotionPhotoMetadata) obj;
    return photoStartPosition == other.photoStartPosition
        && photoSize == other.photoSize
        && photoPresentationTimestampUs == other.photoPresentationTimestampUs
        && videoStartPosition == other.videoStartPosition
        && videoSize == other.videoSize; // 比较所有字段
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + Longs.hashCode(photoStartPosition);
    result = 31 * result + Longs.hashCode(photoSize);
    result = 31 * result + Longs.hashCode(photoPresentationTimestampUs);
    result = 31 * result + Longs.hashCode(videoStartPosition);
    result = 31 * result + Longs.hashCode(videoSize);
    return result; // 计算哈希值
  }

  @Override
  public String toString() {
    return "运动照片元数据: photoStartPosition="
        + photoStartPosition
        + ", photoSize="
        + photoSize
        + ", photoPresentationTimestampUs="
        + photoPresentationTimestampUs
        + ", videoStartPosition="
        + videoStartPosition
        + ", videoSize="
        + videoSize; // 返回字符串表示
  }

  // Parcelable 实现。

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(photoStartPosition);
    dest.writeLong(photoSize);
    dest.writeLong(photoPresentationTimestampUs);
    dest.writeLong(videoStartPosition);
    dest.writeLong(videoSize); // 将数据写入 Parcel
  }

  @Override
  public int describeContents() {
    return 0; // 描述内容类型
  }

  public static final Parcelable.Creator<MotionPhotoMetadata> CREATOR =
      new Parcelable.Creator<MotionPhotoMetadata>() {

        @Override
        public MotionPhotoMetadata createFromParcel(Parcel in) {
          return new MotionPhotoMetadata(in); // 从 Parcel 创建实例
        }

        @Override
        public MotionPhotoMetadata[] newArray(int size) {
          return new MotionPhotoMetadata[size]; // 创建新数组
        }
      };
}