package androidx.media3.extractor.mp4;

import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.Mp4Box;
import androidx.media3.extractor.metadata.mp4.SmtaMetadataEntry;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Utility methods for handling SMTA atoms.
 *
 * <p>See [Internal: b/150138465#comment76], [Internal: b/301273734#comment17].
 */
@UnstableApi
public final class SmtaAtomUtil {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      open = true,
      value = {
          NO_VALUE,
          CAMCORDER_NORMAL,
          CAMCORDER_SINGLE_SUPERSLOW_MOTION,
          CAMCORDER_FRC_SUPERSLOW_MOTION,
          CAMCORDER_SLOW_MOTION_V2,
          CAMCORDER_SLOW_MOTION_V2_120,
          CAMCORDER_SLOW_MOTION_V2_HEVC,
          CAMCORDER_FRC_SUPERSLOW_MOTION_HEVC,
          CAMCORDER_QFRC_SUPERSLOW_MOTION,
      })
  private @interface RecordingMode {

  }

  private static final int NO_VALUE = -1; // 未设置的值，表示无效或未知
  private static final int CAMCORDER_NORMAL = 0; // 普通录制模式
  private static final int CAMCORDER_SINGLE_SUPERSLOW_MOTION = 7; // 单次超级慢动作录制模式
  private static final int CAMCORDER_FRC_SUPERSLOW_MOTION = 9; // FRC（帧率控制）超级慢动作录制模式
  private static final int CAMCORDER_SLOW_MOTION_V2 = 12; // 慢动作 V2 录制模式
  private static final int CAMCORDER_SLOW_MOTION_V2_120 = 13; // 慢动作 V2 120 fps 录制模式
  private static final int CAMCORDER_SLOW_MOTION_V2_HEVC = 21; // 慢动作 V2 HEVC 编码录制模式
  private static final int CAMCORDER_FRC_SUPERSLOW_MOTION_HEVC = 22; // FRC 超级慢动作 HEVC 编码录制模式
  private static final int CAMCORDER_QFRC_SUPERSLOW_MOTION = 23; // QFRC（高质量帧率控制）超级慢动作录制模式
  private SmtaAtomUtil() {
  }

  /**
   * 从三星的 smta 原子中解析元数据。
   */
  @Nullable
  public static Metadata parseSmta(ParsableByteArray smta, int limit) {
    // 跳过 smta 原子的头部（大小和类型字段）
    smta.skipBytes(Mp4Box.FULL_HEADER_SIZE);
    // 遍历 smta 原子的内容，直到达到限制位置
    while (smta.getPosition() < limit) {
      // 记录当前原子的起始位置
      int atomPosition = smta.getPosition();
      // 读取当前原子的大小
      int atomSize = smta.readInt();
      // 读取当前原子的类型
      int atomType = smta.readInt();
      // 如果原子类型是 'saut'，则解析元数据
      if (atomType == Mp4Box.TYPE_saut) {
        // 检查原子大小是否足够（至少 16 字节）
        if (atomSize < 16) {
          return null;
        }
        // 跳过作者字段（4 字节）
        smta.skipBytes(4);

        // 每个字段以键值对的形式存储（1 字节键，1 字节值）
        // 字段的顺序不固定
        @RecordingMode int recordingMode = NO_VALUE; // 录制模式
        int svcTemporalLayerCount = 0; // SVC 时间层数量
        for (int i = 0; i < 2; i++) {
          int key = smta.readUnsignedByte(); // 读取键
          int value = smta.readUnsignedByte(); // 读取值
          if (key == 0x00) { // 录制模式键
            recordingMode = value;
          } else if (key == 0x01) { // SVC 时间层数量键
            svcTemporalLayerCount = value;
          }
        }

        // 获取捕获帧率
        int captureFrameRate = getCaptureFrameRate(recordingMode, smta, limit);
        if (captureFrameRate == C.RATE_UNSET_INT) {
          return null;
        }

        // 返回解析出的元数据
        return new Metadata(new SmtaMetadataEntry(captureFrameRate, svcTemporalLayerCount));
      }
      // 跳转到下一个原子的起始位置
      smta.setPosition(atomPosition + atomSize);
    }
    // 如果没有找到有效的元数据，返回 null
    return null;
  }

  /**
   * 返回给定录制模式的捕获帧率（如果支持）。
   *
   * <p>对于 {@link #CAMCORDER_SLOW_MOTION_V2_HEVC}，这是通过解析三星的 'srfr' 原子实现的。
   *
   * @return 捕获帧率值，如果不可用则返回 {@link C#RATE_UNSET_INT}。
   */
  private static int getCaptureFrameRate(
      @RecordingMode int recordingMode, ParsableByteArray smta, int limit) {
    // 对于 V2 和 V2_120 模式，捕获帧率是固定的
    if (recordingMode == CAMCORDER_SLOW_MOTION_V2) {
      return 240; // V2 模式的捕获帧率为 240 fps
    } else if (recordingMode == CAMCORDER_SLOW_MOTION_V2_120) {
      return 120; // V2_120 模式的捕获帧率为 120 fps
    } else if (recordingMode != CAMCORDER_SLOW_MOTION_V2_HEVC) {
      // 如果录制模式不是 V2_HEVC，则返回未设置的值
      return C.RATE_UNSET_INT;
    }

    // 检查剩余字节是否足够读取原子头部
    if (smta.bytesLeft() < Mp4Box.HEADER_SIZE || smta.getPosition() + Mp4Box.HEADER_SIZE > limit) {
      return C.RATE_UNSET_INT;
    }

    // 读取原子大小和类型
    int atomSize = smta.readInt();
    int atomType = smta.readInt();
    // 检查原子大小是否足够，且类型是否为 'srfr'
    if (atomSize < 12 || atomType != Mp4Box.TYPE_srfr) {
      return C.RATE_UNSET_INT;
    }
    // 捕获帧率以 Q16 格式存储，读取并返回
    return smta.readUnsignedFixedPoint1616();
  }
}
