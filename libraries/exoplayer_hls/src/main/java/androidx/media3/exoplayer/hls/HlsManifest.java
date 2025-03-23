package androidx.media3.exoplayer.hls;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist;

/** 包含一个多变量播放列表及其一个媒体播放列表的快照。 */
@UnstableApi
public final class HlsManifest {

  /** HLS 流的多变量播放列表。 */
  public final HlsMultivariantPlaylist multivariantPlaylist;

  /** {@link #multivariantPlaylist} 所引用的媒体播放列表的快照。 */
  public final HlsMediaPlaylist mediaPlaylist;

  /**
   * @param multivariantPlaylist 多变量播放列表。
   * @param mediaPlaylist 媒体播放列表。
   */
  /* package */ HlsManifest(
      HlsMultivariantPlaylist multivariantPlaylist, HlsMediaPlaylist mediaPlaylist) {
    this.multivariantPlaylist = multivariantPlaylist;
    this.mediaPlaylist = mediaPlaylist;
  }
}