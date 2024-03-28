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

package com.splunk.android.sample;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.app.Application;

import androidx.annotation.NonNull;

import com.splunk.rum.SplunkRum;
import com.splunk.rum.StandardAttributes;
import com.splunk.rum.incubating.HttpSenderCustomizer;

import io.opentelemetry.api.common.Attributes;

import java.io.IOException;
import java.time.Duration;
import java.util.regex.Pattern;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import zipkin2.reporter.okhttp3.OkHttpSender;

public class SampleApplication extends Application {

    private static final Pattern HTTP_URL_SENSITIVE_DATA_PATTERN =
            Pattern.compile("(user|pass)=\\w+");

    @Override
    public void onCreate() {
        super.onCreate();

        SplunkRum.builder()
                // note: for these values to be resolved, put them in your local.properties
                // file as rum.beacon.url and rum.access.token
                .setRealm(getResources().getString(R.string.rum_realm))
                .setApplicationName("Android Demo App")
                .setRumAccessToken(getResources().getString(R.string.rum_access_token))
                .enableDebug()
//                .enableDiskBuffering()
                .enableExperimentalOtlpExporter()
                .setHttpSenderCustomizer(builder -> builder.clientBuilder()
                        .addInterceptor(new Interceptor() {
                    @NonNull
                    @Override
                    public Response intercept(@NonNull Chain chain) throws IOException {
                        Request req = chain.request();
                        Response response = chain.proceed(req);
                        return response;
                    }
                }))
                .disableSubprocessInstrumentation(BuildConfig.APPLICATION_ID)
                .enableBackgroundInstrumentationDeferredUntilForeground()
                .setSlowRenderingDetectionPollInterval(Duration.ofMillis(1000))
                .setDeploymentEnvironment("demo")
                .limitDiskUsageMegabytes(1)
                .setGlobalAttributes(
                        Attributes.builder()
                                .put("vendor", "Splunk")
                                .put(StandardAttributes.APP_VERSION, BuildConfig.VERSION_NAME)
                                .build())
                .filterSpans(
                        spanFilter ->
                                spanFilter
                                        .removeSpanAttribute(stringKey("http.user_agent"))
                                        .rejectSpansByName(spanName -> spanName.contains("ignored"))
                                        // sensitive data in the login http.url attribute
                                        // will be redacted before it hits the exporter
                                        .replaceSpanAttribute(
                                                StandardAttributes.HTTP_URL,
                                                value ->
                                                        HTTP_URL_SENSITIVE_DATA_PATTERN
                                                                .matcher(value)
                                                                .replaceAll("$1=<redacted>")))
                .setHttpSenderCustomizer(
                        okHttpBuilder -> {
                            okHttpBuilder.compressionEnabled(true);
                            okHttpBuilder
                                    .clientBuilder()
                                    .addInterceptor(
                                            chain -> {
                                                Request.Builder requestBuilder =
                                                        chain.request().newBuilder();
                                                requestBuilder.header(
                                                        "X-My-Custom-Header", "abc123");
                                                return chain.proceed(requestBuilder.build());
                                            });
                        })
                .build(this);
    }
}
