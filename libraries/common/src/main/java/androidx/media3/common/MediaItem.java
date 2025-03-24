package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.msToUs;
import static androidx.media3.common.util.Util.usToMs;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.BundleCollectionUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.InlineMe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 媒体项的表示。 */
public final class MediaItem {

  /**
   * 为给定的 URI 创建一个 {@link MediaItem}。
   *
   * @param uri URI 字符串。
   * @return 为给定 URI 创建的 {@link MediaItem}。
   */
  public static MediaItem fromUri(String uri) {
    return new MediaItem.Builder().setUri(uri).build();
  }

  /**
   * 为给定的 {@link Uri URI} 创建一个 {@link MediaItem}。
   *
   * @param uri {@link Uri URI} 对象。
   * @return 为给定 URI 创建的 {@link MediaItem}。
   */
  public static MediaItem fromUri(Uri uri) {
    return new MediaItem.Builder().setUri(uri).build();
  }

  /** {@link MediaItem} 实例的构建器。 */
  public static final class Builder {

    @Nullable private String mediaId; // 媒体项的唯一标识符
    @Nullable private Uri uri; // 媒体项的 URI
    @Nullable private String mimeType; // 媒体项的 MIME 类型
    // TODO: 在所有已弃用的单独设置方法被移除后，将此更改为 ClippingProperties。
    private ClippingConfiguration.Builder clippingConfiguration; // 剪辑配置
    // TODO: 在所有已弃用的单独设置方法被移除后，将此更改为 @Nullable DrmConfiguration。
    private DrmConfiguration.Builder drmConfiguration; // DRM 配置
    private List<StreamKey> streamKeys; // 流密钥列表
    @Nullable private String customCacheKey; // 自定义缓存键
    private ImmutableList<SubtitleConfiguration> subtitleConfigurations; // 字幕配置列表
    @Nullable private AdsConfiguration adsConfiguration; // 广告配置
    @Nullable private Object tag; // 自定义标签
    private long imageDurationMs; // 图像持续时间（毫秒）
    @Nullable private MediaMetadata mediaMetadata; // 媒体元数据
    // TODO: 在所有已弃用的单独设置方法被移除后，将此更改为 LiveConfiguration。
    private LiveConfiguration.Builder liveConfiguration; // 直播配置
    private RequestMetadata requestMetadata; // 请求元数据

    /** Creates a builder. */
    @SuppressWarnings("deprecation") // Temporarily uses DrmConfiguration.Builder() constructor.
    public Builder() {
      clippingConfiguration = new ClippingConfiguration.Builder();
      drmConfiguration = new DrmConfiguration.Builder();
      streamKeys = Collections.emptyList();
      subtitleConfigurations = ImmutableList.of();
      liveConfiguration = new LiveConfiguration.Builder();
      requestMetadata = RequestMetadata.EMPTY;
      imageDurationMs = C.TIME_UNSET;
    }

    // Using deprecated DrmConfiguration.Builder to support deprecated methods.
    @SuppressWarnings("deprecation")
    private Builder(MediaItem mediaItem) {
      this();
      clippingConfiguration = mediaItem.clippingConfiguration.buildUpon();
      mediaId = mediaItem.mediaId;
      mediaMetadata = mediaItem.mediaMetadata;
      liveConfiguration = mediaItem.liveConfiguration.buildUpon();
      requestMetadata = mediaItem.requestMetadata;
      @Nullable LocalConfiguration localConfiguration = mediaItem.localConfiguration;
      if (localConfiguration != null) {
        customCacheKey = localConfiguration.customCacheKey;
        mimeType = localConfiguration.mimeType;
        uri = localConfiguration.uri;
        streamKeys = localConfiguration.streamKeys;
        subtitleConfigurations = localConfiguration.subtitleConfigurations;
        tag = localConfiguration.tag;
        drmConfiguration =
            localConfiguration.drmConfiguration != null
                ? localConfiguration.drmConfiguration.buildUpon()
                : new DrmConfiguration.Builder();
        adsConfiguration = localConfiguration.adsConfiguration;
        imageDurationMs = localConfiguration.imageDurationMs;
      }
    }
    /**
     * 设置可选的媒体 ID，用于标识媒体项。
     *
     * <p>默认使用 {@link #DEFAULT_MEDIA_ID}。
     */
    @CanIgnoreReturnValue
    public Builder setMediaId(String mediaId) {
      this.mediaId = checkNotNull(mediaId); // 确保 mediaId 不为 null
      return this;
    }

    /**
     * 设置可选的 URI。
     *
     * <p>如果 {@code uri} 为 null 或未设置，则在 {@link #build()} 过程中不会创建 {@link LocalConfiguration} 对象，
     * 也不应调用其他会填充 {@link MediaItem#localConfiguration} 的 {@code Builder} 方法。
     */
    @CanIgnoreReturnValue
    public Builder setUri(@Nullable String uri) {
      return setUri(uri == null ? null : Uri.parse(uri)); // 将字符串 URI 转换为 Uri 对象
    }

    /**
     * 设置可选的 URI。
     *
     * <p>如果 {@code uri} 为 null 或未设置，则在 {@link #build()} 过程中不会创建 {@link LocalConfiguration} 对象，
     * 也不应调用其他会填充 {@link MediaItem#localConfiguration} 的 {@code Builder} 方法。
     */
    @CanIgnoreReturnValue
    public Builder setUri(@Nullable Uri uri) {
      this.uri = uri; // 设置 URI
      return this;
    }

    /**
     * 设置可选的 MIME 类型。
     *
     * <p>MIME 类型可以作为推断媒体项类型的提示。
     *
     * <p>只有在 {@link #setUri} 传递了非 null 值时才应调用此方法。
     *
     * @param mimeType MIME 类型。
     */
    @CanIgnoreReturnValue
    public Builder setMimeType(@Nullable String mimeType) {
      this.mimeType = mimeType; // 设置 MIME 类型
      return this;
    }

    /** 设置 {@link ClippingConfiguration}，默认为 {@link ClippingConfiguration#UNSET}。 */
    @CanIgnoreReturnValue
    public Builder setClippingConfiguration(ClippingConfiguration clippingConfiguration) {
      this.clippingConfiguration = clippingConfiguration.buildUpon(); // 使用 ClippingConfiguration 的构建器
      return this;
    }

