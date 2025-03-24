package androidx.media3.common;

import androidx.media3.common.util.UnstableApi;

/** 用于处理输入帧的监听器。 */
@UnstableApi
public interface OnInputFrameProcessedListener {

  /**
   * 当给定的输入帧被处理时调用。
   *
   * @param textureId 已处理纹理的标识符。
   * @param syncObject 一个 GL 同步对象（参见 https://www.khronos.org/opengl/wiki/Sync_Object），
   *     在纹理的最后一次使用后被插入到 GL 命令流中。如果且仅当 {@code GLES30#glFenceSync} 失败或 EGL 上下文版本低于 OpenGL 3.0 时，该值为 0。
   *     同步对象在使用后必须被 {@link androidx.media3.common.util.GlUtil#deleteSyncObject 删除}。
   * @throws VideoFrameProcessingException 如果在处理事件时遇到错误，则抛出此异常。
   */
  void onInputFrameProcessed(int textureId, long syncObject) throws VideoFrameProcessingException;
}
