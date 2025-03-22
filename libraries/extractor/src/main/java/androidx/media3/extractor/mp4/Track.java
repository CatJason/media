package androidx.media3.extractor.mp4;

import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 封装描述 MP4 轨道的信息。 */
@UnstableApi
public final class Track {

  /**
   * 应用于轨道中样本的变换类型。取值为 {@link #TRANSFORMATION_NONE} 或 {@link #TRANSFORMATION_CEA608_CDAT}。
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({TRANSFORMATION_NONE, TRANSFORMATION_CEA608_CDAT})
  public @interface Transformation {}

  /** 无操作的样本变换。 */
  public static final int TRANSFORMATION_NONE = 0;

  /** 用于 cdat 原子中字幕样本的变换。 */
  public static final int TRANSFORMATION_CEA608_CDAT = 1;

  /** 轨道标识符。 */
  public final int id;

  /**
   * 轨道类型，取值为 {@link C#TRACK_TYPE_AUDIO}、{@link C#TRACK_TYPE_VIDEO} 或 {@link C#TRACK_TYPE_TEXT}。
   */
  public final @C.TrackType int type;

  /** 轨道时间尺度，定义为每秒经过的时间单位数。 */
  public final long timescale;

  /** 电影时间尺度。 */
  public final long movieTimescale;

  /** 轨道的持续时间（微秒），如果未知则为 {@link C#TIME_UNSET}。 */
  public final long durationUs;

  /** 媒体的持续时间（微秒），如果未知则为 {@link C#TIME_UNSET}。 */
  public final long mediaDurationUs;

  /** 轨道的格式。 */
  public final Format format;

  /**
   * 取值为 {@code TRANSFORMATION_*} 之一。定义在输出每个样本之前应用的变换。
   */
  public final @Transformation int sampleTransformation;

  /** 编辑列表段在电影时间尺度中的持续时间。如果没有编辑列表，则为 null。 */
  @Nullable public final long[] editListDurations;

  /** 编辑列表段在轨道时间尺度中的媒体时间。如果没有编辑列表，则为 null。 */
  @Nullable public final long[] editListMediaTimes;

  /**
   * 每个样本中 NALUnitLength 字段的长度（字节）。对于不使用长度分隔 NAL 单元的轨道，该值为 0。
   */
  public final int nalUnitLengthFieldLength;

  @Nullable private final TrackEncryptionBox[] sampleDescriptionEncryptionBoxes;

  public Track(
      int id,
      @C.TrackType int type,
      long timescale,
      long movieTimescale,
      long durationUs,
      long mediaDurationUs,
      Format format,
      @Transformation int sampleTransformation,
      @Nullable TrackEncryptionBox[] sampleDescriptionEncryptionBoxes,
      int nalUnitLengthFieldLength,
      @Nullable long[] editListDurations,
      @Nullable long[] editListMediaTimes) {
    this.id = id;
    this.type = type;
    this.timescale = timescale;
    this.movieTimescale = movieTimescale;
    this.durationUs = durationUs;
    this.mediaDurationUs = mediaDurationUs;
    this.format = format;
    this.sampleTransformation = sampleTransformation;
    this.sampleDescriptionEncryptionBoxes = sampleDescriptionEncryptionBoxes;
    this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
    this.editListDurations = editListDurations;
    this.editListMediaTimes = editListMediaTimes;
  }

  /**
   * 返回给定样本描述索引对应的 {@link TrackEncryptionBox}。
   *
   * @param sampleDescriptionIndex 给定的样本描述索引
   * @return 给定样本描述索引对应的 {@link TrackEncryptionBox}。如果不存在，则返回 null。
   */
  @Nullable
  public TrackEncryptionBox getSampleDescriptionEncryptionBox(int sampleDescriptionIndex) {
    return sampleDescriptionEncryptionBoxes == null
        ? null
        : sampleDescriptionEncryptionBoxes[sampleDescriptionIndex];
  }

  /**
   * 返回一个使用新格式的轨道副本。
   *
   * @param format 新格式
   * @return 使用新格式的轨道副本
   */
  public Track copyWithFormat(Format format) {
    return new Track(
        id,
        type,
        timescale,
        movieTimescale,
        durationUs,
        mediaDurationUs,
        format,
        sampleTransformation,
        sampleDescriptionEncryptionBoxes,
        nalUnitLengthFieldLength,
        editListDurations,
        editListMediaTimes);
  }

  /**
   * 返回一个不带编辑列表的轨道副本。
   *
   * @return 不带编辑列表的轨道副本
   */
  public Track copyWithoutEditLists() {
    return new Track(
        id,
        type,
        timescale,
        movieTimescale,
        durationUs,
        mediaDurationUs,
        format,
        sampleTransformation,
        sampleDescriptionEncryptionBoxes,
        nalUnitLengthFieldLength,
        /* editListDurations= */ null,
        /* editListMediaTimes= */ null);
  }
}