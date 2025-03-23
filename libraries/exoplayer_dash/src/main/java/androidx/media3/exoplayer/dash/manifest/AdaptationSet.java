package androidx.media3.exoplayer.dash.manifest;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import java.util.Collections;
import java.util.List;

/** 表示一组可互换的媒体内容组件的编码版本。 */
@UnstableApi
public class AdaptationSet {

  /** {@link #id} 的值，表示未设置值。 */
  public static final long ID_UNSET = -1;

  /**
   * 自适应集的非负标识符，在其所属周期范围内唯一，如果未指定则为 {@link #ID_UNSET}。
   */
  public final long id;

  /** 自适应集的 {@link C.TrackType 轨道类型}。 */
  public final @C.TrackType int type;

  /** 自适应集中的 {@link Representation} 列表。 */
  public final List<Representation> representations;

  /** 自适应集中的辅助功能描述符列表。 */
  public final List<Descriptor> accessibilityDescriptors;

  /** 自适应集中的必要属性列表。 */
  public final List<Descriptor> essentialProperties;

  /** 自适应集中的补充属性列表。 */
  public final List<Descriptor> supplementalProperties;

  /**
   * @param id 自适应集的非负标识符，在其所属周期范围内唯一，如果未指定则为 {@link #ID_UNSET}。
   * @param type 自适应集的 {@link C.TrackType 轨道类型}。
   * @param representations 自适应集中的 {@link Representation} 列表。
   * @param accessibilityDescriptors 自适应集中的辅助功能描述符列表。
   * @param essentialProperties 自适应集中的必要属性列表。
   * @param supplementalProperties 自适应集中的补充属性列表。
   */
  public AdaptationSet(
      long id,
      @C.TrackType int type,
      List<Representation> representations,
      List<Descriptor> accessibilityDescriptors,
      List<Descriptor> essentialProperties,
      List<Descriptor> supplementalProperties) {
    this.id = id;
    this.type = type;
    this.representations = Collections.unmodifiableList(representations);
    this.accessibilityDescriptors = Collections.unmodifiableList(accessibilityDescriptors);
    this.essentialProperties = Collections.unmodifiableList(essentialProperties);
    this.supplementalProperties = Collections.unmodifiableList(supplementalProperties);
  }
}