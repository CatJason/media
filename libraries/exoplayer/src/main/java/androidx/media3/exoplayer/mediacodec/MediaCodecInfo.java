/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.exoplayer.mediacodec;

import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_AUDIO_CHANNEL_COUNT_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_AUDIO_ENCODING_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_AUDIO_SAMPLE_RATE_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_INITIALIZATION_DATA_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_MIME_TYPE_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_VIDEO_COLOR_INFO_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_VIDEO_RESOLUTION_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_VIDEO_ROTATION_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_WORKAROUND;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_NO;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_YES_WITH_FLUSH;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_YES_WITH_RECONFIGURATION;
import static androidx.media3.exoplayer.mediacodec.MediaCodecPerformancePointCoverageProvider.COVERAGE_RESULT_NO;
import static androidx.media3.exoplayer.mediacodec.MediaCodecPerformancePointCoverageProvider.COVERAGE_RESULT_YES;

import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.AudioCapabilities;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.DecoderReuseEvaluation.DecoderDiscardReasons;

/** 关于 {@link MediaCodec} 对给定 MIME 类型的信息。 */
@SuppressWarnings("InlinedApi")
@UnstableApi
public final class MediaCodecInfo {

  public static final String TAG = "MediaCodecInfo";

  /**
   * 如果支持的最大实例数的上限未知，则 {@link #getMaxSupportedInstances()} 返回的值。
   */
  public static final int MAX_SUPPORTED_INSTANCES_UNKNOWN = -1;

  /**
   * 解码器的名称。
   *
   * <p>可以传递给 {@link MediaCodec#createByCodecName(String)} 以创建解码器实例。
   */
  public final String name;

  /** 编解码器处理的 MIME 类型。 */
  public final String mimeType;

  /**
   * 编解码器用于 {@link #mimeType} 类型媒体的 MIME 类型。除非编解码器已知使用非标准的 MIME 类型别名，否则与 {@link #mimeType} 相同。
   */
  public final String codecMimeType;

  /**
   * 解码器的能力，例如其支持的 Profile/Level，如果未知则为 {@code null}。
   */
  @Nullable public final CodecCapabilities capabilities;

  /**
   * 解码器是否支持无缝分辨率切换。
   *
   * @see CodecCapabilities#isFeatureSupported(String)
   * @see CodecCapabilities#FEATURE_AdaptivePlayback
   */
  public final boolean adaptive;

  /**
   * 解码器是否支持隧道模式。
   *
   * @see CodecCapabilities#isFeatureSupported(String)
   * @see CodecCapabilities#FEATURE_TunneledPlayback
   */
  public final boolean tunneling;

  /**
   * 解码器是否为安全解码器。
   *
   * @see CodecCapabilities#isFeatureSupported(String)
   * @see CodecCapabilities#FEATURE_SecurePlayback
   */
  public final boolean secure;

  /**
   * 编解码器是否为硬件加速。
   *
   * <p>这可能是一个近似值，因为确切信息仅在 API 29 及以上版本中提供。
   *
   * @see android.media.MediaCodecInfo#isHardwareAccelerated()
   */
  public final boolean hardwareAccelerated;

  /**
   * 编解码器是否仅为软件实现。
   *
   * <p>这可能是一个近似值，因为确切信息仅在 API 29 及以上版本中提供。
   *
   * @see android.media.MediaCodecInfo#isSoftwareOnly()
   */
  public final boolean softwareOnly;

  /**
   * 编解码器是否由供应商提供。
   *
   * <p>这可能是一个近似值，因为确切信息仅在 API 29 及以上版本中提供。
   *
   * @see android.media.MediaCodecInfo#isVendor()
   */
  public final boolean vendor;

  /**
   * 编解码器是否支持“分离”表面模式，即能够在没有附加表面的情况下进行解码。仅与视频编解码器相关。
   *
   * @see android.media.MediaCodecInfo.CodecCapabilities#FEATURE_DetachedSurface
   */
  public final boolean detachedSurfaceSupported;

  private final boolean isVideo;

  /**
   * 创建一个实例。
   *
   * @param name {@link MediaCodec} 的名称。
   * @param mimeType {@link MediaCodec} 支持的 MIME 类型。
   * @param codecMimeType 编解码器用于 {@code #mimeType} 类型媒体的 MIME 类型。除非编解码器已知使用非标准的 MIME 类型别名，否则与 {@code mimeType} 相同。
   * @param capabilities {@link MediaCodec} 对指定 MIME 类型的能力，如果未知则为 {@code null}。
   * @param hardwareAccelerated {@link MediaCodec} 是否为硬件加速。
   * @param softwareOnly {@link MediaCodec} 是否仅为软件实现。
   * @param vendor {@link MediaCodec} 是否由供应商提供。
   * @param forceDisableAdaptive 是否应强制将 {@link #adaptive} 设置为 {@code false}。
   * @param forceSecure 是否应强制将 {@link #secure} 设置为 {@code true}。
   * @return 创建的实例。
   */
  public static MediaCodecInfo newInstance(
      String name,
      String mimeType,
      String codecMimeType,
      @Nullable CodecCapabilities capabilities,
      boolean hardwareAccelerated,
      boolean softwareOnly,
      boolean vendor,
      boolean forceDisableAdaptive,
      boolean forceSecure) {
    return new MediaCodecInfo(
        name,
        mimeType,
        codecMimeType,
        capabilities,
        hardwareAccelerated,
        softwareOnly,
        vendor,
        /* adaptive= */ !forceDisableAdaptive
            && capabilities != null
            && isAdaptive(capabilities)
            && !needsDisableAdaptationWorkaround(name),
        /* tunneling= */ capabilities != null && isTunneling(capabilities),
        /* secure= */ forceSecure || (capabilities != null && isSecure(capabilities)),
        isDetachedSurfaceSupported(capabilities));
  }

