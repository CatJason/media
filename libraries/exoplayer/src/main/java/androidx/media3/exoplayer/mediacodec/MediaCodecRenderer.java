/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.mediacodec;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_DRM_SESSION_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_OPERATING_RATE_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_REUSE_NOT_IMPLEMENTED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_WORKAROUND;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_NO;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_YES_WITH_FLUSH;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_YES_WITH_RECONFIGURATION;
import static androidx.media3.exoplayer.source.SampleStream.FLAG_OMIT_SAMPLE_DATA;
import static androidx.media3.exoplayer.source.SampleStream.FLAG_PEEK;
import static androidx.media3.exoplayer.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static java.lang.Math.max;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaFormat;
import android.media.metrics.LogSessionId;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.CallSuper;
import androidx.annotation.CheckResult;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.TimedValueQueue;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.DecoderInputBuffer.InsufficientCapacityException;
import androidx.media3.exoplayer.BaseRenderer;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.DecoderReuseEvaluation.DecoderDiscardReasons;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.audio.OggOpusAudioPacketizer;
import androidx.media3.exoplayer.drm.DrmSession;
import androidx.media3.exoplayer.drm.DrmSession.DrmSessionException;
import androidx.media3.exoplayer.drm.FrameworkCryptoConfig;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.SampleStream.ReadDataResult;
import androidx.media3.exoplayer.source.SampleStream.ReadFlags;
import androidx.media3.extractor.OpusUtil;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * 一个抽象渲染器，使用 {@link MediaCodec} 解码样本以进行渲染。
 *
 * SampleStreams 中的输入媒体和 MediaCodec 的行为不受本类的控制，我们尽量做出尽可能少的假设。
 *
 * 关于输入流的假设：
 * - 第一个流可能会从开始时间之前的关键帧预加载样本。通过 replaceSampleStream 添加的后续流始终会被完全渲染。
 *
 * 关于编解码器输出的假设：
 * - 来自一个流的输出时间戳是单调递增的。
 * - 来自第一个流的输出样本中，时间戳小于声明的开始时间的样本是预加载的样本，应该被丢弃。来自后续流的输出样本始终会被完全渲染。
 * - 每个流的最后一个输出样本的时间戳大于或等于该流最大输入样本的时间戳。
 *
 * 本类明确不假设以下行为是无效的：
 * - 输入样本的时间戳可能不是单调递增的（例如，对于 B 帧）。
 * - 输入和输出样本的时间戳可能小于声明的流开始时间。
 * - 输入和输出样本的时间戳可能超过下一个流的开始时间。
 *   （上述两点意味着在流切换时，输出样本的时间戳可能会跳回）
 * - 输入和输出样本的时间戳可能不同。
 * - 输出样本的数量可能与输入样本的数量不同。
 */
@UnstableApi
public abstract class MediaCodecRenderer extends BaseRenderer {

  /**
   * 当初始化解码器失败时抛出的异常。
   */
  public static class DecoderInitializationException extends Exception {

    private static final int CUSTOM_ERROR_CODE_BASE = -50000; // 自定义错误码的基础值
    private static final int NO_SUITABLE_DECODER_ERROR = CUSTOM_ERROR_CODE_BASE + 1; // 没有合适的解码器错误
    private static final int DECODER_QUERY_ERROR = CUSTOM_ERROR_CODE_BASE + 2; // 解码器查询错误

    /** 正在初始化解码器的 MIME 类型。 */
    @Nullable public final String mimeType;

    /** 是否需要解码器支持安全输出路径。 */
    public final boolean secureDecoderRequired;

    /**
     * 初始化失败的解码器的 {@link MediaCodecInfo}。如果未找到合适的解码器，则为 null。
     */
    @Nullable public final MediaCodecInfo codecInfo;

    /** 可选的开发者可读的诊断信息字符串。可能为 null。 */
    @Nullable public final String diagnosticInfo;

    /**
     * 如果解码器初始化失败，并且作为后备的解码器也初始化失败，则为后备解码器的 {@link DecoderInitializationException}。
     * 如果没有后备解码器或未找到合适的解码器，则为 null。
     */
    @Nullable public final DecoderInitializationException fallbackDecoderInitializationException;

    public DecoderInitializationException(
        Format format, @Nullable Throwable cause, boolean secureDecoderRequired, int errorCode) {
      this(
          "解码器初始化失败: [" + errorCode + "], " + format,
          cause,
          format.sampleMimeType,
          secureDecoderRequired,
          /* mediaCodecInfo= */ null,
          buildCustomDiagnosticInfo(errorCode),
          /* fallbackDecoderInitializationException= */ null);
    }

    public DecoderInitializationException(
        Format format,
        @Nullable Throwable cause,
        boolean secureDecoderRequired,
        MediaCodecInfo mediaCodecInfo) {
      this(
          "解码器初始化失败: " + mediaCodecInfo.name + ", " + format,
          cause,
          format.sampleMimeType,
          secureDecoderRequired,
          mediaCodecInfo,
          (cause instanceof CodecException) ? ((CodecException) cause).getDiagnosticInfo() : null,
          /* fallbackDecoderInitializationException= */ null);
    }

    private DecoderInitializationException(
        @Nullable String message,
        @Nullable Throwable cause,
        @Nullable String mimeType,
        boolean secureDecoderRequired,
        @Nullable MediaCodecInfo mediaCodecInfo,
        @Nullable String diagnosticInfo,
        @Nullable DecoderInitializationException fallbackDecoderInitializationException) {
      super(message, cause);
      this.mimeType = mimeType;
      this.secureDecoderRequired = secureDecoderRequired;
      this.codecInfo = mediaCodecInfo;
      this.diagnosticInfo = diagnosticInfo;
      this.fallbackDecoderInitializationException = fallbackDecoderInitializationException;
    }

    @CheckResult
    private DecoderInitializationException copyWithFallbackException(
        DecoderInitializationException fallbackException) {
      return new DecoderInitializationException(
          getMessage(),
          getCause(),
          mimeType,
          secureDecoderRequired,
          codecInfo,
          diagnosticInfo,
          fallbackException);
    }

    private static String buildCustomDiagnosticInfo(int errorCode) {
      String sign = errorCode < 0 ? "neg_" : "";
      String packageName = "androidx.media3.exoplayer.mediacodec";
      return packageName + ".MediaCodecRenderer_" + sign + Math.abs(errorCode);
    }
  }

  /** 表示不应设置编解码器的操作速率。 */
  protected static final float CODEC_OPERATING_RATE_UNSET = -1;

  private static final String TAG = "MediaCodecRenderer";

