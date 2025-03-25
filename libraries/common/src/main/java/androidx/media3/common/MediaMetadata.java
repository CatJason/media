package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkArgument;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link MediaItem} 的元数据、播放列表，或来自多个 {@link Metadata} 源的组合元数据。
 */
public final class MediaMetadata {

  /** {@link MediaMetadata} 实例的构建器。 */
  public static final class Builder {

    @Nullable private CharSequence title; // 媒体标题
    @Nullable private CharSequence artist; // 艺术家
    @Nullable private CharSequence albumTitle; // 专辑标题
    @Nullable private CharSequence albumArtist; // 专辑艺术家
    @Nullable private CharSequence displayTitle; // 显示标题
    @Nullable private CharSequence subtitle; // 副标题
    @Nullable private CharSequence description; // 描述
    @Nullable private Long durationMs; // 持续时间（毫秒）
    @Nullable private Rating userRating; // 用户评分
    @Nullable private Rating overallRating; // 总体评分
    @Nullable private byte[] artworkData; // 封面图片数据
    @Nullable private @PictureType Integer artworkDataType; // 封面图片数据类型
    @Nullable private Uri artworkUri; // 封面图片 URI
    @Nullable private Integer trackNumber; // 曲目编号
    @Nullable private Integer totalTrackCount; // 总曲目数

    @SuppressWarnings("deprecation") // 已弃用字段的构建器
    @Nullable
    private @FolderType Integer folderType; // 文件夹类型（已弃用）

    @Nullable private Boolean isBrowsable; // 是否可浏览
    @Nullable private Boolean isPlayable; // 是否可播放
    @Nullable private Integer recordingYear; // 录制年份
    @Nullable private Integer recordingMonth; // 录制月份
    @Nullable private Integer recordingDay; // 录制日期
    @Nullable private Integer releaseYear; // 发行年份
    @Nullable private Integer releaseMonth; // 发行月份
    @Nullable private Integer releaseDay; // 发行日期
    @Nullable private CharSequence writer; // 作词者
    @Nullable private CharSequence composer; // 作曲者
    @Nullable private CharSequence conductor; // 指挥者
    @Nullable private Integer discNumber; // 光盘编号
    @Nullable private Integer totalDiscCount; // 总光盘数
    @Nullable private CharSequence genre; // 流派
    @Nullable private CharSequence compilation; // 合辑
    @Nullable private CharSequence station; // 电台
    @Nullable private @MediaType Integer mediaType; // 媒体类型
    @Nullable private Bundle extras; // 额外信息（键值对）
    private ImmutableList<String> supportedCommands; // 支持的命令列表

    public Builder() {
      supportedCommands = ImmutableList.of();
    }

    @SuppressWarnings("deprecation") // Assigning from deprecated fields.
    private Builder(MediaMetadata mediaMetadata) {
      this.title = mediaMetadata.title;
      this.artist = mediaMetadata.artist;
      this.albumTitle = mediaMetadata.albumTitle;
      this.albumArtist = mediaMetadata.albumArtist;
      this.displayTitle = mediaMetadata.displayTitle;
      this.subtitle = mediaMetadata.subtitle;
      this.description = mediaMetadata.description;
      this.durationMs = mediaMetadata.durationMs;
      this.userRating = mediaMetadata.userRating;
      this.overallRating = mediaMetadata.overallRating;
      this.artworkData = mediaMetadata.artworkData;
      this.artworkDataType = mediaMetadata.artworkDataType;
      this.artworkUri = mediaMetadata.artworkUri;
      this.trackNumber = mediaMetadata.trackNumber;
      this.totalTrackCount = mediaMetadata.totalTrackCount;
      this.folderType = mediaMetadata.folderType;
      this.isBrowsable = mediaMetadata.isBrowsable;
      this.isPlayable = mediaMetadata.isPlayable;
      this.recordingYear = mediaMetadata.recordingYear;
      this.recordingMonth = mediaMetadata.recordingMonth;
      this.recordingDay = mediaMetadata.recordingDay;
      this.releaseYear = mediaMetadata.releaseYear;
      this.releaseMonth = mediaMetadata.releaseMonth;
      this.releaseDay = mediaMetadata.releaseDay;
      this.writer = mediaMetadata.writer;
      this.composer = mediaMetadata.composer;
      this.conductor = mediaMetadata.conductor;
      this.discNumber = mediaMetadata.discNumber;
      this.totalDiscCount = mediaMetadata.totalDiscCount;
      this.genre = mediaMetadata.genre;
      this.compilation = mediaMetadata.compilation;
      this.station = mediaMetadata.station;
      this.mediaType = mediaMetadata.mediaType;
      this.supportedCommands = mediaMetadata.supportedCommands;
      this.extras = mediaMetadata.extras;
    }

    /** 设置标题。 */
    @CanIgnoreReturnValue
    public Builder setTitle(@Nullable CharSequence title) {
      this.title = title;
      return this;
    }

    /** 设置艺术家。 */
    @CanIgnoreReturnValue
    public Builder setArtist(@Nullable CharSequence artist) {
      this.artist = artist;
      return this;
    }

    /** 设置专辑标题。 */
    @CanIgnoreReturnValue
    public Builder setAlbumTitle(@Nullable CharSequence albumTitle) {
      this.albumTitle = albumTitle;
      return this;
    }

    /** 设置专辑艺术家。 */
    @CanIgnoreReturnValue
    public Builder setAlbumArtist(@Nullable CharSequence albumArtist) {
      this.albumArtist = albumArtist;
      return this;
    }

    /** 设置显示标题。 */
    @CanIgnoreReturnValue
    public Builder setDisplayTitle(@Nullable CharSequence displayTitle) {
      this.displayTitle = displayTitle;
      return this;
    }

    /**
     * 设置副标题。
     *
     * <p>这是媒体的次要标题，与隐藏式字幕无关。
     */
    @CanIgnoreReturnValue
    public Builder setSubtitle(@Nullable CharSequence subtitle) {
      this.subtitle = subtitle;
      return this;
    }

    /** 设置描述。 */
    @CanIgnoreReturnValue
    public Builder setDescription(@Nullable CharSequence description) {
      this.description = description;
      return this;
    }

    /**
     * 设置可选的持续时间，非负值，单位为毫秒。
     *
     * <p>持续时间由应用程序在构建元数据对象时填充，仅用于信息目的。要获取当前播放媒体项的持续时间，
     * 请使用 {@link Player#getDuration()}。
     *
     * @throws IllegalArgumentException 如果持续时间为负数。
     */
    @UnstableApi
    @CanIgnoreReturnValue
    public Builder setDurationMs(@Nullable Long durationMs) {
      checkArgument(durationMs == null || durationMs >= 0);
      this.durationMs = durationMs;
      return this;
    }

    /** 设置用户 {@link Rating}。 */
    @CanIgnoreReturnValue
    public Builder setUserRating(@Nullable Rating userRating) {
      this.userRating = userRating;
      return this;
    }

    /** 设置总体 {@link Rating}。 */
    @CanIgnoreReturnValue
    public Builder setOverallRating(@Nullable Rating overallRating) {
      this.overallRating = overallRating;
      return this;
    }

