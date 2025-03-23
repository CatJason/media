package androidx.media3.exoplayer.hls.playlist;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.StreamKey;
import androidx.media3.common.util.UnstableApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 表示一个 HLS 多变量播放列表。 */
@UnstableApi
public final class HlsMultivariantPlaylist extends HlsPlaylist {

  /** 表示一个空的多变量播放列表，无法继承任何属性。 */
  public static final HlsMultivariantPlaylist EMPTY =
      new HlsMultivariantPlaylist(
          /* baseUri= */ "",
          /* tags= */ Collections.emptyList(),
          /* variants= */ Collections.emptyList(),
          /* videos= */ Collections.emptyList(),
          /* audios= */ Collections.emptyList(),
          /* subtitles= */ Collections.emptyList(),
          /* closedCaptions= */ Collections.emptyList(),
          /* muxedAudioFormat= */ null,
          /* muxedCaptionFormats= */ Collections.emptyList(),
          /* hasIndependentSegments= */ false,
          /* variableDefinitions= */ Collections.emptyMap(),
          /* sessionKeyDrmInitData= */ Collections.emptyList());

  // 这些常量不能被更改，因为它们会被持久化在离线流密钥中。
  public static final int GROUP_INDEX_VARIANT = 0;
  public static final int GROUP_INDEX_AUDIO = 1;
  public static final int GROUP_INDEX_SUBTITLE = 2;

  /** 多变量播放列表中的一个变体（即一个 #EXT-X-STREAM-INF 标签）。 */
  public static final class Variant {

    /** 变体的 URL。 */
    public final Uri url;

    /** 与此变体关联的格式信息。 */
    public final Format format;

    /** 此变体引用的视频渲染组 ID，或 {@code null}。 */
    @Nullable public final String videoGroupId;

    /** 此变体引用的音频渲染组 ID，或 {@code null}。 */
    @Nullable public final String audioGroupId;

    /** 此变体引用的字幕渲染组 ID，或 {@code null}。 */
    @Nullable public final String subtitleGroupId;

    /** 此变体引用的字幕渲染组 ID，或 {@code null}。 */
    @Nullable public final String captionGroupId;

    /**
     * @param url 参见 {@link #url}。
     * @param format 参见 {@link #format}。
     * @param videoGroupId 参见 {@link #videoGroupId}。
     * @param audioGroupId 参见 {@link #audioGroupId}。
     * @param subtitleGroupId 参见 {@link #subtitleGroupId}。
     * @param captionGroupId 参见 {@link #captionGroupId}。
     */
    public Variant(
        Uri url,
        Format format,
        @Nullable String videoGroupId,
        @Nullable String audioGroupId,
        @Nullable String subtitleGroupId,
        @Nullable String captionGroupId) {
      this.url = url;
      this.format = format;
      this.videoGroupId = videoGroupId;
      this.audioGroupId = audioGroupId;
      this.subtitleGroupId = subtitleGroupId;
      this.captionGroupId = captionGroupId;
    }

    /**
     * 为给定的媒体播放列表 URL 创建一个变体。
     *
     * @param url 媒体播放列表的 URL。
     * @return 变体实例。
     */
    public static Variant createMediaPlaylistVariantUrl(Uri url) {
      Format format =
          new Format.Builder().setId("0").setContainerMimeType(MimeTypes.APPLICATION_M3U8).build();
      return new Variant(
          url,
          format,
          /* videoGroupId= */ null,
          /* audioGroupId= */ null,
          /* subtitleGroupId= */ null,
          /* captionGroupId= */ null);
    }

    /** 返回一个具有给定 {@link Format} 的此实例的副本。 */
    public Variant copyWithFormat(Format format) {
      return new Variant(url, format, videoGroupId, audioGroupId, subtitleGroupId, captionGroupId);
    }
  }

  /** 多变量播放列表中的一个渲染（即一个 #EXT-X-MEDIA 标签）。 */
  public static final class Rendition {

    /** 渲染的 URL，如果标签没有 URI 属性则为 null。 */
    @Nullable public final Uri url;

    /** 与此渲染关联的格式信息。 */
    public final Format format;

    /** 此渲染所属的组 ID。 */
    public final String groupId;

