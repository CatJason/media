package androidx.media3.exoplayer;

import android.content.Context;
import androidx.media3.common.util.SystemClock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import java.util.Arrays;

/** 默认的 {@link RendererCapabilitiesList} 实现。 */
@UnstableApi
public final class DefaultRendererCapabilitiesList implements RendererCapabilitiesList {

  /** {@link DefaultRendererCapabilitiesList} 的工厂类。 */
  public static final class Factory implements RendererCapabilitiesList.Factory {
    private final RenderersFactory renderersFactory;

    /**
     * 创建实例。
     *
     * @param context 用于创建 {@link DefaultRenderersFactory} 的上下文，该工厂将作为默认工厂使用。
     */
    public Factory(Context context) {
      this.renderersFactory = new DefaultRenderersFactory(context);
    }

    /**
     * 创建实例。
     *
     * @param renderersFactory 用于创建 {@linkplain Renderer 渲染器} 数组的 {@link RenderersFactory}，
     *     这些渲染器的 {@link RendererCapabilities} 将由 {@link DefaultRendererCapabilitiesList} 表示。
     */
    public Factory(RenderersFactory renderersFactory) {
      this.renderersFactory = renderersFactory;
    }

    @Override
    public DefaultRendererCapabilitiesList createRendererCapabilitiesList() {
      // 创建渲染器数组
      Renderer[] renderers =
          renderersFactory.createRenderers(
              Util.createHandlerForCurrentOrMainLooper(), // 创建与当前或主线程关联的 Handler
              new VideoRendererEventListener() {}, // 视频渲染器事件监听器
              new AudioRendererEventListener() {}, // 音频渲染器事件监听器
              cueGroup -> {}, // 文本输出
              metadata -> {} // 元数据输出
          );
      return new DefaultRendererCapabilitiesList(renderers);
    }
  }

  private final Renderer[] renderers;

  private DefaultRendererCapabilitiesList(Renderer[] renderers) {
    // 复制渲染器数组
    this.renderers = Arrays.copyOf(renderers, renderers.length);
    // 初始化每个渲染器
    for (int i = 0; i < renderers.length; i++) {
      this.renderers[i].init(i, PlayerId.UNSET, SystemClock.DEFAULT);
    }
  }

  @Override
  public RendererCapabilities[] getRendererCapabilities() {
    // 获取每个渲染器的能力
    RendererCapabilities[] rendererCapabilities = new RendererCapabilities[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      rendererCapabilities[i] = renderers[i].getCapabilities();
    }
    return rendererCapabilities;
  }

  @Override
  public int size() {
    // 返回渲染器的数量
    return renderers.length;
  }

  @Override
  public void release() {
    // 释放所有渲染器
    for (Renderer renderer : renderers) {
      renderer.release();
    }
  }
}