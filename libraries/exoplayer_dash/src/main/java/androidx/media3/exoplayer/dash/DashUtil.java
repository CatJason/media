package androidx.media3.exoplayer.dash;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.dash.manifest.Period;
import androidx.media3.exoplayer.dash.manifest.RangedUri;
import androidx.media3.exoplayer.dash.manifest.Representation;
import androidx.media3.exoplayer.source.chunk.BundledChunkExtractor;
import androidx.media3.exoplayer.source.chunk.ChunkExtractor;
import androidx.media3.exoplayer.source.chunk.InitializationChunk;
import androidx.media3.exoplayer.upstream.ParsingLoadable;
import androidx.media3.extractor.ChunkIndex;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** DASH 流处理的实用方法。 */
@UnstableApi
public final class DashUtil {

  /**
   * 为属于 {@link Representation} 的给定 {@link RangedUri} 构建 {@link DataSpec}。
   *
   * @param representation 请求所属的 {@link Representation}。
   * @param baseUrl 用于解析请求 URI 的基础 URL。
   * @param requestUri 要请求的数据的 {@link RangedUri}。
   * @param flags 要设置在返回的 {@link DataSpec} 上的标志。参见 {@link DataSpec.Builder#setFlags(int)}。
   * @param httpRequestHeaders {@link DataSpec#httpRequestHeaders}。
   * @return 构建的 {@link DataSpec}。
   */
  public static DataSpec buildDataSpec(
      Representation representation,
      String baseUrl,
      RangedUri requestUri,
      int flags,
      Map<String, String> httpRequestHeaders) {
    return new DataSpec.Builder()
        .setUri(requestUri.resolveUri(baseUrl))
        .setPosition(requestUri.start)
        .setLength(requestUri.length)
        .setKey(resolveCacheKey(representation, requestUri))
        .setFlags(flags)
        .setHttpRequestHeaders(httpRequestHeaders)
        .build();
  }

  /**
   * @deprecated 请使用 {@link #buildDataSpec(Representation, String, RangedUri, int, Map)} 代替。
   */
  @Deprecated
  public static DataSpec buildDataSpec(
      Representation representation, String baseUrl, RangedUri requestUri, int flags) {
    return buildDataSpec(
        representation, baseUrl, requestUri, flags, /* httpRequestHeaders= */ ImmutableMap.of());
  }

  /**
   * @deprecated 请使用 {@link #buildDataSpec(Representation, String, RangedUri, int, Map)} 代替。
   */
  @Deprecated
  public static DataSpec buildDataSpec(
      Representation representation, RangedUri requestUri, int flags) {
    return buildDataSpec(
        representation,
        representation.baseUrls.get(0).url,
        requestUri,
        flags,
        /* httpRequestHeaders= */ ImmutableMap.of());
  }

  /**
   * 加载 DASH 清单。
   *
   * @param dataSource 用于读取清单的 {@link DataSource}。
   * @param uri 要读取的清单的 {@link Uri}。
   * @return {@link DashManifest} 的实例。
   * @throws IOException 加载过程中发生错误时抛出。
   */
  public static DashManifest loadManifest(DataSource dataSource, Uri uri) throws IOException {
    return ParsingLoadable.load(dataSource, new DashManifestParser(), uri, C.DATA_TYPE_MANIFEST);
  }

  /**
   * 加载用于获取 DASH 清单中给定时期的密钥的 {@link Format}。
   *
   * @param dataSource 用于加载数据的 {@link DataSource}。
   * @param period 给定的 {@link Period}。
   * @return 加载的 {@link Format}，如果未定义则返回 null。
   * @throws IOException 加载过程中发生错误时抛出。
   */
  @Nullable
  public static Format loadFormatWithDrmInitData(DataSource dataSource, Period period)
      throws IOException {
    @C.TrackType int primaryTrackType = C.TRACK_TYPE_VIDEO;
    Representation representation = getFirstRepresentation(period, primaryTrackType);
    if (representation == null) {
      primaryTrackType = C.TRACK_TYPE_AUDIO;
      representation = getFirstRepresentation(period, primaryTrackType);
      if (representation == null) {
        return null;
      }
    }
    Format manifestFormat = representation.format;
    @Nullable
    Format sampleFormat = DashUtil.loadSampleFormat(dataSource, primaryTrackType, representation);
    return sampleFormat == null
        ? manifestFormat
        : sampleFormat.withManifestFormatInfo(manifestFormat);
  }

