package androidx.media3.exoplayer.video;

import android.media.MediaFormat;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;

/** 用于监听与正在渲染的视频帧相关的元数据的监听器。 */
@UnstableApi
public interface VideoFrameMetadataListener {
  /**
   * 在视频帧即将被渲染时在播放线程上调用。
   *
   * @param presentationTimeUs 视频帧的呈现时间，单位为微秒。
   * @param releaseTimeNs 视频帧应该被显示的墙钟时间，单位为纳秒。
   *     如果设备的平台 API 版本小于 21，则此值为最佳估计值。
   * @param format 与视频帧关联的格式。
   * @param mediaFormat 与视频帧关联的框架媒体格式，如果未知或不适用（例如，因为视频帧不是由 {@link
   *     android.media.MediaCodec MediaCodec} 输出），则为 {@code null}。
   */
  void onVideoFrameAboutToBeRendered(
      long presentationTimeUs,
      long releaseTimeNs,
      Format format,
      @Nullable MediaFormat mediaFormat);
}