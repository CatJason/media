package androidx.media3.exoplayer.dash;

import static androidx.media3.common.util.Util.parseXsDateTime;

import android.os.Handler;
import android.os.Message;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.source.SampleQueue;
import androidx.media3.exoplayer.source.chunk.Chunk;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.metadata.MetadataInputBuffer;
import androidx.media3.extractor.metadata.emsg.EventMessage;
import androidx.media3.extractor.metadata.emsg.EventMessageDecoder;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * 处理播放器中所有媒体轨道的 emsg 消息。
 *
 * <p>该类仅响应 schemeIdUri 为 "urn:mpeg:dash:event:2012" 且 value 为 "1"/"2"/"3" 的 emsg 消息。
 * 当遇到这些消息时，它将根据 DASH -IF IOP 版本 4.1 的第 4.5.2.1 节处理消息：
 *
 * <ul>
 *   <li>如果 presentation time delta 和 event duration 都为零，则表示媒体播放已结束。
 *   <li>否则，它将从 emsg 消息中解析消息数据，找到过期清单的 publishTime，并标记 publishTime 小于该值的清单为过期。
 * </ul>
 *
 * 在这两种情况下，DASH 媒体源都会收到通知，并应触发清单重新加载。
 */
@UnstableApi
public final class PlayerEmsgHandler implements Handler.Callback {

  private static final int EMSG_MANIFEST_EXPIRED = 1;

  /** 在 DASH 直播流中遇到播放器 emsg 事件时的回调。 */
  public interface PlayerEmsgCallback {

    /** 当当前清单需要刷新时调用。 */
    void onDashManifestRefreshRequested();

    /**
     * 当具有指定 publishTime 的清单已过期时调用。
     *
     * @param expiredManifestPublishTimeUs 已过期的清单 publishTime。
     */
    void onDashManifestPublishTimeExpired(long expiredManifestPublishTimeUs);
  }

  private final Allocator allocator;
  private final PlayerEmsgCallback playerEmsgCallback;
  private final EventMessageDecoder decoder;
  private final Handler handler;
  private final TreeMap<Long, Long> manifestPublishTimeToExpiryTimeUs;

  private DashManifest manifest;

  private long expiredManifestPublishTimeUs;
  private boolean chunkLoadedCompletedSinceLastManifestRefreshRequest;
  private boolean isWaitingForManifestRefresh;
  private boolean released;

  /**
   * @param manifest 初始清单。
   * @param playerEmsgCallback 该事件处理器在处理生成 DASH 媒体源事件的 emsg 消息时可以调用的回调。
   * @param allocator 用于分配资源的 {@link Allocator}。
   */
  public PlayerEmsgHandler(
      DashManifest manifest, PlayerEmsgCallback playerEmsgCallback, Allocator allocator) {
    this.manifest = manifest;
    this.playerEmsgCallback = playerEmsgCallback;
    this.allocator = allocator;

    manifestPublishTimeToExpiryTimeUs = new TreeMap<>();
    handler = Util.createHandlerForCurrentLooper(/* callback= */ this);
    decoder = new EventMessageDecoder();
  }

  /**
   * 更新该处理器使用的 {@link DashManifest}。
   *
   * @param newManifest 更新后的清单。
   */
  public void updateManifest(DashManifest newManifest) {
    isWaitingForManifestRefresh = false;
    expiredManifestPublishTimeUs = C.TIME_UNSET;
    this.manifest = newManifest;
    removePreviouslyExpiredManifestPublishTimeValues();
  }

  /** 返回一个可以写入 emsg 消息的 {@link TrackOutput}。 */
  public PlayerTrackEmsgHandler newPlayerTrackEmsgHandler() {
    return new PlayerTrackEmsgHandler(allocator);
  }

  /** 释放该 emsg 处理器。调用此方法后不应再使用它。 */
  public void release() {
    released = true;
    handler.removeCallbacksAndMessages(null);
  }

  @Override
  public boolean handleMessage(Message message) {
    if (released) {
      return true;
    }
    switch (message.what) {
      case EMSG_MANIFEST_EXPIRED:
        ManifestExpiryEventInfo messageObj = (ManifestExpiryEventInfo) message.obj;
        handleManifestExpiredMessage(
            messageObj.eventTimeUs, messageObj.manifestPublishTimeMsInEmsg);
        return true;
      default:
        // 无操作。
    }
    return false;
  }

  // 内部方法。

