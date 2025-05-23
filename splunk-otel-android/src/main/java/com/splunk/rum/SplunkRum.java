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

package com.splunk.rum;

import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.app.Application;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;
import androidx.annotation.Nullable;
import com.splunk.rum.internal.GlobalAttributesSupplier;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import okhttp3.Call;
import okhttp3.OkHttpClient;

/** Entrypoint for the Splunk OpenTelemetry Instrumentation for Android. */
public class SplunkRum {
    // initialize this here, statically, to make sure we capture the earliest possible timestamp for
    // startup.
    private static final AppStartupTimer startupTimer = new AppStartupTimer();

    static final AttributeKey<String> COMPONENT_KEY = AttributeKey.stringKey("component");
    static final AttributeKey<String> ERROR_TYPE_KEY = stringKey("error.type");
    static final AttributeKey<String> ERROR_MESSAGE_KEY = stringKey("error.message");
    static final AttributeKey<String> WORKFLOW_NAME_KEY = stringKey("workflow.name");
    static final AttributeKey<Double> LOCATION_LATITUDE_KEY = doubleKey("location.lat");
    static final AttributeKey<Double> LOCATION_LONGITUDE_KEY = doubleKey("location.long");

    static final String COMPONENT_APPSTART = "appstart";
    static final String COMPONENT_UI = "ui";
    static final String COMPONENT_CRASH = "crash";
    static final String COMPONENT_ERROR = "error";
    static final String LOG_TAG = "SplunkRum";
    static final String RUM_TRACER_NAME = "SplunkRum";

    static final AttributeKey<String> LINK_TRACE_ID_KEY = stringKey("link.traceId");
    static final AttributeKey<String> LINK_SPAN_ID_KEY = stringKey("link.spanId");

    static final AttributeKey<String> APP_NAME_KEY = stringKey("app");
    static final AttributeKey<String> RUM_VERSION_KEY = stringKey("splunk.rum.version");
    static final AttributeKey<String> APPLICATION_ID_KEY = stringKey("service.application_id");
    static final AttributeKey<String> APP_VERSION_CODE_KEY = stringKey("service.version_code");
    static final AttributeKey<String> SPLUNK_BUILD_ID = stringKey("splunk.build_id");

    @Nullable private static SplunkRum INSTANCE;

    private final OpenTelemetryRum openTelemetryRum;
    private final GlobalAttributesSupplier globalAttributes;
    private final SettableScreenAttributesAppender screenAttributesAppender;

    static {
        Handler handler = new Handler(Looper.getMainLooper());
        startupTimer.detectBackgroundStart(handler);
    }

    SplunkRum(
            OpenTelemetryRum openTelemetryRum,
            GlobalAttributesSupplier globalAttributes,
            SettableScreenAttributesAppender screenAttributesAppender) {
        this.openTelemetryRum = openTelemetryRum;
        this.globalAttributes = globalAttributes;
        this.screenAttributesAppender = screenAttributesAppender;
    }

    /** Creates a new {@link SplunkRumBuilder}, used to set up a {@link SplunkRum} instance. */
    public static SplunkRumBuilder builder() {
        return new SplunkRumBuilder();
    }

    // for testing purposes
    static SplunkRum initialize(SplunkRumBuilder builder, Application application) {
        if (INSTANCE != null) {
            Log.w(LOG_TAG, "Singleton SplunkRum instance has already been initialized.");
            return INSTANCE;
        }

        if (builder.isSubprocessInstrumentationDisabled() && builder.isSubprocess) {
            INSTANCE = SplunkRum.noop();
        } else {
            INSTANCE =
                    new RumInitializer(builder, application, startupTimer)
                            .initialize(Looper.getMainLooper());
        }

        if (builder.isDebugEnabled()) {
            Log.i(
                    LOG_TAG,
                    "Splunk RUM monitoring initialized with session ID: "
                            + INSTANCE.getRumSessionId());
        }

        return INSTANCE;
    }

