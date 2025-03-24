package androidx.media3.common;

import androidx.media3.common.util.UnstableApi;

/** Marker interface for a video frame effect. */
@UnstableApi
public interface Effect {

  /**
   * Returns the expected duration of the output stream when the effect is applied given a input
   * {@code durationUs}.
   */
  default long getDurationAfterEffectApplied(long durationUs) {
    return durationUs;
  }
}
