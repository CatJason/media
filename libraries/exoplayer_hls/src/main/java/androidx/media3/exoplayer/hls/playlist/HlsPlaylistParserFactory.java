package androidx.media3.exoplayer.hls.playlist;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.upstream.ParsingLoadable;

/** {@link HlsPlaylist} 解析器的工厂接口。 */
@UnstableApi
public interface HlsPlaylistParserFactory {

  /**
   * 返回一个独立的播放列表解析器。由该解析器解析的播放列表不会从其他播放列表继承任何属性。
   */
  ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser();

  /**
   * 返回一个用于解析由给定 {@link HlsMultivariantPlaylist} 引用的播放列表的解析器。
   * 返回的 {@link HlsMediaPlaylist} 实例可能会从 {@code multivariantPlaylist} 继承属性。
   *
   * @param multivariantPlaylist 引用任何解析的媒体播放列表的多变量播放列表。
   * @param previousMediaPlaylist 先前的媒体播放列表，如果没有先前的媒体播放列表则为 null。
   * @return 用于解析 HLS 播放列表的解析器。
   */
  ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser(
      HlsMultivariantPlaylist multivariantPlaylist,
      @Nullable HlsMediaPlaylist previousMediaPlaylist);
}