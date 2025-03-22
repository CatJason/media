package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * 一个 {@link BitmapLoader}，用于缓存最后一次 {@link #decodeBitmap(byte[])} 或 {@link #loadBitmap(Uri)} 请求的结果。
 * 当最后一次请求的位图是从相同的 {@code data} 或相同的 {@code uri} 加载时，请求会从最后一次位图加载请求中获取结果。
 * 如果不满足上述两种情况，则请求会被转发到提供的 {@link BitmapLoader}，并且结果会被缓存。
 */
@UnstableApi
public final class CacheBitmapLoader implements BitmapLoader {

  private final BitmapLoader bitmapLoader;

  private @MonotonicNonNull BitmapLoadRequest lastBitmapLoadRequest;

  /**
   * 创建一个实例，能够缓存最后一次位图加载请求到给定的位图加载器。
   */
  public CacheBitmapLoader(BitmapLoader bitmapLoader) {
    this.bitmapLoader = bitmapLoader;
  }

  @Override
  public boolean supportsMimeType(String mimeType) {
    return bitmapLoader.supportsMimeType(mimeType); // 检查是否支持指定的 MIME 类型
  }

  @Override
  public ListenableFuture<Bitmap> decodeBitmap(byte[] data) {
    if (lastBitmapLoadRequest != null && lastBitmapLoadRequest.matches(data)) {
      return lastBitmapLoadRequest.getFuture(); // 如果请求与缓存匹配，则返回缓存的未来结果
    }
    ListenableFuture<Bitmap> future = bitmapLoader.decodeBitmap(data); // 否则，使用位图加载器解码位图
    lastBitmapLoadRequest = new BitmapLoadRequest(data, future); // 缓存本次请求
    return future;
  }

  @Override
  public ListenableFuture<Bitmap> loadBitmap(Uri uri) {
    if (lastBitmapLoadRequest != null && lastBitmapLoadRequest.matches(uri)) {
      return lastBitmapLoadRequest.getFuture(); // 如果请求与缓存匹配，则返回缓存的未来结果
    }
    ListenableFuture<Bitmap> future = bitmapLoader.loadBitmap(uri); // 否则，使用位图加载器加载位图
    lastBitmapLoadRequest = new BitmapLoadRequest(uri, future); // 缓存本次请求
    return future;
  }

  /**
   * 存储位图加载请求的结果。请求通过字节数组（如果位图是从压缩数据加载）或 URI（如果位图是从 URI 加载）来标识。
   */
  private static class BitmapLoadRequest {
    @Nullable private final byte[] data;
    @Nullable private final Uri uri;
    @Nullable private final ListenableFuture<Bitmap> future;

    public BitmapLoadRequest(byte[] data, ListenableFuture<Bitmap> future) {
      this.data = data;
      this.uri = null;
      this.future = future;
    }

    public BitmapLoadRequest(Uri uri, ListenableFuture<Bitmap> future) {
      this.data = null;
      this.uri = uri;
      this.future = future;
    }

    /** 判断位图加载请求是否是为 {@code data} 执行的。 */
    public boolean matches(@Nullable byte[] data) {
      return this.data != null && Arrays.equals(this.data, data);
    }

    /** 判断位图加载请求是否是为 {@code uri} 执行的。 */
    public boolean matches(@Nullable Uri uri) {
      return this.uri != null && this.uri.equals(uri);
    }

    /** 返回为位图加载请求设置的未来结果。 */
    public ListenableFuture<Bitmap> getFuture() {
      return checkStateNotNull(future);
    }
  }
}