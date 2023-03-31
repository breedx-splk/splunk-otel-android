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

import static io.opentelemetry.android.sample.OtelSampleApplication.RUM;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.location.LocationListenerCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import io.opentelemetry.android.sample.databinding.ActivityMainBinding;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.rum.internal.instrumentation.RumScreenName;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@RumScreenName("Buttercup")
public class MainActivity extends AppCompatActivity {

    private static final Attributes SETTINGS_FEATURE_ATTRIBUTES =
            Attributes.of(stringKey("FeatureName"), "Settings");
    static final int LOCATION_REQUEST_CODE = 42;

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private final LocationListener locationListener = new RumLocationListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController =
                Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        binding.fab.setOnClickListener(
                view -> {
                    new MailDialogFragment(this).show(getSupportFragmentManager(), "Mail");
                });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startListeningForLocations();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_REQUEST_CODE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ((LocationManager) this.getSystemService(Context.LOCATION_SERVICE))
                .removeUpdates(locationListener);
    }

    // we're pretty sure the permission was granted, so we're supressing the permission lint check
    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE
                && Arrays.stream(grantResults)
                        .allMatch(result -> result == PackageManager.PERMISSION_GRANTED)) {
            startListeningForLocations();
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private void startListeningForLocations() {
        ((LocationManager) this.getSystemService(Context.LOCATION_SERVICE))
                .requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        TimeUnit.SECONDS.toMillis(10),
                        100,
                        locationListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {

            //XXX SplunkRum.addRumException() is much cleaner
            //XXX Leaky tracer name exposed here....it's encapsulated better in splunk rum
            UnsupportedOperationException ex = new UnsupportedOperationException("Unimplemented Feature: Settings");
            RUM.getOpenTelemetry().getTracer("OpenTelemetryRum")
                    .spanBuilder(ex.getClass().getSimpleName())
                    .setAllAttributes(SETTINGS_FEATURE_ATTRIBUTES)
                    //XXX No component until spec'd
//                    .setAttribute(COMPONENT_KEY, COMPONENT_ERROR)
                    .startSpan()
                    .recordException(ex)
                    .end();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController =
                Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private static final class RumLocationListener implements LocationListenerCompat {

        @Override
        public void onLocationChanged(@NonNull Location location) {
            Log.d("Location", "Got location " + location);
            // XXX This needs to update the GlobalAttributes, which are currently
            // buried within the RumInitializer
//            SplunkRum.getInstance().updateLocation(location);
        }
    }
}