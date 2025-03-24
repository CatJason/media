package androidx.media3.common;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Ascii;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.dataflow.qual.Pure;

/** Defines common MIME types and helper methods. */
public final class MimeTypes {
  @UnstableApi public static final String BASE_TYPE_VIDEO = "video"; // 视频基础类型
  @UnstableApi public static final String BASE_TYPE_AUDIO = "audio"; // 音频基础类型
  @UnstableApi public static final String BASE_TYPE_TEXT = "text"; // 文本基础类型
  @UnstableApi public static final String BASE_TYPE_IMAGE = "image"; // 图像基础类型
  @UnstableApi public static final String BASE_TYPE_APPLICATION = "application"; // 应用基础类型

  // video/ MIME types

  public static final String VIDEO_MP4 = BASE_TYPE_VIDEO + "/mp4"; // MP4 视频格式
  @UnstableApi public static final String VIDEO_MATROSKA = BASE_TYPE_VIDEO + "/x-matroska"; // Matroska 视频格式
  public static final String VIDEO_WEBM = BASE_TYPE_VIDEO + "/webm"; // WebM 视频格式
  public static final String VIDEO_H263 = BASE_TYPE_VIDEO + "/3gpp"; // H.263 视频格式
  public static final String VIDEO_H264 = BASE_TYPE_VIDEO + "/avc"; // H.264 视频格式
  public static final String VIDEO_H265 = BASE_TYPE_VIDEO + "/hevc"; // H.265 视频格式
  @UnstableApi public static final String VIDEO_VP8 = BASE_TYPE_VIDEO + "/x-vnd.on2.vp8"; // VP8 视频格式
  @UnstableApi public static final String VIDEO_VP9 = BASE_TYPE_VIDEO + "/x-vnd.on2.vp9"; // VP9 视频格式
  public static final String VIDEO_AV1 = BASE_TYPE_VIDEO + "/av01"; // AV1 视频格式
  public static final String VIDEO_MP2T = BASE_TYPE_VIDEO + "/mp2t"; // MPEG-2 传输流格式
  public static final String VIDEO_MP4V = BASE_TYPE_VIDEO + "/mp4v-es"; // MPEG-4 视频格式
  public static final String VIDEO_MPEG = BASE_TYPE_VIDEO + "/mpeg"; // MPEG 视频格式
  public static final String VIDEO_PS = BASE_TYPE_VIDEO + "/mp2p"; // MPEG-2 节目流格式
  public static final String VIDEO_MPEG2 = BASE_TYPE_VIDEO + "/mpeg2"; // MPEG-2 视频格式
  public static final String VIDEO_VC1 = BASE_TYPE_VIDEO + "/wvc1"; // VC-1 视频格式
  public static final String VIDEO_DIVX = BASE_TYPE_VIDEO + "/divx"; // DivX 视频格式
  @UnstableApi public static final String VIDEO_FLV = BASE_TYPE_VIDEO + "/x-flv"; // FLV 视频格式
  public static final String VIDEO_DOLBY_VISION = BASE_TYPE_VIDEO + "/dolby-vision"; // 杜比视界视频格式
  public static final String VIDEO_OGG = BASE_TYPE_VIDEO + "/ogg"; // Ogg 视频格式
  public static final String VIDEO_AVI = BASE_TYPE_VIDEO + "/x-msvideo"; // AVI 视频格式
  public static final String VIDEO_MJPEG = BASE_TYPE_VIDEO + "/mjpeg"; // MJPEG 视频格式
  public static final String VIDEO_MP42 = BASE_TYPE_VIDEO + "/mp42"; // MP42 视频格式
  public static final String VIDEO_MP43 = BASE_TYPE_VIDEO + "/mp43"; // MP43 视频格式
  @UnstableApi public static final String VIDEO_MV_HEVC = BASE_TYPE_VIDEO + "/mv-hevc"; // MV-HEVC 视频格式
  @UnstableApi public static final String VIDEO_RAW = BASE_TYPE_VIDEO + "/raw"; // 原始视频格式
  @UnstableApi public static final String VIDEO_UNKNOWN = BASE_TYPE_VIDEO + "/x-unknown"; // 未知视频格式

  // audio/ MIME types

