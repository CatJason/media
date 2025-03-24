package androidx.media3.common.util;

/** A consumer for long timestamp values. */
@UnstableApi
public interface TimestampConsumer {

  /**
   * Consumes a timestamp.
   *
   * @param timestampUs The timestamp, in microseconds.
   */
  public void onTimestamp(long timestampUs);
}
