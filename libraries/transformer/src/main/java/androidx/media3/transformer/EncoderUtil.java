/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.transformer;

import static java.lang.Math.floor;
import static java.lang.Math.max;
import static java.lang.Math.round;

import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.C.ColorTransfer;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Ascii;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;

/**
 * 提供与{@link MediaCodec}编码器相关的实用方法。
 */
@UnstableApi
public final class EncoderUtil {

  /** 表示未设置编码级别的常量 */
  public static final int LEVEL_UNSET = Format.NO_VALUE;

  // 使用多重映射存储MIME类型到编码器的关系（线程安全，需同步访问）
  @GuardedBy("EncoderUtil.class")
  private static final ArrayListMultimap<String, MediaCodecInfo> mimeTypeToEncoders =
      ArrayListMultimap.create();

  /**
   * 获取支持指定MIME类型的所有编码器列表。
   *
   * @param mimeType 媒体类型（如"video/avc"）
   * @return 包含支持该类型的编码器的不可变列表，若无则返回空列表
   */
  public static synchronized ImmutableList<MediaCodecInfo> getSupportedEncoders(String mimeType) {
    maybePopulateEncoderInfo();
    return ImmutableList.copyOf(mimeTypeToEncoders.get(Ascii.toLowerCase(mimeType)));
  }

  /**
   * 获取设备支持的所有可编码MIME类型。
   *
   * @return 包含所有支持类型的不可变集合
   */
  public static synchronized ImmutableSet<String> getSupportedMimeTypes() {
    maybePopulateEncoderInfo();
    return ImmutableSet.copyOf(mimeTypeToEncoders.keySet());
  }

  /**
   * 清除缓存的编码器信息（主要用于测试环境重置状态）
   */
  @VisibleForTesting
  public static synchronized void clearCachedEncoders() {
    mimeTypeToEncoders.clear();
  }

  /**
   * 获取支持HDR编辑的编码器列表。
   *
   * @param mimeType 媒体类型
   * @param colorInfo 颜色信息（包含色彩传输特性）
   * @return 支持HDR编辑的编码器列表，若设备不支持或参数无效返回空列表
   */
  public static ImmutableList<MediaCodecInfo> getSupportedEncodersForHdrEditing(
      String mimeType, @Nullable ColorInfo colorInfo) {
    // 最低需要API 33且需要有效的颜色信息
    if (Util.SDK_INT < 33 || colorInfo == null) {
      return ImmutableList.of();
    }

    ImmutableList<MediaCodecInfo> encoders = getSupportedEncoders(mimeType);
    ImmutableList<Integer> allowedColorProfiles =
        getCodecProfilesForHdrFormat(mimeType, colorInfo.colorTransfer);
    ImmutableList.Builder<MediaCodecInfo> resultBuilder = new ImmutableList.Builder<>();
    for (int i = 0; i < encoders.size(); i++) {
      MediaCodecInfo mediaCodecInfo = encoders.get(i);
      if (mediaCodecInfo.isAlias()) {
        continue; // 跳过别名编码器
      }

      // 检查HDR编辑特性支持
      boolean hasNeededHdrSupport =
          isFeatureSupported(
                  mediaCodecInfo, mimeType, MediaCodecInfo.CodecCapabilities.FEATURE_HdrEditing)
              || (colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG // 特殊处理HLG格式在API 35+的情况
                  && Util.SDK_INT >= 35
                  && isFeatureSupported(
                      mediaCodecInfo,
                      mimeType,
                      MediaCodecInfo.CodecCapabilities.FEATURE_HlgEditing));
      if (!hasNeededHdrSupport) {
        continue;
      }

      // 检查支持的编码配置是否包含允许的profile
      for (MediaCodecInfo.CodecProfileLevel codecProfileLevel :
          mediaCodecInfo.getCapabilitiesForType(mimeType).profileLevels) {
        if (allowedColorProfiles.contains(codecProfileLevel.profile)) {
          resultBuilder.add(mediaCodecInfo);
        }
      }
    }
    return resultBuilder.build();
  }

