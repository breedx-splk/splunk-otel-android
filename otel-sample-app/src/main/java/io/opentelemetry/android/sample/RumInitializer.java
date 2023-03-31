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

package io.opentelemetry.android.sample;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.DEPLOYMENT_ENVIRONMENT;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.DEVICE_MODEL_IDENTIFIER;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.DEVICE_MODEL_NAME;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.OS_NAME;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.OS_TYPE;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.OS_VERSION;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_VERSION;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.TELEMETRY_SDK_VERSION;

import android.app.Application;
import android.os.Build;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.rum.internal.GlobalAttributesSpanAppender;
import io.opentelemetry.rum.internal.OpenTelemetryRum;
import io.opentelemetry.rum.internal.OpenTelemetryRumBuilder;
import io.opentelemetry.rum.internal.SessionIdRatioBasedSampler;
import io.opentelemetry.rum.internal.instrumentation.ScreenNameExtractor;
import io.opentelemetry.rum.internal.instrumentation.activity.VisibleScreenTracker;
import io.opentelemetry.rum.internal.instrumentation.anr.AnrDetector;
import io.opentelemetry.rum.internal.instrumentation.crash.CrashDetails;
import io.opentelemetry.rum.internal.instrumentation.crash.CrashReporter;
import io.opentelemetry.rum.internal.instrumentation.lifecycle.AndroidLifecycleInstrumentation;
import io.opentelemetry.rum.internal.instrumentation.network.CurrentNetworkProvider;
import io.opentelemetry.rum.internal.instrumentation.network.NetworkAttributesSpanAppender;
import io.opentelemetry.rum.internal.instrumentation.network.NetworkChangeMonitor;
import io.opentelemetry.rum.internal.instrumentation.slowrendering.SlowRenderingDetector;
import io.opentelemetry.rum.internal.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

class RumInitializer {

    static final int MAX_ATTRIBUTE_LENGTH = 256 * 128;

    private final Application application;
    private final AppStartupTimer startupTimer;

    RumInitializer(Application application, AppStartupTimer startupTimer) {
        this.application = application;
        this.startupTimer = startupTimer;
    }

    OpenTelemetryRum initialize(
            Function<Application, CurrentNetworkProvider> currentNetworkProviderFactory,
            Looper mainLooper) {
        VisibleScreenTracker visibleScreenTracker = new VisibleScreenTracker();
        OpenTelemetryRumBuilder otelRumBuilder = OpenTelemetryRum.builder();

        otelRumBuilder.setResource(createResource());

        CurrentNetworkProvider currentNetworkProvider =
                currentNetworkProviderFactory.apply(application);

        Attributes globalAttributes = Attributes.builder()
                .put("vendor", "hopefully-upstream")
                .put(SERVICE_VERSION, BuildConfig.VERSION_NAME)
                .build();
        GlobalAttributesSpanAppender globalAttributesSpanAppender =
                GlobalAttributesSpanAppender.create(globalAttributes);

        // Add span processor that appends global attributes.
        otelRumBuilder.addTracerProviderCustomizer(
                (tracerProviderBuilder, app) ->
                        tracerProviderBuilder.addSpanProcessor(globalAttributesSpanAppender));

        // Add span processor that appends network attributes.
        otelRumBuilder.addTracerProviderCustomizer(
                (tracerProviderBuilder, app) -> {
                    SpanProcessor networkAttributesSpanAppender =
                            NetworkAttributesSpanAppender.create(currentNetworkProvider);
                    return tracerProviderBuilder.addSpanProcessor(networkAttributesSpanAppender);
                });

        // Add span processor that appends screen attributes and generate init event.
        // XXX Splunk-specific ScreenAttributesAppender -- cannot use yet in vanilla
//        otelRumBuilder.addTracerProviderCustomizer(
//                (tracerProviderBuilder, app) -> {
//                    ScreenAttributesAppender screenAttributesAppender =
//                            new ScreenAttributesAppender(visibleScreenTracker);
//                    return tracerProviderBuilder.addSpanProcessor(screenAttributesAppender);
//                });

        // Add batch span processor
        otelRumBuilder.addTracerProviderCustomizer(
                (tracerProviderBuilder, app) -> {
                    SpanExporter zipkinExporter = buildExporter(currentNetworkProvider);

                    BatchSpanProcessor batchSpanProcessor =
                            BatchSpanProcessor.builder(zipkinExporter).build();
                    return tracerProviderBuilder.addSpanProcessor(batchSpanProcessor);
                });

        // Set span limits
        otelRumBuilder.addTracerProviderCustomizer(
                (tracerProviderBuilder, app) ->
                        tracerProviderBuilder.setSpanLimits(
                                SpanLimits.builder()
                                        .setMaxAttributeValueLength(MAX_ATTRIBUTE_LENGTH)
                                        .build()));

        // Set up the sampler, if enabled
        if (false) {
            otelRumBuilder.addTracerProviderCustomizer(
                    (tracerProviderBuilder, app) -> {
                        SessionIdRatioBasedSampler sampler =
                                new SessionIdRatioBasedSampler(
                                        0.99,
                                        otelRumBuilder.getSessionId());
                        return tracerProviderBuilder.setSampler(sampler);
                    });
        }

        // Wire up the logging exporter
        otelRumBuilder.addTracerProviderCustomizer(
                (tracerProviderBuilder, app) -> {
                    tracerProviderBuilder.addSpanProcessor(
                            SimpleSpanProcessor.create(LoggingSpanExporter.create()));
                    return tracerProviderBuilder;
                });

        // Add final event showing tracer provider init finished
        installAnrDetector(otelRumBuilder, mainLooper);
        installNetworkMonitor(otelRumBuilder, currentNetworkProvider);
        installSlowRenderingDetector(otelRumBuilder);
        installCrashReporter(otelRumBuilder);

        // Lifecycle events instrumentation are always installed.
        installLifecycleInstrumentations(otelRumBuilder, visibleScreenTracker);

        return otelRumBuilder.build(application);
    }

