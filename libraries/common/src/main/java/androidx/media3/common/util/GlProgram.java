package androidx.media3.common.util;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.nio.Buffer;
import java.util.HashMap;
import java.util.Map;

/**
 * 表示一个 GLSL 着色器程序。
 *
 * <p>在构造程序后，保留其生命周期的引用，并在不再需要时调用 {@link #delete()}（或释放当前 GL 上下文）。
 */
@UnstableApi
public final class GlProgram {

  // https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_YUV_target.txt
  private static final int GL_SAMPLER_EXTERNAL_2D_Y2Y_EXT = 0x8BE7;

  /** 编译和链接后的 GLSL 着色器程序的标识符。 */
  private final int programId;

  private final Attribute[] attributes;
  private final Uniform[] uniforms;
  private final Map<String, Attribute> attributeByName;
  private final Map<String, Uniform> uniformByName;

  private boolean externalTexturesRequireNearestSampling;

  /**
   * 从顶点和片段着色器 GLSL GLES20 代码编译 GL 着色器程序。
   *
   * @param context 上下文。
   * @param vertexShaderFilePath 顶点着色器文件的路径。
   * @param fragmentShaderFilePath 片段着色器文件的路径。
   * @throws IOException 当读取着色器文件失败时抛出。
   */
  public GlProgram(Context context, String vertexShaderFilePath, String fragmentShaderFilePath)
      throws IOException, GlUtil.GlException {
    this(
        Util.loadAsset(context, vertexShaderFilePath),
        Util.loadAsset(context, fragmentShaderFilePath));
  }

