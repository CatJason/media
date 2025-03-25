package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.BundleCollectionUtil.toBundleArrayList;
import static com.google.common.base.MoreObjects.firstNonNull;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Looper;
import android.view.accessibility.CaptioningManager;
import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.BundleCollectionUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;

// LINT.IfChange(javadoc)

/**
 * 用于控制轨道选择的参数。
 *
 * <p>可以在 {@link Player} 上查询和设置这些参数。例如，以下代码修改参数以将视频轨道选择限制为标清（SD），并优先选择德语音频轨道（如果存在）：
 *
 * <pre>{@code
 * // 基于当前参数构建。
 * TrackSelectionParameters currentParameters = player.getTrackSelectionParameters();
 * // 构建新的参数。
 * TrackSelectionParameters newParameters = currentParameters
 *     .buildUpon()
 *     .setMaxVideoSizeSd()
 *     .setPreferredAudioLanguage("de")
 *     .build();
 * // 设置新参数。
 * player.setTrackSelectionParameters(newParameters);
 * }</pre>
 */
public class TrackSelectionParameters {

  /**
   * {@link TrackSelectionParameters} 的构建器。有关可以使用此构建器配置的参数的说明，请参阅 {@link TrackSelectionParameters} 文档。
   */
  public static class Builder {

    // Video
    private int maxVideoWidth;
    private int maxVideoHeight;
    private int maxVideoFrameRate;
    private int maxVideoBitrate;
    private int minVideoWidth;
    private int minVideoHeight;
    private int minVideoFrameRate;
    private int minVideoBitrate;
    private int viewportWidth;
    private int viewportHeight;
    private boolean viewportOrientationMayChange;
    private ImmutableList<String> preferredVideoMimeTypes;
    private @C.RoleFlags int preferredVideoRoleFlags;
    // Audio
    private ImmutableList<String> preferredAudioLanguages;
    private @C.RoleFlags int preferredAudioRoleFlags;
    private int maxAudioChannelCount;
    private int maxAudioBitrate;
    private ImmutableList<String> preferredAudioMimeTypes;
    private AudioOffloadPreferences audioOffloadPreferences;
    // Text
    private ImmutableList<String> preferredTextLanguages;
    private @C.RoleFlags int preferredTextRoleFlags;
    private @C.SelectionFlags int ignoredTextSelectionFlags;
    private boolean selectUndeterminedTextLanguage;
    // Image
    private boolean isPrioritizeImageOverVideoEnabled;
    // General
    private boolean forceLowestBitrate;
    private boolean forceHighestSupportedBitrate;
    private HashMap<TrackGroup, TrackSelectionOverride> overrides;
    private HashSet<@C.TrackType Integer> disabledTrackTypes;

    /**
     * @deprecated {@link Context} constraints will not be set using this constructor. Use {@link
     * #Builder(Context)} instead.
     */
    @UnstableApi
    @Deprecated
    public Builder() {
      // Video
      maxVideoWidth = Integer.MAX_VALUE;
      maxVideoHeight = Integer.MAX_VALUE;
      maxVideoFrameRate = Integer.MAX_VALUE;
      maxVideoBitrate = Integer.MAX_VALUE;
      viewportWidth = Integer.MAX_VALUE;
      viewportHeight = Integer.MAX_VALUE;
      viewportOrientationMayChange = true;
      preferredVideoMimeTypes = ImmutableList.of();
      preferredVideoRoleFlags = 0;
      // Audio
      preferredAudioLanguages = ImmutableList.of();
      preferredAudioRoleFlags = 0;
      maxAudioChannelCount = Integer.MAX_VALUE;
      maxAudioBitrate = Integer.MAX_VALUE;
      preferredAudioMimeTypes = ImmutableList.of();
      audioOffloadPreferences = AudioOffloadPreferences.DEFAULT;
      // Text
      preferredTextLanguages = ImmutableList.of();
      preferredTextRoleFlags = 0;
      ignoredTextSelectionFlags = 0;
      selectUndeterminedTextLanguage = false;
      // Image
      isPrioritizeImageOverVideoEnabled = false;
      // General
      forceLowestBitrate = false;
      forceHighestSupportedBitrate = false;
      overrides = new HashMap<>();
      disabledTrackTypes = new HashSet<>();
    }

    /**
     * Creates a builder with default initial values.
     *
     * @param context Any context.
     */
    @SuppressWarnings({"deprecation", "method.invocation"}) // Methods invoked are setter only.
    public Builder(Context context) {
      this();
      setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(context);
      setViewportSizeToPhysicalDisplaySize(context, /* viewportOrientationMayChange= */ true);
    }

    /**
     * Creates a builder with the initial values specified in {@code initialValues}.
     */
    @UnstableApi
    protected Builder(TrackSelectionParameters initialValues) {
      init(initialValues);
    }