  /**
   * 加载 {@code representation} 的初始化数据并返回样本 {@link Format}。
   *
   * @param dataSource 用于加载数据的源。
   * @param trackType 表示的类型。通常是 {@link C androidx.media3.common.C} 中的 {@code TRACK_TYPE_*} 常量之一。
   * @param representation 初始化块所属的表示。
   * @param baseUrlIndex 从 {@link Representation#baseUrls 基础 URL 列表} 中选择的基础 URL 的索引。
   * @return 给定表示的样本 {@link Format}。
   * @throws IOException 加载过程中发生错误时抛出。
   */
  @Nullable
  public static Format loadSampleFormat(
      DataSource dataSource, int trackType, Representation representation, int baseUrlIndex)
      throws IOException {
    if (representation.getInitializationUri() == null) {
      return null;
    }
    ChunkExtractor chunkExtractor = newChunkExtractor(trackType, representation.format);
    try {
      loadInitializationData(
          chunkExtractor, dataSource, representation, baseUrlIndex, /* loadIndex= */ false);
    } finally {
      chunkExtractor.release();
    }
    return Assertions.checkStateNotNull(chunkExtractor.getSampleFormats())[0];
  }

  /**
   * 加载 {@code representation} 的初始化数据并返回样本 {@link Format}。
   *
   * <p>使用第一个基础 URL 来加载格式。
   *
   * @param dataSource 用于加载数据的源。
   * @param trackType 表示的类型。通常是 {@link C androidx.media3.common.C} 中的 {@code TRACK_TYPE_*} 常量之一。
   * @param representation 初始化块所属的表示。
   * @return 给定表示的样本 {@link Format}。
   * @throws IOException 加载过程中发生错误时抛出。
   */
  @Nullable
  public static Format loadSampleFormat(
      DataSource dataSource, int trackType, Representation representation) throws IOException {
    return loadSampleFormat(dataSource, trackType, representation, /* baseUrlIndex= */ 0);
  }

  /**
   * 加载 {@code representation} 的初始化和索引数据并返回 {@link ChunkIndex}。
   *
   * @param dataSource 用于加载数据的源。
   * @param trackType 表示的类型。通常是 {@link C androidx.media3.common.C} 中的 {@code TRACK_TYPE_*} 常量之一。
   * @param representation 初始化块所属的表示。
   * @param baseUrlIndex 用于解析请求 URI 的基础 URL 的索引。
   * @return 给定表示的 {@link ChunkIndex}，如果没有初始化或索引数据则返回 null。
   * @throws IOException 加载过程中发生错误时抛出。
   */
  @Nullable
  public static ChunkIndex loadChunkIndex(
      DataSource dataSource, int trackType, Representation representation, int baseUrlIndex)
      throws IOException {
    if (representation.getInitializationUri() == null) {
      return null;
    }
    ChunkExtractor chunkExtractor = newChunkExtractor(trackType, representation.format);
    try {
      loadInitializationData(
          chunkExtractor, dataSource, representation, baseUrlIndex, /* loadIndex= */ true);
    } finally {
      chunkExtractor.release();
    }
    return chunkExtractor.getChunkIndex();
  }

  /**
   * 加载 {@code representation} 的初始化和索引数据并返回 {@link ChunkIndex}。
   *
   * <p>使用第一个基础 URL 来加载索引。
   *
   * @param dataSource 用于加载数据的源。
   * @param trackType 表示的类型。通常是 {@link C androidx.media3.common.C} 中的 {@code TRACK_TYPE_*} 常量之一。
   * @param representation 初始化块所属的表示。
   * @return 给定表示的 {@link ChunkIndex}，如果没有初始化或索引数据则返回 null。
   * @throws IOException 加载过程中发生错误时抛出。
   */
  @Nullable
  public static ChunkIndex loadChunkIndex(
      DataSource dataSource, int trackType, Representation representation) throws IOException {
    return loadChunkIndex(dataSource, trackType, representation, /* baseUrlIndex= */ 0);
  }

