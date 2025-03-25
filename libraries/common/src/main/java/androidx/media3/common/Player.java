package androidx.media3.common;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

/**
 * 一个媒体播放器接口，定义了高级功能，例如播放、暂停、跳转以及查询当前播放媒体的属性。
 *
 * <h2>播放器功能与用法</h2>
 *
 * <p>实现此接口的媒体播放器具有以下重要特性：
 *
 * <ul>
 *   <li>所有方法必须从单一的 {@linkplain #getApplicationLooper() 应用线程} 调用，除非另有说明。注册监听器中的回调也会在同一线程中调用。
 *   <li>可用功能可能受限。播放器实例提供一组 {@link #getAvailableCommands() 可用命令} 来标识功能支持，接口使用者必须仅在相应 {@link Command} 可用时调用方法。
 *   <li>用户可以注册 {@link Player.Listener} 回调，以获取状态变更的通知。
 *   <li>播放器实例需要在每次方法调用后立即更新可见状态，即使实际变更是在后台线程或其他设备上处理的。这简化了方法调用者的使用，因为无需考虑异步处理。
 *   <li>播放器实例可以提供播放列表操作，例如对 {@link MediaItem} 实例的“设置”、“添加”、“删除”、“移动”或“替换”。播放器还可以支持播放列表内的 {@linkplain RepeatMode 重复模式} 和随机播放。播放器提供 {@link Timeline} 表示播放列表的结构及其所有项，可通过调用 {@link #getCurrentTimeline()} 获取。
 *   <li>播放器实例可以提供在当前播放项内和其他项之间的跳转功能，使用各种 {@code seek...} 方法。
 *   <li>播放器实例可以提供 {@link Tracks}，定义当前可用和选定的轨道，可通过调用 {@link #getCurrentTracks()} 获取。用户还可以通过设置 {@link TrackSelectionParameters} 来修改轨道选择行为，使用 {@link #setTrackSelectionParameters}。
 *   <li>播放器实例可以提供关于当前播放项的 {@link MediaMetadata}，可通过调用 {@link #getMediaMetadata()} 获取。
 *   <li>播放器实例可以提供其媒体结构中的广告信息，例如通过 {@link #isPlayingAd()}。
 *   <li>播放器实例可以接受不同类型的视频输出，例如用于视频渲染的 {@link #setVideoSurfaceView SurfaceView} 或 {@link #setVideoTextureView TextureView}。
 *   <li>播放器实例可以处理 {@linkplain #setPlaybackSpeed 播放速度}、{@linkplain #getAudioAttributes 音频属性} 和 {@linkplain #setVolume 音频音量}。
 *   <li>播放器实例可以提供关于 {@linkplain #getDeviceInfo 播放设备} 的信息（可能是远程设备），并允许更改设备的音量。
 * </ul>
 *
 * <h2>API 稳定性保证</h2>
 *
 * <p>Player 接口及其相关类的大部分内容属于稳定 API，保证向后兼容。只有更高级的用例可能需要依赖 {@link UnstableApi} 类和方法，这些类和方法可能会在未来版本中进行不兼容的更改甚至移除。Player 接口的实现者不受这些 API 稳定性保证的约束。
 *
 * <h2>播放器状态</h2>
 *
 * <p>用户可以通过 {@link #addListener} 添加 {@link Player.Listener} 来监听状态变更。
 *
 * <p>播放器整体状态的主要元素包括：
 *
 * <ul>
 *   <li>播放列表
 *       <ul>
 *         <li>可以通过 {@link #setMediaItem} 等方法添加 {@link MediaItem} 实例，以定义播放器将播放的内容。
 *         <li>当前播放列表可通过 {@link #getCurrentTimeline} 以及便捷方法（如 {@link #getMediaItemCount} 或 {@link #getCurrentMediaItem}）获取。
 *         <li>如果播放列表为空，播放器只能处于 {@link #STATE_IDLE} 或 {@link #STATE_ENDED} 状态。
 *       </ul>
 *   <li>播放状态
 *       <ul>
 *         <li>{@link #STATE_IDLE}：初始状态，播放器 {@linkplain #stop 停止} 时的状态，以及播放 {@linkplain #getPlayerError 失败} 时的状态。在此状态下，播放器仅持有有限的资源。必须调用 {@link #prepare} 以从此状态转换。
 *         <li>{@link #STATE_BUFFERING}：播放器无法立即从其当前位置播放。通常是因为需要加载更多数据。
 *         <li>{@link #STATE_READY}：播放器能够立即从其当前位置播放。
 *         <li>{@link #STATE_ENDED}：播放器完成播放所有媒体，或者没有媒体可播放。
 *       </ul>
 *   <li>播放/暂停、播放抑制和 isPlaying
 *       <ul>
 *         <li>{@linkplain #getPlayWhenReady() playWhenReady}：表示用户的播放意图。可以通过 {@link #play} 或 {@link #pause} 设置。
 *         <li>{@linkplain #getPlaybackSuppressionReason() playback suppression}：定义即使 {@linkplain #getPlayWhenReady() playWhenReady} 为 {@code true}，播放仍被抑制的原因。
 *         <li>{@link #isPlaying()}：播放器是否正在播放（即其位置正在前进且媒体正在呈现）。仅当播放状态为 {@link #STATE_READY}、{@linkplain #getPlayWhenReady() playWhenReady} 为 {@code true} 且播放未被抑制时，此值才为 {@code true}。
 *       </ul>
 *   <li>播放位置
 *       <ul>
 *         <li>{@linkplain #getCurrentMediaItemIndex() media item index}：播放列表中的索引。
 *         <li>{@linkplain #isPlayingAd() ad insertion}：是否正在播放插入的广告，以及其所属的 {@linkplain #getCurrentAdGroupIndex() 广告组索引} 和 {@linkplain #getCurrentAdIndexInAdGroup() 广告组中的广告索引}。
 *         <li>{@linkplain #getCurrentPosition() current position}：播放的当前位置。除非正在播放广告，否则这与 {@linkplain #getContentPosition() 内容位置} 相同，此时表示插入广告中的位置。
 *       </ul>
 * </ul>
 *
 * <p>注意，没有针对正常播放进度的回调，只有针对 {@linkplain Listener#onMediaItemTransition 媒体项间切换} 和其他 {@linkplain Listener#onPositionDiscontinuity 位置不连续性} 的回调。需要监控播放进度的代码（例如 UI 进度条）应在适当的时间间隔内查询当前位置。
 *
 * <h2>实现 Player 接口</h2>
 *
 * <p>实现 Player 接口非常复杂，因为该接口包含许多需要提供一致状态和行为的便捷方法，需要正确处理监听器和可用命令，并且即使方法在内部是异步处理的，也期望立即发生状态变更。因此，建议实现者继承 {@link SimpleBasePlayer}，它处理了所有这些复杂性，并为接口实现者提供了更简单的集成点。
 */
public interface Player {

  /**
   * 一个包含 {@linkplain Event 事件} 的集合。
   */
  final class Events {

    private final FlagSet flags;

    /**
     * 创建一个实例。
     *
     * @param flags 包含 {@linkplain Event 事件} 的 {@link FlagSet}。
     */
    @UnstableApi
    public Events(FlagSet flags) {
      this.flags = flags;
    }

    /**
     * 返回给定的 {@link Event} 是否发生。
     *
     * @param event 要检查的 {@link Event}。
     * @return 如果 {@link Event} 发生，则返回 true；否则返回 false。
     */
    public boolean contains(@Event int event) {
      return flags.contains(event);
    }

    /**
     * 返回给定的任意一个 {@linkplain Event 事件} 是否发生。
     *
     * @param events 要检查的 {@linkplain Event 事件} 数组。
     * @return 如果任意一个 {@linkplain Event 事件} 发生，则返回 true；否则返回 false。
     */
    public boolean containsAny(@Event int... events) {
      return flags.containsAny(events);
    }

    /**
     * 返回集合中事件的数量。
     */
    public int size() {
      return flags.size();
    }

    /**
     * 返回给定索引处的 {@link Event}。
     *
     * <p>尽管支持基于索引的访问，但这并不意味着这些事件的特定顺序。
     *
     * @param index 索引。必须在 0（包含）到 {@link #size()}（不包含）之间。
     * @return 给定索引处的 {@link Event}。
     * @throws IndexOutOfBoundsException 如果索引超出允许范围。
     */
    public @Event int get(int index) {
      return flags.get(index);
    }