    /**
     * @deprecated 使用 {@link #setArtworkData(byte[] data, Integer pictureType)} 或 {@link
     *     #maybeSetArtworkData(byte[] data, int pictureType)}，并提供 {@link PictureType}。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setArtworkData(@Nullable byte[] artworkData) {
      return setArtworkData(artworkData, /* artworkDataType= */ null);
    }

    /**
     * 设置封面图片数据为压缩的字节数组，并关联 {@link PictureType artworkDataType}。
     */
    @CanIgnoreReturnValue
    public Builder setArtworkData(
        @Nullable byte[] artworkData, @Nullable @PictureType Integer artworkDataType) {
      this.artworkData = artworkData == null ? null : artworkData.clone();
      this.artworkDataType = artworkDataType;
      return this;
    }

    /**
     * 在以下情况下设置封面图片数据为压缩的字节数组：
     * - 关联的 {@link PictureType} 是 {@link #PICTURE_TYPE_FRONT_COVER}；
     * - 当前的 {@link PictureType} 不是 {@link #PICTURE_TYPE_FRONT_COVER}；
     * - 当前的 `artworkData` 未设置。
     *
     * <p>使用 {@link #setArtworkData(byte[], Integer)} 可以在不检查 {@link PictureType} 的情况下设置封面图片数据。
     */
    @CanIgnoreReturnValue
    public Builder maybeSetArtworkData(byte[] artworkData, @PictureType int artworkDataType) {
      if (this.artworkData == null
          || Util.areEqual(artworkDataType, PICTURE_TYPE_FRONT_COVER)
          || !Util.areEqual(this.artworkDataType, PICTURE_TYPE_FRONT_COVER)) {
        this.artworkData = artworkData.clone();
        this.artworkDataType = artworkDataType;
      }
      return this;
    }

    /** 设置封面图片的 {@link Uri}。 */
    @CanIgnoreReturnValue
    public Builder setArtworkUri(@Nullable Uri artworkUri) {
      this.artworkUri = artworkUri;
      return this;
    }

    /** 设置曲目编号。 */
    @CanIgnoreReturnValue
    public Builder setTrackNumber(@Nullable Integer trackNumber) {
      this.trackNumber = trackNumber;
      return this;
    }

    /** 设置总曲目数。 */
    @CanIgnoreReturnValue
    public Builder setTotalTrackCount(@Nullable Integer totalTrackCount) {
      this.totalTrackCount = totalTrackCount;
      return this;
    }

    /**
     * 设置 {@link FolderType}。
     *
     * @deprecated 使用 {@link #setIsBrowsable} 来指示一个项是否是可浏览的文件夹，并使用
     *     {@link #setMediaType} 来指示文件夹的类型。
     */
    @SuppressWarnings("deprecation") // 使用已弃用的类型。
    @Deprecated
    @CanIgnoreReturnValue
    public Builder setFolderType(@Nullable @FolderType Integer folderType) {
      this.folderType = folderType;
      return this;
    }

    /** 设置媒体项是否是可浏览的文件夹。 */
    @CanIgnoreReturnValue
    public Builder setIsBrowsable(@Nullable Boolean isBrowsable) {
      this.isBrowsable = isBrowsable;
      return this;
    }

    /** 设置媒体项是否可播放。 */
    @CanIgnoreReturnValue
    public Builder setIsPlayable(@Nullable Boolean isPlayable) {
      this.isPlayable = isPlayable;
      return this;
    }

    /**
     * @deprecated 使用 {@link #setRecordingYear(Integer)} 替代。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setYear(@Nullable Integer year) {
      return setRecordingYear(year);
    }

    /** 设置录制日期的年份。 */
    @CanIgnoreReturnValue
    public Builder setRecordingYear(@Nullable Integer recordingYear) {
      this.recordingYear = recordingYear;
      return this;
    }

    /**
     * 设置录制日期的月份。
     *
     * <p>值应在 1 到 12 之间。
     */
    @CanIgnoreReturnValue
    public Builder setRecordingMonth(
        @Nullable @IntRange(from = 1, to = 12) Integer recordingMonth) {
      this.recordingMonth = recordingMonth;
      return this;
    }

    /**
     * 设置录制日期的日。
     *
     * <p>值应在 1 到 31 之间。
     */
    @CanIgnoreReturnValue
    public Builder setRecordingDay(@Nullable @IntRange(from = 1, to = 31) Integer recordingDay) {
      this.recordingDay = recordingDay;
      return this;
    }

    /** 设置发行日期的年份。 */
    @CanIgnoreReturnValue
    public Builder setReleaseYear(@Nullable Integer releaseYear) {
      this.releaseYear = releaseYear;
      return this;
    }

    /**
     * 设置发行日期的月份。
     *
     * <p>值应在 1 到 12 之间。
     */
    @CanIgnoreReturnValue
    public Builder setReleaseMonth(@Nullable @IntRange(from = 1, to = 12) Integer releaseMonth) {
      this.releaseMonth = releaseMonth;
      return this;
    }

    /**
     * 设置发行日期的日。
     *
     * <p>值应在 1 到 31 之间。
     */
    @CanIgnoreReturnValue
    public Builder setReleaseDay(@Nullable @IntRange(from = 1, to = 31) Integer releaseDay) {
      this.releaseDay = releaseDay;
      return this;
    }

    /** 设置作词者。 */
    @CanIgnoreReturnValue
    public Builder setWriter(@Nullable CharSequence writer) {
      this.writer = writer;
      return this;
    }

    /** 设置作曲者。 */
    @CanIgnoreReturnValue
    public Builder setComposer(@Nullable CharSequence composer) {
      this.composer = composer;
      return this;
    }

    /** 设置指挥者。 */
    @CanIgnoreReturnValue
    public Builder setConductor(@Nullable CharSequence conductor) {
      this.conductor = conductor;
      return this;
    }

    /** 设置光盘编号。 */
    @CanIgnoreReturnValue
    public Builder setDiscNumber(@Nullable Integer discNumber) {
      this.discNumber = discNumber;
      return this;
    }

    /** 设置总光盘数。 */
    @CanIgnoreReturnValue
    public Builder setTotalDiscCount(@Nullable Integer totalDiscCount) {
      this.totalDiscCount = totalDiscCount;
      return this;
    }

    /** 设置流派。 */
    @CanIgnoreReturnValue
    public Builder setGenre(@Nullable CharSequence genre) {
      this.genre = genre;
      return this;
    }

    /** 设置合辑。 */
    @CanIgnoreReturnValue
    public Builder setCompilation(@Nullable CharSequence compilation) {
      this.compilation = compilation;
      return this;
    }

    /** 设置流媒体的电台名称。 */
    @CanIgnoreReturnValue
    public Builder setStation(@Nullable CharSequence station) {
      this.station = station;
      return this;
    }

    /** 设置 {@link MediaType}。 */
    @CanIgnoreReturnValue
    public Builder setMediaType(@Nullable @MediaType Integer mediaType) {
      this.mediaType = mediaType;
      return this;
    }

    /** 设置额外的 {@link Bundle} 数据。 */
    @CanIgnoreReturnValue
    public Builder setExtras(@Nullable Bundle extras) {
      this.extras = extras;
      return this;
    }

    /**
     * 设置支持的命令的 ID（例如 Media3 会话模块中的 {@code CommandButton.sessionCommand.customAction}）。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setSupportedCommands(List<String> supportedCommands) {
      this.supportedCommands = ImmutableList.copyOf(supportedCommands);
      return this;
    }

    /**
     * 设置 {@link Metadata} 中所有 {@link Metadata.Entry} 条目支持的字段。
     *
     * <p>只有 {@link Metadata.Entry} 实现了 {@link Metadata.Entry#populateMediaMetadata(Builder)} 方法时，
     * 才会设置相应的字段。
     *
     * <p>如果 {@link Metadata} 中多个 {@link Metadata.Entry} 对象与同一个 {@link MediaMetadata} 字段相关，
     * 则最后一个条目的值将被使用。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder populateFromMetadata(Metadata metadata) {
      for (int i = 0; i < metadata.length(); i++) {
        Metadata.Entry entry = metadata.get(i);
        entry.populateMediaMetadata(this);
      }
      return this;
    }
    /**
     * 设置 {@link Metadata} 列表中所有 {@link Metadata.Entry} 条目支持的字段。
     *
     * <p>只有 {@link Metadata.Entry} 实现了 {@link Metadata.Entry#populateMediaMetadata(Builder)} 方法时，
     * 才会设置相应的字段。
     *
     * <p>如果多个 {@link Metadata.Entry} 对象与同一个 {@link MediaMetadata} 字段相关，则最后一个条目的值将被使用。
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder populateFromMetadata(List<Metadata> metadataList) {
      for (int i = 0; i < metadataList.size(); i++) {
        Metadata metadata = metadataList.get(i);
        for (int j = 0; j < metadata.length(); j++) {
          Metadata.Entry entry = metadata.get(j);
          entry.populateMediaMetadata(this);
        }
      }
      return this;
    }

    /**
     * 从 {@code mediaMetadata} 中填充所有字段。
     *
     * <p>当字段非空时会被填充，但有一个例外：如果 {@code artworkUri} 和 {@code artworkData} 中至少有一个非空，
     * 则这两个字段都会被填充。
     */
    @SuppressWarnings("deprecation") // Populating deprecated fields.
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder populate(@Nullable MediaMetadata mediaMetadata) {
      if (mediaMetadata == null) {
        return this;
      }
      if (mediaMetadata.title != null) {
        setTitle(mediaMetadata.title);
      }
      if (mediaMetadata.artist != null) {
        setArtist(mediaMetadata.artist);
      }
      if (mediaMetadata.albumTitle != null) {
        setAlbumTitle(mediaMetadata.albumTitle);
      }
      if (mediaMetadata.albumArtist != null) {
        setAlbumArtist(mediaMetadata.albumArtist);
      }
      if (mediaMetadata.displayTitle != null) {
        setDisplayTitle(mediaMetadata.displayTitle);
      }
      if (mediaMetadata.subtitle != null) {
        setSubtitle(mediaMetadata.subtitle);
      }
      if (mediaMetadata.description != null) {
        setDescription(mediaMetadata.description);
      }
      if (mediaMetadata.durationMs != null) {
        setDurationMs(mediaMetadata.durationMs);
      }
      if (mediaMetadata.userRating != null) {
        setUserRating(mediaMetadata.userRating);
      }
      if (mediaMetadata.overallRating != null) {
        setOverallRating(mediaMetadata.overallRating);
      }
      if (mediaMetadata.artworkUri != null || mediaMetadata.artworkData != null) {
        setArtworkUri(mediaMetadata.artworkUri);
        setArtworkData(mediaMetadata.artworkData, mediaMetadata.artworkDataType);
      }
      if (mediaMetadata.trackNumber != null) {
        setTrackNumber(mediaMetadata.trackNumber);
      }
      if (mediaMetadata.totalTrackCount != null) {
        setTotalTrackCount(mediaMetadata.totalTrackCount);
      }
      if (mediaMetadata.folderType != null) {
        setFolderType(mediaMetadata.folderType);
      }
      if (mediaMetadata.isBrowsable != null) {
        setIsBrowsable(mediaMetadata.isBrowsable);
      }
      if (mediaMetadata.isPlayable != null) {
        setIsPlayable(mediaMetadata.isPlayable);
      }
      if (mediaMetadata.year != null) {
        setRecordingYear(mediaMetadata.year);
      }
      if (mediaMetadata.recordingYear != null) {
        setRecordingYear(mediaMetadata.recordingYear);
      }
      if (mediaMetadata.recordingMonth != null) {
        setRecordingMonth(mediaMetadata.recordingMonth);
      }
      if (mediaMetadata.recordingDay != null) {
        setRecordingDay(mediaMetadata.recordingDay);
      }
      if (mediaMetadata.releaseYear != null) {
        setReleaseYear(mediaMetadata.releaseYear);
      }
      if (mediaMetadata.releaseMonth != null) {
        setReleaseMonth(mediaMetadata.releaseMonth);
      }
      if (mediaMetadata.releaseDay != null) {
        setReleaseDay(mediaMetadata.releaseDay);
      }
      if (mediaMetadata.writer != null) {
        setWriter(mediaMetadata.writer);
      }
      if (mediaMetadata.composer != null) {
        setComposer(mediaMetadata.composer);
      }
      if (mediaMetadata.conductor != null) {
        setConductor(mediaMetadata.conductor);
      }
      if (mediaMetadata.discNumber != null) {
        setDiscNumber(mediaMetadata.discNumber);
      }
      if (mediaMetadata.totalDiscCount != null) {
        setTotalDiscCount(mediaMetadata.totalDiscCount);
      }
      if (mediaMetadata.genre != null) {
        setGenre(mediaMetadata.genre);
      }
      if (mediaMetadata.compilation != null) {
        setCompilation(mediaMetadata.compilation);
      }
      if (mediaMetadata.station != null) {
        setStation(mediaMetadata.station);
      }
      if (mediaMetadata.mediaType != null) {
        setMediaType(mediaMetadata.mediaType);
      }
      if (mediaMetadata.extras != null) {
        setExtras(mediaMetadata.extras);
      }

      if (!mediaMetadata.supportedCommands.isEmpty()) {
        setSupportedCommands(mediaMetadata.supportedCommands);
      }

      return this;
    }

    /** Returns a new {@link MediaMetadata} instance with the current builder values. */
    public MediaMetadata build() {
      return new MediaMetadata(/* builder= */ this);
    }
  }

  /**
   * The type of content described by the media item.
   *
   * <p>One of {@link #MEDIA_TYPE_MIXED}, {@link #MEDIA_TYPE_MUSIC}, {@link
   * #MEDIA_TYPE_AUDIO_BOOK_CHAPTER}, {@link #MEDIA_TYPE_PODCAST_EPISODE}, {@link
   * #MEDIA_TYPE_RADIO_STATION}, {@link #MEDIA_TYPE_NEWS}, {@link #MEDIA_TYPE_VIDEO}, {@link
   * #MEDIA_TYPE_TRAILER}, {@link #MEDIA_TYPE_MOVIE}, {@link #MEDIA_TYPE_TV_SHOW}, {@link
   * #MEDIA_TYPE_ALBUM}, {@link #MEDIA_TYPE_ARTIST}, {@link #MEDIA_TYPE_GENRE}, {@link
   * #MEDIA_TYPE_PLAYLIST}, {@link #MEDIA_TYPE_YEAR}, {@link #MEDIA_TYPE_AUDIO_BOOK}, {@link
   * #MEDIA_TYPE_PODCAST}, {@link #MEDIA_TYPE_TV_CHANNEL}, {@link #MEDIA_TYPE_TV_SERIES}, {@link
   * #MEDIA_TYPE_TV_SEASON}, {@link #MEDIA_TYPE_FOLDER_MIXED}, {@link #MEDIA_TYPE_FOLDER_ALBUMS},
   * {@link #MEDIA_TYPE_FOLDER_ARTISTS}, {@link #MEDIA_TYPE_FOLDER_GENRES}, {@link
   * #MEDIA_TYPE_FOLDER_PLAYLISTS}, {@link #MEDIA_TYPE_FOLDER_YEARS}, {@link
   * #MEDIA_TYPE_FOLDER_AUDIO_BOOKS}, {@link #MEDIA_TYPE_FOLDER_PODCASTS}, {@link
   * #MEDIA_TYPE_FOLDER_TV_CHANNELS}, {@link #MEDIA_TYPE_FOLDER_TV_SERIES}, {@link
   * #MEDIA_TYPE_FOLDER_TV_SHOWS}, {@link #MEDIA_TYPE_FOLDER_RADIO_STATIONS}, {@link
   * #MEDIA_TYPE_FOLDER_NEWS}, {@link #MEDIA_TYPE_FOLDER_VIDEOS}, {@link
   * #MEDIA_TYPE_FOLDER_TRAILERS} or {@link #MEDIA_TYPE_FOLDER_MOVIES}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
      MEDIA_TYPE_MIXED, // 未确定类型或多种媒体类型的混合
      MEDIA_TYPE_MUSIC, // 音乐
      MEDIA_TYPE_AUDIO_BOOK_CHAPTER, // 有声书章节
      MEDIA_TYPE_PODCAST_EPISODE, // 播客单集
      MEDIA_TYPE_RADIO_STATION, // 电台
      MEDIA_TYPE_NEWS, // 新闻
      MEDIA_TYPE_VIDEO, // 视频
      MEDIA_TYPE_TRAILER, // 电影预告片
      MEDIA_TYPE_MOVIE, // 电影
      MEDIA_TYPE_TV_SHOW, // 电视节目
      MEDIA_TYPE_ALBUM, // 专辑
      MEDIA_TYPE_ARTIST, // 艺术家
      MEDIA_TYPE_GENRE, // 流派
      MEDIA_TYPE_PLAYLIST, // 播放列表
      MEDIA_TYPE_YEAR, // 年份
      MEDIA_TYPE_AUDIO_BOOK, // 有声书
      MEDIA_TYPE_PODCAST, // 播客
      MEDIA_TYPE_TV_CHANNEL, // 电视频道
      MEDIA_TYPE_TV_SERIES, // 电视系列
      MEDIA_TYPE_TV_SEASON, // 电视季
      MEDIA_TYPE_FOLDER_MIXED, // 包含混合或未确定内容的文件夹
      MEDIA_TYPE_FOLDER_ALBUMS, // 包含专辑的文件夹
      MEDIA_TYPE_FOLDER_ARTISTS, // 包含艺术家的文件夹
      MEDIA_TYPE_FOLDER_GENRES, // 包含流派的文件夹
      MEDIA_TYPE_FOLDER_PLAYLISTS, // 包含播放列表的文件夹
      MEDIA_TYPE_FOLDER_YEARS, // 包含年份的文件夹
      MEDIA_TYPE_FOLDER_AUDIO_BOOKS, // 包含有声书的文件夹
      MEDIA_TYPE_FOLDER_PODCASTS, // 包含播客的文件夹
      MEDIA_TYPE_FOLDER_TV_CHANNELS, // 包含电视频道的文件夹
      MEDIA_TYPE_FOLDER_TV_SERIES, // 包含电视系列的文件夹
      MEDIA_TYPE_FOLDER_TV_SHOWS, // 包含电视节目的文件夹
      MEDIA_TYPE_FOLDER_RADIO_STATIONS, // 包含电台的文件夹
      MEDIA_TYPE_FOLDER_NEWS, // 包含新闻的文件夹
      MEDIA_TYPE_FOLDER_VIDEOS, // 包含视频的文件夹
      MEDIA_TYPE_FOLDER_TRAILERS, // 包含电影预告片的文件夹
      MEDIA_TYPE_FOLDER_MOVIES, // 包含电影的文件夹
  })
  public @interface MediaType {}

  /** 未确定类型的媒体或多种 {@linkplain MediaType 媒体类型} 的混合。 */
  public static final int MEDIA_TYPE_MIXED = 0;

  /** 音乐的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_MUSIC = 1;

  /** 有声书章节的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_AUDIO_BOOK_CHAPTER = 2;

  /** 播客单集的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_PODCAST_EPISODE = 3;

  /** 电台的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_RADIO_STATION = 4;

  /** 新闻的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_NEWS = 5;

  /** 视频的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_VIDEO = 6;

  /** 电影预告片的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_TRAILER = 7;

  /** 电影的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_MOVIE = 8;

  /** 电视节目的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_TV_SHOW = 9;

  /**
   * 属于专辑的一组项目（例如 {@link #MEDIA_TYPE_MUSIC 音乐}）的 {@link MediaType}。
   */
  public static final int MEDIA_TYPE_ALBUM = 10;

  /**
   * 来自同一艺术家的一组项目（例如 {@link #MEDIA_TYPE_MUSIC 音乐}）的 {@link MediaType}。
   */
  public static final int MEDIA_TYPE_ARTIST = 11;

  /**
   * 属于同一流派的一组项目（例如 {@link #MEDIA_TYPE_MUSIC 音乐}）的 {@link MediaType}。
   */
  public static final int MEDIA_TYPE_GENRE = 12;

  /**
   * 形成播放列表的一组项目（例如 {@link #MEDIA_TYPE_MUSIC 音乐}）的 {@link MediaType}。
   */
  public static final int MEDIA_TYPE_PLAYLIST = 13;

  /**
   * 来自同一年的一组项目（例如 {@link #MEDIA_TYPE_MUSIC 音乐}）的 {@link MediaType}。
   */
  public static final int MEDIA_TYPE_YEAR = 14;

  /**
   * 形成有声书的一组项目的 {@link MediaType}。此组中的项目通常是 {@link #MEDIA_TYPE_AUDIO_BOOK_CHAPTER} 类型。
   */
  public static final int MEDIA_TYPE_AUDIO_BOOK = 15;

  /**
   * 属于播客的一组项目的 {@link MediaType}。此组中的项目通常是 {@link #MEDIA_TYPE_PODCAST_EPISODE} 类型。
   */
  public static final int MEDIA_TYPE_PODCAST = 16;

  /**
   * 属于电视频道的一组项目的 {@link MediaType}。此组中的项目通常是 {@link #MEDIA_TYPE_TV_SHOW}、{@link #MEDIA_TYPE_TV_SERIES} 或 {@link #MEDIA_TYPE_MOVIE} 类型。
   */
  public static final int MEDIA_TYPE_TV_CHANNEL = 17;

  /**
   * 属于电视系列的一组项目的 {@link MediaType}。此组中的项目通常是 {@link #MEDIA_TYPE_TV_SHOW} 或 {@link #MEDIA_TYPE_TV_SEASON} 类型。
   */
  public static final int MEDIA_TYPE_TV_SERIES = 18;

  /**
   * 属于电视季的一组项目的 {@link MediaType}。此组中的项目通常是 {@link #MEDIA_TYPE_TV_SHOW} 类型。
   */
  public static final int MEDIA_TYPE_TV_SEASON = 19;

  /** 包含混合或未确定内容的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_MIXED = 20;

  /** 包含 {@linkplain #MEDIA_TYPE_ALBUM 专辑} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_ALBUMS = 21;

  /** 包含 {@linkplain #FIELD_ARTIST 艺术家} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_ARTISTS = 22;

  /** 包含 {@linkplain #MEDIA_TYPE_GENRE 流派} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_GENRES = 23;

  /** 包含 {@linkplain #MEDIA_TYPE_PLAYLIST 播放列表} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_PLAYLISTS = 24;

  /** 包含 {@linkplain #MEDIA_TYPE_YEAR 年份} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_YEARS = 25;

  /** 包含 {@linkplain #MEDIA_TYPE_AUDIO_BOOK 有声书} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_AUDIO_BOOKS = 26;

  /** 包含 {@linkplain #MEDIA_TYPE_PODCAST 播客} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_PODCASTS = 27;

  /** 包含 {@linkplain #MEDIA_TYPE_TV_CHANNEL 电视频道} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_TV_CHANNELS = 28;

  /** 包含 {@linkplain #MEDIA_TYPE_TV_SERIES 电视系列} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_TV_SERIES = 29;

  /** 包含 {@linkplain #MEDIA_TYPE_TV_SHOW 电视节目} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_TV_SHOWS = 30;

  /**
   * 包含 {@linkplain #MEDIA_TYPE_RADIO_STATION 电台} 的文件夹的 {@link MediaType}。
   */
  public static final int MEDIA_TYPE_FOLDER_RADIO_STATIONS = 31;

  /** 包含 {@linkplain #MEDIA_TYPE_NEWS 新闻} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_NEWS = 32;

  /** 包含 {@linkplain #MEDIA_TYPE_VIDEO 视频} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_VIDEOS = 33;

  /** 包含 {@linkplain #MEDIA_TYPE_TRAILER 电影预告片} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_TRAILERS = 34;

  /** 包含 {@linkplain #MEDIA_TYPE_MOVIE 电影} 的文件夹的 {@link MediaType}。 */
  public static final int MEDIA_TYPE_FOLDER_MOVIES = 35;

  /**
   * 媒体项的文件夹类型。
   *
   * <p>这可以用作可浏览蓝牙文件夹的类型（参见 <a
   * href="https://www.bluetooth.com/specifications/specs/a-v-remote-control-profile-1-6-2/">Bluetooth
   * AVRCP 1.6.2</a> 的第 6.10.2.2 节）。
   *
   * <p>可以是以下之一：{@link #FOLDER_TYPE_NONE}, {@link #FOLDER_TYPE_MIXED}, {@link #FOLDER_TYPE_TITLES},
   * {@link #FOLDER_TYPE_ALBUMS}, {@link #FOLDER_TYPE_ARTISTS}, {@link #FOLDER_TYPE_GENRES}, {@link
   * #FOLDER_TYPE_PLAYLISTS} 或 {@link #FOLDER_TYPE_YEARS}。
   *
   * @deprecated 使用 {@link #isBrowsable} 来指示一个项是否是可浏览的文件夹，并使用
   *     {@link #mediaType} 来指示文件夹的类型。
   */