  /* package */ boolean maybeRefreshManifestBeforeLoadingNextChunk(long presentationPositionUs) {
    if (!manifest.dynamic) {
      return false;
    }
    if (isWaitingForManifestRefresh) {
      return true;
    }
    boolean manifestRefreshNeeded = false;
    // 找到大于或等于当前清单 publishTime 的最小 publishTime，并检查其对应的过期时间。
    Map.Entry<Long, Long> expiredEntry = ceilingExpiryEntryForPublishTime(manifest.publishTimeMs);
    if (expiredEntry != null) {
      long expiredPointUs = expiredEntry.getValue();
      if (expiredPointUs < presentationPositionUs) {
        expiredManifestPublishTimeUs = expiredEntry.getKey();
        notifyManifestPublishTimeExpired();
        manifestRefreshNeeded = true;
      }
    }
    if (manifestRefreshNeeded) {
      maybeNotifyDashManifestRefreshNeeded();
    }
    return manifestRefreshNeeded;
  }

  /* package */ void onChunkLoadCompleted(Chunk chunk) {
    chunkLoadedCompletedSinceLastManifestRefreshRequest = true;
  }

  /* package */ boolean onChunkLoadError(boolean isForwardSeek) {
    if (!manifest.dynamic) {
      return false;
    }
    if (isWaitingForManifestRefresh) {
      return true;
    }
    if (isForwardSeek) {
      // 如果发生了向前跳转，可能会跳过指示流结束或清单过期的 EMSG。我们必须假设清单可能需要刷新。
      maybeNotifyDashManifestRefreshNeeded();
      return true;
    }
    return false;
  }

  private void handleManifestExpiredMessage(long eventTimeUs, long manifestPublishTimeMsInEmsg) {
    Long previousExpiryTimeUs = manifestPublishTimeToExpiryTimeUs.get(manifestPublishTimeMsInEmsg);
    if (previousExpiryTimeUs == null) {
      manifestPublishTimeToExpiryTimeUs.put(manifestPublishTimeMsInEmsg, eventTimeUs);
    } else {
      if (previousExpiryTimeUs > eventTimeUs) {
        manifestPublishTimeToExpiryTimeUs.put(manifestPublishTimeMsInEmsg, eventTimeUs);
      }
    }
  }

  @Nullable
  private Map.Entry<Long, Long> ceilingExpiryEntryForPublishTime(long publishTimeMs) {
    return manifestPublishTimeToExpiryTimeUs.ceilingEntry(publishTimeMs);
  }

  private void removePreviouslyExpiredManifestPublishTimeValues() {
    for (Iterator<Map.Entry<Long, Long>> it =
        manifestPublishTimeToExpiryTimeUs.entrySet().iterator();
        it.hasNext(); ) {
      Map.Entry<Long, Long> entry = it.next();
      long expiredManifestPublishTime = entry.getKey();
      if (expiredManifestPublishTime < manifest.publishTimeMs) {
        it.remove();
      }
    }
  }

  private void notifyManifestPublishTimeExpired() {
    playerEmsgCallback.onDashManifestPublishTimeExpired(expiredManifestPublishTimeUs);
  }

  /** 请求 DASH 媒体清单刷新（如果需要）。 */
  private void maybeNotifyDashManifestRefreshNeeded() {
    if (!chunkLoadedCompletedSinceLastManifestRefreshRequest) {
      // 除非取得了一些进展，否则不请求刷新。
      return;
    }
    isWaitingForManifestRefresh = true;
    chunkLoadedCompletedSinceLastManifestRefreshRequest = false;
    playerEmsgCallback.onDashManifestRefreshRequested();
  }

  private static long getManifestPublishTimeMsInEmsg(EventMessage eventMessage) {
    try {
      return parseXsDateTime(Util.fromUtf8Bytes(eventMessage.messageData));
    } catch (ParserException ignored) {
      // 如果无法解析此事件，则忽略。
      return C.TIME_UNSET;
    }
  }

  /**
   * 返回具有给定 schemeIdUri 和 value 的事件是否是针对播放器的 DASH emsg 事件。
   */
  private static boolean isPlayerEmsgEvent(String schemeIdUri, String value) {
    return "urn:mpeg:dash:event:2012".equals(schemeIdUri)
        && ("1".equals(value) || "2".equals(value) || "3".equals(value));
  }

  /** 处理播放器特定轨道的 emsg 消息。 */
  public final class PlayerTrackEmsgHandler implements TrackOutput {

    private final SampleQueue sampleQueue;
    private final FormatHolder formatHolder;
    private final MetadataInputBuffer buffer;

    private long maxLoadedChunkEndTimeUs;

    /* package */ PlayerTrackEmsgHandler(Allocator allocator) {
      this.sampleQueue = SampleQueue.createWithoutDrm(allocator);
      formatHolder = new FormatHolder();
      buffer = new MetadataInputBuffer();
      maxLoadedChunkEndTimeUs = C.TIME_UNSET;
    }

    @Override
    public void format(Format format) {
      sampleQueue.format(format);
    }