    private void installLifecycleInstrumentations(
            OpenTelemetryRumBuilder otelRumBuilder, VisibleScreenTracker visibleScreenTracker) {

        // XXX Don't set the "component" attribute until in spec, then create better API
//        Function<Tracer, Tracer> tracerCustomizer =
//                tracer ->
//                        (Tracer)
//                                spanName -> {
//                                    String component =
//                                            spanName.equals(APP_START_SPAN_NAME)
//                                                    ? COMPONENT_APPSTART
//                                                    : COMPONENT_UI;
//                                    return tracer.spanBuilder(spanName)
//                                            .setAttribute(COMPONENT_KEY, component);
//                                };
        AndroidLifecycleInstrumentation lifecycleInst =
                AndroidLifecycleInstrumentation.builder()
                        .setVisibleScreenTracker(visibleScreenTracker)
                        .setStartupTimer(startupTimer)
//                        .setTracerCustomizer(tracerCustomizer)
                        .setScreenNameExtractor(ScreenNameExtractor.DEFAULT)
                        .build();
        otelRumBuilder.addInstrumentation(lifecycleInst::installOn);
    }

    private Resource createResource() {
        int stringId = application.getApplicationContext().getApplicationInfo().labelRes;
        String appName = application.getApplicationContext().getString(stringId);
        ResourceBuilder resourceBuilder =
                Resource.getDefault().toBuilder()
                        .put(SERVICE_NAME, appName)
                        .put(DEPLOYMENT_ENVIRONMENT, "demo"); // This would probably need to be dynamic. What even is deployment env in mobile?
        return resourceBuilder
                .put(TELEMETRY_SDK_VERSION, detectRumVersion())
                .put(DEVICE_MODEL_NAME, Build.MODEL)
                .put(DEVICE_MODEL_IDENTIFIER, Build.MODEL)
                .put(OS_NAME, "Android")
                .put(OS_TYPE, "linux")
                .put(OS_VERSION, Build.VERSION.RELEASE)
                .build();
    }

    private String detectRumVersion() {
        try {
            // todo: figure out if there's a way to get access to resources from pure non-UI library
            // code.
            return application
                    .getApplicationContext()
                    .getResources()
                    .getString(R.string.rum_version);
        } catch (Exception e) {
            // ignore for now
        }
        return "unknown";
    }

    private void installAnrDetector(OpenTelemetryRumBuilder otelRumBuilder, Looper mainLooper) {
        otelRumBuilder.addInstrumentation(
                instrumentedApplication -> {
                    AnrDetector.builder()
                            // XXX component not available until spec'd
//                            .addAttributesExtractor(constant(COMPONENT_KEY, COMPONENT_ERROR))
                            .setMainLooper(mainLooper)
                            .build()
                            .installOn(instrumentedApplication);
                });
    }

    private void installNetworkMonitor(
            OpenTelemetryRumBuilder otelRumBuilder, CurrentNetworkProvider currentNetworkProvider) {
        otelRumBuilder.addInstrumentation(
                instrumentedApplication -> {
                    NetworkChangeMonitor.create(currentNetworkProvider)
                            .installOn(instrumentedApplication);
                });
    }

