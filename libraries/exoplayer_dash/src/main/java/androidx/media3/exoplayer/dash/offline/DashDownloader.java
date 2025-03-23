package androidx.media3.exoplayer.dash.offline;

import static androidx.media3.common.util.Util.castNonNull;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.RunnableFutureTask;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.exoplayer.dash.BaseUrlExclusionList;
import androidx.media3.exoplayer.dash.DashSegmentIndex;
import androidx.media3.exoplayer.dash.DashUtil;
import androidx.media3.exoplayer.dash.DashWrappingSegmentIndex;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.dash.manifest.Period;
import androidx.media3.exoplayer.dash.manifest.RangedUri;
import androidx.media3.exoplayer.dash.manifest.Representation;
import androidx.media3.exoplayer.offline.DownloadException;
import androidx.media3.exoplayer.offline.SegmentDownloader;
import androidx.media3.exoplayer.upstream.ParsingLoadable.Parser;
import androidx.media3.extractor.ChunkIndex;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

// LINT.IfChange(javadoc)
/**
 * 用于下载 DASH 流的下载器。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * SimpleCache cache = new SimpleCache(downloadFolder, new NoOpCacheEvictor(), databaseProvider);
 * CacheDataSource.Factory cacheDataSourceFactory =
 *     new CacheDataSource.Factory()
 *         .setCache(cache)
 *         .setUpstreamDataSourceFactory(new DefaultHttpDataSource.Factory());
 * // 为第一个周期的第一个自适应集的第一个表示创建下载器。
 * DashDownloader dashDownloader =
 *     new DashDownloader(
 *         new MediaItem.Builder()
 *             .setUri(manifestUrl)
 *             .setStreamKeys(Collections.singletonList(new StreamKey(0, 0, 0)))
 *             .build(),
 *         cacheDataSourceFactory);
 * // 执行下载。
 * dashDownloader.download(progressListener);
 * // 使用下载的数据进行播放。
 * DashMediaSource mediaSource =
 *     new DashMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem);
 * }</pre>
 */
@UnstableApi
public final class DashDownloader extends SegmentDownloader<DashManifest> {

  private final BaseUrlExclusionList baseUrlExclusionList;

  /**
   * 创建一个新的实例。
   *
   * @param mediaItem 要下载的 {@link MediaItem}。
   * @param cacheDataSourceFactory 用于写入下载数据的 {@link CacheDataSource.Factory}。
   */
  public DashDownloader(MediaItem mediaItem, CacheDataSource.Factory cacheDataSourceFactory) {
    this(mediaItem, cacheDataSourceFactory, Runnable::run);
  }

  /**
   * 创建一个新的实例。
   *
   * @param mediaItem 要下载的 {@link MediaItem}。
   * @param cacheDataSourceFactory 用于写入下载数据的 {@link CacheDataSource.Factory}。
   * @param executor 用于执行下载请求的 {@link Executor}。提供一个使用多线程的 {@link Executor} 可以加速下载，
   *     因为它允许下载的某些部分并行执行。
   */
  public DashDownloader(
      MediaItem mediaItem, CacheDataSource.Factory cacheDataSourceFactory, Executor executor) {
    this(
        mediaItem,
        new DashManifestParser(),
        cacheDataSourceFactory,
        executor,
        DEFAULT_MAX_MERGED_SEGMENT_START_TIME_DIFF_MS);
  }

  /**
   * @deprecated Use {@link DashDownloader#DashDownloader(MediaItem, Parser,
   *     CacheDataSource.Factory, Executor, long)} instead.
   */
  @Deprecated
  public DashDownloader(
      MediaItem mediaItem,
      Parser<DashManifest> manifestParser,
      CacheDataSource.Factory cacheDataSourceFactory,
      Executor executor) {
    this(
        mediaItem,
        manifestParser,
        cacheDataSourceFactory,
        executor,
        DEFAULT_MAX_MERGED_SEGMENT_START_TIME_DIFF_MS);
  }

  /**
   * 创建一个新的实例。
   *
   * @param mediaItem 要下载的 {@link MediaItem}。
   * @param manifestParser 用于解析 DASH 清单的解析器。
   * @param cacheDataSourceFactory 用于写入下载数据的 {@link CacheDataSource.Factory}。
   * @param executor 用于执行下载请求的 {@link Executor}。提供一个使用多线程的 {@link Executor} 可以加速下载，
   *     因为它允许下载的某些部分并行执行。
   * @param maxMergedSegmentStartTimeDiffMs 两个分段的最大开始时间差（以毫秒为单位），如果小于该值，
   *     则相同 URI 的分段将被合并为单个下载分段。
   */
  public DashDownloader(
      MediaItem mediaItem,
      Parser<DashManifest> manifestParser,
      CacheDataSource.Factory cacheDataSourceFactory,
      Executor executor,
      long maxMergedSegmentStartTimeDiffMs) {
    super(
        mediaItem,
        manifestParser,
        cacheDataSourceFactory,
        executor,
        maxMergedSegmentStartTimeDiffMs);
    baseUrlExclusionList = new BaseUrlExclusionList();
  }

