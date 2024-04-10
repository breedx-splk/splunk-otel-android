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

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.android.export.SpanDataModifier;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.semconv.SemanticAttributes;

/**
 * This class hold {@link AttributeKey}s for standard RUM-related attributes that are not in the
 * OpenTelemetry semantic definitions.
 */
public final class StandardAttributes {
    /**
     * The version of your app. Useful for adding to global attributes.
     *
     * @see SplunkRumBuilder#setGlobalAttributes(Attributes)
     */
    public static final AttributeKey<String> APP_VERSION = stringKey("app.version");

    public static final String EXCEPTION_EVENT_NAME = "exception";

    /**
     * The build type of your app (typically one of debug or release). Useful for adding to global
     * attributes.
     *
     * @see SplunkRumBuilder#setGlobalAttributes(Attributes)
     */
    public static final AttributeKey<String> APP_BUILD_TYPE = stringKey("app.build.type");

    /**
     * Full HTTP client request URL in the form {@code scheme://host[:port]/path?query[#fragment]}.
     * Useful for span data filtering with the {@link SpanDataModifier}.
     *
     * @see SemanticAttributes#HTTP_URL
     */
    public static final AttributeKey<String> HTTP_URL = SemanticAttributes.HTTP_URL;

    public static final AttributeKey<? super String> PREVIOUS_SESSION_ID_KEY =
            AttributeKey.stringKey("splunk.rum.previous_session_id");

    public static final AttributeKey<? super String> SESSION_ID_KEY =
            stringKey("splunk.rumSessionId");

    private StandardAttributes() {}
}