// @Target 列表包括 'default' 目标和 TYPE_USE，以确保与 Kotlin 使用时的向后兼容性
// 在添加 TYPE_USE 之前。
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @Deprecated
  @SuppressWarnings("deprecation") // Defining deprecated constants.
  @IntDef({
    FOLDER_TYPE_NONE,
    FOLDER_TYPE_MIXED,
    FOLDER_TYPE_TITLES,
    FOLDER_TYPE_ALBUMS,
    FOLDER_TYPE_ARTISTS,
    FOLDER_TYPE_GENRES,
    FOLDER_TYPE_PLAYLISTS,
    FOLDER_TYPE_YEARS
  })
  public @interface FolderType {}

  /**
   * 表示不是文件夹的项的类型。
   *
   * @deprecated 改用 {@link #isBrowsable} 设置为 false。
   */
  @Deprecated public static final int FOLDER_TYPE_NONE = -1;

  /**
   * 表示包含混合类型媒体的文件夹的类型。
   *
   * @deprecated 改用 {@link #isBrowsable} 设置为 true 且 {@link #mediaType} 设置为 {@link
   *     #MEDIA_TYPE_FOLDER_MIXED}。
   */
  @Deprecated public static final int FOLDER_TYPE_MIXED = 0;

  /**
   * 表示仅包含可播放媒体的文件夹的类型。
   *
   * @deprecated 改用 {@link #isBrowsable} 设置为 true。
   */
  @Deprecated public static final int FOLDER_TYPE_TITLES = 1;

  /**
   * 表示按专辑分类的媒体文件夹的类型。
   *
   * @deprecated 改用 {@link #isBrowsable} 设置为 true 且 {@link #mediaType} 设置为 {@link
   *     #MEDIA_TYPE_FOLDER_ALBUMS}。
   */
  @Deprecated public static final int FOLDER_TYPE_ALBUMS = 2;

  /**
   * 表示按艺术家分类的媒体文件夹的类型。
   *
   * @deprecated 改用 {@link #isBrowsable} 设置为 true 且 {@link #mediaType} 设置为 {@link
   *     #MEDIA_TYPE_FOLDER_ARTISTS}。
   */
  @Deprecated public static final int FOLDER_TYPE_ARTISTS = 3;

  /**
   * 表示按流派分类的媒体文件夹的类型。
   *
   * @deprecated 改用 {@link #isBrowsable} 设置为 true 且 {@link #mediaType} 设置为 {@link
   *     #MEDIA_TYPE_FOLDER_GENRES}。
   */
  @Deprecated public static final int FOLDER_TYPE_GENRES = 4;

  /**
   * 表示包含播放列表的文件夹的类型。
   *
   * @deprecated 改用 {@link #isBrowsable} 设置为 true 且 {@link #mediaType} 设置为 {@link
   *     #MEDIA_TYPE_FOLDER_PLAYLISTS}。
   */
  @Deprecated public static final int FOLDER_TYPE_PLAYLISTS = 5;

  /**
   * 表示按年份分类的媒体文件夹的类型。
   *
   * @deprecated 改用 {@link #isBrowsable} 设置为 true 且 {@link #mediaType} 设置为 {@link
   *     #MEDIA_TYPE_FOLDER_YEARS}。
   */
  @Deprecated public static final int FOLDER_TYPE_YEARS = 6;

  /**
   * 封面图片的类型。
   *
   * <p>值来源于 ID3 v2.4 规范（参见 https://id3.org/id3v2.4.0-frames 的第 4.14 节）。
   *
   * <p>可以是以下之一：{@link #PICTURE_TYPE_OTHER}, {@link #PICTURE_TYPE_FILE_ICON}, {@link
   * #PICTURE_TYPE_FILE_ICON_OTHER}, {@link #PICTURE_TYPE_FRONT_COVER}, {@link
   * #PICTURE_TYPE_BACK_COVER}, {@link #PICTURE_TYPE_LEAFLET_PAGE}, {@link #PICTURE_TYPE_MEDIA},
   * {@link #PICTURE_TYPE_LEAD_ARTIST_PERFORMER}, {@link #PICTURE_TYPE_ARTIST_PERFORMER}, {@link
   * #PICTURE_TYPE_CONDUCTOR}, {@link #PICTURE_TYPE_BAND_ORCHESTRA}, {@link #PICTURE_TYPE_COMPOSER},
   * {@link #PICTURE_TYPE_LYRICIST}, {@link #PICTURE_TYPE_RECORDING_LOCATION}, {@link
   * #PICTURE_TYPE_DURING_RECORDING}, {@link #PICTURE_TYPE_DURING_PERFORMANCE}, {@link
   * #PICTURE_TYPE_MOVIE_VIDEO_SCREEN_CAPTURE}, {@link #PICTURE_TYPE_A_BRIGHT_COLORED_FISH}, {@link
   * #PICTURE_TYPE_ILLUSTRATION}, {@link #PICTURE_TYPE_BAND_ARTIST_LOGO} 或 {@link
   * #PICTURE_TYPE_PUBLISHER_STUDIO_LOGO}。
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    PICTURE_TYPE_OTHER,
    PICTURE_TYPE_FILE_ICON,
    PICTURE_TYPE_FILE_ICON_OTHER,
    PICTURE_TYPE_FRONT_COVER,
    PICTURE_TYPE_BACK_COVER,
    PICTURE_TYPE_LEAFLET_PAGE,
    PICTURE_TYPE_MEDIA,
    PICTURE_TYPE_LEAD_ARTIST_PERFORMER,
    PICTURE_TYPE_ARTIST_PERFORMER,
    PICTURE_TYPE_CONDUCTOR,
    PICTURE_TYPE_BAND_ORCHESTRA,
    PICTURE_TYPE_COMPOSER,
    PICTURE_TYPE_LYRICIST,
    PICTURE_TYPE_RECORDING_LOCATION,
    PICTURE_TYPE_DURING_RECORDING,
    PICTURE_TYPE_DURING_PERFORMANCE,
    PICTURE_TYPE_MOVIE_VIDEO_SCREEN_CAPTURE,
    PICTURE_TYPE_A_BRIGHT_COLORED_FISH,
    PICTURE_TYPE_ILLUSTRATION,
    PICTURE_TYPE_BAND_ARTIST_LOGO,
    PICTURE_TYPE_PUBLISHER_STUDIO_LOGO
  })
  public @interface PictureType {}
  /** 其他类型的图片。 */
  public static final int PICTURE_TYPE_OTHER = 0x00;

  /** 文件图标类型的图片。 */
  public static final int PICTURE_TYPE_FILE_ICON = 0x01;

  /** 其他文件图标类型的图片。 */
  public static final int PICTURE_TYPE_FILE_ICON_OTHER = 0x02;

  /** 封面图片（如专辑封面）。 */
  public static final int PICTURE_TYPE_FRONT_COVER = 0x03;

  /** 封底图片（如专辑封底）。 */
  public static final int PICTURE_TYPE_BACK_COVER = 0x04;

  /** 小册子页面的图片。 */
  public static final int PICTURE_TYPE_LEAFLET_PAGE = 0x05;

  /** 媒体相关的图片（如 CD 或 DVD 的图片）。 */
  public static final int PICTURE_TYPE_MEDIA = 0x06;

  /** 主要艺术家或表演者的图片。 */
  public static final int PICTURE_TYPE_LEAD_ARTIST_PERFORMER = 0x07;

  /** 艺术家或表演者的图片。 */
  public static final int PICTURE_TYPE_ARTIST_PERFORMER = 0x08;

  /** 指挥者的图片。 */
  public static final int PICTURE_TYPE_CONDUCTOR = 0x09;

  /** 乐队或乐团的图片。 */
  public static final int PICTURE_TYPE_BAND_ORCHESTRA = 0x0A;

  /** 作曲者的图片。 */
  public static final int PICTURE_TYPE_COMPOSER = 0x0B;

  /** 作词者的图片。 */
  public static final int PICTURE_TYPE_LYRICIST = 0x0C;

  /** 录制地点的图片。 */
  public static final int PICTURE_TYPE_RECORDING_LOCATION = 0x0D;

  /** 录制过程中的图片。 */
  public static final int PICTURE_TYPE_DURING_RECORDING = 0x0E;

  /** 表演过程中的图片。 */
  public static final int PICTURE_TYPE_DURING_PERFORMANCE = 0x0F;

  /** 电影或视频的屏幕截图。 */
  public static final int PICTURE_TYPE_MOVIE_VIDEO_SCREEN_CAPTURE = 0x10;

  /** 一条明亮的彩色鱼的图片（通常是测试用途）。 */
  public static final int PICTURE_TYPE_A_BRIGHT_COLORED_FISH = 0x11;

  /** 插画图片。 */
  public static final int PICTURE_TYPE_ILLUSTRATION = 0x12;

  /** 乐队或艺术家的标志图片。 */
  public static final int PICTURE_TYPE_BAND_ARTIST_LOGO = 0x13;

  /** 出版商或工作室的标志图片。 */
  public static final int PICTURE_TYPE_PUBLISHER_STUDIO_LOGO = 0x14;

  /** Empty {@link MediaMetadata}. */
  public static final MediaMetadata EMPTY = new MediaMetadata.Builder().build();

  /** 可选的标题。 */
  @Nullable public final CharSequence title;

  /** 可选的艺术家。 */
  @Nullable public final CharSequence artist;

  /** 可选的专辑标题。 */
  @Nullable public final CharSequence albumTitle;

  /** 可选的专辑艺术家。 */
  @Nullable public final CharSequence albumArtist;

  /** 可选的显示标题。 */
  @Nullable public final CharSequence displayTitle;

  /**
   * 可选的副标题。
   *
   * <p>这是媒体的次要标题，与隐藏式字幕无关。
   */
  @Nullable public final CharSequence subtitle;

  /** 可选的描述。 */
  @Nullable public final CharSequence description;

  /**
   * 可选的持续时间，非负值，单位为毫秒。
   *
   * <p>该字段由应用程序在构建元数据对象时填充，仅用于信息目的。要获取当前播放媒体项的持续时间，
   * 请使用 {@link Player#getDuration()}。
   */
  @UnstableApi @Nullable public final Long durationMs;

  /** 可选的用户 {@link Rating}。 */
  @Nullable public final Rating userRating;

  /** 可选的总体 {@link Rating}。 */
  @Nullable public final Rating overallRating;

  /** 可选的封面图片数据，以压缩的字节数组形式存储。 */
  @Nullable public final byte[] artworkData;

  /** 可选的封面图片数据的 {@link PictureType}。 */
  @Nullable public final @PictureType Integer artworkDataType;

  /** 可选的封面图片 {@link Uri}。 */
  @Nullable public final Uri artworkUri;

  /** 可选的曲目编号。 */
  @Nullable public final Integer trackNumber;

  /** 可选的总曲目数。 */
  @Nullable public final Integer totalTrackCount;

  /**
   * 可选的 {@link FolderType}。
   *
   * @deprecated 使用 {@link #isBrowsable} 来指示一个项是否是可浏览的文件夹，并使用
   *     {@link #mediaType} 来指示文件夹的类型。
   */
  @SuppressWarnings("deprecation") // 定义已弃用类型的字段。
  @Deprecated
  @Nullable
  public final @FolderType Integer folderType;

  /** 可选的布尔值，指示媒体项是否是可浏览的文件夹。 */
  @Nullable public final Boolean isBrowsable;

  /** 可选的布尔值，指示媒体项是否可播放。 */
  @Nullable public final Boolean isPlayable;

  /**
   * @deprecated 使用 {@link #recordingYear} 替代。
   */
  @UnstableApi @Deprecated @Nullable public final Integer year;

  /** 可选的录制日期的年份。 */
  @Nullable public final Integer recordingYear;

  /**
   * 可选的录制日期的月份。
   *
   * <p>请注意，无法保证月份和日期的组合是有效的。
   */
  @Nullable public final Integer recordingMonth;

  /**
   * 可选的录制日期的日。
   *
   * <p>请注意，无法保证月份和日期的组合是有效的。
   */
  @Nullable public final Integer recordingDay;

  /** 可选的发行日期的年份。 */
  @Nullable public final Integer releaseYear;

  /**
   * 可选的发行日期的月份。
   *
   * <p>请注意，无法保证月份和日期的组合是有效的。
   */
  @Nullable public final Integer releaseMonth;

  /**
   * 可选的发行日期的日。
   *
   * <p>请注意，无法保证月份和日期的组合是有效的。
   */
  @Nullable public final Integer releaseDay;

  /** 可选的作词者。 */
  @Nullable public final CharSequence writer;

  /** 可选的作曲者。 */
  @Nullable public final CharSequence composer;

  /** 可选的指挥者。 */
  @Nullable public final CharSequence conductor;

  /** 可选的光盘编号。 */
  @Nullable public final Integer discNumber;

  /** 可选的总光盘数。 */
  @Nullable public final Integer totalDiscCount;

  /** 可选的流派。 */
  @Nullable public final CharSequence genre;

  /** 可选的合辑。 */
  @Nullable public final CharSequence compilation;

  /** 可选的流媒体的电台名称。 */
  @Nullable public final CharSequence station;

  /** 可选的 {@link MediaType}。 */
  @Nullable public final @MediaType Integer mediaType;

  /**
   * 可选的额外信息 {@link Bundle}。
   *
   * <p>由于检查两个 {@link Bundle} 实例是否相等的复杂性，这些额外信息的内容不会在
   * {@link #equals(Object)} 和 {@link #hashCode()} 实现中被考虑。
   */
  @Nullable public final Bundle extras;

  /**
   * 此媒体项支持的命令的 ID（例如 Media3 会话模块中的 {@code
   * CommandButton.sessionCommand.customAction}）。
   */
  @UnstableApi public final ImmutableList<String> supportedCommands;

  @SuppressWarnings("deprecation") // Assigning deprecated fields.
  private MediaMetadata(Builder builder) {
    // Handle compatibility for deprecated fields.
    @Nullable Boolean isBrowsable = builder.isBrowsable;
    @Nullable Integer folderType = builder.folderType;
    @Nullable Integer mediaType = builder.mediaType;
    if (isBrowsable != null) {
      if (!isBrowsable) {
        folderType = FOLDER_TYPE_NONE;
      } else if (folderType == null || folderType == FOLDER_TYPE_NONE) {
        folderType = mediaType != null ? getFolderTypeFromMediaType(mediaType) : FOLDER_TYPE_MIXED;
      }
    } else if (folderType != null) {
      isBrowsable = folderType != FOLDER_TYPE_NONE;
      if (isBrowsable && mediaType == null) {
        mediaType = getMediaTypeFromFolderType(folderType);
      }
    }
    this.title = builder.title;
    this.artist = builder.artist;
    this.albumTitle = builder.albumTitle;
    this.albumArtist = builder.albumArtist;
    this.displayTitle = builder.displayTitle;
    this.subtitle = builder.subtitle;
    this.description = builder.description;
    this.durationMs = builder.durationMs;
    this.userRating = builder.userRating;
    this.overallRating = builder.overallRating;
    this.artworkData = builder.artworkData;
    this.artworkDataType = builder.artworkDataType;
    this.artworkUri = builder.artworkUri;
    this.trackNumber = builder.trackNumber;
    this.totalTrackCount = builder.totalTrackCount;
    this.folderType = folderType;
    this.isBrowsable = isBrowsable;
    this.isPlayable = builder.isPlayable;
    this.year = builder.recordingYear;
    this.recordingYear = builder.recordingYear;
    this.recordingMonth = builder.recordingMonth;
    this.recordingDay = builder.recordingDay;
    this.releaseYear = builder.releaseYear;
    this.releaseMonth = builder.releaseMonth;
    this.releaseDay = builder.releaseDay;
    this.writer = builder.writer;
    this.composer = builder.composer;
    this.conductor = builder.conductor;
    this.discNumber = builder.discNumber;
    this.totalDiscCount = builder.totalDiscCount;
    this.genre = builder.genre;
    this.compilation = builder.compilation;
    this.station = builder.station;
    this.mediaType = mediaType;
    this.supportedCommands = builder.supportedCommands;
    this.extras = builder.extras;
  }

  /** 返回一个新的 {@link Builder} 实例，并使用当前 {@link MediaMetadata} 的字段进行初始化。 */
  public Builder buildUpon() {
    return new Builder(/* mediaMetadata= */ this);
  }

  /**
   * 注意：相等性检查不会考虑 {@link #extras}。
   */
  @SuppressWarnings("deprecation") // 比较已弃用的字段。
  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MediaMetadata that = (MediaMetadata) obj;
    return Util.areEqual(title, that.title)
        && Util.areEqual(artist, that.artist)
        && Util.areEqual(albumTitle, that.albumTitle)
        && Util.areEqual(albumArtist, that.albumArtist)
        && Util.areEqual(displayTitle, that.displayTitle)
        && Util.areEqual(subtitle, that.subtitle)
        && Util.areEqual(description, that.description)
        && Util.areEqual(durationMs, that.durationMs)
        && Util.areEqual(userRating, that.userRating)
        && Util.areEqual(overallRating, that.overallRating)
        && Arrays.equals(artworkData, that.artworkData)
        && Util.areEqual(artworkDataType, that.artworkDataType)
        && Util.areEqual(artworkUri, that.artworkUri)
        && Util.areEqual(trackNumber, that.trackNumber)
        && Util.areEqual(totalTrackCount, that.totalTrackCount)
        && Util.areEqual(folderType, that.folderType)
        && Util.areEqual(isBrowsable, that.isBrowsable)
        && Util.areEqual(isPlayable, that.isPlayable)
        && Util.areEqual(recordingYear, that.recordingYear)
        && Util.areEqual(recordingMonth, that.recordingMonth)
        && Util.areEqual(recordingDay, that.recordingDay)
        && Util.areEqual(releaseYear, that.releaseYear)
        && Util.areEqual(releaseMonth, that.releaseMonth)
        && Util.areEqual(releaseDay, that.releaseDay)
        && Util.areEqual(writer, that.writer)
        && Util.areEqual(composer, that.composer)
        && Util.areEqual(conductor, that.conductor)
        && Util.areEqual(discNumber, that.discNumber)
        && Util.areEqual(totalDiscCount, that.totalDiscCount)
        && Util.areEqual(genre, that.genre)
        && Util.areEqual(compilation, that.compilation)
        && Util.areEqual(station, that.station)
        && Util.areEqual(mediaType, that.mediaType)
        && Util.areEqual(supportedCommands, that.supportedCommands)
        && ((extras == null) == (that.extras == null));
  }

  @SuppressWarnings("deprecation") // Hashing deprecated fields.
  @Override
  public int hashCode() {
    return Objects.hashCode(
        title,
        artist,
        albumTitle,
        albumArtist,
        displayTitle,
        subtitle,
        description,
        durationMs,
        userRating,
        overallRating,
        Arrays.hashCode(artworkData),
        artworkDataType,
        artworkUri,
        trackNumber,
        totalTrackCount,
        folderType,
        isBrowsable,
        isPlayable,
        recordingYear,
        recordingMonth,
        recordingDay,
        releaseYear,
        releaseMonth,
        releaseDay,
        writer,
        composer,
        conductor,
        discNumber,
        totalDiscCount,
        genre,
        compilation,
        station,
        mediaType,
        extras == null,
        supportedCommands);
  }
  /** 标题字段的标识符。 */
  private static final String FIELD_TITLE = Util.intToStringMaxRadix(0);

  /** 艺术家字段的标识符。 */
  private static final String FIELD_ARTIST = Util.intToStringMaxRadix(1);

  /** 专辑标题字段的标识符。 */
  private static final String FIELD_ALBUM_TITLE = Util.intToStringMaxRadix(2);

  /** 专辑艺术家字段的标识符。 */
  private static final String FIELD_ALBUM_ARTIST = Util.intToStringMaxRadix(3);

  /** 显示标题字段的标识符。 */
  private static final String FIELD_DISPLAY_TITLE = Util.intToStringMaxRadix(4);

  /** 副标题字段的标识符。 */
  private static final String FIELD_SUBTITLE = Util.intToStringMaxRadix(5);

  /** 描述字段的标识符。 */
  private static final String FIELD_DESCRIPTION = Util.intToStringMaxRadix(6);

