/*
 * Copyright 2024 The Android Open Source Project
 *
 * 根据 Apache License, Version 2.0（“许可证”）授权；
 * 除非遵守许可证，否则不得使用此文件。
 * 您可以在以下网址获取许可证的副本：
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则按“原样”分发软件，
 * 没有任何明示或暗示的担保或条件。
 * 有关特定语言的管理权限和限制，请参阅许可证。
 */
package androidx.media3.exoplayer;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleStream;
import java.io.IOException;

/** 一个可覆盖的 {@link Renderer} 实现，将所有方法转发给另一个渲染器。 */
@UnstableApi
public class ForwardingRenderer implements Renderer {

  private final Renderer renderer;

  /** 创建一个新实例，将所有操作转发给 {@code renderer}。 */
  public ForwardingRenderer(Renderer renderer) {
    this.renderer = renderer;
  }

  @Override
  public void handleMessage(@MessageType int messageType, @Nullable Object message)
      throws ExoPlaybackException {
    renderer.handleMessage(messageType, message);
  }

  @Override
  public String getName() {
    return renderer.getName();
  }

  @Override
  public @C.TrackType int getTrackType() {
    return renderer.getTrackType();
  }

  @Override
  public RendererCapabilities getCapabilities() {
    return renderer.getCapabilities();
  }

  @Override
  public void init(int index, PlayerId playerId, Clock clock) {
    renderer.init(index, playerId, clock);
  }

  @Nullable
  @Override
  public MediaClock getMediaClock() {
    return renderer.getMediaClock();
  }

  @Override
  public @State int getState() {
    return renderer.getState();
  }

  @Override
  public void enable(
      RendererConfiguration configuration,
      Format[] formats,
      SampleStream stream,
      long positionUs,
      boolean joining,
      boolean mayRenderStartOfStream,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId)
      throws ExoPlaybackException {
    renderer.enable(
        configuration,
        formats,
        stream,
        positionUs,
        joining,
        mayRenderStartOfStream,
        startPositionUs,
        offsetUs,
        mediaPeriodId);
  }

  @Override
  public void start() throws ExoPlaybackException {
    renderer.start();
  }

  @Override
  public void replaceStream(
      Format[] formats,
      SampleStream stream,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId)
      throws ExoPlaybackException {
    renderer.replaceStream(formats, stream, startPositionUs, offsetUs, mediaPeriodId);
  }

  @Nullable
  @Override
  public SampleStream getStream() {
    return renderer.getStream();
  }

  @Override
  public boolean hasReadStreamToEnd() {
    return renderer.hasReadStreamToEnd();
  }

  @Override
  public long getReadingPositionUs() {
    return renderer.getReadingPositionUs();
  }

  @Override
  public long getDurationToProgressUs(long positionUs, long elapsedRealtimeUs) {
    return renderer.getDurationToProgressUs(positionUs, elapsedRealtimeUs);
  }

  @Override
  public void setCurrentStreamFinal() {
    renderer.setCurrentStreamFinal();
  }

  @Override
  public boolean isCurrentStreamFinal() {
    return renderer.isCurrentStreamFinal();
  }

  @Override
  public void maybeThrowStreamError() throws IOException {
    renderer.maybeThrowStreamError();
  }

  @Override
  public void resetPosition(long positionUs) throws ExoPlaybackException {
    renderer.resetPosition(positionUs);
  }

  @Override
  public void setPlaybackSpeed(float currentPlaybackSpeed, float targetPlaybackSpeed)
      throws ExoPlaybackException {
    renderer.setPlaybackSpeed(currentPlaybackSpeed, targetPlaybackSpeed);
  }

  @Override
  public void enableMayRenderStartOfStream() {
    renderer.enableMayRenderStartOfStream();
  }

  @Override
  public void setTimeline(Timeline timeline) {
    renderer.setTimeline(timeline);
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    renderer.render(positionUs, elapsedRealtimeUs);
  }

  @Override
  public boolean isReady() {
    return renderer.isReady();
  }

  @Override
  public boolean isEnded() {
    return renderer.isEnded();
  }

  @Override
  public void stop() {
    renderer.stop();
  }

  @Override
  public void disable() {
    renderer.disable();
  }

  @Override
  public void reset() {
    renderer.reset();
  }

  @Override
  public void release() {
    renderer.release();
  }
}