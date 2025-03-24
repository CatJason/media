package androidx.media3.common;

import android.view.ViewGroup;
import androidx.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** 提供广告播放 UI 的视图信息。 */
public interface AdViewProvider {

  /**
   * 返回位于播放器顶部的 {@link ViewGroup}，用于显示任何广告 UI，如果播放的是纯音频广告，
   * 则返回 {@code null}。返回的视图组上的任何视图必须由 {@link #getAdOverlayInfos()} 返回的 {@link AdOverlayInfo AdOverlayInfos} 描述，
   * 以确保准确的可见性测量。
   */
  @Nullable
  ViewGroup getAdViewGroup();

  /**
   * 返回描述位于广告视图组顶部的视图的 {@link AdOverlayInfo} 实例列表，
   * 但这些视图是控制播放所必需的，应从广告可见性测量中排除。
   *
   * <p>每个视图必须是一个完全透明的覆盖层（用于捕获触摸事件），
   * 或者是播放用户体验中必需的一小部分瞬态 UI（例如暂停/恢复播放的按钮或瞬态的全屏或投屏按钮）。
   * 有关更多信息，请参阅您的广告加载器的文档。
   */
  default List<AdOverlayInfo> getAdOverlayInfos() {
    return ImmutableList.of();
  }
}