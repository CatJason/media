package androidx.media3.common.util;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import androidx.annotation.RequiresOptIn;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 表示某个公共 API（类、方法或字段）在未来的版本中可能会发生不兼容的更改，甚至被移除。
 *
 * <p>此注解的存在并不意味着相关 API 的质量或性能有问题，仅表示该 API 尚未“冻结”。
 *
 * <p>本库遵循 <a href="https://semver.org/">语义化版本控制</a>，稳定 API 构成了版本控制规则中的“公共”API。因此，带有此注解的 API 不受语义化版本控制规则所隐含的兼容性保证的约束。
 *
 * <p>应用程序依赖不稳定的 API 通常是安全的，但在升级时可能需要额外的工作。然而，对于库（它们会被包含在用户的 CLASSPATH 中，超出库开发者的控制范围）来说，依赖不稳定的 API 通常是不明智的。
 *
 * <h2>请求将 API 添加到稳定 API 中</h2>
 *
 * Media3 的稳定 API（即未带有此注解的公共 API 符号）旨在让开发者能够完成常见的媒体相关任务。如果您无法使用稳定 API 实现您的用例，并认为应该能够实现，请在 <a href="https://github.com/androidx/media/issues">GitHub 问题跟踪器</a> 上提交问题，并提供完整的上下文说明您在做什么，以及您需要哪些符号成为稳定 API 的一部分。我们将根据具体情况考虑每个请求。
 *
 * <h2>选择使用不稳定的 API</h2>
 *
 * <p>默认情况下，使用带有此注解的 API 会在 Gradle 和 Android Studio 中生成 lint 错误，以提醒开发者存在破坏性更改的风险。
 *
 * <p>有关如何使用 {@code @OptIn} 注解 Java 和 Kotlin 用法的详细信息，请参阅 <a
 * href="https://developer.android.com/media/media3/exoplayer/troubleshooting#unstable-api-lint-errors">关于这些 lint 错误的故障排除部分</a>。
 */
@Documented
@Retention(CLASS)
@Target({TYPE, METHOD, CONSTRUCTOR, FIELD})
@UnstableApi
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public @interface UnstableApi {}
