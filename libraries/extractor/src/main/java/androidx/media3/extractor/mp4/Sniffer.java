package androidx.media3.extractor.mp4;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.Mp4Box;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.SniffFailure;
import java.io.IOException;

/**
 * 提供从 {@link ExtractorInput} 中读取数据并判断输入是否为 MP4 格式的方法。
 */
@UnstableApi
public final class Sniffer {

  /** ftyp 原子中存储的 QuickTime 媒体品牌。 */
  public static final int BRAND_QUICKTIME = 0x71742020;

  /** ftyp 原子中存储的 HEIC 媒体品牌。 */
  public static final int BRAND_HEIC = 0x68656963;

  /** 在嗅探（sniffing）时，最大需要读取的字节数。 */
  private static final int SEARCH_LENGTH = 4 * 1024;

  private static final int[] COMPATIBLE_BRANDS =
      new int[] {
        0x69736f6d, // isom
        0x69736f32, // iso2
        0x69736f33, // iso3
        0x69736f34, // iso4
        0x69736f35, // iso5
        0x69736f36, // iso6
        0x69736f39, // iso9
        0x61766331, // avc1
        0x68766331, // hvc1
        0x68657631, // hev1
        0x61763031, // av01
        0x6d703431, // mp41
        0x6d703432, // mp42
        0x33673261, // 3g2a
        0x33673262, // 3g2b
        0x33677236, // 3gr6
        0x33677336, // 3gs6
        0x33676536, // 3ge6
        0x33676736, // 3gg6
        0x4d345620, // M4V[space]
        0x4d344120, // M4A[space]
        0x66347620, // f4v[space]
        0x6b646469, // kddi
        0x4d345650, // M4VP
        BRAND_QUICKTIME, // qt[space][space]
        0x4d534e56, // MSNV, Sony PSP
        0x64627931, // dby1, Dolby Vision
        0x69736d6c, // isml
        0x70696666, // piff
      };

  /**
   * 如果从 {@code input} 的当前位置读取的数据与分段 MP4 文件一致，则返回 {@code null}，
   * 否则返回一个 {@link SniffFailure} 描述检测到的第一个不一致之处。
   *
   * @param input 用于读取数据的提取器输入。读取位置将被修改。
   * @return 如果输入数据看起来是分段 MP4 格式，则返回 {@code null}，否则返回一个 {@link SniffFailure}，
   *     描述为什么输入数据不被认为是分段 MP4。
   * @throws IOException 如果从输入读取数据时发生错误。
   */
  @Nullable
  public static SniffFailure sniffFragmented(ExtractorInput input) throws IOException {
    return sniffInternal(input, /* fragmented= */ true, /* acceptHeic= */ false);
  }

  /**
   * 如果从 {@code input} 的当前位置读取的数据与未分段 MP4 文件一致，则返回 {@code null}，
   * 否则返回一个 {@link SniffFailure} 描述检测到的第一个不一致之处。
   *
   * @param input 用于读取数据的提取器输入。读取位置将被修改。
   * @param acceptHeic 是否对 HEIC 照片返回 {@code null}。
   * @return 如果输入数据看起来是未分段 MP4 格式，则返回 {@code null}，否则返回一个 {@link SniffFailure}，
   *     描述为什么输入数据不被认为是未分段 MP4。
   * @throws IOException 如果从输入读取数据时发生错误。
   */
  @Nullable
  public static SniffFailure sniffUnfragmented(ExtractorInput input, boolean acceptHeic)
      throws IOException {
    return sniffInternal(input, /* fragmented= */ false, acceptHeic);
  }

