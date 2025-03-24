package androidx.media3.common.util;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import androidx.annotation.Nullable;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;

/**
 * The standard implementation of {@link Clock}, an instance of which is available via {@link
 * SystemClock#DEFAULT}.
 */
@UnstableApi
public class SystemClock implements Clock {

  protected SystemClock() {}

  @Override
  public long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  @Override
  public long elapsedRealtime() {
    return android.os.SystemClock.elapsedRealtime();
  }

  @Override
  public long uptimeMillis() {
    return android.os.SystemClock.uptimeMillis();
  }

  @Override
  public long nanoTime() {
    return System.nanoTime();
  }

  @Override
  @SuppressWarnings({"nullness:argument", "nullness:return"})
  public HandlerWrapper createHandler(
      Looper looper, @Nullable @UnknownInitialization Callback callback) {
    return new SystemHandlerWrapper(new Handler(looper, callback));
  }

  @Override
  public void onThreadBlocked() {
    // Do nothing.
  }
}