    /** 渲染的名称。 */
    public final String name;

    /**
     * @param url 参见 {@link #url}。
     * @param format 参见 {@link #format}。
     * @param groupId 参见 {@link #groupId}。
     * @param name 参见 {@link #name}。
     */
    public Rendition(@Nullable Uri url, Format format, String groupId, String name) {
      this.url = url;
      this.format = format;
      this.groupId = groupId;
      this.name = name;
    }
  }

  /** 播放列表中引用的所有媒体播放列表 URL。 */
  public final List<Uri> mediaPlaylistUrls;

  /** 播放列表声明的变体。 */
  public final List<Variant> variants;

  /** 播放列表声明的视频渲染。 */
  public final List<Rendition> videos;

  /** 播放列表声明的音频渲染。 */
  public final List<Rendition> audios;

  /** 播放列表声明的字幕渲染。 */
  public final List<Rendition> subtitles;

  /** 播放列表声明的隐藏字幕渲染。 */
  public final List<Rendition> closedCaptions;

  /**
   * 变体中混音的音频格式。如果播放列表未声明任何混音音频，则可能为 null。
   */
  @Nullable public final Format muxedAudioFormat;

  /**
   * 播放列表声明的隐藏字幕格式。如果播放列表明确声明没有可用的字幕，则可能为空；
   * 如果播放列表未声明任何字幕信息，则可能为 null。
   */
  @Nullable public final List<Format> muxedCaptionFormats;

  /** 包含变量定义，由 #EXT-X-DEFINE 标签定义。 */
  public final Map<String, String> variableDefinitions;

  /** 从 #EXT-X-SESSION-KEY 标签派生的 DRM 初始化数据。 */
  public final List<DrmInitData> sessionKeyDrmInitData;

  /**
   * @param baseUri 参见 {@link #baseUri}。
   * @param tags 参见 {@link #tags}。
   * @param variants 参见 {@link #variants}。
   * @param videos 参见 {@link #videos}。
   * @param audios 参见 {@link #audios}。
   * @param subtitles 参见 {@link #subtitles}。
   * @param closedCaptions 参见 {@link #closedCaptions}。
   * @param muxedAudioFormat 参见 {@link #muxedAudioFormat}。
   * @param muxedCaptionFormats 参见 {@link #muxedCaptionFormats}。
   * @param hasIndependentSegments 参见 {@link #hasIndependentSegments}。
   * @param variableDefinitions 参见 {@link #variableDefinitions}。
   * @param sessionKeyDrmInitData 参见 {@link #sessionKeyDrmInitData}。
   */
  public HlsMultivariantPlaylist(
      String baseUri,
      List<String> tags,
      List<Variant> variants,
      List<Rendition> videos,
      List<Rendition> audios,
      List<Rendition> subtitles,
      List<Rendition> closedCaptions,
      @Nullable Format muxedAudioFormat,
      @Nullable List<Format> muxedCaptionFormats,
      boolean hasIndependentSegments,
      Map<String, String> variableDefinitions,
      List<DrmInitData> sessionKeyDrmInitData) {
    super(baseUri, tags, hasIndependentSegments);
    this.mediaPlaylistUrls =
        Collections.unmodifiableList(
            getMediaPlaylistUrls(variants, videos, audios, subtitles, closedCaptions));
    this.variants = Collections.unmodifiableList(variants);
    this.videos = Collections.unmodifiableList(videos);
    this.audios = Collections.unmodifiableList(audios);
    this.subtitles = Collections.unmodifiableList(subtitles);
    this.closedCaptions = Collections.unmodifiableList(closedCaptions);
    this.muxedAudioFormat = muxedAudioFormat;
    this.muxedCaptionFormats =
        muxedCaptionFormats != null ? Collections.unmodifiableList(muxedCaptionFormats) : null;
    this.variableDefinitions = Collections.unmodifiableMap(variableDefinitions);
    this.sessionKeyDrmInitData = Collections.unmodifiableList(sessionKeyDrmInitData);
  }