  @Nullable
  private static SniffFailure sniffInternal(
      ExtractorInput input, boolean fragmented, boolean acceptHeic) throws IOException {
    // 获取输入流的长度
    long inputLength = input.getLength();
    // 计算需要搜索的字节数，如果输入流长度未知或超过最大搜索长度，则使用最大搜索长度
    int bytesToSearch =
        (int)
            (inputLength == C.LENGTH_UNSET || inputLength > SEARCH_LENGTH
                ? SEARCH_LENGTH
                : inputLength);

    // 创建一个可解析的字节数组，用于存储原子头部数据
    ParsableByteArray buffer = new ParsableByteArray(64);
    int bytesSearched = 0; // 已搜索的字节数
    boolean foundGoodFileType = false; // 是否找到兼容的文件类型
    boolean isFragmented = false; // 文件是否为分段格式
    while (bytesSearched < bytesToSearch) {
      // 读取原子头部
      int headerSize = Mp4Box.HEADER_SIZE; // 原子头部大小（默认 8 字节）
      buffer.reset(headerSize);
      // 尝试读取原子头部数据
      boolean success =
          input.peekFully(buffer.getData(), 0, headerSize, /* allowEndOfInput= */ true);
      if (!success) {
        // 如果读取失败（例如到达文件末尾），则跳出循环
        break;
      }
      // 读取原子大小和类型
      long atomSize = buffer.readUnsignedInt();
      int atomType = buffer.readInt();
      if (atomSize == Mp4Box.DEFINES_LARGE_SIZE) {
        // 如果原子大小定义为“大原子”，则读取完整的大原子头部（16 字节）
        headerSize = Mp4Box.LONG_HEADER_SIZE;
        input.peekFully(
            buffer.getData(), Mp4Box.HEADER_SIZE, Mp4Box.LONG_HEADER_SIZE - Mp4Box.HEADER_SIZE);
        buffer.setLimit(Mp4Box.LONG_HEADER_SIZE);
        atomSize = buffer.readLong();
      } else if (atomSize == Mp4Box.EXTENDS_TO_END_SIZE) {
        // 如果原子大小定义为“延伸到文件末尾”，则计算实际大小
        long fileEndPosition = input.getLength();
        if (fileEndPosition != C.LENGTH_UNSET) {
          atomSize = fileEndPosition - input.getPeekPosition() + headerSize;
        }
      }

      if (atomSize < headerSize) {
        // 如果原子大小小于头部大小，则文件无效
        return new AtomSizeTooSmallSniffFailure(atomType, atomSize, headerSize);
      }
      bytesSearched += headerSize; // 更新已搜索的字节数

      if (atomType == Mp4Box.TYPE_moov) {
        // 如果找到 moov 原子，则增加搜索范围，确保不会因为 moov 原子过大而错过 mvex 原子
        bytesToSearch += (int) atomSize;
        if (inputLength != C.LENGTH_UNSET && bytesToSearch > inputLength) {
          // 确保搜索范围不超过文件大小
          bytesToSearch = (int) inputLength;
        }
        // 继续搜索，检查 moov 原子中是否包含 mvex 原子以确定文件是否为分段格式
        continue;
      }

      if (atomType == Mp4Box.TYPE_moof || atomType == Mp4Box.TYPE_mvex) {
        // 如果找到 moof 或 mvex 原子，则文件为分段格式
        isFragmented = true;
        break;
      }

      if (atomType == Mp4Box.TYPE_mdat) {
        // 如果找到 mdat 原子，则文件类型可能是有效的（因为 QuickTime 规范不要求文件必须以 ftyp 原子开头）
        foundGoodFileType = true;
      }

      if (bytesSearched + atomSize - headerSize >= bytesToSearch) {
        // 如果继续搜索会超出搜索范围，则停止搜索
        break;
      }

      // 计算原子数据部分的大小
      int atomDataSize = (int) (atomSize - headerSize);
      bytesSearched += atomDataSize; // 更新已搜索的字节数
      if (atomType == Mp4Box.TYPE_ftyp) {
        // 如果找到 ftyp 原子，则解析并检查文件类型是否兼容
        if (atomDataSize < 8) {
          return new AtomSizeTooSmallSniffFailure(atomType, atomDataSize, 8);
        }
        buffer.reset(atomDataSize);
        input.peekFully(buffer.getData(), 0, atomDataSize);
        int majorBrand = buffer.readInt(); // 读取主要品牌
        if (isCompatibleBrand(majorBrand, acceptHeic)) {
          foundGoodFileType = true; // 如果主要品牌兼容，则标记为找到有效文件类型
        }
        // 跳过次要版本字段
        buffer.skipBytes(4);
        int compatibleBrandsCount = buffer.bytesLeft() / 4; // 计算兼容品牌的数量
        @Nullable int[] compatibleBrands = null;
        if (!foundGoodFileType && compatibleBrandsCount > 0) {
          // 如果尚未找到兼容品牌，则读取所有兼容品牌
          compatibleBrands = new int[compatibleBrandsCount];
          for (int i = 0; i < compatibleBrandsCount; i++) {
            compatibleBrands[i] = buffer.readInt();
            if (isCompatibleBrand(compatibleBrands[i], acceptHeic)) {
              foundGoodFileType = true; // 如果找到兼容品牌，则标记为找到有效文件类型
              break;
            }
          }
        }
        if (!foundGoodFileType) {
          // 如果未找到兼容品牌，则返回不兼容品牌错误
          return new UnsupportedBrandsSniffFailure(majorBrand, compatibleBrands);
        }
      } else if (atomDataSize != 0) {
        // 如果原子数据部分不为空，则跳过该部分
        input.advancePeekPosition(atomDataSize);
      }
    }
    // 根据检查结果返回相应的 SniffFailure 或 null
    if (!foundGoodFileType) {
      return NoDeclaredBrandSniffFailure.INSTANCE; // 未找到有效文件类型
    } else if (fragmented != isFragmented) {
      return isFragmented
          ? IncorrectFragmentationSniffFailure.FILE_FRAGMENTED // 文件为分段格式，但预期为非分段
          : IncorrectFragmentationSniffFailure.FILE_NOT_FRAGMENTED; // 文件为非分段格式，但预期为分段
    } else {
      return null; // 文件格式符合预期
    }
  }

  /**
   * 返回 {@code brand} 是否是 MP4 提取器兼容的 ftyp 原子品牌。
   */
  private static boolean isCompatibleBrand(int brand, boolean acceptHeic) {
    // 如果品牌的前三个字节是 '3gp'（0x00336770），则认为是兼容的
    if (brand >>> 8 == 0x00336770) {
      return true;
    }
    // 如果品牌是 HEIC 并且 acceptHeic 为 true，则认为是兼容的
    else if (brand == BRAND_HEIC && acceptHeic) {
      return true;
    }
    // 遍历所有兼容品牌列表，检查是否匹配
    for (int compatibleBrand : COMPATIBLE_BRANDS) {
      if (compatibleBrand == brand) {
        return true;
      }
    }
    // 如果没有找到匹配的品牌，则返回 false
    return false;
  }

  private Sniffer() {
    // Prevent instantiation.
  }
}