    /**
     * Creates a builder with the initial values specified in {@code bundle}.
     */
    @UnstableApi
    protected Builder(Bundle bundle) {
      // Video
      maxVideoWidth = bundle.getInt(FIELD_MAX_VIDEO_WIDTH, DEFAULT_WITHOUT_CONTEXT.maxVideoWidth);
      maxVideoHeight =
          bundle.getInt(FIELD_MAX_VIDEO_HEIGHT, DEFAULT_WITHOUT_CONTEXT.maxVideoHeight);
      maxVideoFrameRate =
          bundle.getInt(FIELD_MAX_VIDEO_FRAMERATE, DEFAULT_WITHOUT_CONTEXT.maxVideoFrameRate);
      maxVideoBitrate =
          bundle.getInt(FIELD_MAX_VIDEO_BITRATE, DEFAULT_WITHOUT_CONTEXT.maxVideoBitrate);
      minVideoWidth = bundle.getInt(FIELD_MIN_VIDEO_WIDTH, DEFAULT_WITHOUT_CONTEXT.minVideoWidth);
      minVideoHeight =
          bundle.getInt(FIELD_MIN_VIDEO_HEIGHT, DEFAULT_WITHOUT_CONTEXT.minVideoHeight);
      minVideoFrameRate =
          bundle.getInt(FIELD_MIN_VIDEO_FRAMERATE, DEFAULT_WITHOUT_CONTEXT.minVideoFrameRate);
      minVideoBitrate =
          bundle.getInt(FIELD_MIN_VIDEO_BITRATE, DEFAULT_WITHOUT_CONTEXT.minVideoBitrate);
      viewportWidth = bundle.getInt(FIELD_VIEWPORT_WIDTH, DEFAULT_WITHOUT_CONTEXT.viewportWidth);
      viewportHeight = bundle.getInt(FIELD_VIEWPORT_HEIGHT, DEFAULT_WITHOUT_CONTEXT.viewportHeight);
      viewportOrientationMayChange =
          bundle.getBoolean(
              FIELD_VIEWPORT_ORIENTATION_MAY_CHANGE,
              DEFAULT_WITHOUT_CONTEXT.viewportOrientationMayChange);
      preferredVideoMimeTypes =
          ImmutableList.copyOf(
              firstNonNull(bundle.getStringArray(FIELD_PREFERRED_VIDEO_MIMETYPES), new String[0]));
      preferredVideoRoleFlags =
          bundle.getInt(
              FIELD_PREFERRED_VIDEO_ROLE_FLAGS, DEFAULT_WITHOUT_CONTEXT.preferredVideoRoleFlags);
      // Audio
      String[] preferredAudioLanguages1 =
          firstNonNull(bundle.getStringArray(FIELD_PREFERRED_AUDIO_LANGUAGES), new String[0]);
      preferredAudioLanguages = normalizeLanguageCodes(preferredAudioLanguages1);
      preferredAudioRoleFlags =
          bundle.getInt(
              FIELD_PREFERRED_AUDIO_ROLE_FLAGS, DEFAULT_WITHOUT_CONTEXT.preferredAudioRoleFlags);
      maxAudioChannelCount =
          bundle.getInt(
              FIELD_MAX_AUDIO_CHANNEL_COUNT, DEFAULT_WITHOUT_CONTEXT.maxAudioChannelCount);
      maxAudioBitrate =
          bundle.getInt(FIELD_MAX_AUDIO_BITRATE, DEFAULT_WITHOUT_CONTEXT.maxAudioBitrate);
      preferredAudioMimeTypes =
          ImmutableList.copyOf(
              firstNonNull(bundle.getStringArray(FIELD_PREFERRED_AUDIO_MIME_TYPES), new String[0]));
      audioOffloadPreferences = getAudioOffloadPreferencesFromBundle(bundle);
      // Text
      preferredTextLanguages =
          normalizeLanguageCodes(
              firstNonNull(bundle.getStringArray(FIELD_PREFERRED_TEXT_LANGUAGES), new String[0]));
      preferredTextRoleFlags =
          bundle.getInt(
              FIELD_PREFERRED_TEXT_ROLE_FLAGS, DEFAULT_WITHOUT_CONTEXT.preferredTextRoleFlags);
      ignoredTextSelectionFlags =
          bundle.getInt(
              FIELD_IGNORED_TEXT_SELECTION_FLAGS,
              DEFAULT_WITHOUT_CONTEXT.ignoredTextSelectionFlags);
      selectUndeterminedTextLanguage =
          bundle.getBoolean(
              FIELD_SELECT_UNDETERMINED_TEXT_LANGUAGE,
              DEFAULT_WITHOUT_CONTEXT.selectUndeterminedTextLanguage);
      // Image
      isPrioritizeImageOverVideoEnabled =
          bundle.getBoolean(
              FIELD_IS_PREFER_IMAGE_OVER_VIDEO_ENABLED,
              DEFAULT_WITHOUT_CONTEXT.isPrioritizeImageOverVideoEnabled);

      // General
      forceLowestBitrate =
          bundle.getBoolean(FIELD_FORCE_LOWEST_BITRATE, DEFAULT_WITHOUT_CONTEXT.forceLowestBitrate);
      forceHighestSupportedBitrate =
          bundle.getBoolean(
              FIELD_FORCE_HIGHEST_SUPPORTED_BITRATE,
              DEFAULT_WITHOUT_CONTEXT.forceHighestSupportedBitrate);
      @Nullable
      List<Bundle> overrideBundleList = bundle.getParcelableArrayList(FIELD_SELECTION_OVERRIDES);
      List<TrackSelectionOverride> overrideList =
          overrideBundleList == null
              ? ImmutableList.of()
              : BundleCollectionUtil.fromBundleList(
                  TrackSelectionOverride::fromBundle, overrideBundleList);
      overrides = new HashMap<>();
      for (int i = 0; i < overrideList.size(); i++) {
        TrackSelectionOverride override = overrideList.get(i);
        overrides.put(override.mediaTrackGroup, override);
      }
      int[] disabledTrackTypeArray =
          firstNonNull(bundle.getIntArray(FIELD_DISABLED_TRACK_TYPE), new int[0]);
      disabledTrackTypes = new HashSet<>();
      for (@C.TrackType int disabledTrackType : disabledTrackTypeArray) {
        disabledTrackTypes.add(disabledTrackType);
      }
    }

    private static AudioOffloadPreferences getAudioOffloadPreferencesFromBundle(Bundle bundle) {
      Bundle audioOffloadPreferencesBundle = bundle.getBundle(FIELD_AUDIO_OFFLOAD_PREFERENCES);
      return (audioOffloadPreferencesBundle != null)
          ? AudioOffloadPreferences.fromBundle(audioOffloadPreferencesBundle)
          : new AudioOffloadPreferences.Builder()
              .setAudioOffloadMode(
                  bundle.getInt(
                      FIELD_AUDIO_OFFLOAD_MODE_PREFERENCE,
                      AudioOffloadPreferences.DEFAULT.audioOffloadMode))
              .setIsGaplessSupportRequired(
                  bundle.getBoolean(
                      FIELD_IS_GAPLESS_SUPPORT_REQUIRED,
                      AudioOffloadPreferences.DEFAULT.isGaplessSupportRequired))
              .setIsSpeedChangeSupportRequired(
                  bundle.getBoolean(
                      FIELD_IS_SPEED_CHANGE_SUPPORT_REQUIRED,
                      AudioOffloadPreferences.DEFAULT.isSpeedChangeSupportRequired))
              .build();
    }

    /**
     * Overrides the value of the builder with the value of {@link TrackSelectionParameters}.
     */
    @EnsuresNonNull({
        "preferredVideoMimeTypes",
        "preferredAudioLanguages",
        "preferredAudioMimeTypes",
        "audioOffloadPreferences",
        "preferredTextLanguages",
        "overrides",
        "disabledTrackTypes",
    })
    private void init(@UnknownInitialization Builder this, TrackSelectionParameters parameters) {
      // Video
      maxVideoWidth = parameters.maxVideoWidth;
      maxVideoHeight = parameters.maxVideoHeight;
      maxVideoFrameRate = parameters.maxVideoFrameRate;
      maxVideoBitrate = parameters.maxVideoBitrate;
      minVideoWidth = parameters.minVideoWidth;
      minVideoHeight = parameters.minVideoHeight;
      minVideoFrameRate = parameters.minVideoFrameRate;
      minVideoBitrate = parameters.minVideoBitrate;
      viewportWidth = parameters.viewportWidth;
      viewportHeight = parameters.viewportHeight;
      viewportOrientationMayChange = parameters.viewportOrientationMayChange;
      preferredVideoMimeTypes = parameters.preferredVideoMimeTypes;
      preferredVideoRoleFlags = parameters.preferredVideoRoleFlags;
      // Audio
      preferredAudioLanguages = parameters.preferredAudioLanguages;
      preferredAudioRoleFlags = parameters.preferredAudioRoleFlags;
      maxAudioChannelCount = parameters.maxAudioChannelCount;
      maxAudioBitrate = parameters.maxAudioBitrate;
      preferredAudioMimeTypes = parameters.preferredAudioMimeTypes;
      audioOffloadPreferences = parameters.audioOffloadPreferences;
      // Text
      preferredTextLanguages = parameters.preferredTextLanguages;
      preferredTextRoleFlags = parameters.preferredTextRoleFlags;
      ignoredTextSelectionFlags = parameters.ignoredTextSelectionFlags;
      selectUndeterminedTextLanguage = parameters.selectUndeterminedTextLanguage;
      // Image
      isPrioritizeImageOverVideoEnabled = parameters.isPrioritizeImageOverVideoEnabled;
      // General
      forceLowestBitrate = parameters.forceLowestBitrate;
      forceHighestSupportedBitrate = parameters.forceHighestSupportedBitrate;
      disabledTrackTypes = new HashSet<>(parameters.disabledTrackTypes);
      overrides = new HashMap<>(parameters.overrides);
    }

