/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.dash.manifest;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.dash.DashSegmentIndex;
import androidx.media3.exoplayer.dash.manifest.SegmentBase.MultiSegmentBase;
import androidx.media3.exoplayer.dash.manifest.SegmentBase.SingleSegmentBase;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
/** 表示 DASH 中的一个表示（Representation）。 */
@UnstableApi
public abstract class Representation {

  /** {@link #revisionId} 的默认值。 */
  public static final long REVISION_ID_DEFAULT = -1;

  /**
   * 标识表示中包含的媒体的修订版本。如果媒体可能随时间变化（例如由于重新编码），则可以使用此标识符唯一标识媒体的修订版本。
   * 通常可以使用媒体的编码时间戳作为此标识符。
   */
  public final long revisionId;

  /** 表示的格式。 */
  public final Format format;

  /** 表示的基础 URL 列表。 */
  public final ImmutableList<BaseUrl> baseUrls;

  /** 媒体流中表示时间戳相对于媒体时间的偏移量，单位为微秒。 */
  public final long presentationTimeOffsetUs;

  /** 表示中的带内事件流列表。可能为空。 */
  public final List<Descriptor> inbandEventStreams;

  /** 表示中的必要属性列表。可能为空。 */
  public final List<Descriptor> essentialProperties;

  /** 表示中的补充属性列表。可能为空。 */
  public final List<Descriptor> supplementalProperties;

  @Nullable private final RangedUri initializationUri;

  /**
   * 构造一个新的实例。
   *
   * @param revisionId 标识内容的修订版本。
   * @param format 表示的格式。
   * @param baseUrls 表示的基础 URL 列表。
   * @param segmentBase 表示的段基础元素。
   * @return 构造的实例。
   */
  public static Representation newInstance(
      long revisionId, Format format, List<BaseUrl> baseUrls, SegmentBase segmentBase) {
    return newInstance(
        revisionId,
        format,
        baseUrls,
        segmentBase,
        /* inbandEventStreams= */ null,
        /* essentialProperties= */ ImmutableList.of(),
        /* supplementalProperties= */ ImmutableList.of(),
        /* cacheKey= */ null);
  }
  /**
   * 构造一个新的实例。
   *
   * @param revisionId 标识内容的修订版本。
   * @param format 表示的格式。
   * @param baseUrls 表示的基础 URL 列表。
   * @param segmentBase 表示的段基础元素。
   * @param inbandEventStreams 表示中的带内事件流列表。可能为 null。
   * @param essentialProperties 表示中的必要属性列表。可能为空。
   * @param supplementalProperties 表示中的补充属性列表。可能为空。
   * @param cacheKey 可选的缓存键，由 {@link #getCacheKey()} 返回，或为 null。如果 {@code segmentBase} 包含多个分段，则忽略此参数。
   * @return 构造的实例。
   */
  public static Representation newInstance(
      long revisionId,
      Format format,
      List<BaseUrl> baseUrls,
      SegmentBase segmentBase,
      @Nullable List<Descriptor> inbandEventStreams,
      List<Descriptor> essentialProperties,
      List<Descriptor> supplementalProperties,
      @Nullable String cacheKey) {
    if (segmentBase instanceof SingleSegmentBase) {
      return new SingleSegmentRepresentation(
          revisionId,
          format,
          baseUrls,
          (SingleSegmentBase) segmentBase,
          inbandEventStreams,
          essentialProperties,
          supplementalProperties,
          cacheKey,
          /* contentLength= */ C.LENGTH_UNSET);
    } else if (segmentBase instanceof MultiSegmentBase) {
      return new MultiSegmentRepresentation(
          revisionId,
          format,
          baseUrls,
          (MultiSegmentBase) segmentBase,
          inbandEventStreams,
          essentialProperties,
          supplementalProperties);
    } else {
      throw new IllegalArgumentException(
          "segmentBase 必须是 SingleSegmentBase 或 MultiSegmentBase 类型");
    }
  }

