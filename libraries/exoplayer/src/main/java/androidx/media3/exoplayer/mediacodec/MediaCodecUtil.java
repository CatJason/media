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

import static androidx.media3.common.util.CodecSpecificDataUtil.getHevcProfileAndLevel;
import static java.lang.Math.max;

import android.annotation.SuppressLint;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.text.TextUtils;
import android.util.Pair;
import androidx.annotation.CheckResult;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.CodecSpecificDataUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.container.NalUnitUtil;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.InlineMe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * 媒体编解码器工具类（线程安全）
 *
 * <p>本类提供跨Android版本的编解码器查询能力，主要功能包括：</p>
 *
 * <ul>
 *   <li>获取设备支持的编解码器信息列表（按系统优先级排序）</li>
 *   <li>解码器兼容性检测（硬件加速/安全解密/隧道模式等）</li>
 *   <li>编解码器能力评估（如最大支持分辨率）</li>
 *   <li>设备特定问题修复（通过黑名单和优先级调整实现）</li>
 * </ul>
 *
 * <h3>使用规范：</h3>
 * <ol>
 *   <li>编解码器信息查询结果建议缓存，避免重复系统调用</li>
 *   <li>优先使用{@link #getDecoderInfo}自动选择解码器</li>
 *   <li>需要特殊能力（如安全播放）时明确指定参数</li>
 * </ol>
 *
 * <p>注意：本类API可能随Android版本调整，标记为{@link UnstableApi}的接口不保证向后兼容</p>
 *
 * <p>典型使用场景：</p>
 * <pre>{@code
 * // 获取H.264硬件解码器
 * MediaCodecInfo decoderInfo = MediaCodecUtil.getDecoderInfo(MimeTypes.VIDEO_H264, false, false);
 * }</pre>
 */
@SuppressLint("InlinedApi")
@UnstableApi
public final class MediaCodecUtil {

  /**
   * 当查询设备的底层媒体能力发生错误时抛出
   *
   * <p>此类故障在正常操作中通常不会出现，且通常是暂时性的（例如媒体服务（mediaserver）进程
   * 崩溃但尚未重启时可能发生）</p>
   */
  public static class DecoderQueryException extends Exception {

    private DecoderQueryException(Throwable cause) {
      super("Failed to query underlying media codecs", cause);
    }
  }

  private static final String TAG = "MediaCodecUtil";

  @GuardedBy("MediaCodecUtil.class")
  private static final HashMap<CodecKey, List<MediaCodecInfo>> decoderInfosCache = new HashMap<>();

  // Lazily initialized.
  private static int maxH264DecodableFrameSize = -1;

  private MediaCodecUtil() {
  }

  /**
   * 可选方法：为指定MIME类型预热编解码器缓存
   *
   * <p>调用此方法可能会加速后续对 {@link #getDecoderInfo(String, boolean, boolean)} 和
   * {@link #getDecoderInfos(String, boolean, boolean)} 的调用</p>
   *
   * @param mimeType  媒体类型标识符（如"video/avc"）
   * @param secure    是否需要支持安全解密模式。除非确实需要安全解密，否则应始终传递false
   * @param tunneling 是否需要支持隧道模式。除非确实需要隧道传输，否则应始终传递false
   */
  public static void warmDecoderInfoCache(String mimeType, boolean secure, boolean tunneling) {
    try {
      // 主动触发编解码器查询以填充缓存
      getDecoderInfos(mimeType, secure, tunneling);
    } catch (DecoderQueryException e) {
      // 预热操作属于优化措施，可安全忽略异常
      Log.e(TAG, "编解码器预热失败", e);
    }
  }

  /* Clears the codec cache.*/
  @VisibleForTesting
  public static synchronized void clearDecoderInfoCache() {
    decoderInfosCache.clear();
  }

  /**
   * 获取仅用于数据解密（不进行解码）的解码器信息
   *
   * <p>该方法专用于获取支持原始音频格式({@link MimeTypes#AUDIO_RAW})的纯解密解码器，
   * 适用于需要绕过解码流程直接处理加密数据的场景（如DRM解密预处理）</p>
   *
   * @return 描述解码器的{@link MediaCodecInfo}对象，若设备无相应能力则返回null
   * @throws DecoderQueryException 当查询系统解码器时发生底层错误（如媒体服务不可用）
   */
  @Nullable
  public static MediaCodecInfo getDecryptOnlyDecoderInfo() throws DecoderQueryException {
    // 查询原始音频格式（PCM）的解密专用解码器
    // 参数说明：
    // MimeTypes.AUDIO_RAW - 指定原始音频格式（未压缩的PCM数据）
    // secure = false       - 不要求安全解密能力
    // tunneling = false    - 不要求隧道模式传输
    return getDecoderInfo(
        MimeTypes.AUDIO_RAW,
        /* secure= */ false,
        /* tunneling= */ false
    );
  }

  /**
   * 获取指定媒体类型的最优解码器信息
   *
   * <p>该方法通过优先级排序返回最适合指定格式的解码器，系统默认会优先选择硬件加速解码器</p>
   *
   * @param mimeType  媒体格式类型（如"video/avc"表示H.264）
   * @param secure    是否要求支持安全解密模式（如DRM保护内容），非必要场景应始终传false
   * @param tunneling 是否要求支持隧道模式（用于低延迟视频渲染），非必要场景应始终传false
   * @return 解码器信息对象（按系统优先级排序的首选解码器），无可用解码器时返回null
   * @throws DecoderQueryException 当底层媒体服务不可用或查询过程发生错误时抛出
   */
  @Nullable
  public static MediaCodecInfo getDecoderInfo(String mimeType, boolean secure, boolean tunneling)
      throws DecoderQueryException {
    // 获取符合条件的所有解码器列表（已按优先级排序）
    List<MediaCodecInfo> decoderInfos = getDecoderInfos(mimeType, secure, tunneling);

    // 返回列表首项（最高优先级解码器）或null表示无可用
    return decoderInfos.isEmpty() ? null : decoderInfos.get(0);
  }

  /**
   * 获取指定媒体类型的所有可用解码器信息列表（按系统优先级排序）
   *
   * <p>实现特性：
   * 1. 带缓存机制减少重复查询开销
   * 2. 兼容处理Android 5.0-6.0系统的安全解码器列表异常
   * 3. 应用设备特定修复策略</p>
   *
   * @param mimeType  媒体格式类型（如"video/hevc"）
   * @param secure    是否要求安全解密能力（DRM场景使用）
   * @param tunneling 是否要求隧道模式支持（低延迟渲染场景）
   * @return 按系统优先级排序的不可修改解码器列表（可能为空）
   * @throws DecoderQueryException 当底层媒体服务不可用时抛出
   */
  public static synchronized List<MediaCodecInfo> getDecoderInfos(
      String mimeType, boolean secure, boolean tunneling) throws DecoderQueryException {
    // 生成复合缓存键（包含格式+安全+隧道模式参数）
    CodecKey key = new CodecKey(mimeType, secure, tunneling);

    // 优先从缓存获取已处理过的解码器列表
    @Nullable List<MediaCodecInfo> cachedDecoderInfos = decoderInfosCache.get(key);
    if (cachedDecoderInfos != null) {
      return cachedDecoderInfos; // 缓存命中直接返回
    }

    // Android 5.0+ 使用新版API查询（支持安全/隧道模式过滤）
    MediaCodecListCompat mediaCodecList = new MediaCodecListCompatV21(secure, tunneling);
    // 获取原始解码器列表（未排序和应用修复）
    ArrayList<MediaCodecInfo> decoderInfos = getDecoderInfosInternal(key, mediaCodecList);

    // 兼容处理：Android 5.0-6.0系统安全解码器列表缺失问题
    if (secure && decoderInfos.isEmpty() && Util.SDK_INT <= 23) {
      /* 已知问题：部分Android 5.x设备不会在MediaCodecList中列出安全解码器（如Nexus 5）
       * 解决方案：回退到旧版API 16的查询方式（不过滤安全/隧道模式） */
      mediaCodecList = new MediaCodecListCompatV16(); // 旧版查询适配器
      decoderInfos = getDecoderInfosInternal(key, mediaCodecList);

      // 日志提示可能存在错误的安全解码器假设
      if (!decoderInfos.isEmpty()) {
        Log.w(TAG, "系统API未列出安全解码器：" + mimeType + "，假定可用：" + decoderInfos.get(0).name);
      }
    }

    // 应用设备特定的兼容性修复（如排除有缺陷的解码器）
    applyWorkarounds(mimeType, decoderInfos);

    // 转换为不可变列表并更新缓存
    ImmutableList<MediaCodecInfo> immutableDecoderInfos = ImmutableList.copyOf(decoderInfos);
    decoderInfosCache.put(key, immutableDecoderInfos);

    return immutableDecoderInfos;
  }

  /**
   * 获取支持指定格式的所有解码器列表（包含备选MIME类型），按优先级排序
   *
   * <p>该方法扩展了{@link #getDecoderInfos}的能力，通过以下方式增强兼容性：
   * 1. 包含主MIME类型的解码器列表（如format.sampleMimeType）
   * 2. 自动查找并包含兼容的备选MIME类型解码器（如HEVC→H.265的别名处理）</p>
   *
   * <p>注意：返回列表的排序基于{@link MediaCodecSelector}的默认优先级，
   * 但未考虑解码器对格式细节（如profile/level/分辨率）的完整支持程度，如需更精确排序，
   * 应使用{@link #getDecoderInfosSortedByFormatSupport}</p>
   *
   * @param mediaCodecSelector       解码器选择器（定义优先级规则）
   * @param format                   媒体格式描述（必须包含sampleMimeType）
   * @param requiresSecureDecoder    是否要求安全解密能力（如DRM场景）
   * @param requiresTunnelingDecoder 是否要求隧道模式支持（低延迟渲染）
   * @return 包含主备解码器的不可修改列表（可能为空）
   * @throws DecoderQueryException 当底层编解码器查询失败时抛出
   */
  @RequiresNonNull("#2.sampleMimeType")
  public static List<MediaCodecInfo> getDecoderInfosSoftMatch(
      MediaCodecSelector mediaCodecSelector,
      Format format,
      boolean requiresSecureDecoder,
      boolean requiresTunnelingDecoder)
      throws DecoderQueryException {
    // 获取主MIME类型的解码器列表（如video/avc）
    List<MediaCodecInfo> decoderInfos =
        mediaCodecSelector.getDecoderInfos(
            format.sampleMimeType, requiresSecureDecoder, requiresTunnelingDecoder);

    // 查找备选兼容MIME类型的解码器（如video/hevc→video/hvc1）
    List<MediaCodecInfo> alternativeDecoderInfos =
        getAlternativeDecoderInfos(
            mediaCodecSelector, format, requiresSecureDecoder, requiresTunnelingDecoder);

    // 合并主备列表（主类型在前，备选在后）
    return ImmutableList.<MediaCodecInfo>builder()
        .addAll(decoderInfos)         // 添加主类型解码器
        .addAll(alternativeDecoderInfos) // 添加备选类型解码器
        .build();
  }

  /**
   * 获取支持指定格式备选MIME类型的解码器列表（按优先级排序）
   *
   * <p>该方法通过{@link #getAlternativeCodecMimeType}获取与输入格式兼容的替代MIME类型（如将"video/x-vnd.on2.vp9"映射为"video/vp9"），
   * 并返回对应解码器信息。主要用于处理非常规MIME类型的媒体格式兼容性问题</p>
   *
   * <p>典型应用场景：
   * 1. 处理使用非标准MIME类型的媒体流（如某些RTSP流使用"video/h264"而非标准"video/avc"）
   * 2. 兼容不同设备厂商的MIME类型别名</p>
   *
   * @param mediaCodecSelector       解码器选择器（定义优先级规则）
   * @param format                   原始媒体格式描述（需包含有效的sampleMimeType）
   * @param requiresSecureDecoder    是否要求支持安全解密（如播放DRM保护内容）
   * @param requiresTunnelingDecoder 是否要求支持隧道模式（如车载系统低延迟播放）
   * @return 备选MIME类型对应的解码器列表（按系统优先级排序），无备选类型时返回空列表
   * @throws DecoderQueryException 当底层编解码器查询失败时抛出（如媒体服务不可用）
   */
  public static List<MediaCodecInfo> getAlternativeDecoderInfos(
      MediaCodecSelector mediaCodecSelector,
      Format format,
      boolean requiresSecureDecoder,
      boolean requiresTunnelingDecoder)
      throws DecoderQueryException {
    // 获取与输入格式兼容的替代MIME类型（可能为null）
    @Nullable String alternativeMimeType = getAlternativeCodecMimeType(format);

    // 无备选类型时立即返回空列表
    if (alternativeMimeType == null) {
      return ImmutableList.of(); // 返回不可变的空列表
    }

    // 查询备选MIME类型的可用解码器（应用安全/隧道模式过滤）
    return mediaCodecSelector.getDecoderInfos(
        alternativeMimeType, requiresSecureDecoder, requiresTunnelingDecoder);
  }

  /**
   * 对解码器列表进行功能性支持度排序，返回可修改的排序后副本
   *
   * <p>排序规则：完全支持指定格式的解码器优先排列，不支持的后置。
   * 功能性支持指解码器能够处理格式的特定参数组合（如H.264的High Profile@Level5.1）</p>
   *
   * @param decoderInfos 原始解码器列表（不会修改原列表）
   * @param format       需要检测支持度的目标格式
   * @return 新的可修改列表，元素按格式支持度降序排列
   */
  @CheckResult
  public static List<MediaCodecInfo> getDecoderInfosSortedByFormatSupport(
      List<MediaCodecInfo> decoderInfos, Format format) {
    // 创建原始列表的浅拷贝（避免修改输入列表）
    decoderInfos = new ArrayList<>(decoderInfos);

    // 使用双值排序算法（支持=1，不支持=0）
    sortByScore(
        decoderInfos,
        // 检测格式支持性：调用isFormatFunctionallySupported判断
        decoderInfo -> decoderInfo.isFormatFunctionallySupported(format) ? 1 : 0
    );

    return decoderInfos;
  }

  /**
   * 获取设备H.264解码器支持的最大可解码帧尺寸（宽×高像素数）
   *
   * <p>该方法通过以下步骤确定最大支持尺寸：
   * 1. 查询设备默认H.264解码器的所有支持ProfileLevel
   * 2. 将每个H.264级别转换为对应的最大帧尺寸
   * 3. 取所有级别中的最大值，并确保至少满足Android兼容性定义文档（CDD）的480p要求</p>
   *
   * @return 设备支持的最大H.264帧尺寸（像素数），如未找到解码器可能返回0
   * @throws DecoderQueryException 当查询解码器信息时发生错误（如媒体服务不可用）
   */
  public static int maxH264DecodableFrameSize() throws DecoderQueryException {
    if (maxH264DecodableFrameSize == -1) { // 使用-1作为未初始化标记
      int result = 0;

      // 查询非安全/非隧道模式的H.264解码器
      @Nullable
      MediaCodecInfo decoderInfo =
          getDecoderInfo(MimeTypes.VIDEO_H264, /* secure= */ false, /* tunneling= */ false);

      if (decoderInfo != null) {
        // 遍历解码器支持的所有H.264级别（如Level3.1、Level4等）
        for (CodecProfileLevel profileLevel : decoderInfo.getProfileLevels()) {
          // 将H.264级别转换为最大帧尺寸（如Level4对应720p）
          result = max(avcLevelToMaxFrameSize(profileLevel.level), result);
        }

        // 根据Android CDD要求：所有设备至少支持480p（720x480=345600像素）
        result = max(result, 720 * 480); // 确保最低支持480p
      }

      maxH264DecodableFrameSize = result; // 缓存计算结果
    }
    return maxH264DecodableFrameSize;
  }

  /**
   * @deprecated Use {@link CodecSpecificDataUtil#getCodecProfileAndLevel(Format)}.
   */
  @InlineMe(
      replacement = "CodecSpecificDataUtil.getCodecProfileAndLevel(format)",
      imports = {"androidx.media3.common.util.CodecSpecificDataUtil"})
  @Deprecated
  @Nullable
  public static Pair<Integer, Integer> getCodecProfileAndLevel(Format format) {
    return CodecSpecificDataUtil.getCodecProfileAndLevel(format);
  }

  /**
   * 从HEVC媒体格式中解析基础层的编码配置（Profile）和等级（Level）
   *
   * <p>主要用于分层HEVC（L-HEVC）回退到单层HEVC的场景，通过解析初始化数据中的
   * codecs参数获取基础层的编码参数</p>
   *
   * @param format 包含HEVC初始化数据的媒体格式对象（需含有效initializationData）
   * @return 包含Profile和Level常量值的Pair对象（如{@link CodecProfileLevel#HEVCProfileMain},
   * {@link CodecProfileLevel#HEVCMainTierLevel31}），
   * 当初始化数据无效或无法解析时返回null
   */
  @Nullable
  public static Pair<Integer, Integer> getHevcBaseLayerCodecProfileAndLevel(Format format) {
    // 从HEVC初始化数据中提取基础层codecs字符串（如"hvc1.1.6.L123.B0"）
    String codecs = NalUnitUtil.getH265BaseLayerCodecsString(format.initializationData);
    if (codecs == null) {
      return null; // 无有效基础层数据
    }

    // 按点号分割codecs字符串（示例分割后：["hvc1", "1", "6", "L123", "B0"]）
    String[] parts = Util.split(codecs.trim(), "\\.");

    // 解析出Profile和Level，并考虑HDR颜色信息的影响
    return getHevcProfileAndLevel(codecs, parts, format.colorInfo);
  }

  /**
   * 获取当前媒体格式的备选解码MIME类型（除默认sampleMimeType外）
   *
   * <p>该方法针对特定编码格式提供兼容性备选方案，主要用于处理以下场景：
   * 1. E-AC3沉浸式音频降级为普通E-AC3
   * 2. Dolby Vision分层编码回退到基础层编码
   * 3. MV-HEVC多视角视频回退到普通HEVC</p>
   *
   * @param format 媒体格式对象（需包含有效的sampleMimeType）
   * @return 兼容的备选MIME类型（如H.265对应Dolby Vision基础层），无兼容类型时返回null
   */
  @Nullable
  public static String getAlternativeCodecMimeType(Format format) {
    // 场景1：E-AC3沉浸式音频（JOC扩展）→ 降级为普通E-AC3（仅支持2D声道）
    if (MimeTypes.AUDIO_E_AC3_JOC.equals(format.sampleMimeType)) {
      return MimeTypes.AUDIO_E_AC3; // 返回非沉浸式音频类型
    }

    // 场景2：Dolby Vision分层视频→ 根据Profile选择基础层编码类型
    if (MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)) {
      /* Dolby Vision兼容性处理逻辑：
       * - dvheDtr/dvheSt 使用HEVC基础层
       * - dvavSe 使用AVC基础层
       * - dvav110 使用AV1基础层
       * 排除不兼容的Profile：dvheStn（非向后兼容）、dvheDtb（已弃用）*/
      @Nullable Pair<Integer, Integer> codecProfileAndLevel = getCodecProfileAndLevel(format);
      if (codecProfileAndLevel != null) {
        int profile = codecProfileAndLevel.first;
        if (profile == CodecProfileLevel.DolbyVisionProfileDvheDtr
            || profile == CodecProfileLevel.DolbyVisionProfileDvheSt) {
          return MimeTypes.VIDEO_H265; // HEVC基础层
        } else if (profile == CodecProfileLevel.DolbyVisionProfileDvavSe) {
          return MimeTypes.VIDEO_H264; // AVC基础层
        } else if (profile == CodecProfileLevel.DolbyVisionProfileDvav110) {
          return MimeTypes.VIDEO_AV1;  // AV1基础层
        }
      }
    }

    // 场景3：MV-HEVC多视角视频→ 回退到普通HEVC单视角
    if (MimeTypes.VIDEO_MV_HEVC.equals(format.sampleMimeType)) {
      return MimeTypes.VIDEO_H265; // HEVC基础层
    }

    return null; // 无可用备选类型
  }

  // Internal methods.

  /**
   * 根据指定的编解码器键（CodecKey）获取匹配的媒体编解码器信息列表
   *
   * <p>该方法从系统提供的编解码器列表（mediaCodecList）中筛选出符合以下条件的编解码器：
   * 1. 支持CodecKey中指定的MIME类型
   * 2. 满足安全解密要求（若CodecKey要求）
   * 3. 支持隧道模式传输（若CodecKey要求）
   *
   * <p>返回列表顺序与输入mediaCodecList保持严格一致，通常该列表已按系统推荐优先级排序</p>
   *
   * @param key            编解码器查询键，包含：
   *                       - mimeType：媒体格式类型（如"video/avc"）
   *                       - secure：是否需要安全解密能力
   *                       - tunneling：是否需要隧道模式支持
   * @param mediaCodecList 待筛选的原始编解码器列表（通常来自MediaCodecList）
   * @return 符合条件且可用的编解码器信息列表（可能为空）
   * @throws DecoderQueryException 当发生以下情况时抛出：
   *                               1. 底层媒体服务不可用
   *                               2. 编解码器能力查询失败
   *                               3. 安全解密组件初始化异常
   */
  private static ArrayList<MediaCodecInfo> getDecoderInfosInternal(
      CodecKey key, MediaCodecListCompat mediaCodecList) throws DecoderQueryException {
    try {
      ArrayList<MediaCodecInfo> decoderInfos = new ArrayList<>();
      String mimeType = key.mimeType;
      int numberOfCodecs = mediaCodecList.getCodecCount();
      boolean secureDecodersExplicit = mediaCodecList.secureDecodersExplicit();
      // 遍历系统编解码器列表（系统已按优先级排序）
      for (int i = 0; i < numberOfCodecs; i++) {
        android.media.MediaCodecInfo codecInfo = mediaCodecList.getCodecInfoAt(i);
        if (isAlias(codecInfo)) {
          // 过滤条件1：跳过别名编解码器（避免重复）
          continue;
        }

        // 过滤条件2：基础可用性检查（黑名单/命名规范等）
        String name = codecInfo.getName();
        if (!isCodecUsableDecoder(codecInfo, name, secureDecodersExplicit, mimeType)) {
          continue;
        }

        // 过滤条件3：验证实际支持的MIME类型
        @Nullable String codecMimeType = getCodecMimeType(codecInfo, name, mimeType);
        if (codecMimeType == null) {
          continue;
        }
        try {
          // 获取编解码器能力集
          CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(codecMimeType);

          // 隧道模式支持验证
          boolean tunnelingSupported =
              mediaCodecList.isFeatureSupported(
                  CodecCapabilities.FEATURE_TunneledPlayback, codecMimeType, capabilities);
          boolean tunnelingRequired =
              mediaCodecList.isFeatureRequired(
                  CodecCapabilities.FEATURE_TunneledPlayback, codecMimeType, capabilities);
          if ((!key.tunneling && tunnelingRequired) || (key.tunneling && !tunnelingSupported)) {
            continue; // 隧道模式不匹配
          }

          // 安全模式支持验证
          boolean secureSupported =
              mediaCodecList.isFeatureSupported(
                  CodecCapabilities.FEATURE_SecurePlayback, codecMimeType, capabilities);
          boolean secureRequired =
              mediaCodecList.isFeatureRequired(
                  CodecCapabilities.FEATURE_SecurePlayback, codecMimeType, capabilities);
          if ((!key.secure && secureRequired) || (key.secure && !secureSupported)) {
            continue; // 安全模式不匹配
          }

          // 编解码器属性判断
          boolean hardwareAccelerated = isHardwareAccelerated(codecInfo, mimeType);
          boolean softwareOnly = isSoftwareOnly(codecInfo, mimeType);
          boolean vendor = isVendor(codecInfo); // 是否厂商定制解码器

          // 处理安全解码器声明差异
          /**
           * @param secureDecodersExplicit  系统是否显式声明安全解码器（API 21+为true，低版本为false）
           * @param tunnelingSupported 是否支持隧道模式播放（如Android Auto的低延迟需求）
           * @param tunnelingRequired  是否强制要求隧道模式（仅当解码器必须使用隧道模式时返回true）
           * @param hardwareAccelerated  是否硬件加速解码器（如高通/MTK芯片的专用解码模块）
           * @param forceSecure  强制启用安全模式（用于隐式安全支持的特殊处理）
           */
          if ((secureDecodersExplicit && key.secure == secureSupported)
              || (!secureDecodersExplicit && !key.secure)) {
            decoderInfos.add(
                MediaCodecInfo.newInstance(
                    name,
                    mimeType,
                    codecMimeType,
                    capabilities,
                    hardwareAccelerated,
                    softwareOnly,
                    vendor,
                    /* forceDisableAdaptive= */ false,
                    /* forceSecure= */ false));
          } else if (!secureDecodersExplicit && secureSupported) {
            decoderInfos.add(
                MediaCodecInfo.newInstance(
                    name + ".secure",
                    mimeType,
                    codecMimeType,
                    capabilities,
                    hardwareAccelerated,
                    softwareOnly,
                    vendor,
                    /* forceDisableAdaptive= */ false,
                    /* forceSecure= */ true));
            // It only makes sense to have one synthesized secure decoder, return immediately.
            return decoderInfos;
          }
        } catch (Exception e) {
          if (Util.SDK_INT <= 23 && !decoderInfos.isEmpty()) {
            // Suppress error querying secondary codec capabilities up to API level 23.
            Log.e(TAG, "Skipping codec " + name + " (failed to query capabilities)");
          } else {
            // Rethrow error querying primary codec capabilities, or secondary codec
            // capabilities if API level is greater than 23.
            Log.e(TAG, "Failed to query codec " + name + " (" + codecMimeType + ")");
            throw e;
          }
        }
      }
      return decoderInfos;
    } catch (Exception e) {
      // If the underlying mediaserver is in a bad state, we may catch an IllegalStateException
      // or an IllegalArgumentException here.
      throw new DecoderQueryException(e);
    }
  }

  /**
   * 返回编解码器对指定媒体类型支持的MIME类型，若不可用则返回null
   *
   * <p>该方法主要用于处理编解码器的MIME类型兼容性问题，包含以下特性：
   * 1. 当编解码器明确支持传入的MIME类型时，返回相同类型
   * 2. 识别并返回设备厂商使用的非标准MIME类型别名（如"video/avc"→"video/h264"）
   * 3. 当编解码器完全不支持指定类型时返回null</p>
   *
   * @param info     编解码器的能力信息对象（{@link MediaCodecInfo}）
   * @param name     编解码器名称（如"OMX.qcom.video.decoder.avc"）
   * @param mimeType 需要检测的原始MIME类型（如"video/avc"）
   * @return 实际支持的MIME类型字符串，可能情况：
   * - 与输入mimeType相同（标准支持）
   * - 不同的别名（非标准实现，如"video/hevc"→"video/hvc1"）
   * - null（表示完全不支持）
   */
  @Nullable
  private static String getCodecMimeType(
      android.media.MediaCodecInfo info, String name, String mimeType) {
    // 步骤1：遍历编解码器官方声明支持的所有MIME类型
    String[] supportedTypes = info.getSupportedTypes();
    for (String supportedType : supportedTypes) {
      // 忽略大小写匹配，发现直接支持则立即返回（如标准类型匹配）
      if (supportedType.equalsIgnoreCase(mimeType)) {
        return supportedType;
      }
    }

    // 步骤2：处理特殊厂商的非标准MIME类型声明

    // 场景1：Dolby Vision兼容处理
    if (mimeType.equals(MimeTypes.VIDEO_DOLBY_VISION)) {
      // 高通HEVC DV解码器使用video/hevcdv作为标识
      if ("OMX.MS.HEVCDV.Decoder".equals(name)) {
        return "video/hevcdv";
      }
      // Realtek芯片组的DV解码器使用video/dv_hevc标识
      else if ("OMX.RTK.video.decoder".equals(name)
          || "OMX.realtek.video.decoder.tunneled".equals(name)) {
        return "video/dv_hevc";
      }
    }
    // 场景2：多视角HEVC兼容处理
    else if (mimeType.equals(MimeTypes.VIDEO_MV_HEVC)) {
      // 高通QTI芯片组的MV-HEVC解码器使用video/x-mvhevc
      if ("c2.qti.mvhevc.decoder".equals(name)) {
        return "video/x-mvhevc";
      }
    }
    // 场景3：LG设备的音频编解码器特殊标识
    else if (mimeType.equals(MimeTypes.AUDIO_ALAC) && "OMX.lge.alac.decoder".equals(name)) {
      return "audio/x-lg-alac"; // LG ALAC解码器的私有标识
    } else if (mimeType.equals(MimeTypes.AUDIO_FLAC) && "OMX.lge.flac.decoder".equals(name)) {
      return "audio/x-lg-flac"; // LG FLAC解码器的私有标识
    } else if (mimeType.equals(MimeTypes.AUDIO_AC3) && "OMX.lge.ac3.decoder".equals(name)) {
      return "audio/lg-ac3";    // LG AC3解码器的私有标识
    }

    // 步骤3：无匹配类型时返回null（表示不支持）
    return null;
  }

  /**
   * 判断指定编解码器在当前设备上是否可用作解码器
   *
   * <p>该方法通过多维度验证编解码器的可用性，主要检查项包括：
   * 1. 编解码器黑名单（已知存在缺陷的编解码器）
   * 2. 设备型号兼容性（特定厂商设备的特殊处理）
   * 3. 安全解码器声明方式（显式/隐式）
   * 4. 编解码器命名规范校验</p>
   *
   * @param info                  编解码器能力信息对象（需包含支持的MIME类型等元数据）
   * @param name                  编解码器系统名称（如"OMX.qcom.video.decoder.avc"）
   * @param secureDecodersExplicit 安全解码器是否显式声明（API21+为true，低版本为false）
   * @param mimeType              目标媒体格式的MIME类型（如"video/avc"）
   * @return true表示可用，false表示需排除（如存在已知兼容性问题）
   */
  /**
   * 判断指定解码器在当前设备上是否可用
   *
   * <p>本方法通过多个硬件/系统级别的兼容性检查，排除已知存在问题的解码器</p>
   *
   * @param info                   编解码器能力信息
   * @param name                   解码器系统名称（如"OMX.qcom.video.decoder.avc"）
   * @param secureDecodersExplicit 是否显式声明安全解码器（API21+为true）
   * @param mimeType               媒体格式类型
   * @return true表示可用，false表示存在兼容性问题需排除
   */
  private static boolean isCodecUsableDecoder(
      android.media.MediaCodecInfo info,
      String name,
      boolean secureDecodersExplicit,
      String mimeType) {
    // 基础排除条件：编码器或隐式安全声明下的.secure后缀解码器
    if (info.isEncoder() || (!secureDecodersExplicit && name.endsWith(".secure"))) {
      return false;
    }

    /**
     * 修复三星Galaxy S6系列设备AAC解码问题（详见GitHub #3249）
     * 受影响设备：
     * - Galaxy S6 [zeroflte/SC-05G]
     * - Galaxy S6 Edge [zerolte/404SC/SCV31)
     * - Galaxy S6 Edge+ (zenlte)
     * - Galaxy S6 Active (marinelteatt)
     * 问题解码器:
     * - OMX.SEC.aac.dec
     * - OMX.Exynos.AAC.Decoder
     */
    if (Util.SDK_INT < 24
        && ("OMX.SEC.aac.dec".equals(name) || "OMX.Exynos.AAC.Decoder".equals(name))
        && "samsung".equals(Util.MANUFACTURER)
        && (Util.DEVICE.startsWith("zeroflte") // S6设备前缀
        || Util.DEVICE.startsWith("zerolte")  // S6 Edge设备前缀
        || Util.DEVICE.startsWith("zenlte")   // S6 Edge+设备前缀
        || "SC-05G".equals(Util.DEVICE)       // 日本版S6
        || "marinelteatt".equals(Util.DEVICE) // S6 Active
        || "404SC".equals(Util.DEVICE)        // 日本版S6 Edge
        || "SC-04G".equals(Util.DEVICE)
        || "SCV31".equals(Util.DEVICE))) {
      return false; // 已知导致音频解码问题
    }

    /* 排除联发科AC3解码器对E-AC3 JOC格式的支持（内部问题b/69400041）
     * 该解码器无法正确处理3D音频的JOC流，只能降级为2D播放
     */
    if (Util.SDK_INT <= 23
        && MimeTypes.AUDIO_E_AC3_JOC.equals(mimeType)
        && "OMX.MTK.AUDIO.DECODER.DSPAC3".equals(name)) {
      return false;
    }

    return true;
  }

  /**
   * 对解码器列表应用已知的兼容性修复方案
   *
   * <p>此方法针对特定设备和编解码器组合的已知问题进行处理，主要修复类型包括：
   * 1. 移除存在稳定性问题的解码器（如导致崩溃或画面撕裂）
   * 2. 调整解码器优先级（如优先使用软件解码绕过硬件缺陷）
   * 3. 特殊格式的兼容性处理（如HDR/Dolby Vision格式适配）</p>
   *
   * @param mimeType     媒体格式类型（如"video/hevc"）
   * @param decoderInfos 待处理的解码器列表（将被直接修改）
   */
  /**
   * 应用设备特定的解码器兼容性修复方案
   *
   * <p>该方法处理已知的设备/解码器组合问题，包含以下主要修复场景：</p>
   *
   * @param mimeType     媒体格式类型（如audio/raw）
   * @param decoderInfos 解码器信息列表（将被直接修改）
   */
  private static void applyWorkarounds(String mimeType, List<MediaCodecInfo> decoderInfos) {
    // 修复场景1：原始音频解码的特殊处理
    if (MimeTypes.AUDIO_RAW.equals(mimeType)) {
      /* 针对OPPO R9设备的修复（Android 8.0以下）
       * 问题描述：该设备未正确声明原始音频解码能力，需手动添加Google软件解码器
       * 参考问题：GitHub Issue #5782
       */
      if (Util.SDK_INT < 26
          && Util.DEVICE.equals("R9")
          && decoderInfos.size() == 1
          && decoderInfos.get(0).name.equals("OMX.MTK.AUDIO.DECODER.RAW")) {
        // 添加Google软件解码器作为备选
        decoderInfos.add(
            MediaCodecInfo.newInstance(
                /* name= */ "OMX.google.raw.decoder",
                /* mimeType= */ MimeTypes.AUDIO_RAW,
                /* codecMimeType= */ MimeTypes.AUDIO_RAW,
                /* capabilities= */ null,
                /* hardwareAccelerated= */ false,
                /* softwareOnly= */ true,  // 标记为纯软件解码
                /* vendor= */ false,
                /* forceDisableAdaptive= */ false,
                /* forceSecure= */ false));
      }

      /* 原始音频解码器优先级排序策略：
       * 1. 优先Google/Android通用解码器（软件实现更可靠）
       * 2. 降级MTK解码器在Android 8.0以下的优先级（可能修改音频数据）
       * 参考问题：内部问题b/62337687
       */
      sortByScore(
          decoderInfos,
          decoderInfo -> {
            String name = decoderInfo.name;
            if (name.startsWith("OMX.google") || name.startsWith("c2.android")) {
              return 1;  // 提高通用解码器优先级
            }
            if (Util.SDK_INT < 26 && name.equals("OMX.MTK.AUDIO.DECODER.RAW")) {
              return -1; // 降低MTK解码器优先级
            }
            return 0;
          });
    }

    // 修复场景2：FLAC解码器优先级调整（Android 12以下）
    if (Util.SDK_INT < 32 && decoderInfos.size() > 1) {
      String firstCodecName = decoderInfos.get(0).name;
      /* 高通QTI FLAC解码器在旧设备上存在兼容性问题
       * 解决方案：将其从列表首位移至末尾
       * 参考问题：内部问题b/199124812
       */
      if ("OMX.qti.audio.decoder.flac".equals(firstCodecName)) {
        decoderInfos.add(decoderInfos.remove(0)); // 移动首项到末尾
      }
    }
  }

  private static boolean isAlias(android.media.MediaCodecInfo info) {
    return Util.SDK_INT >= 29 && isAliasV29(info);
  }

  @RequiresApi(29)
  private static boolean isAliasV29(android.media.MediaCodecInfo info) {
    return info.isAlias();
  }

  /**
   * 判断编解码器是否支持硬件加速
   *
   * <p>实现策略：
   * 1. Android 10+（API 29+）直接调用系统原生方法
   * 2. 低版本通过反向判断"是否为纯软件实现"来近似推断</p>
   *
   * <p>注意：在API 29以下版本中，此判断为启发式逻辑，可能存在误差。
   * 因为系统未明确区分硬件加速与第三方实现（如厂商自定义的非硬件非纯软件解码器）</p>
   *
   * @param codecInfo 编解码器信息对象
   * @param mimeType  媒体格式类型（用于低版本辅助判断）
   * @return true表示硬件加速，false表示非硬件加速（可能是软件或其它实现）
   */
  private static boolean isHardwareAccelerated(
      android.media.MediaCodecInfo codecInfo, String mimeType) {
    // Android 10+ 使用原生API判断
    if (Util.SDK_INT >= 29) {
      return isHardwareAcceleratedV29(codecInfo); // 调用API 29+专用方法
    }

    /* 低版本启发式判断逻辑：
     * 假设：非纯软件实现 ≈ 硬件加速
     * 潜在误差案例：
     * - 某些厂商的混合实现（如DSP加速但不属于传统硬件加速）
     * - 第三方优化库（如使用NEON指令集的软件解码器）
     */
    return !isSoftwareOnly(codecInfo, mimeType);
  }

  @RequiresApi(29)
  private static boolean isHardwareAcceleratedV29(android.media.MediaCodecInfo codecInfo) {
    return codecInfo.isHardwareAccelerated();
  }

  /**
   * 判断编解码器是否为纯软件实现
   *
   * <p>实现策略：
   * 1. Android 10+ 使用系统原生API判断
   * 2. 低版本通过编解码器名称模式推断</p>
   *
   * <p>注意：音频编解码器在低版本中默认视为软件实现（系统API行为模拟）</p>
   *
   * @param codecInfo 编解码器信息对象
   * @param mimeType  媒体格式类型（用于音频类型特殊处理）
   * @return true表示纯软件实现，false表示可能包含硬件加速
   */
  private static boolean isSoftwareOnly(android.media.MediaCodecInfo codecInfo, String mimeType) {
    // Android 10+ 直接使用系统API判断
    if (Util.SDK_INT >= 29) {
      return isSoftwareOnlyV29(codecInfo);
    }

    // 低版本逻辑
    if (MimeTypes.isAudio(mimeType)) {
      /* 音频编解码器启发式判断：
       * 假设所有音频解码器均为软件实现（可能与实际情况存在偏差）
       * 示例案例：
       * - 高通WCD9380芯片的硬件音频解码器可能被错误分类
       */
      return true;
    }

    // 统一转为小写避免大小写问题
    String codecName = Ascii.toLowerCase(codecInfo.getName());

    // 特殊硬件编解码器例外处理
    if (codecName.startsWith("arc.")) {
      // Chrome OS的ARC虚拟机硬件编解码器（如"arc.video.decoder.avc"）
      return false;
    }

    /* 软件编解码器名称特征匹配（常见模式）：
     * 1. Google软件实现：omx.google.*（如OMX.google.h264.decoder）
     * 2. FFmpeg软件解码器：omx.ffmpeg.*
     * 3. 三星软件实现：omx.sec.*.sw.*（如OMX.SEC.x264.sw.decoder）
     * 4. 高通特殊软件HEVC解码器：omx.qcom.video.decoder.hevcswvdec
     * 5. Codec2软件组件：c2.android.* / c2.google.*
     * 6. 非标准前缀名称（非omx/c2开头视为软件实现）
     */
    return codecName.startsWith("omx.google.")
        || codecName.startsWith("omx.ffmpeg.")
        || (codecName.startsWith("omx.sec.") && codecName.contains(".sw."))
        || codecName.equals("omx.qcom.video.decoder.hevcswvdec")
        || codecName.startsWith("c2.android.")
        || codecName.startsWith("c2.google.")
        || (!codecName.startsWith("omx.") && !codecName.startsWith("c2."));
  }

  @RequiresApi(29)
  private static boolean isSoftwareOnlyV29(android.media.MediaCodecInfo codecInfo) {
    return codecInfo.isSoftwareOnly();
  }

  /**
   * 判断编解码器是否为厂商定制实现
   *
   * <p>实现策略：
   * 1. Android 10+ 使用系统API {@link android.media.MediaCodecInfo#isVendor()}
   * 2. 低版本通过排除Google实现来近似判断（非Google即视为厂商定制）</p>
   *
   * <p>注意：低版本判断为启发式逻辑，可能将第三方开源实现误判为厂商定制</p>
   *
   * @param codecInfo 编解码器信息对象
   * @return true表示厂商定制实现（如高通/三星等），false表示Google或开源实现
   */
  private static boolean isVendor(android.media.MediaCodecInfo codecInfo) {
    // Android 10+ 使用原生API判断
    if (Util.SDK_INT >= 29) {
      return isVendorV29(codecInfo);
    }

    // 低版本逻辑：非Google的编解码器均视为厂商定制
    String codecName = Ascii.toLowerCase(codecInfo.getName());
    /* 排除以下前缀：
     * - omx.google. : Google官方软件实现（如OMX.google.h264.decoder）
     * - c2.android.  : Codec2框架的Android官方实现
     * - c2.google.   : Google的Codec2扩展实现
     */
    return !codecName.startsWith("omx.google.")
        && !codecName.startsWith("c2.android.")
        && !codecName.startsWith("c2.google.");
  }

  @RequiresApi(29)
  private static boolean isVendorV29(android.media.MediaCodecInfo codecInfo) {
    return codecInfo.isVendor();
  }

  /**
   * 根据H.264（AVC）级别计算最大可解码帧尺寸（单位：像素）
   *
   * <p>转换规则基于ISO/IEC 14496-10标准（H.264）表A-1中定义的级别参数，
   * 通过最大宏块数计算得出。每个宏块为16x16像素，故总像素数为：宏块数 × 256</p>
   *
   * @param avcLevel H.264级别常量，来自{@link CodecProfileLevel}的AVCLevel*常量
   * @return 对应级别支持的最大帧像素数，无法识别时返回-1
   */
  private static int avcLevelToMaxFrameSize(int avcLevel) {
    switch (avcLevel) {
      // Level 1 & 1b: 99 宏块 → 99×16×16 = 25,344像素（QCIF分辨率）
      case CodecProfileLevel.AVCLevel1:
      case CodecProfileLevel.AVCLevel1b:
        return 99 * 16 * 16;

      // Level 1.2/1.3/2: 396 宏块 → 396×256 = 101,376像素（CIF×4）
      case CodecProfileLevel.AVCLevel12:
      case CodecProfileLevel.AVCLevel13:
      case CodecProfileLevel.AVCLevel2:
        return 396 * 16 * 16;

      // Level 2.1: 792 宏块 → 792×256 = 202,752像素（D1分辨率）
      case CodecProfileLevel.AVCLevel21:
        return 792 * 16 * 16;

      // Level 2.2/3: 1620 宏块 → 1620×256 = 414,720像素（720×480）
      case CodecProfileLevel.AVCLevel22:
      case CodecProfileLevel.AVCLevel3:
        return 1620 * 16 * 16;

      // Level 3.1: 3600 宏块 → 3600×256 = 921,600像素（1280×720）
      case CodecProfileLevel.AVCLevel31:
        return 3600 * 16 * 16;

      // Level 3.2: 5120 宏块 → 5120×256 = 1,310,720像素（1440×900）
      case CodecProfileLevel.AVCLevel32:
        return 5120 * 16 * 16;

      // Level 4/4.1: 8192 宏块 → 8192×256 = 2,097,152像素（1920×1080）
      case CodecProfileLevel.AVCLevel4:
      case CodecProfileLevel.AVCLevel41:
        return 8192 * 16 * 16;

      // Level 4.2: 8704 宏块 → 8704×256 = 2,228,224像素（2048×1080）
      case CodecProfileLevel.AVCLevel42:
        return 8704 * 16 * 16;

      // Level 5: 22080 宏块 → 22080×256 = 5,652,480像素（4096×2304）
      case CodecProfileLevel.AVCLevel5:
        return 22080 * 16 * 16;

      // Level 5.1/5.2: 36864 宏块 → 36864×256 = 9,437,184像素（4096×3072）
      case CodecProfileLevel.AVCLevel51:
      case CodecProfileLevel.AVCLevel52:
        return 36864 * 16 * 16;

      // Level 6.x: 139264 宏块 → 139264×256 = 35,651,584像素（8192×4320 8K）
      case CodecProfileLevel.AVCLevel6:
      case CodecProfileLevel.AVCLevel61:
      case CodecProfileLevel.AVCLevel62:
        return 139264 * 16 * 16;

      // 未知级别返回-1（需调用方处理）
      default:
        return -1;
    }
  }

  /**
   * 对列表进行降序排序（原地修改），根据元素评分从高到低排列
   *
   * <p>排序特性：
   * 1. 直接修改原始列表（非线程安全）
   * 2. 当评分相同时，不保证元素相对顺序（非稳定排序）
   * 3. 时间复杂度为O(n log n)</p>
   *
   * @param list          待排序列表（将被直接修改）
   * @param scoreProvider 元素评分提供器，通过{@link ScoreProvider#getScore}获取元素评分值
   * @param <T>           列表元素类型
   */
  private static <T> void sortByScore(List<T> list, ScoreProvider<T> scoreProvider) {
    // 使用自定义比较器实现降序排序
    Collections.sort(list, (a, b) -> scoreProvider.getScore(b) - scoreProvider.getScore(a));
  }

  /**
   * 项目评分提供器的接口定义。
   */
  private interface ScoreProvider<T> {

    /**
     * 获取指定项目的评分值。
     *
     * @param t 需要评分的项目对象（泛型类型）
     * @return 项目的整型评分值，数值越大表示优先级越高
     */
    int getScore(T t);
  }

  private interface MediaCodecListCompat {

    /**
     * 编解码器列表中的编解码器数量。
     */
    int getCodecCount();

    /**
     * 获取列表中指定索引位置的编解码器信息。
     *
     * @param index 索引位置
     */
    android.media.MediaCodecInfo getCodecInfoAt(int index);

    /**
     * 判断安全解码器是否被显式列出（如果存在）。
     */
    boolean secureDecodersExplicit();

    /**
     * 判断指定的 {@link CodecCapabilities} 特性是否被支持。
     *
     * @param feature      需要检测的特性名称
     * @param mimeType     媒体类型（如 video/avc）
     * @param capabilities 编解码器能力描述
     */
    boolean isFeatureSupported(String feature, String mimeType, CodecCapabilities capabilities);

    /**
     * 判断指定的 {@link CodecCapabilities} 特性是否是必需项。
     *
     * @param feature      需要检测的特性名称
     * @param mimeType     媒体类型（如 video/avc）
     * @param capabilities 编解码器能力描述
     */
    boolean isFeatureRequired(String feature, String mimeType, CodecCapabilities capabilities);
  }

  /**
   * Android 5.0（API 21）及以上版本的编解码器列表兼容实现
   *
   * <p>实现特性：
   * 1. 支持按需加载编解码器列表（延迟初始化）
   * 2. 显式区分安全/隧道模式编解码器
   * 3. 直接使用系统API判断特性支持情况</p>
   */
  private static final class MediaCodecListCompatV21 implements MediaCodecListCompat {

    private final int codecKind; // 编解码器类型标识（ALL_CODECS或REGULAR_CODECS）

    @Nullable
    private android.media.MediaCodecInfo[] mediaCodecInfos; // 编解码器信息缓存

    /**
     * @param includeSecure    是否包含安全解码器
     * @param includeTunneling 是否包含隧道模式解码器
     */
    public MediaCodecListCompatV21(boolean includeSecure, boolean includeTunneling) {
      // 当需要安全或隧道模式时，获取全部编解码器列表
      codecKind = (includeSecure || includeTunneling)
          ? MediaCodecList.ALL_CODECS
          : MediaCodecList.REGULAR_CODECS;
    }

    @Override
    public int getCodecCount() {
      ensureMediaCodecInfosInitialized(); // 延迟初始化
      return mediaCodecInfos.length;
    }

    @Override
    public android.media.MediaCodecInfo getCodecInfoAt(int index) {
      ensureMediaCodecInfosInitialized(); // 延迟初始化
      return mediaCodecInfos[index];
    }

    @Override
    public boolean secureDecodersExplicit() {
      return true; // API21+显式声明安全解码器
    }

    @Override
    public boolean isFeatureSupported(
        String feature, String mimeType, CodecCapabilities capabilities) {
      // 直接使用系统能力检测
      return capabilities.isFeatureSupported(feature);
    }

    @Override
    public boolean isFeatureRequired(
        String feature, String mimeType, CodecCapabilities capabilities) {
      // 直接使用系统能力检测
      return capabilities.isFeatureRequired(feature);
    }

    /**
     * 延迟初始化编解码器列表（减少不必要的系统调用）
     */
    @EnsuresNonNull({"mediaCodecInfos"})
    private void ensureMediaCodecInfosInitialized() {
      if (mediaCodecInfos == null) {
        mediaCodecInfos = new MediaCodecList(codecKind).getCodecInfos();
      }
    }
  }

  /**
   * Android 4.1-5.0（API 16-20）的编解码器列表兼容实现
   *
   * <p>实现特性：
   * 1. 直接调用系统原生API
   * 2. 隐式处理安全解码器（假设H264存在安全解码器）
   * 3. 不支持特性必需性检测</p>
   */
  private static final class MediaCodecListCompatV16 implements MediaCodecListCompat {

    @Override
    public int getCodecCount() {
      return MediaCodecList.getCodecCount(); // 系统原生调用
    }

    @Override
    public android.media.MediaCodecInfo getCodecInfoAt(int index) {
      return MediaCodecList.getCodecInfoAt(index); // 系统原生调用
    }

    @Override
    public boolean secureDecodersExplicit() {
      return false; // API<21不显式声明安全解码器
    }

    @Override
    public boolean isFeatureSupported(
        String feature, String mimeType, CodecCapabilities capabilities) {
      // 特殊处理H264安全解码器假设
      return CodecCapabilities.FEATURE_SecurePlayback.equals(feature)
          && MimeTypes.VIDEO_H264.equals(mimeType);
    }

    @Override
    public boolean isFeatureRequired(
        String feature, String mimeType, CodecCapabilities capabilities) {
      return false; // 旧版本不支持必需特性检测
    }
  }

  /**
   * 编解码器配置的复合键（用于缓存和查找）
   *
   * <p>关键标识维度：
   * 1. 媒体格式类型（如video/avc）
   * 2. 安全模式需求
   * 3. 隧道模式需求</p>
   */
  private static final class CodecKey {

    public final String mimeType;   // 媒体格式类型标识
    public final boolean secure;    // 是否需要安全解密
    public final boolean tunneling; // 是否需要隧道模式

    /**
     * @param mimeType  媒体格式类型（不可为空）
     * @param secure    安全模式标记
     * @param tunneling 隧道模式标记
     */
    public CodecKey(String mimeType, boolean secure, boolean tunneling) {
      this.mimeType = mimeType;
      this.secure = secure;
      this.tunneling = tunneling;
    }

    /**
     * 复合哈希计算（确保哈希表分布均匀）
     */
    @Override
    public int hashCode() {
      final int prime = 31; // 质数基数
      int result = 1;
      result = prime * result + mimeType.hashCode();
      result = prime * result + (secure ? 1231 : 1237); // 布尔值特殊编码
      result = prime * result + (tunneling ? 1231 : 1237);
      return result;
    }

    /**
     * 全等比较（三个字段必须完全相等）
     */
    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      CodecKey other = (CodecKey) obj;
      return TextUtils.equals(mimeType, other.mimeType)
          && secure == other.secure
          && tunneling == other.tunneling;
    }
  }
}
