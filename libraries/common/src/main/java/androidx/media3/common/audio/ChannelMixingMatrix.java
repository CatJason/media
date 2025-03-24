package androidx.media3.common.audio;

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.media3.common.util.UnstableApi;

/**
 * 一个不可变的矩阵，用于描述输入通道到输出通道的映射关系。
 *
 * <p>矩阵系数定义了将输入通道（行）的样本混合到输出通道（列）时使用的缩放因子。
 *
 * <p>示例：
 *
 * <ul>
 *   <li>立体声转单声道，每个通道的音量为一半：
 *       <pre>
 *         [0.5 0.5]</pre>
 *   <li>立体声转立体声，无混合或缩放：
 *       <pre>
 *         [1 0
 *          0 1]</pre>
 *   <li>立体声转立体声，音量为 0.7：
 *       <pre>
 *         [0.7 0
 *          0 0.7]</pre>
 * </ul>
 */
@UnstableApi
public final class ChannelMixingMatrix {
  private final int inputChannelCount;
  private final int outputChannelCount;
  private final float[] coefficients;
  private final boolean isZero;
  private final boolean isDiagonal;
  private final boolean isIdentity;

  /**
   * 创建一个标准的通道混合矩阵，将 {@code inputChannelCount} 个通道转换为 {@code outputChannelCount} 个通道。
   *
   * <p>如果输入和输出通道数匹配，则返回一个简单的单位矩阵。否则，将使用默认的矩阵系数以最佳匹配通道位置和整体功率水平。
   *
   * @param inputChannelCount 输入通道数。
   * @param outputChannelCount 输出通道数。
   * @return 新的通道混合矩阵。
   * @throws UnsupportedOperationException 如果尚未实现给定输入和输出通道数的默认矩阵系数。
   */
  public static ChannelMixingMatrix create(int inputChannelCount, int outputChannelCount) {
    return new ChannelMixingMatrix(
        inputChannelCount,
        outputChannelCount,
        createMixingCoefficients(inputChannelCount, outputChannelCount));
  }

  /**
   * 使用给定的系数（按行优先顺序）创建一个矩阵。
   *
   * @param inputChannelCount 输入通道数（矩阵的行数）。
   * @param outputChannelCount 输出通道数（矩阵的列数）。
   * @param coefficients 非负的矩阵系数（按行优先顺序）。
   */
  public ChannelMixingMatrix(int inputChannelCount, int outputChannelCount, float[] coefficients) {
    checkArgument(inputChannelCount > 0, "输入通道数必须为正数。");
    checkArgument(outputChannelCount > 0, "输出通道数必须为正数。");
    checkArgument(
        coefficients.length == inputChannelCount * outputChannelCount,
        "系数数组长度无效。");
    this.inputChannelCount = inputChannelCount;
    this.outputChannelCount = outputChannelCount;
    this.coefficients = checkCoefficientsValid(coefficients);

    // 计算矩阵属性。
    boolean allDiagonalCoefficientsAreOne = true;
    boolean allCoefficientsAreZero = true;
    boolean allNonDiagonalCoefficientsAreZero = true;
    for (int row = 0; row < inputChannelCount; row++) {
      for (int col = 0; col < outputChannelCount; col++) {
        float coefficient = getMixingCoefficient(row, col);
        boolean onDiagonal = row == col;

        if (coefficient != 1f && onDiagonal) {
          allDiagonalCoefficientsAreOne = false;
        }
        if (coefficient != 0f) {
          allCoefficientsAreZero = false;
          if (!onDiagonal) {
            allNonDiagonalCoefficientsAreZero = false;
          }
        }
      }
    }
    isZero = allCoefficientsAreZero;
    isDiagonal = isSquare() && allNonDiagonalCoefficientsAreZero;
    isIdentity = isDiagonal && allDiagonalCoefficientsAreOne;
  }

  public int getInputChannelCount() {
    return inputChannelCount;
  }

  public int getOutputChannelCount() {
    return outputChannelCount;
  }

  /** 获取给定输入和输出通道的缩放因子。 */
  public float getMixingCoefficient(int inputChannel, int outputChannel) {
    return coefficients[inputChannel * outputChannelCount + outputChannel];
  }

  /** 返回所有混合系数是否为零。 */
  public boolean isZero() {
    return isZero;
  }

  /** 返回输入和输出通道数是否相同。 */
  public boolean isSquare() {
    return inputChannelCount == outputChannelCount;
  }

  /** 返回矩阵是否为方阵且所有非对角线系数为零。 */
  public boolean isDiagonal() {
    return isDiagonal;
  }

  /** 返回此矩阵是否为单位矩阵。 */
  public boolean isIdentity() {
    return isIdentity;
  }

  /** 返回一个新矩阵，其中所有系数都乘以给定的缩放因子。 */
  public ChannelMixingMatrix scaleBy(float scale) {
    float[] scaledCoefficients = new float[coefficients.length];
    for (int i = 0; i < coefficients.length; i++) {
      scaledCoefficients[i] = scale * coefficients[i];
    }
    return new ChannelMixingMatrix(inputChannelCount, outputChannelCount, scaledCoefficients);
  }

  private static float[] createMixingCoefficients(int inputChannelCount, int outputChannelCount) {
    if (inputChannelCount == outputChannelCount) {
      return initializeIdentityMatrix(outputChannelCount);
    }
    if (inputChannelCount == 1 && outputChannelCount == 2) {
      // 单声道 -> 立体声。
      return new float[] {1f, 1f};
    }
    if (inputChannelCount == 2 && outputChannelCount == 1) {
      // 立体声 -> 单声道。
      return new float[] {0.5f, 0.5f};
    }
    throw new UnsupportedOperationException(
        "尚未实现 "
            + inputChannelCount
            + "->"
            + outputChannelCount
            + " 的默认通道混合系数。");
  }

  private static float[] initializeIdentityMatrix(int channelCount) {
    float[] coefficients = new float[channelCount * channelCount];
    for (int c = 0; c < channelCount; c++) {
      coefficients[channelCount * c + c] = 1f;
    }
    return coefficients;
  }

  private static float[] checkCoefficientsValid(float[] coefficients) {
    for (int i = 0; i < coefficients.length; i++) {
      if (coefficients[i] < 0f) {
        throw new IllegalArgumentException("索引 " + i + " 处的系数为负数。");
      }
    }
    return coefficients;
  }
}