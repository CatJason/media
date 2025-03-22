package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.Rating;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link MediaController} 可以发送给 {@link MediaSession} 的命令。
 *
 * <p>如果 {@link #commandCode} 不是 {@link #COMMAND_CODE_CUSTOM}，则它是预定义命令。如果
 * {@link #commandCode} 是 {@link #COMMAND_CODE_CUSTOM}，则它是自定义命令，且 {@link
 * #customAction} 不能为 {@code null}。
 */
public final class SessionCommand {

  /** 会话命令的命令代码。 */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
      COMMAND_CODE_CUSTOM,
      COMMAND_CODE_SESSION_SET_RATING,
      COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT,
      COMMAND_CODE_LIBRARY_SUBSCRIBE,
      COMMAND_CODE_LIBRARY_UNSUBSCRIBE,
      COMMAND_CODE_LIBRARY_GET_CHILDREN,
      COMMAND_CODE_LIBRARY_GET_ITEM,
      COMMAND_CODE_LIBRARY_SEARCH,
      COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT
  })
  public @interface CommandCode {}

  /**
   * 自定义命令的命令代码，可以通过 {@link SessionCommand} 中的字符串操作定义。
   */
  public static final int COMMAND_CODE_CUSTOM = 0;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // 会话命令（即发送给 MediaSession.Callback 的命令）
  //////////////////////////////////////////////////////////////////////////////////////////////////

  /** 命令代码，用于 {@link MediaController#setRating(String, Rating)}。 */
  public static final int COMMAND_CODE_SESSION_SET_RATING = 40010;

  /* package */ static final ImmutableList<Integer> SESSION_COMMANDS =
      ImmutableList.of(COMMAND_CODE_SESSION_SET_RATING);

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // 媒体库命令（即发送给 MediaLibraryService.MediaLibrarySession.Callback 的命令）
  //////////////////////////////////////////////////////////////////////////////////////////////////

  /** 命令代码，用于 {@link MediaBrowser#getLibraryRoot(LibraryParams)}。 */
  public static final int COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT = 50000;

  /** 命令代码，用于 {@link MediaBrowser#subscribe(String, LibraryParams)}。 */
  public static final int COMMAND_CODE_LIBRARY_SUBSCRIBE = 50001;

  /** 命令代码，用于 {@link MediaBrowser#unsubscribe(String)}。 */
  public static final int COMMAND_CODE_LIBRARY_UNSUBSCRIBE = 50002;

  /** 命令代码，用于 {@link MediaBrowser#getChildren(String, int, int, LibraryParams)}。 */
  public static final int COMMAND_CODE_LIBRARY_GET_CHILDREN = 50003;

  /** 命令代码，用于 {@link MediaBrowser#getItem(String)}。 */
  public static final int COMMAND_CODE_LIBRARY_GET_ITEM = 50004;

  /** 命令代码，用于 {@link MediaBrowser#search(String, LibraryParams)}。 */
  public static final int COMMAND_CODE_LIBRARY_SEARCH = 50005;

  /** 命令代码，用于 {@link MediaBrowser#getSearchResult(String, int, int, LibraryParams)}。 */
  public static final int COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT = 50006;

  /* package */ static final ImmutableList<Integer> LIBRARY_COMMANDS =
      ImmutableList.of(
          COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT,
          COMMAND_CODE_LIBRARY_SUBSCRIBE,
          COMMAND_CODE_LIBRARY_UNSUBSCRIBE,
          COMMAND_CODE_LIBRARY_GET_CHILDREN,
          COMMAND_CODE_LIBRARY_GET_ITEM,
          COMMAND_CODE_LIBRARY_SEARCH,
          COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT);

  /**
   * 预定义命令的命令代码。如果是自定义命令，则为 {@link #COMMAND_CODE_CUSTOM}。
   */
  public final @CommandCode int commandCode;

  /** 自定义命令的操作。如果是预定义命令，则为空字符串。 */
  public final String customAction;

  /**
   * 自定义命令的额外数据包。如果是预定义命令，则为 {@link Bundle#EMPTY}。
   *
   * <p>互操作性：当命令发送到旧版 {@code android.support.v4.media.session.MediaSessionCompat} 或
   * {@code android.support.v4.media.session.MediaControllerCompat} 时，此值不会被使用。
   */
  public final Bundle customExtras;

  /**
   * 创建预定义命令。
   *
   * @param commandCode 预定义命令的命令代码。
   */
  public SessionCommand(@CommandCode int commandCode) {
    checkArgument(
        commandCode != COMMAND_CODE_CUSTOM, "commandCode 不应为 COMMAND_CODE_CUSTOM");
    this.commandCode = commandCode;
    customAction = "";
    customExtras = Bundle.EMPTY;
  }

  /**
   * 创建自定义命令。
   *
   * @param action 自定义命令的操作。
   * @param extras 自定义命令的额外数据包。当命令发送到旧版 {@code android.support.v4.media.session.MediaSessionCompat} 或
   *     {@code android.support.v4.media.session.MediaControllerCompat} 时，此值不会被使用。
   */
  public SessionCommand(String action, Bundle extras) {
    commandCode = COMMAND_CODE_CUSTOM;
    customAction = checkNotNull(action);
    customExtras = new Bundle(checkNotNull(extras));
  }

  /** 检查给定的会话命令是否相等，忽略额外数据包。 */
  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof SessionCommand)) {
      return false;
    }
    SessionCommand other = (SessionCommand) obj;
    return commandCode == other.commandCode && TextUtils.equals(customAction, other.customAction);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(customAction, commandCode);
  }

  private static final String FIELD_COMMAND_CODE = Util.intToStringMaxRadix(0);
  private static final String FIELD_CUSTOM_ACTION = Util.intToStringMaxRadix(1);
  private static final String FIELD_CUSTOM_EXTRAS = Util.intToStringMaxRadix(2);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_COMMAND_CODE, commandCode);
    bundle.putString(FIELD_CUSTOM_ACTION, customAction);
    bundle.putBundle(FIELD_CUSTOM_EXTRAS, customExtras);
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复 {@code SessionCommand}。 */
  @UnstableApi
  public static SessionCommand fromBundle(Bundle bundle) {
    int commandCode = bundle.getInt(FIELD_COMMAND_CODE, /* defaultValue= */ COMMAND_CODE_CUSTOM);
    if (commandCode != COMMAND_CODE_CUSTOM) {
      return new SessionCommand(commandCode);
    } else {
      String customAction = checkNotNull(bundle.getString(FIELD_CUSTOM_ACTION));
      @Nullable Bundle customExtras = bundle.getBundle(FIELD_CUSTOM_EXTRAS);
      return new SessionCommand(customAction, customExtras == null ? Bundle.EMPTY : customExtras);
    }
  }
  ;
}