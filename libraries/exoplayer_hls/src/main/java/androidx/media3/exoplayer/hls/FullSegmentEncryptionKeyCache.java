package androidx.media3.exoplayer.hls;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Assertions;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU 缓存，最多可保存 {@code maxSize} 个完整片段加密密钥。每次添加时，如果缓存大小超过 {@code maxSize}，则移除最旧的项目（根据插入顺序）。
 */
/* package */ final class FullSegmentEncryptionKeyCache {

  private final LinkedHashMap<Uri, byte[]> backingMap;

  public FullSegmentEncryptionKeyCache(int maxSize) {
    backingMap =
        new LinkedHashMap<Uri, byte[]>(
            /* initialCapacity= */ maxSize + 1, /* loadFactor= */ 1, /* accessOrder= */ false) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<Uri, byte[]> eldest) {
            return size() > maxSize;
          }
        };
  }

  /**
   * 返回与此 {@code uri} 对应的缓存中的 {@code encryptionKey}，如果 {@code uri} 为 null 或不在缓存中，则返回 null。
   */
  @Nullable
  public byte[] get(@Nullable Uri uri) {
    if (uri == null) {
      return null;
    }
    return backingMap.get(uri);
  }

  /**
   * 向缓存中插入一个条目。
   *
   * @throws NullPointerException 如果 {@code uri} 或 {@code encryptionKey} 为 null。
   */
  @Nullable
  public byte[] put(Uri uri, byte[] encryptionKey) {
    return backingMap.put(Assertions.checkNotNull(uri), Assertions.checkNotNull(encryptionKey));
  }

  /**
   * 如果 {@code uri} 存在于缓存中，则返回 true。
   *
   * @throws NullPointerException 如果 {@code uri} 为 null。
   */
  public boolean containsUri(Uri uri) {
    return backingMap.containsKey(Assertions.checkNotNull(uri));
  }

  /**
   * 从缓存中移除 {@code uri}。如果 {@code uri} 存在于缓存中，则返回对应的 {@code encryptionKey}，否则返回 null。
   *
   * @throws NullPointerException 如果 {@code uri} 为 null。
   */
  @Nullable
  public byte[] remove(Uri uri) {
    return backingMap.remove(Assertions.checkNotNull(uri));
  }
}