  /**
   * 根据指定的视频编码格式和HDR色彩传输特性，返回支持的编码配置(Codec Profile)列表。
   *
   * @param mimeType 视频媒体类型（例如: "video/vp9", "video/hevc"）
   * @param colorTransfer 色彩传输特性，取值来自{@link C.ColorTransfer}常量
   * @return 包含支持配置的不可变列表，若无匹配返回空列表
   */
  public static ImmutableList<Integer> getCodecProfilesForHdrFormat(
      String mimeType, @ColorTransfer int colorTransfer) {
    // TODO(b/239174610): 待添加对Dolby Vision和HDR10+格式的支持

    // 根据不同的视频编码格式进行分支处理
    switch (mimeType) {
      case MimeTypes.VIDEO_VP9:
        // VP9编码支持HLG和PQ两种HDR格式
        if (colorTransfer == C.COLOR_TRANSFER_HLG || colorTransfer == C.COLOR_TRANSFER_ST2084) {
          // VP9 HDR配置文件（Profile 2和3都支持HDR）
          return ImmutableList.of(
              MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR,
              MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR);
        }
        break;

      case MimeTypes.VIDEO_H264:
        // H.264编码仅支持HLG格式的10-bit High Profile
        if (colorTransfer == C.COLOR_TRANSFER_HLG) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10);
        }
        // 注：H264标准未定义PQ（ST2084）的官方Profile
        break;