    @Override
    public int hashCode() {
      return flags.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Events)) {
        return false;
      }
      Events other = (Events) obj;
      return flags.equals(other.flags);
    }
  }

  /**
   * 描述播放不连续性中涉及的播放位置的信息。
   */
  final class PositionInfo {

    /**
     * 窗口的 UID，如果时间轴 {@link Timeline#isEmpty() 为空}，则为 {@code null}。
     */
    @Nullable
    public final Object windowUid;

    /**
     * @deprecated 请使用 {@link #mediaItemIndex} 代替。
     */
    @UnstableApi
    @Deprecated
    public final int windowIndex;

    /**
     * 媒体项的索引。
     */
    public final int mediaItemIndex;

    /**
     * 媒体项，如果时间轴 {@link Timeline#isEmpty() 为空}，则为 {@code null}。
     */
    @UnstableApi
    @Nullable
    public final MediaItem mediaItem;

    /**
     * 时间段的 UID，如果时间轴 {@link Timeline#isEmpty() 为空}，则为 {@code null}。
     */
    @Nullable
    public final Object periodUid;

    /**
     * 时间段的索引。
     */
    public final int periodIndex;

    /**
     * 播放位置，以毫秒为单位。
     */
    public final long positionMs;

    /**
     * 内容位置，以毫秒为单位。
     *
     * <p>如果 {@link #adGroupIndex} 为 {@link C#INDEX_UNSET}，则此值与 {@link #positionMs} 相同。
     */
    public final long contentPositionMs;

    /**
     * 如果播放位置在广告中，则为广告组的索引；否则为 {@link C#INDEX_UNSET}。
     */
    public final int adGroupIndex;

    /**
     * 如果播放位置在广告中，则为广告组中广告的索引；否则为 {@link C#INDEX_UNSET}。
     */
    public final int adIndexInAdGroup;

    /**
     * @deprecated 请使用 {@link #PositionInfo(Object, int, MediaItem, Object, int, long, long, int,
     * int)} 代替。
     */
    @Deprecated
    @UnstableApi
    public PositionInfo(
        @Nullable Object windowUid,
        int mediaItemIndex,
        @Nullable Object periodUid,
        int periodIndex,
        long positionMs,
        long contentPositionMs,
        int adGroupIndex,
        int adIndexInAdGroup) {
      this(
          windowUid,
          mediaItemIndex,
          MediaItem.EMPTY,
          periodUid,
          periodIndex,
          positionMs,
          contentPositionMs,
          adGroupIndex,
          adIndexInAdGroup);
    }

    /**
     * Creates an instance.
     */
    @UnstableApi
    @SuppressWarnings("deprecation") // Setting deprecated windowIndex field
    public PositionInfo(
        @Nullable Object windowUid,
        int mediaItemIndex,
        @Nullable MediaItem mediaItem,
        @Nullable Object periodUid,
        int periodIndex,
        long positionMs,
        long contentPositionMs,
        int adGroupIndex,
        int adIndexInAdGroup) {
      this.windowUid = windowUid;
      this.windowIndex = mediaItemIndex;
      this.mediaItemIndex = mediaItemIndex;
      this.mediaItem = mediaItem;
      this.periodUid = periodUid;
      this.periodIndex = periodIndex;
      this.positionMs = positionMs;
      this.contentPositionMs = contentPositionMs;
      this.adGroupIndex = adGroupIndex;
      this.adIndexInAdGroup = adIndexInAdGroup;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PositionInfo that = (PositionInfo) o;
      return equalsForBundling(that)
          && Objects.equal(windowUid, that.windowUid)
          && Objects.equal(periodUid, that.periodUid);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(
          windowUid,
          mediaItemIndex,
          mediaItem,
          periodUid,
          periodIndex,
          positionMs,
          contentPositionMs,
          adGroupIndex,
          adIndexInAdGroup);
    }

    /**
     * Returns whether this position info and the other position info would result in the same
     * {@link #toBundle() Bundle}.
     */
    @UnstableApi
    public boolean equalsForBundling(PositionInfo other) {
      return mediaItemIndex == other.mediaItemIndex
          && periodIndex == other.periodIndex
          && positionMs == other.positionMs
          && contentPositionMs == other.contentPositionMs
          && adGroupIndex == other.adGroupIndex
          && adIndexInAdGroup == other.adIndexInAdGroup
          && Objects.equal(mediaItem, other.mediaItem);
    }

    @VisibleForTesting
    static final String FIELD_MEDIA_ITEM_INDEX = Util.intToStringMaxRadix(0);
    private static final String FIELD_MEDIA_ITEM = Util.intToStringMaxRadix(1);
    @VisibleForTesting
    static final String FIELD_PERIOD_INDEX = Util.intToStringMaxRadix(2);
    @VisibleForTesting
    static final String FIELD_POSITION_MS = Util.intToStringMaxRadix(3);
    @VisibleForTesting
    static final String FIELD_CONTENT_POSITION_MS = Util.intToStringMaxRadix(4);
    private static final String FIELD_AD_GROUP_INDEX = Util.intToStringMaxRadix(5);
    private static final String FIELD_AD_INDEX_IN_AD_GROUP = Util.intToStringMaxRadix(6);

    /**
     * 返回此位置信息的副本，并根据指定的可用命令进行过滤。
     *
     * <p>被过滤的字段将被重置为默认值。
     *
     * <p>如果没有字段被过滤，返回值可能是同一个对象。
     *
     * @param canAccessCurrentMediaItem 是否可用 {@link Player#COMMAND_GET_CURRENT_MEDIA_ITEM} 命令。
     * @param canAccessTimeline         是否可用 {@link Player#COMMAND_GET_TIMELINE} 命令。
     * @return 过滤后的位置信息。
     */
    @UnstableApi
    public PositionInfo filterByAvailableCommands(
        boolean canAccessCurrentMediaItem, boolean canAccessTimeline) {
      if (canAccessCurrentMediaItem && canAccessTimeline) {
        return this;
      }
      return new PositionInfo(
          windowUid,
          canAccessTimeline ? mediaItemIndex : 0,
          canAccessCurrentMediaItem ? mediaItem : null,
          periodUid,
          canAccessTimeline ? periodIndex : 0,
          canAccessCurrentMediaItem ? positionMs : 0,
          canAccessCurrentMediaItem ? contentPositionMs : 0,
          canAccessCurrentMediaItem ? adGroupIndex : C.INDEX_UNSET,
          canAccessCurrentMediaItem ? adIndexInAdGroup : C.INDEX_UNSET);
    }

    /**
     * 返回一个 {@link Bundle}，表示此对象中存储的信息。
     *
     * <p>它会忽略 {@link #windowUid} 和 {@link #periodUid} 字段。
     * 通过 {@link #fromBundle(Bundle)} 恢复的实例的 {@link #windowUid} 和 {@link #periodUid} 将始终为 {@code null}。
     *
     * @param controllerInterfaceVersion 此 Bundle 将发送到的媒体控制器的接口版本。
     */
    @UnstableApi
    public Bundle toBundle(int controllerInterfaceVersion) {
      Bundle bundle = new Bundle();
      if (controllerInterfaceVersion < 3 || mediaItemIndex != 0) {
        bundle.putInt(FIELD_MEDIA_ITEM_INDEX, mediaItemIndex);
      }
      if (mediaItem != null) {
        bundle.putBundle(FIELD_MEDIA_ITEM, mediaItem.toBundle());
      }
      if (controllerInterfaceVersion < 3 || periodIndex != 0) {
        bundle.putInt(FIELD_PERIOD_INDEX, periodIndex);
      }
      if (controllerInterfaceVersion < 3 || positionMs != 0) {
        bundle.putLong(FIELD_POSITION_MS, positionMs);
      }
      if (controllerInterfaceVersion < 3 || contentPositionMs != 0) {
        bundle.putLong(FIELD_CONTENT_POSITION_MS, contentPositionMs);
      }
      if (adGroupIndex != C.INDEX_UNSET) {
        bundle.putInt(FIELD_AD_GROUP_INDEX, adGroupIndex);
      }
      if (adIndexInAdGroup != C.INDEX_UNSET) {
        bundle.putInt(FIELD_AD_INDEX_IN_AD_GROUP, adIndexInAdGroup);
      }
      return bundle;
    }

    /**
     * @deprecated Use {@link #toBundle(int)} instead.
     */
    @UnstableApi
    @Deprecated
    public Bundle toBundle() {
      return toBundle(Integer.MAX_VALUE);
    }

    /**
     * Restores a {@code PositionInfo} from a {@link Bundle}.
     */
    @UnstableApi
    public static PositionInfo fromBundle(Bundle bundle) {
      int mediaItemIndex = bundle.getInt(FIELD_MEDIA_ITEM_INDEX, /* defaultValue= */ 0);
      @Nullable Bundle mediaItemBundle = bundle.getBundle(FIELD_MEDIA_ITEM);
      @Nullable
      MediaItem mediaItem = mediaItemBundle == null ? null : MediaItem.fromBundle(mediaItemBundle);
      int periodIndex = bundle.getInt(FIELD_PERIOD_INDEX, /* defaultValue= */ 0);
      long positionMs = bundle.getLong(FIELD_POSITION_MS, /* defaultValue= */ 0);
      long contentPositionMs = bundle.getLong(FIELD_CONTENT_POSITION_MS, /* defaultValue= */ 0);
      int adGroupIndex = bundle.getInt(FIELD_AD_GROUP_INDEX, /* defaultValue= */ C.INDEX_UNSET);
      int adIndexInAdGroup =
          bundle.getInt(FIELD_AD_INDEX_IN_AD_GROUP, /* defaultValue= */ C.INDEX_UNSET);
      return new PositionInfo(
          /* windowUid= */ null,
          mediaItemIndex,
          mediaItem,
          /* periodUid= */ null,
          periodIndex,
          positionMs,
          contentPositionMs,
          adGroupIndex,
          adIndexInAdGroup);
    }
  }

  /**
   * A set of {@linkplain Command commands}.
   *
   * <p>Instances are immutable.
   */
  final class Commands {

    /**
     * A builder for {@link Commands} instances.
     */
    @UnstableApi
    public static final class Builder {

      @SuppressWarnings("deprecation") // Includes deprecated commands
      private static final @Command int[] SUPPORTED_COMMANDS = {
          COMMAND_PLAY_PAUSE,
          COMMAND_PREPARE,
          COMMAND_STOP,
          COMMAND_SEEK_TO_DEFAULT_POSITION,
          COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
          COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
          COMMAND_SEEK_TO_PREVIOUS,
          COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
          COMMAND_SEEK_TO_NEXT,
          COMMAND_SEEK_TO_MEDIA_ITEM,
          COMMAND_SEEK_BACK,
          COMMAND_SEEK_FORWARD,
          COMMAND_SET_SPEED_AND_PITCH,
          COMMAND_SET_SHUFFLE_MODE,
          COMMAND_SET_REPEAT_MODE,
          COMMAND_GET_CURRENT_MEDIA_ITEM,
          COMMAND_GET_TIMELINE,
          COMMAND_GET_METADATA,
          COMMAND_SET_PLAYLIST_METADATA,
          COMMAND_SET_MEDIA_ITEM,
          COMMAND_CHANGE_MEDIA_ITEMS,
          COMMAND_GET_AUDIO_ATTRIBUTES,
          COMMAND_GET_VOLUME,
          COMMAND_GET_DEVICE_VOLUME,
          COMMAND_SET_VOLUME,
          COMMAND_SET_DEVICE_VOLUME,
          COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
          COMMAND_ADJUST_DEVICE_VOLUME,
          COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
          COMMAND_SET_AUDIO_ATTRIBUTES,
          COMMAND_SET_VIDEO_SURFACE,
          COMMAND_GET_TEXT,
          COMMAND_SET_TRACK_SELECTION_PARAMETERS,
          COMMAND_GET_TRACKS,
          COMMAND_RELEASE
      };

      private final FlagSet.Builder flagsBuilder;

      /**
       * Creates a builder.
       */
      public Builder() {
        flagsBuilder = new FlagSet.Builder();
      }

      private Builder(Commands commands) {
        flagsBuilder = new FlagSet.Builder();
        flagsBuilder.addAll(commands.flags);
      }

      /**
       * Adds a {@link Command}.
       *
       * @param command A {@link Command}.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder add(@Command int command) {
        flagsBuilder.add(command);
        return this;
      }

      /**
       * Adds a {@link Command} if the provided condition is true. Does nothing otherwise.
       *
       * @param command   A {@link Command}.
       * @param condition A condition.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder addIf(@Command int command, boolean condition) {
        flagsBuilder.addIf(command, condition);
        return this;
      }

      /**
       * Adds {@linkplain Command commands}.
       *
       * @param commands The {@linkplain Command commands} to add.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder addAll(@Command int... commands) {
        flagsBuilder.addAll(commands);
        return this;
      }

      /**
       * Adds {@link Commands}.
       *
       * @param commands The set of {@linkplain Command commands} to add.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder addAll(Commands commands) {
        flagsBuilder.addAll(commands.flags);
        return this;
      }

      /**
       * Adds all existing {@linkplain Command commands}.
       *
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder addAllCommands() {
        flagsBuilder.addAll(SUPPORTED_COMMANDS);
        return this;
      }

      /**
       * Removes a {@link Command}.
       *
       * @param command A {@link Command}.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder remove(@Command int command) {
        flagsBuilder.remove(command);
        return this;
      }

      /**
       * Removes a {@link Command} if the provided condition is true. Does nothing otherwise.
       *
       * @param command   A {@link Command}.
       * @param condition A condition.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder removeIf(@Command int command, boolean condition) {
        flagsBuilder.removeIf(command, condition);
        return this;
      }

      /**
       * Removes {@linkplain Command commands}.
       *
       * @param commands The {@linkplain Command commands} to remove.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder removeAll(@Command int... commands) {
        flagsBuilder.removeAll(commands);
        return this;
      }

      /**
       * Builds a {@link Commands} instance.
       *
       * @throws IllegalStateException If this method has already been called.
       */
      public Commands build() {
        return new Commands(flagsBuilder.build());
      }
    }

    /**
     * An empty set of commands.
     */
    public static final Commands EMPTY = new Builder().build();

    private final FlagSet flags;

    private Commands(FlagSet flags) {
      this.flags = flags;
    }

    /**
     * Returns a {@link Builder} initialized with the values of this instance.
     */
    @UnstableApi
    public Builder buildUpon() {
      return new Builder(this);
    }

    /**
     * Returns whether the set of commands contains the specified {@link Command}.
     */
    public boolean contains(@Command int command) {
      return flags.contains(command);
    }

    /**
     * Returns whether the set of commands contains at least one of the given {@code commands}.
     */
    public boolean containsAny(@Command int... commands) {
      return flags.containsAny(commands);
    }

    /**
     * Returns the number of commands in this set.
     */
    public int size() {
      return flags.size();
    }

    /**
     * Returns the {@link Command} at the given index.
     *
     * @param index The index. Must be between 0 (inclusive) and {@link #size()} (exclusive).
     * @return The {@link Command} at the given index.
     * @throws IndexOutOfBoundsException If index is outside the allowed range.
     */
    public @Command int get(int index) {
      return flags.get(index);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Commands)) {
        return false;
      }
      Commands commands = (Commands) obj;
      return flags.equals(commands.flags);
    }

    @Override
    public int hashCode() {
      return flags.hashCode();
    }

    private static final String FIELD_COMMANDS = Util.intToStringMaxRadix(0);

    @UnstableApi
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      ArrayList<Integer> commandsBundle = new ArrayList<>();
      for (int i = 0; i < flags.size(); i++) {
        commandsBundle.add(flags.get(i));
      }
      bundle.putIntegerArrayList(FIELD_COMMANDS, commandsBundle);
      return bundle;
    }

    /**
     * 从 {@link Bundle} 恢复 {@code Commands}。
     */
    @UnstableApi
    public static Commands fromBundle(Bundle bundle) {
      @Nullable ArrayList<Integer> commands = bundle.getIntegerArrayList(FIELD_COMMANDS);
      if (commands == null) {
        return Commands.EMPTY;
      }
      Builder builder = new Builder();
      for (int i = 0; i < commands.size(); i++) {
        builder.add(commands.get(i));
      }
      return builder.build();
    }
  }

  /**
   * 用于监听 {@link Player} 变化的监听器。
   *
   * <p>所有方法都有无操作的默认实现，以允许选择性重写。
   *
   * <p>如果由于 {@linkplain #onAvailableCommandsChanged(Commands) 命令可用性} 的变化导致 {@link Player} getter 的返回值发生变化，则会调用相应的监听器方法。如果由于相应的命令 {@linkplain #onAvailableCommandsChanged(Commands) 不可用} 而导致 {@link Player} getter 的返回值未发生变化，则不会调用相应的监听器方法。
   */
  interface Listener {

    /**
     * 当一个或多个播放器状态发生变化时调用。
     *
     * <p>在一个 {@link Looper} 消息队列迭代中发生的状态变化和事件会一起报告，并且仅在所有单独的回调被触发后才会报告。
     *
     * <p>在以下情况下，监听器应优先使用此方法而不是单独的回调：
     *
     * <ul>
     *   <li>它们打算为多个事件触发相同的逻辑（例如，在 {@link #onPlaybackStateChanged(int)} 和 {@link #onPlayWhenReadyChanged(boolean, int)} 时更新 UI）。
     *   <li>它们需要访问 {@link Player} 对象以触发进一步的事件（例如，在 {@link #onMediaItemTransition(MediaItem, int)} 后调用 {@link Player#seekTo(long)}）。
     *   <li>它们打算一起使用多个状态值或与 {@link Player} getter 方法结合使用。例如，使用 {@link #getCurrentMediaItemIndex()} 与 {@link #onTimelineChanged(Timeline, int)} 中提供的 {@code timeline} 结合使用，只有在此方法内部才是安全的。
     *   <li>它们对逻辑上一起发生的事件感兴趣（例如，由于 {@link #onMediaItemTransition(MediaItem, int)} 导致 {@link #onPlaybackStateChanged(int)} 变为 {@link #STATE_BUFFERING}）。
     * </ul>
     *
     * @param player 状态发生变化的 {@link Player}。使用 getter 获取最新状态。
     * @param events 在此迭代中发生的事件，指示哪些播放器状态发生了变化。
     */
    default void onEvents(Player player, Events events) {
    }

    /**
     * 当 {@link Player#getCurrentTimeline()} 的值发生变化时调用。
     *
     * <p>请注意，当前的 {@link MediaItem} 或播放位置可能会由于时间线的变化而发生变化。如果由于此时间线变化导致播放无法顺利继续，则会触发单独的 {@link #onPositionDiscontinuity(PositionInfo, PositionInfo, int)} 回调。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param timeline 最新的时间线。永远不会为 null，但可能为空。
     * @param reason   导致此时间线变化的 {@link TimelineChangeReason}。
     */
    default void onTimelineChanged(Timeline timeline, @TimelineChangeReason int reason) {
    }

    /**
     * 当播放根据当前的 {@link #getRepeatMode() 重复模式} 过渡到某个媒体项或开始重复某个媒体项时调用。
     *
     * <p>请注意，当 {@link #getCurrentTimeline()} 的值变为非空或空时，也会调用此回调。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param mediaItem {@link MediaItem}。如果播放列表变为空，则可能为 null。
     * @param reason    过渡的原因。
     */
    default void onMediaItemTransition(
        @Nullable MediaItem mediaItem, @MediaItemTransitionReason int reason) {
    }

    /**
     * 当 {@link Player#getCurrentTracks()} 的值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param tracks 可用的轨道信息。永远不会为 null，但长度可能为零。
     */
    default void onTracksChanged(Tracks tracks) {
    }

    /**
     * 当 {@link Player#getMediaMetadata()} 的值发生变化时调用。
     *
     * <p>此方法可能会在短时间内多次调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param mediaMetadata 组合的 {@link MediaMetadata}。
     */
    default void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
    }

    /**
     * 当 {@link Player#getPlaylistMetadata()} 的值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     */
    default void onPlaylistMetadataChanged(MediaMetadata mediaMetadata) {
    }

    /**
     * 当播放器开始或停止加载源时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param isLoading 当前是否正在加载源。
     */
    default void onIsLoadingChanged(boolean isLoading) {
    }

    /**
     * @deprecated 请使用 {@link #onIsLoadingChanged(boolean)} 代替。
     */
    @Deprecated
    @UnstableApi
    default void onLoadingChanged(boolean isLoading) {
    }

    /**
     * 当至少一个 {@link Command} 的 {@link #isCommandAvailable(int)} 返回值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param availableCommands 可用的 {@link Commands}。
     */
    default void onAvailableCommandsChanged(Commands availableCommands) {
    }

    /**
     * 当 {@link #getTrackSelectionParameters()} 的返回值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param parameters 新的 {@link TrackSelectionParameters}。
     */
    default void onTrackSelectionParametersChanged(TrackSelectionParameters parameters) {
    }

    /**
     * @deprecated 请使用 {@link #onPlaybackStateChanged(int)} 和 {@link #onPlayWhenReadyChanged(boolean, int)} 代替。
     */
    @Deprecated
    @UnstableApi
    default void onPlayerStateChanged(boolean playWhenReady, @State int playbackState) {
    }

    /**
     * 当 {@link #getPlaybackState()} 的返回值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param playbackState 新的播放 {@link State}。
     */
    default void onPlaybackStateChanged(@State int playbackState) {
    }

    /**
     * 当 {@link #getPlayWhenReady()} 的返回值发生变化时调用。
     *
     * <p>如果 {@code reason} 发生变化，当前的 {@code playWhenReady} 值可能会被重新报告。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param playWhenReady 是否在准备好时继续播放。
     * @param reason        变化的 {@link PlayWhenReadyChangeReason}。
     */
    default void onPlayWhenReadyChanged(
        boolean playWhenReady, @PlayWhenReadyChangeReason int reason) {
    }

    /**
     * 当 {@link #getPlaybackSuppressionReason()} 的返回值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param playbackSuppressionReason 当前的 {@link PlaybackSuppressionReason}。
     */
    default void onPlaybackSuppressionReasonChanged(
        @PlaybackSuppressionReason int playbackSuppressionReason) {
    }

    /**
     * 当 {@link #isPlaying()} 的值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param isPlaying 播放器是否正在播放。
     */
    default void onIsPlayingChanged(boolean isPlaying) {
    }

    /**
     * 当 {@link #getRepeatMode()} 的值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param repeatMode 用于播放的 {@link RepeatMode}。
     */
    default void onRepeatModeChanged(@RepeatMode int repeatMode) {
    }

    /**
     * 当 {@link #getShuffleModeEnabled()} 的值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param shuffleModeEnabled 是否启用了 {@linkplain MediaItem 媒体项} 的随机播放。
     */
    default void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
    }

    /**
     * 当发生错误时调用。播放状态将在调用此方法后立即转换为 {@link #STATE_IDLE}。播放器实例仍可使用，如果不再需要，仍必须调用 {@link #release()}。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * <p>播放器的实现可能会将 {@link PlaybackException} 的子类实例传递给此方法，以包含有关错误的更多信息。
     *
     * @param error 错误。
     */
    default void onPlayerError(PlaybackException error) {
    }

    /**
     * 当 {@link #getPlayerError()} 返回的 {@link PlaybackException} 发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * <p>播放器的实现可能会将 {@link PlaybackException} 的子类实例传递给此方法，以包含有关错误的更多信息。
     *
     * @param error 新的错误，如果错误被清除则为 null。
     */
    default void onPlayerErrorChanged(@Nullable PlaybackException error) {
    }

    /**
     * @deprecated 请使用 {@link #onPositionDiscontinuity(PositionInfo, PositionInfo, int)} 代替。
     */
    @Deprecated
    @UnstableApi
    default void onPositionDiscontinuity(@DiscontinuityReason int reason) {
    }

    /**
     * 当发生位置不连续时调用。
     *
     * <p>位置不连续发生在播放的周期发生变化、播放位置在当前播放的周期内跳跃，或者当前播放的周期被跳过或移除时。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param oldPosition 不连续之前的位置。
     * @param newPosition 不连续之后的位置。
     * @param reason      导致不连续的 {@link DiscontinuityReason}。
     */
    default void onPositionDiscontinuity(
        PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
    }

    /**
     * 当 {@link #getPlaybackParameters()} 的值发生变化时调用。播放参数可能会由于调用 {@link #setPlaybackParameters(PlaybackParameters)} 而发生变化，或者播放器本身可能会更改它们（例如，如果音频播放切换到直通或卸载模式，速度调整将不再可能）。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param playbackParameters 播放参数。
     */
    default void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    }

    /**
     * 当 {@link #getSeekBackIncrement()} 的值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param seekBackIncrementMs {@link #seekBack()} 的增量，以毫秒为单位。
     */
    default void onSeekBackIncrementChanged(long seekBackIncrementMs) {
    }

    /**
     * 当 {@link #getSeekForwardIncrement()} 的值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param seekForwardIncrementMs {@link #seekForward()} 的增量，以毫秒为单位。
     */
    default void onSeekForwardIncrementChanged(long seekForwardIncrementMs) {
    }

    /**
     * 当 {@link #getMaxSeekToPreviousPosition()} 的值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param maxSeekToPreviousPositionMs {@link #seekToPrevious()} 的最大位置，以毫秒为单位。
     */
    default void onMaxSeekToPreviousPositionChanged(long maxSeekToPreviousPositionMs) {
    }

    /**
     * 当音频会话 ID 发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param audioSessionId 音频会话 ID。
     */
    @UnstableApi
    default void onAudioSessionIdChanged(int audioSessionId) {
    }

    /**
     * 当 {@link #getAudioAttributes()} 的值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param audioAttributes 音频属性。
     */
    default void onAudioAttributesChanged(AudioAttributes audioAttributes) {
    }

    /**
     * 当 {@link #getVolume()} 的值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param volume 新的音量，0 表示静音，1 表示单位增益。
     */
    default void onVolumeChanged(float volume) {
    }

    /**
     * 当音频流中跳过静音功能启用或禁用时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param skipSilenceEnabled 是否启用了音频流中的跳过静音功能。
     */
    default void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
    }

    /**
     * 当设备信息发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param deviceInfo 新的 {@link DeviceInfo}。
     */
    default void onDeviceInfoChanged(DeviceInfo deviceInfo) {
    }

    /**
     * 当 {@link #getDeviceVolume()} 或 {@link #isDeviceMuted()} 的值发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param volume 新的设备音量，0 表示静音，1 表示单位增益。
     * @param muted  设备是否静音。
     */
    default void onDeviceVolumeChanged(int volume, boolean muted) {
    }

    /**
     * 每次 {@link Player#getVideoSize()} 发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param videoSize 视频的新尺寸。
     */
    default void onVideoSizeChanged(VideoSize videoSize) {
    }

    /**
     * 每次渲染视频的表面的尺寸发生变化时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param width  表面的宽度，以像素为单位。如果未知，可能是 {@link C#LENGTH_UNSET}；如果视频未渲染到表面上，则可能是 0。
     * @param height 表面的高度，以像素为单位。如果未知，可能是 {@link C#LENGTH_UNSET}；如果视频未渲染到表面上，则可能是 0。
     */
    default void onSurfaceSizeChanged(int width, int height) {
    }

    /**
     * 当自设置表面以来、自渲染器重置以来或自渲染的流更改以来首次渲染帧时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     */
    default void onRenderedFirstFrame() {
    }

    /**
     * 当 {@link #getCurrentCues()} 的值发生变化时调用。
     *
     * <p>当字幕发生变化时，此方法和 {@link #onCues(CueGroup)} 都会被调用。你只需实现其中一个即可。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @deprecated 请使用 {@link #onCues(CueGroup)} 代替。
     */
    @Deprecated
    @UnstableApi
    default void onCues(List<Cue> cues) {
    }

    /**
     * 当 {@link #getCurrentCues()} 的值发生变化时调用。
     *
     * <p>当字幕发生变化时，此方法和 {@link #onCues(List)} 都会被调用。你只需实现其中一个即可。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     */
    default void onCues(CueGroup cueGroup) {
    }

    /**
     * 当与当前播放时间相关的元数据存在时调用。
     *
     * <p>{@link #onEvents(Player, Events)} 也会被调用，以在同一 {@link Looper} 消息队列迭代中报告此事件以及其他事件。
     *
     * @param metadata 元数据。
     */
    @UnstableApi
    default void onMetadata(Metadata metadata) {
    }
  }

  /**
   * 播放状态。取值为 {@link #STATE_IDLE}、{@link #STATE_BUFFERING}、{@link #STATE_READY} 或 {@link #STATE_ENDED} 之一。
   */
