package androidx.media3.common.util;

import static java.lang.Math.min;
import static java.lang.Math.round;

import androidx.media3.common.C;
import androidx.media3.common.audio.SpeedProvider;

/** Utilities for {@link SpeedProvider}. */
@UnstableApi
public class SpeedProviderUtil {

  private SpeedProviderUtil() {}

  /**
   * Returns the duration of the output when the given {@link SpeedProvider} is applied given an
   * input stream with the given {@code durationUs}.
   */
  public static long getDurationAfterSpeedProviderApplied(
      SpeedProvider speedProvider, long durationUs) {
    long speedChangeTimeUs = 0;
    double outputDurationUs = 0;
    while (speedChangeTimeUs < durationUs) {
      long nextSpeedChangeTimeUs = speedProvider.getNextSpeedChangeTimeUs(speedChangeTimeUs);
      if (nextSpeedChangeTimeUs == C.TIME_UNSET) {
        nextSpeedChangeTimeUs = Long.MAX_VALUE;
      }
      outputDurationUs +=
          (min(nextSpeedChangeTimeUs, durationUs) - speedChangeTimeUs)
              / (double) speedProvider.getSpeed(speedChangeTimeUs);
      speedChangeTimeUs = nextSpeedChangeTimeUs;
    }
    return round(outputDurationUs);
  }
}
