package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_CUSTOM;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.SessionCommand.CommandCode;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 一组 {@link SessionCommand 会话命令}。 */
public final class SessionCommands {

  private static final String TAG = "SessionCommands";

  /** 用于构建 {@link SessionCommands} 的构建器。 */
  public static final class Builder {

    private final Set<SessionCommand> commands;

    /** 创建一个新的构建器。 */
    public Builder() {
      commands = new HashSet<>();
    }

    /** 从另一个 {@link SessionCommands} 创建一个新的构建器。 */
    private Builder(SessionCommands sessionCommands) {
      this.commands = new HashSet<>(checkNotNull(sessionCommands).commands);
    }

    /**
     * 添加一个命令。
     *
     * @param command 要添加的命令。
     * @return 当前构建器，用于链式调用。
     */
    @CanIgnoreReturnValue
    public Builder add(SessionCommand command) {
      commands.add(checkNotNull(command));
      return this;
    }

    /**
     * 添加一个命令代码对应的命令。命令代码不能为 {@link SessionCommand#COMMAND_CODE_CUSTOM}。
     *
     * @param commandCode 用于构建命令并添加的命令代码。
     * @return 当前构建器，用于链式调用。
     */
    @CanIgnoreReturnValue
    public Builder add(@CommandCode int commandCode) {
      checkArgument(commandCode != COMMAND_CODE_CUSTOM);
      commands.add(new SessionCommand(commandCode));
      return this;
    }

    /**
     * 添加指定集合中的所有命令。
     *
     * @param commands 包含要添加到此集合中的元素的集合。
     * @return 当前构建器，用于链式调用。
     */
    @CanIgnoreReturnValue
    public Builder addSessionCommands(Collection<SessionCommand> commands) {
      this.commands.addAll(commands);
      return this;
    }

    /**
     * 移除与给定 {@link SessionCommand 命令}匹配的命令。
     *
     * @param command 要查找的命令。
     * @return 当前构建器，用于链式调用。
     */
    @CanIgnoreReturnValue
    public Builder remove(SessionCommand command) {
      commands.remove(checkNotNull(command));
      return this;
    }

    /**
     * 移除与给定 {@code commandCode} 匹配的命令。命令代码不能为 {@link SessionCommand#COMMAND_CODE_CUSTOM}。
     *
     * @param commandCode 要查找的命令代码。
     * @return 当前构建器，用于链式调用。
     */
    @CanIgnoreReturnValue
    public Builder remove(@CommandCode int commandCode) {
      checkArgument(commandCode != COMMAND_CODE_CUSTOM);
      for (SessionCommand command : commands) {
        if (command.commandCode == commandCode) {
          commands.remove(command);
          break;
        }
      }
      return this;
    }

    /**
     * 添加所有会话命令。
     *
     * @return 当前构建器，用于链式调用。
     */
    /* package */ @CanIgnoreReturnValue
    Builder addAllSessionCommands() {
      addCommandCodes(SessionCommand.SESSION_COMMANDS);
      return this;
    }

    /**
     * 添加所有媒体库命令。
     *
     * @return 当前构建器，用于链式调用。
     */
    /* package */ @CanIgnoreReturnValue
    Builder addAllLibraryCommands() {
      addCommandCodes(SessionCommand.LIBRARY_COMMANDS);
      return this;
    }

    /**
     * 添加所有预定义命令。
     *
     * @return 当前构建器，用于链式调用。
     */
    /* package */ @CanIgnoreReturnValue
    Builder addAllPredefinedCommands() {
      addAllSessionCommands();
      addAllLibraryCommands();
      return this;
    }

    private void addCommandCodes(List<@CommandCode Integer> commandCodes) {
      for (int i = 0; i < commandCodes.size(); i++) {
        add(new SessionCommand(commandCodes.get(i)));
      }
    }

    /** 构建 {@link SessionCommands}。 */
    public SessionCommands build() {
      return new SessionCommands(commands);
    }
  }

  /** 空的会话命令集合。 */
  public static final SessionCommands EMPTY = new Builder().build();

  /** 所有会话命令。 */
  public final ImmutableSet<SessionCommand> commands;

  /**
   * 创建一个新的会话命令集合。
   *
   * @param sessionCommands 要复制的会话命令集合。
   */
  private SessionCommands(Collection<SessionCommand> sessionCommands) {
    this.commands = ImmutableSet.copyOf(sessionCommands);
  }

  /**
   * 返回是否存在与给定 {@code command} 匹配的命令。
   *
   * @param command 要查找的命令。
   * @return 是否存在该命令。
   */
  public boolean contains(SessionCommand command) {
    return commands.contains(checkNotNull(command));
  }

  /**
   * 返回是否存在与给定 {@code commandCode} 匹配的命令。
   *
   * @param commandCode 要查找的 {@link SessionCommand.CommandCode} 命令代码。不能为
   *     {@link SessionCommand#COMMAND_CODE_CUSTOM}。
   * @return 是否存在该命令。
   */
  public boolean contains(@CommandCode int commandCode) {
    checkArgument(commandCode != COMMAND_CODE_CUSTOM, "对于自定义命令，请使用 contains(Command)");
    return containsCommandCode(commands, commandCode);
  }

  /** 返回一个用当前实例的值初始化的 {@link Builder}。 */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SessionCommands)) {
      return false;
    }

    SessionCommands that = (SessionCommands) obj;
    return commands.equals(that.commands);
  }

  @Override
  public int hashCode() {
    return ObjectsCompat.hash(commands);
  }

  private static boolean containsCommandCode(
      Collection<SessionCommand> commands, @CommandCode int commandCode) {
    for (SessionCommand command : commands) {
      if (command.commandCode == commandCode) {
        return true;
      }
    }
    return false;
  }

  private static final String FIELD_SESSION_COMMANDS = Util.intToStringMaxRadix(0);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    ArrayList<Bundle> sessionCommandBundleList = new ArrayList<>();
    for (SessionCommand command : commands) {
      sessionCommandBundleList.add(command.toBundle());
    }
    bundle.putParcelableArrayList(FIELD_SESSION_COMMANDS, sessionCommandBundleList);
    return bundle;
  }

  /** 从 {@link Bundle} 中恢复 {@code SessionCommands}。 */
  @UnstableApi
  public static SessionCommands fromBundle(Bundle bundle) {
    @Nullable
    ArrayList<Bundle> sessionCommandBundleList =
        bundle.getParcelableArrayList(FIELD_SESSION_COMMANDS);
    if (sessionCommandBundleList == null) {
      Log.w(TAG, "缺少命令。创建一个空的 SessionCommands");
      return SessionCommands.EMPTY;
    }

    Builder builder = new Builder();
    for (int i = 0; i < sessionCommandBundleList.size(); i++) {
      builder.add(SessionCommand.fromBundle(sessionCommandBundleList.get(i)));
    }
    return builder.build();
  }
  ;
}