// @Target 列表包括 'default' 目标和 TYPE_USE，以确保与在添加 TYPE_USE 之前的 Kotlin 用法兼容。
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({STATE_IDLE, STATE_BUFFERING, STATE_READY, STATE_ENDED})
  @interface State {
  }

  /**
   * 播放器处于空闲状态，意味着它仅持有有限的资源。播放器必须 {@link #prepare() 准备} 后才能播放媒体。
   */
  int STATE_IDLE = 1;

  /**
   * 播放器无法立即播放媒体，但正在为此进行工作。此状态通常发生在播放器需要缓冲更多数据才能开始播放时。
   */
  int STATE_BUFFERING = 2;

  /**
   * 播放器能够立即从其当前位置播放。如果 {@link #getPlayWhenReady()} 为 true，则播放器将播放，否则将暂停。
   */
  int STATE_READY = 3;

  /**
   * 播放器已完成播放媒体。
   */
  int STATE_ENDED = 4;

  /**
   * {@link #getPlayWhenReady() playWhenReady} 变化的原因。取值为 {@link #PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST}、{@link #PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS}、{@link #PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY}、{@link #PLAY_WHEN_READY_CHANGE_REASON_REMOTE}、{@link #PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM} 或 {@link #PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG} 之一。
   */
// @Target 列表包括 'default' 目标和 TYPE_USE，以确保与在添加 TYPE_USE 之前的 Kotlin 用法兼容。
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
      PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
      PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
      PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY,
      PLAY_WHEN_READY_CHANGE_REASON_REMOTE,
      PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM,
      PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG
  })
  @interface PlayWhenReadyChangeReason {
  }

  /**
   * 播放已通过调用 {@link #setPlayWhenReady(boolean)} 开始或暂停。
   */
  int PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST = 1;

  /**
   * 播放已因音频焦点丢失而暂停。
   */
  int PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS = 2;

  /**
   * 播放已为避免变得嘈杂而暂停。
   */
  int PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY = 3;

  /**
   * 播放已因远程更改而开始或暂停。
   */
  int PLAY_WHEN_READY_CHANGE_REASON_REMOTE = 4;

  /**
   * 播放已在媒体项结束时暂停。
   */
  int PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM = 5;

  /**
   * 播放已因 {@linkplain #getPlaybackSuppressionReason() 被抑制} 时间过长而暂停。
   */
  int PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG = 6;

  /**
   * 即使 {@link #getPlayWhenReady()} 为 {@code true}，播放仍被抑制的原因。取值为 {@link #PLAYBACK_SUPPRESSION_REASON_NONE}、{@link #PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS}、{@link #PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_ROUTE} 或 {@link #PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT} 之一。
   */
// @Target 列表包括 'default' 目标和 TYPE_USE，以确保与在添加 TYPE_USE 之前的 Kotlin 用法兼容。
  @SuppressWarnings("deprecation") // 包括已弃用的命令
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
      PLAYBACK_SUPPRESSION_REASON_NONE,
      PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
      PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_ROUTE,
      PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT
  })
  @interface PlaybackSuppressionReason {
  }

  /**
   * 播放未被抑制。
   */
  int PLAYBACK_SUPPRESSION_REASON_NONE = 0;

  /**
   * 播放因短暂的音频焦点丢失而被抑制。
   */
  int PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS = 1;

  /**
   * @deprecated 请使用 {@link #PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT} 代替。
   */
  @Deprecated
  int PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_ROUTE = 2;

  /**
   * 播放因尝试在不适合的音频输出上播放而被抑制（例如，尝试在 Wear OS 设备的内置扬声器上播放）。
   */
  int PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT = 3;

  /**
   * 播放的重复模式。取值为 {@link #REPEAT_MODE_OFF}、{@link #REPEAT_MODE_ONE} 或 {@link #REPEAT_MODE_ALL} 之一。
   */
// @Target 列表包括 'default' 目标和 TYPE_USE，以确保与在添加 TYPE_USE 之前的 Kotlin 用法兼容。
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({REPEAT_MODE_OFF, REPEAT_MODE_ONE, REPEAT_MODE_ALL})
  @interface RepeatMode {
  }

  /**
   * 正常播放，无重复。“上一首”和“下一首”操作分别移动到上一首和下一首 {@link MediaItem}，如果没有上一首或下一首 {@link MediaItem} 可移动，则不执行任何操作。
   */
  int REPEAT_MODE_OFF = 0;

  /**
   * 在持续播放期间无限重复当前正在播放的 {@link MediaItem}。“上一首”和“下一首”操作的行为与 {@link #REPEAT_MODE_OFF} 中相同，分别移动到上一首和下一首 {@link MediaItem}，如果没有上一首或下一首 {@link MediaItem} 可移动，则不执行任何操作。
   */
  int REPEAT_MODE_ONE = 1;

  /**
   * 无限重复整个时间线。“上一首”和“下一首”操作的行为与 {@link #REPEAT_MODE_OFF} 中相同，但在末尾循环，因此当播放第一个 {@link MediaItem} 时，“上一首”将移动到最后一个 {@link MediaItem}，当播放最后一个 {@link MediaItem} 时，“下一首”将移动到第一个 {@link MediaItem}。
   */
  int REPEAT_MODE_ALL = 2;

  /**
   * 位置不连续的原因。取值为 {@link #DISCONTINUITY_REASON_AUTO_TRANSITION}、{@link #DISCONTINUITY_REASON_SEEK}、{@link #DISCONTINUITY_REASON_SEEK_ADJUSTMENT}、{@link #DISCONTINUITY_REASON_SKIP}、{@link #DISCONTINUITY_REASON_REMOVE} 或 {@link #DISCONTINUITY_REASON_INTERNAL} 之一。
   */
