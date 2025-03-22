package androidx.media3.extractor.mp4;

import static java.lang.Math.min;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.container.Mp4Box;
import androidx.media3.extractor.GaplessInfoHolder;
import androidx.media3.extractor.metadata.id3.ApicFrame;
import androidx.media3.extractor.metadata.id3.CommentFrame;
import androidx.media3.extractor.metadata.id3.Id3Frame;
import androidx.media3.extractor.metadata.id3.Id3Util;
import androidx.media3.extractor.metadata.id3.InternalFrame;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;
import com.google.common.collect.ImmutableList;

/** 用于处理 MP4 元数据的工具类。 */
/* package */ final class MetadataUtil {

  private static final String TAG = "MetadataUtil";

  // 以版权字符开头的短类型名称（省略）并具有等效的 ID3 帧。
  private static final int SHORT_TYPE_NAME_1 = 0x006e616d; // "nam"
  private static final int SHORT_TYPE_NAME_2 = 0x0074726b; // "trk"
  private static final int SHORT_TYPE_COMMENT = 0x00636d74; // "cmt"
  private static final int SHORT_TYPE_YEAR = 0x00646179; // "day"
  private static final int SHORT_TYPE_ARTIST = 0x00415254; // "ART"
  private static final int SHORT_TYPE_ENCODER = 0x00746f6f; // "too"
  private static final int SHORT_TYPE_ALBUM = 0x00616c62; // "alb"
  private static final int SHORT_TYPE_COMPOSER_1 = 0x00636f6d; // "com"
  private static final int SHORT_TYPE_COMPOSER_2 = 0x00777274; // "wrt"
  private static final int SHORT_TYPE_LYRICS = 0x006c7972; // "lyr"
  private static final int SHORT_TYPE_GENRE = 0x0067656e; // "gen"

  // 具有等效 ID3 帧的类型。
  private static final int TYPE_COVER_ART = 0x636f7672; // "covr"
  private static final int TYPE_GENRE = 0x676e7265; // "gnre"
  private static final int TYPE_GROUPING = 0x00677270; // "grp"
  private static final int TYPE_DISK_NUMBER = 0x6469736b; // "disk"
  private static final int TYPE_TRACK_NUMBER = 0x74726b6e; // "trkn"
  private static final int TYPE_TEMPO = 0x746d706f; // "tmpo"
  private static final int TYPE_COMPILATION = 0x6370696c; // "cpil"
  private static final int TYPE_ALBUM_ARTIST = 0x61415254; // "aART"
  private static final int TYPE_SORT_TRACK_NAME = 0x736f6e6d; // "sonm"
  private static final int TYPE_SORT_ALBUM = 0x736f616c; // "soal"
  private static final int TYPE_SORT_ARTIST = 0x736f6172; // "soar"
  private static final int TYPE_SORT_ALBUM_ARTIST = 0x736f6161; // "soaa"
  private static final int TYPE_SORT_COMPOSER = 0x736f636f; // "soco"

  // 没有等效 ID3 帧的类型。
  private static final int TYPE_RATING = 0x72746e67; // "rtng"
  private static final int TYPE_GAPLESS_ALBUM = 0x70676170; // "pgap"
  private static final int TYPE_TV_SORT_SHOW = 0x736f736e; // "sosn"
  private static final int TYPE_TV_SHOW = 0x74767368; // "tvsh"

  // 用于播放器内部使用的类型。
  private static final int TYPE_INTERNAL = 0x2d2d2d2d; // "----"

  private static final int PICTURE_TYPE_FRONT_COVER = 3; // 封面图片类型

  private static final int TYPE_TOP_BYTE_COPYRIGHT = 0xA9; // 版权字符的字节
  private static final int TYPE_TOP_BYTE_REPLACEMENT = 0xFD; // \uFFFD 的截断值

  private MetadataUtil() {} // 防止实例化

  /** 更新 {@link Format.Builder}，以包含来自提供的源的元数据。 */
  public static void setFormatMetadata(
      int trackType,
      @Nullable Metadata mdtaMetadata,
      Format.Builder formatBuilder,
      @NullableType Metadata... additionalMetadata) {
    Metadata formatMetadata = new Metadata();

    // 如果存在 mdtaMetadata，则将其条目添加到 formatMetadata 中
    if (mdtaMetadata != null) {
      for (int i = 0; i < mdtaMetadata.length(); i++) {
        Metadata.Entry entry = mdtaMetadata.get(i);
        if (entry instanceof MdtaMetadataEntry) {
          MdtaMetadataEntry mdtaMetadataEntry = (MdtaMetadataEntry) entry;
          // 如果键是视频捕获帧率，并且当前轨道是视频轨道，则添加该条目
          if (mdtaMetadataEntry.key.equals(MdtaMetadataEntry.KEY_ANDROID_CAPTURE_FPS)) {
            if (trackType == C.TRACK_TYPE_VIDEO) {
              formatMetadata = formatMetadata.copyWithAppendedEntries(mdtaMetadataEntry);
            }
          } else {
            formatMetadata = formatMetadata.copyWithAppendedEntries(mdtaMetadataEntry);
          }
        }
      }
    }

    // 将 additionalMetadata 中的条目添加到 formatMetadata 中
    for (Metadata metadata : additionalMetadata) {
      formatMetadata = formatMetadata.copyWithAppendedEntriesFrom(metadata);
    }

    // 如果 formatMetadata 不为空，则将其设置到 formatBuilder 中
    if (formatMetadata.length() > 0) {
      formatBuilder.setMetadata(formatMetadata);
    }
  }


  /** 更新 {@link Format.Builder}，以包含来自提供的源的无缝播放信息。 */
  public static void setFormatGaplessInfo(
      int trackType, GaplessInfoHolder gaplessInfoHolder, Format.Builder formatBuilder) {
    // 如果当前轨道是音频轨道并且包含无缝播放信息，则设置编码器延迟和填充
    if (trackType == C.TRACK_TYPE_AUDIO && gaplessInfoHolder.hasGaplessInfo()) {
      formatBuilder
          .setEncoderDelay(gaplessInfoHolder.encoderDelay)
          .setEncoderPadding(gaplessInfoHolder.encoderPadding);
    }
  }

  /**
   * 从 {@link ParsableByteArray} 解析单个用户数据 ilst 元素。元素从 {@link ParsableByteArray} 的当前位置开始读取，
   * 并且位置会前进元素的大小。即使元素的类型未被识别，位置也会前进。
   *
   * @param ilst 包含要解析的数据。
   * @return 解析后的元素，如果元素的类型未被识别，则返回 null。
   */
  @Nullable
  public static Metadata.Entry parseIlstElement(ParsableByteArray ilst) {
    int position = ilst.getPosition();
    int endPosition = position + ilst.readInt();
    int type = ilst.readInt();
    int typeTopByte = (type >> 24) & 0xFF;
    try {
      // 如果类型以版权字符或替换字符开头，则处理短类型
      if (typeTopByte == TYPE_TOP_BYTE_COPYRIGHT || typeTopByte == TYPE_TOP_BYTE_REPLACEMENT) {
        int shortType = type & 0x00FFFFFF;
        if (shortType == SHORT_TYPE_COMMENT) {
          return parseCommentAttribute(type, ilst);
        } else if (shortType == SHORT_TYPE_NAME_1 || shortType == SHORT_TYPE_NAME_2) {
          return parseTextAttribute(type, "TIT2", ilst);
        } else if (shortType == SHORT_TYPE_COMPOSER_1 || shortType == SHORT_TYPE_COMPOSER_2) {
          return parseTextAttribute(type, "TCOM", ilst);
        } else if (shortType == SHORT_TYPE_YEAR) {
          return parseTextAttribute(type, "TDRC", ilst);
        } else if (shortType == SHORT_TYPE_ARTIST) {
          return parseTextAttribute(type, "TPE1", ilst);
        } else if (shortType == SHORT_TYPE_ENCODER) {
          return parseTextAttribute(type, "TSSE", ilst);
        } else if (shortType == SHORT_TYPE_ALBUM) {
          return parseTextAttribute(type, "TALB", ilst);
        } else if (shortType == SHORT_TYPE_LYRICS) {
          return parseTextAttribute(type, "USLT", ilst);
        } else if (shortType == SHORT_TYPE_GENRE) {
          return parseTextAttribute(type, "TCON", ilst);
        } else if (shortType == TYPE_GROUPING) {
          return parseTextAttribute(type, "TIT1", ilst);
        }
      } else if (type == TYPE_GENRE) {
        return parseStandardGenreAttribute(ilst);
      } else if (type == TYPE_DISK_NUMBER) {
        return parseIndexAndCountAttribute(type, "TPOS", ilst);
      } else if (type == TYPE_TRACK_NUMBER) {
        return parseIndexAndCountAttribute(type, "TRCK", ilst);
      } else if (type == TYPE_TEMPO) {
        return parseIntegerAttribute(type, "TBPM", ilst, true, false);
      } else if (type == TYPE_COMPILATION) {
        return parseIntegerAttribute(type, "TCMP", ilst, true, true);
      } else if (type == TYPE_COVER_ART) {
        return parseCoverArt(ilst);
      } else if (type == TYPE_ALBUM_ARTIST) {
        return parseTextAttribute(type, "TPE2", ilst);
      } else if (type == TYPE_SORT_TRACK_NAME) {
        return parseTextAttribute(type, "TSOT", ilst);
      } else if (type == TYPE_SORT_ALBUM) {
        return parseTextAttribute(type, "TSOA", ilst);
      } else if (type == TYPE_SORT_ARTIST) {
        return parseTextAttribute(type, "TSOP", ilst);
      } else if (type == TYPE_SORT_ALBUM_ARTIST) {
        return parseTextAttribute(type, "TSO2", ilst);
      } else if (type == TYPE_SORT_COMPOSER) {
        return parseTextAttribute(type, "TSOC", ilst);
      } else if (type == TYPE_RATING) {
        return parseIntegerAttribute(type, "ITUNESADVISORY", ilst, false, false);
      } else if (type == TYPE_GAPLESS_ALBUM) {
        return parseIntegerAttribute(type, "ITUNESGAPLESS", ilst, false, true);
      } else if (type == TYPE_TV_SORT_SHOW) {
        return parseTextAttribute(type, "TVSHOWSORT", ilst);
      } else if (type == TYPE_TV_SHOW) {
        return parseTextAttribute(type, "TVSHOW", ilst);
      } else if (type == TYPE_INTERNAL) {
        return parseInternalAttribute(ilst, endPosition);
      }
      Log.d(TAG, "跳过未知的元数据条目: " + Mp4Box.getBoxTypeString(type));
      return null;
    } finally {
      ilst.setPosition(endPosition);
    }
  }

  /**
   * 从 ilst 盒子的当前位置解析 'mdta' 元数据条目。
   *
   * @param ilst ilst 盒子。
   * @param endPosition 条目在 ilst 盒子中的结束位置。
   * @param key mdta 元数据条目的键。
   * @return 解析后的元素，如果条目未被识别，则返回 null。
   */
  @Nullable
  public static MdtaMetadataEntry parseMdtaMetadataEntryFromIlst(
      ParsableByteArray ilst, int endPosition, String key) {
    int atomPosition;
    while ((atomPosition = ilst.getPosition()) < endPosition) {
      int atomSize = ilst.readInt();
      int atomType = ilst.readInt();
      if (atomType == Mp4Box.TYPE_data) {
        int typeIndicator = ilst.readInt();
        int localeIndicator = ilst.readInt();
        int dataSize = atomSize - 16;
        byte[] value = new byte[dataSize];
        ilst.readBytes(value, 0, dataSize);
        return new MdtaMetadataEntry(key, value, localeIndicator, typeIndicator);
      }
      ilst.setPosition(atomPosition + atomSize);
    }
    return null;
  }

  /**
   * 返回给定键的 {@link MdtaMetadataEntry}，如果键不存在，则返回 {@code null}。
   *
   * @param metadata 要从中检索 {@link MdtaMetadataEntry} 的 {@link Metadata}。
   * @param key 要搜索的元数据键。
   */
  @Nullable
  public static MdtaMetadataEntry findMdtaMetadataEntryWithKey(Metadata metadata, String key) {
    for (int i = 0; i < metadata.length(); i++) {
      Metadata.Entry entry = metadata.get(i);
      if (entry instanceof MdtaMetadataEntry) {
        MdtaMetadataEntry mdtaMetadataEntry = (MdtaMetadataEntry) entry;
        if (mdtaMetadataEntry.key.equals(key)) {
          return mdtaMetadataEntry;
        }
      }
    }
    return null;
  }

  @Nullable
  private static TextInformationFrame parseTextAttribute(
      int type, String id, ParsableByteArray data) {
    // 读取当前原子的尺寸
    int atomSize = data.readInt();
    // 读取当前原子的类型
    int atomType = data.readInt();
    // 如果原子类型是 "data"，则解析文本属性
    if (atomType == Mp4Box.TYPE_data) {
      // 跳过版本 (1 字节)、标志 (3 字节) 和空字段 (4 字节)
      data.skipBytes(8);
      // 读取以 null 结尾的字符串作为属性值
      String value = data.readNullTerminatedString(atomSize - 16);
      // 返回一个 TextInformationFrame 对象，包含属性 ID 和值
      return new TextInformationFrame(id, /* 描述= */ null, ImmutableList.of(value));
    }
    // 如果原子类型不是 "data"，则记录警告日志并返回 null
    Log.w(TAG, "解析文本属性失败: " + Mp4Box.getBoxTypeString(type));
    return null;
  }

  @Nullable
  private static CommentFrame parseCommentAttribute(int type, ParsableByteArray data) {
    // 读取当前原子的尺寸
    int atomSize = data.readInt();
    // 读取当前原子的类型
    int atomType = data.readInt();
    // 如果原子类型是 "data"，则解析注释属性
    if (atomType == Mp4Box.TYPE_data) {
      // 跳过版本 (1 字节)、标志 (3 字节) 和空字段 (4 字节)
      data.skipBytes(8);
      // 读取以 null 结尾的字符串作为注释值
      String value = data.readNullTerminatedString(atomSize - 16);
      // 返回一个 CommentFrame 对象，包含语言、注释文本和描述
      return new CommentFrame(C.LANGUAGE_UNDETERMINED, value, value);
    }
    // 如果原子类型不是 "data"，则记录警告日志并返回 null
    Log.w(TAG, "解析注释属性失败: " + Mp4Box.getBoxTypeString(type));
    return null;
  }

  @Nullable
  private static Id3Frame parseIntegerAttribute(
      int type,
      String id,
      ParsableByteArray data,
      boolean isTextInformationFrame,
      boolean isBoolean) {
    // 解析整数属性值
    int value = parseIntegerAttribute(data);
    // 如果属性是布尔类型，则将值限制为 0 或 1
    if (isBoolean) {
      value = min(1, value);
    }
    // 如果解析成功（值 >= 0），则根据 isTextInformationFrame 返回相应的帧
    if (value >= 0) {
      return isTextInformationFrame
          ? new TextInformationFrame(
          id, /* 描述= */ null, ImmutableList.of(Integer.toString(value)))
          : new CommentFrame(C.LANGUAGE_UNDETERMINED, id, Integer.toString(value));
    }
    // 如果解析失败，记录警告日志并返回 null
    Log.w(TAG, "解析 uint8 属性失败: " + Mp4Box.getBoxTypeString(type));
    return null;
  }

  private static int parseIntegerAttribute(ParsableByteArray data) {
    // 读取当前原子的尺寸
    int atomSize = data.readInt();
    // 读取当前原子的类型
    int atomType = data.readInt();
    // 如果原子类型是 "data"，则解析整数属性
    if (atomType == Mp4Box.TYPE_data) {
      // 跳过版本 (1 字节)、标志 (3 字节) 和空字段 (4 字节)
      data.skipBytes(8);
      // 根据数据大小选择解析方式
      switch (atomSize - 16) { // 减去原子头部的大小 (16 字节)
        case 1: // 如果数据大小为 1 字节，则读取无符号字节
          return data.readUnsignedByte();
        case 2: // 如果数据大小为 2 字节，则读取无符号短整型
          return data.readUnsignedShort();
        case 3: // 如果数据大小为 3 字节，则读取无符号 24 位整数
          return data.readUnsignedInt24();
        case 4: // 如果数据大小为 4 字节，则读取无符号整数
          if ((data.peekUnsignedByte() & 0x80) == 0) { // 检查最高位是否为 0（非负数）
            return data.readUnsignedIntToInt();
          }
      }
    }
    // 如果解析失败，记录警告日志并返回 -1
    Log.w(TAG, "解析数据原子为整数失败");
    return -1;
  }

  @Nullable
  private static TextInformationFrame parseIndexAndCountAttribute(
      int type, String attributeName, ParsableByteArray data) {
    // 读取当前原子的尺寸
    int atomSize = data.readInt();
    // 读取当前原子的类型
    int atomType = data.readInt();
    // 如果原子类型是 "data" 并且原子大小至少为 22 字节，则解析索引/计数属性
    if (atomType == Mp4Box.TYPE_data && atomSize >= 22) {
      // 跳过版本 (1 字节)、标志 (3 字节)、空字段 (4 字节) 和空字段 (2 字节)
      data.skipBytes(10);
      // 读取索引值（无符号短整型）
      int index = data.readUnsignedShort();
      // 如果索引值大于 0，则构建属性值
      if (index > 0) {
        String value = "" + index;
        // 读取计数值（无符号短整型）
        int count = data.readUnsignedShort();
        // 如果计数值大于 0，则将计数添加到属性值中
        if (count > 0) {
          value += "/" + count;
        }
        // 返回一个 TextInformationFrame 对象，包含属性名称和值
        return new TextInformationFrame(
            attributeName, /* 描述= */ null, ImmutableList.of(value));
      }
    }
    // 如果解析失败，记录警告日志并返回 null
    Log.w(TAG, "解析索引/计数属性失败: " + Mp4Box.getBoxTypeString(type));
    return null;
  }

  @Nullable
  private static TextInformationFrame parseStandardGenreAttribute(ParsableByteArray data) {
    // 解析流派代码
    int genreCode = parseIntegerAttribute(data);
    // ID3 标签的流派代码是零索引的，但 MP4 gnre 代码是 1 索引的（流派列表是相同的）
    @Nullable String genreString = Id3Util.resolveV1Genre(genreCode - 1);
    // 如果成功解析流派字符串，则返回一个 TextInformationFrame 对象
    if (genreString != null) {
      return new TextInformationFrame(
          "TCON", /* 描述= */ null, ImmutableList.of(genreString));
    }
    // 如果解析失败，记录警告日志并返回 null
    Log.w(TAG, "解析标准流派代码失败");
    return null;
  }

  @Nullable
  private static ApicFrame parseCoverArt(ParsableByteArray data) {
    // 读取当前原子的尺寸
    int atomSize = data.readInt();
    // 读取当前原子的类型
    int atomType = data.readInt();
    // 如果原子类型是 "data"，则解析封面图片属性
    if (atomType == Mp4Box.TYPE_data) {
      // 读取完整的版本和标志字段
      int fullVersionInt = data.readInt();
      // 解析标志字段
      int flags = BoxParser.parseFullBoxFlags(fullVersionInt);
      // 根据标志字段确定图片的 MIME 类型
      @Nullable String mimeType = flags == 13 ? "image/jpeg" : flags == 14 ? "image/png" : null;
      // 如果 MIME 类型无法识别，记录警告日志并返回 null
      if (mimeType == null) {
        Log.w(TAG, "无法识别的封面图片标志: " + flags);
        return null;
      }
      // 跳过空字段 (4 字节)
      data.skipBytes(4);
      // 读取图片数据
      byte[] pictureData = new byte[atomSize - 16]; // 减去原子头部的大小 (16 字节)
      data.readBytes(pictureData, 0, pictureData.length);
      // 返回一个 ApicFrame 对象，包含 MIME 类型、描述、图片类型和图片数据
      return new ApicFrame(
          mimeType,
          /* 描述= */ null,
          /* 图片类型= */ PICTURE_TYPE_FRONT_COVER,
          pictureData);
    }
    // 如果解析失败，记录警告日志并返回 null
    Log.w(TAG, "解析封面图片属性失败");
    return null;
  }

  @Nullable
  private static Id3Frame parseInternalAttribute(ParsableByteArray data, int endPosition) {
    // 初始化域、名称和数据原子的位置和大小
    @Nullable String domain = null;
    @Nullable String name = null;
    int dataAtomPosition = -1;
    int dataAtomSize = -1;
    // 遍历数据，直到达到结束位置
    while (data.getPosition() < endPosition) {
      // 记录当前原子的位置
      int atomPosition = data.getPosition();
      // 读取当前原子的尺寸
      int atomSize = data.readInt();
      // 读取当前原子的类型
      int atomType = data.readInt();
      // 跳过版本 (1 字节) 和标志 (3 字节)
      data.skipBytes(4);
      // 根据原子类型处理数据
      if (atomType == Mp4Box.TYPE_mean) {
        // 如果原子类型是 "mean"，则读取域字符串
        domain = data.readNullTerminatedString(atomSize - 12);
      } else if (atomType == Mp4Box.TYPE_name) {
        // 如果原子类型是 "name"，则读取名称字符串
        name = data.readNullTerminatedString(atomSize - 12);
      } else {
        if (atomType == Mp4Box.TYPE_data) {
          // 如果原子类型是 "data"，则记录数据原子的位置和大小
          dataAtomPosition = atomPosition;
          dataAtomSize = atomSize;
        }
        // 跳过剩余的数据
        data.skipBytes(atomSize - 12);
      }
    }
    // 如果域、名称或数据原子的位置未找到，则返回 null
    if (domain == null || name == null || dataAtomPosition == -1) {
      return null;
    }
    // 定位到数据原子的位置
    data.setPosition(dataAtomPosition);
    // 跳过数据原子的头部（大小 (4), 类型 (4), 版本 (1), 标志 (3), 空 (4)）
    data.skipBytes(16);
    // 读取以 null 结尾的字符串作为属性值
    String value = data.readNullTerminatedString(dataAtomSize - 16);
    // 返回一个 InternalFrame 对象，包含域、名称和值
    return new InternalFrame(domain, name, value);
  }
}
