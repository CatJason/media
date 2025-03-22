package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.ImmutableIntArray;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * 用于 {@link SessionCommand} 或 {@link Player.Command} 的按钮，可以由控制器显示。
 *
 * @see MediaSession#setCustomLayout(MediaSession.ControllerInfo, List)
 * @see MediaController.Listener#onCustomLayoutChanged(MediaController, List)
 */
public final class CommandButton {

// TODO: b/328238954 - 稳定这些常量及对应的方法，并弃用不使用这些常量的方法。

  /**
   * 按钮的图标常量。必须是 {@code CommandButton.ICON_} 常量之一。
   */
  @UnstableApi
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
      ICON_UNDEFINED, // 未定义的图标
      ICON_PLAY, // 播放图标
      ICON_PAUSE, // 暂停图标
      ICON_STOP, // 停止图标
      ICON_NEXT, // 下一首图标
      ICON_PREVIOUS, // 上一首图标
      ICON_SKIP_FORWARD, // 快进图标
      ICON_SKIP_FORWARD_5, // 快进5秒图标
      ICON_SKIP_FORWARD_10, // 快进10秒图标
      ICON_SKIP_FORWARD_15, // 快进15秒图标
      ICON_SKIP_FORWARD_30, // 快进30秒图标
      ICON_SKIP_BACK, // 快退图标
      ICON_SKIP_BACK_5, // 快退5秒图标
      ICON_SKIP_BACK_10, // 快退10秒图标
      ICON_SKIP_BACK_15, // 快退15秒图标
      ICON_SKIP_BACK_30, // 快退30秒图标
      ICON_FAST_FORWARD, // 快速前进图标
      ICON_REWIND, // 快速后退图标
      ICON_REPEAT_ALL, // 全部重复图标
      ICON_REPEAT_ONE, // 单曲重复图标
      ICON_REPEAT_OFF, // 关闭重复图标
      ICON_SHUFFLE_ON, // 随机播放开启图标
      ICON_SHUFFLE_OFF, // 随机播放关闭图标
      ICON_SHUFFLE_STAR, // 随机播放星标图标
      ICON_HEART_FILLED, // 实心爱心图标
      ICON_HEART_UNFILLED, // 空心爱心图标
      ICON_STAR_FILLED, // 实心星星图标
      ICON_STAR_UNFILLED, // 空心星星图标
      ICON_BOOKMARK_FILLED, // 实心书签图标
      ICON_BOOKMARK_UNFILLED, // 空心书签图标
      ICON_THUMB_UP_FILLED, // 实心点赞图标
      ICON_THUMB_UP_UNFILLED, // 空心点赞图标
      ICON_THUMB_DOWN_FILLED, // 实心点踩图标
      ICON_THUMB_DOWN_UNFILLED, // 空心点踩图标
      ICON_FLAG_FILLED, // 实心旗帜图标
      ICON_FLAG_UNFILLED, // 空心旗帜图标
      ICON_PLUS, // 加号图标
      ICON_MINUS, // 减号图标
      ICON_PLAYLIST_ADD, // 添加到播放列表图标
      ICON_PLAYLIST_REMOVE, // 从播放列表移除图标
      ICON_QUEUE_ADD, // 添加到队列图标
      ICON_QUEUE_NEXT, // 下一首队列图标
      ICON_QUEUE_REMOVE, // 从队列移除图标
      ICON_BLOCK, // 屏蔽图标
      ICON_PLUS_CIRCLE_FILLED, // 实心加号圆圈图标
      ICON_PLUS_CIRCLE_UNFILLED, // 空心加号圆圈图标
      ICON_MINUS_CIRCLE_FILLED, // 实心减号圆圈图标
      ICON_MINUS_CIRCLE_UNFILLED, // 空心减号圆圈图标
      ICON_CHECK_CIRCLE_FILLED, // 实心对号圆圈图标
      ICON_CHECK_CIRCLE_UNFILLED, // 空心对号圆圈图标
      ICON_PLAYBACK_SPEED, // 播放速度图标
      ICON_PLAYBACK_SPEED_0_5, // 0.5倍速播放图标
      ICON_PLAYBACK_SPEED_0_8, // 0.8倍速播放图标
      ICON_PLAYBACK_SPEED_1_0, // 1.0倍速播放图标
      ICON_PLAYBACK_SPEED_1_2, // 1.2倍速播放图标
      ICON_PLAYBACK_SPEED_1_5, // 1.5倍速播放图标
      ICON_PLAYBACK_SPEED_1_8, // 1.8倍速播放图标
      ICON_PLAYBACK_SPEED_2_0, // 2.0倍速播放图标
      ICON_SETTINGS, // 设置图标
      ICON_QUALITY, // 质量图标
      ICON_SUBTITLES, // 字幕图标
      ICON_SUBTITLES_OFF, // 关闭字幕图标
      ICON_CLOSED_CAPTIONS, // 隐藏式字幕图标
      ICON_CLOSED_CAPTIONS_OFF, // 关闭隐藏式字幕图标
      ICON_SYNC, // 同步图标
      ICON_SHARE, // 分享图标
      ICON_VOLUME_UP, // 音量增加图标
      ICON_VOLUME_DOWN, // 音量减少图标
      ICON_VOLUME_OFF, // 静音图标
      ICON_ARTIST, // 艺术家图标
      ICON_ALBUM, // 专辑图标
      ICON_RADIO, // 电台图标
      ICON_SIGNAL, // 信号图标
      ICON_FEED // 订阅图标
  })
  public @interface Icon {

  }