// @Target 列表包括 'default' 目标和 TYPE_USE，以确保与在添加 TYPE_USE 之前的 Kotlin 用法兼容。
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
      DISCONTINUITY_REASON_AUTO_TRANSITION,
      DISCONTINUITY_REASON_SEEK,
      DISCONTINUITY_REASON_SEEK_ADJUSTMENT,
      DISCONTINUITY_REASON_SKIP,
      DISCONTINUITY_REASON_REMOVE,
      DISCONTINUITY_REASON_INTERNAL,
      DISCONTINUITY_REASON_SILENCE_SKIP
  })
  @interface DiscontinuityReason {
  }

  /**
   * 从时间轴中的一个时间段自动过渡到下一个时间段。如果当前时间段重复，则时间段索引可能与不连续性之前相同。
   *
   * <p>此原因还表示从内容时间段自动过渡到插入的广告时间段，或反之。或者由另一个播放器引起的过渡（例如，多个控制器可以控制远程设备上的同一播放）。
   */
  int DISCONTINUITY_REASON_AUTO_TRANSITION = 0;

  /**
   * 在当前时间段内或到另一个时间段内的跳转。
   */
  int DISCONTINUITY_REASON_SEEK = 1;

  /**
   * 由于无法跳转到请求的位置或因为跳转被允许不精确而进行的跳转调整。
   */
  int DISCONTINUITY_REASON_SEEK_ADJUSTMENT = 2;

  /**
   * 由于跳过一个时间段（例如跳过的广告）引入的不连续性。
   */
  int DISCONTINUITY_REASON_SKIP = 3;

  /**
   * 由于从 {@link Timeline} 中移除当前时间段引起的不连续性。
   */
  int DISCONTINUITY_REASON_REMOVE = 4;

  /**
   * 内部引入的不连续性（例如由源引起）。
   */
  int DISCONTINUITY_REASON_INTERNAL = 5;

  /**
   * 由于跳过静音引入的不连续性。
   */
  int DISCONTINUITY_REASON_SILENCE_SKIP = 6;

  /**
   * 时间轴变化的原因。取 {@link #TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED} 或 {@link #TIMELINE_CHANGE_REASON_SOURCE_UPDATE} 之一。
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED, TIMELINE_CHANGE_REASON_SOURCE_UPDATE})
  @interface TimelineChangeReason {
  }

  /**
   * 由于播放列表项或项的顺序发生变化而导致的时间轴变化。
   */
  int TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED = 0;

  /**
   * 由于源更新（例如播放媒体的动态更新结果）而导致的时间轴变化。
   *
   * <p>此原因还表示由另一个播放器引起的变化（例如，多个控制器可以控制远程设备上的同一播放）。
   */
  int TIMELINE_CHANGE_REASON_SOURCE_UPDATE = 1;

  /**
   * 媒体项过渡的原因。取 {@link #MEDIA_ITEM_TRANSITION_REASON_REPEAT}、{@link #MEDIA_ITEM_TRANSITION_REASON_AUTO}、{@link #MEDIA_ITEM_TRANSITION_REASON_SEEK} 或 {@link #MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED} 之一。
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
      MEDIA_ITEM_TRANSITION_REASON_REPEAT,
      MEDIA_ITEM_TRANSITION_REASON_AUTO,
      MEDIA_ITEM_TRANSITION_REASON_SEEK,
      MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
  })
  @interface MediaItemTransitionReason {
  }

  /**
   * 媒体项已被重复。
   */
  int MEDIA_ITEM_TRANSITION_REASON_REPEAT = 0;

  /**
   * 播放已自动过渡到下一个媒体项。
   *
   * <p>此原因还表示由另一个播放器引起的过渡（例如，多个控制器可以控制远程设备上的同一播放）。
   */
  int MEDIA_ITEM_TRANSITION_REASON_AUTO = 1;

  /**
   * 发生了跳转到另一个媒体项的操作。
   */
  int MEDIA_ITEM_TRANSITION_REASON_SEEK = 2;

  /**
   * 当前媒体项由于播放列表的变化而发生变化。这可能是由于之前正在播放的媒体项被移除，或者播放列表从空变为非空。
   */
  int MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED = 3;

  /**
   * 可以通过 {@link Listener#onEvents(Player, Events)} 报告的事件。
   *
   * <p>One of the {@link Player}{@code .EVENT_*} values.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
      EVENT_TIMELINE_CHANGED,
      EVENT_MEDIA_ITEM_TRANSITION,
      EVENT_TRACKS_CHANGED,
      EVENT_IS_LOADING_CHANGED,
      EVENT_PLAYBACK_STATE_CHANGED,
      EVENT_PLAY_WHEN_READY_CHANGED,
      EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
      EVENT_IS_PLAYING_CHANGED,
      EVENT_REPEAT_MODE_CHANGED,
      EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
      EVENT_PLAYER_ERROR,
      EVENT_POSITION_DISCONTINUITY,
      EVENT_PLAYBACK_PARAMETERS_CHANGED,
      EVENT_AVAILABLE_COMMANDS_CHANGED,
      EVENT_MEDIA_METADATA_CHANGED,
      EVENT_PLAYLIST_METADATA_CHANGED,
      EVENT_SEEK_BACK_INCREMENT_CHANGED,
      EVENT_SEEK_FORWARD_INCREMENT_CHANGED,
      EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED,
      EVENT_TRACK_SELECTION_PARAMETERS_CHANGED,
      EVENT_AUDIO_ATTRIBUTES_CHANGED,
      EVENT_AUDIO_SESSION_ID,
      EVENT_VOLUME_CHANGED,
      EVENT_SKIP_SILENCE_ENABLED_CHANGED,
      EVENT_SURFACE_SIZE_CHANGED,
      EVENT_VIDEO_SIZE_CHANGED,
      EVENT_RENDERED_FIRST_FRAME,
      EVENT_CUES,
      EVENT_METADATA,
      EVENT_DEVICE_INFO_CHANGED,
      EVENT_DEVICE_VOLUME_CHANGED
  })
  @interface Event {

  }

  /**
   * {@link #getCurrentTimeline()} 发生变化。
   */
  int EVENT_TIMELINE_CHANGED = 0;

  /**
   * {@link #getCurrentMediaItem()} 发生变化，或者播放器开始重复当前项。
   */
  int EVENT_MEDIA_ITEM_TRANSITION = 1;

  /**
   * {@link #getCurrentTracks()} 发生变化。
   */
  int EVENT_TRACKS_CHANGED = 2;

  /**
   * {@link #isLoading()} 发生变化。
   */
  int EVENT_IS_LOADING_CHANGED = 3;

  /**
   * {@link #getPlaybackState()} 发生变化。
   */
  int EVENT_PLAYBACK_STATE_CHANGED = 4;

  /**
   * {@link #getPlayWhenReady()} 发生变化。
   */
  int EVENT_PLAY_WHEN_READY_CHANGED = 5;

  /**
   * {@link #getPlaybackSuppressionReason()} 发生变化。
   */
  int EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED = 6;

  /**
   * {@link #isPlaying()} 发生变化。
   */
  int EVENT_IS_PLAYING_CHANGED = 7;

  /**
   * {@link #getRepeatMode()} 发生变化。
   */
  int EVENT_REPEAT_MODE_CHANGED = 8;

  /**
   * {@link #getShuffleModeEnabled()} 发生变化。
   */
  int EVENT_SHUFFLE_MODE_ENABLED_CHANGED = 9;

  /**
   * {@link #getPlayerError()} 发生变化。
   */
  int EVENT_PLAYER_ERROR = 10;

  /**
   * 发生了位置不连续性。参见 {@link Listener#onPositionDiscontinuity(PositionInfo, PositionInfo, int)}。
   */
  int EVENT_POSITION_DISCONTINUITY = 11;

  /**
   * {@link #getPlaybackParameters()} 发生变化。
   */
  int EVENT_PLAYBACK_PARAMETERS_CHANGED = 12;

  /**
   * {@link #isCommandAvailable(int)} 对至少一个 {@link Command} 发生变化。
   */
  int EVENT_AVAILABLE_COMMANDS_CHANGED = 13;

  /**
   * {@link #getMediaMetadata()} 发生变化。
   */
  int EVENT_MEDIA_METADATA_CHANGED = 14;

  /**
   * {@link #getPlaylistMetadata()} 发生变化。
   */
  int EVENT_PLAYLIST_METADATA_CHANGED = 15;

  /**
   * {@link #getSeekBackIncrement()} 发生变化。
   */
  int EVENT_SEEK_BACK_INCREMENT_CHANGED = 16;

  /**
   * {@link #getSeekForwardIncrement()} 发生变化。
   */
  int EVENT_SEEK_FORWARD_INCREMENT_CHANGED = 17;

  /**
   * {@link #getMaxSeekToPreviousPosition()} 发生变化。
   */
  int EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED = 18;

  /**
   * {@link #getTrackSelectionParameters()} 发生变化。
   */
  int EVENT_TRACK_SELECTION_PARAMETERS_CHANGED = 19;

  /**
   * {@link #getAudioAttributes()} 发生变化。
   */
  int EVENT_AUDIO_ATTRIBUTES_CHANGED = 20;

  /**
   * 音频会话 ID 被设置。
   */
  int EVENT_AUDIO_SESSION_ID = 21;

  /**
   * {@link #getVolume()} 发生变化。
   */
  int EVENT_VOLUME_CHANGED = 22;

  /**
   * 音频流中的静音跳过功能被启用或禁用。
   */
  int EVENT_SKIP_SILENCE_ENABLED_CHANGED = 23;

  /**
   * 渲染视频的表面的尺寸发生变化。
   */
  int EVENT_SURFACE_SIZE_CHANGED = 24;

  /**
   * {@link #getVideoSize()} 发生变化。
   */
  int EVENT_VIDEO_SIZE_CHANGED = 25;

  /**
   * 自设置表面、渲染器重置或渲染的流发生变化以来，首次渲染了一帧。
   */
  int EVENT_RENDERED_FIRST_FRAME = 26;

  /**
   * {@link #getCurrentCues()} 发生变化。
   */
  int EVENT_CUES = 27;

  /**
   * 与当前播放时间相关的元数据发生变化。
   */
  int EVENT_METADATA = 28;

  /**
   * {@link #getDeviceInfo()} 发生变化。
   */
  int EVENT_DEVICE_INFO_CHANGED = 29;

  /**
   * {@link #getDeviceVolume()} 发生变化。
   */
  int EVENT_DEVICE_VOLUME_CHANGED = 30;

  /**
   * 指示在特定 {@code Player} 实例上当前允许调用哪些方法的命令。
   *
   * <p>可以通过 {@link #getAvailableCommands()} 和 {@link #isCommandAvailable(int)} 检查当前可用的命令。
   *
   * <p>请参阅每个命令常量的文档，了解它允许调用哪些方法的具体细节。
   *
   * <p>取以下值之一：
   * <ul>
   *   <li>{@link #COMMAND_PLAY_PAUSE}
   *   <li>{@link #COMMAND_PREPARE}
   *   <li>{@link #COMMAND_STOP}
   *   <li>{@link #COMMAND_SEEK_TO_DEFAULT_POSITION}
   *   <li>{@link #COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM}
   *   <li>{@link #COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM}
   *   <li>{@link #COMMAND_SEEK_TO_PREVIOUS}
   *   <li>{@link #COMMAND_SEEK_TO_NEXT_MEDIA_ITEM}
   *   <li>{@link #COMMAND_SEEK_TO_NEXT}
   *   <li>{@link #COMMAND_SEEK_TO_MEDIA_ITEM}
   *   <li>{@link #COMMAND_SEEK_BACK}
   *   <li>{@link #COMMAND_SEEK_FORWARD}
   *   <li>{@link #COMMAND_SET_SPEED_AND_PITCH}
   *   <li>{@link #COMMAND_SET_SHUFFLE_MODE}
   *   <li>{@link #COMMAND_SET_REPEAT_MODE}
   *   <li>{@link #COMMAND_GET_CURRENT_MEDIA_ITEM}
   *   <li>{@link #COMMAND_GET_TIMELINE}
   *   <li>{@link #COMMAND_GET_METADATA}
   *   <li>{@link #COMMAND_SET_PLAYLIST_METADATA}
   *   <li>{@link #COMMAND_SET_MEDIA_ITEM}
   *   <li>{@link #COMMAND_CHANGE_MEDIA_ITEMS}
   *   <li>{@link #COMMAND_GET_AUDIO_ATTRIBUTES}
   *   <li>{@link #COMMAND_GET_VOLUME}
   *   <li>{@link #COMMAND_GET_DEVICE_VOLUME}
   *   <li>{@link #COMMAND_SET_VOLUME}
   *   <li>{@link #COMMAND_SET_DEVICE_VOLUME}
   *   <li>{@link #COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS}
   *   <li>{@link #COMMAND_ADJUST_DEVICE_VOLUME}
   *   <li>{@link #COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS}
   *   <li>{@link #COMMAND_SET_AUDIO_ATTRIBUTES}
   *   <li>{@link #COMMAND_SET_VIDEO_SURFACE}
   *   <li>{@link #COMMAND_GET_TEXT}
   *   <li>{@link #COMMAND_SET_TRACK_SELECTION_PARAMETERS}
   *   <li>{@link #COMMAND_GET_TRACKS}
   *   <li>{@link #COMMAND_RELEASE}
   * </ul>
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @SuppressWarnings("deprecation") // Listing deprecated constants.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
      COMMAND_INVALID,
      COMMAND_PLAY_PAUSE,
      COMMAND_PREPARE,
      COMMAND_STOP,
      COMMAND_SEEK_TO_DEFAULT_POSITION,
      COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
      COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
      COMMAND_SEEK_TO_PREVIOUS,
      COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
      COMMAND_SEEK_TO_NEXT,
      COMMAND_SEEK_TO_MEDIA_ITEM,
      COMMAND_SEEK_BACK,
      COMMAND_SEEK_FORWARD,
      COMMAND_SET_SPEED_AND_PITCH,
      COMMAND_SET_SHUFFLE_MODE,
      COMMAND_SET_REPEAT_MODE,
      COMMAND_GET_CURRENT_MEDIA_ITEM,
      COMMAND_GET_TIMELINE,
      COMMAND_GET_MEDIA_ITEMS_METADATA,
      COMMAND_GET_METADATA,
      COMMAND_SET_MEDIA_ITEMS_METADATA,
      COMMAND_SET_PLAYLIST_METADATA,
      COMMAND_SET_MEDIA_ITEM,
      COMMAND_CHANGE_MEDIA_ITEMS,
      COMMAND_GET_AUDIO_ATTRIBUTES,
      COMMAND_GET_VOLUME,
      COMMAND_GET_DEVICE_VOLUME,
      COMMAND_SET_VOLUME,
      COMMAND_SET_DEVICE_VOLUME,
      COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
      COMMAND_ADJUST_DEVICE_VOLUME,
      COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
      COMMAND_SET_AUDIO_ATTRIBUTES,
      COMMAND_SET_VIDEO_SURFACE,
      COMMAND_GET_TEXT,
      COMMAND_SET_TRACK_SELECTION_PARAMETERS,
      COMMAND_GET_TRACKS,
      COMMAND_RELEASE,
  })
  @interface Command {

  }

  /**
   * 用于启动、暂停或恢复播放的命令。
   *
   * <p>以下方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用：
   *
   * <ul>
   *   <li>{@link #play()}
   *   <li>{@link #pause()}
   *   <li>{@link #setPlayWhenReady(boolean)}
   * </ul>
   */
  int COMMAND_PLAY_PAUSE = 1;

  /**
   * 用于准备播放器的命令。
   *
   * <p>{@link #prepare()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_PREPARE = 2;

  /**
   * 用于停止播放的命令。
   *
   * <p>{@link #stop()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_STOP = 3;

  /**
   * 用于跳转到当前 {@link MediaItem} 默认位置的命令。
   *
   * <p>{@link #seekToDefaultPosition()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SEEK_TO_DEFAULT_POSITION = 4;

  /**
   * 用于在当前 {@link MediaItem} 内任意位置跳转的命令。
   *
   * <p>{@link #seekTo(long)} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM = 5;

  /**
   * @deprecated 请使用 {@link #COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM} 代替。
   */
  @UnstableApi
  @Deprecated
  int COMMAND_SEEK_IN_CURRENT_WINDOW = COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;

  /**
   * 用于跳转到上一个 {@link MediaItem} 默认位置的命令。
   *
   * <p>{@link #seekToPreviousMediaItem()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM = 6;

  /**
   * @deprecated 请使用 {@link #COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM} 代替。
   */
  @UnstableApi
  @Deprecated
  int COMMAND_SEEK_TO_PREVIOUS_WINDOW = COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;

  /**
   * 用于跳转到当前 {@link MediaItem} 的较早位置或上一个 {@link MediaItem} 默认位置的命令。
   *
   * <p>{@link #seekToPrevious()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SEEK_TO_PREVIOUS = 7;

  /**
   * 用于跳转到下一个 {@link MediaItem} 默认位置的命令。
   *
   * <p>{@link #seekToNextMediaItem()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SEEK_TO_NEXT_MEDIA_ITEM = 8;

  /**
   * @deprecated 请使用 {@link #COMMAND_SEEK_TO_NEXT_MEDIA_ITEM} 代替。
   */
  @UnstableApi
  @Deprecated
  int COMMAND_SEEK_TO_NEXT_WINDOW = COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;

  /**
   * 用于跳转到当前 {@link MediaItem} 的较晚位置或下一个 {@link MediaItem} 默认位置的命令。
   *
   * <p>{@link #seekToNext()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SEEK_TO_NEXT = 9;

  /**
   * 用于在任何 {@link MediaItem} 中任意位置跳转的命令。
   *
   * <p>以下方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用：
   *
   * <ul>
   *   <li>{@link #seekTo(int, long)}
   *   <li>{@link #seekToDefaultPosition(int)}
   * </ul>
   */
  int COMMAND_SEEK_TO_MEDIA_ITEM = 10;

  /**
   * @deprecated 请使用 {@link #COMMAND_SEEK_TO_MEDIA_ITEM} 代替。
   */
  @UnstableApi
  @Deprecated
  int COMMAND_SEEK_TO_WINDOW = COMMAND_SEEK_TO_MEDIA_ITEM;

  /**
   * 用于在当前 {@link MediaItem} 内向后跳转固定增量的命令。
   *
   * <p>{@link #seekBack()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SEEK_BACK = 11;

  /**
   * 用于在当前 {@link MediaItem} 内向前跳转固定增量的命令。
   *
   * <p>{@link #seekForward()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SEEK_FORWARD = 12;

  /**
   * 用于设置播放速度和音调的命令。
   *
   * <p>以下方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用：
   *
   * <ul>
   *   <li>{@link #setPlaybackParameters(PlaybackParameters)}
   *   <li>{@link #setPlaybackSpeed(float)}
   * </ul>
   */
  int COMMAND_SET_SPEED_AND_PITCH = 13;

  /**
   * 用于启用随机播放的命令。
   *
   * <p>{@link #setShuffleModeEnabled(boolean)} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SET_SHUFFLE_MODE = 14;

  /**
   * 用于设置重复模式的命令。
   *
   * <p>{@link #setRepeatMode(int)} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SET_REPEAT_MODE = 15;

  /**
   * 用于获取当前播放的 {@link MediaItem} 信息的命令。
   *
   * <p>以下方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用：
   *
   * <ul>
   *   <li>{@link #getCurrentMediaItem()}
   *   <li>{@link #isCurrentMediaItemDynamic()}
   *   <li>{@link #isCurrentMediaItemLive()}
   *   <li>{@link #isCurrentMediaItemSeekable()}
   *   <li>{@link #getCurrentLiveOffset()}
   *   <li>{@link #getDuration()}
   *   <li>{@link #getCurrentPosition()}
   *   <li>{@link #getBufferedPosition()}
   *   <li>{@link #getContentDuration()}
   *   <li>{@link #getContentPosition()}
   *   <li>{@link #getContentBufferedPosition()}
   *   <li>{@link #getTotalBufferedDuration()}
   *   <li>{@link #isPlayingAd()}
   *   <li>{@link #getCurrentAdGroupIndex()}
   *   <li>{@link #getCurrentAdIndexInAdGroup()}
   * </ul>
   */
  int COMMAND_GET_CURRENT_MEDIA_ITEM = 16;

  /**
   * 用于获取当前时间轴信息的命令。
   *
   * <p>以下方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用：
   *
   * <ul>
   *   <li>{@link #getCurrentTimeline()}
   *   <li>{@link #getCurrentMediaItemIndex()}
   *   <li>{@link #getCurrentPeriodIndex()}
   *   <li>{@link #getMediaItemCount()}
   *   <li>{@link #getMediaItemAt(int)}
   *   <li>{@link #getNextMediaItemIndex()}
   *   <li>{@link #getPreviousMediaItemIndex()}
   *   <li>{@link #hasPreviousMediaItem()}
   *   <li>{@link #hasNextMediaItem()}
   * </ul>
   */
  int COMMAND_GET_TIMELINE = 17;

  /**
   * @deprecated 请使用 {@link #COMMAND_GET_METADATA} 代替。
   */
  @Deprecated
  int COMMAND_GET_MEDIA_ITEMS_METADATA = 18;

  /**
   * 用于获取播放列表和当前 {@link MediaItem} 相关元数据的命令。
   *
   * <p>以下方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用：
   *
   * <ul>
   *   <li>{@link #getMediaMetadata()}
   *   <li>{@link #getPlaylistMetadata()}
   * </ul>
   */
  int COMMAND_GET_METADATA = 18;

  /**
   * @deprecated 请使用 {@link #COMMAND_SET_PLAYLIST_METADATA} 代替。
   */
  @Deprecated
  int COMMAND_SET_MEDIA_ITEMS_METADATA = 19;

  /**
   * 用于设置播放列表元数据的命令。
   *
   * <p>{@link #setPlaylistMetadata(MediaMetadata)} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SET_PLAYLIST_METADATA = 19;

  /**
   * 用于设置 {@link MediaItem} 的命令。
   *
   * <p>以下方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用：
   *
   * <ul>
   *   <li>{@link #setMediaItem(MediaItem)}
   *   <li>{@link #setMediaItem(MediaItem, boolean)}
   *   <li>{@link #setMediaItem(MediaItem, long)}
   * </ul>
   */
  int COMMAND_SET_MEDIA_ITEM = 31;

  /**
   * 用于更改播放列表中 {@linkplain MediaItem 媒体项} 的命令。
   *
   * <p>以下方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用：
   *
   * <ul>
   *   <li>{@link #addMediaItem(MediaItem)}
   *   <li>{@link #addMediaItem(int, MediaItem)}
   *   <li>{@link #addMediaItems(List)}
   *   <li>{@link #addMediaItems(int, List)}
   *   <li>{@link #clearMediaItems()}
   *   <li>{@link #moveMediaItem(int, int)}
   *   <li>{@link #moveMediaItems(int, int, int)}
   *   <li>{@link #removeMediaItem(int)}
   *   <li>{@link #removeMediaItems(int, int)}
   *   <li>{@link #setMediaItems(List)}
   *   <li>{@link #setMediaItems(List, boolean)}
   *   <li>{@link #setMediaItems(List, int, long)}
   *   <li>{@link #replaceMediaItem(int, MediaItem)}
   *   <li>{@link #replaceMediaItems(int, int, List)}
   * </ul>
   */
  int COMMAND_CHANGE_MEDIA_ITEMS = 20;

  /**
   * 用于获取播放器当前 {@link AudioAttributes} 的命令。
   *
   * <p>{@link #getAudioAttributes()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_GET_AUDIO_ATTRIBUTES = 21;

  /**
   * 用于获取播放器音量的命令。
   *
   * <p>{@link #getVolume()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_GET_VOLUME = 22;

  /**
   * 用于获取设备音量及是否静音的命令。
   *
   * <p>以下方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用：
   *
   * <ul>
   *   <li>{@link #getDeviceVolume()}
   *   <li>{@link #isDeviceMuted()}
   * </ul>
   */
  int COMMAND_GET_DEVICE_VOLUME = 23;

  /**
   * 用于设置播放器音量的命令。
   *
   * <p>{@link #setVolume(float)} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SET_VOLUME = 24;

  /**
   * @deprecated 请使用 {@link #COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS} 代替。
   */
  @Deprecated
  int COMMAND_SET_DEVICE_VOLUME = 25;

  /**
   * 用于设置设备音量及音量标志的命令。
   *
   * <p>{@link #setDeviceVolume(int, int)} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS = 33;

  /**
   * @deprecated 请使用 {@link #COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS} 代替。
   */
  @Deprecated
  int COMMAND_ADJUST_DEVICE_VOLUME = 26;

  /**
   * 用于增加、减少设备音量及静音操作的命令。
   *
   * <p>以下方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用：
   *
   * <ul>
   *   <li>{@link #increaseDeviceVolume(int)}
   *   <li>{@link #decreaseDeviceVolume(int)}
   *   <li>{@link #setDeviceMuted(boolean, int)}
   * </ul>
   */
  int COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS = 34;

  /**
   * 用于设置播放器音频属性的命令。
   *
   * <p>{@link #setAudioAttributes(AudioAttributes, boolean)} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SET_AUDIO_ATTRIBUTES = 35;

  /**
   * 用于设置和清除视频渲染表面的命令。
   *
   * <p>以下方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用：
   *
   * <ul>
   *   <li>{@link #setVideoSurface(Surface)}
   *   <li>{@link #clearVideoSurface()}
   *   <li>{@link #clearVideoSurface(Surface)}
   *   <li>{@link #setVideoSurfaceHolder(SurfaceHolder)}
   *   <li>{@link #clearVideoSurfaceHolder(SurfaceHolder)}
   *   <li>{@link #setVideoSurfaceView(SurfaceView)}
   *   <li>{@link #clearVideoSurfaceView(SurfaceView)}
   * </ul>
   */
  int COMMAND_SET_VIDEO_SURFACE = 27;

  /**
   * 用于获取当前应显示的文本的命令。
   *
   * <p>{@link #getCurrentCues()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_GET_TEXT = 28;

  /**
   * 用于设置播放器轨道选择参数的命令。
   *
   * <p>{@link #setTrackSelectionParameters(TrackSelectionParameters)} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_SET_TRACK_SELECTION_PARAMETERS = 29;

  /**
   * 用于获取当前轨道选择详情的命令。
   *
   * <p>{@link #getCurrentTracks()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_GET_TRACKS = 30;

  /**
   * 用于释放播放器的命令。
   *
   * <p>{@link #release()} 方法必须仅在此命令 {@linkplain #isCommandAvailable(int) 可用} 时调用。
   */
  int COMMAND_RELEASE = 32;

  /**
   * 表示无效的 {@link Command}。
   */
  int COMMAND_INVALID = -1;

  /**
   * 返回与用于访问播放器并接收播放器事件的应用线程关联的 {@link Looper}。
   *
   * <p>此方法可以从任何线程调用。
   */
  Looper getApplicationLooper();

  /**
   * 注册一个监听器以接收来自播放器的所有事件。
   *
   * <p>监听器的方法将在与 {@link #getApplicationLooper()} 关联的线程上调用。
   *
   * <p>此方法可以从任何线程调用。
   *
   * @param listener 要注册的监听器。
   */
  void addListener(Listener listener);

  /**
   * 注销通过 {@link #addListener(Listener)} 注册的监听器。监听器将不再接收事件。
   *
   * @param listener 要注销的监听器。
   */
  void removeListener(Listener listener);

  /**
   * 清除播放列表，添加指定的 {@linkplain MediaItem 媒体项}，并将位置重置为默认位置。
   *
   * <p>要替换播放列表中的一部分媒体项（可能无缝地）而不清除播放列表，请使用 {@link #replaceMediaItems}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param mediaItems 新的 {@linkplain MediaItem 媒体项}。
   */
  void setMediaItems(List<MediaItem> mediaItems);

  /**
   * 清除播放列表并添加指定的 {@linkplain MediaItem 媒体项}。
   *
   * <p>要替换播放列表中的一部分媒体项（可能无缝地）而不清除播放列表，请使用 {@link #replaceMediaItems}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param mediaItems    新的 {@linkplain MediaItem 媒体项}。
   * @param resetPosition 是否将播放位置重置为第一个 {@link Timeline.Window} 中的默认位置。如果为 false，播放将从 {@link #getCurrentMediaItemIndex()} 和 {@link #getCurrentPosition()} 定义的位置开始。
   */
  void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition);

  /**
   * 清除播放列表并添加指定的 {@linkplain MediaItem 媒体项}。
   *
   * <p>要替换播放列表中的一部分媒体项（可能无缝地）而不清除播放列表，请使用 {@link #replaceMediaItems}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param mediaItems      新的 {@linkplain MediaItem 媒体项}。
   * @param startIndex      开始播放的 {@link MediaItem} 索引。如果传递 {@link C#INDEX_UNSET}，则不会重置当前位置。
   * @param startPositionMs 开始播放的位置（以毫秒为单位）。如果传递 {@link C#TIME_UNSET}，则使用给定 {@link MediaItem} 的默认位置。无论如何，如果 {@code startIndex} 设置为 {@link C#INDEX_UNSET}，则忽略此参数，并且不会重置位置。
   * @throws IllegalSeekPositionException 如果提供的 {@code startIndex} 不在媒体项列表的范围内。
   */
  void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs);

  /**
   * 清除播放列表，添加指定的 {@link MediaItem}，并将位置重置为默认位置。
   *
   * <p>要替换播放列表中的一个媒体项（可能无缝地）而不清除播放列表，请使用 {@link #replaceMediaItem}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param mediaItem 新的 {@link MediaItem}。
   */
  void setMediaItem(MediaItem mediaItem);

  /**
   * 清除播放列表并添加指定的 {@link MediaItem}。
   *
   * <p>要替换播放列表中的一个媒体项（可能无缝地）而不清除播放列表，请使用 {@link #replaceMediaItem}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param mediaItem       新的 {@link MediaItem}。
   * @param startPositionMs 开始播放的位置（以毫秒为单位）。如果传递 {@link C#TIME_UNSET}，则使用给定 {@link MediaItem} 的默认位置。
   */
  void setMediaItem(MediaItem mediaItem, long startPositionMs);

  /**
   * 清除播放列表并添加指定的 {@link MediaItem}。
   *
   * <p>要替换播放列表中的一个媒体项（可能无缝地）而不清除播放列表，请使用 {@link #replaceMediaItem}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param mediaItem     新的 {@link MediaItem}。
   * @param resetPosition 是否将播放位置重置为默认位置。如果为 false，播放将从 {@link #getCurrentMediaItemIndex()} 和 {@link #getCurrentPosition()} 定义的位置开始。
   */
  void setMediaItem(MediaItem mediaItem, boolean resetPosition);

  /**
   * 将一个媒体项添加到播放列表的末尾。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param mediaItem 要添加的 {@link MediaItem}。
   */
  void addMediaItem(MediaItem mediaItem);

  /**
   * 将一个媒体项添加到播放列表的指定索引处。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param index     要添加媒体项的索引。如果索引大于播放列表的大小，则将媒体项添加到播放列表的末尾。
   * @param mediaItem 要添加的 {@link MediaItem}。
   */
  void addMediaItem(int index, MediaItem mediaItem);

  /**
   * 将一组媒体项添加到播放列表的末尾。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param mediaItems 要添加的 {@linkplain MediaItem 媒体项}。
   */
  void addMediaItems(List<MediaItem> mediaItems);

  /**
   * 将一组媒体项添加到播放列表的指定索引处。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param index      要添加媒体项的索引。如果索引大于播放列表的大小，则将媒体项添加到播放列表的末尾。
   * @param mediaItems 要添加的 {@linkplain MediaItem 媒体项}。
   */
  void addMediaItems(int index, List<MediaItem> mediaItems);

  /**
   * 将播放列表中指定索引处的媒体项移动到新索引处。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param currentIndex 要移动的媒体项的当前索引。如果索引大于播放列表的大小，则忽略该请求。
   * @param newIndex     媒体项的新索引。如果新索引大于播放列表的大小，则将媒体项移动到播放列表的末尾。
   */
  void moveMediaItem(int currentIndex, int newIndex);

  /**
   * 将播放列表中指定范围的媒体项移动到新索引处。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param fromIndex 要移动范围的起始索引。如果索引大于播放列表的大小，则忽略该请求。
   * @param toIndex   要移动范围的结束索引（不包含）。如果索引大于播放列表的大小，则移动范围到播放列表的末尾。
   * @param newIndex  范围中第一个媒体项的新索引。如果新索引大于移除范围后剩余播放列表的大小，则将范围移动到播放列表的末尾。
   */
  void moveMediaItems(int fromIndex, int toIndex, int newIndex);

  /**
   * 替换播放列表中指定索引处的媒体项。
   *
   * <p>此方法的实现可能会尝试无缝继续播放，如果当前正在播放的媒体项被替换为兼容的媒体项（例如相同的 URL，仅元数据发生变化）。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param index     要替换媒体项的索引。如果索引大于播放列表的大小，则忽略该请求。
   * @param mediaItem 新的 {@link MediaItem}。
   */
  void replaceMediaItem(int index, MediaItem mediaItem);

  /**
   * 替换播放列表中指定范围内的媒体项。
   *
   * <p>此方法的实现可能会尝试无缝继续播放，如果当前正在播放的媒体项被替换为兼容的媒体项（例如相同的 URL，仅元数据发生变化）。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * <p>请注意，可以用任意数量的新项替换范围，因此 {@code fromIndex} 和 {@code toIndex} 定义的移除项数量不必与 {@code mediaItems} 定义的添加项数量匹配。因此，它也可能更改未受此操作影响的后续项的索引。
   *
   * @param fromIndex  范围的起始索引。如果索引大于播放列表的大小，则忽略该请求。
   * @param toIndex    范围结束索引（不包含）。如果索引大于播放列表的大小，则替换范围到播放列表的末尾。
   * @param mediaItems 用于替换范围的 {@linkplain MediaItem 媒体项}。
   */
  void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems);

  /**
   * 移除播放列表中指定索引处的媒体项。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param index 要移除媒体项的索引。如果索引大于播放列表的大小，则忽略该请求。
   */
  void removeMediaItem(int index);

  /**
   * 移除播放列表中指定范围内的媒体项。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param fromIndex 开始移除媒体项的索引。如果索引大于播放列表的大小，则忽略该请求。
   * @param toIndex   要保留的第一个项的索引（不包含）。如果索引大于播放列表的大小，则移除范围到播放列表的末尾。
   */
  void removeMediaItems(int fromIndex, int toIndex);

  /**
   * 清除播放列表。
   *
   * <p>此方法必须仅在 {@link #COMMAND_CHANGE_MEDIA_ITEMS} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void clearMediaItems();

  /**
   * 返回指定的 {@link Command} 是否可用。
   *
   * <p>此方法不会执行命令。
   *
   * @param command 一个 {@link Command}。
   * @return 该 {@link Command} 是否可用。
   * @see Listener#onAvailableCommandsChanged(Commands)
   */
  boolean isCommandAvailable(@Command int command);

  /**
   * 返回播放器是否可用于广告媒体会话。
   */
  boolean canAdvertiseSession();

  /**
   * 返回播放器当前可用的 {@link Commands}。
   *
   * <p>返回的 {@link Commands} 不会在可用命令发生变化时更新。使用 {@link Listener#onAvailableCommandsChanged(Commands)} 以在可用命令发生变化时获取更新。
   *
   * @return 当前可用的 {@link Commands}。
   * @see Listener#onAvailableCommandsChanged(Commands)
   */
  Commands getAvailableCommands();

  /**
   * 准备播放器。
   *
   * <p>此方法必须仅在 {@link #COMMAND_PREPARE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * <p>这将使播放器从 {@link #STATE_IDLE 空闲状态} 中移出，播放器将开始加载媒体并获取播放所需的资源。
   */
  void prepare();

  /**
   * 返回播放器当前的 {@linkplain State 播放状态}。
   *
   * @return 当前的 {@linkplain State 播放状态}。
   * @see Listener#onPlaybackStateChanged(int)
   */
  @State
  int getPlaybackState();

  /**
   * 返回即使 {@link #getPlayWhenReady()} 为 {@code true} 时播放仍被抑制的原因，如果播放未被抑制，则返回 {@link #PLAYBACK_SUPPRESSION_REASON_NONE}。
   *
   * @return 当前的 {@link PlaybackSuppressionReason}。
   * @see Listener#onPlaybackSuppressionReasonChanged(int)
   */
  @PlaybackSuppressionReason
  int getPlaybackSuppressionReason();

  /**
   * 返回播放器是否正在播放，即 {@link #getCurrentPosition()} 是否在前进。
   *
   * <p>如果为 {@code false}，则以下至少一项为 true：
   *
   * <ul>
   *   <li>{@link #getPlaybackState() 播放状态} 不是 {@link #STATE_READY 就绪状态}。
   *   <li>没有 {@link #getPlayWhenReady() 播放意图}。
   *   <li>播放被 {@link #getPlaybackSuppressionReason() 其他原因抑制}。
   * </ul>
   *
   * @return 播放器是否正在播放。
   * @see Listener#onIsPlayingChanged(boolean)
   */
  boolean isPlaying();

  /**
   * 返回导致播放失败的错误。这是播放失败时通过 {@link Listener#onPlayerError(PlaybackException)} 报告的错误。在播放器重新准备之前，可以通过此方法查询该错误。
   *
   * <p>请注意，如果 {@link #getPlaybackState()} 不是 {@link #STATE_IDLE}，则此方法始终返回 {@code null}。
   *
   * @return 错误，或 {@code null}。
   * @see Listener#onPlayerError(PlaybackException)
   */
  @Nullable
  PlaybackException getPlayerError();

  /**
   * 当 {@link #getPlaybackState()} == {@link #STATE_READY} 时恢复播放。等同于 {@link #setPlayWhenReady(boolean) setPlayWhenReady(true)}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_PLAY_PAUSE} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void play();

  /**
   * 暂停播放。等同于 {@link #setPlayWhenReady(boolean) setPlayWhenReady(false)}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_PLAY_PAUSE} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void pause();

  /**
   * 设置当 {@link #getPlaybackState()} == {@link #STATE_READY} 时是否应继续播放。
   *
   * <p>如果播放器已处于就绪状态，则此方法将暂停并恢复播放。
   *
   * <p>此方法必须仅在 {@link #COMMAND_PLAY_PAUSE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param playWhenReady 当播放器就绪时是否应继续播放。
   */
  void setPlayWhenReady(boolean playWhenReady);

  /**
   * 返回当 {@link #getPlaybackState()} == {@link #STATE_READY} 时是否应继续播放。
   *
   * @return 当播放器就绪时是否应继续播放。
   * @see Listener#onPlayWhenReadyChanged(boolean, int)
   */
  boolean getPlayWhenReady();

  /**
   * 设置用于播放的 {@link RepeatMode}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_REPEAT_MODE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param repeatMode 重复模式。
   */
  void setRepeatMode(@RepeatMode int repeatMode);

  /**
   * 返回当前用于播放的 {@link RepeatMode}。
   *
   * @return 当前的重复模式。
   * @see Listener#onRepeatModeChanged(int)
   */
  @RepeatMode
  int getRepeatMode();

  /**
   * 设置是否启用媒体项的随机播放。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_SHUFFLE_MODE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param shuffleModeEnabled 是否启用随机播放。
   */
  void setShuffleModeEnabled(boolean shuffleModeEnabled);

  /**
   * 返回是否启用了媒体项的随机播放。
   *
   * @see Listener#onShuffleModeEnabledChanged(boolean)
   */
  boolean getShuffleModeEnabled();

  /**
   * 播放器是否正在加载资源。
   *
   * @return 播放器是否正在加载资源。
   * @see Listener#onIsLoadingChanged(boolean)
   */
  boolean isLoading();

  /**
   * 跳转到与当前 {@link MediaItem} 关联的默认位置。该位置可能取决于正在播放的媒体类型。对于直播流，它通常是直播边缘。对于其他流，它通常是起始位置。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SEEK_TO_DEFAULT_POSITION} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void seekToDefaultPosition();

  /**
   * 跳转到与指定 {@link MediaItem} 关联的默认位置。该位置可能取决于正在播放的媒体类型。对于直播流，它通常是直播边缘。对于其他流，它通常是起始位置。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SEEK_TO_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param mediaItemIndex 要跳转的 {@link MediaItem} 的索引。如果索引大于播放列表的大小，则忽略该请求。
   */
  void seekToDefaultPosition(int mediaItemIndex);

  /**
   * 跳转到当前 {@link MediaItem} 中指定的位置（以毫秒为单位）。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param positionMs 当前 {@link MediaItem} 中的跳转位置，或 {@link C#TIME_UNSET} 以跳转到媒体项的默认位置。
   */
  void seekTo(long positionMs);

  /**
   * 跳转到指定 {@link MediaItem} 中指定的位置（以毫秒为单位）。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SEEK_TO_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param mediaItemIndex {@link MediaItem} 的索引。如果索引大于播放列表的大小，则忽略该请求。
   * @param positionMs     指定 {@link MediaItem} 中的跳转位置，或 {@link C#TIME_UNSET} 以跳转到媒体项的默认位置。
   */
  void seekTo(int mediaItemIndex, long positionMs);

  /**
   * 返回 {@link #seekBack()} 的增量值。
   *
   * @return 向后跳转的增量值（以毫秒为单位）。
   * @see Listener#onSeekBackIncrementChanged(long)
   */
  long getSeekBackIncrement();

  /**
   * 在当前 {@link MediaItem} 中向后跳转 {@link #getSeekBackIncrement()} 毫秒。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SEEK_BACK} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void seekBack();

  /**
   * 返回 {@link #seekForward()} 的增量值。
   *
   * @return 向前跳转的增量值（以毫秒为单位）。
   * @see Listener#onSeekForwardIncrementChanged(long)
   */
  long getSeekForwardIncrement();

  /**
   * 在当前 {@link MediaItem} 中向前跳转 {@link #getSeekForwardIncrement()} 毫秒。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SEEK_FORWARD} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void seekForward();

  /**
   * 返回是否存在上一个媒体项，这可能取决于当前的重复模式和是否启用了随机播放。
   *
   * <p>注意：当重复模式为 {@link #REPEAT_MODE_ONE} 时，此方法的行为与重复模式为 {@link #REPEAT_MODE_OFF} 时相同。有关更多详细信息，请参阅 {@link #REPEAT_MODE_ONE}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_TIMELINE} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  boolean hasPreviousMediaItem();

  /**
   * @deprecated 请使用 {@link #seekToPreviousMediaItem()} 代替。
   */
  @UnstableApi
  @Deprecated
  void seekToPreviousWindow();

  /**
   * 跳转到上一个 {@link MediaItem} 的默认位置，这可能取决于当前的重复模式和是否启用了随机播放。如果 {@link #hasPreviousMediaItem()} 为 {@code false}，则不执行任何操作。
   *
   * <p>注意：当重复模式为 {@link #REPEAT_MODE_ONE} 时，此方法的行为与重复模式为 {@link #REPEAT_MODE_OFF} 时相同。有关更多详细信息，请参阅 {@link #REPEAT_MODE_ONE}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void seekToPreviousMediaItem();

  /**
   * 返回 {@link #seekToPrevious()} 跳转到上一个 {@link MediaItem} 的最大位置（以毫秒为单位）。
   *
   * @return 跳转到上一个媒体项的最大位置（以毫秒为单位）。
   * @see Listener#onMaxSeekToPreviousPositionChanged(long)
   */
  long getMaxSeekToPreviousPosition();

  /**
   * 跳转到当前或上一个 {@link MediaItem} 的较早位置（如果存在）。具体行为如下：
   *
   * <ul>
   *   <li>如果时间轴为空或无法跳转，则不执行任何操作。
   *   <li>否则，如果当前 {@link MediaItem} 是 {@linkplain #isCurrentMediaItemLive() 直播} 且 {@linkplain #isCurrentMediaItemSeekable() 不可跳转}，则：
   *       <ul>
   *         <li>如果 {@linkplain #hasPreviousMediaItem() 存在上一个媒体项}，则跳转到上一个媒体项的默认位置。
   *         <li>否则，不执行任何操作。
   *       </ul>
   *   <li>否则，如果 {@linkplain #hasPreviousMediaItem() 存在上一个媒体项} 且 {@linkplain #getCurrentPosition() 当前位置} 小于 {@link #getMaxSeekToPreviousPosition()}，则跳转到上一个 {@link MediaItem} 的默认位置。
   *   <li>否则，跳转到当前 {@link MediaItem} 的起始位置（0）。
   * </ul>
   *
   * <p>此方法必须仅在 {@link #COMMAND_SEEK_TO_PREVIOUS} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void seekToPrevious();

  /**
   * @deprecated 请使用 {@link #hasNextMediaItem()} 代替。
   */
  @UnstableApi
  @Deprecated
  boolean hasNext();

  /**
   * @deprecated 请使用 {@link #hasNextMediaItem()} 代替。
   */
  @UnstableApi
  @Deprecated
  boolean hasNextWindow();

  /**
   * 返回是否存在下一个 {@link MediaItem}，这可能取决于当前的重复模式和是否启用了随机播放。
   *
   * <p>注意：当重复模式为 {@link #REPEAT_MODE_ONE} 时，此方法的行为与重复模式为 {@link #REPEAT_MODE_OFF} 时相同。有关更多详细信息，请参阅 {@link #REPEAT_MODE_ONE}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_TIMELINE} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  boolean hasNextMediaItem();

  /**
   * @deprecated 请使用 {@link #seekToNextMediaItem()} 代替。
   */
  @UnstableApi
  @Deprecated
  void next();

  /**
   * @deprecated 请使用 {@link #seekToNextMediaItem()} 代替。
   */
  @UnstableApi
  @Deprecated
  void seekToNextWindow();

  /**
   * 跳转到下一个 {@link MediaItem} 的默认位置，这可能取决于当前的重复模式和是否启用了随机播放。如果 {@link #hasNextMediaItem()} 为 {@code false}，则不执行任何操作。
   *
   * <p>注意：当重复模式为 {@link #REPEAT_MODE_ONE} 时，此方法的行为与重复模式为 {@link #REPEAT_MODE_OFF} 时相同。有关更多详细信息，请参阅 {@link #REPEAT_MODE_ONE}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SEEK_TO_NEXT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void seekToNextMediaItem();

  /**
   * 跳转到当前或下一个 {@link MediaItem} 的较晚位置（如果存在）。具体行为如下：
   *
   * <ul>
   *   <li>如果时间轴为空或无法跳转，则不执行任何操作。
   *   <li>否则，如果 {@linkplain #hasNextMediaItem() 存在下一个媒体项}，则跳转到下一个 {@link MediaItem} 的默认位置。
   *   <li>否则，如果当前 {@link MediaItem} 是 {@linkplain #isCurrentMediaItemLive() 直播} 且未结束，则跳转到当前 {@link MediaItem} 的直播边缘。
   *   <li>否则，不执行任何操作。
   * </ul>
   *
   * <p>此方法必须仅在 {@link #COMMAND_SEEK_TO_NEXT} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void seekToNext();

  /**
   * 尝试设置播放参数。传递 {@link PlaybackParameters#DEFAULT} 会将播放器重置为默认值，即没有速度或音调调整。
   *
   * <p>播放参数的变化可能会导致播放器缓冲。每当当前活动的播放参数发生变化时，将调用 {@link Listener#onPlaybackParametersChanged(PlaybackParameters)}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_SPEED_AND_PITCH} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param playbackParameters 播放参数。
   */
  void setPlaybackParameters(PlaybackParameters playbackParameters);

  /**
   * 更改播放速率。音调不会改变。
   *
   * <p>这等同于 {@code setPlaybackParameters(getPlaybackParameters().withSpeed(speed))}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_SPEED_AND_PITCH} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param speed 播放速度的线性因子。必须大于 0。1 是正常速度，2 是两倍速度，0.5 是半速。
   */
  void setPlaybackSpeed(@FloatRange(from = 0, fromInclusive = false) float speed);

  /**
   * 返回当前活动的播放参数。
   *
   * @see Listener#onPlaybackParametersChanged(PlaybackParameters)
   */
  PlaybackParameters getPlaybackParameters();

  /**
   * 停止播放而不重置播放列表。如果意图是暂停播放，请使用 {@link #pause()} 而不是此方法。
   *
   * <p>调用此方法将导致播放状态转换为 {@link #STATE_IDLE}，并且播放器将释放已加载的媒体和播放所需的资源。可以通过再次调用 {@link #prepare()} 来继续使用播放器实例，如果不再需要播放器，则必须调用 {@link #release()}。
   *
   * <p>调用此方法不会清除播放列表、重置播放位置或播放错误。
   *
   * <p>此方法必须仅在 {@link #COMMAND_STOP} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void stop();

  /**
   * 释放播放器。当不再需要播放器时，必须调用此方法。调用此方法后，不得再使用播放器。
   *
   * <p>此方法必须仅在 {@link #COMMAND_RELEASE} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void release();

  /**
   * 返回当前轨道。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_TRACKS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @see Listener#onTracksChanged(Tracks)
   */
  Tracks getCurrentTracks();

  /**
   * 返回约束轨道选择的参数。
   *
   * @see Listener#onTrackSelectionParametersChanged}
   */
  TrackSelectionParameters getTrackSelectionParameters();