  public static final String AUDIO_MP4 = BASE_TYPE_AUDIO + "/mp4"; // MP4 音频格式
  public static final String AUDIO_AAC = BASE_TYPE_AUDIO + "/mp4a-latm"; // AAC 音频格式
  @UnstableApi public static final String AUDIO_MATROSKA = BASE_TYPE_AUDIO + "/x-matroska"; // Matroska 音频格式
  public static final String AUDIO_WEBM = BASE_TYPE_AUDIO + "/webm"; // WebM 音频格式
  public static final String AUDIO_MPEG = BASE_TYPE_AUDIO + "/mpeg"; // MPEG 音频格式
  public static final String AUDIO_MPEG_L1 = BASE_TYPE_AUDIO + "/mpeg-L1"; // MPEG Layer 1 音频格式
  public static final String AUDIO_MPEG_L2 = BASE_TYPE_AUDIO + "/mpeg-L2"; // MPEG Layer 2 音频格式
  public static final String AUDIO_MPEGH_MHA1 = BASE_TYPE_AUDIO + "/mha1"; // MPEG-H MHA1 音频格式
  public static final String AUDIO_MPEGH_MHM1 = BASE_TYPE_AUDIO + "/mhm1"; // MPEG-H MHM1 音频格式
  public static final String AUDIO_RAW = BASE_TYPE_AUDIO + "/raw"; // 原始音频格式
  public static final String AUDIO_ALAW = BASE_TYPE_AUDIO + "/g711-alaw"; // A-law 音频格式
  public static final String AUDIO_MLAW = BASE_TYPE_AUDIO + "/g711-mlaw"; // μ-law 音频格式
  public static final String AUDIO_AC3 = BASE_TYPE_AUDIO + "/ac3"; // AC-3 音频格式
  public static final String AUDIO_E_AC3 = BASE_TYPE_AUDIO + "/eac3"; // E-AC-3 音频格式
  public static final String AUDIO_E_AC3_JOC = BASE_TYPE_AUDIO + "/eac3-joc"; // E-AC-3 JOC 音频格式
  public static final String AUDIO_AC4 = BASE_TYPE_AUDIO + "/ac4"; // AC-4 音频格式
  public static final String AUDIO_TRUEHD = BASE_TYPE_AUDIO + "/true-hd"; // TrueHD 音频格式
  public static final String AUDIO_DTS = BASE_TYPE_AUDIO + "/vnd.dts"; // DTS 音频格式
  public static final String AUDIO_DTS_HD = BASE_TYPE_AUDIO + "/vnd.dts.hd"; // DTS-HD 音频格式
  public static final String AUDIO_DTS_EXPRESS = BASE_TYPE_AUDIO + "/vnd.dts.hd;profile=lbr"; // DTS Express 音频格式
  @UnstableApi public static final String AUDIO_DTS_X = BASE_TYPE_AUDIO + "/vnd.dts.uhd;profile=p2"; // DTS:X 音频格式
  public static final String AUDIO_VORBIS = BASE_TYPE_AUDIO + "/vorbis"; // Vorbis 音频格式
  public static final String AUDIO_OPUS = BASE_TYPE_AUDIO + "/opus"; // Opus 音频格式
  public static final String AUDIO_AMR = BASE_TYPE_AUDIO + "/amr"; // AMR 音频格式
  public static final String AUDIO_AMR_NB = BASE_TYPE_AUDIO + "/3gpp"; // AMR 窄带音频格式
  public static final String AUDIO_AMR_WB = BASE_TYPE_AUDIO + "/amr-wb"; // AMR 宽带音频格式
  public static final String AUDIO_FLAC = BASE_TYPE_AUDIO + "/flac"; // FLAC 音频格式
  public static final String AUDIO_ALAC = BASE_TYPE_AUDIO + "/alac"; // ALAC 音频格式
  public static final String AUDIO_MSGSM = BASE_TYPE_AUDIO + "/gsm"; // GSM 音频格式
  public static final String AUDIO_OGG = BASE_TYPE_AUDIO + "/ogg"; // Ogg 音频格式
  public static final String AUDIO_WAV = BASE_TYPE_AUDIO + "/wav"; // WAV 音频格式
  public static final String AUDIO_MIDI = BASE_TYPE_AUDIO + "/midi"; // MIDI 音频格式
  @UnstableApi public static final String AUDIO_IAMF = BASE_TYPE_AUDIO + "/iamf"; // IAMF 音频格式

  @UnstableApi
  public static final String AUDIO_EXOPLAYER_MIDI = BASE_TYPE_AUDIO + "/x-exoplayer-midi"; // ExoPlayer MIDI 音频格式

  @UnstableApi public static final String AUDIO_UNKNOWN = BASE_TYPE_AUDIO + "/x-unknown"; // 未知音频格式

  // text/ MIME types

  public static final String TEXT_VTT = BASE_TYPE_TEXT + "/vtt"; // WebVTT 字幕格式
  public static final String TEXT_SSA = BASE_TYPE_TEXT + "/x-ssa"; // SSA 字幕格式
  @UnstableApi public static final String TEXT_UNKNOWN = BASE_TYPE_TEXT + "/x-unknown"; // 未知文本格式

  // application/ MIME types

  public static final String APPLICATION_MP4 = BASE_TYPE_APPLICATION + "/mp4"; // MP4 应用格式
  public static final String APPLICATION_WEBM = BASE_TYPE_APPLICATION + "/webm"; // WebM 应用格式

  public static final String APPLICATION_MATROSKA = BASE_TYPE_APPLICATION + "/x-matroska"; // Matroska 应用格式

  public static final String APPLICATION_MPD = BASE_TYPE_APPLICATION + "/dash+xml"; // DASH 媒体描述文件格式
  public static final String APPLICATION_M3U8 = BASE_TYPE_APPLICATION + "/x-mpegURL"; // M3U8 播放列表格式
  public static final String APPLICATION_SS = BASE_TYPE_APPLICATION + "/vnd.ms-sstr+xml"; // Smooth Streaming 格式
  public static final String APPLICATION_ID3 = BASE_TYPE_APPLICATION + "/id3"; // ID3 元数据格式
  public static final String APPLICATION_CEA608 = BASE_TYPE_APPLICATION + "/cea-608"; // CEA-608 字幕格式
  public static final String APPLICATION_CEA708 = BASE_TYPE_APPLICATION + "/cea-708"; // CEA-708 字幕格式
  public static final String APPLICATION_SUBRIP = BASE_TYPE_APPLICATION + "/x-subrip"; // SubRip 字幕格式
  public static final String APPLICATION_TTML = BASE_TYPE_APPLICATION + "/ttml+xml"; // TTML 字幕格式
  public static final String APPLICATION_TX3G = BASE_TYPE_APPLICATION + "/x-quicktime-tx3g"; // QuickTime TX3G 字幕格式
  public static final String APPLICATION_MP4VTT = BASE_TYPE_APPLICATION + "/x-mp4-vtt"; // MP4 WebVTT 字幕格式
  public static final String APPLICATION_MP4CEA608 = BASE_TYPE_APPLICATION + "/x-mp4-cea-608"; // MP4 CEA-608 字幕格式

  /**
   * @deprecated RawCC 是 Google 内部字幕格式，此版本的 Media3 不支持。没有替代值。
   */
  @Deprecated public static final String APPLICATION_RAWCC = BASE_TYPE_APPLICATION + "/x-rawcc"; // RawCC 字幕格式

  public static final String APPLICATION_VOBSUB = BASE_TYPE_APPLICATION + "/vobsub"; // VobSub 字幕格式
  public static final String APPLICATION_PGS = BASE_TYPE_APPLICATION + "/pgs"; // PGS 字幕格式
  @UnstableApi public static final String APPLICATION_SCTE35 = BASE_TYPE_APPLICATION + "/x-scte35"; // SCTE-35 格式
  public static final String APPLICATION_SDP = BASE_TYPE_APPLICATION + "/sdp"; // SDP 会话描述协议格式

