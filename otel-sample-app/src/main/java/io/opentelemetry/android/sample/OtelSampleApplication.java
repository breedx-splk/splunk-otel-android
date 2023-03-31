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

import android.app.Application;
import android.os.Looper;

import java.util.regex.Pattern;

import io.opentelemetry.rum.internal.OpenTelemetryRum;
import io.opentelemetry.rum.internal.instrumentation.network.CurrentNetworkProvider;
import io.opentelemetry.rum.internal.instrumentation.startup.AppStartupTimer;

public class OtelSampleApplication extends Application {

    private static final Pattern HTTP_URL_SENSITIVE_DATA_PATTERN =
            Pattern.compile("(user|pass)=\\w+");

    public static OpenTelemetryRum RUM;

    @Override
    public void onCreate() {
        super.onCreate();

        AppStartupTimer startupTimer = new AppStartupTimer();
        RumInitializer initializer = new RumInitializer(this, startupTimer);
        RUM = initializer.initialize(CurrentNetworkProvider::createAndStart, Looper.getMainLooper());

//        SplunkRum.builder()
//                // note: for these values to be resolved, put them in your local.properties
//                // file as rum.beacon.url and rum.access.token
//                .setRealm(getResources().getString(R.string.rum_realm))
//                .setApplicationName("Android Demo App")
//                .setRumAccessToken(getResources().getString(R.string.rum_access_token))
//                .enableDebug()
//                .enableDiskBuffering()
//                .setSlowRenderingDetectionPollInterval(Duration.ofMillis(1000))
//                .setDeploymentEnvironment("demo")
//                .limitDiskUsageMegabytes(1)
//                .setGlobalAttributes(
//                        Attributes.builder()
//                                .put("vendor", "Splunk")
//                                .put(StandardAttributes.APP_VERSION, BuildConfig.VERSION_NAME)
//                                .build())
//                .filterSpans(
//                        spanFilter ->
//                                spanFilter
//                                        .removeSpanAttribute(stringKey("http.user_agent"))
//                                        .rejectSpansByName(spanName -> spanName.contains("ignored"))
//                                        // sensitive data in the login http.url attribute
//                                        // will be redacted before it hits the exporter
//                                        .replaceSpanAttribute(
//                                                StandardAttributes.HTTP_URL,
//                                                value ->
//                                                        HTTP_URL_SENSITIVE_DATA_PATTERN
//                                                                .matcher(value)
//                                                                .replaceAll("$1=<redacted>")))
//                .build(this);
    }
}
