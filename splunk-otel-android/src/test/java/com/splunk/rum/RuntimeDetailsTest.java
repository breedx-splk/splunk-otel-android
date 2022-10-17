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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuntimeDetailsTest {

    private Context context;

    @BeforeEach
    void setup() {
        context = mock(Context.class);
    }

    @Test
    void testBattery() {
        Intent intent = mock(Intent.class);

        Integer level = 690;
        when(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)).thenReturn(level);
        when(intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)).thenReturn(1000);

        RuntimeDetails details = RuntimeDetails.create(context);

        details.onReceive(context, intent);
        Double result = details.getCurrentBatteryPercent();
        assertEquals(69.0d, result, 0.001);
    }

    @Test
    void testFreeSpace() {
        File filesDir = mock(File.class);

        when(context.getFilesDir()).thenReturn(filesDir);
        when(filesDir.getFreeSpace()).thenReturn(4200L);

        RuntimeDetails details = RuntimeDetails.create(context);

        long result = details.getCurrentStorageFreeSpaceInBytes();
        assertEquals(4200L, result);
    }
}