// LINT.IfChange(set_track_selection_parameters)

  /**
   * 设置约束轨道选择的参数。
   *
   * <p>不支持的参数将被静默忽略。
   *
   * <p>使用 {@link #getTrackSelectionParameters()} 检索当前参数。例如，以下代码片段将视频限制为标清，同时保持其他轨道选择参数不变：
   *
   * <pre>{@code
   * player.setTrackSelectionParameters(
   *   player.getTrackSelectionParameters()
   *         .buildUpon()
   *         .setMaxVideoSizeSd()
   *         .build())
   * }</pre>
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_TRACK_SELECTION_PARAMETERS} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void setTrackSelectionParameters(TrackSelectionParameters parameters);

  /**
   * 返回当前组合的 {@link MediaMetadata}，如果不支持则返回 {@link MediaMetadata#EMPTY}。
   *
   * <p>此 {@link MediaMetadata} 是 {@link MediaItem#mediaMetadata MediaItem 元数据}、媒体 {@link Format#metadata 格式} 中的静态元数据以及从媒体解析并通过 {@link Listener#onMetadata(Metadata)} 输出的任何定时元数据的组合。如果 {@link MediaItem#mediaMetadata} 中填充了某个字段，则该字段将优先于来自静态或定时元数据的相同字段。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_METADATA} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @see Listener#onMediaMetadataChanged(MediaMetadata)
   */
  MediaMetadata getMediaMetadata();

  /**
   * 返回播放列表的 {@link MediaMetadata}，由 {@link #setPlaylistMetadata(MediaMetadata)} 设置，如果不支持则返回 {@link MediaMetadata#EMPTY}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_METADATA} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @see Listener#onPlaylistMetadataChanged(MediaMetadata)
   */
  MediaMetadata getPlaylistMetadata();

  /**
   * 设置播放列表的 {@link MediaMetadata}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_PLAYLIST_METADATA} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void setPlaylistMetadata(MediaMetadata mediaMetadata);

  /**
   * 返回当前的清单（manifest）。类型取决于正在播放的媒体类型。可能为 null。
   */
  @UnstableApi
  @Nullable
  Object getCurrentManifest();

  /**
   * 返回当前的 {@link Timeline}。永远不会为 null，但可能为空。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_TIMELINE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @see Listener#onTimelineChanged(Timeline, int)
   */
  Timeline getCurrentTimeline();

  /**
   * 返回当前正在播放的时间段（period）的索引。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_TIMELINE} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  int getCurrentPeriodIndex();

  /**
   * @deprecated 请使用 {@link #getCurrentMediaItemIndex()} 代替。
   */
  @UnstableApi
  @Deprecated
  int getCurrentWindowIndex();

  /**
   * 返回当前 {@link MediaItem} 在 {@link #getCurrentTimeline() 时间轴} 中的索引，如果 {@link #getCurrentTimeline() 当前时间轴} 为空，则返回预期索引。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_TIMELINE} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  int getCurrentMediaItemIndex();

  /**
   * @deprecated 请使用 {@link #getNextMediaItemIndex()} 代替。
   */
  @UnstableApi
  @Deprecated
  int getNextWindowIndex();

  /**
   * 返回调用 {@link #seekToNextMediaItem()} 时将播放的 {@link MediaItem} 的索引，这可能取决于当前的重复模式和是否启用了随机播放。如果 {@link #hasNextMediaItem()} 为 {@code false}，则返回 {@link C#INDEX_UNSET}。
   *
   * <p>注意：当重复模式为 {@link #REPEAT_MODE_ONE} 时，此方法的行为与重复模式为 {@link #REPEAT_MODE_OFF} 时相同。有关更多详细信息，请参阅 {@link #REPEAT_MODE_ONE}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_TIMELINE} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  int getNextMediaItemIndex();

  /**
   * @deprecated 请使用 {@link #getPreviousMediaItemIndex()} 代替。
   */
  @UnstableApi
  @Deprecated
  int getPreviousWindowIndex();

  /**
   * 返回调用 {@link #seekToPreviousMediaItem()} 时将播放的 {@link MediaItem} 的索引，这可能取决于当前的重复模式和是否启用了随机播放。如果 {@link #hasPreviousMediaItem()} 为 {@code false}，则返回 {@link C#INDEX_UNSET}。
   *
   * <p>注意：当重复模式为 {@link #REPEAT_MODE_ONE} 时，此方法的行为与重复模式为 {@link #REPEAT_MODE_OFF} 时相同。有关更多详细信息，请参阅 {@link #REPEAT_MODE_ONE}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_TIMELINE} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  int getPreviousMediaItemIndex();

  /**
   * 返回当前正在播放的 {@link MediaItem}。如果时间轴为空，则可能为 null。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @see Listener#onMediaItemTransition(MediaItem, int)
   */
  @Nullable
  MediaItem getCurrentMediaItem();

  /**
   * 返回播放列表中 {@linkplain MediaItem 媒体项} 的数量。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_TIMELINE} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  int getMediaItemCount();

  /**
   * 返回指定索引处的 {@link MediaItem}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_TIMELINE} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  MediaItem getMediaItemAt(int index);

  /**
   * 返回当前内容或广告的持续时间（以毫秒为单位），如果持续时间未知，则返回 {@link C#TIME_UNSET}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  long getDuration();

  /**
   * 返回当前内容或广告中的播放位置（以毫秒为单位），如果 {@link #getCurrentTimeline() 当前时间轴} 为空，则返回预期位置。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  long getCurrentPosition();

  /**
   * 返回当前内容或广告中已缓冲数据的估计位置（以毫秒为单位）。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  long getBufferedPosition();

  /**
   * 返回当前内容或广告中已缓冲数据的估计百分比，如果无法估计，则返回 0。
   */
  @IntRange(from = 0, to = 100)
  int getBufferedPercentage();

  /**
   * 返回从当前位置开始的总缓冲持续时间（以毫秒为单位）。这包括后续广告和 {@linkplain MediaItem 媒体项} 的预缓冲数据。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  long getTotalBufferedDuration();

  /**
   * @deprecated 请使用 {@link #isCurrentMediaItemDynamic()} 代替。
   */
  @UnstableApi
  @Deprecated
  boolean isCurrentWindowDynamic();

  /**
   * 返回当前 {@link MediaItem} 是否是动态的（可能在 {@link Timeline} 更新时发生变化），如果 {@link Timeline} 为空，则返回 {@code false}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @see Timeline.Window#isDynamic
   */
  boolean isCurrentMediaItemDynamic();

  /**
   * @deprecated 请使用 {@link #isCurrentMediaItemLive()} 代替。
   */
  @UnstableApi
  @Deprecated
  boolean isCurrentWindowLive();

  /**
   * 返回当前 {@link MediaItem} 是否是直播，如果 {@link Timeline} 为空，则返回 {@code false}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @see Timeline.Window#isLive()
   */
  boolean isCurrentMediaItemLive();

  /**
   * 返回当前播放位置与直播边缘的偏移量（以毫秒为单位），如果当前 {@link MediaItem} {@linkplain #isCurrentMediaItemLive() 不是直播} 或偏移量未知，则返回 {@link C#TIME_UNSET}。
   *
   * <p>偏移量计算为 {@code currentTime - playbackPosition}，因此通常应为正数。
   *
   * <p>请注意，此偏移量可能依赖于准确的本地时间，因此如果系统时钟和服务器时钟之间的差异未知，则此方法可能返回错误的值。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  long getCurrentLiveOffset();

  /**
   * @deprecated 请使用 {@link #isCurrentMediaItemSeekable()} 代替。
   */
  @UnstableApi
  @Deprecated
  boolean isCurrentWindowSeekable();

  /**
   * 返回当前 {@link MediaItem} 是否可跳转，如果 {@link Timeline} 为空，则返回 {@code false}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @see Timeline.Window#isSeekable
   */
  boolean isCurrentMediaItemSeekable();

  /**
   * 返回播放器当前是否正在播放广告。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  boolean isPlayingAd();

  /**
   * 如果 {@link #isPlayingAd()} 返回 true，则返回当前正在播放的时间段中的广告组索引。否则返回 {@link C#INDEX_UNSET}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  int getCurrentAdGroupIndex();

  /**
   * 如果 {@link #isPlayingAd()} 返回 true，则返回广告在其广告组中的索引。否则返回 {@link C#INDEX_UNSET}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  int getCurrentAdIndexInAdGroup();

  /**
   * 如果 {@link #isPlayingAd()} 返回 {@code true}，则返回当前内容的持续时间（以毫秒为单位），如果持续时间未知，则返回 {@link C#TIME_UNSET}。如果没有广告正在播放，则返回的持续时间与 {@link #getDuration()} 返回的值相同。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  long getContentDuration();

  /**
   * 如果 {@link #isPlayingAd()} 返回 {@code true}，则返回广告组中的所有广告播放完毕后将播放的内容位置（以毫秒为单位）。如果没有广告正在播放，则返回的位置与 {@link #getCurrentPosition()} 返回的值相同。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  long getContentPosition();

  /**
   * 如果 {@link #isPlayingAd()} 返回 {@code true}，则返回当前内容中已缓冲数据的估计位置（以毫秒为单位）。如果没有广告正在播放，则返回的位置与 {@link #getBufferedPosition()} 返回的值相同。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  long getContentBufferedPosition();

  /**
   * 返回音频播放的属性。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_AUDIO_ATTRIBUTES} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @see Listener#onAudioAttributesChanged(AudioAttributes)
   */
  AudioAttributes getAudioAttributes();

  /**
   * 设置音频音量，有效值介于 0（静音）和 1（单位增益，信号不变）之间，包括两端值。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_VOLUME} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param volume 应用于所有音频通道的线性输出增益。
   */
  void setVolume(@FloatRange(from = 0, to = 1.0) float volume);

  /**
   * 返回音频音量，0 表示静音，1 表示单位增益（信号不变）。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_VOLUME} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @return 应用于所有音频通道的线性增益。
   * @see Listener#onVolumeChanged(float)
   */
  @FloatRange(from = 0, to = 1.0)
  float getVolume();

  /**
   * 清除当前设置在播放器上的任何 {@link Surface}、{@link SurfaceHolder}、{@link SurfaceView} 或 {@link TextureView}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_VIDEO_SURFACE} {@linkplain #getAvailableCommands() 可用} 时调用。
   */
  void clearVideoSurface();

  /**
   * 清除用于渲染视频的 {@link Surface}，如果它与传入的 {@link Surface} 匹配。否则不执行任何操作。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_VIDEO_SURFACE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param surface 要清除的 {@link Surface}。
   */
  void clearVideoSurface(@Nullable Surface surface);

  /**
   * 设置用于渲染视频的 {@link Surface}。调用者负责跟踪 {@link Surface} 的生命周期，如果 {@link Surface} 被销毁，则必须通过调用 {@code setVideoSurface(null)} 来清除它。
   *
   * <p>如果 {@link Surface} 由 {@link SurfaceView}、{@link TextureView} 或 {@link SurfaceHolder} 持有，则建议使用 {@link #setVideoSurfaceView(SurfaceView)}、{@link #setVideoTextureView(TextureView)} 或 {@link #setVideoSurfaceHolder(SurfaceHolder)} 而不是此方法，因为传递持有者允许播放器自动跟踪 {@link Surface} 的生命周期。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_VIDEO_SURFACE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param surface {@link Surface}。
   */
  void setVideoSurface(@Nullable Surface surface);

  /**
   * 设置持有 {@link Surface} 的 {@link SurfaceHolder}，视频将渲染到该表面上。播放器将自动跟踪表面的生命周期。
   *
   * <p>调用 {@link SurfaceHolder.Callback} 方法的线程必须是与 {@link #getApplicationLooper()} 关联的线程。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_VIDEO_SURFACE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param surfaceHolder 表面持有者。
   */
  void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

  /**
   * 清除持有 {@link Surface} 的 {@link SurfaceHolder}，如果它与传入的 {@link SurfaceHolder} 匹配。否则不执行任何操作。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_VIDEO_SURFACE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param surfaceHolder 要清除的表面持有者。
   */
  void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

  /**
   * 设置 {@link SurfaceView}，视频将渲染到该表面上。播放器将自动跟踪表面的生命周期。
   *
   * <p>调用 {@link SurfaceHolder.Callback} 方法的线程必须是与 {@link #getApplicationLooper()} 关联的线程。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_VIDEO_SURFACE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param surfaceView 表面视图。
   */
  void setVideoSurfaceView(@Nullable SurfaceView surfaceView);

  /**
   * 清除 {@link SurfaceView}，如果它与传入的 {@link SurfaceView} 匹配。否则不执行任何操作。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_VIDEO_SURFACE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param surfaceView 要清除的表面视图。
   */
  void clearVideoSurfaceView(@Nullable SurfaceView surfaceView);

  /**
   * 设置 {@link TextureView}，视频将渲染到该表面上。播放器将自动跟踪表面的生命周期。
   *
   * <p>考虑使用 {@link SurfaceView} 代替 {@link TextureView}，通过 {@link #setVideoSurfaceView} 设置。{@link SurfaceView} 通常会导致更低的电池消耗，并且在处理 HDR 和安全内容时表现更好。有关更多信息，请参阅 <a href="https://developer.android.com/guide/topics/media/ui/playerview#surfacetype">选择表面类型</a>。
   *
   * <p>调用 {@link TextureView.SurfaceTextureListener} 方法的线程必须是与 {@link #getApplicationLooper()} 关联的线程。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_VIDEO_SURFACE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param textureView 纹理视图。
   */
  void setVideoTextureView(@Nullable TextureView textureView);

  /**
   * 清除 {@link TextureView}，如果它与传入的 {@link TextureView} 匹配。否则不执行任何操作。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_VIDEO_SURFACE} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param textureView 要清除的纹理视图。
   */
  void clearVideoTextureView(@Nullable TextureView textureView);

  /**
   * 获取视频的尺寸。
   *
   * <p>如果 {@linkplain Tracks#isTypeSupported(int) 没有支持的视频轨道} 或其尺寸尚未确定，则视频的宽度和高度为 {@code 0}。
   *
   * @see Listener#onVideoSizeChanged(VideoSize)
   */
  VideoSize getVideoSize();

  /**
   * 获取渲染视频的表面的尺寸。
   *
   * @see Listener#onSurfaceSizeChanged(int, int)
   */
  @UnstableApi
  Size getSurfaceSize();

  /**
   * 返回当前的 {@link CueGroup}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_TEXT} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @see Listener#onCues(CueGroup)
   */
  CueGroup getCurrentCues();

  /**
   * 获取设备信息。
   */
  DeviceInfo getDeviceInfo();

  /**
   * 获取设备的当前音量。
   *
   * <p>对于具有 {@link DeviceInfo#PLAYBACK_TYPE_LOCAL 本地播放} 的设备，此方法返回的音量根据当前的 {@link C.StreamType 流类型} 而变化。流类型由 {@link AudioAttributes#usage} 决定，可以通过 {@link Util#getStreamTypeForAudioUsage(int)} 转换为流类型。
   *
   * <p>对于具有 {@link DeviceInfo#PLAYBACK_TYPE_REMOTE 远程播放} 的设备，返回远程设备的音量。
   *
   * <p>请注意，此方法返回设备的音量。要检查当前流的音量，请使用 {@link #getVolume()}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_DEVICE_VOLUME} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @see Listener#onDeviceVolumeChanged(int, boolean)
   */
  @IntRange(from = 0)
  int getDeviceVolume();

  /**
   * 获取设备是否静音。
   *
   * <p>请注意，此方法返回设备的静音状态。要检查当前流是否静音，请使用 {@code getVolume() == 0}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_GET_DEVICE_VOLUME} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @see Listener#onDeviceVolumeChanged(int, boolean)
   */
  boolean isDeviceMuted();

  /**
   * @deprecated 请使用 {@link #setDeviceVolume(int, int)} 代替。
   */
  @Deprecated
  void setDeviceVolume(@IntRange(from = 0) int volume);

  /**
   * 使用音量标志设置设备的音量。
   *
   * <p>请注意，此方法影响设备音量。要仅更改当前流的音量，请使用 {@link #setVolume}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param volume 要设置的音量。
   * @param flags  0 或一个或多个 {@link C.VolumeFlags} 的按位组合。
   */
  void setDeviceVolume(@IntRange(from = 0) int volume, @C.VolumeFlags int flags);

  /**
   * @deprecated 请使用 {@link #increaseDeviceVolume(int)} 代替。
   */
  @Deprecated
  void increaseDeviceVolume();

  /**
   * 增加设备的音量。
   *
   * <p>设备的音量（通过 {@link #getDeviceVolume()} 获取）不能超过 {@link DeviceInfo#maxVolume}（如果已定义）。
   *
   * <p>请注意，此方法影响设备音量。要仅更改当前流的音量，请使用 {@link #setVolume}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param flags 0 或一个或多个 {@link C.VolumeFlags} 的按位组合。
   */
  void increaseDeviceVolume(@C.VolumeFlags int flags);

  /**
   * @deprecated 请使用 {@link #decreaseDeviceVolume(int)} 代替。
   */
  @Deprecated
  void decreaseDeviceVolume();

  /**
   * 降低设备的音量。
   *
   * <p>设备的音量（通过 {@link #getDeviceVolume()} 获取）不能低于 {@link DeviceInfo#minVolume}。
   *
   * <p>请注意，此方法影响设备音量。要仅更改当前流的音量，请使用 {@link #setVolume}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param flags 0 或一个或多个 {@link C.VolumeFlags} 的按位组合。
   */
  void decreaseDeviceVolume(@C.VolumeFlags int flags);

  /**
   * @deprecated 请使用 {@link #setDeviceMuted(boolean, int)} 代替。
   */
  @Deprecated
  void setDeviceMuted(boolean muted);

  /**
   * 设置设备的静音状态。
   *
   * <p>请注意，此方法影响设备音量。要仅静音当前流，请使用 {@code setVolume(0)}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param muted 是否将设备设置为静音。
   * @param flags 0 或一个或多个 {@link C.VolumeFlags} 的按位组合。
   */
  void setDeviceMuted(boolean muted, @C.VolumeFlags int flags);

  /**
   * 设置音频播放的属性，供底层音频轨道使用。
   * 如果未设置，将使用默认的音频属性，这些属性适用于一般的媒体播放。
   *
   * <p>在播放期间设置音频属性可能会导致音频输出出现短暂的间隙，因为音频轨道会被重新创建。
   * 同时会生成一个新的音频会话 ID。
   *
   * <p>如果轨道选择器启用了隧道模式（tunneling），则指定的音频属性将被忽略，但如果稍后在没有隧道模式的情况下播放音频，这些属性将生效。
   *
   * <p>如果设备运行的平台 API 版本低于 21，则无法直接在底层音频轨道上设置音频属性。
   * 在这种情况下，音频用途（usage）将通过 {@link Util#getStreamTypeForAudioUsage(int)} 映射到等效的流类型。
   *
   * <p>如果需要处理音频焦点，则 {@link AudioAttributes#usage} 必须为 {@link C#USAGE_MEDIA} 或 {@link C#USAGE_GAME}。
   * 其他用途将抛出 {@link IllegalArgumentException}。
   *
   * <p>此方法必须仅在 {@link #COMMAND_SET_AUDIO_ATTRIBUTES} {@linkplain #getAvailableCommands() 可用} 时调用。
   *
   * @param audioAttributes  用于音频播放的属性。
   * @param handleAudioFocus 如果播放器应处理音频焦点，则为 true，否则为 false。
   */
  void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus);
}