// 注意：这些图标的常量值与 Material Design 的代码点匹配。

  /**
   * 表示未定义图标的常量，例如现有常量未涵盖的自定义图标。
   */
  @UnstableApi
  public static final int ICON_UNDEFINED = 0;

  /**
   * 显示播放符号的图标（一个向右的三角形）。
   */
  @UnstableApi
  public static final int ICON_PLAY = 0xe037;

  /**
   * 显示暂停符号的图标（两条垂直线）。
   */
  @UnstableApi
  public static final int ICON_PAUSE = 0xe034;

  /**
   * 显示停止符号的图标（一个正方形）。
   */
  @UnstableApi
  public static final int ICON_STOP = 0xe047;

  /**
   * 显示下一首符号的图标（一个向右的三角形带一条垂直线）。
   */
  @UnstableApi
  public static final int ICON_NEXT = 0xe044;

  /**
   * 显示上一首符号的图标（一个向左的三角形带一条垂直线）。
   */
  @UnstableApi
  public static final int ICON_PREVIOUS = 0xe045;

  /**
   * 显示快进符号的图标（一个顺时针的开放箭头）。
   */
  @UnstableApi
  public static final int ICON_SKIP_FORWARD = 0xf6f4;

  /**
   * 显示快进 5 秒符号的图标（一个顺时针的开放箭头带数字 5）。
   */
  @UnstableApi
  public static final int ICON_SKIP_FORWARD_5 = 0xe058;

  /**
   * 显示快进 10 秒符号的图标（一个顺时针的开放箭头带数字 10）。
   */
  @UnstableApi
  public static final int ICON_SKIP_FORWARD_10 = 0xe056;

  /**
   * 显示快进 15 秒符号的图标（一个顺时针的开放箭头带数字 15）。
   */
  @UnstableApi
  public static final int ICON_SKIP_FORWARD_15 = 0xfe056;

  /**
   * 显示快进 30 秒符号的图标（一个顺时针的开放箭头带数字 30）。
   */
  @UnstableApi
  public static final int ICON_SKIP_FORWARD_30 = 0xe057;

  /**
   * 显示快退符号的图标（一个逆时针的开放箭头）。
   */
  @UnstableApi
  public static final int ICON_SKIP_BACK = 0xe042;

  /**
   * 显示快退 5 秒符号的图标（一个逆时针的开放箭头带数字 5）。
   */
  @UnstableApi
  public static final int ICON_SKIP_BACK_5 = 0xe05b;

  /**
   * 显示快退 10 秒符号的图标（一个逆时针的开放箭头带数字 10）。
   */
  @UnstableApi
  public static final int ICON_SKIP_BACK_10 = 0xe059;

  /**
   * 显示快退 15 秒符号的图标（一个逆时针的开放箭头带数字 15）。
   */
  @UnstableApi
  public static final int ICON_SKIP_BACK_15 = 0xfe059;

  /**
   * 显示快退 30 秒符号的图标（一个逆时针的开放箭头带数字 30）。
   */
  @UnstableApi
  public static final int ICON_SKIP_BACK_30 = 0xe05a;

  /**
   * 显示快进符号的图标（两个向右的三角形）。
   */
  @UnstableApi
  public static final int ICON_FAST_FORWARD = 0xe01f;

  /**
   * 显示快退符号的图标（两个向左的三角形）。
   */
  @UnstableApi
  public static final int ICON_REWIND = 0xe020;

  /**
   * 显示全部重复符号的图标（两个顺时针的开放箭头）。
   */
  @UnstableApi
  public static final int ICON_REPEAT_ALL = 0xe040;

  /**
   * 显示单曲重复符号的图标（两个顺时针的开放箭头带数字 1）。
   */
  @UnstableApi
  public static final int ICON_REPEAT_ONE = 0xe041;

  /**
   * 显示关闭重复符号的图标（两个顺时针的开放箭头，颜色表示禁用状态）。
   */
  @UnstableApi
  public static final int ICON_REPEAT_OFF = 0xfe040;

  /**
   * 显示随机播放符号的图标（两条对角线上下箭头）。
   */
  @UnstableApi
  public static final int ICON_SHUFFLE_ON = 0xe043;

  /**
   * 显示关闭随机播放符号的图标（两条对角线上下箭头，颜色表示禁用状态）。
   */
  @UnstableApi
  public static final int ICON_SHUFFLE_OFF = 0xfe044;

  /**
   * 显示带星标的随机播放符号的图标（两条对角线上下箭头带星标）。
   */
  @UnstableApi
  public static final int ICON_SHUFFLE_STAR = 0xfe043;

  /**
   * 显示实心爱心符号的图标。
   */
  @UnstableApi
  public static final int ICON_HEART_FILLED = 0xfe87d;

  /**
   * 显示空心爱心符号的图标。
   */
  @UnstableApi
  public static final int ICON_HEART_UNFILLED = 0xe87d;

  /**
   * 显示实心星星符号的图标。
   */
  @UnstableApi
  public static final int ICON_STAR_FILLED = 0xfe838;

  /**
   * 显示空心星星符号的图标。
   */
  @UnstableApi
  public static final int ICON_STAR_UNFILLED = 0xe838;

  /**
   * 显示实心书签符号的图标。
   */
  @UnstableApi
  public static final int ICON_BOOKMARK_FILLED = 0xfe866;

  /**
   * 显示空心书签符号的图标。
   */
  @UnstableApi
  public static final int ICON_BOOKMARK_UNFILLED = 0xe866;

  /**
   * 显示实心点赞符号的图标。
   */
  @UnstableApi
  public static final int ICON_THUMB_UP_FILLED = 0xfe8dc;

  /**
   * 显示空心点赞符号的图标。
   */
  @UnstableApi
  public static final int ICON_THUMB_UP_UNFILLED = 0xe8dc;

  /**
   * 显示实心点踩符号的图标。
   */
  @UnstableApi
  public static final int ICON_THUMB_DOWN_FILLED = 0xfe8db;

  /**
   * 显示空心点踩符号的图标。
   */
  @UnstableApi
  public static final int ICON_THUMB_DOWN_UNFILLED = 0xe8db;

  /**
   * 显示实心旗帜符号的图标。
   */
  @UnstableApi
  public static final int ICON_FLAG_FILLED = 0xfe153;

  /**
   * 显示空心旗帜符号的图标。
   */
  @UnstableApi
  public static final int ICON_FLAG_UNFILLED = 0xe153;

  /**
   * 显示加号符号的图标。
   */
  @UnstableApi
  public static final int ICON_PLUS = 0xe145;

  /**
   * 显示减号符号的图标。
   */
  @UnstableApi
  public static final int ICON_MINUS = 0xe15b;

  /**
   * 显示添加到播放列表符号的图标（多条水平线带一个小加号）。
   */
  @UnstableApi
  public static final int ICON_PLAYLIST_ADD = 0xe03b;

  /**
   * 显示从播放列表移除符号的图标（多条水平线带一个小减号）。
   */
  @UnstableApi
  public static final int ICON_PLAYLIST_REMOVE = 0xeb80;

  /**
   * 显示添加到队列符号的图标（一个风格化的电视带加号）。
   */
  @UnstableApi
  public static final int ICON_QUEUE_ADD = 0xe05c;
  /**
   * 显示播放下一首队列项符号的图标（一个风格化的电视带加号和向右的箭头）。
   */
  @UnstableApi
  public static final int ICON_QUEUE_NEXT = 0xe066;

  /**
   * 显示从队列移除符号的图标（一个风格化的电视带减号）。
   */
  @UnstableApi
  public static final int ICON_QUEUE_REMOVE = 0xe067;

  /**
   * 显示屏蔽符号的图标（一个带对角线的圆圈）。
   */
  @UnstableApi
  public static final int ICON_BLOCK = 0xe14b;

  /**
   * 显示带加号的实心圆圈的图标。
   */
  @UnstableApi
  public static final int ICON_PLUS_CIRCLE_FILLED = 0xfe147;

  /**
   * 显示带加号的空心圆圈的图标。
   */
  @UnstableApi
  public static final int ICON_PLUS_CIRCLE_UNFILLED = 0xe147;

  /**
   * 显示带减号的实心圆圈的图标。
   */
  @UnstableApi
  public static final int ICON_MINUS_CIRCLE_FILLED = 0xfe148;

  /**
   * 显示带减号的空心圆圈的图标。
   */
  @UnstableApi
  public static final int ICON_MINUS_CIRCLE_UNFILLED = 0xfe149;

  /**
   * 显示带对号的实心圆圈的图标。
   */
  @UnstableApi
  public static final int ICON_CHECK_CIRCLE_FILLED = 0xfe86c;

  /**
   * 显示带对号的空心圆圈的图标。
   */
  @UnstableApi
  public static final int ICON_CHECK_CIRCLE_UNFILLED = 0xe86c;

  /**
   * 显示播放速度符号的图标（一个带半虚线、半实线轮廓的圆圈中的向右三角形）。
   */
  @UnstableApi
  public static final int ICON_PLAYBACK_SPEED = 0xe068;

  /**
   * 显示 0.5 倍速符号的图标。
   */
  @UnstableApi
  public static final int ICON_PLAYBACK_SPEED_0_5 = 0xf4e2;

  /**
   * 显示 0.8 倍速符号的图标。
   */
  @UnstableApi
  public static final int ICON_PLAYBACK_SPEED_0_8 = 0xff4e2;

  /**
   * 显示 1.0 倍速符号的图标。
   */
  @UnstableApi
  public static final int ICON_PLAYBACK_SPEED_1_0 = 0xefcd;

  /**
   * 显示 1.2 倍速符号的图标。
   */
  @UnstableApi
  public static final int ICON_PLAYBACK_SPEED_1_2 = 0xf4e1;

  /**
   * 显示 1.5 倍速符号的图标。
   */
  @UnstableApi
  public static final int ICON_PLAYBACK_SPEED_1_5 = 0xf4e0;

  /**
   * 显示 1.8 倍速符号的图标。
   */
  @UnstableApi
  public static final int ICON_PLAYBACK_SPEED_1_8 = 0xff4e0;

  /**
   * 显示 2.0 倍速符号的图标。
   */
  @UnstableApi
  public static final int ICON_PLAYBACK_SPEED_2_0 = 0xf4eb;

  /**
   * 显示设置符号的图标（一个风格化的齿轮）。
   */
  @UnstableApi
  public static final int ICON_SETTINGS = 0xe8b8;

  /**
   * 显示质量选择符号的图标（多条水平线带滑块）。
   */
  @UnstableApi
  public static final int ICON_QUALITY = 0xe429;

  /**
   * 显示字幕符号的图标（一个带点和水平线的矩形）。
   */
  @UnstableApi
  public static final int ICON_SUBTITLES = 0xe048;

  /**
   * 显示关闭字幕符号的图标（一个带点和水平线的矩形，带一条大对角线）。
   */
  @UnstableApi
  public static final int ICON_SUBTITLES_OFF = 0xef72;

  /**
   * 显示隐藏式字幕符号的图标（一个带字母 CC 的矩形）。
   */
  @UnstableApi
  public static final int ICON_CLOSED_CAPTIONS = 0xe01c;

  /**
   * 显示关闭隐藏式字幕符号的图标（一个带字母 CC 的矩形，带一条大对角线）。
   */
  @UnstableApi
  public static final int ICON_CLOSED_CAPTIONS_OFF = 0xf1dc;

  /**
   * 显示同步符号的图标（两个逆时针的开放箭头）。
   */
  @UnstableApi
  public static final int ICON_SYNC = 0xe627;

  /**
   * 显示分享符号的图标（三个点通过两条对角线连接，右侧开放）。
   */
  @UnstableApi
  public static final int ICON_SHARE = 0xe80d;

  /**
   * 显示音量增加符号的图标（一个风格化的扬声器带多条声波）。
   */
  @UnstableApi
  public static final int ICON_VOLUME_UP = 0xe050;

  /**
   * 显示音量减少符号的图标（一个风格化的扬声器带一条小声波）。
   */
  @UnstableApi
  public static final int ICON_VOLUME_DOWN = 0xe04d;

  /**
   * 显示静音符号的图标（一个风格化的扬声器带多条声波，带一条大对角线）。
   */
  @UnstableApi
  public static final int ICON_VOLUME_OFF = 0xe04f;

  /**
   * 显示艺术家符号的图标（一个风格化的人带一个音符）。
   */
  @UnstableApi
  public static final int ICON_ARTIST = 0xe01a;

  /**
   * 显示专辑符号的图标（一个风格化的黑胶唱片）。
   */
  @UnstableApi
  public static final int ICON_ALBUM = 0xe019;

  /**
   * 显示电台符号的图标（左右方向的声波）。
   */
  @UnstableApi
  public static final int ICON_RADIO = 0xe51e;

  /**
   * 显示信号符号的图标（一个垂直的杆带圆形声波）。
   */
  @UnstableApi
  public static final int ICON_SIGNAL = 0xf048;

  /**
   * 显示订阅符号的图标（左下角一个点带多个同心四分之一圆）。
   */
  @UnstableApi
  public static final int ICON_FEED = 0xe0e5;

