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

package io.opentelemetry.rum.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This class is used to modify span data at export time. It is created with a delegate exporter,
 * and can reject spans outright based on span names or span attribute content. Furthermore, it can
 * replace span attributes with a value newly calculated at export time.
 */
final class SpanDataModifier implements SpanExporter {
    private final SpanExporter delegate;
    private final Predicate<String> rejectSpanNamesPredicate;
    private final Map<AttributeKey<?>, Predicate<?>> rejectSpanAttributesPredicates;
    private final Map<AttributeKey<?>, Function<?, ?>> spanAttributeReplacements;

    SpanDataModifier(
            SpanExporter delegate,
            Predicate<String> rejectSpanNamesPredicate,
            Map<AttributeKey<?>, Predicate<?>> rejectSpanAttributesPredicates,
            Map<AttributeKey<?>, Function<?, ?>> spanAttributeReplacements) {
        this.delegate = delegate;
        this.rejectSpanNamesPredicate = rejectSpanNamesPredicate;
        this.rejectSpanAttributesPredicates = rejectSpanAttributesPredicates;
        this.spanAttributeReplacements = spanAttributeReplacements;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        List<SpanData> modified =
                spans.stream().filter(this::include).map(this::modify).collect(Collectors.toList());
        return delegate.export(modified);
    }

    private boolean include(SpanData span) {
        if (rejectSpanNamesPredicate.test(span.getName())) {
            return false;
        }
        Attributes attributes = span.getAttributes();
        return rejectSpanAttributesPredicates.entrySet().stream()
                .noneMatch(
                        e -> {
                            AttributeKey<?> key = e.getKey();
                            Predicate<? super Object> valuePredicate =
                                    (Predicate<? super Object>) e.getValue();
                            Object attributeValue = attributes.get(key);
                            return (attributeValue != null && valuePredicate.test(attributeValue));
                        });
    }

    private SpanData modify(SpanData span) {
        if (spanAttributeReplacements.isEmpty()) {
            return span;
        }

        AttributesBuilder modifiedAttributes = Attributes.builder();
        BiConsumer<AttributeKey<?>, Object> doModify =
                (key, value) -> {
                    Function<? super Object, ?> valueModifier =
                            (Function<? super Object, ?>)
                                    spanAttributeReplacements.getOrDefault(
                                            key, Function.identity());
                    Object newValue = valueModifier.apply(value);
                    if (newValue != null) {
                        modifiedAttributes.put((AttributeKey<Object>) key, newValue);
                    }
                };
        span.getAttributes().forEach(doModify);

        return new ModifiedSpanData(span, modifiedAttributes.build());
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }
}
