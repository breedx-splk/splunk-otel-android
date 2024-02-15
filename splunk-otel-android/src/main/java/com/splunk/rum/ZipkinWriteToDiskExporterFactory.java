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

import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import zipkin2.reporter.BytesMessageSender;

/**
 * Creates a ZipkinSpanExporter that is configured with an instance of a ZipkinToDiskSender that
 * writes telemetry to disk.
 */
class ZipkinWriteToDiskExporterFactory {

    private ZipkinWriteToDiskExporterFactory() {}

    static ZipkinSpanExporter create(int maxUsageMegabytes, SpanStorage spanStorage) {
        FileUtils fileUtils = new FileUtils();
        DeviceSpanStorageLimiter limiter =
                DeviceSpanStorageLimiter.builder()
                        .fileUtils(fileUtils)
                        .fileProvider(spanStorage)
                        .maxStorageUseMb(maxUsageMegabytes)
                        .build();
        BytesMessageSender sender =
                ZipkinToDiskSender.builder()
                        .spanFileProvider(spanStorage)
                        .fileUtils(fileUtils)
                        .storageLimiter(limiter)
                        .build();
        return ZipkinSpanExporter.builder()
                .setEncoder(new CustomZipkinEncoder())
                .setSender(sender)
                // remove the local IP address
                .setLocalIpAddressSupplier(() -> null)
                .build();
    }
}
