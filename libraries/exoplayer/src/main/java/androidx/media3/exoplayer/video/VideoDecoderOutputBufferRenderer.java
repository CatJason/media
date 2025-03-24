package androidx.media3.exoplayer.video;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.VideoDecoderOutputBuffer;

/** 渲染 {@link VideoDecoderOutputBuffer} 的接口。 */
@UnstableApi
public interface VideoDecoderOutputBufferRenderer {

  /**
   * 设置要渲染的输出缓冲区。渲染器负责释放该缓冲区。
   *
   * @param outputBuffer 要渲染的输出缓冲区。
   */
  void setOutputBuffer(VideoDecoderOutputBuffer outputBuffer);
}