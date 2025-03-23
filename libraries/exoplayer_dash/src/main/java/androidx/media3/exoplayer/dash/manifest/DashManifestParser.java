package androidx.media3.exoplayer.dash.manifest;

import static androidx.media3.exoplayer.dash.manifest.BaseUrl.DEFAULT_DVB_PRIORITY;
import static androidx.media3.exoplayer.dash.manifest.BaseUrl.DEFAULT_WEIGHT;
import static androidx.media3.exoplayer.dash.manifest.BaseUrl.PRIORITY_UNSET;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Pair;
import android.util.Xml;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.DrmInitData.SchemeData;
import androidx.media3.common.Format;
import androidx.media3.common.Label;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.UriUtil;
import androidx.media3.common.util.Util;
import androidx.media3.common.util.XmlPullParserUtil;
import androidx.media3.exoplayer.dash.manifest.SegmentBase.SegmentList;
import androidx.media3.exoplayer.dash.manifest.SegmentBase.SegmentTemplate;
import androidx.media3.exoplayer.dash.manifest.SegmentBase.SegmentTimelineElement;
import androidx.media3.exoplayer.dash.manifest.SegmentBase.SingleSegmentBase;
import androidx.media3.exoplayer.upstream.ParsingLoadable;
import androidx.media3.extractor.metadata.emsg.EventMessage;
import androidx.media3.extractor.mp4.PsshAtomUtil;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/** 媒体呈现描述文件的解析器。 */
@UnstableApi
public class DashManifestParser extends DefaultHandler
    implements ParsingLoadable.Parser<DashManifest> {

  private static final String TAG = "MpdParser";

  private static final Pattern FRAME_RATE_PATTERN = Pattern.compile("(\\d+)(?:/(\\d+))?");

  private static final Pattern CEA_608_ACCESSIBILITY_PATTERN = Pattern.compile("CC([1-4])=.*");
  private static final Pattern CEA_708_ACCESSIBILITY_PATTERN =
      Pattern.compile("([1-9]|[1-5][0-9]|6[0-3])=.*");

  /**
   * 将 AudioChannelConfiguration 的 value 属性（其 schemeIdUri 为 "urn:mpeg:mpegB:cicp:ChannelConfiguration"）
   * 映射到通道数，定义见 ISO 23001-8 第 8.1 节。
   */
  private static final int[] MPEG_CHANNEL_CONFIGURATION_MAPPING =
      new int[] {
        Format.NO_VALUE, 1, 2, 3, 4, 5, 6, 8, 2, 3, 4, 7, 8, 24, 8, 12, 10, 12, 14, 12, 14
      };

  private final XmlPullParserFactory xmlParserFactory;

  public DashManifestParser() {
    try {
      xmlParserFactory = XmlPullParserFactory.newInstance();
    } catch (XmlPullParserException e) {
      throw new RuntimeException("Couldn't create XmlPullParserFactory instance", e);
    }
  }

  // MPD parsing.

  @Override
  public DashManifest parse(Uri uri, InputStream inputStream) throws IOException {
    try {
      XmlPullParser xpp = xmlParserFactory.newPullParser();
      xpp.setInput(inputStream, null);
      int eventType = xpp.next();
      if (eventType != XmlPullParser.START_TAG || !"MPD".equals(xpp.getName())) {
        throw ParserException.createForMalformedManifest(
            "inputStream does not contain a valid media presentation description",
            /* cause= */ null);
      }
      return parseMediaPresentationDescription(xpp, uri);
    } catch (XmlPullParserException e) {
      throw ParserException.createForMalformedManifest(/* message= */ null, /* cause= */ e);
    }
  }

  protected DashManifest parseMediaPresentationDescription(XmlPullParser xpp, Uri documentBaseUri)
      throws XmlPullParserException, IOException {
    boolean dvbProfileDeclared =
        isDvbProfileDeclared(parseProfiles(xpp, "profiles", new String[0]));
    long availabilityStartTime = parseDateTime(xpp, "availabilityStartTime", C.TIME_UNSET);
    long durationMs = parseDuration(xpp, "mediaPresentationDuration", C.TIME_UNSET);
    long minBufferTimeMs = parseDuration(xpp, "minBufferTime", C.TIME_UNSET);
    String typeString = xpp.getAttributeValue(null, "type");
    boolean dynamic = "dynamic".equals(typeString);
    long minUpdateTimeMs =
        dynamic ? parseDuration(xpp, "minimumUpdatePeriod", C.TIME_UNSET) : C.TIME_UNSET;
    long timeShiftBufferDepthMs =
        dynamic ? parseDuration(xpp, "timeShiftBufferDepth", C.TIME_UNSET) : C.TIME_UNSET;
    long suggestedPresentationDelayMs =
        dynamic ? parseDuration(xpp, "suggestedPresentationDelay", C.TIME_UNSET) : C.TIME_UNSET;
    long publishTimeMs = parseDateTime(xpp, "publishTime", C.TIME_UNSET);
    ProgramInformation programInformation = null;
    UtcTimingElement utcTiming = null;
    Uri location = null;
    ServiceDescriptionElement serviceDescription = null;
    long baseUrlAvailabilityTimeOffsetUs = dynamic ? 0 : C.TIME_UNSET;
    BaseUrl documentBaseUrl =
        new BaseUrl(
            documentBaseUri.toString(),
            /* serviceLocation= */ documentBaseUri.toString(),
            dvbProfileDeclared ? DEFAULT_DVB_PRIORITY : PRIORITY_UNSET,
            DEFAULT_WEIGHT);
    ArrayList<BaseUrl> parentBaseUrls = Lists.newArrayList(documentBaseUrl);

    List<Period> periods = new ArrayList<>();
    ArrayList<BaseUrl> baseUrls = new ArrayList<>();
    long nextPeriodStartMs = dynamic ? C.TIME_UNSET : 0;
    boolean seenEarlyAccessPeriod = false;
    boolean seenFirstBaseUrl = false;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "BaseURL")) {
        if (!seenFirstBaseUrl) {
          baseUrlAvailabilityTimeOffsetUs =
              parseAvailabilityTimeOffsetUs(xpp, baseUrlAvailabilityTimeOffsetUs);
          seenFirstBaseUrl = true;
        }
        baseUrls.addAll(parseBaseUrl(xpp, parentBaseUrls, dvbProfileDeclared));
      } else if (XmlPullParserUtil.isStartTag(xpp, "ProgramInformation")) {
        programInformation = parseProgramInformation(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "UTCTiming")) {
        utcTiming = parseUtcTiming(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "Location")) {
        location = UriUtil.resolveToUri(documentBaseUri.toString(), xpp.nextText());
      } else if (XmlPullParserUtil.isStartTag(xpp, "ServiceDescription")) {
        serviceDescription = parseServiceDescription(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "Period") && !seenEarlyAccessPeriod) {
        Pair<Period, Long> periodWithDurationMs =
            parsePeriod(
                xpp,
                !baseUrls.isEmpty() ? baseUrls : parentBaseUrls,
                nextPeriodStartMs,
                baseUrlAvailabilityTimeOffsetUs,
                availabilityStartTime,
                timeShiftBufferDepthMs,
                dvbProfileDeclared);
        Period period = periodWithDurationMs.first;
        if (period.startMs == C.TIME_UNSET) {
          if (dynamic) {
            // This is an early access period. Ignore it. All subsequent periods must also be
            // early access.
            seenEarlyAccessPeriod = true;
          } else {
            throw ParserException.createForMalformedManifest(
                "Unable to determine start of period " + periods.size(), /* cause= */ null);
          }
        } else {
          long periodDurationMs = periodWithDurationMs.second;
          nextPeriodStartMs =
              periodDurationMs == C.TIME_UNSET ? C.TIME_UNSET : (period.startMs + periodDurationMs);
          periods.add(period);
        }
      } else {
        maybeSkipTag(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "MPD"));

    if (durationMs == C.TIME_UNSET) {
      if (nextPeriodStartMs != C.TIME_UNSET) {
        // If we know the end time of the final period, we can use it as the duration.
        durationMs = nextPeriodStartMs;
      } else if (!dynamic) {
        throw ParserException.createForMalformedManifest(
            "Unable to determine duration of static manifest.", /* cause= */ null);
      }
    }

    if (periods.isEmpty()) {
      throw ParserException.createForMalformedManifest("No periods found.", /* cause= */ null);
    }

    return buildMediaPresentationDescription(
        availabilityStartTime,
        durationMs,
        minBufferTimeMs,
        dynamic,
        minUpdateTimeMs,
        timeShiftBufferDepthMs,
        suggestedPresentationDelayMs,
        publishTimeMs,
        programInformation,
        utcTiming,
        serviceDescription,
        location,
        periods);
  }

  protected DashManifest buildMediaPresentationDescription(
      long availabilityStartTime,
      long durationMs,
      long minBufferTimeMs,
      boolean dynamic,
      long minUpdateTimeMs,
      long timeShiftBufferDepthMs,
      long suggestedPresentationDelayMs,
      long publishTimeMs,
      @Nullable ProgramInformation programInformation,
      @Nullable UtcTimingElement utcTiming,
      @Nullable ServiceDescriptionElement serviceDescription,
      @Nullable Uri location,
      List<Period> periods) {
    return new DashManifest(
        availabilityStartTime,
        durationMs,
        minBufferTimeMs,
        dynamic,
        minUpdateTimeMs,
        timeShiftBufferDepthMs,
        suggestedPresentationDelayMs,
        publishTimeMs,
        programInformation,
        utcTiming,
        serviceDescription,
        location,
        periods);
  }

  protected UtcTimingElement parseUtcTiming(XmlPullParser xpp) {
    String schemeIdUri = xpp.getAttributeValue(null, "schemeIdUri");
    String value = xpp.getAttributeValue(null, "value");
    return buildUtcTimingElement(schemeIdUri, value);
  }

  protected UtcTimingElement buildUtcTimingElement(String schemeIdUri, String value) {
    return new UtcTimingElement(schemeIdUri, value);
  }

  protected ServiceDescriptionElement parseServiceDescription(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    long targetOffsetMs = C.TIME_UNSET;
    long minOffsetMs = C.TIME_UNSET;
    long maxOffsetMs = C.TIME_UNSET;
    float minPlaybackSpeed = C.RATE_UNSET;
    float maxPlaybackSpeed = C.RATE_UNSET;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "Latency")) {
        targetOffsetMs = parseLong(xpp, "target", C.TIME_UNSET);
        minOffsetMs = parseLong(xpp, "min", C.TIME_UNSET);
        maxOffsetMs = parseLong(xpp, "max", C.TIME_UNSET);
      } else if (XmlPullParserUtil.isStartTag(xpp, "PlaybackRate")) {
        minPlaybackSpeed = parseFloat(xpp, "min", C.RATE_UNSET);
        maxPlaybackSpeed = parseFloat(xpp, "max", C.RATE_UNSET);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "ServiceDescription"));
    return new ServiceDescriptionElement(
        targetOffsetMs, minOffsetMs, maxOffsetMs, minPlaybackSpeed, maxPlaybackSpeed);
  }

  protected Pair<Period, Long> parsePeriod(
      XmlPullParser xpp,
      List<BaseUrl> parentBaseUrls,
      long defaultStartMs,
      long baseUrlAvailabilityTimeOffsetUs,
      long availabilityStartTimeMs,
      long timeShiftBufferDepthMs,
      boolean dvbProfileDeclared)
      throws XmlPullParserException, IOException {
    @Nullable String id = xpp.getAttributeValue(null, "id");
    long startMs = parseDuration(xpp, "start", defaultStartMs);
    long periodStartUnixTimeMs =
        availabilityStartTimeMs != C.TIME_UNSET ? availabilityStartTimeMs + startMs : C.TIME_UNSET;
    long durationMs = parseDuration(xpp, "duration", C.TIME_UNSET);
    @Nullable SegmentBase segmentBase = null;
    @Nullable Descriptor assetIdentifier = null;
    List<AdaptationSet> adaptationSets = new ArrayList<>();
    List<EventStream> eventStreams = new ArrayList<>();
    ArrayList<BaseUrl> baseUrls = new ArrayList<>();
    boolean seenFirstBaseUrl = false;
    long segmentBaseAvailabilityTimeOffsetUs = C.TIME_UNSET;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "BaseURL")) {
        if (!seenFirstBaseUrl) {
          baseUrlAvailabilityTimeOffsetUs =
              parseAvailabilityTimeOffsetUs(xpp, baseUrlAvailabilityTimeOffsetUs);
          seenFirstBaseUrl = true;
        }
        baseUrls.addAll(parseBaseUrl(xpp, parentBaseUrls, dvbProfileDeclared));
      } else if (XmlPullParserUtil.isStartTag(xpp, "AdaptationSet")) {
        adaptationSets.add(
            parseAdaptationSet(
                xpp,
                !baseUrls.isEmpty() ? baseUrls : parentBaseUrls,
                segmentBase,
                durationMs,
                baseUrlAvailabilityTimeOffsetUs,
                segmentBaseAvailabilityTimeOffsetUs,
                periodStartUnixTimeMs,
                timeShiftBufferDepthMs,
                dvbProfileDeclared));
      } else if (XmlPullParserUtil.isStartTag(xpp, "EventStream")) {
        eventStreams.add(parseEventStream(xpp));
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentBase")) {
        segmentBase = parseSegmentBase(xpp, /* parent= */ null);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentList")) {
        segmentBaseAvailabilityTimeOffsetUs =
            parseAvailabilityTimeOffsetUs(xpp, /* parentAvailabilityTimeOffsetUs= */ C.TIME_UNSET);
        segmentBase =
            parseSegmentList(
                xpp,
                /* parent= */ null,
                periodStartUnixTimeMs,
                durationMs,
                baseUrlAvailabilityTimeOffsetUs,
                segmentBaseAvailabilityTimeOffsetUs,
                timeShiftBufferDepthMs);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentTemplate")) {
        segmentBaseAvailabilityTimeOffsetUs =
            parseAvailabilityTimeOffsetUs(xpp, /* parentAvailabilityTimeOffsetUs= */ C.TIME_UNSET);
        segmentBase =
            parseSegmentTemplate(
                xpp,
                /* parent= */ null,
                ImmutableList.of(),
                periodStartUnixTimeMs,
                durationMs,
                baseUrlAvailabilityTimeOffsetUs,
                segmentBaseAvailabilityTimeOffsetUs,
                timeShiftBufferDepthMs);
      } else if (XmlPullParserUtil.isStartTag(xpp, "AssetIdentifier")) {
        assetIdentifier = parseDescriptor(xpp, "AssetIdentifier");
      } else {
        maybeSkipTag(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "Period"));

    return Pair.create(
        buildPeriod(id, startMs, adaptationSets, eventStreams, assetIdentifier), durationMs);
  }

  protected Period buildPeriod(
      @Nullable String id,
      long startMs,
      List<AdaptationSet> adaptationSets,
      List<EventStream> eventStreams,
      @Nullable Descriptor assetIdentifier) {
    return new Period(id, startMs, adaptationSets, eventStreams, assetIdentifier);
  }

  // AdaptationSet parsing.

  protected AdaptationSet parseAdaptationSet(
      XmlPullParser xpp,
      List<BaseUrl> parentBaseUrls,
      @Nullable SegmentBase segmentBase,
      long periodDurationMs,
      long baseUrlAvailabilityTimeOffsetUs,
      long segmentBaseAvailabilityTimeOffsetUs,
      long periodStartUnixTimeMs,
      long timeShiftBufferDepthMs,
      boolean dvbProfileDeclared)
      throws XmlPullParserException, IOException {
    long id = parseLong(xpp, "id", AdaptationSet.ID_UNSET);
    @C.TrackType int contentType = parseContentType(xpp);

    String mimeType = xpp.getAttributeValue(null, "mimeType");
    String codecs = xpp.getAttributeValue(null, "codecs");
    int width = parseInt(xpp, "width", Format.NO_VALUE);
    int height = parseInt(xpp, "height", Format.NO_VALUE);
    float frameRate = parseFrameRate(xpp, Format.NO_VALUE);
    int audioChannels = Format.NO_VALUE;
    int audioSamplingRate = parseInt(xpp, "audioSamplingRate", Format.NO_VALUE);
    String language = xpp.getAttributeValue(null, "lang");
    String label = xpp.getAttributeValue(null, "label");
    List<Label> labels = new ArrayList<>();
    String drmSchemeType = null;
    ArrayList<SchemeData> drmSchemeDatas = new ArrayList<>();
    ArrayList<Descriptor> inbandEventStreams = new ArrayList<>();
    ArrayList<Descriptor> accessibilityDescriptors = new ArrayList<>();
    ArrayList<Descriptor> roleDescriptors = new ArrayList<>();
    ArrayList<Descriptor> essentialProperties = new ArrayList<>();
    ArrayList<Descriptor> supplementalProperties = new ArrayList<>();
    List<RepresentationInfo> representationInfos = new ArrayList<>();
    ArrayList<BaseUrl> baseUrls = new ArrayList<>();

    boolean seenFirstBaseUrl = false;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "BaseURL")) {
        if (!seenFirstBaseUrl) {
          baseUrlAvailabilityTimeOffsetUs =
              parseAvailabilityTimeOffsetUs(xpp, baseUrlAvailabilityTimeOffsetUs);
          seenFirstBaseUrl = true;
        }
        baseUrls.addAll(parseBaseUrl(xpp, parentBaseUrls, dvbProfileDeclared));
      } else if (XmlPullParserUtil.isStartTag(xpp, "ContentProtection")) {
        Pair<String, SchemeData> contentProtection = parseContentProtection(xpp);
        if (contentProtection.first != null) {
          drmSchemeType = contentProtection.first;
        }
        if (contentProtection.second != null) {
          drmSchemeDatas.add(contentProtection.second);
        }
      } else if (XmlPullParserUtil.isStartTag(xpp, "ContentComponent")) {
        language = checkLanguageConsistency(language, xpp.getAttributeValue(null, "lang"));
        contentType = checkContentTypeConsistency(contentType, parseContentType(xpp));
      } else if (XmlPullParserUtil.isStartTag(xpp, "Role")) {
        roleDescriptors.add(parseDescriptor(xpp, "Role"));
      } else if (XmlPullParserUtil.isStartTag(xpp, "AudioChannelConfiguration")) {
        audioChannels = parseAudioChannelConfiguration(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "Accessibility")) {
        accessibilityDescriptors.add(parseDescriptor(xpp, "Accessibility"));
      } else if (XmlPullParserUtil.isStartTag(xpp, "EssentialProperty")) {
        essentialProperties.add(parseDescriptor(xpp, "EssentialProperty"));
      } else if (XmlPullParserUtil.isStartTag(xpp, "SupplementalProperty")) {
        supplementalProperties.add(parseDescriptor(xpp, "SupplementalProperty"));
      } else if (XmlPullParserUtil.isStartTag(xpp, "Representation")) {
        RepresentationInfo representationInfo =
            parseRepresentation(
                xpp,
                !baseUrls.isEmpty() ? baseUrls : parentBaseUrls,
                mimeType,
                codecs,
                width,
                height,
                frameRate,
                audioChannels,
                audioSamplingRate,
                language,
                roleDescriptors,
                accessibilityDescriptors,
                essentialProperties,
                supplementalProperties,
                segmentBase,
                periodStartUnixTimeMs,
                periodDurationMs,
                baseUrlAvailabilityTimeOffsetUs,
                segmentBaseAvailabilityTimeOffsetUs,
                timeShiftBufferDepthMs,
                dvbProfileDeclared);
        contentType =
            checkContentTypeConsistency(
                contentType, MimeTypes.getTrackType(representationInfo.format.sampleMimeType));
        representationInfos.add(representationInfo);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentBase")) {
        segmentBase = parseSegmentBase(xpp, (SingleSegmentBase) segmentBase);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentList")) {
        segmentBaseAvailabilityTimeOffsetUs =
            parseAvailabilityTimeOffsetUs(xpp, segmentBaseAvailabilityTimeOffsetUs);
        segmentBase =
            parseSegmentList(
                xpp,
                (SegmentList) segmentBase,
                periodStartUnixTimeMs,
                periodDurationMs,
                baseUrlAvailabilityTimeOffsetUs,
                segmentBaseAvailabilityTimeOffsetUs,
                timeShiftBufferDepthMs);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentTemplate")) {
        segmentBaseAvailabilityTimeOffsetUs =
            parseAvailabilityTimeOffsetUs(xpp, segmentBaseAvailabilityTimeOffsetUs);
        segmentBase =
            parseSegmentTemplate(
                xpp,
                (SegmentTemplate) segmentBase,
                supplementalProperties,
                periodStartUnixTimeMs,
                periodDurationMs,
                baseUrlAvailabilityTimeOffsetUs,
                segmentBaseAvailabilityTimeOffsetUs,
                timeShiftBufferDepthMs);
      } else if (XmlPullParserUtil.isStartTag(xpp, "InbandEventStream")) {
        inbandEventStreams.add(parseDescriptor(xpp, "InbandEventStream"));
      } else if (XmlPullParserUtil.isStartTag(xpp, "Label")) {
        labels.add(parseLabel(xpp));
      } else if (XmlPullParserUtil.isStartTag(xpp)) {
        parseAdaptationSetChild(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "AdaptationSet"));

    // Build the representations.
    List<Representation> representations = new ArrayList<>(representationInfos.size());
    for (int i = 0; i < representationInfos.size(); i++) {
      representations.add(
          buildRepresentation(
              representationInfos.get(i),
              label,
              labels,
              drmSchemeType,
              drmSchemeDatas,
              inbandEventStreams));
    }

    return buildAdaptationSet(
        id,
        contentType,
        representations,
        accessibilityDescriptors,
        essentialProperties,
        supplementalProperties);
  }

  protected AdaptationSet buildAdaptationSet(
      long id,
      @C.TrackType int contentType,
      List<Representation> representations,
      List<Descriptor> accessibilityDescriptors,
      List<Descriptor> essentialProperties,
      List<Descriptor> supplementalProperties) {
    return new AdaptationSet(
        id,
        contentType,
        representations,
        accessibilityDescriptors,
        essentialProperties,
        supplementalProperties);
  }

  protected @C.TrackType int parseContentType(XmlPullParser xpp) {
    String contentType = xpp.getAttributeValue(null, "contentType");
    return TextUtils.isEmpty(contentType)
        ? C.TRACK_TYPE_UNKNOWN
        : MimeTypes.BASE_TYPE_AUDIO.equals(contentType)
            ? C.TRACK_TYPE_AUDIO
            : MimeTypes.BASE_TYPE_VIDEO.equals(contentType)
                ? C.TRACK_TYPE_VIDEO
                : MimeTypes.BASE_TYPE_TEXT.equals(contentType)
                    ? C.TRACK_TYPE_TEXT
                    : MimeTypes.BASE_TYPE_IMAGE.equals(contentType)
                        ? C.TRACK_TYPE_IMAGE
                        : C.TRACK_TYPE_UNKNOWN;
  }

  /**
   * 解析一个 ContentProtection 元素。
   *
   * @param xpp 用于读取的解析器。
   * @throws XmlPullParserException 如果解析元素时发生错误。
   * @throws IOException 如果读取元素时发生错误。
   * @return 从 ContentProtection 元素解析出的方案类型和/或 {@link SchemeData}。
   *     根据正在解析的 ContentProtection 元素，两者或其中之一可能为 null。
   */
  protected Pair<@NullableType String, @NullableType SchemeData> parseContentProtection(
      XmlPullParser xpp) throws XmlPullParserException, IOException {
    String schemeType = null;
    String licenseServerUrl = null;
    byte[] data = null;
    UUID uuid = null;

    String schemeIdUri = xpp.getAttributeValue(null, "schemeIdUri");
    if (schemeIdUri != null) {
      switch (Ascii.toLowerCase(schemeIdUri)) {
        case "urn:mpeg:dash:mp4protection:2011":
          schemeType = xpp.getAttributeValue(null, "value");
          String defaultKid = XmlPullParserUtil.getAttributeValueIgnorePrefix(xpp, "default_KID");
          if (!TextUtils.isEmpty(defaultKid)
              && !"00000000-0000-0000-0000-000000000000".equals(defaultKid)) {
            String[] defaultKidStrings = defaultKid.split("\\s+");
            UUID[] defaultKids = new UUID[defaultKidStrings.length];
            for (int i = 0; i < defaultKidStrings.length; i++) {
              defaultKids[i] = UUID.fromString(defaultKidStrings[i]);
            }
            data = PsshAtomUtil.buildPsshAtom(C.COMMON_PSSH_UUID, defaultKids, null);
            uuid = C.COMMON_PSSH_UUID;
          } else {
            Log.w(
                TAG,
                "Ignoring <ContentProtection> with schemeIdUri=\"urn:mpeg:dash:mp4protection:2011\""
                    + " (ClearKey) due to missing required default_KID attribute.");
          }
          break;
        case "urn:uuid:9a04f079-9840-4286-ab92-e65be0885f95":
          uuid = C.PLAYREADY_UUID;
          break;
        case "urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed":
          uuid = C.WIDEVINE_UUID;
          break;
        case "urn:uuid:e2719d58-a985-b3c9-781a-b030af78d30e":
          uuid = C.CLEARKEY_UUID;
          break;
        default:
          break;
      }
    }

    do {
      xpp.next();
      if ((XmlPullParserUtil.isStartTag(xpp, "clearkey:Laurl")
              || XmlPullParserUtil.isStartTag(xpp, "dashif:Laurl"))
          && xpp.next() == XmlPullParser.TEXT) {
        licenseServerUrl = xpp.getText();
      } else if (XmlPullParserUtil.isStartTag(xpp, "ms:laurl")) {
        licenseServerUrl = xpp.getAttributeValue(null, "licenseUrl");
      } else if (data == null
          && XmlPullParserUtil.isStartTagIgnorePrefix(xpp, "pssh")
          && xpp.next() == XmlPullParser.TEXT) {
        // The cenc:pssh element is defined in 23001-7:2015.
        data = Base64.decode(xpp.getText(), Base64.DEFAULT);
        uuid = PsshAtomUtil.parseUuid(data);
        if (uuid == null) {
          Log.w(TAG, "Skipping malformed cenc:pssh data");
          data = null;
        }
      } else if (data == null
          && C.PLAYREADY_UUID.equals(uuid)
          && XmlPullParserUtil.isStartTag(xpp, "mspr:pro")
          && xpp.next() == XmlPullParser.TEXT) {
        // The mspr:pro element is defined in DASH Content Protection using Microsoft PlayReady.
        data =
            PsshAtomUtil.buildPsshAtom(
                C.PLAYREADY_UUID, Base64.decode(xpp.getText(), Base64.DEFAULT));
      } else {
        maybeSkipTag(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "ContentProtection"));
    SchemeData schemeData =
        uuid != null ? new SchemeData(uuid, licenseServerUrl, MimeTypes.VIDEO_MP4, data) : null;
    return Pair.create(schemeType, schemeData);
  }

  /**
   * 解析 {@link AdaptationSet} 元素的子元素。
   *
   * <p>用于解析未被其他方法专门处理的子元素。
   *
   * @param xpp 用于解析子元素的 {@link XmlPullParser}。
   * @throws XmlPullParserException 如果解析元素时发生错误。
   * @throws IOException 如果读取元素时发生错误。
   */
  protected void parseAdaptationSetChild(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    maybeSkipTag(xpp);
  }

  // Representation parsing.

  protected RepresentationInfo parseRepresentation(
      XmlPullParser xpp,
      List<BaseUrl> parentBaseUrls,
      @Nullable String adaptationSetMimeType,
      @Nullable String adaptationSetCodecs,
      int adaptationSetWidth,
      int adaptationSetHeight,
      float adaptationSetFrameRate,
      int adaptationSetAudioChannels,
      int adaptationSetAudioSamplingRate,
      @Nullable String adaptationSetLanguage,
      List<Descriptor> adaptationSetRoleDescriptors,
      List<Descriptor> adaptationSetAccessibilityDescriptors,
      List<Descriptor> adaptationSetEssentialProperties,
      List<Descriptor> adaptationSetSupplementalProperties,
      @Nullable SegmentBase segmentBase,
      long periodStartUnixTimeMs,
      long periodDurationMs,
      long baseUrlAvailabilityTimeOffsetUs,
      long segmentBaseAvailabilityTimeOffsetUs,
      long timeShiftBufferDepthMs,
      boolean dvbProfileDeclared)
      throws XmlPullParserException, IOException {
    String id = xpp.getAttributeValue(null, "id");
    int bandwidth = parseInt(xpp, "bandwidth", Format.NO_VALUE);

    String mimeType = parseString(xpp, "mimeType", adaptationSetMimeType);
    String codecs = parseString(xpp, "codecs", adaptationSetCodecs);
    int width = parseInt(xpp, "width", adaptationSetWidth);
    int height = parseInt(xpp, "height", adaptationSetHeight);
    float frameRate = parseFrameRate(xpp, adaptationSetFrameRate);
    int audioChannels = adaptationSetAudioChannels;
    int audioSamplingRate = parseInt(xpp, "audioSamplingRate", adaptationSetAudioSamplingRate);
    String drmSchemeType = null;
    ArrayList<SchemeData> drmSchemeDatas = new ArrayList<>();
    ArrayList<Descriptor> inbandEventStreams = new ArrayList<>();
    ArrayList<Descriptor> essentialProperties = new ArrayList<>(adaptationSetEssentialProperties);
    ArrayList<Descriptor> supplementalProperties =
        new ArrayList<>(adaptationSetSupplementalProperties);
    ArrayList<BaseUrl> baseUrls = new ArrayList<>();

    boolean seenFirstBaseUrl = false;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "BaseURL")) {
        if (!seenFirstBaseUrl) {
          baseUrlAvailabilityTimeOffsetUs =
              parseAvailabilityTimeOffsetUs(xpp, baseUrlAvailabilityTimeOffsetUs);
          seenFirstBaseUrl = true;
        }
        baseUrls.addAll(parseBaseUrl(xpp, parentBaseUrls, dvbProfileDeclared));
      } else if (XmlPullParserUtil.isStartTag(xpp, "AudioChannelConfiguration")) {
        audioChannels = parseAudioChannelConfiguration(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentBase")) {
        segmentBase = parseSegmentBase(xpp, (SingleSegmentBase) segmentBase);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentList")) {
        segmentBaseAvailabilityTimeOffsetUs =
            parseAvailabilityTimeOffsetUs(xpp, segmentBaseAvailabilityTimeOffsetUs);
        segmentBase =
            parseSegmentList(
                xpp,
                (SegmentList) segmentBase,
                periodStartUnixTimeMs,
                periodDurationMs,
                baseUrlAvailabilityTimeOffsetUs,
                segmentBaseAvailabilityTimeOffsetUs,
                timeShiftBufferDepthMs);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentTemplate")) {
        segmentBaseAvailabilityTimeOffsetUs =
            parseAvailabilityTimeOffsetUs(xpp, segmentBaseAvailabilityTimeOffsetUs);
        segmentBase =
            parseSegmentTemplate(
                xpp,
                (SegmentTemplate) segmentBase,
                adaptationSetSupplementalProperties,
                periodStartUnixTimeMs,
                periodDurationMs,
                baseUrlAvailabilityTimeOffsetUs,
                segmentBaseAvailabilityTimeOffsetUs,
                timeShiftBufferDepthMs);
      } else if (XmlPullParserUtil.isStartTag(xpp, "ContentProtection")) {
        Pair<String, SchemeData> contentProtection = parseContentProtection(xpp);
        if (contentProtection.first != null) {
          drmSchemeType = contentProtection.first;
        }
        if (contentProtection.second != null) {
          drmSchemeDatas.add(contentProtection.second);
        }
      } else if (XmlPullParserUtil.isStartTag(xpp, "InbandEventStream")) {
        inbandEventStreams.add(parseDescriptor(xpp, "InbandEventStream"));
      } else if (XmlPullParserUtil.isStartTag(xpp, "EssentialProperty")) {
        essentialProperties.add(parseDescriptor(xpp, "EssentialProperty"));
      } else if (XmlPullParserUtil.isStartTag(xpp, "SupplementalProperty")) {
        supplementalProperties.add(parseDescriptor(xpp, "SupplementalProperty"));
      } else {
        maybeSkipTag(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "Representation"));

    Format format =
        buildFormat(
            id,
            mimeType,
            width,
            height,
            frameRate,
            audioChannels,
            audioSamplingRate,
            bandwidth,
            adaptationSetLanguage,
            adaptationSetRoleDescriptors,
            adaptationSetAccessibilityDescriptors,
            codecs,
            essentialProperties,
            supplementalProperties);
    segmentBase = segmentBase != null ? segmentBase : new SingleSegmentBase();

    return new RepresentationInfo(
        format,
        !baseUrls.isEmpty() ? baseUrls : parentBaseUrls,
        segmentBase,
        drmSchemeType,
        drmSchemeDatas,
        inbandEventStreams,
        essentialProperties,
        supplementalProperties,
        Representation.REVISION_ID_DEFAULT);
  }

  protected Format buildFormat(
      @Nullable String id,
      @Nullable String containerMimeType,
      int width,
      int height,
      float frameRate,
      int audioChannels,
      int audioSamplingRate,
      int bitrate,
      @Nullable String language,
      List<Descriptor> roleDescriptors,
      List<Descriptor> accessibilityDescriptors,
      @Nullable String codecs,
      List<Descriptor> essentialProperties,
      List<Descriptor> supplementalProperties) {
    @Nullable String sampleMimeType = getSampleMimeType(containerMimeType, codecs);
    if (MimeTypes.AUDIO_E_AC3.equals(sampleMimeType)) {
      sampleMimeType = parseEac3SupplementalProperties(supplementalProperties);
      if (MimeTypes.AUDIO_E_AC3_JOC.equals(sampleMimeType)) {
        codecs = MimeTypes.CODEC_E_AC3_JOC;
      }
    }
    @C.SelectionFlags int selectionFlags = parseSelectionFlagsFromRoleDescriptors(roleDescriptors);
    @C.RoleFlags int roleFlags = parseRoleFlagsFromRoleDescriptors(roleDescriptors);
    roleFlags |= parseRoleFlagsFromAccessibilityDescriptors(accessibilityDescriptors);
    roleFlags |= parseRoleFlagsFromProperties(essentialProperties);
    roleFlags |= parseRoleFlagsFromProperties(supplementalProperties);
    @Nullable Pair<Integer, Integer> tileCounts = parseTileCountFromProperties(essentialProperties);

    Format.Builder formatBuilder =
        new Format.Builder()
            .setId(id)
            .setContainerMimeType(containerMimeType)
            .setSampleMimeType(sampleMimeType)
            .setCodecs(codecs)
            .setPeakBitrate(bitrate)
            .setSelectionFlags(selectionFlags)
            .setRoleFlags(roleFlags)
            .setLanguage(language)
            .setTileCountHorizontal(tileCounts != null ? tileCounts.first : Format.NO_VALUE)
            .setTileCountVertical(tileCounts != null ? tileCounts.second : Format.NO_VALUE);

    if (MimeTypes.isVideo(sampleMimeType)) {
      formatBuilder.setWidth(width).setHeight(height).setFrameRate(frameRate);
    } else if (MimeTypes.isAudio(sampleMimeType)) {
      formatBuilder.setChannelCount(audioChannels).setSampleRate(audioSamplingRate);
    } else if (MimeTypes.isText(sampleMimeType)) {
      int accessibilityChannel = Format.NO_VALUE;
      if (MimeTypes.APPLICATION_CEA608.equals(sampleMimeType)) {
        accessibilityChannel = parseCea608AccessibilityChannel(accessibilityDescriptors);
      } else if (MimeTypes.APPLICATION_CEA708.equals(sampleMimeType)) {
        accessibilityChannel = parseCea708AccessibilityChannel(accessibilityDescriptors);
      }
      formatBuilder.setAccessibilityChannel(accessibilityChannel);
    } else if (MimeTypes.isImage(sampleMimeType)) {
      formatBuilder.setWidth(width).setHeight(height);
    }

    return formatBuilder.build();
  }

  protected Representation buildRepresentation(
      RepresentationInfo representationInfo,
      @Nullable String label,
      List<Label> labels,
      @Nullable String extraDrmSchemeType,
      ArrayList<SchemeData> extraDrmSchemeDatas,
      ArrayList<Descriptor> extraInbandEventStreams) {
    Format.Builder formatBuilder = representationInfo.format.buildUpon();
    if (label != null && labels.isEmpty()) {
      formatBuilder.setLabel(label);
    } else {
      formatBuilder.setLabels(labels);
    }
    @Nullable String drmSchemeType = representationInfo.drmSchemeType;
    if (drmSchemeType == null) {
      drmSchemeType = extraDrmSchemeType;
    }
    ArrayList<SchemeData> drmSchemeDatas = representationInfo.drmSchemeDatas;
    drmSchemeDatas.addAll(extraDrmSchemeDatas);
    if (!drmSchemeDatas.isEmpty()) {
      fillInClearKeyInformation(drmSchemeDatas);
      filterRedundantIncompleteSchemeDatas(drmSchemeDatas);
      formatBuilder.setDrmInitData(new DrmInitData(drmSchemeType, drmSchemeDatas));
    }
    ArrayList<Descriptor> inbandEventStreams = representationInfo.inbandEventStreams;
    inbandEventStreams.addAll(extraInbandEventStreams);
    return Representation.newInstance(
        representationInfo.revisionId,
        formatBuilder.build(),
        representationInfo.baseUrls,
        representationInfo.segmentBase,
        inbandEventStreams,
        representationInfo.essentialProperties,
        representationInfo.supplementalProperties,
        /* cacheKey= */ null);
  }

  // SegmentBase, SegmentList and SegmentTemplate parsing.

  protected SingleSegmentBase parseSegmentBase(
      XmlPullParser xpp, @Nullable SingleSegmentBase parent)
      throws XmlPullParserException, IOException {

    long timescale = parseLong(xpp, "timescale", parent != null ? parent.timescale : 1);
    long presentationTimeOffset =
        parseLong(
            xpp, "presentationTimeOffset", parent != null ? parent.presentationTimeOffset : 0);

    long indexStart = parent != null ? parent.indexStart : 0;
    long indexLength = parent != null ? parent.indexLength : 0;
    String indexRangeText = xpp.getAttributeValue(null, "indexRange");
    if (indexRangeText != null) {
      String[] indexRange = indexRangeText.split("-");
      indexStart = Long.parseLong(indexRange[0]);
      indexLength = Long.parseLong(indexRange[1]) - indexStart + 1;
    }

    @Nullable RangedUri initialization = parent != null ? parent.initialization : null;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "Initialization")) {
        initialization = parseInitialization(xpp);
      } else {
        maybeSkipTag(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "SegmentBase"));

    return buildSingleSegmentBase(
        initialization, timescale, presentationTimeOffset, indexStart, indexLength);
  }

  protected SingleSegmentBase buildSingleSegmentBase(
      RangedUri initialization,
      long timescale,
      long presentationTimeOffset,
      long indexStart,
      long indexLength) {
    return new SingleSegmentBase(
        initialization, timescale, presentationTimeOffset, indexStart, indexLength);
  }

  protected SegmentList parseSegmentList(
      XmlPullParser xpp,
      @Nullable SegmentList parent,
      long periodStartUnixTimeMs,
      long periodDurationMs,
      long baseUrlAvailabilityTimeOffsetUs,
      long segmentBaseAvailabilityTimeOffsetUs,
      long timeShiftBufferDepthMs)
      throws XmlPullParserException, IOException {

    long timescale = parseLong(xpp, "timescale", parent != null ? parent.timescale : 1);
    long presentationTimeOffset =
        parseLong(
            xpp, "presentationTimeOffset", parent != null ? parent.presentationTimeOffset : 0);
    long duration = parseLong(xpp, "duration", parent != null ? parent.duration : C.TIME_UNSET);
    long startNumber = parseLong(xpp, "startNumber", parent != null ? parent.startNumber : 1);
    long availabilityTimeOffsetUs =
        getFinalAvailabilityTimeOffset(
            baseUrlAvailabilityTimeOffsetUs, segmentBaseAvailabilityTimeOffsetUs);

    RangedUri initialization = null;
    List<SegmentTimelineElement> timeline = null;
    List<RangedUri> segments = null;

    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "Initialization")) {
        initialization = parseInitialization(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentTimeline")) {
        timeline = parseSegmentTimeline(xpp, timescale, periodDurationMs);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentURL")) {
        if (segments == null) {
          segments = new ArrayList<>();
        }
        segments.add(parseSegmentUrl(xpp));
      } else {
        maybeSkipTag(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "SegmentList"));

    if (parent != null) {
      initialization = initialization != null ? initialization : parent.initialization;
      timeline = timeline != null ? timeline : parent.segmentTimeline;
      segments = segments != null ? segments : parent.mediaSegments;
    }

    return buildSegmentList(
        initialization,
        timescale,
        presentationTimeOffset,
        startNumber,
        duration,
        timeline,
        availabilityTimeOffsetUs,
        segments,
        timeShiftBufferDepthMs,
        periodStartUnixTimeMs);
  }

  protected SegmentList buildSegmentList(
      RangedUri initialization,
      long timescale,
      long presentationTimeOffset,
      long startNumber,
      long duration,
      @Nullable List<SegmentTimelineElement> timeline,
      long availabilityTimeOffsetUs,
      @Nullable List<RangedUri> segments,
      long timeShiftBufferDepthMs,
      long periodStartUnixTimeMs) {
    return new SegmentList(
        initialization,
        timescale,
        presentationTimeOffset,
        startNumber,
        duration,
        timeline,
        availabilityTimeOffsetUs,
        segments,
        Util.msToUs(timeShiftBufferDepthMs),
        Util.msToUs(periodStartUnixTimeMs));
  }

  protected SegmentTemplate parseSegmentTemplate(
      XmlPullParser xpp,
      @Nullable SegmentTemplate parent,
      List<Descriptor> adaptationSetSupplementalProperties,
      long periodStartUnixTimeMs,
      long periodDurationMs,
      long baseUrlAvailabilityTimeOffsetUs,
      long segmentBaseAvailabilityTimeOffsetUs,
      long timeShiftBufferDepthMs)
      throws XmlPullParserException, IOException {
    long timescale = parseLong(xpp, "timescale", parent != null ? parent.timescale : 1);
    long presentationTimeOffset =
        parseLong(
            xpp, "presentationTimeOffset", parent != null ? parent.presentationTimeOffset : 0);
    long duration = parseLong(xpp, "duration", parent != null ? parent.duration : C.TIME_UNSET);
    long startNumber = parseLong(xpp, "startNumber", parent != null ? parent.startNumber : 1);
    long endNumber =
        parseLastSegmentNumberSupplementalProperty(adaptationSetSupplementalProperties);
    long availabilityTimeOffsetUs =
        getFinalAvailabilityTimeOffset(
            baseUrlAvailabilityTimeOffsetUs, segmentBaseAvailabilityTimeOffsetUs);

    UrlTemplate mediaTemplate =
        parseUrlTemplate(xpp, "media", parent != null ? parent.mediaTemplate : null);
    UrlTemplate initializationTemplate =
        parseUrlTemplate(
            xpp, "initialization", parent != null ? parent.initializationTemplate : null);

    RangedUri initialization = null;
    List<SegmentTimelineElement> timeline = null;

    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "Initialization")) {
        initialization = parseInitialization(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentTimeline")) {
        timeline = parseSegmentTimeline(xpp, timescale, periodDurationMs);
      } else {
        maybeSkipTag(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "SegmentTemplate"));

    if (parent != null) {
      initialization = initialization != null ? initialization : parent.initialization;
      timeline = timeline != null ? timeline : parent.segmentTimeline;
    }

    return buildSegmentTemplate(
        initialization,
        timescale,
        presentationTimeOffset,
        startNumber,
        endNumber,
        duration,
        timeline,
        availabilityTimeOffsetUs,
        initializationTemplate,
        mediaTemplate,
        timeShiftBufferDepthMs,
        periodStartUnixTimeMs);
  }

  protected SegmentTemplate buildSegmentTemplate(
      RangedUri initialization,
      long timescale,
      long presentationTimeOffset,
      long startNumber,
      long endNumber,
      long duration,
      List<SegmentTimelineElement> timeline,
      long availabilityTimeOffsetUs,
      @Nullable UrlTemplate initializationTemplate,
      @Nullable UrlTemplate mediaTemplate,
      long timeShiftBufferDepthMs,
      long periodStartUnixTimeMs) {
    return new SegmentTemplate(
        initialization,
        timescale,
        presentationTimeOffset,
        startNumber,
        endNumber,
        duration,
        timeline,
        availabilityTimeOffsetUs,
        initializationTemplate,
        mediaTemplate,
        Util.msToUs(timeShiftBufferDepthMs),
        Util.msToUs(periodStartUnixTimeMs));
  }

  /**
   * 解析清单中的单个 EventStream 节点。
   *
   * @param xpp 当前的 XML 解析器。
   * @return 从该 EventStream 节点解析出的 {@link EventStream}。
   * @throws XmlPullParserException 如果解析该节点时发生错误。
   * @throws IOException 如果从底层输入流读取时发生错误。
   */
  protected EventStream parseEventStream(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    String schemeIdUri = parseString(xpp, "schemeIdUri", "");
    String value = parseString(xpp, "value", "");
    long timescale = parseLong(xpp, "timescale", 1);
    long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset", 0);
    List<Pair<Long, EventMessage>> eventMessages = new ArrayList<>();
    ByteArrayOutputStream scratchOutputStream = new ByteArrayOutputStream(512);
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "Event")) {
        Pair<Long, EventMessage> event =
            parseEvent(
                xpp, schemeIdUri, value, timescale, presentationTimeOffset, scratchOutputStream);
        eventMessages.add(event);
      } else {
        maybeSkipTag(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "EventStream"));

    long[] presentationTimesUs = new long[eventMessages.size()];
    EventMessage[] events = new EventMessage[eventMessages.size()];
    for (int i = 0; i < eventMessages.size(); i++) {
      Pair<Long, EventMessage> event = eventMessages.get(i);
      presentationTimesUs[i] = event.first;
      events[i] = event.second;
    }
    return buildEventStream(schemeIdUri, value, timescale, presentationTimesUs, events);
  }

  protected EventStream buildEventStream(
      String schemeIdUri,
      String value,
      long timescale,
      long[] presentationTimesUs,
      EventMessage[] events) {
    return new EventStream(schemeIdUri, value, timescale, presentationTimesUs, events);
  }

  /**
   * 解析清单中的单个 Event 节点。
   *
   * @param xpp 当前的 XML 解析器。
   * @param schemeIdUri 父 EventStream 的 schemeIdUri。
   * @param value 父 EventStream 的 schemeIdUri。
   * @param timescale 父 EventStream 的时间刻度。
   * @param presentationTimeOffset 父 EventStream 的未缩放表示时间偏移量。
   * @param scratchOutputStream 解析事件对象时使用的 {@link ByteArrayOutputStream}。
   * @return 包含节点表示时间戳（以微秒为单位）和解析出的 {@link EventMessage} 的键值对。
   * @throws XmlPullParserException 如果解析该节点时发生错误。
   * @throws IOException 如果从底层输入流读取时发生错误。
   */
  protected Pair<Long, EventMessage> parseEvent(
      XmlPullParser xpp,
      String schemeIdUri,
      String value,
      long timescale,
      long presentationTimeOffset,
      ByteArrayOutputStream scratchOutputStream)
      throws IOException, XmlPullParserException {
    long id = parseLong(xpp, "id", 0);
    long duration = parseLong(xpp, "duration", C.TIME_UNSET);
    long presentationTime = parseLong(xpp, "presentationTime", 0);
    long durationMs = Util.scaleLargeTimestamp(duration, C.MILLIS_PER_SECOND, timescale);
    long presentationTimesUs =
        Util.scaleLargeTimestamp(
            presentationTime - presentationTimeOffset, C.MICROS_PER_SECOND, timescale);
    String messageData = parseString(xpp, "messageData", null);
    byte[] eventObject = parseEventObject(xpp, scratchOutputStream);
    return Pair.create(
        presentationTimesUs,
        buildEvent(
            schemeIdUri,
            value,
            id,
            durationMs,
            messageData == null ? eventObject : Util.getUtf8Bytes(messageData)));
  }

  /**
   * 解析事件对象。
   *
   * @param xpp 当前的 XML 解析器。
   * @param scratchOutputStream 解析对象时使用的 {@link ByteArrayOutputStream}。
   * @return 序列化的字节数组。
   * @throws XmlPullParserException 如果解析该节点时发生错误。
   * @throws IOException 如果从底层输入流读取时发生错误。
   */
  protected byte[] parseEventObject(XmlPullParser xpp, ByteArrayOutputStream scratchOutputStream)
      throws XmlPullParserException, IOException {
    scratchOutputStream.reset();
    XmlSerializer xmlSerializer = Xml.newSerializer();
    xmlSerializer.setOutput(scratchOutputStream, StandardCharsets.UTF_8.name());
    // Start reading everything between <Event> and </Event>, and serialize them into an Xml
    // byte array.
    xpp.nextToken();
    while (!XmlPullParserUtil.isEndTag(xpp, "Event")) {
      switch (xpp.getEventType()) {
        case XmlPullParser.START_DOCUMENT:
          xmlSerializer.startDocument(null, false);
          break;
        case XmlPullParser.END_DOCUMENT:
          xmlSerializer.endDocument();
          break;
        case XmlPullParser.START_TAG:
          xmlSerializer.startTag(xpp.getNamespace(), xpp.getName());
          for (int i = 0; i < xpp.getAttributeCount(); i++) {
            xmlSerializer.attribute(
                xpp.getAttributeNamespace(i), xpp.getAttributeName(i), xpp.getAttributeValue(i));
          }
          break;
        case XmlPullParser.END_TAG:
          xmlSerializer.endTag(xpp.getNamespace(), xpp.getName());
          break;
        case XmlPullParser.TEXT:
          xmlSerializer.text(xpp.getText());
          break;
        case XmlPullParser.CDSECT:
          xmlSerializer.cdsect(xpp.getText());
          break;
        case XmlPullParser.ENTITY_REF:
          xmlSerializer.entityRef(xpp.getText());
          break;
        case XmlPullParser.IGNORABLE_WHITESPACE:
          xmlSerializer.ignorableWhitespace(xpp.getText());
          break;
        case XmlPullParser.PROCESSING_INSTRUCTION:
          xmlSerializer.processingInstruction(xpp.getText());
          break;
        case XmlPullParser.COMMENT:
          xmlSerializer.comment(xpp.getText());
          break;
        case XmlPullParser.DOCDECL:
          xmlSerializer.docdecl(xpp.getText());
          break;
        default: // fall out
      }
      xpp.nextToken();
    }
    xmlSerializer.flush();
    return scratchOutputStream.toByteArray();
  }

  protected EventMessage buildEvent(
      String schemeIdUri, String value, long id, long durationMs, byte[] messageData) {
    return new EventMessage(schemeIdUri, value, durationMs, id, messageData);
  }

  protected List<SegmentTimelineElement> parseSegmentTimeline(
      XmlPullParser xpp, long timescale, long periodDurationMs)
      throws XmlPullParserException, IOException {
    List<SegmentTimelineElement> segmentTimeline = new ArrayList<>();
    long startTime = 0;
    long elementDuration = C.TIME_UNSET;
    int elementRepeatCount = 0;
    boolean havePreviousTimelineElement = false;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "S")) {
        long newStartTime = parseLong(xpp, "t", C.TIME_UNSET);
        if (havePreviousTimelineElement) {
          startTime =
              addSegmentTimelineElementsToList(
                  segmentTimeline,
                  startTime,
                  elementDuration,
                  elementRepeatCount,
                  /* endTime= */ newStartTime);
        }
        if (newStartTime != C.TIME_UNSET) {
          startTime = newStartTime;
        }
        elementDuration = parseLong(xpp, "d", C.TIME_UNSET);
        elementRepeatCount = parseInt(xpp, "r", 0);
        havePreviousTimelineElement = true;
      } else {
        maybeSkipTag(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "SegmentTimeline"));
    if (havePreviousTimelineElement) {
      long periodDuration = Util.scaleLargeTimestamp(periodDurationMs, timescale, 1000);
      addSegmentTimelineElementsToList(
          segmentTimeline,
          startTime,
          elementDuration,
          elementRepeatCount,
          /* endTime= */ periodDuration);
    }
    return segmentTimeline;
  }

  /**
   * 为一个 S 标签添加时间线元素到分段时间线中。
   *
   * @param startTime 第一个时间线元素的开始时间。
   * @param elementDuration 一个时间线元素的持续时间。
   * @param elementRepeatCount 时间线元素的数量减一。可能为负数，表示数量由总持续时间和元素持续时间决定。
   * @param endTime 此 S 标签的最后一个时间线元素的结束时间，如果未知则为 {@link C#TIME_UNSET}。仅在 {@code repeatCount} 为负数时需要。
   * @return 计算得出的下一个开始时间。
   */
  private long addSegmentTimelineElementsToList(
      List<SegmentTimelineElement> segmentTimeline,
      long startTime,
      long elementDuration,
      int elementRepeatCount,
      long endTime) {
    int count =
        elementRepeatCount >= 0
            ? 1 + elementRepeatCount
            : (int) Util.ceilDivide(endTime - startTime, elementDuration);
    for (int i = 0; i < count; i++) {
      segmentTimeline.add(buildSegmentTimelineElement(startTime, elementDuration));
      startTime += elementDuration;
    }
    return startTime;
  }

  protected SegmentTimelineElement buildSegmentTimelineElement(long startTime, long duration) {
    return new SegmentTimelineElement(startTime, duration);
  }

  @Nullable
  protected UrlTemplate parseUrlTemplate(
      XmlPullParser xpp, String name, @Nullable UrlTemplate defaultValue) {
    String valueString = xpp.getAttributeValue(null, name);
    if (valueString != null) {
      return UrlTemplate.compile(valueString);
    }
    return defaultValue;
  }

  protected RangedUri parseInitialization(XmlPullParser xpp) {
    return parseRangedUrl(xpp, "sourceURL", "range");
  }

  protected RangedUri parseSegmentUrl(XmlPullParser xpp) {
    return parseRangedUrl(xpp, "media", "mediaRange");
  }

  protected RangedUri parseRangedUrl(
      XmlPullParser xpp, String urlAttribute, String rangeAttribute) {
    String urlText = xpp.getAttributeValue(null, urlAttribute);
    long rangeStart = 0;
    long rangeLength = C.LENGTH_UNSET;
    String rangeText = xpp.getAttributeValue(null, rangeAttribute);
    if (rangeText != null) {
      String[] rangeTextArray = rangeText.split("-");
      rangeStart = Long.parseLong(rangeTextArray[0]);
      if (rangeTextArray.length == 2) {
        rangeLength = Long.parseLong(rangeTextArray[1]) - rangeStart + 1;
      }
    }
    return buildRangedUri(urlText, rangeStart, rangeLength);
  }

  protected RangedUri buildRangedUri(String urlText, long rangeStart, long rangeLength) {
    return new RangedUri(urlText, rangeStart, rangeLength);
  }

  protected ProgramInformation parseProgramInformation(XmlPullParser xpp)
      throws IOException, XmlPullParserException {
    String title = null;
    String source = null;
    String copyright = null;
    String moreInformationURL = parseString(xpp, "moreInformationURL", null);
    String lang = parseString(xpp, "lang", null);
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "Title")) {
        title = xpp.nextText();
      } else if (XmlPullParserUtil.isStartTag(xpp, "Source")) {
        source = xpp.nextText();
      } else if (XmlPullParserUtil.isStartTag(xpp, "Copyright")) {
        copyright = xpp.nextText();
      } else {
        maybeSkipTag(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "ProgramInformation"));
    return new ProgramInformation(title, source, copyright, moreInformationURL, lang);
  }

  /**
   * 解析一个 Label 元素。
   *
   * @param xpp 用于读取的解析器。
   * @throws XmlPullParserException 如果解析元素时发生错误。
   * @throws IOException 如果读取元素时发生错误。
   * @return 解析出的标签。
   */
  protected Label parseLabel(XmlPullParser xpp) throws XmlPullParserException, IOException {
    String lang = xpp.getAttributeValue(null, "lang");
    String value = parseText(xpp, "Label");
    return new Label(lang, value);
  }

  /**
   * 解析一个 BaseURL 元素。
   *
   * @param xpp 用于读取的解析器。
   * @param parentBaseUrls 用于解析已解析 URL 的父级基础 URL。
   * @param dvbProfileDeclared 是否声明了 DVB 配置文件。
   * @throws XmlPullParserException 如果解析元素时发生错误。
   * @throws IOException 如果读取元素时发生错误。
   * @return 解析并解析后的 URL 列表。
   */
  protected List<BaseUrl> parseBaseUrl(
      XmlPullParser xpp, List<BaseUrl> parentBaseUrls, boolean dvbProfileDeclared)
      throws XmlPullParserException, IOException {
    @Nullable String priorityValue = xpp.getAttributeValue(null, "dvb:priority");
    int priority =
        priorityValue != null
            ? Integer.parseInt(priorityValue)
            : (dvbProfileDeclared ? DEFAULT_DVB_PRIORITY : PRIORITY_UNSET);
    @Nullable String weightValue = xpp.getAttributeValue(null, "dvb:weight");
    int weight = weightValue != null ? Integer.parseInt(weightValue) : DEFAULT_WEIGHT;
    @Nullable String serviceLocation = xpp.getAttributeValue(null, "serviceLocation");
    String baseUrl = parseText(xpp, "BaseURL");
    if (UriUtil.isAbsolute(baseUrl)) {
      if (serviceLocation == null) {
        serviceLocation = baseUrl;
      }
      return Lists.newArrayList(new BaseUrl(baseUrl, serviceLocation, priority, weight));
    }

    List<BaseUrl> baseUrls = new ArrayList<>();
    for (int i = 0; i < parentBaseUrls.size(); i++) {
      BaseUrl parentBaseUrl = parentBaseUrls.get(i);
      String resolvedBaseUri = UriUtil.resolve(parentBaseUrl.url, baseUrl);
      String resolvedServiceLocation = serviceLocation == null ? resolvedBaseUri : serviceLocation;
      if (dvbProfileDeclared) {
        // Inherit parent properties only if dvb profile is declared.
        priority = parentBaseUrl.priority;
        weight = parentBaseUrl.weight;
        resolvedServiceLocation = parentBaseUrl.serviceLocation;
      }
      baseUrls.add(new BaseUrl(resolvedBaseUri, resolvedServiceLocation, priority, weight));
    }
    return baseUrls;
  }

  /**
   * 解析 availabilityTimeOffset 值，并返回解析后的值；如果不存在，则返回父级的值。
   *
   * @param xpp 用于读取的解析器。
   * @param parentAvailabilityTimeOffsetUs 父级元素的可用时间偏移量（以微秒为单位）。
   * @return 解析后的 availabilityTimeOffset 值（以微秒为单位）。
   */
  protected long parseAvailabilityTimeOffsetUs(
      XmlPullParser xpp, long parentAvailabilityTimeOffsetUs) {
    String value = xpp.getAttributeValue(/* namespace= */ null, "availabilityTimeOffset");
    if (value == null) {
      return parentAvailabilityTimeOffsetUs;
    }
    if ("INF".equals(value)) {
      return Long.MAX_VALUE;
    }
    return (long) (Float.parseFloat(value) * C.MICROS_PER_SECOND);
  }

  // AudioChannelConfiguration parsing.

  protected int parseAudioChannelConfiguration(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    String schemeIdUri = parseString(xpp, "schemeIdUri", null);
    int audioChannels;
    switch (schemeIdUri) {
      case "urn:mpeg:dash:23003:3:audio_channel_configuration:2011":
        audioChannels = parseInt(xpp, "value", Format.NO_VALUE);
        break;
      case "urn:mpeg:mpegB:cicp:ChannelConfiguration":
        audioChannels = parseMpegChannelConfiguration(xpp);
        break;
      case "tag:dts.com,2014:dash:audio_channel_configuration:2012":
      case "urn:dts:dash:audio_channel_configuration:2012":
        audioChannels = parseDtsChannelConfiguration(xpp);
        break;
      case "tag:dts.com,2018:uhd:audio_channel_configuration":
        audioChannels = parseDtsxChannelConfiguration(xpp);
        break;
      case "tag:dolby.com,2014:dash:audio_channel_configuration:2011":
      case "urn:dolby:dash:audio_channel_configuration:2011":
        audioChannels = parseDolbyChannelConfiguration(xpp);
        break;
      default:
        audioChannels = Format.NO_VALUE;
        break;
    }
    do {
      xpp.next();
    } while (!XmlPullParserUtil.isEndTag(xpp, "AudioChannelConfiguration"));
    return audioChannels;
  }

  // Selection flag parsing.

  protected @C.SelectionFlags int parseSelectionFlagsFromRoleDescriptors(
      List<Descriptor> roleDescriptors) {
    @C.SelectionFlags int result = 0;
    for (int i = 0; i < roleDescriptors.size(); i++) {
      Descriptor descriptor = roleDescriptors.get(i);
      if (Ascii.equalsIgnoreCase("urn:mpeg:dash:role:2011", descriptor.schemeIdUri)) {
        result |= parseSelectionFlagsFromDashRoleScheme(descriptor.value);
      }
    }
    return result;
  }

  protected @C.SelectionFlags int parseSelectionFlagsFromDashRoleScheme(@Nullable String value) {
    if (value == null) {
      return 0;
    }
    switch (value) {
      case "forced_subtitle":
      // Support both hyphen and underscore (https://github.com/google/ExoPlayer/issues/9727).
      case "forced-subtitle":
        return C.SELECTION_FLAG_FORCED;
      default:
        return 0;
    }
  }

  // Role and Accessibility parsing.

  protected @C.RoleFlags int parseRoleFlagsFromRoleDescriptors(List<Descriptor> roleDescriptors) {
    @C.RoleFlags int result = 0;
    for (int i = 0; i < roleDescriptors.size(); i++) {
      Descriptor descriptor = roleDescriptors.get(i);
      if (Ascii.equalsIgnoreCase("urn:mpeg:dash:role:2011", descriptor.schemeIdUri)) {
        result |= parseRoleFlagsFromDashRoleScheme(descriptor.value);
      }
    }
    return result;
  }

  protected @C.RoleFlags int parseRoleFlagsFromAccessibilityDescriptors(
      List<Descriptor> accessibilityDescriptors) {
    @C.RoleFlags int result = 0;
    for (int i = 0; i < accessibilityDescriptors.size(); i++) {
      Descriptor descriptor = accessibilityDescriptors.get(i);
      if (Ascii.equalsIgnoreCase("urn:mpeg:dash:role:2011", descriptor.schemeIdUri)) {
        result |= parseRoleFlagsFromDashRoleScheme(descriptor.value);
      } else if (Ascii.equalsIgnoreCase(
          "urn:tva:metadata:cs:AudioPurposeCS:2007", descriptor.schemeIdUri)) {
        result |= parseTvaAudioPurposeCsValue(descriptor.value);
      }
    }
    return result;
  }

  protected @C.RoleFlags int parseRoleFlagsFromProperties(
      List<Descriptor> accessibilityDescriptors) {
    @C.RoleFlags int result = 0;
    for (int i = 0; i < accessibilityDescriptors.size(); i++) {
      Descriptor descriptor = accessibilityDescriptors.get(i);
      if (Ascii.equalsIgnoreCase(
          "http://dashif.org/guidelines/trickmode", descriptor.schemeIdUri)) {
        result |= C.ROLE_FLAG_TRICK_PLAY;
      }
    }
    return result;
  }

  /**
   * 从 DASH 角色方案中解析角色标志。
   *
   * @param value 角色方案的值，可能为 null。
   * @return 解析后的角色标志，如果无法识别则返回 0。
   */
  protected @C.RoleFlags int parseRoleFlagsFromDashRoleScheme(@Nullable String value) {
    if (value == null) {
      return 0;
    }
    switch (value) {
      case "main":
        return C.ROLE_FLAG_MAIN; // 主内容
      case "alternate":
        return C.ROLE_FLAG_ALTERNATE; // 备用内容
      case "supplementary":
        return C.ROLE_FLAG_SUPPLEMENTARY; // 补充内容
      case "commentary":
        return C.ROLE_FLAG_COMMENTARY; // 评论内容
      case "dub":
        return C.ROLE_FLAG_DUB; // 配音内容
      case "emergency":
        return C.ROLE_FLAG_EMERGENCY; // 紧急内容
      case "caption":
        return C.ROLE_FLAG_CAPTION; // 字幕内容
      case "forced_subtitle":
        // 支持连字符和下划线（https://github.com/google/ExoPlayer/issues/9727）
      case "forced-subtitle":
      case "subtitle":
        return C.ROLE_FLAG_SUBTITLE; // 字幕内容
      case "sign":
        return C.ROLE_FLAG_SIGN; // 手语内容
      case "description":
        return C.ROLE_FLAG_DESCRIBES_VIDEO; // 视频描述内容
      case "enhanced-audio-intelligibility":
        return C.ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY; // 增强对话清晰度的音频内容
      default:
        return 0; // 无法识别的角色方案
    }
  }

  /**
   * 解析 TVA 音频用途分类（CS 值）并返回对应的角色标志。
   *
   * @param value TVA 音频用途分类的值，可能为 null。
   * @return 解析后的角色标志，如果无法识别则返回 0。
   */
  protected @C.RoleFlags int parseTvaAudioPurposeCsValue(@Nullable String value) {
    if (value == null) {
      return 0;
    }
    switch (value) {
      case "1": // 为视障人士提供的音频描述。
        return C.ROLE_FLAG_DESCRIBES_VIDEO;
      case "2": // 为听力障碍人士提供的音频描述。
        return C.ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY;
      case "3": // 补充评论。
        return C.ROLE_FLAG_SUPPLEMENTARY;
      case "4": // 导演评论。
        return C.ROLE_FLAG_COMMENTARY;
      case "6": // 主节目音频。
        return C.ROLE_FLAG_MAIN;
      default:
        return 0; // 无法识别的值
    }
  }

  protected String[] parseProfiles(XmlPullParser xpp, String attributeName, String[] defaultValue) {
    @Nullable String attributeValue = xpp.getAttributeValue(/* namespace= */ null, attributeName);
    if (attributeValue == null) {
      return defaultValue;
    }
    return attributeValue.split(",");
  }

  // Thumbnail tile information parsing

  /**
   * 从给定的描述符中解析缩略图平铺信息。
   *
   * @param essentialProperties 包含缩略图平铺信息的描述符列表。
   * @return 一个包含两个 Integer 值的 Pair，其中第一个值是水平平铺数量，第二个值是垂直平铺数量；
   *     如果未找到缩略图平铺信息，则返回 null。
   */
  @Nullable
  protected Pair<Integer, Integer> parseTileCountFromProperties(
      List<Descriptor> essentialProperties) {
    for (int i = 0; i < essentialProperties.size(); i++) {
      Descriptor descriptor = essentialProperties.get(i);
      if ((Ascii.equalsIgnoreCase("http://dashif.org/thumbnail_tile", descriptor.schemeIdUri)
          || Ascii.equalsIgnoreCase(
          "http://dashif.org/guidelines/thumbnail_tile", descriptor.schemeIdUri))
          && descriptor.value != null) {
        String size = descriptor.value;
        String[] sizeSplit = Util.split(size, "x");
        if (sizeSplit.length != 2) {
          continue;
        }
        try {
          int tileCountHorizontal = Integer.parseInt(sizeSplit[0]);
          int tileCountVertical = Integer.parseInt(sizeSplit[1]);
          return Pair.create(tileCountHorizontal, tileCountVertical);
        } catch (NumberFormatException e) {
          // 如果属性格式错误，则忽略该属性。
        }
      }
    }
    return null;
  }

  // Utility methods.

  /**
   * 如果提供的 {@link XmlPullParser} 当前位于标签的开始位置，则向前跳过该标签的结束位置。
   *
   * @param xpp {@link XmlPullParser} 实例。
   * @throws XmlPullParserException 如果解析流时发生错误。
   * @throws IOException 如果读取流时发生错误。
   */
  public static void maybeSkipTag(XmlPullParser xpp) throws IOException, XmlPullParserException {
    if (!XmlPullParserUtil.isStartTag(xpp)) {
      return; // 如果当前不是开始标签，则直接返回。
    }
    int depth = 1; // 初始化深度为 1，表示当前标签的深度。
    while (depth != 0) {
      xpp.next(); // 移动到下一个事件。
      if (XmlPullParserUtil.isStartTag(xpp)) {
        depth++; // 如果是开始标签，增加深度。
      } else if (XmlPullParserUtil.isEndTag(xpp)) {
        depth--; // 如果是结束标签，减少深度。
      }
    }
  }

  /** 移除不必要的 {@link SchemeData}，即那些 {@link SchemeData#data} 为 null 的实例。 */
  private static void filterRedundantIncompleteSchemeDatas(ArrayList<SchemeData> schemeDatas) {
    for (int i = schemeDatas.size() - 1; i >= 0; i--) {
      SchemeData schemeData = schemeDatas.get(i);
      if (!schemeData.hasData()) {
        for (int j = 0; j < schemeDatas.size(); j++) {
          if (schemeDatas.get(j).canReplace(schemeData)) {
            // 如果 schemeData 不完整，但存在另一个匹配的 SchemeData 并且包含数据，
            // 则移除不完整的 schemeData。
            schemeDatas.remove(i);
            break;
          }
        }
      }
    }
  }

  private static void fillInClearKeyInformation(ArrayList<SchemeData> schemeDatas) {
    // Find and remove ClearKey information.
    @Nullable String clearKeyLicenseServerUrl = null;
    for (int i = 0; i < schemeDatas.size(); i++) {
      SchemeData schemeData = schemeDatas.get(i);
      if (C.CLEARKEY_UUID.equals(schemeData.uuid) && schemeData.licenseServerUrl != null) {
        clearKeyLicenseServerUrl = schemeData.licenseServerUrl;
        schemeDatas.remove(i);
        break;
      }
    }
    if (clearKeyLicenseServerUrl == null) {
      return;
    }
    // Fill in the ClearKey information into the existing PSSH schema data if applicable.
    for (int i = 0; i < schemeDatas.size(); i++) {
      SchemeData schemeData = schemeDatas.get(i);
      if (C.COMMON_PSSH_UUID.equals(schemeData.uuid) && schemeData.licenseServerUrl == null) {
        schemeDatas.set(
            i,
            new SchemeData(
                C.CLEARKEY_UUID, clearKeyLicenseServerUrl, schemeData.mimeType, schemeData.data));
      }
    }
  }

  /**
   * 从容器 MIME 类型和 codecs 属性中推导出样本的 MIME 类型。
   *
   * @param containerMimeType 容器的 MIME 类型。
   * @param codecs codecs 属性。
   * @return 推导出的样本 MIME 类型，如果无法推导则返回 null。
   */
  @Nullable
  private static String getSampleMimeType(
      @Nullable String containerMimeType, @Nullable String codecs) {
    if (MimeTypes.isAudio(containerMimeType)) {
      return MimeTypes.getAudioMediaMimeType(codecs);
    } else if (MimeTypes.isVideo(containerMimeType)) {
      return MimeTypes.getVideoMediaMimeType(codecs);
    } else if (MimeTypes.isText(containerMimeType)) {
      // Text types are raw formats.
      return containerMimeType;
    } else if (MimeTypes.isImage(containerMimeType)) {
      // Image types are raw formats.
      return containerMimeType;
    } else if (MimeTypes.APPLICATION_MP4.equals(containerMimeType)) {
      @Nullable String mimeType = MimeTypes.getMediaMimeType(codecs);
      return MimeTypes.TEXT_VTT.equals(mimeType) ? MimeTypes.APPLICATION_MP4VTT : mimeType;
    }
    return null;
  }

  /**
   * 检查两种语言是否一致，返回一致的语言；如果语言不一致，则抛出 {@link IllegalStateException}。
   *
   * <p>两种语言一致的条件是：它们相等，或者其中一种为 null。
   *
   * @param firstLanguage 第一种语言。
   * @param secondLanguage 第二种语言。
   * @return 一致的语言。
   */
  @Nullable
  private static String checkLanguageConsistency(
      @Nullable String firstLanguage, @Nullable String secondLanguage) {
    if (firstLanguage == null) {
      return secondLanguage;
    } else if (secondLanguage == null) {
      return firstLanguage;
    } else {
      Assertions.checkState(firstLanguage.equals(secondLanguage));
      return firstLanguage;
    }
  }

  /**
   * 检查两个自适应集内容类型是否一致，返回一致的类型；如果类型不一致，则抛出 {@link IllegalStateException}。
   *
   * <p>两种类型一致的条件是：它们相等，或者其中一种为 {@link C#TRACK_TYPE_UNKNOWN}。如果其中一种类型为
   * {@link C#TRACK_TYPE_UNKNOWN}，则返回另一种类型。
   *
   * @param firstType 第一种类型。
   * @param secondType 第二种类型。
   * @return 一致的类型。
   */
  private static int checkContentTypeConsistency(
      @C.TrackType int firstType, @C.TrackType int secondType) {
    if (firstType == C.TRACK_TYPE_UNKNOWN) {
      return secondType;
    } else if (secondType == C.TRACK_TYPE_UNKNOWN) {
      return firstType;
    } else {
      Assertions.checkState(firstType == secondType);
      return firstType;
    }
  }

  /**
   * 从元素中解析一个 {@link Descriptor}。
   *
   * @param xpp 用于读取的解析器。
   * @param tag 正在解析的元素的标签。
   * @throws XmlPullParserException 如果解析元素时发生错误。
   * @throws IOException 如果读取元素时发生错误。
   * @return 解析出的 {@link Descriptor}。
   */
  protected static Descriptor parseDescriptor(XmlPullParser xpp, String tag)
      throws XmlPullParserException, IOException {
    String schemeIdUri = parseString(xpp, "schemeIdUri", "");
    String value = parseString(xpp, "value", null);
    String id = parseString(xpp, "id", null);
    do {
      xpp.next();
    } while (!XmlPullParserUtil.isEndTag(xpp, tag));
    return new Descriptor(schemeIdUri, value, id);
  }

  protected static int parseCea608AccessibilityChannel(List<Descriptor> accessibilityDescriptors) {
    for (int i = 0; i < accessibilityDescriptors.size(); i++) {
      Descriptor descriptor = accessibilityDescriptors.get(i);
      if ("urn:scte:dash:cc:cea-608:2015".equals(descriptor.schemeIdUri)
          && descriptor.value != null) {
        Matcher accessibilityValueMatcher = CEA_608_ACCESSIBILITY_PATTERN.matcher(descriptor.value);
        if (accessibilityValueMatcher.matches()) {
          return Integer.parseInt(accessibilityValueMatcher.group(1));
        } else {
          Log.w(TAG, "Unable to parse CEA-608 channel number from: " + descriptor.value);
        }
      }
    }
    return Format.NO_VALUE;
  }

  protected static int parseCea708AccessibilityChannel(List<Descriptor> accessibilityDescriptors) {
    for (int i = 0; i < accessibilityDescriptors.size(); i++) {
      Descriptor descriptor = accessibilityDescriptors.get(i);
      if ("urn:scte:dash:cc:cea-708:2015".equals(descriptor.schemeIdUri)
          && descriptor.value != null) {
        Matcher accessibilityValueMatcher = CEA_708_ACCESSIBILITY_PATTERN.matcher(descriptor.value);
        if (accessibilityValueMatcher.matches()) {
          return Integer.parseInt(accessibilityValueMatcher.group(1));
        } else {
          Log.w(TAG, "Unable to parse CEA-708 service block number from: " + descriptor.value);
        }
      }
    }
    return Format.NO_VALUE;
  }

  protected static String parseEac3SupplementalProperties(List<Descriptor> supplementalProperties) {
    for (int i = 0; i < supplementalProperties.size(); i++) {
      Descriptor descriptor = supplementalProperties.get(i);
      String schemeIdUri = descriptor.schemeIdUri;
      if (("tag:dolby.com,2018:dash:EC3_ExtensionType:2018".equals(schemeIdUri)
              && "JOC".equals(descriptor.value))
          || ("tag:dolby.com,2014:dash:DolbyDigitalPlusExtensionType:2014".equals(schemeIdUri)
              && "ec+3".equals(descriptor.value))) {
        return MimeTypes.AUDIO_E_AC3_JOC;
      }
    }
    return MimeTypes.AUDIO_E_AC3;
  }

  protected static float parseFrameRate(XmlPullParser xpp, float defaultValue) {
    float frameRate = defaultValue;
    String frameRateAttribute = xpp.getAttributeValue(null, "frameRate");
    if (frameRateAttribute != null) {
      Matcher frameRateMatcher = FRAME_RATE_PATTERN.matcher(frameRateAttribute);
      if (frameRateMatcher.matches()) {
        int numerator = Integer.parseInt(frameRateMatcher.group(1));
        String denominatorString = frameRateMatcher.group(2);
        if (!TextUtils.isEmpty(denominatorString)) {
          frameRate = (float) numerator / Integer.parseInt(denominatorString);
        } else {
          frameRate = numerator;
        }
      }
    }
    return frameRate;
  }

  protected static long parseDuration(XmlPullParser xpp, String name, long defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    if (value == null) {
      return defaultValue;
    } else {
      return Util.parseXsDuration(value);
    }
  }

  protected static long parseDateTime(XmlPullParser xpp, String name, long defaultValue)
      throws ParserException {
    String value = xpp.getAttributeValue(null, name);
    if (value == null) {
      return defaultValue;
    } else {
      return Util.parseXsDateTime(value);
    }
  }

  protected static String parseText(XmlPullParser xpp, String label)
      throws XmlPullParserException, IOException {
    String text = "";
    do {
      xpp.next();
      if (xpp.getEventType() == XmlPullParser.TEXT) {
        text = xpp.getText();
      } else {
        maybeSkipTag(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, label));
    return text;
  }

  protected static int parseInt(XmlPullParser xpp, String name, int defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : Integer.parseInt(value);
  }

  protected static long parseLong(XmlPullParser xpp, String name, long defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : Long.parseLong(value);
  }

  protected static float parseFloat(XmlPullParser xpp, String name, float defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : Float.parseFloat(value);
  }

  protected static String parseString(XmlPullParser xpp, String name, String defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : value;
  }

  /**
   * 从 AudioChannelConfiguration 的 value 属性中解析通道数量，其 schemeIdUri 为 "urn:mpeg:mpegB:cicp:ChannelConfiguration"，
   * 定义见 ISO 23001-8 第 8.1 节。
   *
   * @param xpp 用于读取的解析器。
   * @return 解析出的通道数量，如果无法解析则返回 {@link Format#NO_VALUE}。
   */
  protected static int parseMpegChannelConfiguration(XmlPullParser xpp) {
    int index = parseInt(xpp, "value", C.INDEX_UNSET);
    return 0 <= index && index < MPEG_CHANNEL_CONFIGURATION_MAPPING.length
        ? MPEG_CHANNEL_CONFIGURATION_MAPPING[index]
        : Format.NO_VALUE;
  }

  /**
   * 从 AudioChannelConfiguration 的 value 属性中解析通道数量，其 schemeIdUri 为
   * "tag:dts.com,2014:dash:audio_channel_configuration:2012"（定义见 ETSI TS 102 114 V1.6.1 附录 G (3.2)），
   * 或旧的 schemeIdUri "urn:dts:dash:audio_channel_configuration:2012"。
   *
   * @param xpp 用于读取的解析器。
   * @return 解析出的通道数量，如果无法解析则返回 {@link Format#NO_VALUE}。
   */
  protected static int parseDtsChannelConfiguration(XmlPullParser xpp) {
    int channelCount = parseInt(xpp, "value", Format.NO_VALUE);
    return 0 < channelCount && channelCount < 33 ? channelCount : Format.NO_VALUE;
  }

  /**
   * 从 AudioChannelConfiguration 的 value 属性中解析通道数量，其 schemeIdUri 为
   * "tag:dts.com,2018:uhd:audio_channel_configuration"（定义见 ETSI TS 103 491 v1.2.1 表 B-5）。
   *
   * @param xpp 用于读取的解析器。
   * @return 解析出的通道数量，如果无法解析则返回 {@link Format#NO_VALUE}。
   */
  protected static int parseDtsxChannelConfiguration(XmlPullParser xpp) {
    @Nullable String value = xpp.getAttributeValue(null, "value");
    if (value == null) {
      return Format.NO_VALUE;
    }
    int channelCount = Integer.bitCount(Integer.parseInt(value, /* radix= */ 16));
    return channelCount == 0 ? Format.NO_VALUE : channelCount;
  }

  /**
   * 从 AudioChannelConfiguration 的 value 属性中解析通道数量，其 schemeIdUri 为
   * "tag:dolby.com,2014:dash:audio_channel_configuration:2011"（定义见 ETSI TS 102 366 表 E.5），
   * 或旧的 schemeIdUri "urn:dolby:dash:audio_channel_configuration:2011"。
   *
   * @param xpp 用于读取的解析器。
   * @return 解析出的通道数量，如果无法解析则返回 {@link Format#NO_VALUE}。
   */
  protected static int parseDolbyChannelConfiguration(XmlPullParser xpp) {
    @Nullable String value = xpp.getAttributeValue(null, "value");
    if (value == null) {
      return Format.NO_VALUE;
    }
    switch (Ascii.toLowerCase(value)) {
      case "4000":
        return 1;
      case "a000":
        return 2;
      case "f800":
        return 5;
      case "f801":
        return 6;
      case "fa01":
        return 8;
      default:
        return Format.NO_VALUE;
    }
  }

  protected static long parseLastSegmentNumberSupplementalProperty(
      List<Descriptor> supplementalProperties) {
    for (int i = 0; i < supplementalProperties.size(); i++) {
      Descriptor descriptor = supplementalProperties.get(i);
      if (Ascii.equalsIgnoreCase(
          "http://dashif.org/guidelines/last-segment-number", descriptor.schemeIdUri)) {
        return Long.parseLong(descriptor.value);
      }
    }
    return C.INDEX_UNSET;
  }

  /**
   * 获取最终的可用时间偏移量。
   *
   * @param baseUrlAvailabilityTimeOffsetUs BaseURL 的可用时间偏移量（以微秒为单位）。
   * @param segmentBaseAvailabilityTimeOffsetUs SegmentBase 的可用时间偏移量（以微秒为单位）。
   * @return 最终的可用时间偏移量（以微秒为单位）。
   */
  private static long getFinalAvailabilityTimeOffset(
      long baseUrlAvailabilityTimeOffsetUs, long segmentBaseAvailabilityTimeOffsetUs) {
    long availabilityTimeOffsetUs = segmentBaseAvailabilityTimeOffsetUs;
    if (availabilityTimeOffsetUs == C.TIME_UNSET) {
      // 如果 SegmentBase 未指定偏移量，则回退到 BaseURL 的值。
      availabilityTimeOffsetUs = baseUrlAvailabilityTimeOffsetUs;
    }
    if (availabilityTimeOffsetUs == Long.MAX_VALUE) {
      // 将 INF 值替换为 TIME_UNSET，表示所有片段立即可用。
      availabilityTimeOffsetUs = C.TIME_UNSET;
    }
    return availabilityTimeOffsetUs;
  }

  private boolean isDvbProfileDeclared(String[] profiles) {
    for (String profile : profiles) {
      if (profile.startsWith("urn:dvb:dash:profile:dvb-dash:")) {
        return true;
      }
    }
    return false;
  }

  /** 一个解析后的 Representation 元素。 */
  protected static final class RepresentationInfo {

    public final Format format;
    public final ImmutableList<BaseUrl> baseUrls;
    public final SegmentBase segmentBase;
    @Nullable public final String drmSchemeType;
    public final ArrayList<SchemeData> drmSchemeDatas;
    public final ArrayList<Descriptor> inbandEventStreams;
    public final long revisionId;
    public final List<Descriptor> essentialProperties;
    public final List<Descriptor> supplementalProperties;

    public RepresentationInfo(
        Format format,
        List<BaseUrl> baseUrls,
        SegmentBase segmentBase,
        @Nullable String drmSchemeType,
        ArrayList<SchemeData> drmSchemeDatas,
        ArrayList<Descriptor> inbandEventStreams,
        List<Descriptor> essentialProperties,
        List<Descriptor> supplementalProperties,
        long revisionId) {
      this.format = format;
      this.baseUrls = ImmutableList.copyOf(baseUrls);
      this.segmentBase = segmentBase;
      this.drmSchemeType = drmSchemeType;
      this.drmSchemeDatas = drmSchemeDatas;
      this.inbandEventStreams = inbandEventStreams;
      this.essentialProperties = essentialProperties;
      this.supplementalProperties = supplementalProperties;
      this.revisionId = revisionId;
    }
  }
}
