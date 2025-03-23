package androidx.media3.exoplayer.hls;

import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.source.SampleStream;
import java.io.IOException;

/** 用于 HLS 中特定样本队列的 {@link SampleStream}。 */
/* package */ final class HlsSampleStream implements SampleStream {

  private final int trackGroupIndex;
  private final HlsSampleStreamWrapper sampleStreamWrapper;
  private int sampleQueueIndex;

  public HlsSampleStream(HlsSampleStreamWrapper sampleStreamWrapper, int trackGroupIndex) {
    this.sampleStreamWrapper = sampleStreamWrapper;
    this.trackGroupIndex = trackGroupIndex;
    sampleQueueIndex = HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_PENDING;
  }

  public void bindSampleQueue() {
    Assertions.checkArgument(sampleQueueIndex == HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_PENDING);
    sampleQueueIndex = sampleStreamWrapper.bindSampleQueueToSampleStream(trackGroupIndex);
  }

  public void unbindSampleQueue() {
    if (sampleQueueIndex != HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_PENDING) {
      sampleStreamWrapper.unbindSampleQueue(trackGroupIndex);
      sampleQueueIndex = HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_PENDING;
    }
  }

  // SampleStream implementation.

  @Override
  public boolean isReady() {
    return sampleQueueIndex == HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL
        || (hasValidSampleQueueIndex() && sampleStreamWrapper.isReady(sampleQueueIndex));
  }

  @Override
  public void maybeThrowError() throws IOException {
    if (sampleQueueIndex == HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL) {
      throw new SampleQueueMappingException(
          sampleStreamWrapper.getTrackGroups().get(trackGroupIndex).getFormat(0).sampleMimeType);
    } else if (sampleQueueIndex == HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_PENDING) {
      sampleStreamWrapper.maybeThrowError();
    } else if (sampleQueueIndex != HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL) {
      sampleStreamWrapper.maybeThrowError(sampleQueueIndex);
    }
  }

  @Override
  public int readData(
      FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
    if (sampleQueueIndex == HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL) {
      buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
      return C.RESULT_BUFFER_READ;
    }
    return hasValidSampleQueueIndex()
        ? sampleStreamWrapper.readData(sampleQueueIndex, formatHolder, buffer, readFlags)
        : C.RESULT_NOTHING_READ;
  }

  @Override
  public int skipData(long positionUs) {
    return hasValidSampleQueueIndex()
        ? sampleStreamWrapper.skipData(sampleQueueIndex, positionUs)
        : 0;
  }

  // Internal methods.

  private boolean hasValidSampleQueueIndex() {
    return sampleQueueIndex != HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_PENDING
        && sampleQueueIndex != HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL
        && sampleQueueIndex != HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL;
  }
}