  /**
   * 如果 {@link MediaCodec} 被热交换（即在播放过程中被替换），则在这段时间内，
   * {@link #isReady()} 将返回 true，无论新的编解码器是否已经输出了可以渲染的帧。
   *
   * <p>这允许编解码器的热交换无缝进行，而不会中断其他渲染器的播放，前提是新的编解码器能够在此时间段内解码一些帧。
   */
  private static final long MAX_CODEC_HOTSWAP_TIME_MS = 1000;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    RECONFIGURATION_STATE_NONE,
    RECONFIGURATION_STATE_WRITE_PENDING,
    RECONFIGURATION_STATE_QUEUE_PENDING
  })
  private @interface ReconfigurationState {}

  /** 没有待处理的自适应重新配置工作。 */
  private static final int RECONFIGURATION_STATE_NONE = 0;

  /** 需要将编解码器配置数据写入下一个缓冲区。 */
  private static final int RECONFIGURATION_STATE_WRITE_PENDING = 1;

  /**
   * 编解码器配置数据已写入下一个缓冲区，但该缓冲区仍需返回给编解码器。
   */
  private static final int RECONFIGURATION_STATE_QUEUE_PENDING = 2;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({DRAIN_STATE_NONE, DRAIN_STATE_SIGNAL_END_OF_STREAM, DRAIN_STATE_WAIT_END_OF_STREAM})
  private @interface DrainState {}

  /** 编解码器未被排空。 */
  private static final int DRAIN_STATE_NONE = 0;

  /** 编解码器需要被排空，但我们尚未向其发送结束流的信号。 */
  private static final int DRAIN_STATE_SIGNAL_END_OF_STREAM = 1;

  /** 编解码器需要被排空，我们正在等待其输出结束流的信号。 */
  private static final int DRAIN_STATE_WAIT_END_OF_STREAM = 2;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    DRAIN_ACTION_NONE,
    DRAIN_ACTION_FLUSH,
    DRAIN_ACTION_FLUSH_AND_UPDATE_DRM_SESSION,
    DRAIN_ACTION_REINITIALIZE
  })
  private @interface DrainAction {}

  /** 无需采取特殊操作。 */
  private static final int DRAIN_ACTION_NONE = 0;

  /** 应刷新编解码器。 */
  private static final int DRAIN_ACTION_FLUSH = 1;

  /** 应刷新编解码器并更新为使用待处理的 DRM 会话。 */
  private static final int DRAIN_ACTION_FLUSH_AND_UPDATE_DRM_SESSION = 2;

  /** 应重新初始化编解码器。 */
  private static final int DRAIN_ACTION_REINITIALIZE = 3;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    ADAPTATION_WORKAROUND_MODE_NEVER,
    ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION,
    ADAPTATION_WORKAROUND_MODE_ALWAYS
  })
  private @interface AdaptationWorkaroundMode {}

  /** 从不使用自适应解决方案。 */
  private static final int ADAPTATION_WORKAROUND_MODE_NEVER = 0;

  /**
   * 仅在相同分辨率格式之间进行自适应时使用解决方案。
   */
  private static final int ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION = 1;

  /** 在格式之间进行自适应时始终使用解决方案。 */
  private static final int ADAPTATION_WORKAROUND_MODE_ALWAYS = 2;

  /**
   * 使用自适应解决方案时需要排队的 H.264/AVC 缓冲区（参见 {@link #codecAdaptationWorkaroundMode(String)}）。
   * 它由三个带有起始码的 NAL 单元组成：Baseline 序列/图像参数集和一个 32 * 32 像素的 IDR 切片。
   * 此流可以排队以在适应新格式时强制进行分辨率更改。
   */
  private static final byte[] ADAPTATION_WORKAROUND_BUFFER =
      new byte[] {
        0, 0, 1, 103, 66, -64, 11, -38, 37, -112, 0, 0, 1, 104, -50, 15, 19, 32, 0, 0, 1, 101, -120,
        -124, 13, -50, 113, 24, -96, 0, 47, -65, 28, 49, -61, 39, 93, 120
      };

  private static final int ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT = 32;

  private final MediaCodecAdapter.Factory codecAdapterFactory;
  private final MediaCodecSelector mediaCodecSelector;
  private final boolean enableDecoderFallback;
  private final float assumedMinimumCodecOperatingRate;
  private final DecoderInputBuffer noDataBuffer;
  private final DecoderInputBuffer buffer;
  private final DecoderInputBuffer bypassSampleBuffer;
  private final BatchBuffer bypassBatchBuffer;
  private final MediaCodec.BufferInfo outputBufferInfo;
  private final ArrayDeque<OutputStreamInfo> pendingOutputStreamChanges;
  private final OggOpusAudioPacketizer oggOpusAudioPacketizer;

  @Nullable private Format inputFormat;
  @Nullable private Format outputFormat;
  @Nullable private DrmSession codecDrmSession;
  @Nullable private DrmSession sourceDrmSession;
  @Nullable private WakeupListener wakeupListener;

  /**
   * 用于 {@link MediaCodec#queueSecureInputBuffer(int, int, MediaCodec.CryptoInfo, long, int)} 的框架 {@link MediaCrypto}，
   * 用于播放加密内容。
   *
   * <p>如果正在使用框架解密（即 {@link DrmSession#getCryptoConfig() codecDrmSession.getCryptoConfig()} 返回 {@link FrameworkCryptoConfig} 的实例），
   * 则此值非空。
   *
   * <p>如果内容未加密（此时 {@link #codecDrmSession} 和 {@link #sourceDrmSession} 也将为空），
   * 或者解密过程不依赖框架支持（此时 {@link #codecDrmSession} 和 {@link #sourceDrmSession} 将非空），
   * 则此值可为空。
   */
  @Nullable private MediaCrypto mediaCrypto;

  private long renderTimeLimitMs;
  private float currentPlaybackSpeed;
  private float targetPlaybackSpeed;
  @Nullable private MediaCodecAdapter codec;
  @Nullable private Format codecInputFormat;
  @Nullable private MediaFormat codecOutputMediaFormat;
  private boolean codecOutputMediaFormatChanged;
  private float codecOperatingRate;
  @Nullable private ArrayDeque<MediaCodecInfo> availableCodecInfos;
  @Nullable private DecoderInitializationException preferredDecoderInitializationException;
  @Nullable private MediaCodecInfo codecInfo;
  private @AdaptationWorkaroundMode int codecAdaptationWorkaroundMode;
  private boolean codecNeedsSosFlushWorkaround;
  private boolean codecNeedsEosFlushWorkaround;
  private boolean codecNeedsEosOutputExceptionWorkaround;
  private boolean codecNeedsAdaptationWorkaroundBuffer;
  private boolean shouldSkipAdaptationWorkaroundOutputBuffer;
  private boolean codecNeedsEosPropagation;
  private long lastOutputBufferProcessedRealtimeMs;
  private boolean codecRegisteredOnBufferAvailableListener;
  private long codecHotswapDeadlineMs;
  private int inputIndex;
  private int outputIndex;
  @Nullable private ByteBuffer outputBuffer;
  private boolean isDecodeOnlyOutputBuffer;
  private boolean isLastOutputBuffer;
  private boolean bypassEnabled;
  private boolean bypassSampleBufferPending;
  private boolean bypassDrainAndReinitialize;
  private boolean codecReconfigured;
  private @ReconfigurationState int codecReconfigurationState;
  private @DrainState int codecDrainState;
  private @DrainAction int codecDrainAction;
  private boolean codecReceivedBuffers;
  private boolean codecReceivedEos;
  private boolean codecHasOutputMediaFormat;
  private long largestQueuedPresentationTimeUs;
  private long lastBufferInStreamPresentationTimeUs;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private boolean waitingForFirstSampleInFormat;
  private boolean pendingOutputEndOfStream;
  @Nullable private ExoPlaybackException pendingPlaybackException;
  protected DecoderCounters decoderCounters;
  private OutputStreamInfo outputStreamInfo;
  private long lastProcessedOutputBufferTimeUs;
  private boolean needToNotifyOutputFormatChangeAfterStreamChange;
  private boolean experimentalEnableProcessedStreamChangedAtStart;

  /**
   * @param trackType 渲染器处理的 {@link C.TrackType 轨道类型}。
   * @param codecAdapterFactory 用于创建 {@link MediaCodecAdapter} 实例的工厂。
   * @param mediaCodecSelector 解码器选择器。
   * @param enableDecoderFallback 是否在解码器初始化失败时启用降级到低优先级解码器。这可能导致使用效率较低或速度较慢的解码器。
   * @param assumedMinimumCodecOperatingRate 假设所有由此渲染器实例化的解码器都能隐式满足的最低编解码器操作速率（即无需显式使用 {@link MediaFormat#KEY_OPERATING_RATE} 设置操作速率）。
   */
  public MediaCodecRenderer(
      @C.TrackType int trackType,
      MediaCodecAdapter.Factory codecAdapterFactory,
      MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      float assumedMinimumCodecOperatingRate) {
    super(trackType);
    this.codecAdapterFactory = codecAdapterFactory;
    this.mediaCodecSelector = checkNotNull(mediaCodecSelector);
    this.enableDecoderFallback = enableDecoderFallback;
    this.assumedMinimumCodecOperatingRate = assumedMinimumCodecOperatingRate;
    noDataBuffer = DecoderInputBuffer.newNoDataInstance();
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    bypassSampleBuffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
    bypassBatchBuffer = new BatchBuffer();
    outputBufferInfo = new MediaCodec.BufferInfo();
    currentPlaybackSpeed = 1f;
    targetPlaybackSpeed = 1f;
    renderTimeLimitMs = C.TIME_UNSET;
    pendingOutputStreamChanges = new ArrayDeque<>();
    outputStreamInfo = OutputStreamInfo.UNSET;
    // MediaCodec 以本地字节序输出音频缓冲区：
    // 参见 https://developer.android.com/reference/android/media/MediaCodec#raw-audio-buffers
    // 并且从 MediaCodecAudioRenderer.processOutputBuffer 调用的代码也期望使用这种字节序。
    // 调用 ensureSpaceForWrite 以确保缓冲区具有非空数据，并设置预期的字节序。
    bypassBatchBuffer.ensureSpaceForWrite(/* length= */ 0);
    bypassBatchBuffer.data.order(ByteOrder.nativeOrder());
    oggOpusAudioPacketizer = new OggOpusAudioPacketizer();

    codecOperatingRate = CODEC_OPERATING_RATE_UNSET;
    codecAdaptationWorkaroundMode = ADAPTATION_WORKAROUND_MODE_NEVER;
    codecReconfigurationState = RECONFIGURATION_STATE_NONE;
    inputIndex = C.INDEX_UNSET;
    outputIndex = C.INDEX_UNSET;
    codecHotswapDeadlineMs = C.TIME_UNSET;
    largestQueuedPresentationTimeUs = C.TIME_UNSET;
    lastBufferInStreamPresentationTimeUs = C.TIME_UNSET;
    lastProcessedOutputBufferTimeUs = C.TIME_UNSET;
    lastOutputBufferProcessedRealtimeMs = C.TIME_UNSET;
    codecDrainState = DRAIN_STATE_NONE;
    codecDrainAction = DRAIN_ACTION_NONE;
    decoderCounters = new DecoderCounters();
  }

  /**
   * 设置单次 {@link #render(long, long)} 调用在排空和填充解码器时所能花费的时间限制。
   *
   * <p>此方法应在创建该类的实例后立即调用。
   *
   * @param renderTimeLimitMs 渲染时间限制，单位为毫秒，或 {@link C#TIME_UNSET} 表示无限制。
   */
  public void setRenderTimeLimitMs(long renderTimeLimitMs) {
    this.renderTimeLimitMs = renderTimeLimitMs;
  }

  @Override
  public final @AdaptiveSupport int supportsMixedMimeTypeAdaptation() {
    return ADAPTIVE_NOT_SEAMLESS;
  }

  @Override
  public final @Capabilities int supportsFormat(Format format) throws ExoPlaybackException {
    try {
      return supportsFormat(mediaCodecSelector, format);
    } catch (DecoderQueryException e) {
      throw createRendererException(e, format, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED);
    }
  }

  /**
   * 返回给定 {@link Format} 的 {@link Capabilities}。
   *
   * @param mediaCodecSelector 解码器选择器。
   * @param format 要查询的 {@link Format}。
   * @return 该 {@link Format} 的 {@link Capabilities}。
   * @throws DecoderQueryException 如果查询解码器时发生错误。
   */
  protected abstract @Capabilities int supportsFormat(
      MediaCodecSelector mediaCodecSelector, Format format) throws DecoderQueryException;

  @Override
  public final long getDurationToProgressUs(long positionUs, long elapsedRealtimeUs) {
    return getDurationToProgressUs(
        /* isOnBufferAvailableListenerRegistered= */ codecRegisteredOnBufferAvailableListener,
        positionUs,
        elapsedRealtimeUs);
  }

  /**
   * 启用渲染器在第一个流上调用 {@link #onProcessedStreamChange()}。
   *
   * <p>如果未启用，则从第二个流开始才会调用 {@link #onProcessedStreamChange()}。
   */
  public void experimentalEnableProcessedStreamChangedAtStart() {
    this.experimentalEnableProcessedStreamChangedAtStart = true;
  }

  /**
   * 返回播放必须推进的最小时间，以便 {@link #render} 调用能够取得进展。
   *
   * <p>如果 {@code Renderer} 注册了 {@link MediaCodecAdapter.OnBufferAvailableListener}，
   * 则当解码器输入和输出缓冲区可用时，{@code Renderer} 将收到通知。这些回调可能会影响计算的播放必须推进的最小时间，
   * 以确保 {@link #render} 调用能够取得进展。
   *
   * @param isOnBufferAvailableListenerRegistered {@code Renderer} 是否使用了成功注册了 {@link MediaCodecAdapter.OnBufferAvailableListener OnBufferAvailableListener} 的 {@link MediaCodecAdapter}。
   * @param positionUs 当前媒体时间，以微秒为单位，在渲染循环当前迭代开始时测量。
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} 的值，以微秒为单位，在渲染循环当前迭代开始时测量。
   * @return 播放必须推进的最小时间，以便渲染器能够取得进展。
   */
  protected long getDurationToProgressUs(
      boolean isOnBufferAvailableListenerRegistered, long positionUs, long elapsedRealtimeUs) {
    return super.getDurationToProgressUs(positionUs, elapsedRealtimeUs);
  }

  /**
   * 返回能够解码指定格式媒体的解码器列表，按优先级排序。
   *
   * @param mediaCodecSelector 解码器选择器。
   * @param format 需要解码器的 {@link Format}。
   * @param requiresSecureDecoder 是否需要安全解码器。
   * @return 与解码器对应的 {@link MediaCodecInfo} 列表。可能为空。
   * @throws DecoderQueryException 如果查询解码器时发生错误，则抛出此异常。
   */
  protected abstract List<MediaCodecInfo> getDecoderInfos(
      MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder)
      throws DecoderQueryException;

  /**
   * 返回用于创建和配置 {@link MediaCodec} 以解码给定 {@link Format} 进行播放的 {@link MediaCodecAdapter.Configuration}。
   *
   * @param codecInfo 正在配置的 {@link MediaCodec} 的相关信息。
   * @param format 正在为编解码器配置的 {@link Format}。
   * @param crypto 对于受 DRM 保护的播放，用于解密的 {@link MediaCrypto}。
   * @param codecOperatingRate 编解码器操作速率，或 {@link #CODEC_OPERATING_RATE_UNSET} 表示不应设置操作速率。
   * @return 调用 {@link MediaCodec#configure} 所需的参数。
   */
  protected abstract MediaCodecAdapter.Configuration getMediaCodecConfiguration(
      MediaCodecInfo codecInfo,
      Format format,
      @Nullable MediaCrypto crypto,
      float codecOperatingRate);

  protected final void maybeInitCodecOrBypass() throws ExoPlaybackException {
    if (codec != null || bypassEnabled || inputFormat == null) {
      // We have a codec, are bypassing it, or don't have a format to decide how to render.
      return;
    }
    Format inputFormat = this.inputFormat;

    if (isBypassPossible(inputFormat)) {
      initBypass(inputFormat);
      return;
    }

    setCodecDrmSession(sourceDrmSession);
    if (codecDrmSession == null || initMediaCryptoIfDrmSessionReady()) {
      try {
        boolean mediaCryptoRequiresSecureDecoder =
            codecDrmSession != null
                && (codecDrmSession.getState() == DrmSession.STATE_OPENED
                    || codecDrmSession.getState() == DrmSession.STATE_OPENED_WITH_KEYS)
                && codecDrmSession.requiresSecureDecoder(
                    checkStateNotNull(inputFormat.sampleMimeType));
        maybeInitCodecWithFallback(mediaCrypto, mediaCryptoRequiresSecureDecoder);
      } catch (DecoderInitializationException e) {
        throw createRendererException(
            e, inputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
      }
    }
    if (mediaCrypto != null && codec == null) {
      // mediaCrypto was created, but a codec wasn't, so release the mediaCrypto before returning.
      mediaCrypto.release();
      mediaCrypto = null;
    }
  }

  /**
   * 返回输入格式的缓冲区是否可以在不使用编解码器的情况下被处理。
   *
   * <p>此方法通过检查渲染器能力和 DRM 保护来返回是否可能启用绕过模式。
   *
   * @param format 输入的 {@link Format}。
   * @return 是否可能绕过 {@link MediaCodec} 进行播放。
   */
  protected final boolean isBypassPossible(Format format) {
    return sourceDrmSession == null && shouldUseBypass(format);
  }

  /**
   * 返回输入格式的缓冲区是否可以在不使用编解码器的情况下被处理。
   *
   * <p>此方法仅在内容未受 DRM 保护时调用，因为如果内容受 DRM 保护，则永远无法使用绕过模式。
   *
   * @param format 输入的 {@link Format}。
   * @return 是否支持绕过 {@link MediaCodec} 进行播放。
   */
  protected boolean shouldUseBypass(Format format) {
    return false;
  }

  protected boolean shouldInitCodec(MediaCodecInfo codecInfo) {
    return true;
  }

  /**
   * 返回渲染器是否需要重新初始化编解码器，可能是由于设备能力的变化所致。
   */
  protected boolean shouldReinitCodec() {
    return false;
  }

  /**
   * 返回编解码器是否需要渲染器直接传播结束流信号，而不是通过向编解码器排队一个结束流缓冲区来实现。
   */
  protected boolean getCodecNeedsEosPropagation() {
    return false;
  }

  /** 返回渲染器是否启用了绕过模式。 */
  protected final boolean isBypassEnabled() {
    return bypassEnabled;
  }

  /**
   * 设置一个异常，以便在渲染时重新抛出。
   *
   * @param exception 要重新抛出的异常。
   */
  protected final void setPendingPlaybackException(ExoPlaybackException exception) {
    pendingPlaybackException = exception;
  }

  /**
   * 更新指定输出缓冲区时间戳的输出格式，如果发生更改，则调用 {@link #onOutputFormatChanged}。
   *
   * <p>子类应仅在未从解码器出队缓冲区的模式下调用此方法，例如使用视频隧道时。
   *
   * @throws ExoPlaybackException 如果由于输出格式更改而发生错误，则抛出此异常。
   */
  protected final void updateOutputFormatForTime(long presentationTimeUs)
      throws ExoPlaybackException {
    boolean outputFormatChanged = false;
    @Nullable Format format = outputStreamInfo.formatQueue.pollFloor(presentationTimeUs);
    if (format == null
        && needToNotifyOutputFormatChangeAfterStreamChange
        && codecOutputMediaFormat != null) {
      // After a stream change or after the initial start, there should be an input format change,
      // which we've not found. Check the Format queue in case the corresponding presentation
      // timestamp is greater than presentationTimeUs, which can happen for some codecs
      // [Internal ref: b/162719047 and https://github.com/google/ExoPlayer/issues/8594].
      format = outputStreamInfo.formatQueue.pollFirst();
    }
    if (format != null) {
      outputFormat = format;
      outputFormatChanged = true;
    }
    if (outputFormatChanged || (codecOutputMediaFormatChanged && outputFormat != null)) {
      onOutputFormatChanged(checkNotNull(outputFormat), codecOutputMediaFormat);
      codecOutputMediaFormatChanged = false;
      needToNotifyOutputFormatChangeAfterStreamChange = false;
    }
  }

  @Nullable
  protected final MediaCodecAdapter getCodec() {
    return codec;
  }

  @Nullable
  protected final MediaFormat getCodecOutputMediaFormat() {
    return codecOutputMediaFormat;
  }

  @Nullable
  protected final MediaCodecInfo getCodecInfo() {
    return codecInfo;
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    decoderCounters = new DecoderCounters();
  }

  @Override
  protected void onStreamChanged(
      Format[] formats,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId)
      throws ExoPlaybackException {
    if (outputStreamInfo.streamOffsetUs == C.TIME_UNSET) {
      // 这是第一个流。
      setOutputStreamInfo(
          new OutputStreamInfo(
              /* previousStreamLastBufferTimeUs= */ C.TIME_UNSET, startPositionUs, offsetUs));
      if (experimentalEnableProcessedStreamChangedAtStart) {
        onProcessedStreamChange();
      }
    } else if (pendingOutputStreamChanges.isEmpty()
        && (largestQueuedPresentationTimeUs == C.TIME_UNSET
        || (lastProcessedOutputBufferTimeUs != C.TIME_UNSET
        && lastProcessedOutputBufferTimeUs >= largestQueuedPresentationTimeUs))) {
      // 所有先前的流从未排队任何样本，或者已经完全输出。
      setOutputStreamInfo(
          new OutputStreamInfo(
              /* previousStreamLastBufferTimeUs= */ C.TIME_UNSET, startPositionUs, offsetUs));
      if (outputStreamInfo.streamOffsetUs != C.TIME_UNSET) {
        onProcessedStreamChange();
      }
    } else {
      pendingOutputStreamChanges.add(
          new OutputStreamInfo(largestQueuedPresentationTimeUs, startPositionUs, offsetUs));
    }
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    inputStreamEnded = false; // 重置输入流结束标志
    outputStreamEnded = false; // 重置输出流结束标志
    pendingOutputEndOfStream = false; // 重置待处理的输出流结束标志
    if (bypassEnabled) {
      bypassBatchBuffer.clear(); // 清空绕过模式的批量缓冲区
      bypassSampleBuffer.clear(); // 清空绕过模式的样本缓冲区
      bypassSampleBufferPending = false; // 重置绕过模式样本缓冲区待处理标志
      oggOpusAudioPacketizer.reset(); // 重置 Ogg Opus 音频打包器
    } else {
      flushOrReinitializeCodec(); // 刷新或重新初始化编解码器
    }
    // 如果输入端的格式更改仍未传播到输出端，则需要在下次读取缓冲区时排队一个格式。
    // 这是因为在位置重置后，我们可能不会读取新的输入格式。
    if (outputStreamInfo.formatQueue.size() > 0) {
      waitingForFirstSampleInFormat = true; // 设置等待格式中的第一个样本标志
    }
    outputStreamInfo.formatQueue.clear(); // 清空输出流信息中的格式队列
    pendingOutputStreamChanges.clear(); // 清空待处理的输出流更改列表
  }

  @Override
  public void setPlaybackSpeed(float currentPlaybackSpeed, float targetPlaybackSpeed)
      throws ExoPlaybackException {
    this.currentPlaybackSpeed = currentPlaybackSpeed;
    this.targetPlaybackSpeed = targetPlaybackSpeed;
    updateCodecOperatingRate(codecInputFormat);
  }

  @Override
  protected void onDisabled() {
    inputFormat = null;
    setOutputStreamInfo(OutputStreamInfo.UNSET);
    pendingOutputStreamChanges.clear();
    flushOrReleaseCodec();
  }

  @Override
  protected void onReset() {
    try {
      disableBypass();
      releaseCodec();
    } finally {
      setSourceDrmSession(null);
    }
  }

  private void disableBypass() {
    bypassDrainAndReinitialize = false;
    bypassBatchBuffer.clear();
    bypassSampleBuffer.clear();
    bypassSampleBufferPending = false;
    bypassEnabled = false;
    oggOpusAudioPacketizer.reset();
  }

  protected void releaseCodec() {
    try {
      if (codec != null) {
        codec.release();
        decoderCounters.decoderReleaseCount++;
        onCodecReleased(checkNotNull(codecInfo).name);
      }
    } finally {
      codec = null;
      try {
        if (mediaCrypto != null) {
          mediaCrypto.release();
        }
      } finally {
        mediaCrypto = null;
        setCodecDrmSession(null);
        resetCodecStateForRelease();
      }
    }
  }

  @Override
  protected void onStarted() {
    // Do nothing. Overridden to remove throws clause.
  }

  @Override
  protected void onStopped() {
    // Do nothing. Overridden to remove throws clause.
  }

  @Override
  public void handleMessage(@MessageType int messageType, @Nullable Object message)
      throws ExoPlaybackException {
    if (messageType == MSG_SET_WAKEUP_LISTENER) {
      this.wakeupListener = (WakeupListener) message;
    } else {
      super.handleMessage(messageType, message);
    }
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (pendingOutputEndOfStream) {
      pendingOutputEndOfStream = false; // 重置待处理的输出流结束标志
      processEndOfStream(); // 处理流结束逻辑
    }
    if (pendingPlaybackException != null) {
      ExoPlaybackException playbackException = pendingPlaybackException; // 获取待处理的播放异常
      pendingPlaybackException = null; // 重置待处理的播放异常
      throw playbackException; // 抛出播放异常
    }

    try {
      if (outputStreamEnded) {
        renderToEndOfStream(); // 如果输出流已结束，则渲染到流结束
        return;
      }
      if (inputFormat == null && !readSourceOmittingSampleData(FLAG_REQUIRE_FORMAT)) {
        // 如果仍然没有格式且无法在没有格式的情况下取得进展，则返回
        return;
      }
      // 我们已经有一个格式
      maybeInitCodecOrBypass(); // 可能初始化编解码器或启用绕过模式
      if (bypassEnabled) {
        TraceUtil.beginSection("bypassRender"); // 开始跟踪绕过模式渲染
        while (bypassRender(positionUs, elapsedRealtimeUs)) {} // 执行绕过模式渲染
        TraceUtil.endSection(); // 结束跟踪
      } else if (codec != null) {
        long renderStartTimeMs = getClock().elapsedRealtime(); // 获取渲染开始时间
        TraceUtil.beginSection("drainAndFeed"); // 开始跟踪排空和填充缓冲区
        while (drainOutputBuffer(positionUs, elapsedRealtimeUs)
            && shouldContinueRendering(renderStartTimeMs)) {} // 排空输出缓冲区
        while (feedInputBuffer() && shouldContinueRendering(renderStartTimeMs)) {} // 填充输入缓冲区
        TraceUtil.endSection(); // 结束跟踪
      } else {
        decoderCounters.skippedInputBufferCount += skipSource(positionUs); // 跳过源数据并更新计数器
        // 尽管没有编解码器，我们仍需要读取格式更改，以便更新 drmSession，
        // 并且在编解码器初始化时拥有最新的格式。我们可能也会到达流的末尾。
        // 使用 FLAG_PEEK 是因为我们不想让源数据前进超过 skipSource 已经完成的范围。
        readSourceOmittingSampleData(FLAG_PEEK);
      }
      decoderCounters.ensureUpdated(); // 确保计数器已更新
    } catch (MediaCodec.CryptoException e) {
      throw createRendererException(
          e, inputFormat, Util.getErrorCodeForMediaDrmErrorCode(e.getErrorCode())); // 抛出加密异常
    } catch (IllegalStateException e) {
      if (isMediaCodecException(e)) {
        onCodecError(e); // 处理编解码器错误
        boolean isRecoverable =
            (e instanceof CodecException) && ((CodecException) e).isRecoverable(); // 判断是否可恢复
        if (isRecoverable) {
          releaseCodec(); // 如果可恢复，则释放编解码器
        }
        MediaCodecDecoderException exception = createDecoderException(e, getCodecInfo()); // 创建解码器异常
        @PlaybackException.ErrorCode
        int errorCode =
            exception.errorCode == CodecException.ERROR_RECLAIMED
                ? PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED
                : PlaybackException.ERROR_CODE_DECODING_FAILED; // 根据异常类型设置错误码
        throw createRendererException(exception, inputFormat, isRecoverable, errorCode); // 抛出渲染器异常
      }
      throw e;
    }
  }

  /**
   * 刷新编解码器。如果无法刷新，则编解码器将被释放并重新实例化。
   * 如果编解码器为 {@code null}，则此方法不执行任何操作。
   *
   * <p>此方法的实现会调用 {@link #flushOrReleaseCodec()}，如果需要重新实例化编解码器，则调用 {@link
   * #maybeInitCodecOrBypass()}。
   *
   * @return 编解码器是否被释放并重新初始化，而不是被刷新。
   * @throws ExoPlaybackException 如果重新实例化编解码器时发生错误。
   */
  protected final boolean flushOrReinitializeCodec() throws ExoPlaybackException {
    boolean released = flushOrReleaseCodec(); // 刷新或释放编解码器
    if (released) {
      maybeInitCodecOrBypass(); // 如果需要，则重新初始化编解码器或启用绕过模式
    }
    return released; // 返回编解码器是否被释放并重新初始化
  }

  /**
   * 刷新编解码器。如果无法刷新，则编解码器将被释放。如果编解码器为 {@code null}，则此方法不执行任何操作。
   *
   * @return 编解码器是否被释放。
   */
  protected boolean flushOrReleaseCodec() {
    if (codec == null) {
      return false; // 如果编解码器为 null，则返回 false
    }
    if (codecDrainAction == DRAIN_ACTION_REINITIALIZE
        || (codecNeedsSosFlushWorkaround && !codecHasOutputMediaFormat)
        || (codecNeedsEosFlushWorkaround && codecReceivedEos)) {
      releaseCodec(); // 如果需要重新初始化或满足特定条件，则释放编解码器
      return true; // 返回 true，表示编解码器被释放
    }
    if (codecDrainAction == DRAIN_ACTION_FLUSH_AND_UPDATE_DRM_SESSION) {
      checkState(Util.SDK_INT >= 23); // 检查 SDK 版本是否 >= 23
      // 为了使 lint 检查通过（它无法仅通过 checkState 调用来理解）
      if (Util.SDK_INT >= 23) {
        try {
          updateDrmSessionV23(); // 更新 DRM 会话
        } catch (ExoPlaybackException e) {
          Log.w(TAG, "Failed to update the DRM session, releasing the codec instead.", e);
          releaseCodec(); // 如果更新 DRM 会话失败，则释放编解码器
          return true; // 返回 true，表示编解码器被释放
        }
      }
    }
    flushCodec(); // 刷新编解码器
    return false; // 返回 false，表示编解码器未被释放
  }

  /** 刷新编解码器。 */
  private void flushCodec() {
    try {
      checkStateNotNull(codec).flush(); // 检查编解码器不为 null 并刷新编解码器
    } finally {
      resetCodecStateForFlush(); // 无论是否发生异常，都重置编解码器状态
    }
  }

  /** 在编解码器刷新后重置渲染器的内部状态。 */
  @CallSuper
  protected void resetCodecStateForFlush() {
    resetInputBuffer(); // 重置输入缓冲区
    resetOutputBuffer(); // 重置输出缓冲区
    codecHotswapDeadlineMs = C.TIME_UNSET; // 重置编解码器热交换截止时间
    codecReceivedEos = false; // 重置编解码器是否收到流结束信号
    lastOutputBufferProcessedRealtimeMs = C.TIME_UNSET; // 重置最后处理的输出缓冲区的实时时间
    codecReceivedBuffers = false; // 重置编解码器是否已接收缓冲区
    codecNeedsAdaptationWorkaroundBuffer = false; // 重置编解码器是否需要自适应解决方案缓冲区
    shouldSkipAdaptationWorkaroundOutputBuffer = false; // 重置是否应跳过自适应解决方案的输出缓冲区
    isDecodeOnlyOutputBuffer = false; // 重置输出缓冲区是否仅用于解码
    isLastOutputBuffer = false; // 重置是否为最后一个输出缓冲区
    largestQueuedPresentationTimeUs = C.TIME_UNSET; // 重置最大排队的展示时间
    lastBufferInStreamPresentationTimeUs = C.TIME_UNSET; // 重置流中最后一个缓冲区的展示时间
    lastProcessedOutputBufferTimeUs = C.TIME_UNSET; // 重置最后处理的输出缓冲区的时间
    codecDrainState = DRAIN_STATE_NONE; // 重置编解码器排空状态
    codecDrainAction = DRAIN_ACTION_NONE; // 重置编解码器排空操作
    // 在刷新前不久发送的重新配置数据可能尚未被解码器处理。
    // 如果编解码器已被重新配置，我们总是再次发送重新配置数据，以确保其被处理。
    codecReconfigurationState =
        codecReconfigured ? RECONFIGURATION_STATE_WRITE_PENDING : RECONFIGURATION_STATE_NONE; // 重置编解码器重新配置状态
  }

  /**
   * 在编解码器释放后重置渲染器的内部状态。
   *
   * <p>请注意，此方法仅需重置在 {@link #resetCodecStateForFlush()} 之外被修改的状态变量。
   */
  @CallSuper
  protected void resetCodecStateForRelease() {
    resetCodecStateForFlush();

    pendingPlaybackException = null;
    availableCodecInfos = null;
    codecInfo = null;
    codecInputFormat = null;
    codecOutputMediaFormat = null;
    codecOutputMediaFormatChanged = false;
    codecHasOutputMediaFormat = false;
    codecOperatingRate = CODEC_OPERATING_RATE_UNSET;
    codecAdaptationWorkaroundMode = ADAPTATION_WORKAROUND_MODE_NEVER;
    codecNeedsSosFlushWorkaround = false;
    codecNeedsEosFlushWorkaround = false;
    codecNeedsEosOutputExceptionWorkaround = false;
    codecNeedsEosPropagation = false;
    codecRegisteredOnBufferAvailableListener = false;
    codecReconfigured = false;
    codecReconfigurationState = RECONFIGURATION_STATE_NONE;
  }

  protected MediaCodecDecoderException createDecoderException(
      Throwable cause, @Nullable MediaCodecInfo codecInfo) {
    return new MediaCodecDecoderException(cause, codecInfo);
  }

  /**
   * 在不需要样本数据时从源中读取数据。如果读取到格式或流结束缓冲区，将在调用返回前处理。
   *
   * @param readFlags 额外的 {@link ReadFlags}。{@link SampleStream#FLAG_OMIT_SAMPLE_DATA} 已在内部添加，因此无需传递。
   * @return 是否读取并处理了格式。
   */
  private boolean readSourceOmittingSampleData(@SampleStream.ReadFlags int readFlags)
      throws ExoPlaybackException {
    FormatHolder formatHolder = getFormatHolder();
    noDataBuffer.clear();
    @ReadDataResult
    int result = readSource(formatHolder, noDataBuffer, readFlags | FLAG_OMIT_SAMPLE_DATA);
    if (result == C.RESULT_FORMAT_READ) {
      onInputFormatChanged(formatHolder);
      return true;
    } else if (result == C.RESULT_BUFFER_READ && noDataBuffer.isEndOfStream()) {
      inputStreamEnded = true;
      processEndOfStream();
    }
    return false;
  }

  /**
   * 检查 {@link #codecDrmSession} 是否已准备好进行播放，如果需要则初始化 {@link #mediaCrypto}。
   *
   * @return 如果应继续编解码器初始化，则返回 {@code true}；如果应中止，则返回 {@code false}。
   */
  @RequiresNonNull("this.codecDrmSession")
  private boolean initMediaCryptoIfDrmSessionReady() throws ExoPlaybackException {
    checkState(mediaCrypto == null); // 检查 mediaCrypto 是否为 null
    DrmSession codecDrmSession = this.codecDrmSession; // 获取当前的 DRM 会话
    @Nullable CryptoConfig cryptoConfig = codecDrmSession.getCryptoConfig(); // 获取加密配置
    if (FrameworkCryptoConfig.WORKAROUND_DEVICE_NEEDS_KEYS_TO_CONFIGURE_CODEC
        && cryptoConfig instanceof FrameworkCryptoConfig) {
      @DrmSession.State int drmSessionState = codecDrmSession.getState(); // 获取 DRM 会话状态
      if (drmSessionState == DrmSession.STATE_ERROR) {
        DrmSessionException drmSessionException =
            Assertions.checkNotNull(codecDrmSession.getError()); // 获取 DRM 会话错误
        throw createRendererException(
            drmSessionException, inputFormat, drmSessionException.errorCode); // 抛出渲染器异常
      } else if (drmSessionState != DrmSession.STATE_OPENED_WITH_KEYS) {
        // 等待密钥
        return false;
      }
    }
    if (cryptoConfig == null) {
      @Nullable DrmSessionException drmError = codecDrmSession.getError(); // 获取 DRM 会话错误
      if (drmError != null) {
        // 暂时继续。如果新的输入格式导致会话被替换而未使用，我们可能可以避免失败。
        return true;
      } else {
        // DRM 会话尚未打开
        return false;
      }
    } else if (cryptoConfig instanceof FrameworkCryptoConfig) {
      FrameworkCryptoConfig frameworkCryptoConfig = (FrameworkCryptoConfig) cryptoConfig;
      try {
        mediaCrypto = new MediaCrypto(frameworkCryptoConfig.uuid, frameworkCryptoConfig.sessionId); // 初始化 MediaCrypto
      } catch (MediaCryptoException e) {
        throw createRendererException(
            e, inputFormat, PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR); // 抛出渲染器异常
      }
    }
    return true; // 返回 true，表示初始化成功
  }

  private void maybeInitCodecWithFallback(
      @Nullable MediaCrypto crypto, boolean mediaCryptoRequiresSecureDecoder)
      throws DecoderInitializationException {
    Format inputFormat = checkNotNull(this.inputFormat);
    if (availableCodecInfos == null) {
      try {
        List<MediaCodecInfo> allAvailableCodecInfos =
            getAvailableCodecInfos(mediaCryptoRequiresSecureDecoder);
        availableCodecInfos = new ArrayDeque<>();
        if (enableDecoderFallback) {
          availableCodecInfos.addAll(allAvailableCodecInfos);
        } else if (!allAvailableCodecInfos.isEmpty()) {
          availableCodecInfos.add(allAvailableCodecInfos.get(0));
        }
        preferredDecoderInitializationException = null;
      } catch (DecoderQueryException e) {
        throw new DecoderInitializationException(
            inputFormat,
            e,
            mediaCryptoRequiresSecureDecoder,
            DecoderInitializationException.DECODER_QUERY_ERROR);
      }
    }

    if (availableCodecInfos.isEmpty()) {
      throw new DecoderInitializationException(
          inputFormat,
          /* cause= */ null,
          mediaCryptoRequiresSecureDecoder,
          DecoderInitializationException.NO_SUITABLE_DECODER_ERROR);
    }

    ArrayDeque<MediaCodecInfo> availableCodecInfos = checkNotNull(this.availableCodecInfos);
    while (codec == null) {
      MediaCodecInfo codecInfo = checkNotNull(availableCodecInfos.peekFirst()); // 获取列表中的第一个可用编解码器信息
      if (!shouldInitCodec(codecInfo)) {
        return; // 如果不应初始化该编解码器，则直接返回
      }
      try {
        initCodec(codecInfo, crypto); // 尝试初始化编解码器
      } catch (Exception e) {
        Log.w(TAG, "Failed to initialize decoder: " + codecInfo, e); // 记录初始化失败的日志
        // 该编解码器初始化失败，因此回退到列表中的下一个编解码器（如果有的话）。除非发生格式更改或渲染器被禁用并重新启用，否则不会再次尝试使用此编解码器。
        availableCodecInfos.removeFirst(); // 从列表中移除失败的编解码器信息
        DecoderInitializationException exception =
            new DecoderInitializationException(
                inputFormat, e, mediaCryptoRequiresSecureDecoder, codecInfo); // 创建解码器初始化异常
        onCodecError(exception); // 处理编解码器错误
        if (preferredDecoderInitializationException == null) {
          preferredDecoderInitializationException = exception; // 如果首选解码器初始化异常为空，则设置为当前异常
        } else {
          preferredDecoderInitializationException =
              preferredDecoderInitializationException.copyWithFallbackException(exception); // 否则，将当前异常作为回退异常附加
        }
        if (availableCodecInfos.isEmpty()) {
          throw preferredDecoderInitializationException; // 如果没有更多可用的编解码器，则抛出异常
        }
      }
    }

    this.availableCodecInfos = null;
  }

  private List<MediaCodecInfo> getAvailableCodecInfos(boolean mediaCryptoRequiresSecureDecoder)
      throws DecoderQueryException {
    Format inputFormat = checkNotNull(this.inputFormat); // 检查输入格式不为 null
    List<MediaCodecInfo> codecInfos =
        getDecoderInfos(mediaCodecSelector, inputFormat, mediaCryptoRequiresSecureDecoder); // 获取支持的解码器信息
    if (codecInfos.isEmpty() && mediaCryptoRequiresSecureDecoder) {
      // DRM 会话表示需要安全解码器，但设备没有。假设 supportsFormat 表明支持播放的媒体，我们知道它不需要安全输出路径。
      // 大多数 CDM 实现允许在这种情况下使用非安全解码器进行播放，因此我们尝试继续。
      codecInfos =
          getDecoderInfos(mediaCodecSelector, inputFormat, /* requiresSecureDecoder= */ false); // 获取非安全解码器信息
      if (!codecInfos.isEmpty()) {
        Log.w(
            TAG,
            "Drm session requires secure decoder for "
                + inputFormat.sampleMimeType
                + ", but no secure decoder available. Trying to proceed with "
                + codecInfos
                + "."); // 记录警告日志
      }
    }
    return codecInfos; // 返回可用的解码器信息列表
  }

  /** 配置不使用编解码器的渲染模式。 */
  private void initBypass(Format format) {
    disableBypass(); // 在两种绕过格式之间切换时，先禁用绕过模式。

    String mimeType = format.sampleMimeType; // 获取格式的 MIME 类型
    if (!MimeTypes.AUDIO_AAC.equals(mimeType)
        && !MimeTypes.AUDIO_MPEG.equals(mimeType)
        && !MimeTypes.AUDIO_OPUS.equals(mimeType)) {
      // TODO(b/154746451): 在非卸载模式下，批处理会导致丢帧。
      bypassBatchBuffer.setMaxSampleCount(1); // 设置最大样本数量为 1
    } else {
      bypassBatchBuffer.setMaxSampleCount(BatchBuffer.DEFAULT_MAX_SAMPLE_COUNT); // 使用默认的最大样本数量
    }
    bypassEnabled = true; // 启用绕过模式
  }

  private void initCodec(MediaCodecInfo codecInfo, @Nullable MediaCrypto crypto) throws Exception {
    Format inputFormat = checkNotNull(this.inputFormat);
    long codecInitializingTimestamp;
    long codecInitializedTimestamp;
    String codecName = codecInfo.name;
    float codecOperatingRate =
        Util.SDK_INT < 23
            ? CODEC_OPERATING_RATE_UNSET
            : getCodecOperatingRateV23(targetPlaybackSpeed, inputFormat, getStreamFormats());
    if (codecOperatingRate <= assumedMinimumCodecOperatingRate) {
      codecOperatingRate = CODEC_OPERATING_RATE_UNSET;
    }
    onReadyToInitializeCodec(inputFormat);
    codecInitializingTimestamp = getClock().elapsedRealtime();
    MediaCodecAdapter.Configuration configuration =
        getMediaCodecConfiguration(codecInfo, inputFormat, crypto, codecOperatingRate);
    if (Util.SDK_INT >= 31) {
      Api31.setLogSessionIdToMediaCodecFormat(configuration, getPlayerId());
    }
    try {
      TraceUtil.beginSection("createCodec:" + codecName);
      codec = codecAdapterFactory.createAdapter(configuration);
      codecRegisteredOnBufferAvailableListener =
          codec.registerOnBufferAvailableListener(new MediaCodecRendererCodecAdapterListener());
    } finally {
      TraceUtil.endSection();
    }
    codecInitializedTimestamp = getClock().elapsedRealtime();

    if (!codecInfo.isFormatSupported(inputFormat)) {
      Log.w(
          TAG,
          Util.formatInvariant(
              "Format exceeds selected codec's capabilities [%s, %s]",
              Format.toLogString(inputFormat), codecName));
    }

    this.codecInfo = codecInfo;
    this.codecOperatingRate = codecOperatingRate;
    codecInputFormat = inputFormat;
    codecAdaptationWorkaroundMode = codecAdaptationWorkaroundMode(codecName);
    codecNeedsSosFlushWorkaround = codecNeedsSosFlushWorkaround(codecName);
    codecNeedsEosFlushWorkaround = codecNeedsEosFlushWorkaround(codecName);
    codecNeedsEosOutputExceptionWorkaround = codecNeedsEosOutputExceptionWorkaround(codecName);
    codecNeedsEosPropagation =
        codecNeedsEosPropagationWorkaround(codecInfo) || getCodecNeedsEosPropagation();
    if (checkNotNull(codec).needsReconfiguration()) {
      this.codecReconfigured = true;
      this.codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      this.codecNeedsAdaptationWorkaroundBuffer =
          codecAdaptationWorkaroundMode != ADAPTATION_WORKAROUND_MODE_NEVER;
    }

    if (getState() == STATE_STARTED) {
      codecHotswapDeadlineMs = getClock().elapsedRealtime() + MAX_CODEC_HOTSWAP_TIME_MS;
    }

    decoderCounters.decoderInitCount++;
    long elapsed = codecInitializedTimestamp - codecInitializingTimestamp;
    onCodecInitialized(codecName, configuration, codecInitializedTimestamp, elapsed);
  }

  private boolean shouldContinueRendering(long renderStartTimeMs) {
    return renderTimeLimitMs == C.TIME_UNSET
        || getClock().elapsedRealtime() - renderStartTimeMs < renderTimeLimitMs;
  }

  private boolean hasOutputBuffer() {
    return outputIndex >= 0;
  }

  private void resetInputBuffer() {
    inputIndex = C.INDEX_UNSET;
    buffer.data = null;
  }

  private void resetOutputBuffer() {
    outputIndex = C.INDEX_UNSET;
    outputBuffer = null;
  }

  private void setSourceDrmSession(@Nullable DrmSession session) {
    DrmSession.replaceSession(sourceDrmSession, session);
    sourceDrmSession = session;
  }

  private void setCodecDrmSession(@Nullable DrmSession session) {
    DrmSession.replaceSession(codecDrmSession, session);
    codecDrmSession = session;
  }

  /**
   * @return 是否可能继续输入更多数据。
   * @throws ExoPlaybackException 如果在输入数据时发生错误。
   */
  private boolean feedInputBuffer() throws ExoPlaybackException {
    if (codec == null || codecDrainState == DRAIN_STATE_WAIT_END_OF_STREAM || inputStreamEnded) {
      return false;
    }
    if (codecDrainState == DRAIN_STATE_NONE && shouldReinitCodec()) {
      drainAndReinitializeCodec();
    }

    MediaCodecAdapter codec = checkNotNull(this.codec);
    if (inputIndex < 0) {
      inputIndex = codec.dequeueInputBufferIndex();
      if (inputIndex < 0) {
        return false;
      }
      buffer.data = codec.getInputBuffer(inputIndex);
      buffer.clear();
    }

    if (codecDrainState == DRAIN_STATE_SIGNAL_END_OF_STREAM) {
      // 我们需要重新初始化编解码器。向现有编解码器发送一个流结束信号，以便在释放它之前输出所有剩余的缓冲区。
      if (codecNeedsEosPropagation) {
        // Do nothing.
      } else {
        codecReceivedEos = true;
        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        resetInputBuffer();
      }
      codecDrainState = DRAIN_STATE_WAIT_END_OF_STREAM;
      return false;
    }

    if (codecNeedsAdaptationWorkaroundBuffer) {
      codecNeedsAdaptationWorkaroundBuffer = false;
      checkNotNull(buffer.data).put(ADAPTATION_WORKAROUND_BUFFER);
      codec.queueInputBuffer(inputIndex, 0, ADAPTATION_WORKAROUND_BUFFER.length, 0, 0);
      resetInputBuffer();
      codecReceivedBuffers = true;
      return true;
    }

    // 对于自适应重新配置，解码器期望所有重新配置数据都在包含新格式中第一帧的缓冲区开头提供。
    if (codecReconfigurationState == RECONFIGURATION_STATE_WRITE_PENDING) {
      for (int i = 0; i < checkNotNull(codecInputFormat).initializationData.size(); i++) {
        byte[] data = codecInputFormat.initializationData.get(i);
        checkNotNull(buffer.data).put(data);
      }
      codecReconfigurationState = RECONFIGURATION_STATE_QUEUE_PENDING;
    }
    int adaptiveReconfigurationBytes = checkNotNull(buffer.data).position();

    FormatHolder formatHolder = getFormatHolder();

    @SampleStream.ReadDataResult int result;
    try {
      result = readSource(formatHolder, buffer, /* readFlags= */ 0);
    } catch (InsufficientCapacityException e) {
      onCodecError(e);
      // 通过读取但不包含其数据的方式跳过过大的样本。然后刷新编解码器，以便从下一个关键帧恢复渲染。
      readSourceOmittingSampleData(/* readFlags= */ 0);
      flushCodec();
      return true;
    }

    if (result == C.RESULT_NOTHING_READ) {
      if (hasReadStreamToEnd()) {
        // 通知输出队列最后一个缓冲区的时间戳。
        lastBufferInStreamPresentationTimeUs = largestQueuedPresentationTimeUs;
      }
      return false;
    }
    if (result == C.RESULT_FORMAT_READ) {
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // 我们连续接收到两个格式。清除当前缓冲区中与第一个格式相关的所有重新配置数据。
        buffer.clear();
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      onInputFormatChanged(formatHolder);
      return true;
    }

    // We've read a buffer.
    if (buffer.isEndOfStream()) {
      lastBufferInStreamPresentationTimeUs = largestQueuedPresentationTimeUs;
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // 我们在流结束之前立即接收到一个新格式。
        // 我们需要从当前缓冲区中清除相应的重新配置数据，但如果有后续缓冲区（例如，如果用户向后跳转），则需要将其重新写入。
        buffer.clear();
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      inputStreamEnded = true;
      if (!codecReceivedBuffers) {
        processEndOfStream();
        return false;
      }
      if (codecNeedsEosPropagation) {
        // Do nothing.
      } else {
        codecReceivedEos = true;
        codec.queueInputBuffer(
            inputIndex,
            /* offset= */ 0,
            /* size= */ 0,
            /* presentationTimeUs= */ 0,
            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        resetInputBuffer();
      }
      return false;
    }

    // 此逻辑适用于在从源中正常消费样本期间需要刷新或重新实例化解码器的情况（即，没有相应的 Renderer.enable 或 Renderer.resetPosition 调用）。
    // 这对于某些旧版行为和解决方案是必要的，
    // 例如在 API 级别低于引入 MediaCodec.setOutputSurface 时切换输出 Surface，
    // 以及在需要跳过因过大而无法存储在解码器输入缓冲区中的样本时。
    if (!codecReceivedBuffers && !buffer.isKeyFrame()) {
      buffer.clear();
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        // 我们刚刚清除的缓冲区包含了重新配置数据。
        // 我们需要将这些数据重新写入后续的缓冲区（如果有的话）。
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING;
      }
      return true;
    }

    if (shouldSkipDecoderInputBuffer(buffer)) {
      buffer.clear();
      decoderCounters.skippedInputBufferCount += 1;
      return true;
    }

    boolean bufferEncrypted = buffer.isEncrypted();
    if (bufferEncrypted) {
      buffer.cryptoInfo.increaseClearDataFirstSubSampleBy(adaptiveReconfigurationBytes);
    }

    long presentationTimeUs = buffer.timeUs;

    if (waitingForFirstSampleInFormat) {
      if (!pendingOutputStreamChanges.isEmpty()) {
        pendingOutputStreamChanges
            .peekLast()
            .formatQueue
            .add(presentationTimeUs, checkNotNull(inputFormat));
      } else {
        outputStreamInfo.formatQueue.add(presentationTimeUs, checkNotNull(inputFormat));
      }
      waitingForFirstSampleInFormat = false;
    }
    largestQueuedPresentationTimeUs = max(largestQueuedPresentationTimeUs, presentationTimeUs);
    if (hasReadStreamToEnd() || buffer.isLastSample()) {
      // 通知输出队列最后一个缓冲区的时间戳。
      lastBufferInStreamPresentationTimeUs = largestQueuedPresentationTimeUs;
    }
    buffer.flip();
    if (buffer.hasSupplementalData()) {
      handleInputBufferSupplementalData(buffer);
    }

    onQueueInputBuffer(buffer);
    int flags = getCodecBufferFlags(buffer);
    if (bufferEncrypted) {
      checkNotNull(codec)
          .queueSecureInputBuffer(
              inputIndex, /* offset= */ 0, buffer.cryptoInfo, presentationTimeUs, flags);
    } else {
      checkNotNull(codec)
          .queueInputBuffer(
              inputIndex,
              /* offset= */ 0,
              checkNotNull(buffer.data).limit(),
              presentationTimeUs,
              flags);
    }

    resetInputBuffer();
    codecReceivedBuffers = true;
    codecReconfigurationState = RECONFIGURATION_STATE_NONE;
    decoderCounters.queuedInputBufferCount++;
    return true;
  }

  /**
   * 在准备初始化 {@link MediaCodecAdapter} 时调用。
   *
   * <p>此方法在渲染器获取 {@linkplain #getMediaCodecConfiguration 配置} 并通过传入的 {@link MediaCodecAdapter.Factory} 创建适配器之前调用。
   *
   * <p>默认实现不执行任何操作。
   *
   * @param format 编解码器正在配置的 {@link Format}。
   * @throws ExoPlaybackException 如果准备初始化编解码器时发生错误。
   */
  protected void onReadyToInitializeCodec(Format format) throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * 当 {@link MediaCodec} 被创建并配置时调用。
   *
   * <p>默认实现不执行任何操作。
   *
   * @param name 已初始化的编解码器的名称。
   * @param configuration 用于配置编解码器的 {@link MediaCodecAdapter.Configuration}。
   * @param initializedTimestampMs 初始化完成时的 {@link SystemClock#elapsedRealtime()}。
   * @param initializationDurationMs 初始化编解码器所花费的时间（以毫秒为单位）。
   */
  protected void onCodecInitialized(
      String name,
      MediaCodecAdapter.Configuration configuration,
      long initializedTimestampMs,
      long initializationDurationMs) {
    // Do nothing.
  }

  /**
   * 当 {@link MediaCodec} 被释放时调用。
   *
   * <p>默认实现不执行任何操作。
   *
   * @param name 被释放的编解码器的名称。
   */
  protected void onCodecReleased(String name) {
    // Do nothing.
  }

  /**
   * 当编解码器发生错误时调用。
   *
   * <p>默认实现不执行任何操作。
   *
   * @param codecError 错误信息。
   */
  protected void onCodecError(Exception codecError) {
    // Do nothing.
  }

  /**
   * 当从上游 {@link MediaPeriod} 读取到新的 {@link Format} 时调用。
   *
   * @param formatHolder 包含新 {@link Format} 的 {@link FormatHolder}。
   * @throws ExoPlaybackException 如果重新初始化 {@link MediaCodec} 时发生错误。
   * @return 评估现有解码器实例是否可以重用新格式的结果，如果渲染器没有解码器，则返回 {@code null}。
   */
  @CallSuper
  @Nullable
  protected DecoderReuseEvaluation onInputFormatChanged(FormatHolder formatHolder)
      throws ExoPlaybackException {
    waitingForFirstSampleInFormat = true; // 设置等待新格式中的第一个样本的标志
    Format newFormat = checkNotNull(formatHolder.format); // 检查新格式不为 null
    if (newFormat.sampleMimeType == null) {
      // 如果新格式无效，可能是媒体文件的问题或该格式本不应被播放。
      // 参考：https://github.com/google/ExoPlayer/issues/8283
      throw createRendererException(
          new IllegalArgumentException("Sample MIME type is null."),
          newFormat,
          PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED); // 抛出渲染器异常
    }

    // 如果处理的是 AV1 视频，则移除格式中的初始化数据，因为播放 AV1 视频时不需要这些数据。
    // 参考：https://developer.android.com/reference/android/media/MediaCodec#CSD
    if (Objects.equals(newFormat.sampleMimeType, MimeTypes.VIDEO_AV1)
        && !newFormat.initializationData.isEmpty()) {
      newFormat = newFormat.buildUpon().setInitializationData(null).build(); // 移除初始化数据
    }

    setSourceDrmSession(formatHolder.drmSession); // 设置源 DRM 会话
    inputFormat = newFormat; // 更新输入格式

    if (bypassEnabled) {
      bypassDrainAndReinitialize = true; // 如果绕过模式启用，则设置绕过模式下的排空和重新初始化标志
      return null; // 需要先排空批处理缓冲区
    }

    if (codec == null) {
      availableCodecInfos = null; // 如果编解码器为 null，则清空可用编解码器信息列表
      maybeInitCodecOrBypass(); // 尝试初始化编解码器或启用绕过模式
      return null;
    }

    // 我们有一个现有的编解码器，可能需要重新配置、重新初始化或释放以切换到绕过模式。
    // 如果保留现有编解码器实例，则可能需要更新其操作速率和 DRM 会话。

    // 将当前编解码器和编解码器信息复制到局部变量中，以便在成员变量更新后仍可访问。
    MediaCodecAdapter codec = this.codec;
    MediaCodecInfo codecInfo = checkNotNull(this.codecInfo);

    Format oldFormat = checkNotNull(codecInputFormat);
    if (drmNeedsCodecReinitialization(codecInfo, newFormat, codecDrmSession, sourceDrmSession)) {
      drainAndReinitializeCodec(); // 如果需要重新初始化编解码器，则排空并重新初始化
      return new DecoderReuseEvaluation(
          codecInfo.name,
          oldFormat,
          newFormat,
          REUSE_RESULT_NO,
          DISCARD_REASON_DRM_SESSION_CHANGED); // 返回编解码器重用评估结果
    }
    boolean drainAndUpdateCodecDrmSession = sourceDrmSession != codecDrmSession;
    Assertions.checkState(!drainAndUpdateCodecDrmSession || Util.SDK_INT >= 23);

    DecoderReuseEvaluation evaluation = canReuseCodec(codecInfo, oldFormat, newFormat); // 评估是否可以重用编解码器
    @DecoderDiscardReasons int overridingDiscardReasons = 0;
    switch (evaluation.result) {
      case REUSE_RESULT_NO:
        drainAndReinitializeCodec(); // 如果无法重用编解码器，则排空并重新初始化
        break;
      case REUSE_RESULT_YES_WITH_FLUSH:
        if (!updateCodecOperatingRate(newFormat)) {
          overridingDiscardReasons |= DISCARD_REASON_OPERATING_RATE_CHANGED; // 如果操作速率更新失败，则添加丢弃原因
        } else {
          codecInputFormat = newFormat; // 更新编解码器输入格式
          if (drainAndUpdateCodecDrmSession) {
            if (!drainAndUpdateCodecDrmSessionV23()) {
              overridingDiscardReasons |= DISCARD_REASON_WORKAROUND; // 如果 DRM 会话更新失败，则添加丢弃原因
            }
          } else if (!drainAndFlushCodec()) {
            overridingDiscardReasons |= DISCARD_REASON_WORKAROUND; // 如果排空和刷新编解码器失败，则添加丢弃原因
          }
        }
        break;
      case REUSE_RESULT_YES_WITH_RECONFIGURATION:
        if (!updateCodecOperatingRate(newFormat)) {
          overridingDiscardReasons |= DISCARD_REASON_OPERATING_RATE_CHANGED; // 如果操作速率更新失败，则添加丢弃原因
        } else {
          codecReconfigured = true; // 设置编解码器已重新配置标志
          codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING; // 设置重新配置状态为写入待处理
          codecNeedsAdaptationWorkaroundBuffer =
              codecAdaptationWorkaroundMode == ADAPTATION_WORKAROUND_MODE_ALWAYS
                  || (codecAdaptationWorkaroundMode == ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION
                  && newFormat.width == oldFormat.width
                  && newFormat.height == oldFormat.height); // 判断是否需要自适应解决方案缓冲区
          codecInputFormat = newFormat; // 更新编解码器输入格式
          if (drainAndUpdateCodecDrmSession && !drainAndUpdateCodecDrmSessionV23()) {
            overridingDiscardReasons |= DISCARD_REASON_WORKAROUND; // 如果 DRM 会话更新失败，则添加丢弃原因
          }
        }
        break;
      case REUSE_RESULT_YES_WITHOUT_RECONFIGURATION:
        if (!updateCodecOperatingRate(newFormat)) {
          overridingDiscardReasons |= DISCARD_REASON_OPERATING_RATE_CHANGED; // 如果操作速率更新失败，则添加丢弃原因
        } else {
          codecInputFormat = newFormat; // 更新编解码器输入格式
          if (drainAndUpdateCodecDrmSession && !drainAndUpdateCodecDrmSessionV23()) {
            overridingDiscardReasons |= DISCARD_REASON_WORKAROUND; // 如果 DRM 会话更新失败，则添加丢弃原因
          }
        }
        break;
      default:
        throw new IllegalStateException(); // 永远不会发生
    }

    if (evaluation.result != REUSE_RESULT_NO
        && (this.codec != codec || codecDrainAction == DRAIN_ACTION_REINITIALIZE)) {
      // 初始评估表明可以重用编解码器，但触发了编解码器重新初始化。
      // 丢弃原因由 overridingDiscardReasons 指示。
      return new DecoderReuseEvaluation(
          codecInfo.name, oldFormat, newFormat, REUSE_RESULT_NO, overridingDiscardReasons); // 返回编解码器重用评估结果
    }

    return evaluation; // 返回评估结果
  }

  /**
   * 当输出格式之一发生变化时调用。
   *
   * <p>默认实现不执行任何操作。
   *
   * @param format 输入 {@link Format}，未来输出现在对应的格式。如果渲染器处于绕过模式，这也是输出格式。
   * @param mediaFormat 编解码器输出的 {@link MediaFormat}，如果渲染器处于绕过模式，则为 {@code null}。
   * @throws ExoPlaybackException 如果配置输出时发生错误，则抛出此异常。
   */
  protected void onOutputFormatChanged(Format format, @Nullable MediaFormat mediaFormat)
      throws ExoPlaybackException {
    // 默认实现不执行任何操作。
  }

  /**
   * 处理与输入缓冲区关联的补充数据。
   *
   * <p>默认实现不执行任何操作。
   *
   * @param buffer 即将排队的输入缓冲区。
   * @throws ExoPlaybackException 如果处理补充数据时发生错误，则抛出此异常。
   */
  protected void handleInputBufferSupplementalData(DecoderInputBuffer buffer)
      throws ExoPlaybackException {
    // 默认实现不执行任何操作。
  }

  /**
   * 在输入缓冲区被排队到编解码器之前立即调用。
   *
   * <p>默认实现不执行任何操作。
   *
   * @param buffer 即将被排队的缓冲区。
   * @throws ExoPlaybackException 如果处理输入缓冲区时发生错误，则抛出此异常。
   */
  protected void onQueueInputBuffer(DecoderInputBuffer buffer) throws ExoPlaybackException {
    // 默认实现不执行任何操作。
  }

  /**
   * 返回应为该缓冲区在 {@link MediaCodec#queueInputBuffer} 或 {@link
   * MediaCodec#queueSecureInputBuffer} 上设置的标志。
   *
   * @param buffer 输入缓冲区。
   * @return 在 {@link MediaCodec#queueInputBuffer} 或 {@link
   *     MediaCodec#queueSecureInputBuffer} 上设置的标志。
   */
  protected int getCodecBufferFlags(DecoderInputBuffer buffer) {
    return 0; // 默认返回 0，表示无特殊标志。
  }

  /**
   * 返回是否应在解码器之前跳过输入缓冲区。
   *
   * <p>这可以用于跳过在跳转期间不依赖的缓冲区的解码。参见 {@link C#BUFFER_FLAG_NOT_DEPENDED_ON}。
   *
   * @param buffer 输入缓冲区。
   */
  protected boolean shouldSkipDecoderInputBuffer(DecoderInputBuffer buffer) {
    return false; // 默认返回 false，表示不跳过缓冲区。
  }

  /**
   * 返回流中最后一个缓冲区的展示时间。
   *
   * <p>如果最后一个缓冲区尚未从样本队列中读取，则返回值为 {@link C#TIME_UNSET}。
   *
   * @return 流中最后一个缓冲区的展示时间。
   */
  protected long getLastBufferInStreamPresentationTimeUs() {
    return lastBufferInStreamPresentationTimeUs; // 返回最后一个缓冲区的展示时间。
  }

  /**
   * 当成功处理输出缓冲区时调用。
   *
   * @param presentationTimeUs 与输出缓冲区关联的时间戳。
   */
  @CallSuper
  protected void onProcessedOutputBuffer(long presentationTimeUs) {
    lastProcessedOutputBufferTimeUs = presentationTimeUs; // 更新最后处理的输出缓冲区时间
    while (!pendingOutputStreamChanges.isEmpty()
        && presentationTimeUs >= pendingOutputStreamChanges.peek().previousStreamLastBufferTimeUs) {
      setOutputStreamInfo(checkNotNull(pendingOutputStreamChanges.poll())); // 设置输出流信息
      onProcessedStreamChange(); // 调用流更改后的处理逻辑
    }
  }

  /** 在流更改前的最后一个输出缓冲区被处理后调用。 */
  protected void onProcessedStreamChange() {
    // 默认实现不执行任何操作。
  }

  /**
   * 评估现有的 {@link MediaCodec} 是否可以用于新的 {@link Format}，以及是否需要进行重新配置。
   *
   * <p>默认实现不允许解码器重用。
   *
   * @param codecInfo 描述解码器的 {@link MediaCodecInfo}。
   * @param oldFormat 现有实例已配置的 {@link Format}。
   * @param newFormat 新的 {@link Format}。
   * @return 评估结果。
   */
  protected DecoderReuseEvaluation canReuseCodec(
      MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
    return new DecoderReuseEvaluation(
        codecInfo.name,
        oldFormat,
        newFormat,
        REUSE_RESULT_NO,
        DISCARD_REASON_REUSE_NOT_IMPLEMENTED); // 默认返回不可重用的评估结果
  }

  /**
   * 在输出流偏移量更改后调用。
   *
   * <p>默认实现不执行任何操作。
   *
   * @param outputStreamOffsetUs 输出流偏移量（以微秒为单位）。
   */
  protected void onOutputStreamOffsetUsChanged(long outputStreamOffsetUs) {
    // 默认实现不执行任何操作
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  public boolean isReady() {
    return inputFormat != null
        && (isSourceReady()
            || hasOutputBuffer()
            || (codecHotswapDeadlineMs != C.TIME_UNSET
                && getClock().elapsedRealtime() < codecHotswapDeadlineMs));
  }

  /** 返回当前的播放速度，由 {@link #setPlaybackSpeed} 设置。 */
  protected float getPlaybackSpeed() {
    return currentPlaybackSpeed; // 返回当前播放速度
  }

  /** 返回当前编解码器使用的操作速率。 */
  protected float getCodecOperatingRate() {
    return codecOperatingRate; // 返回编解码器操作速率
  }

  /**
   * 返回给定播放速度、当前 {@link Format} 和可能的流格式集合的 {@link MediaFormat#KEY_OPERATING_RATE} 值。
   *
   * <p>默认实现返回 {@link #CODEC_OPERATING_RATE_UNSET}。
   *
   * @param targetPlaybackSpeed 播放应加速的目标倍数。这可能与当前播放速度不同，例如，在直播播放时临时调整速度。
   * @param format 编解码器正在配置的 {@link Format}。
   * @param streamFormats 可能的流格式集合。
   * @return 编解码器操作速率，如果不应设置编解码器操作速率，则返回 {@link #CODEC_OPERATING_RATE_UNSET}。
   */
  protected float getCodecOperatingRateV23(
      float targetPlaybackSpeed, Format format, Format[] streamFormats) {
    return CODEC_OPERATING_RATE_UNSET; // 默认返回未设置的操作速率
  }

  /** 返回用于通知应调用 {@link #render(long, long)} 的监听器。 */
  @Nullable
  protected final WakeupListener getWakeupListener() {
    return wakeupListener; // 返回唤醒监听器
  }

  /**
   * 更新编解码器操作速率，或者如果之前设置的操作速率需要清除，则触发编解码器释放和重新初始化。
   *
   * @throws ExoPlaybackException 如果释放或初始化编解码器时发生错误。
   * @return 如果触发了编解码器释放和重新初始化，则返回 false。其他情况下返回 true。
   */
  protected final boolean updateCodecOperatingRate() throws ExoPlaybackException {
    return updateCodecOperatingRate(codecInputFormat); // 调用更新编解码器操作速率的方法
  }

  /**
   * 更新编解码器操作速率，或者如果之前设置的操作速率需要清除，则触发编解码器释放和重新初始化。
   *
   * @param format 应配置操作速率的 {@link Format}。
   * @throws ExoPlaybackException 如果释放或初始化编解码器时发生错误。
   * @return 如果触发了编解码器释放和重新初始化，则返回 false。其他情况下返回 true。
   */
  private boolean updateCodecOperatingRate(@Nullable Format format) throws ExoPlaybackException {
    if (Util.SDK_INT < 23) {
      return true; // 如果 API 版本低于 23，则直接返回 true，不支持操作速率设置。
    }

    if (codec == null
        || codecDrainAction == DRAIN_ACTION_REINITIALIZE
        || getState() == STATE_DISABLED) {
      // 如果编解码器为 null，或需要重新初始化，或渲染器处于禁用状态，则无需更新操作速率。
      return true;
    }

    float newCodecOperatingRate =
        getCodecOperatingRateV23(targetPlaybackSpeed, checkNotNull(format), getStreamFormats()); // 获取新的操作速率
    if (codecOperatingRate == newCodecOperatingRate) {
      // 如果操作速率未发生变化，则直接返回 true。
      return true;
    } else if (newCodecOperatingRate == CODEC_OPERATING_RATE_UNSET) {
      // 如果新操作速率为未设置，则需要通过实例化新的编解码器实例来清除操作速率。参见 [Internal ref: b/111543954]。
      drainAndReinitializeCodec(); // 排空并重新初始化编解码器
      return false;
    } else if (codecOperatingRate != CODEC_OPERATING_RATE_UNSET
        || newCodecOperatingRate > assumedMinimumCodecOperatingRate) {
      // 如果之前设置过操作速率，或者新操作速率高于假设的最小速率，则需要设置操作速率。
      Bundle codecParameters = new Bundle(); // 创建编解码器参数 Bundle
      codecParameters.putFloat(MediaFormat.KEY_OPERATING_RATE, newCodecOperatingRate); // 设置操作速率
      checkNotNull(codec).setParameters(codecParameters); // 将参数应用到编解码器
      codecOperatingRate = newCodecOperatingRate; // 更新当前操作速率
      return true;
    }

    return true; // 默认返回 true
  }

  /**
   * 开始排空编解码器以进行刷新，或者如果无法刷新则释放并重新初始化编解码器。如果没有缓冲区已排队到编解码器，则此方法不执行任何操作。
   *
   * @return 如果由于需要应用刷新解决方案而触发了编解码器释放和重新初始化，则返回 false。其他情况下返回 true。
   */
  private boolean drainAndFlushCodec() {
    if (codecReceivedBuffers) { // 如果编解码器已接收缓冲区
      codecDrainState = DRAIN_STATE_SIGNAL_END_OF_STREAM; // 设置排空状态为发送流结束信号
      if (codecNeedsEosFlushWorkaround) { // 如果编解码器需要流结束刷新解决方案
        codecDrainAction = DRAIN_ACTION_REINITIALIZE; // 设置排空操作为重新初始化
        return false; // 返回 false，表示需要重新初始化
      } else {
        codecDrainAction = DRAIN_ACTION_FLUSH; // 否则设置排空操作为刷新
      }
    }
    return true; // 默认返回 true
  }

  /**
   * 开始排空编解码器以刷新并更新其 DRM 会话，或者如果无法刷新则释放并重新初始化编解码器。如果没有缓冲区已排队到编解码器，则此方法会立即更新 DRM 会话而不刷新编解码器。
   *
   * @throws ExoPlaybackException 如果更新编解码器的 DRM 会话时发生错误。
   * @return 如果由于需要应用刷新解决方案而触发了编解码器释放和重新初始化，则返回 false。其他情况下返回 true。
   */
  @TargetApi(23) // 仅在 SDK_INT >= 23 时调用，但 lint 不够智能无法识别。
  private boolean drainAndUpdateCodecDrmSessionV23() throws ExoPlaybackException {
    if (codecReceivedBuffers) { // 如果编解码器已接收缓冲区
      codecDrainState = DRAIN_STATE_SIGNAL_END_OF_STREAM; // 设置排空状态为发送流结束信号
      if (codecNeedsEosFlushWorkaround) { // 如果编解码器需要流结束刷新解决方案
        codecDrainAction = DRAIN_ACTION_REINITIALIZE; // 设置排空操作为重新初始化
        return false; // 返回 false，表示需要重新初始化
      } else {
        codecDrainAction = DRAIN_ACTION_FLUSH_AND_UPDATE_DRM_SESSION; // 否则设置排空操作为刷新并更新 DRM 会话
      }
    } else {
      // 如果没有缓冲区已排队到解码器，则可以立即更新 DRM 会话。
      updateDrmSessionV23(); // 更新 DRM 会话
    }
    return true; // 默认返回 true
  }

  /**
   * 开始排空编解码器以进行重新初始化。如果没有缓冲区已排队到编解码器，则重新初始化可能会立即进行。
   *
   * @throws ExoPlaybackException 如果重新初始化编解码器时发生错误。
   */
  private void drainAndReinitializeCodec() throws ExoPlaybackException {
    if (codecReceivedBuffers) { // 如果编解码器已接收缓冲区
      codecDrainState = DRAIN_STATE_SIGNAL_END_OF_STREAM; // 设置排空状态为发送流结束信号
      codecDrainAction = DRAIN_ACTION_REINITIALIZE; // 设置排空操作为重新初始化
    } else {
      // 如果没有缓冲区已排队到解码器，则可以立即重新初始化。
      reinitializeCodec(); // 重新初始化编解码器
    }
  }

  /**
   * @return 是否可能继续排空更多输出数据。
   * @throws ExoPlaybackException 如果在排空输出缓冲区时发生错误。
   */
  private boolean drainOutputBuffer(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException {
    MediaCodecAdapter codec = checkNotNull(this.codec); // 检查编解码器不为 null
    if (!hasOutputBuffer()) { // 如果没有输出缓冲区
      int outputIndex;
      if (codecNeedsEosOutputExceptionWorkaround && codecReceivedEos) { // 如果编解码器需要流结束输出异常解决方案且已接收到流结束信号
        try {
          outputIndex = codec.dequeueOutputBufferIndex(outputBufferInfo); // 尝试获取输出缓冲区索引
        } catch (IllegalStateException e) {
          processEndOfStream(); // 处理流结束
          if (outputStreamEnded) {
            // 释放编解码器，因为它处于错误状态。
            releaseCodec();
          }
          return false;
        }
      } else {
        outputIndex = codec.dequeueOutputBufferIndex(outputBufferInfo); // 获取输出缓冲区索引
      }

      if (outputIndex < 0) { // 如果输出缓冲区索引为负
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED /* (-2) */) { // 如果输出格式已更改
          processOutputMediaFormatChanged(); // 处理输出格式更改
          return true;
        }
        // MediaCodec.INFO_TRY_AGAIN_LATER (-1) 或未知的负返回值。
        if (codecNeedsEosPropagation
            && (inputStreamEnded || codecDrainState == DRAIN_STATE_WAIT_END_OF_STREAM)) { // 如果编解码器需要流结束传播且输入流已结束或正在等待流结束
          processEndOfStream(); // 处理流结束
        }
        if (lastOutputBufferProcessedRealtimeMs != C.TIME_UNSET
            && lastOutputBufferProcessedRealtimeMs + 100 < getClock().currentTimeMillis()) {
          // 如果最后一个输出缓冲区在 100 毫秒前已被处理且未接收到流结束缓冲区，则可能是编解码器行为异常，因此手动处理流结束。参见 b/359634542。
          processEndOfStream();
        }
        return false;
      }

      // 我们已经获取了一个缓冲区。
      if (shouldSkipAdaptationWorkaroundOutputBuffer) { // 如果需要跳过自适应解决方案输出缓冲区
        shouldSkipAdaptationWorkaroundOutputBuffer = false;
        codec.releaseOutputBuffer(outputIndex, false); // 释放输出缓冲区
        return true;
      } else if (outputBufferInfo.size == 0
          && (outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) { // 如果缓冲区大小为 0 且包含流结束标志
        // 获取的缓冲区表示流结束。立即处理它。
        processEndOfStream();
        return false;
      }

      this.outputIndex = outputIndex; // 保存输出缓冲区索引
      outputBuffer = codec.getOutputBuffer(outputIndex); // 获取输出缓冲区

      // 获取的缓冲区是媒体缓冲区。进行一些初始设置。
      // 它将通过调用 processOutputBuffer（可能多次）进行处理。
      if (outputBuffer != null) {
        outputBuffer.position(outputBufferInfo.offset); // 设置缓冲区位置
        outputBuffer.limit(outputBufferInfo.offset + outputBufferInfo.size); // 设置缓冲区限制
      }
      isDecodeOnlyOutputBuffer = outputBufferInfo.presentationTimeUs < getLastResetPositionUs(); // 判断是否为仅解码输出缓冲区
      isLastOutputBuffer =
          lastBufferInStreamPresentationTimeUs != C.TIME_UNSET
              && lastBufferInStreamPresentationTimeUs <= outputBufferInfo.presentationTimeUs; // 判断是否为最后一个输出缓冲区
      updateOutputFormatForTime(outputBufferInfo.presentationTimeUs); // 根据时间更新输出格式
    }

    boolean processedOutputBuffer;
    if (codecNeedsEosOutputExceptionWorkaround && codecReceivedEos) { // 如果编解码器需要流结束输出异常解决方案且已接收到流结束信号
      try {
        processedOutputBuffer =
            processOutputBuffer(
                positionUs,
                elapsedRealtimeUs,
                codec,
                outputBuffer,
                outputIndex,
                outputBufferInfo.flags,
                /* sampleCount= */ 1,
                outputBufferInfo.presentationTimeUs,
                isDecodeOnlyOutputBuffer,
                isLastOutputBuffer,
                checkNotNull(outputFormat)); // 处理输出缓冲区
      } catch (IllegalStateException e) {
        processEndOfStream(); // 处理流结束
        if (outputStreamEnded) {
          // 释放编解码器，因为它处于错误状态。
          releaseCodec();
        }
        return false;
      }
    } else {
      processedOutputBuffer =
          processOutputBuffer(
              positionUs,
              elapsedRealtimeUs,
              codec,
              outputBuffer,
              outputIndex,
              outputBufferInfo.flags,
              /* sampleCount= */ 1,
              outputBufferInfo.presentationTimeUs,
              isDecodeOnlyOutputBuffer,
              isLastOutputBuffer,
              checkNotNull(outputFormat)); // 处理输出缓冲区
    }

    if (processedOutputBuffer) { // 如果成功处理输出缓冲区
      onProcessedOutputBuffer(outputBufferInfo.presentationTimeUs); // 通知输出缓冲区已处理
      boolean isEndOfStream = (outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0; // 判断是否为流结束
      if (!isEndOfStream && codecReceivedEos && isLastOutputBuffer) {
        lastOutputBufferProcessedRealtimeMs = getClock().currentTimeMillis(); // 更新最后一个输出缓冲区处理时间
      }
      resetOutputBuffer(); // 重置输出缓冲区
      if (!isEndOfStream) {
        return true;
      }
      processEndOfStream(); // 处理流结束
    }

    return false;
  }

  /** 处理解码器输出 {@link MediaFormat} 的更改。 */
  private void processOutputMediaFormatChanged() {
    codecHasOutputMediaFormat = true; // 标记编解码器已输出媒体格式
    MediaFormat mediaFormat = checkNotNull(codec).getOutputFormat(); // 获取编解码器的输出格式
    if (codecAdaptationWorkaroundMode != ADAPTATION_WORKAROUND_MODE_NEVER
        && mediaFormat.getInteger(MediaFormat.KEY_WIDTH) == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT
        && mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
        == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT) {
      // 我们假设此格式更改事件是由自适应解决方案引起的。
      shouldSkipAdaptationWorkaroundOutputBuffer = true; // 标记需要跳过自适应解决方案输出缓冲区
      return;
    }
    codecOutputMediaFormat = mediaFormat; // 保存编解码器输出格式
    codecOutputMediaFormatChanged = true; // 标记编解码器输出格式已更改
  }

  /**
   * 处理输出媒体缓冲区。
   *
   * <p>当一个新的 {@link ByteBuffer} 传递给此方法时，其 position 和 limit 定义了要处理的数据范围。返回值指示缓冲区是否被完全处理。如果返回 true，则下一次调用此方法时将接收一个新的缓冲区进行处理。如果返回 false，则相同的缓冲区将传递给下一次调用。此方法的实现可以自由修改缓冲区，并可以假设缓冲区在连续调用之间不会被外部修改。因此，实现可以（例如）修改缓冲区的位置以跟踪已处理的数据量。
   *
   * <p>注意，在调用 {@link #onPositionReset(long, boolean)} 之后，此方法的第一次调用将始终接收一个新的 {@link ByteBuffer} 进行处理。
   *
   * @param positionUs 当前媒体时间（以微秒为单位），在渲染循环当前迭代开始时测量。
   * @param elapsedRealtimeUs {@link SystemClock#elapsedRealtime()} 的时间（以微秒为单位），在渲染循环当前迭代开始时测量。
   * @param codec {@link MediaCodecAdapter} 实例，或者在绕过模式下为 null（表示未使用编解码器）。
   * @param buffer 要处理的输出缓冲区，如果缓冲区数据未提供给应用层，则为 null（参见 {@link MediaCodec#getOutputBuffer(int)}）。此 {@code buffer} 仅对视频数据可以为 null。注意，在这种情况下，仍然可以通过使用 {@code bufferIndex} 来渲染缓冲区数据。
   * @param bufferIndex 输出缓冲区的索引。
   * @param bufferFlags 附加到输出缓冲区的标志。
   * @param sampleCount 从缓冲区中的样本队列提取的样本数量。这允许批量处理多个样本以提高效率。
   * @param bufferPresentationTimeUs 输出缓冲区的展示时间（以微秒为单位）。
   * @param isDecodeOnlyBuffer 缓冲区时间戳是否小于预期的播放起始位置。
   * @param isLastBuffer 缓冲区是否已知包含当前流的最后一个样本。此标志基于最佳努力设置，任何依赖它的逻辑都应优雅地处理未设置的情况。
   * @param format 与缓冲区关联的 {@link Format}。
   * @return 输出缓冲区是否被完全处理（例如，已渲染或跳过）。
   * @throws ExoPlaybackException 如果处理输出缓冲区时发生错误。
   */
  protected abstract boolean processOutputBuffer(
      long positionUs,
      long elapsedRealtimeUs,
      @Nullable MediaCodecAdapter codec,
      @Nullable ByteBuffer buffer,
      int bufferIndex,
      int bufferFlags,
      int sampleCount,
      long bufferPresentationTimeUs,
      boolean isDecodeOnlyBuffer,
      boolean isLastBuffer,
      Format format)
      throws ExoPlaybackException;

  /**
   * 逐步渲染任何剩余的输出。
   *
   * <p>默认实现不执行任何操作。
   *
   * @throws ExoPlaybackException 如果渲染剩余输出时发生错误，则抛出此异常。
   */
  protected void renderToEndOfStream() throws ExoPlaybackException {
    // 默认实现不执行任何操作。
  }

  /**
   * 处理流结束信号。
   *
   * @throws ExoPlaybackException 如果处理信号时发生错误，则抛出此异常。
   */
// codecDrainAction == DRAIN_ACTION_FLUSH_AND_UPDATE_DRM_SESSION 表示 SDK_INT >= 23。
  @TargetApi(23)
  private void processEndOfStream() throws ExoPlaybackException {
    switch (codecDrainAction) { // 根据编解码器排空操作进行处理
      case DRAIN_ACTION_REINITIALIZE:
        reinitializeCodec(); // 重新初始化编解码器
        break;
      case DRAIN_ACTION_FLUSH_AND_UPDATE_DRM_SESSION:
        flushCodec(); // 刷新编解码器
        updateDrmSessionV23(); // 更新 DRM 会话
        break;
      case DRAIN_ACTION_FLUSH:
        flushCodec(); // 刷新编解码器
        break;
      case DRAIN_ACTION_NONE:
      default:
        outputStreamEnded = true; // 标记输出流已结束
        renderToEndOfStream(); // 渲染剩余输出
        break;
    }
  }

  /**
   * 通知渲染器输出流结束即将到来，应在下一次渲染时处理。
   */
  protected final void setPendingOutputEndOfStream() {
    pendingOutputEndOfStream = true; // 设置输出流结束即将到来的标志
  }

  /**
   * 返回应从 {@link #processOutputBuffer(long, long, MediaCodecAdapter, ByteBuffer, int, int, int, long, boolean, boolean, Format)} 中的 {@code bufferPresentationTimeUs} 中减去的偏移量，以获取相对于媒体的播放位置。
   */
  protected final long getOutputStreamOffsetUs() {
    return outputStreamInfo.streamOffsetUs; // 返回输出流偏移量（以微秒为单位）
  }

  /** 返回当前输出流的起始位置（以微秒为单位）。 */
  protected final long getOutputStreamStartPositionUs() {
    return outputStreamInfo.startPositionUs; // 返回输出流的起始位置
  }

  private void setOutputStreamInfo(OutputStreamInfo outputStreamInfo) {
    this.outputStreamInfo = outputStreamInfo; // 设置输出流信息
    if (outputStreamInfo.streamOffsetUs != C.TIME_UNSET) { // 如果输出流偏移量已设置
      needToNotifyOutputFormatChangeAfterStreamChange = true; // 标记需要在流更改后通知输出格式更改
      onOutputStreamOffsetUsChanged(outputStreamInfo.streamOffsetUs); // 调用输出流偏移量更改的处理方法
    }
  }

  /** 返回此渲染器是否支持给定 {@link Format} 的 DRM 方案。 */
  protected static boolean supportsFormatDrm(Format format) {
    return format.cryptoType == C.CRYPTO_TYPE_NONE || format.cryptoType == C.CRYPTO_TYPE_FRAMEWORK; // 判断格式是否支持 DRM 方案
  }

  /**
   * 返回是否需要重新初始化编解码器以处理 DRM 更改。
   * 如果返回 {@code false}，则要么 {@code oldSession == newSession}（即没有更改），
   * 要么可以使用 MediaCrypto.setMediaDrmSession 更新现有编解码器。
   */
  private boolean drmNeedsCodecReinitialization(
      MediaCodecInfo codecInfo,
      Format newFormat,
      @Nullable DrmSession oldSession,
      @Nullable DrmSession newSession)
      throws ExoPlaybackException {
    if (oldSession == newSession) {
      // 如果旧会话和新会话相同，则无需重新初始化。
      return false;
    }

    // 注意：oldSession 和 newSession 中至少有一个非 null。

    if (newSession == null || oldSession == null) {
      // 从 DRM 切换到无 DRM，或者从无 DRM 切换到 DRM，始终需要重新初始化。
      return true;
    }

    @Nullable CryptoConfig newCryptoConfig = newSession.getCryptoConfig();
    if (newCryptoConfig == null) {
      // 只有在获取 newSession 的 CDM 需要配置时才会发生这种情况。这种情况不太可能发生（可能需要从一个 DRM 方案切换到另一个 DRM 方案，而新的 CDM 之前未被使用过且需要配置）。虽然可以在不重新初始化编解码器的情况下处理这种情况，但这需要重用代码路径能够等待配置完成后再调用 MediaCrypto.setMediaDrmSession。鉴于这种情况不太可能发生，额外的复杂性是不值得的，因此在这种情况下我们选择重新初始化。
      return true;
    }

    @Nullable CryptoConfig oldCryptoConfig = oldSession.getCryptoConfig();
    if (oldCryptoConfig == null || !newCryptoConfig.getClass().equals(oldCryptoConfig.getClass())) {
      // 在不同的 CryptoConfig 实现之间切换表明我们在不同形式的解密之间切换（例如应用内解密与框架提供的解密），这需要重新初始化编解码器。
      return true;
    }

    if (!(newCryptoConfig instanceof FrameworkCryptoConfig)) {
      // 假设非框架的 CryptoConfig 实现表明编解码器可以重用（因为这表明解密是在应用内完成的，然后再将数据传递给 MediaCodec，因此 DRM 密钥的更改不会影响 MediaCodec 看到的（已解密的）数据）。
      return false;
    }

    // 注意：oldSession 和 newSession 都是非 null 的，并且它们是不同的会话。

    if (!newSession.getSchemeUuid().equals(oldSession.getSchemeUuid())) {
      // MediaCrypto.setMediaDrmSession 无法在不同的 DRM 方案之间切换。
      return true;
    }

    if (Util.SDK_INT < 23) {
      // MediaCrypto.setMediaDrmSession 仅在 API 级别 23 及以上可用，因此在较旧的 API 级别上需要重新初始化以切换到 newSession。
      return true;
    }
    if (C.PLAYREADY_UUID.equals(oldSession.getSchemeUuid())
        || C.PLAYREADY_UUID.equals(newSession.getSchemeUuid())) {
      // PlayReady CDM 不支持 MediaCrypto.setMediaDrmSession，无论是作为旧会话还是新会话。
      // TODO: 在 [Internal ref: b/128835874] 修复后添加 API 检查。
      return true;
    }

    // 如果 newSession 可能需要切换到安全输出路径，则需要重新初始化。我们假设 newSession 可能需要安全解码器，如果它尚未完全打开。
    return !codecInfo.secure
        && (newSession.getState() == DrmSession.STATE_OPENING
        || ((newSession.getState() == DrmSession.STATE_OPENED
        || newSession.getState() == DrmSession.STATE_OPENED_WITH_KEYS)
        && newSession.requiresSecureDecoder(checkNotNull(newFormat.sampleMimeType))));
  }

  private void reinitializeCodec() throws ExoPlaybackException {
    releaseCodec();
    maybeInitCodecOrBypass();
  }

  @RequiresApi(23)
  private void updateDrmSessionV23() throws ExoPlaybackException {
    CryptoConfig cryptoConfig = checkNotNull(sourceDrmSession).getCryptoConfig();
    if (cryptoConfig instanceof FrameworkCryptoConfig) {
      try {
        checkNotNull(mediaCrypto)
            .setMediaDrmSession(((FrameworkCryptoConfig) cryptoConfig).sessionId);
      } catch (MediaCryptoException e) {
        throw createRendererException(
            e, inputFormat, PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR);
      }
    }
    setCodecDrmSession(sourceDrmSession);
    codecDrainState = DRAIN_STATE_NONE;
    codecDrainAction = DRAIN_ACTION_NONE;
  }

  /**
   * 在不使用解码器的情况下处理任何待处理的缓冲区批次，并从源中排空新的缓冲区批次。
   *
   * @param positionUs 当前媒体时间（以微秒为单位），在渲染循环当前迭代开始时测量。
   * @param elapsedRealtimeUs {@link SystemClock#elapsedRealtime()} 的时间（以微秒为单位），在渲染循环当前迭代开始时测量。
   * @return 立即再次调用此方法是否会取得更多进展。
   * @throws ExoPlaybackException 如果在处理缓冲区或处理格式更改时发生错误。
   */
  private boolean bypassRender(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException {

    // 处理任何批处理数据。
    checkState(!outputStreamEnded); // 检查输出流未结束
    if (bypassBatchBuffer.hasSamples()) { // 如果批处理缓冲区中有样本
      if (processOutputBuffer(
          positionUs,
          elapsedRealtimeUs,
          /* codec= */ null,
          bypassBatchBuffer.data,
          outputIndex,
          /* bufferFlags= */ 0,
          bypassBatchBuffer.getSampleCount(),
          bypassBatchBuffer.getFirstSampleTimeUs(),
          isDecodeOnly(getLastResetPositionUs(), bypassBatchBuffer.getLastSampleTimeUs()),
          bypassBatchBuffer.isEndOfStream(),
          checkNotNull(outputFormat))) { // 处理输出缓冲区
        // 批处理缓冲区已完全处理。
        onProcessedOutputBuffer(bypassBatchBuffer.getLastSampleTimeUs()); // 通知输出缓冲区已处理
        bypassBatchBuffer.clear(); // 清空批处理缓冲区
      } else {
        // 无法完全处理批处理缓冲区。稍后重试。
        return false;
      }
    }

    // 如果到达流结束，则进行处理。
    if (inputStreamEnded) { // 如果输入流已结束
      outputStreamEnded = true; // 标记输出流已结束
      return false;
    }

    if (bypassSampleBufferPending) { // 如果有待处理的样本缓冲区
      Assertions.checkState(bypassBatchBuffer.append(bypassSampleBuffer)); // 将样本缓冲区追加到批处理缓冲区
      bypassSampleBufferPending = false; // 清除待处理标志
    }

    if (bypassDrainAndReinitialize) { // 如果需要排空并重新初始化
      if (bypassBatchBuffer.hasSamples()) { // 如果批处理缓冲区中有样本
        // 这只有在 bypassSampleBufferPending 为 true 时才会发生。返回 true 以尝试立即处理样本，该样本现在已追加到批处理缓冲区。
        return true;
      }
      // 新格式可能需要使用编解码器而不是绕过模式。
      disableBypass(); // 禁用绕过模式
      bypassDrainAndReinitialize = false; // 清除排空并重新初始化标志
      maybeInitCodecOrBypass(); // 尝试初始化编解码器或启用绕过模式
      if (!bypassEnabled) { // 如果不再处于绕过模式
        return false;
      }
    }

    // 从输入中读取数据，并将任何样本缓冲区追加到批处理缓冲区。
    bypassRead(); // 读取数据

    if (bypassBatchBuffer.hasSamples()) { // 如果批处理缓冲区中有样本
      bypassBatchBuffer.flip(); // 翻转批处理缓冲区
    }

    // 如果我们有批处理数据、流结束或需要重新初始化，则可以取得更多进展（注意在下一次调用时将执行上述一个或多个代码块）。
    return bypassBatchBuffer.hasSamples() || inputStreamEnded || bypassDrainAndReinitialize;
  }

  private void bypassRead() throws ExoPlaybackException {
    checkState(!inputStreamEnded); // 检查输入流未结束
    FormatHolder formatHolder = getFormatHolder(); // 获取格式持有者
    bypassSampleBuffer.clear(); // 清空样本缓冲区
    while (true) {
      bypassSampleBuffer.clear(); // 清空样本缓冲区
      @ReadDataResult int result = readSource(formatHolder, bypassSampleBuffer, /* readFlags= */ 0); // 从源中读取数据
      switch (result) {
        case C.RESULT_FORMAT_READ: // 如果读取到格式
          onInputFormatChanged(formatHolder); // 处理输入格式更改
          return;
        case C.RESULT_NOTHING_READ: // 如果未读取到数据
          if (hasReadStreamToEnd()) { // 如果已读取到流结束
            // 通知输出队列最后一个缓冲区的时间戳。
            lastBufferInStreamPresentationTimeUs = largestQueuedPresentationTimeUs;
          }
          return;
        case C.RESULT_BUFFER_READ: // 如果读取到缓冲区
          if (bypassSampleBuffer.isEndOfStream()) { // 如果缓冲区是流结束标志
            inputStreamEnded = true; // 标记输入流已结束
            lastBufferInStreamPresentationTimeUs = largestQueuedPresentationTimeUs; // 更新最后一个缓冲区的时间戳
            return;
          }
          largestQueuedPresentationTimeUs =
              max(largestQueuedPresentationTimeUs, bypassSampleBuffer.timeUs); // 更新最大排队时间戳
          if (hasReadStreamToEnd() || buffer.isLastSample()) { // 如果已读取到流结束或缓冲区是最后一个样本
            // 通知输出队列最后一个缓冲区的时间戳。
            lastBufferInStreamPresentationTimeUs = largestQueuedPresentationTimeUs;
          }
          if (waitingForFirstSampleInFormat) { // 如果正在等待新格式的第一个样本
            // 这是新格式的第一个缓冲区，必须更新输出格式。
            outputFormat = checkNotNull(inputFormat); // 设置输出格式
            if (Objects.equals(outputFormat.sampleMimeType, MimeTypes.AUDIO_OPUS)
                && !outputFormat.initializationData.isEmpty()) { // 如果格式的 MIME 类型是 Opus 并且初始化数据不为空
              // 格式的 MIME 类型是 Opus，因此应使用 preSkip 数据更新格式。
              // TODO(b/298634018): 根据起始位置调整 encoderDelay 值。
              int numberPreSkipSamples =
                  OpusUtil.getPreSkipSamples(outputFormat.initializationData.get(0)); // 获取 preSkip 样本数
              outputFormat =
                  checkNotNull(outputFormat)
                      .buildUpon()
                      .setEncoderDelay(numberPreSkipSamples) // 设置编码器延迟
                      .build();
            }
            onOutputFormatChanged(outputFormat, /* mediaFormat= */ null); // 处理输出格式更改
            waitingForFirstSampleInFormat = false; // 清除等待标志
          }
          // 尝试将缓冲区追加到批处理缓冲区。
          bypassSampleBuffer.flip(); // 翻转样本缓冲区

          if (outputFormat != null
              && Objects.equals(outputFormat.sampleMimeType, MimeTypes.AUDIO_OPUS)) { // 如果输出格式的 MIME 类型是 Opus
            if (bypassSampleBuffer.hasSupplementalData()) { // 如果缓冲区有补充数据
              // 在样本缓冲区上设置格式，使其包含 MIME 类型和编码器延迟。
              bypassSampleBuffer.format = outputFormat;
              handleInputBufferSupplementalData(bypassSampleBuffer); // 处理输入缓冲区补充数据
            }
            if (OpusUtil.needToDecodeOpusFrame(
                getLastResetPositionUs(), bypassSampleBuffer.timeUs)) { // 如果需要解码 Opus 帧
              // 只要帧不早于最后一次重置位置超过 seek-preroll，就进行分组。
              oggOpusAudioPacketizer.packetize(
                  bypassSampleBuffer, checkNotNull(outputFormat).initializationData); // 对 Opus 音频进行分组
            }
          }
          if (!haveBypassBatchBufferAndNewSampleSameDecodeOnlyState()
              || !bypassBatchBuffer.append(bypassSampleBuffer)) { // 如果批处理缓冲区和新样本的解码状态不同或无法追加
            bypassSampleBufferPending = true; // 标记样本缓冲区待处理
            return;
          }
          break;
        default:
          throw new IllegalStateException(); // 抛出非法状态异常
      }
    }
  }

  private boolean haveBypassBatchBufferAndNewSampleSameDecodeOnlyState() {
    // TODO: b/295800114 - 对于并非每个样本都是关键帧的格式，按仅解码状态拆分批处理缓冲区是不安全的，因为下游组件可能会接收到从非关键帧样本开始的编码数据。
    if (!bypassBatchBuffer.hasSamples()) { // 如果批处理缓冲区中没有样本
      return true;
    }
    long lastResetPositionUs = getLastResetPositionUs(); // 获取最后一次重置位置
    boolean batchBufferIsDecodeOnly =
        isDecodeOnly(lastResetPositionUs, bypassBatchBuffer.getLastSampleTimeUs()); // 判断批处理缓冲区是否为仅解码
    boolean sampleBufferIsDecodeOnly = isDecodeOnly(lastResetPositionUs, bypassSampleBuffer.timeUs); // 判断样本缓冲区是否为仅解码
    return batchBufferIsDecodeOnly == sampleBufferIsDecodeOnly; // 返回批处理缓冲区和样本缓冲区的仅解码状态是否相同
  }

  /**
   * 根据目标播放时间和帧时间返回帧是否应为仅解码。
   *
   * <p>如果格式是 Opus，则当帧时间早于开始时间超过 seek-preroll 时，帧为仅解码。
   *
   * @param startTimeUs 开始播放的时间。
   * @param frameTimeUs 样本的时间。
   * @return 帧是否为仅解码。
   */
  private boolean isDecodeOnly(long startTimeUs, long frameTimeUs) {
    // 如果帧时间早于目标位置超过 seek-preroll，则应跳过 Opus 帧。
    return frameTimeUs < startTimeUs
        && (outputFormat == null
        || !Objects.equals(outputFormat.sampleMimeType, MimeTypes.AUDIO_OPUS)
        || !OpusUtil.needToDecodeOpusFrame(
        /* startTimeUs= */ startTimeUs, /* frameTimeUs= */ frameTimeUs));
  }

  private static boolean isMediaCodecException(IllegalStateException error) {
    if (error instanceof MediaCodec.CodecException) {
      return true;
    }
    StackTraceElement[] stackTrace = error.getStackTrace();
    return stackTrace.length > 0 && stackTrace[0].getClassName().equals("android.media.MediaCodec");
  }

  /**
   * 返回一个模式，指定何时应启用自适应解决方案。
   *
   * <p>启用时，该解决方案会排队并丢弃一个宽度和高度都等于 {@link #ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT} 的空白帧，以在格式更改时重置解码器的内部状态。
   *
   * <p>参见 [Internal: b/27807182]。参见 <a
   * href="https://github.com/google/ExoPlayer/issues/3257">GitHub issue #3257</a>。
   *
   * @param name 解码器的名称。
   * @return 指定何时应启用自适应解决方案的模式。
   */
  private @AdaptationWorkaroundMode int codecAdaptationWorkaroundMode(String name) {
    if (Util.SDK_INT <= 25
        && "OMX.Exynos.avc.dec.secure".equals(name)
        && (Util.MODEL.startsWith("SM-T585")
        || Util.MODEL.startsWith("SM-A510")
        || Util.MODEL.startsWith("SM-A520")
        || Util.MODEL.startsWith("SM-J700"))) { // 如果 API 版本 <= 25 且解码器名称为 OMX.Exynos.avc.dec.secure，并且设备型号匹配
      return ADAPTATION_WORKAROUND_MODE_ALWAYS; // 返回始终启用自适应解决方案的模式
    } else if (Util.SDK_INT < 24
        && ("OMX.Nvidia.h264.decode".equals(name) || "OMX.Nvidia.h264.decode.secure".equals(name))
        && ("flounder".equals(Util.DEVICE)
        || "flounder_lte".equals(Util.DEVICE)
        || "grouper".equals(Util.DEVICE)
        || "tilapia".equals(Util.DEVICE))) { // 如果 API 版本 < 24 且解码器名称为 OMX.Nvidia.h264.decode 或 OMX.Nvidia.h264.decode.secure，并且设备名称匹配
      return ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION; // 返回在相同分辨率时启用自适应解决方案的模式
    } else {
      return ADAPTATION_WORKAROUND_MODE_NEVER; // 返回从不启用自适应解决方案的模式
    }
  }

  /**
   * 返回解码器是否已知在输出 {@link MediaFormat} 之前刷新会导致行为不正确。
   *
   * <p>如果返回 true，渲染器将通过在此情况发生时实例化一个新的解码器来解决此问题。
   *
   * <p>参见 [Internal: b/141097367]。
   *
   * @param name 解码器的名称。
   * @return 如果解码器在输出 {@link MediaFormat} 之前刷新会导致行为不正确，则返回 true。否则返回 false。
   */
  private static boolean codecNeedsSosFlushWorkaround(String name) {
    return Util.SDK_INT == 29 && "c2.android.aac.decoder".equals(name); // 如果 API 版本为 29 且解码器名称为 c2.android.aac.decoder，则返回 true
  }

  /**
   * 返回解码器是否已知在当前设备上错误地处理 {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} 标志的传播。
   *
   * <p>如果返回 true，渲染器将通过在不依赖底层解码器将标志传播到输出缓冲区的情况下近似流结束行为来解决此问题。
   *
   * @param codecInfo 有关 {@link MediaCodec} 的信息。
   * @return 如果解码器已知在当前设备上错误地处理 {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} 标志的传播，则返回 true。否则返回 false。
   */
  private static boolean codecNeedsEosPropagationWorkaround(MediaCodecInfo codecInfo) {
    String name = codecInfo.name;
    return (Util.SDK_INT <= 25 && "OMX.rk.video_decoder.avc".equals(name)) // 如果 API 版本 <= 25 且解码器名称为 OMX.rk.video_decoder.avc
        || (Util.SDK_INT <= 29
        && ("OMX.broadcom.video_decoder.tunnel".equals(name)
        || "OMX.broadcom.video_decoder.tunnel.secure".equals(name)
        || "OMX.bcm.vdec.avc.tunnel".equals(name)
        || "OMX.bcm.vdec.avc.tunnel.secure".equals(name)
        || "OMX.bcm.vdec.hevc.tunnel".equals(name)
        || "OMX.bcm.vdec.hevc.tunnel.secure".equals(name))) // 如果 API 版本 <= 29 且解码器名称匹配 Broadcom 的隧道解码器
        || ("Amazon".equals(Util.MANUFACTURER) && "AFTS".equals(Util.MODEL) && codecInfo.secure); // 如果设备制造商是 Amazon 且设备型号是 AFTS，并且解码器是安全解码器
  }

  /**
   * 返回解码器是否已知在接收到带有 {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} 标志的输入缓冲区后刷新会导致行为不正确。
   *
   * <p>如果返回 true，渲染器将通过在此情况发生时实例化一个新的解码器来解决此问题。
   *
   * <p>参见 [Internal: b/8578467, b/23361053]。
   *
   * @param name 解码器的名称。
   * @return 如果解码器在接收到带有 {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} 标志的输入缓冲区后刷新会导致行为不正确，则返回 true。否则返回 false。
   */
  private static boolean codecNeedsEosFlushWorkaround(String name) {
    return Util.SDK_INT <= 23 && "OMX.google.vorbis.decoder".equals(name); // 如果 API 版本 <= 23 且解码器名称为 OMX.google.vorbis.decoder，则返回 true
  }

  /**
   * 返回解码器在接收到带有 {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} 标志的输入缓冲区后，是否可能从 {@link
   * MediaCodec#dequeueOutputBuffer(MediaCodec.BufferInfo, long)} 或 {@link
   * MediaCodec#releaseOutputBuffer(int, boolean)} 抛出 {@link IllegalStateException}。
   *
   * <p>参见 [Internal: b/17933838]。
   *
   * @param name 解码器的名称。
   * @return 如果解码器在接收到流结束缓冲区后可能抛出异常，则返回 true。
   */
  private static boolean codecNeedsEosOutputExceptionWorkaround(String name) {
    return Util.SDK_INT == 21 && "OMX.google.aac.decoder".equals(name); // 如果 API 版本为 21 且解码器名称为 OMX.google.aac.decoder，则返回 true
  }

  private static final class OutputStreamInfo {

    public static final OutputStreamInfo UNSET =
        new OutputStreamInfo(
            /* previousStreamLastBufferTimeUs= */ C.TIME_UNSET,
            /* startPositionUs= */ C.TIME_UNSET,
            /* streamOffsetUs= */ C.TIME_UNSET); // 未设置的 OutputStreamInfo 实例

    public final long previousStreamLastBufferTimeUs; // 上一个流的最后一个缓冲区的时间（以微秒为单位）
    public final long startPositionUs; // 流的起始位置（以微秒为单位）
    public final long streamOffsetUs; // 流的时间偏移量（以微秒为单位）
    public final TimedValueQueue<Format> formatQueue; // 格式队列，用于存储时间相关的格式信息

    public OutputStreamInfo(
        long previousStreamLastBufferTimeUs, long startPositionUs, long streamOffsetUs) {
      this.previousStreamLastBufferTimeUs = previousStreamLastBufferTimeUs;
      this.startPositionUs = startPositionUs;
      this.streamOffsetUs = streamOffsetUs;
      this.formatQueue = new TimedValueQueue<>(); // 初始化格式队列
    }
  }

  @RequiresApi(31)
  private static final class Api31 {
    private Api31() {}

    public static void setLogSessionIdToMediaCodecFormat(
        MediaCodecAdapter.Configuration codecConfiguration, PlayerId playerId) {
      LogSessionId logSessionId = playerId.getLogSessionId();
      if (!logSessionId.equals(LogSessionId.LOG_SESSION_ID_NONE)) {
        codecConfiguration.mediaFormat.setString("log-session-id", logSessionId.getStringId());
      }
    }
  }

  private final class MediaCodecRendererCodecAdapterListener
      implements MediaCodecAdapter.OnBufferAvailableListener {
    @Override
    public void onInputBufferAvailable() {
      if (wakeupListener != null) {
        wakeupListener.onWakeup();
      }
    }

    @Override
    public void onOutputBufferAvailable() {
      if (wakeupListener != null) {
        wakeupListener.onWakeup();
      }
    }
  }
}
