package androidx.media3.exoplayer;

import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2.ControllerCallback;
import android.media.MediaRouter2.RouteCallback;
import android.media.MediaRouter2.RoutingController;
import android.media.RouteDiscoveryPreference;
import android.media.RoutingSessionInfo;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.Executor;

/** {@link SuitableOutputChecker} 的默认实现。 */
@RequiresApi(35) // 需要 Android API 35 及以上版本
/* package */ final class DefaultSuitableOutputChecker implements SuitableOutputChecker {

  // 空的路由发现偏好，用于禁用路由发现
  private static final RouteDiscoveryPreference EMPTY_DISCOVERY_PREFERENCE =
      new RouteDiscoveryPreference.Builder(
          /* preferredFeatures= */ ImmutableList.of(), /* activeScan= */ false)
          .build();

  private final MediaRouter2 router; // MediaRouter2 实例
  private final RouteCallback routeCallback; // 路由回调
  private final Executor executor; // 执行器

  @Nullable private ControllerCallback controllerCallback; // 控制器回调
  private boolean isPreviousSelectedOutputSuitableForPlayback; // 记录上一次选择的输出是否适合播放

  // 构造函数
  public DefaultSuitableOutputChecker(Context context, Handler eventHandler) {
    router = MediaRouter2.getInstance(context); // 获取 MediaRouter2 实例
    routeCallback = new RouteCallback() {}; // 初始化路由回调
    executor =
        new Executor() {
          @Override
          public void execute(Runnable command) {
            Util.postOrRun(eventHandler, command); // 使用事件处理器执行任务
          }
        };
  }

  // 启用 SuitableOutputChecker
  @Override
  public void enable(Callback callback) {
    // 注册路由回调
    router.registerRouteCallback(executor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);
    // 初始化控制器回调
    controllerCallback =
        new ControllerCallback() {
          @Override
          public void onControllerUpdated(RoutingController controller) {
            // 检查当前选择的输出是否适合播放
            boolean isCurrentSelectedOutputSuitableForPlayback =
                isSelectedOutputSuitableForPlayback();
            // 如果状态发生变化，通知回调
            if (isPreviousSelectedOutputSuitableForPlayback
                != isCurrentSelectedOutputSuitableForPlayback) {
              isPreviousSelectedOutputSuitableForPlayback =
                  isCurrentSelectedOutputSuitableForPlayback;
              callback.onSelectedOutputSuitabilityChanged(
                  isCurrentSelectedOutputSuitableForPlayback);
            }
          }
        };
    // 注册控制器回调
    router.registerControllerCallback(executor, controllerCallback);
    // 初始化记录当前选择的输出是否适合播放
    isPreviousSelectedOutputSuitableForPlayback = isSelectedOutputSuitableForPlayback();
  }

  // 禁用 SuitableOutputChecker
  @Override
  public void disable() {
    checkStateNotNull(controllerCallback, "SuitableOutputChecker is not enabled");
    // 注销控制器回调
    router.unregisterControllerCallback(controllerCallback);
    controllerCallback = null;
    // 注销路由回调
    router.unregisterRouteCallback(routeCallback);
  }

  // 检查当前选择的输出是否适合播放
  @Override
  public boolean isSelectedOutputSuitableForPlayback() {
    checkStateNotNull(controllerCallback, "SuitableOutputChecker is not enabled");
    // 获取传输原因
    int transferReason = router.getSystemController().getRoutingSessionInfo().getTransferReason();
    // 检查传输是否由自身发起
    boolean wasTransferInitiatedBySelf = router.getSystemController().wasTransferInitiatedBySelf();
    // 遍历所有选定的路由，检查是否适合媒体播放
    for (MediaRoute2Info routeInfo : router.getSystemController().getSelectedRoutes()) {
      if (isRouteSuitableForMediaPlayback(routeInfo, transferReason, wasTransferInitiatedBySelf)) {
        return true;
      }
    }
    return false;
  }

  // 检查路由是否适合媒体播放
  private static boolean isRouteSuitableForMediaPlayback(
      MediaRoute2Info routeInfo, int transferReason, boolean wasTransferInitiatedBySelf) {
    int suitabilityStatus = routeInfo.getSuitabilityStatus(); // 获取路由的适用状态

    // 如果路由状态为适合手动传输
    if (suitabilityStatus == MediaRoute2Info.SUITABILITY_STATUS_SUITABLE_FOR_MANUAL_TRANSFER) {
      return (transferReason == RoutingSessionInfo.TRANSFER_REASON_SYSTEM_REQUEST
          || transferReason == RoutingSessionInfo.TRANSFER_REASON_APP)
          && wasTransferInitiatedBySelf;
    }

    // 如果路由状态为适合默认传输
    return suitabilityStatus == MediaRoute2Info.SUITABILITY_STATUS_SUITABLE_FOR_DEFAULT_TRANSFER;
  }
}