    /**
     * Overrides the value of the builder with the value of {@link TrackSelectionParameters}.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    protected Builder set(TrackSelectionParameters parameters) {
      init(parameters);
      return this;
    }

    // 视频

    /**
     * 等同于 {@link #setMaxVideoSize setMaxVideoSize(1279, 719)}。
     *
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setMaxVideoSizeSd() {
      return setMaxVideoSize(1279, 719);
    }

    /**
     * 等同于 {@link #setMaxVideoSize setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)}。
     *
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder clearVideoSizeConstraints() {
      return setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * 设置允许的最大视频宽度和高度。
     *
     * @param maxVideoWidth  允许的最大视频宽度（以像素为单位）。
     * @param maxVideoHeight 允许的最大视频高度（以像素为单位）。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setMaxVideoSize(int maxVideoWidth, int maxVideoHeight) {
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      return this;
    }

    /**
     * 设置允许的最大视频帧率。
     *
     * @param maxVideoFrameRate 允许的最大视频帧率（以赫兹为单位）。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setMaxVideoFrameRate(int maxVideoFrameRate) {
      this.maxVideoFrameRate = maxVideoFrameRate;
      return this;
    }

    /**
     * 设置允许的最大视频比特率。
     *
     * @param maxVideoBitrate 允许的最大视频比特率（以比特/秒为单位）。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setMaxVideoBitrate(int maxVideoBitrate) {
      this.maxVideoBitrate = maxVideoBitrate;
      return this;
    }

    /**
     * 设置允许的最小视频宽度和高度。
     *
     * @param minVideoWidth  允许的最小视频宽度（以像素为单位）。
     * @param minVideoHeight 允许的最小视频高度（以像素为单位）。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setMinVideoSize(int minVideoWidth, int minVideoHeight) {
      this.minVideoWidth = minVideoWidth;
      this.minVideoHeight = minVideoHeight;
      return this;
    }

    /**
     * 设置允许的最小视频帧率。
     *
     * @param minVideoFrameRate 允许的最小视频帧率（以赫兹为单位）。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setMinVideoFrameRate(int minVideoFrameRate) {
      this.minVideoFrameRate = minVideoFrameRate;
      return this;
    }

    /**
     * 设置允许的最小视频比特率。
     *
     * @param minVideoBitrate 允许的最小视频比特率（以比特/秒为单位）。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setMinVideoBitrate(int minVideoBitrate) {
      this.minVideoBitrate = minVideoBitrate;
      return this;
    }

    /**
     * 等同于调用 {@link #setViewportSize(int, int, boolean)}，并使用从 {@link Util#getCurrentDisplayModeSize(Context)} 获取的视口大小。
     *
     * @param context                      任意上下文。
     * @param viewportOrientationMayChange 视口方向是否可能在播放期间发生变化。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setViewportSizeToPhysicalDisplaySize(
        Context context, boolean viewportOrientationMayChange) {
      // 假设视口为全屏。
      Point viewportSize = Util.getCurrentDisplayModeSize(context);
      return setViewportSize(viewportSize.x, viewportSize.y, viewportOrientationMayChange);
    }

    /**
     * 等同于 {@link #setViewportSize setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE, true)}。
     *
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder clearViewportSizeConstraints() {
      return setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE, true);
    }

    /**
     * 设置视口大小以限制自适应视频选择，以便仅选择适合视口的轨道。
     *
     * @param viewportWidth                视口宽度（以像素为单位）。
     * @param viewportHeight               视口高度（以像素为单位）。
     * @param viewportOrientationMayChange 视口方向是否可能在播放期间发生变化。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setViewportSize(
        int viewportWidth, int viewportHeight, boolean viewportOrientationMayChange) {
      this.viewportWidth = viewportWidth;
      this.viewportHeight = viewportHeight;
      this.viewportOrientationMayChange = viewportOrientationMayChange;
      return this;
    }

    /**
     * 设置视频轨道的首选样本 MIME 类型。
     *
     * @param mimeType 视频轨道的首选 MIME 类型，或 {@code null} 以清除先前设置的首选项。
     * @return 此构建器。
     */
    public Builder setPreferredVideoMimeType(@Nullable String mimeType) {
      return mimeType == null ? setPreferredVideoMimeTypes() : setPreferredVideoMimeTypes(mimeType);
    }

    /**
     * 设置视频轨道的首选样本 MIME 类型。
     *
     * @param mimeTypes 视频轨道的首选 MIME 类型（按优先级顺序），或空列表表示无首选项。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setPreferredVideoMimeTypes(String... mimeTypes) {
      preferredVideoMimeTypes = ImmutableList.copyOf(mimeTypes);
      return this;
    }

    /**
     * 设置视频轨道的首选 {@link C.RoleFlags}。
     *
     * @param preferredVideoRoleFlags 首选视频角色标志。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setPreferredVideoRoleFlags(@C.RoleFlags int preferredVideoRoleFlags) {
      this.preferredVideoRoleFlags = preferredVideoRoleFlags;
      return this;
    }

    // 音频

    /**
     * 设置音频和强制文本轨道的首选语言。
     *
     * @param preferredAudioLanguage 首选音频语言，符合 IETF BCP 47 标准，或 {@code null} 以选择默认轨道，如果没有默认轨道则选择第一个轨道。
     * @return 此构建器。
     */
    public Builder setPreferredAudioLanguage(@Nullable String preferredAudioLanguage) {
      return preferredAudioLanguage == null
          ? setPreferredAudioLanguages()
          : setPreferredAudioLanguages(preferredAudioLanguage);
    }

    /**
     * 设置音频和强制文本轨道的首选语言。
     *
     * @param preferredAudioLanguages 首选音频语言，符合 IETF BCP 47 标准（按优先级顺序），或空数组以选择默认轨道，如果没有默认轨道则选择第一个轨道。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setPreferredAudioLanguages(String... preferredAudioLanguages) {
      this.preferredAudioLanguages = normalizeLanguageCodes(preferredAudioLanguages);
      return this;
    }

    /**
     * 设置音频轨道的首选 {@link C.RoleFlags}。
     *
     * @param preferredAudioRoleFlags 首选音频角色标志。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setPreferredAudioRoleFlags(@C.RoleFlags int preferredAudioRoleFlags) {
      this.preferredAudioRoleFlags = preferredAudioRoleFlags;
      return this;
    }

    /**
     * 设置允许的最大音频通道数。
     *
     * @param maxAudioChannelCount 允许的最大音频通道数。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setMaxAudioChannelCount(int maxAudioChannelCount) {
      this.maxAudioChannelCount = maxAudioChannelCount;
      return this;
    }

    /**
     * 设置允许的最大音频比特率。
     *
     * @param maxAudioBitrate 允许的最大音频比特率（以比特/秒为单位）。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setMaxAudioBitrate(int maxAudioBitrate) {
      this.maxAudioBitrate = maxAudioBitrate;
      return this;
    }

    /**
     * 设置音频轨道的首选样本 MIME 类型。
     *
     * @param mimeType 音频轨道的首选 MIME 类型，或 {@code null} 以清除先前设置的首选项。
     * @return 此构建器。
     */
    public Builder setPreferredAudioMimeType(@Nullable String mimeType) {
      return mimeType == null ? setPreferredAudioMimeTypes() : setPreferredAudioMimeTypes(mimeType);
    }

    /**
     * 设置音频轨道的首选样本 MIME 类型。
     *
     * @param mimeTypes 音频轨道的首选 MIME 类型（按优先级顺序），或空列表表示无首选项。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setPreferredAudioMimeTypes(String... mimeTypes) {
      preferredAudioMimeTypes = ImmutableList.copyOf(mimeTypes);
      return this;
    }

    /**
     * 设置音频卸载模式的首选项。这包括是否启用/禁用卸载，以及设置设备是否必须支持无缝过渡或卸载期间的速度变化等要求。
     */
    @UnstableApi
    @CanIgnoreReturnValue
    public Builder setAudioOffloadPreferences(AudioOffloadPreferences audioOffloadPreferences) {
      this.audioOffloadPreferences = audioOffloadPreferences;
      return this;
    }

