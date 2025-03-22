package androidx.media3.extractor.metadata.vorbis;

import android.os.Parcel;
import androidx.media3.common.util.UnstableApi;

/** 从 FLAC 或 Ogg 文件中提取的 Vorbis 注释。 */
@SuppressWarnings("deprecation") // 为了向后兼容，扩展了已弃用的类型。
@UnstableApi
public final class VorbisComment extends androidx.media3.extractor.metadata.flac.VorbisComment {

  /**
   * @param key 键。
   * @param value 值。
   */
  public VorbisComment(String key, String value) {
    super(key, value); // 调用父类构造函数
  }

  /* package */ VorbisComment(Parcel in) {
    super(in); // 从 Parcel 中读取数据
  }

  public static final Creator<VorbisComment> CREATOR =
      new Creator<VorbisComment>() {

        @Override
        public VorbisComment createFromParcel(Parcel in) {
          return new VorbisComment(in); // 从 Parcel 创建实例
        }

        @Override
        public VorbisComment[] newArray(int size) {
          return new VorbisComment[size]; // 创建新数组
        }
      };
}