  @VisibleForTesting
  /* package */ MediaCodecInfo(
      String name,
      String mimeType,
      String codecMimeType,
      @Nullable CodecCapabilities capabilities,
      boolean hardwareAccelerated,
      boolean softwareOnly,
      boolean vendor,
      boolean adaptive,
      boolean tunneling,
      boolean secure,
      boolean detachedSurfaceSupported) {
    this.name = Assertions.checkNotNull(name);
    this.mimeType = mimeType;
    this.codecMimeType = codecMimeType;
    this.capabilities = capabilities;
    this.hardwareAccelerated = hardwareAccelerated;
    this.softwareOnly = softwareOnly;
    this.vendor = vendor;
    this.adaptive = adaptive;
    this.tunneling = tunneling;
    this.secure = secure;
    this.detachedSurfaceSupported = detachedSurfaceSupported;
    isVideo = MimeTypes.isVideo(mimeType);
  }

  @Override
  public String toString() {
    return name;
  }

  /**
   * 返回解码器支持的 Profile 和 Level 列表。
   *
   * @return 解码器支持的 Profile 和 Level 列表。
   */
  public CodecProfileLevel[] getProfileLevels() {
    return capabilities == null || capabilities.profileLevels == null // 如果编解码器能力为空或其 Profile 和 Level 列表为空
        ? new CodecProfileLevel[0] // 返回空数组
        : capabilities.profileLevels; // 否则返回 Profile 和 Level 列表
  }

  /**
   * 返回支持的最大实例数的上限，如果未知则返回 {@link #MAX_SUPPORTED_INSTANCES_UNKNOWN}。应用程序不应期望操作超过返回的最大实例数。
   *
   * @see CodecCapabilities#getMaxSupportedInstances()
   */
  public int getMaxSupportedInstances() {
    if (Util.SDK_INT < 23 || capabilities == null) {
      return MAX_SUPPORTED_INSTANCES_UNKNOWN;
    }
    return getMaxSupportedInstancesV23(capabilities);
  }

  /**
   * 返回解码器是否可能在功能上和性能上支持解码给定的 {@code format}。
   *
   * @param format 输入媒体格式。
   * @return 解码器是否可能支持解码给定的 {@code format}。
   * @throws MediaCodecUtil.DecoderQueryException 如果查询解码器时发生错误，则抛出此异常。
   */
  public boolean isFormatSupported(Format format) throws MediaCodecUtil.DecoderQueryException {
    if (!isSampleMimeTypeSupported(format)) {
      return false;
    }

    if (!isCodecProfileAndLevelSupported(format, /* checkPerformanceCapabilities= */ true)) {
      return false;
    }

    if (isVideo) {
      if (format.width <= 0 || format.height <= 0) {
        return true;
      }
      return isVideoSizeAndRateSupportedV21(format.width, format.height, format.frameRate);
    } else { // Audio
      return (format.sampleRate == Format.NO_VALUE
              || isAudioSampleRateSupportedV21(format.sampleRate))
          && (format.channelCount == Format.NO_VALUE
              || isAudioChannelCountSupportedV21(format.channelCount));
    }
  }

  /**
   * 返回解码器是否可能在功能上支持解码给定的 {@code format}。
   *
   * @param format 输入媒体格式。
   * @return 解码器是否可能在功能上支持解码给定的 {@code format}。
   */
  public boolean isFormatFunctionallySupported(Format format) {
    return isSampleMimeTypeSupported(format)
        && isCodecProfileAndLevelSupported(format, /* checkPerformanceCapabilities= */ false);
  }

  private boolean isSampleMimeTypeSupported(Format format) {
    return mimeType.equals(format.sampleMimeType)
        || mimeType.equals(MediaCodecUtil.getAlternativeCodecMimeType(format));
  }