    /**
     * Starts a UI navigation span and remembers the last screen name.
     *
     * @param screenName Name of the new screen or null when exiting explicit UI navigation mode.
     * @param spanType "Created", "Restarted", or "Resumed"
     * @deprecated This method is deprecated and will be removed in a future release
     */
    @Deprecated
    public void experimentalSetScreenName(String screenName, String spanType) {
        screenAttributesAppender.setScreenName(screenName);

        if (screenName != null) {
            // no need to set the screen name attributes, span processor will do it
            getTracer().spanBuilder(spanType).setAttribute(COMPONENT_KEY, "ui").startSpan().end();
        }
    }

    /**
     * Starts a "Created" UI navigation span and remembers the last screen name.
     *
     * @param screenName Name of the new screen or null when exiting explicit UI navigation mode.
     * @deprecated This method is deprecated and will be removed in a future release
     */
    @Deprecated
    public void experimentalSetScreenName(String screenName) {
        experimentalSetScreenName(screenName, "Created");
    }

    /** Returns {@code true} if the Splunk RUM library has been successfully initialized. */
    public static boolean isInitialized() {
        return INSTANCE != null;
    }

    /** Get the singleton instance of this class. */
    public static SplunkRum getInstance() {
        if (INSTANCE == null) {
            Log.d(LOG_TAG, "SplunkRum not initialized. Returning no-op implementation");
            return NoOpSplunkRum.INSTANCE;
        }
        return INSTANCE;
    }

    /**
     * Initialize a no-op version of the SplunkRum API, including the instance of OpenTelemetry that
     * is available. This can be useful for testing, or configuring your app without RUM enabled,
     * but still using the APIs.
     *
     * @return A no-op instance of {@link SplunkRum}
     */
    public static SplunkRum noop() {
        return NoOpSplunkRum.INSTANCE;
    }

    /**
     * Wrap the provided {@link OkHttpClient} with OpenTelemetry and RUM instrumentation. Since
     * {@link Call.Factory} is the primary useful interface implemented by the OkHttpClient, this
     * should be a drop-in replacement for any usages of OkHttpClient.
     *
     * @param client The {@link OkHttpClient} to wrap with OpenTelemetry and RUM instrumentation.
     * @return A {@link okhttp3.Call.Factory} implementation.
     * @deprecated This method is deprecated and will be removed in a future release
     */
    @Deprecated
    public Call.Factory createRumOkHttpCallFactory(OkHttpClient client) {
        return createOkHttpTracing().newCallFactory(client);
    }

    private OkHttpTelemetry createOkHttpTracing() {
        return OkHttpTelemetry.builder(getOpenTelemetry())
                .addAttributesExtractor(
                        new RumResponseAttributesExtractor(new ServerTimingHeaderParser()))
                .build();
    }

