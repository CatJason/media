package androidx.media3.exoplayer.hls.playlist;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.net.Uri;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.StreamKey;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Represents an HLS media playlist. */
@UnstableApi
public final class HlsMediaPlaylist extends HlsPlaylist {

  /** 服务器控制属性。 */
  public static final class ServerControl {

    /**
     * 增量更新的跳过边界（以微秒为单位），如果不支持增量更新，则为 {@link C#TIME_UNSET}。
     */
    public final long skipUntilUs;

    /**
     * 播放列表是否可以生成跳过较旧的 #EXT-X-DATERANGE 标签以及媒体片段的增量更新。
     */
    public final boolean canSkipDateRanges;

    /**
     * 服务器推荐的直播偏移量（以微秒为单位），如果未定义则为 {@link C#TIME_UNSET}。
     */
    public final long holdBackUs;

    /**
     * 低延迟模式下服务器推荐的直播偏移量（以微秒为单位），如果未定义则为 {@link C#TIME_UNSET}。
     */
    public final long partHoldBackUs;

    /** 服务器是否支持阻塞播放列表重新加载。 */
    public final boolean canBlockReload;

    /**
     * 创建一个新实例。
     *
     * @param skipUntilUs 参见 {@link #skipUntilUs}。
     * @param canSkipDateRanges 参见 {@link #canSkipDateRanges}。
     * @param holdBackUs 参见 {@link #holdBackUs}。
     * @param partHoldBackUs 参见 {@link #partHoldBackUs}。
     * @param canBlockReload 参见 {@link #canBlockReload}。
     */
    public ServerControl(
        long skipUntilUs,
        boolean canSkipDateRanges,
        long holdBackUs,
        long partHoldBackUs,
        boolean canBlockReload) {
      this.skipUntilUs = skipUntilUs;
      this.canSkipDateRanges = canSkipDateRanges;
      this.holdBackUs = holdBackUs;
      this.partHoldBackUs = partHoldBackUs;
      this.canBlockReload = canBlockReload;
    }
  }
  /** 媒体片段引用。 */
  @SuppressWarnings("ComparableType")
  public static final class Segment extends SegmentBase {

    /** 片段的人类可读标题。 */
    public final String title;

    /** 属于此片段的部分。 */
    public final List<Part> parts;

    /**
     * 创建一个用作初始化片段的实例。
     *
     * @param uri 参见 {@link #url}。
     * @param byteRangeOffset 参见 {@link #byteRangeOffset}。
     * @param byteRangeLength 参见 {@link #byteRangeLength}。
     * @param fullSegmentEncryptionKeyUri 参见 {@link #fullSegmentEncryptionKeyUri}。
     * @param encryptionIV 参见 {@link #encryptionIV}。
     */
    public Segment(
        String uri,
        long byteRangeOffset,
        long byteRangeLength,
        @Nullable String fullSegmentEncryptionKeyUri,
        @Nullable String encryptionIV) {
      this(
          uri,
          /* initializationSegment= */ null,
          /* title= */ "",
          /* durationUs= */ 0,
          /* relativeDiscontinuitySequence= */ -1,
          /* relativeStartTimeUs= */ C.TIME_UNSET,
          /* drmInitData= */ null,
          fullSegmentEncryptionKeyUri,
          encryptionIV,
          byteRangeOffset,
          byteRangeLength,
          /* hasGapTag= */ false,
          /* parts= */ ImmutableList.of());
    }

