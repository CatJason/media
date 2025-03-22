/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.mediacodec;

import android.media.MediaCodec;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException;
import java.util.List;

/** {@link MediaCodec} 实例的选择器。 */
@UnstableApi
public interface MediaCodecSelector {

  /**
   * {@link MediaCodecSelector} 的默认实现，返回给定格式的首选解码器。
   */
  MediaCodecSelector DEFAULT = MediaCodecUtil::getDecoderInfos;

  /**
   * 返回能够解码指定 MIME 类型的解码器列表，按优先级顺序排列。
   *
   * @param mimeType 需要解码器的 MIME 类型。
   * @param requiresSecureDecoder 是否需要安全解码器。
   * @param requiresTunnelingDecoder 是否需要隧道解码器。
   * @return 与解码器对应的 {@link MediaCodecInfo} 的不可修改列表。可能为空。
   * @throws DecoderQueryException 如果查询解码器时发生错误，则抛出此异常。
   */
  List<MediaCodecInfo> getDecoderInfos(
      String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder)
      throws DecoderQueryException;
}