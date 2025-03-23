package androidx.media3.exoplayer.hls.playlist;

import androidx.annotation.Nullable;
import androidx.media3.common.StreamKey;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.FilteringManifestParser;
import androidx.media3.exoplayer.upstream.ParsingLoadable;
import java.util.List;

/**
 * 一个 {@link HlsPlaylistParserFactory}，仅包含由给定流密钥标识的流。
 */
@UnstableApi
public final class FilteringHlsPlaylistParserFactory implements HlsPlaylistParserFactory {

  private final HlsPlaylistParserFactory hlsPlaylistParserFactory;
  private final List<StreamKey> streamKeys;

  /**
   * @param hlsPlaylistParserFactory 用于解析将被过滤的播放列表的解析器工厂。
   * @param streamKeys 流密钥。如果为 null 或为空，则不进行过滤。
   */
  public FilteringHlsPlaylistParserFactory(
      HlsPlaylistParserFactory hlsPlaylistParserFactory, List<StreamKey> streamKeys) {
    this.hlsPlaylistParserFactory = hlsPlaylistParserFactory;
    this.streamKeys = streamKeys;
  }

  @Override
  public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser() {
    return new FilteringManifestParser<>(
        hlsPlaylistParserFactory.createPlaylistParser(), streamKeys);
  }

  @Override
  public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser(
      HlsMultivariantPlaylist multivariantPlaylist,
      @Nullable HlsMediaPlaylist previousMediaPlaylist) {
    return new FilteringManifestParser<>(
        hlsPlaylistParserFactory.createPlaylistParser(multivariantPlaylist, previousMediaPlaylist),
        streamKeys);
  }
}