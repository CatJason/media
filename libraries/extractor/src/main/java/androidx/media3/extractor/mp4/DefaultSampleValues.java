package androidx.media3.extractor.mp4;

/* package */ final class DefaultSampleValues {

  // 样本描述索引，用于标识样本的描述信息（如编码格式、分辨率等）
  public final int sampleDescriptionIndex;

  // 样本的持续时间（以时间单位表示，如毫秒或时间戳）
  public final int duration;

  // 样本的大小（以字节为单位）
  public final int size;

  // 样本的标志位，用于描述样本的特定属性（如关键帧、同步帧等）
  public final int flags;

  /**
   * 构造函数，用于初始化 DefaultSampleValues 的各个字段。
   *
   * @param sampleDescriptionIndex 样本描述索引
   * @param duration 样本的持续时间
   * @param size 样本的大小
   * @param flags 样本的标志位
   */
  public DefaultSampleValues(int sampleDescriptionIndex, int duration, int size, int flags) {
    this.sampleDescriptionIndex = sampleDescriptionIndex;
    this.duration = duration;
    this.size = size;
    this.flags = flags;
  }
}