    /**
     * 创建一个实例。
     *
     * @param url 参见 {@link #url}。
     * @param initializationSegment 参见 {@link #initializationSegment}。
     * @param title 参见 {@link #title}。
     * @param durationUs 参见 {@link #durationUs}。
     * @param relativeDiscontinuitySequence 参见 {@link #relativeDiscontinuitySequence}。
     * @param relativeStartTimeUs 参见 {@link #relativeStartTimeUs}。
     * @param drmInitData 参见 {@link #drmInitData}。
     * @param fullSegmentEncryptionKeyUri 参见 {@link #fullSegmentEncryptionKeyUri}。
     * @param encryptionIV 参见 {@link #encryptionIV}。
     * @param byteRangeOffset 参见 {@link #byteRangeOffset}。
     * @param byteRangeLength 参见 {@link #byteRangeLength}。
     * @param hasGapTag 参见 {@link #hasGapTag}。
     * @param parts 参见 {@link #parts}。
     */
    public Segment(
        String url,
        @Nullable Segment initializationSegment,
        String title,
        long durationUs,
        int relativeDiscontinuitySequence,
        long relativeStartTimeUs,
        @Nullable DrmInitData drmInitData,
        @Nullable String fullSegmentEncryptionKeyUri,
        @Nullable String encryptionIV,
        long byteRangeOffset,
        long byteRangeLength,
        boolean hasGapTag,
        List<Part> parts) {
      super(
          url,
          initializationSegment,
          durationUs,
          relativeDiscontinuitySequence,
          relativeStartTimeUs,
          drmInitData,
          fullSegmentEncryptionKeyUri,
          encryptionIV,
          byteRangeOffset,
          byteRangeLength,
          hasGapTag);
      this.title = title;
      this.parts = ImmutableList.copyOf(parts);
    }

    public Segment copyWith(long relativeStartTimeUs, int relativeDiscontinuitySequence) {
      List<Part> updatedParts = new ArrayList<>();
      long relativePartStartTimeUs = relativeStartTimeUs;
      for (int i = 0; i < parts.size(); i++) {
        Part part = parts.get(i);
        updatedParts.add(part.copyWith(relativePartStartTimeUs, relativeDiscontinuitySequence));
        relativePartStartTimeUs += part.durationUs;
      }
      return new Segment(
          url,
          initializationSegment,
          title,
          durationUs,
          relativeDiscontinuitySequence,
          relativeStartTimeUs,
          drmInitData,
          fullSegmentEncryptionKeyUri,
          encryptionIV,
          byteRangeOffset,
          byteRangeLength,
          hasGapTag,
          updatedParts);
    }
  }

  /** 媒体部分。 */
  public static final class Part extends SegmentBase {

    /** 该部分是否独立。 */
    public final boolean isIndependent;

    /** 该部分是否为预加载部分。 */
    public final boolean isPreload;

    /**
     * 创建一个实例。
     *
     * @param url 参见 {@link #url}。
     * @param initializationSegment 参见 {@link #initializationSegment}。
     * @param durationUs 参见 {@link #durationUs}。
     * @param relativeDiscontinuitySequence 参见 {@link #relativeDiscontinuitySequence}。
     * @param relativeStartTimeUs 参见 {@link #relativeStartTimeUs}。
     * @param drmInitData 参见 {@link #drmInitData}。
     * @param fullSegmentEncryptionKeyUri 参见 {@link #fullSegmentEncryptionKeyUri}。
     * @param encryptionIV 参见 {@link #encryptionIV}。
     * @param byteRangeOffset 参见 {@link #byteRangeOffset}。
     * @param byteRangeLength 参见 {@link #byteRangeLength}。
     * @param hasGapTag 参见 {@link #hasGapTag}。
     * @param isIndependent 参见 {@link #isIndependent}。
     * @param isPreload 参见 {@link #isPreload}。
     */
    public Part(
        String url,
        @Nullable Segment initializationSegment,
        long durationUs,
        int relativeDiscontinuitySequence,
        long relativeStartTimeUs,
        @Nullable DrmInitData drmInitData,
        @Nullable String fullSegmentEncryptionKeyUri,
        @Nullable String encryptionIV,
        long byteRangeOffset,
        long byteRangeLength,
        boolean hasGapTag,
        boolean isIndependent,
        boolean isPreload) {
      super(
          url,
          initializationSegment,
          durationUs,
          relativeDiscontinuitySequence,
          relativeStartTimeUs,
          drmInitData,
          fullSegmentEncryptionKeyUri,
          encryptionIV,
          byteRangeOffset,
          byteRangeLength,
          hasGapTag);
      this.isIndependent = isIndependent;
      this.isPreload = isPreload;
    }

