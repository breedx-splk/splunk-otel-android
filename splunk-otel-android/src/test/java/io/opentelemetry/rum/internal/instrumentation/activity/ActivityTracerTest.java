/*
 * Copyright Splunk Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.rum.internal.instrumentation.activity;

import static io.opentelemetry.rum.internal.RumConstants.LAST_SCREEN_NAME_KEY;
import static io.opentelemetry.rum.internal.RumConstants.SCREEN_NAME_KEY;
import static io.opentelemetry.rum.internal.RumConstants.START_TYPE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;
import com.splunk.rum.RumScreenName;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.rum.internal.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.rum.internal.util.ActiveSpan;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ActivityTracerTest {
    @RegisterExtension final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private Tracer tracer;
    private final VisibleScreenTracker visibleScreenTracker = mock(VisibleScreenTracker.class);
    private final AppStartupTimer appStartupTimer = new AppStartupTimer();
    private ActiveSpan activeSpan;

    @BeforeEach
    public void setup() {
        tracer = otelTesting.getOpenTelemetry().getTracer("testTracer");
        activeSpan = new ActiveSpan(visibleScreenTracker::getPreviouslyVisibleScreen);
    }

    @Test
    void restart_nonInitialActivity() {
        ActivityTracer trackableTracer =
                ActivityTracer.builder(mock(Activity.class))
                        .setInitialAppActivity("FirstActivity")
                        .setTracer(tracer)
                        .setAppStartupTimer(appStartupTimer)
                        .setActiveSpan(activeSpan)
                        .build();
        trackableTracer.initiateRestartSpanIfNecessary(false);
        trackableTracer.endActiveSpan();
        SpanData span = getSingleSpan();
        assertEquals("Restarted", span.getName());
        assertNull(span.getAttributes().get(START_TYPE_KEY));
    }

    @Test
    public void restart_initialActivity() {
        ActivityTracer trackableTracer =
                ActivityTracer.builder(mock(Activity.class))
                        .setInitialAppActivity("Activity")
                        .setTracer(tracer)
                        .setAppStartupTimer(appStartupTimer)
                        .setActiveSpan(activeSpan)
                        .build();
        trackableTracer.initiateRestartSpanIfNecessary(false);
        trackableTracer.endActiveSpan();
        SpanData span = getSingleSpan();
        assertEquals("AppStart", span.getName());
        assertEquals("hot", span.getAttributes().get(START_TYPE_KEY));
    }

    @Test
    public void restart_initialActivity_multiActivityApp() {
        ActivityTracer trackableTracer =
                ActivityTracer.builder(mock(Activity.class))
                        .setInitialAppActivity("Activity")
                        .setTracer(tracer)
                        .setAppStartupTimer(appStartupTimer)
                        .setActiveSpan(activeSpan)
                        .build();
        trackableTracer.initiateRestartSpanIfNecessary(true);
        trackableTracer.endActiveSpan();
        SpanData span = getSingleSpan();
        assertEquals("Restarted", span.getName());
        assertNull(span.getAttributes().get(START_TYPE_KEY));
    }

    @Test
    public void create_nonInitialActivity() {
        ActivityTracer trackableTracer =
                ActivityTracer.builder(mock(Activity.class))
                        .setInitialAppActivity("FirstActivity")
                        .setTracer(tracer)
                        .setAppStartupTimer(appStartupTimer)
                        .setActiveSpan(activeSpan)
                        .build();

        trackableTracer.startActivityCreation();
        trackableTracer.endActiveSpan();
        SpanData span = getSingleSpan();
        assertEquals("Created", span.getName());
        assertNull(span.getAttributes().get(START_TYPE_KEY));
    }

    @Test
    public void create_initialActivity() {
        ActivityTracer trackableTracer =
                ActivityTracer.builder(mock(Activity.class))
                        .setInitialAppActivity("Activity")
                        .setTracer(tracer)
                        .setAppStartupTimer(appStartupTimer)
                        .setActiveSpan(activeSpan)
                        .build();
        trackableTracer.startActivityCreation();
        trackableTracer.endActiveSpan();
        SpanData span = getSingleSpan();
        assertEquals("AppStart", span.getName());
        assertEquals("warm", span.getAttributes().get(START_TYPE_KEY));
    }

    @Test
    public void create_initialActivity_firstTime() {
        appStartupTimer.start(tracer);
        ActivityTracer trackableTracer =
                ActivityTracer.builder(mock(Activity.class))
                        .setTracer(tracer)
                        .setAppStartupTimer(appStartupTimer)
                        .setActiveSpan(activeSpan)
                        .build();
        trackableTracer.startActivityCreation();
        trackableTracer.endActiveSpan();
        appStartupTimer.end();

        List<SpanData> spans = otelTesting.getSpans();
        assertEquals(2, spans.size());

        SpanData appStartSpan = spans.get(0);
        assertEquals("AppStart", appStartSpan.getName());
        assertEquals("cold", appStartSpan.getAttributes().get(START_TYPE_KEY));

        SpanData innerSpan = spans.get(1);
        assertEquals("Created", innerSpan.getName());
    }

    @Test
    public void addPreviousScreen_noPrevious() {
        ActivityTracer trackableTracer =
                ActivityTracer.builder(mock(Activity.class))
                        .setTracer(tracer)
                        .setAppStartupTimer(appStartupTimer)
                        .setActiveSpan(activeSpan)
                        .build();

        trackableTracer.startSpanIfNoneInProgress("starting");
        trackableTracer.addPreviousScreenAttribute();
        trackableTracer.endActiveSpan();

        SpanData span = getSingleSpan();
        assertNull(span.getAttributes().get(LAST_SCREEN_NAME_KEY));
    }

    @Test
    public void addPreviousScreen_currentSameAsPrevious() {
        VisibleScreenTracker visibleScreenTracker = mock(VisibleScreenTracker.class);
        when(visibleScreenTracker.getPreviouslyVisibleScreen()).thenReturn("Activity");

        ActivityTracer trackableTracer =
                ActivityTracer.builder(mock(Activity.class))
                        .setTracer(tracer)
                        .setAppStartupTimer(appStartupTimer)
                        .setActiveSpan(activeSpan)
                        .build();

        trackableTracer.startSpanIfNoneInProgress("starting");
        trackableTracer.addPreviousScreenAttribute();
        trackableTracer.endActiveSpan();

        SpanData span = getSingleSpan();
        assertNull(span.getAttributes().get(LAST_SCREEN_NAME_KEY));
    }

    @Test
    public void addPreviousScreen() {
        when(visibleScreenTracker.getPreviouslyVisibleScreen()).thenReturn("previousScreen");

        ActivityTracer trackableTracer =
                ActivityTracer.builder(mock(Activity.class))
                        .setTracer(tracer)
                        .setAppStartupTimer(appStartupTimer)
                        .setActiveSpan(activeSpan)
                        .build();

        trackableTracer.startSpanIfNoneInProgress("starting");
        trackableTracer.addPreviousScreenAttribute();
        trackableTracer.endActiveSpan();

        SpanData span = getSingleSpan();
        assertEquals("previousScreen", span.getAttributes().get(LAST_SCREEN_NAME_KEY));
    }

    @Test
    public void testAnnotatedActivity() {
        Activity annotatedActivity = new AnnotatedActivity();
        ActivityTracer activityTracer =
                ActivityTracer.builder(annotatedActivity)
                        .setTracer(tracer)
                        .setAppStartupTimer(appStartupTimer)
                        .setActiveSpan(activeSpan)
                        .build();
        activityTracer.startActivityCreation();
        activityTracer.endActiveSpan();
        SpanData span = getSingleSpan();
        assertEquals("squarely", span.getAttributes().get(SCREEN_NAME_KEY));
    }

    @RumScreenName("squarely")
    static class AnnotatedActivity extends Activity {}

    private SpanData getSingleSpan() {
        List<SpanData> generatedSpans = otelTesting.getSpans();
        assertEquals(1, generatedSpans.size());
        return generatedSpans.get(0);
    }
}