    // 文本

    /**
     * 根据 {@link CaptioningManager} 的可访问性设置，设置文本轨道的首选语言和角色标志。
     *
     * <p>当 {@link CaptioningManager} 被禁用时，不执行任何操作。
     *
     * @param context 一个 {@link Context}。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(
        Context context) {
      if (Util.SDK_INT < 23 && Looper.myLooper() == null) {
        // Android 平台错误（Marshmallow 之前），当从非 Looper 线程实例化 CaptioningService 时会导致运行时异常。参见 [内部：b/143779904]。
        return this;
      }
      CaptioningManager captioningManager =
          (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
      if (captioningManager == null || !captioningManager.isEnabled()) {
        return this;
      }
      preferredTextRoleFlags = C.ROLE_FLAG_CAPTION | C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND;
      Locale preferredLocale = captioningManager.getLocale();
      if (preferredLocale != null) {
        preferredTextLanguages = ImmutableList.of(Util.getLocaleLanguageTag(preferredLocale));
      }
      return this;
    }

    /**
     * 设置文本轨道的首选语言。
     *
     * @param preferredTextLanguage 首选文本语言，符合 IETF BCP 47 标准，或 {@code null} 以选择默认轨道（如果有），否则不选择任何轨道。
     * @return 此构建器。
     */
    public Builder setPreferredTextLanguage(@Nullable String preferredTextLanguage) {
      return preferredTextLanguage == null
          ? setPreferredTextLanguages()
          : setPreferredTextLanguages(preferredTextLanguage);
    }

    /**
     * 设置文本轨道的首选语言。
     *
     * @param preferredTextLanguages 首选文本语言，符合 IETF BCP 47 标准（按优先级顺序），或空数组以选择默认轨道（如果有），否则不选择任何轨道。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setPreferredTextLanguages(String... preferredTextLanguages) {
      this.preferredTextLanguages = normalizeLanguageCodes(preferredTextLanguages);
      return this;
    }

    /**
     * 设置文本轨道的首选 {@link C.RoleFlags}。
     *
     * @param preferredTextRoleFlags 首选文本角色标志。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setPreferredTextRoleFlags(@C.RoleFlags int preferredTextRoleFlags) {
      this.preferredTextRoleFlags = preferredTextRoleFlags;
      return this;
    }

    /**
     * 设置文本轨道选择时忽略的选择标志位掩码。
     *
     * @param ignoredTextSelectionFlags 文本轨道选择时忽略的 {@link C.SelectionFlags} 位掩码。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setIgnoredTextSelectionFlags(@C.SelectionFlags int ignoredTextSelectionFlags) {
      this.ignoredTextSelectionFlags = ignoredTextSelectionFlags;
      return this;
    }

    /**
     * 设置如果 {@link #setPreferredTextLanguages(String...) 首选语言} 不可用或未设置时，是否应选择语言未确定的文本轨道。
     *
     * @param selectUndeterminedTextLanguage 如果首选语言轨道不可用，是否应选择语言未确定的文本轨道。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setSelectUndeterminedTextLanguage(boolean selectUndeterminedTextLanguage) {
      this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
      return this;
    }

    // 图像

    /**
     * 设置如果图像轨道和视频轨道都可用时，是否优先选择图像轨道。
     *
     * @param isPrioritizeImageOverVideoEnabled 如果图像轨道和视频轨道都可用时，是否优先选择图像轨道。
     * @return 此构建器。
     */
    @UnstableApi
    @CanIgnoreReturnValue
    public Builder setPrioritizeImageOverVideoEnabled(boolean isPrioritizeImageOverVideoEnabled) {
      this.isPrioritizeImageOverVideoEnabled = isPrioritizeImageOverVideoEnabled;
      return this;
    }

    // 通用

    /**
     * 设置是否强制选择符合所有其他约束的最低比特率的音频和视频轨道。
     *
     * @param forceLowestBitrate 是否强制选择最低比特率的音频和视频轨道。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setForceLowestBitrate(boolean forceLowestBitrate) {
      this.forceLowestBitrate = forceLowestBitrate;
      return this;
    }

    /**
     * 设置是否强制选择符合所有其他约束的最高比特率的音频和视频轨道。
     *
     * @param forceHighestSupportedBitrate 是否强制选择最高比特率的音频和视频轨道。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setForceHighestSupportedBitrate(boolean forceHighestSupportedBitrate) {
      this.forceHighestSupportedBitrate = forceHighestSupportedBitrate;
      return this;
    }

    /**
     * 添加一个覆盖，替换相同 {@link TrackGroup} 的任何现有覆盖。
     */
    @CanIgnoreReturnValue
    public Builder addOverride(TrackSelectionOverride override) {
      overrides.put(override.mediaTrackGroup, override);
      return this;
    }

    /**
     * 设置一个覆盖，替换所有具有相同轨道类型的现有覆盖。
     */
    @CanIgnoreReturnValue
    public Builder setOverrideForType(TrackSelectionOverride override) {
      clearOverridesOfType(override.getType());
      overrides.put(override.mediaTrackGroup, override);
      return this;
    }

    /**
     * 移除提供的媒体 {@link TrackGroup} 的覆盖（如果有）。
     */
    @CanIgnoreReturnValue
    public Builder clearOverride(TrackGroup mediaTrackGroup) {
      overrides.remove(mediaTrackGroup);
      return this;
    }

    /**
     * 移除所有指定轨道类型的覆盖。
     */
    @CanIgnoreReturnValue
    public Builder clearOverridesOfType(@C.TrackType int trackType) {
      Iterator<TrackSelectionOverride> it = overrides.values().iterator();
      while (it.hasNext()) {
        TrackSelectionOverride override = it.next();
        if (override.getType() == trackType) {
          it.remove();
        }
      }
      return this;
    }

    /**
     * 移除所有覆盖。
     */
    @CanIgnoreReturnValue
    public Builder clearOverrides() {
      overrides.clear();
      return this;
    }

    /**
     * 设置禁用的轨道类型，阻止选择这些类型的所有轨道进行播放。任何先前禁用的轨道类型将被清除。
     *
     * @param disabledTrackTypes 要禁用的轨道类型。
     * @return 此构建器。
     * @deprecated 使用 {@link #setTrackTypeDisabled(int, boolean)}。
     */
    @CanIgnoreReturnValue
    @Deprecated
    @UnstableApi
    public Builder setDisabledTrackTypes(Set<@C.TrackType Integer> disabledTrackTypes) {
      this.disabledTrackTypes.clear();
      this.disabledTrackTypes.addAll(disabledTrackTypes);
      return this;
    }

    /**
     * 设置是否禁用某个轨道类型。如果禁用，将不会选择指定类型的任何轨道进行播放。
     *
     * @param trackType 轨道类型。
     * @param disabled  是否应禁用该轨道类型。
     * @return 此构建器。
     */
    @CanIgnoreReturnValue
    public Builder setTrackTypeDisabled(@C.TrackType int trackType, boolean disabled) {
      if (disabled) {
        disabledTrackTypes.add(trackType);
      } else {
        disabledTrackTypes.remove(trackType);
      }
      return this;
    }

    /**
     * 使用选定的值构建 {@link TrackSelectionParameters} 实例。
     */
    public TrackSelectionParameters build() {
      return new TrackSelectionParameters(this);
    }

