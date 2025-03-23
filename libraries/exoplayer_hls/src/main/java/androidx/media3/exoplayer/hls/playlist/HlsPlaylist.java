package androidx.media3.exoplayer.hls.playlist;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.FilterableManifest;
import java.util.Collections;
import java.util.List;

/** 表示一个 HLS 播放列表。 */
@UnstableApi
public abstract class HlsPlaylist implements FilterableManifest<HlsPlaylist> {

  /** 基础 URI。用于解析相对路径。 */
  public final String baseUri;

  /** 播放列表中的标签列表。 */
  public final List<String> tags;

  /**
   * 媒体是否由独立片段组成，由 #EXT-X-INDEPENDENT-SEGMENTS 标签定义。
   */
  public final boolean hasIndependentSegments;

  /**
   * @param baseUri 参见 {@link #baseUri}。
   * @param tags 参见 {@link #tags}。
   * @param hasIndependentSegments 参见 {@link #hasIndependentSegments}。
   */
  protected HlsPlaylist(String baseUri, List<String> tags, boolean hasIndependentSegments) {
    this.baseUri = baseUri;
    this.tags = Collections.unmodifiableList(tags);
    this.hasIndependentSegments = hasIndependentSegments;
  }
}