    public Part copyWith(long relativeStartTimeUs, int relativeDiscontinuitySequence) {
      return new Part(
          url,
          initializationSegment,
          durationUs,
          relativeDiscontinuitySequence,
          relativeStartTimeUs,
          drmInitData,
          fullSegmentEncryptionKeyUri,
          encryptionIV,
          byteRangeOffset,
          byteRangeLength,
          hasGapTag,
          isIndependent,
          isPreload);
    }
  }
  /** {@link Segment} 或 {@link Part} 的基类，用于播放所需。 */
  @SuppressWarnings("ComparableType")
  public static class SegmentBase implements Comparable<Long> {
    /** 片段的 URL。 */
    public final String url;

    /**
     * 该片段的媒体初始化部分，由 #EXT-X-MAP 定义。如果媒体播放列表未为该片段定义媒体初始化部分，则可能为 null。所有共享 EXT-X-MAP 标签的片段使用相同的实例。
     */
    @Nullable public final Segment initializationSegment;

    /** 片段的持续时间（以微秒为单位），由 #EXTINF 或 #EXT-X-PART 定义。 */
    public final long durationUs;

    /** 播放列表中片段之前的 #EXT-X-DISCONTINUITY 标签数量。 */
    public final int relativeDiscontinuitySequence;

    /** 片段的开始时间（以微秒为单位），相对于播放列表的开始时间。 */
    public final long relativeStartTimeUs;

    /**
     * 用于样本解密的 DRM 初始化数据，如果片段不使用 CDM-DRM 保护，则为 null。
     */
    @Nullable public final DrmInitData drmInitData;

    /**
     * 由 #EXT-X-KEY 定义的加密身份密钥 URI，如果片段不使用完整片段加密与身份密钥，则为 null。
     */
    @Nullable public final String fullSegmentEncryptionKeyUri;

    /**
     * 由 #EXT-X-KEY 定义的加密初始化向量，如果片段未加密，则为 null。
     */
    @Nullable public final String encryptionIV;

    /**
     * 片段的字节范围偏移量，由 #EXT-X-BYTERANGE、#EXT-X-PART 或 #EXT-X-PRELOAD-HINT 定义。
     */
    public final long byteRangeOffset;

    /**
     * 片段的字节范围长度，由 #EXT-X-BYTERANGE、#EXT-X-PART 或 #EXT-X-PRELOAD-HINT 定义，如果未指定字节范围或字节范围为开放式，则为 {@link C#LENGTH_UNSET}。
     */
    public final long byteRangeLength;

    /** 该片段是否被标记为间隙。 */
    public final boolean hasGapTag;

    private SegmentBase(
        String url,
        @Nullable Segment initializationSegment,
        long durationUs,
        int relativeDiscontinuitySequence,
        long relativeStartTimeUs,
        @Nullable DrmInitData drmInitData,
        @Nullable String fullSegmentEncryptionKeyUri,
        @Nullable String encryptionIV,
        long byteRangeOffset,
        long byteRangeLength,
        boolean hasGapTag) {
      this.url = url;
      this.initializationSegment = initializationSegment;
      this.durationUs = durationUs;
      this.relativeDiscontinuitySequence = relativeDiscontinuitySequence;
      this.relativeStartTimeUs = relativeStartTimeUs;
      this.drmInitData = drmInitData;
      this.fullSegmentEncryptionKeyUri = fullSegmentEncryptionKeyUri;
      this.encryptionIV = encryptionIV;
      this.byteRangeOffset = byteRangeOffset;
      this.byteRangeLength = byteRangeLength;
      this.hasGapTag = hasGapTag;
    }