    private static ImmutableList<String> normalizeLanguageCodes(String[] preferredTextLanguages) {
      ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
      for (String language : checkNotNull(preferredTextLanguages)) {
        listBuilder.add(Util.normalizeLanguageCode(checkNotNull(language)));
      }
      return listBuilder.build();
    }
  }

  /**
   * 启用音频卸载的首选项和约束。
   */
  @UnstableApi
  public static final class AudioOffloadPreferences {

    /**
     * 在音频接收器上启用音频卸载的首选项级别。可以是 {@link #AUDIO_OFFLOAD_MODE_REQUIRED}、{@link #AUDIO_OFFLOAD_MODE_ENABLED} 或 {@link #AUDIO_OFFLOAD_MODE_DISABLED} 之一。
     */
    @Documented
    @Retention(SOURCE)
    @Target(TYPE_USE)
    @IntDef({
        AUDIO_OFFLOAD_MODE_REQUIRED,
        AUDIO_OFFLOAD_MODE_ENABLED,
        AUDIO_OFFLOAD_MODE_DISABLED,
    })
    public @interface AudioOffloadMode {

    }

    /**
     * 轨道选择器将仅选择与渲染器功能兼容的轨道，以提供支持音频卸载的播放场景。如果无法创建支持卸载的轨道选择，则不会选择任何轨道。
     */
    public static final int AUDIO_OFFLOAD_MODE_REQUIRED = 2;

    /**
     * 如果选定的轨道和渲染器功能兼容，轨道选择器将启用音频卸载。
     */
    public static final int AUDIO_OFFLOAD_MODE_ENABLED = 1;

    /**
     * 轨道选择器将禁用音频接收器上的音频卸载。轨道选择不会考虑轨道是否支持卸载。
     */
    public static final int AUDIO_OFFLOAD_MODE_DISABLED = 0;

    /**
     * {@link AudioOffloadPreferences} 的构建器。有关可以使用此构建器配置的参数的说明，请参阅 {@link AudioOffloadPreferences} 文档。
     */
    public static final class Builder {

      private @AudioOffloadMode int audioOffloadMode;
      private boolean isGaplessSupportRequired;
      private boolean isSpeedChangeSupportRequired;

      public Builder() {
        this.audioOffloadMode = AUDIO_OFFLOAD_MODE_DISABLED;
        this.isGaplessSupportRequired = false;
        this.isSpeedChangeSupportRequired = false;
      }

      /**
       * 设置音频卸载模式的首选项。例如，首选模式是启用/禁用，或者是否需要卸载才能播放。默认值为 {@link #AUDIO_OFFLOAD_MODE_DISABLED}。
       *
       * @param audioOffloadMode 用于启用/禁用卸载。可以是 {@link #AUDIO_OFFLOAD_MODE_REQUIRED}、{@link #AUDIO_OFFLOAD_MODE_ENABLED} 或 {@link #AUDIO_OFFLOAD_MODE_DISABLED} 之一。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setAudioOffloadMode(@AudioOffloadMode int audioOffloadMode) {
        this.audioOffloadMode = audioOffloadMode;
        return this;
      }

      /**
       * 设置音频卸载启用的约束。如果为 {@code true}，则仅当设备支持卸载期间的无缝过渡或所选音频不是无缝时，才会启用音频卸载。默认值为 {@code false}。
       *
       * @param isGaplessSupportRequired 用于播放无缝音频卸载。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setIsGaplessSupportRequired(boolean isGaplessSupportRequired) {
        this.isGaplessSupportRequired = isGaplessSupportRequired;
        return this;
      }

      /**
       * 设置音频卸载启用的约束。如果为 {@code true}，则仅当设备支持卸载期间更改播放速度时，才会启用音频卸载。默认值为 {@code false}。
       *
       * @param isSpeedChangeSupportRequired 用于播放音频卸载。
       * @return 此构建器。
       */
      @CanIgnoreReturnValue
      public Builder setIsSpeedChangeSupportRequired(boolean isSpeedChangeSupportRequired) {
        this.isSpeedChangeSupportRequired = isSpeedChangeSupportRequired;
        return this;
      }

      /**
       * 使用选定的值构建 {@link TrackSelectionParameters} 实例。
       */
      public AudioOffloadPreferences build() {
        return new AudioOffloadPreferences(this);
      }
    }

    /**
     * 返回使用默认值配置的实例。
     */
    public static final AudioOffloadPreferences DEFAULT =
        new AudioOffloadPreferences.Builder().build();

    /**
     * 音频播放的首选卸载模式设置。
     */
    public final @AudioOffloadMode int audioOffloadMode;

    /**
     * 启用卸载的约束。如果为 {@code true}，则仅当设备支持卸载期间的无缝过渡或所选音频不是无缝时，才会启用音频卸载。
     */
    public final boolean isGaplessSupportRequired;

    /**
     * 启用卸载的约束。如果为 {@code true}，则仅当设备支持卸载期间更改播放速度时，才会启用音频卸载。
     */
    public final boolean isSpeedChangeSupportRequired;

    private AudioOffloadPreferences(Builder builder) {
      this.audioOffloadMode = builder.audioOffloadMode;
      this.isGaplessSupportRequired = builder.isGaplessSupportRequired;
      this.isSpeedChangeSupportRequired = builder.isSpeedChangeSupportRequired;
    }

    /**
     * 创建一个新的 {@link AudioOffloadPreferences.Builder}，并从当前实例复制初始值。
     */
    public AudioOffloadPreferences.Builder buildUpon() {
      return new AudioOffloadPreferences.Builder()
          .setAudioOffloadMode(audioOffloadMode)
          .setIsGaplessSupportRequired(isGaplessSupportRequired)
          .setIsSpeedChangeSupportRequired(isSpeedChangeSupportRequired);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      AudioOffloadPreferences other = (AudioOffloadPreferences) obj;
      return audioOffloadMode == other.audioOffloadMode
          && isGaplessSupportRequired == other.isGaplessSupportRequired
          && isSpeedChangeSupportRequired == other.isSpeedChangeSupportRequired;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + audioOffloadMode;
      result = 31 * result + (isGaplessSupportRequired ? 1 : 0);
      result = 31 * result + (isSpeedChangeSupportRequired ? 1 : 0);
      return result;
    }

    private static final String FIELD_AUDIO_OFFLOAD_MODE_PREFERENCE = Util.intToStringMaxRadix(1);
    private static final String FIELD_IS_GAPLESS_SUPPORT_REQUIRED = Util.intToStringMaxRadix(2);
    private static final String FIELD_IS_SPEED_CHANGE_SUPPORT_REQUIRED =
        Util.intToStringMaxRadix(3);

    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putInt(FIELD_AUDIO_OFFLOAD_MODE_PREFERENCE, audioOffloadMode);
      bundle.putBoolean(FIELD_IS_GAPLESS_SUPPORT_REQUIRED, isGaplessSupportRequired);
      bundle.putBoolean(FIELD_IS_SPEED_CHANGE_SUPPORT_REQUIRED, isSpeedChangeSupportRequired);
      return bundle;
    }