// TODO: b/332877990 - 稳定这些常量及其他与槽位相关的 API

  /**
   * 用于在 UI 界面中显示按钮的槽位。必须是 {@code CommandButton.SLOT_} 常量之一。
   */
  @UnstableApi
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
      SLOT_CENTRAL, // 中央槽位
      SLOT_BACK, // 后退槽位
      SLOT_FORWARD, // 前进槽位
      SLOT_BACK_SECONDARY, // 辅助后退槽位
      SLOT_FORWARD_SECONDARY, // 辅助前进槽位
      SLOT_OVERFLOW // 溢出槽位
  })
  public @interface Slot {

  }

  /**
   * 播放控制 UI 中的中央槽位，通常用于播放或暂停操作。
   */
  @UnstableApi
  public static final int SLOT_CENTRAL = 1;

  /**
   * 播放控制 UI 中用于向后播放操作的槽位，通常用于上一首或快退操作。
   */
  @UnstableApi
  public static final int SLOT_BACK = 2;

  /**
   * 播放控制 UI 中用于向前播放操作的槽位，通常用于下一首或快进操作。
   */
  @UnstableApi
  public static final int SLOT_FORWARD = 3;

  /**
   * 播放控制 UI 中用于辅助向后播放操作的槽位，通常用于上一首或快退操作。
   */
  @UnstableApi
  public static final int SLOT_BACK_SECONDARY = 4;

  /**
   * 播放控制 UI 中用于辅助向前播放操作的槽位，通常用于下一首或快进操作。
   */
  @UnstableApi
  public static final int SLOT_FORWARD_SECONDARY = 5;

  /**
   * 播放控制 UI 中用于其他不适合放入其他槽位的额外操作的槽位。
   */
  @UnstableApi
  public static final int SLOT_OVERFLOW = 6;

  /**
   * 用于构建 {@link CommandButton} 的构建器。
   */
  public static final class Builder {

    private final @Icon int icon; // 图标常量

    @Nullable
    private SessionCommand sessionCommand; // 会话命令
    private @Player.Command int playerCommand; // 播放器命令
    @DrawableRes
    private int iconResId; // 图标资源 ID
    @Nullable
    private Uri iconUri; // 图标 URI
    private CharSequence displayName; // 显示名称
    private Bundle extras; // 额外数据
    private boolean enabled; // 是否启用
    @Nullable
    private ImmutableIntArray slots; // 允许的槽位

    /**
     * [即将弃用] 请使用 {@link #Builder(int)} 替代，以定义按钮的 {@link Icon}。除非使用 {@link #ICON_UNDEFINED}，
     * 否则不再需要通过 {@link #setIconResId(int)} 设置单独的图标资源 ID。
     */
    public Builder() {
      this(ICON_UNDEFINED);
    }

    /**
     * 创建构建器。
     *
     * @param icon 按钮应显示的 {@link Icon}。
     */
    @UnstableApi
    public Builder(@Icon int icon) {
      this(icon, getIconResIdForIconConstant(icon));
    }

    // 内部构造函数版本，用于立即分配已知的图标资源 ID。
    // 这是为了提高 R8 资源缩减效率，确保图标不需要解析为任何绑定的图标资源。
    /* package */ Builder(@Icon int icon, @DrawableRes int iconResId) {
      this.icon = icon;
      this.iconResId = iconResId;
      displayName = "";
      extras = Bundle.EMPTY;
      playerCommand = Player.COMMAND_INVALID;
      enabled = true;
    }

    /**
     * 设置按钮点击时需要 {@linkplain MediaController#isSessionCommandAvailable 可用} 的 {@link SessionCommand}。
     *
     * <p>如果已通过 {@link #setPlayerCommand(int)} 设置了播放器命令，则不能设置此命令。
     *
     * @param sessionCommand 会话命令。
     * @return 当前构建器，用于链式调用。
     */
    @CanIgnoreReturnValue
    public Builder setSessionCommand(SessionCommand sessionCommand) {
      checkNotNull(sessionCommand, "sessionCommand 不能为 null。");
      checkArgument(
          playerCommand == Player.COMMAND_INVALID,
          "已设置 playerCommands。只能设置 sessionCommand 或 playerCommand 中的一个。");
      this.sessionCommand = sessionCommand;
      return this;
    }

    /**
     * 设置按钮点击时需要 {@linkplain MediaController#isCommandAvailable 可用} 的 {@link Player.Command}。
     *
     * <p>如果已通过 {@link #setSessionCommand(SessionCommand)} 设置了会话命令，则不能设置此命令。
     *
     * @param playerCommand 播放器命令。
     * @return 当前构建器，用于链式调用。
     */
    @CanIgnoreReturnValue
    public Builder setPlayerCommand(@Player.Command int playerCommand) {
      checkArgument(
          sessionCommand == null,
          "已设置 sessionCommand。只能设置 sessionCommand 或 playerCommand 中的一个。");
      this.playerCommand = playerCommand;
      return this;
    }

    /**
     * [即将弃用] 应使用 {@link #Builder(int)} 的 {@link Icon} 参数来定义图标。
     *
     * <p>如果现有图标列表不足，请在构造函数中使用 {@link #ICON_UNDEFINED}，并通过 {@link #setCustomIconResId} 设置单独的图标资源 ID。
     */
    @CanIgnoreReturnValue
    public Builder setIconResId(@DrawableRes int resId) {
      return setCustomIconResId(resId);
    }

    /**
     * 设置图标的备用资源 ID。
     *
     * <p>当预定义的 {@link #icon} 不可用或设置为 {@link #ICON_UNDEFINED} 时，将使用此资源 ID。
     *
     * @param resId 自定义图标的资源 ID。
     * @return 当前构建器，用于链式调用。
     */
    @UnstableApi
    @CanIgnoreReturnValue
    public Builder setCustomIconResId(@DrawableRes int resId) {
      iconResId = resId;
      return this;
    }

    /**
     * 设置按钮图标的备用 {@linkplain ContentResolver#SCHEME_CONTENT 内容} 或 {@linkplain ContentResolver#SCHEME_ANDROID_RESOURCE 资源} {@link Uri}。
     *
     * <p>注意：当预定义的 {@link CommandButton#icon} 不可用或设置为 {@link #ICON_UNDEFINED} 时，可能会使用此 {@link Uri}。
     * 它可以与 {@link #setCustomIconResId} 一起使用，以便能够加载内容或资源 {@link Uri} 的消费者使用。
     *
     * @param uri 图标的 URI。
     * @return 当前构建器，用于链式调用。
     */
    @UnstableApi
    @CanIgnoreReturnValue
    public Builder setIconUri(Uri uri) {
      checkArgument(
          Objects.equal(uri.getScheme(), ContentResolver.SCHEME_CONTENT)
              || Objects.equal(uri.getScheme(), ContentResolver.SCHEME_ANDROID_RESOURCE),
          "CommandButton 仅支持内容或资源 URI。");
      this.iconUri = uri;
      return this;
    }

    /**
     * 设置按钮的显示名称。
     *
     * @param displayName 显示名称。
     * @return 当前构建器，用于链式调用。
     */
    @CanIgnoreReturnValue
    public Builder setDisplayName(CharSequence displayName) {
      this.displayName = displayName;
      return this;
    }

    /**
     * 设置按钮是否启用。
     *
     * <p>注意：如果相应的命令对 {@link MediaController} 实例不可用，此值将设置为 {@code false}（参见 {@link #setPlayerCommand} 和 {@link #setSessionCommand}）。
     *
     * <p>默认值为 {@code true}。
     *
     * @param enabled 是否启用按钮。
     * @return 当前构建器，用于链式调用。
     */
    @CanIgnoreReturnValue
    public Builder setEnabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    /**
     * 设置按钮的额外 {@link Bundle}。
     *
     * @param extras 额外 {@link Bundle}。
     * @return 当前构建器，用于链式调用。
     */
    @CanIgnoreReturnValue
    public Builder setExtras(Bundle extras) {
      this.extras = new Bundle(extras);
      return this;
    }

    /**
     * 设置按钮允许的 {@link Slot} 位置。
     *
     * <p>按钮仅允许在定义的槽位中显示。如果没有任何槽位可以显示按钮（因为槽位不存在、已被占用或 UI 表面不允许特定类型的按钮），则按钮将不会被显示。
     *
     * <p>当提供多个槽位时，它们定义了优先级顺序。按钮将放置在列表中第一个存在、未被占用且允许此类型按钮的槽位中。
     *
     * <p>如果未指定，默认值取决于关联的 {@link #setPlayerCommand 播放器命令} 和构造函数中设置的 {@link Icon}：
     *
     * <ul>
     *   <li>{@link Player#COMMAND_PLAY_PAUSE} 和/或 {@link #ICON_PLAY}, {@link #ICON_PAUSE}: {@link #SLOT_CENTRAL}
     *   <li>{@link Player#COMMAND_SEEK_TO_PREVIOUS}, {@link Player#COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM}, {@link Player#COMMAND_SEEK_BACK} 和/或 {@link #ICON_PREVIOUS}, {@link #ICON_SKIP_BACK}, {@link #ICON_REWIND}: {@link #SLOT_BACK}
     *   <li>{@link Player#COMMAND_SEEK_TO_NEXT}, {@link Player#COMMAND_SEEK_TO_NEXT_MEDIA_ITEM}, {@link Player#COMMAND_SEEK_FORWARD} 和/或 {@link #ICON_NEXT}, {@link #ICON_SKIP_FORWARD}, {@link #ICON_FAST_FORWARD}: {@link #SLOT_FORWARD}
     *   <li>其他情况: {@link #SLOT_OVERFLOW}
     * </ul>
     *
     * @param slots 允许的 {@link Slot} 位置列表。不能为空。
     * @return 当前构建器，用于链式调用。
     */
    @UnstableApi
    @CanIgnoreReturnValue
    public Builder setSlots(@Slot int... slots) {
      checkArgument(slots.length != 0);
      this.slots = ImmutableIntArray.copyOf(slots);
      return this;
    }

    /**
     * 构建 {@link CommandButton}。
     */
    public CommandButton build() {
      checkState(
          (sessionCommand == null) != (playerCommand == Player.COMMAND_INVALID),
          "必须设置 sessionCommand 或 playerCommand 中的一个。");
      if (slots == null) {
        slots = ImmutableIntArray.of(getDefaultSlot(playerCommand, icon));
      }
      return new CommandButton(
          sessionCommand,
          playerCommand,
          icon,
          iconResId,
          iconUri,
          displayName,
          extras,
          enabled,
          slots);
    }
  }

  /**
   * 按钮的会话命令。如果设置了 {@link #playerCommand}，则此值为 {@code null}。
   */
  @Nullable
  public final SessionCommand sessionCommand;

  /**
   * 按钮的 {@link Player.Command} 命令。如果设置了 {@link #sessionCommand}，则此值为 {@link Player#COMMAND_INVALID}。
   */
  public final @Player.Command int playerCommand;

  /**
   * 按钮的 {@link Icon}。
   */
  @UnstableApi
  public final @Icon int icon;

  /**
   * 按钮的备用图标资源 ID。
   *
   * <p>当预定义的 {@link #icon} 不可用或设置为 {@link #ICON_UNDEFINED} 时，将使用此资源 ID。
   *
   * <p>如果不需要，可以为 {@code 0}。
   */
  @DrawableRes
  public final int iconResId;

  /**
   * 按钮图标的备用 {@linkplain ContentResolver#SCHEME_CONTENT 内容} 或 {@linkplain ContentResolver#SCHEME_ANDROID_RESOURCE 资源} {@link Uri}。
   *
   * <p>当预定义的 {@link #icon} 不可用或设置为 {@link #ICON_UNDEFINED} 时，将使用此 {@link Uri}。
   *
   * <p>可以为 {@code null}。
   *
   * <p>注意：此值可以与 {@link #iconResId} 一起使用，以便能够加载内容或资源 {@link Uri} 的消费者使用。
   */
  @UnstableApi
  @Nullable
  public final Uri iconUri;

  /**
   * 按钮的显示名称。如果命令是预定义的且不需要自定义名称，则可以为空。
   */
  public final CharSequence displayName;
  /**
   * 按钮的额外 {@link Bundle}。它是会话和控制器之间的私有信息。
   */
  @UnstableApi
  public final Bundle extras;

  /**
   * 按钮允许的 {@link Slot} 位置。
   *
   * <p>按钮仅允许在定义的槽位中显示。如果没有任何槽位可以显示按钮（因为槽位不存在、已被占用或 UI 表面不允许特定类型的按钮），则按钮将不会被显示。
   *
   * <p>当提供多个槽位时，它们定义了优先级顺序。按钮将放置在列表中第一个存在、未被占用且允许此类型按钮的槽位中。
   */
  @UnstableApi
  public final ImmutableIntArray slots;

  /**
   * 按钮是否启用。
   *
   * <p>注意：如果相应的命令对 {@link MediaController} 实例不可用，此值将设置为 {@code false}（参见 {@link #playerCommand} 和 {@link #sessionCommand}）。
   */
  public final boolean isEnabled;

  private CommandButton(
      @Nullable SessionCommand sessionCommand,
      @Player.Command int playerCommand,
      @Icon int icon,
      @DrawableRes int iconResId,
      @Nullable Uri iconUri,
      CharSequence displayName,
      Bundle extras,
      boolean enabled,
      ImmutableIntArray slots) {
    this.sessionCommand = sessionCommand;
    this.playerCommand = playerCommand;
    this.icon = icon;
    this.iconResId = iconResId;
    this.iconUri = iconUri;
    this.displayName = displayName;
    this.extras = new Bundle(extras);
    this.isEnabled = enabled;
    this.slots = slots;
  }

  /**
   * 返回一个带有新 {@link #isEnabled} 标志的副本。
   */
  @CheckReturnValue
  /* package */ CommandButton copyWithIsEnabled(boolean isEnabled) {
    // 由于此方法仅用于库内部，因此选择了这种方法，而不是传统的 `buildUpon` 方式。
    // 这是为了将其与应用程序使用的公共 Builder-API 分开。
    if (this.isEnabled == isEnabled) {
      return this;
    }
    return new CommandButton(
        sessionCommand,
        playerCommand,
        icon,
        iconResId,
        iconUri,
        displayName,
        new Bundle(extras),
        isEnabled,
        slots);
  }

  /**
   * 检查给定的命令按钮是否相等，忽略 {@link #extras}。
   */
  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof CommandButton)) {
      return false;
    }
    CommandButton button = (CommandButton) obj;
    return Objects.equal(sessionCommand, button.sessionCommand)
        && playerCommand == button.playerCommand
        && icon == button.icon
        && iconResId == button.iconResId
        && Objects.equal(iconUri, button.iconUri)
        && TextUtils.equals(displayName, button.displayName)
        && isEnabled == button.isEnabled
        && slots.equals(button.slots);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        sessionCommand, playerCommand, icon, iconResId, displayName, isEnabled, iconUri, slots);
  }

  /**
   * 返回一个命令按钮列表，如果相应的命令不可用，则将 {@link CommandButton#isEnabled} 标志设置为 false。
   */
  /* package */
  static ImmutableList<CommandButton> copyWithUnavailableButtonsDisabled(
      List<CommandButton> commandButtons,
      SessionCommands sessionCommands,
      Player.Commands playerCommands) {
    ImmutableList.Builder<CommandButton> updatedButtons = new ImmutableList.Builder<>();
    for (int i = 0; i < commandButtons.size(); i++) {
      CommandButton button = commandButtons.get(i);
      if (isButtonCommandAvailable(button, sessionCommands, playerCommands)) {
        updatedButtons.add(button);
      } else {
        updatedButtons.add(button.copyWithIsEnabled(false));
      }
    }
    return updatedButtons.build();
  }

  /**
   * 返回按钮所需的命令（{@link #playerCommand} 或 {@link #sessionCommand}）是否可用。
   *
   * @param button          命令按钮。
   * @param sessionCommands 可用的会话命令。
   * @param playerCommands  可用的播放器命令。
   * @return 按钮所需的命令是否可用。
   */
  /* package */
  static boolean isButtonCommandAvailable(
      CommandButton button, SessionCommands sessionCommands, Player.Commands playerCommands) {
    return (button.sessionCommand != null && sessionCommands.contains(button.sessionCommand))
        || (button.playerCommand != Player.COMMAND_INVALID
        && playerCommands.contains(button.playerCommand));
  }

