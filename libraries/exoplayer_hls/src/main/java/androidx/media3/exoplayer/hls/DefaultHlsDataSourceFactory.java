package androidx.media3.exoplayer.hls;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;

/** {@link HlsDataSourceFactory} 的默认实现。 */
@UnstableApi
public final class DefaultHlsDataSourceFactory implements HlsDataSourceFactory {

  private final DataSource.Factory dataSourceFactory;

  /**
   * @param dataSourceFactory 用于所有数据类型的 {@link DataSource.Factory}。
   */
  public DefaultHlsDataSourceFactory(DataSource.Factory dataSourceFactory) {
    this.dataSourceFactory = dataSourceFactory;
  }

  @Override
  public DataSource createDataSource(@C.DataType int dataType) {
    return dataSourceFactory.createDataSource();
  }
}