    /**
     * @deprecated Use {@link #setClippingConfiguration(ClippingConfiguration)} and {@link
     *     ClippingConfiguration.Builder#setStartPositionMs(long)} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setClipStartPositionMs(@IntRange(from = 0) long startPositionMs) {
      clippingConfiguration.setStartPositionMs(startPositionMs);
      return this;
    }

    /**
     * @deprecated Use {@link #setClippingConfiguration(ClippingConfiguration)} and {@link
     *     ClippingConfiguration.Builder#setEndPositionMs(long)} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setClipEndPositionMs(long endPositionMs) {
      clippingConfiguration.setEndPositionMs(endPositionMs);
      return this;
    }

    /**
     * @deprecated Use {@link #setClippingConfiguration(ClippingConfiguration)} and {@link
     *     ClippingConfiguration.Builder#setRelativeToLiveWindow(boolean)} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setClipRelativeToLiveWindow(boolean relativeToLiveWindow) {
      clippingConfiguration.setRelativeToLiveWindow(relativeToLiveWindow);
      return this;
    }

    /**
     * @deprecated Use {@link #setClippingConfiguration(ClippingConfiguration)} and {@link
     *     ClippingConfiguration.Builder#setRelativeToDefaultPosition(boolean)} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setClipRelativeToDefaultPosition(boolean relativeToDefaultPosition) {
      clippingConfiguration.setRelativeToDefaultPosition(relativeToDefaultPosition);
      return this;
    }

    /**
     * @deprecated Use {@link #setClippingConfiguration(ClippingConfiguration)} and {@link
     *     ClippingConfiguration.Builder#setStartsAtKeyFrame(boolean)} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setClipStartsAtKeyFrame(boolean startsAtKeyFrame) {
      clippingConfiguration.setStartsAtKeyFrame(startsAtKeyFrame);
      return this;
    }

    /** Sets the optional DRM configuration. */
    // Using deprecated DrmConfiguration.Builder to support deprecated methods.
    @SuppressWarnings("deprecation")
    @CanIgnoreReturnValue
    public Builder setDrmConfiguration(@Nullable DrmConfiguration drmConfiguration) {
      this.drmConfiguration =
          drmConfiguration != null ? drmConfiguration.buildUpon() : new DrmConfiguration.Builder();
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setLicenseUri(Uri)} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setDrmLicenseUri(@Nullable Uri licenseUri) {
      drmConfiguration.setLicenseUri(licenseUri);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setLicenseUri(String)} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setDrmLicenseUri(@Nullable String licenseUri) {
      drmConfiguration.setLicenseUri(licenseUri);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setLicenseRequestHeaders(Map)} instead. Note that {@link
     *     DrmConfiguration.Builder#setLicenseRequestHeaders(Map)} doesn't accept null, use an empty
     *     map to clear the headers.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setDrmLicenseRequestHeaders(
        @Nullable Map<String, String> licenseRequestHeaders) {
      drmConfiguration.setLicenseRequestHeaders(
          licenseRequestHeaders != null ? licenseRequestHeaders : ImmutableMap.of());
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and pass the {@code uuid} to
     *     {@link DrmConfiguration.Builder#Builder(UUID)} instead.
     */
    @SuppressWarnings("deprecation") // Forwarding deprecated call
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setDrmUuid(@Nullable UUID uuid) {
      drmConfiguration.setNullableScheme(uuid);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setMultiSession(boolean)} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setDrmMultiSession(boolean multiSession) {
      drmConfiguration.setMultiSession(multiSession);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setForceDefaultLicenseUri(boolean)} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setDrmForceDefaultLicenseUri(boolean forceDefaultLicenseUri) {
      drmConfiguration.setForceDefaultLicenseUri(forceDefaultLicenseUri);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setPlayClearContentWithoutKey(boolean)} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setDrmPlayClearContentWithoutKey(boolean playClearContentWithoutKey) {
      drmConfiguration.setPlayClearContentWithoutKey(playClearContentWithoutKey);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setForceSessionsForAudioAndVideoTracks(boolean)} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setDrmSessionForClearPeriods(boolean sessionForClearPeriods) {
      drmConfiguration.setForceSessionsForAudioAndVideoTracks(sessionForClearPeriods);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setForcedSessionTrackTypes(List)} instead. Note that {@link
     *     DrmConfiguration.Builder#setForcedSessionTrackTypes(List)} doesn't accept null, use an
     *     empty list to clear the contents.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setDrmSessionForClearTypes(
        @Nullable List<@C.TrackType Integer> sessionForClearTypes) {
      drmConfiguration.setForcedSessionTrackTypes(
          sessionForClearTypes != null ? sessionForClearTypes : ImmutableList.of());
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setKeySetId(byte[])} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setDrmKeySetId(@Nullable byte[] keySetId) {
      drmConfiguration.setKeySetId(keySetId);
      return this;
    }

    /**
     * Sets the optional stream keys by which the manifest is filtered (only used for adaptive
     * streams).
     *
     * <p>{@code null} or an empty {@link List} can be used for a reset.
     *
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the stream keys are used to create a
     * {@link LocalConfiguration} object. Otherwise they will be ignored.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setStreamKeys(@Nullable List<StreamKey> streamKeys) {
      this.streamKeys =
          streamKeys != null && !streamKeys.isEmpty()
              ? Collections.unmodifiableList(new ArrayList<>(streamKeys))
              : Collections.emptyList();
      return this;
    }

    /**
     * Sets the optional custom cache key (only used for progressive streams).
     *
     * <p>This method should only be called if {@link #setUri} is passed a non-null value.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setCustomCacheKey(@Nullable String customCacheKey) {
      this.customCacheKey = customCacheKey;
      return this;
    }

    /**
     * @deprecated Use {@link #setSubtitleConfigurations(List)} instead. Note that {@link
     *     #setSubtitleConfigurations(List)} doesn't accept null, use an empty list to clear the
     *     contents.
     */
    @SuppressWarnings("deprecation") // Supporting deprecated type
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setSubtitles(@Nullable List<Subtitle> subtitles) {
      this.subtitleConfigurations =
          subtitles != null ? ImmutableList.copyOf(subtitles) : ImmutableList.of();
      return this;
    }

    /**
     * Sets the optional subtitles.
     *
     * <p>This method should only be called if {@link #setUri} is passed a non-null value.
     */
    @CanIgnoreReturnValue
    public Builder setSubtitleConfigurations(List<SubtitleConfiguration> subtitleConfigurations) {
      this.subtitleConfigurations = ImmutableList.copyOf(subtitleConfigurations);
      return this;
    }

    /**
     * Sets the optional {@link AdsConfiguration}.
     *
     * <p>This method should only be called if {@link #setUri} is passed a non-null value.
     */
    @CanIgnoreReturnValue
    public Builder setAdsConfiguration(@Nullable AdsConfiguration adsConfiguration) {
      this.adsConfiguration = adsConfiguration;
      return this;
    }

    /**
     * @deprecated Use {@link #setAdsConfiguration(AdsConfiguration)}, parse the {@code adTagUri}
     *     with {@link Uri#parse(String)} and pass the result to {@link
     *     AdsConfiguration.Builder#Builder(Uri)} instead.
     */
    @SuppressWarnings("deprecation") // Forwarding to other deprecated setter
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setAdTagUri(@Nullable String adTagUri) {
      return setAdTagUri(adTagUri != null ? Uri.parse(adTagUri) : null);
    }

    /**
     * @deprecated Use {@link #setAdsConfiguration(AdsConfiguration)} and pass the {@code adTagUri}
     *     to {@link AdsConfiguration.Builder#Builder(Uri)} instead.
     */
    @SuppressWarnings("deprecation") // Forwarding to other deprecated setter
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setAdTagUri(@Nullable Uri adTagUri) {
      return setAdTagUri(adTagUri, /* adsId= */ null);
    }

    /**
     * @deprecated Use {@link #setAdsConfiguration(AdsConfiguration)}, pass the {@code adTagUri} to
     *     {@link AdsConfiguration.Builder#Builder(Uri)} and the {@code adsId} to {@link
     *     AdsConfiguration.Builder#setAdsId(Object)} instead.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setAdTagUri(@Nullable Uri adTagUri, @Nullable Object adsId) {
      this.adsConfiguration =
          adTagUri != null ? new AdsConfiguration.Builder(adTagUri).setAdsId(adsId).build() : null;
      return this;
    }

    /** Sets the {@link LiveConfiguration}. Defaults to {@link LiveConfiguration#UNSET}. */
    @CanIgnoreReturnValue
    public Builder setLiveConfiguration(LiveConfiguration liveConfiguration) {
      this.liveConfiguration = liveConfiguration.buildUpon();
      return this;
    }

    /**
     * @deprecated Use {@link #setLiveConfiguration(LiveConfiguration)} and {@link
     *     LiveConfiguration.Builder#setTargetOffsetMs(long)}.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setLiveTargetOffsetMs(long liveTargetOffsetMs) {
      liveConfiguration.setTargetOffsetMs(liveTargetOffsetMs);
      return this;
    }

    /**
     * @deprecated Use {@link #setLiveConfiguration(LiveConfiguration)} and {@link
     *     LiveConfiguration.Builder#setMinOffsetMs(long)}.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setLiveMinOffsetMs(long liveMinOffsetMs) {
      liveConfiguration.setMinOffsetMs(liveMinOffsetMs);
      return this;
    }

    /**
     * @deprecated Use {@link #setLiveConfiguration(LiveConfiguration)} and {@link
     *     LiveConfiguration.Builder#setMaxOffsetMs(long)}.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setLiveMaxOffsetMs(long liveMaxOffsetMs) {
      liveConfiguration.setMaxOffsetMs(liveMaxOffsetMs);
      return this;
    }

    /**
     * @deprecated Use {@link #setLiveConfiguration(LiveConfiguration)} and {@link
     *     LiveConfiguration.Builder#setMinPlaybackSpeed(float)}.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setLiveMinPlaybackSpeed(float minPlaybackSpeed) {
      liveConfiguration.setMinPlaybackSpeed(minPlaybackSpeed);
      return this;
    }

    /**
     * @deprecated Use {@link #setLiveConfiguration(LiveConfiguration)} and {@link
     *     LiveConfiguration.Builder#setMaxPlaybackSpeed(float)}.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    @Deprecated
    public Builder setLiveMaxPlaybackSpeed(float maxPlaybackSpeed) {
      liveConfiguration.setMaxPlaybackSpeed(maxPlaybackSpeed);
      return this;
    }

    /**
     * Sets the optional tag for custom attributes. The tag for the media source which will be
     * published in the {@code androidx.media3.common.Timeline} of the source as {@code
     * androidx.media3.common.Timeline.Window#tag}.
     *
     * <p>This method should only be called if {@link #setUri} is passed a non-null value.
     */
    @CanIgnoreReturnValue
    public Builder setTag(@Nullable Object tag) {
      this.tag = tag;
      return this;
    }

    /**
     * Sets the image duration in video output, in milliseconds.
     *
     * <p>Must be set if {@linkplain #setUri the URI} is set and resolves to an image. Ignored
     * otherwise.
     *
     * <p>Motion photos will be rendered as images if this parameter is set, and as videos
     * otherwise.
     *
     * <p>Default value is {@link C#TIME_UNSET}.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Builder setImageDurationMs(long imageDurationMs) {
      checkArgument(imageDurationMs > 0 || imageDurationMs == C.TIME_UNSET);
      this.imageDurationMs = imageDurationMs;
      return this;
    }

    /** Sets the media metadata. */
    @CanIgnoreReturnValue
    public Builder setMediaMetadata(MediaMetadata mediaMetadata) {
      this.mediaMetadata = mediaMetadata;
      return this;
    }

    /** Sets the request metadata. */
    @CanIgnoreReturnValue
    public Builder setRequestMetadata(RequestMetadata requestMetadata) {
      this.requestMetadata = requestMetadata;
      return this;
    }

    /** Returns a new {@link MediaItem} instance with the current builder values. */
    @SuppressWarnings("deprecation") // Building deprecated ClippingProperties type
    public MediaItem build() {
      // TODO: remove this check once all the deprecated individual DRM setters are removed.
      checkState(drmConfiguration.licenseUri == null || drmConfiguration.scheme != null);
      @Nullable LocalConfiguration localConfiguration = null;
      @Nullable Uri uri = this.uri;
      if (uri != null) {
        localConfiguration =
            new LocalConfiguration(
                uri,
                mimeType,
                drmConfiguration.scheme != null ? drmConfiguration.build() : null,
                adsConfiguration,
                streamKeys,
                customCacheKey,
                subtitleConfigurations,
                tag,
                imageDurationMs);
      }
      return new MediaItem(
          mediaId != null ? mediaId : DEFAULT_MEDIA_ID,
          clippingConfiguration.buildClippingProperties(),
          localConfiguration,
          liveConfiguration.build(),
          mediaMetadata != null ? mediaMetadata : MediaMetadata.EMPTY,
          requestMetadata);
    }
  }

  /** DRM configuration for a media item. */
  public static final class DrmConfiguration {
    /** Builder for {@link DrmConfiguration}. */
    public static final class Builder {

      // TODO remove @Nullable annotation when the deprecated zero-arg constructor is removed.
      @Nullable private UUID scheme;
      @Nullable private Uri licenseUri;
      private ImmutableMap<String, String> licenseRequestHeaders;
      private boolean multiSession;
      private boolean playClearContentWithoutKey;
      private boolean forceDefaultLicenseUri;
      private ImmutableList<@C.TrackType Integer> forcedSessionTrackTypes;
      @Nullable private byte[] keySetId;

      /**
       * Constructs an instance.
       *
       * @param scheme The {@link UUID} of the protection scheme.
       */
      @SuppressWarnings("deprecation") // Calling deprecated constructor to reduce code duplication.
      public Builder(UUID scheme) {
        this();
        this.scheme = scheme;
      }

      /**
       * @deprecated This only exists to support the deprecated setters for individual DRM
       *     properties on {@link MediaItem.Builder}.
       */
      @Deprecated
      private Builder() {
        this.licenseRequestHeaders = ImmutableMap.of();
        this.playClearContentWithoutKey = true;
        this.forcedSessionTrackTypes = ImmutableList.of();
      }

      private Builder(DrmConfiguration drmConfiguration) {
        this.scheme = drmConfiguration.scheme;
        this.licenseUri = drmConfiguration.licenseUri;
        this.licenseRequestHeaders = drmConfiguration.licenseRequestHeaders;
        this.multiSession = drmConfiguration.multiSession;
        this.playClearContentWithoutKey = drmConfiguration.playClearContentWithoutKey;
        this.forceDefaultLicenseUri = drmConfiguration.forceDefaultLicenseUri;
        this.forcedSessionTrackTypes = drmConfiguration.forcedSessionTrackTypes;
        this.keySetId = drmConfiguration.keySetId;
      }

      /** Sets the {@link UUID} of the protection scheme. */
      @CanIgnoreReturnValue
      public Builder setScheme(UUID scheme) {
        this.scheme = scheme;
        return this;
      }

      /**
       * @deprecated This only exists to support the deprecated {@link
       *     MediaItem.Builder#setDrmUuid(UUID)}.
       */
      @CanIgnoreReturnValue
      @Deprecated
      private Builder setNullableScheme(@Nullable UUID scheme) {
        this.scheme = scheme;
        return this;
      }

      /** Sets the optional default DRM license server URI. */
      @CanIgnoreReturnValue
      public Builder setLicenseUri(@Nullable Uri licenseUri) {
        this.licenseUri = licenseUri;
        return this;
      }

      /** Sets the optional default DRM license server URI. */
      @CanIgnoreReturnValue
      public Builder setLicenseUri(@Nullable String licenseUri) {
        this.licenseUri = licenseUri == null ? null : Uri.parse(licenseUri);
        return this;
      }

      /** Sets the optional request headers attached to DRM license requests. */
      @CanIgnoreReturnValue
      public Builder setLicenseRequestHeaders(Map<String, String> licenseRequestHeaders) {
        this.licenseRequestHeaders = ImmutableMap.copyOf(licenseRequestHeaders);
        return this;
      }

      /**
       * Sets whether multi session is enabled.
       *
       * <p>The default is {@code false} (multi session disabled).
       */
      @CanIgnoreReturnValue
      public Builder setMultiSession(boolean multiSession) {
        this.multiSession = multiSession;
        return this;
      }

      /**
       * Sets whether to always use the default DRM license server URI even if the media specifies
       * its own DRM license server URI.
       *
       * <p>The default is {@code false}.
       */
      @CanIgnoreReturnValue
      public Builder setForceDefaultLicenseUri(boolean forceDefaultLicenseUri) {
        this.forceDefaultLicenseUri = forceDefaultLicenseUri;
        return this;
      }

      /**
       * Sets whether clear samples within protected content should be played when keys for the
       * encrypted part of the content have yet to be loaded.
       *
       * <p>The default is {@code true}.
       */
      @CanIgnoreReturnValue
      public Builder setPlayClearContentWithoutKey(boolean playClearContentWithoutKey) {
        this.playClearContentWithoutKey = playClearContentWithoutKey;
        return this;
      }

      /**
       * @deprecated Use {@link #setForceSessionsForAudioAndVideoTracks(boolean)} instead.
       */
      @CanIgnoreReturnValue
      @UnstableApi
      @Deprecated
      @InlineMe(
          replacement =
              "this.setForceSessionsForAudioAndVideoTracks(forceSessionsForAudioAndVideoTracks)")
      public Builder forceSessionsForAudioAndVideoTracks(
          boolean forceSessionsForAudioAndVideoTracks) {
        return setForceSessionsForAudioAndVideoTracks(forceSessionsForAudioAndVideoTracks);
      }

      /**
       * Sets whether a DRM session should be used for clear tracks of type {@link
       * C#TRACK_TYPE_VIDEO} and {@link C#TRACK_TYPE_AUDIO}.
       *
       * <p>This method overrides what has been set by previously calling {@link
       * #setForcedSessionTrackTypes(List)}.
       *
       * <p>The default is {@code false}.
       */
      @CanIgnoreReturnValue
      public Builder setForceSessionsForAudioAndVideoTracks(
          boolean forceSessionsForAudioAndVideoTracks) {
        this.setForcedSessionTrackTypes(
            forceSessionsForAudioAndVideoTracks
                ? ImmutableList.of(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)
                : ImmutableList.of());
        return this;
      }

      /**
       * Sets a list of {@link C.TrackType track type} constants for which to use a DRM session even
       * when the tracks are in the clear.
       *
       * <p>For the common case of using a DRM session for {@link C#TRACK_TYPE_VIDEO} and {@link
       * C#TRACK_TYPE_AUDIO}, {@link #setForceSessionsForAudioAndVideoTracks(boolean)} can be used.
       *
       * <p>This method overrides what has been set by previously calling {@link
       * #setForceSessionsForAudioAndVideoTracks(boolean)}.
       *
       * <p>The default is an empty list (i.e. DRM sessions are not forced for any track type).
       */
      @CanIgnoreReturnValue
      public Builder setForcedSessionTrackTypes(
          List<@C.TrackType Integer> forcedSessionTrackTypes) {
        this.forcedSessionTrackTypes = ImmutableList.copyOf(forcedSessionTrackTypes);
        return this;
      }

      /**
       * Sets the key set ID of the offline license.
       *
       * <p>The key set ID identifies an offline license. The ID is required to query, renew or
       * release an existing offline license (see {@code DefaultDrmSessionManager#setMode(int
       * mode,byte[] offlineLicenseKeySetId)}).
       */
      @CanIgnoreReturnValue
      public Builder setKeySetId(@Nullable byte[] keySetId) {
        this.keySetId = keySetId != null ? Arrays.copyOf(keySetId, keySetId.length) : null;
        return this;
      }

      public DrmConfiguration build() {
        return new DrmConfiguration(this);
      }
    }

    /** The UUID of the protection scheme. */
    public final UUID scheme;

    /**
     * @deprecated Use {@link #scheme} instead.
     */
    @UnstableApi @Deprecated public final UUID uuid;

    /**
     * Optional default DRM license server {@link Uri}. If {@code null} then the DRM license server
     * must be specified by the media.
     */
    @Nullable public final Uri licenseUri;

    /**
     * @deprecated Use {@link #licenseRequestHeaders} instead.
     */
    @UnstableApi @Deprecated public final ImmutableMap<String, String> requestHeaders;

    /** The headers to attach to requests sent to the DRM license server. */
    public final ImmutableMap<String, String> licenseRequestHeaders;

    /** Whether the DRM configuration is multi session enabled. */
    public final boolean multiSession;

    /**
     * Whether clear samples within protected content should be played when keys for the encrypted
     * part of the content have yet to be loaded.
     */
    public final boolean playClearContentWithoutKey;

    /**
     * Whether to force use of {@link #licenseUri} even if the media specifies its own DRM license
     * server URI.
     */
    public final boolean forceDefaultLicenseUri;

    /**
     * @deprecated Use {@link #forcedSessionTrackTypes}.
     */
    @UnstableApi @Deprecated public final ImmutableList<@C.TrackType Integer> sessionForClearTypes;

    /**
     * The types of tracks for which to always use a DRM session even if the content is unencrypted.
     */
    public final ImmutableList<@C.TrackType Integer> forcedSessionTrackTypes;

    @Nullable private final byte[] keySetId;

    @SuppressWarnings("deprecation") // Setting deprecated field
    private DrmConfiguration(Builder builder) {
      checkState(!(builder.forceDefaultLicenseUri && builder.licenseUri == null));
      this.scheme = checkNotNull(builder.scheme);
      this.uuid = scheme;
      this.licenseUri = builder.licenseUri;
      this.requestHeaders = builder.licenseRequestHeaders;
      this.licenseRequestHeaders = builder.licenseRequestHeaders;
      this.multiSession = builder.multiSession;
      this.forceDefaultLicenseUri = builder.forceDefaultLicenseUri;
      this.playClearContentWithoutKey = builder.playClearContentWithoutKey;
      this.sessionForClearTypes = builder.forcedSessionTrackTypes;
      this.forcedSessionTrackTypes = builder.forcedSessionTrackTypes;
      this.keySetId =
          builder.keySetId != null
              ? Arrays.copyOf(builder.keySetId, builder.keySetId.length)
              : null;
    }

    /** Returns the key set ID of the offline license. */
    @Nullable
    public byte[] getKeySetId() {
      return keySetId != null ? Arrays.copyOf(keySetId, keySetId.length) : null;
    }

    /** Returns a {@link Builder} initialized with the values of this instance. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof DrmConfiguration)) {
        return false;
      }

      DrmConfiguration other = (DrmConfiguration) obj;
      return scheme.equals(other.scheme)
          && Util.areEqual(licenseUri, other.licenseUri)
          && Util.areEqual(licenseRequestHeaders, other.licenseRequestHeaders)
          && multiSession == other.multiSession
          && forceDefaultLicenseUri == other.forceDefaultLicenseUri
          && playClearContentWithoutKey == other.playClearContentWithoutKey
          && forcedSessionTrackTypes.equals(other.forcedSessionTrackTypes)
          && Arrays.equals(keySetId, other.keySetId);
    }

    @Override
    public int hashCode() {
      int result = scheme.hashCode();
      result = 31 * result + (licenseUri != null ? licenseUri.hashCode() : 0);
      result = 31 * result + licenseRequestHeaders.hashCode();
      result = 31 * result + (multiSession ? 1 : 0);
      result = 31 * result + (forceDefaultLicenseUri ? 1 : 0);
      result = 31 * result + (playClearContentWithoutKey ? 1 : 0);
      result = 31 * result + forcedSessionTrackTypes.hashCode();
      result = 31 * result + Arrays.hashCode(keySetId);
      return result;
    }

    private static final String FIELD_SCHEME = Util.intToStringMaxRadix(0);
    private static final String FIELD_LICENSE_URI = Util.intToStringMaxRadix(1);
    private static final String FIELD_LICENSE_REQUEST_HEADERS = Util.intToStringMaxRadix(2);
    private static final String FIELD_MULTI_SESSION = Util.intToStringMaxRadix(3);

    @VisibleForTesting
    static final String FIELD_PLAY_CLEAR_CONTENT_WITHOUT_KEY = Util.intToStringMaxRadix(4);

    private static final String FIELD_FORCE_DEFAULT_LICENSE_URI = Util.intToStringMaxRadix(5);
    private static final String FIELD_FORCED_SESSION_TRACK_TYPES = Util.intToStringMaxRadix(6);
    private static final String FIELD_KEY_SET_ID = Util.intToStringMaxRadix(7);

    /** Restores a {@code DrmConfiguration} from a {@link Bundle}. */
    @UnstableApi
    public static DrmConfiguration fromBundle(Bundle bundle) {
      UUID scheme = UUID.fromString(checkNotNull(bundle.getString(FIELD_SCHEME)));
      @Nullable Uri licenseUri = bundle.getParcelable(FIELD_LICENSE_URI);
      Bundle licenseMapAsBundle =
          BundleCollectionUtil.getBundleWithDefault(
              bundle, FIELD_LICENSE_REQUEST_HEADERS, Bundle.EMPTY);
      ImmutableMap<String, String> licenseRequestHeaders =
          BundleCollectionUtil.bundleToStringImmutableMap(licenseMapAsBundle);
      boolean multiSession = bundle.getBoolean(FIELD_MULTI_SESSION, false);
      boolean playClearContentWithoutKey =
          bundle.getBoolean(FIELD_PLAY_CLEAR_CONTENT_WITHOUT_KEY, false);
      boolean forceDefaultLicenseUri = bundle.getBoolean(FIELD_FORCE_DEFAULT_LICENSE_URI, false);
      ArrayList<@C.TrackType Integer> forcedSessionTrackTypesArray =
          BundleCollectionUtil.getIntegerArrayListWithDefault(
              bundle, FIELD_FORCED_SESSION_TRACK_TYPES, new ArrayList<>());
      ImmutableList<@C.TrackType Integer> forcedSessionTrackTypes =
          ImmutableList.copyOf(forcedSessionTrackTypesArray);
      @Nullable byte[] keySetId = bundle.getByteArray(FIELD_KEY_SET_ID);

      Builder builder = new Builder(scheme);
      return builder
          .setLicenseUri(licenseUri)
          .setLicenseRequestHeaders(licenseRequestHeaders)
          .setMultiSession(multiSession)
          .setForceDefaultLicenseUri(forceDefaultLicenseUri)
          .setPlayClearContentWithoutKey(playClearContentWithoutKey)
          .setForcedSessionTrackTypes(forcedSessionTrackTypes)
          .setKeySetId(keySetId)
          .build();
    }

    @UnstableApi
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putString(FIELD_SCHEME, scheme.toString());
      if (licenseUri != null) {
        bundle.putParcelable(FIELD_LICENSE_URI, licenseUri);
      }
      if (!licenseRequestHeaders.isEmpty()) {
        bundle.putBundle(
            FIELD_LICENSE_REQUEST_HEADERS,
            BundleCollectionUtil.stringMapToBundle(licenseRequestHeaders));
      }
      if (multiSession) {
        bundle.putBoolean(FIELD_MULTI_SESSION, multiSession);
      }
      if (playClearContentWithoutKey) {
        bundle.putBoolean(FIELD_PLAY_CLEAR_CONTENT_WITHOUT_KEY, playClearContentWithoutKey);
      }
      if (forceDefaultLicenseUri) {
        bundle.putBoolean(FIELD_FORCE_DEFAULT_LICENSE_URI, forceDefaultLicenseUri);
      }
      if (!forcedSessionTrackTypes.isEmpty()) {
        bundle.putIntegerArrayList(
            FIELD_FORCED_SESSION_TRACK_TYPES, new ArrayList<>(forcedSessionTrackTypes));
      }
      if (keySetId != null) {
        bundle.putByteArray(FIELD_KEY_SET_ID, keySetId);
      }
      return bundle;
    }
  }

  /** 用于播放线性广告的配置。 */
  public static final class AdsConfiguration {

    /** {@link AdsConfiguration} 实例的构建器。 */
    public static final class Builder {

      private Uri adTagUri; // 广告标签 URI
      @Nullable private Object adsId; // 广告标识符

      /**
       * 创建一个新实例。
       *
       * @param adTagUri 要加载的广告标签 URI。
       */
      public Builder(Uri adTagUri) {
        this.adTagUri = adTagUri; // 初始化广告标签 URI
      }

      /** 设置要加载的广告标签 URI。 */
      @CanIgnoreReturnValue
      public Builder setAdTagUri(Uri adTagUri) {
        this.adTagUri = adTagUri; // 更新广告标签 URI
        return this;
      }

      /**
       * 设置广告标识符。
       *
       * <p>有关广告标识符的用途以及未显式设置时如何计算，请参阅 {@link AdsConfiguration#adsId} 的详细说明。
       */
      @CanIgnoreReturnValue
      public Builder setAdsId(@Nullable Object adsId) {
        this.adsId = adsId; // 设置广告标识符
        return this;
      }

      /** 构建 {@link AdsConfiguration} 实例。 */
      public AdsConfiguration build() {
        return new AdsConfiguration(this); // 返回构建的 AdsConfiguration 实例
      }
    }

    /** 要加载的广告标签 URI。 */
    public final Uri adTagUri;

    /**
     * 与此项关联的广告播放状态的不透明标识符，如果未设置，则使用 {@link MediaItem.Builder#setMediaId(String) 媒体 ID} 和 {@link #adTagUri 广告标签 URI} 的组合作为广告标识符。
     *
     * <p>播放列表中具有相同广告标识符和广告加载器的媒体项共享相同的广告播放状态。在从后台返回时重新创建播放列表以恢复广告播放时，请向播放器传递相同的广告标识符。
     */
    @Nullable public final Object adsId;

    private AdsConfiguration(Builder builder) {
      this.adTagUri = builder.adTagUri;
      this.adsId = builder.adsId;
    }

    /** Returns a {@link Builder} initialized with the values of this instance. */
    public Builder buildUpon() {
      return new Builder(adTagUri).setAdsId(adsId);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof AdsConfiguration)) {
        return false;
      }

      AdsConfiguration other = (AdsConfiguration) obj;
      return adTagUri.equals(other.adTagUri) && Util.areEqual(adsId, other.adsId);
    }

    @Override
    public int hashCode() {
      int result = adTagUri.hashCode();
      result = 31 * result + (adsId != null ? adsId.hashCode() : 0);
      return result;
    }

    private static final String FIELD_AD_TAG_URI = Util.intToStringMaxRadix(0);

    /** 从 {@link Bundle} 中恢复一个 {@code AdsConfiguration} 实例。 */
    @UnstableApi
    public static AdsConfiguration fromBundle(Bundle bundle) {
      @Nullable Uri adTagUri = bundle.getParcelable(FIELD_AD_TAG_URI); // 从 Bundle 中获取广告标签 URI
      checkNotNull(adTagUri); // 确保广告标签 URI 不为 null
      return new AdsConfiguration.Builder(adTagUri).build(); // 使用广告标签 URI 构建 AdsConfiguration 实例
    }

    /**
     * 返回表示此对象中存储信息的 {@link Bundle}。
     *
     * <p>它省略了 {@link #adsId} 字段。通过 {@link #fromBundle} 从此类 Bundle 恢复的实例的 {@link #adsId} 将为 {@code null}。
     */
    @UnstableApi
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putParcelable(FIELD_AD_TAG_URI, adTagUri); // 将广告标签 URI 添加到 Bundle
      return bundle; // 返回 Bundle
    }
  }