  /**
   * 从顶点和片段着色器 GLSL GLES20 代码创建 GL 着色器程序。
   *
   * <p>此过程涉及编译、链接和切换 GL 程序等慢速步骤，因此不要在快速渲染循环中调用此方法。
   *
   * @param vertexShaderGlsl 顶点着色器程序。
   * @param fragmentShaderGlsl 片段着色器程序。
   */
  public GlProgram(String vertexShaderGlsl, String fragmentShaderGlsl) throws GlUtil.GlException {
    programId = GLES20.glCreateProgram();
    GlUtil.checkGlError();

    // 添加顶点和片段着色器。
    addShader(programId, GLES20.GL_VERTEX_SHADER, vertexShaderGlsl);
    addShader(programId, GLES20.GL_FRAGMENT_SHADER, fragmentShaderGlsl);

    // 链接并使用程序，并枚举属性/统一变量。
    GLES20.glLinkProgram(programId);
    int[] linkStatus = new int[] {GLES20.GL_FALSE};
    GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, /* offset= */ 0);
    GlUtil.checkGlException(
        linkStatus[0] == GLES20.GL_TRUE,
        "无法链接着色器程序：\n" + GLES20.glGetProgramInfoLog(programId));
    GLES20.glUseProgram(programId);
    attributeByName = new HashMap<>();
    int[] attributeCount = new int[1];
    GLES20.glGetProgramiv(programId, GLES20.GL_ACTIVE_ATTRIBUTES, attributeCount, /* offset= */ 0);
    attributes = new Attribute[attributeCount[0]];
    for (int i = 0; i < attributeCount[0]; i++) {
      Attribute attribute = Attribute.create(programId, i);
      attributes[i] = attribute;
      attributeByName.put(attribute.name, attribute);
    }
    uniformByName = new HashMap<>();
    int[] uniformCount = new int[1];
    GLES20.glGetProgramiv(programId, GLES20.GL_ACTIVE_UNIFORMS, uniformCount, /* offset= */ 0);
    uniforms = new Uniform[uniformCount[0]];
    for (int i = 0; i < uniformCount[0]; i++) {
      Uniform uniform = Uniform.create(programId, i);
      uniforms[i] = uniform;
      uniformByName.put(uniform.name, uniform);
    }
    GlUtil.checkGlError();
  }

  private static void addShader(int programId, int type, String glsl) throws GlUtil.GlException {
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, glsl);
    GLES20.glCompileShader(shader);

    int[] result = new int[] {GLES20.GL_FALSE};
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, /* offset= */ 0);
    GlUtil.checkGlException(
        result[0] == GLES20.GL_TRUE, GLES20.glGetShaderInfoLog(shader) + ", 源代码：\n" + glsl);

    GLES20.glAttachShader(programId, shader);
    GLES20.glDeleteShader(shader);
    GlUtil.checkGlError();
  }

  private static int getAttributeLocation(int programId, String attributeName) {
    return GLES20.glGetAttribLocation(programId, attributeName);
  }

  /** 返回 {@link Attribute} 的位置。 */
  private int getAttributeLocation(String attributeName) {
    return getAttributeLocation(programId, attributeName);
  }

  private static int getUniformLocation(int programId, String uniformName) {
    return GLES20.glGetUniformLocation(programId, uniformName);
  }

  /** 返回 {@link Uniform} 的位置。 */
  public int getUniformLocation(String uniformName) {
    return getUniformLocation(programId, uniformName);
  }

  /**
   * 使用该程序。
   *
   * <p>在渲染循环中调用此方法以在不同程序之间切换。
   */
  public void use() throws GlUtil.GlException {
    GLES20.glUseProgram(programId);
    GlUtil.checkGlError();
  }

  /** 删除程序。删除后的程序无法再次使用。 */
  public void delete() throws GlUtil.GlException {
    GLES20.glDeleteProgram(programId);
    GlUtil.checkGlError();
  }

  /**
   * 返回 {@link Attribute} 的位置，并将其启用为顶点属性数组。
   */
  public int getAttributeArrayLocationAndEnable(String attributeName) throws GlUtil.GlException {
    int location = getAttributeLocation(attributeName);
    GLES20.glEnableVertexAttribArray(location);
    GlUtil.checkGlError();
    return location;
  }

  /** 设置浮点缓冲区类型的属性。 */
  public void setBufferAttribute(String name, float[] values, int size) {
    checkNotNull(attributeByName.get(name)).setBuffer(values, size);
  }

  /**
   * 设置纹理采样器类型的统一变量。
   *
   * @param name 统一变量的名称。
   * @param texId 纹理标识符。
   * @param texUnitIndex 纹理单元索引。为程序中的每个纹理采样器使用不同的索引（0, 1, 2, ...）。
   */
  public void setSamplerTexIdUniform(String name, int texId, int texUnitIndex) {
    checkNotNull(uniformByName.get(name)).setSamplerTexId(texId, texUnitIndex);
  }

  /** 设置 {@code int} 类型的统一变量。 */
  public void setIntUniform(String name, int value) {
    checkNotNull(uniformByName.get(name)).setInt(value);
  }

  /** 设置 {@code int[]} 类型的统一变量。 */
  public void setIntsUniform(String name, int[] value) {
    checkNotNull(uniformByName.get(name)).setInts(value);
  }

  /** 设置 {@code float} 类型的统一变量。 */
  public void setFloatUniform(String name, float value) {
    checkNotNull(uniformByName.get(name)).setFloat(value);
  }

  /** 设置 {@code float[]} 类型的统一变量。 */
  public void setFloatsUniform(String name, float[] value) {
    checkNotNull(uniformByName.get(name)).setFloats(value);
  }

  /** 如果 {@code name} 存在，则设置 {@code float[]} 类型的统一变量，否则不执行任何操作。 */
  public void setFloatsUniformIfPresent(String name, float[] value) {
    @Nullable Uniform uniform = uniformByName.get(name);
    if (uniform == null) {
      return;
    }
    uniform.setFloats(value);
  }

  /** 绑定程序中的所有属性和统一变量。 */
  public void bindAttributesAndUniforms() throws GlUtil.GlException {
    for (Attribute attribute : attributes) {
      attribute.bind();
    }
    for (Uniform uniform : uniforms) {
      uniform.bind(externalTexturesRequireNearestSampling);
    }
  }

  /**
   * 设置是否使用 GL_NEAREST 采样外部纹理。
   *
   * <p>默认值为 {@code false}。
   */
  public void setExternalTexturesRequireNearestSampling(
      boolean externalTexturesRequireNearestSampling) {
    this.externalTexturesRequireNearestSampling = externalTexturesRequireNearestSampling;
  }

  /** 返回 {@code cString} 中空终止 C 字符串的长度。 */
  private static int getCStringLength(byte[] cString) {
    for (int i = 0; i < cString.length; ++i) {
      if (cString[i] == '\0') {
        return i;
      }
    }
    return cString.length;
  }

  /**
   * GL 属性，可以通过 {@link Attribute#setBuffer(float[], int)} 附加到缓冲区。
   */
  private static final class Attribute {

    /* 返回程序中给定索引处的属性。 */
    public static Attribute create(int programId, int index) {
      int[] attributeNameMaxLength = new int[1];
      GLES20.glGetProgramiv(
          programId,
          GLES20.GL_ACTIVE_ATTRIBUTE_MAX_LENGTH,
          attributeNameMaxLength,
          /* offset= */ 0);
      byte[] nameBytes = new byte[attributeNameMaxLength[0]];

      GLES20.glGetActiveAttrib(
          programId,
          index,
          /* bufsize= */ attributeNameMaxLength[0],
          /* unusedLength */ new int[1],
          /* lengthOffset= */ 0,
          /* unusedSize */ new int[1],
          /* sizeOffset= */ 0,
          /* unusedType */ new int[1],
          /* typeOffset= */ 0,
          /* name= */ nameBytes,
          /* nameOffset= */ 0);
      String name = new String(nameBytes, /* offset= */ 0, getCStringLength(nameBytes));
      int location = getAttributeLocation(programId, name);

      return new Attribute(name, location);
    }

    /** GLSL 源代码中属性的名称。 */
    public final String name;

    /** 属性的索引或位置，来自 glGetAttribLocation。 */
    private final int location;

    @Nullable private Buffer buffer;
    private int size;

    private Attribute(String name, int location) {
      this.name = name;
      this.location = location;
    }

    /**
     * 配置 {@link #bind()} 以将 {@code buffer} 中的顶点（每个顶点大小为 {@code size} 元素）附加到此 {@link Attribute}。
     *
     * @param buffer 要绑定到此属性的缓冲区。
     * @param size 每个顶点的元素数。
     */
    public void setBuffer(float[] buffer, int size) {
      this.buffer = GlUtil.createBuffer(buffer);
      this.size = size;
    }

    /**
     * 将顶点属性设置为通过 {@link #setBuffer(float[], int)} 附加的内容。
     *
     * <p>应在每次绘制调用之前调用。
     */
    public void bind() throws GlUtil.GlException {
      Buffer buffer = checkNotNull(this.buffer, "在 bind 之前调用 setBuffer");
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, /* buffer= */ 0);
      GLES20.glVertexAttribPointer(
          location, size, GLES20.GL_FLOAT, /* normalized= */ false, /* stride= */ 0, buffer);
      GLES20.glEnableVertexAttribArray(location);
      GlUtil.checkGlError();
    }
  }

  /**
   * GL 统一变量，可以通过 {@link Uniform#setSamplerTexId(int, int)} 附加到采样器。
   */
  private static final class Uniform {

    /**
     * 返回程序中给定索引处的统一变量。
     *
     * <p>有关更多信息，请参阅 https://docs.gl/es2/glGetActiveUniform。
     */
    public static Uniform create(int programId, int index) {
      int[] length = new int[1];
      GLES20.glGetProgramiv(
          programId, GLES20.GL_ACTIVE_UNIFORM_MAX_LENGTH, length, /* offset= */ 0);

      int[] type = new int[1];
      byte[] nameBytes = new byte[length[0]];

      GLES20.glGetActiveUniform(
          programId,
          index,
          length[0],
          /* unusedLength */ new int[1],
          /* lengthOffset= */ 0,
          /* unusedSize */ new int[1],
          /* sizeOffset= */ 0,
          type,
          /* typeOffset= */ 0,
          nameBytes,
          /* nameOffset= */ 0);
      String name = new String(nameBytes, /* offset= */ 0, getCStringLength(nameBytes));
      int location = getUniformLocation(programId, name);

      return new Uniform(name, location, type[0]);
    }

    /** GLSL 源代码中统一变量的名称。 */
    public final String name;

    private final int location;
    private final int type;
    private final float[] floatValue;
    private final int[] intValue;

    private int texIdValue;
    private int texUnitIndex;

    private Uniform(String name, int location, int type) {
      this.name = name;
      this.location = location;
      this.type = type;
      this.floatValue = new float[16]; // 为 mat4 分配 16
      this.intValue = new int[4]; // 为 ivec4 分配 4
    }

    /**
     * 配置 {@link #bind(boolean)} 以使用指定的 {@code texId} 作为此采样器统一变量。
     *
     * @param texId 从中采样的 GL 纹理标识符。
     * @param texUnitIndex GL 纹理单元索引。
     */
    public void setSamplerTexId(int texId, int texUnitIndex) {
      this.texIdValue = texId;
      this.texUnitIndex = texUnitIndex;
    }

    /** 配置 {@link #bind(boolean)} 以使用指定的 {@code int} {@code value}。 */
    public void setInt(int value) {
      this.intValue[0] = value;
    }

    /** 配置 {@link #bind(boolean)} 以使用指定的 {@code int[]} {@code value}。 */
    public void setInts(int[] value) {
      System.arraycopy(value, /* srcPos= */ 0, this.intValue, /* destPos= */ 0, value.length);
    }

    /** 配置 {@link #bind(boolean)} 以使用指定的 {@code float} {@code value}。 */
    public void setFloat(float value) {
      this.floatValue[0] = value;
    }

    /** 配置 {@link #bind(boolean)} 以使用指定的 {@code float[]} {@code value}。 */
    public void setFloats(float[] value) {
      System.arraycopy(value, /* srcPos= */ 0, this.floatValue, /* destPos= */ 0, value.length);
    }

    /**
     * 将统一变量设置为通过 {@link #setSamplerTexId(int, int)}、{@link #setFloat(float)} 或 {@link
     * #setFloats(float[])} 传递的值。
     *
     * <p>应在每次绘制调用之前调用。
     *
     * @param externalTexturesRequireNearestSampling 外部纹理是否需要 GL_NEAREST 采样以避免从未定义区域采样，
     *     这在使用 GL_LINEAR 时可能发生。
     */
    public void bind(boolean externalTexturesRequireNearestSampling) throws GlUtil.GlException {
      switch (type) {
        case GLES20.GL_INT:
          GLES20.glUniform1iv(location, /* count= */ 1, intValue, /* offset= */ 0);
          GlUtil.checkGlError();
          break;
        case GLES20.GL_INT_VEC2:
          GLES20.glUniform2iv(location, /* count= */ 1, intValue, /* offset= */ 0);
          GlUtil.checkGlError();
          break;
        case GLES20.GL_INT_VEC3:
          GLES20.glUniform3iv(location, /* count= */ 1, intValue, /* offset= */ 0);
          GlUtil.checkGlError();
          break;
        case GLES20.GL_INT_VEC4:
          GLES20.glUniform4iv(location, /* count= */ 1, intValue, /* offset= */ 0);
          GlUtil.checkGlError();
          break;
        case GLES20.GL_FLOAT:
          GLES20.glUniform1fv(location, /* count= */ 1, floatValue, /* offset= */ 0);
          GlUtil.checkGlError();
          break;
        case GLES20.GL_FLOAT_VEC2:
          GLES20.glUniform2fv(location, /* count= */ 1, floatValue, /* offset= */ 0);
          GlUtil.checkGlError();
          break;
        case GLES20.GL_FLOAT_VEC3:
          GLES20.glUniform3fv(location, /* count= */ 1, floatValue, /* offset= */ 0);
          GlUtil.checkGlError();
          break;
        case GLES20.GL_FLOAT_VEC4:
          GLES20.glUniform4fv(location, /* count= */ 1, floatValue, /* offset= */ 0);
          GlUtil.checkGlError();
          break;
        case GLES20.GL_FLOAT_MAT3:
          GLES20.glUniformMatrix3fv(
              location, /* count= */ 1, /* transpose= */ false, floatValue, /* offset= */ 0);
          GlUtil.checkGlError();

          break;
        case GLES20.GL_FLOAT_MAT4:
          GLES20.glUniformMatrix4fv(
              location, /* count= */ 1, /* transpose= */ false, floatValue, /* offset= */ 0);
          GlUtil.checkGlError();
          break;
        case GLES20.GL_SAMPLER_2D:
        case GLES11Ext.GL_SAMPLER_EXTERNAL_OES:
        case GL_SAMPLER_EXTERNAL_2D_Y2Y_EXT:
          if (texIdValue == 0) {
            throw new IllegalStateException("No call to setSamplerTexId() before bind.");
          }
          GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + texUnitIndex);
          GlUtil.checkGlError();
          GlUtil.bindTexture(
              type == GLES20.GL_SAMPLER_2D
                  ? GLES20.GL_TEXTURE_2D
                  : GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
              texIdValue,
              type == GLES20.GL_SAMPLER_2D || !externalTexturesRequireNearestSampling
                  ? GLES20.GL_LINEAR
                  : GLES20.GL_NEAREST);
          GLES20.glUniform1i(location, texUnitIndex);
          GlUtil.checkGlError();
          break;
        default:
          throw new IllegalStateException("Unexpected uniform type: " + type);
      }
    }
  }
}