  @Override
  protected List<Segment> getSegments(
      DataSource dataSource, DashManifest manifest, boolean removing)
      throws IOException, InterruptedException {
    ArrayList<Segment> segments = new ArrayList<>();
    for (int i = 0; i < manifest.getPeriodCount(); i++) {
      Period period = manifest.getPeriod(i);
      long periodStartUs = Util.msToUs(period.startMs);
      long periodDurationUs = manifest.getPeriodDurationUs(i);
      List<AdaptationSet> adaptationSets = period.adaptationSets;
      for (int j = 0; j < adaptationSets.size(); j++) {
        addSegmentsForAdaptationSet(
            dataSource, adaptationSets.get(j), periodStartUs, periodDurationUs, removing, segments);
      }
    }
    return segments;
  }

  private void addSegmentsForAdaptationSet(
      DataSource dataSource,
      AdaptationSet adaptationSet,
      long periodStartUs,
      long periodDurationUs,
      boolean removing,
      ArrayList<Segment> out)
      throws IOException, InterruptedException {
    for (int i = 0; i < adaptationSet.representations.size(); i++) {
      Representation representation = adaptationSet.representations.get(i);
      DashSegmentIndex index;
      try {
        // 尝试获取分段索引
        index = getSegmentIndex(dataSource, adaptationSet.type, representation, removing);
        if (index == null) {
          // 加载成功，但没有找到索引
          throw new DownloadException("Missing segment index");
        }
      } catch (IOException e) {
        if (!removing) {
          // 如果不是在移除操作中，则抛出异常
          throw e;
        }
        // 允许生成不完整的分段列表，继续处理下一个表示
        continue;
      }

      long segmentCount = index.getSegmentCount(periodDurationUs);
      if (segmentCount == DashSegmentIndex.INDEX_UNBOUNDED) {
        throw new DownloadException("Unbounded segment index");
      }

      String baseUrl = castNonNull(baseUrlExclusionList.selectBaseUrl(representation.baseUrls)).url;
      @Nullable RangedUri initializationUri = representation.getInitializationUri();
      if (initializationUri != null) {
        out.add(createSegment(representation, baseUrl, periodStartUs, initializationUri));
      }
      @Nullable RangedUri indexUri = representation.getIndexUri();
      if (indexUri != null) {
        out.add(createSegment(representation, baseUrl, periodStartUs, indexUri));
      }
      long firstSegmentNum = index.getFirstSegmentNum();
      long lastSegmentNum = firstSegmentNum + segmentCount - 1;
      for (long j = firstSegmentNum; j <= lastSegmentNum; j++) {
        out.add(
            createSegment(
                representation,
                baseUrl,
                periodStartUs + index.getTimeUs(j),
                index.getSegmentUrl(j)));
      }
    }
  }

  private Segment createSegment(
      Representation representation, String baseUrl, long startTimeUs, RangedUri rangedUri) {
    DataSpec dataSpec =
        DashUtil.buildDataSpec(
            representation,
            baseUrl,
            rangedUri,
            /* flags= */ 0,
            /* httpRequestHeaders= */ ImmutableMap.of());
    return new Segment(startTimeUs, dataSpec);
  }

  @Nullable
  private DashSegmentIndex getSegmentIndex(
      DataSource dataSource, int trackType, Representation representation, boolean removing)
      throws IOException, InterruptedException {
    DashSegmentIndex index = representation.getIndex();
    if (index != null) {
      return index;
    }
    RunnableFutureTask<@NullableType ChunkIndex, IOException> runnable =
        new RunnableFutureTask<@NullableType ChunkIndex, IOException>() {
          @Override
          protected @NullableType ChunkIndex doWork() throws IOException {
            return DashUtil.loadChunkIndex(dataSource, trackType, representation);
          }
        };
    @Nullable ChunkIndex seekMap = execute(runnable, removing);
    return seekMap == null
        ? null
        : new DashWrappingSegmentIndex(seekMap, representation.presentationTimeOffsetUs);
  }
}