  @UnstableApi
  public static final String APPLICATION_CAMERA_MOTION = BASE_TYPE_APPLICATION + "/x-camera-motion"; // 摄像机运动数据格式

  @UnstableApi
  public static final String APPLICATION_DEPTH_METADATA =
      BASE_TYPE_APPLICATION + "/x-depth-metadata"; // 深度元数据格式

  @UnstableApi public static final String APPLICATION_EMSG = BASE_TYPE_APPLICATION + "/x-emsg"; // EMSG 事件消息格式
  public static final String APPLICATION_DVBSUBS = BASE_TYPE_APPLICATION + "/dvbsubs"; // DVB 字幕格式
  @UnstableApi public static final String APPLICATION_EXIF = BASE_TYPE_APPLICATION + "/x-exif"; // EXIF 元数据格式
  @UnstableApi public static final String APPLICATION_ICY = BASE_TYPE_APPLICATION + "/x-icy"; // ICY 元数据格式
  public static final String APPLICATION_AIT = BASE_TYPE_APPLICATION + "/vnd.dvb.ait"; // AIT 应用信息表格式
  public static final String APPLICATION_RTSP = BASE_TYPE_APPLICATION + "/x-rtsp"; // RTSP 流媒体协议格式

  @UnstableApi
  public static final String APPLICATION_MEDIA3_CUES = BASE_TYPE_APPLICATION + "/x-media3-cues"; // Media3 提示信息格式

  /** 从外部图像管理框架加载的图像 URI 的 MIME 类型。 */
  @UnstableApi
  public static final String APPLICATION_EXTERNALLY_LOADED_IMAGE =
      BASE_TYPE_APPLICATION + "/x-image-uri"; // 外部加载图像格式

  // image/ MIME types

  public static final String IMAGE_JPEG = BASE_TYPE_IMAGE + "/jpeg"; // JPEG 图像格式
  @UnstableApi public static final String IMAGE_JPEG_R = BASE_TYPE_IMAGE + "/jpeg_r"; // JPEG-R 图像格式
  @UnstableApi public static final String IMAGE_PNG = BASE_TYPE_IMAGE + "/png"; // PNG 图像格式
  @UnstableApi public static final String IMAGE_HEIF = BASE_TYPE_IMAGE + "/heif"; // HEIF 图像格式
  @UnstableApi public static final String IMAGE_HEIC = BASE_TYPE_IMAGE + "/heic"; // HEIC 图像格式
  @UnstableApi public static final String IMAGE_AVIF = BASE_TYPE_IMAGE + "/avif"; // AVIF 图像格式
  @UnstableApi public static final String IMAGE_BMP = BASE_TYPE_IMAGE + "/bmp"; // BMP 图像格式
  @UnstableApi public static final String IMAGE_WEBP = BASE_TYPE_IMAGE + "/webp"; // WebP 图像格式
  @UnstableApi public static final String IMAGE_RAW = BASE_TYPE_IMAGE + "/raw"; // 原始图像格式

  /**
   * E-AC3-JOC 的非标准编解码器字符串。使用此常量可以仅从编解码器字符串中区分常规 E-AC3 ("ec-3") 和 E-AC3-JOC ("ec+3") 流。
   * 标准是使用 "ec-3" 表示两者，如 <a href="https://mp4ra.org/#/codecs">MP4RA 注册的编解码器类型</a> 所述。
   */
  @UnstableApi public static final String CODEC_E_AC3_JOC = "ec+3"; // E-AC3-JOC 编解码器格式

  private static final ArrayList<CustomMimeType> customMimeTypes = new ArrayList<>(); // 自定义 MIME 类型列表

  private static final Pattern MP4A_RFC_6381_CODEC_PATTERN =
      Pattern.compile("^mp4a\\.([a-zA-Z0-9]{2})(?:\\.([0-9]{1,2}))?$"); // MP4A 编解码器模式匹配
  /**
   * 注册自定义 MIME 类型。大多数应用程序不需要调用此方法，因为标准 MIME 类型的处理是内置的。
   * 这些内置的 MIME 类型优先于通过此方法注册的任何 MIME 类型。如果使用此方法，必须在创建任何播放器之前调用。
   *
   * @param mimeType 要注册的自定义 MIME 类型。
   * @param codecPrefix 与 MIME 类型关联的 RFC 6381 编解码器字符串前缀。
   * @param trackType 与 MIME 类型关联的 {@link C.TrackType 轨道类型}。
   *     如果 {@code mimeType} 的顶级类型是音频、视频或文本，则忽略此值。
   */
  @UnstableApi
  public static void registerCustomMimeType(
      String mimeType, String codecPrefix, @C.TrackType int trackType) {
    CustomMimeType customMimeType = new CustomMimeType(mimeType, codecPrefix, trackType); // 创建自定义 MIME 类型
    int customMimeTypeCount = customMimeTypes.size();
    for (int i = 0; i < customMimeTypeCount; i++) {
      if (mimeType.equals(customMimeTypes.get(i).mimeType)) {
        customMimeTypes.remove(i); // 如果已存在相同 MIME 类型，则移除旧条目
        break;
      }
    }
    customMimeTypes.add(customMimeType); // 添加新的自定义 MIME 类型
  }

  /** 返回给定字符串是否为音频 MIME 类型。 */
  @UnstableApi
  public static boolean isAudio(@Nullable String mimeType) {
    return BASE_TYPE_AUDIO.equals(getTopLevelType(mimeType)); // 检查顶级类型是否为音频
  }

  /** 返回给定字符串是否为视频 MIME 类型。 */
  @UnstableApi
  public static boolean isVideo(@Nullable String mimeType) {
    return BASE_TYPE_VIDEO.equals(getTopLevelType(mimeType)); // 检查顶级类型是否为视频
  }