  /** 本地播放的属性配置。 */
  public static final class LocalConfiguration {

    /** 媒体资源的 {@link Uri}。 */
    public final Uri uri;

    /**
     * 可选的媒体项 MIME 类型，如果未指定则为 {@code null}。
     *
     * <p>MIME 类型可用于区分那些 URI 无法推断实际媒体类型的媒体项。
     */
    @Nullable public final String mimeType;

    /** 可选的媒体 {@link DrmConfiguration}（数字版权管理配置）。 */
    @Nullable public final DrmConfiguration drmConfiguration;

    /** 可选的广告配置。 */
    @Nullable public final AdsConfiguration adsConfiguration;

    /** 用于过滤清单的可选流密钥列表。 */
    @UnstableApi public final List<StreamKey> streamKeys;

    /** 可选的缓存键（仅用于渐进式流媒体）。 */
    @UnstableApi @Nullable public final String customCacheKey;

    /** 可选的字幕配置列表，用于侧载字幕。 */
    public final ImmutableList<SubtitleConfiguration> subtitleConfigurations;

    /**
     * @deprecated 请使用 {@link #subtitleConfigurations} 替代。
     */
    @SuppressWarnings("deprecation") // 在已弃用的字段中使用已弃用的类型
    @UnstableApi
    @Deprecated
    public final List<Subtitle> subtitles;

