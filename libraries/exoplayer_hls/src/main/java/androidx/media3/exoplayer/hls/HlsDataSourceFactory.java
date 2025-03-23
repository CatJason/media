package androidx.media3.exoplayer.hls;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;

/** 为 HLS 播放列表、加密和媒体块创建 {@link DataSource}。 */
@UnstableApi
public interface HlsDataSourceFactory {

  /**
   * 为给定的数据类型创建 {@link DataSource}。
   *
   * @param dataType {@link DataSource} 将用于的 {@link C.DataType}。
   * @return 用于给定数据类型的 {@link DataSource}。
   */
  DataSource createDataSource(@C.DataType int dataType);
}