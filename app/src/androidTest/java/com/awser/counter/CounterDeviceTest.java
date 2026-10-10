package com.awser.counter;

import android.content.Context;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.TextView;

/** Runs on a real/emulated Android device without adding a runtime library. */
@SuppressWarnings("deprecation")
public class CounterDeviceTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private MainActivity activity;

    public CounterDeviceTest() {
        super(MainActivity.class);
    }

    @Override protected void setUp() throws Exception {
        super.setUp();
        getInstrumentation().getTargetContext()
                .getSharedPreferences(CounterStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        setActivityInitialTouchMode(false);
        activity = getActivity();
        getInstrumentation().waitForIdleSync();
    }

    public void testCounterButtonPersistsEveryTap() {
        final View button = activity.getWindow().getDecorView().findViewWithTag("counter");
        assertNotNull("Accessible counter button should exist", button);
        getInstrumentation().runOnMainSync(() -> {
            for (int i = 0; i < 12; i++) {
                assertTrue(button.performClick());
            }
        });
        getInstrumentation().waitForIdleSync();
        assertEquals(12L, new CounterStore(activity).getCount());
        final TextView countLabel = activity.getWindow().getDecorView().findViewWithTag("count_label");
        assertNotNull("Visible count label should exist", countLabel);
        assertTrue(countLabel.getText().toString().contains("12"));
    }

    public void testCountSurvivesActivityRestart() {
        final View button = activity.getWindow().getDecorView().findViewWithTag("counter");
        assertNotNull(button);
        getInstrumentation().runOnMainSync(() -> {
            for (int i = 0; i < 5; i++) button.performClick();
            activity.finish();
        });
        getInstrumentation().waitForIdleSync();
        Intent restart = new Intent(getInstrumentation().getTargetContext(), MainActivity.class);
        restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity relaunched = (MainActivity) getInstrumentation().startActivitySync(restart);
        getInstrumentation().waitForIdleSync();
        try {
            assertEquals(5L, new CounterStore(relaunched).getCount());
            TextView countLabel = relaunched.getWindow().getDecorView().findViewWithTag("count_label");
            assertNotNull(countLabel);
            assertTrue(countLabel.getText().toString().contains("5"));
        } finally {
            getInstrumentation().runOnMainSync(relaunched::finish);
        }
    }

    public void testOpeningResetKeepsCountUntilConfirmation() {
        getInstrumentation().runOnMainSync(() -> {
            View button = activity.getWindow().getDecorView().findViewWithTag("counter");
            View reset = activity.getWindow().getDecorView().findViewWithTag("reset");
            assertNotNull(button);
            assertNotNull(reset);
            button.performClick();
            button.performClick();
            reset.performClick();
        });
        getInstrumentation().waitForIdleSync();
        assertEquals("Opening reset must not discard a session", 2L, new CounterStore(activity).getCount());
    }
}
