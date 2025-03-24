package androidx.media3.exoplayer.video;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * 实现了 {@link VideoDecoderOutputBufferRenderer} 的 GLSurfaceView，
 * 用于渲染 {@link VideoDecoderOutputBuffer VideoDecoderOutputBuffers}。
 *
 * <p>此视图仅适用于生成 {@link VideoDecoderOutputBuffer VideoDecoderOutputBuffers} 的解码器。
 * 对于其他用例，应使用 {@link android.view.SurfaceView} 或 {@link android.view.TextureView}。
 */
@UnstableApi
public final class VideoDecoderGLSurfaceView extends GLSurfaceView
    implements VideoDecoderOutputBufferRenderer {

  private static final String TAG = "VideoDecoderGLSV"; // 日志标签

  private final Renderer renderer; // 渲染器实例

  /**
   * @param context 上下文对象。
   */
  public VideoDecoderGLSurfaceView(Context context) {
    this(context, /* attrs= */ null); // 调用另一个构造函数，属性集为 null
  }

  /**
   * @param context 上下文对象。
   * @param attrs   自定义属性集。
   */
  @SuppressWarnings({"nullness:assignment", "nullness:argument", "nullness:method.invocation"})
  public VideoDecoderGLSurfaceView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs); // 调用父类构造函数
    renderer = new Renderer(/* surfaceView= */ this); // 初始化渲染器
    setPreserveEGLContextOnPause(true); // 设置暂停时保留 EGL 上下文
    setEGLContextClientVersion(2); // 设置 EGL 客户端版本为 2（OpenGL ES 2.0）
    setRenderer(renderer); // 设置渲染器
    setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY); // 设置渲染模式为“仅在脏时渲染”
  }

  @Override
  public void setOutputBuffer(VideoDecoderOutputBuffer outputBuffer) {
    renderer.setOutputBuffer(outputBuffer); // 设置渲染器的输出缓冲区
  }

  /**
   * @deprecated 此类直接实现了 {@link VideoDecoderOutputBufferRenderer}。
   */
  @Deprecated
  public VideoDecoderOutputBufferRenderer getVideoDecoderOutputBufferRenderer() {
    return this; // 返回当前实例（已弃用）
  }

  // 内部渲染器类
  private static final class Renderer implements GLSurfaceView.Renderer {

    // BT601 颜色转换矩阵
    private static final float[] kColorConversion601 = {
        1.164f, 1.164f, 1.164f,
        0.0f, -0.392f, 2.017f,
        1.596f, -0.813f, 0.0f,
    };

    // BT709 颜色转换矩阵
    private static final float[] kColorConversion709 = {
        1.164f, 1.164f, 1.164f,
        0.0f, -0.213f, 2.112f,
        1.793f, -0.533f, 0.0f,
    };

    // BT2020 颜色转换矩阵
    private static final float[] kColorConversion2020 = {
        1.168f, 1.168f, 1.168f,
        0.0f, -0.188f, 2.148f,
        1.683f, -0.652f, 0.0f,
    };

    // 顶点着色器代码
    private static final String VERTEX_SHADER =
        "varying vec2 interp_tc_y;\n"
            + "varying vec2 interp_tc_u;\n"
            + "varying vec2 interp_tc_v;\n"
            + "attribute vec4 in_pos;\n"
            + "attribute vec2 in_tc_y;\n"
            + "attribute vec2 in_tc_u;\n"
            + "attribute vec2 in_tc_v;\n"
            + "void main() {\n"
            + "  gl_Position = in_pos;\n"
            + "  interp_tc_y = in_tc_y;\n"
            + "  interp_tc_u = in_tc_u;\n"
            + "  interp_tc_v = in_tc_v;\n"
            + "}\n";

    // 纹理 Uniform 名称
    private static final String[] TEXTURE_UNIFORMS = {"y_tex", "u_tex", "v_tex"};

    // 片段着色器代码
    private static final String FRAGMENT_SHADER =
        "precision mediump float;\n"
            + "varying vec2 interp_tc_y;\n"
            + "varying vec2 interp_tc_u;\n"
            + "varying vec2 interp_tc_v;\n"
            + "uniform sampler2D y_tex;\n"
            + "uniform sampler2D u_tex;\n"
            + "uniform sampler2D v_tex;\n"
            + "uniform mat3 mColorConversion;\n"
            + "void main() {\n"
            + "  vec3 yuv;\n"
            + "  yuv.x = texture2D(y_tex, interp_tc_y).r - 0.0625;\n"
            + "  yuv.y = texture2D(u_tex, interp_tc_u).r - 0.5;\n"
            + "  yuv.z = texture2D(v_tex, interp_tc_v).r - 0.5;\n"
            + "  gl_FragColor = vec4(mColorConversion * yuv, 1.0);\n"
            + "}\n";

    // 纹理顶点坐标
    private static final FloatBuffer TEXTURE_VERTICES =
        GlUtil.createBuffer(new float[]{-1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f});

    private final GLSurfaceView surfaceView; // GLSurfaceView 实例
    private final int[] yuvTextures; // YUV 纹理 ID 数组
    private final int[] texLocations; // 纹理 Uniform 位置数组
    private final int[] previousWidths; // 前一次渲染的宽度数组
    private final int[] previousStrides; // 前一次渲染的步长数组
    private final AtomicReference<@NullableType VideoDecoderOutputBuffer>
        pendingOutputBufferReference; // 待渲染的输出缓冲区引用

    // 纹理坐标缓冲区（作为成员变量以避免被垃圾回收）
    private final FloatBuffer[] textureCoords;

    private @MonotonicNonNull GlProgram program; // OpenGL 程序
    private int colorMatrixLocation; // 颜色矩阵 Uniform 位置

    // 当前渲染的输出缓冲区（仅在 GL 线程中访问）
    private @MonotonicNonNull VideoDecoderOutputBuffer renderedOutputBuffer;

    public Renderer(GLSurfaceView surfaceView) {
      this.surfaceView = surfaceView;
      yuvTextures = new int[3];
      texLocations = new int[3];
      previousWidths = new int[3];
      previousStrides = new int[3];
      pendingOutputBufferReference = new AtomicReference<>();
      textureCoords = new FloatBuffer[3];
      for (int i = 0; i < 3; i++) {
        previousWidths[i] = previousStrides[i] = -1;
      }
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
      try {
        // 创建 OpenGL 程序，使用顶点着色器和片段着色器
        program = new GlProgram(VERTEX_SHADER, FRAGMENT_SHADER);

        // 获取顶点位置属性并启用
        int posLocation = program.getAttributeArrayLocationAndEnable("in_pos");
        // 设置顶点位置数据
        GLES20.glVertexAttribPointer(
            posLocation, // 顶点位置属性位置
            2, // 每个顶点的分量数（2D 坐标）
            GLES20.GL_FLOAT, // 数据类型
            /* normalized= */ false, // 是否归一化
            /* stride= */ 0, // 步长
            TEXTURE_VERTICES); // 顶点数据

        // 获取 Y、U、V 纹理坐标属性并启用
        texLocations[0] = program.getAttributeArrayLocationAndEnable("in_tc_y"); // Y 平面纹理坐标
        texLocations[1] = program.getAttributeArrayLocationAndEnable("in_tc_u"); // U 平面纹理坐标
        texLocations[2] = program.getAttributeArrayLocationAndEnable("in_tc_v"); // V 平面纹理坐标

        // 获取颜色矩阵 Uniform 的位置
        colorMatrixLocation = program.getUniformLocation("mColorConversion");

        // 检查 OpenGL 错误
        GlUtil.checkGlError();

        // 设置纹理
        setupTextures();

        // 再次检查 OpenGL 错误
        GlUtil.checkGlError();
      } catch (GlUtil.GlException e) {
        // 记录设置纹理和程序失败的错误日志
        Log.e(TAG, "Failed to set up the textures and program", e);
      }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
      GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
      // 获取待渲染的输出缓冲区，并将其从引用中清除
      @Nullable
      VideoDecoderOutputBuffer pendingOutputBuffer =
          pendingOutputBufferReference.getAndSet(/* newValue= */ null);

      // 如果没有待渲染的缓冲区且当前也没有正在渲染的缓冲区，则直接返回
      if (pendingOutputBuffer == null && renderedOutputBuffer == null) {
        return;
      }

      // 如果有新的待渲染缓冲区，则替换当前正在渲染的缓冲区
      if (pendingOutputBuffer != null) {
        if (renderedOutputBuffer != null) {
          renderedOutputBuffer.release(); // 释放当前正在渲染的缓冲区
        }
        renderedOutputBuffer = pendingOutputBuffer; // 更新为新的缓冲区
      }

      // 获取当前需要渲染的缓冲区
      VideoDecoderOutputBuffer outputBuffer = checkNotNull(renderedOutputBuffer);

      // 设置颜色矩阵。如果颜色空间未知，则默认使用 BT709
      float[] colorConversion = kColorConversion709;
      switch (outputBuffer.colorspace) {
        case VideoDecoderOutputBuffer.COLORSPACE_BT601:
          colorConversion = kColorConversion601; // 使用 BT601 颜色矩阵
          break;
        case VideoDecoderOutputBuffer.COLORSPACE_BT2020:
          colorConversion = kColorConversion2020; // 使用 BT2020 颜色矩阵
          break;
        case VideoDecoderOutputBuffer.COLORSPACE_BT709:
        default:
          // 默认使用 BT709 颜色矩阵
          break;
      }

      // 将颜色矩阵传递给 OpenGL
      GLES20.glUniformMatrix3fv(
          colorMatrixLocation, // 颜色矩阵的 Uniform 位置
          /* count= */ 1, // 矩阵数量
          /* transpose= */ false, // 是否转置
          colorConversion, // 颜色矩阵数据
          /* offset= */ 0); // 数据偏移量

      // 获取 YUV 数据的步长和平面数据
      int[] yuvStrides = checkNotNull(outputBuffer.yuvStrides);
      ByteBuffer[] yuvPlanes = checkNotNull(outputBuffer.yuvPlanes);

      // 遍历 YUV 的三个平面（Y、U、V）
      for (int i = 0; i < 3; i++) {
        int h = (i == 0) ? outputBuffer.height : (outputBuffer.height + 1) / 2; // 计算每个平面的高度
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i); // 激活当前纹理单元
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[i]); // 绑定纹理
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1); // 设置像素存储对齐方式
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, // 目标纹理
            /* level= */ 0, // 纹理级别
            GLES20.GL_LUMINANCE, // 纹理格式（亮度）
            yuvStrides[i], // 纹理宽度（步长）
            h, // 纹理高度
            /* border= */ 0, // 边框宽度
            GLES20.GL_LUMINANCE, // 像素数据格式
            GLES20.GL_UNSIGNED_BYTE, // 像素数据类型
            yuvPlanes[i]); // 像素数据
      }

      // 计算每个平面的宽度
      int[] widths = new int[3];
      widths[0] = outputBuffer.width; // Y 平面的宽度
      // U 和 V 平面的宽度为 Y 平面宽度的一半
      widths[1] = widths[2] = (widths[0] + 1) / 2;

      // 遍历 YUV 的三个平面，设置纹理裁剪
      for (int i = 0; i < 3; i++) {
        // 如果宽度或步长发生变化，则更新纹理裁剪
        if (previousWidths[i] != widths[i] || previousStrides[i] != yuvStrides[i]) {
          Assertions.checkState(yuvStrides[i] != 0); // 确保步长不为 0
          float widthRatio = (float) widths[i] / yuvStrides[i]; // 计算宽度比例
          // 创建纹理坐标缓冲区
          textureCoords[i] =
              GlUtil.createBuffer(
                  new float[]{0.0f, 0.0f, 0.0f, 1.0f, widthRatio, 0.0f, widthRatio, 1.0f});
          // 将纹理坐标传递给 OpenGL
          GLES20.glVertexAttribPointer(
              texLocations[i], // 纹理坐标的 Attribute 位置
              /* size= */ 2, // 每个坐标的分量数
              GLES20.GL_FLOAT, // 数据类型
              /* normalized= */ false, // 是否归一化
              /* stride= */ 0, // 步长
              textureCoords[i]); // 纹理坐标数据
          previousWidths[i] = widths[i]; // 更新前一个宽度
          previousStrides[i] = yuvStrides[i]; // 更新前一个步长
        }
      }

      // 清除颜色缓冲区
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
      // 绘制三角形条带
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);

      // 检查 OpenGL 错误
      try {
        GlUtil.checkGlError();
      } catch (GlUtil.GlException e) {
        Log.e(TAG, "Failed to draw a frame", e); // 记录绘制帧失败的错误日志
      }
    }

    /**
     * 设置输出缓冲区。
     *
     * @param outputBuffer 要渲染的视频解码器输出缓冲区。
     */
    public void setOutputBuffer(VideoDecoderOutputBuffer outputBuffer) {
      // 获取并替换当前的待渲染缓冲区
      @Nullable
      VideoDecoderOutputBuffer oldPendingOutputBuffer =
          pendingOutputBufferReference.getAndSet(outputBuffer);

      // 如果旧的待渲染缓冲区不为空，则释放它
      if (oldPendingOutputBuffer != null) {
        oldPendingOutputBuffer.release(); // 释放旧的缓冲区
      }

      // 请求 GLSurfaceView 进行渲染
      surfaceView.requestRender();
    }

    /**
     * 设置纹理。
     *
     * @throws NullPointerException 如果 program 为 null。
     */
    @RequiresNonNull("program")
    private void setupTextures() {
      try {
        // 生成 3 个纹理 ID（用于 Y、U、V 三个平面）
        GLES20.glGenTextures(/* n= */ 3, yuvTextures, /* offset= */ 0);

        // 配置每个纹理
        for (int i = 0; i < 3; i++) {
          // 将纹理 Uniform 绑定到对应的纹理单元
          GLES20.glUniform1i(program.getUniformLocation(TEXTURE_UNIFORMS[i]), i);

          // 激活当前纹理单元
          GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);

          // 绑定纹理并设置过滤模式为线性
          GlUtil.bindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[i], GLES20.GL_LINEAR);
        }

        // 检查 OpenGL 错误
        GlUtil.checkGlError();
      } catch (GlUtil.GlException e) {
        // 记录设置纹理失败的错误日志
        Log.e(TAG, "Failed to set up the textures", e);
      }
    }
  }
}