// 用于序列化和反序列化 CommandButton 对象的字段名称常量。
// 每个字段名称通过 Util.intToStringMaxRadix(int) 方法生成，确保唯一性。

  /**
   * 会话命令字段名称。
   */
  private static final String FIELD_SESSION_COMMAND = Util.intToStringMaxRadix(0);

  /**
   * 播放器命令字段名称。
   */
  private static final String FIELD_PLAYER_COMMAND = Util.intToStringMaxRadix(1);

  /**
   * 图标资源 ID 字段名称。
   */
  private static final String FIELD_ICON_RES_ID = Util.intToStringMaxRadix(2);

  /**
   * 显示名称字段名称。
   */
  private static final String FIELD_DISPLAY_NAME = Util.intToStringMaxRadix(3);

  /**
   * 额外数据字段名称。
   */
  private static final String FIELD_EXTRAS = Util.intToStringMaxRadix(4);

  /**
   * 启用状态字段名称。
   */
  private static final String FIELD_ENABLED = Util.intToStringMaxRadix(5);

  /**
   * 图标 URI 字段名称。
   */
  private static final String FIELD_ICON_URI = Util.intToStringMaxRadix(6);

  /**
   * 图标字段名称。
   */
  private static final String FIELD_ICON = Util.intToStringMaxRadix(7);

  /**
   * 槽位字段名称。
   */
  private static final String FIELD_SLOTS = Util.intToStringMaxRadix(8);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    if (sessionCommand != null) {
      bundle.putBundle(FIELD_SESSION_COMMAND, sessionCommand.toBundle());
    }
    if (playerCommand != Player.COMMAND_INVALID) {
      bundle.putInt(FIELD_PLAYER_COMMAND, playerCommand);
    }
    if (icon != ICON_UNDEFINED) {
      bundle.putInt(FIELD_ICON, icon);
    }
    if (iconResId != 0) {
      bundle.putInt(FIELD_ICON_RES_ID, iconResId);
    }
    if (displayName != "") {
      bundle.putCharSequence(FIELD_DISPLAY_NAME, displayName);
    }
    if (!extras.isEmpty()) {
      bundle.putBundle(FIELD_EXTRAS, extras);
    }
    if (iconUri != null) {
      bundle.putParcelable(FIELD_ICON_URI, iconUri);
    }
    if (!isEnabled) {
      bundle.putBoolean(FIELD_ENABLED, isEnabled);
    }
    if (slots.length() != 1 || slots.get(0) != SLOT_OVERFLOW) {
      bundle.putIntArray(FIELD_SLOTS, slots.toArray());
    }
    return bundle;
  }

  /**
   * @deprecated 请使用 {@link #fromBundle(Bundle, int)} 替代。
   */
  @Deprecated
  @UnstableApi
  public static CommandButton fromBundle(Bundle bundle) {
    return fromBundle(bundle, MediaSessionStub.VERSION_INT);
  }

  /**
   * 从 {@link Bundle} 中恢复一个 {@code CommandButton}。
   */
  @UnstableApi
  public static CommandButton fromBundle(Bundle bundle, int sessionInterfaceVersion) {
    @Nullable Bundle sessionCommandBundle = bundle.getBundle(FIELD_SESSION_COMMAND);
    @Nullable
    SessionCommand sessionCommand =
        sessionCommandBundle == null ? null : SessionCommand.fromBundle(sessionCommandBundle);
    @Player.Command
    int playerCommand =
        bundle.getInt(FIELD_PLAYER_COMMAND, /* defaultValue= */ Player.COMMAND_INVALID);
    int iconResId = bundle.getInt(FIELD_ICON_RES_ID, /* defaultValue= */ 0);
    CharSequence displayName = bundle.getCharSequence(FIELD_DISPLAY_NAME, /* defaultValue= */ "");
    @Nullable Bundle extras = bundle.getBundle(FIELD_EXTRAS);
    // 在 sessionInterfaceVersion == 3 之前，会话期望此值无意义，我们只能假设其值为 true。
    boolean enabled =
        sessionInterfaceVersion < 3 || bundle.getBoolean(FIELD_ENABLED, /* defaultValue= */ true);
    @Nullable Uri iconUri = bundle.getParcelable(FIELD_ICON_URI);
    @Icon int icon = bundle.getInt(FIELD_ICON, /* defaultValue= */ ICON_UNDEFINED);
    @Nullable
    @Slot
    int[] slots = bundle.getIntArray(FIELD_SLOTS);
    Builder builder = new Builder(icon, iconResId);
    if (sessionCommand != null) {
      builder.setSessionCommand(sessionCommand);
    }
    if (playerCommand != Player.COMMAND_INVALID) {
      builder.setPlayerCommand(playerCommand);
    }
    if (iconUri != null
        && (Objects.equal(iconUri.getScheme(), ContentResolver.SCHEME_CONTENT)
        || Objects.equal(iconUri.getScheme(), ContentResolver.SCHEME_ANDROID_RESOURCE))) {
      builder.setIconUri(iconUri);
    }
    return builder
        .setDisplayName(displayName)
        .setExtras(extras == null ? Bundle.EMPTY : extras)
        .setEnabled(enabled)
        .setSlots(slots == null ? new int[]{SLOT_OVERFLOW} : slots)
        .build();
  }

  /**
   * 返回给定 {@link Icon} 常量对应的可绘制资源 ID。
   *
   * @param icon {@link Icon} 常量。
   * @return 与 {@code icon} 对应的可绘制资源 ID，如果未找到则返回 0。
   */
  @UnstableApi
  @DrawableRes
  public static int getIconResIdForIconConstant(@Icon int icon) {
    switch (icon) {
      case ICON_PLAY:
        return R.drawable.media3_icon_play; // 播放图标
      case ICON_PAUSE:
        return R.drawable.media3_icon_pause; // 暂停图标
      case ICON_STOP:
        return R.drawable.media3_icon_stop; // 停止图标
      case ICON_NEXT:
        return R.drawable.media3_icon_next; // 下一首图标
      case ICON_PREVIOUS:
        return R.drawable.media3_icon_previous; // 上一首图标
      case ICON_SKIP_FORWARD:
        return R.drawable.media3_icon_skip_forward; // 快进图标
      case ICON_SKIP_FORWARD_5:
        return R.drawable.media3_icon_skip_forward_5; // 快进 5 秒图标
      case ICON_SKIP_FORWARD_10:
        return R.drawable.media3_icon_skip_forward_10; // 快进 10 秒图标
      case ICON_SKIP_FORWARD_15:
        return R.drawable.media3_icon_skip_forward_15; // 快进 15 秒图标
      case ICON_SKIP_FORWARD_30:
        return R.drawable.media3_icon_skip_forward_30; // 快进 30 秒图标
      case ICON_SKIP_BACK:
        return R.drawable.media3_icon_skip_back; // 快退图标
      case ICON_SKIP_BACK_5:
        return R.drawable.media3_icon_skip_back_5; // 快退 5 秒图标
      case ICON_SKIP_BACK_10:
        return R.drawable.media3_icon_skip_back_10; // 快退 10 秒图标
      case ICON_SKIP_BACK_15:
        return R.drawable.media3_icon_skip_back_15; // 快退 15 秒图标
      case ICON_SKIP_BACK_30:
        return R.drawable.media3_icon_skip_back_30; // 快退 30 秒图标
      case ICON_FAST_FORWARD:
        return R.drawable.media3_icon_fast_forward; // 快速前进图标
      case ICON_REWIND:
        return R.drawable.media3_icon_rewind; // 快速后退图标
      case ICON_REPEAT_ALL:
        return R.drawable.media3_icon_repeat_all; // 全部重复图标
      case ICON_REPEAT_ONE:
        return R.drawable.media3_icon_repeat_one; // 单曲重复图标
      case ICON_REPEAT_OFF:
        return R.drawable.media3_icon_repeat_off; // 关闭重复图标
      case ICON_SHUFFLE_ON:
        return R.drawable.media3_icon_shuffle_on; // 随机播放开启图标
      case ICON_SHUFFLE_OFF:
        return R.drawable.media3_icon_shuffle_off; // 随机播放关闭图标
      case ICON_SHUFFLE_STAR:
        return R.drawable.media3_icon_shuffle_star; // 随机播放星标图标
      case ICON_HEART_FILLED:
        return R.drawable.media3_icon_heart_filled; // 实心爱心图标
      case ICON_HEART_UNFILLED:
        return R.drawable.media3_icon_heart_unfilled; // 空心爱心图标
      case ICON_STAR_FILLED:
        return R.drawable.media3_icon_star_filled; // 实心星星图标
      case ICON_STAR_UNFILLED:
        return R.drawable.media3_icon_star_unfilled; // 空心星星图标
      case ICON_BOOKMARK_FILLED:
        return R.drawable.media3_icon_bookmark_filled; // 实心书签图标
      case ICON_BOOKMARK_UNFILLED:
        return R.drawable.media3_icon_bookmark_unfilled; // 空心书签图标
      case ICON_THUMB_UP_FILLED:
        return R.drawable.media3_icon_thumb_up_filled; // 实心点赞图标
      case ICON_THUMB_UP_UNFILLED:
        return R.drawable.media3_icon_thumb_up_unfilled; // 空心点赞图标
      case ICON_THUMB_DOWN_FILLED:
        return R.drawable.media3_icon_thumb_down_filled; // 实心点踩图标
      case ICON_THUMB_DOWN_UNFILLED:
        return R.drawable.media3_icon_thumb_down_unfilled; // 空心点踩图标
      case ICON_FLAG_FILLED:
        return R.drawable.media3_icon_flag_filled; // 实心旗帜图标
      case ICON_FLAG_UNFILLED:
        return R.drawable.media3_icon_flag_unfilled; // 空心旗帜图标
      case ICON_PLUS:
        return R.drawable.media3_icon_plus; // 加号图标
      case ICON_MINUS:
        return R.drawable.media3_icon_minus; // 减号图标
      case ICON_PLAYLIST_ADD:
        return R.drawable.media3_icon_playlist_add; // 添加到播放列表图标
      case ICON_PLAYLIST_REMOVE:
        return R.drawable.media3_icon_playlist_remove; // 从播放列表移除图标
      case ICON_QUEUE_ADD:
        return R.drawable.media3_icon_queue_add; // 添加到队列图标
      case ICON_QUEUE_NEXT:
        return R.drawable.media3_icon_queue_next; // 下一首队列图标
      case ICON_QUEUE_REMOVE:
        return R.drawable.media3_icon_queue_remove; // 从队列移除图标
      case ICON_BLOCK:
        return R.drawable.media3_icon_block; // 屏蔽图标
      case ICON_PLUS_CIRCLE_FILLED:
        return R.drawable.media3_icon_plus_circle_filled; // 实心加号圆圈图标
      case ICON_PLUS_CIRCLE_UNFILLED:
        return R.drawable.media3_icon_plus_circle_unfilled; // 空心加号圆圈图标
      case ICON_MINUS_CIRCLE_FILLED:
        return R.drawable.media3_icon_minus_circle_filled; // 实心减号圆圈图标
      case ICON_MINUS_CIRCLE_UNFILLED:
        return R.drawable.media3_icon_minus_circle_unfilled; // 空心减号圆圈图标
      case ICON_CHECK_CIRCLE_FILLED:
        return R.drawable.media3_icon_check_circle_filled; // 实心对号圆圈图标
      case ICON_CHECK_CIRCLE_UNFILLED:
        return R.drawable.media3_icon_check_circle_unfilled; // 空心对号圆圈图标
      case ICON_PLAYBACK_SPEED:
        return R.drawable.media3_icon_playback_speed; // 播放速度图标
      case ICON_PLAYBACK_SPEED_0_5:
        return R.drawable.media3_icon_playback_speed_0_5; // 0.5 倍速播放图标
      case ICON_PLAYBACK_SPEED_0_8:
        return R.drawable.media3_icon_playback_speed_0_8; // 0.8 倍速播放图标
      case ICON_PLAYBACK_SPEED_1_0:
        return R.drawable.media3_icon_playback_speed_1_0; // 1.0 倍速播放图标
      case ICON_PLAYBACK_SPEED_1_2:
        return R.drawable.media3_icon_playback_speed_1_2; // 1.2 倍速播放图标
      case ICON_PLAYBACK_SPEED_1_5:
        return R.drawable.media3_icon_playback_speed_1_5; // 1.5 倍速播放图标
      case ICON_PLAYBACK_SPEED_1_8:
        return R.drawable.media3_icon_playback_speed_1_8; // 1.8 倍速播放图标
      case ICON_PLAYBACK_SPEED_2_0:
        return R.drawable.media3_icon_playback_speed_2_0; // 2.0 倍速播放图标
      case ICON_SETTINGS:
        return R.drawable.media3_icon_settings; // 设置图标
      case ICON_QUALITY:
        return R.drawable.media3_icon_quality; // 质量图标
      case ICON_SUBTITLES:
        return R.drawable.media3_icon_subtitles; // 字幕图标
      case ICON_SUBTITLES_OFF:
        return R.drawable.media3_icon_subtitles_off; // 关闭字幕图标
      case ICON_CLOSED_CAPTIONS:
        return R.drawable.media3_icon_closed_captions; // 隐藏式字幕图标
      case ICON_CLOSED_CAPTIONS_OFF:
        return R.drawable.media3_icon_closed_captions_off; // 关闭隐藏式字幕图标
      case ICON_SYNC:
        return R.drawable.media3_icon_sync; // 同步图标
      case ICON_SHARE:
        return R.drawable.media3_icon_share; // 分享图标
      case ICON_VOLUME_UP:
        return R.drawable.media3_icon_volume_up; // 音量增加图标
      case ICON_VOLUME_DOWN:
        return R.drawable.media3_icon_volume_down; // 音量减少图标
      case ICON_VOLUME_OFF:
        return R.drawable.media3_icon_volume_off; // 静音图标
      case ICON_ARTIST:
        return R.drawable.media3_icon_artist; // 艺术家图标
      case ICON_ALBUM:
        return R.drawable.media3_icon_album; // 专辑图标
      case ICON_RADIO:
        return R.drawable.media3_icon_radio; // 电台图标
      case ICON_SIGNAL:
        return R.drawable.media3_icon_signal; // 信号图标
      case ICON_FEED:
        return R.drawable.media3_icon_feed; // 订阅图标
      default:
        return 0; // 默认返回 0，表示未找到对应的图标资源
    }
  }

  /**
   * 返回按钮的默认 {@link Slot}。
   *
   * @param playerCommand 按钮关联的 {@link Player.Command}。
   * @param icon          按钮的 {@link Icon}。
   * @return 按钮的默认 {@link Slot}。
   */
  @UnstableApi
  public static @Slot int getDefaultSlot(@Player.Command int playerCommand, @Icon int icon) {
    // 如果按钮是播放/暂停命令，或者图标是播放或暂停图标，则返回中央槽位
    if (playerCommand == Player.COMMAND_PLAY_PAUSE || icon == ICON_PLAY || icon == ICON_PAUSE) {
      return SLOT_CENTRAL;
    }
    // 如果按钮是快退、上一首、快退 5 秒、快退 10 秒、快退 15 秒、快退 30 秒命令或图标，则返回后退槽位
    else if (playerCommand == Player.COMMAND_SEEK_BACK
        || playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS
        || playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        || icon == ICON_PREVIOUS
        || icon == ICON_REWIND
        || icon == ICON_SKIP_BACK
        || icon == ICON_SKIP_BACK_5
        || icon == ICON_SKIP_BACK_10
        || icon == ICON_SKIP_BACK_15
        || icon == ICON_SKIP_BACK_30) {
      return SLOT_BACK;
    }
    // 如果按钮是快进、下一首、快进 5 秒、快进 10 秒、快进 15 秒、快进 30 秒命令或图标，则返回前进槽位
    else if (playerCommand == Player.COMMAND_SEEK_FORWARD
        || playerCommand == Player.COMMAND_SEEK_TO_NEXT
        || playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        || icon == ICON_NEXT
        || icon == ICON_FAST_FORWARD
        || icon == ICON_SKIP_FORWARD
        || icon == ICON_SKIP_FORWARD_5
        || icon == ICON_SKIP_FORWARD_10
        || icon == ICON_SKIP_FORWARD_15
        || icon == ICON_SKIP_FORWARD_30) {
      return SLOT_FORWARD;
    }
    // 其他情况返回溢出槽位
    else {
      return SLOT_OVERFLOW;
    }
  }
}