    /**
     * 用于自定义属性的可选标签。该标签将作为 {@code androidx.media3.common.Timeline.Window#tag} 发布在媒体源的 {@code androidx.media3.common.Timeline} 中。
     */
    @Nullable public final Object tag;

    /** 图像资源的持续时间，单位为毫秒。 */
    @UnstableApi public final long imageDurationMs;

    @SuppressWarnings("deprecation") // 设置已弃用的 subtitles 字段。
    private LocalConfiguration(
        Uri uri,
        @Nullable String mimeType,
        @Nullable DrmConfiguration drmConfiguration,
        @Nullable AdsConfiguration adsConfiguration,
        List<StreamKey> streamKeys,
        @Nullable String customCacheKey,
        ImmutableList<SubtitleConfiguration> subtitleConfigurations,
        @Nullable Object tag,
        long imageDurationMs) {
      this.uri = uri;
      this.mimeType = MimeTypes.normalizeMimeType(mimeType); // 规范化 MIME 类型
      this.drmConfiguration = drmConfiguration;
      this.adsConfiguration = adsConfiguration;
      this.streamKeys = streamKeys;
      this.customCacheKey = customCacheKey;
      this.subtitleConfigurations = subtitleConfigurations;
      ImmutableList.Builder<Subtitle> subtitles = ImmutableList.builder();
      for (int i = 0; i < subtitleConfigurations.size(); i++) {
        subtitles.add(subtitleConfigurations.get(i).buildUpon().buildSubtitle()); // 构建字幕列表
      }
      this.subtitles = subtitles.build();
      this.tag = tag;
      this.imageDurationMs = imageDurationMs;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true; // 如果是同一个对象，返回 true
      }
      if (!(obj instanceof LocalConfiguration)) {
        return false; // 如果对象不是 LocalConfiguration 类型，返回 false
      }
      LocalConfiguration other = (LocalConfiguration) obj;

      return uri.equals(other.uri)
          && Util.areEqual(mimeType, other.mimeType)
          && Util.areEqual(drmConfiguration, other.drmConfiguration)
          && Util.areEqual(adsConfiguration, other.adsConfiguration)
          && streamKeys.equals(other.streamKeys)
          && Util.areEqual(customCacheKey, other.customCacheKey)
          && subtitleConfigurations.equals(other.subtitleConfigurations)
          && Util.areEqual(tag, other.tag)
          && Util.areEqual(imageDurationMs, other.imageDurationMs); // 比较所有字段
    }

    @Override
    public int hashCode() {
      int result = uri.hashCode();
      result = 31 * result + (mimeType == null ? 0 : mimeType.hashCode());
      result = 31 * result + (drmConfiguration == null ? 0 : drmConfiguration.hashCode());
      result = 31 * result + (adsConfiguration == null ? 0 : adsConfiguration.hashCode());
      result = 31 * result + streamKeys.hashCode();
      result = 31 * result + (customCacheKey == null ? 0 : customCacheKey.hashCode());
      result = 31 * result + subtitleConfigurations.hashCode();
      result = 31 * result + (tag == null ? 0 : tag.hashCode());
      result = (int) (31L * result + imageDurationMs);
      return result; // 计算哈希值
    }

    private static final String FIELD_URI = Util.intToStringMaxRadix(0);
    private static final String FIELD_MIME_TYPE = Util.intToStringMaxRadix(1);
    private static final String FIELD_DRM_CONFIGURATION = Util.intToStringMaxRadix(2);
    private static final String FIELD_ADS_CONFIGURATION = Util.intToStringMaxRadix(3);
    private static final String FIELD_STREAM_KEYS = Util.intToStringMaxRadix(4);
    private static final String FIELD_CUSTOM_CACHE_KEY = Util.intToStringMaxRadix(5);
    private static final String FIELD_SUBTITLE_CONFIGURATION = Util.intToStringMaxRadix(6);
    private static final String FIELD_IMAGE_DURATION_MS = Util.intToStringMaxRadix(7);

