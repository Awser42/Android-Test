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

    public void testFreshInstallStartsWithoutATarget() {
        assertEquals(0L, new CounterStore(activity).getCount());
        assertEquals(0, new CounterStore(activity).getGoal());
        View progress = activity.getWindow().getDecorView().findViewWithTag("progress");
        assertNotNull(progress);
        assertEquals(View.GONE, progress.getVisibility());
        TextView label = activity.getWindow().getDecorView().findViewWithTag("count_label");
        assertNotNull(label);
        assertFalse(label.getText().toString().contains("Target"));
    }

    public void testCountSurvivesActivityRestart() {
        final View button = activity.getWindow().getDecorView().findViewWithTag("counter");
        assertNotNull(button);
        getInstrumentation().runOnMainSync(() -> {
            CounterStore saved = new CounterStore(activity);
            saved.setGoal(0);
            saved.setCounterName("Device restart");
            saved.setDarkTheme(false);
            saved.setDisplayLightEnabled(false);
            saved.setVibrationEnabled(false);
            for (int i = 0; i < 5; i++) button.performClick();
            saved.saveSession("Device snapshot");
            activity.finish();
        });
        getInstrumentation().waitForIdleSync();
        Intent restart = new Intent(getInstrumentation().getTargetContext(), MainActivity.class);
        restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity relaunched = (MainActivity) getInstrumentation().startActivitySync(restart);
        getInstrumentation().waitForIdleSync();
        try {
            assertEquals(5L, new CounterStore(relaunched).getCount());
            CounterStore restored = new CounterStore(relaunched);
            assertEquals(0, restored.getGoal());
            assertEquals("Device restart", restored.getCounterName());
            assertFalse(restored.getDarkTheme());
            assertFalse(restored.getDisplayLightEnabled());
            assertFalse(restored.getVibrationEnabled());
            assertEquals(1, restored.sessions().size());
            assertEquals(0, restored.sessions().get(0).getGoal());
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
