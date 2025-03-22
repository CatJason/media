package androidx.media3.extractor.mp4;

import static androidx.media3.extractor.Extractor.RESULT_SEEK;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.media3.common.C;
import androidx.media3.common.Metadata;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.metadata.mp4.SlowMotionData;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Samsung Extension Format (SEF) metadata.
 *
 * <p>To be used in conjunction with {@link Mp4Extractor}.
 */
/* package */ final class SefReader {

  /**
   * 读取器状态。
   * <p>
   * 这是一个注解，用于定义读取器的状态。
   * 使用 {@link IntDef} 注解限制状态为指定的常量值。
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE) // 表示该注解仅在源码中保留，不会编译到字节码中
  @Target(TYPE_USE) // 表示该注解可以用于类型声明
  @IntDef({
      STATE_SHOULD_CHECK_FOR_SEF, // 应检查是否存在 SEF 数据的状态
      STATE_CHECKING_FOR_SEF,     // 正在检查是否存在 SEF 数据的状态
      STATE_READING_SDRS,         // 正在读取 SDRs（Slow Data Records）数据的状态
      STATE_READING_SEF_DATA      // 正在读取 SEF（Samsung Extended Format）数据的状态
  })
  private @interface State {
    // 这是一个空接口，仅用于定义读取器状态注解
  }

  private static final int STATE_SHOULD_CHECK_FOR_SEF = 0;
  private static final int STATE_CHECKING_FOR_SEF = 1;
  private static final int STATE_READING_SDRS = 2;
  private static final int STATE_READING_SEF_DATA = 3;

  /**
   * 支持的数据类型。
   * <p>
   * 这是一个注解，用于定义支持的 SEF 数据类型。
   * 使用 {@link IntDef} 注解限制数据类型为指定的常量值。
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE) // 表示该注解仅在源码中保留，不会编译到字节码中
  @Target(TYPE_USE) // 表示该注解可以用于类型声明
  @IntDef({
      TYPE_SLOW_MOTION_DATA,          // 慢动作数据类型
      TYPE_SUPER_SLOW_MOTION_DATA,    // 超级慢动作数据类型
      TYPE_SUPER_SLOW_MOTION_BGM,     // 超级慢动作背景音乐数据类型
      TYPE_SUPER_SLOW_MOTION_EDIT_DATA, // 超级慢动作编辑数据类型
      TYPE_SUPER_SLOW_DEFLICKERING_ON // 超级慢动作去闪烁开启数据类型
  })
  private @interface DataType {
    // 这是一个空接口，仅用于定义数据类型注解
  }

  private static final int TYPE_SLOW_MOTION_DATA = 0x0890; // 2192
  private static final int TYPE_SUPER_SLOW_MOTION_DATA = 0x0b00; // 2816
  private static final int TYPE_SUPER_SLOW_MOTION_BGM = 0x0b01; // 2817
  private static final int TYPE_SUPER_SLOW_MOTION_EDIT_DATA = 0x0b03; // 2819
  private static final int TYPE_SUPER_SLOW_DEFLICKERING_ON = 0x0b04; // 2820

  private static final String TAG = "SefReader";

  /**
   * {@code SEFT} 的整数表示（ASCII 编码）。
   *
   * <p>这是包含三星扩展格式（SEF）数据的文件的最后 4 个字节。
   */
  private static final int SAMSUNG_TAIL_SIGNATURE = 0x53454654;

  /**
   * 起始签名（4 字节），SEF 版本（4 字节），SDR 数量（4 字节）。
   */
  private static final int TAIL_HEADER_LENGTH = 12;

  /**
   * 尾部偏移量（4 字节），尾部签名（4 字节）。
   */
  private static final int TAIL_FOOTER_LENGTH = 8;

  private static final int LENGTH_OF_ONE_SDR = 12;
  private static final Splitter COLON_SPLITTER = Splitter.on(':');
  private static final Splitter ASTERISK_SPLITTER = Splitter.on('*');

  private final List<DataReference> dataReferences;
  private @State int readerState;
  private int tailLength;

  public SefReader() {
    dataReferences = new ArrayList<>();
    readerState = STATE_SHOULD_CHECK_FOR_SEF;
  }

  public void reset() {
    dataReferences.clear();
    readerState = STATE_SHOULD_CHECK_FOR_SEF;
  }

  public @Extractor.ReadResult int read(
      ExtractorInput input,
      PositionHolder seekPosition,
      List<Metadata.Entry> slowMotionMetadataEntries)
      throws IOException {
    switch (readerState) {
      case STATE_SHOULD_CHECK_FOR_SEF:
        long inputLength = input.getLength();
        seekPosition.position =
            inputLength == C.LENGTH_UNSET || inputLength < TAIL_FOOTER_LENGTH
                ? 0
                : inputLength - TAIL_FOOTER_LENGTH;
        readerState = STATE_CHECKING_FOR_SEF;
        break;
      case STATE_CHECKING_FOR_SEF:
        checkForSefData(input, seekPosition);
        break;
      case STATE_READING_SDRS:
        readSdrs(input, seekPosition);
        break;
      case STATE_READING_SEF_DATA:
        readSefData(input, slowMotionMetadataEntries);
        seekPosition.position = 0;
        break;
      default:
        throw new IllegalStateException();
    }
    return RESULT_SEEK;
  }

  private void checkForSefData(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    // 创建一个可解析的字节数组，用于存储尾部数据
    ParsableByteArray scratch = new ParsableByteArray(/* limit= */ TAIL_FOOTER_LENGTH);
    // 从输入流中读取完整的尾部数据
    input.readFully(scratch.getData(), /* offset= */ 0, /* length= */ TAIL_FOOTER_LENGTH);
    // 解析尾部长度（尾部数据长度 + 尾部尾部长度）
    tailLength = scratch.readLittleEndianInt() + TAIL_FOOTER_LENGTH;
    // 检查尾部签名是否匹配
    if (scratch.readInt() != SAMSUNG_TAIL_SIGNATURE) {
      // 如果不匹配，设置 seekPosition 为 0 并返回
      seekPosition.position = 0;
      return;
    }

    // 计算尾部数据的起始位置
    // input.getPosition 当前位于尾部的末尾，因此向前跳转 tailLength，但需要忽略尾部头部
    seekPosition.position = input.getPosition() - (tailLength - TAIL_HEADER_LENGTH);
    // 更新读取器状态为“正在读取 SDRs”
    readerState = STATE_READING_SDRS;
  }

  private void readSdrs(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    // 获取输入流的长度
    long streamLength = input.getLength();
    // 计算 SDRs（Slow Data Records）的长度
    int sdrsLength = tailLength - TAIL_HEADER_LENGTH - TAIL_FOOTER_LENGTH;
    // 创建一个可解析的字节数组，用于存储 SDRs 数据
    ParsableByteArray scratch = new ParsableByteArray(/* limit= */ sdrsLength);
    // 从输入流中读取完整的 SDRs 数据
    input.readFully(scratch.getData(), /* offset= */ 0, /* length= */ sdrsLength);

    // 遍历每个 SDR
    for (int i = 0; i < sdrsLength / LENGTH_OF_ONE_SDR; i++) {
      // 跳过 SDR 数据子信息标志和保留位（2 字节）
      scratch.skipBytes(2);
      // 读取数据类型（2 字节，小端序）
      @DataType int dataType = scratch.readLittleEndianShort();
      // 根据数据类型进行处理
      switch (dataType) {
        case TYPE_SLOW_MOTION_DATA:
        case TYPE_SUPER_SLOW_MOTION_DATA:
        case TYPE_SUPER_SLOW_MOTION_BGM:
        case TYPE_SUPER_SLOW_MOTION_EDIT_DATA:
        case TYPE_SUPER_SLOW_DEFLICKERING_ON:
          // 读取一个整数，表示从尾部信息到元数据起始位置的距离
          // 通过从流末尾向前计算偏移量，得到元数据的起始位置
          long startOffset = streamLength - tailLength - scratch.readLittleEndianInt();
          // 读取元数据的大小
          int size = scratch.readLittleEndianInt();
          // 将数据引用添加到列表中
          dataReferences.add(new DataReference(dataType, startOffset, size));
          break;
        default:
          // 对于不支持的数据类型，跳过起始位置和大小字段（8 字节）
          scratch.skipBytes(8);
      }
    }

    // 如果没有找到任何数据引用，则设置 seekPosition 为 0 并返回
    if (dataReferences.isEmpty()) {
      seekPosition.position = 0;
      return;
    }

    // 更新读取器状态为“正在读取 SEF 数据”
    readerState = STATE_READING_SEF_DATA;
    // 设置 seekPosition 为第一个数据引用的起始位置
    seekPosition.position = dataReferences.get(0).startOffset;
  }

  private void readSefData(ExtractorInput input, List<Metadata.Entry> slowMotionMetadataEntries)
      throws IOException {
    // 记录数据起始位置
    long dataStartOffset = input.getPosition();
    // 计算总数据长度（文件总长度减去当前读取位置，再减去尾部长度）
    int totalDataLength = (int) (input.getLength() - input.getPosition() - tailLength);
    // 创建一个可解析的字节数组，用于存储数据
    ParsableByteArray data = new ParsableByteArray(/* limit= */ totalDataLength);
    // 从输入流中读取完整的数据到字节数组中
    input.readFully(data.getData(), 0, totalDataLength);

    // 遍历所有数据引用
    for (int i = 0; i < dataReferences.size(); i++) {
      DataReference dataReference = dataReferences.get(i);
      // 计算目标数据在字节数组中的位置
      int intendedPosition = (int) (dataReference.startOffset - dataStartOffset);
      // 设置字节数组的读取位置
      data.setPosition(intendedPosition);

      // 跳过数据类型和数据子信息字段（4 字节）
      data.skipBytes(4);
      // 读取名称长度
      int nameLength = data.readLittleEndianInt();
      // 读取名称
      String name = data.readString(nameLength);
      // 根据名称解析数据类型
      @DataType int dataType = nameToDataType(name);

      // 计算剩余数据的长度
      int remainingDataLength = dataReference.size - (8 + nameLength);
      // 根据数据类型处理数据
      switch (dataType) {
        case TYPE_SLOW_MOTION_DATA:
          // 读取慢动作数据并添加到元数据条目列表中
          slowMotionMetadataEntries.add(readSlowMotionData(data, remainingDataLength));
          break;
        case TYPE_SUPER_SLOW_MOTION_DATA:
        case TYPE_SUPER_SLOW_MOTION_BGM:
        case TYPE_SUPER_SLOW_MOTION_EDIT_DATA:
        case TYPE_SUPER_SLOW_DEFLICKERING_ON:
          // 对于这些数据类型，暂时不处理
          break;
        default:
          // 如果遇到不支持的数据类型，抛出异常
          throw new IllegalStateException();
      }
    }
  }

  private static SlowMotionData readSlowMotionData(ParsableByteArray data, int dataLength)
      throws ParserException {
    // 创建一个列表，用于存储慢动作数据的分段
    List<SlowMotionData.Segment> segments = new ArrayList<>();
    // 读取指定长度的字符串数据
    String dataString = data.readString(dataLength);
    // 使用星号分隔符将字符串分割为多个分段字符串
    List<String> segmentStrings = ASTERISK_SPLITTER.splitToList(dataString);
    // 遍历每个分段字符串
    for (int i = 0; i < segmentStrings.size(); i++) {
      // 使用冒号分隔符将分段字符串分割为多个值
      List<String> values = COLON_SPLITTER.splitToList(segmentStrings.get(i));
      // 如果值的数量不等于 3，则抛出异常
      if (values.size() != 3) {
        throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null);
      }
      try {
        // 解析起始时间（毫秒）
        long startTimeMs = Long.parseLong(values.get(0));
        // 解析结束时间（毫秒）
        long endTimeMs = Long.parseLong(values.get(1));
        // 解析速度模式
        int speedMode = Integer.parseInt(values.get(2));
        // 计算速度除数
        int speedDivisor = 1 << (speedMode - 1);
        // 将分段添加到列表中
        segments.add(new SlowMotionData.Segment(startTimeMs, endTimeMs, speedDivisor));
      } catch (NumberFormatException e) {
        // 如果解析失败，抛出异常
        throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ e);
      }
    }
    // 返回包含所有分段的慢动作数据对象
    return new SlowMotionData(segments);
  }

  private static @DataType int nameToDataType(String name) throws ParserException {
    // 根据名称返回对应的数据类型
    switch (name) {
      // 如果名称是 "SlowMotion_Data"，返回慢动作数据类型
      case "SlowMotion_Data":
        return TYPE_SLOW_MOTION_DATA;
      // 如果名称是 "Super_SlowMotion_Data"，返回超级慢动作数据类型
      case "Super_SlowMotion_Data":
        return TYPE_SUPER_SLOW_MOTION_DATA;
      // 如果名称是 "Super_SlowMotion_BGM"，返回超级慢动作背景音乐数据类型
      case "Super_SlowMotion_BGM":
        return TYPE_SUPER_SLOW_MOTION_BGM;
      // 如果名称是 "Super_SlowMotion_Edit_Data"，返回超级慢动作编辑数据类型
      case "Super_SlowMotion_Edit_Data":
        return TYPE_SUPER_SLOW_MOTION_EDIT_DATA;
      // 如果名称是 "Super_SlowMotion_Deflickering_On"，返回超级慢动作去闪烁开启数据类型
      case "Super_SlowMotion_Deflickering_On":
        return TYPE_SUPER_SLOW_DEFLICKERING_ON;
      // 如果名称不匹配任何已知类型，抛出异常
      default:
        throw ParserException.createForMalformedContainer("无效的 SEF 名称", /* cause= */ null);
    }
  }

  private static final class DataReference {

    public final @DataType int dataType;
    public final long startOffset;
    public final int size;

    public DataReference(@DataType int dataType, long startOffset, int size) {
      this.dataType = dataType;
      this.startOffset = startOffset;
      this.size = size;
    }
  }
}
