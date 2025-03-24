package androidx.media3.common.util;

/**
 * Represents an operation that accepts a single input argument and returns no result. Unlike most
 * other functional interfaces, Consumer is expected to operate via side-effects.
 */
@UnstableApi
public interface Consumer<T> {

  /** Performs this operation on the given argument. */
  void accept(T t);
}
