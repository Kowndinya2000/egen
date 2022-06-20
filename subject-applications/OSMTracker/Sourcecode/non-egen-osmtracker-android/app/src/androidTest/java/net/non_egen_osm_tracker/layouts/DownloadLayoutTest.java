package net.non_egen_osm_tracker.layouts;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.rule.ActivityTestRule;

import net.non_egen_osm_tracker.OSMTracker;
import net.non_egen_osm_tracker.R;
import net.non_egen_osm_tracker.activity.TrackManager;
import net.non_egen_osm_tracker.util.TestUtils;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;

import java.util.Locale;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.PreferenceMatchers.withTitleText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static junit.framework.TestCase.fail;

public class DownloadLayoutTest {
    @Rule
    public ActivityTestRule<TrackManager> mRule = new ActivityTestRule(TrackManager.class) {
        @Override
        protected void beforeActivityLaunched() {
            // Skip cool intro
            SharedPreferences dtPrefs = PreferenceManager
                    .getDefaultSharedPreferences(getInstrumentation().getTargetContext());
            dtPrefs.edit().putBoolean(OSMTracker.Preferences.KEY_DISPLAY_APP_INTRO, false).apply();
        }
    };

    @Test
    public void downloadLayoutTest() {
        deleteLayoutsDirectory();

        TestUtils.setLayoutsTestingRepository();

        String layoutName = "abc";

        navigateToAvailableLayouts();

        clickButtonsToDownloadLayout(layoutName);

        makePostDownloadAssertions(layoutName);
    }


    public void deleteLayoutsDirectory(){
        try {
            FileUtils.deleteDirectory(TestUtils.getLayoutsDirectory());
        }catch (Exception e){
            e.printStackTrace();
            fail();
        }
    }


    /**
     * Assuming being in TrackManager
     */
    public void navigateToAvailableLayouts(){
        openActionBarOverflowOrOptionsMenu(getInstrumentation().getTargetContext());

        onView(withText(TestUtils.getStringResource(R.string.menu_settings))).perform(click());

        onData(withTitleText(TestUtils.getStringResource(R.string.prefs_ui_buttons_layout))).perform(scrollTo(), click());

        onView(withId(R.id.launch_available)).perform(click());
    }


    /**
     * Check the new layouts appears as a new option
     * Select the layout and check its buttons are shown when tracking
     * @param layoutName
     */
    private void makePostDownloadAssertions(String layoutName) {
        Espresso.pressBack();

        // Check the layout appears as a new option in AvailableLayouts
        onView(withText(layoutName.toLowerCase())).check(ViewAssertions.matches(isDisplayed()));

        // Select the layout
        onView(withText(layoutName.toLowerCase())).perform(click());

        // Go to TrackLogger
        Espresso.pressBack();
        Espresso.pressBack();
        onView(withId(R.id.trackmgr_fab)).perform(click());

        // Check the buttons are loaded correctly
        String expectedButtonsLabels[] = new String[]{"A", "B", "C"};
        for(String label : expectedButtonsLabels)
            onView(withText(label)).check(ViewAssertions.matches(isDisplayed()));

    }


    private void clickButtonsToDownloadLayout(String layoutName) {
        onView(withText(layoutName)).perform(click());

        // Catch languages available dialog that shows up when the cell phone is not in English
        if (! Locale.getDefault().getLanguage().equalsIgnoreCase("en")) {
            onView(withText("English")).perform(click());
        }

        onView(withText(TestUtils.getStringResource(R.string.available_layouts_description_dialog_positive_confirmation))).
                perform(click());

        TestUtils.checkToastIsShownWith(TestUtils.getStringResource(R.string.available_layouts_successful_download));
    }
}