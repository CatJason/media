/*
 * 版权所有 2024 The Android Open Source Project
 *
 * 根据 Apache License, Version 2.0（“许可证”）授权；
 * 除非符合许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则按“原样”分发的软件
 * 没有任何形式的明示或暗示的保证或条件。
 * 请参阅许可证以了解特定语言下的权限和限制。
 */
package androidx.media3.exoplayer.mediacodec;

import static androidx.media3.common.util.Assertions.checkState;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.media.LoudnessCodecController.OnLoudnessCodecUpdateListener;
import android.media.MediaCodec;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.UnstableApi;
import java.util.HashSet;
import java.util.Iterator;

/** 平台 {@link android.media.LoudnessCodecController} 的封装类。 */
@RequiresApi(35)
@UnstableApi
public final class LoudnessCodecController {

  /** 用于拦截和修改响度参数后再将其应用到编解码器的接口。 */
  public interface LoudnessParameterUpdateListener {

    /** 默认的更新监听器，返回未修改的参数集。 */
    LoudnessParameterUpdateListener DEFAULT = bundle -> bundle;

    /**
     * 返回要应用到编解码器的更新后的响度参数。
     *
     * @param parameters 建议的响度参数。
     * @return 更新后的响度参数。
     */
    Bundle onLoudnessParameterUpdate(Bundle parameters);
  }

  private final HashSet<MediaCodec> mediaCodecs;
  private final LoudnessParameterUpdateListener updateListener;

  @Nullable private android.media.LoudnessCodecController loudnessCodecController;

  /** 创建响度控制器。 */
  public LoudnessCodecController() {
    this(LoudnessParameterUpdateListener.DEFAULT);
  }

  /**
   * 创建响度控制器。
   *
   * @param updateListener 用于拦截和修改参数的 {@link LoudnessParameterUpdateListener}。
   */
  public LoudnessCodecController(LoudnessParameterUpdateListener updateListener) {
    this.mediaCodecs = new HashSet<>();
    this.updateListener = updateListener;
  }

  /**
   * 使用音频会话 ID 配置响度控制器。
   *
   * @param audioSessionId 音频会话 ID。
   */
  public void setAudioSessionId(int audioSessionId) {
    if (loudnessCodecController != null) {
      loudnessCodecController.close();
      loudnessCodecController = null;
    }
    android.media.LoudnessCodecController loudnessCodecController =
        android.media.LoudnessCodecController.create(
            audioSessionId,
            directExecutor(),
            new OnLoudnessCodecUpdateListener() {
              @Override
              public Bundle onLoudnessCodecUpdate(MediaCodec codec, Bundle parameters) {
                return updateListener.onLoudnessParameterUpdate(parameters);
              }
            });
    this.loudnessCodecController = loudnessCodecController;
    for (Iterator<MediaCodec> it = mediaCodecs.iterator(); it.hasNext(); ) {
      boolean registered = loudnessCodecController.addMediaCodec(it.next());
      if (!registered) {
        it.remove();
      }
    }
  }

  /**
   * 添加一个由响度控制器配置的编解码器。
   *
   * @param mediaCodec 一个 {@link MediaCodec}。
   */
  public void addMediaCodec(MediaCodec mediaCodec) {
    if (loudnessCodecController != null && !loudnessCodecController.addMediaCodec(mediaCodec)) {
      // 如果现有的响度控制器无法处理该编解码器，则不添加。
      return;
    }
    checkState(mediaCodecs.add(mediaCodec));
  }

  /**
   * 从响度控制器的配置中移除一个编解码器。
   *
   * @param mediaCodec 一个 {@link MediaCodec}。
   */
  public void removeMediaCodec(MediaCodec mediaCodec) {
    boolean removedCodec = mediaCodecs.remove(mediaCodec);
    if (removedCodec && loudnessCodecController != null) {
      loudnessCodecController.removeMediaCodec(mediaCodec);
    }
  }

  /** 释放响度控制器。 */
  public void release() {
    mediaCodecs.clear();
    if (loudnessCodecController != null) {
      loudnessCodecController.close();
    }
  }
}