  /**
   * 返回给定字符串是否为文本 MIME 类型，包括使用 "application" 作为基础类型的已知文本类型。
   */
  @SuppressWarnings("deprecation") // 支持已弃用的 MIME 类型
  @UnstableApi
  @Pure
  public static boolean isText(@Nullable String mimeType) {
    return BASE_TYPE_TEXT.equals(getTopLevelType(mimeType)) // 检查顶级类型是否为文本
        || APPLICATION_MEDIA3_CUES.equals(mimeType)
        || APPLICATION_CEA608.equals(mimeType)
        || APPLICATION_CEA708.equals(mimeType)
        || APPLICATION_MP4CEA608.equals(mimeType)
        || APPLICATION_SUBRIP.equals(mimeType)
        || APPLICATION_TTML.equals(mimeType)
        || APPLICATION_TX3G.equals(mimeType)
        || APPLICATION_MP4VTT.equals(mimeType)
        || APPLICATION_RAWCC.equals(mimeType)
        || APPLICATION_VOBSUB.equals(mimeType)
        || APPLICATION_PGS.equals(mimeType)
        || APPLICATION_DVBSUBS.equals(mimeType);
  }

  /** 返回给定字符串是否为图像 MIME 类型。 */
  @UnstableApi
  public static boolean isImage(@Nullable String mimeType) {
    return BASE_TYPE_IMAGE.equals(getTopLevelType(mimeType)) // 检查顶级类型是否为图像
        || APPLICATION_EXTERNALLY_LOADED_IMAGE.equals(mimeType);
  }

  /**
   * 如果已知给定 MIME 类型和编解码器的流中的所有样本都保证是同步样本（即每个样本都保证设置了 {@link C#BUFFER_FLAG_KEY_FRAME}），
   * 并且每个样本的固有持续时间可以忽略不计（即我们永远不需要因为播放部分落入其持续时间而请求样本），则返回 true。
   *
   * @param mimeType 流的 MIME 类型。
   * @param codec 流的 RFC 6381 编解码器字符串，如果未知则为 {@code null}。
   * @return 是否已知流中的所有样本都保证是同步样本。
   */
  @UnstableApi
  public static boolean allSamplesAreSyncSamples(
      @Nullable String mimeType, @Nullable String codec) {
    if (mimeType == null) {
      return false;
    }
    // TODO: 添加更多音频 MIME 类型。还可以考虑基于 Format 而不是仅基于 MIME 类型进行评估，
    // 因为在某些情况下，该属性仅对属于单个 MIME 类型的部分配置文件为 true。如果这样做，
    // 我们应该将此方法移动到另一个类中。参见 [内部参考: http://go/exo-audio-format-random-access]。
    switch (mimeType) {
      case AUDIO_MPEG:
      case AUDIO_MPEG_L1:
      case AUDIO_MPEG_L2:
      case AUDIO_RAW:
      case AUDIO_ALAW:
      case AUDIO_MLAW:
      case AUDIO_FLAC:
      case AUDIO_AC3:
      case AUDIO_E_AC3:
      case AUDIO_E_AC3_JOC:
        return true; // 这些音频格式的所有样本都是同步样本
      case AUDIO_AAC:
        if (codec == null) {
          return false;
        }
        @Nullable Mp4aObjectType objectType = getObjectTypeFromMp4aRFC6381CodecString(codec); // 从编解码器字符串中获取对象类型
        if (objectType == null) {
          return false;
        }
        @C.Encoding int encoding = objectType.getEncoding(); // 获取编码类型
        // xHE-AAC 是一个例外，其所有样本不保证是同步样本。
        // 如果编码为 ENCODING_INVALID，也表示我们无法从编解码器字符串中解析编码，返回 false。
        return encoding != C.ENCODING_INVALID && encoding != C.ENCODING_AAC_XHE;
      default:
        return false; // 其他情况返回 false
    }
  }

  /**
   * 从 RFC 6381 编解码器字符串中返回第一个视频 MIME 类型。
   *
   * @param codecs RFC 6381 编解码器字符串。
   * @return 第一个派生的视频 MIME 类型，如果未找到则返回 {@code null}。
   */
  @UnstableApi
  @Nullable
  public static String getVideoMediaMimeType(@Nullable String codecs) {
    if (codecs == null) {
      return null; // 如果编解码器字符串为 null，返回 null
    }
    String[] codecList = Util.splitCodecs(codecs); // 将编解码器字符串拆分为数组
    for (String codec : codecList) {
      @Nullable String mimeType = getMediaMimeType(codec); // 获取编解码器对应的 MIME 类型
      if (mimeType != null && isVideo(mimeType)) {
        return mimeType; // 如果 MIME 类型是视频类型，则返回
      }
    }
    return null; // 未找到视频 MIME 类型，返回 null
  }

  /**
   * 检查给定的 {@code codecs} 字符串是否包含与给定 {@code mimeType} 对应的编解码器。
   *
   * @param codecs RFC 6381 编解码器字符串。
   * @param mimeType 要查找的 MIME 类型。
   * @return 如果 {@code codecs} 字符串包含与 {@code mimeType} 对应的编解码器，则返回 true。
   */
  @UnstableApi
  public static boolean containsCodecsCorrespondingToMimeType(
      @Nullable String codecs, String mimeType) {
    return getCodecsCorrespondingToMimeType(codecs, mimeType) != null; // 检查是否存在对应的编解码器
  }

