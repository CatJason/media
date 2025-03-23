package androidx.media3.exoplayer.hls.playlist;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.hls.HlsDataSourceFactory;
import androidx.media3.exoplayer.source.MediaSourceEventListener.EventDispatcher;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import java.io.IOException;

/**
 * 跟踪与 HLS 流关联的播放列表并提供快照。
 *
 * <p>播放列表跟踪器负责暴露由播放列表暴露的片段定义的搜索窗口。此播放列表称为主播放列表，在直播流的情况下需要定期刷新。注意，主播放列表是媒体播放列表之一，而多变量播放列表是 HLS 规范（RFC 8216）定义的一种可选播放列表类型。
 *
 * <p>播放列表加载可能会遇到错误。跟踪器可能会选择排除它们，以确保始终有一个主播放列表可用。
 */
@UnstableApi
public interface HlsPlaylistTracker {

  /** {@link HlsPlaylistTracker} 实例的工厂接口。 */
  interface Factory {

    /**
     * 创建一个新的跟踪器实例。
     *
     * @param dataSourceFactory 用于播放列表加载的 {@link HlsDataSourceFactory}。
     * @param loadErrorHandlingPolicy 用于播放列表加载错误的 {@link LoadErrorHandlingPolicy}。
     * @param playlistParserFactory 用于播放列表解析的 {@link HlsPlaylistParserFactory}。
     */
    HlsPlaylistTracker createTracker(
        HlsDataSourceFactory dataSourceFactory,
        LoadErrorHandlingPolicy loadErrorHandlingPolicy,
        HlsPlaylistParserFactory playlistParserFactory);
  }

  /** 主播放列表变化的监听器。 */
  interface PrimaryPlaylistListener {

    /**
     * 当主播放列表发生变化时调用。
     *
     * @param mediaPlaylist 主播放列表的新快照。
     */
    void onPrimaryPlaylistRefreshed(HlsMediaPlaylist mediaPlaylist);
  }

  /** 在播放列表加载事件时调用。 */
  interface PlaylistEventListener {

    /** 当播放列表发生变化时调用。 */
    void onPlaylistChanged();

    /**
     * 当加载播放列表时遇到错误时调用。
     *
     * @param url 导致错误的加载 URL。
     * @param loadErrorInfo 加载错误信息。
     * @param forceRetry 是否在不考虑排除的情况下强制重试。
     * @return 如果排除未遇到错误则返回 true，否则返回 false。
     */
    boolean onPlaylistError(
        Uri url, LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo, boolean forceRetry);
  }

  /** 当播放列表由于服务器端错误被认为卡住时抛出。 */
  final class PlaylistStuckException extends IOException {

    /** 卡住的播放列表的 URL。 */
    public final Uri url;

    /**
     * 创建实例。
     *
     * @param url 参见 {@link #url}。
     */
    public PlaylistStuckException(Uri url) {
      this.url = url;
    }
  }

  /** 当新快照的媒体序列指示服务器已重置时抛出。 */
  final class PlaylistResetException extends IOException {

    /** 重置的播放列表的 URL。 */
    public final Uri url;

    /**
     * 创建实例。
     *
     * @param url 参见 {@link #url}。
     */
    public PlaylistResetException(Uri url) {
      this.url = url;
    }
  }

  /**
   * 启动播放列表跟踪器。
   *
   * <p>必须在播放线程中调用。跟踪器可以在 {@link #stop()} 调用后重新启动。
   *
   * @param initialPlaylistUri HLS 流的 URI。可以指向媒体播放列表或多变量播放列表。
   * @param eventDispatcher 用于通知事件的分发器。
   * @param primaryPlaylistListener 用于主播放列表变化事件的回调。
   */
  void start(
      Uri initialPlaylistUri,
      EventDispatcher eventDispatcher,
      PrimaryPlaylistListener primaryPlaylistListener);

  /**
   * 停止播放列表跟踪器并释放所有获取的资源。
   *
   * <p>每次 {@link #start} 调用后必须调用一次。
   */
  void stop();

  /**
   * 注册监听器以接收来自播放列表跟踪器的事件。
   *
   * @param listener 监听器。
   */
  void addListener(PlaylistEventListener listener);

  /**
   * 取消注册监听器。
   *
   * @param listener 要取消注册的监听器。
   */
  void removeListener(PlaylistEventListener listener);

  /**
   * 返回多变量播放列表。
   *
   * <p>如果传递给 {@link #start} 的 URI 指向媒体播放列表，则返回包含该媒体播放列表的单个变体的 {@link HlsMultivariantPlaylist}。
   *
   * @return 多变量播放列表。如果初始播放列表尚未加载，则返回 null。
   */
  @Nullable
  HlsMultivariantPlaylist getMultivariantPlaylist();

  /**
   * 返回由提供的 {@link Uri} 引用的播放列表的最新快照。
   *
   * @param url 请求的媒体播放列表对应的 {@link Uri}。
   * @param isForPlayback 调用者是否可能使用快照来请求媒体片段进行播放。如果为 true，主播放列表可能会更新为请求的播放列表。
   * @return 由提供的 {@link Uri} 引用的播放列表的最新快照。如果尚未加载快照，则可能为 null。
   */
  @Nullable
  HlsMediaPlaylist getPlaylistSnapshot(Uri url, boolean isForPlayback);

  /**
   * 返回第一个加载的主播放列表的开始时间，如果尚未加载媒体播放列表，则返回 {@link C#TIME_UNSET}。
   */
  long getInitialStartTimeUs();

  /**
   * 返回由提供的 {@link Uri} 引用的播放列表的快照是否有效，即播放列表引用的所有片段预计都可用。如果播放列表无效，则某些片段可能不再可用。
   *
   * @param url {@link Uri}。
   * @return 由提供的 {@link Uri} 引用的播放列表的快照是否有效。
   */
  boolean isSnapshotValid(Uri url);

  /**
   * 如果跟踪器在刷新多变量播放列表或主播放列表时遇到问题，此方法会抛出底层错误。否则，不执行任何操作。
   *
   * @throws IOException 底层错误。
   */
  void maybeThrowPrimaryPlaylistRefreshError() throws IOException;

  /**
   * 如果播放列表在刷新由给定 {@link Uri} 引用的播放列表时遇到问题，此方法会抛出底层错误。
   *
   * @param url {@link Uri}。
   * @throws IOException 底层错误。
   */
  void maybeThrowPlaylistRefreshError(Uri url) throws IOException;

  /**
   * 在给定的持续时间内（以毫秒为单位）排除给定的媒体播放列表。
   *
   * @param playlistUrl 媒体播放列表的 URL。
   * @param exclusionDurationMs 排除播放列表的持续时间。
   * @return 排除是否成功。
   */
  boolean excludeMediaPlaylist(Uri playlistUrl, long exclusionDurationMs);

  /**
   * 请求刷新播放列表并将其从排除列表中移除。
   *
   * <p>播放列表跟踪器可能会选择延迟播放列表刷新。如果刷新已经在等待中，则丢弃该请求。
   *
   * @param url 要刷新的播放列表的 {@link Uri}。
   */
  void refreshPlaylist(Uri url);

  /**
   * 返回跟踪的播放列表是否描述了一个直播流。
   *
   * @return 如果内容是直播的则返回 true，否则返回 false。
   */
  boolean isLive();

  /**
   * 停用播放列表以进行播放。
   *
   * <p>默认实现为空操作。
   *
   * @param url 要停用以进行播放的播放列表的 {@link Uri}。
   */
  default void deactivatePlaylistForPlayback(Uri url) {}
}
