package androidx.media3.exoplayer.dash.manifest;

import androidx.media3.common.util.UnstableApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 用于构建 URL 的模板。
 *
 * <p>URL 根据 ISO/IEC 23009-1:2014 5.3.9.4.4 中定义的替换规则构建。
 */
@UnstableApi
public final class UrlTemplate {

  private static final String REPRESENTATION = "RepresentationID";
  private static final String NUMBER = "Number";
  private static final String BANDWIDTH = "Bandwidth";
  private static final String TIME = "Time";
  private static final String ESCAPED_DOLLAR = "$$";
  private static final String DEFAULT_FORMAT_TAG = "%01d";

  private static final int REPRESENTATION_ID = 1;
  private static final int NUMBER_ID = 2;
  private static final int BANDWIDTH_ID = 3;
  private static final int TIME_ID = 4;

  private final List<String> urlPieces;
  private final List<Integer> identifiers;
  private final List<String> identifierFormatTags;

  /**
   * 从提供的模板字符串编译一个实例。
   *
   * @param template 模板字符串。
   * @return 编译后的实例。
   * @throws IllegalArgumentException 如果模板字符串格式不正确。
   */
  public static UrlTemplate compile(String template) {
    List<String> urlPieces = new ArrayList<>();
    List<Integer> identifiers = new ArrayList<>();
    List<String> identifierFormatTags = new ArrayList<>();

    parseTemplate(template, urlPieces, identifiers, identifierFormatTags);
    return new UrlTemplate(urlPieces, identifiers, identifierFormatTags);
  }

  /** 内部构造函数。使用 {@link #compile(String)} 来构建此类的实例。 */
  private UrlTemplate(
      List<String> urlPieces, List<Integer> identifiers, List<String> identifierFormatTags) {
    this.urlPieces = urlPieces;
    this.identifiers = identifiers;
    this.identifierFormatTags = identifierFormatTags;
  }

  /**
   * 根据模板构建 URI，并替换提供的参数。
   *
   * <p>如果模板中不存在对应的标识符，则忽略相应的参数。
   *
   * @param representationId 表示标识符。
   * @param segmentNumber 分段编号。
   * @param bandwidth 带宽。
   * @param time 分段时间线中指定的时间。
   * @return 构建的 URI。
   */
  public String buildUri(String representationId, long segmentNumber, int bandwidth, long time) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < identifiers.size(); i++) {
      builder.append(urlPieces.get(i));
      if (identifiers.get(i) == REPRESENTATION_ID) {
        builder.append(representationId);
      } else if (identifiers.get(i) == NUMBER_ID) {
        builder.append(String.format(Locale.US, identifierFormatTags.get(i), segmentNumber));
      } else if (identifiers.get(i) == BANDWIDTH_ID) {
        builder.append(String.format(Locale.US, identifierFormatTags.get(i), bandwidth));
      } else if (identifiers.get(i) == TIME_ID) {
        builder.append(String.format(Locale.US, identifierFormatTags.get(i), time));
      }
    }
    builder.append(urlPieces.get(identifiers.size()));
    return builder.toString();
  }

  /**
   * 解析 {@code template}，并将分解后的组件放入提供的列表中。
   *
   * <p>如果 {@code template} 中的标识符数量为 N，则 {@code urlPieces} 将包含 (N+1) 个字符串，
   * 这些字符串必须与 N 个参数交错以构建 URL。与所需参数对应的 N 个标识符及其格式标签分别
   * 返回在 {@code identifiers} 和 {@code identifierFormatTags} 中。
   *
   * @param template 要解析的模板。
   * @param urlPieces 用于存放从模板解析出的 URL 片段的容器。
   * @param identifiers 用于存放从模板解析出的标识符的容器。
   * @param identifierFormatTags 用于存放与解析出的标识符对应的格式标签的容器。
   * @throws IllegalArgumentException 如果模板字符串格式不正确。
   */
  private static void parseTemplate(
      String template,
      List<String> urlPieces,
      List<Integer> identifiers,
      List<String> identifierFormatTags) {
    urlPieces.add("");
    int templateIndex = 0;
    while (templateIndex < template.length()) {
      int dollarIndex = template.indexOf("$", templateIndex);
      if (dollarIndex == -1) {
        urlPieces.set(
            identifiers.size(),
            urlPieces.get(identifiers.size()) + template.substring(templateIndex));
        templateIndex = template.length();
      } else if (dollarIndex != templateIndex) {
        urlPieces.set(
            identifiers.size(),
            urlPieces.get(identifiers.size()) + template.substring(templateIndex, dollarIndex));
        templateIndex = dollarIndex;
      } else if (template.startsWith(ESCAPED_DOLLAR, templateIndex)) {
        urlPieces.set(identifiers.size(), urlPieces.get(identifiers.size()) + "$");
        templateIndex += 2;
      } else {
        identifierFormatTags.add("");
        int secondIndex = template.indexOf("$", templateIndex + 1);
        String identifier = template.substring(templateIndex + 1, secondIndex);
        if (identifier.equals(REPRESENTATION)) {
          identifiers.add(REPRESENTATION_ID);
        } else {
          int formatTagIndex = identifier.indexOf("%0");
          String formatTag = DEFAULT_FORMAT_TAG;
          if (formatTagIndex != -1) {
            formatTag = identifier.substring(formatTagIndex);
            // 允许的转换是十进制整数（DASH 规范中唯一允许的转换）和十六进制整数（由于现有内容使用它）。
            // 否则，我们假设缺少转换，并且它应该是十进制整数。
            if (!formatTag.endsWith("d") && !formatTag.endsWith("x") && !formatTag.endsWith("X")) {
              formatTag += "d";
            }
            identifier = identifier.substring(0, formatTagIndex);
          }
          switch (identifier) {
            case NUMBER:
              identifiers.add(NUMBER_ID);
              break;
            case BANDWIDTH:
              identifiers.add(BANDWIDTH_ID);
              break;
            case TIME:
              identifiers.add(TIME_ID);
              break;
            default:
              throw new IllegalArgumentException("Invalid template: " + template);
          }
          identifierFormatTags.set(identifiers.size() - 1, formatTag);
        }
        urlPieces.add("");
        templateIndex = secondIndex + 1;
      }
    }
  }
}