  /**
   * 返回 {@code codecs} 中包含与给定 {@code mimeType} 对应的编解码器字符串的子序列。
   * 如果 {@code mimeType} 为 null、{@code codecs} 为 null，或 {@code codecs} 不包含与 {@code mimeType} 对应的编解码器，则返回 null。
   *
   * @param codecs RFC 6381 编解码器字符串。
   * @param mimeType 要查找的 MIME 类型。
   * @return 包含与 {@code mimeType} 对应的编解码器字符串的子序列。如果未找到，则返回 null。
   */
  @UnstableApi
  @Nullable
  public static String getCodecsCorrespondingToMimeType(
      @Nullable String codecs, @Nullable String mimeType) {
    if (codecs == null || mimeType == null) {
      return null; // 如果编解码器字符串或 MIME 类型为 null，返回 null
    }
    String[] codecList = Util.splitCodecs(codecs); // 将编解码器字符串拆分为数组
    StringBuilder builder = new StringBuilder(); // 用于构建结果字符串
    for (String codec : codecList) {
      if (mimeType.equals(getMediaMimeType(codec))) { // 检查编解码器是否与 MIME 类型匹配
        if (builder.length() > 0) {
          builder.append(","); // 如果已存在其他编解码器，则添加逗号分隔
        }
        builder.append(codec); // 添加匹配的编解码器
      }
    }
    return builder.length() > 0 ? builder.toString() : null; // 返回结果字符串，如果未找到则返回 null
  }

  /**
   * 从 RFC 6381 编解码器字符串中返回第一个音频 MIME 类型。
   *
   * @param codecs RFC 6381 编解码器字符串。
   * @return 第一个派生的音频 MIME 类型，如果未找到则返回 {@code null}。
   */
  @UnstableApi
  @Nullable
  public static String getAudioMediaMimeType(@Nullable String codecs) {
    if (codecs == null) {
      return null; // 如果编解码器字符串为 null，返回 null
    }
    String[] codecList = Util.splitCodecs(codecs); // 将编解码器字符串拆分为数组
    for (String codec : codecList) {
      @Nullable String mimeType = getMediaMimeType(codec); // 获取编解码器对应的 MIME 类型
      if (mimeType != null && isAudio(mimeType)) {
        return mimeType; // 如果 MIME 类型是音频类型，则返回
      }
    }
    return null; // 未找到音频 MIME 类型，返回 null
  }

  /**
   * 从 RFC 6381 编解码器字符串中返回第一个文本 MIME 类型。
   *
   * @param codecs RFC 6381 编解码器字符串。
   * @return 第一个派生的文本 MIME 类型，如果未找到则返回 {@code null}。
   */
  @UnstableApi
  @Nullable
  public static String getTextMediaMimeType(@Nullable String codecs) {
    if (codecs == null) {
      return null; // 如果编解码器字符串为 null，返回 null
    }
    String[] codecList = Util.splitCodecs(codecs); // 将编解码器字符串拆分为数组
    for (String codec : codecList) {
      @Nullable String mimeType = getMediaMimeType(codec); // 获取编解码器对应的 MIME 类型
      if (mimeType != null && isText(mimeType)) {
        return mimeType; // 如果 MIME 类型是文本类型，则返回
      }
    }
    return null; // 未找到文本 MIME 类型，返回 null
  }

  /**
   * 返回与 RFC 6381 编解码器字符串对应的 MIME 类型，如果无法确定则返回 {@code null}。
   *
   * @param codec RFC 6381 编解码器字符串。
   * @return 对应的 MIME 类型，如果无法确定则返回 {@code null}。
   */
  @UnstableApi
  @Nullable
  public static String getMediaMimeType(@Nullable String codec) {
    if (codec == null) {
      return null; // 如果编解码器字符串为 null，返回 null
    }
    codec = Ascii.toLowerCase(codec.trim()); // 将编解码器字符串转换为小写并去除前后空格
    if (codec.startsWith("avc1") || codec.startsWith("avc3")) {
      return MimeTypes.VIDEO_H264; // H.264 视频格式
    } else if (codec.startsWith("hev1") || codec.startsWith("hvc1")) {
      return MimeTypes.VIDEO_H265; // H.265 视频格式
    } else if (codec.startsWith("dvav")
        || codec.startsWith("dva1")
        || codec.startsWith("dvhe")
        || codec.startsWith("dvh1")) {
      return MimeTypes.VIDEO_DOLBY_VISION; // 杜比视界视频格式
    } else if (codec.startsWith("av01")) {
      return MimeTypes.VIDEO_AV1; // AV1 视频格式
    } else if (codec.startsWith("vp9") || codec.startsWith("vp09")) {
      return MimeTypes.VIDEO_VP9; // VP9 视频格式
    } else if (codec.startsWith("vp8") || codec.startsWith("vp08")) {
      return MimeTypes.VIDEO_VP8; // VP8 视频格式
    } else if (codec.startsWith("mp4a")) {
      @Nullable String mimeType = null;
      if (codec.startsWith("mp4a.")) {
        @Nullable Mp4aObjectType objectType = getObjectTypeFromMp4aRFC6381CodecString(codec); // 从编解码器字符串中获取 MP4 对象类型
        if (objectType != null) {
          mimeType = getMimeTypeFromMp4ObjectType(objectType.objectTypeIndication); // 根据对象类型获取 MIME 类型
        }
      }
      return mimeType == null ? MimeTypes.AUDIO_AAC : mimeType; // 默认返回 AAC 音频格式
    } else if (codec.startsWith("mha1")) {
      return MimeTypes.AUDIO_MPEGH_MHA1; // MPEG-H MHA1 音频格式
    } else if (codec.startsWith("mhm1")) {
      return MimeTypes.AUDIO_MPEGH_MHM1; // MPEG-H MHM1 音频格式
    } else if (codec.startsWith("ac-3") || codec.startsWith("dac3")) {
      return MimeTypes.AUDIO_AC3; // AC-3 音频格式
    } else if (codec.startsWith("ec-3") || codec.startsWith("dec3")) {
      return MimeTypes.AUDIO_E_AC3; // E-AC-3 音频格式
    } else if (codec.startsWith(CODEC_E_AC3_JOC)) {
      return MimeTypes.AUDIO_E_AC3_JOC; // E-AC-3 JOC 音频格式
    } else if (codec.startsWith("ac-4") || codec.startsWith("dac4")) {
      return MimeTypes.AUDIO_AC4; // AC-4 音频格式
    } else if (codec.startsWith("dtsc")) {
      return MimeTypes.AUDIO_DTS; // DTS 音频格式
    } else if (codec.startsWith("dtse")) {
      return MimeTypes.AUDIO_DTS_EXPRESS; // DTS Express 音频格式
    } else if (codec.startsWith("dtsh") || codec.startsWith("dtsl")) {
      return MimeTypes.AUDIO_DTS_HD; // DTS-HD 音频格式
    } else if (codec.startsWith("dtsx")) {
      return MimeTypes.AUDIO_DTS_X; // DTS:X 音频格式
    } else if (codec.startsWith("opus")) {
      return MimeTypes.AUDIO_OPUS; // Opus 音频格式
    } else if (codec.startsWith("vorbis")) {
      return MimeTypes.AUDIO_VORBIS; // Vorbis 音频格式
    } else if (codec.startsWith("flac")) {
      return MimeTypes.AUDIO_FLAC; // FLAC 音频格式
    } else if (codec.startsWith("stpp")) {
      return MimeTypes.APPLICATION_TTML; // TTML 字幕格式
    } else if (codec.startsWith("wvtt")) {
      return MimeTypes.TEXT_VTT; // WebVTT 字幕格式
    } else if (codec.contains("cea708")) {
      return MimeTypes.APPLICATION_CEA708; // CEA-708 字幕格式
    } else if (codec.contains("eia608") || codec.contains("cea608")) {
      return MimeTypes.APPLICATION_CEA608; // CEA-608 字幕格式
    } else {
      return getCustomMimeTypeForCodec(codec); // 获取自定义 MIME 类型
    }
  }

