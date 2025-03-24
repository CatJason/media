package androidx.media3.common;

import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;

/** 包含描述 OpenGL 纹理的信息。 */
@UnstableApi
public final class GlTextureInfo {
  /** 一个所有字段均未设置的 {@link GlTextureInfo} 实例。 */
  public static final GlTextureInfo UNSET =
      new GlTextureInfo(
          /* texId= */ C.INDEX_UNSET,
          /* fboId= */ C.INDEX_UNSET,
          /* rboId= */ C.INDEX_UNSET,
          /* width= */ C.LENGTH_UNSET,
          /* height= */ C.LENGTH_UNSET);

  /** OpenGL 纹理标识符，如果未指定则为 {@link C#INDEX_UNSET}。 */
  public final int texId;

  /**
   * 与纹理关联的帧缓冲区对象标识符，如果未指定则为 {@link C#INDEX_UNSET}。
   */
  public final int fboId;

  /**
   * 与帧缓冲区关联的渲染缓冲区对象标识符，如果未指定则为 {@link C#INDEX_UNSET}。
   */
  public final int rboId;

  /** 纹理的宽度，单位为像素，如果未指定则为 {@link C#LENGTH_UNSET}。 */
  public final int width;

  /** 纹理的高度，单位为像素，如果未指定则为 {@link C#LENGTH_UNSET}。 */
  public final int height;

  /**
   * 创建一个新实例。
   *
   * @param texId OpenGL 纹理标识符，如果未指定则为 {@link C#INDEX_UNSET}。
   * @param fboId 与纹理关联的帧缓冲区对象标识符，如果未指定则为 {@link C#INDEX_UNSET}。
   * @param rboId 与纹理关联的渲染缓冲区对象标识符，如果未指定则为 {@link C#INDEX_UNSET}。
   * @param width 纹理的宽度，单位为像素，如果未指定则为 {@link C#LENGTH_UNSET}。
   * @param height 纹理的高度，单位为像素，如果未指定则为 {@link C#LENGTH_UNSET}。
   */
  public GlTextureInfo(int texId, int fboId, int rboId, int width, int height) {
    this.texId = texId;
    this.fboId = fboId;
    this.rboId = rboId;
    this.width = width;
    this.height = height;
  }

  /** 释放与此实例关联的所有资源。 */
  public void release() throws GlUtil.GlException {
    if (texId != C.INDEX_UNSET) {
      GlUtil.deleteTexture(texId);
    }
    if (fboId != C.INDEX_UNSET) {
      GlUtil.deleteFbo(fboId);
    }
    if (rboId != C.INDEX_UNSET) {
      GlUtil.deleteRbo(rboId);
    }
  }
}