  private Representation(
      long revisionId,
      Format format,
      List<BaseUrl> baseUrls,
      SegmentBase segmentBase,
      @Nullable List<Descriptor> inbandEventStreams,
      List<Descriptor> essentialProperties,
      List<Descriptor> supplementalProperties) {
    checkArgument(!baseUrls.isEmpty());
    this.revisionId = revisionId;
    this.format = format;
    this.baseUrls = ImmutableList.copyOf(baseUrls);
    this.inbandEventStreams =
        inbandEventStreams == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(inbandEventStreams);
    this.essentialProperties = essentialProperties;
    this.supplementalProperties = supplementalProperties;
    initializationUri = segmentBase.getInitialization(this);
    presentationTimeOffsetUs = segmentBase.getPresentationTimeOffsetUs();
  }
  /**
   * 返回定义表示初始化数据位置的 {@link RangedUri}，如果不存在初始化数据则返回 null。
   */
  @Nullable
  public RangedUri getInitializationUri() {
    return initializationUri;
  }

  /**
   * 返回定义表示分段索引位置的 {@link RangedUri}，如果表示直接提供索引则返回 null。
   */
  @Nullable
  public abstract RangedUri getIndexUri();

  /** 如果表示直接提供索引，则返回索引，否则返回 null。 */
  @Nullable
  public abstract DashSegmentIndex getIndex();

  /** 返回表示的缓存键（如果已设置），否则返回 null。 */
  @Nullable
  public abstract String getCacheKey();
  /** 表示由单个分段组成的 DASH 表示。 */
  public static class SingleSegmentRepresentation extends Representation {

    /** 单个分段的 URI。 */
    public final Uri uri;

    /** 内容长度，如果未知则为 {@link C#LENGTH_UNSET}。 */
    public final long contentLength;

    @Nullable private final String cacheKey;
    @Nullable private final RangedUri indexUri;
    @Nullable private final SingleSegmentIndex segmentIndex;

    /**
     * @param revisionId 标识内容的修订版本。
     * @param format 表示的格式。
     * @param uri 媒体的 URI。
     * @param initializationStart 初始化数据的第一个字节的偏移量。
     * @param initializationEnd 初始化数据的最后一个字节的偏移量。
     * @param indexStart 索引数据的第一个字节的偏移量。
     * @param indexEnd 索引数据的最后一个字节的偏移量。
     * @param inbandEventStreams 表示中的带内事件流列表。可能为 null。
     * @param cacheKey 可选的缓存键，由 {@link #getCacheKey()} 返回，或为 null。
     * @param contentLength 内容长度，如果未知则为 {@link C#LENGTH_UNSET}。
     */
    public static SingleSegmentRepresentation newInstance(
        long revisionId,
        Format format,
        String uri,
        long initializationStart,
        long initializationEnd,
        long indexStart,
        long indexEnd,
        List<Descriptor> inbandEventStreams,
        @Nullable String cacheKey,
        long contentLength) {
      RangedUri rangedUri =
          new RangedUri(null, initializationStart, initializationEnd - initializationStart + 1);
      SingleSegmentBase segmentBase =
          new SingleSegmentBase(rangedUri, 1, 0, indexStart, indexEnd - indexStart + 1);
      ImmutableList<BaseUrl> baseUrls = ImmutableList.of(new BaseUrl(uri));
      return new SingleSegmentRepresentation(
          revisionId,
          format,
          baseUrls,
          segmentBase,
          inbandEventStreams,
          /* essentialProperties= */ ImmutableList.of(),
          /* supplementalProperties= */ ImmutableList.of(),
          cacheKey,
          contentLength);
    }

    /**
     * @param revisionId 标识内容的修订版本。
     * @param format 表示的格式。
     * @param baseUrls 表示的基础 URL 列表。
     * @param segmentBase 表示的基础分段元素。
     * @param inbandEventStreams 表示中的带内事件流列表。可能为 null。
     * @param essentialProperties 表示中的必要属性列表。可能为空。
     * @param supplementalProperties 表示中的补充属性列表。可能为空。
     * @param cacheKey 可选的缓存键，由 {@link #getCacheKey()} 返回，或为 null。
     * @param contentLength 内容长度，如果未知则为 {@link C#LENGTH_UNSET}。
     */
    public SingleSegmentRepresentation(
        long revisionId,
        Format format,
        List<BaseUrl> baseUrls,
        SingleSegmentBase segmentBase,
        @Nullable List<Descriptor> inbandEventStreams,
        List<Descriptor> essentialProperties,
        List<Descriptor> supplementalProperties,
        @Nullable String cacheKey,
        long contentLength) {
      super(
          revisionId,
          format,
          baseUrls,
          segmentBase,
          inbandEventStreams,
          essentialProperties,
          supplementalProperties);
      this.uri = Uri.parse(baseUrls.get(0).url);
      this.indexUri = segmentBase.getIndex();
      this.cacheKey = cacheKey;
      this.contentLength = contentLength;
      // 如果有索引 URI，则索引是外部定义的，我们不应直接返回索引。
      // 如果没有索引 URI，则我们只能返回一个定义单个分段的索引。
      segmentIndex =
          indexUri != null ? null : new SingleSegmentIndex(new RangedUri(null, 0, contentLength));
    }