  /**
   * 返回与 MIME 类型对应的 MP4 对象类型标识符，定义在 RFC 6381 和 <a href="https://mp4ra.org/registered-types/object-types">MPEG-4 对象类型</a> 中。
   *
   * @param sampleMimeType 轨道的 MIME 类型。
   * @return 对应的 MP4 对象类型标识符，如果无法确定则返回 {@code null}。
   */
  @UnstableApi
  @Nullable
  public static Byte getMp4ObjectTypeFromMimeType(String sampleMimeType) {
    switch (sampleMimeType) {
      case MimeTypes.AUDIO_AAC:
        return (byte) 0x40; // AAC 音频格式对应的对象类型
      case MimeTypes.AUDIO_VORBIS:
        return (byte) 0xDD; // Vorbis 音频格式对应的对象类型
      case MimeTypes.VIDEO_MP4V:
        return (byte) 0x20; // MPEG-4 视频格式对应的对象类型
      default:
        return null; // 其他情况返回 null
    }
  }

  /**
   * 返回与 MP4 对象类型标识符对应的 MIME 类型，定义在 RFC 6381 和 https://mp4ra.org/#/object_types 中。
   *
   * @param objectType MP4 对象类型标识符。
   * @return 对应的 MIME 类型，如果无法确定则返回 {@code null}。
   */
  @UnstableApi
  @Nullable
  public static String getMimeTypeFromMp4ObjectType(int objectType) {
    switch (objectType) {
      case 0x20:
        return MimeTypes.VIDEO_MP4V; // MPEG-4 视频格式
      case 0x21:
        return MimeTypes.VIDEO_H264; // H.264 视频格式
      case 0x23:
        return MimeTypes.VIDEO_H265; // H.265 视频格式
      case 0x60:
      case 0x61:
      case 0x62:
      case 0x63:
      case 0x64:
      case 0x65:
        return MimeTypes.VIDEO_MPEG2; // MPEG-2 视频格式
      case 0x6A:
        return MimeTypes.VIDEO_MPEG; // MPEG 视频格式
      case 0x69:
      case 0x6B:
        return MimeTypes.AUDIO_MPEG; // MPEG 音频格式
      case 0x6C:
        return MimeTypes.IMAGE_JPEG; // JPEG 图像格式
      case 0xA3:
        return MimeTypes.VIDEO_VC1; // VC-1 视频格式
      case 0xB1:
        return MimeTypes.VIDEO_VP9; // VP9 视频格式
      case 0x40:
      case 0x66:
      case 0x67:
      case 0x68:
        return MimeTypes.AUDIO_AAC; // AAC 音频格式
      case 0xA5:
        return MimeTypes.AUDIO_AC3; // AC-3 音频格式
      case 0xA6:
        return MimeTypes.AUDIO_E_AC3; // E-AC-3 音频格式
      case 0xA9:
      case 0xAC:
        return MimeTypes.AUDIO_DTS; // DTS 音频格式
      case 0xAA:
      case 0xAB:
        return MimeTypes.AUDIO_DTS_HD; // DTS-HD 音频格式
      case 0xAD:
        return MimeTypes.AUDIO_OPUS; // Opus 音频格式
      case 0xAE:
        return MimeTypes.AUDIO_AC4; // AC-4 音频格式
      case 0xDD:
        return MimeTypes.AUDIO_VORBIS; // Vorbis 音频格式
      default:
        return null; // 其他情况返回 null
    }
  }

  /**
   * 返回与指定 MIME 类型对应的 {@link C.TrackType 轨道类型} 常量，如果无法确定则返回 {@link C#TRACK_TYPE_UNKNOWN}。
   *
   * @param mimeType MIME 类型。
   * @return 对应的 {@link C.TrackType 轨道类型}，如果无法确定则返回 {@link C#TRACK_TYPE_UNKNOWN}。
   */
  @UnstableApi
  public static @C.TrackType int getTrackType(@Nullable String mimeType) {
    if (TextUtils.isEmpty(mimeType)) {
      return C.TRACK_TYPE_UNKNOWN; // 如果 MIME 类型为空，返回未知类型
    } else if (isAudio(mimeType)) {
      return C.TRACK_TYPE_AUDIO; // 音频轨道类型
    } else if (isVideo(mimeType)) {
      return C.TRACK_TYPE_VIDEO; // 视频轨道类型
    } else if (isText(mimeType)) {
      return C.TRACK_TYPE_TEXT; // 文本轨道类型
    } else if (isImage(mimeType)) {
      return C.TRACK_TYPE_IMAGE; // 图像轨道类型
    } else if (APPLICATION_ID3.equals(mimeType)
        || APPLICATION_EMSG.equals(mimeType)
        || APPLICATION_SCTE35.equals(mimeType)
        || APPLICATION_ICY.equals(mimeType)
        || APPLICATION_AIT.equals(mimeType)) {
      return C.TRACK_TYPE_METADATA; // 元数据轨道类型
    } else if (APPLICATION_CAMERA_MOTION.equals(mimeType)) {
      return C.TRACK_TYPE_CAMERA_MOTION; // 摄像机运动轨道类型
    } else {
      return getTrackTypeForCustomMimeType(mimeType); // 获取自定义 MIME 类型的轨道类型
    }
  }

