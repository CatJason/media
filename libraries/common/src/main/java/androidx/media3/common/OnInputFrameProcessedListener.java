package androidx.media3.common;

import androidx.media3.common.util.UnstableApi;

/** A listener for processing input frames. */
@UnstableApi
public interface OnInputFrameProcessedListener {

  /**
   * Called when the given input frame has been processed.
   *
   * @param textureId The identifier of the processed texture.
   * @param syncObject A GL sync object (see https://www.khronos.org/opengl/wiki/Sync_Object) that
   *     has been inserted into the GL command stream after the last use of the texture. Value is 0
   *     if and only if the {@code GLES30#glFenceSync} failed or the EGL context version is less
   *     than OpenGL 3.0. The sync object must be {@link
   *     androidx.media3.common.util.GlUtil#deleteSyncObject deleted} after use.
   * @throws VideoFrameProcessingException Thrown if an error was encountered handling the event.
   */
  void onInputFrameProcessed(int textureId, long syncObject) throws VideoFrameProcessingException;
}