    @Override
    public int compareTo(Long relativeStartTimeUs) {
      return this.relativeStartTimeUs > relativeStartTimeUs
          ? 1
          : (this.relativeStartTimeUs < relativeStartTimeUs ? -1 : 0);
    }
  }
  /**
   * 针对在其他媒体播放列表中定义的替代渲染的渲染报告。
   *
   * <p>参见 RFC 8216，第 4.4.5.1.4 节。
   */
  public static final class RenditionReport {
    /** 报告的渲染的媒体播放列表的 URI。 */
    public final Uri playlistUri;

    /** 报告的渲染的播放列表中的最后一个媒体序列。 */
    public final long lastMediaSequence;

    /**
     * 报告的渲染的播放列表中的最后一个部分索引，如果渲染不包含部分片段，则为 {@link C#INDEX_UNSET}。
     */
    public final int lastPartIndex;

    /**
     * 创建一个新实例。
     *
     * @param playlistUri 参见 {@link #playlistUri}。
     * @param lastMediaSequence 参见 {@link #lastMediaSequence}。
     * @param lastPartIndex 参见 {@link #lastPartIndex}。
     */
    public RenditionReport(Uri playlistUri, long lastMediaSequence, int lastPartIndex) {
      this.playlistUri = playlistUri;
      this.lastMediaSequence = lastMediaSequence;
      this.lastPartIndex = lastPartIndex;
    }
  }

  /**
   * 播放列表的类型，由 #EXT-X-PLAYLIST-TYPE 定义。可以是 {@link #PLAYLIST_TYPE_UNKNOWN}、{@link #PLAYLIST_TYPE_VOD} 或 {@link #PLAYLIST_TYPE_EVENT} 之一。
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({PLAYLIST_TYPE_UNKNOWN, PLAYLIST_TYPE_VOD, PLAYLIST_TYPE_EVENT})
  public @interface PlaylistType {}
  public static final int PLAYLIST_TYPE_UNKNOWN = 0;
  public static final int PLAYLIST_TYPE_VOD = 1;
  public static final int PLAYLIST_TYPE_EVENT = 2;
  /** 播放列表的类型。参见 {@link PlaylistType}。 */
  public final @PlaylistType int playlistType;

  /**
   * 从播放列表开始处的起始偏移量（以微秒为单位），由 #EXT-X-START 定义，如果未定义则为 {@link C#TIME_UNSET}。该值保证在 0 到 {@link #durationUs} 之间（含）。
   */
  public final long startOffsetUs;

  /**
   * {@link #startOffsetUs} 是否由 #EXT-X-START 明确定义为正值或零。
   */
  public final boolean hasPositiveStartOffset;

  /** 起始位置是否应精确，由 #EXT-X-START 定义。 */
  public final boolean preciseStart;

  /**
   * 如果 {@link #hasProgramDateTime} 为 true，则包含自纪元以来的微秒数表示的日期时间。否则，包含到此播放列表快照为止已移除片段的累计持续时间。
   */
  public final long startTimeUs;

  /** 播放列表是否包含 #EXT-X-DISCONTINUITY-SEQUENCE 标签。 */
  public final boolean hasDiscontinuitySequence;

  /**
   * 播放列表中第一个媒体片段的不连续序列号，由 #EXT-X-DISCONTINUITY-SEQUENCE 定义。
   */
  public final int discontinuitySequence;

  /**
   * 播放列表中第一个媒体片段的媒体序列号，由 #EXT-X-MEDIA-SEQUENCE 定义。
   */
  public final long mediaSequence;

  /** 兼容版本，由 #EXT-X-VERSION 定义。 */
  public final int version;

  /** 目标持续时间（以微秒为单位），由 #EXT-X-TARGETDURATION 定义。 */
  public final long targetDurationUs;

  /**
   * 片段部分的目标持续时间，由 #EXT-X-PART-INF 定义，如果未定义则为 {@link C#TIME_UNSET}。
   */
  public final long partTargetDurationUs;