  /**
   * 返回与指定音频 MIME 类型和 RFC 6381 编解码器字符串对应的 {@link C.Encoding} 常量，如果无法确定则返回 {@link C#ENCODING_INVALID}。
   *
   * @param mimeType MIME 类型。
   * @param codec RFC 6381 编解码器字符串，如果未知或不适用则为 {@code null}。
   * @return 对应的 {@link C.Encoding}，如果无法确定则返回 {@link C#ENCODING_INVALID}。
   */
  @UnstableApi
  public static @C.Encoding int getEncoding(String mimeType, @Nullable String codec) {
    switch (mimeType) {
      case MimeTypes.AUDIO_MPEG:
        return C.ENCODING_MP3;
      case MimeTypes.AUDIO_AAC:
        if (codec == null) {
          return C.ENCODING_INVALID;
        }
        @Nullable Mp4aObjectType objectType = getObjectTypeFromMp4aRFC6381CodecString(codec);
        if (objectType == null) {
          return C.ENCODING_INVALID;
        }
        return objectType.getEncoding();
      case MimeTypes.AUDIO_AC3:
        return C.ENCODING_AC3;
      case MimeTypes.AUDIO_E_AC3:
        return C.ENCODING_E_AC3;
      case MimeTypes.AUDIO_E_AC3_JOC:
        return C.ENCODING_E_AC3_JOC;
      case MimeTypes.AUDIO_AC4:
        return C.ENCODING_AC4;
      case MimeTypes.AUDIO_DTS:
        return C.ENCODING_DTS;
      case MimeTypes.AUDIO_DTS_HD:
        return C.ENCODING_DTS_HD;
      case MimeTypes.AUDIO_DTS_EXPRESS:
        return C.ENCODING_DTS_HD;
      case MimeTypes.AUDIO_DTS_X:
        return C.ENCODING_DTS_UHD_P2;
      case MimeTypes.AUDIO_TRUEHD:
        return C.ENCODING_DOLBY_TRUEHD;
      case MimeTypes.AUDIO_OPUS:
        return C.ENCODING_OPUS;
      default:
        return C.ENCODING_INVALID;
    }
  }
  /**
   * 等效于 {@code getTrackType(getMediaMimeType(codec))}。
   *
   * @param codec RFC 6381 编解码器字符串。
   * @return 对应的 {@link C.TrackType 轨道类型}，如果无法确定则返回 {@link C#TRACK_TYPE_UNKNOWN}。
   */
  @UnstableApi
  public static @C.TrackType int getTrackTypeOfCodec(String codec) {
    return getTrackType(getMediaMimeType(codec)); // 获取编解码器对应的轨道类型
  }

  /**
   * 规范化提供的 MIME 类型，使等效的 MIME 类型具有唯一的表示形式。
   *
   * @param mimeType 要规范化的 MIME 类型，或 null。
   * @return 规范化后的 MIME 类型，如果其规范化形式未知，则返回传入的 MIME 类型。
   */
  @UnstableApi
  public static @PolyNull String normalizeMimeType(@PolyNull String mimeType) {
    if (mimeType == null) {
      return null; // 如果 MIME 类型为 null，返回 null
    }
    mimeType = Ascii.toLowerCase(mimeType); // 将 MIME 类型转换为小写
    switch (mimeType) {
      // 将一些视频 MIME 类型的不常见版本规范化为其标准等效形式。
      case BASE_TYPE_VIDEO + "/x-mvhevc":
        return VIDEO_MV_HEVC; // MV-HEVC 视频格式
      // 将一些音频 MIME 类型的不常见版本规范化为其标准等效形式。
      case BASE_TYPE_AUDIO + "/x-flac":
        return AUDIO_FLAC; // FLAC 音频格式
      case BASE_TYPE_AUDIO + "/mp3":
        return AUDIO_MPEG; // MPEG 音频格式
      case BASE_TYPE_AUDIO + "/x-wav":
        return AUDIO_WAV; // WAV 音频格式
      // 将通常用大写字母书写的 MIME 类型规范化为其常见形式。
      case "application/x-mpegurl":
        return APPLICATION_M3U8; // M3U8 播放列表格式
      case "audio/mpeg-l1":
        return AUDIO_MPEG_L1; // MPEG Layer 1 音频格式
      case "audio/mpeg-l2":
        return AUDIO_MPEG_L2; // MPEG Layer 2 音频格式
      default:
        return mimeType; // 其他情况返回原 MIME 类型
    }
  }

  /** 返回给定的 {@code mimeType} 是否为 Matroska MIME 类型，包括 WebM。 */
  @UnstableApi
  public static boolean isMatroska(@Nullable String mimeType) {
    if (mimeType == null) {
      return false; // 如果 MIME 类型为 null，返回 false
    }
    return mimeType.startsWith(MimeTypes.VIDEO_WEBM) // 检查是否为 WebM 视频格式
        || mimeType.startsWith(MimeTypes.AUDIO_WEBM) // 检查是否为 WebM 音频格式
        || mimeType.startsWith(MimeTypes.APPLICATION_WEBM) // 检查是否为 WebM 应用格式
        || mimeType.startsWith(MimeTypes.VIDEO_MATROSKA) // 检查是否为 Matroska 视频格式
        || mimeType.startsWith(MimeTypes.AUDIO_MATROSKA) // 检查是否为 Matroska 音频格式
        || mimeType.startsWith(MimeTypes.APPLICATION_MATROSKA); // 检查是否为 Matroska 应用格式
  }

