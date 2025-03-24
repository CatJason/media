package androidx.media3.common.util;

import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaMetadata;
import com.google.common.util.concurrent.ListenableFuture;

/** 用于加载图像的接口。 */
@UnstableApi
public interface BitmapLoader {

  /** 返回是否支持给定的 {@code mimeType}。 */
  boolean supportsMimeType(String mimeType);

  /** 从压缩的二进制数据中解码图像。 */
  ListenableFuture<Bitmap> decodeBitmap(byte[] data);

  /** 从 {@code uri} 加载图像。 */
  ListenableFuture<Bitmap> loadBitmap(Uri uri);

  /**
   * 从 {@link MediaMetadata} 加载图像。如果 {@code metadata} 不包含位图信息，则返回 null。
   *
   * <p>默认情况下，如果 {@link MediaMetadata#artworkData} 存在，该方法将尝试从中解码图像。否则，如果 {@link MediaMetadata#artworkUri} 存在，该方法将尝试从中加载图像。如果 {@link MediaMetadata#artworkData} 和 {@link MediaMetadata#artworkUri} 都不存在，则返回 null。
   */
  @Nullable
  default ListenableFuture<Bitmap> loadBitmapFromMetadata(MediaMetadata metadata) {
    @Nullable ListenableFuture<Bitmap> future;
    if (metadata.artworkData != null) {
      future = decodeBitmap(metadata.artworkData);
    } else if (metadata.artworkUri != null) {
      future = loadBitmap(metadata.artworkUri);
    } else {
      future = null;
    }
    return future;
  }
}