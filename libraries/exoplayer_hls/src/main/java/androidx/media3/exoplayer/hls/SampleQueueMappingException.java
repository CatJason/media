package androidx.media3.exoplayer.hls;

import androidx.annotation.Nullable;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.SampleQueue;
import java.io.IOException;

/** 当无法将 {@link TrackGroup} 映射到 {@link SampleQueue} 时抛出的异常。 */
@UnstableApi
public final class SampleQueueMappingException extends IOException {

  /**
   * @param mimeType 映射失败的轨道组的 MIME 类型。
   */
  public SampleQueueMappingException(@Nullable String mimeType) {
    super("无法将样本队列绑定到 MIME 类型为 " + mimeType + " 的 TrackGroup。");
  }
}