    @Override
    public int sampleData(
        DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
        throws IOException {
      return sampleQueue.sampleData(input, length, allowEndOfInput);
    }

    @Override
    public void sampleData(ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
      sampleQueue.sampleData(data, length);
    }

    @Override
    public void sampleMetadata(
        long timeUs, int flags, int size, int offset, @Nullable CryptoData cryptoData) {
      sampleQueue.sampleMetadata(timeUs, flags, size, offset, cryptoData);
      parseAndDiscardSamples();
    }

    /**
     * 对于直播流，检查 DASH 清单是否在下个分段开始时间之前过期。如果过期，将通知 DASH 媒体源刷新清单。
     *
     * @param presentationPositionUs 下个加载位置的时间（以微秒为单位）。
     * @return 如果已请求清单刷新，则返回 true，否则返回 false。
     */
    public boolean maybeRefreshManifestBeforeLoadingNextChunk(long presentationPositionUs) {
      return PlayerEmsgHandler.this.maybeRefreshManifestBeforeLoadingNextChunk(
          presentationPositionUs);
    }

    /**
     * 当分段加载完成时调用。
     *
     * @param chunk 加载完成的分段。
     */
    public void onChunkLoadCompleted(Chunk chunk) {
      if (maxLoadedChunkEndTimeUs == C.TIME_UNSET || chunk.endTimeUs > maxLoadedChunkEndTimeUs) {
        maxLoadedChunkEndTimeUs = chunk.endTimeUs;
      }
      PlayerEmsgHandler.this.onChunkLoadCompleted(chunk);
    }

    /**
     * 当分段加载遇到错误时调用。
     *
     * @param chunk 加载遇到错误的分段。
     * @return 是否已请求清单刷新。
     */
    public boolean onChunkLoadError(Chunk chunk) {
      boolean isAfterForwardSeek =
          maxLoadedChunkEndTimeUs != C.TIME_UNSET && maxLoadedChunkEndTimeUs < chunk.startTimeUs;
      return PlayerEmsgHandler.this.onChunkLoadError(isAfterForwardSeek);
    }

    /** 释放该轨道 emsg 处理器。调用此方法后不应再使用它。 */
    public void release() {
      sampleQueue.release();
    }

    // 内部方法。

    private void parseAndDiscardSamples() {
      while (sampleQueue.isReady(/* loadingFinished= */ false)) {
        @Nullable MetadataInputBuffer inputBuffer = dequeueSample();
        if (inputBuffer == null) {
          continue;
        }
        long eventTimeUs = inputBuffer.timeUs;
        @Nullable Metadata metadata = decoder.decode(inputBuffer);
        if (metadata == null) {
          continue;
        }
        EventMessage eventMessage = (EventMessage) metadata.get(0);
        if (isPlayerEmsgEvent(eventMessage.schemeIdUri, eventMessage.value)) {
          parsePlayerEmsgEvent(eventTimeUs, eventMessage);
        }
      }
      sampleQueue.discardToRead();
    }

    @Nullable
    private MetadataInputBuffer dequeueSample() {
      buffer.clear();
      int result =
          sampleQueue.read(formatHolder, buffer, /* readFlags= */ 0, /* loadingFinished= */ false);
      if (result == C.RESULT_BUFFER_READ) {
        buffer.flip();
        return buffer;
      }
      return null;
    }

    private void parsePlayerEmsgEvent(long eventTimeUs, EventMessage eventMessage) {
      long manifestPublishTimeMsInEmsg = getManifestPublishTimeMsInEmsg(eventMessage);
      if (manifestPublishTimeMsInEmsg == C.TIME_UNSET) {
        return;
      }
      onManifestExpiredMessageEncountered(eventTimeUs, manifestPublishTimeMsInEmsg);
    }

    private void onManifestExpiredMessageEncountered(
        long eventTimeUs, long manifestPublishTimeMsInEmsg) {
      ManifestExpiryEventInfo manifestExpiryEventInfo =
          new ManifestExpiryEventInfo(eventTimeUs, manifestPublishTimeMsInEmsg);
      handler.sendMessage(handler.obtainMessage(EMSG_MANIFEST_EXPIRED, manifestExpiryEventInfo));
    }
  }

  /** 保存与清单过期事件相关的信息。 */
  private static final class ManifestExpiryEventInfo {

    public final long eventTimeUs;
    public final long manifestPublishTimeMsInEmsg;

    public ManifestExpiryEventInfo(long eventTimeUs, long manifestPublishTimeMsInEmsg) {
      this.eventTimeUs = eventTimeUs;
      this.manifestPublishTimeMsInEmsg = manifestPublishTimeMsInEmsg;
    }
  }
}