  @Override
  public HlsMultivariantPlaylist copy(List<StreamKey> streamKeys) {
    return new HlsMultivariantPlaylist(
        baseUri,
        tags,
        copyStreams(variants, GROUP_INDEX_VARIANT, streamKeys),
        // TODO: 允许流密钥指定要保留的视频渲染。
        /* videos= */ Collections.emptyList(),
        copyStreams(audios, GROUP_INDEX_AUDIO, streamKeys),
        copyStreams(subtitles, GROUP_INDEX_SUBTITLE, streamKeys),
        // TODO: 更新以保留所有隐藏字幕。
        /* closedCaptions= */ Collections.emptyList(),
        muxedAudioFormat,
        muxedCaptionFormats,
        hasIndependentSegments,
        variableDefinitions,
        sessionKeyDrmInitData);
  }

  /**
   * 创建一个包含单个变体的播放列表。
   *
   * @param variantUrl 单个变体的 URL。
   * @return 一个包含提供的 URL 的单个变体的多变量播放列表。
   */
  public static HlsMultivariantPlaylist createSingleVariantMultivariantPlaylist(String variantUrl) {
    List<Variant> variant =
        Collections.singletonList(Variant.createMediaPlaylistVariantUrl(Uri.parse(variantUrl)));
    return new HlsMultivariantPlaylist(
        /* baseUri= */ "",
        /* tags= */ Collections.emptyList(),
        variant,
        /* videos= */ Collections.emptyList(),
        /* audios= */ Collections.emptyList(),
        /* subtitles= */ Collections.emptyList(),
        /* closedCaptions= */ Collections.emptyList(),
        /* muxedAudioFormat= */ null,
        /* muxedCaptionFormats= */ null,
        /* hasIndependentSegments= */ false,
        /* variableDefinitions= */ Collections.emptyMap(),
        /* sessionKeyDrmInitData= */ Collections.emptyList());
  }

  private static List<Uri> getMediaPlaylistUrls(
      List<Variant> variants,
      List<Rendition> videos,
      List<Rendition> audios,
      List<Rendition> subtitles,
      List<Rendition> closedCaptions) {
    ArrayList<Uri> mediaPlaylistUrls = new ArrayList<>();
    for (int i = 0; i < variants.size(); i++) {
      Uri uri = variants.get(i).url;
      if (!mediaPlaylistUrls.contains(uri)) {
        mediaPlaylistUrls.add(uri);
      }
    }
    addMediaPlaylistUrls(videos, mediaPlaylistUrls);
    addMediaPlaylistUrls(audios, mediaPlaylistUrls);
    addMediaPlaylistUrls(subtitles, mediaPlaylistUrls);
    addMediaPlaylistUrls(closedCaptions, mediaPlaylistUrls);
    return mediaPlaylistUrls;
  }

  private static void addMediaPlaylistUrls(List<Rendition> renditions, List<Uri> out) {
    for (int i = 0; i < renditions.size(); i++) {
      Uri uri = renditions.get(i).url;
      if (uri != null && !out.contains(uri)) {
        out.add(uri);
      }
    }
  }

  private static <T> List<T> copyStreams(
      List<T> streams, int groupIndex, List<StreamKey> streamKeys) {
    List<T> copiedStreams = new ArrayList<>(streamKeys.size());
    // TODO:
    // 1. 当具有相同 URL 的变体未被去重时，重复项不应增加 trackIndex，以避免破坏已持久化的离线流密钥。
    //    如果第一个变体被复制，则应复制所有重复项，否则应丢弃所有重复项。
    // 2. 当允许具有 null URL 的渲染时，它们不应增加 trackIndex，以避免破坏已持久化的离线流密钥。
    //    所有具有 null URL 的渲染都应被复制。如果所有引用它们的变体都被移除，它们可能会变得不可访问，但这是可以接受的。
    // 3. 与复制的变体 URL 匹配的渲染应始终被复制，即使对应的流密钥被省略。否则，我们是在无意义地丢弃信息。
    for (int i = 0; i < streams.size(); i++) {
      T stream = streams.get(i);
      for (int j = 0; j < streamKeys.size(); j++) {
        StreamKey streamKey = streamKeys.get(j);
        if (streamKey.groupIndex == groupIndex && streamKey.streamIndex == i) {
          copiedStreams.add(stream);
          break;
        }
      }
    }
    return copiedStreams;
  }
}