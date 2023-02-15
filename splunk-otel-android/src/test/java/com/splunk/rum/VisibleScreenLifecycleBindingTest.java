package com.splunk.rum;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Activity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VisibleScreenLifecycleBindingTest {

    @Mock
    Activity activity;
    @Mock
    VisibleScreenTracker tracker;

    @Test
    void postResumed() {
        VisibleScreenLifecycleBinding underTest = new VisibleScreenLifecycleBinding(tracker);
        underTest.onActivityPostResumed(activity);
        verify(tracker).activityResumed(activity);
        verifyNoMoreInteractions(tracker);
    }

    @Test
    void prePaused() {
        VisibleScreenLifecycleBinding underTest = new VisibleScreenLifecycleBinding(tracker);
        underTest.onActivityPrePaused(activity);
        verify(tracker).activityPaused(activity);
    }
}
