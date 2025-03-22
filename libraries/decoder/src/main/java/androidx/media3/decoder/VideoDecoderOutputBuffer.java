/*
 * 版权所有 (C) 2019 The Android Open Source Project
 *
 * 根据 Apache 许可证 2.0 版本（“许可证”）授权；
 * 除非遵守许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则根据许可证分发的软件
 * 均按“原样”分发，不附带任何明示或暗示的担保或条件。
 * 请参阅许可证以了解特定语言的权限和限制。
 */
package androidx.media3.decoder;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;

/** 包含视频帧数据的视频解码器输出缓冲区。 */
@UnstableApi
public class VideoDecoderOutputBuffer extends DecoderOutputBuffer {

  public static final int COLORSPACE_UNKNOWN = 0;
  public static final int COLORSPACE_BT601 = 1;
  public static final int COLORSPACE_BT709 = 2;
  public static final int COLORSPACE_BT2020 = 3;

  /** 解码器私有数据。在本地代码中使用。 */
  public int decoderPrivate;

  /** 输出模式。 */
  public @C.VideoOutputMode int mode;

  /** RGB 模式的 RGB 缓冲区。 */
  @Nullable public ByteBuffer data;

  public int width;
  public int height;

  /** 解码此输出缓冲区的输入格式。 */
  @Nullable public Format format;

  /** YUV 模式的 YUV 平面。 */
  @Nullable public ByteBuffer[] yuvPlanes;

  @Nullable public int[] yuvStrides;
  public int colorspace;

  /**
   * 与输出帧相关的补充数据，如果 {@link #hasSupplementalData()} 返回 true。
   * 如果存在，缓冲区将填充从位置 0 到其限制的补充数据。
   */
  @Nullable public ByteBuffer supplementalData;

  private final Owner<VideoDecoderOutputBuffer> owner;

  /**
   * 创建 VideoDecoderOutputBuffer。
   *
   * @param owner 缓冲区所有者。
   */
  public VideoDecoderOutputBuffer(Owner<VideoDecoderOutputBuffer> owner) {
    this.owner = owner;
  }

  @Override
  public void release() {
    owner.releaseOutputBuffer(this);
  }

  /**
   * 初始化缓冲区。
   *
   * @param timeUs 缓冲区的呈现时间戳，以微秒为单位。
   * @param mode 输出模式。可以是 {@link C#VIDEO_OUTPUT_MODE_NONE}、{@link
   *     C#VIDEO_OUTPUT_MODE_YUV} 和 {@link C#VIDEO_OUTPUT_MODE_SURFACE_YUV} 之一。
   * @param supplementalData 与帧相关的补充数据，如果不存在则为 {@code null}。在方法返回后可以安全地重用提供的缓冲区。
   */
  public void init(
      long timeUs, @C.VideoOutputMode int mode, @Nullable ByteBuffer supplementalData) {
    this.timeUs = timeUs;
    this.mode = mode;
    if (supplementalData != null && supplementalData.hasRemaining()) {
      addFlag(C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA);
      int size = supplementalData.limit();
      if (this.supplementalData == null || this.supplementalData.capacity() < size) {
        this.supplementalData = ByteBuffer.allocate(size);
      } else {
        this.supplementalData.clear();
      }
      this.supplementalData.put(supplementalData);
      this.supplementalData.flip();
      supplementalData.position(0);
    } else {
      this.supplementalData = null;
    }
  }

  /**
   * 根据给定的步幅调整缓冲区大小。在解码完成后通过 JNI 调用。
   *
   * @return 缓冲区是否成功调整大小。
   */
  public boolean initForYuvFrame(int width, int height, int yStride, int uvStride, int colorspace) {
    this.width = width;
    this.height = height;
    this.colorspace = colorspace;
    int uvHeight = (int) (((long) height + 1) / 2);
    if (!isSafeToMultiply(yStride, height) || !isSafeToMultiply(uvStride, uvHeight)) {
      return false;
    }
    int yLength = yStride * height;
    int uvLength = uvStride * uvHeight;
    int minimumYuvSize = yLength + (uvLength * 2);
    if (!isSafeToMultiply(uvLength, 2) || minimumYuvSize < yLength) {
      return false;
    }

    // 初始化数据。
    if (data == null || data.capacity() < minimumYuvSize) {
      data = ByteBuffer.allocateDirect(minimumYuvSize);
    } else {
      data.position(0);
      data.limit(minimumYuvSize);
    }

    if (yuvPlanes == null) {
      yuvPlanes = new ByteBuffer[3];
    }

    ByteBuffer data = this.data;
    ByteBuffer[] yuvPlanes = this.yuvPlanes;

    // 必须在每一帧上重新包装，因为步幅可能已经改变。
    yuvPlanes[0] = data.slice();
    yuvPlanes[0].limit(yLength);
    data.position(yLength);
    yuvPlanes[1] = data.slice();
    yuvPlanes[1].limit(uvLength);
    data.position(yLength + uvLength);
    yuvPlanes[2] = data.slice();
    yuvPlanes[2].limit(uvLength);
    if (yuvStrides == null) {
      yuvStrides = new int[3];
    }
    yuvStrides[0] = yStride;
    yuvStrides[1] = uvStride;
    yuvStrides[2] = uvStride;
    return true;
  }

  /**
   * 在通过 {@link #decoderPrivate} 传递实际帧数据时，为给定的帧尺寸配置缓冲区。在解码完成后通过 JNI 调用。
   */
  public void initForPrivateFrame(int width, int height) {
    this.width = width;
    this.height = height;
  }

  /**
   * 确保单个数字相乘的结果可以适应整数的大小限制。
   */
  private static boolean isSafeToMultiply(int a, int b) {
    return a >= 0 && b >= 0 && !(b > 0 && a >= Integer.MAX_VALUE / b);
  }
}