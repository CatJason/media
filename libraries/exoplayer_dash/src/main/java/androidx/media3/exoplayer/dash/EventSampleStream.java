package androidx.media3.exoplayer.dash;

import static java.lang.Math.max;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.dash.manifest.EventStream;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.extractor.metadata.emsg.EventMessage;
import androidx.media3.extractor.metadata.emsg.EventMessageEncoder;
import java.io.IOException;

/**
 * 一个 {@link SampleStream}，由从 {@link EventStream} 读取的序列化 {@link EventMessage} 组成。
 */
/* package */ final class EventSampleStream implements SampleStream {

  private final Format upstreamFormat;
  private final EventMessageEncoder eventMessageEncoder;

  private long[] eventTimesUs;
  private boolean eventStreamAppendable;
  private EventStream eventStream;

  private boolean isFormatSentDownstream;
  private int currentIndex;
  private long pendingSeekPositionUs;

  public EventSampleStream(
      EventStream eventStream, Format upstreamFormat, boolean eventStreamAppendable) {
    this.upstreamFormat = upstreamFormat;
    this.eventStream = eventStream;
    eventMessageEncoder = new EventMessageEncoder();
    pendingSeekPositionUs = C.TIME_UNSET;
    eventTimesUs = eventStream.presentationTimesUs;
    updateEventStream(eventStream, eventStreamAppendable);
  }

  public String eventStreamId() {
    return eventStream.id();
  }

  public void updateEventStream(EventStream eventStream, boolean eventStreamAppendable) {
    long lastReadPositionUs = currentIndex == 0 ? C.TIME_UNSET : eventTimesUs[currentIndex - 1];

    this.eventStreamAppendable = eventStreamAppendable;
    this.eventStream = eventStream;
    this.eventTimesUs = eventStream.presentationTimesUs;
    if (pendingSeekPositionUs != C.TIME_UNSET) {
      seekToUs(pendingSeekPositionUs);
    } else if (lastReadPositionUs != C.TIME_UNSET) {
      currentIndex =
          Util.binarySearchCeil(
              eventTimesUs, lastReadPositionUs, /* inclusive= */ false, /* stayInBounds= */ false);
    }
  }

  /**
   * 跳转到指定的时间位置（以微秒为单位）。
   *
   * @param positionUs 跳转的时间位置，单位为微秒。
   */
  public void seekToUs(long positionUs) {
    currentIndex =
        Util.binarySearchCeil(
            eventTimesUs, positionUs, /* inclusive= */ true, /* stayInBounds= */ false);
    boolean isPendingSeek = eventStreamAppendable && currentIndex == eventTimesUs.length;
    pendingSeekPositionUs = isPendingSeek ? positionUs : C.TIME_UNSET;
  }

  @Override
  public boolean isReady() {
    return true;
  }

  @Override
  public void maybeThrowError() throws IOException {
    // 无操作。
  }

  @Override
  public int readData(
      FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
    boolean noMoreEventsInStream = currentIndex == eventTimesUs.length;
    if (noMoreEventsInStream && !eventStreamAppendable) {
      buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      return C.RESULT_BUFFER_READ;
    }
    if ((readFlags & FLAG_REQUIRE_FORMAT) != 0 || !isFormatSentDownstream) {
      formatHolder.format = upstreamFormat;
      isFormatSentDownstream = true;
      return C.RESULT_FORMAT_READ;
    }
    if (noMoreEventsInStream) {
      // 后续可能会追加更多事件。
      return C.RESULT_NOTHING_READ;
    }
    int sampleIndex = currentIndex;
    if ((readFlags & SampleStream.FLAG_PEEK) == 0) {
      currentIndex++;
    }
    if ((readFlags & SampleStream.FLAG_OMIT_SAMPLE_DATA) == 0) {
      byte[] serializedEvent = eventMessageEncoder.encode(eventStream.events[sampleIndex]);
      buffer.ensureSpaceForWrite(serializedEvent.length);
      buffer.data.put(serializedEvent);
    }
    buffer.timeUs = eventTimesUs[sampleIndex];
    buffer.setFlags(C.BUFFER_FLAG_KEY_FRAME);
    return C.RESULT_BUFFER_READ;
  }

  @Override
  public int skipData(long positionUs) {
    int newIndex = max(currentIndex, Util.binarySearchCeil(eventTimesUs, positionUs, true, false));
    int skipped = newIndex - currentIndex;
    currentIndex = newIndex;
    return skipped;
  }
}