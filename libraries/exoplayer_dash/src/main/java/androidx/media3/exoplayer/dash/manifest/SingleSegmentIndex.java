package androidx.media3.exoplayer.dash.manifest;

import androidx.media3.common.C;
import androidx.media3.exoplayer.dash.DashSegmentIndex;

/** 一个定义了单个分段的 {@link DashSegmentIndex}。 */
/* package */ final class SingleSegmentIndex implements DashSegmentIndex {

  private final RangedUri uri;

  /**
   * @param uri 一个定义了分段数据位置的 {@link RangedUri}。
   */
  public SingleSegmentIndex(RangedUri uri) {
    this.uri = uri;
  }

  @Override
  public long getSegmentNum(long timeUs, long periodDurationUs) {
    return 0;
  }

  @Override
  public long getTimeUs(long segmentNum) {
    return 0;
  }

  @Override
  public long getDurationUs(long segmentNum, long periodDurationUs) {
    return periodDurationUs;
  }

  @Override
  public RangedUri getSegmentUrl(long segmentNum) {
    return uri;
  }

  @Override
  public long getFirstSegmentNum() {
    return 0;
  }

  @Override
  public long getFirstAvailableSegmentNum(long periodDurationUs, long nowUnixTimeUs) {
    return 0;
  }

  @Override
  public long getSegmentCount(long periodDurationUs) {
    return 1;
  }

  @Override
  public long getAvailableSegmentCount(long periodDurationUs, long nowUnixTimeUs) {
    return 1;
  }

  @Override
  public long getNextSegmentAvailableTimeUs(long periodDurationUs, long nowUnixTimeUs) {
    return C.TIME_UNSET;
  }

  @Override
  public boolean isExplicit() {
    return true;
  }
}