    private void installSlowRenderingDetector(OpenTelemetryRumBuilder otelRumBuilder) {
        otelRumBuilder.addInstrumentation(
                instrumentedApplication -> {
                    SlowRenderingDetector.builder()
                            .setSlowRenderingDetectionPollInterval(Duration.ofMillis(1000))
                            .build()
                            .installOn(instrumentedApplication);
                });
    }

    private void installCrashReporter(OpenTelemetryRumBuilder otelRumBuilder) {
        otelRumBuilder.addInstrumentation(
                instrumentedApplication -> {
                    CrashReporter.builder()
                            .addAttributesExtractor(new AttributesExtractor<CrashDetails, Void>() {
                                @Override
                                public void onStart(AttributesBuilder attributes, Context parentContext, CrashDetails crashDetails) {
                                    //nop -- ideally extract runtime details (heap, storage, battery, etc)
                                }

                                @Override
                                public void onEnd(AttributesBuilder attributes, Context context, CrashDetails crashDetails, Void unused, Throwable error) {

                                }
                            })
                            //XXX runtime details not available until this extractor is migrated
//                                    RuntimeDetailsExtractor.create(
//                                            instrumentedApplication
//                                                    .getApplication()
//                                                    .getApplicationContext()))
                            // XXX Component not available until spec'd
//                            .addAttributesExtractor(new CrashComponentExtractor())
                            .build()
                            .installOn(instrumentedApplication);

                });
    }

    // visible for testing
    SpanExporter buildExporter(CurrentNetworkProvider currentNetworkProvider) {
        // XXX Can't yet enable disk buffering until migrated
//        SpanExporter exporter = buildMemoryBufferingThrottledExporter(currentNetworkProvider);
//        SpanExporter splunkTranslatedExporter =
//                new SplunkSpanDataModifier(exporter, builder.isReactNativeSupportEnabled());
//        SpanExporter filteredExporter = builder.decorateWithSpanFilter(splunkTranslatedExporter);
//        return filteredExporter;

        // return a lazy init exporter so the main thread doesn't block on the setup.
//        String endpoint = "http://localhost:4317"; // necessary if default?
        String endpoint = "http://192.168.66.175:4317"; // XXX hard coded! As a def this stinks.
        SpanExporter coreExporter = new LazyInitSpanExporter(
                () -> OtlpGrpcSpanExporter.builder()
                        .setEndpoint(endpoint)
                        .build());
        return coreExporter;
        // XXX Guessing memory buffering is already built into otlp exporter, not needed
//        new MemoryBufferingExporter(currentNetworkProvider, coreExporter));
    }

    // XXX Can't throttle until ThrottlingExporter migrated
//    private SpanExporter buildMemoryBufferingThrottledExporter(
//            CurrentNetworkProvider currentNetworkProvider) {
//        String endpoint = getEndpoint();
//        SpanExporter zipkinSpanExporter = getCoreSpanExporter(endpoint);
//        return ThrottlingExporter.newBuilder(
//                        new MemoryBufferingExporter(currentNetworkProvider, zipkinSpanExporter))
//                .categorizeByAttribute(COMPONENT_KEY)
//                .maxSpansInWindow(100)
//                .windowSize(Duration.ofSeconds(30))
//                .build();
//    }

    // XXX Can't buffer to disk until migrated
//    SpanExporter getToDiskExporter() {
//        return new LazyInitSpanExporter(
//                () ->
//                        ZipkinWriteToDiskExporterFactory.create(
//                                application, builder.maxUsageMegabytes));
//    }

    // XXX Is this useful enough to migrate?
    private static class LazyInitSpanExporter implements SpanExporter {
        @Nullable private volatile SpanExporter delegate;
        private final Supplier<SpanExporter> s;

        public LazyInitSpanExporter(Supplier<SpanExporter> s) {
            this.s = s;
        }

        private SpanExporter getDelegate() {
            SpanExporter d = delegate;
            if (d == null) {
                synchronized (this) {
                    d = delegate;
                    if (d == null) {
                        delegate = d = s.get();
                    }
                }
            }
            return d;
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            return getDelegate().export(spans);
        }

        @Override
        public CompletableResultCode flush() {
            return getDelegate().flush();
        }

        @Override
        public CompletableResultCode shutdown() {
            return getDelegate().shutdown();
        }
    }
}