  /**
   * 返回 {@code mimeType} 的顶级类型，如果 {@code mimeType} 为 null 或不包含斜杠字符（{@code '/'}），则返回 null。
   */
  @UnstableApi
  @Nullable
  private static String getTopLevelType(@Nullable String mimeType) {
    if (mimeType == null) {
      return null; // 如果 MIME 类型为 null，返回 null
    }
    int indexOfSlash = mimeType.indexOf('/'); // 查找斜杠字符的位置
    if (indexOfSlash == -1) {
      return null; // 如果未找到斜杠字符，返回 null
    }
    return mimeType.substring(0, indexOfSlash); // 返回斜杠字符前的部分（顶级类型）
  }

  @Nullable
  private static String getCustomMimeTypeForCodec(String codec) {
    int customMimeTypeCount = customMimeTypes.size();
    for (int i = 0; i < customMimeTypeCount; i++) {
      CustomMimeType customMimeType = customMimeTypes.get(i);
      if (codec.startsWith(customMimeType.codecPrefix)) {
        return customMimeType.mimeType;
      }
    }
    return null;
  }

  private static @C.TrackType int getTrackTypeForCustomMimeType(String mimeType) {
    int customMimeTypeCount = customMimeTypes.size();
    for (int i = 0; i < customMimeTypeCount; i++) {
      CustomMimeType customMimeType = customMimeTypes.get(i);
      if (mimeType.equals(customMimeType.mimeType)) {
        return customMimeType.trackType;
      }
    }
    return C.TRACK_TYPE_UNKNOWN;
  }

  private MimeTypes() {
    // Prevent instantiation.
  }

  /**
   * 返回 RFC 6381 MP4 音频编解码器字符串的 {@link Mp4aObjectType}。
   *
   * <p>根据 https://mp4ra.org/#/object_types 和 https://tools.ietf.org/html/rfc6381#section-3.3，
   * MP4 编解码器字符串的格式为：
   *
   * <pre>
   *         ~~~~~~~~~~~~~~ 对象类型指示符 (OTI) 字节的十六进制表示
   *    mp4a.[a-zA-Z0-9]{2}(.[0-9]{1,2})?
   *                         ~~~~~~~~~~ 音频 OTI，十进制。仅适用于某些 OTI。
   * </pre>
   *
   * 例如，mp4a.40.2 的 OTI 为 0x40，音频 OTI 为 2。
   *
   * @param codec RFC 6381 MP4 音频编解码器字符串。
   * @return {@link Mp4aObjectType}，如果输入无效则返回 {@code null}。
   */
  @VisibleForTesting
  @Nullable
  /* package */ static Mp4aObjectType getObjectTypeFromMp4aRFC6381CodecString(String codec) {
    Matcher matcher = MP4A_RFC_6381_CODEC_PATTERN.matcher(codec);
    if (!matcher.matches()) {
      return null;
    }
    String objectTypeIndicationHex = Assertions.checkNotNull(matcher.group(1));
    @Nullable String audioObjectTypeIndicationDec = matcher.group(2);
    int objectTypeIndication;
    int audioObjectTypeIndication = 0;
    try {
      objectTypeIndication = Integer.parseInt(objectTypeIndicationHex, 16);
      if (audioObjectTypeIndicationDec != null) {
        audioObjectTypeIndication = Integer.parseInt(audioObjectTypeIndicationDec);
      }
    } catch (NumberFormatException e) {
      return null;
    }
    return new Mp4aObjectType(objectTypeIndication, audioObjectTypeIndication);
  }

  /** 一个 MP4A 对象类型指示符（OTI）及其可选的音频 OTI，由 RFC 6381 定义。 */
  @VisibleForTesting
  /* package */ static final class Mp4aObjectType {
    /** MP4A 编解码器的对象类型指示符（OTI）。 */
    public final int objectTypeIndication;

    /** MP4A 编解码器的音频对象类型指示符（音频 OTI），如果不存在则为 0。 */
    public final int audioObjectTypeIndication;

    public Mp4aObjectType(int objectTypeIndication, int audioObjectTypeIndication) {
      this.objectTypeIndication = objectTypeIndication;
      this.audioObjectTypeIndication = audioObjectTypeIndication;
    }

    /** 返回 {@link #audioObjectTypeIndication} 对应的编码类型。 */
    public @C.Encoding int getEncoding() {
      // 参见 AacUtil 中的 AUDIO_OBJECT_TYPE_AAC_* 常量。
      switch (audioObjectTypeIndication) {
        case 2:
          return C.ENCODING_AAC_LC; // AAC 低复杂度（Low Complexity）编码
        case 5:
          return C.ENCODING_AAC_HE_V1; // AAC 高效版本 1（High Efficiency v1）编码
        case 29:
          return C.ENCODING_AAC_HE_V2; // AAC 高效版本 2（High Efficiency v2）编码
        case 42:
          return C.ENCODING_AAC_XHE; // AAC 扩展高效（Extended High Efficiency）编码
        case 23:
          return C.ENCODING_AAC_ELD; // AAC 增强低延迟（Enhanced Low Delay）编码
        case 22:
          return C.ENCODING_AAC_ER_BSAC; // AAC 错误恢复（Error Resilient）和 BSAC 编码
        default:
          return C.ENCODING_INVALID; // 无效编码类型
      }
    }
  }
  private static final class CustomMimeType {
    public final String mimeType;
    public final String codecPrefix;
    public final @C.TrackType int trackType;

    public CustomMimeType(String mimeType, String codecPrefix, @C.TrackType int trackType) {
      this.mimeType = mimeType;
      this.codecPrefix = codecPrefix;
      this.trackType = trackType;
    }
  }
}