    /**
     * 返回表示此对象中存储信息的 {@link Bundle}。
     *
     * <p>它省略了 {@link #tag} 字段。通过 {@link #fromBundle} 从此类 Bundle 恢复的实例的 {@link #tag} 将为 {@code null}。
     */
    @UnstableApi
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putParcelable(FIELD_URI, uri); // 添加 URI
      if (mimeType != null) {
        bundle.putString(FIELD_MIME_TYPE, mimeType); // 添加 MIME 类型
      }
      if (drmConfiguration != null) {
        bundle.putBundle(FIELD_DRM_CONFIGURATION, drmConfiguration.toBundle()); // 添加 DRM 配置
      }
      if (adsConfiguration != null) {
        bundle.putBundle(FIELD_ADS_CONFIGURATION, adsConfiguration.toBundle()); // 添加广告配置
      }
      if (!streamKeys.isEmpty()) {
        bundle.putParcelableArrayList(
            FIELD_STREAM_KEYS,
            BundleCollectionUtil.toBundleArrayList(streamKeys, StreamKey::toBundle)); // 添加流密钥
      }
      if (customCacheKey != null) {
        bundle.putString(FIELD_CUSTOM_CACHE_KEY, customCacheKey); // 添加自定义缓存键
      }
      if (!subtitleConfigurations.isEmpty()) {
        bundle.putParcelableArrayList(
            FIELD_SUBTITLE_CONFIGURATION,
            BundleCollectionUtil.toBundleArrayList(
                subtitleConfigurations, SubtitleConfiguration::toBundle)); // 添加字幕配置
      }
      if (imageDurationMs != C.TIME_UNSET) {
        bundle.putLong(FIELD_IMAGE_DURATION_MS, imageDurationMs); // 添加图像持续时间
      }
      return bundle; // 返回 Bundle
    }

    /** 从 {@link Bundle} 中恢复一个 {@code LocalConfiguration} 实例。 */
    @UnstableApi
    public static LocalConfiguration fromBundle(Bundle bundle) {
      @Nullable Bundle drmBundle = bundle.getBundle(FIELD_DRM_CONFIGURATION);
      DrmConfiguration drmConfiguration =
          drmBundle == null ? null : DrmConfiguration.fromBundle(drmBundle); // 恢复 DRM 配置
      @Nullable Bundle adsBundle = bundle.getBundle(FIELD_ADS_CONFIGURATION);
      AdsConfiguration adsConfiguration =
          adsBundle == null ? null : AdsConfiguration.fromBundle(adsBundle); // 恢复广告配置
      @Nullable List<Bundle> streamKeysBundles = bundle.getParcelableArrayList(FIELD_STREAM_KEYS);
      List<StreamKey> streamKeys =
          streamKeysBundles == null
              ? ImmutableList.of()
              : BundleCollectionUtil.fromBundleList(StreamKey::fromBundle, streamKeysBundles); // 恢复流密钥
      @Nullable
      List<Bundle> subtitleBundles = bundle.getParcelableArrayList(FIELD_SUBTITLE_CONFIGURATION);
      ImmutableList<SubtitleConfiguration> subtitleConfiguration =
          subtitleBundles == null
              ? ImmutableList.of()
              : BundleCollectionUtil.fromBundleList(
                  SubtitleConfiguration::fromBundle, subtitleBundles); // 恢复字幕配置
      long imageDurationMs = bundle.getLong(FIELD_IMAGE_DURATION_MS, C.TIME_UNSET); // 恢复图像持续时间

      return new LocalConfiguration(
          checkNotNull(bundle.getParcelable(FIELD_URI)), // 恢复 URI
          bundle.getString(FIELD_MIME_TYPE), // 恢复 MIME 类型
          drmConfiguration,
          adsConfiguration,
          streamKeys,
          bundle.getString(FIELD_CUSTOM_CACHE_KEY), // 恢复自定义缓存键
          subtitleConfiguration,
          /* tag= */ null,
          imageDurationMs);
    }
  }

  /** 直播播放配置。 */
  public static final class LiveConfiguration {

    /** {@link LiveConfiguration} 实例的构建器。 */
    public static final class Builder {
      private long targetOffsetMs; // 目标直播偏移量（毫秒）
      private long minOffsetMs; // 最小允许的直播偏移量（毫秒）
      private long maxOffsetMs; // 最大允许的直播偏移量（毫秒）
      private float minPlaybackSpeed; // 最小播放速度
      private float maxPlaybackSpeed; // 最大播放速度

      /** 使用默认值创建一个新实例。 */
      public Builder() {
        this.targetOffsetMs = C.TIME_UNSET; // 默认值为未设置
        this.minOffsetMs = C.TIME_UNSET; // 默认值为未设置
        this.maxOffsetMs = C.TIME_UNSET; // 默认值为未设置
        this.minPlaybackSpeed = C.RATE_UNSET; // 默认值为未设置
        this.maxPlaybackSpeed = C.RATE_UNSET; // 默认值为未设置
      }

      private Builder(LiveConfiguration liveConfiguration) {
        this.targetOffsetMs = liveConfiguration.targetOffsetMs; // 从现有配置中复制目标偏移量
        this.minOffsetMs = liveConfiguration.minOffsetMs; // 从现有配置中复制最小偏移量
        this.maxOffsetMs = liveConfiguration.maxOffsetMs; // 从现有配置中复制最大偏移量
        this.minPlaybackSpeed = liveConfiguration.minPlaybackSpeed; // 从现有配置中复制最小播放速度
        this.maxPlaybackSpeed = liveConfiguration.maxPlaybackSpeed; // 从现有配置中复制最大播放速度
      }

      /**
       * 设置目标直播偏移量，单位为毫秒。
       *
       * <p>参见 {@code Player#getCurrentLiveOffset()}。
       *
       * <p>默认值为 {@link C#TIME_UNSET}，表示使用媒体定义的默认值。
       */
      @CanIgnoreReturnValue
      public Builder setTargetOffsetMs(long targetOffsetMs) {
        this.targetOffsetMs = targetOffsetMs; // 设置目标偏移量
        return this;
      }

      /**
       * 设置最小允许的直播偏移量，单位为毫秒。
       *
       * <p>参见 {@code Player#getCurrentLiveOffset()}。
       *
       * <p>默认值为 {@link C#TIME_UNSET}，表示使用媒体定义的默认值。
       */
      @CanIgnoreReturnValue
      public Builder setMinOffsetMs(long minOffsetMs) {
        this.minOffsetMs = minOffsetMs; // 设置最小偏移量
        return this;
      }

      /**
       * 设置最大允许的直播偏移量，单位为毫秒。
       *
       * <p>参见 {@code Player#getCurrentLiveOffset()}。
       *
       * <p>默认值为 {@link C#TIME_UNSET}，表示使用媒体定义的默认值。
       */
      @CanIgnoreReturnValue
      public Builder setMaxOffsetMs(long maxOffsetMs) {
        this.maxOffsetMs = maxOffsetMs; // 设置最大偏移量
        return this;
      }

      /**
       * 设置最小播放速度。
       *
       * <p>默认值为 {@link C#RATE_UNSET}，表示使用媒体定义的默认值。
       */
      @CanIgnoreReturnValue
      public Builder setMinPlaybackSpeed(float minPlaybackSpeed) {
        this.minPlaybackSpeed = minPlaybackSpeed; // 设置最小播放速度
        return this;
      }

      /**
       * 设置最大播放速度。
       *
       * <p>默认值为 {@link C#RATE_UNSET}，表示使用媒体定义的默认值。
       */
      @CanIgnoreReturnValue
      public Builder setMaxPlaybackSpeed(float maxPlaybackSpeed) {
        this.maxPlaybackSpeed = maxPlaybackSpeed; // 设置最大播放速度
        return this;
      }

      /** 使用此构建器的值创建一个 {@link LiveConfiguration} 实例。 */
      public LiveConfiguration build() {
        return new LiveConfiguration(this); // 构建并返回 LiveConfiguration 实例
      }
    }

    /**
     * 一个未设置的直播播放配置，表示将使用媒体定义的默认值。
     */
    public static final LiveConfiguration UNSET = new LiveConfiguration.Builder().build();

    /**
     * 目标直播边缘的偏移量，单位为毫秒，或 {@link C#TIME_UNSET} 以使用媒体定义的默认值。
     */
    public final long targetOffsetMs;

    /**
     * 允许的最小直播边缘偏移量，单位为毫秒，或 {@link C#TIME_UNSET} 以使用媒体定义的默认值。
     */
    public final long minOffsetMs;

    /**
     * 允许的最大直播边缘偏移量，单位为毫秒，或 {@link C#TIME_UNSET} 以使用媒体定义的默认值。
     */
    public final long maxOffsetMs;

    /**
     * 播放速度的最小倍数，或 {@link C#RATE_UNSET} 以使用媒体定义的默认值。
     */
    public final float minPlaybackSpeed;

    /**
     * 播放速度的最大倍数，或 {@link C#RATE_UNSET} 以使用媒体定义的默认值。
     */
    public final float maxPlaybackSpeed;

    @SuppressWarnings("deprecation") // 在构造函数存在时使用已弃用的构造函数。
    private LiveConfiguration(Builder builder) {
      this(
          builder.targetOffsetMs,
          builder.minOffsetMs,
          builder.maxOffsetMs,
          builder.minPlaybackSpeed,
          builder.maxPlaybackSpeed);
    }

    /**
     * @deprecated 请使用 {@link Builder} 替代。
     */
    @UnstableApi
    @Deprecated
    public LiveConfiguration(
        long targetOffsetMs,
        long minOffsetMs,
        long maxOffsetMs,
        float minPlaybackSpeed,
        float maxPlaybackSpeed) {
      this.targetOffsetMs = targetOffsetMs; // 设置目标偏移量
      this.minOffsetMs = minOffsetMs; // 设置最小偏移量
      this.maxOffsetMs = maxOffsetMs; // 设置最大偏移量
      this.minPlaybackSpeed = minPlaybackSpeed; // 设置最小播放速度
      this.maxPlaybackSpeed = maxPlaybackSpeed; // 设置最大播放速度
    }

    /** 返回一个用此实例的值初始化的 {@link Builder}。 */
    public Builder buildUpon() {
      return new Builder(this); // 返回一个新的构建器实例
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true; // 如果是同一个对象，返回 true
      }
      if (!(obj instanceof LiveConfiguration)) {
        return false; // 如果对象不是 LiveConfiguration 类型，返回 false
      }
      LiveConfiguration other = (LiveConfiguration) obj;

      return targetOffsetMs == other.targetOffsetMs
          && minOffsetMs == other.minOffsetMs
          && maxOffsetMs == other.maxOffsetMs
          && minPlaybackSpeed == other.minPlaybackSpeed
          && maxPlaybackSpeed == other.maxPlaybackSpeed; // 比较所有字段
    }

    @Override
    public int hashCode() {
      int result = (int) (targetOffsetMs ^ (targetOffsetMs >>> 32));
      result = 31 * result + (int) (minOffsetMs ^ (minOffsetMs >>> 32));
      result = 31 * result + (int) (maxOffsetMs ^ (maxOffsetMs >>> 32));
      result = 31 * result + (minPlaybackSpeed != 0 ? Float.floatToIntBits(minPlaybackSpeed) : 0);
      result = 31 * result + (maxPlaybackSpeed != 0 ? Float.floatToIntBits(maxPlaybackSpeed) : 0);
      return result; // 计算哈希值
    }

    private static final String FIELD_TARGET_OFFSET_MS = Util.intToStringMaxRadix(0);
    private static final String FIELD_MIN_OFFSET_MS = Util.intToStringMaxRadix(1);
    private static final String FIELD_MAX_OFFSET_MS = Util.intToStringMaxRadix(2);
    private static final String FIELD_MIN_PLAYBACK_SPEED = Util.intToStringMaxRadix(3);
    private static final String FIELD_MAX_PLAYBACK_SPEED = Util.intToStringMaxRadix(4);

    @UnstableApi
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      if (targetOffsetMs != UNSET.targetOffsetMs) {
        bundle.putLong(FIELD_TARGET_OFFSET_MS, targetOffsetMs); // 添加目标偏移量
      }
      if (minOffsetMs != UNSET.minOffsetMs) {
        bundle.putLong(FIELD_MIN_OFFSET_MS, minOffsetMs); // 添加最小偏移量
      }
      if (maxOffsetMs != UNSET.maxOffsetMs) {
        bundle.putLong(FIELD_MAX_OFFSET_MS, maxOffsetMs); // 添加最大偏移量
      }
      if (minPlaybackSpeed != UNSET.minPlaybackSpeed) {
        bundle.putFloat(FIELD_MIN_PLAYBACK_SPEED, minPlaybackSpeed); // 添加最小播放速度
      }
      if (maxPlaybackSpeed != UNSET.maxPlaybackSpeed) {
        bundle.putFloat(FIELD_MAX_PLAYBACK_SPEED, maxPlaybackSpeed); // 添加最大播放速度
      }
      return bundle; // 返回 Bundle
    }

    /** 从 {@link Bundle} 中恢复一个 {@code LiveConfiguration} 实例。 */
    @UnstableApi
    public static LiveConfiguration fromBundle(Bundle bundle) {
      return new LiveConfiguration.Builder()
          .setTargetOffsetMs(
              bundle.getLong(FIELD_TARGET_OFFSET_MS, /* defaultValue= */ UNSET.targetOffsetMs))
          .setMinOffsetMs(
              bundle.getLong(FIELD_MIN_OFFSET_MS, /* defaultValue= */ UNSET.minOffsetMs))
          .setMaxOffsetMs(
              bundle.getLong(FIELD_MAX_OFFSET_MS, /* defaultValue= */ UNSET.maxOffsetMs))
          .setMinPlaybackSpeed(
              bundle.getFloat(FIELD_MIN_PLAYBACK_SPEED, /* defaultValue= */ UNSET.minPlaybackSpeed))
          .setMaxPlaybackSpeed(
              bundle.getFloat(FIELD_MAX_PLAYBACK_SPEED, /* defaultValue= */ UNSET.maxPlaybackSpeed))
          .build(); // 从 Bundle 中恢复配置
    }
  }

  /** Properties for a text track. */
  // TODO: Mark this final when Subtitle is deleted.
  public static class SubtitleConfiguration {

    /** Builder for {@link SubtitleConfiguration} instances. */
    public static final class Builder {
      private Uri uri;
      @Nullable private String mimeType;
      @Nullable private String language;
      private @C.SelectionFlags int selectionFlags;
      private @C.RoleFlags int roleFlags;
      @Nullable private String label;
      @Nullable private String id;

      /**
       * Constructs an instance.
       *
       * @param uri The {@link Uri} to the subtitle file.
       */
      public Builder(Uri uri) {
        this.uri = uri;
      }

      private Builder(SubtitleConfiguration subtitleConfiguration) {
        this.uri = subtitleConfiguration.uri;
        this.mimeType = subtitleConfiguration.mimeType;
        this.language = subtitleConfiguration.language;
        this.selectionFlags = subtitleConfiguration.selectionFlags;
        this.roleFlags = subtitleConfiguration.roleFlags;
        this.label = subtitleConfiguration.label;
        this.id = subtitleConfiguration.id;
      }

      /** Sets the {@link Uri} to the subtitle file. */
      @CanIgnoreReturnValue
      public Builder setUri(Uri uri) {
        this.uri = uri;
        return this;
      }

      /** Sets the MIME type. */
      @CanIgnoreReturnValue
      public Builder setMimeType(@Nullable String mimeType) {
        this.mimeType = MimeTypes.normalizeMimeType(mimeType);
        return this;
      }

      /** Sets the optional language of the subtitle file. */
      @CanIgnoreReturnValue
      public Builder setLanguage(@Nullable String language) {
        this.language = language;
        return this;
      }

      /** Sets the flags used for track selection. */
      @CanIgnoreReturnValue
      public Builder setSelectionFlags(@C.SelectionFlags int selectionFlags) {
        this.selectionFlags = selectionFlags;
        return this;
      }

      /** Sets the role flags. These are used for track selection. */
      @CanIgnoreReturnValue
      public Builder setRoleFlags(@C.RoleFlags int roleFlags) {
        this.roleFlags = roleFlags;
        return this;
      }

      /** Sets the optional label for this subtitle track. */
      @CanIgnoreReturnValue
      public Builder setLabel(@Nullable String label) {
        this.label = label;
        return this;
      }

      /** Sets the optional ID for this subtitle track. */
      @CanIgnoreReturnValue
      public Builder setId(@Nullable String id) {
        this.id = id;
        return this;
      }

      /** Creates a {@link SubtitleConfiguration} from the values of this builder. */
      public SubtitleConfiguration build() {
        return new SubtitleConfiguration(this);
      }

      @SuppressWarnings("deprecation") // Building deprecated type to support deprecated builder
      private Subtitle buildSubtitle() {
        return new Subtitle(this);
      }
    }

    /** The {@link Uri} to the subtitle file. */
    public final Uri uri;

    /** The optional MIME type of the subtitle file, or {@code null} if unspecified. */
    @Nullable public final String mimeType;

    /** The language. */
    @Nullable public final String language;

    /** The selection flags. */
    public final @C.SelectionFlags int selectionFlags;

    /** The role flags. */
    public final @C.RoleFlags int roleFlags;

    /** The label. */
    @Nullable public final String label;

    /**
     * The ID of the subtitles. This will be propagated to the {@link Format#id} of the subtitle
     * track created from this configuration.
     */
    @Nullable public final String id;

    private SubtitleConfiguration(
        Uri uri,
        String mimeType,
        @Nullable String language,
        int selectionFlags,
        int roleFlags,
        @Nullable String label,
        @Nullable String id) {
      this.uri = uri;
      this.mimeType = MimeTypes.normalizeMimeType(mimeType);
      this.language = language;
      this.selectionFlags = selectionFlags;
      this.roleFlags = roleFlags;
      this.label = label;
      this.id = id;
    }

    private SubtitleConfiguration(Builder builder) {
      this.uri = builder.uri;
      this.mimeType = builder.mimeType;
      this.language = builder.language;
      this.selectionFlags = builder.selectionFlags;
      this.roleFlags = builder.roleFlags;
      this.label = builder.label;
      this.id = builder.id;
    }

    /** Returns a {@link Builder} initialized with the values of this instance. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof SubtitleConfiguration)) {
        return false;
      }

      SubtitleConfiguration other = (SubtitleConfiguration) obj;

      return uri.equals(other.uri)
          && Util.areEqual(mimeType, other.mimeType)
          && Util.areEqual(language, other.language)
          && selectionFlags == other.selectionFlags
          && roleFlags == other.roleFlags
          && Util.areEqual(label, other.label)
          && Util.areEqual(id, other.id);
    }

    @Override
    public int hashCode() {
      int result = uri.hashCode();
      result = 31 * result + (mimeType == null ? 0 : mimeType.hashCode());
      result = 31 * result + (language == null ? 0 : language.hashCode());
      result = 31 * result + selectionFlags;
      result = 31 * result + roleFlags;
      result = 31 * result + (label == null ? 0 : label.hashCode());
      result = 31 * result + (id == null ? 0 : id.hashCode());
      return result;
    }

    private static final String FIELD_URI = Util.intToStringMaxRadix(0);
    private static final String FIELD_MIME_TYPE = Util.intToStringMaxRadix(1);
    private static final String FIELD_LANGUAGE = Util.intToStringMaxRadix(2);
    private static final String FIELD_SELECTION_FLAGS = Util.intToStringMaxRadix(3);
    private static final String FIELD_ROLE_FLAGS = Util.intToStringMaxRadix(4);
    private static final String FIELD_LABEL = Util.intToStringMaxRadix(5);
    private static final String FIELD_ID = Util.intToStringMaxRadix(6);

    /** Restores a {@code SubtitleConfiguration} from a {@link Bundle}. */
    @UnstableApi
    public static SubtitleConfiguration fromBundle(Bundle bundle) {
      Uri uri = checkNotNull(bundle.getParcelable(FIELD_URI));
      @Nullable String mimeType = bundle.getString(FIELD_MIME_TYPE);
      @Nullable String language = bundle.getString(FIELD_LANGUAGE);
      @C.SelectionFlags int selectionFlags = bundle.getInt(FIELD_SELECTION_FLAGS, 0);
      @C.RoleFlags int roleFlags = bundle.getInt(FIELD_ROLE_FLAGS, 0);
      @Nullable String label = bundle.getString(FIELD_LABEL);
      @Nullable String id = bundle.getString(FIELD_ID);

      SubtitleConfiguration.Builder builder = new SubtitleConfiguration.Builder(uri);
      return builder
          .setMimeType(mimeType)
          .setLanguage(language)
          .setSelectionFlags(selectionFlags)
          .setRoleFlags(roleFlags)
          .setLabel(label)
          .setId(id)
          .build();
    }

    @UnstableApi
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putParcelable(FIELD_URI, uri);
      if (mimeType != null) {
        bundle.putString(FIELD_MIME_TYPE, mimeType);
      }
      if (language != null) {
        bundle.putString(FIELD_LANGUAGE, language);
      }
      if (selectionFlags != 0) {
        bundle.putInt(FIELD_SELECTION_FLAGS, selectionFlags);
      }
      if (roleFlags != 0) {
        bundle.putInt(FIELD_ROLE_FLAGS, roleFlags);
      }
      if (label != null) {
        bundle.putString(FIELD_LABEL, label);
      }
      if (id != null) {
        bundle.putString(FIELD_ID, id);
      }
      return bundle;
    }
  }

  /**
   * @deprecated Use {@link MediaItem.SubtitleConfiguration} instead
   */
  @UnstableApi
  @Deprecated
  public static final class Subtitle extends SubtitleConfiguration {

    /**
     * @deprecated Use {@link Builder} instead.
     */
    @SuppressWarnings("deprecation") // Forwarding to other deprecated constructor
    @UnstableApi
    @Deprecated
    public Subtitle(Uri uri, String mimeType, @Nullable String language) {
      this(uri, mimeType, language, /* selectionFlags= */ 0);
    }

    /**
     * @deprecated Use {@link Builder} instead.
     */
    @SuppressWarnings("deprecation") // Forwarding to other deprecated constructor
    @UnstableApi
    @Deprecated
    public Subtitle(
        Uri uri, String mimeType, @Nullable String language, @C.SelectionFlags int selectionFlags) {
      this(uri, mimeType, language, selectionFlags, /* roleFlags= */ 0, /* label= */ null);
    }

    /**
     * @deprecated Use {@link Builder} instead.
     */
    @UnstableApi
    @Deprecated
    public Subtitle(
        Uri uri,
        String mimeType,
        @Nullable String language,
        @C.SelectionFlags int selectionFlags,
        @C.RoleFlags int roleFlags,
        @Nullable String label) {
      super(uri, mimeType, language, selectionFlags, roleFlags, label, /* id= */ null);
    }

    private Subtitle(Builder builder) {
      super(builder);
    }
  }
  /** 可选地将媒体项剪辑到自定义的开始和结束位置。 */
