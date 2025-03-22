/*
 * 版权所有 (C) 2020 The Android Open Source Project
 *
 * 根据 Apache 许可证 2.0 版本（“许可证”）授权；
 * 除非符合许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则根据许可证分发的软件是基于“按原样”分发的，
 * 没有任何明示或暗示的担保或条件。
 * 请参阅许可证以了解具体的语言权限和限制。
 */
package androidx.media3.exoplayer.mediacodec;

import android.media.MediaCodec;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderException;

/** 当 {@link MediaCodec} 解码器发生故障时抛出。 */
@UnstableApi
public class MediaCodecDecoderException extends DecoderException {

  /** 失败的解码器的 {@link MediaCodecInfo}。如果未知则为 null。 */
  @Nullable public final MediaCodecInfo codecInfo;

  /** 可选的开发者可读的诊断信息字符串。可能为 null。 */
  @Nullable public final String diagnosticInfo;

  /** 编解码器报告的可选错误代码。如果无法获取错误代码，则可能为 0。 */
  public final int errorCode;

  public MediaCodecDecoderException(Throwable cause, @Nullable MediaCodecInfo codecInfo) {
    super("Decoder failed: " + (codecInfo == null ? null : codecInfo.name), cause);
    this.codecInfo = codecInfo;
    diagnosticInfo =
        cause instanceof MediaCodec.CodecException
            ? ((MediaCodec.CodecException) cause).getDiagnosticInfo()
            : null;
    errorCode =
        Util.SDK_INT >= 23
            ? getErrorCodeV23(cause)
            : Util.getErrorCodeFromPlatformDiagnosticsInfo(diagnosticInfo);
  }

  @RequiresApi(23)
  private static int getErrorCodeV23(Throwable cause) {
    if (cause instanceof MediaCodec.CodecException) {
      return ((MediaCodec.CodecException) cause).getErrorCode();
    }
    return 0;
  }
}