    /**
     * Construct an instance from a {@link Bundle} produced by {@link #toBundle()}.
     */
    public static AudioOffloadPreferences fromBundle(Bundle bundle) {
      return new AudioOffloadPreferences.Builder()
          .setAudioOffloadMode(
              bundle.getInt(FIELD_AUDIO_OFFLOAD_MODE_PREFERENCE, DEFAULT.audioOffloadMode))
          .setIsGaplessSupportRequired(
              bundle.getBoolean(
                  FIELD_IS_GAPLESS_SUPPORT_REQUIRED, DEFAULT.isGaplessSupportRequired))
          .setIsSpeedChangeSupportRequired(
              bundle.getBoolean(
                  FIELD_IS_SPEED_CHANGE_SUPPORT_REQUIRED, DEFAULT.isSpeedChangeSupportRequired))
          .build();
    }
  }
  /**
   * 一个包含默认值的实例，除了从 {@link Context} 获取的值。
   *
   * <p>如果可能，请使用 {@link #getDefaults(Context)} 代替。
   *
   * <p>此实例将不包含以下设置：
   *
   * <ul>
   *   <li>{@link Builder#setViewportSizeToPhysicalDisplaySize(Context, boolean) 视口约束} 未为主显示器配置。
   *   <li>{@link Builder#setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(Context)
   *       首选文本语言和角色标志} 未配置为 {@link CaptioningManager} 的可访问性设置。
   * </ul>
   */
  @UnstableApi
  @SuppressWarnings("deprecation")
  public static final TrackSelectionParameters DEFAULT_WITHOUT_CONTEXT = new Builder().build();

  /**
   * @deprecated 此实例未使用 {@link Context} 约束进行配置。请使用 {@link #getDefaults(Context)} 代替。
   */
  @UnstableApi
  @Deprecated
  public static final TrackSelectionParameters DEFAULT = DEFAULT_WITHOUT_CONTEXT;

  /**
   * 返回使用默认值配置的实例。
   */
  public static TrackSelectionParameters getDefaults(Context context) {
    return new Builder(context).build();
  }

  // 视频
  /**
   * 允许的最大视频宽度（以像素为单位）。默认值为 {@link Integer#MAX_VALUE}（即无约束）。
   *
   * <p>要限制自适应视频轨道选择以适应给定的视口（视频将在其中播放的显示区域），请使用 ({@link #viewportWidth}, {@link #viewportHeight} 和 {@link #viewportOrientationMayChange}) 代替。
   */
  public final int maxVideoWidth;

  /**
   * 允许的最大视频高度（以像素为单位）。默认值为 {@link Integer#MAX_VALUE}（即无约束）。
   *
   * <p>要限制自适应视频轨道选择以适应给定的视口（视频将在其中播放的显示区域），请使用 ({@link #viewportWidth}, {@link #viewportHeight} 和 {@link #viewportOrientationMayChange}) 代替。
   */
  public final int maxVideoHeight;

  /**
   * 允许的最大视频帧率（以赫兹为单位）。默认值为 {@link Integer#MAX_VALUE}（即无约束）。
   */
  public final int maxVideoFrameRate;

  /**
   * 允许的最大视频比特率（以比特/秒为单位）。默认值为 {@link Integer#MAX_VALUE}（即无约束）。
   */
  public final int maxVideoBitrate;

  /**
   * 允许的最小视频宽度（以像素为单位）。默认值为 0（即无约束）。
   */
  public final int minVideoWidth;

  /**
   * 允许的最小视频高度（以像素为单位）。默认值为 0（即无约束）。
   */
  public final int minVideoHeight;

  /**
   * 允许的最小视频帧率（以赫兹为单位）。默认值为 0（即无约束）。
   */
  public final int minVideoFrameRate;

  /**
   * 允许的最小视频比特率（以比特/秒为单位）。默认值为 0（即无约束）。
   */
  public final int minVideoBitrate;

  /**
   * 视口宽度（以像素为单位）。限制自适应内容的视频轨道选择，以便仅选择适合视口的轨道。默认值是主显示器的物理宽度（以像素为单位）。
   */
  public final int viewportWidth;

  /**
   * 视口高度（以像素为单位）。限制自适应内容的视频轨道选择，以便仅选择适合视口的轨道。默认值是主显示器的物理高度（以像素为单位）。
   */
  public final int viewportHeight;

  /**
   * 视口方向是否可能在播放期间发生变化。限制自适应内容的视频轨道选择，以便仅选择适合视口的轨道。默认值为 {@code true}。
   */
  public final boolean viewportOrientationMayChange;

  /**
   * 视频轨道的首选样本 MIME 类型（按优先级顺序），或空列表表示无首选项。默认值为空列表。
   */
  public final ImmutableList<String> preferredVideoMimeTypes;

  /**
   * 视频轨道的首选 {@link C.RoleFlags}。{@code 0} 选择默认轨道（如果有），否则选择第一个轨道。默认值为 {@code 0}。
   */
  public final @C.RoleFlags int preferredVideoRoleFlags;

  // 音频
  /**
   * 音频和强制文本轨道的首选语言，符合 IETF BCP 47 标准（按优先级顺序）。空列表选择默认轨道，如果没有默认轨道则选择第一个轨道。默认值为空列表。
   */
  public final ImmutableList<String> preferredAudioLanguages;

  /**
   * 音频轨道的首选 {@link C.RoleFlags}。{@code 0} 选择默认轨道（如果有），否则选择第一个轨道。默认值为 {@code 0}。
   */
  public final @C.RoleFlags int preferredAudioRoleFlags;

  /**
   * 允许的最大音频通道数。默认值为 {@link Integer#MAX_VALUE}（即无约束）。
   */
  public final int maxAudioChannelCount;

  /**
   * 允许的最大音频比特率（以比特/秒为单位）。默认值为 {@link Integer#MAX_VALUE}（即无约束）。
   */
  public final int maxAudioBitrate;

  /**
   * 音频轨道的首选样本 MIME 类型（按优先级顺序），或空列表表示无首选项。默认值为空列表。
   */
  public final ImmutableList<String> preferredAudioMimeTypes;

  /**
   * 音频播放的首选卸载模式设置。默认值为 {@link AudioOffloadPreferences#DEFAULT}。
   */
  @UnstableApi
  public final AudioOffloadPreferences audioOffloadPreferences;

  // 文本
  /**
   * 文本轨道的首选语言，符合 IETF BCP 47 标准（按优先级顺序）。空列表选择默认轨道（如果有），否则不选择任何轨道。默认值为空列表，或者如果启用了可访问性 {@link CaptioningManager}，则为 {@link CaptioningManager} 的语言。
   */
  public final ImmutableList<String> preferredTextLanguages;

  /**
   * 文本轨道的首选 {@link C.RoleFlags}。{@code 0} 选择默认轨道（如果有），否则不选择任何轨道。默认值为 {@code 0}，或者如果启用了可访问性 {@link CaptioningManager}，则为 {@link C#ROLE_FLAG_SUBTITLE} | {@link C#ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND}。
   */
  public final @C.RoleFlags int preferredTextRoleFlags;

  /**
   * 文本轨道选择时忽略的选择标志位掩码。参见 {@link C.SelectionFlags}。默认值为 {@code 0}（即不忽略任何标志）。
   */
  public final @C.SelectionFlags int ignoredTextSelectionFlags;

  /**
   * 如果 {@link #preferredTextLanguages} 不可用或未设置，是否应选择语言未确定的文本轨道。默认值为 {@code false}。
   */
  public final boolean selectUndeterminedTextLanguage;

  // 图像
  /**
   * 如果图像轨道和视频轨道都可用，是否优先选择图像轨道。默认值为 {@code false}。
   */
  @UnstableApi
  public final boolean isPrioritizeImageOverVideoEnabled;

  // 通用
  /**
   * 是否强制选择符合所有其他约束的最低比特率的音频和视频轨道。默认值为 {@code false}。
   */
  public final boolean forceLowestBitrate;

  /**
   * 是否强制选择符合所有其他约束的最高比特率的音频和视频轨道。默认值为 {@code false}。
   */
  public final boolean forceHighestSupportedBitrate;

  /**
   * 强制选择特定轨道的覆盖。
   */
  public final ImmutableMap<TrackGroup, TrackSelectionOverride> overrides;

  /**
   * 禁用的轨道类型。不会选择禁用类型的任何轨道，因此不会播放包含在集合中的任何轨道类型。默认值为未禁用任何轨道类型（空集合）。
   */
  public final ImmutableSet<@C.TrackType Integer> disabledTrackTypes;

  @UnstableApi
  protected TrackSelectionParameters(Builder builder) {
    // Video
    this.maxVideoWidth = builder.maxVideoWidth;
    this.maxVideoHeight = builder.maxVideoHeight;
    this.maxVideoFrameRate = builder.maxVideoFrameRate;
    this.maxVideoBitrate = builder.maxVideoBitrate;
    this.minVideoWidth = builder.minVideoWidth;
    this.minVideoHeight = builder.minVideoHeight;
    this.minVideoFrameRate = builder.minVideoFrameRate;
    this.minVideoBitrate = builder.minVideoBitrate;
    this.viewportWidth = builder.viewportWidth;
    this.viewportHeight = builder.viewportHeight;
    this.viewportOrientationMayChange = builder.viewportOrientationMayChange;
    this.preferredVideoMimeTypes = builder.preferredVideoMimeTypes;
    this.preferredVideoRoleFlags = builder.preferredVideoRoleFlags;
    // Audio
    this.preferredAudioLanguages = builder.preferredAudioLanguages;
    this.preferredAudioRoleFlags = builder.preferredAudioRoleFlags;
    this.maxAudioChannelCount = builder.maxAudioChannelCount;
    this.maxAudioBitrate = builder.maxAudioBitrate;
    this.preferredAudioMimeTypes = builder.preferredAudioMimeTypes;
    this.audioOffloadPreferences = builder.audioOffloadPreferences;
    // Text
    this.preferredTextLanguages = builder.preferredTextLanguages;
    this.preferredTextRoleFlags = builder.preferredTextRoleFlags;
    this.ignoredTextSelectionFlags = builder.ignoredTextSelectionFlags;
    this.selectUndeterminedTextLanguage = builder.selectUndeterminedTextLanguage;
    // Image
    this.isPrioritizeImageOverVideoEnabled = builder.isPrioritizeImageOverVideoEnabled;
    // General
    this.forceLowestBitrate = builder.forceLowestBitrate;
    this.forceHighestSupportedBitrate = builder.forceHighestSupportedBitrate;
    this.overrides = ImmutableMap.copyOf(builder.overrides);
    this.disabledTrackTypes = ImmutableSet.copyOf(builder.disabledTrackTypes);
  }

  /**
   * Creates a new {@link Builder}, copying the initial values from this instance.
   */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackSelectionParameters other = (TrackSelectionParameters) obj;
    // Video
    return maxVideoWidth == other.maxVideoWidth
        && maxVideoHeight == other.maxVideoHeight
        && maxVideoFrameRate == other.maxVideoFrameRate
        && maxVideoBitrate == other.maxVideoBitrate
        && minVideoWidth == other.minVideoWidth
        && minVideoHeight == other.minVideoHeight
        && minVideoFrameRate == other.minVideoFrameRate
        && minVideoBitrate == other.minVideoBitrate
        && viewportOrientationMayChange == other.viewportOrientationMayChange
        && viewportWidth == other.viewportWidth
        && viewportHeight == other.viewportHeight
        && preferredVideoMimeTypes.equals(other.preferredVideoMimeTypes)
        && preferredVideoRoleFlags == other.preferredVideoRoleFlags
        // Audio
        && preferredAudioLanguages.equals(other.preferredAudioLanguages)
        && preferredAudioRoleFlags == other.preferredAudioRoleFlags
        && maxAudioChannelCount == other.maxAudioChannelCount
        && maxAudioBitrate == other.maxAudioBitrate
        && preferredAudioMimeTypes.equals(other.preferredAudioMimeTypes)
        && audioOffloadPreferences.equals(other.audioOffloadPreferences)
        // Text
        && preferredTextLanguages.equals(other.preferredTextLanguages)
        && preferredTextRoleFlags == other.preferredTextRoleFlags
        && ignoredTextSelectionFlags == other.ignoredTextSelectionFlags
        && selectUndeterminedTextLanguage == other.selectUndeterminedTextLanguage
        // Image
        && isPrioritizeImageOverVideoEnabled == other.isPrioritizeImageOverVideoEnabled
        // General
        && forceLowestBitrate == other.forceLowestBitrate
        && forceHighestSupportedBitrate == other.forceHighestSupportedBitrate
        && overrides.equals(other.overrides)
        && disabledTrackTypes.equals(other.disabledTrackTypes);
  }

  @Override
  public int hashCode() {
    int result = 1;
    // Video
    result = 31 * result + maxVideoWidth;
    result = 31 * result + maxVideoHeight;
    result = 31 * result + maxVideoFrameRate;
    result = 31 * result + maxVideoBitrate;
    result = 31 * result + minVideoWidth;
    result = 31 * result + minVideoHeight;
    result = 31 * result + minVideoFrameRate;
    result = 31 * result + minVideoBitrate;
    result = 31 * result + (viewportOrientationMayChange ? 1 : 0);
    result = 31 * result + viewportWidth;
    result = 31 * result + viewportHeight;
    result = 31 * result + preferredVideoMimeTypes.hashCode();
    result = 31 * result + preferredVideoRoleFlags;
    // Audio
    result = 31 * result + preferredAudioLanguages.hashCode();
    result = 31 * result + preferredAudioRoleFlags;
    result = 31 * result + maxAudioChannelCount;
    result = 31 * result + maxAudioBitrate;
    result = 31 * result + preferredAudioMimeTypes.hashCode();
    result = 31 * result + audioOffloadPreferences.hashCode();
    // Text
    result = 31 * result + preferredTextLanguages.hashCode();
    result = 31 * result + preferredTextRoleFlags;
    result = 31 * result + ignoredTextSelectionFlags;
    result = 31 * result + (selectUndeterminedTextLanguage ? 1 : 0);
    // Image
    result = 31 * result + (isPrioritizeImageOverVideoEnabled ? 1 : 0);
    // General
    result = 31 * result + (forceLowestBitrate ? 1 : 0);
    result = 31 * result + (forceHighestSupportedBitrate ? 1 : 0);
    result = 31 * result + overrides.hashCode();
    result = 31 * result + disabledTrackTypes.hashCode();
    return result;
  }

  private static final String FIELD_PREFERRED_AUDIO_LANGUAGES = Util.intToStringMaxRadix(1);
  private static final String FIELD_PREFERRED_AUDIO_ROLE_FLAGS = Util.intToStringMaxRadix(2);
  private static final String FIELD_PREFERRED_TEXT_LANGUAGES = Util.intToStringMaxRadix(3);
  private static final String FIELD_PREFERRED_TEXT_ROLE_FLAGS = Util.intToStringMaxRadix(4);
  private static final String FIELD_SELECT_UNDETERMINED_TEXT_LANGUAGE = Util.intToStringMaxRadix(5);
  private static final String FIELD_MAX_VIDEO_WIDTH = Util.intToStringMaxRadix(6);
  private static final String FIELD_MAX_VIDEO_HEIGHT = Util.intToStringMaxRadix(7);
  private static final String FIELD_MAX_VIDEO_FRAMERATE = Util.intToStringMaxRadix(8);
  private static final String FIELD_MAX_VIDEO_BITRATE = Util.intToStringMaxRadix(9);
  private static final String FIELD_MIN_VIDEO_WIDTH = Util.intToStringMaxRadix(10);
  private static final String FIELD_MIN_VIDEO_HEIGHT = Util.intToStringMaxRadix(11);
  private static final String FIELD_MIN_VIDEO_FRAMERATE = Util.intToStringMaxRadix(12);
  private static final String FIELD_MIN_VIDEO_BITRATE = Util.intToStringMaxRadix(13);
  private static final String FIELD_VIEWPORT_WIDTH = Util.intToStringMaxRadix(14);
  private static final String FIELD_VIEWPORT_HEIGHT = Util.intToStringMaxRadix(15);
  private static final String FIELD_VIEWPORT_ORIENTATION_MAY_CHANGE = Util.intToStringMaxRadix(16);
  private static final String FIELD_PREFERRED_VIDEO_MIMETYPES = Util.intToStringMaxRadix(17);
  private static final String FIELD_MAX_AUDIO_CHANNEL_COUNT = Util.intToStringMaxRadix(18);
  private static final String FIELD_MAX_AUDIO_BITRATE = Util.intToStringMaxRadix(19);
  private static final String FIELD_PREFERRED_AUDIO_MIME_TYPES = Util.intToStringMaxRadix(20);
  private static final String FIELD_FORCE_LOWEST_BITRATE = Util.intToStringMaxRadix(21);
  private static final String FIELD_FORCE_HIGHEST_SUPPORTED_BITRATE = Util.intToStringMaxRadix(22);
  private static final String FIELD_SELECTION_OVERRIDES = Util.intToStringMaxRadix(23);
  private static final String FIELD_DISABLED_TRACK_TYPE = Util.intToStringMaxRadix(24);
  private static final String FIELD_PREFERRED_VIDEO_ROLE_FLAGS = Util.intToStringMaxRadix(25);
  private static final String FIELD_IGNORED_TEXT_SELECTION_FLAGS = Util.intToStringMaxRadix(26);
  private static final String FIELD_AUDIO_OFFLOAD_MODE_PREFERENCE = Util.intToStringMaxRadix(27);
  private static final String FIELD_IS_GAPLESS_SUPPORT_REQUIRED = Util.intToStringMaxRadix(28);
  private static final String FIELD_IS_SPEED_CHANGE_SUPPORT_REQUIRED = Util.intToStringMaxRadix(29);
  private static final String FIELD_AUDIO_OFFLOAD_PREFERENCES = Util.intToStringMaxRadix(30);
  private static final String FIELD_IS_PREFER_IMAGE_OVER_VIDEO_ENABLED =
      Util.intToStringMaxRadix(31);

  /**
   * 定义子类在实现 {@link #toBundle()} 并委托给 {@link Builder#Builder(Bundle)} 时使用的最小字段 ID 值。
   *
   * <p>子类应通过在此常量上应用非负偏移量并将结果传递给 {@link Util#intToStringMaxRadix(int)} 来获取其 {@link Bundle} 表示的键。
   */
  @UnstableApi
  protected static final int FIELD_CUSTOM_ID_BASE = 1000;

  @CallSuper
  public Bundle toBundle() {
    Bundle bundle = new Bundle();

    // Video
    bundle.putInt(FIELD_MAX_VIDEO_WIDTH, maxVideoWidth);
    bundle.putInt(FIELD_MAX_VIDEO_HEIGHT, maxVideoHeight);
    bundle.putInt(FIELD_MAX_VIDEO_FRAMERATE, maxVideoFrameRate);
    bundle.putInt(FIELD_MAX_VIDEO_BITRATE, maxVideoBitrate);
    bundle.putInt(FIELD_MIN_VIDEO_WIDTH, minVideoWidth);
    bundle.putInt(FIELD_MIN_VIDEO_HEIGHT, minVideoHeight);
    bundle.putInt(FIELD_MIN_VIDEO_FRAMERATE, minVideoFrameRate);
    bundle.putInt(FIELD_MIN_VIDEO_BITRATE, minVideoBitrate);
    bundle.putInt(FIELD_VIEWPORT_WIDTH, viewportWidth);
    bundle.putInt(FIELD_VIEWPORT_HEIGHT, viewportHeight);
    bundle.putBoolean(FIELD_VIEWPORT_ORIENTATION_MAY_CHANGE, viewportOrientationMayChange);
    bundle.putStringArray(
        FIELD_PREFERRED_VIDEO_MIMETYPES, preferredVideoMimeTypes.toArray(new String[0]));
    bundle.putInt(FIELD_PREFERRED_VIDEO_ROLE_FLAGS, preferredVideoRoleFlags);
    // Audio
    bundle.putStringArray(
        FIELD_PREFERRED_AUDIO_LANGUAGES, preferredAudioLanguages.toArray(new String[0]));
    bundle.putInt(FIELD_PREFERRED_AUDIO_ROLE_FLAGS, preferredAudioRoleFlags);
    bundle.putInt(FIELD_MAX_AUDIO_CHANNEL_COUNT, maxAudioChannelCount);
    bundle.putInt(FIELD_MAX_AUDIO_BITRATE, maxAudioBitrate);
    bundle.putStringArray(
        FIELD_PREFERRED_AUDIO_MIME_TYPES, preferredAudioMimeTypes.toArray(new String[0]));
    // Text
    bundle.putStringArray(
        FIELD_PREFERRED_TEXT_LANGUAGES, preferredTextLanguages.toArray(new String[0]));
    bundle.putInt(FIELD_PREFERRED_TEXT_ROLE_FLAGS, preferredTextRoleFlags);
    bundle.putInt(FIELD_IGNORED_TEXT_SELECTION_FLAGS, ignoredTextSelectionFlags);
    bundle.putBoolean(FIELD_SELECT_UNDETERMINED_TEXT_LANGUAGE, selectUndeterminedTextLanguage);
    bundle.putInt(FIELD_AUDIO_OFFLOAD_MODE_PREFERENCE, audioOffloadPreferences.audioOffloadMode);
    bundle.putBoolean(
        FIELD_IS_GAPLESS_SUPPORT_REQUIRED, audioOffloadPreferences.isGaplessSupportRequired);
    bundle.putBoolean(
        FIELD_IS_SPEED_CHANGE_SUPPORT_REQUIRED,
        audioOffloadPreferences.isSpeedChangeSupportRequired);
    bundle.putBundle(FIELD_AUDIO_OFFLOAD_PREFERENCES, audioOffloadPreferences.toBundle());
    // Image
    bundle.putBoolean(FIELD_IS_PREFER_IMAGE_OVER_VIDEO_ENABLED, isPrioritizeImageOverVideoEnabled);
    // General
    bundle.putBoolean(FIELD_FORCE_LOWEST_BITRATE, forceLowestBitrate);
    bundle.putBoolean(FIELD_FORCE_HIGHEST_SUPPORTED_BITRATE, forceHighestSupportedBitrate);
    bundle.putParcelableArrayList(
        FIELD_SELECTION_OVERRIDES,
        toBundleArrayList(overrides.values(), TrackSelectionOverride::toBundle));
    bundle.putIntArray(FIELD_DISABLED_TRACK_TYPE, Ints.toArray(disabledTrackTypes));

    return bundle;
  }

  /**
   * Construct an instance from a {@link Bundle} produced by {@link #toBundle()}.
   */
  public static TrackSelectionParameters fromBundle(Bundle bundle) {
    return new Builder(bundle).build();
  }
}