// 7 保留以保持与之前定义字段的向后兼容性。

  /** 用户评分字段的标识符。 */
  private static final String FIELD_USER_RATING = Util.intToStringMaxRadix(8);

  /** 总体评分字段的标识符。 */
  private static final String FIELD_OVERALL_RATING = Util.intToStringMaxRadix(9);

  /** 封面图片数据字段的标识符。 */
  private static final String FIELD_ARTWORK_DATA = Util.intToStringMaxRadix(10);

  /** 封面图片 URI 字段的标识符。 */
  private static final String FIELD_ARTWORK_URI = Util.intToStringMaxRadix(11);

  /** 曲目编号字段的标识符。 */
  private static final String FIELD_TRACK_NUMBER = Util.intToStringMaxRadix(12);

  /** 总曲目数字段的标识符。 */
  private static final String FIELD_TOTAL_TRACK_COUNT = Util.intToStringMaxRadix(13);

  /** 文件夹类型字段的标识符。 */
  private static final String FIELD_FOLDER_TYPE = Util.intToStringMaxRadix(14);

  /** 是否可播放字段的标识符。 */
  private static final String FIELD_IS_PLAYABLE = Util.intToStringMaxRadix(15);

  /** 录制日期年份字段的标识符。 */
  private static final String FIELD_RECORDING_YEAR = Util.intToStringMaxRadix(16);

  /** 录制日期月份字段的标识符。 */
  private static final String FIELD_RECORDING_MONTH = Util.intToStringMaxRadix(17);

  /** 录制日期日字段的标识符。 */
  private static final String FIELD_RECORDING_DAY = Util.intToStringMaxRadix(18);

  /** 发行日期年份字段的标识符。 */
  private static final String FIELD_RELEASE_YEAR = Util.intToStringMaxRadix(19);

  /** 发行日期月份字段的标识符。 */
  private static final String FIELD_RELEASE_MONTH = Util.intToStringMaxRadix(20);

  /** 发行日期日字段的标识符。 */
  private static final String FIELD_RELEASE_DAY = Util.intToStringMaxRadix(21);

  /** 作词者字段的标识符。 */
  private static final String FIELD_WRITER = Util.intToStringMaxRadix(22);

  /** 作曲者字段的标识符。 */
  private static final String FIELD_COMPOSER = Util.intToStringMaxRadix(23);

  /** 指挥者字段的标识符。 */
  private static final String FIELD_CONDUCTOR = Util.intToStringMaxRadix(24);

  /** 光盘编号字段的标识符。 */
  private static final String FIELD_DISC_NUMBER = Util.intToStringMaxRadix(25);

  /** 总光盘数字段的标识符。 */
  private static final String FIELD_TOTAL_DISC_COUNT = Util.intToStringMaxRadix(26);

  /** 流派字段的标识符。 */
  private static final String FIELD_GENRE = Util.intToStringMaxRadix(27);

  /** 合辑字段的标识符。 */
  private static final String FIELD_COMPILATION = Util.intToStringMaxRadix(28);

  /** 封面图片数据类型字段的标识符。 */
  private static final String FIELD_ARTWORK_DATA_TYPE = Util.intToStringMaxRadix(29);

  /** 电台名称字段的标识符。 */
  private static final String FIELD_STATION = Util.intToStringMaxRadix(30);

  /** 媒体类型字段的标识符。 */
  private static final String FIELD_MEDIA_TYPE = Util.intToStringMaxRadix(31);

  /** 是否可浏览字段的标识符。 */
  private static final String FIELD_IS_BROWSABLE = Util.intToStringMaxRadix(32);

  /** 持续时间字段的标识符（单位：毫秒）。 */
  private static final String FIELD_DURATION_MS = Util.intToStringMaxRadix(33);

  /** 支持的命令字段的标识符。 */
  private static final String FIELD_SUPPORTED_COMMANDS = Util.intToStringMaxRadix(34);

  /** 额外信息字段的标识符。 */
  private static final String FIELD_EXTRAS = Util.intToStringMaxRadix(1000);

  @SuppressWarnings("deprecation") // Bundling deprecated fields.
  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    if (title != null) {
      bundle.putCharSequence(FIELD_TITLE, title);
    }
    if (artist != null) {
      bundle.putCharSequence(FIELD_ARTIST, artist);
    }
    if (albumTitle != null) {
      bundle.putCharSequence(FIELD_ALBUM_TITLE, albumTitle);
    }
    if (albumArtist != null) {
      bundle.putCharSequence(FIELD_ALBUM_ARTIST, albumArtist);
    }
    if (displayTitle != null) {
      bundle.putCharSequence(FIELD_DISPLAY_TITLE, displayTitle);
    }
    if (subtitle != null) {
      bundle.putCharSequence(FIELD_SUBTITLE, subtitle);
    }
    if (description != null) {
      bundle.putCharSequence(FIELD_DESCRIPTION, description);
    }
    if (durationMs != null) {
      bundle.putLong(FIELD_DURATION_MS, durationMs);
    }
    if (artworkData != null) {
      bundle.putByteArray(FIELD_ARTWORK_DATA, artworkData);
    }
    if (artworkUri != null) {
      bundle.putParcelable(FIELD_ARTWORK_URI, artworkUri);
    }
    if (writer != null) {
      bundle.putCharSequence(FIELD_WRITER, writer);
    }
    if (composer != null) {
      bundle.putCharSequence(FIELD_COMPOSER, composer);
    }
    if (conductor != null) {
      bundle.putCharSequence(FIELD_CONDUCTOR, conductor);
    }
    if (genre != null) {
      bundle.putCharSequence(FIELD_GENRE, genre);
    }
    if (compilation != null) {
      bundle.putCharSequence(FIELD_COMPILATION, compilation);
    }
    if (station != null) {
      bundle.putCharSequence(FIELD_STATION, station);
    }
    if (userRating != null) {
      bundle.putBundle(FIELD_USER_RATING, userRating.toBundle());
    }
    if (overallRating != null) {
      bundle.putBundle(FIELD_OVERALL_RATING, overallRating.toBundle());
    }
    if (trackNumber != null) {
      bundle.putInt(FIELD_TRACK_NUMBER, trackNumber);
    }
    if (totalTrackCount != null) {
      bundle.putInt(FIELD_TOTAL_TRACK_COUNT, totalTrackCount);
    }
    if (folderType != null) {
      bundle.putInt(FIELD_FOLDER_TYPE, folderType);
    }
    if (isBrowsable != null) {
      bundle.putBoolean(FIELD_IS_BROWSABLE, isBrowsable);
    }
    if (isPlayable != null) {
      bundle.putBoolean(FIELD_IS_PLAYABLE, isPlayable);
    }
    if (recordingYear != null) {
      bundle.putInt(FIELD_RECORDING_YEAR, recordingYear);
    }
    if (recordingMonth != null) {
      bundle.putInt(FIELD_RECORDING_MONTH, recordingMonth);
    }
    if (recordingDay != null) {
      bundle.putInt(FIELD_RECORDING_DAY, recordingDay);
    }
    if (releaseYear != null) {
      bundle.putInt(FIELD_RELEASE_YEAR, releaseYear);
    }
    if (releaseMonth != null) {
      bundle.putInt(FIELD_RELEASE_MONTH, releaseMonth);
    }
    if (releaseDay != null) {
      bundle.putInt(FIELD_RELEASE_DAY, releaseDay);
    }
    if (discNumber != null) {
      bundle.putInt(FIELD_DISC_NUMBER, discNumber);
    }
    if (totalDiscCount != null) {
      bundle.putInt(FIELD_TOTAL_DISC_COUNT, totalDiscCount);
    }
    if (artworkDataType != null) {
      bundle.putInt(FIELD_ARTWORK_DATA_TYPE, artworkDataType);
    }
    if (mediaType != null) {
      bundle.putInt(FIELD_MEDIA_TYPE, mediaType);
    }
    if (!supportedCommands.isEmpty()) {
      bundle.putStringArrayList(FIELD_SUPPORTED_COMMANDS, new ArrayList<>(supportedCommands));
    }
    if (extras != null) {
      bundle.putBundle(FIELD_EXTRAS, extras);
    }
    return bundle;
  }

  /** Restores a {@code MediaMetadata} from a {@link Bundle}. */
  @UnstableApi
  @SuppressWarnings("deprecation") // Unbundling deprecated fields.
  public static MediaMetadata fromBundle(Bundle bundle) {
    Builder builder = new Builder();
    builder
        .setTitle(bundle.getCharSequence(FIELD_TITLE))
        .setArtist(bundle.getCharSequence(FIELD_ARTIST))
        .setAlbumTitle(bundle.getCharSequence(FIELD_ALBUM_TITLE))
        .setAlbumArtist(bundle.getCharSequence(FIELD_ALBUM_ARTIST))
        .setDisplayTitle(bundle.getCharSequence(FIELD_DISPLAY_TITLE))
        .setSubtitle(bundle.getCharSequence(FIELD_SUBTITLE))
        .setDescription(bundle.getCharSequence(FIELD_DESCRIPTION))
        .setArtworkData(
            bundle.getByteArray(FIELD_ARTWORK_DATA),
            bundle.containsKey(FIELD_ARTWORK_DATA_TYPE)
                ? bundle.getInt(FIELD_ARTWORK_DATA_TYPE)
                : null)
        .setArtworkUri(bundle.getParcelable(FIELD_ARTWORK_URI))
        .setWriter(bundle.getCharSequence(FIELD_WRITER))
        .setComposer(bundle.getCharSequence(FIELD_COMPOSER))
        .setConductor(bundle.getCharSequence(FIELD_CONDUCTOR))
        .setGenre(bundle.getCharSequence(FIELD_GENRE))
        .setCompilation(bundle.getCharSequence(FIELD_COMPILATION))
        .setStation(bundle.getCharSequence(FIELD_STATION))
        .setExtras(bundle.getBundle(FIELD_EXTRAS));

    if (bundle.containsKey(FIELD_USER_RATING)) {
      @Nullable Bundle fieldBundle = bundle.getBundle(FIELD_USER_RATING);
      if (fieldBundle != null) {
        builder.setUserRating(Rating.fromBundle(fieldBundle));
      }
    }
    if (bundle.containsKey(FIELD_OVERALL_RATING)) {
      @Nullable Bundle fieldBundle = bundle.getBundle(FIELD_OVERALL_RATING);
      if (fieldBundle != null) {
        builder.setOverallRating(Rating.fromBundle(fieldBundle));
      }
    }
    if (bundle.containsKey(FIELD_DURATION_MS)) {
      builder.setDurationMs(bundle.getLong(FIELD_DURATION_MS));
    }
    if (bundle.containsKey(FIELD_TRACK_NUMBER)) {
      builder.setTrackNumber(bundle.getInt(FIELD_TRACK_NUMBER));
    }
    if (bundle.containsKey(FIELD_TOTAL_TRACK_COUNT)) {
      builder.setTotalTrackCount(bundle.getInt(FIELD_TOTAL_TRACK_COUNT));
    }
    if (bundle.containsKey(FIELD_FOLDER_TYPE)) {
      builder.setFolderType(bundle.getInt(FIELD_FOLDER_TYPE));
    }
    if (bundle.containsKey(FIELD_IS_BROWSABLE)) {
      builder.setIsBrowsable(bundle.getBoolean(FIELD_IS_BROWSABLE));
    }
    if (bundle.containsKey(FIELD_IS_PLAYABLE)) {
      builder.setIsPlayable(bundle.getBoolean(FIELD_IS_PLAYABLE));
    }
    if (bundle.containsKey(FIELD_RECORDING_YEAR)) {
      builder.setRecordingYear(bundle.getInt(FIELD_RECORDING_YEAR));
    }
    if (bundle.containsKey(FIELD_RECORDING_MONTH)) {
      builder.setRecordingMonth(bundle.getInt(FIELD_RECORDING_MONTH));
    }
    if (bundle.containsKey(FIELD_RECORDING_DAY)) {
      builder.setRecordingDay(bundle.getInt(FIELD_RECORDING_DAY));
    }
    if (bundle.containsKey(FIELD_RELEASE_YEAR)) {
      builder.setReleaseYear(bundle.getInt(FIELD_RELEASE_YEAR));
    }
    if (bundle.containsKey(FIELD_RELEASE_MONTH)) {
      builder.setReleaseMonth(bundle.getInt(FIELD_RELEASE_MONTH));
    }
    if (bundle.containsKey(FIELD_RELEASE_DAY)) {
      builder.setReleaseDay(bundle.getInt(FIELD_RELEASE_DAY));
    }
    if (bundle.containsKey(FIELD_DISC_NUMBER)) {
      builder.setDiscNumber(bundle.getInt(FIELD_DISC_NUMBER));
    }
    if (bundle.containsKey(FIELD_TOTAL_DISC_COUNT)) {
      builder.setTotalDiscCount(bundle.getInt(FIELD_TOTAL_DISC_COUNT));
    }
    if (bundle.containsKey(FIELD_MEDIA_TYPE)) {
      builder.setMediaType(bundle.getInt(FIELD_MEDIA_TYPE));
    }
    @Nullable
    ArrayList<String> supportedCommands = bundle.getStringArrayList(FIELD_SUPPORTED_COMMANDS);
    if (supportedCommands != null) {
      builder.setSupportedCommands(supportedCommands);
    }

    return builder.build();
  }

  @SuppressWarnings("deprecation") // Converting deprecated field.
  private static @FolderType int getFolderTypeFromMediaType(@MediaType int mediaType) {
    switch (mediaType) {
      case MEDIA_TYPE_ALBUM:
      case MEDIA_TYPE_ARTIST:
      case MEDIA_TYPE_AUDIO_BOOK:
      case MEDIA_TYPE_AUDIO_BOOK_CHAPTER:
      case MEDIA_TYPE_FOLDER_MOVIES:
      case MEDIA_TYPE_FOLDER_NEWS:
      case MEDIA_TYPE_FOLDER_RADIO_STATIONS:
      case MEDIA_TYPE_FOLDER_TRAILERS:
      case MEDIA_TYPE_FOLDER_VIDEOS:
      case MEDIA_TYPE_GENRE:
      case MEDIA_TYPE_MOVIE:
      case MEDIA_TYPE_MUSIC:
      case MEDIA_TYPE_NEWS:
      case MEDIA_TYPE_PLAYLIST:
      case MEDIA_TYPE_PODCAST:
      case MEDIA_TYPE_PODCAST_EPISODE:
      case MEDIA_TYPE_RADIO_STATION:
      case MEDIA_TYPE_TRAILER:
      case MEDIA_TYPE_TV_CHANNEL:
      case MEDIA_TYPE_TV_SEASON:
      case MEDIA_TYPE_TV_SERIES:
      case MEDIA_TYPE_TV_SHOW:
      case MEDIA_TYPE_VIDEO:
      case MEDIA_TYPE_YEAR:
        return FOLDER_TYPE_TITLES;
      case MEDIA_TYPE_FOLDER_ALBUMS:
        return FOLDER_TYPE_ALBUMS;
      case MEDIA_TYPE_FOLDER_ARTISTS:
        return FOLDER_TYPE_ARTISTS;
      case MEDIA_TYPE_FOLDER_GENRES:
        return FOLDER_TYPE_GENRES;
      case MEDIA_TYPE_FOLDER_PLAYLISTS:
        return FOLDER_TYPE_PLAYLISTS;
      case MEDIA_TYPE_FOLDER_YEARS:
        return FOLDER_TYPE_YEARS;
      case MEDIA_TYPE_FOLDER_AUDIO_BOOKS:
      case MEDIA_TYPE_FOLDER_MIXED:
      case MEDIA_TYPE_FOLDER_TV_CHANNELS:
      case MEDIA_TYPE_FOLDER_TV_SERIES:
      case MEDIA_TYPE_FOLDER_TV_SHOWS:
      case MEDIA_TYPE_FOLDER_PODCASTS:
      case MEDIA_TYPE_MIXED:
      default:
        return FOLDER_TYPE_MIXED;
    }
  }

  @SuppressWarnings("deprecation") // Converting deprecated field.
  private static @MediaType int getMediaTypeFromFolderType(@FolderType int folderType) {
    switch (folderType) {
      case FOLDER_TYPE_ALBUMS:
        return MEDIA_TYPE_FOLDER_ALBUMS;
      case FOLDER_TYPE_ARTISTS:
        return MEDIA_TYPE_FOLDER_ARTISTS;
      case FOLDER_TYPE_GENRES:
        return MEDIA_TYPE_FOLDER_GENRES;
      case FOLDER_TYPE_PLAYLISTS:
        return MEDIA_TYPE_FOLDER_PLAYLISTS;
      case FOLDER_TYPE_TITLES:
        return MEDIA_TYPE_MIXED;
      case FOLDER_TYPE_YEARS:
        return MEDIA_TYPE_FOLDER_YEARS;
      case FOLDER_TYPE_MIXED:
      case FOLDER_TYPE_NONE:
      default:
        return MEDIA_TYPE_FOLDER_MIXED;
    }
  }
}