  /** 播放列表是否包含 #EXT-X-ENDLIST 标签。 */
  public final boolean hasEndTag;

  /** 播放列表是否包含 #EXT-X-PROGRAM-DATE-TIME 标签。 */
  public final boolean hasProgramDateTime;

  /**
   * 包含此播放列表中片段使用的 CDM 保护方案。不包含任何密钥获取数据。如果播放列表中没有片段是 CDM 加密的，则为 null。
   */
  @Nullable public final DrmInitData protectionSchemes;

  /** 播放列表中的片段列表。 */
  public final List<Segment> segments;

  /**
   * 播放列表末尾的部分列表，其片段尚未在播放列表中。
   */
  public final List<Part> trailingParts;

  /** 替代渲染播放列表的渲染报告。 */
  public final Map<Uri, RenditionReport> renditionReports;

  /** 播放列表的总持续时间（以微秒为单位）。 */
  public final long durationUs;

  /** #EXT-X-SERVER-CONTROL 标头的属性。 */
  public final ServerControl serverControl;

  /**
   * 构造一个实例。
   *
   * @param playlistType 参见 {@link #playlistType}。
   * @param baseUri 参见 {@link #baseUri}。
   * @param tags 参见 {@link #tags}。
   * @param startOffsetUs 参见 {@link #startOffsetUs}。
   * @param preciseStart 参见 {@link #preciseStart}。
   * @param startTimeUs 参见 {@link #startTimeUs}。
   * @param hasDiscontinuitySequence 参见 {@link #hasDiscontinuitySequence}。
   * @param discontinuitySequence 参见 {@link #discontinuitySequence}。
   * @param mediaSequence 参见 {@link #mediaSequence}。
   * @param version 参见 {@link #version}。
   * @param targetDurationUs 参见 {@link #targetDurationUs}。
   * @param partTargetDurationUs 参见 {@link #partTargetDurationUs}。
   * @param hasIndependentSegments 参见 {@link #hasIndependentSegments}。
   * @param hasEndTag 参见 {@link #hasEndTag}。
   * @param hasProgramDateTime 参见 {@link #hasProgramDateTime}。
   * @param protectionSchemes 参见 {@link #protectionSchemes}。
   * @param segments 参见 {@link #segments}。
   * @param trailingParts 参见 {@link #trailingParts}。
   * @param serverControl 参见 {@link #serverControl}。
   * @param renditionReports 参见 {@link #renditionReports}。
   */
  public HlsMediaPlaylist(
      @PlaylistType int playlistType,
      String baseUri,
      List<String> tags,
      long startOffsetUs,
      boolean preciseStart,
      long startTimeUs,
      boolean hasDiscontinuitySequence,
      int discontinuitySequence,
      long mediaSequence,
      int version,
      long targetDurationUs,
      long partTargetDurationUs,
      boolean hasIndependentSegments,
      boolean hasEndTag,
      boolean hasProgramDateTime,
      @Nullable DrmInitData protectionSchemes,
      List<Segment> segments,
      List<Part> trailingParts,
      ServerControl serverControl,
      Map<Uri, RenditionReport> renditionReports) {
    super(baseUri, tags, hasIndependentSegments);
    this.playlistType = playlistType;
    this.startTimeUs = startTimeUs;
    this.preciseStart = preciseStart;
    this.hasDiscontinuitySequence = hasDiscontinuitySequence;
    this.discontinuitySequence = discontinuitySequence;
    this.mediaSequence = mediaSequence;
    this.version = version;
    this.targetDurationUs = targetDurationUs;
    this.partTargetDurationUs = partTargetDurationUs;
    this.hasEndTag = hasEndTag;
    this.hasProgramDateTime = hasProgramDateTime;
    this.protectionSchemes = protectionSchemes;
    this.segments = ImmutableList.copyOf(segments);
    this.trailingParts = ImmutableList.copyOf(trailingParts);
    this.renditionReports = ImmutableMap.copyOf(renditionReports);
    if (!trailingParts.isEmpty()) {
      Part lastPart = Iterables.getLast(trailingParts);
      durationUs = lastPart.relativeStartTimeUs + lastPart.durationUs;
    } else if (!segments.isEmpty()) {
      Segment lastSegment = Iterables.getLast(segments);
      durationUs = lastSegment.relativeStartTimeUs + lastSegment.durationUs;
    } else {
      durationUs = 0;
    }
    // 根据 RFC 8216 第 4.4.2.2 节：如果 startOffsetUs 为负值，则表示从播放列表末尾的偏移量。
    // 如果其绝对值超过播放列表的持续时间，则表示播放列表的开始（如果为负）或结束（如果为正）。
    this.startOffsetUs =
        startOffsetUs == C.TIME_UNSET
            ? C.TIME_UNSET
            : startOffsetUs >= 0
                ? min(durationUs, startOffsetUs)
                : max(0, durationUs + startOffsetUs);
    this.hasPositiveStartOffset = startOffsetUs >= 0;
    this.serverControl = serverControl;
  }