    @Override
    @Nullable
    public RangedUri getIndexUri() {
      return indexUri;
    }

    @Override
    @Nullable
    public DashSegmentIndex getIndex() {
      return segmentIndex;
    }

    @Override
    @Nullable
    public String getCacheKey() {
      return cacheKey;
    }
  }
  /** 表示由多个分段组成的 DASH 表示。 */
  public static class MultiSegmentRepresentation extends Representation
      implements DashSegmentIndex {

    @VisibleForTesting /* package */ final MultiSegmentBase segmentBase;

    /**
     * 创建多分段表示。
     *
     * @param revisionId 标识内容的修订版本。
     * @param format 表示的格式。
     * @param baseUrls 表示的基础 URL 列表。
     * @param segmentBase 表示的基础分段元素。
     * @param inbandEventStreams 表示中的带内事件流列表。可能为 null。
     * @param essentialProperties 表示中的必要属性列表。可能为空。
     * @param supplementalProperties 表示中的补充属性列表。可能为空。
     */
    public MultiSegmentRepresentation(
        long revisionId,
        Format format,
        List<BaseUrl> baseUrls,
        MultiSegmentBase segmentBase,
        @Nullable List<Descriptor> inbandEventStreams,
        List<Descriptor> essentialProperties,
        List<Descriptor> supplementalProperties) {
      super(
          revisionId,
          format,
          baseUrls,
          segmentBase,
          inbandEventStreams,
          essentialProperties,
          supplementalProperties);
      this.segmentBase = segmentBase;
    }

    @Override
    @Nullable
    public RangedUri getIndexUri() {
      return null;
    }

    @Override
    public DashSegmentIndex getIndex() {
      return this;
    }

    @Override
    @Nullable
    public String getCacheKey() {
      return null;
    }

    // DashSegmentIndex implementation.

    @Override
    public RangedUri getSegmentUrl(long segmentNum) {
      return segmentBase.getSegmentUrl(this, segmentNum);
    }

    @Override
    public long getSegmentNum(long timeUs, long periodDurationUs) {
      return segmentBase.getSegmentNum(timeUs, periodDurationUs);
    }

    @Override
    public long getTimeUs(long segmentNum) {
      return segmentBase.getSegmentTimeUs(segmentNum);
    }

    @Override
    public long getDurationUs(long segmentNum, long periodDurationUs) {
      return segmentBase.getSegmentDurationUs(segmentNum, periodDurationUs);
    }

    @Override
    public long getFirstSegmentNum() {
      return segmentBase.getFirstSegmentNum();
    }

    @Override
    public long getFirstAvailableSegmentNum(long periodDurationUs, long nowUnixTimeUs) {
      return segmentBase.getFirstAvailableSegmentNum(periodDurationUs, nowUnixTimeUs);
    }

    @Override
    public long getSegmentCount(long periodDurationUs) {
      return segmentBase.getSegmentCount(periodDurationUs);
    }

    @Override
    public long getAvailableSegmentCount(long periodDurationUs, long nowUnixTimeUs) {
      return segmentBase.getAvailableSegmentCount(periodDurationUs, nowUnixTimeUs);
    }

    @Override
    public long getNextSegmentAvailableTimeUs(long periodDurationUs, long nowUnixTimeUs) {
      return segmentBase.getNextSegmentAvailableTimeUs(periodDurationUs, nowUnixTimeUs);
    }

    @Override
    public boolean isExplicit() {
      return segmentBase.isExplicit();
    }
  }
}
