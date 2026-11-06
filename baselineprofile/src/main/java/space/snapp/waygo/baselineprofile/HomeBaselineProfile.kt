package space.snapp.waygo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code the home screen uses, so it ships compiled instead of being interpreted on
 * first use. Run on an emulator, never your phone: the test run uninstalls the app, data included.
 *
 * Fake locations are off in this build, so the emulator needs a real fix, kept up during the run:
 *
 *   adb -s emulator-5554 emu geo fix 152.9965 -27.4806
 *   ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateFastBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class HomeBaselineProfile {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun home() = rule.collect(packageName = "space.snapp.waygo", includeInStartupProfile = true) {
        // Without a location there are no rows, and the rows are what most needs recording
        device.executeShellCommand("pm grant space.snapp.waygo android.permission.ACCESS_FINE_LOCATION")
        pressHome()
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.textContains("routes nearby")), 30_000)) {
            "No rows on screen: send the emulator a location in SEQ with `adb emu geo fix 152.9965 -27.4806`"
        }

        // Pan the map
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(3) {
            device.swipe(w / 4, h / 5, w * 3 / 4, h * 2 / 5, 20)
            device.swipe(w * 3 / 4, h * 2 / 5, w / 4, h / 5, 20)
        }
        // Select the first row: its stop, route line and vehicles on the map
        device.click(w / 2, h * 3 / 4)
        device.waitForIdle()
        Thread.sleep(2_000)

        // Drag the panel up, scroll the list, and bring it back
        device.swipe(w / 2, h * 3 / 5, w / 2, h / 5, 30)
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.apply {
            setGestureMargin(h / 10)
            scroll(Direction.DOWN, 1f)
            scroll(Direction.UP, 1f)
        }
        device.swipe(w / 2, h / 4, w / 2, h * 4 / 5, 30)
        device.waitForIdle()

        // A tab and back
        device.findObject(By.text("Favourites"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Search"))?.click()
        device.wait(Until.hasObject(By.textContains("routes nearby")), 10_000)
    }
}