  @Override
  public HlsMediaPlaylist copy(List<StreamKey> streamKeys) {
    return this;
  }

  /**
   * 返回此播放列表是否比 {@code other} 更新。
   *
   * @param other 要比较的播放列表。
   * @return 此播放列表是否比 {@code other} 更新。
   */
  public boolean isNewerThan(@Nullable HlsMediaPlaylist other) {
    if (other == null || mediaSequence > other.mediaSequence) {
      return true;
    }
    if (mediaSequence < other.mediaSequence) {
      return false;
    }
    // The media sequences are equal.
    int segmentCountDifference = segments.size() - other.segments.size();
    if (segmentCountDifference != 0) {
      return segmentCountDifference > 0;
    }
    int partCount = trailingParts.size();
    int otherPartCount = other.trailingParts.size();
    return partCount > otherPartCount
        || (partCount == otherPartCount && hasEndTag && !other.hasEndTag);
  }

  /** 返回播放列表的持续时间与其开始时间相加的结果。 */
  public long getEndTimeUs() {
    return startTimeUs + durationUs;
  }

  /**
   * 返回与此播放列表相同的播放列表，但起始时间、不连续序列和 {@code hasDiscontinuitySequence} 值除外。前两个值设置为指定值，{@code hasDiscontinuitySequence} 设置为 true。
   *
   * @param startTimeUs 返回的播放列表的起始时间。
   * @param discontinuitySequence 返回的播放列表的不连续序列。
   * @return 包含提供的不连续和时序信息的相同播放列表。
   */
  public HlsMediaPlaylist copyWith(long startTimeUs, int discontinuitySequence) {
    return new HlsMediaPlaylist(
        playlistType,
        baseUri,
        tags,
        startOffsetUs,
        preciseStart,
        startTimeUs,
        /* hasDiscontinuitySequence= */ true,
        discontinuitySequence,
        mediaSequence,
        version,
        targetDurationUs,
        partTargetDurationUs,
        hasIndependentSegments,
        hasEndTag,
        hasProgramDateTime,
        protectionSchemes,
        segments,
        trailingParts,
        serverControl,
        renditionReports);
  }

  /**
   * 返回与此播放列表相同的播放列表，但添加了一个结束标签。如果已经存在结束标签，则返回播放列表本身。
   */
  public HlsMediaPlaylist copyWithEndTag() {
    if (this.hasEndTag) {
      return this;
    }
    return new HlsMediaPlaylist(
        playlistType,
        baseUri,
        tags,
        startOffsetUs,
        preciseStart,
        startTimeUs,
        hasDiscontinuitySequence,
        discontinuitySequence,
        mediaSequence,
        version,
        targetDurationUs,
        partTargetDurationUs,
        hasIndependentSegments,
        /* hasEndTag= */ true,
        hasProgramDateTime,
        protectionSchemes,
        segments,
        trailingParts,
        serverControl,
        renditionReports);
  }
}