  private boolean isCodecProfileAndLevelSupported(
      Format format, boolean checkPerformanceCapabilities) {
    Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(format); // 获取编解码器的 Profile 和 Level
    if (format.sampleMimeType != null && format.sampleMimeType.equals(MimeTypes.VIDEO_MV_HEVC)) { // 如果格式是 MV-HEVC
      String normalizedCodecMimeType = MimeTypes.normalizeMimeType(codecMimeType); // 获取标准化的 MIME 类型
      if (normalizedCodecMimeType.equals(MimeTypes.VIDEO_MV_HEVC)) { // 如果编解码器支持 MV-HEVC
        // 目前 Android 框架中没有正式支持 MV-HEVC，底层编解码器未正确指定 Profile；假设从 MV-HEVC 样本中获取的 Profile 受支持。
        return true;
      } else if (normalizedCodecMimeType.equals(MimeTypes.VIDEO_H265)) { // 如果编解码器支持单层 HEVC
        // 从 MV-HEVC 回退到单层 HEVC。获取基础层的 Profile 和 Level。
        codecProfileAndLevel = MediaCodecUtil.getHevcBaseLayerCodecProfileAndLevel(format);
      }
    }

    if (codecProfileAndLevel == null) { // 如果无法获取 Profile 和 Level
      // 如果不了解更多，我们假设 Profile 和 Level 受支持。
      return true;
    }
    int profile = codecProfileAndLevel.first; // 获取 Profile
    int level = codecProfileAndLevel.second; // 获取 Level
    if (MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)) { // 如果格式是 Dolby Vision
      // 如果此编解码器是 H264 或 H265，我们仅支持 Dolby Vision 基础层，并将 Dolby Vision Profile 映射到相应的基础层 Profile。同时假设此基础层 Profile 的所有 Level 都受支持。
      if (MimeTypes.VIDEO_H264.equals(mimeType)) { // 如果是 H264
        profile = CodecProfileLevel.AVCProfileHigh; // 设置为 H264 High Profile
        level = 0; // Level 设置为 0
      } else if (MimeTypes.VIDEO_H265.equals(mimeType)) { // 如果是 H265
        profile = CodecProfileLevel.HEVCProfileMain10; // 设置为 H265 Main10 Profile
        level = 0; // Level 设置为 0
      }
    }

    if (!isVideo && profile != CodecProfileLevel.AACObjectXHE) { // 如果不是视频且 Profile 不是 xHE-AAC
      // 某些设备/构建版本低估了音频能力，因此假设支持，但 xHE-AAC 可能不被广泛支持。参见 https://github.com/google/ExoPlayer/issues/5145。
      return true;
    }

    CodecProfileLevel[] profileLevels = getProfileLevels(); // 获取编解码器的 Profile 和 Level 列表
    if (Util.SDK_INT <= 23 && MimeTypes.VIDEO_VP9.equals(mimeType) && profileLevels.length == 0) { // 如果 API 版本 <= 23 且格式是 VP9 且 ProfileLevels 为空
      // 某些旧设备未报告 VP9 的 Profile 和 Level。使用编解码器能力中的其他数据估算它们。
      profileLevels = estimateLegacyVp9ProfileLevels(capabilities); // 估算 VP9 的 Profile 和 Level
    }