    /**
     * Get a handle to the instance of the OpenTelemetry API that this instance is using for
     * instrumentation.
     */
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetryRum.getOpenTelemetry();
    }

    /**
     * Get the Splunk Session ID associated with this instance of the RUM instrumentation library.
     * Note: this value can change throughout the lifetime of an application instance, so it is
     * recommended that you do not cache this value, but always retrieve it from here when needed.
     */
    public String getRumSessionId() {
        return openTelemetryRum.getRumSessionId();
    }

    /**
     * Add a custom event to RUM monitoring. This can be useful to capture business events, or
     * simply add instrumentation to your application.
     *
     * <p>This event will be turned into a Span and sent to the RUM ingest along with other,
     * auto-generated spans.
     *
     * @param name The name of the event.
     * @param attributes Any {@link Attributes} to associate with the event.
     */
    public void addRumEvent(String name, Attributes attributes) {
        getTracer().spanBuilder(name).setAllAttributes(attributes).startSpan().end();
    }

    /**
     * Start a Span to time a named workflow.
     *
     * @param workflowName The name of the workflow to start.
     * @return A {@link Span} that has been started.
     */
    public Span startWorkflow(String workflowName) {
        return getTracer()
                .spanBuilder(workflowName)
                .setAttribute(WORKFLOW_NAME_KEY, workflowName)
                .startSpan();
    }

    /**
     * Add a custom exception to RUM monitoring. This can be useful for tracking custom error
     * handling in your application.
     *
     * <p>This event will be turned into a Span and sent to the RUM ingest along with other,
     * auto-generated spans.
     *
     * @param throwable A {@link Throwable} associated with this event.
     */
    public void addRumException(Throwable throwable) {
        addRumException(throwable, Attributes.empty());
    }

    /**
     * Add a custom exception to RUM monitoring. This can be useful for tracking custom error
     * handling in your application.
     *
     * <p>This event will be turned into a Span and sent to the RUM ingest along with other,
     * auto-generated spans.
     *
     * @param throwable A {@link Throwable} associated with this event.
     * @param attributes Any {@link Attributes} to associate with the event.
     */
    public void addRumException(Throwable throwable, Attributes attributes) {
        getTracer()
                .spanBuilder(throwable.getClass().getSimpleName())
                .setAllAttributes(attributes)
                .setAttribute(COMPONENT_KEY, COMPONENT_ERROR)
                .startSpan()
                .recordException(throwable)
                .end();
    }

    Tracer getTracer() {
        return getOpenTelemetry().getTracer(RUM_TRACER_NAME);
    }

    /**
     * Set an attribute in the global attributes that will be appended to every span and event.
     *
     * <p>Note: If this key is the same as an existing key in the global attributes, it will replace
     * the existing value.
     *
     * <p>If you attempt to set a value to null or use a null key, this call will be ignored.
     *
     * <p>Note: this operation performs an atomic update. The passed function should be free from
     * side effects, since it may be called multiple times in case of thread contention.
     *
     * @param key The {@link AttributeKey} for the attribute.
     * @param value The value of the attribute, which must match the generic type of the key.
     * @param <T> The generic type of the value.
     */
    public <T> void setGlobalAttribute(AttributeKey<T> key, T value) {
        updateGlobalAttributes(attributesBuilder -> attributesBuilder.put(key, value));
    }

    /**
     * Update the global set of attributes that will be appended to every span and event.
     *
     * <p>Note: this operation performs an atomic update. The passed function should be free from
     * side effects, since it may be called multiple times in case of thread contention.
     *
     * @param attributesUpdater A function which will update the current set of attributes, by
     *     operating on a {@link AttributesBuilder} from the current set.
     */
    public void updateGlobalAttributes(Consumer<AttributesBuilder> attributesUpdater) {
        globalAttributes.update(attributesUpdater);
    }

    // for testing only
    static void resetSingletonForTest() {
        INSTANCE = null;
    }

    // (currently) for testing only
    void flushSpans() {
        OpenTelemetry openTelemetry = getOpenTelemetry();
        if (openTelemetry instanceof OpenTelemetrySdk) {
            ((OpenTelemetrySdk) openTelemetry)
                    .getSdkTracerProvider()
                    .forceFlush()
                    .join(1, TimeUnit.SECONDS);
        }
    }

    /**
     * This method will enable Splunk Browser-based RUM to integrate with the current Android RUM
     * Session. It injects a javascript object named "SplunkRumNative" into your WebView which
     * exposes the Android Session ID to the browser-based RUM javascript implementation.
     *
     * <p>Please note: This API is not stable and may change in future releases.
     *
     * @param webView The WebView to inject the javascript object into.
     */
    public void integrateWithBrowserRum(WebView webView) {
        webView.addJavascriptInterface(new NativeRumSessionId(this), "SplunkRumNative");
    }

    /**
     * Updates the current location. The latitude and longitude will be appended to every span and
     * event.
     *
     * <p>Note: this operation performs an atomic update. You can safely call it from your {@code
     * LocationListener} or {@code LocationCallback}.
     *
     * @param location the current location. Passing {@code null} removes the location data.
     * @deprecated This method is deprecated and will be removed in a future release
     */
    @Deprecated
    public void updateLocation(@Nullable Location location) {
        if (location == null) {
            updateGlobalAttributes(
                    attributes ->
                            attributes
                                    .remove(LOCATION_LATITUDE_KEY)
                                    .remove(LOCATION_LONGITUDE_KEY));
        } else {
            updateGlobalAttributes(
                    attributes ->
                            attributes
                                    .put(LOCATION_LATITUDE_KEY, location.getLatitude())
                                    .put(LOCATION_LONGITUDE_KEY, location.getLongitude()));
        }
    }
}
