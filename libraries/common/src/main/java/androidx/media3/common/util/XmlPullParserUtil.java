package androidx.media3.common.util;

import androidx.annotation.Nullable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** {@link XmlPullParser} 工具方法。 */
@UnstableApi
public final class XmlPullParserUtil {

  private XmlPullParserUtil() {}

  /**
   * 返回当前事件是否为具有指定名称的结束标签。
   *
   * @param xpp 要查询的 {@link XmlPullParser}。
   * @param name 指定的名称。
   * @return 当前事件是否为具有指定名称的结束标签。
   * @throws XmlPullParserException 如果查询解析器时发生错误。
   */
  public static boolean isEndTag(XmlPullParser xpp, String name) throws XmlPullParserException {
    return isEndTag(xpp) && xpp.getName().equals(name);
  }

  /**
   * 返回当前事件是否为结束标签。
   *
   * @param xpp 要查询的 {@link XmlPullParser}。
   * @return 当前事件是否为结束标签。
   * @throws XmlPullParserException 如果查询解析器时发生错误。
   */
  public static boolean isEndTag(XmlPullParser xpp) throws XmlPullParserException {
    return xpp.getEventType() == XmlPullParser.END_TAG;
  }

  /**
   * 返回当前事件是否为具有指定名称的开始标签。
   *
   * @param xpp 要查询的 {@link XmlPullParser}。
   * @param name 指定的名称。
   * @return 当前事件是否为具有指定名称的开始标签。
   * @throws XmlPullParserException 如果查询解析器时发生错误。
   */
  public static boolean isStartTag(XmlPullParser xpp, String name) throws XmlPullParserException {
    return isStartTag(xpp) && xpp.getName().equals(name);
  }

  /**
   * 返回当前事件是否为开始标签。
   *
   * @param xpp 要查询的 {@link XmlPullParser}。
   * @return 当前事件是否为开始标签。
   * @throws XmlPullParserException 如果查询解析器时发生错误。
   */
  public static boolean isStartTag(XmlPullParser xpp) throws XmlPullParserException {
    return xpp.getEventType() == XmlPullParser.START_TAG;
  }

  /**
   * 返回当前事件是否为具有指定名称的开始标签。如果当前事件有原始名称，则在匹配之前会剥离其前缀。
   *
   * @param xpp 要查询的 {@link XmlPullParser}。
   * @param name 指定的名称。
   * @return 当前事件是否为具有指定名称的开始标签。
   * @throws XmlPullParserException 如果查询解析器时发生错误。
   */
  public static boolean isStartTagIgnorePrefix(XmlPullParser xpp, String name)
      throws XmlPullParserException {
    return isStartTag(xpp) && stripPrefix(xpp.getName()).equals(name);
  }

  /**
   * 返回当前开始标签的某个属性的值。
   *
   * @param xpp 要查询的 {@link XmlPullParser}。
   * @param attributeName 属性的名称。
   * @return 属性的值，如果当前事件不是开始标签或未找到该属性，则返回 null。
   */
  @Nullable
  public static String getAttributeValue(XmlPullParser xpp, String attributeName) {
    int attributeCount = xpp.getAttributeCount();
    for (int i = 0; i < attributeCount; i++) {
      if (xpp.getAttributeName(i).equals(attributeName)) {
        return xpp.getAttributeValue(i);
      }
    }
    return null;
  }

  /**
   * 返回当前开始标签的某个属性的值。当前开始标签中的任何原始属性名称在匹配之前都会剥离其前缀。
   *
   * @param xpp 要查询的 {@link XmlPullParser}。
   * @param attributeName 属性的名称。
   * @return 属性的值，如果当前事件不是开始标签或未找到该属性，则返回 null。
   */
  @Nullable
  public static String getAttributeValueIgnorePrefix(XmlPullParser xpp, String attributeName) {
    int attributeCount = xpp.getAttributeCount();
    for (int i = 0; i < attributeCount; i++) {
      if (stripPrefix(xpp.getAttributeName(i)).equals(attributeName)) {
        return xpp.getAttributeValue(i);
      }
    }
    return null;
  }

  private static String stripPrefix(String name) {
    int prefixSeparatorIndex = name.indexOf(':');
    return prefixSeparatorIndex == -1 ? name : name.substring(prefixSeparatorIndex + 1);
  }
}