    for (CodecProfileLevel profileLevel : profileLevels) { // 遍历 Profile 和 Level 列表
      if (profileLevel.profile == profile
          && (profileLevel.level >= level || !checkPerformanceCapabilities) // 如果 Profile 匹配且 Level 满足要求或不需要检查性能能力
          && !needsProfileExcludedWorkaround(mimeType, profile)) { // 且不需要排除特定 Profile 的解决方案
        return true;
      }
    }
    logNoSupport("codec.profileLevel, " + format.codecs + ", " + codecMimeType); // 记录不支持的原因
    return false;
  }

  /** 编解码器是否支持 HDR10+ 带外元数据。 */
  public boolean isHdr10PlusOutOfBandMetadataSupported() {
    if (Util.SDK_INT >= 29 && MimeTypes.VIDEO_VP9.equals(mimeType)) { // 如果 API 版本 >= 29 且 MIME 类型是 VP9
      for (CodecProfileLevel capabilities : getProfileLevels()) { // 遍历编解码器的 Profile 和 Level 列表
        if (capabilities.profile == CodecProfileLevel.VP9Profile2HDR10Plus) { // 如果 Profile 是 VP9Profile2HDR10Plus
          return true; // 返回支持
        }
      }
    }
    return false; // 否则返回不支持
  }

  /**
   * 返回当编解码器配置为播放指定 {@code format} 格式的媒体时，是否可以适应此解码器实例以播放不同的格式。
   *
   * <p>要使适应成功，编解码器还必须配置适当的最大值，并且 {@link #canReuseCodec(Format, Format)} 必须为旧/新格式返回 {@code true}。
   *
   * @param format 编解码器将配置的媒体格式。
   * @return 是否可能实现无缝适应
   */
  public boolean isSeamlessAdaptationSupported(Format format) {
    if (isVideo) { // 如果是视频
      return adaptive; // 返回是否支持自适应
    } else { // 如果是音频
      Pair<Integer, Integer> profileLevel = MediaCodecUtil.getCodecProfileAndLevel(format); // 获取编解码器的 Profile 和 Level
      return profileLevel != null && profileLevel.first == CodecProfileLevel.AACObjectXHE; // 如果 Profile 是 AACObjectXHE，则返回支持
    }
  }

  /**
   * 评估是否可以重用当前正在解码 {@code oldFormat} 的此解码器实例来解码 {@code newFormat}。
   *
   * <p>要使适应成功，编解码器还必须配置与新格式兼容的最大值。
   *
   * @param oldFormat 当前正在解码的格式。
   * @param newFormat 新的格式。
   * @return 评估结果。
   */
  public DecoderReuseEvaluation canReuseCodec(Format oldFormat, Format newFormat) {
    @DecoderDiscardReasons int discardReasons = 0; // 初始化丢弃原因
    if (!Util.areEqual(oldFormat.sampleMimeType, newFormat.sampleMimeType)) { // 如果 MIME 类型不同
      discardReasons |= DISCARD_REASON_MIME_TYPE_CHANGED; // 添加 MIME 类型更改的丢弃原因
    }

    if (isVideo) { // 如果是视频
      if (oldFormat.rotationDegrees != newFormat.rotationDegrees) { // 如果旋转角度不同
        discardReasons |= DISCARD_REASON_VIDEO_ROTATION_CHANGED; // 添加视频旋转角度更改的丢弃原因
      }
      if (!adaptive
          && (oldFormat.width != newFormat.width || oldFormat.height != newFormat.height)) { // 如果不支持自适应且分辨率不同
        discardReasons |= DISCARD_REASON_VIDEO_RESOLUTION_CHANGED; // 添加视频分辨率更改的丢弃原因
      }
      if ((!ColorInfo.isEquivalentToAssumedSdrDefault(oldFormat.colorInfo)
          || !ColorInfo.isEquivalentToAssumedSdrDefault(newFormat.colorInfo))
          && !Util.areEqual(oldFormat.colorInfo, newFormat.colorInfo)) { // 如果颜色信息不同且不处于默认 SDR 假设范围内
        // 如果两个 ColorInfo 都符合默认 SDR 假设，则不进行详细检查。
        discardReasons |= DISCARD_REASON_VIDEO_COLOR_INFO_CHANGED; // 添加视频颜色信息更改的丢弃原因
      }
      if (needsAdaptationReconfigureWorkaround(name)
          && !oldFormat.initializationDataEquals(newFormat)) { // 如果需要适应重新配置的解决方案且初始化数据不同
        discardReasons |= DISCARD_REASON_WORKAROUND; // 添加解决方案相关的丢弃原因
      }

      if (discardReasons == 0) { // 如果没有丢弃原因
        return new DecoderReuseEvaluation(
            name,
            oldFormat,
            newFormat,
            oldFormat.initializationDataEquals(newFormat)
                ? REUSE_RESULT_YES_WITHOUT_RECONFIGURATION // 如果初始化数据相同，返回无需重新配置
                : REUSE_RESULT_YES_WITH_RECONFIGURATION, // 否则返回需要重新配置
            /* discardReasons= */ 0);
      }
    } else { // 如果是音频
      if (oldFormat.channelCount != newFormat.channelCount) { // 如果声道数不同
        discardReasons |= DISCARD_REASON_AUDIO_CHANNEL_COUNT_CHANGED; // 添加音频声道数更改的丢弃原因
      }
      if (oldFormat.sampleRate != newFormat.sampleRate) { // 如果采样率不同
        discardReasons |= DISCARD_REASON_AUDIO_SAMPLE_RATE_CHANGED; // 添加音频采样率更改的丢弃原因
      }
      if (oldFormat.pcmEncoding != newFormat.pcmEncoding) { // 如果 PCM 编码不同
        discardReasons |= DISCARD_REASON_AUDIO_ENCODING_CHANGED; // 添加音频编码更改的丢弃原因
      }

      // 检查是否在两个 xHE-AAC 格式之间适应，对于 xHE-AAC，适应是可能的，无需重新配置或刷新。
      if (discardReasons == 0 && MimeTypes.AUDIO_AAC.equals(mimeType)) { // 如果没有丢弃原因且 MIME 类型是 AAC
        @Nullable
        Pair<Integer, Integer> oldCodecProfileLevel =
            MediaCodecUtil.getCodecProfileAndLevel(oldFormat); // 获取旧格式的 Profile 和 Level
        @Nullable
        Pair<Integer, Integer> newCodecProfileLevel =
            MediaCodecUtil.getCodecProfileAndLevel(newFormat); // 获取新格式的 Profile 和 Level
        if (oldCodecProfileLevel != null && newCodecProfileLevel != null) { // 如果两者都不为空
          int oldProfile = oldCodecProfileLevel.first; // 获取旧格式的 Profile
          int newProfile = newCodecProfileLevel.first; // 获取新格式的 Profile
          if (oldProfile == CodecProfileLevel.AACObjectXHE
              && newProfile == CodecProfileLevel.AACObjectXHE) { // 如果两者都是 xHE-AAC
            return new DecoderReuseEvaluation(
                name,
                oldFormat,
                newFormat,
                REUSE_RESULT_YES_WITHOUT_RECONFIGURATION, // 返回无需重新配置
                /* discardReasons= */ 0);
          }
        }
      }

      if (!oldFormat.initializationDataEquals(newFormat)) { // 如果初始化数据不同
        discardReasons |= DISCARD_REASON_INITIALIZATION_DATA_CHANGED; // 添加初始化数据更改的丢弃原因
      }
      if (needsAdaptationFlushWorkaround(mimeType)) { // 如果需要适应刷新的解决方案
        discardReasons |= DISCARD_REASON_WORKAROUND; // 添加解决方案相关的丢弃原因
      }

      if (discardReasons == 0) { // 如果没有丢弃原因
        return new DecoderReuseEvaluation(
            name, oldFormat, newFormat, REUSE_RESULT_YES_WITH_FLUSH, /* discardReasons= */ 0); // 返回需要刷新
      }
    }

    return new DecoderReuseEvaluation(name, oldFormat, newFormat, REUSE_RESULT_NO, discardReasons); // 返回不可重用
  }

  /**
   * 解码器是否支持给定宽度、高度和帧率的视频。
   *
   * @param width 宽度（以像素为单位）。
   * @param height 高度（以像素为单位）。
   * @param frameRate 可选的帧率（以帧/秒为单位）。如果设置为 {@link Format#NO_VALUE} 或任何小于或等于 0 的值，则忽略。
   * @return 解码器是否支持给定宽度、高度和帧率的视频。
   */
  public boolean isVideoSizeAndRateSupportedV21(int width, int height, double frameRate) {
    if (capabilities == null) { // 如果编解码器能力为空
      logNoSupport("sizeAndRate.caps"); // 记录不支持的原因
      return false; // 返回不支持
    }
    VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities(); // 获取视频能力
    if (videoCapabilities == null) { // 如果视频能力为空
      logNoSupport("sizeAndRate.vCaps"); // 记录不支持的原因
      return false; // 返回不支持
    }

    if (Util.SDK_INT >= 29) { // 如果 API 版本 >= 29
      @MediaCodecPerformancePointCoverageProvider.PerformancePointCoverageResult
      int evaluation =
          MediaCodecPerformancePointCoverageProvider.areResolutionAndFrameRateCovered(
              videoCapabilities, width, height, frameRate); // 评估分辨率和帧率是否被覆盖
      if (evaluation == COVERAGE_RESULT_YES) { // 如果覆盖结果为支持
        return true; // 返回支持
      } else if (evaluation == COVERAGE_RESULT_NO) { // 如果覆盖结果为不支持
        logNoSupport("sizeAndRate.cover, " + width + "x" + height + "@" + frameRate); // 记录不支持的原因
        return false; // 返回不支持
      }
      // 如果覆盖结果为 COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED，则继续执行下面的逻辑
    }

    if (!areSizeAndRateSupported(videoCapabilities, width, height, frameRate)) { // 如果视频能力不支持给定的宽度、高度和帧率
      if (width >= height
          || !needsRotatedVerticalResolutionWorkaround(name)
          || !areSizeAndRateSupported(videoCapabilities, height, width, frameRate)) { // 如果不需要旋转分辨率解决方案或旋转后仍不支持
        logNoSupport("sizeAndRate.support, " + width + "x" + height + "@" + frameRate); // 记录不支持的原因
        return false; // 返回不支持
      }
      logAssumedSupport("sizeAndRate.rotated, " + width + "x" + height + "@" + frameRate); // 记录假设支持的原因
    }
    return true; // 返回支持
  }

  /**
   * 返回大于或等于指定尺寸的最小视频尺寸，同时满足 {@link MediaCodec} 的宽度和高度对齐要求。
   *
   * @param width 宽度（以像素为单位）。
   * @param height 高度（以像素为单位）。
   * @return 返回大于或等于指定尺寸的最小视频尺寸，同时满足 {@link MediaCodec} 的宽度和高度对齐要求；如果不是视频编解码器，则返回 null。
   */
  @Nullable
  public Point alignVideoSizeV21(int width, int height) {
    if (capabilities == null) { // 如果编解码器能力为空
      return null; // 返回 null
    }
    VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities(); // 获取视频能力
    if (videoCapabilities == null) { // 如果视频能力为空
      return null; // 返回 null
    }
    return alignVideoSize(videoCapabilities, width, height); // 返回对齐后的视频尺寸
  }

  /**
   * 解码器是否支持给定采样率的音频。
   *
   * @param sampleRate 采样率（以 Hz 为单位）。
   * @return 解码器是否支持给定采样率的音频。
   */
  public boolean isAudioSampleRateSupportedV21(int sampleRate) {
    if (capabilities == null) { // 如果编解码器能力为空
      logNoSupport("sampleRate.caps"); // 记录不支持的原因
      return false; // 返回不支持
    }
    AudioCapabilities audioCapabilities = capabilities.getAudioCapabilities(); // 获取音频能力
    if (audioCapabilities == null) { // 如果音频能力为空
      logNoSupport("sampleRate.aCaps"); // 记录不支持的原因
      return false; // 返回不支持
    }
    if (!audioCapabilities.isSampleRateSupported(sampleRate)) { // 如果音频能力不支持给定的采样率
      logNoSupport("sampleRate.support, " + sampleRate); // 记录不支持的原因
      return false; // 返回不支持
    }
    return true; // 返回支持
  }

  /**
   * 解码器是否支持给定声道数的音频。
   *
   * @param channelCount 声道数。
   * @return 解码器是否支持给定声道数的音频。
   */
  public boolean isAudioChannelCountSupportedV21(int channelCount) {
    if (capabilities == null) { // 如果编解码器能力为空
      logNoSupport("channelCount.caps"); // 记录不支持的原因
      return false; // 返回不支持
    }
    AudioCapabilities audioCapabilities = capabilities.getAudioCapabilities(); // 获取音频能力
    if (audioCapabilities == null) { // 如果音频能力为空
      logNoSupport("channelCount.aCaps"); // 记录不支持的原因
      return false; // 返回不支持
    }
    int maxInputChannelCount =
        adjustMaxInputChannelCount(name, mimeType, audioCapabilities.getMaxInputChannelCount()); // 调整最大输入声道数
    if (maxInputChannelCount < channelCount) { // 如果最大输入声道数小于给定声道数
      logNoSupport("channelCount.support, " + channelCount); // 记录不支持的原因
      return false; // 返回不支持
    }
    return true; // 返回支持
  }

  private void logNoSupport(String message) {
    Log.d(
        TAG,
        "NoSupport ["
            + message
            + "] ["
            + name
            + ", "
            + mimeType
            + "] ["
            + Util.DEVICE_DEBUG_INFO
            + "]");
  }

  private void logAssumedSupport(String message) {
    Log.d(
        TAG,
        "AssumedSupport ["
            + message
            + "] ["
            + name
            + ", "
            + mimeType
            + "] ["
            + Util.DEVICE_DEBUG_INFO
            + "]");
  }

  private static int adjustMaxInputChannelCount(String name, String mimeType, int maxChannelCount) {
    if (maxChannelCount > 1 || (Util.SDK_INT >= 26 && maxChannelCount > 0)) { // 如果最大声道数大于 1，或者 API 版本 >= 26 且最大声道数大于 0
      // 最大声道数看起来已经正确设置。
      return maxChannelCount; // 返回最大声道数
    }
    if (MimeTypes.AUDIO_MPEG.equals(mimeType) // 如果 MIME 类型是 MPEG
        || MimeTypes.AUDIO_AMR_NB.equals(mimeType) // 或者 AMR-NB
        || MimeTypes.AUDIO_AMR_WB.equals(mimeType) // 或者 AMR-WB
        || MimeTypes.AUDIO_AAC.equals(mimeType) // 或者 AAC
        || MimeTypes.AUDIO_VORBIS.equals(mimeType) // 或者 Vorbis
        || MimeTypes.AUDIO_OPUS.equals(mimeType) // 或者 Opus
        || MimeTypes.AUDIO_RAW.equals(mimeType) // 或者 RAW
        || MimeTypes.AUDIO_FLAC.equals(mimeType) // 或者 FLAC
        || MimeTypes.AUDIO_ALAW.equals(mimeType) // 或者 ALAW
        || MimeTypes.AUDIO_MLAW.equals(mimeType) // 或者 MLAW
        || MimeTypes.AUDIO_MSGSM.equals(mimeType)) { // 或者 MSGSM
      // 平台代码应该已经设置了默认值。
      return maxChannelCount; // 返回最大声道数
    }
    // 最大声道数看起来不正确。将其调整为假设的默认值。
    int assumedMaxChannelCount;
    if (MimeTypes.AUDIO_AC3.equals(mimeType)) { // 如果 MIME 类型是 AC3
      assumedMaxChannelCount = 6; // 假设最大声道数为 6
    } else if (MimeTypes.AUDIO_E_AC3.equals(mimeType)) { // 如果 MIME 类型是 E-AC3
      assumedMaxChannelCount = 16; // 假设最大声道数为 16
    } else { // 其他情况
      // 默认为平台限制，即 30。
      assumedMaxChannelCount = 30; // 假设最大声道数为 30
    }
    Log.w(
        TAG,
        "AssumedMaxChannelAdjustment: "
            + name
            + ", ["
            + maxChannelCount
            + " to "
            + assumedMaxChannelCount
            + "]"); // 记录警告日志
    return assumedMaxChannelCount; // 返回假设的最大声道数
  }

  private static boolean isAdaptive(CodecCapabilities capabilities) {
    return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback);
  }

  private static boolean isTunneling(CodecCapabilities capabilities) {
    return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_TunneledPlayback);
  }

  private static boolean isSecure(CodecCapabilities capabilities) {
    return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_SecurePlayback);
  }

  private static boolean isDetachedSurfaceSupported(@Nullable CodecCapabilities capabilities) {
    return Util.SDK_INT >= 35
        && capabilities != null
        && capabilities.isFeatureSupported(CodecCapabilities.FEATURE_DetachedSurface);
  }

  private static boolean areSizeAndRateSupported(
      VideoCapabilities capabilities, int width, int height, double frameRate) {
    // 永远不要因为对齐问题而失败。参见：https://github.com/google/ExoPlayer/issues/6551。
    Point alignedSize = alignVideoSize(capabilities, width, height); // 对齐视频尺寸
    width = alignedSize.x; // 获取对齐后的宽度
    height = alignedSize.y; // 获取对齐后的高度

    // VideoCapabilities.areSizeAndRateSupported 在某些 Android 版本上，如果 frameRate < 1，会错误地返回 false，
    // 因此在这种情况下我们只检查尺寸 [Internal ref: b/153940404]。
    if (frameRate == Format.NO_VALUE || frameRate < 1) { // 如果帧率为 Format.NO_VALUE 或小于 1
      return capabilities.isSizeSupported(width, height); // 只检查尺寸是否支持
    } else { // 否则
      // 信号的帧率可能略高于实际帧率，因此我们取 floor 值，以避免由于略微超出标准格式的限制（例如，1080p 30 fps）而导致 areSizeAndRateSupported 检查失败。
      double floorFrameRate = Math.floor(frameRate); // 取帧率的 floor 值
      return capabilities.areSizeAndRateSupported(width, height, floorFrameRate); // 检查尺寸和帧率是否支持
    }
  }

  private static Point alignVideoSize(VideoCapabilities capabilities, int width, int height) {
    int widthAlignment = capabilities.getWidthAlignment();
    int heightAlignment = capabilities.getHeightAlignment();
    return new Point(
        Util.ceilDivide(width, widthAlignment) * widthAlignment,
        Util.ceilDivide(height, heightAlignment) * heightAlignment);
  }

  @RequiresApi(23)
  private static int getMaxSupportedInstancesV23(CodecCapabilities capabilities) {
    return capabilities.getMaxSupportedInstances();
  }

  /**
   * 在 {@link Util#SDK_INT} 为 23 及以下的设备上调用，用于处理 {@link CodecCapabilities} 未正确报告 Profile 和 Level 的 VP9 解码器。
   * 返回的 {@link CodecProfileLevel CodecProfileLevels} 是基于 {@link CodecCapabilities} 中的其他数据估算的。
   *
   * @param capabilities VP9 解码器的 {@link CodecCapabilities}，如果未知则为 {@code null}。
   * @return 解码器的估算 {@link CodecProfileLevel CodecProfileLevels}。
   */
  private static CodecProfileLevel[] estimateLegacyVp9ProfileLevels(
      @Nullable CodecCapabilities capabilities) {
    int maxBitrate = 0; // 初始化最大比特率为 0
    if (capabilities != null) { // 如果编解码器能力不为空
      @Nullable VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities(); // 获取视频能力
      if (videoCapabilities != null) { // 如果视频能力不为空
        maxBitrate = videoCapabilities.getBitrateRange().getUpper(); // 获取最大比特率
      }
    }

    // 值取自 https://www.webmproject.org/vp9/levels。
    int level;
    if (maxBitrate >= 180_000_000) { // 如果最大比特率 >= 180 Mbps
      level = CodecProfileLevel.VP9Level52; // 设置为 VP9 Level 5.2
    } else if (maxBitrate >= 120_000_000) { // 如果最大比特率 >= 120 Mbps
      level = CodecProfileLevel.VP9Level51; // 设置为 VP9 Level 5.1
    } else if (maxBitrate >= 60_000_000) { // 如果最大比特率 >= 60 Mbps
      level = CodecProfileLevel.VP9Level5; // 设置为 VP9 Level 5
    } else if (maxBitrate >= 30_000_000) { // 如果最大比特率 >= 30 Mbps
      level = CodecProfileLevel.VP9Level41; // 设置为 VP9 Level 4.1
    } else if (maxBitrate >= 18_000_000) { // 如果最大比特率 >= 18 Mbps
      level = CodecProfileLevel.VP9Level4; // 设置为 VP9 Level 4
    } else if (maxBitrate >= 12_000_000) { // 如果最大比特率 >= 12 Mbps
      level = CodecProfileLevel.VP9Level31; // 设置为 VP9 Level 3.1
    } else if (maxBitrate >= 7_200_000) { // 如果最大比特率 >= 7.2 Mbps
      level = CodecProfileLevel.VP9Level3; // 设置为 VP9 Level 3
    } else if (maxBitrate >= 3_600_000) { // 如果最大比特率 >= 3.6 Mbps
      level = CodecProfileLevel.VP9Level21; // 设置为 VP9 Level 2.1
    } else if (maxBitrate >= 1_800_000) { // 如果最大比特率 >= 1.8 Mbps
      level = CodecProfileLevel.VP9Level2; // 设置为 VP9 Level 2
    } else if (maxBitrate >= 800_000) { // 如果最大比特率 >= 800 Kbps
      level = CodecProfileLevel.VP9Level11; // 设置为 VP9 Level 1.1
    } else { // 假设 Level 1 始终支持。
      level = CodecProfileLevel.VP9Level1; // 设置为 VP9 Level 1
    }

    CodecProfileLevel profileLevel = new CodecProfileLevel(); // 创建 CodecProfileLevel 实例
    // 由于此方法仅用于旧设备，因此假设仅支持 Profile 0。
    profileLevel.profile = CodecProfileLevel.VP9Profile0; // 设置 Profile 为 VP9 Profile 0
    profileLevel.level = level; // 设置 Level

    return new CodecProfileLevel[] {profileLevel}; // 返回包含单个 ProfileLevel 的数组
  }

  /**
   * 返回解码器是否已知在适应时失败，尽管它自称是自适应解码器。
   *
   * @param name 解码器名称。
   * @return 如果解码器已知在适应时失败，则返回 true。
   */
  private static boolean needsDisableAdaptationWorkaround(String name) {
    return Util.SDK_INT <= 22 // 如果 API 版本 <= 22
        && ("ODROID-XU3".equals(Util.MODEL) || "Nexus 10".equals(Util.MODEL)) // 并且设备型号是 ODROID-XU3 或 Nexus 10
        && ("OMX.Exynos.AVC.Decoder".equals(name) || "OMX.Exynos.AVC.Decoder.secure".equals(name)); // 并且解码器名称是 OMX.Exynos.AVC.Decoder 或其安全版本
  }

  /**
   * 返回解码器是否已知在尝试使用新格式的配置数据重新配置时失败。
   *
   * @param name 解码器名称。
   * @return 如果解码器已知在尝试使用新格式的配置数据重新配置时失败，则返回 true。
   */
  private static boolean needsAdaptationReconfigureWorkaround(String name) {
    return Util.MODEL.startsWith("SM-T230") // 如果设备型号以 SM-T230 开头
        && "OMX.MARVELL.VIDEO.HW.CODA7542DECODER".equals(name); // 并且解码器名称是 OMX.MARVELL.VIDEO.HW.CODA7542DECODER
  }

  /**
   * 返回解码器是否已知在刷新以适应新格式时行为不正确。
   *
   * @param mimeType MIME 类型的名称。
   * @return 如果解码器已知在刷新以适应新格式时行为不正确，则返回 true。
   */
  private static boolean needsAdaptationFlushWorkaround(String mimeType) {
    // 对于 Opus，我们不会刷新并重用编解码器，因为解码器在刷新后可能会丢弃样本，这会导致流更改后音频被丢弃（参见 [Internal: b/143450854]）。
    // 对于其他格式，如果编解码器初始化数据未更改，我们允许在刷新后重用。
    return MimeTypes.AUDIO_OPUS.equals(mimeType); // 如果 MIME 类型是 Opus，则返回 true
  }

  /**
   * 已知在某些设备上，垂直分辨率的能力报告不准确 [Internal ref: b/31387661]。
   * 启用此解决方案后，我们还会检查宽度和高度交换后能力是否支持。如果支持，我们假设垂直分辨率也受支持。
   *
   * @param name 编解码器的名称。
   * @return 是否启用该解决方案。
   */
  private static boolean needsRotatedVerticalResolutionWorkaround(String name) {
    if ("OMX.MTK.VIDEO.DECODER.HEVC".equals(name) && "mcv5a".equals(Util.DEVICE)) { // 如果编解码器名称是 OMX.MTK.VIDEO.DECODER.HEVC 且设备名称是 mcv5a
      // 参见 https://github.com/google/ExoPlayer/issues/6612。
      return false; // 返回 false
    }
    return true; // 否则返回 true
  }

  /**
   * 判断某个 Profile 是否从支持的 Profile 列表中排除。这可能发生在设备声明支持某个 Profile，但实际上并不支持的情况下。
   */
  private static boolean needsProfileExcludedWorkaround(String mimeType, int profile) {
    // 参见 https://github.com/google/ExoPlayer/issues/3537
    return MimeTypes.VIDEO_H265.equals(mimeType) // 如果 MIME 类型是 H265
        && CodecProfileLevel.HEVCProfileMain10 == profile // 并且 Profile 是 HEVC Main10
        && ("sailfish".equals(Util.DEVICE) || "marlin".equals(Util.DEVICE)); // 并且设备名称是 sailfish 或 marlin
  }
}
