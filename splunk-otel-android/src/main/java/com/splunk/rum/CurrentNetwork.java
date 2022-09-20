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

import android.os.Build;

import androidx.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

final class CurrentNetwork {
    @Nullable private final Carrier carrier;
    private final NetworkState state;
    @Nullable private final String subType;

    CurrentNetwork(NetworkState state) {
        this(state, null);
    }

    CurrentNetwork(Carrier carrier, NetworkState state) {
        this(carrier, state, null);
    }

    CurrentNetwork(NetworkState state, @Nullable String subType) {
        this(null, state, subType);
    }

    CurrentNetwork(@Nullable Carrier carrier, NetworkState state, @Nullable String subType) {
        this.carrier = carrier;
        this.state = state;
        this.subType = subType;
    }

    boolean isOnline() {
        return getState() != NetworkState.NO_NETWORK_AVAILABLE;
    }

    NetworkState getState() {
        return state;
    }

    Optional<String> getSubType() {
        return Optional.ofNullable(subType);
    }

    @Override
    public String toString() {
        return "CurrentNetwork{" + "state=" + state + ", subType='" + subType + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CurrentNetwork that = (CurrentNetwork) o;
        return Objects.equals(carrier, that.carrier) && state == that.state && Objects.equals(subType, that.subType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carrier, state, subType);
    }

    @SuppressWarnings("NullAway")
    @Nullable
    public String getCarrierCountryCode() {
        return haveCarrier() ? carrier.getMobileCountryCode() : null;
    }

    @SuppressWarnings("NullAway")
    @Nullable
    public String getCarrierIsoCountryCode() {
        return haveCarrier() ? carrier.getIsoCountryCode() : null;
    }

    @SuppressWarnings("NullAway")
    @Nullable
    public String getCarrierNetworkCode() {
        return haveCarrier() ? carrier.getMobileNetworkCode() : null;
    }

    @SuppressWarnings("NullAway")
    @Nullable
    public String getCarrierName() {
        return haveCarrier() ? carrier.getName() : null;
    }

    private boolean haveCarrier(){
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && (carrier != null);
    }
}
