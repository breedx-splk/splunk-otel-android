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

import static io.opentelemetry.rum.internal.RumConstants.SESSION_ID_KEY;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;

/**
 * Session ID ratio based sampler. Uses {@link Sampler#traceIdRatioBased(double)} sampler
 * internally, but passes sessionId instead of traceId to the underlying sampler in order to use the
 * same ratio logic but on sessionId instead. This is valid as sessionId uses {@link
 * io.opentelemetry.api.trace.TraceId#fromLongs(long, long)} internally to generate random session
 * IDs.
 */
class SessionIdRatioBasedSampler implements Sampler {
    private final Sampler ratioBasedSampler;
    private final AttributeKey<String> sessionIdAttributeKey;

    SessionIdRatioBasedSampler(double ratio) {
        this(ratio, SESSION_ID_KEY);
    }

    SessionIdRatioBasedSampler(double ratio, AttributeKey<String> sessionIdAttributeKey) {
        this.sessionIdAttributeKey = sessionIdAttributeKey;
        // SessionId uses the same format as TraceId, so we can reuse trace ID ratio sampler.
        this.ratioBasedSampler = Sampler.traceIdRatioBased(ratio);
    }

    @Override
    public SamplingResult shouldSample(
            Context parentContext,
            String traceId,
            String name,
            SpanKind spanKind,
            Attributes attributes,
            List<LinkData> parentLinks) {

        String sessionId = attributes.get(sessionIdAttributeKey);
        // Replace traceId with sessionId
        return ratioBasedSampler.shouldSample(
                parentContext, sessionId, name, spanKind, attributes, parentLinks);
    }

    @Override
    public String getDescription() {
        return String.format(
                "SessionIdRatioBased{traceIdRatioBased:%s}",
                this.ratioBasedSampler.getDescription());
    }
}
