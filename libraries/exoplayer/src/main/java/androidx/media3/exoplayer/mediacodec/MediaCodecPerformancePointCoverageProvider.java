/*
 * 版权所有 2024 The Android Open Source Project
 *
 * 根据 Apache 许可证 2.0 版本（“许可证”）授权；
 * 除非符合许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则根据许可证分发的软件是基于“按原样”分发的，
 * 没有任何明示或暗示的担保或条件。
 * 请参阅许可证以了解具体的语言权限和限制。
 */
package androidx.media3.exoplayer.mediacodec;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import androidx.annotation.IntDef;
import androidx.annotation.RequiresApi;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** 通过 PerformancePoints 检查媒体编解码器支持的实用类。 */
/* package */ final class MediaCodecPerformancePointCoverageProvider {

  /**
   * 设备是否提供了 PerformancePoints，并且应忽略覆盖结果，因为 PerformancePoints 未覆盖 CDD 要求。
   */
  @SuppressWarnings("NonFinalStaticField")
  private static @MonotonicNonNull Boolean shouldIgnorePerformancePoints;

  private MediaCodecPerformancePointCoverageProvider() {}

  /** 评估 {@link PerformancePoint} 覆盖结果的可能结果。 */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
      COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED,
      COVERAGE_RESULT_NO,
      COVERAGE_RESULT_YES
  })
  @interface PerformancePointCoverageResult {}

  /**
   * {@link VideoCapabilities} 不包含任何有效的 {@linkplain PerformancePoint PerformancePoints}。
   */
  /* package */ static final int COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED = 0;

  /**
   * 解码器至少有一个 PerformancePoint，但没有一个覆盖分辨率和帧率。
   */
  /* package */ static final int COVERAGE_RESULT_NO = 1;

  /** 解码器有一个 PerformancePoint 覆盖了分辨率和帧率。 */
  /* package */ static final int COVERAGE_RESULT_YES = 2;

  /**
   * 此方法返回解码器的 {@link VideoCapabilities} 是否通过其 {@link PerformancePoint} 列表覆盖了分辨率和帧率。
   *
   * @param videoCapabilities 解码器的 {@link VideoCapabilities}
   * @param width 宽度（以像素为单位）。
   * @param height 高度（以像素为单位）。
   * @param frameRate 可选的帧率（以帧/秒为单位）。如果设置为 {@link Format#NO_VALUE} 或任何小于或等于 0 的值，则忽略。
   * @return 如果 {@link VideoCapabilities} 有一个 {@link PerformancePoint} 列表覆盖了分辨率和帧率，则返回 {@link
   *     #COVERAGE_RESULT_YES}；如果列表未提供覆盖，则返回 {@link #COVERAGE_RESULT_NO}。如果 {@link
   *     VideoCapabilities} 不包含有效的 {@code PerformancePoints} 列表，则返回 {@link
   *     #COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED}
   */
  public static @PerformancePointCoverageResult int areResolutionAndFrameRateCovered(
      VideoCapabilities videoCapabilities, int width, int height, double frameRate) {
    if (Util.SDK_INT < 29
        || (shouldIgnorePerformancePoints != null && shouldIgnorePerformancePoints)) {
      return COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED;
    }

    return Api29.areResolutionAndFrameRateCovered(videoCapabilities, width, height, frameRate);
  }

  @RequiresApi(29)
  private static final class Api29 {
    public static @PerformancePointCoverageResult int areResolutionAndFrameRateCovered(
        VideoCapabilities videoCapabilities, int width, int height, double frameRate) {
      List<PerformancePoint> performancePointList =
          videoCapabilities.getSupportedPerformancePoints();
      if (performancePointList == null || performancePointList.isEmpty()) {
        return COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED;
      }

      // 将帧率向下取整，以避免由于略微超出标准格式的限制而导致覆盖检查失败的情况（例如，1080p 30 fps）。[Internal ref: b/134706676]
      PerformancePoint targetPerformancePoint =
          new PerformancePoint(width, height, (int) frameRate);

      @PerformancePointCoverageResult
      int performancePointCoverageResult =
          evaluatePerformancePointCoverage(performancePointList, targetPerformancePoint);

      if (performancePointCoverageResult == COVERAGE_RESULT_NO
          && shouldIgnorePerformancePoints == null) {
        // 参见 https://github.com/google/ExoPlayer/issues/10898,
        // https://github.com/androidx/media/issues/693,
        // https://github.com/androidx/media/issues/966 和 [internal ref: b/267324685]。
        shouldIgnorePerformancePoints = shouldIgnorePerformancePoints();
        if (shouldIgnorePerformancePoints) {
          return COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED;
        }
      }

      return performancePointCoverageResult;
    }

    /**
     * 检查 PerformancePoints 是否覆盖了支持 H264 720p 60 fps 的 CDD 要求。
     */
    private static boolean shouldIgnorePerformancePoints() {
      if (Util.SDK_INT >= 35) {
        // 与下面相同的检查在 CTS 中进行了测试，我们应该从 API 35 获得可靠的结果。
        return false;
      }
      @PerformancePointCoverageResult
      int h264RequiredSupportResult =
          evaluateH264RequiredSupport(/* requiresSecureDecoder= */ false);
      @PerformancePointCoverageResult
      int h264SecureRequiredSupportResult =
          evaluateH264RequiredSupport(/* requiresSecureDecoder= */ true);

      if (h264RequiredSupportResult == COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED) {
        return true;
      }
      if (h264SecureRequiredSupportResult == COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED) {
        return h264RequiredSupportResult != COVERAGE_RESULT_YES;
      }
      return h264RequiredSupportResult != COVERAGE_RESULT_YES
          || h264SecureRequiredSupportResult != COVERAGE_RESULT_YES;
    }

    private static @PerformancePointCoverageResult int evaluateH264RequiredSupport(
        boolean requiresSecureDecoder) {
      try {
        Format formatH264 = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build();
        // 需要空检查以通过 RequiresNonNull 注解的 getDecoderInfosSoftMatch。
        if (formatH264.sampleMimeType != null) {
          List<MediaCodecInfo> decoderInfos =
              MediaCodecUtil.getDecoderInfosSoftMatch(
                  MediaCodecSelector.DEFAULT,
                  formatH264,
                  /* requiresSecureDecoder= */ requiresSecureDecoder,
                  /* requiresTunnelingDecoder= */ false);
          for (int i = 0; i < decoderInfos.size(); i++) {
            if (decoderInfos.get(i).capabilities != null
                && decoderInfos.get(i).capabilities.getVideoCapabilities() != null) {
              List<PerformancePoint> performancePointListH264 =
                  decoderInfos
                      .get(i)
                      .capabilities
                      .getVideoCapabilities()
                      .getSupportedPerformancePoints();
              if (performancePointListH264 != null && !performancePointListH264.isEmpty()) {
                PerformancePoint targetPerformancePointH264 =
                    new PerformancePoint(/* width= */ 1280, /* height= */ 720, /* frameRate= */ 60);
                return evaluatePerformancePointCoverage(
                    performancePointListH264, targetPerformancePointH264);
              }
            }
          }
        }
        return COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED;
      } catch (MediaCodecUtil.DecoderQueryException ignored) {
        return COVERAGE_RESULT_NO_PERFORMANCE_POINTS_UNSUPPORTED;
      }
    }

    private static @PerformancePointCoverageResult int evaluatePerformancePointCoverage(
        List<PerformancePoint> performancePointList, PerformancePoint targetPerformancePoint) {
      for (int i = 0; i < performancePointList.size(); i++) {
        if (performancePointList.get(i).covers(targetPerformancePoint)) {
          return COVERAGE_RESULT_YES;
        }
      }
      return COVERAGE_RESULT_NO;
    }
  }
}