package androidx.media3.common.text;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.os.Bundle;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/**
 * 表示文本所属说话者的 Span。
 *
 * <p>例如 <a href="https://www.w3.org/TR/webvtt1/#webvtt-cue-voice-span">WebVTT voice span</a>。
 */
@UnstableApi
public final class VoiceSpan {

  /** 说话者名称。 */
  public final String name;

  private static final String FIELD_NAME = Util.intToStringMaxRadix(0);

  public VoiceSpan(String name) {
    this.name = name;
  }

  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putString(FIELD_NAME, name);
    return bundle;
  }

  public static VoiceSpan fromBundle(Bundle bundle) {
    return new VoiceSpan(checkNotNull(bundle.getString(FIELD_NAME)));
  }
}