package androidx.media3.extractor.metadata.mp4;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import com.google.common.primitives.Floats;

/**
 * 存储来自三星 smta 盒子的元数据。
 *
 * <p>参见 [Internal: b/150138465#comment76], [Internal: b/301273734#comment17]。
 */
@UnstableApi
public final class SmtaMetadataEntry implements Metadata.Entry {

  /**
   * 捕获帧率，单位为 fps，如果未知则为 {@link C#RATE_UNSET}。
   *
   * <p>如果已知，捕获帧率应始终为整数值。
   */
  public final float captureFrameRate;

  /** SVC 扩展帧中的层数。 */
  public final int svcTemporalLayerCount;

  /** 创建一个实例。 */
  public SmtaMetadataEntry(float captureFrameRate, int svcTemporalLayerCount) {
    this.captureFrameRate = captureFrameRate;
    this.svcTemporalLayerCount = svcTemporalLayerCount;
  }

  private SmtaMetadataEntry(Parcel in) {
    captureFrameRate = in.readFloat(); // 从 Parcel 中读取捕获帧率
    svcTemporalLayerCount = in.readInt(); // 从 Parcel 中读取 SVC 层数
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true; // 如果是同一个对象，返回 true
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false; // 如果对象为 null 或类型不同，返回 false
    }
    SmtaMetadataEntry other = (SmtaMetadataEntry) obj;
    return captureFrameRate == other.captureFrameRate
        && svcTemporalLayerCount == other.svcTemporalLayerCount; // 比较所有字段
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + Floats.hashCode(captureFrameRate); // 计算捕获帧率的哈希值
    result = 31 * result + svcTemporalLayerCount; // 计算 SVC 层数的哈希值
    return result; // 返回哈希值
  }

  @Override
  public String toString() {
    return "smta: captureFrameRate="
        + captureFrameRate
        + ", svcTemporalLayerCount="
        + svcTemporalLayerCount; // 返回对象的字符串表示
  }

  // Parcelable 实现。

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeFloat(captureFrameRate); // 将捕获帧率写入 Parcel
    dest.writeInt(svcTemporalLayerCount); // 将 SVC 层数写入 Parcel
  }

  @Override
  public int describeContents() {
    return 0; // 描述内容类型
  }

  public static final Parcelable.Creator<SmtaMetadataEntry> CREATOR =
      new Parcelable.Creator<SmtaMetadataEntry>() {

        @Override
        public SmtaMetadataEntry createFromParcel(Parcel in) {
          return new SmtaMetadataEntry(in); // 从 Parcel 创建实例
        }

        @Override
        public SmtaMetadataEntry[] newArray(int size) {
          return new SmtaMetadataEntry[size]; // 创建新数组
        }
      };
}