  /**
   * 加载 {@code representation} 的初始化数据，并可选地加载索引数据，然后返回包含输出的 {@link BundledChunkExtractor}。
   *
   * @param chunkExtractor 要使用的 {@link ChunkExtractor}。
   * @param dataSource 用于加载数据的源。
   * @param representation 初始化块所属的表示。
   * @param baseUrlIndex 用于解析请求 URI 的基础 URL 的索引。
   * @param loadIndex 是否也加载索引数据。
   * @throws IOException 加载过程中发生错误时抛出。
   */
  private static void loadInitializationData(
      ChunkExtractor chunkExtractor,
      DataSource dataSource,
      Representation representation,
      int baseUrlIndex,
      boolean loadIndex)
      throws IOException {
    RangedUri initializationUri = Assertions.checkNotNull(representation.getInitializationUri());
    @Nullable RangedUri requestUri;
    if (loadIndex) {
      @Nullable RangedUri indexUri = representation.getIndexUri();
      if (indexUri == null) {
        return;
      }
      // 初始化和索引数据通常存储在一起。尝试将两个请求合并为一个请求。
      requestUri =
          initializationUri.attemptMerge(indexUri, representation.baseUrls.get(baseUrlIndex).url);
      if (requestUri == null) {
        loadInitializationData(
            dataSource, representation, baseUrlIndex, chunkExtractor, initializationUri);
        requestUri = indexUri;
      }
    } else {
      requestUri = initializationUri;
    }
    loadInitializationData(dataSource, representation, baseUrlIndex, chunkExtractor, requestUri);
  }

  /**
   * 加载 {@code representation} 的初始化数据，并可选地加载索引数据，然后返回包含输出的 {@link BundledChunkExtractor}。
   *
   * <p>使用第一个基础 URL 来加载初始化数据。
   *
   * @param chunkExtractor 要使用的 {@link ChunkExtractor}。
   * @param dataSource 用于加载数据的源。
   * @param representation 初始化块所属的表示。
   * @param loadIndex 是否也加载索引数据。
   * @throws IOException 加载过程中发生错误时抛出。
   */
  public static void loadInitializationData(
      ChunkExtractor chunkExtractor,
      DataSource dataSource,
      Representation representation,
      boolean loadIndex)
      throws IOException {
    loadInitializationData(
        chunkExtractor, dataSource, representation, /* baseUrlIndex= */ 0, loadIndex);
  }

  private static void loadInitializationData(
      DataSource dataSource,
      Representation representation,
      int baseUrlIndex,
      ChunkExtractor chunkExtractor,
      RangedUri requestUri)
      throws IOException {
    DataSpec dataSpec =
        DashUtil.buildDataSpec(
            representation,
            representation.baseUrls.get(baseUrlIndex).url,
            requestUri,
            /* flags= */ 0,
            /* httpRequestHeaders= */ ImmutableMap.of());
    InitializationChunk initializationChunk =
        new InitializationChunk(
            dataSource,
            dataSpec,
            representation.format,
            C.SELECTION_REASON_UNKNOWN,
            /* trackSelectionData= */ null,
            chunkExtractor);
    initializationChunk.load();
  }

  /**
   * 解析用于请求给定 {@link Representation} 的给定范围 URI 的缓存键。
   *
   * @param representation URI 所属的 {@link Representation}。
   * @param rangedUri 要解析缓存键的 URI。
   * @return 缓存键。
   */
  public static String resolveCacheKey(Representation representation, RangedUri rangedUri) {
    @Nullable String cacheKey = representation.getCacheKey();
    return cacheKey != null
        ? cacheKey
        : rangedUri.resolveUri(representation.baseUrls.get(0).url).toString();
  }

  private static ChunkExtractor newChunkExtractor(int trackType, Format format) {
    String mimeType = format.containerMimeType;
    boolean isWebm =
        mimeType != null
            && (mimeType.startsWith(MimeTypes.VIDEO_WEBM)
            || mimeType.startsWith(MimeTypes.AUDIO_WEBM));
    Extractor extractor =
        isWebm
            ? new MatroskaExtractor(
            SubtitleParser.Factory.UNSUPPORTED, MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA)
            : new FragmentedMp4Extractor(
                SubtitleParser.Factory.UNSUPPORTED,
                FragmentedMp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA);
    return new BundledChunkExtractor(extractor, trackType, format);
  }

  @Nullable
  private static Representation getFirstRepresentation(Period period, @C.TrackType int type) {
    int index = period.getAdaptationSetIndex(type);
    if (index == C.INDEX_UNSET) {
      return null;
    }
    List<Representation> representations = period.adaptationSets.get(index).representations;
    return representations.isEmpty() ? null : representations.get(0);
  }

  private DashUtil() {}
}