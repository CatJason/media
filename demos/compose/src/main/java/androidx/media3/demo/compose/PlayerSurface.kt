package androidx.media3.demo.compose

import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.IntDef
import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.AndroidExternalSurfaceScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.Player

/**
 * 为使用 [Player] 的媒体播放提供一个专用的绘制 [Surface]。
 *
 * 播放器的视频输出可以通过 [SurfaceView]/[AndroidExternalSurface] 或 [TextureView]/[AndroidEmbeddedExternalSurface] 显示。
 *
 * [Player] 负责将渲染输出附加到 [Surface]，并在其销毁时清理它。
 *
 * 更多信息，请参阅
 * [选择表面类型](https://developer.android.com/media/media3/ui/playerview#surfacetype)。
 */
@Composable
fun PlayerSurface(player: Player, surfaceType: @SurfaceType Int, modifier: Modifier = Modifier) {
  val onSurfaceCreated: (Surface) -> Unit = { surface -> player.setVideoSurface(surface) }
  val onSurfaceDestroyed: () -> Unit = { player.setVideoSurface(null) }
  val onSurfaceInitialized: AndroidExternalSurfaceScope.() -> Unit = {
    onSurface { surface, _, _ ->
      onSurfaceCreated(surface)
      surface.onDestroyed { onSurfaceDestroyed() }
    }
  }

  when (surfaceType) {
    SURFACE_TYPE_SURFACE_VIEW ->
      AndroidExternalSurface(modifier = modifier, onInit = onSurfaceInitialized)
    SURFACE_TYPE_TEXTURE_VIEW ->
      AndroidEmbeddedExternalSurface(modifier = modifier, onInit = onSurfaceInitialized)
    else -> throw IllegalArgumentException("Unrecognized surface type: $surfaceType")
  }
}

/**
 * 用于媒体播放的表面视图类型。取值为 [SURFACE_TYPE_SURFACE_VIEW] 或 [SURFACE_TYPE_TEXTURE_VIEW] 之一。
 */
@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
@IntDef(SURFACE_TYPE_SURFACE_VIEW, SURFACE_TYPE_TEXTURE_VIEW)
annotation class SurfaceType

/** Surface type equivalent to [SurfaceView] . */
const val SURFACE_TYPE_SURFACE_VIEW = 1
/** Surface type equivalent to [TextureView]. */
const val SURFACE_TYPE_TEXTURE_VIEW = 2
