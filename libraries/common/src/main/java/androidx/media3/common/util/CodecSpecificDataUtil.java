package androidx.media3.common.util;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.annotation.SuppressLint;
import android.media.MediaCodecInfo;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 提供处理各种类型编解码器特定数据的工具方法。 */
@SuppressLint("InlinedApi")
@UnstableApi
public final class CodecSpecificDataUtil {

  private static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};
  private static final String[] HEVC_GENERAL_PROFILE_SPACE_STRINGS =
      new String[] {"", "A", "B", "C"};

  // MP4V-ES
  private static final int VISUAL_OBJECT_LAYER = 1;
  private static final int VISUAL_OBJECT_LAYER_START = 0x20;
  private static final int EXTENDED_PAR = 0x0F;
  private static final int RECTANGULAR = 0x00;

  // 编解码器到常量的映射。
  // H263
  private static final String CODEC_ID_H263 = "s263";
  // AVC.
  private static final String CODEC_ID_AVC1 = "avc1";
  private static final String CODEC_ID_AVC2 = "avc2";
  // VP9
  private static final String CODEC_ID_VP09 = "vp09";
  // HEVC.
  private static final String CODEC_ID_HEV1 = "hev1";
  private static final String CODEC_ID_HVC1 = "hvc1";
  // AV1.
  private static final String CODEC_ID_AV01 = "av01";
  // MP4A AAC.
  private static final String CODEC_ID_MP4A = "mp4a";

  private static final Pattern PROFILE_PATTERN = Pattern.compile("^\\D?(\\d+)$");

  private static final String TAG = "CodecSpecificDataUtil";

  /**
   * 解析 ALAC AudioSpecificConfig（即 <a
   * href="https://github.com/macosforge/alac/blob/master/ALACMagicCookieDescription.txt">ALACSpecificConfig</a>）。
   *
   * @param audioSpecificConfig 包含要解析的 AudioSpecificConfig 的字节数组。
   * @return 包含采样率（Hz）和声道数的 Pair。
   */
  public static Pair<Integer, Integer> parseAlacAudioSpecificConfig(byte[] audioSpecificConfig) {
    ParsableByteArray byteArray = new ParsableByteArray(audioSpecificConfig);
    byteArray.setPosition(9);
    int channelCount = byteArray.readUnsignedByte();
    byteArray.setPosition(20);
    int sampleRate = byteArray.readUnsignedIntToInt();
    return Pair.create(sampleRate, channelCount);
  }

  /**
   * 返回 MIME 类型为 {@link MimeTypes#APPLICATION_CEA708} 的格式的初始化数据。
   *
   * @param isWideAspectRatio CEA-708 闭路字幕服务是否针对 16:9 宽高比的显示器进行了格式化。
   * @return MIME 类型为 {@link MimeTypes#APPLICATION_CEA708} 的格式的初始化数据。
   */
  public static List<byte[]> buildCea708InitializationData(boolean isWideAspectRatio) {
    return Collections.singletonList(isWideAspectRatio ? new byte[] {1} : new byte[] {0});
  }

  /**
   * 返回具有给定初始化数据的 CEA-708 闭路字幕服务是否针对 16:9 宽高比的显示器进行了格式化。
   *
   * @param initializationData 要解析的初始化数据。
   * @return CEA-708 闭路字幕服务是否针对 16:9 宽高比的显示器进行了格式化。
   */
  public static boolean parseCea708InitializationData(List<byte[]> initializationData) {
    return initializationData.size() == 1
        && initializationData.get(0).length == 1
        && initializationData.get(0)[0] == 1;
  }

  /**
   * 返回 VP9 的 CodecPrivate 格式的初始化数据。
   *
   * <p>VP9 CodecPrivate 的每个功能由 ID（1 字节）、长度（1 字节）和数据（1 字节）的二进制格式定义。有关更多详细信息，请参阅 <a>
   * href="https://www.webmproject.org/docs/container/#vp9-codec-feature-metadata-codecprivate">VP9 的 CodecPrivate 格式</a>。
   *
   * @param profile VP9 编解码器配置文件。
   * @param level VP9 编解码器级别。
   * @param bitDepth 亮度和颜色分量的位深度。
   * @param chromaSubsampling 色度子采样。
   */
  public static ImmutableList<byte[]> buildVp9CodecPrivateInitializationData(
      byte profile, byte level, byte bitDepth, byte chromaSubsampling) {
    byte profileId = 0x01;
    byte levelId = 0x02;
    byte bitDepthId = 0x03;
    byte chromaSubsamplingId = 0x04;
    byte length = 0x01;
    return ImmutableList.of(
        new byte[] {
            profileId, length, profile,
            levelId, length, level,
            bitDepthId, length, bitDepth,
            chromaSubsamplingId, length, chromaSubsampling
        });
  }

  /**
   * 解析 MPEG-4 视频配置信息，定义在 ISO/IEC14496-2 中。
   *
   * @param videoSpecificConfig 包含要解析的 MPEG-4 视频配置信息的字节数组。
   * @return 视频宽度和高度的 Pair。
   */
  public static Pair<Integer, Integer> getVideoResolutionFromMpeg4VideoConfig(
      byte[] videoSpecificConfig) {
    int offset = 0;
    boolean foundVOL = false;
    ParsableByteArray scratchBytes = new ParsableByteArray(videoSpecificConfig);
    while (offset + 3 < videoSpecificConfig.length) {
      if (scratchBytes.readUnsignedInt24() != VISUAL_OBJECT_LAYER
          || (videoSpecificConfig[offset + 3] & 0xF0) != VISUAL_OBJECT_LAYER_START) {
        scratchBytes.setPosition(scratchBytes.getPosition() - 2);
        offset++;
        continue;
      }
      foundVOL = true;
      break;
    }

    checkArgument(foundVOL, "无效输入：未找到 VOL。");

    ParsableBitArray scratchBits = new ParsableBitArray(videoSpecificConfig);
    // 跳过比特流中的起始码
    scratchBits.skipBits((offset + 4) * 8);
    scratchBits.skipBits(1); // random_accessible_vol
    scratchBits.skipBits(8); // video_object_type_indication

    if (scratchBits.readBit()) { // object_layer_identifier
      scratchBits.skipBits(4); // video_object_layer_verid
      scratchBits.skipBits(3); // video_object_layer_priority
    }

    int aspectRatioInfo = scratchBits.readBits(4);
    if (aspectRatioInfo == EXTENDED_PAR) {
      scratchBits.skipBits(8); // par_width
      scratchBits.skipBits(8); // par_height
    }

    if (scratchBits.readBit()) { // vol_control_parameters
      scratchBits.skipBits(2); // chroma_format
      scratchBits.skipBits(1); // low_delay
      if (scratchBits.readBit()) { // vbv_parameters
        scratchBits.skipBits(79);
      }
    }

    int videoObjectLayerShape = scratchBits.readBits(2);
    checkArgument(
        videoObjectLayerShape == RECTANGULAR,
        "仅支持矩形视频对象层形状。");

    checkArgument(scratchBits.readBit()); // marker_bit
    int vopTimeIncrementResolution = scratchBits.readBits(16);
    checkArgument(scratchBits.readBit()); // marker_bit

    if (scratchBits.readBit()) { // fixed_vop_rate
      checkArgument(vopTimeIncrementResolution > 0);
      vopTimeIncrementResolution--;
      int numBitsToSkip = 0;
      while (vopTimeIncrementResolution > 0) {
        numBitsToSkip++;
        vopTimeIncrementResolution >>= 1;
      }
      scratchBits.skipBits(numBitsToSkip); // fixed_vop_time_increment
    }

    checkArgument(scratchBits.readBit()); // marker_bit
    int videoObjectLayerWidth = scratchBits.readBits(13);
    checkArgument(scratchBits.readBit()); // marker_bit
    int videoObjectLayerHeight = scratchBits.readBits(13);
    checkArgument(scratchBits.readBit()); // marker_bit

    scratchBits.skipBits(1); // interlaced

    return Pair.create(videoObjectLayerWidth, videoObjectLayerHeight);
  }

  /**
   * 使用提供的参数构建 RFC 6381 AVC 编解码器字符串。
   *
   * @param profileIdc 编码配置文件。
   * @param constraintsFlagsAndReservedZero2Bits 约束标志，后跟保留的 2 位零，全部包含在整数的低字节中。
   * @param levelIdc 编码级别。
   * @return 使用提供的参数构建的 RFC 6381 AVC 编解码器字符串。
   */
  public static String buildAvcCodecString(
      int profileIdc, int constraintsFlagsAndReservedZero2Bits, int levelIdc) {
    return String.format(
        "avc1.%02X%02X%02X", profileIdc, constraintsFlagsAndReservedZero2Bits, levelIdc);
  }

  /** 使用提供的参数构建 RFC 6381 HEVC 编解码器字符串。 */
  public static String buildHevcCodecString(
      int generalProfileSpace,
      boolean generalTierFlag,
      int generalProfileIdc,
      int generalProfileCompatibilityFlags,
      int[] constraintBytes,
      int generalLevelIdc) {
    StringBuilder builder =
        new StringBuilder(
            Util.formatInvariant(
                "hvc1.%s%d.%X.%c%d",
                HEVC_GENERAL_PROFILE_SPACE_STRINGS[generalProfileSpace],
                generalProfileIdc,
                generalProfileCompatibilityFlags,
                generalTierFlag ? 'H' : 'L',
                generalLevelIdc));
    // 省略尾随的零字节。
    int trailingZeroIndex = constraintBytes.length;
    while (trailingZeroIndex > 0 && constraintBytes[trailingZeroIndex - 1] == 0) {
      trailingZeroIndex--;
    }
    for (int i = 0; i < trailingZeroIndex; i++) {
      builder.append(String.format(".%02X", constraintBytes[i]));
    }
    return builder.toString();
  }

  /** 使用配置文件和级别构建 RFC 6381 H263 编解码器字符串。 */
  public static String buildH263CodecString(int profile, int level) {
    return Util.formatInvariant("s263.%d.%d", profile, level);
  }

  /**
   * 返回与给定格式的编解码器描述字符串（定义在 RFC 6381 中）对应的配置文件和级别（定义在 {@link MediaCodecInfo.CodecProfileLevel} 中）。
   *
   * @param format 具有编解码器描述字符串的媒体格式，定义在 RFC 6381 中。
   * @return 如果 {@code format} 的编解码器格式正确且被识别，则返回 Pair（配置文件常量，级别常量），否则返回 null。
   */
  @Nullable
  public static Pair<Integer, Integer> getCodecProfileAndLevel(Format format) {
    if (format.codecs == null) {
      return null;
    }
    String[] parts = format.codecs.split("\\.");
    // Dolby Vision 可以使用 DV、AVC 或 HEVC 编解码器 ID，因此首先检查 MIME 类型。
    if (MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)) {
      return getDolbyVisionProfileAndLevel(format.codecs, parts);
    }
    switch (parts[0]) {
      case CODEC_ID_H263:
        return getH263ProfileAndLevel(format.codecs, parts);
      case CODEC_ID_AVC1:
      case CODEC_ID_AVC2:
        return getAvcProfileAndLevel(format.codecs, parts);
      case CODEC_ID_VP09:
        return getVp9ProfileAndLevel(format.codecs, parts);
      case CODEC_ID_HEV1:
      case CODEC_ID_HVC1:
        return getHevcProfileAndLevel(format.codecs, parts, format.colorInfo);
      case CODEC_ID_AV01:
        return getAv1ProfileAndLevel(format.codecs, parts, format.colorInfo);
      case CODEC_ID_MP4A:
        return getAacCodecProfileAndLevel(format.codecs, parts);
      default:
        return null;
    }
  }

  /**
   * 返回与编解码器描述字符串（定义在 RFC 6381 中）及其 {@link ColorInfo} 对应的 HEVC 配置文件和级别。
   *
   * @param codec 编解码器描述字符串，定义在 RFC 6381 中。
   * @param parts 按 "." 分割的编解码器字符串。
   * @param colorInfo {@link ColorInfo}。
   * @return 如果配置文件和级别被识别，则返回 Pair（配置文件常量，级别常量），否则返回 {@code null}。
   */
  @Nullable
  public static Pair<Integer, Integer> getHevcProfileAndLevel(
      String codec, String[] parts, @Nullable ColorInfo colorInfo) {
    if (parts.length < 4) {
      // 编解码器的部分少于 HEVC 编解码器字符串格式所需的部分。
      Log.w(TAG, "忽略格式错误的 HEVC 编解码器字符串：" + codec);
      return null;
    }
    // 忽略 profile_space。
    Matcher matcher = PROFILE_PATTERN.matcher(parts[1]);
    if (!matcher.matches()) {
      Log.w(TAG, "忽略格式错误的 HEVC 编解码器字符串：" + codec);
      return null;
    }
    @Nullable String profileString = matcher.group(1);
    int profile;
    if ("1".equals(profileString)) {
      profile = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain;
    } else if ("2".equals(profileString)) {
      if (colorInfo != null && colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084) {
        profile = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10;
      } else {
        // 对于所有其他情况，我们映射到 Main10 配置文件。注意，这包括 HLG HDR。在 Android 13+ 上，平台保证支持 HEVCProfileMain10 的解码器将能够解码 HLG。对于较旧的 Android 版本，此保证不成立，但我们仍然映射到 Main10 以保持向后兼容性。
        profile = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10;
      }
    } else if ("6".equals(profileString)) {
      // 框架未定义 profileLevel.HEVCProfileMultiviewMain。
      profile = 6;
    } else {
      Log.w(TAG, "未知的 HEVC 配置文件字符串：" + profileString);
      return null;
    }
    @Nullable String levelString = parts[3];
    @Nullable Integer level = hevcCodecStringToProfileLevel(levelString);
    if (level == null) {
      Log.w(TAG, "未知的 HEVC 级别字符串：" + levelString);
      return null;
    }
    return new Pair<>(profile, level);
  }

  /**
   * 构建一个由 NAL 起始码后跟指定数据组成的 NAL 单元。
   *
   * @param data 包含应跟在 NAL 起始码之后的数据的数组。
   * @param offset {@code data} 中的起始偏移量。
   * @param length 要从 {@code data} 复制的字节数。
   * @return 构建的 NAL 单元。
   */
  public static byte[] buildNalUnit(byte[] data, int offset, int length) {
    byte[] nalUnit = new byte[length + NAL_START_CODE.length];
    System.arraycopy(NAL_START_CODE, 0, nalUnit, 0, NAL_START_CODE.length);
    System.arraycopy(data, offset, nalUnit, NAL_START_CODE.length, length);
    return nalUnit;
  }

  /**
   * 将 NAL 单元数组分割。
   *
   * <p>如果输入由 NAL 起始码分隔的单元组成，则返回的数组由分割后的 NAL 单元组成，每个单元仍以 NAL 起始码为前缀。对于任何其他输入，返回 null。
   *
   * @param data 数据数组。
   * @return 各个 NAL 单元，如果输入不由 NAL 起始码分隔的单元组成，则返回 null。
   */
  @Nullable
  public static byte[][] splitNalUnits(byte[] data) {
    if (!isNalStartCode(data, 0)) {
      // data does not consist of NAL start code delimited units.
      return null;
    }
    List<Integer> starts = new ArrayList<>();
    int nalUnitIndex = 0;
    do {
      starts.add(nalUnitIndex);
      nalUnitIndex = findNalStartCode(data, nalUnitIndex + NAL_START_CODE.length);
    } while (nalUnitIndex != C.INDEX_UNSET);
    byte[][] split = new byte[starts.size()][];
    for (int i = 0; i < starts.size(); i++) {
      int startIndex = starts.get(i);
      int endIndex = i < starts.size() - 1 ? starts.get(i + 1) : data.length;
      byte[] nal = new byte[endIndex - startIndex];
      System.arraycopy(data, startIndex, nal, 0, nal.length);
      split[i] = nal;
    }
    return split;
  }


  /**
   * 从给定索引开始查找下一个 NAL 起始码。
   *
   * @param data 要在其中搜索的数据。
   * @param index 要测试的第一个索引。
   * @return 找到的起始码的第一个字节的索引，或 {@link C#INDEX_UNSET}。
   */
  private static int findNalStartCode(byte[] data, int index) {

    int endIndex = data.length - NAL_START_CODE.length;
    for (int i = index; i <= endIndex; i++) {
      if (isNalStartCode(data, i)) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  /**
   * 测试给定索引处是否存在 NAL 起始码。
   *
   * @param data 数据。
   * @param index 要测试的索引。
   * @return 是否存在以 {@code index} 开头的起始码。
   */
  private static boolean isNalStartCode(byte[] data, int index) {

    if (data.length - index <= NAL_START_CODE.length) {
      return false;
    }
    for (int j = 0; j < NAL_START_CODE.length; j++) {
      if (data[index + j] != NAL_START_CODE[j]) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private static Pair<Integer, Integer> getDolbyVisionProfileAndLevel(
      String codec, String[] parts) {
    if (parts.length < 3) {
      // 编解码器的部分少于 Dolby Vision 编解码器字符串格式所需的部分。
      Log.w(TAG, "忽略格式错误的 Dolby Vision 编解码器字符串：" + codec);
      return null;
    }
    // 忽略 profile_space。
    Matcher matcher = PROFILE_PATTERN.matcher(parts[1]);
    if (!matcher.matches()) {
      Log.w(TAG, "忽略格式错误的 Dolby Vision 编解码器字符串：" + codec);
      return null;
    }
    @Nullable String profileString = matcher.group(1);
    @Nullable Integer profile = dolbyVisionStringToProfile(profileString);
    if (profile == null) {
      Log.w(TAG, "未知的 Dolby Vision 配置文件字符串：" + profileString);
      return null;
    }
    String levelString = parts[2];
    @Nullable Integer level = dolbyVisionStringToLevel(levelString);
    if (level == null) {
      Log.w(TAG, "未知的 Dolby Vision 级别字符串：" + levelString);
      return null;
    }
    return new Pair<>(profile, level);
  }

  /** 从编解码器字符串中返回 H263 配置文件和级别。 */
  private static Pair<Integer, Integer> getH263ProfileAndLevel(String codec, String[] parts) {
    Pair<Integer, Integer> defaultProfileAndLevel =
        new Pair<>(
            MediaCodecInfo.CodecProfileLevel.H263ProfileBaseline,
            MediaCodecInfo.CodecProfileLevel.H263Level10);
    if (parts.length < 3) {
      Log.w(TAG, "忽略格式错误的 H263 编解码器字符串：" + codec);
      return defaultProfileAndLevel;
    }

    try {
      int profile = Integer.parseInt(parts[1]);
      int level = Integer.parseInt(parts[2]);
      return new Pair<>(profile, level);
    } catch (NumberFormatException e) {
      Log.w(TAG, "忽略格式错误的 H263 编解码器字符串：" + codec);
      return defaultProfileAndLevel;
    }
  }

  @Nullable
  private static Pair<Integer, Integer> getAvcProfileAndLevel(String codec, String[] parts) {
    if (parts.length < 2) {
      // 编解码器的部分少于 AVC 编解码器字符串格式所需的部分。
      Log.w(TAG, "忽略格式错误的 AVC 编解码器字符串：" + codec);
      return null;
    }
    int profileInteger;
    int levelInteger;
    try {
      if (parts[1].length() == 6) {
        // 格式：avc1.xxccyy，其中 xx 是配置文件，yy 是级别，均为十六进制。
        profileInteger = Integer.parseInt(parts[1].substring(0, 2), 16);
        levelInteger = Integer.parseInt(parts[1].substring(4), 16);
      } else if (parts.length >= 3) {
        // 格式：avc1.xx.[y]yy，其中 xx 是配置文件，[y]yy 是级别，均为十进制。
        profileInteger = Integer.parseInt(parts[1]);
        levelInteger = Integer.parseInt(parts[2]);
      } else {
        // 我们不识别该格式。
        Log.w(TAG, "忽略格式错误的 AVC 编解码器字符串：" + codec);
        return null;
      }
    } catch (NumberFormatException e) {
      Log.w(TAG, "忽略格式错误的 AVC 编解码器字符串：" + codec);
      return null;
    }

    int profile = avcProfileNumberToConst(profileInteger);
    if (profile == -1) {
      Log.w(TAG, "未知的 AVC 配置文件：" + profileInteger);
      return null;
    }
    int level = avcLevelNumberToConst(levelInteger);
    if (level == -1) {
      Log.w(TAG, "未知的 AVC 级别：" + levelInteger);
      return null;
    }
    return new Pair<>(profile, level);
  }

  @Nullable
  private static Pair<Integer, Integer> getVp9ProfileAndLevel(String codec, String[] parts) {
    if (parts.length < 3) {
      Log.w(TAG, "忽略格式错误的 VP9 编解码器字符串：" + codec);
      return null;
    }
    int profileInteger;
    int levelInteger;
    try {
      profileInteger = Integer.parseInt(parts[1]);
      levelInteger = Integer.parseInt(parts[2]);
    } catch (NumberFormatException e) {
      Log.w(TAG, "忽略格式错误的 VP9 编解码器字符串：" + codec);
      return null;
    }

    int profile = vp9ProfileNumberToConst(profileInteger);
    if (profile == -1) {
      Log.w(TAG, "未知的 VP9 配置文件：" + profileInteger);
      return null;
    }
    int level = vp9LevelNumberToConst(levelInteger);
    if (level == -1) {
      Log.w(TAG, "未知的 VP9 级别：" + levelInteger);
      return null;
    }
    return new Pair<>(profile, level);
  }

  @Nullable
  private static Pair<Integer, Integer> getAv1ProfileAndLevel(
      String codec, String[] parts, @Nullable ColorInfo colorInfo) {
    if (parts.length < 4) {
      Log.w(TAG, "忽略格式错误的 AV1 编解码器字符串：" + codec);
      return null;
    }
    int profileInteger;
    int levelInteger;
    int bitDepthInteger;
    try {
      profileInteger = Integer.parseInt(parts[1]);
      levelInteger = Integer.parseInt(parts[2].substring(0, 2));
      bitDepthInteger = Integer.parseInt(parts[3]);
    } catch (NumberFormatException e) {
      Log.w(TAG, "忽略格式错误的 AV1 编解码器字符串：" + codec);
      return null;
    }

    if (profileInteger != 0) {
      Log.w(TAG, "未知的 AV1 配置文件：" + profileInteger);
      return null;
    }
    if (bitDepthInteger != 8 && bitDepthInteger != 10) {
      Log.w(TAG, "未知的 AV1 位深度：" + bitDepthInteger);
      return null;
    }
    int profile;
    if (bitDepthInteger == 8) {
      profile = MediaCodecInfo.CodecProfileLevel.AV1ProfileMain8;
    } else if (colorInfo != null
        && (colorInfo.hdrStaticInfo != null
        || colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
        || colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084)) {
      profile = MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10;
    } else {
      profile = MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10;
    }

    int level = av1LevelNumberToConst(levelInteger);
    if (level == -1) {
      Log.w(TAG, "未知的 AV1 级别：" + levelInteger);
      return null;
    }
    return new Pair<>(profile, level);
  }

  @Nullable
  private static Pair<Integer, Integer> getAacCodecProfileAndLevel(String codec, String[] parts) {
    if (parts.length != 3) {
      Log.w(TAG, "忽略格式错误的 MP4A 编解码器字符串：" + codec);
      return null;
    }
    try {
      // 获取对象类型指示，它是一个十六进制值（参见 RFC 6381/ISO 14496-1）。
      int objectTypeIndication = Integer.parseInt(parts[1], 16);
      @Nullable String mimeType = MimeTypes.getMimeTypeFromMp4ObjectType(objectTypeIndication);
      if (MimeTypes.AUDIO_AAC.equals(mimeType)) {
        // 对于 MPEG-4 音频，其后是音频对象类型指示，为十进制数。
        int audioObjectTypeIndication = Integer.parseInt(parts[2]);
        int profile = mp4aAudioObjectTypeToProfile(audioObjectTypeIndication);
        if (profile != -1) {
          // 在 AAC 解码器的 CodecProfileLevels 中，级别设置为零。
          return new Pair<>(profile, 0);
        }
      }
    } catch (NumberFormatException e) {
      Log.w(TAG, "忽略格式错误的 MP4A 编解码器字符串：" + codec);
    }
    return null;
  }

  private static int avcProfileNumberToConst(int profileNumber) {
    switch (profileNumber) {
      case 66:
        return MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
      case 77:
        return MediaCodecInfo.CodecProfileLevel.AVCProfileMain;
      case 88:
        return MediaCodecInfo.CodecProfileLevel.AVCProfileExtended;
      case 100:
        return MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
      case 110:
        return MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10;
      case 122:
        return MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422;
      case 244:
        return MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444;
      default:
        return -1;
    }
  }

  private static int avcLevelNumberToConst(int levelNumber) {
    // TODO: 找到 CodecProfileLevel.AVCLevel1b 的整数值。
    switch (levelNumber) {
      case 10:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel1;
      case 11:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel11;
      case 12:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel12;
      case 13:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel13;
      case 20:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel2;
      case 21:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel21;
      case 22:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel22;
      case 30:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel3;
      case 31:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel31;
      case 32:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel32;
      case 40:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel4;
      case 41:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel41;
      case 42:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel42;
      case 50:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel5;
      case 51:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel51;
      case 52:
        return MediaCodecInfo.CodecProfileLevel.AVCLevel52;
      default:
        return -1;
    }
  }

  private static int vp9ProfileNumberToConst(int profileNumber) {
    switch (profileNumber) {
      case 0:
        return MediaCodecInfo.CodecProfileLevel.VP9Profile0;
      case 1:
        return MediaCodecInfo.CodecProfileLevel.VP9Profile1;
      case 2:
        return MediaCodecInfo.CodecProfileLevel.VP9Profile2;
      case 3:
        return MediaCodecInfo.CodecProfileLevel.VP9Profile3;
      default:
        return -1;
    }
  }

  private static int vp9LevelNumberToConst(int levelNumber) {
    switch (levelNumber) {
      case 10:
        return MediaCodecInfo.CodecProfileLevel.VP9Level1;
      case 11:
        return MediaCodecInfo.CodecProfileLevel.VP9Level11;
      case 20:
        return MediaCodecInfo.CodecProfileLevel.VP9Level2;
      case 21:
        return MediaCodecInfo.CodecProfileLevel.VP9Level21;
      case 30:
        return MediaCodecInfo.CodecProfileLevel.VP9Level3;
      case 31:
        return MediaCodecInfo.CodecProfileLevel.VP9Level31;
      case 40:
        return MediaCodecInfo.CodecProfileLevel.VP9Level4;
      case 41:
        return MediaCodecInfo.CodecProfileLevel.VP9Level41;
      case 50:
        return MediaCodecInfo.CodecProfileLevel.VP9Level5;
      case 51:
        return MediaCodecInfo.CodecProfileLevel.VP9Level51;
      case 60:
        return MediaCodecInfo.CodecProfileLevel.VP9Level6;
      case 61:
        return MediaCodecInfo.CodecProfileLevel.VP9Level61;
      case 62:
        return MediaCodecInfo.CodecProfileLevel.VP9Level62;
      default:
        return -1;
    }
  }

  @Nullable
  private static Integer hevcCodecStringToProfileLevel(@Nullable String codecString) {
    if (codecString == null) {
      return null;
    }
    switch (codecString) {
      case "L30":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel1;
      case "L60":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel2;
      case "L63":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel21;
      case "L90":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel3;
      case "L93":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel31;
      case "L120":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4;
      case "L123":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41;
      case "L150":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel5;
      case "L153":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51;
      case "L156":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel52;
      case "L180":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel6;
      case "L183":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel61;
      case "L186":
        return MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel62;
      case "H30":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel1;
      case "H60":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel2;
      case "H63":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel21;
      case "H90":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel3;
      case "H93":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel31;
      case "H120":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel4;
      case "H123":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel41;
      case "H150":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel5;
      case "H153":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel51;
      case "H156":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel52;
      case "H180":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel6;
      case "H183":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel61;
      case "H186":
        return MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel62;
      default:
        return null;
    }
  }

  @Nullable
  private static Integer dolbyVisionStringToProfile(@Nullable String profileString) {
    if (profileString == null) {
      return null;
    }
    switch (profileString) {
      case "00":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavPer;
      case "01":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavPen;
      case "02":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDer;
      case "03":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDen;
      case "04":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtr;
      case "05":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn;
      case "06":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDth;
      case "07":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtb;
      case "08":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt;
      case "09":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavSe;
      case "10":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvav110;
      default:
        return null;
    }
  }

  @Nullable
  private static Integer dolbyVisionStringToLevel(@Nullable String levelString) {
    if (levelString == null) {
      return null;
    }
    // TODO (内部问题: b/179261323): 使用框架常量表示级别 13。
    switch (levelString) {
      case "01":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelHd24;
      case "02":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelHd30;
      case "03":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd24;
      case "04":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd30;
      case "05":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd60;
      case "06":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd24;
      case "07":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd30;
      case "08":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd48;
      case "09":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd60;
      case "10":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd120;
      case "11":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionLevel8k30;
      case "12":
        return MediaCodecInfo.CodecProfileLevel.DolbyVisionLevel8k60;
      case "13":
        return 0x1000;
      default:
        return null;
    }
  }

  private static int av1LevelNumberToConst(int levelNumber) {
    // 有关将 AV1 编解码器字符串映射到级别的更多信息，请参阅 https://aomediacodec.github.io/av1-spec/av1-spec.pdf 附录 A：配置文件和级别。
    switch (levelNumber) {
      case 0:
        return MediaCodecInfo.CodecProfileLevel.AV1Level2;
      case 1:
        return MediaCodecInfo.CodecProfileLevel.AV1Level21;
      case 2:
        return MediaCodecInfo.CodecProfileLevel.AV1Level22;
      case 3:
        return MediaCodecInfo.CodecProfileLevel.AV1Level23;
      case 4:
        return MediaCodecInfo.CodecProfileLevel.AV1Level3;
      case 5:
        return MediaCodecInfo.CodecProfileLevel.AV1Level31;
      case 6:
        return MediaCodecInfo.CodecProfileLevel.AV1Level32;
      case 7:
        return MediaCodecInfo.CodecProfileLevel.AV1Level33;
      case 8:
        return MediaCodecInfo.CodecProfileLevel.AV1Level4;
      case 9:
        return MediaCodecInfo.CodecProfileLevel.AV1Level41;
      case 10:
        return MediaCodecInfo.CodecProfileLevel.AV1Level42;
      case 11:
        return MediaCodecInfo.CodecProfileLevel.AV1Level43;
      case 12:
        return MediaCodecInfo.CodecProfileLevel.AV1Level5;
      case 13:
        return MediaCodecInfo.CodecProfileLevel.AV1Level51;
      case 14:
        return MediaCodecInfo.CodecProfileLevel.AV1Level52;
      case 15:
        return MediaCodecInfo.CodecProfileLevel.AV1Level53;
      case 16:
        return MediaCodecInfo.CodecProfileLevel.AV1Level6;
      case 17:
        return MediaCodecInfo.CodecProfileLevel.AV1Level61;
      case 18:
        return MediaCodecInfo.CodecProfileLevel.AV1Level62;
      case 19:
        return MediaCodecInfo.CodecProfileLevel.AV1Level63;
      case 20:
        return MediaCodecInfo.CodecProfileLevel.AV1Level7;
      case 21:
        return MediaCodecInfo.CodecProfileLevel.AV1Level71;
      case 22:
        return MediaCodecInfo.CodecProfileLevel.AV1Level72;
      case 23:
        return MediaCodecInfo.CodecProfileLevel.AV1Level73;
      default:
        return -1;
    }
  }

  private static int mp4aAudioObjectTypeToProfile(int profileNumber) {
    switch (profileNumber) {
      case 1:
        return MediaCodecInfo.CodecProfileLevel.AACObjectMain;
      case 2:
        return MediaCodecInfo.CodecProfileLevel.AACObjectLC;
      case 3:
        return MediaCodecInfo.CodecProfileLevel.AACObjectSSR;
      case 4:
        return MediaCodecInfo.CodecProfileLevel.AACObjectLTP;
      case 5:
        return MediaCodecInfo.CodecProfileLevel.AACObjectHE;
      case 6:
        return MediaCodecInfo.CodecProfileLevel.AACObjectScalable;
      case 17:
        return MediaCodecInfo.CodecProfileLevel.AACObjectERLC;
      case 20:
        return MediaCodecInfo.CodecProfileLevel.AACObjectERScalable;
      case 23:
        return MediaCodecInfo.CodecProfileLevel.AACObjectLD;
      case 29:
        return MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS;
      case 39:
        return MediaCodecInfo.CodecProfileLevel.AACObjectELD;
      case 42:
        return MediaCodecInfo.CodecProfileLevel.AACObjectXHE;
      default:
        return -1;
    }
  }

  private CodecSpecificDataUtil() {}
}