      case MimeTypes.VIDEO_H265:
        // HEVC编码的Main10 Profile支持HLG
        if (colorTransfer == C.COLOR_TRANSFER_HLG) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10);
        }
        // HEVC的特殊HDR10配置文件
        else if (colorTransfer == C.COLOR_TRANSFER_ST2084) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10);
        }
        break;

      case MimeTypes.VIDEO_AV1:
        // AV1的Main10 Profile支持HLG
        if (colorTransfer == C.COLOR_TRANSFER_HLG) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10);
        }
        // AV1的Main10+HDR10配置文件
        else if (colorTransfer == C.COLOR_TRANSFER_ST2084) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10);
        }
        break;

      default:
        // 其他未处理的视频格式直接跳过
        break;
    }

    // 没有找到匹配的HDR配置文件或参数不合法时返回空列表
    return ImmutableList.of();
  }

  /**
   * 检查指定编码器是否支持给定的分辨率。
   *
   * 实现说明：
   * 1. 首先通过编码器官方声明的能力进行验证
   * 2. 针对特定设备的兼容性问题进行特殊处理
   *
   * @param encoderInfo 编码器信息对象（需确保已正确初始化）
   * @param mimeType 媒体格式类型（如"video/avc"）
   * @param width 需要验证的视频宽度（像素单位）
   * @param height 需要验证的视频高度（像素单位）
   * @return 支持返回true，否则返回false
   */
  public static boolean isSizeSupported(
      MediaCodecInfo encoderInfo, String mimeType, int width, int height) {

    // 第一步：标准验证流程
    if (encoderInfo
        .getCapabilitiesForType(mimeType)
        .getVideoCapabilities()
        .isSizeSupported(width, height)) {
      return true; // 编码器官方声明支持该分辨率
    }

    /*
     * 设备兼容性处理：
     * 已知问题（参见b/222095724，b/229825948）：
     * - 部分设备（三星、华为、Pixel 6系列）存在低报编码能力的问题
     * - 典型表现：
     *   - H265编码的3840x2160实际支持高度被错误报告为2144
     *   - H264编码的1920x1080实际支持高度被错误报告为1072
     *
     * 解决方案：
     * 通过CamcorderProfile进行二次验证，该配置文件记录设备实际支持的录制参数
     */

    // 检查常见分辨率（1080p）
    if (width == 1920 && height == 1080) {
      return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P);
    }

    // 检查4K分辨率（2160p）
    if (width == 3840 && height == 2160) {
      return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_2160P);
    }

    // 其他分辨率不进行特殊处理
    return false;
  }

  /**
   * 获取指定编码器在给定媒体类型和宽度下支持的高度范围。
   *
   * <p>该方法通过查询编码器的视频能力信息，返回在指定宽度参数下可用的高度取值范围。
   * 典型使用场景：当需要确定某个分辨率是否可用时，结合宽度参数获取垂直方向的可选范围。
   *
   * @param encoderInfo 编码器信息对象，需包含目标MIME类型的能力数据
   * @param mimeType    媒体类型（如"video/avc"），需与编码器支持的类型匹配
   * @param width       需要查询的像素宽度，必须在{@link #getSupportedResolutionRanges}返回的宽度范围内
   *
   * @return 包含支持高度区间的{@link Range}对象，区间边界值为闭区间[min, max]
   *
   * @throws IllegalArgumentException 当出现以下情况时抛出：
   *                                  1. 宽度参数不在编码器支持的宽度范围内
   *                                  2. 编码器不支持指定的MIME类型
   *                                  3. 视频能力数据不可用
   *
   * @see MediaCodecInfo.VideoCapabilities#getSupportedHeightsFor(int)
   * @see #getSupportedResolutionRanges(MediaCodecInfo, String)
   */
  public static Range<Integer> getSupportedHeights(
      MediaCodecInfo encoderInfo, String mimeType, int width) {
    return encoderInfo
        .getCapabilitiesForType(mimeType)
        .getVideoCapabilities()
        .getSupportedHeightsFor(width);
  }

  /**
   * 获取指定编码器支持的视频分辨率范围集合
   *
   * <p>该方法通过查询编码器的视频编解码能力，返回宽度和高度的有效取值范围。
   * 典型应用场景包括：
   * <ul>
   *   <li>确定编码器支持的最小/最大分辨率</li>
   *   <li>检测特定分辨率是否在支持范围内</li>
   *   <li>生成分辨率选择菜单的可选项</li>
   * </ul>
   *
   * @param encoderInfo 编码器信息对象，需包含目标MIME类型的编解码能力数据
   * @param mimeType    媒体格式类型（如"video/avc"），需与编码器支持的类型匹配
   *
   * @return 包含两个{@link Range}对象的{@link Pair}结构：
   *         <li>first:  宽度取值范围（闭区间[min, max]）</li>
   *         <li>second: 高度取值范围（闭区间[min, max]）</li>
   *
   * @throws IllegalArgumentException 当出现以下情况时抛出：
   *                                  1. 无效的MIME类型（编码器不支持）
   *                                  2. 编码器信息不包含视频能力数据
   *                                  3. 参数为null时（隐式抛出，取决于系统实现）
   *
   * @see MediaCodecInfo.VideoCapabilities#getSupportedWidths()
   * @see MediaCodecInfo.VideoCapabilities#getSupportedHeights()
   */
  public static Pair<Range<Integer>, Range<Integer>> getSupportedResolutionRanges(
      MediaCodecInfo encoderInfo, String mimeType) {
    // 获取视频编解码能力实例
    MediaCodecInfo.VideoCapabilities videoCapabilities =
        encoderInfo.getCapabilitiesForType(mimeType).getVideoCapabilities();

    // 构建分辨率范围对：宽度范围 + 高度范围
    return Pair.create(
        videoCapabilities.getSupportedWidths(),
        videoCapabilities.getSupportedHeights()
    );
  }

  /**
   * 查找并返回编码器支持的与给定分辨率最接近的分辨率。
   *
   * <p>如果输入分辨率在对齐到编码器要求后支持，则直接返回。
   *
   * <p>若不支持，则会按比例缩小分辨率，尝试多种缩减因子直到找到支持的分辨率。若所有尝试失败，
   * 则强制将分辨率限制在编码器支持的范围内，并保持宽高比（可能因对齐要求无法完全保持）。
   *
   * @param encoderInfo 编码器的媒体编解码信息
   * @param mimeType    输出视频的MIME类型（如"video/avc"）
   * @param width       原始宽度（调整前的水平像素数）
   * @param height      原始高度（调整前的垂直像素数）
   * @return 支持的分辨率对象，若无法找到则返回null
   */
  @Nullable
  public static Size getSupportedResolution(
      MediaCodecInfo encoderInfo, String mimeType, int width, int height) {

    // 获取指定MIME类型的视频编解码能力
    MediaCodecInfo.VideoCapabilities videoCapabilities =
        encoderInfo.getCapabilitiesForType(mimeType).getVideoCapabilities();

    // 获取编码器要求的宽度/高度对齐粒度（如需要16字节对齐）
    int widthAlignment = videoCapabilities.getWidthAlignment();
    int heightAlignment = videoCapabilities.getHeightAlignment();

    // 第一步：对齐原始分辨率到编码器的对齐要求
    int newWidth = alignResolution(width, widthAlignment);  // 按宽度对齐粒度对齐
    int newHeight = alignResolution(height, heightAlignment); // 按高度对齐粒度对齐

    // 检查对齐后的分辨率是否直接被支持
    if (isSizeSupported(encoderInfo, mimeType, newWidth, newHeight)) {
      return new Size(newWidth, newHeight);
    }

    // 第二步：按预设比例系数逐步缩小分辨率尝试
    float[] reductionFactors =
        new float[] { // 分辨率缩小系数数组（从95%到25%逐步尝试）
            0.95f, 0.9f, 0.85f, 0.8f, 0.75f, 0.7f, 2f / 3f, 0.6f, 0.55f, 0.5f, 0.4f, 1f / 3f, 0.25f
        };

    // 遍历所有缩小系数尝试适配
    for (float reductionFactor : reductionFactors) {
      // 计算缩小后的分辨率并再次对齐
      newWidth = alignResolution(round(width * reductionFactor), widthAlignment);
      newHeight = alignResolution(round(height * reductionFactor), heightAlignment);

      // 检查当前缩小后的分辨率是否支持
      if (isSizeSupported(encoderInfo, mimeType, newWidth, newHeight)) {
        return new Size(newWidth, newHeight);
      }
    }

    // 第三步：强制适配到编码器支持的范围
    // 获取编码器支持的最接近宽度（使用clamp函数限制在[min,max]区间）
    int supportedWidth = videoCapabilities.getSupportedWidths().clamp(width);
    // 在支持的宽度下，获取对应的最接近高度
    int adjustedHeight = videoCapabilities.getSupportedHeightsFor(supportedWidth).clamp(height);

    // 如果高度需要调整，则按比例重新计算宽度（保持宽高比）
    if (adjustedHeight != height) {
      width = alignResolution((int) round((double) width * adjustedHeight / height), widthAlignment);
      height = alignResolution(adjustedHeight, heightAlignment);
    }

    // 最终检查调整后的分辨率是否支持
    return isSizeSupported(encoderInfo, mimeType, width, height) ? new Size(width, height) : null;
  }

  /**
   * 返回指定编码器和MIME类型支持的{@linkplain MediaCodecInfo.CodecProfileLevel 编码配置集}。
   *
   * <p>该方法会从编码器的能力信息中提取所有支持的编码配置，并以不可变集合的形式返回。</p>
   *
   * @param encoderInfo 编码器信息对象，包含设备硬件编解码能力
   * @param mimeType    媒体类型标识符（如"video/avc"表示H.264）
   * @return 包含所有支持配置的不可变集合（例如H264的Baseline/Main/High Profile对应的常量值）
   */
  public static ImmutableSet<Integer> findSupportedEncodingProfiles(
      MediaCodecInfo encoderInfo, String mimeType) {
    // 获取编码器支持的配置文件和等级数组（可能包含如H264ProfileBaseline等条目）
    MediaCodecInfo.CodecProfileLevel[] profileLevels =
        encoderInfo.getCapabilitiesForType(mimeType).profileLevels;

    // 构建不可变集合的构造器（Guava库提供的类型安全集合）
    ImmutableSet.Builder<Integer> supportedProfilesBuilder = new ImmutableSet.Builder<>();

    // 遍历所有支持的配置等级对象
    for (MediaCodecInfo.CodecProfileLevel profileLevel : profileLevels) {
      // 提取具体配置标识（profile字段包含如MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline等值）
      supportedProfilesBuilder.add(profileLevel.profile);
    }

    // 构建最终不可修改的配置集合
    return supportedProfilesBuilder.build();
  }

  /**
   * 查找指定编码配置（profile）支持的最高编码等级（level）
   *
   * <p>遍历编码器支持的所有配置等级组合，找到与指定配置匹配的最高等级值</p>
   *
   * @param encoderInfo 编码器硬件信息对象（包含编解码能力数据）
   * @param mimeType    媒体类型标识符（如"video/avc"）
   * @param profile     需要查询的目标编码配置（如H264的baseline profile）
   * @return 支持的最高等级值（取自{@link MediaCodecInfo.CodecProfileLevel}），
   *         如果该配置不被支持则返回{@link #LEVEL_UNSET}
   */
  public static int findHighestSupportedEncodingLevel(
      MediaCodecInfo encoderInfo, String mimeType, int profile) {
    // TODO(b/214964116): 待合并到MediaCodecUtil工具类

    // 从编码器能力集中获取所有配置等级组合（如AVCProfileBaseline+LEVEL_3.1等）
    MediaCodecInfo.CodecProfileLevel[] profileLevels =
        encoderInfo.getCapabilitiesForType(mimeType).profileLevels;

    int maxSupportedLevel = LEVEL_UNSET; // 初始化为未设置状态

    // 遍历所有配置等级组合
    for (MediaCodecInfo.CodecProfileLevel profileLevel : profileLevels) {
      // 仅处理与目标配置匹配的条目
      if (profileLevel.profile == profile) {
        // 通过最大值比较获取最高等级（例如在多个LEVEL_2/LEVEL_3中取3）
        maxSupportedLevel = max(maxSupportedLevel, profileLevel.level);
      }
    }

    // 返回找到的最高等级或未设置状态
    return maxSupportedLevel;
  }

  /**
   * 查找支持指定媒体格式的编解码器（解码器/编码器）
   *
   * <p>该方法兼容处理Android API 21的特殊帧率参数问题，优先返回系统推荐的最佳编解码器</p>
   *
   * @param format    需要匹配的媒体格式（包含编码类型、分辨率等参数）
   * @param isDecoder true表示查找解码器，false表示查找编码器
   * @return 支持该格式的编解码器名称（如"OMX.qcom.video.decoder.avc"），未找到返回null
   */
  @Nullable
  public static String findCodecForFormat(MediaFormat format, boolean isDecoder) {
    // 创建常规编解码器列表（排除安全编解码器等特殊类型）
    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);

    // 处理Android 5.0系统帧率参数兼容性问题
    float frameRate = Format.NO_VALUE;
    if (Util.SDK_INT == 21 && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
      try {
        // 尝试读取浮点型帧率（标准方式）
        frameRate = format.getFloat(MediaFormat.KEY_FRAME_RATE);
      } catch (ClassCastException e) {
        // 处理某些设备将帧率存储为整型的异常情况
        frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
      }
      // 临时清空帧率参数（Android 5.0系统find方法不支持此参数）
      format.setString(MediaFormat.KEY_FRAME_RATE, null);
    }

    // 执行编解码器查找（系统自动匹配最合适设备）
    String mediaCodecName =
        isDecoder
            ? mediaCodecList.findDecoderForFormat(format)  // 查找解码器
            : mediaCodecList.findEncoderForFormat(format); // 查找编码器

    // Android 5.0系统恢复原始帧率参数
    if (Util.SDK_INT == 21) {
      // 将帧率四舍五入后重新设置为整型参数
      MediaFormatUtil.maybeSetInteger(format, MediaFormat.KEY_FRAME_RATE, round(frameRate));
    }
    return mediaCodecName;
  }

  /**
   * 获取指定编码器对某MIME类型支持的视频比特率范围
   *
   * <p>该方法通过查询编码器的视频编解码能力，返回其支持的比特率区间范围。
   * 区间单位为比特/秒(bps)，包含闭区间[lower, upper]边界值</p>
   *
   * @param encoderInfo 编码器信息对象（需确保支持指定MIME类型）
   * @param mimeType    媒体类型标识符（如"video/avc"）
   * @return 支持的最小/最大比特率区间（当MIME类型无效时可能抛出IllegalArgumentException）
   */
  public static Range<Integer> getSupportedBitrateRange(
      MediaCodecInfo encoderInfo, String mimeType) {
    // 链式调用获取视频能力参数：编解码类型能力 → 视频能力 → 比特率范围
    return encoderInfo.getCapabilitiesForType(mimeType).getVideoCapabilities().getBitrateRange();
  }

  /**
   * 检测编码器是否支持指定的比特率控制模式
   *
   * <p>用于验证编码器是否支持特定的码率控制策略（如CBR恒定码率/VBR可变码率等）</p>
   *
   * @param encoderInfo 编码器硬件信息（需确保支持该MIME类型）
   * @param mimeType    媒体格式类型（如"video/hevc"）
   * @param bitrateMode 需要检测的码率模式，使用{@link MediaCodecInfo.EncoderCapabilities}中的常量：
   *                    BITRATE_MODE_CQ（质量优先）、BITRATE_MODE_VBR（动态码率）、BITRATE_MODE_CBR（恒定码率）等
   * @return true表示支持该模式，false表示不支持
   * @throws IllegalArgumentException 当传入不支持的mimeType时会抛出异常
   */
  public static boolean isBitrateModeSupported(
      MediaCodecInfo encoderInfo, String mimeType, int bitrateMode) {
    return encoderInfo
        .getCapabilitiesForType(mimeType)
        .getEncoderCapabilities()
        .isBitrateModeSupported(bitrateMode);
  }

  /**
   * 获取指定编码器和MIME类型支持的颜色格式列表
   *
   * <p>颜色格式以整数常量形式表示，例如{@link MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Planar}等，
   * 完整列表参考Android文档中颜色格式定义</p>
   *
   * @param encoderInfo 编码器信息对象（需确保支持该MIME类型）
   * @param mimeType    媒体格式类型（如"video/avc"）
   * @return 包含支持颜色格式整型值的不可修改列表（可能为空列表，表示无明确颜色格式要求）
   */
  public static ImmutableList<Integer> getSupportedColorFormats(
      MediaCodecInfo encoderInfo, String mimeType) {
    return ImmutableList.copyOf(
        Ints.asList(encoderInfo.getCapabilitiesForType(mimeType).colorFormats));
  }

  /**
   * 检测编解码器是否支持硬件加速
   *
   * <p>该方法通过不同策略判断硬件加速支持情况：
   * 1. Android 10（API 29）及以上使用系统原生方法
   * 2. 低版本通过反向判断是否为纯软件实现</p>
   *
   * @param encoderInfo 编解码器信息对象（需确保支持该MIME类型）
   * @param mimeType    媒体格式类型（仅低版本需要用于兼容性判断）
   * @return true表示支持硬件加速，false表示纯软件实现
   */
  public static boolean isHardwareAccelerated(MediaCodecInfo encoderInfo, String mimeType) {
    // TODO(b/214964116): 待合并到MediaCodecUtil工具类

    // Android 10+ 使用系统原生判断方法
    if (Util.SDK_INT >= 29) {
      return Api29.isHardwareAccelerated(encoderInfo);
    }

    // 低版本逻辑：假设非纯软件实现即为硬件加速（可能存在误差）
    // 注意：系统API中isHardwareAccelerated()和isSoftwareOnly()非严格互斥
    return !isSoftwareOnly(encoderInfo, mimeType);
  }

  /**
   * 检测编码器是否支持指定的功能特性
   *
   * <p>用于验证编码器是否支持特定高级功能（如低延迟模式、HDR编码等），
   * 功能名称需参考{@link MediaCodecInfo.CodecCapabilities}中定义的FEATURE_前缀常量</p>
   *
   * @param encoderInfo 编码器信息对象（需确保支持该MIME类型）
   * @param mimeType    媒体格式类型（如"video/hevc"）
   * @param featureName 需要检测的功能名称（例如"low-latency"或FEATURE_开头的系统常量）
   * @return true表示支持该功能，false表示不支持
   * @throws IllegalArgumentException 当传入无效的mimeType时会抛出异常
   */
  public static boolean isFeatureSupported(
      MediaCodecInfo encoderInfo, String mimeType, String featureName) {
    return encoderInfo.getCapabilitiesForType(mimeType).isFeatureSupported(featureName);
  }

  /**
   * 获取编解码器支持的最大并发实例数量
   *
   * <p>该方法用于查询同一编解码器在同一设备上可同时运行的实例上限（如同时开启多个H264编码器），
   * 实际支持数量受设备硬件资源和系统限制影响</p>
   *
   * @param encoderInfo 编解码器信息对象（需确保支持该MIME类型）
   * @param mimeType    媒体格式类型（如"video/avc"）
   * @return 支持的最大并发实例数（返回1表示仅支持单实例，0或负值表示无法获取/不支持多实例）
   * @throws UnsupportedOperationException 当API级别低于23时调用会抛出异常
   */
  @RequiresApi(23) // 指定Android 6.0及以上可用
  public static int getMaxSupportedInstances(MediaCodecInfo encoderInfo, String mimeType) {
    // 通过编解码能力对象获取最大实例数（Android原生API）
    return encoderInfo.getCapabilitiesForType(mimeType).getMaxSupportedInstances();
  }

  /**
   * 判断编解码器是否为纯软件实现（无硬件加速）
   *
   * <p>实现策略：
   * 1. Android 10+ 使用系统原生API判断
   * 2. 低版本通过编解码器名称模式推断</p>
   *
   * @param encoderInfo 编解码器信息对象
   * @param mimeType    媒体格式类型（用于音频类型特殊处理）
   * @return true表示纯软件实现，false表示可能包含硬件加速
   */
  private static boolean isSoftwareOnly(MediaCodecInfo encoderInfo, String mimeType) {
    // Android 10+ 使用原生API判断
    if (Util.SDK_INT >= 29) {
      return Api29.isSoftwareOnly(encoderInfo);
    }

    // 音频编解码器默认视为软件实现（系统API行为模拟）
    if (MimeTypes.isAudio(mimeType)) {
      return true; // 假设音频解码器均为软件实现
    }

    // 统一转为小写避免大小写敏感问题
    String codecName = Ascii.toLowerCase(encoderInfo.getName());

    // 特殊硬件编解码器例外处理
    if (codecName.startsWith("arc.")) {
      return false; // Chrome OS的ARC虚拟机硬件编解码器
    }

    /* 软件编解码器名称特征匹配（常见模式）：
     * 1. Google软件实现：omx.google.*（如OMX.google.h264.decoder）
     * 2. FFmpeg软件解码器：omx.ffmpeg.*
     * 3. 三星软件实现：omx.sec.*.sw.*（如OMX.SEC.x264.sw.decoder）
     * 4. 高通特殊软件HEVC解码器
     * 5. Codec2软件组件：c2.android.* / c2.google.*
     * 6. 非标准前缀名称（非omx/c2开头视为软件实现）
     */
    return codecName.startsWith("omx.google.")
        || codecName.startsWith("omx.ffmpeg.")
        || (codecName.startsWith("omx.sec.") && codecName.contains(".sw."))
        || codecName.equals("omx.qcom.video.decoder.hevcswvdec") // 高通软件HEVC解码器
        || codecName.startsWith("c2.android.")    // Codec2软件组件
        || codecName.startsWith("c2.google.")     // Codec2软件组件
        || (!codecName.startsWith("omx.") && !codecName.startsWith("c2.")); // 非标准前缀
  }

  /**
   * 将分辨率对齐到最接近且符合编码器对齐要求的尺寸
   *
   * <p>特殊处理规则：
   * 1. 当原始尺寸末位为1时（如1081），强制向下取整对齐（避免向上对齐导致分辨率突增）
   * 2. 其他情况采用四舍五入对齐（如45对齐到48）
   *
   * @param size      需要对齐的原始尺寸（宽度/高度）
   * @param alignment 编码器要求的对齐粒度（如16表示需要16的倍数）
   * @return 对齐后的尺寸值（满足 size % alignment == 0）
   */
  private static int alignResolution(int size, int alignment) {
    // 特殊场景处理：当尺寸末位为1时（如1081px），采用向下取整策略
    boolean shouldRoundDown = false;
    if (size % 10 == 1) { // 如：1081 % 10 = 1 → 触发向下对齐
      shouldRoundDown = true;
    }

    // 根据策略选择对齐方式
    return shouldRoundDown
        ? (int) (alignment * floor((float) size / alignment))  // 向下取整对齐（如1081→1080）
        : alignment * round((float) size / alignment);        // 四舍五入对齐（如45→48）
  }

  /**
   * 初始化编码器信息映射表（延迟加载且线程安全）
   *
   * <p>该方法通过单次扫描系统编解码器列表，构建MIME类型到编码器的映射关系。
   * 使用synchronized关键字确保多线程环境下只执行一次初始化操作</p>
   */
  private static synchronized void maybePopulateEncoderInfo() {
    // 检查是否已完成初始化（避免重复扫描编解码器列表）
    if (!mimeTypeToEncoders.isEmpty()) {
      return;
    }

    // 创建常规编解码器列表（排除安全编解码器等特殊类型）
    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
    // 获取系统中所有编解码器信息（包含编码器与解码器）
    MediaCodecInfo[] allCodecInfos = mediaCodecList.getCodecInfos();

    // 遍历所有编解码器进行筛选处理
    for (MediaCodecInfo mediaCodecInfo : allCodecInfos) {
      // 跳过解码器，仅处理编码器
      if (!mediaCodecInfo.isEncoder()) {
        continue;
      }

      // 获取当前编码器支持的所有MIME类型（如"video/avc", "audio/mp4a-latm"）
      String[] supportedMimeTypes = mediaCodecInfo.getSupportedTypes();

      // 将每个MIME类型转换为小写后存入映射表
      for (String mimeType : supportedMimeTypes) {
        // 使用小写格式避免大小写敏感问题（如"Video/AVC"转为"video/avc"）
        // 注意：如果多个编码器支持同一MIME类型，后扫描的会覆盖之前的
        mimeTypeToEncoders.put(Ascii.toLowerCase(mimeType), mediaCodecInfo);
      }
    }
  }

  /**
   * Android 10（API 29）及以上版本专用功能封装类
   *
   * <p>此类通过版本隔离机制，为高版本系统API提供兼容性封装，
   * 避免低版本设备触发{@link NoSuchMethodError}等异常</p>
   */
  @RequiresApi(29) // 限定仅Android 10+设备可调用
  private static final class Api29 {

    /**
     * 系统原生硬件加速检测方法封装
     *
     * @param encoderInfo 编解码器信息对象
     * @return 设备是否声明该编解码器使用硬件加速（如GPU/DSP等专用芯片）
     */
    public static boolean isHardwareAccelerated(MediaCodecInfo encoderInfo) {
      return encoderInfo.isHardwareAccelerated(); // 调用Android 10+原生API
    }

    /**
     * 系统原生纯软件实现检测方法封装
     *
     * @param encoderInfo 编解码器信息对象
     * @return 设备是否声明该编解码器为纯软件实现（无硬件模块参与）
     */
    public static boolean isSoftwareOnly(MediaCodecInfo encoderInfo) {
      return encoderInfo.isSoftwareOnly(); // 调用Android 10+原生API
    }
  }

  private EncoderUtil() {}
}