// TODO: 当 ClippingProperties 被删除时，将此标记为 final。
  public static class ClippingConfiguration {

    /** 具有默认值的剪辑配置。 */
    public static final ClippingConfiguration UNSET = new ClippingConfiguration.Builder().build();

    /** {@link ClippingConfiguration} 实例的构建器。 */
    public static final class Builder {
      private long startPositionUs; // 开始位置（微秒）
      private long endPositionUs; // 结束位置（微秒）
      private boolean relativeToLiveWindow; // 是否相对于直播窗口
      private boolean relativeToDefaultPosition; // 是否相对于默认位置
      private boolean startsAtKeyFrame; // 是否从关键帧开始

      /** 使用默认值创建一个新实例。 */
      public Builder() {
        endPositionUs = C.TIME_END_OF_SOURCE; // 默认结束位置为媒体末尾
      }

      private Builder(ClippingConfiguration clippingConfiguration) {
        startPositionUs = clippingConfiguration.startPositionUs; // 复制开始位置
        endPositionUs = clippingConfiguration.endPositionUs; // 复制结束位置
        relativeToLiveWindow = clippingConfiguration.relativeToLiveWindow; // 复制是否相对于直播窗口
        relativeToDefaultPosition = clippingConfiguration.relativeToDefaultPosition; // 复制是否相对于默认位置
        startsAtKeyFrame = clippingConfiguration.startsAtKeyFrame; // 复制是否从关键帧开始
      }

      /**
       * 设置可选的开始位置（毫秒），必须大于或等于零（默认值：0）。
       */
      @CanIgnoreReturnValue
      public Builder setStartPositionMs(@IntRange(from = 0) long startPositionMs) {
        return setStartPositionUs(msToUs(startPositionMs)); // 将毫秒转换为微秒并设置
      }

      /**
       * 设置可选的开始位置（微秒），必须大于或等于零（默认值：0）。
       */
      @UnstableApi
      @CanIgnoreReturnValue
      public Builder setStartPositionUs(@IntRange(from = 0) long startPositionUs) {
        Assertions.checkArgument(startPositionUs >= 0); // 验证开始位置是否合法
        this.startPositionUs = startPositionUs; // 设置开始位置
        return this;
      }

      /**
       * 设置可选的结束位置（毫秒），必须大于或等于零，或 {@link C#TIME_END_OF_SOURCE} 表示播放到媒体末尾（默认值：{@link C#TIME_END_OF_SOURCE}）。
       */
      @CanIgnoreReturnValue
      public Builder setEndPositionMs(long endPositionMs) {
        return setEndPositionUs(msToUs(endPositionMs)); // 将毫秒转换为微秒并设置
      }

      /**
       * 设置可选的结束位置（微秒），必须大于或等于零，或 {@link C#TIME_END_OF_SOURCE} 表示播放到媒体末尾（默认值：{@link C#TIME_END_OF_SOURCE}）。
       */
      @UnstableApi
      @CanIgnoreReturnValue
      public Builder setEndPositionUs(long endPositionUs) {
        Assertions.checkArgument(endPositionUs == C.TIME_END_OF_SOURCE || endPositionUs >= 0); // 验证结束位置是否合法
        this.endPositionUs = endPositionUs; // 设置结束位置
        return this;
      }

      /**
       * 设置开始/结束位置是否应随直播窗口移动。如果为 {@code false}，直播流将在播放到达首次加载媒体时看到的直播窗口的结束位置时结束（默认值：{@code false}）。
       */
      @CanIgnoreReturnValue
      public Builder setRelativeToLiveWindow(boolean relativeToLiveWindow) {
        this.relativeToLiveWindow = relativeToLiveWindow; // 设置是否相对于直播窗口
        return this;
      }

      /**
       * 设置开始位置和结束位置是否相对于窗口中的默认位置（默认值：{@code false}）。
       */
      @CanIgnoreReturnValue
      public Builder setRelativeToDefaultPosition(boolean relativeToDefaultPosition) {
        this.relativeToDefaultPosition = relativeToDefaultPosition; // 设置是否相对于默认位置
        return this;
      }

      /**
       * 设置起始点是否保证是关键帧。如果为 {@code false}，进入剪辑的播放过渡可能不流畅（默认值：{@code false}）。
       */
      @CanIgnoreReturnValue
      public Builder setStartsAtKeyFrame(boolean startsAtKeyFrame) {
        this.startsAtKeyFrame = startsAtKeyFrame; // 设置是否从关键帧开始
        return this;
      }

      /**
       * 返回一个用此构建器的值初始化的 {@link ClippingConfiguration} 实例。
       */
      public ClippingConfiguration build() {
        return new ClippingConfiguration(this); // 构建并返回 ClippingConfiguration 实例
      }

      /**
       * @deprecated 请使用 {@link #build()} 替代。
       */
      @SuppressWarnings("deprecation") // 构建已弃用的类型以支持已弃用的方法
      @UnstableApi
      @Deprecated
      public ClippingProperties buildClippingProperties() {
        return new ClippingProperties(this); // 构建并返回 ClippingProperties 实例
      }
    }
    /** 开始位置（毫秒）。该值必须大于或等于零。 */
    @IntRange(from = 0)
    public final long startPositionMs;

    /** 开始位置（微秒）。该值必须大于或等于零。 */
    @UnstableApi
    @IntRange(from = 0)
    public final long startPositionUs;

    /**
     * 结束位置（毫秒）。该值必须大于或等于零，或 {@link C#TIME_END_OF_SOURCE} 表示播放到流末尾。
     */
    public final long endPositionMs;

    /**
     * 结束位置（微秒）。该值必须大于或等于零，或 {@link C#TIME_END_OF_SOURCE} 表示播放到流末尾。
     */
    @UnstableApi public final long endPositionUs;

    /**
     * 活动媒体周期的剪辑是否随直播窗口移动。如果为 {@code false}，播放将在到达 {@link #endPositionMs} 时结束。
     */
    public final boolean relativeToLiveWindow;

    /**
     * {@link #startPositionMs} 和 {@link #endPositionMs} 是否相对于默认位置。
     */
    public final boolean relativeToDefaultPosition;

    /** 设置起始点是否保证是关键帧。 */
    public final boolean startsAtKeyFrame;

    private ClippingConfiguration(Builder builder) {
      this.startPositionMs = usToMs(builder.startPositionUs);
      this.endPositionMs = usToMs(builder.endPositionUs);
      this.startPositionUs = builder.startPositionUs;
      this.endPositionUs = builder.endPositionUs;
      this.relativeToLiveWindow = builder.relativeToLiveWindow;
      this.relativeToDefaultPosition = builder.relativeToDefaultPosition;
      this.startsAtKeyFrame = builder.startsAtKeyFrame;
    }

    /** Returns a {@link Builder} initialized with the values of this instance. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof ClippingConfiguration)) {
        return false;
      }

      ClippingConfiguration other = (ClippingConfiguration) obj;

      return startPositionUs == other.startPositionUs
          && endPositionUs == other.endPositionUs
          && relativeToLiveWindow == other.relativeToLiveWindow
          && relativeToDefaultPosition == other.relativeToDefaultPosition
          && startsAtKeyFrame == other.startsAtKeyFrame;
    }

    @Override
    public int hashCode() {
      int result = (int) (startPositionUs ^ (startPositionUs >>> 32));
      result = 31 * result + (int) (endPositionUs ^ (endPositionUs >>> 32));
      result = 31 * result + (relativeToLiveWindow ? 1 : 0);
      result = 31 * result + (relativeToDefaultPosition ? 1 : 0);
      result = 31 * result + (startsAtKeyFrame ? 1 : 0);
      return result;
    }

    private static final String FIELD_START_POSITION_MS = Util.intToStringMaxRadix(0);
    private static final String FIELD_END_POSITION_MS = Util.intToStringMaxRadix(1);
    private static final String FIELD_RELATIVE_TO_LIVE_WINDOW = Util.intToStringMaxRadix(2);
    private static final String FIELD_RELATIVE_TO_DEFAULT_POSITION = Util.intToStringMaxRadix(3);
    private static final String FIELD_STARTS_AT_KEY_FRAME = Util.intToStringMaxRadix(4);
    static final String FIELD_START_POSITION_US = Util.intToStringMaxRadix(5);
    static final String FIELD_END_POSITION_US = Util.intToStringMaxRadix(6);

    @UnstableApi
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      if (startPositionMs != UNSET.startPositionMs) {
        bundle.putLong(FIELD_START_POSITION_MS, startPositionMs);
      }
      if (endPositionMs != UNSET.endPositionMs) {
        bundle.putLong(FIELD_END_POSITION_MS, endPositionMs);
      }
      if (startPositionUs != UNSET.startPositionUs) {
        bundle.putLong(FIELD_START_POSITION_US, startPositionUs);
      }
      if (endPositionUs != UNSET.endPositionUs) {
        bundle.putLong(FIELD_END_POSITION_US, endPositionUs);
      }
      if (relativeToLiveWindow != UNSET.relativeToLiveWindow) {
        bundle.putBoolean(FIELD_RELATIVE_TO_LIVE_WINDOW, relativeToLiveWindow);
      }
      if (relativeToDefaultPosition != UNSET.relativeToDefaultPosition) {
        bundle.putBoolean(FIELD_RELATIVE_TO_DEFAULT_POSITION, relativeToDefaultPosition);
      }
      if (startsAtKeyFrame != UNSET.startsAtKeyFrame) {
        bundle.putBoolean(FIELD_STARTS_AT_KEY_FRAME, startsAtKeyFrame);
      }
      return bundle;
    }

    /** Restores a {@code ClippingProperties} from a {@link Bundle}. */
    @SuppressWarnings("deprecation") // Building deprecated type for backwards compatibility
    @UnstableApi
    public static ClippingProperties fromBundle(Bundle bundle) {
      ClippingConfiguration.Builder clippingConfiguration =
          new ClippingConfiguration.Builder()
              .setStartPositionMs(
                  bundle.getLong(
                      FIELD_START_POSITION_MS, /* defaultValue= */ UNSET.startPositionMs))
              .setEndPositionMs(
                  bundle.getLong(FIELD_END_POSITION_MS, /* defaultValue= */ UNSET.endPositionMs))
              .setRelativeToLiveWindow(
                  bundle.getBoolean(
                      FIELD_RELATIVE_TO_LIVE_WINDOW,
                      /* defaultValue= */ UNSET.relativeToLiveWindow))
              .setRelativeToDefaultPosition(
                  bundle.getBoolean(
                      FIELD_RELATIVE_TO_DEFAULT_POSITION,
                      /* defaultValue= */ UNSET.relativeToDefaultPosition))
              .setStartsAtKeyFrame(
                  bundle.getBoolean(
                      FIELD_STARTS_AT_KEY_FRAME, /* defaultValue= */ UNSET.startsAtKeyFrame));
      long startPositionUs =
          bundle.getLong(FIELD_START_POSITION_US, /* defaultValue= */ UNSET.startPositionUs);
      if (startPositionUs != UNSET.startPositionUs) {
        clippingConfiguration.setStartPositionUs(startPositionUs);
      }
      long endPositionUs =
          bundle.getLong(FIELD_END_POSITION_US, /* defaultValue= */ UNSET.endPositionUs);
      if (endPositionUs != UNSET.endPositionUs) {
        clippingConfiguration.setEndPositionUs(endPositionUs);
      }
      return clippingConfiguration.buildClippingProperties();
    }
  }

  /**
   * @deprecated Use {@link ClippingConfiguration} instead.
   */
  @UnstableApi
  @Deprecated
  public static final class ClippingProperties extends ClippingConfiguration {
    @SuppressWarnings("deprecation") // Using deprecated type
    public static final ClippingProperties UNSET =
        new ClippingConfiguration.Builder().buildClippingProperties();

    private ClippingProperties(Builder builder) {
      super(builder);
    }
  }

  /**
   * 帮助播放器理解由 {@link MediaItem} 表示的播放请求的元数据。
   *
   * <p>此元数据在播放请求被转发到其他播放器实例（例如从 {@code androidx.media3.session.MediaController}）且创建请求的播放器不知道播放所需的 {@link LocalConfiguration} 时最为有用。
   */
  public static final class RequestMetadata {

    /** 空的请求元数据。 */
    public static final RequestMetadata EMPTY = new Builder().build();

    /** {@link RequestMetadata} 实例的构建器。 */
    public static final class Builder {

      @Nullable private Uri mediaUri; // 请求媒体的 URI
      @Nullable private String searchQuery; // 请求媒体的搜索查询
      @Nullable private Bundle extras; // 可选的附加信息 Bundle

      /** 创建一个实例。 */
      public Builder() {}

      private Builder(RequestMetadata requestMetadata) {
        this.mediaUri = requestMetadata.mediaUri; // 复制媒体 URI
        this.searchQuery = requestMetadata.searchQuery; // 复制搜索查询
        this.extras = requestMetadata.extras; // 复制附加信息
      }

      /** 设置请求媒体的 URI，如果未知或不适用则为 null。 */
      @CanIgnoreReturnValue
      public Builder setMediaUri(@Nullable Uri mediaUri) {
        this.mediaUri = mediaUri; // 设置媒体 URI
        return this;
      }

      /** 设置请求媒体的搜索查询，如果不适用则为 null。 */
      @CanIgnoreReturnValue
      public Builder setSearchQuery(@Nullable String searchQuery) {
        this.searchQuery = searchQuery; // 设置搜索查询
        return this;
      }

      /** 设置可选的附加信息 {@link Bundle}。 */
      @CanIgnoreReturnValue
      public Builder setExtras(@Nullable Bundle extras) {
        this.extras = extras; // 设置附加信息
        return this;
      }

      /** 构建请求元数据。 */
      public RequestMetadata build() {
        return new RequestMetadata(this); // 返回构建的 RequestMetadata 实例
      }
    }

    /** 请求媒体的 URI，如果未知或不适用则为 null。 */
    @Nullable public final Uri mediaUri;

    /** 请求媒体的搜索查询，如果不适用则为 null。 */
    @Nullable public final String searchQuery;

    /**
     * 可选的附加信息 {@link Bundle}。
     *
     * <p>由于检查两个 {@link Bundle} 实例是否相等的复杂性，这些附加信息的内容不会在 {@link #equals(Object)} 或 {@link #hashCode()} 实现中被考虑。
     */
    @Nullable public final Bundle extras;

    private RequestMetadata(Builder builder) {
      this.mediaUri = builder.mediaUri; // 初始化媒体 URI
      this.searchQuery = builder.searchQuery; // 初始化搜索查询
      this.extras = builder.extras; // 初始化附加信息
    }

    /** 返回一个用此实例的值初始化的 {@link Builder}。 */
    public Builder buildUpon() {
      return new Builder(this); // 返回一个新的构建器实例
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true; // 如果是同一个对象，返回 true
      }
      if (!(o instanceof RequestMetadata)) {
        return false; // 如果对象不是 RequestMetadata 类型，返回 false
      }
      RequestMetadata that = (RequestMetadata) o;
      return Util.areEqual(mediaUri, that.mediaUri)
          && Util.areEqual(searchQuery, that.searchQuery)
          && ((extras == null) == (that.extras == null)); // 比较媒体 URI、搜索查询和附加信息的存在性
    }

    @Override
    public int hashCode() {
      int result = mediaUri == null ? 0 : mediaUri.hashCode();
      result = 31 * result + (searchQuery == null ? 0 : searchQuery.hashCode());
      result = 31 * result + (extras == null ? 0 : 1);
      return result; // 计算哈希值
    }

    private static final String FIELD_MEDIA_URI = Util.intToStringMaxRadix(0);
    private static final String FIELD_SEARCH_QUERY = Util.intToStringMaxRadix(1);
    private static final String FIELD_EXTRAS = Util.intToStringMaxRadix(2);

    @UnstableApi
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      if (mediaUri != null) {
        bundle.putParcelable(FIELD_MEDIA_URI, mediaUri); // 添加媒体 URI
      }
      if (searchQuery != null) {
        bundle.putString(FIELD_SEARCH_QUERY, searchQuery); // 添加搜索查询
      }
      if (extras != null) {
        bundle.putBundle(FIELD_EXTRAS, extras); // 添加附加信息
      }
      return bundle; // 返回 Bundle
    }

    /** 从 {@link Bundle} 中恢复一个 {@code RequestMetadata} 实例。 */
    @UnstableApi
    public static RequestMetadata fromBundle(Bundle bundle) {
      return new RequestMetadata.Builder()
          .setMediaUri(bundle.getParcelable(FIELD_MEDIA_URI)) // 恢复媒体 URI
          .setSearchQuery(bundle.getString(FIELD_SEARCH_QUERY)) // 恢复搜索查询
          .setExtras(bundle.getBundle(FIELD_EXTRAS)) // 恢复附加信息
          .build(); // 构建并返回 RequestMetadata 实例
    }
  }
  /**
   * 默认的媒体 ID，如果未通过 {@link Builder#setMediaId(String)} 显式设置媒体 ID，则使用此值。
   */
  public static final String DEFAULT_MEDIA_ID = "";

  /** 空的 {@link MediaItem}。 */
  public static final MediaItem EMPTY = new MediaItem.Builder().build();

  /** 标识媒体项的唯一 ID。 */
  public final String mediaId;

  /**
   * 可选的本地播放配置。如果跨进程边界共享，则可能为 {@code null}。
   */
  @Nullable public final LocalConfiguration localConfiguration;

  /**
   * @deprecated 请使用 {@link #localConfiguration} 替代。
   */
  @UnstableApi @Deprecated @Nullable public final LocalConfiguration playbackProperties;

  /** 直播播放配置。 */
  public final LiveConfiguration liveConfiguration;

  /** 媒体元数据。 */
  public final MediaMetadata mediaMetadata;

  /** 剪辑配置。 */
  public final ClippingConfiguration clippingConfiguration;

  /**
   * @deprecated 请使用 {@link #clippingConfiguration} 替代。
   */
  @SuppressWarnings("deprecation") // 保留已弃用的字段与已弃用的类型
  @UnstableApi
  @Deprecated
  public final ClippingProperties clippingProperties;

  /** 媒体的 {@link RequestMetadata}。 */
  public final RequestMetadata requestMetadata;

  // Using ClippingProperties until they're deleted.
  @SuppressWarnings("deprecation")
  private MediaItem(
      String mediaId,
      ClippingProperties clippingConfiguration,
      @Nullable LocalConfiguration localConfiguration,
      LiveConfiguration liveConfiguration,
      MediaMetadata mediaMetadata,
      RequestMetadata requestMetadata) {
    this.mediaId = mediaId;
    this.localConfiguration = localConfiguration;
    this.playbackProperties = localConfiguration;
    this.liveConfiguration = liveConfiguration;
    this.mediaMetadata = mediaMetadata;
    this.clippingConfiguration = clippingConfiguration;
    this.clippingProperties = clippingConfiguration;
    this.requestMetadata = requestMetadata;
  }

  /** Returns a {@link Builder} initialized with the values of this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MediaItem)) {
      return false;
    }

    MediaItem other = (MediaItem) obj;

    return Util.areEqual(mediaId, other.mediaId)
        && clippingConfiguration.equals(other.clippingConfiguration)
        && Util.areEqual(localConfiguration, other.localConfiguration)
        && Util.areEqual(liveConfiguration, other.liveConfiguration)
        && Util.areEqual(mediaMetadata, other.mediaMetadata)
        && Util.areEqual(requestMetadata, other.requestMetadata);
  }

  @Override
  public int hashCode() {
    int result = mediaId.hashCode();
    result = 31 * result + (localConfiguration != null ? localConfiguration.hashCode() : 0);
    result = 31 * result + liveConfiguration.hashCode();
    result = 31 * result + clippingConfiguration.hashCode();
    result = 31 * result + mediaMetadata.hashCode();
    result = 31 * result + requestMetadata.hashCode();
    return result;
  }

  private static final String FIELD_MEDIA_ID = Util.intToStringMaxRadix(0);
  private static final String FIELD_LIVE_CONFIGURATION = Util.intToStringMaxRadix(1);
  private static final String FIELD_MEDIA_METADATA = Util.intToStringMaxRadix(2);
  private static final String FIELD_CLIPPING_PROPERTIES = Util.intToStringMaxRadix(3);
  private static final String FIELD_REQUEST_METADATA = Util.intToStringMaxRadix(4);
  private static final String FIELD_LOCAL_CONFIGURATION = Util.intToStringMaxRadix(5);

  @UnstableApi
  private Bundle toBundle(boolean includeLocalConfiguration) {
    Bundle bundle = new Bundle();
    if (!mediaId.equals(DEFAULT_MEDIA_ID)) {
      bundle.putString(FIELD_MEDIA_ID, mediaId);
    }
    if (!liveConfiguration.equals(LiveConfiguration.UNSET)) {
      bundle.putBundle(FIELD_LIVE_CONFIGURATION, liveConfiguration.toBundle());
    }
    if (!mediaMetadata.equals(MediaMetadata.EMPTY)) {
      bundle.putBundle(FIELD_MEDIA_METADATA, mediaMetadata.toBundle());
    }
    if (!clippingConfiguration.equals(ClippingConfiguration.UNSET)) {
      bundle.putBundle(FIELD_CLIPPING_PROPERTIES, clippingConfiguration.toBundle());
    }
    if (!requestMetadata.equals(RequestMetadata.EMPTY)) {
      bundle.putBundle(FIELD_REQUEST_METADATA, requestMetadata.toBundle());
    }
    if (includeLocalConfiguration && localConfiguration != null) {
      bundle.putBundle(FIELD_LOCAL_CONFIGURATION, localConfiguration.toBundle());
    }
    return bundle;
  }
  /**
   * 返回表示此对象中存储信息的 {@link Bundle}。
   *
   * <p>它省略了 {@link #localConfiguration} 字段。通过 {@link #fromBundle} 从此类 Bundle 恢复的实例的 {@link #localConfiguration} 将为 {@code null}。
   */
  @UnstableApi
  public Bundle toBundle() {
    return toBundle(/* includeLocalConfiguration= */ false); // 默认不包含 localConfiguration
  }

  /**
   * 返回表示此 {@link #MediaItem} 对象中存储信息的 {@link Bundle}，同时包含 {@link #localConfiguration} 字段（如果它不为 null，否则跳过）。
   */
  @UnstableApi
  public Bundle toBundleIncludeLocalConfiguration() {
    return toBundle(/* includeLocalConfiguration= */ true); // 包含 localConfiguration
  }

  /**
   * 从 {@link Bundle} 中恢复一个 {@code MediaItem}。
   *
   * <p>恢复的实例的 {@link #localConfiguration} 将始终为 {@code null}。
   */
  @UnstableApi
  @SuppressWarnings("deprecation") // Unbundling to ClippingProperties while it still exists.
  public static MediaItem fromBundle(Bundle bundle) {
    String mediaId = checkNotNull(bundle.getString(FIELD_MEDIA_ID, DEFAULT_MEDIA_ID));
    @Nullable Bundle liveConfigurationBundle = bundle.getBundle(FIELD_LIVE_CONFIGURATION);
    LiveConfiguration liveConfiguration;
    if (liveConfigurationBundle == null) {
      liveConfiguration = LiveConfiguration.UNSET;
    } else {
      liveConfiguration = LiveConfiguration.fromBundle(liveConfigurationBundle);
    }
    @Nullable Bundle mediaMetadataBundle = bundle.getBundle(FIELD_MEDIA_METADATA);
    MediaMetadata mediaMetadata;
    if (mediaMetadataBundle == null) {
      mediaMetadata = MediaMetadata.EMPTY;
    } else {
      mediaMetadata = MediaMetadata.fromBundle(mediaMetadataBundle);
    }
    @Nullable Bundle clippingConfigurationBundle = bundle.getBundle(FIELD_CLIPPING_PROPERTIES);
    ClippingProperties clippingConfiguration;
    if (clippingConfigurationBundle == null) {
      clippingConfiguration = ClippingProperties.UNSET;
    } else {
      clippingConfiguration = ClippingConfiguration.fromBundle(clippingConfigurationBundle);
    }
    @Nullable Bundle requestMetadataBundle = bundle.getBundle(FIELD_REQUEST_METADATA);
    RequestMetadata requestMetadata;
    if (requestMetadataBundle == null) {
      requestMetadata = RequestMetadata.EMPTY;
    } else {
      requestMetadata = RequestMetadata.fromBundle(requestMetadataBundle);
    }
    @Nullable Bundle localConfigurationBundle = bundle.getBundle(FIELD_LOCAL_CONFIGURATION);
    LocalConfiguration localConfiguration;
    if (localConfigurationBundle == null) {
      localConfiguration = null;
    } else {
      localConfiguration = LocalConfiguration.fromBundle(localConfigurationBundle);
    }
    return new MediaItem(
        mediaId,
        clippingConfiguration,
        localConfiguration,
        liveConfiguration,
        mediaMetadata,
        requestMetadata);
  }
}
